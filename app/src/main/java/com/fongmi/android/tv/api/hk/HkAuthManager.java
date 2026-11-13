package com.fongmi.android.tv.api.hk;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.ai.AiActivityTracker;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 海阔敏感 API 授权管理器（简化版）。
 * 8.83 原版用 AuthStorageItem + 授权弹窗；星辰简化为 SharedPreferences 记录 + AlertDialog。
 * 规则首次调用敏感 API 时弹窗询问，用户允许后记住（按 规则标题+API名），下次不再询问。
 */
public class HkAuthManager {

    private static final String PREFS = "hk_auth";
    private static final long TIMEOUT_SEC = 120;

    /** 检查是否已授权。 */
    public static boolean isAuthorized(String ruleTitle, String apiName) {
        try {
            SharedPreferences sp = App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return sp.getBoolean(key(ruleTitle, apiName), false);
        } catch (Throwable e) {
            return false;
        }
    }

    /** 设置授权状态。 */
    public static void setAuthorized(String ruleTitle, String apiName, boolean allow) {
        try {
            SharedPreferences sp = App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            sp.edit().putBoolean(key(ruleTitle, apiName), allow).apply();
        } catch (Throwable ignored) {
        }
    }

    private static String key(String ruleTitle, String apiName) {
        String r = ruleTitle == null ? "" : ruleTitle;
        String a = apiName == null ? "" : apiName;
        return r + "|||" + a;
    }

    /**
     * 检查授权：已授权则直接执行 onAllow；未授权则弹窗询问。
     * 必须在 JS 线程调用（会阻塞等待用户选择）；弹窗在 UI 线程显示。
     * @return true=已授权（onAllow 已执行或将执行），false=用户拒绝
     */
    public static boolean checkAuth(String ruleTitle, String apiName, String description, Runnable onAllow) {
        if (isAuthorized(ruleTitle, apiName)) {
            if (onAllow != null) {
                try {
                    onAllow.run();
                } catch (Throwable ignored) {
                }
            }
            return true;
        }
        Activity activity = AiActivityTracker.top();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return false;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] allowed = {false};
        activity.runOnUiThread(() -> {
            try {
                HkAuthDialog.show(activity, ruleTitle, apiName, description, new HkAuthDialog.Callback() {
                    @Override
                    public void onAllow() {
                        allowed[0] = true;
                        setAuthorized(ruleTitle, apiName, true);
                        latch.countDown();
                    }

                    @Override
                    public void onDeny() {
                        allowed[0] = false;
                        latch.countDown();
                    }
                });
            } catch (Throwable e) {
                latch.countDown();
            }
        });
        try {
            latch.await(TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (allowed[0] && onAllow != null) {
            try {
                onAllow.run();
            } catch (Throwable ignored) {
            }
        }
        return allowed[0];
    }

    /** 获取当前规则标题（用于授权记录）。 */
    public static String currentRuleTitle() {
        // 由调用方（HkJsRuntime）传入 rule.getTitle()，此处仅作兜底
        return "";
    }
}
