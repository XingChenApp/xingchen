package com.fongmi.android.tv.hongguo.ui;

import com.fongmi.android.tv.player.engine.ExoPlayerEngine;

/**
 * 按 vid 的共享播放器池＋交接单（进程级单槽）：竖屏页进横屏时把
 * 播放器解绑入池（连同交接单：vid／集序号／进度／播放意愿／弹幕
 * 开关／倍速／剧名／内容类型），横屏页取走同一播放器对象续播——
 * 不重拉模型、不重开流、进度长在播放器里；退出横屏同路写回，
 * 竖屏页领回后按 DramaLandLogic.reclaimDecision 对位。
 *
 * <p>单槽：同一时刻只有一个持有者泊出。泊出者负责先摘自己的
 * Player.Listener 再入池，领走者加自己的 listener。池中引擎在被
 * 领走前归泊出方：泊出方销毁时不许 release 它。新泊覆盖旧泊时
 * 旧引擎由池释放。全部方法只在主线程调用。</p>
 */
public final class DramaPlayerPool {

    /** 交接单：随播放器一起过手的播放/UI 状态快照。 */
    public static final class Bag {
        public String seriesId;
        public String vid;
        public int episodeIndex;
        public long positionMs;
        public boolean playWhenReady;
        public boolean danmakuOn;
        public float speed = 1f;
        public String seriesTitle;
        public int contentType;
    }

    private static ExoPlayerEngine parkedEngine;
    private static Bag parkedBag;

    private DramaPlayerPool() {
    }

    /** 泊入：槽里已有未领走的引擎（异常路径）先释放，不许泄漏。
     * 空引擎泊入（B-20）：交接单与引擎必须成对——只写新单、留旧
     * 引擎，领走方会把旧引擎当新单的剧续播（错配）。空泊视同无
     * 引擎可交：旧泊引擎先释放清槽，只留新单（消费端对单在引擎
     * 空都有兜底：横屏页 finish、竖屏领回走 playVid 常路）。 */
    public static synchronized void park(Bag bag, ExoPlayerEngine engine) {
        if (engine == null) {
            if (parkedEngine != null) {
                try {
                    parkedEngine.release();
                } catch (Throwable ignored) {
                }
                parkedEngine = null;
            }
            parkedBag = bag;
            return;
        }
        if (parkedEngine != null && parkedEngine != engine) {
            try {
                parkedEngine.release();
            } catch (Throwable ignored) {
            }
        }
        parkedEngine = engine;
        parkedBag = bag;
    }

    /** 只看交接单、不领走（认领判定用）。 */
    public static synchronized Bag peekBag() {
        return parkedBag;
    }

    /** 领走引擎（交接单一并清掉）：没有泊出物时返回 null。 */
    public static synchronized ExoPlayerEngine takeEngine() {
        ExoPlayerEngine e = parkedEngine;
        parkedEngine = null;
        parkedBag = null;
        return e;
    }

    /** 认领失败/对不上剧：连引擎带交接单整清（引擎释放）。 */
    public static synchronized void releaseParked() {
        if (parkedEngine != null) {
            try {
                parkedEngine.release();
            } catch (Throwable ignored) {
            }
        }
        parkedEngine = null;
        parkedBag = null;
    }
}
