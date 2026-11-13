package com.fongmi.android.tv.cloud.drive115;

import android.util.Base64;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * 115 接口加密（downurl 用）
 * 来源：115_tvbox_4_mx3q.py（m115_encode / m115_decode / _rsa_encrypt / _rsa_decrypt）
 * 参考 115 官方客户端协议：随机 16 字节 key + 异或 + 反转 + RSA(1024) 分组
 */
public class Crypto115 {

    private static final BigInteger RSA_N = new BigInteger(
            "8686980c0f5a24c4b9d43020cd2c22703ff3f450756529058b1cf88f09b86021"
                    + "36477198a6e2683149659bd122c33592fdb5ad47944ad1ea4d36c6b172aad633"
                    + "8c3bb6ac6227502d010993ac967d1aef00f0c8e038de2e4d3bc2ec368af2e9f1"
                    + "0a6f1eda4f7262f136420c07c331b871bf139f74f3010e3c4fe57df3afb71683", 16);
    private static final BigInteger RSA_E = BigInteger.valueOf(0x10001);
    private static final int RSA_KL = RSA_N.bitLength() / 8; // 128

    private static final byte[] XOR_SEED = new byte[]{
            (byte) 0xf0, (byte) 0xe5, 0x69, (byte) 0xae, (byte) 0xbf, (byte) 0xdc, (byte) 0xbf, (byte) 0x8a,
            0x1a, 0x45, (byte) 0xe8, (byte) 0xbe, 0x7d, (byte) 0xa6, 0x73, (byte) 0xb8,
            (byte) 0xde, (byte) 0x8f, (byte) 0xe7, (byte) 0xc4, 0x45, (byte) 0xda, (byte) 0x86, (byte) 0xc4,
            (byte) 0x9b, 0x64, (byte) 0x8b, 0x14, 0x6a, (byte) 0xb4, (byte) 0xf1, (byte) 0xaa,
            0x38, 0x01, 0x35, (byte) 0x9e, 0x26, 0x69, 0x2c, (byte) 0x86,
            0x00, 0x6b, 0x4f, (byte) 0xa5, 0x36, 0x34, 0x62, (byte) 0xa6,
            0x2a, (byte) 0x96, 0x68, 0x18, (byte) 0xf2, 0x4a, (byte) 0xfd, (byte) 0xbd,
            0x6b, (byte) 0x97, (byte) 0x8f, 0x4d, (byte) 0x8f, (byte) 0x89, 0x13, (byte) 0xb7,
            0x6c, (byte) 0x8e, (byte) 0x93, (byte) 0xed, 0x0e, 0x0d, 0x48, 0x3e,
            (byte) 0xd7, 0x2f, (byte) 0x88, (byte) 0xd8, (byte) 0xfe, (byte) 0xfe, 0x7e, (byte) 0x86,
            0x50, (byte) 0x95, 0x4f, (byte) 0xd1, (byte) 0xeb, (byte) 0x83, 0x26, 0x34,
            (byte) 0xdb, 0x66, 0x7b, (byte) 0x9c, 0x7e, (byte) 0x9d, 0x7a, (byte) 0x81,
            0x32, (byte) 0xea, (byte) 0xb6, 0x33, (byte) 0xde, 0x3a, (byte) 0xa9, 0x59,
            0x34, 0x66, 0x3b, (byte) 0xaa, (byte) 0xba, (byte) 0x81, 0x60, 0x48,
            (byte) 0xb9, (byte) 0xd5, (byte) 0x81, (byte) 0x9c, (byte) 0xf8, 0x6c, (byte) 0x84, 0x77,
            (byte) 0xff, 0x54, 0x78, 0x26, 0x5f, (byte) 0xbe, (byte) 0xe8, 0x1e,
            0x36, (byte) 0x9f, 0x34, (byte) 0x80, 0x5c, 0x45, 0x2c, (byte) 0x9b,
            0x76, (byte) 0xd5, 0x1b, (byte) 0x8f, (byte) 0xcc, (byte) 0xc3, (byte) 0xb8, (byte) 0xf5,
    };

    private static final byte[] XOR_CLIENT = new byte[]{
            0x78, 0x06, (byte) 0xad, 0x4c, 0x33, (byte) 0x86,
            0x5d, 0x18, 0x4c, 0x01, 0x3f, 0x46
    };

