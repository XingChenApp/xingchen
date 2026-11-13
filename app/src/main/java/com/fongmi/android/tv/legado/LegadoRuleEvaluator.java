package com.fongmi.android.tv.legado;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Legado 规则表达式求值器（阶段一主线形态）。
 * <p>
 * 支持的语言：Jsoup CSS（Default，{@code @@} 可省）、JSONPath（{@code @Json:}/{@code $.}）、
 * 正则（{@code :} 开头三形态）、JavaScript（{@code <js></js>} / {@code {{}}} / {@code @js:}）。
 * 组合符：{@code &&} 链式、{@code ||} 备选、{@code %%} 逐元素映射。
 * 跨阶段变量：{@code @put:{k:rule}} / {@code @get:{k}}，另有 {@code java.put/get} 桥。
 * <p>
 * 阶段一明确不支持（报源级错，不崩壳）：XPath（{@code @XPath:}/{@code //}）、
 * {@code @css:} 扩展伪类（退化为普通 CSS）、URL 模板 {@code <a,b>} 条件页。
 * 纯 Java，无 android.* 依赖，离线回放脚手架直接调用本类。
 */
public class LegadoRuleEvaluator {

    /** 求值上下文。 */
    public static class Ctx {
        /** 根选择器（HTML Document 或 JSON 根）。 */
        public Sel root;
        /** 原始响应文本（正则模式与 JS 的 result 用）。 */
        public String rawText = "";
        public String baseUrl = "";
        /** @put/@get 与 java.put/get 共享变量（管线内整本书会话共享）。 */
        public Map<String, String> vars = new HashMap<>();
        public final Map<String, String> book = new HashMap<>();
        public final Map<String, String> chapter = new HashMap<>();
        public final Map<String, String> source = new HashMap<>();
        public final Map<String, String> cookie = new HashMap<>();
        public String title = "";
        public String charset = "utf-8";
        public LegadoHttp http;
        /** 延迟创建的 java.* 桥（JS 求值用）。 */
        public LegadoJsBridge bridge;
    }

    /** 选择器：HTML 元素或 JSON 节点。 */
    public abstract static class Sel {
        public abstract boolean isJson();
    }

    static class HtmlSel extends Sel {
        final Element el;
        HtmlSel(Element el) { this.el = el; }
        @Override public boolean isJson() { return false; }
    }

    static class JsonSel extends Sel {
        final Object node;
        JsonSel(Object node) { this.node = node; }
        @Override public boolean isJson() { return true; }
    }

    /** 建上下文：HTML/JSON 自适应。 */
    public static Ctx newCtx(String rawText, String baseUrl, LegadoHttp http) {
        Ctx ctx = new Ctx();
        ctx.rawText = rawText == null ? "" : rawText;
        ctx.baseUrl = baseUrl == null ? "" : baseUrl;
        ctx.http = http;
        String t = ctx.rawText.trim();
        if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) {
            try {
                Object node = t.startsWith("{") ? new JSONObject(t) : new JSONArray(t);
                ctx.root = new JsonSel(node);
                return ctx;
            } catch (Throwable ignored) {
            }
        }
        Document doc = Jsoup.parse(ctx.rawText, ctx.baseUrl);
        ctx.root = new HtmlSel(doc);
        return ctx;
    }

    // ================= 对外 API =================

    /** 取元素集（bookList/chapterList 用）。 */
    public static List<Sel> elements(Ctx ctx, String rule) {
        return elements(ctx.root, preprocess(ctx, ctx.root, rule));
    }

    /**
     * 取单字符串。isUrl=true 时无后缀默认取 href 并转绝对地址。
     * 永不返回 null（正则可选分组未参与匹配等形态会产出 null；
     * 下游 search()/showHits() 在主线程拼串，null 会闪退——壳子侧兜住）。
     */
    public static String str(Sel scope, Ctx ctx, String rule, boolean isUrl) {
        String v = strInner(scope, ctx, rule, isUrl);
        return v == null ? "" : v;
    }

    private static String strInner(Sel scope, Ctx ctx, String rule, boolean isUrl) {
        rule = preprocess(ctx, scope, rule);
        if (rule.isEmpty()) return "";
        // URL 规则里的 {expr} 模板（如 bookUrl 中的 {$.id}）：原版阅读会按当前节点求值。
        // 只在 isUrl 时展开——正则规则里的 {n} 量词（如 :\d{3}）不能碰。
        if (isUrl) {
            rule = expandBraces(scope, ctx, rule);
            // 展开后若已是干净 URL，直接返回，不再走选择器求值
            //（http 串按 CSS/JSONPath 求值只会得到空）。
            if (!rule.contains("{") && !rule.contains("}")
                    && rule.regionMatches(true, 0, "http", 0, 4)) {
                return rule;
            }
        }
        String gv = pureGet(ctx, rule);
        if (gv != null) {
            if (isUrl && !gv.isEmpty() && !gv.startsWith("http") && !gv.startsWith("data:")) {
                gv = absUrl(ctx.baseUrl, gv);
            }
            return gv;
        }
        int mode = detect(rule);
        if (mode == Mode.JS) return jsEval(ctx, stripJs(rule));
        if (mode == Mode.XPATH) throw new LegadoException("XPath 规则暂不支持（阶段一）: " + abbrev(rule));
        if (mode == Mode.JSON) return jsonStr(scope, stripJson(rule));
        if (mode == Mode.REGEX) return regexStr(textOf(scope, ctx), stripRegex(rule));
        return cssStr(scope, ctx, rule, isUrl);
    }

    /** 取字符串列表（%% 映射或 JSON [*] 用）。 */
    public static List<String> strList(Sel scope, Ctx ctx, String rule) {
        rule = preprocess(ctx, scope, rule);
        if (rule.isEmpty()) return new ArrayList<>();
        String gv = pureGet(ctx, rule);
        if (gv != null) {
            List<String> one = new ArrayList<>();
            one.add(gv);
            return one;
        }
        int mode = detect(rule);
        if (mode == Mode.JS) {
            Object o = jsEvalRaw(ctx, stripJs(rule));
            return toStringList(o);
        }
        if (mode == Mode.XPATH) throw new LegadoException("XPath 规则暂不支持（阶段一）: " + abbrev(rule));
        if (mode == Mode.JSON) return jsonList(scope, stripJson(rule));
        if (mode == Mode.REGEX) return regexList(textOf(scope, ctx), stripRegex(rule));
        // Default：%% 逐元素映射
        String[] parts = splitTop(rule, "%%");
        if (parts.length > 1) {
            List<Sel> els = elements(scope, parts[0]);
            List<String> out = new ArrayList<>();
            for (Sel el : els) out.add(cssStr(el, ctx, parts[1], false));
            return out;
        }
        List<Sel> els = elements(scope, rule);
        List<String> out = new ArrayList<>();
        for (Sel el : els) {
            if (el instanceof HtmlSel) out.add(((HtmlSel) el).el.text());
            else out.add(String.valueOf(((JsonSel) el).node));
        }
        return out;
    }

    // ================= 语言判定 =================

    private static class Mode {
        static final int DEFAULT = 0, JSON = 1, REGEX = 2, JS = 3, XPATH = 4;
    }

    private static int detect(String rule) {
        String r = rule.trim();
        if ((r.startsWith("<js>") && r.endsWith("</js>"))
                || (r.startsWith("{{") && r.endsWith("}}"))
                || r.startsWith("@js:")) return Mode.JS;
        if (r.startsWith("@XPath:") || r.startsWith("//")) return Mode.XPATH;
        if (r.startsWith("@Json:") || r.startsWith("$.")) return Mode.JSON;
        if (r.startsWith(":")) return Mode.REGEX;
        return Mode.DEFAULT;
    }

    private static String stripJs(String r) {
        String t = r.trim();
        if (t.startsWith("<js>") && t.endsWith("</js>")) return t.substring(4, t.length() - 5);
        if (t.startsWith("{{") && t.endsWith("}}")) return t.substring(2, t.length() - 2);
        if (t.startsWith("@js:")) return t.substring(4);
        return t;
    }

    private static String stripJson(String r) {
        String t = r.trim();
        if (t.startsWith("@Json:")) return t.substring(6);
        return t;
    }

    private static String stripRegex(String r) {
        String t = r.trim();
        return t.startsWith(":") ? t.substring(1) : t;
    }

    private static String abbrev(String r) {
        return r.length() > 60 ? r.substring(0, 60) + "…" : r;
    }

    // ================= @put/@get 预处理 =================

    private static String preprocess(Ctx ctx, Sel scope, String rule) {
        if (rule == null) return "";
        String r = rule;

        // @put:{key:rule}：求值后存入 vars，从规则中摘除
        for (;;) {
            int i = r.indexOf("@put:{");
            if (i < 0) break;
            int end = balancedEnd(r, i + 6);
            if (end < 0) throw new LegadoException("@put:{…} 花括号不配对: " + abbrev(r));
            String inner = r.substring(i + 6, end);
            int colon = inner.indexOf(':');
            if (colon < 0) throw new LegadoException("@put 需为 key:rule 形态: " + abbrev(r));
            String key = inner.substring(0, colon).trim();
            String sub = inner.substring(colon + 1);
            String val = str(scope, ctx, sub, false);
            ctx.vars.put(key, val);
            r = r.substring(0, i) + r.substring(end + 1);

        }
        // @get:{key}：纯引用由 pureGet 在预处理后直接取值；嵌入式不展开（罕见，展开会污染规则）
        return r.trim();
    }

    /**
     * URL 规则中的 {expr} 单花括号模板求值（原版阅读行为）。
     * {{...}} 是 JS 占位，跳过不碰（由各自调用方处理）；@put:{...} 已在 preprocess 摘除。
     * 花括号不配对时原样保留，不抛错（容错优先）。
     */
    private static String expandBraces(Sel scope, Ctx ctx, String rule) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < rule.length()) {
            char c = rule.charAt(i);
            if (c == '{' && i + 1 < rule.length() && rule.charAt(i + 1) == '{') {
                int end = rule.indexOf("}}", i + 2);
                if (end < 0) { sb.append(rule.substring(i)); break; }
                sb.append(rule, i, end + 2);
                i = end + 2;
                continue;
            }
            // @get:{k} 是原子引用（由 pureGet 取值）：{k} 不是模板，不许展开——
            // 展开会把规则打成 "@get:" 半截串、后续按 CSS 解析直接抛错
            // （目录 chapterUrl 的 @put/@get 组合实证，harness A14）
            if (c == '@' && rule.startsWith("@get:{", i)) {
                int depth = 1, j = i + 6;
                while (j < rule.length() && depth > 0) {
                    char d = rule.charAt(j);
                    if (d == '{') depth++;
                    else if (d == '}') depth--;
                    j++;
                }
                if (depth != 0) { sb.append(rule.substring(i)); break; }
                sb.append(rule, i, j);
                i = j;
                continue;
            }
            if (c == '{') {
                int depth = 1, j = i + 1;
                while (j < rule.length() && depth > 0) {
                    char d = rule.charAt(j);
                    if (d == '{') depth++;
                    else if (d == '}') depth--;
                    j++;
                }
                if (depth != 0) { sb.append(rule.substring(i)); break; }
                String inner = rule.substring(i + 1, j - 1);
                String val;
                try {
                    val = strInner(scope, ctx, inner, false);
                } catch (Throwable t) {
                    val = "";
                }
                sb.append(val == null ? "" : val);
                i = j;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** 纯 @get:{k} 引用：直接返回值，不进规则求值。 */
    private static String pureGet(Ctx ctx, String rule) {        String t = rule.trim();
        if (t.startsWith("@get:{") && t.endsWith("}") && t.indexOf('{', 6) < 0) {
            String key = t.substring(6, t.length() - 1).trim();
            String v = ctx.vars.get(key);
            return v == null ? "" : v;
        }
        return null;
    }

    private static int balancedEnd(String s, int open) {
        int depth = 1;
        for (int i = open + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    // ================= Default（CSS） =================

    private static List<Sel> elements(Sel scope, String rule) {
        List<Sel> out = new ArrayList<>();
        if (!(scope instanceof HtmlSel)) {
            // JSON 上下文的元素集：用 jsonList 的节点版
            for (Object n : jsonNodes((JsonSel) scope, rule)) out.add(new JsonSel(n));
            return out;
        }
        String sel = rule;
        if (sel.startsWith("@css:")) sel = sel.substring(5); // 阶段一：退化为普通 CSS
        // || 备选（与 cssStr 一致）：首个非空胜出
        String[] alts = splitTop(sel, "||");
        if (alts.length > 1) {
            for (String alt : alts) {
                List<Sel> r = elements(scope, alt);
                if (!r.isEmpty()) return r;
            }
            return out;
        }
        // 取第一个 %% 之前的选择器部分
        String[] parts = splitTop(sel, "%%");
        String chain = parts[0];
        Elements current = null;
        Element rootEl = ((HtmlSel) scope).el;
        for (String part : splitTop(chain, "&&")) {
            String p = stripSuffix(part).trim();
            if (p.isEmpty()) {
                // 空选择器 = 作用域元素自身（@href/@text 裸写法）
                if (current == null) { current = new Elements(); current.add(rootEl); }
                continue;
            }
            try {
                if (current == null) current = rootEl.select(p);
                else {
                    Elements next = new Elements();
                    for (Element e : current) next.addAll(e.select(p));
                    current = next;
                }
            } catch (Throwable t) {
                throw new LegadoException("CSS 选择器非法: " + abbrev(p) + "（" + t.getMessage() + "）");
            }
        }
        if (current != null) for (Element e : current) out.add(new HtmlSel(e));
        return out;
    }

    private static String cssStr(Sel scope, Ctx ctx, String rule, boolean isUrl) {
        // %% 逐元素映射：结果以换行拼接
        String[] parts = splitTop(rule, "%%");
        if (parts.length > 1) {
            List<Sel> els = elements(scope, parts[0]);
            StringBuilder sb = new StringBuilder();
            for (Sel el : els) {
                String s = cssAtom(el, ctx, parts[1], isUrl);
                if (!s.isEmpty()) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(s);
                }
            }
            return sb.toString();
        }
        // || 备选：首个非空胜出
        String[] alts = splitTop(rule, "||");
        if (alts.length > 1) {
            for (String alt : alts) {
                String s = cssAtomChain(scope, ctx, alt, isUrl);
                if (!s.isEmpty()) return s;
            }
            return "";
        }
        return cssAtomChain(scope, ctx, rule, isUrl);
    }

    private static String cssAtomChain(Sel scope, Ctx ctx, String rule, boolean isUrl) {
        if (scope instanceof JsonSel) {
            // JSON 上下文里混写 CSS：退化为节点转字符串
            return jsonStr(scope, rule);
        }
        String[] chain = splitTop(rule, "&&");
        Sel cur = scope;
        for (int i = 0; i < chain.length - 1; i++) {
            List<Sel> els = elements(cur, chain[i]);
            if (els.isEmpty()) return "";
            cur = els.get(0);
        }
        List<Sel> last = elements(cur, chain[chain.length - 1]);
        if (last.isEmpty()) return "";
        // 多个命中：单值取首个（与 Legado getString 一致）
        // 末步直接提取：last 已经是完整选择器的命中结果，对它再套完整选择器
        // jsoup 不回溯匹配上下文自身，后代选择器恒返回空（如 h3 a@text）。
        return cssAtomDirect(last.get(0), ctx, chain[chain.length - 1], isUrl);
    }

    /** 原子提取（直接版）：scope 已是选择器的命中元素，直接按后缀提取，不重套选择器。 */
    private static String cssAtomDirect(Sel scope, Ctx ctx, String rule, boolean isUrl) {
        if (!(scope instanceof HtmlSel)) return jsonStr(scope, rule);
        Element target = ((HtmlSel) scope).el;
        Atom a = parseAtom(rule);
        String val;
        switch (a.extractor) {
            case ATTR:
                val = a.attr.startsWith("abs:") ? target.absUrl(a.attr.substring(4)) : target.attr(a.attr);
                break;
            case TEXT_NODES: {
                StringBuilder sb = new StringBuilder();
                for (org.jsoup.nodes.TextNode tn : target.textNodes()) sb.append(tn.text());
                val = sb.toString();
                break;
            }
            case OWN_TEXT: val = target.ownText(); break;
            case HTML: val = target.html(); break;
            case ALL: val = target.outerHtml(); break;
            case TEXT:
            default: val = target.text(); break;
        }
        if (a.regex != null) val = applyRegex(val, a.regex, a.replacement);
        if (isUrl && !val.isEmpty() && !val.startsWith("http") && !val.startsWith("data:")) {
            val = absUrl(ctx.baseUrl, val);
        }
        return val == null ? "" : val;
    }

    /** 单选择器＋后缀的原子求值。 */
    private static String cssAtom(Sel scope, Ctx ctx, String rule, boolean isUrl) {
        if (!(scope instanceof HtmlSel)) return jsonStr(scope, rule);
        Element el = ((HtmlSel) scope).el;
        Atom a = parseAtom(rule);
        Elements found;
        try {
            found = a.selector.isEmpty() ? new Elements(el) : el.select(a.selector);
        } catch (Throwable t) {
            throw new LegadoException("CSS 选择器非法: " + abbrev(a.selector));
        }
        if (found.isEmpty()) return "";
        Element target = found.first();
        String val;
        switch (a.extractor) {
            case ATTR:
                val = a.attr.startsWith("abs:") ? target.absUrl(a.attr.substring(4)) : target.attr(a.attr);
                break;
            case TEXT_NODES: {
                StringBuilder sb = new StringBuilder();
                for (org.jsoup.nodes.TextNode tn : target.textNodes()) sb.append(tn.text());
                val = sb.toString();
                break;
            }
            case OWN_TEXT: val = target.ownText(); break;
            case HTML: val = target.html(); break;
            case ALL: val = target.outerHtml(); break;
            case TEXT:
            default: val = target.text(); break;
        }
        if (a.regex != null) val = applyRegex(val, a.regex, a.replacement);
        if (isUrl && !val.isEmpty() && !val.startsWith("http") && !val.startsWith("data:")) {
            val = absUrl(ctx.baseUrl, val);
        }
        return val == null ? "" : val;
    }

    private enum Extractor { TEXT, TEXT_NODES, OWN_TEXT, HTML, ALL, ATTR }

    private static class Atom {
        String selector = "";
        Extractor extractor = Extractor.TEXT;
        String attr = "";
        String regex;
        String replacement;
        boolean hasExtractor;
    }

    private static Atom parseAtom(String rule) {
        Atom a = new Atom();
        String r = rule.trim();
        // ## 替换形态
        int di = r.lastIndexOf("##");
        if (di >= 0) {
            a.replacement = r.substring(di + 2);
            r = r.substring(0, di);
        }
        // 已知文本后缀优先
        String[] known = {":textNodes", ":ownText", ":all", ":text", ":html"};
        for (String k : known) {
            if (r.endsWith(k)) {
                a.hasExtractor = true;
                a.extractor = k.equals(":textNodes") ? Extractor.TEXT_NODES
                        : k.equals(":ownText") ? Extractor.OWN_TEXT
                        : k.equals(":all") ? Extractor.ALL
                        : k.equals(":html") ? Extractor.HTML : Extractor.TEXT;
                a.selector = r.substring(0, r.length() - k.length());
                return a;
            }
        }
        // @@ = 文本
        if (r.endsWith("@@")) {
            a.hasExtractor = true;
            a.selector = r.substring(0, r.length() - 2);
            return a;
        }
        // @text = 文本（真实书源常见写法；HTML 几乎不存在名为 text 的属性）
        if (r.endsWith("@text")) {
            a.hasExtractor = true;
            a.extractor = Extractor.TEXT;
            a.selector = r.substring(0, r.length() - 5);
            return a;
        }
        // 尾部 :regex（括号外最后一个冒号）
        int ci = lastColonOutsideParens(r);
        String core = r;
        if (ci >= 0) {
            String tail = r.substring(ci + 1);
            // 排除 @attr 形态里的冒号（abs:href）
            if (!tail.contains("@") && !tail.isEmpty()) {
                a.regex = tail;
                core = r.substring(0, ci);
            }
        }
        // @attr
        int ai = core.lastIndexOf('@');
        if (ai >= 0 && ai < core.length() - 1) {
            String after = core.substring(ai + 1);
            if (!after.contains(":") && !after.contains(" ") && !after.contains("[") && !after.contains("(")) {
                a.hasExtractor = true;
                a.extractor = Extractor.ATTR;
                a.attr = after;
                a.selector = core.substring(0, ai);
                return a;
            }
        }
        a.selector = core;
        return a;
    }

    private static String stripSuffix(String part) {
        // 元素集选择时去掉末尾提取后缀，只留选择器
        Atom a = parseAtom(part);
        return a.selector;
    }

    private static int lastColonOutsideParens(String s) {
        int depth = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') depth--;
            else if (c == ':' && depth == 0) return i;
        }
        return -1;
    }

    private static String applyRegex(String input, String regex, String replacement) {
        if (input == null) return "";
        try {
            Pattern p = Pattern.compile(regex, Pattern.DOTALL);
            Matcher m = p.matcher(input);
            if (replacement != null) return m.replaceAll(replacement);
            if (m.find()) return m.groupCount() >= 1 ? m.group(1) : m.group();
            return "";
        } catch (Throwable t) {
            throw new LegadoException("正则非法: " + abbrev(regex));
        }
    }

    // ================= JSONPath（子集） =================

    private static String jsonStr(Sel scope, String path) {
        List<String> l = jsonList(scope, path);
        return l.isEmpty() ? "" : l.get(0);
    }

    private static List<String> jsonList(Sel scope, String path) {
        List<String> out = new ArrayList<>();
        for (Object n : jsonNodes(scope, path)) {
            if (n == null || n == JSONObject.NULL) continue;
            out.add(String.valueOf(n));
        }
        return out;
    }

    private static List<Object> jsonNodes(Sel scope, String path) {
        List<Object> out = new ArrayList<>();
        if (!(scope instanceof JsonSel)) return out;
        Object cur = ((JsonSel) scope).node;
        String p = path.trim();
        if (p.startsWith("$")) p = p.substring(1);
        List<Object> current = new ArrayList<>();
        current.add(cur);
        for (String token : splitJsonPath(p)) {
            List<Object> next = new ArrayList<>();
            for (Object o : current) next.addAll(jsonStep(o, token));
            current = next;
        }
        out.addAll(current);
        return out;
    }

    private static List<String> splitJsonPath(String p) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            if (c == '.' && depth == 0) {
                if (sb.length() > 0) { tokens.add(sb.toString()); sb.setLength(0); }
            } else sb.append(c);
        }
        if (sb.length() > 0) tokens.add(sb.toString());
        return tokens;
    }

    private static List<Object> jsonStep(Object o, String token) {
        List<Object> out = new ArrayList<>();
        if (o == null || token.isEmpty()) return out;
        // name[idx] / name[*] / [idx]
        String name = token;
        String idx = null;
        int bi = token.indexOf('[');
        if (bi >= 0 && token.endsWith("]")) {
            name = token.substring(0, bi);
            idx = token.substring(bi + 1, token.length() - 1);
        }
        Object v = o;
        if (!name.isEmpty()) {
            if (o instanceof JSONObject) v = ((JSONObject) o).opt(name);
            else return out;
        }
        if (idx == null) {
            // JSONPath 语义：$.a.b 中 b 若是数组，逐元素展开为多条结果
            //（原版阅读行为）。旧写法把整个数组包成一条，bookList 永远只出 1 项
            // 且逐项取字段全空——JSON API 源全灭。
            if (v instanceof JSONArray) {
                JSONArray arr = (JSONArray) v;
                for (int i = 0; i < arr.length(); i++) {
                    Object e = arr.opt(i);
                    if (e != null && e != JSONObject.NULL) out.add(e);
                }
                return out;
            }
            if (v != null && v != JSONObject.NULL) out.add(v);
            return out;
        }
        if (v instanceof JSONArray) {
            JSONArray arr = (JSONArray) v;
            if ("*".equals(idx)) {
                for (int i = 0; i < arr.length(); i++) {
                    Object e = arr.opt(i);
                    if (e != null && e != JSONObject.NULL) out.add(e);
                }
            } else {
                try {
                    int n = Integer.parseInt(idx);
                    if (n < 0) n = arr.length() + n;
                    Object e = arr.opt(n);
                    if (e != null && e != JSONObject.NULL) out.add(e);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    // ================= 正则 =================

    private static String regexStr(String text, String rule) {
        String regex = rule;
        String replacement = null;
        int di = rule.lastIndexOf("##");
        if (di >= 0) {
            replacement = rule.substring(di + 2);
            regex = rule.substring(0, di);
        }
        return applyRegex(text, regex, replacement);
    }

    private static List<String> regexList(String text, String rule) {
        List<String> out = new ArrayList<>();
        String regex = rule;
        int di = rule.lastIndexOf("##");
        if (di >= 0) regex = rule.substring(0, di);
        try {
            Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text == null ? "" : text);
            while (m.find()) out.add(m.groupCount() >= 1 ? m.group(1) : m.group());
        } catch (Throwable t) {
            throw new LegadoException("正则非法: " + abbrev(regex));
        }
        return out;
    }

    private static String textOf(Sel scope, Ctx ctx) {
        if (scope instanceof HtmlSel) return ((HtmlSel) scope).el.text();
        if (scope instanceof JsonSel) return String.valueOf(((JsonSel) scope).node);
        return ctx.rawText;
    }

    // ================= JavaScript =================

    private static String jsEval(Ctx ctx, String code) {
        return toStr(jsEvalRaw(ctx, code));
    }

    static Object jsEvalRaw(Ctx ctx, String code) {
        if (ctx.bridge == null) ctx.bridge = new LegadoJsBridge(ctx);
        return ctx.bridge.eval(code);
    }

    private static String toStr(Object o) {
        if (o == null) return "";
        if (o instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object e : (List<?>) o) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(String.valueOf(e));
            }
            return sb.toString();
        }
        return String.valueOf(o);
    }

    static List<String> toStringList(Object o) {
        List<String> out = new ArrayList<>();
        if (o == null) return out;
        if (o instanceof List) {
            for (Object e : (List<?>) o) out.add(String.valueOf(e));
            return out;
        }
        // Rhino NativeArray
        try {
            Class<?> na = Class.forName("org.mozilla.javascript.NativeArray");
            if (na.isInstance(o)) {
                long len = ((Number) na.getMethod("getLength").invoke(o)).longValue();
                java.lang.reflect.Method get = na.getMethod("get", int.class, org.mozilla.javascript.Scriptable.class);
                for (int i = 0; i < len; i++) out.add(String.valueOf(get.invoke(o, i, null)));
                return out;
            }
        } catch (Throwable ignored) {
        }
        String s = String.valueOf(o);
        if (!s.isEmpty()) out.add(s);
        return out;
    }

    // ================= 杂项 =================

    /** 顶层分割（忽略括号内的分隔符）。 */
    static String[] splitTop(String s, String delim) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            if (depth == 0 && s.startsWith(delim, i)) {
                parts.add(sb.toString());
                sb.setLength(0);
                i += delim.length() - 1;
            } else sb.append(c);
        }
        parts.add(sb.toString());
        return parts.toArray(new String[0]);
    }

    static String absUrl(String base, String rel) {
        if (rel == null || rel.isEmpty()) return "";
        try {
            return new URL(new URL(base), rel).toString();
        } catch (Throwable t) {
            return rel;
        }
    }
}
