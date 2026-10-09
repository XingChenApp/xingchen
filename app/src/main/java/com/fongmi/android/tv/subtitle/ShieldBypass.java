package com.fongmi.android.tv.subtitle;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.utils.Prefers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 字幕站过盾：WebView 人工验证后复用 Cookie。
 *
 * 流程：搜索返回的 HTML 命中盾特征 -> 记为 blocked ->
 * 字幕搜索弹窗提示用户 -> 打开 ShieldWebViewDialog 人工过验证 ->
 * 取 CookieManager 的 Cookie 存入 Prefers -> 后续请求自动带 Cookie。
 */
public class ShieldBypass {

    private static final String KEY_PREFIX = "shield_cookie_";

    /** 命中任意一条即判定为盾页（小写匹配） */
    private static final String[] SHIELD_MARKERS = {
            "just a moment",
            "challenge-platform",
            "/cdn-cgi/challenge",
            "cf-chl",
            "cf_clearance",
            "attention required",
            "verify you are human",
            "checking your browser",
            "checking if the site connection is secure",
            "人机验证",
            "安全验证",
            "滑动验证",
            "点击验证",
            "captcha",
    };

    private static final Set<String> BLOCKED = Collections.synchronizedSet(new HashSet<>());

    /** providerId -> 人工验证入口 URL */
    public static String verifyUrl(String providerId) {
        switch (providerId) {
            case "subhd":
                return "https://subhd.tv/";
            case "shooter":
                return "https://assrt.net/";
            case "zimuku":
                return "https://zimuku.org/";
            default:
                return "";
        }
    }

    /** providerId -> 显示名 */
    public static String providerName(String providerId) {
        switch (providerId) {
            case "subhd":
                return "SubHD";
            case "shooter":
                return "射手网";
            case "zimuku":
                return "字幕库";
            default:
                return providerId;
        }
    }

    /** 判定 HTML 是否为盾页 */
    public static boolean isShieldPage(String html) {
        if (TextUtils.isEmpty(html)) return false;
        String lower = html.toLowerCase(Locale.US);
        for (String marker : SHIELD_MARKERS) {
            if (lower.contains(marker)) return true;
        }
        return false;
    }

    /** 从 URL 取 host */
    public static String hostOf(String url) {
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? "" : host;
        } catch (Throwable e) {
            return "";
        }
    }

    /** 取已存的 Cookie（可直接作为 Cookie 请求头） */
    public static String getCookie(String host) {
        if (TextUtils.isEmpty(host)) return "";
        return Prefers.getString(KEY_PREFIX + host, "");
    }

    /** 保存 WebView 验证后拿到的 Cookie */
    public static void saveCookie(String host, String cookie) {
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(cookie)) return;
        Prefers.put(KEY_PREFIX + host, cookie);
    }

    /** 清掉某 host 的 Cookie（验证过期时用） */
    public static void clearCookie(String host) {
        if (!TextUtils.isEmpty(host)) Prefers.remove(KEY_PREFIX + host);
    }

    /** 给请求头注入已存 Cookie（无 Cookie 时不加头） */
    public static void injectCookie(Map<String, String> headers, String host) {
        if (headers == null) return;
        String cookie = getCookie(host);
        if (!TextUtils.isEmpty(cookie)) headers.put("Cookie", cookie);
    }

    /** 标记某源本次搜索被盾拦截 */
    public static void setBlocked(String providerId) {
        if (!TextUtils.isEmpty(providerId)) BLOCKED.add(providerId);
    }

    /** 本次搜索被盾拦截的源（有序） */
    public static List<String> getBlocked() {
        List<String> list = new ArrayList<>(BLOCKED);
        Collections.sort(list);
        return list;
    }

    public static boolean hasBlocked() {
        return !BLOCKED.isEmpty();
    }

    /** 搜索开始时清掉标记 */
    public static void clearBlocked() {
        BLOCKED.clear();
    }
}
