package com.fongmi.android.tv.player.exo;

import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.source.preload.PreCacheHelper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 下一集数据预下：把已预解析出的下一集真地址，提前下一段数据进播放缓存。
 *
 * 缓存同源（成败关键）：起播链的 CacheDataSource 用的是 MediaSourceFactory.getCache()
 * 这个静态单例 SimpleCache（MediaSourceFactory.getDataSourceFactory 一路），
 * 本类照 PreCache.createHelper 的接法，把 PreCacheHelper 建在同一个 getCache() 上、
 * 上游用同一个 createUpstreamDataSourceFactory（PLAYBACK_PRELOAD 优先级，播放忙时自动让路）。
 * 缓存键：起播 DataSpec 不带自定义键，默认按 URI 字符串建键；预下用同一 URI 即同键，
 * 起播时直接命中已下好的分片/文件头，不需要任何额外索引。
 *
 * MPV 路没有这套共享缓存口径，本类只服务 Exo 路（上层预加载本就只走 Exo）。
 */
public class NextEpisodeDataPrefetcher {

    public enum State {IDLE, RUNNING, COMPLETED, CAPPED, FAILED}

    public interface Listener {
        void onStateChanged(State state, long bytesAdded);
    }

    /** 整集目标：短剧一集远短于 30 分钟，请求 30 分钟即等于下到集尾。 */
    public static final long WHOLE_EPISODE_TARGET_MS = 30 * 60 * 1000L;
    /**
     * 长集预下时长：120 秒。按常见 VOD 码率 2–6Mbps 约 30–90MB，
     * 落定后先靠这段连续播，其余边播边下；起播瞬态是秒级，120 秒有十倍以上余量。
     */
    public static final long HEAD_TARGET_MS = 120 * 1000L;
    /** 整集预下的字节保险丝：防把长集误判成短集时下爆缓存。 */
    private static final long WHOLE_EPISODE_BYTE_CAP = 256L * 1024 * 1024;
    private static final long HEAD_BYTE_CAP = 160L * 1024 * 1024;
    private static final long WATCHDOG_MS = 2000L;

    private State state = State.IDLE;
    private long bytesAdded;
    private long baselineBytes;
    private String cacheKey;
    private long byteCapBytes;
    private long targetMs;
    private String url;
    private Map<String, String> headers;
    private MediaItem item;
    private boolean wholeEpisode;
    private Listener listener;
    private PreCacheHelper helper;
    private HandlerThread worker;
    private ThreadPoolExecutor executor;
    private Handler watchdogHandler;
    private boolean sessionActive;

