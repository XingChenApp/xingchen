package com.fongmi.android.tv.ui.lab;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * .lab 包安装链：选文件（或 .lab 文件点开拉起）→ 解析校验 lab.json →
 * 解压到 files/lab/packages/<id>/ → 登记注册表 → 首页出卡。
 * 校验失败只报人话，不抛技术栈。
 */
public final class LabPackageInstaller {

    public interface Listener {
        void onProgress(String text);

        void onOk(LabRegistry.Svc pkg);

        void onFail(String reason);

        /** 用户取消：半成品已清，可直接再装。默认转一句失败提示兜底防挂死，弹窗调用方覆写走取消收口 */
        default void onCancelled() {
            onFail("安装已取消");
        }
    }

    /** 取消信号：执行体在分块循环与段间检查点 check()，命中就以此异常 unwind 到收口处（与 LabRuntimeManager 同口径） */
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

    /**
     * 一次装包的取消句柄：入口方法按次新建并返回，弹窗取消钮拿着它点取消。
     * 与该次安装一一对应：重试是新句柄，上一次迟到的取消点不到新令牌，不会误伤下一次安装。
     * 本次安装正在自动装环境时，取消连环境那条腿一起收（经 LabRuntimeManager 的令牌）。
     */
    public static final class InstallHandle {
        private final CancelToken token = new CancelToken();
        /** 正在飞的环境腿运行时（ensureRuntime 记上、收口时清掉）；空表示当前不在装环境 */
        private volatile String envRuntime;

        public void cancel() {
            token.cancel();
            String rt = envRuntime;
            if (rt != null) LabRuntimeManager.cancelInstall(rt);
        }
    }

    /** 正在安装中的包（防重复：同包从两个入口同时装，同一 dest 并发删/解/写会装出
     *  半成品、注册表互相覆盖；照 LabRuntimeManager 环境侧 INSTALLING 的既有口径，
     *  包侧按包 id 守一手：同包已有安装在飞时，后到者直接走失败终态） */
    private static final java.util.Set<String> INSTALLING =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private static boolean beginPackageInstall(String id) {
        // id 为空的包本来就过不了说明校验、走不到写 dest，这里放行不拦，口径不另变
        return id == null || id.isEmpty() || INSTALLING.add(id);
    }

    /** 释放本次安装的守卫（按持有者只放一次）：已放过的再调是空操作，
     *  不会误删随后同包新安装刚登记的守卫 */
    private static void releasePackageInstall(String[] guard) {
        if (guard == null) return;
        String id;
        synchronized (guard) {
            id = guard[0];
            if (id == null) return;
            guard[0] = null;
        }
        INSTALLING.remove(id);
    }

    private LabPackageInstaller() {
    }

    /** 从 content/file Uri 安装 */
    public static InstallHandle installFromUri(Context ctx, Uri uri, Listener listener) {
        return installFromUri(ctx, uri, null, listener);
    }

    /**
     * 从 content/file Uri 安装：defaultRuntime（环境页「＋」进来时传当前环境）只在
     * 包自己没声明运行时（runtime 为 none）时用，声明了的听声明。
     */
    public static InstallHandle installFromUri(Context ctx, Uri uri, String defaultRuntime, Listener listener) {
        final InstallHandle handle = new InstallHandle();
        final CancelToken token = handle.token;
        new Thread(() -> {
            File tmp = new File(ctx.getCacheDir(), "lab-install-" + System.currentTimeMillis() + ".lab");
            try {
                copyStream(ctx.getContentResolver().openInputStream(uri), tmp, token);
                // installFromFile 是另起线程异步跑的，临时文件只能在终态回调里删，
                // 当场删会跟安装线程抢跑，安装那边打开就是 ENOENT。
                Listener wrapped = new Listener() {
                    @Override
                    public void onProgress(String text) {
                        listener.onProgress(text);
                    }

                    @Override
                    public void onOk(LabRegistry.Svc pkg) {
                        tmp.delete();
                        listener.onOk(pkg);
                    }

                    @Override
                    public void onFail(String reason) {
                        tmp.delete();
                        listener.onFail(reason);
                    }

                    @Override
                    public void onCancelled() {
                        tmp.delete();
                        listener.onCancelled();
                    }
                };
                doInstallFromFile(ctx.getApplicationContext(), tmp, defaultRuntime, wrapped, handle);
            } catch (InstallCancelledException cx) {
                tmp.delete();
                cancelled(listener);
            } catch (Exception e) {
                tmp.delete();
                // 拷贝与取消同时出头时以取消为准：失败很可能就是取消掐断的那一下
                if (token.isCancelled()) {
                    cancelled(listener);
                } else {
                    fail(listener, "读不到这个包文件：" + human(e));
                }
            }
        }).start();
        return handle;
    }

