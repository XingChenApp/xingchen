package com.fongmi.android.tv.api.hk.browser;

import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import com.fongmi.android.tv.api.hk.HkHttp;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 8.83 com.example.hikerview.ui.browser.service.BrowserProxy 的等价实现：网页代理规则注册表＋消费。
 *
 * <p>注册表三件（与原版同语义）：
 * addProxyRule 同步增改（同名先移除再追加到表尾）；removeProxyRule 按名移除第一个同名；
 * initProxyRules 从 hiker://files/rules/browser_proxy_rules.json 重读整表（refreshWebProxyRule 走这条）。
 * 规则的落盘保存（原版由浏览器侧 UI 触发的 saveFile）星辰无对应 UI 面，规则经 JS 增删后只活在内存，
 * 与原版 JS 侧三件套的可见行为一致（原版 add/remove 本身也不落盘）。
 *
 * <p>消费（原版 BrowserProxy.proxy，smali 932 起）：规则按表序逐条试，match 以 Pattern.matches
 * 全串正则命中请求 URL 才接管；静态扩展名（WHITE 表）请求只认 force=true 的规则；接管后以请求头为底
 * 组装抓取头（缺 Cookie 时从 CookieManager 补、缺 Referer 时补当前页地址），再叠规则的 requestHeaders，
 * 由宿主（HkHttp.client，与海阔其余请求同一 CookieJar）抓取目标地址并组 WebResourceResponse 回给 WebView。
 *
 * <p>replace 的多行 DSL 只复刻已抠实的核心行：整串为空 → 目标＝原 URL（只改头）；
 * 首行 $replace=xxx → 目标＝xxx；首行本身是 http(s) 地址 → 目标＝该行。
 * $host= 行、dns/headers 分段与内嵌 JS 求值行未复刻——命中这类规则时本实现放弃接管（回 null 走
 * WebView 原生加载），不假装等价。
 *
 * <p>responseHeaders 照原版抠实语义落：match 全串正则匹配响应头「名」，replace 为空或与原名
 * 相同则删头，否则把头名改为 replace（值不变）。
 */
public final class HkBrowserProxy {

    /** 原版 WHITE 静态扩展名表（smali &lt;clinit&gt; 逐项抠实，共 32 项）。 */
    private static final String[] WHITE = {
            ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
            ".woff", ".ttf", ".woff2", ".eot", ".otf",
            ".mp4", ".webm", ".ogg", ".mp3", ".wav", ".flac", ".aac", ".m4a",
            ".3gp", ".mov", ".wmv", ".avi", ".swf", ".flv", ".mkv", ".mpeg", ".mpg", ".ogv"
    };

    private static final Gson GSON = new Gson();
    private static List<HkBrowserProxyRule> rules;

    private HkBrowserProxy() {
    }

    /** 原版 BrowserProxy.addProxyRule：synchronized，同名先移除再追加。 */
    public static synchronized void addProxyRule(HkBrowserProxyRule rule) {
        ensureLoaded();
        if (rule == null) return;
        for (int i = rules.size() - 1; i >= 0; i--) {
            if (rule.name != null && rule.name.equals(rules.get(i).name)) rules.remove(i);
        }
        rules.add(rule);
    }

    /** 原版 BrowserProxy.removeProxyRule：按名移除第一个同名即返回。 */
    public static synchronized void removeProxyRule(String name) {
        ensureLoaded();
        if (name == null) return;
        for (int i = 0; i < rules.size(); i++) {
            if (name.equals(rules.get(i).name)) {
                rules.remove(i);
                return;
            }
        }
    }

    /** 原版 BrowserProxy.initProxyRules：从 rules/browser_proxy_rules.json 重读整表。 */
    public static synchronized void initProxyRules() {
        List<HkBrowserProxyRule> loaded = new ArrayList<>();
        try {
            String path = HkJsRuntime.getFilePath("hiker://files/rules/browser_proxy_rules.json");
            File file = path == null ? null : new File(path);
            if (file != null && file.isFile()) {
                String json = readUtf8(file);
                if (!TextUtils.isEmpty(json)) {
                    List<HkBrowserProxyRule> parsed = GSON.fromJson(json,
                            TypeToken.getParameterized(List.class, HkBrowserProxyRule.class).getType());
                    if (parsed != null) loaded = parsed;
                }
            }
        } catch (Throwable ignored) {
            // 原版同口径：读不到/解析失败就是空表，不打断页面
        }
        rules = loaded;
    }

    /** 查询接口：当前规则表快照（副本）。 */
    public static synchronized List<HkBrowserProxyRule> getRules() {
        ensureLoaded();
        return new ArrayList<>(rules);
    }

