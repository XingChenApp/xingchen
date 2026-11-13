package com.fongmi.android.tv.tts;

import com.fongmi.android.tv.read.ReadingSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 朗读任务：从小说阅读器递给朗读播放页。
 * 大数据走静态仓，Intent 只带 id（照 ReadingSession 模式）。
 */
public class TtsJob {

    public String bookTitle = "";
    public String coverUrl = "";
    public String sourceName = "";
    /** py 源 key / Legado 书源 key / 海阔规则名 / 本地书 id（正文懒取用）。 */
    public String siteKey = "";
    public String legadoKey = "";
    public String ruleTitle = "";
    public String localId = "";
    /** py 内联正文（首章，session.text 口径）。 */
    public String inlineText = "";
    /** 章节表（name/url）。 */
    public List<ReadingSession.Chapter> chapters = new ArrayList<>();
    /** 起始章下标。 */
    public int startChapter = 0;
    /** 起始章内的阅读位置（万分比 0~10000，用于估算起始段）。 */
    public int startFraction = 0;
    public String voiceId = EdgeVoiceCatalog.DEFAULT_VOICE;
    public float rate = 1.0f;

    private static final Map<String, TtsJob> HOLDER = new ConcurrentHashMap<>();

    public static String put(TtsJob job) {
        String id = UUID.randomUUID().toString();
        HOLDER.put(id, job);
        return id;
    }

    /** 取任务（不删除；后台回来重建页面时还能拿到）。 */
    public static TtsJob get(String id) {
        if (id == null) return null;
        return HOLDER.get(id);
    }

    /** 用户真关闭朗读时删。 */
    public static void remove(String id) {
        if (id != null) HOLDER.remove(id);
    }

    public String bookKey() {
        return sourceName + "|" + bookTitle;
    }
}
