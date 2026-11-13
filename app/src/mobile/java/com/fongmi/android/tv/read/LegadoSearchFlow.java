package com.fongmi.android.tv.read;

import android.app.Activity;

import com.fongmi.android.tv.legado.LegadoAudio;
import com.fongmi.android.tv.legado.LegadoPipeline;
import com.fongmi.android.tv.legado.LegadoReading;
import com.fongmi.android.tv.legado.LegadoSource;
import com.fongmi.android.tv.legado.LegadoStore;
import com.fongmi.android.tv.ui.activity.AudioBookActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 书源搜书链（无弹窗版，1.3.50 起）：搜书入口统一打开搜索页，
 * 这里只留搜索页/目录页共用的纯逻辑——按类型取已启用源、源名点名、点章进阅读/听书。
 * 旧的三档弹窗反馈（showHits/failDialog/options 结果列表）已随搜索页上线整组删除，
 * 不许再加回来：搜书整条链只有搜索页一种呈现。
 */
public final class LegadoSearchFlow {

    private LegadoSearchFlow() {
    }

    /** 一个已启用源＋它入库时的原始 JSON（目录页重建源用，保证与入库解析一致）。 */
    public static final class Src {
        public final LegadoSource source;
        public final String json;

        Src(LegadoSource source, String json) {
            this.source = source;
            this.json = json;
        }
    }

    /**
     * 按类型取全部已启用且可跑的书源（调用方按页签传类型：小说→小说源，漫画→漫画源，
     * 各搜各的不混）。返回源＋原始 JSON，供搜索页并行搜、目录页重建。
     */
    public static List<Src> enabledSources(int bookSourceType) {
        List<Src> out = new ArrayList<>();
        try {
            for (LegadoStore.Entry e : LegadoStore.all()) {
                if (!e.enabled) continue;
                LegadoSource s = LegadoStore.parse(e.json);
                if (s == null) continue;
                if (s.bookSourceType != bookSourceType) continue;
                if (!LegadoSource.runnable(s.bookSourceType)) continue;
                out.add(new Src(s, e.json));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 源名字（搜索页失败点名用，空名兜底）。 */
    public static String sourceName(LegadoSource s) {
        if (s == null || s.bookSourceName == null || s.bookSourceName.isEmpty()) return "未知源";
        return s.bookSourceName;
    }

    /**
     * 点章 → 进阅读/听书（原 showChapters 点选体，原样搬迁；目录页点章用）。
     * 听书走听书播放器、小说漫画走阅读器，各是各的播放器只许调用不许混。
     */
    public static void openChapter(Activity activity, LegadoSource s,
                                   LegadoPipeline.BookInfo info,
                                   List<LegadoPipeline.TocChapter> chapters, int which) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (s.bookSourceType == LegadoSource.TYPE_AUDIO) {
            // 听书：专辑进听书播放页。
            LegadoAudio.AudioAlbum album = LegadoAudio.buildAlbum(s, info, chapters, which);
            String id = LegadoAudio.put(album);
            AudioBookActivity.start(activity, id);
        } else {
            ReadingSession session = LegadoReading.buildSession(s, info, chapters, which);
            ReadingRouter.openReading(activity, session);
        }
    }
}