    /** 查询接口：按 URL 找第一条命中规则（口径同消费：静态资源只认 force 规则）；无命中回 null。 */
    public static HkBrowserProxyRule findRule(String url) {
        if (TextUtils.isEmpty(url)) return null;
        boolean white = isWhite(url);
        for (HkBrowserProxyRule rule : getRules()) {
            if (rule == null || TextUtils.isEmpty(rule.match)) continue;
            if (white && !rule.force) continue;
            try {
                if (Pattern.matches(rule.match, url)) return rule;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void ensureLoaded() {
        if (rules == null) initProxyRules();
    }

    private static String readUtf8(File file) {
        try {
            byte[] bytes = new byte[(int) file.length()];
            try (java.io.InputStream in = new java.io.FileInputStream(file)) {
                int off = 0, n;
                while (off < bytes.length && (n = in.read(bytes, off, bytes.length - off)) > 0) off += n;
            }
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean isWhite(String url) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int h = path.indexOf('#');
        if (h >= 0) path = path.substring(0, h);
        path = path.toLowerCase();
        for (String ext : WHITE) if (path.endsWith(ext)) return true;
        return false;
    }

    /**
     * 消费入口（HkWebkitHolder.shouldInterceptRequest 在 browserProxyHook 未被外部接管时回落到此）：
     * 命中规则则宿主抓取并回 WebResourceResponse；未命中/接管失败回 null 走 WebView 原生加载。
     * 仅接管 GET/HEAD（原版对带 body 的请求同样不接管改写）。
     */
    public static WebResourceResponse proxyRequest(String pageUrl, WebResourceRequest request,
                                                     Map<String, String> baseHeaders) {
        try {
            if (request == null || request.getUrl() == null) return null;
            String method = request.getMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) return null;
            String url = request.getUrl().toString();
            HkBrowserProxyRule rule = findRule(url);
            if (rule == null) return null;
            String target = resolveTarget(rule.replace, url);
            if (target == null) return null; // replace 含未复刻 DSL 行，放弃接管，不假装等价
            Map<String, String> headers = new HashMap<>();
            if (baseHeaders != null) headers.putAll(baseHeaders);
            if (request.getRequestHeaders() != null) headers.putAll(request.getRequestHeaders());
            if (!hasHeader(headers, "Cookie")) {
                try {
                    String cookie = CookieManager.getInstance().getCookie(url);
                    if (!TextUtils.isEmpty(cookie)) headers.put("Cookie", cookie);
                } catch (Throwable ignored) {
                }
            }
            if (!hasHeader(headers, "Referer") && !TextUtils.isEmpty(pageUrl)) {
                headers.put("Referer", pageUrl);
            }
            if (rule.requestHeaders != null) {
                for (Map.Entry<String, String> e : rule.requestHeaders.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) headers.put(e.getKey(), e.getValue());
                }
            }
            return fetch(target, headers, rule);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * replace DSL 的核心行复刻（见类头说明）：空 → 原 URL；首行 $replace= → 其值；
     * 首行 http(s) → 该行；含其它 DSL 行（$host=、$js、多段）→ 回 null 表示未复刻、放弃接管。
     */
    private static String resolveTarget(String replace, String url) {
        if (TextUtils.isEmpty(replace)) return url;
        String[] lines = replace.split("\n");
        String first = lines[0].trim();
        if (first.startsWith("$replace=")) {
            String target = first.substring("$replace=".length()).trim();
            return target.startsWith("http") ? target : null;
        }
        if (first.startsWith("$")) return null;
        if (first.startsWith("http")) {
            // 多行且后续行是 DSL 段时整条规则超出复刻范围
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].trim().startsWith("$")) return null;
            }
            return first;
        }
        return null;
    }

    private static WebResourceResponse fetch(String target, Map<String, String> headers,
                                             HkBrowserProxyRule rule) {
        try {
            Request.Builder builder = new Request.Builder().url(target);
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) builder.header(e.getKey(), e.getValue());
            }
            Response response = HkHttp.client().newCall(builder.build()).execute();
            int code = response.code();
            if (code < 100 || code > 599) return null;
            ResponseBody body = response.body();
            InputStream data = body != null ? body.byteStream() : new ByteArrayInputStream(new byte[0]);
            String mime = "text/html";
            String encoding = "utf-8";
            if (body != null) {
                MediaType type = body.contentType();
                if (type != null) {
                    mime = type.type() + "/" + type.subtype();
                    Charset charset = type.charset();
                    if (charset != null) encoding = charset.name();
                }
            }
            Map<String, String> responseHeaders = new HashMap<>();
            Headers hs = response.headers();
            for (int i = 0; i < hs.size(); i++) {
                // 同名多值头 WebResourceResponse 只能留一份，照原版 put 口径后者覆盖
                responseHeaders.put(hs.name(i), hs.value(i));
            }
            applyResponseHeaderRules(responseHeaders, rule);
            String reason = response.message();
            if (TextUtils.isEmpty(reason)) reason = "OK";
            return new WebResourceResponse(mime, encoding, code, reason, responseHeaders, data);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 原版响应头处理：match 命中头名 → replace 空/同名删头、否则改名（值不变）。 */
    private static void applyResponseHeaderRules(Map<String, String> headers, HkBrowserProxyRule rule) {
        if (rule.responseHeaders == null || rule.responseHeaders.isEmpty()) return;
        for (String name : new ArrayList<>(headers.keySet())) {
            for (HkBrowserProxyHeader item : rule.responseHeaders) {
                if (item == null || item.match == null) continue;
                boolean matched;
                try {
                    matched = Pattern.matches(item.match, name);
                } catch (Throwable t) {
                    matched = false;
                }
                if (!matched) continue;
                String value = headers.remove(name);
                if (!TextUtils.isEmpty(item.replace) && !item.replace.equals(name)) {
                    headers.put(item.replace, value);
                }
                break;
            }
        }
    }

    private static boolean hasHeader(Map<String, String> headers, String name) {
        for (String key : headers.keySet()) {
            if (key != null && key.equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
