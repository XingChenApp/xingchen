package com.fongmi.android.tv;

import java.util.concurrent.TimeUnit;

public class Constant {

    public static final long INTERVAL_SEEK = TimeUnit.SECONDS.toMillis(10);
    public static final long INTERVAL_HIDE = TimeUnit.SECONDS.toMillis(5);
    public static final long TIMEOUT_VOD = TimeUnit.SECONDS.toMillis(30);
    // 动作链专用：动作结果即命令结果（解压大包等慢命令跑完才回页面/提示），
    // 与点播共用 30 秒会在命令真身还在跑时先报超时错。动作槽自有顶替机制
    // （新动作顶掉旧动作），放长不堵后续动作；点播链 TIMEOUT_VOD 不动。
    public static final long TIMEOUT_ACTION = TimeUnit.HOURS.toMillis(1);
    public static final long TIMEOUT_PUSH_DETAIL = TimeUnit.SECONDS.toMillis(75);
    public static final long TIMEOUT_LIVE = TimeUnit.SECONDS.toMillis(30);
    public static final long TIMEOUT_EPG = TimeUnit.SECONDS.toMillis(5);
    public static final long TIMEOUT_XML = TimeUnit.SECONDS.toMillis(15);
    public static final long TIMEOUT_PLAY = TimeUnit.SECONDS.toMillis(30);
    public static final long TIMEOUT_SYNC = TimeUnit.SECONDS.toMillis(2);
    public static final long TIMEOUT_SYNC_TRANSFER = TimeUnit.MINUTES.toMillis(10);
    public static final long TIMEOUT_SEARCH = TimeUnit.SECONDS.toMillis(15);
    public static final long TIMEOUT_PARSE_DEF = TimeUnit.SECONDS.toMillis(15);
    public static final long TIMEOUT_PARSE_SUPER = TimeUnit.SECONDS.toMillis(90);
    public static final long TIMEOUT_PARSE_WEB = TimeUnit.SECONDS.toMillis(30);
    public static final long TIMEOUT_PARSE_LIVE = TimeUnit.SECONDS.toMillis(10);
    public static final long HISTORY_TIME = TimeUnit.DAYS.toMillis(60);

    public static long getOpEdLimit(long duration) {
        if (duration < TimeUnit.MINUTES.toMillis(15)) return TimeUnit.MINUTES.toMillis(3);
        if (duration < TimeUnit.MINUTES.toMillis(30)) return TimeUnit.MINUTES.toMillis(6);
        return TimeUnit.MINUTES.toMillis(10);
    }
}
