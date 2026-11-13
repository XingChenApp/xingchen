package com.example.hikerview.ui.setting.model;

/**
 * 官 {@code com.example.hikerview.ui.setting.model.VersionInfo} 照官转写
 * （官源 smali/com/example/hikerview/ui/setting/model/VersionInfo.smali）：
 * Kotlin data class，三字段 url/c/v，含无参＋三参构造与全套 getter/setter；
 * MoreSettingOfficer.getLatestVersion 以 fastjson parseObject 落本类。
 * equals/hashCode/toString 按 Kotlin data class 语义补齐。
 */
public final class VersionInfo {

    private String url;
    private Integer c;
    private String v;

    public VersionInfo() {
    }

    public VersionInfo(String url, Integer c, String v) {
        this.url = url;
        this.c = c;
        this.v = v;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getC() {
        return c;
    }

    public void setC(Integer c) {
        this.c = c;
    }

    public String getV() {
        return v;
    }

    public void setV(String v) {
        this.v = v;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VersionInfo)) {
            return false;
        }
        VersionInfo other = (VersionInfo) o;
        return java.util.Objects.equals(url, other.url)
                && java.util.Objects.equals(c, other.c)
                && java.util.Objects.equals(v, other.v);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(url, c, v);
    }

    @Override
    public String toString() {
        return "VersionInfo(url=" + url + ", c=" + c + ", v=" + v + ")";
    }
}
