package com.fongmi.android.tv.player.danmaku.live.huya;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 验收用小工具（非 JUnit）：用生产 HuyaPacketCodec 解录制原字节，
 * 逐条打印 昵称\t字体色\t文本（人气打印 ONLINE），与独立参照解码（huya-998-expected.txt）对拍。
 * 录制格式：重复的 [4字节BE长度][一帧 WS 二进制消息]。
 * 用法：java ... HuyaRecordingCheck <录制文件>
 */
public final class HuyaRecordingCheck {

    private HuyaRecordingCheck() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        byte[] raw = Files.readAllBytes(Path.of(args[0]));
        int count = 0;
        int pos = 0;
        while (pos + 4 <= raw.length) {
            int length = (raw[pos] & 0xFF) << 24 | (raw[pos + 1] & 0xFF) << 16 | (raw[pos + 2] & 0xFF) << 8 | (raw[pos + 3] & 0xFF);
            pos += 4;
            byte[] frame = new byte[length];
            System.arraycopy(raw, pos, frame, 0, length);
            pos += length;
            HuyaPacketCodec.Decoded decoded = HuyaPacketCodec.decode(frame);
            for (HuyaPacketCodec.Chat chat : decoded.chats()) {
                System.out.println(chat.nickname() + "\t" + chat.fontColor() + "\t" + chat.text());
                count++;
            }
            if (decoded.online() != null) System.out.println("ONLINE\t" + decoded.online());
        }
        System.out.println("COUNT\t" + count);
    }
}
