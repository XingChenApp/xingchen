package com.fongmi.android.tv.node;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.CatSource;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.crawler.SpiderDebug;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 猫源运行时：主进程侧的协调层。
 *
 * <p>真正的 Node 启动跑在 {@link NodeService}（{@code :node} 子进程）里。
 * 这里只负责：启动子进程 Service、接收子进程通过 Messenger 回报的进度/端口/错误、
 * 换源时杀旧子进程再起新子进程。
 *
 * <p>换源流程：如果已有子进程在跑且 bundle 不同，先 {@link NodeService#stop} 杀掉它
 * （Node/V8 随进程销毁干净），再 {@link NodeService#start} 起一个新的 :node 进程
 * 跑新 bundle。这绕过了 nodejs-mobile 的 {@code node::Start} 不可进程内重入的限制。
 */
public final class NodeRuntime {

    /** 首选端口，被占用时往后找；bundle 本身不做 EADDRINUSE 重试，所以由这边探。 */
    private static final int PREFERRED_PORT = 9988;
    private static final int PORT_SCAN = 20;

    /**
     * 猫源首次加载包含运行时检查、bundle 下载、Node 启动和 /config 探测，不能沿用普通接口的短预算。
     * 这些预算集中在运行时类中，保证主进程等待、子进程就绪和文件传输不会各自使用互相矛盾的时长。
     */
    public static final long START_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(12);
    static final long TRANSFER_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(3);
    static final long LIB_TRANSFER_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);
    static final long METADATA_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(8);
    static final long READY_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(3);
    static final long READY_PROBE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(2);
    static final long READY_POLL_MS = 200L;
    /**
     * 请求前探活失败后，同步等原 bundle 重启就绪的上限。走到这里时运行时与 bundle
     * 都已在机上（首次下载不在此列），正常几秒内就绪；超过这个预算按恢复失败处理，
     * 不让一次点击无限转圈。它只约束 {@link #ensureReady} 的轮询等待，单次 start
     * 本身的 12 分钟总预算（{@link #START_TIMEOUT_MS}）不因此被截断——超时后那轮
     * start 仍在后台继续，后续请求探活通过即可用。
     */
    private static final long REVIVE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(15);

    /** 探活专用客户端：与 NodeService.waitReady 的探测同口径（2 秒全超时、只认 /config 形状）。 */
    private static final OkHttpClient PROBE_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(READY_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(READY_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(READY_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(READY_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build();

    private static volatile int port;
    private static final AtomicBoolean STARTING = new AtomicBoolean(false);
    private static volatile boolean running;
    private static volatile String servingUrl = "";
    private static volatile String servingSourceKey = "";
    /** 每轮启动的世代号，用来丢弃旧子进程的延迟回报。 */
    private static final AtomicLong START_GENERATION = new AtomicLong();
    /** 本轮是否为后台静默重载：静默轮不弹任何用户可见提示（换包是后台的事，不惊动用户）。 */
    private static volatile boolean quietRound;

    /** 主进程侧的回复 Messenger，接收子进程回报。写在后台线程、读在主线程，必须 volatile。 */
    private static volatile Messenger replyMessenger;
    private static volatile NodeRuntime.Callback pendingCallback;

    public interface Callback {
        void onProgress(String message);

        void onReady(String baseUrl);

        void onError(String message);
    }

    private NodeRuntime() {
    }

    public static boolean isRunning() {
        return running && serviceAlive();
    }

    /**
     * 如果 :node 子进程已死（崩溃/被系统杀掉）但还在 serving，立刻用原 bundle 重启它。
     *
     * <p>崩溃时 {@link #running} 没有回调可置假，只能靠进程探活发现。调用方（如爬虫发现
     * 本机 bundle 连不上）检测到后调这里，把运行时恢复到可用状态，后续请求会走新端口。
     * 返回 true 表示本次调用发起了重启（或本来就健康无需动作）。
     */
    public static boolean restartIfDead(Context context) {
        if (running && serviceAlive()) return true;
        if (TextUtils.isEmpty(servingUrl)) return false;
        SpiderDebug.log("node", "restartIfDead: node process dead while serving %s, restarting", servingUrl);
        // 直接复用 start：崩溃分支会清 running/port 并重新拉起同一个 bundle。
        start(context, servingUrl, null);
        return true;
    }

    /**
     * 当前端口上的服务是否还认得出是猫源。与 {@code NodeService.waitReady} 同一款探法：
     * GET 本机 {@code /config}，按 {@link CatSource#isConfig} 的形状认——附带服务
     * （弹幕等）对 /config 回的 401 信封不算活，进程在但 bundle 服务已不答也不算活。
     */
    private static boolean probeReady() {
        int current = port;
        if (current <= 0) return false;
        try (Response response = PROBE_CLIENT.newCall(new Request.Builder()
                .url("http://127.0.0.1:" + current + "/config")
                .build()).execute()) {
            String cfg = response.body() == null ? "" : response.body().string();
            return CatSource.isConfig(cfg);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 请求前的同步探活兜底（点播/浏览共用，调用方在后台线程）：
     * 探活通过直接返回 {@code true}，只多一次本机回环 GET；
     * 探不活（:node 在「加载→点播」的时间差里被系统回收，或进程在但服务已不答）
     * 就用正在服务的原 bundle 走既有 start 流程强制重启一轮——跳过 start 的复用
     * 捷径，因为进程级存活证明不了服务在答——再在 {@link #REVIVE_TIMEOUT_MS} 内
     * 轮询探活等就绪。预算内没恢复返回 {@code false}，调用方按既有失败口径收口。
     * 已有 start 在飞（配置加载/换源）时不重复投递，只轮询等它落定。
     */
    public static boolean ensureReady(Context context) {
        if (probeReady()) return true;
        if (TextUtils.isEmpty(servingUrl)) return false;
        SpiderDebug.log("node", "ensureReady: cat server not answering on port %s, reviving %s", port, servingUrl);
        startInternal(context, servingUrl, null, true);
        long deadline = SystemClock.elapsedRealtime() + REVIVE_TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (probeReady()) return true;
            try {
                Thread.sleep(READY_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        boolean ready = probeReady();
        if (!ready) SpiderDebug.log("node", "ensureReady: revive not ready after %sms", REVIVE_TIMEOUT_MS);
        return ready;
    }

    /**
     * {@code :node} 子进程是否还活着。
     *
     * <p>{@link #running} 只在收到 READY 时置真、收到 ERROR/超时才置假：<b>子进程被系统或
     * 自身 OOM 杀掉时没有任何回调</b>，这个标志会一直停在「已就绪」。此后 {@link #start} 的
     * 复用捷径会一直把那个已经没人监听的死端口当成就绪服务返回，表现为「配置页加载不出来」
     * 且搜不出任何结果——用户只能杀掉应用重开。所以复用前必须实际核对进程是否还在。
     */
    private static boolean serviceAlive() {
        try {
            return isProcessRunning(App.get(), App.get().getPackageName() + ":node");
        } catch (Throwable e) {
            // 探测本身不可用时不能把功能一起判死：宁可退回旧行为（信任标志位）。
            return true;
        }
    }

    public static int port() {
        return port;
    }

    public static String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    public static String configUrl() {
        return "http://127.0.0.1:" + port + "/config";
    }

    /**
     * 启动运行时。同一个 bundle 已在跑则直接复用；换 bundle 则杀旧子进程再起新的。
     *
     * @param url 用户填的猫源地址（{@code .../index.js.md5}）
     */
    public static synchronized void start(Context context, String url, Callback callback) {
        startInternal(context, url, callback, false, false);
    }

    /**
     * 后台换包后的静默重载：新包已落盘、且已确认没人正在用这个接口时，
     * 用同一套杀旧起新把新代码跑起来，全程不弹任何用户可见提示，失败只记日志。
     * 绝不整 App 重启——代价只是一个 :node 子进程的重启。
     */
    public static synchronized void reloadQuietly(Context context, String url) {
        startInternal(context, url, null, false, true);
    }

    /** 是否有一轮启动正在飞（后台换包要避让：它自己的 ensure 会顺势用上新包）。 */
    static boolean isStarting() {
        return STARTING.get();
    }

    /** 当前是否正在服务这个 bundle 地址。 */
    static boolean isServing(String url) {
        return running && same(url);
    }

    /**
     * 条目被亲手删掉、且删的正是当前在服务的 bundle 时停掉 :node（收口形态照
     * {@link #timeout}）：已删来源不许在本进程里继续被复用或被探活复活。
     * 不是它在服务时什么都不动。
     */
    public static synchronized void stopIfServing(Context context, String url) {
        if (!running || !same(url)) return;
        START_GENERATION.incrementAndGet();
        STARTING.set(false);
        quietRound = false;
        NodeService.stop(context);
        running = false;
        servingUrl = "";
        servingSourceKey = "";
        port = 0;
        pendingCallback = null;
        replyMessenger = null;
    }

    /**
     * @param force 跳过复用捷径、强制走一遍杀旧起新的完整流程。{@link #ensureReady}
     *              探活已确认服务不答时用：进程级存活（{@link #serviceAlive}）过不了
     *              探活这一关，不能再用「进程在＋同一 bundle」把死服务当成就绪返回。
     */
    private static synchronized void startInternal(Context context, String url, Callback callback, boolean force) {
        startInternal(context, url, callback, force, false);
    }

    private static synchronized void startInternal(Context context, String url, Callback callback, boolean force, boolean quiet) {
        if (TextUtils.isEmpty(url)) {
            if (callback != null) callback.onError("未填写猫源地址");
            return;
        }
        // 复用要求「同一地址」且「来源身份仍与运行中的一致」：只比地址的话，本地包被改写后
        // 会继续跑旧 JS，所以本地包按内容指纹判定、内容没变就无需重启。远端不再实时比对
        // 服务端校验值（那是加载路上的网络等待，慢服务器能拖十几秒）：与磁盘上已安装的
        // 来源键比对即可，服务端更新由后台刷新换包后经安装键变化自然触发重启。
        // 还必须确认子进程仍在：它被杀掉时没有任何回调会把 running 置假。
        boolean alive = running && serviceAlive();
        if (!force && alive && same(url) && NodeBundle.servesCurrentSource(url, servingSourceKey, NodeBundle.installedSourceKey(context))) {
            NodeRefresh.maybeCheck(context, url);
            if (callback != null) callback.onReady(baseUrl());
            return;
        }
        if (running && !alive) {
            SpiderDebug.log("node", "node process is gone while runtime still marked running, restarting");
            running = false;
            servingSourceKey = "";
            port = 0;
        }
        if (!STARTING.compareAndSet(false, true)) {
            if (callback != null) callback.onError("正在启动中");
            return;
        }
        quietRound = quiet;
        long generation = START_GENERATION.incrementAndGet();
        pendingCallback = callback;
        replyMessenger = new Messenger(new ReplyHandler(generation));
        // 换源：先杀旧子进程（如果有），再起新的
        if (running || !TextUtils.isEmpty(servingUrl)) {
            SpiderDebug.log("node", "switching source: stopping old node service (was=%s)", servingUrl);
            if (!quiet) notifyProgress(context, callback, context.getString(R.string.node_stopping));
            NodeService.stop(context);
            // stopService 是异步的，onDestroy 里的 killProcess 也需要时间生效。
            // 不等的话 startForegroundService 会投递到还没死掉的旧进程，node::Start 第二次调用会 SIGSEGV。
            if (!waitForProcessDeath(context)) {
                // 扫描 O6：旧进程 3 秒没死透就绝不投递新启动——旧进程的
                // NodeService.nodeLaunched 已为 true，新 Intent 进不了 startNode、
                // 也不回 ERROR，本轮只能等满 12 分钟超时。改为本轮直接失败：
                // 状态保持旧值（旧进程仍在退出路上），下一次 start 会先等它真死。
                STARTING.set(false);
                quietRound = false;
                pendingCallback = null;
                replyMessenger = null;
                SpiderDebug.log("node", "abort switch: old :node process still alive after 3s");
                if (callback != null) callback.onError("旧猫源进程未退出，请重试");
                return;
            }
            running = false;
            servingSourceKey = "";
            port = 0;
            if (!quiet) notifyProgress(context, callback, context.getString(R.string.node_switch_restart));
        }
        servingUrl = NodeBundle.bundleUrl(url);
        servingSourceKey = "";
        try {
            NodeService.start(context, url, replyMessenger);
            App.post(() -> timeout(context, generation), START_TIMEOUT_MS);
        } catch (RuntimeException e) {
            STARTING.set(false);
            quietRound = false;
            running = false;
            servingUrl = "";
            servingSourceKey = "";
            port = 0;
            pendingCallback = null;
            replyMessenger = null;
            SpiderDebug.log("node", "failed to start node service: %s", e.getMessage());
            if (callback != null) callback.onError("猫源服务启动失败: " + e.getMessage());
        }
    }

    private static synchronized void timeout(Context context, long generation) {
        if (generation != START_GENERATION.get() || !STARTING.compareAndSet(true, false)) return;
        boolean quiet = quietRound;
        quietRound = false;
        // 先让当前 reply handler 失效：stopService 是异步的，旧 :node 仍可能在退出前发 READY。
        // 同时保留 servingUrl，让下一次 start 先等待这个旧进程真的结束，不能把重试投给旧 Service。
        START_GENERATION.incrementAndGet();
        NodeService.stop(context);
        running = false;
        servingSourceKey = "";
        port = 0;
        if (!quiet) {
            NodeNotify.done(context, "猫源启动失败：服务未在预期时间内就绪");
            Notify.show("猫源启动失败：服务未在预期时间内就绪");
        } else {
            SpiderDebug.log("node", "quiet reload timed out, keeping quiet");
        }
        Callback callback = pendingCallback;
        pendingCallback = null;
        replyMessenger = null;
        if (callback != null) callback.onError("服务未在预期时间内就绪");
    }

    /** 是否就是当前这个 bundle。 */
    private static boolean same(String url) {
        String requested = NodeBundle.bundleUrl(url);
        return !TextUtils.isEmpty(requested) && requested.equals(servingUrl);
    }

    /** 换源等主进程侧阶段进度：同时推通知栏和 callback。 */
    private static void notifyProgress(Context context, Callback callback, String text) {
        SpiderDebug.log("node", "%s", text);
        NodeNotify.progress(context, text, -1);
        App.post(() -> Notify.show(text));
        if (callback != null) callback.onProgress(text);
    }

    /**
     * 等 :node 子进程真正死掉，最多等 3 秒；返回 true 表示已确认死亡。
     * stopService / killProcess 都是异步的，不等就 startForegroundService 会复用旧进程导致 node::Start 重入崩溃。
     * 超时/被中断都算「没确认死」——调用方据此中止本轮启动（扫描 O6），不许照样投递。
     */
    private static boolean waitForProcessDeath(Context context) {
        String procName = context.getPackageName() + ":node";
        for (int i = 0; i < 30; i++) {
            if (!isProcessRunning(context, procName)) return true;
            try { Thread.sleep(100); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        SpiderDebug.log("node", "old :node process still alive after 3s");
        return false;
    }

    private static boolean isProcessRunning(Context context, String processName) {
        android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (android.app.ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
            if (processName.equals(info.processName)) return true;
        }
        return false;
    }

    /**
     * 主进程侧处理子进程通过 Messenger 回报的消息。
     *
     * <p>每轮 start 都带自己的 generation：旧 :node 进程可能在 waitForProcessDeath 的 3 秒后
     * 还活着并继续回报，若不校验就会把旧端口写进新一轮的 port/servingSourceKey，
     * 表现为「选了 B 源却连到 A 源」，同时把新一轮的 callback 吃掉。
     */
    private static class ReplyHandler extends Handler {
        private final long generation;

        ReplyHandler(long generation) {
            super(Looper.getMainLooper());
            this.generation = generation;
        }

        @Override
        public void handleMessage(Message msg) {
            if (generation != START_GENERATION.get()) {
                SpiderDebug.log("node", "ignoring stale node reply what=%d gen=%d current=%d", msg.what, generation, START_GENERATION.get());
                return;
            }
            NodeRuntime.Callback callback = pendingCallback;
            switch (msg.what) {
                case NodeService.MSG_READY: {
                    boolean quiet = quietRound;
                    quietRound = false;
                    port = msg.arg1;
                    running = true;
                    servingSourceKey = NodeBundle.installedSourceKey(App.get());
                    STARTING.set(false);
                    pendingCallback = null;
                    replyMessenger = null;
                    if (!quiet) {
                        String ready = App.get().getString(R.string.node_ready);
                        NodeNotify.done(App.get(), ready + "，端口 " + port);
                        Notify.show(ready);
                    }
                    // 服务已用（缓存）包跑起来：顺势安排一次后台校验刷新（内部有节流，几乎零成本）。
                    NodeRefresh.maybeCheck(App.get(), servingUrl);
                    if (callback != null) callback.onReady(baseUrl());
                    break;
                }
                case NodeService.MSG_PROGRESS: {
                    String text = msg.getData() != null ? msg.getData().getString("text") : "";
                    if (!quietRound) NodeNotify.progress(App.get(), text, -1);
                    if (callback != null) callback.onProgress(text);
                    break;
                }
                case NodeService.MSG_ERROR: {
                    boolean quiet = quietRound;
                    quietRound = false;
                    String text = msg.getData() != null ? msg.getData().getString("text") : "未知错误";
                    STARTING.set(false);
                    running = false;
                    servingSourceKey = "";
                    pendingCallback = null;
                    replyMessenger = null;
                    if (!quiet) {
                        NodeNotify.done(App.get(), "猫源启动失败：" + text);
                        Notify.show("猫源启动失败：" + text);
                    }
                    if (callback != null) callback.onError(text);
                    break;
                }
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * 从首选端口往后找一个能绑上的。探测用的 socket 立刻关闭，与 Node 真正 bind 之间
     * 存在极小的竞争窗口——所以最终仍以落盘的实际端口为准，探测只是让端口尽量可预期。
     * 全部占用时返回 0，让 bundle 自己取随机端口。
     */
    static int freePort() {
        for (int candidate = PREFERRED_PORT; candidate < PREFERRED_PORT + PORT_SCAN; candidate++) {
            try (java.net.ServerSocket socket = new java.net.ServerSocket()) {
                socket.setReuseAddress(true);
                socket.bind(new java.net.InetSocketAddress("127.0.0.1", candidate));
                return candidate;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    /** 引导脚本落盘的候选端口，逗号分隔，猫源那个（我们指定的）在最前。兼容只有单个端口的旧格式。 */
    static List<Integer> readPorts(File file) {
        List<Integer> ports = new ArrayList<>();
        if (!file.exists()) return ports;
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            byte[] buf = new byte[128];
            int len = in.read(buf);
            if (len <= 0) return ports;
            for (String part : new String(buf, 0, len).trim().split(",")) {
                try {
                    int value = Integer.parseInt(part.trim());
                    if (value > 0 && !ports.contains(value)) ports.add(value);
                } catch (NumberFormatException ignored) {
                }
            }
            return ports;
        } catch (Exception ignored) {
            return ports;
        }
    }
}
