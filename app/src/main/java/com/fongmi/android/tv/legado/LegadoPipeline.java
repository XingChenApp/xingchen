package com.fongmi.android.tv.legado;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 书源执行管线：搜索 → 详情 → 目录 → 正文。
 * <p>
 * 每段独立 try/catch，失败抛带源名的 LegadoException（源级错，不崩壳）。
 * URL 模板：{@code {{key}}} / {@code {{page}}} / {@code {{var}}} 变量替换，
 * 尾部 {@code ,{...}} 请求选项 JSON（method/body/headers/charset）。
 */
public class LegadoPipeline {

    private static final int MAX_TOC_PAGES = 50;
    private static final int MAX_CONTENT_PAGES = 20;

    // ================= 结果 POJO =================

    public static class Book {
        public String name = "";
        public String author = "";
        public String coverUrl = "";
        public String bookUrl = "";
    }

    public static class BookInfo {
        public String bookUrl = "";
        public String name = "";
        public String author = "";
        public String coverUrl = "";
        public String intro = "";
        public String tocUrl = "";
    }

    public static class TocChapter {
        public String name = "";
        public String url = "";
    }

    public static class Content {
        public String title = "";
        /** 文本：正文；图片：换行分隔的图片 URL。 */
        public String text = "";
    }

    // ================= 请求选项 =================

    static class ReqOpt {
        String method = "GET";
        byte[] body;
        final Map<String, String> headers = new HashMap<>();
        String charset = "utf-8";
    }

    /** 解析「url,{json}」形态。 */
    static class UrlSpec {
        String url;
        final ReqOpt opt = new ReqOpt();
    }

    static UrlSpec parseUrl(String template, Map<String, String> vars) {
        return parseUrl(template, vars, null);
    }