    private static byte[] xorDerive(byte[] seed, int size) {
        if (seed.length < size) return new byte[0];
        byte[] k = new byte[size];
        for (int i = 0; i < size; i++) {
            k[i] = (byte) ((seed[i] + XOR_SEED[size * i]) & 0xFF);
            k[i] ^= XOR_SEED[size * (size - i - 1)];
        }
        return k;
    }

    private static void xorTransform(byte[] buf, byte[] key) {
        int n = buf.length, kk = key.length;
        if (n == 0 || kk == 0) return;
        int mod = n % 4;
        for (int i = 0; i < mod; i++) buf[i] ^= key[i % kk];
        for (int i = mod; i < n; i++) buf[i] ^= key[(i - mod) % kk];
    }

    private static void reverse(byte[] buf) {
        for (int i = 0, j = buf.length - 1; i < j; i++, j--) {
            byte t = buf[i];
            buf[i] = buf[j];
            buf[j] = t;
        }
    }

    private static byte[] rsaEncrypt(byte[] data) {
        SecureRandom sr = new SecureRandom();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int step = RSA_KL - 11;
        for (int i = 0; i < data.length; i += step) {
            byte[] chunk = Arrays.copyOfRange(data, i, Math.min(i + step, data.length));
            int padlen = RSA_KL - chunk.length - 3;
            byte[] pad = new byte[padlen];
            for (int j = 0; j < padlen; j++) {
                pad[j] = (byte) (((sr.nextInt(256) % 0xFF) + 1) & 0xFF);
            }
            byte[] block = new byte[RSA_KL];
            block[0] = 0x00;
            block[1] = 0x02;
            System.arraycopy(pad, 0, block, 2, padlen);
            block[2 + padlen] = 0x00;
            System.arraycopy(chunk, 0, block, 3 + padlen, chunk.length);
            BigInteger m = new BigInteger(1, block);
            BigInteger c = m.modPow(RSA_E, RSA_N);
            byte[] cb = c.toByteArray();
            byte[] fixed = new byte[RSA_KL];
            if (cb.length > RSA_KL) {
                System.arraycopy(cb, cb.length - RSA_KL, fixed, 0, RSA_KL);
            } else if (cb.length < RSA_KL) {
                System.arraycopy(cb, 0, fixed, RSA_KL - cb.length, cb.length);
            } else {
                fixed = cb;
            }
            out.write(fixed, 0, RSA_KL);
        }
        return out.toByteArray();
    }

    private static byte[] rsaDecrypt(byte[] data) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < data.length; i += RSA_KL) {
            byte[] chunk = Arrays.copyOfRange(data, i, Math.min(i + RSA_KL, data.length));
            BigInteger c = new BigInteger(1, chunk);
            BigInteger m = c.modPow(RSA_E, RSA_N);
            byte[] b = m.toByteArray();
            // 去掉 PKCS#1 padding：找 0x00 分隔
            for (int j = 1; j < b.length; j++) {
                if (b[j] == 0) {
                    out.write(b, j + 1, b.length - j - 1);
                    break;
                }
            }
        }
        return out.toByteArray();
    }

    /** 加密请求体（downurl 用） */
    public static String m115Encode(byte[] plain, byte[] key16) {
        byte[] buf = new byte[16 + plain.length];
        System.arraycopy(key16, 0, buf, 0, 16);
        System.arraycopy(plain, 0, buf, 16, plain.length);
        byte[] body = Arrays.copyOfRange(buf, 16, buf.length);
        xorTransform(body, xorDerive(key16, 4));
        reverse(body);
        xorTransform(body, XOR_CLIENT);
        System.arraycopy(body, 0, buf, 16, body.length);
        return Base64.encodeToString(rsaEncrypt(buf), Base64.NO_WRAP);
    }

    /** 解服务端返回 */
    public static byte[] m115Decode(String cipherB64, byte[] key16) {
        byte[] data = rsaDecrypt(Base64.decode(cipherB64, Base64.DEFAULT));
        return m115DecodeBody(data, key16);
    }

    private static byte[] m115DecodeBody(byte[] data, byte[] key16) {
        if (data.length <= 16) return new byte[0];
        byte[] out = Arrays.copyOfRange(data, 16, data.length);
        xorTransform(out, xorDerive(Arrays.copyOfRange(data, 0, 16), 12));
        reverse(out);
        xorTransform(out, xorDerive(key16, 4));
        return out;
    }
}
