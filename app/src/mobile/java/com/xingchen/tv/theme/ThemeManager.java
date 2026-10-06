package com.xingchen.tv.theme;

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
        applyAll();
    }

    private void applyAll() {
        try {
            if (currentActivity != null) apply(currentActivity);
        } catch (Exception e) { flog("ERROR applyAll failed: " + e); }
    }
    private static android.app.Activity currentActivity;

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
            flog( "doApply: activity=" + activity.getClass().getSimpleName());
            if (theme == null) theme = XingChenTheme.load(activity);
            ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            ensureWallpaperLayer(activity, decor);
            // Keep decor transparent so wallpaper shows, don't set beige
            makeTransparent(decor);
        } catch (Exception e) { flog("ERROR doApply failed: " + e); }
    }

    private void ensureWallpaperLayer(Activity activity, ViewGroup decor) {
        try {
            View existing = decor.findViewWithTag("xc_wallpaper");
            ImageView iv;
            if (existing instanceof ImageView) {
                flog( "ensureWallpaperLayer: found existing ImageView");
                iv = (ImageView) existing;
            } else {
                flog( "ensureWallpaperLayer: creating new ImageView");
                iv = new ImageView(activity);
                iv.setTag("xc_wallpaper");
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                decor.addView(iv, 0, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
                flog( "ensureWallpaperLayer: ImageView added to decor, childCount=" + decor.getChildCount());
            }
            iv.setVisibility(View.VISIBLE);
            setWallpaperDrawable(activity, iv);
        } catch (Exception e) { flog("ERROR ensureWallpaperLayer failed: " + e); }
    }

    private void setWallpaperDrawable(Activity activity, ImageView iv) {
        try {
            String type = theme.wallpaperType;
            String value = theme.wallpaperValue;
            flog( "setWallpaperDrawable: type=" + type + ", value=" + value);
            if (XingChenTheme.WP_COLOR.equals(type)) {
                try {
                    iv.setImageDrawable(null);
                    iv.setBackgroundColor(android.graphics.Color.parseColor(value));
                } catch (Exception e) {
                    iv.setBackgroundColor(0x00000000);
                    iv.setImageResource(getBuiltinRes(activity, "shanjian"));
                }
            } else if (XingChenTheme.WP_LOCAL.equals(type)) {
                try {
                    android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(value);
                    if (bm != null) {
                        iv.setImageBitmap(bm);
                        iv.setBackgroundColor(0x00000000);
                    } else {
                        iv.setImageResource(getBuiltinRes(activity, "shanjian"));
                    }
                } catch (Exception e) {
                    iv.setImageResource(getBuiltinRes(activity, "shanjian"));
                }
            } else if (XingChenTheme.WP_URL.equals(type)) {
                try {
                    try {
                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(value);
                        if (bmp != null) iv.setImageBitmap(bmp);
                    } catch (Exception e) {};
                    iv.setBackgroundColor(0x00000000);
                } catch (Exception e) {
                    iv.setImageResource(getBuiltinRes(activity, "shanjian"));
                }
            } else {
                iv.setBackgroundColor(0x00000000);
                int resId = getBuiltinRes(activity, value);
                flog( "setWallpaperDrawable: builtin resId=" + resId + " for value=" + value);
                iv.setImageResource(resId);
            }
        } catch (Exception e) { flog("ERROR setWallpaperDrawable failed: " + e); }
    }

    private int getBuiltinRes(android.content.Context ctx, String name) {
        try {
            if ("shanjian".equals(name)) {
                return ctx.getResources().getIdentifier("poster_shanjian_blur", "drawable", ctx.getPackageName());
            }
            return ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
        } catch (Exception e) {
            flog("ERROR getBuiltinRes failed for name: " + e);
            return 0;
        }
    }

    private void makeTransparent(ViewGroup root) {
        try {
            int cleared = 0;
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if ("xc_wallpaper".equals(child.getTag())) continue;
                String cls = child.getClass().getName();
                if (cls.contains("BottomNavigationView")) continue;
                // Clear background for ALL views, not just ViewGroups
                try {
                    Drawable bg = child.getBackground();
                    if (bg != null) {
                        child.setBackgroundColor(0x00000000);
                        cleared++;
                    }
                } catch (Exception e) {}
                // Recurse into ALL ViewGroups (including ScrollView, RecyclerView, ListView)
                if (child instanceof ViewGroup) {
                    makeTransparent((ViewGroup) child);
                }
            }
            if (cleared > 0) flog("makeTransparent: cleared " + cleared + " backgrounds in " + root.getClass().getSimpleName());
        } catch (Exception e) { flog("ERROR makeTransparent failed: " + e); }
    }
}
