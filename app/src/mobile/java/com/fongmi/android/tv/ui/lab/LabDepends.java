package com.fongmi.android.tv.ui.lab;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 运行前依赖的备料逻辑（对齐上游 LabEnv.prepareDependency 口径）：
 * 判定缺不缺（check_file / save 落点 / 解压标记）→ 下载 → 按声明原样落盘或整包解压
 * （单顶层目录剥壳、合并进目标目录）→ 解压树补可执行 → binary_path 归位 → 写完成标记。
 *
 * 纯 java.io 逻辑，不碰 android.*：路径占位（{sdcard} 等）由调用方（LabRunner）
 * 先展开再传进来；下载动作也由调用方以 Downloader 注入（它有自己的代理/超时口径）。
 */
public final class LabDepends {

    /** 下载动作注入点：把 url 拉到 dest 文件 */
    public interface Downloader {
        void download(String url, File dest) throws Exception;
    }

    private LabDepends() {
    }

    // ---------------- 路径口径 ----------------

    /** 已展开的声明路径 → 实际文件：绝对原样，相对落包目录 */
    public static File resolve(File pkgDir, String path) {
        if (path == null) return null;
        String p = path.trim();
        if (p.isEmpty()) return null;
        return p.startsWith("/") ? new File(p) : new File(pkgDir, p);
    }

    /**
     * 落点收口（与安装器 zip-slip 守卫同口径）：只许包目录内，或 {sdcard} 展开后的
     * 公共目录（上游订阅配置类 save_to 就落那儿）；其余一律拒绝，不落盘。
     */
    public static File guard(File pkgDir, File f, String what) throws Exception {
        String canon = f.getCanonicalPath();
        String pkg = pkgDir.getCanonicalPath();
        if (canon.equals(pkg) || canon.startsWith(pkg + File.separator)) return f;
        String sd = "/storage/emulated/0";
        if (canon.equals(sd) || canon.startsWith(sd + File.separator)) return f;
        throw new Exception(what + "跑出允许的目录了：" + f.getPath());
    }

    /** 解压模式判定：extractTo 非空（"."=包根）即解压；否则有 file 走 save 原样落盘 */
    public static boolean isExtract(LabManifest.LabDownload d) {
        return d != null && d.extractTo != null && !d.extractTo.trim().isEmpty();
    }

    public static File checkFile(File pkgDir, LabManifest.LabDownload d) {
        return d == null ? null : resolve(pkgDir, d.checkFile);
    }

    public static File saveDest(File pkgDir, LabManifest.LabDownload d) {
        return d == null ? null : resolve(pkgDir, d.file);
    }

    public static File extractDir(File pkgDir, LabManifest.LabDownload d) {
        if (!isExtract(d)) return null;
        String to = d.extractTo.trim();
        if (".".equals(to)) return pkgDir;
        return resolve(pkgDir, to);
    }

    /** 解压完成标记（上游 depMarker 同名口径）：在解压目录里，见到它就不再重下重解 */
    public static File marker(File extractDir, LabManifest.LabDownload d) {
        String url = d == null || d.url == null ? "" : d.url;
        return new File(extractDir, ".lab_dep_ok_" + Integer.toHexString(url.hashCode()));
    }

    // ---------------- 判定与备料 ----------------

    /** 这条依赖还缺不缺（上游 dependencyNeeded 口径） */
    public static boolean needed(File pkgDir, LabManifest.LabDownload d) {
        if (d == null || d.url == null || d.url.trim().isEmpty()) return false;
        File check = checkFile(pkgDir, d);
        if (check != null) return !check.exists();
        if (isExtract(d)) {
            File dir = extractDir(pkgDir, d);
            return dir == null || !marker(dir, d).exists();
        }
        File dest = saveDest(pkgDir, d);
        if (dest != null) return !dest.exists();
        return true;
    }

