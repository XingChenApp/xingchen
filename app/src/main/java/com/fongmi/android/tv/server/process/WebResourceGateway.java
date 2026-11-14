package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.web.CookieBridge;
import com.fongmi.android.tv.web.HeaderPolicy;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.SourceGate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

public class WebResourceGateway implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/webResource");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        if (session.getMethod() == NanoHTTPD.Method.OPTIONS) return cors(NanoHTTPD.newFixedLengthResponse(Response.Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, ""), session);
        // 源归属打点（照 WebCall 同形）：铸址侧（HomeWebBridge.resourceUrl）把当前
        // 承载站点的 (kind,id) 随址带入，本机服务线程本身无归属——不打点则
        // SourceGate 对网关取数只能靠 15 秒回退窗口猜归属、窗口外按无归属直连，
        // 与同源 fm.req（有打点）的定向裁决两样。无 kind 的旧址不打点、行为不变。
        String kind = session.getParms().get("kind");
        String id = session.getParms().get("id");
        boolean marked = kind != null && !kind.isEmpty();
        SourceGate.Mark prev = marked ? SourceGate.push(kind, id == null ? "" : id) : null;
        try {
            // 日志必须在 try 内：push 与 try 之间不许有语句，日志一抛 pop 就永不
            // 执行、mark 残留服务线程且会被下一请求的 push 当 prev 存下不自愈
            // （WebCall/SiteApi.marked 的 push 紧贴 try 同此口径）。
            if (marked) SpiderDebug.log("web-resource", "gate mark kind=%s id=%s", kind, id == null ? "" : id);
            return serve(session, url, files);
        } finally {
            if (marked) SourceGate.pop(prev);
        }
    }

    private Response serve(IHTTPSession session, String url, Map<String, String> files) {
        okhttp3.Response response = null;
        try {
            Map<String, String> params = session.getParms();
            String target = params.get("url");
            if (!isAllowed(target)) return cors(Nano.error(Response.Status.BAD_REQUEST, "Unsupported url"), session);
            Map<String, String> headers = HeaderPolicy.withDefaultUa(HeaderPolicy.parse(params.get("headers")));
            // 列表不转发 Range：列表要整份拿回来改写（见 playlist 支），带 Range
            // 只会取回一段残片、改不动；分段与其它资源的 Range 照旧透传。
            if (!M3u8.isPlaylistUrl(target)) copyRange(session, headers);
            Request.Builder builder = new Request.Builder().url(target).headers(HeaderPolicy.of(headers));
            CookieBridge.apply(builder.build().url(), builder, "include".equals(params.get("credentials")), HeaderPolicy.hasCookie(headers));
            builder.method(getMethod(session), getBody(session, builder.build().headers(), files));
            Request request = builder.build();
            // 取证（鉴黄师子列表 403 案）：票到底有没有真挂上上游腿，导出日志里
            // 网址全被脱敏、无从判读——发上游前把实发头形态记一行：只记 Cookie
            // 有无与长度、不记值，UA/Referer 只记有无，credentials 记实收形态。
            String sentCookie = request.header("Cookie");
            SpiderDebug.log("web-resource", "upstream send host %s cookie %s length %d ua %s referer %s credentials %s", request.url().host(), sentCookie == null || sentCookie.isEmpty() ? "absent" : "present", sentCookie == null ? 0 : sentCookie.length(), request.header("User-Agent") == null ? "absent" : "present", request.header("Referer") == null ? "absent" : "present", "include".equals(params.get("credentials")) ? "include" : "none");
            long start = System.currentTimeMillis();
            SpiderDebug.log("web-resource", "%s %s", request.method(), request.url());
            response = OkHttp.client().newCall(request).execute();
            SpiderDebug.log("web-resource", "%s %s -> %s in %sms", request.method(), request.url(), response.code(), System.currentTimeMillis() - start);
            CookieBridge.set(target, response.headers());
            return cors(toResponse(session, response), session);
        } catch (Throwable e) {
            if (response != null) response.close();
            SpiderDebug.log("web-resource", e);
            return cors(Nano.error(e.getMessage()), session);
        }
    }

    private boolean isAllowed(String url) {
        return !TextUtils.isEmpty(url) && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private void copyRange(IHTTPSession session, Map<String, String> headers) {
        String range = session.getHeaders().get("range");
        if (!TextUtils.isEmpty(range)) headers.put("Range", range);
    }

    private String getMethod(IHTTPSession session) {
        String method = session.getMethod().name();
        return "OPTIONS".equals(method) ? "GET" : method;
    }

    private RequestBody getBody(IHTTPSession session, Headers headers, Map<String, String> files) {
        String method = getMethod(session);
        if ("GET".equals(method) || "HEAD".equals(method)) return null;
        String body = session.getParms().get("body");
        if (TextUtils.isEmpty(body)) body = files.get("postData");
        String contentType = headers.get("Content-Type");
        MediaType type = MediaType.parse(contentType == null ? "text/plain; charset=utf-8" : contentType);
        return RequestBody.create(body == null ? "" : body, type);
    }

    private Response toResponse(IHTTPSession session, okhttp3.Response response) throws IOException {
        if (response.body() != null && M3u8.isPlaylist(response.request().url().toString(), response.body().contentType())) return playlist(session, response);
        InputStream stream = response.body().byteStream();
        String type = getContentType(response);
        Response.Status status = Response.Status.lookup(response.code());
        Response result = NanoHTTPD.newChunkedResponse(status == null ? Response.Status.OK : status, type, stream);
        copyHeaders(response, result);
        return result;
    }

    /**
     * 列表支：列表只透传不改写时，分段行是相对名，播放器只能以本机
     * /webResource 址为基址解析——相对名全落到本机根路径上、拿回假成功空体，
     * 整表分段全废（2026-11-14 鉴黄师案：2,060 个空分段被判 malformed）。
     * 这里把列表整份读回，用 M3u8 的同一套改写把每个地址先按上游列表址
     * 解析成绝对址、再包回 /webResource（头、凭证、源归属随原请求参数原样
     * 带下），分段与子列表回网关时与列表同头、同归属、同路取真数据。
     * 正文不是列表的（上游错误页等）按原状态与原正文回，不吞上游状态。
     */
    private Response playlist(IHTTPSession session, okhttp3.Response response) throws IOException {
        Map<String, String> params = session.getParms();
        byte[] raw = response.body().bytes();
        Response.Status status = Response.Status.lookup(response.code());
        if (status == null) status = Response.Status.OK;
        String text = new String(raw, StandardCharsets.UTF_8);
        if (M3u8.looksLikePlaylist(text)) {
            HttpUrl base = response.request().url();
            String rewritten = M3u8.rewrite(base, text, target -> wrapSegment(params, target));
            byte[] bytes = rewritten.getBytes(StandardCharsets.UTF_8);
            SpiderDebug.log("web-resource", "playlist rewritten base=%s bytes=%s->%s", base, raw.length, bytes.length);
            logSubListPairs(text, rewritten);
            Response result = NanoHTTPD.newFixedLengthResponse(status, "application/vnd.apple.mpegurl; charset=utf-8", new ByteArrayInputStream(bytes), bytes.length);
            copyHeaders(response, result);
            return result;
        }
        Response result = NanoHTTPD.newFixedLengthResponse(status, getContentType(response), new ByteArrayInputStream(raw), raw.length);
        copyHeaders(response, result);
        return result;
    }

    /**
     * 取证（鉴黄师子列表 403 案）：改写有没有打坏子列表行的签名，靠原始行与
     * 改写后行成对判读——只记子列表行（.m3u8/.m3u），每行只落 host、path 与
     * query 的键名及各值长度、不落值（全值会被日志脱敏整段抹掉、键与长度是
     * 验签形态的判读面）。改写保持行序行数，逐行对位；上限 8 行防大表刷屏。
     */
    private void logSubListPairs(String text, String rewritten) {
        String[] rawLines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        String[] newLines = rewritten.split("\n", -1);
        int logged = 0;
        for (int i = 0; i < rawLines.length && i < newLines.length && logged < 8; i++) {
            String raw = rawLines[i].trim();
            if (raw.isEmpty() || raw.startsWith("#") || !M3u8.isPlaylistUrl(raw)) continue;
            SpiderDebug.log("web-resource", "playlist sub raw %s wrapped %s", urlShape(raw), urlShape(newLines[i].trim()));
            logged++;
        }
    }

    /** 地址形态：host＋path＋query 键名与值长（不含值）；网关址先剥壳取内包真址再拆。 */
    private String urlShape(String url) {
        String value = url;
        int marker = value.indexOf("/webResource?url=");
        if (marker >= 0) {
            String rest = value.substring(marker + "/webResource?url=".length());
            int amp = rest.indexOf('&');
            if (amp >= 0) rest = rest.substring(0, amp);
            try {
                value = URLDecoder.decode(rest, StandardCharsets.UTF_8);
            } catch (Throwable ignored) {
                value = rest;
            }
        }
        int frag = value.indexOf('#');
        if (frag >= 0) value = value.substring(0, frag);
        String head = value;
        String query = "";
        int q = value.indexOf('?');
        if (q >= 0) {
            head = value.substring(0, q);
            query = value.substring(q + 1);
        }
        String host = "";
        String path = head;
        int scheme = head.indexOf("://");
        if (scheme >= 0) {
            String rest = head.substring(scheme + 3);
            int slash = rest.indexOf('/');
            host = slash >= 0 ? rest.substring(0, slash) : rest;
            path = slash >= 0 ? rest.substring(slash) : "/";
        }
        StringBuilder shape = new StringBuilder("host ").append(host).append(" path ").append(path).append(" qkeys ");
        if (query.isEmpty()) {
            shape.append("none");
        } else {
            String[] pairs = query.split("&", -1);
            for (int i = 0; i < pairs.length; i++) {
                if (i > 0) shape.append(',');
                int eq = pairs[i].indexOf('=');
                if (eq >= 0) shape.append(pairs[i].substring(0, eq)).append('(').append(pairs[i].length() - eq - 1).append(')');
                else shape.append(pairs[i]).append("(-)");
            }
        }
        return shape.toString();
    }

    /** 分段/子列表/密钥地址包回网关：与列表请求同一套参数（头、凭证、源归属），取数路一字不变。 */
    private String wrapSegment(Map<String, String> params, String target) {
        StringBuilder builder = new StringBuilder(Server.get().getAddress("/webResource?url=")).append(encode(target));
        String headers = params.get("headers");
        if (headers != null) builder.append("&headers=").append(encode(headers));
        if ("include".equals(params.get("credentials"))) builder.append("&credentials=include");
        String kind = params.get("kind");
        if (kind != null && !kind.isEmpty()) {
            builder.append("&kind=").append(encode(kind));
            String id = params.get("id");
            builder.append("&id=").append(encode(id == null ? "" : id));
        }
        return builder.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void copyHeaders(okhttp3.Response response, Response result) {
        for (String name : response.headers().names()) if (copyHeader(name)) for (String value : response.headers(name)) result.addHeader(name, value);
    }

    private String getContentType(okhttp3.Response response) {
        MediaType type = response.body().contentType();
        return type == null ? NanoHTTPD.MIME_PLAINTEXT : type.toString();
    }

    private boolean copyHeader(String name) {
        return !"Connection".equalsIgnoreCase(name) && !"Transfer-Encoding".equalsIgnoreCase(name) && !"Keep-Alive".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name);
    }

    private Response cors(Response response, IHTTPSession session) {
        String origin = session.getHeaders().get("origin");
        response.addHeader("Access-Control-Allow-Origin", TextUtils.isEmpty(origin) ? "*" : origin);
        response.addHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,HEAD,OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "*");
        response.addHeader("Access-Control-Expose-Headers", "*");
        response.addHeader("Access-Control-Max-Age", "86400");
        return response;
    }
}
