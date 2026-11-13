package com.example.hikerview.utils.contentdisposition;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * ByteArrayBuffer.smali 等同名 smali 共 523 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
public final class ByteArrayBuffer implements ByteSequence {
    private byte[] buffer;
    private int len;

    public ByteArrayBuffer(int i) {
        if (i < 0) {
            throw new IllegalArgumentException("Buffer capacity may not be negative");
        }
        this.buffer = new byte[i];
    }

    public ByteArrayBuffer(byte[] bArr, boolean z) {
        this(bArr, bArr.length, z);
    }

    public ByteArrayBuffer(byte[] bArr, int i, boolean z) {
        if (bArr == null) {
            throw new IllegalArgumentException();
        }
        if (i < 0 || i > bArr.length) {
            throw new IllegalArgumentException();
        }
        if (z) {
            this.buffer = bArr;
        } else {
            byte[] bArr2 = new byte[i];
            this.buffer = bArr2;
            System.arraycopy(bArr, 0, bArr2, 0, i);
        }
        this.len = i;
    }

    private void expand(int i) {
        byte[] bArr = new byte[Math.max(this.buffer.length << 1, i)];
        System.arraycopy(this.buffer, 0, bArr, 0, this.len);
        this.buffer = bArr;
    }

    public void append(byte[] bArr, int i, int i2) {
        int i3;
        if (bArr == null) {
            return;
        }
        if (i < 0 || i > bArr.length || i2 < 0 || (i3 = i + i2) < 0 || i3 > bArr.length) {
            throw new IndexOutOfBoundsException();
        }
        if (i2 == 0) {
            return;
        }
        int i4 = this.len + i2;
        if (i4 > this.buffer.length) {
            expand(i4);
        }
        System.arraycopy(bArr, i, this.buffer, this.len, i2);
        this.len = i4;
    }

    public void append(int i) {
        int i2 = this.len + 1;
        if (i2 > this.buffer.length) {
            expand(i2);
        }
        this.buffer[this.len] = (byte) i;
        this.len = i2;
    }

    public void clear() {
        this.len = 0;
    }

    @Override // com.example.hikerview.utils.contentdisposition.ByteSequence
    public byte[] toByteArray() {
        int i = this.len;
        byte[] bArr = new byte[i];
        if (i > 0) {
            System.arraycopy(this.buffer, 0, bArr, 0, i);
        }
        return bArr;
    }

    @Override // com.example.hikerview.utils.contentdisposition.ByteSequence
    public byte byteAt(int i) {
        if (i < 0 || i >= this.len) {
            throw new IndexOutOfBoundsException();
        }
        return this.buffer[i];
    }

    public int capacity() {
        return this.buffer.length;
    }

    @Override // com.example.hikerview.utils.contentdisposition.ByteSequence
    public int length() {
        return this.len;
    }

    public byte[] buffer() {
        return this.buffer;
    }

    public int indexOf(byte b) {
        return indexOf(b, 0, this.len);
    }

    public int indexOf(byte b, int i, int i2) {
        if (i < 0) {
            i = 0;
        }
        int i3 = this.len;
        if (i2 > i3) {
            i2 = i3;
        }
        if (i > i2) {
            return -1;
        }
        while (i < i2) {
            if (this.buffer[i] == b) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public void setLength(int i) {
        if (i < 0 || i > this.buffer.length) {
            throw new IndexOutOfBoundsException();
        }
        this.len = i;
    }

    public void remove(int i, int i2) {
        int i3;
        int i4;
        if (i < 0 || i > (i3 = this.len) || i2 < 0 || (i4 = i + i2) < 0 || i4 > i3) {
            throw new IndexOutOfBoundsException();
        }
        if (i2 == 0) {
            return;
        }
        int i5 = (i3 - i) - i2;
        if (i5 > 0) {
            byte[] bArr = this.buffer;
            System.arraycopy(bArr, i4, bArr, i, i5);
        }
        this.len -= i2;
    }

    public boolean isEmpty() {
        return this.len == 0;
    }

    public boolean isFull() {
        return this.len == this.buffer.length;
    }

    public String toString() {
        return new String(toByteArray());
    }
}
