package com.fongmi.android.tv.read;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Episode;
import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 详情级改道支撑（不漏认两层）。
 * <p>
 * 源级标记 / 详情级判别命中后，由 VideoActivity 在详情打开时调用：
 * 本文件只做纯数据准备（章节表组装、听书守卫），kind 分辨与会话组装走
 * {@link ReadingManual#probeSession}（与手动入口同一探针分发：只取解析产出，
 * 不 fetch、不写缓存，先自动口径再放宽口径）。
 */
public class ReadingDetailDivert {

    private ReadingDetailDivert() {
    }

    /** 章节 url 里的音频扩展名（听书源守卫：听书也用"第X章"命名，不进阅读改道）。 */
    private static final String[] AUDIO_EXTS = {
            ".mp3", ".m4a", ".aac", ".ogg", ".oga", ".wav", ".flac", ".opus", ".wma", ".amr"
    };

    private static void log(String fmt, Object... args) {
        try {
            SpiderDebug.log("reading-nomiss", fmt, args);
        } catch (Throwable ignored) {
        }
    }

    /** 从选集表取章节名表（判别用）。 */
    public static List<String> chapterNames(List<Episode> episodes) {
        List<String> names = new ArrayList<>();
        if (episodes == null) return names;
        for (Episode e : episodes) {
            if (e != null) names.add(e.getName() == null ? "" : e.getName());
        }
        return names;
    }

    /** 从选集表组装章节表。 */
    public static List<ReadingSession.Chapter> toChapters(List<Episode> episodes) {
        List<ReadingSession.Chapter> chapters = new ArrayList<>();
        if (episodes == null) return chapters;
        for (Episode e : episodes) {
            if (e == null) continue;
            String name = e.getName() == null ? "" : e.getName();
            String url = e.getUrl() == null ? "" : e.getUrl();
            if (!TextUtils.isEmpty(name) || !TextUtils.isEmpty(url)) {
                chapters.add(new ReadingSession.Chapter(name, url));
            }
        }
        return chapters;
    }

    /** 任一章节地址是否音频文件（听书源守卫）。 */
    public static boolean hasAudioChapterUrl(List<ReadingSession.Chapter> chapters) {
        if (chapters == null) return false;
        for (ReadingSession.Chapter c : chapters) {
            if (c != null && isAudioUrl(c.url)) {
                log("skip audio-chapter url=%s", c.url);
                return true;
            }
        }
        return false;
    }

    private static boolean isAudioUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String low = url.trim().toLowerCase(Locale.ROOT);
        int q = low.indexOf('?');
        if (q >= 0) low = low.substring(0, q);
        int h = low.indexOf('#');
        if (h >= 0) low = low.substring(0, h);
        for (String ext : AUDIO_EXTS) {
            if (low.endsWith(ext)) return true;
        }
        return false;
    }
}
