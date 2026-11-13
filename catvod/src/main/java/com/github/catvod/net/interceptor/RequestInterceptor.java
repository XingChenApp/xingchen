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

    public void clear() {
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

    // auth 自动补参已禁用：按 host 缓存 auth 会导致不同视频间串参，引发 403（Mofilm 直接移除了该逻辑）。
    // 2026-10-11 曾按"跟默影视一模一样"恢复过，导致 115 直链被注入过期 auth 参数报"网络读取失败"，故重新禁用。
    private void checkAuth(HttpUrl url, Request.Builder builder) {
    }
}
