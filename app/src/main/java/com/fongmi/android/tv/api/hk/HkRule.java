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

    /**
     * 页面参数（hiker://page/ 子页面用）：点击条目的 extra 序列化 JSON，
     * 页面 rule 里通过 MY_PARAMS 取用。不序列化，运行时设置。
     */
    private transient String pageParams = "";

    /** 导入校验：title/find_rule 非空；js: 规则允许 url 为空（自包含）。type 不限制（video/music/cartoon 等均可导入）。 */
    public void validate() {
        if (isEmpty(title)) throw new IllegalArgumentException("规则缺少 title");
        if (isEmpty(url) && !HkSelector.isJsRule(findRule)) throw new IllegalArgumentException("规则缺少 url");
        if (isEmpty(findRule)) throw new IllegalArgumentException("规则缺少 find_rule");
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

    /** 规则级 col_type（官方 dealRule：@rule= 条目无 6 段样式时的默认；空则回退 MOVIE_3）。 */
    public String getColType() {
        return colType == null ? "" : colType;
    }

    public void setColType(String colType) {
        this.colType = colType;
    }

    /** 清空分类/地区/年份/类型导航（子列表派生用：官方 dealRule 的新规则不带这些字段）。 */
    public void clearNav() {
        className = "";
        classUrl = "";
        areaName = "";
        areaUrl = "";
        yearName = "";
        yearUrl = "";
        sortName = "";
        sortUrl = "";
    }

    /**
     * 派生子列表规则（官方 ArticleListFragment.dealRule 语义）：
     * 复制当前规则全部字段，仅覆盖 url / find_rule / col_type；
     * 其余（ua/group/preRule/title/last_chapter_rule/pages 等）继承父规则。
     */
    public HkRule deriveSubRule(String url, String findRule, String colType) {
        HkRule r = new HkRule();
        r.title = this.title;
        r.author = this.author;
        r.version = this.version;
        r.type = this.type;
        r.group = this.group;
        r.ua = this.ua;
        r.url = url;
        r.className = this.className;
        r.classUrl = this.classUrl;
        r.areaName = this.areaName;
        r.areaUrl = this.areaUrl;
        r.yearName = this.yearName;
        r.yearUrl = this.yearUrl;
        r.sortName = this.sortName;
        r.sortUrl = this.sortUrl;
        r.colType = colType;
        r.findRule = findRule;
        r.searchUrl = this.searchUrl;
        r.searchFind = this.searchFind;
        r.detailFindRule = this.detailFindRule;
        r.sdetailFindRule = this.sdetailFindRule;
        r.detailColType = this.detailColType;
        r.sdetailColType = this.sdetailColType;
        r.preRule = this.preRule;
        r.pages = this.pages;
        r.lastChapterRule = this.lastChapterRule;
        r.icon = this.icon;
        r.enabled = this.enabled;
        r.pageParams = this.pageParams;
        return r;
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

    public String getDetailColType() {
        return detailColType == null ? "" : detailColType;
    }

    public String getSdetailColType() {
        return sdetailColType == null ? "" : sdetailColType;
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

    public String getPageParams() {
        return pageParams == null ? "" : pageParams;
    }

    public void setPageParams(String pageParams) {
        this.pageParams = pageParams == null ? "" : pageParams;
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
