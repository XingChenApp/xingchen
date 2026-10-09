package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;
import android.util.Base64;

import com.fongmi.android.tv.App;
import com.fongmi.quickjs.bean.Req;
import com.fongmi.quickjs.utils.Connect;
import com.fongmi.quickjs.utils.Crypto;
import com.fongmi.quickjs.utils.JSUtil;
import com.fongmi.quickjs.utils.Parser;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.orhanobut.logger.Logger;
import com.whl.quickjs.android.QuickJSLoader;
import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 海阔 JS 运行时（M2，设计文档 §4.4）。
 *
 * <ul>
 *   <li>每个规则一个独立 QuickJSContext（隔离变量），跑在单线程 executor 上，API 全部同步实现。</li>
 *   <li>注册 Hiker 方言 API：fetch/post、setResult 系列、parseDom 系列、MY_* 变量、putVar/getVar、setItem/getItem、
 *   toast/log/setError、base64、encodeStr/decodeStr、aes、getResCode/getUrl、require。</li>
 *   <li>preRule 在首次 home/search 前各执行一次（主要用途取 cookie）。</li>
 *   <li>实现 {@link HkSelector.JsEvaluator}，给选择器的 {@code .js:} 值加工提供求值能力。</li>
 * </ul>
 *
 * 注意：lazyRule 回调按"独立作用域"铁律设计——HikerApi 只注入全局函数与形参，不依赖 JS 闭包。
 */
public class HkJsRuntime implements HkSelector.JsEvaluator {

    private static final String TAG = "HkJsRuntime";
    private static final Gson GSON = new Gson();
    private static final Type MAP_LIST_TYPE = new TypeToken<List<Map<String, Object>>>() {}.getType();

    static {
        try {
            QuickJSLoader.init();
        } catch (Throwable ignored) {
        }
    }

    private final ExecutorService executor;
    private final HkRule rule;
    private final Parser parser;
    private final Map<String, String> vars;
    private final List<HkItem> results;
    /** 明细原始条目收集（parseDetailRaw 用，保留 line/col_type 等扩展字段）。 */
    private final List<Map<String, String>> rawResults = new ArrayList<>();
    private volatile boolean collectRaw;

    private QuickJSContext ctx;
    private Map<String, String> kv;
    private String error;
    private int resCode;
    private String lastUrl;
    private volatile boolean destroyed;
    private volatile boolean preRuleDone;

    public HkJsRuntime(HkRule rule) {
        this.rule = rule;
        this.executor = Executors.newSingleThreadExecutor();
        this.parser = new Parser();
        this.vars = new HashMap<>();
        this.results = new ArrayList<>();
        this.kv = new HashMap<>();
    }

    private <T> Future<T> submit(java.util.concurrent.Callable<T> callable) {
        return executor.submit(callable);
    }

    /**
     * 在 JS 线程上初始化：建 Context、注册 API、加载 kv、跑 preRule。
     */
    public void init() throws Exception {
        submit(() -> {
            ctx = QuickJSContext.create();
            registerApi();
            loadKv();
            runPreRule();
            return null;
        }).get();
    }

    // ================= API 注册 =================

