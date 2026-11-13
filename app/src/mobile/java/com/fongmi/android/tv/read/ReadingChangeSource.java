package com.fongmi.android.tv.read;
import com.fongmi.android.tv.utils.ToastUtil;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import com.fongmi.android.tv.legado.LegadoPipeline;
import com.fongmi.android.tv.legado.LegadoReading;
import com.fongmi.android.tv.legado.LegadoSource;
import com.fongmi.android.tv.legado.LegadoStore;
import com.fongmi.android.tv.read.ReadingMatch;
import com.fongmi.android.tv.read.ReadingSession;
import com.fongmi.android.tv.read.ReadingStore;
import com.fongmi.android.tv.ui.activity.ComicReaderActivity;
import com.fongmi.android.tv.ui.activity.NovelReaderActivity;
import com.fongmi.android.tv.ui.hk.HkDialogs;
import com.fongmi.android.tv.utils.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * 换源：同一本书在已启用的书源里按书名/作者匹配，一键切换。
 * 只认 Legado 书源（py/海阔无搜索口，换不了源）；进度按章节名对位带过去。
 * 换源失败人话提示，书架里的书不动、不丢。
 */
public final class ReadingChangeSource {

    private ReadingChangeSource() {
    }

    public static class Candidate {
        public LegadoSource source;
        public String bookName = "";
        public String author = "";
        public String bookUrl = "";
    }

    /** 当前阅读器是否支持换源（仅 Legado 书源的书）。 */
    public static boolean supported(ReadingSession s) {
        return s != null && !TextUtils.isEmpty(s.legadoKey);
    }

    public interface CandidatesDone {
        void onDone(List<Candidate> candidates);
    }

