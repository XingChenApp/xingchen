package com.example.hikerview.ui.video;

import com.example.hikerview.model.DownloadRecord;
import com.xunlei.downloadlib.parameter.TorrentFileInfo;

/**
 * 8.83 {@code com.example.hikerview.ui.video.VideoChapter} 的同包名同签名补齐
 * （官包 jadx 反编译原样）。字段类型落点：DownloadRecord 同批补齐；
 * TorrentFileInfo 用星辰 thunder 模块的同名真类。
 */
public class VideoChapter {
    private String cache0;
    private boolean cacheLoading;
    private String codeAndHeader;
    private DownloadRecord downloadRecord;
    private String extra;
    private String memoryTitle;
    private String originalUrl;
    private String picUrl;
    private int realPos;
    private String st;
    private String title;
    private TorrentFileInfo torrentFileInfo;
    private String url;
    private String urlCache;
    private boolean use;

    public boolean isUse() {
        return this.use;
    }

    public void setUse(boolean z) {
        this.use = z;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String str) {
        this.url = str;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String str) {
        this.title = str;
    }

    public DownloadRecord getDownloadRecord() {
        return this.downloadRecord;
    }

    public void setDownloadRecord(DownloadRecord downloadRecord) {
        this.downloadRecord = downloadRecord;
    }

    public String getOriginalUrl() {
        return this.originalUrl;
    }

    public void setOriginalUrl(String str) {
        this.originalUrl = str;
    }

    public String getCodeAndHeader() {
        return this.codeAndHeader;
    }

    public void setCodeAndHeader(String str) {
        this.codeAndHeader = str;
    }

    public String getMemoryTitle() {
        return this.memoryTitle;
    }

    public void setMemoryTitle(String str) {
        this.memoryTitle = str;
    }

    public String getPicUrl() {
        return this.picUrl;
    }

    public void setPicUrl(String str) {
        this.picUrl = str;
    }

    public String getExtra() {
        return this.extra;
    }

    public void setExtra(String str) {
        this.extra = str;
    }

    public String getUrlCache() {
        return this.urlCache;
    }

    public String getCache() {
        String str = this.urlCache;
        setUrlCache(null);
        return str;
    }

    public void setUrlCache(String str) {
        setCacheLoading(false);
        this.urlCache = str;
    }

    public boolean isCacheLoading() {
        return this.cacheLoading;
    }

    public void setCacheLoading(boolean z) {
        this.cacheLoading = z;
    }

    public int getRealPos() {
        return this.realPos;
    }

    public void setRealPos(int i) {
        this.realPos = i;
    }

    public String getCache0() {
        return this.cache0;
    }

    public void setCache0(String str) {
        this.cache0 = str;
    }

    public TorrentFileInfo getTorrentFileInfo() {
        return this.torrentFileInfo;
    }

    public void setTorrentFileInfo(TorrentFileInfo torrentFileInfo) {
        this.torrentFileInfo = torrentFileInfo;
    }

    public String getSt() {
        return this.st;
    }

    public void setSt(String str) {
        this.st = str;
    }
}
