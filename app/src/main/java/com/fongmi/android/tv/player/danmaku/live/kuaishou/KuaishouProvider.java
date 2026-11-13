package com.fongmi.android.tv.player.danmaku.live.kuaishou;

import com.fongmi.android.tv.login.LoginManager;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuMessage;
import com.fongmi.android.tv.player.danmaku.live.PlatformDanmakuProvider;
import com.fongmi.android.tv.player.danmaku.live.PreparedTarget;
import com.fongmi.android.tv.player.danmaku.live.RoomRef;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLDecoder;
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

import okhttp3.Request;
import okhttp3.Response;

/**
 * 快手直播弹幕 provider，照 dart_simple_live 快手分支（simple-live-kuaishou 的
 * kuaishou_site.dart＋kuaishou_danmaku.dart）对译：
 * 看播匿名可看，弹幕是另一道门——websocketinfo 匿名调用只回 {"data":{"result":2}}
 * （2026-10-24 本机实拉双路复证：带设备 Cookie 预热与完全裸连同果），必须带网页登录
 * Cookie 才换得到每房的 token＋websocketUrls。Cookie 只从壳子统一登录托管取
 * （LoginManager.getCookie("kuaishou")），本类不自造登录、不存任何凭证；
 * 没托管凭证时 prepare 直接静默不成立、连房间页都不发，播放全程不弹窗。
 * 预备步：带 Cookie 拉房间页 SSR 状态（window.__INITIAL_STATE__ 的 liveroom），
 * 从 playList 选在播条目取 liveStream.id/expTag 与条目 expTag（attach），页面内嵌了
 * token/websocketUrls 就直接用，缺一才调 websocketinfo 补取（带 Kww 头，至多两次）；
 * 连 websocketUrls 首个地址，头带 UA/Origin/Referer/Cookie，连上先发进房帧，20 秒心跳。
 * 未开播、凭证失效、补取仍缺件一律静默不接，弹幕断不断播。
 */
public final class KuaishouProvider implements PlatformDanmakuProvider {

    public static final String ROOM_PAGE_URL = "https://live.kuaishou.com/u/";
    public static final String WEBSOCKET_INFO_URL = "https://live.kuaishou.com/live_api/liveroom/websocketinfo";
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    public static final long HEARTBEAT_MS = 20_000L;
    /** 壳子统一登录托管里的提供方 id（与 LoginManager 注册口径一致）。 */
    public static final String LOGIN_PROVIDER_ID = "kuaishou";

    private static final Pattern INITIAL_STATE = Pattern.compile("window\\.__INITIAL_STATE__=(.*?);", Pattern.DOTALL);
    private static final String PAGE_ID_CHARS = "useandom-26T198340PX75pxJACKVERYMINDBUSHWOLF_GQZbfghjklqvwyzrict";
    private static final long RETRY_DELAY_MS = 500L;

