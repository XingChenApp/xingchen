package com.fongmi.android.tv.patch;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.OpenableColumns;

import com.fongmi.android.tv.utils.FileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * 补丁更新整链（潇洒哥 2026-11-14 亲定口径）：纯本地拼包器。
 * 不联网、不查版、不提醒；五道硬门按序执行、逐门留可查结果
 * （files/patch_update/last_result.txt）；任一门失败当场停、明说原因、
 * 现有版本零触碰，绝不自动改走全量下载——用不用全量由他看报错自选。
 *
 * 门序：1 补丁完整（容器头＋数据段指纹）→ 2 基包身份（已装版本号＋已装包整包指纹）
 * → 3 空间 → 4 拼包（ApkDiffPatch 同一 C++ 核）→ 5 目标包指纹＋签名（与发布包逐字节
 * 全等且签名者与已装一致）→ 全过才拉起系统安装界面。
 * 基包只读系统登记的已装包原文件（applicationInfo.sourceDir），不许用重打包副本。
 */
public final class PatchInstaller {

    public static final int STATE_WAIT = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_PASS = 2;
    public static final int STATE_FAIL = 3;

    private static final String WORK_DIR = "patch_update";
    /** 拼包临时量按目标包 1.2 倍估（设计账 §5 门 3 口径）。 */
    private static final long TEMP_FACTOR_NUM = 6;
    private static final long TEMP_FACTOR_DEN = 5;

    public interface Listener {
        /** meta 解析成功后回调一次：如「1.4.28 → 1.4.29」。 */
        void onMeta(String fromToText);

        void onStage(int stage, int state);

        void onDetail(String text);

        void onSuccess(File outApk);

        void onFailure(int stage, String reason);
    }

    private PatchInstaller() {
    }

    /** 清掉上次遗留的工作目录（拼好的包在内），后台线程删，不挡界面。 */
    public static void cleanStale(Context context) {
        Context app = context.getApplicationContext();
        new Thread(() -> deleteRecursive(workDir(app)), "xc-patch-clean").start();
    }

