package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.webkit.WebView;

import com.fongmi.android.tv.App;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 环29（D29-1）复核订正 B1：官 WebUtil.goWeb 两形的咱家承载登记口。
 * 官形（WebUtil.smali goWeb 段亲读）：静态 webViewActivity 在位时，逆序遍历
 * ActivityManager 活动栈、finish 承载页之上的每一层直到承载页，再 post
 * WebUpdateUrlEvent 由承载页对既有 WebView loadUrl；承载不在位时新开
 * WebViewActivity。
 *
 * <p>咱家承载只许是专用网页页——{@link HkListActivity#openX5PlayPage} 新开
 * 的那个 HkListActivity（Intent 带 {@link HkListActivity#EXTRA_WEB_CARRIER_PAGE}）
 * 的 WebView 持有方（其 HkArticleFragment 的 HkWebViewHolder 在该页语境下
 * 创建 WebView 时登记，见 HkWebViewHolder.ensureWebView 的门）。普通列表页
 * 的行内 WebView 不登记。登记随页面生命周期：页 onDestroy 注销（HkListActivity
 * .onDestroy 与 HkArticleFragment.onDestroyView 双点同口 unregister，按身份
 * 匹配才清）；承载已销毁/不在活动栈＝未命中，goWeb 走新开页路，绝不假报命中。
 *
 * <p>活动栈由本类经 ActivityLifecycleCallbacks 自维护（官 ActivityManager
 * 活动栈的等价物），栈序自底向上；register 时显式入栈承载页（其 onCreate
 * 可能早于回调装配）。
 */
public final class HkWebCarrier {

    private static WeakReference<Activity> activityRef;
    private static WeakReference<WebView> webViewRef;
    private static final List<WeakReference<Activity>> STACK = new ArrayList<>();
    private static boolean stackTrackingInstalled;

    private static final Application.ActivityLifecycleCallbacks STACK_CALLBACKS =
            new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    pushStack(activity);
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityResumed(Activity activity) {
                }

                @Override
                public void onActivityPaused(Activity activity) {
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    removeFromStack(activity);
                    unregister(activity);
                }
            };

    private HkWebCarrier() {
    }

    /** 专用网页页的 WebView 持有方创建 WebView 时登记（调用方已按页形把门）。 */
    public static synchronized void register(Activity activity, WebView webView) {
        if (activity == null || webView == null) return;
        installStackTracking();
        activityRef = new WeakReference<>(activity);
        webViewRef = new WeakReference<>(webView);
        pushStack(activity);
    }

    /** 注销：仅当登记的承载宿主就是该页时才清（别页销毁不误清）。 */
    public static synchronized void unregister(Activity activity) {
        if (activity == null) return;
        Activity a = activityRef == null ? null : activityRef.get();
        if (a == activity) {
            activityRef = null;
            webViewRef = null;
        }
        removeFromStack(activity);
    }

    private static synchronized void installStackTracking() {
        if (stackTrackingInstalled) return;
        stackTrackingInstalled = true;
        try {
            App.get().registerActivityLifecycleCallbacks(STACK_CALLBACKS);
        } catch (Throwable ignored) {
            // 装配失败时栈只含 register 显式入栈的承载页：承载页仍可命中
            // （其上无已知层可 finish），不影响命中/未命中的判定口径。
        }
    }

    private static synchronized void pushStack(Activity activity) {
        if (activity == null) return;
        for (WeakReference<Activity> ref : STACK) {
            if (ref.get() == activity) return;
        }
        STACK.add(new WeakReference<>(activity));
    }

    private static synchronized void removeFromStack(Activity activity) {
        Iterator<WeakReference<Activity>> it = STACK.iterator();
        while (it.hasNext()) {
            Activity a = it.next().get();
            if (a == null || a == activity) it.remove();
        }
    }

    private static synchronized List<Activity> stackSnapshot() {
        List<Activity> out = new ArrayList<>();
        for (WeakReference<Activity> ref : STACK) {
            Activity a = ref.get();
            if (a != null) out.add(a);
        }
        return out;
    }

    /** 当前活着的承载宿主（已 finishing/销毁回 null）。 */
    public static synchronized Activity liveActivity() {
        Activity a = activityRef == null ? null : activityRef.get();
        if (a == null || a.isFinishing() || a.isDestroyed()) return null;
        return a;
    }

    /** 当前活着的承载 WebView（未登记/已被回收回 null）。 */
    public static synchronized WebView liveWebView() {
        return webViewRef == null ? null : webViewRef.get();
    }

    /**
     * finish 栈形决策（纯函数，夹具口径）：栈（自底向上）里承载页之上的层，
     * 按自顶向下的逆序返回（待 finish 序）；承载页不在栈内回 null（未命中）。
     * 承载页本身永不在返回表内。
     */
    static <T> List<T> finishPlan(List<T> stackBottomToTop, T carrier) {
        if (stackBottomToTop == null || carrier == null) return null;
        int idx = -1;
        for (int i = 0; i < stackBottomToTop.size(); i++) {
            if (stackBottomToTop.get(i) == carrier) idx = i;
        }
        if (idx < 0) return null;
        List<T> out = new ArrayList<>();
        for (int i = stackBottomToTop.size() - 1; i > idx; i--) out.add(stackBottomToTop.get(i));
        return out;
    }

    /**
     * goWeb 命中支（官形）：承载在位 → 逆序 finish 其上每一层直到承载页，
     * 再由承载页对既有 WebView loadUrl，回 true；承载已销毁/不在栈/无
     * WebView → 回 false（未命中，调用方走新开页路）。
     */
    public static boolean loadIntoExisting(String url) {
        Activity activity = liveActivity();
        WebView webView = liveWebView();
        if (activity == null || webView == null) return false;
        List<Activity> finish = finishPlan(stackSnapshot(), activity);
        if (finish == null) return false;
        for (Activity above : finish) above.finish();
        activity.runOnUiThread(() -> webView.loadUrl(url));
        return true;
    }
}
