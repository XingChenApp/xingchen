package com.fongmi.android.tv.ai.asr;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import com.fongmi.android.tv.utils.Download;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import dalvik.system.DexClassLoader;

/**
 * sherpa-onnx 插件管理器。
 *
 * 插件化设计（APK 体积控制）：
 * - sherpa-onnx AAR（37MB）不打包进 APK，compileOnly 参与编译
 * - 运行时按需下载插件：sherpa-plugin.dex（297KB）+ 对应 ABI 的 .so（16-23MB）
 * - 通过 DexClassLoader 加载 SherpaBridge，System.load() 加载 native 库
 *
 * 插件文件托管在可直连的地址（见 ~/workspace/xingchen-asr-plugin/README.md），
 * PLUGIN_BASE_URL 指向托管地址，支持国内直连。
 */
public class AiAsrPluginManager {

    /**
     * 插件托管主地址：他的 Cloudflare Pages（xingchen-dl 项目 asr/ 目录，国内直连）。
     * 2026-10-24 从 GitHub raw 迁来——raw 国内经常超时，jsDelivr 又有 20MB 单文件上限
     * （23MB 的 arm64 .so 回 403「File size exceeded the configured limit of 20 MB」），两路真机全灭过。
     */
    public static final String PLUGIN_BASE_URL = "https://xingchen-dl.pages.dev/asr/";

    /** 备用地址（自有仓库 raw 兜底；jsDelivr 因 20MB 上限放不下 .so，插件文件不再走它） */
    public static final String PLUGIN_MIRROR_URL = "https://raw.githubusercontent.com/XingChenApp/xingchen-asr/main/";

    private static final String DEX_NAME = "sherpa-plugin.dex";
    private static final String SO_NAME = "libsherpa-onnx-jni.so";

    // SHA-256 校验值（sherpa-onnx v1.13.7，2026-10-12 构建，CTC 模型版）
    private static final String SHA_DEX = "e1de5214f9071a66ccfc5be1ea8cbf9c82894ecaff40a1b9e7d4f0e1c24f96aa";
    private static final String SHA_SO_ARM64 = "8bb1c435bdf64366f4a326a80333d9bc804fca7b1f578e241900803f62a14455";
    private static final String SHA_SO_ARMV7 = "41ae5dd424a9c3f44ac747bb380962e3cf95bf83ccdc6c5e70d832cc5cf703cc";

    private static volatile IAiAsrBridge bridge;
    private static final AtomicBoolean loading = new AtomicBoolean(false);
    private static volatile boolean loadFailed = false;

    public interface Callback {
        void onProgress(String stage, int percent);
        void onSuccess(IAiAsrBridge bridge);
        void onError(String msg);
    }

    /** 插件目录 */
    public static File pluginDir(Context context) {
        return new File(context.getFilesDir(), "asr-plugin");
    }

    private static File dexFile(Context context) {
        return new File(pluginDir(context), DEX_NAME);
    }

    private static File soFile(Context context) {
        String abi = getAbi();
        return new File(new File(pluginDir(context), abi), SO_NAME);
    }

