package com.fongmi.android.tv.player.danmaku.live.huya;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * HuyaProvider 对拍：房间页提取用真房页面夹具（虎牙 998 房，2026-10-24 本机实拉）；
 * 预备步产物（地址/心跳/进房帧）与参照实现一致；解码走生产 decode 出口，
 * 与 HuyaPacketCodecTest 的实录期望同源（17 条弹幕＋2 次人气）。
 */
public class HuyaProviderTest {

    private static final RoomRef ROOM = new RoomRef(RoomRef.Platform.HUYA, "998");

    private static String roomPage() throws Exception {
        return new String(HuyaPacketCodecTest.readResource("/huya-room-998.html"), StandardCharsets.UTF_8);
    }

    @Test
    public void parseRoomPageExtractsJoinIdsAndLiveStatus() throws Exception {
        HuyaProvider.RoomPage page = HuyaProvider.parseRoomPage(roomPage());
        assertEquals(new HuyaProvider.RoomPage(229813522L, 294636272L, 294636272L, 2), page);
    }

    @Test
    public void parseRoomPageRejectsMissingOrBrokenPages() {
        assertNull(HuyaProvider.parseRoomPage(null));
        assertNull(HuyaProvider.parseRoomPage(""));
        assertNull(HuyaProvider.parseRoomPage("<html>no init here</html>"));
        assertNull(HuyaProvider.parseRoomPage("window.HNF_GLOBAL_INIT = {};</script>"));
    }

    @Test
    public void prepareBuildsReferenceTargetWhenLive() throws Exception {
        HuyaProvider provider = new HuyaProvider(roomId -> roomPage());
        Optional<PreparedTarget> target = provider.prepare(ROOM).get(5, TimeUnit.SECONDS);
        assertTrue(target.isPresent());
        assertEquals(HuyaProvider.WS_URL, target.get().wsUrl());
        assertEquals(60_000L, target.get().heartbeatMs());
        assertTrue(target.get().headers().isEmpty());
        assertEquals(1, target.get().onOpenFrames().size());
        assertEquals("00011d000017020db2ad1210012600360042118fcaf052118fcaf06c7c",
                hex(target.get().onOpenFrames().get(0)));
    }

    @Test
    public void prepareSkipsWhenNotLiveOrPageBroken() throws Exception {
        HuyaProvider offline = new HuyaProvider(roomId -> roomPage().replace("\"eLiveStatus\":2", "\"eLiveStatus\":1"));
        assertTrue(offline.prepare(ROOM).get(5, TimeUnit.SECONDS).isEmpty());
        HuyaProvider garbage = new HuyaProvider(roomId -> "<html>404</html>");
        assertTrue(garbage.prepare(ROOM).get(5, TimeUnit.SECONDS).isEmpty());
        HuyaProvider broken = new HuyaProvider(roomId -> {
            throw new IllegalStateException("boom");
        });
        assertTrue(broken.prepare(ROOM).get(5, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    public void heartbeatFrameIsReferenceBlob() {
        HuyaProvider provider = new HuyaProvider(roomId -> "");
        assertEquals("00141d000c2c36004c", hex(provider.heartbeatFrame(ROOM)));
    }

    @Test
    public void decodeMapsRecordingToMessagesAndOnline() throws Exception {
        HuyaProvider provider = new HuyaProvider(roomId -> "");
        List<LiveDanmakuMessage> messages = new ArrayList<>();
        List<Long> onlines = new ArrayList<>();
        for (byte[] frame : HuyaPacketCodecTest.readRecording()) {
            PlatformDanmakuProvider.DecodeResult result = provider.decode(frame, 7L, 1000L);
            messages.addAll(result.messages());
            if (result.online() != null) onlines.add(result.online());
        }
        assertEquals(17, messages.size());
        assertEquals(List.of(4990172L, 4989665L), onlines);
        LiveDanmakuMessage first = messages.get(0);
        assertEquals("族长 我饿了 我要吃你蘑菇", first.text());
        assertEquals(0xFFFFCC00, first.colorArgb());
        assertEquals(7L, first.generation());
        assertEquals(1000L, first.receivedAtMs());
        LiveDanmakuMessage blue = messages.get(2);
        assertEquals("是吗", blue.text());
        assertEquals(0xFF0000FF, blue.colorArgb());
    }

    @Test
    public void colorArgbFollowsReferenceRule() {
        assertEquals(0xFFFFFFFF, HuyaProvider.colorArgb(0));
        assertEquals(0xFFFFFFFF, HuyaProvider.colorArgb(-1));
        assertEquals(0xFF0000FF, HuyaProvider.colorArgb(255));
        assertEquals(0xFFFFCC00, HuyaProvider.colorArgb(16763904));
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) builder.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return builder.toString();
    }
}
