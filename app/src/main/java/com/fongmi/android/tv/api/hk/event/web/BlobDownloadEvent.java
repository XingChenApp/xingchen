package com.fongmi.android.tv.api.hk.event.web;

/**
 * 8.83 com.example.hikerview.event.web.BlobDownloadEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 网页桥 fy_bridge_app.downloadBlob(url, fileName, headerMap, result) 时发布；
 * 官由 WebViewActivity 订阅后经 BlobDownloadHelper 落盘，咱家暂无对应消费面（记名，见第八阶段回执）。
 */
public class BlobDownloadEvent {

    private final String url;
    private final String fileName;
    private final String headerMap;
    private final String result;
    private boolean finished;

    public BlobDownloadEvent(String url, String fileName) {
        this(url, fileName, null);
    }

    public BlobDownloadEvent(String url, String fileName, String headerMap) {
        this(url, fileName, headerMap, null);
    }

    public BlobDownloadEvent(String url, String fileName, String headerMap, String result) {
        this.url = url;
        this.fileName = fileName;
        this.headerMap = headerMap;
        this.result = result;
    }

    public String getUrl() {
        return url;
    }

    public String getFileName() {
        return fileName;
    }

    public String getHeaderMap() {
        return headerMap;
    }

    public String getResult() {
        return result;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }
}
