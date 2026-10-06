package com.xingchen.tv;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class XingChenApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                // Add blurred mountain wallpaper to DecorView globally
                ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
                // Remove old if exists
                android.view.View old = decor.findViewWithTag("xc_wallpaper");
                if (old != null) decor.removeView(old);
                
                ImageView wallView = new ImageView(activity);
                wallView.setTag("xc_wallpaper");
                wallView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                try {
                    int resId = activity.getResources().getIdentifier("poster_shanjian_blur", "drawable", activity.getPackageName());
                    if (resId != 0) wallView.setImageResource(resId);
                } catch (Exception e) {
                    // Ignore
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
