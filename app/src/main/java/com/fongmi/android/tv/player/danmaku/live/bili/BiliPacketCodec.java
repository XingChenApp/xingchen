package com.fongmi.android.tv.player.danmaku.live.bili;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Inflater;

/**
 * Bilibili 直播弹幕二进制包编解码，照 dart_simple_live 的 bilibili_danmaku.dart 对译：
 * 16 字节大端头＝总长 u32be＋头长 u16be＝16＋protover u16be＋op u32be＋seq u32be＝1。
 * 进房 op＝7（body 为 JSON，protover 字段填 2，只走 zlib、免 brotli）、心跳 op＝2 空包、
 * 人气 op＝3（body＝u32be 在线数）、推送 op＝5（protover 2 时 body 为 zlib 压缩的多包拼接，
 * 解开后按内层包头逐包切；参照实现按控制字符切文本，真实流量上两种切法结果一致）。
 * 解码容错：坏包/残包即停已解出的保留，protover＝3（brotli）无库可解直接跳过，绝不抛异常。
 */
public final class BiliPacketCodec {

    public static final int OP_HEARTBEAT = 2;
    public static final int OP_HEARTBEAT_REPLY = 3;
    public static final int OP_MESSAGE = 5;
    public static final int OP_AUTH = 7;

    public static final int HEADER_BYTES = 16;

    private BiliPacketCodec() {
    }

    /** 解出的单个包：op＋protover＋body（op5 的压缩包已解开并按内层包头展开）。 */
    public record RawPacket(int op, int protover, byte[] body) {
    }

    public static byte[] encodePacket(int protover, int op, byte[] body) {
        byte[] payload = body == null ? new byte[0] : body;
        byte[] out = new byte[HEADER_BYTES + payload.length];
        putInt32Be(out, 0, out.length);
        putInt16Be(out, 4, HEADER_BYTES);
        putInt16Be(out, 6, protover);
        putInt32Be(out, 8, op);
        putInt32Be(out, 12, 1);
        System.arraycopy(payload, 0, out, HEADER_BYTES, payload.length);
        return out;
    }

    /** 进房包：uid＝0 匿名，protover＝2（zlib），key 为 getDanmuInfo 的 token。 */
    public static byte[] encodeAuth(long roomId, String buvid, String token) {
        String json = "{\"uid\":0"
                + ",\"roomid\":" + roomId
                + ",\"protover\":2"
                + ",\"buvid\":" + quote(buvid)
                + ",\"platform\":\"web\""
                + ",\"type\":2"
                + ",\"key\":" + quote(token)
                + "}";
        return encodePacket(0, OP_AUTH, json.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] encodeHeartbeat() {
        return encodePacket(0, OP_HEARTBEAT, new byte[0]);
    }

    /**
     * 把一帧字节流按总长切成全部包并展开 op5 压缩包；遇到残缺/非法长度即停。
     */
    public static List<RawPacket> decode(byte[] frame) {
        List<RawPacket> packets = new ArrayList<>();
        if (frame == null) return packets;
        collect(frame, packets, 0);
        return packets;
    }

    private static void collect(byte[] data, List<RawPacket> out, int depth) {
        int offset = 0;
        while (offset + HEADER_BYTES <= data.length) {
            int total = getInt32Be(data, offset);
            if (total < HEADER_BYTES || offset + (long) total > data.length) break;
            int protover = getInt16Be(data, offset + 6);
            int op = getInt32Be(data, offset + 8);
            byte[] body = new byte[total - HEADER_BYTES];
            System.arraycopy(data, offset + HEADER_BYTES, body, 0, body.length);
            if (op == OP_MESSAGE && protover == 2 && depth == 0) {
                byte[] inflated = inflate(body);
                if (inflated != null) collect(inflated, out, depth + 1);
            } else if (op == OP_MESSAGE && protover == 3) {
                // brotli 无库可解：进房已声明 protover＝2，正常不会收到，收到也静默跳过
            } else {
                out.add(new RawPacket(op, protover, body));
            }
            offset += total;
        }
    }

    static byte[] inflate(byte[] data) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(64, data.length * 3));
            byte[] chunk = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(chunk);
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break;
                }
                buffer.write(chunk, 0, count);
            }
            return buffer.toByteArray();
        } catch (Exception error) {
            return null;
        } finally {
            inflater.end();
        }
    }

    private static String quote(String value) {
        String safe = value == null ? "" : value;
        StringBuilder builder = new StringBuilder(safe.length() + 2);
        builder.append('"');
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            if (c == '"' || c == '\\') builder.append('\\');
            builder.append(c);
        }
        return builder.append('"').toString();
    }

    private static void putInt32Be(byte[] out, int offset, int value) {
        out[offset] = (byte) (value >>> 24);
        out[offset + 1] = (byte) (value >>> 16);
        out[offset + 2] = (byte) (value >>> 8);
        out[offset + 3] = (byte) value;
    }

    private static void putInt16Be(byte[] out, int offset, int value) {
        out[offset] = (byte) (value >>> 8);
        out[offset + 1] = (byte) value;
    }

    static int getInt32Be(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static int getInt16Be(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }
}
