package com.fongmi.android.tv.ui.hk;

import com.fongmi.android.tv.api.hk.HkHttpParser;

import java.util.HashMap;
import java.util.Map;

/**
 * 8.83 {@code ui/video/PlayerChooser} 中网页桥用到的三法转写（getHeaderMap / getReferer /
 * decorateHeader0，含私有 isSafe），供 fy_bridge_app 的 getHeaderUrl / clearM3u8Ad 使用。
 * decorateHeader0 里查 DetectorManager 嗅探结果补 "#.m3u8" 的一支：咱家无进程级
 * DetectedMediaResult 存储（嗅探结果只活在解析期），该支恒不触发（记名，见第八阶段回执）。
 */
public final class HkPlayerChooser {

    private HkPlayerChooser() {
    }

    /** PlayerChooser.getHeaderMap（smali ui/video/PlayerChooser:452-476）。 */
    public static Map<String, String> getHeaderMap(Map<String, String> map, String referer,
                                                   String url, String cookie) {
        Map<String, String> out = new HashMap<>();
        if (HkStringUtil.isEmpty(url)) return out;
        if (map != null) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                String key = e.getKey();
                if ("Accept".equalsIgnoreCase(key)) continue;
                if ("Range".equalsIgnoreCase(key)) continue;
                if ("Upgrade-Insecure-Requests".equalsIgnoreCase(key)) continue;
                out.put(key, e.getValue());
            }
        }
        if (HkStringUtil.isNotEmpty(cookie) && !out.containsKey("Cookie")) {
            out.put("Cookie", cookie);
        }
        if (HkStringUtil.isNotEmpty(referer) && !out.containsKey("Referer")) {
            out.put("Referer", referer);
        }
        return out;
    }

    /** PlayerChooser.getReferer（smali ui/video/PlayerChooser:348-412）。 */
    public static String getReferer(Map<String, String> map, String policy0,
                                    String pageUrl, String targetUrl) {
        if (map != null && map.containsKey("Referer")) return map.get("Referer");
        if (map != null && map.containsKey("referer")) return map.get("referer");
        String policy = map == null ? null : map.get("Referrer-Policy");
        if (HkStringUtil.isEmpty(policy) && map != null) {
            policy = map.get("referrer-policy");
        }
        if (HkStringUtil.isEmpty(policy) && HkStringUtil.isNotEmpty(policy0)) {
            policy = policy0;
        }
        if (HkStringUtil.isEmpty(policy)) policy = "no-referrer-when-downgrade";
        switch (policy) {
            case "no-referrer":
                return null;
            case "unsafe-url":
                return pageUrl;
            case "origin":
                if (HkStringUtil.isNotEmpty(pageUrl)) return HkStringUtil.getHome(pageUrl) + "/";
                return pageUrl;
            case "origin-when-cross-origin":
                if (HkStringUtil.isNotEmpty(pageUrl)
                        && !HkStringUtil.getHome(pageUrl).equals(HkStringUtil.getHome(targetUrl))) {
                    return HkStringUtil.getHome(pageUrl) + "/";
                }
                return pageUrl;
            case "strict-origin":
                // 官 pswitch_4：pageUrl 空或不安全都回 null，在且安全才回 home+"/"
                if (HkStringUtil.isNotEmpty(pageUrl)) {
                    if (!isSafe(pageUrl, targetUrl)) return null;
                    return HkStringUtil.getHome(pageUrl) + "/";
                }
                return null;
            case "same-origin":
                if (HkStringUtil.isNotEmpty(pageUrl)
                        && !HkStringUtil.getHome(pageUrl).equals(HkStringUtil.getHome(targetUrl))) {
                    return null;
                }
                return pageUrl;
            case "strict-origin-when-cross-origin":
                if (HkStringUtil.isNotEmpty(pageUrl)
                        && !HkStringUtil.getHome(pageUrl).equals(HkStringUtil.getHome(targetUrl))) {
                    if (!isSafe(pageUrl, targetUrl)) return null;
                    return HkStringUtil.getHome(pageUrl) + "/";
                }
                return pageUrl;
            default:
                // no-referrer-when-downgrade 与未识策略同支（官 packed-switch 缺省支）
                if (HkStringUtil.isNotEmpty(pageUrl) && !isSafe(pageUrl, targetUrl)) return null;
                if (HkStringUtil.isNotEmpty(pageUrl)
                        && !HkStringUtil.getHome(pageUrl).equals(HkStringUtil.getHome(targetUrl))) {
                    return HkStringUtil.getHome(pageUrl) + "/";
                }
                return pageUrl;
        }
    }

    /** PlayerChooser.isSafe（smali ui/video/PlayerChooser:414）：https 页拉非 https 目标才算不安全。 */
    private static boolean isSafe(String pageUrl, String targetUrl) {
        if (HkStringUtil.isNotEmpty(pageUrl) && pageUrl.startsWith("https")
                && (targetUrl == null || !targetUrl.startsWith("https"))) {
            return false;
        }
        return true;
    }

    /** PlayerChooser.decorateHeader0（smali ui/video/PlayerChooser:423-449）。 */
    public static String decorateHeader0(Map<String, String> map, String ua,
                                         String url, String cookie) {
        if (HkStringUtil.isEmpty(url)) return url;
        Map<String, String> headers = getHeaderMap(map, ua, url, cookie);
        // 官此处还有一支：url 无媒体后缀且嗅探结果里同 url 被判 m3u8 时给 url 补 "#.m3u8"；
        // 咱家无 DetectedMediaResult 存储，该支恒不触发（见类头记名）。
        if (headers.isEmpty()) return url;
        return url + ";" + HkHttpParser.getHeadersStr(headers);
    }
}
