package com.fongmi.android.tv.api.hk;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 直接移植自海阔 8.83 {@code com.example.hikerview.service.parser.LazyRuleParser}（do not rewrite）。
 * <p>
 * 解析 {@code @lazyRule=} 动态规则，三种模式：
 * <ul>
 * <li>{@code .js:} / {@code js:} —— JS 求值</li>
 * <li>HTML —— 抓取后按 CSS 选择器提取 URL</li>
 * </ul>
 * 适配：
 * <ul>
 * <li>kotlinx CoroutineScope → {@link Executor}（调用方可传自定义执行器）</li>
 * <li>JSEngine 单例 → {@link JsRuntime}（宿主注入）</li>
 * <li>MovieRule → {@link HkRule}；CommonParser → {@link HkCommonParser}</li>
 * <li>ThreadTool/HeavyTaskUtil → Executor + main {@link Handler}</li>
 * <li>ToastMgr/DebugUtil → Log</li>
 * </ul>
 */
public class HkLazyRuleParser {
    private static final String TAG = "HkLazyRuleParser";

    private static final Executor BG = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 8.83 BaseParseCallback 原样。 */
    public interface BaseParseCallback<T> {
        void start();

        void success(T result);

        void error(String msg);
    }

    /** JS 运行时桥（对应 8.83 JSEngine 单例的相关方法）。 */
    public interface JsRuntime {
        String evalLazyRule(Object rule, String myCode, String jsRule);

        Object evalJSForObj(String script, Object input);

        String getMyRule(Object rule);

        /** 取走 refreshPage 标记（读后清零）；默认无标记，运行时实现覆盖。 */
        default boolean consumeRefreshRequest() {
            return false;
        }
    }

    private static volatile JsRuntime jsRuntime;

    public static void setJsRuntime(JsRuntime runtime) {
        jsRuntime = runtime;
    }

    /** 8.83 JSEngine.generateMY 原样。 */
    public static String generateMY(String name, Object value) {
        if (value == null) {
            return "var " + name + " = null;\n";
        }
        if (value instanceof String) {
            return "var " + name + " = '" + escapeJavaScriptString((String) value) + "';\n";
        }
        return "var " + name + " = " + value + ";\n";
    }

