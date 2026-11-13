package com.fongmi.android.tv.ai;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.lang.ref.WeakReference;

/**
 * 顶层 Activity 追踪（P1）。
 * 在 Application.onCreate 中调用 AiActivityTracker.init(app) 即可。
 */
public class AiActivityTracker {

    private static WeakReference<Activity> topRef;

    public static void init(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) { topRef = new WeakReference<>(a); }
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    public static Activity top() {
        return topRef == null ? null : topRef.get();
    }
}
