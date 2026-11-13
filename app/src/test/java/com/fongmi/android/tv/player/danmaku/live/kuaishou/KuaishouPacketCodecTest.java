package com.fongmi.android.tv.player.danmaku.live.kuaishou;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * KuaishouPacketCodec 对拍：快手弹幕须登录凭证才有真流量，本机无凭证录不到真帧，
 * 样本按 dart_simple_live 快手分支（kuaishou_danmaku.dart）的协议结构由独立 Python
 * 参照探针（手写 protobuf、与生产 Java 零共享）自造——明文/gzip 两路 FeedPush、
 * 隐藏/无正文/无昵称/带色评论俱全，附错误帧与未知帧；期望行由探针同批产出，
 * 与生产解码逐行比对。进房/心跳编码向量同样由探针产出（kuaishou-vectors.txt 口径），
 * 在此逐字节钉死。
 */
public class KuaishouPacketCodecTest {

    @Test
    public void decodeRecordingMatchesIndependentReference() throws Exception {
        StringBuilder actual = new StringBuilder();
        int count = 0;
        for (byte[] frame : readRecording()) {
            for (KuaishouPacketCodec.Chat chat : KuaishouPacketCodec.decode(frame).chats()) {
                actual.append(chat.nickname()).append('\t')
                        .append(chat.colorArgb() & 0xFFFFFFFFL).append('\t')
                        .append(chat.text()).append('\n');
                count++;
            }
        }
        actual.append("COUNT\t").append(count).append('\n');
        String expected = new String(readResource("/kuaishou-feedpush-expected.txt"), StandardCharsets.UTF_8);
        assertEquals(expected, actual.toString());
    }

    @Test
    public void encodeEnterRoomMatchesReferenceVector() {
        byte[] actual = KuaishouPacketCodec.encodeEnterRoom("tok_abc123", "1_B7BrugJfg", "exp-1", "att-2", "PAGEIDPAGEIDPAGE");
        assertArrayEquals(hex("08c8011a3d0a0a746f6b5f616263313233120b315f4237427275674a6667180020002a056578702d3132056174742d323a1050414745494450414745494450414745"), actual);
    }

    @Test
    public void encodeHeartbeatMatchesReferenceVector() {
        assertArrayEquals(hex("08011a07089b85f8ec9634"), KuaishouPacketCodec.encodeHeartbeat(1792840434331L));
    }

    @Test
    public void encodeEnterRoomSkipsEmptyStringsButKeepsZeroVarints() {
        byte[] frame = KuaishouPacketCodec.encodeEnterRoom("tok", "sid", "", "", "pg");
        Map<Integer, List<Object>> socket = KuaishouPacketCodec.parse(frame);
        assertEquals(200L, socket.get(1).get(0));
        byte[] payload = (byte[]) socket.get(3).get(0);
        Map<Integer, List<Object>> enter = KuaishouPacketCodec.parse(payload);
        assertEquals("tok", new String((byte[]) enter.get(1).get(0), StandardCharsets.UTF_8));
        assertEquals("sid", new String((byte[]) enter.get(2).get(0), StandardCharsets.UTF_8));
        assertEquals(0L, enter.get(3).get(0));
        assertEquals(0L, enter.get(4).get(0));
        assertTrue("expTag 为空应跳过", !enter.containsKey(5));
        assertTrue("attach 为空应跳过", !enter.containsKey(6));
        assertEquals("pg", new String((byte[]) enter.get(7).get(0), StandardCharsets.UTF_8));
    }

    @Test
    public void decodeDropsAesUnknownAndBrokenFrames() {
        // compressionType=3（AES）：参照实现同样不支持，整帧丢
        assertTrue(KuaishouPacketCodec.decode(hex("08b60210031a03414243")).chats().isEmpty());
        // 未知 payloadType 999
        assertTrue(KuaishouPacketCodec.decode(hex("08e7071a020801")).chats().isEmpty());
        // 错误帧 103
        assertTrue(KuaishouPacketCodec.decode(hex("08671a06080212026f6b")).chats().isEmpty());
        // 坏帧/空帧不抛、归零
        assertTrue(KuaishouPacketCodec.decode(null).chats().isEmpty());
        assertTrue(KuaishouPacketCodec.decode(new byte[0]).chats().isEmpty());
        assertTrue(KuaishouPacketCodec.decode(new byte[]{0x08}).chats().isEmpty());
        assertTrue(KuaishouPacketCodec.decode(new byte[]{0x1a, 0x05, 0x01}).chats().isEmpty());
    }

    @Test
    public void parseColorFollowsReference() {
        assertEquals(0xFFFF0000, KuaishouPacketCodec.parseColor("#FF0000"));
        assertEquals(0xFF00FF00, KuaishouPacketCodec.parseColor("00FF00"));
        assertEquals(0xFFFFFFFF, KuaishouPacketCodec.parseColor(""));
        assertEquals(0xFFFFFFFF, KuaishouPacketCodec.parseColor(null));
        assertEquals(0xFFFFFFFF, KuaishouPacketCodec.parseColor("#12345"));
        assertEquals(0xFFFFFFFF, KuaishouPacketCodec.parseColor("zzzzzz"));
    }

    static List<byte[]> readRecording() throws Exception {
        byte[] raw = readResource("/kuaishou-feedpush-recording.bin");
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

    static byte[] readResource(String name) throws Exception {
        try (InputStream in = KuaishouPacketCodecTest.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("missing resource " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    static byte[] hex(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return out;
    }
}
