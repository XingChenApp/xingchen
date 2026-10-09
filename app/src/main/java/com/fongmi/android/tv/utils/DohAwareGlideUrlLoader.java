package com.fongmi.android.tv.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.load.model.stream.HttpGlideUrlLoader;
import com.github.catvod.net.OkHttp;

import java.io.InputStream;

/**
 * Chooses Glide's network stack per request based on the effective DNS mode:
 *
 * - DoH provider selected (Tencent / Ali / 360) or custom host overrides present
 *   ({@code OkHttp.dns().isCustom()}): load covers through the shared custom
 *   OkHttp client. The client is fetched lazily per request so DoH mode switches
 *   (which rebuild the client via {@code OkHttp.resetClients()}) take effect
 *   without tearing down Glide.
 *
 * - System DNS mode: use Glide's stock {@link HttpGlideUrlLoader}
 *   (HttpURLConnection), bypassing the custom OkHttp stack (trust-all SSL,
 *   interceptors) entirely. This matches 默影视 behavior, where Glide was never
 *   forced onto OkHttp.
 */
public class DohAwareGlideUrlLoader implements ModelLoader<GlideUrl, InputStream> {

    private static final HttpGlideUrlLoader HTTP_LOADER = new HttpGlideUrlLoader();

    @Nullable
    @Override
    public LoadData<InputStream> buildLoadData(@NonNull GlideUrl model, int width, int height, @NonNull Options options) {
        if (OkHttp.dns().isCustom()) {
            return new OkHttpUrlLoader(OkHttp.client()).buildLoadData(model, width, height, options);
        }
        return HTTP_LOADER.buildLoadData(model, width, height, options);
    }

    @Override
    public boolean handles(@NonNull GlideUrl model) {
        return true;
    }

    public static class Factory implements ModelLoaderFactory<GlideUrl, InputStream> {

        @NonNull
        @Override
        public ModelLoader<GlideUrl, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
            return new DohAwareGlideUrlLoader();
        }

        @Override
        public void teardown() {
        }
    }
}
