package com.example.hikerview.ui.browser.model;

import com.example.hikerview.ui.download.VideoFormat;

import java.util.Objects;

/**
 * 照官逐字转写（官包 smali_classes2/com/example/hikerview/ui/browser/model/
 * VideoInfo.smali 全文）：嗅探栈一员——detectVideoComplex 的返回类；
 * detectImageType 初值 "" 照官构造器；equals/hashCode 照官只比
 * sourcePageUrl 与 sourcePageTitle 两字段，随④团转写。
 */
public class VideoInfo {

    private String detectImageType;
    private double duration;
    private String fileName;
    private long size;
    private String sourcePageTitle;
    private String sourcePageUrl;
    private String url;
    private VideoFormat videoFormat;

    public VideoInfo() {
        this.detectImageType = "";
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public VideoFormat getVideoFormat() {
        return videoFormat;
    }

    public void setVideoFormat(VideoFormat videoFormat) {
        this.videoFormat = videoFormat;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public String getSourcePageUrl() {
        return sourcePageUrl;
    }

    public void setSourcePageUrl(String sourcePageUrl) {
        this.sourcePageUrl = sourcePageUrl;
    }

    public String getSourcePageTitle() {
        return sourcePageTitle;
    }

    public void setSourcePageTitle(String sourcePageTitle) {
        this.sourcePageTitle = sourcePageTitle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        VideoInfo videoInfo = (VideoInfo) o;
        return Objects.equals(sourcePageUrl, videoInfo.sourcePageUrl) && Objects.equals(sourcePageTitle, videoInfo.sourcePageTitle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourcePageUrl, sourcePageTitle);
    }

    public String getDetectImageType() {
        return detectImageType;
    }

    public void setDetectImageType(String detectImageType) {
        this.detectImageType = detectImageType;
    }
}
