package com.fongmi.android.tv.api.hk;

import com.google.gson.annotations.SerializedName;

/**
 * 海阔规则解析出的一条列表/搜索结果。
 * 对应设计文档 §5 HkItem。
 *
 * 字段名由 @SerializedName 钉死：本类不在 proguard 保名名单内，R8 会改字段名，
 * Gson 反射读写若按改后名对键，落盘 JSON 的键随混淆漂移、跨版本互不相认
 * （2026-10-29 1.4.60 真机崩定根，账 hk-list-cast-crash-20261029）。
 */
public class HkItem {

    @SerializedName("title")
    private String title;
    @SerializedName("url")
    private String url;
    @SerializedName("pic")
    private String pic;
    @SerializedName("desc")
    private String desc;
    /** 海阔 col_type（movie_3/pic_1_full/input 等，deleteItemByCls/updateItem 用）。 */
    @SerializedName("colType")
    private String colType = "";
    /**
     * 条目 extra：8.83 JSEngine.convertToArticleList 语义——存原始 JSON 串（String 原样/
     * 非字符串 JSON.toJSONString），数字等保持 JSON 原始类型，不逐值转字符串。
     * （updateItem/deleteItem/findItemsByCls 用）
     */
    @SerializedName("extra")
    private String extra = "";
    /** 8.83 ArticleList.sel：选中态（text_1 等行标题变色用）。 */
    @SerializedName("sel")
    private boolean sel;
    /** 搜索多内容字段（官方 content）。 */
    @SerializedName("content")
    private String content = "";
    /** 明细线路名（官方 line，详情页按 line 分组用；列表里一般为空）。 */
    @SerializedName("line")
    private String line = "";
    /** 8.83 DetailPage.findRule：条目级 find_rule（覆盖规则级）。 */
    @SerializedName("findRule")
    private String findRule = "";
    /** 8.83 DetailPage.group：条目分组。 */
    @SerializedName("group")
    private String group = "";
    /** 8.83 DetailPage.pages：条目分页标记。 */
    @SerializedName("pages")
    private String pages = "";
    /** 8.83 DetailPage.preRule：条目级 preRule。 */
    @SerializedName("preRule")
    private String preRule = "";
    /** 8.83 DetailPage.rule：条目携带的规则串。 */
    @SerializedName("rule")
    private String rule = "";

    public HkItem() {
    }

    public HkItem(String title, String url, String pic, String desc) {
        this.title = title;
        this.url = url;
        this.pic = pic;
        this.desc = desc;
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPic() {
        return pic == null ? "" : pic;
    }

    public void setPic(String pic) {
        this.pic = pic;
    }

    public String getDesc() {
        return desc == null ? "" : desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getColType() {
        return colType == null ? "" : colType;
    }

    public void setColType(String colType) {
        this.colType = colType;
    }

    /** 对应原版 ArticleList 的 type 字段，与 colType 同一字段（转写层统一用 getType/setType）。 */
    public String getType() {
        return getColType();
    }

    public void setType(String type) {
        setColType(type);
    }

    public String getExtra() {
        return extra == null ? "" : extra;
    }

    public void setExtra(String extra) {
        this.extra = extra == null ? "" : extra;
    }

    /**
     * extra 里取单个值（id/cls/pageTitle 等），缺省 ""。
     * 8.83 ArticleList.getBaseExtra 语义：extra 是原始 JSON 串，取值时按 JSON 解析。
     */
    public String getExtra(String key) {
        if (extra == null || extra.isEmpty() || key == null) return "";
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(extra).getAsJsonObject().get(key);
            if (el == null || el.isJsonNull()) return "";
            if (el.isJsonPrimitive()) return el.getAsString();
            return el.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    public boolean isSel() {
        return sel;
    }

    public void setSel(boolean sel) {
        this.sel = sel;
    }

    public String getContent() {
        return content == null ? "" : content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLine() {
        return line == null ? "" : line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    /** 8.83 DetailPage.findRule 对应。 */
    public String getFindRule() {
        return findRule == null ? "" : findRule;
    }

    public void setFindRule(String findRule) {
        this.findRule = findRule;
    }

    /** 8.83 DetailPage.group 对应。 */
    public String getGroup() {
        return group == null ? "" : group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    /** 8.83 DetailPage.pages 对应。 */
    public String getPages() {
        return pages == null ? "" : pages;
    }

    public void setPages(String pages) {
        this.pages = pages;
    }

    /** 8.83 DetailPage.preRule 对应。 */
    public String getPreRule() {
        return preRule == null ? "" : preRule;
    }

    public void setPreRule(String preRule) {
        this.preRule = preRule;
    }

    /** 8.83 DetailPage.rule 对应。 */
    public String getRule() {
        return rule == null ? "" : rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    /**
     * 8.83 DetailPage.getImageUrl() 同款语义：pic_url → pic → picUrl 取首个非空。
     * 本类 pic 字段已是多 key 合并结果（pic_url/img/pic/…），此处直接返回 getPic()，
     * 保留与 8.83 一致的方法名供调用方按原版写法使用。
     */
    public String getImageUrl() {
        return getPic();
    }

    @Override
    public String toString() {
        return "HkItem{title='" + title + "', url='" + url + "', pic='" + pic + "', desc='" + desc + "'}";
    }

    /**
     * 搜索结果点击进详情/播放时转回通用条目（SearchResult 保持 8.83 原版纯净，转换放这边）。
     */
    public static HkItem fromSearchResult(SearchResult sr) {
        HkItem it = new HkItem();
        if (sr == null) return it;
        it.setTitle(sr.getTitle());
        it.setUrl(sr.getUrl());
        it.setPic(sr.getImg());
        it.setDesc(sr.getDesc());
        it.setContent(sr.getContent());
        return it;
    }
}
