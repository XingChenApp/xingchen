package com.fongmi.android.tv.ui.hk;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 8.83 GlideUtil 的逐行转写（GlideUtil.smali）：
 * loadPicDrawable 两重载、$3/$4 Target、MyCustomTarget、smartCompress 两重载、
 * ImgUtil.compressImage(Resources, Drawable, int, int)。
 * getGlideUrl：首步 hiker://images/ 映射走 HkImageUrlMap（ImageUrlMapEnum 全量转写），
 * 其余分支委托工程内已按同方法逐段移植审计的 ImgUtil.getUrl(picUrl, ruleUrl)。
 */
public final class HkGlideUtil {

    private static final ExecutorService HEAVY = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private HkGlideUtil() {
    }

    /**
     * 对应原版 GlideUtil.getGlideUrl(pageUrl, picUrl)（参数顺序与原版一致）：
     * 首步照原版查 ImageUrlMapEnum.getIdByUrl(picUrl)，命中（id>0）直接返回 Integer 资源 id；
     * 其余分支（#色块/hiker://files/相对路径/http 后缀组 GlideUrl）由 ImgUtil.getUrl(picUrl, ruleUrl) 承担，
     * 该实现已按 8.83 同方法逐段移植审计。
     */
    public static Object getGlideUrl(String pageUrl, String picUrl) {
        int id = HkImageUrlMap.getIdByUrl(com.fongmi.android.tv.App.get(), picUrl);
        if (id > 0) return Integer.valueOf(id);
        return ImgUtil.getUrl(picUrl, pageUrl);
    }

    /** 对应原版 ScreenUtil.getScreenMin：默认显示宽高取小。 */
    static int getScreenMin(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        return Math.min(metrics.widthPixels, metrics.heightPixels);
    }

