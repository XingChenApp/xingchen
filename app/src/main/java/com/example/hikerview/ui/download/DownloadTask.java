package com.example.hikerview.ui.download;

import com.example.hikerview.service.parser.HttpParser;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 照官 8.83 本体逐字转写（官源 smali_classes2/com/example/hikerview/ui/download/DownloadTask.smali，
 * 全类照落）。构造初值（AtomicLong 三件、status="ready"、failedReason=""、rootPath 取
 * DownloadConfig.rootPath）与 setUrl 三连（url/headers/downloadUrl）逐字照官。
 */
public class DownloadTask {

    private String contentType;
    private boolean continueDownload;
    private long currentSpeed;
    private int downloadThread;
    private String downloadUrl;
    private String failedReason;
    private String fileExtension;
    private String fileName;
    private String film;
    private Map<String, String> headers;
    private boolean ignoreError;
    private long lastClearSpeedTime;
    private AtomicLong lastDurationDownloadSize;
    private String originalTitle;
    private String rootPath;
    private AtomicLong size;
    private String sourcePageTitle;
    private String sourcePageUrl;
    private String status;
    private String subtitle;
    private String suffix;
    private String taskId;
    private AtomicLong totalDownloaded;
    private String url;
    private String videoType;

    public DownloadTask(String taskId, String fileName, String videoType, String fileExtension, String url, String sourcePageUrl, String sourcePageTitle, Long size) {
        this.size = new AtomicLong(0L);
        this.totalDownloaded = new AtomicLong(0L);
        this.lastDurationDownloadSize = new AtomicLong(0L);
        this.status = "ready";
        this.failedReason = "";
        this.taskId = taskId;
        this.fileName = fileName;
        this.videoType = videoType;
        this.fileExtension = fileExtension;
        setUrl(url);
        this.sourcePageUrl = sourcePageUrl;
        this.sourcePageTitle = sourcePageTitle;
        this.size.set(size.longValue());
        this.rootPath = DownloadConfig.rootPath;
    }

    public String getTaskId() {
        return this.taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getVideoType() {
        return this.videoType;
    }

    public void setVideoType(String videoType) {
        this.videoType = videoType;
    }

    public String getFileExtension() {
        return this.fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getFileName() {
        return this.fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Map<String, String> getHeaders() {
        return this.headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public boolean isContinueDownload() {
        return this.continueDownload;
    }

    public void setContinueDownload(boolean continueDownload) {
        this.continueDownload = continueDownload;
    }

    public String getDownloadUrl() {
        return this.downloadUrl;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
        this.headers = HttpParser.getHeaders(url);
        this.downloadUrl = HttpParser.getThirdDownloadSource(url);
    }

    public AtomicLong getSize() {
        return this.size;
    }

    public AtomicLong getTotalDownloaded() {
        return this.totalDownloaded;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getLastClearSpeedTime() {
        return this.lastClearSpeedTime;
    }

    public void setLastClearSpeedTime(long lastClearSpeedTime) {
        this.lastClearSpeedTime = lastClearSpeedTime;
    }

    public long getCurrentSpeed() {
        return this.currentSpeed;
    }

    public void setCurrentSpeed(long currentSpeed) {
        this.currentSpeed = currentSpeed;
    }

    public AtomicLong getLastDurationDownloadSize() {
        return this.lastDurationDownloadSize;
    }

    public void setVideoType2(String videoType) {
        this.videoType = videoType;
    }

    public String getSourcePageUrl() {
        return this.sourcePageUrl;
    }

    public void setSourcePageUrl(String sourcePageUrl) {
        this.sourcePageUrl = sourcePageUrl;
    }

    public String getSourcePageTitle() {
        return this.sourcePageTitle;
    }

    public void setSourcePageTitle(String sourcePageTitle) {
        this.sourcePageTitle = sourcePageTitle;
    }

    public void setFailedReason(String failedReason) {
        this.failedReason = failedReason;
    }

    public String getFailedReason() {
        return this.failedReason;
    }

    public String getFilm() {
        return this.film;
    }

    public void setFilm(String film) {
        this.film = film;
    }

    public String getSubtitle() {
        return this.subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getOriginalTitle() {
        return this.originalTitle;
    }

    public void setOriginalTitle(String originalTitle) {
        this.originalTitle = originalTitle;
    }

    public String getRootPath() {
        return this.rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getSuffix() {
        return this.suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public boolean isIgnoreError() {
        return this.ignoreError;
    }

    public void setIgnoreError(boolean ignoreError) {
        this.ignoreError = ignoreError;
    }

    public String getContentType() {
        return this.contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public int getDownloadThread() {
        return this.downloadThread;
    }

    public void setDownloadThread(int downloadThread) {
        this.downloadThread = downloadThread;
    }
}
