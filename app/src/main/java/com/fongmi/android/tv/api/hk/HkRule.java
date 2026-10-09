package com.fongmi.android.tv.api.hk;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 海阔 rule.json 的 Java 数据模型（Gson 反序列化）。
 * 字段对照设计文档 §2.1；第一期只支持 type=video。
 */
public class HkRule {

    private static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String PC_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @SerializedName("title")
    private String title;
    @SerializedName("author")
    private String author;
    @SerializedName("version")
    private int version;
    @SerializedName("type")
    private String type;
    @SerializedName("group")
    private String group;
    @SerializedName("ua")
    private String ua;
    @SerializedName("url")
    private String url;
    @SerializedName("class_name")
    private String className;
    @SerializedName("class_url")
    private String classUrl;
    @SerializedName("area_name")
    private String areaName;
    @SerializedName("area_url")
    private String areaUrl;
    @SerializedName("year_name")
    private String yearName;
    @SerializedName("year_url")
    private String yearUrl;
    @SerializedName("sort_name")
    private String sortName;
    @SerializedName("sort_url")
    private String sortUrl;
    @SerializedName("col_type")
    private String colType;
    @SerializedName("find_rule")
    private String findRule;
    @SerializedName("search_url")
    private String searchUrl;
    @SerializedName("searchFind")
    private String searchFind;
    @SerializedName("detail_find_rule")
    private String detailFindRule;
    @SerializedName("sdetail_find_rule")
    private String sdetailFindRule;
    @SerializedName("detail_col_type")
    private String detailColType;
    @SerializedName("sdetail_col_type")
    private String sdetailColType;
    @SerializedName("preRule")
    private String preRule;
    @SerializedName("pages")
    private String pages;
    @SerializedName("last_chapter_rule")
    private String lastChapterRule;
    @SerializedName("icon")
    private String icon;

    /** 运行时启用开关，不序列化进 rule.json，由 HkRuleManager 持久化。 */
    private transient boolean enabled = true;

    /** 导入校验：title/url/find_rule 非空；第一期 type 必须为 video。 */
    public void validate() {
        if (isEmpty(title)) throw new IllegalArgumentException("规则缺少 title");
        if (isEmpty(url)) throw new IllegalArgumentException("规则缺少 url");
        if (isEmpty(findRule)) throw new IllegalArgumentException("规则缺少 find_rule");
        if (!isEmpty(type) && !"video".equalsIgnoreCase(type.trim())) {
            throw new IllegalArgumentException("第一期只支持 video 类型，当前 type=" + type);
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 解析 mobile/pc/自定义 UA。 */
    public String resolvedUa() {
        if ("mobile".equalsIgnoreCase(ua)) return MOBILE_UA;
        if ("pc".equalsIgnoreCase(ua)) return PC_UA;
        return isEmpty(ua) ? MOBILE_UA : ua.trim();
    }

    public boolean isJsFindRule() {
        return findRule != null && findRule.trim().startsWith("js:");
    }

    public boolean isJsSearchRule() {
        return searchFind != null && searchFind.trim().startsWith("js:");
    }

    public boolean hasSearch() {
        return !isEmpty(searchUrl) && !isEmpty(searchFind);
    }

    /** class_name/class_url 按 & 配对，返回 [name, url] 数组列表。 */
    public List<String[]> getClassPairs() {
        return pairList(className, classUrl);
    }

    public List<String[]> getAreaPairs() {
        return pairList(areaName, areaUrl);
    }

    public List<String[]> getYearPairs() {
        return pairList(yearName, yearUrl);
    }

    public List<String[]> getSortPairs() {
        return pairList(sortName, sortUrl);
    }

    private static List<String[]> pairList(String names, String urls) {
        List<String[]> list = new ArrayList<>();
        if (isEmpty(names) || isEmpty(urls)) return list;
        String[] ns = names.split("&", -1);
        String[] us = urls.split("&", -1);
        int n = Math.min(ns.length, us.length);
        for (int i = 0; i < n; i++) {
            list.add(new String[]{ns[i].trim(), us[i].trim()});
        }
        return list;
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author == null ? "" : author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getType() {
        return type == null ? "" : type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getGroup() {
        return group == null ? "" : group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getUa() {
        return ua == null ? "" : ua;
    }

    public void setUa(String ua) {
        this.ua = ua;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getClassName() {
        return className == null ? "" : className;
    }

    public String getClassUrl() {
        return classUrl == null ? "" : classUrl;
    }

    public String getFindRule() {
        return findRule == null ? "" : findRule;
    }

    public void setFindRule(String findRule) {
        this.findRule = findRule;
    }

    public String getSearchUrl() {
        return searchUrl == null ? "" : searchUrl;
    }

    public String getSearchFind() {
        return searchFind == null ? "" : searchFind;
    }

    public String getDetailFindRule() {
        return detailFindRule == null ? "" : detailFindRule;
    }

    public String getSdetailFindRule() {
        return sdetailFindRule == null ? "" : sdetailFindRule;
    }

    public String getPreRule() {
        return preRule == null ? "" : preRule;
    }

    public String getPages() {
        return pages == null ? "" : pages;
    }

    public String getIcon() {
        return icon == null ? "" : icon;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "HkRule{title='" + title + "', author='" + author + "', version=" + version + ", type='" + type + "'}";
    }
}
