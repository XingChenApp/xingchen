package com.fongmi.android.tv.read;

import android.text.TextUtils;

import com.fongmi.android.tv.legado.LegadoPipeline;
import com.fongmi.android.tv.legado.LegadoSource;
import com.fongmi.android.tv.legado.LegadoStore;
import com.fongmi.android.tv.read.ReadingStore;
import com.fongmi.android.tv.utils.Task;

import java.util.List;

/**
 * 连载更新检查：书架刷新时低频跑。
 * 只查 Legado 书源的书（有 bookUrl 才能取目录）；6 小时节流；失败静默。
 */
public final class ReadingUpdateChecker {

    private ReadingUpdateChecker() {
    }

    /** 6 小时内查过就跳过。 */
    private static final long THROTTLE = 6L * 3600 * 1000;

    public interface Done {
        void onDone(boolean changed);
    }

    /** 检查一批记录，回调在主线程（changed=true 表示有记录被更新了角标）。 */
    public static void check(List<ReadingStore.Record> records, android.os.Handler ui, Done done) {
        Task.execute(() -> {
            boolean changed = false;
            long now = System.currentTimeMillis();
            for (ReadingStore.Record r : records) {
                if (TextUtils.isEmpty(r.legadoKey) || TextUtils.isEmpty(r.bookUrl)) continue;
                if (now - r.lastCheckedAt < THROTTLE) continue;
                try {
                    LegadoSource src = findSource(r.legadoKey);
                    if (src == null) continue;
                    LegadoPipeline pipe = new LegadoPipeline(src);
                    LegadoPipeline.BookInfo info = pipe.bookInfo(r.bookUrl);
                    List<LegadoPipeline.TocChapter> toc = pipe.toc(info.tocUrl);
                    int total = toc == null ? 0 : toc.size();
                    r.lastCheckedAt = now;
                    if (total > 0) {
                        if (r.totalChapters > 0 && total > r.totalChapters) {
                            r.hasUpdate = true;
                            changed = true;
                        } else if (r.totalChapters == 0) {
                            // 首次建基线，不算更新
                            r.hasUpdate = false;
                        }
                        r.totalChapters = total;
                    }
                    ReadingStore.save(r);
                } catch (Throwable ignored) {
                    // 失败静默：不打扰用户
                }
            }
            final boolean fChanged = changed;
            ui.post(() -> done.onDone(fChanged));
        });
    }

    /** 读完新章后清掉更新角标。 */
    public static void clearUpdate(ReadingStore.Record r) {
        if (r == null) return;
        r.hasUpdate = false;
        ReadingStore.save(r);
    }

    private static LegadoSource findSource(String legadoKey) {
        for (LegadoSource s : LegadoStore.runnable()) {
            if (legadoKey.equals(s.bookSourceUrl)) return s;
        }
        // 被停用的源也允许查一次（只读目录）
        for (LegadoStore.Entry e : LegadoStore.all()) {
            try {
                LegadoSource s = LegadoStore.parse(e.json);
                if (legadoKey.equals(s.bookSourceUrl)) return s;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
