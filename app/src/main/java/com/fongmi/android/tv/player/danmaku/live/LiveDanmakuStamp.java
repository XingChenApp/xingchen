package com.fongmi.android.tv.player.danmaku.live;

/**
 * 直播弹幕装配时间戳口径（douyu-danmaku-stops-trace.md 第三节实证）：渲染层
 * DanmakuView 只激活「播放进度 −3000ms ～ 当前进度」窗内的条目（池按 timeMs
 * 分桶、copyRange 左开右闭）。直播弹幕是实时流、没有自己的时间轴，装配时必须
 * 把 timeMs 打成装配时刻的播放进度，条目才能当场落进激活窗；恒打 0 会在进度
 * 过 3 秒后永远落在窗外——数据照常进池、屏幕永远不出（1.4.24 真机实证）。
 * 网关路与平台路共用 PlayerManager.onLiveDanmakuBatch 装配，一处口径全覆盖。
 */
public final class LiveDanmakuStamp {

    private LiveDanmakuStamp() {
    }

    /** 装配时刻的播放进度即条目时间戳；未就绪的负值（TIME_UNSET 等）钳到 0。 */
    public static long timeMsAt(long playerPositionMs) {
        return Math.max(0L, playerPositionMs);
    }
}
