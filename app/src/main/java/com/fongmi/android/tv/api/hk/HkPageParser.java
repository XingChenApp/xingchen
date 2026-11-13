package com.fongmi.android.tv.api.hk;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 直接移植自海阔 8.83 {@code com.example.hikerview.service.parser.PageParser}（do not rewrite）。
 * <p>
 * 处理 {@code hiker://page/} 子页面导航。
 * 适配：
 * <ul>
 * <li>ArticleListRule → {@link HkRule}；ArticleListPageRule → {@link HkPage}</li>
 * <li>LitePal 按 title 查规则 → {@link HkRuleManager#getRule(String)}</li>
 * <li>fastjson → Gson；Timber → Log</li>
 * <li>goPageByPageUrl 的 Activity 跳转改为返回解析后的 {@link HkRule}（宿主自行导航）</li>
 * </ul>
 */
public class HkPageParser {
    private static final String TAG = "HkPageParser";
    private static final Gson GSON = new Gson();

    /** 按标题查规的注入点：生产恒走 HkRuleManager；离线夹具可替换为桩（不改生产口径）。 */
    public interface RuleLookup {
        HkRule get(String title);
    }

    private static volatile RuleLookup ruleLookupOverride;

    public static void setRuleLookupOverride(RuleLookup lookup) {
        ruleLookupOverride = lookup;
    }

    private static HkRule lookupRule(String title) {
        RuleLookup override = ruleLookupOverride;
        return override != null ? override.get(title) : HkRuleManager.get().getRule(title);
    }

    /** 8.83 ArticleListPageRule 的最小移植（HkRule.pages JSON 数组的元素类型）。 */
    public static class HkPage {
        @com.google.gson.annotations.SerializedName("col_type")
        private String col_type;
        @com.google.gson.annotations.SerializedName("name")
        private String name;
        private Map<String, String> params;
        private transient String parent;
        private transient HkRule parentRule;
        @com.google.gson.annotations.SerializedName("path")
        private String path;
        @com.google.gson.annotations.SerializedName("rule")
        private String rule;

        public String getCol_type() {
            return col_type;
        }

        public void setCol_type(String col_type) {
            this.col_type = col_type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, String> getParams() {
            return params;
        }

        public void setParams(Map<String, String> params) {
            this.params = params;
        }

        public String getParent() {
            return parent;
        }

        public void setParent(String parent) {
            this.parent = parent;
        }

        public HkRule getParentRule() {
            return parentRule;
        }

        public void setParentRule(HkRule parentRule) {
            this.parentRule = parentRule;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getRule() {
            return rule;
        }

        public void setRule(String rule) {
            this.rule = rule;
        }
    }

    /** 8.83 ParseException 的最小移植。 */
    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    public static boolean isPageUrl(String url) {
        return HkParserKit.isNotEmpty(url) && url.startsWith("hiker://page/");
    }

    /**
     * 8.83 原文（PageParser.smali，jadx 未能反编译，按内嵌 smali 逐分支移植）：
     * 由 hiker://page/ URL 构造下一页的 HkRule。
     */
    public static HkRule getNextPage(HkRule rule, String url, String paramsJson) throws ParseException {
        if (!isPageUrl(url)) {
            return null;
        }
        HkRule next = new HkRule();
        String ruleTitle = null;
        Map<String, String> paramsMap = null;
        if (HkParserKit.isNotEmpty(paramsJson) && paramsJson.startsWith("{") && paramsJson.endsWith("}")) {
            try {
                paramsMap = GSON.fromJson(paramsJson, new TypeToken<Map<String, String>>() {
                }.getType());
                if (paramsMap != null && paramsMap.containsKey("rule")) {
                    ruleTitle = paramsMap.get("rule");
                }
            } catch (Exception e) {
                // 参数解析失败不许静默丢参继续拼页：本方法本就 throws
                // ParseException、调用方（HkListActivity 等）对 ParseException
                // 有弹消息的既有出口，抛出去显形（批 residual）。
                Log.e(TAG, "getNextPage params parse failed", e);
                throw new ParseException("子页参数解析失败：" + e.getMessage());
            }
        }
        HkPage pageRule = parsePageRule(rule, ruleTitle, url);
        HkRule parentRule = pageRule.getParentRule();
        next.setFindRule(pageRule.getRule());
        next.setPreRule(parentRule.getPreRule());
        // 8.83 PageParser.getNextPage 继承序：preRule 之后、group 之前拷 proxy
        // （PageParser.smali:141-145 parentRule.getProxy→next.setProxy）；
        // 旧形漏此一行，带 proxy 的规则翻子页后代理丢失（G47）。
        next.setProxy(parentRule.getProxy());
        next.setGroup(parentRule.getGroup());
        next.setUa(parentRule.getUa());
        next.setTitle(parentRule.getTitle());
        next.setLastChapterRule(parentRule.getLastChapterRule());
        next.setColType(pageRule.getCol_type());
        next.setPages(parentRule.getPages());
        if (HkParserKit.isEmpty(next.getColType()) || "*".equals(next.getColType())) {
            next.setColType(parentRule.getColType());
        }
        Map<String, String> pageParams = pageRule.getParams();
        if (pageParams != null && pageParams.containsKey("url")) {
            next.setUrl(pageParams.get("url"));
        } else if (paramsMap != null && paramsMap.containsKey("url") && paramsMap.get("url") != null) {
            next.setUrl(paramsMap.get("url"));
        } else {
            next.setUrl(url);
        }
        if (HkParserKit.isNotEmpty(paramsJson) && paramsJson.startsWith("{") && paramsJson.endsWith("}")) {
            // 照 8.83 PageParser 原文 rule.setParams(params)：只写唯一的可序列化 params 字段，
            // 子页规则过 Intent 的 JSON 往返后仍在，MY_PARAMS 从 getParams() 生成（D6 口径）。
            next.setParams(paramsJson);
        }
        return next;
    }

    public static String getUrl(String url, String paramsJson) {
        // 环10 对官（PageParser.getUrl，smali:305-376）：paramsJson 为 {…} 且
        // 解析后含 "url" 且其值非 null → 回该 url；余回原 url。旧形恒回 url
        // 且注释自称「逻辑恒等」——与官原文不符（官有 paramsJson.url 支），
        // 已按官补回并订正注释。
        if (HkParserKit.isNotEmpty(paramsJson) && paramsJson.startsWith("{") && paramsJson.endsWith("}")) {
            try {
                // 复核订正7：逐键强转保原文——不用 Map<String,Object> 全表解析
                // （数值会被 Gson 统一 Double 化，如 123→"123.0"）；只取 "url"
                // 一键按 fastjson getString 口径转：串直回、数保原始字面、
                // 对象/数组回 JSON 文本（其内数值同样保字面）。
                com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(paramsJson);
                if (root != null && root.isJsonObject()) {
                    com.google.gson.JsonObject obj = root.getAsJsonObject();
                    if (obj.has("url")) {
                        com.google.gson.JsonElement u = obj.get("url");
                        if (u != null && !u.isJsonNull()) {
                            if (u.isJsonPrimitive()) {
                                com.google.gson.JsonPrimitive p = u.getAsJsonPrimitive();
                                if (p.isString()) return p.getAsString();
                                if (p.isNumber()) return p.getAsNumber().toString();
                                if (p.isBoolean()) return String.valueOf(p.getAsBoolean());
                            }
                            return u.toString();
                        }
                    }
                }
            } catch (Exception e) {
                // 回原 url 是官形契约不变，但留痕带入参，与「params 里本无
                // url 键」（无此日志）可区分（批 residual）。
                Log.e(TAG, "getUrl params parse failed, fallback to url=" + url
                        + " params=" + (paramsJson.length() > 120 ? paramsJson.substring(0, 120) : paramsJson), e);
            }
        }
        return url;
    }

    private static HkPage parsePageRule(HkRule rule, String ruleTitle, String url) throws ParseException {
        String title = rule == null ? null : rule.getTitle();
        Map<String, String> params = HkHttpParser.getParamsByUrl(url);
        if (HkParserKit.isNotEmpty(ruleTitle) && !params.containsKey("rule")) {
            params.put("rule", ruleTitle);
        }
        if (params.containsKey("rule")) {
            title = params.get("rule");
            if (HkParserKit.isEmpty(title)) {
                title = rule == null ? null : rule.getTitle();
            } else {
                rule = lookupRule(title);
                if (rule == null) {
                    throw new ParseException("找不到“" + title + "”这个小程序");
                }
            }
        }
        String path = HkParserKit.clearTag(HkParserKit.replaceOnce(
                HkParserKit.splitUrlByQuestionMark(url.split(";")[0])[0], "hiker://page/", ""));
        if (rule == null) {
            throw new ParseException("找不到对应的小程序");
        }
        List<HkPage> pageList = getPageList(rule);
        HkPage found = null;
        if (HkParserKit.isNotEmpty(pageList)) {
            for (HkPage p : pageList) {
                if (path.equals(p.getPath())) {
                    found = p;
                    break;
                }
            }
        }
        if (found == null) {
            throw new ParseException("找不到“" + path + "”这个页面");
        }
        found.setParent(title);
        found.setParentRule(rule);
        found.setParams(params);
        return found;
    }

    /** 8.83 ArticleListRule.getPageList：pages JSON → List<HkPage>。 */
    public static List<HkPage> getPageList(HkRule rule) {
        List<HkPage> list = new ArrayList<>();
        if (rule == null || HkParserKit.isEmpty(rule.getPages())) {
            return list;
        }
        try {
            List<HkPage> parsed = GSON.fromJson(rule.getPages(),
                    TypeToken.getParameterized(List.class, HkPage.class).getType());
            if (parsed != null) {
                list.addAll(parsed);
            }
        } catch (Exception e) {
            // 回空表是官形契约（parsePageRule 据空表抛「找不到页面」、
            // 其余调用方按无子页处理），留痕带规则标题，与「规则本无
            // pages」（无此日志）可区分（批 residual）。
            Log.e(TAG, "getPageList parse failed rule=" + rule.getTitle(), e);
        }
        return list;
    }

    /**
     * 8.83 goPageByPageUrl 的逻辑核心（去掉 Activity 跳转，返回解析后的规则，宿主自行导航）。
     *
     * @return 下一页规则；非 page URL 或解析失败返回 null
     */
    public static HkRule resolvePageUrl(String url) {
        try {
            HkRule next = getNextPage(null, url, null);
            if (next == null) {
                return null;
            }
            if (next.getUrl() != null && next.getUrl().contains("#noHistory#")) {
                next.setUrl(HkParserKit.replaceOnce(next.getUrl(), "#noHistory#", ""));
            }
            return next;
        } catch (Exception e) {
            Log.e(TAG, "resolvePageUrl", e);
            return null;
        }
    }
}
