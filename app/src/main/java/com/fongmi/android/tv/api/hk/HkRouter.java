package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;

import com.orhanobut.logger.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

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
 *   <li>{@link #play} → VideoActivity 播放前解析（M4 完整分流链）</li>
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
     * 播放地址完整分流链（M4，设计文档 §6）。
     *
     * <p>顺序：</p>
     * <ol>
     *   <li>剥 {@code #isVideo=true#} / {@code #noLoading#} 等页面标识；</li>
     *   <li>拆 {@code ;} URL 增强（{@code url;method;encoding;{H@V&&H2@V2}}），header 进结果；</li>
     *   <li>{@code video://} 剥协议；</li>
     *   <li>{@code @lazyRule=}：{@code .js:} 走 JS 独立作用域求值，否则按选择器对剧集页提地址；</li>
     *   <li>{@code @rule=js:}：按 JS 求值；</li>
     *   <li>{@code js:} 整段：按 JS 求值；</li>
     *   <li>{@code {urls:[],names:[],headers:[],subtitle}} JSON：取首地址 + headers；</li>
     *   <li>直链或兜底：清理后的原样地址（播放器/嗅探继续处理）。</li>
     * </ol>
     */
    public HkPlay play(String episodeUrl) {
        HkPlay out = new HkPlay();
        if (episodeUrl == null) return out;
        String u = episodeUrl.trim();
        // 1. # 页面标识
        int hash = u.indexOf('#');
        if (hash >= 0) u = u.substring(0, hash).trim();
        // 2. ; URL 增强
        u = splitEnhancement(u, out);
        // 3. video://
        if (u.startsWith("video://")) u = u.substring(8).trim();
        // 4. @lazyRule=
        int lr = u.indexOf("@lazyRule=");
        if (lr >= 0) {
            String pageUrl = u.substring(0, lr).trim();
            String ruleText = decodeConflict(u.substring(lr + 10).trim());
            resolveLazyRule(pageUrl, ruleText, out);
            if (!out.isEmpty()) return out;
            u = pageUrl;
        }
        // 5. @rule=js:
        int ar = u.indexOf("@rule=");
        if (ar >= 0) {
            String pageUrl = u.substring(0, ar).trim();
            String ruleText = u.substring(ar + 6).trim();
            if (ruleText.startsWith("js:")) {
                String real = evalJsPlay(ruleText.substring(3), pageUrl);
                if (!real.isEmpty()) {
                    out.setUrl(real);
                    return out;
                }
            }
            u = pageUrl;
        }
        // 6. js: 整段
        if (u.startsWith("js:")) {
            String real = evalJsPlay(u.substring(3), u);
            if (!real.isEmpty()) {
                out.setUrl(real);
                return out;
            }
        }
        // 7. {urls:[]} JSON
        if (u.startsWith("{") && u.contains("\"urls\"")) {
            if (parsePlayJson(u, out)) return out;
        }
        // 8. 兜底
        out.setUrl(u);
        return out;
    }

    /**
     * 拆 {@code ;} URL 增强：{@code url;method;encoding;{K@V&&K2@V2}}。
     * header 块进结果，method/encoding 丢弃，只保留纯 url。
     */
    private String splitEnhancement(String u, HkPlay out) {
        int brace = u.indexOf('{');
        int braceEnd = u.lastIndexOf('}');
        if (brace > 0 && braceEnd > brace) {
            String inner = u.substring(brace + 1, braceEnd);
            for (String kv : inner.split("&&")) {
                int at = kv.indexOf('@');
                if (at > 0) out.putHeader(kv.substring(0, at).trim(), kv.substring(at + 1).trim());
            }
            u = (u.substring(0, brace) + u.substring(braceEnd + 1)).trim();
        }
        int semi = u.indexOf(';');
        if (semi >= 0) u = u.substring(0, semi).trim();
        return u;
    }

    /**
     * 解析 {@code @lazyRule=}：{@code .js:}/{@code js:} 开头走 JS 独立作用域求值，
     * 否则把规则当选择器对剧集页提单个地址（{@code .js:} 值后缀由选择器内部处理）。
     */
    private void resolveLazyRule(String pageUrl, String ruleText, HkPlay out) {
        try {
            String r = ruleText.trim();
            boolean js = false;
            if (r.startsWith(".js:")) {
                r = r.substring(4);
                js = true;
            } else if (r.startsWith("js:")) {
                r = r.substring(3);
                js = true;
            }
            String v;
            if (js) {
                v = engine.getJsRuntime().evalLazy(r, pageUrl);
            } else {
                String html = HkHttp.get(pageUrl, rule.resolvedUa());
                v = engine.getSelector().evalField(html, r, pageUrl);
            }
            if (!TextUtils.isEmpty(v)) out.setUrl(v.trim());
        } catch (Throwable e) {
            Logger.t(TAG).d("lazyRule failed: %s", e.getMessage());
        }
    }

    private String evalJsPlay(String code, String pageUrl) {
        try {
            return engine.getJsRuntime().evalLazy(code, pageUrl);
        } catch (Throwable e) {
            Logger.t(TAG).d("js play failed: %s", e.getMessage());
            return "";
        }
    }

    /**
     * 解析多线路/字幕 JSON：{@code {urls:[], names:[], headers:[], subtitle:'...'}}。
     * headers 元素形如 {@code "Cookie@xxx"}。
     */
    private boolean parsePlayJson(String u, HkPlay out) {
        try {
            JSONObject o = new JSONObject(u);
            JSONArray urls = o.optJSONArray("urls");
            if (urls != null && urls.length() > 0) out.setUrl(urls.optString(0, "").trim());
            JSONArray headers = o.optJSONArray("headers");
            if (headers != null) {
                for (int i = 0; i < headers.length(); i++) {
                    String kv = headers.optString(i, "");
                    int at = kv.indexOf('@');
                    if (at > 0) out.putHeader(kv.substring(0, at).trim(), kv.substring(at + 1).trim());
                }
            }
            String sub = o.optString("subtitle", "").trim();
            if (!sub.isEmpty()) out.setSubtitle(sub);
            return !out.isEmpty();
        } catch (Throwable e) {
            Logger.t(TAG).d("play json failed: %s", e.getMessage());
            return false;
        }
    }

    /**
     * 冲突字符解码（官方 StringUtil.decodeConflictStr）：
     * {@code ？？}→{@code ?}、{@code ＆＆}→{@code &}、{@code ；；}→{@code ;}、{@code ，，}→{@code ,}。
     */
    private String decodeConflict(String s) {
        if (s == null) return "";
        return s.replace("？？", "?").replace("＆＆", "&").replace("；；", ";").replace("，，", ",");
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
