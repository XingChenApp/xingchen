package com.fongmi.android.tv.clash;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * mihomo 内核下载 / 校验 / 解压 / 赋可执行权限。
 * <p>
 * 用户已拍板：内核不打进安装包，第一次开开关时下载（约十几兆）。
 * 二进制走用户自己的公开仓库做国内加速（与 asr 模型相同的托管思路），
 * 按顺序尝试多个镜像源，全部失败才报错。
 * <p>
 * 注意：本类只做 IO，不碰 UI；调用方自己决定线程（建议后台线程），
 * 进度回调在下载线程回。
 */
public final class KernelDownloader {

    /**
     * 本版星辰想要的内核版本（编译常量）。
     * 注意：wantVersion() 直接返回此常量——星辰新版把它抬高后，
     * 老用户下次开节点开关会自动重新下载新内核（升级通道）。
     * 换版本时必须同步更新 EXPECTED_SHA256。
     */
    public static final String DEFAULT_KERNEL_VERSION = "1.19.33";

    /** 期望的文件名：mihomo-android-arm64-v8-v{version}.gz（注意官方包名带 -v8）。 */
    private static final String ASSET_NAME = "mihomo-android-arm64-v8-v%s.gz";

    /** 本版内核包的 SHA256（托管在自有仓库的 gz 包实测值）。换版本时同步更新。 */
    private static final String EXPECTED_SHA256 = "2ddbd3aaa884f52b4568f406876ec81dddeef9f1bb67b90d4004d356e2c6a708";

    /** 最小合法体积（小于此值视为下载损坏）。 */
    private static final long MIN_GZ_BYTES = 5L * 1024 * 1024;

    private KernelDownloader() {
    }

    public interface Listener {
        void onProgress(long downloaded, long total);

        void onDone(File binary);

        void onError(String msg);
    }

