package com.fongmi.android.tv.api.hk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 投屏/局域网服务侧的播放态登记（8.83 RemoteServerManager 的 playUrl/UrlDTO/
 * VideoPlayerActivity.chapters 三件的星辰等价，DlanWebController 诸端点的数据源）。
 * 8.83 原文：RemoteServerManager 持 playUrl 与 DlanUrlDTO（播放器起播时写入），
 *   VideoPlayerActivity.chapters 为静态集表；DlanWebController 的 /playUrl、
 *   /getPlayList、/proxyM3u8、/file 只读这三处。
 * 星辰落点：生产方＝HkListActivity 的 startPlayerSingle/startPlayerChapters
 *   入场登记＋VideoActivity.onPlayDataUpdate 换址同步；消费方＝HkDlanController。
 * 环34 起：danmu/subtitle/audioUrl 三字段由 SiteApi.hkPlayerContent 在
 *   PlayData 在手点经 updateMediaInfo 写入（官由播放器起播时用 PlayData
 *   写入 DTO，咱家 TVBox 播放面无 PlayData 在手点、SiteApi 是等价落点）；
 *   jump 时长两字段仍无写入点、恒空，与官「无媒体信息时」同形。
 */
public final class HkDlanState {

    /** 8.83 DlanUrlDTO 的字段集（ui/webdlan/model/DlanUrlDTO.smali 抠实）。 */
    public static final class Dto {
        public String url;
        public String title;
        public Map<String, String> headers;
        public String danmu;
        public String subtitle;
        public String audioUrl;
        public int jumpStartDuration;
        public int jumpEndDuration;

        public Dto copy() {
            Dto d = new Dto();
            d.url = url;
            d.title = title;
            d.headers = headers;
            d.danmu = danmu;
            d.subtitle = subtitle;
            d.audioUrl = audioUrl;
            d.jumpStartDuration = jumpStartDuration;
            d.jumpEndDuration = jumpEndDuration;
            return d;
        }
    }

    private static volatile String playUrl;
    private static volatile Dto dto;
    private static volatile List<String[]> chapters = new ArrayList<>();
    private static volatile String ruleTitle;

    private HkDlanState() {
    }

    /** 起播登记：单条播放（startPlayerSingle）。 */
    public static void setSingle(String rule, String title, String url) {
        ruleTitle = rule;
        playUrl = url;
        Dto d = new Dto();
        d.url = url;
        d.title = title;
        dto = d;
        List<String[]> list = new ArrayList<>();
        list.add(new String[]{title == null ? "" : title, url == null ? "" : url});
        chapters = list;
    }

    /** 起播登记：集表播放（startPlayerChapters），currentUrl 为起播集地址。 */
    public static void setChapters(String rule, String title, String currentUrl,
                                   List<String[]> chapterPairs) {
        ruleTitle = rule;
        playUrl = currentUrl;
        Dto d = new Dto();
        d.url = currentUrl;
        d.title = title;
        dto = d;
        chapters = chapterPairs == null ? new ArrayList<>() : new ArrayList<>(chapterPairs);
    }

    /**
     * 环34：媒体信息写入（官 DlanUrlDTO 由播放器以 PlayData 填 audioUrl/danmu/
     * subtitle）。dto 未登记（未起播）时不写——官 DTO 恒由播放器先建。
     */
    public static void updateMediaInfo(String audioUrl, String danmu, String subtitle) {
        Dto d = dto;
        if (d == null) return;
        if (audioUrl != null && !audioUrl.isEmpty()) d.audioUrl = audioUrl;
        if (danmu != null && !danmu.isEmpty()) d.danmu = danmu;
        if (subtitle != null && !subtitle.isEmpty()) d.subtitle = subtitle;
    }

    /** refreshVideoUrl 换址同步（VideoActivity.onPlayDataUpdate）。 */
    public static void updatePlayUrl(String url) {
        if (url == null || url.isEmpty()) return;
        playUrl = url;
        Dto d = dto;
        if (d != null) d.url = url;
    }

    public static String getPlayUrl() {
        return playUrl;
    }

    public static Dto getDto() {
        return dto;
    }

    public static List<String[]> getChapters() {
        return chapters;
    }

    /**
     * 8.83 VideoPlayerActivity.getChapters/playChapter 共用的标题 "-" 分段映射
     * （smali 6041-6046 亲读）：标题按 "-" 切分，显示名取末段；标题为空、以 "-"
     * 结尾、切分不足两段、末段为空的条目不入表。origIndices 为过滤序→原始序。
     * /getPlayList 的列表与 playChapter 的定位共用此表（官两处同建一遍）。
     */
    public static final class ChapterMap {
        public final List<String> titles = new ArrayList<>();
        public final List<Integer> origIndices = new ArrayList<>();
    }

    public static ChapterMap buildChapterMap(List<String> rawTitles) {
        ChapterMap map = new ChapterMap();
        if (rawTitles == null) return map;
        for (int i = 0; i < rawTitles.size(); i++) {
            String t = rawTitles.get(i);
            if (t == null || t.isEmpty()) continue;
            if (t.endsWith("-")) continue;
            String[] parts = t.split("-");
            if (parts.length < 2) continue;
            String last = parts[parts.length - 1];
            if (last == null || last.isEmpty()) continue;
            map.titles.add(last);
            map.origIndices.add(i);
        }
        return map;
    }

    public static String getRuleTitle() {
        return ruleTitle;
    }
}
