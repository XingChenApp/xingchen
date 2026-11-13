package com.fongmi.android.tv.player.danmaku.live.huya;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * HuyaPacketCodec 对拍：进房包/心跳与参照实现（dart_simple_live）逐字节一致；
 * 解码用真房实录（虎牙 998 房，2026-10-24 本机直连录制）与独立参照解码
 * （Python 照 tars_dart 重写）逐条对齐，期望存 /huya-998-expected.txt。
 */
public class HuyaPacketCodecTest {

    @Test
    public void encodeJoinMatchesReferenceBytes() {
        assertEquals("00011d000017020db2ad1210012600360042118fcaf052118fcaf06c7c",
                hex(HuyaPacketCodec.encodeJoin(229813522, 294636272, 294636272)));
    }

    @Test
    public void heartbeatIsReferenceBlob() {
        assertEquals("00141d000c2c36004c", hex(HuyaPacketCodec.HEARTBEAT_FRAME));
    }

    @Test
    public void decodeRecordingMatchesReference() throws Exception {
        List<String> lines = new ArrayList<>();
        int chats = 0;
        for (byte[] frame : readRecording()) {
            HuyaPacketCodec.Decoded decoded = HuyaPacketCodec.decode(frame);
            for (HuyaPacketCodec.Chat chat : decoded.chats()) {
                lines.add(chat.nickname() + "\t" + chat.fontColor() + "\t" + chat.text());
                chats++;
            }
            if (decoded.online() != null) lines.add("ONLINE\t" + decoded.online());
        }
        lines.add("COUNT\t" + chats);
        String expected = new String(readResource("/huya-998-expected.txt"), StandardCharsets.UTF_8);
        assertEquals(expected.lines().toList(), lines);
    }

    @Test
    public void decodeRejectsGarbageAndTruncation() {
        assertTrue(HuyaPacketCodec.decode(null).chats().isEmpty());
        assertTrue(HuyaPacketCodec.decode(new byte[0]).chats().isEmpty());
        assertTrue(HuyaPacketCodec.decode(new byte[]{1, 2, 3}).chats().isEmpty());
        byte[] join = HuyaPacketCodec.encodeJoin(1, 2, 3);
        assertTrue(HuyaPacketCodec.decode(join).chats().isEmpty());
        byte[] truncated = new byte[]{0x00, 0x07, 0x1D, 0x00, 0x02, 0x10};
        assertTrue(HuyaPacketCodec.decode(truncated).chats().isEmpty());
    }

    static List<byte[]> readRecording() throws Exception {
        byte[] raw = readResource("/huya-998-recording.bin");
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
        assertEquals(raw.length, pos);
        return frames;
    }

    static byte[] readResource(String resource) throws Exception {
        InputStream stream = HuyaPacketCodecTest.class.getResourceAsStream(resource);
        assertNotNull(stream);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        stream.transferTo(out);
        return out.toByteArray();
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) builder.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return builder.toString();
    }
}
