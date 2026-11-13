package com.fongmi.android.tv.ui.lab;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 运行环境管理：python / nodejs / php / mihomo / alist / frp / aria2 / ubuntu / java / bun / ffmpeg。
 * 照参照物：装到 files/lab/runtimes/<name>/，二进制归位 bin/，整目录 chmod 755。
 * 安装两条路：在线（从下载地址拉包）/ 本地（选 zip）。都走 installFromFile 同一条管线：
 * 解压 → 755 → bin 校验 → 登记注册表。校验失败只报人话，不抛技术栈、不静默。
 * ubuntu 例外：rootfs 不是 bin/ 布局，proot 跑法基座还没接——装与跑都过不了校验，如实报错，不开后门。
 */
public final class LabRuntimeManager {

    public static final List<String> RUNTIMES = Arrays.asList(
            "python", "nodejs", "php", "mihomo", "alist", "frp", "aria2",
            "ubuntu", "java", "bun", "ffmpeg");

    /** 环境包下载地址（公开仓 XingChenApp/xingchen-lab-assets 已建，按清单回填；ubuntu 留空等 proot） */
    public static final Map<String, String> DOWNLOAD_URLS = new HashMap<>();
    /** 包版本（安装登记用，与清单一致） */
    private static final Map<String, String> PKG_VERSION = new HashMap<>();
    /** 包大小（≤20MB 的走 jsDelivr 镜像优先） */
    private static final Map<String, Long> PKG_SIZE = new HashMap<>();
    /** 整包 sha256（下载后校验用） */
    private static final Map<String, String> PKG_SHA256 = new HashMap<>();

    /** 正在安装中的环境（防重复：同环境并发安装会抢同一个目录互相 deleteDir） */
    private static final java.util.Set<String> INSTALLING =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /** 取消令牌：beginInstall 时按环境新建一份，endInstall 时摘掉，天然与单次安装一一对应，
     * 上一次安装迟到的取消点不到新令牌，不会误伤下一次安装 */
    private static final java.util.Map<String, CancelToken> CANCEL_TOKENS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 取消信号：执行体在分块循环与段间检查点 check()，命中就以此异常 unwind 到收口处 */
    private static final class InstallCancelledException extends Exception {
    }

    private static final class CancelToken {
        private volatile boolean cancelled;

        void cancel() {
            cancelled = true;
        }

        boolean isCancelled() {
            return cancelled;
        }

        void check() throws InstallCancelledException {
            if (cancelled) throw new InstallCancelledException();
        }
    }

    /** 取消某个正在装的环境：没在装（无令牌）就是空操作 */
    public static void cancelInstall(String runtime) {
        CancelToken t = CANCEL_TOKENS.get(LabManifest.normalizeRuntime(runtime));
        if (t != null) t.cancel();
    }

    public static boolean isInstalling(String runtime) {
        return INSTALLING.contains(LabManifest.normalizeRuntime(runtime));
    }

    private static boolean beginInstall(String runtime, Listener listener) {
        if (INSTALLING.add(runtime)) {
            CANCEL_TOKENS.put(runtime, new CancelToken());
            return true;
        }
        fail(listener, "「" + label(runtime) + "」正在安装中，等它装完再试");
        return false;
    }

    private static void endInstall(String runtime, CancelToken token) {
        // 按值条件摘令牌：只删自己登记的那一份。先摘令牌再摘在装标记——
        // 新安装只能在标记摘掉后登记，旧收口的条件删删不掉新安装刚登记的令牌，
        // 也不会在新安装登记后回头摘掉它的在装标记
        if (token != null) CANCEL_TOKENS.remove(runtime, token);
        INSTALLING.remove(runtime);
    }

    /** 本次安装的令牌（beginInstall 已建）；防御性兜底：万一不在表里，给一份永不取消的 */
    private static CancelToken token(String runtime) {
        CancelToken t = CANCEL_TOKENS.get(runtime);
        return t != null ? t : new CancelToken();
    }

    /** 没有在线地址时的真因（别再报「下载地址还没准备好」这种假原因：地址是故意留空的） */
    private static String noAddressReason(String runtime) {
        if ("ubuntu".equals(runtime)) {
            return "Ubuntu 环境暂时装不了：它的运行基座（proot）还没接好";
        }
        return "「" + label(runtime) + "」还没有在线安装地址，换本地安装试试";
    }

    /** 仓侧包 id：运行时叫 nodejs、仓里叫 node。查表前先经全仓唯一归一入口对齐，
     * 不管传进来的是 node 还是 nodejs 都落到仓侧键，别再出现两表键不一致查不到 */
    private static String pkgKey(String runtime) {
        String rt = LabManifest.normalizeRuntime(runtime);
        return "nodejs".equals(rt) ? "node" : rt;
    }

    private static final String REPO_RAW = "https://raw.githubusercontent.com/XingChenApp/xingchen-lab-assets/main/env/";
    private static final String REPO_JSD = "https://cdn.jsdelivr.net/gh/XingChenApp/xingchen-lab-assets@main/env/";

