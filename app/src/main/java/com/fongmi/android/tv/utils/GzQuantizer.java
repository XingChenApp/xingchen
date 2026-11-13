package com.fongmi.android.tv.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 瓜子 iq5/i91/h91/jq5 的逐行移植（本体＝androidx Palette 的 ColorCutQuantizer 着色版）：
 * 直方图 5bit 量化→体积优先盒切分→盒内加权平均。瓜子各调用点都先把过滤器表清空
 * （e/f/o 三处 iq5.f.clear()），故本移植不带任何颜色过滤（C()≡false），与瓜子实效一致。
 * 面积缩放沿 iq5.a()：像素数 &gt;12544 时等比缩（filter=false），region 按宽比换算。
 */
public final class GzQuantizer {

    private GzQuantizer() {
    }

    public static final class Swatch {
        public final int rgb;
        public final int population;
        private float[] mHsl;

        Swatch(int rgb, int population) {
            this.rgb = rgb;
            this.population = population;
        }

        /** HSL（jq5.b()＝z91.a 的 RGBToHSL 移植）。 */
        public float[] hsl() {
            if (mHsl == null) {
                mHsl = new float[3];
                rgbToHsl(Color.red(rgb), Color.green(rgb), Color.blue(rgb), mHsl);
            }
            return mHsl;
        }
    }

    public static final class Result {
        public final List<Swatch> swatches;
        /** 人口最大者（ug3.g 口径：严格大于才换，平局取先）。 */
        public final Swatch dominant;

        Result(List<Swatch> swatches) {
            this.swatches = swatches;
            Swatch best = null;
            int bestPop = Integer.MIN_VALUE;
            for (Swatch s : swatches) {
                if (s.population > bestPop) {
                    best = s;
                    bestPop = s.population;
                }
            }
            this.dominant = best;
        }
    }

    private static final int RESIZE_AREA = 12544;

    /** region 为位图坐标系内的裁剪区（iq5.g），null＝全图。 */
    public static Result quantize(Bitmap bitmap, int maxColors, Rect region) {
        Bitmap work = bitmap;
        double scale = -1.0;
        int area = bitmap.getWidth() * bitmap.getHeight();
        if (area > RESIZE_AREA) {
            scale = Math.sqrt((double) RESIZE_AREA / area);
        }
        Rect reg = region;
        if (scale > 0) {
            work = Bitmap.createScaledBitmap(bitmap,
                    (int) Math.ceil(bitmap.getWidth() * scale),
                    (int) Math.ceil(bitmap.getHeight() * scale), false);
            if (reg != null) {
                double ratio = (double) work.getWidth() / bitmap.getWidth();
                reg = new Rect(
                        (int) Math.floor(region.left * ratio),
                        (int) Math.floor(region.top * ratio),
                        Math.min((int) Math.ceil(region.right * ratio), work.getWidth()),
                        Math.min((int) Math.ceil(region.bottom * ratio), work.getHeight()));
            }
        }
        int w = work.getWidth(), h = work.getHeight();
        int[] pixels = new int[w * h];
        work.getPixels(pixels, 0, w, 0, 0, w, h);
        if (reg != null) {
            int rw = reg.width(), rh = reg.height();
            int[] cut = new int[rw * rh];
            for (int y = 0; y < rh; y++) {
                System.arraycopy(pixels, (reg.top + y) * w + reg.left, cut, y * rw, rw);
            }
            pixels = cut;
        }

        int[] hist = new int[32768];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int q = (quantizeChannel(Color.red(p)) << 10)
                    | (quantizeChannel(Color.green(p)) << 5)
                    | quantizeChannel(Color.blue(p));
            pixels[i] = q;
            hist[q]++;
        }
        int distinct = 0;
        for (int v : hist) {
            if (v > 0) distinct++;
        }
        int[] colors = new int[distinct];
        int idx = 0;
        for (int i = 0; i < 32768; i++) {
            if (hist[i] > 0) colors[idx++] = i;
        }