    /** 从本地 zip 文件安装（.lab） */
    public static InstallHandle installFromFile(Context ctx, File zip, Listener listener) {
        return installFromFile(ctx, zip, null, listener);
    }

    /** 同上，带默认运行时（包没声明时才用）；声明的环境没装会自动先装环境 */
    public static InstallHandle installFromFile(Context ctx, File zip, String defaultRuntime, Listener listener) {
        InstallHandle handle = new InstallHandle();
        doInstallFromFile(ctx, zip, defaultRuntime, listener, handle);
        return handle;
    }

    private static void doInstallFromFile(Context ctx, File zip, String defaultRuntime,
                                          Listener listener, InstallHandle handle) {
        final CancelToken token = handle.token;
        new Thread(() -> {
            final String[] guard = new String[1];
            try {
                progress(listener, "正在读包说明…");
                LabManifest manifest = loadManifest(zip);
                token.check();
                if (!beginPackageInstall(manifest.id)) {
                    fail(listener, "「" + (manifest.name == null || manifest.name.isEmpty()
                            ? manifest.id : manifest.name) + "」正在安装中，等它装完再试");
                    return;
                }
                guard[0] = manifest.id;
                final boolean mergeFiles = hasLabJson(zip);
                ensureRuntime(ctx.getApplicationContext(), manifest.runtime, defaultRuntime, handle,
                        new RuntimeEnsure() {
                            @Override
                            public void onEnvProgress(String runtime, String text) {
                                progress(listener, "正在装「"
                                        + LabRuntimeManager.label(runtime) + "」：" + text);
                            }

                            @Override
                            public void onReady(String runtime, String hint) {
                                File dest = LabPaths.packageDir(ctx, manifest.id);
                                // 暂存安装：全程只写同级暂存目录，失败/取消只删暂存——
                                // 旧包目录与注册表登记都不动，重装失败旧包原样可用；
                                // 全部落盘＋检查点过完才在提交点停进程、换目录、登记。
                                // （旧实现 unzip 进门先删旧目录：重装一失败，旧包已毁、
                                // 新包半截、注册表还留旧登记显示已装，点开是残缺文件。）
                                File staging = stagingDir(dest);
                                try {
                                    token.check();
                                    if (hint != null) progress(listener, hint);
                                    manifest.runtime = runtime;
                                    progress(listener, "正在解压…");
                                    unzip(zip, staging, mergeFiles, token);
                                    // 生成型说明（如整目录 Node 项目按 package.json 生成）包里没有
                                    // lab.json：把说明落进包根，运行链读包说明才找得到
                                    if (!new File(staging, "lab.json").exists()) {
                                        writeJson(new File(staging, "lab.json"), manifest);
                                    }
                                    // 本地 Node bundle（zip 路）的启动命令是宿主生成的、引用
                                    // 宿主生成的引导脚本，但包文件来自 zip 本身、引导脚本
                                    // 不在包里：在包根补写一份（与上面补写生成型 lab.json
                                    // 同一落点）。命令与引导脚本是配套生成的，包里万一
                                    // 有同名文件也以宿主这份为准，直接覆盖
                                    if (manifestReferencesNodeBoot(manifest)) {
                                        try (FileOutputStream out = new FileOutputStream(
                                                new File(staging, LabImportCompat.NODE_BOOT_NAME))) {
                                            out.write(LabImportCompat.nodeBootBytes());
                                        }
                                    }
                                    token.check();
                                    chmod755(staging);
                                    token.check();
                                    // 提交点：先停这个包的全部进程（与卸载同口径，不停就换
                                    // 目录等于抽走进程脚下文件），再换目录、登记落笔；
                                    // 登记落笔即算装成，此后不再查取消旗
                                    try {
                                        LabRunner.stopPackage(ctx, manifest.id);
                                    } catch (Exception ignored) {
                                    }
                                    swapIntoPlace(staging, dest);
                                    createDeclaredDirs(ctx, dest, manifest.mkdir);
                                    LabRegistry.Svc pkg = new LabRegistry.Svc();
                                    pkg.id = manifest.id;
                                    pkg.name = manifest.name;
                                    pkg.desc = manifest.desc;
                                    pkg.icon = manifest.icon;
                                    pkg.version = manifest.version;
                                    pkg.runtime = manifest.runtime;
                                    pkg.autoStartDefault = manifest.autoStart;
                                    pkg.installTime = System.currentTimeMillis();
                                    // 分组与兼容导入路同口径带上：此前原生 zip/.lab 路
                                    // 漏落，清单里有 group、注册表恒为空串，登记不全
                                    pkg.group = manifest.group == null ? "" : manifest.group;
                                    LabRegistry.get(ctx).upsertSvc(ctx, pkg);
                                    releasePackageInstall(guard);
                                    ok(listener, pkg);
                                } catch (InstallCancelledException cx) {
                                    deleteDir(staging);
                                    releasePackageInstall(guard);
                                    cancelled(listener);
                                } catch (Exception e) {
                                    releasePackageInstall(guard);
                                    if (token.isCancelled()) {
                                        deleteDir(staging);
                                        cancelled(listener);
                                    } else {
                                        fail(listener, human(e));
                                    }
                                }
                            }

                            @Override
                            public void onFail(String reason) {
                                releasePackageInstall(guard);
                                fail(listener, reason);
                            }

                            @Override
                            public void onCancelled() {
                                // 环境腿被取消 = 本次装包取消，收口到取消终态
                                releasePackageInstall(guard);
                                cancelled(listener);
                            }
                        });
            } catch (InstallCancelledException cx) {
                releasePackageInstall(guard);
                cancelled(listener);
            } catch (Exception e) {
                releasePackageInstall(guard);
                if (token.isCancelled()) {
                    cancelled(listener);
                } else {
                    fail(listener, human(e));
                }
            }
        }).start();
    }

