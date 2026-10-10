package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;

import com.fongmi.quickjs.utils.Parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 海阔选择器引擎（设计文档 §4.3）。
 * <p>
 * 在 quickjs {@link Parser}（已实现 {@code &&} 路径、{@code --} 排除、{@code Text}/{@code Html} 取值、
 * 属性 {@code ||} 兜底）之上，补上海阔方言：
 * <ul>
 * <li>5 段（首页：列表;标题;图片;描述;链接）/ 6 段（搜索：列表;标题;链接;描述;详情;图片）切分，
 * {@code .js:} 代码里的分号不会把段切错</li>
 * <li>{@code &,n} 索引（含负数）→ jsoup {@code :eq(n)}</li>
 * <li>路径级 {@code ||}（如 {@code body&&#app||#app2&&Text}）：逐个候选尝试，首个非空胜出</li>
 * <li>{@code *} 占位：该段跳过</li>
 * <li>{@code .js:} 值加工：把取值结果作为 {@code input} 交给 {@link JsEvaluator}；
 * M1 未接 JS 运行时，默认原样透传（M2 接入 HkJsRuntime）</li>
 * </ul>
 */
public class HkSelector {

    /** M2 接入真正的 JS 求值；M1 为 null 时 .js: 段原样返回 input。 */
    public interface JsEvaluator {
        String eval(String js, String input);
    }

    private final Parser parser = new Parser();
    private JsEvaluator jsEvaluator;

    public void setJsEvaluator(JsEvaluator jsEvaluator) {
        this.jsEvaluator = jsEvaluator;
    }

    public static boolean isJsRule(String rule) {
        return rule != null && rule.trim().startsWith("js:");
    }

    /**
     * 冲突字符解码（官方 StringUtil.decodeConflictStr）：
     * {@code ？？}→{@code ?}、{@code ＆＆}→{@code &}、{@code ；；}→{@code ;}、{@code ，，}→{@code ,}。
     * 作用于所有选择器规则文本（必须在 && 切分之前做，否则全角 ＆＆ 会被误切）。
     * 注意：只对选择器 head 部分解码，.js: 代码原样保留。
     */
    public static String decodeConflict(String s) {
        if (s == null) return "";
        return s.replace("？？", "?").replace("＆＆", "&").replace("；；", ";").replace("，，", ",");
    }

    /**
     * 解析首页列表：find_rule 5 段 = 列表;标题;图片;描述;链接。
     * js: 整段规则需 M2，本方法直接返回空列表。
     */
    public List<HkItem> parseList(String html, String findRule, String baseUrl) {
        List<HkItem> result = new ArrayList<>();
        if (html == null || findRule == null || isJsRule(findRule)) return result;
        String[] seg = splitSegments(findRule, 5);
        List<String> items = evalList(html, seg[0]);
        for (String itemHtml : items) {
            HkItem it = new HkItem();
            it.setTitle(evalField(itemHtml, seg[1], baseUrl));
            it.setPic(evalField(itemHtml, seg[2], baseUrl));
            it.setDesc(evalField(itemHtml, seg[3], baseUrl));
            it.setUrl(evalField(itemHtml, seg[4], baseUrl));
            if (!it.getTitle().isEmpty() || !it.getUrl().isEmpty()) result.add(it);
        }
        return result;
    }

    /**
     * 解析搜索列表：searchFind 6 段 = 列表;标题;链接;描述;详情;图片。
     * 第 5 段"详情"无处可放，内容并入 desc（M1 约定）。
     */
    public List<HkItem> parseSearch(String html, String searchFind, String baseUrl) {
        List<HkItem> result = new ArrayList<>();
        if (html == null || searchFind == null || isJsRule(searchFind)) return result;
        String[] seg = splitSegments(searchFind, 6);
        List<String> items = evalList(html, seg[0]);
        for (String itemHtml : items) {
            HkItem it = new HkItem();
            it.setTitle(evalField(itemHtml, seg[1], baseUrl));
            it.setUrl(evalField(itemHtml, seg[2], baseUrl));
            String desc = evalField(itemHtml, seg[3], baseUrl);
            String detail = evalField(itemHtml, seg[4], baseUrl);
            if (!detail.isEmpty()) desc = desc.isEmpty() ? detail : desc + " " + detail;
            it.setDesc(desc);
            it.setPic(evalField(itemHtml, seg[5], baseUrl));
            if (!it.getTitle().isEmpty() || !it.getUrl().isEmpty()) result.add(it);
        }
        return result;
    }

    /**
     * 解析详情页：detail_find_rule 6 段 = 列表;标题;图片;描述;链接;样式。
     * M3 约定：标题段为空时回退取首个 a 的文本；链接段为空/* 时回退取 a&&href。
     * 每条解析结果为一个选集候选，线路分组由 HkRouter 按 line 字段完成。
     */
    public List<HkDetailItem> parseDetail(String html, String detailRule, String baseUrl) {
        List<HkDetailItem> result = new ArrayList<>();
        if (html == null || detailRule == null || isJsRule(detailRule)) return result;
        String trimmed = detailRule.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed)) return result;
        String[] seg = splitSegments(detailRule, 6);
        List<String> items = evalList(html, seg[0]);
        for (String itemHtml : items) {
            HkDetailItem it = new HkDetailItem();
            String title = evalField(itemHtml, seg[1], baseUrl);
            if (title.isEmpty()) title = evalField(itemHtml, "a&&Text", baseUrl);
            if (title.isEmpty()) title = evalField(itemHtml, "Text", baseUrl);
            it.setTitle(title);
            it.setPic(evalField(itemHtml, seg[2], baseUrl));
            it.setDesc(evalField(itemHtml, seg[3], baseUrl));
            String url = evalField(itemHtml, seg[4], baseUrl);
            if (url.isEmpty()) url = evalField(itemHtml, "a&&href", baseUrl);
            it.setUrl(url);
            it.setColType(evalField(itemHtml, seg[5], baseUrl));
            if (!it.getTitle().isEmpty() || !it.getUrl().isEmpty()) result.add(it);
        }
        return result;
    }

    /**
     * 对单个条目 HTML 求一个字段规则的值（M3 详情页复用）。
     * 支持 * 占位、|| 候选、&,n 索引、.js: 后加工。
     */
    public String evalField(String itemHtml, String fieldRule, String baseUrl) {
        if (itemHtml == null || fieldRule == null) return "";
        fieldRule = fieldRule.trim();
        if (fieldRule.isEmpty() || "*".equals(fieldRule)) return "";
        String js = null;
        String head = fieldRule;
        int jsIdx = fieldRule.indexOf(".js:");
        if (jsIdx >= 0) {
            head = fieldRule.substring(0, jsIdx);
            js = fieldRule.substring(jsIdx + 4);
        }
        // 选择器 head 先做冲突字符解码（&& 切分之前）
        head = decodeConflict(head);
        String input = evalHead(itemHtml, head, baseUrl);
        if (js != null) {
            // P1：官方多段链——取值规则以 .js: 结尾时用中文 ＋ 切分多段依次加工
            input = evalJsChain(js, input);
        }
        return input == null ? "" : input;
    }

    /** .js: 多段链求值：中文 ＋ 分隔的多段 JS 依次加工（单段时与原来一致）。 */
    private String evalJsChain(String js, String input) {
        if (jsEvaluator == null) return input == null ? "" : input;
        String cur = input == null ? "" : input;
        // 注意：＋ 在 JS 字符串字面量里也可能出现，但官方语义就是按 ＋ 切分
        String[] segs = js.split("＋", -1);
        for (String seg : segs) {
            cur = jsEvaluator.eval(seg, cur);
            if (cur == null) cur = "";
        }
        return cur;
    }

    /** head 为空→""；含 &&→选择器；否则先按选择器试，空则按字面量。 */
    private String evalHead(String itemHtml, String head, String baseUrl) {
        if (head == null || head.isEmpty()) return "";
        if (head.contains("&&")) return evalCandidates(itemHtml, head, baseUrl);
        String v = evalCandidates(itemHtml, head, baseUrl);
        return v.isEmpty() ? head : v;
    }

    private String evalCandidates(String itemHtml, String rule, String baseUrl) {
        for (String candidate : expandOr(rule)) {
            String v = parser.pdfh(itemHtml, convertIndex(candidate), baseUrl);
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }

    private List<String> evalList(String html, String listRule) {
        if (listRule == null || listRule.trim().isEmpty() || "*".equals(listRule.trim())) {
            return Collections.emptyList();
        }
        String js = null;
        String head = listRule;
        int jsIdx = listRule.indexOf(".js:");
        if (jsIdx >= 0) {
            head = listRule.substring(0, jsIdx);
            js = listRule.substring(jsIdx + 4);
        }
        // 选择器 head 先做冲突字符解码（&& 切分之前）
        head = decodeConflict(head);
        List<String> items = Collections.emptyList();
        for (String candidate : expandOr(head)) {
            items = parser.pdfa(html, convertIndex(candidate));
            if (items != null && !items.isEmpty()) break;
        }
        if (items == null) items = Collections.emptyList();
        if (js != null && jsEvaluator != null && !items.isEmpty()) {
            List<String> out = new ArrayList<>(items.size());
            // P1：多段链——中文 ＋ 切分多段依次加工
            String[] segs = js.split("＋", -1);
            for (String it : items) {
                String cur = it;
                for (String seg : segs) {
                    cur = jsEvaluator.eval(seg, cur);
                    if (cur == null) cur = "";
                }
                out.add(cur);
            }
            return out;
        }
        return items;
    }

    /**
     * 路径级 || 展开：含 || 的 && 步骤拆成多候选，做笛卡尔展开。
     * 例：body&&#app||#app2&&Text → [body&&#app&&Text, body&&#app2&&Text]
     */
    private List<String> expandOr(String rule) {
        List<List<String>> stepOptions = new ArrayList<>();
        for (String step : rule.split("&&", -1)) {
            List<String> opts = new ArrayList<>();
            Collections.addAll(opts, step.split("\\|\\|", -1));
            stepOptions.add(opts);
        }
        List<String> out = new ArrayList<>();
        out.add("");
        for (List<String> opts : stepOptions) {
            List<String> next = new ArrayList<>();
            for (String prefix : out) {
                for (String o : opts) next.add(prefix.isEmpty() ? o : prefix + "&&" + o);
            }
            out = next;
        }
        return out;
    }

    /**
     * 海阔 {@code &,n} 索引 → jsoup {@code :eq(n)}（Parser 已支持负数）；
     * {@code &,m:n} 范围索引 → {@code :gt(m-1):lt(n+1)}（m=0 时为 {@code :lt(n+1)}）。
     */
    private static final Pattern RANGE_INDEX = Pattern.compile(",(\\d+):(\\d+)$");

    private String convertIndex(String rule) {
        String[] steps = rule.split("&&", -1);
        for (int i = 0; i < steps.length; i++) {
            String s = steps[i];
            Matcher rm = RANGE_INDEX.matcher(s);
            if (rm.find()) {
                int from = Integer.parseInt(rm.group(1));
                int to = Integer.parseInt(rm.group(2));
                String rep = from <= 0 ? ":lt(" + (to + 1) + ")" : ":gt(" + (from - 1) + "):lt(" + (to + 1) + ")";
                s = s.substring(0, rm.start()) + rep;
            } else {
                s = s.replaceAll(",(-?\\d+)$", ":eq($1)");
            }
            steps[i] = s;
        }
        return TextUtils.join("&&", steps);
    }

    /**
     * 按 ; 切段，容忍 .js: 代码里的分号：
     * 含 .js: 的段先按"多出段数"合并；若合并后 JS 引号/括号仍不平衡，
     * 继续吞入后续段，直到平衡或段用完。不足 expected 时补 *。
     */
    private String[] splitSegments(String rule, int expected) {
        String[] parts = rule.split(";", -1);
        List<String> seg = new ArrayList<>();
        int i = 0;
        while (seg.size() < expected && i < parts.length) {
            if (parts[i].contains(".js:")) {
                int partsAfter = parts.length - i - 1;
                int slotsAfter = expected - seg.size() - 1;
                int take = 1 + Math.max(0, partsAfter - slotsAfter);
                while (take < parts.length - i && isJsUnbalanced(joinParts(parts, i, take))) {
                    take++;
                }
                seg.add(joinParts(parts, i, take));
                i += take;
            } else {
                seg.add(parts[i]);
                i++;
            }
        }
        while (seg.size() < expected) seg.add("*");
        return seg.toArray(new String[0]);
    }

    private String joinParts(String[] parts, int from, int count) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < count; k++) {
            if (k > 0) sb.append(';');
            sb.append(parts[from + k]);
        }
        return sb.toString();
    }

    /** 检查 .js: 代码的引号/括号是否不平衡（是则说明分号切断了 JS）。 */
    private boolean isJsUnbalanced(String segmentWithJs) {
        int jsIdx = segmentWithJs.indexOf(".js:");
        String js = jsIdx >= 0 ? segmentWithJs.substring(jsIdx + 4) : segmentWithJs;
        int single = 0, dbl = 0, paren = 0, square = 0, brace = 0;
        for (int i = 0; i < js.length(); i++) {
            char c = js.charAt(i);
            if (c == '\'') single++;
            else if (c == '"') dbl++;
            else if (c == '(') paren++;
            else if (c == ')') paren--;
            else if (c == '[') square++;
            else if (c == ']') square--;
            else if (c == '{') brace++;
            else if (c == '}') brace--;
        }
        return (single % 2 != 0) || (dbl % 2 != 0) || paren != 0 || square != 0 || brace != 0;
    }
}
