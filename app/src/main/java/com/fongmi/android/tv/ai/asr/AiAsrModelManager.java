package com.fongmi.android.tv.ai.asr;

import android.content.Context;
import android.text.TextUtils;

import com.fongmi.android.tv.utils.Download;

import java.io.File;

/**
 * sherpa-onnx 语音模型管理器。
 *
 * 模型：sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01（中文，约31MB）
 * - 下载：tar.bz2 压缩包，走国内镜像直连（不用 VPN）
 * - 解压到 filesDir/ai_models/zh/，包含 encoder.int8.onnx / decoder.onnx / joiner.int8.onnx / tokens.txt
 * - SharedPreferences 记录下载状态
 */
public class AiAsrModelManager {

    /** 模型下载地址（GitHub 源站，App 内实际走 MIRROR_URL） */
    public static final String MODEL_URL_GITHUB =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01.tar.bz2";

    /**
     * 主下载地址（他的 Cloudflare Pages，xingchen-dl 项目 asr/ 目录，国内直连）。
     * 2026-10-24 从 GitHub raw 迁来（raw 国内经常超时）。App 内实际走此地址下载模型 zip。
     */
    public static final String MODEL_URL_MIRROR =
            "https://xingchen-dl.pages.dev/asr/model-zh.zip";

    /** 备用下载地址（jsDelivr CDN；模型 zip 20,146,260 字节已贴近其 20MB 上限，主源失败时兜底重试） */
    public static final String MODEL_URL_BACKUP =
            "https://cdn.jsdelivr.net/gh/XingChenApp/xingchen-asr@main/" +
            "model-zh.zip";

    /** 模型 zip 的 SHA-256（用于完整性校验） */
    private static final String MODEL_SHA256 =
            "e471991555f83d5b08454ef49e288864f15490d08dd322533badb82bea55ed87";

    /** 模型文件名（解压后，CTC 单文件模型） */
    private static final String[] MODEL_FILES = {
            "model.int8.onnx", "tokens.txt"
    };

    private static final String PREF_MODEL_READY = "xingchen.ai_asr_model_ready";

    public interface Callback {
        void onProgress(String stage, int percent);
        void onSuccess(File modelDir);
        void onError(String msg);
    }

    /** 模型目录 */
    public static File modelDir(Context context) {
        return new File(new File(context.getFilesDir(), "ai_models"), "zh");
    }

    /** 模型是否已下载完成（四个文件都在） */
    public static boolean isModelReady(Context context) {
        if (!context.getSharedPreferences("xingchen", Context.MODE_PRIVATE)
                .getBoolean(PREF_MODEL_READY, false)) {
            return false;
        }
        File dir = modelDir(context);
        for (String name : MODEL_FILES) {
            if (!new File(dir, name).exists()) return false;
        }
        return true;
    }

    private static void setModelReady(Context context, boolean ready) {
        context.getSharedPreferences("xingchen", Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_MODEL_READY, ready).apply();
    }

    /**
     * 下载并解压模型。
     * 回调在主线程执行。
     */
    public static void downloadModel(Context context, Callback callback) {
        if (isModelReady(context)) {
            runOnMain(() -> callback.onSuccess(modelDir(context)));
            return;
        }
        new Thread(() -> {
            try {
                File dir = modelDir(context);
                if (!dir.exists()) dir.mkdirs();

                // 1. 下载 zip
                runOnMain(() -> callback.onProgress("下载语音模型", 2));
                File zipFile = new File(dir.getParent(), "zh-model.zip");
                downloadFile(MODEL_URL_MIRROR, zipFile, MODEL_SHA256,
                        p -> runOnMain(() -> callback.onProgress("下载语音模型", 2 + p * 70 / 100)));

                // 2. 解压（Android 原生支持 zip）
                runOnMain(() -> callback.onProgress("解压模型", 75));
                extractZip(zipFile, dir);
                zipFile.delete();

                // 3. 校验
                runOnMain(() -> callback.onProgress("校验模型", 95));
                for (String name : MODEL_FILES) {
                    if (!new File(dir, name).exists()) {
                        // 尝试在子目录中查找（tar 包可能带顶层目录）
                        File found = findFile(dir, name);
                        if (found != null && !found.getParentFile().equals(dir)) {
                            moveFile(found, new File(dir, name));
                        } else {
                            throw new Exception("模型文件缺失：" + name);
                        }
                    }
                }

                setModelReady(context, true);
                runOnMain(() -> callback.onProgress("完成", 100));
                runOnMain(() -> callback.onSuccess(dir));
            } catch (Exception e) {
                setModelReady(context, false);
                String msg = "模型下载失败：" + e.getMessage();
                runOnMain(() -> callback.onError(msg));
            }
        }).start();
    }

