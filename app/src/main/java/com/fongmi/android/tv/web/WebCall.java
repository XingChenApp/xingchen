package com.fongmi.android.tv.web;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.SourceGate;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebCall {

    // 不再自建裸客户端：旧裸客户端绕开了全 App 标准客户端上的按源定向／全局
    // 代理包装与信托全过 TLS，fm.req 成了取数面里唯一不认源归属、不认代理
    // 策略的一路（fm.res 网关早已走 OkHttp.client()）。基底统一走标准客户端，
    // 本类只在其上派生超时与重定向口径（见 client(int)）。

    public static String request(JsonObject payload) {
        return request(payload, null);
    }

    public static String request(JsonObject payload, HomeWebController controller) {
        Response response = null;
        Request request = null;
        String url = Json.safeString(payload, "url");
        String method = Json.safeString(payload, "method");
        long start = 0;
        // 源归属打点（照 SiteApi.marked 同形）：fm.req 跑在桥的任务线程上，
        // 不打点则按源定向对它无归属可判；归属取控制器当前承载的站点。
        Site markSite = controller == null ? null : controller.currentSite();
        String markKind = markSite == null ? null : markKindOf(markSite);
        SourceGate.Mark prevMark = markKind == null ? null : SourceGate.push(markKind, markSite.getKey());
        try {
            String body = Json.safeString(payload, "body");
            String responseType = Json.safeString(payload, "responseType");
            boolean include = "include".equals(Json.safeString(payload, "credentials"));
            int timeout = getTimeout(payload);
            Map<String, String> headers = HeaderPolicy.withDefaultUa(HeaderPolicy.parse(payload.get("headers")));
            setDefaultEncoding(headers);
            Request.Builder builder = new Request.Builder().url(url).headers(HeaderPolicy.of(headers));
            CookieBridge.apply(builder.build().url(), builder, include, HeaderPolicy.hasCookie(headers));
            builder.method(getMethod(method), getBody(getMethod(method), body, builder.build().headers()));
            request = builder.build();
            start = System.currentTimeMillis();
            dispatch(controller, "NATIVE_START", request.method(), request.url().toString(), 0, 0, requestInfo(request));
            SpiderDebug.log("webhome-net", "%s %s include=%s timeout=%ss headers=%s", request.method(), request.url(), include, timeout, request.headers().names());
            response = client(timeout).newCall(request).execute();
            SpiderDebug.log("webhome-net", "%s %s -> %s in %sms", request.method(), request.url(), response.code(), System.currentTimeMillis() - start);
            CookieBridge.set(url, response.headers());
            dispatch(controller, "NATIVE_DONE", request.method(), response.request().url().toString(), response.code(), System.currentTimeMillis() - start, responseInfo(response));
            return toJson(response, responseType);
        } catch (Throwable e) {
            SpiderDebug.log("webhome-net", e);
            dispatch(controller, "NATIVE_ERROR", request == null ? getMethod(method) : request.method(), request == null ? url : request.url().toString(), 0, start <= 0 ? 0 : System.currentTimeMillis() - start, e.getMessage());
            return error(e);
        } finally {
            if (response != null) response.close();
            if (markKind != null) SourceGate.pop(prevMark);
        }
    }

    /** 站点 → 按源定向的种类（SiteApi.proxyKindForKey，恒有值、兜底 vod；无站点才不打点）。 */
    private static String markKindOf(Site site) {
        try {
            String kind = SiteApi.proxyKindForKey(site.getKey());
            return kind == null || kind.isEmpty() ? null : kind;
        } catch (Throwable e) {
            return null;
        }
    }

    private static void dispatch(HomeWebController controller, String type, String method, String url, int status, long durationMs, String detail) {
        if (controller != null) controller.dispatchDebugNetwork(type, method, url, status, durationMs, detail);
    }

    private static String requestInfo(Request request) {
        StringBuilder builder = new StringBuilder();
        builder.append("Request Headers\n").append(request.headers());
        RequestBody body = request.body();
        if (body != null) builder.append("Body\n").append("contentType=").append(body.contentType()).append('\n').append("contentLength=").append(contentLength(body)).append('\n').append(bodyPreview(body));
        return builder.toString().trim();
    }

    private static String responseInfo(Response response) {
        return ("message=" + response.message() + "\nResponse Headers\n" + response.headers()).trim();
    }

    private static long contentLength(RequestBody body) {
        try {
            return body.contentLength();
        } catch (Throwable e) {
            return -1;
        }
    }

    private static String bodyPreview(RequestBody body) {
        try {
            okio.Buffer buffer = new okio.Buffer();
            body.writeTo(buffer);
            String text = buffer.readString(StandardCharsets.UTF_8);
            return "payload:\n" + (text.length() > 2000 ? text.substring(0, 2000) + "\n...truncated" : text);
        } catch (Throwable e) {
            return "payloadPreview=" + e.getMessage();
        }
    }

    private static int getTimeout(JsonObject payload) {
        try {
            return Math.max(payload.get("timeout").getAsInt(), 1);
        } catch (Exception e) {
            return 30;
        }
    }

    private static OkHttpClient client(int timeout) {
        long millis = TimeUnit.SECONDS.toMillis(timeout);
        return OkHttp.client().newBuilder().followRedirects(true).followSslRedirects(true).connectTimeout(millis, TimeUnit.MILLISECONDS).readTimeout(millis, TimeUnit.MILLISECONDS).writeTimeout(millis, TimeUnit.MILLISECONDS).build();
    }

    private static String getMethod(String method) {
        return TextUtils.isEmpty(method) ? "GET" : method.toUpperCase();
    }

    private static void setDefaultEncoding(Map<String, String> headers) {
        boolean hasEncoding = headers.keySet().stream().anyMatch("Accept-Encoding"::equalsIgnoreCase);
        if (!hasEncoding) headers.put("Accept-Encoding", "gzip");
    }

    private static RequestBody getBody(String method, String body, Headers headers) {
        if ("GET".equals(method) || "HEAD".equals(method)) return null;
        String type = headers.get("Content-Type");
        MediaType mediaType = MediaType.parse(TextUtils.isEmpty(type) ? "text/plain; charset=utf-8" : type);
        return RequestBody.create(body == null ? "" : body, mediaType);
    }

    private static String toJson(Response response, String responseType) throws Exception {
        byte[] raw = response.body() == null ? new byte[0] : readAll(response.body().byteStream());
        byte[] bytes = decode(response.header("Content-Encoding"), raw);
        JsonObject object = new JsonObject();
        object.addProperty("ok", response.isSuccessful());
        object.addProperty("status", response.code());
        object.addProperty("url", response.request().url().toString());
        object.add("headers", headers(response.headers()));
        object.add("cookies", App.gson().toJsonTree(response.headers().values("Set-Cookie")));
        String body = new String(bytes, StandardCharsets.UTF_8);
        if ("base64".equals(responseType)) object.addProperty("body", Util.base64(bytes));
        else if ("json".equals(responseType)) object.add("body", Json.parse(body));
        else object.addProperty("body", body);
        return object.toString();
    }

    private static byte[] decode(String encoding, byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) return bytes;
        if (hasEncoding(encoding, "br")) throw new IOException("Unsupported Content-Encoding: br");
        if (hasEncoding(encoding, "gzip") || isGzip(bytes)) return gunzip(bytes);
        // deflate 不在此解：换底后走的标准客户端链里，ResponseInterceptor
        // 已在读流时把 deflate 解压（原响应头保留），到这里的字节已是
        // 明文——再 inflate 一遍必抛、整发 fm.req 作废。gzip 因本类手动
        // 带 Accept-Encoding、桥接层不做透明解压，仍归这里解。
        return bytes;
    }

    private static boolean hasEncoding(String encoding, String value) {
        if (TextUtils.isEmpty(encoding)) return false;
        for (String item : encoding.split(",")) if (value.equalsIgnoreCase(item.trim())) return true;
        return false;
    }

    private static boolean isGzip(byte[] bytes) {
        return bytes.length > 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b;
    }

    private static byte[] gunzip(byte[] bytes) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return readAll(gzip);
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream stream = input) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static JsonObject headers(Headers headers) {
        JsonObject object = new JsonObject();
        for (String name : headers.names()) {
            if (headers.values(name).size() == 1) object.addProperty(name, headers.get(name));
            else object.add(name, App.gson().toJsonTree(headers.values(name)));
        }
        return object;
    }

    private static String error(Throwable e) {
        JsonObject object = new JsonObject();
        object.addProperty("ok", false);
        object.addProperty("status", 500);
        object.addProperty("body", "");
        object.addProperty("error", e.getMessage());
        object.add("headers", new JsonObject());
        return object.toString();
    }

    public static JsonObject object(String json) {
        JsonElement element = Json.parse(json);
        return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }
}
