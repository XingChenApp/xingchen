package com.fongmi.android.tv.ui.hk;

import android.text.TextUtils;

import com.github.catvod.utils.Util;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 照抄 8.83 ScanLiveTVUtils（smali_classes2/ui/video/util/ScanLiveTVUtils.smali + $MyRunnable）：
 * 取本机局域网 IP 前缀，逐个探测 http://{前缀}{0..255}:12345/index.html，响应体等于 "ok" 的
 * 第一个地址即世界直播远程地址；256 个全部失败才回调失败。超时 2000ms。
 * 原版 OkGo 日志拦截器与不安全 SSL 配置与扫描语义无关，未搬。
 * 原版回调线程为 OkHttp 分发线程，UI 归置由调用方（HkLivePlayerHelper）照原版 runOnUiThread 包裹。
 */
public class HkScanLiveTvUtils {

    private static final String HTTP = "http://";
    private static final String PLAY_URL = "/index.html";
    private static final String PORT = ":12345";

    public static boolean isScanning;

    private AtomicInteger counter;
    private Consumer<String> failListener;
    private AtomicBoolean hasFound;
    private OkHttpClient okHttpClient;
    private Consumer<String> okListener;

    private String getLocAddrIndex(String ip) {
        if (TextUtils.isEmpty(ip)) return "";
        int index = ip.lastIndexOf(".");
        if (index < 0) return "";
        return ip.substring(0, index + 1);
    }

    public void scan(Consumer<String> okListener, Consumer<String> failListener) {
        isScanning = true;
        this.okListener = okListener;
        this.failListener = failListener;
        try {
            String localIp = Util.getIp();
            String prefix = getLocAddrIndex(localIp == null ? "" : localIp);
            if (TextUtils.isEmpty(prefix)) {
                // 1.4.16 修复（扫查中4）：原版此处只打日志返回、isScanning 永不复位，进程内扫描永久失效；
                // 改走既有失败回调通路并复位，之后开网再扫可正常重试
                isScanning = false;
                if (failListener != null) failListener.accept("");
                return;
            }
            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(2000, TimeUnit.MILLISECONDS)
                    .readTimeout(2000, TimeUnit.MILLISECONDS)
                    .writeTimeout(2000, TimeUnit.MILLISECONDS)
                    .build();
            hasFound = new AtomicBoolean(false);
            counter = new AtomicInteger(256);
            for (int i = 0; i < 256; i++) {
                probe(prefix + i);
            }
        } catch (Exception e) {
            // 1.4.16 修复（扫查中5）：循环级异常保证失败回调必达、isScanning 必复位，不让上层加载框永挂
            isScanning = false;
            if (failListener != null) failListener.accept("");
        }
    }

    private void probe(final String ip) {
        try {
            if (hasFound.get()) {
                onProbeDone();
                return;
            }
            String url = HTTP + ip + PORT + PLAY_URL;
            Request request = new Request.Builder().url(url).get().build();
            okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                onProbeDone();
            }

            @Override
            public void onResponse(Call call, Response response) {
                boolean ok = false;
                try {
                    ResponseBody body = response.body();
                    if (body != null) ok = "ok".equals(body.string());
                } catch (Exception ignored) {
                } finally {
                    response.close();
                }
                if (ok && hasFound.compareAndSet(false, true)) {
                    isScanning = false;
                    if (okListener != null) okListener.accept(HTTP + ip + PORT);
                }
                onProbeDone();
            }
            });
        } catch (Exception e) {
            // 1.4.16 修复（扫查中5）：单个地址畸形（url() 抛 IllegalArgumentException 等）就地吞掉，
            // 按一次探测失败计数后继续扫其余地址，不中断整个扫描循环
            onProbeDone();
        }
    }

    private void onProbeDone() {
        if (counter.decrementAndGet() == 0 && !hasFound.get()) {
            isScanning = false;
            if (failListener != null) failListener.accept("");
        }
    }
}
