package com.fongmi.android.tv.api.hk;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import java.io.InputStream;

import okhttp3.Call;
import okhttp3.OkHttpClient;

/**
 * GlideUrl → InputStream 的 ModelLoader（8.83 OkHttpLibraryGlideModule 注册同款），
 * 数据抓取走 {@link HkOkHttpStreamFetcher}（@js= 流式解密语义）。
 * 缓存键用 GlideUrl 默认（即 8.83 官方 OkHttpUrlLoader 的 `new LoadData<>(model, fetcher)`），不做归一化。
 *
 * <p>逐图自动 DNS（照 8.83 OkHttpUrlLoader.buildLoadData 原文）：先判
 * {@link HkHttp#shouldUseAutoDNS(String)}，命中才把 client 换成
 * {@link HkHttp#buildAutoDNSClient(String, OkHttpClient)} 的派生 client
 * （名单内域名主解析走 DoH/静态地址，不再先过系统解析）；未命中用原 client。</p>
 *
 * <p>同时把原 client 与「首发是否已走自动 DNS」交给抓取器：首发没走自动
 * DNS 而加载失败时，抓取器可用原 client 派生自动 DNS client 换解析重试
 * 一次（名单外域名被系统解析给错 IP 的兜底，见 HkOkHttpStreamFetcher）。</p>
 */
public class HkGlideUrlLoader implements ModelLoader<GlideUrl, InputStream> {

    private final Call.Factory client;

    public HkGlideUrlLoader(Call.Factory client) {
        this.client = client;
    }

    @Override
    public LoadData<InputStream> buildLoadData(@NonNull GlideUrl model, int width, int height, @NonNull Options options) {
        Call.Factory use = client;
        String url = model.toStringUrl();
        boolean auto = false;
        if (url != null && client instanceof OkHttpClient && HkHttp.shouldUseAutoDNS(url)) {
            use = HkHttp.buildAutoDNSClient(url, (OkHttpClient) client);
            auto = true;
        }
        OkHttpClient base = client instanceof OkHttpClient ? (OkHttpClient) client : null;
        return new LoadData<>(model, new HkOkHttpStreamFetcher(use, model, base, auto));
    }

    @Override
    public boolean handles(@NonNull GlideUrl model) {
        return true;
    }

    public static class Factory implements ModelLoaderFactory<GlideUrl, InputStream> {

        private final Call.Factory client;

        public Factory(Call.Factory client) {
            this.client = client;
        }

        @NonNull
        @Override
        public ModelLoader<GlideUrl, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
            return new HkGlideUrlLoader(client);
        }

        @Override
        public void teardown() {
        }
    }
}
