package com.fongmi.android.tv.ui.activity;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.MediaItem;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Url;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.exo.NextEpisodeDataPrefetcher;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;

import java.util.Collections;
import java.util.Map;

/**
 * 下一集预加载：setPlayer 成功后，后台提前解析下一集的播放 URL。
 * 切集时若命中（key+flag+episodeUrl 一致且未过期），跳过解析链直接播。
 * 只做 URL 预解析，不做双 Player 实例（省解码器资源）。
 *
 * 数据预下（无感切集）：预解析拿到真地址后，再把下一集的数据提前下进播放缓存
 * （NextEpisodeDataPrefetcher，与起播同一份 SimpleCache、同 URI 建键）。
 * 短剧/短集整集预下，长集预下开头 120 秒；当前集播过半、过八成各复查一次，没跟上就补。
 * 守卫与预解析一致：只走 Exo（调用方已挡 MPV）、雷磁类在调用方与消费侧双挡，
 * 这里再挡解析型/DRM/本地代理地址。
 */
public class NextEpisodePreloader {

    /** 当前集播放探针：数据预下的闸门与过半复查都靠它，取不到值返回 -1/false。 */
    public interface PlaybackProbe {
        long positionMs();

        long durationMs();

        int bufferedPercent();

        boolean loading();
    }

    private static final long TTL_MS = 10 * 60 * 1000; // 10 分钟
    private static final long POLL_MS = 5000;
    /** 当前集时长不超过 8 分钟视作短集，下一集按整集预下。 */
    private static final long SHORT_FORM_MAX_MS = 8 * 60 * 1000;
    private static final int MAX_TOP_UPS = 2;

    private Result mPreloaded;
    private String mSpec; // key|flag|episodeUrl
    private long mTimestamp;

    private final NextEpisodeDataPrefetcher mDataPrefetcher = new NextEpisodeDataPrefetcher();
    private final Handler mPollHandler = new Handler(Looper.getMainLooper());
    private final Runnable mPoll = this::pollDataPrefetch;
    private volatile PlaybackProbe mProbe;
    private volatile boolean mShortFormHint;
    private volatile int mQualityHint = -1;
    private volatile String mDataSpec;
    private volatile MediaItem mDataItem;
    private volatile Map<String, String> mDataHeaders;
    private volatile String mDataUrl;
    private volatile boolean mDataWhole;
    private volatile boolean mDataArmed;
    private volatile boolean mDataStarted;
    private volatile long mDataArmTimeMs;
    private volatile long mDataBytes;
    private volatile int mTopUpsUsed;
    private volatile boolean mTopUp50Done;
    private volatile boolean mTopUp80Done;

    /** 由 VideoActivity 在触发预加载前喂入：播放探针、短剧标记、当前画质档位（多线源预下对准起播会选的那条线）。 */
    public void configureDataPrefetch(PlaybackProbe probe, boolean shortForm, int qualityPosition) {
        mProbe = probe;
        mShortFormHint = shortForm;
        mQualityHint = qualityPosition;
    }

