package com.fongmi.android.tv.player.danmaku.live.douyu;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * SttCodec 对拍：编码字节与参照实现（dart_simple_live serializeDouyu，Python 独立复刻已验）逐字节一致；
 * 解码对多包帧、残缺尾包、STT 转义的口径与参照实现一致。
 */
public class SttCodecTest {

    private static String hex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte b : data) builder.append(String.format("%02x", b));
        return builder.toString();
    }

    @Test
    public void encodeMatchesReferenceBytes() {
        assertEquals("2500000025000000b102000074797065403d6c6f67696e7265712f726f6f6d6964403d393939392f00",
                hex(SttCodec.encode("type@=loginreq/roomid@=9999/")));
        assertEquals("1400000014000000b102000074797065403d6d726b6c2f00",
                hex(SttCodec.encode("type@=mrkl/")));
    }

    @Test
    public void decodeSplitsMultiPacketFrame() {
        byte[] first = SttCodec.encode("type@=chatmsg/txt@=hi/");
        byte[] second = SttCodec.encode("type@=chatmsg/txt@=yo/");
        byte[] frame = new byte[first.length + second.length];
        System.arraycopy(first, 0, frame, 0, first.length);
        System.arraycopy(second, 0, frame, first.length, second.length);
        assertEquals(List.of("type@=chatmsg/txt@=hi/", "type@=chatmsg/txt@=yo/"), SttCodec.decodePackets(frame));
    }

    @Test
    public void decodeIgnoresTruncatedTail() {
        byte[] full = SttCodec.encode("type@=mrkl/");
        byte[] frame = new byte[full.length + 5];
        System.arraycopy(full, 0, frame, 0, full.length);
        assertEquals(List.of("type@=mrkl/"), SttCodec.decodePackets(frame));
        assertTrue(SttCodec.decodePackets(new byte[0]).isEmpty());
        assertTrue(SttCodec.decodePackets(null).isEmpty());
        assertTrue(SttCodec.decodePackets(new byte[]{1, 2, 3}).isEmpty());
    }

    @Test
    public void parseFieldsReadsFlatFields() {
        Map<String, String> fields = SttCodec.parseFields("type@=chatmsg/nn@=abc/txt@=hello/col@=2/dms@=4/");
        assertEquals("chatmsg", fields.get("type"));
        assertEquals("abc", fields.get("nn"));
        assertEquals("hello", fields.get("txt"));
        assertEquals("2", fields.get("col"));
        assertEquals("4", fields.get("dms"));
    }

    @Test
    public void parseFieldsUnescapesValuesAfterSplit() {
        // 值里的 @S/@A 是转义形态：切字段必须在反转义之前，否则 @S 变回 / 会切错字段
        Map<String, String> fields = SttCodec.parseFields("type@=chatmsg/txt@=a@Sb@Ac/dms@=1/");
        assertEquals("a/b@c", fields.get("txt"));
        assertEquals("1", fields.get("dms"));
    }

    @Test
    public void unescapeOrderMatchesReference() {
        assertEquals("a/b", SttCodec.unescape("a@Sb"));
        assertEquals("a@b", SttCodec.unescape("a@Ab"));
        // 先 @S 后 @A（参照 unscapeSlashAt 的替换顺序）："@AS" 里没有 @S，结果是把 @A 换成 @
        assertEquals("@S", SttCodec.unescape("@AS"));
        assertEquals("", SttCodec.unescape(""));
    }

    @Test
    public void parseFieldsSkipsBrokenFields() {
        Map<String, String> fields = SttCodec.parseFields("type@=chatmsg/brokenfield/txt@=ok//dms@=4/");
        assertEquals("chatmsg", fields.get("type"));
        assertEquals("ok", fields.get("txt"));
        assertEquals("4", fields.get("dms"));
    }
}