    /** 卸载：先停这个包的全部进程（服务＋命令＋终端），再删目录、注销 */
    public static void uninstall(Context ctx, String id) {
        try {
            LabRunner.stopPackage(ctx, id);
        } catch (Exception ignored) {
        }
        deleteDir(LabPaths.packageDir(ctx, id));
        LabRegistry.get(ctx).removeSvc(ctx, id);
    }

    /** 环境自检回调：导入/添加服务时读包声明的运行时 */
    public interface RuntimeEnsure {
        /** 需要的环境没装、正在自动走在线安装：text 是安装进度人话 */
        void onEnvProgress(String runtime, String text);

        /** 环境就绪：runtime 为最终运行时；hint 为兜底提示（可空，调 progress 透给用户） */
        void onReady(String runtime, String hint);

        void onFail(String reason);

        /** 环境腿被用户取消：整次装包按取消收口（不是失败）。默认转 onFail 兜底，老实现方不覆写也不挂死 */
        default void onCancelled() {
            onFail("安装已取消");
        }
    }

    /**
     * 读包里声明的运行时：该环境没装就自动走在线安装（调用方弹安装中弹窗），
     * 装好服务直接就位能跑。声明缺失/认不出来才落回 defaultRuntime（环境页当前环境），
     * 并给一句人话提示；连兜底都没有就按 "none" 走老路。
     */
    public static void ensureRuntime(Context ctx, String runtime, String defaultRuntime, RuntimeEnsure cb) {
        ensureRuntime(ctx, runtime, defaultRuntime, new InstallHandle(), cb);
    }

    private static void ensureRuntime(Context ctx, String runtime, String defaultRuntime,
                                      InstallHandle handle, RuntimeEnsure cb) {
        final Context app = ctx.getApplicationContext();
        // 入口先归一（node→nodejs，全仓唯一入口在 LabManifest）：不归一会被当未声明，
        // 静默改投兜底环境——包明明写了 Node 却跑错环境
        String rt = LabManifest.normalizeRuntime(runtime);
        String hint = null;
        if (rt.isEmpty() || "none".equals(rt) || !LabRuntimeManager.RUNTIMES.contains(rt)) {
            String def = LabManifest.normalizeRuntime(defaultRuntime);
            if (!"none".equals(def) && LabRuntimeManager.RUNTIMES.contains(def)) {
                hint = "包里没写要什么环境，先放在「" + LabRuntimeManager.label(def) + "」里";
                rt = def;
            } else {
                cb.onReady("none", null);
                return;
            }
        }
        final String need = rt;
        final String readyHint = hint;
        if (LabRuntimeManager.isInstalled(app, need)) {
            cb.onReady(need, readyHint);
            return;
        }
        // 环境腿在飞期间把运行时记到句柄上：装包侧点取消时连这条腿一起收；
        // 收口回调先清掉再往下传，腿一落地句柄就不再指着它（防误伤随后无关的同名环境安装）
        handle.envRuntime = need;
        LabRuntimeManager.installOnline(app, need, new LabRuntimeManager.Listener() {
            @Override
            public void onProgress(String text) {
                cb.onEnvProgress(need, text);
            }

            @Override
            public void onOk() {
                handle.envRuntime = null;
                cb.onReady(need, readyHint);
            }

            @Override
            public void onFail(String reason) {
                handle.envRuntime = null;
                cb.onFail("「" + LabRuntimeManager.label(need) + "」没装上：" + reason);
            }

            @Override
            public void onCancelled() {
                handle.envRuntime = null;
                // 环境腿被取消必须收口到这里，否则装包链永远等不到 onReady/onFail
                cb.onCancelled();
            }
        });
        // 取消若赶在环境腿开跑之前就点了：那会儿环境令牌还不存在、点不到，
        // 这时它已经建好，补点一次，别让这条腿白装一整场（装完也会在就绪检查点收口）
        if (handle.token.isCancelled()) LabRuntimeManager.cancelInstall(need);
    }

