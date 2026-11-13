package com.fongmi.android.tv.cloud;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.cloud.drive115.Drive115;
import com.fongmi.android.tv.cloud.drivepikpak.DrivePikPak;
import com.fongmi.android.tv.cloud.drivequark.DriveQuark;
import com.fongmi.android.tv.utils.PanAuth;
import com.github.catvod.crawler.SpiderDebug;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 网盘账号信息抓取器：把三家「容量＋VIP」拉回来存进 PanAuth，
 * 供网盘登录页副标题与各网盘页顶部显示。
 *
 * 此前 PanAuth 的 put115Vip/putQuarkVip/putPikPakVip 全代码无人调用，
 * 三个取值恒空，副标题永远只剩「已登录」。这里是唯一的写入方。
 *
 * 缓存口径：登录/退出会把抓取时间戳清零（PanAuth 内），下次进页必重拉；
 * 拉成功后 6 小时内进页不重拉；拉失败把时间戳回拨成 5 分钟后过期，
 * 下次进页可再试。任何一家失败只影响自己，登录态与已有显示都不动。
 */
public final class PanAccountInfo {

    private static final long STALE_MS = 6 * 60 * 60 * 1000L;
    private static final long RETRY_MS = 5 * 60 * 1000L;
    private static final AtomicBoolean FETCHING = new AtomicBoolean(false);

    private PanAccountInfo() {
    }

    /** 需要时后台抓取；有任何一家更新了显示数据，回调切主线程执行 */
    public static void maybeFetch(Runnable onUpdated) {
        long now = System.currentTimeMillis();
        boolean need115 = PanAuth.is115LoggedIn() && stale(PanAuth.get115InfoTs(), now);
        boolean needQuark = PanAuth.isQuarkLoggedIn() && stale(PanAuth.getQuarkInfoTs(), now);
        boolean needPikPak = PanAuth.isPikPakLoggedIn() && stale(PanAuth.getPikPakInfoTs(), now);
        if (!need115 && !needQuark && !needPikPak) return;
        if (!FETCHING.compareAndSet(false, true)) return;
        new Thread(() -> {
            boolean updated = false;
            try {
                if (need115) updated |= fetch115();
                if (needQuark) updated |= fetchQuark();
                if (needPikPak) updated |= fetchPikPak();
            } finally {
                FETCHING.set(false);
            }
            if (updated && onUpdated != null) App.post(onUpdated);
        }, "pan-account-info").start();
    }

    private static boolean stale(long ts, long now) {
        return ts <= 0 || now - ts > STALE_MS;
    }

    /** 成功记当前时刻；失败回拨到「RETRY_MS 后即过期」，避免每次刷新都重打接口 */
    private static long attemptTs(boolean ok) {
        long now = System.currentTimeMillis();
        return ok ? now : now - (STALE_MS - RETRY_MS);
    }

    private static boolean fetch115() {
        Drive115 d = new Drive115();
        boolean ok = d.fetchAccountInfo();
        if (ok) {
            PanAuth.put115Vip(d.getVipText());
            PanAuth.put115Space(d.getSpaceText());
            SpiderDebug.log("panacct", "115 vip=%s space=%s", d.getVipText(), d.getSpaceText());
        }
        PanAuth.mark115InfoTs(attemptTs(ok));
        return ok;
    }

    private static boolean fetchQuark() {
        DriveQuark d = new DriveQuark();
        boolean ok = d.fetchAccountInfo();
        if (ok) {
            PanAuth.putQuarkVip(d.getVipText());
            String space = "";
            try {
                space = d.storageSummary();
            } catch (Exception ignored) {
            }
            PanAuth.putQuarkSpace(space);
            SpiderDebug.log("panacct", "quark vip=%s space=%s", d.getVipText(), space);
        }
        PanAuth.markQuarkInfoTs(attemptTs(ok));
        return ok;
    }

    private static boolean fetchPikPak() {
        DrivePikPak d = new DrivePikPak(App.get());
        boolean ok = d.fetchAccountInfo();
        if (ok) {
            PanAuth.putPikPakVip(d.getVipText());
            PanAuth.putPikPakSpace(d.getSpaceText());
            SpiderDebug.log("panacct", "pikpak vip=%s space=%s", d.getVipText(), d.getSpaceText());
        }
        // 用户名可能刚被 user/me 补上存档，PanAuth 的 PikPak 缓存要失效重读；
        // 注意 invalidatePikPakCache 会清信息时间戳，必须在 mark 之前调
        PanAuth.invalidatePikPakCache();
        PanAuth.markPikPakInfoTs(attemptTs(ok));
        return ok;
    }
}