    static {
        // 整包：raw 直链
        DOWNLOAD_URLS.put("python", REPO_RAW + "env-python.zip");
        DOWNLOAD_URLS.put("mihomo", REPO_RAW + "env-mihomo.zip");
        DOWNLOAD_URLS.put("frp", REPO_RAW + "env-frp.zip");
        DOWNLOAD_URLS.put("aria2", REPO_RAW + "env-aria2.zip");
        DOWNLOAD_URLS.put("bun", REPO_RAW + "env-bun.zip");
        DOWNLOAD_URLS.put("ffmpeg", REPO_RAW + "env-ffmpeg.zip");
        // 分片包：填清单 JSON 地址（下载层识别 .json 走分片拼接＋校验）
        DOWNLOAD_URLS.put("node", REPO_RAW + "env-node.json");
        DOWNLOAD_URLS.put("php", REPO_RAW + "env-php.json");
        DOWNLOAD_URLS.put("java", REPO_RAW + "env-java.json");
        DOWNLOAD_URLS.put("alist", REPO_RAW + "env-alist.json");
        // ubuntu 留空：rootfs 过不了 bin/ 校验、proot 未接，填了是骗点击

        // 回填表必须与目录资产（lab_env_catalog.json）同值：仓里无版本名文件已被新版
        // 原名覆盖（python/mihomo/frp 大小已按公开仓实核），表停旧值时回退路 sha 永远
        // 验不过、版本号也与目录对不上（假更新提示）。.json 分片路（node/php/java/alist）
        // 的 sha 在清单里自带，本表不放其 sha。
        PKG_VERSION.put("python", "3.14.8-1");
        PKG_VERSION.put("node", "26.4.0-1");
        PKG_VERSION.put("php", "8.5.1-1");
        PKG_VERSION.put("mihomo", "1.19.32");
        PKG_VERSION.put("alist", "3.65.0");
        PKG_VERSION.put("frp", "0.71.1");
        PKG_VERSION.put("aria2", "1.37.0");
        PKG_VERSION.put("java", "17.0.20");
        PKG_VERSION.put("bun", "1.4.3");
        PKG_VERSION.put("ffmpeg", "9.0.2");

        PKG_SIZE.put("python", 14158025L);
        PKG_SIZE.put("node", 55876413L);
        PKG_SIZE.put("php", 49454027L);
        PKG_SIZE.put("mihomo", 20698996L);
        PKG_SIZE.put("alist", 42010903L);
        PKG_SIZE.put("frp", 12469827L);
        PKG_SIZE.put("aria2", 2481027L);
        PKG_SIZE.put("java", 66186060L);
        PKG_SIZE.put("bun", 39196506L);
        PKG_SIZE.put("ffmpeg", 42056568L);

        PKG_SHA256.put("python", "a9369193e2bd06936cac97047001caab22cc656bc5378e32e3082003f3ce5ce6");
        PKG_SHA256.put("mihomo", "5c89a3108406d264ad83d9c89e00c50701435e6ffe43025add4556a727884dd9");
        PKG_SHA256.put("frp", "1e86a57b73a0ccd5e8bcf20d3b16996a80dcd337e3805925d1008165171e5809");
        PKG_SHA256.put("aria2", "272ebf5c717a337ea86e27007ff5dbb07875993d2d4a20277622956641780580");
        PKG_SHA256.put("java", "38fb0699ce4ff746d302d8dc82985abf6c86384b7755827ff665f35414f4800f");
        PKG_SHA256.put("bun", "8ff4385d826a9d5a6c3f7efe93375e6586a1cd6127d012952ea992266de2da0d");
        PKG_SHA256.put("ffmpeg", "6118b3717cb1532644610db0a3c199eecad2e0ab26f301d9780e884df624e8e0");
    }

    /** jsDelivr 镜像（gh 端点单文件 ≤20MB 才吃得下）：raw 域名换过去即得 */
    private static String jsdelivrOf(String rawUrl) {
        if (rawUrl != null && rawUrl.startsWith(REPO_RAW)) {
            return REPO_JSD + rawUrl.substring(REPO_RAW.length());
        }
        return null;
    }

    /** 候选地址：≤20MB 的镜像优先、直链兜底；大的只走直链 */
    private static List<String> candidates(String url, long size) {
        List<String> out = new ArrayList<>();
        String jsd = jsdelivrOf(url);
        if (size > 0 && size <= 20L * 1024 * 1024 && jsd != null) {
            out.add(jsd);
            out.add(url);
        } else {
            out.add(url);
        }
        return out;
    }

