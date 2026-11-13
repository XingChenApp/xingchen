package com.fongmi.android.tv.node;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.loader.CatSpider;
import com.github.catvod.crawler.SpiderDebug;

import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 猫源远端 bundle 的后台刷新：加载路只用已校验的本地缓存先行（见 {@link NodeBundle}），
 * 服务端有没有新版改由这里在后台静默跟进，全程不阻塞用户、不弹任何提示。
 *
 * <p>节奏刻意克制：同一个地址两次检查至少间隔 {@link #CHECK_INTERVAL_MS}（落盘计时，
 * 进程重启不重置），检查只发一轮（每文件单次尝试、8 秒超时，见
 * {@link NodeBundle#remoteMd5PairBackground}）；取不到校验值就当作没更新，
 * 失败也消耗本次周期——绝不重试叠加。检查时间在发请求**之前**就落盘，
 * 防止慢服务器/断网把检查拖成变相重试。
 *
 * <p>发现新版先下到 pending 区（{@link NodeBundle#stageRemoteUpdate}），再挑时机换上：
 * 没人用这个接口时下好即换（{@link NodeBundle#applyPending} + 必要时
 * {@link NodeRuntime#reloadQuietly} 静默重载引擎）；有人在用时起一个看守，
 * 每 {@link #WATCH_INTERVAL_MS} 复查一次，直到人退出/闲置再换，最长看守
 * {@link #WATCH_LIMIT_MS}——过期作罢，pending 留在盘上，下次该接口（重）启时
 * 由 {@code NodeBundle.ensure} 顺势换上，不丢。
 *
 * <p>「在用」的判定（{@link #inUseDecision}）：最近 {@link #PLAY_GUARD_MS} 内有过
 * 本机 bundle 的点播解析，视为可能正在播放（流可能经 bundle 代理，重启 :node
 * 会掐断它），无条件避让；否则看当前配置是否还是这个接口、且最近
 * {@link #ACTIVITY_QUIET_MS} 内有过浏览/搜索/详情动作。仅停在该接口首页长时间
 * 不动视为可换——已渲染的列表在内存里不受影响，下一次点击走既有探活拉起。
 */
public final class NodeRefresh {

    /** 同一地址两次后台检查的最小间隔：6 小时。bundle 更新本就稀少，这个节奏足够跟上。 */
    static final long CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000;
    /** 看守复查间隔与最长看守时长。 */
    static final long WATCH_INTERVAL_MS = 20_000L;
    static final long WATCH_LIMIT_MS = 2L * 60 * 60 * 1000;
    /** 浏览类动作的避让窗：最近 90 秒动过这个接口，就不在这一拍换包。 */
    static final long ACTIVITY_QUIET_MS = 90_000L;
    /** 点播避让窗：最近 2 小时内解析过播放入口，按「可能在看一部长片」避让。 */
    static final long PLAY_GUARD_MS = 2L * 60 * 60 * 1000;
    /** 一轮看守里真正尝试换包的上限：装包连续失败多半是盘的问题，留 pending 给下次启动处理。 */
    static final int MAX_APPLY_ATTEMPTS = 3;

    private static final String STAMP_NAME = "refresh.stamp";

    private static final Set<String> CHECKING = ConcurrentHashMap.newKeySet();
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // 看守状态（LOCK 保护）：同一时刻只看一份 pending（单 bundle 目录本来也只有一份）。
    private static Context watchContext;
    private static String watchUrl = "";
    private static long watchDeadline;
    private static int watchAttempts;
    private static String attemptKey = "";
    private static boolean watching;

    private NodeRefresh() {
    }

    /**
     * 安排一次后台检查（若到期）。调用点在服务就绪/复用命中时，都是主进程；
     * 本方法只做廉价判定就返回，真正的读盘校验与网络都在工作线程。
     */
    public static void maybeCheck(Context context, String url) {
        if (context == null || !NodeBundle.isRemote(url)) return;
        String id = NodeBundle.bundleUrl(url);
        if (id.isEmpty() || !CHECKING.add(id)) return;
        File dir = NodeBundle.dir(context);
        if (!due(dir, url)) {
            CHECKING.remove(id);
            return;
        }
        Context app = context.getApplicationContext();
        new Thread(() -> check(app, dir, url, id), "node-refresh").start();
    }

    private static void check(Context context, File dir, String url, String id) {
        try {
            // 无缓存可先行时不归后台管：首次添加/缓存损坏走前台完整流程，别在这里抢。
            if (!NodeBundle.hasVerifiedCache(dir, url)) return;
            // 先落检查时间再发请求：失败/超时同样消耗本周期，杜绝变相重试风暴。
            NodeBundle.writeStampText(dir, STAMP_NAME, NodeBundle.bundleUrl(url) + "\n" + System.currentTimeMillis());
            String[] digests = NodeBundle.remoteMd5PairBackground(url);
            String key = NodeBundle.remoteSourceKey(url, digests[0], digests[1]);
            if (TextUtils.isEmpty(key)) {
                SpiderDebug.log("node", "background refresh: md5 unavailable, treating as no update");
                return;
            }
            String installed = NodeBundle.installedSourceKey(context);
            if (key.equals(installed)) return;
            if (!NodeBundle.installedIsRemoteOf(installed, url)) return;
            if (!NodeBundle.stageRemoteUpdate(dir, url, digests[0], digests[1])) return;
            if (!tryApply(context, url)) startWatch(context, url);
        } catch (Throwable e) {
            SpiderDebug.log("node", "background refresh failed silently: %s", e.getMessage());
        } finally {
            CHECKING.remove(id);
        }
    }

    private static boolean due(File dir, String url) {
        String stamp = NodeBundle.readStampText(dir, STAMP_NAME);
        if (TextUtils.isEmpty(stamp)) return true;
        int newline = stamp.indexOf('\n');
        if (newline < 0 || !NodeBundle.bundleUrl(url).equals(stamp.substring(0, newline).trim())) return true;
        try {
            long last = Long.parseLong(stamp.substring(newline + 1).trim());
            return System.currentTimeMillis() - last >= CHECK_INTERVAL_MS;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * 尝试立刻把 pending 换上。
     *
     * @return true 表示本轮到此为止（已换上／无 pending／pending 已作废／尝试次数用尽）；
     * false 表示只是时机未到（有人在用／正在起进程），交给看守稍后再试
     */
    static boolean tryApply(Context context, String url) {
        synchronized (LOCK) {
            File dir = NodeBundle.dir(context);
            if (TextUtils.isEmpty(NodeBundle.pendingKey(dir))) return true;
            if (!NodeBundle.bundleUrl(url).equals(NodeBundle.pendingUrl(dir))) return true;
            String installed = NodeBundle.installedSourceKey(context);
            if (!NodeBundle.installedIsRemoteOf(installed, url)) {
                // 期间用户换了别的猫源：这份 pending 的地基（缓存目录）已易主，丢弃。
                NodeBundle.discardPending(dir);
                return true;
            }
            if (NodeRuntime.isStarting()) return false;
            if (inUse(url)) return false;
            String pendingKey = NodeBundle.pendingKey(dir);
            if (!pendingKey.equals(attemptKey)) {
                attemptKey = pendingKey;
                watchAttempts = 0;
            }
            if (watchAttempts >= MAX_APPLY_ATTEMPTS) return true;
            watchAttempts++;
            if (!NodeBundle.applyPending(dir, url)) return false;
            watchAttempts = 0;
            attemptKey = "";
            // 进程正跑着这个接口（只是人不在用）才需要重载让新代码生效；
            // 没在跑则下次启动自然读新文件，不必为换包专门起一趟进程。
            if (NodeRuntime.isServing(url)) NodeRuntime.reloadQuietly(context, url);
            return true;
        }
    }

    private static void startWatch(Context context, String url) {
        synchronized (LOCK) {
            watchContext = context.getApplicationContext();
            watchUrl = url;
            watchDeadline = SystemClock.elapsedRealtime() + WATCH_LIMIT_MS;
            if (watching) return;
            watching = true;
        }
        MAIN.postDelayed(NodeRefresh::tick, WATCH_INTERVAL_MS);
    }

    private static void tick() {
        Context context;
        String url;
        synchronized (LOCK) {
            if (!watching) return;
            context = watchContext;
            url = watchUrl;
            if (context == null || TextUtils.isEmpty(url) || SystemClock.elapsedRealtime() > watchDeadline) {
                watching = false;
                watchContext = null;
                watchUrl = "";
                return;
            }
        }
        boolean done;
        try {
            done = tryApply(context, url);
        } catch (Throwable e) {
            SpiderDebug.log("node", "pending watch failed silently: %s", e.getMessage());
            done = false;
        }
        synchronized (LOCK) {
            if (!watching) return;
            if (done) {
                watching = false;
                watchContext = null;
                watchUrl = "";
                return;
            }
        }
        MAIN.postDelayed(NodeRefresh::tick, WATCH_INTERVAL_MS);
    }

    /** 「在用」实时判定：拼当前配置与 CatSpider 的两个时间戳。纯判定抽在下面供单测。 */
    static boolean inUse(String url) {
        boolean configActive = false;
        try {
            String active = VodConfig.getUrl();
            configActive = !TextUtils.isEmpty(active)
                    && NodeBundle.bundleUrl(active).equals(NodeBundle.bundleUrl(url));
        } catch (Throwable ignored) {
            // 配置还没加载出来：按已离开处理，别让判定异常挡住换包。
        }
        long now = SystemClock.elapsedRealtime();
        return inUseDecision(configActive, since(CatSpider.lastLocalActivityAt(), now), since(CatSpider.lastLocalPlayAt(), now));
    }

    private static long since(long stamp, long now) {
        return stamp <= 0 ? Long.MAX_VALUE : now - stamp;
    }

    /**
     * 纯判定：点播避让无条件优先（哪怕已切走配置，后台/画中画可能还在播这个接口的流）；
     * 浏览避让只在「当前配置仍是这个接口」时成立——切走配置即视为已退出。
     */
    static boolean inUseDecision(boolean configActive, long sinceActivityMs, long sincePlayMs) {
        if (sincePlayMs < PLAY_GUARD_MS) return true;
        return configActive && sinceActivityMs < ACTIVITY_QUIET_MS;
    }
}
