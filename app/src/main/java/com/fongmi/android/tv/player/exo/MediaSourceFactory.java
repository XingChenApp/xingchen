package com.fongmi.android.tv.player.exo;

import static androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.TsExtractor;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.exo.ass.AssFontSet;
import com.fongmi.android.tv.player.exo.ass.ExoAssSession;
import com.fongmi.android.tv.player.cache.DiskCacheCapacityPolicy;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MediaSourceFactory implements MediaSource.Factory {

    private static final String CONCAT_SOURCE_SEPARATOR = "***";
    private static final String CONCAT_SOURCE_SEPARATOR_REGEX = "\\*\\*\\*";
    private static final String CONCAT_DURATION_SEPARATOR = "|||";
    private static final String CONCAT_DURATION_SEPARATOR_REGEX = "\\|\\|\\|";
    /**
     * 海阔本地代理（startProxyServer 返回的 127.0.0.1 回环）超时：
     * 代理连上后 X 秒无数据就抛错给播放器，不再无限 BUFFERING（短剧实证断点）。
     * 仅代理 URL 走这组超时，非代理 URL 绝不受影响。
     */
    private static final int PROXY_CONNECT_TIMEOUT_MS = 8000;
    private static final int PROXY_READ_TIMEOUT_MS = 15000;
    private static final PriorityTaskManager PLAYBACK_PRIORITY_MANAGER = new PriorityTaskManager();
    private static final CacheCapacityState CACHE_CAPACITY_STATE = new CacheCapacityState();

    private static StandaloneDatabaseProvider databaseProvider;
    private static Cache cache;

    private final DefaultMediaSourceFactory defaultMediaSourceFactory;
    private OkHttpDataSource.Factory httpDataSourceFactory;
    private DataSource.Factory dataSourceFactory;
    private ExtractorsFactory extractorsFactory;
    /** 仅供 127.0.0.1 本地代理 URL 用的带超时链路（实例缓存，保证 applyHeaders 互不串）。 */
    private OkHttpDataSource.Factory proxyHttpDataSourceFactory;
    private DataSource.Factory proxyDataSourceFactory;
    @Nullable private final ExoDolbyVisionPlaybackState dolbyVisionPlaybackState;
    @Nullable private final ExoAssSession assSession;

    public MediaSourceFactory() {
        this(null);
    }

    MediaSourceFactory(
            @Nullable ExoDolbyVisionPlaybackState dolbyVisionPlaybackState) {
        this(dolbyVisionPlaybackState, null);
    }

    MediaSourceFactory(@Nullable ExoDolbyVisionPlaybackState dolbyVisionPlaybackState,
            @Nullable ExoAssSession assSession) {
        this.dolbyVisionPlaybackState = dolbyVisionPlaybackState;
        this.assSession = assSession;
        defaultMediaSourceFactory = new DefaultMediaSourceFactory(getDataSourceFactory(), getExtractorsFactory()).setLoadOnlySelectedTracks(PlaybackPerformanceSetting.isLoadOnlySelectedTracksEnabled());
    }

    static DataSource.Factory createUpstreamDataSourceFactory(Map<String, String> headers) {
        return createUpstreamDataSourceFactory(headers, "");
    }

    static DataSource.Factory createUpstreamDataSourceFactory(Map<String, String> headers, String url) {
        OkHttpDataSource.Factory factory = new OkHttpDataSource.Factory(OkHttp.player());
        applyHeaders(factory, headers, url);
        DataSource.Factory upstream = new DefaultDataSource.Factory(App.get(), factory);
        DataSource.Factory recovered = new HttpEofRecoveryDataSource.Factory(upstream);
        return new PriorityTaskDataSource.Factory(recovered, PLAYBACK_PRIORITY_MANAGER, C.PRIORITY_PLAYBACK_PRELOAD, true);
    }

    static synchronized Cache getCache() {
        if (cache != null) return cache;
        File dir = Path.exoCache();
        DiskCacheCapacityPolicy.Decision decision = resolveCapacity(dir, FileUtil.getDirectorySize(dir));
        long capacityBytes = initialCapacityBytes(decision);
        Cache created = new SimpleCache(dir, new LeastRecentlyUsedCacheEvictor(capacityBytes), getDatabaseProvider());
        cache = created;
        CACHE_CAPACITY_STATE.recordCreated(capacityBytes);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-cache", "created capacityBytes=%d policy=%s existingBytes=%d availableBytes=%d reserveBytes=%d", capacityBytes, decision.state(), decision.existingCacheBytes(), decision.availableStorageBytes(), decision.reserveBytes());
        return created;
    }

    public static synchronized void acquireCacheSession() {
        DiskCacheCapacityPolicy.Decision decision = refreshPendingCacheCapacity();
        if (isReliable(decision) && CACHE_CAPACITY_STATE.canReleasePending()) rebuildCacheLocked("next-player-session");
        CACHE_CAPACITY_STATE.acquireSession();
    }

    public static synchronized void releaseCacheSession() {
        CACHE_CAPACITY_STATE.releaseSession();
        DiskCacheCapacityPolicy.Decision decision = refreshPendingCacheCapacity();
        if (isReliable(decision) && CACHE_CAPACITY_STATE.canReleasePending()) rebuildCacheLocked("last-player-release");
    }

    private static StandaloneDatabaseProvider getDatabaseProvider() {
        if (databaseProvider == null) databaseProvider = new StandaloneDatabaseProvider(App.get());
        return databaseProvider;
    }

    private static DiskCacheCapacityPolicy.Decision resolveCapacity(File dir, long existingCacheBytes) {
        FileUtil.StorageSpace storage = FileUtil.getStorageSpace(dir);
        return DiskCacheCapacityPolicy.resolve(storage.available(), PreloadSetting.getPreloadSizeBytes(PlayerSetting.EXO), existingCacheBytes, storage.availableBytes(), storage.totalBytes());
    }

    private static long initialCapacityBytes(DiskCacheCapacityPolicy.Decision decision) {
        if (!isReliable(decision)) return decision.existingCacheBytes();
        return decision.effectiveCapacityBytes();
    }

    static synchronized long getCacheCapacityBytes() {
        DiskCacheCapacityPolicy.Decision decision = refreshPendingCacheCapacity();
        return cache == null ? initialCapacityBytes(decision) : CACHE_CAPACITY_STATE.actualCapacityBytes();
    }

    static synchronized long getPendingCacheCapacityBytes() {
        refreshPendingCacheCapacity();
        return CACHE_CAPACITY_STATE.pendingCapacityBytes();
    }

    static synchronized ExoCacheWritePolicy.Decision getCacheWriteDecision() {
        DiskCacheCapacityPolicy.Decision capacity = refreshPendingCacheCapacity();
        long actualCapacityBytes = cache == null ? 0 : CACHE_CAPACITY_STATE.actualCapacityBytes();
        return ExoCacheWritePolicy.resolve(capacity, actualCapacityBytes);
    }

    private static DiskCacheCapacityPolicy.Decision refreshPendingCacheCapacity() {
        File dir = Path.exoCache();
        long existingCacheBytes = cache == null ? FileUtil.getDirectorySize(dir) : cache.getCacheSpace();
        DiskCacheCapacityPolicy.Decision decision = resolveCapacity(dir, existingCacheBytes);
        if (isReliable(decision)) CACHE_CAPACITY_STATE.report(decision.effectiveCapacityBytes());
        return decision;
    }

    private static boolean isReliable(DiskCacheCapacityPolicy.Decision decision) {
        return decision.state() != DiskCacheCapacityPolicy.State.UNAVAILABLE;
    }

    private static void rebuildCacheLocked(String reason) {
        if (!releaseCacheLocked(reason)) return;
        try {
            getCache();
        } catch (RuntimeException e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-cache", "rebuild-failed reason=%s error=%s", reason, e.getClass().getSimpleName());
        }
    }

    private static boolean releaseCacheLocked(String reason) {
        if (cache == null) return false;
        Cache releasing = cache;
        long actual = CACHE_CAPACITY_STATE.actualCapacityBytes();
        long pending = CACHE_CAPACITY_STATE.pendingCapacityBytes();
        try {
            releasing.release();
            cache = null;
            CACHE_CAPACITY_STATE.recordReleased();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-cache", "released reason=%s actualCapacityBytes=%d pendingCapacityBytes=%d activeSessions=%d", reason, actual, pending, CACHE_CAPACITY_STATE.activeSessions());
            return true;
        } catch (RuntimeException e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-cache", "release-failed reason=%s error=%s activeSessions=%d", reason, e.getClass().getSimpleName(), CACHE_CAPACITY_STATE.activeSessions());
            return false;
        }
    }

    static boolean isConcatenatingUrl(String url) {
        return url != null && url.contains(CONCAT_SOURCE_SEPARATOR) && url.contains(CONCAT_DURATION_SEPARATOR);
    }

    @NonNull
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(@NonNull DrmSessionManagerProvider drmSessionManagerProvider) {
        return this;
    }

    @NonNull
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(@NonNull LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        return this;
    }

    @NonNull
    @Override
    public @C.ContentType int[] getSupportedTypes() {
        return defaultMediaSourceFactory.getSupportedTypes();
    }

    /**
     * 环34 复核订正 B2：海阔分离音轨合流装配——MediaItem extras 带
     * {@link com.fongmi.android.tv.player.engine.HkPlayCompanion#EXTRA_AUDIO_URLS}
     * （只由海阔面经 PlaySpec.hkAudioUrls 写入）时，主源照旧由
     * createMainMediaSource 装配，每条伴随址以同请求头/同装配链造源后经
     * MergingMediaSource 与主源合流（官 VideoPlayerManager.setAudioUrls 的
     * 分离音轨随主址合流语义）；extras 无此键（其他源恒如此）时直接回主源，
     * 与旧形一字不变。伴随 MediaItem 的 extras 摘掉该键防递归合流。
     */
    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
        java.util.ArrayList<String> companion = mediaItem.requestMetadata != null
                && mediaItem.requestMetadata.extras != null
                ? mediaItem.requestMetadata.extras.getStringArrayList(
                        com.fongmi.android.tv.player.engine.HkPlayCompanion.EXTRA_AUDIO_URLS)
                : null;
        List<String> audioUrls = com.fongmi.android.tv.player.engine.HkPlayCompanion.companionAudioUrls(companion);
        MediaSource main = createMainMediaSource(mediaItem);
        if (audioUrls.isEmpty()) return main;
        MediaSource[] sources = new MediaSource[audioUrls.size() + 1];
        sources[0] = main;
        for (int i = 0; i < audioUrls.size(); i++) {
            android.os.Bundle extras = new android.os.Bundle(mediaItem.requestMetadata.extras);
            extras.remove(com.fongmi.android.tv.player.engine.HkPlayCompanion.EXTRA_AUDIO_URLS);
            MediaItem audioItem = mediaItem.buildUpon()
                    .setUri(UrlUtil.uri(audioUrls.get(i)))
                    .setRequestMetadata(new MediaItem.RequestMetadata.Builder()
                            .setMediaUri(UrlUtil.uri(audioUrls.get(i)))
                            .setExtras(extras)
                            .build())
                    .build();
            sources[i + 1] = createMainMediaSource(audioItem);
        }
        return new MergingMediaSource(sources);
    }

    @NonNull
    private MediaSource createMainMediaSource(@NonNull MediaItem mediaItem) {
        String url = mediaItem.requestMetadata.mediaUri != null ? mediaItem.requestMetadata.mediaUri.toString() : "";
        boolean localProxy = isLocalProxyUrl(url);
        applyHeaders(localProxy ? getProxyHttpDataSourceFactory() : getHttpDataSourceFactory(), ExoUtil.extractHeaders(mediaItem), url);
        AssFontSet fonts = assSession == null ? null : assSession.beginMediaFonts();
        if (isConcatenatingUrl(url)) return createConcatenatingMediaSource(mediaItem, url);
        if (localProxy) {
            // 本地代理走带连接/读取超时的独立链路：代理连上无响应时抛错给播放器，不再无限 BUFFERING。
            // 提取器分支与主链路保持一致（诊断桥/字幕字体），非代理 URL 走原链路行为不变。
            DataSource.Factory dsFactory = getProxyDataSourceFactory();
            if (fonts != null || mediaItem.localConfiguration != null && mediaItem.localConfiguration.tag instanceof com.fongmi.android.tv.player.PlaybackDiagnosticCollector.Context) {
                return new DefaultMediaSourceFactory(dsFactory, Media3DiagnosticBridge.extractors(buildExtractorsFactory(fonts), mediaItem))
                        .setLoadOnlySelectedTracks(PlaybackPerformanceSetting.isLoadOnlySelectedTracksEnabled())
                        .createMediaSource(mediaItem);
            }
            return new DefaultMediaSourceFactory(dsFactory, getExtractorsFactory())
                    .setLoadOnlySelectedTracks(PlaybackPerformanceSetting.isLoadOnlySelectedTracksEnabled())
                    .createMediaSource(mediaItem);
        }
        if (fonts != null || mediaItem.localConfiguration != null && mediaItem.localConfiguration.tag instanceof com.fongmi.android.tv.player.PlaybackDiagnosticCollector.Context) {
            // Bind the collection to this source's extractors. Old loaders cannot populate a new video.
            // Container ASS needs its attachments even when no external subtitle was configured.
            return new DefaultMediaSourceFactory(getDataSourceFactory(), Media3DiagnosticBridge.extractors(buildExtractorsFactory(fonts), mediaItem))
                    .setLoadOnlySelectedTracks(PlaybackPerformanceSetting.isLoadOnlySelectedTracksEnabled())
                    .createMediaSource(mediaItem);
        }
        else return defaultMediaSourceFactory.createMediaSource(mediaItem);
    }

    private MediaSource createConcatenatingMediaSource(MediaItem mediaItem, String url) {
        ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder();
        DefaultMediaSourceFactory sourceFactory = new DefaultMediaSourceFactory(getDataSourceFactory(),
                Media3DiagnosticBridge.extractors(getExtractorsFactory(), mediaItem))
                .setLoadOnlySelectedTracks(PlaybackPerformanceSetting.isLoadOnlySelectedTracksEnabled());
        for (String split : url.split(CONCAT_SOURCE_SEPARATOR_REGEX)) {
            String[] info = split.split(CONCAT_DURATION_SEPARATOR_REGEX);
            if (info.length >= 2) {
                long duration;
                try {
                    duration = Long.parseLong(info[1].trim());
                } catch (NumberFormatException e) {
                    // 时长段畸形（非数字/溢出）与无时长段（info.length < 2）同口径：整段跳过，
                    // 不许 NumberFormatException 打穿 createMediaSource。
                    continue;
                }
                builder.add(sourceFactory.createMediaSource(mediaItem.buildUpon().setUri(UrlUtil.uri(info[0])).build()), duration);
            }
        }
        return builder.build();
    }

    private ExtractorsFactory getExtractorsFactory() {
        if (extractorsFactory == null) extractorsFactory = buildExtractorsFactory(null);
        return extractorsFactory;
    }

    private ExtractorsFactory buildExtractorsFactory(@Nullable AssFontSet fonts) {
            ExtractorsFactory defaults = new DefaultExtractorsFactory()
                    .setTsExtractorFlags(FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                    .setTsExtractorTimestampSearchBytes(
                            TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 10);
            ExtractorsFactory withApe = new ExtractorsFactory() {
                @Override
                public androidx.media3.extractor.Extractor[] createExtractors() {
                    return prependApe(defaults.createExtractors());
                }

                @Override
                public androidx.media3.extractor.Extractor[] createExtractors(
                        Uri uri, Map<String, List<String>> responseHeaders) {
                    return prependApe(defaults.createExtractors(uri, responseHeaders));
                }
            };
            return new DolbyVisionP81ExtractorsFactory(withApe, dolbyVisionPlaybackState, fonts);
    }

    private static androidx.media3.extractor.Extractor[] prependApe(
            androidx.media3.extractor.Extractor[] defaults) {
        androidx.media3.extractor.Extractor[] extractors =
                new androidx.media3.extractor.Extractor[defaults.length + 1];
        extractors[0] = new ApeExtractor();
        System.arraycopy(defaults, 0, extractors, 1, defaults.length);
        return extractors;
    }

    private DataSource.Factory getDataSourceFactory() {
        if (dataSourceFactory == null) {
            // Content-sniffed completion of the manifest split: a playlist
            // whose address carries no URI indicator is learned by its
            // "#EXTM3U" head the first time it flows through the byte
            // layer — from then on its URI bypasses the cache, and any
            // frozen entry it left in the cache is evicted on the spot.
            PlaybackBytePositionDataSource.setManifestObserver(uri -> {
                ManifestBypassDataSource.noteManifestUri(uri);
                try {
                    getCache().removeResource(uri);
                } catch (RuntimeException ignored) {
                    // Best-effort: the registry alone already reroutes
                    // every later load of this URI around the cache.
                }
            });
            // Media loads keep the write-through disk cache; manifest loads
            // (live playlists must advance between reloads) take a parallel
            // chain without the cache. See ManifestBypassDataSource.
            DataSource.Factory mediaChain = new PriorityTaskDataSource.Factory(
                    new PlaybackBytePositionDataSource.Factory(
                            getCacheDataSource(new DefaultDataSource.Factory(App.get(), getHttpDataSourceFactory()))),
                    PLAYBACK_PRIORITY_MANAGER, C.PRIORITY_PLAYBACK, false);
            DataSource.Factory manifestChain = new PriorityTaskDataSource.Factory(
                    new PlaybackBytePositionDataSource.Factory(
                            new HttpEofRecoveryDataSource.Factory(
                                    new DefaultDataSource.Factory(App.get(), getHttpDataSourceFactory()))),
                    PLAYBACK_PRIORITY_MANAGER, C.PRIORITY_PLAYBACK, false);
            dataSourceFactory = new ManifestBypassDataSource.Factory(mediaChain, manifestChain);
        }
        return dataSourceFactory;
    }

    private CacheDataSource.Factory getCacheDataSource(DataSource.Factory upstreamFactory) {
        // Write-through: played bytes land in the shared SimpleCache (same cache
        // PreCache writes to, LRU-evicted at the managed capacity), so when a seek
        // resets the stream and re-requests segments, they are served from disk
        // instead of being re-downloaded from the network.
        return new CacheDataSource.Factory()
                .setCache(getCache())
                .setUpstreamDataSourceFactory(new HttpEofRecoveryDataSource.Factory(upstreamFactory))
                .setEventListener(PlaybackCacheMetrics.listener())
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    private OkHttpDataSource.Factory getHttpDataSourceFactory() {
        if (httpDataSourceFactory == null) httpDataSourceFactory = new OkHttpDataSource.Factory(OkHttp.player());
        return httpDataSourceFactory;
    }

    /**
     * 是否海阔本地代理 URL（startProxyServer 返回的 127.0.0.1 回环）。
     * 严格按 host 等于 127.0.0.1 判定，非代理 URL 绝不进超时链路。
     */
    static boolean isLocalProxyUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            return "127.0.0.1".equals(Uri.parse(url).getHost());
        } catch (Exception e) {
            return false;
        }
    }

    /** 代理专用 OkHttp 链路：继承播放器 client 配置，只改连接/读取超时。 */
    private synchronized OkHttpDataSource.Factory getProxyHttpDataSourceFactory() {
        if (proxyHttpDataSourceFactory == null) {
            proxyHttpDataSourceFactory = new OkHttpDataSource.Factory(OkHttp.player().newBuilder()
                    .connectTimeout(PROXY_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(PROXY_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build());
        }
        return proxyHttpDataSourceFactory;
    }

    /** 代理专用上游链路：与主链路同结构（DefaultDataSource→EOF恢复→优先级），只是 HTTP 层带超时。 */
    private synchronized DataSource.Factory getProxyDataSourceFactory() {
        if (proxyDataSourceFactory == null) {
            DataSource.Factory upstream = new DefaultDataSource.Factory(App.get(), getProxyHttpDataSourceFactory());
            DataSource.Factory recovered = new HttpEofRecoveryDataSource.Factory(upstream);
            proxyDataSourceFactory = new PriorityTaskDataSource.Factory(recovered, PLAYBACK_PRIORITY_MANAGER, C.PRIORITY_PLAYBACK, false);
        }
        return proxyDataSourceFactory;
    }

    private static void applyHeaders(OkHttpDataSource.Factory factory, Map<String, String> headers, String url) {
        Map<String, String> sanitized = sanitizeHeaders(headers, url);
        String userAgent = removeUserAgentHeader(sanitized);
        factory.setUserAgent(userAgent).setDefaultRequestProperties(sanitized);
    }

    static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        return sanitizeHeaders(headers, "");
    }

    static Map<String, String> sanitizeHeaders(Map<String, String> headers, String url) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        if (headers == null || headers.isEmpty()) return sanitized;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String key = entry.getKey().trim();
            if (key.isEmpty()) continue;
            sanitized.put(key, entry.getValue().trim());
        }
        // 仅 115 CDN（*.115cdn.net）特殊处理：其直链对 Accept-Encoding 敏感，
        // OkHttp 默认的 gzip 会被拒流（"网络读取失败"），必须发 identity。
        // 其他域名保持与默影视一致（OkHttp 默认行为），不注入。
        if (is115Cdn(url)) {
            boolean hasAcceptEncoding = false;
            for (String key : sanitized.keySet()) {
                if ("Accept-Encoding".equalsIgnoreCase(key)) {
                    hasAcceptEncoding = true;
                    break;
                }
            }
            if (!hasAcceptEncoding) sanitized.put("Accept-Encoding", "identity");
        }
        return sanitized;
    }

    private static boolean is115Cdn(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            String host = Uri.parse(url).getHost();
            return host != null && host.toLowerCase().endsWith("115cdn.net");
        } catch (Exception e) {
            return false;
        }
    }

    static String removeUserAgentHeader(Map<String, String> headers) {
        String userAgent = null;
        Iterator<Map.Entry<String, String>> iterator = headers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (!"User-Agent".equalsIgnoreCase(entry.getKey())) continue;
            String value = entry.getValue().trim();
            if (!value.isEmpty()) userAgent = value;
            iterator.remove();
        }
        return userAgent;
    }
}
