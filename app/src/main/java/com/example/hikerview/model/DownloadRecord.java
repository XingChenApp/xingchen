package com.example.hikerview.model;

import com.alibaba.fastjson.annotation.JSONField;

/**
 * 照官 8.83 本体移植（jadx 官包源）：下载记录模型。与 Bookmark
 * 同口径——官 LitePal 基类与 @Column 注解摘除落纯模型，id 改自有
 * 字段；fastjson 注解（lastPlay 不序列化）照官保留。
 * ①团订正：DownloadTask 已逐字转写落位（ui/download），update 两重载
 * 在本批照官补落（见类尾）。136 批续补：以 DownloadTask 为参的构造一法
 * 已照官补落（见构造注）；官调用方 DownloadManager 的「.save()」持久化步
 * 在本纯模型上无对应面（本类无 save 法、LitePal 基类已摘），由下载管理器
 * 侧自理、不在本模型职责内，记名。
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

    /**
     * 官 DownloadRecord(DownloadTask) 构造（DownloadRecord.smali:86，
     * .line 56-75）字段表照官逐项：failedReason 先经无参构造置 ""；
     * taskId/fileName/videoType/fileExtension/url/sourcePageUrl 直拷；
     * sourcePageTitle 非 null 时置 FileUtil.getSimpleName(标题)（官构造支
     * 无「以扩展名结尾才简化」的条件，与 update 法不同、照官各行其是）；
     * size/totalDownloaded 取 task 的 AtomicLong.get()；status/failedReason/
     * currentSpeed/lastClearSpeedTime/film/subtitle 直拷；saveTime 取
     * System.currentTimeMillis()；rootPath 取 DownloadConfig.rootPath 静态值。
     * 官 DownloadManager 两处「查不到记录时 new DownloadRecord(task).save()」
     * 的构造落点即此；save 步见类头注。
     */
    public DownloadRecord(com.example.hikerview.ui.download.DownloadTask task) {
        this();
        this.taskId = task.getTaskId();
        this.fileName = task.getFileName();
        this.videoType = task.getVideoType();
        this.fileExtension = task.getFileExtension();
        this.url = task.getUrl();
        this.sourcePageUrl = task.getSourcePageUrl();
        if (task.getSourcePageTitle() != null) {
            this.sourcePageTitle = com.example.hikerview.utils.FileUtil.getSimpleName(task.getSourcePageTitle());
        }
        this.size = task.getSize().get();
        this.status = task.getStatus();
        this.failedReason = task.getFailedReason();
        this.totalDownloaded = task.getTotalDownloaded().get();
        this.currentSpeed = task.getCurrentSpeed();
        this.lastClearSpeedTime = task.getLastClearSpeedTime();
        this.saveTime = System.currentTimeMillis();
        this.film = task.getFilm();
        this.subtitle = task.getSubtitle();
        this.rootPath = com.example.hikerview.ui.download.DownloadConfig.rootPath;
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

    /** 照官 update(DownloadTask) 逐字：委托 update(task, false)。 */
    public DownloadRecord update(com.example.hikerview.ui.download.DownloadTask task) {
        return update(task, false);
    }

    /** 照官 update(DownloadTask, boolean) 逐字转写（官源 DownloadRecord.smali 同名法）。 */
    public DownloadRecord update(com.example.hikerview.ui.download.DownloadTask task, boolean force) {
        this.taskId = task.getTaskId();
        if (force || com.example.hikerview.utils.StringUtil.isEmpty(this.videoType)) {
            this.videoType = task.getVideoType();
        }
        if (force || com.example.hikerview.utils.StringUtil.isEmpty(this.fileExtension)) {
            this.fileExtension = task.getFileExtension();
        }
        if (com.example.hikerview.utils.StringUtil.isNotEmpty(task.getFileName())) {
            this.fileName = task.getFileName();
        }
        this.url = task.getUrl();
        this.sourcePageUrl = task.getSourcePageUrl();
        if (task.getSourcePageTitle() != null) {
            if (com.example.hikerview.utils.StringUtil.isNotEmpty(task.getFileExtension())
                    && task.getSourcePageTitle().endsWith("." + task.getFileExtension())) {
                this.sourcePageTitle = com.example.hikerview.utils.FileUtil.getSimpleName(task.getSourcePageTitle());
            } else {
                this.sourcePageTitle = task.getSourcePageTitle();
            }
        }
        this.size = task.getSize().get();
        this.status = task.getStatus();
        this.failedReason = task.getFailedReason();
        this.totalDownloaded = task.getTotalDownloaded().get();
        this.currentSpeed = task.getCurrentSpeed();
        this.lastClearSpeedTime = task.getLastClearSpeedTime();
        this.film = task.getFilm();
        this.subtitle = task.getSubtitle();
        return this;
    }
}
