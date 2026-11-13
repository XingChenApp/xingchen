package com.fongmi.android.tv.ui.lab;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 实验室设置（SharedPreferences "lab_settings"）：
 * 全局代理开关＋端口＋no_proxy —— 生效于 lab 进程环境（LabRunner.applyEnv）
 * 与环境包下载（LabRuntimeManager）。
 */
public final class LabSettings {

    private LabSettings() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("lab_settings", Context.MODE_PRIVATE);
    }

    public static boolean proxyOn(Context c) {
        return prefs(c).getBoolean("proxy_on", false);
    }

    public static void setProxyOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("proxy_on", on).apply();
    }

    public static String proxyPort(Context c) {
        return prefs(c).getString("proxy_port", "7890");
    }

    public static void setProxyPort(Context c, String port) {
        prefs(c).edit().putString("proxy_port", port == null ? "7890" : port.trim()).apply();
    }

    public static String proxyNoProxy(Context c) {
        return prefs(c).getString("proxy_no", "localhost,127.0.0.1");
    }

    public static void setProxyNoProxy(Context c, String noProxy) {
        prefs(c).edit().putString("proxy_no", noProxy == null ? "" : noProxy.trim()).apply();
    }
}
