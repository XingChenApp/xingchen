package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 直接移植自海阔 8.83 {@code com.example.hikerview.service.parser.CommonParser}（do not rewrite）。
 * <p>
 * 仅移植 HkSearchParser / HkLazyRuleParser 用到的选择器方法。
 * 适配：MovieRule → {@link HkRule}；JSEngine → {@link JsEval}（宿主注入）；
 * Timber → Log；StringUtil → {@link HkParserKit}。
 */
public class HkCommonParser {
    private static final String TAG = "HkCommonParser";

    private static final String[] NORMAL_ATTRS = {"href", "src", "class", "title", "alt"};

    /** JS 求值钩子（对应 8.83 JSEngine.getInstance().evalJS，按当前规则的运行时求值）。 */
    public interface JsEval {
        String evalJs(HkRule rule, String js, String input);
    }

    private static volatile JsEval jsEval;

    public static void setJsEval(JsEval eval) {
        jsEval = eval;
    }

    private static void prettyPrint(Element element, boolean pretty) {
        if (element == null || element.ownerDocument() == null) {
            return;
        }
        element.ownerDocument().outputSettings().prettyPrint(pretty);
    }

    public static Element getTrueElement(String rule, Element element) {
        Element result;
        if (rule.startsWith("Text") || rule.startsWith("Attr")) {
            return element;
        }
        for (String attr : NORMAL_ATTRS) {
            if (attr.equals(rule)) {
                return element;
            }
        }
        String[] minusSplit = rule.split("--");
        if (minusSplit.length > 1) {
            prettyPrint(element, false);
            Element doc = getTrueElement(minusSplit[0], element);
            String html = doc.outerHtml();
            for (int i = 1; i < minusSplit.length; i++) {
                html = html.replace(getTrueElement(minusSplit[i], doc).outerHtml(), "");
                doc = Jsoup.parse(html);
                prettyPrint(doc, false);
            }
            prettyPrint(element, true);
            prettyPrint(doc, true);
            return doc;
        }
        String[] orSplit = rule.split("\\|\\|");
        if (orSplit.length > 1) {
            for (String r : orSplit) {
                try {
                    result = getTrueElement(r, element);
                } catch (Exception e) {
                    e.printStackTrace();
                    result = null;
                }
                if (result != null) {
                    return result;
                }
            }
        }
        String[] commaSplit = rule.split(",");
        if (commaSplit.length > 1) {
            // G39：8.83 CommonParser.getTrueElement 单索引路原形
            // （smali 653-921）：parseInt 无 trim 无 catch、
            // selected.get(resolved) 无越界防卫——索引非法/越界
            // 照官抛异常，由 || 段级 catch（本法上方）或调用方
            // 整链 catch 吞成该段无结果、保其余段。此前咱家
            // catch 后回 null＋越界回 null，多段场景下整调用
            // 提前以 null 收场、其余段拿不到，与官分叉。
            int index = Integer.parseInt(commaSplit[1]);
            Elements selected = element.select(HkParserKit.decodeConflictStr(commaSplit[0]));
            int resolved = index < 0 ? selected.size() + index : index;
            return selected.get(resolved);
        }
        return element.select(HkParserKit.decodeConflictStr(rule)).first();
    }

    public static Elements selectElements(Element element, String rule) {
        String[] orSplit = rule.split("\\|\\|");
        Elements elements = new Elements();
        for (String r : orSplit) {
            try {
                elements.addAll(selectElementsWithoutOr(element, r));
            } catch (Exception e) {
                // || 并集的段级隔离不变（其余段照常入集），但失败段必须
                // 带段原文留痕，与「该段本就无匹配」可区分（批 residual）。
                Log.e(TAG, "selectElements segment failed: " + r + " (in rule: " + rule + ")", e);
            }
        }
        return elements;
    }