    /** 兼容导入（默影视命令 / Termux 清单 / 裸脚本 / lab.json 粘贴）：归一后的条目直接落盘 */
    public interface CompatListener {
        void onProgress(String text);

        void onDone(int count);

        void onFail(String reason);

        /** 用户取消：已装成的条目照算，正在装的那条半成品已清。默认转一句失败提示兜底防挂死 */
        default void onCancelled() {
            onFail("安装已取消");
        }
    }

    public static InstallHandle installCompat(Context ctx, java.util.List<LabImportCompat.ImportItem> items, CompatListener listener) {
        return installCompat(ctx, items, null, listener);
    }

    /** 同上，带默认运行时（条目没声明时才用）；每条先保证环境就绪再落盘 */
    public static InstallHandle installCompat(Context ctx, java.util.List<LabImportCompat.ImportItem> items, String defaultRuntime, CompatListener listener) {
        InstallHandle handle = new InstallHandle();
        new Thread(() -> installCompatNext(ctx.getApplicationContext(), items, 0, defaultRuntime, listener, 0, handle)).start();
        return handle;
    }

    /** 逐条链式安装：前一条的环境没装就先自动装好，再装下一条 */
    private static void installCompatNext(Context ctx, java.util.List<LabImportCompat.ImportItem> items,
                                          int idx, String defaultRuntime, CompatListener listener, int okCount,
                                          InstallHandle handle) {
        if (idx >= items.size()) {
            if (listener != null) listener.onDone(okCount);
            return;
        }
        // 条目之间查旗：上一条登记落笔后取消赶到，已装成的照算（提交点已过），剩下的不再装
        if (handle.token.isCancelled()) {
            cancelled2(listener);
            return;
        }
        LabImportCompat.ImportItem it = items.get(idx);
        progress2(listener, "正在装：" + it.name + "…");
        final LabManifest m = LabImportCompat.toManifest(it);
        final String[] guard = new String[1];
        if (!beginPackageInstall(m.id)) {
            if (listener != null) listener.onFail("「" + it.name + "」正在安装中，等它装完再试");
            return;
        }
        guard[0] = m.id;
        try {
        ensureRuntime(ctx, m.runtime, defaultRuntime, handle, new RuntimeEnsure() {
            @Override
            public void onEnvProgress(String runtime, String text) {
                progress2(listener, "正在装「" + LabRuntimeManager.label(runtime) + "」：" + text);
            }

            @Override
            public void onReady(String runtime, String hint) {
                File dest = LabPaths.packageDir(ctx, m.id);
                // 暂存安装（与 .lab 链同口径）：本条全程只写同级暂存目录，
                // 失败/取消只删暂存，旧包目录与登记不动；提交点才停进程、换目录、登记
                File staging = stagingDir(dest);
                try {
                    handle.token.check();
                    if (hint != null) progress2(listener, hint);
                    m.runtime = runtime;
                    deleteDir(staging);
                    staging.mkdirs();
                    writeJson(new File(staging, "lab.json"), m);
                    handle.token.check();
                    if (it.assetBytes != null && it.assetBytes.length > 0
                            && it.assetName != null && !it.assetName.isEmpty()) {
                        File f = new File(staging, it.assetName);
                        if (f.getCanonicalPath().startsWith(staging.getCanonicalPath() + File.separator)) {
                            try (FileOutputStream out = new FileOutputStream(f)) {
                                out.write(it.assetBytes);
                            }
                        }
                    }
                    handle.token.check();
                    // 7z 离线包 binary/ 条目：相对路径落进包目录（同上，canonical 越界拒绝）
                    if (it.extraFiles != null && !it.extraFiles.isEmpty()) {
                        for (java.util.Map.Entry<String, byte[]> fe : it.extraFiles.entrySet()) {
                            handle.token.check();
                            String rel = fe.getKey().replace('\\', '/');
                            while (rel.startsWith("/")) rel = rel.substring(1);
                            if (rel.isEmpty() || rel.contains("..")) continue;
                            File f2 = new File(staging, rel);
                            if (!f2.getCanonicalPath().startsWith(staging.getCanonicalPath() + File.separator))
                                continue;
                            if (f2.getParentFile() != null) f2.getParentFile().mkdirs();
                            try (FileOutputStream out = new FileOutputStream(f2)) {
                                out.write(fe.getValue());
                            }
                        }
                    }
                    chmod755(staging);
                    handle.token.check();
                    // 提交点：停旧包进程→换目录→登记落笔；落笔即算这条装成，
                    // 此后取消只停剩下的条目，不回滚这一条
                    try {
                        LabRunner.stopPackage(ctx, m.id);
                    } catch (Exception ignored) {
                    }
                    swapIntoPlace(staging, dest);
                    createDeclaredDirs(ctx, dest, m.mkdir);
                    LabRegistry.Svc pkg = new LabRegistry.Svc();
                    pkg.id = m.id;
                    pkg.name = m.name;
                    pkg.desc = m.desc;
                    pkg.icon = m.icon;
                    pkg.version = m.version;
                    pkg.runtime = m.runtime;
                    pkg.autoStartDefault = m.autoStart;
                    pkg.installTime = System.currentTimeMillis();
                    pkg.needsReview = it.needsReview;
                    pkg.group = m.group == null ? "" : m.group;
                    LabRegistry.get(ctx).upsertSvc(ctx, pkg);
                    // 离线包 commands.json 的自建命令：写进本包 custom.json（同 id 不重复写，
                    // 重装不叠份）；LabCustomCommands 的 desc 字段与转入时已对好名
                    if (it.customCommands != null && !it.customCommands.isEmpty()) {
                        java.util.Set<String> have = new java.util.HashSet<>();
                        for (LabCustomCommands.CustomCommand old : LabCustomCommands.list(ctx, m.id)) {
                            if (old.id != null) have.add(old.id);
                        }
                        for (LabManifest.LabCommand cc : it.customCommands) {
                            if (cc.id != null && have.contains(cc.id)) continue;
                            LabCustomCommands.CustomCommand nc = new LabCustomCommands.CustomCommand();
                            nc.id = cc.id;
                            nc.name = cc.name;
                            nc.desc = cc.desc;
                            nc.command = cc.command;
                            nc.background = cc.background;
                            nc.autoExecute = cc.autoExecute;
                            nc.showOutput = cc.showOutput;
                            nc.minVersion = cc.minVersion;
                            nc.variables.addAll(cc.variables);
                            LabCustomCommands.add(ctx, m.id, nc);
                        }
                    }
                    releasePackageInstall(guard);
                    // 安装流程中不直接拉起服务：自启标记已随 manifest/注册表落盘，
                    // 由 LabBoot（开机/进实验室）与用户手动启动消费。曾在这里逐条
                    // upsert 后立即 LabRunner.start 并吞掉失败——服务可能在包还没
                    // 装完时就起来、失败也无任何提示，已恢复改前时序。
                    installCompatNext(ctx, items, idx + 1, defaultRuntime, listener, okCount + 1, handle);
                } catch (InstallCancelledException cx) {
                    // 只删暂存：dest 从头到尾没被碰过，旧包与登记原样
                    deleteDir(staging);
                    releasePackageInstall(guard);
                    cancelled2(listener);
                } catch (Exception e) {
                    releasePackageInstall(guard);
                    if (handle.token.isCancelled()) {
                        deleteDir(staging);
                        cancelled2(listener);
                    } else if (listener != null) {
                        listener.onFail(human(e));
                    }
                }
            }

            @Override
            public void onFail(String reason) {
                releasePackageInstall(guard);
                if (listener != null) listener.onFail(reason);
            }

            @Override
            public void onCancelled() {
                // 本条的环境腿被取消 = 整次导入取消，链到此收口，不再装下一条
                releasePackageInstall(guard);
                cancelled2(listener);
            }
        });
        } catch (RuntimeException setupEx) {
            // ensureRuntime 同步抛错（还没走到任何回调）时守卫也要放，并给明确终态
            releasePackageInstall(guard);
            if (listener != null) listener.onFail(human(setupEx));
        }
    }

