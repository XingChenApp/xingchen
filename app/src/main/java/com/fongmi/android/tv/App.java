package com.fongmi.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.playback.PlaybackRemoteSyncer;
import com.fongmi.android.tv.player.PlaybackMemoryMonitor;
import com.fongmi.android.tv.player.PlaybackSystemConditionMonitor;
import com.fongmi.android.tv.player.mpv.PlaybackRecoveryMonitor;
import com.fongmi.android.tv.remote.RemoteAgent;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.AdBlock;
import com.fongmi.android.tv.utils.DanmakuSearchListFocusFixer;
import com.fongmi.android.tv.utils.NsdDeviceDiscovery;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PreviousProcessExitLogger;
import com.fongmi.android.tv.receiver.RuntimeReceiver;
import com.fongmi.android.tv.utils.RuntimeInstaller;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.hook.Hook;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.Init;
import com.google.gson.Gson;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static volatile App instance;

    private final Handler handler;
    private final Gson gson;
    private final long time;

    private volatile Activity activity;
    private Hook hook;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public static App get() {
        return instance;
    }

    // Application 构造前被触发的静态入口兜底（其他进程的 Provider 抢先、工具直调等）：
    // 此前全是 get().xxx 直调，get() 为 null 即裸 NPE、无从诊断（S5-12）。
    private static final Gson FALLBACK_GSON = new Gson();
    private static final Handler FALLBACK_HANDLER = HandlerCompat.createAsync(Looper.getMainLooper());

    private static Handler handler() {
        App app = get();
        return app == null ? FALLBACK_HANDLER : app.handler;
    }

    public static Gson gson() {
        App app = get();
        return app == null ? FALLBACK_GSON : app.gson;
    }

    public static long time() {
        App app = get();
        return app == null ? System.currentTimeMillis() : app.time;
    }

    public static Activity activity() {
        App app = get();
        return app == null ? null : app.activity;
    }

    public static void post(Runnable runnable) {
        handler().post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        handler().removeCallbacks(runnable);
        if (delayMillis >= 0) handler().postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        handler().removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) handler().removeCallbacks(r);
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (PlaybackRecoveryMonitor.isRecoveryProcess(base)) return;
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // 进程守卫一刀切落到全套（S5-07）：此前只有后台服务的投递点按主进程守，
        // 守卫之前的监听/观察/设置一套（ThemeManager、AiActivityTracker、内存/系统
        // 监控、插件目录 watch 等）在崩溃页进程（:error_activity）里照样全跑。
        // 口径与 startBackgroundServices 的注释一致：非主进程什么都不起，
        // 只留 Application 本体供系统在该进程拉起页面（崩溃页/恢复页）。
        if (!PlaybackRecoveryMonitor.isMainProcess(this)) return;
        // 海阔 LitePal 存储层照官接法（①团记名）：官 ui.Application.onCreate:130-131 为
        // LitePal.initialize(context) 后 LitePal.getDatabase().disableWriteAheadLogging()，
        // 官 Application 继承的是 android.app.Application（非 LitePalApplication）；
        // 咱家等价接进本类 onCreate 主进程守卫之后、其余初始化之前（记名接点）。
        // 配置走 assets/litepal.xml（本树已有且与官逐字同：dbname=hiker、version=54、14 映射）。
        org.litepal.LitePal.initialize(this);
        org.litepal.LitePal.getDatabase().disableWriteAheadLogging();
        // 实验室自启抢在已选配置首读之前：配置首读在 HomeActivity.initConfig，
        // 比这里晚得多；已选配置若是实验室服务的本地回环地址（如样本服务
        // http://127.0.0.1:<分配端口>/），服务没拉起来首读必然连不上、每次冷启
        // 必弹「配置取得失败」。实验室在 mobile 源集，主源集不能直引（leanback
        // 无此类），照 AiActionExecutor／NodeLabInfo 的反射口径调 LabBoot.kick；
        // 实验室已无总开关（2026-10-29 摘除），拉不拉由 LabBoot 按每个服务
        // 自身的自启标记（autoStartOf）决定，类不在时静默跳过。
        try {
            Class.forName("com.fongmi.android.tv.ui.lab.LabBoot")
                    .getMethod("kick", Context.class)
                    .invoke(null, this);
        } catch (Throwable ignored) {
        }
        try { com.xingchen.tv.theme.ThemeManager.init(this); } catch (Exception e) { SpiderDebug.log("startup", "ThemeManager init failed error=%s", e.getMessage()); SpiderDebug.log("startup", e); android.util.Log.e("App", "ThemeManager init failed, theme features unavailable this session", e); }
        try { com.fongmi.android.tv.ai.AiActivityTracker.init(this); } catch (Exception e) { SpiderDebug.log("startup", "AiActivityTracker init failed error=%s", e.getMessage()); SpiderDebug.log("startup", e); android.util.Log.e("App", "AiActivityTracker init failed, AI activity tracking unavailable this session", e); }
        PlaybackMemoryMonitor.process().initialize(this);
        PlaybackSystemConditionMonitor.process().initialize(this);
        Setting.applyLanguage();
        DebugLogStore.restoreEnabled();
        if (DebugLogStore.isEnabled()) {
            PlaybackRecoveryMonitor.logPreviousResult(this);
            Setting.logDebugEnvironment("restore");
            PreviousProcessExitLogger.log(this);
        }
        Notify.createChannel();
        ProxySetting.apply();
        AdBlock.refresh(this);
        DanmakuSearchListFocusFixer.start();
        registerActivityLifecycleCallbacks(this);
        // 8.83 本体 ActivityManager 同批登记：聚阅插件（hikerPop.js）经
        // ActivityManager.getInstance().getCurrentActivity() 取当前页，
        // 栈靠这份登记喂（类在主源集 com.example.hikerview.ui）。
        registerActivityLifecycleCallbacks(com.example.hikerview.ui.ActivityManager.getInstance());
        // 上游 App 同位置：启动即登记运行时包安装／卸载广播，RuntimeModule.refresh()
        // 靠它感知外置包变化；ModuleViewModel 各自的接收器只跟自己那块模块。
        RuntimeReceiver.register(this);
        // 海阔图片毒缓存一次性清理：跨版本首次启动清 hiker files 根的 cache/，
        // 内部自吞异常＋后台执行，不影响启动（见 HkCachePurge 类头定案说明）。
        try { com.fongmi.android.tv.api.hk.HkCachePurge.onStartup(); } catch (Throwable ignored) {}
        try {
            com.fongmi.android.tv.api.loader.BaseLoader.get()
                    .watchPyPlugins(new java.io.File(getFilesDir(), "plugins/py"));
        } catch (Throwable e) { SpiderDebug.log("startup", "watch py plugins failed error=%s", e.getMessage()); SpiderDebug.log("startup", e); android.util.Log.e("App", "watchPyPlugins failed, PY plugin changes will not be auto-detected this session", e); }
        try {
            com.fongmi.android.tv.api.loader.BaseLoader.get()
                    .watchJsPlugins(new java.io.File(getFilesDir(), "plugins/js"));
        } catch (Throwable e) { SpiderDebug.log("startup", "watch js plugins failed error=%s", e.getMessage()); SpiderDebug.log("startup", e); android.util.Log.e("App", "watchJsPlugins failed, JS plugin changes will not be auto-detected this session", e); }
        // 后台服务全家只许主进程起：崩溃页进程（:error_activity）此前无守卫，
        // 会在崩溃页进程里再起一整套（本地服务/RemoteAgent/节点内核/Python 预热），
        // 主进程崩后残留占位，重新拉起时播放全线对着残留状态假死、须强退才恢复。
        // 恢复页进程已有 isRecoveryProcess 守卫，这里按主进程一刀切补齐。
        if (PlaybackRecoveryMonitor.isMainProcess(this)) post(this::startBackgroundServices, 1200);
        // 定音心跳（账 activity-destroy-cause-20261107 §五.4）：进程级时间尺。
        startHeartbeat();
    }

    /** 应用级定音打点（账 activity-destroy-cause-20261107 §五）：util.Log 与导出通道（[xc-app]）双写，只记日志、绝不影响行为。 */
    private static void appLog(String msg) {
        android.util.Log.i("XcApp", msg);
        try {
            SpiderDebug.log("xc-app", "%s", msg);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 进程冻结间隙心跳（§五.4）：守护线程每 2 秒一行 tick（elapsedRealtime
     * ＋墙钟）；进程被厂商冻结时 tick 停摆、解冻后间隔自动显形，间隔超 5 秒
     * 另补一行 tick gap，把冻结窗钉到秒级——与单页 destroy 打点对位即可判
     * 读销毁发生在冻结前还是解冻拍（113 那场只能靠 av-diag 序号反推冻结窗）。
     */
    private void startHeartbeat() {
        Thread t = new Thread(() -> {
            long last = android.os.SystemClock.elapsedRealtime();
            while (true) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }
                long now = android.os.SystemClock.elapsedRealtime();
                long gap = now - last;
                last = now;
                if (gap > 5000) appLog("tick gap " + gap + "ms");
                appLog("tick elapsed=" + now
                        + " wall=" + System.currentTimeMillis());
            }
        }, "xc-heartbeat");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onTrimMemory(int level) {
        // 定音打点（§五.2）：level 本体逐发入日志——销毁发生而 trim 一次
        // 没来，即与内存彻底脱钩；来过则 level 对得上是不是低内存档位。
        // 必须打在 App 本体：现行 monitor 样本通道不覆盖短剧会话（113 本
        // 窗零样本），只打那侧等于没打。
        appLog("trim level=" + level);
        if (!PlaybackRecoveryMonitor.isRecoveryProcess(this)) PlaybackMemoryMonitor.process().onTrimMemory(level);
        super.onTrimMemory(level);
    }

    @Override
    public void onLowMemory() {
        if (!PlaybackRecoveryMonitor.isRecoveryProcess(this)) PlaybackMemoryMonitor.process().onLowMemory();
        super.onLowMemory();
    }

    private void startBackgroundServices() {
        // 双保险：投递点已按主进程守卫，这里再挡一次，非主进程绝不起后台全家。
        if (!PlaybackRecoveryMonitor.isMainProcess(this)) return;
        SpiderDebug.log("startup", "background services start cost=%sms", System.currentTimeMillis() - time);
        Server.get().start();
        PlaybackRemoteSyncer.start();
        RemoteAgent.get().start();
        NsdDeviceDiscovery.register();
        Task.execute(() -> {
            try {
                com.fongmi.chaquo.Loader.warmUp();
            } catch (Throwable ignored) {}
        });
        // 节点开关是长期设置：开着退出再进，后台自动把内核拉起来；
        // 拉起失败静默降级为直连并把开关置回关，不卡启动。
        Task.execute(() -> com.fongmi.android.tv.clash.MihomoManager.get().bootRestore(getApplicationContext()));
        SpiderDebug.log("startup", "background services ready cost=%sms", System.currentTimeMillis() - time);
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity != activity()) this.activity = activity;
        // 上游 App.onActivityResumed 同段：活动恢复时续跑外置运行时安装确认／卸载。
        // Updater.notifyModules 是朋友版只查版的注入位：正式版 Updater 已全摘
        // 成空壳（零检测更新，2026-11-11 定案），此调用在正式版无行为。
        RuntimeInstaller.resumeUpdate(activity);
        Updater.create().notifyModules();
        // 迷你条：页恢复时按白名单收口刷新（音乐只在音乐首页、听书只在阅读页）
        try {
            com.fongmi.android.tv.ui.minibar.ListenMiniBar.get(this)
                    .onActivityResumed(activity);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        try {
            com.fongmi.android.tv.ui.minibar.ListenMiniBar.get(this)
                    .onActivityPaused(activity);
        } catch (Throwable ignored) {
        }
    }

    @Override
        public void onActivityCreated(@NonNull Activity activity, android.os.Bundle savedInstanceState) {
        try {
            android.view.View v = activity.findViewById(android.R.id.content);
            if (v != null) v.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        } catch (Exception e) {}
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    // 整应用前后台计数（定音打点 §五.5）：started 归零延迟一拍才记
    // background——配置变更的停/起对（旋转重建）不许误报成切后台，
    // 与 ProcessLifecycleOwner 的去抖同义（报告原案许复用现有
    // lifecycle 回调计数，本类已注册 ActivityLifecycleCallbacks）。
    private int startedActivityCount;
    private boolean appForegroundLogged;

    // 整应用退后台听者（退后台在 appBackgroundLogTask 的去抖
    // 确认点统一分发）：需要会话收口的模块挂这里——计数只认
    // 本类进程起的这一本，模块自起一本中途注册的计数会被注
    // 册前已 started 的页面压成负基线，不许另起（行为
    // 记录批复核 B2）。
    private static final java.util.List<Runnable> appBackgroundListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addAppBackgroundListener(Runnable listener) {
        if (listener != null) appBackgroundListeners.add(listener);
    }

    private final Runnable appBackgroundLogTask = () -> {
        if (startedActivityCount == 0 && appForegroundLogged) {
            appForegroundLogged = false;
            appLog("app background");
            for (Runnable listener : appBackgroundListeners) {
                try {
                    listener.run();
                } catch (Throwable ignored) {
                }
            }
        }
    };

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (++startedActivityCount == 1) {
            handler.removeCallbacks(appBackgroundLogTask);
            if (!appForegroundLogged) {
                appForegroundLogged = true;
                appLog("app foreground");
            }
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (--startedActivityCount == 0) {
            handler.postDelayed(appBackgroundLogTask, 700);
        }
    }
}
