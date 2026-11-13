package com.fongmi.android.tv.login;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.cloud.CloudAccountStore;

/**
 * 快手网页登录（壳子统一登录托管的快手提供方，id="kuaishou"）。
 * <p>
 * 登录目标是快手网页端（live.kuaishou.com），登录 Cookie 组经壳子统一弹窗
 * （PanLoginDialog，WebView 登录）拿到后落盘托管，供快手直播弹幕 provider
 * 经 {@link LoginManager#getCookie(String)} 读取、换 websocketinfo 的 token。
 * 存储与 115 同轨：CloudAccountStore 加密存储（PanAuth 的 115/夸克同路），
 * 本类不走 PanAuth（那是网盘专用门面）。
 * <p>
 * 过期口径与 115 一致：提供方只做格式判定，不预判服务端有效期；Cookie 真过期后
 * 弹幕 provider 换不到 token 时静默不接（不弹窗扰播），用户重新登录即刷新。
 */
public class KuaishouLoginProvider implements LoginProvider {

    /** 提供方 id，与快手直播弹幕 provider 的 LOGIN_PROVIDER_ID 对齐。 */
    public static final String ID = "kuaishou";

    /** 登录页：快手直播网页端，在此页内完成快手网页登录。 */
    public static final String LOGIN_URL = "https://live.kuaishou.com/";

    /** WebView 抓 Cookie 的域名口径（登录 Cookie 以 .kuaishou.com 域下发）。 */
    public static final String COOKIE_DOMAIN = "kuaishou.com";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "快手";
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
     * 快手网页登录 Cookie 有效性判定，与弹幕 provider 的实际消费逐项对齐：
     * provider 把整串 Cookie 透传给房间页与 websocketinfo，只从中解析 kwfv1
     * 派生 Kww 头（无 kwfv1 则不带 Kww、补取路残缺）；登录身份标记取 userId
     * （匿名访客 Cookie 只有 did 等设备项，无 userId）。两项必须齐且值非空。
     */
    public static boolean isCookieValid(String cookie) {
        if (TextUtils.isEmpty(cookie)) return false;
        boolean kwfv1 = false;
        boolean userId = false;
        for (String pair : cookie.split(";")) {
            String item = pair.trim();
            int eq = item.indexOf('=');
            if (eq <= 0) continue;
            String name = item.substring(0, eq).trim();
            String value = item.substring(eq + 1).trim();
            if (value.isEmpty()) continue;
            if ("kwfv1".equals(name)) kwfv1 = true;
            else if ("userId".equals(name)) userId = true;
            if (kwfv1 && userId) return true;
        }
        return false;
    }
}
