package com.example.hikerview.utils.contentdisposition;

import java.nio.charset.Charset;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * Charsets.smali 等同名 smali 共 60 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
public final class Charsets {
    public static final Charset DEFAULT_CHARSET;
    public static final Charset ISO_8859_1;
    public static final Charset US_ASCII;
    public static final Charset UTF_8;

    static {
        Charset forName = Charset.forName("US-ASCII");
        US_ASCII = forName;
        ISO_8859_1 = Charset.forName("ISO-8859-1");
        UTF_8 = Charset.forName("UTF-8");
        DEFAULT_CHARSET = forName;
    }
}
