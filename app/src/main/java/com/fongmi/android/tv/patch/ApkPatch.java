package com.fongmi.android.tv.patch;

/**
 * ApkDiffPatch 安卓端拼包核的 JNI 口（sisong/ApkDiffPatch，MIT；
 * 与桌面 ZipPatch 同一 C++ 核，phase 0 离线往返已验通）。
 * 上游原件（com.github.sisong.ApkPatch）只有 native 声明、没有 loadLibrary，
 * 本类按同签名落进自家包并补上加载——库名 apkpatch（libapkpatch.so，arm64-v8a）。
 * 返回 0 为成功，非零为拼包核错误码。
 */
public final class ApkPatch {

    static {
        System.loadLibrary("apkpatch");
    }

    private ApkPatch() {
    }

    public static native int patch(String oldApkPath, String patchFilePath, String outNewApkPath,
                                   long maxUncompressMemory, String tempUncompressFilePath, int threadNum);
}
