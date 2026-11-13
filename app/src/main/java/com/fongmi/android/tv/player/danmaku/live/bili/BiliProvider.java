package com.fongmi.android.tv.player.danmaku.live.bili;

import com.fongmi.android.tv.player.danmaku.LiveDanmakuMessage;
import com.fongmi.android.tv.player.danmaku.live.PlatformDanmakuProvider;
import com.fongmi.android.tv.player.danmaku.live.PreparedTarget;
import com.fongmi.android.tv.player.danmaku.live.RoomRef;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
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
 * Bilibili 直播弹幕 provider，照 dart_simple_live 的 bilibili_site.dart＋bilibili_danmaku.dart 对译：
 * 预备步先过 getInfoByRoom 把短号换真实房间号并校验在播（live_status＝1 才接），再用 spi 的 buvid3
 * 兜底、带 buvid Cookie 调 getDanmuInfo 取 token 与 host_list（两跳都要 WBI 签名，nav 取 keys）；
 * 连 wss://{host}/sub 发 op7 进房包（uid＝0 匿名、protover＝2 只走 zlib），30 秒 op2 心跳；
 * op5 解 DANMU_MSG（文本/昵称/颜色）与 SUPER_CHAT_MESSAGE，op3 回人气喂 online。
 * 免用户登录、免登录 Cookie；校验不过/取不到 token 一律静默不接。
 */
public final class BiliProvider implements PlatformDanmakuProvider {

    public static final String NAV_URL = "https://api.bilibili.com/x/web-interface/nav";
    public static final String SPI_URL = "https://api.bilibili.com/x/frontend/finger/spi";
    public static final String INFO_BY_ROOM_URL = "https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom";
    public static final String DANMU_INFO_URL = "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo";
    public static final String DEFAULT_HOST = "broadcastlv.chat.bilibili.com";
    public static final long HEARTBEAT_MS = 30_000L;

