package com.fongmi.android.tv.hongguo.crypto;

import java.util.Arrays;

/**
 * spade_a → CENC 内容解密钥匙（DEK，十六进制串）的推导。
 *
 * <p>逐条移植自 ~/workspace/hongguo/lib_hg_lib.js 文件头的 spadeToKey / spadeDeob
 * （libttmplayer.so:0xc7120 的纯 JS 复刻，与 Unicorn 真代码 7000 例差分对拍 0 不符）。
 * 本类不另想算法：循环、常量、字面量表与 JS 版一一对应，字节一律按无符号处理。</p>
 */
public final class SpadeKey {

    /** 与 JS 版 SPADE_LIT_HEX 逐字相同。 */
    private static final String LIT_HEX =
            "6170705f7632007765625f7632004b68454b9cabee47ca04f404000000ae000000001004001c000000ae000000c500000066665f7265676973746572" +
            "5f63616368652e637070004646526567697374657243616368650046465265676973746572436163686520636f6e73004e33636f6d3273733374746d" +
            "3666666d706567313546465265676973746572436163686545004e33636f6d3273733374746d365374726f6e67494e53315f3666666d706567313546" +
            "46526567697374657243616368654545450000000002000000020000000200000001000000000000000100000042797465564331204465636f646572" +
            "0064756d7062730064756d7020627974652076633120696e7075742062697473747265616d00627974655f7663315f74687265616473007468726561" +
            "6473207061737320746f206465636f6465722873686f756c64206c657373207468616e2063707520636f726520636f756e742900627974655f766331" +
            "5f6f75747075745f6d6574686f64003020666f72206e6f726d616c206f75747075742c203120666f72206c6f77206c6174656e6379206f7574707574" +
            "00627974655f7663315f6572726f725f70726f74656374696f6e003020666f72206e6f206572726f722070726f7465637474696f6e2c203120666f72" +
            "2064726f7070696e67204e414c20756e74696c206e65787420706172616d6574";

    private static final int APP_OFF = 0;   /* "app_v2" */
    private static final int WEB_OFF = 7;   /* "web_v2" */

    private static byte[] litBytes;

    private SpadeKey() {
    }

    /** 推导结果：成功时 key 为 DEK 十六进制串；失败时 error 说明原因。 */
    public static final class Result {
        public final String key;
        public final String out2;
        public final boolean magic;
        public final String error;
        /**
         * v2 形态（out2=="2"）下按 openhongguo spade.go 同路解出的候选
         * key（32 位 hex 才给）；证据冲突未定，见 {@link #derive} 注。
         * 普通消费方不许用，只许「全部正路皆死」时的末位兜底用。
         */
        public final String v2Candidate;

        private Result(String key, String out2, boolean magic, String error) {
            this(key, out2, magic, error, null);
        }

        private Result(String key, String out2, boolean magic, String error,
                       String v2Candidate) {
            this.key = key;
            this.out2 = out2;
            this.magic = magic;
            this.error = error;
            this.v2Candidate = v2Candidate;
        }

        public boolean isOk() {
            return key != null && !key.isEmpty();
        }
    }

    private static int litByte(int off) {
        if (litBytes == null) litBytes = HgHex.decode(LIT_HEX);
        return litBytes[off] & 0xff;
    }

