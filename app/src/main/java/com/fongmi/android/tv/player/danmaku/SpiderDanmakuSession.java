package com.fongmi.android.tv.player.danmaku;

import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.media3.ui.danmaku.Danmaku;

import com.fongmi.android.tv.api.SiteApi;
import com.github.catvod.crawler.SpiderDebug;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 源自供弹幕订阅会话：FongMi 5.8.9 player/LiveDanmaku 的同职责移植——持一条
 * spider:// 订阅（SiteApi.danmaku → 蜘蛛 danmaku(id, emit)），把 py 递来的条目
 * 解析成弹幕、状态条目转状态回调，切源/退出时调订阅句柄收口（即 py 的 stop）。
 * 与上游的适配差异（记名）：① 类名/包按咱家 player/danmaku 会话族（LiveDanmaku*
 * 已被 ws 房间族占用）；② connect/stop 返回代次、由 PlayerManager 镜像进它的
 * liveDanmakuGeneration（咱家 ws 会话同形），上游是组件自持代次只防旧代回递；
 * ③ 解析结果以 Item 递出、media3 Danmaku 由 PlayerManager 在装配时刻打播放进度戳
 * 再 offerLiveBatch（咱家直播弹幕定版口径，见 PlayerManager.onLiveDanmakuBatch），
 * 上游 parseItem 直造 time=0 的 Danmaku 递消费方；④ 上游 setEnabled 内部开关未搬，
 * 启停由 PlayerManager 以 stop/connect 承担（ws 会话同形）。解析与状态语义逐字照上游。
 */
public final class SpiderDanmakuSession {

    public interface Listener {

        void onItem(long generation, Item item);

        void onState(long generation, String state, String msg);
    }

    /** 解析后的弹幕条目（上游 parseItem 的产物形，时间戳留到装配时打）。 */
    public record Item(String text, int type, int colorArgb, float size, int[] colors, long receivedAtMs) {
    }

    private final Listener listener;

    private volatile long generation;
    private volatile boolean closed;
    private Uri source;
    private ExecutorService executor;
    private AutoCloseable subscription;
    private String state = "";
    private String msg = "";

    public SpiderDanmakuSession(Listener listener) {
        this.listener = listener;
    }

    public static boolean isSpider(@Nullable Uri uri) {
        return uri != null && "spider".equalsIgnoreCase(uri.getScheme());
    }

    public synchronized long connect(String url) {
        if (closed) return generation;
        Uri uri = url == null ? null : Uri.parse(url);
        if (Objects.equals(source, uri)) return generation;
        source = uri;
        restart();
        return generation;
    }

    public synchronized long stop(String reason) {
        if (closed) return generation;
        source = null;
        generation++;
        updateState("", "");
        if (executor != null) executor.execute(this::closeSubscription);
        return generation;
    }

    public void release() {
        if (closed) return;
        synchronized (this) {
            closed = true;
            generation++;
            source = null;
            updateState("", "");
        }
        if (executor == null) return;
        executor.execute(this::closeSubscription);
        executor.shutdown();
    }

