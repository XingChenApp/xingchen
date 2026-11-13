package com.example.hikerview.utils.contentdisposition;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * ContentDispositionParserConstants.smali 等同名 smali 共 176 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
public interface ContentDispositionParserConstants {
    public static final int ANY = 22;
    public static final int ATOKEN = 20;
    public static final int COMMENT = 7;
    public static final int DEFAULT = 0;
    public static final int DIGITS = 19;
    public static final int EOF = 0;
    public static final int INCOMMENT = 1;
    public static final int INQUOTEDSTRING = 3;
    public static final int NESTED_COMMENT = 2;
    public static final int QUOTEDPAIR = 21;
    public static final int QUOTEDSTRING = 18;
    public static final int WS = 5;
    public static final String[] tokenImage = {"<EOF>", "\"\\r\"", "\"\\n\"", "\";\"", "\"=\"", "<WS>", "\"(\"", "\")\"", "<token of kind 8>", "\"(\"", "<token of kind 10>", "<token of kind 11>", "\"(\"", "\")\"", "<token of kind 14>", "\"\\\"\"", "<token of kind 16>", "<token of kind 17>", "\"\\\"\"", "<DIGITS>", "<ATOKEN>", "<QUOTEDPAIR>", "<ANY>"};
}
