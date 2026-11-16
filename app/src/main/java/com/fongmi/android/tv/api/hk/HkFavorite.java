package com.fongmi.android.tv.api.hk;

import com.google.gson.annotations.SerializedName;

/**
 * 海阔详情收藏记录：对齐 8.83 ViewCollection 的关键字段
 * （MTitle / CUrl / picUrl / group / params / time）。
 * 查找键 = ruleTitle + episodeUrl（对齐原版 CUrl + MTitle 唯一定位）。
 * 字段名由 @SerializedName 钉死：记录经 SharedPreferences 跨版本往返，
 * 本类不在保名名单内，R8 改名会让旧版存档的键与新版对不上、收藏静默丢失
 * （2026-10-29 同型扫，账 hk-list-cast-crash-20261029）。
 */
public class HkFavorite {

    @SerializedName("title")
    private String title = "";
    @SerializedName("ruleTitle")
    private String ruleTitle = "";
    @SerializedName("ruleUrl")
    private String ruleUrl = "";
    @SerializedName("episodeUrl")
    private String episodeUrl = "";
    @SerializedName("picUrl")
    private String picUrl = "";
    @SerializedName("group")
    private String group = "";
    /** 在直达播放页收藏的：episodeUrl 存的是集址不是条目址，再入走直达支回放。 */
    @SerializedName("direct")
    private boolean direct;
    /** 直达收藏当时的线路名（再入直达支用）。 */
    @SerializedName("flag")
    private String flag = "";
    /**
     * 品类裁决（断点乙）：建收藏时首入分流的品类随记录带走（值与键标记
     * 同字面：video/novel/audio/music），再入先读它定归属与分签；空＝
     * 旧记录，再入回退整规则分类。条目级借道（帖内「播放音频」钮）靠它
     * 才回得去借面。
     */
    @SerializedName("kind")
    private String kind = "";
    /**
     * 直达收藏的整表选集载荷（断点甲）：在播放器内收藏时把交接载荷原样
     * 存下，再入按它重摆全表；从历史行转存等无载荷来源时为空，再入回
     * 退按行自带字段拼单集载荷。
     */
    @SerializedName("episodes")
    private String episodes = "";
    @SerializedName("time")
    private long time;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public String getRuleTitle() {
        return ruleTitle;
    }

    public void setRuleTitle(String ruleTitle) {
        this.ruleTitle = ruleTitle == null ? "" : ruleTitle;
    }

    public String getRuleUrl() {
        return ruleUrl;
    }

    public void setRuleUrl(String ruleUrl) {
        this.ruleUrl = ruleUrl == null ? "" : ruleUrl;
    }

    public String getEpisodeUrl() {
        return episodeUrl;
    }

    public void setEpisodeUrl(String episodeUrl) {
        this.episodeUrl = episodeUrl == null ? "" : episodeUrl;
    }

    public String getPicUrl() {
        return picUrl;
    }

    public void setPicUrl(String picUrl) {
        this.picUrl = picUrl == null ? "" : picUrl;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group == null ? "" : group;
    }

    public boolean isDirect() {
        return direct;
    }

    public void setDirect(boolean direct) {
        this.direct = direct;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag == null ? "" : flag;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind == null ? "" : kind;
    }

    public String getEpisodes() {
        return episodes;
    }

    public void setEpisodes(String episodes) {
        this.episodes = episodes == null ? "" : episodes;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
