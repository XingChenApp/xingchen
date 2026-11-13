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

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