    /**
     * 搜候选源（后台线程）：在已启用的同类型书源里搜书名，过滤出同书。
     * 回调在主线程。
     */
    public static void search(Activity act, ReadingSession session, Handler ui,
                              CandidatesDone done) {
        Task.execute(() -> {
            List<Candidate> out = new ArrayList<>();
            try {
                int wantType = session.kind == ReadingSession.Kind.COMIC
                        ? LegadoSource.TYPE_IMAGE : LegadoSource.TYPE_TEXT;
                for (LegadoSource src : LegadoStore.runnable()) {
                    if (src.bookSourceType != wantType) continue;
                    if (src.bookSourceUrl.equals(session.legadoKey)) continue; // 跳过当前源
                    List<LegadoPipeline.Book> books;
                    try {
                        books = new LegadoPipeline(src).search(session.bookTitle);
                    } catch (Throwable t) {
                        continue; // 单个源搜失败不影响其它源
                    }
                    if (books == null) continue;
                    for (LegadoPipeline.Book b : books) {
                        if (ReadingMatch.sameBook(session.bookTitle, "", b.name, b.author)) {
                            Candidate c = new Candidate();
                            c.source = src;
                            c.bookName = b.name;
                            c.author = b.author;
                            c.bookUrl = b.bookUrl;
                            out.add(c);
                            break; // 每个源只取最像的一本
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            final List<Candidate> fOut = out;
            ui.post(() -> done.onDone(fOut));
        });
    }

    /** 弹出候选源列表，选中后切换并打开新阅读器（旧阅读器 finish）。 */
    public static void pickAndSwitch(Activity act, ReadingSession session,
                                     List<Candidate> candidates, int oldChapterIndex,
                                     float oldFraction, int oldPage) {
        if (candidates.isEmpty()) {
            ToastUtil.show(act, "其它书源里没找到这本书", Toast.LENGTH_SHORT);
            return;
        }
        String[] names = new String[candidates.size()];
        for (int i = 0; i < names.length; i++) {
            Candidate c = candidates.get(i);
            names[i] = c.source.tag() + " · " + c.bookName
                    + (TextUtils.isEmpty(c.author) ? "" : "（" + c.author + "）");
        }
        new HkDialogs(act)
                .setTitle("换源（" + candidates.size() + " 个源有这本书）")
                .setItems(names, (d, which) -> {
                    d.dismiss();
                    doSwitch(act, session, candidates.get(which),
                            oldChapterIndex, oldFraction, oldPage);
                })
                .setPositiveButton("取消", (d, w) -> d.dismiss())
                .show();
    }

    private static void doSwitch(Activity act, ReadingSession old, Candidate c,
                                 int oldChapterIndex, float oldFraction, int oldPage) {
        ToastUtil.show(act, "正在从「" + c.source.tag() + "」取目录…", Toast.LENGTH_SHORT);
        Task.execute(() -> {
            try {
                LegadoPipeline pipe = new LegadoPipeline(c.source);
                LegadoPipeline.BookInfo info = pipe.bookInfo(c.bookUrl);
                List<LegadoPipeline.TocChapter> toc = pipe.toc(info.tocUrl);
                if (toc == null || toc.isEmpty()) {
                    fail(act, "这个源目录为空，换源失败");
                    return;
                }
                List<String> names = new ArrayList<>(toc.size());
                for (LegadoPipeline.TocChapter t : toc) names.add(t.name);
                String curName = "";
                if (old.chapters != null && oldChapterIndex >= 0
                        && oldChapterIndex < old.chapters.size()) {
                    curName = old.chapters.get(oldChapterIndex).name;
                }
                int aligned = ReadingMatch.alignChapter(names, curName, oldChapterIndex);
                ReadingSession s = LegadoReading.buildSession(c.source, info, toc, aligned);
                // 进度带过去：小说按万分比，漫画按页（ clamp 到新章图数未知，先带下标）
                if (s.kind == ReadingSession.Kind.NOVEL) {
                    s.startFraction = Math.round(oldFraction * 10000);
                } else {
                    s.startPage = Math.max(0, oldPage);
                }
                // 书架键＝kind|源名|书名（ReadingStore.Record.key），键里含源名：
                // 换源前后根本不是同键——buildSession 已按新源名存了一条新记录，
                // 旧源名下还留着一条旧记录。这里把进度迁到新条、再删旧条，只留一条。
                // 章节位置：章节下标用对位后的 aligned；章内进度小说按万分比、
                // 漫画按页号，都从旧页带过来的值走，不把用户进度弄丢。
                String kind = s.kind == ReadingSession.Kind.NOVEL ? "NOVEL" : "COMIC";
                String oldKind = old.kind == ReadingSession.Kind.NOVEL ? "NOVEL" : "COMIC";
                String oldSource = old.sourceName == null ? "" : old.sourceName;
                String oldTitle = old.bookTitle == null ? "" : old.bookTitle;
                // 先给旧会话打标：旧阅读器 finish 时 onPause 还会存一次进度，
                // 不打标它会按旧源键把刚删掉的旧记录重新写回书架（见两阅读器 saveProgress 守卫）
                old.shelfSuperseded = true;
                ReadingStore.Record r = ReadingStore.find(kind, s.sourceName, s.bookTitle);
                if (r == null) {
                    // buildSession 存书架是吞异常的，万一没存上，这里按新会话补建一条，
                    // 保证删旧之后书架里至少还有新条、书不丢
                    r = new ReadingStore.Record();
                    r.kind = kind;
                    r.sourceName = s.sourceName;
                    r.bookTitle = s.bookTitle;
                    r.coverUrl = s.coverUrl;
                    ReadingSession.Chapter ch = s.currentChapter();
                    if (ch != null) {
                        r.chapterName = ch.name;
                        r.chapterUrl = ch.url;
                    }
                    r.legadoKey = s.legadoKey;
                    r.bookUrl = info.bookUrl == null ? "" : info.bookUrl;
                }
                r.chapterIndex = aligned;
                if (s.kind == ReadingSession.Kind.NOVEL) r.position = s.startFraction;
                else r.position = s.startPage;
                ReadingStore.save(r);
                // 删旧条：新旧键真相同（同名源）时就是同一条，不删（进度已在上面迁好）
                boolean sameKey = kind.equals(oldKind)
                        && (s.sourceName == null ? "" : s.sourceName).equals(oldSource)
                        && (s.bookTitle == null ? "" : s.bookTitle).equals(oldTitle);
                if (!sameKey) ReadingStore.remove(oldKind, oldSource, oldTitle);
                act.runOnUiThread(() -> {
                    ToastUtil.show(act, "已换到「" + c.source.tag() + "」", Toast.LENGTH_SHORT);
                    ReadingRouter.openReading(act, s);
                    act.finish();
                });
            } catch (Throwable t) {
                fail(act, "换源失败：" + shortMsg(t));
            }
        });
    }

    private static void fail(Activity act, String msg) {
        act.runOnUiThread(() -> ToastUtil.show(act, msg, Toast.LENGTH_SHORT));
    }

    private static String shortMsg(Throwable t) {
        String m = t.getMessage();
        if (TextUtils.isEmpty(m)) return "网络或规则异常";
        return m.length() > 40 ? m.substring(0, 40) : m;
    }
}