    private void registerApi() {
        // ---- 结果返回 ----
        ctx.getGlobalObject().setProperty("setResult", args -> {
            collectResult(args);
            return null;
        });
        ctx.getGlobalObject().setProperty("setHomeResult", args -> {
            collectResult(args);
            return null;
        });
        ctx.getGlobalObject().setProperty("setSearchResult", args -> {
            collectResult(args);
            return null;
        });
        ctx.getGlobalObject().setProperty("setError", args -> {
            error = args != null && args.length > 0 ? String.valueOf(args[0]) : "";
            Logger.t(TAG).d("setError: %s", error);
            return null;
        });

        // ---- 网络 ----
        ctx.getGlobalObject().setProperty("fetch", args -> {
            if (args == null || args.length == 0) return "";
            String url = String.valueOf(args[0]);
            String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : null;
            return fetchSync(url, options);
        });
        ctx.getGlobalObject().setProperty("post", args -> {
            if (args == null || args.length == 0) return "";
            String url = String.valueOf(args[0]);
            String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : "{}";
            options = mergeMethod(options, "post");
            return fetchSync(url, options);
        });
        ctx.getGlobalObject().setProperty("getResCode", args -> resCode);
        ctx.getGlobalObject().setProperty("getUrl", args -> lastUrl == null ? "" : lastUrl);

        // ---- DOM 解析（复用 Parser，即海阔 parseHikerToJq 路径） ----
        ctx.getGlobalObject().setProperty("parseDom", args -> {
            if (args == null || args.length < 2) return "";
            String urlKey = args.length > 2 && args[2] != null ? String.valueOf(args[2]) : (lastUrl == null ? "" : lastUrl);
            return parser.pdfh(String.valueOf(args[0]), String.valueOf(args[1]), urlKey);
        });
        ctx.getGlobalObject().setProperty("pd", args -> {
            if (args == null || args.length < 2) return "";
            String urlKey = args.length > 2 && args[2] != null ? String.valueOf(args[2]) : (lastUrl == null ? "" : lastUrl);
            return parser.pdfh(String.valueOf(args[0]), String.valueOf(args[1]), urlKey);
        });
        ctx.getGlobalObject().setProperty("parseDomForHtml", args -> {
            if (args == null || args.length < 2) return "";
            return parser.pdfh(String.valueOf(args[0]), String.valueOf(args[1]), "");
        });
        ctx.getGlobalObject().setProperty("pdfh", args -> {
            if (args == null || args.length < 2) return "";
            return parser.pdfh(String.valueOf(args[0]), String.valueOf(args[1]), "");
        });
        ctx.getGlobalObject().setProperty("parseDomForArray", args -> {
            if (args == null || args.length < 2) return JSUtil.toArray(ctx, new ArrayList<>());
            List<String> items = parser.pdfa(String.valueOf(args[0]), String.valueOf(args[1]));
            return JSUtil.toArray(ctx, items);
        });
        ctx.getGlobalObject().setProperty("pdfa", args -> {
            if (args == null || args.length < 2) return JSUtil.toArray(ctx, new ArrayList<>());
            List<String> items = parser.pdfa(String.valueOf(args[0]), String.valueOf(args[1]));
            return JSUtil.toArray(ctx, items);
        });

        // ---- 存储 ----
        ctx.getGlobalObject().setProperty("putVar", args -> {
            if (args != null && args.length > 1) vars.put(String.valueOf(args[0]), String.valueOf(args[1]));
            return null;
        });
        ctx.getGlobalObject().setProperty("getVar", args -> {
            if (args == null || args.length == 0) return "";
            String v = vars.get(String.valueOf(args[0]));
            if (v == null && args.length > 1) v = String.valueOf(args[1]);
            return v == null ? "" : v;
        });
        ctx.getGlobalObject().setProperty("setItem", args -> {
            if (args != null && args.length > 1) {
                kv.put(String.valueOf(args[0]), String.valueOf(args[1]));
                saveKv();
            }
            return null;
        });
        ctx.getGlobalObject().setProperty("getItem", args -> {
            if (args == null || args.length == 0) return "";
            String v = kv.get(String.valueOf(args[0]));
            if (v == null && args.length > 1) v = String.valueOf(args[1]);
            return v == null ? "" : v;
        });

        // ---- 调试 ----
        ctx.getGlobalObject().setProperty("toast", args -> {
            final String msg = args != null && args.length > 0 ? String.valueOf(args[0]) : "";
            Logger.t(TAG).d("toast: %s", msg);
            try {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        android.widget.Toast.makeText(App.get(), msg, android.widget.Toast.LENGTH_SHORT).show());
            } catch (Throwable ignored) {
            }
            return null;
        });
        ctx.getGlobalObject().setProperty("log", args -> {
            Logger.t(TAG).d("%s", args != null && args.length > 0 ? String.valueOf(args[0]) : "");
            return null;
        });

        // ---- 编解码 ----
        ctx.getGlobalObject().setProperty("base64Encode", args -> {
            if (args == null || args.length == 0) return "";
            return Util.base64(String.valueOf(args[0]).getBytes(Charset.forName("UTF-8")));
        });
        ctx.getGlobalObject().setProperty("base64Decode", args -> {
            if (args == null || args.length == 0) return "";
            try {
                return new String(Base64.decode(String.valueOf(args[0]), Base64.DEFAULT), Charset.forName("UTF-8"));
            } catch (Throwable e) {
                return "";
            }
        });
        ctx.getGlobalObject().setProperty("encodeStr", args -> {
            if (args == null || args.length == 0) return "";
            try {
                String charset = args.length > 1 ? String.valueOf(args[1]) : "UTF-8";
                return Util.base64(String.valueOf(args[0]).getBytes(Charset.forName(charset)));
            } catch (Throwable e) {
                return "";
            }
        });
        ctx.getGlobalObject().setProperty("decodeStr", args -> {
            if (args == null || args.length == 0) return "";
            try {
                String charset = args.length > 1 ? String.valueOf(args[1]) : "UTF-8";
                return new String(Base64.decode(String.valueOf(args[0]), Base64.DEFAULT), Charset.forName(charset));
            } catch (Throwable e) {
                return "";
            }
        });
        ctx.getGlobalObject().setProperty("aesEncode", args -> {
            if (args == null || args.length < 2) return "";
            String key = String.valueOf(args[1]);
            String iv = args.length > 2 ? String.valueOf(args[2]) : "";
            return Crypto.aes("AES/CBC/PKCS5Padding", true, String.valueOf(args[0]), false, key, iv, true);
        });
        ctx.getGlobalObject().setProperty("aesDecode", args -> {
            if (args == null || args.length < 2) return "";
            String key = String.valueOf(args[1]);
            String iv = args.length > 2 ? String.valueOf(args[2]) : "";
            return Crypto.aes("AES/CBC/PKCS5Padding", false, String.valueOf(args[0]), true, key, iv, false);
        });

