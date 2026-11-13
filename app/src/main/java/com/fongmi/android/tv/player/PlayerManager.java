package com.fongmi.android.tv.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.effect.ColorLut;
import androidx.media3.ui.danmaku.DanmakuConfig;
import androidx.media3.ui.danmaku.DanmakuController;
import androidx.media3.mpvplayer.MpvPlayer;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.codec.CodecCapabilityInspector;
import com.fongmi.android.tv.player.engine.ExoPlayerEngine;
import com.fongmi.android.tv.player.exo.ass.ExoAssSession;
import com.fongmi.android.tv.player.engine.MpvPlayerEngine;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.engine.PlayerCacheState;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.exo.ExoDecoderResourceRecoveryLimiter;
import com.fongmi.android.tv.player.exo.ExoNetworkGuardBufferPolicy;
import com.fongmi.android.tv.player.exo.ExoNetworkGuardController;
import com.fongmi.android.tv.player.exo.ExoNetworkGuardEligibility;
import com.fongmi.android.tv.player.exo.ExoRtspLiveLagController;
import com.fongmi.android.tv.player.exo.ExoRtspLiveLagPolicy;
import com.fongmi.android.tv.player.exo.ForwardBufferTrend;
import com.fongmi.android.tv.player.exo.PlaybackAnalyticsListener;
import com.fongmi.android.tv.player.danmaku.DanmakuUrlPolicy;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuBatcher;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuBuffer;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuMessage;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuMetrics;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuParser;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuWebSocketSession;
import com.fongmi.android.tv.player.danmaku.live.LiveDanmakuCoordinator;
import com.fongmi.android.tv.player.danmaku.live.LiveDanmakuStamp;
import com.fongmi.android.tv.player.danmaku.live.LiveRoomResolver;
import com.fongmi.android.tv.player.lut.DynamicLutEffect;
import com.fongmi.android.tv.player.lut.LutEffectFactory;
import com.fongmi.android.tv.player.lut.LutEligibility;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.player.lut.MpvLutShader;
import com.fongmi.android.tv.player.lut.MpvLutShaderFactory;
import com.fongmi.android.tv.player.mpv.MpvAutoController;
import com.fongmi.android.tv.player.mpv.MpvAutoControlPolicy;
import com.fongmi.android.tv.player.mpv.MpvAutoOutputPolicy;
import com.fongmi.android.tv.player.mpv.MpvAutoRenderPolicy;
import com.fongmi.android.tv.player.mpv.MpvBackCacheController;
import com.fongmi.android.tv.player.mpv.MpvBackCachePolicy;
import com.fongmi.android.tv.player.mpv.MpvCacheTargetCoordinator;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.fongmi.android.tv.player.mpv.MpvForwardCacheController;
import com.fongmi.android.tv.player.mpv.MpvForwardCachePolicy;
import com.fongmi.android.tv.player.mpv.MpvHlsVariantController;
import com.fongmi.android.tv.player.mpv.MpvHlsVariantPolicy;
import com.fongmi.android.tv.player.mpv.MpvPreloadController;
import com.fongmi.android.tv.player.mpv.MpvPreloadPolicy;
import com.fongmi.android.tv.player.mpv.MpvResourcePressureController;
import com.fongmi.android.tv.player.mpv.MpvVulkanBackendPolicy;
import com.fongmi.android.tv.player.mpv.MpvResourcePressurePolicy;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.ExoPerformanceSetting;
import com.fongmi.android.tv.setting.MpvPerformanceSetting;
import com.fongmi.android.tv.setting.PlaybackExperimentSetting;
import com.fongmi.android.tv.setting.PlaybackLightweightAssessmentSetting;
import com.fongmi.android.tv.setting.PlaybackPerformanceCatalog;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlaybackProfileAbSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.SubtitleSetting;
import com.fongmi.android.tv.utils.LocalProxyDebug;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;

