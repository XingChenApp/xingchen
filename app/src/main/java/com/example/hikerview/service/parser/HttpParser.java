package com.example.hikerview.service.parser;

import android.text.TextUtils;

import com.example.hikerview.utils.StringUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * 照官 8.83 本体按需转写（官源 smali/com/example/hikerview/service/parser/HttpParser.smali）。
 * 落 getParamsByUrl（①团先行：PageParser.parsePageRule 的参数解析底座）与
 * getRealUrlFilterHeaders（逐字照官）、getHeaders/getThirdDownloadSource（DownloadTask.setUrl 链；
 * 本体转调 com.fongmi 侧既有同名逐字转写 HkHttpParser——其 parseHeadersByJs 的 JS 桥接法
 * 在 Hk 侧以可空 jsBridge 接，桥未装时跳过 .js 求值，与官 JSEngine 单例面在此的差记名在
 * HkHttpParser 类头），官类其余法未落、记名在案。
 */
public class HttpParser {

    public static Map<String, String> getParamsByUrl(String url) {
        if (StringUtil.isEmpty(url)) {
            return new HashMap<>();
        }
        String[] split = url.split(";");
        HashMap<String, String> map = new HashMap<>();
        String[] splitUrl = StringUtil.splitUrlByQuestionMark(split[0]);
        String[] pairs;
        if (splitUrl.length > 1) {
            pairs = splitUrl[1].split("&");
        } else {
            pairs = new String[0];
        }
        for (int i = 0; i < pairs.length; i++) {
            if (TextUtils.isEmpty(pairs[i])) {
                continue;
            }
            String[] kv = pairs[i].split("=");
            if (kv.length >= 2) {
                map.put(kv[0], StringUtil.decodeConflictStr(StringUtil.arrayToString(kv, 1, "=")));
            }
        }
        return map;
    }

    public static String getRealUrlFilterHeaders(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        String[] split = url.split(";");
        String last = split[split.length - 1];
        if (last.startsWith("{") && last.endsWith("}")) {
            return split[0];
        }
        return url;
    }

    public static Map<String, String> getHeaders(String url) {
        return com.fongmi.android.tv.api.hk.HkHttpParser.getHeaders(url);
    }

    public static String getThirdDownloadSource(String url) {
        return com.fongmi.android.tv.api.hk.HkHttpParser.getThirdDownloadSource(url);
    }
}