        List<Swatch> swatches = new ArrayList<>();
        if (distinct <= maxColors) {
            for (int c : colors) {
                swatches.add(new Swatch(expandColor(c), hist[c]));
            }
        } else {
            PriorityQueue<Box> queue = new PriorityQueue<>(maxColors,
                    (a, b) -> b.volume() - a.volume());
            queue.offer(new Box(colors, hist, 0, colors.length - 1));
            while (queue.size() < maxColors) {
                Box box = queue.poll();
                if (box == null || (box.maxIndex + 1) - box.minIndex <= 1) break;
                int rRange = box.maxR - box.minR;
                int gRange = box.maxG - box.minG;
                int bRange = box.maxB - box.minB;
                int component;
                if (rRange < gRange || rRange < bRange) {
                    component = (gRange < rRange || gRange < bRange) ? -1 : -2;
                } else {
                    component = -3;
                }
                reorder(colors, component, box.minIndex, box.maxIndex);
                Arrays.sort(colors, box.minIndex, box.maxIndex + 1);
                reorder(colors, component, box.minIndex, box.maxIndex);
                int midPop = box.population / 2;
                int split = box.minIndex;
                int sum = 0;
                for (int i = box.minIndex; i <= box.maxIndex; i++) {
                    sum += hist[colors[i]];
                    if (sum >= midPop) {
                        split = Math.min(box.maxIndex - 1, i);
                        break;
                    }
                }
                Box upper = new Box(colors, hist, split + 1, box.maxIndex);
                box.maxIndex = split;
                box.fit(colors, hist);
                queue.offer(upper);
                queue.offer(box);
            }
            for (Box box : queue) {
                long rSum = 0, gSum = 0, bSum = 0;
                int pop = 0;
                for (int i = box.minIndex; i <= box.maxIndex; i++) {
                    int c = colors[i];
                    int cnt = hist[c];
                    pop += cnt;
                    rSum += ((c >> 10) & 31) * (long) cnt;
                    gSum += ((c >> 5) & 31) * (long) cnt;
                    bSum += (c & 31) * (long) cnt;
                }
                float f = pop;
                int rgb = Color.rgb(
                        expandChannel(Math.round(rSum / f)),
                        expandChannel(Math.round(gSum / f)),
                        expandChannel(Math.round(bSum / f)));
                swatches.add(new Swatch(rgb, pop));
            }
        }
        if (work != bitmap) work.recycle();
        return new Result(swatches);
    }

    private static final class Box {
        int minIndex;
        int maxIndex;
        int population;
        int minR, maxR, minG, maxG, minB, maxB;

        Box(int[] colors, int[] hist, int min, int max) {
            this.minIndex = min;
            this.maxIndex = max;
            fit(colors, hist);
        }

        void fit(int[] colors, int[] hist) {
            int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;
            int minG = Integer.MAX_VALUE, maxG = Integer.MIN_VALUE;
            int minB = Integer.MAX_VALUE, maxB = Integer.MIN_VALUE;
            int pop = 0;
            for (int i = minIndex; i <= maxIndex; i++) {
                int c = colors[i];
                pop += hist[c];
                int r = (c >> 10) & 31, g = (c >> 5) & 31, b = c & 31;
                if (r > maxR) maxR = r;
                if (r < minR) minR = r;
                if (g > maxG) maxG = g;
                if (g < minG) minG = g;
                if (b > maxB) maxB = b;
                if (b < minB) minB = b;
            }
            this.minR = minR;
            this.maxR = maxR;
            this.minG = minG;
            this.maxG = maxG;
            this.minB = minB;
            this.maxB = maxB;
            this.population = pop;
        }

        int volume() {
            return (maxB - minB + 1) * (maxG - minG + 1) * (maxR - minR + 1);
        }
    }

    /** i91.y：把待排序分量换到高位槽，排完再换回（-1=蓝、-2=绿、-3=红不动）。 */
    private static void reorder(int[] colors, int component, int from, int to) {
        if (component == -2) {
            for (int i = from; i <= to; i++) {
                int c = colors[i];
                colors[i] = (c & 31) | (((c >> 5) & 31) << 10) | (((c >> 10) & 31) << 5);
            }
        } else if (component == -1) {
            for (int i = from; i <= to; i++) {
                int c = colors[i];
                colors[i] = ((c >> 10) & 31) | ((c & 31) << 10) | (((c >> 5) & 31) << 5);
            }
        }
    }

    /** i91.z(v, 8, 5)。 */
    private static int quantizeChannel(int v) {
        return (v >> 3) & 31;
    }

    /** i91.z(v, 5, 8)。 */
    private static int expandChannel(int v) {
        return (v << 3) & 255;
    }

    private static int expandColor(int q) {
        return Color.rgb(expandChannel((q >> 10) & 31), expandChannel((q >> 5) & 31), expandChannel(q & 31));
    }

    /** z91.a（androidx ColorUtils.RGBToHSL）移植。 */
    static void rgbToHsl(int r, int g, int b, float[] out) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        float lightness = (max + min) / 2f;
        float hue, sat;
        if (max == min) {
            hue = 0f;
            sat = 0f;
        } else {
            hue = max == rf ? ((gf - bf) / delta) % 6f
                    : max == gf ? ((bf - rf) / delta) + 2f
                    : 4f + ((rf - gf) / delta);
            sat = delta / (1f - Math.abs(2f * lightness - 1f));
        }
        float h = (hue * 60f) % 360f;
        if (h < 0f) h += 360f;
        out[0] = h < 0f ? 0f : Math.min(h, 360f);
        out[1] = sat < 0f ? 0f : Math.min(sat, 1f);
        out[2] = lightness >= 0f ? Math.min(lightness, 1f) : 0f;
    }
}
