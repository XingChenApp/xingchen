package com.fongmi.android.tv.read;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 详情级判别（不漏认第二层·捞漏层）。
 * <p>
 * 未被源级标记的源，打开详情时用<b>整本书形态</b>再判一次
 * （比单章内容信号强）。只许用书籍专属信号的<b>组合</b>：
 * <ul>
 * <li>显式声明：详情分类/类型含"小说"/"文学"（规则/源自声明）。
 * "漫画"/"动漫"不认——动漫多为视频，单凭分类无法区分。</li>
 * <li>章节名书籍专属模式：≥3 个章节名含"第X章/回/卷"（阿拉伯或汉字数字）。
 * "集"/"期"永不计入（电视剧/综艺）；"话"永不计入（动漫视频与漫画重名，
 * 单凭它无法区分，宁可漏认）；"节"永不计入（在线课程视频常用"第X节"）。</li>
 * </ul>
 * 纯数字章节、"标题-N"（短剧）、"播放地址N·XX节点"等一律不触发。
 * <p>
 * 纯 Java，不依赖 Android 类——对抗审计用同构 harness 直接跑本逻辑。
 * fail-open：判别抛错一律返回 miss（调用方 try/catch 兜底）。
 * 每次命中在日志留一行（判据＋源名），真机可回溯。
 */
public class ReadingDetailJudge {

    /** 判别结果。 */
    public static class Verdict {
        /** 是否书籍。 */
        public final boolean isBook;
        /** 判据说明（命中时记日志用）。 */
        public final String reason;

        private Verdict(boolean isBook, String reason) {
            this.isBook = isBook;
            this.reason = reason == null ? "" : reason;
        }

        public static Verdict hit(String reason) {
            return new Verdict(true, reason);
        }

        public static Verdict miss(String reason) {
            return new Verdict(false, reason);
        }
    }

    /** 章节名书籍专属模式：只认章/回/卷。 */
    private static final Pattern BOOK_CHAPTER = Pattern.compile(
            "(第\\s*[0-9一二三四五六七八九十百千零两]+\\s*[章回卷])"
                    + "|(^\\s*[0-9]{1,4}\\s*[章回卷]\\s*$)"
                    + "|(^\\s*[一二三四五六七八九十百千零两]+\\s*[章回卷]\\s*$)",
            Pattern.CASE_INSENSITIVE);

    /** 触发下限：单个"第X章"可能是偶然，≥3 个才算书形。 */
    private static final int MIN_BOOK_CHAPTER_HITS = 3;

    private ReadingDetailJudge() {
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 详情分类显式声明：含"小说"/"文学"即书籍。
     * "漫画"/"动漫"不认（动漫分类下多为视频，无法区分）。
     */
    public static boolean declaredNovelCategory(String typeName) {
        if (isEmpty(typeName)) return false;
        String t = typeName.trim();
        return t.contains("小说") || t.contains("文学");
    }

    /** 单个章节名是否书籍专属形态（章/回/卷）。 */
    public static boolean isBookChapterName(String name) {
        if (isEmpty(name)) return false;
        return BOOK_CHAPTER.matcher(name).find();
    }

    /** 书籍形态章节计数。 */
    public static int countBookChapters(List<String> chapterNames) {
        if (chapterNames == null) return 0;
        int hits = 0;
        for (String n : chapterNames) {
            if (isBookChapterName(n)) hits++;
        }
        return hits;
    }

    /**
     * 详情级判别。
     *
     * @param chapterNames 章节名表（当前线路选集名）
     * @param typeName     详情分类/类型名（Vod.getTypeName）
     */
    public static Verdict judgeDetail(List<String> chapterNames, String typeName) {
        try {
            if (declaredNovelCategory(typeName)) {
                return Verdict.hit("declared-category:" + typeName.trim());
            }
            int hits = countBookChapters(chapterNames);
            int total = chapterNames == null ? 0 : chapterNames.size();
            if (hits >= MIN_BOOK_CHAPTER_HITS) {
                return Verdict.hit("chapter-names:" + hits + "/" + total);
            }
            return Verdict.miss("hits=" + hits + "/" + total);
        } catch (Throwable t) {
            return Verdict.miss("exception");
        }
    }
}
