package com.example.hikerview.utils.contentdisposition;

import java.io.IOException;
import java.io.InputStream;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * QuotedPrintableInputStream.smali 等同名 smali 共 1305 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 * read0 一法 jadx 结构还原损坏（尾语句不可达），已按 smali 逐支手工转写（法内注）。
 */
public class QuotedPrintableInputStream extends InputStream {
    private static final byte CR = 13;
    private static final int DEFAULT_BUFFER_SIZE = 2048;
    private static final byte EQ = 61;
    private static final byte LF = 10;
    private final ByteArrayBuffer blanks;
    private boolean closed;
    private final ByteArrayBuffer decodedBuf;
    private final byte[] encoded;
    private final InputStream in;
    private boolean lastWasCR;
    private int limit;
    private final DecodeMonitor monitor;
    private int pos;
    private final byte[] singleByte;

    private int convert(int i) {
        if (i >= 48 && i <= 57) {
            return i - 48;
        }
        if (i >= 65 && i <= 70) {
            return i - 55;
        }
        if (i < 97 || i > 102) {
            return -1;
        }
        return i - 87;
    }

    public QuotedPrintableInputStream(InputStream inputStream, DecodeMonitor decodeMonitor) {
        this(DEFAULT_BUFFER_SIZE, inputStream, decodeMonitor);
    }

    protected QuotedPrintableInputStream(int i, InputStream inputStream, DecodeMonitor decodeMonitor) {
        this.singleByte = new byte[1];
        this.pos = 0;
        this.limit = 0;
        this.lastWasCR = false;
        this.in = inputStream;
        this.encoded = new byte[i];
        this.decodedBuf = new ByteArrayBuffer(512);
        this.blanks = new ByteArrayBuffer(512);
        this.closed = false;
        this.monitor = decodeMonitor;
    }

    protected QuotedPrintableInputStream(int i, InputStream inputStream, boolean z) {
        this(i, inputStream, z ? DecodeMonitor.STRICT : DecodeMonitor.SILENT);
    }

    public QuotedPrintableInputStream(InputStream inputStream, boolean z) {
        this(DEFAULT_BUFFER_SIZE, inputStream, z);
    }

    public QuotedPrintableInputStream(InputStream inputStream) {
        this(inputStream, false);
    }

    @Override // java.io.InputStream, java.io.Closeable, java.lang.AutoCloseable
    public void close() throws IOException {
        this.closed = true;
    }

    private int fillBuffer() throws IOException {
        int i = this.pos;
        int i2 = this.limit;
        if (i < i2) {
            byte[] bArr = this.encoded;
            System.arraycopy(bArr, i, bArr, 0, i2 - i);
            this.limit -= this.pos;
            this.pos = 0;
        } else {
            this.limit = 0;
            this.pos = 0;
        }
        byte[] bArr2 = this.encoded;
        int length = bArr2.length;
        int i3 = this.limit;
        int i4 = length - i3;
        if (i4 <= 0) {
            return 0;
        }
        int read = this.in.read(bArr2, i3, i4);
        if (read > 0) {
            this.limit += read;
        }
        return read;
    }

    private int getnext() {
        int i = this.pos;
        if (i >= this.limit) {
            return -1;
        }
        byte b = this.encoded[i];
        this.pos = i + 1;
        return b & 255;
    }

    private int peek(int i) {
        int i2 = this.pos;
        if (i2 + i < this.limit) {
            return this.encoded[i2 + i] & 255;
        }
        return -1;
    }

    private int transfer(int i, byte[] bArr, int i2, int i3, boolean z) throws IOException {
        if (z && this.blanks.length() > 0) {
            int min = Math.min(this.blanks.length(), i3 - i2);
            System.arraycopy(this.blanks.buffer(), 0, bArr, i2, min);
            i2 += min;
            int length = this.blanks.length() - min;
            if (length > 0) {
                this.decodedBuf.append(this.blanks.buffer(), min, length);
            }
            this.blanks.clear();
        } else if (this.blanks.length() > 0 && !z) {
            StringBuilder sb = new StringBuilder(this.blanks.length() * 3);
            for (int i4 = 0; i4 < this.blanks.length(); i4++) {
                sb.append(" ").append((int) this.blanks.byteAt(i4));
            }
            if (this.monitor.warn("ignored blanks", sb.toString())) {
                throw new IOException("ignored blanks");
            }
        }
        if (i == -1) {
            return i2;
        }
        if (i2 < i3) {
            int i5 = i2 + 1;
            bArr[i2] = (byte) i;
            return i5;
        }
        this.decodedBuf.append(i);
        return i2;
    }

