package com.example.hikerview.model;

import org.litepal.crud.LitePalSupport;

/**
 * 照官 8.83 本体逐字转写（官源 smali/com/example/hikerview/model/PlayerPosHis.smali）。
 * 继承 LitePalSupport；双构造、copyFrom（playUrl/pos）、save/saveOrUpdate 置 gmtModified 照官。
 */
public class PlayerPosHis extends LitePalSupport {

    private long gmtModified;
    private boolean hide;
    private String playUrl;
    private int pos;

    public PlayerPosHis() {
    }

    public PlayerPosHis(String playUrl, int pos) {
        this.playUrl = playUrl;
        this.pos = pos;
    }

    public PlayerPosHis copyFrom(PlayerPosHis other) {
        this.playUrl = other.playUrl;
        this.pos = other.pos;
        return this;
    }

    public long getGmtModified() {
        return this.gmtModified;
    }

    public void setGmtModified(long j) {
        this.gmtModified = j;
    }

    public boolean isHide() {
        return this.hide;
    }

    public void setHide(boolean z) {
        this.hide = z;
    }

    public String getPlayUrl() {
        return this.playUrl;
    }

    public void setPlayUrl(String str) {
        this.playUrl = str;
    }

    public int getPos() {
        return this.pos;
    }

    public void setPos(int i) {
        this.pos = i;
    }

    @Override
    public boolean save() {
        this.gmtModified = System.currentTimeMillis();
        return super.save();
    }

    @Override
    public boolean saveOrUpdate(String... conditions) {
        this.gmtModified = System.currentTimeMillis();
        return super.saveOrUpdate(conditions);
    }
}