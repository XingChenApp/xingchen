package com.fongmi.android.tv.player.danmaku.live.bili;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 验收用小工具（非 JUnit）：用生产 BiliPacketCodec＋BiliProvider.parseMessage 解录制原字节，
 * 逐条打印 昵称\t颜色十进制\t文本，与独立参照解码（bili-expected.txt）对拍。
 * 用法：java ... BiliRecordingCheck <录制文件>
 */
public final class BiliRecordingCheck {

    private BiliRecordingCheck() {
    }

    public static void main(String[] args) throws Exception {
        byte[] raw = Files.readAllBytes(Path.of(args[0]));
        int count = 0;
        for (BiliPacketCodec.RawPacket packet : BiliPacketCodec.decode(raw)) {
            if (packet.op() == BiliPacketCodec.OP_HEARTBEAT_REPLY) {
                System.out.println("ONLINE\t" + BiliPacketCodec.getInt32Be(packet.body(), 0));
                continue;
            }
            if (packet.op() != BiliPacketCodec.OP_MESSAGE) continue;
            BiliProvider.DanmuMessage danmu = BiliProvider.parseMessage(new String(packet.body(), StandardCharsets.UTF_8));
            if (danmu == null) continue;
            int rgb = danmu.colorArgb() & 0xFFFFFF;
            System.out.println(danmu.nickname() + "\t" + rgb + "\t" + (danmu.superChat() ? "SC:" : "") + danmu.text());
            count++;
        }
        System.out.println("COUNT\t" + count);
    }
}
