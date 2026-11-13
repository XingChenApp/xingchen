package com.fongmi.android.tv.api.hk;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/**
 * 移植自 8.83: com.lzy.okgo.cookie.CookieJarImpl（Application.getCookieJar()）
 * 持久化 Cookie 存储，海阔规则很多站需要 Cookie 才能出图/出数据。
 */
public class HkCookieJar implements CookieJar {

    private static final String PREF = "hiker_cookie";
    private static volatile HkCookieJar instance;

    private final SharedPreferences prefs;
    private final Map<String, List<Cookie>> memory = new HashMap<>();

    private HkCookieJar(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static HkCookieJar get(Context context) {
        if (instance == null) {
            synchronized (HkCookieJar.class) {
                if (instance == null) instance = new HkCookieJar(context);
            }
        }
        return instance;
    }

    @Override
    public synchronized void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        String host = url.host();
        List<Cookie> list = memory.get(host);
        if (list == null) {
            list = new ArrayList<>();
            memory.put(host, list);
        }
        for (Cookie nc : cookies) {
            for (int i = 0; i < list.size(); i++) {
                Cookie oc = list.get(i);
                if (oc.name().equals(nc.name()) && oc.domain().equals(nc.domain()) && oc.path().equals(nc.path())) {
                    list.remove(i);
                    break;
                }
            }
            list.add(nc);
        }
        persist(host, list);
    }

    @Override
    public synchronized List<Cookie> loadForRequest(HttpUrl url) {
        String host = url.host();
        List<Cookie> list = memory.get(host);
        if (list == null) {
            list = restore(host);
            if (!list.isEmpty()) memory.put(host, list);
        }
        if (list == null || list.isEmpty()) return Collections.emptyList();
        List<Cookie> valid = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Cookie c : list) {
            if (c.expiresAt() >= now && c.matches(url)) valid.add(c);
        }
        return valid;
    }

    /** 清空全部 Cookie（内存 + 持久化），供壳子"清除海阔 Cookie"入口调用。 */
    public synchronized void clear() {
        memory.clear();
        try {
            prefs.edit().clear().apply();
        } catch (Throwable ignored) {
        }
    }

    /** 清空指定 host 的 Cookie。 */
    public synchronized void clearHost(String host) {
        if (host == null || host.isEmpty()) return;
        memory.remove(host);
        try {
            prefs.edit().remove(host).apply();
        } catch (Throwable ignored) {
        }
    }

    private void persist(String host, List<Cookie> list) {
        try {
            StringBuilder sb = new StringBuilder();
            for (Cookie c : list) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(c.name()).append("\t").append(c.value()).append("\t")
                        .append(c.domain()).append("\t").append(c.path()).append("\t")
                        .append(c.expiresAt()).append("\t").append(c.secure()).append("\t")
                        .append(c.httpOnly()).append("\t").append(c.hostOnly());
            }
            prefs.edit().putString(host, sb.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private List<Cookie> restore(String host) {
        List<Cookie> list = new ArrayList<>();
        try {
            String s = prefs.getString(host, null);
            if (s == null || s.isEmpty()) return list;
            for (String line : s.split("\n")) {
                String[] p = line.split("\t", -1);
                if (p.length < 8) continue;
                Cookie.Builder b = new Cookie.Builder()
                        .name(p[0]).value(p[1])
                        .expiresAt(Long.parseLong(p[4]))
                        .path(p[3]);
                if (Boolean.parseBoolean(p[7])) b.hostOnlyDomain(p[2]);
                else b.domain(p[2]);
                if (Boolean.parseBoolean(p[5])) b.secure();
                if (Boolean.parseBoolean(p[6])) b.httpOnly();
                list.add(b.build());
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}
