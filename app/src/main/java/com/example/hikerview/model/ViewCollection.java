package com.example.hikerview.model;

import org.litepal.annotation.Column;
import org.litepal.crud.LitePalSupport;

import java.util.Date;

/**
 * 照官 8.83 本体逐字转写（官源 smali/com/example/hikerview/model/ViewCollection.smali）。
 * 继承 LitePalSupport、implements Comparable<ViewCollection>；isUpdating 带 @Column(ignore=true) 照官；
 * compareTo（对方 time 为准的逆序）、copyFrom（11 字段直赋、isUpdating 不拷）、save 置新 Date 照官。
 */
public class ViewCollection extends LitePalSupport implements Comparable<ViewCollection> {

    private String CUrl;
    private String MITitle;
    private String MTitle;
    private String desc;
    private String extraData;
    private String group;
    private String lastClick;
    private String params;
    private String picUrl;
    private Date time;
    private String videoUrl;
    @Column(ignore = true)
    private boolean isUpdating;

    public String getCUrl() {
        return this.CUrl;
    }

    public void setCUrl(String str) {
        this.CUrl = str;
    }

    public String getMITitle() {
        return this.MITitle;
    }

    public void setMITitle(String str) {
        this.MITitle = str;
    }

    public String getMTitle() {
        return this.MTitle;
    }

    public void setMTitle(String str) {
        this.MTitle = str;
    }

    public String getDesc() {
        return this.desc;
    }

    public void setDesc(String str) {
        this.desc = str;
    }

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

    public Date getTime() {
        return this.time;
    }

    public void setTime(Date date) {
        this.time = date;
    }

    public String getVideoUrl() {
        return this.videoUrl;
    }

    public void setVideoUrl(String str) {
        this.videoUrl = str;
    }

    public boolean getUpdating() {
        return this.isUpdating;
    }

    public void setUpdating(boolean z) {
        this.isUpdating = z;
    }

    public long getId() {
        return getBaseObjId();
    }

    @Override
    public int compareTo(ViewCollection other) {
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

    public ViewCollection copyFrom(ViewCollection other) {
        this.CUrl = other.getCUrl();
        this.MITitle = other.getMITitle();
        this.MTitle = other.getMTitle();
        this.desc = other.desc;
        this.extraData = other.extraData;
        this.group = other.group;
        this.lastClick = other.lastClick;
        this.params = other.params;
        this.picUrl = other.picUrl;
        this.time = other.time;
        this.videoUrl = other.videoUrl;
        return this;
    }

    @Override
    public boolean save() {
        setTime(new Date());
        return super.save();
    }

    public boolean saveNoUpdateTime() {
        return super.save();
    }

    public boolean saveNotUpdateTime() {
        return super.save();
    }
}