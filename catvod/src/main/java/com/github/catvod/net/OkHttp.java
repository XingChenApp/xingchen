package com.github.catvod.net;

import android.annotation.SuppressLint;

import androidx.collection.ArrayMap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.catvod.net.interceptor.AuthInterceptor;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.interceptor.RequestInterceptor;
import com.github.catvod.net.interceptor.ResponseInterceptor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Dns;
import okhttp3.EventListener;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Protocol;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

public class OkHttp {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    private ResponseInterceptor responseInterceptor;
    private RequestInterceptor requestInterceptor;
    private AuthInterceptor authInterceptor;
    private OkAuthenticator authenticator;
    private OkProxySelector selector;
    private OkHttpClient client;
    private OkHttpClient player;
    private OkDns dns;

    public static OkHttp get() {
        return Loader.INSTANCE;
    }

    public static OkDns dns() {
        if (get().dns != null) return get().dns;
        return get().dns = new OkDns();
    }

    public static ResponseInterceptor responseInterceptor() {
        if (get().responseInterceptor != null) return get().responseInterceptor;
        return get().responseInterceptor = new ResponseInterceptor();
    }

    public static RequestInterceptor requestInterceptor() {
        if (get().requestInterceptor != null) return get().requestInterceptor;
        return get().requestInterceptor = new RequestInterceptor();
    }

    public static AuthInterceptor authInterceptor() {
        if (get().authInterceptor != null) return get().authInterceptor;
        return get().authInterceptor = new AuthInterceptor();
    }

    public static OkAuthenticator authenticator() {
        if (get().authenticator != null) return get().authenticator;
        return get().authenticator = new OkAuthenticator(selector());
    }

    public static OkProxySelector selector() {
        if (get().selector != null) return get().selector;
        return get().selector = new OkProxySelector();
    }

    public static void closeIdleConnections() {
        new Thread(OkHttp::evictIdleConnections, "okhttp-evict-idle").start();
    }

    /**
     * Drop the cached clients so the next use rebuilds them (picks up DNS
     * mode changes, e.g. System <-> DoH provider). In-flight calls keep
     * working on their own client references.
     */
    public static synchronized void resetClients() {
        try {
            if (get().client != null) get().client.connectionPool().evictAll();
            if (get().player != null) get().player.connectionPool().evictAll();
        } catch (Throwable ignored) {
        }
        get().client = null;
        get().player = null;
    }

    private static synchronized void evictIdleConnections() {
        try {
            if (get().client != null) get().client.connectionPool().evictAll();
            if (get().player != null) get().player.connectionPool().evictAll();
            SpiderDebug.log("proxy", "connection pool evicted");
        } catch (Throwable e) {
            SpiderDebug.log("proxy", "connection pool evict failed error=%s", e.getMessage());
        }
    }

    public static synchronized OkHttpClient client() {
        if (get().client != null) return get().client;
        return get().client = getBuilder().build();
    }

    public static synchronized OkHttpClient player() {
        if (get().player != null) return get().player;
        return get().player = getBuilder()
                .addInterceptor(new KuaishouMediaFallbackInterceptor())
                .eventListenerFactory(call -> SpiderDebug.isEnabled() ? new DebugEventListener() : EventListener.NONE)
                .build();
    }

