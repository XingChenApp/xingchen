package com.example.hikerview.utils.contentdisposition;

import java.io.Serializable;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * Token.smali 等同名 smali 共 108 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
public class Token implements Serializable {
    private static final long serialVersionUID = 1;
    public int beginColumn;
    public int beginLine;
    public int endColumn;
    public int endLine;
    public String image;
    public int kind;
    public Token next;
    public Token specialToken;

    public Object getValue() {
        return null;
    }

    public Token() {
    }

    public Token(int i) {
        this(i, null);
    }

    public Token(int i, String str) {
        this.kind = i;
        this.image = str;
    }

    public String toString() {
        return this.image;
    }

    public static Token newToken(int i, String str) {
        return new Token(i, str);
    }

    public static Token newToken(int i) {
        return newToken(i, null);
    }
}
