package com.example.hikerview.utils.contentdisposition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * TextInputStream.smali 等同名 smali 共 454 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
class TextInputStream extends InputStream {
    private final ByteBuffer bbuf;
    private final CharBuffer cbuf;
    private final CharsetEncoder encoder;
    private int mark = -1;

    @Override // java.io.InputStream, java.io.Closeable, java.lang.AutoCloseable
    public void close() throws IOException {
    }

    @Override // java.io.InputStream
    public boolean markSupported() {
        return true;
    }

    TextInputStream(CharSequence charSequence, Charset charset, int i) {
        this.encoder = charset.newEncoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
        ByteBuffer allocate = ByteBuffer.allocate(i);
        this.bbuf = allocate;
        allocate.flip();
        this.cbuf = CharBuffer.wrap(charSequence);
    }

    private void fillBuffer() throws CharacterCodingException {
        this.bbuf.compact();
        CoderResult encode = this.encoder.encode(this.cbuf, this.bbuf, true);
        if (encode.isError()) {
            encode.throwException();
        }
        this.bbuf.flip();
    }

    @Override // java.io.InputStream
    public int read(byte[] bArr, int i, int i2) throws IOException {
        bArr.getClass();
        if (i < 0 || i2 < 0 || i + i2 > bArr.length) {
            throw new IndexOutOfBoundsException();
        }
        if (!this.bbuf.hasRemaining() && !this.cbuf.hasRemaining()) {
            return -1;
        }
        int i3 = 0;
        while (i2 > 0) {
            if (this.bbuf.hasRemaining()) {
                int min = Math.min(this.bbuf.remaining(), i2);
                this.bbuf.get(bArr, i, min);
                i += min;
                i2 -= min;
                i3 += min;
            } else {
                fillBuffer();
                if (!this.bbuf.hasRemaining() && !this.cbuf.hasRemaining()) {
                    break;
                }
            }
        }
        if (i3 > 0 || this.bbuf.hasRemaining() || this.cbuf.hasRemaining()) {
            return i3;
        }
        return -1;
    }

    @Override // java.io.InputStream
    public int read() throws IOException {
        while (!this.bbuf.hasRemaining()) {
            fillBuffer();
            if (!this.bbuf.hasRemaining() && !this.cbuf.hasRemaining()) {
                return -1;
            }
        }
        return this.bbuf.get() & 255;
    }

    @Override // java.io.InputStream
    public int read(byte[] bArr) throws IOException {
        return read(bArr, 0, bArr.length);
    }

    @Override // java.io.InputStream
    public long skip(long j) throws IOException {
        int i = 0;
        while (j > 0 && this.cbuf.hasRemaining()) {
            this.cbuf.get();
            j--;
            i++;
        }
        return i;
    }

    @Override // java.io.InputStream
    public int available() throws IOException {
        return this.cbuf.remaining();
    }

    @Override // java.io.InputStream
    public void mark(int i) {
        this.mark = this.cbuf.position();
    }

    @Override // java.io.InputStream
    public void reset() throws IOException {
        int i = this.mark;
        if (i != -1) {
            this.cbuf.position(i);
            this.mark = -1;
        }
    }
}