    private static final ExecutorService PREPARE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "live-danmaku-prepare");
        thread.setDaemon(true);
        return thread;
    });

    private final Api api;

    public BiliProvider() {
        this(new LiveApi());
    }

    BiliProvider(Api api) {
        this.api = api;
    }

    @Override
    public RoomRef.Platform platform() {
        return RoomRef.Platform.BILI;
    }

    @Override
    public CompletableFuture<Optional<PreparedTarget>> prepare(RoomRef room) {
        return CompletableFuture.supplyAsync(() -> prepareSync(room), PREPARE_EXECUTOR);
    }

    private Optional<PreparedTarget> prepareSync(RoomRef room) {
        try {
            JSONObject info = api.getInfoByRoom(room.roomId());
            if (info == null || info.optInt("code", -1) != 0) return Optional.empty();
            JSONObject data = info.optJSONObject("data");
            JSONObject roomInfo = data == null ? null : data.optJSONObject("room_info");
            if (roomInfo == null) return Optional.empty();
            long realId = roomInfo.optLong("room_id", -1);
            if (realId <= 0) return Optional.empty();
            if (roomInfo.optInt("live_status", 0) != 1) return Optional.empty();
            JSONObject danmu = api.getDanmuInfo(realId);
            if (danmu == null || danmu.optInt("code", -1) != 0) return Optional.empty();
            JSONObject danmuData = danmu.optJSONObject("data");
            if (danmuData == null) return Optional.empty();
            String token = danmuData.optString("token", "");
            if (token.isEmpty()) return Optional.empty();
            String host = DEFAULT_HOST;
            JSONArray hosts = danmuData.optJSONArray("host_list");
            if (hosts != null && hosts.length() > 0) {
                JSONObject first = hosts.optJSONObject(0);
                String candidate = first == null ? "" : first.optString("host", "");
                if (!candidate.isEmpty()) host = candidate;
            }
            String buvid = api.buvid3();
            byte[] auth = BiliPacketCodec.encodeAuth(realId, buvid == null ? "" : buvid, token);
            return Optional.of(new PreparedTarget("wss://" + host + "/sub", Map.of(), List.of(auth), HEARTBEAT_MS));
        } catch (Throwable error) {
            return Optional.empty();
        }
    }

    @Override
    public byte[] heartbeatFrame(RoomRef room) {
        return BiliPacketCodec.encodeHeartbeat();
    }

    @Override
    public DecodeResult decode(byte[] frame, long generation, long receivedAtMs) {
        List<LiveDanmakuMessage> messages = new ArrayList<>();
        Long online = null;
        for (BiliPacketCodec.RawPacket packet : BiliPacketCodec.decode(frame)) {
            if (packet.op() == BiliPacketCodec.OP_HEARTBEAT_REPLY) {
                if (packet.body().length >= 4) online = (long) BiliPacketCodec.getInt32Be(packet.body(), 0);
            } else if (packet.op() == BiliPacketCodec.OP_MESSAGE) {
                DanmuMessage danmu = parseMessage(new String(packet.body(), StandardCharsets.UTF_8));
                if (danmu == null) continue;
                LiveDanmakuMessage.Type type = danmu.superChat() ? LiveDanmakuMessage.Type.SUPER_CHAT : LiveDanmakuMessage.Type.NORMAL;
                messages.add(new LiveDanmakuMessage(type, danmu.text(), danmu.colorArgb(), receivedAtMs, generation));
            }
        }
        if (messages.isEmpty() && online == null) return DecodeResult.EMPTY;
        return new DecodeResult(messages, online);
    }

    /**
     * 一条推送 JSON 的解出结果：昵称＋文本＋颜色。统一消息模型与网关路一致只带文本与颜色，
     * 昵称在此解出备查、不进渲染文本（与斗鱼口径一致）。
     * 过滤口径照参照实现：cmd 含 DANMU_MSG 的取 info[1] 文本、info[2][1] 昵称、info[0][3] 颜色
     * （十进制 RGB，0 为白）；SUPER_CHAT_MESSAGE 取 data.message；其余 cmd 一律丢。
     */
    record DanmuMessage(String nickname, String text, int colorArgb, boolean superChat) {
    }

    static DanmuMessage parseMessage(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(json);
            String cmd = root.optString("cmd", "");
            if (cmd.contains("DANMU_MSG")) {
                JSONArray info = root.optJSONArray("info");
                if (info == null || info.length() < 3) return null;
                Object textValue = info.opt(1);
                String text = textValue == null ? "" : String.valueOf(textValue);
                if (text.isEmpty()) return null;
                JSONArray sender = info.optJSONArray(2);
                String nickname = sender == null ? "" : sender.optString(1, "");
                JSONArray meta = info.optJSONArray(0);
                int color = meta == null ? 0 : meta.optInt(3, 0);
                int argb = color == 0 ? 0xFFFFFFFF : (0xFF000000 | (color & 0xFFFFFF));
                return new DanmuMessage(nickname, text, argb, false);
            }
            if ("SUPER_CHAT_MESSAGE".equals(cmd)) {
                JSONObject scData = root.optJSONObject("data");
                if (scData == null) return null;
                String text = scData.optString("message", "");
                if (text.isEmpty()) return null;
                JSONObject user = scData.optJSONObject("user_info");
                String nickname = user == null ? "" : user.optString("uname", "");
                return new DanmuMessage(nickname, text, parseHexColor(scData.optString("background_color", "")), true);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    /** "#RRGGBB" → ARGB；解析不出回白色（参照实现 SC 颜色另有结构化字段，此处只取底色近似）。 */
    static int parseHexColor(String raw) {
        if (raw != null) {
            String hex = raw.startsWith("#") ? raw.substring(1) : raw;
            if (hex.length() == 6) {
                try {
                    return 0xFF000000 | Integer.parseInt(hex, 16);
                } catch (NumberFormatException ignored) {
                    return 0xFFFFFFFF;
                }
            }
        }
        return 0xFFFFFFFF;
    }

    interface Api {

        JSONObject getInfoByRoom(String candidateId) throws Exception;

        JSONObject getDanmuInfo(long realId) throws Exception;

        /** spi 的 buvid3；取不到回空串（匿名兜底，不许抛）。 */
        String buvid3();
    }

    /** 真接口实现：nav 取 WBI keys（静态缓存）、spi 取 buvid、两跳签名调用（带 buvid Cookie，照参照 getHeader）。 */
    static final class LiveApi implements Api {

        private static String cachedMixinKey;

        private String[] buvid;

        @Override
        public JSONObject getInfoByRoom(String candidateId) throws Exception {
            String query = BiliWbi.buildSignedQuery(Map.of("room_id", candidateId), mixinKey(), System.currentTimeMillis() / 1000);
            return httpGet(INFO_BY_ROOM_URL + "?" + query, cookieHeader());
        }

        @Override
        public JSONObject getDanmuInfo(long realId) throws Exception {
            String query = BiliWbi.buildSignedQuery(Map.of("id", String.valueOf(realId)), mixinKey(), System.currentTimeMillis() / 1000);
            return httpGet(DANMU_INFO_URL + "?" + query, cookieHeader());
        }

        @Override
        public synchronized String buvid3() {
            return buvid()[0];
        }

        private synchronized String[] buvid() {
            if (buvid != null) return buvid;
            String b3 = "";
            String b4 = "";
            try {
                JSONObject root = httpGet(SPI_URL, "");
                JSONObject data = root.optJSONObject("data");
                if (data != null) {
                    b3 = data.optString("b_3", "");
                    b4 = data.optString("b_4", "");
                }
            } catch (Exception ignored) {
            }
            buvid = new String[]{b3, b4};
            return buvid;
        }

        private String cookieHeader() {
            String[] pair = buvid();
            return pair[0].isEmpty() ? "" : "buvid3=" + pair[0] + ";buvid4=" + pair[1];
        }

        private static synchronized String mixinKey() throws Exception {
            if (cachedMixinKey != null) return cachedMixinKey;
            JSONObject root = httpGet(NAV_URL, "");
            JSONObject data = root.optJSONObject("data");
            JSONObject wbiImg = data == null ? null : data.optJSONObject("wbi_img");
            if (wbiImg == null) throw new IllegalStateException("wbi_img missing");
            cachedMixinKey = BiliWbi.mixinKey(fileKey(wbiImg.optString("img_url", "")), fileKey(wbiImg.optString("sub_url", "")));
            return cachedMixinKey;
        }

        private static String fileKey(String url) {
            int slash = url.lastIndexOf('/');
            String name = slash >= 0 ? url.substring(slash + 1) : url;
            int dot = name.indexOf('.');
            return dot >= 0 ? name.substring(0, dot) : name;
        }

        private static JSONObject httpGet(String url, String cookie) throws Exception {
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://live.bilibili.com/");
            if (cookie != null && !cookie.isEmpty()) builder.header("Cookie", cookie);
            try (Response response = OkHttp.client().newCall(builder.build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) throw new IllegalStateException("http " + response.code());
                return new JSONObject(response.body().string());
            }
        }
    }
}
