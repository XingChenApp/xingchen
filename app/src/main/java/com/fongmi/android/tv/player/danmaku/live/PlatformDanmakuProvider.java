package com.fongmi.android.tv.player.danmaku.live;

import com.fongmi.android.tv.player.danmaku.LiveDanmakuMessage;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 平台弹幕 provider：只管协议，不管连接（连接归 LiveDanmakuWebSocketSession，
 * 生命周期归 LiveDanmakuCoordinator）。解码产物统一翻译成 LiveDanmakuMessage，
 * 喂回现有 Buffer/Batcher/渲染管线，不另造渲染与设置。
 */
public interface PlatformDanmakuProvider {

    RoomRef.Platform platform();

    /**
     * 异步预备步：连前校验（查开播/取 token/提 id）都在这步，
     * 校验不过或失败返回 empty＝不接，全程静默。
     */
    CompletableFuture<Optional<PreparedTarget>> prepare(RoomRef room);

    /** 周期心跳帧；无心跳的平台返回 null。 */
    byte[] heartbeatFrame(RoomRef room);

    /** 解一帧平台字节流；异常帧返回 EMPTY，不许抛到会话回调线程上。 */
    DecodeResult decode(byte[] frame, long generation, long receivedAtMs);

    /**
     * 解码产物：弹幕与人气喂回现有管线；replyFrames 是协议要求即时回的帧
     * （如抖音 needAck 回执），由 Coordinator 经同一会话按代际守卫代发，
     * provider 仍只管协议不管连接。无回帧的平台一律空表。
     */
    record DecodeResult(List<LiveDanmakuMessage> messages, Long online, List<byte[]> replyFrames) {

        public static final DecodeResult EMPTY = new DecodeResult(List.of(), null, List.of());

        public DecodeResult {
            messages = messages == null ? List.of() : List.copyOf(messages);
            replyFrames = replyFrames == null ? List.of() : List.copyOf(replyFrames);
        }

        public DecodeResult(List<LiveDanmakuMessage> messages, Long online) {
            this(messages, online, List.of());
        }
    }
}
