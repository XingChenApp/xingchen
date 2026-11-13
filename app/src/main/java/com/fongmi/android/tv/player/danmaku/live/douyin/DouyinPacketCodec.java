package com.fongmi.android.tv.player.danmaku.live.douyin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 抖音直播弹幕 PushFrame/Response 编解码，照 dart_simple_live 的 douyin.proto 与
 * douyin_danmaku.dart 对译：外层 PushFrame（payloadType 在 7、payload 在 8、logId 在 2），
 * payload 是 gzip 压过的 Response；Response.messagesList 逐条按 method 分发，
 * WebcastChatMessage 取 user.nickName＋content，WebcastRoomUserSeqMessage 取
 * totalUser 喂人气；Response.needAck 为真时要回一帧 ack（payloadType=ack、
 * logId 原样、payload=internalExt 原文），不回平台会停推。
 * 解码全程不抛：坏帧/异形帧一律返回 EMPTY。
 */
public final class DouyinPacketCodec {

    /** 弹幕默认色：抖音没有直给的颜色字段，颜色藏在 RTF 富文本段里（见 {@link #rtfBodyColor}），取不到时为白。 */
    public static final int CHAT_COLOR_ARGB = 0xFFFFFFFF;

    private DouyinPacketCodec() {
    }

    public record Chat(String nickname, String text, int colorArgb) {
    }

    /** 一帧 Response 内的事件按平台下发原序排列（弹幕与人气消息同帧交错，不许重排）。 */
    public sealed interface Event {

        record ChatEvent(Chat chat) implements Event {
        }

        record OnlineEvent(long totalUser) implements Event {
        }
    }

    /** ackFrame 非空＝本帧要求回执，内容已是可直接发送的 ack 帧字节。 */
    public record Decoded(List<Event> events, byte[] ackFrame) {

        public static final Decoded EMPTY = new Decoded(List.of(), null);

        public Decoded {
            events = events == null ? List.of() : List.copyOf(events);
        }
    }

