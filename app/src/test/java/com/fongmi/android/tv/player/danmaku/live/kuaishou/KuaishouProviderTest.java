package com.fongmi.android.tv.player.danmaku.live.kuaishou;

import com.fongmi.android.tv.player.danmaku.LiveDanmakuMessage;
import com.fongmi.android.tv.player.danmaku.live.PlatformDanmakuProvider;
import com.fongmi.android.tv.player.danmaku.live.PreparedTarget;
import com.fongmi.android.tv.player.danmaku.live.RoomRef;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 快手 provider 预备步：凭证门（没托管 Cookie 直接静默、零请求）、房间页提取、
 * websocketinfo 补取（含 2026-10-24 实拉的匿名 result＝2 响应原样回放）、
 * 目标拼装（进房帧字段/头/心跳间隔）。页面样本按参照解析口径自造，
 * 限流空壳页是当日匿名实拉的真响应（kuaishou-room-throttled.html）。
 */
public class KuaishouProviderTest {

    private static final RoomRef ROOM = new RoomRef(RoomRef.Platform.KUAISHOU, "laoayi-88");
    private static final String COOKIE = "did=web_abc; kwfv1=abc%20def; userId=424242";
    private static final String LIVE_STREAM_ID = "1_B7BrugJfg";

    /** 在播页：凭证未内嵌（token/urls 全空），须走 websocketinfo 补取。 */
    private static String livePageNoCreds() {
        return page("{\"liveroom\":{\"playList\":[{"
                + "\"liveStream\":{\"id\":\"" + LIVE_STREAM_ID + "\",\"expTag\":\"exp-1\"},"
                + "\"author\":{\"id\":\"laoayi-88\",\"name\":\"老阿姨\"},"
                + "\"isLiving\":true,\"expTag\":\"att-2\",\"websocketInfo\":{}}],"
                + "\"token\":\"\",\"websocketUrls\":[]}}");
    }

    /** 在播页：凭证已内嵌在 liveroom 顶层。 */
    private static String livePageEmbedded() {
        return page("{\"liveroom\":{\"playList\":[{"
                + "\"liveStream\":{\"id\":\"" + LIVE_STREAM_ID + "\",\"expTag\":\"exp-1\"},"
                + "\"author\":{\"id\":\"laoayi-88\"},\"isLiving\":true,\"expTag\":\"att-2\"}],"
                + "\"token\":\"tok_embedded\",\"websocketUrls\":[\"wss://wsgetgif.kuaishou.com/connect\"]}}");
    }

    /** 未开播页：条目在、liveStream 空对象、无 errorType。 */
    private static String offlinePage() {
        return page("{\"liveroom\":{\"playList\":[{"
                + "\"liveStream\":{},\"author\":{\"id\":\"laoayi-88\"},\"isLiving\":false}],"
                + "\"token\":\"\",\"websocketUrls\":[]}}");
    }

    private static String page(String stateJson) {
        return "<html><head></head><body><script>window.__INITIAL_STATE__=" + stateJson + ";</script></body></html>";
    }

    private static JSONObject infoJson(String body) throws Exception {
        return new JSONObject(body);
    }

