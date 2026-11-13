package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.github.catvod.net.GlobalProxy;
import com.github.catvod.net.OkHttp;

/**
 * 全局 HTTP/SOCKS5 代理设置（与“走壳代理”按域名规则互斥，开启时优先）。
 * 配置存 xingchen SharedPreferences，生效时重建 OkHttp clients 并同步 Python requests 环境变量。
 */
public class XingChenProxy {

    public static final int TYPE_OFF = GlobalProxy.TYPE_NONE;
    public static final int TYPE_HTTP = GlobalProxy.TYPE_HTTP;
    public static final int TYPE_SOCKS5 = GlobalProxy.TYPE_SOCKS5;
    public static final String[] TYPE_NAMES = {"关闭", "HTTP", "SOCKS5"};

    private static final String KEY_TYPE = "xingchen.proxy_type";
    private static final String KEY_HOST = "xingchen.proxy_host";
    private static final String KEY_PORT = "xingchen.proxy_port";
    private static final String KEY_USER = "xingchen.proxy_user";
    private static final String KEY_PASS = "xingchen.proxy_pass";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("xingchen", Context.MODE_PRIVATE);
    }

    public static int getType(Context context) {
        int t = prefs(context).getInt(KEY_TYPE, TYPE_OFF);
        return (t >= TYPE_OFF && t <= TYPE_SOCKS5) ? t : TYPE_OFF;
    }

    public static String getHost(Context context) {
        return prefs(context).getString(KEY_HOST, "");
    }

    public static int getPort(Context context) {
        return prefs(context).getInt(KEY_PORT, 0);
    }

    public static String getUser(Context context) {
        return prefs(context).getString(KEY_USER, "");
    }

    public static String getPass(Context context) {
        return prefs(context).getString(KEY_PASS, "");
    }

    public static GlobalProxy.Config read(Context context) {
        GlobalProxy.Config c = new GlobalProxy.Config();
        c.type = getType(context);
        c.host = getHost(context);
        c.port = getPort(context);
        c.user = getUser(context);
        c.pass = getPass(context);
        return c;
    }

    public static void save(Context context, int type, String host, int port, String user, String pass) {
        prefs(context).edit()
                .putInt(KEY_TYPE, type)
                .putString(KEY_HOST, host == null ? "" : host.trim())
                .putInt(KEY_PORT, port)
                .putString(KEY_USER, user == null ? "" : user)
                .putString(KEY_PASS, pass == null ? "" : pass)
                .apply();
        apply(context);
    }

    /** 读取配置 → 推入 GlobalProxy → 重建 OkHttp clients → 同步 Python requests */
    public static void apply(Context context) {
        try {
            GlobalProxy.set(read(context));
            OkHttp.resetClients();
            GlobalProxy.syncPython();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static String summary(Context context) {
        GlobalProxy.Config c = read(context);
        if (!c.enabled()) return "未启用";
        String type = c.type == TYPE_SOCKS5 ? "SOCKS5" : "HTTP";
        return type + " " + c.host + ":" + c.port;
    }

    public static boolean validHost(String host) {
        return !TextUtils.isEmpty(host) && host.trim().length() > 0;
    }

    public static boolean validPort(int port) {
        return port > 0 && port <= 65535;
    }
}
