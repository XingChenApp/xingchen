package com.github.sisong;

/**
 * ApkDiffPatch 安卓端拼包核的 JNI 口（sisong/ApkDiffPatch，MIT；
 * 与桌面 ZipPatch 同一 C++ 核，phase 0 离线往返已验通）。
 * 本类必须留在上游全限定名下：libapkpatch.so 的唯一 JNI 入口是
 * Java_com_github_sisong_ApkPatch_patch、没有 JNI_OnLoad/RegisterNatives，
 * JNI 按 native 方法声明类的全限定名找符号——声明类改名后任何机器上
 * 首次调用必抛 UnsatisfiedLinkError（1.4.133 真机补丁门 4 实锤：
 * 当时声明落在 com.fongmi.android.tv.patch.ApkPatch，符号对不上）。
 * proguard-rules.pro 有整类保名行防 R8 改名，成品审计逐版验名。
 * 上游原件没有 loadLibrary，这里补上，加载点收在本类静态块。
 * 返回 0 为成功，非零为拼包核错误码。
 */
public final class ApkPatch {

    static {
        System.loadLibrary("apkpatch");
    }

    private ApkPatch() {
    }

    /** 触发本类初始化（即加载拼包库），供调用方把「加载失败」与「链接/调用失败」分开报。 */
    public static void ensureLoaded() {
    }

    public static native int patch(String oldApkPath, String patchFilePath, String outNewApkPath,
                                   long maxUncompressMemory, String tempUncompressFilePath, int threadNum);
}
