package com.fongmi.android.tv.utils;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.HkWebkitFetcher;
import com.fongmi.android.tv.cloud.drive115.Proxy115;
import com.fongmi.android.tv.playback.PlaybackRemoteSyncer;
import com.fongmi.android.tv.player.PlaybackMemoryMonitor;
import com.fongmi.android.tv.player.PlaybackSystemConditionMonitor;
import com.fongmi.android.tv.remote.RemoteAgent;
import com.github.catvod.crawler.SpiderDebug;

/**
 * 切后台全静默（账 bg-silence-land-20261114）：整应用退后台确认点
 * （App 的 700ms 去抖分发）统一收、回前台确认点统一放。
 *
 * <p>收的只是「周期发火」：观影记录同步的 5 分钟轮帖（触发收成
 * 一口、回前台补发）、远程代理会话的 4 秒轮询与长连、两台播放
 * 诊断采样的周期臂、115 代理的清理轮转、海阔网页取数在册
 * WebView；顺带放闲置——Glide 内存缓存与短剧池里无持有位的泊
 * 台。放行时各模块走各自既有入口续行（同步 resume 补发＋续帖、
 * 远程 start() 按配置重起、采样按在册会话重臂），登记与状态
 * 一律不注销不清零。</p>
 *
 * <p>红线：正在播的台与续播状态不在任何停口内——短剧当前/预
 * 备台在会话槽里持有、泊台差集逐个点名排除；后台音频播放本
 * 体（服务与播放器）不碰，被停的全是同步/轮询/诊断类杂事。</p>
 */
public final class BackgroundSilence {

    private static boolean installed;

    private BackgroundSilence() {
    }

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        App.addAppBackgroundListener(BackgroundSilence::onAppBackground);
        App.addAppForegroundListener(BackgroundSilence::onAppForeground);
    }

    private static void onAppBackground() {
        SpiderDebug.log("bg-silence", "silence on");
        run("remote-sync", PlaybackRemoteSyncer::suspend);
        run("remote-agent", () -> RemoteAgent.get().suspendSessions());
        run("memory-monitor", () -> PlaybackMemoryMonitor.process().setPeriodicSuspended(true));
        run("condition-monitor", () -> PlaybackSystemConditionMonitor.process().setPeriodicSuspended(true));
        run("proxy115", () -> {
            Proxy115 proxy = Proxy115.peekInstance();
            if (proxy != null) proxy.suspendCleaner();
        });
        run("webkit-fetcher", HkWebkitFetcher::destroyLiveOnBackground);
        // 次项·放闲置：图片内存缓存＋位图池（本分发点在主线程，
        // clearMemory 的线程要求正合）。磁盘缓存不动。
        run("glide-memory", () -> Glide.get(App.get()).clearMemory());
        // 次项·放闲置：短剧池泊台。类名拆字串＋反射同 App 的引擎
        // 预热点火口径（朋友版导出摘短剧本体时类不在、静默跳过）。
        run("parked-units", () -> {
            try {
                Class<?> layer = Class.forName("com.fongmi.android.tv."
                        + "hong" + "guo" + ".ui." + "Hg" + "PlayerLayer");
                Object instance = layer.getMethod("get").invoke(null);
                layer.getMethod("releaseParkedUnitsForBackground").invoke(instance);
            } catch (Throwable ignored) {
            }
        });
    }

    private static void onAppForeground() {
        SpiderDebug.log("bg-silence", "silence off");
        run("remote-sync", PlaybackRemoteSyncer::resume);
        run("remote-agent", () -> RemoteAgent.get().start());
        run("memory-monitor", () -> PlaybackMemoryMonitor.process().setPeriodicSuspended(false));
        run("condition-monitor", () -> PlaybackSystemConditionMonitor.process().setPeriodicSuspended(false));
        run("proxy115", () -> {
            Proxy115 proxy = Proxy115.peekInstance();
            if (proxy != null) proxy.resumeCleaner();
        });
        // Glide 缓存、取数 WebView、短剧泊台都是按需重建，无恢复动作。
    }

    /** 单件隔离：一件失败不许连坐其余件的停/续（与 App 听者分发
     * 的逐个 try 同口径，这里再包一层并留名打点）。 */
    private static void run(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable e) {
            SpiderDebug.log("bg-silence", "%s failed error=%s", name, e.getMessage());
        }
    }
}
