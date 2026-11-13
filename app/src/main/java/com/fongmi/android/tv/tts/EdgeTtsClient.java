package com.fongmi.android.tv.tts;

import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;


/**
 * Edge 大声朗读合成客户端（「阅读」生态在线朗读同款通路）。
 * <p>
 * 协议：wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1，
 * 先发 speech.config，再发 ssml；音频以二进制帧回传，turn.end 收尾。
 * 鉴权（TrustedClientToken / Sec-MS-GEC / Sec-MS-GEC-Version / ConnectionId）
 * <b>必须全拼进查询串</b>（浏览器形状）：微软服务端只认查询串，放 HTTP 头一律 403。
 * <p>
 * GEC 算法见 secMsGec()（edge-tts drm.py generate_sec_ms_gec_v2 的 Java 等价实现，
 * 输出 "<CS64 16位大写>-<SHA256 64位大写>"）。
 * <p>
 * 2026-10-19 教训两条：①迁 v2 时把 Sec-MS-GEC 抄成请求头，服务端拒握手 403；
 * ②api.msedgeservices.com 的 v1 路径是 Azure 订阅密钥口，不认浏览器 token，
 * 查询串形状在它上面照样 403——必须走 speech.platform.bing.com 的 edge/v1。
 * <p>
 * 失败一律抛 TtsException（人话）；引擎层有系统语音兜底（他 2026-10-27
 * 令「不许只押一家供应商」，原「不许回退系统声」自家禁令就此作废）。
 */
public class EdgeTtsClient {

    /** 给人看的失败文案（统一口径）。 */
    public static final String ERR_OFFLINE = "朗读服务连不上，请检查网络后重试";
    /** 握手走到服务器但被拒绝（如 403）：本地网络没问题，是服务端侧有变动。 */
    public static final String ERR_REJECTED = "朗读服务拒绝了连接，可能是服务端有变动，请稍后再试";
    /** 服务端有 HTTP 响应但非拒绝类错误。 */
    public static final String ERR_SERVER = "朗读服务暂时不可用，请稍后再试";
    /** 12 秒没等到合成结果（2026-10-28 由 45 秒压下：干挂比失败更伤，
     * 超时由引擎切系统语音兜底，不在这干等）。 */
    public static final String ERR_TIMEOUT = "朗读服务响应超时，请重试";
    /** 建连后中途断开且没拿到音频。 */
    public static final String ERR_BROKEN = "朗读服务中途断开了连接，请重试";
    /** 正常结束但音频为空。 */
    public static final String ERR_EMPTY = "朗读服务返回了空音频，换个音色试试";

    /**
     * Edge 浏览器标识（与 GEC 版本号配套）。
     * 以后微软再升级，只改这两个常量即可。
     */
    public static final String EDGE_CHROMIUM_FULL = "143.0.3650.75";
    public static final String EDGE_CHROMIUM_MAJOR = "143";

    public static final String EDGE_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/" + EDGE_CHROMIUM_MAJOR + ".0.0.0 Safari/537.36 Edg/"
                    + EDGE_CHROMIUM_MAJOR + ".0.0.0";

    /** Sec-MS-GEC 版本（查询串参数：v2-<Chromium 完整版本>，与 UA 配套）。 */
    public static final String GEC_VERSION = "2-" + EDGE_CHROMIUM_FULL;

    private static final String TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";

    private static final String WS_BASE =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1";
    private static final String ORIGIN =
            "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold";
    private static final String OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3";

    /**
     * 合成连接直连（2026-10-28 定根后改）：此前挂 SourceGate 节点门，
     * TTS 线程无归属打点会被 fallback 猜测误路由到书源的节点口、耗到
     * 超时干挂（ttsprobe/FINDINGS.md）。合成端点是固定基础设施，
     * 不归属任何书源——直连，系统分应用 VPN 由用户自己掌控。
     */
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();

