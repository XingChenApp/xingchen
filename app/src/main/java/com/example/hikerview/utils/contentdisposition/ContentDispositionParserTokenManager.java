package com.example.hikerview.utils.contentdisposition;

import java.io.IOException;
import java.io.PrintStream;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * ContentDispositionParserTokenManager.smali 等同名 smali 共 2895 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 * getNextToken 一法 jadx 反编译失败，已按 smali（同名段 437 行）逐条手工转写（JavaCC 模板形）；MoreLexicalActions/TokenLexicalActions 中 jadx 的 r0 伪影按 smali 订正为 image。
 * 另：jjMoveNfa 四法中 jadx 把官 shl-long 的 1L 误写为 int 移位 1（≥32 位全错位、DFA 吞串），已按 smali 全改回 1L（8 处）；真编＋解析用例验过。
 */
public class ContentDispositionParserTokenManager implements ContentDispositionParserConstants {
    static int commentNest;
    protected char curChar;
    int curLexState;
    public PrintStream debugStream;
    int defaultLexState;
    private StringBuilder image;
    protected SimpleCharStream input_stream;
    private final StringBuilder jjimage;
    private int jjimageLen;
    int jjmatchedKind;
    int jjmatchedPos;
    int jjnewStateCnt;
    int jjround;
    private final int[] jjrounds;
    private final int[] jjstateSet;
    private int lengthOfMatch;
    static final long[] jjbitVec0 = {0, 0, -1, -1};
    static final int[] jjnextStates = new int[0];
    public static final String[] jjstrLiteralImages = {"", "\r", "\n", ";", "=", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null};
    public static final String[] lexStateNames = {"DEFAULT", "INCOMMENT", "NESTED_COMMENT", "INQUOTEDSTRING"};
    public static final int[] jjnewLexState = {-1, -1, -1, -1, -1, -1, 1, 0, -1, 2, -1, -1, -1, -1, -1, 3, -1, -1, 0, -1, -1, -1, -1};
    static final long[] jjtoToken = {1835039};
    static final long[] jjtoSkip = {160};
    static final long[] jjtoSpecial = {32};
    static final long[] jjtoMore = {261952};

    private final int jjStopStringLiteralDfa_0(int i, long j) {
        return -1;
    }

    private final int jjStopStringLiteralDfa_1(int i, long j) {
        return -1;
    }

    private final int jjStopStringLiteralDfa_2(int i, long j) {
        return -1;
    }

    private final int jjStopStringLiteralDfa_3(int i, long j) {
        return -1;
    }

    public void setDebugStream(PrintStream printStream) {
        this.debugStream = printStream;
    }

    private final int jjStartNfa_0(int i, long j) {
        return jjMoveNfa_0(jjStopStringLiteralDfa_0(i, j), i + 1);
    }

    private int jjStopAtPos(int i, int i2) {
        this.jjmatchedKind = i2;
        this.jjmatchedPos = i;
        return i + 1;
    }

    private int jjMoveStringLiteralDfa0_0() {
        char c = this.curChar;
        if (c == '\n') {
            return jjStartNfaWithStates_0(0, 2, 2);
        }
        if (c == '\r') {
            return jjStartNfaWithStates_0(0, 1, 2);
        }
        if (c == '\"') {
            return jjStopAtPos(0, 15);
        }
        if (c == '(') {
            return jjStopAtPos(0, 6);
        }
        if (c == ';') {
            return jjStopAtPos(0, 3);
        }
        if (c == '=') {
            return jjStopAtPos(0, 4);
        }
        return jjMoveNfa_0(3, 0);
    }

    private int jjStartNfaWithStates_0(int i, int i2, int i3) {
        this.jjmatchedKind = i2;
        this.jjmatchedPos = i;
        try {
            this.curChar = this.input_stream.readChar();
            return jjMoveNfa_0(i3, i + 1);
        } catch (IOException unused) {
            return i + 1;
        }
    }

