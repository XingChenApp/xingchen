package com.fongmi.quickjs.crawler;

import android.content.Context;

import com.fongmi.quickjs.method.Console;
import com.fongmi.quickjs.method.WvHost;
import com.fongmi.quickjs.utils.Async;
import com.fongmi.quickjs.utils.JSUtil;
import com.fongmi.quickjs.utils.Module;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * 新格式宿主型 JS 源（wvB / WvSpider 类）的通道。
 *
 * 这类源与普通 drpy 源的约定完全不同：没有 __JS_SPIDER__，入口是顶层全局函数
 * （homeContent/homeVideoContent/categoryContent/detailContent/recommendContent/
 * searchContent/playerContent/action），网络与存储走宿主面（fetch 与 host 对象，
 * 或 Java 与 Result 对象，见 js/lib/wv.js 与 WvHost），文件头有 @config 声明
 * （timeout/returnType/blockImages/debug）。本类只负责：识别、建上下文、
 * 注入宿主、按同名全局函数分发、把返回值翻译成星辰的 Result 口径；
 * 普通 JS 源的 Spider 一字未动。
 */
public class WvSpider extends com.github.catvod.crawler.Spider {

    private static final Pattern ROUTES_PATTERN = Pattern.compile("\\b(?:var|let|const)\\s+routes\\s*=");
    private static final Pattern HOME_PATTERN = Pattern.compile("function\\s+homeContent\\s*\\(");
    private static final Pattern PLAYER_PATTERN = Pattern.compile("function\\s+playerContent\\s*\\(");
    private static final String[] ENTRIES = {"init", "homeContent", "homeVideoContent", "categoryContent", "detailContent", "recommendContent", "searchContent", "playerContent", "action", "destroy"};

    private final ExecutorService executor;
    private final String api;
    private final Set<String> entries = new HashSet<>();

    private QuickJSContext ctx;
    private JSObject globalObj;
    private WvHost host;
    private Config config = new Config();
    private volatile boolean unresponsive;

    public WvSpider(String api) {
        this.executor = Executors.newSingleThreadExecutor();
        this.api = api;
    }

    /* ---------- 识别与 @config 解析（纯函数，可单测） ---------- */

    /**
     * 入口识别：产出 __JS_SPIDER__ 的一律是普通源（否决项，优先于一切特征）；
     * 余下认 @config 头、routes 入口表、或成对的同名入口函数定义。
     */
    public static boolean isWvSource(String content) {
        if (content == null || content.isEmpty()) return false;
        if (content.contains("__JS_SPIDER__")) return false;
        if (content.contains("@config")) return true;
        if (ROUTES_PATTERN.matcher(content).find()) return true;
        return HOME_PATTERN.matcher(content).find() && PLAYER_PATTERN.matcher(content).find();
    }

    public static class Config {
        public int timeout = 60;
        public String returnType = "";
        public boolean blockImages;
        public boolean debug;
    }

    /** 解析文件头注释里的 @config 段（key: value 逐行），缺项给默认。 */
    public static Config parseConfig(String content) {
        Config config = new Config();
        if (content == null) return config;
        int at = content.indexOf("@config");
        if (at < 0) return config;
        int end = content.indexOf("*/", at);
        String block = content.substring(at, end < 0 ? content.length() : end);
        for (String line : block.split("\\R")) {
            String text = line.trim().replaceFirst("^\\*+", "").trim();
            int colon = text.indexOf(':');
            if (colon <= 0) continue;
            String key = text.substring(0, colon).trim();
            String value = text.substring(colon + 1).trim();
            try {
                switch (key) {
                    case "timeout":
                        config.timeout = Integer.parseInt(value);
                        break;
                    case "returnType":
                        config.returnType = value;
                        break;
                    case "blockImages":
                        config.blockImages = Boolean.parseBoolean(value);
                        break;
                    case "debug":
                        config.debug = Boolean.parseBoolean(value);
                        break;
                }
            } catch (Exception ignored) {
            }
        }
        return config;
    }

