package com.fongmi.android.tv.ui.hk;

import android.text.TextUtils;

import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.read.ReadingGate;
import com.fongmi.android.tv.read.ReadingSession;

import java.util.ArrayList;
import java.util.List;

/**
 * 环42 #autoReader# → 咱阅读组件的映射层（dig2 账 §8.2 映射表的落码形）。
 * 全部映射只发生在 hk 侧调用方：阅读组件（ReadingGate/ReadingSession/
 * NovelReaderActivity）只被调用，其内部语义一字不动。
 *
 * <p>映射逐项（官形 → ReadingSession 面）：</p>
 * <ul>
 *   <li>条目 title → Chapter.name（直填）；url → Chapter.url（直填，组件
 *   按此 url 懒取正文）；pic → Chapter.pic（在位可填可空）。</li>
 *   <li>条目 desc/content/extra/sel → 无对应字段，丢弃（extra.id 在链 B
 *   是「本章 url」标记，与 Chapter.url 重复）。</li>
 *   <li>CommonReadPageData.currentUrl → 当前章 Chapter.url；
 *   title → 当前章 Chapter.name（时序在章内容之后到达，按 currentUrl
 *   找回已组装的那条回填，dig2 §8.3-4）；nextPage → 章节表尾的待定
 *   下一章（name 先空、其 refreshReadData 到达时回填）；id（规则标题）
 *   → session.ruleTitle/sourceName——链 B 合成规则的 id 恒「阅读模式」，
 *   组装时须以真实规则名替换，否则懒取正文按 ruleTitle 查规则落空。</li>
 *   <li>ReadPageData（官页段记账）与官 ViewHistory.url 改写 → 不映射：
 *   组件自有 ReadingStore.Record 的 chapterIndex/position 承接续看，
 *   两套进度不可互译（dig2 §8.2）。</li>
 * </ul>
 *
 * <p>出口实况（批 C2 复核订正）：组装段已建、出口未接记名——
 * {@link #buildSession} 与 {@link #chaptersFromItems} 全树零调用
 * （buildSession 仅被 HkArticleFragment.buildAutoReaderSession 引用，
 * 该法本身亦零调用）。官形是规则在列表内直读，咱 hk 列表无阅读
 * 主题渲染面，造触发钮＝自创 UI，故不补自创触发点。链回告累积
 * {@link #applyChainEvent} 已接有消费（回告并入章节表在跑），但
 * 累积表尚无组装消费方；翻页本体、setReadTheme、$17/$64、
 * HkPageRecordStore 三新口等已接通段不在此记名内。</p>
 *
 * <p>接不通、记名不硬凑的段（dig2 §8.3）：①链 B 的章 url 是普通网页
 * url、正文靠 Readability 抽取（loadReadContentPage 的 data 段落数组），
 * 不是规则 find_rule 的产物——组装出的 Chapter.url 喂
 * HkNovelFetcher.fetchText(ruleTitle, url) 无规则可跑，此形正文只能走
 * hk 列表内链 A 直读，映射层不把链 B 章节送进阅读组件（buildSession
 * 只供链 A 规则形：章 url 是规则可再跑出正文的形）。②startReader
 * 合成形无真实规则名，ruleTitle 无从填、且咱家无 startReader 入口
 * 本身，两件都不在本层解。</p>
 */
public final class HkAutoReaderMapper {

    private HkAutoReaderMapper() {
    }

    /**
     * 条目 → 章节表（dig2 §8.2 前三行）：被当作「一章入口」的条目
     * （有 url、非 rich_text 段落条目）逐条直填 name/url/pic。
     * （零调用：组装段在位、出口未接记名，见类注出口实况。）
     */
    public static List<ReadingSession.Chapter> chaptersFromItems(List<HkItem> items) {
        List<ReadingSession.Chapter> chapters = new ArrayList<>();
        if (items == null) return chapters;
        for (HkItem item : items) {
            if (item == null || TextUtils.isEmpty(item.getUrl())) continue;
            if (HkColType.RICH_TEXT.code.equals(item.getType())) continue;
            ReadingSession.Chapter chapter = new ReadingSession.Chapter(
                    item.getTitle() == null ? "" : item.getTitle(), item.getUrl());
            chapter.pic = item.getPic() == null ? "" : item.getPic();
            chapters.add(chapter);
        }
        return chapters;
    }

    /**
     * 链式增量的一次 refreshReadData 回告并入章节表：currentUrl 非空 →
     * 确保该章在表（不在则以本次 title 建条、在则以非空 title 回填
     * name）；nextPage 非空且不在表 → 表尾追加待定章（name 空）。
     * 返回 currentUrl 在表中的下标（无 currentUrl 回 -1）。
     */
    public static int applyChainEvent(List<ReadingSession.Chapter> chapters,
                                      String currentUrl, String title, String nextPage) {
        int index = -1;
        if (!TextUtils.isEmpty(currentUrl)) {
            index = indexOf(chapters, currentUrl);
            if (index < 0) {
                chapters.add(new ReadingSession.Chapter(title == null ? "" : title, currentUrl));
                index = chapters.size() - 1;
            } else if (!TextUtils.isEmpty(title)) {
                chapters.get(index).name = title;
            }
        }
        if (!TextUtils.isEmpty(nextPage) && indexOf(chapters, nextPage) < 0) {
            chapters.add(new ReadingSession.Chapter("", nextPage));
        }
        return index;
    }

    public static int indexOf(List<ReadingSession.Chapter> chapters, String url) {
        if (chapters == null || url == null) return -1;
        for (int i = 0; i < chapters.size(); i++) {
            if (url.equals(chapters.get(i).url)) return i;
        }
        return -1;
    }

    /**
     * 组装会话（dig2 §8.1 hk 专用组装口）：全量章节表副本＋起始下标
     * （currentUrl 在表中的位置，找不到回 0）→ ReadingGate.fromHkNovel。
     * ruleTitle 必须是真实规则名（懒取正文的键），书名由调用方给
     * （页面标题口径），本层不猜。
     * （组装段已建、出口未接记名：仅 HkArticleFragment
     * .buildAutoReaderSession 引用、该法本身零调用，见类注出口实况。）
     */
    public static ReadingSession buildSession(String ruleTitle, String bookTitle,
                                              List<ReadingSession.Chapter> chapters, String currentUrl) {
        List<ReadingSession.Chapter> copy = new ArrayList<>();
        if (chapters != null) {
            for (ReadingSession.Chapter c : chapters) {
                ReadingSession.Chapter n = new ReadingSession.Chapter(c.name, c.url);
                n.pic = c.pic;
                copy.add(n);
            }
        }
        int index = Math.max(0, indexOf(copy, currentUrl));
        return ReadingGate.fromHkNovel(ruleTitle, bookTitle, copy, index);
    }
}
