package com.fongmi.android.tv.api.hk;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 海阔 URL 模板展开 + 简单 GET（设计文档 §4.2）。
 * <p>
 * 支持的替换词：
 * <ul>
 * <li>{@code fyclass/fyarea/fyyear/fysort} 分类/地区/年份/排序</li>
 * <li>{@code fyAll} 通用替换（取分类值）</li>
 * <li>{@code fypage} 页码，支持算术后缀 {@code fypage@-1@*20@}（即 (page-1)*20 → 0,20,40…）</li>
 * <li>{@code [firstPage=xxx]} page==1 时用括号内模板，否则去掉</li>
 * <li>{@code hiker://empty#真实地址} 取 # 后的真实地址</li>
 * <li>{@code **} 搜索关键词占位（URL 编码后替换）</li>
 * </ul>
 */
public class HkHttp {

    private static final String EMPTY_PREFIX = "hiker://empty#";
    private static final Pattern FIRST_PAGE = Pattern.compile("\\[firstPage=([^\\]]*)\\]");
    /** fypage@-1@*20@ 即 (page-1)*20；各 op 以 @ 分隔：@op1@op2@… */
    private static final Pattern FYPAGE_EX = Pattern.compile("fypage@((?:[-+*/]\\d+@)+)");
    private static final Pattern FYPAGE_OP = Pattern.compile("([-+*/])(\\d+)@");

    private static final String DEFAULT_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static volatile OkHttpClient client;

    private static OkHttpClient client() {
        if (client == null) {
            synchronized (HkHttp.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .followRedirects(true)
                            .build();
                }
            }
        }
        return client;
    }

    /**
     * 展开首页/分类 URL 模板。
     *
     * @param template 规则 url 模板
     * @param cls      分类值（fyclass/fyAll）
     * @param area     地区值（fyarea）
     * @param year     年份值（fyyear）
     * @param sort     排序值（fysort）
     * @param page     页码（从 1 开始）
     */
    public static String expandUrl(String template, String cls, String area, String year, String sort, int page) {
        return expandUrl(template, cls, area, year, sort, page, true);
    }

    /**
     * 展开首页/分类 URL 模板。
     *
     * @param stripEmptyPrefix 是否剥离 {@code hiker://empty#} 前缀。
     *                       js: 规则必须传 false——规则 JS 里常写
     *                       {@code MY_URL.replace("hiker://empty##", host)}，
     *                       MY_URL 必须是原始 url，提前剥离会破坏替换逻辑。
     */
    public static String expandUrl(String template, String cls, String area, String year, String sort, int page, boolean stripEmptyPrefix) {
        if (template == null) return "";
        String url = template.trim();
        if (stripEmptyPrefix && url.startsWith(EMPTY_PREFIX)) url = url.substring(EMPTY_PREFIX.length());
        Matcher fm = FIRST_PAGE.matcher(url);
        if (fm.find()) {
            String first = fm.group(1);
            url = page <= 1 ? first : fm.replaceAll("");
            if (stripEmptyPrefix && url.startsWith(EMPTY_PREFIX)) url = url.substring(EMPTY_PREFIX.length());
        }
        url = url.replace("fyclass", safe(cls));
        url = url.replace("fyarea", safe(area));
        url = url.replace("fyyear", safe(year));
        url = url.replace("fysort", safe(sort));
        url = url.replace("fyAll", safe(cls));
        Matcher pm = FYPAGE_EX.matcher(url);
        StringBuffer sb = new StringBuffer();
        while (pm.find()) {
            pm.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(applyOps(page, pm.group(1)))));
        }
        pm.appendTail(sb);
        return sb.toString().replace("fypage", String.valueOf(page));
    }

    /** 展开搜索 URL：先按 expandUrl 处理，再把 ** 换成编码后的关键词。 */
    public static String expandSearchUrl(String template, String keyword, int page) {
        return expandSearchUrl(template, keyword, page, true);
    }

    /** 展开搜索 URL；stripEmptyPrefix=false 时保留 hiker://empty# 前缀（js: 规则用）。 */
    public static String expandSearchUrl(String template, String keyword, int page, boolean stripEmptyPrefix) {
        String url = expandUrl(template, "", "", "", "", page, stripEmptyPrefix);
        String kw = keyword == null ? "" : keyword;
        try {
            kw = URLEncoder.encode(kw, "UTF-8");
        } catch (Exception ignored) {
        }
        return url.replace("**", kw);
    }

    private static int applyOps(int page, String ops) {
        int v = page;
        if (ops == null) return v;
        Matcher m = FYPAGE_OP.matcher(ops);
        while (m.find()) {
            int n;
            try {
                n = Integer.parseInt(m.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            switch (m.group(1)) {
                case "+":
                    v += n;
                    break;
                case "-":
                    v -= n;
                    break;
                case "*":
                    v *= n;
                    break;
                case "/":
                    if (n != 0) v /= n;
                    break;
                default:
                    break;
            }
        }
        return v;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * URL 增强解析结果（官方格式 {@code url;method;encoding;{K@V&&K2@V2}}）。
     */
    public static class Enhancement {
        public String url = "";
        public String method = "GET";
        public String charset = "UTF-8";
        public final Map<String, String> headers = new HashMap<>();

        /** 是否为"干净"请求（无增强，按原逻辑走即可）。 */
        public boolean isPlain() {
            return "GET".equals(method) && "UTF-8".equalsIgnoreCase(charset) && headers.isEmpty();
        }
    }

    /**
     * 解析 URL 增强：{@code url;method;encoding;{K@V&&K2@V2}}。
     * header 块用 {@code {}} 包裹、内以 {@code &&} 分隔、键值以 {@code @} 分隔。
     */
    public static Enhancement parseEnhancement(String raw) {
        Enhancement e = new Enhancement();
        if (raw == null) return e;
        String u = raw.trim();
        int b = u.indexOf('{');
        int be = u.lastIndexOf('}');
        if (b > 0 && be > b) {
            for (String kv : u.substring(b + 1, be).split("&&")) {
                int at = kv.indexOf('@');
                if (at > 0) e.headers.put(kv.substring(0, at).trim(), kv.substring(at + 1).trim());
            }
            u = (u.substring(0, b) + u.substring(be + 1)).trim();
        }
        String[] parts = u.split(";", -1);
        e.url = parts[0].trim();
        if (parts.length > 1 && !parts[1].trim().isEmpty()) e.method = parts[1].trim().toUpperCase();
        if (parts.length > 2 && !parts[2].trim().isEmpty()) e.charset = parts[2].trim();
        return e;
    }

    /**
     * 简单 GET 请求，返回文本。
     *
     * @throws IOException        网络失败/非 2xx/空 body
     * @throws IllegalArgumentException 非 http(s) 链接（hiker://、js: 等需 M2 的 JS 运行时处理）
     */
    public static String get(String url, String ua) throws IOException {
        Enhancement e = parseEnhancement(url);
        if (e.isPlain()) return getPlain(e.url, ua);
        return getEnhanced(e, ua);
    }

    /**
     * 下载二进制（图片解密用）。支持 headers map。
     */
    public static byte[] getBytes(String url, Map<String, String> headers) throws IOException {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("不支持的链接协议: " + url);
        }
        Request.Builder rb = new Request.Builder().url(url)
                .header("User-Agent", DEFAULT_UA)
                .header("Accept", "image/*,*/*;q=0.8");
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                try {
                    rb.header(h.getKey(), h.getValue());
                } catch (Throwable ignored) {
                }
            }
        }
        try (Response resp = client().newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + " " + url);
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("空响应 " + url);
            return body.bytes();
        }
    }

    /** 无增强的原逻辑（行为与之前完全一致）。 */
    private static String getPlain(String url, String ua) throws IOException {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("不支持的链接协议（需 M2 JS 运行时）：" + url);
        }
        Request req = new Request.Builder().url(url)
                .header("User-Agent", ua == null || ua.isEmpty() ? DEFAULT_UA : ua)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build();
        try (Response resp = client().newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + " " + url);
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("空响应 " + url);
            return body.string();
        }
    }

    /** 带 method/encoding/headers 的增强请求。 */
    private static String getEnhanced(Enhancement e, String ua) throws IOException {
        String url = e.url;
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("不支持的链接协议（需 M2 JS 运行时）：" + url);
        }
        Request.Builder rb = new Request.Builder().url(url)
                .header("User-Agent", ua == null || ua.isEmpty() ? DEFAULT_UA : ua)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9");
        for (Map.Entry<String, String> h : e.headers.entrySet()) {
            try {
                rb.header(h.getKey(), h.getValue());
            } catch (Throwable ignored) {
            }
        }
        if ("POST".equals(e.method)) {
            rb.post(RequestBody.create(new byte[0], null));
        }
        try (Response resp = client().newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + " " + url);
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("空响应 " + url);
            byte[] bytes = body.bytes();
            try {
                return new String(bytes, e.charset);
            } catch (Throwable t) {
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
    }
}