    public static class TtsException extends Exception {
        public TtsException(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------
    // Sec-MS-GEC v2（edge-tts drm.py generate_sec_ms_gec_v2 的 Java 等价实现）
    //
    // 流程：ticks = (unix秒 + Windows纪元差11644473600，向下取整到300秒) * 1e7，
    // s = ticks + token；输出 "<CS64(16位大写)>-<SHA256(64位大写)>"。
    // 其中 SHA-256 是带调整常量的变体（见 _SHA256_* 系列），CS64 是 64 位定制摘要。
    // 全部纯函数，可单测；不要把 Python 源码粘进来，逻辑用 Java 重写。
    // ------------------------------------------------------------------

    private static final int[] SHA256_INITIAL = {
            0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A,
            0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19};
    private static final int[] SHA256_STATE = {
            0xFD9FBEA2, 0x91243CE7, 0xBE499094, 0xCD2E1E0A,
            0x6C006F49, 0x04BD35AB, 0xFEB0A7E4, 0x03EFFBD2};
    private static final int[] SHA256_SCHEDULE = {
            0xEB24BF9E, 0x1558691F, 0xA0C09EE2, 0x0CFA32BA,
            0xA622EE8C, 0xD1DD9491, 0xC0DD25A0, 0xF9E70AB6,
            0xF0C31573, 0x7058CE4E, 0x7188E606, 0x00E5AB33,
            0xD258B060, 0x72766564};
    private static final int[] SHA256_ROUND_CONSTANTS = {
            0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5,
            0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5,
            0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3,
            0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
            0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC,
            0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
            0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7,
            0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
            0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13,
            0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
            0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3,
            0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
            0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5,
            0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
            0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208,
            0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2};
    private static final int SHA256_ADJUSTMENT = 0xC9DC06A7;
    private static final int[] SHA256_BLOCK_CONSTANTS;
    private static final int SHA256_SIGMA0_ADJUSTMENT = 0x059C0B80;
    private static final int SHA256_SIGMA1_ADJUSTMENT = 0x000B92C0;

    private static final int[][] CS64_COEFFICIENTS = {
            {0xE82E91B1, 0xB32700D0}, {0x44DE24C6, 0x88DFF51D},
            {0x3BEB65BC, 0x29514038}, {0xC89C5933, 0xB328CBDF},
            {0x8D1EC211, 0xD405B4D5}, {0x314EF5D5, 0x26E55F3C},
            {0xE92491B1, 0x607801C6}, {0xF0A5B9D5, 0x82B2C1FB},
            {0x06064CF3, 0xFA51A3DA}, {0xDA79FA60, 0x207CC464},
            {0xBED20560, 0x3C3C74F3}, {0x89F9C9F8, 0xD2D14B61},
            {0x35FA38C6, 0xBB04A2C1}, {0xCB49E3C6, 0xC5626167}};
    private static final int CS64_FACTOR = 0x82893933;
    private static final int CS64_ADJUSTMENT0 = 0xF2F90B1A;
    private static final int CS64_ADJUSTMENT1 = 0x0B1A0000;

    static {
        SHA256_BLOCK_CONSTANTS = SHA256_ROUND_CONSTANTS.clone();
        SHA256_BLOCK_CONSTANTS[14] = SHA256_ADJUSTMENT;
    }

    private static int rol16(int v) {
        return (v << 16) | (v >>> 16);
    }

    /** 变体 SHA-256 的小 sigma（截断式，配合 32 位 int 自然截断）。 */
    private static int sigma0s(int v) {
        return (v >>> 7) ^ (v << 25) ^ (v >>> 18) ^ (v << 14) ^ (v >>> 3);
    }

    private static int sigma1s(int v) {
        return (v >>> 17) ^ (v << 15) ^ (v >>> 19) ^ (v << 13) ^ (v >>> 10);
    }

    /** 变体 SHA-256 消息扩展（words 长度至少 64，first 起逐项填充）。 */
    private static void expandSchedule(int[] words, int first) {
        for (int i = first; i < 64; i++) {
            int x = words[i - 2], y = words[i - 15];
            int s1 = (x >>> 17) ^ (x << 15) ^ (x >>> 19) ^ (x << 13) ^ (x >>> 10);
            int s0 = (y >>> 7) ^ (y << 25) ^ (y >>> 18) ^ (y << 14) ^ (y >>> 3);
            words[i] = s1 + words[i - 7] + s0 + words[i - 16];
        }
    }

    /** 变体 SHA-256 压缩轮（标准轮式，与 drm 的 8 轮展开数学等价）。 */
    private static int[] sha256Rounds(int[] state, int[] words, int firstRound) {
        int[] k = (firstRound == 14) ? SHA256_BLOCK_CONSTANTS : SHA256_ROUND_CONSTANTS;
        int a = state[0], b = state[1], c = state[2], d = state[3];
        int e = state[4], f = state[5], g = state[6], h = state[7];
        for (int i = firstRound; i < 64; i++) {
            int t1 = h + (Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25))
                    + ((e & f) ^ (~e & g)) + k[i] + words[i];
            int t2 = (Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22))
                    + ((a & b) ^ (a & c) ^ (b & c));
            h = g;
            g = f;
            f = e;
            e = d + t1;
            d = c;
            c = b;
            b = a;
            a = t1 + t2;
        }
        return new int[]{a, b, c, d, e, f, g, h};
    }