    private int jjMoveNfa_0(int i, int i2) {
        this.jjnewStateCnt = 3;
        this.jjstateSet[0] = i;
        int i3 = Integer.MAX_VALUE;
        int i4 = i2;
        int i5 = 1;
        int i6 = 0;
        int i7 = Integer.MAX_VALUE;
        while (true) {
            int i8 = this.jjround + 1;
            this.jjround = i8;
            if (i8 == i3) {
                ReInitRounds();
            }
            char c = this.curChar;
            if (c < '@') {
                long j = 1L << c;
                do {
                    i5--;
                    int i9 = this.jjstateSet[i5];
                    if (i9 != 0) {
                        if (i9 != 1) {
                            if (i9 != 2) {
                                if (i9 == 3) {
                                    if ((j & 288068726467591679L) != 0) {
                                        if (i7 > 20) {
                                            i7 = 20;
                                        }
                                        jjCheckNAdd(2);
                                    } else if ((j & 4294967808L) != 0) {
                                        if (i7 > 5) {
                                            i7 = 5;
                                        }
                                        jjCheckNAdd(0);
                                    }
                                    if ((j & 287948901175001088L) != 0) {
                                        int i10 = i7 <= 19 ? i7 : 19;
                                        jjCheckNAdd(1);
                                        i7 = i10;
                                    }
                                }
                            } else if ((j & 288068726467591679L) != 0) {
                                if (i7 > 20) {
                                    i7 = 20;
                                }
                                jjCheckNAdd(2);
                            }
                        } else if ((j & 287948901175001088L) != 0) {
                            if (i7 > 19) {
                                i7 = 19;
                            }
                            jjCheckNAdd(1);
                        }
                    } else if ((j & 4294967808L) != 0) {
                        jjCheckNAdd(0);
                        i7 = 5;
                    }
                } while (i5 != i6);
            } else if (c < 128) {
                long j2 = 1L << (c & 63);
                do {
                    i5--;
                    int i11 = this.jjstateSet[i5];
                    if ((i11 == 2 || i11 == 3) && (j2 & (-939524098)) != 0) {
                        jjCheckNAdd(2);
                        i7 = 20;
                    }
                } while (i5 != i6);
            } else {
                int i12 = (c & 255) >> 6;
                long j3 = 1L << (c & 63);
                do {
                    i5--;
                    int i13 = this.jjstateSet[i5];
                    if ((i13 == 2 || i13 == 3) && (jjbitVec0[i12] & j3) != 0) {
                        if (i7 > 20) {
                            i7 = 20;
                        }
                        jjCheckNAdd(2);
                    }
                } while (i5 != i6);
            }
            if (i7 != Integer.MAX_VALUE) {
                this.jjmatchedKind = i7;
                this.jjmatchedPos = i4;
                i7 = Integer.MAX_VALUE;
            }
            i4++;
            i5 = this.jjnewStateCnt;
            this.jjnewStateCnt = i6;
            i6 = 3 - i6;
            if (i5 == i6) {
                return i4;
            }
            try {
                this.curChar = this.input_stream.readChar();
                i3 = Integer.MAX_VALUE;
            } catch (IOException unused) {
                return i4;
            }
        }
    }

    private final int jjStartNfa_1(int i, long j) {
        return jjMoveNfa_1(jjStopStringLiteralDfa_1(i, j), i + 1);
    }

    private int jjMoveStringLiteralDfa0_1() {
        char c = this.curChar;
        if (c == '(') {
            return jjStopAtPos(0, 9);
        }
        if (c == ')') {
            return jjStopAtPos(0, 7);
        }
        return jjMoveNfa_1(0, 0);
    }

