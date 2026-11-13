package com.example.hikerview.utils.contentdisposition;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * QuotedPrintableOutputStream.smali 等同名 smali 共 762 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
public class QuotedPrintableOutputStream extends FilterOutputStream {
    private static final byte CR = 13;
    private static final int DEFAULT_BUFFER_SIZE = 3072;
    private static final byte DOT = 46;
    private static final byte EQ = 61;
    private static final byte[] HEX_DIGITS = {48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 65, 66, 67, 68, 69, 70};
    private static final byte LF = 10;
    private static final byte QUOTED_PRINTABLE_LAST_PLAIN = 126;
    private static final int QUOTED_PRINTABLE_MAX_LINE_LENGTH = 76;
    private static final int QUOTED_PRINTABLE_OCTETS_PER_ESCAPE = 3;
    private static final byte SP = 32;
    private static final byte TB = 9;
    private final boolean binary;
    private boolean closed;
    private int nextSoftBreak;
    private final byte[] outBuffer;
    private int outputIndex;
    private boolean pendingCR;
    private boolean pendingSpace;
    private boolean pendingTab;
    private final byte[] singleByte;

    public QuotedPrintableOutputStream(int i, OutputStream outputStream, boolean z) {
        super(outputStream);
        this.closed = false;
        this.singleByte = new byte[1];
        this.outBuffer = new byte[i];
        this.binary = z;
        this.pendingSpace = false;
        this.pendingTab = false;
        this.pendingCR = false;
        this.outputIndex = 0;
        this.nextSoftBreak = 77;
    }

    public QuotedPrintableOutputStream(OutputStream outputStream, boolean z) {
        this(DEFAULT_BUFFER_SIZE, outputStream, z);
    }

    private void encodeChunk(byte[] bArr, int i, int i2) throws IOException {
        for (int i3 = i; i3 < i2 + i; i3++) {
            encode(bArr[i3]);
        }
    }

    private void completeEncoding() throws IOException {
        writePending();
        flushOutput();
    }

    private void writePending() throws IOException {
        if (this.pendingSpace) {
            plain(SP);
        } else if (this.pendingTab) {
            plain(TB);
        } else if (this.pendingCR) {
            plain(CR);
        }
        clearPending();
    }

    private void clearPending() throws IOException {
        this.pendingSpace = false;
        this.pendingTab = false;
        this.pendingCR = false;
    }

    private void encode(byte b) throws IOException {
        if (b == 10) {
            if (this.binary) {
                writePending();
                escape(b);
                return;
            } else {
                if (this.pendingCR) {
                    if (this.pendingSpace) {
                        escape(SP);
                    } else if (this.pendingTab) {
                        escape(TB);
                    }
                    lineBreak();
                    clearPending();
                    return;
                }
                writePending();
                plain(b);
                return;
            }
        }
        if (b == 13) {
            if (this.binary) {
                escape(b);
                return;
            } else {
                this.pendingCR = true;
                return;
            }
        }
        writePending();
        if (b == 32) {
            if (this.binary) {
                escape(b);
                return;
            } else {
                this.pendingSpace = true;
                return;
            }
        }
        if (b == 9) {
            if (this.binary) {
                escape(b);
                return;
            } else {
                this.pendingTab = true;
                return;
            }
        }
        if (b < 32) {
            escape(b);
            return;
        }
        if (b > 126) {
            escape(b);
        } else if (b == 61 || b == 46) {
            escape(b);
        } else {
            plain(b);
        }
    }

    private void plain(byte b) throws IOException {
        int i = this.nextSoftBreak - 1;
        this.nextSoftBreak = i;
        if (i <= 1) {
            softBreak();
        }
        write(b);
    }

    private void escape(byte b) throws IOException {
        int i = this.nextSoftBreak - 1;
        this.nextSoftBreak = i;
        if (i <= 3) {
            softBreak();
        }
        int i2 = b & 255;
        write(EQ);
        this.nextSoftBreak--;
        byte[] bArr = HEX_DIGITS;
        write(bArr[i2 >> 4]);
        this.nextSoftBreak--;
        write(bArr[i2 % 16]);
    }

    private void write(byte b) throws IOException {
        byte[] bArr = this.outBuffer;
        int i = this.outputIndex;
        int i2 = i + 1;
        this.outputIndex = i2;
        bArr[i] = b;
        if (i2 >= bArr.length) {
            flushOutput();
        }
    }

    private void softBreak() throws IOException {
        write(EQ);
        lineBreak();
    }

    private void lineBreak() throws IOException {
        write(CR);
        write(LF);
        this.nextSoftBreak = QUOTED_PRINTABLE_MAX_LINE_LENGTH;
    }

    void flushOutput() throws IOException {
        if (this.outputIndex < this.outBuffer.length) {
            this.out.write(this.outBuffer, 0, this.outputIndex);
        } else {
            this.out.write(this.outBuffer);
        }
        this.outputIndex = 0;
    }

    @Override // java.io.FilterOutputStream, java.io.OutputStream, java.io.Closeable, java.lang.AutoCloseable
    public void close() throws IOException {
        if (this.closed) {
            return;
        }
        try {
            completeEncoding();
        } finally {
            this.closed = true;
        }
    }

    @Override // java.io.FilterOutputStream, java.io.OutputStream, java.io.Flushable
    public void flush() throws IOException {
        flushOutput();
    }

    @Override // java.io.FilterOutputStream, java.io.OutputStream
    public void write(int i) throws IOException {
        byte[] bArr = this.singleByte;
        bArr[0] = (byte) i;
        write(bArr, 0, 1);
    }

    @Override // java.io.FilterOutputStream, java.io.OutputStream
    public void write(byte[] bArr, int i, int i2) throws IOException {
        if (this.closed) {
            throw new IOException("Stream has been closed");
        }
        encodeChunk(bArr, i, i2);
    }
}
