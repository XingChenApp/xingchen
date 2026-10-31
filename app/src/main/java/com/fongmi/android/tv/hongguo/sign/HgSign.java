package com.fongmi.android.tv.hongguo.sign;

import com.fongmi.android.tv.api.FqCommentSigner;
import com.fongmi.android.tv.hongguo.api.HgDevice;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字节系请求签名（X-Gorgon/X-Argus/X-Helios/X-Medusa/X-Ladon/X-Khronos/X-SS-STUB）：
 * 逐行移植 openhongguo internal/protocol/sign/（request/gorgon/helios/medusa/f13/aes/
 * prim/proto），纯算法无 native 依赖。定罪依据 openhongguo-dig-20261028.md §2/§3：
 * 无签名＋假指纹的请求被网关回 200 空体，签名头才是门。例外：X-Argus/X-Ladon
 * 自 2026-10-29 起改走树内 {@link FqCommentSigner} 的真算法（见 signRequest 内注），
 * 不再是 openhongguo 的时间戳占位。
 *
 * <p>正确性锚点＝Go 侧 sign_test.go 的参考向量（gorgon/helios/keyHash/xmxor/medusa），
 * 离线单测 HgSignTest 逐条对拍。数值常量在 {@link HgSignConsts}（脚本从 Go 源码提取）。</p>
 */
public final class HgSign {

    private static final int GORGON_SDK_VERSION = 67503104;
    /** metasec 构建串：medusa 消息字段 8 与 argus 消息字段 8 同值同源（同一构建自称）。 */
    private static final String METASEC_SDK_VERSION = "v04.06.04-ml-android";
    static final String APP_ID = "8662";
    static final String CHANNEL_ID = "1588093228";

    private HgSign() {
    }

    /** 签名熵源（对照 Go Entropy；random 类字段为 uint32 语义，存 int）。 */
    public static final class Entropy {
        public long envLaunch;
        public long envPid;
        public long nowSecs;
        public int messageRandom;
        public int random;
        public int packedRandom;
        public int gorgonRandom;
        public int heliosRandom;
    }

    /** 一次签名的产物：最终查询串（已含 ts 与 _rticket）＋签名头集。 */
    public static final class Signed {
        public final String query;
        public final Map<String, String> headers;

        Signed(String query, Map<String, String> headers) {
            this.query = query;
            this.headers = headers;
        }
    }

    /** POST 签名：body 必须与实际发送的字节逐字一致（签名覆盖 body MD5）。 */
    public static Signed signPost(String path, List<String[]> pairs, long nowMillis,
                                  String deviceId, String versionName, byte[] body, Entropy entropy) {
        return signRequest(path, pairs, nowMillis, deviceId, versionName, body, entropy);
    }

    /**
     * GET 签名：path 由调用方传真实请求路径（定罪账 2026-10-28 点名——旧版把
     * 签名内路径写死 search/tab，feed 频道面 GET 若照此签会被服务端拒）。
     * 注：Go 原版 SignGET 同样写死该路径且 ohg 实跑能通，两种签法哪种被
     * 服务端接受以本机探针 A/B 实证为准，证据见 fix-batch-1455.md。
     */
    public static Signed signGet(String path, List<String[]> pairs, long nowMillis,
                                 String deviceId, String versionName, Entropy entropy) {
        return signRequest(path, pairs, nowMillis, deviceId, versionName, null, entropy);
    }

    private static Signed signRequest(String path, List<String[]> pairs, long nowMillis,
                                      String deviceId, String versionName, byte[] body, Entropy entropy) {
        Entropy ent = entropy != null ? entropy : drawEntropy(nowMillis);
        boolean hasBody = body != null;
        byte[] bodyMD5 = new byte[16];
        if (hasBody) bodyMD5 = md5Raw(body);
        long ticket = nowMillis;
        int khronos = 0;
        String query = null;
        // SM3 分支试探回路：branch==1 时 ticket 自增重试，至多 32 次（对照 Go 原码）。
        for (long offset = 0; offset < 32; offset++) {
            ticket = nowMillis + offset;
            khronos = (int) (ticket / 1000);
            List<String[]> withTicket = new ArrayList<>(pairs);
            withTicket.add(new String[]{"ts", String.valueOf(khronos & 0xFFFFFFFFL)});
            withTicket.add(new String[]{"_rticket", String.valueOf(ticket)});
            query = encodeQuery(withTicket);
            byte[] sum = HgSm3.sum(query.getBytes(StandardCharsets.UTF_8));
            if (branchOf(sum, bodyMD5, le32(khronos)) != 1) break;
        }
        if (ent.nowSecs == 0) ent.nowSecs = khronos & 0xFFFFFFFFL;
        // medusa 的哈希 URL 宿主写死 reading.snssdk.com，不随实际请求宿主变（Go 原码注）。
        String rawURL = "https://reading.snssdk.com" + path + "?" + query;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json; charset=utf-8,application/x-protobuf");
        headers.put("x-xs-from-web", "0");
        headers.put("x-ss-req-ticket", String.valueOf(ticket));
        headers.put("x-tt-request-tag", "t=0;n=0");
        headers.put("sdk-version", "2");
        headers.put("passport-sdk-version", "50561");
        headers.put("x-vc-bdturing-sdk-version", "3.7.2.cn");
        headers.put("x-khronos", String.valueOf(khronos & 0xFFFFFFFFL));
        // x-argus/x-ladon（2026-10-29 改）：改走 FqCommentSigner 的真算法
        // （argus＝protobuf＋Simon＋AES、ladon＝轮函数展开，与评论签名同一实现，
        // 不另抄一份）。此前是 Base64(时间戳) 占位——签名门不拦占位值，但 passport
        // 两路登录（短信/抖音兑换）都在最后一跳被风控以「为保证账号安全，暂不
        // 支持此操作」收口，占位签名是第一嫌疑（定根账 sms-login-fail-20261029.md
        // 第五节）。nonce 从本次签名熵派生：同熵同刻产出确定（离线单测的头集
        // 确定性断言靠它），熵本身每次现抽随机。
        // 身份分轨（2026-10-29 深夜，login-close-loop-20261029.md 第四-1 路 A）：
        // 真算法的消息自称的是抓样来源番茄（aid 3019 / 6.8.1.32 / v04.07.01），
        // 与本请求的 medusa（8662 / 本档案版本名 / v04.06.04 / 67503104）及公参
        // 正面矛盾——风控层是全链唯一会解密细看 argus 的地方。此处按红果身份
        // 组装（aid、版本名与同请求 medusa 字段 4/7 同源、SDK 串/构建号与
        // medusa 字段 8/9 同源），评论链的番茄身份在 FqCommentSigner 内保留不动。
        byte[] argusNonce = new byte[11];
        System.arraycopy(le32(ent.messageRandom), 0, argusNonce, 0, 4);
        System.arraycopy(le32(ent.random), 0, argusNonce, 4, 4);
        System.arraycopy(le32(ent.packedRandom), 0, argusNonce, 8, 3);
        String[] argusLadon = FqCommentSigner.argusLadon(query, body, khronos & 0xFFFFFFFFL, argusNonce,
                new FqCommentSigner.Identity(APP_ID, versionName, METASEC_SDK_VERSION, GORGON_SDK_VERSION));
        headers.put("x-ladon", argusLadon[1]);
        headers.put("x-argus", argusLadon[0]);
        headers.put("x-gorgon", xGorgon(query, body, hasBody, khronos, ent.gorgonRandom));
        headers.put("x-helios", assembleHelios(khronos, ent.heliosRandom));
        headers.put("x-medusa", assembleMedusa(rawURL, body, hasBody, khronos, deviceId, versionName, ent));
        // x-tt-dt：值＝device_register 回包下发的 device_token——官方链
        // 是注册 SDK 从回包读出、落本地偏好、每请求以此头带出（Java 层
        // 全程可抠，两候选原生库 strings 里根本没这串字；定根账
        // login-ticket-dig-20261029.md 第一节）。旧版 HgDevice 解析注册
        // 回包时把这个字段扔了、这里只能恒发空串；现签名时现读档案现值
        // （与 deviceId 同源），档案还没刷到 token（存量旧档案补刷在途）
        // 时仍为空串，不拿假值糊弄。
        headers.put("x-tt-dt", HgDevice.deviceToken());
        if (hasBody) {
            headers.put("Content-Type", "application/json; charset=UTF-8");
            headers.put("x-ss-stub", hexUpper(md5Raw(body)));
        }
        return new Signed(query, headers);
    }

