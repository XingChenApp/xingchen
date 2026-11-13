package com.fongmi.android.tv.ui.hk;

import android.text.TextUtils;

import com.fongmi.android.tv.api.hk.HkCommonParser;
import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.api.hk.HkParserKit;
import com.fongmi.android.tv.api.hk.HkRule;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import android.util.Log;

/**
 * 照抄 8.83 HomeParser.findList：
 * find_rule 以 "js:" 开头 → JsEngineBridge.parseHomeCallBack（此处走 jsListParser 钩子，桥绑定运行时）；
 * 否则 HTML 路：find_rule 以 "==>" 切头尾（尾段逐条 replace fyIndex 后以 "@rule=" 回拼到条目 url）、
 * 头段以 ";" 切成 [选择器链, 标题, 封面, 描述, url...]，url 段以 "&&" 回拼；
 * MovieRule 上下文等价为 rule 的 transient baseUrl/chapterUrl；
 * developerMode 时单项失败置空/置 "*"，否则整项跳过；canAdd 照官恒真
 * （FilterUtil.hasFilterWord(String) 是恒 false 桩，硬门账 C 环27 §2），标题不删条；
 * 整体异常 → error("解析失败", e.toString(), "404", e) 并记引擎日志。
 * findRule 为 null 时首行 startsWith 自然抛 NPE 穿出本函数，由上层
 * ArticleListModel 的通用 catch 收尾（error("", "ArticleListModel-解析失败："+e, "", e)，
 * 硬门账 C 环27 D27-2 追记订正）；空串走选择器路、由 jsoup select("") 抛
 * ValidationException 落本函数整体 catch 的 404 形，两路不许混。
 */
public final class HkHomeParser {
    private static final String TAG = "HkHomeParser";

    /** 对应 BaseCallback&lt;ArticleList&gt; 的两法（桥适配到 Model 的 Callback）。 */
    public interface Callback {
        void bindArrayToView(String key, List<HkItem> list);

        /** 环 25-1：官 BaseArticleCallback.bindObjectToView——addListener 的
         * onUpdate(key, value) 以仅 title=value 的条目绑回、页面按 key 分发。
         * 默认空实现，只有 findList 的桥适配层转发到模型 Callback。 */
        default void bindObjectToView(String key, HkItem item) {
        }

        void error(String title, String msg, String code, Exception e);
    }

    /** 对应 JsEngineBridge.parseHomeCallBack(key, colType, rule, findRule, options, html, url, callback)。 */
    public interface JsListParser {
        void parseHome(String key, String colType, HkRule rule, String findRule,
                       Map<String, Object> options, String html, String url, Callback callback);
    }

    /** 对应 SettingConfig.developerMode（宿主/桥绑定时置位）。 */
    public static volatile boolean developerMode;

    /** 对应 JsEngineBridge（桥绑定时注入）。 */
    public static volatile JsListParser jsListParser;

    /**
     * 对应 FilterUtil.hasFilterWord(String)：官该重载是恒 return false 的桩
     * （FilterUtil.smali:222-228，.locals 0），故 canAdd 恒真。旧形默认接星辰
     * AdBlock 词表删条，与官口径冲突（硬门账 C 环27 D27-1，用户已拍板照官摘除）；
     * 保留注入点仅供测试夹具核对调用，生产无绑定、恒不删条。
     */
    public static volatile Predicate<String> filterWordChecker;

    /** 对应原版整体异常时的 JSEngine.log(e.getMessage(), ruleJson)（桥绑定时注入）。 */
    public static volatile java.util.function.BiConsumer<String, HkRule> errorLogger;

    private HkHomeParser() {
    }

