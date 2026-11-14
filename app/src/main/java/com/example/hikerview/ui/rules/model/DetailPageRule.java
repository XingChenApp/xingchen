package com.example.hikerview.ui.rules.model;

/**
 * 官 {@code com.example.hikerview.ui.rules.model.DetailPageRule} 本体转写
 * （DetailPageRule.smali 字段表逐字：title/data/picUrl 三 String 字段、
 * 无参＋三参构造（序 title,data,picUrl）、getter/setter 全套）。
 * 二级页面详情口令（￥page_detail￥）的载体：data 为一条规则 JSON，
 * 由 AutoImportHelper 经 HkListActivity.putTempRule 打开。
 */
public class DetailPageRule {

    private String data;
    private String picUrl;
    private String title;

    public DetailPageRule() {
    }

    public DetailPageRule(String title, String data, String picUrl) {
        this.title = title;
        this.data = data;
        this.picUrl = picUrl;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getPicUrl() {
        return picUrl;
    }

    public void setPicUrl(String picUrl) {
        this.picUrl = picUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
