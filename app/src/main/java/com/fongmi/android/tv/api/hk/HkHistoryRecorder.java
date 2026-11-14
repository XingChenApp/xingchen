package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.setting.Setting;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 海阔历史记录器（2026-11-14 23:03/23:05 他亲定）：海阔打开过的一切（含借
 * 音乐播放器/阅读器播的小说、听书、音乐）都必须记在海阔自家历史里——借面
 * 只当调用、会话只活内存，进度与集序的唯一住所就是这张 History 表（站点段
 * hk_ 命名空间，与视频记录同表同键形）。
 *
 * <p>写入分工：记录由交接/分流处经 {@link #recordOpened} 建（不存在才建，
 * 已有只刷新展示字段与时间，绝不覆盖进度字段）；会话进行中的进度由借面存
 * 进度时经 {@link #updateProgress} 回写本表（借面自家存档对海阔内容零写入，
 * 见 MusicStore/ReadingStore 两侧的门）。position 的语义随品类：一说小说＝
 * 章内万分比、听书/音乐＝章/曲内毫秒，与 ReadingStore 旧口径一致，各恢复点
 * 按自家品类解读。
 *
 * <p>写串行：全部写入走同一条单线程队列按提交序落盘，避免「分流建记录」
 * 与「借面回写进度」并发交错出半成品；读（find）同步直查（VideoActivity
 * checkHistoryUi 同款主线程先例）。
 */
public final class HkHistoryRecorder {

    private HkHistoryRecorder() {
    }

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hk-history-recorder");
        t.setDaemon(true);
        return t;
    });

    /** 历史键：与 VideoActivity.getHistoryKey 同形（站点段@@@vodId@@@cid）。 */
    public static String keyOf(String resTitle, String vodId) {
        return HkDetailBridge.siteKey(resTitle)
                + AppDatabase.SYMBOL + (vodId == null ? "" : vodId)
                + AppDatabase.SYMBOL + VodConfig.getCid();
    }

    public static History findByKey(String key) {
        if (TextUtils.isEmpty(key)) return null;
        try {
            return History.find(key);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 按站点（解析名）＋条目名找一条（借面专辑不带历史键时的兜底定位）。 */
    public static History findBySiteAndName(String resTitle, String vodName) {
        if (TextUtils.isEmpty(resTitle) || TextUtils.isEmpty(vodName)) return null;
        try {
            String siteKey = HkDetailBridge.siteKey(resTitle);
            for (History h : History.getHk()) {
                if (siteKey.equals(h.getSiteKey()) && vodName.equals(h.getVodName())) return h;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 分流/交接处建记录：不存在才按给定字段建（position=0）；已存在只刷新
     * 名称/封面/线路与 createTime（顶到历史最前），集名/集址/position 三个
     * 进度字段一律不动——它们归 updateProgress 与恢复逻辑管。
     */
    public static void recordOpened(String key, String vodName, String vodPic, String flag,
                                    String episodeName, String episodeUrl) {
        if (TextUtils.isEmpty(key) || Setting.isIncognito()) return;
        WRITER.execute(() -> {
            try {
                History h = History.find(key);
                if (h == null) {
                    h = new History();
                    h.setKey(key);
                    h.setCid(VodConfig.getCid());
                    h.setVodName(vodName == null ? "" : vodName);
                    h.setVodPic(vodPic == null ? "" : vodPic);
                    h.setVodFlag(flag == null ? "" : flag);
                    h.setVodRemarks(episodeName == null ? "" : episodeName);
                    h.setEpisodeUrl(episodeUrl == null ? "" : episodeUrl);
                    h.setPosition(0);
                } else {
                    if (!TextUtils.isEmpty(vodName)) h.setVodName(vodName);
                    if (!TextUtils.isEmpty(vodPic)) h.setVodPic(vodPic);
                    if (!TextUtils.isEmpty(flag)) h.setVodFlag(flag);
                }
                h.setCreateTime(System.currentTimeMillis());
                h.save();
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * 进度回写（借面存进度时调用）：覆盖集名/集址/position 并刷新时间。
     * 记录不存在时按带入的展示字段补建（分流写若丢，这里兜底成形）；
     * 展示字段为空不覆盖已有值。
     */
    public static void updateProgress(String key, String vodName, String vodPic, String flag,
                                      String episodeName, String episodeUrl, long position) {
        if (TextUtils.isEmpty(key) || Setting.isIncognito()) return;
        WRITER.execute(() -> {
            try {
                History h = History.find(key);
                if (h == null) {
                    h = new History();
                    h.setKey(key);
                    h.setCid(VodConfig.getCid());
                    h.setPosition(0);
                }
                if (!TextUtils.isEmpty(vodName)) h.setVodName(vodName);
                if (!TextUtils.isEmpty(vodPic)) h.setVodPic(vodPic);
                if (!TextUtils.isEmpty(flag)) h.setVodFlag(flag);
                if (episodeName != null) h.setVodRemarks(episodeName);
                if (episodeUrl != null) h.setEpisodeUrl(episodeUrl);
                h.setPosition(Math.max(0, position));
                h.setCreateTime(System.currentTimeMillis());
                h.save();
            } catch (Throwable ignored) {
            }
        });
    }
}
