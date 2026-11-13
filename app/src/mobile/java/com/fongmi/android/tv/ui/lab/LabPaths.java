package com.fongmi.android.tv.ui.lab;

import android.content.Context;

import java.io.File;

/**
 * 实验室目录布局（应用私有目录，照参照物）：
 * files/lab/
 *   packages/<id>/      已装 .lab 包（files/ 内容解开于此，lab.json 在根）
 *   runtimes/<runtime>/ 运行环境（python/nodejs/php），bin/ 在其下
 *   pids/               进程跟踪
 *   logs/               每包一份日志
 *   registry.json       安装注册表
 */
public final class LabPaths {

    private LabPaths() {
    }

    public static File root(Context c) {
        return new File(c.getFilesDir(), "lab");
    }

    public static File packages(Context c) {
        return new File(root(c), "packages");
    }

    public static File packageDir(Context c, String id) {
        File dir = new File(packages(c), requireSafeId(id));
        ensureInside(packages(c), dir);
        return dir;
    }

    public static File runtimes(Context c) {
        return new File(root(c), "runtimes");
    }

    public static File runtimeDir(Context c, String runtime) {
        File dir = new File(runtimes(c), requireSafeId(runtime));
        ensureInside(runtimes(c), dir);
        return dir;
    }

    public static File pids(Context c) {
        return new File(root(c), "pids");
    }

    public static File logs(Context c) {
        return new File(root(c), "logs");
    }

    public static File registry(Context c) {
        return new File(root(c), "registry.json");
    }

    public static File manifestOf(Context c, String id) {
        return new File(packageDir(c, id), "lab.json");
    }

    /**
     * 包/环境 id 合法性（全仓唯一口径）：id 直接决定目录，纯靠 safe() 换字符挡不住
     * 纯「.」/「..」——那种 id 会让 packageDir 落到实验室根，安装/卸载的删目录
     * 就能把环境、注册表、全部包一锅端。进门就拒：空、纯点、带路径分隔、
     * 首尾点、含「..」一律抛（调用链都有人话兜底），绝不把删除范围交给 id 作者。
     */
    public static String requireSafeId(String id) {
        if (id == null) throw new IllegalArgumentException("id 是空的");
        String s = id.trim();
        if (s.isEmpty() || ".".equals(s) || "..".equals(s)
                || s.indexOf('/') >= 0 || s.indexOf('\\') >= 0 || s.indexOf('\0') >= 0
                || s.startsWith(".") || s.endsWith(".") || s.contains("..")) {
            throw new IllegalArgumentException("id 不合法：" + id);
        }
        return safe(s);
    }

    /** 双保险：拼出的目录 canonical 后必须仍在父目录内（挡符号链接等绕法），越界直接抛 */
    private static void ensureInside(File parent, File dir) {
        try {
            String p = parent.getCanonicalPath();
            String d = dir.getCanonicalPath();
            if (!d.equals(p) && !d.startsWith(p + File.separator)) {
                throw new IllegalArgumentException("目录越界：" + dir.getName());
            }
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("目录校验失败：" + dir.getName());
        }
    }

    private static String safe(String s) {
        if (s == null) return "unknown";
        return s.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }
}