    /** 心跳帧兼进房帧（参照实现 onReady 即发一帧 hb，之后 10 秒一帧）。 */
    public static byte[] encodeHeartbeat() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeString(out, 7, "hb");
        return out.toByteArray();
    }

    public static byte[] encodeAck(long logId, String internalExt) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarintField(out, 2, logId);
        writeString(out, 7, "ack");
        if (internalExt != null && !internalExt.isEmpty()) writeBytes(out, 8, internalExt.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    public static Decoded decode(byte[] frame) {
        try {
            if (frame == null || frame.length == 0) return Decoded.EMPTY;
            Map<Integer, List<Object>> push = parse(frame);
            long logId = firstVarint(push, 2, 0L);
            byte[] payload = firstBytes(push, 8);
            if (payload == null || payload.length == 0) return Decoded.EMPTY;
            Map<Integer, List<Object>> response = parse(gunzip(payload));
            String internalExt = firstString(response, 5);
            boolean needAck = firstVarint(response, 9, 0L) != 0L;
            List<Event> events = new ArrayList<>();
            for (Object blob : response.getOrDefault(1, List.of())) {
                if (!(blob instanceof byte[] messageBytes)) continue;
                Map<Integer, List<Object>> message = parse(messageBytes);
                String method = firstString(message, 1);
                byte[] body = firstBytes(message, 2);
                if (body == null) continue;
                if ("WebcastChatMessage".equals(method)) {
                    Chat chat = parseChat(body);
                    if (chat != null) events.add(new Event.ChatEvent(chat));
                } else if ("WebcastRoomUserSeqMessage".equals(method)) {
                    Long total = parseOnline(body);
                    if (total != null) events.add(new Event.OnlineEvent(total));
                }
            }
            byte[] ack = needAck ? encodeAck(logId, internalExt) : null;
            if (events.isEmpty() && ack == null) return Decoded.EMPTY;
            return new Decoded(events, ack);
        } catch (Throwable error) {
            return Decoded.EMPTY;
        }
    }

    private static Chat parseChat(byte[] body) {
        Map<Integer, List<Object>> chat = parse(body);
        String content = firstString(chat, 3);
        if (content == null || content.isEmpty()) return null;
        String nickname = "";
        byte[] user = firstBytes(chat, 2);
        if (user != null) {
            String nick = firstString(parse(user), 3);
            if (nick != null) nickname = nick;
        }
        return new Chat(nickname, content, rtfBodyColor(firstBytes(chat, 41)));
    }

    /**
     * 抖音弹幕颜色实证（2026-10-24 对在播房录制帧做字段转储）：ChatMessage 没有直给的
     * 颜色字段，颜色在 field 41 的 RTF 富文本里——重复段（field 4）各带样式
     * （段 field 2 的 field 1 为 "#RRGGBB" 或 "#AARRGGBB"）。口径：只看正文段
     * （段类型 field 1 == 1）的颜色，全部正文段同色才取该色作整条弹幕颜色；
     * @提及段（类型 11）的蓝是提及高亮、属分段色，整条单色模型不表达、不参与投票；
     * 无 RTF、正文段无色或同条多色不一致，一律回白色（与无 RTF 的普通弹幕一致）。
     * 解码全程不抛：RTF 异形只丢颜色、不丢弹幕。
     */
    static int rtfBodyColor(byte[] rtfBytes) {
        if (rtfBytes == null || rtfBytes.length == 0) return CHAT_COLOR_ARGB;
        try {
            Map<Integer, List<Object>> rtf = parse(rtfBytes);
            Integer agreed = null;
            for (Object blob : rtf.getOrDefault(4, List.of())) {
                if (!(blob instanceof byte[] spanBytes)) continue;
                Map<Integer, List<Object>> span = parse(spanBytes);
                if (firstVarint(span, 1, -1L) != 1L) continue;
                byte[] styleBytes = firstBytes(span, 2);
                if (styleBytes == null) continue;
                Integer color = parseStyleColor(firstString(parse(styleBytes), 1));
                if (color == null) continue;
                if (agreed == null) {
                    agreed = color;
                } else if (agreed.intValue() != color.intValue()) {
                    return CHAT_COLOR_ARGB;
                }
            }
            return agreed == null ? CHAT_COLOR_ARGB : agreed;
        } catch (Throwable error) {
            return CHAT_COLOR_ARGB;
        }
    }

    /** "#RRGGBB" → 补 FF alpha；"#AARRGGBB" → 原样（含 alpha）；解析不出回 null。 */
    private static Integer parseStyleColor(String raw) {
        if (raw == null) return null;
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        try {
            if (hex.length() == 6) return 0xFF000000 | Integer.parseInt(hex, 16);
            if (hex.length() == 8) return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    private static Long parseOnline(byte[] body) {
        long totalUser = firstVarint(parse(body), 7, 0L);
        return totalUser > 0 ? totalUser : null;
    }

    private static byte[] gunzip(byte[] payload) throws Exception {
        if (payload.length < 2 || (payload[0] & 0xFF) != 0x1F || (payload[1] & 0xFF) != 0x8B) return payload;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(payload))) {
            return gzip.readAllBytes();
        }
    }

    // ---------- 最小 protobuf 线格式（只认 varint 与 length-delimited，其余线型跳过） ----------

    static Map<Integer, List<Object>> parse(byte[] bytes) {
        Map<Integer, List<Object>> fields = new LinkedHashMap<>();
        Cursor cursor = new Cursor(bytes);
        while (cursor.hasNext()) {
            long key = cursor.readVarint();
            int field = (int) (key >>> 3);
            int wire = (int) (key & 7);
            if (field <= 0) break;
            switch (wire) {
                case 0 -> fields.computeIfAbsent(field, k -> new ArrayList<>()).add(cursor.readVarint());
                case 1 -> fields.computeIfAbsent(field, k -> new ArrayList<>()).add(cursor.readBytes(8));
                case 2 -> {
                    int length = (int) cursor.readVarint();
                    fields.computeIfAbsent(field, k -> new ArrayList<>()).add(cursor.readBytes(length));
                }
                case 5 -> fields.computeIfAbsent(field, k -> new ArrayList<>()).add(cursor.readBytes(4));
                default -> throw new IllegalArgumentException("wire " + wire);
            }
        }
        return fields;
    }

    private static long firstVarint(Map<Integer, List<Object>> fields, int field, long fallback) {
        List<Object> values = fields.get(field);
        if (values == null || values.isEmpty()) return fallback;
        return values.get(0) instanceof Long value ? value : fallback;
    }

    private static byte[] firstBytes(Map<Integer, List<Object>> fields, int field) {
        List<Object> values = fields.get(field);
        if (values == null || values.isEmpty()) return null;
        return values.get(0) instanceof byte[] value ? value : null;
    }

    private static String firstString(Map<Integer, List<Object>> fields, int field) {
        byte[] bytes = firstBytes(fields, field);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarintField(ByteArrayOutputStream out, int field, long value) {
        writeVarint(out, ((long) field << 3));
        writeVarint(out, value);
    }

    private static void writeString(ByteArrayOutputStream out, int field, String value) {
        writeBytes(out, field, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(ByteArrayOutputStream out, int field, byte[] value) {
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

    private static final class Cursor {

        private final byte[] bytes;
        private int pos;

        Cursor(byte[] bytes) {
            this.bytes = bytes;
        }

        boolean hasNext() {
            return pos < bytes.length;
        }

        long readVarint() {
            long result = 0;
            int shift = 0;
            while (pos < bytes.length) {
                int b = bytes[pos++] & 0xFF;
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return result;
                shift += 7;
                if (shift > 63) throw new IllegalArgumentException("varint too long");
            }
            throw new IllegalArgumentException("truncated varint");
        }

        byte[] readBytes(int length) {
            if (length < 0 || pos + length > bytes.length) throw new IllegalArgumentException("truncated bytes");
            byte[] out = new byte[length];
            System.arraycopy(bytes, pos, out, 0, length);
            pos += length;
            return out;
        }
    }
}
