package com.fongmi.android.tv.api.hk;

import java.util.HashMap;
import java.util.Map;

/**
 * 海阔播放解析结果（M4）。
 *
 * <p>由 {@link HkRouter#play(String)} 的分流链产出：真播放地址 + 随地址的请求头
 * （如 {@code ;{Cookie@..&&Referer@..}} 拆出来的）+ 可选字幕地址。</p>
 */
public class HkPlay {

    private String url = "";
    private final Map<String, String> headers = new HashMap<>();
    /**
     * P2 多线路 JSON 的三列保全（8.83 PlayData 语义：urls/names/headers 按下标对齐，
     * 逐线路取、禁止跨线路合并）。单地址路径沿用 url/headers 旧字段。
     */
    private final java.util.List<String> urls = new java.util.ArrayList<>();
    private final java.util.List<String> names = new java.util.ArrayList<>();
    private final java.util.List<java.util.Map<String, String>> lineHeaders = new java.util.ArrayList<>();

    public java.util.List<String> getUrls() {
        return urls;
    }

    public void addUrl(String u) {
        if (u != null && !u.isEmpty()) urls.add(u);
    }

    public java.util.List<String> getNames() {
        return names;
    }

    public void addName(String n) {
        names.add(n == null ? "" : n);
    }

    public java.util.List<java.util.Map<String, String>> getLineHeaders() {
        return lineHeaders;
    }

    public void addLineHeaders(java.util.Map<String, String> h) {
        lineHeaders.add(h == null ? new java.util.HashMap<>() : h);
    }

    /** 按下标取某条线路的地址（越界回首条；无多线时回 url 旧字段）。 */
    public String getUrl(int line) {
        if (urls.isEmpty()) return getUrl();
        return urls.get(line >= 0 && line < urls.size() ? line : 0);
    }

    /** 按下标取某条线路的请求头（对齐 8.83 getHeaders(url,playData,i)；无多线时回旧字段）。 */
    public java.util.Map<String, String> getHeaders(int line) {
        if (!lineHeaders.isEmpty()) {
            java.util.Map<String, String> m = lineHeaders.get(line >= 0 && line < lineHeaders.size() ? line : 0);
            if (m != null && !m.isEmpty()) return m;
        }
        return getHeaders();
    }

