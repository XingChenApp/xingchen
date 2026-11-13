package com.fongmi.android.tv.player.danmaku.live.huya;

import java.util.List;

/**
 * 虎牙弹幕 WS 帧编解码，照 dart_simple_live 的 huya_danmaku.dart 对译：
 * 外层是 WS 命令流（TARS）：字段 0 为命令号、字段 1 为负载字节数组。
 * 命令 1＝进房，负载为进房结构（ayyuid@0、匿名 true@1、空串@2/@3、topSid@4、subSid@5、0@6/@7）；
 * 命令 7＝推送，负载为 HYPushMessage（uri@1、msg@2）：uri 1400 为弹幕 HYMessage
 * （发送者结构@0 里 uid@0、昵称@2，正文@3，弹幕格式结构@6 里字体色@0），uri 8006 为人气（int@0）。
 * 心跳是参照实现写死的固定帧（base64 ABQdAAwsNgBM），60 秒一发。
 * 解码容错：坏帧/非推送/未知 uri 一律回 EMPTY，绝不抛到会话回调线程上。
 */
public final class HuyaPacketCodec {

    /** 参照实现写死的心跳帧（huya_danmaku.dart 的 heartbeatData）。 */
    public static final byte[] HEARTBEAT_FRAME = {0x00, 0x14, 0x1D, 0x00, 0x0C, 0x2C, 0x36, 0x00, 0x4C};

    public static final int CMD_JOIN = 1;
    public static final int CMD_PUSH = 7;
    public static final int URI_CHAT = 1400;
    public static final int URI_ONLINE = 8006;

    private HuyaPacketCodec() {
    }

    /** 一条弹幕的解出结果：昵称＋文本＋字体色（十进制 RGB 原值，<=0 表示未设色）。 */
    public record Chat(String nickname, String text, int fontColor) {
    }

    /** 一帧的解出结果：一帧至多一条弹幕或一次人气（与参照实现的逐消息回调形态一致）。 */
    public record Decoded(List<Chat> chats, Long online) {

        public static final Decoded EMPTY = new Decoded(List.of(), null);

        public Decoded {
            chats = chats == null ? List.of() : List.copyOf(chats);
        }
    }

    public static byte[] encodeJoin(long ayyuid, long topSid, long subSid) {
        byte[] inner = new TarsCodec.Writer()
                .writeInt(ayyuid, 0)
                .writeBool(true, 1)
                .writeString("", 2)
                .writeString("", 3)
                .writeInt(topSid, 4)
                .writeInt(subSid, 5)
                .writeInt(0, 6)
                .writeInt(0, 7)
                .toByteArray();
        return new TarsCodec.Writer()
                .writeInt(CMD_JOIN, 0)
                .writeBytes(inner, 1)
                .toByteArray();
    }

    public static Decoded decode(byte[] frame) {
        try {
            TarsCodec.Reader reader = new TarsCodec.Reader(frame);
            if (reader.readInt(0) != CMD_PUSH) return Decoded.EMPTY;
            byte[] body = reader.readBytes(1);
            TarsCodec.Reader push = new TarsCodec.Reader(body);
            push.readInt(0);
            long uri = push.readInt(1);
            byte[] msg = push.readBytes(2);
            if (uri == URI_ONLINE) {
                return new Decoded(List.of(), new TarsCodec.Reader(msg).readInt(0));
            }
            if (uri != URI_CHAT) return Decoded.EMPTY;
            Chat chat = parseChat(msg);
            return chat == null ? Decoded.EMPTY : new Decoded(List.of(chat), null);
        } catch (Throwable error) {
            return Decoded.EMPTY;
        }
    }

    /**
     * HYMessage 一帧的解码：发送者昵称在此解出备查、不进渲染文本（与斗鱼/B站口径一致）；
     * 过滤口径照参照实现：正文为空一律丢。
     */
    static Chat parseChat(byte[] msg) {
        TarsCodec.Reader reader = new TarsCodec.Reader(msg);
        String nickname = "";
        if (reader.enterStruct(0)) {
            reader.readInt(0);
            nickname = reader.readString(2);
            reader.exitStruct();
        }
        String content = reader.readString(3);
        int fontColor = 0;
        if (reader.enterStruct(6)) {
            fontColor = (int) reader.readInt(0);
            reader.exitStruct();
        }
        if (content.isEmpty()) return null;
        return new Chat(nickname, content, fontColor);
    }
}