    /** 变体 SHA-256 初始态（drm._sha256_init：取 token 前 7 字节）。 */
    private static int[] sha256Init(byte[] data) {
        int[] words = new int[64];
        words[14] = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
        words[15] = ((data[3] & 0xFF) << 24) | ((data[4] & 0xFF) << 16)
                | ((data[5] & 0xFF) << 8) | (data[6] & 0xFF);
        for (int index = 16; index < 30; index++) {
            int value = SHA256_SCHEDULE[index - 16];
            if (index - 2 >= 14) {
                value += (index == 16)
                        ? (sigma1s(words[index - 2]) ^ SHA256_SIGMA1_ADJUSTMENT)
                        : sigma1s(words[index - 2]);
            }
            if (index - 7 >= 14) {
                value += words[index - 7];
            }
            if (index - 15 >= 14) {
                value += (index == 29)
                        ? (sigma0s(words[index - 15]) ^ SHA256_SIGMA0_ADJUSTMENT)
                        : sigma0s(words[index - 15]);
            }
            words[index] = value;
        }
        words[30] = sigma1s(words[28]) + words[23] + sigma0s(words[15]) + words[14]
                + SHA256_ADJUSTMENT - SHA256_ROUND_CONSTANTS[14];
        expandSchedule(words, 31);
        int[] working = sha256Rounds(SHA256_STATE, words, 14);
        int[] state = new int[8];
        for (int i = 0; i < 8; i++) state[i] = SHA256_INITIAL[i] + working[i];
        return state;
    }

    /** 变体 SHA-256 摘要（drm._sha256_digest）。 */
    private static byte[] sha256Digest(byte[] data) {
        if (data.length < 7) throw new IllegalArgumentException("token too short");
        int length = 57 + data.length;
        int zeroPad = ((55 - length) % 64 + 64) % 64;
        int total = (data.length - 7) + 1 + zeroPad + 8;
        byte[] padded = new byte[total];
        System.arraycopy(data, 7, padded, 0, data.length - 7);
        padded[data.length - 7] = (byte) 0x80;
        long bitLen = (long) length * 8L;
        for (int i = 0; i < 8; i++) padded[total - 8 + i] = (byte) ((bitLen >>> (56 - 8 * i)) & 0xFF);
        int[] state = sha256Init(java.util.Arrays.copyOf(data, 7));
        for (int off = 0; off < total; off += 64) {
            int[] words = new int[64];
            for (int i = 0; i < 16; i++) {
                words[i] = ((padded[off + 4 * i] & 0xFF) << 24) | ((padded[off + 4 * i + 1] & 0xFF) << 16)
                        | ((padded[off + 4 * i + 2] & 0xFF) << 8) | (padded[off + 4 * i + 3] & 0xFF);
            }
            expandSchedule(words, 16);
            int[] working = sha256Rounds(state, words, 0);
            for (int i = 0; i < 8; i++) state[i] += working[i];
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 8; i++) {
            out[4 * i] = (byte) (state[i] >>> 24);
            out[4 * i + 1] = (byte) (state[i] >>> 16);
            out[4 * i + 2] = (byte) (state[i] >>> 8);
            out[4 * i + 3] = (byte) state[i];
        }
        return out;
    }

