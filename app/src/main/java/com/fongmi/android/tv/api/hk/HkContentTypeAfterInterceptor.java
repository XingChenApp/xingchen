package com.fongmi.android.tv.api.hk;

import java.io.IOException;
import java.util.Set;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 移植自 8.83: com.example.hikerview.service.http.ContentTypeAfterInterceptor
 * (smali: decompiled/smali/com/example/hikerview/service/http/ContentTypeAfterInterceptor.smali)
 * 8.83 中作为 networkInterceptor 使用。
 *
 * 请求后（网络层）拦截：
 * 1. 若有 Cookie-Temp：值为 "#noCookie#" 则删除 Cookie 头，否则用其值恢复 Cookie 头；最后删除 Cookie-Temp
 * 2. 若有 Content-Type-Temp：恢复 Content-Type 头，删除 Content-Type-Temp
 * 3. 删除所有值为 "hiker://empty" 的头（占位清理）
 */
public class HkContentTypeAfterInterceptor implements Interceptor {

    public static final HkContentTypeAfterInterceptor INSTANCE = new HkContentTypeAfterInterceptor();

    private HkContentTypeAfterInterceptor() {
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Request.Builder builder = request.newBuilder();
        try {
            String cookieTemp = request.header("Cookie-Temp");
            if (cookieTemp != null) {
                if ("#noCookie#".equals(cookieTemp)) {
                    builder.removeHeader("Cookie");
                } else {
                    builder.header("Cookie", cookieTemp);
                }
                builder.removeHeader("Cookie-Temp");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (request.body() != null) {
            String ctTemp = request.header("Content-Type-Temp");
            if (ctTemp != null) {
                builder.header("Content-Type", ctTemp);
                builder.removeHeader("Content-Type-Temp");
            }
        }
        try {
            Set<String> names = request.headers().names();
            for (String name : names) {
                String value = request.headers().get(name);
                if ("hiker://empty".equals(value)) {
                    builder.removeHeader(name);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return chain.proceed(builder.build());
    }
}
