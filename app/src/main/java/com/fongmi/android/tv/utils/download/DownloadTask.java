package com.fongmi.android.tv.utils.download;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

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
    // 下载外壳件 1/5：落盘镜像字段。headers 是续传请求头（恢复后 resume 靠它，
    // 多数源没请求头直接 403）；pic 任务行小封面；qualityName/variantBandwidth
    // 是件 5 选档快照；finishTime/lastPlayTime 供件 6 容量清理口径。
    private java.util.Map<String, String> headers;
    private String pic;
    private String qualityName;
    private long variantBandwidth;
    private long finishTime;
    private long lastPlayTime;
    // 件 4 瞬时字段（不落盘，重启归零、恢复后重新采样）：速度 字节/秒、
    // 剩余秒数（未知 -1）。暂停/完成/出错时由 Manager 采样口径清零。
    private long speedBps;
    private long etaSeconds = -1;
    /** 件 4：已下真实字节（m3u8 的 downloadedBytes 是分片计数，字节口径走本字段；
     * HTTP 任务与 downloadedBytes 同值）。速度采样的数据源，不落盘。 */
    private long realBytes;

    // C-24: 进程内单调递增序号。只用毫秒当 id 时，同毫秒连下两个任务会同 id，
    // 在 DownloadManager 与两个下载器的 Map 里互相覆盖（批量连下可达）。
    private static final AtomicLong ID_SEQUENCE = new AtomicLong(0);

    public DownloadTask(String url, String name) {
        long now = System.currentTimeMillis();
        // id 仍以毫秒开头（排序/可读性不变），尾巴拼进程内唯一序号保证不撞；
        // id 在全仓只作不透明 Map 键使用，无任何按纯数字解析的地方。
        this.id = now + "-" + ID_SEQUENCE.incrementAndGet();
        this.url = url;
        this.name = name;
        this.isM3u8 = url != null && (url.contains(".m3u8") || url.contains("m3u8"));
        this.createTime = now;
    }

    /** 件 1 恢复专用构造：id/createTime 用落盘原值，不新生成（跨进程不撞靠毫秒单调）。 */
    public DownloadTask(String id, String url, String name, long createTime) {
        this.id = id;
        this.url = url;
        this.name = name;
        this.isM3u8 = url != null && (url.contains(".m3u8") || url.contains("m3u8"));
        this.createTime = createTime;
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
    public java.util.Map<String, String> getHeaders() { return headers; }
    public void setHeaders(java.util.Map<String, String> headers) { this.headers = headers; }
    public String getPic() { return pic; }
    public void setPic(String pic) { this.pic = pic; }
    public String getQualityName() { return qualityName; }
    public void setQualityName(String qualityName) { this.qualityName = qualityName; }
    public long getVariantBandwidth() { return variantBandwidth; }
    public void setVariantBandwidth(long variantBandwidth) { this.variantBandwidth = variantBandwidth; }
    public long getFinishTime() { return finishTime; }
    public void setFinishTime(long finishTime) { this.finishTime = finishTime; }
    public long getLastPlayTime() { return lastPlayTime; }
    public void setLastPlayTime(long lastPlayTime) { this.lastPlayTime = lastPlayTime; }
    public long getSpeedBps() { return speedBps; }
    public void setSpeedBps(long speedBps) { this.speedBps = speedBps; }
    public long getEtaSeconds() { return etaSeconds; }
    public void setEtaSeconds(long etaSeconds) { this.etaSeconds = etaSeconds; }
    public long getRealBytes() { return realBytes; }
    public void setRealBytes(long realBytes) { this.realBytes = realBytes; }

    /** 件 4：剩余时间文案（<60 秒「约 N 秒」/<60 分「约 N 分钟」/以上「约 N 小时 M 分钟」） */
    public static String formatEta(long seconds) {
        if (seconds < 0) return "";
        if (seconds < 60) return "约 " + Math.max(1, seconds) + " 秒";
        if (seconds < 3600) return "约 " + (seconds / 60) + " 分钟";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return m > 0 ? "约 " + h + " 小时 " + m + " 分钟" : "约 " + h + " 小时";
    }

    public int getProgress() {
        if (totalBytes <= 0) return 0;
        return (int) (downloadedBytes * 100 / totalBytes);
    }

    /**
     * 件 8：全行唯一的状态生产者——状态词只许从这里出（状态行专用）。
     * 拼比/排位/清晰度都进行：下载中「下载中 45% · 720P」、等待中
     * 「等待中 · 第 N 位」。getSizeText() 已去状态化，不许再复述状态。
     */
    public String getStatusText() {
        StringBuilder sb = new StringBuilder();
        switch (status) {
            case STATUS_WAITING: {
                sb.append("等待中");
                int pos = DownloadManager.get().queuePosition(id);
                if (pos > 0) sb.append(" · 第 ").append(pos).append(" 位");
                break;
            }
            case STATUS_DOWNLOADING:
                sb.append("下载中 ").append(getProgress()).append("%");
                break;
            case STATUS_PAUSED:
                sb.append("已暂停");
                break;
            case STATUS_COMPLETED:
                sb.append("已完成");
                break;
            case STATUS_ERROR:
                sb.append("出错：").append(error != null ? error : "");
                break;
            case STATUS_MERGING:
                sb.append("合并中");
                break;
            default:
                break;
        }
        if (qualityName != null && !qualityName.isEmpty()
                && (status == STATUS_DOWNLOADING || status == STATUS_WAITING || status == STATUS_PAUSED)) {
            sb.append(" · ").append(qualityName);
        }
        return sb.toString();
    }

    /**
     * 件 8：数据行文案（去状态化）：已下/总量；下载中缀速度与剩余时间
     * （件 4 字段，无速度整段不出现、不留占位符）；已完成只给大小本身；
     * 出错只给已下量。m3u8 分片计数＋字节量并排（总量字节未知不编造）。
     */
    public String getSizeText() {
        if (isM3u8) {
            if (status == STATUS_COMPLETED) {
                long size = getFileSize();
                return size > 0 ? formatSize(size) : "";
            }
            StringBuilder sb = new StringBuilder();
            if (totalBytes > 0) sb.append("分片 ").append(downloadedBytes).append("/").append(totalBytes);
            if (realBytes > 0) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append("已下 ").append(formatSize(realBytes));
            }
            appendSpeedEta(sb);
            return sb.toString();
        }
        switch (status) {
            case STATUS_COMPLETED: {
                long size = getFileSize();
                return size > 0 ? formatSize(size) : "";
            }
            case STATUS_ERROR:
                return downloadedBytes > 0 ? "已下载 " + formatSize(downloadedBytes) : "";
            default: {
                StringBuilder sb = new StringBuilder();
                if (totalBytes > 0) sb.append(formatSize(downloadedBytes)).append(" / ").append(formatSize(totalBytes));
                else if (downloadedBytes > 0) sb.append("已下载 ").append(formatSize(downloadedBytes)).append("（大小未知）");
                appendSpeedEta(sb);
                return sb.toString();
            }
        }
    }

    /** 下载中才缀速度/ETA（暂停/等待不许留冻结旧值，字段已在暂停时清零，这里再按状态把关） */
    private void appendSpeedEta(StringBuilder sb) {
        if (status != STATUS_DOWNLOADING || speedBps <= 0) return;
        if (sb.length() > 0) sb.append(" · ");
        sb.append(formatSize(speedBps)).append("/s");
        String eta = formatEta(etaSeconds);
        if (!eta.isEmpty()) sb.append(" · ").append(eta);
    }

    /** 磁盘实际文件大小（字节），文件不存在返回 0 */
    private long getFileSize() {
        if (filePath == null || filePath.isEmpty()) return 0;
        File f = new File(filePath);
        return f.exists() ? f.length() : 0;
    }

    /** 字节数格式化：B/KB/MB/GB/TB */
    public static String formatSize(long bytes) {
        if (bytes < 0) return "未知";
        if (bytes < 1024) return bytes + "B";
        double v = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(java.util.Locale.US, "%.1f%s", v, units[i]);
    }
}
