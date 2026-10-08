package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class RequestInterceptor implements Interceptor {

    public RequestInterceptor() {
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        Request.Builder builder = request.newBuilder();
        HttpUrl url = request.url();
        checkAuth(url, builder);
        return chain.proceed(builder.build());
    }

    // auth 自动补参已禁用：按 host 缓存 auth 会导致不同视频间串参，引发 403（Mofilm 直接移除了该逻辑）
    private void checkAuth(HttpUrl url, Request.Builder builder) {
    }

    public void clear() {
    }
}