import is.xyz.mpv.MPVLib;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerManager implements ParseCallback {

    public static final String RELOAD_LUT_WARMUP = "__webhtv_lut_warmup_reload__";
    private static final String NETWORK_GUARD_DEBUG = "EXO_NETWORK_GUARD";

    private static void logNetworkGuard(String message) {
        if (SpiderDebug.isEnabled()) Log.d(NETWORK_GUARD_DEBUG, message);
    }

    private static final long LOCAL_PROXY_READY_TIMEOUT_MS = 5000;
    private static final long LOCAL_PROXY_RETRY_DELAY_MS = 1000;
    private static final long HARD_DECODE_SWITCH_RETRY_DELAY_MS = 1200;
    private static final long EXO_TUNNELING_RETRY_DELAY_MS = 250;
    private static final long EXO_DECODER_RUNTIME_RETRY_DELAY_MS = 1200;
    private static final long EXO_DECODER_RESOURCE_RECOVERY_DELAY_MS = 500;
    private static final long EXO_DV7_FIRST_FRAME_FALLBACK_DELAY_MS = 1200;
    private static final long MPV_AUTO_OUTPUT_PROBE_INTERVAL_MS = 250;
    private static final int LOCAL_PROXY_MAX_RETRY = 2;
    private static final int MPV_AUTO_OUTPUT_PROBE_MAX_ATTEMPTS = 20;
    private static final int LUT_WARMUP_RECOVERED_ERROR_REFRESH_THRESHOLD = 3;
    private static final long DANMAKU_FORCE_RELOAD_DEBOUNCE_MS = 10000;
    private static final long LIVE_DANMAKU_METRICS_INTERVAL_MS = 15000L;
    // 平台路弹幕在「已连上、播放尚未激活」窗口内的待喂上限：起播头一两秒 isPlaying
    // 未翻 true 时先攒着、激活后补喂，不再整批静默丢（douyu-danmaku-stops-trace.md
    // 第四节次要项）。大房聊天峰值约百条/秒量级，256 条足够覆盖该窗最坏积压；
    // 超限丢最旧（直播弹幕时效性强、旧条目补喂价值最低，且补喂还要过 buffer 自身
    // TTL/STALE 一道），内存上界即 256 个小对象。
    private static final int LIVE_DANMAKU_PENDING_CAP = 256;
    private static final long PLAYBACK_TELEMETRY_INTERVAL_MS = 5000L;
    private static final long MPV_FRAME_TIMING_LOG_INTERVAL_MS = 5000L;
    private static final long LUT_PREVIEW_FRAME_INTERVAL_MS = 16L;
    private static final float[] SPEED_PRESETS = new float[]{0.5f, 0.75f, 1f, 1.2f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 5f};
    private static final DecimalFormat SPEED_FORMAT = new DecimalFormat("0.##x");

    private final Runnable runnable;
    private final Runnable liveDanmakuMetricsRunnable;
    private final Runnable networkProtectionRunnable;
    private final Runnable playbackTelemetryRunnable;
    private final Callback callback;
    private final DynamicLutEffect dynamicLutEffect;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private final BroadcastReceiver noisyReceiver;
    private final PlaybackBufferingTracker playbackBufferingTracker;
    private final PlaybackTrace playbackTrace;
    private final PlaybackAutoContextStore playbackAutoContextStore;
    private final PlaybackTelemetryCoordinator playbackTelemetryCoordinator;
    private final PlaybackProfileAbCoordinator playbackProfileAbCoordinator;
    private final PlaybackProfileAbCoordinator
            playbackLightweightAssessmentCoordinator;
    private final PlaybackMediaFactsCoordinator playbackMediaFactsCoordinator;
    private final ExoDecoderResourceRecoveryLimiter
            exoDecoderResourceRecoveryLimiter;
    private final ExoNetworkGuardController networkProtectionController;
    private final ExoRtspLiveLagController rtspLiveLagController;
    private final MpvAutoController mpvAutoController;
    private final MpvForwardCacheController mpvForwardCacheController;
    private final MpvBackCacheController mpvBackCacheController;
    private final MpvCacheTargetCoordinator mpvCacheTargetCoordinator;
    private final MpvHlsVariantController mpvHlsVariantController;
    private final MpvResourcePressureController mpvResourcePressureController;
    private final MpvPreloadController mpvPreloadController;
    private final PlaybackMemoryCoordinator.Registration mpvResourceMemoryRegistration;
    private final PlaybackSystemConditionCoordinator.Registration mpvResourceSystemRegistration;
    private final PlaybackExperimentCoordinator playbackExperimentCoordinator;
    private final PlaybackExperimentCoordinator.Registration playbackExperimentRegistration;
    private final ForwardBufferTrend networkProtectionTrend;
    private final LiveDanmakuBatcher liveDanmakuBatcher;
    private final LiveDanmakuBuffer liveDanmakuBuffer;
    private final LiveDanmakuMetrics liveDanmakuMetrics;
    private DanmakuController danmakuController;
    private LiveDanmakuWebSocketSession liveDanmakuSession;
    private final LiveDanmakuCoordinator liveDanmakuCoordinator;
    private PlayerEngine engine;
    private VideoSize videoSize;
    private ParseJob parseJob;
    private PlaySpec spec;
    private Player player;
    private String currentDanmakuUrl;
    private String currentDanmakuKey;
    private String loadingDanmakuKey;
    private String lastAutoDanmakuKey;
    private String lastLoggedRouteTraceId = PlaybackTrace.NONE;
    private ExoDecoderResourceRecovery pendingExoDecoderResourceRecovery;
    private PlaybackAutoContext.SessionToken playbackAutoSession = PlaybackAutoContext.SessionToken.none();
    private long playbackTrackSequence;
    private long danmakuLoadStartedAtMs;
    private volatile long liveDanmakuGeneration;
    private volatile boolean liveDanmakuPlaybackActive;
    // 平台路未激活窗口的待喂缓冲：以本 list 对象为锁；pendingGeneration 是「武装代」
    // ——CONNECTING 时按代武装（与 buffer/batcher 的 reset 同点），只有武装代的消息
    // 才进缓冲，补喂/停连后解除武装。代不符一律不收，防串房。
    private final List<LiveDanmakuMessage> liveDanmakuPendingMessages = new ArrayList<>();
    private long liveDanmakuPendingGeneration = -1L;
    // 网关路未激活窗口的待喂缓冲：与平台路同口径（96513940），但缓冲 list 与武装代
    // 各自独立——网关消息只进本 list、按网关会话自己的代际武装。两路分发互斥
    // （选网关源即停 coordinator、平台路只在非网关源时启），缓冲不许互相串；
    // offer 的目标 buffer/batcher 是两路共用的同一份（同一时刻只有一路在跑）。
    private final List<LiveDanmakuMessage> liveDanmakuGatewayPendingMessages = new ArrayList<>();
    private long liveDanmakuGatewayPendingGeneration = -1L;
    private long pendingSwitchPositionMs = C.TIME_UNSET;
    private long pendingInitialStartPositionMs = C.TIME_UNSET;
    private float pendingSwitchSpeed = 1f;
    private boolean danmakuLoadInProgress;
    private boolean danmakuForeground = true;
    private boolean pendingSwitchRepeat;
    private boolean pendingSwitchRestore;
    private boolean audioFocusHeld;
    private boolean noisyReceiverRegistered;
    private boolean resumeOnAudioFocusGain;
    private Object audioFocusRequest;

    private boolean initTrack;
    private boolean videoEffectsActive;
    private boolean videoEffectsDirty;
    private boolean lutAppliedForItem;
    private boolean lutApplyInProgress;
    private boolean lutPipelineReadyForItem;
    private boolean lutPipelinePrepareInProgress;
    private boolean pendingLutPreview;
    private boolean waitingLutBeforePlay;
    private boolean playWhenReady = true;
    private boolean lutWarmupRecoveryActive;
    private boolean lutWarmupRefreshRequested;
    private boolean lutWarmupReloadPreviewPending;
    private boolean hardDecodeSwitchRetryArmed;
    private boolean lutAllowed = true;
    private boolean mpvAutoOutputEvaluated;
    private boolean mpvAutoOutputFrameReady;
    private boolean mpvAutoOutputEvaluationScheduled;
    private boolean mpvExplicitSubtitlePreference;
    private boolean mpvAutoGpuPinnedForSession;
    private boolean mpvAutoVulkanPinnedForItem;
    private boolean mpvAutoVulkanDisabledForItem;
    private boolean mpvSurfaceFallbackTried;
    private boolean mpvVulkanFallbackTried;
    private com.fongmi.android.tv.bean.Track lastSecondarySubtitleTrack;
    private boolean mpvHlsManagedReload;
    private boolean playbackForeground;
    private boolean exoDecoderResourceRecoveryInProgress;
    private int playerType;
    private int retry;
    private int localProxyRetry;
    private int prepareSeq;
    private int lutApplySeq;
    private int lutWarmupRecoveredErrors;
    private int lutWarmupRefreshAttempts;
    private boolean autoKernelSwitchTried;
    private int mpvOutputEvaluationSeq;
    private int mpvAutoOutputProbeAttempts;
    private float userPlaybackSpeed = 1f;
    private float networkProtectionSpeed = 1f;
    private float networkProtectionSupportedSpeed = 1f;
    private long networkProtectionMediaBitrate;
    private long lastMpvFrameTimingLogMs;
    private ExoNetworkGuardController.State networkProtectionState = ExoNetworkGuardController.State.NORMAL;
    private ExoNetworkGuardController.ProtectionTier networkProtectionTier = ExoNetworkGuardController.ProtectionTier.NONE;
    private String networkProtectionReason = "waiting";
    private PlaybackExperimentCoordinator.Token networkProtectionExperimentToken;

    public PlayerManager(Callback callback) {
        this.callback = callback;
        PlaybackExperimentSetting.ensureInitialized();
        this.playbackExperimentCoordinator =
                PlaybackExperimentCoordinator.process();
        this.playbackExperimentRegistration =
                playbackExperimentCoordinator.addListener(update ->
                        App.post(() -> onPlaybackExperimentPolicyChanged(update)));
        this.runnable = this::onPlaybackTimeout;
        this.liveDanmakuMetricsRunnable = () -> logLiveDanmakuMetrics("periodic", true);
        this.networkProtectionRunnable = this::evaluateNetworkProtection;
        this.playbackTelemetryRunnable = this::publishPlaybackTelemetryTick;
        this.playbackBufferingTracker = new PlaybackBufferingTracker();
        this.playbackTrace = new PlaybackTrace();
        this.playbackAutoContextStore = PlaybackAutoContextStore.process();
        this.playbackTelemetryCoordinator = PlaybackTelemetryCoordinator.process();
        this.playbackProfileAbCoordinator = PlaybackProfileAbCoordinator.process();
        this.playbackLightweightAssessmentCoordinator =
                PlaybackLightweightAssessmentSetting.coordinator();
        this.playbackMediaFactsCoordinator = new PlaybackMediaFactsCoordinator(playbackAutoContextStore);
        this.exoDecoderResourceRecoveryLimiter =
                new ExoDecoderResourceRecoveryLimiter();
        this.networkProtectionController = new ExoNetworkGuardController();
        this.rtspLiveLagController = new ExoRtspLiveLagController();
        this.mpvAutoController = new MpvAutoController();
        this.mpvForwardCacheController = new MpvForwardCacheController();
        this.mpvBackCacheController = new MpvBackCacheController();
        this.mpvCacheTargetCoordinator = new MpvCacheTargetCoordinator();
        this.mpvHlsVariantController = new MpvHlsVariantController();
        this.mpvResourcePressureController = new MpvResourcePressureController();
        this.mpvPreloadController = new MpvPreloadController();
        this.mpvResourceMemoryRegistration = PlaybackMemoryCoordinator.process().addListener(update ->
                App.post(() -> onMpvResourceMemoryUpdate(update)));
        this.mpvResourceSystemRegistration = PlaybackSystemConditionCoordinator.process().addListener(update ->
                App.post(() -> onMpvResourceSystemUpdate(update)));
        this.networkProtectionTrend = new ForwardBufferTrend();
        this.liveDanmakuBuffer = new LiveDanmakuBuffer();
        this.liveDanmakuMetrics = new LiveDanmakuMetrics();
        this.liveDanmakuBatcher = new LiveDanmakuBatcher(liveDanmakuBuffer, this::onLiveDanmakuBatch);
        this.liveDanmakuCoordinator = new LiveDanmakuCoordinator(new LiveDanmakuCoordinator.Host() {
            @Override
            public void onState(LiveDanmakuWebSocketSession.State state, long generation, String targetTag, int code, String detail) {
                onPlatformLiveState(state, generation, targetTag, code, detail);
            }

            @Override
            public void onMessages(long generation, List<LiveDanmakuMessage> messages) {
                onPlatformLiveMessages(generation, messages);
            }

            @Override
            public void onOnline(long generation, long online) {
                onPlatformLiveOnline(generation, online);
            }

            @Override
            public boolean isForeground() {
                return danmakuForeground;
            }
        });
        this.dynamicLutEffect = new DynamicLutEffect();
        this.audioFocusChangeListener = this::onNativeAudioFocusChanged;
        this.noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) return;
                onNativeAudioBecomingNoisy();
            }
        };
        this.playerType = PlayerSetting.getPlayer();
        this.engine = buildEngine(playerType, PlayerSetting.isVideoPrefer(playerType) ? PlayerEngine.HARD : PlayerEngine.SOFT);
        this.player = engine.getPlayer();
    }

    public void release() {
        if (player == null) return;
        com.fongmi.android.tv.api.HomePrewarm.playbackActive = false; // 首页预热恢复推进
        logStopAction("release");
        prepareSeq++;
        lutApplySeq++;
        prepareTerminalRelease();
        resetNetworkProtectionSession("release");
        clearExoDecoderResourceRecovery(true);
        player.removeListener(listener);
        App.removeCallbacks(runnable);
        App.removeCallbacks(networkProtectionRunnable);
        App.removeCallbacks(playbackTelemetryRunnable);
        mpvResourceMemoryRegistration.close();
        mpvResourceSystemRegistration.close();
        playbackExperimentRegistration.close();
        stopNativeAudioSession();
        clearDanmaku("release");
        releaseLiveDanmakuSession();
        liveDanmakuCoordinator.release();
        liveDanmakuBatcher.release();
        App.removeCallbacks(liveDanmakuMetricsRunnable);
        if (danmakuController != null) danmakuController.setListener(null);
        danmakuController = null;
        endPlaybackTelemetrySession("release");
        clearPlaybackAutoContext();
        mpvAutoGpuPinnedForSession = false;
        mpvAutoVulkanPinnedForItem = false;
        mpvAutoVulkanDisabledForItem = false;
        if (engine == null) return;
        if (engine instanceof ExoPlayerEngine exoEngine) exoEngine.setTunnelingWatchdogCallback(null);
        engine.release();
        engine = null;
        player = null;
        videoEffectsActive = false;
        videoEffectsDirty = false;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        lutWarmupReloadPreviewPending = false;
        clearLutWarmupRecovery();
        playbackBufferingTracker.reset();
        playbackTrace.clear();
        lastLoggedRouteTraceId = PlaybackTrace.NONE;
    }

    public void prepareTerminalRelease() {
        if (engine instanceof MpvPlayerEngine mpv) mpv.prepareTerminalRelease();
    }

    private boolean experimentAllowed(PlaybackExperimentPolicy.Action action) {
        return PlaybackExperimentSetting.isAllowed(action);
    }

    private void onPlaybackExperimentPolicyChanged(
            PlaybackExperimentCoordinator.Update update) {
        if (update == null) return;
        invalidatePlaybackProfileAssessments(
                PlaybackProfileAbCoordinator.InvalidationReason
                        .GENERATION_CHANGED);
        PlaybackExperimentPolicy.State policy =
                PlaybackExperimentSetting.getState();
        PlaybackTrace.log(
                "playback-experiment",
                playbackTrace.current(),
                "generation=%d change=%s strategy=%s frameAb=%s profileAb=%s action=invalidate-internal-experiments",
                update.generation(),
                update.change(),
                policy.strategyId(),
                policy.allows(PlaybackExperimentPolicy.Action
                        .EXO_FRAME_SCHEDULING_AB),
                policy.allows(PlaybackExperimentPolicy.Action
                        .SHARED_PROFILE_AB_VALIDATION));
    }

    private void onPlaybackTimeout() {
        if (retryLutWarmupByRefresh("timeout")) return;
        if (retryMpvDv7P81FirstFrameTimeout()) return;
        if (retryMpvDv7NativeFirstFrameTimeout()) return;
        if (retryMpvVulkanBackendTimeout()) return;
        if (retryMpvAutoVulkanToOpenGl("auto-vulkan-first-frame-timeout")) return;
        if (retryExoDv7FirstFrameTimeout()) return;
        callback.onError(ResUtil.getString(R.string.error_play_timeout));
    }

    private void resetLutRuntimeState(String reason, boolean clearEngineEffects) {
        lutApplySeq++;
        if (clearEngineEffects && engine != null && engine.supportsNativeLut()) {
            safeSetNativeLut(null, reason + "_reset");
        } else if (clearEngineEffects && engine != null && videoEffectsActive) {
            try {
                engine.setVideoEffects(Collections.emptyList());
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "clear effects before reset reason=%s", reason);
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "clear effects before reset failed reason=%s error=%s", reason, causeChain(e));
            }
        }
        dynamicLutEffect.clear();
        videoEffectsActive = false;
        videoEffectsDirty = false;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        lutWarmupReloadPreviewPending = false;
        clearLutWarmupRecovery();
    }

    public Player getPlayer() {
        return player;
    }

    public ExoAssSession getAssSession() {
        return engine instanceof ExoPlayerEngine exo ? exo.getAssSession() : null;
    }

    public com.fongmi.android.tv.player.exo.subtitle.ExoSubtitleSession getSubtitleSession() {
        return engine instanceof ExoPlayerEngine exo ? exo.getSubtitleSession() : null;
    }

    public Tracks getCurrentTracks() {
        return engine == null ? Tracks.EMPTY : engine.getCurrentTracks();
    }

    public List<MediaEdition> getCurrentMediaEditions() {
        return engine == null ? Collections.emptyList() : engine.getCurrentMediaEditions();
    }

    public MediaItem getCurrentMediaItem() {
        return isReleased() ? null : player.getCurrentMediaItem();
    }

    public String getPlaybackTraceId() {
        return playbackTrace.current();
    }

    public PlaybackAutoContext getPlaybackAutoContext() {
        return playbackAutoContextStore.snapshot();
    }

    public void publishPlaybackRenderTarget(PlaybackAutoContext.RenderTarget renderTarget) {
        playbackMediaFactsCoordinator.publishRenderTarget(
                playbackAutoSession, renderTarget, SystemClock.elapsedRealtime());
    }

    public void publishPlaybackDisplayFacts(
            PlaybackAutoContext.DisplayMode currentMode,
            PlaybackAutoContext.DisplayMode requestedMode) {
        playbackMediaFactsCoordinator.publishDisplayFacts(
                playbackAutoSession, currentMode, requestedMode, SystemClock.elapsedRealtime());
    }

    public void publishPlaybackDecision(PlaybackTelemetry.DecisionEvent event) {
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession, event, SystemClock.elapsedRealtime());
    }

    public int getPlaybackState() {
        return isReleased() ? Player.STATE_IDLE : player.getPlaybackState();
    }

    public boolean isPlaying() {
        return !isReleased() && player.isPlaying();
    }

    public boolean isReleased() {
        return player == null;
    }

    public String getUrl() {
        return spec != null ? spec.getUrl() : null;
    }

    public String getKey() {
        return spec != null ? spec.getKey() : null;
    }

    public List<Danmaku> getDanmakus() {
        return spec != null ? spec.getDanmakus() : null;
    }

    public MediaMetadata getMetadata() {
        return spec != null ? spec.getMetadata() : null;
    }

    public Map<String, String> getHeaders() {
        return spec == null || spec.getHeaders() == null ? new HashMap<>() : spec.getHeaders();
    }

    public float getSpeed() {
        return userPlaybackSpeed;
    }

    public float getEffectiveSpeed() {
        return player == null ? userPlaybackSpeed : player.getPlaybackParameters().speed;
    }

    public String getNetworkProtectionText() {
        if (!isExo() || !ExoPerformanceSetting.isNetworkProtectionEnabled()) return "";
        if (Math.abs(userPlaybackSpeed - 1f) > 0.001f) return "手动倍速时停用";
        if (!isVod()) return "仅支持点播";
        ExoNetworkGuardEligibility.Decision eligibility = getNetworkProtectionEligibility();
        if (!eligibility.eligible()) return networkProtectionEligibilityText(eligibility.reason());
        return switch (networkProtectionState) {
            case NORMAL -> "正常";
            case WARNING -> "评估中";
            case PROTECT -> "降速中";
            case RECOVERY -> "恢复中";
            case UNSUSTAINABLE -> "网络不足";
        };
    }

    private String networkProtectionEligibilityText(String reason) {
        return switch (reason == null ? "" : reason) {
            case "preserve-passthrough" -> "音频直通时停用";
            case "preserve-tunneling" -> "隧道模式时停用";
            case "speed-unsupported" -> "播放器不支持调速";
            case "user-speed" -> "手动倍速时停用";
            case "vod-only" -> "仅支持点播";
            default -> "未启用";
        };
    }

    public long getNetworkProtectionMediaBitrate() {
        return networkProtectionMediaBitrate;
    }

    public long getNetworkProtectionStableThroughput() {
        return networkProtectionMediaBitrate <= 0 ? 0 : Math.max(0, Math.round(networkProtectionMediaBitrate * networkProtectionSupportedSpeed));
    }

    public long getNetworkProtectionConsumption() {
        return networkProtectionMediaBitrate <= 0 ? 0 : Math.max(0, Math.round(networkProtectionMediaBitrate * getEffectiveSpeed()));
    }

    public float getNetworkProtectionSupportedSpeed() {
        return networkProtectionSupportedSpeed;
    }

    public boolean isEmpty() {
        return spec == null || TextUtils.isEmpty(spec.getUrl());
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public boolean isLive() {
        return engine != null && engine.isLive();
    }

    public boolean isVod() {
        return engine != null && engine.isVod();
    }

    public boolean haveTrack(int type) {
        return engine != null && engine.haveTrack(type);
    }

    public boolean haveTitle() {
        return engine != null && engine.haveTitle();
    }

    public boolean haveDanmaku() {
        return getDanmakus() != null && getDanmakus().stream().anyMatch(Danmaku::isSelected);
    }

    /** 直播弹幕已挂载（与管线自身的连会话判定同口径：当前弹幕地址分级为 live）；点播弹幕不在此列，走 haveDanmaku()。 */
    public boolean haveLiveDanmaku() {
        return !TextUtils.isEmpty(currentDanmakuUrl) && DanmakuUrlPolicy.classify(currentDanmakuUrl).isLive();
    }

    public boolean canSetOpening(long position, long duration) {
        return position > 0 && duration > 0 && position <= Constant.getOpEdLimit(duration);
    }

    public boolean canSetEnding(long position, long duration) {
        return position > 0 && duration > 0 && duration - position <= Constant.getOpEdLimit(duration);
    }

    public int getVideoWidth() {
        return videoSize == null ? 0 : videoSize.width;
    }

    public int getVideoHeight() {
        return videoSize == null ? 0 : videoSize.height;
    }

    public long getPosition() {
        return isReleased() ? 0 : player.getCurrentPosition();
    }

    public long getBufferedDuration() {
        return Math.max(0, getNativeBufferedPosition() - getPosition());
    }

    public int getBufferedPercentage() {
        if (!isExo()) return player.getBufferedPercentage();
        long duration = player.getDuration();
        if (duration == 0) return 100;
        if (duration < 0) return 0;
        return Math.max(0, Math.min(100, androidx.media3.common.util.Util.percentInt(
                getNativeBufferedPosition(), duration)));
    }

    // Display-facing buffered position: the player's own in-memory buffered end only.
    // Disk-backed ranges (PlaybackDiskBufferStore) deliberately do not count here: data
    // that exists only as cache files is not buffered media, and merging it in made the
    // seek bar and OSD over-report (bounded only by the preload window). The disk store
    // still serves PreCache's refill decisions, where on-disk data is the real input.
    private long getNativeBufferedPosition() {
        return Math.max(0, player.getBufferedPosition());
    }

    public boolean isLoading() {
        return player.isLoading();
    }

    public String getSizeText() {
        int width = getVideoWidth();
        int height = getVideoHeight();
        if (width <= 0 || height <= 0) {
            Format format = getVideoFormat();
            if (format != null) {
                if (width <= 0) width = format.width;
                if (height <= 0) height = format.height;
            }
        }
        return width <= 0 || height <= 0 ? "" : width + " x " + height;
    }

    public Format getVideoFormat() {
        return engine == null ? null : engine.getVideoFormat();
    }

    public PlayerCacheState getCacheState() {
        return engine == null ? PlayerCacheState.empty() : engine.getCacheState();
    }

    public String getRenderDiagnostics() {
        return engine == null ? "" : engine.getRenderDiagnostics();
    }

    public String getRuntimeDiagnostics() {
        return engine == null ? "" : engine.getRuntimeDiagnostics();
    }

    public String getGpuLoadDiagnostics() {
        return engine == null ? "" : engine.getGpuLoadDiagnostics();
    }

    public void setGpuLoadDiagnosticsEnabled(boolean enabled) {
        if (engine != null) engine.setGpuLoadDiagnosticsEnabled(enabled);
    }

    public PlayerEngine.VideoPlaybackDetails getVideoPlaybackDetails() {
        return engine == null
                ? PlayerEngine.VideoPlaybackDetails.empty()
                : engine.getVideoPlaybackDetails();
    }

    public AudioPlaybackDiagnostics.Snapshot getAudioPlaybackDiagnostics() {
        return engine == null
                ? AudioPlaybackDiagnostics.Snapshot.empty()
                : engine.getAudioPlaybackDiagnostics();
    }

    public long getDroppedFrames() {
        return engine == null ? 0 : engine.getDroppedFrames();
    }

    public int getRebufferCount() {
        long discCount = player instanceof MpvPlayer mpv ? mpv.getDiscRebufferCount() : 0;
        return (int) Math.min(Integer.MAX_VALUE, playbackBufferingTracker.getRebufferCount() + discCount);
    }

    public long getRebufferTotalMs() {
        return getRebufferTotalMs(SystemClock.elapsedRealtime());
    }

    private long getRebufferTotalMs(long nowMs) {
        long discMs = player instanceof MpvPlayer mpv ? mpv.getDiscRebufferTotalMs(nowMs) : 0;
        return playbackBufferingTracker.getRebufferTotalMs(nowMs) + discMs;
    }

    public boolean supportsSubtitleStyle() {
        return engine != null && engine.supportsSubtitleStyle();
    }

    public boolean supportsSecondarySubtitle() {
        return engine != null && engine.supportsSecondarySubtitle();
    }

    public boolean isSecondarySubtitleSelected(Format format) {
        return engine != null && engine.isSecondarySubtitleSelected(format);
    }

    public boolean isPrimarySubtitleSelected(Format format) {
        return engine != null && engine.isPrimarySubtitleSelected(format);
    }

    public String getAudioPassThroughText() {
        if (!PlayerSetting.isAudioPassThrough()) return "关";
        if (!isMpv()) return "开";
        String codecs = engine == null ? "" : engine.getAudioSpdifCodecs();
        return TextUtils.isEmpty(codecs) ? "开/PCM" : "开/" + codecs;
    }

    public void setSubtitleStyle(float textSize, float position) {
        if (engine != null) engine.setSubtitleStyle(textSize, position);
    }

    public String getSpeedText() {
        return SPEED_FORMAT.format(getEffectiveSpeed());
    }

    public String getDecodeText() {
        return engine == null ? "" : engine.getDecodeText();
    }

    public boolean isHardDecode() {
        return engine != null && engine.isHard();
    }

    public String getPlayerText() {
        return ResUtil.getStringArray(R.array.select_player_kernel)[PlayerSetting.playerToIndex(playerType)];
    }

    public int getPlayerType() {
        return playerType;
    }

    /** 当前播放的 PlaySpec（含 URL 与请求头，供 AI 本地字幕 MPV 影子采集使用） */
    public PlaySpec getSpec() {
        return spec;
    }

    public String getLutText() {
        return LutSetting.getButtonText();
    }

    public void setLutAllowed(boolean allowed) {
        if (lutAllowed == allowed) return;
        lutAllowed = allowed;
        if (!allowed) resetLutRuntimeState("lut_disallowed", true);
    }

    public String getLutUnavailableReason() {
        return LutEligibility.getUnavailableReason(engine, spec);
    }

    public boolean selectLut(@Nullable LutPreset preset, boolean preview) {
        boolean autoSwitchMpvToGpu = preset != null
                && MpvPerformanceSetting.getOutputMode() == MpvPerformanceSetting.OUTPUT_AUTO
                && engine instanceof MpvPlayerEngine mpv
                && mpv.isSurfaceDirect();
        if (preset != null) {
            String reason = autoSwitchMpvToGpu
                    ? LutEligibility.getUnavailableReason(engine, spec, true)
                    : getLutUnavailableReason();
            if (!TextUtils.isEmpty(reason)) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-ui", "reject preset=%s reason=%s", preset.getId(), reason);
                Notify.show(reason);
                return false;
            }
        }
        LutSetting.select(preset);
        callback.onPlayerRenderRequired();
        if (autoSwitchMpvToGpu) {
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log("lut-mpv", "auto output switch surface-direct -> gpu preset=%s preview=%s",
                        preset.getId(), preview);
            }
            return rebuildAndRestartMpv(false, "auto-lut-selected");
        }
        if (preset != null && preview) applyLutPreview(true);
        else applyLut(true);
        return true;
    }

    public boolean isMpv() {
        return playerType == PlayerSetting.MPV;
    }

    public boolean sendMpvCustomButton(String id, boolean longPress) {
        if (!isMpv() || TextUtils.isEmpty(id) || !(engine instanceof MpvPlayerEngine mpv)) return false;
        return mpv.sendScriptMessage(MpvConfigStore.CUSTOM_BUTTON_MESSAGE, id, longPress ? "long" : "short");
    }

    public boolean isMpvSurfaceDirect() {
        return engine instanceof MpvPlayerEngine mpv && mpv.isSurfaceDirect();
    }

    /**
     * Keep the native player's shutter visible while automatic MPV output is
     * still being selected. This prevents a failed direct DV probe from
     * exposing a stale poster or the last frame before the GPU rebuild.
     */
    public boolean shouldKeepVideoShutterClosed() {
        return isMpv()
                && MpvPerformanceSetting.getOutputMode() == MpvPerformanceSetting.OUTPUT_AUTO
                && !mpvAutoOutputEvaluated
                && !mpvAutoOutputFrameReady;
    }

    public boolean isExo() {
        return playerType == PlayerSetting.EXO;
    }

    public boolean requiresTextureRenderForLut() {
        return isExo()
                && LutSetting.isEnabled()
                && canWarmLutPipeline()
                && TextUtils.isEmpty(getLutUnavailableReason());
    }

    public boolean isNativePlayer() {
        return !isExo();
    }

    public String getPositionTime(long delta) {
        long time = Math.max(0, Math.min(getPosition() + delta, Math.max(0, getDuration())));
        return Util.timeMs(time);
    }

    public long getDuration() {
        return isReleased() ? 0 : player.getDuration();
    }

    public String getDurationTime() {
        return Util.timeMs(Math.max(0, getDuration()));
    }

    public void setSub(Sub sub) {
        if (spec != null) spec.setSub(sub);
        long position = Math.max(0, getPosition());
        setMediaItem();
        if (position > 0) seekTo(position);
    }

    public void setFormat(String format) {
        if (spec != null) spec.setFormat(format);
        setMediaItem();
    }

    public void setTitle(MediaEdition edition) {
        if (edition == null) return;
        if (isMpv() && engine.selectEdition(edition)) return;
        if (spec != null) spec.setUrl(spec.getUri().buildUpon().fragment("edition=" + edition.index).build().toString());
        if (engine.selectEdition(edition)) return;
        setMediaItem();
        seekTo(0);
    }

    /**
     * 海阔 refreshVideoUrl 的等价换址面（8.83 VideoPlayerActivity.refreshVideoUrl → reStartPlayer）：
     * 原地换当前播放地址，不重建播放页。restartPosition=true 从头播；false 保留当前进度续播
     * （与 setSub 的「换面保进度」同款）。已释放或无当前 spec 时回 false、不动作。
     */
    public boolean refreshUrl(String url, boolean restartPosition) {
        if (isReleased() || spec == null || TextUtils.isEmpty(url)) return false;
        long position = restartPosition ? 0 : Math.max(0, getPosition());
        spec.setUrl(url);
        setMediaItem();
        if (!restartPosition && position > 0) seekTo(position);
        return true;
    }

    public static MediaMetadata buildMetadata(String title, String artist, String artUri) {
        Uri artwork = TextUtils.isEmpty(artUri) ? null : Uri.parse(artUri);
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(artwork).build();
    }

    public void setMetadata(MediaMetadata data) {
        if (spec != null) spec.setMetadata(data);
        engine.setMetadata(data);
    }

    public void setDanmakuController(DanmakuController controller) {
        if (danmakuController == controller) {
            configureDanmakuController(controller);
            return;
        }
        if (danmakuController != null) {
            danmakuController.setListener(null);
            danmakuController.clearItems();
        }
        danmakuController = controller;
        if (danmakuController == null) return;
        configureDanmakuController(danmakuController);
        restoreDanmakuDataSource();
    }

    private void configureDanmakuController(DanmakuController controller) {
        if (controller == null) return;
        controller.setOkHttpClient(OkHttp.player());
        controller.setConfig(DanmakuSetting.getConfig());
        controller.setEnabled(DanmakuSetting.isShow());
        controller.setListener(new DanmakuController.Listener() {
            @Override
            public void onLoadCompleted(Uri uri, int count) {
                logDanmakuLoad("completed", uri, count, null);
                finishDanmakuLoad(uri);
            }

            @Override
            public void onLoadError(Uri uri, IOException error) {
                logDanmakuLoad("error", uri, -1, error);
                finishDanmakuLoad(uri);
            }
        });
    }

    private void restoreDanmakuDataSource() {
        if (danmakuController == null || TextUtils.isEmpty(currentDanmakuUrl)) return;
        if (!DanmakuUrlPolicy.classify(currentDanmakuUrl).isStatic()) return;
        loadingDanmakuKey = currentDanmakuKey;
        danmakuLoadStartedAtMs = SystemClock.elapsedRealtime();
        danmakuLoadInProgress = true;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "restore controller %s key=%s", DanmakuUrlPolicy.logSummary(currentDanmakuUrl), summarizeUrl(currentDanmakuKey));
        danmakuController.setDataSource(Uri.parse(currentDanmakuUrl));
    }

    public void setDanmakuConfig(DanmakuConfig config) {
        if (danmakuController != null) danmakuController.setConfig(config);
    }

    public void setDanmakuEnabled(boolean enabled) {
        if (danmakuController != null) danmakuController.setEnabled(enabled);
        if (!enabled) {
            stopLiveDanmakuSession("hidden");
            liveDanmakuCoordinator.suspend("hidden");
        } else if (danmakuForeground && DanmakuUrlPolicy.classify(currentDanmakuUrl).isLive()) {
            connectLiveDanmakuSession(currentDanmakuUrl);
        } else if (danmakuForeground) {
            liveDanmakuCoordinator.resume();
        }
    }

    public void setDanmakuForeground(boolean foreground) {
        if (danmakuForeground == foreground) return;
        danmakuForeground = foreground;
        if (!foreground) {
            stopLiveDanmakuSession("background");
            discardLiveDanmakuPending();
            liveDanmakuCoordinator.suspend("background");
        } else if (DanmakuSetting.isShow() && DanmakuUrlPolicy.classify(currentDanmakuUrl).isLive()) {
            connectLiveDanmakuSession(currentDanmakuUrl);
        } else if (DanmakuSetting.isShow()) {
            liveDanmakuCoordinator.resume();
        }
    }

    public void setPlaybackForeground(boolean foreground) {
        playbackForeground = foreground;
        if (!foreground || pendingExoDecoderResourceRecovery == null) return;
        ExoDecoderResourceRecovery recovery = pendingExoDecoderResourceRecovery;
        pendingExoDecoderResourceRecovery = null;
        scheduleExoDecoderResourceRecovery(recovery, "foreground");
    }

    public void sendDanmaku(String text) {
        if (danmakuController != null) danmakuController.sendNow(text);
    }

    public String setSpeed(float speed) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return getSpeedText();
        if (Math.abs(speed - userPlaybackSpeed) >= 0.001f) {
            invalidatePlaybackProfileAssessments(
                    PlaybackProfileAbCoordinator.InvalidationReason.USER_SPEED);
        }
        userPlaybackSpeed = speed;
        resetNetworkProtectionSession("user-speed");
        if (Math.abs(speed - 1f) < 0.001f) scheduleNetworkProtection(0);
        return getSpeedText();
    }

    private void applyEffectiveSpeed(float speed, String reason) {
        if (player == null || !player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) {
            logNetworkGuard("apply skipped reason=" + reason + " player=" + (player != null)
                    + " command=" + (player != null && player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)));
            return;
        }
        float current = player.getPlaybackParameters().speed;
        if (Math.abs(speed - userPlaybackSpeed) >= 0.001f) {
            invalidatePlaybackProfileAssessments(
                    PlaybackProfileAbCoordinator.InvalidationReason
                            .SPEED_RESCUE_CONFOUND);
        }
        logNetworkGuard(String.format(java.util.Locale.US,
                "apply request reason=%s requested=%.3f current=%.3f user=%.3f state=%d playing=%s loading=%s",
                reason, speed, current, userPlaybackSpeed, player.getPlaybackState(), player.isPlaying(), player.isLoading()));
        if (Math.abs(current - speed) < 0.001f) return;
        player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(speed));
        logNetworkGuard(String.format(java.util.Locale.US,
                "apply result reason=%s requested=%.3f actual=%.3f", reason, speed, player.getPlaybackParameters().speed));
        PlaybackTrace.log("exo-network-protection", playbackTrace.current(), "speed %.3f->%.3f reason=%s user=%.2f", current, speed, reason, userPlaybackSpeed);
    }

    private void resetNetworkProtectionSession(String reason) {
        App.removeCallbacks(networkProtectionRunnable);
        networkProtectionController.reset();
        networkProtectionTrend.reset();
        networkProtectionState = ExoNetworkGuardController.State.NORMAL;
        networkProtectionTier = ExoNetworkGuardController.ProtectionTier.NONE;
        networkProtectionReason = reason;
        networkProtectionSpeed = 1f;
        networkProtectionSupportedSpeed = 1f;
        networkProtectionMediaBitrate = 0;
        networkProtectionExperimentToken = null;
        applyEffectiveSpeed(userPlaybackSpeed, reason);
    }

    private ExoNetworkGuardEligibility.Decision getNetworkProtectionEligibility() {
        AudioPlaybackDiagnostics.OutputMode audioOutputMode = getAudioPlaybackDiagnostics()
                .outputMode();
        return ExoNetworkGuardEligibility.resolve(new ExoNetworkGuardEligibility.Request(
                ExoPerformanceSetting.isNetworkProtectionEnabled()
                        && experimentAllowed(
                        PlaybackExperimentPolicy.Action.EXO_NETWORK_SPEED),
                player != null && isExo(),
                isVod(),
                Math.abs(userPlaybackSpeed - 1f) < 0.001f,
                player != null && player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH),
                PlayerSetting.isTunnel(),
                audioOutputMode));
    }

    private void scheduleNetworkProtection(long delayMs) {
        App.removeCallbacks(networkProtectionRunnable);
        ExoNetworkGuardEligibility.Decision eligibility = getNetworkProtectionEligibility();
        AudioPlaybackDiagnostics.OutputMode audioOutputMode = getAudioPlaybackDiagnostics()
                .outputMode();
        logNetworkGuard("schedule delay=" + delayMs + " eligible=" + eligibility.eligible()
                + " reason=" + eligibility.reason() + " exo=" + isExo() + " vod=" + isVod()
                + " userSpeed=" + userPlaybackSpeed + " tunnel=" + PlayerSetting.isTunnel()
                + " configuredPassthrough=" + PlayerSetting.isAudioPassThrough(PlayerSetting.EXO)
                + " audioOutput=" + audioOutputMode
                + " state=" + (player == null ? -1 : player.getPlaybackState())
                + " playing=" + (player != null && player.isPlaying()));
        if (!eligibility.eligible()) {
            if (networkProtectionSpeed < 0.999f) resetNetworkProtectionSession(eligibility.reason());
            else {
                networkProtectionState = ExoNetworkGuardController.State.NORMAL;
                networkProtectionTier = ExoNetworkGuardController.ProtectionTier.NONE;
                networkProtectionReason = eligibility.reason();
            }
            return;
        }
        if (player.getPlaybackState() != Player.STATE_READY || !player.isPlaying()) return;
        networkProtectionExperimentToken = playbackExperimentCoordinator.capture(
                PlaybackExperimentPolicy.Action.EXO_NETWORK_SPEED);
        App.post(networkProtectionRunnable, delayMs);
    }

    private void evaluateNetworkProtection() {
        if (player == null) return;
        if (!playbackExperimentCoordinator.isCurrent(
                networkProtectionExperimentToken)
                || !experimentAllowed(
                PlaybackExperimentPolicy.Action.EXO_NETWORK_SPEED)) {
            resetNetworkProtectionSession("experiment-disabled");
            return;
        }
        ExoNetworkGuardEligibility.Decision eligibility = getNetworkProtectionEligibility();
        boolean eligible = eligibility.eligible();
        long nowMs = SystemClock.elapsedRealtime();
        boolean ready = player.getPlaybackState() == Player.STATE_READY;
        boolean playing = player.isPlaying();
        boolean loading = player.isLoading();
        long bufferedMs = Math.max(0, player.getTotalBufferedDuration());
        networkProtectionTrend.observe(
                nowMs,
                bufferedMs,
                eligible && ready && playing,
                loading);
        ForwardBufferTrend.Snapshot trend = networkProtectionTrend.snapshot();
        PlaybackAnalyticsListener.Snapshot analytics = PlaybackAnalyticsListener.getSnapshot();
        PlaybackAnalyticsListener.DisplayMediaBitrateEstimate media = PlaybackAnalyticsListener.getDisplayMediaBitrateEstimate(getVideoFormat());
        boolean networkEstimateKnown = isTrustedNetworkEstimate(analytics, media);
        float networkSupportedSpeed = networkEstimateKnown ? Math.min(2f, analytics.bandwidthEstimate() * 0.90f / media.bitrateBitsPerSecond()) : 1f;
        long safeBufferMs = getNetworkProtectionSafeBufferMs();
        float previousEffectiveSpeed = getEffectiveSpeed();
        ExoNetworkGuardController.State previousState = networkProtectionState;
        ExoNetworkGuardController.ProtectionTier previousTier = networkProtectionTier;
        ExoNetworkGuardController.Decision decision = networkProtectionController.evaluate(new ExoNetworkGuardController.Input(
                nowMs,
                eligible,
                ready,
                playing,
                loading,
                bufferedMs,
                trend.known(),
                trend.slopeMsPerSecond(),
                trend.fastSlopeMsPerSecond(),
                trend.slowSlopeMsPerSecond(),
                trend.windowMs(),
                analytics.rebufferCount(),
                previousEffectiveSpeed,
                ExoPerformanceSetting.getNetworkProtectionMinimumSpeed(),
                safeBufferMs,
                networkEstimateKnown,
                networkSupportedSpeed));
        logNetworkGuard(String.format(java.util.Locale.US,
                "evaluate eligible=%s ready=%s playing=%s loading=%s buffered=%d safe=%d trendKnown=%s slope=%d fast=%d slow=%d window=%d rebuffer=%d current=%.3f networkKnown=%s networkSupported=%.3f decision=%s tier=%s reason=%s changed=%s target=%.3f supported=%.3f raw=%.3f calculated=%.3f tte=%d ttr=%d requiredSlew=%.4f appliedSlew=%.4f feasible=%s",
                eligible, ready, playing, loading, bufferedMs, safeBufferMs, trend.known(), trend.slopeMsPerSecond(),
                trend.fastSlopeMsPerSecond(), trend.slowSlopeMsPerSecond(), trend.windowMs(), analytics.rebufferCount(),
                getEffectiveSpeed(), networkEstimateKnown, networkSupportedSpeed, decision.state(), decision.tier(), decision.reason(),
                decision.changed(), decision.targetSpeed(), decision.supportedSpeed(), decision.rawTargetSpeed(),
                decision.calculatedTargetSpeed(), decision.timeToEmptyMs(), decision.timeToReserveMs(),
                decision.requiredSlewPerSecond(), decision.appliedSlewPerSecond(), decision.rampFeasible()));
        networkProtectionState = decision.state();
        networkProtectionTier = decision.tier();
        networkProtectionReason = decision.reason();
        networkProtectionSpeed = decision.targetSpeed();
        networkProtectionSupportedSpeed = decision.supportedSpeed();
        networkProtectionMediaBitrate = media.bitrateBitsPerSecond();
        if (decision.changed()) applyEffectiveSpeed(networkProtectionSpeed, "guard-" + decision.reason());
        PlaybackTelemetry.DecisionOutcome telemetryOutcome = decision.changed()
                ? PlaybackTelemetry.DecisionOutcome.APPLIED
                : !eligible || !ready || !playing
                ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                : PlaybackTelemetry.DecisionOutcome.HELD;
        playbackTelemetryCoordinator.publishDecision(playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.NETWORK_PROTECTION,
                        telemetryOutcome,
                        previousState.name().toLowerCase(java.util.Locale.US),
                        decision.state().name().toLowerCase(java.util.Locale.US),
                        networkProtectionState.name().toLowerCase(java.util.Locale.US),
                        decision.reason(),
                        telemetryOutcome == PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                                ? eligibility.reason() : telemetryOutcome == PlaybackTelemetry.DecisionOutcome.HELD ? "no-change" : "none",
                        List.of(
                                PlaybackTelemetry.DecisionInput.bool("eligible", eligible, PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("ready", ready, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("playing", playing, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("loading", loading, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("buffered_ms", bufferedMs, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("safe_buffer_ms", safeBufferMs, PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                networkEstimateKnown ? PlaybackTelemetry.DecisionInput.number("bandwidth_bps", analytics.bandwidthEstimate(), PlaybackAutoContext.ValueSource.ESTIMATOR, PlaybackAutoContext.Confidence.MEDIUM) : PlaybackTelemetry.DecisionInput.unknown("bandwidth_bps"),
                                media.bitrateBitsPerSecond() > 0 ? PlaybackTelemetry.DecisionInput.number("media_bitrate_bps", media.bitrateBitsPerSecond(), PlaybackAutoContext.ValueSource.ESTIMATOR, telemetryConfidence(media.confidence())) : PlaybackTelemetry.DecisionInput.unknown("media_bitrate_bps"),
                                PlaybackTelemetry.DecisionInput.number("rebuffer_count", analytics.rebufferCount(), PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                trend.known() ? PlaybackTelemetry.DecisionInput.number("buffer_slope_msps", trend.slopeMsPerSecond(), PlaybackAutoContext.ValueSource.ESTIMATOR, PlaybackAutoContext.Confidence.MEDIUM) : PlaybackTelemetry.DecisionInput.unknown("buffer_slope_msps"),
                                PlaybackTelemetry.DecisionInput.decimal("current_speed", previousEffectiveSpeed, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.decimal("target_speed", decision.targetSpeed(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH))),
                nowMs);
        if (decision.changed() || previousState != networkProtectionState || previousTier != networkProtectionTier) {
            PlaybackTrace.log("exo-network-protection", playbackTrace.current(), "state=%s tier=%s reason=%s speed=%.3f supported=%.3f rawTarget=%.3f target=%.3f floor=%.2f buffered=%d safe=%d tte=%d ttr=%d requiredSlew=%.4f appliedSlew=%.4f feasible=%s loading=%s slope=%d fast=%d slow=%d window=%d rebuffer=%d networkKnown=%s networkSupported=%.3f route=%s",
                    networkProtectionState, networkProtectionTier, networkProtectionReason, networkProtectionSpeed, decision.supportedSpeed(), decision.rawTargetSpeed(), decision.calculatedTargetSpeed(), ExoPerformanceSetting.getNetworkProtectionMinimumSpeed(),
                    player.getTotalBufferedDuration(), decision.safeBufferMs(), decision.timeToEmptyMs(), decision.timeToReserveMs(), decision.requiredSlewPerSecond(), decision.appliedSlewPerSecond(), decision.rampFeasible(),
                    player.isLoading(), trend.slopeMsPerSecond(), trend.fastSlopeMsPerSecond(), trend.slowSlopeMsPerSecond(), trend.windowMs(), analytics.rebufferCount(), networkEstimateKnown, networkSupportedSpeed, getEffectivePlaybackRoute().route());
        }
        if (eligible && player.getPlaybackState() == Player.STATE_READY && player.isPlaying()) scheduleNetworkProtection(getNetworkProtectionEvaluationDelayMs());
    }

    private long getNetworkProtectionEvaluationDelayMs() {
        return switch (networkProtectionState) {
            case WARNING, PROTECT, RECOVERY -> ExoNetworkGuardController.CONTROL_INTERVAL_MS;
            case NORMAL, UNSUSTAINABLE -> ExoNetworkGuardController.OBSERVE_INTERVAL_MS;
        };
    }

    private long getNetworkProtectionSafeBufferMs() {
        PlaybackRoute.Resolution route = getEffectivePlaybackRoute();
        return ExoNetworkGuardBufferPolicy.resolve(route.loopback(), ExoPerformanceSetting.getRebufferMs());
    }

    private boolean isTrustedNetworkEstimate(PlaybackAnalyticsListener.Snapshot analytics, PlaybackAnalyticsListener.DisplayMediaBitrateEstimate media) {
        if (analytics.bandwidthEstimate() <= 0 || media.bitrateBitsPerSecond() <= 0) return false;
        if ("unknown".equals(media.source()) || "low".equals(media.confidence()) || "unknown".equals(media.confidence())) return false;
        return getEffectivePlaybackRoute().route() == PlaybackRoute.DIRECT_REMOTE_HTTP;
    }

    public String addSpeed() {
        return setSpeed(nextPresetSpeed());
    }

    public String addSpeed(float value) {
        return setSpeed(Math.min(getSpeed() + value, 5));
    }

    public String subSpeed(float value) {
        return setSpeed(Math.max(getSpeed() - value, 0.25f));
    }

    public String toggleSpeed() {
        return setSpeed(getSpeed() == 1 ? PlayerSetting.getSpeed() : 1);
    }

    private float nextPresetSpeed() {
        float speed = getSpeed();
        for (float preset : SPEED_PRESETS) if (speed < preset - 0.01f) return preset;
        return SPEED_PRESETS[0];
    }

    public void setTrack(List<Track> tracks) {
        mpvExplicitSubtitlePreference = hasRequestedSubtitle(tracks);
        if (mpvExplicitSubtitlePreference && engine instanceof MpvPlayerEngine mpv) {
            mpv.retainSubtitleSurfaceForCurrentItem();
        }
        if (!tracks.isEmpty()) engine.setTrack(tracks);
    }

    public void setSecondarySubtitleTrack(Track track) {
        lastSecondarySubtitleTrack = track;
        if (track != null && !track.isDisabled()
                && engine instanceof MpvPlayerEngine mpv) {
            mpv.retainSubtitleSurfaceForCurrentItem();
        }
        if (engine != null) engine.setSecondarySubtitleTrack(track);
    }




    public void play() {
        startNativeAudioSession(true);
        player.play();
    }

    public void pause() {
        invalidatePlaybackProfileAssessments(
                PlaybackProfileAbCoordinator.InvalidationReason.PAUSED);
        player.pause();
        // E4：只暂停不放弃焦点。旧路放弃焦点会让被焦点暂停的音乐在视频页
        // 还开着、只是暂停时就自动续播，与 X-28 暂停路行为分裂。焦点在真正
        // 退出（stop/release）时才放弃；切到别的音频应用时焦点丢失回调仍会
        // 正常放弃。Exo 路 Media3 自身在 playWhenReady=false 时也会放弃焦点，
        // 该路本批未改 Media3 焦点配置，仍属残留，见交回。
        unregisterNoisyReceiver();
    }

    public void stop() {
        com.fongmi.android.tv.api.HomePrewarm.playbackActive = false; // 首页预热恢复推进
        logStopAction("stop");
        prepareSeq++;
        stopNativeAudioSession();
        clearDanmaku("stop");
        if (engine != null) engine.stop();
        stopParse();
    }

    /**
     * 自停诊断：凡是走到 stop/release 都记一行，带调用栈关键帧（类名＋方法名）与当前播放 key。
     * 下次出现「没人操作播放器自己停」时，直接看 frames 就知道是谁下的手。
     */
    private void logStopAction(String action) {
        if (!SpiderDebug.isEnabled()) return;
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        StringBuilder frames = new StringBuilder();
        // [0]=getStackTrace [1]=logStopAction [2]=stop/release，调用方从 [3] 开始
        for (int i = 3; i < trace.length && i < 8; i++) {
            String cn = trace[i].getClassName();
            String simple = cn.substring(cn.lastIndexOf('.') + 1);
            if (frames.length() > 0) frames.append(" <- ");
            frames.append(simple).append('.').append(trace[i].getMethodName());
        }
        SpiderDebug.log("player-stop", "action=%s key=%s frames=%s", action, getKey(), frames);
    }

    public void clearMediaItems() {
        if (isReleased()) return;
        stopNativeAudioSession();
        clearDanmaku("clear_media_items");
        mpvAutoGpuPinnedForSession = false;
        player.clearMediaItems();
    }

    public boolean isRepeatOne() {
        return engine != null && engine.isRepeatOne();
    }

    public void setRepeatOne(boolean repeat) {
        if (engine != null) engine.setRepeatOne(repeat);
    }

    public void seekTo(long time) {
        long now = SystemClock.elapsedRealtime();
        invalidatePlaybackProfileAssessments(
                PlaybackProfileAbCoordinator.InvalidationReason.USER_SEEK);
        rtspLiveLagController.onUserSeek(playbackAutoSession, now);
        resetNetworkProtectionSession("user-seek");
        if (isExo()) {
            PlaybackAnalyticsListener.onUserSeekRequested(
                    player.getCurrentPosition(),
                    time,
                    player.getPlaybackState(),
                    player.getBufferedPosition(),
                    player.getTotalBufferedDuration(),
                    player.isLoading(),
                    player.isPlaying());
        }
        player.seekTo(time);
    }

    public long getTextOffsetMs() {
        if (player.isCommandAvailable(Player.COMMAND_GET_TEXT_OFFSET)) return player.getTextOffsetMs();
        return 0;
    }

    public void setTextOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_TEXT_OFFSET)) player.setTextOffsetMs(offsetMs);
    }

    public long getAudioOffsetMs() {
        if (player.isCommandAvailable(Player.COMMAND_GET_AUDIO_OFFSET)) return player.getAudioOffsetMs();
        return 0;
    }

    public void setAudioOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_AUDIO_OFFSET)) player.setAudioOffsetMs(offsetMs);
    }

    public void reset() {
        App.removeCallbacks(runnable);
        boolean activePlayback = player != null
                && player.getPlaybackState() == Player.STATE_READY
                && player.getPlayWhenReady();
        if (activePlayback) scheduleNetworkProtection(0);
        else resetNetworkProtectionSession("reset");
        retry = 0;
        localProxyRetry = 0;
        hardDecodeSwitchRetryArmed = false;
        clearPendingSwitchRestore();
    }

    public void clear() {
        prepareSeq++;
        lutApplySeq++;
        clearExoDecoderResourceRecovery(true);
        resetNetworkProtectionSession("clear");
        resetMpvOutputRuntime();
        spec = null;
        clearPendingSwitchRestore();
        clearDanmaku("clear");
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        clearLutWarmupRecovery();
        endPlaybackTelemetrySession("clear");
        playbackBufferingTracker.reset();
        clearPlaybackAutoContext();
        playbackTrace.clear();
        lastLoggedRouteTraceId = PlaybackTrace.NONE;
    }

    public void resetTrack() {
        if (engine != null) engine.resetTrack();
    }

    public void restoreVideoTrack() {
        if (engine != null) engine.restoreVideoTrack();
    }

    public void toggleDecode() {
        if (engine == null || player == null) return;
        int next = engine.isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD;
        boolean resetVideoSurface = playerType == PlayerSetting.EXO && next == PlayerEngine.HARD;
        long position = Math.max(0, getPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        hardDecodeSwitchRetryArmed = next == PlayerEngine.HARD;
        beginPlaybackTrace("switch-decode");
        engine.setDecode(next);
        if (engine instanceof ExoPlayerEngine exo) {
            exo.prepareFrameSchedulingForNextPlayback();
        }
        rebuildPlayer(resetVideoSurface);
        setMediaItem();
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    public void switchDecode(PlaySpec freshSpec, long position, float speed, boolean repeat) {
        if (engine == null || player == null || freshSpec == null) return;
        beginPlaybackTrace("switch-decode-fresh");
        int next = engine.isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD;
        boolean resetVideoSurface = playerType == PlayerSetting.EXO && next == PlayerEngine.HARD;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        PlayerEngine newEngine = buildEngineOrNull(playerType, next);
        if (newEngine == null) return;
        prepareSeq++;
        resetLutRuntimeState("switch_decode_fresh", true);
        stopNativeAudioSession();
        engine.release();
        engine = newEngine;
        player = engine.getPlayer();
        resetMpvOutputRuntime();
        spec = freshSpec;
        bindPlaybackTrace();
        hardDecodeSwitchRetryArmed = next == PlayerEngine.HARD;
        playWhenReady = wasPlayWhenReady;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch decode fresh decode=%d position=%d spec=%s", next, position, debugSpec());
        callback.onPlayerRebuild(player, resetVideoSurface);
        setMediaItem(Constant.TIMEOUT_PLAY);
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    public void switchDecode(Result result, String key, MediaMetadata metadata, boolean useParse, long position, float speed, boolean repeat) {
        if (engine == null || player == null || result == null || result.hasMsg() || result.getRealUrl().isEmpty()) return;
        beginPlaybackTrace("switch-decode-result");
        int next = engine.isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD;
        boolean resetVideoSurface = playerType == PlayerSetting.EXO && next == PlayerEngine.HARD;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        PlayerEngine newEngine = buildEngineOrNull(playerType, next);
        if (newEngine == null) return;
        prepareSeq++;
        resetLutRuntimeState("switch_decode_result", true);
        stopNativeAudioSession();
        stopParse();
        engine.release();
        engine = newEngine;
        player = engine.getPlayer();
        resetMpvOutputRuntime();
        hardDecodeSwitchRetryArmed = next == PlayerEngine.HARD;
        playWhenReady = wasPlayWhenReady;
        callback.onPlayerRebuild(player, resetVideoSurface);
        if (result.needParse() || useParse) {
            pendingSwitchRestore = true;
            pendingSwitchPositionMs = position;
            pendingSwitchSpeed = speed;
            pendingSwitchRepeat = repeat;
            spec = inheritDetailVodId(PlaySpec.fromParse(result, key, metadata, useParse));
            bindPlaybackTrace();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch decode fresh parse decode=%d position=%d useParse=%s spec=%s", next, position, useParse, debugSpec());
            parseJob = ParseJob.create(this).start(result, useParse);
        } else {
            spec = inheritDetailVodId(PlaySpec.from(result, key, metadata));
            bindPlaybackTrace();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch decode fresh result decode=%d position=%d spec=%s", next, position, debugSpec());
            setMediaItem(Constant.TIMEOUT_PLAY);
            if (position > 0) seekTo(position);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
        }
    }

    public void togglePlayer() {
        switchPlayer(PlayerSetting.nextPlayer(playerType));
    }

    public void switchPlayer(int type) {
        switchPlayer(type, true);
    }

    public void switchPlayer(int type, PlaySpec freshSpec, long position, float speed, boolean repeat) {
        if (engine == null || player == null || freshSpec == null) return;
        beginPlaybackTrace("switch-player-fresh");
        type = PlayerSetting.sanitizePlayer(type);
        if (type == PlayerSetting.MPV && freshSpec.getDrm() != null) {
            callback.onError(ResUtil.getString(R.string.error_play_mpv_drm_unsupported));
            return;
        }
        boolean resetVideoSurface = type != playerType;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        int decode = engine.getDecode();
        PlayerEngine newEngine = buildEngineOrNull(type, decode);
        if (newEngine == null) return;
        prepareSeq++;
        resetLutRuntimeState("switch_player_fresh", true);
        stopNativeAudioSession();
        engine.release();
        engine = newEngine;
        player = engine.getPlayer();
        resetMpvOutputRuntime();
        playerType = type;
        PlayerSetting.putPlayer(type);
        spec = freshSpec;
        bindPlaybackTrace();
        playWhenReady = wasPlayWhenReady;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player fresh type=%d position=%d spec=%s", type, position, debugSpec());
        callback.onPlayerRebuild(player, resetVideoSurface);
        setMediaItem(Constant.TIMEOUT_PLAY);
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    public void switchPlayer(int type, Result result, String key, MediaMetadata metadata, boolean useParse, long position, float speed, boolean repeat) {
        if (engine == null || player == null || result == null || result.hasMsg() || result.getRealUrl().isEmpty()) return;
        beginPlaybackTrace("switch-player-result");
        type = PlayerSetting.sanitizePlayer(type);
        if (type == PlayerSetting.MPV && result.getDrm() != null) {
            callback.onError(ResUtil.getString(R.string.error_play_mpv_drm_unsupported));
            return;
        }
        boolean resetVideoSurface = type != playerType;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        int decode = engine.getDecode();
        PlayerEngine newEngine = buildEngineOrNull(type, decode);
        if (newEngine == null) return;
        prepareSeq++;
        resetLutRuntimeState("switch_player_result", true);
        stopNativeAudioSession();
        stopParse();
        engine.release();
        engine = newEngine;
        player = engine.getPlayer();
        resetMpvOutputRuntime();
        playerType = type;
        PlayerSetting.putPlayer(type);
        playWhenReady = wasPlayWhenReady;
        callback.onPlayerRebuild(player, resetVideoSurface);
        if (result.needParse() || useParse) {
            pendingSwitchRestore = true;
            pendingSwitchPositionMs = position;
            pendingSwitchSpeed = speed;
            pendingSwitchRepeat = repeat;
            spec = inheritDetailVodId(PlaySpec.fromParse(result, key, metadata, useParse));
            bindPlaybackTrace();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player fresh parse type=%d position=%d useParse=%s spec=%s", type, position, useParse, debugSpec());
            parseJob = ParseJob.create(this).start(result, useParse);
        } else {
            spec = inheritDetailVodId(PlaySpec.from(result, key, metadata));
            bindPlaybackTrace();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player fresh result type=%d position=%d spec=%s", type, position, debugSpec());
            setMediaItem(Constant.TIMEOUT_PLAY);
            if (position > 0) seekTo(position);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
        }
    }

    private void switchPlayer(int type, boolean persist) {
        if (engine == null || player == null) return;
        type = PlayerSetting.sanitizePlayer(type);
        if (type == playerType) return;
        beginPlaybackTrace("switch-player");
        // DRM 前置门：旧路先翻偏好并持久化、再释放旧引擎、最后经 setMediaItem
        // 撞门，误点一次就丢了当次播放且下次仍是 MPV。本路在切换前拦住，
        // 旧引擎与旧偏好原样保留，只给可见错。
        if (type == PlayerSetting.MPV && spec != null && spec.getDrm() != null) {
            callback.onError(ResUtil.getString(R.string.error_play_mpv_drm_unsupported));
            return;
        }
        long position = getPosition();
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        int decode = engine.getDecode();
        PlayerEngine newEngine = buildEngineOrNull(type, decode);
        if (newEngine == null) return;
        prepareSeq++;
        resetLutRuntimeState("switch_player", true);
        stopNativeAudioSession();
        engine.release();
        engine = newEngine;
        player = engine.getPlayer();
        resetMpvOutputRuntime();
        playerType = type;
        if (persist) {
            PlayerSetting.putPlayer(type);
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player type=%d persist=%s position=%d spec=%s", type, persist, position, debugSpec());
        // A Surface used by a CPU renderer stays connected after decoder release.
        // A different player needs a new Surface before its GPU/codec can connect.
        callback.onPlayerRebuild(player, true);
        if (spec == null || spec.getUrl() == null) return;
        this.playWhenReady = wasPlayWhenReady;
        if (reparseForPlayerSwitch(position, speed, repeat)) return;
        setMediaItem(Constant.TIMEOUT_PLAY);
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    private void rebuildPlayer() {
        rebuildPlayer(false);
    }

    private void rebuildPlayer(boolean resetVideoSurface) {
        stopNativeAudioSession();
        try {
            player = engine.rebuild(listener);
        } catch (Throwable e) {
            // 引擎先建后释：构建失败时旧实例未释放，player 仍指向旧实例，
            // 调用方随后 engine.start 走旧实例恢复，不悬空在已释放实例上。
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "rebuild failed, keep old player error=%s", causeChain(e));
        }
        videoEffectsActive = false;
        videoEffectsDirty = false;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        callback.onPlayerRebuild(player, resetVideoSurface);
        // 重建后手动选的副字幕无恢复点（MPV buildPlayer 只带回初始字幕面）：
        // 这里显式重放上次副字幕选择，失败不影响主流程。
        try {
            if (lastSecondarySubtitleTrack != null) engine.setSecondarySubtitleTrack(lastSecondarySubtitleTrack);
        } catch (Throwable ignored) {
        }
    }

    private void applyMpvHlsInitialControl(
            MpvPlayerEngine mpv,
            PlaybackAutoContext context,
            boolean automatic,
            long now) {
        String networkIdentity = PlaybackSystemConditionMonitor.process()
                .currentNetworkIdentityDigest();
        PlaybackThroughputHistory.Match history = PlaybackThroughputHistory
                .process().lookup(context, networkIdentity, now);
        MpvHlsVariantPolicy.InitialAssessment assessment =
                MpvHlsVariantPolicy.resolveInitial(
                        automatic,
                        isMpv(),
                        MpvPerformanceSetting.isPerformancePriority(),
                        history);
        MpvHlsVariantController.Snapshot before =
                mpvHlsVariantController.snapshot();
        MpvHlsVariantController.Decision decision =
                mpvHlsVariantController.evaluateInitial(
                        playbackAutoSession,
                        context == null ? PlaybackAutoContext.SessionToken.none()
                                : context.session(),
                        assessment);
        boolean pendingContextRestore = !decision.requestsApply()
                && decision.reason()
                == MpvHlsVariantController.Reason.ACTION_PENDING
                && before.state() != MpvHlsVariantController.State.APPLYING
                && assessment.active()
                && !TextUtils.isEmpty(before.targetOption());
        String requestedOption = pendingContextRestore
                ? before.targetOption() : decision.targetOption();
        boolean started = false;
        MpvPlayer.AutoHlsBitrateResult result =
                MpvPlayer.AutoHlsBitrateResult.REJECTED;
        if (pendingContextRestore) {
            result = mpv.applyAutoHlsBitrate(
                    playbackTrace.current(), requestedOption);
        } else if (decision.requestsApply()) {
            started = mpvHlsVariantController.beginApply(
                    playbackAutoSession, decision, now);
            if (started) {
                result = mpv.applyAutoHlsBitrate(
                        playbackTrace.current(), decision.targetOption());
                mpvHlsVariantController.completeApply(
                        playbackAutoSession,
                        decision,
                        result.accepted(),
                        result.staged(),
                        now);
            }
        } else if (decision.reason()
                != MpvHlsVariantController.Reason.ACTION_PENDING) {
            mpv.clearAutoHlsBitrate();
            if (!automatic
                    || assessment.reason()
                    == MpvHlsVariantPolicy.Reason.CONFIG_PRIORITY) {
                mpvHlsVariantController.suppress(playbackAutoSession);
            }
        }
        PlaybackTelemetry.DecisionOutcome outcome =
                !automatic
                        || assessment.reason()
                        == MpvHlsVariantPolicy.Reason.CONFIG_PRIORITY
                        ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                        : pendingContextRestore && !result.accepted()
                        ? PlaybackTelemetry.DecisionOutcome.FAILED
                        : pendingContextRestore && result.staged()
                        ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                        : pendingContextRestore
                        ? PlaybackTelemetry.DecisionOutcome.APPLIED
                        : !decision.requestsApply()
                        ? PlaybackTelemetry.DecisionOutcome.HELD
                        : !started || !result.accepted()
                        ? PlaybackTelemetry.DecisionOutcome.FAILED
                        : result.staged()
                        ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                        : PlaybackTelemetry.DecisionOutcome.APPLIED;
        String suppression = !automatic
                ? "not-automatic"
                : assessment.reason() == MpvHlsVariantPolicy.Reason.CONFIG_PRIORITY
                ? "mpv-conf-priority"
                : pendingContextRestore && !result.accepted()
                ? "native-restore-failed"
                : pendingContextRestore
                ? "none"
                : decision.requestsApply() && !started
                ? "action-rejected"
                : decision.requestsApply() && !result.accepted()
                ? "native-apply-failed"
                : "none";
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "ceiling_bps", assessment.ceilingBitsPerSecond(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(assessment.trustedThroughputBitsPerSecond() > 0
                ? PlaybackTelemetry.DecisionInput.number(
                "trusted_throughput_bps",
                assessment.trustedThroughputBitsPerSecond(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                assessment.confidence())
                : PlaybackTelemetry.DecisionInput.unknown(
                "trusted_throughput_bps"));
        inputs.add(history.usable()
                ? PlaybackTelemetry.DecisionInput.number(
                "evidence_age_ms", history.ageMs(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                history.confidence())
                : PlaybackTelemetry.DecisionInput.unknown("evidence_age_ms"));
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "history_reason", history.reason().label(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(assessment.pathKind() == PlaybackAutoContext.PathKind.UNKNOWN
                ? PlaybackTelemetry.DecisionInput.unknown("path")
                : PlaybackTelemetry.DecisionInput.text(
                "path", assessment.pathKind().label(),
                PlaybackAutoContext.ValueSource.ROUTE_CLASSIFIER,
                assessment.confidence()));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "native_readbacks",
                mpv.getAutoHlsRuntimeSnapshot().observedCount(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_HLS_VARIANT,
                        outcome,
                        hlsOptionLabel(before.targetOption()),
                        hlsOptionLabel(requestedOption),
                        result.accepted()
                                ? hlsOptionLabel(requestedOption)
                                : hlsOptionLabel(before.targetOption()),
                        decision.reason().label(),
                        suppression,
                        inputs),
                now);
    }

    private void applyMpvAutoInitialControl() {
        if (!(engine instanceof MpvPlayerEngine mpv) || !playbackAutoSession.active()) return;
        long now = SystemClock.elapsedRealtime();
        PlaybackAutoContext context = playbackAutoContextStore.snapshot();
        boolean automaticForward = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.MPV,
                PlaybackPerformanceCatalog.BUFFER_BYTES);
        boolean automaticBack = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.MPV,
                PlaybackPerformanceCatalog.BACK_BUFFER);
        boolean automatic = automaticForward || automaticBack;
        applyMpvHlsInitialControl(
                mpv,
                context,
                PlaybackPerformanceSetting.isAuto(
                        PlayerSetting.MPV,
                        PlaybackPerformanceCatalog.MPV_HLS_BITRATE),
                now);
        MpvAutoControlPolicy.Request request = MpvAutoControlPolicy.requestFrom(
                context,
                automatic,
                isMpv(),
                MpvPerformanceSetting.isPerformancePriority(),
                now);
        MpvAutoControlPolicy.Decision decision = mpvAutoController.evaluate(
                playbackAutoSession,
                context.session(),
                request);
        MpvAutoController.Snapshot before = mpvAutoController.snapshot();
        if (!automatic) {
            mpv.clearAutoCacheBaseline();
            mpvForwardCacheController.suppress(playbackAutoSession);
            mpvBackCacheController.suppress(playbackAutoSession);
            mpvCacheTargetCoordinator.suppress(playbackAutoSession);
            evaluateMpvCaches(
                    null,
                    null,
                    MpvBackCachePolicy.SeekObservation.none(),
                    now);
            return;
        }

        boolean started = false;
        boolean applied = false;
        boolean staged = false;
        String applyResult = "not-requested";
        MpvForwardCacheController.Trigger forwardTrigger = null;
        MpvBackCacheController.Trigger backTrigger = null;
        long targetForwardBytes = automaticForward
                ? decision.forwardBytes() : mpv.getConfiguredForwardCacheBytes();
        long targetBackBytes = automaticBack
                ? decision.backBytes() : mpv.getConfiguredBackCacheBytes();
        if (targetBackBytes > targetForwardBytes) {
            if (automaticForward && !automaticBack) {
                targetForwardBytes = targetBackBytes;
            } else if (!automaticForward && automaticBack) {
                targetBackBytes = targetForwardBytes;
            }
        }
        if (decision.requestsApply()) {
            started = mpvAutoController.beginApply(playbackAutoSession, decision);
            MpvPlayer.AutoCacheBaselineResult result = started
                    ? mpv.applyAutoCacheBaseline(
                    playbackTrace.current(), targetForwardBytes, targetBackBytes)
                    : MpvPlayer.AutoCacheBaselineResult.REJECTED;
            applied = result.accepted();
            staged = result.staged();
            applyResult = result.label();
            if (started) {
                mpvAutoController.completeApply(
                        playbackAutoSession, decision, applied, staged);
            }
        } else {
            mpv.clearAutoCacheBaseline();
            mpvForwardCacheController.suppress(playbackAutoSession);
            mpvBackCacheController.suppress(playbackAutoSession);
            mpvCacheTargetCoordinator.suppress(playbackAutoSession);
        }
        if (applied) {
            MpvForwardCacheController.Snapshot forwardBefore = mpvForwardCacheController.snapshot();
            MpvBackCacheController.Snapshot backBefore = mpvBackCacheController.snapshot();
            boolean preserveForwardTarget = forwardBefore.baselineInitialized();
            boolean preserveBackTarget = backBefore.baselineInitialized();
            mpvForwardCacheController.recordBaseline(
                    playbackAutoSession, targetForwardBytes, preserveForwardTarget);
            mpvBackCacheController.recordBaseline(
                    playbackAutoSession, targetBackBytes, preserveBackTarget);
            mpvCacheTargetCoordinator.recordBaseline(
                    playbackAutoSession, targetForwardBytes, targetBackBytes);
            forwardTrigger = !automaticForward ? null : preserveForwardTarget
                    ? MpvForwardCacheController.Trigger.REBUILD
                    : MpvForwardCacheController.Trigger.BASELINE;
            backTrigger = !automaticBack ? null : preserveBackTarget
                    ? MpvBackCacheController.Trigger.REBUILD
                    : MpvBackCacheController.Trigger.BASELINE;
        }
        MpvAutoController.Snapshot after = mpvAutoController.snapshot();
        PlaybackTelemetry.DecisionOutcome outcome =
                decision.reason() == MpvAutoControlPolicy.Reason.CONFIG_PRIORITY
                        ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                        : !decision.requestsApply()
                        ? PlaybackTelemetry.DecisionOutcome.HELD
                        : staged
                        ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                        : applied
                        ? PlaybackTelemetry.DecisionOutcome.APPLIED
                        : PlaybackTelemetry.DecisionOutcome.FAILED;
        String oldValue = before.appliedForwardBytes() >= 0
                ? "forward-" + before.appliedForwardBytes()
                + "-back-" + before.appliedBackBytes()
                : "startup-baseline";
        String resultValue = applied ? decision.targetLabel() : oldValue;
        String suppression = decision.reason() == MpvAutoControlPolicy.Reason.CONFIG_PRIORITY
                ? "mpv-conf-priority"
                : decision.requestsApply() && !started
                ? "action-rejected"
                : decision.requestsApply() && !applied
                ? "native-apply-failed"
                : "none";

        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(decision.requestsApply()
                ? PlaybackTelemetry.DecisionInput.number(
                "forward_bytes", decision.forwardBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("forward_bytes"));
        inputs.add(decision.requestsApply()
                ? PlaybackTelemetry.DecisionInput.number(
                "back_bytes", decision.backBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("back_bytes"));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "performance_priority", request.performancePriority(),
                PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(request.pressureUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "memory_pressure", request.memoryPressure().label(),
                context.device().memoryPressure().source(),
                context.device().memoryPressure().confidence())
                : PlaybackTelemetry.DecisionInput.unknown("memory_pressure"));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "memory_snapshot_usable", request.snapshotUsable(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(request.snapshotUsable()
                && request.memorySnapshot().lowRamDevice() != null
                ? PlaybackTelemetry.DecisionInput.bool(
                "low_ram", request.memorySnapshot().lowRamDevice(),
                context.device().memorySnapshot().source(),
                context.device().memorySnapshot().confidence())
                : PlaybackTelemetry.DecisionInput.unknown("low_ram"));
        inputs.add(request.protocolUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "protocol", request.protocol().label(),
                context.resource().protocol().source(),
                context.resource().protocol().confidence())
                : PlaybackTelemetry.DecisionInput.unknown("protocol"));
        inputs.add(request.streamKindUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "stream", request.streamKind().label(),
                context.resource().streamKind().source(),
                context.resource().streamKind().confidence())
                : PlaybackTelemetry.DecisionInput.unknown("stream"));
        inputs.add(request.playerPathUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "player_path", request.playerPath().label(),
                context.path().playerPath().source(),
                context.path().playerPath().confidence())
                : PlaybackTelemetry.DecisionInput.unknown("player_path"));
        inputs.add(request.upstreamPathUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "upstream_path", request.upstreamPath().label(),
                context.path().upstreamPath().source(),
                context.path().upstreamPath().confidence())
                : PlaybackTelemetry.DecisionInput.unknown("upstream_path"));
        inputs.add(request.upstreamStateUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "upstream_state", request.upstreamState().label(),
                context.path().upstreamState().source(),
                context.path().upstreamState().confidence())
                : PlaybackTelemetry.DecisionInput.unknown("upstream_state"));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "apply_attempts", after.applyAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));

        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_CACHE,
                        outcome,
                        oldValue,
                        decision.targetLabel(),
                        resultValue,
                        decision.reason().label(),
                        suppression,
                        inputs),
                now);
        PlaybackTrace.log("mpv-auto", playbackTrace.current(),
                "state=%s action=%s reason=%s forwardBytes=%d backBytes=%d capped=%s attempts=%d result=%s",
                after.state().label(),
                decision.action().label(),
                decision.reason().label(),
                decision.forwardBytes(),
                decision.backBytes(),
                decision.capped(),
                after.applyAttempts(),
                decision.requestsApply() ? applyResult : outcome.label());
        if (forwardTrigger != null || backTrigger != null) {
            evaluateMpvCaches(forwardTrigger, backTrigger,
                    MpvBackCachePolicy.SeekObservation.none(), now);
        } else {
            evaluateMpvCaches(
                    null,
                    null,
                    MpvBackCachePolicy.SeekObservation.none(),
                    now);
        }
    }

    private void onMpvResourceMemoryUpdate(PlaybackMemoryCoordinator.Update update) {
        if (update == null || !playbackAutoSession.active()
                || !playbackAutoSession.equals(update.session())) return;
        evaluateMpvCaches(
                MpvForwardCacheController.Trigger.MEMORY,
                MpvBackCacheController.Trigger.MEMORY,
                MpvBackCachePolicy.SeekObservation.none(),
                SystemClock.elapsedRealtime());
    }

    private void onMpvResourceSystemUpdate(
            PlaybackSystemConditionCoordinator.Update update) {
        if (update == null || !playbackAutoSession.active()
                || !playbackAutoSession.equals(update.session())) return;
        evaluateMpvCaches(
                MpvForwardCacheController.Trigger.RESOURCE,
                MpvBackCacheController.Trigger.RESOURCE,
                MpvBackCachePolicy.SeekObservation.none(),
                SystemClock.elapsedRealtime());
    }

    private void evaluateMpvCaches(
            MpvForwardCacheController.Trigger forwardTrigger,
            MpvBackCacheController.Trigger backTrigger,
            MpvBackCachePolicy.SeekObservation seekObservation,
            long nowElapsedMs) {
        if (!(engine instanceof MpvPlayerEngine mpv) || !playbackAutoSession.active()) return;
        long now = Math.max(0, nowElapsedMs);
        PlaybackAutoContext context = playbackAutoContextStore.snapshot();
        boolean automaticForward = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.MPV,
                PlaybackPerformanceCatalog.BUFFER_BYTES);
        boolean automaticBack = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.MPV,
                PlaybackPerformanceCatalog.BACK_BUFFER);
        boolean automaticPreload = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.MPV,
                PlaybackPerformanceCatalog.PRELOAD);
        boolean automatic = automaticForward || automaticBack || automaticPreload;
        boolean performancePriority = MpvPerformanceSetting.isPerformancePriority();
        MpvForwardCacheController.Snapshot forwardBefore = mpvForwardCacheController.snapshot();
        MpvBackCacheController.Snapshot backBefore = mpvBackCacheController.snapshot();
        long baseline = forwardBefore.initialBaselineBytes() > 0
                ? forwardBefore.initialBaselineBytes() : MpvForwardCachePolicy.MIN_FORWARD_BYTES;
        long resourceForward = forwardBefore.controlledTargetBytes() > 0
                ? forwardBefore.controlledTargetBytes() : baseline;
        long resourceBack = backBefore.controlledTargetBytes() >= 0
                ? backBefore.controlledTargetBytes() : 0;
        MpvResourcePressurePolicy.Assessment resourceAssessment =
                MpvResourcePressurePolicy.assess(
                        context,
                        automatic,
                        isMpv(),
                        performancePriority,
                        now);
        MpvResourcePressureController.Snapshot resourceBefore =
                mpvResourcePressureController.snapshot();
        MpvResourcePressureController.Trigger resourceTrigger =
                mpvResourceTrigger(forwardTrigger, backTrigger);
        MpvResourcePressureController.Decision resourceDecision =
                mpvResourcePressureController.evaluate(
                        playbackAutoSession,
                        context.session(),
                        resourceAssessment,
                        resourceTrigger,
                        resourceForward,
                        resourceBack,
                        now);
        boolean resourcePreloadChanged = resourceBefore.preloadAllowed()
                != resourceDecision.preloadAllowed();
        publishMpvResourcePressureDecision(
                resourceBefore,
                resourceAssessment,
                resourceDecision,
                resourcePreloadChanged,
                now);
        evaluateMpvPreload(mpv, context, resourceDecision, automaticPreload,
                performancePriority, now);
        MpvForwardCachePolicy.Assessment forwardAssessment = MpvForwardCachePolicy.assess(
                context,
                automaticForward,
                isMpv(),
                performancePriority,
                baseline,
                now);
        MpvForwardCacheController.Decision forwardDecision = !automaticForward
                || forwardTrigger == null
                ? null : mpvForwardCacheController.evaluate(
                playbackAutoSession, context.session(), forwardAssessment, resourceDecision,
                forwardTrigger, now);
        MpvForwardCacheController.Snapshot forwardEvaluated =
                mpvForwardCacheController.snapshot();
        long currentForward = !automaticForward
                ? mpv.getConfiguredForwardCacheBytes()
                : forwardEvaluated.controlledTargetBytes() > 0
                ? forwardEvaluated.controlledTargetBytes() : baseline;
        long targetForward = forwardDecision != null && forwardDecision.requestsApply()
                ? forwardDecision.targetBytes() : currentForward;

        boolean seekableForBack = isCurrentMpvMediaSeekable()
                || backTrigger == MpvBackCacheController.Trigger.REBUILD
                && backBefore.controlledTargetBytes() > 0;
        MpvBackCachePolicy.Request backRequest = MpvBackCachePolicy.requestFrom(
                context,
                automaticBack,
                isMpv(),
                performancePriority,
                seekableForBack,
                isCurrentMpvMediaLive(),
                targetForward,
                forwardAssessment,
                now);
        MpvBackCachePolicy.Assessment backAssessment =
                MpvBackCachePolicy.resolve(backRequest);
        MpvBackCacheController.Decision backDecision = !automaticBack
                || backTrigger == null
                ? null : mpvBackCacheController.evaluate(
                playbackAutoSession, context.session(), backAssessment, resourceDecision,
                backTrigger, seekObservation, now);
        MpvBackCacheController.Snapshot backEvaluated = mpvBackCacheController.snapshot();
        long currentBack = !automaticBack
                ? mpv.getConfiguredBackCacheBytes()
                : backEvaluated.controlledTargetBytes() >= 0
                ? backEvaluated.controlledTargetBytes() : 0;
        long targetBack = backDecision != null && backDecision.requestsApply()
                ? backDecision.targetBytes() : currentBack;
        if (automaticForward && !automaticBack && targetBack > targetForward) {
            targetForward = targetBack;
        } else if (!automaticForward && automaticBack
                && targetBack > targetForward) {
            targetBack = targetForward;
        }

        boolean cacheExpansionAllowed = experimentAllowed(
                PlaybackExperimentPolicy.Action.MPV_CACHE_EXPANSION);
        boolean forwardPolicyRequested = forwardDecision != null
                && forwardDecision.requestsApply();
        boolean backPolicyRequested = backDecision != null
                && backDecision.requestsApply();
        boolean forwardRequested = forwardPolicyRequested
                && (targetForward <= currentForward || cacheExpansionAllowed);
        boolean backRequested = backPolicyRequested
                && (targetBack <= currentBack || cacheExpansionAllowed);
        boolean expansionSuppressed = forwardPolicyRequested && !forwardRequested
                || backPolicyRequested && !backRequested;
        if (forwardPolicyRequested && !forwardRequested) {
            targetForward = currentForward;
        }
        if (backPolicyRequested && !backRequested) {
            targetBack = currentBack;
        }
        boolean applyRequested = forwardRequested || backRequested;
        boolean forwardStarted = !forwardRequested;
        boolean backStarted = !backRequested;
        boolean coordinatorStarted = false;
        boolean adopted = false;
        boolean accepted = false;
        boolean staged = false;
        String applyResult = expansionSuppressed
                ? "experiment-disabled" : "not-requested";
        MpvCacheTargetCoordinator.Decision combinedDecision = null;
        if (applyRequested) {
            combinedDecision = mpvCacheTargetCoordinator.evaluate(
                    playbackAutoSession, targetForward, targetBack);
            if (forwardRequested) {
                forwardStarted = mpvForwardCacheController.beginApply(
                        playbackAutoSession, forwardDecision);
            }
            if (backRequested) {
                backStarted = mpvBackCacheController.beginApply(
                        playbackAutoSession, backDecision);
            }
            boolean controllersStarted = forwardStarted && backStarted;
            if (controllersStarted && combinedDecision.requestsApply()) {
                coordinatorStarted = mpvCacheTargetCoordinator.beginApply(
                        playbackAutoSession, combinedDecision);
                MpvPlayer.AutoCacheBaselineResult result = coordinatorStarted
                        ? mpv.applyAutoCacheBaseline(
                        playbackTrace.current(), targetForward, targetBack)
                        : MpvPlayer.AutoCacheBaselineResult.REJECTED;
                accepted = result.accepted();
                staged = result.staged();
                applyResult = result.label();
                if (coordinatorStarted) {
                    mpvCacheTargetCoordinator.completeApply(
                            playbackAutoSession, combinedDecision, accepted, staged);
                }
            } else if (controllersStarted
                    && combinedDecision.reason()
                    == MpvCacheTargetCoordinator.Reason.TARGET_STABLE) {
                adopted = true;
                accepted = true;
                applyResult = "already-applied";
            } else if (!controllersStarted) {
                applyResult = "controller-rejected";
            } else {
                applyResult = combinedDecision.reason().label();
            }
            boolean controllerAccepted = accepted && (coordinatorStarted || adopted);
            if (forwardRequested && forwardStarted) {
                mpvForwardCacheController.completeApply(
                        playbackAutoSession, forwardDecision,
                        controllerAccepted, staged, now);
            }
            if (backRequested && backStarted) {
                mpvBackCacheController.completeApply(
                        playbackAutoSession, backDecision,
                        controllerAccepted, staged, now);
            }
            if (controllerAccepted) {
                mpvForwardCacheController.syncNativeTarget(
                        playbackAutoSession, targetForward);
                mpvBackCacheController.syncNativeTarget(
                        playbackAutoSession, targetBack);
            }
        }

        MpvForwardCacheController.Snapshot forwardAfter = mpvForwardCacheController.snapshot();
        MpvBackCacheController.Snapshot backAfter = mpvBackCacheController.snapshot();
        PlayerCacheState cache = mpv.getAutoCacheSnapshot();
        boolean commitStarted = coordinatorStarted || adopted;
        String forwardApplyResult = forwardPolicyRequested && !forwardRequested
                ? "experiment-disabled" : applyResult;
        String backApplyResult = backPolicyRequested && !backRequested
                ? "experiment-disabled" : applyResult;
        if (forwardDecision != null) {
            publishMpvForwardCacheDecision(
                    forwardTrigger,
                    forwardAssessment,
                    forwardDecision,
                    forwardAfter,
                    cache,
                    context,
                    now,
                    forwardRequested && forwardStarted && commitStarted,
                    accepted,
                    staged,
                    forwardApplyResult);
        }
        if (backDecision != null) {
            publishMpvBackCacheDecision(
                    backTrigger,
                    backAssessment,
                    backDecision,
                    backAfter,
                    cache,
                    context,
                    now,
                    backRequested && backStarted && commitStarted,
                    accepted,
                    staged,
                    backApplyResult);
        }
        if (applyRequested || expansionSuppressed) {
            PlaybackTrace.log("mpv-cache-target", playbackTrace.current(),
                    "forward=%d back=%d totalBudget=%d forwardChanged=%s backChanged=%s coordinator=%s result=%s",
                    targetForward, targetBack, backAssessment.totalBudgetBytes(),
                    forwardRequested, backRequested,
                    combinedDecision == null ? "none" : combinedDecision.reason().label(),
                    applyResult);
        }
    }

    private void evaluateMpvPreload(
            MpvPlayerEngine mpv,
            PlaybackAutoContext context,
            MpvResourcePressureController.Decision resourceDecision,
            boolean automatic,
            boolean performancePriority,
            long now) {
        boolean experimentalAutomatic = automatic && experimentAllowed(
                PlaybackExperimentPolicy.Action.MPV_AUTO_PRELOAD);
        MpvPlayer.AutoHlsRuntimeSnapshot hls = mpv.getAutoHlsRuntimeSnapshot();
        MpvPlayer.AutoHlsPreloadRuntimeSnapshot proxy =
                mpv.getAutoHlsPreloadRuntimeSnapshot();
        PlaybackAutoContext.Fact<PlaybackAutoContext.Protocol> protocolFact =
                context.resource().protocol();
        PlaybackAutoContext.Fact<PlaybackAutoContext.StreamKind> streamFact =
                context.resource().streamKind();
        PlaybackAutoContext.Fact<PlaybackAutoContext.PathKind> playerPathFact =
                context.path().playerPath();
        PlaybackAutoContext.Fact<PlaybackAutoContext.PathKind> upstreamPathFact =
                context.path().upstreamPath();
        PlaybackAutoContext.Fact<PlaybackAutoContext.UpstreamState> upstreamStateFact =
                context.path().upstreamState();
        PlaybackAutoContext.Fact<Long> bufferFact =
                context.runtime().bufferedDurationMs();
        PlaybackAutoContext.Fact<Integer> rebufferFact =
                context.runtime().rebufferCount();
        PlaybackAutoContext.Fact<Long> positionFact =
                context.runtime().positionMs();
        boolean protocolUsable = protocolFact.isUsable(now);
        boolean streamUsable = streamFact.isUsable(now);
        PlaybackAutoContext.StreamKind streamKind = streamUsable
                ? streamFact.value() : PlaybackAutoContext.StreamKind.UNKNOWN;
        if (streamKind == PlaybackAutoContext.StreamKind.VOD && !proxy.vod()) {
            streamUsable = false;
            streamKind = PlaybackAutoContext.StreamKind.UNKNOWN;
        }
        boolean bufferUsable = bufferFact.isUsable(now);
        long selectedBits = hls.selectedVariant() == null
                ? 0 : hls.selectedVariant().selectionBitsPerSecond();
        if (selectedBits <= 0 && hls.variants().size() == 1) {
            selectedBits = hls.variants().get(0).selectionBitsPerSecond();
        }
        if (selectedBits <= 0) {
            PlaybackAutoContext.Fact<Long> mediaBitrateFact =
                    context.runtime().mediaBitrateBitsPerSecond();
            if (mediaBitrateFact.isUsable(now) && mediaBitrateFact.value() > 0) {
                selectedBits = mediaBitrateFact.value();
            }
        }
        boolean buffering = player != null
                && player.getPlaybackState() == Player.STATE_BUFFERING;
        MpvPreloadPolicy.Request request = new MpvPreloadPolicy.Request(
                experimentalAutomatic,
                isMpv(),
                performancePriority,
                proxy.preloadConfigured(),
                protocolUsable ? protocolFact.value()
                        : PlaybackAutoContext.Protocol.UNKNOWN,
                protocolUsable,
                streamKind,
                streamUsable,
                playerPathFact.isUsable(now) ? playerPathFact.value()
                        : PlaybackAutoContext.PathKind.UNKNOWN,
                playerPathFact.isUsable(now),
                upstreamPathFact.isUsable(now) ? upstreamPathFact.value()
                        : PlaybackAutoContext.PathKind.UNKNOWN,
                upstreamPathFact.isUsable(now),
                upstreamStateFact.isUsable(now) ? upstreamStateFact.value()
                        : PlaybackAutoContext.UpstreamState.UNKNOWN,
                upstreamStateFact.isUsable(now),
                resourceDecision.preloadAllowed(),
                proxy.cacheEnabled(),
                proxy.cacheStorageKnown(),
                proxy.cacheBudgetAvailable(),
                proxy.cacheCircuitOpen(),
                proxy.upstreamBitsPerSecond(),
                proxy.throughputKnown(),
                proxy.throughputFresh(),
                proxy.throughputSampleAtElapsedMs(),
                selectedBits,
                bufferUsable,
                bufferUsable ? bufferFact.value() : 0,
                bufferUsable ? bufferFact.sampledAtElapsedMs() : -1,
                rebufferFact.hasValue() ? rebufferFact.value() : 0,
                buffering,
                false,
                false,
                proxy.foregroundRequests(),
                context.revision());
        MpvPreloadController.Snapshot before = mpvPreloadController.snapshot();
        MpvPreloadController.Decision decision = mpvPreloadController.evaluate(
                playbackAutoSession, context.session(), request, now);
        boolean automaticPreloadManaged = MpvPreloadPolicy.ownsProxyControl(
                automatic, performancePriority);
        boolean gateChanged = mpv.updateAutomaticPreloadControl(
                automaticPreloadManaged,
                experimentalAutomatic && resourceDecision.preloadAllowed(),
                experimentalAutomatic && decision.preloadAllowed());
        boolean scheduled = false;
        if (experimentalAutomatic && automaticPreloadManaged
                && decision.preloadAllowed()
                && positionFact.isUsable(now)) {
            mpv.requestAutomaticHlsPreload(Math.max(0, positionFact.value()));
            scheduled = true;
        }
        publishMpvPreloadDecision(
                before, decision, request, proxy, gateChanged, scheduled, now);
    }

    private void publishMpvPreloadDecision(
            MpvPreloadController.Snapshot before,
            MpvPreloadController.Decision decision,
            MpvPreloadPolicy.Request request,
            MpvPlayer.AutoHlsPreloadRuntimeSnapshot proxy,
            boolean gateChanged,
            boolean scheduled,
            long now) {
        PlaybackTelemetry.DecisionOutcome outcome;
        if (decision.policyReason() == MpvPreloadPolicy.Reason.CONFIG_PRIORITY
                || decision.policyReason() == MpvPreloadPolicy.Reason.NOT_AUTOMATIC) {
            outcome = PlaybackTelemetry.DecisionOutcome.SUPPRESSED;
        } else if (gateChanged || decision.cancellationRequested()
                || decision.action() == MpvPreloadController.Action.ALLOW) {
            outcome = PlaybackTelemetry.DecisionOutcome.REQUESTED;
        } else if (decision.changed()) {
            outcome = PlaybackTelemetry.DecisionOutcome.OBSERVED;
        } else {
            outcome = PlaybackTelemetry.DecisionOutcome.HELD;
        }
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        addNumberInput(inputs, "upstream_bps",
                request.throughputKnown() ? request.upstreamBitsPerSecond() : -1,
                PlaybackAutoContext.ValueSource.PROXY,
                request.throughputKnown()
                        ? PlaybackAutoContext.Confidence.MEDIUM
                        : PlaybackAutoContext.Confidence.UNKNOWN);
        addNumberInput(inputs, "throughput_age_ms",
                request.throughputKnown() ? proxy.throughputAgeMs() : -1,
                PlaybackAutoContext.ValueSource.PROXY,
                request.throughputKnown()
                        ? PlaybackAutoContext.Confidence.MEDIUM
                        : PlaybackAutoContext.Confidence.UNKNOWN);
        addNumberInput(inputs, "selected_bps",
                request.selectedBitsPerSecond() > 0
                        ? request.selectedBitsPerSecond() : -1,
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                request.selectedBitsPerSecond() > 0
                        ? PlaybackAutoContext.Confidence.HIGH
                        : PlaybackAutoContext.Confidence.UNKNOWN);
        addNumberInput(inputs, "ratio_permille", decision.ratioPermille(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "buffer_ms",
                request.bufferUsable() ? request.bufferedDurationMs() : -1,
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                request.bufferUsable()
                        ? PlaybackAutoContext.Confidence.HIGH
                        : PlaybackAutoContext.Confidence.UNKNOWN);
        addNumberInput(inputs, "foreground", request.foregroundRequests(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "cache_physical", proxy.cachePhysicalBytes(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "cache_reserved", proxy.cacheReservedBytes(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "cache_write_budget",
                proxy.cacheNewWriteBudgetBytes(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "cache_effective",
                proxy.cacheEffectiveCapacityBytes(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "recovery_samples", decision.recoverySamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "recovery_ms", decision.recoveryRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "throughput_filter",
                proxy.lastThroughputRejectReason(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "resource_allowed", request.resourcePreloadAllowed(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "cache_budget", request.cacheBudgetAvailable(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "cache_circuit", request.cacheCircuitOpen(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_PRELOAD,
                        outcome,
                        before.lastDecision().targetLabel(),
                        decision.targetLabel(),
                        scheduled ? "scheduled" : gateChanged
                                ? "gate-updated" : "held",
                        decision.policyReason().label(),
                        decision.reason().label(),
                        inputs),
                now);
        PlaybackTrace.log("mpv-preload", playbackTrace.current(),
                "state=%s action=%s reason=%s policy=%s threads=%d ratio=%d upstream=%d selected=%d buffer=%d foreground=%d cachePhysical=%d cacheReserved=%d cacheBudget=%d cacheEffective=%d circuit=%s recoverySamples=%d recoveryMs=%d gateChanged=%s scheduled=%s",
                decision.state().label(), decision.action().label(),
                decision.reason().label(), decision.policyReason().label(),
                decision.concurrency(), decision.ratioPermille(),
                request.upstreamBitsPerSecond(), request.selectedBitsPerSecond(),
                request.bufferedDurationMs(), request.foregroundRequests(),
                proxy.cachePhysicalBytes(), proxy.cacheReservedBytes(),
                proxy.cacheNewWriteBudgetBytes(),
                proxy.cacheEffectiveCapacityBytes(), proxy.cacheCircuitOpen(),
                decision.recoverySamples(), decision.recoveryRemainingMs(),
                gateChanged, scheduled);
    }

    private static MpvResourcePressureController.Trigger mpvResourceTrigger(
            MpvForwardCacheController.Trigger forwardTrigger,
            MpvBackCacheController.Trigger backTrigger) {
        if (forwardTrigger == MpvForwardCacheController.Trigger.RESOURCE
                || backTrigger == MpvBackCacheController.Trigger.RESOURCE) {
            return MpvResourcePressureController.Trigger.SYSTEM;
        }
        if (forwardTrigger == MpvForwardCacheController.Trigger.MEMORY
                || backTrigger == MpvBackCacheController.Trigger.MEMORY) {
            return MpvResourcePressureController.Trigger.MEMORY;
        }
        if (forwardTrigger == MpvForwardCacheController.Trigger.REBUILD
                || backTrigger == MpvBackCacheController.Trigger.REBUILD) {
            return MpvResourcePressureController.Trigger.REBUILD;
        }
        if (forwardTrigger == MpvForwardCacheController.Trigger.BASELINE
                || backTrigger == MpvBackCacheController.Trigger.BASELINE) {
            return MpvResourcePressureController.Trigger.BASELINE;
        }
        return MpvResourcePressureController.Trigger.RUNTIME;
    }

    private void publishMpvResourcePressureDecision(
            MpvResourcePressureController.Snapshot before,
            MpvResourcePressurePolicy.Assessment assessment,
            MpvResourcePressureController.Decision decision,
            boolean preloadGateChanged,
            long now) {
        MpvResourcePressureController.Snapshot previous = before == null
                ? mpvResourcePressureController.snapshot() : before;
        PlaybackTelemetry.DecisionOutcome outcome = !assessment.active()
                && assessment.reason() == MpvResourcePressurePolicy.Reason.CONFIG_PRIORITY
                ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                : decision.changed() || preloadGateChanged
                ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                : decision.action() == MpvResourcePressureController.Action.STABLE
                ? PlaybackTelemetry.DecisionOutcome.OBSERVED
                : PlaybackTelemetry.DecisionOutcome.HELD;
        String oldValue = mpvResourceTargetLabel(
                previous.forwardCeilingBytes(),
                previous.backCeilingBytes(),
                previous.preloadAllowed());
        String targetValue = decision.targetLabel();
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "level",
                assessment.level().label(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(assessment.memoryUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "memory_pressure",
                assessment.memoryPressure().label(),
                PlaybackAutoContext.ValueSource.SYSTEM_API,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("memory_pressure"));
        inputs.add(assessment.thermalUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "thermal",
                assessment.thermal().label(),
                PlaybackAutoContext.ValueSource.SYSTEM_API,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("thermal"));
        inputs.add(assessment.powerUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "power",
                assessment.power().label(),
                PlaybackAutoContext.ValueSource.SYSTEM_API,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("power"));
        inputs.add(assessment.networkCostUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "network_cost",
                assessment.networkCost().label(),
                PlaybackAutoContext.ValueSource.SYSTEM_API,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("network_cost"));
        inputs.add(assessment.networkSnapshotUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "data_saver",
                assessment.networkSnapshot().dataSaverState().label(),
                PlaybackAutoContext.ValueSource.SYSTEM_API,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("data_saver"));
        addNumberInput(inputs, "forward_ceiling", decision.forwardCeilingBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "back_ceiling", decision.backCeilingBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "expansion_allowed",
                decision.expansionAllowed(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "preload_allowed",
                decision.preloadAllowed(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "preload_gate_changed",
                preloadGateChanged,
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        addNumberInput(inputs, "normal_samples", decision.normalSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "cooldown_ms", decision.cooldownRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_RESOURCE_PRESSURE,
                        outcome,
                        oldValue,
                        targetValue,
                        targetValue,
                        decision.policyReason().label(),
                        decision.reason().label(),
                        inputs),
                now);
        PlaybackTrace.log("mpv-resource-pressure", playbackTrace.current(),
                "state=%s trigger=%s input=%s action=%s reason=%s policy=%s forwardCeiling=%d backCeiling=%d expansion=%s preload=%s gateChanged=%s normalSamples=%d cooldown=%d",
                mpvResourcePressureController.snapshot().state().label(),
                decision.trigger().label(),
                decision.inputLevel().label(),
                decision.action().label(),
                decision.reason().label(),
                decision.policyReason().label(),
                decision.forwardCeilingBytes(),
                decision.backCeilingBytes(),
                decision.expansionAllowed(),
                decision.preloadAllowed(),
                preloadGateChanged,
                decision.normalSamples(),
                decision.cooldownRemainingMs());
    }

    private static String mpvResourceTargetLabel(
            long forwardBytes,
            long backBytes,
            boolean preloadAllowed) {
        return "forward-" + Math.max(0, forwardBytes)
                + "-back-" + Math.max(0, backBytes)
                + "-preload-" + preloadAllowed;
    }

    private void publishMpvForwardCacheDecision(
            MpvForwardCacheController.Trigger trigger,
            MpvForwardCachePolicy.Assessment assessment,
            MpvForwardCacheController.Decision decision,
            MpvForwardCacheController.Snapshot after,
            PlayerCacheState cache,
            PlaybackAutoContext context,
            long now,
            boolean started,
            boolean accepted,
            boolean staged,
            String applyResult) {
        PlaybackTelemetry.DecisionOutcome outcome;
        if (!assessment.active()
                && assessment.inactiveReason() == MpvForwardCachePolicy.Reason.CONFIG_PRIORITY) {
            outcome = PlaybackTelemetry.DecisionOutcome.SUPPRESSED;
        } else if ("experiment-disabled".equals(applyResult)) {
            outcome = PlaybackTelemetry.DecisionOutcome.SUPPRESSED;
        } else if (!decision.requestsApply()) {
            outcome = PlaybackTelemetry.DecisionOutcome.OBSERVED;
        } else if (!started || !accepted) {
            outcome = PlaybackTelemetry.DecisionOutcome.FAILED;
        } else if (staged) {
            outcome = PlaybackTelemetry.DecisionOutcome.REQUESTED;
        } else {
            outcome = PlaybackTelemetry.DecisionOutcome.APPLIED;
        }
        String oldValue = forwardTargetLabel(decision.oldNativeTargetBytes());
        String targetValue = forwardTargetLabel(decision.targetBytes());
        String resultValue = forwardTargetLabel(after.nativeTargetBytes());
        String suppression = "experiment-disabled".equals(applyResult)
                ? "experiment-disabled"
                : !decision.requestsApply()
                ? decision.reason().label()
                : !started
                ? "action-rejected"
                : !accepted
                ? "native-apply-failed"
                : "none";
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        addBitrateInput(inputs, "average_bps", assessment.averageBitrate());
        addBitrateInput(inputs, "peak_bps", assessment.peakBitrate());
        addNumberInput(inputs, "media_target", assessment.mediaReliable()
                ? assessment.mediaTargetBytes() : -1,
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "safe_capacity", assessment.safeTargetBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "initial_baseline", after.initialBaselineBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "actual_fw", cache.forwardBytes(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM);
        addNumberInput(inputs, "actual_total", cache.totalBytes(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM);
        PlaybackAutoContext.Fact<PlaybackAutoContext.MemorySnapshot> memoryFact =
                context.device().memorySnapshot();
        addNumberInput(inputs, "native_heap",
                memoryFact.isUsable(now) && memoryFact.value().nativeHeapAllocatedBytes() != null
                        ? memoryFact.value().nativeHeapAllocatedBytes() : -1,
                memoryFact.isUsable(now) ? memoryFact.source() : PlaybackAutoContext.ValueSource.UNKNOWN,
                memoryFact.isUsable(now) ? memoryFact.confidence() : PlaybackAutoContext.Confidence.UNKNOWN);
        PlaybackAutoContext.Fact<Long> pssFact = context.device().diagnosticPssBytes();
        addNumberInput(inputs, "diagnostic_pss",
                pssFact.isUsable(now) ? pssFact.value() : -1,
                pssFact.isUsable(now) ? pssFact.source() : PlaybackAutoContext.ValueSource.UNKNOWN,
                pssFact.isUsable(now) ? pssFact.confidence() : PlaybackAutoContext.Confidence.UNKNOWN);
        PlaybackAutoContext.Fact<PlaybackAutoContext.MemoryPressure> pressureFact =
                context.device().memoryPressure();
        if (pressureFact.isUsable(now)) {
            inputs.add(PlaybackTelemetry.DecisionInput.text(
                    "memory_pressure", pressureFact.value().label(),
                    pressureFact.source(), pressureFact.confidence()));
        } else {
            inputs.add(PlaybackTelemetry.DecisionInput.unknown("memory_pressure"));
        }
        addNumberInput(inputs, "stable_samples", after.demandSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "cooldown_ms", decision.cooldownRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_FORWARD_CACHE,
                        outcome,
                        oldValue,
                        targetValue,
                        resultValue,
                        decision.reason().label(),
                        suppression,
                        inputs),
                now);
        PlaybackTrace.log("mpv-forward-cache", playbackTrace.current(),
                "state=%s trigger=%s action=%s reason=%s old=%s target=%s result=%s fw=%d total=%d stable=%d cooldown=%d apply=%s",
                after.state().label(), trigger == null ? "runtime" : trigger.label(),
                decision.action().label(), decision.reason().label(), oldValue,
                targetValue, resultValue, cache.forwardBytes(), cache.totalBytes(),
                after.demandSamples(), decision.cooldownRemainingMs(), applyResult);
    }

    private void publishMpvBackCacheDecision(
            MpvBackCacheController.Trigger trigger,
            MpvBackCachePolicy.Assessment assessment,
            MpvBackCacheController.Decision decision,
            MpvBackCacheController.Snapshot after,
            PlayerCacheState cache,
            PlaybackAutoContext context,
            long now,
            boolean started,
            boolean accepted,
            boolean staged,
            String applyResult) {
        PlaybackTelemetry.DecisionOutcome outcome;
        if (!assessment.active()
                && assessment.reason() == MpvBackCachePolicy.Reason.CONFIG_PRIORITY) {
            outcome = PlaybackTelemetry.DecisionOutcome.SUPPRESSED;
        } else if ("experiment-disabled".equals(applyResult)) {
            outcome = PlaybackTelemetry.DecisionOutcome.SUPPRESSED;
        } else if (!decision.requestsApply()) {
            outcome = PlaybackTelemetry.DecisionOutcome.OBSERVED;
        } else if (!started || !accepted) {
            outcome = PlaybackTelemetry.DecisionOutcome.FAILED;
        } else if (staged) {
            outcome = PlaybackTelemetry.DecisionOutcome.REQUESTED;
        } else {
            outcome = PlaybackTelemetry.DecisionOutcome.APPLIED;
        }
        String oldValue = backTargetLabel(decision.oldNativeTargetBytes());
        String targetValue = backTargetLabel(decision.targetBytes());
        String resultValue = backTargetLabel(after.nativeTargetBytes());
        String suppression = "experiment-disabled".equals(applyResult)
                ? "experiment-disabled"
                : !decision.requestsApply()
                ? decision.reason().label()
                : !started
                ? "action-rejected"
                : !accepted
                ? "native-apply-failed"
                : "none";
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        addNumberInput(inputs, "seek_distance_ms",
                decision.lastBackwardSeekDistanceMs() > 0
                        ? decision.lastBackwardSeekDistanceMs() : -1,
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                decision.lastBackwardSeekDistanceMs() > 0
                        ? PlaybackAutoContext.Confidence.HIGH
                        : PlaybackAutoContext.Confidence.UNKNOWN);
        addNumberInput(inputs, "seek_evidence", decision.backwardSeekEvidence(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "learned_target", decision.learnedTargetBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "safe_back", assessment.safeBackBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "total_budget", assessment.totalBudgetBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "forward_target", assessment.forwardBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "actual_fw", cache.forwardBytes(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM);
        addNumberInput(inputs, "actual_total", cache.totalBytes(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM);
        PlaybackAutoContext.Fact<PlaybackAutoContext.MemoryPressure> pressureFact =
                context.device().memoryPressure();
        if (pressureFact.isUsable(now)) {
            inputs.add(PlaybackTelemetry.DecisionInput.text(
                    "memory_pressure", pressureFact.value().label(),
                    pressureFact.source(), pressureFact.confidence()));
        } else {
            inputs.add(PlaybackTelemetry.DecisionInput.unknown("memory_pressure"));
        }
        addNumberInput(inputs, "normal_samples", after.normalSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "cooldown_ms", decision.cooldownRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "apply_attempts", after.applyAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_BACK_CACHE,
                        outcome,
                        oldValue,
                        targetValue,
                        resultValue,
                        decision.reason().label(),
                        suppression,
                        inputs),
                now);
        PlaybackTrace.log("mpv-back-cache", playbackTrace.current(),
                "state=%s trigger=%s action=%s reason=%s old=%s target=%s result=%s seekDistance=%d seekEvidence=%d learned=%d safe=%d forward=%d totalBudget=%d fw=%d total=%d cooldown=%d apply=%s",
                after.state().label(), trigger == null ? "runtime" : trigger.label(),
                decision.action().label(), decision.reason().label(), oldValue,
                targetValue, resultValue, decision.lastBackwardSeekDistanceMs(),
                decision.backwardSeekEvidence(), decision.learnedTargetBytes(),
                assessment.safeBackBytes(), assessment.forwardBytes(),
                assessment.totalBudgetBytes(), cache.forwardBytes(), cache.totalBytes(),
                decision.cooldownRemainingMs(), applyResult);
    }

    private boolean isCurrentMpvMediaSeekable() {
        if (player == null
                || !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            return false;
        }
        try {
            Timeline timeline = player.getCurrentTimeline();
            int index = player.getCurrentMediaItemIndex();
            if (timeline.isEmpty() || index < 0 || index >= timeline.getWindowCount()) return false;
            Timeline.Window window = new Timeline.Window();
            timeline.getWindow(index, window);
            return window.isSeekable;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isCurrentMpvMediaLive() {
        if (player == null) return false;
        try {
            return player.isCurrentMediaItemLive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String forwardTargetLabel(long bytes) {
        return bytes < 0 ? "unknown" : "forward-" + bytes;
    }

    private static String backTargetLabel(long bytes) {
        return bytes < 0 ? "unknown" : "back-" + bytes;
    }

    private static void addBitrateInput(
            List<PlaybackTelemetry.DecisionInput> inputs,
            String name,
            MpvForwardCachePolicy.BitrateEvidence evidence) {
        if (evidence != null && evidence.reliable()) {
            inputs.add(PlaybackTelemetry.DecisionInput.number(
                    name, evidence.bitsPerSecond(), evidence.valueSource(), evidence.confidence()));
        } else {
            inputs.add(PlaybackTelemetry.DecisionInput.unknown(name));
        }
    }

    private static void addNumberInput(
            List<PlaybackTelemetry.DecisionInput> inputs,
            String name,
            long value,
            PlaybackAutoContext.ValueSource source,
            PlaybackAutoContext.Confidence confidence) {
        if (value < 0 || source == PlaybackAutoContext.ValueSource.UNKNOWN
                || confidence == PlaybackAutoContext.Confidence.UNKNOWN) {
            inputs.add(PlaybackTelemetry.DecisionInput.unknown(name));
        } else {
            inputs.add(PlaybackTelemetry.DecisionInput.number(name, value, source, confidence));
        }
    }

    private static List<MpvHlsVariantPolicy.Variant> toPolicyVariants(
            List<MpvPlayer.HlsVariant> variants) {
        if (variants == null || variants.isEmpty()) return List.of();
        List<MpvHlsVariantPolicy.Variant> result =
                new ArrayList<>(variants.size());
        for (MpvPlayer.HlsVariant variant : variants) {
            MpvHlsVariantPolicy.Variant mapped = toPolicyVariant(variant);
            if (mapped != null) result.add(mapped);
        }
        return List.copyOf(result);
    }

    @Nullable
    private static MpvHlsVariantPolicy.Variant toPolicyVariant(
            @Nullable MpvPlayer.HlsVariant variant) {
        if (variant == null) return null;
        return new MpvHlsVariantPolicy.Variant(
                variant.bandwidthBitsPerSecond(),
                variant.averageBandwidthBitsPerSecond(),
                variant.width(),
                variant.height());
    }

    private static String hlsOptionLabel(String option) {
        if (TextUtils.isEmpty(option)) return "unset";
        String value = option.trim();
        if ("min".equals(value) || "max".equals(value)
                || "no".equals(value)) return value;
        long bits = optionBits(value);
        return bits > 0 ? "bps-" + bits : "invalid";
    }

    private static long optionBits(String option) {
        if (TextUtils.isEmpty(option)) return 0;
        try {
            return Math.max(0, Long.parseLong(option.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private record MpvHlsApplyResult(
            boolean started,
            boolean optionAccepted,
            boolean reloadStarted,
            boolean succeeded,
            String optionResult) {

        private MpvHlsApplyResult {
            optionResult = optionResult == null ? "unknown" : optionResult;
        }

        private static MpvHlsApplyResult notRequested() {
            return new MpvHlsApplyResult(
                    false, false, false, false, "not-requested");
        }

        private static MpvHlsApplyResult rejected() {
            return new MpvHlsApplyResult(
                    false, false, false, false, "rejected");
        }
    }

    public void applyPerformanceSettings() {
        if (isExo()) {
            resetNetworkProtectionSession("performance-settings-changed");
            scheduleNetworkProtection(0);
            if (engine instanceof ExoPlayerEngine exo
                    && exo.requiresDv7Hdr10FallbackRebuild()) {
                rebuildAndRestartExo("dv7-fallback-setting-changed");
            }
            return;
        }
        if (!isMpv() || spec == null || TextUtils.isEmpty(spec.getUrl()) || !(engine instanceof MpvPlayerEngine mpv)) return;
        resetMpvOutputEvaluationState();
        mpvSurfaceFallbackTried = false; // An explicit settings change permits a fresh attempt.
        mpvAutoVulkanPinnedForItem = false;
        mpvAutoVulkanDisabledForItem = false;
        mpv.setSurfaceDirectOverride(null);
        mpv.setVulkanRenderOverride(null);
        mpv.resetDv7HandlingForNewItem();
        mpv.resetDv8HandlingForNewItem();
        prepareMpvFelOutput(mpv);
        rebuildAndRestartMpv(null, "performance-settings-changed");
    }

    private boolean rebuildAndRestartExo(String reason) {
        if (!isExo() || spec == null || TextUtils.isEmpty(spec.getUrl())) {
            return false;
        }
        // 与 setMediaItemNow 同款分类：重建直接调 engine.start，不经过 setMediaItemNow，
        // 这里再走一次分类归一（正常流程重建时 spec 已通过首检，这里是防御层）。
        if (classifyAndNormalizeSpec()) return false;
        long position = Math.max(0, player.getCurrentPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        long textOffsetMs = getTextOffsetMs();
        long audioOffsetMs = getAudioOffsetMs();
        prepareSeq++;
        App.removeCallbacks(runnable);
        videoSize = null;
        initTrack = false;
        rebuildPlayer();
        playWhenReady = wasPlayWhenReady;
        applySubtitleStyle();
        playbackTrace.mark(PlaybackTrace.Stage.PREPARE,
                "player=" + playerType + " decode=" + engine.getDecode()
                        + " exo=" + reason);
        // X-28 双服务互斥：Exo 重建恢复起播前先暂停音乐，照 setMediaItemNow 的写法
        // （仅 playWhenReady 时、try/catch 包住）。
        if (wasPlayWhenReady) {
            try {
            } catch (Throwable ignored) {
            }
        }
        if (spec != null) setDanmakus(spec.getDanmakus());
        engine.start(spec.checkUa(), position, wasPlayWhenReady);
        startNativeAudioSession(wasPlayWhenReady);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
        setTextOffsetMs(textOffsetMs);
        setAudioOffsetMs(audioOffsetMs);
        App.post(runnable, Constant.TIMEOUT_PLAY);
        return true;
    }

    private boolean rebuildAndRestartMpv(Boolean surfaceDirectOverride, String reason) {
        if (!isMpv() || spec == null || TextUtils.isEmpty(spec.getUrl()) || !(engine instanceof MpvPlayerEngine mpv)) return false;
        // 与 rebuildAndRestartExo 同款分类：重建直接调 engine.start，不经过 setMediaItemNow，
        // 这里再走一次分类归一；未转换协议由分类内报错并返回 true，届时不重建。
        if (classifyAndNormalizeSpec()) return false;
        long position = Math.max(0, player.getCurrentPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        long textOffsetMs = getTextOffsetMs();
        long audioOffsetMs = getAudioOffsetMs();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        prepareSeq++;
        resetMpvOutputEvaluationState();
        App.removeCallbacks(runnable);
        Boolean effectiveSurfaceDirectOverride = surfaceDirectOverride;
        if (effectiveSurfaceDirectOverride == null
                && (mpvAutoGpuPinnedForSession || mpvSurfaceFallbackTried)
                && MpvPerformanceSetting.getOutputMode() == MpvPerformanceSetting.OUTPUT_AUTO) {
            effectiveSurfaceDirectOverride = false;
        }
        mpv.setSurfaceDirectOverride(effectiveSurfaceDirectOverride);
        videoSize = null;
        initTrack = false;
        rebuildPlayer();
        playWhenReady = wasPlayWhenReady;
        applySubtitleStyle();
        applyMpvAutoInitialControl();
        playbackTrace.mark(PlaybackTrace.Stage.PREPARE, "player=" + playerType + " decode=" + engine.getDecode() + " mpv-output=" + reason);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("mpv-output", "rebuild reason=%s directOverride=%s position=%d play=%s speed=%s repeat=%s spec=%s", reason, effectiveSurfaceDirectOverride, position, wasPlayWhenReady, speed, repeat, debugSpec());
        // X-28 双服务互斥：与 Exo 重建同款，恢复起播前先暂停音乐（仅 playWhenReady 时、
        // try/catch 包住；pauseMusicForVideo 自身亦有 try/catch，本处包住只为与 Exo 路写法一致）。
        if (wasPlayWhenReady) {
            try {
            } catch (Throwable ignored) {
            }
        }
        if (spec != null) setDanmakus(spec.getDanmakus());
        engine.start(spec.checkUa(), position, wasPlayWhenReady);
        scheduleMpvAutoOutputEvaluation();
        startNativeAudioSession(wasPlayWhenReady);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
        setTextOffsetMs(textOffsetMs);
        setAudioOffsetMs(audioOffsetMs);
        App.post(runnable, Constant.TIMEOUT_PLAY);
        return true;
    }

    private void prepareMpvOutputForNewItem() {
        resetMpvOutputEvaluationState();
        mpvVulkanFallbackTried = false;
        mpvSurfaceFallbackTried = false;
        List<Track> persistedTracks = Track.find(getKey());
        Track persistedSubtitle = findRequestedSubtitle(persistedTracks);
        mpvExplicitSubtitlePreference = persistedSubtitle != null;
        if (!(engine instanceof MpvPlayerEngine mpv)) return;
        mpv.prepareSubtitleForNewItem(persistedSubtitle);
        boolean dv7HandlingChanged = mpv.resetDv7HandlingForNewItem();
        boolean dv8HandlingChanged = mpv.resetDv8HandlingForNewItem();
        boolean clearAutoVulkanRenderer = mpvAutoVulkanPinnedForItem
                && !mpv.isDv7FelOutputEnabled();
        mpvAutoVulkanPinnedForItem = false;
        mpvAutoVulkanDisabledForItem = false;
        mpv.setVulkanRenderOverride(null);
        if (MpvPerformanceSetting.getOutputMode()
                == MpvPerformanceSetting.OUTPUT_AUTO) {
            callback.onPlayerOutputPending();
        }
        mpv.setSurfaceDirectOverride(null);
        boolean autoDirectEligible = MpvAutoOutputPolicy.canStartSurfaceDirect(
                engine.isHard(),
                Util.isLeanback(),
                videoEffectsActive || videoEffectsDirty || MpvPerformanceSetting.isInterpolation()
                        || lutAllowed && LutSetting.isEnabled(),
                MpvConfigStore.hasGpuVideoProcessing());
        if (mpvAutoGpuPinnedForSession
                && MpvPerformanceSetting.getOutputMode() == MpvPerformanceSetting.OUTPUT_AUTO) {
            autoDirectEligible = false;
        }
        boolean shouldStartDirect = MpvPerformanceSetting.shouldUseSurfaceDirect(
                autoDirectEligible, Util.isLeanback(), engine.isHard());
        if (mpv.isConfiguredSurfaceDirect() == shouldStartDirect
                && !clearAutoVulkanRenderer && !dv7HandlingChanged && !dv8HandlingChanged) return;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("mpv-output", "prepare new item rebuild currentDirect=%s desiredDirect=%s clearAutoVulkan=%s mode=%s", mpv.isSurfaceDirect(), shouldStartDirect, clearAutoVulkanRenderer, MpvPerformanceSetting.getOutputModeText());
        mpv.setSurfaceDirectOverride(shouldStartDirect);
        rebuildPlayer();
    }

    private void resetMpvOutputRuntime() {
        resetMpvOutputEvaluationState();
        localProxyRetry = 0;
        mpvVulkanFallbackTried = false;
        mpvSurfaceFallbackTried = false;
        mpvAutoGpuPinnedForSession = false;
        mpvAutoVulkanPinnedForItem = false;
        mpvAutoVulkanDisabledForItem = false;
        lastMpvFrameTimingLogMs = 0;
        if (engine instanceof MpvPlayerEngine mpv) {
            mpv.setSurfaceDirectOverride(null);
            mpv.setVulkanRenderOverride(null);
        }
    }

    private void resetMpvOutputEvaluationState() {
        mpvAutoOutputEvaluated = false;
        mpvAutoOutputFrameReady = false;
        mpvAutoOutputEvaluationScheduled = false;
        mpvAutoOutputProbeAttempts = 0;
        // 注意：Vulkan 一次性旗不在此清。旧路每次 rebuildAndRestartMpv 都调
        // 本方法，清掉刚置的 tried 旗，旗语义已破；Vulkan 旗只在整集重置
        // （resetMpvOutputRuntime / 新集 prepare）时清。
        mpvOutputEvaluationSeq++;
    }

    private void scheduleMpvAutoOutputEvaluation() {
        if (!isMpv()
                || !shouldEvaluateMpvOutput()) return;
        if (mpvAutoOutputEvaluated || mpvAutoOutputEvaluationScheduled) return;
        mpvAutoOutputEvaluationScheduled = true;
        int seq = ++mpvOutputEvaluationSeq;
        App.post(() -> {
            if (seq != mpvOutputEvaluationSeq) return;
            mpvAutoOutputEvaluationScheduled = false;
            if (mpvHlsManagedReload) {
                scheduleMpvAutoOutputEvaluation();
                return;
            }
            mpvAutoOutputProbeAttempts++;
            boolean evaluated = evaluateMpvAutoOutput();
            if (!evaluated && !mpvAutoOutputEvaluated && mpvAutoOutputProbeAttempts < MPV_AUTO_OUTPUT_PROBE_MAX_ATTEMPTS) {
                scheduleMpvAutoOutputEvaluation();
            } else if (!evaluated && SpiderDebug.isEnabled()) {
                SpiderDebug.log("mpv-output", "auto probe exhausted attempts=%d size=%dx%d tracksEmpty=%s", mpvAutoOutputProbeAttempts, getVideoWidth(), getVideoHeight(), engine == null || engine.getCurrentTracks() == null || engine.getCurrentTracks().isEmpty());
            }
        }, MPV_AUTO_OUTPUT_PROBE_INTERVAL_MS);
    }

    private boolean evaluateMpvAutoOutput() {
        if (!isMpv() || mpvAutoOutputEvaluated
                || !(engine instanceof MpvPlayerEngine mpv)) return true;
        if (mpvHlsManagedReload) return false;
        if (mpv.isDv7FelRequested() && !mpv.isNativeOutputSelectionReady()) return false;
        prepareMpvFelOutput(mpv);
        if (mpv.isDv7FelOutputEnabled()) {
            // Already selected by native before decoder initialization. A
            // second App-level transition would reopen the same media.
            mpvAutoOutputEvaluated = true;
            return true;
        }
        Tracks tracks = engine.getCurrentTracks();
        boolean tracksReady = tracks != null && !tracks.isEmpty();
        if (!tracksReady) return false;
        boolean automaticOutput = MpvPerformanceSetting.getOutputMode()
                == MpvPerformanceSetting.OUTPUT_AUTO;
        Format format = tracksReady ? engine.getVideoFormat() : null;
        PlayerEngine.VideoPlaybackDetails videoDetails = engine.getVideoPlaybackDetails();
        boolean dolbyVision = videoDetails != null
                && videoDetails.hasDolbyVisionSource();
        // P8 HDR10 compatibility is also required with an explicit output
        // backend. Leave other explicit-mode content on its existing path.
        if (!automaticOutput && (!mpv.isHard() || !dolbyVision
                || videoDetails.dolbyVisionProfile() != 8)) {
            mpvAutoOutputEvaluated = true;
            return true;
        }
        if (!dolbyVision && (format == null || !tracks.containsType(C.TRACK_TYPE_VIDEO))) {
            return false;
        }
        VideoSize probedSize = mpv.getVideoSizeSnapshot();
        int width = format != null && format.width > 0 ? format.width : probedSize.width > 0 ? probedSize.width : getVideoWidth();
        int height = format != null && format.height > 0 ? format.height : probedSize.height > 0 ? probedSize.height : getVideoHeight();
        if (width <= 0 || height <= 0) return false;
        boolean externalSubtitleActive = spec != null && spec.getSubs() != null && !spec.getSubs().isEmpty();
        boolean earlyEvaluation = false;
        boolean subtitleActive = externalSubtitleActive || mpvExplicitSubtitlePreference;
        boolean lutOrFilterActive = videoEffectsActive || videoEffectsDirty || lutAllowed && LutSetting.isEnabled() || MpvPerformanceSetting.isInterpolation();
        boolean customGpuProcessing = MpvConfigStore.hasGpuVideoProcessing();
        boolean dv7Hdr10FallbackEnabled = dolbyVision
                && videoDetails.dolbyVisionProfile() == 7
                && mpv.isDv7Hdr10Active();
        MpvAutoOutputPolicy.DolbyVisionSupport dolbyVisionSupport = dolbyVision
                ? CodecCapabilityInspector.dolbyVisionSupport(
                App.get(), videoDetails, format, width, height)
                : MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN;
        MpvAutoOutputPolicy.DolbyVisionSupport profile81Support =
                dolbyVision && videoDetails.dolbyVisionProfile() == 7
                        && PlaybackPerformanceSetting.getMpvDv7HandlingMode()
                        == PlaybackPerformanceSetting.DV7_HANDLING_P81
                        ? CodecCapabilityInspector.dolbyVisionProfileSupport(
                        App.get(), 8, videoDetails.dolbyVisionLevel(),
                        videoDetails.sourceCodecs(), format, width, height)
                        : MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN;
        boolean dv7HandlingChanged = dolbyVision
                && videoDetails.dolbyVisionProfile() == 7
                && mpv.updateDv7Handling(dolbyVisionSupport, profile81Support);
        MpvAutoOutputPolicy.DolbyVisionSupport hevcHdr10Support =
                dolbyVision && videoDetails.dolbyVisionProfile() == 8
                        ? CodecCapabilityInspector.hevcHdr10Support(
                        App.get(), format, width, height)
                        : MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN;
        boolean dv8HandlingChanged = dolbyVision
                && videoDetails.dolbyVisionProfile() == 8
                && mpv.updateDv8Handling(dolbyVisionSupport, hevcHdr10Support);
        if (!automaticOutput) {
            mpvAutoOutputEvaluated = true;
            if (dv8HandlingChanged) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("mpv-output", "P8 compatibility handling=%s dvSupport=%s hdr10Support=%s explicitOutput=%d", mpv.getDv8HandlingOption(), dolbyVisionSupport, hevcHdr10Support, MpvPerformanceSetting.getOutputMode());
                // Preserve the chosen output/render backend and decode mode.
                rebuildAndRestartMpv(null, "compat-dv8-" + mpv.getDv8HandlingOption());
            }
            return true;
        }
        dv7Hdr10FallbackEnabled = dolbyVision
                && videoDetails.dolbyVisionProfile() == 7
                && mpv.isDv7Hdr10Active();
        MpvAutoOutputPolicy.Decision decision = MpvAutoOutputPolicy.evaluate(
                width, height, engine.isHard(),
                Util.isLeanback(), lutOrFilterActive, customGpuProcessing,
                dolbyVisionSupport,
                dolbyVision ? videoDetails.dolbyVisionProfile() : C.INDEX_UNSET,
                dv7Hdr10FallbackEnabled,
                hevcHdr10Support);
        decision = MpvAutoOutputPolicy.afterSurfaceFailure(decision, mpvSurfaceFallbackTried);
        decision = MpvAutoOutputPolicy.forFelReconstruction(decision,
                mpv.isDv7FelOutputEnabled());
        int dolbyVisionProfile = dolbyVision
                ? videoDetails.dolbyVisionProfile() : C.INDEX_UNSET;
        boolean currentlyVulkan = mpv.isVulkanRenderer();
        MpvAutoRenderPolicy.Decision renderDecision = MpvAutoRenderPolicy.evaluate(
                PlaybackPerformanceSetting.isAuto(
                        PlayerSetting.MPV, PlaybackPerformanceCatalog.MPV_RENDER),
                engine.isHard(), dolbyVisionProfile, dolbyVisionSupport,
                MPVLib.isBundledVulkanEnabled(App.get()),
                MPVLib.isDeviceVulkan13Capable(App.get()),
                currentlyVulkan, mpvAutoVulkanDisabledForItem,
                mpv.isDv7FelOutputEnabled());
        boolean enableAutoVulkan = renderDecision.action()
                == MpvAutoRenderPolicy.Action.ENABLE_VULKAN;
        if (enableAutoVulkan) {
            mpvAutoVulkanPinnedForItem = true;
            mpv.setVulkanRenderOverride(true);
        }
        if (dolbyVision && decision.reason().startsWith("dolby-vision-hw-")) {
            mpvAutoGpuPinnedForSession = true;
        }
        mpvAutoOutputEvaluated = true;
        boolean currentlyDirect = isMpvSurfaceDirect();
        MpvAutoOutputPolicy.Transition transition = MpvAutoOutputPolicy.transition(decision.eligible(), currentlyDirect);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("mpv-output", "auto decision eligible=%s transition=%s reason=%s renderAction=%s renderReason=%s size=%dx%d tracksReady=%s early=%s subtitle=%s lutOrFilter=%s customGpu=%s dvProfile=%d dvSupport=%s direct=%s gpuPinned=%s autoVulkan=%s attempts=%d", decision.eligible(), transition, decision.reason(), renderDecision.action(), renderDecision.reason(), width, height, tracksReady, earlyEvaluation, subtitleActive, lutOrFilterActive, customGpuProcessing, dolbyVisionProfile, dolbyVisionSupport, currentlyDirect, mpvAutoGpuPinnedForSession, mpvAutoVulkanPinnedForItem, mpvAutoOutputProbeAttempts);
        boolean transitionRequested = dv7HandlingChanged || dv8HandlingChanged || enableAutoVulkan
                || transition == MpvAutoOutputPolicy.Transition.ENTER_SURFACE_DIRECT
                || transition == MpvAutoOutputPolicy.Transition.LEAVE_SURFACE_DIRECT;
        boolean requestAccepted = true;
        if (dv7HandlingChanged || dv8HandlingChanged) {
            Boolean outputOverride = enableAutoVulkan
                    || transition == MpvAutoOutputPolicy.Transition.LEAVE_SURFACE_DIRECT
                    ? Boolean.FALSE
                    : transition == MpvAutoOutputPolicy.Transition.ENTER_SURFACE_DIRECT
                    ? Boolean.TRUE : null;
            String reason = dv7HandlingChanged
                    ? "auto-dv7-" + mpv.getDv7HandlingOption()
                    : "auto-dv8-" + mpv.getDv8HandlingOption();
            requestAccepted = rebuildAndRestartMpv(outputOverride, reason);
        } else if (enableAutoVulkan) {
            requestAccepted = rebuildAndRestartMpv(false,
                    "auto-" + renderDecision.reason());
        } else if (transition == MpvAutoOutputPolicy.Transition.ENTER_SURFACE_DIRECT) {
            requestAccepted = rebuildAndRestartMpv(true, "auto-" + decision.reason());
        } else if (transition == MpvAutoOutputPolicy.Transition.LEAVE_SURFACE_DIRECT) {
            requestAccepted = rebuildAndRestartMpv(false, "auto-" + decision.reason());
        }
        String oldOutput = currentlyDirect ? "surface-direct"
                : currentlyVulkan ? "gpu-vulkan" : "gpu-opengl";
        String targetOutput = enableAutoVulkan ? "gpu-vulkan"
                : decision.eligible() ? "surface-direct" : "gpu";
        PlaybackTelemetry.DecisionOutcome telemetryOutcome = transitionRequested
                ? requestAccepted ? PlaybackTelemetry.DecisionOutcome.REQUESTED : PlaybackTelemetry.DecisionOutcome.FAILED
                : PlaybackTelemetry.DecisionOutcome.HELD;
        playbackTelemetryCoordinator.publishDecision(playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_OUTPUT,
                        telemetryOutcome,
                        oldOutput,
                        targetOutput,
                        transitionRequested && requestAccepted ? targetOutput : oldOutput,
                        enableAutoVulkan ? renderDecision.reason() : decision.reason(),
                        transitionRequested ? requestAccepted ? "none" : "rebuild-rejected" : "no-transition",
                        List.of(
                                PlaybackTelemetry.DecisionInput.number("width", width, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("height", height, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("hard_decode", engine.isHard(), PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("leanback", Util.isLeanback(), PlaybackAutoContext.ValueSource.SYSTEM_API, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("tracks_ready", tracksReady, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("early_evaluation", earlyEvaluation, PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("subtitle_active", subtitleActive, PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("lut_or_filter", lutOrFilterActive, PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("custom_gpu", customGpuProcessing, PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("currently_direct", currentlyDirect, PlaybackAutoContext.ValueSource.NATIVE_RUNTIME, PlaybackAutoContext.Confidence.MEDIUM),
                                PlaybackTelemetry.DecisionInput.bool("eligible", decision.eligible(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("probe_attempts", mpvAutoOutputProbeAttempts, PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH))),
                SystemClock.elapsedRealtime());
        if (!transitionRequested || !requestAccepted) {
            callback.onPlayerOutputReady();
        }
        return true;
    }

    private boolean hasRequestedSubtitle(List<Track> tracks) {
        return findRequestedSubtitle(tracks) != null;
    }

    private Track findRequestedSubtitle(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) return null;
        for (Track track : tracks) {
            if (track.getType() == C.TRACK_TYPE_TEXT
                    && track.isSelected() && !track.isDisabled()) return track;
        }
        return null;
    }

    private void restoreTrackSelection(List<Track> tracks) {
        if (tracks != null && !tracks.isEmpty()) engine.setTrack(tracks);
    }

    private void onMpvVideoSizeProbed(Integer width, Integer height) {
        if (width == null || height == null || width <= 0 || height <= 0) return;
        // MPV 的尺寸只到这里：同样写进 videoSize，否则 isPortrait()/isLandscape() 对 MPV
        // 恒为 false，元数据到达后的方向纠正（checkOrientation）永远转不动 MPV 的竖屏。
        videoSize = new VideoSize(width, height);
        if (!isMpv()
                || !shouldEvaluateMpvOutput()
                || mpvAutoOutputEvaluated) return;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("mpv-output", "auto size probe size=%dx%d attempts=%d", width, height, mpvAutoOutputProbeAttempts);
        mpvAutoOutputEvaluationScheduled = false;
        mpvOutputEvaluationSeq++;
        if (!evaluateMpvAutoOutput()) scheduleMpvAutoOutputEvaluation();
    }

    private boolean shouldEvaluateMpvOutput() {
        return MpvPerformanceSetting.getOutputMode() == MpvPerformanceSetting.OUTPUT_AUTO
                || engine instanceof MpvPlayerEngine mpv
                && (mpv.isDv7FelRequested() || mpv.isHard());
    }

    private boolean prepareMpvFelOutput(MpvPlayerEngine mpv) {
        if (!mpv.updateDv7FelOutputForCurrentItem()) return false;
        if (mpv.isDv7FelOutputEnabled() && mpv.isVulkanRenderer()
                && engine.isHard() && PlaybackPerformanceSetting.isAuto(
                        PlayerSetting.MPV, PlaybackPerformanceCatalog.MPV_RENDER)) {
            // Retain runtime failure recovery without changing the configured
            // renderer: native owns this per-source automatic choice.
            mpvAutoVulkanPinnedForItem = true;
        }
        PlaybackTrace.log("mpv-dv", playbackTrace.current(),
                "native FEL selection: active=%s single-load=1",
                mpv.isDv7FelOutputEnabled());
        return true;
    }

    private boolean retryMpvDv7P81Failure(PlaybackException error) {
        if (error == null || !(engine instanceof MpvPlayerEngine mpv)
                || !mpv.isDv7P81Active()) return false;
        String message = error.getMessage();
        boolean conversionOrDecodeFailure = error.errorCode
                == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                || error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
                || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
                || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                || message != null && (message.startsWith(MpvPlayer.ERROR_DECODE_FAILED)
                || message.startsWith(MpvPlayer.ERROR_INVALID_MEDIA_DATA));
        if (!conversionOrDecodeFailure || !mpv.prepareDv7P81Hdr10Fallback()) {
            return false;
        }
        return rebuildAndRestartMpv(null, "dv7-p81-hdr10-fallback");
    }

    private boolean retryMpvDv7P81FirstFrameTimeout() {
        if (!(engine instanceof MpvPlayerEngine mpv)
                || player == null
                || !mpv.isDv7P81Active()
                || playbackTrace.hasStage(PlaybackTrace.Stage.FIRST_FRAME)
                || !mpv.prepareDv7P81Hdr10Fallback()) {
            return false;
        }
        long position = Math.max(0, player.getCurrentPosition());
        PlaybackTrace.log("mpv-dv", playbackTrace.current(),
                "P8.1 produced no first frame before startup timeout; retry HDR10 position=%d",
                position);
        return rebuildAndRestartMpv(null, "dv7-p81-first-frame-timeout");
    }

    private boolean retryMpvDv7NativeFirstFrameTimeout() {
        if (player == null || !isDv7NativeAttemptRequested() || mpvSurfaceFallbackTried) return false;
        if (playbackTrace.hasStage(PlaybackTrace.Stage.FIRST_FRAME)) return false;
        mpvSurfaceFallbackTried = true;
        mpvOutputEvaluationSeq++;
        PlaybackTrace.log("mpv-output", playbackTrace.current(), "dv7 native produced no first frame before startup timeout; fallback gpu once");
        return rebuildAndRestartMpv(false, "dv7-native-first-frame-timeout");
    }

    private boolean retryMpvSurfaceDirectFailure(PlaybackException error) {
        if (!isMpvSurfaceDirect() || mpvSurfaceFallbackTried || error == null) return false;
        // DV7 原生档不再在门口堵死：与其它档同走后段 GPU 回落，一次性旗
        // mpvSurfaceFallbackTried 防循环回落（函数首行已查）。
        String message = error.getMessage();
        boolean outputFailure = error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED
                || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
                || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                || message != null && (message.startsWith(MpvPlayer.ERROR_VIDEO_OUTPUT_FAILED) || message.startsWith(MpvPlayer.ERROR_DECODE_FAILED));
        if (!outputFailure) return false;
        mpvSurfaceFallbackTried = true;
        mpvOutputEvaluationSeq++;
        PlaybackTrace.log("mpv-output", playbackTrace.current(), "surface direct failed; fallback gpu once code=%d message=%s", error.errorCode, message);
        return rebuildAndRestartMpv(false, "surface-direct-failure");
    }

    private boolean retryMpvVulkanBackendFailure(PlaybackException error) {
        if (mpvVulkanFallbackTried || error == null
                || !(engine instanceof MpvPlayerEngine mpv)
                || !mpv.shouldFallbackVulkanToStable()) return false;
        String message = error.getMessage();
        boolean outputFailure = error.errorCode
                == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED
                || message != null
                && message.startsWith(MpvPlayer.ERROR_VIDEO_OUTPUT_FAILED);
        if (!outputFailure) return false;
        return retryMpvVulkanBackendToStable(mpv, "direct-output-failure");
    }

    private boolean retryMpvVulkanBackendTimeout() {
        if (mpvVulkanFallbackTried || !(engine instanceof MpvPlayerEngine mpv)
                || !mpv.shouldFallbackVulkanToStable()) return false;
        VideoSize size = mpv.getVideoSizeSnapshot();
        if ((size == null || size.width <= 0 || size.height <= 0)
                && getVideoWidth() <= 0 && getVideoHeight() <= 0) return false;
        return retryMpvVulkanBackendToStable(mpv, "direct-first-frame-timeout");
    }

    private boolean retryMpvVulkanBackendToStable(MpvPlayerEngine mpv,
                                                   String reason) {
        mpvVulkanFallbackTried = true;
        MpvVulkanBackendPolicy.rememberDirectFailure();
        mpv.setVulkanBackendOverride(MpvVulkanBackendPolicy.STABLE);
        PlaybackTrace.log("mpv-vulkan", playbackTrace.current(),
                "backend direct failed; fallback stable once reason=%s", reason);
        return rebuildAndRestartMpv(false, reason);
    }

    private boolean retryMpvAutoVulkanFailure(PlaybackException error) {
        if (error == null || !mpvAutoVulkanPinnedForItem
                || !(engine instanceof MpvPlayerEngine mpv)
                || !mpv.isVulkanRenderer()) return false;
        String message = error.getMessage();
        boolean outputFailure = error.errorCode
                == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED
                || message != null
                && message.startsWith(MpvPlayer.ERROR_VIDEO_OUTPUT_FAILED);
        return outputFailure
                && retryMpvAutoVulkanToOpenGl("auto-vulkan-output-failure");
    }

    private boolean retryMpvAutoVulkanToOpenGl(String reason) {
        if (!mpvAutoVulkanPinnedForItem
                || !(engine instanceof MpvPlayerEngine mpv)
                || !mpv.isVulkanRenderer()) return false;
        mpvAutoVulkanPinnedForItem = false;
        mpvAutoVulkanDisabledForItem = true;
        mpv.setVulkanRenderOverride(false);
        mpv.setVulkanBackendOverride(null);
        PlaybackTrace.log("mpv-vulkan", playbackTrace.current(),
                "automatic DV GPU Vulkan failed; fallback OpenGL once reason=%s",
                reason);
        return rebuildAndRestartMpv(false, reason);
    }

    private boolean isDv7NativeAttemptRequested() {
        if (!isMpv() || !(engine instanceof MpvPlayerEngine mpv)
                || !engine.isHard() || !mpv.isDv7NativeActive()) return false;
        PlayerEngine.VideoPlaybackDetails details =
                engine.getVideoPlaybackDetails();
        return details != null && details.dolbyVisionProfile() == 7;
    }

    /** 先建后释：构建失败返回 null，调用方保留旧引擎/旧播放，不悬空。 */
    private PlayerEngine buildEngineOrNull(int type, int decode) {
        try {
            return buildEngine(type, decode);
        } catch (Throwable e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "build engine failed type=%d decode=%d error=%s", type, decode, causeChain(e));
            return null;
        }
    }

    private PlayerEngine buildEngine(int type, int decode) {
        return switch (type) {
            case PlayerSetting.MPV -> new MpvPlayerEngine(decode, listener, this::onMpvVideoSizeProbed);
            default -> {
                ExoPlayerEngine exo = new ExoPlayerEngine(decode, listener);
                // 隧道看门狗回调注入：引擎超时只上报，重启由 PM 走隧道失败重试同款路。
                // 引擎 release() 时自清回调，PM release() 时也先清，防循环引用残留。
                exo.setTunnelingWatchdogCallback(() -> onExoTunnelingWatchdogTimeout(exo));
                yield exo;
            }
        };
    }

    public void browse(PlaySpec spec) {
        reset();
        clear();
        stopParse();
        start(spec, Constant.TIMEOUT_PLAY);
    }

    public void start(PlaySpec spec, long timeout) {
        start(spec, timeout, true);
    }

    public void start(PlaySpec spec, long timeout, boolean playWhenReady) {
        start(spec, timeout, playWhenReady, C.TIME_UNSET);
    }

    public void start(PlaySpec spec, long timeout, boolean playWhenReady, long positionMs) {
        lutWarmupRefreshAttempts = 0;
        autoKernelSwitchTried = false;
        if (engine instanceof ExoPlayerEngine exo) exo.resetTunnelingFallbackForNewItem();
        endPlaybackTelemetrySession("replace-start");
        clearPendingSwitchRestore();
        clearDanmaku("start");
        this.spec = spec;
        pendingInitialStartPositionMs = positionMs > 0 ? positionMs : C.TIME_UNSET;
        prepareMpvOutputForNewItem();
        beginPlaybackTrace("start", false);
        this.playWhenReady = playWhenReady;
        retry = 0;
        localProxyRetry = 0;
        hardDecodeSwitchRetryArmed = false;
        setMediaItem(timeout);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata) {
        parse(key, result, useParse, metadata, true);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata, boolean playWhenReady) {
        parse(key, result, useParse, metadata, playWhenReady, C.TIME_UNSET);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata,
                      boolean playWhenReady, long positionMs) {
        endPlaybackTelemetrySession("replace-parse");
        stopParse();
        clearPendingSwitchRestore();
        clearDanmaku("parse");
        spec = PlaySpec.fromParse(result, key, metadata, useParse);
        pendingInitialStartPositionMs = positionMs > 0 ? positionMs : C.TIME_UNSET;
        prepareMpvOutputForNewItem();
        beginPlaybackTrace("parse", false);
        this.playWhenReady = playWhenReady;
        retry = 0;
        localProxyRetry = 0;
        lutWarmupRefreshAttempts = 0;
        autoKernelSwitchTried = false;
        if (engine instanceof ExoPlayerEngine exo) exo.resetTunnelingFallbackForNewItem();
        hardDecodeSwitchRetryArmed = false;
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    private boolean reparseForPlayerSwitch(long position, float speed, boolean repeat) {
        if (spec == null || !spec.canReparse() || !spec.isParseSource()) return false;
        Result result = spec.getParseResult();
        boolean useParse = spec.isParseUseParse();
        MediaMetadata metadata = spec.getMetadata();
        String key = spec.getKey();
        pendingSwitchRestore = true;
        pendingSwitchPositionMs = position;
        pendingSwitchSpeed = speed;
        pendingSwitchRepeat = repeat;
        stopParse();
        if (spec.isParseSource()) {
            spec = inheritDetailVodId(PlaySpec.fromParse(result, key, metadata, useParse));
            bindPlaybackTrace();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player reparse type=%d position=%d useParse=%s spec=%s", playerType, position, useParse, debugSpec());
            parseJob = ParseJob.create(this).start(result, useParse);
        } else {
            refreshDirectForPlayerSwitch(result, key, metadata);
        }
        return true;
    }

    private void refreshDirectForPlayerSwitch(Result result, String key, MediaMetadata metadata) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player refresh direct type=%d keyLen=%d flag=%s url=%s", playerType, safeLength(key), result.getFlag(), summarizeUrl(result.getUrl().v()));
        Task.execute(() -> {
            try {
                Result refreshed = SiteApi.playerContent(key, result.getFlag(), result.getUrl().v(), playerType);
                App.post(() -> startRefreshedSwitchResult(refreshed, key, metadata));
            } catch (Throwable e) {
                String msg = e.getMessage();
                App.post(() -> {
                    clearPendingSwitchRestore();
                    callback.onError(msg == null || msg.isEmpty() ? ResUtil.getString(R.string.error_play_url) : msg);
                });
            }
        });
    }

    private void startRefreshedSwitchResult(Result result, String key, MediaMetadata metadata) {
        if (result == null || result.hasMsg() || result.getRealUrl().isEmpty()) {
            clearPendingSwitchRestore();
            callback.onError(result == null ? ResUtil.getString(R.string.error_play_url) : result.hasMsg() ? result.getMsg() : ResUtil.getString(R.string.error_play_url));
            return;
        }
        if (result.needParse()) {
            spec = inheritDetailVodId(PlaySpec.fromParse(result, key, metadata, false));
            bindPlaybackTrace();
            parseJob = ParseJob.create(this).start(result, false);
            return;
        }
        spec = inheritDetailVodId(PlaySpec.from(result, key, metadata));
        bindPlaybackTrace();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player refreshed direct spec=%s", debugSpec());
        setMediaItem(Constant.TIMEOUT_PLAY);
        restoreAfterSwitchReparse();
    }

    private void restoreAfterSwitchReparse() {
        if (!pendingSwitchRestore) return;
        long position = pendingSwitchPositionMs;
        float speed = pendingSwitchSpeed;
        boolean repeat = pendingSwitchRepeat;
        clearPendingSwitchRestore();
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    private void clearPendingSwitchRestore() {
        pendingSwitchRestore = false;
        pendingSwitchPositionMs = C.TIME_UNSET;
        pendingSwitchSpeed = 1f;
        pendingSwitchRepeat = false;
    }

    public void setMediaItem() {
        playWhenReady = player == null || player.getPlayWhenReady();
        setMediaItem(Constant.TIMEOUT_PLAY);
    }

    private void setMediaItem(long timeout) {
        if (spec == null || spec.getUrl() == null || spec.getUrl().isEmpty()) {
            callback.onError(ResUtil.getString(R.string.error_play_url));
            return;
        }
        int seq = ++prepareSeq;
        if (rejectMpvDrmMedia()) return;
        if (LocalProxyDebug.shouldAwaitReady(spec.getUrl())) {
            awaitLocalProxyAndSetMediaItem(seq, timeout);
            return;
        }
        setMediaItemNow(timeout, true);
    }

    private void awaitLocalProxyAndSetMediaItem(int seq, long timeout) {
        PlaySpec target = spec;
        String url = target.getUrl();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy await start seq=%d timeout=%d spec=%s", seq, timeout, debugSpec());
        Task.execute(() -> {
            boolean ready = LocalProxyDebug.awaitReady(url, LOCAL_PROXY_READY_TIMEOUT_MS);
            App.post(() -> {
                if (seq != prepareSeq || spec != target || engine == null) {
                    if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy await skip seq=%d current=%d ready=%s", seq, prepareSeq, ready);
                    return;
                }
                if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy await done seq=%d ready=%s spec=%s", seq, ready, debugSpec());
                if (!ready) {
                    callback.onError(ResUtil.getString(R.string.error_play_timeout));
                    return;
                }
                setMediaItemNow(timeout, true);
            });
        });
    }

    /**
     * 地址分类＋路由（跟默影视一样走分类）：只拦上游转换漏网地址的可见错门——
     * 未经上游转换就走到播放器门口的地址才在此给可见错，其余一律放行或转成本地
     * 地址后放行。除本可见错门外，播放器门口另有一道旧有可见
     * 错门：MPV＋DRM（rejectMpvDrmMedia，MPV 不吃 DRM 媒体），同样是旧有门，不是
     * 本分类新增的拦。
     * - http/https/rtsp/rtmp/file/content：直接放行。rtmp 在 Exo 下无原生支持时
     *   由引擎报错走可见错，不在门口预判拦掉；MPV 本就吃 rtmp。
     * - proxy/assets/asset：走 UrlUtil.convert 转成本地地址再放行（壳子自有转换口径；
     *   asset 单数与 assets 复数同款处理，不再放行原串）。
     * - data:（内联 DASH MPD）：放行。Exo 侧 MediaSourceFactory 用 DefaultDataSource
     *   链（含 data scheme），MPV 侧取链层 MpdEdlResolver 本就读 data: 清单；引擎真吃
     *   不了时走引擎可见错，不在门口预判。
     * - 无 scheme 相对地址：先试 UrlUtil.resolve（基地址依次取解析结果的 playUrl、
     *   当前配置地址），解得出绝对地址就替换，解不出也不拦，兜底交给引擎试播、
     *   引擎报错时可见。
     * - magnet/ed2k/thunder/video/p2p：转换点在上游（Source.fetch 的各 Extractor、
     *   SiteApi.hkPlayerContent 的迅雷转换），本方法在主线程不能同步跑迅雷/DHT
     *   转换；走到这里说明上游转换被旁路，引擎本身播不了，只能给可见错（只拦上游
     *   转换漏网地址的可见错门；修旁路要去上游，不是在这里再拦一类）。
     * - 其余认不出的 scheme：一律兜底放行，交给当前引擎试播，引擎报错时可见。
     * 返回 true 表示已给可见错、调用方不要继续起播；返回 false 表示已分类/归一完成。
     * 其它进播放器的路：start()/parse 成功／换集／重建全部汇到 setMediaItemNow；
     * Exo 重建路（rebuildAndRestartExo）另有一道同款分类，并补 X-28 暂停；重试路
     * （retryExo*、解码切换重试、Exo 恢复、首帧回落）复用同一个 spec 对象、URL 只
     * 能经 onParseSuccess→setMediaItem 改写（改写必过本分类），故重试路不重复分类，
     * 但真恢复起播前仍按 X-28 先暂停音乐（仅 playWhenReady 时），见各重试路注释；
     * MPV 重建路不分类（MPV 原生协议面，已声明口径，本批不动）。
     */
    private boolean classifyAndNormalizeSpec() {
        if (spec == null) return false;
        String url = spec.getUrl();
        if (url == null || url.isEmpty()) return false;
        url = url.trim();
        if (!url.equals(spec.getUrl())) spec.setUrl(url);
        String scheme = UrlUtil.scheme(url);
        if (scheme.equals("proxy") || scheme.equals("assets") || scheme.equals("asset")) {
            try {
                String converted = UrlUtil.convert(url);
                if (converted != null && !converted.isEmpty() && !converted.equals(url)) spec.setUrl(converted);
            } catch (Throwable ignored) {
            }
            return false;
        }
        if (scheme.isEmpty()) {
            String base = "";
            if (spec.getParseResult() != null) base = spec.getParseResult().getPlayUrl();
            if (base == null || UrlUtil.scheme(base).isEmpty()) {
                try {
                    base = VodConfig.getUrl();
                } catch (Throwable ignored) {
                    base = "";
                }
            }
            if (base != null && !UrlUtil.scheme(base).isEmpty()) {
                try {
                    String resolved = UrlUtil.resolve(base, url);
                    if (resolved != null && !resolved.isEmpty() && !UrlUtil.scheme(resolved).isEmpty()) spec.setUrl(resolved);
                } catch (Throwable ignored) {
                }
            }
            return false;
        }
        boolean unconverted = scheme.equals("magnet") || scheme.equals("ed2k") || scheme.equals("thunder") || scheme.equals("video") || scheme.equals("p2p");
        if (unconverted) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "unconverted p2p url reached player, upstream conversion bypassed scheme=%s spec=%s", scheme, debugSpec());
            callback.onError(ResUtil.getString(R.string.error_play_url));
            return true;
        }
        return false;
    }

    private void setMediaItemNow(long timeout, boolean notifyPrepare) {
        if (spec == null || spec.getUrl() == null || spec.getUrl().isEmpty() || engine == null) {
            callback.onError(ResUtil.getString(R.string.error_play_url));
            return;
        }
        if (classifyAndNormalizeSpec()) return;
        prepareExoFrameSchedulingForNewPlayback();
        spec.setPlaybackTraceId(playbackTrace.ensure());
        spec.refreshPlaybackRoute();
        publishPlaybackAutoContext(false);
        applyMpvAutoInitialControl();
        logPlaybackRoute();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "setMediaItem timeout=%d notify=%s spec=%s", timeout, notifyPrepare, debugSpec());
        resetNetworkProtectionSession("new-media");
        App.removeCallbacks(runnable);
        setDanmakus(spec.getDanmakus());
        autoSearchDanmaku();
        maybeStartLivePlatformDanmaku();
        prepareLutPipeline();
        initTrack = false;
        waitingLutBeforePlay = false;
        applySubtitleStyle();
        playbackTrace.mark(PlaybackTrace.Stage.PREPARE, "player=" + playerType + " decode=" + engine.getDecode());
        // X-28 双服务互斥：视频真起播前，在应用层先暂停音乐，不只靠系统焦点仲裁
        // （音乐未起播时其暂停为空转；起播暂停时不碰音乐，避免无声打断）。
        if (playWhenReady) {
            try {
            } catch (Throwable ignored) {
            }
        }
        long initialPositionMs = pendingInitialStartPositionMs;
        pendingInitialStartPositionMs = C.TIME_UNSET;
        engine.start(spec.checkUa(), initialPositionMs, playWhenReady);
        publishPlaybackTelemetry();
        schedulePlaybackTelemetry();
        scheduleMpvAutoOutputEvaluation();
        startNativeAudioSession(playWhenReady);
        App.post(runnable, timeout);
        if (notifyPrepare) callback.onPrepare();
    }

    private void prepareExoFrameSchedulingForNewPlayback() {
        if (!(engine instanceof ExoPlayerEngine exo)) return;
        if (!exo.prepareFrameSchedulingForNextPlayback()) return;
        rebuildPlayer(false);
    }

    private void applySubtitleStyle() {
        if (engine != null) engine.setSubtitleStyle(PlayerSetting.getSubtitleTextSize(), PlayerSetting.getSubtitlePosition());
        if (player != null) setTextOffsetMs(SubtitleSetting.getDelayMs());
    }

    private void startNativeAudioSession(boolean shouldPlay) {
        if (!shouldPlay || !isNativePlayer()) return;
        requestNativeAudioFocus();
        registerNoisyReceiver();
    }

    private void stopNativeAudioSession() {
        unregisterNoisyReceiver();
        abandonNativeAudioFocus();
        resumeOnAudioFocusGain = false;
    }

    private void requestNativeAudioFocus() {
        AudioManager manager = audioManager();
        if (manager == null || audioFocusHeld) return;
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusApi26.request(manager, audioFocusChangeListener, raw -> diagnosticNativeFocus("request", raw, 0));
            result = audioFocusRequest == null ? AudioManager.AUDIOFOCUS_REQUEST_FAILED : AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else {
            result = manager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            diagnosticNativeFocus("request", result, 0);
        }
        audioFocusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!audioFocusHeld && SpiderDebug.isEnabled()) SpiderDebug.log("player", "native audio focus request denied type=%d", playerType);
    }

    private void abandonNativeAudioFocus() {
        if (!audioFocusHeld) return;
        AudioManager manager = audioManager();
        if (manager != null) {
            int result = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? AudioFocusApi26.abandon(manager, audioFocusRequest) : manager.abandonAudioFocus(audioFocusChangeListener);
            diagnosticNativeFocus("abandon", result, 0);
        }
        audioFocusRequest = null;
        audioFocusHeld = false;
    }

    private void registerNoisyReceiver() {
        if (noisyReceiverRegistered) return;
        try {
            App.get().registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
            noisyReceiverRegistered = true;
        } catch (Throwable e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "register noisy receiver failed error=%s", causeChain(e));
        }
    }

    private void unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return;
        try {
            App.get().unregisterReceiver(noisyReceiver);
        } catch (Throwable e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "unregister noisy receiver failed error=%s", causeChain(e));
        }
        noisyReceiverRegistered = false;
    }

    private void onNativeAudioBecomingNoisy() {
        if (!isNativePlayer() || player == null) return;
        boolean wasPlaying = player.isPlaying();
        player.pause();
        stopNativeAudioSession();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "native audio noisy pause type=%d wasPlaying=%s", playerType, wasPlaying);
    }

    private void onNativeAudioFocusChanged(int focusChange) {
        if (!isNativePlayer() || player == null) return;
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnAudioFocusGain) {
                    resumeOnAudioFocusGain = false;
                    startNativeAudioSession(true);
                    player.play();
                }
            }
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                resumeOnAudioFocusGain = player.isPlaying();
                player.pause();
            }
            case AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnAudioFocusGain = false;
                player.pause();
                stopNativeAudioSession();
            }
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "native audio focus changed type=%d change=%d resume=%s", playerType, focusChange, resumeOnAudioFocusGain);
        diagnosticNativeFocus("callback", 0, focusChange);
    }

    private void diagnosticNativeFocus(String action, int result, int change) {
        if (!com.github.catvod.crawler.DebugLogStore.isEnabled()) return;
        com.github.catvod.crawler.DebugLogStore.event(new com.github.catvod.crawler.diagnostics.DiagnosticEvent("audio.focus", getPlaybackTraceId(),
                "native-focus-controller", 0, 0).observed("focusOwner", "App/native-player").observed("focusAction", action)
                .observed("focusGain", AudioManager.AUDIOFOCUS_GAIN).observed("focusResult", result).observed("focusChange", change)
                .observed("usage", android.media.AudioAttributes.USAGE_MEDIA).observed("contentType", android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                .observed("playWhenReady", player != null && player.getPlayWhenReady()).observed("active", audioFocusHeld)
                .observed("metricScope", "controller focus session; callback result is a focus change, not a new request"));
    }

    private AudioManager audioManager() {
        return (AudioManager) App.get().getSystemService(Context.AUDIO_SERVICE);
    }

    private static final class AudioFocusApi26 {

        private static Object request(AudioManager manager, AudioManager.OnAudioFocusChangeListener listener, java.util.function.IntConsumer observe) {
            android.media.AudioAttributes attributes = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build();
            android.media.AudioFocusRequest request = new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(listener)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .build();
            int result = manager.requestAudioFocus(request); observe.accept(result);
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? request : null;
        }

        private static int abandon(AudioManager manager, Object request) {
            return request instanceof android.media.AudioFocusRequest ? manager.abandonAudioFocusRequest((android.media.AudioFocusRequest) request) : AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        }
    }

    private boolean rejectMpvDrmMedia() {
        if (!isMpv() || spec == null || spec.getDrm() == null) return false;
        App.removeCallbacks(runnable);
        clearPendingSwitchRestore();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "reject drm for mpv spec=%s drm=%s", debugSpec(), spec.getDrm().getType());
        callback.onError(ResUtil.getString(R.string.error_play_mpv_drm_unsupported));
        return true;
    }

    private void prepareLutPipeline() {
        lutApplySeq++;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        dynamicLutEffect.clear();
        clearLutWarmupRecovery();
        if (videoEffectsDirty) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "rebuild clean player before media item reason=prepare spec=%s", debugSpec());
            rebuildPlayer();
        }
        if (shouldPreinstallDynamicLutPipeline()
                && safeSetVideoEffects(dynamicLutEffect.effects(), "prepare_dynamic_passthrough")) {
            lutPipelineReadyForItem = true;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "preinstalled dynamic pipeline before playback spec=%s", debugSpec());
            return;
        }
        clearVideoEffects("prepare");
    }

    private boolean shouldPreinstallDynamicLutPipeline() {
        return LutSetting.isEnabled()
                && engine != null
                && !engine.supportsNativeLut()
                && canWarmLutPipeline()
                && TextUtils.isEmpty(getLutUnavailableReason());
    }

    private boolean shouldPrepareLutBeforePlay() {
        return false;
    }

    public void applyLut(boolean notify) {
        applyLut(notify, false);
    }

    public void applyLutPreview(boolean notify) {
        applyLut(notify, true);
    }

    private void applyLut(boolean notify, boolean preview) {
        if (engine == null) return;
        int seq = ++lutApplySeq;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "request seq=%d notify=%s preview=%s enabled=%s preset=%s state=%s videoFormat=%s tracksEmpty=%s active=%s dirty=%s applied=%s applying=%s pendingPreview=%s", seq, notify, preview, LutSetting.isEnabled(), LutSetting.getPresetId(), stateName(player.getPlaybackState()), engine.getVideoFormat(), engine.getCurrentTracks() == null || engine.getCurrentTracks().isEmpty(), videoEffectsActive, videoEffectsDirty, lutAppliedForItem, lutApplyInProgress, pendingLutPreview);
        if (!lutAllowed) {
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("disallowed");
            completeLutBeforePlay("disallowed");
            return;
        }
        if (!LutSetting.isEnabled()) {
            if (waitingLutBeforePlay && shouldWaitForVideoFormat()) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before neutral start reason=off state=%s spec=%s", stateName(player.getPlaybackState()), debugSpec());
                return;
            }
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("off");
            completeLutBeforePlay("off");
            return;
        }
        LutPreset preset = LutStore.find(LutSetting.getPresetId());
        if (preset == null) {
            if (waitingLutBeforePlay && shouldWaitForVideoFormat()) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before neutral start reason=missing state=%s spec=%s", stateName(player.getPlaybackState()), debugSpec());
                return;
            }
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("missing");
            completeLutBeforePlay("missing");
            if (notify) Notify.show(R.string.lut_missing);
            return;
        }
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            if (notify) Notify.show(reason);
            return;
        }
        if (shouldWaitForLutFormat()) {
            lutAppliedForItem = false;
            lutApplyInProgress = false;
            if (notify || preview) pendingLutPreview = preview;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format notify=%s preview=%s state=%s spec=%s", notify, preview, stateName(player.getPlaybackState()), debugSpec());
            return;
        }
        if (notify || preview) pendingLutPreview = preview;
        if (engine.supportsNativeLut()) {
            applyNativeLut(seq, preset, notify, preview);
            return;
        }
        if (!ensureLutPipelineReadyForCurrentItem("request")) {
            return;
        }
        lutAppliedForItem = false;
        lutApplyInProgress = true;
        pendingLutPreview = false;
        int strength = LutSetting.getStrength();
        int previewSeconds = LutSetting.getPreviewSeconds();
        Task.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                ColorLut colorLut = LutEffectFactory.createColorLut(preset, strength);
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "create dynamic preset=%s format=%s strength=%d preview=%s seconds=%d cost=%dms", preset.getId(), preset.getFormat(), strength, preview, previewSeconds, System.currentTimeMillis() - start);
                App.post(() -> applyLutColor(seq, colorLut, notify, preview, previewSeconds));
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "create failed preset=%s strength=%d error=%s", preset.getId(), strength, causeChain(e));
                App.post(() -> {
                    if (seq != lutApplySeq || engine == null) return;
                    lutApplyInProgress = false;
                    setNeutralVideoEffects("error");
                    completeLutBeforePlay("error");
                    if (notify) Notify.show(R.string.lut_apply_failed);
                });
            }
        });
    }

    private void applyNativeLut(int seq, LutPreset preset, boolean notify, boolean preview) {
        lutAppliedForItem = false;
        lutApplyInProgress = true;
        pendingLutPreview = false;
        int strength = LutSetting.getStrength();
        int previewSeconds = LutSetting.getPreviewSeconds();
        Task.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                MpvLutShader shader = MpvLutShaderFactory.create(preset, strength, preview);
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "create shader preset=%s format=%s strength=%d preview=%s seconds=%d clock=monotonic cost=%dms", preset.getId(), preset.getFormat(), strength, preview, previewSeconds, System.currentTimeMillis() - start);
                App.post(() -> applyNativeLutShader(seq, shader, notify, preview));
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "create shader failed preset=%s strength=%d error=%s", preset.getId(), strength, causeChain(e));
                App.post(() -> {
                    if (seq != lutApplySeq || engine == null) return;
                    lutApplyInProgress = false;
                    setNeutralVideoEffects("native_error");
                    completeLutBeforePlay("native_error");
                    if (notify) Notify.show(R.string.lut_apply_failed);
                });
            }
        });
    }

    private void applyNativeLutShader(int seq, MpvLutShader shader, boolean notify, boolean preview) {
        if (seq != lutApplySeq || engine == null) return;
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            lutApplyInProgress = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            if (notify) Notify.show(reason);
            return;
        }
        if (safeSetNativeLut(shader, preview ? "preview_native" : "apply_native")) {
            lutAppliedForItem = true;
            pendingLutPreview = false;
            if (preview) scheduleNativeLutPreviewCommit(seq);
        } else {
            lutAppliedForItem = false;
        }
        lutApplyInProgress = false;
        completeLutBeforePlay(preview ? "preview_native" : "apply_native");
    }

    private void scheduleNativeLutPreviewCommit(int seq) {
        int holdMs = Math.max(1, LutSetting.getPreviewSeconds()) * 1000;
        long slideStartMs = SystemClock.elapsedRealtime() + holdMs;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "preview scheduled seq=%d hold=%d slide=%d", seq, holdMs, MpvLutShaderFactory.PREVIEW_SLIDE_MS);
        App.post(() -> updateNativeLutPreview(seq, slideStartMs), holdMs);
    }

    private void updateNativeLutPreview(int seq, long slideStartMs) {
        if (seq != lutApplySeq || engine == null || !engine.supportsNativeLut()) return;
        if (!lutAllowed || !LutSetting.isEnabled()) return;
        long elapsedMs = Math.max(0, SystemClock.elapsedRealtime() - slideStartMs);
        float progress = Math.min(1f, elapsedMs / (float) MpvLutShaderFactory.PREVIEW_SLIDE_MS);
        engine.setNativeLutPreviewProgress(progress);
        if (progress < 1f) {
            App.post(() -> updateNativeLutPreview(seq, slideStartMs), LUT_PREVIEW_FRAME_INTERVAL_MS);
            return;
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "preview completed seq=%d elapsed=%d", seq, elapsedMs);
    }

    private void applyLutColor(int seq, ColorLut colorLut, boolean notify, boolean preview, int previewSeconds) {
        if (seq != lutApplySeq || engine == null) return;
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            dynamicLutEffect.clear();
            lutApplyInProgress = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            if (notify) Notify.show(reason);
            return;
        }
        if (shouldWaitForLutFormat()) {
            lutAppliedForItem = false;
            lutApplyInProgress = false;
            if (notify || preview) pendingLutPreview = preview;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before set effects preview=%s spec=%s", preview, debugSpec());
            return;
        }
        dynamicLutEffect.set(colorLut, preview, previewSeconds);
        boolean applied = videoEffectsActive;
        if (applied) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "updated dynamic pipeline in place reason=%s preview=%s", preview ? "preview_dynamic" : "apply_dynamic", preview);
        } else {
            applied = safeSetVideoEffects(dynamicLutEffect.effects(), preview ? "preview_dynamic" : "apply_dynamic");
        }
        if (applied) {
            lutAppliedForItem = true;
            pendingLutPreview = false;
        } else {
            lutAppliedForItem = false;
        }
        lutApplyInProgress = false;
        completeLutBeforePlay(preview ? "preview" : "apply");
    }

    private void applyLutForCurrentItem() {
        if (engine == null) return;
        if (!lutAllowed) {
            if (lutAppliedForItem && !videoEffectsActive && !waitingLutBeforePlay) return;
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("auto_disallowed");
            completeLutBeforePlay("auto_disallowed");
            return;
        }
        if (!LutSetting.isEnabled()) {
            if (lutAppliedForItem && !videoEffectsActive && !waitingLutBeforePlay) return;
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("auto_off");
            completeLutBeforePlay("auto_off");
            return;
        }
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            return;
        }
        if (shouldWaitForLutFormat()) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before auto apply state=%s spec=%s", stateName(player.getPlaybackState()), debugSpec());
            return;
        }
        if (lutApplyInProgress) return;
        if (lutAppliedForItem) return;
        if (!ensureLutPipelineReadyForCurrentItem("auto")) return;
        boolean preview = pendingLutPreview || lutWarmupReloadPreviewPending;
        lutWarmupReloadPreviewPending = false;
        applyLut(false, preview);
    }

    private void completeLutBeforePlay(String reason) {
        if (!waitingLutBeforePlay || player == null) return;
        waitingLutBeforePlay = false;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "start playback after lut decision reason=%s active=%s dirty=%s", reason, videoEffectsActive, videoEffectsDirty);
        player.play();
    }

    private boolean ensureLutPipelineReadyForCurrentItem(String reason) {
        if (lutPipelineReadyForItem) return true;
        if (lutPipelinePrepareInProgress) return false;
        if (!canWarmLutPipeline()) return true;
        if (shouldWaitForVideoFormat()) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before pipeline warmup reason=%s state=%s spec=%s", reason, stateName(player.getPlaybackState()), debugSpec());
            return false;
        }
        String unavailable = getLutUnavailableReason();
        if (!TextUtils.isEmpty(unavailable)) return true;
        return prepareLutPipelineForCurrentItem(reason);
    }

    private boolean prepareLutPipelineForCurrentItem(String reason) {
        if (spec == null || engine == null || player == null) return true;
        // 引擎内重启同款口径（照 Exo 重建路）：直接调 engine.restart，不经过 setMediaItemNow，
        // 故这里再走一次分类归一；未转换协议由分类内报错并返回 true，届时返回 false 中止
        // 本轮 LUT 应用（不继续往已报错的 spec 上贴效果），不按旧路直接 restart。
        if (classifyAndNormalizeSpec()) return false;
        lutPipelinePrepareInProgress = true;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        dynamicLutEffect.clear();
        long position = Math.max(0, getPosition());
        boolean playWhenReady = player.getPlayWhenReady();
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        long textOffsetMs = getTextOffsetMs();
        long audioOffsetMs = getAudioOffsetMs();
        if (!safeSetVideoEffects(dynamicLutEffect.effects(), reason + "_prepare_dynamic_passthrough")) {
            lutPipelinePrepareInProgress = false;
            return true;
        }
        lutPipelineReadyForItem = true;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "prepare current item with effects reason=%s position=%d play=%s spec=%s", reason, position, playWhenReady, debugSpec());
        startLutWarmupRecovery();
        initTrack = false;
        if (spec != null) setDanmakus(spec.getDanmakus());
        // X-28 双服务互斥同款：LUT 准备重启引擎前先暂停音乐（仅播着时、try/catch 包住）。
        if (playWhenReady) {
            try {
            } catch (Throwable ignored) {
            }
        }
        engine.restart(spec.checkUa(), position, playWhenReady);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
        setTextOffsetMs(textOffsetMs);
        setAudioOffsetMs(audioOffsetMs);
        // restart 直启路不经 rebuildPlayer，副字幕重放照 rebuildPlayer 口径补上。
        try {
            if (lastSecondarySubtitleTrack != null) engine.setSecondarySubtitleTrack(lastSecondarySubtitleTrack);
        } catch (Throwable ignored) {
        }
        lutPipelinePrepareInProgress = false;
        return false;
    }

    private void startLutWarmupRecovery() {
        lutWarmupRecoveryActive = true;
        lutWarmupRefreshRequested = false;
        lutWarmupRecoveredErrors = 0;
    }

    private void clearLutWarmupRecovery() {
        lutWarmupRecoveryActive = false;
        lutWarmupRefreshRequested = false;
        lutWarmupRecoveredErrors = 0;
    }

    private boolean retryLutWarmupByRefresh(String reason) {
        if (!lutWarmupRecoveryActive || lutWarmupRefreshRequested || !LutSetting.isEnabled()) return false;
        // 持续性预热失败必须有出口：旧路单次旗在 onRefresh/prepare 后被清零
        // 重武装，超时路无计数时会无限“刷新->重解析->预热->失败”。同集最多
        // 2 次，耗尽后返回 false 走正常可见错，不再刷新。
        if (lutWarmupRefreshAttempts >= 2) return false;
        lutWarmupRefreshAttempts++;
        lutWarmupRefreshRequested = true;
        lutWarmupRecoveryActive = false;
        lutWarmupReloadPreviewPending = true;
        App.removeCallbacks(runnable);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "request playback refresh after warmup failure reason=%s errors=%d spec=%s", reason, lutWarmupRecoveredErrors, debugSpec());
        // 调色预热失败不再走 onReload 全量刷新：旧路经 VideoActivity.onRefresh 重跑
        // playerContent 全量重解析，解析开头 Source.stop() 会删掉在播的迅雷任务
        // （磁力在播时调色预热失败即断链）。改走 rebuildAndRestartExo：只重建播放器、
        // 复用当前 spec 地址与进度，不重解析、不碰迅雷任务。stop() 本方法不动。
        // 重建不可用（非 Exo 或空 spec，rebuildAndRestartExo 返回 false）时才回落
        // 旧刷新，避免预热失败后无恢复路；Exo 路不再进旧刷新。
        if (rebuildAndRestartExo("lut-warmup-refresh")) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "warmup failure rebuilt exo, no reparse reason=%s", reason);
            return true;
        }
        if (rebuildAndRestartMpv(null, "lut-warmup-refresh")) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "warmup failure rebuilt mpv, no reparse reason=%s", reason);
            return true;
        }
        callback.onReload(RELOAD_LUT_WARMUP);
        return true;
    }

    private boolean retryLutWarmupByRefresh(PlayerEngine.ErrorAction action, PlaybackException e) {
        if (!lutWarmupRecoveryActive || lutWarmupRefreshRequested || !LutSetting.isEnabled()) return false;
        if (action == PlayerEngine.ErrorAction.RECOVERED && ++lutWarmupRecoveredErrors < LUT_WARMUP_RECOVERED_ERROR_REFRESH_THRESHOLD) return false;
        if (action != PlayerEngine.ErrorAction.RECOVERED && action != PlayerEngine.ErrorAction.FATAL && action != PlayerEngine.ErrorAction.RELOAD) return false;
        return retryLutWarmupByRefresh("error_" + e.errorCode + "_" + action);
    }

    private boolean shouldWaitForLutFormat() {
        if (engine == null || !LutSetting.isEnabled()) return false;
        return shouldWaitForVideoFormat();
    }

    private boolean shouldWaitForVideoFormat() {
        if (engine == null) return false;
        Format currentFormat = engine.getVideoFormat();
        if (isUsableVideoFormat(currentFormat)) return false;
        Tracks tracks = engine.getCurrentTracks();
        if (tracks == null || tracks.isEmpty()) return true;
        boolean hasVideo = false;
        boolean hasUsableFormat = false;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            hasVideo = true;
            for (int i = 0; i < group.length; i++) {
                if (isUsableVideoFormat(group.getTrackFormat(i))) {
                    hasUsableFormat = true;
                    break;
                }
            }
            if (hasUsableFormat) break;
        }
        return hasVideo && !hasUsableFormat;
    }

    private boolean isUsableVideoFormat(Format format) {
        if (format == null) return false;
        return !TextUtils.isEmpty(format.sampleMimeType) || !TextUtils.isEmpty(format.codecs) || format.colorInfo != null || format.width > 0 || format.height > 0;
    }

    private void clearVideoEffects(String reason) {
        dynamicLutEffect.clear();
        if (engine != null && engine.supportsNativeLut()) {
            safeSetNativeLut(null, reason);
            return;
        }
        safeSetVideoEffects(Collections.emptyList(), reason);
    }

    private void setNeutralVideoEffects(String reason) {
        dynamicLutEffect.clear();
        if (engine != null && engine.supportsNativeLut()) {
            safeSetNativeLut(null, reason);
            return;
        }
        if (canKeepWarmNeutralEffects()) safeSetVideoEffects(dynamicLutEffect.effects(), reason + "_dynamic_passthrough");
        else clearVideoEffects(reason);
    }

    private boolean canKeepWarmNeutralEffects() {
        if (!LutSetting.isEnabled()) return false;
        if (!canWarmLutPipeline()) return false;
        if (shouldWaitForVideoFormat()) return false;
        return TextUtils.isEmpty(getLutUnavailableReason());
    }

    private boolean canWarmLutPipeline() {
        if (!lutAllowed) return false;
        if (engine == null || !engine.supportsVideoEffects()) return false;
        if (spec != null && spec.getDrm() != null) return false;
        if (PlayerSetting.isTunnel()) return false;
        if (engine.getDecode() == PlayerEngine.SOFT) return false;
        if (PlayerSetting.isVideoPrefer()) return false;
        return true;
    }

    private boolean safeSetVideoEffects(List<Effect> effects, String reason) {
        if (engine == null) return false;
        boolean empty = effects == null || effects.isEmpty();
        if (empty && !videoEffectsActive) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "skip clear effects reason=%s", reason);
            return false;
        }
        if (!empty && videoEffectsActive) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "reuse active effects reason=%s", reason);
            return true;
        }
        try {
            engine.setVideoEffects(empty ? Collections.emptyList() : effects);
            if (empty) {
                videoEffectsActive = false;
            } else {
                videoEffectsActive = true;
                videoEffectsDirty = true;
            }
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "set effects=%d reason=%s active=%s dirty=%s", empty ? 0 : effects.size(), reason, videoEffectsActive, videoEffectsDirty);
            return true;
        } catch (Throwable e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "set effects failed reason=%s error=%s", reason, causeChain(e));
            return false;
        }
    }

    private boolean safeSetNativeLut(MpvLutShader shader, String reason) {
        if (engine == null || !engine.supportsNativeLut()) return false;
        try {
            engine.setNativeLutShader(shader);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "set shader=%s reason=%s", shader == null ? "none" : shader.diagnostics(), reason);
            return true;
        } catch (Throwable e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "set shader failed reason=%s error=%s", reason, causeChain(e));
            return false;
        }
    }

    private void setDanmakus(List<Danmaku> items) {
        setDanmaku(items == null || items.isEmpty() ? Danmaku.empty() : items.get(0));
    }

    /**
     * 播放时自动搜索弹幕：有弹幕 API 且站源没给弹幕时，按剧名+集数自动匹配。
     * 匹配度不足时静默放弃，不打扰用户，手动搜索仍可用。
     */
    private void autoSearchDanmaku() {
        if (!DanmakuSetting.isAutoDanmaku()) return;
        if (!DanmakuSetting.hasValidApiUrl()) return;
        if (spec == null) return;
        List<Danmaku> siteDanmakus = spec.getDanmakus();
        if (siteDanmakus != null && !siteDanmakus.isEmpty()) return;
        MediaMetadata metadata = getMetadata();
        if (metadata == null) return;
        String title = metadata.title == null ? "" : metadata.title.toString().trim();
        String episode = metadata.artist == null ? "" : metadata.artist.toString().trim();
        if (TextUtils.isEmpty(title)) return;
        String key = title + "\n" + episode;
        if (TextUtils.equals(key, lastAutoDanmakuKey)) return;
        lastAutoDanmakuKey = key;
        int seq = prepareSeq;
        PlaySpec target = spec;
        DanmakuApi.searchBest(title, episode, item -> {
            if (seq != prepareSeq || spec != target) return;
            if (!TextUtils.isEmpty(currentDanmakuUrl)) return;
            setDanmaku(item);
        });
    }

    public void setDanmaku(Danmaku item) {
        setDanmaku(item, false);
    }

    public void reloadDanmaku(Danmaku item) {
        setDanmaku(item, true);
    }

    private void setDanmaku(Danmaku item, boolean force) {
        if (item.isEmpty()) {
            if (spec != null) spec.setDanmaku(item);
            clearDanmaku("empty_source");
            return;
        }
        if (danmakuController == null) return;
        String url = DanmakuUrlPolicy.normalize(DanmakuSetting.getValidApiUrl(), item.getRealUrl());
        DanmakuUrlPolicy.SourceType sourceType = DanmakuUrlPolicy.classify(url);
        if (!sourceType.isSupported()) {
            if (spec != null) spec.setDanmaku(item);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "reject %s", DanmakuUrlPolicy.logSummary(url));
            clearDanmaku("unsupported_source");
            return;
        }
        String key = normalizeDanmakuKey(url);
        if (!force && TextUtils.equals(currentDanmakuUrl, url)) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "skip same %s", DanmakuUrlPolicy.logSummary(url));
            return;
        }
        if (force && shouldSkipForcedDanmakuReload(key)) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "skip duplicate reload key=%s %s", summarizeUrl(key), DanmakuUrlPolicy.logSummary(url));
            return;
        }
        if (spec != null) spec.setDanmaku(item);
        if (force && currentDanmakuUrl != null) danmakuController.clearItems();
        currentDanmakuUrl = url;
        currentDanmakuKey = key;
        if (sourceType.isLive()) {
            danmakuController.clearItems();
            loadingDanmakuKey = null;
            danmakuLoadStartedAtMs = 0;
            danmakuLoadInProgress = false;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "select live source pending websocket connection %s", DanmakuUrlPolicy.logSummary(url));
            liveDanmakuCoordinator.stop("gateway_source");
            if (danmakuForeground && DanmakuSetting.isShow()) connectLiveDanmakuSession(url);
            return;
        }
        stopLiveDanmakuSession("static_source");
        liveDanmakuCoordinator.stop("static_source");
        loadingDanmakuKey = key;
        danmakuLoadStartedAtMs = SystemClock.elapsedRealtime();
        danmakuLoadInProgress = true;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "%s name=%s %s key=%s", force ? "reload" : "load", item.getName(), DanmakuUrlPolicy.logSummary(url), summarizeUrl(key));
        danmakuController.setDataSource(Uri.parse(url));
    }

    private boolean shouldSkipForcedDanmakuReload(String key) {
        if (TextUtils.isEmpty(key) || !TextUtils.equals(currentDanmakuKey, key) || danmakuLoadStartedAtMs <= 0) return false;
        if (danmakuLoadInProgress && (TextUtils.isEmpty(loadingDanmakuKey) || TextUtils.equals(loadingDanmakuKey, key))) return true;
        long elapsed = SystemClock.elapsedRealtime() - danmakuLoadStartedAtMs;
        return elapsed >= 0 && elapsed < DANMAKU_FORCE_RELOAD_DEBOUNCE_MS;
    }

    private void finishDanmakuLoad(Uri uri) {
        String key = normalizeDanmakuKey(uri == null ? "" : uri.toString());
        if (!TextUtils.isEmpty(loadingDanmakuKey) && !TextUtils.equals(loadingDanmakuKey, key)) return;
        danmakuLoadInProgress = false;
        loadingDanmakuKey = null;
    }

    private void clearDanmakuState() {
        currentDanmakuUrl = null;
        currentDanmakuKey = null;
        loadingDanmakuKey = null;
        danmakuLoadStartedAtMs = 0;
        danmakuLoadInProgress = false;
    }

    private void clearDanmaku(String reason) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "clear reason=%s current=%s", reason, DanmakuUrlPolicy.logSummary(currentDanmakuUrl));
        stopLiveDanmakuSession(reason);
        liveDanmakuCoordinator.stop(reason);
        liveDanmakuBatcher.clear();
        liveDanmakuBuffer.clear();
        if (danmakuController != null) danmakuController.clearItems();
        clearDanmakuState();
    }

    private void connectLiveDanmakuSession(String url) {
        if (TextUtils.isEmpty(url)) return;
        if (liveDanmakuSession == null) {
            liveDanmakuSession = new LiveDanmakuWebSocketSession(App.get(), new LiveDanmakuWebSocketSession.Listener() {
                @Override
                public void onStateChanged(LiveDanmakuWebSocketSession.State state, long generation, String sourceUrl, int code, String detail) {
                    liveDanmakuGeneration = generation;
                    liveDanmakuMetrics.onState(state, code, SystemClock.elapsedRealtime());
                    if (state == LiveDanmakuWebSocketSession.State.CONNECTING) {
                        liveDanmakuBuffer.reset(generation);
                        liveDanmakuBatcher.reset(generation);
                        armPendingGatewayLiveMessages(generation);
                    } else if (state == LiveDanmakuWebSocketSession.State.RETRY_WAIT || state == LiveDanmakuWebSocketSession.State.STOPPED || state == LiveDanmakuWebSocketSession.State.RELEASED) {
                        liveDanmakuBatcher.clear();
                        liveDanmakuBuffer.clear();
                        disarmPendingGatewayLiveMessages();
                    }
                    if (state != LiveDanmakuWebSocketSession.State.OPEN) {
                        App.post(() -> logLiveDanmakuMetrics("state_" + state, false));
                        clearLiveDanmakuRenderer(generation);
                    }
                    if (state == LiveDanmakuWebSocketSession.State.CONNECTING || state == LiveDanmakuWebSocketSession.State.OPEN || state == LiveDanmakuWebSocketSession.State.RETRY_WAIT) scheduleLiveDanmakuMetrics();
                    else App.removeCallbacks(liveDanmakuMetricsRunnable);
                    if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku-ws", "state=%s generation=%d code=%d detail=%s %s", state, generation, code, detail, DanmakuUrlPolicy.logSummary(sourceUrl));
                }

                @Override
                public void onMessage(long generation, String text) {
                    if (generation != liveDanmakuGeneration) return;
                    liveDanmakuMetrics.onFrame();
                    long parseStartedNs = SystemClock.elapsedRealtimeNanos();
                    LiveDanmakuParser.Result result = LiveDanmakuParser.parse(text, generation, SystemClock.elapsedRealtime());
                    liveDanmakuMetrics.onParse(result, SystemClock.elapsedRealtimeNanos() - parseStartedNs);
                    if (!result.isAccepted()) return;
                    LiveDanmakuWebSocketSession session = liveDanmakuSession;
                    if (session != null) session.markMessageAccepted(generation);
                    if (result.kind() == LiveDanmakuParser.Kind.ONLINE) {
                        liveDanmakuBuffer.updateOnline(generation, result.online());
                    } else {
                        if (!liveDanmakuPlaybackActive) {
                            // 起播未激活窗：旧口径整条静默丢，与平台路同型（96513940
                            // 只修了平台路、本处当时留痕未动）。改为进网关路自己的
                            // 待喂缓冲、激活后补喂，口径与平台路逐项对齐。
                            bufferPendingGatewayLiveMessage(generation, result.message());
                            return;
                        }
                        offerGatewayLiveMessages(generation, Collections.singletonList(result.message()));
                    }
                }
            });
        }
        liveDanmakuGeneration = liveDanmakuSession.connect(url);
    }

    private void stopLiveDanmakuSession(String reason) {
        if (liveDanmakuSession != null) liveDanmakuGeneration = liveDanmakuSession.stop(reason);
    }

    private void releaseLiveDanmakuSession() {
        if (liveDanmakuSession == null) return;
        liveDanmakuSession.release();
        liveDanmakuSession = null;
    }

    /** 网关路 CONNECTING 时按代武装待喂缓冲（与网关会话的 buffer/batcher reset 同点、同代）。 */
    private void armPendingGatewayLiveMessages(long generation) {
        synchronized (liveDanmakuGatewayPendingMessages) {
            liveDanmakuGatewayPendingMessages.clear();
            liveDanmakuGatewayPendingGeneration = generation;
        }
    }

    /** 网关路停连/待重试/释放：清缓冲并解除武装，旧代消息不许补喂进新房。 */
    private void disarmPendingGatewayLiveMessages() {
        synchronized (liveDanmakuGatewayPendingMessages) {
            liveDanmakuGatewayPendingMessages.clear();
            liveDanmakuGatewayPendingGeneration = -1L;
        }
    }

    /** 只清网关缓冲不清武装：暂停丢弃待喂（discardLiveDanmakuPending）时与平台路同点调用。 */
    private void clearPendingGatewayLiveMessages() {
        synchronized (liveDanmakuGatewayPendingMessages) {
            liveDanmakuGatewayPendingMessages.clear();
        }
    }

    /**
     * 网关消息是 onMessage 逐条解析到达（与平台路整批到达不同），缓冲也逐条收；
     * 其余口径与平台路同：只收武装代、上限同一 LIVE_DANMAKU_PENDING_CAP、超限丢最旧。
     */
    private void bufferPendingGatewayLiveMessage(long generation, LiveDanmakuMessage message) {
        synchronized (liveDanmakuGatewayPendingMessages) {
            // 非武装代不收：已激活过的代在暂停期到达的消息维持旧口径丢弃
            // （暂停丢弃语义，同平台路），不攒着等恢复时倒灌。
            if (generation != liveDanmakuGatewayPendingGeneration) return;
            liveDanmakuGatewayPendingMessages.add(message);
            while (liveDanmakuGatewayPendingMessages.size() > LIVE_DANMAKU_PENDING_CAP) liveDanmakuGatewayPendingMessages.remove(0);
        }
    }

    /** 网关路播放激活（isPlaying 翻 true）时补喂：取出整批、解除武装，复核代与激活态后走与直达同路的 offer。 */
    private void flushPendingGatewayLiveMessages() {
        List<LiveDanmakuMessage> pending;
        long generation;
        synchronized (liveDanmakuGatewayPendingMessages) {
            if (liveDanmakuGatewayPendingMessages.isEmpty()) {
                liveDanmakuGatewayPendingGeneration = -1L;
                return;
            }
            generation = liveDanmakuGatewayPendingGeneration;
            pending = new ArrayList<>(liveDanmakuGatewayPendingMessages);
            liveDanmakuGatewayPendingMessages.clear();
            liveDanmakuGatewayPendingGeneration = -1L;
        }
        if (generation != liveDanmakuGeneration || !liveDanmakuPlaybackActive) return;
        offerGatewayLiveMessages(generation, pending);
    }

    /** 网关路 offer 同路点：直达与补喂共用——offer＋metrics＋requestDrain 一条不少（同平台路 offerPlatformLiveMessages 形态）。 */
    private void offerGatewayLiveMessages(long generation, List<LiveDanmakuMessage> messages) {
        boolean drained = false;
        for (LiveDanmakuMessage message : messages) {
            LiveDanmakuBuffer.OfferResult offer = liveDanmakuBuffer.offer(message);
            liveDanmakuMetrics.onOffer(offer);
            if (offer != LiveDanmakuBuffer.OfferResult.STALE) drained = true;
        }
        if (drained) liveDanmakuBatcher.requestDrain(generation);
    }

    /**
     * 平台直连直播弹幕旁路触发（设计第五节缺口 4）：spec 自带 LIVE 网关弹幕源时网关路优先、
     * 本路不启；否则把装配点信号快照交给 Coordinator 识别，命中平台房间才直连。
     * 只加旁路调用，不改 setDanmakus/autoSearchDanmaku 与起播主链语义。
     */
    private void maybeStartLivePlatformDanmaku() {
        if (spec == null) return;
        if (DanmakuUrlPolicy.classify(currentDanmakuUrl).isLive()) return;
        // 快照由 PlaySpec 组：vodId 优先详情 vod id（play 结果的 Vod 恒空，只用它识别恒落空）
        liveDanmakuCoordinator.onPlayback(spec.liveDanmakuSnapshot());
    }

    /** 切内核/切解码/重解析等重建 spec 的路：详情 vod id 从旧 spec 承继，别让直播弹幕识别断在重建后。 */
    private PlaySpec inheritDetailVodId(PlaySpec fresh) {
        if (fresh != null && fresh.getDetailVodId().isEmpty() && spec != null) fresh.setDetailVodId(spec.getDetailVodId());
        return fresh;
    }

    private void onPlatformLiveState(LiveDanmakuWebSocketSession.State state, long generation, String targetTag, int code, String detail) {
        liveDanmakuGeneration = generation;
        liveDanmakuMetrics.onState(state, code, SystemClock.elapsedRealtime());
        if (state == LiveDanmakuWebSocketSession.State.CONNECTING) {
            liveDanmakuBuffer.reset(generation);
            liveDanmakuBatcher.reset(generation);
            armPendingPlatformLiveMessages(generation);
        } else if (state == LiveDanmakuWebSocketSession.State.RETRY_WAIT || state == LiveDanmakuWebSocketSession.State.STOPPED || state == LiveDanmakuWebSocketSession.State.RELEASED) {
            liveDanmakuBatcher.clear();
            liveDanmakuBuffer.clear();
            disarmPendingPlatformLiveMessages();
        }
        if (state != LiveDanmakuWebSocketSession.State.OPEN) {
            App.post(() -> logLiveDanmakuMetrics("state_" + state, false));
            clearLiveDanmakuRenderer(generation);
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku-live", "state=%s generation=%d code=%d detail=%s target=%s", state, generation, code, detail, targetTag);
    }

    private void onPlatformLiveMessages(long generation, List<LiveDanmakuMessage> messages) {
        if (generation != liveDanmakuGeneration || messages.isEmpty()) return;
        liveDanmakuMetrics.onFrame();
        if (!liveDanmakuPlaybackActive) {
            // 起播未激活窗（WS 已 OPEN、isPlaying 未翻 true）：旧口径整批静默丢，
            // 首帧弹幕必丢（trace 第四节次要项）。改为进待喂缓冲、激活后补喂。
            bufferPendingPlatformLiveMessages(generation, messages);
            return;
        }
        offerPlatformLiveMessages(generation, messages);
    }

    private void offerPlatformLiveMessages(long generation, List<LiveDanmakuMessage> messages) {
        boolean drained = false;
        for (LiveDanmakuMessage message : messages) {
            LiveDanmakuBuffer.OfferResult offer = liveDanmakuBuffer.offer(message);
            liveDanmakuMetrics.onOffer(offer);
            if (offer != LiveDanmakuBuffer.OfferResult.STALE) drained = true;
        }
        if (drained) liveDanmakuBatcher.requestDrain(generation);
    }

    /** CONNECTING 时按代武装待喂缓冲（与 buffer/batcher 的 reset 同点、同代）。 */
    private void armPendingPlatformLiveMessages(long generation) {
        synchronized (liveDanmakuPendingMessages) {
            liveDanmakuPendingMessages.clear();
            liveDanmakuPendingGeneration = generation;
        }
    }

    /** 停连/待重试/释放：清缓冲并解除武装，旧代消息不许补喂进新房。 */
    private void disarmPendingPlatformLiveMessages() {
        synchronized (liveDanmakuPendingMessages) {
            liveDanmakuPendingMessages.clear();
            liveDanmakuPendingGeneration = -1L;
        }
    }

    /** 只清缓冲不清武装：暂停丢弃待喂（与 discardLiveDanmakuPending 同口径）时用。 */
    private void clearPendingPlatformLiveMessages() {
        synchronized (liveDanmakuPendingMessages) {
            liveDanmakuPendingMessages.clear();
        }
    }

    private void bufferPendingPlatformLiveMessages(long generation, List<LiveDanmakuMessage> messages) {
        synchronized (liveDanmakuPendingMessages) {
            // 非武装代不收：已激活过的代在暂停期到达的消息维持旧口径丢弃
            // （暂停丢弃语义，见 fix 报告），不攒着等恢复时倒灌。
            if (generation != liveDanmakuPendingGeneration) return;
            liveDanmakuPendingMessages.addAll(messages);
            while (liveDanmakuPendingMessages.size() > LIVE_DANMAKU_PENDING_CAP) liveDanmakuPendingMessages.remove(0);
        }
    }

    /** 播放激活（isPlaying 翻 true）时补喂：取出整批、解除武装，再走与直达同路的 offer。 */
    private void flushPendingPlatformLiveMessages() {
        List<LiveDanmakuMessage> pending;
        long generation;
        synchronized (liveDanmakuPendingMessages) {
            if (liveDanmakuPendingMessages.isEmpty()) {
                liveDanmakuPendingGeneration = -1L;
                return;
            }
            generation = liveDanmakuPendingGeneration;
            pending = new ArrayList<>(liveDanmakuPendingMessages);
            liveDanmakuPendingMessages.clear();
            liveDanmakuPendingGeneration = -1L;
        }
        if (generation != liveDanmakuGeneration || !liveDanmakuPlaybackActive) return;
        offerPlatformLiveMessages(generation, pending);
    }

    private void onPlatformLiveOnline(long generation, long online) {
        if (generation != liveDanmakuGeneration) return;
        liveDanmakuBuffer.updateOnline(generation, online);
    }

    private void onLiveDanmakuBatch(long generation, List<LiveDanmakuMessage> messages) {
        long scheduledAtMs = SystemClock.elapsedRealtime();
        App.post(() -> {
            if (generation != liveDanmakuGeneration || messages.isEmpty() || danmakuController == null || !DanmakuSetting.isShow()) return;
            liveDanmakuMetrics.onBatch(messages.size(), SystemClock.elapsedRealtime() - scheduledAtMs);
            // 时间戳必须打装配时刻的播放进度：恒 0 会在进度过 3 秒后跌出渲染层
            // 激活窗、弹幕永远不上屏（trace 第三节主断点）。整批共用装配时刻一值。
            long timeMs = LiveDanmakuStamp.timeMsAt(player == null ? 0L : player.getCurrentPosition());
            List<androidx.media3.ui.danmaku.Danmaku> batch = new ArrayList<>(messages.size());
            for (LiveDanmakuMessage message : messages) {
                int pool = message.type() == LiveDanmakuMessage.Type.SUPER_CHAT ? androidx.media3.ui.danmaku.Danmaku.POOL_SPECIAL : androidx.media3.ui.danmaku.Danmaku.POOL_NORMAL;
                long ttlMs = message.type() == LiveDanmakuMessage.Type.SUPER_CHAT ? LiveDanmakuBuffer.DEFAULT_PRIORITY_TTL_MS : LiveDanmakuBuffer.DEFAULT_NORMAL_TTL_MS;
                batch.add(new androidx.media3.ui.danmaku.Danmaku(message.text(), timeMs, androidx.media3.ui.danmaku.Danmaku.TYPE_SCROLL, message.colorArgb(), 0f, pool, "", 0L)
                        .setLiveExpiryElapsedRealtimeMs(message.receivedAtMs() + ttlMs));
            }
            danmakuController.offerLiveBatch(batch);
        });
    }

    private void clearLiveDanmakuRenderer(long generation) {
        App.post(() -> {
            if (generation != liveDanmakuGeneration || danmakuController == null) return;
            danmakuController.clearLiveItems();
        });
    }

    private void discardLiveDanmakuPending() {
        clearPendingPlatformLiveMessages();
        clearPendingGatewayLiveMessages();
        liveDanmakuBuffer.discardPending();
        liveDanmakuBatcher.reset(liveDanmakuGeneration);
        clearLiveDanmakuRenderer(liveDanmakuGeneration);
    }

    private void scheduleLiveDanmakuMetrics() {
        if (!SpiderDebug.isEnabled()) return;
        App.post(liveDanmakuMetricsRunnable, LIVE_DANMAKU_METRICS_INTERVAL_MS);
    }

    private void logLiveDanmakuMetrics(String reason, boolean reschedule) {
        if (!SpiderDebug.isEnabled()) return;
        long nowMs = SystemClock.elapsedRealtime();
        LiveDanmakuMetrics.Snapshot metrics = liveDanmakuMetrics.snapshotAndReset(nowMs);
        LiveDanmakuBuffer.Snapshot buffer = liveDanmakuBuffer.snapshot();
        androidx.media3.ui.danmaku.DanmakuView.LiveStats render = danmakuController == null ? androidx.media3.ui.danmaku.DanmakuView.LiveStats.EMPTY : danmakuController.getLiveStats();
        SpiderDebug.log("danmaku-ws-metrics", "reason=%s state=%s stateMs=%d openMs=%d code=%d received=%d parsed=%d invalid=%d normal=%d super=%d online=%d queued=%d overflow=%d stale=%d batches=%d batchMessages=%d parseAvgUs=%d parseMaxUs=%d mainAvgMs=%d mainMaxMs=%d retries=%d appPending=%d/%d appExpired=%d appHigh=%d renderOffered=%d renderAdmitted=%d renderOverflow=%d renderExpired=%d trackWaits=%d renderPending=%d renderHigh=%d active=%d", reason, metrics.state(), metrics.stateDurationMs(), metrics.openDurationMs(), metrics.lastCode(), metrics.received(), metrics.parsed(), metrics.invalid(), metrics.normal(), metrics.superChat(), metrics.online(), metrics.queued(), metrics.overflow(), metrics.stale(), metrics.batches(), metrics.batchedMessages(), metrics.averageParseNanos() / 1000L, metrics.maxParseNanos() / 1000L, metrics.averageMainDelayMs(), metrics.maxMainDelayMs(), metrics.retryWaits(), buffer.normalPending(), buffer.priorityPending(), buffer.droppedExpired(), buffer.highWaterMark(), render.offered, render.admitted, render.droppedOverflow, render.droppedExpired, render.trackWaits, render.pending, render.highWaterMark, render.active);
        if (reschedule && liveDanmakuSession != null && liveDanmakuSession.state() != LiveDanmakuWebSocketSession.State.STOPPED && liveDanmakuSession.state() != LiveDanmakuWebSocketSession.State.RELEASED) scheduleLiveDanmakuMetrics();
    }

    private void logDanmakuLoad(String event, Uri uri, int count, IOException error) {
        if (!SpiderDebug.isEnabled()) return;
        long elapsed = danmakuLoadStartedAtMs <= 0 ? -1 : SystemClock.elapsedRealtime() - danmakuLoadStartedAtMs;
        if (error == null) {
            SpiderDebug.log("danmaku", "load %s count=%d elapsed=%dms %s", event, count, elapsed, DanmakuUrlPolicy.logSummary(uri == null ? "" : uri.toString()));
        } else {
            SpiderDebug.log("danmaku", "load %s elapsed=%dms %s error=%s", event, elapsed, DanmakuUrlPolicy.logSummary(uri == null ? "" : uri.toString()), error.getMessage());
        }
    }

    private static String normalizeDanmakuKey(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String value = url.trim();
        try {
            Uri uri = Uri.parse(value);
            String nested = getNestedDanmakuUrl(uri);
            return TextUtils.isEmpty(nested) ? value : normalizeDanmakuKey(nested);
        } catch (Throwable e) {
            return value;
        }
    }

    private static String getNestedDanmakuUrl(Uri uri) {
        if (uri == null) return "";
        String path = uri.getPath();
        if (TextUtils.isEmpty(path) || !path.endsWith("/danmaku")) return "";
        return uri.getQueryParameter("url");
    }

    public void addDanmaku(Danmaku item) {
        if (danmakuController == null || item.isEmpty()) return;
        if (spec != null) spec.addDanmaku(item);
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        playbackTrace.mark(PlaybackTrace.Stage.PARSE_COMPLETE, "headers=" + (headers == null ? 0 : headers.size()));
        PlaybackTrace.log("player", playbackTrace.current(), "parseSuccess from=%s url=%s headers=%s", from, summarizeUrl(url), headers == null ? 0 : headers.size());
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        if (spec != null) spec.setHeaders(headers);
        if (TextUtils.isEmpty(url)) {
            callback.onError(ResUtil.getString(R.string.error_play_url));
            return;
        }
        if (spec != null) spec.setUrl(url);
        setMediaItem(Constant.TIMEOUT_PLAY);
        restoreAfterSwitchReparse();
    }

    @Override
    public void onParseError() {
        clearPendingSwitchRestore();
        callback.onError(ResUtil.getString(R.string.error_play_parse));
    }

    private String debugSpec() {
        if (spec == null) return "null";
        return "trace=" + playbackTrace.current() +
                ", keyLen=" + safeLength(spec.getKey()) +
                ", url=" + summarizeUrl(spec.getUrl()) +
                ", format=" + spec.getFormat() +
                ", headers=" + (spec.getHeaders() == null ? 0 : spec.getHeaders().size()) +
                ", subs=" + (spec.getSubs() == null ? 0 : spec.getSubs().size()) +
                ", danmakus=" + (spec.getDanmakus() == null ? 0 : spec.getDanmakus().size());
    }

    private void beginPlaybackTrace(String reason) {
        beginPlaybackTrace(reason, true);
    }

    private void beginPlaybackTrace(String reason, boolean finishCurrentSession) {
        if (finishCurrentSession) {
            endPlaybackTelemetrySession("replace-" + reason);
        }
        playbackBufferingTracker.reset();
        clearExoDecoderResourceRecovery(true);
        playbackTrace.begin();
        long now = SystemClock.elapsedRealtime();
        playbackAutoSession = playbackAutoContextStore.beginSession(playbackTrace.current(), now);
        rtspLiveLagController.beginSession(playbackAutoSession);
        mpvAutoController.beginSession(playbackAutoSession);
        mpvForwardCacheController.beginSession(playbackAutoSession);
        mpvBackCacheController.beginSession(playbackAutoSession);
        mpvCacheTargetCoordinator.beginSession(playbackAutoSession);
        mpvHlsVariantController.beginSession(playbackAutoSession);
        mpvResourcePressureController.beginSession(playbackAutoSession);
        mpvPreloadController.beginSession(playbackAutoSession);
        mpvHlsManagedReload = false;
        playbackTrackSequence = 1;
        playbackMediaFactsCoordinator.beginSession(playbackAutoSession);
        PlaybackMemoryMonitor.process().beginSession(playbackAutoSession);
        PlaybackSystemConditionMonitor.process().beginSession(playbackAutoSession);
        playbackTelemetryCoordinator.beginSession(playbackAutoSession, now);
        beginPlaybackProfileAbSession(now);
        lastLoggedRouteTraceId = PlaybackTrace.NONE;
        bindPlaybackTrace();
        playbackTrace.mark(PlaybackTrace.Stage.REQUEST, "reason=" + reason + " player=" + playerType + " decode=" + (engine == null ? -1 : engine.getDecode()));
        publishPlaybackTelemetry();
        schedulePlaybackTelemetry();
    }

    private void bindPlaybackTrace() {
        if (spec != null) spec.setPlaybackTraceId(playbackTrace.current());
    }

    private void beginPlaybackProfileAbSession(long nowElapsedMs) {
        PlaybackProfileAbPolicy.EnrollmentResolution enrollment =
                PlaybackProfileAbSetting.getEnrollmentResolution();
        PlaybackProfileAbPolicy.Arm arm = currentPlaybackProfileAbArm();
        playbackProfileAbCoordinator.beginSession(
                playbackAutoSession,
                new PlaybackProfileAbCoordinator.StartConfig(
                        enrollment.active()
                                && playbackProfileAbGateOpen()
                                && arm != null,
                        arm,
                        enrollment.enrollment().deviceDigest(),
                        playbackExperimentCoordinator.generation(),
                        Math.abs(userPlaybackSpeed - 1f) < 0.001f),
                nowElapsedMs);
        PlaybackProfileAbPolicy.EnrollmentResolution lightweightEnrollment =
                PlaybackLightweightAssessmentSetting
                        .getEnrollmentResolution();
        PlaybackProfileAbPolicy.Arm lightweightArm =
                currentPlaybackLightweightAssessmentArm();
        playbackLightweightAssessmentCoordinator.beginSession(
                playbackAutoSession,
                new PlaybackProfileAbCoordinator.StartConfig(
                        lightweightEnrollment.active()
                                && playbackProfileAbGateOpen()
                                && lightweightArm != null,
                        lightweightArm,
                        lightweightEnrollment.enrollment().deviceDigest(),
                        playbackExperimentCoordinator.generation(),
                        Math.abs(userPlaybackSpeed - 1f) < 0.001f),
                nowElapsedMs);
    }

    private void observePlaybackProfileAb(
            PlaybackTelemetry.RuntimeObservation observation,
            long nowElapsedMs) {
        if (!playbackAutoSession.active()) return;
        boolean playbackIntended = false;
        if (player != null) {
            try {
                playbackIntended = player.getPlayWhenReady()
                        && (player.isPlaying()
                        || player.getPlaybackState()
                        == Player.STATE_BUFFERING);
            } catch (Throwable ignored) {
            }
        }
        boolean frameSchedulingExperimentActive = false;
        if (isExo()) {
            try {
                var frameScheduling = PlaybackAnalyticsListener
                        .getFrameSchedulingExperimentSnapshot();
                frameSchedulingExperimentActive = frameScheduling.active()
                        && playbackTrace.current().equals(
                        frameScheduling.traceId());
            } catch (Throwable ignored) {
            }
        }
        playbackProfileAbCoordinator.observe(
                playbackAutoSession,
                new PlaybackProfileAbCoordinator.RuntimeInput(
                        PlaybackProfileAbSetting.isEnrolled()
                                && playbackProfileAbGateOpen(),
                        currentPlaybackProfileAbArm(),
                        playbackExperimentCoordinator.generation(),
                        playbackAutoContextStore.snapshot(),
                        observation,
                        playbackIntended,
                        frameSchedulingExperimentActive,
                        false),
                nowElapsedMs);
        playbackLightweightAssessmentCoordinator.observe(
                playbackAutoSession,
                new PlaybackProfileAbCoordinator.RuntimeInput(
                        PlaybackLightweightAssessmentSetting.isEnrolled()
                                && playbackProfileAbGateOpen(),
                        currentPlaybackLightweightAssessmentArm(),
                        playbackExperimentCoordinator.generation(),
                        playbackAutoContextStore.snapshot(),
                        observation,
                        playbackIntended,
                        frameSchedulingExperimentActive,
                        false),
                nowElapsedMs);
    }

    private void finishPlaybackProfileAbSession(
            String reason,
            long nowElapsedMs) {
        playbackProfileAbCoordinator.endSession(
                playbackAutoSession,
                new PlaybackProfileAbCoordinator.EndConfig(
                        PlaybackProfileAbSetting.isEnrolled()
                                && playbackProfileAbGateOpen(),
                        currentPlaybackProfileAbArm(),
                        playbackExperimentCoordinator.generation(),
                        reason),
                nowElapsedMs,
                System.currentTimeMillis());
        playbackLightweightAssessmentCoordinator.endSession(
                playbackAutoSession,
                new PlaybackProfileAbCoordinator.EndConfig(
                        PlaybackLightweightAssessmentSetting.isEnrolled()
                                && playbackProfileAbGateOpen(),
                        currentPlaybackLightweightAssessmentArm(),
                        playbackExperimentCoordinator.generation(),
                        reason),
                nowElapsedMs,
                System.currentTimeMillis());
    }

    private PlaybackProfileAbPolicy.Arm currentPlaybackProfileAbArm() {
        return PlaybackProfileAbPolicy.armForProfile(
                PlaybackPerformanceSetting.getProfile(playerType));
    }

    private PlaybackProfileAbPolicy.Arm
    currentPlaybackLightweightAssessmentArm() {
        return PlaybackLightweightAssessmentPolicy.armForProfile(
                PlaybackPerformanceSetting.getProfile(playerType));
    }

    private void invalidatePlaybackProfileAssessments(
            PlaybackProfileAbCoordinator.InvalidationReason reason) {
        playbackProfileAbCoordinator.invalidate(
                playbackAutoSession, reason);
        playbackLightweightAssessmentCoordinator.invalidate(
                playbackAutoSession, reason);
    }

    private boolean playbackProfileAbGateOpen() {
        return PlaybackProfileAbPolicy.gateAllows(
                PlaybackExperimentSetting.getState(),
                playbackAutoKernel(playerType));
    }

    private PlaybackResourceClassifier.Classification currentResourceClassification() {
        PlaybackResourceClassifier.Classification request = PlaybackResourceClassifier.classifyRequest(
                spec == null ? null : spec.getUrl(),
                spec == null ? null : spec.getFormat(),
                spec == null ? null : spec.getFormat());
        PlaybackResourceClassifier.Classification observed =
                engine == null ? null : engine.getResourceClassification();
        return PlaybackResourceClassifier.merge(request, observed);
    }

    private void publishPlaybackAutoContext(boolean acceptDecoder) {
        if (spec == null || engine == null || !playbackAutoSession.active()) return;
        PlaybackResourceClassifier.Classification classification = currentResourceClassification();
        PlaybackRoute.Resolution observedRoute = engine.getEffectivePlaybackRoute();
        if (observedRoute == null || observedRoute.route() == PlaybackRoute.OTHER) observedRoute = spec.getPlaybackRoute();
        long now = SystemClock.elapsedRealtime();
        PlaybackAutoContext.Fact<PlaybackAutoContext.Kernel> kernel = PlaybackAutoContext.Fact.forSession(
                playbackAutoKernel(playerType), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH, now);
        PlaybackAutoContext.Fact<PlaybackAutoContext.DecodeMode> decode = PlaybackAutoContext.Fact.forSession(
                engine.isHard() ? PlaybackAutoContext.DecodeMode.HARDWARE : PlaybackAutoContext.DecodeMode.SOFTWARE,
                PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST, PlaybackAutoContext.Confidence.HIGH, now);
        PlaybackAutoContext.PathFacts path = classification.toPathFacts(observedRoute, now);
        PlaybackAutoContext.ResourceFacts resource = classification.toResourceFacts(now);
        if (!playbackAutoContextStore.publishPlaybackFacts(playbackAutoSession, kernel, decode, resource, path, now)) return;
        try {
            playbackMediaFactsCoordinator.publishEngineFacts(
                    playbackAutoSession,
                    playbackTrackSequence,
                    engine.getPlaybackFactsSnapshot(),
                    acceptDecoder,
                    now);
        } catch (Throwable error) {
            PlaybackTrace.log("playback-auto-context", playbackTrace.current(),
                    "media facts unavailable type=%s action=keep-partial", error.getClass().getSimpleName());
        }
        PlaybackAutoContext snapshot = playbackAutoContextStore.snapshot();
        if (playbackAutoSession.equals(snapshot.session())) {
            PlaybackTrace.log("playback-auto-context", playbackTrace.current(), "%s", snapshot.logSummary());
            PlaybackTrace.log("playback-auto-resource", playbackTrace.current(), "%s", classification.logSummary());
        }
    }

    private void publishPlaybackTelemetryTick() {
        if (!playbackAutoSession.active()) return;
        publishPlaybackTelemetry();
        schedulePlaybackTelemetry();
    }

    private void publishPlaybackTelemetry() {
        publishPlaybackTelemetry(null);
    }

    private void publishPlaybackTelemetry(PlaybackAutoContext.PlaybackPhase phaseOverride) {
        publishPlaybackTelemetry(phaseOverride, true);
    }

    private void publishPlaybackTelemetry(
            PlaybackAutoContext.PlaybackPhase phaseOverride,
            boolean evaluateMpvHlsVariant) {
        if (!playbackAutoSession.active()) return;
        long now = SystemClock.elapsedRealtime();
        PlaybackTelemetry.RuntimeObservation observation =
                collectPlaybackTelemetry(phaseOverride, now);
        playbackTelemetryCoordinator.publishRuntime(
                playbackAutoSession, observation, now);
        observePlaybackProfileAb(observation, now);
        evaluateExoRtspLiveLag(observation, now);
        if (isMpv()) {
            if (evaluateMpvHlsVariant) {
                evaluateMpvHlsVariant(observation, now);
            }
            evaluateMpvCaches(
                    MpvForwardCacheController.Trigger.RUNTIME,
                    MpvBackCacheController.Trigger.RUNTIME,
                    MpvBackCachePolicy.SeekObservation.none(),
                    now);
        }
    }

    private void evaluateMpvHlsVariant(
            PlaybackTelemetry.RuntimeObservation telemetry,
            long now) {
        if (!(engine instanceof MpvPlayerEngine mpv)
                || !playbackAutoSession.active()) return;
        PlaybackAutoContext context = playbackAutoContextStore.snapshot();
        MpvPlayer.AutoHlsRuntimeSnapshot runtime =
                mpv.getAutoHlsRuntimeSnapshot();
        PlaybackAutoContext.Fact<PlaybackAutoContext.Protocol> protocolFact =
                context.resource().protocol();
        PlaybackAutoContext.Fact<PlaybackAutoContext.StreamKind> streamFact =
                context.resource().streamKind();
        boolean protocolUsable = protocolFact.isUsable(now);
        boolean streamUsable = streamFact.isUsable(now);
        PlaybackTelemetry.Metric<Long> bufferedMetric = telemetry == null
                ? PlaybackTelemetry.Metric.unknown()
                : telemetry.bufferedDurationMs();
        PlaybackTelemetry.Metric<Long> positionMetric = telemetry == null
                ? PlaybackTelemetry.Metric.unknown() : telemetry.positionMs();
        PlaybackTelemetry.Metric<Integer> rebufferMetric = telemetry == null
                ? PlaybackTelemetry.Metric.unknown() : telemetry.rebufferCount();
        boolean buffering = player != null
                && player.getPlaybackState() == Player.STATE_BUFFERING;
        MpvHlsVariantController.RuntimeObservation observation =
                new MpvHlsVariantController.RuntimeObservation(
                        PlaybackPerformanceSetting.isAuto(
                                PlayerSetting.MPV,
                                PlaybackPerformanceCatalog.MPV_HLS_BITRATE)
                                && experimentAllowed(
                                PlaybackExperimentPolicy.Action.MPV_HLS_RUNTIME_RELOAD),
                        isMpv(),
                        MpvPerformanceSetting.isPerformancePriority(),
                        protocolUsable ? protocolFact.value()
                                : PlaybackAutoContext.Protocol.UNKNOWN,
                        protocolUsable,
                        streamUsable ? streamFact.value()
                                : PlaybackAutoContext.StreamKind.UNKNOWN,
                        streamUsable,
                        toPolicyVariants(runtime.variants()),
                        toPolicyVariant(runtime.selectedVariant()),
                        runtime.underrun(),
                        runtime.underrunCount(),
                        rebufferMetric.known()
                                ? Math.max(0, rebufferMetric.value())
                                : getRebufferCount(),
                        buffering,
                        bufferedMetric.known(),
                        bufferedMetric.known()
                                ? Math.max(0, bufferedMetric.value()) : 0,
                        runtime.rawInputBitsPerSecond(),
                        runtime.rawInputRateUsable(),
                        positionMetric.known()
                                ? Math.max(0, positionMetric.value()) : 0);
        MpvHlsVariantController.Decision decision =
                mpvHlsVariantController.evaluateRuntime(
                        playbackAutoSession, context.session(), observation, now);
        MpvHlsApplyResult apply = decision.requestsApply()
                ? executeMpvHlsVariantDecision(mpv, decision, now)
                : MpvHlsApplyResult.notRequested();
        MpvHlsVariantController.Snapshot snapshot =
                mpvHlsVariantController.snapshot();
        if (decision.reason() == MpvHlsVariantController.Reason.ROLLBACK_TIMEOUT
                || decision.requestsApply() && !apply.succeeded()) {
            mpvHlsManagedReload = false;
        }
        PlaybackTelemetry.DecisionOutcome outcome =
                decision.policyReason() == MpvHlsVariantPolicy.Reason.NOT_AUTOMATIC
                        || decision.policyReason()
                        == MpvHlsVariantPolicy.Reason.CONFIG_PRIORITY
                        ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                        : !decision.requestsApply()
                        ? PlaybackTelemetry.DecisionOutcome.HELD
                        : apply.succeeded()
                        ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                        : PlaybackTelemetry.DecisionOutcome.FAILED;
        String suppression = decision.policyReason()
                == MpvHlsVariantPolicy.Reason.NOT_AUTOMATIC
                ? "not-automatic"
                : decision.policyReason()
                == MpvHlsVariantPolicy.Reason.CONFIG_PRIORITY
                ? "mpv-conf-priority"
                : decision.requestsApply() && !apply.started()
                ? "action-rejected"
                : decision.requestsApply() && !apply.optionAccepted()
                ? "native-apply-failed"
                : decision.requestsApply() && !apply.reloadStarted()
                ? "reload-start-failed"
                : decision.reason().label();
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        addNumberInput(inputs, "selected_bps",
                decision.targetBitsPerSecond() > 0
                        ? optionBits(decision.oldOption())
                        : runtime.selectedVariant() == null
                        ? -1 : runtime.selectedVariant().selectionBitsPerSecond(),
                PlaybackAutoContext.ValueSource.MANIFEST,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "target_bps", decision.targetBitsPerSecond(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "raw_input_bps",
                runtime.rawInputRateUsable()
                        ? runtime.rawInputBitsPerSecond() : -1,
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM);
        addNumberInput(inputs, "buffered_ms",
                bufferedMetric.known() ? bufferedMetric.value() : -1,
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH);
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "underrun_count", runtime.underrunCount(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "rebuffer_count", observation.rebufferCount(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "risk_samples", decision.riskSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "hard_risk_samples", decision.hardRiskSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "throughput_risk_samples", decision.throughputRiskSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "buffer_risk_samples", decision.bufferRiskSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "reload_attempts", snapshot.reloadAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "cooldown_ms", decision.cooldownRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_HLS_VARIANT,
                        outcome,
                        hlsOptionLabel(decision.oldOption()),
                        hlsOptionLabel(decision.targetOption()),
                        apply.succeeded()
                                ? hlsOptionLabel(decision.targetOption())
                                : hlsOptionLabel(decision.oldOption()),
                        decision.reason().label(),
                        suppression,
                        inputs),
                now);
    }

    private MpvHlsApplyResult executeMpvHlsVariantDecision(
            MpvPlayerEngine mpv,
            MpvHlsVariantController.Decision decision,
            long now) {
        boolean started = mpvHlsVariantController.beginApply(
                playbackAutoSession, decision, now);
        if (!started) return MpvHlsApplyResult.rejected();
        MpvPlayer.AutoHlsBitrateResult option = mpv.applyAutoHlsBitrate(
                playbackTrace.current(), decision.targetOption());
        boolean reloadStarted = option.accepted()
                && restartMpvHlsVariant(decision);
        boolean succeeded = option.accepted() && reloadStarted;
        if (!succeeded && !TextUtils.isEmpty(decision.oldOption())) {
            mpv.applyAutoHlsBitrate(
                    playbackTrace.current(), decision.oldOption());
        }
        mpvHlsVariantController.completeApply(
                playbackAutoSession,
                decision,
                succeeded,
                option.staged(),
                SystemClock.elapsedRealtime());
        return new MpvHlsApplyResult(
                true, option.accepted(), reloadStarted, succeeded,
                option.label());
    }

    private boolean restartMpvHlsVariant(
            MpvHlsVariantController.Decision decision) {
        if (!decision.reloadsMedia()
                || spec == null
                || TextUtils.isEmpty(spec.getUrl())
                || engine == null
                || player == null) return false;
        // 引擎内重启同款口径（照 Exo 重建路）：直接调 engine.restart，不经过 setMediaItemNow，
        // 故这里再走一次分类归一；未转换协议由分类内报错并返回 true，届时返回 false，
        // 调用方会把变体档位恢复成旧档，不静默停播。
        if (classifyAndNormalizeSpec()) return false;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        long textOffsetMs = getTextOffsetMs();
        long audioOffsetMs = getAudioOffsetMs();
        long position = decision.preservesVodPosition()
                ? decision.resumePositionMs()
                : decision.resumePositionMs() > 0 && getDuration() > 0
                        ? decision.resumePositionMs() : C.TIME_UNSET;
        long fallbackPosition = Math.max(0, getPosition());
        try {
            prepareSeq++;
            App.removeCallbacks(runnable);
            mpvHlsManagedReload = true;
            initTrack = false;
            playWhenReady = wasPlayWhenReady;
            PlaybackTrace.log("mpv-hls-variant", playbackTrace.current(),
                    "action=%s stream=%s target=%d resume=%d play=%s",
                    decision.action().label(), decision.streamKind().label(),
                    decision.targetBitsPerSecond(),
                    position == C.TIME_UNSET ? 0 : position,
                    wasPlayWhenReady);
            if (spec != null) setDanmakus(spec.getDanmakus());
            // X-28 双服务互斥同款：变体重启引擎前先暂停音乐（仅播着时、try/catch 包住；
            // restart 抛错走下方旧档回落起播时，音乐已是暂停态，不必再补一次）。
            if (wasPlayWhenReady) {
                try {
                } catch (Throwable ignored) {
                }
            }
            engine.restart(spec.checkUa(), position, wasPlayWhenReady);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
            setTextOffsetMs(textOffsetMs);
            setAudioOffsetMs(audioOffsetMs);
            // restart 直启路不经 rebuildPlayer，副字幕重放照 rebuildPlayer 口径补上。
            try {
                if (lastSecondarySubtitleTrack != null) engine.setSecondarySubtitleTrack(lastSecondarySubtitleTrack);
            } catch (Throwable error) {
                // 副字幕没恢复不许无声：照本方法外层失败同款写进播放追踪，查日志能定位。
                PlaybackTrace.log("mpv-hls-variant", playbackTrace.current(),
                        "action=%s secondarySubtitleRestore=failed errorType=%s",
                        decision.action().label(),
                        error.getClass().getSimpleName());
            }
            App.post(runnable, Constant.TIMEOUT_PLAY);
            return true;
        } catch (Throwable error) {
            mpvHlsManagedReload = false;
            PlaybackTrace.log("mpv-hls-variant", playbackTrace.current(),
                    "action=%s result=failed errorType=%s",
                    decision.action().label(),
                    error.getClass().getSimpleName());
            // 周期性变体评估失败旧路只记遥测、播放器停在已停态无可见错：
            // restart 已 stop 过，再用变体前的位置起一次旧档，起不来才
            // 返回 false 走可见错，不静默停播。
            try {
                if (spec != null) setDanmakus(spec.getDanmakus());
                engine.start(spec.checkUa(), fallbackPosition, wasPlayWhenReady);
                if (speed != 1f) setSpeed(speed);
                setRepeatOne(repeat);
                setTextOffsetMs(textOffsetMs);
                setAudioOffsetMs(audioOffsetMs);
                App.post(runnable, Constant.TIMEOUT_PLAY);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    private void schedulePlaybackTelemetry() {
        App.removeCallbacks(playbackTelemetryRunnable);
        if (!playbackAutoSession.active() || player == null || player.getPlaybackState() == Player.STATE_ENDED) return;
        App.post(playbackTelemetryRunnable, PLAYBACK_TELEMETRY_INTERVAL_MS);
    }

    private void endPlaybackTelemetrySession(String reason) {
        App.removeCallbacks(playbackTelemetryRunnable);
        if (!playbackAutoSession.active()) return;
        long now = SystemClock.elapsedRealtime();
        PlaybackTelemetry.RuntimeObservation observation =
                collectPlaybackTelemetry(null, now);
        observePlaybackProfileAb(observation, now);
        finishPlaybackProfileAbSession(reason, now);
        playbackTelemetryCoordinator.endSession(
                playbackAutoSession, reason, observation, now);
        rtspLiveLagController.endSession(playbackAutoSession);
    }

    private PlaybackTelemetry.RuntimeObservation collectPlaybackTelemetry(
            PlaybackAutoContext.PlaybackPhase phaseOverride,
            long now) {
        PlaybackAutoContext.PlaybackPhase phaseValue = phaseOverride == null ? playbackPhaseSnapshot() : phaseOverride;
        PlaybackTelemetry.Metric<PlaybackAutoContext.PlaybackPhase> phase = phaseValue == PlaybackAutoContext.PlaybackPhase.UNKNOWN
                ? PlaybackTelemetry.Metric.unknown()
                : PlaybackTelemetry.Metric.of(phaseValue, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH);
        PlaybackTelemetry.Metric<Boolean> loading = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Long> position = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Long> duration = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Long> buffered = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Long> bandwidth = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Long> mediaBitrate = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Float> renderedFrameRate = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Long> droppedFrames = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Long> liveLag = PlaybackTelemetry.Metric.unknown();
        if (player != null) {
            try {
                loading = PlaybackTelemetry.Metric.of(player.isLoading(), PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                        PlaybackAutoContext.Confidence.HIGH);
            } catch (Throwable ignored) {
            }
            try {
                long value = player.getCurrentPosition();
                if (value >= 0) position = PlaybackTelemetry.Metric.of(value, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                        PlaybackAutoContext.Confidence.HIGH);
            } catch (Throwable ignored) {
            }
            try {
                long value = player.getDuration();
                if (value >= 0 && value != C.TIME_UNSET) duration = PlaybackTelemetry.Metric.of(value,
                        PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH);
            } catch (Throwable ignored) {
            }
            try {
                long value = player.getTotalBufferedDuration();
                if (value >= 0) buffered = PlaybackTelemetry.Metric.of(value, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                        PlaybackAutoContext.Confidence.HIGH);
            } catch (Throwable ignored) {
            }
        }
        if (isExo() && playbackTrace.current().equals(PlaybackAnalyticsListener.getPlaybackTraceId())) {
            PlaybackAnalyticsListener.Snapshot analytics = PlaybackAnalyticsListener.getSnapshot();
            if (analytics.bandwidthEstimate() > 0) {
                bandwidth = PlaybackTelemetry.Metric.of(analytics.bandwidthEstimate(), PlaybackAutoContext.ValueSource.ESTIMATOR,
                        PlaybackAutoContext.Confidence.MEDIUM);
            }
            PlaybackAnalyticsListener.DisplayMediaBitrateEstimate media =
                    PlaybackAnalyticsListener.getDisplayMediaBitrateEstimate(getVideoFormat());
            if (media.bitrateBitsPerSecond() > 0) {
                PlaybackAutoContext.ValueSource source = "format".equals(media.source())
                        ? PlaybackAutoContext.ValueSource.PLAYER_CALLBACK : PlaybackAutoContext.ValueSource.ESTIMATOR;
                mediaBitrate = PlaybackTelemetry.Metric.of(media.bitrateBitsPerSecond(), source,
                        telemetryConfidence(media.confidence()));
            }
            PlaybackAnalyticsListener.DisplayFrameRateEstimate frameRate = PlaybackAnalyticsListener.getDisplayFrameRateEstimate();
            if (frameRate.frameRate() > 0 && frameRate.sampleCount() > 0) {
                renderedFrameRate = PlaybackTelemetry.Metric.of(frameRate.frameRate(), PlaybackAutoContext.ValueSource.ESTIMATOR,
                        frameRate.sampleCount() >= 12 ? PlaybackAutoContext.Confidence.HIGH : PlaybackAutoContext.Confidence.MEDIUM);
            }
            if (analytics.everReady() || analytics.videoFormat() != null) {
                droppedFrames = PlaybackTelemetry.Metric.of(Math.max(0, analytics.droppedFrames()),
                        PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH);
            }
            try {
                long value = player.getCurrentLiveOffset();
                if (player.isCurrentMediaItemLive() && value >= 0 && value != C.TIME_UNSET) {
                    liveLag = PlaybackTelemetry.Metric.of(value, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                            PlaybackAutoContext.Confidence.HIGH);
                }
            } catch (Throwable ignored) {
            }
        } else if (engine != null) {
            try {
                PlayerEngine.RuntimeMetrics metrics = engine.getRuntimeMetrics();
                if (metrics.bandwidthBitsPerSecond() != null) bandwidth = PlaybackTelemetry.Metric.of(
                        metrics.bandwidthBitsPerSecond(), PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                        PlaybackAutoContext.Confidence.MEDIUM);
                if (metrics.mediaBitrateBitsPerSecond() != null) mediaBitrate = PlaybackTelemetry.Metric.of(
                        metrics.mediaBitrateBitsPerSecond(), PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                        PlaybackAutoContext.Confidence.MEDIUM);
                if (metrics.renderedFrameRate() != null) renderedFrameRate = PlaybackTelemetry.Metric.of(
                        metrics.renderedFrameRate(), PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                        PlaybackAutoContext.Confidence.MEDIUM);
                if (metrics.droppedFrames() != null) droppedFrames = PlaybackTelemetry.Metric.of(
                        metrics.droppedFrames(), PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                        PlaybackAutoContext.Confidence.HIGH);
            } catch (Throwable error) {
                PlaybackTrace.log("playback-telemetry", playbackTrace.current(),
                        "native metrics unavailable type=%s action=keep-unknown", error.getClass().getSimpleName());
            }
        }
        long firstFrameMs = playbackTrace.stageElapsedMs(PlaybackTrace.Stage.FIRST_FRAME);
        PlaybackTelemetry.Metric<Long> firstFrame = firstFrameMs < 0 ? PlaybackTelemetry.Metric.unknown()
                : PlaybackTelemetry.Metric.of(firstFrameMs, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH);
        logMpvFrameTiming(now);
        return new PlaybackTelemetry.RuntimeObservation(
                phase,
                loading,
                position,
                duration,
                buffered,
                bandwidth,
                mediaBitrate,
                renderedFrameRate,
                droppedFrames,
                PlaybackTelemetry.Metric.of(getRebufferCount(),
                        PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                PlaybackTelemetry.Metric.of(getRebufferTotalMs(now),
                        PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                firstFrame,
                liveLag);
    }

    private void logMpvFrameTiming(long now) {
        if (!SpiderDebug.isEnabled() || !(engine instanceof MpvPlayerEngine mpv)) return;
        if (now - lastMpvFrameTimingLogMs < MPV_FRAME_TIMING_LOG_INTERVAL_MS) return;
        lastMpvFrameTimingLogMs = now;
        MpvPlayer.FrameTimingSnapshot timing = mpv.getFrameTimingSnapshot();
        PlaybackTrace.log("mpv-frame-timing", playbackTrace.current(),
                "dec=%d out=%d mistimed=%d delayed=%d avSyncMs=%d contentFps=%.3f displayFps=%.3f direct=%s observed=%s",
                timing.decoderDroppedFrames(),
                timing.outputDroppedFrames(),
                timing.mistimedFrames(),
                timing.delayedFrames(),
                Math.round(timing.avSyncSeconds() * 1000.0),
                timing.contentFrameRate(),
                timing.displayFrameRate(),
                mpv.isSurfaceDirect(),
                timing.observed());
    }

    private void evaluateExoRtspLiveLag(
            PlaybackTelemetry.RuntimeObservation observation,
            long nowMs) {
        if (!playbackAutoSession.active()) return;
        boolean automatic = PlaybackPerformanceSetting.isAuto(PlayerSetting.EXO)
                && experimentAllowed(
                PlaybackExperimentPolicy.Action.EXO_RTSP_RECOVERY);
        boolean exo = isExo() && engine instanceof ExoPlayerEngine;
        PlaybackResourceClassifier.Classification classification =
                currentResourceClassification();
        boolean rtsp = classification.protocol() == PlaybackAutoContext.Protocol.RTSP;
        boolean classifiedLive = classification.streamKind() == PlaybackAutoContext.StreamKind.LIVE
                || classification.streamKind() == PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE;
        boolean mediaItemLive = false;
        boolean active = false;
        boolean loading = false;
        boolean seekAvailable = false;
        if (player != null) {
            try {
                mediaItemLive = player.isCurrentMediaItemLive();
            } catch (Throwable ignored) {
            }
            try {
                int state = player.getPlaybackState();
                active = player.getPlayWhenReady()
                        && (player.isPlaying()
                        || state == Player.STATE_READY
                        || state == Player.STATE_BUFFERING);
            } catch (Throwable ignored) {
            }
            try {
                loading = player.isLoading();
            } catch (Throwable ignored) {
            }
            try {
                seekAvailable = player.isCommandAvailable(
                        Player.COMMAND_SEEK_TO_DEFAULT_POSITION);
            } catch (Throwable ignored) {
            }
        }
        boolean live = classifiedLive && mediaItemLive;
        boolean startupComplete = playbackTrace.hasStage(PlaybackTrace.Stage.FIRST_FRAME)
                || playbackTrace.hasStage(PlaybackTrace.Stage.AUDIO_PLAYABLE);
        long liveLagMs = metricLong(observation == null ? null : observation.liveLagMs());
        long bufferedMs = metricLong(observation == null ? null : observation.bufferedDurationMs());
        boolean liveEdgeReliable = isReliableDynamicLiveEdge(
                live, seekAvailable, liveLagMs);
        ExoRtspLiveLagPolicy.Decision decision = rtspLiveLagController.evaluate(
                new ExoRtspLiveLagController.Input(
                        playbackAutoSession,
                        automatic,
                        exo,
                        rtsp,
                        live,
                        active,
                        startupComplete,
                        false,
                        loading,
                        liveEdgeReliable,
                        seekAvailable,
                        liveLagMs,
                        bufferedMs,
                        nowMs));
        ExoRtspLiveLagController.Snapshot stateAtDecision =
                rtspLiveLagController.snapshot();

        // Do not fill the shared decision log with inapplicable HLS/DASH/native observations.
        if (!automatic || !exo || !rtsp) return;
        if (!decision.requestsRecovery()) {
            publishExoRtspLiveLagDecision(
                    decision,
                    PlaybackTelemetry.DecisionOutcome.HELD,
                    false,
                    active,
                    loading,
                    seekAvailable,
                    liveEdgeReliable,
                    stateAtDecision,
                    nowMs);
            return;
        }

        boolean started = rtspLiveLagController.beginAction(
                playbackAutoSession, decision.action(), nowMs);
        boolean succeeded = started && executeExoRtspLiveLagRecovery(decision.action());
        if (started) {
            rtspLiveLagController.completeAction(
                    playbackAutoSession, decision.action(), succeeded);
        }
        publishExoRtspLiveLagDecision(
                decision,
                succeeded ? PlaybackTelemetry.DecisionOutcome.APPLIED
                        : PlaybackTelemetry.DecisionOutcome.FAILED,
                succeeded,
                active,
                loading,
                seekAvailable,
                liveEdgeReliable,
                stateAtDecision,
                nowMs);
    }

    private boolean executeExoRtspLiveLagRecovery(
            ExoRtspLiveLagPolicy.Action action) {
        try {
            return switch (action) {
                case SEEK_LIVE_EDGE -> engine instanceof ExoPlayerEngine exo
                        && exo.recoverRtspLiveEdge();
                case REBUILD_SESSION -> restartExoRtspLiveSession();
                case HOLD -> false;
            };
        } catch (Throwable error) {
            PlaybackTrace.log("exo-rtsp-live", playbackTrace.current(),
                    "action=%s result=failed errorType=%s",
                    action.label(), error.getClass().getSimpleName());
            return false;
        }
    }

    private boolean restartExoRtspLiveSession() {
        if (!(engine instanceof ExoPlayerEngine)
                || player == null
                || spec == null
                || TextUtils.isEmpty(spec.getUrl())) {
            return false;
        }
        // 引擎内重启同款口径（照 Exo 重建路）：直接调 engine.restart，不经过 setMediaItemNow，
        // 故这里再走一次分类归一；未转换协议由分类内报错并返回 true，届时返回 false，
        // 调用方按失败处理，不继续重建会话。
        if (classifyAndNormalizeSpec()) return false;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        long textOffsetMs = getTextOffsetMs();
        long audioOffsetMs = getAudioOffsetMs();
        prepareSeq++;
        App.removeCallbacks(runnable);
        App.removeCallbacks(networkProtectionRunnable);
        resetNetworkProtectionSession("rtsp-live-rebuild");
        setDanmakus(spec.getDanmakus());
        initTrack = false;
        waitingLutBeforePlay = false;
        playWhenReady = wasPlayWhenReady;
        applySubtitleStyle();
        PlaybackTrace.log("exo-rtsp-live", playbackTrace.current(),
                "action=rebuild-session play=%s speed=%.3f repeat=%s",
                wasPlayWhenReady, speed, repeat);
        // X-28 双服务互斥同款：RTSP 会话重建重启引擎前先暂停音乐（仅播着时、try/catch 包住）。
        if (wasPlayWhenReady) {
            try {
            } catch (Throwable ignored) {
            }
        }
        engine.restart(spec.checkUa(), C.TIME_UNSET, wasPlayWhenReady);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
        setTextOffsetMs(textOffsetMs);
        setAudioOffsetMs(audioOffsetMs);
        // restart 直启路不经 rebuildPlayer，副字幕重放照 rebuildPlayer 口径补上。
        try {
            if (lastSecondarySubtitleTrack != null) engine.setSecondarySubtitleTrack(lastSecondarySubtitleTrack);
        } catch (Throwable ignored) {
        }
        App.post(runnable, Constant.TIMEOUT_PLAY);
        callback.onPrepare();
        return true;
    }

    private boolean isReliableDynamicLiveEdge(
            boolean live,
            boolean seekAvailable,
            long liveLagMs) {
        if (!live || !seekAvailable || liveLagMs < 0 || player == null) return false;
        try {
            Timeline timeline = player.getCurrentTimeline();
            int index = player.getCurrentMediaItemIndex();
            if (timeline == null || timeline.isEmpty()
                    || index < 0 || index >= timeline.getWindowCount()) {
                return false;
            }
            Timeline.Window window = timeline.getWindow(index, new Timeline.Window());
            return window.isLive() && window.isDynamic;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void publishExoRtspLiveLagDecision(
            ExoRtspLiveLagPolicy.Decision decision,
            PlaybackTelemetry.DecisionOutcome outcome,
            boolean succeeded,
            boolean active,
            boolean loading,
            boolean seekAvailable,
            boolean liveEdgeReliable,
            ExoRtspLiveLagController.Snapshot stateAtDecision,
            long nowMs) {
        ExoRtspLiveLagController.Snapshot snapshot =
                rtspLiveLagController.snapshot();
        ExoRtspLiveLagController.Snapshot decisionState = stateAtDecision == null
                ? snapshot : stateAtDecision;
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "trigger", decision.trigger().label(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(decision.liveLagMs() < 0
                ? PlaybackTelemetry.DecisionInput.unknown("live_lag_ms")
                : PlaybackTelemetry.DecisionInput.number(
                "live_lag_ms", decision.liveLagMs(),
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(decision.lagGrowthMsPerSecond() == Long.MIN_VALUE
                ? PlaybackTelemetry.DecisionInput.unknown("lag_growth_msps")
                : PlaybackTelemetry.DecisionInput.number(
                "lag_growth_msps", decision.lagGrowthMsPerSecond(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(decision.bufferedMs() < 0
                ? PlaybackTelemetry.DecisionInput.unknown("buffered_ms")
                : PlaybackTelemetry.DecisionInput.number(
                "buffered_ms", decision.bufferedMs(),
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(decision.bufferGrowthMsPerSecond() == Long.MIN_VALUE
                ? PlaybackTelemetry.DecisionInput.unknown("buffer_growth_msps")
                : PlaybackTelemetry.DecisionInput.number(
                "buffer_growth_msps", decision.bufferGrowthMsPerSecond(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "active", active,
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "loading", loading,
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "edge_reliable", liveEdgeReliable,
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "seek_available", seekAvailable,
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "risk_samples", decisionState.consecutiveRiskSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "recoveries", snapshot.recoveryAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "cooldown_ms", decision.cooldownRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.RTSP_LIVE_RECOVERY,
                        outcome,
                        decisionState.lastAction().label(),
                        decision.action().label(),
                        succeeded ? decision.action().label() : "hold",
                        decision.reason().label(),
                        decision.requestsRecovery() ? succeeded ? "none" : "action-failed"
                                : decision.reason().label(),
                        inputs),
                nowMs);
    }

    private static long metricLong(PlaybackTelemetry.Metric<Long> metric) {
        return metric == null || !metric.known() || metric.value() < 0
                ? -1 : metric.value();
    }

    private PlaybackAutoContext.PlaybackPhase playbackPhaseSnapshot() {
        if (player == null) return PlaybackAutoContext.PlaybackPhase.IDLE;
        return switch (player.getPlaybackState()) {
            case Player.STATE_BUFFERING -> PlaybackAutoContext.PlaybackPhase.BUFFERING;
            case Player.STATE_READY -> PlaybackAutoContext.PlaybackPhase.READY;
            case Player.STATE_ENDED -> PlaybackAutoContext.PlaybackPhase.ENDED;
            case Player.STATE_IDLE -> spec == null ? PlaybackAutoContext.PlaybackPhase.IDLE : PlaybackAutoContext.PlaybackPhase.PREPARING;
            default -> PlaybackAutoContext.PlaybackPhase.UNKNOWN;
        };
    }

    private static PlaybackAutoContext.Confidence telemetryConfidence(String value) {
        if (value == null) return PlaybackAutoContext.Confidence.UNKNOWN;
        return switch (value) {
            case "high" -> PlaybackAutoContext.Confidence.HIGH;
            case "medium" -> PlaybackAutoContext.Confidence.MEDIUM;
            case "low" -> PlaybackAutoContext.Confidence.LOW;
            default -> PlaybackAutoContext.Confidence.UNKNOWN;
        };
    }

    private void clearPlaybackAutoContext() {
        mpvForwardCacheController.endSession(playbackAutoSession);
        mpvBackCacheController.endSession(playbackAutoSession);
        mpvCacheTargetCoordinator.endSession(playbackAutoSession);
        mpvHlsVariantController.endSession(playbackAutoSession);
        mpvResourcePressureController.endSession(playbackAutoSession);
        mpvPreloadController.endSession(playbackAutoSession);
        mpvHlsManagedReload = false;
        mpvAutoController.endSession(playbackAutoSession);
        PlaybackSystemConditionMonitor.process().endSession(playbackAutoSession);
        PlaybackMemoryMonitor.process().endSession(playbackAutoSession);
        playbackMediaFactsCoordinator.endSession(playbackAutoSession);
        playbackAutoContextStore.clear(playbackAutoSession);
        playbackAutoSession = PlaybackAutoContext.SessionToken.none();
        playbackTrackSequence = 0;
    }

    private static PlaybackAutoContext.Kernel playbackAutoKernel(int playerType) {
        return switch (PlayerSetting.sanitizePlayer(playerType)) {
            case PlayerSetting.MPV -> PlaybackAutoContext.Kernel.MPV;
            default -> PlaybackAutoContext.Kernel.EXO;
        };
    }

    private void logPlaybackRoute() {
        if (spec == null) return;
        String traceId = playbackTrace.current();
        if (traceId.equals(lastLoggedRouteTraceId)) return;
        PlaybackRoute.Resolution resolution = spec.getPlaybackRoute();
        PlaybackTrace.log("playback-route", traceId, "%s", resolution.logSummary());
        DiagnosticControls.forTrace(traceId, "input.route", "resolved-playback-route", e -> e
                .observed("inputRoute", resolution.route().name()).observed("source", resolution.owner().name())
                .observed("reason", resolution.evidence().name()).observed("input", resolution.logSummary())
                .observed("metricScope", "resolved local route; external proxy upstream is not observed"));
        lastLoggedRouteTraceId = traceId;
    }

    private static String summarizeUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        return "scheme=" + (TextUtils.isEmpty(scheme) ? "unknown" : scheme)
                + " len=" + url.length();
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private static String stateName(int state) {
        return switch (state) {
            case Player.STATE_IDLE -> "IDLE";
            case Player.STATE_BUFFERING -> "BUFFERING";
            case Player.STATE_READY -> "READY";
            case Player.STATE_ENDED -> "ENDED";
            default -> String.valueOf(state);
        };
    }

    private void markStartupCompletion(boolean ready, Tracks tracks) {
        if (tracks == null) return;
        boolean hasVideo = tracks.containsType(C.TRACK_TYPE_VIDEO);
        boolean hasAudio = tracks.containsType(C.TRACK_TYPE_AUDIO);
        boolean videoSubmitted = engine instanceof MpvPlayerEngine mpv
                && mpv.hasSubmittedVideoFrame();
        PlaybackStartupPolicy.Completion completion = PlaybackStartupPolicy.resolve(ready, videoSubmitted, hasVideo, hasAudio);
        if (completion == PlaybackStartupPolicy.Completion.FIRST_FRAME) {
            if (playbackTrace.hasStage(PlaybackTrace.Stage.FIRST_FRAME)) return;
            playbackTrace.mark(PlaybackTrace.Stage.FIRST_FRAME, "source=mpv-vo-submitted player=" + playerType);
            } else if (completion == PlaybackStartupPolicy.Completion.AUDIO_PLAYABLE) {
            playbackTrace.mark(PlaybackTrace.Stage.AUDIO_PLAYABLE, "source=ready player=" + playerType);
        }
    }

    private void completeMpvDirectFirstFrame(int state) {
        if (!(engine instanceof MpvPlayerEngine mpv) || !mpv.hasSubmittedVideoFrame()) return;
        if (!MpvAutoOutputPolicy.canRevealDirectFrame(
                MpvPerformanceSetting.getOutputMode() == MpvPerformanceSetting.OUTPUT_AUTO,
                mpvAutoOutputEvaluated,
                state == Player.STATE_READY,
                isMpvSurfaceDirect(),
                getVideoWidth(),
                getVideoHeight())) return;
        mpvAutoOutputFrameReady = true;
        callback.onPlayerOutputReady();
        if (!playbackTrace.hasStage(PlaybackTrace.Stage.FIRST_FRAME)) {
            playbackTrace.mark(PlaybackTrace.Stage.FIRST_FRAME,
                    "source=mpv-vo-submitted-direct player=" + playerType);
            }
        PlaybackTrace.log("mpv-output", playbackTrace.current(),
                "auto shutter release reason=direct-playback-restart size=%dx%d evaluated=%s",
                getVideoWidth(), getVideoHeight(), mpvAutoOutputEvaluated);
    }

    private void recordBufferingState(int state) {
        if (player == null) return;
        if (isExo()
                && state == Player.STATE_BUFFERING
                && !playbackBufferingTracker.isBuffering()
                && PlaybackAnalyticsListener.isSeekRecoveryActive()) {
            PlaybackTrace.log("playback-buffer", playbackTrace.current(),
                    "event=excluded phase=seek outcome=user-action");
            return;
        }
        if (mpvHlsManagedReload
                && state == Player.STATE_BUFFERING
                && !playbackBufferingTracker.isBuffering()) {
            String domain = "mpv-hls-variant";
            PlaybackTrace.log(domain,
                    playbackTrace.current(),
                    "action=managed-reload-buffering result=excluded-from-rebuffer");
            return;
        }
        boolean startupComplete = playbackTrace.hasStage(PlaybackTrace.Stage.FIRST_FRAME) || playbackTrace.hasStage(PlaybackTrace.Stage.AUDIO_PLAYABLE);
        PlaybackBufferingTracker.Event event = playbackBufferingTracker.update(
                state == Player.STATE_BUFFERING,
                startupComplete,
                SystemClock.elapsedRealtime(),
                state,
                currentPositionSnapshot(),
                forwardBufferedSnapshot(),
                loadingSnapshot());
        if (event == null) return;
        PlaybackTrace.log("playback-buffer", playbackTrace.current(),
                "event=%s phase=%s outcome=%s duration=%dms count=%d total=%dms position=%d forward=%d state=%s loading=%s",
                event.type().label(), event.phase().label(), bufferingOutcome(event), event.durationMs(), event.rebufferCount(), event.rebufferTotalMs(),
                event.positionMs(), event.forwardBufferedMs(), stateName(event.playbackState()), event.loading());
    }

    private long currentPositionSnapshot() {
        try {
            return Math.max(0, player.getCurrentPosition());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private long forwardBufferedSnapshot() {
        try {
            return Math.max(0, player.getTotalBufferedDuration());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private boolean loadingSnapshot() {
        try {
            return player.isLoading();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String bufferingOutcome(PlaybackBufferingTracker.Event event) {
        if (event.type() == PlaybackBufferingTracker.Type.START) return "-";
        return switch (event.playbackState()) {
            case Player.STATE_READY -> "ready";
            case Player.STATE_ENDED -> "ended";
            case Player.STATE_IDLE -> "idle";
            default -> "left-buffering";
        };
    }

    private void onMpvHlsPlaybackReady(long now) {
        if (!isMpv() || !playbackAutoSession.active()) return;
        MpvHlsVariantController.Completion completion =
                mpvHlsVariantController.onPlaybackReady(
                        playbackAutoSession, now);
        MpvHlsVariantController.Snapshot snapshot =
                mpvHlsVariantController.snapshot();
        if (snapshot.pendingMode()
                == MpvHlsVariantController.PendingMode.NONE) {
            mpvHlsManagedReload = false;
        }
        if (!completion.changed()) return;
        MpvHlsVariantController.Decision action = snapshot.lastDecision();
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "reload_attempts", snapshot.reloadAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "successful_downgrades", snapshot.successfulDowngrades(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "rollback_count", snapshot.rollbackCount(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "failed_actions", snapshot.failedActions(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        if (engine instanceof MpvPlayerEngine mpv) {
            MpvPlayer.AutoHlsRuntimeSnapshot runtime =
                    mpv.getAutoHlsRuntimeSnapshot();
            inputs.add(PlaybackTelemetry.DecisionInput.number(
                    "native_readbacks", runtime.observedCount(),
                    PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                    PlaybackAutoContext.Confidence.HIGH));
        }
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_HLS_VARIANT,
                        PlaybackTelemetry.DecisionOutcome.APPLIED,
                        hlsOptionLabel(action.oldOption()),
                        hlsOptionLabel(action.targetOption()),
                        hlsOptionLabel(completion.option()),
                        completion.reason().label(),
                        "none",
                        inputs),
                now);
    }

    private boolean recoverMpvHlsVariantError() {
        if (!(engine instanceof MpvPlayerEngine mpv)
                || !playbackAutoSession.active()) return false;
        MpvHlsVariantController.Decision decision =
                mpvHlsVariantController.requestRollbackOnError(
                        playbackAutoSession);
        if (!decision.requestsApply()) {
            if (mpvHlsVariantController.snapshot().pendingMode()
                    == MpvHlsVariantController.PendingMode.ROLLBACK) {
                mpvHlsManagedReload = false;
            }
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        MpvHlsApplyResult apply = executeMpvHlsVariantDecision(
                mpv, decision, now);
        PlaybackTelemetry.DecisionOutcome outcome = apply.succeeded()
                ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                : PlaybackTelemetry.DecisionOutcome.FAILED;
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "resume_position_ms", decision.resumePositionMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "stream", decision.streamKind().label(),
                PlaybackAutoContext.ValueSource.MANIFEST,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_HLS_VARIANT,
                        outcome,
                        hlsOptionLabel(decision.oldOption()),
                        hlsOptionLabel(decision.targetOption()),
                        apply.succeeded()
                                ? hlsOptionLabel(decision.targetOption())
                                : hlsOptionLabel(decision.oldOption()),
                        decision.reason().label(),
                        apply.succeeded() ? "none" : "rollback-start-failed",
                        inputs),
                now);
        if (!apply.succeeded()) mpvHlsManagedReload = false;
        return apply.succeeded();
    }

    private static String trackSummary(Tracks tracks) {
        return "video=" + tracks.containsType(C.TRACK_TYPE_VIDEO) +
                " audio=" + tracks.containsType(C.TRACK_TYPE_AUDIO) +
                " text=" + tracks.containsType(C.TRACK_TYPE_TEXT) +
                " groups=" + tracks.getGroups().size();
    }

    private static String causeChain(Throwable error) {
        if (error == null) return "null";
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (builder.length() > 0) builder.append(" <- ");
            builder.append(current.getClass().getName());
            current = current.getCause();
        }
        return builder.toString();
    }

    public interface Callback {

        void onPrepare();

        void onTracksChanged();

        void onTitlesChanged();

        void onError(String msg);

        void onReload(String msg);

        default void onPlayerRenderRequired() {
        }

        default void onPlayerOutputPending() {
        }

        default void onPlayerOutputReady() {
        }

        default void onExoFirstFrame() {
        }

        void onPlayerRebuild(Player newPlayer, boolean resetVideoSurface);
    }

    private final Player.Listener listener = new Player.Listener() {

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            liveDanmakuPlaybackActive = isPlaying;
            if (isPlaying) flushPendingPlatformLiveMessages();
            if (isPlaying) flushPendingGatewayLiveMessages();
            if (!isPlaying) discardLiveDanmakuPending();
            if (isPlaying) scheduleNetworkProtection(0);
            else if (player.getPlaybackState() == Player.STATE_READY && !player.getPlayWhenReady()) {
                invalidatePlaybackProfileAssessments(
                        PlaybackProfileAbCoordinator.InvalidationReason.PAUSED);
                resetNetworkProtectionSession("paused");
            } else if (player.getPlaybackState() != Player.STATE_BUFFERING) {
                App.removeCallbacks(networkProtectionRunnable);
                networkProtectionController.disrupt(networkProtectionSpeed);
                networkProtectionTrend.reset();
                networkProtectionState = networkProtectionController.getState();
                networkProtectionTier = networkProtectionController.getTier();
            } else {
                // A rebuffer is exactly the evidence the guard needs after playback resumes.
                // Keep the current protection speed, controller history, and forward-buffer
                // trend instead of forcing another warm-up window on every stall.
                App.removeCallbacks(networkProtectionRunnable);
                networkProtectionReason = "buffering-hold";
            }
            publishPlaybackTelemetry();
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            // 起播钟只在 READY/ENDED 摘：IDLE->BUFFERING 是常规第一步，旧路任一
            // 非 IDLE 即摘等于钟刚挂就被摘，流连上不出首帧时只剩转圈无报错。
            if (state == Player.STATE_READY || state == Player.STATE_ENDED) App.removeCallbacks(runnable);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "state=%s spec=%s", stateName(state), debugSpec());
            publishPlaybackAutoContext(state != Player.STATE_IDLE);
            if (state == Player.STATE_READY) {
                completeMpvDirectFirstFrame(state);
                        onMpvHlsPlaybackReady(SystemClock.elapsedRealtime());
                playbackTrace.mark(PlaybackTrace.Stage.READY, "player=" + playerType);
                markStartupCompletion(true, getCurrentTracks());
                hardDecodeSwitchRetryArmed = false;
                clearLutWarmupRecovery();
                applyLutForCurrentItem();
                scheduleNetworkProtection(0);
            } else if (state == Player.STATE_BUFFERING) {
                App.removeCallbacks(networkProtectionRunnable);
                // Do not reset/disrupt the network guard here. BUFFERING is transient and
                // clearing its trend makes repeated stalls permanently outrun the 10 s
                // confirmation window. READY schedules an immediate evaluation using the
                // retained pre-stall trend plus the newly recorded rebuffer count.
                networkProtectionReason = "buffering-hold";
            } else {
                resetNetworkProtectionSession(state == Player.STATE_ENDED ? "ended" : "inactive");
            }
            recordBufferingState(state);
            publishPlaybackTelemetry();
            if (state == Player.STATE_ENDED) {
                App.removeCallbacks(playbackTelemetryRunnable);
                finishPlaybackProfileAbSession(
                        "ended", SystemClock.elapsedRealtime());
            } else {
                schedulePlaybackTelemetry();
            }
        }

        @Override
        public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
            if (isExo()) scheduleNetworkProtection(0);
        }

        @Override
        public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
            rtspLiveLagController.onPositionDiscontinuity(playbackAutoSession);
            resetNetworkProtectionSession("discontinuity-" + reason);
            scheduleNetworkProtection(ExoNetworkGuardController.OBSERVE_INTERVAL_MS);
            if (isMpv()) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    mpvPreloadController.disrupt(playbackAutoSession);
                }
                MpvBackCachePolicy.SeekObservation seek = MpvBackCachePolicy.observeSeek(
                        reason == Player.DISCONTINUITY_REASON_SEEK,
                        oldPosition.mediaItemIndex == newPosition.mediaItemIndex,
                        oldPosition.positionMs,
                        newPosition.positionMs);
                evaluateMpvCaches(
                        null,
                        MpvBackCacheController.Trigger.SEEK,
                        seek,
                        SystemClock.elapsedRealtime());
            }
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize size) {
            videoSize = size;
            publishPlaybackAutoContext(true);
            publishPlaybackTelemetry();
            applyLutForCurrentItem();
            scheduleMpvAutoOutputEvaluation();
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (playbackTrackSequence < Long.MAX_VALUE) playbackTrackSequence++;
            publishPlaybackAutoContext(false);
            if (isExo()) scheduleNetworkProtection(0);
            if (!tracks.isEmpty() && !initTrack) {
                playbackTrace.mark(PlaybackTrace.Stage.TRACKS, trackSummary(tracks));
                restoreTrackSelection(Track.find(getKey()));
                if (engine instanceof MpvPlayerEngine mpv) {
                    mpv.completeInitialSubtitleTrackRestore();
                }
                callback.onTracksChanged();
                initTrack = true;
            }
            markStartupCompletion(player != null && player.getPlaybackState() == Player.STATE_READY, tracks);
            publishPlaybackTelemetry();
            applyLutForCurrentItem();
            scheduleMpvAutoOutputEvaluation();
        }

        @Override
        public void onRenderedFirstFrame() {
            // A rendered video frame proves that the media and video decoder are
            // working even if a slow audio track keeps Exo in BUFFERING briefly.
            // Do not let the generic startup timer turn that valid playback into
            // a false connection-timeout error.
            App.removeCallbacks(runnable);
            playbackTrace.mark(PlaybackTrace.Stage.FIRST_FRAME,
                    "source=" + (isMpv() ? "mpv-vo-submitted" : "media3") + " player=" + playerType);
            if (isMpv()) {
                mpvAutoOutputFrameReady = true;
                callback.onPlayerOutputReady();
            }
            publishPlaybackAutoContext(true);
                publishPlaybackTelemetry();
            if (isExo()) callback.onExoFirstFrame();
        }

        @Override
        public void onMediaEditionsChanged(@NonNull List<MediaEdition> editions) {
            callback.onTitlesChanged();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            App.removeCallbacks(runnable);
            App.removeCallbacks(networkProtectionRunnable);
            if (handleExoDecoderResourcesReclaimed(e)) return;
                rtspLiveLagController.onPlaybackError(playbackAutoSession);
            // Publish the failing runtime snapshot without letting the periodic
            // HLS timeout path start a rollback before this concrete error is
            // classified. A downgrade error gets exactly one rollback attempt;
            // an error from that rollback continues through the normal handler.
            publishPlaybackTelemetry(
                    PlaybackAutoContext.PlaybackPhase.ERROR, false);
            if (recoverMpvHlsVariantError()) return;
            if (retryMpvDv7P81Failure(e)) return;
            if (retryMpvSurfaceDirectFailure(e)) return;
            if (retryMpvVulkanBackendFailure(e)) return;
            if (retryMpvAutoVulkanFailure(e)) return;
            PlaybackErrorClassifier.Failure failure = PlaybackErrorClassifier.classify(e, getEffectivePlaybackRoute());
            PlayerEngine.ErrorAction action = engine.handleError(e);
            PlaybackTrace.log("playback-error", playbackTrace.current(), "%s action=%s player=%d decode=%d", failure.logSummary(), action, playerType, engine.getDecode());
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "error code=%d errorType=%s action=%s retry=%d causeTypes=%s", e.errorCode, e.getClass().getSimpleName(), action, retry, causeChain(e));
            LocalProxyDebug.dumpIfLocalFailure(spec == null ? null : spec.getUrl(), e);
            if (retryLutFailure(e)) return;
            if (retryLutWarmupByRefresh(action, e)) return;
            boolean decoderRuntimeObserved = action == PlayerEngine.ErrorAction.DECODE
                    && engine instanceof ExoPlayerEngine exo
                    && exo.observeDecoderRuntimeFailure(e);
            if (action == PlayerEngine.ErrorAction.DECODE && retryExoTunnelingFailure(e)) return;
            if (decoderRuntimeObserved && retryExoDecoderRuntimeFailure(e)) return;
            if (action == PlayerEngine.ErrorAction.DECODE && retryHardDecodeSwitch(e)) return;
            if (action == PlayerEngine.ErrorAction.FATAL && retryLocalProxy(e)) return;
            if (action == PlayerEngine.ErrorAction.RELOAD) {
                finishPlaybackProfileAbSession(
                        "player-error", SystemClock.elapsedRealtime());
                callback.onReload(getPlaybackErrorMessage(failure));
                return;
            }
            if (action == PlayerEngine.ErrorAction.RECOVERED) {
                if (spec != null) setDanmakus(spec.getDanmakus());
                // 引擎内自救（格式重试/PCM 重启/默认位置）后补挂起播钟，否则
                // 再卡住即永久静默转圈。
                App.post(runnable, Constant.TIMEOUT_PLAY);
                return;
            }
            // 自救分支已返回，切核判断放自救之后：自救成功的错不再被抢去换核，
            // 一次性切核旗不被耗掉。
            if (retryAutoKernelSwitch(failure)) return;
            finishPlaybackProfileAbSession(
                    "player-error", SystemClock.elapsedRealtime());
            callback.onError(getPlaybackErrorMessage(failure));
        }
    };

    private record ExoDecoderResourceRecovery(
            PlaySpec target,
            long positionMs,
            float speed,
            boolean repeat,
            boolean playWhenReady,
            long textOffsetMs,
            long audioOffsetMs) {
    }

    private PlaybackRoute.Resolution getEffectivePlaybackRoute() {
        PlaybackRoute.Resolution route = engine == null ? null : engine.getEffectivePlaybackRoute();
        if (route != null && route.route() != PlaybackRoute.OTHER) return route;
        return spec == null ? PlaybackRoute.resolve(null) : spec.getPlaybackRoute();
    }

    private String getPlaybackErrorMessage(PlaybackErrorClassifier.Failure failure) {
        return switch (failure.stage()) {
            case LOCAL_ENDPOINT -> switch (failure.route().owner()) {
                case APP_MAIN_SERVER, APP_HLS_PROXY -> ResUtil.getString(R.string.error_play_stage_app_local);
                default -> ResUtil.getString(R.string.error_play_stage_external_local);
            };
            case NETWORK_IO -> PlaybackRouteCapabilities.resolve(failure.route()).externalUpstreamOpaque()
                    ? ResUtil.getString(R.string.error_play_stage_external_supply)
                    : ResUtil.getString(R.string.error_play_stage_network);
            case MEDIA_PARSING -> ResUtil.getString(R.string.error_play_stage_media);
            case DECODER -> ResUtil.getString(R.string.error_play_stage_decoder);
            case OUTPUT -> isAudioOutputFailure(failure)
                    ? ResUtil.getString(R.string.error_play_stage_audio_output)
                    : isVideoOutputFailure(failure)
                    ? ResUtil.getString(R.string.error_play_stage_video_output)
                    : ResUtil.getString(R.string.error_play_stage_output);
            case DRM -> ResUtil.getString(R.string.error_play_stage_drm);
            case UNKNOWN -> ResUtil.getString(R.string.error_play_stage_unknown);
        };
    }

    private boolean isAudioOutputFailure(PlaybackErrorClassifier.Failure failure) {
        return hasErrorCode(failure, PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED)
                || hasErrorCode(failure, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED)
                || hasErrorCode(failure, PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED)
                || hasErrorCode(failure, PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED);
    }

    private boolean isVideoOutputFailure(PlaybackErrorClassifier.Failure failure) {
        return hasErrorCode(failure, PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED)
                || hasErrorCode(failure, PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED)
                || "mpv-video-output-marker".equals(failure.evidence());
    }

    private boolean hasErrorCode(
            PlaybackErrorClassifier.Failure failure, int errorCode) {
        return PlaybackException.getErrorCodeName(errorCode).equals(failure.errorCode());
    }

    private boolean handleExoDecoderResourcesReclaimed(
            PlaybackException error) {
        if (error.errorCode
                != PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED) {
            return false;
        }
        if (pendingExoDecoderResourceRecovery != null
                || exoDecoderResourceRecoveryInProgress) {
            PlaybackTrace.log(
                    "exo-decoder-resource",
                    playbackTrace.current(),
                    "action=suppress-duplicate pending=%s inProgress=%s",
                    pendingExoDecoderResourceRecovery != null,
                    exoDecoderResourceRecoveryInProgress);
            return true;
        }
        ExoDecoderResourceRecovery recovery =
                captureExoDecoderResourceRecovery();
        ExoDecoderResourceRecoveryLimiter.Action action =
                exoDecoderResourceRecoveryLimiter.request(
                        error.errorCode,
                        engine instanceof ExoPlayerEngine,
                        engine != null && player != null,
                        recovery != null,
                        playbackForeground);
        if (action
                == ExoDecoderResourceRecoveryLimiter.Action.DEFER_UNTIL_FOREGROUND) {
            hardDecodeSwitchRetryArmed = false;
            pendingExoDecoderResourceRecovery = recovery;
            App.removeCallbacks(playbackTelemetryRunnable);
            resetNetworkProtectionSession("exo-resource-reclaimed-deferred");
            PlaybackTrace.log(
                    "exo-decoder-resource",
                    playbackTrace.current(),
                    "action=defer position=%d play=%s",
                    recovery.positionMs(),
                    recovery.playWhenReady());
            return true;
        }
        if (action
                != ExoDecoderResourceRecoveryLimiter.Action.RECOVER_NOW) {
            PlaybackTrace.log(
                    "exo-decoder-resource",
                    playbackTrace.current(),
                    "action=pass-through reason=%s",
                    action);
            return false;
        }
        hardDecodeSwitchRetryArmed = false;
        scheduleExoDecoderResourceRecovery(recovery, "foreground-error");
        return true;
    }

    @Nullable
    private ExoDecoderResourceRecovery captureExoDecoderResourceRecovery() {
        if (!(engine instanceof ExoPlayerEngine)
                || player == null
                || spec == null
                || spec.getUrl() == null) {
            return null;
        }
        return new ExoDecoderResourceRecovery(
                spec,
                Math.max(0, getPosition()),
                getSpeed(),
                isRepeatOne(),
                player.getPlayWhenReady(),
                getTextOffsetMs(),
                getAudioOffsetMs());
    }

    private void scheduleExoDecoderResourceRecovery(
            ExoDecoderResourceRecovery recovery,
            String reason) {
        if (recovery == null) return;
        if (!playbackForeground) {
            pendingExoDecoderResourceRecovery = recovery;
            PlaybackTrace.log(
                    "exo-decoder-resource",
                    playbackTrace.current(),
                    "action=defer-before-rebuild reason=%s",
                    reason);
            return;
        }
        if (!(engine instanceof ExoPlayerEngine exo)
                || player == null
                || spec != recovery.target()) {
            return;
        }
        exoDecoderResourceRecoveryInProgress = true;
        int seq = ++prepareSeq;
        App.removeCallbacks(runnable);
        App.removeCallbacks(networkProtectionRunnable);
        App.removeCallbacks(playbackTelemetryRunnable);
        resetNetworkProtectionSession("exo-resource-reclaimed");
        rebuildPlayer(true);
        this.playWhenReady = recovery.playWhenReady();
        initTrack = false;
        PlaybackTrace.log(
                "exo-decoder-resource",
                playbackTrace.current(),
                "action=rebuild delay=%d position=%d play=%s reason=%s",
                EXO_DECODER_RESOURCE_RECOVERY_DELAY_MS,
                recovery.positionMs(),
                recovery.playWhenReady(),
                reason);
        App.post(() -> {
            if (seq != prepareSeq
                    || spec != recovery.target()
                    || engine != exo
                    || player == null) {
                exoDecoderResourceRecoveryInProgress = false;
                return;
            }
            if (!playbackForeground) {
                pendingExoDecoderResourceRecovery = recovery;
                exoDecoderResourceRecoveryInProgress = false;
                PlaybackTrace.log(
                        "exo-decoder-resource",
                        playbackTrace.current(),
                        "action=defer-before-prepare");
                return;
            }
            try {
                setDanmakus(recovery.target().getDanmakus());
                waitingLutBeforePlay = false;
                applySubtitleStyle();
                // 复用已分类 spec，故不重复分类；X-28 双服务互斥：真恢复起播前先暂停音乐，
                // 照 setMediaItemNow 的写法（仅 playWhenReady 时、try/catch 包住）。
                if (recovery.playWhenReady()) {
                    try {
                    } catch (Throwable ignored) {
                    }
                }
                engine.start(
                        recovery.target().checkUa(),
                        recovery.positionMs(),
                        recovery.playWhenReady());
                setSpeed(recovery.speed());
                setRepeatOne(recovery.repeat());
                setTextOffsetMs(recovery.textOffsetMs());
                setAudioOffsetMs(recovery.audioOffsetMs());
                App.post(runnable, Constant.TIMEOUT_PLAY);
                callback.onPrepare();
                PlaybackTrace.log(
                        "exo-decoder-resource",
                        playbackTrace.current(),
                        "action=prepare position=%d play=%s",
                        recovery.positionMs(),
                        recovery.playWhenReady());
            } finally {
                exoDecoderResourceRecoveryInProgress = false;
            }
        }, EXO_DECODER_RESOURCE_RECOVERY_DELAY_MS);
    }

    private void clearExoDecoderResourceRecovery(boolean resetBudget) {
        pendingExoDecoderResourceRecovery = null;
        exoDecoderResourceRecoveryInProgress = false;
        if (resetBudget) exoDecoderResourceRecoveryLimiter.reset();
    }

    private boolean retryHardDecodeSwitch(PlaybackException e) {
        // armed 只在“切到 HARD 后”置位，首播 HARD 失败 armed=false 时旧路
        // 永远走不到软解。本批放开 armed 门：软解切换本身只发生一次（切后
        // 引擎即 SOFT，门限 isHard() 挡住二次），不会循环。
        if (engine == null || player == null || spec == null || !engine.isHard()) return false;
        if (e.errorCode != PlaybackException.ERROR_CODE_DECODER_INIT_FAILED && e.errorCode != PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED && e.errorCode != PlaybackException.ERROR_CODE_DECODING_FAILED
                && e.errorCode != PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED && e.errorCode != PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES) return false;
        hardDecodeSwitchRetryArmed = false;
        int seq = ++prepareSeq;
        PlaySpec target = spec;
        long position = Math.max(0, getPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        long textOffsetMs = getTextOffsetMs();
        long audioOffsetMs = getAudioOffsetMs();
        App.removeCallbacks(runnable);
        resetLutRuntimeState("hard_decode_switch_retry", true);
        // R-D4：旧路名叫 switch 实际仍建 HARD，且新引擎构造会把隧道回落
        // 标记归零。改建 SOFT 才是真切换；隧道标记由引擎侧按集保留（见
        // ExoPlayerEngine.tunnelingFallbackAttempted 传入 rebuild）。
        PlayerEngine newEngine = buildEngineOrNull(playerType, PlayerEngine.SOFT);
        if (newEngine == null) return false;
        engine.release();
        engine = newEngine;
        player = engine.getPlayer();
        resetMpvOutputRuntime();
        callback.onPlayerRebuild(player, true);
        this.playWhenReady = wasPlayWhenReady;
        initTrack = false;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "hard decode switch retry delay=%d position=%d errorType=%s", HARD_DECODE_SWITCH_RETRY_DELAY_MS, position, e.getClass().getSimpleName());
        App.post(() -> {
            if (seq != prepareSeq || spec != target || engine == null || player == null || engine.isHard()) return;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "hard decode switch retry start position=%d", position);
            setDanmakus(target.getDanmakus());
            initTrack = false;
            waitingLutBeforePlay = false;
            applySubtitleStyle();
            // 复用已分类 spec，故不重复分类；X-28 双服务互斥：真恢复起播前先暂停音乐，
            // 照 setMediaItemNow 的写法（仅 playWhenReady 时、try/catch 包住）。
            if (wasPlayWhenReady) {
                try {
                } catch (Throwable ignored) {
                }
            }
            engine.start(target.checkUa(), position, wasPlayWhenReady);
            scheduleMpvAutoOutputEvaluation();
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
            setTextOffsetMs(textOffsetMs);
            setAudioOffsetMs(audioOffsetMs);
            App.post(runnable, Constant.TIMEOUT_PLAY);
            callback.onPrepare();
        }, HARD_DECODE_SWITCH_RETRY_DELAY_MS);
        return true;
    }

    private boolean retryExoTunnelingFailure(PlaybackException e) {
        if (!(engine instanceof ExoPlayerEngine exo) || player == null || spec == null) return false;
        if (e.errorCode != PlaybackException.ERROR_CODE_DECODER_INIT_FAILED && e.errorCode != PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED && e.errorCode != PlaybackException.ERROR_CODE_DECODING_FAILED
                && e.errorCode != PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED && e.errorCode != PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES) return false;
        if (!exo.disableTunnelingForSession()) return false;
        return scheduleExoTunnelingFallback(exo);
    }

    /**
     * 隧道看门狗超时入口（引擎侧只上报、不再自行 stop/start，见
     * ExoPlayerEngine.TunnelingWatchdogCallback）：与报错路同款回落——关隧道、
     * 重建、延迟起播，存恢复全套（位置/倍速/循环/偏移）在
     * scheduleExoTunnelingFallback 里统一做。source 必须是当前引擎，
     * 旧引擎的迟到上报直接丢弃。
     */
    private void onExoTunnelingWatchdogTimeout(ExoPlayerEngine source) {
        if (engine != source || player == null || spec == null) return;
        if (!source.disableTunnelingForSession()) return;
        scheduleExoTunnelingFallback(source);
    }

    private boolean scheduleExoTunnelingFallback(ExoPlayerEngine exo) {
        int seq = ++prepareSeq;
        PlaySpec target = spec;
        long position = Math.max(0, getPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        long textOffsetMs = getTextOffsetMs();
        long audioOffsetMs = getAudioOffsetMs();
        App.removeCallbacks(runnable);
        rebuildPlayer(true);
        this.playWhenReady = wasPlayWhenReady;
        initTrack = false;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-tunnel", "fallback scheduled delay=%d position=%d", EXO_TUNNELING_RETRY_DELAY_MS, position);
        App.post(() -> {
            if (seq != prepareSeq || spec != target || engine != exo || player == null) return;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-tunnel", "fallback start position=%d", position);
            setDanmakus(target.getDanmakus());
            waitingLutBeforePlay = false;
            applySubtitleStyle();
            // 复用已分类 spec，故不重复分类；X-28 双服务互斥：真恢复起播前先暂停音乐，
            // 照 setMediaItemNow 的写法（仅 playWhenReady 时、try/catch 包住）。
            if (wasPlayWhenReady) {
                try {
                } catch (Throwable ignored) {
                }
            }
            engine.start(target.checkUa(), position, wasPlayWhenReady);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
            setTextOffsetMs(textOffsetMs);
            setAudioOffsetMs(audioOffsetMs);
            App.post(runnable, Constant.TIMEOUT_PLAY);
            callback.onPrepare();
        }, EXO_TUNNELING_RETRY_DELAY_MS);
        return true;
    }

    private boolean retryExoDecoderRuntimeFailure(PlaybackException e) {
        if (!(engine instanceof ExoPlayerEngine exo)
                || player == null
                || spec == null) {
            return false;
        }
        boolean dolbyVisionFallback =
                exo.isDolbyVisionP81RuntimeFailurePending();
        if (!dolbyVisionFallback
                && !experimentAllowed(
                PlaybackExperimentPolicy.Action.EXO_DECODER_RUNTIME_REBUILD)) {
            return false;
        }
        if (!exo.prepareDecoderRuntimeFallback()) {
            return false;
        }
        hardDecodeSwitchRetryArmed = false;
        int seq = ++prepareSeq;
        PlaySpec target = spec;
        long position = Math.max(0, getPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        long textOffsetMs = getTextOffsetMs();
        long audioOffsetMs = getAudioOffsetMs();
        App.removeCallbacks(runnable);
        rebuildPlayer(true);
        this.playWhenReady = wasPlayWhenReady;
        initTrack = false;
        if (SpiderDebug.isEnabled()) {
            SpiderDebug.log(
                    "exo-decoder-profile",
                    "action=retry-scheduled delay=%d position=%d errorType=%s",
                    EXO_DECODER_RUNTIME_RETRY_DELAY_MS,
                    position,
                    e.getClass().getSimpleName());
        }
        App.post(() -> {
            if (seq != prepareSeq || spec != target || engine != exo || player == null) return;
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log(
                        "exo-decoder-profile",
                        "action=retry-start position=%d",
                        position);
            }
            setDanmakus(target.getDanmakus());
            waitingLutBeforePlay = false;
            applySubtitleStyle();
            // 复用已分类 spec，故不重复分类；X-28 双服务互斥：真恢复起播前先暂停音乐，
            // 照 setMediaItemNow 的写法（仅 playWhenReady 时、try/catch 包住）。
            if (wasPlayWhenReady) {
                try {
                } catch (Throwable ignored) {
                }
            }
            engine.start(target.checkUa(), position, wasPlayWhenReady);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
            setTextOffsetMs(textOffsetMs);
            setAudioOffsetMs(audioOffsetMs);
            App.post(runnable, Constant.TIMEOUT_PLAY);
            callback.onPrepare();
        }, EXO_DECODER_RUNTIME_RETRY_DELAY_MS);
        return true;
    }

    private boolean retryExoDv7FirstFrameTimeout() {
        if (!(engine instanceof ExoPlayerEngine exo)
                || player == null
                || spec == null
                || !exo.prepareDv7Hdr10FallbackForFirstFrameTimeout()) {
            return false;
        }
        int seq = ++prepareSeq;
        PlaySpec target = spec;
        long position = Math.max(0, player.getCurrentPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        long textOffsetMs = getTextOffsetMs();
        long audioOffsetMs = getAudioOffsetMs();
        App.removeCallbacks(runnable);
        rebuildPlayer(true);
        this.playWhenReady = wasPlayWhenReady;
        initTrack = false;
        if (SpiderDebug.isEnabled()) {
            SpiderDebug.log(
                    "exo-dv",
                    "action=first-frame-timeout-fallback-scheduled delay=%d position=%d",
                    EXO_DV7_FIRST_FRAME_FALLBACK_DELAY_MS,
                    position);
        }
        App.post(() -> {
            if (seq != prepareSeq || spec != target || engine != exo || player == null) return;
            setDanmakus(target.getDanmakus());
            waitingLutBeforePlay = false;
            applySubtitleStyle();
            // 复用已分类 spec，故不重复分类；X-28 双服务互斥：真恢复起播前先暂停音乐，
            // 照 setMediaItemNow 的写法（仅 playWhenReady 时、try/catch 包住）。
            if (wasPlayWhenReady) {
                try {
                } catch (Throwable ignored) {
                }
            }
            engine.start(target.checkUa(), position, wasPlayWhenReady);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
            setTextOffsetMs(textOffsetMs);
            setAudioOffsetMs(audioOffsetMs);
            App.post(runnable, Constant.TIMEOUT_PLAY);
            callback.onPrepare();
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log(
                        "exo-dv",
                        "action=first-frame-timeout-fallback-start position=%d",
                        position);
            }
        }, EXO_DV7_FIRST_FRAME_FALLBACK_DELAY_MS);
        return true;
    }

    private boolean retryLutFailure(PlaybackException e) {
        if (!LutSetting.isEnabled()) return false;
        if (e.errorCode != PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED && e.errorCode != PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED) return false;
        App.removeCallbacks(runnable);
        LutSetting.select(null);
        lutAppliedForItem = true;
        lutApplyInProgress = false;
        pendingLutPreview = false;
        lutWarmupReloadPreviewPending = false;
        clearVideoEffects("lut_error_retry");
        Notify.show(R.string.lut_apply_failed);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "disable and retry after frame processing error code=%d spec=%s cause=%s", e.errorCode, debugSpec(), causeChain(e));
        if (spec != null) setMediaItem();
        return true;
    }

    /**
     * 报错后自动切核（双向）：Exo 报错切 MPV、MPV 报错切 Exo，同集只试一次，
     * 不持久化用户偏好（private switchPlayer persist=false），切后仍失败则
     * 落可见错。DRM 与网络/本地端点错误不切核：DRM 改道是另一批活，网络错
     * 换核也救不了，只会多一次无效尝试。
     */
    private boolean retryAutoKernelSwitch(PlaybackErrorClassifier.Failure failure) {
        if (autoKernelSwitchTried || spec == null || spec.getUrl() == null || engine == null || player == null) return false;
        switch (failure.stage()) {
            case DECODER, OUTPUT, MEDIA_PARSING -> {
            }
            default -> {
                return false;
            }
        }
        autoKernelSwitchTried = true;
        int other = playerType == PlayerSetting.MPV ? PlayerSetting.EXO : PlayerSetting.MPV;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "auto kernel switch from=%d to=%d stage=%s spec=%s", playerType, other, failure.stage(), debugSpec());
        switchPlayer(other, false);
        return true;
    }

    private boolean retryLocalProxy(PlaybackException e) {
        if (spec == null || !LocalProxyDebug.isLocalProxyUrl(spec.getUrl())) return false;
        // 旧路只认拒连：超时（代理刚起、未就绪）直接可见错。404 等明确失败
        // 不重试，仍走可见错。
        boolean timeout = e.errorCode == PlaybackException.ERROR_CODE_TIMEOUT
                || (e.getMessage() != null && e.getMessage().toLowerCase(java.util.Locale.US).contains("timed out"));
        if (!timeout && !LocalProxyDebug.isConnectionRefused(e)) return false;
        if (++localProxyRetry > LOCAL_PROXY_MAX_RETRY) return false;
        int attempt = localProxyRetry;
        int seq = prepareSeq;
        PlaySpec target = spec;
        PlayerEngine targetEngine = engine;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy retry schedule attempt=%d delay=%d spec=%s", attempt, LOCAL_PROXY_RETRY_DELAY_MS, debugSpec());
        App.removeCallbacks(runnable);
        App.post(() -> {
            if (spec == null || spec != target || engine != targetEngine || seq != prepareSeq || attempt != localProxyRetry) return;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy retry start attempt=%d spec=%s", attempt, debugSpec());
            setMediaItem();
        }, LOCAL_PROXY_RETRY_DELAY_MS);
        return true;
    }

}
