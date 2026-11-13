package com.fongmi.android.tv.localbook;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 轻量编码探测：BOM → UTF-8 严格校验 → GBK/Big5 双字节打分 → 兜底 GBK。
 * 不引入第三方库。GBK 与 Big5 只在前导/后续字节合法性上打分区分，
 * 混合文本可能误判，属已知边界（可重导，用户可删了重来）。
 */
public class LocalEncoding {

    /** 最多取这么多字节做样本。 */
    private static final int SAMPLE = 128 * 1024;

    public static String detect(InputStream in) {
        try {
            byte[] buf = new byte[SAMPLE];
            int n = 0, r;
            while (n < SAMPLE && (r = in.read(buf, n, SAMPLE - n)) > 0) n += r;
            return detectBytes(buf, n);
        } catch (Throwable t) {
            return "GBK";
        }
    }

    public static String detectBytes(byte[] buf, int len) {
        if (len >= 3 && (buf[0] & 0xFF) == 0xEF && (buf[1] & 0xFF) == 0xBB && (buf[2] & 0xFF) == 0xBF) {
            return "UTF-8";
        }
        if (len >= 2) {
            int b0 = buf[0] & 0xFF, b1 = buf[1] & 0xFF;
            if (b0 == 0xFF && b1 == 0xFE) return "UTF-16LE";
            if (b0 == 0xFE && b1 == 0xFF) return "UTF-16BE";
        }
        // 无 BOM 的 UTF-16：文本里本不该有 0x00，出现一批就是 UTF-16；
        // ASCII 字符的高字节 0x00 落在奇位=LE、偶位=BE，多数投票
        // （CJK 字符如"一"U+4E00 的 0x00 会落另一边，投票扛得住）。
        int zeros = 0, zerosOdd = 0, zerosEven = 0;
        for (int zi = 0; zi < len; zi++) {
            if (buf[zi] == 0) {
                zeros++;
                if ((zi & 1) == 1) zerosOdd++;
                else zerosEven++;
            }
        }
        if (zeros > len * 0.05) {
            return zerosOdd >= zerosEven ? "UTF-16LE" : "UTF-16BE";
        }
        if (isValidUtf8(buf, len)) return "UTF-8";
        // GBK 与 Big5 打分：双字节对在各自合法区间内的命中数
        int gbk = 0, big5 = 0, bad = 0;
        int i = 0;
        while (i < len) {
            int b = buf[i] & 0xFF;
            if (b < 0x80) {
                i++;
                continue;
            }
            if (i + 1 >= len) {
                bad++;
                break;
            }
            int c = buf[i + 1] & 0xFF;
            boolean inGbk = b >= 0x81 && b <= 0xFE && c >= 0x40 && c <= 0xFE && c != 0x7F;
            boolean inBig5 = b >= 0x81 && b <= 0xFE
                    && ((c >= 0x40 && c <= 0x7E) || (c >= 0xA1 && c <= 0xFE));
            if (inGbk) gbk++;
            if (inBig5) big5++;
            if (!inGbk && !inBig5) bad++;
            i += 2;
        }
        if (bad > (gbk + big5) / 2 + 4) return "UTF-8";
        return big5 > gbk * 2 ? "Big5" : "GBK";
    }

    private static boolean isValidUtf8(byte[] buf, int len) {
        try {
            CharsetDecoder d = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            d.decode(ByteBuffer.wrap(buf, 0, len));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    public static Charset charsetOf(String name) {
        try {
            return Charset.forName(name);
        } catch (Throwable t) {
            return Charset.forName("GBK");
        }
    }

    /** 换行符里含 0x00 的编码（UTF-16），不能按字节切行。 */
    public static boolean isCharMode(String charset) {
        return "UTF-16LE".equalsIgnoreCase(charset) || "UTF-16BE".equalsIgnoreCase(charset)
                || "UTF-16".equalsIgnoreCase(charset);
    }
}