    /**
     * 备料全程：下载到缓存 → save 模式原样落盘；extract 模式解进暂存、补权限、
     * 剥单顶层目录后合并进目标目录，再按 binary_path（d.file）把二进制归位，最后写标记。
     * 所有落点先过 guard（包目录/公共目录之外拒绝）。
     */
    public static void fetch(File pkgDir, File cacheDir, LabManifest.LabDownload d, Downloader dl) throws Exception {
        // 主/次两片各自判缺：主包已备好时只补第二分片，不重下主包
        if (needed(pkgDir, d)) fetchWithMirrors(pkgDir, cacheDir, d, dl);
        // 第二分片（liburl）：主包落完后按同一落点再备一份（依赖库包与主包同目录
        // 合并）。第二分片自己不再带 liburl，不会递归
        if (d.libUrl != null && !d.libUrl.trim().isEmpty()) {
            LabManifest.LabDownload lib = new LabManifest.LabDownload();
            lib.url = d.libUrl.trim();
            lib.extractTo = d.extractTo;
            lib.required = d.required;
            // file（归位目标）与 checkFile（在位判定）必须随副本带上：丢了 file，
            // save 模式/按名归位对第二分片不生效；丢了 checkFile，已在位也会
            // 误判「缺」每次启动重复下载
            lib.file = d.file;
            lib.checkFile = d.checkFile;
            if (needed(pkgDir, lib)) fetchOne(pkgDir, cacheDir, lib, dl, lib.url);
        }
    }

