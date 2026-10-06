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
        } catch (Exception e) { flog("ERROR doApply failed: " + e); }
    }

    private android.graphics.drawable.Drawable getWallpaperDrawable(Activity activity) {
        try {
            String type = theme.wallpaperType;
            String value = theme.wallpaperValue;
            flog("getWallpaperDrawable: type=" + type + ", value=" + value);
            if (XingChenTheme.WP_COLOR.equals(type)) {
                try {
                    int color = android.graphics.Color.parseColor(value);
                    return new android.graphics.drawable.ColorDrawable(color);
                } catch (Exception e) {
                    return null;
                }
            } else if (XingChenTheme.WP_LOCAL.equals(type)) {
                try {
                    android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(value);
                    if (bm != null) {
                        flog("getWallpaperDrawable: local bitmap loaded, " + bm.getWidth() + "x" + bm.getHeight());
                        return new android.graphics.drawable.BitmapDrawable(activity.getResources(), bm);
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
                        return new android.graphics.drawable.BitmapDrawable(activity.getResources(), bm);
                    }
                } catch (Exception e) {}
            }
            int id = getBuiltinRes(activity, value);
            flog("getWallpaperDrawable: builtin id=" + id);
            if (id != 0) {
                return activity.getResources().getDrawable(id, null);
            }
            return null;
        } catch (Exception e) {
            flog("ERROR getWallpaperDrawable: " + e);
            return null;
        }
    }

    private int getBuiltinRes(android.content.Context ctx, String name) {
        try {
            if ("shanjian".equals(name)) {
                return ctx.getResources().getIdentifier("poster_shanjian_blur_v2", "drawable", ctx.getPackageName());
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
