package com.example.hikerview.ui;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.util.Iterator;
import java.util.Stack;

/**
 * 照官 8.83 本体移植（官为 Kotlin object 伴生单例，jadx 官包源转 Java 等价）：
 * 全进程 Activity 栈，onActivityCreated 入栈、Destroyed 出栈。
 * 两处与官的差异记名：① 官 removeActivity 里调 JSEngine.clearSchedulePool
 * 清该页的定时任务池，星辰引擎的定时任务按运行时作用域管理、无此口，
 * 此处不调；② 官 getMainActivity 找其 ui.home.MainActivity 实例，星辰无
 * 该类，改为返回栈底 Activity（即承载海阔页的宿主）。
 * ③ getCurrentActivity 改按 resumed 取当前页（他 2026-11-14 层级线：提示窗
 * 与报错必须落在当前页面之上）：官的创建序栈顶在转场与迟到提示时给到
 * 已非顶层的页、窗随之沉到页面后面；咱家此口改为先给最近 resumed 的页，
 * 创建序栈只作兜底，栈空时返回 null（官形 lastElement 会直接抛异常）。
 */
public final class ActivityManager implements Application.ActivityLifecycleCallbacks {

    private static ActivityManager activityManager;

    private final Stack<Activity> activityStack = new Stack<>();

    /** 最近 resumed 的页＝当前页（getCurrentActivity 的首选，见类头差异③）。 */
    private volatile Activity resumedActivity;

    private ActivityManager() {
    }

    public static ActivityManager getInstance() {
        if (activityManager == null) {
            synchronized (ActivityManager.class) {
                if (activityManager == null) {
                    activityManager = new ActivityManager();
                }
            }
        }
        return activityManager;
    }

    public Stack<Activity> getActivityStack() {
        return this.activityStack;
    }

    public void addActivity(Activity activity) {
        this.activityStack.add(activity);
    }

    public void removeActivity(Activity activity) {
        if (activity != null) {
            this.activityStack.remove(activity);
        }
    }

    public void finishActivity() {
        finishActivity(this.activityStack.lastElement());
    }

    public void finishActivity(Activity activity) {
        if (activity != null) {
            removeActivity(activity);
            activity.finish();
        }
    }

    public Activity findActivity(Class<?> ac) {
        int size = getInstance().activityStack.size();
        do {
            size--;
            if (-1 >= size) {
                return null;
            }
        } while (getInstance().activityStack.elementAt(size).getClass() != ac);
        return getInstance().activityStack.elementAt(size);
    }

    public boolean hasActivity(Class<?> ac) {
        Iterator<Activity> it = this.activityStack.iterator();
        while (it.hasNext()) {
            if (it.next().getClass() == ac) {
                return true;
            }
        }
        return false;
    }

    public Activity getCurrentActivity() {
        Activity resumed = this.resumedActivity;
        if (resumed != null && !resumed.isFinishing()) {
            return resumed;
        }
        return this.activityStack.isEmpty() ? null : this.activityStack.lastElement();
    }

    public Activity getMainActivity() {
        if (!this.activityStack.isEmpty()) {
            return this.activityStack.firstElement();
        }
        return null;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        addActivity(activity);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        removeActivity(activity);
        if (this.resumedActivity == activity) {
            this.resumedActivity = null;
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
        this.resumedActivity = activity;
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (this.resumedActivity == activity) {
            this.resumedActivity = null;
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }
}
