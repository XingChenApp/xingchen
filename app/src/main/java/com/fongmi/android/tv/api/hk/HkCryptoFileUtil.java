package com.fongmi.android.tv.api.hk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 8.83 {@code com.example.hikerview.utils.FileUtil} 在图片解密链里的同签名替代。
 *
 * <p>原版 assets/crypto-java.js 经 Rhino Java 互操作直调 {@code FileUtil.toBytes(InputStream)} /
 * {@code toInputStream(byte[])}（javax.crypto.Cipher 原生 AES）。星辰没有海阔包名的 FileUtil，
 * 故由宿主在图片求值作用域注入 {@code __cryptoFileUtil}（本类实例），crypto-java.js 内以
 * {@code const FileUtil = __cryptoFileUtil} 接线，语义与原版一致。</p>
 */
public class HkCryptoFileUtil {

    /** 读完流并返回全部字节；空流返回空数组（原版同款语义）。 */
    public byte[] toBytes(InputStream in) throws IOException {
        if (in == null) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    /** 字节数组 → 可重复读的输入流（原版同款语义）。 */
    public InputStream toInputStream(byte[] bytes) {
        return new ByteArrayInputStream(bytes == null ? new byte[0] : bytes);
    }
}
