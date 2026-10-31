package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.fongmi.android.tv.api.hk.HkCommonParser;
import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkLazyRuleParser;
import com.fongmi.android.tv.api.hk.HkRule;
import com.github.catvod.crawler.SpiderDebug;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import android.util.Log;

/**
 * HkArticleListModel.EngineBridge 的真绑定（引擎阶段）：
 * 运行时走 HkEngineManager 单例化（A1）；preRule 每规则只跑一次（原版 preCalled 语义，成功才标记）；
 * findList 走 HkHomeParser（HTML 路照抄 HomeParser、js 路走运行时 setListContext + parseList）；
 * lazy 走 HkLazyRuleParser（其 JsRuntime 接缝在此绑到单例运行时）；
 * #memoryPage# 存 SharedPreferences（原版为引擎内记忆，等价实现）。
 */
public final class HkEngineBridge implements HkArticleListModel.EngineBridge {
    private static final String TAG = "HkEngineBridge";

    /** HkLazyRuleParser.parse 需要 Activity（错误弹窗/加载弹窗），由宿主注入当前列表页。 */
    public static volatile Supplier<Activity> activityProvider;

    private final Context appContext;
    private final Set<String> preRuleDone = ConcurrentHashMap.newKeySet();

    public HkEngineBridge(Context context) {
        appContext = context.getApplicationContext();
        HkUrlDetector.init(appContext);
        HkHomeParser.jsListParser = HkEngineBridge::parseHomeJs;
        HkHomeParser.errorLogger = (msg, rule) -> log(msg, rule);
        HkSearchParser.jsSearchParser = HkEngineBridge::parseSearchJs;
        HkSearchParser.errorLogger = (msg, rule) -> log(msg, rule);
        HkSearchModel.bridge = new HkSearchModel.SearchBridge() {
            @Override
            public void parsePreRule(com.fongmi.android.tv.api.hk.HkSearchEngine engine) throws Exception {
                HkRule rule = engine.toRule();
                HkJsRuntime rt = HkEngineManager.runtimeFor(rule);
                HkEngineManager.ensureInit(rt);
                rt.parsePreEngineRule(rule);
            }

            @Override
            public String proxyUrl(String url, String proxy) {
                return HkJsRuntime.proxyUrl(url, proxy);
            }

            @Override
            public void log(Object msg, Object rule) {
                HkEngineBridge.this.log(msg, rule);
            }
        };
        HkLazyRuleParser.setJsRuntime(new HkLazyRuleParser.JsRuntime() {
            @Override
            public String evalLazyRule(Object rule, String myCode, String jsRule) {
                if (!(rule instanceof HkRule)) throw new IllegalArgumentException("rule is not HkRule: " + (rule == null ? "null" : rule.getClass().getName()));
                HkRule hkRule = (HkRule) rule;
                HkJsRuntime rt = HkEngineManager.runtimeFor(hkRule);
                try {
                    HkEngineManager.ensureInit(rt);
                } catch (Exception e) {
                    Log.e(TAG, "hk error", e);
                }
                return rt.evalLazyRule(rule, myCode, jsRule);
            }

            @Override
            public Object evalJSForObj(String script, Object input) {
                HkRule rule = HkEngineManager.currentRule();
                if (rule == null) return null;
                HkJsRuntime rt = HkEngineManager.runtimeFor(rule);
                try {
                    HkEngineManager.ensureInit(rt);
                } catch (Exception e) {
                    Log.e(TAG, "hk error", e);
                    return null;
                }
                return rt.evalJSForObj(script, input);
            }

            @Override
            public String getMyRule(Object rule) {
                return HkJsRuntime.getMyRule(rule);
            }
        });
    }

