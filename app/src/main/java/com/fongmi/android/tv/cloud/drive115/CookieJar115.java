package com.fongmi.android.tv.cloud.drive115;

import android.text.TextUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 115 会话 cookie 罐（对照 PY Api._jar + _absorb_cookies）
 *
 * 115 的文件 CDN 要的门票 cookie 是接口在某次响应里悄悄下发的，
 * 只看取链那一次的响应就会时有时无——罐子把整个会话的都留住。
 */
public class CookieJar115 {

    private final Map<String, String> jar = new LinkedHashMap<>();

    /**
     * 把一批响应 Set-Cookie 并进罐（同名覆盖，空值删除）
     * 对照 PY Api._absorb_cookies
     */
    public synchronized void absorb(List<String> setCookieHeaders) {
        if (setCookieHeaders == null) return;
        for (String sc : setCookieHeaders) {
            if (sc == null) continue;
            String pair = sc.split(";")[0].trim();
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String k = pair.substring(0, eq).trim();
            String v = pair.substring(eq + 1).trim();
            if (TextUtils.isEmpty(k)) continue;
            if (v.isEmpty() || "deleted".equalsIgnoreCase(v)) {
                jar.remove(k);
            } else {
                jar.put(k, v);
            }
        }
    }

    /** 单条 Set-Cookie 的便捷重载 */
    public synchronized void absorb(String setCookie) {
        if (!TextUtils.isEmpty(setCookie)) {
            absorb(java.util.Collections.singletonList(setCookie));
        }
    }

    /**
     * 登录 cookie 打底 + 罐内按名去重合并（罐内优先）
     * 对照 PY _auth_headers_merged / jar_cookie_str 逻辑
     */
    public synchronized String merged(String loginCookie) {
        Map<String, String> out = new LinkedHashMap<>();
        // 先放登录 cookie
        if (!TextUtils.isEmpty(loginCookie)) {
            for (String part : loginCookie.split(";")) {
                String p = part.trim();
                int eq = p.indexOf('=');
                if (eq > 0) {
                    out.put(p.substring(0, eq).trim(), p.substring(eq + 1).trim());
                }
            }
        }
        // 罐内覆盖（罐内优先）
        out.putAll(jar);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : out.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    /** 调试用：输出罐内 cookie 名 */
    public synchronized String names() {
        return TextUtils.join(",", jar.keySet());
    }

    public synchronized void clear() {
        jar.clear();
    }

    public synchronized int size() {
        return jar.size();
    }
}