    private int jjMoveNfa_1(int i, int i2) {
        this.jjnewStateCnt = 3;
        this.jjstateSet[0] = i;
        int i3 = 1;
        int i4 = 0;
        int i5 = Integer.MAX_VALUE;
        int i6 = i2;
        while (true) {
            int i7 = this.jjround + 1;
            this.jjround = i7;
            if (i7 == Integer.MAX_VALUE) {
                ReInitRounds();
            }
            char c = this.curChar;
            if (c < '@') {
                do {
                    i3--;
                    int i8 = this.jjstateSet[i3];
                    if (i8 != 0) {
                        if (i8 == 1 && i5 > 8) {
                            i5 = 8;
                        }
                    } else if (i5 > 10) {
                        i5 = 10;
                    }
                } while (i3 != i4);
            } else if (c < 128) {
                do {
                    int[] iArr = this.jjstateSet;
                    i3--;
                    int i9 = iArr[i3];
                    if (i9 == 0) {
                        if (i5 > 10) {
                            i5 = 10;
                        }
                        if (this.curChar == '\\') {
                            int i10 = this.jjnewStateCnt;
                            this.jjnewStateCnt = i10 + 1;
                            iArr[i10] = 1;
                        }
                    } else if (i9 != 1) {
                        if (i9 == 2 && i5 > 10) {
                            i5 = 10;
                        }
                    } else if (i5 > 8) {
                        i5 = 8;
                    }
                } while (i3 != i4);
            } else {
                int i11 = (c & 255) >> 6;
                long j = 1L << (c & 63);
                do {
                    i3--;
                    int i12 = this.jjstateSet[i3];
                    if (i12 != 0) {
                        if (i12 == 1 && (jjbitVec0[i11] & j) != 0 && i5 > 8) {
                            i5 = 8;
                        }
                    } else if ((jjbitVec0[i11] & j) != 0 && i5 > 10) {
                        i5 = 10;
                    }
                } while (i3 != i4);
            }
            if (i5 != Integer.MAX_VALUE) {
                this.jjmatchedKind = i5;
                this.jjmatchedPos = i6;
                i5 = Integer.MAX_VALUE;
            }
            i6++;
            i3 = this.jjnewStateCnt;
            this.jjnewStateCnt = i4;
            i4 = 3 - i4;
            if (i3 == i4) {
                return i6;
            }
            try {
                this.curChar = this.input_stream.readChar();
            } catch (IOException unused) {
                return i6;
            }
        }
    }

    private final int jjStartNfa_3(int i, long j) {
        return jjMoveNfa_3(jjStopStringLiteralDfa_3(i, j), i + 1);
    }

    private int jjMoveStringLiteralDfa0_3() {
        if (this.curChar == '\"') {
            return jjStopAtPos(0, 18);
        }
        return jjMoveNfa_3(0, 0);
    }

    private int jjMoveNfa_3(int i, int i2) {
        this.jjnewStateCnt = 3;
        this.jjstateSet[0] = i;
        int i3 = 1;
        int i4 = 0;
        int i5 = Integer.MAX_VALUE;
        int i6 = i2;
        while (true) {
            int i7 = this.jjround + 1;
            this.jjround = i7;
            if (i7 == Integer.MAX_VALUE) {
                ReInitRounds();
            }
            char c = this.curChar;
            if (c < '@') {
                long j = 1L << c;
                do {
                    i3--;
                    int i8 = this.jjstateSet[i3];
                    if (i8 != 0) {
                        if (i8 != 1) {
                            if (i8 != 2) {
                            }
                        } else if (i5 > 16) {
                            i5 = 16;
                        }
                    }
                    if ((j & (-17179869185L)) != 0) {
                        if (i5 > 17) {
                            i5 = 17;
                        }
                        jjCheckNAdd(2);
                    }
                } while (i3 != i4);
            } else if (c < 128) {
                long j2 = 1L << (c & 63);
                do {
                    int[] iArr = this.jjstateSet;
                    i3--;
                    int i9 = iArr[i3];
                    if (i9 != 0) {
                        if (i9 != 1) {
                            if (i9 == 2 && (j2 & (-268435457)) != 0) {
                                if (i5 > 17) {
                                    i5 = 17;
                                }
                                jjCheckNAdd(2);
                            }
                        } else if (i5 > 16) {
                            i5 = 16;
                        }
                    } else if ((j2 & (-268435457)) != 0) {
                        if (i5 > 17) {
                            i5 = 17;
                        }
                        jjCheckNAdd(2);
                    } else if (this.curChar == '\\') {
                        int i10 = this.jjnewStateCnt;
                        this.jjnewStateCnt = i10 + 1;
                        iArr[i10] = 1;
                    }
                } while (i3 != i4);
            } else {
                int i11 = (c & 255) >> 6;
                long j3 = 1L << (c & 63);
                do {
                    i3--;
                    int i12 = this.jjstateSet[i3];
                    if (i12 != 0) {
                        if (i12 != 1) {
                            if (i12 != 2) {
                            }
                        } else if ((jjbitVec0[i11] & j3) != 0 && i5 > 16) {
                            i5 = 16;
                        }
                    }
                    if ((jjbitVec0[i11] & j3) != 0) {
                        if (i5 > 17) {
                            i5 = 17;
                        }
                        jjCheckNAdd(2);
                    }
                } while (i3 != i4);
            }
            if (i5 != Integer.MAX_VALUE) {
                this.jjmatchedKind = i5;
                this.jjmatchedPos = i6;
                i5 = Integer.MAX_VALUE;
            }
            i6++;
            i3 = this.jjnewStateCnt;
            this.jjnewStateCnt = i4;
            i4 = 3 - i4;
            if (i3 == i4) {
                return i6;
            }
            try {
                this.curChar = this.input_stream.readChar();
            } catch (IOException unused) {
                return i6;
            }
        }
    }

