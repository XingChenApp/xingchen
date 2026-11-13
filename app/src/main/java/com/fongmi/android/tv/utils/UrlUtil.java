package com.fongmi.android.tv.utils;

import android.net.Uri;

import com.fongmi.android.tv.server.Server;
import com.github.catvod.utils.UriUtil;
import com.google.common.net.HttpHeaders;

public class UrlUtil {

    public static Uri uri(String url) {
        return Uri.parse(url.trim().replace("\\", ""));
    }

    public static String scheme(String url) {
        return url == null ? "" : scheme(Uri.parse(url));
    }

    public static String scheme(Uri uri) {
        String scheme = uri.getScheme();
        return scheme == null ? "" : scheme.toLowerCase().trim();
    }

    public static String host(String url) {
        return url == null ? "" : host(Uri.parse(url));
    }

    public static String host(Uri uri) {
        String host = uri.getHost();
        return host == null ? "" : host.toLowerCase().trim();
    }

    public static String path(String url) {
        return url == null ? "" : path(Uri.parse(url));
    }

    public static String path(Uri uri) {
        String path = uri.getLastPathSegment();
        return path == null ? "" : path.trim();
    }

    public static String resolve(String baseUri, String referenceUri) {
        return UriUtil.resolve(baseUri, referenceUri);
    }

    public static String convert(String url) {
        String scheme = scheme(url);
        String path = null;
        if ("assets".equals(scheme) || "asset".equals(scheme)) path = "/";
        else if ("file".equals(scheme)) path = "/file/";
        else if ("clan".equals(scheme)) path = "/file/";
        else if ("proxy".equals(scheme)) path = "/proxy?";
        if (path == null) return url;
        if ("clan".equals(scheme)) {
            // clan://localhost/<路径> 与 file://<路径> 同义：localhost 只是占位主机名，
            // 剥掉后剩下的就是存储根起的路径，与 file 形态逐字汇合到 /file/ 空间。
            String rest = url.substring(url.indexOf("://") + 3);
            if (rest.regionMatches(true, 0, "localhost/", 0, 10)) rest = rest.substring(10);
            return Server.get().getAddress(path) + rest;
        }
        return url.replace(scheme + "://", Server.get().getAddress(path));
    }

    public static String getName(String url) {
        Uri uri = Uri.parse(url);
        String path = path(uri);
        String host = host(uri);
        return !path.isEmpty() ? path : !host.isEmpty() ? host : url;
    }

    public static String fixHeader(String key) {
        if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key)) return HttpHeaders.USER_AGENT;
        if (HttpHeaders.REFERER.equalsIgnoreCase(key)) return HttpHeaders.REFERER;
        if (HttpHeaders.COOKIE.equalsIgnoreCase(key)) return HttpHeaders.COOKIE;
        return key;
    }
}