    /** 内核工作目录：<filesDir>/clash。 */
    public static File workDir(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), "clash");
        dir.mkdirs();
        return dir;
    }

    /** 内核二进制路径：<filesDir>/clash/mihomo（下载版；安卓 10+ 在私有目录不让执行，只作兜底）。 */
    public static File binaryFile(Context context) {
        return new File(workDir(context), "mihomo");
    }

    /**
     * 随包内置的内核（2026-10-15 他拍板「塞」）：以 libmihomo.so 放 jniLibs，
     * 系统装包时解到 nativeLibraryDir——那是安卓唯一允许执行自带程序的目录。
     * 不存在（老包/异架构）返回 null。
     */
    public static File bundledBinary(Context context) {
        try {
            String dir = context.getApplicationContext().getApplicationInfo().nativeLibraryDir;
            if (dir == null) return null;
            File f = new File(dir, "libmihomo.so");
            return f.exists() && f.length() > 1024 * 1024 ? f : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 内核是否可用：内置版在就算就绪；否则看下载版（存在、可执行、版本与记录一致）。 */
    public static boolean isReady(Context context) {
        if (bundledBinary(context) != null) return true;
        File bin = binaryFile(context);
        if (!bin.exists() || !bin.canExecute() || bin.length() < 1024 * 1024) return false;
        String want = wantVersion(context);
        String have = ClashSettings.getKernelVersion(context);
        return want.equals(have);
    }

    /**
     * 本版星辰想要的内核版本 = 编译常量。
     * 老包曾返回 prefs 里记录的已下载版本，导致 want 与 have 永远相等、
     * 新版抬高常量后老用户也不重下——升级通道是死的，现改对。
     */
    public static String wantVersion(Context context) {
        return DEFAULT_KERNEL_VERSION;
    }

    /**
     * 确保内核可用：已就绪直接回 onDone，否则起后台线程下载。
     * 失败原因通过 onError 返回纯中文短句（UI 可直接展示）。
     */
    public static void ensure(final Context context, final Listener listener) {
        final Context app = context.getApplicationContext();
        if (isReady(app)) {
            File bundled = bundledBinary(app);
            listener.onDone(bundled != null ? bundled : binaryFile(app));
            return;
        }
        // 防重：旧形每次调用都起一条下载线程，两次齐调同写同一个 tmp/bin 互相踩踏。
        // 改为单飞——登记与收单都在同一把锁内：在途时后来者只登记、由在途那发
        // 统一回调；批次关单后到达的才另起一发，不漏回调也不双开下载。
        synchronized (downloadLock) {
            pendingListeners.add(listener);
            if (downloading) return;
            downloading = true;
        }
        new Thread(() -> {
            try {
                File bin = download(app, progressBroadcaster);
                for (Listener l : drainPending()) l.onDone(bin);
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "内核下载失败" : e.getMessage();
                for (Listener l : drainPending()) l.onError(msg);
            }
        }, "clash-kernel-dl").start();
    }

    private static final Object downloadLock = new Object();

    /** 在途下载标志（单飞闸门，只在 downloadLock 内读写） */
    private static boolean downloading;

    /** 在途期间登记的等待方，完成时一次性取走回调 */
    private static final java.util.concurrent.CopyOnWriteArrayList<Listener> pendingListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final Listener progressBroadcaster = new Listener() {
        @Override
        public void onProgress(long downloaded, long total) {
            for (Listener l : pendingListeners) l.onProgress(downloaded, total);
        }

        @Override
        public void onDone(File binary) {
        }

        @Override
        public void onError(String msg) {
        }
    };

    private static java.util.List<Listener> drainPending() {
        synchronized (downloadLock) {
            downloading = false;
            java.util.List<Listener> out = new java.util.ArrayList<>(pendingListeners);
            pendingListeners.clear();
            return out;
        }
    }

    /**
     * 候选下载地址（按顺序试）。占位 %s 为版本号。
     * 顺序：他的 Cloudflare（xingchen-dl.pages.dev，国内直连、文件在他自己账号下）第一，
     * 自有仓库 raw 第二，官方第三。jsDelivr 单文件上限 20MB、本包 21.9MB 必失败，已剔除。
     * 每个候选下完都验 SHA256，指纹不对就试下一个，来源不可信也下不到假内核。
     */
    static List<String> candidateUrls(String version) {
        String asset = String.format(ASSET_NAME, version);
        List<String> urls = new ArrayList<>();
        // 1) 他的 Cloudflare Pages（xingchen-dl 项目 clash/ 目录，2026-10-15 他拍板放这里）
        urls.add("https://xingchen-dl.pages.dev/clash/" + asset);
        // 2) 自有仓库 raw（XingChenApp/xingchen-asr 的 clash/ 目录，国内多半下不动，兜底）
        urls.add("https://raw.githubusercontent.com/XingChenApp/xingchen-asr/main/clash/" + asset);
        // 3) 官方 release 直链（需境外网络时才通，兜底）
        urls.add("https://github.com/MetaCubeX/mihomo/releases/download/v" + version + "/" + asset);
        return urls;
    }

    private static File download(Context context, Listener listener) throws Exception {
        String version = wantVersion(context);
        File dir = workDir(context);
        File gz = new File(dir, "mihomo.gz.tmp");
        Exception last = null;
        boolean ok = false;
        for (String url : candidateUrls(version)) {
            try {
                fetchToFile(url, gz, listener);
                verifyDownload(gz);
                ok = true;
                break;
            } catch (Exception e) {
                last = e;
            }
        }
        if (!ok) {
            throw new Exception("内核下载失败，已试多个镜像仍不可用"
                    + (last == null ? "" : "：" + last.getMessage()));
        }
        File bin = binaryFile(context);
        gunzip(gz, bin);
        gz.delete();
        chmodX(bin);
        if (!bin.canExecute()) throw new Exception("内核无可执行权限");
        ClashSettings.setKernelVersion(context, version);
        return bin;
    }

    /** 强校验：gzip 魔数 + 最小体积 + SHA256。任何一项不符就抛，调用方换下一个镜像。 */
    private static void verifyDownload(File gz) throws Exception {
        if (!looksLikeGzip(gz) || gz.length() < MIN_GZ_BYTES) {
            throw new Exception("文件校验失败（非 gzip 或体积异常）");
        }
        String sha = sha256Hex(gz);
        if (!EXPECTED_SHA256.equalsIgnoreCase(sha)) {
            throw new Exception("文件校验失败（签名不符）");
        }
    }

    /** 文件的 SHA256 十六进制摘要。 */
    private static String sha256Hex(File f) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
        } finally {
            in.close();
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void fetchToFile(String urlStr, File out, Listener listener) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = java.net.URI.create(urlStr).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "XingChen-ClashKit");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            long total = conn.getContentLengthLong();
            InputStream in = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int r;
            while ((r = in.read(buf)) > 0) {
                fos.write(buf, 0, r);
                done += r;
                listener.onProgress(done, total);
            }
            fos.close();
            in.close();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean looksLikeGzip(File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int a = in.read();
            int b = in.read();
            in.close();
            return a == 0x1F && b == 0x8B;
        } catch (Exception e) {
            return false;
        }
    }

    private static void gunzip(File gz, File out) throws Exception {
        GZIPInputStream in = new GZIPInputStream(new java.io.FileInputStream(gz));
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[64 * 1024];
        int r;
        while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
        fos.close();
        in.close();
    }

    private static void chmodX(File f) {
        try {
            android.system.Os.chmod(f.getAbsolutePath(), 0755);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Runtime.getRuntime().exec(new String[]{"chmod", "755", f.getAbsolutePath()}).waitFor();
        } catch (Throwable ignored) {
        }
    }
}
