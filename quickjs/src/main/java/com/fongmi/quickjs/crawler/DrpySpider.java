package com.fongmi.quickjs.crawler;

import android.content.Context;

import com.fongmi.quickjs.bean.Res;
import com.fongmi.quickjs.method.Console;
import com.fongmi.quickjs.method.Global;
import com.fongmi.quickjs.method.Local;
import com.fongmi.quickjs.utils.Async;
import com.fongmi.quickjs.utils.JSUtil;
import com.fongmi.quickjs.utils.Module;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.UriUtil;
import com.github.catvod.utils.Util;
import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dalvik.system.DexClassLoader;

/**
 * drpy 通道：规则文件＋引擎型 JS 源（TVBox 标准配法 api 指引擎、ext 放规则，
 * 或 api 直接给规则文件）。
 *
 * 引擎＝内置定版 drpy2（hjdhnx/dr_py libs/drpy2.min.js，与上游逐字节相同，
 * 见资产旁注与 drpy-engine-integration-20261030.md）：以模块名
 * assets://js/lib/drpy2.min.js 求值，它的相对 import 按 UriUtil.resolve
 * 落到既有资产（cheerio.min.js/crypto-js.js/gbk.js 同名同物）、新 vendored
 * 的 jsencrypt.js（js/lib/）与模板.js（js/js/，resolve 的实际落点）。
 * 引擎的握手是 init(ext)：ext 为对象即规则本身、为 http 串则自取规则文本
 * eval、为内联串则直接 eval（把首个 "var rule" 改成对引擎 rule 的赋值）；
 * 导出对象的方法名（init/home/homeVod/category/detail/play/search/proxy/
 * sniffer/isVideo）与普通通道的调用名逐一对齐，故骨架与 Spider 同型同治，
 * 只换 createObj 的装载顺序与 init 的 ext 来源，多一层探测分流：
 *
 * - 经典规则（字段全字符串/模板继承）：走引擎本体，行为与 FongMi 一致。
 * - ds 型规则（一级/搜索/二级/推荐/class_parse/lazy/proxy_rule 含函数）：
 *   引擎的解析器只认字符串字段、函数型直接回 "{}"，故在引擎之外先把规则
 *   文本在本上下文顶层求值一份（__DRPY_RULE__），探到函数字段就装
 *   drpy-compat.js 垫片＋drpy-ds.js 适配桥接管蜘蛛对象；探不到/探失败
 *   一律回落引擎通道，不比现状差。
 * - api 内容自带 __JS_SPIDER__ 的（被名字误分到本通道的普通源）：按
 *   Spider.createObj 原样装载，零回归保险阀。
 */
public class DrpySpider extends com.github.catvod.crawler.Spider {

    /** 内置引擎的模块名：相对 import 的解析基准，必须与资产实位一致。 */
    static final String ENGINE_NAME = "assets://js/lib/drpy2.min.js";

    private static final java.util.regex.Pattern RULE_PATTERN =
            java.util.regex.Pattern.compile("(?:var|let|const)\\s+rule\\s*=");

    private static final String PROBE_PRE = "(function(){try{\n";
    private static final String PROBE_POST =
            "\n;globalThis.__DRPY_RULE__=(typeof rule!=='undefined')?rule:null;}" +
            "catch(e){globalThis.__DRPY_RULE__=null;globalThis.__DRPY_PROBE_ERROR__=String(e&&e.message||e);}})();";
    private static final String DS_DETECT =
            "(function(){var r=globalThis.__DRPY_RULE__;if(!r)return 'none';" +
            "var f=function(x){return typeof x==='function'};" +
            "return (f(r.一级)||f(r.搜索)||f(r.class_parse)||f(r.推荐)||f(r.二级)||f(r.lazy)||f(r.proxy_rule))?'ds':'classic';})()";

    private final ExecutorService executor;
    private final DexClassLoader dex;
    private final String api;

