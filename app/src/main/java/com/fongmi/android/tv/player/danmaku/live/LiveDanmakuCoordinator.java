package com.fongmi.android.tv.player.danmaku.live;

import android.os.SystemClock;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuMessage;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuWebSocketSession;
import com.fongmi.android.tv.player.danmaku.live.bili.BiliProvider;
import com.fongmi.android.tv.player.danmaku.live.douyin.DouyinProvider;
import com.fongmi.android.tv.player.danmaku.live.douyu.DouyuProvider;
import com.fongmi.android.tv.player.danmaku.live.huya.HuyaProvider;
import com.fongmi.android.tv.player.danmaku.live.kuaishou.KuaishouProvider;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.github.catvod.crawler.SpiderDebug;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 平台直连直播弹幕总管：PlayerManager 只对它说话（设计 5.3）。
 * 收起播快照→识别（LiveRoomResolver）→provider 预备（连前校验）→复用现有
 * LiveDanmakuWebSocketSession 连接→解码后把 LiveDanmakuMessage 交回宿主喂现有
 * Buffer/Batcher/渲染管线。识别不到、校验不过、连接失败全静默，不碰播放状态。
 * 与网关路互斥：宿主在 spec 自带 LIVE 网关弹幕源时不会调到这里（网关优先）。
 */
public final class LiveDanmakuCoordinator {

    public interface Host {

        void onState(LiveDanmakuWebSocketSession.State state, long generation, String targetTag, int code, String detail);

        void onMessages(long generation, List<LiveDanmakuMessage> messages);

        void onOnline(long generation, long online);

        boolean isForeground();
    }

    private final Host host;
    private final Map<RoomRef.Platform, PlatformDanmakuProvider> providers;

    private volatile LiveDanmakuWebSocketSession session;
    private LiveRoomResolver.Snapshot lastSnapshot;
    private RoomRef currentRoom;
    private volatile PlatformDanmakuProvider activeProvider;
    private volatile RoomRef activeRoom;
    private boolean suspended;
    private boolean released;
    private int flowGeneration;

    public LiveDanmakuCoordinator(Host host) {
        this(host, Map.of(RoomRef.Platform.DOUYU, new DouyuProvider(), RoomRef.Platform.BILI, new BiliProvider(), RoomRef.Platform.HUYA, new HuyaProvider(), RoomRef.Platform.DOUYIN, new DouyinProvider(), RoomRef.Platform.KUAISHOU, new KuaishouProvider()));
    }

    LiveDanmakuCoordinator(Host host, Map<RoomRef.Platform, PlatformDanmakuProvider> providers) {
        this.host = host;
        this.providers = providers;
    }

    /** 起播装配点旁路触发：每次起播/切集/切房调一次，快照内部识别。 */
    public synchronized void onPlayback(LiveRoomResolver.Snapshot snapshot) {
        if (released) return;
        lastSnapshot = snapshot;
        Optional<RoomRef> resolved = LiveRoomResolver.resolve(snapshot);
        if (resolved.isEmpty()) {
            stopLocked("unresolved");
            return;
        }
        RoomRef room = resolved.get();
        if (room.equals(currentRoom) && session != null) {
            LiveDanmakuWebSocketSession.State state = session.state();
            if (state == LiveDanmakuWebSocketSession.State.CONNECTING || state == LiveDanmakuWebSocketSession.State.OPEN || state == LiveDanmakuWebSocketSession.State.RETRY_WAIT) return;
        }
        startLocked(room);
    }

    /** 切集/切房/停播收尾：与现有 clearDanmaku 同点调用，代际在会话层防串房。 */
    public synchronized void stop(String reason) {
        stopLocked(reason);
    }

    /** 弹幕隐藏/页面后台：停连但记住房间，回前台 resume 续上。 */
    public synchronized void suspend(String reason) {
        if (released) return;
        suspended = true;
        flowGeneration++;
        if (session != null) session.stop(reason);
    }

