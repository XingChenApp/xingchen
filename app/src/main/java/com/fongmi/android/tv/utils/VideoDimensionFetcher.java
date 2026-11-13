package com.fongmi.android.tv.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 视频宽高后台预取：取 HLS playlist 的 RESOLUTION 或 MP4 头部的 avc1 宽高，
 * 供进入全屏前精准判断横竖屏，避免先横后竖闪一下。
 * <p>
 * 只对用户标记为短剧的源预取（{@link ShortDramaHelper}），控制流量消耗。
 * 所有网络操作在后台线程，失败静默丢弃。
 */
public class VideoDimensionFetcher {

    private static final String TAG = "VideoDimFetch";
    private static final long FETCH_TIMEOUT_MS = 5000;
    private static final int MP4_HEADER_SIZE = 64 * 1024;

    private static final Pattern RESOLUTION = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)");
    private static final Pattern BANDWIDTH = Pattern.compile("BANDWIDTH=(\\d+)");

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "video-dim-fetch");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** 防抖：同一 URL 500ms 内只调度一次 */
    private static final java.util.Set<String> PENDING = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    public interface Callback {
        void onResult(String url, int width, int height);
    }

    /**
     * 防抖预取：同一 URL 500ms 内只发起一次后台任务。供列表滑动时调用。
     */
    public static void preFetchDebounced(String url) {
        if (TextUtils.isEmpty(url)) return;
        if (VideoDimensionCache.get(url) != null) return; // 已有缓存
        String low = url.toLowerCase();
        if (!low.startsWith("http://") && !low.startsWith("https://")) return;
        if (!PENDING.add(url)) return; // 已在等待队列
        MAIN.postDelayed(() -> {
            PENDING.remove(url);
            preFetch(url);
        }, 500);
    }

    /**
     * 后台预取并写入缓存。无回调版本，供列表滑动时调用。
     */
    public static void preFetch(String url) {
        fetch(url, null);
    }

    /**
     * 异步取宽高。回调在主线程执行。取不到或失败时不回调。
     */
    public static void fetch(String url, Callback callback) {
        if (TextUtils.isEmpty(url)) return;
        // 已有缓存直接回
        int[] cached = VideoDimensionCache.get(url);
        if (cached != null) {
            if (callback != null) {
                int w = cached[0], h = cached[1];
                MAIN.post(() -> callback.onResult(url, w, h));
            }
            return;
        }
        // 只处理 http(s)，跳过 magnet/thunder 等
        String low = url.toLowerCase();
        if (!low.startsWith("http://") && !low.startsWith("https://")) return;
        EXECUTOR.execute(() -> {
            try {
                int[] wh = doFetch(url);
                if (wh != null) {
                    VideoDimensionCache.put(url, wh[0], wh[1]);
                    if (callback != null) {
                        MAIN.post(() -> callback.onResult(url, wh[0], wh[1]));
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static int[] doFetch(String url) throws Exception {
        String low = url.toLowerCase();
        if (low.contains(".m3u8") || low.contains("m3u8")) {
            return fetchHls(url);
        }
        return fetchMp4Header(url);
    }

    private static OkHttpClient client() {
        return OkHttp.client().newBuilder()
                .connectTimeout(FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    private static String get(String url, String range) throws IOException {
        Request.Builder builder = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36");
        if (!TextUtils.isEmpty(range)) builder.header("Range", range);
        try (Response resp = client().newCall(builder.build()).execute()) {
            if (!resp.isSuccessful()) return null;
            ResponseBody body = resp.body();
            return body == null ? null : body.string();
        }
    }

    private static byte[] getBytes(String url, String range) throws IOException {
        Request.Builder builder = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36");
        if (!TextUtils.isEmpty(range)) builder.header("Range", range);
        try (Response resp = client().newCall(builder.build()).execute()) {
            if (!resp.isSuccessful()) return null;
            ResponseBody body = resp.body();
            return body == null ? null : body.bytes();
        }
    }

    /**
     * HLS：取 playlist，解析 #EXT-X-STREAM-INF 的 RESOLUTION。
     * Master playlist 取带宽最高的 variant；media playlist 无分辨率信息返回 null。
     */
    private static int[] fetchHls(String url) {
        try {
            String playlist = get(url, null);
            if (TextUtils.isEmpty(playlist)) return null;
            // 找所有带 RESOLUTION 的 STREAM-INF，取带宽最高的
            String[] lines = playlist.split("\n");
            int bestW = 0, bestH = 0;
            long bestBw = -1;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.startsWith("#EXT-X-STREAM-INF")) continue;
                Matcher rm = RESOLUTION.matcher(line);
                if (!rm.find()) continue;
                int w = Integer.parseInt(rm.group(1));
                int h = Integer.parseInt(rm.group(2));
                long bw = 0;
                Matcher bm = BANDWIDTH.matcher(line);
                if (bm.find()) {
                    try {
                        bw = Long.parseLong(bm.group(1));
                    } catch (Exception ignored) {
                    }
                }
                if (bw >= bestBw) {
                    bestBw = bw;
                    bestW = w;
                    bestH = h;
                }
            }
            if (bestW > 0 && bestH > 0) return new int[]{bestW, bestH};
            // 可能是变体 playlist（无 STREAM-INF），尝试取下一级
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                // 相对 URL 拼接
                String variant = resolveUrl(url, line);
                if (variant != null && !variant.equals(url)) {
                    return fetchHls(variant);
                }
                break; // 只试第一个
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String resolveUrl(String base, String relative) {
        try {
            if (relative.startsWith("http://") || relative.startsWith("https://")) return relative;
            int slash = base.lastIndexOf('/');
            if (slash < 0) return null;
            return base.substring(0, slash + 1) + relative;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * MP4：Range 取前 64KB，解析 moov/trak/mdia/minf/stbl/stsd/avc1 的宽高。
     * avc1 box 内：width（2 字节）height（2 字节）在特定偏移。
     * 简化实现：在 avc1 后找 "avc1" 标记，按 ISO BMFF 结构读取。
     */
    private static int[] fetchMp4Header(String url) {
        try {
            byte[] data = getBytes(url, "bytes=0-" + (MP4_HEADER_SIZE - 1));
            if (data == null || data.length < 100) return null;
            return parseAvc1Dimensions(data);
        } catch (Exception e) {
            return null;
        }
    }

    private static int readU16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static int readU32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static int[] parseAvc1Dimensions(byte[] data) {
        try {
            // 在头部中找 "avc1" 四字节标记
            for (int i = 0; i < data.length - 100; i++) {
                if (data[i] == 'a' && data[i + 1] == 'v' && data[i + 2] == 'c' && data[i + 3] == '1') {
                    // avc1 SampleEntry 结构：
                    // +8: reserved(6) + data_reference_index(2)
                    // +16: pre_defined(16) + reserved(2) + pre_defined(3*4)
                    // +32: width(2) height(2)
                    // 从 'avc1' 起算：box header 8 字节（size+type），然后 SampleEntry
                    int base = i + 8; // 跳过 size(4)+type(4)，指向 SampleEntry 起始
                    // reserved 6 + data_reference_index 2 = 8
                    // pre_defined 16 + reserved 2 + pre_defined 12 = 30
                    int widthOffset = base + 8 + 30;
                    int heightOffset = widthOffset + 2;
                    if (heightOffset + 1 >= data.length) continue;
                    int w = readU16(data, widthOffset);
                    int h = readU16(data, heightOffset);
                    // 合理性校验
                    if (w >= 16 && w <= 8192 && h >= 16 && h <= 8192) {
                        return new int[]{w, h};
                    }
                }
            }
            // 兜底：找 tkhd 的宽高（4 字节定点数，取整数部分）
            for (int i = 0; i < data.length - 100; i++) {
                if (data[i] == 't' && data[i + 1] == 'k' && data[i + 2] == 'h' && data[i + 3] == 'd') {
                    // tkhd version 0: width 在 +76（4 字节 16.16 定点），height 在 +80
                    // 先读 version
                    int base = i + 8;
                    if (base >= data.length) continue;
                    int version = data[base] & 0xFF;
                    int wOff, hOff;
                    if (version == 0) {
                        wOff = base + 76;
                        hOff = base + 80;
                    } else {
                        wOff = base + 88;
                        hOff = base + 92;
                    }
                    if (hOff + 3 >= data.length) continue;
                    int w = readU32(data, wOff) >> 16;
                    int h = readU32(data, hOff) >> 16;
                    if (w >= 16 && w <= 8192 && h >= 16 && h <= 8192) {
                        return new int[]{w, h};
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
