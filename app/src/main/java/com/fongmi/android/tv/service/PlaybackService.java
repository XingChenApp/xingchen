package com.fongmi.android.tv.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.CommandButton;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionCommands;
import androidx.media3.session.SessionError;
import androidx.media3.session.SessionResult;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.browse.BrowseTree;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.playback.PlaybackEventCollector;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.lyrics.DesktopLyricsWindow;
import com.fongmi.android.tv.player.lyrics.LyricsLine;
import com.fongmi.android.tv.player.lyrics.LyricsResult;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 播放服务（共享机制本体）：视频与普通听书/TTS 共用本服务（主服务）。
 * 红果听书自 2026-10-31 W-15 拆套起走子类 HgAudioPlaybackService 的独立
 * 实例——同一套机制、两份运行时状态（各自的 PlayerManager/会话/通知/
 * 导航槽），互不顶播。子类只覆写下方三个钩子，其余逻辑一律共用。
 */
public class PlaybackService extends MediaLibraryService implements MediaLibrarySession.Callback, PlayerManager.Callback {

    public static final String LOCAL_BIND_ACTION = BuildConfig.APPLICATION_ID.concat(".LOCAL_BIND");

    private static final SessionCommand COMMAND_REPEAT = new SessionCommand(ActionEvent.REPEAT, Bundle.EMPTY);

    /** 主服务存活旗（类级）：isRunning() 的既有调用方问的都是主服务，只许主服务写。 */
    private static volatile boolean running;

    /** 本实例存活旗：内部判定一律读它，双服务时各读各的，不许读类级旗串扰。 */
    private boolean alive;

    private final List<PlayerCallback> playerCallbacks = new CopyOnWriteArrayList<>();
    private final IBinder binder = new LocalBinder();

    private NavigationCallback navigationCallback;
    private MediaLibrarySession session;
    private DesktopLyricsWindow desktopLyrics;
    private Runnable onNewBinding;
    private boolean externalBound;
    private PlayerManager player;
    private String navigationKey;
    private Player exoPlayer;

    public static boolean isRunning() {
        return running;
    }

    /** 媒体会话 id（主服务 xc_video；红果听书服务覆写为 xc_hgaudio）。 */
    protected String sessionId() {
        return "xc_video";
    }

    /** 通知 id（主服务沿用 media3 默认 1001；红果听书服务覆写避撞）。 */
    protected int notificationId() {
        return DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID;
    }

    /**
     * 是否主服务：只有主服务写类级 running 旗、注册 Server 单例、接管
     * PlaybackEventCollector 指针——这三处都是全局单槽，拆套后必须归一，
     * 不许两个服务轮流抢（今日之前是「最后创建者赢」，属隐性串扰）。
     */
    protected boolean isPrimaryService() {
        return true;
    }

    public void replaceBinding(Runnable callback) {
        if (onNewBinding != null) onNewBinding.run();
        onNewBinding = callback;
    }

    public PlayerManager player() {
        return player;
    }

    private String serviceState() {
        return "running=" + alive +
                " player=" + (player != null && !player.isReleased()) +
                " key=" + (player == null || player.isReleased() ? null : player.getKey()) +
                " callbacks=" + playerCallbacks.size() +
                " external=" + externalBound +
                " navigation=" + (navigationCallback != null) +
                " navigationKey=" + navigationKey +
                " session=" + (session != null);
    }

    private boolean hasNavigationCallback() {
        return navigationCallback != null;
    }

    private boolean isPlayerAvailable() {
        return alive && player != null && !player.isReleased();
    }