    @Test
    public void noHostedCookieSkipsSilentlyWithoutAnyRequest() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        KuaishouProvider.Api api = new KuaishouProvider.Api() {
            @Override
            public String fetchRoomPage(String roomId, String cookie) {
                calls.incrementAndGet();
                return livePageEmbedded();
            }

            @Override
            public JSONObject fetchWebsocketInfo(String liveStreamId, String roomId, String cookie) {
                calls.incrementAndGet();
                return null;
            }
        };
        KuaishouProvider provider = new KuaishouProvider(api, () -> "");
        assertTrue(provider.prepare(ROOM).get(5, TimeUnit.SECONDS).isEmpty());
        assertEquals("没凭证时连房间页都不许发", 0, calls.get());
    }

    @Test
    public void parseRoomPageExtractsLiveFields() {
        KuaishouProvider.RoomPage page = KuaishouProvider.parseRoomPage(livePageNoCreds());
        assertEquals(true, page.living());
        assertEquals(LIVE_STREAM_ID, page.liveStreamId());
        assertEquals("", page.token());
        assertTrue(page.websocketUrls().isEmpty());
        assertEquals("exp-1", page.expTag());
        assertEquals("att-2", page.attach());
        assertEquals("laoayi-88", page.authorId());
    }

    @Test
    public void parseRoomPageHandlesOfflineThrottledAndBrokenPages() throws Exception {
        assertFalse(KuaishouProvider.parseRoomPage(offlinePage()).living());
        String throttled = new String(KuaishouPacketCodecTest.readResource("/kuaishou-room-throttled.html"), StandardCharsets.UTF_8);
        KuaishouProvider.RoomPage shell = KuaishouProvider.parseRoomPage(throttled);
        assertTrue("限流空壳页不许判在播", shell == null || !shell.living());
        assertNull(KuaishouProvider.parseRoomPage(null));
        assertNull(KuaishouProvider.parseRoomPage(""));
        assertNull(KuaishouProvider.parseRoomPage("<html>no state</html>"));
    }

    @Test
    public void initialStateSurvivesSemicolonInsideCaption() {
        // 标题里带分号：参照的正则口径会被截断，括号配平兜底必须把整段救回来
        String html = page("{\"note\":\"a;b;c\",\"liveroom\":{\"playList\":[{"
                + "\"liveStream\":{\"id\":\"" + LIVE_STREAM_ID + "\"},\"author\":{\"id\":\"laoayi-88\"},\"isLiving\":true}],"
                + "\"token\":\"t\",\"websocketUrls\":[\"wss://x.example/connect\"]}}");
        KuaishouProvider.RoomPage page = KuaishouProvider.parseRoomPage(html);
        assertEquals(LIVE_STREAM_ID, page.liveStreamId());
        assertEquals("t", page.token());
    }

    @Test
    public void prepareUsesEmbeddedCredentialsWithoutInfoCall() throws Exception {
        AtomicInteger infoCalls = new AtomicInteger();
        KuaishouProvider.Api api = new KuaishouProvider.Api() {
            @Override
            public String fetchRoomPage(String roomId, String cookie) {
                assertEquals("laoayi-88", roomId);
                assertEquals(COOKIE, cookie);
                return livePageEmbedded();
            }

            @Override
            public JSONObject fetchWebsocketInfo(String liveStreamId, String roomId, String cookie) {
                infoCalls.incrementAndGet();
                return null;
            }
        };
        KuaishouProvider provider = new KuaishouProvider(api, () -> COOKIE);
        Optional<PreparedTarget> target = provider.prepare(ROOM).get(5, TimeUnit.SECONDS);
        assertTrue(target.isPresent());
        assertEquals(0, infoCalls.get());
        assertEquals("wss://wsgetgif.kuaishou.com/connect", target.get().wsUrl());
        assertEquals(KuaishouProvider.HEARTBEAT_MS, target.get().heartbeatMs());
        assertEquals(COOKIE, target.get().headers().get("Cookie"));
        assertEquals("https://live.kuaishou.com", target.get().headers().get("Origin"));
        assertEquals("https://live.kuaishou.com/u/laoayi-88", target.get().headers().get("Referer"));
        assertEquals(KuaishouProvider.USER_AGENT, target.get().headers().get("User-Agent"));
        assertEquals(1, target.get().onOpenFrames().size());
        // 进房帧逐字段核：200/token/liveStreamId/expTag/attach/pageId(16 字符)
        Map<Integer, List<Object>> socket = KuaishouPacketCodec.parse(target.get().onOpenFrames().get(0));
        assertEquals(200L, socket.get(1).get(0));
        Map<Integer, List<Object>> enter = KuaishouPacketCodec.parse((byte[]) socket.get(3).get(0));
        assertEquals("tok_embedded", new String((byte[]) enter.get(1).get(0), StandardCharsets.UTF_8));
        assertEquals(LIVE_STREAM_ID, new String((byte[]) enter.get(2).get(0), StandardCharsets.UTF_8));
        assertEquals("exp-1", new String((byte[]) enter.get(5).get(0), StandardCharsets.UTF_8));
        assertEquals("att-2", new String((byte[]) enter.get(6).get(0), StandardCharsets.UTF_8));
        assertEquals(16, ((byte[]) enter.get(7).get(0)).length);
    }

    @Test
    public void prepareFallsBackToWebsocketInfo() throws Exception {
        KuaishouProvider.Api api = new KuaishouProvider.Api() {
            @Override
            public String fetchRoomPage(String roomId, String cookie) {
                return livePageNoCreds();
            }

            @Override
            public JSONObject fetchWebsocketInfo(String liveStreamId, String roomId, String cookie) throws Exception {
                assertEquals(LIVE_STREAM_ID, liveStreamId);
                assertEquals("laoayi-88", roomId);
                assertEquals(COOKIE, cookie);
                return infoJson("{\"data\":{\"result\":1,\"token\":\"tok_info\","
                        + "\"websocketUrls\":[\"wss://ws1.example/connect\",\"wss://ws2.example/connect\"]}}");
            }
        };
        KuaishouProvider provider = new KuaishouProvider(api, () -> COOKIE);
        Optional<PreparedTarget> target = provider.prepare(ROOM).get(5, TimeUnit.SECONDS);
        assertTrue(target.isPresent());
        assertEquals("wss://ws1.example/connect", target.get().wsUrl());
        Map<Integer, List<Object>> socket = KuaishouPacketCodec.parse(target.get().onOpenFrames().get(0));
        Map<Integer, List<Object>> enter = KuaishouPacketCodec.parse((byte[]) socket.get(3).get(0));
        assertEquals("tok_info", new String((byte[]) enter.get(1).get(0), StandardCharsets.UTF_8));
    }

    @Test
    public void anonymousWebsocketInfoResult2StaysSilent() throws Exception {
        AtomicInteger infoCalls = new AtomicInteger();
        KuaishouProvider.Api api = new KuaishouProvider.Api() {
            @Override
            public String fetchRoomPage(String roomId, String cookie) {
                return livePageNoCreds();
            }

            @Override
            public JSONObject fetchWebsocketInfo(String liveStreamId, String roomId, String cookie) throws Exception {
                infoCalls.incrementAndGet();
                // 2026-10-24 本机实拉的匿名响应原样：{"data":{"result":2}}
                return infoJson("{\"data\":{\"result\":2}}");
            }
        };
        KuaishouProvider provider = new KuaishouProvider(api, () -> COOKIE);
        assertTrue(provider.prepare(ROOM).get(10, TimeUnit.SECONDS).isEmpty());
        assertEquals("照参照口径补取两次后收手", 2, infoCalls.get());
    }

    @Test
    public void prepareSkipsWhenOfflineOrApiBroken() throws Exception {
        KuaishouProvider offline = new KuaishouProvider(new KuaishouProvider.Api() {
            @Override
            public String fetchRoomPage(String roomId, String cookie) {
                return offlinePage();
            }

            @Override
            public JSONObject fetchWebsocketInfo(String liveStreamId, String roomId, String cookie) {
                return null;
            }
        }, () -> COOKIE);
        assertTrue(offline.prepare(ROOM).get(5, TimeUnit.SECONDS).isEmpty());
        KuaishouProvider broken = new KuaishouProvider(new KuaishouProvider.Api() {
            @Override
            public String fetchRoomPage(String roomId, String cookie) throws Exception {
                throw new IllegalStateException("boom");
            }

            @Override
            public JSONObject fetchWebsocketInfo(String liveStreamId, String roomId, String cookie) {
                return null;
            }
        }, () -> COOKIE);
        assertTrue(broken.prepare(ROOM).get(5, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    public void kwwDerivesFromKwfv1Cookie() {
        assertEquals("abc def###ssrc", KuaishouProvider.kwwFromCookie(COOKIE));
        assertEquals("", KuaishouProvider.kwwFromCookie("did=web_abc; userId=1"));
        assertEquals("", KuaishouProvider.kwwFromCookie(""));
        assertEquals("", KuaishouProvider.kwwFromCookie(null));
    }

    @Test
    public void parseWebsocketInfoShapes() throws Exception {
        assertEquals("", KuaishouProvider.parseWebsocketInfo(null).token());
        assertEquals("", KuaishouProvider.parseWebsocketInfo(infoJson("{}")).token());
        assertEquals("", KuaishouProvider.parseWebsocketInfo(infoJson("{\"data\":{\"result\":2}}")).token());
        KuaishouProvider.WebsocketInfo info = KuaishouProvider.parseWebsocketInfo(
                infoJson("{\"data\":{\"token\":\"t\",\"webSocketAddresses\":[\"wss://a.example/x\"]}}"));
        assertEquals("t", info.token());
        assertEquals(List.of("wss://a.example/x"), info.websocketUrls());
    }

    @Test
    public void heartbeatAndDecodeDelegateToCodec() throws Exception {
        KuaishouProvider provider = new KuaishouProvider(new KuaishouProvider.Api() {
            @Override
            public String fetchRoomPage(String roomId, String cookie) {
                return null;
            }

            @Override
            public JSONObject fetchWebsocketInfo(String liveStreamId, String roomId, String cookie) {
                return null;
            }
        }, () -> "");
        Map<Integer, List<Object>> socket = KuaishouPacketCodec.parse(provider.heartbeatFrame(ROOM));
        assertEquals(1L, socket.get(1).get(0));
        List<LiveDanmakuMessage> messages = new ArrayList<>();
        for (byte[] frame : KuaishouPacketCodecTest.readRecording()) {
            PlatformDanmakuProvider.DecodeResult result = provider.decode(frame, 9L, 1234L);
            messages.addAll(result.messages());
            assertNull(result.online());
        }
        assertEquals(4, messages.size());
        assertEquals("快手老铁双击666", messages.get(0).text());
        assertEquals(0xFFFF0000, messages.get(0).colorArgb());
        assertEquals(9L, messages.get(0).generation());
        assertEquals(1234L, messages.get(0).receivedAtMs());
    }
}
