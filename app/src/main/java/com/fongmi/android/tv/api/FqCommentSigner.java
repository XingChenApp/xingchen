package com.fongmi.android.tv.api;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 红果（fqnovel）评论只读轻签名器：X-Gorgon 0404 + X-Khronos + 轻量 X-Argus / X-Ladon。
 * 逐行移植自在产实现 hongguoTV CommentSignature（设计 hongguo-comments-shell-design.md §3），
 * SM3 手写对译红果 py 源自带实现。评论链只服务匿名只读、不带任何账号凭据；
 * 其中 X-Argus / X-Ladon 真算法自 2026-10-29 起也被 HgSign 复用（passport 登录
 * 签名改走真算法，定根见 hongguo/sms-login-fail-20261029.md），见 {@link #argusLadon}。
 * <p>
 * 身份分轨（2026-10-29 深夜，login-close-loop-20261029.md 第四-1 路 A）：本签名器
 * 的消息字段 4/7/8/9 与 ladon 数据尾自带的身份是抓样来源的番茄（aid 3019 /
 * 6.8.1.32 / v04.07.01 / 135135744，见 {@link Identity#TOMATO}）——评论链的目标
 * 平台就是番茄系评论口，此身份是它的活证形态，评论链（{@link #headers} 与
 * 四参 {@link #argusLadon}）逐字保留不动。HgSign 的红果主链（内容＋passport）
 * 改走五参 {@link #argusLadon(String, byte[], long, byte[], Identity)} 递交红果
 * 身份（与同请求 medusa/公参同源），不再让每条红果请求自称番茄。算法与密钥
 * 两轨共用、不变（密码学有效性已双证）；字段 6 与 ladon 中段的 1611921764 是
 * 两式共用的许可常量、与身份无关，路 A 未动。
 */
public class FqCommentSigner {

    private static final byte[] ARGUS_SECRET = hex("ac1adaae95a7af94a5114ab3b3a97dd80050aa0a39314c40528caec95256c28c");
    private static final byte[] ARGUS_MASK = hex("d04ffdffd04ffdff");
    private static final long SIMON_SEED = 0x3dc94c3a046d678bL;

    /**
     * argus/ladon 的自称身份（argus 消息字段 4=aid、7=版本名、8=SDK 版本串、
     * 9=SDK 构建号；ladon 明文尾段的 aid）。算法只认字段位置，身份由调用方给：
     * 评论链用 {@link #TOMATO}（抓样活证），红果主链由 HgSign 按其同请求
     * medusa/公参的口径组装（8662 / 档案版本名 / v04.06.04-ml-android /
     * 67503104）。
     */
    public static final class Identity {
        /** 抓样来源的番茄身份（评论链现行值，逐字保留）。 */
        public static final Identity TOMATO = new Identity("3019", "6.8.1.32", "v04.07.01-ml-android", 135135744);

        public final String aid;
        public final String appVersion;
        public final String sdkVersion;
        public final long sdkBuild;

        public Identity(String aid, String appVersion, String sdkVersion, long sdkBuild) {
            this.aid = aid;
            this.appVersion = appVersion;
            this.sdkVersion = sdkVersion;
            this.sdkBuild = sdkBuild;
        }
    }

    public static Map<String, String> headers(String query) {
        byte[] nonce = new byte[11];
        new SecureRandom().nextBytes(nonce);
        return headers(query, System.currentTimeMillis(), nonce);
    }

    public static Map<String, String> headers(String query, long now, byte[] nonce) {
        if (nonce == null || nonce.length != 11) throw new IllegalArgumentException("nonce must be 11 bytes");
        long ts = now / 1000;
        Map<String, String> map = new LinkedHashMap<>();
        map.put("X-Gorgon", gorgon(query, ts, nonce));
        map.put("X-Khronos", String.valueOf(ts));
        map.put("X-SS-Req-Ticket", String.valueOf(now));
        map.put("X-SS-STUB", "");
        map.put("X-Argus", argus(query, null, ts, nonce));
        map.put("X-Ladon", ladon(ts, copyOfRange(nonce, 2, 6)));
        return map;
    }

    /**
     * X-Argus / X-Ladon 真算法的对外入口（HgSign 的 passport 等签名复用同一实现，
     * 不另抄一份）：query 传最终查询串（含 ts/_rticket），body 传实际请求体字节
     * （GET/无体传 null），tsSeconds 与 x-khronos 同秒，nonce 为 11 字节随机源
     * （argus 的 random 与 ladon 的 salt 都从它派生，与 {@link #headers} 同口径）。
     * 返回 {x-argus, x-ladon}。
     */
    public static String[] argusLadon(String query, byte[] body, long tsSeconds, byte[] nonce) {
        return argusLadon(query, body, tsSeconds, nonce, Identity.TOMATO);
    }

    /**
     * 带身份的入口（红果主链用）：与四参版同一算法/密钥/随机口径，只把消息里
     * 的自称身份换成 {@code identity}（argus 字段 4/7/8/9 与 ladon 明文尾段）。
     */
    public static String[] argusLadon(String query, byte[] body, long tsSeconds, byte[] nonce, Identity identity) {
        if (nonce == null || nonce.length != 11) throw new IllegalArgumentException("nonce must be 11 bytes");
        return new String[]{argus(query, body, tsSeconds, nonce, identity), ladon(tsSeconds, copyOfRange(nonce, 2, 6), identity.aid)};
    }

    private static String gorgon(String query, long ts, byte[] nonce) {
        int first = ((nonce[0] & 255) % 7 + 1) << 5;
        int second = 0xe0 | (nonce[1] & 15);
        byte[] input = new byte[20];
        System.arraycopy(md5(query.getBytes()), 0, input, 0, 4);
        System.arraycopy(hex("00010704"), 0, input, 12, 4);
        ByteBuffer.wrap(input, 16, 4).putInt((int) ts);
        int[] key = {0x4a, 0, 0x16, second, 0x47, 0x6c, 0, first};
        int[] box = new int[256];
        for (int i = 0; i < 256; i++) box[i] = i;
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + box[i] + key[i % 8]) & 255;
            box[i] = box[j];
        }
        j = 0;
        for (int n = 0; n < input.length; n++) {
            int i = n + 1;
            j = (j + box[i]) & 255;
            box[i] = box[j];
            input[n] = (byte) ((input[n] & 255) ^ box[(box[i] + box[j]) & 255]);
        }
        for (int i = 0; i < input.length; i++) {
            int v = input[i] & 255;
            int swapped = ((v << 4) | (v >>> 4)) & 255;
            int next = input[(i + 1) % input.length] & 255;
            input[i] = (byte) (((Integer.reverse(swapped ^ next) >>> 24) ^ 0xeb) & 255);
        }
        byte[] out = new byte[6 + input.length];
        out[0] = (byte) 0x84;
        out[1] = 4;
        out[2] = (byte) first;
        out[3] = (byte) second;
        System.arraycopy(input, 0, out, 6, input.length);
        return hexString(out);
    }

    private static String ladon(long ts, byte[] salt) {
        return ladon(ts, salt, Identity.TOMATO.aid);
    }

    private static String ladon(long ts, byte[] salt, String aidTail) {
        byte[] ascii = hexString(md5(concat(salt, "8662".getBytes()))).getBytes();
        java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
        queue.add(word(ascii, 16));
        queue.add(word(ascii, 24));
        long left = word(ascii, 0);
        long right = word(ascii, 8);
        long[] keys = new long[34];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = left;
            long next = (Long.rotateRight(right, 8) + left) ^ (long) i;
            queue.add(next);
            left = next ^ Long.rotateLeft(left, 3);
            right = queue.removeFirst();
        }
        byte[] data = pad((ts + "-1611921764-" + aidTail).getBytes());
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(salt, 0, salt.length);
        for (int at = 0; at < data.length; at += 16) {
            long a = word(data, at);
            long b = word(data, at + 8);
            for (long k : keys) {
                b = k ^ (a + Long.rotateRight(b, 8));
                a = b ^ Long.rotateLeft(a, 3);
            }
            byte[] la = le(a);
            byte[] lb = le(b);
            result.write(la, 0, la.length);
            result.write(lb, 0, lb.length);
        }
        return Base64.getEncoder().encodeToString(result.toByteArray());
    }

    private static String argus(String query, byte[] body, long ts, byte[] nonce) {
        return argus(query, body, ts, nonce, Identity.TOMATO);
    }

    private static String argus(String query, byte[] body, long ts, byte[] nonce, Identity identity) {
        long random = ByteBuffer.wrap(nonce, 6, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xffffffffL;
        byte[] nested = new Proto()
                .varint(1, 2 + (nonce[10] & 255) % 49 * 2)
                .varint(2, 1388734)
                .varint(3, 1388734)
                .varint(4, 1388734)
                .build();
        byte[] message = new Proto()
                .varint(1, 0x40401252)
                .varint(2, 2)
                .varint(3, random)
                .bytes(4, identity.aid.getBytes())
                .bytes(6, "1611921764".getBytes())
                .bytes(7, identity.appVersion.getBytes())
                .bytes(8, identity.sdkVersion.getBytes())
                .varint(9, identity.sdkBuild)
                .bytes(10, new byte[8])
                .varint(12, ts * 2)
                // 字段 13＝请求体哈希前 6 字节：有体取 sm3(body)；无体按算法惯例
                // 用 16 字节零的哈希（评论 GET 恒走此支，产出与改前逐字一致）。
                .bytes(13, copyOf(sm3(body != null ? body : new byte[16]), 6))
                .bytes(14, copyOf(sm3(query.getBytes()), 6))
                .bytes(15, nested)
                .bytes(20, "none".getBytes())
                .varint(21, 738)
                .build();
        byte[] secret = ARGUS_SECRET;
        byte[] derived = sm3(concat(secret, hex("3ccc567b"), secret));
        long[] keys = new long[72];
        for (int i = 0; i < 4; i++) keys[i] = word(derived, i * 8);
        for (int i = 4; i < 72; i++) {
            long x = Long.rotateRight(keys[i - 1], 3) ^ keys[i - 3];
            keys[i] = ~keys[i - 4] ^ x ^ Long.rotateRight(x, 1) ^ ((SIMON_SEED >>> ((i - 4) % 62)) & 1) ^ 3;
        }
        byte[] blocks = pad(message);
        ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
        for (int at = 0; at < blocks.length; at += 16) {
            long a = word(blocks, at);
            long b = word(blocks, at + 8);
            for (long k : keys) {
                long next = a ^ (Long.rotateLeft(b, 1) & Long.rotateLeft(b, 8)) ^ Long.rotateLeft(b, 2) ^ k;
                a = b;
                b = next;
            }
            byte[] la = le(a);
            byte[] lb = le(b);
            encrypted.write(la, 0, la.length);
            encrypted.write(lb, 0, lb.length);
        }
        byte[] enc = encrypted.toByteArray();
        byte[] scrambled = new byte[enc.length];
        for (int i = 0; i < enc.length; i++) scrambled[i] = (byte) ((enc[i] & 255) ^ (ARGUS_MASK[i % 8] & 255));
        byte[] joined = concat(ARGUS_MASK, scrambled);
        byte[] container = concat(hex("a6e783ee7001100918"), reverse(joined), hex("567b"));
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(md5(copyOfRange(secret, 0, 16)), "AES"), new IvParameterSpec(md5(copyOfRange(secret, 16, 32))));
            return Base64.getEncoder().encodeToString(concat(hex("3ccc"), cipher.doFinal(container)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 手写 protobuf 编码（varint 与 length-delimited 两类），字段按写入顺序输出。 */
    private static class Proto {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Proto varint(int tag, long value) {
            variable(tag << 3);
            variable(value);
            return this;
        }

        Proto bytes(int tag, byte[] value) {
            variable((tag << 3) | 2);
            variable(value.length);
            out.write(value, 0, value.length);
            return this;
        }

        private void variable(long value) {
            long n = value;
            while ((n >>> 7) != 0) {
                out.write((int) (n & 127) | 128);
                n >>>= 7;
            }
            out.write((int) n);
        }

        byte[] build() {
            return out.toByteArray();
        }
    }

    /** SM3（手写对译红果 py 源自带纯实现，标准 GB/T 32905 压缩函数）。 */
    static byte[] sm3(byte[] data) {
        int[] iv = {0x7380166f, 0x4914b2b9, 0x172442d7, 0xda8a0600, 0xa96f30bc, 0x163138aa, 0xe38dee4d, 0xb0fb0e4e};
        long bitLen = (long) data.length * 8;
        int padZeros = (56 - (data.length + 1) % 64 + 64) % 64;
        byte[] padded = new byte[data.length + 1 + padZeros + 8];
        System.arraycopy(data, 0, padded, 0, data.length);
        padded[data.length] = (byte) 0x80;
        for (int i = 0; i < 8; i++) padded[padded.length - 1 - i] = (byte) (bitLen >>> (8 * i));
        int[] w = new int[68];
        int[] w1 = new int[64];
        for (int off = 0; off < padded.length; off += 64) {
            for (int i = 0; i < 16; i++) w[i] = ByteBuffer.wrap(padded, off + i * 4, 4).getInt();
            for (int j = 16; j < 68; j++) {
                int x = w[j - 16] ^ w[j - 9] ^ Integer.rotateLeft(w[j - 3], 15);
                w[j] = (x ^ Integer.rotateLeft(x, 15) ^ Integer.rotateLeft(x, 23)) ^ Integer.rotateLeft(w[j - 13], 7) ^ w[j - 6];
            }
            for (int j = 0; j < 64; j++) w1[j] = w[j] ^ w[j + 4];
            int a = iv[0], b = iv[1], c = iv[2], d = iv[3], e = iv[4], f = iv[5], g = iv[6], h = iv[7];
            for (int j = 0; j < 64; j++) {
                int tj = j < 16 ? 0x79cc4519 : 0x7a879d8a;
                int ss1 = Integer.rotateLeft(Integer.rotateLeft(a, 12) + e + Integer.rotateLeft(tj, j % 32), 7);
                int ss2 = ss1 ^ Integer.rotateLeft(a, 12);
                int ff = j < 16 ? a ^ b ^ c : (a & b) | (a & c) | (b & c);
                int gg = j < 16 ? e ^ f ^ g : (e & f) | (~e & g);
                int tt1 = ff + d + ss2 + w1[j];
                int tt2 = gg + h + ss1 + w[j];
                d = c;
                c = Integer.rotateLeft(b, 9);
                b = a;
                a = tt1;
                h = g;
                g = Integer.rotateLeft(f, 19);
                f = e;
                e = tt2 ^ Integer.rotateLeft(tt2, 9) ^ Integer.rotateLeft(tt2, 17);
            }
            iv[0] ^= a;
            iv[1] ^= b;
            iv[2] ^= c;
            iv[3] ^= d;
            iv[4] ^= e;
            iv[5] ^= f;
            iv[6] ^= g;
            iv[7] ^= h;
        }
        ByteBuffer out = ByteBuffer.allocate(32);
        for (int v : iv) out.putInt(v);
        return out.array();
    }

    private static byte[] md5(byte[] data) {
        try {
            return MessageDigest.getInstance("MD5").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] pad(byte[] data) {
        int p = 16 - data.length % 16;
        byte[] out = new byte[data.length + p];
        System.arraycopy(data, 0, out, 0, data.length);
        for (int i = data.length; i < out.length; i++) out[i] = (byte) p;
        return out;
    }

        private static long word(byte[] b, int at) {
        return ByteBuffer.wrap(b, at, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private static byte[] le(long n) {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(n).array();
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    private static String hexString(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) sb.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return sb.toString();
    }

    private static byte[] copyOf(byte[] data, int len) {
        byte[] out = new byte[len];
        System.arraycopy(data, 0, out, 0, Math.min(len, data.length));
        return out;
    }

    private static byte[] copyOfRange(byte[] data, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(data, from, out, 0, to - from);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    private static byte[] reverse(byte[] data) {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) out[i] = data[data.length - 1 - i];
        return out;
    }
}
