package com.example.hikerview.utils.contentdisposition;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * Base64OutputStream.smali 等同名 smali 共 444 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 * 含内部类 ExtraCrlfOutputStream（官 smali 独立文件 Base64OutputStream$ExtraCrlfOutputStream.smali）；主体构造器照官用 java.util.Base64.getMimeEncoder——官 smali 原文如此，非自创替换。Base64OutputStream-IA.smali 为 3 行空合成标记、无类体可落。
 */
public class Base64OutputStream extends OutputStream {
    private static final int DEFAULT_LINE_LENGTH = 76;
    private final OutputStream delegate;
    private static final byte[] CRLF_SEPARATOR = {13, 10};
    static final byte[] BASE64_TABLE = {65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 43, 47};

    public Base64OutputStream(OutputStream outputStream) {
        this(outputStream, DEFAULT_LINE_LENGTH, CRLF_SEPARATOR);
    }

    public Base64OutputStream(OutputStream outputStream, int i) {
        this(outputStream, i, CRLF_SEPARATOR);
    }

    public Base64OutputStream(OutputStream outputStream, int i, byte[] bArr) {
        this.delegate = Base64.getMimeEncoder(i, bArr).wrap(new ExtraCrlfOutputStream(outputStream, bArr));
    }

    @Override // java.io.OutputStream
    public void write(int i) throws IOException {
        this.delegate.write(i);
    }

    @Override // java.io.OutputStream
    public void write(byte[] bArr) throws IOException {
        this.delegate.write(bArr);
    }

    @Override // java.io.OutputStream
    public void write(byte[] bArr, int i, int i2) throws IOException {
        this.delegate.write(bArr, i, i2);
    }

    @Override // java.io.OutputStream, java.io.Flushable
    public void flush() throws IOException {
        this.delegate.flush();
    }

    @Override // java.io.OutputStream, java.io.Closeable, java.lang.AutoCloseable
    public void close() throws IOException {
        this.delegate.close();
    }

    private static class ExtraCrlfOutputStream extends OutputStream {
        private boolean appendExtraCrlf;
        private final OutputStream delegate;
        private final byte[] lineSeparator;

        private ExtraCrlfOutputStream(OutputStream outputStream, byte[] bArr) {
            this.delegate = outputStream;
            this.lineSeparator = bArr;
            this.appendExtraCrlf = false;
        }

        @Override // java.io.OutputStream
        public void write(int i) throws IOException {
            this.delegate.write(i);
            this.appendExtraCrlf = true;
        }

        @Override // java.io.OutputStream
        public void write(byte[] bArr) throws IOException {
            this.delegate.write(bArr);
            this.appendExtraCrlf = (bArr.length > 0) | this.appendExtraCrlf;
        }

        @Override // java.io.OutputStream
        public void write(byte[] bArr, int i, int i2) throws IOException {
            this.delegate.write(bArr, i, i2);
            this.appendExtraCrlf |= i2 > 0;
        }

        @Override // java.io.OutputStream, java.io.Flushable
        public void flush() throws IOException {
            this.delegate.flush();
        }

        @Override // java.io.OutputStream, java.io.Closeable, java.lang.AutoCloseable
        public void close() throws IOException {
            if (this.appendExtraCrlf) {
                this.delegate.write(this.lineSeparator);
            }
        }
    }
}