    /**
     * 在线安装统一入口（新口径）：
     * 资产目录有条目 → 走目录制（架构/镜像/本地搜索）；否则走 DOWNLOAD_URLS 回填表
     * （.json 结尾＝分片清单：拉清单→逐分片下→拼接→sha256 校验→安装）。
     */
    public static void installOnline(Context ctx, String runtime, Listener listener) {
        runtime = LabManifest.normalizeRuntime(runtime);
        LabEnvCatalog.EnvEntry e = LabEnvCatalog.entry(ctx, runtime);
        if (e != null && !e.downloads.isEmpty()) {
            installFromCatalog(ctx, runtime, listener);
            return;
        }
        String url = DOWNLOAD_URLS.get(pkgKey(runtime));
        if (url == null || url.isEmpty()) {
            fail(listener, noAddressReason(runtime));
            return;
        }
        final String rt = runtime;
        if (!beginInstall(rt, listener)) return;
        final CancelToken token = token(rt);
        new Thread(() -> {
            File tmp = new File(ctx.getCacheDir(), "lab-rt-" + rt + "-" + System.currentTimeMillis() + ".zip");
            try {
                if (url.endsWith(".json")) {
                    downloadSharded(url, tmp, listener, token);
                } else {
                    downloadWhole(url, tmp, listener, rt, token);
                }
                installFromFile(ctx.getApplicationContext(), rt, tmp,
                        PKG_VERSION.get(pkgKey(rt)), listener, token);
            } catch (InstallCancelledException cx) {
                cancelled(listener);
            } catch (Exception ex) {
                // 下载与取消同时出头时以取消为准：失败很可能就是取消掐断的那一下
                if (token.isCancelled()) {
                    cancelled(listener);
                } else {
                    String m = ex.getMessage();
                    fail(listener, m == null || m.isEmpty() ? "环境包下不下来，换本地安装试试" : m);
                }
            } finally {
                // 成功装完的 tmp 原本就删；取消/失败留下的半截包一并清，别占缓存
                tmp.delete();
                endInstall(rt, token);
            }
        }).start();
    }

    /** 整包下载：镜像↔直链换着重试（默认 2 轮），每路下完都验 sha256，不对就换下一路 */
    private static void downloadWhole(String url, File dest, Listener listener, String runtime,
                                      CancelToken token) throws Exception {
        long size = PKG_SIZE.get(pkgKey(runtime)) == null ? 0 : PKG_SIZE.get(pkgKey(runtime));
        progress(listener, "正在下载环境包…");
        String sha = PKG_SHA256.get(pkgKey(runtime));
        downloadVerified(candidates(url, size), dest, sha, 2, listener, token);
    }

