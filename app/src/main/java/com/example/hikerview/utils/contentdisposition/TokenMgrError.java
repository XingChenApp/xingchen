package com.example.hikerview.utils.contentdisposition;


/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * TokenMgrError.smali 等同名 smali 共 383 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 * addEscapes 的 default 支 jadx 多出一句不可达 break（真编不过），已按 smali 摘除（两子支各自 break、语义不变）。
 */
public class TokenMgrError extends Error {
    static final int INVALID_LEXICAL_STATE = 2;
    static final int LEXICAL_ERROR = 0;
    static final int LOOP_DETECTED = 3;
    static final int STATIC_LEXER_ERROR = 1;
    private static final long serialVersionUID = 1;
    int errorCode;

    protected static final String addEscapes(String str) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < str.length(); i++) {
            char charAt = str.charAt(i);
            if (charAt != 0) {
                if (charAt == '\"') {
                    stringBuffer.append("\\\"");
                } else if (charAt == '\'') {
                    stringBuffer.append("\\'");
                } else if (charAt == '\\') {
                    stringBuffer.append("\\\\");
                } else if (charAt == '\f') {
                    stringBuffer.append("\\f");
                } else if (charAt != '\r') {
                    switch (charAt) {
                        case 8:
                            stringBuffer.append("\\b");
                            break;
                        case '\t':
                            stringBuffer.append("\\t");
                            break;
                        case '\n':
                            stringBuffer.append("\\n");
                            break;
                        default:
                            char charAt2 = str.charAt(i);
                            if (charAt2 < ' ' || charAt2 > '~') {
                                String str2 = "0000" + Integer.toString(charAt2, 16);
                                stringBuffer.append("\\u" + str2.substring(str2.length() - 4, str2.length()));
                                break;
                            } else {
                                stringBuffer.append(charAt2);
                                break;
                            }
                    }
                } else {
                    stringBuffer.append("\\r");
                }
            }
        }
        return stringBuffer.toString();
    }

    protected static String LexicalError(boolean z, int i, int i2, int i3, String str, char c) {
        return "Lexical error at line " + i2 + ", column " + i3 + ".  Encountered: " + (z ? "<EOF> " : "\"" + addEscapes(String.valueOf(c)) + "\" (" + ((int) c) + "), ") + "after : \"" + addEscapes(str) + "\"";
    }

    @Override // java.lang.Throwable
    public String getMessage() {
        return super.getMessage();
    }

    public TokenMgrError() {
    }

    public TokenMgrError(String str, int i) {
        super(str);
        this.errorCode = i;
    }

    public TokenMgrError(boolean z, int i, int i2, int i3, String str, char c, int i4) {
        this(LexicalError(z, i, i2, i3, str, c), i4);
    }
}
