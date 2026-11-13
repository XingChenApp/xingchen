package com.fongmi.android.tv.legado;

import android.text.TextUtils;

import com.fongmi.android.tv.read.ReadingSession;
import com.fongmi.android.tv.read.ReadingStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 书源 → 阅读器会话：type 0 走小说阅读器（正文懒取），type 2 走漫画阅读器（图片懒取）。
 * 书架/进度复用 ReadingStore，不另起炉灶。
 */
public class LegadoReading {

    /**
     * 组装一本书的会话：章节表进会话，当前章内容首载尽量内联。
     * 打开动作由 mobile 侧经 ReadingRouter.openReading 完成（main 不引用 mobile 类）。
     */
    public static ReadingSession buildSession(LegadoSource source, LegadoPipeline.BookInfo info,
                                               List<LegadoPipeline.TocChapter> chapters, int startIndex) {
        ReadingSession s = new ReadingSession();
        if (source == null) return s;
        s.kind = source.bookSourceType == LegadoSource.TYPE_IMAGE
                ? ReadingSession.Kind.COMIC : ReadingSession.Kind.NOVEL;
        s.sourceName = source.tag();
        s.bookTitle = info.name == null ? "" : info.name;
        s.coverUrl = info.coverUrl == null ? "" : info.coverUrl;
        s.legadoKey = source.bookSourceUrl;
        if (chapters != null) {
            for (LegadoPipeline.TocChapter c : chapters) {
                s.chapters.add(new ReadingSession.Chapter(c.name, c.url));
            }
        }
        s.chapterIndex = Math.max(0, Math.min(startIndex, Math.max(0, s.chapters.size() - 1)));
        // 首章内容尽量内联，打开即有东西看
        try {
            ReadingSession.Chapter ch = s.currentChapter();
            if (ch != null && !TextUtils.isEmpty(ch.url)) {
                LegadoPipeline.Content c = new LegadoPipeline(source).content(ch.url);
                if (s.kind == ReadingSession.Kind.COMIC) s.images = splitImages(c.text);
                else s.text = c.text == null ? "" : c.text;
                s.chapterUrl = ch.url;
            }
        } catch (Throwable ignored) {
        }
        // 进书架
        try {
            ReadingStore.Record r = new ReadingStore.Record();
            r.kind = s.kind == ReadingSession.Kind.NOVEL ? "NOVEL" : "COMIC";
            r.sourceName = s.sourceName;
            r.bookTitle = s.bookTitle;
            r.coverUrl = s.coverUrl;
            r.chapterIndex = s.chapterIndex;
            ReadingSession.Chapter ch = s.currentChapter();
            if (ch != null) {
                r.chapterName = ch.name;
                r.chapterUrl = ch.url;
            }
            r.legadoKey = s.legadoKey;
            r.bookUrl = info.bookUrl == null ? "" : info.bookUrl;
            r.updatedAt = System.currentTimeMillis();
            ReadingStore.save(r);
        } catch (Throwable ignored) {
        }
        return s;
    }

    /** 小说阅读器按章懒取（NovelReaderActivity 分支调用）。 */
    public static String fetchText(String legadoKey, String chapterUrl) {
        LegadoSource source = LegadoStore.get(legadoKey);
        if (source == null || TextUtils.isEmpty(chapterUrl)) return "";
        try {
            LegadoPipeline.Content c = new LegadoPipeline(source).content(chapterUrl);
            return c.text == null ? "" : c.text;
        } catch (Throwable t) {
            return "";
        }
    }

    /** 漫画阅读器按章懒取（ComicReaderActivity 分支调用）。 */
    public static List<String> fetchImages(String legadoKey, String chapterUrl) {
        LegadoSource source = LegadoStore.get(legadoKey);
        if (source == null || TextUtils.isEmpty(chapterUrl)) return new ArrayList<>();
        try {
            LegadoPipeline.Content c = new LegadoPipeline(source).content(chapterUrl);
            return splitImages(c.text);
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    private static List<String> splitImages(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            String u = line.trim();
            if (u.startsWith("http")) out.add(u);
        }
        return out;
    }
}
