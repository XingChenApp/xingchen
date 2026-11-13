package com.example.hikerview.model;

import com.alibaba.fastjson.annotation.JSONField;
import org.litepal.annotation.Column;
import org.litepal.crud.LitePalSupport;

import java.util.Date;

/**
 * 照官 8.83 本体逐字转写（官源 smali/com/example/hikerview/model/ViewHistory.smali）。
 * 继承 LitePalSupport、implements Comparable<ViewHistory>；selected/selecting 带 fastjson
 * serialize/deserialize=false 与 @Column(ignore=true) 照官；compareTo/copyFrom（10 字段直赋）照官。
 */
public class ViewHistory extends LitePalSupport implements Comparable<ViewHistory> {

    private String extraData;
    private String group;
    private String lastClick;
    private String params;
    private String picUrl;
    private String ruleBaseUrl;
    private Date time;
    private String title;
    private String type;
    private String url;
    private String videoUrl;
    @JSONField(deserialize = false, serialize = false)
    @Column(ignore = true)
    private boolean selected;
    @JSONField(deserialize = false, serialize = false)
    @Column(ignore = true)
    private boolean selecting;

    public String getExtraData() {
        return this.extraData;
    }

    public void setExtraData(String str) {
        this.extraData = str;
    }

    public String getGroup() {
        return this.group;
    }

    public void setGroup(String str) {
        this.group = str;
    }

    public String getLastClick() {
        return this.lastClick;
    }

    public void setLastClick(String str) {
        this.lastClick = str;
    }

    public String getParams() {
        return this.params;
    }

    public void setParams(String str) {
        this.params = str;
    }

    public String getPicUrl() {
        return this.picUrl;
    }

    public void setPicUrl(String str) {
        this.picUrl = str;
    }

    public String getRuleBaseUrl() {
        return this.ruleBaseUrl;
    }

    public void setRuleBaseUrl(String str) {
        this.ruleBaseUrl = str;
    }

    public Date getTime() {
        return this.time;
    }

    public void setTime(Date date) {
        this.time = date;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String str) {
        this.title = str;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String str) {
        this.type = str;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String str) {
        this.url = str;
    }

    public String getVideoUrl() {
        return this.videoUrl;
    }

    public void setVideoUrl(String str) {
        this.videoUrl = str;
    }

    public boolean isSelected() {
        return this.selected;
    }

    public void setSelected(boolean z) {
        this.selected = z;
    }

    public boolean isSelecting() {
        return this.selecting;
    }

    public void setSelecting(boolean z) {
        this.selecting = z;
    }

    public long getId() {
        return getBaseObjId();
    }

    @Override
    public int compareTo(ViewHistory other) {
        if (other == null) {
            return 0;
        }
        if (other.getTime() == null) {
            return 1;
        }
        if (getTime() == null) {
            return -1;
        }
        return other.getTime().compareTo(getTime());
    }

    public ViewHistory copyFrom(ViewHistory other) {
        this.type = other.type;
        this.ruleBaseUrl = other.ruleBaseUrl;
        this.url = other.url;
        this.title = other.title;
        this.group = other.group;
        this.params = other.params;
        this.lastClick = other.lastClick;
        this.extraData = other.extraData;
        this.picUrl = other.picUrl;
        this.videoUrl = other.videoUrl;
        return this;
    }
}