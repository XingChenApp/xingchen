package com.fongmi.android.tv.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;

/**
 * 用途：从专辑封面提取主色与氛围底色，供音乐播放页整页取色（纯静态工具，无状态、无缓存）。
 */
public final class CoverColor {

    // ===== 常量区（数值出处：GZ 反编译 CoverAmbientKt 自研取色算法实证，2026-10-21） =====
    /** 兜底色：空图／全透明图时返回（GZ CoverAmbientKt 默认色 0xFF1C1C1E 原值）。 */
    public static final int FALLBACK_COLOR = 0xFF1C1C1E;
    /** 调用方应先把图缩到 ≤96px；方法内再兜底缩放到的最长边。 */
    private static final int MAX_EDGE = 96;
    /** 自研量化器档数：每通道量化到 24 档后聚合直方图（GZ 口径）。 */
    private static final int QUANT_LEVELS = 24;
    /** 下半区域起始比例：画面下方 55%（即 y ≥ 高度 × 0.45 起）。 */
    private static final float LOWER_START_RATIO = 0.45f;
    /** 下半区域采样权重倍率（GZ 口径 ×1.35）。 */
    private static final double LOWER_WEIGHT = 1.35d;
    /** HSV 过滤（GZ 主路函数 e，CoverAmbientKt:1152/1162）：明度 V∈[0.06,0.92]、饱和度 S≥0.1（HSV 口径）。 */
    private static final float MIN_VALUE = 0.06f;
    private static final float MAX_VALUE = 0.92f;
    private static final float MIN_SATURATION = 0.10f;
    /** 打分（GZ 主路）：S≥0.12 时饱和系数 1.0，否则 0.6。 */
    private static final float SATURATION_FULL_THRESHOLD = 0.12f;
    /** 暖色相降权（GZ 主路）：色相 5–50° 且 S∈[0.12,0.72] 且 V∈[0.25,0.82] 时打分 ×0.3。 */
    private static final float WARM_HUE_MIN = 5f;
    private static final float WARM_HUE_MAX = 50f;
    private static final float WARM_SAT_MIN = 0.12f;
    private static final float WARM_SAT_MAX = 0.72f;
    private static final float WARM_VAL_MIN = 0.25f;
    private static final float WARM_VAL_MAX = 0.82f;
    private static final double WARM_PENALTY = 0.3d;
    /** 低于此 alpha 的像素视为透明、跳过采样。 */
    private static final int MIN_ALPHA = 128;
    /** ambientBackground 压暗口径：主色向黑色混合的比例（GZ 口径 0.55）。 */
    private static final float AMBIENT_DARKEN_MIX = 0.55f;
    /** isBright 的相对亮度阈值（0–1）。 */
    private static final float BRIGHT_LUMINANCE = 0.62f;

    private CoverColor() {
    }

    /**
     * 提取封面主色（GZ 主路函数 e 口径）：24 档量化扫全图（下半 55% 区域权重 ×1.35），
     * 对每个色桶取均值色做 HSV 过滤（V∈[0.06,0.92]、S≥0.1）后按 GZ 主路打分
     * 「计数 ×(S≥0.12?1:0.6) ×暖色相降权」计分，按色相分桶（12 桶）取最高桶内最高分。
     * 同步方法，请在后台线程调用；入参为小图（≤96px），方法内再兜底缩放。
     */
    public static int dominantColor(Bitmap bmp) {
        Bitmap small = prepare(bmp);
        if (small == null) return FALLBACK_COLOR;
        try {
            return pickDominant(small);
        } finally {
            if (small != bmp) small.recycle();
        }
    }

    /**
     * CD 链取色（GZ CoverAmbientKt 函数 o 本体，2026-10-26 jadx 复读）：iq5 量化 24 色
     * （GzQuantizer 逐行移植、过滤器表已清＝无颜色过滤）后在色板上选：HSL 口径过滤
     * 明度 L∈[0.05,0.95]；饱和子集（S≥0.06）人口占比 ≥12% 时走色相分桶打分选色，
     * 占比不足回落过滤集人口最大者，过滤集为空回落量化主导色。
     * 返回的是未归一的单色，调用方再过 toneAdjust（GZ 的 d()）得 tone。
     * 同步方法，请在后台线程调用。
     */
    public static int cdToneColor(Bitmap bmp) {
        if (bmp == null || bmp.isRecycled() || bmp.getWidth() < 1 || bmp.getHeight() < 1) return FALLBACK_COLOR;
        return pickCdTone(bmp);
    }