    // init 传入的原始 ext：探测规则文本与 ds 适配的 init 都要用，createObj
    // 在 init 流程内执行，故先存字段再 initializeJS（与 Spider 的时序差只在此）。
    private String extendRaw;
    private String ruleText;
    private boolean apiIsRule;
    private boolean ds;

    // 单次调用的超时上限（同步执行段与 Promise 等待段各一份）。wrapper 没有
    // 中断处理器，卡在原生 JS 里的执行线程无法从 Java 侧强杀，能做的是：
    // 同步段超时即判定执行线程已死、标记 unresponsive，后续调用快速失败、
    // 不再排进死线程后面；Promise 永不 settle 时执行线程还活着，只失败本次。
    private static final long TIMEOUT_SECONDS = 60;

    private QuickJSContext ctx;
    private JSObject jsObject;
    private Global global;
    private boolean cat;
    private volatile boolean unresponsive;

    public DrpySpider(String api, DexClassLoader dex) {
        this.executor = Executors.newSingleThreadExecutor();
        this.api = api;
        this.dex = dex;
    }

    /** api 是否按名字指向 drpy2 引擎（drpy2.min.js / drpy2-fast.min.js 等），JsLoader 分发用。 */
    public static boolean isEngineApi(String api) {
        return api != null && api.toLowerCase(java.util.Locale.ROOT).contains("drpy2");
    }

    /** 内容是否为 drpy2 引擎本体（引擎内部名指纹，规则与普通源都不会带）。 */
    public static boolean isEngineContent(String content) {
        return content != null && content.contains("getMubans") && content.contains("homeVodParse");
    }

    /**
     * 内容是否为裸规则文件（有 rule 定义、无 __JS_SPIDER__ 本体）。
     * 引擎本体自带 let rule={}，必须先被引擎指纹排除，否则会被误当规则
     * 拿去探测/init（harness 实证）。
     */
    public static boolean isBareRule(String content) {
        return content != null && !content.contains("__JS_SPIDER__") && !isEngineContent(content) && RULE_PATTERN.matcher(content).find();
    }

