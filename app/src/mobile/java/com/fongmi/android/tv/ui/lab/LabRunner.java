package com.fongmi.android.tv.ui.lab;

import android.content.Context;
import android.text.TextUtils;

import com.fongmi.android.tv.api.CatSource;
import com.fongmi.android.tv.clash.ClashSettings;
import com.fongmi.android.tv.clash.MihomoManager;
import com.fongmi.android.tv.server.Server;
import com.github.catvod.Proxy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务起停（照参照物 LabRunner 的做法）：
 * ProcessBuilder("/system/bin/setsid","/system/bin/sh","-c",命令)，
 * cwd=包目录，PATH/LD_LIBRARY_PATH 指向环境与包内 bin；
 * pid 落盘＋/proc 恢复；输出进内存（上限）＋每包一份日志文件。
 */
public final class LabRunner {

    public interface Listener {
        void onOutput(String text);

        void onExit(int code);

        /**
         * 进程已确认起跑：runShell 里 builder.start() 成功、RUNNING 登记之后触发。
         * 给「启动中…」这类乐观态一个翻回「运行中」的时机——onExit 只在进程退出时
         * 才来，常驻服务等不到它。默认空实现，老实现不用改。
         */
        default void onStarted() {
        }
    }

    /** 日志内存上限（参照物：最近 20 万字符） */
    private static final int LOG_CAP = 200_000;

    private static final Map<String, Process> RUNNING = new ConcurrentHashMap<>();
    private static final Map<String, StringBuilder> LOGS = new ConcurrentHashMap<>();
    private static final Map<String, java.io.OutputStream> STDIN = new ConcurrentHashMap<>();
    /** 最近一次启动失败的人话原因（成功启动后清掉） */
    private static final Map<String, String> ERRORS = new ConcurrentHashMap<>();

    public static String lastError(String pkgId) {
        return ERRORS.get(keyOf(pkgId));
    }

    private LabRunner() {
    }

    // ---------------- 状态 ----------------

    public static boolean isRunning(Context c, String pkgId) {
        String key = keyOf(pkgId);
        Process p = RUNNING.get(key);
        if (LabProc.alive(p)) return true;
        return LabProc.recoveredAliveKeys(c).contains(key);
    }

    public static int runningCount(Context c) {
        Set<String> keys = new LinkedHashSet<>();
        for (Map.Entry<String, Process> e : RUNNING.entrySet()) {
            if (LabProc.alive(e.getValue())) keys.add(e.getKey());
        }
        keys.addAll(LabProc.recoveredAliveKeys(c));
        return keys.size();
    }

    /** 包最后一次分配的端口（port=auto 时记住，下次复用） */
    public static int portOf(Context c, String pkgId) {
        LabRegistry.Svc p = LabRegistry.get(c).svc(pkgId);
        return p == null ? 0 : p.port;
    }

    /** 宿主 Nano 服务口兜底值（原件 LabConfig.serverPort 同值：拉起后仍无口时用它） */
    static final int HOST_PORT_FALLBACK = 9978;

    /**
     * 宿主口安全取值（原件 LabConfig.serverPort 三件口径）：Proxy 还没起时
     * getPort() 是 -1——冷启时 App 先反射 kick LabBoot（新线程立即 boot→start→
     * 本类 expand），Server.get().start() 在 App 里排在其后 1200ms 的后台服务
     * 批次里，自启展开直取必然拿到 -1，命令里的 LAB_HOST_PORT=-1 让 bundle 的
     * /msg 回调永远连不上。故 ≤0 时先把 Server 拉起来再取一次（start 是
     * synchronized 且已在跑时空转，重复调无副作用），仍无口兜底 9978
     * （Server 自己的首选口），绝不把 -1 展开进命令。expand 的全部调用点都在
     * 启动链上（start/startCommand 的命令与依赖展开、runShell 前的 applyEnv），
     * 没有离线纯展开场景，在这里拉起不算乱启动。
     */
    static int hostPort() {
        return resolveHostPort(Proxy.getPort(), () -> {
            try {
                Server.get().start();
            } catch (Throwable ignored) {
            }
            return Proxy.getPort();
        });
    }

    /** hostPort 的纯逻辑核（便于单测）：已有口直接用；≤0 时问一次拉起后的口，仍 ≤0 兜底 */
    static int resolveHostPort(int currentPort, java.util.function.IntSupplier afterEnsure) {
        if (currentPort > 0) return currentPort;
        int p = afterEnsure.getAsInt();
        return p > 0 ? p : HOST_PORT_FALLBACK;
    }

    // ---------------- 按命令起停（上游 LabCommandSheet 口径） ----------------

    /** 命令键：pkg/<pkgId>/cmd/<cmdId>（服务默认命令沿用旧 keyOf 语义，不动） */
    public static String commandKey(String pkgId, String cmdId) {
        return "cmd/" + pkgId + "/" + cmdId;
    }

    public static boolean isCommandRunning(Context c, String pkgId, String cmdId) {
        String key = commandKey(pkgId, cmdId);
        Process p = RUNNING.get(key);
        if (LabProc.alive(p)) return true;
        return LabProc.recoveredAliveKeys(c).contains(key);
    }

    public static void stopCommand(Context c, String pkgId, String cmdId) {
        stopKey(c, commandKey(pkgId, cmdId));
    }

    /** 运行中条目（运行中对话框用）：key → 人话名 */
    public static List<String[]> runningEntries(Context c) {
        List<String[]> out = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>(RUNNING.keySet());
        keys.addAll(LabProc.recoveredAliveKeys(c));
        for (String key : keys) {
            out.add(new String[]{key, keyLabel(c, key)});
        }
        return out;
    }