    private final int jjStartNfa_2(int i, long j) {
        return jjMoveNfa_2(jjStopStringLiteralDfa_2(i, j), i + 1);
    }

    private int jjMoveStringLiteralDfa0_2() {
        char c = this.curChar;
        if (c == '(') {
            return jjStopAtPos(0, 12);
        }
        if (c == ')') {
            return jjStopAtPos(0, 13);
        }
        return jjMoveNfa_2(0, 0);
    }

    private int jjMoveNfa_2(int i, int i2) {
        this.jjnewStateCnt = 3;
        this.jjstateSet[0] = i;
        int i3 = 1;
        int i4 = 0;
        int i5 = Integer.MAX_VALUE;
        int i6 = i2;
        while (true) {
            int i7 = this.jjround + 1;
            this.jjround = i7;
            if (i7 == Integer.MAX_VALUE) {
                ReInitRounds();
            }
            char c = this.curChar;
            if (c < '@') {
                do {
                    i3--;
                    int i8 = this.jjstateSet[i3];
                    if (i8 != 0) {
                        if (i8 == 1 && i5 > 11) {
                            i5 = 11;
                        }
                    } else if (i5 > 14) {
                        i5 = 14;
                    }
                } while (i3 != i4);
            } else if (c < 128) {
                do {
                    int[] iArr = this.jjstateSet;
                    i3--;
                    int i9 = iArr[i3];
                    if (i9 == 0) {
                        if (i5 > 14) {
                            i5 = 14;
                        }
                        if (this.curChar == '\\') {
                            int i10 = this.jjnewStateCnt;
                            this.jjnewStateCnt = i10 + 1;
                            iArr[i10] = 1;
                        }
                    } else if (i9 != 1) {
                        if (i9 == 2 && i5 > 14) {
                            i5 = 14;
                        }
                    } else if (i5 > 11) {
                        i5 = 11;
                    }
                } while (i3 != i4);
            } else {
                int i11 = (c & 255) >> 6;
                long j = 1L << (c & 63);
                do {
                    i3--;
                    int i12 = this.jjstateSet[i3];
                    if (i12 != 0) {
                        if (i12 == 1 && (jjbitVec0[i11] & j) != 0 && i5 > 11) {
                            i5 = 11;
                        }
                    } else if ((jjbitVec0[i11] & j) != 0 && i5 > 14) {
                        i5 = 14;
                    }
                } while (i3 != i4);
            }
            if (i5 != Integer.MAX_VALUE) {
                this.jjmatchedKind = i5;
                this.jjmatchedPos = i6;
                i5 = Integer.MAX_VALUE;
            }
            i6++;
            i3 = this.jjnewStateCnt;
            this.jjnewStateCnt = i4;
            i4 = 3 - i4;
            if (i3 == i4) {
                return i6;
            }
            try {
                this.curChar = this.input_stream.readChar();
            } catch (IOException unused) {
                return i6;
            }
        }
    }

    public ContentDispositionParserTokenManager(SimpleCharStream simpleCharStream) {
        this.debugStream = System.out;
        this.jjrounds = new int[3];
        this.jjstateSet = new int[6];
        StringBuilder sb = new StringBuilder();
        this.jjimage = sb;
        this.image = sb;
        this.curLexState = 0;
        this.defaultLexState = 0;
        this.input_stream = simpleCharStream;
    }

    public ContentDispositionParserTokenManager(SimpleCharStream simpleCharStream, int i) {
        this(simpleCharStream);
        SwitchTo(i);
    }

    public void ReInit(SimpleCharStream simpleCharStream) {
        this.jjnewStateCnt = 0;
        this.jjmatchedPos = 0;
        this.curLexState = this.defaultLexState;
        this.input_stream = simpleCharStream;
        ReInitRounds();
    }