    /**
     * 选项 JSON 解析：先严格 JSON；失败则做单引号归一化再试（生态源常见单引号/
     * 单双引号混写；严格合法的 JSON 归一化后原样，不受影响）。
     * 两次都失败返回 null（调用方按纯 URL 走）。
     */
    static JSONObject optJson(String json) {
        try {
            return new JSONObject(json);
        } catch (Throwable t1) {
            try {
                return new JSONObject(normalizeQuotes(json));
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    /**
     * 把单引号定界的 JSON 字符串归一化为双引号：
     * 双引号串里的单引号不动；单引号串里的双引号转义为 \"；\' 还原为 '。
     * 合法 JSON 走一遍原样返回。
     */
    static String normalizeQuotes(String json) {
        if (json == null) return "";
        StringBuilder sb = new StringBuilder(json.length());
        boolean inD = false;
        int i = 0, n = json.length();
        while (i < n) {
            char c = json.charAt(i);
            if (inD) {
                if (c == '\\' && i + 1 < n) { sb.append(c).append(json.charAt(i + 1)); i += 2; continue; }
                if (c == '"') inD = false;
                appendJsonChar(sb, c, true);
                i++; continue;
            }
            if (c == '"') { inD = true; sb.append(c); i++; continue; }
            if (c == '\'') {
                sb.append('"'); i++;
                while (i < n) {
                    char d = json.charAt(i);
                    if (d == '\\' && i + 1 < n && json.charAt(i + 1) == '\'') { sb.append('\''); i += 2; continue; }
                    if (d == '\\' && i + 1 < n) { sb.append(d).append(json.charAt(i + 1)); i += 2; continue; }
                    if (d == '\'') { sb.append('"'); i++; break; }
                    if (d == '"') sb.append("\\\"");
                    else appendJsonChar(sb, d, true);
                    i++;
                }
                continue;
            }
            sb.append(c); i++;
        }
        return sb.toString();
    }

    /** 往 JSON 字符串内容里追加字符：字面控制字符必须转义（org.json 严格拒绝）。 */
    private static void appendJsonChar(StringBuilder sb, char c, boolean inString) {
        if (inString) {
            if (c == '\n') { sb.append("\\n"); return; }
            if (c == '\r') { sb.append("\\r"); return; }
            if (c == '\t') { sb.append("\\t"); return; }
            if (c < 0x20) { sb.append(String.format("\\u%04x", (int) c)); return; }
        }
        sb.append(c);
    }

    /**
     * 解析「url,{json}」形态。charsetOverride 非空时，body 编码与响应解码回退
     * 都按它走（GBK 重试用；声明优先，override 只在没声明时由壳子传）。
     */
    static UrlSpec parseUrl(String template, Map<String, String> vars, String charsetOverride) {
        UrlSpec spec = new UrlSpec();
        String t = template == null ? "" : template.trim();
        int oi = t.lastIndexOf(",{");
        if (oi > 0 && t.endsWith("}")) {
            JSONObject o = optJson(t.substring(oi + 1));
            if (o == null) return finishUrl(spec, t, vars, charsetOverride);
            try {
                if (o.has("method")) spec.opt.method = o.optString("method", "GET");
                if (o.has("charset")) spec.opt.charset = o.optString("charset", "utf-8");
                if (charsetOverride != null) spec.opt.charset = charsetOverride;
                if (o.has("body")) {
                    String b = o.optString("body", "");
                    // body 按该 URL 声明的 charset 编码（GBK 站按 UTF-8 发 body
                    // 会导致服务端读到的 key 是乱码——与 legado AnalyzeUrl 行为对齐）
                    spec.opt.body = toBytes(b, spec.opt.charset);
                }
                if (o.has("headers")) {
                    JSONObject h = o.optJSONObject("headers");
                    if (h != null) {
                        for (java.util.Iterator<String> it = h.keys(); it.hasNext(); ) { String k = it.next(); spec.opt.headers.put(k, h.optString(k, "")); }
                    }
                }
                t = t.substring(0, oi);
            } catch (Throwable ignored) {
                // JSON 解析失败：按纯 URL 走
            }
        }
        return finishUrl(spec, t, vars, charsetOverride);
    }

    /** parseUrl 尾段：变量渲染＋body 二次渲染。 */
    private static UrlSpec finishUrl(UrlSpec spec, String t, Map<String, String> vars, String charsetOverride) {
        spec.url = renderVars(t, vars);
        // body 里的变量也渲染（重试时按 override 编码重发）
        if (spec.opt.body != null) {
            try {
                String cs = charsetOverride != null ? charsetOverride : "UTF-8";
                String b = new String(spec.opt.body, cs);
                spec.opt.body = renderVars(b, vars).getBytes(cs);
            } catch (Throwable ignored) {
            }
        }
        return spec;
    }

    private static String renderVars(String t, Map<String, String> vars) {
        if (t == null || vars == null) return t == null ? "" : t;
        Matcher m = Pattern.compile("\\{\\{([^}]+)\\}\\}").matcher(t);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1).trim();
            String v = vars.get(key);
            if (v == null) v = "";
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 取 URL 模板尾部 ,{...} 里声明的 charset（无声明→UTF-8）。
     * key 的 URL 编码与 POST body 编码都按它走——与 legado AnalyzeUrl 行为对齐：
     * GBK 站若按 UTF-8 发 key，服务端按 GBK 解码得到乱码，搜索恒 0 条
     * （m.manhuagui.com 实测：UTF-8 键回显乱码"鏂楃綏澶ч檰"0 条，GBK 键 4 条）。
     */
    private static String urlCharset(String template) {
        String d = declaredCharset(template);
        return d != null ? d : "UTF-8";
    }

    /**
     * 尾部 ,{...} 里是否声明了 charset（有→归一化后的值，无→null）。
     * 声明优先；没声明且默认编码搜回 0 条，壳子自动换 GBK 重试一次兜底
     * （旧源文件不用重新导入，装上直接能搜）。
     */
    private static String declaredCharset(String template) {
        String t = template == null ? "" : template.trim();
        int oi = t.lastIndexOf(",{");
        if (oi > 0 && t.endsWith("}")) {
            JSONObject o = optJson(t.substring(oi + 1));
            if (o != null && o.has("charset")) return normCharset(o.optString("charset", "utf-8"));
        }
        return null;
    }

    private static byte[] toBytes(String s, String charset) {
        String v = s == null ? "" : s;
        try {
            return v.getBytes(charset);
        } catch (Throwable t) {
            try {
                return v.getBytes("UTF-8");
            } catch (Throwable t2) {
                return new byte[0];
            }
        }
    }

    private static String urlEncode(String s, String charset) {
        try {
            return URLEncoder.encode(s == null ? "" : s, charset);
        } catch (Throwable e) {
            return s == null ? "" : s;
        }
    }

    // ================= 抓取 =================

    private final LegadoSource source;
    private final LegadoHttp http;
    /** 整本书会话变量（@put/@get 跨阶段）。 */
    private final Map<String, String> sessionVars = new HashMap<>();

    public LegadoPipeline(LegadoSource source) {
        this(source, LegadoHttpAndroid.get());
    }

    public LegadoPipeline(LegadoSource source, LegadoHttp http) {
        if (source == null) throw new LegadoException("书源为空");
        this.source = source;
        this.http = http == null ? LegadoHttpAndroid.get() : http;
    }

    private String fetchText(UrlSpec spec) {
        try {
            String url = spec.url;
            // 相对请求地址按书源站地址补全：生态源 searchUrl 常写相对路
            //（如 /api/search?q={{key}}）；原版 AnalyzeUrl 也是按源地址解析的。
            // 不补的话 LegadoHttpAndroid 直接报"不支持的协议"，这类源永远搜不出。
            if (url != null && !url.regionMatches(true, 0, "http", 0, 4)) {
                url = LegadoRuleEvaluator.absUrl(source.bookSourceUrl, url);
            }
            LegadoHttp.Result r = http.fetch(url, spec.opt.method, spec.opt.headers, spec.opt.body);
            String charset = charsetOf(r, spec.opt.charset);
            return new String(r.body, charset);
        } catch (LegadoException e) {
            throw e;
        } catch (Throwable t) {
            throw new LegadoException(source.tag() + " 抓取失败: " + t.getMessage());
        }
    }

    /**
     * 节点归属作用域：整个书源操作（抓取＋规则求值＋JS 桥 ajax）记到具体书源名下，
     * LegadoHttpAndroid 打点用。公共入口用 try-with-resources 包住整个操作。
     */
    public final class SourceScope implements AutoCloseable {
        private final String prevKey;

        SourceScope() {
            this.prevKey = LegadoHttpAndroid.swapSourceKey(source.bookSourceUrl);
        }

        @Override
        public void close() {
            LegadoHttpAndroid.restoreSourceKey(prevKey);
        }
    }

    /** 打开一次书源操作的作用域。 */
    public SourceScope scope() {
        return new SourceScope();
    }

    private String charsetOf(LegadoHttp.Result r, String fallback) {
        String ct = r.contentType == null ? "" : r.contentType.toLowerCase();
        int i = ct.indexOf("charset=");
        if (i >= 0) {
            String cs = ct.substring(i + 8).split("[; ]")[0].trim().replace("\"", "");
            if (!cs.isEmpty()) return normCharset(cs);
        }
        return normCharset(fallback);
    }

    /** GBK/Big5 按盘点要求处理。 */
    static String normCharset(String cs) {
        if (cs == null) return "UTF-8";
        String c = cs.trim().toLowerCase();
        if (c.contains("gbk") || c.contains("gb2312") || c.contains("gb18030")) return "GBK";
        if (c.contains("big5")) return "Big5";
        return "UTF-8";
    }

    private LegadoRuleEvaluator.Ctx ctxOf(String rawText, String baseUrl) {
        LegadoRuleEvaluator.Ctx ctx = LegadoRuleEvaluator.newCtx(rawText, baseUrl, http);
        ctx.vars = sessionVars;
        ctx.source.put("bookSourceUrl", source.bookSourceUrl);
        ctx.source.put("bookSourceName", source.bookSourceName);
        ctx.source.put("bookSourceType", String.valueOf(source.bookSourceType));
        if (source.jsLib != null && !source.jsLib.isEmpty()) {
            try {
                ctx.bridge = new LegadoJsBridge(ctx);
                ctx.bridge.eval(source.jsLib);
            } catch (Throwable t) {
                throw new LegadoException(source.tag() + " jsLib 执行失败: " + t.getMessage());
            }
        }
        return ctx;
    }

    private Map<String, String> baseVars() {
        Map<String, String> v = new HashMap<>();
        v.put("bookSourceUrl", source.bookSourceUrl);
        return v;
    }

    // ================= 搜索 =================

    public List<Book> search(String key) {
        return search(key, 1);
    }

    public List<Book> search(String key, int page) {
        // 节点归属：整个操作记到本书源名下（含 JS 桥 ajax）。
        try (SourceScope ignored = scope()) {
            if (source.ruleSearch == null || isEmpty(source.ruleSearch.bookList)) {
                throw new LegadoException(source.tag() + " 未配置搜索规则");
            }
            String declared = declaredCharset(source.searchUrl);
            List<Book> out = searchOnce(key, page, declared != null ? declared : "UTF-8");
            // 壳子兜底：源没声明 charset 且默认编码搜回 0 条，换 GBK 重试一次
            //（URL 上的词和 POST body 里的词一起换；旧源文件不用重新导入）。
            if (out.isEmpty() && declared == null) {
                out = searchOnce(key, page, "GBK");
            }
            return out;
        }
    }

    /** 单次搜索：key 按 charset 做 URL 编码；POST body 同步按该编码重发。 */
    private List<Book> searchOnce(String key, int page, String charset) {
        try {
            Map<String, String> vars = baseVars();
            // key 按指定 charset 编码（声明优先；壳子重试时传 GBK）
            vars.put("key", urlEncode(key, charset));
            vars.put("page", String.valueOf(page));
            UrlSpec spec = parseUrl(source.searchUrl, vars, charset);
            if (source.header != null) spec.opt.headers.putAll(source.header);
            String text = fetchText(spec);
            LegadoRuleEvaluator.Ctx ctx = ctxOf(text, spec.url);
            List<LegadoRuleEvaluator.Sel> els = LegadoRuleEvaluator.elements(ctx, source.ruleSearch.bookList);
            List<Book> out = new ArrayList<>();
            for (LegadoRuleEvaluator.Sel el : els) {
                Book b = new Book();
                b.name = LegadoRuleEvaluator.str(el, ctx, source.ruleSearch.name, false);
                b.author = LegadoRuleEvaluator.str(el, ctx, source.ruleSearch.author, false);
                b.coverUrl = LegadoRuleEvaluator.str(el, ctx, source.ruleSearch.coverUrl, true);
                b.bookUrl = LegadoRuleEvaluator.str(el, ctx, source.ruleSearch.bookUrl, true);
                if (!b.name.isEmpty() && !b.bookUrl.isEmpty()) out.add(b);
            }
            return out;
        } catch (LegadoException e) {
            throw e;
        } catch (Throwable t) {
            throw new LegadoException(source.tag() + " 搜索失败: " + t.getMessage());
        }
    }

    // ================= 详情 =================

    public BookInfo bookInfo(String bookUrl) {
        // 节点归属：整个操作记到本书源名下（含 JS 桥 ajax）。
        try (SourceScope ignored = scope()) {
            if (source.ruleBookInfo == null) throw new LegadoException(source.tag() + " 未配置详情规则");
            try {
                UrlSpec spec = parseUrl(bookUrl, baseVars());
                if (source.header != null) spec.opt.headers.putAll(source.header);
                String text = fetchText(spec);
                LegadoRuleEvaluator.Ctx ctx = ctxOf(text, spec.url);
                LegadoSource.RuleBookInfo r = source.ruleBookInfo;
                BookInfo info = new BookInfo();
                info.bookUrl = bookUrl;
                info.name = LegadoRuleEvaluator.str(ctx.root, ctx, r.name, false);
                info.author = LegadoRuleEvaluator.str(ctx.root, ctx, r.author, false);
                info.coverUrl = LegadoRuleEvaluator.str(ctx.root, ctx, r.coverUrl, true);
                info.intro = LegadoRuleEvaluator.str(ctx.root, ctx, r.intro, false);
                info.tocUrl = LegadoRuleEvaluator.str(ctx.root, ctx, r.tocUrl, true);
                return info;
            } catch (LegadoException e) {
                throw e;
            } catch (Throwable t) {
                throw new LegadoException(source.tag() + " 详情失败: " + t.getMessage());
            }
            }
    }

    // ================= 目录 =================

    public List<TocChapter> toc(String tocUrl) {
        // 节点归属：整个操作记到本书源名下（含 JS 桥 ajax）。
        try (SourceScope ignored = scope()) {
            if (source.ruleToc == null || isEmpty(source.ruleToc.chapterList)) {
                throw new LegadoException(source.tag() + " 未配置目录规则");
            }
            List<TocChapter> out = new ArrayList<>();
            String next = tocUrl;
            int pages = 0;
            try {
                while (next != null && !next.isEmpty() && pages < MAX_TOC_PAGES) {
                    pages++;
                    UrlSpec spec = parseUrl(next, baseVars());
                    if (source.header != null) spec.opt.headers.putAll(source.header);
                    String text = fetchText(spec);
                    LegadoRuleEvaluator.Ctx ctx = ctxOf(text, spec.url);
                    LegadoSource.RuleToc r = source.ruleToc;
                    // 优先逐元素；元素口径取空时退化为列表 zip
                    List<LegadoRuleEvaluator.Sel> els = LegadoRuleEvaluator.elements(ctx, r.chapterList);
                    boolean usedElements = false;
                    for (LegadoRuleEvaluator.Sel el : els) {
                        String name = LegadoRuleEvaluator.str(el, ctx, r.chapterName, false);
                        if (!name.isEmpty()) usedElements = true;
                    }
                    if (usedElements) {
                        for (LegadoRuleEvaluator.Sel el : els) {
                            TocChapter c = new TocChapter();
                            c.name = LegadoRuleEvaluator.str(el, ctx, r.chapterName, false);
                            c.url = LegadoRuleEvaluator.str(el, ctx, r.chapterUrl, true);
                            if (!c.name.isEmpty()) out.add(c);
                        }
                    } else {
                        List<String> names = LegadoRuleEvaluator.strList(ctx.root, ctx, r.chapterName);
                        List<String> urls = new ArrayList<>();
                        for (String u : LegadoRuleEvaluator.strList(ctx.root, ctx, r.chapterUrl)) {
                            urls.add(LegadoRuleEvaluator.absUrl(spec.url, u));
                        }
                        int n = Math.min(names.size(), urls.size());
                        for (int i = 0; i < n; i++) {
                            TocChapter c = new TocChapter();
                            c.name = names.get(i);
                            c.url = urls.get(i);
                            out.add(c);
                        }
                    }
                    next = LegadoRuleEvaluator.str(ctx.root, ctx, r.nextTocUrl, true);
                }
                return out;
            } catch (LegadoException e) {
                throw e;
            } catch (Throwable t) {
                throw new LegadoException(source.tag() + " 目录失败: " + t.getMessage());
            }
            }
    }

    // ================= 正文 =================

    public Content content(String chapterUrl) {
        // 节点归属：整个操作记到本书源名下（含 JS 桥 ajax）。
        try (SourceScope ignored = scope()) {
            if (source.ruleContent == null || isEmpty(source.ruleContent.content)) {
                throw new LegadoException(source.tag() + " 未配置正文规则");
            }
            Content c = new Content();
            StringBuilder sb = new StringBuilder();
            String next = chapterUrl;
            int pages = 0;
            try {
                while (next != null && !next.isEmpty() && pages < MAX_CONTENT_PAGES) {
                    pages++;
                    UrlSpec spec = parseUrl(next, baseVars());
                    if (source.header != null) spec.opt.headers.putAll(source.header);
                    String text = fetchText(spec);
                    LegadoRuleEvaluator.Ctx ctx = ctxOf(text, spec.url);
                    LegadoSource.RuleContent r = source.ruleContent;
                    if (pages == 1) c.title = LegadoRuleEvaluator.str(ctx.root, ctx, r.title, false);
                    String part;
                    if (source.bookSourceType == LegadoSource.TYPE_IMAGE) {
                        // 漫画：图片 URL 列表（换行分隔为社区共识；JSON [*] 走列表口径）
                        List<String> imgs = LegadoRuleEvaluator.strList(ctx.root, ctx, r.content);
                        part = String.join("\n", imgs);
                        for (String line : part.split("\n")) {
                            String u = line.trim();
                            if (u.startsWith("http")) {
                                if (sb.length() > 0) sb.append('\n');
                                sb.append(u);
                            }
                        }
                    } else {
                        part = LegadoRuleEvaluator.str(ctx.root, ctx, r.content, false);
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(part.trim());
                    }
                    next = LegadoRuleEvaluator.str(ctx.root, ctx, r.nextContentUrl, true);
                }
                c.text = applyReplaceRegex(sb.toString().trim());
                return c;
            } catch (LegadoException e) {
                throw e;
            } catch (Throwable t) {
                throw new LegadoException(source.tag() + " 正文失败: " + t.getMessage());
            }
            }
    }

    /**
     * 正文清洗（源 ruleContent.replaceRegex，Legado 口径）：逐行一条
     * 「正则##替换」，没有 ## 就是删掉匹配段；按序全篇执行。单条规则坏
     * （正则编译不过/替换串非法）只跳过该条，不杀整章——源规则生态野。
     * 漫画（图片 URL 列表）不走清洗，防正则误伤地址。
     */
    private String applyReplaceRegex(String text) {
        if (source.bookSourceType == LegadoSource.TYPE_IMAGE) return text;
        String rules = source.ruleContent == null ? null : source.ruleContent.replaceRegex;
        if (rules == null || rules.trim().isEmpty()) return text;
        String out = text;
        for (String line : rules.split("\n")) {
            String rule = line.trim();
            if (rule.isEmpty()) continue;
            int sep = rule.indexOf("##");
            String regex = sep >= 0 ? rule.substring(0, sep) : rule;
            String replacement = sep >= 0 ? rule.substring(sep + 2) : "";
            if (regex.isEmpty()) continue;
            try {
                out = java.util.regex.Pattern.compile(regex).matcher(out).replaceAll(replacement);
            } catch (Throwable ignored) {
            }
        }
        return out.trim();
    }

    // ================= 杂项 =================

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String urlEncode(String s) {
        return urlEncode(s, "UTF-8");
    }
}
