package com.fongmi.android.tv.player.danmaku.live.douyin;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * DouyinPacketCodec 对拍：录制是 2026-10-24 本机从在播房 974709925783
 * （2026DNF全国格斗大赛秋季赛）按生产口径（webmssdk 签名连入、hb 心跳、needAck 回执）
 * 录的 35 秒原始帧；期望由独立 Python 参照探针（手写 protobuf 解码）同录制产出，
 * 两边逐行比对（昵称/白色/文本/人气/顺序）。
 */
public class DouyinPacketCodecTest {

    @Test
    public void decodeRecordingMatchesIndependentReference() throws Exception {
        StringBuilder actual = new StringBuilder();
        int count = 0;
        for (byte[] frame : readRecording()) {
            DouyinPacketCodec.Decoded decoded = DouyinPacketCodec.decode(frame);
            for (DouyinPacketCodec.Event event : decoded.events()) {
                if (event instanceof DouyinPacketCodec.Event.ChatEvent chatEvent) {
                    DouyinPacketCodec.Chat chat = chatEvent.chat();
                    actual.append(chat.nickname()).append('\t')
                            .append(chat.colorArgb() & 0xFFFFFFFFL).append('\t')
                            .append(chat.text()).append('\n');
                    count++;
                } else if (event instanceof DouyinPacketCodec.Event.OnlineEvent onlineEvent) {
                    actual.append("ONLINE\t").append(onlineEvent.totalUser()).append('\n');
                }
            }
        }
        actual.append("COUNT\t").append(count).append('\n');
        String expected = new String(readResource("/douyin-974709925783-expected.txt"), StandardCharsets.UTF_8);
        assertEquals(expected, actual.toString());
        assertEquals(29, count);
    }

    @Test
    public void needAckFramesProduceWellFormedAck() throws Exception {
        int acks = 0;
        for (byte[] frame : readRecording()) {
            DouyinPacketCodec.Decoded decoded = DouyinPacketCodec.decode(frame);
            if (decoded.ackFrame() == null) continue;
            acks++;
            Map<Integer, List<Object>> fields = DouyinPacketCodec.parse(decoded.ackFrame());
            assertEquals("ack", stringField(fields, 7));
            assertTrue(fields.containsKey(2));
            assertTrue(fields.containsKey(8));
        }
        assertTrue("录制窗内应有 needAck 回执帧", acks > 0);
    }

    @Test
    public void heartbeatFrameIsPayloadTypeHb() {
        Map<Integer, List<Object>> fields = DouyinPacketCodec.parse(DouyinPacketCodec.encodeHeartbeat());
        assertEquals("hb", stringField(fields, 7));
        assertEquals(1, fields.size());
    }

    @Test
    public void garbageFramesDecodeToEmpty() {
        assertEquals(DouyinPacketCodec.Decoded.EMPTY, DouyinPacketCodec.decode(null));
        assertEquals(DouyinPacketCodec.Decoded.EMPTY, DouyinPacketCodec.decode(new byte[0]));
        assertEquals(DouyinPacketCodec.Decoded.EMPTY, DouyinPacketCodec.decode(new byte[]{1, 2, 3}));
        assertEquals(DouyinPacketCodec.Decoded.EMPTY, DouyinPacketCodec.decode("not a push frame".getBytes(StandardCharsets.UTF_8)));
    }

    static List<byte[]> readRecording() throws Exception {
        byte[] raw = readResource("/douyin-974709925783-recording.bin");
        List<byte[]> frames = new ArrayList<>();
        int pos = 0;
        while (pos + 4 <= raw.length) {
            int length = (raw[pos] & 0xFF) << 24 | (raw[pos + 1] & 0xFF) << 16 | (raw[pos + 2] & 0xFF) << 8 | (raw[pos + 3] & 0xFF);
            pos += 4;
            byte[] frame = new byte[length];
            System.arraycopy(raw, pos, frame, 0, length);
            pos += length;
            frames.add(frame);
        }
        return frames;
    }

    static byte[] readResource(String resource) throws Exception {
        try (InputStream stream = DouyinPacketCodecTest.class.getResourceAsStream(resource)) {
            assertNotNull("missing test resource " + resource, stream);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            stream.transferTo(out);
            return out.toByteArray();
        }
    }

    @Test
    public void rtfSingleColoredBodySpanYieldsColor() throws Exception {
        byte[] rtf = rtfBytes(spanBytes(11, "#8CE7FF", null), spanBytes(1, "#FF6482", "正文"));
        assertEquals(0xFFFF6482, DouyinPacketCodec.rtfBodyColor(rtf));
        DouyinPacketCodec.Decoded decoded = DouyinPacketCodec.decode(chatFrame("主播好", rtf));
        assertEquals(1, decoded.events().size());
        DouyinPacketCodec.Chat chat = ((DouyinPacketCodec.Event.ChatEvent) decoded.events().get(0)).chat();
        assertEquals("主播好", chat.text());
        assertEquals(0xFFFF6482, chat.colorArgb());
    }

