package com.example.hikerview.model;

import android.text.TextUtils;

import com.alibaba.fastjson.JSON;

/**
 * 照官 8.83 本体逐字转写（官源 smali/com/example/hikerview/model/ViewCollectionExtraData.smali）。
 * 纯模型（不继承 LitePalSupport，照官）；双构造（jumpStart/JumpEndDuration 初值 -1 照官）、
 * fromJson（空串回新实例）/toJson 走 fastjson 照官。
 */
public class ViewCollectionExtraData {

    private long collectionId;
    private boolean hasUpdatedChapter;
    private boolean isCustomJump;
    private boolean isCustomPlayer;
    private int jumpEndDuration;
    private int jumpStartDuration;
    private String lastChapterError;
    private String lastChapterStatus;
    private long lastChapterUpdateTime;
    private boolean newWindow;
    private int pageIndex;
    private int player;
    private int switchIndex;
    private String windowId;

    public ViewCollectionExtraData() {
        this.jumpStartDuration = -1;
        this.jumpEndDuration = -1;
    }

    public ViewCollectionExtraData(long collectionId) {
        this.jumpStartDuration = -1;
        this.jumpEndDuration = -1;
        this.collectionId = collectionId;
    }

    public static ViewCollectionExtraData fromJson(String json) {
        if (TextUtils.isEmpty(json)) {
            return new ViewCollectionExtraData();
        }
        ViewCollectionExtraData data = JSON.parseObject(json, ViewCollectionExtraData.class);
        return data == null ? new ViewCollectionExtraData() : data;
    }

    public long getCollectionId() {
        return this.collectionId;
    }

    public void setCollectionId(long j) {
        this.collectionId = j;
    }

    public boolean isHasUpdatedChapter() {
        return this.hasUpdatedChapter;
    }

    public void setHasUpdatedChapter(boolean z) {
        this.hasUpdatedChapter = z;
    }

    public boolean isCustomJump() {
        return this.isCustomJump;
    }

    public void setCustomJump(boolean z) {
        this.isCustomJump = z;
    }

    public boolean isCustomPlayer() {
        return this.isCustomPlayer;
    }

    public void setCustomPlayer(boolean z) {
        this.isCustomPlayer = z;
    }

    public int getJumpEndDuration() {
        return this.jumpEndDuration;
    }

    public void setJumpEndDuration(int i) {
        this.jumpEndDuration = i;
    }

    public int getJumpStartDuration() {
        return this.jumpStartDuration;
    }

    public void setJumpStartDuration(int i) {
        this.jumpStartDuration = i;
    }

    public String getLastChapterError() {
        return this.lastChapterError;
    }

    public void setLastChapterError(String str) {
        this.lastChapterError = str;
    }

    public String getLastChapterStatus() {
        return this.lastChapterStatus;
    }

    public void setLastChapterStatus(String str) {
        this.lastChapterStatus = str;
    }

    public long getLastChapterUpdateTime() {
        return this.lastChapterUpdateTime;
    }

    public void setLastChapterUpdateTime(long j) {
        this.lastChapterUpdateTime = j;
    }

    public boolean isNewWindow() {
        return this.newWindow;
    }

    public void setNewWindow(boolean z) {
        this.newWindow = z;
    }

    public int getPageIndex() {
        return this.pageIndex;
    }

    public void setPageIndex(int i) {
        this.pageIndex = i;
    }

    public int getPlayer() {
        return this.player;
    }

    public void setPlayer(int i) {
        this.player = i;
    }

    public int getSwitchIndex() {
        return this.switchIndex;
    }

    public void setSwitchIndex(int i) {
        this.switchIndex = i;
    }

    public String getWindowId() {
        return this.windowId;
    }

    public void setWindowId(String str) {
        this.windowId = str;
    }

    public String toJson() {
        return JSON.toJSONString(this);
    }
}