    @Override
    public void onCreate() {
        long start = System.currentTimeMillis();
        super.onCreate();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "service onCreate start");
        alive = true;
        if (isPrimaryService()) running = true;
        player = new PlayerManager(this);
        desktopLyrics = new DesktopLyricsWindow(this);
        if (isPrimaryService()) PlaybackEventCollector.get().setPlayer(player);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "service player ready cost=%dms", System.currentTimeMillis() - start);
        exoPlayer = player.getPlayer();
        exoPlayer.addListener(listener);
        session = new MediaLibrarySession.Builder(this, wrap(exoPlayer), this).setId(sessionId()).build();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "service session ready cost=%dms", System.currentTimeMillis() - start);
        session.setSessionActivity(buildDefaultIntent());
        EventBus.getDefault().register(this);
        if (isPrimaryService()) Server.get().setService(this);
        setupNotification();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "service onCreate end cost=%dms", System.currentTimeMillis() - start);
    }

    private PendingIntent buildDefaultIntent() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent == null) intent = new Intent();
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void setupNotification() {
        DefaultMediaNotificationProvider provider = new DefaultMediaNotificationProvider.Builder(this).setNotificationId(notificationId()).build();
        session.setMediaButtonPreferences(ImmutableList.of(buildRepeatButton(), buildStopButton()));
        provider.setSmallIcon(R.drawable.ic_notification);
        setMediaNotificationProvider(provider);
    }

    private CommandButton buildStopButton() {
        return new CommandButton.Builder(CommandButton.ICON_STOP).setPlayerCommand(Player.COMMAND_STOP).setDisplayName(getString(R.string.play_stop)).build();
    }

    private CommandButton buildRepeatButton() {
        return new CommandButton.Builder(player.isRepeatOne() ? CommandButton.ICON_REPEAT_ONE : CommandButton.ICON_REPEAT_OFF).setSessionCommand(COMMAND_REPEAT).setDisplayName(getString(R.string.play_repeat)).build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service startCommand action=%s flags=%d startId=%d %s", intent == null ? null : intent.getAction(), flags, startId, serviceState());
        if (intent != null) handleAction(intent.getAction());
        return super.onStartCommand(intent, flags, startId);
    }

    private void handleAction(String action) {
        if (!isPlayerAvailable()) return;
        if (ActionEvent.PLAY.equals(action)) player.play();
        else if (ActionEvent.PAUSE.equals(action)) player.pause();
        else if (ActionEvent.PREV.equals(action)) dispatchPrev();
        else if (ActionEvent.NEXT.equals(action)) dispatchNext();
        else if (ActionEvent.STOP.equals(action)) dispatchStop();
        else if (ActionEvent.AUDIO.equals(action)) dispatchAudio();
        else if (ActionEvent.REPEAT.equals(action)) dispatchRepeat();
        else if (ActionEvent.REPLAY.equals(action)) dispatchReplay();
    }

    private boolean isLocalBind(Intent intent) {
        return LOCAL_BIND_ACTION.equals(intent != null ? intent.getAction() : null);
    }

    private boolean isExternalBind(Intent intent) {
        return "android.media.browse.MediaBrowserService".equals(intent != null ? intent.getAction() : null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service bind action=%s local=%s external=%s %s", intent == null ? null : intent.getAction(), isLocalBind(intent), isExternalBind(intent), serviceState());
        if (isLocalBind(intent)) return binder;
        if (isExternalBind(intent)) externalBound = true;
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service unbind action=%s local=%s external=%s before %s", intent == null ? null : intent.getAction(), isLocalBind(intent), isExternalBind(intent), serviceState());
        if (isExternalBind(intent)) releaseExternal();
        if (isLocalBind(intent)) tryShutdown();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service unbind after %s", serviceState());
        return super.onUnbind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service taskRemoved root=%s %s", rootIntent, serviceState());
        tryShutdown();
    }

    @Override
    public void onDisconnected(@NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controller) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service controller disconnected package=%s %s", controller.getPackageName(), serviceState());
        if (controller.getPackageName().equals(getPackageName())) return;
        tryShutdown();
    }

    @Override
    public void onDestroy() {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service destroy before %s", serviceState());
        alive = false;
        if (isPrimaryService()) running = false;
        // player 在 onCreate 首段才赋值：构造中途抛错时 player 为 null，
        // 这里无条件解引用会以二次 NPE 把真因盖掉，故逐处判空。
        if (player != null) {
            player.prepareTerminalRelease();
            if (isPrimaryService()) PlaybackEventCollector.get().onStop(player);
        }
        if (desktopLyrics != null) desktopLyrics.release();
        releaseSession();
        if (player != null) {
            player.stop();
            player.release();
        }
        removeForeground();
        if (isPrimaryService()) Server.get().setService(null);
        EventBus.getDefault().unregister(this);
        super.onDestroy();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service destroy after running=%s", alive);
    }

    private void stopAndClear() {
        if (isPrimaryService()) PlaybackEventCollector.get().onStop(player);
        player.stop();
        // 视频会话退出（suspend/shutdown）：被视频暂停的音乐显式续播。
        // stop() 本身不续（刷新/换源/报错路也会调 stop，视频页还开着）。
        player.resumeMusicPausedByVideo();
        // 上游 PlaybackService.java:339-346 退出路径同款：player.stop() 之后调
        // Source.get().stop()，退出时停掉提取器任务（含迅雷任务）。本树 Source 已
        // import（player.Source），其 stop() 只转发给各 Extractor.stop()，不 release。
        Source.get().stop();
        player.clearMediaItems();
    }

    public void suspend() {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service suspend %s", serviceState());
        stopAndClear();
        removeForeground();
    }

    public void shutdown() {
        if (!alive) return;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service shutdown %s", serviceState());
        alive = false;
        if (isPrimaryService()) running = false;
        player.prepareTerminalRelease();
        stopAndClear();
        removeForeground();
        stopSelf();
    }

    private void tryShutdown() {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service tryShutdown %s", serviceState());
        if (!hasNavigationCallback() && !hasExternalClient()) shutdown();
    }

    private void releaseExternal() {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service releaseExternal before %s", serviceState());
        externalBound = false;
        saveProgress();
        BrowseTree.clear();
        tryShutdown();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service releaseExternal after %s", serviceState());
    }

    private void releaseSession() {
        if (session == null) return;
        session.release();
        session = null;
    }

    private void removeForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    private void saveProgress() {
        if (hasNavigationCallback() || session == null) return;
        // 听书隔离：audiobook: 开头的播放进度归 ReadingStore，不写点播历史；
        // hgaudio: 是红果听书自己那套的键前缀（进度归 HgAudioStore），同隔离
        try {
            if (player.getSpec() != null && player.getSpec().getKey() != null
                    && (player.getSpec().getKey().startsWith("audiobook:")
                        || player.getSpec().getKey().startsWith("hgaudio:"))) return;
        } catch (Throwable ignored) {
        }
        if (BrowseTree.saveProgress(player.getPosition(), player.getDuration(), player)) {
            session.notifyChildrenChanged("VOD", 0, null);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.isPlayerPerformance()) {
            if (isPlayerAvailable()) player.applyPerformanceSettings();
        } else if (session == null) {
            return;
        } else if (event.isVod()) {
            BrowseTree.clearVod();
            session.notifyChildrenChanged("VOD", 0, null);
        } else if (event.isLive()) {
            BrowseTree.clearLive();
            session.notifyChildrenChanged("LIVE", 0, null);
        }
    }

    @Nullable
    @Override
    public MediaLibrarySession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @NonNull
    @Override
    public MediaSession.ConnectionResult onConnect(@NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controller) {
        SessionCommands commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon().add(COMMAND_REPEAT).build();
        return new MediaLibrarySession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build();
    }

    @NonNull
    @Override
    public ListenableFuture<SessionResult> onCustomCommand(@NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controller, @NonNull SessionCommand customCommand, @NonNull Bundle args) {
        if (COMMAND_REPEAT.customAction.equals(customCommand.customAction)) {
            dispatchRepeat();
            return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
        }
        return MediaLibrarySession.Callback.super.onCustomCommand(session, controller, customCommand, args);
    }

    public boolean hasExternalClient() {
        return externalBound;
    }

    public void setSessionActivity(PendingIntent pendingIntent) {
        if (session != null) session.setSessionActivity(pendingIntent);
    }

    public void setPlaybackForeground(boolean foreground) {
        if (player != null) player.setPlaybackForeground(foreground);
        if (desktopLyrics != null) desktopLyrics.setForeground(foreground);
    }

    public void setDesktopLyricsAudioContent(boolean audioContent) {
        if (desktopLyrics != null) desktopLyrics.setAudioContent(audioContent);
    }

    public void setDesktopLyricsSnapshot(LyricsResult result, List<LyricsLine> lines) {
        if (desktopLyrics != null) desktopLyrics.setLyricsSnapshot(result, lines);
    }

    public void resetSessionActivity() {
        setSessionActivity(buildDefaultIntent());
    }

    public void setNavigationCallback(NavigationCallback navigationCallback, String key) {
        if (navigationCallback == null) {
            // 清除只认 key：只有传入 key 与当前槽位 key 一致才清，别家传 null
            //（含不带 key 的 null）清不掉本家的回调——四家共用单槽时互清就是
            // 这么来的。key 为 null 时也只清同样以 null 占槽的回调。
            if (key == null ? navigationKey != null : !key.equals(navigationKey)) return;
            this.navigationCallback = null;
            this.navigationKey = null;
            return;
        }
        // 注册／覆写口径保留：后注册者直接顶掉旧 owner（回调与 key 一起换），
        // 归属判定仍走 isNavigationOwner() 对 player key 的比对，覆写不另做拦截。
        this.navigationCallback = navigationCallback;
        this.navigationKey = key;
    }

    private boolean isNavigationOwner() {
        return isPlayerAvailable() && (navigationKey == null || navigationKey.equals(player.getKey()));
    }

    public void addPlayerCallback(PlayerCallback callback) {
        playerCallbacks.add(callback);
    }

    public void removePlayerCallback(PlayerCallback callback) {
        playerCallbacks.remove(callback);
    }

    public boolean hasPlayerCallback() {
        return !playerCallbacks.isEmpty();
    }

    public void dispatchPrev() {
        dispatchNavigate(NavigationCallback::onPrev, -1);
    }

    public void dispatchNext() {
        dispatchNavigate(NavigationCallback::onNext, 1);
    }

    private void dispatchNavigate(Consumer<NavigationCallback> action, int delta) {
        if (!isPlayerAvailable()) return;
        if (hasNavigationCallback() && isNavigationOwner()) dispatch(action);
        else navigateItem(delta);
    }

    public void dispatchStop() {
        if (!isPlayerAvailable()) return;
        if (player.getPlaybackState() == Player.STATE_IDLE) return;
        if (hasNavigationCallback() && isNavigationOwner()) dispatch(NavigationCallback::onStop);
        else stopAndClear();
    }

    public void dispatchRepeat() {
        if (!isPlayerAvailable()) return;
        player.setRepeatOne(!player.isRepeatOne());
    }

    public void dispatchReplay() {
        if (!isPlayerAvailable()) return;
        if (hasNavigationCallback() && isNavigationOwner()) dispatch(NavigationCallback::onReplay);
        else {
            player.seekTo(0);
            player.play();
        }
    }

    public void dispatchAudio() {
        if (!isPlayerAvailable()) return;
        dispatch(NavigationCallback::onAudio);
    }

    private void dispatch(Consumer<NavigationCallback> action) {
        NavigationCallback callback = navigationCallback;
        if (callback != null) App.post(() -> action.accept(callback));
    }

    private void navigateItem(int delta) {
        MediaItem current = player.getCurrentMediaItem();
        if (current == null) return;
        Task.submit(() -> {
            try {
                MediaItem next = BrowseTree.navigate(current.mediaId, delta);
                if (next == null || next.localConfiguration == null) return;
                Result result = BrowseTree.consumeBrowseResult(next.mediaId);
                if (result == null || !alive) return;
                App.post(() -> startBrowse(next, result, 0));
            } catch (Exception ignored) {
            }
        });
    }

    private boolean isSameItem(MediaItem item) {
        if (item == null || item.localConfiguration == null) return false;
        return item.localConfiguration.uri.toString().equals(player.getUrl());
    }

    private void interceptItem(@NonNull MediaItem item, long startPositionMs) {
        if (isSameItem(item)) return;
        playViaManager(item, startPositionMs);
    }

    private void interceptItems(@NonNull List<MediaItem> items, int startIndex, long startPositionMs) {
        if (items.isEmpty()) return;
        int idx = (startIndex >= 0 && startIndex < items.size()) ? startIndex : 0;
        interceptItem(items.get(idx), startPositionMs > 0 ? startPositionMs : 0);
    }

    private ForwardingPlayer wrap(Player base) {
        return new ForwardingPlayer(base) {
            @Override
            public void setMediaItem(@NonNull MediaItem item) {
                interceptItem(item, 0);
            }

            @Override
            public void setMediaItem(@NonNull MediaItem item, boolean resetPosition) {
                interceptItem(item, 0);
            }

            @Override
            public void setMediaItem(@NonNull MediaItem item, long startPositionMs) {
                interceptItem(item, startPositionMs);
            }

            @Override
            public void setMediaItems(@NonNull List<MediaItem> items) {
                interceptItems(items, 0, 0);
            }

            @Override
            public void setMediaItems(@NonNull List<MediaItem> items, boolean resetPosition) {
                interceptItems(items, 0, 0);
            }

            @Override
            public void setMediaItems(@NonNull List<MediaItem> items, int startIndex, long startPositionMs) {
                interceptItems(items, startIndex, startPositionMs);
            }

            @Override
            public void seekToPrevious() {
                dispatchPrev();
            }

            @Override
            public void seekToPreviousMediaItem() {
                dispatchPrev();
            }

            @Override
            public void seekToNext() {
                dispatchNext();
            }

            @Override
            public void seekToNextMediaItem() {
                dispatchNext();
            }

            @Override
            public void stop() {
                dispatchStop();
            }

            @NonNull
            @Override
            public Commands getAvailableCommands() {
                return super.getAvailableCommands().buildUpon().add(COMMAND_SEEK_TO_PREVIOUS).add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).add(COMMAND_SEEK_TO_NEXT).add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).add(COMMAND_SEEK_BACK).add(COMMAND_SEEK_FORWARD).add(COMMAND_STOP).add(COMMAND_SET_REPEAT_MODE).build();
            }
        };
    }

    private void playViaManager(MediaItem item, long startPositionMs) {
        if (item == null || item.localConfiguration == null) return;
        Result result = BrowseTree.consumeBrowseResult(item.mediaId);
        if (result != null) startBrowse(item, result, startPositionMs);
    }

    private void startBrowse(MediaItem item, Result result, long startPositionMs) {
        player.browse(PlaySpec.from(result, item.mediaId, item.mediaMetadata));
        if (startPositionMs > 0) player.seekTo(startPositionMs);
    }

    @Override
    public void onPrepare() {
        if (desktopLyrics != null) desktopLyrics.refresh(player);
        playerCallbacks.forEach(PlayerCallback::onPrepare);
    }

    @Override
    public void onTracksChanged() {
        if (desktopLyrics != null) desktopLyrics.refresh(player);
        playerCallbacks.forEach(PlayerCallback::onTracksChanged);
    }

    @Override
    public void onTitlesChanged() {
        if (desktopLyrics != null) desktopLyrics.refresh(player);
        playerCallbacks.forEach(PlayerCallback::onTitlesChanged);
    }

    @Override
    public void onError(String msg) {
        playerCallbacks.forEach(callback -> callback.onError(msg));
    }

    @Override
    public void onReload(String msg) {
        playerCallbacks.forEach(callback -> callback.onReload(msg));
    }

    @Override
    public boolean onHttpGoneRetry() {
        for (PlayerCallback callback : playerCallbacks) {
            try {
                if (callback.onHttpGoneRetry()) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    @Override
    public void onPlayerRenderRequired() {
        playerCallbacks.forEach(PlayerCallback::onPlayerRenderRequired);
    }

    @Override
    public void onPlayerOutputPending() {
        playerCallbacks.forEach(PlayerCallback::onPlayerOutputPending);
    }

    @Override
    public void onPlayerOutputReady() {
        playerCallbacks.forEach(PlayerCallback::onPlayerOutputReady);
    }

    @Override
    public void onExoFirstFrame() {
        playerCallbacks.forEach(PlayerCallback::onExoFirstFrame);
    }

    @Override
    public void onPlayerRebuild(Player newPlayer, boolean resetVideoSurface) {
        exoPlayer.removeListener(listener);
        exoPlayer = newPlayer;
        exoPlayer.addListener(listener);
        if (isPrimaryService()) PlaybackEventCollector.get().setPlayer(player);
        if (session != null) session.setPlayer(wrap(newPlayer));
        playerCallbacks.forEach(callback -> callback.onPlayerRebuild(newPlayer, resetVideoSurface));
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            PlaybackEventCollector.get().onPlaybackStateChanged(player, state);
            if (desktopLyrics != null) desktopLyrics.update(player);
            if (state == Player.STATE_ENDED) {
                boolean ownerHandlesNavigation = hasNavigationCallback() && isNavigationOwner();
                if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-auto-next", "service ended owner=%s navigation=%s key=%s navigationKey=%s action=%s", isNavigationOwner(), hasNavigationCallback(), player.getKey(), navigationKey, ownerHandlesNavigation ? "defer-to-owner" : "browse-next");
                if (!ownerHandlesNavigation) navigateItem(1);
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            PlaybackEventCollector.get().onIsPlayingChanged(player, isPlaying);
            if (desktopLyrics != null) desktopLyrics.update(player);
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            if (session != null) session.setMediaButtonPreferences(ImmutableList.of(buildRepeatButton(), buildStopButton()));
        }
    };

    @NonNull
    @Override
    public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(@NonNull MediaLibrarySession session, @NonNull MediaSession.ControllerInfo browser, @Nullable MediaLibraryService.LibraryParams params) {
        return Futures.immediateFuture(LibraryResult.ofItem(BrowseTree.getRootItem(), params));
    }

    @NonNull
    @Override
    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(@NonNull MediaLibrarySession session, @NonNull MediaSession.ControllerInfo browser, @NonNull String parentId, int page, int pageSize, @Nullable MediaLibraryService.LibraryParams params) {
        return Task.executor().submit(() -> LibraryResult.ofItemList(BrowseTree.getChildren(parentId), params));
    }

    @NonNull
    @Override
    public ListenableFuture<LibraryResult<Void>> onSearch(@NonNull MediaLibrarySession session, @NonNull MediaSession.ControllerInfo browser, @NonNull String query, @Nullable MediaLibraryService.LibraryParams params) {
        Task.execute(() -> {
            ImmutableList<MediaItem> results = BrowseTree.search(query);
            App.post(() -> session.notifySearchResultChanged(browser, query, results.size(), params));
        });
        return Futures.immediateFuture(LibraryResult.ofVoid(params));
    }

    @NonNull
    @Override
    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResult(@NonNull MediaLibrarySession session, @NonNull MediaSession.ControllerInfo browser, @NonNull String query, int page, int pageSize, @Nullable MediaLibraryService.LibraryParams params) {
        return Futures.immediateFuture(LibraryResult.ofItemList(BrowseTree.getSearchResult(), params));
    }

    @NonNull
    @Override
    public ListenableFuture<LibraryResult<MediaItem>> onGetItem(@NonNull MediaLibrarySession session, @NonNull MediaSession.ControllerInfo browser, @NonNull String mediaId) {
        MediaItem item = BrowseTree.getItem(mediaId);
        return Futures.immediateFuture(item != null ? LibraryResult.ofItem(item, null) : LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
    }

    @NonNull
    @Override
    public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(@NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controller, @NonNull List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        saveProgress();
        return Task.executor().submit(() -> {
            List<MediaItem> resolved = mediaItems.stream().map(BrowseTree::resolveOrKeep).toList();
            int index = resolved.isEmpty() ? 0 : Math.min(Math.max(startIndex, 0), resolved.size() - 1);
            long position = BrowseTree.consumeResumePosition();
            return new MediaSession.MediaItemsWithStartPosition(resolved, index, position);
        });
    }

    public interface PlayerCallback {

        default void onPrepare() {
        }

        default void onTracksChanged() {
        }

        default void onTitlesChanged() {
        }

        default void onError(String msg) {
        }

        default void onReload(String msg) {
        }

        /**
         * 播放链返回 HTTP 410（直链过期）时的自救：返回 true 表示已接管重试，
         * 上游不再走 onError。默认不接管。
         */
        default boolean onHttpGoneRetry() {
            return false;
        }

        default void onPlayerRenderRequired() {
        }

        default void onPlayerOutputPending() {
        }

        default void onPlayerOutputReady() {
        }

        default void onExoFirstFrame() {
        }

        default void onPlayerRebuild(Player player, boolean resetVideoSurface) {
        }
    }

    public interface NavigationCallback {

        default void onPrev() {
        }

        default void onNext() {
        }

        default void onStop() {
        }

        default void onReplay() {
        }

        default void onAudio() {
        }
    }

    public class LocalBinder extends Binder {

        public PlaybackService getService() {
            return PlaybackService.this;
        }
    }
}