    /** 当前设备 ABI（只支持 arm64-v8a 和 armeabi-v7a） */
    public static String getAbi() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) return "arm64-v8a";
            if ("armeabi-v7a".equals(abi)) return "armeabi-v7a";
        }
        return Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";
    }

    /** 是否支持当前设备（arm64/armv7） */
    public static boolean isSupported() {
        String abi = getAbi();
        return "arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi);
    }

    /** 插件是否已下载（dex + so 都存在） */
    public static boolean isPluginDownloaded(Context context) {
        return dexFile(context).exists() && soFile(context).exists();
    }

    /** 插件是否已加载 */
    public static boolean isPluginLoaded() {
        return bridge != null;
    }

    /** 获取已加载的桥接实例（未加载返回 null） */
    public static IAiAsrBridge getBridge() {
        return bridge;
    }

    /**
     * 确保插件可用：已加载直接返回，否则下载后加载。
     * 回调在主线程执行。
     */
    public static void ensurePlugin(Context context, Callback callback) {
        if (bridge != null) {
            // 类注释承诺回调在主线程（低-04）：早退分支也要走主线程，别在调用线程直调
            IAiAsrBridge loaded = bridge;
            runOnMain(() -> callback.onSuccess(loaded));
            return;
        }
        if (loadFailed) {
            // 上次加载失败，允许重试
            loadFailed = false;
        }
        if (!loading.compareAndSet(false, true)) {
            runOnMain(() -> callback.onError("插件正在加载中，请稍候"));
            return;
        }
        new Thread(() -> {
            try {
                if (!isPluginDownloaded(context)) {
                    downloadPlugin(context, callback);
                } else {
                    callback.onProgress("正在加载插件", 90);
                }
                IAiAsrBridge b = loadPlugin(context);
                bridge = b;
                loading.set(false);
                runOnMain(() -> callback.onSuccess(b));
            } catch (Exception e) {
                loading.set(false);
                loadFailed = true;
                String msg = "插件加载失败：" + e.getMessage();
                runOnMain(() -> callback.onError(msg));
            }
        }).start();
    }

    /** 下载插件（dex + so） */
    private static void downloadPlugin(Context context, Callback callback) throws Exception {
        File dir = pluginDir(context);
        if (!dir.exists()) dir.mkdirs();

        // 1. 下载 dex（小，先下）
        runOnMain(() -> callback.onProgress("下载识别引擎 (1/2)", 5));
        File dex = dexFile(context);
        downloadFile(DEX_NAME, dex, SHA_DEX,
                p -> runOnMain(() -> callback.onProgress("下载识别引擎 (1/2)", 5 + p * 25 / 100)));

        // 2. 下载 so（大）
        runOnMain(() -> callback.onProgress("下载本地库 (2/2)", 30));
        String abi = getAbi();
        File soDir = new File(dir, abi);
        if (!soDir.exists()) soDir.mkdirs();
        File so = soFile(context);
        String sha = "arm64-v8a".equals(abi) ? SHA_SO_ARM64 : SHA_SO_ARMV7;
        downloadFile("libsherpa-onnx-jni-" + abi + ".so", so, sha,
                p -> runOnMain(() -> callback.onProgress("下载本地库 (2/2)", 30 + p * 60 / 100)));
    }

    /**
     * 下载单个文件（带 SHA-256 校验；主源失败自动切备用源重试一次）。
     * relPath 为相对文件名（如 sherpa-plugin.dex），主备地址分别拼接。
     */
    private static void downloadFile(String relPath, File dest, String expectedSha, ProgressListener listener) throws Exception {
        // 已存在且校验通过则跳过（断点续传简化：直接复用）
        if (dest.exists() && verifySha256(dest, expectedSha)) {
            listener.onProgress(100);
            return;
        }
        if (dest.exists()) dest.delete();
        File tmp = new File(dest.getParent(), dest.getName() + ".tmp");
        if (tmp.exists()) tmp.delete();

        Exception primaryError = null;
        try {
            attemptDownload(PLUGIN_BASE_URL + relPath, tmp, listener);
        } catch (Exception e) {
            primaryError = e;
        }
        if (primaryError != null) {
            // 主源失败，切备用源重试一次
            if (tmp.exists()) tmp.delete();
            try {
                attemptDownload(PLUGIN_MIRROR_URL + relPath, tmp, listener);
            } catch (Exception e) {
                throw new Exception("下载失败（主源：" + shortMsg(primaryError) +
                        "；备用源：" + shortMsg(e) + "）");
            }
        }

        if (!verifySha256(tmp, expectedSha)) {
            tmp.delete();
            throw new Exception("文件校验失败，可能已损坏");
        }
        if (!tmp.renameTo(dest)) throw new Exception("文件保存失败");
    }

    /** 单次下载尝试（10 分钟超时），失败抛异常 */
    private static void attemptDownload(String url, File tmp, ProgressListener listener) throws Exception {
        final Exception[] error = new Exception[1];
        final boolean[] done = new boolean[1];
        Download.create(url, tmp).start(new Download.Callback() {
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
                    error[0] = new Exception(msg);
                    done.notifyAll();
                }
            }
        });

        synchronized (done) {
            long deadline = System.currentTimeMillis() + 10 * 60 * 1000; // 10 分钟超时
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

    /** 加载插件：先 so，后 dex */
    private static IAiAsrBridge loadPlugin(Context context) throws Exception {
        File so = soFile(context);
        File dex = dexFile(context);
        if (!so.exists() || !dex.exists()) {
            throw new Exception("插件文件缺失");
        }

        // 1. 先加载 native 库（sherpa 类内部的 loadLibrary 会复用已加载的）
        System.load(so.getAbsolutePath());

        // 2. DexClassLoader 加载 dex
        File optDir = new File(pluginDir(context), "opt");
        if (!optDir.exists()) optDir.mkdirs();
        DexClassLoader loader = new DexClassLoader(
                dex.getAbsolutePath(),
                optDir.getAbsolutePath(),
                so.getParent(),  // native 库搜索路径
                AiAsrPluginManager.class.getClassLoader()
        );

        // 3. 实例化 SherpaBridge（IAiAsrBridge 由父 ClassLoader 加载，类型兼容）
        Class<?> clazz = loader.loadClass("com.fongmi.android.tv.ai.asr.SherpaBridge");
        Object instance = clazz.getDeclaredConstructor().newInstance();
        if (!(instance instanceof IAiAsrBridge)) {
            throw new Exception("插件类型不匹配");
        }
        return (IAiAsrBridge) instance;
    }

    /** 删除插件（释放空间） */
    public static void deletePlugin(Context context) {
        // 先释放桥再删文件（低-04）：只置空不 release，native 句柄要等进程退出才回收
        IAiAsrBridge b = bridge;
        bridge = null;
        if (b != null) {
            try {
                b.release();
            } catch (Throwable ignored) {
            }
        }
        loadFailed = false;
        deleteRecursive(pluginDir(context));
    }

    /** 插件总大小（用于设置页显示） */
    public static long getPluginSize(Context context) {
        return dirSize(pluginDir(context));
    }

    // --- 工具方法 ---

    private static boolean verifySha256(File file, String expected) {
        if (TextUtils.isEmpty(expected)) return true;
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