    private synchronized void restart() {
        long token = ++generation;
        Uri uri = source;
        updateState(uri == null ? "" : "connecting", "");
        if (executor == null || executor.isShutdown()) executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> subscribe(token, uri));
    }

    private boolean isCurrent(long token) {
        return !closed && generation == token;
    }

    private void subscribe(long token, @Nullable Uri uri) {
        if (!isCurrent(token)) return;
        closeSubscription();
        if (uri == null) return;
        try {
            subscription = SiteApi.danmaku(uri, item -> emit(token, item));
            if (!isCurrent(token)) closeSubscription();
        } catch (Exception error) {
            SpiderDebug.log(error);
            fail(token, error);
        }
    }

    private synchronized void emit(long token, JSONObject item) {
        if (!isCurrent(token)) return;
        if (item.has("state")) {
            String state = parseState(item);
            String msg = parseMessage(item);
            if (state.equals("error")) finish(msg);
            else updateState(state, msg);
        } else {
            listener.onItem(token, parseItem(item));
        }
    }

    private synchronized void fail(long token, Exception error) {
        if (!isCurrent(token)) return;
        String msg = error.getMessage();
        if (msg == null || msg.isBlank()) msg = error.getClass().getSimpleName();
        finish(msg.substring(0, Math.min(msg.length(), 512)));
    }

    private void finish(String msg) {
        generation++;
        executor.execute(this::closeSubscription);
        updateState("error", msg);
    }

    private void updateState(String state, String msg) {
        if (this.state.equals(state) && this.msg.equals(msg)) return;
        this.state = state;
        this.msg = msg;
        listener.onState(generation, state, msg);
    }

    private static String parseState(JSONObject item) {
        if (item.has("text") || !(item.opt("state") instanceof String state) || !(state.equals("ready") || state.equals("retrying") || state.equals("error"))) throw new IllegalArgumentException("Invalid danmaku state");
        return state;
    }

    private static String parseMessage(JSONObject item) {
        if (!item.has("msg")) return "";
        if (!(item.opt("msg") instanceof String msg) || msg.length() > 512) throw new IllegalArgumentException("Invalid danmaku message");
        return msg;
    }

    static Item parseItem(JSONObject item) {
        String text = parseText(item);
        int type = parseType(item);
        float size = parseSize(item);
        int color = toArgb(number(item, "color", 0xffffff));
        return new Item(text, type, color, size, colors(item), SystemClock.elapsedRealtime());
    }

    private static String parseText(JSONObject item) {
        if (!(item.opt("text") instanceof String text) || text.isBlank() || text.length() > 4096) throw new IllegalArgumentException("Danmaku requires text up to 4096 characters");
        return text;
    }

    private static int parseType(JSONObject item) {
        double value = number(item, "type", Danmaku.TYPE_SCROLL);
        int type = (int) value;
        if (value != type || !isSupportedType(type)) throw new IllegalArgumentException("Invalid danmaku type");
        return type;
    }

    private static boolean isSupportedType(int type) {
        return type == Danmaku.TYPE_SCROLL || type == Danmaku.TYPE_BOTTOM || type == Danmaku.TYPE_TOP || type == Danmaku.TYPE_REVERSE;
    }

    private static float parseSize(JSONObject item) {
        double size = number(item, "size", 0);
        if (size < 0 || size > 96) throw new IllegalArgumentException("Invalid danmaku size");
        return (float) size;
    }

    private static double number(JSONObject item, String key, double defaultValue) {
        if (!item.has(key)) return defaultValue;
        if (!(item.opt(key) instanceof Number value) || !Double.isFinite(value.doubleValue())) throw new IllegalArgumentException("Invalid danmaku " + key);
        return value.doubleValue();
    }

    private static int toArgb(double value) {
        if (value < 0 || value > 0xffffff || value != (int) value) throw new IllegalArgumentException("Invalid danmaku color");
        return 0xff000000 | (int) value;
    }

    @Nullable
    private static int[] colors(JSONObject item) {
        if (!item.has("colors")) return null;
        if (!(item.opt("colors") instanceof JSONArray values) || values.length() < 2 || values.length() > 8) throw new IllegalArgumentException("Danmaku colors requires 2 to 8 RGB values");
        int[] colors = new int[values.length()];
        for (int index = 0; index < colors.length; index++) {
            if (!(values.opt(index) instanceof Number value)) throw new IllegalArgumentException("Invalid danmaku color");
            colors[index] = toArgb(value.doubleValue());
        }
        return colors;
    }

    private void closeSubscription() {
        AutoCloseable previous = subscription;
        subscription = null;
        if (previous == null) return;
        try {
            previous.close();
        } catch (Exception error) {
            SpiderDebug.log(error);
        }
    }
}
