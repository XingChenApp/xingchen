package com.example.hikerview.utils.contentdisposition;

import java.io.IOException;
import java.io.InputStream;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * Base64InputStream.smali 等同名 smali 共 946 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 * read0 一法 jadx 反编译失败，已按 smali（Base64InputStream.smali read0 段）逐寄存器手工转写；$assertionsDisabled 为未被引用的合成字段、官码各处实为字面 false，落码用 false、不落该合成字段。
 */
public class Base64InputStream extends InputStream {
    private static final int[] BASE64_DECODE = new int[256];
    private static final byte BASE64_PAD = 61;
    private static final int ENCODED_BUFFER_SIZE = 1536;
    private static final int EOF = -1;
    private boolean closed;
    private final ByteArrayBuffer decodedBuf;
    private final byte[] encoded;
    private boolean eof;
    private final InputStream in;
    private final DecodeMonitor monitor;
    private int position;
    private final byte[] singleByte;
    private int size;

    static {
        for (int i = 0; i < 256; i++) {
            BASE64_DECODE[i] = -1;
        }
        for (int i2 = 0; i2 < Base64OutputStream.BASE64_TABLE.length; i2++) {
            BASE64_DECODE[Base64OutputStream.BASE64_TABLE[i2] & 255] = i2;
        }
    }

    public Base64InputStream(InputStream inputStream, DecodeMonitor decodeMonitor) {
        this(ENCODED_BUFFER_SIZE, inputStream, decodeMonitor);
    }

    protected Base64InputStream(int i, InputStream inputStream, DecodeMonitor decodeMonitor) {
        this.singleByte = new byte[1];
        this.position = 0;
        this.size = 0;
        this.closed = false;
        if (inputStream == null) {
            throw new IllegalArgumentException();
        }
        this.encoded = new byte[i];
        this.decodedBuf = new ByteArrayBuffer(512);
        this.in = inputStream;
        this.monitor = decodeMonitor;
    }

    public Base64InputStream(InputStream inputStream) {
        this(inputStream, false);
    }

    public Base64InputStream(InputStream inputStream, boolean z) {
        this(ENCODED_BUFFER_SIZE, inputStream, z ? DecodeMonitor.STRICT : DecodeMonitor.SILENT);
    }

    @Override // java.io.InputStream
    public int read() throws IOException {
        int read0;
        if (this.closed) {
            throw new IOException("Stream has been closed");
        }
        do {
            read0 = read0(this.singleByte, 0, 1);
            if (read0 == -1) {
                return -1;
            }
        } while (read0 != 1);
        return this.singleByte[0] & 255;
    }

    @Override // java.io.InputStream
    public int read(byte[] bArr) throws IOException {
        if (this.closed) {
            throw new IOException("Stream has been closed");
        }
        bArr.getClass();
        if (bArr.length == 0) {
            return 0;
        }
        return read0(bArr, 0, bArr.length);
    }

    @Override // java.io.InputStream
    public int read(byte[] bArr, int i, int i2) throws IOException {
        if (this.closed) {
            throw new IOException("Stream has been closed");
        }
        bArr.getClass();
        if (i < 0 || i2 < 0 || i + i2 > bArr.length) {
            throw new IndexOutOfBoundsException();
        }
        if (i2 == 0) {
            return 0;
        }
        return read0(bArr, i, i2);
    }

    @Override // java.io.InputStream, java.io.Closeable, java.lang.AutoCloseable
    public void close() throws IOException {
        if (this.closed) {
            return;
        }
        this.closed = true;
    }