    private static int pickCdTone(Bitmap bmp) {
        GzQuantizer.Result res = GzQuantizer.quantize(bmp, 24, null);
        if (res.swatches.isEmpty()) return FALLBACK_COLOR;

        int dominantColor = res.dominant != null ? res.dominant.rgb : FALLBACK_COLOR;
        int filteredBestColor = FALLBACK_COLOR;
        double filteredBestPop = -1;
        double filteredPop = 0;
        double saturatedPop = 0;
        double[] bucketScore = new double[12];
        int[] bucketBestColor = new int[12];
        double[] bucketBestInner = new double[12];
        for (int i = 0; i < 12; i++) bucketBestInner[i] = -1;
        for (GzQuantizer.Swatch sw : res.swatches) {
            double weight = sw.population;
            int color = sw.rgb;
            float[] hsl = sw.hsl();
            float hue = hsl[0];
            float sat = hsl[1];
            float light = hsl[2];
            if (light < 0.05f || light > 0.95f) continue;
            filteredPop += weight;
            if (weight > filteredBestPop) {
                filteredBestPop = weight;
                filteredBestColor = color;
            }
            if (sat < 0.06f) continue;
            saturatedPop += weight;
            // 桶分累加：人口 × min(0.53×min(S,0.6)＋0.68, 1.0) × 目标盒因子
            // （盒内：色相 12–58°、S∈[0.1,0.58]、L∈[0.16,0.84] → 1.0，否则 0.78）
            double weightPer = Math.min(0.53d * Math.min(sat, 0.6f) + 0.68d, 1.0d);
            boolean inBox = hue >= 12f && hue <= 58f
                    && sat >= 0.1f && sat <= 0.58f
                    && light >= 0.16f && light <= 0.84f;
            int hb = hueBucketIndex(hue);
            bucketScore[hb] += weight * weightPer * (inBox ? 1.0d : 0.78d);
            // 桶内胜出：人口 ×(0.88＋0.12S) × 明度因子（[0.22,0.78]→1.0、[0.12,0.88]→0.82、其余 0.55）
            double lightFactor = (light >= 0.22f && light <= 0.78f) ? 1.0d
                    : (light >= 0.12f && light <= 0.88f) ? 0.82d : 0.55d;
            double inner = weight * (0.88d + 0.12d * sat) * lightFactor;
            if (inner > bucketBestInner[hb]) {
                bucketBestInner[hb] = inner;
                bucketBestColor[hb] = color;
            }
        }
        if (filteredPop > 0 && saturatedPop / filteredPop >= 0.12d) {
            int bestBucket = -1;
            double bestScore = -1;
            for (int i = 0; i < 12; i++) {
                if (bucketBestInner[i] < 0) continue;
                if (bucketScore[i] > bestScore) {
                    bestScore = bucketScore[i];
                    bestBucket = i;
                }
            }
            if (bestBucket >= 0) return bucketBestColor[bestBucket];
        }
        if (filteredPop > 0) return filteredBestColor;
        return dominantColor;
    }

    /**
     * 经典页外壳底色（GZ CoverAmbientKt 的 mk1 链，2026-10-26 smali/jadx 逐项抠死）：
     * 底色 ＝ b变换(e选色， accent)，即缓存对 wk1 的 a 分量（竖屏经典页 $targetAmbient 的值，
     * 450ms 动画的目标色；b 分量是状态栏采样色、z 是顶部亮度旗，经典外壳不消费）。
     * e选色：量化两趟（全图 24 色＋下部 0.45 以下区域 24 色，人口权重 1.0/1.35 按色合并），
     * HSL 过滤 L∈[0.06,0.92]、S≥0.1 后按色相 12 桶汇总打分（人口×(S≥0.12?1:0.6)×暖色盒 0.3），
     * 胜桶内按 t 分（(log(人口+1)×0.15+1)×人口×饱和因子×明度因子）取最大；
     * 无候选时回落 L 过滤集人口最大者，再回落全图趟主导色。
     * accent：e 色 HSV 饱和＜0.1 时取 o 路（cdToneColor）的色，其 HSV 饱和≥0.15 才算数。
     * 量化器 iq5 已逐行移植为 GzQuantizer（含其内部 12544 面积缩放与盒切分），
     * 与瓜子同图同结果，不再经 androidx Palette。同步方法，后台线程调用。
     */
    public static int shellBaseColor(Bitmap bmp) {
        if (bmp == null || bmp.isRecycled() || bmp.getWidth() < 1 || bmp.getHeight() < 1) return FALLBACK_COLOR;
        int picked = pickShellColor(bmp);
        Integer accent = null;
        float[] hsv = new float[3];
        Color.colorToHSV(picked, hsv);
        if (hsv[1] < 0.1f) {
            int o = cdToneColor(bmp);
            float[] hsvO = new float[3];
            Color.colorToHSV(o, hsvO);
            if (hsvO[1] >= 0.15f) accent = o;
        }
        return shellToneTransform(picked, accent);
    }

