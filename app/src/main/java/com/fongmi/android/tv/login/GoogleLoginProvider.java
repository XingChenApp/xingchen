package com.fongmi.android.tv.login;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.cloud.CloudAccountStore;

/**
 * 谷歌网页登录（壳子统一登录托管的谷歌提供方，id="google"）。
 * <p>
 * 登录目标是油管音乐网页端（music.youtube.com），登录 Cookie 经壳子统一弹窗
 * （PanLoginDialog，WebView 登录）拿到后落盘托管，供油管音乐源经
 * {@link LoginManager#getCookie(String)} 读取、给搜索/取链请求带态
 * （脚本侧用 SAPISID 系 Cookie 组装 SAPISIDHASH 鉴权头）。
 * 存储与快手/115 同轨：CloudAccountStore 加密存储，本类不走 PanAuth（网盘专用门面）。
 * <p>
 * 为什么需要登录态：用户出口 IP 被谷歌标记时，匿名搜索会被拦、取链回
 * LOGIN_REQUIRED；带他自己的谷歌登录态请求是唯一剩下的路（2026-10-25 他亲口批准）。
 * 过期口径与快手一致：提供方只做格式判定，不预判服务端有效期；Cookie 真过期后
 * 源请求失败回落匿名行为，用户重新登录即刷新。
 */
public class GoogleLoginProvider implements LoginProvider {

    /** 提供方 id，油管音乐源在插件里以 loginProvider="google" 声明对齐。 */
    public static final String ID = "google";

    /** 登录页：油管音乐网页端，在此页内完成谷歌账号登录。 */
    public static final String LOGIN_URL = "https://music.youtube.com/";

    /** WebView 抓 Cookie 的域名口径（登录 Cookie 以 .youtube.com 域下发）。 */
    public static final String COOKIE_DOMAIN = "youtube.com";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "谷歌";
    }

    @Override
    public boolean isLoggedIn() {
        return isCookieValid(getCookie());
    }

    @Override
    public String getCookie() {
        try {
            String cookie = CloudAccountStore.getCookie(App.get(), ID);
            return cookie == null ? "" : cookie;
        } catch (Throwable ignored) {
            return "";
        }
    }

    @Override
    public void clear() {
        try {
            CloudAccountStore.clear(App.get(), ID);
        } catch (Throwable ignored) {
        }
    }

    /** 登录成功后落盘托管（登录弹窗的 onLogin 回调调此口，与实例 clear/get 同一轨）。 */
    public static void putCookie(String cookie) {
        try {
            CloudAccountStore.saveCookie(App.get(), ID, cookie == null ? "" : cookie);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 谷歌网页登录 Cookie 有效性判定，与油管音乐源的实际消费逐项对齐：
     * 脚本组装 SAPISIDHASH 只能用 SAPISID 系三颗种子（SAPISID / __Secure-1PSAPISID /
     * __Secure-3PSAPISID）之一，缺了它们「已登录」就是假话、带态请求照样被拒。
     * 三颗里任意一颗值非空即算登录成立。（2026-10-25 改：原写 __Secure-1PAPISID 少个 S，
     * 与谷歌真实 Cookie 名对不上，只有 __Secure 种子的会话会被误判未登录。）
     */
    public static boolean isCookieValid(String cookie) {
        if (TextUtils.isEmpty(cookie)) return false;
        for (String pair : cookie.split(";")) {
            String item = pair.trim();
            int eq = item.indexOf('=');
            if (eq <= 0) continue;
            String name = item.substring(0, eq).trim();
            String value = item.substring(eq + 1).trim();
            if (value.isEmpty()) continue;
            if ("SAPISID".equals(name) || "__Secure-1PSAPISID".equals(name) || "__Secure-3PSAPISID".equals(name)) return true;
        }
        return false;
    }
}
