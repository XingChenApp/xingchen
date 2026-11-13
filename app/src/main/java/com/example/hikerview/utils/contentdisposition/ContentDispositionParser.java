package com.example.hikerview.utils.contentdisposition;

import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * ContentDispositionParser.smali 等同名 smali 共 1182 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
public class ContentDispositionParser implements ContentDispositionParserConstants {
    private static int[] jj_la1_0;
    private String dispositionType;
    private List<int[]> jj_expentries;
    private int[] jj_expentry;
    private int jj_gen;
    SimpleCharStream jj_input_stream;
    private int jj_kind;
    private final int[] jj_la1;
    public Token jj_nt;
    private int jj_ntk;
    private final MimeParameterMapping mapping;
    public Token token;
    public ContentDispositionParserTokenManager token_source;

    public final void disable_tracing() {
    }

    public final void enable_tracing() {
    }

    public String getDispositionType() {
        return this.dispositionType;
    }

    public Map<String, String> getParameters() {
        return this.mapping.getParameters();
    }

    public static void main(String[] strArr) throws ParseException {
        while (true) {
            try {
                new ContentDispositionParser(System.in).parseLine();
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }

    public final void parseLine() throws ParseException {
        parse();
        int i = this.jj_ntk;
        if (i == -1) {
            i = jj_ntk();
        }
        if (i == 1) {
            jj_consume_token(1);
        } else {
            this.jj_la1[0] = this.jj_gen;
        }
        jj_consume_token(2);
    }

    public final void parseAll() throws ParseException {
        parse();
        jj_consume_token(0);
    }

    public final void parse() throws ParseException {
        this.dispositionType = jj_consume_token(20).image;
        while (true) {
            int i = this.jj_ntk;
            if (i == -1) {
                i = jj_ntk();
            }
            if (i != 3) {
                this.jj_la1[1] = this.jj_gen;
                return;
            } else {
                jj_consume_token(3);
                parameter();
            }
        }
    }

    public final void parameter() throws ParseException {
        Token jj_consume_token = jj_consume_token(20);
        jj_consume_token(4);
        this.mapping.addParameter(jj_consume_token.image, value());
    }

    public final String value() throws ParseException {
        Token jj_consume_token;
        int i = this.jj_ntk;
        if (i == -1) {
            i = jj_ntk();
        }
        switch (i) {
            case ContentDispositionParserConstants.QUOTEDSTRING /* 18 */:
                jj_consume_token = jj_consume_token(18);
                break;
            case ContentDispositionParserConstants.DIGITS /* 19 */:
                jj_consume_token = jj_consume_token(19);
                break;
            case ContentDispositionParserConstants.ATOKEN /* 20 */:
                jj_consume_token = jj_consume_token(20);
                break;
            default:
                this.jj_la1[2] = this.jj_gen;
                jj_consume_token(-1);
                throw new ParseException();
        }
        return jj_consume_token.image;
    }

    static {
        jj_la1_init_0();
    }

    private static void jj_la1_init_0() {
        jj_la1_0 = new int[]{2, 8, 1835008};
    }

    public ContentDispositionParser(InputStream inputStream) {
        this(inputStream, null);
    }

    public ContentDispositionParser(InputStream inputStream, String str) {
        this.mapping = new MimeParameterMapping();
        this.jj_la1 = new int[3];
        this.jj_expentries = new ArrayList();
        this.jj_kind = -1;
        try {
            this.jj_input_stream = new SimpleCharStream(inputStream, str, 1, 1);
            this.token_source = new ContentDispositionParserTokenManager(this.jj_input_stream);
            this.token = new Token();
            this.jj_ntk = -1;
            this.jj_gen = 0;
            for (int i = 0; i < 3; i++) {
                this.jj_la1[i] = -1;
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public void ReInit(InputStream inputStream) {
        ReInit(inputStream, null);
    }

    public void ReInit(InputStream inputStream, String str) {
        try {
            this.jj_input_stream.ReInit(inputStream, str, 1, 1);
            this.token_source.ReInit(this.jj_input_stream);
            this.token = new Token();
            this.jj_ntk = -1;
            this.jj_gen = 0;
            for (int i = 0; i < 3; i++) {
                this.jj_la1[i] = -1;
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public ContentDispositionParser(Reader reader) {
        this.mapping = new MimeParameterMapping();
        this.jj_la1 = new int[3];
        this.jj_expentries = new ArrayList();
        this.jj_kind = -1;
        this.jj_input_stream = new SimpleCharStream(reader, 1, 1);
        this.token_source = new ContentDispositionParserTokenManager(this.jj_input_stream);
        this.token = new Token();
        this.jj_ntk = -1;
        this.jj_gen = 0;
        for (int i = 0; i < 3; i++) {
            this.jj_la1[i] = -1;
        }
    }

    public void ReInit(Reader reader) {
        this.jj_input_stream.ReInit(reader, 1, 1);
        this.token_source.ReInit(this.jj_input_stream);
        this.token = new Token();
        this.jj_ntk = -1;
        this.jj_gen = 0;
        for (int i = 0; i < 3; i++) {
            this.jj_la1[i] = -1;
        }
    }

    public ContentDispositionParser(ContentDispositionParserTokenManager contentDispositionParserTokenManager) {
        this.mapping = new MimeParameterMapping();
        this.jj_la1 = new int[3];
        this.jj_expentries = new ArrayList();
        this.jj_kind = -1;
        this.token_source = contentDispositionParserTokenManager;
        this.token = new Token();
        this.jj_ntk = -1;
        this.jj_gen = 0;
        for (int i = 0; i < 3; i++) {
            this.jj_la1[i] = -1;
        }
    }

    public void ReInit(ContentDispositionParserTokenManager contentDispositionParserTokenManager) {
        this.token_source = contentDispositionParserTokenManager;
        this.token = new Token();
        this.jj_ntk = -1;
        this.jj_gen = 0;
        for (int i = 0; i < 3; i++) {
            this.jj_la1[i] = -1;
        }
    }

    private Token jj_consume_token(int i) throws ParseException {
        Token token = this.token;
        if (token.next != null) {
            this.token = this.token.next;
        } else {
            Token token2 = this.token;
            Token nextToken = this.token_source.getNextToken();
            token2.next = nextToken;
            this.token = nextToken;
        }
        this.jj_ntk = -1;
        if (this.token.kind == i) {
            this.jj_gen++;
            return this.token;
        }
        this.token = token;
        this.jj_kind = i;
        throw generateParseException();
    }

    public final Token getNextToken() {
        if (this.token.next != null) {
            this.token = this.token.next;
        } else {
            Token token = this.token;
            Token nextToken = this.token_source.getNextToken();
            token.next = nextToken;
            this.token = nextToken;
        }
        this.jj_ntk = -1;
        this.jj_gen++;
        return this.token;
    }

    public final Token getToken(int i) {
        Token token = this.token;
        for (int i2 = 0; i2 < i; i2++) {
            if (token.next != null) {
                token = token.next;
            } else {
                Token nextToken = this.token_source.getNextToken();
                token.next = nextToken;
                token = nextToken;
            }
        }
        return token;
    }

    private int jj_ntk() {
        Token token = this.token.next;
        this.jj_nt = token;
        if (token == null) {
            Token token2 = this.token;
            Token nextToken = this.token_source.getNextToken();
            token2.next = nextToken;
            int i = nextToken.kind;
            this.jj_ntk = i;
            return i;
        }
        int i2 = token.kind;
        this.jj_ntk = i2;
        return i2;
    }

    public ParseException generateParseException() {
        this.jj_expentries.clear();
        boolean[] zArr = new boolean[23];
        int i = this.jj_kind;
        if (i >= 0) {
            zArr[i] = true;
            this.jj_kind = -1;
        }
        for (int i2 = 0; i2 < 3; i2++) {
            if (this.jj_la1[i2] == this.jj_gen) {
                for (int i3 = 0; i3 < 32; i3++) {
                    if ((jj_la1_0[i2] & (1 << i3)) != 0) {
                        zArr[i3] = true;
                    }
                }
            }
        }
        for (int i4 = 0; i4 < 23; i4++) {
            if (zArr[i4]) {
                int[] iArr = {i4};
                this.jj_expentry = iArr;
                this.jj_expentries.add(iArr);
            }
        }
        int[][] iArr2 = new int[this.jj_expentries.size()][];
        for (int i5 = 0; i5 < this.jj_expentries.size(); i5++) {
            iArr2[i5] = this.jj_expentries.get(i5);
        }
        return new ParseException(this.token, iArr2, tokenImage);
    }
}