    private <T> T submitAndGet(Callable<T> callable) throws Exception {
        if (unresponsive) throw new IllegalStateException("quickjs spider unresponsive");
        Future<T> future = executor.submit(callable);
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            unresponsive = true;
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            // 等待被自家取消机制打断＝本次调用被取消，不是执行失败：
            // 恢复中断旗后原样抛，分类归上层（与 chaquo 侧同口径）。
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private Object call(String func, Object... args) throws Exception {
        CompletableFuture<Object> future = submitAndGet(() -> Async.run(jsObject, func, args));
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // JS 已同步返回、只是 Promise 永不 settle：执行线程还活着，
            // 只让本次调用按超时报错返回，不连坐整个蜘蛛。
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    // call 结果的类型兜底：JS 函数缺失时 Async 回 null、返回非约定类型时
    // 裸强转会把 ClassCastException／拆箱 NPE 直接打穿调用方。字符串路
    // 非 String 按其文本形态转，布尔路非 Boolean 一律按 false（与 chaquo
    // 侧缺方法给 False 的定案口径一致）。
    private static String asString(Object result) {
        if (result == null || result instanceof String) return (String) result;
        return String.valueOf(result);
    }

    private static boolean asBoolean(Object result) {
        return result instanceof Boolean && (Boolean) result;
    }

    // 与 WvSpider 同型同治：release 也是 ctx 操作（freeValue 头一步查线程亲和），
    // 在调用线程上做会抛 QuickJSException("Must be call same thread in
    // QuickJSContext.create!")。普通源主线（home/search）返回字符串不踩，
    // 但 category/player/proxy 的入参对象与 proxy 结果对象一直是这个雷。
    // 只把 release 搬回本类自己的执行器线程（FIFO 在先前调用之后），
    // 调用调度与超时口径一字不动；执行器已关停时放弃释放、随 ctx 销毁回收。
    private void releaseArg(Object arg) {
        if (!(arg instanceof JSObject)) return;
        JSObject object = (JSObject) arg;
        try {
            executor.execute(() -> {
                try {
                    object.release();
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        extendRaw = extend;
        initializeJS();
        if (ds) {
            // ds 适配的 init 吃原始 ext 串，不走 getExt 的结构化转换；
            // 串参无 JSObject 包装，不涉 release 口径。
            call("init", extend);
            return;
        }
        if (apiIsRule) {
            // 规则在 api 本身：规则文本作 ext 交引擎 init 内联 eval
            // （引擎对非 http 串正是此口径），站本身的 ext 在此形态下无通道，如实舍。
            call("init", ruleText);
            return;
        }
        Object ext = submitAndGet(() -> getExt(extend));
        try {
            call("init", ext);
        } finally {
            releaseArg(ext);
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        return asString(call("home", filter));
    }

    @Override
    public String homeVideoContent() throws Exception {
        return asString(call("homeVod"));
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        JSObject obj = submitAndGet(() -> JSUtil.toObject(ctx, extend));
        try {
            return asString(call("category", tid, pg, filter, obj));
        } finally {
            releaseArg(obj);
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        // ids 为 null/空时裸 get(0) 必崩；按基类 Spider.detailContent 的缺省口径返回空串，
        // 不拿空 id 去调 JS（与 CatSpider 空时以 "" 处理同口径）。
        if (ids == null || ids.isEmpty()) return "";
        return asString(call("detail", ids.get(0)));
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return asString(call("search", key, quick));
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return asString(call("search", key, quick, pg));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSArray array = submitAndGet(() -> JSUtil.toArray(ctx, vipFlags));
        try {
            return asString(call("play", flag, id, array));
        } finally {
            releaseArg(array);
        }
    }

    @Override
    public String liveContent(String url) throws Exception {
        return asString(call("live", url));
    }

    @Override
    public boolean manualVideoCheck() throws Exception {
        return asBoolean(call("sniffer"));
    }

    @Override
    public boolean isVideoFormat(String url) throws Exception {
        return asBoolean(call("isVideo", url));
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        return "catvod".equals(params.get("from")) ? proxy2(params) : proxy1(params);
    }

    @Override
    public String action(String action) throws Exception {
        return asString(call("action", action));
    }

    @Override
    public void destroy() {
        // 执行线程已卡死时不再碰 ctx：排队进去的 destroy 永远轮不到执行，
        // 从别的线程 destroy 正在跑原生代码的上下文有崩溃风险，直接关执行器。
        // 代价是这一个 ctx 的原生内存留到进程结束，换的是不崩、不挂死。
        if (!unresponsive) {
            try {
                call("destroy");
            } catch (Throwable e) {
                e.printStackTrace();
            }
            try {
                releaseJS();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        executor.shutdownNow();
    }

    private void releaseJS() throws Exception {
        submitAndGet(() -> {
            if (global != null) global.destroy();
            if (jsObject != null) jsObject.release();
            if (ctx != null) ctx.destroy();
            return null;
        });
    }

    private void initializeJS() throws Exception {
        submitAndGet(() -> {
            createCtx();
            createFun();
            createObj();
            return null;
        });
    }

    private void createCtx() {
        ctx = QuickJSContext.create();
        ctx.setConsole(new Console());
        ctx.evaluate(Asset.read("js/lib/http.js"));
        // 全局包装是上下文内部缓存的同一份（QuickJSContext 自持），不是每次
        // 新取的临时副本：只取来注册，绝不 release——放掉会把全上下文共用的
        // 包装 refCount 打到 0 并 native 多 free 一次，紧接着的 Global 注册
        // 会踩已死包装（批五 5eab204f4 误加，音乐桥同型已于 1.4.58 撤销，
        // 定根见 home-searchbox-jitter-20261029.md 闪退节）。
        ctx.getGlobalObject().setProperty("local", Local.class);
        ctx.setModuleLoader(new QuickJSContext.BytecodeModuleLoader() {
            @Override
            public String moduleNormalizeName(String baseModuleName, String moduleName) {
                return UriUtil.resolve(baseModuleName, moduleName);
            }

            @Override
            public byte[] getModuleBytecode(String moduleName) {
                return ctx.compileModule(Module.get().fetch(moduleName), moduleName);
            }
        });
    }

    private void createFun() {
        try {
            global = Global.create(ctx, executor);
            Class<?> clz = dex.loadClass("com.github.catvod.js.Function");
            clz.getDeclaredConstructor(QuickJSContext.class).newInstance(ctx);
        } catch (Throwable ignored) {
        }
    }

    private void createObj() {
        String spider = "__JS_SPIDER__";
        String content = Module.get().fetch(api);
        // 保险阀：api 内容自带 __JS_SPIDER__ 本体的是普通源（名字误分到
        // 本通道），按 Spider.createObj 原样装载，行为与普通通道逐字一致。
        if (content != null && content.contains(spider)) {
            cat = content.contains("__jsEvalReturn");
            ctx.evaluateModule(content.replace(spider, "globalThis." + spider), api);
            ctx.evaluateModule(String.format(Asset.read("js/lib/spider.js"), api, JSONObject.quote(siteKey)));
            // 全局包装是上下文缓存的同一份，只取来读属性，绝不 release（见
            // createCtx 注）；取到的 created 本体由 jsObject 持有、releaseJS 收口。
            Object created = ctx.getProperty(ctx.getGlobalObject(), spider);
            jsObject = created instanceof JSObject ? (JSObject) created : null;
            return;
        }
        // 垫片先行：ds 型源的沙箱全局（axios/Buffer/require/URLSearchParams/
        // setResult 等）要在规则文本求值前就位；引擎是模块作用域、自带同名
        // 绑定，经典规则不读这些全局，均无影响。
        ctx.evaluate(Asset.read("js/lib/drpy-compat.js"));
        String engine = Asset.read("js/lib/drpy2.min.js");
        if (engine == null || engine.isEmpty()) throw new IllegalStateException("drpy engine asset unavailable");
        ctx.evaluateModule(engine, ENGINE_NAME);
        ctx.evaluateModule(String.format(Asset.read("js/lib/spider.js"), ENGINE_NAME, JSONObject.quote(siteKey)));
        probeRule(content);
        if (ds) ctx.evaluate(Asset.read("js/lib/drpy-ds.js"));
        // 全局包装口径同 Spider：只取来读属性，绝不 release。
        Object created = ctx.getProperty(ctx.getGlobalObject(), spider);
        jsObject = created instanceof JSObject ? (JSObject) created : null;
        if (jsObject == null) throw new IllegalStateException("drpy spider object missing: " + api);
    }

    /**
     * ds 探测：把规则文本包进 IIFE 求值捕获 rule 对象（顶层只执行定义——
     * 常量/函数/缓存，与引擎 init 里的 eval 同性质），含函数字段则 ds=true
     * 由适配桥接管。探测抛错/无 rule/全字符串字段一律回落引擎通道。
     */
    private void probeRule(String apiContent) {
        ruleText = resolveRuleText(apiContent);
        if (ruleText == null) return;
        try {
            ctx.evaluate(PROBE_PRE + ruleText + PROBE_POST);
            Object mode = ctx.evaluate(DS_DETECT);
            ds = "ds".equals(mode);
        } catch (Throwable e) {
            ds = false;
        }
    }

    /**
     * 规则文本三来源：api 本身（裸规则形态）、ext 的 URL（Module 取）、
     * ext 内联文本。ext 是 JSON 对象/数组的是引擎的对象规则形态，不探测。
     */
    private String resolveRuleText(String apiContent) {
        if (apiContent != null && isBareRule(apiContent)) {
            apiIsRule = true;
            return apiContent;
        }
        if (extendRaw == null || extendRaw.isEmpty()) return null;
        if (extendRaw.startsWith("http")) {
            try {
                return Module.get().fetch(extendRaw);
            } catch (Throwable e) {
                return null;
            }
        }
        if (Json.isObj(extendRaw) || Json.isArray(extendRaw)) return null;
        return extendRaw.contains("rule") ? extendRaw : null;
    }

    private Object getExt(String ext) {
        // 对象与数组两种形态都要转成真 JS 值再传 init：旧写法只认对象，
        // 数组型 extend 会被当普通字符串传进去，期望数组的蜘蛛拿到的是
        // 字符串原文（与 returnType 须转真形态的历史坑同族）。
        boolean structured = Json.isObj(ext) || Json.isArray(ext);
        if (!cat) return structured ? ctx.parse(ext) : ext;
        JSObject obj = ctx.createNewJSObject();
        obj.setProperty("stype", 3);
        obj.setProperty("skey", siteKey);
        Object parsed = structured ? ctx.parse(ext) : null;
        if (parsed instanceof JSObject) {
            // parsed 被 obj 的 ext 属性持有后，本地包装这一份即 release，
            // 与非 cat 分支「返回本体由调用方 release」的口径对齐。
            try {
                obj.setProperty("ext", (JSObject) parsed);
            } finally {
                ((JSObject) parsed).release();
            }
        } else obj.setProperty("ext", ext);
        return obj;
    }

    private Object[] proxy1(Map<String, String> params) throws Exception {
        JSObject obj = submitAndGet(() -> JSUtil.toObject(ctx, params));
        Object called;
        try {
            called = call("proxy", obj);
        } finally {
            releaseArg(obj);
        }
        if (!(called instanceof JSArray)) return null;
        JSArray proxy = (JSArray) called;
        String json = submitAndGet(proxy::stringify);
        releaseArg(proxy);
        JSONArray array = new JSONArray(json);
        Map<String, String> headers = array.length() > 3 ? Json.toMap(array.optString(3)) : null;
        boolean base64 = array.length() > 4 && array.optInt(4) == 1;
        Object[] result = new Object[4];
        result[0] = array.optInt(0);
        result[1] = array.optString(1);
        result[2] = getStream(array.opt(2), base64);
        result[3] = headers;
        return result;
    }

    private Object[] proxy2(Map<String, String> params) throws Exception {
        String url = params.get("url");
        String header = params.get("header");
        // 入参缺失守卫：url 为 null 时旧写法在 split 先 NPE、header 为 null
        // 时 ctx.parse(null) 无守卫。url 缺失属坏请求，按无代理结果返回。
        if (url == null) return null;
        JSArray array = submitAndGet(() -> JSUtil.toArray(ctx, Arrays.asList(url.split("/"))));
        Object object = submitAndGet(() -> header == null ? ctx.createNewJSObject() : ctx.parse(header));
        String proxy;
        try {
            proxy = asString(call("proxy", array, object));
        } finally {
            releaseArg(array);
            releaseArg(object);
        }
        Res res = Res.objectFrom(proxy);
        Object[] result = new Object[3];
        result[0] = res.getCode();
        result[1] = res.getContentType();
        result[2] = res.getStream();
        return result;
    }

    private ByteArrayInputStream getStream(Object o, boolean base64) {
        if (o instanceof byte[]) {
            return new ByteArrayInputStream((byte[]) o);
        } else if (o == null || o == JSONObject.NULL) {
            // 蜘蛛没给内容（opt(2) 取到 null/JSONObject.NULL）：回空流，
            // 不许 o.toString() 先 NPE 或把字面 "null" 当内容交出去。
            return new ByteArrayInputStream(new byte[0]);
        } else {
            String content = o.toString();
            if (base64 && content.contains("base64,")) content = content.split("base64,")[1];
            return new ByteArrayInputStream(base64 ? Util.decode(content) : content.getBytes());
        }
    }
}