    public static OkHttpClient client(long timeout) {
        return client().newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS).build();
    }

    public static OkHttpClient noRedirect() {
        return noRedirect(TIMEOUT);
    }

    public static OkHttpClient noRedirect(long timeout) {
        return client().newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS).followRedirects(false).followSslRedirects(false).build();
    }

    public static OkHttpClient client(boolean redirect, long timeout) {
        return redirect ? client(timeout) : noRedirect(timeout);
    }

    public static String string(String url) {
        if (!url.startsWith("http")) return "";
        return string(() -> newCall(url), url);
    }

    public static String string(String url, long timeout) {
        if (!url.startsWith("http")) return "";
        return string(() -> newCall(client(timeout), url), url);
    }

    public static String string(String url, Map<String, String> headers) {
        if (!url.startsWith("http")) return "";
        return string(() -> newCall(url, headers), url);
    }

    public static String string(String url, Map<String, String> headers, long timeout) {
        if (!url.startsWith("http")) return "";
        return string(() -> newCall(client(timeout), url, headers), url);
    }

    private static String string(Supplier<Call> supplier, String url) {
        Call call = null;
        try {
            call = supplier.get();
            try (Response res = call.execute()) {
                return res.body().string();
            }
        } catch (Exception e) {
            // 返回值口径不变（失败仍回空串，调用方众多），但失败不再与
            // 空响应同形无痕：真因进 SpiderDebug，下游按空内容误判时
            // 至少有据可查（旧写法只 printStackTrace、日志里查不到）。
            // newCall 求值也在 try 内：畸形 URL/header 抛的
            // IllegalArgumentException 同样按此口径回空串＋留真因，
            // 不直穿调用方（旧契约：失败一律回空串）。
            SpiderDebug.log("okhttp", "string fetch failed url=%s error=%s", call != null ? call.request().url() : url, e.getMessage());
            return "";
        }
    }

    public static Call newCall(String url) {
        return client().newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(String url, String tag) {
        return client().newCall(new Request.Builder().url(url).tag(tag).build());
    }

    public static Call newCall(OkHttpClient client, String url) {
        return client.newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(OkHttpClient client, String url, String tag) {
        return client.newCall(new Request.Builder().url(url).tag(tag).build());
    }

    public static Call newCall(String url, Map<String, String> headers) {
        return client().newCall(new Request.Builder().url(url).headers(Headers.of(headers)).build());
    }

    public static Call newCall(OkHttpClient client, String url, Map<String, String> headers) {
        return client.newCall(new Request.Builder().url(url).headers(Headers.of(headers)).build());
    }

    public static Call newCall(String url, Map<String, String> headers, ArrayMap<String, String> params) {
        return client().newCall(new Request.Builder().url(buildUrl(url, params)).headers(Headers.of(headers)).build());
    }

    public static Call newCall(String url, Map<String, String> headers, RequestBody body) {
        return client().newCall(new Request.Builder().url(url).headers(Headers.of(headers)).post(body).build());
    }

    public static Call newCall(String url, RequestBody body, String tag) {
        return client().newCall(new Request.Builder().url(url).post(body).tag(tag).build());
    }

    public static Call newCall(OkHttpClient client, String url, RequestBody body) {
        return client.newCall(new Request.Builder().url(url).post(body).build());
    }

    public static void cancel(String tag) {
        cancel(client(), tag);
    }

    public static void cancel(OkHttpClient client, String tag) {
        for (Call call : client.dispatcher().queuedCalls()) if (tag.equals(call.request().tag())) call.cancel();
        for (Call call : client.dispatcher().runningCalls()) if (tag.equals(call.request().tag())) call.cancel();
    }

    public static void cancelAll() {
        cancelAll(client());
    }

    public static void cancelAll(OkHttpClient client) {
        client.dispatcher().cancelAll();
    }

    public static FormBody toBody(ArrayMap<String, String> params) {
        FormBody.Builder body = new FormBody.Builder();
        for (Map.Entry<String, String> entry : params.entrySet()) body.add(entry.getKey(), entry.getValue());
        return body.build();
    }

    private static HttpUrl buildUrl(String url, ArrayMap<String, String> params) {
        HttpUrl.Builder builder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) builder.addQueryParameter(entry.getKey(), entry.getValue());
        return builder.build();
    }

    private static OkHttpClient.Builder getBuilder() {
        // 始终走 OkDns 包装器：即使"系统"模式（无 DoH 服务商）也需要它的 IPv4 优选过滤
        // （过滤坏 IPv6/回环劫持）和系统 DNS 失败时的公共 DoH 回退，否则封面等图片加载失败。
        // OkDns 内部在 doh==null 时本来就是用 Dns.SYSTEM，只是多了过滤和回退。
        Dns dns = dns();
        OkHttpClient.Builder builder = new OkHttpClient.Builder().addInterceptor(requestInterceptor()).addInterceptor(authInterceptor()).addNetworkInterceptor(responseInterceptor()).connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS).readTimeout(TIMEOUT, TimeUnit.MILLISECONDS).writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS).dns(dns).hostnameVerifier((hostname, session) -> true).sslSocketFactory(getSSLContext().getSocketFactory(), trustAllCertificates());
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY);
        //builder.addNetworkInterceptor(logging);
        // 全局代理包装走壳代理：开启时接管全部流量（回环除外），关闭时行为与原来一致
        // 按源定向选择器包在最外层：命中策略的流量先走 127.0.0.1:混合口，其余透传原链
        builder.proxySelector(SourceGate.wrapSelector(GlobalProxy.wrapSelector(selector())));
        builder.proxyAuthenticator(GlobalProxy.wrapAuthenticator(authenticator()));
        return builder;
    }

    private static SSLContext getSSLContext() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{trustAllCertificates()}, new SecureRandom());
            return context;
        } catch (Throwable e) {
            // 不许回 null：唯一调用点直接 getSocketFactory()，回 null 只会
            // 变成一条无真因的 NPE。失败就带着真因当场抛。
            throw new IllegalStateException("failed to create SSLContext", e);
        }
    }

    @SuppressLint({"TrustAllX509TrustManager", "CustomX509TrustManager"})
    private static X509TrustManager trustAllCertificates() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    private static class DebugEventListener extends EventListener {

        private final long startNs;

        private DebugEventListener() {
            this.startNs = System.nanoTime();
        }

        @Override
        public void callStart(@NonNull Call call) {
            if (!SpiderDebug.isEnabled()) return;
            log(call, "start", "method=" + call.request().method() + OkHttpLogPolicy.requestMetadata(call.request().headers()));
        }

        @Override
        public void connectStart(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull java.net.Proxy proxy) {
            if (!SpiderDebug.isEnabled()) return;
            log(call, "connectStart", "host=" + OkHttpLogPolicy.redactHost(inetSocketAddress.getHostString()) + ", proxyType=" + proxy.type());
        }

        @Override
        public void connectEnd(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull java.net.Proxy proxy, @Nullable Protocol protocol) {
            if (!SpiderDebug.isEnabled()) return;
            log(call, "connectEnd", "host=" + OkHttpLogPolicy.redactHost(inetSocketAddress.getHostString()) + ", proxyType=" + proxy.type() + ", protocol=" + protocol);
        }

        @Override
        public void connectFailed(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull java.net.Proxy proxy, @Nullable Protocol protocol, @NonNull IOException ioe) {
            if (!SpiderDebug.isEnabled()) return;
            log(call, "connectFailed", "host=" + OkHttpLogPolicy.redactHost(inetSocketAddress.getHostString()) + ", proxyType=" + proxy.type() + ", protocol=" + protocol + ", error=" + error(ioe));
        }

        @Override
        public void connectionAcquired(@NonNull Call call, @NonNull Connection connection) {
            if (!SpiderDebug.isEnabled()) return;
            log(call, "connectionAcquired", "protocol=" + connection.protocol() + ", proxyType=" + connection.route().proxy().type());
        }

        @Override
        public void responseHeadersEnd(@NonNull Call call, @NonNull Response response) {
            if (!SpiderDebug.isEnabled()) return;
            log(call, "response", "code=" + response.code() + ", message=" + response.message() + ", contentLength=" + response.header("Content-Length") + ", contentType=" + response.header("Content-Type") + ", contentRange=" + response.header("Content-Range"));
        }

        @Override
        public void callEnd(@NonNull Call call) {
            if (!SpiderDebug.isEnabled()) return;
            log(call, "end", "elapsedMs=" + elapsedMs());
        }

        @Override
        public void callFailed(@NonNull Call call, @NonNull IOException ioe) {
            if (!SpiderDebug.isEnabled()) return;
            log(call, "failed", "elapsedMs=" + elapsedMs() + ", error=" + error(ioe));
        }

        private void log(Call call, String event, String message) {
            SpiderDebug.log("okhttp-player", "%s url=%s %s", event, OkHttpLogPolicy.redactUrl(call.request().url()), message);
        }

        private long elapsedMs() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        }

        private String error(Throwable error) {
            return OkHttpLogPolicy.errorChain(error);
        }
    }

    public void clear() {
        cancelAll();
        dns().clear();
        selector().clear();
        authInterceptor().clear();
        requestInterceptor().clear();
        responseInterceptor().clear();
    }

    private static class Loader {
        static volatile OkHttp INSTANCE = new OkHttp();
    }
}
