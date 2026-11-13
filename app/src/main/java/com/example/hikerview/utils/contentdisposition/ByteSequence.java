package com.example.hikerview.utils.contentdisposition;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * ByteSequence.smali 等同名 smali 共 33 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
public interface ByteSequence {
    public static final ByteSequence EMPTY = new EmptyByteSequence();

    byte byteAt(int i);

    int length();

    byte[] toByteArray();
}
