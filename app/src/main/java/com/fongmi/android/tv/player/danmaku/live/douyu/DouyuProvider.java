package com.fongmi.android.tv.player.danmaku.live.douyu;

import com.fongmi.android.tv.player.danmaku.LiveDanmakuMessage;
import com.fongmi.android.tv.player.danmaku.live.PlatformDanmakuProvider;
import com.fongmi.android.tv.player.danmaku.live.PreparedTarget;
import com.fongmi.android.tv.player.danmaku.live.RoomRef;
import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 斗鱼直播弹幕 provider，照 dart_simple_live 的 douyu_danmaku.dart 对译：
 * wss://danmuproxy.douyu.com:8506，小端包＋STT 文本，进房 loginreq＋joingroup(gid=-9999)，
 * 45 秒 mrkl 心跳，免登录免签名。连前先查 RoomApi 开播状态（room_status=1 才接），
 * 校验不过静默不接。斗鱼此通道不回人气（参照实现同口径），无 online 事件。
 */
public final class DouyuProvider implements PlatformDanmakuProvider {

    public static final String WS_URL = "wss://danmuproxy.douyu.com:8506";
    public static final String ROOM_API_URL = "https://open.douyucdn.cn/api/RoomApi/room/";
    public static final long HEARTBEAT_MS = 45_000L;

    private static final ExecutorService PREPARE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "live-danmaku-prepare");
        thread.setDaemon(true);
        return thread;
    });

    private final RoomChecker roomChecker;

    public DouyuProvider() {
        this(DouyuProvider::checkRoomLive);
    }

    DouyuProvider(RoomChecker roomChecker) {
        this.roomChecker = roomChecker;
    }

    @Override
    public RoomRef.Platform platform() {
        return RoomRef.Platform.DOUYU;
    }

    @Override
    public CompletableFuture<Optional<PreparedTarget>> prepare(RoomRef room) {
        return CompletableFuture.supplyAsync(() -> {
            boolean live;
            try {
                live = roomChecker.isLive(room.roomId());
            } catch (Throwable error) {
                live = false;
            }
            if (!live) return Optional.empty();
            List<byte[]> onOpen = List.of(
                    SttCodec.encode("type@=loginreq/roomid@=" + room.roomId() + "/"),
                    SttCodec.encode("type@=joingroup/rid@=" + room.roomId() + "/gid@=-9999/"));
            return Optional.of(new PreparedTarget(WS_URL, Map.of(), onOpen, HEARTBEAT_MS));
        }, PREPARE_EXECUTOR);
    }

    @Override
    public byte[] heartbeatFrame(RoomRef room) {
        return SttCodec.encode("type@=mrkl/");
    }

    @Override
    public DecodeResult decode(byte[] frame, long generation, long receivedAtMs) {
        List<LiveDanmakuMessage> messages = new ArrayList<>();
        for (String body : SttCodec.decodePackets(frame)) {
            ChatMessage chat = parseChat(SttCodec.parseFields(body));
            if (chat == null) continue;
            messages.add(new LiveDanmakuMessage(LiveDanmakuMessage.Type.NORMAL, chat.text(), chat.colorArgb(), receivedAtMs, generation));
        }
        return messages.isEmpty() ? DecodeResult.EMPTY : new DecodeResult(messages, null);
    }

    /**
     * chatmsg 一条的解出结果：昵称＋文本＋颜色。统一消息模型（LiveDanmakuMessage）
     * 与网关路一致只带文本与颜色，昵称在此解出备查、不进渲染文本。
     * 过滤口径照参照实现：非 chatmsg、缺 dms（阴间弹幕）、缺文本一律丢。
     */
    record ChatMessage(String nickname, String text, int colorArgb) {
    }

    static ChatMessage parseChat(Map<String, String> fields) {
        if (fields == null || !"chatmsg".equals(fields.get("type"))) return null;
        if (!fields.containsKey("dms")) return null;
        String text = fields.get("txt");
        if (text == null || text.isEmpty()) return null;
        String nickname = fields.getOrDefault("nn", "");
        return new ChatMessage(nickname, text, colorArgb(fields.get("col")));
    }

    /** 斗鱼颜色枚举（参照 getColor）：1红/2蓝/3绿/4橙/5紫/6粉，其余与缺失为白。 */
    static int colorArgb(String raw) {
        int col = 0;
        if (raw != null) {
            try {
                col = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                col = 0;
            }
        }
        return switch (col) {
            case 1 -> 0xFFFF0000;
            case 2 -> 0xFF1E87F0;
            case 3 -> 0xFF7AC84B;
            case 4 -> 0xFFFF7F00;
            case 5 -> 0xFF9B39F4;
            case 6 -> 0xFFFF69B4;
            default -> 0xFFFFFFFF;
        };
    }

    static boolean checkRoomLive(String rid) throws Exception {
        Request request = new Request.Builder()
                .url(ROOM_API_URL + rid)
                .header("User-Agent", "Mozilla/5.0")
                .build();
        try (Response response = OkHttp.client().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return false;
            JSONObject root = new JSONObject(response.body().string());
            if (root.optInt("error", -1) != 0) return false;
            JSONObject data = root.optJSONObject("data");
            return data != null && "1".equals(data.optString("room_status"));
        }
    }

    interface RoomChecker {

        boolean isLive(String rid) throws Exception;
    }
}