    @Test
    public void rtfMentionBluePlusWhiteBodyStaysWhite() throws Exception {
        byte[] rtf = rtfBytes(spanBytes(11, "#8CE7FF", null), spanBytes(1, "#FFFFFF", "正文"));
        assertEquals(0xFFFFFFFF, DouyinPacketCodec.rtfBodyColor(rtf));
    }

    @Test
    public void rtfConflictingBodyColorsFallBackToWhite() throws Exception {
        byte[] rtf = rtfBytes(spanBytes(1, "#FF0000", "前"), spanBytes(1, "#00FF00", "后"));
        assertEquals(0xFFFFFFFF, DouyinPacketCodec.rtfBodyColor(rtf));
    }

    @Test
    public void rtfArgbEightDigitColorIsHonored() throws Exception {
        byte[] rtf = rtfBytes(spanBytes(1, "#806241FF", "正文"));
        assertEquals(0x806241FF, DouyinPacketCodec.rtfBodyColor(rtf));
    }

    @Test
    public void missingOrGarbageRtfStaysWhiteAndKeepsMessage() throws Exception {
        assertEquals(0xFFFFFFFF, DouyinPacketCodec.rtfBodyColor(null));
        assertEquals(0xFFFFFFFF, DouyinPacketCodec.rtfBodyColor(new byte[]{1, 2, 3}));
        DouyinPacketCodec.Decoded decoded = DouyinPacketCodec.decode(chatFrame("还在", new byte[]{9, 9, 9}));
        assertEquals(1, decoded.events().size());
        DouyinPacketCodec.Chat chat = ((DouyinPacketCodec.Event.ChatEvent) decoded.events().get(0)).chat();
        assertEquals("还在", chat.text());
        assertEquals(0xFFFFFFFF, chat.colorArgb());
    }

    @Test
    public void emojiAndPlaceholderTextSurviveDecode() throws Exception {
        DouyinPacketCodec.Decoded decoded = DouyinPacketCodec.decode(chatFrame("🌈彩虹[捂脸]🐢", null));
        assertEquals(1, decoded.events().size());
        DouyinPacketCodec.Chat chat = ((DouyinPacketCodec.Event.ChatEvent) decoded.events().get(0)).chat();
        assertEquals("🌈彩虹[捂脸]🐢", chat.text());
    }

    /** 造一条完整的 PushFrame（gzip Response 内含一条 WebcastChatMessage），rtf 为空则不带 field 41。 */
    private static byte[] chatFrame(String content, byte[] rtf) throws Exception {
        ByteArrayOutputStream chat = new ByteArrayOutputStream();
        writeBytesField(chat, 2, stringMessage(3, "昵称"));
        writeBytesField(chat, 3, content.getBytes(StandardCharsets.UTF_8));
        if (rtf != null) writeBytesField(chat, 41, rtf);
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        writeBytesField(message, 1, "WebcastChatMessage".getBytes(StandardCharsets.UTF_8));
        writeBytesField(message, 2, chat.toByteArray());
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        writeBytesField(response, 1, message.toByteArray());
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(gz)) {
            gzip.write(response.toByteArray());
        }
        ByteArrayOutputStream push = new ByteArrayOutputStream();
        writeBytesField(push, 7, "msg".getBytes(StandardCharsets.UTF_8));
        writeBytesField(push, 8, gz.toByteArray());
        return push.toByteArray();
    }

    private static byte[] stringMessage(int field, String value) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeBytesField(out, field, value.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static byte[] rtfBytes(byte[]... spans) throws Exception {
        ByteArrayOutputStream rtf = new ByteArrayOutputStream();
        writeBytesField(rtf, 1, "chat_rtf_content".getBytes(StandardCharsets.UTF_8));
        for (byte[] span : spans) writeBytesField(rtf, 4, span);
        return rtf.toByteArray();
    }

    private static byte[] spanBytes(int type, String color, String text) throws Exception {
        ByteArrayOutputStream style = new ByteArrayOutputStream();
        if (color != null) writeBytesField(style, 1, color.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream span = new ByteArrayOutputStream();
        writeVarintField(span, 1, type);
        writeBytesField(span, 2, style.toByteArray());
        if (text != null) writeBytesField(span, 11, text.getBytes(StandardCharsets.UTF_8));
        return span.toByteArray();
    }

    private static void writeVarintField(ByteArrayOutputStream out, int field, long value) {
        writeVarint(out, (long) field << 3);
        writeVarint(out, value);
    }

    private static void writeBytesField(ByteArrayOutputStream out, int field, byte[] value) {
        writeVarint(out, ((long) field << 3) | 2);
        writeVarint(out, value.length);
        out.write(value, 0, value.length);
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        long rest = value;
        while (true) {
            int bits = (int) (rest & 0x7F);
            rest >>>= 7;
            if (rest == 0) {
                out.write(bits);
                return;
            }
            out.write(bits | 0x80);
        }
    }

    private static String stringField(Map<Integer, List<Object>> fields, int field) {
        List<Object> values = fields.get(field);
        if (values == null || values.isEmpty() || !(values.get(0) instanceof byte[] bytes)) return null;
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
