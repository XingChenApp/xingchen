package com.example.hikerview.utils.contentdisposition;

import java.util.Random;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * MimeUtil.smali 等同名 smali 共 616 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
public final class MimeUtil {
    public static final String ENC_7BIT = "7bit";
    public static final String ENC_8BIT = "8bit";
    public static final String ENC_BASE64 = "base64";
    public static final String ENC_BINARY = "binary";
    public static final String ENC_QUOTED_PRINTABLE = "quoted-printable";
    private static final Random random = new Random();
    private static int counter = 0;

    private MimeUtil() {
    }

    public static boolean isSameMimeType(String str, String str2) {
        return (str == null || str2 == null || !str.equalsIgnoreCase(str2)) ? false : true;
    }

    public static boolean isMessage(String str) {
        return str != null && str.equalsIgnoreCase("message/rfc822");
    }

    public static boolean isMultipart(String str) {
        return str != null && str.toLowerCase().startsWith("multipart/");
    }

    public static boolean isBase64Encoding(String str) {
        return ENC_BASE64.equalsIgnoreCase(str);
    }

    public static boolean isQuotedPrintableEncoded(String str) {
        return ENC_QUOTED_PRINTABLE.equalsIgnoreCase(str);
    }

    public static String createUniqueBoundary() {
        StringBuilder sb = new StringBuilder("-=Part.");
        sb.append(Integer.toHexString(nextCounterValue()));
        sb.append('.');
        Random random2 = random;
        sb.append(Long.toHexString(random2.nextLong()));
        sb.append('.');
        sb.append(Long.toHexString(System.currentTimeMillis()));
        sb.append('.');
        sb.append(Long.toHexString(random2.nextLong()));
        sb.append("=-");
        return sb.toString();
    }

    public static String createUniqueMessageId(String str) {
        StringBuilder sb = new StringBuilder("<Mime4j.");
        sb.append(Integer.toHexString(nextCounterValue()));
        sb.append('.');
        sb.append(Long.toHexString(random.nextLong()));
        sb.append('.');
        sb.append(Long.toHexString(System.currentTimeMillis()));
        if (str != null) {
            sb.append('@');
            sb.append(str);
        }
        sb.append('>');
        return sb.toString();
    }

    public static String fold(String str, int i) {
        int length = str.length();
        if (i + length <= 76) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        int i2 = -i;
        int indexOfWsp = indexOfWsp(str, 0);
        while (indexOfWsp != length) {
            int indexOfWsp2 = indexOfWsp(str, indexOfWsp + 1);
            if (indexOfWsp2 - i2 > 76) {
                sb.append((CharSequence) str, Math.max(0, i2), indexOfWsp);
                sb.append(CharsetUtil.CRLF);
                i2 = indexOfWsp;
            }
            indexOfWsp = indexOfWsp2;
        }
        sb.append(str.substring(Math.max(0, i2)));
        return sb.toString();
    }

    public static String unfold(String str) {
        int length = str.length();
        for (int i = 0; i < length; i++) {
            char charAt = str.charAt(i);
            if (charAt == '\r' || charAt == '\n') {
                return unfold0(str, i);
            }
        }
        return str;
    }

    public static String unscrambleHeaderValue(String str) {
        return DecoderUtil.decodeEncodedWords(unfold(str), DecodeMonitor.SILENT);
    }

    private static String unfold0(String str, int i) {
        int length = str.length();
        StringBuilder sb = new StringBuilder(length);
        if (i > 0) {
            sb.append((CharSequence) str, 0, i);
        }
        for (int i2 = i + 1; i2 < length; i2++) {
            char charAt = str.charAt(i2);
            if (charAt == '\r' || charAt == '\n') {
                int i3 = i + 1;
                if (i2 > i3) {
                    sb.append((CharSequence) str, i3, i2);
                }
                i = i2;
            }
        }
        if (i < str.length() - 1 && str.length() > 0) {
            sb.append((CharSequence) str, i + 1, str.length());
        }
        return sb.toString();
    }

    private static int indexOfWsp(String str, int i) {
        int length = str.length();
        while (i < length) {
            char charAt = str.charAt(i);
            if (charAt == ' ' || charAt == '\t') {
                return i;
            }
            i++;
        }
        return length;
    }

    private static synchronized int nextCounterValue() {
        int i;
        synchronized (MimeUtil.class) {
            i = counter;
            counter = i + 1;
        }
        return i;
    }
}
