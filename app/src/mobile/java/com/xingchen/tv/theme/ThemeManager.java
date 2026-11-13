package com.xingchen.tv.theme;
import com.fongmi.android.tv.R;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ThemeManager {
    // 壁纸缓存只存「图源」（解码后的位图 / 纯色值），不存 Drawable 本体：
    // 每个窗口贴图时按图源新建一份 Drawable，避免全 App 共用同一个
    // Drawable 实例时跨窗口 detach/回调互相污染（一处 detach 满屏黑）。
    private static android.graphics.Bitmap sCachedBitmap = null;
    private static Integer sCachedColor = null;
    private static String sCachedKey = "";
    // 上一张成功贴出的壁纸图源：真图拿不到时兜底用，永不落透明黑。
    private static android.graphics.Bitmap sLastBitmap = null;
    private static Integer sLastColor = null;
    // 每个窗口上次成功贴上的壁纸键（与缓存键同口径）。回前台重贴时新图与
    // 窗口已贴的同键＝同一张图，此时绝不许再走交叉淡变：新旧两层同图叠淡
    // 中段合成不透明度掉到约 0.75、透出窗口黑底，整屏暗闪一下（他真机
    // 「每次从后台回首页闪一下」2026-10-26 的定罪点）。淡变只留给壁纸真换时。
    private static final java.util.WeakHashMap<android.view.Window, String> sAppliedKeyByWindow =
            new java.util.WeakHashMap<>();
    // 连上一张都没有时的深色占位（深蓝黑，与阅读底色同族）。
    private static final int PLACEHOLDER_COLOR = 0xFF0E1622;

    private static ThemeManager instance;
    private XingChenTheme theme;
    private static final String TAG = "XC_WALLPAPER";
    private static void flog(String msg) {
        Log.d(TAG, msg);
        try {
            File dir = new File("/sdcard/XingChen");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "wallpaper.log");
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
            String line = sdf.format(new Date()) + " " + msg + "\n";
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.close();
        } catch (IOException e) {
            Log.e(TAG, "flog failed", e);
        }
    }

    private ThemeManager() {}

    public static synchronized ThemeManager get() {
        if (instance == null) instance = new ThemeManager();
        return instance;
    }

    public static void init(Application app) {
        get().theme = XingChenTheme.load(app);
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) { currentActivity = a; get().apply(a); }
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    public XingChenTheme getTheme() {
        return theme;
    }

    public void refresh(android.content.Context ctx) {
        theme = XingChenTheme.load(ctx);
    }

    public void setWallpaper(android.content.Context ctx, String type, String value) {
        theme.wallpaperType = type;
        theme.wallpaperValue = value;
        theme.save(ctx);
        // Invalidate wallpaper cache: local/url reuse fixed filenames, so the
        // cache key (type:value) would otherwise match the stale drawable.
        sCachedKey = "";
        sCachedBitmap = null;
        sCachedColor = null;
        // 已贴登记一并清：换壁纸后第一贴必须走淡变（真切换的定案行为）。
        sAppliedKeyByWindow.clear();
        applyAll();
    }

    private void applyAll() {
        try {
            if (currentActivity != null) apply(currentActivity);
        } catch (Exception e) { flog("ERROR applyAll failed: " + e); }
    }
    private static android.app.Activity currentActivity;

    public void setTheme(XingChenTheme theme) {
        this.theme = theme;
    }

    public void apply(Activity activity) {
        // 第一贴（同步）：命中缓存或有上一张图时先把窗口底贴上，不等 post。
        // 以前只靠 post 的异步一贴，那次失败被吞掉首页就持久黑；
        // 同步这步只贴已解码的缓存图，绝不在此新开大图解码（89MB 教训）。
        try {
            applyCachedNow(activity);
        } catch (Exception e) {
            Log.e(TAG, "sync wallpaper apply failed", e);
            flog("ERROR sync apply failed: " + e);
        }
        // 第二贴（异步）：全量校验/解码，在其后覆盖同步贴的结果。
        try {
            activity.getWindow().getDecorView().post(new Runnable() {
                @Override public void run() {
                    doApply(activity);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "async wallpaper apply failed", e);
            flog("ERROR apply failed: " + e);
        }
    }

    private boolean isHkPage(Activity activity) {
        String actName = activity.getClass().getName();
        return actName.endsWith(".ui.hk.HkListActivity")
                || actName.endsWith(".ui.hk.HkSearchActivity")
                || actName.endsWith(".ui.activity.HkPageActivity")
                || actName.endsWith(".ui.activity.HkRuleSearchActivity");
    }

    // 本管理器给窗口落过底的登记：占位兜底只许在窗口还没落过任何底时用，
    // 不许盖掉先前已落的真底（个别内置图转不成位图、不进缓存的那条支路）。
    private static final java.util.WeakHashMap<android.view.Window, Boolean> sBgTouched =
            new java.util.WeakHashMap<>();

    private void setWindowColorNow(Activity activity, int color) {
        try {
            activity.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(color));
            sBgTouched.put(activity.getWindow(), Boolean.TRUE);
        } catch (Exception ignored) {}
    }

    // 同步贴（apply 第一贴）：全类页面都在这一贴把窗口底落到定案值，不许等 post 的 doApply。
    // 此前海阔/音乐/播放页在这一贴被整段跳过、底色只靠异步 doApply 落，进页首帧与落底
    // 之间留空档＝他点名「进所有页都会闪」的共用病根之一（2026-10-26 全域进页闪普查）。
    // 普通页同步贴仍只用已有图源（当前缓存优先，其次上一张），不在此新开大图解码。
    private void applyCachedNow(Activity activity) {
        if (activity == null) return;
        if (isHkPage(activity)) {
            try {
                setWindowColorNow(activity, activity.getColor(com.fongmi.android.tv.R.color.hk_status_bar));
            } catch (Exception ignored) {}
            return;
        }
        if (theme == null) theme = XingChenTheme.load(activity);
        android.graphics.drawable.Drawable dw = null;
        boolean fromCache = false;
        if (currentCacheKey().equals(sCachedKey)) {
            dw = freshDrawableFromCache(activity);
            fromCache = dw != null;
        }
        if (dw == null) dw = freshDrawableFromLast(activity);
        if (dw != null) {
            activity.getWindow().setBackgroundDrawable(dw);
            sBgTouched.put(activity.getWindow(), Boolean.TRUE);
            // 贴的正是当前缓存键那一张：登记本窗口已贴键，其后 doApply 同键
            // 直贴不淡变（开页与回前台都不再对同图自淡一遍）。
            if (fromCache) sAppliedKeyByWindow.put(activity.getWindow(), currentCacheKey());
            flog("applyCachedNow: sync-pasted for " + activity.getClass().getSimpleName());
        } else if (!sBgTouched.containsKey(activity.getWindow())) {
            // 真图还没解出来：先同步落深色占位（与 doApply 兜底同色），不许让主题
            // 起始底留在首帧上；doApply 解出真图后按既有口径换上。
            setWindowColorNow(activity, PLACEHOLDER_COLOR);
            flog("applyCachedNow: placeholder for " + activity.getClass().getSimpleName());
        }
    }

    // 按当前缓存图源新建一份本窗口独立的 Drawable（位图本体可共享，状态不共享）。
    private android.graphics.drawable.Drawable freshDrawableFromCache(Activity activity) {
        if (sCachedColor != null) return new android.graphics.drawable.ColorDrawable(sCachedColor);
        if (sCachedBitmap != null && !sCachedBitmap.isRecycled()) {
            android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(activity.getResources(), sCachedBitmap);
            bd.setTintList(null);
            return bd;
        }
        return null;
    }

    private android.graphics.drawable.Drawable freshDrawableFromLast(Activity activity) {
        if (sLastColor != null) return new android.graphics.drawable.ColorDrawable(sLastColor);
        if (sLastBitmap != null && !sLastBitmap.isRecycled()) {
            android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(activity.getResources(), sLastBitmap);
            bd.setTintList(null);
            return bd;
        }
        return null;
    }

    // 兜底：上一张也没有就落深色占位，绝不落透明（透明＝黑）。
    private android.graphics.drawable.Drawable fallbackDrawable(Activity activity) {
        android.graphics.drawable.Drawable dw = freshDrawableFromLast(activity);
        if (dw != null) return dw;
        return new android.graphics.drawable.ColorDrawable(PLACEHOLDER_COLOR);
    }

    private String currentCacheKey() {
        String type = theme.wallpaperType;
        String value = theme.wallpaperValue;
        boolean blur = theme.wallpaperBlur;
        String cacheKey = type + ":" + value + (blur ? ":blur" : "");
        // Local/url wallpapers reuse fixed filenames; include lastModified so
        // picking a new image with the same path busts the cache.
        if ((XingChenTheme.WP_LOCAL.equals(type) || XingChenTheme.WP_URL.equals(type)) && value != null) {
            try {
                java.io.File f = new java.io.File(value);
                if (f.exists()) cacheKey += ":" + f.lastModified();
            } catch (Exception e) {}
        }
        return cacheKey;
    }

    private void doApply(Activity activity) {
        doApply(activity, false);
    }

    private void doApply(Activity activity, boolean isRetry) {
        try {
            flog("doApply: activity=" + activity.getClass().getSimpleName());
            // 海阔浅色页（列表页/搜索页/聚合页/规则搜索页）不跟主 App 主题走：不铺星辰壁纸，
            // 窗口底用动态背景顶色兜底；状态栏各页自己管（透明＋深色图标），这里不再强制不透明，
            // 否则会盖住 decor 注入的动态背景，状态栏区又和页面断成两截
            if (isHkPage(activity)) {
                try {
                    activity.getWindow().setBackgroundDrawable(
                            new android.graphics.drawable.ColorDrawable(activity.getColor(com.fongmi.android.tv.R.color.hk_status_bar)));
                    sBgTouched.put(activity.getWindow(), Boolean.TRUE);
                } catch (Exception ignored) {}
                return;
            }
            if (theme == null) theme = XingChenTheme.load(activity);
            android.graphics.drawable.Drawable dw = obtainWallpaper(activity);
            boolean real = dw != null;
            // 真图拿不到也绝不落透明黑：上一张优先，没有就深色占位。
            if (!real) dw = fallbackDrawable(activity);
            flog("doApply: setting window background, real=" + real);
            try {
                // 真图时图源键就是当前缓存键（命中缓存或刚按此键解码入缓存）；
                // 兜底图（上一张/占位）传 null：不登记、不冒充当前壁纸键。
                setWindowBackgroundWithFade(activity, dw, real ? currentCacheKey() : null);
            } catch (Exception e) {
                Log.e(TAG, "setting window background failed", e);
                flog("ERROR setting window bg: " + e);
            }
            if (!real && !isRetry) {
                // 已落兜底，再约一次延迟重试；下一次 onActivityResumed 也会再走 apply。
                try {
                    activity.getWindow().getDecorView().postDelayed(new Runnable() {
                        @Override public void run() {
                            if (!activity.isFinishing() && !activity.isDestroyed()) doApply(activity, true);
                        }
                    }, 1500);
                    flog("doApply: wallpaper missing, retry scheduled");
                } catch (Exception e) {
                    Log.e(TAG, "schedule wallpaper retry failed", e);
                    flog("ERROR schedule retry: " + e);
                }
            }
            try {
                android.view.View decor = activity.getWindow().getDecorView();
                flog("DIAG DecorView: class=" + decor.getClass().getSimpleName() + ", bg=" + bgDesc(decor.getBackground()));
                if (decor instanceof android.view.ViewGroup) {
                    android.view.ViewGroup dg = (android.view.ViewGroup) decor;
                    for (int i = 0; i < dg.getChildCount() && i < 3; i++) {
                        android.view.View child = dg.getChildAt(i);
                        flog("DIAG Decor child " + i + ": class=" + child.getClass().getSimpleName() + ", bg=" + bgDesc(child.getBackground()));
                        if (child instanceof android.view.ViewGroup && ((android.view.ViewGroup) child).getChildCount() > 0) {
                            android.view.View grandchild = ((android.view.ViewGroup) child).getChildAt(0);
                            flog("DIAG   grandchild: class=" + grandchild.getClass().getSimpleName() + ", bg=" + bgDesc(grandchild.getBackground()));
                        }
                    }
                }
            } catch (Exception e) {
                flog("DIAG error: " + e);
            }
            // Clear opaque ColorDrawable cover views sitting directly in the DecorView
            // (e.g. a solid beige View that hides the window wallpaper). Only plain
            // Views are touched: ViewGroups (content) and ImageViews (wallpaper
            // layer) are left alone.
            try {
                android.view.View decor2 = activity.getWindow().getDecorView();
                if (decor2 instanceof android.view.ViewGroup) {
                    android.view.ViewGroup dg2 = (android.view.ViewGroup) decor2;
                    for (int i = 0; i < dg2.getChildCount(); i++) {
                        android.view.View child = dg2.getChildAt(i);
                        if (child == null) continue;
                        if (child instanceof android.view.ViewGroup) continue;
                        if (child instanceof android.widget.ImageView) continue;
                        if ("xc_wallpaper".equals(child.getTag())) continue;
                        android.graphics.drawable.Drawable bg = child.getBackground();
                        if (bg instanceof android.graphics.drawable.ColorDrawable) {
                            int color = ((android.graphics.drawable.ColorDrawable) bg).getColor();
                            if (android.graphics.Color.alpha(color) == 255) {
                                child.setBackground(null);
                                flog("Cleared opaque cover view: child " + i + " (" + child.getClass().getSimpleName() + ")");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                flog("ERROR clearing cover views: " + e);
            }
        } catch (Exception e) { flog("ERROR doApply failed: " + e); }
    }

    // 主题/壁纸切换淡变（统一 200ms）：窗口已有旧底时新旧底交叉淡化，不再硬换；
    // 首贴无旧底、或系统动画关时直接落，与防闪的同步贴口径不冲突。
    // 同图重贴直落（防闪定罪，2026-10-26）：新图源键与本窗口已贴键一致＝同一张
    // 图——回前台的无条件重贴正落此类。若仍造 TransitionDrawable 对同图交叉淡，
    // 中段两层各半透明、合成不透明度约 0.75，窗口黑底透出，整屏暗闪 200ms，
    // 且回前台 apply 跑两遍就闪两轮。淡变只在图源键真变了（换壁纸/换主题）时走。
    // 旧底若已是上一次淡变的 TransitionDrawable，只取其末层作旧底，避免逐次套娃。
    private void setWindowBackgroundWithFade(Activity activity, Drawable newBg, String sourceKey) {
        android.view.Window window = activity.getWindow();
        Drawable oldBg = window.getDecorView().getBackground();
        boolean unchanged = sourceKey != null && sourceKey.equals(sAppliedKeyByWindow.get(window));
        if (oldBg == null || newBg == null || unchanged
                || !com.fongmi.android.tv.utils.XcFadeTransition.isAnimEnabled(activity)) {
            window.setBackgroundDrawable(newBg);
            sBgTouched.put(window, Boolean.TRUE);
            if (sourceKey != null) sAppliedKeyByWindow.put(window, sourceKey);
            return;
        }
        if (oldBg instanceof android.graphics.drawable.TransitionDrawable) {
            android.graphics.drawable.TransitionDrawable prev =
                    (android.graphics.drawable.TransitionDrawable) oldBg;
            if (prev.getNumberOfLayers() > 0) oldBg = prev.getDrawable(prev.getNumberOfLayers() - 1);
        }
        android.graphics.drawable.TransitionDrawable td =
                new android.graphics.drawable.TransitionDrawable(new Drawable[]{oldBg, newBg});
        td.setCrossFadeEnabled(true);
        window.setBackgroundDrawable(td);
        sBgTouched.put(window, Boolean.TRUE);
        td.startTransition(200);
        if (sourceKey != null) sAppliedKeyByWindow.put(window, sourceKey);
    }

    // 拿到真壁纸时返回一份本窗口独立的 Drawable，并把图源存进缓存/上一张；
    // 拿不到（解码失败等）返回 null，由 doApply 落兜底并约重试。
    private android.graphics.drawable.Drawable obtainWallpaper(Activity activity) {
        try {
            String type = theme.wallpaperType;
            String value = theme.wallpaperValue;
            boolean blur = theme.wallpaperBlur;
            String cacheKey = currentCacheKey();
            // Check cache first - avoids re-decoding on every page switch (black flash fix)
            if (cacheKey.equals(sCachedKey)) {
                android.graphics.drawable.Drawable hit = freshDrawableFromCache(activity);
                if (hit != null) {
                    flog("obtainWallpaper: cache HIT for " + cacheKey);
                    return hit;
                }
            }
            flog("obtainWallpaper: cache MISS for " + cacheKey + ", decoding...");
            flog("obtainWallpaper: type=" + type + ", value=" + value);
            if (XingChenTheme.WP_COLOR.equals(type)) {
                try {
                    int color = android.graphics.Color.parseColor(value);
                    cacheColor(cacheKey, color);
                    return new android.graphics.drawable.ColorDrawable(color);
                } catch (Exception e) {
                    flog("ERROR color parse: " + e);
                    return null;
                }
            } else if (XingChenTheme.WP_LOCAL.equals(type) || XingChenTheme.WP_URL.equals(type)) {
                try {
                    android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(value);
                    if (bm != null) {
                        flog("obtainWallpaper: file bitmap loaded, " + bm.getWidth() + "x" + bm.getHeight());
                        if (blur) bm = WallpaperBlur.blurredWallpaper(bm);
                        cacheBitmap(cacheKey, bm);
                        return freshDrawableFromCache(activity);
                    } else {
                        flog("obtainWallpaper: file decode failed, fallback to builtin");
                    }
                } catch (Exception e) {
                    flog("ERROR file decode: " + e);
                }
            }
            boolean preBlurred = blur && "shanjian".equals(value);
            int id = getBuiltinRes(activity, value, blur);
            flog("obtainWallpaper: builtin id=" + id);
            if (id != 0) {
                try {
                    // Decode with sampling to avoid 89MB full-size bitmap (black flash fix)
                    android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    android.graphics.BitmapFactory.decodeResource(activity.getResources(), id, opts);
                    int screenW = activity.getResources().getDisplayMetrics().widthPixels;
                    int screenH = activity.getResources().getDisplayMetrics().heightPixels;
                    int sample = 1;
                    while ((opts.outWidth / sample) > screenW * 2 || (opts.outHeight / sample) > screenH * 2) {
                        sample *= 2;
                    }
                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = sample;
                    flog("obtainWallpaper: decoding with sample=" + sample + " for " + opts.outWidth + "x" + opts.outHeight);
                    android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeResource(activity.getResources(), id, opts);
                    if (bm != null) {
                        if (blur && !preBlurred) bm = WallpaperBlur.blurredWallpaper(bm);
                        flog("obtainWallpaper: builtin bitmap decoded, " + bm.getWidth() + "x" + bm.getHeight() + ", caching");
                        cacheBitmap(cacheKey, bm);
                        return freshDrawableFromCache(activity);
                    }
                } catch (Exception e) {
                    flog("ERROR builtin decode: " + e);
                }
                android.graphics.drawable.Drawable fallback = activity.getResources().getDrawable(id, null);
                if (fallback != null) {
                    android.graphics.Bitmap fbm = drawableToBitmap(activity, fallback);
                    if (fbm != null) {
                        if (blur && !preBlurred) fbm = WallpaperBlur.blurredWallpaper(fbm);
                        cacheBitmap(cacheKey, fbm);
                        return freshDrawableFromCache(activity);
                    }
                    // 转不成位图就直接用这一份（不进缓存），也好过落黑。
                    return fallback;
                }
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "obtainWallpaper failed", e);
            flog("ERROR obtainWallpaper: " + e);
            return null;
        }
    }

    private void cacheBitmap(String key, android.graphics.Bitmap bm) {
        sCachedBitmap = bm;
        sCachedColor = null;
        sCachedKey = key;
        sLastBitmap = bm;
        sLastColor = null;
    }

    private void cacheColor(String key, int color) {
        sCachedColor = color;
        sCachedBitmap = null;
        sCachedKey = key;
        sLastColor = color;
        sLastBitmap = null;
    }

    private android.graphics.Bitmap drawableToBitmap(Activity activity, android.graphics.drawable.Drawable d) {
        try {
            int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : activity.getResources().getDisplayMetrics().widthPixels;
            int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : activity.getResources().getDisplayMetrics().heightPixels;
            android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(bm);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            return bm;
        } catch (Exception e) {
            flog("ERROR drawableToBitmap: " + e);
            return null;
        }
    }

    private int getBuiltinRes(android.content.Context ctx, String name, boolean blur) {
        try {
            if ("shanjian".equals(name)) {
                // Blur OFF -> clear original; Blur ON -> pre-blurred soft version (moderate).
                // Runtime blur is skipped for the pre-blurred resource (no double blur).
                return blur ? R.drawable.poster_shanjian_blur_soft : R.drawable.poster_shanjian;
            }
            return ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
        } catch (Exception e) {
            flog("ERROR getBuiltinRes failed for name: " + e);
            return 0;
        }
    }

    private void makeTransparent(ViewGroup root) {
        try {
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if ("xc_wallpaper".equals(child.getTag())) continue;
                String cls = child.getClass().getName();
                if (cls.contains("BottomNavigationView")) continue;
                if (child instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) child;
                    try {
                        Drawable bg = vg.getBackground();
                        if (bg instanceof android.graphics.drawable.ColorDrawable) {
                            vg.setBackgroundColor(0x00000000);
                            flog("makeTransparent: cleared root bg in " + cls);
                        }
                    } catch (Exception e) {}
                    // Only go one level deep, do not touch nested cards
                }
            }
        } catch (Exception e) { flog("ERROR makeTransparent failed: " + e); }
    }

    private String bgDesc(android.graphics.drawable.Drawable bg) {
        if (bg == null) return "null";
        String cls = bg.getClass().getSimpleName();
        if (bg instanceof android.graphics.drawable.ColorDrawable) {
            int c = ((android.graphics.drawable.ColorDrawable) bg).getColor();
            return cls + "(color=#" + Integer.toHexString(c) + ")";
        }
        return cls;
    }
}