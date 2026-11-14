package com.fongmi.android.tv.read;

import android.text.TextUtils;

import com.fongmi.android.tv.api.hk.HkHttp;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkRouter;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.ui.hk.HkEngineManager;
import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 聚阅小说/漫画子源的「续段求值」取数口（契约账
 * juyue-subsource-materials-20261114 §四/§五）：
 * <p>
 * 章节正文/图列不在任何规则的 find_rule 里，只能由章节条目自带的
 * @rule=/@lazyRule= 续段码在宿主规则（聚阅）运行时里现算：码内
 * getObjCode(jkdata,'jx') 取子源 parse、parse['解析'](MY_PARAMS.url)
 * 回一个字符串——宿主契约恒为这一个字符串，非 m3u8 原样透传。
 * MY_PARAMS＝条目 extra 原文（宿主详情链 @rule= 求值同口径：
 * setDetailParams(item.getExtra())），extra.url 即章原址。
 * <p>
 * 得串后按形分发（官阅读面消费口径）：是网址→取页抽正文（小说）/
 * 抽图（漫画）；是 pics:// →走 pics 拆分；是正文本体→直接用。
 * 子源实现经 jkdata 现取现算，本口不依赖本机 jiekou 注册表。
 * <p>
 * 断了会怎样：宿主规则不在/求值失败/回空 → 回 ""/空表，阅读器按
 * 既有失败形显形（正文抓取失败/这一章没有取到图片），不崩。
 */
public class HkJuFetcher {

    private HkJuFetcher() {
    }

    /** 会话章节是否为聚阅续段形（juMode 会话的章一律走本口）。 */
    public static boolean isJuChapter(ReadingSession session, ReadingSession.Chapter ch) {
        return session != null && session.juMode && ch != null;
    }

    /** 小说：续段求值 → 正文（调用方保证在后台线程）。 */
    public static String fetchText(ReadingSession session, ReadingSession.Chapter ch) {
        return novelFromResult(session, evalChapter(session, ch));
    }

    /** 漫画：续段求值 → 图列（调用方保证在后台线程）。 */
    public static List<String> fetchImages(ReadingSession session, ReadingSession.Chapter ch) {
        return imagesFromResult(session, ch, evalChapter(session, ch));
    }

    /**
     * 续段求值本体：以章节自带码在宿主规则运行时里现算，返回契约
     * 字符串。无续段码＝恒等形（模板解析默认返回入参地址），直接回
     * 章原址走分发。求值失败回 ""。
     */
    static String evalChapter(ReadingSession session, ReadingSession.Chapter ch) {
        if (session == null || ch == null) return "";
        String code = ch.juCode == null ? "" : ch.juCode.trim();
        if (code.isEmpty()) return ch.url == null ? "" : ch.url;
        HkRule host = null;
        try {
            host = HkRuleManager.get().getRule(session.ruleTitle);
        } catch (Throwable ignored) {
        }
        if (host == null) {
            SpiderDebug.log("reading", "ju eval no host rule: %s", session.ruleTitle);
            return "";
        }
        com.github.catvod.net.SourceGate.Mark prevMark =
                com.github.catvod.net.SourceGate.push(
                        com.fongmi.android.tv.clash.SourceProxyPolicy.KIND_HK, session.ruleTitle);
        try {
            HkJsRuntime rt = HkEngineManager.runtimeFor(host);
            HkEngineManager.ensureInit(rt);
            rt.setDetailParams(paramsOf(ch));
            String v = rt.evalLazy(code, ch.juPageUrl == null ? "" : ch.juPageUrl);
            if (v == null) return "";
            v = v.trim();
            if (v.isEmpty() || "undefined".equalsIgnoreCase(v) || "null".equalsIgnoreCase(v)) return "";
            if (v.startsWith("error:")) {
                SpiderDebug.log("reading", "ju eval error rule=%s: %s", session.ruleTitle, v);
                return "";
            }
            return v;
        } catch (Throwable t) {
            SpiderDebug.log("reading", "ju eval failed rule=%s: %s",
                    session.ruleTitle, t.getMessage());
            return "";
        } finally {
            com.github.catvod.net.SourceGate.pop(prevMark);
        }
    }

    /** MY_PARAMS 原文：条目 extra 优先；缺失时按章原址最小拼装。 */
    private static String paramsOf(ReadingSession.Chapter ch) {
        if (!TextUtils.isEmpty(ch.juParams)) return ch.juParams;
        try {
            return new org.json.JSONObject()
                    .put("url", ch.url == null ? "" : ch.url).toString();
        } catch (Throwable ignored) {
            return "{}";
        }
    }

