package com.fongmi.android.tv.api.hk;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 移植自 8.83: com.example.hikerview.service.http.ContentTypePreInterceptor
 * (smali: decompiled/smali/com/example/hikerview/service/http/ContentTypePreInterceptor.smali)
 *
 * 请求前拦截：
 * 1. 若有 Cookie 头，备份到 Cookie-Temp
 * 2. 若 body 是 application/x-www-form-urlencoded 且有 Content-Type 头，备份到 Content-Type-Temp
 * 3. gitee.io 域名无 Referer 时，自动补 Referer 为 home + "/"
 * 4. gitee.com 域名无 Referer 时，自动补 Referer 为 https://gitee.com
 */
public class HkContentTypePreInterceptor implements Interceptor {

    public static final HkContentTypePreInterceptor INSTANCE = new HkContentTypePreInterceptor();

    private HkContentTypePreInterceptor() {
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Request.Builder builder = request.newBuilder();
        try {
            String cookie = request.header("Cookie");
            if (cookie != null) {
                builder.header("Cookie-Temp", cookie);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        RequestBody body = request.body();
        if (body != null) {
            MediaType contentType = body.contentType();
            if (contentType != null && "application/x-www-form-urlencoded".equals(contentType.toString())) {
                String ct = request.header("Content-Type");
                if (ct != null) {
                    builder.header("Content-Type-Temp", ct);
                }
            }
        }
        try {
            String host = request.url().host();
            if (host.contains("gitee.io")) {
                if (request.header("Referer") == null) {
                    String home = getHome(request.url().toString());
                    builder.header("Referer", home + "/");
                }
            }
            if (host.contains("gitee.com")) {
                if (request.header("Referer") == null) {
                    builder.header("Referer", "https://gitee.com");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return chain.proceed(builder.build());
    }

    /** 取 URL 的 home（scheme://host[:port]），对应 8.83 StringUtil.getHome */
    private static String getHome(String url) {
        try {
            okhttp3.HttpUrl h = okhttp3.HttpUrl.get(url);
            return h.scheme() + "://" + h.host() + (h.port() != okhttp3.HttpUrl.defaultPort(h.scheme()) ? ":" + h.port() : "");
        } catch (Exception e) {
            return url;
        }
    }
}
