package com.fongmi.android.tv.ui.activity;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.mpvplayer.MpvPlayer;
import androidx.media3.mpvplayer.MpvDiscMenuPolicy;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackTelemetry;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.exo.ExoOutputModeManager;
import com.fongmi.android.tv.player.exo.ExoOutputModePolicy;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.player.exo.ass.ExoAssSession;
import com.fongmi.android.tv.player.exo.subtitle.ExoSubtitleSession;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.DiscMenuDialog;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.TopProgressView;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.google.common.util.concurrent.ListenableFuture;

public abstract class PlaybackActivity extends BaseActivity implements MediaController.Listener, Player.Listener, ServiceConnection {

    private static final String SIZE_TAG = "MPV_SIZE";

    private ListenableFuture<MediaController> mControllerFuture;
    private MediaController mController;
    private PlaybackService mService;
    private boolean audioOnly;
    private boolean redirect;
    private boolean playbackExiting;
    private boolean resumeOnReturn;
    private String resumeOnReturnUrl;
    private boolean nativeOutputPending;
    private boolean bound;
    private boolean stop;
    private boolean lock;
    private int render = -1;
    private int requestedResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private ExoOutputModeManager exoOutputModeManager;
    private ExoAssSession attachedAssSession;
    private ExoSubtitleSession attachedSubtitleSession;
    private final com.fongmi.android.tv.player.SurfaceDiagnosticCollector surfaceDiagnostics =
            new com.fongmi.android.tv.player.SurfaceDiagnosticCollector();

    protected MediaController controller() {
        return mController;
    }

    protected PlaybackService service() {
        return mService;
    }

    protected PlayerManager player() {
        // 服务未绑/已解绑时返 null（深挖 S1）：子类所有 player() 消费点必须判空，
        // 不许再裸解——服务被系统回收后点播放即 NPE 闪退的根就在这。
        return mService == null ? null : mService.player();
    }

    protected boolean isRedirect() {
        return redirect;
    }

    protected void setRedirect(boolean redirect) {
        this.redirect = redirect;
        if (mService != null) mService.setNavigationCallback(redirect ? null : getNavigationCallback(), getPlaybackKey());
    }

    protected boolean isPlaybackExiting() {
        return playbackExiting;
    }

    protected void markPlaybackExiting() {
        this.playbackExiting = true;
    }

    protected void finishPlayback() {
        markPlaybackExiting();
        stopPlayback();
        finish();
    }

    protected void stopPlayback() {
        if (mService != null && isOwner()) {
            mService.shutdown();
        } else if (mController != null) {
            mController.stop();
        }
    }

    protected void updateNavigationKey() {
        if (mService != null) mService.setNavigationCallback(getNavigationCallback(), getPlaybackKey());
    }

    protected boolean isAudioOnly() {
        return audioOnly;
    }

    protected void setAudioOnly(boolean audioOnly) {
        this.audioOnly = audioOnly;
    }

    protected boolean isStop() {
        return stop;
    }

    protected void setStop(boolean stop) {
        this.stop = stop;
    }

    protected boolean isLock() {
        return lock;
    }

    protected void setLock(boolean lock) {
        this.lock = lock;
    }

    protected abstract PlaybackService.NavigationCallback getNavigationCallback();

    protected abstract CustomSeekView getSeekView();

    /** 顶部细进度条：子类覆写提供视图；默认不提供则不接进度监听（投屏/直播页）。 */
    protected TopProgressView getTopProgressView() {
        return null;
    }

    private void setTopProgressPlayer(Player player) {
        TopProgressView v = getTopProgressView();
        if (v != null) v.setProgressPlayer(player);
    }

    protected abstract PlayerView getExoView();

    protected abstract String getPlaybackKey();

    protected boolean deferPlaybackServiceBinding() {
        return false;
    }

    protected boolean isOwner() {
        String key = getPlaybackKey();
        if (key == null) return true;
        PlayerManager manager = player();
        return manager != null && key.equals(manager.getKey());
    }

    protected boolean isIdle() {
        return mController == null || mController.getPlaybackState() == Player.STATE_IDLE;
    }

    protected boolean isEnded() {
        return mController != null && mController.getPlaybackState() == Player.STATE_ENDED;
    }

    protected boolean isBuffering() {
        return mController != null && mController.getPlaybackState() == Player.STATE_BUFFERING;
    }