    private void ReInitRounds() {
        this.jjround = -2147483647;
        int i = 3;
        while (true) {
            int i2 = i - 1;
            if (i <= 0) {
                return;
            }
            this.jjrounds[i2] = Integer.MIN_VALUE;
            i = i2;
        }
    }

    public void ReInit(SimpleCharStream simpleCharStream, int i) {
        ReInit(simpleCharStream);
        SwitchTo(i);
    }

    public void SwitchTo(int i) {
        if (i >= 4 || i < 0) {
            throw new TokenMgrError("Error: Ignoring invalid lexical state : " + i + ". State unchanged.", 2);
        }
        this.curLexState = i;
    }

    protected Token jjFillToken() {
        String str = jjstrLiteralImages[this.jjmatchedKind];
        if (str == null) {
            str = this.input_stream.GetImage();
        }
        int beginLine = this.input_stream.getBeginLine();
        int beginColumn = this.input_stream.getBeginColumn();
        int endLine = this.input_stream.getEndLine();
        int endColumn = this.input_stream.getEndColumn();
        Token newToken = Token.newToken(this.jjmatchedKind, str);
        newToken.beginLine = beginLine;
        newToken.endLine = endLine;
        newToken.beginColumn = beginColumn;
        newToken.endColumn = endColumn;
        return newToken;
    }

    // 按官 smali 逐条手工转写（jadx 对本法反编译失败）：JavaCC 生成模板原形，
    // 外层 EOFLoop 回 BeginToken、内层按 curLexState 分发四态 DFA。
    public Token getNextToken() {
        Token specialToken = null;
        Token matchedToken;
        int curPos = 0;
        EOFLoop:
        while (true) {
            try {
                this.curChar = this.input_stream.BeginToken();
            } catch (IOException e) {
                this.jjmatchedKind = 0;
                matchedToken = jjFillToken();
                matchedToken.specialToken = specialToken;
                return matchedToken;
            }
            this.image = this.jjimage;
            this.image.setLength(0);
            this.jjimageLen = 0;
            while (true) {
                switch (this.curLexState) {
                    case 0:
                        this.jjmatchedKind = Integer.MAX_VALUE;
                        this.jjmatchedPos = 0;
                        curPos = jjMoveStringLiteralDfa0_0();
                        break;
                    case 1:
                        this.jjmatchedKind = Integer.MAX_VALUE;
                        this.jjmatchedPos = 0;
                        curPos = jjMoveStringLiteralDfa0_1();
                        break;
                    case 2:
                        this.jjmatchedKind = Integer.MAX_VALUE;
                        this.jjmatchedPos = 0;
                        curPos = jjMoveStringLiteralDfa0_2();
                        break;
                    case 3:
                        this.jjmatchedKind = Integer.MAX_VALUE;
                        this.jjmatchedPos = 0;
                        curPos = jjMoveStringLiteralDfa0_3();
                        break;
                }
                if (this.jjmatchedKind != Integer.MAX_VALUE) {
                    if (this.jjmatchedPos + 1 < curPos) {
                        this.input_stream.backup(curPos - this.jjmatchedPos - 1);
                    }
                    if ((jjtoToken[this.jjmatchedKind >> 6] & (1L << (this.jjmatchedKind & 63))) != 0) {
                        matchedToken = jjFillToken();
                        matchedToken.specialToken = specialToken;
                        TokenLexicalActions(matchedToken);
                        if (jjnewLexState[this.jjmatchedKind] != -1) {
                            this.curLexState = jjnewLexState[this.jjmatchedKind];
                        }
                        return matchedToken;
                    } else if ((jjtoSkip[this.jjmatchedKind >> 6] & (1L << (this.jjmatchedKind & 63))) != 0) {
                        if ((jjtoSpecial[this.jjmatchedKind >> 6] & (1L << (this.jjmatchedKind & 63))) != 0) {
                            matchedToken = jjFillToken();
                            if (specialToken != null) {
                                matchedToken.specialToken = specialToken;
                                specialToken.next = matchedToken;
                            }
                            specialToken = matchedToken;
                        }
                        if (jjnewLexState[this.jjmatchedKind] != -1) {
                            this.curLexState = jjnewLexState[this.jjmatchedKind];
                        }
                        continue EOFLoop;
                    } else {
                        MoreLexicalActions();
                        if (jjnewLexState[this.jjmatchedKind] != -1) {
                            this.curLexState = jjnewLexState[this.jjmatchedKind];
                        }
                        this.jjmatchedKind = Integer.MAX_VALUE;
                        try {
                            this.curChar = this.input_stream.readChar();
                            curPos = 0;
                            continue;
                        } catch (IOException e) {
                            curPos = 0;
                        }
                    }
                }
                int errorLine = this.input_stream.getEndLine();
                int errorColumn = this.input_stream.getEndColumn();
                String errorAfter = null;
                boolean EOFSeen = false;
                try {
                    this.input_stream.readChar();
                    this.input_stream.backup(1);
                } catch (IOException e1) {
                    EOFSeen = true;
                    errorAfter = curPos <= 1 ? "" : this.input_stream.GetImage();
                    if (this.curChar == '\n' || this.curChar == '\r') {
                        errorLine++;
                        errorColumn = 0;
                    } else {
                        errorColumn++;
                    }
                }
                if (!EOFSeen) {
                    this.input_stream.backup(1);
                    errorAfter = curPos <= 1 ? "" : this.input_stream.GetImage();
                }
                throw new TokenMgrError(EOFSeen, this.curLexState, errorLine, errorColumn, errorAfter, this.curChar, 0);
            }
        }
    }

