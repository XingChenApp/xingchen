package com.fongmi.quickjs.method;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.CookieStore;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * WvSpider 宿主桥：给新格式宿主型 JS 源提供的 Java 侧能力（同步执行）。
 *
 * 只实现真实样本实际调用的面（见 js/lib/wv.js 的垫片）：
 * __wvHttp —— fetch / Java.req 共用的 HTTP 出口，走壳子 OkHttp（含代理与 DNS 口径）；
 * __wvCacheGet/__wvCacheSet —— host.cache 与 Java 缓存共用，落 Prefers，按源隔离命名空间；
 * __wvCookies —— host.cookies.get，先读 WebView CookieManager（网页登录态在那），
 * 回退壳子 CookieStore；
 * __wvDomParse/__wvDomQuery/__wvDomQueryIn —— returnType:dom 的 DOM 支撑，jsoup 实现。
 *
 * 每个方法都不许把异常抛进 JS：失败折叠成约定形态并 SpiderDebug 留痕，
 * 与 Global 的 JSMethod 失败口径一致。
 */
public class WvHost {

    private static final int MAX_ELEMENTS = 500;
    private static final int MAX_OUTER_HTML = 200_000;

    private final Map<String, Document> docs = new HashMap<>();
    private final AtomicInteger docSeq = new AtomicInteger();
    private final String namespace;
    private final Gson gson = new Gson();

    public WvHost(String namespace) {
        this.namespace = namespace == null || namespace.isEmpty() ? "default" : namespace;
    }

    public void bind(QuickJSContext ctx) {
        // 全局包装只取一份走完注册：它是上下文内部缓存的同一份，只取来
        // 注册，绝不 release（放掉即打死全上下文共用包装，WvSpider 后续
        // 任何经包装的调用都会踩已死对象；批五 5eab204f4 误加、本注随
        // 撤销一并改回，定根见 home-searchbox-jitter-20261029.md 闪退节）。
        JSObject globalObj = ctx.getGlobalObject();
        globalObj.setProperty("__wvHttp", args -> http(arg(args, 0)));
        globalObj.setProperty("__wvCacheGet", args -> cacheGet(arg(args, 0)));
        globalObj.setProperty("__wvCacheSet", args -> {
            cacheSet(arg(args, 0), arg(args, 1));
            return null;
        });
        globalObj.setProperty("__wvCookies", args -> cookies(arg(args, 0)));
        globalObj.setProperty("__wvDomParse", args -> domParse(arg(args, 0), arg(args, 1)));
        globalObj.setProperty("__wvDomQuery", args -> domQuery(arg(args, 0), arg(args, 1)));
        globalObj.setProperty("__wvDomQueryIn", args -> domQueryIn(arg(args, 0), arg(args, 1), arg(args, 2)));
    }

    public void destroy() {
        docs.clear();
    }

    private static String arg(Object[] args, int index) {
        if (args == null || args.length <= index || args[index] == null) return "";
        return String.valueOf(args[index]);
    }

    /* ---------- HTTP ---------- */

