package com.example.hikerview.utils.contentdisposition;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * DecoderUtil.smali 等同名 smali 共 992 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 * decodeEncodedWords 中 jadx 把官「空文本支直接回环头」写成经共享尾 i2=i（i 未定值、真编不过），已按 smali 支路订正：i2=i 只在另两支内执行。
 */
public class DecoderUtil {
    private static byte[] decodeQuotedPrintable(String str, DecodeMonitor decodeMonitor) {
        try {
            QuotedPrintableInputStream quotedPrintableInputStream = new QuotedPrintableInputStream(InputStreams.createAscii(str), decodeMonitor);
            try {
                ByteArrayBuffer byteArrayBuffer = new ByteArrayBuffer(str.length());
                while (true) {
                    int read = quotedPrintableInputStream.read();
                    if (read != -1) {
                        byteArrayBuffer.append(read);
                    } else {
                        return byteArrayBuffer.toByteArray();
                    }
                }
            } finally {
                quotedPrintableInputStream.close();
            }
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private static byte[] decodeBase64(String str, DecodeMonitor decodeMonitor) {
        try {
            Base64InputStream base64InputStream = new Base64InputStream(InputStreams.createAscii(str), decodeMonitor);
            try {
                ByteArrayBuffer byteArrayBuffer = new ByteArrayBuffer(str.length());
                while (true) {
                    int read = base64InputStream.read();
                    if (read != -1) {
                        byteArrayBuffer.append(read);
                    } else {
                        return byteArrayBuffer.toByteArray();
                    }
                }
            } finally {
                base64InputStream.close();
            }
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    static String decodeB(String str, String str2, DecodeMonitor decodeMonitor) throws UnsupportedEncodingException {
        return new String(decodeBase64(str, decodeMonitor), str2);
    }

    static String decodeQ(String str, String str2, DecodeMonitor decodeMonitor) throws UnsupportedEncodingException {
        return new String(decodeQuotedPrintable(replaceUnderscores(str), decodeMonitor), str2);
    }

    static String decodeEncodedWords(String str) {
        return decodeEncodedWords(str, DecodeMonitor.SILENT);
    }

    public static String decodeEncodedWords(String str, DecodeMonitor decodeMonitor) throws IllegalArgumentException {
        return decodeEncodedWords(str, decodeMonitor, null, Collections.emptyMap());
    }

    public static String decodeEncodedWords(String str, Charset charset) throws IllegalArgumentException {
        return decodeEncodedWords(str, null, charset, Collections.emptyMap());
    }

    public static String decodeEncodedWords(String str, DecodeMonitor decodeMonitor, Charset charset) throws IllegalArgumentException {
        return decodeEncodedWords(str, decodeMonitor, charset, Collections.emptyMap());
    }

    public static String decodeEncodedWords(String str, DecodeMonitor decodeMonitor, Charset charset, Map<Charset, Charset> map) throws IllegalArgumentException {
        int i;
        StringBuilder sb = new StringBuilder();
        int i2 = 0;
        while (true) {
            if (i2 >= str.length()) {
                break;
            }
            int indexOf = str.indexOf("=?", i2);
            if (indexOf >= 0) {
                int i3 = indexOf + 2;
                int indexOf2 = str.indexOf(63, i3);
                int i4 = indexOf2 + 1;
                int indexOf3 = str.indexOf(63, i4);
                int i5 = indexOf3 + 1;
                int indexOf4 = str.indexOf("?=", i5);
                if (indexOf2 < 0 || indexOf3 < 0 || indexOf4 < 0) {
                    sb.append((CharSequence) str, i2, i3);
                    i2 = i3;
                } else {
                    if (indexOf3 == indexOf4) {
                        i = indexOf4 + 2;
                        sb.append((CharSequence) str, i2, Math.min(i, str.length()));
                        i2 = i;
                    } else {
                        String substring = str.substring(i2, indexOf);
                        if ((!CharsetUtil.isWhitespace(substring) || i2 == 0) && !substring.isEmpty()) {
                            sb.append(substring);
                        }
                        String substring2 = str.substring(i3, indexOf2);
                        String substring3 = str.substring(i4, indexOf3);
                        String substring4 = str.substring(i5, indexOf4);
                        if (substring4.isEmpty()) {
                            i2 = indexOf4 + 2;
                        } else {
                            String tryDecodeEncodedWord = tryDecodeEncodedWord(substring2, substring3, substring4, decodeMonitor, charset, map);
                            if (tryDecodeEncodedWord != null) {
                                if (!CharsetUtil.isWhitespace(tryDecodeEncodedWord) && !tryDecodeEncodedWord.isEmpty()) {
                                    sb.append(tryDecodeEncodedWord);
                                }
                            } else {
                                sb.append((CharSequence) str, indexOf, indexOf4 + 2);
                            }
                            i = indexOf4 + 2;
                            i2 = i;
                        }
                    }
                }
            } else {
                if (i2 == 0) {
                    return str;
                }
                sb.append((CharSequence) str, i2, str.length());
            }
        }
        return sb.toString();
    }

    private static String tryDecodeEncodedWord(String str, String str2, String str3, DecodeMonitor decodeMonitor, Charset charset, Map<Charset, Charset> map) {
        Charset lookupCharset = lookupCharset(str, charset, map);
        if (lookupCharset == null) {
            monitor(decodeMonitor, str, str2, str3, "leaving word encoded", "Mime charser '", str, "' doesn't have a corresponding Java charset");
            return null;
        }
        if (str3.length() == 0) {
            monitor(decodeMonitor, str, str2, str3, "leaving word encoded", "Missing encoded text in encoded word");
            return null;
        }
        try {
            if (str2.equalsIgnoreCase("Q")) {
                return decodeQ(str3, lookupCharset.name(), decodeMonitor);
            }
            if (str2.equalsIgnoreCase("B")) {
                return decodeB(str3, lookupCharset.name(), decodeMonitor);
            }
            monitor(decodeMonitor, str, str2, str3, "leaving word encoded", "Warning: Unknown encoding in encoded word");
            return null;
        } catch (UnsupportedEncodingException e) {
            monitor(decodeMonitor, str, str2, str3, "leaving word encoded", "Unsupported encoding (", e.getMessage(), ") in encoded word");
            return null;
        } catch (RuntimeException e2) {
            monitor(decodeMonitor, str, str2, str3, "leaving word encoded", "Could not decode (", e2.getMessage(), ") encoded word");
            return null;
        }
    }

    private static Charset lookupCharset(String str, Charset charset, Map<Charset, Charset> map) {
        Charset lookup = CharsetUtil.lookup(str);
        if (lookup == null) {
            return charset;
        }
        Charset charset2 = map.get(lookup);
        return charset2 != null ? charset2 : lookup;
    }

    private static void monitor(DecodeMonitor decodeMonitor, String str, String str2, String str3, String str4, String... strArr) throws IllegalArgumentException {
        if (decodeMonitor.isListening()) {
            String recombine = recombine(str, str2, str3);
            StringBuilder sb = new StringBuilder();
            for (String str5 : strArr) {
                sb.append(str5);
            }
            sb.append(" (");
            sb.append(recombine);
            sb.append(")");
            if (decodeMonitor.warn(sb.toString(), str4)) {
                throw new IllegalArgumentException(sb.toString());
            }
        }
    }

    private static String recombine(String str, String str2, String str3) {
        return "=?" + str + "?" + str2 + "?" + str3 + "?=";
    }

    private static String replaceUnderscores(String str) {
        StringBuilder sb = new StringBuilder(128);
        for (int i = 0; i < str.length(); i++) {
            char charAt = str.charAt(i);
            if (charAt == '_') {
                sb.append("=20");
            } else {
                sb.append(charAt);
            }
        }
        return sb.toString();
    }
}