    private static int pickShellColor(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        GzQuantizer.Result full = GzQuantizer.quantize(bmp, 24, null);
        int lowerTop = Math.max(0, Math.min(h - 1, (int) (h * 0.45f)));
        GzQuantizer.Result lower = GzQuantizer.quantize(bmp, 24, new Rect(0, lowerTop, w, h));
        // 按色合并两趟人口（权重 1.0 / 1.35）
        java.util.LinkedHashMap<Integer, float[]> merged = new java.util.LinkedHashMap<>();
        java.util.HashMap<Integer, float[]> hslByRgb = new java.util.HashMap<>();
        for (GzQuantizer.Swatch s : full.swatches) {
            merged.put(s.rgb, new float[]{s.population});
            hslByRgb.put(s.rgb, s.hsl());
        }
        for (GzQuantizer.Swatch s : lower.swatches) {
            float[] cur = merged.get(s.rgb);
            if (cur == null) {
                merged.put(s.rgb, new float[]{s.population * 1.35f});
                hslByRgb.put(s.rgb, s.hsl());
            } else {
                cur[0] += s.population * 1.35f;
            }
        }
        if (merged.isEmpty()) return FALLBACK_COLOR;
        // HSL 过滤：L∈[0.06,0.92]
        java.util.ArrayList<Integer> lightOk = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Integer, float[]> e : merged.entrySet()) {
            float l = hslByRgb.get(e.getKey())[2];
            if (l >= 0.06f && l <= 0.92f) lightOk.add(e.getKey());
        }
        // S≥0.1 子集 → 色相分桶打分
        double[] bucketTotals = new double[12];
        boolean anySat = false;
        for (int rgb : lightOk) {
            float[] hsl = hslByRgb.get(rgb);
            if (hsl[1] < 0.1f) continue;
            anySat = true;
            double score = merged.get(rgb)[0] * (hsl[1] >= 0.12f ? 1.0 : 0.6);
            boolean warm = hsl[0] >= 5f && hsl[0] <= 50f
                    && hsl[1] >= 0.12f && hsl[1] <= 0.72f
                    && hsl[2] >= 0.25f && hsl[2] <= 0.82f;
            if (warm) score *= 0.3;
            bucketTotals[hueBucketIndex(hsl[0])] += score;
        }
        if (anySat) {
            int bestBucket = -1;
            double bestTotal = -1;
            for (int i = 0; i < 12; i++) {
                if (bucketTotals[i] > bestTotal) {
                    bestTotal = bucketTotals[i];
                    bestBucket = i;
                }
            }
            int bestRgb = 0;
            float bestT = -1f;
            for (int rgb : lightOk) {
                float[] hsl = hslByRgb.get(rgb);
                if (hsl[1] < 0.1f || hueBucketIndex(hsl[0]) != bestBucket) continue;
                float t = shellTScore(merged.get(rgb)[0], hsl[1], hsl[2]);
                if (t > bestT) {
                    bestT = t;
                    bestRgb = rgb;
                }
            }
            if (bestT >= 0) return bestRgb;
        }
        // 回落：L 过滤集人口最大者；再回落全图趟主导色
        if (!lightOk.isEmpty()) {
            int bestRgb = lightOk.get(0);
            float bestPop = -1f;
            for (int rgb : lightOk) {
                float p = merged.get(rgb)[0];
                if (p > bestPop) {
                    bestPop = p;
                    bestRgb = rgb;
                }
            }
            return bestRgb;
        }
        return full.dominant != null ? full.dominant.rgb : FALLBACK_COLOR;
    }

    /** GZ t() 打分：(log(人口+1)×0.15+1)×人口 × 饱和因子（[0.12,0.55]→1.25、(0.55,0.85]→1.1、其余 0.9）× 明度因子（[0.25,0.72]→1.2、[0.15,0.85]→1.0、其余 0.55），HSL 口径（smali 亲验）。 */
    private static float shellTScore(float pop, float s, float l) {
        float base = (float) ((Math.log(pop + 1.0) * 0.15) + 1.0) * pop;
        float satF = (s >= 0.12f && s <= 0.55f) ? 1.25f : (s > 0.55f && s <= 0.85f) ? 1.1f : 0.9f;
        float lightF = (l >= 0.25f && l <= 0.72f) ? 1.2f : (l >= 0.15f && l <= 0.85f) ? 1.0f : 0.55f;
        return base * satF * lightF;
    }

    /**
     * GZ b() 变换（HSV 口径，smali 逐常量亲验）：主色饱和＜0.1 且 accent 饱和≥0.15 时借 accent 的色相；
     * S'：借相时 S＜0.08→clamp(S,0,0.16)、S＜0.18→clamp(S×1.35+0.05,0.12,0.45)、否则 clamp(S×1.32+0.05,0.22,0.9)；
     * 未借相→clamp(S×1.32+0.1,0.28,0.9)。V'＝clamp(V×因子,0.18,0.5)，因子按 V 分段：≥0.75→0.4、≥0.55→0.48、≥0.35→0.58、其余 0.78。
     */
    private static int shellToneTransform(int color, Integer accent) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        float hue = hsv[0];
        float sat = hsv[1];
        float val = hsv[2];
        boolean borrowed = false;
        if (sat < 0.1f && accent != null) {
            float[] hsvA = new float[3];
            Color.colorToHSV(accent, hsvA);
            if (hsvA[1] >= 0.15f) {
                hue = hsvA[0];
                borrowed = true;
            }
        }
        float newSat;
        if (borrowed) {
            if (sat < 0.08f) newSat = clampFloat(sat, 0f, 0.16f);
            else if (sat < 0.18f) newSat = clampFloat(sat * 1.35f + 0.05f, 0.12f, 0.45f);
            else newSat = clampFloat(sat * 1.32f + 0.05f, 0.22f, 0.9f);
        } else {
            newSat = clampFloat(sat * 1.32f + 0.1f, 0.28f, 0.9f);
        }
        float vf = val >= 0.75f ? 0.4f : val >= 0.55f ? 0.48f : val >= 0.35f ? 0.58f : 0.78f;
        float newVal = clampFloat(val * vf, 0.18f, 0.5f);
        return Color.HSVToColor(new float[]{hue, newSat, newVal});
    }

    /** 返回氛围底色：主色向黑色混 0.55 的压暗版，供整页底色。 */
    public static int ambientBackground(Bitmap bmp) {
        int dominant = dominantColor(bmp);
        return mixToBlack(dominant, AMBIENT_DARKEN_MIX);
    }

    /** 判断颜色是否偏亮（相对亮度口径）。 */
    public static boolean isBright(int color) {
        float r = Color.red(color) / 255f;
        float g = Color.green(color) / 255f;
        float b = Color.blue(color) / 255f;
        float luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        return luminance >= BRIGHT_LUMINANCE;
    }

    private static Bitmap prepare(Bitmap bmp) {
        if (bmp == null || bmp.isRecycled()) return null;
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (w <= 0 || h <= 0) return null;
        int max = Math.max(w, h);
        if (max <= MAX_EDGE) return bmp;
        float scale = MAX_EDGE / (float) max;
        int nw = Math.max(1, Math.round(w * scale));
        int nh = Math.max(1, Math.round(h * scale));
        try {
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true);
            return scaled == null ? bmp : scaled;
        } catch (RuntimeException e) {
            return bmp;
        }
    }

    private static int pickDominant(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        int lowerStartY = Math.round(h * LOWER_START_RATIO);

        // 量化直方图：key = (qr << 10) | (qg << 5) | qb，每通道 24 档映射到 0–31 的 5bit 槽位存桶。
        // 每桶累计加权票数与加权 RGB 和，最后取均值色参与打分。
        java.util.HashMap<Integer, Bucket> buckets = new java.util.HashMap<>();
        for (int y = 0; y < h; y++) {
            double weight = y >= lowerStartY ? LOWER_WEIGHT : 1.0d;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int pixel = pixels[row + x];
                if (Color.alpha(pixel) < MIN_ALPHA) continue;
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                int key = (quantKey(r) << 10) | (quantKey(g) << 5) | quantKey(b);
                Bucket bucket = buckets.get(key);
                if (bucket == null) {
                    bucket = new Bucket();
                    buckets.put(key, bucket);
                }
                bucket.weight += weight;
                bucket.rSum += r * weight;
                bucket.gSum += g * weight;
                bucket.bSum += b * weight;
            }
        }
        if (buckets.isEmpty()) return FALLBACK_COLOR;

        int fallbackColor = FALLBACK_COLOR;
        double fallbackVotes = -1;
        float[] hsv = new float[3];
        // GZ 主路：先按单色打分，再按色相分桶汇总，取最高桶内最高分（CoverAmbientKt 函数 e/l）。
        double[] hueBucketTotals = new double[12];
        int[] hueBucketBestColor = new int[12];
        double[] hueBucketBestScore = new double[12];
        for (int i = 0; i < 12; i++) hueBucketBestScore[i] = -1;
        boolean anyCandidate = false;
        for (Bucket bucket : buckets.values()) {
            if (bucket.weight <= 0) continue;
            int r = clamp255(Math.round((float) (bucket.rSum / bucket.weight)));
            int g = clamp255(Math.round((float) (bucket.gSum / bucket.weight)));
            int b = clamp255(Math.round((float) (bucket.bSum / bucket.weight)));
            int color = Color.rgb(r, g, b);
            if (bucket.weight > fallbackVotes) {
                fallbackVotes = bucket.weight;
                fallbackColor = color;
            }
            Color.colorToHSV(color, hsv);
            float hue = hsv[0];
            float saturation = hsv[1];
            float value = hsv[2];
            if (value < MIN_VALUE || value > MAX_VALUE) continue;
            if (saturation < MIN_SATURATION) continue;
            // GZ 主路打分：计数 ×(S≥0.12?1:0.6) ×暖色相降权（暖色相且 S/V 落区间时 ×0.3）。
            double score = bucket.weight * (saturation >= SATURATION_FULL_THRESHOLD ? 1.0d : 0.6d);
            boolean warm = hue >= WARM_HUE_MIN && hue <= WARM_HUE_MAX
                    && saturation >= WARM_SAT_MIN && saturation <= WARM_SAT_MAX
                    && value >= WARM_VAL_MIN && value <= WARM_VAL_MAX;
            if (warm) score *= WARM_PENALTY;
            int hueBucket = hueBucketIndex(hue);
            hueBucketTotals[hueBucket] += score;
            anyCandidate = true;
            if (score > hueBucketBestScore[hueBucket]) {
                hueBucketBestScore[hueBucket] = score;
                hueBucketBestColor[hueBucket] = color;
            }
        }
        if (anyCandidate) {
            int bestBucket = -1;
            double bestTotal = -1;
            for (int i = 0; i < 12; i++) {
                if (hueBucketBestScore[i] < 0) continue;
                if (hueBucketTotals[i] > bestTotal) {
                    bestTotal = hueBucketTotals[i];
                    bestBucket = i;
                }
            }
            if (bestBucket >= 0) return hueBucketBestColor[bestBucket];
        }
        // 全部色桶都被 HSV 过滤掉时，退回票数最高的均值色而不是兜底灰（图本身有内容）。
        return fallbackColor;
    }

    /** GZ 色相分桶（CoverAmbientKt 函数 l）：((hue＋15)/30) 取整对 12 取模，每桶 30°、偏移 15°。 */
    private static int hueBucketIndex(float hueDegrees) {
        return (((int) ((hueDegrees + 15f) / 30f)) % 12 + 12) % 12;
    }

    /**
     * 主色可用化调整（GZ 函数 d，CoverAmbientKt:1101-1108，HSV 口径）：
     * S＜0.06 时 S 清零，否则 S＝clamp(S×1.05＋0.02, 0.16, 0.8)；V＝clamp(V, 0.38, 0.72)。色相与 alpha 不动。
     */
    public static int toneAdjust(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (hsv[1] < 0.06f) hsv[1] = 0f;
        else hsv[1] = clampFloat(hsv[1] * 1.05f + 0.02f, 0.16f, 0.8f);
        hsv[2] = clampFloat(hsv[2], 0.38f, 0.72f);
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int quantKey(int channel) {
        int level = channel * QUANT_LEVELS / 256; // 0..23
        return level * 31 / (QUANT_LEVELS - 1); // 映射到 5bit 桶位 0..31，避免相邻档撞桶
    }

    private static int mixToBlack(int color, float mix) {
        float keep = 1f - mix;
        int r = clamp255(Math.round(Color.red(color) * keep));
        int g = clamp255(Math.round(Color.green(color) * keep));
        int b = clamp255(Math.round(Color.blue(color) * keep));
        return Color.rgb(r, g, b);
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static final class Bucket {
        double weight;
        double rSum;
        double gSum;
        double bSum;
    }
}