    private static String keyLabel(Context c, String key) {
        try {
            if (key.startsWith("cmd/")) {
                String[] parts = key.split("/", 3);
                if (parts.length == 3) {
                    LabRegistry.Svc s = LabRegistry.get(c).svc(parts[1]);
                    String name = s == null ? parts[1] : s.name;
                    return name + " / " + parts[2];
                }
            } else if (key.startsWith("pkg/")) {
                String id = key.substring(4);
                LabRegistry.Svc s = LabRegistry.get(c).svc(id);
                return s == null ? id : s.name;
            } else if (key.startsWith("term/")) {
                String id = key.substring(5);
                LabRegistry.Svc s = LabRegistry.get(c).svc(id);
                return "终端：" + (s == null ? id : s.name);
            }
        } catch (Exception ignored) {
        }
        return key;
    }

    /**
     * 按命令启动（变量表/自定义命令/快捷运行都走这里）。
     *
     * @param cmd    命令（包命令或自定义命令转成的 LabCommand）
     * @param vars   变量值（可为 null）
     */
    public static void startCommand(Context ctx, String pkgId, LabManifest.LabCommand cmd,
                                    Map<String, String> vars, Listener listener) {
        final Context c = ctx.getApplicationContext();
        final String key = commandKey(pkgId, cmd == null || cmd.id == null ? "cmd" : cmd.id);
        new Thread(() -> {
            try {
                LabRegistry.Svc pkg = LabRegistry.get(c).svc(pkgId);
                if (pkg == null) throw new Exception("这个包已经不在了");
                String runtime = pkg.runtime == null ? "none" : pkg.runtime;
                if (!"none".equals(runtime) && !LabRuntimeManager.isInstalled(c, runtime)) {
                    throw new Exception("缺" + LabRuntimeManager.label(runtime) + "，先把它装上");
                }
                checkMinVersion(c, cmd);
                stopKey(c, key);
                // 变量默认值垫底（此前只有弹窗路才有值、主启路 {root_dir} 等原样残留），
                // 包设置值再垫一层，同名变量最后覆盖（上游变量值缓存复用口径）
                Map<String, String> merged = defaultVars(readManifestQuiet(c, pkgId), cmd);
                merged.putAll(LabCustomCommands.settingsOf(c, pkgId));
                if (vars != null) merged.putAll(vars);
                int port = allocPort(c, pkg, readManifestQuiet(c, pkgId), merged.get("port"));
                ensureDependency(c, pkgId, cmd, port);
                String command = expand(c, pkgId, cmd.command, port, merged);
                ERRORS.remove(key);
                runShell(c, pkgId, key, command, pkgId, listener);
            } catch (Exception e) {
                String m = e.getMessage();
                // 失败原因进 ERRORS（lastError 的数据源）：与默认命令那条 startCommand 同口径，
                // 此前这条路的失败只通知 listener、页面上查不到原因
                ERRORS.put(key, m == null ? "未知原因" : m);
                recordFailure(c, pkgId, key, m == null ? "未知原因" : m);
                String fail = "启动失败：" + (m == null ? "未知原因" : m) + "\n";
                // 正在看这条命令实时输出的页面也要拿到失败原因（总线另一半在 pump）
                LabLiveOutputActivity.LiveBus.emit(key, fail);
                LabLiveOutputActivity.LiveBus.emitExit(key, -1);
                if (listener != null) {
                    listener.onOutput(fail);
                    listener.onExit(-1);
                }
            }
        }).start();
    }

    /** min_version 门控：App 版本不够就提示升级（上游 Command.isSupported 口径） */
    private static void checkMinVersion(Context c, LabManifest.LabCommand cmd) throws Exception {
        if (cmd == null || cmd.minVersion <= 0) return;
        try {
            int vc = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
            if (vc < cmd.minVersion) {
                throw new Exception("这条命令要新版星辰才跑得动，先更新 App");
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("新版星辰")) throw e;
        }
    }

    /**
     * 命令依赖备料：download 声明缺了才下，按声明原样落盘（save_to）或整包解压
     * （extract_to / Item 级 downloads，含 .gz/.tar.gz 解压、binary_path 归位、补可执行），
     * 判定与落位走 LabDepends（上游 prepareDependency 口径）。路径里的占位
     * （{sdcard}/{pkgDir}/{dataPath} 等）先按命令同口径展开再判定落盘。
     */
    private static void ensureDependency(Context c, String pkgId, LabManifest.LabCommand cmd, int port) throws Exception {
        // url 空但只有 libUrl 的声明也要备料：主片 needed() 按 url 空自动跳过、
        // 第二分片照常判缺补下；主片与第二分片都空才真是没东西可备
        if (cmd == null || cmd.download == null
                || (TextUtils.isEmpty(cmd.download.url) && TextUtils.isEmpty(cmd.download.libUrl))) return;
        File pkgDir = LabPaths.packageDir(c, pkgId);
        LabManifest.LabDownload src = cmd.download;
        LabManifest.LabDownload d = new LabManifest.LabDownload();
        d.url = expand(c, pkgId, src.url, port);
        d.file = expand(c, pkgId, src.file, port);
        d.checkFile = expand(c, pkgId, src.checkFile, port);
        d.extractTo = expand(c, pkgId, src.extractTo, port);
        d.libUrl = expand(c, pkgId, src.libUrl, port);
        // 镜像列表也要带上（此前拼下载副本时丢了，运行层只剩主 url 一条路，
        // 主 url 失效＋镜像存活的包必然下载失败）；逐条同口径展开占位
        if (src.mirrors != null) {
            for (String m : src.mirrors) d.mirrors.add(expand(c, pkgId, m, port));
        }
        d.required = src.required;
        boolean mainNeeded = LabDepends.needed(pkgDir, d);
        boolean libNeeded = d.libUrl != null && !d.libUrl.trim().isEmpty()
                && LabDepends.needed(pkgDir, libOnly(d));
        if (!mainNeeded && !libNeeded) return;
        try {
            LabDepends.fetch(pkgDir, c.getCacheDir(), d, LabRunner::downloadFile);
        } catch (Exception e) {
            // required 消费点（此前收而不用、失败恒抛）：声明必备才拦启动；
            // 非必备的下载失败只记进运行记录，命令照常起（文件可能已在别处备好）
            if (src.required) throw e;
            appendFile(LabProc.logFile(c, pkgId),
                    "依赖没下下来（声明为非必备，继续启动）：" + e.getMessage() + "\n");
        }
    }

