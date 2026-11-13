package com.fongmi.android.tv.localbook;

import android.text.TextUtils;

import com.fongmi.android.tv.read.ReadingSession;
import com.fongmi.android.tv.read.ReadingStore;

/**
 * 本地书 → 阅读会话：章节表一次带入（索引已建好，无二次解析），
 * 正文/图片进阅读器后按章懒取。
 */
public class LocalBookSession {

    /** 从 LocalBook 建会话（chapterIndex 起始章）。 */
    public static ReadingSession build(LocalBook book, int chapterIndex) {
        if (book == null || book.chapters.isEmpty()) return null;
        ReadingSession s = new ReadingSession();
        s.kind = "COMIC".equals(book.kind) ? ReadingSession.Kind.COMIC : ReadingSession.Kind.NOVEL;
        s.sourceName = "本地书";
        s.bookTitle = book.title;
        s.coverUrl = TextUtils.isEmpty(book.coverPath) ? "" : "file://" + book.coverPath;
        s.localId = book.id;
        s.chapterIndex = Math.max(0, Math.min(chapterIndex, book.chapters.size() - 1));
        for (int i = 0; i < book.chapters.size(); i++) {
            LocalBook.ChapterRef c = book.chapters.get(i);
            ReadingSession.Chapter ch = new ReadingSession.Chapter(c.name,
                    LocalBook.chapterUrl(book.id, i));
            s.chapters.add(ch);
        }
        ReadingSession.Chapter cur = s.currentChapter();
        s.chapterUrl = cur.url;
        return s;
    }

    /** 从书架记录重建（继续读用）。书没了返回 null，调用方给人话提示。 */
    public static ReadingSession fromRecord(ReadingStore.Record r) {
        if (r == null || TextUtils.isEmpty(r.localId)) return null;
        LocalBook book = LocalBookStore.get(r.localId);
        if (book == null) return null;
        return build(book, r.chapterIndex);
    }

    /** 书架记录里的 startPage 恢复：漫画=看到第几张图。 */
    public static int startPageOf(ReadingStore.Record r) {
        if (r == null || !"COMIC".equals(r.kind)) return 0;
        return Math.max(0, r.position);
    }
}
