package com.fongmi.android.tv.server;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.player.PlaybackRouteRegistry;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.Proxy;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Util;

public class Server {

    private volatile PlaybackService service;
    private volatile Nano nano;
    private volatile boolean manage;
    /** 停启代次：每次 start() 递增；stop() 入队时捕获当时的代次，任务真执行时
     * 代次已变说明期间又 start 过，这个排队中的 stop 必须放弃，不许停掉新服务。 */
    private volatile int generation;
    /** 起服务失败的已重试次数：端口被崩后残留进程占着时，限次重试等占位者消亡后自愈。 */
    private volatile int startRetries;
    private static final int START_RETRY_MAX = 12;
    private static final long START_RETRY_DELAY_MS = 5000;
    /** 是否已有一条重试链在排队：重试预算只由链本身按 5 秒一拍消耗，
     * 外部入口（管理页 15 秒 checker、touch 等）的失败调用只负责确保链在跑，
     * 不许再各自递增计数把播放路径的重试窗口提前耗尽。 */
    private volatile boolean retryPending;
    /** 预算耗尽的用户提示只发一次（成功起服后清旗，可再提示下一轮）。 */
    private volatile boolean startFailureNotified;
    /** 固定实例：App.post(runnable, delay) 按实例 removeCallbacks 去重，
     * 每次新建 this::start 方法引用会让去重失效、重试链可叠多条。 */
    private final Runnable retryStart = () -> {
        retryPending = false;
        start();
    };
    private PlaybackRouteRegistry.Registration routeRegistration;

    private static class Loader {
        static volatile Server INSTANCE = new Server();
    }

    public static Server get() {
        return Loader.INSTANCE;
    }

    public PlaybackService getService() {
        return service;
    }

    public void setService(PlaybackService service) {
        this.service = service;
    }

    public boolean isRunning() {
        return nano != null;
    }

    public String getAddress() {
        return getAddress(false);
    }

    public String getAddress(int tab) {
        return getAddress(false) + "?tab=" + tab;
    }

    public String getAddress(String path) {
        return getAddress(true) + path;
    }

    public String getAddress(boolean local) {
        return "http://" + (local ? "127.0.0.1" : Util.getIp()) + ":" + Proxy.getPort();
    }

    public synchronized void startManage() {
        manage = true;
        start();
    }

    public synchronized void stopManage() {
        manage = false;
        if (service == null) stop();
    }

    public synchronized void start() {
        // 无论本次是否真新建（已在跑时是空转），都算一次新的启动意图：
        // 递增代次，让此前排队中的 stop 任务执行时自行放弃。
        generation++;
        if (nano != null) {
            startRetries = 0;
            startFailureNotified = false;
            return;
        }
        for (int i = 9978; i < 9999; i++) {
            try {
                nano = new Nano(i);
                nano.start(500);
                Proxy.set(i);
                if (routeRegistration != null) routeRegistration.close();
                routeRegistration = PlaybackRouteRegistry.registerAppService(i, PlaybackRouteRegistry.AppOwner.MAIN_SERVER);
                break;
            } catch (Throwable e) {
                nano = null;
            }
        }
        if (nano != null) {
            startRetries = 0;
            startFailureNotified = false;
            return;
        }
        // 候选端口全被占（典型是崩后残留进程还占着本地服务端口）：此前这里静默
        // 放弃，Proxy 端口停在未起态，播放全线对着不存在的本地服务转圈假死且无
        // 任何日志。改成留痕＋限次重试：占位者被系统回收/退出后本进程能自愈，
        // 不必靠用户强退。重试有上限，长期被别家占满时不无限空转。
        SpiderDebug.log("server", "local server start failed: ports 9978-9998 busy, retry=%d/%d", startRetries, START_RETRY_MAX);
        if (retryPending) {
            // 已有重试链在排队：本次失败不另起链、不消耗预算。
            return;
        }
        if (startRetries < START_RETRY_MAX) {
            startRetries++;
            retryPending = true;
            App.post(retryStart, START_RETRY_DELAY_MS);
        } else if (!startFailureNotified) {
            // 预算耗尽：日志之外必须让用户看得见，否则管理页/局域网功能
            // 打不开时用户只面对一个死页面、没有任何说明。
            startFailureNotified = true;
            App.post(() -> Notify.show("本地服务启动失败：端口被占用，局域网功能暂时不可用"));
        }
    }

    public void stop() {
        final int gen = generation;
        Task.execute(() -> {
            synchronized (this) {
                // 代次已变＝排队期间又 start 过：放弃本次 stop，不许停新服务。
                if (gen != generation) return;
                if (manage || service != null) return;
                if (nano != null) nano.stop();
                nano = null;
                if (routeRegistration != null) routeRegistration.close();
                routeRegistration = null;
                // W7-04：停服必须连已排队的重试链一并摘掉——否则起服失败的
                // 重试期里 stop（nano 本为 null、无实事可做）后，5 秒一拍的
                // retryStart 照常触发 start()，服务在用户已退出后自行复活、
                // 甚至在退出后弹失败提示。清旗与计数后，下一轮 start 按全新
                // 预算重新起链（start 路径既有语义不动）。
                App.removeCallbacks(retryStart);
                retryPending = false;
                startRetries = 0;
                startFailureNotified = false;
                // 端口复位到未起态：catvod Proxy 的初始值就是 -1（set(-1) 即复位，
                // 不是置 0）；引用方口径已核：NsdDeviceDiscovery 以 getPort() <= 0
                // 守门、Server.getAddress／Proxy.getUrl 只拼字符串，与冷启未 start
                // 时拿到 -1 的既有行为一致，不引入新形态。
                Proxy.set(-1);
            }
        });
    }
}
