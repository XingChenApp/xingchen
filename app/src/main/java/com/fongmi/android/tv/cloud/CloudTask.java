package com.fongmi.android.tv.cloud;

/**
 * 离线下载任务
 */
public class CloudTask {

    private String hash;
    private String name;
    private long size;
    private int status;     // 任务状态码
    private int percent;    // 进度 0-100
    private String statusText;

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public int getPercent() { return percent; }
    public void setPercent(int percent) { this.percent = percent; }

    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
}