    /**
     * 主下载的来源顺序（纯函数，便于单测）：主 url 在前、mirrors 按声明顺序在后，
     * 空值与重复去掉（解析层在 url 为空时已把 mirrors[0] 提成主 url，列表里会重一份）。
     */
    static List<String> candidateUrls(LabManifest.LabDownload d) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (d != null) {
            if (d.url != null && !d.url.trim().isEmpty()) set.add(d.url.trim());
            if (d.mirrors != null) {
                for (String m : d.mirrors) {
                    if (m != null && !m.trim().isEmpty()) set.add(m.trim());
                }
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 按 candidateUrls 顺序逐个真尝试下载备料：单个来源失败（连不上/下坏/解不开）
     * 继续下一个，全失败才把最后一个错抛上去。落点、解压与完成标记始终以主
     * 声明（d）为准——镜像只是同一份文件的不同来源，不许因换源改落点或标记键。
     */
    private static void fetchWithMirrors(File pkgDir, File cacheDir, LabManifest.LabDownload d, Downloader dl) throws Exception {
        Exception last = null;
        for (String u : candidateUrls(d)) {
            try {
                fetchOne(pkgDir, cacheDir, d, dl, u);
                return;
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new Exception("依赖下不下来：没给能用的下载地址");
    }

    private static void fetchOne(File pkgDir, File cacheDir, LabManifest.LabDownload d, Downloader dl, String useUrl) throws Exception {
        String url = useUrl.trim();
        String name = fileNameOf(url);
        File tmp = new File(cacheDir, "lab_dep_" + sanitize(name));
        dl.download(url, tmp);
        try {
            if (isExtract(d)) {
                File root = extractDir(pkgDir, d);
                guard(pkgDir, root, "依赖解压目录");
                root.mkdirs();
                File staging = new File(cacheDir, "lab_extract_" + System.currentTimeMillis());
                staging.mkdirs();
                try {
                    if (isArchiveName(name)) {
                        extractArchive(tmp, staging);
                    } else {
                        copyFile(tmp, new File(staging, name));
                    }
                    chmodTree(staging);
                    File source = unwrap(staging, root);
                    mergeTree(source, root);
                    // binary_path 归位（上游 normalizeBinary 口径）：声明位置没有，
                    // 就在刚解压的内容里按文件名找一份拷过去并补可执行；
                    // 再找不到、而解压物里拢共只有一个文件（单文件 .gz 解出的
                    // 带版本号名字，如 mihomo-android-arm64-…），就拿它归位——
                    // 上游对这种 .gz 一律按 tar 读、解出零条目，归位无从谈起，
                    // 星辰这里按单文件口径补上，样本才走得通
                    if (d.file != null && !d.file.trim().isEmpty()) {
                        File want = resolve(pkgDir, d.file);
                        guard(pkgDir, want, "依赖落点");
                        if (!want.exists()) {
                            File found = findFile(source, want.getName(), 0);
                            if (found == null) {
                                java.util.List<File> all = new java.util.ArrayList<>();
                                collectFiles(source, all, 0);
                                if (all.size() == 1) found = all.get(0);
                            }
                            if (found == null) found = findFile(pkgDir, want.getName(), 0);
                            if (found != null && found.isFile()) {
                                if (want.getParentFile() != null) want.getParentFile().mkdirs();
                                copyFile(found, want);
                                chmodFile(want);
                            }
                        } else {
                            chmodFile(want);
                        }
                    }
                } finally {
                    deleteDir(staging);
                }
                try {
                    marker(root, d).createNewFile();
                } catch (Exception ignored) {
                }
            } else {
                File dest = saveDest(pkgDir, d);
                if (dest == null) dest = new File(pkgDir, name);
                guard(pkgDir, dest, "依赖落点");
                if (dest.getParentFile() != null) dest.getParentFile().mkdirs();
                copyFile(tmp, dest);
                chmodFile(dest);
            }
        } finally {
            tmp.delete();
        }
    }

    // ---------------- 解压 ----------------

    public static boolean isArchiveName(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.endsWith(".gz") || n.endsWith(".tgz") || n.endsWith(".xz")
                || n.endsWith(".zip") || n.endsWith(".7z") || n.endsWith(".tar");
    }

    /** 按扩展名解压到 outDir（条目逐个过 zip-slip 守卫，越界条目跳过不落盘） */
    public static void extractArchive(File archive, File outDir) throws Exception {
        String n = archive.getName().toLowerCase();
        outDir.mkdirs();
        if (n.endsWith(".tar.gz") || n.endsWith(".tgz") || n.endsWith(".tar.xz")
                || n.endsWith(".tar")) {
            extractTar(archive, outDir);
        } else if (n.endsWith(".gz") || n.endsWith(".xz")) {
            // 单文件压缩：解出来就是同名文件（去掉压缩后缀）
            String base = archive.getName().replaceFirst("(?i)\\.(gz|xz)$", "");
            // 暂存名带 lab_dep_ 前缀时解出的名字会跟着带前缀，按原下载名再剥一次
            if (base.startsWith("lab_dep_")) base = base.substring("lab_dep_".length());
            File out = new File(outDir, base);
            guardDir(outDir, out);
            try (InputStream in = singleFileStream(archive, n);
                 OutputStream outS = new BufferedOutputStream(new FileOutputStream(out))) {
                pump(in, outS);
            }
        } else if (n.endsWith(".zip")) {
            try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    File out = new File(outDir, e.getName().replace('\\', '/'));
                    if (!guardDirQuiet(outDir, out)) continue;
                    if (out.getParentFile() != null) out.getParentFile().mkdirs();
                    try (OutputStream outS = new BufferedOutputStream(new FileOutputStream(out))) {
                        pump(zin, outS);
                    }
                }
            }
        } else if (n.endsWith(".7z")) {
            try (SevenZFile sz = new SevenZFile(archive)) {
                SevenZArchiveEntry e;
                while ((e = sz.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    File out = new File(outDir, e.getName().replace('\\', '/'));
                    if (!guardDirQuiet(outDir, out)) continue;
                    if (out.getParentFile() != null) out.getParentFile().mkdirs();
                    try (OutputStream outS = new BufferedOutputStream(new FileOutputStream(out))) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = sz.read(buf)) > 0) outS.write(buf, 0, r);
                    }
                }
            }
        } else {
            copyFile(archive, new File(outDir, archive.getName()));
        }
    }

    private static InputStream singleFileStream(File archive, String lowerName) throws Exception {
        InputStream raw = new BufferedInputStream(new FileInputStream(archive));
        if (lowerName.endsWith(".gz")) return new GZIPInputStream(raw);
        return new XZCompressorInputStream(raw);
    }

