package com.fongmi.android.tv.player.danmaku.live.douyin;

import com.fongmi.android.tv.player.danmaku.LiveDanmakuMessage;
import com.fongmi.android.tv.player.danmaku.live.PlatformDanmakuProvider;
import com.fongmi.android.tv.player.danmaku.live.PreparedTarget;
import com.fongmi.android.tv.player.danmaku.live.RoomRef;
import com.github.catvod.net.OkHttp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 抖音直播弹幕 provider，照 dart_simple_live 的 douyin_site.dart＋douyin_danmaku.dart 对译：
 * 识别层给的房号是 webRid（固定不变），预备步先 HEAD 房间页攒 ttwid 等 Cookie、
 * 再 GET 房间页全文，从 SSR 状态里提取真实 room_id（每场新生成）、在播状态
 * （status＝2 才接）与 odin 的 user_unique_id；随后用 webmssdk 签名
 * （DouyinMssdkSigner，QuickJS 内跑，与 a_bogus 是两套东西）拼 wss 地址
 * webcast3-ws-web-lq.douyin.com/webcast/im/push/v2/，带 Cookie/Origin/UA 连入；
 * 进房即发一帧 hb、之后 10 秒一帧心跳；推送解码与 needAck 回执在 DouyinPacketCodec，
 * 回执帧经 DecodeResult.replyFrames 由 Coordinator 代发。
 * 页面拉不到、未开播、签名失败一律静默不接。
 */
public final class DouyinProvider implements PlatformDanmakuProvider {

    public static final String WS_URL = "wss://webcast3-ws-web-lq.douyin.com/webcast/im/push/v2/";
    public static final String ROOM_PAGE_URL = "https://live.douyin.com/";
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.97 Safari/537.36 Core/1.116.567.400 QQBrowser/19.7.6764.400";
    public static final long HEARTBEAT_MS = 10_000L;
    public static final int LIVE_STATUS = 2;

    private static final Pattern ROOM_ID = Pattern.compile("\"id_str\":\"(\\d+)\"");
    private static final Pattern ROOM_STATUS = Pattern.compile("\"status\":(\\d+)");
    private static final Pattern USER_UNIQUE_ID = Pattern.compile("\"user_unique_id\":\"(\\d+)\"");
    private static final int ROOM_SEGMENT_CHARS = 6_000;
    private static final int ODIN_SEGMENT_CHARS = 2_000;

