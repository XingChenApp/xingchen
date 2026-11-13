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

public class Spider extends com.github.catvod.crawler.Spider {

    private final ExecutorService executor;
    private final DexClassLoader dex;
    private final String api;

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

    public Spider(String api, DexClassLoader dex) {
        this.executor = Executors.newSingleThreadExecutor();
        this.api = api;
        this.dex = dex;
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
        initializeJS();
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
        String global = "globalThis." + spider;
        String content = Module.get().fetch(api);
        // 内容取不到（本地读失败/远程非 200）时 fetch 回 null：不许裸 NPE 在
        // content.contains 上、更不许把 null 交给 evaluateModule，具名失败给
        // JsLoader 记账，上层照旧降级 SpiderNull。
        if (content == null) throw new IllegalStateException("quickjs source content unavailable: " + api);
        cat = content.contains("__jsEvalReturn");
        ctx.evaluateModule(content.replace(spider, global), api);
        ctx.evaluateModule(String.format(Asset.read("js/lib/spider.js"), api, JSONObject.quote(siteKey)));
        // 全局包装是上下文缓存的同一份，只取来读属性，绝不 release（见
        // createCtx 注）；取到的 created 本体由 jsObject 持有、releaseJS 收口。
        Object created = ctx.getProperty(ctx.getGlobalObject(), spider);
        jsObject = created instanceof JSObject ? (JSObject) created : null;
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
