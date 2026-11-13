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

    // ---- 失败不再静默（349 阅读组）：最近一次取数/进书架失败的原因 ----
    // 正文/图片懒取的失败原因留在这里：阅读器按既有失败态呈现（小说「正文抓取
    // 失败」/漫画「这一章没有取到图片」），批量缓存（ReadingDownload）按章读它
    // 拼失败原因，不许只剩一个空串/空表无从分辨是没内容还是取失败。
    private static volatile String lastFetchError;
    private static volatile String lastShelfError;

    public static String lastFetchError() {
        return lastFetchError;
    }

    public static String lastShelfError() {
        return lastShelfError;
    }

    private static String msgOf(Throwable t) {
        if (t == null) return "";
        String m = t.getMessage();
        return m == null || m.isEmpty() ? t.getClass().getSimpleName() : m;
    }

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
                lastFetchError = null;
            }
        } catch (Throwable t) {
            // 内联失败不再无痕：原因留档（见类头），会话照常打开，阅读器懒取
            // 重试后仍失败会按其既有失败态明示，不会只剩空白页无解释。
            lastFetchError = msgOf(t);
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
            // 保存失败由 ReadingStore 新结构承接（commit 校验＋提示＋不覆盖旧记录）；
            // 这里把结局留档，不许再出现「看过的书没进书架」却查不到原因。
            lastShelfError = ReadingStore.lastWriteError();
        } catch (Throwable t) {
            lastShelfError = msgOf(t);
        }
        return s;
    }

    /** 小说阅读器按章懒取（NovelReaderActivity 分支调用）。 */
    public static String fetchText(String legadoKey, String chapterUrl) {
        LegadoSource source = LegadoStore.get(legadoKey);
        if (source == null || TextUtils.isEmpty(chapterUrl)) {
            lastFetchError = source == null ? "书源已不存在" : "章节地址为空";
            return "";
        }
        try {
            LegadoPipeline.Content c = new LegadoPipeline(source).content(chapterUrl);
            lastFetchError = null;
            return c.text == null ? "" : c.text;
        } catch (Throwable t) {
            lastFetchError = msgOf(t);
            return "";
        }
    }

    /** 漫画阅读器按章懒取（ComicReaderActivity 分支调用）。 */
    public static List<String> fetchImages(String legadoKey, String chapterUrl) {
        LegadoSource source = LegadoStore.get(legadoKey);
        if (source == null || TextUtils.isEmpty(chapterUrl)) {
            lastFetchError = source == null ? "书源已不存在" : "章节地址为空";
            return new ArrayList<>();
        }
        try {
            LegadoPipeline.Content c = new LegadoPipeline(source).content(chapterUrl);
            lastFetchError = null;
            return splitImages(c.text);
        } catch (Throwable t) {
            lastFetchError = msgOf(t);
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