    public static void start(Context context, Uri patchUri, Listener listener) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> run(app, patchUri, listener, main), "xc-patch").start();
    }

    /** 拉起系统安装器（与手动装全量包同一道门，签名与版本由系统校验）。 */
    public static void launchInstall(Context context, File apk) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(FileUtil.getShareUri(apk), "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }

    private static File workDir(Context app) {
        return new File(app.getCacheDir(), WORK_DIR);
    }

    private static void run(Context app, Uri patchUri, Listener listener, Handler main) {
        StringBuilder log = new StringBuilder();
        logLine(log, "补丁更新 " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));
        int stage = 1;
        try {
            File dir = workDir(app);
            deleteRecursive(dir);
            if (!dir.mkdirs()) throw new Fail(1, "建不出临时目录，拼包没法干");
            File input = new File(dir, "input.xcpatch");
            String displayName = copyPicked(app, patchUri, input);
            logLine(log, "补丁文件：" + displayName + "（" + input.length() + " 字节）");

            // 门 1：补丁完整——容器头＋数据段指纹。
            postStage(main, listener, 1, STATE_RUNNING);
            PatchMeta meta = PatchMeta.parse(input);
            postMeta(main, listener, meta.fromVersionName + " → " + meta.toVersionName);
            logLine(log, "目标：" + meta.fromVersionName + "（" + meta.fromVersionCode + "）→ "
                    + meta.toVersionName + "（" + meta.toVersionCode + "）");
            String dataHash = sha256Range(input, meta.dataOffset, meta.patchDataSize);
            if (!meta.patchDataSha256.equals(dataHash)) {
                logLine(log, "门1 失败：数据段指纹 期望 " + shortHash(meta.patchDataSha256) + " 实际 " + shortHash(dataHash));
                throw new Fail(1, "补丁文件不完整或已损坏（指纹对不上）。多半是下载断了，重新下一次补丁再来。");
            }
            logLine(log, "门1 补丁完整：通过（指纹 " + shortHash(dataHash) + "）");
            postStage(main, listener, 1, STATE_PASS);

            // 门 2：基包身份——已装版本号＋已装包整包指纹。
            stage = 2;
            postStage(main, listener, 2, STATE_RUNNING);
            PackageManager pm = app.getPackageManager();
            PackageInfo self = pm.getPackageInfo(app.getPackageName(), 0);
            long curCode = longVersionCode(self);
            if (curCode != meta.fromVersionCode) {
                logLine(log, "门2 失败：已装 versionCode=" + curCode + "，补丁基版=" + meta.fromVersionCode);
                throw new Fail(2, "这补丁是给 " + meta.fromVersionName + " 用的，你现在是 " + self.versionName + "，版本对不上，这补丁用不了。");
            }
            File baseApk = new File(app.getApplicationInfo().sourceDir);
            String baseHash = sha256(baseApk);
            if (!meta.baseApkSha256.equals(baseHash)) {
                logLine(log, "门2 失败：已装包指纹 期望 " + shortHash(meta.baseApkSha256) + " 实际 " + shortHash(baseHash));
                throw new Fail(2, "你现在装的这一版跟补丁要的底包不是同一份（指纹对不上），这补丁用不了。");
            }
            logLine(log, "门2 基包身份：通过（" + self.versionName + "，指纹 " + shortHash(baseHash) + "）");
            postStage(main, listener, 2, STATE_PASS);

            // 门 3：空间。
            stage = 3;
            postStage(main, listener, 3, STATE_RUNNING);
            long need = meta.targetSize + meta.targetSize * TEMP_FACTOR_NUM / TEMP_FACTOR_DEN + meta.patchDataSize;
            long free = new StatFs(app.getCacheDir().getAbsolutePath()).getAvailableBytes();
            if (free < need) {
                long lackMb = (need - free + 1024 * 1024 - 1) / (1024 * 1024);
                logLine(log, "门3 失败：需要 " + need + " 字节，可用 " + free + " 字节");
                throw new Fail(3, "空间不够，还差约 " + lackMb + " MB。清出空间后再来。");
            }
            logLine(log, "门3 空间：通过（可用 " + free / (1024 * 1024) + " MB）");
            postStage(main, listener, 3, STATE_PASS);

            // 门 4：拼包。
            stage = 4;
            postStage(main, listener, 4, STATE_RUNNING);
            postDetail(main, listener, "正在拼包，包越大越久，十几秒到几分钟都有可能…");
            File rawPatch = new File(dir, "raw.patch");
            copyRange(input, meta.dataOffset, meta.patchDataSize, rawPatch);
            File tmpDir = new File(dir, "tmp");
            if (!tmpDir.mkdirs()) throw new Fail(4, "建不出拼包临时目录");
            File outApk = new File(dir, "out.apk");
            long t0 = System.currentTimeMillis();
            int rc = runPatchCore(baseApk.getAbsolutePath(), rawPatch.getAbsolutePath(),
                    outApk.getAbsolutePath(), tmpDir.getAbsolutePath());
            long costMs = System.currentTimeMillis() - t0;
            if (rc != 0 || !outApk.isFile() || outApk.length() == 0) {
                logLine(log, "门4 失败：拼包核返回 " + rc + "，耗时 " + costMs + "ms");
                if (rc == -1001)
                    throw new Fail(4, "拼包核没加载起来（这台机器的芯片用不了补丁更新）。把这句原样发我看。");
                throw new Fail(4, "拼包没拼成（拼包核返回 " + rc + "）。把这句原样发我看。");
            }
            logLine(log, "门4 拼包：通过（" + outApk.length() + " 字节，耗时 " + costMs / 1000.0 + "s）");
            postStage(main, listener, 4, STATE_PASS);

            // 门 5：目标包指纹＋签名。
            stage = 5;
            postStage(main, listener, 5, STATE_RUNNING);
            String outHash = sha256(outApk);
            if (!meta.targetApkSha256.equals(outHash)) {
                logLine(log, "门5 失败：产物指纹 期望 " + shortHash(meta.targetApkSha256) + " 实际 " + shortHash(outHash));
                throw new Fail(5, "拼出来的包不对（指纹对不上：期望 " + shortHash(meta.targetApkSha256)
                        + "，实际 " + shortHash(outHash) + "）。把这句原样发我看。");
            }
            byte[] outSigner = archiveSigner(pm, outApk);
            byte[] selfSigner = installedSigner(pm, app.getPackageName());
            if (outSigner == null || selfSigner == null || !Arrays.equals(outSigner, selfSigner)) {
                logLine(log, "门5 失败：产物签名者与已装不一致");
                throw new Fail(5, "拼出来的包签名跟你现在装的对不上，装不进去。把这句原样发我看。");
            }
            logLine(log, "门5 目标包指纹＋签名：通过（指纹 " + shortHash(outHash) + "）");
            postStage(main, listener, 5, STATE_PASS);

            logLine(log, "结果：五门全过，交系统安装");
            writeResultLog(app, log);
            main.post(() -> listener.onSuccess(outApk));
        } catch (Fail f) {
            logLine(log, "结果：停在门 " + f.stage + "：" + f.getMessage());
            writeResultLog(app, log);
            int s = f.stage;
            String reason = f.getMessage();
            main.post(() -> {
                listener.onStage(s, STATE_FAIL);
                listener.onFailure(s, reason);
            });
        } catch (Exception e) {
            String reason;
            if (e instanceof IllegalArgumentException) reason = e.getMessage();
            else if (e instanceof IOException) reason = "文件读写出问题了（" + e.getMessage() + "）。多半是空间或文件被动过，清出空间后重来一次。";
            else reason = "出问题了：" + e.getClass().getSimpleName() + "（" + e.getMessage() + "）。把这句原样发我看。";
            logLine(log, "结果：停在门 " + stage + "（异常）：" + reason);
            writeResultLog(app, log);
            int s = stage;
            main.post(() -> {
                listener.onStage(s, STATE_FAIL);
                listener.onFailure(s, reason);
            });
        }
    }

    /** 拼包核调用（ApkDiffPatch 安卓端，与桌面 ZipPatch 同一 C++ 核）。
     * 加载失败（如非 arm64 机型没有这份 .so）会以 Error 抛出，这里收成错误码 -1001 走门 4 显形。 */
    private static int runPatchCore(String basePath, String patchPath, String outPath, String tempPath) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        try {
            return ApkPatch.patch(basePath, patchPath, outPath, 512L * 1024 * 1024, tempPath, threads);
        } catch (Throwable t) {
            android.util.Log.e("XcPatch", "拼包核加载/调用失败", t);
            return -1001;
        }
    }

    private static String copyPicked(Context app, Uri uri, File dst) throws IOException {
        String name = "所选文件";
        Cursor cursor = null;
        try {
            cursor = app.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String queried = cursor.getString(idx);
                    if (queried != null && !queried.isEmpty()) name = queried;
                }
            }
        } catch (Exception ignored) {
            // 名字查不到不挡正事，留痕即可。
        } finally {
            if (cursor != null) cursor.close();
        }
        try (InputStream in = app.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dst)) {
            if (in == null) throw new IOException("补丁文件读不进来");
            copyStream(in, out);
        }
        return name;
    }

    private static void copyRange(File src, long offset, long length, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            long skipped = 0;
            while (skipped < offset) {
                long n = in.skip(offset - skipped);
                if (n <= 0) throw new IOException("补丁文件不完整");
                skipped += n;
            }
            byte[] buf = new byte[128 * 1024];
            long left = length;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) throw new IOException("补丁文件不完整");
                out.write(buf, 0, n);
                left -= n;
            }
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[128 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static String sha256(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = newDigest();
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return hex(md.digest());
        }
    }

    private static String sha256Range(File file, long offset, long length) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            long skipped = 0;
            while (skipped < offset) {
                long n = in.skip(offset - skipped);
                if (n <= 0) throw new IOException("补丁文件不完整");
                skipped += n;
            }
            MessageDigest md = newDigest();
            byte[] buf = new byte[256 * 1024];
            long left = length;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) throw new IOException("补丁文件不完整");
                md.update(buf, 0, n);
                left -= n;
            }
            return hex(md.digest());
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return sb.toString();
    }

    private static String shortHash(String hash) {
        return hash == null || hash.length() <= 8 ? String.valueOf(hash) : hash.substring(0, 8) + "…";
    }

    private static long longVersionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
    }

    private static byte[] archiveSigner(PackageManager pm, File apk) {
        try {
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
            return info == null ? null : firstSigner(info);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] installedSigner(PackageManager pm, String pkg) {
        try {
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            return firstSigner(pm.getPackageInfo(pkg, flags));
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] firstSigner(PackageInfo info) {
        if (info == null) return null;
        Signature[] signers;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (info.signingInfo == null) return null;
            signers = info.signingInfo.getApkContentsSigners();
        } else {
            signers = info.signatures;
        }
        return signers == null || signers.length == 0 ? null : signers[0].toByteArray();
    }

    private static void writeResultLog(Context app, StringBuilder log) {
        try {
            File dir = new File(app.getFilesDir(), WORK_DIR);
            if (!dir.exists()) dir.mkdirs();
            try (OutputStream out = new FileOutputStream(new File(dir, "last_result.txt"))) {
                out.write(log.toString().getBytes("UTF-8"));
            }
        } catch (Exception ignored) {
            // 留痕失败不挡正链结果呈现。
        }
    }

    private static void logLine(StringBuilder log, String line) {
        log.append(line).append('\n');
        android.util.Log.i("XcPatch", line);
    }

    private static void postStage(Handler main, Listener listener, int stage, int state) {
        main.post(() -> listener.onStage(stage, state));
    }

    private static void postMeta(Handler main, Listener listener, String text) {
        main.post(() -> listener.onMeta(text));
    }

    private static void postDetail(Handler main, Listener listener, String text) {
        main.post(() -> listener.onDetail(text));
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) for (File child : files) deleteRecursive(child);
        }
        f.delete();
    }

    private static final class Fail extends Exception {
        final int stage;

        Fail(int stage, String message) {
            super(message);
            this.stage = stage;
        }
    }
}
