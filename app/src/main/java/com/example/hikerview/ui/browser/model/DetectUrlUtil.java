package com.example.hikerview.ui.browser.model;

import com.example.hikerview.utils.StringUtil;

import java.util.Arrays;

/**
 * 照官 8.83 本体移植（jadx 官包源）——本批只落 UrlDetector 判定链
 * 用到的 getNeedCheckUrl（去协议头、去域名段，余下路径参与判定），
 * 逐字照官（官 ScanDeviceUtil.HTTP 即 "http://" 字面值）。
 * detectVideoComplex 等嗅探族耦合官请求栈，未落，记名在案。
 */
public class DetectUrlUtil {

    public static String getNeedCheckUrl(String str) {
        String replace = str.replace("http://", "").replace("https://", "");
        if (!replace.contains("/")) {
            return replace;
        }
        String[] split = replace.split("/");
        if (split.length > 1) {
            return StringUtil.listToString(Arrays.asList(split), 1, "/");
        }
        return (split.length >= 1 && new StringBuilder().append(split[0]).append("/").toString().equals(replace)) ? "" : replace;
    }
}