    /**
     * 8.83 原文（CommonParser.smali，jadx 未能反编译，按 smali 逐分支移植）：
     * 规则形如 {@code selector} 或 {@code selector,start:end}，end 可为负（从末尾计数）。
     */
    private static Elements selectElementsWithoutOr(Element element, String rule) {
        String[] commaSplit = rule.split(",");
        if (commaSplit.length > 1) {
            String[] range = commaSplit[1].split(":", -1);
            int start = 0;
            int end = 0;
            if (!TextUtils.isEmpty(range[0])) {
                try {
                    start = Integer.parseInt(range[0]);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "selectElementsWithoutOr", e);
                }
            }
            // 8.83 原文此处无条件取 range[1]（CommonParser.smali selectElementsWithoutOr）：
            // 单 ",n"（无冒号）时数组越界直接抛出，由 selectElements 的 catch 吞掉、
            // 该 || 候选整段落空。此前转写加了 length 防卫，会误返 start..末尾 全段，第④块改回。
            String endStr = range[1];
            if (!TextUtils.isEmpty(endStr)) {
                try {
                    end = Integer.parseInt(endStr);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "selectElementsWithoutOr", e);
                }
            }
            Elements selected = element.select(HkParserKit.decodeConflictStr(commaSplit[0]));
            if (end > selected.size()) {
                end = selected.size();
            }
            if (end <= 0) {
                end = end + selected.size();
            }
            Elements result = new Elements();
            for (int i = start; i < end && i < selected.size(); i++) {
                result.add(selected.get(i));
            }
            return result;
        }
        return element.select(HkParserKit.decodeConflictStr(rule));
    }

    public static String getUrlByRule(String baseUrl, HkRule rule, Element element, String ruleStr) {
        if ("*".equals(ruleStr)) {
            return baseUrl;
        }
        String[] chain = ruleStr.split("&&");
        if (chain.length != 1) {
            element = getTrueElement(chain[0], element);
        }
        for (int i = 1; i < chain.length - 1; i++) {
            element = getTrueElement(chain[i], element);
        }
        return getUrl(element, chain[chain.length - 1], rule, rule.getChapterUrl());
    }

    public static String getTextByRule(Element element, String rule) {
        if (HkParserKit.isEmpty(rule) || "*".equals(rule)) {
            return "";
        }
        if (rule.contains(".js:")) {
            return getAndTextByRule(element, rule, "＋");
        }
        if (rule.contains("＋")) {
            return getAndTextByRule(element, rule, "＋");
        }
        return getAndTextByRule(element, rule, "\\+");
    }

    private static String getAndTextByRule(Element element, String rule, String sep) {
        String[] parts = rule.split(sep);
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            String trim = part.trim();
            if ((trim.startsWith("'") && trim.endsWith("'")) || (trim.startsWith("\"") && trim.endsWith("\""))) {
                out.add(trim.substring(1, trim.length() - 1).replace("\\n", "\n"));
            } else {
                out.add(getContentWithoutAnd(element, trim));
            }
        }
        return HkParserKit.listToString(out, "");
    }

    private static String getContentWithoutAnd(Element element, String rule) {
        String[] chain = rule.split("&&");
        if (chain.length != 1) {
            element = getTrueElement(chain[0], element);
        }
        for (int i = 1; i < chain.length - 1; i++) {
            element = getTrueElement(chain[i], element);
        }
        return getText(element, chain[chain.length - 1]);
    }

    public static String getText(Element element, String rule) {
        if ("*".equals(rule)) {
            return "null";
        }
        String[] orSplit = rule.split("\\|\\|");
        if (orSplit.length > 1) {
            for (String r : orSplit) {
                String text = null;
                try {
                    text = getTextWithoutOr(element, r);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!TextUtils.isEmpty(text)) {
                    return text;
                }
            }
        }
        return getTextWithoutOr(element, rule);
    }

    private static String getTextWithoutOr(Element element, String rule) {
        if (element == null) return "";
        String js;
        String[] jsSplit = rule.split("\\.js:");
        if (jsSplit.length > 1) {
            rule = jsSplit[0];
            js = HkParserKit.arrayToString(jsSplit, 1, jsSplit.length, ".js:");
        } else {
            js = "";
        }
        String attr;
        String[] bangSplit = rule.split("!");
        if (bangSplit.length > 1) {
            if ("Text".equals(bangSplit[0])) {
                attr = element.text();
            } else if ("Html".equals(bangSplit[0])) {
                attr = element.html();
            } else if (bangSplit[0].contains("Attr")) {
                attr = element.attr(bangSplit[0].replace("Attr", ""));
            } else {
                attr = element.attr(HkParserKit.decodeConflictStr(bangSplit[0]));
            }
            if (!"Html".equals(rule)) {
                attr = attr.replaceAll("\\n", " ");
            }
            for (int i = 1; i < bangSplit.length; i++) {
                attr = attr.replace(bangSplit[i], "");
            }
        } else {
            if ("Text".equals(rule)) {
                attr = element.text();
            } else if ("Html".equals(rule)) {
                attr = element.html();
            } else if (rule.contains("Attr")) {
                attr = element.attr(rule.replace("Attr", ""));
            } else {
                attr = element.attr(HkParserKit.decodeConflictStr(rule));
            }
            if (!"Html".equals(rule)) {
                attr = attr.replaceAll("\\n", " ");
            }
        }
        if (!HkParserKit.isNotEmpty(js)) {
            return attr;
        }
        try {
            JsEval eval = jsEval;
            String evaluated = eval != null ? eval.evalJs(null, js, attr) : attr;
            return evaluated != null ? evaluated : attr;
        } catch (Exception unused) {
            return attr;
        }
    }

    public static String getUrl(Element element, String rule, HkRule hkRule, String baseUrl) {
        if ("*".equals(rule)) {
            return "null";
        }
        String[] orSplit = rule.split("\\|\\|");
        if (orSplit.length > 1) {
            for (String r : orSplit) {
                String url = null;
                try {
                    url = getUrlWithoutOr(element, r, hkRule, baseUrl);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!TextUtils.isEmpty(url)) {
                    return url;
                }
            }
        }
        return getUrlWithoutOr(element, rule, hkRule, baseUrl);
    }

    private static String getUrlWithoutOr(Element element, String rule, HkRule hkRule, String baseUrl) {
        String js;
        String[] jsSplit = rule.split("\\.js:");
        if (jsSplit.length > 1) {
            rule = jsSplit[0];
            js = HkParserKit.arrayToString(jsSplit, 1, jsSplit.length, ".js:");
        } else {
            js = "";
        }
        if (element == null) {
            return "";
        }
        String attr;
        if (rule.startsWith("Text")) {
            attr = element.text();
        } else if ("Html".equals(rule)) {
            attr = element.html();
        } else {
            if (rule.startsWith("AttrNo")) {
                String base = hkRule != null && hkRule.getBaseUrl() != null ? hkRule.getBaseUrl() : "";
                return base + element.attr(rule.replaceFirst("AttrNo", ""));
            }
            if (rule.startsWith("AttrYes")) {
                attr = element.attr(rule.replaceFirst("AttrYes", ""));
            } else if (rule.startsWith("Attr")) {
                attr = element.attr(rule.replaceFirst("Attr", ""));
            } else {
                attr = element.attr(HkParserKit.decodeConflictStr(rule));
            }
        }
        if (TextUtils.isEmpty(js)) {
            if (!"Html".equals(rule)) {
                attr = HkParserKit.trimBlanks(attr);
            }
        } else {
            try {
                JsEval eval = jsEval;
                if (eval != null) {
                    attr = eval.evalJs(hkRule, js, attr);
                }
                if (attr.toLowerCase().trim().startsWith("http") || attr.toLowerCase().trim().startsWith("x5://")) {
                    return attr.trim();
                }
            } catch (Exception e) {
                if (!"Html".equals(rule)) {
                    attr = HkParserKit.trimBlanks(attr);
                }
                Log.e(TAG, "getUrlWithoutOr", e);
            }
        }
        if (HkParserKit.isEmpty(attr)) {
            return "";
        }
        if ("Html".equals(rule) || attr.startsWith("http") || attr.startsWith("hiker")) {
            return attr;
        }
        if (attr.startsWith("//")) {
            return joinUrl(baseUrl, attr);
        }
        if (attr.startsWith("magnet") || attr.startsWith("thunder") || attr.startsWith("ftp") || attr.startsWith("ed2k")) {
            return attr;
        }
        if (attr.startsWith("/") || attr.startsWith("./") || attr.startsWith("../") || attr.startsWith("?")) {
            return joinUrl(baseUrl, attr);
        }
        String[] dollarSplit = attr.split("\\$");
        if (dollarSplit.length > 1 && dollarSplit[1].startsWith("http")) {
            return dollarSplit[1];
        }
        if (attr.contains("url(")) {
            String[] urlSplit = attr.split("url\\(");
            if (urlSplit.length > 1 && urlSplit[1].startsWith("http")) {
                return urlSplit[1].split("\\)")[0];
            }
        }
        return joinUrl(baseUrl, attr);
    }

    /**
     * 照抄 8.83 CommonParser.parsePageClassUrl(url, page, rule, options)：
     * 四组 url 为 null 时回填 ""（原版会改规则本体）；options 非空时灌 MY_*_NAME/URL；
     * [firstPage=...] 仅 page==1 时生效；fypage@ 算式以 page 为初值顺序运算；
     * fyAll 按 firstHeader 取一组、否则分别替换 fyclass/fyyear/fyarea/fysort；";" 后段原样拼回。
     */
    public static String parsePageClassUrl(String url, int page, HkRule rule, java.util.Map<String, Object> options) {
        if (url == null || url.length() == 0) return url;
        if (rule == null) rule = new HkRule();
        if (rule.getClassUrl() == null) rule.setClassUrl("");
        if (rule.getYearUrl() == null) rule.setYearUrl("");
        if (rule.getAreaUrl() == null) rule.setAreaUrl("");
        if (rule.getSortUrl() == null) rule.setSortUrl("");
        if (options != null) {
            if (rule.getClassUrl().length() > 0) {
                options.put("MY_CLASS_NAME", rule.getClassName());
                options.put("MY_CLASS_URL", rule.getClassUrl());
            }
            if (rule.getYearUrl().length() > 0) {
                options.put("MY_YEAR_NAME", rule.getYearName());
                options.put("MY_YEAR_URL", rule.getYearUrl());
            }
            if (rule.getAreaUrl().length() > 0) {
                options.put("MY_AREA_NAME", rule.getAreaName());
                options.put("MY_AREA_URL", rule.getAreaUrl());
            }
            if (rule.getSortUrl().length() > 0) {
                options.put("MY_SORT_NAME", rule.getSortName());
                options.put("MY_SORT_URL", rule.getSortUrl());
            }
        }
        String[] urlParts = url.split(";");
        String[] fp = urlParts[0].split("\\[firstPage=");
        String result;
        if (page == 1 && fp.length > 1) {
            result = fp[1].split("]")[0];
        } else if (fp[0].contains("fypage@")) {
            String[] parts = fp[0].split("fypage@");
            String[] expr = parts[1].split("@");
            int p = page;
            for (int i = 0; i < expr.length - 1; i++) {
                String t = expr[i];
                // 规则作者文本裸 parseInt 兜底（同文件 selectElementsWithoutOr 口径）：
                // 解析失败记日志并跳过这一项运算，p 保持原值，不让 NumberFormatException 冒出本方法；
                // 除数为 0 同样跳过，避免 ArithmeticException。
                try {
                    if (t.startsWith("-")) {
                        p -= Integer.parseInt(t.replace("-", ""));
                    } else if (t.startsWith("+")) {
                        p += Integer.parseInt(t.replace("+", ""));
                    } else if (t.startsWith("*")) {
                        p *= Integer.parseInt(t.replace("*", ""));
                    } else if (t.startsWith("/")) {
                        int divisor = Integer.parseInt(t.replace("/", ""));
                        if (divisor != 0) p /= divisor;
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "parsePageClassUrl", e);
                }
            }
            result = parts[0] + p + expr[expr.length - 1];
        } else {
            result = fp[0].replace("fypage", String.valueOf(page));
        }
        if (result.contains("fyAll")) {
            String fh = rule.getFirstHeader();
            if ("class".equals(fh)) {
                result = result.replace("fyAll", rule.getClassUrl());
            } else if ("area".equals(fh)) {
                result = result.replace("fyAll", rule.getAreaUrl());
            } else if ("year".equals(fh)) {
                result = result.replace("fyAll", rule.getYearUrl());
            } else if ("sort".equals(fh)) {
                result = result.replace("fyAll", rule.getSortUrl());
            } else {
                result = result.replace("fyAll", rule.getClassUrl());
            }
        } else {
            result = result.replace("fyclass", rule.getClassUrl());
            result = result.replace("fyyear", rule.getYearUrl());
            result = result.replace("fyarea", rule.getAreaUrl());
            result = result.replace("fysort", rule.getSortUrl());
        }
        StringBuilder sb = new StringBuilder(result);
        for (int i = 1; i < urlParts.length; i++) {
            sb.append(";").append(urlParts[i]);
        }
        return sb.toString();
    }

    public static String joinUrl(String baseUrl, String relative) {
        if (HkParserKit.isEmpty(baseUrl)) {
            return relative;
        }
        try {
            return new URL(new URL(baseUrl), relative).toExternalForm();
        } catch (MalformedURLException e) {
            Log.e(TAG, "joinUrl", e);
            return baseUrl;
        }
    }
}
