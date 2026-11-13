package com.example.hikerview.utils.contentdisposition;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * CharsetUtil.smali 等同名 smali 共 207 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
public class CharsetUtil {
    public static final int CR = 13;
    public static final String CRLF = "\r\n";
    public static final int HT = 9;
    public static final int LF = 10;
    public static final int SP = 32;

    public static boolean isASCII(char c) {
        return (c & 65408) == 0;
    }

    public static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    public static boolean isASCII(String str) {
        if (str == null) {
            throw new IllegalArgumentException("String may not be null");
        }
        int length = str.length();
        for (int i = 0; i < length; i++) {
            if (!isASCII(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isWhitespace(String str) {
        if (str == null) {
            throw new IllegalArgumentException("String may not be null");
        }
        int length = str.length();
        for (int i = 0; i < length; i++) {
            if (!isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static Charset lookup(String str) {
        if (str == null) {
            return null;
        }
        try {
            return Charset.forName(str);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException unused) {
            return null;
        }
    }
}
