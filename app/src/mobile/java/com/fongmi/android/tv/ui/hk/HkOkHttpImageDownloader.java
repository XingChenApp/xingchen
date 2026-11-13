package com.fongmi.android.tv.ui.hk;

import com.bumptech.glide.load.model.GlideUrl;
import com.zzhoujay.richtext.callback.BitmapStream;
import com.zzhoujay.richtext.ig.ImageDownloader;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 照抄 8.83 OkHttpImageDownloader（ui/richtext/OkHttpImageDownloader.smali）：
 * download 只包一层惰性 BitmapStreamWrapper；真正请求在 getInputStream 时执行——
 * 先 GlideUtil.getGlideUrl(url, url) 取带头地址（GlideUrl 时把 headers 逐个 addHeader），
 * 否则裸 url 建 GET；客户端为静态单例：全信任 SSL + 主机名不校验 + 读/写/连各 10 秒。
 * 原版非 http 地址先走 EpubFile 取图（epub 子系统），星辰无此子系统，该分支略过、直接走请求路径。
 */
public class HkOkHttpImageDownloader implements ImageDownloader {

    @Override
    public BitmapStream download(String url) throws IOException {
        return new BitmapStreamWrapper(url);
    }

    private static OkHttpClient getClient() {
        return OkHttpClientHolder.CLIENT;
    }

    private static class OkHttpClientHolder {
        static final OkHttpClient CLIENT;

        static {
            X509TrustManager trustAll = new X509TrustManager() {
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
            SSLContext sslContext = null;
            try {
                sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, new TrustManager[]{trustAll}, new SecureRandom());
            } catch (Exception e) {
                e.printStackTrace();
            }
            OkHttpClient.Builder builder = new OkHttpClient().newBuilder()
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .connectTimeout(10, TimeUnit.SECONDS);
            if (sslContext != null) {
                builder.sslSocketFactory(sslContext.getSocketFactory(), trustAll);
            }
            builder.hostnameVerifier((hostname, session) -> true);
            CLIENT = builder.build();
        }
    }

    private static class BitmapStreamWrapper implements BitmapStream {
        private InputStream inputStream;
        private Response response;
        private final String url;

        BitmapStreamWrapper(String url) {
            this.url = url == null ? "" : url;
        }

        @Override
        public void close() throws IOException {
            if (inputStream != null) inputStream.close();
            if (response != null) response.close();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            Object model = HkGlideUtil.getGlideUrl(url, url);
            Request request;
            if (model instanceof GlideUrl) {
                GlideUrl glideUrl = (GlideUrl) model;
                Request.Builder builder = new Request.Builder().url(glideUrl.toString());
                for (Map.Entry<String, String> entry : glideUrl.getHeaders().entrySet()) {
                    builder.addHeader(entry.getKey(), entry.getValue());
                }
                request = builder.get().build();
            } else {
                request = new Request.Builder().url(url).get().build();
            }
            response = getClient().newCall(request).execute();
            inputStream = response.body().byteStream();
            return inputStream;
        }
    }
}