        // ---- require：远程库加载（preRule 常用） ----
        ctx.getGlobalObject().setProperty("require", args -> {
            if (args == null || args.length == 0) return null;
            try {
                String libUrl = String.valueOf(args[0]);
                String code = fetchSync(libUrl, null);
                if (!TextUtils.isEmpty(code)) ctx.evaluate(code);
            } catch (Throwable e) {
                Logger.t(TAG).d("require failed: %s", e.getMessage());
            }
            return null;
        });
    }

    // ================= 对外接口 =================

    /**
     * 执行首页/分类的 js: 规则，返回条目列表。
     */
    public List<HkItem> parseList(String jsCode, String myUrl) throws Exception {
        return submit(() -> {
            results.clear();
            error = null;
            setContext(myUrl);
            ctx.evaluate(stripJsPrefix(jsCode));
            return drainResults();
        }).get();
    }

    /**
     * 执行搜索的 js: 规则，返回条目列表。
     */
    public List<HkItem> parseSearch(String jsCode, String myUrl, String keyword) throws Exception {
        return submit(() -> {
            results.clear();
            error = null;
            setContext(myUrl);
            ctx.getGlobalObject().setProperty("MY_KEYWORD", keyword == null ? "" : keyword);
            ctx.evaluate(stripJsPrefix(jsCode));
            return drainResults();
        }).get();
    }

    /**
     * HkSelector.JsEvaluator 实现：选择器 .js: 值加工。
     * 把取值结果作为全局 input，执行 JS 片段并返回结果字符串。
     */
    @Override
    public String eval(String js, String input) {
        try {
            return submit(() -> {
                ctx.getGlobalObject().setProperty("input", input == null ? "" : input);
                Object r = ctx.evaluate(js);
                return r == null ? "" : String.valueOf(r);
            }).get();
        } catch (Exception e) {
            Logger.t(TAG).d("evalJs failed: %s", e.getMessage());
            return input == null ? "" : input;
        }
    }

    /**
     * 给 HkSelector 配一个带 JS 求值能力的实例（M1 的 null 透传升级为真求值）。
     */
    public HkSelector newSelector() {
        HkSelector selector = new HkSelector();
        selector.setJsEvaluator(this);
        return selector;
    }

    public String getError() {
        return error;
    }

    public void destroy() {
        destroyed = true;
        try {
            submit(() -> {
                try {
                    if (ctx != null) ctx.destroy();
                } catch (Throwable ignored) {
                }
                return null;
            }).get();
        } catch (Throwable ignored) {
        } finally {
            executor.shutdownNow();
        }
    }

    // ================= 内部实现 =================

    private void setContext(String myUrl) {
        lastUrl = myUrl == null ? "" : myUrl;
        ctx.getGlobalObject().setProperty("MY_URL", lastUrl);
        ctx.getGlobalObject().setProperty("MY_HOME", homeOf(lastUrl));
        try {
            ctx.getGlobalObject().setProperty("MY_RULE", ctx.parse(GSON.toJson(rule)));
        } catch (Throwable e) {
            ctx.getGlobalObject().setProperty("MY_RULE", ctx.createNewJSObject());
        }
    }

    private static String homeOf(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
            String host = uri.getHost() == null ? "" : uri.getHost();
            int port = uri.getPort();
            return scheme + "://" + host + (port > 0 ? ":" + port : "");
        } catch (Throwable e) {
            return url;
        }
    }

    private static String stripJsPrefix(String code) {
        if (code == null) return "";
        String t = code.trim();
        return t.startsWith("js:") ? t.substring(3) : t;
    }

    private void runPreRule() {
        if (preRuleDone) return;
        preRuleDone = true;
        String pre = rule.getPreRule();
        if (TextUtils.isEmpty(pre) || TextUtils.isEmpty(pre.trim())) return;
        try {
            setContext(HkHttp.expandUrl(rule.getUrl(), "", "", "", "", 1));
            ctx.evaluate(pre.trim().startsWith("js:") ? pre.trim().substring(3) : pre);
            Logger.t(TAG).d("preRule done for %s", rule.getTitle());
        } catch (Throwable e) {
            Logger.t(TAG).d("preRule failed for %s: %s", rule.getTitle(), e.getMessage());
        }
    }

    private void collectResult(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) return;
        try {
            String json = args[0] instanceof JSArray
                    ? ((JSArray) args[0]).stringify()
                    : String.valueOf(args[0]);
            List<Map<String, Object>> list = GSON.fromJson(json, MAP_LIST_TYPE);
            if (list == null) return;
            for (Map<String, Object> m : list) {
                if (collectRaw) {
                    Map<String, String> raw = new HashMap<>();
                    for (Map.Entry<String, Object> e : m.entrySet()) {
                        raw.put(e.getKey(), e.getValue() == null ? "" : String.valueOf(e.getValue()));
                    }
                    rawResults.add(raw);
                    continue;
                }
                HkItem item = new HkItem();
                item.setTitle(str(m, "title"));
                item.setUrl(str(m, "url"));
                String pic = str(m, "pic_url");
                if (TextUtils.isEmpty(pic)) pic = str(m, "img");
                if (TextUtils.isEmpty(pic)) pic = str(m, "pic");
                item.setPic(pic);
                item.setDesc(str(m, "desc"));
                results.add(item);
            }
        } catch (Throwable e) {
            Logger.t(TAG).d("collectResult failed: %s", e.getMessage());
        }
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private List<HkItem> drainResults() {
        List<HkItem> out = new ArrayList<>(results);
        results.clear();
        return out;
    }

    /**
     * 执行详情的 js: 规则，返回原始条目（含 line/col_type 等扩展字段，M3）。
     * setResult 项约定：title/url/pic_url/desc + 可选 line（线路名）/col_type。
     */
    public List<Map<String, String>> parseDetailRaw(String jsCode, String myUrl) throws Exception {
        return submit(() -> {
            rawResults.clear();
            results.clear();
            error = null;
            collectRaw = true;
            try {
                setContext(myUrl);
                ctx.evaluate(stripJsPrefix(jsCode));
            } finally {
                collectRaw = false;
            }
            List<Map<String, String>> out = new ArrayList<>(rawResults);
            rawResults.clear();
            results.clear();
            return out;
        }).get();
    }

    private String stringifyArg(Object arg) {
        try {
            if (arg instanceof JSObject) return ((JSObject) arg).stringify();
        } catch (Throwable ignored) {
        }
        return String.valueOf(arg);
    }

    private static String mergeMethod(String optionsJson, String method) {
        try {
            Map<String, String> map = Json.toMap(optionsJson);
            if (map == null) map = new HashMap<>();
            map.put("method", method);
            return GSON.toJson(map);
        } catch (Throwable e) {
            return "{\"method\":\"" + method + "\"}";
        }
    }

    /**
     * 同步 fetch（跑在 JS 单线程上，直接阻塞等待）。
     * options 为 JSON：{headers, body/data, method, timeout, withHeaders}。
     */
    private String fetchSync(String url, String optionsJson) {
        if (TextUtils.isEmpty(url)) return "";
        // hiker://empty 等非 http 协议直接返回空（由上层按形态分流）
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "";
        try {
            Req req = TextUtils.isEmpty(optionsJson) ? Req.objectFrom("{}") : Req.objectFrom(optionsJson);
            boolean withHeaders = optionsJson != null && optionsJson.contains("\"withHeaders\"")
                    && (optionsJson.contains("\"withHeaders\":true") || optionsJson.contains("\"withHeaders\":1"));
            try (Response res = Connect.to(url, req).execute()) {
                resCode = res.code();
                lastUrl = url;
                ResponseBody body = res.body();
                byte[] bytes = body == null ? new byte[0] : body.bytes();
                String content = new String(bytes, req.getCharset());
                if (!withHeaders) return content;
                Map<String, Object> out = new HashMap<>();
                out.put("body", content);
                Map<String, String> headers = new HashMap<>();
                for (String name : res.headers().names()) headers.put(name, res.header(name, ""));
                out.put("headers", headers);
                out.put("code", res.code());
                return GSON.toJson(out);
            }
        } catch (Throwable e) {
            Logger.t(TAG).d("fetch failed %s: %s", url, e.getMessage());
            resCode = 0;
            return "";
        }
    }

    // ================= setItem/getItem 持久化 =================

    private File kvFile() {
        File dir = HkRuleManager.get().getDataDir(rule.getTitle());
        return new File(dir, "kv.json");
    }

    private void loadKv() {
        try {
            File f = kvFile();
            if (!f.exists()) return;
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> map = GSON.fromJson(new String(bytes, "UTF-8"), type);
            if (map != null) kv.putAll(map);
        } catch (Throwable ignored) {
        }
    }

    private void saveKv() {
        try {
            File f = kvFile();
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            java.nio.file.Files.write(f.toPath(), GSON.toJson(kv).getBytes("UTF-8"));
        } catch (Throwable ignored) {
        }
    }
}
