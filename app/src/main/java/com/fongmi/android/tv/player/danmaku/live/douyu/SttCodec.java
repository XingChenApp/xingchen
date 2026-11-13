package com.fongmi.android.tv.player.danmaku.live.douyu;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 斗鱼 STT 协议编解码，照 dart_simple_live 的 douyu_danmaku.dart 逐行对译：
 * 帧＝小端包头 12 字节（len u32le 写两遍＋msgType u16le＋encrypt u8＋reserved u8）＋STT 正文 UTF-8＋结尾 0x00，
 * 其中 len = 4+4+正文字节数+1；一帧可连包多条，按 len 切分（参照实现只解首包，本处按设计口径切全帧）。
 * STT 正文＝key@=value/ 逐字段，转义 @→@A、/→@S。
 */
public final class SttCodec {

    public static final int CLIENT_MSG_TYPE = 689;
    public static final int SERVER_MSG_TYPE = 690;

    private static final int HEADER_BYTES = 12;
    private static final int MIN_LENGTH = 9;

    private SttCodec() {
    }

    public static byte[] encode(String body) {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        int length = 4 + 4 + payload.length + 1;
        byte[] out = new byte[4 + length];
        putInt32Le(out, 0, length);
        putInt32Le(out, 4, length);
        putInt16Le(out, 8, CLIENT_MSG_TYPE);
        out[10] = 0;
        out[11] = 0;
        System.arraycopy(payload, 0, out, HEADER_BYTES, payload.length);
        out[out.length - 1] = 0;
        return out;
    }

    /**
     * 把一帧字节流按 len 切成全部 STT 正文；遇到残缺/非法长度即停，已切出的保留，
     * 与参照实现的容错口径一致（单包解析失败不拖垮整帧其余包）。
     */
    public static List<String> decodePackets(byte[] frame) {
        List<String> bodies = new ArrayList<>();
        if (frame == null) return bodies;
        int offset = 0;
        while (offset + HEADER_BYTES <= frame.length) {
            int length = getInt32Le(frame, offset);
            if (length < MIN_LENGTH || offset + 4L + length > frame.length) break;
            int bodyLength = length - MIN_LENGTH;
            bodies.add(new String(frame, offset + HEADER_BYTES, bodyLength, StandardCharsets.UTF_8));
            offset += 4 + length;
        }
        return bodies;
    }

    /**
     * STT 一层字段解析（参照 sttToJObject 的第一层）：先按原始文本切字段，再逐值反转义。
     * 嵌套组（// 分隔）在第一层只保留反转义后的原文，斗鱼 chatmsg 的消费字段全在一层，用不到递归。
     */
    public static Map<String, String> parseFields(String body) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (body == null || body.isEmpty()) return fields;
        for (String field : body.split("/")) {
            if (field.isEmpty()) continue;
            int separator = field.indexOf("@=");
            if (separator < 0) continue;
            String key = field.substring(0, separator);
            String value = unescape(field.substring(separator + 2));
            fields.put(key, value);
        }
        return fields;
    }

    public static String unescape(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replace("@S", "/").replace("@A", "@");
    }

    private static void putInt32Le(byte[] out, int offset, int value) {
        out[offset] = (byte) value;
        out[offset + 1] = (byte) (value >>> 8);
        out[offset + 2] = (byte) (value >>> 16);
        out[offset + 3] = (byte) (value >>> 24);
    }

    private static void putInt16Le(byte[] out, int offset, int value) {
        out[offset] = (byte) value;
        out[offset + 1] = (byte) (value >>> 8);
    }

    private static int getInt32Le(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }
}
