package com.xingchen.tv.theme;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class ThemeManager {
    private static ThemeManager instance;
    private XingChenTheme theme;

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
            @Override public void onActivityResumed(Activity a) { get().apply(a); }
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
    }

    public void apply(Activity activity) {
        try {
            activity.getWindow().getDecorView().post(new Runnable() {
                @Override public void run() {
                    doApply(activity);
                }
            });
        } catch (Exception e) {}
    }

    private void doApply(Activity activity) {
        try {
            if (theme == null) theme = XingChenTheme.load(activity);
            ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            ensureWallpaperLayer(activity, decor);
            if (XingChenTheme.UI_GLASS.equals(theme.uiStyle)) {
                makeTransparent(decor);
            } else {
                decor.setBackgroundColor(0xFFF5F0E8);
            }
        } catch (Exception e) {}
    }

    private void ensureWallpaperLayer(Activity activity, ViewGroup decor) {
        try {
            View existing = decor.findViewWithTag("xc_wallpaper");
            ImageView iv;
            if (existing instanceof ImageView) {
                iv = (ImageView) existing;
            } else {
                iv = new ImageView(activity);
                iv.setTag("xc_wallpaper");
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                decor.addView(iv, 0, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            }
            if (XingChenTheme.UI_GLASS.equals(theme.uiStyle)) {
                iv.setVisibility(View.VISIBLE);
                setWallpaperDrawable(activity, iv);
            } else {
                iv.setVisibility(View.GONE);
            }
        } catch (Exception e) {}
    }

    private void setWallpaperDrawable(Activity activity, ImageView iv) {
        try {
            String type = theme.wallpaperType;
            String value = theme.wallpaperValue;
            if (XingChenTheme.WP_COLOR.equals(type)) {
                try {
                    iv.setImageDrawable(null);
                    iv.setBackgroundColor(android.graphics.Color.parseColor(value));
                } catch (Exception e) {
                    iv.setBackgroundColor(0x00000000);
                    iv.setImageResource(getBuiltinRes(activity, "shanjian"));
                }
            } else if (XingChenTheme.WP_LOCAL.equals(type) || XingChenTheme.WP_URL.equals(type)) {
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
            } else {
                iv.setBackgroundColor(0x00000000);
                iv.setImageResource(getBuiltinRes(activity, value));
            }
        } catch (Exception e) {}
    }

    private int getBuiltinRes(android.content.Context ctx, String name) {
        try {
            if ("shanjian".equals(name)) {
                return ctx.getResources().getIdentifier("poster_shanjian_blur", "drawable", ctx.getPackageName());
            }
            return ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
        } catch (Exception e) {
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
                    Drawable bg = vg.getBackground();
                    if (bg != null) {
                        vg.setBackgroundColor(0x00000000);
                    }
                    if (vg.getChildCount() > 0 && !(child instanceof android.widget.ScrollView)
                            && !(child instanceof androidx.recyclerview.widget.RecyclerView)
                            && !(child instanceof android.widget.ListView)) {
                        makeTransparent(vg);
                    }
                }
            }
        } catch (Exception e) {}
    }
}
