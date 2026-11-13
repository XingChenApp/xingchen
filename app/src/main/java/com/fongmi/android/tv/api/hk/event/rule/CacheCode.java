package com.fongmi.android.tv.api.hk.event.rule;

/**
 * 8.83 com.example.hikerview.event.rule.CacheCode 的等价实现（EventBus 事件类，纯数据载体）。
 * 规则 JS 调 cacheCode(...) 时由引擎 cacheCode0 发布（JSEngine.smali cacheCode0：
 * 非空才 post）；列表页 HkArticleFragment.updateCacheCode 订阅后存为本页 cacheCode，
 * 随 #autoCache# 的列表结果一并落盘，下次开页先执行这段代码再渲染缓存。
 */
public class CacheCode {

    private String code;

    public CacheCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
