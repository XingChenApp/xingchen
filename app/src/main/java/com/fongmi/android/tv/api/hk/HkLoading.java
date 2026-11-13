package com.fongmi.android.tv.api.hk;

import android.os.Handler;
import android.os.Looper;

/**
 * preRule / showLoading 的 UI 桥。
 *
 * <p>8.83 等价实现：JSEngine.showLoading(text) → EventBus post LoadingEvent(text, true)，
 * hideLoading() → post LoadingEvent(null, false)，由 ArticleListFragment 订阅后弹 loading 框。
 * 宿主无 EventBus 依赖，改用静态 Listener 持有（HkPageActivity 注册实现弹窗）。</p>
 *
 * <p>HkJsRuntime 在 app/src/main，无 Activity 引用；Listener 回调一律 post 到主线程。
 * 调用方需自带 ruleTitle，UI 层用它做 canTouchUI 等价校验（只响应当前规则）。</p>
 */
public final class HkLoading {

    public interface Listener {
        void onShowLoading(String ruleTitle, String text);
        void onHideLoading(String ruleTitle);
    }

    private static volatile Listener listener;

    private HkLoading() {
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    public static void clearListener(Listener l) {
        if (listener == l) listener = null;
    }

    private static void post(Runnable r) {
        try {
            new Handler(Looper.getMainLooper()).post(r);
        } catch (Throwable ignored) {
        }
    }

    /** 8.83 showLoading(text)：规则 preRule 里调，如 showLoading("正在获取域名，请稍候")。 */
    public static void show(final String ruleTitle, final String text) {
        final Listener l = listener;
        if (l == null) return;
        post(() -> {
            try {
                l.onShowLoading(ruleTitle, text);
            } catch (Throwable ignored) {
            }
        });
    }

    /** 8.83 hideLoading()。 */
    public static void hide(final String ruleTitle) {
        final Listener l = listener;
        if (l == null) return;
        post(() -> {
            try {
                l.onHideLoading(ruleTitle);
            } catch (Throwable ignored) {
            }
        });
    }
}