    private static final ExecutorService PREPARE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "live-danmaku-prepare");
        thread.setDaemon(true);
        return thread;
    });

    private final Api api;
    private final Cookies cookies;

    public KuaishouProvider() {
        this(new LiveApi(), new HostedCookies());
    }

    KuaishouProvider(Api api, Cookies cookies) {
        this.api = api;
        this.cookies = cookies;
    }

    @Override
    public RoomRef.Platform platform() {
        return RoomRef.Platform.KUAISHOU;
    }

    @Override
    public CompletableFuture<Optional<PreparedTarget>> prepare(RoomRef room) {
        return CompletableFuture.supplyAsync(() -> prepareSync(room), PREPARE_EXECUTOR);
    }

    private Optional<PreparedTarget> prepareSync(RoomRef room) {
        try {
            String cookie = cookies.cookie();
            if (cookie == null || cookie.isEmpty()) return Optional.empty();
            String html = api.fetchRoomPage(room.roomId(), cookie);
            RoomPage page = parseRoomPage(html);
            if (page == null || !page.living() || page.liveStreamId().isEmpty()) return Optional.empty();
            String authorId = page.authorId().isEmpty() ? room.roomId() : page.authorId();
            String token = page.token();
            List<String> urls = page.websocketUrls();
            // 页面内嵌凭证未就绪时补调 websocketinfo（照参照实现：至多两次、间隔 500ms）
            for (int attempt = 0; attempt < 2 && (token.isEmpty() || urls.isEmpty()); attempt++) {
                if (attempt > 0) Thread.sleep(RETRY_DELAY_MS);
                JSONObject info = api.fetchWebsocketInfo(page.liveStreamId(), authorId, cookie);
                WebsocketInfo parsed = parseWebsocketInfo(info);
                if (!parsed.token().isEmpty()) token = parsed.token();
                if (!parsed.websocketUrls().isEmpty()) urls = parsed.websocketUrls();
            }
            if (token.isEmpty() || urls.isEmpty()) return Optional.empty();
            byte[] enter = KuaishouPacketCodec.encodeEnterRoom(token, page.liveStreamId(), page.expTag(), page.attach(), generatePageId());
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", USER_AGENT);
            headers.put("Origin", "https://live.kuaishou.com");
            headers.put("Referer", ROOM_PAGE_URL + authorId);
            headers.put("Cookie", cookie);
            return Optional.of(new PreparedTarget(urls.get(0), headers, List.of(enter), HEARTBEAT_MS));
        } catch (Throwable error) {
            return Optional.empty();
        }
    }

    @Override
    public byte[] heartbeatFrame(RoomRef room) {
        return KuaishouPacketCodec.encodeHeartbeat(System.currentTimeMillis());
    }

    @Override
    public DecodeResult decode(byte[] frame, long generation, long receivedAtMs) {
        KuaishouPacketCodec.Decoded decoded = KuaishouPacketCodec.decode(frame);
        if (decoded.chats().isEmpty()) return DecodeResult.EMPTY;
        List<LiveDanmakuMessage> messages = new ArrayList<>();
        for (KuaishouPacketCodec.Chat chat : decoded.chats()) {
            messages.add(new LiveDanmakuMessage(LiveDanmakuMessage.Type.NORMAL, chat.text(), chat.colorArgb(), receivedAtMs, generation));
        }
        return new DecodeResult(messages, null);
    }

    /** 房间页提取产物：在播判定＋liveStreamId＋页面内嵌凭证（缺件留空，备 websocketinfo 补取）。 */
    record RoomPage(boolean living, String liveStreamId, String token, List<String> websocketUrls, String expTag, String attach, String authorId) {

        RoomPage {
            websocketUrls = websocketUrls == null ? List.of() : List.copyOf(websocketUrls);
        }
    }

    record WebsocketInfo(String token, List<String> websocketUrls) {

        static final WebsocketInfo EMPTY = new WebsocketInfo("", List.of());

        WebsocketInfo {
            websocketUrls = websocketUrls == null ? List.of() : List.copyOf(websocketUrls);
        }
    }

    /**
     * 房间页解析（照参照 _parseRoomDetail）：状态 JSON 里的 undefined 先替 null 再解；
     * playList 逐条按「liveStream.id 非空或 isLiving/living 为真」判在播、选第一条在播的
     * （一条都没标在播时退回第一条并按其状态判）；限流/未开播的空壳页（条目 liveStream
     * 为空对象、带 errorType）自然落成 living=false。正则取不到状态时再以括号配平兜底
     * （正文标题里带分号会截断正则口径，兜底保证整段 JSON 可解）。
     */
    static RoomPage parseRoomPage(String html) {
        if (html == null || html.isEmpty()) return null;
        JSONObject state = extractInitialState(html);
        if (state == null) return null;
        JSONObject liveroom = state.optJSONObject("liveroom");
        if (liveroom == null) return null;
        JSONArray playList = liveroom.optJSONArray("playList");
        if (playList == null || playList.length() == 0) return null;
        JSONObject chosen = null;
        JSONObject first = null;
        for (int i = 0; i < playList.length(); i++) {
            JSONObject item = playList.optJSONObject(i);
            if (item == null) continue;
            if (first == null) first = item;
            if (isLiving(item)) {
                chosen = item;
                break;
            }
        }
        if (chosen == null) chosen = first;
        if (chosen == null) return null;
        JSONObject liveStream = chosen.optJSONObject("liveStream");
        String liveStreamId = liveStream == null ? "" : liveStream.optString("id", "");
        boolean living = !liveStreamId.isEmpty() || isLiving(chosen);
        String token = liveroom.optString("token", "");
        List<String> urls = stringList(liveroom.opt("websocketUrls"));
        JSONObject embedded = chosen.optJSONObject("websocketInfo");
        if (embedded != null) {
            if (token.isEmpty()) token = embedded.optString("token", "");
            if (urls.isEmpty()) {
                urls = stringList(embedded.opt("websocketUrls"));
                if (urls.isEmpty()) urls = stringList(embedded.opt("webSocketAddresses"));
            }
        }
        JSONObject author = chosen.optJSONObject("author");
        String authorId = author == null ? "" : author.optString("id", "");
        String expTag = liveStream == null ? "" : liveStream.optString("expTag", "");
        String attach = chosen.optString("expTag", "");
        return new RoomPage(living, liveStreamId, token, urls, expTag, attach, authorId);
    }

    private static boolean isLiving(JSONObject item) {
        JSONObject liveStream = item.optJSONObject("liveStream");
        String id = liveStream == null ? "" : liveStream.optString("id", "");
        return !id.isEmpty() || truthy(item.opt("isLiving")) || truthy(item.opt("living"));
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() == 1;
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

    static JSONObject extractInitialState(String html) {
        Matcher matcher = INITIAL_STATE.matcher(html);
        if (matcher.find()) {
            JSONObject parsed = parseState(matcher.group(1));
            if (parsed != null) return parsed;
        }
        int anchor = html.indexOf("window.__INITIAL_STATE__=");
        if (anchor < 0) return null;
        return parseState(balancedObject(html, anchor + "window.__INITIAL_STATE__=".length()));
    }

    private static JSONObject parseState(String json) {
        if (json == null) return null;
        try {
            return new JSONObject(json.replace("undefined", "null"));
        } catch (Exception error) {
            return null;
        }
    }

    /** 括号配平提取完整 JSON 对象（跳过字符串内的括号与转义），取不到返回 null。 */
    private static String balancedObject(String text, int from) {
        int start = text.indexOf('{', from);
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return text.substring(start, i + 1);
        }
        return null;
    }

    /** websocketinfo 响应解析：data 不是对象、或只有 result（匿名 result＝2）时自然全空。 */
    static WebsocketInfo parseWebsocketInfo(JSONObject root) {
        if (root == null) return WebsocketInfo.EMPTY;
        JSONObject data = root.optJSONObject("data");
        if (data == null) return WebsocketInfo.EMPTY;
        String token = data.optString("token", "");
        List<String> urls = stringList(data.opt("websocketUrls"));
        if (urls.isEmpty()) urls = stringList(data.opt("webSocketAddresses"));
        return new WebsocketInfo(token, urls);
    }

    private static List<String> stringList(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                String item = array.optString(i, "").trim();
                if (!item.isEmpty()) out.add(item);
            }
        } else if (value instanceof String text) {
            if (!text.trim().isEmpty()) out.add(text.trim());
        }
        return out;
    }

    /**
     * Kww 头派生（照参照 resolveServerKww）：取登录 Cookie 里的 kwfv1 值，
     * URL 解码后拼 "###ssrc"；Cookie 里没有 kwfv1 就返回空串（不带 Kww 头）。
     */
    static String kwwFromCookie(String cookie) {
        if (cookie == null) return "";
        for (String part : cookie.split(";")) {
            String item = part.trim();
            if (!item.startsWith("kwfv1=")) continue;
            String value = item.substring("kwfv1=".length()).trim();
            if (value.isEmpty()) continue;
            try {
                return URLDecoder.decode(value, StandardCharsets.UTF_8) + "###ssrc";
            } catch (Exception ignored) {
                return value + "###ssrc";
            }
        }
        return "";
    }

    /** pageId 生成（照参照 _generatePageId）：固定字符表里随机取 16 个字符。 */
    static String generatePageId() {
        StringBuilder out = new StringBuilder(16);
        for (int i = 0; i < 16; i++) out.append(PAGE_ID_CHARS.charAt(ThreadLocalRandom.current().nextInt(PAGE_ID_CHARS.length())));
        return out.toString();
    }

    interface Cookies {

        /** 壳子统一登录托管里的快手网页登录 Cookie；没登录返回空串。 */
        String cookie();
    }

    /** 默认凭证来源：只读壳子统一登录托管，本类不接触任何登录流程与存储。 */
    static final class HostedCookies implements Cookies {

        @Override
        public String cookie() {
            try {
                String cookie = LoginManager.getCookie(LOGIN_PROVIDER_ID);
                return cookie == null ? "" : cookie;
            } catch (Throwable error) {
                return "";
            }
        }
    }

    interface Api {

        /** 带登录 Cookie 拉房间页全文；失败抛错由 prepare 吞成静默不接。 */
        String fetchRoomPage(String roomId, String cookie) throws Exception;

        /** websocketinfo 补取；返回解析后的根 JSON，失败抛错由 prepare 吞成静默不接。 */
        JSONObject fetchWebsocketInfo(String liveStreamId, String roomId, String cookie) throws Exception;
    }

    /** 真接口实现：房间页与 websocketinfo 都带托管 Cookie，websocketinfo 另带 Kww/Referer（照参照口径）。 */
    static final class LiveApi implements Api {

        @Override
        public String fetchRoomPage(String roomId, String cookie) throws Exception {
            Request request = new Request.Builder()
                    .url(ROOM_PAGE_URL + roomId)
                    .header("User-Agent", USER_AGENT)
                    .header("Cookie", cookie)
                    .build();
            try (Response response = OkHttp.client().newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) throw new IllegalStateException("http " + response.code());
                return response.body().string();
            }
        }

        @Override
        public JSONObject fetchWebsocketInfo(String liveStreamId, String roomId, String cookie) throws Exception {
            Request.Builder builder = new Request.Builder()
                    .url(WEBSOCKET_INFO_URL + "?liveStreamId=" + liveStreamId)
                    .header("User-Agent", USER_AGENT)
                    .header("accept", "application/json, text/plain, */*")
                    .header("referer", ROOM_PAGE_URL + roomId)
                    .header("Cookie", cookie);
            String kww = kwwFromCookie(cookie);
            if (!kww.isEmpty()) builder.header("Kww", kww);
            try (Response response = OkHttp.client().newCall(builder.build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) throw new IllegalStateException("http " + response.code());
                return new JSONObject(response.body().string());
            }
        }
    }
}
