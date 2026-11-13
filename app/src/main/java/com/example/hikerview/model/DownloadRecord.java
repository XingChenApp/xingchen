package com.example.hikerview.model;

import com.alibaba.fastjson.annotation.JSONField;

/**
 * 照官 8.83 本体移植（jadx 官包源）：下载记录模型。与 Bookmark
 * 同口径——官 LitePal 基类与 @Column 注解摘除落纯模型，id 改自有
 * 字段；fastjson 注解（lastPlay 不序列化）照官保留。
 * 记名未落：以 DownloadTask 为参的构造与 update 两法——耦合官下载
 * 栈（DownloadTask/DownloadConfig），星辰未落该栈，随之再补。
 */
public class DownloadRecord {

    private long id;
    private long currentSpeed;
    private String failedReason;
    private String fileExtension;
    private String fileName;
    private String film;
    private long finishedTime;
    private long lastClearSpeedTime;

    @JSONField(deserialize = false, serialize = false)
    private boolean lastPlay;
    private int order;
    private String playPos;
    private String rootPath;
    private long saveTime;
    private boolean selected;
    private boolean showTime;
    private long size;
    private String sourcePageTitle;
    private String sourcePageUrl;
    private String status;
    private String subtitle;
    private String taskId;
    private long totalDownloaded;
    private String url;
    private String videoType;

    public DownloadRecord() {
        this.failedReason = "";
    }

    public String getTaskId() {
        return this.taskId;
    }

    public void setTaskId(String str) {
        this.taskId = str;
    }

    public String getFileName() {
        return this.fileName;
    }

    public void setFileName(String str) {
        this.fileName = str;
    }

    public String getFullName() {
        String str = "player/m3u8".equals(this.fileExtension) ? "m3u8" : this.fileExtension;
        String str2 = this.fileName;
        if (str2 != null && str != null && !str2.contains("." + str)) {
            return this.fileName + "." + str;
        }
        return this.fileName;
    }

    public String getVideoType() {
        return this.videoType;
    }

    public void setVideoType(String str) {
        this.videoType = str;
    }

    public String getFileExtension() {
        return this.fileExtension;
    }

    public void setFileExtension(String str) {
        this.fileExtension = str;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String str) {
        this.url = str;
    }

    public String getSourcePageUrl() {
        return this.sourcePageUrl;
    }

    public void setSourcePageUrl(String str) {
        this.sourcePageUrl = str;
    }

    public String getSourcePageTitle() {
        return this.sourcePageTitle;
    }

    public void setSourcePageTitle(String str) {
        this.sourcePageTitle = str;
    }

    public long getSize() {
        return this.size;
    }

    public void setSize(long j) {
        this.size = j;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String str) {
        this.status = str;
    }

    public String getFailedReason() {
        return this.failedReason;
    }

    public void setFailedReason(String str) {
        this.failedReason = str;
    }

    public long getTotalDownloaded() {
        return this.totalDownloaded;
    }

    public void setTotalDownloaded(long j) {
        this.totalDownloaded = j;
    }

    public long getCurrentSpeed() {
        return this.currentSpeed;
    }

    public void setCurrentSpeed(long j) {
        this.currentSpeed = j;
    }

    public long getLastClearSpeedTime() {
        return this.lastClearSpeedTime;
    }

    public void setLastClearSpeedTime(long j) {
        this.lastClearSpeedTime = j;
    }

    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSaveTime() {
        return this.saveTime;
    }

    public void setSaveTime(long j) {
        this.saveTime = j;
    }

    public long getFinishedTime() {
        return this.finishedTime;
    }

    public void setFinishedTime(long j) {
        this.finishedTime = j;
    }

    public int getOrder() {
        return this.order;
    }

    public void setOrder(int i) {
        this.order = i;
    }

    public boolean isSelected() {
        return this.selected;
    }

    public void setSelected(boolean z) {
        this.selected = z;
    }

    public String getFilm() {
        return this.film;
    }

    public void setFilm(String str) {
        this.film = str;
    }

    public String getSubtitle() {
        return this.subtitle;
    }

    public void setSubtitle(String str) {
        this.subtitle = str;
    }

    public String getPlayPos() {
        return this.playPos;
    }

    public void setPlayPos(String str) {
        this.playPos = str;
    }

    public String getRootPath() {
        return this.rootPath;
    }

    public void setRootPath(String str) {
        this.rootPath = str;
    }

    public boolean isShowTime() {
        return this.showTime;
    }

    public void setShowTime(boolean z) {
        this.showTime = z;
    }

    public boolean isLastPlay() {
        return this.lastPlay;
    }

    public void setLastPlay(boolean z) {
        this.lastPlay = z;
    }
}
