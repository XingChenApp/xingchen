package com.fongmi.android.tv.web;

import com.fongmi.android.tv.bean.PluginKinds;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 桥方言型 HTML 源的静态打标（S1，账 html-jhs-playbtn-bridge-20261111 §六）：
 * 导入的 HTML 源文件在本地，扫其脚本引用的 fm.* 顶层名集，与 SDK 核心键集
 * （HomeWebController.getSdk 的 window.fm 核心键）求差——差集非空即「桥方言型」：
 * 页面把点播收敛到核心键集之外的桥名（如 fm.openUrl）或其 window.open 回落上，
 * 裸 WebView 里永远播不起来，点播步要靠嗅探接管（见 HomeWebController 的接管口）。
 * openUrl／toast 是本批补的兼容别名、刻意不进核心键集：引用它们的页面仍是
 * 方言型（别名救不了在解析期已把 window.fm 捕获成 null 的页面），照样打标。
 * 顺带从文件内容里取「内容主域」：非素材类 URL 里出现最多的 host（如
 * www.javrate.com，图片 CDN 那类素材 host 不计），作接管判据 S4 的同域收窄用。
 * 纯 Java 实现（只依赖 PluginKinds），可离线单测；结果按 site key＋文件戳缓存。
 */
public final class DialectSourceTag {

    /** SDK 核心键集：与 HomeWebController.getSdk() 里 window.fm 的核心键逐字对位（别名键不算）。 */
    static final Set<String> CORE_KEYS = Set.of(
            "req", "res", "play", "vod", "vodInline", "preloadArtwork", "ctrl", "stat",
            "search", "openVod", "openLive", "openKeep", "openSetting", "history",
            "pan", "check", "cache", "ui", "device", "site", "config", "back", "reload");

    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final Pattern FM_REF = Pattern.compile("\\bfm\\.([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern URL_REF = Pattern.compile("https?://([A-Za-z0-9.-]+)(/[^\"'\\s<>]*)?");
    private static final Set<String> ASSET_EXT = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".svg", ".ico",
            ".css", ".js", ".woff", ".woff2", ".ttf", ".mp4", ".m3u8", ".ts");
    private static final Set<String> IGNORED_HOSTS = Set.of(
            "www.w3.org", "w3.org", "localhost", "127.0.0.1");

    private static final Map<String, DialectSourceTag> CACHE = new ConcurrentHashMap<>();

    private final String siteKey;
    private final String apiPath;
    private final String stamp;
    private final Set<String> dialectNames;
    private final String contentHost;

    private DialectSourceTag(String siteKey, String apiPath, String stamp, Set<String> dialectNames, String contentHost) {
        this.siteKey = siteKey;
        this.apiPath = apiPath;
        this.stamp = stamp;
        this.dialectNames = dialectNames;
        this.contentHost = contentHost;
    }

    public boolean isDialect() {
        return !dialectNames.isEmpty();
    }

    /** 差集（核心键集之外的 fm.* 名），已排序，只读。 */
    public Set<String> getDialectNames() {
        return dialectNames;
    }

    /** 内容主域（非素材 URL 里出现最多的 host）；取不到为 null，此时 S4 同域项无从验明、判据按不成立处理（judge 拒绝接管）。 */
    public String getContentHost() {
        return contentHost;
    }

    /** 缓存窥视（不做 IO，文件戳现算除外）：命中且戳仍有效才返回，未命中返回 null。 */
    public static DialectSourceTag peek(String siteKey, String apiPath) {
        if (siteKey == null) return null;
        DialectSourceTag tag = CACHE.get(siteKey);
        if (tag == null || !tag.matches(apiPath)) return null;
        return tag;
    }

    /** 解析（缓存优先，未命中同步扫文件——调用方须在可 IO 的线程上，如桥线程／后台线程）。 */
    public static DialectSourceTag resolve(String siteKey, String apiPath) {
        DialectSourceTag tag = peek(siteKey, apiPath);
        if (tag != null) return tag;
        tag = scan(siteKey, apiPath);
        if (siteKey != null) CACHE.put(siteKey, tag);
        return tag;
    }

    private boolean matches(String path) {
        if (path == null ? apiPath != null : !path.equals(apiPath)) return false;
        return stamp.equals(stampOf(apiPath));
    }

    private static String stampOf(String apiPath) {
        if (apiPath == null) return "";
        File file = new File(apiPath);
        if (!file.isFile()) return "";
        return file.length() + ":" + file.lastModified();
    }

    static DialectSourceTag scan(String siteKey, String apiPath) {
        String stamp = stampOf(apiPath);
        // 只认导入 HTML 源（key 前缀 html_）且 api 是本地文件；其余一律不打标、不读文件。
        if (!PluginKinds.HTML.equals(PluginKinds.kindOfKey(siteKey)) || stamp.isEmpty()) {
            return new DialectSourceTag(siteKey, apiPath, stamp, Collections.emptySet(), null);
        }
        String text = readText(new File(apiPath));
        if (text == null) {
            return new DialectSourceTag(siteKey, apiPath, stamp, Collections.emptySet(), null);
        }
        Set<String> refs = new TreeSet<>();
        Matcher fm = FM_REF.matcher(text);
        while (fm.find()) refs.add(fm.group(1));
        refs.removeAll(CORE_KEYS);
        return new DialectSourceTag(siteKey, apiPath, stamp,
                Collections.unmodifiableSet(refs), dominantHost(text));
    }

    private static String readText(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[MAX_BYTES + 1];
            int total = 0;
            int read;
            while (total < buffer.length && (read = in.read(buffer, total, buffer.length - total)) > 0) total += read;
            int length = Math.min(total, MAX_BYTES);
            return new String(buffer, 0, length, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 内容主域：非素材类 URL 的 host 计数取最大（图片/CDN 素材 host 不计，避免把图床误当内容域）。 */
    private static String dominantHost(String text) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Matcher matcher = URL_REF.matcher(text);
        while (matcher.find()) {
            String host = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            if (IGNORED_HOSTS.contains(host)) continue;
            if (isAsset(matcher.group(2))) continue;
            counts.merge(host, 1, Integer::sum);
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    private static boolean isAsset(String path) {
        if (path == null || path.isEmpty()) return false;
        int cut = path.length();
        int query = path.indexOf('?');
        if (query >= 0) cut = Math.min(cut, query);
        int hash = path.indexOf('#');
        if (hash >= 0) cut = Math.min(cut, hash);
        String clean = path.substring(0, cut).toLowerCase(java.util.Locale.ROOT);
        for (String ext : ASSET_EXT) if (clean.endsWith(ext)) return true;
        return false;
    }
}
