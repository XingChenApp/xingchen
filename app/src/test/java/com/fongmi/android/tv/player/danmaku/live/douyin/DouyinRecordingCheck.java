package com.fongmi.android.tv.player.danmaku.live.douyin;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 验收用小工具（非 JUnit）：用生产 DouyinPacketCodec 解录制原字节，
 * 逐条打印 昵称\t字体色（无符号）\t文本（人气打印 ONLINE），与独立参照解码
 * （douyin-974709925783-expected.txt）对拍。
 * 录制格式：重复的 [4字节BE长度][一帧 WS 二进制消息]。
 * 用法：java ... DouyinRecordingCheck <录制文件>
 */
public final class DouyinRecordingCheck {

    private DouyinRecordingCheck() {
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
            DouyinPacketCodec.Decoded decoded = DouyinPacketCodec.decode(frame);
            for (DouyinPacketCodec.Event event : decoded.events()) {
                if (event instanceof DouyinPacketCodec.Event.ChatEvent chatEvent) {
                    DouyinPacketCodec.Chat chat = chatEvent.chat();
                    System.out.println(chat.nickname() + "\t" + (chat.colorArgb() & 0xFFFFFFFFL) + "\t" + chat.text());
                    count++;
                } else if (event instanceof DouyinPacketCodec.Event.OnlineEvent onlineEvent) {
                    System.out.println("ONLINE\t" + onlineEvent.totalUser());
                }
            }
        }
        System.out.println("COUNT\t" + count);
    }
}
