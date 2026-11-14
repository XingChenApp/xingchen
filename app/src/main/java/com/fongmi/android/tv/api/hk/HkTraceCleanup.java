package com.fongmi.android.tv.api.hk;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.fongmi.android.tv.read.ReadingBookmarkStore;
import com.fongmi.android.tv.read.ReadingStore;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 存量清理（2026-11-14 不留痕新规首启一次性）：新规之前，海阔借音乐播放
 * 器/阅读器播的小说、听书、音乐曾在对方自家存档里留下记录；新版首启把
 * 这些旧痕清掉，只清海阔来源、阅读/音乐自家来源的记录一字不动。
 *
 * <p>判据（全部用记录自带的身份字段，与写入侧同一把尺）：
 * <ul>
 *   <li>音乐最近播放/队列断点/收藏/歌单：曲目 platform == "hk"
 *       （HkMusicSource.PLATFORM，trackFor 造数时写死）；</li>
 *   <li>听书记录：kind == "AUDIO" 且 legadoKey 以 "hk:" 开头（海阔专辑
 *       legadoKey 造数口径）；</li>
 *   <li>小说/朗读记录：kind ∈ {NOVEL, TTS} 且 siteKey 空、legadoKey 空、
 *       localId 空、origin != "local"——与 ReadingRouter.openRecord 对记
 *       录的自家分类同口径（py 书有 siteKey、Legado 书有 legadoKey、本地
 *       书有 localId，海阔书三者皆空）。</li>
 * </ul>
 * 漫画（COMIC）记录不在新规点名面内（其进度住所本来就是 ReadingStore，
 * 海阔页内续读靠它），整类不动。一次性标记只在整轮跑完后落，半途失败
 * 下次启动重跑（清理本身幂等）。
 */
public final class HkTraceCleanup {

    private HkTraceCleanup() {
    }

    private static final String PREFS = "hk_notrace_cleanup";
    private static final String KEY_DONE = "v1_done";

    /** App.onCreate 主进程守卫后调用：未跑过才在后台跑一轮。 */
    public static void kick(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (sp.getBoolean(KEY_DONE, false)) return;
            new Thread(() -> {
                try {
                    // 核查 136 订正：完成标记只认整轮真跑完——runOnce 任一
                    // 段失败回 false，不落标记、下次启动重跑（清理幂等）。
                    if (runOnce()) sp.edit().putBoolean(KEY_DONE, true).apply();
                } catch (Throwable ignored) {
                    // 没跑完不落标记，下次启动重跑
                }
            }, "hk-trace-cleanup").start();
        } catch (Throwable ignored) {
        }
    }

    /** @return 四段是否全跑完；任一段抛错回 false（调用方据此不落完成标记）。 */
    private static boolean runOnce() {
        boolean ok = true;
        // —— 阅读侧 ——
        try {
            for (ReadingStore.Record r : ReadingStore.list()) {
                if (r == null || !isHkRecord(r)) continue;
                ReadingStore.remove(r);
                // 连带清该书的书签（书签店按书键挂靠，书没了签是孤儿）
                try {
                    ReadingBookmarkStore.removeBook(r.key());
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
            ok = false;
        }
        return ok;
    }

    /** 海阔来源记录判据（见类头注释，逐字对写入侧造数口径）。 */
    static boolean isHkRecord(ReadingStore.Record r) {
        if ("AUDIO".equals(r.kind)) {
            return r.legadoKey != null && r.legadoKey.startsWith("hk:");
        }
        if ("NOVEL".equals(r.kind) || "TTS".equals(r.kind)) {
            return TextUtils.isEmpty(r.siteKey)
                    && TextUtils.isEmpty(r.legadoKey)
                    && TextUtils.isEmpty(r.localId)
                    && !"local".equals(r.origin);
        }
        return false;
    }
}
