package com.example.hikerview.utils.contentdisposition;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * InputStreams.smali 等同名 smali 共 188 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
public final class InputStreams {
    private InputStreams() {
    }

    public static InputStream create(byte[] bArr, int i, int i2) {
        if (bArr == null) {
            throw new IllegalArgumentException("Byte array may not be null");
        }
        return new BinaryInputStream(ByteBuffer.wrap(bArr, i, i2));
    }

    public static InputStream create(byte[] bArr) {
        if (bArr == null) {
            throw new IllegalArgumentException("Byte array may not be null");
        }
        return new BinaryInputStream(ByteBuffer.wrap(bArr));
    }

    public static InputStream create(ByteArrayBuffer byteArrayBuffer) {
        if (byteArrayBuffer == null) {
            throw new IllegalArgumentException("Byte array may not be null");
        }
        return new BinaryInputStream(ByteBuffer.wrap(byteArrayBuffer.buffer(), 0, byteArrayBuffer.length()));
    }

    public static InputStream create(ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            throw new IllegalArgumentException("Byte array may not be null");
        }
        return new BinaryInputStream(byteBuffer);
    }

    public static InputStream createAscii(CharSequence charSequence) {
        if (charSequence == null) {
            throw new IllegalArgumentException("CharSequence may not be null");
        }
        return new TextInputStream(charSequence, Charsets.US_ASCII, 1024);
    }

    public static InputStream create(CharSequence charSequence, Charset charset) {
        if (charSequence == null) {
            throw new IllegalArgumentException("CharSequence may not be null");
        }
        if (charset == null) {
            charset = Charsets.DEFAULT_CHARSET;
        }
        return new TextInputStream(charSequence, charset, 1024);
    }
}