    /**
     * 对应 JsEngineBridge.parseCallBack 的搜索 JS 路：预取正文进 resCode、
     * 注入 MY_KEYWORD/MY_PAGE 后走运行时 parseSearch（setResult 式），与 HkEngine.search 同链。
     */
    private static List<com.fongmi.android.tv.api.hk.SearchResult> parseSearchJs(
            String url, com.fongmi.android.tv.api.hk.HkSearchEngine engine, String html,
            Map<String, Object> options) throws Exception {
        HkRule rule = engine.toRule();
        HkJsRuntime rt = HkEngineManager.runtimeFor(rule);
        HkEngineManager.ensureInit(rt);
        Object keywordObj = options == null ? null : options.get("MY_KEYWORD");
        Object pageObj = options == null ? null : options.get("MY_PAGE");
        String keyword = keywordObj == null ? "" : String.valueOf(keywordObj);
        int page = pageObj instanceof Number ? ((Number) pageObj).intValue() : 1;
        rt.setListContext("search", "", "", "", "", "", "");
        rt.setEvalResCodeBody(html);
        try {
            // 走第①块备好的带规则重载：求值前按当前页规则重绑（与 runtimeFor 的重绑同口径，双保险）
            return rt.parseSearch(rule, engine.getFindRule(), url, keyword, page);
        } catch (Exception e) {
            String err = rt.getError();
            throw new Exception(err != null && !err.isEmpty() ? err : e.getMessage(), e);
        } finally {
            rt.setEvalResCodeBody(null);
        }
    }

    /** 对应 JsEngineBridge.parseHomeCallBack 的 JS 路：注入列表上下文后走运行时 parseList。 */
    private static void parseHomeJs(String key, String colType, HkRule rule, String findRule,
                                    Map<String, Object> options, String html, String url,
                                    HkHomeParser.Callback callback) {
        HkJsRuntime rt = null;
        try {
            rt = HkEngineManager.runtimeFor(rule);
            HkEngineManager.ensureInit(rt);
            int page = 1;
            Object myPage = options == null ? null : options.get("MY_PAGE");
            if (myPage instanceof Number) page = ((Number) myPage).intValue();
            rt.setListContext("home", rule.getClassUrl(), rule.getClassName(), rule.getParams(),
                    rule.getAreaUrl(), rule.getYearUrl(), rule.getSortUrl());
            // 预抓正文必须递进运行时：getResCode() 靠它取首页 HTML。搜索路（parseSearchJs）
            // 与 HkEngine.home 都设了，唯独首页这条漏了——靠 getResCode 的规则（如漫小肆）
            // 拿到的恒是空正文，列表解析全空、轮播类脚本直接崩（item.img of undefined）。
            rt.setEvalResCodeBody(html);
            List<HkItem> list;
            try {
                // 走第①块备好的带规则重载：求值前按当前页规则重绑（与 runtimeFor 的重绑同口径，双保险）
                list = rt.parseList(rule, findRule, url, page);
            } finally {
                rt.setEvalResCodeBody(null);
            }
            callback.bindArrayToView(key, list);
        } catch (Exception e) {
            // 诊断：新链 findList 内层求值失败（旧 home failed 已不可达），只记日志不改行为；
            // getError 带来源/代码行/详情口径，与旧链对齐
            String detail = rt != null ? rt.getError() : null;
            SpiderDebug.log("hk", "hk: home findList failed rule=%s err=%s",
                    rule.getTitle(),
                    detail != null && !detail.isEmpty() ? detail : String.valueOf(e));
            Log.e(TAG, "hk error", e);
            callback.error("解析失败", e.toString(), "404", e);
        }
    }

    @Override
    public void parsePreRule(HkRule rule) throws Exception {
        String key = HkEngineManager.ruleKey(rule);
        if (preRuleDone.contains(key)) return;
        HkJsRuntime rt = HkEngineManager.runtimeFor(rule);
        HkEngineManager.ensureInit(rt);
        rt.parsePreRule(rule, true);
        preRuleDone.add(key);
    }

    @Override
    public String proxyUrl(String url, String proxyConfig) {
        return HkJsRuntime.proxyUrl(url, proxyConfig);
    }

