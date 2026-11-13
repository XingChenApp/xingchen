package com.fongmi.android.tv.api.hk;

import java.util.Date;

/**
 * 首页订阅记录（移植自 8.83 SubscribeRecord）。
 * 8.83 原文：com/example/hikerview/ui/rules/model/SubscribeRecord.smali（238 行）
 * 字段、getter/setter 与原文一致。
 */
public class HkSubscribeRecord {

    private Date createDate;
    private int errorCount;
    private int lastUpdateCount;
    private boolean lastUpdateSuccess;
    private Date modifyDate;
    private boolean onlyUpdate;
    private int rulesCount;
    private int successCount;
    private String title;
    private String url;
    private boolean use;

    public HkSubscribeRecord() {
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public int getLastUpdateCount() {
        return lastUpdateCount;
    }

    public void setLastUpdateCount(int lastUpdateCount) {
        this.lastUpdateCount = lastUpdateCount;
    }

    public boolean isLastUpdateSuccess() {
        return lastUpdateSuccess;
    }

    public void setLastUpdateSuccess(boolean lastUpdateSuccess) {
        this.lastUpdateSuccess = lastUpdateSuccess;
    }

    public Date getModifyDate() {
        return modifyDate;
    }

    public void setModifyDate(Date modifyDate) {
        this.modifyDate = modifyDate;
    }

    public boolean isOnlyUpdate() {
        return onlyUpdate;
    }

    public void setOnlyUpdate(boolean onlyUpdate) {
        this.onlyUpdate = onlyUpdate;
    }

    public int getRulesCount() {
        return rulesCount;
    }

    public void setRulesCount(int rulesCount) {
        this.rulesCount = rulesCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isUse() {
        return use;
    }

    public void setUse(boolean use) {
        this.use = use;
    }
}
