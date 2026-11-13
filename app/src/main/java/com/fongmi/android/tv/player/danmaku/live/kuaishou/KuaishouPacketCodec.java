package com.fongmi.android.tv.player.danmaku.live.kuaishou;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 快手直播弹幕 SocketMessage 编解码，照 dart_simple_live 快手分支
 * （simple-live-kuaishou 的 kuaishou_danmaku.dart）对译：外层 protobuf
 * SocketMessage（1＝payloadType、2＝compressionType、3＝payload）；
 * 进房 payloadType＝200（CSWebEnterRoom：1 token、2 liveStreamId、3/4 varint 0、
 * 5 expTag、6 attach、7 pageId），心跳 payloadType＝1（payload 1＝当前毫秒）；
 * 推送 payloadType＝310（SCWebFeedPush）的 5 号字段逐条是评论 feed
 * （2＝SimpleUserInfo{2 昵称}、3 正文、6 字体色 "#RRGGBB"、7＝2 为隐藏），
 * payloadType＝103 为错误帧、不产出弹幕；compressionType＝2 先 gzip 解开，
 * ＝3（AES）参照实现同样不支持、整帧丢弃。
 * 解码全程不抛：坏帧/异形帧一律返回 EMPTY。
 */
public final class KuaishouPacketCodec {

    public static final int PAYLOAD_HEARTBEAT = 1;
    public static final int PAYLOAD_ERROR = 103;
    public static final int PAYLOAD_ENTER_ROOM = 200;
    public static final int PAYLOAD_FEED_PUSH = 310;
    public static final int COMPRESSION_GZIP = 2;
    public static final int COMPRESSION_AES = 3;
    public static final int WHITE_ARGB = 0xFFFFFFFF;

    private KuaishouPacketCodec() {
    }

    public record Chat(String nickname, String text, int colorArgb) {
    }

    public record Decoded(List<Chat> chats) {

        public static final Decoded EMPTY = new Decoded(List.of());

        public Decoded {
            chats = chats == null ? List.of() : List.copyOf(chats);
        }
    }

    /** 外层 SocketMessage：只写 payloadType 与 payload 两字段（照参照实现的 encode 口径）。 */
    public static byte[] encodeSocketMessage(int payloadType, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarintField(out, 1, payloadType);
        writeBytes(out, 3, payload == null ? new byte[0] : payload);
        return out.toByteArray();
    }

    /** 进房帧（payloadType＝200）：空串字段照参照 writer 口径跳过、3/4 两 varint 恒写 0。 */
    public static byte[] encodeEnterRoom(String token, String liveStreamId, String expTag, String attach, String pageId) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeString(payload, 1, token);
        writeString(payload, 2, liveStreamId);
        writeVarintField(payload, 3, 0);
        writeVarintField(payload, 4, 0);
        writeString(payload, 5, expTag);
        writeString(payload, 6, attach);
        writeString(payload, 7, pageId);
        return encodeSocketMessage(PAYLOAD_ENTER_ROOM, payload.toByteArray());
    }

    /** 心跳帧（payloadType＝1）：payload 只有 1 号 varint＝当前毫秒时间戳。 */
    public static byte[] encodeHeartbeat(long timestampMs) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarintField(payload, 1, timestampMs);
        return encodeSocketMessage(PAYLOAD_HEARTBEAT, payload.toByteArray());
    }

    public static Decoded decode(byte[] frame) {
        try {
            if (frame == null || frame.length == 0) return Decoded.EMPTY;
            Map<Integer, List<Object>> socket = parse(frame);
            long payloadType = firstVarint(socket, 1, 0L);
            long compressionType = firstVarint(socket, 2, 0L);
            byte[] payload = firstBytes(socket, 3);
            if (payload == null || payload.length == 0) return Decoded.EMPTY;
            if (compressionType == COMPRESSION_AES) return Decoded.EMPTY;
            if (compressionType == COMPRESSION_GZIP) payload = gunzip(payload);
            if (payloadType != PAYLOAD_FEED_PUSH) return Decoded.EMPTY;
            List<Chat> chats = new ArrayList<>();
            for (Object blob : parse(payload).getOrDefault(5, List.of())) {
                if (!(blob instanceof byte[] feedBytes)) continue;
                Chat chat = parseCommentFeed(feedBytes);
                if (chat != null) chats.add(chat);
            }
            return chats.isEmpty() ? Decoded.EMPTY : new Decoded(chats);
        } catch (Throwable error) {
            return Decoded.EMPTY;
        }
    }

    /** 评论 feed：2 用户、3 正文、6 颜色串、7 隐藏标记（＝2 丢）；正文空丢（照参照口径）。 */
    static Chat parseCommentFeed(byte[] blob) {
        Map<Integer, List<Object>> feed = parse(blob);
        if (firstVarint(feed, 7, 0L) == 2L) return null;
        String content = firstString(feed, 3);
        if (content == null || content.isEmpty()) return null;
        String nickname = "";
        byte[] user = firstBytes(feed, 2);
        if (user != null) {
            String name = firstString(parse(user), 2);
            if (name != null) nickname = name;
        }
        return new Chat(nickname, content, parseColor(firstString(feed, 6)));
    }

    /** "#RRGGBB"/"RRGGBB" → ARGB；解析不出回白色（照参照 _parseColor）。 */
    static int parseColor(String raw) {
        if (raw != null) {
            String hex = raw.trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() == 6) {
                try {
                    return 0xFF000000 | Integer.parseInt(hex, 16);
                } catch (NumberFormatException ignored) {
                    return WHITE_ARGB;
                }
            }
        }
        return WHITE_ARGB;
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
        if (value == null || value.isEmpty()) return;
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
