package com.fongmi.android.tv.player.danmaku.live.huya;

import com.fongmi.android.tv.player.danmaku.LiveDanmakuMessage;
import com.fongmi.android.tv.player.danmaku.live.PlatformDanmakuProvider;
import com.fongmi.android.tv.player.danmaku.live.PreparedTarget;
import com.fongmi.android.tv.player.danmaku.live.RoomRef;
import com.github.catvod.net.OkHttp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 虎牙直播弹幕 provider，照 dart_simple_live 的 huya_site.dart＋huya_danmaku.dart 对译：
 * 预备步拉 m.huya.com 房间页，从 window.HNF_GLOBAL_INIT 块里提取进房三件套
 * （ayyuid＝tLiveInfo.lYyid、topSid＝lChannelId、subSid＝lSubChannelId），
 * 并校验在播（roomInfo.eLiveStatus＝2 才接）；连 wss://cdnws.api.huya.com 发 TARS 进房包，
 * 60 秒固定心跳帧；推送 uri 1400 解弹幕、8006 回人气喂 online。
 * 免登录免签名；页面拉不到、三件套缺失、未开播一律静默不接。
 */
public final class HuyaProvider implements PlatformDanmakuProvider {

    public static final String WS_URL = "wss://cdnws.api.huya.com";
    public static final String ROOM_PAGE_URL = "https://m.huya.com/";
    public static final String USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    public static final long HEARTBEAT_MS = 60_000L;

    private static final Pattern YYID = Pattern.compile("\"lYyid\":(\\d+)");
    private static final Pattern TOP_SID = Pattern.compile("\"lChannelId\":(\\d+)");
    private static final Pattern SUB_SID = Pattern.compile("\"lSubChannelId\":(\\d+)");
    private static final Pattern LIVE_STATUS = Pattern.compile("\"eLiveStatus\":(\\d+)");

    private static final ExecutorService PREPARE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "live-danmaku-prepare");
        thread.setDaemon(true);
        return thread;
    });

    private final Api api;

    public HuyaProvider() {
        this(new LiveApi());
    }

    HuyaProvider(Api api) {
        this.api = api;
    }

    @Override
    public RoomRef.Platform platform() {
        return RoomRef.Platform.HUYA;
    }

    @Override
    public CompletableFuture<Optional<PreparedTarget>> prepare(RoomRef room) {
        return CompletableFuture.supplyAsync(() -> prepareSync(room), PREPARE_EXECUTOR);
    }

    private Optional<PreparedTarget> prepareSync(RoomRef room) {
        try {
            RoomPage page = parseRoomPage(api.fetchRoomPage(room.roomId()));
            if (page == null || page.liveStatus() != 2) return Optional.empty();
            if (page.ayyuid() <= 0 || page.topSid() <= 0 || page.subSid() <= 0) return Optional.empty();
            byte[] join = HuyaPacketCodec.encodeJoin(page.ayyuid(), page.topSid(), page.subSid());
            return Optional.of(new PreparedTarget(WS_URL, Map.of(), List.of(join), HEARTBEAT_MS));
        } catch (Throwable error) {
            return Optional.empty();
        }
    }

    @Override
    public byte[] heartbeatFrame(RoomRef room) {
        return HuyaPacketCodec.HEARTBEAT_FRAME.clone();
    }

    @Override
    public DecodeResult decode(byte[] frame, long generation, long receivedAtMs) {
        HuyaPacketCodec.Decoded decoded = HuyaPacketCodec.decode(frame);
        if (decoded.chats().isEmpty() && decoded.online() == null) return DecodeResult.EMPTY;
        List<LiveDanmakuMessage> messages = new ArrayList<>();
        for (HuyaPacketCodec.Chat chat : decoded.chats()) {
            messages.add(new LiveDanmakuMessage(LiveDanmakuMessage.Type.NORMAL, chat.text(), colorArgb(chat.fontColor()), receivedAtMs, generation));
        }
        return new DecodeResult(messages, decoded.online());
    }

    /** 字体色为十进制 RGB；<=0 回白色（照参照实现：color<=0 取白，其余按 RGB 值取色）。 */
    static int colorArgb(int fontColor) {
        return fontColor <= 0 ? 0xFFFFFFFF : (0xFF000000 | (fontColor & 0xFFFFFF));
    }

    /** 房间页提取产物：进房三件套＋开播状态，任一缺失整页作废。 */
    record RoomPage(long ayyuid, long topSid, long subSid, int liveStatus) {
    }

    /**
     * 提取口径照参照实现：HNF_GLOBAL_INIT 块内取 lYyid 与 eLiveStatus；
     * lChannelId/lSubChannelId 块内没有时回落整页首个命中（参照实现对整页文本正则）。
     */
    static RoomPage parseRoomPage(String html) {
        if (html == null) return null;
        int start = html.indexOf("HNF_GLOBAL_INIT");
        if (start < 0) return null;
        int end = html.indexOf("</script>", start);
        String block = end > start ? html.substring(start, end) : html.substring(start);
        Long ayyuid = findLong(YYID, block);
        Long status = findLong(LIVE_STATUS, block);
        Long topSid = findLong(TOP_SID, block);
        if (topSid == null) topSid = findLong(TOP_SID, html);
        Long subSid = findLong(SUB_SID, block);
        if (subSid == null) subSid = findLong(SUB_SID, html);
        if (ayyuid == null || status == null || topSid == null || subSid == null) return null;
        return new RoomPage(ayyuid, topSid, subSid, status.intValue());
    }

    private static Long findLong(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return null;
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    interface Api {

        String fetchRoomPage(String roomId) throws Exception;
    }

    /** 真接口实现：移动端 UA 拉 m.huya.com 房间页全文（与源蜘蛛 _huya_detail 同一路）。 */
    static final class LiveApi implements Api {

        @Override
        public String fetchRoomPage(String roomId) throws Exception {
            Request request = new Request.Builder()
                    .url(ROOM_PAGE_URL + roomId)
                    .header("User-Agent", USER_AGENT)
                    .build();
            try (Response response = OkHttp.client().newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) throw new IllegalStateException("http " + response.code());
                return response.body().string();
            }
        }
    }
}