    // 按官 smali 逐条手工转写（jadx 对本法反编译失败）：data/sextets 两量在
    // 内外两层循环之间换手保存、data 每凑满四 sextet 出三字节后照官不复位。
    private int read0(byte[] bArr, int i, int i2) throws IOException {
        int i3 = i + i2;
        int i4 = i;
        if (this.decodedBuf.length() > 0) {
            int min = Math.min(this.decodedBuf.length(), i2);
            System.arraycopy(this.decodedBuf.buffer(), 0, bArr, i, min);
            this.decodedBuf.remove(0, min);
            i4 = min + i;
        }
        if (this.eof) {
            if (i4 == i) {
                return -1;
            }
            return i4 - i;
        }
        int i5 = 0;
        int i6 = 0;
        while (i4 < i3) {
            while (this.position == this.size) {
                int read = this.in.read(this.encoded, 0, this.encoded.length);
                if (read == -1) {
                    this.eof = true;
                    if (i5 != 0) {
                        handleUnexpectedEof(i5);
                    }
                    if (i4 == i) {
                        return -1;
                    }
                    return i4 - i;
                }
                if (read > 0) {
                    this.position = 0;
                    this.size = read;
                }
            }
            int i7 = i6;
            int i8 = i5;
            while (this.position < this.size && i4 < i3) {
                byte[] bArr2 = this.encoded;
                int i9 = this.position;
                this.position = i9 + 1;
                int i10 = bArr2[i9] & 255;
                if (i10 == 61) {
                    return decodePad(i7, i8, bArr, i4, i3) - i;
                }
                int i11 = BASE64_DECODE[i10];
                if (i11 < 0) {
                    if (i10 == 13 || i10 == 10 || i10 == 32) {
                        continue;
                    }
                    if (this.monitor.warn("Unexpected base64 byte: " + ((byte) i10), "ignoring.")) {
                        throw new IOException("Unexpected base64 byte");
                    }
                } else {
                    i7 = (i7 << 6) | i11;
                    i8++;
                    if (i8 == 4) {
                        byte b = (byte) (i7 >>> 16);
                        byte b2 = (byte) (i7 >>> 8);
                        byte b3 = (byte) i7;
                        if (i4 < i3 - 2) {
                            bArr[i4] = b;
                            bArr[i4 + 1] = b2;
                            bArr[i4 + 2] = b3;
                            i4 += 3;
                            i8 = 0;
                        } else if (i4 < i3 - 1) {
                            bArr[i4] = b;
                            bArr[i4 + 1] = b2;
                            this.decodedBuf.append(b3);
                            return i3 - i;
                        } else if (i4 < i3) {
                            bArr[i4] = b;
                            this.decodedBuf.append(b2);
                            this.decodedBuf.append(b3);
                            return i3 - i;
                        } else {
                            this.decodedBuf.append(b);
                            this.decodedBuf.append(b2);
                            this.decodedBuf.append(b3);
                            return i3 - i;
                        }
                    }
                }
            }
            i5 = i8;
            i6 = i7;
        }
        return i3 - i;
    }

    private int decodePad(int i, int i2, byte[] bArr, int i3, int i4) throws IOException {
        this.eof = true;
        if (i2 == 2) {
            byte b = (byte) (i >>> 4);
            if (i3 < i4) {
                int i5 = i3 + 1;
                bArr[i3] = b;
                return i5;
            }
            this.decodedBuf.append(b);
            return i3;
        }
        if (i2 != 3) {
            handleUnexpecedPad(i2);
            return i3;
        }
        byte b2 = (byte) (i >>> 10);
        byte b3 = (byte) ((i >>> 2) & 255);
        if (i3 < i4 - 1) {
            int i6 = i3 + 1;
            bArr[i3] = b2;
            int i7 = i3 + 2;
            bArr[i6] = b3;
            return i7;
        }
        if (i3 < i4) {
            int i8 = i3 + 1;
            bArr[i3] = b2;
            this.decodedBuf.append(b3);
            return i8;
        }
        this.decodedBuf.append(b2);
        this.decodedBuf.append(b3);
        return i3;
    }

    private void handleUnexpectedEof(int i) throws IOException {
        if (this.monitor.warn("Unexpected end of BASE64 stream", "dropping " + i + " sextet(s)")) {
            throw new IOException("Unexpected end of BASE64 stream");
        }
    }

    private void handleUnexpecedPad(int i) throws IOException {
        if (this.monitor.warn("Unexpected padding character", "dropping " + i + " sextet(s)")) {
            throw new IOException("Unexpected padding character");
        }
    }
}
