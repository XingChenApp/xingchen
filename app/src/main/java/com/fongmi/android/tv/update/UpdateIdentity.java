package com.fongmi.android.tv.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 更新包本地档的身份旁车：update.apk 同目录放一份 update.apk.id，内容是
 * 本次更新目标的身份串（清单 sha256 优先，缺失时退 name|code|size）。
 * 进度回填与两条传输路的续传判定只认「档在＋旁车在＋身份全等」；身份
 * 对不上或没有旁车的档一律不认——只凭文件长度认档会把旧版整包的长度
 * 当成本次进度（一开弹窗就 99%），甚至把新包尾巴拼到旧包上。
 */
public final class UpdateIdentity {

    private UpdateIdentity() {
    }

    public static File sidecar(File apk) {
        return new File(apk.getParentFile(), apk.getName() + ".id");
    }

    public static String of(String sha256, String name, int code, long size) {
        if (sha256 != null && !sha256.isEmpty()) return "sha256:" + sha256.toLowerCase(Locale.ROOT);
        return "meta:" + (name == null ? "" : name) + "|" + code + "|" + size;
    }

    public static boolean matches(File apk, String identity) {
        if (apk == null || identity == null || identity.isEmpty()) return false;
        File sidecar = sidecar(apk);
        if (!sidecar.isFile()) return false;
        try (FileInputStream input = new FileInputStream(sidecar)) {
            byte[] buffer = new byte[512];
            int read = input.read(buffer);
            if (read <= 0) return false;
            return identity.equals(new String(buffer, 0, read, StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            return false;
        }
    }

    /** 落旁车；写失败返回 false（调用方留痕），不抛异常打断下载流程。 */
    public static boolean write(File apk, String identity) {
        if (apk == null || identity == null || identity.isEmpty()) return false;
        File sidecar = sidecar(apk);
        File parent = sidecar.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
        try (FileOutputStream output = new FileOutputStream(sidecar)) {
            output.write(identity.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 删档＋旁车；任一删失败返回 false 交调用方留痕，不静默吞。 */
    public static boolean clear(File apk) {
        if (apk == null) return true;
        boolean apkGone = !apk.exists() || apk.delete();
        return clearSidecar(apk) && apkGone;
    }

    public static boolean clearSidecar(File apk) {
        if (apk == null) return true;
        File sidecar = sidecar(apk);
        return !sidecar.exists() || sidecar.delete();
    }
}
