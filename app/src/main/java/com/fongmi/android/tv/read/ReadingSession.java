package com.fongmi.android.tv.read;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 阅读会话载荷：判别层（{@link ReadingGate}）产出、阅读器消费。
 * 大数据（图片列表）走进程内静态仓，Intent 只带 id 与小字段，
 * 与 VideoActivity.HK_EPISODES_STORE 同思路，避免 Binder 事务撑爆。
 */
public class ReadingSession {

    public enum Kind {COMIC, NOVEL}

    /** 单章。 */
    public static class Chapter {
        public String name = "";
        public String url = "";
        public String pic = "";

        public Chapter() {
        }

        public Chapter(String name, String url) {
            this.name = name == null ? "" : name;
            this.url = url == null ? "" : url;
        }
    }

    public Kind kind = Kind.COMIC;
    /** 来源名：规则名（海阔）/ 源名（py）。 */
    public String sourceName = "";
    public String bookTitle = "";
    public String coverUrl = "";
    /** 当前章下标（chapters 为空时为 0）。 */
    public int chapterIndex = 0;
    public List<Chapter> chapters = new ArrayList<>();
    /** 漫画：当前章图片（可能为空＝进阅读器后懒取）。 */
    public List<String> images = new ArrayList<>();
    /** 小说：当前章正文（可能为空＝进阅读器后懒取）。 */
    public String text = "";
    /** 海阔懒取：规则名。 */
    public String ruleTitle = "";
    /** 懒取键：章节地址。 */
    public String chapterUrl = "";
    /** 图片防盗链 Referer（海阔规则页地址）。 */
    public String referer = "";
    /**
     * 章节页地址：图片组装时的页面上下文（与海阔文章页 getGlideUrl 的 pageUrl 同义，
     * 供自动 Referer 计算与 @js=/@headers= 后缀解析）。与 referer 分开存——
     * referer 只是请求头里的一个值，顶替不了页面地址，混装正是漫画没画面的断点。
     */
    public String pageUrl = "";
    /**
     * play 解析的整套请求头（Cookie/UA/Referer 等）：图片组装时图串自带后缀
     * 没给的头从这里补。入口带入、切章时随新章 play 一并刷新。
     */
    public Map<String, String> playHeaders = new HashMap<>();
    /** py 源 key（懒取章节表用）。 */
    public String siteKey = "";
    /** py 小说：书的详情 id（书架重建章节表时重拉详情用；老会话为空）。 */
    public String detailId = "";
    /** py 小说：建会话时的线路 flag 名（多线路时重建选同一线路的章节表）。 */
    public String playFlag = "";
    /** Legado 书源 bookSourceUrl（书源懒取正文/图片用）。 */
    public String legadoKey = "";
    /** 本地书 id（localbook 包；非空=本地书，懒取走 LocalBookReader）。 */
    public String localId = "";
    /** 起始页（漫画第几张图；阅读器打开时定位用）。 */
    public int startPage = 0;
    /** 起始进度万分比（小说换源对位后带入，阅读器恢复用）。 */
    public int startFraction = -1;
    /**
     * 换源成功后由换源链在旧会话上置真：旧阅读器已把书交给新源，
     * 它退出时的 onPause 存进度必须跳过，否则会按旧源键把已删的旧书架记录写回来。
     */
    public boolean shelfSuperseded = false;

    // ---- 进程内静态仓：大数据不走 Intent ----
    private static final int STORE_MAX = 8;
    private static final ConcurrentHashMap<String, ReadingSession> STORE = new ConcurrentHashMap<>();
    /** 入仓顺序（淘汰按先进先出；ConcurrentHashMap 自身无序，不能拿它的首键当最老）。 */
    private static final Queue<String> STORE_ORDER = new ConcurrentLinkedQueue<>();

    /**
     * 存入并返回 id。
     * 残留清理放在建新会话这一侧，不靠读取时顺手删：同书同源的旧会话先清掉
     * （重开同本书不攒两份）；总量超过上限时按入仓顺序淘汰最老的一条。
     * 正在显示的阅读器手里有会话对象直引用，淘汰只影响之后按 id 重建能否命中。
     */
    public static String put(ReadingSession s) {
        // 入参判空（低-11）：塞 null 进 ConcurrentHashMap 直接 NPE，先挡掉。
        if (s == null) return null;
        if (s.bookTitle != null && !s.bookTitle.isEmpty()) {
            for (Map.Entry<String, ReadingSession> e : STORE.entrySet()) {
                ReadingSession old = e.getValue();
                if (old != null && old != s && old.kind == s.kind
                        && s.bookTitle.equals(old.bookTitle)
                        && (s.sourceName == null ? "" : s.sourceName)
                                .equals(old.sourceName == null ? "" : old.sourceName)) {
                    STORE.remove(e.getKey());
                    STORE_ORDER.remove(e.getKey());
                }
            }
        }
        String id = UUID.randomUUID().toString();
        STORE.put(id, s);
        STORE_ORDER.add(id);
        while (STORE.size() > STORE_MAX) {
            String eldest = STORE_ORDER.poll();
            if (eldest == null) break;
            STORE.remove(eldest);
        }
        return id;
    }

    /**
     * 取会话（不删除，照 TtsJob.get 口径）：阅读器被重建（主题切换、系统回收后
     * 在同进程内恢复等）时会二次进 onCreate 按同一 id 取，必须还能拿到；
     * 从未建过会话（id 为空或进程已死、仓里确实没有）才返回 null，阅读器据此退出。
     * 同一 id 因此可被重复取——无重复消费问题：每个 id 只由一次 openReading 生成、
     * 只指向它打开的那一个阅读器，再取的只会是该阅读器自己的重建。
     */
    public static ReadingSession get(String id) {
        if (id == null) return null;
        return STORE.get(id);
    }

    public static ReadingSession peek(String id) {
        return get(id);
    }

    /** 显式清理（打开失败等真不要了的场合）；不靠读取顺手删。 */
    public static void remove(String id) {
        if (id == null) return;
        STORE.remove(id);
        STORE_ORDER.remove(id);
    }

    public Chapter currentChapter() {
        if (chapters != null && chapterIndex >= 0 && chapterIndex < chapters.size()) {
            return chapters.get(chapterIndex);
        }
        Chapter c = new Chapter();
        c.name = bookTitle;
        c.url = chapterUrl;
        return c;
    }
}