    private final PreCacheHelper.Listener helperListener = new PreCacheHelper.Listener() {
        @Override
        public void onPrepared(MediaItem originalMediaItem, MediaItem preparedMediaItem) {
        }

        @Override
        public void onPreCacheCompleted(MediaItem mediaItem) {
            finish(State.COMPLETED);
        }

        @Override
        public void onPrepareError(MediaItem mediaItem, IOException exception) {
            finish(State.FAILED);
        }

        @Override
        public void onDownloadError(MediaItem mediaItem, IOException exception) {
            finish(State.FAILED);
        }
    };

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            boolean capped;
            synchronized (NextEpisodeDataPrefetcher.this) {
                if (!sessionActive) return;
                refreshBytesLocked();
                capped = bytesAdded >= byteCapBytes;
                if (!capped && watchdogHandler != null) watchdogHandler.postDelayed(this, WATCHDOG_MS);
            }
            if (capped) finish(State.CAPPED);
        }
    };

    /** 地址是否可预下：仅 http(s)、非本地代理（代理路起播不走这套缓存）、非拼接串。 */
    public static boolean isEligibleUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String scheme = Uri.parse(url).getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        if (MediaSourceFactory.isLocalProxyUrl(url)) return false;
        return !MediaSourceFactory.isConcatenatingUrl(url);
    }

    /**
     * 开始预下。wholeEpisode=true 时按下完整集目标（短剧），否则只下开头 HEAD_TARGET_MS。
     * 返回 false 表示没启动起来（状态置 FAILED，可由 topUp 重试）。
     */
    public boolean start(String url, Map<String, String> headers, MediaItem item, boolean wholeEpisode, Listener listener) {
        synchronized (this) {
            shutdownSessionLocked();
            state = State.IDLE;
            if (!isEligibleUrl(url) || item == null) {
                state = State.FAILED;
                return false;
            }
            this.url = url;
            this.headers = headers;
            this.item = item;
            this.wholeEpisode = wholeEpisode;
            this.listener = listener;
            this.targetMs = wholeEpisode ? WHOLE_EPISODE_TARGET_MS : HEAD_TARGET_MS;
            this.byteCapBytes = byteCapFor(wholeEpisode);
            this.bytesAdded = 0;
            this.cacheKey = item.localConfiguration == null || item.localConfiguration.uri == null ? url : item.localConfiguration.uri.toString();
            try {
                this.baselineBytes = cachedBytesLocked();
                worker = new HandlerThread("NextEpisodeDataPrefetch");
                worker.start();
                executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
                DataSource.Factory upstream = MediaSourceFactory.createUpstreamDataSourceFactory(headers, url);
                helper = new PreCacheHelper.Factory(MediaSourceFactory.getCache(), upstream, ExoUtil.buildRenderersFactory(), worker.getLooper())
                        .setDownloadExecutor(executor)
                        .setListener(helperListener)
                        .create(item);
                sessionActive = true;
                state = State.RUNNING;
                watchdogHandler = new Handler(worker.getLooper());
                watchdogHandler.postDelayed(watchdog, WATCHDOG_MS);
                helper.preCache(0, targetMs);
            } catch (Throwable e) {
                state = State.FAILED;
                shutdownSessionLocked();
                return false;
            }
            return true;
        }
    }

    /** 补下：在下就重申同一区间（已下分片自动跳过）；已失败则按原参数重建一场。 */
    public boolean topUp() {
        synchronized (this) {
            if (state == State.RUNNING && helper != null) {
                try {
                    helper.preCache(0, targetMs);
                    return true;
                } catch (Throwable e) {
                    return false;
                }
            }
            if (state == State.FAILED && url != null && item != null) {
                return start(url, headers, item, wholeEpisode, listener);
            }
            return false;
        }
    }

    /** 取消并释放整场预下（已下进缓存的数据保留，起播仍可命中）。 */
    public void cancel() {
        synchronized (this) {
            state = State.IDLE;
            listener = null;
            shutdownSessionLocked();
            url = null;
            headers = null;
            item = null;
            cacheKey = null;
            bytesAdded = 0;
        }
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized long getBytesAdded() {
        if (sessionActive) refreshBytesLocked();
        return bytesAdded;
    }

    private void finish(State outcome) {
        Listener target;
        long bytes;
        synchronized (this) {
            if (!sessionActive || state != State.RUNNING) return;
            state = outcome;
            refreshBytesLocked();
            bytes = bytesAdded;
            target = listener;
            shutdownSessionLocked();
        }
        if (target != null) target.onStateChanged(outcome, bytes);
    }

    private void refreshBytesLocked() {
        // 只量本集缓存键名下的字节：整缓存增量会把前台当前集自身的缓存增长
        // 也算进来，可误触 byteCap 提前停预下、回报也虚高。分段内容（HLS）的
        // 分片各有键名、本键只量到清单本体，cap 对分段内容不生效——其下载量
        // 由 PreCacheHelper 的 targetMs 天然封顶；单文件内容计量准确。
        long now = cachedBytesLocked();
        if (now >= baselineBytes) bytesAdded = now - baselineBytes;
    }

    private long cachedBytesLocked() {
        if (cacheKey == null) return 0;
        try {
            return MediaSourceFactory.getCache().getCachedBytes(cacheKey, 0, Long.MAX_VALUE);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static long byteCapFor(boolean wholeEpisode) {
        long absolute = wholeEpisode ? WHOLE_EPISODE_BYTE_CAP : HEAD_BYTE_CAP;
        long capacity = 0;
        try {
            capacity = MediaSourceFactory.getCacheCapacityBytes();
        } catch (Throwable ignored) {
        }
        if (capacity <= 0) return absolute;
        // 保险丝不超过缓存容量的一半，免得一集把整个缓存挤爆；容量读数异常小时给个下限。
        return Math.min(absolute, Math.max(capacity / 2, 16L * 1024 * 1024));
    }

    private void shutdownSessionLocked() {
        sessionActive = false;
        if (watchdogHandler != null) watchdogHandler.removeCallbacks(watchdog);
        watchdogHandler = null;
        PreCacheHelper retiringHelper = helper;
        helper = null;
        HandlerThread retiringWorker = worker;
        worker = null;
        ThreadPoolExecutor retiringExecutor = executor;
        executor = null;
        if (retiringHelper != null) retiringHelper.stop();
        if (retiringHelper != null) retiringHelper.release(false);
        if (retiringWorker == null) {
            if (retiringExecutor != null) retiringExecutor.shutdown();
            return;
        }
        // 照 PreCache.release 的口径：release() 会把取消动作投递到 worker looper，
        // 执行器与线程的关闭排在那条投递之后，免得下载器向已关闭的执行器提交任务。
        new Handler(retiringWorker.getLooper()).post(() -> {
            if (retiringExecutor != null) retiringExecutor.shutdown();
            retiringWorker.quitSafely();
        });
    }
}
