package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.fongmi.android.tv.api.hk.HkCommonParser;
import com.fongmi.android.tv.api.hk.HkHttpParser;
import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkLazyRuleParser;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkWebkitFetcher;
import com.github.catvod.crawler.SpiderDebug;

import java.util.List;
import java.util.Map;
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

    /**
     * HkHttpParser 的 JS 桥（对应 8.83 HttpParser 直调 JSEngine 单例的那条路）：
     * 移植时 HkHttpParser 把单例直调改成了可注入静态桥却从未注入，桥恒 null，
     * `.js:` 三支（URL 本体/查询参数/请求头）与 webview:// 取数支全程空转——
     * 容器子规则首页地址恒是 `.js:` 形（indexUrl 取数时现算真地址、现读分类
     * 选择），桥不通则每次取数只打站点根页，分类/翻页永不变（账
     * heji-subrule-category-20261114）。在此与其它引擎接缝同处装配。
     */
    private static final HkHttpParser.JsBridge HTTP_JS_BRIDGE = new HkHttpParser.JsBridge() {
        @Override
        public String evalJs(String js, String input) {
            // 官形钉死（8.83 HttpParser.smali 三处调用点 2318/2839/2967）：
            // 取数线程直调 JSEngine.evalJS(js, input) 两参形（decode=true），
            // 引擎在调用线程现开 Context 求值——不进运行时 submit 串行段
            // （那是 parseList/parseSearch 等全装配入口的面，官 evalJS 不在其列；
            // 且取数发生在模型 IO 线程/懒解析调用线程，提交串行段再 .get()
            // 只会平添死等）。当前规则经 HkEngineManager 的 ThreadLocal 通道
            // 定位：模型取数前已在同线程把指针置到本页规则（列表/搜索模型
            // parse 入口），懒解析路 parseLazyRule 入口同置。
            HkRule rule = HkEngineManager.currentRule();
            HkJsRuntime rt = rule != null ? HkEngineManager.runtimeFor(rule)
                    : HkJsRuntime.sharedBridge();
            if (rt == null) {
                // 进程内还没有任何规则加载过（无从求值）：回 input 原样，
                // 与桥未接时「段不求值」的退化同级，不把脚本尾巴漏进地址/头。
                return input;
            }
            try {
                HkEngineManager.ensureInit(rt);
            } catch (Exception e) {
                // 与运行时 evalJS 自身的失败形对齐（"error:"+msg），不回 null
                // 让调用方把 null 拼进地址。
                Log.e(TAG, "HkHttpParser.evalJs 引擎初始化失败", e);
                return "error:" + e.getMessage();
            }
            return rt.evalJS(js, input);
        }

        @Override
        public String fetchCodeByWebView(String url, Map<String, Object> options) {
            // 现成件：运行时自身的 fetchCodeByWebView API 同走这一份。
            return HkWebkitFetcher.fetchCodeByWebView(url, options);
        }

        @Override
        public boolean canWebViewParse(String url) {
            // 8.83 WebkitFetcher.Companion.canParse 原文（官包 smali 亲读）：
            // 非空且以 "webview://" 开头（大小写敏感）。
            return url != null && !url.isEmpty() && url.startsWith("webview://");
        }
    };

    private final Context appContext;

    public HkEngineBridge(Context context) {
        appContext = context.getApplicationContext();
        HkHttpParser.setJsBridge(HTTP_JS_BRIDGE);
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
                    // 初始化失败不许在未初始化的运行时上继续求值装出空结果
                    // （残项处置 2026-11-14）：抛给 HkLazyRuleParser 既有
                    // catch → callback.error 失败出口。
                    Log.e(TAG, "evalLazyRule 引擎初始化失败", e);
                    throw new RuntimeException("引擎初始化失败：" + e.getMessage(), e);
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
                    // 同 evalLazyRule：初始化失败抛给调用方既有错误回调，
                    // 不许回 null 与「求值无结果」同形（残项处置 2026-11-14）。
                    Log.e(TAG, "evalJSForObj 引擎初始化失败", e);
                    throw new RuntimeException("引擎初始化失败：" + e.getMessage(), e);
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
        // 环 25-1：搜索路 onUpdate 通道——HkSearchParser 把 SearchCallback.onUpdate
        // 以 BiConsumer 放进 options（ON_UPDATE），此处转成运行时 bindObject 监听
        // （条目仅 title=value → 回调 onUpdate(key, title)），求值毕清除。搜索路
        // 既有真回调（HkSearchModel 侧）不动、只是补上运行时到它的线。
        Object onUpdate = options == null ? null : options.get("ON_UPDATE");
        if (onUpdate instanceof java.util.function.BiConsumer) {
            @SuppressWarnings("unchecked")
            java.util.function.BiConsumer<String, String> consumer =
                    (java.util.function.BiConsumer<String, String>) onUpdate;
            rt.setBindObjectToViewListener((key, item) ->
                    consumer.accept(key, item == null ? null : item.getTitle()));
        }
        try {
            // 走第①块备好的带规则重载：求值前按当前页规则重绑（与 runtimeFor 的重绑同口径，双保险）
            return rt.parseSearch(rule, engine.getFindRule(), url, keyword, page);
        } catch (Exception e) {
            String err = rt.getError();
            throw new Exception(err != null && !err.isEmpty() ? err : e.getMessage(), e);
        } finally {
            rt.setEvalResCodeBody(null);
            rt.setBindObjectToViewListener(null);
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
            // 环 25-1：主路 onUpdate 通道——页面 Callback 的 bindObjectToView 置入
            // 运行时监听，求值毕（下方外层收口）清除；addListener 的 onUpdate 经
            // armCallbackKey 装配的真回调绑回页面按 key 分发。
            rt.setBindObjectToViewListener(callback::bindObjectToView);
            // 预抓正文必须递进运行时：getResCode() 靠它取首页 HTML。搜索路（parseSearchJs）
            // 与 HkEngine.home 都设了，唯独首页这条漏了——靠 getResCode 的规则（如漫小肆）
            // 拿到的恒是空正文，列表解析全空、轮播类脚本直接崩（item.img of undefined）。
            rt.setEvalResCodeBody(html);
            List<HkItem> list;
            try {
                // 走第①块备好的带规则重载：求值前按当前页规则重绑（与 runtimeFor 的重绑同口径，双保险）
                // 顶替身份随 options 自模型递入：顶替只在本页面的新发之间（账 hk-import-firstload-empty-dig §六.1）
                Object evalScope = options == null ? null : options.get(HkArticleListModel.KEY_LIST_EVAL_SCOPE);
                list = evalScope != null
                        ? rt.parseList(rule, findRule, url, page, evalScope)
                        : rt.parseList(rule, findRule, url, page);
            } finally {
                rt.setEvalResCodeBody(null);
                rt.setBindObjectToViewListener(null);
            }
            callback.bindArrayToView(key, list);
        } catch (HkJsRuntime.ListEvalSupersededException cancelled) {
            // 本发已被同页面更新的加载顶替（取消语义）：静默丢弃——不回空表、
            // 不报解析失败，继任发的回调自会落页（官形：结果真假与上屏分离）。
            SpiderDebug.log("hk", "hk: home findList superseded rule=%s", rule.getTitle());
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
        // G35：官上层每调一次跑一次、引擎内外无次数闸——桥侧旧规则级
        // 次数闩已摘（与 HkJsRuntime init 一次性闩同批），HkArticleListModel
        // 第一页加载调一次就真跑一次，与搜索侧 parsePreEngineRule 无闩同口径。
        HkJsRuntime rt = HkEngineManager.runtimeFor(rule);
        HkEngineManager.ensureInit(rt);
        // 环11 对官：官模型层调 parsePreRule(rule) 单参＝不抛形——preRule 的
        // JS 错误在引擎内被 setError 吞成 Toast、find_rule 照常继续跑；旧形
        // 传 true（抛形）使 HkArticleListModel 的 catch 整页报错中止，与官
        // 失败语义整条相反。模型层 catch 随之成官形死支（只兜拼装期异常）。
        rt.parsePreRule(rule);
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
                    public void bindObjectToView(String key, HkItem item) {
                        // 环 25-1：运行时 onUpdate 真回调的页面落点（模型 Callback
                        // 的 bindObjectToView → Fragment 按 key 分发）。
                        callback.bindObjectToView(key, item);
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
            // 文件选择求值失败不再与无结果同形静默（残项处置 2026-11-14）：
            // 回 null 不变（调用方按无结果收口），但失败经既有提示形态显形。
            Log.e(TAG, "evalFileSelect 文件选择求值失败", e);
            Activity act = activityProvider == null ? null : activityProvider.get();
            com.fongmi.android.tv.utils.ToastUtil.show(act != null ? act : appContext,
                    "文件选择处理失败", android.widget.Toast.LENGTH_SHORT);
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
