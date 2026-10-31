package com.fongmi.android.tv.hongguo.book;

import com.fongmi.android.tv.service.PlaybackService;

/**
 * 红果听书专属播放服务（2026-10-31 W-15 拆套，他亲口「拆」）：
 * 与主 PlaybackService 同一套机制（PlaybackService/PlayerManager 本体、
 * Exo/MPV 引擎），但独立实例——自己的 PlayerManager 与底层播放器、
 * 媒体会话（xc_hgaudio）、通知（id 1005，主服务 1001、音乐 1002–1004
 * 已占）、导航回调槽与生命周期。红果听书与普通听书/视频/TTS 从此
 * 互不顶播、互不连坐回收；进度本来就分两份（HgAudioStore／
 * ReadingStore），本次一字未动。
 *
 * 非主服务：类级 running 旗、Server 单例注册、PlaybackEventCollector
 * 指针三处全局单槽只归主服务，本服务不碰（见基类 isPrimaryService 注）。
 */
public class HgAudioPlaybackService extends PlaybackService {

    /** 通知 id：1001 主服务、1002–1004 音乐服务已占，红果听书用 1005。 */
    private static final int NOTIFICATION_ID = 1005;

    @Override
    protected String sessionId() {
        return "xc_hgaudio";
    }

    @Override
    protected int notificationId() {
        return NOTIFICATION_ID;
    }

    @Override
    protected boolean isPrimaryService() {
        return false;
    }
}