    private static final ExecutorService PREPARE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "live-danmaku-prepare");
        thread.setDaemon(true);
        return thread;
    });

    private final Api api;
    private final Signer signer;

    public DouyinProvider() {
        this(new LiveApi(), new DouyinMssdkSigner());
    }

    DouyinProvider(Api api, Signer signer) {
        this.api = api;
        this.signer = signer;
    }

    @Override
    public RoomRef.Platform platform() {
        return RoomRef.Platform.DOUYIN;
    }

    @Override
    public CompletableFuture<Optional<PreparedTarget>> prepare(RoomRef room) {
        return CompletableFuture.supplyAsync(() -> prepareSync(room), PREPARE_EXECUTOR);
    }

    private Optional<PreparedTarget> prepareSync(RoomRef room) {
        try {
            Api.RoomPageData fetched = api.fetchRoomPage(room.roomId());
            if (fetched == null) return Optional.empty();
            RoomPage page = parseRoomPage(fetched.html());
            if (page == null || page.status() != LIVE_STATUS || page.roomId().isEmpty()) return Optional.empty();
            String signature = signer.sign(page.roomId(), page.userUniqueId());
            if (signature == null || signature.isEmpty()) return Optional.empty();
            String url = buildWsUrl(page.roomId(), page.userUniqueId(), signature, System.currentTimeMillis());
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", USER_AGENT);
            if (fetched.cookie() != null && !fetched.cookie().isEmpty()) headers.put("Cookie", fetched.cookie());
            headers.put("Origin", "https://live.douyin.com");
            return Optional.of(new PreparedTarget(url, headers, List.of(DouyinPacketCodec.encodeHeartbeat()), HEARTBEAT_MS));
        } catch (Throwable error) {
            return Optional.empty();
        }
    }

    @Override
    public byte[] heartbeatFrame(RoomRef room) {
        return DouyinPacketCodec.encodeHeartbeat();
    }

    @Override
    public DecodeResult decode(byte[] frame, long generation, long receivedAtMs) {
        DouyinPacketCodec.Decoded decoded = DouyinPacketCodec.decode(frame);
        if (decoded.events().isEmpty() && decoded.ackFrame() == null) return DecodeResult.EMPTY;
        List<LiveDanmakuMessage> messages = new ArrayList<>();
        Long online = null;
        for (DouyinPacketCodec.Event event : decoded.events()) {
            if (event instanceof DouyinPacketCodec.Event.ChatEvent chatEvent) {
                DouyinPacketCodec.Chat chat = chatEvent.chat();
                messages.add(new LiveDanmakuMessage(LiveDanmakuMessage.Type.NORMAL, chat.text(), chat.colorArgb(), receivedAtMs, generation));
            } else if (event instanceof DouyinPacketCodec.Event.OnlineEvent onlineEvent) {
                online = onlineEvent.totalUser();
            }
        }
        List<byte[]> replies = decoded.ackFrame() == null ? List.of() : List.of(decoded.ackFrame());
        return new DecodeResult(messages, online, replies);
    }

    /** 房间页提取产物：真实 room_id＋开播状态＋访客唯一 id，任一关键项缺失整页作废。 */
    record RoomPage(String roomId, int status, String userUniqueId) {
    }

    /**
     * 提取口径（2026-10-24 本机实拉页面复核）：房间页 SSR 状态是转义后的 JSON 文本，
     * 先整页反转义（\"→"、\\→\），再锚定 roomStore.roomInfo.room 段取 id_str/status，
     * user_unique_id 取 userStore.odin 段内的值、缺失回落整页首个命中、再缺失随机生成
     * （参照实现的接口路同样用随机数兜底）。roomInfo 为空（未开播/不存在）返回 null。
     */
    static RoomPage parseRoomPage(String html) {
        if (html == null || html.isEmpty()) return null;
        String text = html.replace("\\\"", "\"").replace("\\\\", "\\");
        int anchor = text.indexOf("\"roomInfo\":{\"room\"");
        if (anchor < 0) return null;
        String segment = text.substring(anchor, Math.min(text.length(), anchor + ROOM_SEGMENT_CHARS));
        Matcher idMatcher = ROOM_ID.matcher(segment);
        if (!idMatcher.find()) return null;
        Matcher statusMatcher = ROOM_STATUS.matcher(segment);
        if (!statusMatcher.find()) return null;
        int status;
        try {
            status = Integer.parseInt(statusMatcher.group(1));
        } catch (NumberFormatException error) {
            return null;
        }
        return new RoomPage(idMatcher.group(1), status, extractUserUniqueId(text));
    }

    private static String extractUserUniqueId(String text) {
        int odin = text.indexOf("\"odin\":{");
        if (odin >= 0) {
            String segment = text.substring(odin, Math.min(text.length(), odin + ODIN_SEGMENT_CHARS));
            Matcher matcher = USER_UNIQUE_ID.matcher(segment);
            if (matcher.find()) return matcher.group(1);
        }
        Matcher fallback = USER_UNIQUE_ID.matcher(text);
        if (fallback.find()) return fallback.group(1);
        // 参照实现兜底：随机数字串（odin 本就是访客级匿名 id，每次进页都新发）
        StringBuilder random = new StringBuilder(19);
        random.append(ThreadLocalRandom.current().nextInt(1, 10));
        for (int i = 1; i < 19; i++) random.append(ThreadLocalRandom.current().nextInt(0, 10));
        return random.toString();
    }

    /** wss 参数表照参照实现逐项拼（顺序即参照顺序），signature 挂在最后。 */
    static String buildWsUrl(String roomId, String userUniqueId, String signature, long timestampMs) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_name", "douyin_web");
        params.put("version_code", "180800");
        params.put("webcast_sdk_version", "1.3.0");
        params.put("update_version_code", "1.3.0");
        params.put("compress", "gzip");
        params.put("cursor", "h-1_t-" + timestampMs + "_r-1_d-1_u-1");
        params.put("host", "https://live.douyin.com");
        params.put("aid", "6383");
        params.put("live_id", "1");
        params.put("did_rule", "3");
        params.put("debug", "false");
        params.put("maxCacheMessageNumber", "20");
        params.put("endpoint", "live_pc");
        params.put("support_wrds", "1");
        params.put("im_path", "/webcast/im/fetch/");
        params.put("user_unique_id", userUniqueId);
        params.put("device_platform", "web");
        params.put("cookie_enabled", "true");
        params.put("screen_width", "1920");
        params.put("screen_height", "1080");
        params.put("browser_language", "zh-CN");
        params.put("browser_platform", "Win32");
        params.put("browser_name", "Mozilla");
        params.put("browser_version", USER_AGENT.replace("Mozilla/", ""));
        params.put("browser_online", "true");
        params.put("tz_name", "Asia/Shanghai");
        params.put("identity", "audience");
        params.put("room_id", roomId);
        params.put("heartbeatDuration", "0");
        StringBuilder url = new StringBuilder(WS_URL).append('?');
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) url.append('&');
            first = false;
            url.append(entry.getKey()).append('=').append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return url.append("&signature=").append(URLEncoder.encode(signature, StandardCharsets.UTF_8)).toString();
    }

    interface Signer {

        String sign(String roomId, String userUniqueId) throws Exception;
    }

    interface Api {

        /** cookie 是给 wss 握手带的访客凭证（ttwid 为主），html 是房间页全文。 */
        record RoomPageData(String cookie, String html) {
        }

        RoomPageData fetchRoomPage(String webRid) throws Exception;
    }

    /**
     * 真接口实现，照参照实现的 _getWebCookie＋_getRoomDataByHtml：
     * 先 HEAD 房间页攒 ttwid/__ac_nonce/msToken（手动跟重定向逐跳攒），
     * 再带 Cookie GET 全文（GET 响应新发的同名 Cookie 一并合并）。
     */
    static final class LiveApi implements Api {

        private static final List<String> COOKIE_KEYS = List.of("ttwid", "__ac_nonce", "msToken");

        @Override
        public RoomPageData fetchRoomPage(String webRid) throws Exception {
            Map<String, String> cookies = new LinkedHashMap<>();
            collectHeadCookies(webRid, cookies);
            Request request = new Request.Builder()
                    .url(ROOM_PAGE_URL + webRid)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://live.douyin.com")
                    .header("Cookie", cookieHeader(cookies))
                    .build();
            try (Response response = OkHttp.client().newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) throw new IllegalStateException("http " + response.code());
                mergeCookies(response, cookies);
                return new RoomPageData(cookieHeader(cookies), response.body().string());
            }
        }

        private void collectHeadCookies(String webRid, Map<String, String> cookies) {
            try {
                okhttp3.OkHttpClient noRedirect = OkHttp.client().newBuilder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .build();
                String url = ROOM_PAGE_URL + webRid;
                for (int hop = 0; hop < 5 && url != null; hop++) {
                    Request request = new Request.Builder().url(url).head()
                            .header("User-Agent", USER_AGENT)
                            .build();
                    try (Response response = noRedirect.newCall(request).execute()) {
                        mergeCookies(response, cookies);
                        url = null;
                        if (response.code() >= 300 && response.code() < 400) {
                            String location = response.header("Location");
                            HttpUrl next = location == null ? null : response.request().url().resolve(location);
                            if (next != null) url = next.toString();
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Cookie 攒不到不算致命：GET 与 wss 仍可试（参照实现亦无此步的失败分支）
            }
        }

        private void mergeCookies(Response response, Map<String, String> cookies) {
            for (String header : response.headers("Set-Cookie")) {
                String pair = header.split(";", 2)[0];
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                String key = pair.substring(0, eq).trim();
                if (COOKIE_KEYS.contains(key)) cookies.put(key, pair.substring(eq + 1).trim());
            }
        }

        private String cookieHeader(Map<String, String> cookies) {
            StringBuilder header = new StringBuilder();
            for (Map.Entry<String, String> entry : cookies.entrySet()) {
                if (header.length() > 0) header.append("; ");
                header.append(entry.getKey()).append('=').append(entry.getValue());
            }
            return header.toString();
        }
    }
}