    /** 二进制安全的 Base64 → 无符号字节数组（与 JS 版 spadeB64ToBytes 同逻辑）。 */
    static int[] b64ToBytes(String s) {
        final String table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        int[] out = new int[s.length()];
        int n = 0, acc = 0, bits = 0;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '=' || c == '\n' || c == '\r' || c == ' ') continue;
            int v = table.indexOf(c);
            if (v < 0) continue;
            acc = (acc << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[n++] = (acc >> bits) & 0xff;
            }
        }
        return Arrays.copyOf(out, n);
    }

    private static int popcnt32(int v) {
        return Integer.bitCount(v);
    }

    private static void swapPairs(int[] a, int n) {
        int i = 0;
        while (i + 1 < n) {
            int t = a[i];
            a[i] = a[i + 1];
            a[i + 1] = t;
            i += 2;
        }
    }

    private static void loop1(int[] a, int n, int w4) {
        int w11 = 0x55, w12 = 0xfa;
        for (int i = 0; i < n; i++) {
            int b = a[i];
            boolean even = (i & 1) == 0;
            int c = even ? w12 : w11;
            if (even) w12 = b; else w11 = b;
            int e = (w4 == 0) ? (-21 - popcnt32(i)) : (popcnt32(i) + 21);
            a[i] = ((b ^ c) + e) & 0xff;
        }
    }

    private static void loop2(int[] a, int n, int w4) {
        int w12 = 0x55, w13 = 0xfa;
        for (int i = 0; i < n; i++) {
            int b = a[i];
            boolean even = (i & 1) == 0;
            int w16 = even ? w13 : w12;
            int w15 = even ? w12 : w13;
            if (even) w13 = b; else w12 = b;
            int e = (w4 == 0) ? (-21 - popcnt32(i)) : (popcnt32(i) + 21);
            a[i] = (((b ^ w16) - w15) + e) & 0xff;
        }
    }

    private static int ncmp(int[] x, int litOff, int n) {
        for (int i = 0; i < n; i++) {
            int a = x[i], b = litByte(litOff + i);
            if (a != b) return a > b ? 1 : -1;
        }
        return 0;
    }

    private static final class Deob {
        boolean early = true;
        boolean magic;
        int[] out1;
        int[] out2;
    }

    /** 与 JS 版 spadeDeob 逐指令对应。 */
    private static Deob deob(int[] buf, int w4) {
        Deob r = new Deob();
        int len = buf.length;
        if (len < 3) return r;
        int key = (buf[0] ^ buf[1] ^ buf[2]) & 0xff;
        int x27 = key - 0x30;
        if (x27 < 1) return r;
        int w9 = (len - key) >>> 0;
        int w22 = (w9 + 0x2f) >>> 0;
        if (w22 < 1) return r;
        int n21 = w22;
        if (n21 <= 0 || n21 > len - 1) return r;

        int[] x21 = new int[n21];
        System.arraycopy(buf, 1, x21, 0, n21);

        int i0 = len - x27 - 2;
        if (i0 < 0) return r;
        int w11 = buf[i0], w10 = buf[i0 + 1];
        int w9m = w10 ^ w11;
        int[] x23 = new int[x27];
        for (int j = 0; j < x27; j++) x23[j] = (w9m ^ buf[len - x27 + j]) & 0xff;

        boolean magic = ncmp(x23, APP_OFF, x27) == 0 || ncmp(x23, WEB_OFF, x27) == 0;
        r.magic = magic;

        if (magic) {
            if (w22 != 1) swapPairs(x21, w22);
            loop2(x21, w22, w4);
            if (w22 != 1) swapPairs(x21, w22);
        } else {
            loop1(x21, w22, w4);
        }

        int c = x21[0];
        int d = (c - 0x30) >>> 0;
        long s;
        if (d < 0xa) {
            s = d;
        } else {
            int t = (c - 0x61) >>> 0;
            s = (t < 0x1a) ? ((c - 0x57) >>> 0) : 0xffffffffL;
        }
        int sByte = (int) (s & 0xff);
        int w9b = (w22 - sByte) >>> 0;
        if (w9b < 2) return r;
        int l1 = w9b;
        r.early = false;

        int[] out1 = new int[l1];
        int[] out2 = new int[sByte + 1];
        out1[l1 - 1] = 0;
        out2[sByte] = 0;
        if (l1 - 1 >= 1) {
            int cl = l1 - 1;
            for (int m = 0; m < cl; m++) out1[m] = x21[1 + m];
        }
        if (sByte != 0) {
            int off = w22 - sByte;
            for (int z = 0; z < sByte; z++) out2[z] = x21[off + z];
        }
        r.out1 = out1;
        r.out2 = out2;
        return r;
    }

    private static String bytesToAscii(int[] a) {
        if (a == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int v : a) {
            if (v == 0) break;
            sb.append((char) v);
        }
        return sb.toString();
    }

    /** 入口：spade_a（Base64）→ DEK 十六进制串。与 JS 版 spadeToKey 同口径（w4=0）。 */
    public static Result derive(String spadeBase64) {
        return derive(spadeBase64, 0);
    }

    public static Result derive(String spadeBase64, int w4) {
        if (spadeBase64 == null || spadeBase64.isEmpty()) {
            return new Result(null, null, false, "spade_a 为空");
        }
        int[] buf = b64ToBytes(spadeBase64);
        Deob r = deob(buf, w4);
        if (r.early) {
            return new Result(null, null, r.magic, "spade 去混淆提前退出（选择子非法/长度不足）");
        }
        String o2 = bytesToAscii(r.out2);
        if ("2".equals(o2)) {
            // 证据冲突（如实记）：JS 移植原注称 v2 需 keystr 走 GCM 分支
            // （其自身亦未实现）；而 openhongguo 的 Go 版 Derive
            // （internal/drm/spade.go，自述对真集验过）不分版本——同路
            // 解出 out1 作 key、Version 只作标记返回。两说未有 v2 真样本
            // 对拍定案：正路仍判失败（key=null），但把按 Go 口径校验
            // 过的 out1（32 位 hex）挂 v2Candidate，只许末位兜底消费。
            String cand = bytesToAscii(r.out1);
            if (!isHex32(cand)) cand = null;
            return new Result(null, o2, r.magic,
                    "v2 形态 spade（out2==\"2\"）：GCM/keystr 说与 Go 版同路解说未定案", cand);
        }
        return new Result(bytesToAscii(r.out1), o2, r.magic, null);
    }

    /** 32 位十六进制串（16 字节 DEK 的文本形）校验，与 Go 版 Derive 的 hex/偶长校验同向、长度收严到 DEK 规格。 */
    private static boolean isHex32(String s) {
        if (s == null || s.length() != 32) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    /** 便捷口：只取 DEK，失败抛 IllegalStateException（带原因）。 */
    public static String deriveKeyHex(String spadeBase64) {
        Result r = derive(spadeBase64);
        if (!r.isOk()) throw new IllegalStateException(r.error);
        return r.key;
    }
}