    private static String escapeJavaScriptString(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '"' || c == '\'' || c == '\\') {
                sb.append('\\');
                sb.append(c);
            } else {
                sb.append(c);
            }
        }
        String r = sb.toString();
        r = r.replace(" ", "\\u2028").replace(" ", "\\u2029");
        return r;
    }

    private static void clearLazyTag(String[] lazyRule) {
        if (HkParserKit.isEmpty(lazyRule[0])) {
            return;
        }
        lazyRule[0] = HkParserKit.replaceOnce(lazyRule[0], "#noLoading#", "");
    }

    private static void runBg(Executor executor, Runnable r) {
        (executor != null ? executor : BG).execute(r);
    }

    private static void runUi(Activity activity, Runnable r) {
        if (activity != null) {
            activity.runOnUiThread(r);
        } else {
            MAIN.post(r);
        }
    }

    private static boolean isFinishing(Activity activity) {
        return activity != null && activity.isFinishing();
    }

    // ---- js: 模式 ----

    private static void parseByJs(Executor executor, final String prefix, final String myUrl,
                                  final Activity activity, final Object ruleObj, final String[] lazyRule,
                                  final int position, final String lazyTag,
                                  final BaseParseCallback<String> callback) {
        runBg(executor, () -> {
            try {
                JsRuntime rt = jsRuntime;
                if (rt == null) {
                    throw new IllegalStateException("JsRuntime not set");
                }
                rt.consumeRefreshRequest();
                final String result = rt.evalLazyRule(ruleObj,
                        generateMY("MY_URL", escapeJavaScriptString(myUrl))
                                + generateMY("MY_POSITION", position)
                                + HkParserKit.replaceOnce(lazyRule[1], prefix, ""),
                        lazyRule[0]);
                // 本链里 refreshPage 的刷新已由事件兑现，清掉标记防残留被别处误读
                rt.consumeRefreshRequest();
                if (isFinishing(activity)) {
                    return;
                }
                runUi(activity, () -> callback.success(result));
            } catch (Exception e) {
                Log.e(TAG, "parseByJs", e);
                if (isFinishing(activity)) {
                    return;
                }
                runUi(activity, () -> {
                    Log.e(TAG, "parseByJs error: " + e.getMessage());
                    callback.error(e.getMessage());
                });
            }
        });
    }

    private static void parseByJsForAny(Executor executor, final String prefix, final String myUrl,
                                        final Activity activity, final Object ruleObj, final String[] lazyRule,
                                        final int position, final String lazyTag,
                                        final BaseParseCallback<Object> callback) {
        runBg(executor, () -> {
            try {
                JsRuntime rt = jsRuntime;
                if (rt == null) {
                    throw new IllegalStateException("JsRuntime not set");
                }
                rt.consumeRefreshRequest();
                final Object result = rt.evalJSForObj(
                        rt.getMyRule(ruleObj) + HkParserKit.decodeConflictStr(
                                generateMY("MY_URL", escapeJavaScriptString(myUrl))
                                        + generateMY("MY_POSITION", position)
                                        + HkParserKit.replaceOnce(lazyRule[1], prefix, "")),
                        lazyRule[0]);
                rt.consumeRefreshRequest();
                if (isFinishing(activity)) {
                    return;
                }
                runUi(activity, () -> callback.success(result));
            } catch (Exception e) {
                Log.e(TAG, "parseByJsForAny", e);
                if (isFinishing(activity)) {
                    return;
                }
                runUi(activity, () -> {
                    Log.e(TAG, "parseByJsForAny error: " + e.getMessage());
                    callback.error(e.getMessage());
                });
            }
        });
    }

    // ---- 对外入口（8.83 原样，CoroutineScope → Executor） ----

    public static void parse(Activity activity, String[] lazyRule, String myUrl, String lazyTag,
                             BaseParseCallback<String> callback) {
        parse(null, activity, null, lazyRule, -1, myUrl, lazyTag, callback);
    }

    public static void parse(Activity activity, Object ruleObj, String[] lazyRule, String myUrl,
                             String lazyTag, BaseParseCallback<String> callback) {
        parse(null, activity, ruleObj, lazyRule, -1, myUrl, lazyTag, callback);
    }

    public static void parse(Activity activity, Object ruleObj, String[] lazyRule, int position,
                             String myUrl, String lazyTag, BaseParseCallback<String> callback) {
        parse(null, activity, ruleObj, lazyRule, position, myUrl, lazyTag, callback);
    }

    public static void parse(Executor executor, Activity activity, Object ruleObj, String[] lazyRule,
                             int position, String myUrl, String lazyTag,
                             BaseParseCallback<String> callback) {
        if (lazyRule.length < 2) {
            Log.e(TAG, "动态解析规则有误");
            return;
        }
        if (lazyRule.length > 2) {
            lazyRule = new String[]{lazyRule[0], HkParserKit.arrayToString(lazyRule, 1, "@lazyRule=")};
        }
        final String[] finalRule = lazyRule;
        callback.start();
        clearLazyTag(finalRule);
        if (finalRule[1].startsWith(".js:")) {
            parseByJs(executor, ".js:", myUrl, activity, ruleObj, finalRule, position, lazyTag, callback);
        } else if (finalRule[1].startsWith("js:")) {
            parseByJs(executor, "js:", myUrl, activity, ruleObj, finalRule, position, lazyTag, callback);
        } else {
            // 8.83 LazyRuleParser.parse 的第 5 个 String 参数是 codeAndHeader（取数时拼在详情页 URL 后，
            // 正确形态如"详情页URL+;get"）；星辰移植把该位传成了列表页 URL，拼出垃圾地址（1.3.42 封面案：
            // 「黄色仓库123」点封面卡在"动态解析规则中"）。
            // 本方法签名里第 6 个 String 参数 lazyTag 实际承接的就是 codeAndHeader（调用处见 HkEngineBridge），
            // 此处拼 lazyTag；签名不改，避免改动面扩散。
            String fetchUrl = finalRule[0].contains(";") ? finalRule[0] : finalRule[0] + lazyTag;
            HkHttpParser.parseSearchUrlForHtml(fetchUrl, new HkHttpParser.OnSearchCallBack() {
                @Override
                public void onSuccess(String url, final String html) {
                    runBg(executor, () -> {
                        try {
                            Element doc = Jsoup.parse(html);
                            // 冲突字符必须先解码再切分：黄色仓库123 的懒规则体是全角
                            // body＆＆＆＆#video_img＆＆＆＆src，不解码 split("&&") 切不开、
                            // 整串当字段名取空 → 点击没反应（他 18:49 真机日志实证）。
                            // 8.83 的选择器口径本就先 decodeConflictStr（HkSelector 同款）。
                            String[] chain = HkParserKit.decodeConflictStr(finalRule[1]).split("&&");
                            Element el = doc;
                            if (chain.length != 1) {
                                el = HkCommonParser.getTrueElement(chain[0], doc);
                            }
                            for (int i = 1; i < chain.length - 1; i++) {
                                el = HkCommonParser.getTrueElement(chain[i], el);
                            }
                            HkRule movieRule = new HkRule();
                            movieRule.setBaseUrl(HkParserKit.getBaseUrl(finalRule[0]));
                            movieRule.setChapterUrl(myUrl);
                            final String result = HkCommonParser.getUrl(el, chain[chain.length - 1], movieRule, finalRule[0]);
                            if (isFinishing(activity)) {
                                return;
                            }
                            runUi(activity, () -> callback.success(result));
                        } catch (Exception e) {
                            Log.e(TAG, "parse html", e);
                            if (isFinishing(activity)) {
                                return;
                            }
                            runUi(activity, () -> {
                                Log.e(TAG, "parse html error: " + e.getMessage());
                                callback.error(e.getMessage());
                            });
                        }
                    });
                }

                @Override
                public void onFailure(int code, final String msg) {
                    if (isFinishing(activity)) {
                        return;
                    }
                    runUi(activity, () -> {
                        Log.e(TAG, "fetch error: " + msg);
                        callback.error(msg);
                    });
                }
            });
        }
    }

    public static void parseFroAny(Executor executor, Activity activity, Object ruleObj, String[] lazyRule,
                                   int position, String myUrl, String lazyTag,
                                   final BaseParseCallback<Object> callback) {
        if (lazyRule.length < 2) {
            Log.e(TAG, "动态解析规则有误");
            return;
        }
        if (lazyRule.length > 2) {
            lazyRule = new String[]{lazyRule[0], HkParserKit.arrayToString(lazyRule, 1, "@lazyRule=")};
        }
        final String[] finalRule = lazyRule;
        callback.start();
        clearLazyTag(finalRule);
        if (finalRule[1].startsWith(".js:")) {
            parseByJsForAny(executor, ".js:", myUrl, activity, ruleObj, finalRule, position, lazyTag, callback);
        } else if (finalRule[1].startsWith("js:")) {
            parseByJsForAny(executor, "js:", myUrl, activity, ruleObj, finalRule, position, lazyTag, callback);
        } else {
            parse(executor, activity, ruleObj, finalRule, position, myUrl, lazyTag,
                    new BaseParseCallback<String>() {
                        @Override
                        public void start() {
                            callback.start();
                        }

                        @Override
                        public void success(String result) {
                            callback.success(result);
                        }

                        @Override
                        public void error(String msg) {
                            callback.error(msg);
                        }
                    });
        }
    }
}
