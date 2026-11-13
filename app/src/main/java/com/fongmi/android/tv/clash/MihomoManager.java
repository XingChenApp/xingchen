package com.fongmi.android.tv.clash;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
* mihomo 内核进程全生命周期：下载校验 → 生成配置 → 启停 → 健康检查 → 退出清理。
* <p>
*
* <ol>
* <li>开关是长期设置：{@link ClashSettings#setEnabled} 立刻写盘；
* <li>App 启动时按保存状态自动恢复——恢复点见「接入说明.md」：
* {@code App.startBackgroundServices()} 内后台线程调 {@link #bootRestore}；
* <li>{@link #bootRestore} 语义：开关为开 → 尝试拉起；拉起失败（内核未下载/
* 无可用节点/配置生成失败/健康检查超时/任何异常）→ 静默降级为直连，
* 并把开关置回关，不抛异常、不弹提示、不卡启动；
* <li>开关为关 → 什么都不做。
* </ol>
* <p>
* 用户手动拨开关走 {@link #setEnabled}：写盘后在后台线程启/停，
* 结果经 {@link Listener} 回调（回调在工作线程，UI 自己切主线程）。
*/
public final class MihomoManager {

private static final String TAG = "Mihomo";

/** 健康检查：本地口 TCP 可连上的最长等待。 */
private static final long HEALTH_TIMEOUT_MS = 10000;

public interface Listener {
void onStarted();

void onStopped();

/** 中文短句，可直接展示。 */
void onError(String msg);
}

private static final MihomoManager INSTANCE = new MihomoManager();

public static MihomoManager get() {
return INSTANCE;
}

private Process process;
private Listener listener;

private MihomoManager() {
}

public synchronized void setListener(Listener l) {
this.listener = l;
}

public synchronized boolean isRunning() {
return process!= null && process.isAlive();
}

/**
* 用户拨开关：持久化 + 后台启停。关 → 停内核；开 → 确保内核后启动。
* 内核未下载时先走下载流程（回调 onError/onStarted 告知结果）。
*/
public void setEnabled(final Context context, final boolean enabled) {
final Context app = context.getApplicationContext();
ClashSettings.setEnabled(app, enabled);
new Thread(() -> {
if (enabled) {
startWithKernelCheck(app);
} else {
stopSync();
fireStopped();
}
}, "clash-toggle").start();
}

/**
* App 启动恢复点（接入说明：App.startBackgroundServices 内后台线程调用）。
* <p>
* 开关为开 → 尝试拉起；任何失败 → 静默降级：开关置回关，不抛不弹。
* 开关为关 → 直接返回。本方法本身同步，调用方负责放后台线程。
*/
public void bootRestore(Context context) {
Context app = context.getApplicationContext();
if (!ClashSettings.isEnabled(app)) return;
try {
startSync(app);
} catch (Throwable t) {
Log.w(TAG, "boot restore failed, degrade to direct: " + t.getMessage());
// 失败必须留痕到文件日志：此前静默把开关置回关，他那边定向开关还亮着、
// 流量却全直连，只会得出「定向不生效」（2026-10-15 晚他实证）。
com.github.catvod.crawler.SpiderDebug.log("clash", "kernel boot-restore FAILED, switch set OFF: %s", String.valueOf(t));
try {
stopSync();
} catch (Throwable ignored) {
}
// 静默降级：开关置回关，下次启动不再尝试
ClashSettings.setEnabled(app, false);
}
}

private void startWithKernelCheck(final Context app) {
if (KernelDownloader.isReady(app)) {
try {
startSync(app);
fireStarted();
} catch (Throwable t) {
com.github.catvod.crawler.SpiderDebug.log("clash", "kernel start FAILED: %s", String.valueOf(t));
fireError(t.getMessage() == null? "内核启动失败": t.getMessage());
}
return;
}
KernelDownloader.ensure(app, new KernelDownloader.Listener() {
@Override
public void onProgress(long downloaded, long total) {
}

@Override
public void onDone(File binary) {
try {
startSync(app);
fireStarted();
} catch (Throwable t) {
com.github.catvod.crawler.SpiderDebug.log("clash", "kernel start after download FAILED: %s", String.valueOf(t));
fireError(t.getMessage() == null? "内核启动失败": t.getMessage());
}
}

@Override
public void onError(String msg) {
// 下载失败：开关保持开（用户意图），但内核没起来；
// 下次拨开关/重启 App 会重试；此处不自动关，避免用户困惑。
fireError(msg);
}
});
}

/**
* 按需静默拉起（音乐源开了「走节点」但总开关没开时用）：
* 已在跑直接返回 true；否则同步 startSync，失败返回 false。
* 不碰总开关持久化、不改「全部走节点」语义、不新造常驻——
* 内核停的时机沿用现有生命周期（用户拨总开关关时 stopSync）。
* 必须在后台线程调用（同步阻塞，含最长 10 秒健康检查）。
*/
public boolean ensureRunning(Context context) {
try {
if (isRunning()) return true;
startSync(context.getApplicationContext());
return isRunning();
} catch (Throwable t) {
com.github.catvod.crawler.SpiderDebug.log("clash", "kernel auto-start FAILED: %s", String.valueOf(t));
return false;
}
}

/** 同步启动：抛异常表示失败（调用方按规格处理）。 */
public synchronized void startSync(Context context) throws Exception {
Context app = context.getApplicationContext();
if (isRunning()) return;
File bin = KernelDownloader.bundledBinary(app);
if (bin == null) bin = KernelDownloader.binaryFile(app);
if (!bin.exists() ||!bin.canExecute()) throw new IllegalStateException("内核未下载");
File cfg = ConfigBuilder.write(app);
File dir = KernelDownloader.workDir(app);
ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath(), "-f", cfg.getAbsolutePath());
pb.directory(dir);
pb.redirectErrorStream(true);
// 子进程继承环境即可；不注入代理变量，避免内核自身流量回环
process = pb.start();
drainOutput(process);
int port = ClashSettings.getMixedPort(app);
if (!waitHealthy(port)) {
stopSync();
throw new IllegalStateException("内核健康检查超时");
}
Log.i(TAG, "mihomo started, mixed-port=" + port);
com.github.catvod.crawler.SpiderDebug.log("clash", "kernel started port=%d", port);
}

/**
* 排流：内核输出（stdout/stderr 已合并）必须有人持续读走。
* 没人读时管道缓冲攒满，内核写日志会阻塞、代理静默 stall 且无日志可查。
* 守护线程逐行读到 EOF（进程退出即收），顺手转进 logcat 留痕。
*/
private void drainOutput(Process p) {
Thread t = new Thread(() -> {
try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
String line;
while ((line = reader.readLine()) != null) {
Log.d(TAG, "kernel: " + line);
}
} catch (Throwable ignored) {
}
}, "mihomo-output-drain");
t.setDaemon(true);
t.start();
}

/** 同步停止：杀进程并清理。 */
public synchronized void stopSync() {
if (process!= null) {
try {
process.destroy();
try {
// 给 3 秒优雅退出，不行再强杀
long deadline = System.currentTimeMillis() + 3000;
while (process.isAlive() && System.currentTimeMillis() < deadline) {
Thread.sleep(100);
}
} catch (InterruptedException e) {
Thread.currentThread().interrupt();
}
if (process.isAlive()) process.destroyForcibly();
} catch (Throwable ignored) {
} finally {
process = null;
}
Log.i(TAG, "mihomo stopped");
}
}

private boolean waitHealthy(int port) {
long deadline = System.currentTimeMillis() + HEALTH_TIMEOUT_MS;
while (System.currentTimeMillis() < deadline) {
if (process!= null &&!process.isAlive()) return false;
try {
Socket s = new Socket();
s.connect(new InetSocketAddress("127.0.0.1", port), 800);
s.close();
return true;
} catch (Exception ignored) {
}
try {
Thread.sleep(400);
} catch (InterruptedException e) {
Thread.currentThread().interrupt();
return false;
}
}
return false;
}

/** 供 SourceProxyPolicy/代理决策层查询：本地混合口地址（未运行时返回空）。 */
public String localProxyUrl(Context context) {
if (!isRunning()) return "";
return ClashSettings.localProxyUrl(context);
}

private void fireStarted() {
Listener l;
synchronized (this) {
l = listener;
}
if (l!= null) {
try {
l.onStarted();
} catch (Throwable ignored) {
}
}
}

private void fireStopped() {
Listener l;
synchronized (this) {
l = listener;
}
if (l!= null) {
try {
l.onStopped();
} catch (Throwable ignored) {
}
}
}

private void fireError(String msg) {
Listener l;
synchronized (this) {
l = listener;
}
if (l!= null) {
try {
l.onError(msg);
} catch (Throwable ignored) {
}
}
}
}