    private String http(String json) {
        JsonObject out = new JsonObject();
        JsonArray pairs = new JsonArray();
        out.addProperty("status", 0);
        out.addProperty("url", "");
        out.add("headers", pairs);
        out.addProperty("body", "");
        out.add("error", null);
        String url = "";
        try {
            JsonObject req = JsonParser.parseString(json).getAsJsonObject();
            url = req.has("url") ? req.get("url").getAsString() : "";
            out.addProperty("url", url);
            String method = req.has("method") ? req.get("method").getAsString() : "GET";
            String body = req.has("body") && !req.get("body").isJsonNull() ? req.get("body").getAsString() : null;
            boolean redirect = !req.has("redirect") || req.get("redirect").getAsBoolean();
            long timeout = req.has("timeoutMs") ? req.get("timeoutMs").getAsLong() : 60_000L;
            Map<String, String> headers = new HashMap<>();
            if (req.has("headers") && req.get("headers").isJsonObject()) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : req.getAsJsonObject("headers").entrySet()) {
                    if (e.getValue() != null && e.getValue().isJsonPrimitive()) headers.put(e.getKey(), e.getValue().getAsString());
                }
            }
            Request.Builder builder = new Request.Builder().url(url);
            for (Map.Entry<String, String> e : headers.entrySet()) builder.header(e.getKey(), e.getValue());
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || body != null) {
                String contentType = headerOf(headers, "Content-Type");
                MediaType type = MediaType.parse(contentType != null ? contentType : "text/plain; charset=utf-8");
                builder.method(method.toUpperCase(), RequestBody.create(body == null ? "" : body, type));
            } else {
                builder.method(method.toUpperCase(), null);
            }
            OkHttpClient client = OkHttp.client(redirect, timeout);
            try (Response res = client.newCall(builder.build()).execute()) {
                out.addProperty("status", res.code());
                out.addProperty("url", res.request().url().toString());
                for (int i = 0; i < res.headers().size(); i++) {
                    JsonArray pair = new JsonArray();
                    pair.add(res.headers().name(i));
                    pair.add(res.headers().value(i));
                    pairs.add(pair);
                }
                out.addProperty("body", res.body() == null ? "" : res.body().string());
            }
        } catch (Throwable e) {
            SpiderDebug.log("wvhost", "http failed url=%s error=%s", url, e);
            out.addProperty("error", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
        return gson.toJson(out);
    }

    private static String headerOf(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        return null;
    }

    /* ---------- 缓存（host.cache 与 Java.setCache/getCache 共用一层） ---------- */

    private String cacheKey(String key) {
        return "wv_" + namespace + "_" + key;
    }

    private String cacheGet(String key) {
        try {
            String value = Prefers.getString(cacheKey(key));
            return value == null ? "" : value;
        } catch (Throwable e) {
            SpiderDebug.log("wvhost", "cache get failed key=%s error=%s", key, e);
            return "";
        }
    }

    private void cacheSet(String key, String value) {
        try {
            Prefers.put(cacheKey(key), value);
        } catch (Throwable e) {
            SpiderDebug.log("wvhost", "cache set failed key=%s error=%s", key, e);
        }
    }

    /* ---------- Cookie ---------- */

    private String cookies(String url) {
        try {
            String cookie = android.webkit.CookieManager.getInstance().getCookie(url);
            if (cookie != null && !cookie.isEmpty()) return cookie;
        } catch (Throwable ignored) {
            // 桌面/无 WebView 环境走下面的壳子存储回退。
        }
        try {
            HttpUrl httpUrl = HttpUrl.parse(url);
            if (httpUrl != null) {
                String cookie = CookieStore.get(httpUrl.host());
                if (cookie != null) return cookie;
            }
        } catch (Throwable e) {
            SpiderDebug.log("wvhost", "cookies failed url=%s error=%s", url, e);
        }
        return "";
    }

    /* ---------- DOM（jsoup） ---------- */

    private String domParse(String html, String base) {
        try {
            String id = "d" + docSeq.incrementAndGet();
            docs.put(id, Jsoup.parse(html == null ? "" : html, base == null ? "" : base));
            return id;
        } catch (Throwable e) {
            SpiderDebug.log("wvhost", "dom parse failed error=%s", e);
            return "";
        }
    }

    private String domQuery(String docId, String selector) {
        try {
            Document doc = docs.get(docId);
            if (doc == null) return "[]";
            return gson.toJson(describe(doc.select(selector)));
        } catch (Throwable e) {
            SpiderDebug.log("wvhost", "dom query failed selector=%s error=%s", selector, e);
            return "[]";
        }
    }

    private String domQueryIn(String html, String selector, String base) {
        try {
            Document doc = Jsoup.parseBodyFragment(html == null ? "" : html, base == null ? "" : base);
            return gson.toJson(describe(doc.select(selector)));
        } catch (Throwable e) {
            SpiderDebug.log("wvhost", "dom queryIn failed selector=%s error=%s", selector, e);
            return "[]";
        }
    }

    private JsonArray describe(Elements elements) {
        JsonArray array = new JsonArray();
        int count = 0;
        for (Element element : elements) {
            if (++count > MAX_ELEMENTS) break;
            JsonObject item = new JsonObject();
            item.addProperty("tag", element.tagName());
            JsonObject attrs = new JsonObject();
            for (Attribute attr : element.attributes()) attrs.addProperty(attr.getKey(), attr.getValue());
            item.add("attrs", attrs);
            JsonObject abs = new JsonObject();
            String href = element.absUrl("href");
            String src = element.absUrl("src");
            if (!href.isEmpty()) abs.addProperty("href", href);
            if (!src.isEmpty()) abs.addProperty("src", src);
            item.add("abs", abs);
            item.addProperty("text", element.text());
            String html = element.outerHtml();
            item.addProperty("html", html.length() > MAX_OUTER_HTML ? html.substring(0, MAX_OUTER_HTML) : html);
            array.add(item);
        }
        return array;
    }
}