    private static void extractTar(File archive, File outDir) throws Exception {
        // 压缩层按扩展名明走（不用 CompressorStreamFactory 自动识别：
        // 它拖 commons-io 的版本配对，App 的依赖配对里没这层必要风险）
        InputStream raw = new BufferedInputStream(new FileInputStream(archive));
        InputStream in = raw;
        String n = archive.getName().toLowerCase();
        if (n.endsWith(".tar.gz") || n.endsWith(".tgz")) {
            in = new GZIPInputStream(raw);
        } else if (n.endsWith(".tar.xz")) {
            in = new XZCompressorInputStream(raw);
        }
        try (InputStream tin = new BufferedInputStream(in)) {
            extractTarStream(tin, outDir);
        }
    }

    /**
     * 最小 ustar 读法（512 字节头：name/prefix、八进制 size、typeflag）。
     * 不用 commons-compress 的 TarArchiveInputStream：它 1.27.1 的
     * TarArchiveEntry 构造要 org.apache.commons.lang3，App 没这个依赖，
     * 引进来就是真机 NoClassDefFoundError。只处理普通文件与目录条目，
     * GNU 长名（'L'）与 pax 扩展头（'x'）按其语义消费/跳过，其余类型跳过。
     */
    static void extractTarStream(InputStream in, File outDir) throws Exception {
        byte[] header = new byte[512];
        String longName = null;
        while (true) {
            int hn = readFully(in, header);
            if (hn < 512 || isZeroBlock(header)) break;
            String name = cstr(header, 0, 100);
            String prefix = cstr(header, 345, 155);
            if (!prefix.isEmpty()) name = prefix + "/" + name;
            long size = parseOctal(header, 124, 12);
            char type = (char) header[156];
            long toSkip = ((size + 511) / 512) * 512;
            if (type == 'L') {
                byte[] buf = new byte[(int) size];
                readFully(in, buf);
                longName = new String(buf, java.nio.charset.StandardCharsets.UTF_8).replace("\0", "").trim();
                skipFully(in, toSkip - size);
                continue;
            }
            if (type == 'x' || type == 'g') {
                skipFully(in, toSkip);
                longName = null;
                continue;
            }
            String entryName = longName != null ? longName : name;
            longName = null;
            if (type == '5' || entryName.endsWith("/")) {
                skipFully(in, toSkip);
                continue;
            }
            if (type != '0' && type != '\0' && type != '7') {
                skipFully(in, toSkip);
                continue;
            }
            File out = new File(outDir, entryName.replace('\\', '/'));
            if (!guardDirQuiet(outDir, out)) {
                skipFully(in, toSkip);
                continue;
            }
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            try (OutputStream outS = new BufferedOutputStream(new FileOutputStream(out))) {
                long left = size;
                byte[] buf = new byte[8192];
                while (left > 0) {
                    int r = in.read(buf, 0, (int) Math.min(buf.length, left));
                    if (r < 0) throw new Exception("压缩包不完整（tar 条目被截断）");
                    outS.write(buf, 0, r);
                    left -= r;
                }
            }
            skipFully(in, toSkip - size);
        }
    }

    private static int readFully(InputStream in, byte[] buf) throws Exception {
        int n = 0;
        while (n < buf.length) {
            int r = in.read(buf, n, buf.length - n);
            if (r < 0) break;
            n += r;
        }
        return n;
    }

    private static void skipFully(InputStream in, long bytes) throws Exception {
        long left = bytes;
        byte[] buf = new byte[8192];
        while (left > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (r < 0) break;
            left -= r;
        }
    }

    private static boolean isZeroBlock(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static String cstr(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, java.nio.charset.StandardCharsets.UTF_8).trim();
    }

    private static long parseOctal(byte[] b, int off, int len) {
        long v = 0;
        for (int i = off; i < off + len; i++) {
            byte c = b[i];
            if (c >= '0' && c <= '7') v = v * 8 + (c - '0');
            else if (v > 0 || c == ' ') break;
        }
        return v;
    }

    // ---------------- 文件小工具 ----------------