    void MoreLexicalActions() {
        int i = this.jjimageLen;
        int i2 = this.jjmatchedPos + 1;
        this.lengthOfMatch = i2;
        int i3 = i + i2;
        this.jjimageLen = i3;
        switch (this.jjmatchedKind) {
            case 8:
                this.image.append(this.input_stream.GetSuffix(i3));
                this.jjimageLen = 0;
                this.image.deleteCharAt(this.image.length() - 2);
                break;
            case 9:
                this.image.append(this.input_stream.GetSuffix(i3));
                this.jjimageLen = 0;
                commentNest = 1;
                break;
            case 11:
                this.image.append(this.input_stream.GetSuffix(i3));
                this.jjimageLen = 0;
                this.image.deleteCharAt(this.image.length() - 2);
                break;
            case 12:
                this.image.append(this.input_stream.GetSuffix(i3));
                this.jjimageLen = 0;
                commentNest++;
                break;
            case 13:
                this.image.append(this.input_stream.GetSuffix(i3));
                this.jjimageLen = 0;
                int i4 = commentNest - 1;
                commentNest = i4;
                if (i4 == 0) {
                    SwitchTo(1);
                    break;
                }
                break;
            case 15:
                this.image.append(this.input_stream.GetSuffix(i3));
                this.jjimageLen = 0;
                StringBuilder sb = this.image;
                sb.deleteCharAt(sb.length() - 1);
                break;
            case 16:
                this.image.append(this.input_stream.GetSuffix(i3));
                this.jjimageLen = 0;
                this.image.deleteCharAt(this.image.length() - 2);
                break;
        }
    }

    void TokenLexicalActions(Token token) {
        if (this.jjmatchedKind != 18) {
            return;
        }
        StringBuilder sb = this.image;
        SimpleCharStream simpleCharStream = this.input_stream;
        int i = this.jjimageLen;
        int i2 = this.jjmatchedPos + 1;
        this.lengthOfMatch = i2;
        sb.append(simpleCharStream.GetSuffix(i + i2));
        token.image = this.image.substring(0, this.image.length() - 1);
    }

    private void jjCheckNAdd(int i) {
        int[] iArr = this.jjrounds;
        int i2 = iArr[i];
        int i3 = this.jjround;
        if (i2 != i3) {
            int[] iArr2 = this.jjstateSet;
            int i4 = this.jjnewStateCnt;
            this.jjnewStateCnt = i4 + 1;
            iArr2[i4] = i;
            iArr[i] = i3;
        }
    }

    private void jjAddStates(int i, int i2) {
        while (true) {
            int[] iArr = this.jjstateSet;
            int i3 = this.jjnewStateCnt;
            this.jjnewStateCnt = i3 + 1;
            iArr[i3] = jjnextStates[i];
            int i4 = i + 1;
            if (i == i2) {
                return;
            } else {
                i = i4;
            }
        }
    }

    private void jjCheckNAddTwoStates(int i, int i2) {
        jjCheckNAdd(i);
        jjCheckNAdd(i2);
    }
}
