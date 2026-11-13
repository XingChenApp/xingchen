package com.fongmi.android.tv.api.hk;

/**
 * 订阅历史账本条目（移植自 8.83 SubscribeHistory）。
 * 8.83 原文：com/example/hikerview/ui/rules/model/SubscribeHistory.smali
 * 字段仅三件（sub/title/version）＋三参构造，与原文一致。
 * 账本独立于订阅记录表落盘（官文件 rules/cache/sub_history.txt），是
 * checkUpdate 删除支与未装条目版本比对的依据。
 */
public class HkSubscribeHistory {

    private String sub;
    private String title;
    private int version;

    public HkSubscribeHistory() {
    }

    public HkSubscribeHistory(String title, int version, String sub) {
        this.title = title;
        this.version = version;
        this.sub = sub;
    }

    public String getSub() {
        return sub;
    }

    public void setSub(String sub) {
        this.sub = sub;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
