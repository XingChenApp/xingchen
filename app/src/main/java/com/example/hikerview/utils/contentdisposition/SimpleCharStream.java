package com.example.hikerview.utils.contentdisposition;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * SimpleCharStream.smali 等同名 smali 共 1483 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 * GetSuffix 中 jadx 的 r2 伪影按 smali 订正为 bufpos。
 */
public class SimpleCharStream {
    public static final boolean staticFlag = false;
    int available;
    protected int[] bufcolumn;
    protected char[] buffer;
    protected int[] bufline;
    public int bufpos;
    int bufsize;
    protected int column;
    protected int inBuf;
    protected Reader inputStream;
    protected int line;
    protected int maxNextCharInd;
    protected boolean prevCharIsCR;
    protected boolean prevCharIsLF;
    protected int tabSize;
    int tokenBegin;

    protected void setTabSize(int i) {
        this.tabSize = i;
    }

    protected int getTabSize(int i) {
        return this.tabSize;
    }

    protected void ExpandBuff(boolean z) {
        int i = this.bufsize;
        char[] cArr = new char[i + 2048];
        int[] iArr = new int[i + 2048];
        int[] iArr2 = new int[i + 2048];
        try {
            if (z) {
                char[] cArr2 = this.buffer;
                int i2 = this.tokenBegin;
                System.arraycopy(cArr2, i2, cArr, 0, i - i2);
                System.arraycopy(this.buffer, 0, cArr, this.bufsize - this.tokenBegin, this.bufpos);
                this.buffer = cArr;
                int[] iArr3 = this.bufline;
                int i3 = this.tokenBegin;
                System.arraycopy(iArr3, i3, iArr, 0, this.bufsize - i3);
                System.arraycopy(this.bufline, 0, iArr, this.bufsize - this.tokenBegin, this.bufpos);
                this.bufline = iArr;
                int[] iArr4 = this.bufcolumn;
                int i4 = this.tokenBegin;
                System.arraycopy(iArr4, i4, iArr2, 0, this.bufsize - i4);
                System.arraycopy(this.bufcolumn, 0, iArr2, this.bufsize - this.tokenBegin, this.bufpos);
                this.bufcolumn = iArr2;
                int i5 = this.bufpos + (this.bufsize - this.tokenBegin);
                this.bufpos = i5;
                this.maxNextCharInd = i5;
            } else {
                char[] cArr3 = this.buffer;
                int i6 = this.tokenBegin;
                System.arraycopy(cArr3, i6, cArr, 0, i - i6);
                this.buffer = cArr;
                int[] iArr5 = this.bufline;
                int i7 = this.tokenBegin;
                System.arraycopy(iArr5, i7, iArr, 0, this.bufsize - i7);
                this.bufline = iArr;
                int[] iArr6 = this.bufcolumn;
                int i8 = this.tokenBegin;
                System.arraycopy(iArr6, i8, iArr2, 0, this.bufsize - i8);
                this.bufcolumn = iArr2;
                int i9 = this.bufpos - this.tokenBegin;
                this.bufpos = i9;
                this.maxNextCharInd = i9;
            }
            int i10 = this.bufsize + 2048;
            this.bufsize = i10;
            this.available = i10;
            this.tokenBegin = 0;
        } catch (Throwable th) {
            throw new Error(th.getMessage());
        }
    }

    protected void FillBuff() throws IOException {
        int i = this.maxNextCharInd;
        int i2 = this.available;
        if (i == i2) {
            int i3 = this.bufsize;
            if (i2 == i3) {
                int i4 = this.tokenBegin;
                if (i4 > 2048) {
                    this.maxNextCharInd = 0;
                    this.bufpos = 0;
                    this.available = i4;
                } else if (i4 < 0) {
                    this.maxNextCharInd = 0;
                    this.bufpos = 0;
                } else {
                    ExpandBuff(false);
                }
            } else {
                int i5 = this.tokenBegin;
                if (i2 > i5) {
                    this.available = i3;
                } else if (i5 - i2 < 2048) {
                    ExpandBuff(true);
                } else {
                    this.available = i5;
                }
            }
        }
        try {
            Reader reader = this.inputStream;
            char[] cArr = this.buffer;
            int i6 = this.maxNextCharInd;
            int read = reader.read(cArr, i6, this.available - i6);
            if (read == -1) {
                this.inputStream.close();
                throw new IOException();
            }
            this.maxNextCharInd += read;
        } catch (IOException e) {
            this.bufpos--;
            backup(0);
            if (this.tokenBegin == -1) {
                this.tokenBegin = this.bufpos;
            }
            throw e;
        }
    }