    @Override
    public void log(Object msg, Object extra) {
        try {
            SpiderDebug.log("hk", "%s", String.valueOf(msg));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String parsePageClassUrl(String url, int page, HkRule rule, Map<String, Object> options) {
        return HkCommonParser.parsePageClassUrl(url, page, rule, options);
    }

    @Override
    public void findList(String url, String actionType, HkRule rule, String findRule, String html,
                         int page, Map<String, Object> injectMap, HkArticleListModel.Callback callback) {
        HkHomeParser.findList(actionType, url, rule, findRule, html, page, injectMap,
                new HkHomeParser.Callback() {
                    @Override
                    public void bindArrayToView(String key, List<HkItem> list) {
                        callback.bindArrayToView(key, list);
                    }

                    @Override
                    public void error(String title, String msg, String code, Exception e) {
                        callback.error(title, msg, code, e);
                    }
                });
    }

    @Override
    public int getMemoryPage(String url, String title) {
        return prefs().getInt(memoryKey(url, title), 1);
    }

    @Override
    public void updateMemoryPage(String url, String title, int page) {
        SharedPreferences.Editor editor = prefs().edit();
        if (page == -1) {
            editor.remove(memoryKey(url, title));
        } else {
            editor.putInt(memoryKey(url, title), page);
        }
        editor.apply();
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences("hk_memory_page", Context.MODE_PRIVATE);
    }

    private static String memoryKey(String url, String title) {
        return (url == null ? "" : url) + "\n" + (title == null ? "" : title);
    }

    @Override
    public String evalWithRule(HkRule rule, String js, String input) {
        try {
            HkJsRuntime rt = HkEngineManager.runtimeFor(rule);
            HkEngineManager.ensureInit(rt);
            return rt.evalJS(HkJsRuntime.getMyRule(rule) + js, input);
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
            return null;
        }
    }

    /**
     * 照原版 lambda$initData$11（ArticleListFragment.smali）：先 evalJS(getMyRule+code, url)
     * 原样求值（putVar 等副作用与原版同一时机发生），结果是 JSON 数组串时再经运行时
     * setResult 通道转条目——走 collectResult 原装映射（img&gt;pic&gt;pic_url、extra 语义），
     * 不另写第二份 JSON→条目映射。任一步失败/无产出返回 null，调用方回落缓存 data。
     */
    @Override
    public List<HkItem> evalCacheCode(HkRule rule, String code, String url) {
        try {
            String result = evalWithRule(rule, code, url);
            if (result == null) return null;
            result = result.trim();
            if (!result.startsWith("[") || !result.endsWith("]")) return null;
            HkJsRuntime rt = HkEngineManager.runtimeFor(rule);
            HkEngineManager.ensureInit(rt);
            // 走 Unmarked 入口：这是缓存代码重建、不是列表加载，不许顶掉在跑的列表
            // 求值（列表求值的顶替机制只认 parseList 两入口，见 HkJsRuntime.ListEvalMark）
            List<HkItem> items = rt.parseListUnmarked(rule, "setResult(" + result + ")", url, 1);
            return items == null || items.isEmpty() ? null : items;
        } catch (Exception e) {
            Log.e(TAG, "hk evalCacheCode", e);
            return null;
        }
    }

    @Override
    public String evalFileSelect(HkRule rule, String js, String input, String myUrl, String myPath) {
        try {
            HkJsRuntime rt = HkEngineManager.runtimeFor(rule);
            HkEngineManager.ensureInit(rt);
            String script = HkJsRuntime.getMyRule(rule)
                    + HkLazyRuleParser.generateMY("MY_URL", myUrl)
                    + HkLazyRuleParser.generateMY("MY_PATH", myPath)
                    + js;
            return rt.evalJS(script, input);
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
            return null;
        }
    }

    @Override
    public String pageParserGetUrl(String url, String extraJson) {
        return HkStringUtil.pageParserGetUrl(url, extraJson);
    }

    @Override
    public void parseLazyRule(HkRule rule, String[] lazyParts, int position, String codeAndHeader,
                              String myUrl, HkArticleListModel.LazyCallback callback) {
        Activity activity = activityProvider == null ? null : activityProvider.get();
        HkEngineManager.setCurrentRule(rule);
        HkLazyRuleParser.parse(null, activity, rule, lazyParts, position, myUrl, codeAndHeader,
                new HkLazyRuleParser.BaseParseCallback<String>() {
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