    /**
     * 包声明的预建目录真建（上游 LabEnv.install 的 mkdirList 口径，此前星辰只在
     * 导入提示里说一句「没有预建」就丢了，G-4）。占位展开：{sdcard}→公共目录、
     * {dataPath}→包内 data、{pkgDir}/{filesDir}；相对路径落包内。只建包目录内
     * 与公共目录下的（声明由包作者控制，越界的不碰），建不上不拦安装
     * （上游同样逐个吞异常）——公共目录在真机上受分区存储管控，能不能建出来
     * 看系统脸色，建不出来命令跑起来时才见分晓，已记进交付报告的待真机项。
     */
    static void createDeclaredDirs(Context ctx, File pkgDir, java.util.List<String> dirs) {
        if (dirs == null) return;
        for (String raw : dirs) {
            if (raw == null) continue;
            String p = raw.trim()
                    .replace("{sdcard}", "/storage/emulated/0")
                    .replace("{dataPath}", new File(pkgDir, "data").getAbsolutePath())
                    .replace("{pkgDir}", pkgDir.getAbsolutePath())
                    .replace("{filesDir}", ctx.getFilesDir().getAbsolutePath());
            if (p.isEmpty()) continue;
            try {
                File dir = p.startsWith("/") ? new File(p) : new File(pkgDir, p);
                String canon = dir.getCanonicalPath();
                String pkg = pkgDir.getCanonicalPath();
                String sd = "/storage/emulated/0";
                if (canon.equals(pkg) || canon.startsWith(pkg + File.separator)
                        || canon.equals(sd) || canon.startsWith(sd + File.separator)) {
                    dir.mkdirs();
                }
            } catch (Exception ignored) {
            }
        }
    }

