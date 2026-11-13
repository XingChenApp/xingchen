package com.example.hikerview.ui.browser.data;

/**
 * 照官 8.83 本体逐字转写（官源 smali_classes2/com/example/hikerview/ui/browser/data/TabHistory.smali，
 * 全类照落）。
 */
public class TabHistory {

    private String url;
    private boolean use;

    public TabHistory() {
    }

    public TabHistory(String url, boolean use) {
        this.url = url;
        this.use = use;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isUse() {
        return this.use;
    }

    public void setUse(boolean use) {
        this.use = use;
    }
}
