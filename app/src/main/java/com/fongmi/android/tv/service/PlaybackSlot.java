package com.fongmi.android.tv.service;

import com.fongmi.android.tv.App;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统三面（灵动岛／通知栏／锁屏）显示位归属账——全进程唯一一本（账
 * session-slot-follow-play-20261115）。同包同时登记着三四个媒体会话、
 * 三四条媒体通知，系统单槽挑中谁本不由「现在谁在播」决定，于是出现
 * 放视频显歌、看短剧显书、该显不显。本账把规矩收成一条：**显示位跟
 * 正在播的走**。
 *
 * 规矩：
 * - 认领 claim：任一源真起播（自家播放器报 isPlaying=true）即认领；
 *   上一任持位者降为候补（fallback），并收到让位通知。让位怎么做归各
 *   源自己（media3 两套走 removeSession 撤卡、短剧视频撤自家通知），
 *   本账只记归属、不碰任何源的内部件。
 * - 释放 release：只有持位者能释放（服务销毁／短剧台整清时调）；释放
 *   后候补收到回位通知，候补自查仍有内容才 claimPaused 回位——回不回
 *   由候补按自家真实状态裁，本账不替它顶卡。
 * - 注销 unregister：参与者销毁时摘登记；顺带清掉指向它的持位/候补。
 *
 * 通知一律经 App.post 到主线程发，调用方在任何线程认领/释放都安全。
 */
public final class PlaybackSlot {

    public enum Source {
        /** 音乐（音乐服务，通知 1004） */
        MUSIC,
        /** 主 PlaybackService（xc_video／通知 1001）：点播·直播·听书·TTS 共用 */
        MAIN
    }

    public interface Participant {
        /** 显示位被别的源认领：自家没在播就撤下自家那张面。 */
        void onSlotYielded();

        /** 持位者释放、且自家是候补：自查有内容才 claimPaused 回位。 */
        void onSlotReclaimable();
    }

    private static final Map<Source, Participant> PARTICIPANTS = new ConcurrentHashMap<>();
    private static volatile Source owner;
    private static volatile Source fallback;
    /** 媒体卡在岗事实（provider 真发出过该源的媒体通知）：与持位/候补同册——
     * 音乐服务原先自养的跨实例静态标记并入本账（2026-11-19 守卫扫除④ D24）。 */
    private static final java.util.Set<Source> MEDIA_CARD_POSTED = ConcurrentHashMap.newKeySet();

    private PlaybackSlot() {
    }

    public static Source owner() {
        return owner;
    }

    /** 显示位在别人手里（空位不算）。 */
    public static boolean ownedByOther(Source me) {
        Source o = owner;
        return o != null && o != me;
    }

    /**
     * 登记参与者。若登记时显示位已属别人，立即（主线程）给它发一次让位，
     * 堵住「服务在别人占位期间冷启、自家占位先顶上去」的口（音乐断点顶卡）。
     */
    public static void register(Source source, Participant participant) {
        if (source == null || participant == null) return;
        PARTICIPANTS.put(source, participant);
        if (ownedByOther(source)) App.post(participant::onSlotYielded);
    }

    /** 记/读某源的媒体卡在岗事实：置位只许在 provider 通知变更回调，清位在降前台与注销。 */
    public static void setMediaCardPosted(Source source, boolean posted) {
        if (source == null) return;
        if (posted) MEDIA_CARD_POSTED.add(source);
        else MEDIA_CARD_POSTED.remove(source);
    }

    public static boolean isMediaCardPosted(Source source) {
        return source != null && MEDIA_CARD_POSTED.contains(source);
    }

    public static void unregister(Source source, Participant participant) {
        if (source == null) return;
        // 在岗事实随登记一起摘：参与者销毁后事实自动归零，不再靠各服务在
        // onDestroy 里手工回置跨实例静态（音乐 H3 那次漏复位就是散养的账）
        MEDIA_CARD_POSTED.remove(source);
        if (participant != null) PARTICIPANTS.remove(source, participant);
        else PARTICIPANTS.remove(source);
        Participant reclaimable = null;
        synchronized (PlaybackSlot.class) {
            if (fallback == source) fallback = null;
            if (owner == source) {
                // 没先 release 就注销：按释放同规处理，候补照样收回位通知
                owner = null;
                Source fb = fallback;
                fallback = null;
                reclaimable = fb == null ? null : PARTICIPANTS.get(fb);
            }
        }
        if (reclaimable != null) App.post(reclaimable::onSlotReclaimable);
    }

    /** 真起播认领：上一任降候补并收让位通知；认领者若本是候补，候补位清掉。 */
    public static void claim(Source source) {
        if (source == null) return;
        Participant yielded;
        synchronized (PlaybackSlot.class) {
            if (owner == source) {
                if (fallback == source) fallback = null;
                return;
            }
            Source prev = owner;
            owner = source;
            if (fallback == source) fallback = null;
            if (prev != null) fallback = prev;
            yielded = prev == null ? null : PARTICIPANTS.get(prev);
        }
        if (yielded != null) App.post(yielded::onSlotYielded);
    }

    /**
     * 候补回位（没在播、只是把自家暂停面顶回来）：位空着才认，认上返回 true。
     * 调用方先 claimPaused 再顶卡，防释放与新认领交错时把卡顶错位。
     */
    public static boolean claimPaused(Source source) {
        if (source == null) return false;
        synchronized (PlaybackSlot.class) {
            if (owner != null) return false;
            owner = source;
            if (fallback == source) fallback = null;
            return true;
        }
    }

    /** 持位者释放：位归空，候补收回位通知（回不回它自裁）。 */
    public static void release(Source source) {
        if (source == null) return;
        Participant reclaimable;
        synchronized (PlaybackSlot.class) {
            if (owner != source) return;
            owner = null;
            Source fb = fallback;
            fallback = null;
            reclaimable = fb == null ? null : PARTICIPANTS.get(fb);
        }
        if (reclaimable != null) App.post(reclaimable::onSlotReclaimable);
    }
}
