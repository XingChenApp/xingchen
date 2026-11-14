package com.fongmi.android.tv.patch;

/**
 * 拼包核在自家包内的调用口：只做委托，不声明 native。
 * native 声明本体在 {@link com.github.sisong.ApkPatch}——libapkpatch.so
 * 的 JNI 符号按上游全限定名写死（Java_com_github_sisong_ApkPatch_patch），
 * 声明类必须用那个名字；本类曾经自带同签名 native 声明落自家包名，
 * 符号对不上、任何机器首次调用必断（1.4.133 真机补丁门 4 实锤），已摘。
 */
public final class ApkPatch {

    private ApkPatch() {
    }

    /** 触发拼包库加载；加载失败在此处以 Error 抛出，与链接/调用失败分开报。 */
    public static void ensureLoaded() {
        com.github.sisong.ApkPatch.ensureLoaded();
    }

    /** 返回 0 为成功，非零为拼包核错误码。 */
    public static int patch(String oldApkPath, String patchFilePath, String outNewApkPath,
                            long maxUncompressMemory, String tempUncompressFilePath, int threadNum) {
        return com.github.sisong.ApkPatch.patch(oldApkPath, patchFilePath, outNewApkPath,
                maxUncompressMemory, tempUncompressFilePath, threadNum);
    }
}
