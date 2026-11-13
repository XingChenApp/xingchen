package com.example.hikerview.ui.rules.model;

/**
 * 8.83 {@code com.example.hikerview.ui.rules.model.RequireDescription} 的同包名
 * 同形态补齐（RequireDescription.smali 逐字段核：url/version(I)，全
 * getter/setter，无参＋两参 ctor）。模块描述文件的 JSON 载体
 * （RequireUtils.getRequireVersion/updateDescription 读写）。
 */
public class RequireDescription {

    private String url;
    private int version;

    public RequireDescription() {
    }

    public RequireDescription(String url, int version) {
        this.url = url;
        this.version = version;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