    protected boolean isPaused() {
        return !isBuffering() && !isIdle();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (dispatchDiscMenuKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    protected boolean dispatchDiscMenuKey(KeyEvent event) {
        if (mService == null || !isOwner() || event == null) return false;
        Player active = player().getPlayer();
        if (!(active instanceof MpvPlayer mpv)) return false;
        String action = MpvDiscMenuPolicy.keyAction(event.getKeyCode());
        if (action == null) return false;
        if (!mpv.isDiscMenuActive()
                && !(event.getKeyCode() == KeyEvent.KEYCODE_MENU && mpv.isDiscMenuAvailable())) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return true;
        mpv.sendDiscNav(action);
        return true;
    }

    protected boolean hasDiscMenu() {
        return mService != null && isOwner() && player().getPlayer() instanceof MpvPlayer mpv
                && mpv.isDiscMenuAvailable();
    }

    protected boolean hasDiscNavigationTimeline() {
        return mService != null && isOwner() && player().getPlayer() instanceof MpvPlayer mpv
                && mpv.hasDiscNavigationTimeline();
    }

    protected boolean canSavePlaybackHistory(History history) {
        if (history == null) return false;
        boolean navigationStarted = mService != null && isOwner()
                && player().getPlayer() instanceof MpvPlayer mpv
                && mpv.hasStartedDiscNavigation();
        return MpvDiscMenuPolicy.canSaveHistory(history.canSave(), navigationStarted);
    }

    protected void showDiscMenuControls() {
        if (hasDiscMenu()) DiscMenuDialog.show(this, (MpvPlayer) player().getPlayer());
    }

    protected void openDiscMenu() {
        if (hasDiscMenu()) ((MpvPlayer) player().getPlayer()).sendDiscNav("menu");
    }

    protected boolean dispatchDiscMenuTouch(MotionEvent event) {
        if (isLock() || mService == null || !isOwner()) return false;
        Player active = player().getPlayer();
        if (!(active instanceof MpvPlayer mpv) || !mpv.isDiscMenuActive()) return false;
        View surface = getExoView().getVideoSurfaceView();
        if (surface == null || event.getPointerCount() != 1) return false;
        int[] origin = new int[2];
        surface.getLocationOnScreen(origin);
        int pointerX = Math.round(event.getRawX() - origin[0]);
        int pointerY = Math.round(event.getRawY() - origin[1]);
        if (pointerX < 0 || pointerY < 0 || pointerX >= surface.getWidth() || pointerY >= surface.getHeight()) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                && event.getEventTime() - event.getDownTime() >= ViewConfiguration.getLongPressTimeout()) {
            showDiscMenuControls();
        } else if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                || event.getActionMasked() == MotionEvent.ACTION_MOVE
                || event.getActionMasked() == MotionEvent.ACTION_UP) {
            mpv.sendDiscNavPointer(pointerX, pointerY, event.getActionMasked() == MotionEvent.ACTION_UP);
        }
        return true;
    }

    protected void onServiceConnected() {
    }

    protected boolean isLutAllowed() {
        return true;
    }

    protected void onPrepare() {
    }

    protected void onExoFirstFrame() {
        View progress = getExoView().getRootView().findViewById(R.id.progress);
        if (progress != null) progress.setVisibility(View.GONE);
    }

    /**
     * 首帧真正上屏的统一钩子：Exo 走首帧回调、原生 MPV 走输出就绪（与原生遮罩
     * 放行同一时机）。子类可据此撤除切集盖图等过渡层，不许用定时器猜。
     */
    protected void onVideoFirstFrame() {
    }

    protected void onTracksChanged() {
    }

    protected void onTitlesChanged() {
    }

    protected void onPlayerRebuilt() {
    }

    protected void onControllerReady(Player controller) {
    }