    public synchronized void resume() {
        if (released) return;
        suspended = false;
        if (currentRoom != null) startLocked(currentRoom);
        else if (lastSnapshot != null) onPlayback(lastSnapshot);
    }

    public synchronized void release() {
        if (released) return;
        released = true;
        flowGeneration++;
        currentRoom = null;
        activeProvider = null;
        activeRoom = null;
        if (session != null) {
            session.release();
            session = null;
        }
    }

    private void stopLocked(String reason) {
        flowGeneration++;
        currentRoom = null;
        activeProvider = null;
        activeRoom = null;
        if (session != null) session.stop(reason);
    }

    private void startLocked(RoomRef room) {
        currentRoom = room;
        if (!isVisible()) {
            suspended = true;
            flowGeneration++;
            if (session != null) session.stop("hidden");
            return;
        }
        suspended = false;
        PlatformDanmakuProvider provider = providers.get(room.platform());
        if (provider == null) {
            currentRoom = null;
            return;
        }
        if (session != null) session.stop("switch");
        int flow = ++flowGeneration;
        provider.prepare(room).whenComplete((target, error) -> {
            synchronized (LiveDanmakuCoordinator.this) {
                if (released || suspended || flow != flowGeneration || !room.equals(currentRoom)) return;
                if (error != null || target == null || target.isEmpty()) {
                    if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku-live", "skip target=%s error=%s", room.key(), error == null ? "not-live" : error.getClass().getSimpleName());
                    return;
                }
                connectLocked(room, provider, target.get());
            }
        });
    }

    private void connectLocked(RoomRef room, PlatformDanmakuProvider provider, PreparedTarget target) {
        activeProvider = provider;
        activeRoom = room;
        LiveDanmakuWebSocketSession targetSession = ensureSession();
        LiveDanmakuWebSocketSession.FrameHooks hooks = new LiveDanmakuWebSocketSession.FrameHooks() {
            @Override
            public List<byte[]> onOpenFrames() {
                return target.onOpenFrames();
            }

            @Override
            public byte[] heartbeatFrame() {
                return provider.heartbeatFrame(room);
            }

            @Override
            public long heartbeatIntervalMs() {
                return target.heartbeatMs();
            }
        };
        targetSession.connect(target.wsUrl(), target.headers(), room.key(), hooks);
    }

    private boolean isVisible() {
        return host.isForeground() && DanmakuSetting.isShow();
    }

    private LiveDanmakuWebSocketSession ensureSession() {
        if (session == null) {
            session = new LiveDanmakuWebSocketSession(App.get(), new LiveDanmakuWebSocketSession.Listener() {
                @Override
                public void onStateChanged(LiveDanmakuWebSocketSession.State state, long generation, String url, int code, String detail) {
                    RoomRef room = activeRoom;
                    host.onState(state, generation, room == null ? "" : room.key(), code, detail);
                }

                @Override
                public void onMessage(long generation, String text) {
                    // 平台直连只走二进制帧，文本帧不消费（网关路在宿主侧另有会话）
                }

                @Override
                public void onMessage(long generation, byte[] bytes) {
                    PlatformDanmakuProvider provider = activeProvider;
                    if (provider == null) return;
                    PlatformDanmakuProvider.DecodeResult result;
                    try {
                        result = provider.decode(bytes, generation, SystemClock.elapsedRealtime());
                    } catch (Throwable error) {
                        return;
                    }
                    if (result == null) return;
                    if (!result.messages().isEmpty()) {
                        LiveDanmakuWebSocketSession current = session;
                        if (current != null) current.markMessageAccepted(generation);
                        host.onMessages(generation, result.messages());
                    }
                    if (result.online() != null) host.onOnline(generation, result.online());
                    if (!result.replyFrames().isEmpty()) {
                        LiveDanmakuWebSocketSession current = session;
                        if (current != null && current.generation() == generation) {
                            for (byte[] reply : result.replyFrames()) current.send(reply);
                        }
                    }
                }
            });
        }
        return session;
    }
}
