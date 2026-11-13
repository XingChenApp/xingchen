package com.fongmi.android.tv.api.hk;

/**
 * 搜索发动机模型，一比一对照 8.83 {@code ui/browser/model/SearchEngine}（20 字段）。
 * toRule() 照原版 toMovieRule() 的 11 项映射（search_url→searchUrl、findRule→searchFind、
 * baseUrl 由 search_url 计算）；fromRule() 是规则库生成发动机的一路（原版 getEngines 从规则库组装）。
 */
public class HkSearchEngine {

    private String detail_col_type;
    private String detail_find_rule;
    private String displayName;
    private String findRule;
    private String group;
    private String icon;
    private String last_chapter_rule;
    private int order;
    private String pages;
    private String params;
    private String preRule;
    private String proxy;
    private String search_url;
    private int status;
    private String title;
    private String titleColor;
    private String type;
    private String ua;
    private String url;
    private boolean use;

    public String getDetail_col_type() {
        return detail_col_type;
    }

    public void setDetail_col_type(String detail_col_type) {
        this.detail_col_type = detail_col_type;
    }

    public String getDetail_find_rule() {
        return detail_find_rule;
    }

    public void setDetail_find_rule(String detail_find_rule) {
        this.detail_find_rule = detail_find_rule;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 原版 getDisplayTitle（SearchEngine.smali:419）仅判 null——但星辰的 HkRule.getDisplayName()
     * 把 null 归一成 ""，fromRule 照单全收，未设 displayName 的规则（绝大多数）到这里 displayName
     * 恒为空串、永不为 null，源名在底部状态条/结果来源标/失败汇总三处全灭（2026-10-24 真机实证）。
     * 故此处口径修正为空串同样回落 title；设了 displayName 的规则行为与原版一致。
     */
    public String getDisplayTitle() {
        return displayName != null && !displayName.isEmpty() ? displayName : title;
    }

    public String getFindRule() {
        return findRule;
    }

    public void setFindRule(String findRule) {
        this.findRule = findRule;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getLast_chapter_rule() {
        return last_chapter_rule;
    }

    public void setLast_chapter_rule(String last_chapter_rule) {
        this.last_chapter_rule = last_chapter_rule;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getPages() {
        return pages;
    }

    public void setPages(String pages) {
        this.pages = pages;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public String getPreRule() {
        return preRule;
    }

    public void setPreRule(String preRule) {
        this.preRule = preRule;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public String getSearch_url() {
        return search_url;
    }

    public void setSearch_url(String search_url) {
        this.search_url = search_url;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitleColor() {
        return titleColor;
    }

    public void setTitleColor(String titleColor) {
        this.titleColor = titleColor;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUa() {
        return ua;
    }

    public void setUa(String ua) {
        this.ua = ua;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isUse() {
        return use;
    }

    public void setUse(boolean use) {
        this.use = use;
    }

    /** 照原版 toMovieRule()：11 项映射进 HkRule（baseUrl 按 search_url 计算）。 */
    public HkRule toRule() {
        HkRule rule = new HkRule();
        rule.setTitle(title);
        rule.setSearchUrl(search_url);
        rule.setSearchFind(findRule);
        rule.setBaseUrl(HkStringUtilCompat.getBaseUrl(search_url));
        rule.setParams(params);
        rule.setPages(pages);
        rule.setDisplayName(displayName);
        rule.setUrl(url);
        rule.setProxy(proxy);
        rule.setPreRule(preRule);
        rule.setUa(ua);
        return rule;
    }

    /** 规则库 → 发动机（原版 getEngines 按规则组装 SearchEngine 的字段对应）。 */
    public static HkSearchEngine fromRule(HkRule rule) {
        HkSearchEngine engine = new HkSearchEngine();
        engine.setTitle(rule.getTitle());
        engine.setUrl(rule.getUrl());
        // 环 15 残项（官 SearchEngine.fromArticleRule 源行 113-120）：search_url
        // 非空、以 "/" 开头且 url 非空时 setSearch_url(getHome(url)+search_url)，
        // 余形直拷 search_url。getHome 用 HkParserKit.getHome——与 ui/hk
        // HkStringUtil.getHome 同一份官 StringUtil.getHome 转写（主源集够不到
        // mobile 源集，转写收口在 HkParserKit，核对见该类注释）。
        String searchUrl = rule.getSearchUrl();
        String ruleUrl = rule.getUrl();
        if (searchUrl != null && !searchUrl.isEmpty() && searchUrl.startsWith("/")
                && ruleUrl != null && !ruleUrl.isEmpty()) {
            engine.setSearch_url(HkParserKit.getHome(ruleUrl) + searchUrl);
        } else {
            engine.setSearch_url(searchUrl);
        }
        engine.setFindRule(rule.getSearchFind());
        // 环15 sdetail 折叠（官 SearchEngine.fromArticleRule 源行125-130，
        // 全包唯一折叠点，只在搜索语境构造时发生）：sdetail_find_rule=="*"
        // 时 engine 的详情段取 detail 段，否则取 sdetail 段——find_rule 与
        // col_type 成对换。普通详情链（ArticleListFragment.clickItem 形）
        // 从不读 sdetail、不经此处。
        if ("*".equals(rule.getSdetailFindRule())) {
            engine.setDetail_find_rule(rule.getDetailFindRule());
            engine.setDetail_col_type(rule.getDetailColType());
        } else {
            engine.setDetail_find_rule(rule.getSdetailFindRule());
            engine.setDetail_col_type(rule.getSdetailColType());
        }
        engine.setPreRule(rule.getPreRule());
        engine.setUa(rule.getUa());
        engine.setGroup(rule.getGroup());
        engine.setPages(rule.getPages());
        engine.setParams(rule.getParams());
        engine.setProxy(rule.getProxy());
        engine.setIcon(rule.getIcon());
        engine.setLast_chapter_rule(rule.getLastChapterRule());
        engine.setTitleColor(rule.getTitleColor());
        engine.setDisplayName(rule.getDisplayName());
        engine.setType(rule.getType());
        return engine;
    }

    /**
     * 委托搜索父规则回填（官 SearchActivity.initData delegateOnlySearch 支，
     * 源行685-698）：preRule 空才补父 preRule；pages/ua/proxy 无条件覆父值；
     * detail_find_rule 空才按父规则的 sdetail 折叠对补填（父 sdetail=="*" 用
     * 父 detail 段、否则用父 sdetail 段，find/col_type 成对）。
     */
    public static void backfillFromParent(HkSearchEngine engine, HkRule parent) {
        if (engine == null || parent == null) return;
        if (engine.getPreRule() == null || engine.getPreRule().isEmpty()) {
            engine.setPreRule(parent.getPreRule());
        }
        engine.setPages(parent.getPages());
        engine.setUa(parent.getUa());
        engine.setProxy(parent.getProxy());
        String detail = engine.getDetail_find_rule();
        if (detail == null || detail.isEmpty()) {
            if ("*".equals(parent.getSdetailFindRule())) {
                engine.setDetail_find_rule(parent.getDetailFindRule());
                engine.setDetail_col_type(parent.getDetailColType());
            } else {
                engine.setDetail_find_rule(parent.getSdetailFindRule());
                engine.setDetail_col_type(parent.getSdetailColType());
            }
        }
    }

    /** HkStringUtil.getBaseUrl 的 api 包内转接（避免 ui.hk 反向依赖成环，与 HkStringUtil 同实现）。 */
    static class HkStringUtilCompat {
        static String getBaseUrl(String url) {
            if (url == null) return null;
            String cleaned = url.replace("http://", "").replace("https://", "");
            String[] parts = cleaned.split("/");
            String scheme = url.startsWith("https") ? "https://" : "http://";
            return parts.length == 0 ? url : scheme + parts[0];
        }
    }
}
