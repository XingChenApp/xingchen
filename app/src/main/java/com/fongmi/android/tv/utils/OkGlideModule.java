package com.fongmi.android.tv.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;

@GlideModule
public class OkGlideModule extends AppGlideModule {

    private static final long DISK_CACHE_SIZE = 1024L * 1024L * 1024L; // 1GB

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setLogLevel(Log.ERROR);
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, DISK_CACHE_SIZE));
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, Registry registry) {
        registry.replace(GlideUrl.class, InputStream.class, new DohAwareGlideUrlLoader.Factory());
    }
}