    public char BeginToken() throws IOException {
        this.tokenBegin = -1;
        char readChar = readChar();
        this.tokenBegin = this.bufpos;
        return readChar;
    }

    protected void UpdateLineColumn(char c) {
        this.column++;
        if (this.prevCharIsLF) {
            this.prevCharIsLF = false;
            int i = this.line;
            this.column = 1;
            this.line = i + 1;
        } else if (this.prevCharIsCR) {
            this.prevCharIsCR = false;
            if (c == '\n') {
                this.prevCharIsLF = true;
            } else {
                int i2 = this.line;
                this.column = 1;
                this.line = i2 + 1;
            }
        }
        if (c == '\t') {
            int i3 = this.column - 1;
            this.column = i3;
            int i4 = this.tabSize;
            this.column = i3 + (i4 - (i3 % i4));
        } else if (c == '\n') {
            this.prevCharIsLF = true;
        } else if (c == '\r') {
            this.prevCharIsCR = true;
        }
        int[] iArr = this.bufline;
        int i5 = this.bufpos;
        iArr[i5] = this.line;
        this.bufcolumn[i5] = this.column;
    }

    public char readChar() throws IOException {
        int i = this.inBuf;
        if (i > 0) {
            this.inBuf = i - 1;
            int i2 = this.bufpos + 1;
            this.bufpos = i2;
            if (i2 == this.bufsize) {
                this.bufpos = 0;
            }
            return this.buffer[this.bufpos];
        }
        int i3 = this.bufpos + 1;
        this.bufpos = i3;
        if (i3 >= this.maxNextCharInd) {
            FillBuff();
        }
        char c = this.buffer[this.bufpos];
        UpdateLineColumn(c);
        return c;
    }

    @Deprecated
    public int getColumn() {
        return this.bufcolumn[this.bufpos];
    }

    @Deprecated
    public int getLine() {
        return this.bufline[this.bufpos];
    }

    public int getEndColumn() {
        return this.bufcolumn[this.bufpos];
    }

    public int getEndLine() {
        return this.bufline[this.bufpos];
    }

    public int getBeginColumn() {
        return this.bufcolumn[this.tokenBegin];
    }

    public int getBeginLine() {
        return this.bufline[this.tokenBegin];
    }

    public void backup(int i) {
        this.inBuf += i;
        int i2 = this.bufpos - i;
        this.bufpos = i2;
        if (i2 < 0) {
            this.bufpos = i2 + this.bufsize;
        }
    }

    public SimpleCharStream(Reader reader, int i, int i2, int i3) {
        this.bufpos = -1;
        this.prevCharIsCR = false;
        this.prevCharIsLF = false;
        this.maxNextCharInd = 0;
        this.inBuf = 0;
        this.tabSize = 8;
        this.inputStream = reader;
        this.line = i;
        this.column = i2 - 1;
        this.bufsize = i3;
        this.available = i3;
        this.buffer = new char[i3];
        this.bufline = new int[i3];
        this.bufcolumn = new int[i3];
    }

    public SimpleCharStream(Reader reader, int i, int i2) {
        this(reader, i, i2, 4096);
    }

    public SimpleCharStream(Reader reader) {
        this(reader, 1, 1, 4096);
    }

    public void ReInit(Reader reader, int i, int i2, int i3) {
        this.inputStream = reader;
        this.line = i;
        this.column = i2 - 1;
        char[] cArr = this.buffer;
        if (cArr == null || i3 != cArr.length) {
            this.bufsize = i3;
            this.available = i3;
            this.buffer = new char[i3];
            this.bufline = new int[i3];
            this.bufcolumn = new int[i3];
        }
        this.prevCharIsCR = false;
        this.prevCharIsLF = false;
        this.maxNextCharInd = 0;
        this.inBuf = 0;
        this.tokenBegin = 0;
        this.bufpos = -1;
    }

    public void ReInit(Reader reader, int i, int i2) {
        ReInit(reader, i, i2, 4096);
    }

    public void ReInit(Reader reader) {
        ReInit(reader, 1, 1, 4096);
    }

    public SimpleCharStream(InputStream inputStream, String str, int i, int i2, int i3) throws UnsupportedEncodingException {
        this(str == null ? new InputStreamReader(inputStream) : new InputStreamReader(inputStream, str), i, i2, i3);
    }

