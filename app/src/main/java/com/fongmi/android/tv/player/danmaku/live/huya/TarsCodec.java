package com.fongmi.android.tv.player.danmaku.live.huya;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * TARS 编解码最小子集，照 dart_simple_live 的 tars_dart（tars_output_stream/tars_input_stream）对译，
 * 只覆盖虎牙弹幕用到的形态：整数、布尔、字符串、字节数组、结构体的按 tag 前向读与整字段跳过。
 * 头字节＝(tag<<4)|type，tag>=15 时第二字节才是 tag；类型号：0 BYTE/1 SHORT/2 INT/3 LONG/
 * 4 FLOAT/5 DOUBLE/6 STRING1/7 STRING4/8 MAP/9 LIST/10 STRUCT_BEGIN/11 STRUCT_END/12 ZERO_TAG/13 SIMPLE_LIST。
 * 写整数按值取最短宽（0 只写 ZERO_TAG 头）；读侧 skipToTag 只许向前扫，读不到回默认值，
 * 截断/坏类型抛 TarsException，由 provider 吞掉静默，绝不越界读。
 */
public final class TarsCodec {

    public static final int BYTE = 0;
    public static final int SHORT = 1;
    public static final int INT = 2;
    public static final int LONG = 3;
    public static final int FLOAT = 4;
    public static final int DOUBLE = 5;
    public static final int STRING1 = 6;
    public static final int STRING4 = 7;
    public static final int MAP = 8;
    public static final int LIST = 9;
    public static final int STRUCT_BEGIN = 10;
    public static final int STRUCT_END = 11;
    public static final int ZERO_TAG = 12;
    public static final int SIMPLE_LIST = 13;

    private TarsCodec() {
    }

    public static final class TarsException extends RuntimeException {

        TarsException(String message) {
            super(message);
        }
    }

    /** 写侧：与 TarsOutputStream 同形态。 */
    public static final class Writer {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        public Writer writeHead(int type, int tag) {
            if (tag < 15) {
                out.write((tag << 4) | type);
            } else if (tag < 256) {
                out.write((15 << 4) | type);
                out.write(tag);
            } else {
                throw new TarsException("tag too large: " + tag);
            }
            return this;
        }

        public Writer writeInt(long value, int tag) {
            if (value == 0) {
                writeHead(ZERO_TAG, tag);
            } else if (value >= -128 && value <= 127) {
                writeHead(BYTE, tag);
                out.write((byte) value);
            } else if (value >= -32768 && value <= 32767) {
                writeHead(SHORT, tag);
                writeBigEndian(value, 2);
            } else if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                writeHead(INT, tag);
                writeBigEndian(value, 4);
            } else {
                writeHead(LONG, tag);
                writeBigEndian(value, 8);
            }
            return this;
        }

        public Writer writeBool(boolean value, int tag) {
            return writeInt(value ? 1 : 0, tag);
        }

