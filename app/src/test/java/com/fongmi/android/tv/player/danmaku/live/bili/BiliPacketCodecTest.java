package com.fongmi.android.tv.player.danmaku.live.bili;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * BiliPacketCodec 对拍：编码向量由独立 Python 实现离线生成（大端头＋JSON 进房体），
 * 解码向量含合成的 op3 人气包、op5 zlib 多包捆绑与 op5 明文包。
 */
public class BiliPacketCodecTest {

    private static final String AUTH_HEX = "000000780010000000000007000000017b22756964223a302c22726f6f6d6964223a363635303032392c2270726f746f766572223a322c226275766964223a2254455354425556494433222c22706c6174666f726d223a22776562222c2274797065223a322c226b6579223a2254455354544f4b454e227d";
    private static final String HEARTBEAT_HEX = "00000010001000000000000200000001";
    private static final String OP3_HEX = "000000140010000100000003000000010012d687";
    private static final String BUNDLE_HEX = "0000016d001000020000000500000001789c636060d8cd20c00002ac40cc58ad949c9ba264a5e4e2e8e71b1aef1becaea4a394999796af64151d6da063a86364aa6368666e68686601e4991b40810e082a252625a728819979a539393ad5b5101aca89d5517abe6bd18bce4d4ff7ec7cba73eab3addd2fd64f55d28936343030d45182709f4f59f1ac63fbf3299b40c628298110d0b8589de85890690648e619c4d6029dbb0dafd3ad4cac0cac8cc0d0c0ca00d323483e3084fb2015bf0f66ee85fb00e27623a020d8d54f76ce24d2d5f3b0b93a3834c03528ded9c33124ded73538d8d1dd1568504a6249a29255b5526e6a7171627a2a50d9cbb649cf67af7b3e75e68b150db020542a28ca04badbca18687b526272767a517e695e4a7c727e4e7e11508bb29b1bc88b4075a5c5a945f19040a8562acd4bcc0599f8a2a1f1d9dcfd4f76cc54aaad05b9ce119beb3cfd425c831c9d43e2c3fd835c901c063764fbfa27bb76014d0000a98ebda4";
    private static final String PLAIN_HEX = "000000bb0010000000000005000000017b22636d64223a2244414e4d555f4d5347222c22696e666f223a5b5b302c312c32352c31363731313638302c313730303030303030302c302c302c2261626364222c302c302c6e756c6c2c7b7d2c6e756c6c2c6e756c6c2c7b7d5d2c22e7baa2e889b2e5bcb9e5b995e6b58be8af95222c5b313030312c22e6b58be8af95e794a8e688b7e794b2222c302c22222c22222c302c305d2c5b5d2c7b7d2c302c6e756c6c2c6e756c6c2c305d7d";

    @Test
    public void encodeAuthMatchesReferenceBytes() {
        byte[] auth = BiliPacketCodec.encodeAuth(6650029L, "TESTBUVID3", "TESTTOKEN");
        assertEquals(AUTH_HEX, HexFormat.of().formatHex(auth));
    }

    @Test
    public void encodeHeartbeatMatchesReferenceBytes() {
        assertEquals(HEARTBEAT_HEX, HexFormat.of().formatHex(BiliPacketCodec.encodeHeartbeat()));
    }

    @Test
    public void decodeHeartbeatReplyPacket() {
        List<BiliPacketCodec.RawPacket> packets = BiliPacketCodec.decode(HexFormat.of().parseHex(OP3_HEX));
        assertEquals(1, packets.size());
        assertEquals(BiliPacketCodec.OP_HEARTBEAT_REPLY, packets.get(0).op());
        assertEquals(1234567, BiliPacketCodec.getInt32Be(packets.get(0).body(), 0));
    }

    @Test
    public void decodeZlibBundleExpandsInnerPackets() {
        List<BiliPacketCodec.RawPacket> packets = BiliPacketCodec.decode(HexFormat.of().parseHex(BUNDLE_HEX));
        assertEquals(4, packets.size());
        for (BiliPacketCodec.RawPacket packet : packets) {
            assertEquals(BiliPacketCodec.OP_MESSAGE, packet.op());
            assertTrue(new String(packet.body(), StandardCharsets.UTF_8).contains("\"cmd\""));
        }
    }

    @Test
    public void decodePlainMessagePacket() {
        List<BiliPacketCodec.RawPacket> packets = BiliPacketCodec.decode(HexFormat.of().parseHex(PLAIN_HEX));
        assertEquals(1, packets.size());
        assertEquals(BiliPacketCodec.OP_MESSAGE, packets.get(0).op());
        assertTrue(new String(packets.get(0).body(), StandardCharsets.UTF_8).contains("DANMU_MSG"));
    }

    @Test
    public void decodeConcatenatedPacketsAndToleratesGarbage() {
        byte[] joined = HexFormat.of().parseHex(OP3_HEX + PLAIN_HEX);
        assertEquals(2, BiliPacketCodec.decode(joined).size());
        assertTrue(BiliPacketCodec.decode(new byte[0]).isEmpty());
        assertTrue(BiliPacketCodec.decode(null).isEmpty());
        assertTrue(BiliPacketCodec.decode(new byte[]{1, 2, 3}).isEmpty());
        // 头部自报长度超出实际：停在坏包处，已解出的保留
        byte[] truncated = HexFormat.of().parseHex(OP3_HEX + "0000016000100000");
        assertEquals(1, BiliPacketCodec.decode(truncated).size());
    }
}
