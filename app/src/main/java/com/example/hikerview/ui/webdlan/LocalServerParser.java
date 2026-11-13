package com.example.hikerview.ui.webdlan;

import android.content.Context;

import com.example.hikerview.utils.StringUtil;

/**
 * 照官 8.83 本体按需转写（官源 smali_classes2/com/example/hikerview/ui/webdlan/LocalServerParser.smali）。
 * 本批只落 HeavyTaskUtil 播放进度链用到的 realUrl 字段与 getUrlForPos 一法，逐字照官
 * （Context 入参在官本体即未用，照官保留签名）；官类其余法未落、记名在案。
 */
public class LocalServerParser {

    private static final String TAG = "LocalServerParser";

    private static String realUrl;

    public static String getUrlForPos(Context context, String url) {
        if (StringUtil.isEmpty(url)) {
            return url;
        }
        String[] split = url.split(":");
        if (split.length > 2 && StringUtil.isNotEmpty(realUrl) && split[2].startsWith("11111/")) {
            return realUrl;
        }
        return url;
    }
}