    public static void findList(String key, String url, HkRule rule, String findRule, String html,
                                int page, Map<String, Object> options, Callback callback) {
        // D27-2 照官：HomeParser.findList 首指令即 findRule.startsWith("js:")，
        // null 在此抛 NPE 且位于 HomeParser 内部 try 之前、直接穿出，由上层
        // ArticleListModel$2 的通用 catch 收成 error("", "ArticleListModel-解析失败："+e, "", e)。
        // 旧形先行 error("解析失败","findRule 为空","404",null) 与官终态不符，整支摘除。
        if (findRule.startsWith("js:")) {
            Map<String, Object> opts = options == null ? new HashMap<>() : options;
            opts.put("MY_PAGE", page);
            JsListParser parser = jsListParser;
            if (parser != null) {
                parser.parseHome(key, rule.getColType(), rule, findRule, opts, html, url, callback);
            } else {
                callback.error("解析失败", "js engine not bound", "404", null);
            }
            return;
        }
        String fullRule = findRule + "";
        String ruleHead = fullRule.split("==>")[0];
        rule.setBaseUrl(HkStringUtil.getBaseUrl(rule.getUrl()));
        rule.setChapterUrl(url);
        try {
            Document doc = Jsoup.parse(html);
            List<HkItem> out = new ArrayList<>();
            String[] ruleParts = ruleHead.split(";");
            Elements elements = new Elements();
            String[] chain = ruleParts[0].split("&&");
            Element scope = HkCommonParser.getTrueElement(chain[0], doc);
            for (int i = 1; i < chain.length - 1; i++) {
                scope = HkCommonParser.getTrueElement(chain[i], scope);
            }
            elements.addAll(HkCommonParser.selectElements(scope, chain[chain.length - 1]));
            int index = 0;
            for (Element el : elements) {
                String tailRule = "";
                String[] fr = fullRule.split("==>");
                if (fr.length > 1) {
                    fr[1] = fr[1].replace("fyIndex", String.valueOf(index));
                    tailRule = HkParserKit.arrayToString(fr, 1, "==>");
                }
                try {
                    HkItem item = new HkItem();
                    item.setType(TextUtils.isEmpty(rule.getColType())
                            ? HkColType.MOVIE_3.code : rule.getColType());
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
                            getPicUrl(item, rule, el, ruleParts[2]);
                        } catch (Exception e) {
                            item.setPic("");
                        }
                    } else {
                        getPicUrl(item, rule, el, ruleParts[2]);
                    }
                    if (developerMode) {
                        try {
                            item.setDesc(HkCommonParser.getTextByRule(el, ruleParts[3]));
                        } catch (Exception e) {
                            item.setDesc("");
                        }
                    } else {
                        item.setDesc(HkCommonParser.getTextByRule(el, ruleParts[3]));
                    }
                    String urlRule = HkParserKit.arrayToString(ruleParts, 4, ruleParts.length, "&&");
                    if (developerMode) {
                        try {
                            getUrl(url, item, rule, el, urlRule, ruleParts, tailRule);
                        } catch (Exception e) {
                            item.setUrl("*");
                        }
                    } else {
                        getUrl(url, item, rule, el, urlRule, ruleParts, tailRule);
                    }
                    if (canAdd(item.getTitle())) out.add(item);
                } catch (Exception e) {
                    Log.e(TAG, "hk error", e);
                }
                index++;
            }
            callback.bindArrayToView(key, out);
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
            callback.error("解析失败", e.toString(), "404", e);
            if (errorLogger != null) errorLogger.accept(e.getMessage(), rule);
        }
    }

    /** 照抄 HomeParser.getPicUrl："*" 时 pic="*"，且规则 col_type 为 movie_1 时条目改 text_1。 */
    private static void getPicUrl(HkItem item, HkRule rule, Element el, String picRule) {
        if ("*".equals(picRule)) {
            item.setPic("*");
            if (HkColType.MOVIE_1.code.equals(rule.getColType())) {
                item.setType(HkColType.TEXT_1.code);
            }
        } else {
            item.setPic(HkCommonParser.getUrlByRule("*", rule, el, picRule));
        }
    }

    /** 照抄 HomeParser.getUrl：ruleParts 超过 4 段才取 url；尾段非空以 "@rule=" 回拼。 */
    private static void getUrl(String pageUrl, HkItem item, HkRule rule, Element el, String urlRule,
                               String[] ruleParts, String tailRule) {
        if (ruleParts.length > 4) {
            item.setUrl(HkCommonParser.getUrlByRule(pageUrl, rule, el, urlRule));
            if (HkStringUtil.isNotEmpty(tailRule)) {
                item.setUrl(item.getUrl() + "@rule=" + tailRule);
            }
        }
    }

    /** 照抄 HomeParser.canAdd = !FilterUtil.hasFilterWord(title)；官该重载恒 false，故恒真。 */
    private static boolean canAdd(String title) {
        Predicate<String> checker = filterWordChecker;
        return checker == null || !checker.test(title);
    }
}
