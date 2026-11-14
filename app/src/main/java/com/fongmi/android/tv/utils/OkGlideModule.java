package com.fongmi.android.tv.utils;

import android.content.Context;
import android.graphics.drawable.PictureDrawable;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.caverock.androidsvg.SVG;
import com.fongmi.android.tv.api.hk.HkGlideUrlLoader;
import com.fongmi.android.tv.api.hk.HkHttp;

import java.io.InputStream;

@GlideModule
public class OkGlideModule extends AppGlideModule {

    // 对齐 8.83/Glide 默认磁盘缓存上限 250MB
    private static final long DISK_CACHE_SIZE = 250L * 1024L * 1024L;

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setLogLevel(Log.ERROR);
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, DISK_CACHE_SIZE));
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, Registry registry) {
        // 8.83 OkHttpLibraryGlideModule 同款：GlideUrl→InputStream 走自研 fetcher，
        // 图片经海阔专用 OkHttp client（信任所有证书 + DoH + CookieJar + Brotli），
        // @js= 在此做流式解密。
        // HttpURLConnection 无法注入自定义 Dns，会暴露在异常系统 DNS 下，故不用它。
        registry.replace(GlideUrl.class, InputStream.class, new HkGlideUrlLoader.Factory(HkHttp.imageClient()));
        // SVG 解码（照官 8.83 MyAppGlideModule 注册三件原文，账
        // hk-cover-missing-types-20261114 型2）：服务端 200 回的
        // image/svg+xml 封面/图标此前无解码器全灭。注册序照官——
        // transcoder 用 register、decoder 与 encoder 用 append：
        // SVG 解码 append 在位图解码之后，位图（含 hkimage 解密通道
        // 解出的位图字节）先走位图路，互不抢；只有位图解不出的
        // SVG 字节落 SvgDecoder。
        registry.register(SVG.class, PictureDrawable.class, new SvgDrawableTranscoder())
                .append(InputStream.class, SVG.class, new SvgDecoder())
                .append(SVG.class, new SvgEncoder());
    }
}