    public SimpleCharStream(InputStream inputStream, int i, int i2, int i3) {
        this(new InputStreamReader(inputStream), i, i2, i3);
    }

    public SimpleCharStream(InputStream inputStream, String str, int i, int i2) throws UnsupportedEncodingException {
        this(inputStream, str, i, i2, 4096);
    }

    public SimpleCharStream(InputStream inputStream, int i, int i2) {
        this(inputStream, i, i2, 4096);
    }

    public SimpleCharStream(InputStream inputStream, String str) throws UnsupportedEncodingException {
        this(inputStream, str, 1, 1, 4096);
    }

    public SimpleCharStream(InputStream inputStream) {
        this(inputStream, 1, 1, 4096);
    }

    public void ReInit(InputStream inputStream, String str, int i, int i2, int i3) throws UnsupportedEncodingException {
        ReInit(str == null ? new InputStreamReader(inputStream) : new InputStreamReader(inputStream, str), i, i2, i3);
    }

    public void ReInit(InputStream inputStream, int i, int i2, int i3) {
        ReInit(new InputStreamReader(inputStream), i, i2, i3);
    }

    public void ReInit(InputStream inputStream, String str) throws UnsupportedEncodingException {
        ReInit(inputStream, str, 1, 1, 4096);
    }

    public void ReInit(InputStream inputStream) {
        ReInit(inputStream, 1, 1, 4096);
    }

    public void ReInit(InputStream inputStream, String str, int i, int i2) throws UnsupportedEncodingException {
        ReInit(inputStream, str, i, i2, 4096);
    }

    public void ReInit(InputStream inputStream, int i, int i2) {
        ReInit(inputStream, i, i2, 4096);
    }

    public String GetImage() {
        if (this.bufpos >= this.tokenBegin) {
            char[] cArr = this.buffer;
            int i = this.tokenBegin;
            return new String(cArr, i, (this.bufpos - i) + 1);
        }
        char[] cArr2 = this.buffer;
        int i2 = this.tokenBegin;
        return new String(cArr2, i2, this.bufsize - i2).concat(new String(this.buffer, 0, this.bufpos + 1));
    }

    public char[] GetSuffix(int i) {
        char[] cArr = new char[i];
        int i2 = this.bufpos;
        if (i2 + 1 >= i) {
            System.arraycopy(this.buffer, (i2 - i) + 1, cArr, 0, i);
        } else {
            System.arraycopy(this.buffer, this.bufsize - ((i - i2) - 1), cArr, 0, (i - i2) - 1);
            System.arraycopy(this.buffer, 0, cArr, (i - this.bufpos) - 1, this.bufpos + 1);
        }
        return cArr;
    }

    public void Done() {
        this.buffer = null;
        this.bufline = null;
        this.bufcolumn = null;
    }

    public void adjustBeginLineColumn(int i, int i2) {
        int i3;
        int i4 = this.tokenBegin;
        int i5 = this.bufpos;
        if (i5 >= i4) {
            i3 = (i5 - i4) + this.inBuf + 1;
        } else {
            i3 = this.inBuf + (this.bufsize - i4) + i5 + 1;
        }
        int i6 = 0;
        int i7 = 0;
        int i8 = 0;
        while (true) {
            if (i6 >= i3) {
                break;
            }
            int[] iArr = this.bufline;
            int i9 = this.bufsize;
            int i10 = i4 % i9;
            i4++;
            int i11 = i4 % i9;
            if (iArr[i10] != iArr[i11]) {
                i7 = i10;
                break;
            }
            iArr[i10] = i;
            int[] iArr2 = this.bufcolumn;
            int i12 = (iArr2[i11] + i8) - iArr2[i10];
            iArr2[i10] = i8 + i2;
            i6++;
            i8 = i12;
            i7 = i10;
        }
        if (i6 < i3) {
            int i13 = i + 1;
            this.bufline[i7] = i;
            this.bufcolumn[i7] = i2 + i8;
            while (true) {
                int i14 = i6 + 1;
                if (i6 >= i3) {
                    break;
                }
                int[] iArr3 = this.bufline;
                int i15 = this.bufsize;
                i7 = i4 % i15;
                i4++;
                if (iArr3[i7] != iArr3[i4 % i15]) {
                    iArr3[i7] = i13;
                    i13++;
                } else {
                    iArr3[i7] = i13;
                }
                i6 = i14;
            }
        }
        this.line = this.bufline[i7];
        this.column = this.bufcolumn[i7];
    }
}
