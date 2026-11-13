package com.fongmi.android.tv.player.danmaku.live.douyin;

import com.fongmi.android.tv.player.danmaku.LiveDanmakuMessage;
import com.fongmi.android.tv.player.danmaku.live.PlatformDanmakuProvider;
import com.fongmi.android.tv.player.danmaku.live.PreparedTarget;
import com.fongmi.android.tv.player.danmaku.live.RoomRef;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * DouyinProvider 对拍：房间页夹具是 2026-10-24 本机实拉的在播房 974709925783 与
 * 未开播房 369324308707 页面原文片段（原始转义字节）；签名向量经 Node 跑同一份
 * webmssdk 实测；解码走生产 decode 出口，与 DouyinPacketCodecTest 同源录制。
 */
public class DouyinProviderTest {

    private static final RoomRef ROOM = new RoomRef(RoomRef.Platform.DOUYIN, "974709925783");
    private static final String LIVE_ROOM_ID = "7700171610636274470";
    private static final String LIVE_UUID = "7700186095443985935";

    private static String livePage() throws Exception {
        return new String(DouyinPacketCodecTest.readResource("/douyin-room-974709925783.html"), StandardCharsets.UTF_8);
    }

    private static String offlinePage() throws Exception {
        return new String(DouyinPacketCodecTest.readResource("/douyin-room-offline.html"), StandardCharsets.UTF_8);
    }

    @Test
    public void parseRoomPageExtractsRealIdsAndLiveStatus() throws Exception {
        DouyinProvider.RoomPage page = DouyinProvider.parseRoomPage(livePage());
        assertEquals(new DouyinProvider.RoomPage(LIVE_ROOM_ID, 2, LIVE_UUID), page);
        DouyinProvider.RoomPage offline = DouyinProvider.parseRoomPage(offlinePage());
        assertEquals(4, offline.status());
        assertEquals("7076284313080843019", offline.roomId());
    }

    @Test
    public void parseRoomPageRejectsBrokenPages() {
        assertNull(DouyinProvider.parseRoomPage(null));
        assertNull(DouyinProvider.parseRoomPage(""));
        assertNull(DouyinProvider.parseRoomPage("<html>no room state</html>"));
    }

    @Test
    public void msStubMatchesReferenceVector() throws Exception {
        // 向量：Node 跑参照实现 getMsStub 同参数（room_id=123、user_unique_id=456）的实测值
        assertEquals("cc7c67b1b2ac2282e581f3c09fd079e8", DouyinMssdkSigner.msStub("123", "456"));
    }

    @Test
    public void prepareBuildsSignedTargetWhenLive() throws Exception {
        DouyinProvider provider = new DouyinProvider(
                webRid -> new DouyinProvider.Api.RoomPageData("ttwid=abc%7C123", livePage()),
                (roomId, userUniqueId) -> {
                    assertEquals(LIVE_ROOM_ID, roomId);
                    assertEquals(LIVE_UUID, userUniqueId);
                    return "SIGnature+/";
                });
        Optional<PreparedTarget> target = provider.prepare(ROOM).get(5, TimeUnit.SECONDS);
        assertTrue(target.isPresent());
        String url = target.get().wsUrl();
        assertTrue(url.startsWith(DouyinProvider.WS_URL + "?"));
        assertTrue(url.contains("room_id=" + LIVE_ROOM_ID));
        assertTrue(url.contains("user_unique_id=" + LIVE_UUID));
        assertTrue(url.contains("webcast_sdk_version=1.3.0"));
        assertTrue(url.contains("&signature=SIGnature%2B%2F"));
        assertEquals(10_000L, target.get().heartbeatMs());
        assertEquals("ttwid=abc%7C123", target.get().headers().get("Cookie"));
        assertEquals("https://live.douyin.com", target.get().headers().get("Origin"));
        assertEquals(DouyinProvider.USER_AGENT, target.get().headers().get("User-Agent"));
        assertEquals(1, target.get().onOpenFrames().size());
        assertArrayEquals(DouyinPacketCodec.encodeHeartbeat(), target.get().onOpenFrames().get(0));
    }

    @Test
    public void prepareSkipsWhenNotLiveBrokenOrSignFails() throws Exception {
        DouyinProvider offline = new DouyinProvider(
                webRid -> new DouyinProvider.Api.RoomPageData("", offlinePage()),
                (roomId, userUniqueId) -> "sig");
        assertTrue(offline.prepare(ROOM).get(5, TimeUnit.SECONDS).isEmpty());
        DouyinProvider garbage = new DouyinProvider(
                webRid -> new DouyinProvider.Api.RoomPageData("", "<html>404</html>"),
                (roomId, userUniqueId) -> "sig");
        assertTrue(garbage.prepare(ROOM).get(5, TimeUnit.SECONDS).isEmpty());
        DouyinProvider broken = new DouyinProvider(webRid -> {
            throw new IllegalStateException("boom");
        }, (roomId, userUniqueId) -> "sig");
        assertTrue(broken.prepare(ROOM).get(5, TimeUnit.SECONDS).isEmpty());
        DouyinProvider signFails = new DouyinProvider(
                webRid -> new DouyinProvider.Api.RoomPageData("", livePage()),
                (roomId, userUniqueId) -> {
                    throw new IllegalStateException("quickjs boom");
                });
        assertTrue(signFails.prepare(ROOM).get(5, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    public void heartbeatFrameIsCodecHeartbeat() {
        DouyinProvider provider = new DouyinProvider(webRid -> null, (roomId, userUniqueId) -> "sig");
        assertArrayEquals(DouyinPacketCodec.encodeHeartbeat(), provider.heartbeatFrame(ROOM));
    }

    @Test
    public void decodeMapsRecordingToMessagesOnlineAndAckReplies() throws Exception {
        DouyinProvider provider = new DouyinProvider(webRid -> null, (roomId, userUniqueId) -> "sig");
        List<LiveDanmakuMessage> messages = new ArrayList<>();
        List<Long> onlines = new ArrayList<>();
        List<Long> expectedOnlines = new ArrayList<>();
        int replies = 0;
        for (byte[] frame : DouyinPacketCodecTest.readRecording()) {
            // provider 每帧只上报最后一个 online：期望按 codec 的帧内原序推导（codec 本体已由逐行对拍钉死）
            Long frameOnline = null;
            for (DouyinPacketCodec.Event event : DouyinPacketCodec.decode(frame).events()) {
                if (event instanceof DouyinPacketCodec.Event.OnlineEvent onlineEvent) frameOnline = onlineEvent.totalUser();
            }
            if (frameOnline != null) expectedOnlines.add(frameOnline);
            PlatformDanmakuProvider.DecodeResult result = provider.decode(frame, 7L, 1000L);
            messages.addAll(result.messages());
            if (result.online() != null) onlines.add(result.online());
            replies += result.replyFrames().size();
        }
        assertEquals(29, messages.size());
        assertEquals(expectedOnlines, onlines);
        assertTrue("录制窗内应产生 needAck 回执", replies > 0);
        LiveDanmakuMessage first = messages.get(0);
        assertEquals("@抖音白手夢偿君 温柔个人赛五战全胜早晋级了", first.text());
        assertEquals(0xFFFFFFFF, first.colorArgb());
        assertEquals(7L, first.generation());
        assertEquals(1000L, first.receivedAtMs());
    }
}