    protected void onPlayerPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
    }

    protected void onError(String msg) {
    }

    protected void onReload(String msg) {
        onError(msg);
    }

    protected void onPlayingChanged(boolean isPlaying) {
    }

    protected void onStateChanged(int state) {
    }

    protected void onSizeChanged(VideoSize size) {
    }

    protected void onSurfaceAttached() {
    }

    protected void applyResizeMode(int resizeMode) {
        requestedResizeMode = resizeMode;
        int effectiveResizeMode = effectiveResizeMode(resizeMode);
        logSurfaceState("applyResizeMode before mode=" + resizeMode + " effective=" + effectiveResizeMode);
        PlayerView view = getExoView();
        view.setResizeMode(effectiveResizeMode);
        view.requestLayout();
        View surface = view.getVideoSurfaceView();
        if (surface != null) surface.requestLayout();
        logSurfaceState("applyResizeMode after mode=" + resizeMode + " effective=" + effectiveResizeMode);
    }

    private int effectiveResizeMode(int resizeMode) {
        PlayerManager manager = player();
        if (manager != null && manager.isMpv() && !manager.isMpvSurfaceDirect() && resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
            return AspectRatioFrameLayout.RESIZE_MODE_FILL;
        }
        return resizeMode;
    }

    protected void onReclaim() {
    }

    protected boolean seekTo(long deltaMs) {
        PlayerManager manager = player();
        if (manager == null || mController == null) return false;
        long targetMs = Math.max(0, manager.getPosition() + deltaMs);
        long durationMs = manager.getDuration();
        boolean seekToEnd = durationMs > 0 && targetMs >= durationMs;
        mController.seekTo(seekToEnd ? durationMs : targetMs);
        if (!seekToEnd) mController.play();
        return seekToEnd;
    }

    protected void startPlayer(String key, Result result, boolean useParse, long timeout, MediaMetadata metadata) {
        startPlayer(key, result, useParse, timeout, metadata, C.TIME_UNSET);
    }

    protected void startPlayer(String key, Result result, boolean useParse, long timeout,
                               MediaMetadata metadata, long startPositionMs) {
        if (rejectUnsupportedDrm(key, result)) {
            return;
        } else if (result.getDrm() != null && !FrameworkMediaDrm.isCryptoSchemeSupported(result.getDrm().getUUID())) {
            onError(ResUtil.getString(R.string.error_play_drm));
        } else if (result.hasMsg()) {
            onError(result.getMsg());
        } else if (result.getRealUrl().isEmpty()) {
            onError(ResUtil.getString(R.string.error_play_url));
        } else if (result.needParse() || useParse) {
            attachSurface();
            player().parse(key, result, useParse, metadata, PlayerSetting.isAutoPlay(), startPositionMs);
            PlaySpec spec = player().getSpec();
            if (spec != null) spec.setDetailVodId(getDetailVodId());
        } else {
            attachSurface();
            player().start(PlaySpec.from(result, key, metadata).setDetailVodId(getDetailVodId()), timeout, PlayerSetting.isAutoPlay(), startPositionMs);
        }
    }

    /** 详情 vod id（详情页起播装配时写入 PlaySpec，供直播平台弹幕识别）；非详情页默认无。 */
    protected String getDetailVodId() {
        return "";
    }

    private boolean rejectUnsupportedDrm(String key, Result result) {
        if (result == null || result.getDrm() == null || !isSelectedMpvPlayer()) return false;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "reject drm for mpv key=%s drm=%s", key, result.getDrm().getType());
        onError(ResUtil.getString(R.string.error_play_mpv_drm_unsupported));
        return true;
    }

    private boolean isSelectedMpvPlayer() {
        return mService != null ? player().isMpv() : PlayerSetting.getPlayer() == PlayerSetting.MPV;
    }

    private void bindPlaybackService() {
        if (bound) return;
        long start = System.currentTimeMillis();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "bind service start key=%s", getPlaybackKey());
        startService(new Intent(this, PlaybackService.class));
        bindService(new Intent(this, PlaybackService.class).setAction(PlaybackService.LOCAL_BIND_ACTION), this, BIND_AUTO_CREATE);
        buildControllerAsync();
        bound = true;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "bind service requested cost=%dms key=%s", System.currentTimeMillis() - start, getPlaybackKey());
    }

    private void bindPlaybackServiceAfterFirstFrame() {
        View root = getExoView().getRootView();
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(this);
                root.post(() -> {
                    if (!isFinishing() && !isDestroyed()) bindPlaybackService();
                });
                return true;
            }
        });
    }

    private void buildControllerAsync() {
        long start = System.currentTimeMillis();
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        mControllerFuture = new MediaController.Builder(this, token).setListener(this).buildAsync();
        mControllerFuture.addListener(this::onControllerConnected, ContextCompat.getMainExecutor(this));
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "controller build requested cost=%dms key=%s", System.currentTimeMillis() - start, getPlaybackKey());
    }

    private void onControllerConnected() {
        long start = System.currentTimeMillis();
        try {
            mController = mControllerFuture.get();
            getSeekView().setPlayer(mController);
            setTopProgressPlayer(mController);
            onControllerReady(mController);
            mController.addListener(this);
        } catch (Exception ignored) {
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "controller connected cost=%dms key=%s", System.currentTimeMillis() - start, getPlaybackKey());
    }

    private PendingIntent buildSessionIntent() {
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        Bundle extras = getIntent().getExtras();
        if (extras != null) intent.putExtras(extras);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private boolean shouldReclaim() {
        return mService != null && !isOwner();
    }

    private void closePiP() {
        if (!isInPictureInPictureMode()) return;
        detach();
        finish();
    }

    protected void attachSurface() {
        if (mService == null) return;
        int targetRender = getRender();
        logSurfaceState("attach start target=" + targetRender);
        // 切集重挂面时播放器与渲染方式都没变：不强制拉黑遮罩，旧集画面留到新集
        // 首帧顶上，避免每次切集先黑一下；换渲染方式/播放器未挂时仍照旧拉黑恢复。
        boolean steadySurface = !player().isNativePlayer() && getExoView().getPlayer() != null && render == targetRender;
        if (!steadySurface) syncShutter(true);
        if (render != targetRender) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "switch render from=%d to=%d", render, targetRender);
            if (getExoView().getPlayer() != null) getExoView().setPlayer(null);
            getExoView().setRender(targetRender);
            render = targetRender;
            syncShutter(true);
            logSurfaceState("attach after setRender target=" + targetRender);
        }
        surfaceDiagnostics.bind(getExoView(), player().getPlaybackTraceId());
        if (getExoView().getPlayer() == null) {
            getExoView().setPlayer(player().getPlayer());
            logSurfaceState("attach after setPlayer");
            syncVideoSurfaceSize(null);
            syncShutter();
            if (player().isNativePlayer()) getExoView().post(this::syncShutter);
        }
        publishRenderTarget(getExoView().getVideoSurfaceView());
        ExoAssSession assSession = player().getAssSession();
        if (attachedAssSession != assSession) {
            detachAssSurface();
            attachedAssSession = assSession;
        }
        if (attachedAssSession != null) attachedAssSession.attach(getExoView());
        ExoSubtitleSession subtitleSession = player().getSubtitleSession();
        if (attachedSubtitleSession != subtitleSession) {
            if (attachedSubtitleSession != null) attachedSubtitleSession.detach();
            attachedSubtitleSession = subtitleSession;
        }
        if (attachedSubtitleSession != null) attachedSubtitleSession.attach(getExoView());
        onSurfaceAttached();
        logSurfaceState("attach done");
    }

    private void syncVideoSurfaceSize(VideoSize size) {
        if (mService == null) return;
        View surface = getExoView().getVideoSurfaceView();
        if (!(surface instanceof SurfaceView surfaceView)) return;
        if (!PlaybackPerformanceSetting.isSurfaceFixedSizeEnabled() || getRender() != PlayerSetting.RENDER_SURFACE || player().isNativePlayer()) {
            surfaceDiagnostics.resize("layout", -1, -1);
            surfaceView.getHolder().setSizeFromLayout();
            logSurfaceState("syncVideoSurfaceSize layout size=" + (size == null ? "null" : size.width + "x" + size.height));
            return;
        }
        int width = size != null && size.width > 0 ? size.width : player().getVideoWidth();
        int height = size != null && size.height > 0 ? size.height : player().getVideoHeight();
        if (width <= 0 || height <= 0) return;
        ExoUtil.EnhancedVideoProfile profile = ExoUtil.getEnhancedVideoProfile();
        float scale = Math.min((float) profile.width() / width, (float) profile.height() / height);
        if (scale < 1f) {
            width = Math.max(1, Math.round(width * scale));
            height = Math.max(1, Math.round(height * scale));
        }
        surfaceDiagnostics.resize("fixed", width, height);
        surfaceView.getHolder().setFixedSize(width, height);
        logSurfaceState("syncVideoSurfaceSize fixed=" + width + "x" + height);
    }

    private void logSurfaceState(String step) {
        PlayerView view = getExoView();
        if (view == null) return;
        View surface = view.getVideoSurfaceView();
        View content = view.findViewById(androidx.media3.ui.R.id.exo_content_frame);
        String playerText = mService == null ? "none" : player().getPlayerText();
        boolean nativePlayer = mService != null && player().isNativePlayer();
        int targetRender = mService == null ? -1 : getRender();
        String message = "playback " + step
                + " key=" + getPlaybackKey()
                + " player=" + playerText
                + " native=" + nativePlayer
                + " render=" + render
                + " target=" + targetRender
                + " resize=" + view.getResizeMode()
                + " playerView=" + viewSize(view)
                + " content=" + viewSize(content)
                + " surface=" + surfaceName(surface) + ":" + viewSize(surface)
                + " holder=" + surfaceHolderSize(surface)
                + " rotation=" + (getDisplay() == null ? -1 : getDisplay().getRotation())
                + " orientation=" + getResources().getConfiguration().orientation;
        Log.d(SIZE_TAG, message);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("surface-size", "%s", message);
    }

    private static String viewSize(View view) {
        if (view == null) return "null";
        return view.getWidth() + "x" + view.getHeight();
    }

    private static String surfaceName(View view) {
        return view == null ? "null" : view.getClass().getSimpleName();
    }

    private static String surfaceHolderSize(View view) {
        if (!(view instanceof SurfaceView surfaceView)) return "n/a";
        android.graphics.Rect frame = surfaceView.getHolder().getSurfaceFrame();
        return frame.width() + "x" + frame.height() + "/valid=" + surfaceView.getHolder().getSurface().isValid();
    }

    private void syncShutter() {
        syncShutter(false);
    }

    private void syncShutter(boolean restoreExo) {
        if (mService == null) return;
        boolean nativePlayer = player().isNativePlayer();
        View shutter = getExoView().findViewById(androidx.media3.ui.R.id.exo_shutter);
        if (nativePlayer) {
            boolean keepClosed = nativeOutputPending
                    || player().shouldKeepVideoShutterClosed();
            View videoSurface = getExoView().getVideoSurfaceView();
            // Native MPV uses SurfaceView, which is composed above the normal
            // PlayerView shutter. Alpha hides its buffer without replacing or
            // detaching the Surface while automatic output is being decided.
            if (videoSurface != null) videoSurface.setAlpha(keepClosed ? 0f : 1f);
            getExoView().setShutterBackgroundColor(keepClosed ? Color.BLACK : Color.TRANSPARENT);
            if (shutter != null) shutter.setVisibility(keepClosed ? View.VISIBLE : View.GONE);
        } else if (restoreExo) {
            View videoSurface = getExoView().getVideoSurfaceView();
            if (videoSurface != null) videoSurface.setAlpha(1f);
            getExoView().setShutterBackgroundColor(Color.BLACK);
            if (shutter != null) shutter.setVisibility(View.VISIBLE);
        }
        surfaceDiagnostics.snapshot("shutter-policy");
    }

    private void detachSurface() {
        surfaceDiagnostics.unbind();
        detachAssSurface();
        getExoView().setPlayer(null);
        if (mService != null) player().publishPlaybackRenderTarget(PlaybackAutoContext.RenderTarget.DETACHED);
    }

    private void detachAssSurface() {
        if (attachedAssSession != null) attachedAssSession.detach();
        attachedAssSession = null;
        if (attachedSubtitleSession != null) attachedSubtitleSession.detach();
        attachedSubtitleSession = null;
    }

    private void resetVideoSurfaceForDecoderSwitch() {
        detachAssSurface();
        int targetRender = getRender();
        int temporaryRender = targetRender == PlayerSetting.RENDER_TEXTURE ? PlayerSetting.RENDER_SURFACE : PlayerSetting.RENDER_TEXTURE;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "reset video surface for decoder switch temp=%d target=%d", temporaryRender, targetRender);
        getExoView().setPlayer(null);
        getExoView().setRender(temporaryRender);
        getExoView().setRender(targetRender);
        render = -1;
    }

    protected void setRender() {
        render = -1;
        detachSurface();
        attachSurface();
    }

    private int getRender() {
        if (mService != null && player().isNativePlayer()) return 0;
        if (mService != null && player().requiresTextureRenderForLut()) return PlayerSetting.RENDER_TEXTURE;
        return PlayerSetting.getRender();
    }

    private void releasePlaybackService() {
        if (mService != null) releaseService(isOwner());
        detach();
    }

    private void releaseService(boolean owner) {
        mService.removePlayerCallback(mPlayerCallback);
        if (owner) mService.setNavigationCallback(null, getPlaybackKey());
        if (mService.hasExternalClient() || mService.hasPlayerCallback()) {
            if (owner) mService.suspend();
            mService.resetSessionActivity();
        } else if (owner) {
            mService.shutdown();
        }
    }

    private void detach() {
        releaseController();
        releaseBinding();
    }

    private void releaseController() {
        if (mControllerFuture != null) MediaController.releaseFuture(mControllerFuture);
        if (mController != null) mController.removeListener(this);
        mControllerFuture = null;
        mController = null;
    }

    private void releaseBinding() {
        if (!bound) return;
        bound = false;
        if (mService != null) mService.removePlayerCallback(mPlayerCallback);
        getSeekView().setProgressPlayer(null);
        setTopProgressPlayer(null);
        unbindService(this);
        mService = null;
    }

    private String lifecycleState() {
        String playerKey = null;
        boolean released = true;
        if (mService != null && mService.player() != null) {
            released = mService.player().isReleased();
            if (!released) playerKey = mService.player().getKey();
        }
        return "activity=" + getClass().getSimpleName() +
                " key=" + getPlaybackKey() +
                " playerKey=" + playerKey +
                " owner=" + isOwner() +
                " bound=" + bound +
                " service=" + (mService != null) +
                " controller=" + (mController != null) +
                " released=" + released +
                " redirect=" + redirect +
                " stop=" + stop +
                " finishing=" + isFinishing() +
                " destroyed=" + isDestroyed() +
                " keepScreen=" + ((getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
    }

    private final PlaybackService.PlayerCallback mPlayerCallback = new PlaybackService.PlayerCallback() {

        @Override
        public void onPrepare() {
            if (isOwner()) PlaybackActivity.this.onPrepare();
        }

        @Override
        public void onTracksChanged() {
            if (isOwner()) PlaybackActivity.this.onTracksChanged();
        }

        @Override
        public void onTitlesChanged() {
            if (isOwner()) PlaybackActivity.this.onTitlesChanged();
        }

        @Override
        public void onError(String msg) {
            if (isOwner()) PlaybackActivity.this.onError(msg);
        }

        @Override
        public void onReload(String msg) {
            if (isOwner()) PlaybackActivity.this.onReload(msg);
        }

        @Override
        public void onPlayerRenderRequired() {
            if (!isOwner()) return;
            int targetRender = getRender();
            if (render == targetRender) return;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "LUT switch render from=%d to=%d", render, targetRender);
            setRender();
            applyResizeMode(requestedResizeMode);
        }

        @Override
        public void onPlayerOutputPending() {
            if (!isOwner()) return;
            nativeOutputPending = true;
            syncShutter();
        }

        @Override
        public void onPlayerOutputReady() {
            if (!isOwner()) return;
            nativeOutputPending = false;
            syncShutter();
            PlaybackActivity.this.onVideoFirstFrame();
        }

        @Override
        public void onExoFirstFrame() {
            if (!isOwner() || !player().isExo()) return;
            View shutter = getExoView().findViewById(androidx.media3.ui.R.id.exo_shutter);
            if (shutter != null) shutter.setVisibility(View.INVISIBLE);
            getExoView().setShutterBackgroundColor(Color.TRANSPARENT);
            PlaybackActivity.this.onExoFirstFrame();
            PlaybackActivity.this.onVideoFirstFrame();
        }

        @Override
        public void onPlayerRebuild(Player player, boolean resetVideoSurface) {
            if (isOwner()) {
                nativeOutputPending = player().shouldKeepVideoShutterClosed();
                getSeekView().setProgressPlayer(player);
                setTopProgressPlayer(player);
                if (resetVideoSurface) resetVideoSurfaceForDecoderSwitch();
                setRender();
                applyResizeMode(requestedResizeMode);
                PlaybackActivity.this.onPlayerRebuilt();
            }
        }
    };

    // 视频/直播内容深色（全屏时栏隐藏，非全屏时白图标），BaseActivity 默认已改深色图标，这里盖回。
    @Override
    protected boolean useDarkSystemIcons() {
        return false;
    }

    protected void initView(Bundle savedInstanceState) {
        long start = System.currentTimeMillis();
        super.initView(savedInstanceState);
        ExoUtil.setPlayerView(getExoView());
        exoOutputModeManager = new ExoOutputModeManager(getWindow());
        if (deferPlaybackServiceBinding()) bindPlaybackServiceAfterFirstFrame();
        else bindPlaybackService();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "initView cost=%dms key=%s deferred=%s", System.currentTimeMillis() - start, getPlaybackKey(), deferPlaybackServiceBinding());
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (!isOwner()) return;
        if (isPlaying) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else if (!isBuffering()) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "playing changed isPlaying=%s state=%d %s", isPlaying, mController == null ? -1 : mController.getPlaybackState(), lifecycleState());
        onPlayingChanged(isPlaying);
    }

    @Override
    public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
        if (isOwner()) onPlayerPositionDiscontinuity(oldPosition, newPosition, reason);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (mService != null && isOwner()) surfaceDiagnostics.bind(getExoView(), player().getPlaybackTraceId());
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "state changed state=%d %s", state, lifecycleState());
        if (isOwner()) onStateChanged(state);
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize size) {
        if (!isOwner()) return;
        logSurfaceState("onVideoSizeChanged size=" + size.width + "x" + size.height + " ratio=" + size.pixelWidthHeightRatio);
        publishRenderTarget(getExoView().getVideoSurfaceView());
        syncVideoSurfaceSize(size);
        applyExoOutputMode();
        onSizeChanged(size);
    }

    private void applyExoOutputMode() {
        if (mService == null || exoOutputModeManager == null) return;
        publishRenderTarget(getExoView().getVideoSurfaceView());
        ExoOutputModeManager.Result result;
        if (player().isExo() && getRender() == PlayerSetting.RENDER_SURFACE) {
            Format format = player().getVideoFormat();
            result = exoOutputModeManager.apply(format);
        } else {
            result = exoOutputModeManager.observe("observation-only");
        }
        publishDisplayFacts(result);
        if (SpiderDebug.isEnabled() && result.decision() != null && result.decision().mode() != null) {
            ExoOutputModePolicy.Mode mode = result.decision().mode();
            SpiderDebug.log("playback-flow", "exo output mode reason=%s applied=%s target=%dx%d@%.3fHz", result.reason(), result.applied(), mode.width(), mode.height(), mode.refreshRateMilliHz() / 1000f);
        }
    }

    private void restoreExoOutputMode() {
        if (exoOutputModeManager == null) return;
        ExoOutputModeManager.Result result = exoOutputModeManager.restore();
        if (mService != null) publishDisplayFacts(result);
    }

    private void publishDisplayFacts(ExoOutputModeManager.Result result) {
        if (mService == null || result == null) return;
        surfaceDiagnostics.snapshot("display-mode-request");
        surfaceDiagnostics.display(result);
        player().publishPlaybackDisplayFacts(
                toDisplayMode(result.currentMode()),
                toDisplayMode(result.requestedMode()));
        ExoOutputModePolicy.Decision decision = result.decision();
        boolean hasTarget = decision != null && decision.mode() != null;
        PlaybackTelemetry.DecisionOutcome outcome = result.applied()
                ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                : !hasTarget ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                : decision.changeRequired() ? PlaybackTelemetry.DecisionOutcome.SELECTED
                : PlaybackTelemetry.DecisionOutcome.HELD;
        player().publishPlaybackDecision(new PlaybackTelemetry.DecisionEvent(
                PlaybackTelemetry.DecisionDomain.DISPLAY_MODE,
                outcome,
                displayModeLabel(result.currentMode()),
                displayModeLabel(result.requestedMode()),
                result.applied() ? "window-requested" : displayModeLabel(result.currentMode()),
                result.reason(),
                result.applied() ? "none" : result.reason(),
                java.util.List.of(
                        displayModeInput("current_mode_id", result.currentMode() == null ? null : result.currentMode().id(), PlaybackAutoContext.ValueSource.SYSTEM_API),
                        displayModeInput("current_width", result.currentMode() == null ? null : result.currentMode().width(), PlaybackAutoContext.ValueSource.SYSTEM_API),
                        displayModeInput("current_height", result.currentMode() == null ? null : result.currentMode().height(), PlaybackAutoContext.ValueSource.SYSTEM_API),
                        displayModeInput("current_refresh_millihz", result.currentMode() == null ? null : result.currentMode().refreshRateMilliHz(), PlaybackAutoContext.ValueSource.SYSTEM_API),
                        displayModeInput("target_mode_id", result.requestedMode() == null ? null : result.requestedMode().id(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER),
                        displayModeInput("target_width", result.requestedMode() == null ? null : result.requestedMode().width(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER),
                        displayModeInput("target_height", result.requestedMode() == null ? null : result.requestedMode().height(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER),
                        displayModeInput("target_refresh_millihz", result.requestedMode() == null ? null : result.requestedMode().refreshRateMilliHz(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER),
                        PlaybackTelemetry.DecisionInput.bool("change_required", decision != null && decision.changeRequired(),
                                PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                        PlaybackTelemetry.DecisionInput.bool("window_request", result.applied(),
                                PlaybackAutoContext.ValueSource.SYSTEM_API, PlaybackAutoContext.Confidence.HIGH))));
    }

    private static PlaybackTelemetry.DecisionInput displayModeInput(
            String name, Integer value, PlaybackAutoContext.ValueSource source) {
        return value == null
                ? PlaybackTelemetry.DecisionInput.unknown(name)
                : PlaybackTelemetry.DecisionInput.number(name, value,
                source, PlaybackAutoContext.Confidence.HIGH);
    }

    private static String displayModeLabel(ExoOutputModePolicy.Mode mode) {
        return mode == null ? "unknown" : "mode-" + mode.id();
    }

    private void publishRenderTarget(View surface) {
        if (mService == null) return;
        PlaybackAutoContext.RenderTarget target = surface instanceof SurfaceView
                ? PlaybackAutoContext.RenderTarget.SURFACE_VIEW
                : surface instanceof TextureView
                ? PlaybackAutoContext.RenderTarget.TEXTURE_VIEW
                : PlaybackAutoContext.RenderTarget.UNKNOWN;
        player().publishPlaybackRenderTarget(target);
    }

    private static PlaybackAutoContext.DisplayMode toDisplayMode(ExoOutputModePolicy.Mode mode) {
        return mode == null ? PlaybackAutoContext.DisplayMode.unknown()
                : new PlaybackAutoContext.DisplayMode(mode.id(), mode.width(), mode.height(), mode.refreshRateMilliHz());
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        long start = System.currentTimeMillis();
        mService = ((PlaybackService.LocalBinder) binder).getService();
        mService.replaceBinding(this::closePiP);
        mService.setSessionActivity(buildSessionIntent());
        mService.setPlaybackForeground(true);
        mService.setNavigationCallback(getNavigationCallback(), getPlaybackKey());
        mService.addPlayerCallback(mPlayerCallback);
        getSeekView().setProgressPlayer(player().getPlayer());
        setTopProgressPlayer(player().getPlayer());
        player().setLutAllowed(isLutAllowed());
        player().setDanmakuForeground(true);
        publishRenderTarget(getExoView().getVideoSurfaceView());
        applyExoOutputMode();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "service connected cost=%dms key=%s", System.currentTimeMillis() - start, getPlaybackKey());
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service connected %s", lifecycleState());
        onServiceConnected();
        tryResumeOnReturn();
        // 崩后恢复链探活：本地服务没在跑时在播放入口补拉一次。启动那一拍若被
        // 崩后残留进程占位打断，Server 侧只有限次重试兜底；这里是第二个自愈点，
        // 免得重进后播放全线对着死掉的本地服务静默假死。在跑时仅一次判空空转。
        if (!Server.get().isRunning()) Server.get().start();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service disconnected name=%s %s", name, lifecycleState());
        getSeekView().setProgressPlayer(null);
        setTopProgressPlayer(null);
        mService = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mService != null) {
            mService.setPlaybackForeground(true);
            if (isOwner()) player().setDanmakuForeground(true);
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity resume %s", lifecycleState());
        playbackExiting = false;
        setRedirect(false);
        applyExoOutputMode();
        if (shouldReclaim()) {
            detachSurface();
            onReclaim();
        }
        tryResumeOnReturn();
    }

    /**
     * 回前台续播：只认 onStop 在「后台播放关闭」暂停前记下的标记。回来时内容已换
     * （地址对不上）、已不是本页在播、或状态已不在可播区间都不动；用户在后台期间
     * 已从通知栏等处按了播放（playWhenReady 已为真）时也不重复触发。
     * 服务还没绑回来时不消费标记，等 onServiceConnected 再试。
     */
    private void tryResumeOnReturn() {
        if (!resumeOnReturn || mService == null) return;
        String url = resumeOnReturnUrl;
        resumeOnReturn = false;
        resumeOnReturnUrl = null;
        try {
            if (isFinishing() || !isOwner() || player() == null) return;
            Player target = mController != null ? mController : player().getPlayer();
            if (target == null || target.getPlayWhenReady()) return;
            int state = target.getPlaybackState();
            if (state != Player.STATE_READY && state != Player.STATE_BUFFERING) return;
            if (url == null || !url.equals(player().getUrl())) return;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "resume on return after background pause %s", lifecycleState());
            if (mController != null) mController.play();
            else player().play();
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onPause() {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity pause %s", lifecycleState());
        super.onPause();
        if (isRedirect() && mController != null) mController.pause();
    }

    @Override
    protected void onStop() {
        if (mService != null) {
            mService.setPlaybackForeground(false);
            if (isOwner()) player().setDanmakuForeground(false);
        }
        restoreExoOutputMode();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity stop backgroundOff=%s %s", PlayerSetting.isBackgroundOff(), lifecycleState());
        super.onStop();
        // 先清上次的回前台续播标记，下面的暂停若符合条件再重新置位，
        // 保证标记只描述「最近这一次切后台」。
        resumeOnReturn = false;
        resumeOnReturnUrl = null;
        if (isOwner() && !isAudioOnly() && PlayerSetting.isBackgroundOff()) {
            // 后台播放关闭时切后台必须暂停：mController 可能尚未连接（null），此时直接停 player，
            // 否则开关关了关 App 仍有声音。
            try {
                // 暂停前先看清：只有「正在播（playWhenReady 为真且状态在可播区间）」被这次
                // 切后台暂停的，才记回前台续播标记并存下当前地址；用户在切后台前已手动
                // 暂停或已播完的不记，切回来不许擅自播。正在结束页面的也不记。
                Player target = mController != null ? mController : mService != null && player() != null ? player().getPlayer() : null;
                if (target != null && !isFinishing() && target.getPlayWhenReady()
                        && (target.getPlaybackState() == Player.STATE_READY || target.getPlaybackState() == Player.STATE_BUFFERING)) {
                    resumeOnReturn = true;
                    resumeOnReturnUrl = player() != null ? player().getUrl() : null;
                }
                if (mController != null) mController.pause();
                else if (mService != null) player().pause();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onTrimMemory(int level) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity trimMemory level=%d %s", level, lifecycleState());
        super.onTrimMemory(level);
    }

    @Override
    protected void onDestroy() {
        surfaceDiagnostics.unbind();
        detachAssSurface();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity destroy beforeRelease %s", lifecycleState());
        restoreExoOutputMode();
        super.onDestroy();
        if (isChangingConfigurations()) {
            if (mService != null) mService.removePlayerCallback(mPlayerCallback);
            detach();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity destroy configuration change preserved service key=%s", getPlaybackKey());
            return;
        }
        // 非 owner 销毁且服务因外部客户端存活时，旧视图曾被 player 监听拖着：
        // 这里显式摘掉 PlayerView（方向变化走上面 return 分支，不摘，复用时重挂）。
        try {
            getExoView().setPlayer(null);
        } catch (Throwable ignored) {
        }
        releasePlaybackService();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity destroy afterRelease activity=%s key=%s", getClass().getSimpleName(), getPlaybackKey());
    }

    /** AI 剧情问答：当前影片名（供 AiQaHelper 反射调用） */
    public String aiGetVodName() {
        try {
            String name = getIntent().getStringExtra("vodName");
            if (name != null && !name.isEmpty()) return name;
            name = getIntent().getStringExtra("title");
            if (name != null && !name.isEmpty()) return name;
            name = getIntent().getStringExtra("name");
            if (name != null && !name.isEmpty()) return name;
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** AI 剧情问答：当前影片元数据（供 AiQaHelper 反射调用） */
    public String aiGetVodMeta() {
        try {
            String name = aiGetVodName();
            if (!name.isEmpty()) {
                return "片名：" + name + "\n";
            }
        } catch (Throwable ignored) {
        }
        return "";
    }
}