    /** 按下标取某条线路的名称（越界回首条；无多线时回空）。 */
    public String getName(int line) {
        if (names.isEmpty()) return "";
        return names.get(line >= 0 && line < names.size() ? line : 0);
    }
    private String subtitle = "";
    /** P1：#isM3u8# 强制按 m3u8 处理。 */
    private boolean forceM3u8;
    /** P1：#concat# 多段地址拼接播放（url 里以 && 或换行分隔的多段）。 */
    private boolean concat;
    /** P1：#fastPlayMode# / #threads=N# 极速模式标记（透传给播放器）。 */
    private boolean fastPlayMode;
    private int threads;
    /**
     * P1/G49：{urls:[], audioUrls:[]} 音视频分离时的音频地址全表（8.83 PlayData
     * audioUrls 是 List 全量）。旧形只留首条单值（HkRouter 绕补、且全树无消费方），
     * G49 归模型正路：整表承载，getAudioUrl() 只作首条便捷口。
     */
    private final java.util.List<String> audioUrls = new java.util.ArrayList<>();
    /** G49/G59：PlayData 附带字段 danmu（弹幕地址），经 SiteApi 接 Result 弹幕面。 */
    private String danmu = "";
    /** G49/G59：PlayData 附带字段 lyric（歌词），承载至本结果为止（见回执记名）。 */
    private String lyric = "";
    /**
     * 本结果是否经过 pics:// 解析（parsePicsInto 跑过）：消费方据此区分
     * 「不是漫画」与「是漫画但拆完无图」——后者不许静默落进视频装配，
     * 须强制留底＋给可见错（SiteApi hkPlayerContent 空 pics 守卫）。
     */
    private boolean picsAttempted;
    /** P1：pics:// 漫画多图模式的图片列表（非视频，UI 提示不支持）。 */
    private final java.util.List<String> pics = new java.util.ArrayList<>();
    /**
     * 产出本结果的真实页面地址（懒解析/规则求值实际跑在哪个页面上）：
     * pics 分流进阅读器时作会话 pageUrl（图片组装的页面上下文）。
     * 集地址本身（尤其 pics:// 串全文）不是页面地址，绝不许拿它冒充——
     * 冒充时组装层算出假域、自动 Referer 恒不成立（漫画黑屏复核报告 §3）。
     * play 现算不出产出页时留空，消费方宁可空着走自动判定。
     */
    private String pageUrl = "";
    /** P1：ed2k/magnet/thunder/ftp 等特殊协议类型（透传给播放器前识别）。 */
    private String playType = "";
    /**
     * 官 dealLazyRule 收口标记（账 heji-subrule-sort-20261114）：本次
     * {@code @lazyRule=} 求值只起副作用（putMyVar + refreshPage，如容器
     * 设置页排序两段式的第一点、启用/禁用翻转），回址是陪衬值（多为
     * {@code hiker://empty}），不是播放目标。置位时 url 已清空，调用方
     * 应重刷当前列表让新闭包生效，不进播放链、也不许按「解析失败」报错。
     * 与 {@link HkDetail#isTabSwitch()} 同口径（详情预解析路的变化检测）。
     */
    private boolean refreshOnly;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url == null ? "" : url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void putHeader(String key, String value) {
        if (key != null && value != null) headers.put(key, value);
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle == null ? "" : subtitle;
    }

    public boolean isForceM3u8() {
        return forceM3u8;
    }

    public void setForceM3u8(boolean forceM3u8) {
        this.forceM3u8 = forceM3u8;
    }

    public boolean isConcat() {
        return concat;
    }

    public void setConcat(boolean concat) {
        this.concat = concat;
    }

    public boolean isFastPlayMode() {
        return fastPlayMode;
    }

    public void setFastPlayMode(boolean fastPlayMode) {
        this.fastPlayMode = fastPlayMode;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public java.util.List<String> getAudioUrls() {
        return audioUrls;
    }

    public void addAudioUrl(String u) {
        if (u != null && !u.isEmpty()) audioUrls.add(u);
    }

    /** 音频地址首条（无则 ""）——旧单值口的等价便捷口。 */
    public String getAudioUrl() {
        return audioUrls.isEmpty() ? "" : audioUrls.get(0);
    }

    public String getDanmu() {
        return danmu == null ? "" : danmu;
    }

    public void setDanmu(String danmu) {
        this.danmu = danmu == null ? "" : danmu;
    }

    public String getLyric() {
        return lyric == null ? "" : lyric;
    }

    public void setLyric(String lyric) {
        this.lyric = lyric == null ? "" : lyric;
    }

    public String getPageUrl() {
        return pageUrl == null ? "" : pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl == null ? "" : pageUrl;
    }

    public java.util.List<String> getPics() {
        return pics;
    }

    public void addPic(String pic) {
        if (pic != null && !pic.isEmpty()) pics.add(pic);
    }

    public boolean isPicsAttempted() {
        return picsAttempted;
    }

    public void setPicsAttempted(boolean picsAttempted) {
        this.picsAttempted = picsAttempted;
    }

    public String getPlayType() {
        return playType == null ? "" : playType;
    }

    public void setPlayType(String playType) {
        this.playType = playType == null ? "" : playType;
    }

    public boolean isRefreshOnly() {
        return refreshOnly;
    }

    public void setRefreshOnly(boolean refreshOnly) {
        this.refreshOnly = refreshOnly;
    }

    public boolean isEmpty() {
        return (url == null || url.isEmpty()) && pics.isEmpty();
    }
}