    /** Content-MD5 大写 hex（注册口 X-SS-STUB 与签名 x-ss-stub 同口径）。 */
    public static String contentMd5Hex(byte[] body) {
        return hexUpper(md5Raw(body));
    }

    private static Entropy drawEntropy(long nowMillis) {
        byte[] buf = new byte[16];
        new SecureRandom().nextBytes(buf);
        long n = le64(buf, 0);
        Entropy e = new Entropy();
        e.envLaunch = 100 + (n % 21);
        e.envPid = 10001 + (le16(buf, 8) % 2000);
        e.nowSecs = nowMillis / 1000;
        e.messageRandom = le32i(buf, 0);
        e.random = le32i(buf, 4);
        e.packedRandom = le32i(buf, 8);
        e.gorgonRandom = le16(buf, 12);
        e.heliosRandom = le32i(buf, 12);
        return e;
    }

    private static String encodeQuery(List<String[]> pairs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairs.size(); i++) {
            if (i > 0) sb.append('&');
            sb.append(encodeComponent(pairs.get(i)[0])).append('=').append(encodeComponent(pairs.get(i)[1]));
        }
        return sb.toString();
    }

    private static final String UNRESERVED = "-_.!~*'()";
    private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();

    private static String encodeComponent(String s) {
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || UNRESERVED.indexOf(c) >= 0;
            if (ok) {
                sb.append((char) c);
            } else {
                sb.append('%').append(HEX_UPPER[c >>> 4]).append(HEX_UPPER[c & 15]);
            }
        }
        return sb.toString();
    }

    // ---------- prim（对照 prim.go） ----------

    static int rol32(int value, int count) {
        return Integer.rotateLeft(value, count);
    }

    static int ror32(int value, int count) {
        return Integer.rotateRight(value, count);
    }

    static long ror64(long value, long count) {
        return Long.rotateRight(value, (int) count);
    }

    static int reverseBits(int value) {
        int result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 1) | ((value >>> i) & 1);
        }
        return result & 0xFF;
    }

    static long zigzag(long value) {
        return (value << 1) ^ (value >> 63);
    }

    static byte[] le32(int value) {
        return new byte[]{(byte) value, (byte) (value >>> 8), (byte) (value >>> 16), (byte) (value >>> 24)};
    }

    static byte[] be32(int value) {
        return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    static byte[] md5Raw(byte[] value) {
        try {
            return MessageDigest.getInstance("MD5").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static int getIV(int iv, byte[] data) {
        int value = iv;
        for (int i = 0; i < data.length; i++) {
            int b = data[i] & 0xFF;
            if ((i & 1) == 0) {
                value = (value >>> 4) ^ value ^ (value << 6) ^ b;
            } else {
                int inner = (value >>> 7) ^ value ^ (b | (value << 12));
                value = ~inner;
            }
        }
        return value;
    }

    static int sumMD5(byte[] data) {
        int check = 0x20220420;
        for (int i = 0; i < 12; i++) {
            int temp;
            if ((i & 1) == 0) {
                temp = (check >>> 3) ^ check;
                check = (data[i] & 0xFF) ^ (check << 7);
            } else {
                temp = (check >>> 5) ^ check;
                check = (data[i] & 0xFF) | (check << 11);
                check = ~check;
            }
            check ^= temp;
        }
        return (check | 4) ^ 0x01000000;
    }

    static int le32i(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    static int le16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    static long le64(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) v |= (long) (b[off + i] & 0xFF) << (8 * i);
        return v;
    }

    public static byte[] hexDecode(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    static String hexLower(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        }
        return sb.toString();
    }

    static String hexUpper(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(HEX_UPPER[(b >>> 4) & 15]).append(HEX_UPPER[b & 15]);
        }
        return sb.toString();
    }

    // ---------- gorgon（对照 gorgon.go；RC4 变体：KSA 只赋值不交换、PRGA 的 j 只加不取模） ----------

    private static byte[] rc4Gorgon(byte[] data, byte[] key) {
        int[] s = new int[256];
        for (int i = 0; i < 256; i++) s[i] = i;
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + s[i] + (key[i % key.length] & 0xFF)) & 255;
            s[i] = s[j];
        }
        byte[] out = new byte[data.length];
        int i = 0;
        j = 0;
        for (int n = 0; n < data.length; n++) {
            i++;
            int x = s[i & 255];
            j += x;
            int y = s[j & 255];
            s[i & 255] = y;
            out[n] = (byte) ((data[n] & 0xFF) ^ s[(y + y) & 255]);
        }
        return out;
    }

    public static String xGorgon(String query, byte[] body, boolean hasBody, int khronos, int random) {
        byte[] queryMD5 = md5Raw(query.getBytes(StandardCharsets.UTF_8));
        byte[] bodyMD5 = new byte[16];
        if (hasBody) bodyMD5 = md5Raw(body);
        ByteArrayOutputStream input = new ByteArrayOutputStream(20);
        input.write(queryMD5, 0, 4);
        input.write(bodyMD5, 0, 4);
        input.write(new byte[4], 0, 4);
        byte[] v = le32(GORGON_SDK_VERSION);
        input.write(v, 0, 4);
        v = be32(khronos);
        input.write(v, 0, 4);
        byte[] key = {0x4a, 0x40, 0x16, (byte) ((random >>> 8) & 255), 0x47, 0x6c, 0x01, (byte) (random & 255)};
        byte[] out = rc4Gorgon(input.toByteArray(), key);
        int n = out.length;
        for (int i = 0; i < n; i++) {
            int value = out[i] & 0xFF;
            int swapped = ((value >>> 4) | (value << 4)) & 0xFF;
            int next = i + 1 < n ? out[i + 1] & 0xFF : out[0] & 0xFF;
            out[i] = (byte) (~(reverseBits(next ^ swapped) ^ 20) & 0xFF);
        }
        byte[] result = new byte[6 + n];
        result[0] = (byte) 0x84;
        result[1] = 0x04;
        result[2] = (byte) (random & 255);
        result[3] = (byte) ((random >>> 8) & 255);
        result[4] = 0x40;
        result[5] = 0x01;
        System.arraycopy(out, 0, result, 6, n);
        return hexLower(result);
    }

    // ---------- helios（对照 helios.go） ----------

    public static String assembleHelios(int khronos, int random) {
        byte[] seed = new byte[4 + APP_ID.length()];
        System.arraycopy(le32(random), 0, seed, 0, 4);
        System.arraycopy(APP_ID.getBytes(StandardCharsets.US_ASCII), 0, seed, 4, APP_ID.length());
        byte[] ascii = hexLower(md5Raw(seed)).getBytes(StandardCharsets.US_ASCII);
        long[] words = new long[4];
        for (int i = 0; i < 4; i++) words[i] = le64(ascii, i * 8);
        long[] table = diffuse(words);
        byte[] padded = heliosPad(khronos);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int off = 0; off < padded.length; off += 16) {
            long a = le64(padded, off);
            long b = le64(padded, off + 8);
            for (int t = 0; t < 34; t++) {
                b = table[t] ^ (a + ror64(b, 8));
                a = b ^ ror64(a, 61);
            }
            for (int i = 0; i < 8; i++) output.write((byte) (a >>> (8 * i)));
            for (int i = 0; i < 8; i++) output.write((byte) (b >>> (8 * i)));
        }
        byte[] body = output.toByteArray();
        byte[] result = new byte[4 + body.length];
        System.arraycopy(le32(random), 0, result, 0, 4);
        System.arraycopy(body, 0, result, 4, body.length);
        return Base64.getEncoder().encodeToString(result);
    }

    private static long[] diffuse(long[] words) {
        List<Long> table = new ArrayList<>();
        table.add(words[0]);
        long b0 = words[0];
        long b8 = words[1];
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(words[2]);
        queue.add(words[3]);
        for (long i = 0; i < 34; i++) {
            long x8 = ror64(b8, 8) + b0;
            x8 ^= i;
            queue.add(x8);
            x8 ^= ror64(b0, 61);
            table.add(x8);
            b0 = x8;
            b8 = queue.removeFirst();
        }
        long[] out = new long[table.size()];
        for (int i = 0; i < out.length; i++) out[i] = table.get(i);
        return out;
    }

    private static byte[] heliosPad(int khronos) {
        byte[] text = ((khronos & 0xFFFFFFFFL) + "-" + CHANNEL_ID + "-" + APP_ID)
                .getBytes(StandardCharsets.US_ASCII);
        int fill = 16 - (text.length % 16);
        int size = ((text.length + 1 + 15) / 16) * 16;
        byte[] out = new byte[size];
        Arrays.fill(out, (byte) fill);
        System.arraycopy(text, 0, out, 0, text.length);
        return out;
    }

    // ---------- f13（对照 f13.go） ----------

    public static int branchOf(byte[] query, byte[] bodyMD5, byte[] ts) {
        int iv = getIV(getIV(getIV(0x20230928, query), bodyMD5), ts);
        int low = iv & 15;
        return low - ((low * 171) >>> 9) * 3;
    }

    static byte[] hashF13(byte[] querySM3, byte[] bodyMD5, byte[] ts, int khronos) {
        int iv = getIV(getIV(getIV(0x20230928, querySM3), bodyMD5), ts);
        int low = iv & 15;
        int ivV0 = (low * 171) >>> 9;
        int branch = low - ivV0 * 3;
        if (branch == 2) {
            return branch2F13(iv, querySM3, bodyMD5, ts, khronos);
        }
        if (branch != 0) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(36);
            out.write(querySM3, 0, 16);
            out.write(bodyMD5, 0, bodyMD5.length);
            byte[] summed = new byte[querySM3.length + bodyMD5.length];
            System.arraycopy(querySM3, 0, summed, 0, querySM3.length);
            System.arraycopy(bodyMD5, 0, summed, querySM3.length, bodyMD5.length);
            byte[] s = le32(sumMD5(summed));
            out.write(s, 0, 4);
            return out.toByteArray();
        }
        return branch0F13(querySM3, bodyMD5, ts, khronos, ivV0);
    }

    private static int[] expandWords(byte[] data) {
        List<Integer> di = new ArrayList<>(128);
        for (int off = 0; off + 4 <= data.length; off += 4) {
            di.add(be32i(data, off));
        }
        int di0 = di.get(0);
        for (int i = 0; i < 112; i++) {
            int di1 = di.get(i + 1);
            int di14 = di.get(i + 14);
            int r1 = rol32(di1, 14) ^ rol32(di1, 25) ^ (di1 >>> 3);
            int r2 = rol32(di14, 13) ^ rol32(di14, 15) ^ (di14 >>> 10);
            di.add(di0 + di.get(i + 9) + r1 + r2);
            di0 = di1;
        }
        int[] out = new int[di.size()];
        for (int i = 0; i < out.length; i++) out[i] = di.get(i);
        return out;
    }

    private static int be32i(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static int[] mixRounds(int[] stateIn, int[] rounds, int[] di, int[] tt, int ivV1) {
        int[] state = stateIn;
        for (int i = 0; i < rounds[0]; i++) {
            int base = ivV1 + i;
            int w = di[base & 127];
            int n1 = ((state[rounds[3]] ^ state[rounds[4]]) & state[rounds[1]]) ^ state[rounds[3]];
            int n2 = rol32(state[rounds[1]], 26) ^ rol32(state[rounds[1]], 21) ^ rol32(state[rounds[1]], 7);
            int n4 = w + n1 + n2 + tt[base & 63] + state[rounds[5]];
            int n5 = rol32(state[rounds[2]], 30) ^ rol32(state[rounds[2]], 19) ^ rol32(state[rounds[2]], 10);
            int n6 = (state[rounds[2]] & state[rounds[6]]) | ((state[rounds[2]] | state[rounds[6]]) & state[rounds[7]]);
            int n7 = n5 + n6;
            int old = state[rounds[9]];
            int last = state[state.length - 1];
            int[] next = new int[state.length];
            next[0] = last;
            System.arraycopy(state, 0, next, 1, state.length - 1);
            state = next;
            state[rounds[10]] = n7 + n4;
            state[rounds[8]] = old + n4;
        }
        return state;
    }

    private static byte[] branch0F13(byte[] querySM3, byte[] bodyMD5, byte[] ts, int khronos, int ivV0) {
        int ivV1 = HgSignConsts.BRANCH0_IV[Math.min(ivV0, HgSignConsts.BRANCH0_IV.length - 1)];
        int countV1 = (ivV1 + khronos + 1) & 255;
        int countV2 = ivV1 + khronos;
        int[] tt = new int[HgSignConsts.BRANCH0_TT.length];
        for (int i = 0; i < tt.length; i++) tt[i] = ror32(HgSignConsts.BRANCH0_TT[i], countV1);
        int n0 = (countV2 + 2) & 7;
        byte[] seed = {(byte) 0xfa, 0x45, 0x61, (byte) 0xd7};
        byte[] pad = new byte[seed.length];
        for (int i = 0; i < seed.length; i++) {
            int v = seed[i] & 0xFF;
            int combined = (v | (v << 8)) & 0xFFFF;
            pad[i] = (byte) ((combined >>> n0) & 255);
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream(64);
        data.write(querySM3, 0, querySM3.length);
        data.write(bodyMD5, 0, bodyMD5.length);
        data.write(ts, 0, ts.length);
        data.write(pad, 0, pad.length);
        byte[] tail = hexDecode("00000000000001a0");
        data.write(tail, 0, tail.length);
        int[] di = expandWords(data.toByteArray());
        int[] init = new int[HgSignConsts.BRANCH0_INIT.length];
        for (int i = 0; i < init.length; i++) init[i] = ror32(HgSignConsts.BRANCH0_INIT[i], countV2 & 31);
        int[] d = mixRounds(Arrays.copyOf(init, init.length), HgSignConsts.BRANCH0_ROUNDS[ivV0], di, tt, ivV1);
        byte[] ret = new byte[32];
        for (int i = 0; i < 8; i++) {
            int val = d[i] + init[i];
            ret[i * 4] = (byte) (val >>> 24);
            ret[i * 4 + 1] = (byte) (val >>> 16);
            ret[i * 4 + 2] = (byte) (val >>> 8);
            ret[i * 4 + 3] = (byte) val;
        }
        for (int i = 0; i < 16; i++) ret[i] ^= ret[16 + i];
        ByteArrayOutputStream out = new ByteArrayOutputStream(20);
        out.write(ret, 0, 16);
        byte[] s = le32(sumMD5(Arrays.copyOf(ret, 16)));
        out.write(s, 0, 4);
        return out.toByteArray();
    }

    private static int md5V3Step(int kind, int a, int b, int c, int d, int m, int shift, int constant) {
        int f;
        switch (kind) {
            case 0:
                f = (b & c) | (~b & d);
                break;
            case 1:
                f = (b & d) | (c & ~d);
                break;
            case 2:
                f = b ^ c ^ d;
                break;
            default:
                f = c ^ (b | ~d);
                break;
        }
        return b + rol32(a + f + m + constant, shift);
    }

    private static byte[] md5SumV3(byte[] message, int countV2, byte[] orders, int countV1) {
        int[] sv = new int[HgSignConsts.BRANCH2_SV.length];
        for (int i = 0; i < sv.length; i++) sv[i] = ror32(HgSignConsts.BRANCH2_SV[i], countV1);
        int[] start = {
                ror32(0x79e0f2fb, countV2),
                ror32(0xc8b52570, countV2),
                ror32(0xebc2f8cd, countV2),
                ror32(0x7c104d93, countV2),
        };
        int endCount = (countV2 + 6) & 255;
        int[] end = {
                ror32(0x19be4866, endCount),
                ror32(0xe85986b4, endCount),
                ror32(0xe19b326e, endCount),
                ror32(0x71d1d7d4, endCount),
        };
        int[] m = new int[16];
        for (int i = 0; i < 16; i++) m[i] = le32i(message, i * 4);
        int[] st = {start[0], start[1], start[2], start[3]};
        md5V3Run(st, 0, new int[]{7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22}, 0, m, orders, sv);
        md5V3Run(st, 1, new int[]{5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20}, 16, m, orders, sv);
        md5V3Run(st, 2, new int[]{4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23}, 32, m, orders, sv);
        md5V3Run(st, 3, new int[]{6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21}, 48, m, orders, sv);
        ByteArrayOutputStream ret = new ByteArrayOutputStream(20);
        for (int i = 0; i < 4; i++) {
            byte[] w = le32((start[i] + st[i]) ^ end[i]);
            ret.write(w, 0, 4);
        }
        byte[] r16 = ret.toByteArray();
        byte[] s = le32(sumMD5(r16));
        ret.write(s, 0, 4);
        return ret.toByteArray();
    }

    /** st = {a,b,c,d}，就地轮转（对照 md5SumV3 的 run 闭包相位语义）。 */
    private static void md5V3Run(int[] st, int kind, int[] shifts, int offset,
                                 int[] m, byte[] orders, int[] sv) {
        for (int i = 0; i < 16; i++) {
            int phase = i & 3;
            int w = m[orders[offset + i] & 0xFF];
            int k = sv[offset + i];
            switch (phase) {
                case 0:
                    st[0] = md5V3Step(kind, st[0], st[1], st[2], st[3], w, shifts[i], k);
                    break;
                case 1:
                    st[3] = md5V3Step(kind, st[3], st[0], st[1], st[2], w, shifts[i], k);
                    break;
                case 2:
                    st[2] = md5V3Step(kind, st[2], st[3], st[0], st[1], w, shifts[i], k);
                    break;
                default:
                    st[1] = md5V3Step(kind, st[1], st[2], st[3], st[0], w, shifts[i], k);
                    break;
            }
        }
    }

    private static byte[] branch2F13(int iv, byte[] querySM3, byte[] bodyMD5, byte[] ts, int khronos) {
        int v = (iv & 13) * 86;
        int ivV0 = ((v >>> 15) & 255) + ((v >>> 8) & 255);
        int n1 = HgSignConsts.BRANCH2_N1S[Math.min(ivV0, HgSignConsts.BRANCH2_N1S.length - 1)];
        int countV1 = (n1 + khronos + 1) & 255;
        int countV2 = n1 + khronos;
        int shift = (countV2 + 5) & 7;
        byte[] seed = {(byte) 0x84, (byte) 0x96, 0x77, (byte) 0x9d, (byte) 0xd4, 0x15, 0x0b, (byte) 0xf8};
        byte[] pad = new byte[seed.length];
        for (int i = 0; i < seed.length; i++) {
            int value = seed[i] & 0xFF;
            int combined = (value | (value << 8)) & 0xFFFF;
            pad[i] = (byte) ((combined >>> shift) & 255);
        }
        ByteArrayOutputStream input = new ByteArrayOutputStream(64);
        input.write(querySM3, 0, querySM3.length);
        input.write(bodyMD5, 0, bodyMD5.length);
        input.write(ts, 0, ts.length);
        input.write(pad, 0, pad.length);
        byte[] tail = hexDecode("a0010000");
        input.write(tail, 0, tail.length);
        byte[] all = hexDecode(HgSignConsts.BRANCH2_ORDERS_HEX);
        int begin = ivV0 << 6;
        byte[] orders = Arrays.copyOfRange(all, begin, begin + 64);
        return md5SumV3(input.toByteArray(), countV2, orders, countV1);
    }

    // ---------- AES 变体（对照 aes.go，非标准 AES：密钥扩展与轮函数均按原码） ----------

    private static final class AesV3 {
        private final byte[] box;
        private final int[] con2;
        private final int[] order;
        private final byte[][] keys;

        AesV3(byte[] key, int khronos) {
            int word = khronos & 3;
            byte[] all = Base64.getDecoder().decode(HgSignConsts.SBOX_B64);
            this.box = Arrays.copyOfRange(all, word * 256, word * 256 + 256);
            this.con2 = HgSignConsts.AES_CON2[word];
            this.order = HgSignConsts.AES_ORDER[word];
            this.keys = expandAES(key, word);
        }

        private static byte[][] expandAES(byte[] key, int word) {
            int init = HgSignConsts.AES_INIT_WORD[word];
            byte[] all = Base64.getDecoder().decode(HgSignConsts.SBOX_B64);
            byte[] box = Arrays.copyOfRange(all, word * 256, word * 256 + 256);
            byte[] initial = new byte[16];
            for (int i = 0; i < 4; i++) System.arraycopy(le32(init), 0, initial, i * 4, 4);
            byte[] expanded = new byte[48];
            for (int i = 0; i < 16; i++) expanded[i] = (byte) (initial[i] ^ key[i % key.length]);
            int rounds = 8;
            for (int i = 4; i < 12; i++) {
                int at = 4 * (i - 1);
                int a = expanded[at] & 0xFF;
                int b = expanded[at + 1] & 0xFF;
                int c = expanded[at + 2] & 0xFF;
                int d = expanded[at + 3] & 0xFF;
                if ((i & 3) == 0) {
                    int t = ((init >>> (rounds & 24)) ^ (box[b] & 0xFF)) & 255;
                    b = box[c] & 0xFF;
                    c = box[d] & 0xFF;
                    d = box[a] & 0xFF;
                    a = t;
                }
                rounds += 2;
                expanded[at + 4] = (byte) (a ^ (expanded[at - 12] & 0xFF));
                expanded[at + 5] = (byte) (b ^ (expanded[at - 11] & 0xFF));
                expanded[at + 6] = (byte) (c ^ (expanded[at - 10] & 0xFF));
                expanded[at + 7] = (byte) (d ^ (expanded[at - 9] & 0xFF));
            }
            byte[][] keys = new byte[12][4];
            for (int i = 0; i < 12; i++) System.arraycopy(expanded, i * 4, keys[i], 0, 4);
            return keys;
        }

        private static void addCon(int[][] state, byte[][] keys, int from, int[] con2) {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    state[i][j] ^= keys[from + i][con2[j]] & 0xFF;
                }
            }
        }

        private void sub(int[][] state) {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    state[i][j] = box[state[i][j]] & 0xFF;
                }
            }
            int[][] old = new int[4][];
            for (int i = 0; i < 4; i++) old[i] = state[i].clone();
            for (int i = 0; i < 4; i++) state[i] = old[con2[i]];
        }

        private void shift(int[][] state) {
            int[] flat = new int[16];
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) flat[i * 4 + j] = state[i][j];
            }
            for (int i = 0; i < 16; i++) {
                state[i / 4][i % 4] = flat[order[i]];
            }
        }

        private static void shiftCon(int[][] state, int[] con2) {
            int[][] old = new int[4][];
            for (int i = 0; i < 4; i++) old[i] = state[i].clone();
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) state[i][j] = old[i][con2[j]];
            }
        }

        private static int xt(int x) {
            int v = (x << 1) & 0xFF;
            if ((x & 128) != 0) v ^= 0x1b;
            return v;
        }

        private static void mixColumns(int[][] state) {
            for (int i = 0; i < 4; i++) {
                int t = state[0][i] ^ state[1][i] ^ state[2][i] ^ state[3][i];
                int u = state[0][i];
                state[0][i] ^= t ^ xt(state[0][i] ^ state[1][i]);
                state[1][i] ^= t ^ xt(state[1][i] ^ state[2][i]);
                state[2][i] ^= t ^ xt(state[2][i] ^ state[3][i]);
                state[3][i] ^= t ^ xt(state[3][i] ^ u);
            }
        }

        byte[] block(byte[] value) {
            int[][] state = new int[4][4];
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) state[i][j] = value[i * 4 + j] & 0xFF;
            }
            addCon(state, keys, 0, con2);
            for (int i = 1; i < 3; i++) {
                sub(state);
                shift(state);
                if (i == 1) {
                    shiftCon(state, con2);
                    mixColumns(state);
                }
                addCon(state, keys, i * 4, con2);
            }
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    state[i][j] ^= keys[4 + i][j] & 0xFF;
                }
            }
            byte[] out = new byte[16];
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) out[i * 4 + j] = (byte) state[i][j];
            }
            return out;
        }

        byte[] encrypt(byte[] data, byte[] iv) {
            byte[] source = Arrays.copyOf(data, data.length);
            byte[] plaintext = new byte[32];
            for (int i = 0; i < 31; i++) {
                int at = i * 8;
                int n0 = ((source[at] & 0xFF) >>> 4) & 2;
                int n1 = n0 | ((source[at + 1] & 0xFF) & 64);
                int n2 = n1 | (((source[at + 2] & 0xFF) >>> 2) & 1);
                int n3 = n2 | (((source[at + 3] & 0xFF) << 3) & 128);
                int n4 = n3 | (((source[at + 4] & 0xFF) >>> 1) & 4);
                int n5 = n4 | (((source[at + 5] & 0xFF) << 3) & 16);
                int n6 = n5 | (((source[at + 6] & 0xFF) << 5) & 32);
                plaintext[i] = (byte) (n6 | (((source[at + 7] & 0xFF) >>> 4) & 8));
            }
            plaintext[31] = 1;
            byte[] blocks = new byte[32];
            byte[] previous = Arrays.copyOf(iv, iv.length);
            for (int off = 0; off < 32; off += 16) {
                byte[] chunk = new byte[16];
                for (int i = 0; i < 16; i++) chunk[i] = (byte) (plaintext[off + i] ^ previous[i]);
                byte[] value = block(chunk);
                System.arraycopy(value, 0, blocks, off, 16);
                previous = value;
            }
            byte[] out = source;
            for (int i = 0; i < 31; i++) {
                int at = i * 8;
                int k = blocks[i] & 0xFF;
                out[at] = (byte) (((out[at] & 0xFF) & 0xdf) | ((k << 4) & 32));
                out[at + 1] = (byte) (((out[at + 1] & 0xFF) & 0xbf) | (k & 64));
                out[at + 2] = (byte) (((out[at + 2] & 0xFF) & 0xfb) | ((k << 2) & 4));
                out[at + 3] = (byte) (((out[at + 3] & 0xFF) & 0xef) | ((k >>> 3) & 16));
                out[at + 4] = (byte) (((out[at + 4] & 0xFF) & 0xf7) | ((k + k) & 8));
                out[at + 5] = (byte) (((out[at + 5] & 0xFF) & 0xfd) | ((k >>> 3) & 2));
                out[at + 6] = (byte) (((out[at + 6] & 0xFF) & 0xfe) | ((k >>> 5) & 1));
                out[at + 7] = (byte) (((out[at + 7] & 0xFF) & 0x7f) | ((k << 4) & 128));
            }
            byte[] result = new byte[1 + out.length];
            result[0] = blocks[31];
            System.arraycopy(out, 0, result, 1, out.length);
            return result;
        }
    }

    // ---------- proto（对照 proto.go；protoBytes 空体不写字段） ----------

    private static byte[] varint(long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long v = value;
        while (v > 127 || v < 0) {
            out.write((byte) ((v & 127) | 128));
            v >>>= 7;
        }
        out.write((byte) v);
        return out.toByteArray();
    }

    private static byte[] protoKey(int tag, int wire) {
        return varint(((long) tag << 3) | wire);
    }

    private static byte[] protoBytes(int tag, byte[] body) {
        if (body == null || body.length == 0) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] k = protoKey(tag, 2);
        out.write(k, 0, k.length);
        byte[] len = varint(body.length);
        out.write(len, 0, len.length);
        out.write(body, 0, body.length);
        return out.toByteArray();
    }

    private static byte[] protoString(int tag, String s) {
        return protoBytes(tag, s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] protoSInt(int tag, long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] k = protoKey(tag, 0);
        out.write(k, 0, k.length);
        byte[] v = varint(zigzag(value));
        out.write(v, 0, v.length);
        return out.toByteArray();
    }

    private static byte[] protoFloat(int tag, float value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] k = protoKey(tag, 5);
        out.write(k, 0, k.length);
        int bits = Float.floatToIntBits(value);
        out.write(bits & 0xFF);
        out.write((bits >>> 8) & 0xFF);
        out.write((bits >>> 16) & 0xFF);
        out.write((bits >>> 24) & 0xFF);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p, 0, p.length);
        return out.toByteArray();
    }

    // ---------- medusa（对照 medusa.go） ----------

    public static byte[] xmxor(byte[] data, byte[] key) {
        int n = data.length;
        int[] encoded = new int[n];
        for (int i = 0; i < n; i++) {
            int at = (i * 4) & 28;
            int d0 = key[at] & 0xFF;
            int d1 = key[at + 1] & 0xFF;
            int v = data[i] & 0xFF;
            int d2 = ((((v << 4) & 0xFF) | (v >>> 4)) + d0) & 0xFF;
            d2 = ((~d2) & 0xFF) ^ d1;
            d2 = ((d2 << 3) | (d2 >>> 5)) & 0xFF;
            d2 = (d2 + d1) & 0xFF;
            d2 ^= d0;
            encoded[n - i - 1] = (~d2) & 0xFF;
        }
        int last = encoded[n - 1] ^ encoded[n - 2];
        int first = encoded[0];
        encoded[0] = (((~last) & 0xFF) + first) & 0xFF;
        encoded[1] = ((encoded[0] ^ encoded[n - 1] ^ 254) + encoded[1]) & 0xFF;
        int shifted = ((encoded[1] << 3) | (encoded[1] >>> 5)) & 0xFF;
        int fix2 = ((last - first) & 0xFF) ^ (shifted ^ 2);
        encoded[2] = (encoded[2] + fix2) & 0xFF;
        for (int i = 0; i < n - 4; i++) {
            int sh = ((encoded[i + 2] << 3) | (encoded[i + 2] >>> 5)) & 0xFF;
            int temp = sh ^ encoded[i + 1] ^ ((i + 3) & 0xFF);
            encoded[i + 3] = (((~temp) & 0xFF) + encoded[i + 3]) & 0xFF;
        }
        encoded[n - 1] ^= encoded[n - 2];
        long sum = 0;
        for (int i = 1; i < n; i++) sum += encoded[i];
        encoded[0] = ((encoded[0] ^ encoded[1]) + (int) (sum & 0xFF)) & 0xFF;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) out[i] = (byte) encoded[i];
        return out;
    }

    /** 返回 {hash(32B), seed(4B)}（对照 keyHash）。 */
    public static byte[][] keyHash(int random) {
        byte[] signKey = hexDecode(HgSignConsts.MEDUSA_SIGN_KEY_HEX);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(signKey, 0, signKey.length);
        byte[] r = le32(random);
        buf.write(r, 0, 4);
        buf.write(signKey, 0, signKey.length);
        byte[] hash = HgSm3.sum(buf.toByteArray());
        int d1 = (random >>> 16) & 255;
        int d2 = (d1 << 11) | ((random >>> 24) & 0xFF);
        d2 ^= d1 >>> 5;
        d2 ^= d1;
        d2 = ~d2;
        return new byte[][]{hash, le32(d2)};
    }

    /**
     * medusa 的设备描述（assembleMedusa env 段字段 12 的内嵌 proto）。
     *
     * <p>设备串自 2026-10-29 起对齐注册档案：机型/品牌/系统版本经
     * {@link HgDevice} 的 deviceModel/deviceBrand/deviceOsVersion 现取，
     * 与同一请求的注册体、公参、UA 是同一台（Xiaomi 23127PN0CC /
     * Android 14）。此前照 openhongguo 抓样写死 Redmi 25053RT47C /
     * Android 16——三层三台是源码可证的矛盾：x-tt-dt 补齐后真机短信
     * 登录仍被风控以「为保证账号安全，暂不支持此操作」拒，按预案第一
     * 刀只切这组矛盾（login-ticket-dig-20261029.md 第六节、
     * login-medusa-align-20261029.md）。字段 34（厂商位）照注册体
     * device_manufacturer=BRAND 的口径同取品牌常量。</p>
     *
     * <p>字段 25–28（装机/首启族时间戳）同刀处理：此前冻结在抓样时刻
     * 1728388016635（2024-10-07），与「刚注册、new_user=1」的档案正面
     * 冲突；现取签名时刻 nowMillis（调用方以 khronos 秒 ×1000 传入），
     * 字段 28 照样本形态 +2ms。</p>
     *
     * <p>字段 24 于 2026-10-29 闭环单改取 {@link HgDevice#deviceOsApi()}
     * （＝34，与本档案 Android 14 成对）：原抓样值 36 的定性证据链——
     * openhongguo sign_test.go 的参照抓样与 medusa 抓样出自同一台样机
     * （25053RT47C），其查询串 os_api=36 / os_version=16 与 medusa
     * 字段 24=36 / 字段 22="16" 各对一位；官方包 targetSdk=35 已排除
     * 「应用目标 SDK」这一竞争解释；字段 22/24 又同处设备身份段。
     * 保留 36 等于让 medusa 自称「Android 14 却 API 36」（不可能的
     * 设备），故按 os_api 改。证据等级与回退法见
     * login-close-loop-20261029.md。</p>
     *
     * <p>指纹族于 2026-10-29 根治单改按档案派生（hongguo-profile-
     * rootfix-20261029.md）：字段 5 的 token、字段 16–21 的传感器浮点、
     * 消息字段 16 的 token 与字段 24 JSON 的 un_h/fkd/pd 不再冻结抓样
     * 原值，改经 {@link HgDevice} 的 fp* 访问口按档案种子派生（同档案
     * 稳定、跨档案唯一）——全网档案共用一台样机的指纹正是合成设备被
     * 档案级限流的定根之一（feed-fail-1460-20261029.md）。字段 23/38
     * 的整数语义未证，仍冻结抓样值，残余风险记在根治账。</p>
     */
    public static byte[] deviceProto(String deviceId, long nowMillis) {
        // 旧「共享呈现作用域」（2026-10-30，hongguo-shared-profile-
        // impl-20261030.md）已整套摘除（共享面随 W-03 于 2026-10-31
        // 摘净）：HgDevice.medusaTsBase 现恒等、deviceManufacturer
        // ＝品牌、fp* 指纹族按自家档案种子派生，下文即自家口径本身
        // （HgSignTest 的档案对齐断言按此口径验）。
        long tsBase = HgDevice.medusaTsBase(nowMillis);
        return concat(
                protoSInt(1, 1),
                protoSInt(2, 2),
                protoString(3, APP_ID),
                protoString(4, deviceId),
                protoString(5, HgDevice.fpToken5()),
                protoString(6, "!noperm!"),
                protoSInt(7, -888888),
                protoSInt(8, -888888),
                protoSInt(9, 3),
                protoSInt(10, -888888),
                protoString(11, "!notset!"),
                protoString(12, "Asia/Shanghai,8"),
                protoString(13, "zh_CN"),
                protoSInt(14, 4),
                protoFloat(16, HgDevice.fpSensor(0)),
                protoFloat(17, HgDevice.fpSensor(1)),
                protoFloat(18, HgDevice.fpSensor(2)),
                protoFloat(19, HgDevice.fpSensor(3)),
                protoFloat(20, HgDevice.fpSensor(4)),
                protoFloat(21, HgDevice.fpSensor(5)),
                protoString(22, HgDevice.deviceOsVersion()),
                protoSInt(23, 41),
                protoSInt(24, HgDevice.deviceOsApi()),
                protoSInt(25, tsBase),
                protoSInt(26, tsBase),
                protoSInt(27, tsBase),
                protoSInt(28, tsBase + 2),
                protoSInt(29, -1),
                protoString(30, HgDevice.deviceModel()),
                protoString(31, HgDevice.deviceBrand()),
                protoString(32, HgDevice.deviceModel()),
                protoString(33, HgDevice.deviceModel()),
                protoString(34, HgDevice.deviceManufacturer()),
                protoString(35, HgDevice.deviceBrand()),
                protoString(36, HgDevice.deviceBrand()),
                protoSInt(38, 31));
    }

    public static String assembleMedusa(String rawURL, byte[] body, boolean hasBody, int khronos,
                                        String deviceId, String versionName, Entropy entropy) {
        byte[] bodyMD5 = new byte[16];
        if (hasBody) bodyMD5 = md5Raw(body);
        String queryRaw = "";
        int qi = rawURL.indexOf('?');
        if (qi >= 0) queryRaw = rawURL.substring(qi + 1);
        byte[] querySM3 = HgSm3.sum(queryRaw.getBytes(StandardCharsets.UTF_8));
        byte[] ts = le32(khronos);
        byte[] queryBodyTS = hashF13(querySM3, bodyMD5, ts, khronos);
        int check = ((querySM3[0] & 63) << 14) | 0x18000001 | ((queryBodyTS[0] & 63) << 8);

        byte[] nested = concat(protoSInt(1, 111), protoSInt(2, 10), protoSInt(3, 694367), protoSInt(5, 586952199));
        byte[] nested13 = concat(protoSInt(1, entropy.nowSecs), protoSInt(2, -2), protoSInt(4, 200));
        byte[] env = concat(
                protoSInt(1, entropy.envLaunch),
                protoSInt(2, 146331399),
                protoSInt(3, 146331396),
                protoSInt(5, 7),
                protoString(6, "v04.06.04.03-bugfix"),
                protoSInt(7, entropy.envPid),
                protoBytes(12, deviceProto(deviceId, (khronos & 0xFFFFFFFFL) * 1000L)),
                protoBytes(13, nested13),
                protoString(14, versionName));

        ByteArrayOutputStream qh = new ByteArrayOutputStream();
        byte[] qr = queryRaw.getBytes(StandardCharsets.UTF_8);
        qh.write(qr, 0, qr.length);
        qh.write(bodyMD5, 0, bodyMD5.length);
        byte[] none = "none".getBytes(StandardCharsets.US_ASCII);
        qh.write(none, 0, none.length);
        byte[] queryHash = HgSm3.sum(qh.toByteArray());

        byte[] fixed1 = hexDecode("f7e85ffad7d7dc3bd62ac87057cf6118");
        byte[] fixed2 = hexDecode("4001000000000000");
        byte[] message = concat(
                protoBytes(1, fixed1),
                protoSInt(2, 3),
                protoSInt(3, entropy.messageRandom & 0xFFFFFFFFL),
                protoString(4, APP_ID),
                protoString(5, deviceId),
                protoString(6, CHANNEL_ID),
                protoString(7, versionName),
                protoString(8, METASEC_SDK_VERSION),
                protoSInt(9, GORGON_SDK_VERSION),
                protoBytes(10, fixed2),
                protoSInt(12, khronos & 0xFFFFFFFFL),
                protoBytes(13, queryBodyTS),
                protoBytes(14, Arrays.copyOf(querySM3, 6)),
                protoBytes(15, nested),
                protoString(16, HgDevice.fpMsgToken()),
                protoSInt(17, khronos & 0xFFFFFFFFL),
                protoBytes(19, queryHash),
                protoString(20, "none"),
                protoSInt(21, 312),
                protoBytes(23, env),
                protoString(24, "{\"cmr\":16777216,\"cmr2\":16777216,\"un_h\":" + HgDevice.fpUnH()
                        + ",\"vpn\":0,\"kd\":0,\"fkd\":" + HgDevice.fpFkd()
                        + ",\"pd\":" + HgDevice.fpPd() + ",\"dyn\":\"\",\"do\":0,\"tk\":true}"));

        byte[][] hs = keyHash(entropy.random);
        byte[] hash = hs[0];
        byte[] seed = hs[1];
        byte[] transformed = xmxor(message, hash);
        byte[] withPrefix = new byte[fixed2.length + transformed.length];
        System.arraycopy(fixed2, 0, withPrefix, 0, fixed2.length);
        System.arraycopy(transformed, 0, withPrefix, fixed2.length, transformed.length);
        transformed = withPrefix;
        for (int i = 0, j = transformed.length - 1; i < j; i++, j--) {
            byte tmp = transformed[i];
            transformed[i] = transformed[j];
            transformed[j] = tmp;
        }
        for (int i = 0; i < transformed.length; i++) {
            transformed[i] ^= seed[(~i) & 3];
        }
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        packed.write(0x35);
        byte[] pr = le32(entropy.packedRandom);
        packed.write(pr, 0, 4);
        byte[] ck = le32(check);
        packed.write(ck, 0, 4);
        packed.write(transformed, 0, transformed.length);
        packed.write((entropy.random >>> 16) & 0xFF);
        packed.write((entropy.random >>> 24) & 0xFF);

        byte[] encrypted = new AesV3(hexDecode(HgSignConsts.MEDUSA_AES_KEY_HEX), khronos)
                .encrypt(packed.toByteArray(), hexDecode(HgSignConsts.MEDUSA_AES_IV_HEX));
        byte[] version = hexDecode("03000000f7e85ffad7d7dc3bd62ac87057cf6118");
        byte[] prefix = new byte[20];
        for (int i = 0; i < 5; i++) {
            int v = le32i(version, i * 4) ^ khronos;
            System.arraycopy(le32(v), 0, prefix, i * 4, 4);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(prefix, 0, 20);
        out.write(entropy.random & 0xFF);
        out.write((entropy.random >>> 8) & 0xFF);
        out.write(0);
        out.write(1);
        out.write(encrypted, 0, encrypted.length);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