    /** 单顶层目录剥壳（上游 prepareDependency 口径）：暂存里只有一个目录就进它；
     *  或有与目标目录同名的目录就进它；否则原样 */
    private static File unwrap(File staging, File extractRoot) {
        File[] top = staging.listFiles();
        if (top == null) return staging;
        if (top.length == 1 && top[0].isDirectory()) return top[0];
        String targetName = extractRoot.getName();
        for (File entry : top) {
            if (entry.isDirectory() && entry.getName().equals(targetName)) return entry;
        }
        return staging;
    }

    private static void mergeTree(File src, File dst) throws Exception {
        mergeTree(src, dst, 0);
    }

    private static void mergeTree(File src, File dst, int depth) throws Exception {
        if (src == null || !src.exists() || depth > 40) return;
        if (src.isDirectory()) {
            if (!dst.exists()) dst.mkdirs();
            File[] children = src.listFiles();
            if (children == null) return;
            for (File child : children) mergeTree(child, new File(dst, child.getName()), depth + 1);
        } else {
            if (dst.getParentFile() != null) dst.getParentFile().mkdirs();
            copyFile(src, dst);
        }
    }

    /** 收集目录下全部普通文件（深度 8，跳过 rootfs 子树） */
    private static void collectFiles(File dir, java.util.List<File> out, int depth) {
        if (dir == null || !dir.isDirectory() || depth > 8) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile()) out.add(f);
            else if (f.isDirectory() && !"rootfs".equals(f.getName())) collectFiles(f, out, depth + 1);
        }
    }

    /** 按名找文件（上游 findFile 口径：深度 8，跳过 rootfs 子树——那是另一个包的根） */
    private static File findFile(File dir, String name, int depth) {
        if (dir == null || !dir.isDirectory() || depth > 8) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && name.equals(file.getName())) return file;
        }
        for (File file : files) {
            if (file.isDirectory() && !"rootfs".equals(file.getName())) {
                File found = findFile(file, name, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** 递归补 755：一条 chmod -R 走内核递归（大树不逐个 fork），失败回退逐文件 setExecutable */
    public static void chmodTree(File root) {
        if (root == null || !root.exists()) return;
        try {
            new ProcessBuilder("chmod", "-R", "755", root.getAbsolutePath()).start().waitFor();
            return;
        } catch (Exception ignored) {
        }
        chmodFallback(root);
    }

    private static void chmodFallback(File f) {
        if (f.isDirectory()) {
            f.setExecutable(true, false);
            f.setReadable(true, false);
            f.setWritable(true, false);
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) chmodFallback(k);
        } else {
            f.setExecutable(true, false);
            f.setReadable(true, false);
            f.setWritable(true, false);
        }
    }

    /** 单文件补可执行（下载落盘的二进制要能直接被命令调到） */
    public static void chmodFile(File f) {
        if (f == null || !f.exists()) return;
        try {
            f.setExecutable(true, false);
            f.setReadable(true, false);
        } catch (Exception ignored) {
        }
    }

    public static void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new BufferedInputStream(new FileInputStream(src));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(dst))) {
            pump(in, out);
        }
    }

    private static void pump(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static void guardDir(File root, File out) throws Exception {
        if (!guardDirQuiet(root, out)) throw new Exception("压缩包里有跑出目录的条目：" + out.getName());
    }

    private static boolean guardDirQuiet(File root, File out) {
        try {
            String rp = root.getCanonicalPath() + File.separator;
            return out.getCanonicalPath().startsWith(rp);
        } catch (Exception e) {
            return false;
        }
    }

    static void deleteDir(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteDir(k);
        }
        f.delete();
    }

    private static String fileNameOf(String url) {
        String u = url == null ? "" : url.split("\\?")[0];
        int i = u.lastIndexOf('/');
        String name = i >= 0 ? u.substring(i + 1) : u;
        return name.isEmpty() ? "dep.bin" : name;
    }

    private static String sanitize(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isEmpty() ? "archive" : cleaned;
    }
}
