package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 下载任务落盘记录（下载外壳件 1）：内存任务表 DownloadTask 的持久镜像，
 * App 重启后由 DownloadManager.restore() 逐条对账灌回。字段一次定全
 * （含 pic/qualityName/headersJson/storageUri 预留列），避免同批二次升库。
 * headersJson 必须存：多数源没请求头直接 403，续传靠它。
 */
@Entity
public class DownloadRecord {

    /** 与 DownloadTask 的 id 同值（毫秒-进程序号，C-24 已保唯一） */
    @PrimaryKey
    @NonNull
    private String id = "";
    private String url;
    private String name;
    private String filePath;
    /** DownloadTask 的 STATUS_* 原值 */
    private int status;
    private long totalBytes;
    private long downloadedBytes;
    private boolean isM3u8;
    private long createTime;
    /** 完成时刻，未完成为 0（容量清理的 lastActivity 口径之一） */
    private long finishTime;
    /** 最近一次从下载列表点开播放的时刻，未播为 0（容量清理口径） */
    private long lastPlayTime;
    /** 请求头 Map 的 Gson JSON，续传用 */
    private String headersJson;
    /** 封面 url（任务行小封面用），无封面为空 */
    private String pic;
    /** 入队时实际选中的清晰度名快照（件 5），单档/自动为空 */
    private String qualityName;
    private String error;
    /** 预留：将来「下完搬运到自选目录」立批时存 document uri，本批恒空 */
    private String storageUri;
    /** m3u8 列表内变体带宽（件 5），0＝自动最高 */
    private long variantBandwidth;

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public void setDownloadedBytes(long downloadedBytes) {
        this.downloadedBytes = downloadedBytes;
    }

    public boolean isM3u8() {
        return isM3u8;
    }

    public void setM3u8(boolean m3u8) {
        isM3u8 = m3u8;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(long finishTime) {
        this.finishTime = finishTime;
    }

    public long getLastPlayTime() {
        return lastPlayTime;
    }

    public void setLastPlayTime(long lastPlayTime) {
        this.lastPlayTime = lastPlayTime;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public void setHeadersJson(String headersJson) {
        this.headersJson = headersJson;
    }

    public String getPic() {
        return pic;
    }

    public void setPic(String pic) {
        this.pic = pic;
    }

    public String getQualityName() {
        return qualityName;
    }

    public void setQualityName(String qualityName) {
        this.qualityName = qualityName;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String storageUri) {
        this.storageUri = storageUri;
    }

    public long getVariantBandwidth() {
        return variantBandwidth;
    }

    public void setVariantBandwidth(long variantBandwidth) {
        this.variantBandwidth = variantBandwidth;
    }
}