    /**
     * 后台预解析指定集。episode 为空或 URL 为空时清空。
     */
    public void preload(String key, String flag, Episode episode) {
        if (episode == null || episode.getUrl() == null || episode.getUrl().isEmpty()) {
            clear();
            return;
        }
        String spec = buildSpec(key, flag, episode.getUrl());
        resetDataPrefetch();
        synchronized (this) {
            mSpec = spec;
            mPreloaded = null;
            mTimestamp = System.currentTimeMillis();
        }
        Task.execute(() -> {
            try {
                Result result = SiteApi.playerContentParseOnly(key, flag, episode.getUrl());
                boolean stored = false;
                synchronized (this) {
                    if (spec.equals(mSpec)) {
                        mPreloaded = result;
                        mTimestamp = System.currentTimeMillis();
                        stored = true;
                    }
                }
                if (stored) armDataPrefetch(spec, result);
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * 若预加载命中则返回 Result 并清空，未命中返回 null。
     */
    public synchronized Result consumeIfMatch(String key, String flag, String episodeUrl) {
        if (mSpec == null || mPreloaded == null) return null;
        if (!mSpec.equals(buildSpec(key, flag, episodeUrl))) return null;
        if (System.currentTimeMillis() - mTimestamp > TTL_MS) {
            clear();
            return null;
        }
        Result result = mPreloaded;
        logDataPrefetchHit(buildSpec(key, flag, episodeUrl));
        clear();
        return result;
    }

    public synchronized void clear() {
        mPreloaded = null;
        mSpec = null;
        mTimestamp = 0;
        resetDataPrefetch();
    }

    private static String buildSpec(String key, String flag, String url) {
        return key + "|" + flag + "|" + url;
    }

    // ===== 数据预下编排 =====

    /** 预解析落地后武装数据预下：过守卫、对准画质线、建与起播等价的 MediaItem，然后起轮询等闸门。 */
    private void armDataPrefetch(String spec, Result result) {
        if (result == null) return;
        if (!PreloadSetting.isPreload(PlayerSetting.EXO)) return;
        if (result.getDrm() != null) return;
        if (result.needParse()) return;
        Url url = result.getUrl();
        if (url == null) return;
        int original = url.getPosition();
        if (mQualityHint >= 0 && url.isMulti()) url.set(mQualityHint);
        String realUrl = result.getRealUrl();
        if (url.getPosition() != original) url.set(original);
        if (realUrl == null || realUrl.isEmpty()) return;
        if (!NextEpisodeDataPrefetcher.isEligibleUrl(realUrl)) return;
        Map<String, String> headers = result.getHeader();
        MediaItem item = new MediaItem.Builder()
                .setUri(realUrl)
                .setMimeType(result.getFormat())
                .setRequestMetadata(new MediaItem.RequestMetadata.Builder()
                        .setMediaUri(Uri.parse(realUrl))
                        .setExtras(PlayerHelper.toBundle(headers == null ? Collections.emptyMap() : headers))
                        .build())
                .build();
        boolean whole = mShortFormHint;
        if (!whole && mProbe != null) {
            long duration = safeDuration(mProbe);
            whole = duration > 0 && duration <= SHORT_FORM_MAX_MS;
        }
        mDataSpec = spec;
        mDataItem = item;
        mDataHeaders = headers;
        mDataUrl = realUrl;
        mDataWhole = whole;
        mDataBytes = 0;
        mTopUpsUsed = 0;
        mTopUp50Done = false;
        mTopUp80Done = false;
        mDataStarted = false;
        mDataArmed = true;
        mDataArmTimeMs = System.currentTimeMillis();
        SpiderDebug.log("video-flow", "next-episode data prefetch armed whole=%s", whole);
        mPollHandler.removeCallbacks(mPoll);
        mPollHandler.postDelayed(mPoll, 1000L);
    }

    private void pollDataPrefetch() {
        String spec = mDataSpec;
        if (spec == null || !mDataArmed || !spec.equals(mSpec)) return;
        NextEpisodeDataPrefetcher.State state = mDataPrefetcher.getState();
        mDataBytes = Math.max(mDataBytes, mDataPrefetcher.getBytesAdded());
        if (state == NextEpisodeDataPrefetcher.State.COMPLETED
                || state == NextEpisodeDataPrefetcher.State.CAPPED) return;
        PlaybackProbe probe = mProbe;
        long position = probe == null ? -1 : safePosition(probe);
        long duration = probe == null ? -1 : safeDuration(probe);
        if (!mDataStarted) {
            if (!gatePassed(probe, position, duration)) {
                mPollHandler.postDelayed(mPoll, POLL_MS);
                return;
            }
            startDataPrefetch();
            mPollHandler.postDelayed(mPoll, POLL_MS);
            return;
        }
        if (duration > 0 && position >= 0) {
            if (!mTopUp50Done && position * 2 >= duration) {
                mTopUp50Done = true;
                topUpDataPrefetch("50%");
            }
            if (!mTopUp80Done && position * 5 >= duration * 4) {
                mTopUp80Done = true;
                topUpDataPrefetch("80%");
            }
        }
        if (state == NextEpisodeDataPrefetcher.State.FAILED && mTopUpsUsed >= MAX_TOP_UPS) return;
        mPollHandler.postDelayed(mPoll, POLL_MS);
    }

    /**
     * 开下闸门：当前集播稳了才下，不跟起播抢带宽。
     * 上游工厂本身是 PLAYBACK_PRELOAD 优先级、播放忙时自动让路，这里再加一层粗闸：
     * 播过 20 秒无条件放行；播过 5 秒且不在缓冲放行；缓冲已攒够四分之一且播过 3 秒放行；
     * 武装后满 30 秒兜底放行（短集可能还没播稳就临近结尾，晚下总比不下强）。
     */
    private boolean gatePassed(PlaybackProbe probe, long position, long duration) {
        if (System.currentTimeMillis() - mDataArmTimeMs >= 30000) return true;
        if (probe == null) return true;
        if (position >= 20000) return true;
        if (position >= 5000 && !safeLoading(probe)) return true;
        return duration > 0 && position >= 3000 && safeBuffered(probe) >= 25;
    }

    private void startDataPrefetch() {
        MediaItem item = mDataItem;
        if (item == null) return;
        mDataStarted = true;
        boolean ok = mDataPrefetcher.start(mDataUrl, mDataHeaders, item, mDataWhole, (state, bytes) -> {
            mDataBytes = Math.max(mDataBytes, bytes);
            if (state == NextEpisodeDataPrefetcher.State.COMPLETED)
                SpiderDebug.log("video-flow", "next-episode data prefetch completed bytes=%d", bytes);
            else if (state == NextEpisodeDataPrefetcher.State.CAPPED)
                SpiderDebug.log("video-flow", "next-episode data prefetch capped bytes=%d", bytes);
            else if (state == NextEpisodeDataPrefetcher.State.FAILED)
                SpiderDebug.log("video-flow", "next-episode data prefetch failed");
        });
        SpiderDebug.log("video-flow", "next-episode data prefetch start whole=%s ok=%s", mDataWhole, ok);
        if (!ok) mDataStarted = false; // 启动即失败：留着让过半复查走 topUp 重建
    }

    private void topUpDataPrefetch(String at) {
        if (mTopUpsUsed >= MAX_TOP_UPS) return;
        if (mDataPrefetcher.topUp()) {
            mTopUpsUsed++;
            mDataStarted = true;
            SpiderDebug.log("video-flow", "next-episode data prefetch top-up at=%s used=%d", at, mTopUpsUsed);
        }
    }

    private void logDataPrefetchHit(String spec) {
        if (spec == null || !spec.equals(mDataSpec)) return;
        NextEpisodeDataPrefetcher.State state = mDataPrefetcher.getState();
        long bytes = Math.max(mDataBytes, mDataPrefetcher.getBytesAdded());
        if (state == NextEpisodeDataPrefetcher.State.COMPLETED
                || state == NextEpisodeDataPrefetcher.State.CAPPED || bytes > 0) {
            SpiderDebug.log("video-flow", "next-episode data prefetch hit state=%s bytes=%d", state, bytes);
        }
    }

    private void resetDataPrefetch() {
        mPollHandler.removeCallbacks(mPoll);
        mDataPrefetcher.cancel();
        mDataSpec = null;
        mDataItem = null;
        mDataHeaders = null;
        mDataUrl = null;
        mDataWhole = false;
        mDataArmed = false;
        mDataStarted = false;
        mDataBytes = 0;
        mTopUpsUsed = 0;
        mTopUp50Done = false;
        mTopUp80Done = false;
    }

    private static long safePosition(PlaybackProbe probe) {
        try {
            return probe.positionMs();
        } catch (Throwable e) {
            return -1;
        }
    }

    private static long safeDuration(PlaybackProbe probe) {
        try {
            return probe.durationMs();
        } catch (Throwable e) {
            return -1;
        }
    }

    private static int safeBuffered(PlaybackProbe probe) {
        try {
            return probe.bufferedPercent();
        } catch (Throwable e) {
            return -1;
        }
    }

    private static boolean safeLoading(PlaybackProbe probe) {
        try {
            return probe.loading();
        } catch (Throwable e) {
            return false;
        }
    }
}