    /** 照抄原版 ImgUtil.compressImage(Resources, Drawable, int, int)（ImgUtil.smali:140）。 */
    static Drawable compressImage(Resources res, Drawable drawable, int w, int h) {
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap != null) {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, w, h, false);
                if (scaled != bitmap) {
                    drawable = new BitmapDrawable(res, scaled);
                }
            }
        }
        return drawable;
    }

    /** 照抄原版 GlideUtil.smartCompress(Context, Bitmap)（smali:1274）。 */
    public static int[] smartCompress(Context context, Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int byteCount = bitmap.getByteCount();
        float scale = (float) getScreenMin(context) / (float) w;
        if (w <= getScreenMin(context)) {
            if (byteCount > 0x3200000) {
                w = (int) ((double) (float) w / 1.5);
                h = (int) ((double) (float) h / 1.5);
            }
        } else if (byteCount >= 0xa00000) {
            float est = byteCount / scale / scale;
            if (est < 5242880.0f) {
                w = getScreenMin(context);
                h = (int) ((float) bitmap.getHeight() / (float) bitmap.getWidth() * (float) w);
            } else if (byteCount > 0x6400000) {
                w = (int) ((double) (float) w / 4.5);
                h = (int) ((double) (float) h / 4.5);
            } else if (byteCount > 0x3200000) {
                w = (int) ((double) (float) w / 3.3);
                h = (int) ((double) (float) h / 3.3);
            } else if (byteCount > 0x1400000) {
                w = (int) ((float) w / 2.0f);
                h = (int) ((float) h / 2.0f);
            }
        }
        if (w > getScreenMin(context)) {
            w = getScreenMin(context);
            h = (int) ((float) bitmap.getHeight() / (float) bitmap.getWidth() * (float) w);
        }
        if (w < 1) w = 1;
        if (h < 1) h = 1;
        return new int[]{w, h};
    }

    /** 照抄原版 GlideUtil.smartCompress(Context, Drawable)（smali:1476）：内在尺寸 + Bitmap 字节数同逻辑。 */
    public static int[] smartCompress(Context context, Drawable drawable) {
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        // 无内在尺寸（矢量/色块类 drawable 可为 0/-1）时除法会出 NaN/0、下游缩放必崩：原样返回，调用方走不压缩直摆
        if (w <= 0 || h <= 0) return new int[]{w, h};
        int byteCount = 0;
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap != null) byteCount = bitmap.getByteCount();
        }
        float scale = (float) getScreenMin(context) / (float) w;
        if (w <= getScreenMin(context)) {
            if (byteCount > 0x3200000) {
                w = (int) ((double) (float) w / 1.5);
                h = (int) ((double) (float) h / 1.5);
            }
        } else if (byteCount >= 0xa00000) {
            float est = byteCount / scale / scale;
            if (est < 5242880.0f) {
                int iw = drawable.getIntrinsicWidth();
                int ih = drawable.getIntrinsicHeight();
                w = getScreenMin(context);
                h = (int) ((float) ih / (float) iw * (float) w);
            } else if (byteCount > 0x6400000) {
                w = (int) ((double) (float) w / 4.5);
                h = (int) ((double) (float) h / 4.5);
            } else if (byteCount > 0x3200000) {
                w = (int) ((double) (float) w / 3.3);
                h = (int) ((double) (float) h / 3.3);
            } else if (byteCount > 0x1400000) {
                w = (int) ((float) w / 2.0f);
                h = (int) ((float) h / 2.0f);
            }
        }
        if (w > getScreenMin(context)) {
            int iw = drawable.getIntrinsicWidth();
            int ih = drawable.getIntrinsicHeight();
            w = getScreenMin(context);
            h = (int) ((float) ih / (float) iw * (float) w);
        }
        if (w < 1) w = 1;
        if (h < 1) h = 1;
        return new int[]{w, h};
    }

    /** 照抄原版 GlideUtil$MyCustomTarget（smali 内部类）。 */
    static abstract class HkTarget extends CustomTarget<Drawable> {
        protected final Context context;
        protected final ImageView imageView;

        HkTarget(Context context, ImageView imageView) {
            this.context = context;
            this.imageView = imageView;
        }

        @Override
        public void onLoadStarted(Drawable placeholder) {
            imageView.setImageDrawable(placeholder);
        }

        @Override
        public void onLoadCleared(Drawable placeholder) {
            try {
                if (placeholder instanceof GifDrawable) {
                    ((GifDrawable) placeholder).stop();
                }
            } catch (Exception ignored) {
            }
        }
    }

    /** 照抄原版 GlideUtil.loadPicDrawable(Context, ImageView, Object, RequestOptions)（smali:1101）+ $3 Target。 */
    public static void loadPicDrawable(Context context, ImageView imageView, Object url, RequestOptions options) {
        if (url == null || "".equals(url)) {
            imageView.setImageDrawable(null);
            return;
        }
        final Long ticket = System.currentTimeMillis();
        imageView.setTag(R.id.custom_tag, ticket);
        Glide.with(context).asDrawable().apply(options).load(url).into(new HkTarget(context, imageView) {
            @Override
            public void onResourceReady(Drawable resource, Transition<? super Drawable> transition) {
                if (ticket != imageView.getTag(R.id.custom_tag)) return;
                if (imageView.getWidth() != resource.getIntrinsicWidth()) {
                    try {
                        int[] wh = smartCompress(context, resource);
                        final int fw = wh[0], fh = wh[1];
                        HEAVY.execute(() -> {
                            Drawable compressed = compressImage(context.getResources(), resource, fw, fh);
                            MAIN.post(() -> {
                                if (ticket != imageView.getTag(R.id.custom_tag)) return;
                                loadPicDrawable(context, imageView, compressed, options, fw, fh);
                            });
                        });
                        return;
                    } catch (Exception ignored) {
                    }
                }
                try {
                    if (resource instanceof GifDrawable) {
                        GifDrawable gif = (GifDrawable) resource;
                        gif.setLoopCount(-1);
                        gif.start();
                    }
                } catch (Exception ignored) {
                }
                imageView.setImageDrawable(resource);
            }
        });
    }

    /** 照抄原版 GlideUtil.loadFullPicDrawable 4 参/5 参（smali:903/920 段）+ GlideUtil$1 Target。 */
    public static void loadFullPicDrawable(Context context, ImageView imageView, Object url, RequestOptions options) {
        loadFullPicDrawable(context, imageView, url, options, null);
    }

    public static void loadFullPicDrawable(Context context, ImageView imageView, Object url, RequestOptions options,
                                           java.util.function.Consumer<String> tagListener) {
        if (url == null || "".equals(url)) {
            imageView.setImageDrawable(null);
            return;
        }
        final Long ticket = System.currentTimeMillis();
        imageView.setTag(R.id.custom_tag, ticket);
        Glide.with(context).asDrawable().apply(options).load(url).into(new HkTarget(context, imageView) {
            @Override
            public void onResourceReady(Drawable resource, Transition<? super Drawable> transition) {
                if (ticket != imageView.getTag(R.id.custom_tag)) return;
                int[] wh = smartCompress(context, resource);
                if (wh[0] != resource.getIntrinsicWidth()) {
                    imageView.setTag(resource.getIntrinsicWidth() + "×" + resource.getIntrinsicHeight()
                            + "/" + wh[0] + "×" + wh[1]);
                    Object tag = imageView.getTag();
                    if (tagListener != null && tag instanceof String) tagListener.accept((String) tag);
                    final int fw = wh[0], fh = wh[1];
                    HEAVY.execute(() -> {
                        Drawable compressed;
                        try {
                            compressed = compressImage(context.getResources(), resource, fw, fh);
                        } catch (Exception e) {
                            // 压缩失败不许让图静默永不显示：回主线程把原图直接摆上
                            MAIN.post(() -> {
                                if (ticket != imageView.getTag(R.id.custom_tag)) return;
                                imageView.setTag(resource.getIntrinsicWidth() + "×" + resource.getIntrinsicHeight());
                                imageView.setPadding(0, 0, 0, 0);
                                imageView.setImageDrawable(resource);
                            });
                            return;
                        }
                        MAIN.post(() -> {
                            if (ticket != imageView.getTag(R.id.custom_tag)) return;
                            loadFullPicDrawable(context, imageView, compressed, options, fw, fh);
                        });
                    });
                } else {
                    try {
                        if (resource instanceof GifDrawable) {
                            GifDrawable gif = (GifDrawable) resource;
                            gif.setLoopCount(-1);
                            gif.start();
                        }
                    } catch (Exception ignored) {
                    }
                    imageView.setTag(resource.getIntrinsicWidth() + "×" + resource.getIntrinsicHeight());
                    imageView.setPadding(0, 0, 0, 0);
                    imageView.setImageDrawable(resource);
                }
            }
        });
    }

    /** 照抄原版 GlideUtil.loadFullPicDrawable(Context, ImageView, Object, RequestOptions, int, int) + GlideUtil$2 Target。 */
    public static void loadFullPicDrawable(Context context, ImageView imageView, Object url, RequestOptions options, int w, int h) {
        if (url == null || "".equals(url)) {
            imageView.setImageDrawable(null);
            return;
        }
        final Long ticket = System.currentTimeMillis();
        imageView.setTag(R.id.custom_tag, ticket);
        RequestOptions forced = options.skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.ALL);
        Glide.with(context).asDrawable().override(w, h).apply(forced).load(url).into(new HkTarget(context, imageView) {
            @Override
            public void onResourceReady(Drawable resource, Transition<? super Drawable> transition) {
                if (ticket != imageView.getTag(R.id.custom_tag)) return;
                try {
                    if (resource instanceof GifDrawable) {
                        GifDrawable gif = (GifDrawable) resource;
                        gif.setLoopCount(-1);
                        gif.start();
                    }
                } catch (Exception ignored) {
                }
                imageView.setPadding(0, 0, 0, 0);
                imageView.setImageDrawable(resource);
                imageView.setAdjustViewBounds(true);
            }
        });
    }

    /** 照抄原版 GlideUtil.loadPicDrawable(Context, ImageView, Object, RequestOptions, int, int)（smali:420 段）。 */
    public static void loadPicDrawable(Context context, ImageView imageView, Object url, RequestOptions options, int w, int h) {
        if (url == null || "".equals(url)) {
            imageView.setImageDrawable(null);
            return;
        }
        final Long ticket = System.currentTimeMillis();
        imageView.setTag(R.id.custom_tag, ticket);
        RequestOptions forced = options.skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.ALL);
        Glide.with(context).asDrawable().override(w, h).apply(forced).load(url).into(new HkTarget(context, imageView) {
            @Override
            public void onResourceReady(Drawable resource, Transition<? super Drawable> transition) {
                if (ticket != imageView.getTag(R.id.custom_tag)) return;
                try {
                    if (resource instanceof GifDrawable) {
                        GifDrawable gif = (GifDrawable) resource;
                        gif.setLoopCount(-1);
                        gif.start();
                    }
                } catch (Exception ignored) {
                }
                imageView.setImageDrawable(resource);
            }
        });
    }
}