    /** 小说分发：网址取页抽正文、正文本体直接用、HTML 本体走抽取。 */
    static String novelFromResult(ReadingSession session, String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.isEmpty()) return "";
        if (s.startsWith("toast://")) {
            SpiderDebug.log("reading", "ju novel toast: %s", s);
            return "";
        }
        if (isHttp(s)) {
            String html = fetchHtml(session, s);
            if (TextUtils.isEmpty(html)) return "";
            return extractArticleText(html);
        }
        if (looksLikeHtml(s)) return extractArticleText(s);
        return s;
    }

    /** 漫画分发：pics:// 走共用拆分；网址取页抽图；图串直拆；HTML 本体抽图。 */
    static List<String> imagesFromResult(ReadingSession session, ReadingSession.Chapter ch, String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        s = s.trim();
        if (s.isEmpty() || s.startsWith("toast://")) return out;
        if (s.regionMatches(true, 0, "pics://", 0, "pics://".length())) {
            for (String p : HkRouter.parsePics(s)) {
                if (isHttp(p)) out.add(p);
            }
            return out;
        }
        if (isHttp(s)) {
            if (ReadingGate.isImageUrl(s)) {
                out.add(s);
                return out;
            }
            String html = fetchHtml(session, s);
            if (!TextUtils.isEmpty(html)) {
                out.addAll(extractImages(html, s));
                // 页地址刷新：后续图组装的 Referer 上下文（与漫画海阔支
                // router.play 后刷新 pageUrl 同口径）
                if (session != null) session.pageUrl = s;
            }
            return out;
        }
        List<String> parts = ReadingGate.splitImages(s);
        boolean allImage = !parts.isEmpty();
        for (String p : parts) {
            if (!ReadingGate.isImageUrl(p)) {
                allImage = false;
                break;
            }
        }
        if (allImage) return parts;
        if (looksLikeHtml(s)) return extractImages(s, ch == null ? "" : ch.url);
        return out;
    }

    /** 取页（宿主规则 UA、节点归属与求值同口）。失败回 ""。 */
    private static String fetchHtml(ReadingSession session, String url) {
        String ua = "";
        try {
            HkRule host = session == null ? null
                    : HkRuleManager.get().getRule(session.ruleTitle);
            if (host != null) ua = host.resolvedUa();
        } catch (Throwable ignored) {
        }
        com.github.catvod.net.SourceGate.Mark prevMark =
                com.github.catvod.net.SourceGate.push(
                        com.fongmi.android.tv.clash.SourceProxyPolicy.KIND_HK,
                        session == null ? "" : session.ruleTitle);
        try {
            String html = HkHttp.get(url, ua);
            return html == null ? "" : html;
        } catch (Throwable t) {
            SpiderDebug.log("reading", "ju fetch failed: %s", t.getMessage());
            return "";
        } finally {
            com.github.catvod.net.SourceGate.pop(prevMark);
        }
    }

    /**
     * 通用正文抽取（官 readTheme 同旨：取页后抽文章正文）：去脚本/样式
     * 后整页转纯文本，块级边界成行、逐行去空。子源站点版式各异，这是
     * 无规则可依时的唯一通用口——抽不到回 ""。
     */
    static String extractArticleText(String html) {
        if (TextUtils.isEmpty(html)) return "";
        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
            doc.select("script,style,iframe,svg,ins,form,nav,header,footer").remove();
            String cleaned = org.jsoup.Jsoup.clean(doc.body().html(), "",
                    org.jsoup.safety.Safelist.none(),
                    new org.jsoup.nodes.Document.OutputSettings().prettyPrint(true));
            String text = org.jsoup.parser.Parser.unescapeEntities(cleaned, true);
            StringBuilder sb = new StringBuilder();
            for (String line : text.split("\n")) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                if (sb.length() > 0) sb.append("\n");
                sb.append(t);
            }
            return sb.toString().trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /** 通用图列抽取：img 的 data-src/data-original/src 按序取、相对址按页址补全、去重。 */
    static List<String> extractImages(String html, String baseUrl) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(html)) return out;
        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(
                    html, baseUrl == null ? "" : baseUrl);
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (org.jsoup.nodes.Element img : doc.select("img")) {
                String v = img.absUrl("data-src");
                if (v.isEmpty()) v = img.absUrl("data-original");
                if (v.isEmpty()) v = img.absUrl("src");
                if (isHttp(v) && seen.add(v)) out.add(v);
            }
            return out;
        } catch (Throwable t) {
            return out;
        }
    }

    private static boolean isHttp(String s) {
        if (TextUtils.isEmpty(s)) return false;
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("http://") || t.startsWith("https://");
    }

    private static boolean looksLikeHtml(String s) {
        if (TextUtils.isEmpty(s)) return false;
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("<") || t.contains("<p>") || t.contains("<br")
                || t.contains("<div") || t.contains("<img");
    }
}
