package com.fongmi.android.tv.utils;

import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

// 运行时包描述：RuntimeInstaller.createAsset / getUpdatePackage / verifyDownload 用。
// 上游取件里没有这个文件（见交回卡点），这里按 RuntimeInstaller 的调用点合同实现：
// 构造 (version, url, size, digest)，取值 version()/url()/size()/digest()，
// 校验 verifyFile(file)。构造时只做非空与 size > 0 校验，不把 digest 为空
// 当成构造错——getUpdatePackage 里 digest 为空要自己转 IOException（“checksum is missing”），
// 构造时先拦会改掉那条报错口径。
public record UpdatePackage(String version, String url, long size, String digest) {

    public UpdatePackage {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("Invalid runtime version");
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Invalid runtime url");
        if (size <= 0) throw new IllegalArgumentException("Invalid runtime size");
        if (digest == null) throw new IllegalArgumentException("Invalid runtime digest");
    }

    // 下好的 APK 必须同时对上大小与 sha256 摘要，否则当成坏包删掉重下。
    // digest 形如 "sha256:<64 位十六进制>"，不带前缀时整串当十六进制用。
    public void verifyFile(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Runtime APK is missing");
        if (file.length() != size) throw new IOException("Runtime APK size mismatch");
        if (digest.isEmpty()) throw new IOException("Runtime APK checksum is missing");
        String expected = digest.startsWith("sha256:") ? digest.substring("sha256:".length()) : digest;
        if (!expected.matches("[0-9a-f]{64}")) throw new IOException("Runtime APK checksum is invalid");
        if (!expected.equals(sha256(file))) throw new IOException("Runtime APK checksum mismatch");
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) md.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }
}
