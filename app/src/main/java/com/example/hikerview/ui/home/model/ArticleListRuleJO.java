package com.example.hikerview.ui.home.model;

import com.alibaba.fastjson.annotation.JSONField;

/**
 * 照官 8.83 本体逐字转写（官源 smali_classes2/com/example/hikerview/ui/home/model/ArticleListRuleJO.smali）。
 * 纯数据类：33 字段与访问器、fastjson ordinal 注解逐项照官（ordinal 重复值亦照官原样）。
 * 官类不继承 LitePalSupport（与 ArticleListRule 不同），照官落纯模型。
 */
public class ArticleListRuleJO {

    @JSONField(ordinal = 17)
    private String adBlockUrls;

    @JSONField(ordinal = 6)
    private String area_name;

    @JSONField(ordinal = 7)
    private String area_url;

    @JSONField(ordinal = 2)
    private String author;

    @JSONField(ordinal = 11)
    private String chapter_find;

    @JSONField(ordinal = 4)
    private String class_name;

    @JSONField(ordinal = 5)
    private String class_url;

    @JSONField(ordinal = 3)
    private String col_type;

    @JSONField(ordinal = 20)
    private String detail_col_type;

    @JSONField(ordinal = 21)
    private String detail_find_rule;

    @JSONField(ordinal = 10)
    private String find_rule;

    private String firstHeader;

    @JSONField(ordinal = 16)
    private String group;

    @JSONField(ordinal = 32)
    private String icon;

    private String last_chapter_rule;

    @JSONField(ordinal = 12)
    private String movie_find;

    @JSONField(ordinal = 13)
    private int order;

    @JSONField(ordinal = 31)
    private String pages;

    @JSONField(ordinal = 30)
    private String preRule;

    @JSONField(ordinal = 33)
    private String proxy;

    @JSONField(ordinal = 22)
    private String sdetail_col_type;

    @JSONField(ordinal = 23)
    private String sdetail_find_rule;

    @JSONField(ordinal = 18)
    private String searchFind;

    @JSONField(ordinal = 14)
    private String search_url;

    @JSONField(ordinal = 8)
    private String sort_name;

    @JSONField(ordinal = 9)
    private String sort_url;

    @JSONField(ordinal = 1)
    private String title;

    @JSONField(ordinal = 15)
    private String titleColor;

    @JSONField(ordinal = 4)
    private String type;

    @JSONField(ordinal = 29)
    private String ua;

    @JSONField(ordinal = 2)
    private String url;

    @JSONField(ordinal = 2)
    private int version;

    @JSONField(ordinal = 8)
    private String year_name;

    @JSONField(ordinal = 9)
    private String year_url;

    public String getAdBlockUrls() {
        return this.adBlockUrls;
    }

    public void setAdBlockUrls(String str) {
        this.adBlockUrls = str;
    }

    public String getArea_name() {
        return this.area_name;
    }

    public void setArea_name(String str) {
        this.area_name = str;
    }

    public String getArea_url() {
        return this.area_url;
    }

    public void setArea_url(String str) {
        this.area_url = str;
    }

    public String getAuthor() {
        return this.author;
    }

    public void setAuthor(String str) {
        this.author = str;
    }

    public String getChapter_find() {
        return this.chapter_find;
    }

    public void setChapter_find(String str) {
        this.chapter_find = str;
    }

    public String getClass_name() {
        return this.class_name;
    }

    public void setClass_name(String str) {
        this.class_name = str;
    }

    public String getClass_url() {
        return this.class_url;
    }

    public void setClass_url(String str) {
        this.class_url = str;
    }

    public String getCol_type() {
        return this.col_type;
    }

    public void setCol_type(String str) {
        this.col_type = str;
    }

    public String getDetail_col_type() {
        return this.detail_col_type;
    }

    public void setDetail_col_type(String str) {
        this.detail_col_type = str;
    }

    public String getDetail_find_rule() {
        return this.detail_find_rule;
    }

    public void setDetail_find_rule(String str) {
        this.detail_find_rule = str;
    }

    public String getFind_rule() {
        return this.find_rule;
    }

    public void setFind_rule(String str) {
        this.find_rule = str;
    }

    public String getFirstHeader() {
        return this.firstHeader;
    }

    public void setFirstHeader(String str) {
        this.firstHeader = str;
    }

    public String getGroup() {
        return this.group;
    }

    public void setGroup(String str) {
        this.group = str;
    }

    public String getIcon() {
        return this.icon;
    }

    public void setIcon(String str) {
        this.icon = str;
    }

    public String getLast_chapter_rule() {
        return this.last_chapter_rule;
    }

    public void setLast_chapter_rule(String str) {
        this.last_chapter_rule = str;
    }

    public String getMovie_find() {
        return this.movie_find;
    }

    public void setMovie_find(String str) {
        this.movie_find = str;
    }

    public int getOrder() {
        return this.order;
    }

    public void setOrder(int i) {
        this.order = i;
    }

    public String getPages() {
        return this.pages;
    }

    public void setPages(String str) {
        this.pages = str;
    }

    public String getPreRule() {
        return this.preRule;
    }

    public void setPreRule(String str) {
        this.preRule = str;
    }

    public String getProxy() {
        return this.proxy;
    }

    public void setProxy(String str) {
        this.proxy = str;
    }

    public String getSdetail_col_type() {
        return this.sdetail_col_type;
    }

    public void setSdetail_col_type(String str) {
        this.sdetail_col_type = str;
    }

    public String getSdetail_find_rule() {
        return this.sdetail_find_rule;
    }

    public void setSdetail_find_rule(String str) {
        this.sdetail_find_rule = str;
    }

    public String getSearchFind() {
        return this.searchFind;
    }

    public void setSearchFind(String str) {
        this.searchFind = str;
    }

    public String getSearch_url() {
        return this.search_url;
    }

    public void setSearch_url(String str) {
        this.search_url = str;
    }

    public String getSort_name() {
        return this.sort_name;
    }

    public void setSort_name(String str) {
        this.sort_name = str;
    }

    public String getSort_url() {
        return this.sort_url;
    }

    public void setSort_url(String str) {
        this.sort_url = str;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String str) {
        this.title = str;
    }

    public String getTitleColor() {
        return this.titleColor;
    }

    public void setTitleColor(String str) {
        this.titleColor = str;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String str) {
        this.type = str;
    }

    public String getUa() {
        return this.ua;
    }

    public void setUa(String str) {
        this.ua = str;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String str) {
        this.url = str;
    }

    public int getVersion() {
        return this.version;
    }

    public void setVersion(int i) {
        this.version = i;
    }

    public String getYear_name() {
        return this.year_name;
    }

    public void setYear_name(String str) {
        this.year_name = str;
    }

    public String getYear_url() {
        return this.year_url;
    }

    public void setYear_url(String str) {
        this.year_url = str;
    }

}