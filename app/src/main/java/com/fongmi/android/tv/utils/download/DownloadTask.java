package com.fongmi.android.tv.utils.download;

import java.io.File;

/**
 * 下载任务模型
 */
public class DownloadTask {

    public static final int STATUS_WAITING = 0;
    public static final int STATUS_DOWNLOADING = 1;
    public static final int STATUS_PAUSED = 2;
    public static final int STATUS_COMPLETED = 3;
    public static final int STATUS_ERROR = 4;
    public static final int STATUS_MERGING = 5;

    private final String id;
    private final String url;
    private String name;
    private String filePath;
    private int status = STATUS_WAITING;
    private long totalBytes = -1;
    private long downloadedBytes = 0;
    private String error;
    private boolean isM3u8;
    private long createTime;

    public DownloadTask(String url, String name) {
        this.id = String.valueOf(System.currentTimeMillis());
        this.url = url;
        this.name = name;
        this.isM3u8 = url != null && (url.contains(".m3u8") || url.contains("m3u8"));
        this.createTime = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public String getUrl() { return url; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
    public long getDownloadedBytes() { return downloadedBytes; }
    public void setDownloadedBytes(long downloadedBytes) { this.downloadedBytes = downloadedBytes; }
    public void addDownloadedBytes(long bytes) { this.downloadedBytes += bytes; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public boolean isM3u8() { return isM3u8; }
    public void setM3u8(boolean m3u8) { isM3u8 = m3u8; }
    public long getCreateTime() { return createTime; }

    public int getProgress() {
        if (totalBytes <= 0) return 0;
        return (int) (downloadedBytes * 100 / totalBytes);
    }

    public String getStatusText() {
        switch (status) {
            case STATUS_WAITING: return "等待中";
            case STATUS_DOWNLOADING: return "下载中 " + getProgress() + "%";
            case STATUS_PAUSED: return "已暂停";
            case STATUS_COMPLETED: return "已完成";
            case STATUS_ERROR: return "出错: " + (error != null ? error : "");
            case STATUS_MERGING: return "合并中...";
            default: return "";
        }
    }
}