    /**
     * 跨源重试下载＋逐路 sha 校验（校验链落点 1）：
     * 某一路下完但 sha 不过，视同该路失败，直接换下一路，不在同一路死磕；
     * 全路跑满 rounds 轮都没拿到对的包才抛。sha 为空时退化成纯下载重试。
     */
    private static void downloadVerified(List<String> urls, File dest, String sha256,
                                         int rounds, Listener listener, CancelToken token) throws Exception {
        Exception last = null;
        boolean shaFailed = false;
        boolean needSha = sha256 != null && !sha256.isEmpty();
        for (int r = 0; r < rounds; r++) {
            for (String u : urls) {
                token.check();
                try {
                    download(u, dest, token);
                } catch (InstallCancelledException cx) {
                    throw cx;
                } catch (Exception e) {
                    last = e;
                    continue;
                }
                if (!needSha) return;
                boolean ok = false;
                try {
                    ok = sha256Of(dest, token).equalsIgnoreCase(sha256);
                } catch (InstallCancelledException cx) {
                    throw cx;
                } catch (Exception e) {
                    last = e;
                }
                if (ok) return;
                // 这一路下是下下来了，但包不对（旧缓存/错包），换下一路
                shaFailed = true;
                progress(listener, "这一路包不对，换一路再下…");
            }
            if (r + 1 < rounds) {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ignored) {
                }
            }
        }
        if (shaFailed) throw new Exception("包校验没过，重新下一次试试");
        if (last != null) throw last;
        throw new Exception("下不动");
    }

    /**
     * 跨源重试下载：同一批候选地址（镜像/直链/镜像站）跑满 rounds 轮，
     * 每轮内部按序换源，不在同一个源上死磕；轮间歇 400ms。
     * 全部用尽才抛最后一次的异常（调用方转人话＋报错弹窗）。
     */
    private static void downloadRetry(List<String> urls, File dest, int rounds, CancelToken token) throws Exception {
        Exception last = null;
        for (int r = 0; r < rounds; r++) {
            for (String u : urls) {
                token.check();
                try {
                    download(u, dest, token);
                    return;
                } catch (InstallCancelledException cx) {
                    throw cx;
                } catch (Exception e) {
                    last = e;
                }
            }
            if (r + 1 < rounds) {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ignored) {
                }
            }
        }
        if (last != null) throw last;
        throw new Exception("下不动");
    }

    /** 文本拉取（分片清单）：同样跨源重试 */
    private static String downloadTextRetry(String url, CancelToken token) throws Exception {
        List<String> urls = candidates(url, 1024);
        Exception last = null;
        for (int r = 0; r < 2; r++) {
            for (String u : urls) {
                token.check();
                try {
                    return downloadText(u, token);
                } catch (InstallCancelledException cx) {
                    throw cx;
                } catch (Exception e) {
                    last = e;
                }
            }
            if (r == 0) {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ignored) {
                }
            }
        }
        throw last == null ? new Exception("分片清单拉不到，换本地安装试试") : last;
    }

    /**
     * 分片下载：拉清单 JSON → 按 parts 逐个下（每分片 8MB，镜像优先）→ 拼接 → sha256 校验。
     * 进度按分片走（「正在下载（3/8）…」），不断了干等。
     */
    private static void downloadSharded(String manifestUrl, File dest, Listener listener,
                                        CancelToken token) throws Exception {
        // 清单本身也走镜像优先（JSON 很小），跨源重试
        String json;
        try {
            json = downloadTextRetry(manifestUrl, token);
        } catch (InstallCancelledException cx) {
            throw cx;
        } catch (Exception e) {
            throw new Exception("分片清单拉不到，换本地安装试试");
        }
        org.json.JSONObject o = new org.json.JSONObject(json);
        String sha = o.optString("sha256", "");
        org.json.JSONArray parts = o.optJSONArray("parts");
        if (parts == null || parts.length() == 0) throw new Exception("分片清单里没有分片表");
        int n = parts.length();
        File tmpDir = new File(dest.getParentFile(), dest.getName() + ".parts");
        tmpDir.mkdirs();
        try {
            for (int i = 0; i < n; i++) {
                token.check();
                String partUrl = resolvePart(manifestUrl, parts.optString(i, ""));
                if (partUrl.isEmpty()) throw new Exception("分片表第 " + (i + 1) + " 个地址是空的");
                if (listener != null) listener.onShard(i + 1, n);
                File partFile = new File(tmpDir, "p" + i);
                try {
                    // 每片都在镜像/直链之间换着重试 2 轮，单片失败才算整包失败
                    downloadRetry(candidates(partUrl, 8L * 1024 * 1024), partFile, 2, token);
                } catch (InstallCancelledException cx) {
                    throw cx;
                } catch (Exception pe) {
                    throw new Exception("第 " + (i + 1) + "/" + n + " 片下不下来，重试一次试试");
                }
            }
            // 拼接
            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
                byte[] buf = new byte[8192];
                for (int i = 0; i < n; i++) {
                    token.check();
                    File partFile = new File(tmpDir, "p" + i);
                    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(partFile))) {
                        int r;
                        while ((r = in.read(buf)) > 0) {
                            token.check();
                            out.write(buf, 0, r);
                        }
                    }
                }
            }
            if (!sha.isEmpty() && !sha256Of(dest, token).equalsIgnoreCase(sha)) {
                throw new Exception("包校验没过，重新下一次试试");
            }
        } finally {
            LabPackageInstaller.deleteDir(tmpDir);
        }
    }

    /**
     * 分片地址解析：仓清单里的 parts 是仓根相对路径（如 "env/chunks/node/p00"），
     * 不解析直接 new URL 就抛无协议异常（1.3.50 Node 在线装第一片必废的真根）。
     * http(s) 开头原样；否则按「清单地址去掉 /env/ 之后的部分＋part」拼仓根绝对地址；
     * 清单地址是 raw 还是 jsDelivr 域名两种都成立（都按原清单的根拼）。
     */
    private static String resolvePart(String manifestUrl, String part) {
        if (part == null) return "";
        part = part.trim();
        if (part.isEmpty()) return "";
        if (part.startsWith("http://") || part.startsWith("https://")) return part;
        int k = manifestUrl.indexOf("/env/");
        if (k > 0) return manifestUrl.substring(0, k + 1) + part;
        int s = manifestUrl.lastIndexOf('/');
        return s > 0 ? manifestUrl.substring(0, s + 1) + part : part;
    }

    private static String downloadText(String url, CancelToken token) throws Exception {
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("拉不到（" + code + "）");
            try (BufferedInputStream in = new BufferedInputStream(c.getInputStream());
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    token.check();
                    out.write(buf, 0, n);
                }
                return out.toString("UTF-8");
            }
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String sha256Of(File f, CancelToken token) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(f))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                token.check();
                md.update(buf, 0, n);
            }
        }
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public interface Listener {
        void onProgress(String text);

        void onOk();

        void onFail(String reason);

        /** 用户取消：半成品已清、守卫已释放，可直接再装。默认空实现，老实现方不覆写也不挂死 */
        default void onCancelled() {
        }

        /** 分片进度（默认实现转成人话，供安装中弹窗拼「第 N 片 / 共 M 片 · P%」） */
        default void onShard(int done, int total) {
            onProgress("正在下载（" + done + "/" + total + "）…");
        }
    }

    private static void cancelled(Listener l) {
        if (l != null) l.onCancelled();
    }

    private LabRuntimeManager() {
    }

    public static String label(String runtime) {
        if ("python".equals(runtime)) return "Python 环境";
        if ("nodejs".equals(runtime)) return "Node 环境";
        if ("php".equals(runtime)) return "PHP 环境";
        if ("mihomo".equals(runtime)) return "mihomo";
        if ("alist".equals(runtime)) return "alist";
        if ("frp".equals(runtime)) return "frp";
        if ("aria2".equals(runtime)) return "aria2";
        if ("ubuntu".equals(runtime)) return "Ubuntu 环境";
        if ("java".equals(runtime)) return "Java 环境";
        if ("bun".equals(runtime)) return "Bun 环境";
        if ("ffmpeg".equals(runtime)) return "ffmpeg";
        return runtime;
    }

    public static String desc(String runtime) {
        if ("python".equals(runtime)) return "跑脚本的底子，很多服务靠它";
        if ("nodejs".equals(runtime)) return "JS 服务的底子";
        if ("php".equals(runtime)) return "PHP 程序的底子";
        return "";
    }

    public static String iconText(String runtime) {
        if ("python".equals(runtime)) return "Py";
        if ("nodejs".equals(runtime)) return "Js";
        if ("php".equals(runtime)) return "PH";
        return "?";
    }

    public static boolean isInstalled(Context c, String runtime) {
        File bin = new File(LabPaths.runtimeDir(c, LabManifest.normalizeRuntime(runtime)), "bin");
        return bin.isDirectory() && bin.list() != null && bin.list().length > 0;
    }

    /** 从本地 zip 导入环境包（在线下载落盘后也走同一条安装管线） */
    public static void importFromUri(Context ctx, String runtime, Uri uri, Listener listener) {
        final String rt = LabManifest.normalizeRuntime(runtime);
        if (!beginInstall(rt, listener)) return;
        final CancelToken token = token(rt);
        new Thread(() -> {
            File tmp = new File(ctx.getCacheDir(), "lab-rt-" + rt + "-" + System.currentTimeMillis() + ".zip");
            try {
                copyUri(ctx, uri, tmp, token);
                // 本地包文件名合仓包约定时回填版本号，免得登记留空、版本行没数
                String version = versionFromPackageName(displayName(ctx, uri), rt);
                installFromFile(ctx.getApplicationContext(), rt, tmp, version, listener, token);
            } catch (InstallCancelledException cx) {
                cancelled(listener);
            } catch (Exception e) {
                if (token.isCancelled()) {
                    cancelled(listener);
                } else {
                    String m = e.getMessage();
                    fail(listener, m == null || m.isEmpty() ? "环境装不上，换一个压缩包试试" : m);
                }
            } finally {
                tmp.delete();
                endInstall(rt, token);
            }
        }).start();
    }

    /** 在线安装：从下载地址拉包，落盘后走与本地安装同一条管线（跨源重试） */
    public static void importFromUrl(Context ctx, String runtime, String url, Listener listener) {
        final String rt = LabManifest.normalizeRuntime(runtime);
        if (!beginInstall(rt, listener)) return;
        final CancelToken token = token(rt);
        new Thread(() -> {
            File tmp = new File(ctx.getCacheDir(), "lab-rt-" + rt + "-" + System.currentTimeMillis() + ".zip");
            try {
                progress(listener, "正在下载环境包…");
                downloadRetry(candidates(url, PKG_SIZE.get(pkgKey(rt)) == null
                        ? 0 : PKG_SIZE.get(pkgKey(rt))), tmp, 2, token);
                // 下载地址末段合仓包约定时回填版本号（同本地导入口径）
                installFromFile(ctx.getApplicationContext(), rt, tmp, versionFromPackageName(url, rt), listener, token);
            } catch (InstallCancelledException cx) {
                cancelled(listener);
            } catch (Exception e) {
                if (token.isCancelled()) {
                    cancelled(listener);
                } else {
                    String m = e.getMessage();
                    fail(listener, m == null || m.isEmpty() ? "环境包下不下来，换本地安装试试" : m);
                }
            } finally {
                tmp.delete();
                endInstall(rt, token);
            }
        }).start();
    }

    /** 环境包下载地址（公开仓未建，先留空；建好后填这里，全 App 只这一处） */
    public static String downloadUrl(String runtime) {
        return DOWNLOAD_URLS.get(pkgKey(runtime));
    }

    /** 从仓包文件名按约定 env-包id-版本号.zip 解析版本号；解析不出返回空串（登记留空）。 */
    private static String versionFromPackageName(String name, String runtime) {
        if (name == null) return "";
        String base = name;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) base = base.substring(slash + 1);
        String prefix = "env-" + pkgKey(runtime) + "-";
        if (!base.startsWith(prefix) || !base.endsWith(".zip")) return "";
        String v = base.substring(prefix.length(), base.length() - 4);
        return v.isEmpty() || !Character.isDigit(v.charAt(0)) ? "" : v;
    }

    /** 本地导入时取源文件显示名（解析版本号用）；取不到返回空串。 */
    private static String displayName(Context ctx, Uri uri) {
        try (android.database.Cursor cur = ctx.getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                int idx = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cur.getString(idx);
                    return name == null ? "" : name;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /** 安装管线唯一入口：解压 → 755 → bin 校验 → 软链 → 登记注册表。在线/本地都走这里。 */
    private static void installFromFile(Context ctx, String runtime, File zip, Listener listener,
                                        CancelToken token) {
        installFromFile(ctx, runtime, zip, "", listener, token);
    }

    private static void installFromFile(Context ctx, String runtime, File zip, String version,
                                        Listener listener, CancelToken token) {
        File dest = LabPaths.runtimeDir(ctx, runtime);
        // 旧目录动过没有：取消若踩在删旧目录之前，旧安装还是好的，收口时不许连它一起删
        boolean destTouched = false;
        // 提交点（登记落笔）过没过：过了就算装成，此后的失败不算半成品、不许删
        boolean committed = false;
        try {
            progress(listener, "正在准备环境…");
            token.check();
            // 重装先停靠这个环境跑着的服务再删目录（与卸载同口径）：
            // 不停就删，进程脚下的 bin/lib 被抽走，服务半死不活
            stopRuntimeUsers(ctx, runtime);
            LabPackageInstaller.deleteDir(dest);
            destTouched = true;
            dest.mkdirs();
            unzip(zip, dest, listener, token);
            chmod755(dest);
            token.check();
            if (!isInstalled(ctx, runtime)) throw new Exception("环境包里没有 bin，换一个压缩包试试");
            symlinkShared(ctx, runtime, dest);
            token.check();
            LabRegistry.Runtime r = new LabRegistry.Runtime();
            r.name = runtime;
            r.version = version == null ? "" : version;
            r.installTime = System.currentTimeMillis();
            LabRegistry.get(ctx).upsertRuntime(ctx, r);
            committed = true;
            // 登记落笔即算装成：此后不再查取消旗——取消踩在这个窗口就按装成收口，
            // 否则会出现注册表有登记、目录却被取消清理删掉的半截状态
            ok(listener);
        } catch (InstallCancelledException cx) {
            // 取消收口：已经动手替换了才清——半解压目录整个删掉，bin 半成品留着
            // 会被 isInstalled 误判成已装；登记在解压后才写，注册表还没动，无需回滚
            if (destTouched && !committed) LabPackageInstaller.deleteDir(dest);
            cancelled(listener);
        } catch (Exception e) {
            if (token.isCancelled()) {
                if (destTouched && !committed) LabPackageInstaller.deleteDir(dest);
                cancelled(listener);
            } else {
                // 失败收口与取消路径对齐：未过提交点的半成品同样整删，
                // 否则残缺 bin 会被 isInstalled 误判已装、跳过重装往下跑；
                // 已过提交点的失败不算半成品，目录留着
                if (destTouched && !committed) LabPackageInstaller.deleteDir(dest);
                String m = e.getMessage();
                fail(listener, m == null || m.isEmpty() ? "环境装不上，换一个压缩包试试" : m);
            }
        }
    }

    /**
     * 共享库软链（上游 LabEnv.symlinkShared 口径的轻量版）：
     * 把包内各级的共享库软链到 runtimes/<name>/lib/，进程 LD_LIBRARY_PATH 已指向它。
     * 收集口径必须含带版本号的 SONAME 文件（libz.so.1、libcrypto.so.3 一类）——ELF 的
     * DT_NEEDED 记的正是这些名字，只认 .so 结尾会把它们整批漏掉。上游是 bin 内文件
     * 不过滤全链，星辰这边按文件名过滤就必须把版本化名字放进来。
     * proot 那套重型软链（ubuntu rootfs）等 proot 钩子一起做，见报告。
     */
    private static void symlinkShared(Context ctx, String runtime, File dest) {
        try {
            File lib = libDir(ctx, runtime);
            lib.mkdirs();
            List<File> sos = new ArrayList<>();
            collectSo(dest, sos, 0);
            for (File so : sos) {
                File link = new File(lib, so.getName());
                // 源文件就在落点目录里（包自带 lib/ 与落点同为 runtimes/<name>/lib 时）：
                // 自链没有意义；落点已有真实文件（非软链）时不许覆盖，那是包自己的库。
                if (link.getAbsolutePath().equals(so.getAbsolutePath())) continue;
                if (link.exists() && !java.nio.file.Files.isSymbolicLink(link.toPath())) continue;
                if (link.exists()) link.delete();
                new ProcessBuilder("ln", "-sf", so.getAbsolutePath(), link.getAbsolutePath())
                        .start().waitFor();
            }
        } catch (Exception ignored) {
        }
    }

    private static void collectSo(File dir, List<File> out, int depth) {
        if (depth > 4 || dir == null || !dir.isDirectory()) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) collectSo(k, out, depth + 1);
            else if (isSharedLib(k.getName())) out.add(k);
        }
    }

    /** 共享库文件名：libfoo.so 本名与 libfoo.so.1 / libfoo.so.1.3.2 版本化 SONAME 都算。 */
    static boolean isSharedLib(String name) {
        if (name.endsWith(".so")) return true;
        return name.matches(".+\\.so\\.\\d+(\\.\\d+)*");
    }

    private static String fileNameOf(String url) {
        String u = url.split("\\?")[0];
        int i = u.lastIndexOf('/');
        return i >= 0 ? u.substring(i + 1) : "env.zip";
    }

    private static void copyFile(File src, File dest, CancelToken token) throws Exception {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(src));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                token.check();
                out.write(buf, 0, n);
            }
        }
    }

    /** 环境 bin 目录：给服务进程拼 PATH 用 */
    public static File binDir(Context c, String runtime) {
        return new File(LabPaths.runtimeDir(c, LabManifest.normalizeRuntime(runtime)), "bin");
    }

    /** 环境共享库目录（软链落点，供 LD_LIBRARY_PATH） */
    public static File libDir(Context c, String runtime) {
        return new File(LabPaths.runtimeDir(c, LabManifest.normalizeRuntime(runtime)), "lib");
    }

    // ---------------- 目录制安装（架构/镜像/本地自动搜索/版本） ----------------

    /**
     * 从公开仓目录安装（新口径）：
     * 架构匹配 → 本地包自动搜索（常见目录找同名包）→ 主地址/镜像依次下载 → 同一管线安装。
     * 目录没到或该环境没条目时，回退旧 downloadUrl 路径。
     */
    public static void installFromCatalog(Context ctx, String runtime, Listener listener) {
        runtime = LabManifest.normalizeRuntime(runtime);
        LabEnvCatalog.EnvEntry entry = LabEnvCatalog.entry(ctx, runtime);
        if (entry == null || entry.downloads.isEmpty()) {
            // 目录没到：走旧路（下载地址故意留空时如实说真因，不报假原因）
            String url = downloadUrl(runtime);
            if (url == null || url.isEmpty()) {
                fail(listener, noAddressReason(runtime));
                return;
            }
            importFromUrl(ctx, runtime, url, listener);
            return;
        }
        LabEnvCatalog.Download dl = LabEnvCatalog.chooseDownload(entry);
        if (dl == null) {
            fail(listener, "这个环境没有你手机架构的包");
            return;
        }
        final String rt2 = runtime;
        if (!beginInstall(rt2, listener)) return;
        final CancelToken token = token(rt2);
        new Thread(() -> {
            // 文件名：目录给了 file 字段就用它（版本化新名），没给从 URL 尾段取
            String fileName = !dl.file.isEmpty() ? dl.file : fileNameOf(dl.url);
            File tmp = new File(ctx.getCacheDir(), "lab-rt-" + rt2 + "-" + System.currentTimeMillis() + ".zip");
            try {
                if (dl.url.endsWith(".json")) {
                    // 分片清单型：走分片拼接＋校验
                    downloadSharded(dl.url, tmp, listener, token);
                } else {
                    File local = verifyLocalArchive(ctx, fileName, dl.sha256, listener, token);
                    if (local != null) {
                        progress(listener, "找到本地包，直接装…");
                        copyFile(local, tmp, token);
                    } else {
                        progress(listener, "正在下载环境包…");
                        // ≤20MB 镜像优先、直链兜底；大的只走直链；2 轮跨源重试；
                        // 某一路下完 sha 不对就换下一路，全路不对才报错
                        List<String> urls = new ArrayList<>();
                        String jsd = jsdelivrOf(dl.url);
                        if (dl.size > 0 && dl.size <= 20L * 1024 * 1024 && jsd != null) {
                            urls.add(jsd);
                        }
                        urls.add(dl.url);
                        urls.addAll(dl.mirrors);
                        downloadVerified(urls, tmp, dl.sha256, 2, listener, token);
                    }
                }
                // 整包 sha 校验（目录里有就验，没有不硬拦；最后一道保险）
                if (!dl.sha256.isEmpty() && !sha256Of(tmp, token).equalsIgnoreCase(dl.sha256)) {
                    throw new Exception("包校验没过，重新下一次试试");
                }
                installFromFile(ctx.getApplicationContext(), rt2, tmp, entry.version, listener, token);
            } catch (InstallCancelledException cx) {
                cancelled(listener);
            } catch (Exception e) {
                if (token.isCancelled()) {
                    cancelled(listener);
                } else {
                    String m = e.getMessage();
                    fail(listener, m == null || m.isEmpty() ? "环境包下不下来，换本地安装试试" : m);
                }
            } finally {
                tmp.delete();
                endInstall(rt2, token);
            }
        }).start();
    }

    /** 本地包自动搜索（上游 findLocalArchive 口径，星辰目录版）：常见目录找同名包 */
    public static File findLocalArchive(Context c, String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        List<File> dirs = new ArrayList<>();
        dirs.add(new File(LabPaths.root(c), "EnvFiles"));
        dirs.add(new File(LabPaths.root(c), "EnvFiles/二进制"));
        dirs.add(new File(LabPaths.root(c), "Download"));
        dirs.add(new File("/storage/emulated/0/Download"));
        dirs.add(new File("/storage/emulated/0/EnvFiles"));
        dirs.add(new File("/storage/emulated/0/EnvFiles/二进制"));
        dirs.add(LabPaths.root(c));
        for (File dir : dirs) {
            File f = new File(dir, fileName);
            if (f.isFile()) return f;
        }
        return null;
    }

    /**
     * 本地包先验 sha 再用（校验链落点 3）：
     * 找到同名包后先比 sha256——对得上才用；对不上就删掉（旧包/错包留着只会再炸），
     * 返回 null 走下载。目录里没给 sha 时无法验，直接用。
     */
    private static File verifyLocalArchive(Context ctx, String fileName, String sha256, Listener listener,
                                           CancelToken token) throws InstallCancelledException {
        File local = findLocalArchive(ctx, fileName);
        if (local == null) return null;
        if (sha256 != null && !sha256.isEmpty()) {
            boolean ok = false;
            try {
                ok = sha256Of(local, token).equalsIgnoreCase(sha256);
            } catch (InstallCancelledException cx) {
                throw cx;
            } catch (Exception ignored) {
            }
            if (!ok) {
                progress(listener, "本地包对不上新版，删掉重下…");
                local.delete();
                return null;
            }
        }
        return local;
    }

    /**
     * 仓包命名约定（校验链落点 2）：大改包必须换新文件名，禁止原名覆盖——
     * 整包 env-包id-版本号.zip（如 env-python-3.14.8-1.zip），
     * 分片清单 env-包id-版本号.json；版本号用目录 version 原样。
     * 壳子从目录的 file/url 读名；仓侧动作见 lab-verify-chain-fix-report.md。
     */
    public static String packageFileName(String pkgId, String version) {
        return "env-" + pkgKey(pkgId) + "-" + (version == null ? "" : version) + ".zip";
    }

    /** 已装版本（环境页版本行与 Node 信息面展示用） */
    public static String installedVersion(Context c, String runtime) {
        LabRegistry.Runtime r = LabRegistry.get(c).runtime(LabManifest.normalizeRuntime(runtime));
        return r == null || r.version == null ? "" : r.version;
    }

    /** 目录里的版本号（Node 信息面可用版本展示用） */
    public static String catalogVersion(Context c, String runtime) {
        LabEnvCatalog.EnvEntry e = LabEnvCatalog.entry(c, runtime);
        return e == null ? "" : e.version;
    }

    /** 停掉依赖某环境的运行中服务（卸载/重装环境删目录前用） */
    private static void stopRuntimeUsers(Context ctx, String runtime) {
        for (LabRegistry.Svc s : LabRegistry.get(ctx).services(runtime)) {
            try {
                LabRunner.stopPackage(ctx, s.id);
            } catch (Exception ignored) {
            }
        }
    }

    /** 卸载环境：先停掉依赖它的运行中服务，再删环境目录、注销登记 */
    public static void uninstall(Context ctx, String runtime) {
        runtime = LabManifest.normalizeRuntime(runtime);
        stopRuntimeUsers(ctx, runtime);
        LabPackageInstaller.deleteDir(LabPaths.runtimeDir(ctx, runtime));
        LabRegistry.get(ctx).removeRuntime(ctx, runtime);
    }

    private static void download(String url, File dest, CancelToken token) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(120000);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("下不动这个地址（" + code + "）");
            try (BufferedInputStream in = new BufferedInputStream(c.getInputStream());
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    token.check();
                    out.write(buf, 0, n);
                }
            }
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static void unzip(File zip, File dest, Listener listener, CancelToken token) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            long total = 0;
            while ((entry = zin.getNextEntry()) != null) {
                token.check();
                if (entry.isDirectory()) continue;
                File out = new File(dest, entry.getName());
                if (!out.getCanonicalPath().startsWith(dest.getCanonicalPath() + File.separator)) continue;
                out.getParentFile().mkdirs();
                try (BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(out))) {
                    int n;
                    while ((n = zin.read(buf)) > 0) {
                        token.check();
                        bout.write(buf, 0, n);
                        total += n;
                    }
                }
                zin.closeEntry();
                if (total % (8 * 1024 * 1024) < 8192) progress(listener, "正在准备环境…");
            }
        }
    }

    private static void chmod755(File dir) {
        try {
            new ProcessBuilder("chmod", "-R", "755", dir.getAbsolutePath()).start().waitFor();
        } catch (Exception ignored) {
        }
    }

    private static void copyUri(Context ctx, Uri uri, File dest, CancelToken token) throws Exception {
        try (BufferedInputStream in = new BufferedInputStream(ctx.getContentResolver().openInputStream(uri));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                token.check();
                out.write(buf, 0, n);
            }
        }
    }

    private static void progress(Listener l, String s) {
        if (l != null) l.onProgress(s);
    }

    private static void ok(Listener l) {
        if (l != null) l.onOk();
    }

    private static void fail(Listener l, String s) {
        if (l != null) l.onFail(s);
    }
}
