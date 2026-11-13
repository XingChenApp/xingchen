package com.fongmi.android.tv.api.hk.event.web;

/**
 * 8.83 com.example.hikerview.event.web.BlobDownloadProgressEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 网页桥 fy_bridge_app.downloadBlobUpdate(url, progress) 时发布。
 */
public class BlobDownloadProgressEvent {

    private final String url;
    private final String progress;
    private boolean finished;

    public BlobDownloadProgressEvent(String url, String progress) {
        this.url = url;
        this.progress = progress;
    }

    public String getUrl() {
        return url;
    }

    public String getProgress() {
        return progress;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }
}