    /** CS64 摘要（drm._cs64_digest），返回两个 32 位字。 */
    private static int[] cs64Digest(byte[] data, byte[] digest) {
        int keyA = (digest[0] & 0xFF) | ((digest[1] & 0xFF) << 8)
                | ((digest[2] & 0xFF) << 16) | (digest[3] << 24);
        int keyB = (digest[4] & 0xFF) | ((digest[5] & 0xFF) << 8)
                | ((digest[6] & 0xFF) << 16) | (digest[7] << 24);
        keyA |= 1;
        keyB |= 1;
        int[] factors = new int[]{keyA * CS64_FACTOR, keyB * CS64_FACTOR};
        java.util.ArrayList<Integer> terms = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> offsets = new java.util.ArrayList<>();
        for (int i = 0; i < CS64_COEFFICIENTS.length; i++) {
            terms.add(CS64_COEFFICIENTS[i][0] * factors[i % 2]);
            offsets.add(CS64_COEFFICIENTS[i][1]);
        }
        // b"\x00" + data 按小端切 32 位字，补到偶数个
        int n = data.length + 1;
        int count = (n + 3) / 4;
        if ((count & 1) == 1) count++;
        for (int index = 0; index < count; index++) {
            int w = 0;
            for (int b = 0; b < 4; b++) {
                int pos = index * 4 + b - 1; // 减 1：首字节是补的 \x00
                int bv = (pos < 0) ? 0 : (pos < data.length ? (data[pos] & 0xFF) : 0);
                w |= bv << (8 * b);
            }
            int kb = (index % 2 == 0) ? keyA : keyB;
            int term = w * kb;
            int off = rol16(w) * ((index % 2 == 0) ? 0xEF0569FB : 0xC31713DB);
            if (index == 0) {
                term += CS64_ADJUSTMENT0 * factors[0];
                off += CS64_ADJUSTMENT1;
            }
            terms.add(term);
            offsets.add(off);
        }
        int swapAcc = 0, swapLast = 0, state = 0, acc = 0;
        for (int index = 0; index < terms.size(); index += 2) {
            int x = terms.get(index), y = terms.get(index + 1);
            int t = rol16(offsets.get(index) + x);
            int q = t * 0x76F50F34;
            swapAcc += rol16(q) * 0xC3EFEA97 + q;
            t = rol16(offsets.get(index + 1) + y);
            q = t * 0x1881CE3C;
            swapLast = rol16(q) * 0x35BD1EC9 + q;
            swapAcc += swapLast;
            state = state * keyA + x;
            state = rol16(state) * 0xCF98B111;
            state = rol16(state) * 0x87085B9F;
            state = rol16(state) * 0x12CEB96D;
            state = rol16(state) * 0x257E1D83;
            acc += state;
            t = state * keyB + y;
            t = rol16(t) * 0xA27416F5;
            t = rol16(t) * 0xD38396FF;
            state = rol16(rol16(t) * 0x7C932B89) * 0xBFA49F69;
            acc += state;
        }
        return new int[]{swapLast ^ state, swapAcc ^ acc};
    }

