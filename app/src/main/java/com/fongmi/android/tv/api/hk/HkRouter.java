package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;

import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 海阔规则引擎路由层（M3，设计文档 §4.5）。
 *
 * <p>纯数据 API，供上层调用：</p>
 * <ul>
 *   <li>{@link #home} / {@link #search} → HkPageActivity（列表/搜索）</li>
 *   <li>{@link #detail} → VideoActivity 海阔分支（详情/线路/选集）</li>
 *   <li>{@link #play} → VideoActivity 播放前解析（M3 基础分流，完整链 M4）</li>
 * </ul>
 */
public class HkRouter {

    private static final String TAG = "HkRouter";

    private final HkRule rule;
    private final HkEngine engine;

    public HkRouter(HkRule rule) throws Exception {
        this.rule = rule;
        this.engine = new HkEngine(rule);
    }

    /** 按规则名打开路由（规则不存在抛异常）。 */
    public static HkRouter open(String title) throws Exception {
        HkRule r = HkRuleManager.get().getRule(title);
        if (r == null) throw new IllegalArgumentException("hk rule not found: " + title);
        return new HkRouter(r);
    }

    public HkRule getRule() {
        return rule;
    }

    /** 首页/分类列表。海阔分类即 url 替换词，直接复用 home。 */
    public List<HkItem> home(int page, String cls, String area, String year, String sort) {
        return engine.home(page, cls, area, year, sort);
    }

    /** 单页内置搜索。 */
    public List<HkItem> search(String keyword, int page) {
        return engine.search(keyword, page);
    }

    /**
     * 详情：请求条目页 → detail_find_rule（搜索来源且 sdetail 非 * 时用 sdetail_find_rule）
     * → HkDetail。item 用于标题/封面/简介回退。
     */
    public HkDetail detail(String itemUrl, HkItem item, boolean fromSearch) {
        HkDetail detail = new HkDetail();
        if (item != null) {
            detail.setTitle(item.getTitle());
            detail.setPic(item.getPic());
            detail.setContent(item.getDesc());
        }
        try {
            rule.validate();
            String ruleText = effectiveDetailRule(fromSearch);
            if (ruleText == null || ruleText.trim().isEmpty()) return detail;
            String url = cleanDetailUrl(itemUrl);
            List<HkDetailItem> items;
            if (HkSelector.isJsRule(ruleText)) {
                items = fromJsDetail(ruleText, url);
            } else {
                String html = HkHttp.get(url, rule.resolvedUa());
                items = engine.getSelector().parseDetail(html, ruleText, url);
            }
            buildDetail(detail, items);
        } catch (Throwable e) {
            Logger.t(TAG).d("detail failed: %s", e.getMessage());
        }
        return detail;
    }

    /**
     * 播放地址基础分流（M3）：剥页面标识与 header 需求，返回待播放地址。
     * 完整分流链（lazyRule/@rule/二次解析）是 M4 内容。
     */
    public String play(String episodeUrl) {
        if (episodeUrl == null) return "";
        String u = episodeUrl.trim();
        int hash = u.indexOf('#');
        if (hash >= 0) u = u.substring(0, hash).trim();
        int semi = u.indexOf(';');
        if (semi >= 0) {
            String tail = u.substring(semi + 1).trim();
            if (tail.startsWith("{") || tail.equalsIgnoreCase("POST") || tail.equalsIgnoreCase("GET")) {
                u = u.substring(0, semi).trim();
            }
        }
        if (u.startsWith("video://")) u = u.substring(8);
        return u;
    }

    public void destroy() {
        engine.destroy();
    }

    // ================= 内部 =================

    private String effectiveDetailRule(boolean fromSearch) {
        String r = null;
        if (fromSearch) {
            r = rule.getSdetailFindRule();
            if (r == null || r.trim().isEmpty() || "*".equals(r.trim())) r = null;
        }
        if (r == null) r = rule.getDetailFindRule();
        if (r == null || r.trim().isEmpty() || "*".equals(r.trim())) return null;
        return r;
    }

    private String cleanDetailUrl(String url) {
        if (url == null) return "";
        String u = url.trim();
        int hash = u.indexOf('#');
        if (hash >= 0) u = u.substring(0, hash).trim();
        return u;
    }

    private List<HkDetailItem> fromJsDetail(String jsCode, String url) throws Exception {
        List<Map<String, String>> raw = engine.getJsRuntime().parseDetailRaw(jsCode, url);
        List<HkDetailItem> items = new ArrayList<>();
        for (Map<String, String> m : raw) {
            HkDetailItem it = new HkDetailItem();
            it.setTitle(str(m, "title"));
            it.setUrl(str(m, "url"));
            String pic = str(m, "pic_url");
            if (pic.isEmpty()) pic = str(m, "pic");
            if (pic.isEmpty()) pic = str(m, "img");
            it.setPic(pic);
            it.setDesc(str(m, "desc"));
            it.setLine(str(m, "line"));
            String col = str(m, "col_type");
            if (col.isEmpty()) col = str(m, "colType");
            it.setColType(col);
            items.add(it);
        }
        return items;
    }

    private static String str(Map<String, String> m, String key) {
        String v = m.get(key);
        return v == null ? "" : v;
    }

    /**
     * 组装 HkDetail：
     * - col_type 含 long_text → 简介；
     * - 纯展示项（无可播链接）→ 标题/封面回退；
     * - 其余按 line 分组为线路/选集。
     */
    private void buildDetail(HkDetail detail, List<HkDetailItem> items) {
        if (items == null) return;
        for (HkDetailItem it : items) {
            String col = it.getColType().toLowerCase();
            if (col.contains("long_text")) {
                String c = (it.getTitle() + it.getDesc()).trim();
                if (!c.isEmpty()) detail.setContent(c);
                continue;
            }
            if (it.isDisplayOnly()) {
                if (!it.getPic().isEmpty()) detail.setPic(it.getPic());
                if (!it.getTitle().isEmpty() && detail.getTitle().isEmpty()) detail.setTitle(it.getTitle());
                if (!it.getDesc().isEmpty() && detail.getContent().isEmpty()) detail.setContent(it.getDesc());
                continue;
            }
            detail.getOrCreateLine(it.getLine()).addEpisode(it.getTitle(), it.getUrl());
        }
    }
}