    /** 只带第二分片的判定用副本（needed 先看 checkFile、再看解压标记/落点） */
    private static LabManifest.LabDownload libOnly(LabManifest.LabDownload d) {
        LabManifest.LabDownload lib = new LabManifest.LabDownload();
        lib.url = d.libUrl;
        lib.extractTo = d.extractTo;
        // checkFile 不带上时，在位判定永远落到解压标记/落点上猜：声明了
        // checkFile 的包第二分片已在位也会误判「缺」、每次启动重复下载
        lib.checkFile = d.checkFile;
        return lib;
    }

    private static void downloadFile(String url, File dest) throws Exception {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setInstanceFollowRedirects(true);
            applyProxyConn(conn);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("依赖下不下来（" + code + "）");
            if (dest.getParentFile() != null) dest.getParentFile().mkdirs();
            try (java.io.InputStream in = new java.io.BufferedInputStream(conn.getInputStream());
                 java.io.OutputStream out = new java.io.BufferedOutputStream(new FileOutputStream(dest))) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void applyProxyConn(java.net.HttpURLConnection conn) {
        // 走 JVM 全局代理太重；下载走直连（与既有 importFromUrl 行为一致）
    }

    // ---------------- 交互终端 ----------------

    /**
     * 给包开一个交互 shell（终端页用）：cwd=包目录，环境与服务进程一致。
     * 返回 stdin 输出流写把柄由调用方持有；进程按 term/<pkgId> 跟踪，随「全部停止」一起停。
     */
    public static Process startTerminal(Context ctx, String pkgId) throws Exception {
        final Context c = ctx.getApplicationContext();
        LabRegistry.Svc pkg = LabRegistry.get(c).svc(pkgId);
        if (pkg == null) throw new Exception("这个包已经不在了");
        File cwd = LabPaths.packageDir(c, pkgId);
        cwd.mkdirs();
        String key = "term/" + pkgId;
        stopKey(c, key);
        ProcessBuilder builder = new ProcessBuilder("/system/bin/sh");
        builder.directory(cwd);
        builder.redirectErrorStream(true);
        applyEnv(c, pkgId, builder.environment());
        Process p = builder.start();
        RUNNING.put(key, p);
        try {
            STDIN.put(key, p.getOutputStream());
        } catch (Exception ignored) {
        }
        LabProc.track(c, key, LabProc.pidOf(p), LabProc.logFile(c, key).getAbsolutePath());
        LabKeepAlive.refresh(c);
        return p;
    }

    /** 往指定 key 的进程 stdin 写字节（终端页用） */
    public static void writeStdin(String key, byte[] data) throws Exception {
        java.io.OutputStream out = STDIN.get(key);
        if (out == null) throw new Exception("进程不在了");
        out.write(data);
        out.flush();
    }

    /** 停止任意 key（pkg/cmd/term 通用） */
    public static void stopKey(Context c, String key) {
        Process p = RUNNING.remove(key);
        java.io.OutputStream stdin = STDIN.remove(key);
        if (stdin != null) {
            try {
                stdin.close();
            } catch (Exception ignored) {
            }
        }
        if (p != null) {
            try {
                p.destroy();
            } catch (Exception ignored) {
            }
            LabProc.killTree(LabProc.pidOf(p));
        }
        LabProc.stopByState(c, key);
        LabKeepAlive.refresh(c);
    }

    // ---------------- 启动 ----------------

    /**
     * 启动包的默认命令。先查环境（缺了抛人话，由 UI 引导装环境）。
     */
    public static void start(Context ctx, String pkgId, Listener listener) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                LabRegistry.Svc pkg = LabRegistry.get(c).svc(pkgId);
                if (pkg == null) throw new Exception("这个包已经不在了");
                LabManifest manifest = readManifest(c, pkgId);
                LabManifest.LabCommand cmd = manifest.defaultCommand();
                if (cmd == null || TextUtils.isEmpty(cmd.command)) throw new Exception("这个包没有可运行的命令");
                String runtime = pkg.runtime == null ? "none" : pkg.runtime;
                if (!"none".equals(runtime) && !LabRuntimeManager.isInstalled(c, runtime)) {
                    throw new Exception("缺" + LabRuntimeManager.label(runtime) + "，先把它装上");
                }
                checkMinVersion(c, cmd);
                ERRORS.remove(keyOf(pkgId));
                stop(c, pkgId);
                // 主启路也填变量默认值＋包设置（此前 vars=null，{root_dir} 原样
                // 残留在命令里、变量 port 的默认端口也被分配口接管）
                Map<String, String> merged = defaultVars(manifest, cmd);
                merged.putAll(LabCustomCommands.settingsOf(c, pkgId));
                int port = allocPort(c, pkg, manifest, merged.get("port"));
                // 默认命令路此前不备料也不查版本门：靠 downloads 拿二进制的包
                // （mihomo/frp/ubuntu 一族）从服务页启动永远缺文件，与按命令启动
                // 那条路口径对齐（上游运行前统一 prepareDependency）
                ensureDependency(c, pkgId, cmd, port);
                String command = expand(c, pkgId, cmd.command, port, merged);
                runShell(c, pkgId, keyOf(pkgId), command, pkgId, listener);
                if (listener != null) listener.onOutput("");
            } catch (Exception e) {
                String m = e.getMessage();
                ERRORS.put(keyOf(pkgId), m == null ? "未知原因" : m);
                recordFailure(c, pkgId, keyOf(pkgId), m == null ? "未知原因" : m);
                if (listener != null) {
                    listener.onOutput("启动失败：" + (m == null ? "未知原因" : m) + "\n");
                    listener.onExit(-1);
                }
            }
        }).start();
    }

    /**
     * 启动失败落运行记录：runShell 之前抛的失败（包说明读坏、缺环境、端口被占、
     * 命令为空等）此前只进内存 ERRORS，进程一重启就没了，运行记录页永远空着，
     * 界面却叫人「去运行记录看看」。失败必须在记录里留一行，内存与落盘各一份，
     * 与 pump 写进程输出同一份文件（getLog 的读取口径）。
     */
    private static void recordFailure(Context c, String pkgId, String key, String message) {
        String line = "启动失败：" + message + "\n";
        appendLog(key, line);
        appendFile(LabProc.logFile(c, pkgId), line);
    }

    /** 变量表里名为 port 的变量值（用户填的/默认值）优先于清单端口策略：
     * 默影视口径里变量 port 就是命令监听口，分配口必须跟它对齐，否则服务页
     * 登记的口与命令实际绑的口两张皮 */
    private static int allocPort(Context c, LabRegistry.Svc pkg, LabManifest manifest,
                                 String preferredPort) throws Exception {
        int preferred = fixedPortOf(preferredPort);
        if (preferred > 0) {
            LabManifest fake = new LabManifest();
            fake.output = new LabManifest.LabOutput();
            fake.output.port = String.valueOf(preferred);
            return allocPort(c, pkg, fake);
        }
        return allocPort(c, pkg, manifest);
    }

    /**
     * 固定口解析（纯函数，便于单测）：纯数字且落在 1..65535 内才认，返回口值；
     * 其余（空/非数字/0/超限/长到溢出的数字串）一律无效、返回 -1，按「没声明
     * 固定口」回落 auto 分配。此前只判 \d+：填 0 或 >65535 也进固定口分支，
     * 十次重试后误报「固定端口已经被别的程序占着」，文案与真因不符；
     * 溢出数字串还会让 parseInt 直接抛 NumberFormatException。
     */
    static int fixedPortOf(String policy) {
        if (policy == null) return -1;
        String p = policy.trim();
        if (!p.matches("\\d+")) return -1;
        try {
            int v = Integer.parseInt(p);
            return v >= 1 && v <= 65535 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 命令变量默认值表：命令 variables 的 defaultValue + 包 settings 的 defaultValue */
    private static Map<String, String> defaultVars(LabManifest manifest, LabManifest.LabCommand cmd) {
        Map<String, String> out = new java.util.HashMap<>();
        if (manifest != null) {
            for (LabManifest.LabVar v : manifest.settings) {
                if (v.key != null && v.defaultValue != null) out.put(v.key, v.defaultValue);
            }
        }
        if (cmd != null) {
            for (LabManifest.LabVar v : cmd.variables) {
                if (v.key != null && v.defaultValue != null) out.put(v.key, v.defaultValue);
            }
        }
        return out;
    }

    private static int allocPort(Context c, LabRegistry.Svc pkg, LabManifest manifest) throws Exception {
        String policy = manifest == null || manifest.output == null ? "auto" : manifest.output.port;
        int fixed = fixedPortOf(policy);
        if (fixed > 0) {
            // 固定口先查占用（G-3）：此前不查，被占时服务绑口失败只进日志、
            // 界面还按这个口登记，用户对着死口干等。刚停掉的自家进程放口
            // 可能慢一拍，给十次短重试；仍占着就报人话，不许静默按错口展开
            boolean free = portFree(fixed);
            for (int i = 0; !free && i < 10; i++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                free = portFree(fixed);
            }
            if (!free) {
                throw new Exception("固定端口 " + fixed + " 已经被别的程序占着了，先停掉占它的服务，或者改这个包的端口");
            }
            if (pkg.port != fixed) {
                pkg.port = fixed;
                LabRegistry.get(c).upsertSvc(c, pkg);
            }
            return fixed;
        }
        if (pkg.port > 0 && portFree(pkg.port)) return pkg.port;
        int port = findFreePort();
        pkg.port = port;
        LabRegistry.get(c).upsertSvc(c, pkg);
        return port;
    }

    private static int findFreePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (Exception e) {
            return 18080 + (int) (Math.random() * 4000);
        }
    }

    private static boolean portFree(int port) {
        try (ServerSocket s = new ServerSocket(port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String expand(Context c, String pkgId, String command, int port) {
        return expand(c, pkgId, command, port, null);
    }

    /**
     * 命令展开（上游 LabRunner.expand 口径的精简版）：
     * 1) {var} 变量替换（vars 优先，包设置值已在调用方合并进来），支持嵌套（最多 5 层），
     *    ${...} 跳过不碰；没值的占位原样保留（缺失变量由调用方先弹变量表补齐）。
     * 2) 特殊占位：{serverPort}＝壳子文件服务口（默影视语义，与 {hostPort} 同值；
     *    此前误换成本次分配口，默影视包里靠它下 EnvFiles 的地址永远连不上）；
     *    {hostPort}＝宿主 Nano 服务口（与点播链 NodeBoot 给 bundle 的
     *    catDartServerPort 同一个值）。两件都经 hostPort() 安全取值：服务没
     *    起来时先拉起、仍无兜底 9978，不再把 -1 展开进命令（原件
     *    LabConfig.serverPort 口径）；
     *    {tvPath}{dataPath}{cachePath}
     *    {envRootPath}{wwwroot}{sdcard}；旧 {port}{pkgDir}{filesDir} 照旧。
     */
    private static String expand(Context c, String pkgId, String command, int port,
                                 Map<String, String> vars) {
        if (command == null) return "";
        String out = expandVars(command, vars, 0);
        File pkgDir = LabPaths.packageDir(c, pkgId);
        File labRoot = LabPaths.root(c);
        String host = String.valueOf(hostPort());
        return out
                .replace("{serverPort}", host)
                .replace("{hostPort}", host)
                .replace("{tvPath}", new File(c.getFilesDir(), "TV").getAbsolutePath())
                .replace("{dataPath}", new File(pkgDir, "data").getAbsolutePath())
                .replace("{cachePath}", c.getCacheDir().getAbsolutePath())
                .replace("{envRootPath}", pkgDir.getAbsolutePath())
                .replace("{wwwroot}", new File(labRoot, "wwwroot").getAbsolutePath())
                .replace("{sdcard}", "/storage/emulated/0")
                .replace("{port}", String.valueOf(port))
                .replace("{pkgDir}", pkgDir.getAbsolutePath())
                .replace("{filesDir}", c.getFilesDir().getAbsolutePath());
    }

    private static String expandVars(String text, Map<String, String> vars, int depth) {
        if (text == null || depth > 5) return text;
        Matcher m = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}").matcher(text);
        StringBuffer sb = new StringBuffer();
        boolean changed = false;
        while (m.find()) {
            if (m.start() > 0 && text.charAt(m.start() - 1) == '$') {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            String val = vars == null ? null : vars.get(m.group(1));
            if (val == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(expandVars(val, vars, depth + 1)));
            changed = true;
        }
        m.appendTail(sb);
        if (!changed) return sb.toString();
        return expandVars(sb.toString(), vars, depth + 1);
    }

    private static void runShell(Context c, String pkgId, String key, String command, String logName, Listener listener) throws Exception {
        // 失败只从这一处往上抛：listener 通知归调用方（start/startCommand）唯一出口。
        // 此前这里也通知一遍、还不进 ERRORS，与 start 的 catch 两头不一致、双回调。
        // 进程退出后的 onExit 由 pump 负责，与启动失败分开。
        if (TextUtils.isEmpty(command)) throw new Exception("命令为空");
        File cwd = LabPaths.packageDir(c, pkgId);
        cwd.mkdirs();
        File log = LabProc.logFile(c, logName);
        // 相对路径兜底：接在 expand() 之后（命令已是最终形态，{pkgDir} 等已展开成绝对路径），
        // ProcessBuilder 拼 shell 之前。包根下找不到、files/ 下有的相对路径补上 files/ 前缀。
        String aligned = LabPathAlign.align(cwd, command);
        if (!aligned.equals(command)) {
            String pathNote = "命令路径自动对齐到 files/：原命令里的脚本在包根下找不到、files/ 下有，已补上 files/ 前缀";
            appendLog(key, pathNote + "\n");
            appendFile(log, pathNote + "\n");
            command = aligned;
        }
        // Node bundle 包：上一轮引导脚本落盘的候选口先删——认口线程读到旧文件
        // 会把登记改回上一轮的随机口
        boolean nodeBundle = isNodeBundle(readManifestQuiet(c, pkgId));
        if (nodeBundle) {
            try {
                new File(cwd, LabImportCompat.NODE_PORT_NAME).delete();
            } catch (Exception ignored) {
            }
        }
        ProcessBuilder builder = new ProcessBuilder("/system/bin/setsid", "/system/bin/sh", "-c", command);
        builder.directory(cwd);
        builder.redirectErrorStream(true);
        applyEnv(c, pkgId, builder.environment());
        String nodeNote = applyNodeProxy(c, pkgId, builder.environment());
        Process process = builder.start();
        RUNNING.put(key, process);
        try {
            STDIN.put(key, process.getOutputStream());
        } catch (Exception ignored) {
        }
        // 进程已确认起跑：通知 UI 把「启动中…」翻回「运行中」。
        // （onExit 只在进程退出时才来，常驻服务等不到它。）
        if (listener != null) listener.onStarted();
        if (nodeNote != null) {
            appendLog(key, nodeNote + "\n");
            appendFile(log, nodeNote + "\n");
        }
        ERRORS.remove(key);
        LabProc.track(c, key, LabProc.pidOf(process), log.getAbsolutePath());
        LabKeepAlive.refresh(c);
        if (nodeBundle) watchNodePort(c, pkgId, key, process);
        pump(c, key, process, log, listener);
    }

    /** 这个包是不是走宿主引导脚本的 Node bundle（任一命令引用 lab-node-boot.js） */
    private static boolean isNodeBundle(LabManifest mf) {
        if (mf == null || mf.commands == null) return false;
        for (LabManifest.LabCommand cmd : mf.commands) {
            if (cmd != null && cmd.command != null
                    && cmd.command.contains(LabImportCompat.NODE_BOOT_NAME)) return true;
        }
        return false;
    }

    /** 认口轮询间隔/总窗口：引导脚本的发布窗口是 150×200ms≈30 秒，这边留 45 秒盖住起进程延迟 */
    private static final long NODE_PORT_POLL_MS = 250;
    private static final long NODE_PORT_WATCH_MS = 45_000;

    /**
     * Node bundle 认口（点播链 NodeBoot/NodeService 同口径的最小同效实现）：
     * 引导脚本把候选监听口落盘到包根 lab-node-port，这边逐个探 /config、
     * 回包形状过 CatSource.isConfig 的才是真口；认到且与登记不同就改登记
     * （Svc.port 是服务页地址与产出地址的唯一数据源，改它即改登记），
     * 并在运行记录里留一行。进程被停/换代（RUNNING 里的实例变了）立即收工，
     * 不许上一轮的认口线程改下一轮的登记。结构差异说明：点播链的认口跑在
     * :node 子进程的就绪等待里、结果走 Messenger 回主进程；实验室的 bundle
     * 是 shell 子进程、没有这条回报通道，故认口放在 LabRunner 的轮询线程里，
     * 落盘＋探 /config 两件核心机制与点播链一致。
     */
    private static void watchNodePort(Context c, String pkgId, String key, Process process) {
        final File portFile = new File(LabPaths.packageDir(c, pkgId), LabImportCompat.NODE_PORT_NAME);
        new Thread(() -> {
            long deadline = System.currentTimeMillis() + NODE_PORT_WATCH_MS;
            while (System.currentTimeMillis() < deadline) {
                if (RUNNING.get(key) != process) return;
                for (int candidate : readNodePorts(portFile)) {
                    if (RUNNING.get(key) != process) return;
                    if (probeNodeConfig(candidate)) {
                        LabRegistry.Svc svc = LabRegistry.get(c).svc(pkgId);
                        if (svc != null && svc.port != candidate) {
                            svc.port = candidate;
                            LabRegistry.get(c).upsertSvc(c, svc);
                            String note = "实际监听口与登记口不同，已按 /config 认到的口重新登记：" + candidate + "\n";
                            appendLog(key, note);
                            appendFile(LabProc.logFile(c, pkgId), note);
                        }
                        return;
                    }
                }
                try {
                    Thread.sleep(NODE_PORT_POLL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }).start();
    }

    /** 引导脚本落盘的候选端口（逗号分隔，指定口在最前）——与 NodeRuntime.readPorts 同口径，兼容单口旧格式 */
    static java.util.List<Integer> readNodePorts(File file) {
        java.util.List<Integer> ports = new java.util.ArrayList<>();
        if (file == null || !file.exists()) return ports;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[128];
            int len = in.read(buf);
            if (len <= 0) return ports;
            for (String part : new String(buf, 0, len, StandardCharsets.UTF_8).trim().split(",")) {
                try {
                    int v = Integer.parseInt(part.trim());
                    if (v > 0 && v <= 65535 && !ports.contains(v)) ports.add(v);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return ports;
    }

    /** 探 /config 认口（与 NodeService.waitReady 同口径）：2 秒超时，回包形状过 isConfig 才算这个口 */
    static boolean probeNodeConfig(int port) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(
                    "http://127.0.0.1:" + port + "/config").openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            try (java.io.InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[8192];
                StringBuilder sb = new StringBuilder();
                int n;
                while ((n = in.read(buf)) > 0 && sb.length() < 65536) {
                    sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
                return CatSource.isConfig(sb.toString());
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void applyEnv(Context c, String pkgId, Map<String, String> env) {
        LabRegistry.Svc pkg = LabRegistry.get(c).svc(pkgId);
        String runtime = pkg == null || pkg.runtime == null ? "none" : pkg.runtime;
        Set<String> path = new LinkedHashSet<>();
        if (!"none".equals(runtime)) path.add(LabRuntimeManager.binDir(c, runtime).getAbsolutePath());
        path.add(new File(LabPaths.packageDir(c, pkgId), "bin").getAbsolutePath());
        String old = env.get("PATH");
        if (old != null) path.add(old);
        path.add("/system/bin");
        path.add("/system/xbin");
        path.add("/vendor/bin");
        env.put("PATH", join(path, ":"));
        String bin = new File(LabPaths.packageDir(c, pkgId), "bin").getAbsolutePath();
        String oldLd = env.get("LD_LIBRARY_PATH");
        String lib = LabRuntimeManager.libDir(c, runtime).getAbsolutePath();
        // 运行时自己的 bin 必须在 LD 里：运行时是 Termux 布局，可执行文件与它的依赖 .so
        // 同住 bin/，且二进制 RUNPATH 写死 Termux 前缀、在星辰目录下不存在。只靠 lib/
        // 软链农场时，农场漏掉的版本化 SONAME（libz.so.1 一类）无处可达，进程在链接期
        // 直接 CANNOT LINK EXECUTABLE。上游口径正是把环境 bin 内文件全体链进 sharedBin
        // 再让 LD 指向它——星辰分家布局下的等价做法就是把运行时 bin 本人放进 LD。
        Set<String> ldSet = new LinkedHashSet<>();
        ldSet.add(bin);
        if (!"none".equals(runtime)) ldSet.add(LabRuntimeManager.binDir(c, runtime).getAbsolutePath());
        ldSet.add(lib);
        String ld = join(ldSet, File.pathSeparator);
        env.put("LD_LIBRARY_PATH", TextUtils.isEmpty(oldLd) ? ld : ld + File.pathSeparator + oldLd);
        // Python 运行时证书下发：运行时是 Termux 构建，OpenSSL 默认证书路径写死在
        // Termux 前缀下、星辰目录里不存在，进程启动加载零根证书，任何 HTTPS 验真必挂
        // （视频机器人轮询 Telegram 报 self-signed certificate in certificate chain 即此）。
        // 统一下发指向运行时自带的 etc/tls/cert.pem；文件不在就不下发、保持原行为。
        // 放在包声明（var_path）之前：包自己声明了同名变量时以包的为准。
        String runtimeNorm = LabManifest.normalizeRuntime(runtime);
        if ("python".equals(runtimeNorm)) {
            for (Map.Entry<String, String> e : pythonCertVars(LabPaths.runtimeDir(c, runtimeNorm)).entrySet()) {
                env.put(e.getKey(), e.getValue());
            }
        }
        // 包声明环境变量（上游 Item.var_path，此前导入按 String 读、运行期也没人注入，
        // 整段是死的，G-5）：PATH 并入进程 PATH（相对段落包内、含 com.termux 的跳过），
        // 其余键作环境变量（相对值落包内、占位展开），LD_LIBRARY_PATH 前插不覆盖
        LabManifest mf = readManifestQuiet(c, pkgId);
        if (mf != null && !mf.varPath.isEmpty()) {
            File pkgDir = LabPaths.packageDir(c, pkgId);
            for (Map.Entry<String, String> e : mf.varPath.entrySet()) {
                String key = e.getKey();
                String value = e.getValue();
                if (key == null || value == null || value.isEmpty()) continue;
                if ("PATH".equals(key)) {
                    LinkedHashSet<String> merged = new LinkedHashSet<>();
                    String cur = env.get("PATH");
                    if (cur != null) for (String s : cur.split(":")) if (!s.isEmpty()) merged.add(s);
                    for (String part : value.split(":")) {
                        if (part.isEmpty() || part.contains("com.termux")) continue;
                        merged.add(part.startsWith("/") || part.contains("{")
                                ? expand(c, pkgId, part, 0) : new File(pkgDir, part).getAbsolutePath());
                    }
                    env.put("PATH", join(merged, ":"));
                    continue;
                }
                String v = value;
                if (!v.startsWith("/") && !v.startsWith("$")) v = new File(pkgDir, v).getAbsolutePath();
                v = expand(c, pkgId, v, 0);
                if ("LD_LIBRARY_PATH".equals(key)) {
                    String prevLd = env.get("LD_LIBRARY_PATH");
                    env.put(key, TextUtils.isEmpty(prevLd) ? v : v + File.pathSeparator + prevLd);
                } else {
                    env.put(key, v);
                }
            }
        }
        // 全局代理（实验室设置页）：开着就给进程 http_proxy/https_proxy/no_proxy
        if (LabSettings.proxyOn(c)) {
            String port = LabSettings.proxyPort(c);
            String proxy = "http://127.0.0.1:" + port;
            env.put("http_proxy", proxy);
            env.put("https_proxy", proxy);
            env.put("HTTP_PROXY", proxy);
            env.put("HTTPS_PROXY", proxy);
            String no = LabSettings.proxyNoProxy(c);
            if (!TextUtils.isEmpty(no)) {
                env.put("no_proxy", no);
                env.put("NO_PROXY", no);
            }
        }
    }

    /**
     * 单服务节点开关：该服务开了开关，启动命令时给进程注入本机节点代理
     * （默影视上游 applyGlobalProxy 口径：大小写变体＋已有值不覆盖＋mihomo 跳过）。
     * 变量清单：http_proxy/https_proxy/HTTP_PROXY/HTTPS_PROXY＝http://127.0.0.1:<混合端口>；
     * all_proxy/ALL_PROXY＝socks5://127.0.0.1:<混合端口+1>；no_proxy/NO_PROXY＝实验室设置里的例外值。
     * 地址取法照壳子现有口径（ClashSettings.localProxyUrl）。
     *
     * @return 节点开关开着但没注入时的人话说明（节点没在跑→直连启动，留行记录）；
     * 开关没开 / mihomo 服务本身 / 注入成功都返回 null。
     */
    static String applyNodeProxy(Context c, String pkgId, Map<String, String> env) {
        LabRegistry.Svc pkg = LabRegistry.get(c).svc(pkgId);
        if (!nodeProxyWanted(pkg)) return null;
        if (!MihomoManager.get().isRunning()) {
            return "「节点」开着，但节点还没跑，这次直连启动；等节点起来后下次启动就走节点";
        }
        int mixed = ClashSettings.getMixedPort(c);
        if (mixed <= 0) return null;
        for (Map.Entry<String, String> e : nodeProxyVars(mixed, LabSettings.proxyNoProxy(c)).entrySet()) {
            if (!env.containsKey(e.getKey())) env.put(e.getKey(), e.getValue());
        }
        return null;
    }

    /** 该服务是否要走节点：开了开关、且不是 mihomo 服务本身（防自环） */
    static boolean nodeProxyWanted(LabRegistry.Svc pkg) {
        if (pkg == null || !pkg.nodeProxy) return false;
        return !"mihomo".equals(pkg.runtime) && !"mihomo".equals(pkg.id);
    }

    /**
     * Python 运行时证书变量表（纯函数，便于单测）：运行时根目录按其实际布局推导
     * etc/tls/cert.pem，文件存在才给三件套（OpenSSL 本体读 SSL_CERT_FILE，
     * curl 系读 CURL_CA_BUNDLE，requests 系读 REQUESTS_CA_BUNDLE，同一个文件）；
     * 不存在返回空表，调用方不下发、保持原行为。
     */
    static Map<String, String> pythonCertVars(File runtimeDir) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (runtimeDir == null) return out;
        File cert = new File(new File(new File(runtimeDir, "etc"), "tls"), "cert.pem");
        if (!cert.isFile()) return out;
        String path = cert.getAbsolutePath();
        out.put("SSL_CERT_FILE", path);
        out.put("CURL_CA_BUNDLE", path);
        out.put("REQUESTS_CA_BUNDLE", path);
        return out;
    }

    /** 节点代理变量表（纯函数，便于单测）：混合端口 + socks 端口（混合+1）＋例外域名 */
    static Map<String, String> nodeProxyVars(int mixed, String noProxy) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        String http = "http://127.0.0.1:" + mixed;
        String socks = "socks5://127.0.0.1:" + (mixed + 1);
        out.put("http_proxy", http);
        out.put("https_proxy", http);
        out.put("HTTP_PROXY", http);
        out.put("HTTPS_PROXY", http);
        out.put("all_proxy", socks);
        out.put("ALL_PROXY", socks);
        if (!TextUtils.isEmpty(noProxy)) {
            out.put("no_proxy", noProxy);
            out.put("NO_PROXY", noProxy);
        }
        return out;
    }

    private static String join(Set<String> s, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String x : s) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(x);
        }
        return sb.toString();
    }

    private static void pump(Context c, String key, Process process, File log, Listener listener) {
        appendLog(key, "$ 启动\n");
        appendFile(log, "$ 启动\n");
        new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int n;
                while ((n = br.read(buf)) > 0) {
                    String s = new String(buf, 0, n);
                    appendLog(key, s);
                    appendFile(log, s);
                    // 实时输出页订的是总线：以前只有订阅没有发布，页面永远等不到新输出
                    LabLiveOutputActivity.LiveBus.emit(key, s);
                    if (listener != null) listener.onOutput(s);
                }
            } catch (Exception ignored) {
            }
            int code = -1;
            try {
                code = process.waitFor();
            } catch (Exception ignored) {
            }
            String end = "\n[进程退出，code=" + code + "]\n";
            appendLog(key, end);
            appendFile(log, end);
            LabLiveOutputActivity.LiveBus.emit(key, end);
            LabLiveOutputActivity.LiveBus.emitExit(key, code);
            RUNNING.remove(key);
            STDIN.remove(key);
            LabProc.untrack(c, key);
            LabKeepAlive.refresh(c);
            if (listener != null) listener.onExit(code);
        }).start();
    }

    private static void appendLog(String key, String s) {
        StringBuilder sb = LOGS.computeIfAbsent(key, k -> new StringBuilder());
        synchronized (sb) {
            sb.append(s);
            if (sb.length() > LOG_CAP) sb.delete(0, sb.length() - LOG_CAP);
        }
    }

    private static void appendFile(File log, String s) {
        try {
            if (log.getParentFile() != null) log.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(log, true);
                 OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                w.write(s);
            }
        } catch (Exception ignored) {
        }
    }

    // ---------------- 停止 ----------------

    public static void stop(Context c, String pkgId) {
        stopKey(c, keyOf(pkgId));
    }

    /** 停一个包的全部进程：服务键＋终端键＋它名下的全部命令键（含 pid 恢复的）。
     * 卸载/删包前用这个，只停服务键会把命令与终端进程留在被删的目录上继续跑。 */
    public static void stopPackage(Context c, String pkgId) {
        Set<String> keys = new LinkedHashSet<>(RUNNING.keySet());
        keys.addAll(LabProc.recoveredAliveKeys(c));
        String cmdPrefix = "cmd/" + pkgId + "/";
        for (String key : keys) {
            if (key.equals(keyOf(pkgId)) || key.equals("term/" + pkgId) || key.startsWith(cmdPrefix)) {
                stopKey(c, key);
            }
        }
        LabKeepAlive.refresh(c);
    }

    /**
     * 全部停止：按真实键逐个停（pkg/…、cmd/…、term/… 三类全停，外加靠 pid 文件
     * 恢复的进程）。此前经 pkgIdOf＋keyOf 往返拼键，把 cmd/term 键拼成
     * pkg/cmd/…、pkg/term/… 永远查不到，进程照跑、界面却报「都停了」。
     * 返回停完后仍在跑的条数，界面据此如实提示，停不干净不许报「都停了」。
     */
    public static int stopAll(Context c) {
        Set<String> keys = new LinkedHashSet<>(RUNNING.keySet());
        keys.addAll(LabProc.recoveredAliveKeys(c));
        for (String key : keys) stopKey(c, key);
        LabKeepAlive.refresh(c);
        return remainingRunningCount(c);
    }

    /** 还在跑的条数：内存表里真活着的＋pid 文件恢复后仍活着的（与 runningEntries 同口径） */
    public static int remainingRunningCount(Context c) {
        int n = 0;
        for (Map.Entry<String, Process> e : RUNNING.entrySet()) {
            if (LabProc.alive(e.getValue())) n++;
        }
        return n + LabProc.recoveredAliveKeys(c).size();
    }

    // ---------------- 日志 ----------------

    /** 每包一份自己的日志：内存＋落盘，留全 */
    public static String getLog(Context c, String pkgId) {
        String key = keyOf(pkgId);
        StringBuilder sb = LOGS.get(key);
        if (sb != null && sb.length() > 0) return sb.toString();
        File log = LabProc.logFile(c, pkgId);
        if (log.exists()) {
            try (FileInputStream in = new FileInputStream(log)) {
                byte[] buf = new byte[(int) Math.min(log.length(), 512 * 1024)];
                int n = in.read(buf);
                if (n > 0) return new String(buf, 0, n, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    public static void clearLog(Context c, String pkgId) {
        LOGS.remove(keyOf(pkgId));
        File log = LabProc.logFile(c, pkgId);
        if (log.exists()) log.delete();
    }

    // ---------------- 内部 ----------------

    private static String keyOf(String pkgId) {
        return "pkg/" + pkgId;
    }

    private static LabManifest readManifestQuiet(Context c, String pkgId) {
        try {
            return readManifest(c, pkgId);
        } catch (Exception e) {
            return null;
        }
    }

    private static LabManifest readManifest(Context c, String pkgId) throws Exception {
        File f = LabPaths.manifestOf(c, pkgId);
        if (!f.exists()) throw new Exception("包说明丢了，重装这个包试试");
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            // 存量包的 lab.json 可能没写 id：身份以注册表/目录名为准，派生兜底，
            // 不让一份缺 id 的说明把整个包读死（上游包本就没有 id 概念）
            return LabManifest.parse(new String(buf, 0, n, StandardCharsets.UTF_8), pkgId);
        }
    }
}
