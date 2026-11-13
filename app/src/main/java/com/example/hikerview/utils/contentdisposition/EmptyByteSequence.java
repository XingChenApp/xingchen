package com.example.hikerview.utils.contentdisposition;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * EmptyByteSequence.smali 等同名 smali 共 64 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
final class EmptyByteSequence implements ByteSequence {
    private static final byte[] EMPTY_BYTES = new byte[0];

    @Override // com.example.hikerview.utils.contentdisposition.ByteSequence
    public int length() {
        return 0;
    }

    EmptyByteSequence() {
    }

    @Override // com.example.hikerview.utils.contentdisposition.ByteSequence
    public byte byteAt(int i) {
        throw new IndexOutOfBoundsException();
    }

    @Override // com.example.hikerview.utils.contentdisposition.ByteSequence
    public byte[] toByteArray() {
        return EMPTY_BYTES;
    }
}
