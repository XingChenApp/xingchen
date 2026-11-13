package com.example.hikerview.utils.contentdisposition;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * MimeParameterMapping.smali 等同名 smali 共 264 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
public class MimeParameterMapping {
    private final Map<String, String> parameters = new HashMap();

    public Map<String, String> getParameters() {
        HashMap hashMap = new HashMap();
        for (Map.Entry<String, String> entry : this.parameters.entrySet()) {
            hashMap.put(entry.getKey(), decodeParameterValue(entry.getValue()));
        }
        return hashMap;
    }

    public void addParameter(String str, String str2) {
        String lowerCase = removeSectionFromName(str).toLowerCase();
        if (this.parameters.containsKey(lowerCase)) {
            this.parameters.put(lowerCase, this.parameters.get(lowerCase) + str2);
        } else {
            this.parameters.put(lowerCase, str2);
        }
    }

    private String decodeParameterValue(String str) {
        if (str == null) {
            return null;
        }
        int indexOf = str.indexOf("'");
        int indexOf2 = str.indexOf("'", indexOf + 1);
        if (indexOf < 0 || indexOf2 < 0) {
            return MimeUtil.unscrambleHeaderValue(str);
        }
        try {
            return URLDecoder.decode(str.substring(indexOf2 + 1), str.substring(0, indexOf));
        } catch (UnsupportedEncodingException unused) {
            return MimeUtil.unscrambleHeaderValue(str);
        }
    }

    private String removeSectionFromName(String str) {
        int indexOf = str.indexOf(42);
        if (indexOf < 0) {
            indexOf = str.length();
        }
        return str.substring(0, indexOf);
    }
}
