package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.fongmi.android.tv.App;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;

public class PanAuth {

    private static final String PREFS = "xingchen";
    private static final String KEY_115_COOKIE = "pan115_cookie";
    private static final String KEY_115_VIP = "pan115_vip";
    private static final String KEY_QUARK_COOKIE = "panquark_cookie";
    private static final String KEY_QUARK_VIP = "panquark_vip";

    private static SharedPreferences prefs() {
        Context ctx = App.get();
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String get115Cookie() {
        try {
            String c = prefs().getString(KEY_115_COOKIE, "");
            if (c != null && !c.isEmpty()) return c;
            Context ctx = App.get();
            if (ctx != null) {
                File f = new File(ctx.getFilesDir(), "plugins/py/115_cookie.json");
                if (f.exists()) {
                    String txt = readAll(f);
                    String cookie = new JSONObject(txt).optString("cookie", "");
                    if (!cookie.isEmpty()) return cookie;
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static void put115Cookie(String cookie) {
        prefs().edit().putString(KEY_115_COOKIE, cookie == null ? "" : cookie).apply();
    }

    public static void clear115() {
        prefs().edit().remove(KEY_115_COOKIE).remove(KEY_115_VIP).apply();
    }

    public static boolean is115LoggedIn() {
        return isCookieValid(get115Cookie());
    }

    /**
     * 115 登录态格式校验：必须包含 UID/CID/SEID 三个关键字段，
     * 与 115 网盘爬虫的 cookie_ok 判定一致。光有 cookie 字符串不算登录。
     */
    private static boolean isCookieValid(String cookie) {
        if (cookie == null || cookie.isEmpty()) return false;
        String upper = cookie.toUpperCase(Locale.US);
        return upper.contains("UID=") && upper.contains("CID=") && upper.contains("SEID=");
    }

    public static String get115Vip() {
        return prefs().getString(KEY_115_VIP, "");
    }

    public static void put115Vip(String vip) {
        prefs().edit().putString(KEY_115_VIP, vip == null ? "" : vip).apply();
    }

    public static String getQuarkCookie() {
        try {
            String c = prefs().getString(KEY_QUARK_COOKIE, "");
            return c == null ? "" : c;
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static void putQuarkCookie(String cookie) {
        prefs().edit().putString(KEY_QUARK_COOKIE, cookie == null ? "" : cookie).apply();
    }

    public static void clearQuark() {
        prefs().edit().remove(KEY_QUARK_COOKIE).remove(KEY_QUARK_VIP).apply();
    }

    public static boolean isQuarkLoggedIn() {
        return !getQuarkCookie().isEmpty();
    }

    public static String getQuarkVip() {
        return prefs().getString(KEY_QUARK_VIP, "");
    }

    public static void putQuarkVip(String vip) {
        prefs().edit().putString(KEY_QUARK_VIP, vip == null ? "" : vip).apply();
    }

    public static String loginSummary() {
        int n = 0;
        if (is115LoggedIn()) n++;
        if (isQuarkLoggedIn()) n++;
        if (n == 0) return "未登录";
        StringBuilder sb = new StringBuilder();
        if (is115LoggedIn()) sb.append("115");
        if (isQuarkLoggedIn()) {
            if (sb.length() > 0) sb.append("、");
            sb.append("夸克");
        }
        sb.append("已登录");
        return sb.toString();
    }

    private static String readAll(File f) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }
}
