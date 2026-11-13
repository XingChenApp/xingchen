package com.fongmi.android.tv.legado;

import com.fongmi.android.tv.api.hk.HkHttp;
import com.fongmi.android.tv.clash.SourceProxyPolicy;
import com.github.catvod.net.SourceGate;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 书源网络层宿主默认实现：复用 HkHttp 的 OkHttp 客户端（含节点选路）。
 * 超时：连接 12s / 读写 20s；任何失败只抛 IOException，不拖垮宿主。
 */
public class LegadoHttpAndroid implements LegadoHttp {

    /** 响应体上限 16MB（低-17）。 */
    private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
    /** 非 2xx 只读前缀上限：不整包吞错误体。 */
    private static final int ERROR_PREFIX_BYTES = 256 * 1024;
    /** 书源请求 UA（移动 Chrome 口径，生态源按此适配）。 */
    static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    private static volatile LegadoHttp instance;

    public static void setInstance(LegadoHttp impl) {
        instance = impl;
    }

    public static LegadoHttp get() {
        LegadoHttp impl = instance;
        return impl != null ? impl : Holder.DEF;
    }

    private static class Holder {
        static final LegadoHttpAndroid DEF = new LegadoHttpAndroid();
    }

    private OkHttpClient client() {
        return HkHttp.client().newBuilder()
                .connectTimeout(LegadoHttp.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(LegadoHttp.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(LegadoHttp.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * 当前线程正在抓取的书源 key（bookSourceUrl）：LegadoPipeline 在抓取前后
     * 设置/恢复，打点精确到具体书源（设置页「书源」栏逐个开关）。
     * 用 push/pop 语义，外层打点（如章节缓存下载的外层归属）不会被吞。
     */
    private static final ThreadLocal<String> SOURCE_KEY = new ThreadLocal<>();

    public static String swapSourceKey(String key) {
        String prev = SOURCE_KEY.get();
        if (key == null || key.isEmpty()) SOURCE_KEY.remove();
        else SOURCE_KEY.set(key);
        return prev;
    }

    public static void restoreSourceKey(String prev) {
        if (prev == null || prev.isEmpty()) SOURCE_KEY.remove();
        else SOURCE_KEY.set(prev);
    }

    /** 一次原始请求的结果：状态码/终点 URL/响应体（非 2xx 只留前缀）/回带 Cookie。 */
    private static class Raw {
        int code;
        String finalUrl = "";
        String contentType = "";
        byte[] body = new byte[0];
        final Map<String, String> cookies = new java.util.HashMap<>();
    }

    @Override
    public LegadoHttp.Result fetch(String url, String method, Map<String, String> headers, byte[] body) throws IOException {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IOException("不支持的协议: " + url);
        }
        // 节点打点：书源走 KIND_BOOK 口径（id=具体书源 key），与「按源定向」书源栏一致。
        // 无 key（如导入时校验地址）时 id 为空串 → 只有「全部走节点」能带走它。
        String key = SOURCE_KEY.get();
        SourceGate.Mark prev = SourceGate.push(SourceProxyPolicy.KIND_BOOK, key == null ? "" : key);
        try {
            // 人机验证整套已删（他 2026-10-28 令「没啥用」）：撞墙的站就按
            // 普通失败走（HTTP 错误/解析不出），不再弹验证页、不存通行证
            Raw raw = doFetch(url, method, headers, body);
            if (raw.code < 200 || raw.code >= 300) throw new IOException("HTTP " + raw.code + " " + url);
            LegadoHttp.Result r = new LegadoHttp.Result();
            r.body = raw.body;
            r.contentType = raw.contentType;
            r.cookies.putAll(raw.cookies);
            return r;
        } finally {
            SourceGate.pop(prev);
        }
    }

    private Raw doFetch(String url, String method, Map<String, String> headers, byte[] body) throws IOException {
        Request.Builder rb = new Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9");
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                try {
                    if (h.getKey() != null && h.getValue() != null) rb.header(h.getKey(), h.getValue());
                } catch (Throwable ignored) {
                }
            }
        }
        String m = method == null ? "GET" : method.toUpperCase();
        if ("POST".equals(m)) {
            byte[] b = body == null ? new byte[0] : body;
            rb.post(RequestBody.create(b, MediaType.parse("application/x-www-form-urlencoded; charset=utf-8")));
        } else {
            rb.get();
        }
        Raw raw = new Raw();
        try (Response resp = client().newCall(rb.build()).execute()) {
            raw.code = resp.code();
            raw.finalUrl = resp.request().url().toString();
            String ct = resp.header("Content-Type");
            raw.contentType = ct == null ? "" : ct;
            List<String> setCookies = resp.headers("Set-Cookie");
            if (setCookies != null) {
                for (String c : setCookies) {
                    int eq = c.indexOf('=');
                    int sc = c.indexOf(';');
                    if (eq > 0) raw.cookies.put(c.substring(0, eq).trim(),
                            c.substring(eq + 1, sc < 0 ? c.length() : sc).trim());
                }
            }
            ResponseBody rb2 = resp.body();
            if (rb2 == null) {
                if (resp.isSuccessful()) throw new IOException("空响应 " + url);
                return raw;
            }
            if (resp.isSuccessful()) {
                // 流式读＋16MB 上限（低-17）：rb2.bytes() 整包进内存，对方回个超大包
                // （或被劫持回流式垃圾）直接顶爆内存；书源正文/目录远小于此上限。
                if (rb2.contentLength() > MAX_BODY_BYTES) {
                    throw new IOException("响应过大: " + rb2.contentLength());
                }
                java.io.InputStream in = rb2.byteStream();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                    if (baos.size() > MAX_BODY_BYTES) throw new IOException("响应过大（超 16MB）");
                }
                raw.body = baos.toByteArray();
            } else {
                // 错误响应只读前缀：不整包吞错误体
                java.io.InputStream in = rb2.byteStream();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while (baos.size() < ERROR_PREFIX_BYTES && (n = in.read(buf)) != -1) {
                    baos.write(buf, 0, Math.min(n, ERROR_PREFIX_BYTES - baos.size()));
                }
                raw.body = baos.toByteArray();
            }
        }
        return raw;
    }
}