    /** 删除模型（释放空间） */
    public static void deleteModel(Context context) {
        setModelReady(context, false);
        deleteRecursive(modelDir(context));
        File zip = new File(modelDir(context).getParent(), "zh-model.zip");
        if (zip.exists()) zip.delete();
    }

    /** 模型总大小 */
    public static long getModelSize(Context context) {
        return dirSize(modelDir(context));
    }

    /** 模型显示大小（如 "31MB"） */
    public static String getModelSizeText(Context context) {
        long size = getModelSize(context);
        if (size <= 0) return "约31MB";
        if (size < 1024 * 1024) return (size / 1024) + "KB";
        return String.format("%.0fMB", size / 1024.0 / 1024.0);
    }

    // --- 内部实现 ---

    /** 下载单个文件（主源失败自动切备用源重试一次，带 SHA-256 校验） */
    private static void downloadFile(String url, File dest, String expectedSha, ProgressListener listener) throws Exception {
        Exception primaryError = null;
        try {
            attemptDownload(url, dest, listener);
        } catch (Exception e) {
            primaryError = e;
        }
        if (primaryError != null) {
            // 主源失败，切备用源重试一次
            if (dest.exists()) dest.delete();
            try {
                attemptDownload(MODEL_URL_BACKUP, dest, listener);
            } catch (Exception e) {
                throw new Exception("下载失败（主源：" + shortMsg(primaryError) +
                        "；备用源：" + shortMsg(e) + "）");
            }
        }
        // SHA-256 校验
        if (!TextUtils.isEmpty(expectedSha) && !verifySha256(dest, expectedSha)) {
            dest.delete();
            throw new Exception("文件校验失败，可能已损坏");
        }
    }

    /** 单次下载尝试（15 分钟超时），失败抛异常 */
    private static void attemptDownload(String url, File dest, ProgressListener listener) throws Exception {
        if (dest.exists()) dest.delete();
        final Exception[] error = new Exception[1];
        final boolean[] done = new boolean[1];
        Download.create(url, dest).start(new Download.Callback() {
            @Override
            public void progress(int progress) {
                listener.onProgress(progress);
            }

            @Override
            public void success(File file) {
                synchronized (done) {
                    done[0] = true;
                    done.notifyAll();
                }
            }

            @Override
            public void error(String msg) {
                synchronized (done) {
                    error[0] = new Exception(TextUtils.isEmpty(msg) ? "下载失败" : msg);
                    done.notifyAll();
                }
            }
        });
        synchronized (done) {
            long deadline = System.currentTimeMillis() + 15 * 60 * 1000; // 15 分钟
            while (!done[0] && error[0] == null) {
                long wait = deadline - System.currentTimeMillis();
                if (wait <= 0) break;
                done.wait(wait);
            }
        }
        if (error[0] != null) throw error[0];
        if (!done[0]) throw new Exception("下载超时");
    }

    /** 异常信息摘要（避免超长堆栈污染提示文案） */
    private static String shortMsg(Exception e) {
        String m = e == null || e.getMessage() == null ? "未知错误" : e.getMessage();
        return m.length() > 60 ? m.substring(0, 60) + "…" : m;
    }

    private static boolean verifySha256(File file, String expected) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) md.update(buf, 0, n);
            fis.close();
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString().equalsIgnoreCase(expected);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解压 zip（Android 原生支持 java.util.zip）。
     */
    private static void extractZip(File zipFile, File destDir) throws Exception {
        java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile);
        java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
        byte[] buf = new byte[8192];
        while (entries.hasMoreElements()) {
            java.util.zip.ZipEntry entry = entries.nextElement();
            // 跳过目录和隐藏文件，取 basename 防止 zip 内带多层目录
            String name = new File(entry.getName()).getName();
            if (entry.isDirectory() || name.startsWith(".")) continue;
            File out = new File(destDir, name);
            java.io.InputStream in = zip.getInputStream(entry);
            java.io.FileOutputStream outStream = new java.io.FileOutputStream(out);
            int n;
            while ((n = in.read(buf)) > 0) outStream.write(buf, 0, n);
            in.close();
            outStream.close();
        }
        zip.close();
    }

    private static File findFile(File dir, String name) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                File found = findFile(f, name);
                if (found != null) return found;
            } else if (name.equals(f.getName())) {
                return f;
            }
        }
        return null;
    }

    private static void moveFile(File src, File dest) throws Exception {
        if (dest.exists()) dest.delete();
        if (!src.renameTo(dest)) {
            // 跨目录 rename 失败则复制
            java.io.FileInputStream in = new java.io.FileInputStream(src);
            java.io.FileOutputStream out = new java.io.FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            src.delete();
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) for (File c : files) deleteRecursive(c);
        }
        f.delete();
    }

    private static long dirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        if (dir.isFile()) return dir.length();
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) size += dirSize(f);
        return size;
    }

    private static void runOnMain(Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }

    private interface ProgressListener {
        void onProgress(int percent);
    }
}
