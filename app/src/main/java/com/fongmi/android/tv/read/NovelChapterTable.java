package com.fongmi.android.tv.read;

import java.util.List;

/**
 * py 小说章节表索引定位（纯 Java，不依赖 Android 类）。
 * <p>
 * 书架记录只存了"当时在看哪一章"（地址/名/旧索引），重建全量章节表后必须把
 * 记录对回正确的下标。优先级：地址精确匹配 ＞ 章名匹配 ＞ 旧索引钳制。
 * <p>
 * 地址优先是关键：1.3.57 时代的毒记录（chapterIndex=1 实际是第一章地址）
 * 靠它回到 0，不错位；真·第二章的记录地址也能精确命中 1。
 */
public final class NovelChapterTable {

    private NovelChapterTable() {
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 把书架记录定位到重建章节表的下标。
     *
     * @param chapters    重建的全量章节表（非空）
     * @param chapterUrl  记录里的章节地址（可能为空）
     * @param chapterName 记录里的章节名（可能为空）
     * @param savedIndex  记录里的旧下标
     * @return 0 ~ size-1
     */
    public static int resolveIndex(List<ReadingSession.Chapter> chapters,
                                   String chapterUrl, String chapterName, int savedIndex) {
        if (chapters == null || chapters.isEmpty()) return 0;
        if (!isEmpty(chapterUrl)) {
            for (int i = 0; i < chapters.size(); i++) {
                ReadingSession.Chapter c = chapters.get(i);
                if (c != null && chapterUrl.equals(c.url)) return i;
            }
        }
        if (!isEmpty(chapterName)) {
            for (int i = 0; i < chapters.size(); i++) {
                ReadingSession.Chapter c = chapters.get(i);
                if (c != null && chapterName.equals(c.name)) return i;
            }
        }
        if (savedIndex >= 0 && savedIndex < chapters.size()) return savedIndex;
        return 0;
    }
}