    /** 清单里任一命令是否引用宿主生成的 Node bundle 引导脚本 */
    private static boolean manifestReferencesNodeBoot(LabManifest manifest) {
        if (manifest == null || manifest.commands == null) return false;
        for (LabManifest.LabCommand c : manifest.commands) {
            if (c != null && c.command != null
                    && c.command.contains(LabImportCompat.NODE_BOOT_NAME)) return true;
        }
        return false;
    }

    private static void writeJson(File f, LabManifest m) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f);
             java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            new com.google.gson.Gson().toJson(m, w);
        }
    }

    private static void progress2(CompatListener l, String s) {
        if (l != null) l.onProgress(s);
    }

    /** 预读包说明（安装确认页用），不安装 */
    public static LabManifest peekManifest(File zip) throws Exception {
        return loadManifest(zip);
    }

    /** 包文件名去扩展（kuwo.lab.7z / kuwo.lab / kuwo.zip → kuwo），派生 id 的原料 */
    private static String zipStem(File zip) {
        String n = zip == null ? "" : zip.getName();
        String lower = n.toLowerCase();
        for (String ext : new String[]{".lab.7z", ".lab", ".zip", ".7z"}) {
            if (lower.endsWith(ext)) return n.substring(0, n.length() - ext.length());
        }
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    /** 包里有没有 lab.json（根目录或 files/ 下）：决定解压时要不要把 files/ 合并到包根 */
    private static boolean hasLabJson(File zip) {
        try (ZipFile zf = new ZipFile(zip)) {
            return zf.getEntry("lab.json") != null || zf.getEntry("files/lab.json") != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 读包说明：先按 .lab 包找 lab.json；没有 lab.json 但压缩包里有 npm 的 package.json，
     * 就按整目录 Node 项目生成服务说明（全部条目原样解进包目录，命令在项目根里跑）。
     * 两样都没有才报「不是 .lab 包」。
     */
    private static LabManifest loadManifest(File zip) throws Exception {
        assertZip(zip);
        try (ZipFile zf = new ZipFile(zip)) {
            ZipEntry e = zf.getEntry("lab.json");
            if (e == null) e = zf.getEntry("files/lab.json");
            if (e != null) {
                try (InputStream in = zf.getInputStream(e)) {
                    // lab.json 没写 id 时按包文件名派生（上游包没有 id 概念，
                    // 身份就是包名），不让整包卡在这一项上
                    return LabManifest.parse(readAll(in), LabImportCompat.sanitize(zipStem(zip)));
                }
            }
            java.util.List<String> names = new java.util.ArrayList<>();
            java.util.Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (!ze.isDirectory()) names.add(ze.getName().replace('\\', '/'));
            }
            String pkgEntry = LabImportCompat.findNodePackageJson(names);
            if (pkgEntry != null) {
                String pkgText;
                try (InputStream in = zf.getInputStream(zf.getEntry(pkgEntry))) {
                    pkgText = readAll(in);
                }
                org.json.JSONObject pkg = new org.json.JSONObject(pkgText);
                if (LabImportCompat.isNpmPackageJson(pkg)) {
                    LabImportCompat.ImportItem it = LabImportCompat.fromNodeProject(pkg, pkgEntry, names);
                    if (it.command == null || it.command.trim().isEmpty()) {
                        throw new Exception("这个 Node 项目找不到入口：package.json 里没写 start/main，项目根也没有 index.js");
                    }
                    return LabImportCompat.toManifest(it);
                }
            }
            // 本地 Node bundle 裸包（G-7 余路）：没 lab.json/package.json、但有
            // index.js 入口——整包由安装器原样解压落盘（mergeFiles 只认 lab.json，
            // 这类包为 false、结构原样），命令按入口前缀生成，与网址 .md5 路、
            // 7z 路同一类目（Kind.NODE_BUNDLE）收口
            String bundlePrefix = LabImportCompat.findNodeBundleEntry(names);
            if (bundlePrefix != null) {
                LabImportCompat.ImportItem it = LabImportCompat.fromNodeBundleLocal(
                        zip.getName(), null, bundlePrefix, true);
                return LabImportCompat.toManifest(it);
            }
        }
        throw new Exception("包里没有 lab.json，这不是 .lab 包");
    }

    /** 魔数预检：7z/rar 等非 zip 包给人话提示，不抛技术栈 */
    private static void assertZip(File zip) throws Exception {
        try (FileInputStream in = new FileInputStream(zip)) {
            byte[] magic = new byte[6];
            int n = in.read(magic);
            if (n >= 6 && magic[0] == 0x37 && magic[1] == 0x7A
                    && magic[2] == (byte) 0xBC && magic[3] == (byte) 0xAF) {
                throw new Exception("这是 7z 打的包，星辰只认 zip 打的 .lab 包");
            }
            if (n >= 4 && magic[0] == 'R' && magic[1] == 'a' && magic[2] == 'r' && magic[3] == '!') {
                throw new Exception("这是 rar 包，星辰只认 zip 打的 .lab 包");
            }
            if (n >= 2 && !(magic[0] == 'P' && magic[1] == 'K')) {
                throw new Exception("这不是 zip 包，装不了");
            }
        }
    }

    /** 暂存目录：与目标包目录同级（同文件系统内改名才可靠），包 id 后缀 .staging */
    private static File stagingDir(File dest) {
        return new File(dest.getParentFile(), dest.getName() + ".staging");
    }

    /**
     * 提交点换目录：旧目录先改名到 .old 备份，新目录改名就位后删备份；
     * 新目录就位失败时把备份改回原位并抛错——调用方按失败收口（此时注册表
     * 还没落笔、旧包原样可用）。两个目录同级，改名是同盘操作。
     */
    private static void swapIntoPlace(File staging, File dest) throws Exception {
        File backup = new File(dest.getParentFile(), dest.getName() + ".old");
        deleteDir(backup);
        boolean hadOld = dest.exists();
        if (hadOld && !dest.renameTo(backup)) throw new Exception("旧包目录挪不开，装不了");
        if (!staging.renameTo(dest)) {
            if (hadOld && !dest.exists()) backup.renameTo(dest);
            throw new Exception("新包目录就位失败，已还原旧包");
        }
        deleteDir(backup);
    }

    /**
     * 解压全部条目到包目录。mergeFiles=true（.lab 包）时按格式约定把 files/ 前缀去掉、
     * 内容合并到包根；整目录 Node 项目不合并，项目结构原样保留。
     */
    private static void unzip(File zip, File dest, boolean mergeFiles, CancelToken token) throws Exception {
        deleteDir(dest);
        dest.mkdirs();
        String destPrefix = dest.getCanonicalPath() + File.separator;
        try (ZipFile zf = new ZipFile(zip)) {
            // 两趟写，消除同名覆盖的不确定性：mergeFiles 时先写 files/ 合并条目，
            // 再写根目录条目——根目录版本永远赢，不再取决于 zip 内的条目顺序
            // （此前单趟流式写，根与 files/ 同名时谁后出现谁赢，含 lab.json 本身）
            for (int pass = 0; pass < 2; pass++) {
                java.util.Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry entry = en.nextElement();
                    token.check();
                    if (entry.isDirectory()) continue;
                    String raw = entry.getName();
                    boolean fromFiles = mergeFiles && raw.startsWith("files/");
                    if ((pass == 0) != fromFiles && mergeFiles) continue;
                    if (!mergeFiles && pass == 1) break;
                    String name = raw;
                    if (fromFiles) {
                        name = raw.substring("files/".length());
                        if (name.isEmpty()) continue;
                    }
                    // 防 zip-slip
                    File out = new File(dest, name);
                    if (!out.getCanonicalPath().startsWith(destPrefix)) continue;
                    out.getParentFile().mkdirs();
                    try (InputStream in = zf.getInputStream(entry);
                         BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(out))) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            token.check();
                            bout.write(buf, 0, n);
                        }
                    }
                }
                if (!mergeFiles) break;
            }
        }
    }

    private static void chmod755(File dir) {
        try {
            // 整目录递归 755（参照物优化过：逐文件 chmod 在 node 这类大包上会卡）
            new ProcessBuilder("chmod", "-R", "755", dir.getAbsolutePath()).start().waitFor();
        } catch (Exception ignored) {
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

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static void copyStream(InputStream in, File dest, CancelToken token) throws Exception {
        if (in == null) throw new Exception("打不开这个文件");
        try (BufferedInputStream bin = new BufferedInputStream(in);
             BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(dest))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = bin.read(buf)) > 0) {
                token.check();
                bout.write(buf, 0, n);
            }
        }
    }

    private static String human(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) return "这个包打不开，换一个试试";
        return m;
    }

    private static void progress(Listener l, String s) {
        if (l != null) l.onProgress(s);
    }

    private static void ok(Listener l, LabRegistry.Svc p) {
        if (l != null) l.onOk(p);
    }

    private static void fail(Listener l, String s) {
        if (l != null) l.onFail(s);
    }

    private static void cancelled(Listener l) {
        if (l != null) l.onCancelled();
    }

    private static void cancelled2(CompatListener l) {
        if (l != null) l.onCancelled();
    }

    /** 导出回调：fileName 为下载目录里的文件名 */
    public interface ExportListener {
        void onOk(String fileName);

        void onFail(String reason);
    }

    /**
     * 导出包（导入的对）：把包目录打成 .lab 包，写进下载目录/星辰实验室。
     * Q+ 走 MediaStore，Q 以下走 Download 文件（需 WRITE_EXTERNAL_STORAGE，maxSdkVersion=28）。
     */
    public static void export(Context ctx, String pkgId, ExportListener listener) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                LabRegistry.Svc pkg = LabRegistry.get(c).svc(pkgId);
                if (pkg == null) throw new Exception("这个包已经不在了");
                File src = LabPaths.packageDir(c, pkgId);
                if (!src.exists()) throw new Exception("包目录找不到了");
                String safeName = (pkg.name == null ? pkgId : pkg.name).replaceAll("[\\\\/:*?\"<>|]", "_");
                String fileName = safeName + ".lab";
                File tmp = new File(c.getCacheDir(), "lab-export-" + System.currentTimeMillis() + ".lab");
                zipDir(src, tmp);
                boolean ok = writeToDownload(c, fileName, tmp);
                tmp.delete();
                if (!ok) throw new Exception("写不进下载目录");
                if (listener != null) listener.onOk(fileName);
            } catch (Exception e) {
                if (listener != null) listener.onFail("导出失败：" + human(e));
            }
        }).start();
    }

    private static void zipDir(File src, File dest) throws Exception {
        if (dest.getParentFile() != null) dest.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(dest);
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                     new BufferedOutputStream(fos))) {
            zipRec(src, src, zos);
        }
    }

    private static void zipRec(File root, File f, java.util.zip.ZipOutputStream zos) throws Exception {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) zipRec(root, k, zos);
            return;
        }
        String rel = root.toURI().relativize(f.toURI()).getPath();
        zos.putNextEntry(new ZipEntry(rel));
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
        }
        zos.closeEntry();
    }

    private static boolean writeToDownload(Context c, String fileName, File src) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
                v.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip");
                v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/星辰实验室");
                android.net.Uri uri = c.getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return false;
                try (java.io.InputStream in = new FileInputStream(src);
                     java.io.OutputStream out = c.getContentResolver().openOutputStream(uri)) {
                    if (out == null) return false;
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                return true;
            }
            File dir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "星辰实验室");
            if (!dir.exists() && !dir.mkdirs()) return false;
            try (java.io.InputStream in = new FileInputStream(src);
                 java.io.OutputStream out = new FileOutputStream(new File(dir, fileName))) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