    /**
     * playerContent 返回值的口径翻译（纯函数，可单测）：
     * 源形态 {type:'parse'|'wvplayer'|其它, url, headers, click, ...} 与
     * Result.error 的 {__wvError} 标记，翻译成星辰 Result 的
     * {parse, url, header, click, msg}；无 url 的未知形态原样透传。
     * wvplayer 的 script/selectors/playerSelector/timeout 在 Result 里没有
     * 对应字段，本轮不透传（url+header+click 已走壳子既有 WebView 解析路）。
     */
    public static String translatePlayerResult(String json) {
        if (json == null || json.isEmpty()) return "{}";
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("__wvError")) {
                JsonObject out = new JsonObject();
                out.addProperty("msg", obj.get("__wvError").getAsString());
                out.addProperty("url", "");
                out.addProperty("parse", 0);
                return out.toString();
            }
            if (!obj.has("url")) return json;
            JsonObject out = new JsonObject();
            out.addProperty("url", obj.get("url").getAsString());
            if (obj.has("headers")) out.add("header", obj.get("headers"));
            else if (obj.has("header")) out.add("header", obj.get("header"));
            String type = obj.has("type") ? obj.get("type").getAsString() : "";
            if ("parse".equals(type) || "wvplayer".equals(type)) out.addProperty("parse", 1);
            else if (obj.has("parse")) out.add("parse", obj.get("parse"));
            else out.addProperty("parse", 0);
            if ("wvplayer".equals(type) && obj.has("click")) out.add("click", obj.get("click"));
            if (obj.has("msg")) out.add("msg", obj.get("msg"));
            return out.toString();
        } catch (Exception e) {
            return json;
        }
    }

    /* ---------- 执行骨架（与 quickjs Spider 同款的单线程＋双段超时口径） ---------- */

    private long timeoutSeconds() {
        return Math.max(60, config.timeout);
    }

    private <T> T submitAndGet(Callable<T> callable) throws Exception {
        if (unresponsive) throw new IllegalStateException("wv spider unresponsive");
        Future<T> future = executor.submit(callable);
        try {
            return future.get(timeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            unresponsive = true;
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private Object call(String func, Object... args) throws Exception {
        if (!entries.contains(func)) return null;
        CompletableFuture<Object> future = submitAndGet(() -> Async.run(globalObj, func, args));
        try {
            return future.get(timeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    /**
     * JSObject 的 release 必须回到执行器线程做：wrapper 的 freeValue 头一步就是
     * 线程亲和校验（checkSameThread），在调用线程上 release 会直接抛
     * QuickJSException("Must be call same thread in QuickJSContext.create!")——
     * 这正是 wvB 类源首页空白的断点（结果对象在 stringifyResult 的 finally 里、
     * 入参对象在各入口的 finally 里被调用线程 release）。
     * 单线程执行器 FIFO 保证 release 排在先前提交的调用/stringify 之后执行，
     * 寿命口径与原地 release 一致，只换线程。执行器已关停（destroy 竞态）时
     * 放弃释放、随 ctx 销毁回收，与 destroy 的既定取舍一致；绝不在当前线程硬放。
     */
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

    /** JS 返回值转 JSON 字符串：对象走 stringify，字符串直通，其余按文本形态。 */
    private String stringifyResult(Object result) throws Exception {
        if (result == null) return "";
        if (result instanceof String) return (String) result;
        if (result instanceof JSObject) {
            JSObject object = (JSObject) result;
            try {
                String json = submitAndGet(object::stringify);
                return json == null ? "" : json;
            } finally {
                releaseArg(object);
            }
        }
        return String.valueOf(result);
    }

    /* ---------- catvod Spider 接口 ---------- */

    @Override
    public void init(Context context, String extend) throws Exception {
        initializeJS();
        if (!entries.contains("init")) return;
        Object ext = submitAndGet(() -> getExt(extend));
        try {
            call("init", ext);
        } finally {
            releaseArg(ext);
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        return stringifyResult(call("homeContent", filter));
    }

    @Override
    public String homeVideoContent() throws Exception {
        return stringifyResult(call("homeVideoContent"));
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        JSObject obj = submitAndGet(() -> JSUtil.toObject(ctx, extend));
        try {
            return stringifyResult(call("categoryContent", tid, pg, filter, obj));
        } finally {
            releaseArg(obj);
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Object array = submitAndGet(() -> JSUtil.toArray(ctx, ids));
        try {
            return stringifyResult(call("detailContent", array));
        } finally {
            releaseArg(array);
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return stringifyResult(call("searchContent", key, quick, pg));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        Object array = submitAndGet(() -> JSUtil.toArray(ctx, vipFlags));
        try {
            return translatePlayerResult(stringifyResult(call("playerContent", flag, id, array)));
        } finally {
            releaseArg(array);
        }
    }

    /** 壳子当前没有 recommend 的消费口，先把通道留好，源里有就调得到。 */
    public String recommendContent(List<String> ids, String pg) throws Exception {
        Object array = submitAndGet(() -> JSUtil.toArray(ctx, ids));
        try {
            return stringifyResult(call("recommendContent", array, pg));
        } finally {
            releaseArg(array);
        }
    }

    @Override
    public String action(String action) throws Exception {
        Object result = call("action", action);
        if (result == null) return null;
        return stringifyResult(result);
    }

    @Override
    public void destroy() {
        if (!unresponsive) {
            try {
                call("destroy");
            } catch (Throwable ignored) {
            }
            try {
                releaseJS();
            } catch (Throwable ignored) {
            }
        }
        executor.shutdownNow();
    }

    private void releaseJS() throws Exception {
        submitAndGet(() -> {
            if (host != null) host.destroy();
            if (ctx != null) ctx.destroy();
            return null;
        });
    }

    /* ---------- 上下文建立 ---------- */

    private void initializeJS() throws Exception {
        submitAndGet(() -> {
            String content = Module.get().fetch(api);
            // 同普通通道：内容取不到时 fetch 回 null，parseConfig 虽容 null，
            // createCtx 的 evaluate(null) 不容——具名失败、上层降级 SpiderNull。
            if (content == null) throw new IllegalStateException("wv source content unavailable: " + api);
            config = parseConfig(content);
            createCtx(content);
            return null;
        });
    }

    private void createCtx(String content) {
        ctx = QuickJSContext.create();
        ctx.setConsole(new Console());
        globalObj = ctx.getGlobalObject();
        String namespace = siteKey != null && !siteKey.isEmpty() ? siteKey : Util.md5(api);
        host = new WvHost(namespace);
        host.bind(ctx);
        JsonObject cfg = new JsonObject();
        cfg.addProperty("timeout", config.timeout);
        cfg.addProperty("returnType", config.returnType);
        cfg.addProperty("blockImages", config.blockImages);
        cfg.addProperty("debug", config.debug);
        ctx.evaluate("globalThis.__wvConfig = " + new Gson().toJson(cfg) + ";");
        ctx.evaluate(Asset.read("js/lib/wv.js"));
        // 经典脚本形态求值：顶层函数/var 声明落到全局，正是这类源的入口约定；
        // 不走 evaluateModule（模块作用域会把入口藏起来）。
        ctx.evaluate(content);
        detectEntries();
    }

    private void detectEntries() {
        StringBuilder script = new StringBuilder("(function(){var o={};");
        for (String name : ENTRIES) script.append("o['").append(name).append("']=typeof globalThis['").append(name).append("']==='function';");
        script.append("return JSON.stringify(o);})()");
        Object result = ctx.evaluate(script.toString());
        if (!(result instanceof String)) return;
        try {
            JsonObject obj = JsonParser.parseString((String) result).getAsJsonObject();
            for (String name : ENTRIES) {
                if (obj.has(name) && obj.get(name).getAsBoolean()) entries.add(name);
            }
        } catch (Exception e) {
            SpiderDebug.log("wvspider", "detect entries failed api=%s error=%s", api, e);
        }
    }

    private Object getExt(String ext) {
        boolean structured = Json.isObj(ext) || Json.isArray(ext);
        return structured ? ctx.parse(ext) : ext;
    }
}
