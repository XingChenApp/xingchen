package com.fongmi.android.tv.ui.hk;

import com.fongmi.android.tv.api.hk.HkCommonParser;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkSearchEngine;
import com.fongmi.android.tv.api.hk.SearchResult;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import android.util.Log;

/**
 * 照 8.83 SearchParser（service/parser/SearchParser.smali，666 行）转写：
 * findList 先 engine.toMovieRule() 并 setChapterUrl(url)；findRule 空 → showErr("搜索解析规则不能为空")；
 * 隐私规则命中 → findRule 改写 "js: setResult([]);"（钩子默认不命中，星辰无隐私子系统）；
 * js: 路 → options{MY_PAGE, MY_KEYWORD} 走 JsEngineBridge.parseCallBack 等价钩子；
 * HTML 路 → findRule 按 ";" 切段（[0]=链、[1]=标题、[2]=url、[3]=descMore、[4]=content、[5]=图），
 * 逐条组装 SearchResult（desc = engine.getDisplayTitle()、type = "video"），
 * developerMode 时单字段失败置 ""，否则整条跳过；整体异常 → showErr("findList，msg：" + e)。
 */
public final class HkSearchParser {
    private static final String TAG = "HkSearchParser";

    /** 对应 SearchJsCallBack（showData/showErr/onUpdate）。 */
    public interface SearchCallback {
        void showData(List<SearchResult> list);

        void showErr(String msg);

        default void onUpdate(String key, String value) {
        }
    }

    /** 对应 JsEngineBridge.parseCallBack(url, engine, html, options, callback) 的 JS 路接缝（桥绑定运行时）。 */
    public interface JsSearchParser {
        List<SearchResult> parseSearch(String url, HkSearchEngine engine, String html,
                                       Map<String, Object> options) throws Exception;
    }

    /** 对应 SettingConfig.developerMode（与 HkHomeParser 同一开关，桥绑定时置位）。 */
    public static volatile boolean developerMode;

    public static volatile JsSearchParser jsSearchParser;

    /** 对应 JSEngine.log（整体异常时记 (msg, rule)）。 */
    public static volatile BiConsumer<String, HkRule> errorLogger;

    /** 对应 SettingConfig.shouldReplacePrivacyRule（星辰无此子系统，默认不命中）。 */
    public static volatile Predicate<String> privacyReplacer;

    private HkSearchParser() {
    }

    private static synchronized void callBackError(SearchCallback callback, String msg) {
        if (callback != null) callback.showErr(msg);
    }

    private static synchronized void callBackSuccess(SearchCallback callback, List<SearchResult> list) {
        if (callback != null) callback.showData(list);
    }

