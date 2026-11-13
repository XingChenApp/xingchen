package com.fongmi.android.tv.bean;

import java.util.Locale;

/**
 * 插件源种类共用定义：目录／后缀／动态源 key 前缀一处收口。
 * 此前种类口径散在各面各自硬编码（PluginActivity 的 isPy 二分、SourcePolicyActivity
 * 的后缀与前缀、VodFragment／HomePrewarm 的拼 Site 口径……），加 HTML 第三类时
 * 在此定一处、各面照此取，不再逐面各写一份。
 * 取值与 SourceProxyPolicy.KIND_PY／KIND_JS／KIND_HTML 同字面同义
 * （定向存储 kind 与插件 type 同名）。
 */
public final class PluginKinds {

    public static final String PY = "py";
    public static final String JS = "js";
    public static final String HTML = "html";

    private PluginKinds() {
    }

    /** 插件目录（相对 filesDir）：plugins/py、plugins/js、plugins/html。 */
    public static String dirOf(String kind) {
        return "plugins/" + kind;
    }

    /** 文件后缀（含点）：.py、.js、.html。 */
    public static String extOf(String kind) {
        return "." + kind;
    }

    /** 动态源 key 前缀：py_、js_、html_。 */
    public static String keyPrefixOf(String kind) {
        return kind + "_";
    }

    /** 文件名属哪类（后缀判定，大小写不敏感；.wv 归 JS 家族）；都不中返回 null。 */
    public static String kindOfFileName(String name) {
        if (name == null) return null;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".py")) return PY;
        if (n.endsWith(".js") || n.endsWith(".wv")) return JS;
        if (n.endsWith(".html")) return HTML;
        return null;
    }

    /** 动态源 key 属哪类（按 key 前缀）；都不中返回 null。 */
    public static String kindOfKey(String key) {
        if (key == null) return null;
        if (key.startsWith("py_")) return PY;
        if (key.startsWith("js_")) return JS;
        if (key.startsWith("html_")) return HTML;
        return null;
    }
}
