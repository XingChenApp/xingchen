package com.fongmi.android.tv.player.danmaku.live.douyu;

import com.fongmi.android.tv.player.danmaku.LiveDanmakuMessage;
import com.fongmi.android.tv.player.danmaku.live.PlatformDanmakuProvider;
import com.fongmi.android.tv.player.danmaku.live.PreparedTarget;
import com.fongmi.android.tv.player.danmaku.live.RoomRef;

import org.junit.Test;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * DouyuProvider 对拍：预备步产物（进房帧/心跳/地址）与参照实现一致；
 * 解码用真机实录样本（斗鱼房间 9999，2026-10-24 本机直连录制）与独立参照解码逐条对齐。
 */
public class DouyuProviderTest {

    private static final RoomRef ROOM = new RoomRef(RoomRef.Platform.DOUYU, "9999");

    // 实录前缀：loginres+pingreq+uenter+3 条 chatmsg 的完整包流（原始字节 Base64）
    private static final String RECORDED_B64 = "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";

    @Test
    public void prepareBuildsReferenceTargetWhenLive() throws Exception {
        DouyuProvider provider = new DouyuProvider(rid -> true);
        Optional<PreparedTarget> target = provider.prepare(ROOM).get(5, TimeUnit.SECONDS);
        assertTrue(target.isPresent());
        assertEquals(DouyuProvider.WS_URL, target.get().wsUrl());
        assertEquals(45_000L, target.get().heartbeatMs());
        assertTrue(target.get().headers().isEmpty());
        List<byte[]> onOpen = target.get().onOpenFrames();
        assertEquals(2, onOpen.size());
        assertEquals(List.of("type@=loginreq/roomid@=9999/"), SttCodec.decodePackets(onOpen.get(0)));
        assertEquals(List.of("type@=joingroup/rid@=9999/gid@=-9999/"), SttCodec.decodePackets(onOpen.get(1)));
    }

    @Test
    public void prepareSkipsWhenRoomNotLiveOrCheckFails() throws Exception {
        DouyuProvider offline = new DouyuProvider(rid -> false);
        assertTrue(offline.prepare(ROOM).get(5, TimeUnit.SECONDS).isEmpty());
        DouyuProvider broken = new DouyuProvider(rid -> {
            throw new IllegalStateException("boom");
        });
        assertTrue(broken.prepare(ROOM).get(5, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    public void heartbeatFrameIsMrkl() {
        DouyuProvider provider = new DouyuProvider(rid -> true);
        assertEquals(List.of("type@=mrkl/"), SttCodec.decodePackets(provider.heartbeatFrame(ROOM)));
    }

    @Test
    public void decodeRecordedSampleMatchesReference() {
        DouyuProvider provider = new DouyuProvider(rid -> true);
        byte[] frame = Base64.getDecoder().decode(RECORDED_B64);
        PlatformDanmakuProvider.DecodeResult result = provider.decode(frame, 7L, 1234L);
        assertNull(result.online());
        assertEquals(3, result.messages().size());
        assertMessage(result.messages().get(0), "字一色啊", 0xFFFF69B4);
        assertMessage(result.messages().get(1), "软柿子真多啊，人家立直了，这个红5万能不打吗", 0xFF1E87F0);
        assertMessage(result.messages().get(2), "恶龙没有烟跟槟榔，战力下降太多", 0xFFFF0000);
        for (LiveDanmakuMessage message : result.messages()) {
            assertEquals(7L, message.generation());
            assertEquals(1234L, message.receivedAtMs());
            assertEquals(LiveDanmakuMessage.Type.NORMAL, message.type());
        }
    }

    @Test
    public void decodeFiltersNonChatAndShadowDanmaku() {
        DouyuProvider provider = new DouyuProvider(rid -> true);
        byte[] uenter = SttCodec.encode("type@=uenter/rid@=9999/nn@=someone/");
        assertTrue(provider.decode(uenter, 1L, 0L).messages().isEmpty());
        // 缺 dms 的 chatmsg 是阴间弹幕，照参照实现丢弃
        byte[] shadow = SttCodec.encode("type@=chatmsg/nn@=x/txt@=hidden/col@=1/");
        assertTrue(provider.decode(shadow, 1L, 0L).messages().isEmpty());
        // 有 dms 才放行
        byte[] visible = SttCodec.encode("type@=chatmsg/nn@=x/txt@=shown/col@=1/dms@=4/");
        List<LiveDanmakuMessage> messages = provider.decode(visible, 1L, 0L).messages();
        assertEquals(1, messages.size());
        assertEquals("shown", messages.get(0).text());
    }

    @Test
    public void parseChatKeepsNickname() {
        DouyuProvider.ChatMessage chat = DouyuProvider.parseChat(SttCodec.parseFields("type@=chatmsg/nn@=可乐炖鸡/txt@=字一色啊/col@=6/dms@=4/"));
        assertEquals("可乐炖鸡", chat.nickname());
        assertEquals("字一色啊", chat.text());
        assertEquals(0xFFFF69B4, chat.colorArgb());
    }

    @Test
    public void colorTableMatchesReference() {
        assertEquals(0xFFFF0000, DouyuProvider.colorArgb("1"));
        assertEquals(0xFF1E87F0, DouyuProvider.colorArgb("2"));
        assertEquals(0xFF7AC84B, DouyuProvider.colorArgb("3"));
        assertEquals(0xFFFF7F00, DouyuProvider.colorArgb("4"));
        assertEquals(0xFF9B39F4, DouyuProvider.colorArgb("5"));
        assertEquals(0xFFFF69B4, DouyuProvider.colorArgb("6"));
        assertEquals(0xFFFFFFFF, DouyuProvider.colorArgb("0"));
        assertEquals(0xFFFFFFFF, DouyuProvider.colorArgb("99"));
        assertEquals(0xFFFFFFFF, DouyuProvider.colorArgb("abc"));
        assertEquals(0xFFFFFFFF, DouyuProvider.colorArgb(null));
    }

    private static void assertMessage(LiveDanmakuMessage message, String text, int colorArgb) {
        assertEquals(text, message.text());
        assertEquals(colorArgb, message.colorArgb());
    }
}
