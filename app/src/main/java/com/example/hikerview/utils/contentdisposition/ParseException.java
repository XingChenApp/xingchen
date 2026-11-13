package com.example.hikerview.utils.contentdisposition;


/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * ParseException.smali 等同名 smali 共 732 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 * add_escapes 的 default 支 jadx 多出一句不可达 break（真编不过），已按 smali 摘除（两子支各自 break、语义不变）。
 */
public class ParseException extends Exception {
    private static final long serialVersionUID = 1;
    public Token currentToken;
    protected String eol;
    public int[][] expectedTokenSequences;
    protected boolean specialConstructor;
    public String[] tokenImage;

    protected ParseException(String str, Throwable th) {
        super(str, th);
        this.eol = System.getProperty("line.separator", "\n");
    }

    public ParseException(Token token, int[][] iArr, String[] strArr) {
        super("");
        this.eol = System.getProperty("line.separator", "\n");
        this.specialConstructor = true;
        this.currentToken = token;
        this.expectedTokenSequences = iArr;
        this.tokenImage = strArr;
    }

    public ParseException() {
        super("Cannot parse field");
        this.eol = System.getProperty("line.separator", "\n");
        this.specialConstructor = false;
    }

    public ParseException(Throwable th) {
        super(th);
        this.eol = System.getProperty("line.separator", "\n");
        this.specialConstructor = false;
    }

    public ParseException(String str) {
        super(str);
        this.eol = System.getProperty("line.separator", "\n");
        this.specialConstructor = false;
    }

    @Override // java.lang.Throwable
    public String getMessage() {
        String str;
        int[] iArr;
        if (!this.specialConstructor) {
            return super.getMessage();
        }
        StringBuffer stringBuffer = new StringBuffer();
        int i = 0;
        int i2 = 0;
        while (true) {
            int[][] iArr2 = this.expectedTokenSequences;
            if (i >= iArr2.length) {
                break;
            }
            int[] iArr3 = iArr2[i];
            if (i2 < iArr3.length) {
                i2 = iArr3.length;
            }
            int i3 = 0;
            while (true) {
                iArr = this.expectedTokenSequences[i];
                if (i3 >= iArr.length) {
                    break;
                }
                stringBuffer.append(this.tokenImage[iArr[i3]]).append(" ");
                i3++;
            }
            if (iArr[iArr.length - 1] != 0) {
                stringBuffer.append("...");
            }
            stringBuffer.append(this.eol).append("    ");
            i++;
        }
        Token token = this.currentToken.next;
        String str2 = "Encountered \"";
        int i4 = 0;
        while (true) {
            if (i4 >= i2) {
                break;
            }
            if (i4 != 0) {
                str2 = str2 + " ";
            }
            if (token.kind == 0) {
                str2 = str2 + this.tokenImage[0];
                break;
            }
            str2 = str2 + add_escapes(token.image);
            token = token.next;
            i4++;
        }
        String str3 = (str2 + "\" at line " + this.currentToken.next.beginLine + ", column " + this.currentToken.next.beginColumn) + "." + this.eol;
        if (this.expectedTokenSequences.length == 1) {
            str = str3 + "Was expecting:" + this.eol + "    ";
        } else {
            str = str3 + "Was expecting one of:" + this.eol + "    ";
        }
        return str + stringBuffer.toString();
    }

    protected String add_escapes(String str) {
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
}
