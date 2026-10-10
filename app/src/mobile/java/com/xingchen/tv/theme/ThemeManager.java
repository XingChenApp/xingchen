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
    private static android.graphics.drawable.Drawable sCachedWallpaper = null;
    private static String sCachedKey = "";

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

    public void setUiStyle(android.content.Context ctx, String style) {
        theme.uiStyle = style;
        theme.save(ctx);
        applyAll();
    }

    public void setWallpaper(android.content.Context ctx, String type, String value) {
        theme.wallpaperType = type;
        theme.wallpaperValue = value;
        theme.save(ctx);
        // Invalidate wallpaper cache: local/url reuse fixed filenames, so the
        // cache key (type:value) would otherwise match the stale drawable.
        sCachedKey = "";
        sCachedWallpaper = null;
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
        try {
            activity.getWindow().getDecorView().post(new Runnable() {
                @Override public void run() {
                    doApply(activity);
                }
            });
        } catch (Exception e) { flog("ERROR apply failed: " + e); }
    }

    private void doApply(Activity activity) {
        try {
            flog("doApply: activity=" + activity.getClass().getSimpleName());
            if (theme == null) theme = XingChenTheme.load(activity);
            android.graphics.drawable.Drawable dw = getWallpaperDrawable(activity);
            flog("doApply: setting window background, drawable=" + (dw != null));
            try {
                if (dw != null) {
                    activity.getWindow().setBackgroundDrawable(dw);
                } else {
                    activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
                }
                
            } catch (Exception e) {
                flog("ERROR setting window bg: " + e);
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

    private android.graphics.drawable.Drawable getWallpaperDrawable(Activity activity) {
        try {
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
            // Check cache first - avoids re-decoding on every page switch (black flash fix)
            if (cacheKey.equals(sCachedKey) && sCachedWallpaper != null) {
                flog("getWallpaperDrawable: cache HIT for " + cacheKey);
                return sCachedWallpaper;
            }
            flog("getWallpaperDrawable: cache MISS for " + cacheKey + ", decoding...");
            flog("getWallpaperDrawable: type=" + type + ", value=" + value);
            if (XingChenTheme.WP_COLOR.equals(type)) {
                try {
                    int color = android.graphics.Color.parseColor(value);
                    android.graphics.drawable.ColorDrawable cd = new android.graphics.drawable.ColorDrawable(color);
                    sCachedWallpaper = cd;
                    sCachedKey = cacheKey;
                    return cd;
                } catch (Exception e) {
                    return null;
                }
            } else if (XingChenTheme.WP_LOCAL.equals(type)) {
                try {
                    android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(value);
                    if (bm != null) {
                        flog("getWallpaperDrawable: local bitmap loaded, " + bm.getWidth() + "x" + bm.getHeight());
                        if (blur) bm = WallpaperBlur.blurredWallpaper(bm);
                        android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(activity.getResources(), bm);
                        sCachedWallpaper = bd;
                        sCachedKey = cacheKey;
                        return bd;
                    } else {
                        flog("getWallpaperDrawable: local decode failed, fallback to builtin");
                    }
                } catch (Exception e) {
                    flog("ERROR local decode: " + e);
                }
            } else if (XingChenTheme.WP_URL.equals(type)) {
                try {
                    android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(value);
                    if (bm != null) {
                        if (blur) bm = WallpaperBlur.blurredWallpaper(bm);
                        android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(activity.getResources(), bm);
                        sCachedWallpaper = bd;
                        sCachedKey = cacheKey;
                        return bd;
                    }
                } catch (Exception e) {}
            }
            boolean preBlurred = blur && "shanjian".equals(value);
            int id = getBuiltinRes(activity, value, blur);
            flog("getWallpaperDrawable: builtin id=" + id);
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
                    flog("getWallpaperDrawable: decoding with sample=" + sample + " for " + opts.outWidth + "x" + opts.outHeight);
                    android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeResource(activity.getResources(), id, opts);
                    if (bm != null) {
                        if (blur && !preBlurred) bm = WallpaperBlur.blurredWallpaper(bm);
                        android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(activity.getResources(), bm);
                        bd.setTintList(null);
                        flog("getWallpaperDrawable: builtin bitmap decoded, " + bm.getWidth() + "x" + bm.getHeight() + ", caching");
                        sCachedWallpaper = bd;
                        sCachedKey = cacheKey;
                        return bd;
                    }
                } catch (Exception e) {
                    flog("ERROR builtin decode: " + e);
                }
                android.graphics.drawable.Drawable fallback = activity.getResources().getDrawable(id, null);
                if (blur && !preBlurred) {
                    android.graphics.Bitmap fbm = drawableToBitmap(activity, fallback);
                    if (fbm != null) {
                        fallback = new android.graphics.drawable.BitmapDrawable(activity.getResources(), WallpaperBlur.blurredWallpaper(fbm));
                    }
                }
                sCachedWallpaper = fallback;
                sCachedKey = cacheKey;
                return fallback;
            }
            return null;
        } catch (Exception e) {
            flog("ERROR getWallpaperDrawable: " + e);
            return null;
        }
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