    /**
     * Sec-MS-GEC v2 时间戳挑战（纯函数，可单测）。
     * ticks = (unix秒 + 11644473600，向下取整到300秒) * 1e7，拼 token 后
     * 输出 "<CS64 16位大写>-<SHA256 64位大写>"。
     */
    public static String secMsGec(long nowMs) {
        try {
            double ticks = nowMs / 1000.0;
            ticks += 11644473600.0;
            ticks -= ticks % 300.0;
            ticks *= 10000000.0;
            String src = String.format(Locale.US, "%.0f", ticks) + TRUSTED_CLIENT_TOKEN;
            byte[] data = src.getBytes("US-ASCII");
            byte[] sha = sha256Digest(data);
            int[] cs = cs64Digest(data, sha);
            StringBuilder sb = new StringBuilder();
            for (int v : cs) {
                sb.append(String.format(Locale.US, "%02X", v & 0xFF));
                sb.append(String.format(Locale.US, "%02X", (v >>> 8) & 0xFF));
                sb.append(String.format(Locale.US, "%02X", (v >>> 16) & 0xFF));
                sb.append(String.format(Locale.US, "%02X", (v >>> 24) & 0xFF));
            }
            sb.append('-');
            for (byte b : sha) sb.append(String.format(Locale.US, "%02X", b));
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    public static String secMsGec() {
        return secMsGec(System.currentTimeMillis());
    }

    public static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 语速倍率 → SSML prosody rate（如 1.25 → +25%）。 */
    public static String rateTag(float rate) {
        int pct = Math.round((rate - 1f) * 100f);
        return (pct >= 0 ? "+" : "") + pct + "%";
    }

    public static String ssml(String text, String voiceId, float rate) {
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' "
                + "xml:lang='zh-CN'><voice name='" + voiceId + "'>"
                + "<prosody pitch='+0Hz' rate='" + rateTag(rate) + "' volume='+0%'>"
                + escapeXml(text) + "</prosody></voice></speak>";
    }

    private static String wsDate() {
        try {
            SimpleDateFormat f = new SimpleDateFormat(
                    "EEE MMM dd yyyy HH:mm:ss 'GMT'Z '(Coordinated Universal Time)'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.format(new Date());
        } catch (Throwable t) {
            return "";
        }
    }

    private static String noDashes() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 握手地址：wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1，
     * 鉴权全拼进查询串（浏览器形状，服务端只认这一形）。
     * 每次现算 GEC（300 秒窗口）与 ConnectionId。
     * 注意：api.msedgeservices.com 的 v1 路径是 Azure 订阅密钥口，不认浏览器 token，
     * 查询串形状在它上面照样 403（2026-10-19 本机实测）——必须走 speech.platform.bing.com。
     */
    static String wsUrl() {
        return WS_BASE
                + "?TrustedClientToken=" + TRUSTED_CLIENT_TOKEN
                + "&Sec-MS-GEC=" + secMsGec()
                + "&Sec-MS-GEC-Version=" + GEC_VERSION
                + "&ConnectionId=" + noDashes();
    }

    /** 握手请求拼装（抽出来，方便抓包单测）。 */
    static Request newRequest() {
        return newRequest(wsUrl());
    }

    /** 握手请求拼装（测试用：把地址指到本地 mock 服务）。 */
    static Request newRequest(String url) {
        return new Request.Builder().url(url)
                .header("Origin", ORIGIN)
                .header("User-Agent", EDGE_UA)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .build();
    }

    /** speech.config 文本帧（抽出来，方便抓包单测）。 */
    static String speechConfigFrame(String date) {
        return "X-Timestamp:" + date + "\r\n"
                + "Content-Type:application/json; charset=utf-8\r\n"
                + "Path:speech.config\r\n\r\n"
                + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":"
                + "{\"sentenceBoundaryEnabled\":false,\"wordBoundaryEnabled\":true},"
                + "\"outputFormat\":\"" + OUTPUT_FORMAT + "\"}}}}";
    }

    /**
     * 合成一段文本 → mp3 字节。阻塞调用（调方自开线程），单段 12 秒超时
     * （2026-10-28 由 45 秒压下，见 ERR_TIMEOUT）。抛 TtsException（人话）；
     * 失败后是否切系统语音由引擎决定（他 2026-10-27 令：不许只押一家）。
     */
    public byte[] synthesize(String text, String voiceId, float rate) throws TtsException {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(voiceId)) {
            throw new TtsException("没有可合成的文本");
        }
        final CountDownLatch done = new CountDownLatch(1);
        final ByteArrayOutputStream audio = new ByteArrayOutputStream();
        final AtomicReference<String> failure = new AtomicReference<>();
        final AtomicInteger closeCode = new AtomicInteger(-1);
        final String date = wsDate();
        final String connectionId = noDashes();
        final String requestId = noDashes();

        Request req = newRequest();

        WebSocket ws = CLIENT.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                webSocket.send(speechConfigFrame(date));
                String msg = "X-RequestId:" + requestId + "\r\n"
                        + "Content-Type:application/ssml+xml\r\n"
                        + "X-Timestamp:" + date + "\r\n"
                        + "Path:ssml\r\n\r\n"
                        + ssml(text, voiceId, rate);
                webSocket.send(msg);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                if (text != null && text.contains("Path:turn.end")) {
                    done.countDown();
                } else if (text != null && text.contains("Path:turn.start")) {
                    // 合成开始，无动作
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                try {
                    byte[] raw = bytes.toByteArray();
                    if (raw.length < 2) return;
                    int headerLen = ByteBuffer.wrap(raw, 0, 2)
                            .order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;
                    int bodyStart = 2 + headerLen;
                    if (bodyStart < raw.length) {
                        synchronized (audio) {
                            audio.write(raw, bodyStart, raw.length - bodyStart);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                // 失败分四类给人话：连不上服务器 / 服务器拒绝 / 超时 / 没拿到声音。
                // response != null 说明握手走到了服务器（HTTP 错误码），否则是网络层失败。
                String msg = ERR_OFFLINE;
                if (response != null) {
                    msg = (response.code() == 403) ? ERR_REJECTED : ERR_SERVER;
                }
                failure.compareAndSet(null, msg);
                done.countDown();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                closeCode.compareAndSet(-1, code);
                done.countDown();
            }
        });

        try {
            boolean ok = done.await(12, TimeUnit.SECONDS);
            if (!ok) {
                failure.compareAndSet(null, ERR_TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure.compareAndSet(null, ERR_OFFLINE);
        } finally {
            try {
                ws.close(1000, null);
            } catch (Throwable ignored) {
            }
        }
        if (failure.get() != null) throw new TtsException(failure.get());
        byte[] out;
        synchronized (audio) {
            out = audio.toByteArray();
        }
        if (out.length == 0) {
            int code = closeCode.get();
            if (code != -1 && code != 1000) throw new TtsException(ERR_BROKEN);
            throw new TtsException(ERR_EMPTY);
        }
        return out;
    }
}