        public Writer writeString(String value, int tag) {
            byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 255) {
                writeHead(STRING4, tag);
                writeBigEndian(bytes.length, 4);
            } else {
                writeHead(STRING1, tag);
                out.write(bytes.length);
            }
            out.write(bytes, 0, bytes.length);
            return this;
        }

        public Writer writeBytes(byte[] value, int tag) {
            byte[] bytes = value == null ? new byte[0] : value;
            writeHead(SIMPLE_LIST, tag);
            writeHead(BYTE, 0);
            writeInt(bytes.length, 0);
            out.write(bytes, 0, bytes.length);
            return this;
        }

        public byte[] toByteArray() {
            return out.toByteArray();
        }

        private void writeBigEndian(long value, int bytes) {
            for (int i = bytes - 1; i >= 0; i--) out.write((byte) (value >>> (i * 8)));
        }
    }

    /** 读侧：与 TarsInputStream 同语义（skipToTag 只向前、结构体进/出成对）。 */
    public static final class Reader {

        public record Head(int type, int tag) {
        }

        private final byte[] data;
        private int pos;

        public Reader(byte[] data) {
            this.data = data == null ? new byte[0] : data;
        }

        public Head readHead() {
            int b = readU8();
            int type = b & 15;
            int tag = (b >> 4) & 15;
            if (tag == 15) tag = readU8();
            return new Head(type, tag);
        }

        private Head peekHead() {
            int saved = pos;
            try {
                return readHead();
            } finally {
                pos = saved;
            }
        }

        public boolean skipToTag(int tag) {
            try {
                while (true) {
                    if (pos >= data.length) return false;
                    Head head = peekHead();
                    if (tag <= head.tag() || head.type() == STRUCT_END) return tag == head.tag();
                    readHead();
                    skipField(head.type());
                }
            } catch (TarsException error) {
                return false;
            }
        }

        public void skipField(int type) {
            switch (type) {
                case BYTE -> skip(1);
                case SHORT -> skip(2);
                case INT, FLOAT -> skip(4);
                case LONG, DOUBLE -> skip(8);
                case STRING1 -> skip(readU8());
                case STRING4 -> skip(readInt32());
                case MAP -> {
                    long size = readInt(0);
                    for (long i = 0; i < size * 2; i++) skipField(readHead().type());
                }
                case LIST -> {
                    long size = readInt(0);
                    for (long i = 0; i < size; i++) skipField(readHead().type());
                }
                case SIMPLE_LIST -> {
                    Head elem = readHead();
                    if (elem.type() != BYTE) throw new TarsException("simple list elem type " + elem.type());
                    skip((int) readInt(0));
                }
                case STRUCT_BEGIN -> exitStruct();
                case STRUCT_END, ZERO_TAG -> {
                }
                default -> throw new TarsException("unknown type " + type);
            }
        }

        public long readInt(int tag) {
            if (!skipToTag(tag)) return 0;
            Head head = readHead();
            return switch (head.type()) {
                case ZERO_TAG -> 0;
                case BYTE -> readU8();
                case SHORT -> readInt16();
                case INT -> readInt32();
                case LONG -> readInt64();
                default -> throw new TarsException("type mismatch int: " + head.type());
            };
        }

        public String readString(int tag) {
            if (!skipToTag(tag)) return "";
            Head head = readHead();
            int length;
            if (head.type() == STRING1) {
                length = readU8();
            } else if (head.type() == STRING4) {
                length = readInt32();
                if (length < 0 || length > 100 * 1024 * 1024) throw new TarsException("string too long: " + length);
            } else {
                throw new TarsException("type mismatch string: " + head.type());
            }
            return new String(readRaw(length), StandardCharsets.UTF_8);
        }

        public byte[] readBytes(int tag) {
            if (!skipToTag(tag)) return new byte[0];
            Head head = readHead();
            if (head.type() == SIMPLE_LIST) {
                Head elem = readHead();
                if (elem.type() != BYTE) throw new TarsException("simple list elem type " + elem.type());
                long size = readInt(0);
                if (size < 0) throw new TarsException("invalid size " + size);
                return readRaw((int) size);
            }
            if (head.type() == LIST) {
                long size = readInt(0);
                if (size < 0 || size > 100 * 1024 * 1024) throw new TarsException("invalid size " + size);
                byte[] out = new byte[(int) size];
                for (int i = 0; i < size; i++) out[i] = (byte) readInt(0);
                return out;
            }
            throw new TarsException("type mismatch bytes: " + head.type());
        }

        public boolean enterStruct(int tag) {
            if (!skipToTag(tag)) return false;
            Head head = readHead();
            if (head.type() != STRUCT_BEGIN) throw new TarsException("type mismatch struct: " + head.type());
            return true;
        }

        /** 从结构体内部当前位置跳到并消费 STRUCT_END（与 skipToStructEnd 同语义）。 */
        public void exitStruct() {
            Head head;
            do {
                head = readHead();
                skipField(head.type());
            } while (head.type() != STRUCT_END);
        }

        private void skip(int length) {
            if (length < 0 || pos + length > data.length) throw new TarsException("skip overflow");
            pos += length;
        }

        private byte[] readRaw(int length) {
            if (length < 0 || pos + length > data.length) throw new TarsException("read overflow");
            byte[] out = new byte[length];
            System.arraycopy(data, pos, out, 0, length);
            pos += length;
            return out;
        }

        private int readU8() {
            if (pos >= data.length) throw new TarsException("read to end");
            return data[pos++] & 0xFF;
        }

        private int readInt16() {
            byte[] b = readRaw(2);
            return (short) ((b[0] & 0xFF) << 8 | (b[1] & 0xFF));
        }

        private int readInt32() {
            byte[] b = readRaw(4);
            return (b[0] & 0xFF) << 24 | (b[1] & 0xFF) << 16 | (b[2] & 0xFF) << 8 | (b[3] & 0xFF);
        }

        private long readInt64() {
            byte[] b = readRaw(8);
            long value = 0;
            for (int i = 0; i < 8; i++) value = value << 8 | (b[i] & 0xFF);
            return value;
        }
    }
}
