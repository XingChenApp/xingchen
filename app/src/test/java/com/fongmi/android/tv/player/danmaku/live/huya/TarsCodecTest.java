package com.fongmi.android.tv.player.danmaku.live.huya;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TarsCodec 对拍：写侧字节形态与 tars_dart 的 TarsOutputStream 逐宽一致；
 * 读侧 skipToTag 前向扫能跨过 map/list/简单列表/嵌套结构体取到目标字段，
 * 截断输入不越界（回默认或抛 TarsException 由上层吞）。
 */
public class TarsCodecTest {

    private static byte[] hex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    @Test
    public void writeIntPicksShortestWidth() {
        assertArrayEquals(hex("0c"), new TarsCodec.Writer().writeInt(0, 0).toByteArray());
        assertArrayEquals(hex("0001"), new TarsCodec.Writer().writeInt(1, 0).toByteArray());
        assertArrayEquals(hex("007f"), new TarsCodec.Writer().writeInt(127, 0).toByteArray());
        assertArrayEquals(hex("00ff"), new TarsCodec.Writer().writeInt(-1, 0).toByteArray());
        assertArrayEquals(hex("21012c"), new TarsCodec.Writer().writeInt(300, 2).toByteArray());
        assertArrayEquals(hex("42118fcaf0"), new TarsCodec.Writer().writeInt(294636272, 4).toByteArray());
        assertArrayEquals(hex("13000000012a05f200"), new TarsCodec.Writer().writeInt(5000000000L, 1).toByteArray());
    }

    @Test
    public void writeStringAndBytesForms() {
        assertArrayEquals(hex("2600"), new TarsCodec.Writer().writeString("", 2).toByteArray());
        assertArrayEquals(hex("16026162"), new TarsCodec.Writer().writeString("ab", 1).toByteArray());
        byte[] longString = new TarsCodec.Writer().writeString("x".repeat(300), 0).toByteArray();
        assertEquals((byte) 0x07, longString[0]);
        assertEquals(300, (longString[1] & 0xFF) << 24 | (longString[2] & 0xFF) << 16 | (longString[3] & 0xFF) << 8 | (longString[4] & 0xFF));
        assertArrayEquals(hex("1d000003010203"), new TarsCodec.Writer().writeBytes(new byte[]{1, 2, 3}, 1).toByteArray());
    }

    @Test
    public void readerNavigatesStructFields() {
        byte[] data = new TarsCodec.Writer()
                .writeHead(TarsCodec.STRUCT_BEGIN, 0)
                .writeInt(123456, 0)
                .writeString("昵称", 2)
                .writeInt(7, 3)
                .writeHead(TarsCodec.STRUCT_END, 0)
                .writeString("正文", 3)
                .writeHead(TarsCodec.STRUCT_BEGIN, 6)
                .writeInt(255, 0)
                .writeHead(TarsCodec.STRUCT_END, 0)
                .toByteArray();
        TarsCodec.Reader reader = new TarsCodec.Reader(data);
        assertTrue(reader.enterStruct(0));
        assertEquals(123456, reader.readInt(0));
        assertEquals("昵称", reader.readString(2));
        reader.exitStruct();
        assertEquals("正文", reader.readString(3));
        assertTrue(reader.enterStruct(6));
        assertEquals(255, reader.readInt(0));
        reader.exitStruct();
    }

    @Test
    public void readerSkipsMapListAndSimpleList() {
        TarsCodec.Writer writer = new TarsCodec.Writer();
        writer.writeHead(TarsCodec.MAP, 1).writeInt(1, 0).writeString("k", 0).writeInt(9, 1);
        writer.writeHead(TarsCodec.LIST, 2).writeInt(3, 0).writeInt(1, 0).writeInt(2, 0).writeInt(3, 0);
        writer.writeBytes(new byte[]{9, 8, 7}, 4);
        writer.writeInt(42, 5);
        TarsCodec.Reader reader = new TarsCodec.Reader(writer.toByteArray());
        assertEquals(42, reader.readInt(5));
    }

    @Test
    public void readerDefaultsAndTruncationSafety() {
        TarsCodec.Reader empty = new TarsCodec.Reader(new byte[0]);
        assertEquals(0, empty.readInt(0));
        assertEquals("", empty.readString(1));
        assertEquals(0, empty.readBytes(2).length);
        assertFalse(empty.enterStruct(0));
        TarsCodec.Reader nullData = new TarsCodec.Reader(null);
        assertEquals(0, nullData.readInt(3));
        // 只有头没有身子的截断帧：skipToTag 吞掉异常回 false，不许越界
        TarsCodec.Reader truncated = new TarsCodec.Reader(new byte[]{0x02, 0x11});
        assertEquals(0, truncated.readInt(5));
    }

    @Test
    public void roundTripLongAndUtf8() {
        byte[] data = new TarsCodec.Writer()
                .writeInt(1909591507, 0)
                .writeString("虎牙弹幕🎮", 1)
                .writeBytes(new byte[]{0, 1, 2, (byte) 0xFF}, 2)
                .toByteArray();
        TarsCodec.Reader reader = new TarsCodec.Reader(data);
        assertEquals(1909591507, reader.readInt(0));
        assertEquals("虎牙弹幕🎮", reader.readString(1));
        assertArrayEquals(new byte[]{0, 1, 2, (byte) 0xFF}, reader.readBytes(2));
    }
}
