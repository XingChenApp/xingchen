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
        String paramsUrl = null;
        if (HkParserKit.isNotEmpty(paramsJson) && paramsJson.startsWith("{") && paramsJson.endsWith("}")) {
            try {
                // 照官 JSONObject 形态（8.83 PageParser 原文，账
                // hk-subpage-params-array-20261114）：fastjson parseObject
                // 解成 JSONObject、rule/url 逐键 getString 取——值是任意
                // JSON 形状都合法（extra 带 longClick/blockRules 等数组/
                // 对象值是海阔常规写法），非串值按 getString 口径回其
                // JSON 文本；整体解析失败吞掉置空、组页继续，官没有
                // 「子页参数解析失败」这句。此前停在 Map<String,String>
                // 强类型解：数组/对象值撞 BEGIN_ARRAY/BEGIN_OBJECT 抛
                // 错、子页打不开，已摘。逐键转串口径与同文件 getUrl 的
                // 复核订正7 逐键法同形（见 jsonGetString）。
                com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(paramsJson);
                if (root != null && root.isJsonObject()) {
                    com.google.gson.JsonObject obj = root.getAsJsonObject();
                    ruleTitle = jsonGetString(obj, "rule");
                    paramsUrl = jsonGetString(obj, "url");
                }
            } catch (Exception e) {
                // 官形：解析失败只留痕（官 Timber.e 对位），params 视为空。
                Log.e(TAG, "getNextPage params parse failed", e);
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
        } else if (paramsUrl != null) {
            next.setUrl(paramsUrl);
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

    /**
     * fastjson getString 口径逐键取串（与 getUrl 复核订正7 逐键法同形）：
     * 串直回、数保原始字面、布尔转串、对象/数组回 JSON 文本；
     * 键缺失或 JsonNull 回 null。
     */
    private static String jsonGetString(com.google.gson.JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        com.google.gson.JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isString()) return p.getAsString();
            if (p.isNumber()) return p.getAsNumber().toString();
            if (p.isBoolean()) return String.valueOf(p.getAsBoolean());
        }
        return el.toString();
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
            List<HkPage> parsed = parsePageListJson(rule.getPages());
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
     * pages 定义解析（面②#2，136 批 W2）：官 ArticleListPageRule.params 带
     * {@code @JSONField(deserialize=false)}——解析 pages 时根本不读 params；
     * 其余标量字段经 fastjson parseString 收敛。咱家旧形 Gson 直解
     * List&lt;HkPage&gt;：任一页 params 带数组/对象值、或 col_type/name/path/rule
     * 为非串，整表抛错成空表、该规则全部子页瘫痪。改法：pages 为 JsonArray
     * 时逐页预处理——params 键先摘掉不喂 Gson（定义 params 无消费方：
     * parsePageRule 在读 params 前恒以 URL 参数 setParams 覆盖，见
     * getNextPage:177 读的是运行期那份；其余 getPageList 消费方只读
     * name/path/rule），其余 String 字段的非串值按 parseString 收敛成文本
     * 再交 Gson。pages 非数组/坏 JSON 时走回旧直解路，失败仍由 getPageList
     * 的 catch 成空表（官形契约不变）。
     */
    private static List<HkPage> parsePageListJson(String pages) {
        com.google.gson.JsonElement el = null;
        try {
            el = com.google.gson.JsonParser.parseString(pages);
        } catch (Exception ignored) {
            // 坏 JSON 落回下方旧直解路抛错、由调用方 catch 收空表，与旧形同终态。
        }
        if (el == null || !el.isJsonArray()) {
            return GSON.fromJson(pages,
                    TypeToken.getParameterized(List.class, HkPage.class).getType());
        }
        String[] stringFields = {"col_type", "name", "path", "rule"};
        for (com.google.gson.JsonElement item : el.getAsJsonArray()) {
            if (item == null || !item.isJsonObject()) continue;
            com.google.gson.JsonObject page = item.getAsJsonObject();
            page.remove("params");
            for (String field : stringFields) {
                if (!page.has(field)) continue;
                com.google.gson.JsonElement v = page.get(field);
                if (v == null || v.isJsonNull()) continue;
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) continue;
                String text = HkRuleManager.fastJsonValueText(v);
                if (text != null) page.addProperty(field, text);
            }
        }
        return GSON.fromJson(el.toString(),
                TypeToken.getParameterized(List.class, HkPage.class).getType());
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
