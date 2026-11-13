package com.example.hikerview.utils;

import com.example.hikerview.ui.browser.model.UrlDetector;
import com.google.android.exoplayer2.util.FileTypes;

import java.util.List;
import java.util.Map;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/HttpUtil.smali）——
 * 本批落嗅探栈用到的 isFuckImage 及同类小件 getRealUrl、两常数
 * （HTML=-101、M3U8=-100，照官字段初值）。
 *
 * <p>inferFileTypeFromResponse 已补落（hk-14 五团）：尾步所赖官
 * ExoPlayer2 的 com.google.android.exoplayer2.util.FileTypes 已照官
 * FQN 部分转写落码（inferFileTypeFromResponseHeaders＋
 * inferFileTypeFromMimeType 两法与全表，见该类类头记名），本法照官
 * 逐字调它，不用本树 media3 的不等值表。
 */
public class HttpUtil {

    public static final int HTML = -101;
    public static final int M3U8 = -100;

    public static String getRealUrl(String str, String str2) {
        String baseUrl = StringUtil.getBaseUrl(str);
        if (str2.startsWith("http")) {
            return str2;
        }
        if (str2.startsWith("//")) {
            return "http:" + str2;
        }
        if (str2.startsWith("magnet") || str2.startsWith("thunder") || str2.startsWith("ftp") || str2.startsWith("ed2k")) {
            return str2;
        }
        if (str2.startsWith("/")) {
            if (baseUrl.endsWith("/")) {
                return baseUrl.replace("/", "") + str2;
            }
            return baseUrl + str2;
        }
        if (str2.startsWith("./")) {
            String[] split = str.split(";")[0].split("/");
            if (split.length <= 1) {
                return str2;
            }
            return str.split(";")[0].replace(split[split.length - 1], "") + str2.replace("./", "");
        }
        if (str2.startsWith("?")) {
            return str + str2;
        }
        return str2;
    }

    public static int inferFileTypeFromResponse(String str, Map<String, List<String>> map) {
        if (map == null) {
            return -1;
        }
        List<String> list = map.get("Content-Type");
        if (list == null && map.containsKey("content-type")) {
            list = map.get("content-type");
        }
        String str2 = (list == null || list.isEmpty()) ? null : list.get(0);
        if (isFuckImage(str, str2)) {
            return -100;
        }
        if ("text/html".equals(str2)) {
            return -101;
        }
        if (str2 != null && str2.contains(";")) {
            list.add(str2.split(";")[0]);
            list.remove(0);
        }
        return FileTypes.inferFileTypeFromResponseHeaders(map);
    }

    public static boolean isFuckImage(String str, String str2) {
        if (str2 == null) {
            return false;
        }
        if (str2.contains("text/html") && str.contains(".m3u8")) {
            return true;
        }
        return !UrlDetector.isImage(str) && (str2.contains("image/") || "image/jpeg".equals(str2));
    }
}
