package com.example.hikerview.ui.rules.model;

/**
 * 8.83 {@code com.example.hikerview.ui.rules.model.RequireItem} 的同包名同形态补齐
 * （RequireItem.smali 逐字段核：url/proxy/file/accessTime(J)，全 getter/setter，
 * 无参＋四参 ctor）。官方以 fastjson 序列化进 require.json，星辰用 Gson，
 * 字段名一致、格式互通。
 */
public class RequireItem {

    private String url;
    private String proxy;
    private String file;
    private long accessTime;

    public RequireItem() {
    }

    public RequireItem(String url, String proxy, String file, long accessTime) {
        this.url = url;
        this.proxy = proxy;
        this.file = file;
        this.accessTime = accessTime;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public long getAccessTime() {
        return accessTime;
    }

    public void setAccessTime(long accessTime) {
        this.accessTime = accessTime;
    }
}
