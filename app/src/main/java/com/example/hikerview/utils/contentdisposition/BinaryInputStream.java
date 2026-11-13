package com.example.hikerview.utils.contentdisposition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * BinaryInputStream.smali 等同名 smali 共 221 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
class BinaryInputStream extends InputStream {
    private final ByteBuffer bbuf;

    @Override // java.io.InputStream, java.io.Closeable, java.lang.AutoCloseable
    public void close() throws IOException {
    }

    BinaryInputStream(ByteBuffer byteBuffer) {
        this.bbuf = byteBuffer;
    }

    @Override // java.io.InputStream
    public int read(byte[] bArr, int i, int i2) throws IOException {
        bArr.getClass();
        if (i < 0 || i2 < 0 || i + i2 > bArr.length) {
            throw new IndexOutOfBoundsException();
        }
        if (i2 == 0) {
            return 0;
        }
        if (!this.bbuf.hasRemaining()) {
            return -1;
        }
        int min = Math.min(this.bbuf.remaining(), i2);
        this.bbuf.get(bArr, i, min);
        return min;
    }

    @Override // java.io.InputStream
    public int read() throws IOException {
        if (this.bbuf.hasRemaining()) {
            return this.bbuf.get() & 255;
        }
        return -1;
    }

    @Override // java.io.InputStream
    public int read(byte[] bArr) throws IOException {
        return read(bArr, 0, bArr.length);
    }

    @Override // java.io.InputStream
    public long skip(long j) throws IOException {
        int i = 0;
        while (j > 0 && this.bbuf.hasRemaining()) {
            this.bbuf.get();
            j--;
            i++;
        }
        return i;
    }

    @Override // java.io.InputStream
    public int available() throws IOException {
        return this.bbuf.remaining();
    }
}
