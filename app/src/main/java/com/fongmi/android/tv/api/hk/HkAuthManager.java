package com.fongmi.android.tv.api.hk;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.example.hikerview.utils.DisplayUtil;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.ai.AiActivityTracker;
import com.lxj.xpopup.XPopup;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 海阔敏感 API 授权管理器。
 * <p>
 * 一般敏感面（findJavaClass/loadJavaClass/registerDNS/publishRule 等）沿既有
 * 形态：SharedPreferences 按「规则标题+API名」持久授权，首次弹窗、授权后
 * 不再问（与官 AuthBridgeEvent/updateAuth 的持久裁决面对应，见
 * event/AuthBridgeEvent 类头注的契约账）。
 * <p>
 * openAppIntent 一面已于 2026-11-14 簇4 照官改姿态（官据 JSEngine.smali
 * openAppIntent:20047＋canTouchUI 三参形:1724＋$16 确认:7531＋$15 执行
 * :7266）：官此面<b>没有</b>持久授权——每回先过 canTouchUI(规则标题,
 * "openAppIntent", 1000ms) 频控门（通过即占槽、1 秒内再调直回 false），
 * 过门后每回弹 XPopup 确认，JS 线程在 CountDownLatch 上 await 1 分钟读
 * 结果（超时回 false）。本类的 checkAuth 对 apiName="openAppIntent" 分流
 * 到 {@link #checkOpenAppIntent}，不读不写持久授权；历史上已存的
 * openAppIntent 持久条目按官形裁定为死数据（官无此存量概念），在新路
 * 首调时顺手清除，其余 API 的存量条目不动。
 */
public class HkAuthManager {

    private static final String PREFS = "hk_auth";
    private static final long TIMEOUT_SEC = 120;

    /** openAppIntent 的 API 名（官 canTouchUI 的 method 键逐字）。 */
    public static final String API_OPEN_APP_INTENT = "openAppIntent";

    /** 官 canTouchUI 的 uiUseMap 等价（键＝规则标题+"@"+API名，值＝上次放行时刻）。 */
    private static final ConcurrentHashMap<String, Long> UI_USE_MAP = new ConcurrentHashMap<>();

    /**
     * 官 JSEngine.canTouchUI(String, String, long)（smali:1724）逐字语义：
     * 键＝type+"@"+key；上次放行距今 &lt; interval 回 false（不更新时刻），
     * 否则记下当前时刻回 true（通过即占槽）。
     */
    private static boolean canTouchUI(String type, String key, long interval) {
        String k = type + "@" + key;
        long now = System.currentTimeMillis();
        Long last = UI_USE_MAP.get(k);
        if (last != null && now - last < interval) {
            return false;
        }
        UI_USE_MAP.put(k, now);
        return true;
    }

    /** 检查是否已授权（仅一般敏感面用；openAppIntent 不走此面）。 */
    public static boolean isAuthorized(String ruleTitle, String apiName) {
        try {
            SharedPreferences sp = App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return sp.getBoolean(key(ruleTitle, apiName), false);
        } catch (Throwable e) {
            return false;
        }
    }

    /** 设置授权状态（仅一般敏感面用；openAppIntent 不走此面）。 */
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

    /** 清除某一面的历史持久条目（仅 openAppIntent 改姿态时清自己那面）。 */
    private static void clearStored(String ruleTitle, String apiName) {
        try {
            SharedPreferences sp = App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String k = key(ruleTitle, apiName);
            if (sp.contains(k)) {
                sp.edit().remove(k).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 检查授权：已授权则直接执行 onAllow；未授权则弹窗询问。
     * 必须在 JS 线程调用（会阻塞等待用户选择）；弹窗在 UI 线程显示。
     * apiName 为 "openAppIntent" 时分流官姿态（见类头注），其余面走持久授权。
     * @return true=已授权（onAllow 已执行或将执行），false=用户拒绝
     */
    public static boolean checkAuth(String ruleTitle, String apiName, String description, Runnable onAllow) {
        if (API_OPEN_APP_INTENT.equals(apiName)) {
            // 包名不在本签名内：确认文案的括号内显示名只能由四参新法
            // checkOpenAppIntent(ruleTitle, packageName, onAllow) 填准，
            // 本路以无包名形出文案（见该法注与分账主线缝）。
            return checkOpenAppIntent(ruleTitle, null, onAllow);
        }
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

    /**
     * openAppIntent 官姿态确认（官 JSEngine.openAppIntent:20047 全形）：
     * <ol>
     * <li>canTouchUI(规则标题, "openAppIntent", 1000) 频控门——不过直回
     * false，不弹窗、不执行；</li>
     * <li>每回确认（$16:7531）：XPopup asConfirm，borderRadius=16dp、
     * dismissOnBackPressed(false)、dismissOnTouchOutside(false)，
     * 标题「温馨提示」，正文「小程序{ruleTitle}想要打开第三方软件
     * ({显示名})，确定允许吗？」——显示名＝包名本体，唯
     * "com.alicloud.databox" 显示「阿里云盘」（$16:7544-7557 逐字）；
     * 钮「取消」/「确定」。packageName 为 null 时正文省去括号段（本类
     * 三参 checkAuth 分流的降级形；调用方应改走本法填包名，见分账）；</li>
     * <li>结果锁存＝CountDownLatch(1)：调用线程 await 1 分钟（官
     * :20178-20182 逐字 1 MINUTES），确认置 true 后 countDown、取消只
     * countDown（保持 false），超时读到的仍是 false——锁存锁的是「这一回
     * 确认的结果」，不是授权缓存，下一回照样重新确认；</li>
     * <li>确认后才在调用线程执行 onAllow（官 $15 在确认回调内于 UI 线程
     * 执行拼装/启动，咱家沿 checkAuth 既有格局在 latch 释放后于调用
     * 线程执行，终态一致、执行线程差记名于分账）。</li>
     * </ol>
     * 不读不写持久授权；历史 openAppIntent 持久条目在首调时清除（官无
     * 此存量概念，见类头注）。
     *
     * @return true=用户本回确认且 onAllow 已执行；false=频控未过/无宿主/取消/超时
     */
    public static boolean checkOpenAppIntent(String ruleTitle, String packageName, Runnable onAllow) {
        String title = ruleTitle == null ? "" : ruleTitle;
        clearStored(title, API_OPEN_APP_INTENT);
        if (!canTouchUI(title, API_OPEN_APP_INTENT, 1000L)) {
            return false;
        }
        Activity activity = AiActivityTracker.top();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return false;
        }
        String display = packageName;
        if ("com.alicloud.databox".equals(packageName)) {
            display = "阿里云盘";
        }
        final String message = display == null || display.isEmpty()
                ? "小程序" + title + "想要打开第三方软件，确定允许吗？"
                : "小程序" + title + "想要打开第三方软件 (" + display + ")，确定允许吗？";
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] allowed = {false};
        activity.runOnUiThread(() -> {
            try {
                new XPopup.Builder(activity)
                        .borderRadius((float) DisplayUtil.dpToPx(activity, 16))
                        .dismissOnBackPressed(Boolean.FALSE)
                        .dismissOnTouchOutside(Boolean.FALSE)
                        .asConfirm("温馨提示", message, "取消", "确定",
                                () -> {
                                    allowed[0] = true;
                                    latch.countDown();
                                },
                                () -> latch.countDown(),
                                false)
                        .show();
            } catch (Throwable e) {
                latch.countDown();
            }
        });
        try {
            latch.await(1, TimeUnit.MINUTES);
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