    public static void findList(String url, String keyword, HkSearchEngine engine, String html,
                                int page, SearchCallback callback) {
        HkRule rule = engine.toRule();
        rule.setChapterUrl(url);
        if (HkStringUtil.isEmpty(engine.getFindRule())) {
            callBackError(callback, "搜索解析规则不能为空");
            return;
        }
        Predicate<String> privacy = privacyReplacer;
        if (privacy != null && privacy.test(engine.getTitle())) {
            engine.setFindRule("js: setResult([]);");
        }
        if (engine.getFindRule().startsWith("js:")) {
            Map<String, Object> options = new HashMap<>();
            options.put("MY_PAGE", page);
            options.put("MY_KEYWORD", keyword);
            // 环 25-1：把 SearchCallback.onUpdate 递进桥层（运行时 onUpdate 真回调
            // 经它绑回搜索页）；回调为 null 时不放，桥层见无此键即不置监听。
            if (callback != null) {
                options.put("ON_UPDATE", (BiConsumer<String, String>) callback::onUpdate);
            }
            JsSearchParser parser = jsSearchParser;
            if (parser == null) {
                callBackError(callback, "js engine not bound");
                return;
            }
            try {
                callBackSuccess(callback, parser.parseSearch(url, engine, html, options));
            } catch (Exception e) {
                Log.e(TAG, "hk error", e);
                callBackError(callback, e.getMessage() != null ? e.getMessage() : e.toString());
            }
            return;
        }
        List<SearchResult> list = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(html);
            String[] ruleParts = engine.getFindRule().split(";");
            String[] chain = ruleParts[0].split("&&");
            Element scope = HkCommonParser.getTrueElement(chain[0], doc);
            for (int i = 1; i < chain.length - 1; i++) {
                scope = HkCommonParser.getTrueElement(chain[i], scope);
            }
            Elements elements = HkCommonParser.selectElements(scope, chain[chain.length - 1]);
            int itemFailed = 0;
            Exception itemLastError = null;
            for (Element el : elements) {
                SearchResult item = new SearchResult();
                try {
                    if (developerMode) {
                        try {
                            item.setTitle(HkCommonParser.getTextByRule(el, ruleParts[1]));
                        } catch (Exception e) {
                            item.setTitle("");
                        }
                    } else {
                        item.setTitle(HkCommonParser.getTextByRule(el, ruleParts[1]));
                    }
                    if (developerMode) {
                        try {
                            getUrl(item, url, rule, el, ruleParts);
                        } catch (Exception e) {
                            item.setUrl("");
                        }
                    } else {
                        getUrl(item, url, rule, el, ruleParts);
                    }
                    if (developerMode) {
                        try {
                            getDesc(item, url, rule, el, ruleParts);
                        } catch (Exception e) {
                            item.setDescMore("");
                        }
                    } else {
                        getDesc(item, url, rule, el, ruleParts);
                    }
                    if (developerMode) {
                        try {
                            getContent(item, url, rule, el, ruleParts);
                        } catch (Exception e) {
                            item.setContent("");
                        }
                    } else {
                        getContent(item, url, rule, el, ruleParts);
                    }
                    if (developerMode) {
                        try {
                            getPicUrl(item, url, rule, el, ruleParts);
                        } catch (Exception e) {
                            item.setImg("");
                        }
                    } else {
                        getPicUrl(item, url, rule, el, ruleParts);
                    }
                    item.setDesc(engine.getDisplayTitle());
                    item.setType("video");
                    list.add(item);
                } catch (Exception e) {
                    itemFailed++;
                    itemLastError = e;
                    Log.e(TAG, "搜索结果单条解析失败（第 " + (list.size() + itemFailed) + " 条）", e);
                }
            }
            if (itemFailed > 0) {
                // 单条失败不再静默消失（残项处置 2026-11-14）：部分失败经既有
                // 引擎日志通道报数；全部失败走既有 showErr 错误回调，不发空成功表。
                BiConsumer<String, HkRule> logger = errorLogger;
                if (logger != null) logger.accept(
                        "findList 单条解析失败 " + itemFailed + "/" + elements.size(), rule);
                if (list.isEmpty()) {
                    callBackError(callback, "搜索结果解析失败：" + itemLastError);
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
            callBackError(callback, "findList，msg：" + e.toString());
            BiConsumer<String, HkRule> logger = errorLogger;
            if (logger != null) logger.accept(e.getMessage(), rule);
            return;
        }
        callBackSuccess(callback, list);
    }

    /** 照原版 getUrl：parts[2] → getUrlByRule，无长度/星号守卫（异常由调用方 try 承担）。 */
    private static void getUrl(SearchResult result, String pageUrl, HkRule rule, Element el, String[] ruleParts) {
        result.setUrl(HkCommonParser.getUrlByRule(pageUrl, rule, el, ruleParts[2]));
    }

    /** 照原版 getDesc：parts[3] 非空且非 "*" → descMore = getTextByRule。 */
    private static void getDesc(SearchResult result, String pageUrl, HkRule rule, Element el, String[] ruleParts) {
        if (ruleParts.length > 3 && HkStringUtil.isNotEmpty(ruleParts[3]) && !"*".equals(ruleParts[3])) {
            result.setDescMore(HkCommonParser.getTextByRule(el, ruleParts[3]));
        }
    }

    /** 照原版 getContent：parts[4] 非空且非 "*" → content = getTextByRule。 */
    private static void getContent(SearchResult result, String pageUrl, HkRule rule, Element el, String[] ruleParts) {
        if (ruleParts.length > 4 && HkStringUtil.isNotEmpty(ruleParts[4]) && !"*".equals(ruleParts[4])) {
            result.setContent(HkCommonParser.getTextByRule(el, ruleParts[4]));
        }
    }

    /** 照原版 getPicUrl：parts[5] 非空且非 "*" → img = getUrlByRule("*", ...)。 */
    private static void getPicUrl(SearchResult result, String pageUrl, HkRule rule, Element el, String[] ruleParts) {
        if (ruleParts.length > 5 && HkStringUtil.isNotEmpty(ruleParts[5]) && !"*".equals(ruleParts[5])) {
            result.setImg(HkCommonParser.getUrlByRule("*", rule, el, ruleParts[5]));
        }
    }
}
