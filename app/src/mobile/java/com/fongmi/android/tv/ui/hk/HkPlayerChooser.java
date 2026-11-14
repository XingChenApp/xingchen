package com.fongmi.android.tv.ui.hk;

import android.app.Activity;

import com.fongmi.android.tv.api.hk.HkHttpParser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 8.83 {@code ui/video/PlayerChooser} 中网页桥用到的三法转写（getHeaderMap / getReferer /
 * decorateHeader0，含私有 isSafe），供 fy_bridge_app 的 getHeaderUrl / clearM3u8Ad 使用。
 * decorateHeader0 里查 DetectorManager 嗅探结果补 "#.m3u8" 的一支：咱家无进程级
 * DetectedMediaResult 存储（嗅探结果只活在解析期），该支恒不触发（记名，见第八阶段回执）。
 *
 * <p>另含 E1 尾巴的当前页 Wrapper 来源口（136b 簇2）：官 decorateHeader(Activity…)
 * 从当前 WebView 的 WebViewHelper 直取请求头表/referer 策略；咱家等价物是
 * HkWebkitHolder 的私有 Wrapper（{@link HkWebViewWrapper}），公开面只能走到
 * HkWebkitHolder 本体、最后一跳无公开取口，故在此设可替换 provider＋按
 * Activity 登记表两口，登记一行待主线在 HkWebkitHolder.initWebView 补
 * （见 worknotes/fill-136b-c2-player.md「主线缝」）。
 */
public final class HkPlayerChooser {

    private HkPlayerChooser() {
    }

    /** 当前页 Wrapper 的可替换来源：按 Activity 回其当前页的 Wrapper，取不到回 null。 */
    public interface CurrentWrapperProvider {
        HkWebViewWrapper provide(Activity activity);
    }

    private static volatile CurrentWrapperProvider currentWrapperProvider;

    /** 按 Activity 的当前页 Wrapper 登记表（弱键：宿主销毁后条目随之失效）。 */
    private static final Map<Activity, HkWebViewWrapper> CURRENT_WRAPPERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 换来源实现（主线若改走 FragmentManager 直查等他路，只换此口、消费方不动）。 */
    public static void setCurrentWrapperProvider(CurrentWrapperProvider provider) {
        currentWrapperProvider = provider;
    }

    /** 登记某 Activity 当前页的 Wrapper（同宿主后登记覆盖先登记，与官「当前 WebView」同义）。 */
    public static void registerCurrentWrapper(Activity activity, HkWebViewWrapper wrapper) {
        if (activity == null || wrapper == null) return;
        CURRENT_WRAPPERS.put(activity, wrapper);
    }

    /** 注销：仅当登记在案的正是该 Wrapper 时才清（别页/旧件销毁不误清现行登记）。 */
    public static void unregisterCurrentWrapper(Activity activity, HkWebViewWrapper wrapper) {
        if (activity == null || wrapper == null) return;
        synchronized (CURRENT_WRAPPERS) {
            if (CURRENT_WRAPPERS.get(activity) == wrapper) {
                CURRENT_WRAPPERS.remove(activity);
            }
        }
    }

    /**
     * 取某 Activity 当前页的 Wrapper：provider 在位先问 provider（其异常按
     * 取不到处理、回登记表续查），否则查登记表；全无回 null——消费方
     * （PlayerChooser.decorateHeader(Activity…)）按官 helper 缺位形以 null
     * 头表/null 策略走同链。
     */
    public static HkWebViewWrapper currentWrapper(Activity activity) {
        if (activity == null) return null;
        CurrentWrapperProvider provider = currentWrapperProvider;
        if (provider != null) {
            try {
                HkWebViewWrapper wrapper = provider.provide(activity);
                if (wrapper != null) return wrapper;
            } catch (Exception ignored) {
            }
        }
        synchronized (CURRENT_WRAPPERS) {
            return CURRENT_WRAPPERS.get(activity);
        }
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