    // 按官 smali 逐条手工转写（jadx 对本法结构还原损坏、尾语句不可达）：
    // 外层回填＋内层逐字节两层循环，位置量在内层一支、回外层时换手。
    private int read0(byte[] bArr, int i, int i2) throws IOException {
        int i3 = i + i2;
        int i4;
        if (this.decodedBuf.length() > 0) {
            int min = Math.min(this.decodedBuf.length(), i3 - i);
            System.arraycopy(this.decodedBuf.buffer(), 0, bArr, i, min);
            this.decodedBuf.remove(0, min);
            i4 = min + i;
        } else {
            i4 = i;
        }
        boolean z = false;
        while (i4 < i3) {
            if (this.limit - this.pos < 3) {
                z = fillBuffer() == -1;
            }
            boolean z2 = z;
            if (this.limit - this.pos == 0 && z2) {
                if (i4 == i) {
                    return -1;
                }
                return i4 - i;
            }
            int i5 = i4;
            while (this.pos < this.limit && i5 < i3) {
                byte[] bArr2 = this.encoded;
                int i6 = this.pos;
                this.pos = i6 + 1;
                byte b = bArr2[i6];
                int i7 = b & 255;
                boolean z3 = this.lastWasCR;
                if (z3 && i7 != 10) {
                    if (this.monitor.warn("Found CR without LF", "Leaving it as is")) {
                        throw new IOException("Found CR without LF");
                    }
                    i5 = transfer(13, bArr, i5, i3, false);
                } else if (!z3 && i7 == 10 && this.monitor.warn("Found LF without CR", "Translating to CRLF")) {
                    throw new IOException("Found LF without CR");
                }
                if (i7 == 13) {
                    this.lastWasCR = true;
                    continue;
                }
                this.lastWasCR = false;
                if (i7 == 10) {
                    if (this.blanks.length() == 0) {
                        i5 = transfer(10, bArr, transfer(13, bArr, i5, i3, false), i3, false);
                    } else if (this.blanks.byteAt(0) != 61) {
                        i5 = transfer(10, bArr, transfer(13, bArr, i5, i3, false), i3, false);
                    }
                    this.blanks.clear();
                } else if (i7 == 61) {
                    if (this.limit - this.pos < 2 && !z2) {
                        this.pos--;
                        break;
                    }
                    int i8 = getnext();
                    if (i8 == 61) {
                        i5 = transfer(i8, bArr, i5, i3, true);
                        int peek = peek(0);
                        int peek2 = peek(1);
                        if (peek == 10 || (peek == 13 && peek2 == 10)) {
                            this.monitor.warn("Unexpected ==EOL encountered", "== 0x" + peek + " 0x" + peek2);
                            this.blanks.append(i8);
                        } else {
                            this.monitor.warn("Unexpected == encountered", "==");
                        }
                    } else {
                        char c = (char) i8;
                        if (Character.isWhitespace(c)) {
                            int peek3 = peek(0);
                            if ((i8 != 13 || peek3 != 10) && this.monitor.warn("Found non-standard soft line break", "Translating to soft line break")) {
                                throw new IOException("Non-standard soft line break");
                            }
                            if (peek3 == 10) {
                                this.lastWasCR = i8 == 13;
                            }
                            i5 = transfer(-1, bArr, i5, i3, true);
                            if (i8 != 10) {
                                this.blanks.append(i7);
                                this.blanks.append(i8);
                            }
                        } else {
                            int i9 = getnext();
                            int convert = convert(i8);
                            int convert2 = convert(i9);
                            if (convert < 0 || convert2 < 0) {
                                this.monitor.warn("Malformed encoded value encountered", "leaving =" + c + ((char) i9) + " as is");
                                i5 = transfer(i9, bArr, transfer(i8, bArr, transfer(61, bArr, i5, i3, true), i3, false), i3, false);
                            } else {
                                i5 = transfer((convert << 4) | convert2, bArr, i5, i3, true);
                            }
                        }
                    }
                } else if (Character.isWhitespace(i7)) {
                    this.blanks.append(i7);
                } else {
                    i5 = transfer(b & 255, bArr, i5, i3, true);
                }
            }
            i4 = i5;
            z = z2;
        }
        return i3 - i;
    }

    @Override // java.io.InputStream
    public int read() throws IOException {
        int read;
        if (this.closed) {
            throw new IOException("Stream has been closed");
        }
        do {
            read = read(this.singleByte, 0, 1);
            if (read == -1) {
                return -1;
            }
        } while (read != 1);
        return this.singleByte[0] & 255;
    }

    @Override // java.io.InputStream
    public int read(byte[] bArr, int i, int i2) throws IOException {
        if (this.closed) {
            throw new IOException("Stream has been closed");
        }
        return read0(bArr, i, i2);
    }
}
