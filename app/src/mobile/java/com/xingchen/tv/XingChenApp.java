package com.xingchen.tv;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.xingchen.tv.theme.XingChenTheme;

public class XingChenApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
                android.view.View old = decor.findViewWithTag("xc_wallpaper");
                if (old != null) decor.removeView(old);
                
                ImageView wallView = new ImageView(activity);
                wallView.setTag("xc_wallpaper");
                wallView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                
                // Read from XingChenTheme
                XingChenTheme theme = XingChenTheme.load(activity);
                String type = theme.wallpaperType;
                String value = theme.wallpaperValue;
                
                if (XingChenTheme.WP_BUILTIN.equals(type)) {
                    int resId = activity.getResources().getIdentifier("poster_shanjian_blur", "drawable", activity.getPackageName());
                    if (resId != 0) wallView.setImageResource(resId);
                } else if (XingChenTheme.WP_LOCAL.equals(type)) {
                    try {
                        wallView.setImageURI(android.net.Uri.parse(value));
                    } catch (Exception e) {}
                } else if (XingChenTheme.WP_URL.equals(type)) {
                    // TODO: Load URL
                }
                
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                );
                decor.addView(wallView, 0, params);
            }
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }
}
