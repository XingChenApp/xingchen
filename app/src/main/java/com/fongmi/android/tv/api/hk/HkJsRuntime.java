package com.fongmi.android.tv.api.hk;
import com.fongmi.android.tv.utils.ToastUtil;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.text.TextUtils;
import android.util.Base64;

import com.fongmi.android.tv.App;
import com.fongmi.quickjs.bean.Req;
import com.fongmi.quickjs.utils.Crypto;
import com.fongmi.quickjs.utils.Parser;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.SourceGate;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.orhanobut.logger.Logger;
import org.greenrobot.eventbus.EventBus;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ConsString;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.NativeJavaArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Response;
import okhttp3.ResponseBody;
import android.os.Environment;
import java.net.URI;
import java.util.UUID;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.GeneratedClassLoader;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.SecurityController;
import org.mozilla.javascript.commonjs.module.provider.StrongCachingModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.provider.UrlModuleSourceProvider;

/**
 * 海阔 JS 运行时（M2，设计文档 §4.4）。
 *
 * <ul>
 *   <li>每个规则一个独立 Rhino Context/Scope（隔离变量），跑在单线程 executor 上，API 全部同步实现。</li>
 *   <li>注册 Hiker 方言 API：fetch/post、setResult 系列、parseDom 系列、MY_* 变量、putVar/getVar、setItem/getItem、
 *   toast/log/setError、base64、encodeStr/decodeStr、aes、getResCode/getUrl、require。</li>
 *   <li>preRule 在首次 home/search 前各执行一次（主要用途取 cookie）。</li>
 *   <li>实现 {@link HkSelector.JsEvaluator}，给选择器的 {@code .js:} 值加工提供求值能力。</li>
 * </ul>
 *
 * 注意：lazyRule 回调按"独立作用域"铁律设计——HikerApi 只注入全局函数与形参，不依赖 JS 闭包。
 */
public class HkJsRuntime implements HkSelector.JsEvaluator, Parser.JsEvaluator {

    private static final String TAG = "HkJsRuntime";
    // 聚阅借名垫片用的路径常量（原文 SrcJuMethod.js:2-8，ea2ea7f49c1d87b8d19784fa4206921f.js）。
    // 只垫被跨文件借用的；cachepath/casefile 只在方法库内部用，不垫。
    private static final String JUYUE_LIBSPATH = "hiker://files/data/聚阅/";
    private static final String JUYUE_RULEPATH = "hiker://files/rules/Src/Juyue/";
    private static final String JUYUE_JKFILESPATH = "hiker://files/rules/Src/Juyue/jiekou/";
    private static final String JUYUE_JKFILE = "hiker://files/rules/Src/Juyue/jiekou.json";
    private static final String JUYUE_CFGFILE = "hiker://files/rules/Src/Juyue/config.json";
    private static final String JUYUE_SORTFILE = "hiker://files/rules/Src/Juyue/jksort.json";
    /** be()/batchExecute 真实实现后置 true，跨小程序内容搜索据此判断引擎是否就绪。 */
    public static volatile boolean BATCH_EXECUTE_READY = false;
    private static final Gson GSON = new Gson();
    private static final Type MAP_LIST_TYPE = new TypeToken<List<Map<String, Object>>>() {}.getType();

    // ---- JS 看门狗 ----
    // 2026-10-13 真机实锤：1.2.6 里 $.require 修好后，规则 preRule 第一次能真正跑进去，
    // 一旦 JS 进入死循环（拉不到数据无限重试等），解释模式的 Rhino 没有指令观察者就
    // 永远不抛异常、永远不结束——init 的 submit().get() 无限等待，页面永远转圈、
    // 日志里只有 "load start" 再无下文。这里装全局 ContextFactory：每次经
    // evalJs/evalJsInSharedScope 求值前在 Context 上放一个截止时间，指令观察者
    // 越过截止就抛 Error 打断脚本（经 submit/get 转成 ExecutionException 被上层接住）。
    private static final String DEADLINE_KEY = "hk.eval.deadline";
    // 预算口径（2026-10-27 对差补缺）：8.83 无指令看门狗、evalJS 同步跑完为止。此预算是
    // 2026-10-13 死循环把 init 堵死后的兜底网，不能撤；但 20 秒是墙钟、把求值内的网络
    // 等待也算进去，慢而正当的规则（多站串行取数）会被误杀、原版能跑完。故放宽到 120 秒：
    // 正当慢规则跑得完，真死循环仍有网兜底（代价是死循环页最长转 120 秒才失败）。
    private static final long EVAL_BUDGET_MS = 120_000L;

    private static class HkContextFactory extends ContextFactory {
        @Override
        protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setInstructionObserverThreshold(10_000);
            return cx;
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            Object d = cx.getThreadLocal(DEADLINE_KEY);
            if (d instanceof Long && System.currentTimeMillis() > (Long) d) {
                throw new Error("hk js eval timeout (" + EVAL_BUDGET_MS + "ms)");
            }
        }
    }

    static {
        try {
            if (!ContextFactory.hasExplicitGlobal()) ContextFactory.initGlobal(new HkContextFactory());
        } catch (Throwable ignored) {
        }
    }

    /** 给当前 Context 装上求值截止时间，返回旧值供嵌套求值恢复。 */
    private static Object armEvalDeadline() {
        try {
            Context cx = Context.getCurrentContext();
            if (cx == null) return null;
            Object old = cx.getThreadLocal(DEADLINE_KEY);
            cx.putThreadLocal(DEADLINE_KEY, System.currentTimeMillis() + EVAL_BUDGET_MS);
            return old;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void restoreEvalDeadline(Object old) {
        try {
            Context cx = Context.getCurrentContext();
            if (cx == null) return;
            if (old == null) cx.removeThreadLocal(DEADLINE_KEY);
            else cx.putThreadLocal(DEADLINE_KEY, old);
        } catch (Throwable ignored) {
        }
    }

    private final ExecutorService executor;
    /**
     * 当前页规则绑定（块一 W3 口径）：8.83 JSEngine 是进程单例且不持有规则字段，
     * 每次求值由入口把「当前页规则」带入现拼上下文（getMyRule(rule) 前缀）。
     * 星辰常驻运行时按 title 复用实例，此字段只作未带规则入口的当前绑定，
     * 由 {@link #bindRule(HkRule)} 在每次取用时按当前页规则重绑——绝不允许
     * 停留在实例创建时的旧规则对象（子规则与父规则同 title 时即为首页规则）。
     */
    private volatile HkRule rule;

    /**
     * 带规则入口的串行锁：bind 在调用线程写 this.rule、求值在引擎单线程上
     * 异步执行时才读——两页并发取用同一常驻实例时，后一次 bind 可能赶在
     * 前一次求值执行前把 rule 换掉（串页）。带规则入口持此锁走完
     * bind→求值全程（求值本身阻塞等结果，锁只多串行化并发入口），
     * 再叠加各委托在提交时捕获规则、任务开头重申（见 parseList 等），
     * 双层关掉这个窗口。
     */
    private final Object ruleEvalLock = new Object();
    private final Parser parser;
    /** JS 单线程 executor 的 worker 线程（.js: 回调时判断是否已在 JS 线程，避免死锁） */
    private volatile Thread jsThread;
    private final Map<String, String> vars;
    private final List<HkItem> results;
    /** 明细原始条目收集（parseDetailRaw 用，保留 line/col_type 等扩展字段）。 */
    private final List<Map<String, String>> rawResults = new ArrayList<>();
    private volatile boolean collectRaw;
    /** 规则内会话变量（getMyVar/putMyVar，官方 key 形态为 ruleTitle@key；单规则单实例，等价）。
     * D3：图片求值走调用线程并发后，图片单例的变量表可能被多线程同时读写，用并发 map（单线程语义不变）。 */
    private final Map<String, String> myVars = new java.util.concurrent.ConcurrentHashMap<>();

    /** Java 侧设置规则变量（供 HkPageActivity 等调用）。 */
    public void putMyVar(String key, String value) {
        if (key != null) myVars.put(myVarKey(key), value == null ? "" : value);
    }

    /** Java 侧读取规则变量。 */
    public String getMyVar(String key) {
        String v = myVars.get(myVarKey(key));
        return v == null ? "" : v;
    }

    /**
     * E8: myVar key 加 ruleTitle@ 前缀做规则间隔离（8.83 JSEngine.smali:21694）。
     * 若 key 已带 @ 则直接用（兼容旧数据）。
     */
    private String myVarKey(String key) {
        return myVarKeyFor(key, null);
    }

    /** W4：8.83 getRuleTitle(ruleTitleKey)（smali:17390）：空则取当前规则标题，否则用给定标题。 */
    private String myVarKeyFor(String key, String ruleTitleKey) {
        if (key == null) return "";
        if (key.contains("@")) return key;
        String title;
        if (ruleTitleKey != null && !ruleTitleKey.isEmpty()) title = ruleTitleKey;
        else {
            try {
                title = rule != null && rule.getTitle() != null ? rule.getTitle() : "";
            } catch (Throwable ignored) { title = ""; }
        }
        return title + "@" + key;
    }

    /** W4：跨规则变量表定位。无第三参回本规则表；有则查对方规则的常驻运行时，
     * 对方没建过运行时按"没有"处理（不触发创建，避免副作用）。 */
    private Map<String, String> myVarsFor(String ruleTitleKey) {
        if (ruleTitleKey == null || ruleTitleKey.isEmpty()) return myVars;
        try {
            HkRule other = HkRuleManager.get().getInstalledRule(ruleTitleKey);
            if (other == null) return myVars;
            HkJsRuntime rt = com.fongmi.android.tv.ui.hk.HkEngineManager.runtimeIfPresent(other);
            if (rt == null) return myVars;
            return rt.myVars;
        } catch (Throwable ignored) {
            return myVars;
        }
    }
    /** 跨规则公共持久化（setPublicItem/getPublicItem）。D3：并发图片求值可能同时读写，用并发 map。 */
    private final Map<String, String> publicKv = new java.util.concurrent.ConcurrentHashMap<>();
    /** addListener 注册的事件回调（事件名 → 回调描述）。 */
    private final Map<String, String> listeners = new HashMap<>();
    /** fc/rc 的内存缓存（url → 数据+过期时间）。 */
    private final Map<String, MemCache> memCache = new HashMap<>();
    /** setPreResult 预返回 staging：下一记 setResult/setSearchResult 先清空。 */
    private boolean preResultActive;
    /** refreshPage 请求标记（JS 线程写，宿主线程读后清零）。 */
    private volatile boolean refreshRequested;
    private volatile boolean refreshToTop;
    /** refreshPage 节流（照官方 canTouchUI 的 200ms）：上次发刷新事件的时间。 */
    private volatile long lastRefreshPageAt;
    /** goTo(url) 导航请求（JS 线程写，宿主线程读后清零）。规则如 xhotvideo 用 goTo('hiker://page/detail?...') 跳 V4。 */
    private volatile String goToUrl;
    /** setPageTitle/getPageTitle 的页标题状态。 */
    private String pageTitle = "";
    /** setPagePicUrl：官方 SetPagePicEvent 语义，宿主存字段供上层取用。 */
    private String pagePicUrl = "";
    /** setLastChapterRule：官方存最新章节规则，宿主存字段供上层取用。 */
    private String lastChapterRule = "";
    /** setStrResult/setLastChapterResult：官方 JS 回调字符串结果，宿主存字段供上层取用。 */
    private volatile String strResult = "";
    /**
     * 当前求值入口的类型（与注入的 var MY_TYPE 一致）：parseHome→home、parseLastChapterRule→lastChapter、
     * parseSearch/parseSearchRes→search、eval 系→eval、preEngine/preHome/string 同名。
     * 供 setResult 的 1 参调用自动路由（8.83 语义），以及 JS 函数 getMyType() 取值。
     * JS 求值走单线程串行，volatile 足够。
     */
    private volatile String currentMyType = "";
    /** 8.83 confirm 弹框接缝：宿主（webview 上下文）设置后，规则调 confirm() 弹框走回调。 */
    public interface ConfirmHandler {
        void onConfirm(String title, String content, String confirmJs, String cancelJs);
    }

    private volatile ConfirmHandler confirmHandler;

    public void setConfirmHandler(ConfirmHandler h) {
        this.confirmHandler = h;
    }

    /** 求值 confirm 回调 JS（8.83 ArticleListIsland.lambda$confirm$1 同款：evalJS 回调串，结果走路由）。 */
    /** 从规则传入的对象上取字符串属性（confirm 弹窗用）。 */
    private static String scriptableString(Scriptable o, String key) {
        try {
            Object v = ScriptableObject.getProperty(o, key);
            if (v == null || v == Scriptable.NOT_FOUND || v instanceof Undefined) return "";
            return Context.toString(v);
        } catch (Throwable t) {
            return "";
        }
    }

    /** 取回调属性：字符串原样；函数对象取源码包成立即调用式，供 evalConfirmJs 求值。 */
    private static String scriptableCallback(Scriptable o, String key) {
        try {
            Object v = ScriptableObject.getProperty(o, key);
            if (v == null || v == Scriptable.NOT_FOUND || v instanceof Undefined) return "";
            if (v instanceof CharSequence) return v.toString();
            if (v instanceof org.mozilla.javascript.Function) {
                String src = ((org.mozilla.javascript.Function) v).toString();
                if (src != null && !src.trim().isEmpty()) return "(" + src + ")()";
            }
            return Context.toString(v);
        } catch (Throwable t) {
            return "";
        }
    }

    public String evalConfirmJs(String js) {
        try {
            if (js == null) return "";
            return evalJS(js, "", true);
        } catch (Throwable t) {
            return "";
        }
    }
    /** 当前页码（MY_PAGE 注入用）。 */
    private int page = 1;
    /** 列表页上下文（MY_TYPE/MY_CLASS_URL/MY_CLASS_NAME/MY_PARAMS/MY_AREA/MY_YEAR/MY_SORT 注入用）。 */
    private String myType = "";
    private String myClassUrl = "";
    private String myClassName = "";
    private String myParams = "";
    private String myArea = "";
    private String myYear = "";
    private String mySort = "";

    /** 8.83 AES_DEFAULT_KEY（JSEngine.java:162）：getPrivateJS/evalPrivateJS 共用此密钥。 */
    private static final String AES_DEFAULT_KEY = "hk6666666109";
    private static final String UA_MOBILE = "Mozilla/5.0 (Linux; Android 15; 2407FRK8EC Build/AP3A.240617.008; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.127 Mobile Safari/537.36";
    private static final String UA_PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    /** 内存缓存条目。 */
    private static class MemCache {
        final String data;
        final long expireAt;
        MemCache(String data, long expireAt) {
            this.data = data;
            this.expireAt = expireAt;
        }
        boolean alive() {
            return System.currentTimeMillis() < expireAt;
        }
    }

    private Context rhinoCx;
    private ScriptableObject scope;
    // 新鲜作用域补注册用：非空时 putFunction 改写到该作用域（见 registerApiInto）。
    private ScriptableObject apiRegTarget;
    private final Object apiRegisterLock = new Object();
    // storage0 壳只建一次（数据在 vars/myVars/kv 里），各作用域共用同一壳。
    private ScriptableObject sharedStorage0;
    private Map<String, String> kv;
    private String error;
    /** preRule 执行失败的根因（动态域名类规则排障用）；parseList 会清 error，本字段独立保留。 */
    private String preRuleError;
    private int resCode;
    /** @rule= js: 预检时设置的响应正文（HkHttp.get 取回），供 getResCode() 返回；用后清零。 */
    private String evalResCodeBody;
    private String lastUrl;
    /** 网络诊断（排障用）：最近一次 fetch 异常/非2xx/空数据的脱敏信息，供错误弹窗展示。只记录，不改业务逻辑。 */
    private static volatile String lastFetchDiag = "";
    /** 供 HkErrorUtil 在"错误详细信息"里展示最近一次网络诊断。 */
    public static String getLastFetchDiag() { return lastFetchDiag; }
    /** 脱敏：只取 host，不带 path/query（避免 token 泄露）。 */
    private static String hostOfUrl(String url) {
        try {
            String u = url == null ? "" : url.trim();
            int p = u.indexOf("://");
            String rest = p >= 0 ? u.substring(p + 3) : u;
            int q = rest.indexOf('?');
            if (q >= 0) rest = rest.substring(0, q);
            int slash = rest.indexOf('/');
            if (slash >= 0) rest = rest.substring(0, slash);
            int at = rest.lastIndexOf('@');
            if (at >= 0) rest = rest.substring(at + 1);
            return rest.isEmpty() ? "(unknown)" : rest;
        } catch (Throwable t) {
            return "(unknown)";
        }
    }
    private static String hostOfQuiet(String url) {
        try {
            String h = java.net.URI.create(url.trim()).getHost();
            return h == null ? "" : h;
        } catch (Throwable t) {
            return "";
        }
    }

    /** 路径形状（脱敏）：数字串压成 #、长串压成 *，只留结构与参数名，供与原版地址对照。 */
    public static String maskedPath(String url) {
        try {
            java.net.URI u = java.net.URI.create(url.trim());
            String path = u.getPath() == null ? "" : u.getPath();
            path = path.replaceAll("\\d+", "#").replaceAll("[A-Za-z0-9_\\-]{12,}", "*");
            String q = u.getQuery();
            if (q != null && !q.isEmpty()) {
                StringBuilder sb = new StringBuilder(path).append('?');
                String[] parts = q.split("&");
                for (int i = 0; i < parts.length && i < 6; i++) {
                    if (i > 0) sb.append('&');
                    int eq = parts[i].indexOf('=');
                    sb.append(eq >= 0 ? parts[i].substring(0, eq) : parts[i]).append("=*");
                }
                return sb.toString();
            }
            return path;
        } catch (Throwable t) {
            return "";
        }
    }

    private static void recordFetchDiag(String url, int code, Throwable e, boolean emptyBody) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("host=").append(hostOfUrl(url));
            sb.append(" code=").append(code);
            if (e != null) {
                String msg = e.getMessage();
                sb.append(" err=").append(e.getClass().getSimpleName());
                if (!TextUtils.isEmpty(msg)) {
                    String m = msg.length() > 160 ? msg.substring(0, 160) : msg;
                    sb.append(": ").append(m.replace('\n', ' '));
                }
            } else if (code < 200 || code >= 300) {
                sb.append(" httpError");
            } else if (emptyBody) {
                sb.append(" bodyEmpty");
            }
            lastFetchDiag = sb.toString();
            // 同步写进调试日志文件（此前只喂屏幕弹窗，日志里查不到海阔的失败）
            com.github.catvod.crawler.SpiderDebug.log("hk", "fetch %s", lastFetchDiag);
        } catch (Throwable ignored) {
        }
    }
    private volatile boolean destroyed;
    private volatile boolean preRuleDone;
    /**
     * 入口口令/输入框一次性 input 覆盖（官方 ArticleListFragment.clickItem 语义）：
     * input 类型条目点击时，把用户在 EditText 里输入的文本作为 input 全局变量注入，
     * 而不是默认的 pageUrl。set 后仅下一次 evalLazy 生效，读后清零。
     */
    private volatile String nextInputOverride;
    /** JS 定时器（setTimeout/setInterval）：id → ScheduledFuture；回调统一提交回 JS 单线程执行。 */
    private final Map<Integer, ScheduledFuture<?>> jsTimers = new HashMap<>();
    private final ScheduledExecutorService timerScheduler = Executors.newScheduledThreadPool(1);
    private final AtomicInteger timerIdSeq = new AtomicInteger(1);
    /** 8.83 registerTask 定时任务：taskId(String) → ScheduledFuture；JS 字符串延迟单次执行。 */
    private final Map<String, ScheduledFuture<?>> jsTasks = new HashMap<>();

    /** QuickJS 风格的函数签名（Object[] args -> Object），供 putFunction 注册。 */
    private interface JsFunc {
        Object call(Object[] args);
    }

    /**
     * 扫码宿主回调（8.83 JSEngine.startQRScanPage:25977 对齐）。
     * main 包不能引用 mobile 的 HkPageActivity/ScanActivity，由宿主 Activity 实现并注册。
     */
    public interface QrScanHost {
        void startQrScan();
    }

    private static volatile QrScanHost qrScanHost;

    public static void setQrScanHost(QrScanHost host) {
        qrScanHost = host;
    }

    /**
     * 把 lambda 注册为 scope 上的全局函数（Rhino BaseFunction）。
     * 8.83 官方语义（JSEngine.smali）：桥接函数内不使用 Context.javaToJS
     * （22 处调用全在 runScript/evalJS 的 scope 全局注入）；Java 返回值靠 Rhino
     * LiveConnect 自动包装。Java null 直接返回（Rhino 转为 JS null，
     * 与官方 STRING/OBJECT 模板的 null 语义一致），不转 undefined。
     */
    private void putFunction(String name, JsFunc fn) {
        putFunction(apiRegTarget != null ? apiRegTarget : scope, name, fn);
    }

    private void putFunction(ScriptableObject target, String name, JsFunc fn) {
        putFunctionEx(target, name, (cx, scope, args) -> fn.call(args));
    }

    /**
     * 需要 Rhino 调用方作用域的函数签名（原版 require0 语义：库代码经回调在调用方
     * 作用域链里求值，库顶层声明对调用方可见、调用方声明对库可见）。
     */
    private interface JsFuncEx {
        Object call(Context cx, Scriptable scope, Object[] args);
    }

    private void putFunctionEx(String name, JsFuncEx fn) {
        putFunctionEx(apiRegTarget != null ? apiRegTarget : scope, name, fn);
    }

    private void putFunctionEx(ScriptableObject target, String name, JsFuncEx fn) {
        ScriptableObject.putProperty(target, name, new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                Object[] a = args == null ? new Object[0] : args;
                for (int i = 0; i < a.length; i++) {
                    if (a[i] == Context.getUndefinedValue()) a[i] = null;
                }
                try {
                    return fn.call(cx, scope, a);
                } catch (org.mozilla.javascript.RhinoException e) {
                    throw e; // 已是 JS 侧错误，原样穿桥
                } catch (RuntimeException e) {
                    // 桥面异常收口（全量宿主 API 只此一处出口）：宿主 lambda 抛的裸
                    // RuntimeException 必须裹成 WrappedException 才算穿过桥——Rhino 的
                    // JS try/catch 只接 RhinoException，裸 RuntimeException 会穿透全部
                    // JS 帧（规则写了 catch 也接不住）、直达线程未捕获处理器变全局崩溃
                    // 弹窗（2026-10-31 真机案：downloadFile 下载 HTTP 416 抛错炸穿全桥）。
                    // 8.83 官方走反射注册、抛错由 Rhino 自动裹；我方手写 BaseFunction
                    // 没有这层自动裹，在注册咽喉补齐。Error 不裹（OOM/栈溢出原样上抛）。
                    throw new org.mozilla.javascript.WrappedException(e);
                }
            }
        });
    }

    /**
     * 在指定调用方作用域求值（8.83 require0 useCallback=true 同款：库代码在调用方
     * 作用域里跑，而非共享顶层 scope；规则侧 let/var 声明对库可见）。
     */
    private Object evalJsInScope(Context cx, Scriptable callerScope, String js, String sourceName) {
        Object oldDeadline = armEvalDeadline();
        SourceGate.Mark prevMark = swapHkMark();
        try {
            return cx.evaluateString(callerScope, js, sourceName, 1, null);
        } finally {
            restoreMark(prevMark);
            restoreEvalDeadline(oldDeadline);
        }
    }

    /** 求值（替代 ctx.evaluate）。每次用新 scope，避免规则顶层 const 污染共享 scope。 */
    // 按源定向打点：JS 求值期间，本线程发出的请求归属当前规则（kind=hk、id=规则名），
    // HkHttp 的选择器链会据此查 SourceProxyPolicy。求值可嵌套，进出按栈恢复。
    private SourceGate.Mark swapHkMark() {
        SourceGate.Mark prev = SourceGate.current();
        if (rule != null) {
            SourceGate.mark("hk", rule.getTitle());
            // 规则主域名先登记认领（与阅读/Emby 共用认领表）：本规则后续在
            // 无归属线程发出的请求（图片、调度线程判读的取数）能按域名认领
            // 回本规则，不必等第一次带标记的取数先行（JAV Verse 案）。
            try {
                com.fongmi.android.tv.clash.ReadingProxyGate.recordUrl(
                        rule.getUrl(), "hk", rule.getTitle());
            } catch (Throwable ignored) {
            }
        }
        return prev;
    }

    private void restoreMark(SourceGate.Mark prev) {
        if (prev != null) SourceGate.mark(prev.kind, prev.id);
        else SourceGate.clear();
    }

    private Object evalJs(String js) {
        // 新 scope 以共享 scope 为原型，API 函数经原型链可访问，但顶层 const/let 声明隔离在新 scope 内
        Scriptable newScope = rhinoCx.newObject(scope);
        newScope.setPrototype(scope);
        newScope.setParentScope(null);
        Object oldDeadline = armEvalDeadline();
        SourceGate.Mark prevMark = swapHkMark();
        try {
            return rhinoCx.evaluateString(newScope, js, "hk", 1, null);
        } finally {
            // 【不变量·不许在此清 Module._cache】8.83 的一次交互（find_rule→require→页模块→
            // 懒回调）在单个 evalJS 调用内跑完，缓存只活一次调用天然够用；星辰把一次交互
            // 拆成多次 evalJs 分别走，模块缓存必须跨求值活着——规则的站表、会话态都存在
            // 模块 exports 里，清掉就等于数据全丢。实证：1.4.46 曾把此处清空做实
            // （aa7c9a21 经 $.require.cache.clear()），他真机当场「好多小程序数据都没了」，
            // 已整批回退。此处旧片段对共享 scope 恒读不到 Module、从未真正执行，留着只是
            // 下一个「做实」它的引信，故删除。缓存生命周期＝运行时生命周期：规则文件
            // 更新（mtime 变）或运行时销毁时，由 HkEngineManager.runtimeFor 作废整实例。
            restoreMark(prevMark);
            restoreEvalDeadline(oldDeadline);
        }
    }

    /** 在共享 scope 上直接求值（用于插件 JS 如 Hikerurl.js，需将 $ 等挂到共享 scope 供规则经原型链访问）。 */
    private Object evalJsInSharedScope(String js) {
        Object oldDeadline = armEvalDeadline();
        SourceGate.Mark prevMark = swapHkMark();
        try {
            return rhinoCx.evaluateString(scope, js, "hk-plugin", 1, null);
        } finally {
            restoreMark(prevMark);
            restoreEvalDeadline(oldDeadline);
        }
    }

    /**
     * JS 定时器实现（setTimeout/setInterval）。
     * 调度走独立 ScheduledExecutorService，到期后把回调提交回 JS 单线程 executor 执行
     *（Rhino Context 与 JS 线程绑定，跨线程直接求值会崩）。返回数字 timerId。
     */
    private int jsSetTimer(Object[] args, boolean repeat) {
        if (destroyed) return 0;
        if (args == null || args.length == 0) return 0;
        final Object cb = args[0];
        long delay = 0;
        if (args.length > 1 && args[1] instanceof Number) delay = Math.max(0, ((Number) args[1]).longValue());
        final Object[] cbArgs;
        if (args.length > 2) {
            cbArgs = new Object[args.length - 2];
            System.arraycopy(args, 2, cbArgs, 0, cbArgs.length);
        } else {
            cbArgs = new Object[0];
        }
        final int id = timerIdSeq.getAndIncrement();
        final long period = Math.max(1, delay);
        Runnable fire = () -> {
            try {
                submit(() -> {
                    try {
                        if (destroyed) return null;
                        synchronized (jsTimers) {
                            if (!jsTimers.containsKey(id)) return null;
                            if (!repeat) jsTimers.remove(id);
                        }
                        fireTimerCallback(cb, cbArgs);
                    } catch (Throwable t) {
                        Logger.t(TAG).d("timer fire failed: " + t.getMessage());
                    }
                    return null;
                });
            } catch (Throwable ignored) {
            }
        };
        try {
            ScheduledFuture<?> f = repeat
                    ? timerScheduler.scheduleAtFixedRate(fire, delay, period, TimeUnit.MILLISECONDS)
                    : timerScheduler.schedule(fire, delay, TimeUnit.MILLISECONDS);
            synchronized (jsTimers) {
                jsTimers.put(id, f);
            }
            return id;
        } catch (Throwable t) {
            Logger.t(TAG).d("setTimer failed: " + t.getMessage());
            return 0;
        }
    }

    /** 取消 JS 定时器（clearTimeout/clearInterval 共用）。 */
    private void jsClearTimer(Object[] args) {
        if (args == null || args.length == 0) return;
        int id = 0;
        try {
            if (args[0] instanceof Number) id = ((Number) args[0]).intValue();
            else id = Integer.parseInt(String.valueOf(args[0]));
        } catch (Throwable ignored) {
            return;
        }
        if (id <= 0) return;
        ScheduledFuture<?> f;
        synchronized (jsTimers) {
            f = jsTimers.remove(id);
        }
        if (f != null) {
            try {
                f.cancel(false);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 在 JS 线程上执行定时器回调：Function 直接调用，字符串则求值。 */
    private void fireTimerCallback(Object cb, Object[] cbArgs) {
        try {
            if (cb instanceof Function) {
                ((Function) cb).call(rhinoCx, scope, scope, cbArgs);
            } else {
                String code = argStr(cb);
                if (!TextUtils.isEmpty(code)) evalJs(code);
            }
        } catch (Throwable t) {
            error = t.getMessage();
            Logger.t(TAG).d("timer callback failed: " + t.getMessage());
        }
    }

    /** 清理全部 JS 定时器（页面销毁/规则切换时调用，避免泄漏）。 */
    private void clearAllTimers() {
        Map<Integer, ScheduledFuture<?>> copy;
        synchronized (jsTimers) {
            copy = new HashMap<>(jsTimers);
            jsTimers.clear();
        }
        for (ScheduledFuture<?> f : copy.values()) {
            try {
                f.cancel(false);
            } catch (Throwable ignored) {
            }
        }
        // 8.83 registerTask 任务一并清理
        Map<String, ScheduledFuture<?>> copy2;
        synchronized (jsTasks) {
            copy2 = new HashMap<>(jsTasks);
            jsTasks.clear();
        }
        for (ScheduledFuture<?> f : copy2.values()) {
            try {
                f.cancel(false);
            } catch (Throwable ignored) {
            }
        }
    }

    /** JSON 解析（替代 ctx.parse，即 JSON.parse 语义）：非法 JSON 返回带原型的空对象，不抛异常。
     * 8.83 做法：Java 侧不造裸 NativeObject（无原型会导致 JS 拼字符串时抛"未找到对象默认值"），
     * 一律用 cx.newObject(scope) 创建带原型链的对象。 */
    // Rhino 1.7.15 的 NativeJSON.parse 传 null reviver 会抛 NullPointerException（walk 里直接调 reviver.call），
    // 旧实现把异常吞掉恒返回空对象，导致 config/存储对象永远读不到值（真机 18J、久久漫画实证）。
    // 8.83 走 JS 侧 JSON.parse 无此问题；这里传一个原样返回的 reviver 等价于 JS 的 JSON.parse(text)。
    private static final org.mozilla.javascript.Callable JSON_REVIVER =
            (cx, sc, thisObj, args) -> args != null && args.length > 1 ? args[1] : null;

    /** 严格版 parseJson：解析失败返回 null（不吞成空对象）。给存储读回用——
     *  坏缓存（如旧版把数组压成 "[object Object]"）必须能被识别成解析失败。 */
    private Object parseJsonStrict(String json) {
        try {
            return NativeJSON.parse(rhinoCx, scope, json, JSON_REVIVER);
        } catch (Throwable e) {
            return null;
        }
    }

    private Object parseJson(String json) {
        if (TextUtils.isEmpty(json)) return rhinoCx.newObject(scope);
        try {
            Object r = NativeJSON.parse(rhinoCx, scope, json, JSON_REVIVER);
            return r == null ? rhinoCx.newObject(scope) : r;
        } catch (Throwable e) {
            return rhinoCx.newObject(scope);
        }
    }

    /** JSON 序列化（替代 JSObject.stringify）。 */
    private String jsStringify(Object value) {
        try {
            Object r = NativeJSON.stringify(rhinoCx, scope, value, null, null);
            if (r == null || r == Context.getUndefinedValue()) return "null";
            return Context.toString(r);
        } catch (Throwable e) {
            return String.valueOf(value);
        }
    }

    /** evaluateString 结果转字符串（undefined/null 归一为空）。 */
    private static String jsResultToString(Object r) {
        if (r == null || r == Context.getUndefinedValue()) return "";
        return String.valueOf(r);
    }

    /**
     * 8.83 JSEngine.isUndefined（smali 5714-5738）：字符串 "undefined" 或 Rhino Undefined。
     */
    private static boolean isUndefined(Object o) {
        return "undefined".equals(o) || Undefined.isUndefined(o);
    }

    /**
     * 8.83 JSEngine.argsNativeObjectAdjust(Object)（smali 704-716）：调双参版本，p2=false。
     */
    private static Object argsNativeObjectAdjust(Object o) {
        return argsNativeObjectAdjust(o, false);
    }

    /**
     * 8.83 JSEngine.argsNativeObjectAdjust(Object, boolean)（smali 717-897）：
     * 递归把 NativeObject 转为 Map、NativeArray 转为 List；p2=true 时跳过 "param" 键的递归；
     * ConsString 取字符串；NativeJavaObject 解包；其余原样返回。
     * 注：8.83 用 fastjson 的 JSONObject/JSONArray，本项目未引入 fastjson，
     * 用 JDK 的 LinkedHashMap/ArrayList 等价替代（getString 只判断 instanceof String，行为一致）。
     */
    private static Object argsNativeObjectAdjust(Object o, boolean skipParam) {
        return argsNativeObjectAdjust(o, skipParam, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static final int ARGS_ADJUST_MAX_DEPTH = 32;

    private static Object argsNativeObjectAdjust(Object o, boolean skipParam, int depth, Set<Object> visiting) {
        if (isUndefined(o)) return o;
        if (depth > ARGS_ADJUST_MAX_DEPTH) return o;
        if (o instanceof NativeObject) {
            if (!visiting.add(o)) return o;
            try {
                Map<String, Object> map = new LinkedHashMap<>();
                NativeObject no = (NativeObject) o;
                for (Object key : no.keySet()) {
                    Object val = no.get(key);
                    if (!skipParam || !"param".equals(key)) {
                        val = argsNativeObjectAdjust(val, skipParam, depth + 1, visiting);
                    }
                    try {
                        map.put(key + "", val);
                    } catch (Throwable e) {
                        Logger.t(TAG).e(e, "argsNativeObjectAdjust put failed");
                    }
                }
                return map;
            } finally {
                visiting.remove(o);
            }
        }
        if (o instanceof NativeArray) {
            if (!visiting.add(o)) return o;
            try {
                List<Object> list = new ArrayList<>();
                NativeArray na = (NativeArray) o;
                for (int i = 0; i < na.size(); i++) {
                    list.add(argsNativeObjectAdjust(na.get(i), skipParam, depth + 1, visiting));
                }
                return list;
            } finally {
                visiting.remove(o);
            }
        }
        if (o instanceof ConsString) return o.toString();
        if (o instanceof NativeJavaObject) o = ((NativeJavaObject) o).unwrap();
        return o;
    }

    /** JS 侧传来的参数对象转 Java Map：必须走 argsNativeObjectAdjust（NativeObject→Map）；
     *  直接判 instanceof Map 永远不命中，fetchCodeByWebView 的 timeout/blockRules 曾因此全丢。 */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> jsOptions(Object[] args, int idx) {
        if (args == null || args.length <= idx || args[idx] == null) return null;
        Object adj = argsNativeObjectAdjust(args[idx]);
        return adj instanceof java.util.Map ? (java.util.Map<String, Object>) adj : null;
    }

    /**
     * 8.83 JSEngine.getString（smali 5320-5343）：永不对 JS 对象调 toString，
     * 非 String 一律返回 null（91短视频 TypeError: 未找到对象默认值 根因修复）。
     */
    private String getString(Object o) {
        Object adjusted = argsNativeObjectAdjust(o);
        if (adjusted instanceof String) return (String) adjusted;
        return null;
    }

    /**
     * JS 参数转字符串（8.83 getString 语义 + 调用方判空惯例）：
     * 先走 getString；Number/Boolean 保留 Java 字符串形式（sleep/parseBoolean 等数值参数兼容，
     * 对应 8.83 getIntValue 对数值的保留）；其余（JS 对象/undefined/null）兜底 ""。
     */
    private String argStr(Object o) {
        String s = getString(o);
        if (s != null) return s;
        if (o instanceof Number || o instanceof Boolean) return String.valueOf(o);
        return "";
    }

    /**
     * 照 8.83 JSEngine.refreshPage (smali:22071-22140)：只有真正的 Boolean false 才不回顶，
     * 缺参/null/undefined/非布尔一律回顶（原版 :cond_1 默认 0x1）。
     */
    private static boolean refreshScrollTop(Object[] args) {
        if (args != null && args.length > 0 && args[0] instanceof Boolean) return (Boolean) args[0];
        return true;
    }

    /**
     * 8.83 StringUtil.searchContains 等价实现：
     * text 为空返回 false，keyword 为空返回 true；
     * ignoreCase 为 true 时转小写；keyword 按空格切分，多词需在 text 中按序包含。
     */
    private boolean hkSearchContains(String text, String keyword, boolean ignoreCase) {
        if (text == null || text.isEmpty()) return false;
        if (keyword == null || keyword.isEmpty()) return true;
        if (ignoreCase) {
            text = text.toLowerCase();
            keyword = keyword.toLowerCase();
        }
        String[] parts = keyword.split(" ");
        if (parts.length >= 2) {
            int pos = 0;
            for (String part : parts) {
                int idx = text.indexOf(part, pos);
                if (idx < 0) return false;
                pos = idx + part.length();
            }
            return true;
        }
        return text.contains(keyword);
    }

    /**
     * JS 对象转 Map（用于 proxyClearM3u8 的 headers 参数解析）。
     * 支持 Rhino NativeObject 和 Java Map。
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<?, ?> toStringMap(Object o) {
        if (o == null) return null;
        Object adjusted = argsNativeObjectAdjust(o);
        if (adjusted instanceof java.util.Map) return (java.util.Map<?, ?>) adjusted;
        return null;
    }

    /** MD5（用于 proxyClearM3u8 缓存文件名，对应 8.83 StringUtil.md5）。 */
    private String md5(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable e) {
            return String.valueOf(s.hashCode());
        }
    }

    /** List<String> -> JS 数组（替代 JSUtil.toArray）。
     * 8.83 做法：用 cx.newArray 创建带原型链的数组，不用裸 new NativeArray()。 */
    private NativeArray toJsArray(List<String> items) {
        NativeArray arr = (NativeArray) rhinoCx.newArray(scope, items == null ? 0 : items.size());
        if (items != null) {
            for (int i = 0; i < items.size(); i++) arr.put(i, arr, items.get(i));
        }
        return arr;
    }

    /** byte[] -> JS 数组（替代 JSUtil.toArray）。
     * 8.83 做法：用 cx.newArray 创建带原型链的数组，不用裸 new NativeArray()。
     * B33：8.83 smali:18148 在 JS 里 eval 出 Uint8Array（0-255 无符号），
     * 此处按无符号存（& 0xFF），加密类规则逐字节运算结果才对。 */
    private NativeArray toJsArray(byte[] bytes) {
        NativeArray arr = (NativeArray) rhinoCx.newArray(scope, bytes == null ? 0 : bytes.length);
        if (bytes != null) {
            for (int i = 0; i < bytes.length; i++) arr.put(i, arr, bytes[i] & 0xFF);
        }
        return arr;
    }

    /** log/xlog 共用的打日志实现（1.3.42 聚阅案：xlog 是宿主全局别名）。 */
    private void logImpl(Object[] args) {
        Logger.t(TAG).d("%s", args != null && args.length > 0 ? argStr(args[0]) : "");
    }

    /**
     * 垫片共用的 fetch（与注册的 "fetch" 函数同语义，供聚阅借名垫片在 Java 侧直接调用，
     * 不经过 JS 层：hiker://assets 走内置资源；hiker://files|file:// 走内部协议分发；
     * 其余走 fetchSync）。
     */
    private String fetchForShim(String url) {
        if (url == null) return "";
        if (url.startsWith("hiker://assets/")) return loadAssetText(url);
        if (url.startsWith("hiker://") || url.startsWith("file://")) {
            Object r = fetchByHiker(url, null, false, false);
            return r instanceof String ? (String) r : "";
        }
        return fetchSync(url, null);
    }

    /** 新建空 JS 对象（优先当前 Context，兜底 new NativeObject，永不抛）。 */
    private Scriptable newEmptyJsObject() {
        try {
            Context cx = Context.getCurrentContext();
            if (cx == null) cx = rhinoCx;
            if (cx != null && scope != null) return cx.newObject(scope);
        } catch (Throwable ignored) {
        }
        return new NativeObject();
    }

    /** JS 松散语义：数值 0（原文 so.use==0 对字串 "0" 也成立，垫片只认数值，见 setJkSort 注释）。 */
    private boolean isJsZero(Object v) {
        return v instanceof Number && ((Number) v).doubleValue() == 0;
    }

    /** JS 真值语义（对象恒真；原文 so.use / so.fail 分支用）。 */
    private boolean isJsTruthy(Object v) {
        if (v == null || v == Scriptable.NOT_FOUND || v == Context.getUndefinedValue()) return false;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        if (v instanceof CharSequence) return ((CharSequence) v).length() > 0;
        return true;
    }

    /** 取数值（原文 jksort.use||0 的取值口径）。 */
    private long jsToLong(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof CharSequence) {
            try {
                return Long.parseLong(v.toString().trim());
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    /**
     * 对等 juItemO.items()（SrcJuMethod.js:33-43）：读存储文件 → 解析成对象；
     * 空文件回 {}；坏文件记日志（原文 "加载juItem存储文件失败>…"）回 {}。
     */
    private Scriptable juStoreItems(String file) {
        try {
            String content = fetchForShim(file);
            if (content != null && !content.isEmpty()) {
                Object o = parseJsonStrict(content);
                if (o instanceof Scriptable) return (Scriptable) o;
                logImpl(new Object[]{"加载juItem存储文件失败>内容不是合法对象"});
            }
        } catch (Throwable e) {
            logImpl(new Object[]{"加载juItem存储文件失败>" + e.getMessage()});
        }
        return newEmptyJsObject();
    }

    /** 对等原文 items[id] || {}。 */
    private Scriptable juStoreItemOf(Scriptable items, String id) {
        if (ScriptableObject.hasProperty(items, id)) {
            Object o = ScriptableObject.getProperty(items, id);
            if (o instanceof Scriptable) return (Scriptable) o;
        }
        return newEmptyJsObject();
    }

    /**
     * 对等原文 id 解析：id = id2 || id || (storage0.getMyVar('二级源接口信息')
     * || storage0.getMyVar('一级源接口信息')).id。
     * 原文把结果写回闭包 id（下次调用沿用），宿主侧用 idBox[0] 承载同一语义。
     * 失败回 null（原文此时 id 为 undefined/falsy，调用方走" id获取失败"分支）。
     */
    private String juStoreId(String[] idBox, Object id2arg) {
        String id2 = id2arg == null || id2arg == Context.getUndefinedValue() ? null : argStr(id2arg);
        if (id2 != null && !id2.isEmpty()) {
            idBox[0] = id2;
            return id2;
        }
        if (idBox[0] != null && !idBox[0].isEmpty()) return idBox[0];
        try {
            Map<String, String> m = myVarsFor(null);
            Object v = storage0GetByKey(m, myVarKeyFor("二级源接口信息", null), new Object[0]);
            if (v == null || "".equals(v)) {
                v = storage0GetByKey(m, myVarKeyFor("一级源接口信息", null), new Object[0]);
            }
            if (v instanceof Scriptable) {
                Object idObj = ScriptableObject.getProperty((Scriptable) v, "id");
                if (idObj != null && idObj != Scriptable.NOT_FOUND
                        && idObj != Context.getUndefinedValue()) {
                    String s = Context.toString(idObj);
                    if (!s.isEmpty()) {
                        idBox[0] = s;
                        return s;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 聚阅 juItemF(id, s) 的宿主实现（SrcJuMethod.js:30-164），按原文 7 个方法逐行实现：
     * items/set/get/clear/getAll/clearAll/listKeys。
     * file 口径：s ? rulepath+'juItem.json' : libspath+'juItem.json'。
     * 读写走 fetch/writeFile（hiker:// 语义与原文一致），id 解析走 storage0.getMyVar
     * 同命名空间，日志走 xlog（logImpl）。每个方法独立 try/catch，失败只记日志不抛。
     *
     * @param ctorId     对等 juItemF(id)：juItem 传 null，juItem2 传 "Juyue"
     * @param useRulePath 对等 juItemF(id, s)：juItem 传 false，juItem2 传 true
     */
    private Scriptable makeJuItemStore(final String ctorId, final boolean useRulePath) {
        final String file = (useRulePath ? JUYUE_RULEPATH : JUYUE_LIBSPATH) + "juItem.json";
        final String[] idBox = new String[]{ctorId};
        final Scriptable store = newEmptyJsObject();

        ScriptableObject.putProperty(store, "items", new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable s, Scriptable thisObj, Object[] args) {
                return juStoreItems(file);
            }
        });

        ScriptableObject.putProperty(store, "set", new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable s, Scriptable thisObj, Object[] args) {
                try {
                    // 原文：if(!key || str === undefined || str === null) return;
                    String key = args != null && args.length > 0 ? argStr(args[0]) : "";
                    Object str = args != null && args.length > 1 ? args[1] : null;
                    if (key == null || key.isEmpty()
                            || str == null || str == Context.getUndefinedValue()) return null;
                    // 原文：str.toString().length>1000 则 xlog(key+':无法写入长度超1000') 并返回
                    if (Context.toString(str).length() > 1000) {
                        logImpl(new Object[]{key + ":无法写入长度超1000"});
                        return null;
                    }
                    String id = juStoreId(idBox, args.length > 2 ? args[2] : null);
                    if (id == null || id.isEmpty()) {
                        logImpl(new Object[]{key + ":id获取失败"});
                        return null;
                    }
                    Scriptable items = juStoreItems(file);
                    Scriptable item = juStoreItemOf(items, id);
                    ScriptableObject.putProperty(item, key, str);
                    ScriptableObject.putProperty(items, id, item);
                    // 原文：writeFile(this.file, JSON.stringify(items))
                    writeFileContent(resolveHikerPath(file), jsStringify(items));
                } catch (Throwable e) {
                    logImpl(new Object[]{"juItem.set失败>" + e.getMessage()});
                }
                return null;
            }
        });

        ScriptableObject.putProperty(store, "get", new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable s, Scriptable thisObj, Object[] args) {
                try {
                    // 原文：if(!key) return;
                    String key = args != null && args.length > 0 ? argStr(args[0]) : "";
                    if (key == null || key.isEmpty()) return null;
                    Object def = args != null && args.length > 1 ? args[1] : null;
                    String id = juStoreId(idBox, args.length > 2 ? args[2] : null);
                    if (id == null || id.isEmpty()) {
                        logImpl(new Object[]{key + ":id获取失败"});
                        return "";
                    }
                    Scriptable items = juStoreItems(file);
                    Scriptable item = juStoreItemOf(items, id);
                    // 原文：item[key] !== undefined && !== null 则返回
                    if (ScriptableObject.hasProperty(item, key)) {
                        Object v = ScriptableObject.getProperty(item, key);
                        if (v != null && v != Scriptable.NOT_FOUND
                                && v != Context.getUndefinedValue()) return v;
                    }
                    // 原文：else if(str != '' && str != undefined) return str; 否则 ''
                    //（宿主侧：缺省值非空字串才回；数值 0 等极端宽松语义差异见施工报告）
                    if (def != null && def != Context.getUndefinedValue()
                            && !"".equals(Context.toString(def))) return def;
                    return "";
                } catch (Throwable e) {
                    logImpl(new Object[]{"juItem.get失败>" + e.getMessage()});
                    return "";
                }
            }
        });

        ScriptableObject.putProperty(store, "clear", new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable s, Scriptable thisObj, Object[] args) {
                try {
                    // 原文：if(!key) return;
                    String key = args != null && args.length > 0 ? argStr(args[0]) : "";
                    if (key == null || key.isEmpty()) return null;
                    String id = juStoreId(idBox, args.length > 1 ? args[1] : null);
                    if (id == null || id.isEmpty()) {
                        logImpl(new Object[]{key + ":id获取失败"});
                        return null;
                    }
                    Scriptable items = juStoreItems(file);
                    if (ScriptableObject.hasProperty(items, id)) {
                        Scriptable item = juStoreItemOf(items, id);
                        if (ScriptableObject.hasProperty(item, key)) {
                            ScriptableObject.deleteProperty(item, key);
                            ScriptableObject.putProperty(items, id, item);
                            writeFileContent(resolveHikerPath(file), jsStringify(items));
                        }
                    }
                } catch (Throwable e) {
                    logImpl(new Object[]{"juItem.clear失败>" + e.getMessage()});
                }
                return null;
            }
        });

        ScriptableObject.putProperty(store, "getAll", new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable s, Scriptable thisObj, Object[] args) {
                try {
                    String id = juStoreId(idBox, args != null && args.length > 0 ? args[0] : null);
                    if (id == null || id.isEmpty()) {
                        // 原文此处的 xlog 用了未定义的 key 变量（恒为 undefined），
                        // 垫片记一条可读日志，返回值保持原文的 ''。
                        logImpl(new Object[]{"juItem.getAll:id获取失败"});
                        return "";
                    }
                    return juStoreItemOf(juStoreItems(file), id);
                } catch (Throwable e) {
                    logImpl(new Object[]{"juItem.getAll失败>" + e.getMessage()});
                    return "";
                }
            }
        });

        ScriptableObject.putProperty(store, "clearAll", new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable s, Scriptable thisObj, Object[] args) {
                try {
                    // 原文：if(!id2) return;（注意：用 id2 直删，不走闭包 id 解析）
                    String id2 = args != null && args.length > 0 ? argStr(args[0]) : "";
                    if (id2 == null || id2.isEmpty()) return null;
                    Scriptable items = juStoreItems(file);
                    ScriptableObject.deleteProperty(items, id2);
                    writeFileContent(resolveHikerPath(file), jsStringify(items));
                } catch (Throwable e) {
                    logImpl(new Object[]{"juItem.clearAll失败>" + e.getMessage()});
                }
                return null;
            }
        });

        ScriptableObject.putProperty(store, "listKeys", new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable s, Scriptable thisObj, Object[] args) {
                try {
                    String id = juStoreId(idBox, args != null && args.length > 0 ? args[0] : null);
                    if (id == null || id.isEmpty()) {
                        logImpl(new Object[]{"juItem.listKeys:id获取失败"});
                        return "";
                    }
                    Scriptable item = juStoreItemOf(juStoreItems(file), id);
                    Object[] ids = item.getIds();
                    List<String> keys = new ArrayList<>();
                    for (Object k : ids) keys.add(Context.toString(k));
                    // 原文：Object.keys(item)
                    return keys.toArray(new String[0]);
                } catch (Throwable e) {
                    logImpl(new Object[]{"juItem.listKeys失败>" + e.getMessage()});
                    return "";
                }
            }
        });

        return store;
    }

    public HkJsRuntime(HkRule rule) {
        this.rule = rule;
        this.executor = Executors.newSingleThreadExecutor();
        this.parser = new Parser();
        this.parser.setJsEvaluator(this);
        this.vars = new java.util.concurrent.ConcurrentHashMap<>();
        this.results = new ArrayList<>();
        this.kv = new HashMap<>();
        loadPublicKv();
    }

    /**
     * 按当前页规则重绑（块一 W3 接缝）：宿主每次取常驻运行时求值前调用，
     * 使 setContext 的 MY_RULE/MY_NAME/MY_HEADERS、findPageCode、handlePageRequest
     * 读到的恒是当前页规则而非实例创建时规则。与 8.83「引擎无规则字段、
     * 上下文随页现拼」等价。变量表/作用域等进程级状态不随重绑重置（同 title 同表，
     * 与 8.83 单例语义一致）。
     */
    public void bindRule(HkRule rule) {
        if (rule != null) this.rule = rule;
    }

    private <T> Future<T> submit(java.util.concurrent.Callable<T> callable) {
        return executor.submit(callable);
    }

    /**
     * 求值等待上限（与 init 的等待同口径）：各入口旧形 submit().get() 无超时，
     * 规则里写死循环时调用线程永久挂起、该 runtime 后续调用全堵在单线程
     * executor 后面。加超时后调用方拿到 TimeoutException 按失败处理；
     * JS 侧另有 EVAL_BUDGET_MS 看门狗会真打断脚本。本等待必须大于该预算，
     * 否则调用方先放弃、结果被丢弃但脚本副作用已落（2026-10-27 随预算一并放宽）。
     */
    private static final long EVAL_TIMEOUT_SECONDS = 150;

    /** destroy 的清理等待：只给短暂窗口，超时就走 shutdownNow，不陪死循环耗。 */
    private static final long DESTROY_WAIT_SECONDS = 5;

    /**
     * 在 JS 线程上初始化：建 Context、注册 API、加载 kv、跑 preRule。
     */
    public void init() throws Exception {
        java.util.concurrent.Future<?> initFuture = submit(() -> {
            jsThread = Thread.currentThread();
            // Rhino：每个规则独立 Context + Scope（变量隔离），跑在单线程 executor 上；
            // setOptimizationLevel(-1) 禁用优化器（Android 无字节码生成，必须关）
            rhinoCx = Context.enter();
            rhinoCx.setOptimizationLevel(-1);
            // 8.83 initRhino 同款：语言版本 200（此前热路径 Context 没设，与第二套实现不一致）
            rhinoCx.setLanguageVersion(200);
            scope = rhinoCx.initStandardObjects();
            registerApi();
            // 主作用域补挂（此前只有 D1 解析作用域有）：requirejs 按名字取组件
            // （黄色仓库 require('crypto') 经 plugin.js 包装走 requirejs）必须在主作用域可用；
            // JavaAdapter 垫片让红果这类懒流规则在 ART 上能跑（见 HkJavaAdapterFn）。
            try { initRequire(rhinoCx, scope); } catch (Throwable t) { Logger.t(TAG).d("initRequire main: " + t.getMessage()); }
            try { installJavaAdapterShim(scope); } catch (Throwable t) { Logger.t(TAG).d("JavaAdapterShim main: " + t.getMessage()); }
            com.github.catvod.crawler.SpiderDebug.log("hk", "init step=api rule=%s", rule.getTitle());
            // D1: build 8.83 allFunctions prelude (JSAnnotation-reflected stubs + MY_UA suffix).
            try {
                allFunctions = String.format(getAllFunctions(), HkJsRuntime.class.getName())
                        + "\n var MY_UA = JSON.parse(getUaObject());\n var MOBILE_UA =  MY_UA.mobileUa;\n var PC_UA = MY_UA.pcUa";
            } catch (Throwable t) {
                Logger.t(TAG).e(t, "build allFunctions");
                allFunctions = "";
            }
            // 官方内置 CryptoJS（aes.js）：自动注入为全局，兼容直接使用 CryptoJS 的规则；
            // 库本身幂等（var CryptoJS = CryptoJS || ...），规则再 eval(getCryptoJS()) 无害
            try {
                String cryptoJs = loadAsset("aes.js");
                if (!TextUtils.isEmpty(cryptoJs)) evalJsInSharedScope(cryptoJs);
            } catch (Throwable e) {
                Logger.t(TAG).d("inject CryptoJS failed: " + e.getMessage());
            }
            // 官方内置 Hikerurl.js v6（$ 工具集的唯一定义，含官方 $.require 实现）：
            // 本地用生产同款 Rhino 1.7.15 + 宿主桥桩实测——加载完整跑通，
            // $.require('engine').home() 能真实走通模块加载并返回结果。
            // 它加载时需要语言版本 200（init 首行已设）与 clearMyVar 等桥（registerApi 已注册）。
            // 此前并存的仿写 $ 预置段已删除（错漏连环、与资产互相覆盖）；8.83 的 v2 版无 require，
            // 用户的现代规则都用 $.require，故用 v6 官方版本。下面的 RequireUtils stub 必须
            // 在资产之前求值：v6 的 require 工厂引用该类路径，stub 给它一个 no-op 的 generateRequireMap。
            try {
                evalJsInSharedScope(
                    "var com = (typeof com !== 'undefined') ? com : {};\n" +
                    "com.example = com.example || {};\n" +
                    "com.example.hikerview = com.example.hikerview || {};\n" +
                    "com.example.hikerview.ui = com.example.hikerview.ui || {};\n" +
                    "com.example.hikerview.ui.rules = com.example.hikerview.ui.rules || {};\n" +
                    "com.example.hikerview.ui.rules.service = com.example.hikerview.ui.rules.service || {};\n" +
                    "com.example.hikerview.ui.rules.service.require = com.example.hikerview.ui.rules.service.require || {};\n" +
                    "com.example.hikerview.ui.rules.service.require.RequireUtils = " +
                    "com.example.hikerview.ui.rules.service.require.RequireUtils || { generateRequireMap: function() {} };\n"
                );
                String hikerUrlJs = loadAsset("Hikerurl.js");
                if (!TextUtils.isEmpty(hikerUrlJs)) evalJsInSharedScope(hikerUrlJs);
            } catch (Throwable e) {
                Logger.t(TAG).d("inject Hikerurl.js failed: " + e.getMessage());
            }
            com.github.catvod.crawler.SpiderDebug.log("hk", "init step=hikerurl rule=%s", rule.getTitle());
            loadKv();
            loadConfig();
            com.github.catvod.crawler.SpiderDebug.log("hk", "init step=config rule=%s", rule.getTitle());
            runPreRule();
            com.github.catvod.crawler.SpiderDebug.log("hk", "init step=prerule-done rule=%s", rule.getTitle());
            return null;
        });
        try {
            initFuture.get(EVAL_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            // 兜底：init 内部真堵死（非 JS 死循环的 Java 侧阻塞）也不能让加载线程无限等
            com.github.catvod.crawler.SpiderDebug.log("hk", "engine init timeout rule=%s", rule.getTitle());
            throw new Exception("engine init timeout (60s) rule=" + rule.getTitle(), e);
        }
    }

    // ================= API 注册 =================

    /**
     * 把整套 API 函数注册进一个新鲜作用域。
     * 原版每个作用域都自带全部 159 个函数（反射预置段）；我方函数是 registerApi
     * 的 lambda 集、原本只注册进共享作用域，四条新鲜路径（runScript 与三个 evalJS）
     * 因此缺 getJsPlugin 等全部函数，规则一调就报未定义。在锁内临时切换注册目标，
     * 避免并发求值把函数注册错作用域。
     */
    private void registerApiInto(ScriptableObject target) {
        synchronized (apiRegisterLock) {
            ScriptableObject prev = apiRegTarget;
            apiRegTarget = target;
            try {
                registerApi();
            } finally {
                apiRegTarget = prev;
            }
        }
    }

    private void registerApi() {
        // 8.83 原版 JSON.parse：空串/非法 JSON 抛 SyntaxError（"Empty JSON string"），不吞错。
        // 之前宽容模式（返回空对象）导致规则 try/catch 降级失效，空对象往下传才在各处报 startsWith on undefined。
        // 已恢复原版行为。
        // ---- 结果返回 ----
        // 8.83 setResult(o, callbackKey, ruleKey, typeKey)（JSEngine.java:1109-1118）按 typeKey 路由：
        // "lastChapter"→setLastChapterResult（字符串结果）、"search"→callbackSearchResult、
        // 其他→callbackHomeResult（条目列表）。规则实际都只传 1 参（官方文档明示），
        // 类型由注入的 var MY_TYPE 决定（此处取 currentMyType，与注入口径一致）。
        putFunction("setResult", args -> {
            String typeKey = (args != null && args.length > 3 && args[3] != null)
                    ? argStr(args[3]) : currentMyType;
            if ("lastChapter".equals(typeKey)) {
                strResult = args != null && args.length > 0 && args[0] != null ? argStr(args[0]) : "";
                Logger.t(TAG).d("setResult(lastChapter): %d chars", strResult.length());
            } else {
                // search 与 home/searchRes 都是条目列表收集（官方文档：三者可混用、自动识别）
                beginResult();
                collectResult(args);
            }
            return null;
        });
        putFunction("setHomeResult", args -> {
            beginResult();
            collectHomeResult(args);
            return null;
        });
        putFunction("setSearchResult", args -> {
            beginResult();
            collectResult(args);
            return null;
        });
        // setPreResult：预返回（官方要求与 setResult 成对、顺序固定）。
        // 语义：先占位展示，下一记 setResult/setSearchResult 到达时追加合并（官方文档原话：
        // "最后再调用setResult()返回需要追加显示的部分"；8.83 smali 亦为 PRE 立即显示＋AFTER 追加，
        // 无"整体替换"）。规则侧占位符（如"加载中"）由规则自己调 deleteItem/remove，无需壳清。
        putFunction("setPreResult", args -> {
            results.clear();
            rawResults.clear();
            collectResult(args);
            preResultActive = true;
            Logger.t(TAG).d("setPreResult: %d items staged", results.size());
            return null;
        });
        // ------------------------------------------------------------------
        // 8.83: setError(Object o, Object callbackKey, Object ruleKey, Object evalKey)V
        // [JSEngine.smali] alias "error". D2: 完整实现。
        // o: String | JavaScriptException | RhinoException | ScriptableObject({fileName,lineNumber,lineSource})
        // 构造 "\n来源：\n代码：\n行数：\n详情：" 明细；超 2000 字截断；查 callbackMap：
        // 有回调 -> callback.showErr(msg)；无 -> 宿主 Toast。
        // ------------------------------------------------------------------
        putFunction("setError", args -> {
            setErrorImpl(args);
            return null;
        });
        // 官方别名：error 即 setError
        putFunction("error", args -> {
            setErrorImpl(args);
            return null;
        });

        // ---- 网络 ----
        putFunction("fetch", args -> {
            if (args == null || args.length == 0) return "";
            String url = argStr(args[0]);
            // D2：hiker://assets/ 走内置 asset（官方 HttpHelper.fetch 经 fetchByHiker 拦截同款）
            if (url.startsWith("hiker://assets/")) return loadAssetText(url);
            // 8.83 fetchByHiker 同款：hiker://page/、hiker://files/、file://、通用 hiker://
            // 走内部协议分发返回内容，不再直接掉进 fetchSync 返回 ""（曾导致 JSON.parse("") 抛 Empty JSON string）
            if (url.startsWith("hiker://") || url.startsWith("file://")) {
                Object r = fetchByHiker(url, null, false, false);
                return r instanceof String ? (String) r : "";
            }
            String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : null;
            return fetchSync(url, options);
        });
        putFunction("post", args -> {
            if (args == null || args.length == 0) return "";
            String url = argStr(args[0]);
            // 8.83 post0→fetch0 经 RuleFetchDelegate→fetchByHiker 拦截同款：内部协议走分发
            if (url.startsWith("hiker://") || url.startsWith("file://")) {
                Object r = fetchByHiker(url, null, false, false);
                return r instanceof String ? (String) r : "";
            }
            String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : "{}";
            // 8.83 post0（smali:8148，request=false）：generatePostOps 后走
            // generateRequestOptions(op, true)——缺 UA 时补桌面端 UA。
            // 此前的注释「post 不补 UA」写反了（postRequest 才是补移动端 UA 那支）。
            options = applyGeneratePostOps(options);
            return fetchSync(url, mergeDesktopUa(options));
        });
        putFunction("request", args -> {
            if (args == null || args.length == 0) return "";
            String url = argStr(args[0]);
            // hiker://page/<path> 内部协议：从规则 pages 里按 path 取页面规则，返回 {"rule": "..."}
            if (url.startsWith("hiker://page/")) return handlePageRequest(url);
            // D2：hiker://assets/ 走内置 asset（官方 $.require 经 getScript→request() 取脚本同款）
            if (url.startsWith("hiker://assets/")) return loadAssetText(url);
            // 8.83 fetchByHiker 同款：hiker://files/、file://、通用 hiker:// 走内部协议分发
            if (url.startsWith("hiker://") || url.startsWith("file://")) {
                Object r = fetchByHiker(url, null, false, false);
                return r instanceof String ? (String) r : "";
            }
            String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : null;
            // N1: 8.83 的 request 默认补移动端 UA（JSEngine.smali:22856），裸 fetch 才不补
            options = injectMobileUa(options);
            return fetchSync(url, options);
        });
        putFunction("getRule", args -> {
            // 8.83 JSEngine.getRule：恒等返回传入的规则字符串（此前缺失，调用的规则直接 ReferenceError）
            return args == null || args.length == 0 || args[0] == null ? "" : argStr(args[0]);
        });
        putFunction("getResCode", args -> {
            // 8.83 (JSEngine.smali:17733): getResCode(callbackKey) 返回 resCodeMap 中的响应正文 (String)，
            // 不是 int 状态码。规则常写 getResCode() || ''，返回 0 会导致空内容。
            // 1. JS 显式传了 callbackKey 参数时用它查表
            if (args != null && args.length > 0 && args[0] != null) {
                try {
                    String key = String.valueOf(argsNativeObjectAdjust(args[0]));
                    String v = resCodeMap.get(key);
                    if (v != null) return v;
                } catch (Throwable ignored) {}
            }
            // 2. 从当前 scope 取 CALLBACK_KEY 查表（parseHome/parseStr 等流程注入）
            try {
                Object ck = scope.get("CALLBACK_KEY", scope);
                if (ck != null && ck != Scriptable.NOT_FOUND) {
                    String key = String.valueOf(argsNativeObjectAdjust(ck));
                    String v = resCodeMap.get(key);
                    if (v != null) return v;
                }
            } catch (Throwable ignored) {}
            // 3. fallback: @rule= js: 预检时设置的正文
            if (evalResCodeBody != null) return evalResCodeBody;
            // 8.83 JSEngine.java:537：resCodeMap.get(key) 直接返回，缺省 null（有值时的回退链保留）
            return null;
        });
        // 官方别名：getCode 即 getResCode（8.83 smali alias="getCode"）
        putFunction("getCode", args -> {
            if (args != null && args.length > 0 && args[0] != null) {
                try {
                    String key = String.valueOf(argsNativeObjectAdjust(args[0]));
                    String v = resCodeMap.get(key);
                    if (v != null) return v;
                } catch (Throwable ignored) {}
            }
            try {
                Object ck = scope.get("CALLBACK_KEY", scope);
                if (ck != null && ck != Scriptable.NOT_FOUND) {
                    String key = String.valueOf(argsNativeObjectAdjust(ck));
                    String v = resCodeMap.get(key);
                    if (v != null) return v;
                }
            } catch (Throwable ignored) {}
            if (evalResCodeBody != null) return evalResCodeBody;
            // 8.83 JSEngine.java:537：resCodeMap.get(key) 直接返回，缺省 null（getCode 为 getResCode 官方别名）
            return null;
        });
        putFunction("getUrl", args -> lastUrl == null ? "" : lastUrl);

        // ---- DOM 解析（复用 Parser，即海阔 parseHikerToJq 路径） ----
        putFunction("parseDom", args -> {
            if (args == null || args.length < 2) return "";
            String urlKey = args.length > 2 && args[2] != null ? argStr(args[2]) : (lastUrl == null ? "" : lastUrl);
            return parser.pdfh(argStr(args[0]), sel(args[1]), urlKey);
        });
        putFunction("pd", args -> {
            if (args == null || args.length < 2) return "";
            String urlKey = args.length > 2 && args[2] != null ? argStr(args[2]) : (lastUrl == null ? "" : lastUrl);
            return parser.pdfh(argStr(args[0]), sel(args[1]), urlKey);
        });
        putFunction("parseDomForHtml", args -> {
            if (args == null || args.length < 2) return "";
            return parser.pdfh(argStr(args[0]), sel(args[1]), "");
        });
        putFunction("pdfh", args -> {
            if (args == null || args.length < 2) return "";
            return parser.pdfh(argStr(args[0]), sel(args[1]), "");
        });
        putFunction("parseDomForArray", args -> {
            if (args == null || args.length < 2) return toJsArray(new ArrayList<>());
            List<String> items = parser.pdfa(argStr(args[0]), sel(args[1]));
            return toJsArray(items);
        });
        putFunction("pdfa", args -> {
            if (args == null || args.length < 2) return toJsArray(new ArrayList<>());
            List<String> items = parser.pdfa(argStr(args[0]), sel(args[1]));
            return toJsArray(items);
        });

        // ---- 存储 ----
        // 8.83 JSEngine.putVar：值非字符串时 JSON.toJSONString 后存入（原版做法），
        // 以前 argStr 对非字符串一律兜底空串，对象/数组存进去就丢了。
        // 8.83 JSEngine.java:630：支持单对象参数 putVar({key, value})
        putFunction("putVar", args -> {
            if (args == null || args.length == 0) return null;
            if (args.length < 2) {
                java.util.Map<?, ?> m = toStringMap(args[0]);
                if (m != null && m.get("key") != null) {
                    Object val = m.get("value");
                    String s = val instanceof String ? (String) val
                            : (val == null ? "" : stringifyArg(val));
                    vars.put(String.valueOf(m.get("key")), s);
                }
                return null;
            }
            Object v = args[1];
            String s;
            if (v instanceof String) s = (String) v;
            else {
                try { s = stringifyArg(v); }
                catch (Throwable ignored) { s = ""; }
            }
            vars.put(argStr(args[0]), s);
            return null;
        });
        // 官方 putVar2：值非字符串时 JSON 序列化后存入（与 putVar 区别仅在此）
        putFunction("putVar2", args -> {
            if (args == null || args.length < 2) return null;
            String k = argStr(args[0]);
            if (TextUtils.isEmpty(k)) return null;
            Object v = args[1];
            String s;
            if (v instanceof String) s = (String) v;
            else {
                try { s = stringifyArg(v); }
                catch (Throwable ignored) { s = String.valueOf(v); }
            }
            vars.put(k, s);
            return null;
        });
        putFunction("getVar", args -> {
            if (args == null || args.length == 0) return "";
            String v = vars.get(argStr(args[0]));
            // 8.83 官方 getVar：default 为 undefined/null 时返回 ""，不是 "null" 字符串
            if (v == null && args.length > 1 && args[1] != null) v = argStr(args[1]);
            return v == null ? "" : v;
        });
        // 8.83 JSEngine.java:2450 setItem：key/value 经 tryGetString，非字符串抛错
        putFunction("setItem", args -> {
            String k = mustBeString(args, 0);
            String v = mustBeString(args, 1);
            kv.put(k, v);
            saveKv();
            // 诊断：口令记号存（星集使用前须知门）。只记键名与长度，不记值本身。
            if ("xj_firstopen".equals(k)) {
                String stored = kv.get(k);
                com.github.catvod.crawler.SpiderDebug.log("hk", "setItem xj_firstopen saved len=%d ruleVerLen=%d",
                        stored == null ? -1 : stored.length(),
                        rule == null ? -1 : rule.getVersionRaw().length());
            }
            return null;
        });
        // 8.83 原样：缺键回传入的默认、未传默认即 null（不是空串）——8.83 的 STRING
        // 返回桥是 retStr==null ? retStr : retStr+''，null 原样穿过（JSEngine.java:3110 亲核）。
        // 按 null/undefined 分支的规则（口令门、tab 开关）靠这个语义；曾误改归一空串已回滚。
        putFunction("getItem", args -> {
            if (args == null || args.length == 0) return null;
            String key = getString(args[0]);
            String def = args.length > 1 ? getString(args[1]) : null;
            if (key == null || key.isEmpty()) return def;
            String v = kv.get(key);
            // 诊断：口令记号读（星集使用前须知门）。只记命中与长度，不记值本身。
            if ("xj_firstopen".equals(key)) {
                com.github.catvod.crawler.SpiderDebug.log("hk", "getItem xj_firstopen hit=%s len=%d ruleVerLen=%d",
                        v != null, v == null ? -1 : v.length(),
                        rule == null ? -1 : rule.getVersionRaw().length());
            }
            // 毒值兜底：旧版 setItem 把 JS undefined 经 argStr 存成了字面量 "undefined" 字符串，
            // 该值与 undefined 恒不等、口令门永远判假；按缺键处理，老装机自动解毒。
            if ("undefined".equals(v)) v = null;
            return v != null ? v : def;
        });

        // ---- 调试 ----
        putFunction("toast", args -> {
            final String msg = args != null && args.length > 0 ? argStr(args[0]) : "";
            Logger.t(TAG).d("toast: %s", msg);
            try {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        ToastUtil.show(App.get(), msg, android.widget.Toast.LENGTH_SHORT));
            } catch (Throwable ignored) {
            }
            return null;
        });
        putFunction("log", args -> {
            logImpl(args);
            return null;
        });
        // 聚阅库兼容垫片（1.3.42 聚阅案）：聚阅 SrcJuPublic.js 在全新作用域里「先用后定义」
        // xlog（let xlog = log 只在 SrcJuMethod.js 里，而该文件在这条加载链上从未被
        // require），require 作用域修法救不了这种写法。加宿主全局别名 xlog=log：
        // SrcJuPublic.js 的 xlog(...) 取到全局别名正常跑；SrcJuMethod.js 的 let xlog = log
        // 在调用方作用域内遮蔽全局，不冲突。xlog 不在 8.83 原版清单里，属兼容垫片。
        putFunction("xlog", args -> {
            logImpl(args);
            return null;
        });
        // 聚阅库兼容垫片（1.3.45 聚阅聚合页卡白案）：聚阅 SrcJuPublic.js:11 在全新作用域里调用
        // downloadPlugins()，该函数只定义在 SrcJuMethod.js:1237，而该文件在本加载链从未被 require；
        // 8.83 靠进程单例持久作用域跨求值泄漏得到它，星辰逐次新作用域直接报 ReferenceError，
        // parseList 异常被吞成空列表＝聚合页白页。照 xlog 垫片先例在宿主侧实现，按原函数逐行语义
        // （~/workspace/juyue/libs/ea2ea7f49c1d87b8d19784fa4206921f.js:1237-1250）：从代码仓下载
        // 6 个插件/模板文件到 hiker://files/data/聚阅/ 本地化，自身 try/catch、失败只记日志不抛。
        putFunction("downloadPlugins", args -> {
            try {
                boolean update = false;
                if (args != null && args.length > 0) {
                    Object u = args[0];
                    update = Boolean.TRUE.equals(u)
                            || (u instanceof Number && ((Number) u).intValue() != 0);
                }
                // 原文：(config.聚阅||getPublicItem('聚阅','')) — 规则配置对象，公共 KV 回退
                String repo = "";
                try {
                    Object cfg = scope.get("config", scope);
                    if (cfg instanceof Scriptable) repo = scriptableString((Scriptable) cfg, "聚阅");
                } catch (Throwable ignored) {
                }
                if (repo.isEmpty()) {
                    String v = publicKv.get("聚阅");
                    if (v != null) repo = v;
                }
                // 原文：.replace(/[^/]*$/,'') — 去掉末尾最后一个 / 之后的部分，保留末尾斜杠
                String base = repo.replaceAll("/[^/]*$", "/");
                String libsPath = "hiker://files/data/聚阅/"; // 原 SrcJuMethod.js:2 let libspath
                String[] files = {"plugins/hikerPop.js", "plugins/gzip.js", "plugins/pinyin-match.js",
                        "template/GetAppApi.js", "template/parseCode.js", "template/采集cms.js"};
                for (String it : files) {
                    String dest = resolveHikerPath(libsPath + it);
                    if (update) new File(dest).delete(); // 原文：deleteFile(libspath + it)
                    if (new File(dest).exists()) continue; // 宿主 requireDownload 语义：已存在跳过
                    downloadToFile(base + it, dest, "{}");
                }
            } catch (Throwable e) {
                // 原文：catch(e){ xlog('插件文件本地化失败>' + e.message) }
                logImpl(new Object[]{"插件文件本地化失败>" + e.getMessage()});
            }
            return null;
        });

        // 聚阅库兼容垫片（1.3.47 聚阅借名垫齐案）：聚阅 SrcJuPublic.js
        //（dc83a9ca6959bd597f109b9f1e16df6e.js）在全新作用域里借用了一批只定义在
        // SrcJuMethod.js（ea2ea7f49c1d87b8d19784fa4206921f.js）顶层的名字，
        // 本加载链从未 require 方法库文件，逐次新作用域直接报 ReferenceError。
        // 借名清单（枚举方法：两文件顶层 function/let 全量统计＋交叉引用核对，
        // 详见施工报告；xlog=1.3.42、downloadPlugins=1.3.46 已垫，不再重复；
        // juFile/Juconfig/sourcename 经核对是误报——路径字串里的 "juFile"、
        // 方法库内部局部变量、属性名字串，不垫）：
        //   路径常量：libspath(:2)、rulepath(:3)、jkfilespath(:5)、jkfile(:6)、
        //             cfgfile(:7)、sortfile(:8)
        //   getJuconfig(:170) —— 公共库:47,645,705,733,755,773,779
        //   juItem(:165, =juItemF()) —— 公共库:1386 clearAll
        //   juItem2(:166, =juItemF('Juyue',1)) —— 公共库:55,58,62,122,165,209,
        //             467,495,588,618,619,621（getAll/set/get/clear）
        //   setJkSort(:1142) —— 公共库:276,398
        //   readDir(:1514) —— 公共库:1300
        // 写法与 1.3.46 downloadPlugins 垫片同款：按原函数逐行实现，只用宿主已实证
        // 的等价能力（fetch/writeFile/deleteFile/fileExist/getPath/resolveHikerPath、
        // storage0.getMyVar、xlog/logImpl、parseJson/jsStringify），try/catch 包住、
        // 失败只记日志不抛。垫片不是 8.83 原版 API，属兼容垫片。
        ScriptableObject regTarget = apiRegTarget != null ? apiRegTarget : scope;
        // —— 路径常量（原文 :2-:8 的 let，直接挂全局；规则文件自己的同名 let 会遮蔽，不冲突）
        ScriptableObject.putProperty(regTarget, "libspath", JUYUE_LIBSPATH);
        ScriptableObject.putProperty(regTarget, "rulepath", JUYUE_RULEPATH);
        ScriptableObject.putProperty(regTarget, "jkfilespath", JUYUE_JKFILESPATH);
        ScriptableObject.putProperty(regTarget, "jkfile", JUYUE_JKFILE);
        ScriptableObject.putProperty(regTarget, "cfgfile", JUYUE_CFGFILE);
        ScriptableObject.putProperty(regTarget, "sortfile", JUYUE_SORTFILE);
        // —— getJuconfig()（原文 :170-177：fetch(cfgfile)，非空则 eval 成对象；空文件回 {}）
        putFunction("getJuconfig", args -> {
            try {
                String c = fetchForShim(JUYUE_CFGFILE);
                if (c == null || c.isEmpty()) return newEmptyJsObject();
                Object o = parseJsonStrict(c);
                if (o instanceof Scriptable) return o;
                // 原文 eval 失败会抛；垫片按"失败只记日志不抛"口径回空对象
                logImpl(new Object[]{"加载Juconfig失败>内容不是合法对象"});
                return newEmptyJsObject();
            } catch (Throwable e) {
                logImpl(new Object[]{"加载Juconfig失败>" + e.getMessage()});
                return newEmptyJsObject();
            }
        });
        // —— juItem / juItem2（原文 :30-167 juItemF 工厂 + :165-:167 三个实例）
        try {
            ScriptableObject.putProperty(regTarget, "juItem", makeJuItemStore(null, false));
            ScriptableObject.putProperty(regTarget, "juItem2", makeJuItemStore("Juyue", true));
        } catch (Throwable e) {
            logImpl(new Object[]{"juItem垫片装配失败>" + e.getMessage()});
        }
        // —— setJkSort(data, so)（原文 :1142-1185；$.type 用 Java instanceof 还原）
        putFunction("setJkSort", args -> {
            try {
                Object data = args != null && args.length > 0 ? args[0] : null;
                Object so = args != null && args.length > 1 ? args[1] : null;
                // 原文 waitlist 装配：string→[data]；object→[data]；array→data；其余→[]
                //（$.type(array)='array'，故数组分支必须排在 Scriptable 之前）
                List<Object> waitlist = new ArrayList<>();
                if (data instanceof CharSequence) {
                    waitlist.add(data);
                } else if (data instanceof NativeArray) {
                    for (Object o : (NativeArray) data) waitlist.add(o);
                } else if (data != null && data.getClass().isArray()) {
                    int n = java.lang.reflect.Array.getLength(data);
                    for (int i = 0; i < n; i++) waitlist.add(java.lang.reflect.Array.get(data, i));
                } else if (data instanceof List) {
                    waitlist.addAll((List<?>) data);
                } else if (data instanceof Scriptable) {
                    waitlist.add(data);
                }
                // 原文：sort={}；fetch(sortfile) 非空则 eval("sort="+内容)；失败 xlog
                //（原文调了两次 fetch，宿主侧一次读完，外部可观察行为一致）
                Scriptable sort = newEmptyJsObject();
                String sortContent = fetchForShim(JUYUE_SORTFILE);
                if (sortContent != null && !sortContent.isEmpty()) {
                    Object o = parseJsonStrict(sortContent);
                    if (o instanceof Scriptable) sort = (Scriptable) o;
                    else logImpl(new Object[]{"加载使用记录sort文件失败>内容不是合法对象"});
                }
                for (Object it : waitlist) {
                    // 原文：object 取 it.id；string 取自身；其余 key 保持 undefined
                    //（JS 里 undefined 作键即 "undefined"，宿主侧直接用该字串）
                    String key;
                    if (it instanceof Scriptable && !(it instanceof NativeArray)) {
                        Object idv = ScriptableObject.getProperty((Scriptable) it, "id");
                        key = (idv == null || idv == Scriptable.NOT_FOUND
                                || idv == Context.getUndefinedValue()) ? "undefined" : Context.toString(idv);
                    } else if (it instanceof CharSequence) {
                        key = it.toString();
                    } else {
                        key = "undefined";
                    }
                    // 原文：jksort = sort[key] || {}；非 object 则重置为 {}
                    Scriptable jksort;
                    if (ScriptableObject.hasProperty(sort, key)) {
                        Object e = ScriptableObject.getProperty(sort, key);
                        jksort = (e instanceof Scriptable && !(e instanceof NativeArray))
                                ? (Scriptable) e : newEmptyJsObject();
                    } else {
                        jksort = newEmptyJsObject();
                    }
                    // 原文四分支（so.use==0 / so.fail==0 / so.use++ / so.fail++）；
                    // ==0 用宽松语义（数值 0；原文对字串 "0" 也成立，宿主侧只认数值，
                    // 实调点只传 {fail:0}/{use:1} 对象字面量，无影响）
                    if (so instanceof Scriptable) {
                        Object useV = ScriptableObject.getProperty((Scriptable) so, "use");
                        Object failV = ScriptableObject.getProperty((Scriptable) so, "fail");
                        if (isJsZero(useV)) {
                            ScriptableObject.putProperty(jksort, "use", 0);
                        } else if (isJsZero(failV)) {
                            ScriptableObject.putProperty(jksort, "fail", 0);
                        } else if (isJsTruthy(useV)) {
                            long u = jsToLong(ScriptableObject.getProperty(jksort, "use"));
                            ScriptableObject.putProperty(jksort, "use", u + 1);
                        } else if (isJsTruthy(failV)) {
                            long f = jsToLong(ScriptableObject.getProperty(jksort, "fail"));
                            ScriptableObject.putProperty(jksort, "fail", f + 1);
                        }
                    }
                    ScriptableObject.putProperty(sort, key, jksort);
                }
                // 原文：writeFile(sortfile, JSON.stringify(sort))
                writeFileContent(resolveHikerPath(JUYUE_SORTFILE), jsStringify(sort));
            } catch (Throwable e) {
                // 原文无外层 try（内层读文件失败才 xlog）；垫片统一"失败只记日志不抛"
                logImpl(new Object[]{"setJkSort失败>" + e.getMessage()});
            }
            return null;
        });
        // —— readDir(path)（原文 :1514-1524：hiker:// 走 getPath 再去 "file://" 前缀；
        // getPath 即 "file://" + resolveHikerPath，宿主侧直接 resolve，结果等价；
        // 非 hiker:// 路径原文直接 new File(path)，同样保留）
        putFunction("readDir", args -> {
            try {
                String p = args != null && args.length > 0 ? argStr(args[0]) : "";
                if (p == null || p.isEmpty()) return new String[0];
                String abs = p.startsWith("hiker://") ? resolveHikerPath(p) : p;
                File dir = new File(abs);
                if (!dir.exists() || !dir.isDirectory()) return new String[0];
                File[] fs = dir.listFiles();
                List<String> names = new ArrayList<>();
                if (fs != null) for (File f : fs) names.add(f.getName());
                return names.toArray(new String[0]);
            } catch (Throwable e) {
                logImpl(new Object[]{"readDir失败>" + e.getMessage()});
                return new String[0];
            }
        });

        // ---- 交互/配置（海阔规则常用，preRule 里调） ----
        putFunction("confirm", args -> {
            // 8.83 JSEngine.confirm 同款：弹确认框；确定走 confirm 回调、取消走 cancel 回调。
            // 回调在原版是函数对象：此前先 stringify 再按 JSON 取串，JSON 化会把函数
            // 整个丢掉，回调恒为空串——弹窗能弹、点确定毫无反应（福建兄妹靠 confirm
            // 自更新域名，真机实证更新永不生效、海阔原版正常）。改成直接从对象上取：
            // 函数取源码包成立即调用式，交给 evalConfirmJs 求值后走路由。
            try {
                if (args != null && args.length > 0 && args[0] instanceof Scriptable) {
                    Scriptable o = (Scriptable) args[0];
                    String title = scriptableString(o, "title");
                    String content = scriptableString(o, "content");
                    String confirmJs = scriptableCallback(o, "confirm");
                    String cancelJs = scriptableCallback(o, "cancel");
                    ConfirmHandler h = confirmHandler;
                    if (h != null && (!TextUtils.isEmpty(title) || !TextUtils.isEmpty(content))) {
                        h.onConfirm(title, content, confirmJs, cancelJs);
                        return true;
                    }
                    Logger.t(TAG).d("confirm(no handler): %s", title);
                }
            } catch (Throwable ignored) {
            }
            return true;
        });
        putFunction("initConfig", args -> {
            // initConfig({host: ...})：把配置持久化到 plugins/hk/config/<规则名>.json，供 config.xxx 读取
            try {
                if (args != null && args.length > 0 && args[0] != null) {
                    String json = stringifyArg(args[0]);
                    if (!TextUtils.isEmpty(json) && json.startsWith("{")) {
                        HkRuleManager.get().saveRuleConfig(rule.getTitle(), json);
                        // 同步刷新当前 ctx 的 config 对象
                        try {
                            Object obj = parseJson(json);
                            ScriptableObject.putProperty(scope, "config", obj);
                        } catch (Throwable ignored) {
                        }
                        Logger.t(TAG).d("initConfig saved for %s", rule.getTitle());
                    }
                }
            } catch (Throwable e) {
                Logger.t(TAG).d("initConfig failed: %s", e.getMessage());
            }
            return null;
        });
        // 8.83 JSEngine.java:547：getParam(key, defaultValue, urlKey)；未命中回 defaultValue（不传则 undefined）；
        // 值经 decodeConflictStr（HkHttpParser.getParamsByUrl 内已做，与原版同口径）
        putFunction("getParam", args -> {
            if (args == null || args.length == 0 || !(args[0] instanceof String)) return Undefined.instance;
            String key = (String) args[0];
            String urlKey = args.length > 2 && args[2] instanceof String
                    ? (String) args[2] : (lastUrl == null ? "" : lastUrl);
            Map<String, String> params = HkHttpParser.getParamsByUrl(urlKey);
            if (params.containsKey(key)) return params.get(key);
            if (args.length > 1 && args[1] != null) return args[1];
            return Undefined.instance;
        });

        // ---- 编解码 ----
        putFunction("base64Encode", args -> {
            if (args == null || args.length == 0) return "";
            return Util.base64(argStr(args[0]).getBytes(Charset.forName("UTF-8")));
        });
        putFunction("base64Decode", args -> {
            if (args == null || args.length == 0) return "";
            try {
                return new String(Base64.decode(argStr(args[0]), Base64.DEFAULT), Charset.forName("UTF-8"));
            } catch (Throwable e) {
                return "";
            }
        });
        // ---- encodeStr/decodeStr：8.83 是 URL 编解码（JSEngine.java:701-741，
        // HttpParser.encodeUrl/decodeUrl，带 charset 参数）。旧实现误写成 base64；
        // base64 能力已有 base64Encode/base64Decode 专用函数，不丢。
        putFunction("encodeStr", args -> {
            if (args == null || args.length < 2 || args[0] == null || args[1] == null) return "";
            String str = String.valueOf(args[0]);
            String code = String.valueOf(args[1]);
            // 8.83 encodeUrl：code 为空/UTF-8/* 时原样返回，否则 URLEncoder.encode
            if (code.isEmpty() || "UTF-8".equalsIgnoreCase(code) || "*".equals(code)) return str;
            try {
                return java.net.URLEncoder.encode(str, code);
            } catch (Throwable e) {
                return str;
            }
        });
        putFunction("decodeStr", args -> {
            if (args == null || args.length < 2 || args[0] == null || args[1] == null) return "";
            String str = String.valueOf(args[0]);
            String code = String.valueOf(args[1]);
            // 8.83 decodeUrl：先处理残 % 与 +，再 URLDecoder.decode
            try {
                String s = str.replaceAll("%(?![0-9a-fA-F]{2})", "%25").replaceAll("\\+", "%2B");
                return java.net.URLDecoder.decode(s, code);
            } catch (Throwable e) {
                return str;
            }
        });
        // ---- aesEncode/aesDecode：8.83（JSEngine.java:771-814）：即 AesUtil.encrypt/decrypt，
        // 参数序 (key, data)；AES/ECB/PKCS5Padding；密钥补"0"到 32 字节；base64 出入。
        putFunction("aesEncode", args -> {
            if (args == null || args.length < 2 || args[0] == null || args[1] == null) return "";
            try {
                return aesUtilCrypt(true, String.valueOf(args[0]), String.valueOf(args[1]));
            } catch (Throwable e) {
                return "";
            }
        });
        putFunction("aesDecode", args -> {
            if (args == null || args.length < 2 || args[0] == null || args[1] == null) return "";
            try {
                return aesUtilCrypt(false, String.valueOf(args[0]), String.valueOf(args[1]));
            } catch (Throwable e) {
                return "";
            }
        });

        // ---- require：远程库加载（preRule 常用）；本地 libs/<md5(url)>.js 优先（.hkzip 自带库） ----
        // 8.83 require0：eval 后返回模块执行结果——此点保留（UU 的 require('tools') 靠它）。
        // 但"未命中抛错"不能照搬：黄色仓库等规则会 require 一个本地没有的裸名（如 'crypto'），
        // 1.3.19 及之前本实现是取不到就记日志回 null、规则照常跑；1.3.20 的 B1 改成抛错后
        // 这类规则整个列表页全灭（2026-10-16 真机实证，"修好的又修坏了"）。按真机结论回软失败。
        // 8.83 语义（JSEngine require0 + 真机）：取到的库代码求值后，顶层声明对 require
        // 调用方可见（如 JuRequire.js 的 getRely）。原版 require 调 require0 时 useCallback=true，
        // 库代码串经回调 Function.call 在调用方作用域链里求值（JSEngine.smali require0:3702/3719）。
        // 此前实现（2026-10-16 聚阅修法）把库代码扔进共享顶层 scope 求值：库的顶层声明对调用方
        // 可见了，但反方向断了——规则侧的顶层声明（如 let xlog = log）对库不可见，UU 插件
        // 在库里调 xlog 报 ReferenceError（2026-10-17 真机实证 hk-plugin#10）。
        // 改为在调用方作用域求值（原版同款）：两个方向都通；规则每次 runScript 都是新鲜作用域，
        // 重复 require 不会在共享顶层攒下重复 let，比之前更干净。
        putFunctionEx("require", (cx, callerScope, args) -> {
            if (args == null || args.length == 0) return null;
            String libUrl = argStr(args[0]);
            String code = loadLibLocal(libUrl);
            if (code == null) code = fetchSync(libUrl, null);
            if (TextUtils.isEmpty(code)) {
                Logger.t(TAG).d("require miss (soft): %s", libUrl);
                com.github.catvod.crawler.SpiderDebug.log("hk", "require miss soft name=%s rule=%s",
                        libUrl, rule == null ? "" : rule.getTitle());
                return null;
            }
            return evalJsInScope(cx, callerScope, code, "hk-plugin");
        });

        // ---- JS 定时器（setTimeout/setInterval/clearTimeout/clearInterval） ----
        // 规则"20秒后继续"倒计时等场景依赖；回调统一提交回 JS 单线程执行（Rhino Context 线程绑定）。
        putFunction("setTimeout", args -> jsSetTimer(args, false));
        putFunction("setInterval", args -> jsSetTimer(args, true));
        putFunction("clearTimeout", args -> {
            jsClearTimer(args);
            return null;
        });
        putFunction("clearInterval", args -> {
            jsClearTimer(args);
            return null;
        });

        // ---- __hkShowUpdateLog：更新日志底部弹窗（星辰原生重实现） ----
        // 规则的 hikerPop.updateRecordsBottom 依赖 8.83 内部类（MyStatusBarUtil 等），
        // 在星辰包名下跑不起来。此处原生实现 8.83 UpdateRecordsBottom 样式：
        // 从底部滑出，标题"更新日志"，列表展示各版本 records，右下"知道啦"关闭。
        putFunction("__hkShowUpdateLog", args -> {
            if (args == null || args.length == 0) return null;
            final String json = argStr(args[0]);
            com.fongmi.android.tv.App.post(() -> {
                try {
                    com.fongmi.android.tv.api.hk.HkUpdateLogPopup.show(json);
                } catch (Throwable t) {
                    android.util.Log.d("HkJs", "__hkShowUpdateLog failed: " + t.getMessage());
                }
            });
            return null;
        });

        // ---- __hkRequirePage 已删除（2026-10-27 对差补缺第 16 项销账）----
        // 它曾是 $.require 的一套 Java 旁路实现，但全树＋全部资产 grep 实证：除定义外
        // 无任何调用方——规则调的 $.require 恒走官方 Hikerurl.js 的 Module（带缓存与
        // exports 语义），此旁路不可达。留着只会让后人误以为有两套 require 在跑，故删。
        // findPageCode/loadLibLocal 等被其它路径共用的助手保留不动。

        // ---- 规则内会话变量（getMyVar/putMyVar/clearMyVar/listMyVarKeys） ----
        // E8: key 加 ruleTitle@ 前缀做规则间隔离（8.83 JSEngine.smali:21694）
        putFunction("putMyVar", args -> {
            // E8: 8.83 putMyVar 对象值 JSON 序列化存（JSEngine.smali:21861），key 加 ruleTitle@ 前缀隔离
            // W4：第三参 ruleTitleKey 指定别的规则名时写进该规则的变量表（原版单例一张表天然互通，
            // JSEngine.smali:17390）；我方按规则分实例，跨规则时查对方常驻运行时。
            if (args != null && args.length > 1) {
                Map<String, String> target = myVarsFor(args.length > 2 ? argStr(args[2]) : null);
                target.put(myVarKeyFor(argStr(args[0]), args.length > 2 ? argStr(args[2]) : null), stringifyArg(args[1]));
                // 取证日志（一个APP 分类案真机取证用，下版定案后可删）：只记规则标题/键/值，超长截断，抛错不影响主流程
                try {
                    String pv = stringifyArg(args[1]);
                    SpiderDebug.log("hk", "putMyVar rule=%s key=%s value=%s", rule.getTitle(), argStr(args[0]), pv != null && pv.length() > 100 ? pv.substring(0, 100) + "…" : pv);
                } catch (Throwable ignored) {}
            }
            return null;
        });
        // 8.83 JSEngine.java:594：key 非字符串直接回 ""（忽略默认值）
        putFunction("getMyVar", args -> {
            if (args == null || args.length == 0) return "";
            if (!(args[0] instanceof String)) return "";
            // W4：第三参 ruleTitleKey 跨规则读（smali:17390），见 putMyVar 注释。
            String rt = args.length > 2 ? argStr(args[2]) : null;
            Map<String, String> target = myVarsFor(rt);
            String v = target.get(myVarKeyFor((String) args[0], rt));
            // 取证日志（一个APP 分类案真机取证用，下版定案后可删）：只记规则标题/键/读到的值，存里没读到标「空」，超长截断，抛错不影响主流程
            try {
                SpiderDebug.log("hk", "getMyVar rule=%s key=%s value=%s", rule.getTitle(), (String) args[0], v == null ? "空" : (v.length() > 100 ? v.substring(0, 100) + "…" : v));
            } catch (Throwable ignored) {}
            if (v == null && args.length > 1) v = argStr(args[1]);
            return v == null ? "" : v;
        });
        putFunction("clearMyVar", args -> {
            // E8: 8.83 无"无参清空全表"行为（JSEngine.smali:12188），无参时不做任何事，避免误删其他规则的变量
            if (args != null && args.length > 0) myVars.remove(myVarKey(argStr(args[0])));
            return null;
        });
        // listMyVarKeys：8.83（smali:18947，lambda:7220）：按 getRuleTitle(ruleTitleKey)+"@"
        // 过滤本规则的 key，strip 前缀（split("@")[1]）后序列化。旧实现全表裸 keySet。
        putFunction("listMyVarKeys", args -> {
            String title = "";
            if (args != null && args.length > 0 && args[0] != null) title = argStr(args[0]);
            if (title.isEmpty()) {
                try {
                    title = rule != null && rule.getTitle() != null ? rule.getTitle() : "";
                } catch (Throwable ignored) {}
            }
            String prefix = title + "@";
            List<String> out = new ArrayList<>();
            for (String k : myVars.keySet()) {
                if (k != null && k.startsWith(prefix)) {
                    String[] parts = k.split("@");
                    if (parts.length > 1) out.add(parts[1]);
                }
            }
            // returnType=JSON：原版桥接层 JSON.parse 后是真数组（1.3.42 扫尾）。
            return parseJson(GSON.toJson(out));
        });
        putFunction("clearVar", args -> {
            // 8.83 JSEngine.clearVar：只删指定键；无参时直接 return（无"清空全表"行为，
            // 与 clearMyVar 的 E8 注释同源）。以前无视参数全清，会误删其他规则的变量。
            if (args != null && args.length > 0) vars.remove(argStr(args[0]));
            return null;
        });

        // ---- 跨规则公共持久化（setPublicItem/getPublicItem/clearPublicItem） ----
        // 8.83 smali:17688/24926/12255：key/value 须为字符串（缺参/undefined/非字符串抛"参数类型只能是字符串"）；
        // getPublicItem 缺键回默认，无默认则抛（原版先转默认再查表）。
        putFunction("setPublicItem", args -> {
            publicKv.put(mustBeString(args, 0), mustBeString(args, 1));
            savePublicKv();
            return null;
        });
        putFunction("getPublicItem", args -> {
            String key = mustBeString(args, 0);
            String def = mustBeString(args, 1);
            String v = publicKv.get(key);
            return v == null ? def : v;
        });
        putFunction("clearPublicItem", args -> {
            publicKv.remove(mustBeString(args, 0));
            savePublicKv();
            return null;
        });

        // ---- storage0 对象（官方：支持存储 JSON 对象的存储封装） ----
        // put* 用 stringifyArg 把值（含 JSObject）序列化为 JSON 存；get* 存的是 JSON 对象/数组则解析回 JS 对象返回
        // 壳只建一次存进 sharedStorage0：新鲜作用域补注册时直接共用，避免跨 Context 重建与重复挂载。
        // registerApiInto(imageScope) 画像求值单例场景下字段 scope 恒为 null（initImageEngine
        // 从不赋值 scope），此行与 1634/1637 行同 pattern 走守卫，免 Context.newObject(null) 打空。
        // D3：图片求值改走调用线程独立 Context 后，此处用「当前线程已 enter 的 Context」
        // （规则路径下与字段 rhinoCx 是同一对象，语义不变；图片路径下即本次求值的独立 cx）。
        if (sharedStorage0 == null) sharedStorage0 = (ScriptableObject) Context.getCurrentContext().newObject(apiRegTarget != null ? apiRegTarget : scope);
        ScriptableObject storage0 = sharedStorage0;
        putFunction(storage0, "putVar", args -> {
            if (args != null && args.length > 1) vars.put(argStr(args[0]), stringifyArg(args[1]));
            return null;
        });
        putFunction(storage0, "getVar", args -> storage0Get(vars, args));
        putFunction(storage0, "putMyVar", args -> {
            // P0：与全局 putMyVar 同命名空间（myVarKeyFor＋myVarsFor，含第三参规则名口径）。
            // 此前裸键存、全局版「规则名@键」存，写读分家 → 分类页签永不切换。
            if (args != null && args.length > 1) {
                String rt3 = args.length > 2 ? argStr(args[2]) : null;
                Map<String, String> target = myVarsFor(rt3);
                target.put(myVarKeyFor(argStr(args[0]), rt3), stringifyArg(args[1]));
                // 取证日志（与全局版同格式）：只记规则标题/键/值，超长截断，抛错不影响主流程
                try {
                    String pv = stringifyArg(args[1]);
                    SpiderDebug.log("hk", "putMyVar rule=%s key=%s value=%s", rule.getTitle(), argStr(args[0]), pv != null && pv.length() > 100 ? pv.substring(0, 100) + "…" : pv);
                } catch (Throwable ignored) {}
            }
            return null;
        });
        putFunction(storage0, "getMyVar", args -> {
            // P0：与全局 getMyVar 同键处理；保留 storage0 的 JSON 回解析语义（见 storage0GetByKey）。
            if (args == null || args.length == 0) return "";
            String rt3 = args.length > 2 ? argStr(args[2]) : null;
            Map<String, String> target = myVarsFor(rt3);
            String resolved = myVarKeyFor(argStr(args[0]), rt3);
            Object r = storage0GetByKey(target, resolved, args);
            // 取证日志（与全局版同格式）：存里没读到标「空」，超长截断，抛错不影响主流程
            try {
                String raw = String.valueOf(r);
                SpiderDebug.log("hk", "getMyVar rule=%s key=%s value=%s", rule.getTitle(), argStr(args[0]), target.get(resolved) == null ? "空" : (raw.length() > 100 ? raw.substring(0, 100) + "…" : raw));
            } catch (Throwable ignored) {}
            return r;
        });
        putFunction(storage0, "setItem", args -> {
            if (args != null && args.length > 1) {
                kv.put(argStr(args[0]), stringifyArg(args[1]));
                saveKv();
            }
            return null;
        });
        putFunction(storage0, "getItem", args -> storage0Get(kv, args));
        putFunction(storage0, "setPublicItem", args -> {
            if (args != null && args.length > 1) {
                publicKv.put(argStr(args[0]), stringifyArg(args[1]));
                savePublicKv();
            }
            return null;
        });
        putFunction(storage0, "getPublicItem", args -> storage0Get(publicKv, args));
        ScriptableObject.putProperty(apiRegTarget != null ? apiRegTarget : scope, "storage0", storage0);
        // 8.83 crypto-java.js 原生 AES 接线（规则作用域也可用，如文本加签场景）。
        ScriptableObject cryptoTarget = apiRegTarget != null ? apiRegTarget : scope;
        Object cryptoFileUtil = Context.javaToJS(new HkCryptoFileUtil(), cryptoTarget);
        ScriptableObject.putProperty(cryptoTarget, "__cryptoFileUtil", cryptoFileUtil);
        // 8.83 宿主类路径补齐：原版 com.example.hikerview.utils.FileUtil 是包内真实类，
        // 规则在列表/详情/搜索/播放各作用域都直调；星辰已按原包名补回真实类
        // （com.example.hikerview.utils.FileUtil），ImporterTopLevel 作用域原生可达；
        // 共享作用域的 com 是普通 JS 对象、包解析走不到，这里把同一实例挂进对象链
        // 兜底（判定与合并规则见 HkHostClasses）。缺了这步，规则在列表期同步解密
        // 图片直调 FileUtil.toBytes 抛错、被规则 catch 吞掉回退加密原址 → 没封面。
        HkHostClasses.installFileUtilChain(cryptoTarget, cryptoFileUtil);
        injectAndroidPackage(cryptoTarget);

        // ---- 当前结果列表的动态修改（官方经 EventBus 改 UI 列表；本宿主直接改 results） ----
        putFunction("updateItem", args -> {
            // updateItem(id, obj) 或 updateItem(obj)（obj.extra.id / obj.url 作 id）
            // 8.83 JSEngine.java:1167：obj 非 JSON 对象抛"updateItem：格式有误，只支持json"
            String id = null;
            Object obj = null;
            if (args != null && args.length >= 2) {
                Object idArg = args[0];
                if (idArg instanceof String) id = (String) idArg;
                else if (toStrMap(idArg) != null) obj = idArg;
                if (obj == null) obj = args[1];
            } else if (args != null && args.length == 1) {
                obj = args[0];
            }
            if (obj == null) throw new RuntimeException("updateItem：格式有误，只支持json");
            Map<String, Object> m = toStrMap(obj);
            if (m == null) throw new RuntimeException("updateItem：格式有误，只支持json");
            if (id == null) {
                id = str(m, "url");
                if (TextUtils.isEmpty(id)) {
                    Object extra = m.get("extra");
                    if (extra instanceof Map) id = str((Map<String, Object>) extra, "id");
                }
            }
                if (!TextUtils.isEmpty(id)) {
                    int idx = findResultIndex(id);
                    if (idx >= 0) {
                        mergeItem(results.get(idx), m);
                        Logger.t(TAG).d("updateItem: %s", id);
                        // 8.83 JSEngine.updateItem(26507)：post ItemModifyEvent(UPDATE) 经 EventBus，
                        // 内容页收到后就地更新条目（parse 时直接改 results 已够；展示后需同步 UI）
                        postItemModifyEvent(results.get(idx),
                                com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action.UPDATE, null);
                    }
                    if (collectRaw) {
                        for (Map<String, String> raw : rawResults) {
                            if (id.equals(raw.get("url"))) {
                                for (Map.Entry<String, Object> e : m.entrySet()) {
                                    if (!"extra".equals(e.getKey())) raw.put(e.getKey(), String.valueOf(e.getValue()));
                                }
                            }
                        }
                    }
                }
            return null;
        });
        putFunction("addItemAfter", args -> {
            addItemAt(args, true);
            return null;
        });
        putFunction("addItemBefore", args -> {
            addItemAt(args, false);
            return null;
        });
        // 8.83 JSEngine.java:1258：id 非字符串抛"deleteItem：格式有误，请传入ID字符串"；支持 id 数组批量删。
        putFunction("deleteItem", args -> {
            if (args == null || args.length == 0 || args[0] == null)
                throw new RuntimeException("deleteItem：格式有误，请传入ID字符串");
            List<String> ids = new ArrayList<>();
            Object o = args[0];
            if (o instanceof NativeArray) {
                NativeArray na = (NativeArray) o;
                for (int i = 0; i < na.size(); i++) {
                    Object e = na.get(i);
                    if (!(e instanceof String))
                        throw new RuntimeException("deleteItem：格式有误，请传入ID字符串");
                    ids.add((String) e);
                }
            } else if (o instanceof String) {
                ids.add((String) o);
            } else {
                throw new RuntimeException("deleteItem：格式有误，请传入ID字符串");
            }
            for (String id : ids) {
                int idx = findResultIndex(id);
                HkItem removed = null;
                if (idx >= 0) removed = results.remove(idx);
                if (collectRaw) rawResults.removeIf(r -> id.equals(r.get("url")));
                Logger.t(TAG).d("deleteItem: %s", id);
                // 8.83 JSEngine.deleteItem(13478)：post ItemModifyEvent(DELETE) 经 EventBus
                if (removed != null) {
                    postItemModifyEvent(removed,
                            com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action.DELETE, null);
                }
            }
            return null;
        });
        // 8.83 JSEngine.java:1268：cls 非字符串抛"deleteItemByCls：格式有误，请传入cls字符串"
        putFunction("deleteItemByCls", args -> {
            if (args == null || args.length == 0 || !(args[0] instanceof String))
                throw new RuntimeException("deleteItemByCls：格式有误，请传入cls字符串");
            String cls = (String) args[0];
            results.removeIf(it -> cls.equals(it.getColType()));
            if (collectRaw) rawResults.removeIf(r -> cls.equals(r.get("col_type")));
            Logger.t(TAG).d("deleteItemByCls: %s", cls);
            // 8.83 JSEngine.deleteItemByCls(13643)：post ItemModifyEvent(DELETE) + setCls 经 EventBus
            postItemModifyEvent(null,
                    com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action.DELETE, cls);
            return null;
        });
        // 官方 clearItem(key) 清的是规则持久化存储里的 key；本宿主 kv.json 即规则存储，对其删 key 等价。
        putFunction("clearItem", args -> {
            if (args != null && args.length > 0 && args[0] != null) {
                kv.remove(argStr(args[0]));
                saveKv();
                Logger.t(TAG).d("clearItem: %s", args[0]);
            }
            return null;
        });

        // ---- 私有加密 JS：8.83（JSEngine.java:2711）：AesUtil.decrypt(AES_DEFAULT_KEY, code) 后求值 ----
        putFunction("evalPrivateJS", args -> {
            if (args == null || args.length == 0) return null;
            try {
                String plain = aesUtilCrypt(false, AES_DEFAULT_KEY, argStr(args[0]));
                if (TextUtils.isEmpty(plain)) return null;
                return evalJs(plain);
            } catch (Throwable e) {
                Logger.t(TAG).d("evalPrivateJS failed: %s", e.getMessage());
                return null;
            }
        });

        // ---- W2：fc/fetchCache 按 8.83 require0 代码缓存重做 ----
        // 原版 fc = require0(url, h, options, ruleTitleKey, evalKey=false, v)，returnType=STRING
        // （JSEngine.smali:3554-3563）：支持 js:/file:///hiker://page/ 前缀、本地 libs/<md5>.js 缓存、
        // 版本校验、h 默认 24 小时、h==0 强制重下。我方之前是纯 URL body 文件缓存，走样。
        // 软失败口径保留（取不到回 ""，不抛错打死整页，见 require 处注释）。
        putFunction("fc", args -> fcImpl(args));
        // 官方全名：fetchCache 即 fc
        putFunction("fetchCache", args -> fcImpl(args));
        // W5（8.83 require0:3702/3719）：requireCache 求值走调用方作用域——照 require 同款修法（UU 1.3.30）。
        putFunctionEx("rc", (cx, scope, args) -> requireCacheEval(args, cx, scope));
        // 官方全名：requireCache 即 rc
        putFunctionEx("requireCache", (cx, scope, args) -> requireCacheEval(args, cx, scope));
        // ---- 批量（bf=batchFetch 并发取回 body 数组；be=batchExecute 预加载；bcm 返回原地址） ----
        // W7：8.83 JSEngine.batchExecute (smali:11005, 2230-2266)：线程数 min(n,16)，
        // CountDownLatch 总等待 n/16*10+10 秒，按索引组装结果。我方之前固定 4 线程＋每项 30s。
        putFunction("bf", args -> batchFetchImpl(args));
        // 官方全名：batchFetch 即 bf（并发取回 body 数组）
        putFunction("batchFetch", args -> batchFetchImpl(args));
        // ------------------------------------------------------------------
        // 8.83: batchExecute(Object tasks, Object listener0, Object success, Object ruleKey)V
        // [JSEngine.smali 11276-11680]  alias "be". D2: 真实实现（替代 no-op）。
        // 语义：tasks 为 [{id, func, param}]，并发在独立 Rhino Context 里执行各
        // func(param)；每完成一个，若 listener0.func 存在且完成数 <= success，
        // 则回调 listener0.func(listener0.param, id, false, result)；返回 "break"
        // 则停止后续回调。等待 (n/16*30+30)s 后取消并关闭线程池。
        // 跨小程序内容搜索（"我的主页"规则）依赖此实现。
        // ------------------------------------------------------------------
        putFunction("be", args -> {
            batchExecuteImpl(args);
            return null;
        });
        // 官方全名：batchExecute 即 be
        putFunction("batchExecute", args -> {
            batchExecuteImpl(args);
            return null;
        });
        putFunction("bcm", args -> batchCacheM3u8Impl(args));
        // 官方全名：batchCacheM3u8 即 bcm（8.83 JSEngine.java:1943，returnType=JSON）
        putFunction("batchCacheM3u8", args -> batchCacheM3u8Impl(args));
        // ---- PC 模式请求（桌面 UA）与 postRequest（即 post） ----
        putFunction("fetchPC", args -> {
            if (args == null || args.length == 0) return "";
            String url = argStr(args[0]);
            String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : "{}";
            return fetchSync(url, mergeDesktopUa(options));
        });
        // 官方 postPC：桌面 UA 的 POST
        putFunction("postPC", args -> {
            if (args == null || args.length == 0) return "";
            String url = argStr(args[0]);
            String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : "{}";
            options = applyGeneratePostOps(options);
            return fetchSync(url, mergeDesktopUa(options));
        });
        // 官方 fetchCookie(url, options)：带 withHeaders 抓取，返回 set-cookie
        putFunction("fetchCookie", args -> {
            if (args == null || args.length == 0) return "";
            try {
                String url = argStr(args[0]);
                String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : "{}";
                // 强制 withHeaders
                if (!options.contains("withHeaders")) {
                    options = options.trim();
                    if (options.endsWith("}")) options = options.substring(0, options.length() - 1) + ",\"withHeaders\":true}";
                    else options = "{\"withHeaders\":true}";
                }
                String result = fetchSync(url, options);
                if (TextUtils.isEmpty(result)) return "";
                Map<String, Object> map = GSON.fromJson(result, new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
                if (map == null) return "";
                Object headers = map.get("headers");
                if (!(headers instanceof Map)) return "";
                Map<String, Object> hm = (Map<String, Object>) headers;
                // 8.83（JSEngine.java:2033）：取 headers 的 set-cookie 全量值（三种大小写），
                // 返回其 JSON 数组字符串；无 cookie / 失败返回 ""。旧实现截成"名=值; "拼接串是错的。
                List<String> cookies = new ArrayList<>();
                for (Map.Entry<String, Object> e : hm.entrySet()) {
                    if (e.getKey() != null && e.getKey().equalsIgnoreCase("set-cookie") && e.getValue() != null) {
                        Object v = e.getValue();
                        if (v instanceof List) {
                            for (Object one : (List<?>) v) {
                                if (one != null) cookies.add(String.valueOf(one));
                            }
                        } else {
                            cookies.add(String.valueOf(v));
                        }
                    }
                }
                if (cookies.isEmpty()) return "";
                return GSON.toJson(cookies);
            } catch (Throwable e) {
                Logger.t(TAG).d("fetchCookie failed: %s", e.getMessage());
                return "";
            }
        });
        putFunction("postRequest", args -> {
            if (args == null || args.length == 0) return "";
            String url = argStr(args[0]);
            String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : "{}";
            // 8.83 post0(request=true)：generatePostOps 之后再走 generateRequestOptions（缺 UA 时补移动端 UA）
            return fetchSync(url, injectMobileUa(applyGeneratePostOps(options)));
        });
        // fcbw/ewr：WebView 取码与 Web 规则执行
        // 8.83 原文：
        //   fetchCodeByWebView(url, options) (JSEngine.smali:15513，别名 fcbw)：
        //     用 WebkitFetcher（WebView）在后台加载 url，取 JS 渲染后的页面源码；
        //     options.timeout（1000-30000ms，默认 30000ms）；同步等待 CountDownLatch；返回源码字符串。
        //   executeWebRule(url, rule, options) (JSEngine.smali:15132，别名 ewr)：
        //     用 WebkitExecutor 在 WebView 里执行另一条 web 规则；同上超时机制；返回执行结果字符串。
        // 移植：HkWebkitFetcher（WebView 主线程加载 + CountDownLatch 同步等待，语义与 8.83 一致）
        putFunction("fetchCodeByWebView", args -> {
            try {
                String url = args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
                if (url.isEmpty()) return "";
                java.util.Map<String, Object> options = jsOptions(args, 1);
                return HkWebkitFetcher.fetchCodeByWebView(url, options);
            } catch (Throwable t) {
                return "";
            }
        });
        putFunction("fcbw", args -> {
            try {
                String url = args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
                if (url.isEmpty()) return "";
                java.util.Map<String, Object> options = jsOptions(args, 1);
                return HkWebkitFetcher.fetchCodeByWebView(url, options);
            } catch (Throwable t) {
                return "";
            }
        });
        putFunction("executeWebRule", args -> {
            try {
                String url = args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
                String rule = args != null && args.length > 1 && args[1] != null ? String.valueOf(args[1]) : "";
                if (url.isEmpty()) return "";
                java.util.Map<String, Object> options = jsOptions(args, 2);
                return HkWebkitFetcher.executeWebRule(url, rule, options);
            } catch (Throwable t) {
                return "";
            }
        });
        putFunction("ewr", args -> {
            try {
                String url = args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
                String rule = args != null && args.length > 1 && args[1] != null ? String.valueOf(args[1]) : "";
                if (url.isEmpty()) return "";
                java.util.Map<String, Object> options = jsOptions(args, 2);
                return HkWebkitFetcher.executeWebRule(url, rule, options);
            } catch (Throwable t) {
                return "";
            }
        });
        stub("cacheCode0", null);
        // setPageReverse：官方设置页面倒序标记；宿主存 kv 供规则自查
        // 8.83 JSEngine.setPageReverse(4140)：post OnReversePageEvent(reverse) 经 EventBus，FilmListActivity.onSetReverse 接收后调 reverseArticleList
        putFunction("setPageReverse", args -> {
            setPageReverseDo(args);
            return null;
        });
        // W1：8.83 里 refreshPage 与 setPageReverse 同挂别名 "refresh"
        // （JSEngine.smali:22084 refreshPage / :24779 setPageReverse），注册顺序靠后者胜，
        // 别名 refresh 实际指向倒序页面语义（OnReversePageEvent，smali:4140）。
        // 注：别名赢家依赖原版方法声明顺序，ART 上为声明顺序，非规范保证
        // （对差表"没定死"第 5 项）。
        putFunction("refresh", args -> {
            setPageReverseDo(args);
            return null;
        });

        // ---- 文件（作用域限定 App 文件目录；hiker://files/ 映射到 filesDir） ----
        putFunction("writeFile", args -> {
            if (args != null && args.length > 1) writeFileContent(resolveHikerPath(argStr(args[0])), argStr(args[1]));
            return null;
        });
        putFunction("readFile", args -> {
            // 8.83 JSEngine.java:2132：第 2 参是 ruleTitleKey；含分隔符公开读，否则 basename+规则目录私有读。
            // 兼容说明：星辰曾把第 2 参当 charset 用。双重解释——合法字符集名当 charset（兼容星辰旧规则），
            // 否则当 ruleTitleKey（8.83 语义；规则标题不可能是字符集名，两边不冲突）。
            if (args == null || args.length == 0) return "";
            String p = argStr(args[0]);
            String second = args.length > 1 && args[1] != null ? argStr(args[1]) : null;
            String charset = null;
            String rt = null;
            if (second != null && !second.isEmpty()) {
                if (isCharsetName(second)) charset = second;
                else rt = second;
            }
            if (p.contains("/")) {
                return readFileContent(resolveHikerPath(p), charset);
            }
            String base = p.substring(p.lastIndexOf('/') + 1);
            if (base.isEmpty()) base = p;
            return readFileContent(new File(ruleDirForTitle(rt), base).getAbsolutePath(), charset);
        });
        putFunction("fileExist", args -> {
            // 8.83 JSEngine.java:1574 @JSAnnotation(returnType=BOOL)：JS 侧拿到的是真布尔。
            // 曾回字符串 "false"——JS 里非空字符串为真，红果代理 if(!fileExist(...)) 永不成立、
            // 密文从不下载、只回 45 字节报错文本（真机+字节级实证）。全局 miss 再查按规则 libs。
            if (args == null || args.length == 0) return Boolean.FALSE;
            String p = argStr(args[0]);
            if (new File(resolveHikerPath(p)).exists()) return Boolean.TRUE;
            return resolvePerRuleLibFile(p) != null ? Boolean.TRUE : Boolean.FALSE;
        });
        // 官方别名：exist 即 fileExist
        putFunction("exist", args -> {
            if (args == null || args.length == 0) return Boolean.FALSE;
            String p = argStr(args[0]);
            if (new File(resolveHikerPath(p)).exists()) return Boolean.TRUE;
            return resolvePerRuleLibFile(p) != null ? Boolean.TRUE : Boolean.FALSE;
        });
        // getPath(filePath)：8.83（JSEngine.java:2105）恒返回 "file://" + getFilePath(filePath)。
        // 内部文件操作（writeFile/readFile/exists/delete/download）都直接调 resolveHikerPath，
        // 不走 JS 可见的 getPath，故加前缀无连环影响。空输入仍回 ""。
        putFunction("getPath", args -> {
            if (args == null || args.length == 0) return "";
            String p = resolveHikerPath(argStr(args[0]));
            if (TextUtils.isEmpty(p)) return "";
            return "file://" + p;
        });
        putFunction("saveFile", args -> {
            // 8.83 JSEngine.java:2120：hiker://files/ 走公开路径；否则取 basename 存入规则目录；第 3 参为 ruleTitleKey。
            if (args != null && args.length > 1) {
                String p = argStr(args[0]);
                String content = argStr(args[1]);
                String rt = args.length > 2 && args[2] != null ? argStr(args[2]) : null;
                if (p.startsWith("hiker://files/")) {
                    writeFileContent(resolveHikerPath(p), content);
                } else {
                    String base = p.substring(p.lastIndexOf('/') + 1);
                    if (base.isEmpty()) base = p;
                    writeFileContent(new File(ruleDirForTitle(rt), base).getAbsolutePath(), content);
                }
            }
            return null;
        });
        // 8.83 JSEngine.java:2145：含分隔符公开删 + 私有删两份都删；无分隔符只删私有（basename+规则目录）。
        putFunction("deleteFile", args -> {
            try {
                if (args != null && args.length > 0) {
                    String p = argStr(args[0]);
                    String rt = args.length > 1 && args[1] != null ? argStr(args[1]) : null;
                    if (p.contains("/")) {
                        new File(resolveHikerPath(p)).delete();
                    }
                    String base = p.substring(p.lastIndexOf('/') + 1);
                    if (!base.isEmpty()) new File(ruleDirForTitle(rt), base).delete();
                }
            } catch (Throwable ignored) {}
            return null;
        });

        // 8.83 JSEngine.java:2514 downloadFile：失败抛 Exception("下载文件失败：…")；第 3 参 headers。
        putFunction("downloadFile", args -> {
            if (args == null || args.length < 2) throw new RuntimeException("下载文件失败：参数不足");
            String url = argStr(args[0]);
            String dest = resolveHikerPath(argStr(args[1]));
            String headers = args.length > 2 && args[2] != null ? stringifyArg(args[2]) : "{}";
            downloadToFile(url, dest, headers);
            return null;
        });

        // ---- 剪贴板 / 分享 ----
        putFunction("copy", args -> {
            try {
                if (args != null && args.length > 0) {
                    ClipboardManager cm = (ClipboardManager) App.get().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("hk", argStr(args[0])));
                }
            } catch (Throwable e) {
                Logger.t(TAG).d("copy failed: %s", e.getMessage());
            }
            return null;
        });
        putFunction("parsePaste", args -> {
            // 解析分享文本：云口令则拉取规则 JSON 返回；内容不是规则时（如聚合接口列表）
            // 返回通道粘贴文本原文（8.83 RuleImporterManager.parseSync 语义）；非云口令返回原文
            if (args == null || args.length == 0) return "";
            String t = argStr(args[0]).trim();
            try {
                if (HkRuleManager.isCloudCode(t)) {
                    HkRule r = HkRuleManager.get().importByCloudCode(t);
                    Logger.t(TAG).d("parsePaste: cloud code imported %s", r.getTitle());
                    return GSON.toJson(r);
                }
            } catch (Throwable e) {
                Logger.t(TAG).d("parsePaste cloud failed: %s", e.getMessage());
                try {
                    String content = HkRuleManager.get().fetchPasteRawText(t);
                    if (content != null && !content.isEmpty()) return content;
                } catch (Throwable ignored) {
                }
            }
            return t;
        });
        putFunction("getPastes", args -> {
            // 8.83 JSEngine.getPastes：returnType=JSON。星辰无剪贴板分享能力，回真数组（1.3.42 扫尾）。
            return toJsArray(new ArrayList<>());
        });
        putFunction("sharePaste", args -> {
            Logger.t(TAG).d("sharePaste: no-op in host");
            return "";
        });

        // ---- m3u8 ----
        putFunction("fixM3u8", args -> {
            if (args == null || args.length < 2) return args != null && args.length > 0 ? argStr(args[0]) : "";
            return fixM3u8Content(argStr(args[0]), argStr(args[1]));
        });
        // 8.83 JSEngine.java:1928 cacheM3u8：真下载索引文件到 hiker://files/cache/，返回 "file##url"；非 m3u8/等价时返原 url。
        putFunction("cacheM3u8", args -> {
            String url = args != null && args.length > 0 ? argStr(args[0]) : "";
            String options = args != null && args.length > 1 && args[1] != null ? stringifyArg(args[1]) : null;
            String fileName = args != null && args.length > 2 && args[2] != null && !TextUtils.isEmpty(argStr(args[2]))
                    ? argStr(args[2]) : "video.m3u8";
            String file = downloadM3u8File(url, fileName, options);
            return file.equals(url) ? url : file + "##" + url;
        });

        // proxyClearM3u8：移植自 8.83 JSEngine.proxyClearM3u8 (JSEngine.smali:21401)。
        // 语义：proxyClearM3u8(u, h, r) 去 m3u8 广告片段。
        //   u: m3u8 URL；h: headers Map（可选，取 "headers"/"header" 子键）；r: 广告规则 JSONArray（可选）。
        //   返回：清理后的内容存为 hiker://files/_cache/<md5>.m3u8，返回 "file://<path>##<原URL>"；
        //   若清理结果为空则返回原 URL。
        // 依赖：8.83 的 M3u8ClearUtil.getProxyContent（2193 行，smali_classes2）。
        //   完整移植见 HkM3u8ClearUtil.java（待搬运）。
        putFunction("proxyClearM3u8", args -> {
            try {
                if (args == null || args.length == 0 || args[0] == null) return "";
                String url = argStr(args[0]);
                if (url == null || url.isEmpty() || "undefined".equals(url)) return "";
                // h 参数：headers Map（8.83 从 "headers" 或 "header" 子键取）
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                if (args.length > 1 && args[1] != null) {
                    Object h = args[1];
                    // Rhino NativeObject -> Map 转换（复用现有 argMap 逻辑）
                    java.util.Map<?, ?> hm = toStringMap(h);
                    if (hm != null) {
                        Object sub = hm.get("headers");
                        if (sub == null) sub = hm.get("header");
                        java.util.Map<?, ?> sm = toStringMap(sub);
                        if (sm != null) {
                            for (java.util.Map.Entry<?, ?> e : sm.entrySet()) {
                                if (e.getKey() != null && e.getValue() != null) {
                                    headers.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                                }
                            }
                        }
                    }
                }
                // r 参数：广告规则 JSONArray（8.83 传 List<M3u8ClearAdRule>）
                java.util.List<HkM3u8ClearAdRule> adRules = null;
                if (args.length > 2 && args[2] != null) {
                    try {
                        String rJson = argStr(args[2]);
                        if (rJson != null && !rJson.isEmpty() && !"undefined".equals(rJson)) {
                            adRules = new com.google.gson.Gson().fromJson(rJson,
                                    com.google.gson.reflect.TypeToken.getParameterized(
                                            java.util.List.class, HkM3u8ClearAdRule.class).getType());
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (adRules == null) adRules = HkM3u8ClearUtil.getRules();
                String cleaned = HkM3u8ClearUtil.getProxyContent(url, headers, adRules);
                if (cleaned == null || cleaned.isEmpty()) {
                    return url; // 8.83：清理结果为空返回原 URL
                }
                // 存缓存文件，返回 file://## 格式（8.83 JSEngine.smali:21580-21620）
                String name = md5(url) + ".m3u8";
                java.io.File cacheDir = new java.io.File(App.get().getFilesDir(), "hk_m3u8_cache");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                java.io.File out = new java.io.File(cacheDir, name);
                try (java.io.FileWriter fw = new java.io.FileWriter(out)) {
                    fw.write(cleaned);
                }
                com.fongmi.android.tv.api.hk.HkM3u8ClearUtil.pruneCacheDir(cacheDir);
                return "file://" + out.getAbsolutePath() + "##" + url;
            } catch (Throwable e) {
                Logger.t(TAG).d("proxyClearM3u8 failed: %s", e.getMessage());
                return args != null && args.length > 0 && args[0] != null ? argStr(args[0]) : "";
            }
        });

        // ---- 页面信息 ----
        // setPageTitle：8.83 JSEngine.setPageTitle(2758)：post SetPageTitleEvent(title) 经 EventBus
        putFunction("setPageTitle", args -> {
            if (args != null && args.length > 0) {
                pageTitle = argStr(args[0]);
                try {
                    org.greenrobot.eventbus.EventBus.getDefault().post(new com.fongmi.android.tv.api.hk.event.home.SetPageTitleEvent(pageTitle));
                } catch (Throwable ignored) {
                }
            }
            return null;
        });
        // 8.83 smali:17466：取当前 Activity.getTitle()；拿不到/为空时回 setPageTitle 存的值
        putFunction("getPageTitle", args -> {
            try {
                android.app.Activity a = App.activity();
                if (a != null) {
                    CharSequence t = a.getTitle();
                    if (t != null && t.length() > 0) return t.toString();
                }
            } catch (Throwable ignored) {
            }
            return pageTitle;
        });
        putFunction("getHome", args -> {
            if (args == null || args.length == 0) return "";
            return homeOf(argStr(args[0]));
        });
        // 8.83 JSEngine.java:1966：不做 URL 编码（HttpHelper.buildParamStr：k=v&k2=v2，非字符串值 JSON 序列化）；
        // url 为空只返回参数串（无前导 ?）
        putFunction("buildUrl", args -> {
            if (args == null || args.length == 0) return "";
            String url = argStr(args[0]);
            String body = "";
            if (args.length >= 2 && args[1] != null) {
                try {
                    String json = stringifyArg(args[1]);
                    Map<String, Object> map = GSON.fromJson(json, new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
                    if (map != null && !map.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (Map.Entry<String, Object> e : map.entrySet()) {
                            if (e.getValue() == null) continue;
                            if (sb.length() > 0) sb.append('&');
                            Object v = e.getValue();
                            sb.append(e.getKey()).append('=')
                                    .append(v instanceof String ? (String) v : GSON.toJson(v));
                        }
                        body = sb.toString();
                    }
                } catch (Throwable ignored) {
                }
            }
            if (url == null || url.isEmpty() || "undefined".equals(url)) return body;
            if (url.contains("?")) return url + "&" + body;
            return url + "?" + body;
        });
        putFunction("joinUrl", args -> {
            if (args == null || args.length < 2) return args != null && args.length > 0 ? argStr(args[0]) : "";
            try {
                return new java.net.URI(argStr(args[0])).resolve(argStr(args[1])).toString();
            } catch (Throwable e) {
                return argStr(args[1]);
            }
        });
        // getColTypes：8.83（JSEngine.java:2495）ReturnType.JSON（桥接层把 JSON 串转成真数组给 JS）。
        // 星辰旧实现回 GSON 串，putFunction 经 Rhino 直接透传——JS 拿到的是字符串不是数组。
        // 改为直接回真数组（toJsArray 原生对象通道），与 8.83 的 JS 可见形态一致。
        putFunction("getColTypes", args -> toJsArray(COL_TYPES));

        // ---- 编解码补充 ----
        putFunction("md5", args -> {
            if (args == null || args.length == 0) return "";
            String v = Util.md5(argStr(args[0]));
            return v == null ? "" : v;
        });
        // 官方 _base64 全局：8.83 JSEngine 注入的 MyBase64 实例（DEFAULT/NO_WRAP 常量 + 编解码方法），
        // 规则如 CryptoUtil.Data.parseBase64(s, _base64.NO_WRAP) 依赖其存在
        putFunction("__b64encodeToString", args -> {
            if (args == null || args.length == 0) return "";
            try {
                byte[] data = args[0] instanceof byte[] ? (byte[]) args[0] : argStr(args[0]).getBytes("UTF-8");
                int flags = args.length > 1 ? (int) Double.parseDouble(argStr(args[1])) : android.util.Base64.DEFAULT;
                return android.util.Base64.encodeToString(data, flags);
            } catch (Throwable e) {
                return "";
            }
        });
        putFunction("__b64decode", args -> {
            if (args == null || args.length == 0) return toJsArray(new byte[0]);
            try {
                int flags = args.length > 1 ? (int) Double.parseDouble(argStr(args[1])) : android.util.Base64.DEFAULT;
                return toJsArray(android.util.Base64.decode(argStr(args[0]), flags));
            } catch (Throwable e) {
                return toJsArray(new byte[0]);
            }
        });
        try {
            evalJsInSharedScope(
                "var _base64 = {\n" +
                "  DEFAULT: 0,\n" +
                "  NO_PADDING: 1,\n" +
                "  NO_WRAP: 2,\n" +
                "  CRLF: 4,\n" +
                "  URL_SAFE: 8,\n" +
                "  encodeToString: function(d, f) { return __b64encodeToString(d, f); },\n" +
                "  decode: function(s, f) { return __b64decode(s, f); },\n" +
                "  encode: function(d, f) { return __b64decode(__b64encodeToString(d, f)); },\n" +
                "  decodeToString: function(s, f) { var b = __b64decode(s, f); var r = ''; for (var i = 0; i < b.length; i++) r += String.fromCharCode(b[i] & 255); return r; }\n" +
                "};\n"
            );
        } catch (Throwable e) {
            Logger.t(TAG).d("_base64 inject failed: " + e.getMessage());
        }
        putFunction("hexToBase64", args -> {
            if (args == null || args.length == 0) return "";
            return hexToB64(argStr(args[0]));
        });
        putFunction("hexToBytes", args -> {
            if (args == null || args.length == 0) return toJsArray(new byte[0]);
            try {
                String h = argStr(args[0]).trim();
                byte[] b = new byte[h.length() / 2];
                for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
                return toJsArray(b);
            } catch (Throwable e) {
                return toJsArray(new byte[0]);
            }
        });
        putFunction("rsaEncrypt", args -> {
            if (args == null || args.length < 2) return "";
            return rsaCrypto(true, argStr(args[0]), argStr(args[1]));
        });
        putFunction("rsaDecrypt", args -> {
            if (args == null || args.length < 2) return "";
            return rsaCrypto(false, argStr(args[0]), argStr(args[1]));
        });
        putFunction("getCryptoJS", args -> {
            // 官方语义：返回内置 CryptoJS 库（aes.js）源码，规则 eval(getCryptoJS()) 后使用
            String code = loadAsset("aes.js");
            if (code == null) {
                Logger.t(TAG).d("getCryptoJS: aes.js missing in assets");
                return "";
            }
            return code;
        });
        // B32：8.83 smali:26129 的入口守卫字面语义是反的——非 undefined 输入直接回 null，
        // 规范化分支不可达（undefined 进去反而在 tryGetString 抛错），且该 APK 内无任何调用方，
        // 疑似原版 bug。按函数本意实现 sane 语义：null/undefined→null；""→""；解析失败→"null"；
        // 成功→规范化重序列化。原版中段（isEmpty→""、parse 失败→"null"、成功→toJSONString）逐字对齐。
        putFunction("toCorrectJSONString", args -> {
            if (args == null || args.length == 0 || args[0] == null) return null;
            Object o = args[0];
            if (!(o instanceof String)) throw new RuntimeException("参数类型只能是字符串");
            String t = (String) o;
            if (t.isEmpty()) return "";
            try {
                Object parsed = GSON.fromJson(t, Object.class);
                if (parsed == null) return "null";
                return GSON.toJson(parsed);
            } catch (Throwable e) {
                return "null";
            }
        });
        // 8.83 smali:18729 justTestSign(appId, devId, userId)：三参 tryGetString；
        // 拼 "appId:devId:userId:0" 后用设备 AndroidKeyStore 的 EC 密钥做 SHA256withECDSA 签名，
        // 返回 <签名hex后128位>01##04<公钥hex>（EcdsaSha256Kt.sign 等价）。
        // 注：原版 catch 块疑似笔误（异常时回 devId），此处异常回 ""。
        putFunction("justTestSign", args -> {
            try {
                String appId = mustBeString(args, 0);
                String devId = mustBeString(args, 1);
                String userId = mustBeString(args, 2);
                return ecdsaSignDevice(appId + ":" + devId + ":" + userId + ":0");
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                Logger.t(TAG).d("justTestSign failed: %s", e.getMessage());
                return "";
            }
        });

        // ---- 网络杂项 ----
        // getCookie(url)：8.83（smali:16625 → CookieJarUtil.getCookie）：
        // 取 Application 的 cookie 池 loadForRequest(httpUrl)，拼成 "name=value; name=value"，无则空串。
        // 旧注记说宿主没维护池子是错的——HkCookieJar（照 8.83 CookieJarImpl 移植）早已接入 HkHttp 的 OkHttp。
        putFunction("getCookie", args -> {
            try {
                String url = args != null && args.length > 0 ? argStr(args[0]) : "";
                if (TextUtils.isEmpty(url)) return "";
                okhttp3.HttpUrl httpUrl = okhttp3.HttpUrl.parse(url);
                if (httpUrl == null) return "";
                StringBuilder sb = new StringBuilder();
                for (okhttp3.Cookie c : HkCookieJar.get(App.get()).loadForRequest(httpUrl)) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(c.name()).append("=").append(c.value());
                }
                return sb.toString();
            } catch (Throwable e) {
                Logger.t(TAG).d("getCookie failed: %s", e.getMessage());
                return "";
            }
        });
        putFunction("getIP", args -> getLocalIp());
        // ipping(ip, h)：8.83（smali:18432，java:4008）：h 取整（缺省 3000）；
        // InetAddress.getByName(ip).isReachable(timeout)，异常打日志回 false。
        putFunction("ipping", args -> {
            try {
                String ip = args != null && args.length > 0 ? argStr(args[0]) : "";
                int timeout = 3000;
                if (args != null && args.length > 1) {
                    Object h = argsNativeObjectAdjust(args[1]);
                    if (h instanceof Number) timeout = ((Number) h).intValue();
                }
                return java.net.InetAddress.getByName(ip).isReachable(timeout);
            } catch (Throwable e) {
                Logger.t(TAG).d("ipping failed: %s", e.getMessage());
                return false;
            }
        });
        // 8.83 JSEngine.isLogin：恒返回字符串 "true"（此前写反成 false，按登录态分流的规则全走错分支）
        // 8.83 smali:18539：returnType=BOOL，桥接把 "true" 转为真布尔；JS 可见为 boolean true
        putFunction("isLogin", args -> Boolean.TRUE);

        // ---- 事件监听（官方 onClose 等；宿主仅记录，不触发） ----
        putFunction("addListener", args -> {
            try {
                if (args != null && args.length > 1) {
                    listeners.put(argStr(args[0]), argStr(args[1]));
                    Logger.t(TAG).d("addListener: %s", args[0]);
                }
            } catch (Throwable ignored) {}
            return null;
        });
        // 官方别名：listen 即 addListener
        putFunction("listen", args -> {
            try {
                if (args != null && args.length > 1) listeners.put(argStr(args[0]), argStr(args[1]));
            } catch (Throwable ignored) {}
            return null;
        });

        // ---- 选择弹窗（官方弹 UI；宿主无 UI，no-op） ----
        putFunction("showSelectOptions", args -> {
            Logger.t(TAG).d("showSelectOptions: no-op in host");
            return null;
        });

        // ---- 下划线内部别名：8.83（smali:10148）三参全透传 HtmlParser.parseDomForUrl(html, rule, urlKey)，
        // urlKey 用于拼绝对地址。pd 走 CommonParser 口径，_pd 走 HtmlParser 口径，两套不得互串。
        putFunction("_pd", args -> {
            if (args == null || args.length < 2) return "";
            String urlKey = args.length > 2 ? argStr(args[2]) : "";
            return parser.hpdh(argStr(args[0]), sel(args[1]), urlKey);
        });
        putFunction("_pdfh", args -> {
            if (args == null || args.length < 2) return "";
            return parser.hpdh(argStr(args[0]), sel(args[1]), "");
        });
        putFunction("_pdfa", args -> {
            if (args == null || args.length < 2) return toJsArray(new ArrayList<>());
            return toJsArray(parser.hpda(argStr(args[0]), sel(args[1])));
        });
        // _pdfl(html, listRule, htmlRule, texts, urls)：8.83（HtmlParser.smali:569）：
        // 每项 = parseDomForUrl(itemHtml, htmlRule, "").trim() + "$" + parseDomForUrl(itemHtml, texts, urls)，
        // 返回 text$url 真数组。旧实现只调 pdfa 丢了 texts/urls（每项只剩裸 html）。
        putFunction("_pdfl", args -> {
            if (args == null || args.length < 2) return toJsArray(new ArrayList<>());
            String htmlRule = args.length > 2 ? argStr(args[2]) : "";
            String texts = args.length > 3 ? argStr(args[3]) : "";
            String urls = args.length > 4 ? argStr(args[4]) : "";
            return toJsArray(parser.hpdfl(argStr(args[0]), sel(args[1]), htmlRule, texts, urls));
        });
        // _findItem(id)：8.83（JSEngine.java:1280）按 id 查当前条目列表（ItemFindEvent 机制），
        // 找到返回条目对象、找不到 null。星辰旧实现恒 ""。
        // 条目来源用运行时现有的当前列表持有处 results（id 存 extra.id，见 _findItemsByCls 同源）。
        // returnType=JSON：原版桥接层 JSON.parse 后是真 JS 对象；此处把 Map 经 parseJson 转成
        // 带原型的真对象（1.3.42 UU 案同图），不用 Rhino 的 NativeJavaObject 包装。
        putFunction("_findItem", args -> {
            try {
                String id = args != null && args.length > 0 ? argStr(args[0]) : "";
                for (HkItem it : results) {
                    if (id.equals(it.getExtra("id"))) {
                        return parseJson(GSON.toJson(itemToFullMap(it)));
                    }
                }
                return null;
            } catch (Throwable e) {
                return null;
            }
        });
        // findItemsByCls/deleteItemByCls：按条目 extra.cls 匹配当前已收集结果
        // B35：8.83 _findItemsByCls 回 ArticleList 完整序列化（含 content/extra/type 等），此处按 HkItem 现有字段补齐
        // returnType=JSON：原版桥接层 JSON.parse 后是真数组，此处走 parseJson（1.3.42 UU 案同图）。
        putFunction("_findItemsByCls", args -> {
            try {
                String cls = args != null && args.length > 0 ? argStr(args[0]) : "";
                List<Map<String, Object>> out = new ArrayList<>();
                for (HkItem it : results) {
                    if (cls.equals(it.getExtra("cls"))) out.add(itemToFullMap(it));
                }
                return parseJson(GSON.toJson(out));
            } catch (Throwable e) {
                return toJsArray(new ArrayList<>());
            }
        });

        // ---- xpath/xpathArray（xpa）：8.83 JSEngine 同款 javax.xml 真 XPath 求值 ----
        // 此前是空桩（借口宿主 Parser 不支持），XML/RSS 类规则必死
        // 8.83 JSEngine.java:2971：失败 throws Exception（靠 try/catch 判失败的规则进 catch）
        putFunction("xpath", args -> {
            if (args == null || args.length < 2) throw new RuntimeException("xpath：参数不足");
            try {
                javax.xml.xpath.XPath xp = javax.xml.xpath.XPathFactory.newInstance().newXPath();
                Object r = xp.evaluate(argStr(args[1]), parseXmlDoc(argStr(args[0])));
                return r == null ? "" : String.valueOf(r);
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException("xpath 解析失败：" + e.getMessage());
            }
        });
        putFunction("xpa", args -> xpathArrayImpl(args));
        // 官方全名：xpathArray 即 xpa
        putFunction("xpathArray", args -> xpathArrayImpl(args));

        // ---- refreshPage：照官方 JSEngine.refreshPage——置标记（供 HkRouter 的变化检测读）
        // 之外，直接发 OnRefreshPageEvent 事件让当前列表页重刷（官方就是事件驱动；
        // 此前只有标记、新列表模块无人消费，脚本调了等于没调）。
        putFunction("refreshPage", args -> {
            refreshRequested = true;
            refreshToTop = refreshScrollTop(args);
            long now = System.currentTimeMillis();
            if (now - lastRefreshPageAt >= 200) {
                lastRefreshPageAt = now;
                org.greenrobot.eventbus.EventBus.getDefault().post(
                        new com.fongmi.android.tv.api.hk.event.home.OnRefreshPageEvent(refreshToTop));
            }
            Logger.t(TAG).d("refreshPage(%s) requested", refreshToTop);
            return null;
        });

        // ---- goTo(url)：页面导航。规则（如 xhotvideo）在 @rule=js: 回调里调
        // goTo('hiker://page/detail?rule=X&u=Y') 跳 V4 详情页。宿主实现为"导航请求"
        // 标记：调用方在 JS 求值后用 consumeGoToRequest() 取走并处理。
        putFunction("goTo", args -> {
            goToUrl = args != null && args.length > 0 ? argStr(args[0]) : "";
            Logger.t(TAG).d("goTo(%s) requested", goToUrl);
            return null;
        });

        // ---- sleep(ms)：官方真实 API（规则里常被 try/catch 包裹做限速/等待）
        putFunction("sleep", args -> {
            long ms = 0;
            try {
                if (args != null && args.length > 0) ms = Long.parseLong(argStr(args[0]));
            } catch (Throwable ignored) {
            }
            if (ms > 0) {
                try {
                    Thread.sleep(Math.min(ms, 10000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return null;
        });

        // ---- 其余官方 API：宿主无对应能力，一律安全桩（防 ReferenceError，中断规则） ----
        // 导航/UI 类
        stub("back", null);
        stub("backToHome", null);
        // closeMe：移植自 8.83 JSEngine.closeMe (JSEngine.smali:12407)。
        // 关闭 fetch({inputStream:true}) 返回的流，规则用完必须调。
        putFunction("closeMe", args -> {
            try {
                if (args != null && args.length > 0 && args[0] instanceof java.io.InputStream) {
                    ((java.io.InputStream) args[0]).close();
                }
            } catch (Throwable e) {
                Logger.t(TAG).d("closeMe failed: %s", e.getMessage());
            }
            return null;
        });
        // openAppIntent(p, a, param, ruleTitleKey)：8.83 JSEngine.openAppIntent。
        // 敏感操作，需用户授权。授权后解析 Intent 并启动外部应用。
        // 8.83 smali:20047：末尾 Boolean.toString，回 "true"/"false" 字符串
        putFunction("openAppIntent", args -> {
            try {
                String ruleTitle = rule != null ? rule.getTitle() : "";
                final boolean[] result = {false};
                boolean allowed = HkAuthManager.checkAuth(ruleTitle, "openAppIntent",
                        "打开其他应用", () -> {
                            try {
                                android.content.Intent intent = new android.content.Intent();
                                if (args != null && args.length > 0 && args[0] != null) {
                                    String p = argStr(args[0]);
                                    if (p.startsWith("http")) {
                                        intent.setAction(android.content.Intent.ACTION_VIEW);
                                        intent.setData(android.net.Uri.parse(p));
                                    } else {
                                        intent.setClassName(App.get(), p);
                                    }
                                }
                                if (args != null && args.length > 1 && args[1] != null) {
                                    intent.setAction(argStr(args[1]));
                                }
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                                App.get().startActivity(intent);
                                result[0] = true;
                            } catch (Throwable e) {
                                Logger.t(TAG).d("openAppIntent failed: %s", e.getMessage());
                            }
                        });
                return String.valueOf(allowed && result[0]);
            } catch (Throwable e) {
                Logger.t(TAG).d("openAppIntent failed: %s", e.getMessage());
                return "false";
            }
        });
        // publishRule(ruleJson, ruleTitleKey)：8.83 JSEngine.publishRule。
        // 敏感操作，需用户授权。简化版：仅提示，完整发布逻辑待实现。
        putFunction("publishRule", args -> {
            try {
                String ruleTitle = rule != null ? rule.getTitle() : "";
                boolean allowed = HkAuthManager.checkAuth(ruleTitle, "publishRule",
                        "发布/分享规则", () -> {
                            try {
                                android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                                h.post(() -> ToastUtil.show(App.get(), "规则发布功能开发中", android.widget.Toast.LENGTH_SHORT));
                            } catch (Throwable ignored) {
                            }
                        });
                return null;
            } catch (Throwable e) {
                Logger.t(TAG).d("publishRule failed: %s", e.getMessage());
                return null;
            }
        });
        // registerDNS(options, ruleTitleKey)：8.83 JSEngine.registerDNS。
        // 敏感操作，需用户授权。options 为 JSON（含 dns/hosts 等）。
        putFunction("registerDNS", args -> {
            try {
                String ruleTitle = rule != null ? rule.getTitle() : "";
                boolean allowed = HkAuthManager.checkAuth(ruleTitle, "registerDNS",
                        "设置自定义 DNS", () -> {
                            try {
                                if (args != null && args.length > 0 && args[0] != null) {
                                    // 8.83 JSEngine.registerDNS → DohProvidersKt.registerDNS：
                                    // {网址或域名: DoH 地址 / 空格分隔 IP} 批量写进 autoDomain 表，
                                    // 取数 client 解析命中域名时走注册方式（HkHttp.HK_AUTO_DNS）。
                                    // 此前是只记日志的空壳，靠它绕污染解析的规则（如花都影视）全挂。
                                    String json = stringifyArg(args[0]);
                                    Logger.t(TAG).d("registerDNS: %s", json);
                                    if (json != null && json.startsWith("{")) {
                                        org.json.JSONObject o = new org.json.JSONObject(json);
                                        java.util.Map<String, String> map = new java.util.HashMap<>();
                                        java.util.Iterator<String> it = o.keys();
                                        while (it.hasNext()) {
                                            String k = it.next();
                                            Object v = o.opt(k);
                                            if (v instanceof String) map.put(k, (String) v);
                                        }
                                        HkHttp.registerAutoDomain(map);
                                    }
                                }
                            } catch (Throwable e) {
                                Logger.t(TAG).d("registerDNS apply failed: %s", e.getMessage());
                            }
                        });
                return null;
            } catch (Throwable e) {
                Logger.t(TAG).d("registerDNS failed: %s", e.getMessage());
                return null;
            }
        });
        // findJavaClass(p, c, so, ruleTitleKey)：8.83 JSEngine.findJavaClass。
        // 高敏感操作（可执行任意代码），需用户明确授权。
        putFunction("findJavaClass", args -> {
            try {
                String ruleTitle = rule != null ? rule.getTitle() : "";
                final Object[] result = {null};
                boolean allowed = HkAuthManager.checkAuth(ruleTitle, "findJavaClass",
                        "加载 Java 类", () -> {
                            try {
                                String className = args != null && args.length > 1 ? argStr(args[1]) : "";
                                if (!className.isEmpty()) {
                                    result[0] = Class.forName(className);
                                }
                            } catch (Throwable e) {
                                Logger.t(TAG).d("findJavaClass failed: %s", e.getMessage());
                            }
                        });
                return allowed ? result[0] : null;
            } catch (Throwable e) {
                Logger.t(TAG).d("findJavaClass failed: %s", e.getMessage());
                return null;
            }
        });
        // loadJavaClass(p, c, so, ruleTitleKey)：8.83 JSEngine.loadJavaClass。
        // 高敏感操作（可执行任意代码），需用户明确授权。
        putFunction("loadJavaClass", args -> {
            try {
                String ruleTitle = rule != null ? rule.getTitle() : "";
                final Object[] result = {null};
                boolean allowed = HkAuthManager.checkAuth(ruleTitle, "loadJavaClass",
                        "从 URL 加载 Java 类", () -> {
                            try {
                                String className = args != null && args.length > 1 ? argStr(args[1]) : "";
                                if (!className.isEmpty()) {
                                    result[0] = Class.forName(className);
                                }
                            } catch (Throwable e) {
                                Logger.t(TAG).d("loadJavaClass failed: %s", e.getMessage());
                            }
                        });
                return allowed ? result[0] : null;
            } catch (Throwable e) {
                Logger.t(TAG).d("loadJavaClass failed: %s", e.getMessage());
                return null;
            }
        });
        // back(refreshPage)：8.83 JSEngine.back：post OnBackEvent(refreshPage, false) 经 EventBus。
        // 参数为 Boolean 时用其值，否则默认 true（8.83 语义）。
        putFunction("back", args -> {
            boolean refreshPage = true;
            try {
                if (args != null && args.length > 0) {
                    Object o = argsNativeObjectAdjust(args[0]);
                    if (o instanceof Boolean) {
                        refreshPage = (Boolean) o;
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                EventBus.getDefault().post(new com.fongmi.android.tv.api.hk.event.OnBackEvent(refreshPage, false));
            } catch (Throwable e) {
                Logger.t(TAG).d("back failed: %s", e.getMessage());
            }
            return null;
        });
        // backToHome()：8.83 JSEngine.backToHome：post BackMainEvent 经 EventBus（简化版，完整 Activity 栈管理为海阔特有）。
        putFunction("backToHome", args -> {
            try {
                EventBus.getDefault().post(new com.fongmi.android.tv.api.hk.event.video.BackMainEvent());
            } catch (Throwable e) {
                Logger.t(TAG).d("backToHome failed: %s", e.getMessage());
            }
            return null;
        });
        // getSearchMode()：8.83 JSEngine.getSearchMode：返回 Preference "smode"（默认 0）的字符串形式。
        putFunction("getSearchMode", args -> {
            try {
                android.content.SharedPreferences sp = App.get().getSharedPreferences("hiker", android.content.Context.MODE_PRIVATE);
                int mode = sp.getInt("smode", 0);
                return String.valueOf(mode);
            } catch (Throwable e) {
                return "0";
            }
        });
        // setSearchMode(mode)：8.83 JSEngine.setSearchMode：mode 为 0 或 1 时存入 Preference "smode"。
        putFunction("setSearchMode", args -> {
            try {
                int mode = 0;
                if (args != null && args.length > 0) {
                    Object o = argsNativeObjectAdjust(args[0]);
                    if (o instanceof Integer) {
                        mode = (Integer) o;
                    } else if (o instanceof Double) {
                        mode = ((Double) o).intValue();
                    }
                }
                if (mode == 0 || mode == 1) {
                    App.get().getSharedPreferences("hiker", android.content.Context.MODE_PRIVATE)
                            .edit().putInt("smode", mode).apply();
                }
            } catch (Throwable e) {
                Logger.t(TAG).d("setSearchMode failed: %s", e.getMessage());
            }
            return null;
        });
        // searchContains(text, keyword, ignoreCase)：8.83 JSEngine.searchContains（smali:23905，
        // returnType=BOOL）：原生方法回 String.valueOf(boolean)，桥接层按 BOOL 注解转成真布尔给 JS。
        // 星辰的 putFunction 直接经 Rhino 透传——若回字符串 "false"，JS 里是 truthy，
        // if(searchContains(...)) 恒真。故这里直接回真布尔（Boolean→JS boolean）。
        // text 为空返回 false，keyword 为空返回 true，否则按 ignoreCase 做包含匹配
        //（keyword 按空格切分，多词需按序包含）。
        putFunction("searchContains", args -> {
            try {
                String text = args != null && args.length > 0 ? getString(args[0]) : null;
                String keyword = args != null && args.length > 1 ? getString(args[1]) : null;
                boolean ignoreCase = false;
                if (args != null && args.length > 2) {
                    Object o = argsNativeObjectAdjust(args[2]);
                    if (o instanceof Boolean) ignoreCase = (Boolean) o;
                }
                return hkSearchContains(text, keyword, ignoreCase);
            } catch (Throwable e) {
                return false;
            }
        });
        // （W1：refresh 别名已迁至 setPageReverse 处注册为倒序语义，此处旧的重刷实现删除。）
        // setPageParams：8.83 JSEngine.setPageParams(2779)：post SetPageParamsEvent(t) 经 EventBus，FilmListActivity.onSetParams 接收后更新 rule params
        putFunction("setPageParams", args -> {
            try {
                String params = args != null && args.length > 0 ? argStr(args[0]) : "";
                if (params == null) params = "";
                org.greenrobot.eventbus.EventBus.getDefault().post(new com.fongmi.android.tv.api.hk.event.home.SetPageParamsEvent(params));
            } catch (Throwable ignored) {
            }
            return null;
        });
        // 官方 setPagePicUrl(title)：发 SetPagePicEvent 更新页面配图；宿主存字段供上层取用
        // 8.83 JSEngine.setPagePicUrl(2769)：post SetPagePicEvent(picUrl) 经 EventBus
        putFunction("setPagePicUrl", args -> {
            try {
                pagePicUrl = args != null && args.length > 0 && args[0] != null ? argStr(args[0]) : "";
                Logger.t(TAG).d("setPagePicUrl: %s", pagePicUrl);
                org.greenrobot.eventbus.EventBus.getDefault().post(new com.fongmi.android.tv.api.hk.event.home.SetPagePicEvent(pagePicUrl));
            } catch (Throwable e) {
                Logger.t(TAG).d("setPagePicUrl failed: %s", e.getMessage());
            }
            return null;
        });
        // 8.83 showLoading(text)/hideLoading()：post LoadingEvent 由 UI 层弹 loading 框；
        // 动态域名类规则 preRule 里调 showLoading("正在获取域名，请稍候")，之前是 stub 导致无任何提示。
        putFunction("showLoading", args -> {
            try {
                String text = args != null && args.length > 0 ? argStr(args[0]) : "";
                Logger.t(TAG).d("showLoading: %s", text);
                HkLoading.show(rule.getTitle(), text);
            } catch (Throwable e) {
                Logger.t(TAG).d("showLoading failed: %s", e.getMessage());
            }
            return null;
        });
        putFunction("hideLoading", args -> {
            try {
                HkLoading.hide(rule.getTitle());
            } catch (Throwable e) {
                Logger.t(TAG).d("hideLoading failed: %s", e.getMessage());
            }
            return null;
        });
        stub("refreshReadData", null);
        stub("refreshVideoSource", null);
        stub("refreshVideoUrl", null);
        stub("refreshX5WebView", null);
        // 8.83 JSEngine.startQRScanPage(25977)：请求相机权限后 startActivityForResult
        // 扫码页（8.83 用 MyCaptureActivity，星辰用已有的 ScanActivity），扫码结果经
        // onActivityResult → dealQRContent 处理。宿主（HkPageActivity）通过 setQrScanHost 注册。
        putFunction("startQRScanPage", args -> {
            try {
                QrScanHost host = qrScanHost;
                if (host != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        try {
                            host.startQrScan();
                        } catch (Throwable t) {
                            Logger.t(TAG).d("startQRScanPage failed: %s", t.getMessage());
                        }
                    });
                } else {
                    Logger.t(TAG).d("stub startQRScanPage called (no host registered)");
                }
            } catch (Throwable e) {
                Logger.t(TAG).d("startQRScanPage failed: %s", e.getMessage());
            }
            return null;
        });
        stub("createQRCode", null);
        stub("createQRCodeToFile", "");
        stub("initChaquopy", null);
        // 文件/下载类（官方行为：saveImage 下载图片到 path；requireDownload 文件不存在才下载；
        // deleteCache(c) c=-1 清规则全部缓存否则按 url 清，宿主清内存缓存）
        // 8.83 JSEngine.java:2505 requireDownload：文件已存在直接返回，否则 downloadFile；失败抛错。
        putFunction("requireDownload", args -> {
            if (args == null || args.length < 2) throw new RuntimeException("下载文件失败：参数不足");
            String url = argStr(args[0]);
            String dest = resolveHikerPath(argStr(args[1]));
            if (new File(dest).exists()) return null;
            String headers = args.length > 2 && args[2] != null ? stringifyArg(args[2]) : "{}";
            downloadToFile(url, dest, headers);
            return null;
        });
        putFunction("saveImage", args -> {
            // 官方 saveImage(url, path)：参数顺序注意是 (url, path)
            if (args == null || args.length < 2) return null;
            try {
                String url = argStr(args[0]);
                String path = argStr(args[1]);
                if (TextUtils.isEmpty(url) || "undefined".equalsIgnoreCase(url)) return null;
                if (TextUtils.isEmpty(path) || "undefined".equalsIgnoreCase(path)) return null;
                downloadToFile(url, resolveHikerPath(path), "{}");
            } catch (Throwable e) {
                Logger.t(TAG).d("saveImage failed: %s", e.getMessage());
            }
            return null;
        });
        stub("copyFiles", null);
        // 8.83 JSEngine.java:2772 deleteCache：c 经 tryGetString，非字符串抛错；缺省 "-1" 清规则缓存
        putFunction("deleteCache", args -> {
            String c;
            if (args == null || args.length == 0 || args[0] == null) {
                c = "-1";
            } else {
                c = mustBeString(args, 0);
            }
            if ("-1".equals(c) || TextUtils.isEmpty(c)) {
                memCache.clear();
                Logger.t(TAG).d("deleteCache: cleared all memCache");
            } else {
                final String key = c;
                memCache.keySet().removeIf(k -> k.contains(key));
                Logger.t(TAG).d("deleteCache: cleared memCache for %s", key);
            }
            return null;
        });
        stub("shareDirectory", null);
        stub("writeHexFile", null);
        // 代理/服务类
        // startProxyServer：8.83 JSEngine.startProxyServer (JSEngine.smali:25880)。
        //   语义：startProxyServer(js) 设置代理 JS，启动本地服务（30秒超时等待），
        //   返回 serverUrl + "/proxy"。
        //   8.83 用 RemoteServerManager + AndServer；星辰用 HkProxyServer（NanoHTTPD，
        //   项目已有依赖），只实现 /proxy 端点（DLNA/web 管理属 App 壳子，不移植）。
        putFunction("startProxyServer", args -> {
            try {
                String js = args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
                HkProxyServer server = HkProxyServer.instance();
                server.setProxyJS(js);
                server.setEvaluator(this);
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                try {
                    if (server.isRunning()) {
                        latch.countDown();
                    } else {
                        // 端口被占用时照 8.83 递增重试（52020→52025），换到实际实例再回填 JS 与求值器
                        server = HkProxyServer.startWithFallback();
                        server.setProxyJS(js);
                        server.setEvaluator(this);
                        latch.countDown();
                    }
                } catch (Throwable t) {
                    latch.countDown();
                }
                latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
                // 体检批六：getServerUrl 只按绑定端口拼串、未启动也非空，全端口启动
                // 失败时不许把假地址回给 JS（原版启动异常直接抛、函数不回地址）；
                // 未真跑起一律按本函数既有失败口径回 ""。
                if (!server.isRunning()) return "";
                String url = server.getServerUrl();
                if (url == null || url.isEmpty()) return "";
                return url + "/proxy";
            } catch (Throwable t) {
                return "";
            }
        });
        stub("addWebProxyRule", null);
        stub("removeWebProxyRule", null);
        stub("refreshWebProxyRule", null);
        stub("png2Ts", null);
        stub("buildWebDav", null);
        // 阅读类（第二期再做）
        stub("getEpubChapters", "[]");
        stub("getEpubContent0", "");
        stub("getEpubMetadata", "{}");
        // 字符串结果回调：官方 setStrResult(o, callbackKey, ruleKey) 完成 JS 回调；
        // 宿主同步模型无 callbackMap，存 strResult 字段供上层取用（setLastChapterResult 官方即委托 setStrResult）
        putFunction("setStrResult", args -> {
            try {
                strResult = args != null && args.length > 0 && args[0] != null ? argStr(args[0]) : "";
                Logger.t(TAG).d("setStrResult: %d chars", strResult.length());
            } catch (Throwable e) {
                Logger.t(TAG).d("setStrResult failed: %s", e.getMessage());
            }
            return null;
        });
        putFunction("setLastChapterResult", args -> {
            try {
                strResult = args != null && args.length > 0 && args[0] != null ? argStr(args[0]) : "";
                Logger.t(TAG).d("setLastChapterResult: %d chars", strResult.length());
            } catch (Throwable e) {
                Logger.t(TAG).d("setLastChapterResult failed: %s", e.getMessage());
            }
            return null;
        });
        putFunction("setLastChapterRule", args -> {
            try {
                lastChapterRule = args != null && args.length > 0 && args[0] != null ? argStr(args[0]) : "";
                Logger.t(TAG).d("setLastChapterRule: %d chars", lastChapterRule.length());
                // 8.83 JSEngine.setLastChapterRule(2774)：post SetPageLastChapterRuleEvent(rule) 经 EventBus
                org.greenrobot.eventbus.EventBus.getDefault().post(new com.fongmi.android.tv.api.hk.event.home.SetPageLastChapterRuleEvent(lastChapterRule));
            } catch (Throwable e) {
                Logger.t(TAG).d("setLastChapterRule failed: %s", e.getMessage());
            }
            return null;
        });
        // 搜索/隐私（setSearchMode/searchContains 真实现在 2371/2393 行，此处不再设桩覆盖）
        stub("checkPrivacyPassword", false);
        stub("getPrivacyPasswordLen", 0);
        // 规则管理（官方：LitePal 规则表；宿主：HkRuleManager 落盘目录）
        putFunction("getLastRules", args -> {
            try {
                int count = 12;
                if (args != null && args.length > 0 && args[0] != null) {
                    try {
                        count = (int) toDouble(args[0]);
                    } catch (Throwable ignored) {
                        return toJsArray(new ArrayList<>());
                    }
                    if (count == -1) count = Integer.MAX_VALUE;
                }
                File dir = HkRuleManager.get().getDir();
                File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
                if (files == null || files.length == 0) return toJsArray(new ArrayList<>());
                java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                List<Object> out = new ArrayList<>();
                for (File f : files) {
                    if (out.size() >= count) break;
                    try {
                        byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                        Object o = GSON.fromJson(new String(bytes, Charset.forName("UTF-8")), Object.class);
                        if (o != null) out.add(o);
                    } catch (Throwable ignored) {
                    }
                }
                // returnType=JSON：原版桥接层 JSON.parse 后是真数组（1.3.42 扫尾）。
                return parseJson(GSON.toJson(out));
            } catch (Throwable e) {
                Logger.t(TAG).d("getLastRules failed: %s", e.getMessage());
                return toJsArray(new ArrayList<>());
            }
        });
        putFunction("getRuleCount", args -> {
            try {
                File dir = HkRuleManager.get().getDir();
                File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
                return String.valueOf(files == null ? 0 : files.length);
            } catch (Throwable e) {
                return "0";
            }
        });
        stub("isVideoOrMusic", false);
        stub("isShorthand", false);
        // 任务调度：移植自 8.83 JSEngine.registerTask/unRegisterTask
        // (JSEngine.smali:22645/26271)。8.83 没有 setTimeout/setInterval，规则定时全靠它。
        // 语义：registerTask(id, time, js) 延迟 time 毫秒后单次执行 JS 字符串；id 为空则生成 UUID；
        // time 默认 10000ms，最小 500ms；unRegisterTask(id) 取消。
        putFunction("registerTask", args -> {
            try {
                String id = args != null && args.length > 0 ? argStr(args[0]) : null;
                if (id == null || id.isEmpty() || "undefined".equals(id) || "null".equals(id)) {
                    id = java.util.UUID.randomUUID().toString();
                }
                long delay = 10000; // 8.83 defaultInt = 0x2710
                if (args != null && args.length > 1 && args[1] != null) {
                    Object t = args[1];
                    if (t instanceof Integer) delay = ((Integer) t).intValue();
                    else if (t instanceof Double) delay = ((Double) t).intValue();
                    else if (t instanceof Long) delay = (Long) t;
                    else if (t instanceof Number) delay = ((Number) t).intValue();
                }
                if (delay < 500) delay = 500; // 8.83 最小 0x1f4
                String js = args != null && args.length > 2 && args[2] != null ? argStr(args[2]) : "";
                final String taskId = id;
                final String taskJs = js;
                Runnable fire = () -> {
                    try {
                        submit(() -> {
                            try {
                                if (destroyed) return null;
                                synchronized (jsTasks) {
                                    if (!jsTasks.containsKey(taskId)) return null;
                                    jsTasks.remove(taskId); // 单次执行
                                }
                                if (taskJs != null && !taskJs.isEmpty()) {
                                    evalJs(taskJs);
                                }
                            } catch (Throwable th) {
                                Logger.t(TAG).d("registerTask fire failed: " + th.getMessage());
                            }
                            return null;
                        });
                    } catch (Throwable ignored) {
                    }
                };
                ScheduledFuture<?> f = timerScheduler.schedule(fire, delay, TimeUnit.MILLISECONDS);
                synchronized (jsTasks) {
                    // 同 id 覆盖：先取消旧的（8.83 SchedulePool.add 同 key 覆盖语义）
                    ScheduledFuture<?> old = jsTasks.put(taskId, f);
                    if (old != null) old.cancel(false);
                }
                Logger.t(TAG).d("registerTask(%s, %dms)", taskId, delay);
            } catch (Throwable e) {
                Logger.t(TAG).d("registerTask failed: %s", e.getMessage());
            }
            return null;
        });
        putFunction("unRegisterTask", args -> {
            try {
                String id = args != null && args.length > 0 && args[0] != null ? argStr(args[0]) : null;
                if (id != null) {
                    synchronized (jsTasks) {
                        ScheduledFuture<?> f = jsTasks.remove(id);
                        if (f != null) f.cancel(false);
                    }
                    Logger.t(TAG).d("unRegisterTask(%s)", id);
                }
            } catch (Throwable e) {
                Logger.t(TAG).d("unRegisterTask failed: %s", e.getMessage());
            }
            return null;
        });
        // Java 互操作（宿主不开放）
        stub("getCurrentActivity", null);
        // 官方 getPrivateJS(c)：AesUtil.encrypt(AES_DEFAULT_KEY, c)（JSEngine.java:2966）
        putFunction("getPrivateJS", args -> {
            if (args == null || args.length == 0 || args[0] == null) return "";
            try {
                return aesUtilCrypt(true, AES_DEFAULT_KEY, argStr(args[0]));
            } catch (Throwable e) {
                Logger.t(TAG).d("getPrivateJS failed: %s", e.getMessage());
                return "";
            }
        });
        // 官方语义：返回内置 Hikerurl.js（$ 工具库）源码，规则 eval(getJsPlugin()) 后使用；
        // init() 已自动注入为全局，规则不 eval 也能用
        putFunction("getJsPlugin", args -> {
            String code = loadAsset("Hikerurl.js");
            if (TextUtils.isEmpty(code)) {
                Logger.t(TAG).d("getJsPlugin: Hikerurl.js missing in assets");
                return "";
            }
            return code;
        });
        // 官方 getJsLazyPlugin()：返回内置 assets/plugin.js 源码
        putFunction("getJsLazyPlugin", args -> {
            String code = loadAsset("plugin.js");
            if (code == null) {
                Logger.t(TAG).d("getJsLazyPlugin: plugin.js missing in assets");
                return "";
            }
            return code;
        });
        // getMyType()：返回当前求值入口的类型（与 setResult 路由用的口径一致）。
        // 注意与 Java 侧生成注入串的 getMyType(String) 同名异义——那个只拼 var MY_TYPE。
        putFunction("getMyType", args -> currentMyType == null ? "" : currentMyType);
        stub("getMyCallbackKey", "");
        stub("getMyInput", "");
        stub("getMyJs", "");
        // syncExecute(task)：8.83 JSEngine.syncExecute（smali 26009-26128）真实现。
        // 同步方法；task 为 null/undefined 时 json 为空；argsNativeObjectAdjust(task, true)
        // 转 Map（原版 fastjson JSONObject，本项目等价 Map），取 "func"（BaseFunction）与
        // "param"，在当前 Context 下用 func 的父 scope 调 func.call(cx, parentScope, func,
        // new Object[]{param}) 并返回结果；异常经 catchall 原样重抛（原版不吞）。
        putFunction("syncExecute", args -> {
            synchronized (HkJsRuntime.this) {
                Object task = (args != null && args.length > 0) ? args[0] : null;
                try {
                    Map<?, ?> json = null;
                    if (task != null) {
                        Object adj = argsNativeObjectAdjust(task, true);
                        // 体检批六判型：原版此处裸 check-cast JSONObject，数组/字符串任务
                        // 抛 CCE 经 catchall 重抛；星辰按本函数既有「无可调用函数回 null」
                        // 口径（下方 func 判型同型），非 Map 即当无任务，正常路径不变。
                        json = adj instanceof Map ? (Map<?, ?>) adj : null;
                    }
                    BaseFunction func = null;
                    if (json != null && json.containsKey("func")) {
                        // 扫查待裁 22：安全取值——func 缺失/非函数时原裸强转＋空解引用
                        // 直接 NPE/CCE 打死调用链；无可调用函数即按无任务返回 null。
                        Object funcObj = json.get("func");
                        if (funcObj instanceof BaseFunction) func = (BaseFunction) funcObj;
                    }
                    if (func == null) return null;
                    Object param = null;
                    if (json != null) {
                        param = json.get("param");
                    }
                    Scriptable parentScope = func.getParentScope();
                    Context cx = Context.getCurrentContext();
                    return func.call(cx, parentScope, func, new Object[]{param});
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        });
        // 首页子模块：8.83 JSEngine.getHomeSub/hasHomeSub (JSEngine.smali:17037/18002)
        // 8.83 语义：getHomeSub() 返回订阅记录 JSON 数组；hasHomeSub(url) 是 ReturnType.BOOL
        // （JSEngine.java:2311），JS 侧真布尔——旧注释写"返回字符串"是错的，已按原版改。
        // 移植：HkHomeSubService.getSubRecords()（SharedPreferences 存，语义与 8.83 BigTextDO.getHomeSub() 一致）
        putFunction("getHomeSub", args -> {
            try {
                // returnType=JSON：原版桥接层 JSON.parse 后是真数组（1.3.42 扫尾）。
                return parseJson(new com.google.gson.Gson().toJson(HkHomeSubService.getSubRecords()));
            } catch (Throwable t) {
                return toJsArray(new ArrayList<>());
            }
        });
        putFunction("hasHomeSub", args -> {
            try {
                String url = args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
                return "true".equals(HkHomeSubService.hasHomeSub(url)) ? Boolean.TRUE : Boolean.FALSE;
            } catch (Throwable t) {
                return Boolean.FALSE;
            }
        });
        // 应用信息（真实现）
        putFunction("getAppVersion", args -> {
            try {
                android.content.pm.PackageInfo pi = App.get().getPackageManager()
                        .getPackageInfo(App.get().getPackageName(), 0);
                return pi.versionName == null ? "" : pi.versionName;
            } catch (Throwable ignored) {
                return "";
            }
        });
        putFunction("getCpuAbi", args -> {
            try {
                String[] abis = android.os.Build.SUPPORTED_ABIS;
                return abis != null && abis.length > 0 ? abis[0] : "";
            } catch (Throwable ignored) {
                return "";
            }
        });
        putFunction("getUaObject", args -> {
            Map<String, String> ua = new HashMap<>();
            ua.put("mobileUa", UA_MOBILE);
            ua.put("pcUa", UA_PC);
            return GSON.toJson(ua);
        });

        // ---- 原版 8.83 补齐（JSEngine @JSAnnotation 清单全量对账，2026-10-17） ----
        // 原版 160 个注解函数（去重后 159 个名），此前缺 25 个。逐个按原版行为补：
        // 自带能力的按原版实现；依赖原版应用内能力（X5/EPUB/WebDAV/Chaquopy/网页代理/
        // 事件总线私类）而星辰没有的，注册同名空实现（同返回类型、不抛错），仅防 ReferenceError，
        // 行为缺口逐条注明。注意：xlog 不在原版清单里（规则侧 let xlog = log 的别名），
        // 其 ReferenceError 根因是 require 作用域，已在上面按原版 require0 修，不在此处加新名。
        putFunction("isShorthand", args -> {
            // 8.83 JSEngine.isShorthand：判参是否为简写方法（JSFunction.isShorthand）。
            // 本 Rhino 侧函数皆为 BaseFunction，无此概念，恒 false。
            return Boolean.FALSE;
        });
        putFunction("isVideoOrMusic", args -> {
            // 8.83 JSEngine.isVideoOrMusic：UrlDetector.isVideoOrMusic(url)，BOOL 型。
            if (args == null || args.length == 0) return Boolean.FALSE;
            try {
                return HkRouter.isVideoOrMusicUrl(argStr(args[0]));
            } catch (Throwable ignored) {
                return Boolean.FALSE;
            }
        });
        putFunction("checkPrivacyPassword", args -> {
            // 8.83 JSEngine.checkPrivacyPassword：星辰无隐私密码功能，恒 false。
            return Boolean.FALSE;
        });
        putFunction("getPrivacyPasswordLen", args -> {
            // 8.83 JSEngine.getPrivacyPasswordLen：星辰无隐私密码功能，回 0。
            return 0;
        });
        putFunction("copyFiles", args -> {
            // 8.83 JSEngine.copyFiles(p, p2, dirKey)：Num 型，返回拷贝成功文件数。
            try {
                if (args == null || args.length < 2) return 0;
                java.nio.file.Path src = new File(resolveHikerPath(argStr(args[0]))).toPath();
                java.nio.file.Path dst = new File(resolveHikerPath(argStr(args[1]))).toPath();
                if (!java.nio.file.Files.isRegularFile(src)) return 0;
                if (dst.getParent() != null) java.nio.file.Files.createDirectories(dst.getParent());
                java.nio.file.Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return 1;
            } catch (Throwable ignored) {
                return 0;
            }
        });
        putFunction("writeHexFile", args -> {
            // 8.83 JSEngine.writeHexFile(filePath, content)：hex 串解码后写文件。
            try {
                if (args == null || args.length < 2) return null;
                String hex = argStr(args[1]).replaceAll("\\s", "");
                int n = hex.length() - hex.length() % 2;
                byte[] data = new byte[n / 2];
                for (int i = 0; i < n; i += 2) data[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
                java.nio.file.Path p = new File(resolveHikerPath(argStr(args[0]))).toPath();
                if (p.getParent() != null) java.nio.file.Files.createDirectories(p.getParent());
                java.nio.file.Files.write(p, data);
            } catch (Throwable ignored) {
            }
            return null;
        });
        putFunction("shareDirectory", args -> {
            // 8.83 JSEngine.shareDirectory：分享目录。星辰用系统分享框实现等价效果。
            try {
                if (args == null || args.length == 0) return null;
                File dir = new File(resolveHikerPath(argStr(args[0])));
                if (!dir.isDirectory()) return null;
                File[] files = dir.listFiles();
                if (files == null || files.length == 0) return null;
                java.util.ArrayList<android.net.Uri> uris = new java.util.ArrayList<>();
                for (File f : files) {
                    if (f.isFile()) uris.add(com.fongmi.android.tv.utils.FileUtil.getShareUri(f));
                }
                if (uris.isEmpty()) return null;
                android.content.Intent it = new android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE);
                it.setType("*/*");
                it.putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris);
                it.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                App.get().startActivity(android.content.Intent.createChooser(it, ""));
            } catch (Throwable ignored) {
            }
            return null;
        });
        putFunction("cacheCode0", args -> {
            // 8.83 JSEngine.cacheCode0(Object code)（smali 11838）：tryGetString 取码串，
            // 非空才 post event.rule.CacheCode(code)。消费侧：HkArticleFragment.updateCacheCode
            // 存入页面 cacheCode 字段，随 #autoCache# 列表结果一并落盘。
            String code = args != null && args.length > 0 ? getString(args[0]) : null;
            if (!TextUtils.isEmpty(code)) {
                EventBus.getDefault().post(
                        new com.fongmi.android.tv.api.hk.event.rule.CacheCode(code));
            }
            return null;
        });
        putFunction("buildWebDav", args -> {
            // 8.83 JSEngine.buildWebDav：构造 WebDavHolder。星辰无 WebDAV 客户端，回 null。
            return null;
        });
        putFunction("createQRCode", args -> {
            // 8.83 JSEngine.createQRCode(text, size)：应用内展示二维码。
            // 星辰无此弹窗，用系统图片查看器打开生成的二维码（等价可见，不新造 UI 语义）。
            try {
                String text = args != null && args.length > 0 ? argStr(args[0]) : "";
                if (TextUtils.isEmpty(text) || "null".equals(text)) return null;
                int size = 512;
                if (args.length > 1) {
                    try { size = (int) Double.parseDouble(argStr(args[1])); } catch (Throwable ignored) {}
                    if (size <= 0) size = 512;
                }
                android.graphics.Bitmap bmp = com.fongmi.android.tv.utils.QRCode.getBitmap(text, size, 2);
                if (bmp == null) return null;
                File f = new File(App.get().getCacheDir(), "hk_qrcode.png");
                java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                out.close();
                android.content.Intent it = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                it.setDataAndType(com.fongmi.android.tv.utils.FileUtil.getShareUri(f), "image/png");
                it.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                App.get().startActivity(it);
            } catch (Throwable ignored) {
            }
            return null;
        });
        putFunction("createQRCodeToFile", args -> {
            // 8.83 JSEngine.createQRCodeToFile(text)：生成二维码存文件，回 "file://"+路径。
            try {
                String text = args != null && args.length > 0 ? argStr(args[0]) : "";
                if (TextUtils.isEmpty(text) || "null".equals(text)) return "";
                android.graphics.Bitmap bmp = com.fongmi.android.tv.utils.QRCode.getBitmap(text, 512, 2);
                if (bmp == null) return "";
                File f = new File(App.get().getCacheDir(), "hk_qrcode_" + System.currentTimeMillis() + ".jpg");
                java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out);
                out.close();
                return "file://" + f.getAbsolutePath();
            } catch (Throwable ignored) {
                return "";
            }
        });
        putFunction("getCurrentActivity", args -> {
            // 8.83 JSEngine.getCurrentActivity：回当前 Activity（原版同款语义）。
            try {
                return App.activity();
            } catch (Throwable ignored) {
                return null;
            }
        });
        putFunction("getEpubChapters", args -> {
            // 8.83 JSEngine.getEpubChapters：returnType=JSON。星辰无 EPUB 解析能力，回真数组（1.3.42 扫尾）。
            return toJsArray(new ArrayList<>());
        });
        putFunction("getEpubContent0", args -> {
            // 8.83 JSEngine.getEpubContent0：星辰无 EPUB 解析能力，回空串。
            return "";
        });
        putFunction("getEpubMetadata", args -> {
            // 8.83 JSEngine.getEpubMetadata：returnType=JSON。星辰无 EPUB 解析能力，回真对象（1.3.42 扫尾）。
            return parseJson("{}");
        });
        putFunction("initChaquopy", args -> {
            // 8.83 JSEngine.initChaquopy：星辰无 Chaquopy，恒 false。
            return Boolean.FALSE;
        });
        putFunction("addWebProxyRule", args -> {
            // 8.83 JSEngine.addWebProxyRule(Object obj)（smali 10577）：入参须是 JSON 对象，
            // 否则抛 RuntimeException("参数格式不对")；串化后 parseObject 成 BrowserProxyRule，
            // name 或 match 为空抛 RuntimeException("name和match不能为空")；再 BrowserProxy.addProxyRule
            //（同名先移除再追加）。星辰等价：api/hk/browser/HkBrowserProxy 注册表维护，消费在
            // HkWebkitHolder.shouldInterceptRequest → HkBrowserProxy.proxyRequest。
            Object o = args != null && args.length > 0 ? argsNativeObjectAdjust(args[0]) : null;
            if (!(o instanceof java.util.Map)) throw new RuntimeException("参数格式不对");
            com.fongmi.android.tv.api.hk.browser.HkBrowserProxyRule rule;
            try {
                rule = GSON.fromJson(GSON.toJson(o),
                        com.fongmi.android.tv.api.hk.browser.HkBrowserProxyRule.class);
            } catch (Throwable t) {
                throw new RuntimeException("参数格式不对");
            }
            if (rule == null) throw new RuntimeException("参数格式不对");
            if (TextUtils.isEmpty(rule.name) || TextUtils.isEmpty(rule.match)) {
                throw new RuntimeException("name和match不能为空");
            }
            com.fongmi.android.tv.api.hk.browser.HkBrowserProxy.addProxyRule(rule);
            return null;
        });
        putFunction("removeWebProxyRule", args -> {
            // 8.83 JSEngine.removeWebProxyRule(Object name)（smali 22785）：tryGetString 取名，
            // 空抛 RuntimeException("参数不能为空")；BrowserProxy.removeProxyRule 按名移除第一个同名。
            String name = args != null && args.length > 0 ? getString(args[0]) : null;
            if (TextUtils.isEmpty(name)) throw new RuntimeException("参数不能为空");
            com.fongmi.android.tv.api.hk.browser.HkBrowserProxy.removeProxyRule(name);
            return null;
        });
        putFunction("refreshWebProxyRule", args -> {
            // 8.83 JSEngine.refreshWebProxyRule()（smali 22398）：BrowserProxy.initProxyRules()
            // 从 hiker://files/rules/browser_proxy_rules.json 重读规则表。
            com.fongmi.android.tv.api.hk.browser.HkBrowserProxy.initProxyRules();
            return null;
        });
        putFunction("refreshReadData", args -> {
            // 8.83 JSEngine.refreshReadData(Object p)（smali 22151）：p 为 undefined/null 直接无操作；
            // 否则调整后串化 parseObject 成 CommonReadPageData{currentUrl,id,nextPage,title 四个 String}，
            // 把 bean 本身当事件 post。消费侧：HkArticleFragment.onRefreshReadData
            //（原版 ArticleListFragment.refreshReadData 的等价面）。
            if (args == null || args.length == 0 || args[0] == null || isUndefined(args[0])) return null;
            Object o = argsNativeObjectAdjust(args[0]);
            if (!(o instanceof java.util.Map)) return null;
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) o;
            com.fongmi.android.tv.api.hk.event.rule.RefreshReadDataEvent data =
                    new com.fongmi.android.tv.api.hk.event.rule.RefreshReadDataEvent();
            data.setCurrentUrl(map.get("currentUrl") instanceof String ? (String) map.get("currentUrl") : null);
            data.setId(map.get("id") instanceof String ? (String) map.get("id") : null);
            data.setNextPage(map.get("nextPage") instanceof String ? (String) map.get("nextPage") : null);
            data.setTitle(map.get("title") instanceof String ? (String) map.get("title") : null);
            EventBus.getDefault().post(data);
            return null;
        });
        putFunction("refreshVideoSource", args -> {
            // 8.83 JSEngine.refreshVideoSource(Object url, Object headers)（smali 22209）：
            // headers 是 JSON 对象时逐键取 String 值组成 HashMap（非对象则空表），随后无条件
            // post OnRefreshVideoUrlEvent(url, headers)（无订阅者门）。消费侧：
            // HkArticleFragment.onRefreshVideoUrl → HkWebViewHolder.setVideoUrl/setVideoHeaders
            //（原版 ArticleListFragment.onRefreshVideoUrl → ArticleWebViewHolder.updateVideoUrl 的等价面）。
            String url = args != null && args.length > 0 ? argStr(args[0]) : "";
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            if (args != null && args.length > 1) {
                Object h = argsNativeObjectAdjust(args[1]);
                if (h instanceof java.util.Map) {
                    for (java.util.Map.Entry<?, ?> e : ((java.util.Map<?, ?>) h).entrySet()) {
                        if (e.getKey() != null && e.getValue() instanceof String) {
                            headers.put(String.valueOf(e.getKey()), (String) e.getValue());
                        }
                    }
                }
            }
            EventBus.getDefault().post(
                    new com.fongmi.android.tv.api.hk.event.rule.RefreshVideoUrlEvent(url, headers));
            return null;
        });
        putFunction("refreshVideoUrl", args -> {
            // 8.83 JSEngine.refreshVideoUrl(Object c, Object r)（smali 22294）：
            // c 是 String 直接用、非 String 走 JSON.toJSONString；r 只有真 Boolean 才取值，
            // 缺参/null/非布尔一律默认 true（=PlayDataUpdateEvent.restartPosition，与 refreshPage 的布尔口径同源）。
            // 仅当事件有订阅者才 post（原版 hasSubscriberForEvent 门）。消费侧：VideoActivity.onPlayDataUpdate
            // → PlayerManager.refreshUrl 原地换址（原版 VideoPlayerActivity.refreshVideoUrl → reStartPlayer 的等价面）。
            Object c = args != null && args.length > 0 ? argsNativeObjectAdjust(args[0]) : null;
            String url;
            if (c instanceof String) {
                url = (String) c;
            } else if (c == null || isUndefined(c)) {
                url = "";
            } else {
                try {
                    url = GSON.toJson(c);
                } catch (Throwable t) {
                    url = "";
                }
            }
            boolean restart = true;
            if (args != null && args.length > 1) {
                Object r = argsNativeObjectAdjust(args[1]);
                if (r instanceof Boolean) restart = (Boolean) r;
            }
            if (EventBus.getDefault().hasSubscriberForEvent(
                    com.fongmi.android.tv.api.hk.event.video.PlayDataUpdateEvent.class)) {
                EventBus.getDefault().post(new com.fongmi.android.tv.api.hk.event.video
                        .PlayDataUpdateEvent(url, restart));
            }
            return null;
        });
        putFunction("refreshX5Desc", args -> {
            // 8.83 JSEngine.refreshX5Desc：post OnRefreshX5HeightEvent(desc)，列表页据此改 WebView 的模式与高度。
            // 此函数与 X5 内核无关——系统 WebView 同样靠它定高（规则先以默认小高建条目、加载后调它改成 float&&100% 全屏）。
            // 此前以「星辰无 X5 内核」空实现，网页型规则的高度永远停在默认 240，只剩顶部一小条（2026-10-27 他真机点名）。
            String desc = args != null && args.length > 0 ? argStr(args[0]) : "";
            EventBus.getDefault().post(
                    new com.fongmi.android.tv.api.hk.event.rule.RefreshX5DescEvent(desc));
            return null;
        });
        putFunction("refreshX5WebView", args -> {
            // 8.83 JSEngine.refreshX5WebView：post OnRefreshWebViewEvent(url)，列表页据此把 WebView 指到新址。
            // 与 refreshX5Desc 同族、与 X5 内核无关：此前同以「无 X5」空实现，规则调它换址等于没调，
            // WebView 地址恒空（他点的「未知链接 null」一类症状的来源之一，2026-10-27 补）。
            String url = args != null && args.length > 0 ? argStr(args[0]) : "";
            EventBus.getDefault().post(
                    new com.fongmi.android.tv.api.hk.event.rule.RefreshWebViewEvent(url));
            return null;
        });
        putFunction("png2Ts", args -> {
            // 8.83 JSEngine.png2Ts(Object c, Object headers)（smali 21180，注解标 VOID 但实际回 String）：
            // url 空抛 RuntimeException("参数不能为空")；先清 _cache 缓存目录（原版异步清，星辰同步清小目录）；
            // http 地址：按扩展名（无则 png）拼 hiker://files/_cache/<md5(url)>.<ext> 落盘点（已存在先删），
            // 带 headers 下载，下成回 png2Ts(落盘路径)、下不成回原 url；非 http：本地文件不存在回原 url、
            // 存在回 png2Ts(路径)。png2Ts 本体见 png2TsFile（FileUtil.png2Ts 逐条转写）。
            String url = args != null && args.length > 0 ? getString(args[0]) : null;
            if (TextUtils.isEmpty(url)) throw new RuntimeException("参数不能为空");
            clearHkCacheDir();
            if (url.startsWith("http")) {
                String ext = extensionOf(url);
                if (TextUtils.isEmpty(ext)) ext = "png";
                String dest = resolveHikerPath("hiker://files/_cache/" + md5(url) + "." + ext);
                File old = new File(dest);
                if (old.exists()) old.delete();
                try {
                    String headers = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : "{}";
                    downloadToFile(url, dest, headers);
                } catch (Throwable t) {
                    return url;
                }
                return png2TsFile(dest);
            }
            String local = resolveHikerPath(url);
            if (!new File(local).exists()) return url;
            return png2TsFile(local);
        });

        // ---- $ 工具集：由 init 注入的官方 v6 版 Hikerurl.js 提供（$ 本体/$.require/$.toString/lazyRule），
        // 本地用生产同款 Rhino 1.7.15 实测：加载跑通、$.require('engine').home() 能真实返回结果。
        // 此前这里有一段手敲仿写的 $ 预置（照反编译重写 $require 等），与资产 $ 互相覆盖且错漏连环
        // （三处转义写错曾让整段在解析期全灭），已整段删除，不再与官方实现并存。
        // 仅保留 Uint8Array 垫片：Rhino 无此内建，部分规则/加密代码会用到，官方 $ 不提供。
        try {
            evalJs(
                "function Uint8Array(a) {\n" +
                "  var r = [];\n" +
                "  if (typeof a === 'number') { for (var i = 0; i < a; i++) r.push(0); }\n" +
                "  else if (a && typeof a.length === 'number') { for (var j = 0; j < a.length; j++) r.push(a[j] & 255); }\n" +
                "  return r;\n" +
                "}\n"
            );
        } catch (Throwable e) {
            Logger.t(TAG).d("Uint8Array shim failed: %s", e.getMessage());
        }
    }

    /** 8.83 AesUtil 同款（encrypt/decrypt）：AES/ECB/PKCS5Padding；密钥补"0"到 32 字节（只补不截）；
     * base64 出入（Android Base64 NO_WRAP）；UTF-8。原版无 SHA1PRNG、无 hex。 */
    private String aesUtilCrypt(boolean encode, String key, String data) throws Exception {
        String padded = key == null ? "" : key;
        while (padded.length() < 32) padded += "0";
        javax.crypto.spec.SecretKeySpec spec = new javax.crypto.spec.SecretKeySpec(
                padded.getBytes(java.nio.charset.StandardCharsets.UTF_8), "AES");
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
        if (encode) {
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, spec);
            byte[] out = cipher.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(out);
        } else {
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, spec);
            byte[] enc = java.util.Base64.getMimeDecoder().decode(data);
            return new String(cipher.doFinal(enc), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** W2：原版 fc 语义（require0，useCallback=false）——返回代码字符串，不求值。
     * 参数：url, h(小时，默认24，0=强制重下), options, v(版本号，默认0)。
     * require0 已有忠实移植（D1-C），fc 直接复用：js:/file:///hiker://page/ 前缀、
     * 本地 libs/<md5>.js 缓存＋版本校验、按 3600000*h 过期、h==0 永远重下。 */
    private String fcImpl(Object[] args) {
        if (args == null || args.length == 0) return "";
        try {
            String url = argStr(args[0]);
            int hours = args.length > 1 ? (int) toDouble(args[1]) : 24;
            Object options = args.length > 2 ? args[2] : null;
            int v = args.length > 3 ? (int) toDouble(args[3]) : 0;
            String ruleTitle = rule != null ? rule.getTitle() : "";
            Object r = require0(options, url, hours, options, null, false, ruleTitle, v);
            return r == null ? "" : String.valueOf(r);
        } catch (Throwable e) {
            Logger.t(TAG).d("fc failed (soft): %s", e.getMessage());
            return "";
        }
    }

    /** W7：原版 batchFetch/batchExecute 语义（JSEngine.smali:11005）。
     * 线程数 min(n,16)，总等待 n/16*10+10 秒，按索引组装；失败项填 ""。 */
    /** W7 batchFetch：8.83（JSEngine.java:11633）returnType=JSON。
     *  原版桥接层对 JSON 型做 JSON.parse(retStr)——规则拿到真 JS 数组。
     *  星辰曾直接回 GSON 字符串（UU 案：规则调 .map 当场 TypeError）。
     *  此处走 toJsArray，与 8.83 的 JS 可见形态一致。 */
    private NativeArray batchFetchImpl(Object[] args) {
        if (args == null || args.length == 0) return toJsArray(new ArrayList<>());
        try {
            String json = args[0] instanceof NativeArray ? jsStringify(args[0]) : argStr(args[0]);
            List<Object> list = GSON.fromJson(json.trim().startsWith("[") ? json : "[]",
                    new com.google.gson.reflect.TypeToken<List<Object>>() {}.getType());
            if (list == null || list.isEmpty()) return toJsArray(new ArrayList<>());
            int n = list.size();
            int threads = Math.min(n, 16);
            if (args.length > 1) {
                try { threads = Math.max(1, Math.min(n, (int) toDouble(args[1]))); } catch (Throwable ignored) {}
            }
            java.util.concurrent.ExecutorService pool = Executors.newFixedThreadPool(threads);
            java.util.concurrent.ConcurrentHashMap<Integer, String> results = new java.util.concurrent.ConcurrentHashMap<>();
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(n);
            try {
                for (int i = 0; i < n; i++) {
                    final int idx = i;
                    Object o = list.get(i);
                    final String u;
                    final String opt;
                    if (o instanceof Map) {
                        Map<String, Object> m = (Map<String, Object>) o;
                        u = String.valueOf(m.get("url"));
                        Object op = m.get("options");
                        opt = op == null ? null : GSON.toJson(op);
                    } else {
                        u = String.valueOf(o);
                        opt = null;
                    }
                    pool.execute(() -> {
                        try { results.put(idx, fetchSync(u, opt)); }
                        catch (Throwable t) { results.put(idx, ""); }
                        finally { latch.countDown(); }
                    });
                }
                // 原版：await(n/16*10+10, SECONDS)（smali:2260）
                latch.await((long) (n / 16 * 10 + 10), TimeUnit.SECONDS);
                List<String> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) out.add(results.getOrDefault(i, ""));
                return toJsArray(out);
            } finally {
                pool.shutdownNow();
            }
        } catch (Throwable e) {
            Logger.t(TAG).d("batchFetch failed: %s", e.getMessage());
            return toJsArray(new ArrayList<>());
        }
    }

    /** W1 倒序语义本体（8.83 JSEngine.setPageReverse:4140：布尔参有效时发 OnReversePageEvent）。 */
    private void setPageReverseDo(Object[] args) {
        try {
            boolean rev = args != null && args.length > 0 && Boolean.parseBoolean(argStr(args[0]));
            kv.put("__pageReverse", rev ? "1" : "0");
            saveKv();
            // 对齐 8.83：发 EventBus 事件，详情页收到后倒序列表
            org.greenrobot.eventbus.EventBus.getDefault().post(new com.fongmi.android.tv.api.hk.event.home.OnReversePageEvent(rev));
        } catch (Throwable ignored) {
        }
    }

    /** 8.83 require0 的 eval 分支（requireCache/rc，returnType=OBJECT）：返回模块执行结果；缺码抛错。
     * W5：求值在调用方作用域（同 require 修法），库代码与调用方顶层声明双向可见。 */
    private Object requireCacheEval(Object[] args, Context cx, Scriptable callerScope) {
        if (args == null || args.length == 0) return null;
        String url = argStr(args[0]);
        double hours = args.length > 1 ? toDouble(args[1]) : 0;
        String code = memCached("rc:" + url, hours, () -> {
            String c = loadLibLocal(url);
            return c == null ? fetchSync(url, null) : c;
        });
        if (TextUtils.isEmpty(code)) {
            // 同 require：取不到软失败，别把整页打死（B1 抛错的回归，见 require 处注释）。
            Logger.t(TAG).d("requireCache miss (soft): %s", url);
            return null;
        }
        if (cx != null && callerScope != null) return evalJsInScope(cx, callerScope, stripJsPrefix(code), "hk-plugin");
        return evalJs(stripJsPrefix(code));
    }

    /** 8.83 JSEngine.tryGetString 等价：先走 argsNativeObjectAdjust 整理（ConsString 取真串、
     * NativeJavaObject 拆包、NativeObject/Array 转集合），整理后仍非字符串（含缺参/undefined）抛错。 */
    private static String mustBeString(Object[] args, int i) {
        if (args == null || args.length <= i) throw new RuntimeException("参数类型只能是字符串");
        Object o = argsNativeObjectAdjust(args[i]);
        if (!(o instanceof String)) throw new RuntimeException("参数类型只能是字符串");
        return (String) o;
    }

    /** 8.83 JSEngine.getRuleTitle(ruleTitleKey) 等价：空则取当前规则标题，否则按给定标题取规则数据目录。 */
    private File ruleDirForTitle(String rt) {
        String title = (rt == null || rt.isEmpty()) ? (rule != null ? rule.getTitle() : "") : rt;
        return HkRuleManager.get().getDataDir(title);
    }

    /** 是否合法字符集名（readFile 第 2 参双重解释用）。 */
    private static boolean isCharsetName(String s) {
        try {
            return java.nio.charset.Charset.isSupported(s.trim());
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 8.83 M3u8Utils.downloadM3u8 等价：只缓存索引文件（片段不下载），相对路径改写为绝对地址，
     * 嵌套子 m3u8 递归跟进；写到 hiker://files/cache/<fileName>，返回 "file://<绝对路径>"。
     * 非 m3u8 地址（mp4/flv/m4a/mp3）直接回原 url。
     */
    private String downloadM3u8File(String url, String fileName, String optionsJson) {
        if (TextUtils.isEmpty(url)) throw new RuntimeException("m3u8 地址为空");
        if (isNotM3u8Url(url)) return url;
        String result;
        try {
            result = fetchSync(url, withHeadersOpt(optionsJson));
        } catch (Throwable e) {
            throw new RuntimeException("m3u8 下载失败：" + e.getMessage());
        }
        String body = "";
        String realUrl = url;
        try {
            Map<String, Object> map = GSON.fromJson(result,
                    new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
            if (map != null) {
                Object b = map.get("body");
                if (b != null) body = String.valueOf(b);
                Object u = map.get("url");
                if (u != null && !TextUtils.isEmpty(String.valueOf(u))) realUrl = String.valueOf(u);
            }
        } catch (Throwable ignored) {
        }
        if (TextUtils.isEmpty(body)) throw new RuntimeException("m3u8 内容为空：" + url);
        String content = convertM3u8Paths(body, realUrl, fileName, optionsJson);
        String abs = resolveHikerPath("hiker://files/cache/" + fileName);
        File f = new File(abs);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        writeFileContent(abs, content);
        Logger.t(TAG).d("downloadM3u8File ok: %s", abs);
        return "file://" + abs;
    }

    /** 8.83 M3u8Utils.isNotM3u8 等价。 */
    private static boolean isNotM3u8Url(String url) {
        if (url.contains(".flv") || url.contains(".m4a") || url.contains(".mp3")
                || url.contains("mine_type=video_mp4")) return true;
        return url.contains("mp4") && !url.contains("m3u8");
    }

    /** 8.83 M3u8Utils.convertPath 等价：EXT-X-KEY 的 URI 与相对片段路径改写为绝对地址。 */
    private String convertM3u8Paths(String content, String baseUrl, String fileName, String optionsJson) {
        StringBuilder sb = new StringBuilder();
        boolean hasTs = false;
        for (String raw : content.split("\n")) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.startsWith("#EXT-X-KEY:")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("URI=\"(.*?)\"").matcher(line);
                if (m.find()) {
                    String keyUri = m.group(1);
                    String keyUrl = keyUri.startsWith("http://") || keyUri.startsWith("https://")
                            ? keyUri : resolveUrl(baseUrl, keyUri.trim());
                    sb.append(line.replace(keyUri, keyUrl)).append('\n');
                } else {
                    sb.append(raw).append('\n');
                }
            } else if (line.startsWith("/") || (!line.startsWith("#") && !line.startsWith("http") && !line.isEmpty())) {
                String newUrl = resolveUrl(baseUrl, line);
                if (!hasTs && line.endsWith(".m3u8") && !newUrl.equals(baseUrl)) {
                    // 嵌套子索引：递归下载，内容写回同一 fileName（8.83 语义）
                    return convertM3u8Paths(fetchM3u8Body(newUrl, optionsJson), newUrl, fileName, optionsJson);
                }
                sb.append(newUrl).append('\n');
                hasTs = true;
            } else {
                sb.append(raw).append('\n');
            }
        }
        return sb.toString();
    }

    private String fetchM3u8Body(String url, String optionsJson) {
        String result = fetchSync(url, withHeadersOpt(optionsJson));
        try {
            Map<String, Object> map = GSON.fromJson(result,
                    new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
            if (map != null && map.get("body") != null) return String.valueOf(map.get("body"));
        } catch (Throwable ignored) {
        }
        throw new RuntimeException("m3u8 内容为空：" + url);
    }

    private static String resolveUrl(String base, String rel) {
        try {
            return new java.net.URL(new java.net.URL(base), rel).toString();
        } catch (Throwable e) {
            return rel;
        }
    }

    /** options JSON 里强制 withHeaders=true（8.83 fetchWithHeaders 语义）。 */
    private static String withHeadersOpt(String optionsJson) {
        try {
            com.google.gson.JsonObject obj;
            if (TextUtils.isEmpty(optionsJson)) {
                obj = new com.google.gson.JsonObject();
            } else {
                com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(optionsJson);
                obj = el != null && el.isJsonObject() ? el.getAsJsonObject() : new com.google.gson.JsonObject();
            }
            obj.addProperty("withHeaders", true);
            return GSON.toJson(obj);
        } catch (Throwable e) {
            return "{\"withHeaders\":true}";
        }
    }

    /**
     * 8.83 JSEngine.java:1943 batchCacheM3u8 等价：params 须为数组（元素 {url, options} 或裸字符串），
     * 否则回 "[]"；每项下载索引文件，返回 "file##url"（失败/等价项回原 url，抛错项置 null），
     * 整体 JSON 数组字符串。并发下载（最多 8 线）。
     */
    /** 8.83（JSEngine.java:10933）returnType=JSON：原版桥接层 JSON.parse 后是真数组。
     *  星辰曾回 GSON 字符串（1.3.42 UU 案同图），此处改回真数组。 */
    private NativeArray batchCacheM3u8Impl(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) return toJsArray(new ArrayList<>());
        List<Object> items = new ArrayList<>();
        try {
            Object p0 = args[0];
            if (p0 instanceof NativeArray) {
                NativeArray na = (NativeArray) p0;
                for (int i = 0; i < na.size(); i++) items.add(na.get(i));
            } else {
                String json = argStr(p0).trim();
                if (!json.startsWith("[")) return toJsArray(new ArrayList<>());
                List<Object> list = GSON.fromJson(json,
                        new com.google.gson.reflect.TypeToken<List<Object>>() {}.getType());
                if (list != null) items.addAll(list);
            }
        } catch (Throwable e) {
            Logger.t(TAG).d("batchCacheM3u8 parse failed: %s", e.getMessage());
            return toJsArray(new ArrayList<>());
        }
        if (items.isEmpty()) return toJsArray(new ArrayList<>());
        int n = items.size();
        int threads = Math.min(n, 8);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        List<String> out = new ArrayList<>(java.util.Collections.nCopies(n, null));
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    Object o = items.get(idx);
                    String u;
                    String opt = null;
                    java.util.Map<?, ?> m = toStringMap(o);
                    if (m != null) {
                        u = m.get("url") == null ? "" : String.valueOf(m.get("url"));
                        if (m.get("options") != null) opt = GSON.toJson(m.get("options"));
                    } else {
                        u = String.valueOf(o);
                    }
                    try {
                        if (TextUtils.isEmpty(u)) {
                            out.set(idx, null);
                            return;
                        }
                        String file = downloadM3u8File(u, "video" + idx + ".m3u8", opt);
                        out.set(idx, file.equals(u) ? u : file + "##" + u);
                    } catch (Throwable e) {
                        // 8.83 batchExecute：单项抛错该槽留空（null）
                        out.set(idx, null);
                    }
                }));
            }
            for (java.util.concurrent.Future<?> f : futures) {
                try {
                    f.get(90, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Throwable ignored) {
                }
            }
        } finally {
            pool.shutdownNow();
        }
        // returnType=JSON（见本方法头注释）：回真数组，null 槽位保留为 JS null。
        return toJsArray(out);
    }

    /**
     * 安全桩：官方有但宿主无对应能力的 API，返回类型合适的默认值并打日志，
     * 保证任何规则都不会因 ReferenceError 中断。
     */
    private void stub(String name, Object ret) {
        try {
            putFunction(name, args -> {
                Logger.t(TAG).d("stub %s called (no-op in host)", name);
                return ret;
            });
        } catch (Throwable ignored) {
        }
    }


    /**
     * hiker://page/&lt;path&gt; 内部协议：从规则 pages（JSON 数组）里按 path 找页面规则，
     * 返回 {@code {"rule": "..."}} JSON 字符串。官方 JSEngine.fetchByHiker 同款语义。
     */
    /**
     * 处理 hiker://page/ 请求，移植自 8.83 PageParser.parsePageRule：
     * (decompiled/smali/com/example/hikerview/service/parser/PageParser.smali:544)
     *
     * 8.83 返回完整的 ArticleListPageRule 对象（path/name/rule/col_type/params/parent），
     * 而非仅 {"rule":"..."}。params 来自 URL query，进 pageRule.params Map 供 JS
     * 经 fetch 自取（D6：不进 MY_PARAMS；MY_PARAMS 唯一来源是规则 params 字段），
     * parent 为规则标题。找不到页面时 8.83 抛 ParseException，上层返回 ""。
     */
    private String handlePageRequest(String url) {
        try {
            String path = url.substring("hiker://page/".length());
            // D1：解析 query 中的 rule= 参数（官方 PageParser.parsePageRule：?rule=标题 按标题换规则查库）
            String ruleTitle = null;
            // 8.83 HttpParser.getParamsByUrl：解析 URL query 为 Map（进 pageRule.params，D6）
            Map<String, String> params = parseUrlParams(url);
            int q = path.indexOf('?');
            if (q >= 0) {
                String query = path.substring(q + 1);
                int hh = query.indexOf('#');
                if (hh >= 0) query = query.substring(0, hh);
                for (String pair : query.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        String k = java.net.URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                        if ("rule".equals(k)) {
                            ruleTitle = java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                            break;
                        }
                    }
                }
                path = path.substring(0, q);
            }
            int h = path.indexOf('#');
            if (h >= 0) path = path.substring(0, h);
            // 8.83：按 ; 分隔取第一段（UrlDetector.clearTag 前）
            int sc = path.indexOf(';');
            if (sc >= 0) path = path.substring(0, sc);
            path = path.trim();
            // hikerPop.js：星辰原生重实现（规则自带的依赖 8.83 内部类，跑不起来）。
            // 返回合成页面，rule 里 $.exports.updateRecordsBottom 调原生 __hkShowUpdateLog 弹底部弹窗。
            if ("hikerPop.js".equals(path)) {
                Map<String, Object> out = new HashMap<>();
                out.put("path", path);
                out.put("rule", "js:$.exports.updateRecordsBottom = function(data){ __hkShowUpdateLog(JSON.stringify(data)); };");
                out.put("params", params);
                out.put("parent", rule != null ? rule.getTitle() : "");
                return GSON.toJson(out);
            }
            // 若指定了 rule= 标题，从目标规则的 pages 里找；找不到标题则回退当前规则
            HkRule targetRule = rule;
            String parentTitle = rule != null ? rule.getTitle() : "";
            if (ruleTitle != null && !ruleTitle.isEmpty()) {
                HkRule found = HkRuleManager.get().getInstalledRule(ruleTitle);
                if (found != null) {
                    targetRule = found;
                    parentTitle = found.getTitle();
                } else {
                    Logger.t(TAG).d("handlePageRequest: rule title not found: %s, fallback to current", ruleTitle);
                }
            }
            String pagesJson = targetRule != null ? targetRule.getPages() : "";
            if (!TextUtils.isEmpty(pagesJson)) {
                List<Map<String, Object>> pages = GSON.fromJson(pagesJson, MAP_LIST_TYPE);
                if (pages != null) {
                    for (Map<String, Object> p : pages) {
                        if (path.equals(String.valueOf(p.get("path")))) {
                            // 8.83：返回完整 page 对象，补 params 和 parent
                            Map<String, Object> out = new HashMap<>(p);
                            out.put("params", params);
                            out.put("parent", parentTitle);
                            return GSON.toJson(out);
                        }
                    }
                }
            }
            Logger.t(TAG).d("handlePageRequest: no page rule for path=%s", path);
        } catch (Throwable e) {
            Logger.t(TAG).d("handlePageRequest failed: %s", e.getMessage());
        }
        // 8.83 找不到时抛 ParseException，上层返回 ""（非 "{}"，避免 JSON.parse("{}").rule 为 undefined）
        return "";
    }

    /** 8.83 HttpParser.getParamsByUrl 等价：解析 URL query 为 Map */
    private static Map<String, String> parseUrlParams(String url) {
        Map<String, String> map = new HashMap<>();
        try {
            int q = url.indexOf('?');
            if (q < 0) return map;
            String query = url.substring(q + 1);
            int hh = query.indexOf('#');
            if (hh >= 0) query = query.substring(0, hh);
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String k = java.net.URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                    String v = java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                    map.put(k, v);
                } else if (!pair.isEmpty()) {
                    map.put(java.net.URLDecoder.decode(pair, "UTF-8"), "");
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    /**
     * 8.83 语义：setPreResult 的预存保留、追加合并。
     * JSEngine.smali:918 六参版：p5=1 → preCalled.add(key)＋ARTICLE_LIST_PRE 立即显示导航；
     * p5=0 且 key 在 preCalled 中 → ARTICLE_LIST_AFTER 追加视频。ArticleListFragment 两次都走
     * append，无"整体替换"。旧实现（清掉预存）与原版相悖，已按原版对齐：只翻转标志。
     * 跨次求值不串数据：parseList/parseSearch 入口已清 results 并复位 preResultActive。
     */
    private void beginResult() {
        preResultActive = false;
    }

    /**
     * $.require('hiker://page/xxx') 的页面代码查找：从规则 pages（JSON 数组）按 path 取 rule 字段。
     */
    private String findPageCode(String path) {
        try {
            String pagesJson = rule.getPages();
            if (TextUtils.isEmpty(pagesJson)) return null;
            List<Map<String, Object>> pages = GSON.fromJson(pagesJson, MAP_LIST_TYPE);
            if (pages == null) return null;
            for (Map<String, Object> p : pages) {
                if (path.equals(String.valueOf(p.get("path")))) {
                    Object r = p.get("rule");
                    return r == null ? null : String.valueOf(r);
                }
            }
        } catch (Throwable e) {
            Logger.t(TAG).d("findPageCode failed: %s", e.getMessage());
        }
        return null;
    }

    /** 把 JS 传来的对象参数转成 Map<String,Object>（JSObject/stringify/JSON 字符串均可）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toStrMap(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof Map) return (Map<String, Object>) o;
            String json = o instanceof NativeObject ? jsStringify(o) : String.valueOf(o).trim();
            if (!json.startsWith("{")) return null;
            return GSON.fromJson(json, new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
        } catch (Throwable e) {
            return null;
        }
    }

    /** 在当前 results 里按 url 找条目下标。 */
    private int findResultIndex(String id) {
        for (int i = 0; i < results.size(); i++) {
            if (id.equals(results.get(i).getUrl())) return i;
        }
        return -1;
    }

    /** updateItem：把 obj 里的 title/url/pic/pic_url/img/desc/col_type 合并进条目。 */
    private void mergeItem(HkItem item, Map<String, Object> m) {
        String t = str(m, "title");
        if (!TextUtils.isEmpty(t)) item.setTitle(t);
        String u = str(m, "url");
        if (!TextUtils.isEmpty(u)) item.setUrl(u);
        String pic = str(m, "pic_url");
        if (TextUtils.isEmpty(pic)) pic = str(m, "img");
        if (TextUtils.isEmpty(pic)) pic = str(m, "pic");
        if (!TextUtils.isEmpty(pic)) item.setPic(pic);
        String d = str(m, "desc");
        if (!TextUtils.isEmpty(d)) item.setDesc(d);
        String c = str(m, "col_type");
        if (!TextUtils.isEmpty(c)) item.setColType(c);
        // 8.83 DetailPage 字段（updateItem/addItem 用）
        String fr = str(m, "findRule");
        if (TextUtils.isEmpty(fr)) fr = str(m, "find_rule");
        if (!TextUtils.isEmpty(fr)) item.setFindRule(fr);
        String g = str(m, "group");
        if (!TextUtils.isEmpty(g)) item.setGroup(g);
        String pg = str(m, "pages");
        if (!TextUtils.isEmpty(pg)) item.setPages(pg);
        String pr = str(m, "preRule");
        if (TextUtils.isEmpty(pr)) pr = str(m, "pre_rule");
        if (!TextUtils.isEmpty(pr)) item.setPreRule(pr);
        String r = str(m, "rule");
        if (!TextUtils.isEmpty(r)) item.setRule(r);
    }

    /**
     * 8.83 JSEngine item 系列 API（updateItem/deleteItem/deleteItemByCls）经 EventBus
     * post ItemModifyEvent，内容页收到后就地更新已展示列表（对齐 8.83 FilmListActivity/
     * ArticleListFragment.onItemModify）。parse 时直接改 results 已够；展示后的规则
     * 回调（如点击动作里调 updateItem）需经此链同步 UI。
     */
    private void postItemModifyEvent(HkItem item,
            com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action action, String cls) {
        try {
            com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent e =
                    new com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent(item, action);
            if (cls != null) e.setCls(cls);
            org.greenrobot.eventbus.EventBus.getDefault().post(e);
        } catch (Throwable t) {
            Logger.t(TAG).d("postItemModifyEvent failed: %s", t.getMessage());
        }
    }

    /** addItemAfter/addItemBefore(id, obj)：在匹配 url 的条目之后/之前插入。 */
    /**
     * 8.83 JSEngine.java:1198/1240：id 非字符串抛"addItemAfter：格式有误，请传入ID字符串"；
     * o 非 JSON 对象/数组抛"addItem：格式有误，只支持json"；数组逐条插入。
     */
    private void addItemAt(Object[] args, boolean after) {
        String who = after ? "addItemAfter" : "addItemBefore";
        if (args == null || args.length < 1 || !(args[0] instanceof String))
            throw new RuntimeException(who + "：格式有误，请传入ID字符串");
        String id = (String) args[0];
        if (args.length < 2 || args[1] == null)
            throw new RuntimeException("addItem：格式有误，只支持json");
        List<Map<String, Object>> items = new ArrayList<>();
        Object o = args[1];
        if (o instanceof NativeArray) {
            NativeArray na = (NativeArray) o;
            for (int i = 0; i < na.size(); i++) {
                Map<String, Object> m = toStrMap(na.get(i));
                if (m == null) throw new RuntimeException("addItem：格式有误，只支持json");
                items.add(m);
            }
        } else {
            Map<String, Object> m = toStrMap(o);
            if (m == null) throw new RuntimeException("addItem：格式有误，只支持json");
            items.add(m);
        }
        for (Map<String, Object> m : items) {
            HkItem item = new HkItem();
            mergeItem(item, m);
            if (TextUtils.isEmpty(item.getUrl())) item.setUrl(id);
            int idx = findResultIndex(id);
            if (idx < 0) {
                results.add(item);
            } else {
                results.add(after ? idx + 1 : idx, item);
            }
        }
        Logger.t(TAG).d("addItem%s: %s x%d", after ? "After" : "Before", id, items.size());
    }

    /** 条目完整序列化（8.83 ArticleList 字段口径）：_findItem/_findItemsByCls 共用。 */
    private static Map<String, Object> itemToFullMap(HkItem it) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", it.getTitle());
        m.put("url", it.getUrl());
        m.put("pic", it.getPic());
        m.put("desc", it.getDesc());
        m.put("col_type", it.getColType());
        m.put("type", it.getType());
        m.put("content", it.getContent());
        m.put("extra", it.getExtra());
        m.put("line", it.getLine());
        m.put("findRule", it.getFindRule());
        m.put("group", it.getGroup());
        return m;
    }

    /** fc/fetchCache 的持久文件缓存：8.83 JSEngine.java:2814 语义——默认 24 小时，
     * 按 url 的 md5 落盘（App cacheDir/hkfc/）；hour==0 强制刷新；空结果不落盘。 */
    private String fileCached(String url, double hours, java.util.concurrent.Callable<String> loader) {
        try {
            File dir = new File(App.get().getCacheDir(), "hkfc");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, md5(url) + ".txt");
            // 8.83：hour==0 强制重新下载；未过期直接读盘
            if (f.exists() && hours != 0
                    && System.currentTimeMillis() - f.lastModified() <= (long) (hours * 3600_000)) {
                String hit = readFileContent(f.getAbsolutePath());
                if (!hit.isEmpty()) {
                    Logger.t(TAG).d("fileCache hit: %s", url);
                    return hit;
                }
            }
            String data = loader.call();
            if (data == null) data = "";
            if (!data.isEmpty()) writeFileContent(f.getAbsolutePath(), data);
            return data;
        } catch (Throwable e) {
            Logger.t(TAG).d("fileCached failed: %s", e.getMessage());
            return "";
        }
    }

    /** fc/rc 的内存缓存：hours<=0 不缓存；命中且未过期直接返回。 */
    private String memCached(String key, double hours, java.util.concurrent.Callable<String> loader) {
        try {
            if (hours > 0) {
                MemCache hit = memCache.get(key);
                if (hit != null && hit.alive()) {
                    Logger.t(TAG).d("memCache hit: %s", key);
                    return hit.data;
                }
            }
            String data = loader.call();
            if (data == null) data = "";
            if (hours > 0) memCache.put(key, new MemCache(data, System.currentTimeMillis() + (long) (hours * 3600_000)));
            return data;
        } catch (Throwable e) {
            Logger.t(TAG).d("memCached failed: %s", e.getMessage());
            return "";
        }
    }

    private static double toDouble(Object o) {
        try {
            if (o instanceof Number) return ((Number) o).doubleValue();
            return Double.parseDouble(String.valueOf(o));
        } catch (Throwable e) {
            return 0;
        }
    }

    /** fetchPC：options 里没有 UA 时补桌面 UA（规则自带的 UA 优先保留）。 */
    private static String mergeDesktopUa(String optionsJson) {
        try {
            Map<String, Object> map = GSON.fromJson(
                    TextUtils.isEmpty(optionsJson) ? "{}" : optionsJson,
                    new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
            if (map == null) map = new HashMap<>();
            Object headers = map.get("headers");
            Map<String, Object> hm = headers instanceof Map ? (Map<String, Object>) headers : new HashMap<>();
            boolean hasUa = false;
            for (String k : hm.keySet()) {
                if ("user-agent".equalsIgnoreCase(k)) { hasUa = true; break; }
            }
            if (!hasUa) {
                hm.put("User-Agent", UA_PC);
                map.put("headers", hm);
            }
            return GSON.toJson(map);
        } catch (Throwable e) {
            return "{\"headers\":{\"User-Agent\":\"" + UA_PC + "\"}}";
        }
    }

    /**
     * 8.83 JSEngine.generateRequestOptions 同款：fetch/post/request 没传 User-Agent 时
     * 自动补移动端 UA（SettingConfig.getMobileUA）。照抄 mergeDesktopUa，只换 UA 值。
     */
    private static String mergeMobileUa(String optionsJson) {
        try {
            Map<String, Object> map = GSON.fromJson(
                    TextUtils.isEmpty(optionsJson) ? "{}" : optionsJson,
                    new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
            if (map == null) map = new HashMap<>();
            Object headers = map.get("headers");
            Map<String, Object> hm = headers instanceof Map ? (Map<String, Object>) headers : new HashMap<>();
            boolean hasUa = false;
            for (String k : hm.keySet()) {
                if ("user-agent".equalsIgnoreCase(k)) { hasUa = true; break; }
            }
            if (!hasUa) {
                hm.put("User-Agent", UA_MOBILE);
                map.put("headers", hm);
            }
            return GSON.toJson(map);
        } catch (Throwable e) {
            return "{\"headers\":{\"User-Agent\":\"" + UA_MOBILE + "\"}}";
        }
    }

    /**
     * 文件路径解析：hiker://files/... → App filesDir；绝对路径原样；相对路径 → 规则数据目录。
     * 全部钳制在 App 文件目录内。
     */
    private String resolveHikerPath(String p) {
        try {
            if (TextUtils.isEmpty(p)) return "";
            if (p.startsWith("hiker://files/")) {
                // 8.83 writeFile/readFile 与 fetch 同走 JSEngine.getFilePath（外部 Documents 根目录）；
                // 此前这里误用内部 filesDir，规则自己 writeFile 写下的数据文件 fetch 永远读不到
                // （真机游戏集 eval(fetch('hiker://files/rules/Hood/yxj.json')) 后 list 未定义实证）。
                return getFilePath(p);
            }
            if (p.startsWith("file://")) p = p.substring("file://".length());
            File f = new File(p);
            if (!f.isAbsolute()) f = new File(HkRuleManager.get().getDataDir(rule.getTitle()), p);
            return f.getAbsolutePath();
        } catch (Throwable ignored) {
            return p == null ? "" : p;
        }
    }

    private String readFileContent(String absPath) {
        return readFileContent(absPath, null);
    }

    /**
     * 读文件为字符串（8.83 FileUtil.fileToString 等价：平台默认字符集，Android 上即 UTF-8）。
     * 8.83 本身不做字符集探测；此处在等价基础上做两处兼容增强：
     * 1) 显式 charset 参数（规则可传 "GBK" 等）；
     * 2) 剥离 UTF-8 BOM（\uFEFF），否则 JSON 解析可能失败。
     */
    private String readFileContent(String absPath, String charset) {
        try {
            File f = new File(absPath);
            hkSeedRestorePath(absPath, f);
            if (!f.exists() || !f.isFile()) return "";
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            Charset cs = Charset.forName("UTF-8");
            if (charset != null && !charset.trim().isEmpty()) {
                try {
                    cs = Charset.forName(charset.trim());
                } catch (Throwable ignored) {
                }
            }
            String s = new String(bytes, cs);
            if (!s.isEmpty() && s.charAt(0) == '\uFEFF') s = s.substring(1);
            return s;
        } catch (Throwable e) {
            return "";
        }
    }

    /** 8.83 JSEngine.aesDecode(Object, Object) 的 Java 等价：AES/CBC 解密文件内容。 */
    private String aesDecode(String key, String content) {
        try {
            return Crypto.aes("AES/CBC/PKCS5Padding", false, content, true, key, "", false);
        } catch (Throwable e) {
            return "";
        }
    }

    private void writeFileContent(String absPath, String content) {
        try {
            File f = new File(absPath);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            java.nio.file.Files.write(f.toPath(), (content == null ? "" : content).getBytes(Charset.forName("UTF-8")));
        } catch (Throwable e) {
            Logger.t(TAG).d("writeFile failed %s: %s", absPath, e.getMessage());
        }
    }

    /** fixM3u8(url, content)：把 m3u8 里相对分片地址按 url 补全。 */
    private static String fixM3u8Content(String url, String content) {
        if (TextUtils.isEmpty(content)) return "";
        try {
            java.net.URI base = new java.net.URI(url);
            StringBuilder sb = new StringBuilder();
            for (String line : content.split("\n")) {
                String t = line.trim();
                if (!t.isEmpty() && !t.startsWith("#") && !t.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
                    try { t = base.resolve(t).toString(); } catch (Throwable ignored) {}
                    sb.append(t).append('\n');
                } else {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (Throwable e) {
            return content;
        }
    }

    private static String hexToB64(String hex) {
        try {
            String h = hex.trim();
            byte[] b = new byte[h.length() / 2];
            for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
            return Base64.encodeToString(b, Base64.NO_WRAP);
        } catch (Throwable e) {
            return "";
        }
    }

    /** RSA 加解密（best-effort）：公钥加密 X.509 / 私钥解密 PKCS8，PKCS1Padding，base64 出入。 */
    private static String rsaCrypto(boolean encrypt, String input, String keyB64) {
        try {
            byte[] keyBytes = Base64.decode(keyB64.trim(), Base64.DEFAULT);
            javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
            if (encrypt) {
                java.security.PublicKey pub = kf.generatePublic(new java.security.spec.X509EncodedKeySpec(keyBytes));
                c.init(javax.crypto.Cipher.ENCRYPT_MODE, pub);
                return Base64.encodeToString(c.doFinal(input.getBytes(Charset.forName("UTF-8"))), Base64.NO_WRAP);
            } else {
                java.security.PrivateKey prv = kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(keyBytes));
                c.init(javax.crypto.Cipher.DECRYPT_MODE, prv);
                return new String(c.doFinal(Base64.decode(input.trim(), Base64.DEFAULT)), Charset.forName("UTF-8"));
            }
        } catch (Throwable e) {
            return "";
        }
    }

    private static String getLocalIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress() && a.getHostAddress() != null && a.getHostAddress().indexOf(':') < 0) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    // ================= setPublicItem/getPublicItem 持久化（跨规则） =================

    /** 8.83 setPublicItem0：持久化路径恒为 hiker://files/rules/_item00.json（JSEngine.smali:3059）。 */
    private File publicKvFile() {
        return new File(getFilePath("hiker://files/rules/_item00.json"));
    }

    /** 旧版 public_kv.json（规则数据目录旁）→ 新版 _item00.json 的一次性迁移。 */
    private File legacyPublicKvFile() {
        File dataDir = HkRuleManager.get().getDataDir(rule.getTitle());
        File parent = dataDir.getParentFile();
        if (parent == null) parent = dataDir;
        return new File(parent, "public_kv.json");
    }

    private void loadPublicKv() {
        try {
            File f = publicKvFile();
            if (!f.exists()) {
                // 一次性迁移：旧版 public_kv.json 有数据而新版尚无，搬过去
                File legacy = legacyPublicKvFile();
                if (legacy.exists() && legacy.length() > 0) {
                    byte[] lb = java.nio.file.Files.readAllBytes(legacy.toPath());
                    if (f.getParentFile() != null) f.getParentFile().mkdirs();
                    java.nio.file.Files.write(f.toPath(), lb);
                    legacy.delete();
                } else {
                    return;
                }
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            Map<String, String> map = GSON.fromJson(new String(bytes, "UTF-8"),
                    new com.google.gson.reflect.TypeToken<Map<String, String>>() {}.getType());
            if (map != null) publicKv.putAll(map);
        } catch (Throwable ignored) {
        }
    }

    private void savePublicKv() {
        try {
            File f = publicKvFile();
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            java.nio.file.Files.write(f.toPath(), GSON.toJson(publicKv).getBytes("UTF-8"));
        } catch (Throwable ignored) {
        }
    }

    /** 官方 ArticleColTypeEnum 的全部 col_type code（getColTypes 用）。 */
    private static final List<String> COL_TYPES = java.util.Arrays.asList(
            "text_1", "text_2", "text_3", "text_4", "text_5", "text_center_1", "text_icon",
            "movie_1", "movie_2", "movie_3", "movie_1_left_pic", "movie_1_vertical_pic",
            "movie_1_vertical_pic_blur", "movie_3_marquee",
            "pic_1", "pic_2", "pic_3", "pic_1_full", "pic_1_center", "pic_3_square", "pic_1_card", "pic_2_card",
            "icon_1_search", "icon_2_round", "icon_3_fill", "icon_4", "icon_small_4", "icon_round_small_4",
            "icon_5_no_crop", "icon_1_left_pic", "icon_2", "icon_3_round_fill", "icon_4_card", "icon_5",
            "icon_round_4", "icon_small_3",
            "rich_text", "long_text", "avatar", "video", "header", "footer", "line", "line_blank",
            "blank_block", "big_blank_block", "big_big_blank_block", "scroll_button", "flex_button", "input",
            "card_pic_1", "card_pic_2", "card_pic_2_2", "card_pic_2_2_left", "card_pic_3", "card_pic_3_center",
            "x5_webview_single"
    );

    /**
     * hiker://files/libs/&lt;md5&gt;.js 全局 miss 时的按规则回退：.hkzip 导入的库落在
     * 规则数据目录 data/&lt;规则名&gt;/libs/（与 loadLibLocal 同一位置）。$.require(http)
     * 经 Hikerurl.js require.resolve 拼出的全局路径在全新导入、弱网下不存在，回退到
     * 这里命中；命中即用，不走网络。只认 libs/ 下的单层 .js 文件名，防路径穿越。
     *
     * @return 命中返回绝对路径，否则 null
     */
    private String resolvePerRuleLibFile(String hikerPath) {
        try {
            if (hikerPath == null || rule == null) return null;
            String p = hikerPath;
            int q = p.indexOf('?');
            if (q >= 0) p = p.substring(0, q);
            int h = p.indexOf('#');
            if (h >= 0) p = p.substring(0, h);
            final String prefix = "hiker://files/libs/";
            if (!p.startsWith(prefix) || !p.toLowerCase(Locale.ROOT).endsWith(".js")) return null;
            String name = p.substring(prefix.length());
            if (name.isEmpty() || name.contains("..") || name.contains("/") || name.contains("\\")) return null;
            java.io.File f = new java.io.File(
                    new java.io.File(HkRuleManager.get().getDataDir(rule.getTitle()), "libs"), name);
            return f.exists() ? f.getAbsolutePath() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 种子数据文件表：hiker://files/ 相对路径 → 内置 asset 路径。
     * 磁力君.简 的站点表文件：规则本体只带壳，configs 页
     * $.require("rules").rrules 的数据靠这份文件——rules 页先读本地、
     * 没有就从 raw.githubusercontent.com/ApolloRino/R 现下。该账号
     * 已整体注销（2026-10-31 实测 GitHub API/raw/jsDelivr/各存档
     * 全 404），本地再丢（2026-10-26 那回小程序数据被清）就永久
     * 缺失，rrules 恒未定义、rules.map 必炸，且失败回写还会把 404
     * 正文写进本地文件变成空壳。种子内容＝潇洒哥自己 2026-10-03
     * 备份里同规则 v223 版的内联站点表（27 条）按现行消费口径
     * （$.exports＝{rrules, version}）重封装，账在
     * ~/workspace/magnet-rrules/PROVENANCE.md；不是远端原文件
     * （原文件已无存），表是 v223 快照、个别站点域名可能已失效。
     */
    private static final String[][] HK_SEED_FILES = {
            {"rules/Apollo/磁力君.简.js", "hk-seeds/rules/Apollo/磁力君.简.js"},
    };

    /** url 命中种子表才返回 asset 路径（只认 hiker://files/ 原形，去 query/fragment 后比对，含 URL 编码形）。 */
    private static String hkSeedAssetFor(String url) {
        if (url == null || !url.startsWith("hiker://files/")) return null;
        String rel = url.substring("hiker://files/".length());
        int q = rel.indexOf('?');
        if (q >= 0) rel = rel.substring(0, q);
        int h = rel.indexOf('#');
        if (h >= 0) rel = rel.substring(0, h);
        String dec = android.net.Uri.decode(rel);
        for (String[] row : HK_SEED_FILES) {
            if (row[0].equals(rel) || row[0].equals(dec)) return row[1];
        }
        return null;
    }

    /** fetch 读路入口：命中种子表才回补，其余路径零动作。 */
    private void hkSeedRestore(String url, java.io.File target) {
        String asset = hkSeedAssetFor(url);
        if (asset != null) hkSeedWriteIfInvalid(asset, target);
    }

    /** readFile 读路入口：按文件系统路径尾段命中同一种子表。 */
    private void hkSeedRestorePath(String absPath, java.io.File target) {
        if (absPath == null) return;
        String norm = absPath.replace('\\', '/');
        for (String[] row : HK_SEED_FILES) {
            if (norm.endsWith("/" + row[0])) {
                hkSeedWriteIfInvalid(row[1], target);
                return;
            }
        }
    }

    /**
     * 目标缺失、或内容不含标志串 rrules（只剩 404 正文等空壳）时，
     * 把内置种子原样写回目标。有效内容（含 rrules 的正版/更新件）
     * 永不覆盖。全程吞异常：回补失败保持原缺失语义（读回 ""）。
     */
    private void hkSeedWriteIfInvalid(String asset, java.io.File target) {
        try {
            if (target.exists()) {
                String cur = fileToString(target, null, true);
                if (cur != null && cur.contains("rrules")) return;
            }
            String code = loadAsset(asset);
            if (code == null || !code.contains("rrules")) return;
            java.io.File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            java.io.FileOutputStream out = new java.io.FileOutputStream(target, false);
            out.write(code.getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {
        }
    }

    /**
     * require() 本地库优先：找规则数据目录 data/&lt;规则名&gt;/libs/&lt;md5(url)&gt;.js，
     * 命中则直接加载（.hkzip 导入时已解压，无网络也能用）；未命中返回 null 走远程。
     */
    private String loadLibLocal(String libUrl) {
        try {
            if (libUrl == null || !libUrl.startsWith("http")) return null;
            String md5 = Util.md5(libUrl);
            if (TextUtils.isEmpty(md5)) return null;
            File f = new File(new File(HkRuleManager.get().getDataDir(rule.getTitle()), "libs"), md5 + ".js");
            if (!f.exists()) return null;
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            String code = new String(bytes, Charset.forName("UTF-8"));
            return TextUtils.isEmpty(code) ? null : code;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * hiker://assets/xxx：从宿主内置 assets 读取文件内容（海阔官方内置库同款路径）。
     * 文件不存在返回 null。
     */
    private String loadAsset(String assetPath) {
        try {
            java.io.InputStream is = App.get().getAssets().open(assetPath);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            String code = bos.toString("UTF-8");
            return TextUtils.isEmpty(code) ? null : code;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * D2：hiker://assets/xxx.js → 读宿主内置 assets 返回文本内容。
     * 供 request()/fetch() 调用（官方 $.require 经 getScript→request() 取脚本，
     * 不是 __hkRequirePage）。去 query/fragment 后按 asset 路径读取。
     * 找不到返回 ""（不抛异常）。
     */
    private String loadAssetText(String url) {
        try {
            String assetPath = url.substring("hiker://assets/".length());
            int q = assetPath.indexOf('?');
            if (q >= 0) assetPath = assetPath.substring(0, q);
            int h = assetPath.indexOf('#');
            if (h >= 0) assetPath = assetPath.substring(0, h);
            String code = loadAsset(assetPath.trim());
            return code == null ? "" : code;
        } catch (Throwable ignored) {
            return "";
        }
    }

    // ================= 对外接口 =================

    /**
     * 带 headers 下载文件到绝对路径（saveImage/requireDownload 共用）。
     * headersJson 为 JSON：{headers:{...}} 或直接 {k:v}。
     */
    private void downloadToFile(String url, String dest, String headersJson) {
        if (TextUtils.isEmpty(url)) return;
        File f = null;
        File tmp = null;
        try {
            f = new File(dest);
            if (f.isDirectory() || dest.endsWith("/")) {
                String name = url.replaceAll("[?#].*$", "").replaceAll("^.*/", "");
                if (TextUtils.isEmpty(name)) name = "download.bin";
                f = new File(f, name);
            }
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            // 毒文件根治（红果冷播可行性 §四）：最终路径「存在即完整」。
            // 同目标的历史临时文件先清（60 分钟前的 .part-<唯一> 残留；在途的并发下载因年轻跳过；
            // P0 预热的固定名 X.part 不带 "-<唯一>" 后缀，不会被扫到）。
            sweepStalePartFiles(f);
            // 临时名带唯一后缀：同目标多线程并发下载互不踩；与最终文件同目录，原子改名要求同文件系统。
            tmp = new File(f.getParentFile(),
                    f.getName() + ".part-" + System.nanoTime() + "-" + Thread.currentThread().getId());
            String opt = "{\"headers\":{}}";
            try {
                Map<String, Object> m = GSON.fromJson(headersJson,
                        new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
                if (m != null) {
                    Object h = m.get("headers");
                    if (h == null) h = m.get("header");
                    Map<String, Object> hm = h instanceof Map ? (Map<String, Object>) h : m;
                    Map<String, String> flat = new HashMap<>();
                    for (Map.Entry<String, Object> e : hm.entrySet()) {
                        if (e.getValue() != null) flat.put(e.getKey(), String.valueOf(e.getValue()));
                    }
                    Map<String, Object> wrap = new HashMap<>();
                    wrap.put("headers", flat);
                    opt = GSON.toJson(wrap);
                }
            } catch (Throwable ignored) {
            }
            try (okhttp3.Response res = HkHttp.to(url,
                    com.fongmi.quickjs.bean.Req.objectFrom(opt)).execute()) {
                if (res.isSuccessful() && res.body() != null) {
                    byte[] body = res.body().bytes();
                    if (body.length == 0) {
                        // 空响应不落盘：规则侧把 0 字节密文当毒（hgpSize 校验），删了反而自愈。
                        // 与 HTTP 失败同口径抛错（8.83 官方：失败抛 Exception）。
                        throw new RuntimeException("下载文件失败：空响应");
                    }
                    java.nio.file.Files.write(tmp.toPath(), body);
                    try {
                        java.nio.file.Files.move(tmp.toPath(), f.toPath(),
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                        java.nio.file.Files.move(tmp.toPath(), f.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    tmp = null; // 已落定，catch 不再删
                    Logger.t(TAG).d("downloadToFile ok: %s", f.getAbsolutePath());
                    // 落盘视频自动清理（潇洒哥 2026-10-18 定：6 小时/8 个文件/400MB 删最旧）：
                    // 后台线程跑，不阻塞规则当前执行；落盘根与 downloadFile 一致。
                    HkMediaCacheCleaner.scheduleClean(new File(getFilePath("hiker://files/")));
                } else {
                    throw new RuntimeException("下载文件失败：HTTP " + res.code());
                }
            }
        } catch (Throwable e) {
            // 失败/中断：临时文件直接删，最终路径上永远不会出现半截文件。
            if (tmp != null) {
                try { tmp.delete(); } catch (Throwable ignored) {}
            }
            if (e instanceof RuntimeException && String.valueOf(e.getMessage()).startsWith("下载文件失败")) throw (RuntimeException) e;
            throw new RuntimeException("下载文件失败：" + e.getMessage());
        }
    }

    /** png2Ts 前置：清 hiker://files/_cache 目录内容（原版 FilesUtilsKt.clearCacheDir 为异步清，星辰同步清）。 */
    private void clearHkCacheDir() {
        try {
            File dir = new File(resolveHikerPath("hiker://files/_cache"));
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) deleteRecursively(f);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }

    /** FileUtil.getExtension 的口径：去 ? 查询与 ## 段后，取末段路径里最后一个点之后的串，无点回空串。 */
    private static String extensionOf(String url) {
        String s = url;
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        int h = s.indexOf("##");
        if (h >= 0) s = s.substring(0, h);
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : "";
    }

    /**
     * 8.83 FileUtil.png2Ts(path) 的逐条转写（smali_classes2 utils/FileUtil 2838）：
     * 输出路径＝原路径换扩展为 .ts（已存在先删）；仅解 bounds 取 mime——image/png 跳前 8 字节、
     * image/bmp 跳前 14 字节，其余 mime 或解 bounds 异常直接回原路径；拷贝（1MB 缓冲）到 .ts 后
     * 回 .ts 路径，拷贝出错记日志仍回 .ts 路径。原理：剥掉图片文件头，余下字节流当 TS 喂播放器。
     */
    private String png2TsFile(String path) {
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        String tsPath = dot > slash && dot > 0 ? path.substring(0, dot) + ".ts" : path + ".ts";
        File ts = new File(tsPath);
        if (ts.exists()) ts.delete();
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            android.graphics.BitmapFactory.decodeFile(path, options);
        } catch (Throwable t) {
            return path;
        }
        long skip;
        if ("image/png".equals(options.outMimeType)) {
            skip = 8;
        } else if ("image/bmp".equals(options.outMimeType)) {
            skip = 14;
        } else {
            return path;
        }
        try (java.io.InputStream in = new java.io.FileInputStream(path);
             java.io.OutputStream out = new java.io.FileOutputStream(ts)) {
            long remaining = skip;
            while (remaining > 0) {
                long skipped = in.skip(remaining);
                if (skipped <= 0) break;
                remaining -= skipped;
            }
            byte[] buffer = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        } catch (Throwable t) {
            Logger.t(TAG).e(t, "png2Ts copy failed: %s", path);
        }
        return tsPath;
    }

    /**
     * 清同目标的历史临时下载文件（.part-<唯一> 残留）。
     * 只删 60 分钟前的：在途的并发下载因年轻跳过；P0 预热的固定名 X.part 无 "-<唯一>" 后缀，不会被扫到。
     */
    private static void sweepStalePartFiles(File target) {
        try {
            File dir = target.getParentFile();
            if (dir == null) return;
            final String prefix = target.getName() + ".part-";
            File[] kids = dir.listFiles();
            if (kids == null) return;
            long cutoff = System.currentTimeMillis() - 60L * 60 * 1000;
            for (File k : kids) {
                if (k.isFile() && k.getName().startsWith(prefix) && k.lastModified() < cutoff) {
                    try { k.delete(); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 执行首页/分类的 js: 规则，返回条目列表。
     */
    public List<HkItem> parseList(String jsCode, String myUrl) throws Exception {
        return parseList(jsCode, myUrl, 1);
    }

    /**
     * 执行首页/分类的 js: 规则，返回条目列表（带页码，注入 MY_PAGE）。
     */
    public List<HkItem> parseList(String jsCode, String myUrl, int page) throws Exception {
        // 提交时捕获当前绑定，任务在引擎线程开头重申：防提交与执行之间
        // 别的页 bindRule 换掉 this.rule 造成串页（与带规则入口的锁互补）
        final HkRule boundRule = this.rule;
        return submit(() -> {
            if (boundRule != null) this.rule = boundRule;
            results.clear();
            error = null;
            preResultActive = false;
            // R6: 求值前清掉 refreshPage 标记，避免上次残留被误判为本次的新请求
            refreshRequested = false;
            refreshToTop = false;
            this.page = Math.max(1, page);
            setContext(myUrl);
            try {
                // 8.83 parseHome 同款：每次求值注入 fresh CALLBACK_KEY 并登记正文表
                //（正文=预抓正文，HkEngine.home() 经 setEvalResCodeBody 传入）
                String cbKey = armCallbackKey(evalResCodeBody);
                evalJs(getMyCallbackKey(cbKey) + stripJsPrefix(jsCode));
            } catch (Throwable e) {
                // 之前异常直接上抛到 HkEngine.home() 被吞掉只剩 debug 日志，
                // UI 永远只显示"加载失败"。先把根因记下来，供 getError()/空态展示。
                error = jsErrorMsg(e);
                throw e;
            }
            return drainResults();
        }).get(EVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** 带当前页规则的入口（8.83 parseHome 形态：规则随求值带入）：先重绑再走原路。 */
    public List<HkItem> parseList(HkRule rule, String jsCode, String myUrl, int page) throws Exception {
        synchronized (ruleEvalLock) {
            bindRule(rule);
            return parseList(jsCode, myUrl, page);
        }
    }

    /**
     * 执行搜索的 js: 规则，返回条目列表。
     */
    public List<SearchResult> parseSearch(String jsCode, String myUrl, String keyword) throws Exception {
        return parseSearch(jsCode, myUrl, keyword, 1);
    }

    /**
     * 执行搜索的 js: 规则，返回搜索结果（带页码，注入 MY_PAGE）。
     * 8.83 JSEngine.callbackSearchResult 同款：JS 结果按 title/url/desc/descMore/
     * content/img/extra/type 映射为独立 SearchResult（type 硬编码 "video"）。
     */
    public List<SearchResult> parseSearch(String jsCode, String myUrl, String keyword, int page) throws Exception {
        final HkRule boundRule = this.rule;
        return submit(() -> {
            if (boundRule != null) this.rule = boundRule;
            results.clear();
            error = null;
            preResultActive = false;
            // R6: 求值前清掉 refreshPage 标记，避免上次残留被误判
            refreshRequested = false;
            refreshToTop = false;
            this.page = Math.max(1, page);
            setContext(myUrl);
            ScriptableObject.putProperty(scope, "MY_KEYWORD", keyword == null ? "" : keyword);
            try {
                // 8.83 parseSearch 同款：每次求值注入 fresh CALLBACK_KEY 并登记正文表
                //（正文=搜索预抓正文，调用方经 setEvalResCodeBody 传入）
                String cbKey = armCallbackKey(evalResCodeBody);
                currentMyType = "search";
                evalJs(getMyCallbackKey(cbKey) + stripJsPrefix(jsCode));
            } catch (Throwable e) {
                error = jsErrorMsg(e);
                throw e;
            }
            return drainSearchResults();
        }).get(EVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** 带当前页规则的入口（8.83 parseSearchRes 形态：规则随求值带入）：先重绑再走原路。 */
    public List<SearchResult> parseSearch(HkRule rule, String jsCode, String myUrl, String keyword, int page) throws Exception {
        synchronized (ruleEvalLock) {
            bindRule(rule);
            return parseSearch(jsCode, myUrl, keyword, page);
        }
    }

    /** setStrResult/setLastChapterResult 存下的字符串结果。 */
    public String getStrResult() {
        return strResult;
    }

    /** setLastChapterRule 存下的最新章节规则。 */
    public String getLastChapterRule() {
        return lastChapterRule;
    }

    /** setPagePicUrl 存下的页面配图。 */
    public String getPagePicUrl() {
        return pagePicUrl;
    }

    /**
     * HkSelector.JsEvaluator / Parser.JsEvaluator 实现：选择器 .js: 值加工。
     * 把取值结果作为全局 input，执行 JS 片段并返回结果字符串。
     * 已在 JS 线程时（如 pdfh/pdfa 的 .js: 回调）直接求值，避免单线程池死锁。
     */
    @Override
    public String eval(String js, String input) {
        error = null;
        try {
            if (Thread.currentThread() == jsThread) {
                ScriptableObject.putProperty(scope, "input", input == null ? "" : input);
                return argStr(evalJs(js));
            }
            return submit(() -> {
                ScriptableObject.putProperty(scope, "input", input == null ? "" : input);
                Object r = evalJs(js);
                // 8.83 语义：数字/布尔返回值转字符串（如 .js: 取 input.id 为数字时），
                // 用 argStr() 而非裸 getString()（后者对 Number 返回 null → ""）。
                return argStr(r);
            }).get(EVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            String msg = e.getCause() != null && e.getCause().getMessage() != null
                    ? e.getCause().getMessage() : e.getMessage();
            error = msg == null || msg.isEmpty() ? "JS 执行失败" : msg;
            Logger.t(TAG).d("evalJs failed: %s", e.getMessage());
            return input == null ? "" : input;
        }
    }

    /** D2：8.83 JSEngine.globalMap 同款——进程共享的 JS 全局变量表，挂载为图片 scope 的 `_globalMap`。 */
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> imageGlobalMap = new java.util.concurrent.ConcurrentHashMap<>();

    // W3：图片 @js= 专用进程单例（8.83 JSEngine.getInstance() 同款）。
    // 图片求值不再走规则运行时：规则的 putVar 变量对图片 JS 不可见（原版语义），
    // 图片 JS 自己的 putVar/getVar 落到单例变量表，与规则隔离。
    private static volatile HkJsRuntime imageEngine;

    /**
     * W3：取图片求值单例。DCL；初始化失败返回 null（调用方退化为原图直出）。
     */
    public static HkJsRuntime imageEngine() {
        HkJsRuntime e = imageEngine;
        if (e == null) {
            synchronized (HkJsRuntime.class) {
                e = imageEngine;
                if (e == null) {
                    try {
                        e = new HkJsRuntime(null);
                        e.initImageEngine();
                        imageEngine = e;
                    } catch (Throwable t) {
                        Logger.t(TAG).e(t, "imageEngine init failed");
                        return null;
                    }
                }
            }
        }
        return e;
    }

    /**
     * W3：图片单例的轻量初始化——只进 Rhino Context，不跑规则 init
     *（无规则、无 preRule、无 kv/config；求值所需 API 每次在新 scope 里现场注册）。
     */
    private void initImageEngine() throws Exception {
        java.util.concurrent.Future<?> f = submit(() -> {
            jsThread = Thread.currentThread();
            // 8.83 initRhino 同款：单 Context + 禁优化器 + 语言版本 200
            rhinoCx = Context.enter();
            rhinoCx.setOptimizationLevel(-1);
            rhinoCx.setLanguageVersion(200);
            return null;
        });
        try {
            f.get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new Exception("imageEngine init timeout", e);
        }
    }

    /**
     * 8.83 OkHttpStreamFetcher.evalJS 语义：图片 {@code @js=} 求值。
     *
     * <p>{@code input} 绑定为 Java InputStream（响应流；下载失败时为 null）。
     * JS 求值结果为 InputStream（或其 NativeJavaObject 包装）才采用，且与原 input
     * 不同时关闭原流；结果非流或任何异常 → 返回原 input（调用方决定失败处理）。
     * W3：走图片单例、每次 {@code initStandardObjects()} 全新 scope（8.83
     * JSEngine.smali:14051 同款），不再以规则 scope 为原型——规则变量对图片 JS
     * 不可见，图片 JS 的变量也不写穿到规则。</p>
     */
    public InputStream evalImageJs(String jsCode, InputStream input, Map<String, List<String>> headers, String urlString) {
        HkJsRuntime img = imageEngine();
        if (img == null) return input;
        return img.evalImageJs(jsCode, input, headers, urlString, null);
    }

    /**
     * D3：8.83 并发模型（OkHttpStreamFetcher.evalJS / JSEngine.evalJS 同款）——
     * 每次求值在调用线程（OkHttp 回调线程）进独立 Rhino Context + 建独立 scope，
     * 不再经图片单线程 executor 串行排队。线程安全靠「每求值独立」构造：
     * Context、scope、挂载物全部是本次调用的局部量。
     * W3（b4210351）「每次全新 scope」保留；fe18e2d1 的 FileUtil 补链与
     * 54d569bb 的 registerApi 空指针守卫在新模型下照常成立（见内联说明）。
     * ruleTitle 只做 MY_TITLE 回退（无害项保留）。
     */
    public InputStream evalImageJs(String jsCode, InputStream input, Map<String, List<String>> headers, String urlString, String ruleTitle) {
        HkJsRuntime img = imageEngine();
        if (img == null) return input;
        Context cx = Context.enter();
        cx.setOptimizationLevel(-1);
        cx.setLanguageVersion(200);
        try {
            return img.evalImageJsInternal(cx, jsCode, input, headers, urlString, ruleTitle);
        } catch (Throwable t) {
            String cls = t.getClass().getSimpleName();
            String msg = t.getMessage();
            SpiderDebug.log("hkimage", "图片解密求值失败 url=%s 类型=%s 信息=%s",
                    urlString, cls, msg == null ? "" : msg);
            return input;
        } finally {
            Context.exit();
        }
    }

    /**
     * D3：调用线程上的真实求值体。cx 与 imageScope 均为本次调用独占。
     * registerApiInto 的 apiRegisterLock 照常保护「注册目标切换 + sharedStorage0
     * 首次创建」（注册期串行、求值期并行）；registerApi 内部的 54d569bb 守卫
     * （Context.getCurrentContext().newObject(非空 target)）在新模型下恒成立——
     * 注册发生在已 enter 的调用线程内，currentContext 即本次的 cx。
     */
    private InputStream evalImageJsInternal(Context cx, String jsCode, InputStream input, Map<String, List<String>> headers, String urlString, String ruleTitle) {
        // W3：全新 scope（8.83 JSEngine.smali:14051 initStandardObjects），
        // 不再 newObject(规则 scope) 挂原型链。
        ScriptableObject imageScope = cx.initStandardObjects();
        // 8.83 line 454-455：ImporterTopLevel + initRequire
        org.mozilla.javascript.ImporterTopLevel.init(cx, imageScope, false);
        initRequire(cx, imageScope);
        // 8.83 allFunctions 等价：全部宿主 API 函数现场注册进新 scope
        registerApiInto(imageScope);
        // C5 保留：重求值 Hikerurl.js，$ / $.require 必可用。
        // 8.83 crypto-java.js 原生 AES 接线：原版直引海阔包内 FileUtil，星辰注入宿主实现。
        ScriptableObject.putProperty(imageScope, "__cryptoFileUtil",
                Context.javaToJS(new HkCryptoFileUtil(), imageScope));
        // D2：8.83 JSEngine.evalJS 挂载物对齐（JSEngine.smali:13914 段，Java 456-461 行）：
        // input / _globalMap / rc4 / _base64 / javaContext / javaLoader。
        ScriptableObject.putProperty(imageScope, "_globalMap", Context.javaToJS(imageGlobalMap, imageScope));
        ScriptableObject.putProperty(imageScope, "rc4", Context.javaToJS(new HkRc4(), imageScope));
        ScriptableObject.putProperty(imageScope, "_base64", Context.javaToJS(new HkMyBase64(), imageScope));
        ScriptableObject.putProperty(imageScope, "javaContext", Context.javaToJS(this, imageScope));
        ScriptableObject.putProperty(imageScope, "javaLoader", Context.javaToJS(HkJsRuntime.class.getClassLoader(), imageScope));
        injectAndroidPackage(imageScope);
        // D7：图片 scope 里 $.require("hiker://assets/...") 直通宿主资产——
        // 宿主函数读资产文本，JS 侧在当前图片 scope 内按 Hikerurl 模块机求值
        // （new Function(module,exports,__filename) + $.exports 交接），不绕
        // Hikerurl 的 request/getScript 链。目标：一个APP 的 crypto-java.js
        // 在图片 scope 里完整加载（android.util.Base64 已注入，__cryptoFileUtil 已注入）。
        ScriptableObject.putProperty(imageScope, "__imageRequireAsset", new org.mozilla.javascript.BaseFunction() {
            @Override
            public Object call(org.mozilla.javascript.Context cxx, Scriptable scope, Scriptable thisObj, Object[] args) {
                String p = args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
                return loadAssetText(p);
            }
        });
        // wrapImageJs 的 JS 侧 catch 走此函数记可导出日志（原 log() 只进 logcat）。
        ScriptableObject.putProperty(imageScope, "__hkImageLog", new org.mozilla.javascript.BaseFunction() {
            @Override
            public Object call(org.mozilla.javascript.Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                SpiderDebug.log("hkimage", "图片解密JS内报错 host=%s 信息=%s",
                        hostOfUrl(urlString), args != null && args.length > 0 ? String.valueOf(args[0]) : "");
                return null;
            }
        });
        long t0 = android.os.SystemClock.elapsedRealtime();
        try {
            // 重求值 Hikerurl.js，确保 $ 完整可用
            try {
                String hikerUrlJs = loadAsset("Hikerurl.js");
                if (!TextUtils.isEmpty(hikerUrlJs)) {
                    cx.evaluateString(imageScope, hikerUrlJs, "Hikerurl.js", 1, null);
                }
            } catch (Throwable t) {
                Logger.t(TAG).d("evalImageJs: re-eval Hikerurl.js failed: %s", t.getMessage());
            }
            // D7 直通补丁：Hikerurl.js 求值成功（$ / $.require 可用）时才装；
            // 若 Hikerurl.js 没起来，走 ensureDollarShim() 的兜底 require。
            try {
                cx.evaluateString(imageScope, imageAssetRequirePatch(), "imageAssetRequire", 1, null);
            } catch (Throwable t) {
                Logger.t(TAG).d("evalImageJs: asset require patch failed: %s", t.getMessage());
            }
            // fe18e2d1 保留：UU 等规则图片回调直引 com.example.hikerview.utils.FileUtil
            // （8.83 包内真实类）。星辰无此包：建链定义指向同一个 HkCryptoFileUtil 实例
            // （与 __cryptoFileUtil 既有 shim 策略一致；crypto-java.js:17 的同款兼容判断
            // 即依赖 __cryptoFileUtil）。修前 Rhino 侧只剩 JavaPackage 占位、调 toBytes
            // 抛 TypeError → 封面空白。
            // 注：var com 只遮蔽 ImporterTopLevel 对 com.* Java 包的回退；图片解密 JS 现实只会
            // 用到 com.example.hikerview.utils.FileUtil（UU/oneapp 皆如此），java.* 与
            // android 包不受影响。
            try {
                cx.evaluateString(imageScope,
                        "var com = {};\n"
                                + "com.example = {};\n"
                                + "com.example.hikerview = {};\n"
                                + "com.example.hikerview.utils = {};\n"
                                + "com.example.hikerview.utils.FileUtil = __cryptoFileUtil;",
                        "fileutilShim", 1, null);
            } catch (Throwable t) {
                Logger.t(TAG).d("evalImageJs: fileutil shim failed: %s", t.getMessage());
            }
            ScriptableObject.putProperty(imageScope, "input", Context.javaToJS(input, imageScope));
            String code = generateMyHeaders(headers) + getMyUrlJs(urlString) + getMyTitleJs(jsCode, ruleTitle) + ensureDollarShim() + wrapImageJs(jsCode);
            Object r = cx.evaluateString(imageScope, code, "imageJs", 1, null);
            InputStream out = coerceImageStream(r, input, urlString);
            SpiderDebug.log("hkimage", "图片解密完成 host=%s 耗时=%dms 结果=%s",
                    hostOfUrl(urlString), android.os.SystemClock.elapsedRealtime() - t0,
                    out == null ? "null" : out.getClass().getSimpleName() + (out == input ? "(回退原字节)" : ""));
            return out;
        } catch (Throwable t) {
            // 图片解密求值失败：记进可导出日志（此前静默吞掉，真机断点不可见）
            String cls = t.getClass().getSimpleName();
            String msg = t.getMessage();
            SpiderDebug.log("hkimage", "图片解密求值失败 url=%s 类型=%s 信息=%s",
                    urlString, cls, msg == null ? "" : msg);
            return input;
        }
    }

    /**
     * D7：图片 scope 的 hiker://assets 直通补丁（JS）。
     * 拦截 $.require("hiker://assets/...")：宿主 __imageRequireAsset 读资产文本，
     * 在当前图片 scope 内按 Hikerurl 模块机求值（new Function(module,exports,__filename)
     * + $.exports 交接，与 Module._extensions.js / Module.prototype.load 同语义），
     * 带模块缓存；非 assets 路径走原 $.require。
     */
    private static String imageAssetRequirePatch() {
        return "(function(){\n"
                + "if (typeof $ === 'undefined' || typeof $.require !== 'function') return;\n"
                + "var __assetModules = {};\n"
                + "function __imageAssetRequire(path) {\n"
                + "  var key = String(path);\n"
                + "  if (__assetModules[key]) return __assetModules[key].exports;\n"
                + "  var code = String(__imageRequireAsset(key));\n"
                + "  if (!code) throw new Error('Module \"' + key + '\" cannot be found.');\n"
                + "  if (code.indexOf('js:') === 0) code = code.slice(3);\n"
                + "  var module = { id: key, exports: {} };\n"
                + "  var __savedExports = $.exports;\n"
                + "  $.exports = module.exports;\n"
                + "  try {\n"
                + "    var __fn = new Function('module', 'exports', '__filename', code);\n"
                + "    __fn.call(module.exports, module, module.exports, key);\n"
                + "    if ($.exports !== module.exports) module.exports = $.exports;\n"
                + "  } finally { $.exports = __savedExports; }\n"
                + "  __assetModules[key] = module;\n"
                + "  return module.exports;\n"
                + "}\n"
                + "var __origRequire = $.require;\n"
                + "$.require = function(path, importParam, headers, time) {\n"
                + "  var p = String(path == null ? '' : path);\n"
                + "  if (p.indexOf('hiker://assets/') === 0) return __imageAssetRequire(p);\n"
                + "  return __origRequire.call(this, path, importParam, headers, time);\n"
                + "};\n"
                + "})();\n";
    }

    /**
     * 图片 @js= 求值时的 $ 兜底。
     *
     * <p>8.83 的图片 JS 求值上下文（OkHttpStreamFetcher.evalJS）与规则共用 JSEngine 单例，
     * $（Hikerurl.js）必然可用。但星辰是多规则 HkJsRuntime，图片求值复用的 scope
     * 可能因 Hikerurl.js 注入失败而没有 $，导致解密 JS 的 $.require("hiker://assets/crypto-java.js")
     * 抛 ReferenceError，被 wrapImageJs 的 try/catch 吞掉后返回加密原字节，Glide 解码失败。</p>
     *
     * <p>此处注入最小 $ shim：仅当 $ 或 $.require 不可用时生效，提供能加载
     * hiker://assets/crypto-java.js 的 require（从宿主 assets 读取，复用 $.exports 导出约定）。</p>
     */
    private String ensureDollarShim() {
        try {
            String cryptoJs = loadAsset("crypto-java.js");
            if (cryptoJs == null) return "";
            // 转义为 JS 字符串字面量
            String esc = escapeJsString(cryptoJs);
            return "if (typeof $ === 'undefined' || typeof $.require !== 'function') {\n" +
                    "  var $ = (typeof $ !== 'undefined') ? $ : {};\n" +
                    "  $.exports = $.exports || {};\n" +
                    "  $.require = (function() {\n" +
                    "    var __cryptoCache = null;\n" +
                    "    var __cryptoCode = '" + esc + "';\n" +
                    "    return function(path) {\n" +
                    "      if (path === 'hiker://assets/crypto-java.js') {\n" +
                    "        if (__cryptoCache) return __cryptoCache;\n" +
                    "        eval(__cryptoCode);\n" +
                    "        __cryptoCache = $.exports;\n" +
                    "        $.exports = {};\n" +
                    "        return __cryptoCache;\n" +
                    "      }\n" +
                    "      throw new Error('Module not found in image context: ' + path);\n" +
                    "    };\n" +
                    "  })();\n" +
                    "}\n";
        } catch (Throwable t) {
            return "";
        }
    }

    /** 8.83 wrapJS：@js= 体包 try/catch，异常记可导出日志后按"非流"处理（返回原 input）。 */
    private static String wrapImageJs(String jsCode) {
        return "try{\n" + jsCode + "\n}catch(e){\ntry{__hkImageLog(String(e));}catch(_){}\ntry{log(e.toString());}catch(_){}\n}";
    }

    /**
     * 8.83 运行环境还原：原版 crypto-java.js 直写 {@code android.util.Base64}（裸包名）。
     * Rhino 默认只导入 java/javax/org/com/edu/net，裸 {@code android} 解析不到——原版宿主
     * 实际放行了 android.*（其 ClassShutter 只拉黑危险类）。此处显式注入 android 包，
     * 使资产库文件能与原版逐字节一致地跑在我们的 Rhino 里。
     */
    private static void injectAndroidPackage(Scriptable target) {
        try {
            org.mozilla.javascript.NativeJavaPackage pkg =
                    new org.mozilla.javascript.NativeJavaPackage("android", HkJsRuntime.class.getClassLoader());
            pkg.setParentScope(target);
            pkg.setPrototype(org.mozilla.javascript.ScriptableObject.getObjectPrototype(target));
            ScriptableObject.putProperty(target, "android", pkg);
        } catch (Throwable ignored) {
        }
    }

    /** 8.83 JSEngine.generateMY("MY_HEADERS", multimap) 语义。 */
    private static String generateMyHeaders(Map<String, List<String>> headers) {
        if (headers == null) return "var MY_HEADERS = null;\n";
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                o.put(e.getKey(), new org.json.JSONArray(e.getValue()));
            }
            return "var my_headers = '" + escapeJsString(o.toString()) + "';\n var MY_HEADERS = JSON.parse(my_headers);\n";
        } catch (Throwable t) {
            return "var MY_HEADERS = null;\n";
        }
    }

    /** 8.83 JSEngine.getMyUrl 语义：var MY_URL / var MY_HOME。 */
    private static String getMyUrlJs(String url) {
        if (url == null) url = "";
        String esc = escapeJsString(url);
        return "var MY_URL = '" + esc + "';\nvar MY_HOME = '" + getHomeUrl(esc) + "';\n";
    }

    /** 8.83 StringUtil.getHome 语义：scheme://dom。 */
    private static String getHomeUrl(String url) {
        try {
            String u = getRealImageUrl(url);
            String dom = getImageDom(u);
            String scheme = u.split("://")[0];
            return scheme + "://" + dom;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 图片 @js= 求值补裸全局 MY_TITLE。
     *
     * <p>规则 {@code $().image(fn)} 生成的代码体引用裸 {@code MY_TITLE}
     *（如 {@code $.require("data?rule=" + MY_TITLE)}），图片求值作用域若无此全局则报
     * ReferenceError，被 try/catch 吞掉后返回加密原字节，Glide 解码失败、封面空白。
     * 取值：优先从代码体 {@code $.hiker.MY_TITLE = "..."} 提取，取不到时用调用方传入的规则标题
     *（W3：图片走进程单例，单例本身无规则，标题由调用链显式传入）。</p>
     */
    private String getMyTitleJs(String jsCode, String ruleTitle) {
        String title = null;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\$\\.hiker\\.MY_TITLE\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                    .matcher(jsCode == null ? "" : jsCode);
            if (m.find()) title = m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        } catch (Throwable ignored) {
        }
        if (android.text.TextUtils.isEmpty(title)) title = ruleTitle;
        if (android.text.TextUtils.isEmpty(title)) return "";
        return "var MY_TITLE = '" + escapeJsString(title) + "';\n";
    }

    /** JS 单引号字符串转义（adblockplus Utils.escapeJavaScriptString 原样：\n \r \" \' \\ 转义 + U+2028/U+2029）。 */
    private static String escapeJsString(String s) {
        if (s == null) return "";
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

    /** 8.83 StringUtil.getRealUrl 简化版：去 @lazyRule=/@rule= 后缀与 hiker://empty 前缀。 */
    private static String getRealImageUrl(String url) {
        if (TextUtils.isEmpty(url)) return url;
        try {
            String u = url.split("@lazyRule=")[0].split("@rule=")[0];
            if (u.startsWith("hiker://empty#http")) u = replaceOnce(u, "hiker://empty#", "");
            else if (u.startsWith("webview://")) u = u.replaceFirst("webview://", "");
            else if (u.startsWith("hiker://empty##http") || u.startsWith("hiker://empty##https%3A%2F%2F") || u.startsWith("hiker://empty##http%3A%2F%2F"))
                u = replaceOnce(u, "hiker://empty##", "");
            else if (u.startsWith("hiker://empty$http")) u = replaceOnce(u, "hiker://empty$", "");
            else if (u.startsWith("hiker://empty$$http")) u = replaceOnce(u, "hiker://empty$$", "");
            int d = u.indexOf("$$$");
            if (d >= 0) u = u.substring(0, d);
            return u;
        } catch (Throwable t) {
            return url;
        }
    }

    private static String replaceOnce(String s, String from, String to) {
        int i = s.indexOf(from);
        return i < 0 ? s : s.substring(0, i) + to + s.substring(i + from.length());
    }

    /** 8.83 StringUtil.getDom/getDom0 语义：取 host。 */
    private static String getImageDom(String url) {
        if (TextUtils.isEmpty(url)) return url;
        try {
            String u = getRealImageUrl(url);
            String[] parts = u.split("://");
            if (parts.length > 1) return parts[1].split("/")[0];
            parts = u.split("//");
            if (parts.length > 1) return parts[1].split("/")[0];
            return u.replaceFirst("http://", "").replaceFirst("https://", "").replaceFirst("hiker://", "").split("/")[0];
        } catch (Throwable t) {
            return "";
        }
    }

    /** 求值结果 → 流：是流则采用（与原流不同时关原流），否则返回原 input。
     *  我方 crypto-java.js 是纯 JS 重写版，解密函数的 toInputStream() 返回的是
     *  JS 数字数组（bytes.slice()），不是 Java 流；原版靠 Rhino 互操作直接返回真流。
     *  这里补上数组 → 字节流的转换，否则解密结果会被丢弃、回退成加密原字节，
     *  图片解码失败（「一个APP」首页大图无封面即此因，本机用真实数据复现定案）。 */
    private static InputStream coerceImageStream(Object r, InputStream input, String urlString) {
        try {
            Object v = r;
            if (v instanceof NativeJavaObject) v = ((NativeJavaObject) v).unwrap();
            if (v instanceof NativeJavaArray) v = ((NativeJavaArray) v).unwrap();
            if (v instanceof InputStream) {
                if (v != input && input != null) {
                    try {
                        input.close();
                    } catch (Throwable ignored) {
                    }
                }
                return (InputStream) v;
            }
            byte[] bytes = null;
            if (v instanceof byte[]) {
                bytes = (byte[]) v;
            } else if (v instanceof NativeArray) {
                NativeArray arr = (NativeArray) v;
                int len = (int) arr.getLength();
                bytes = new byte[len];
                for (int i = 0; i < len; i++) {
                    Object e = arr.get(i, arr);
                    bytes[i] = (byte) (e instanceof Number ? ((Number) e).intValue() : 0);
                }
            } else if (v instanceof Object[]) {
                Object[] arr = (Object[]) v;
                bytes = new byte[arr.length];
                for (int i = 0; i < arr.length; i++) {
                    bytes[i] = (byte) (arr[i] instanceof Number ? ((Number) arr[i]).intValue() : 0);
                }
            }
            if (bytes != null) {
                if (input != null) {
                    try {
                        input.close();
                    } catch (Throwable ignored) {
                    }
                }
                return new ByteArrayInputStream(bytes);
            }
            // 真回退：记原因进可导出日志（此前静默回原字节，真机断点不可见）
            String rt = r == null ? "null" : r.getClass().getSimpleName();
            if (r != null) {
                try {
                    String rs = String.valueOf(org.mozilla.javascript.Context.toString(r));
                    rt += ":" + rs.substring(0, Math.min(80, rs.length()));
                } catch (Throwable ignored) {
                }
            }
            SpiderDebug.log("hkimage", "图片解密结果非流，回退原字节 host=%s 结果类型=%s", hostOfUrl(urlString), rt);
        } catch (Throwable ignored) {
        }
        return input;
    }

    /**
     * M4：lazyRule / 播放 js 的独立作用域求值。
     *
     * <p>按"独立作用域"铁律：只注入全局 Hiker API，外加 {@code MY_URL}/{@code input}
     * 两个形参（均为当前页面地址），不依赖 JS 闭包。返回代码求值结果的字符串；
     * 若求值为空则兜底取 {@code setResult} 收集到的第一条 url。</p>
     */
    public String evalLazy(String jsCode, String pageUrl) throws Exception {
        final HkRule boundRule = this.rule;
        return submit(() -> {
            if (boundRule != null) this.rule = boundRule;
            error = null;
            setContext(pageUrl);
            // 入口口令/输入框：若有一次性 input 覆盖（用户输入的文本），优先使用；
            // 否则按原逻辑用 pageUrl。读后清零，保证只生效一次。
            String inputVal = nextInputOverride != null ? nextInputOverride : (pageUrl == null ? "" : pageUrl);
            nextInputOverride = null;
            ScriptableObject.putProperty(scope, "input", inputVal);
            Object r;
            try {
                r = evalJs(stripJsPrefix(jsCode));
            } catch (Exception e) {
                logEvalError("evalLazy", jsCode, e);
                throw e;
            }
            // 同 eval()：用 argStr() 处理数字/布尔返回值
            String s = argStr(r);
            s = s.trim();
            if (s.isEmpty() || "undefined".equals(s) || "null".equals(s)) {
                List<HkItem> items = drainResults();
                if (!items.isEmpty() && !TextUtils.isEmpty(items.get(0).getUrl())) {
                    return items.get(0).getUrl().trim();
                }
                return "";
            }
            return s;
        }).get(EVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** 带当前页规则的入口：先重绑再走原路（子页 lazy 求值的 MY_RULE 须为子页规则）。 */
    public String evalLazy(HkRule rule, String jsCode, String pageUrl) throws Exception {
        synchronized (ruleEvalLock) {
            bindRule(rule);
            return evalLazy(jsCode, pageUrl);
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

    /** 选择器文本预处理：冲突字符解码（？？→?、＆＆→&、；；→;、，，→,）。 */
    private String sel(Object o) {
        return HkSelector.decodeConflict(String.valueOf(o));
    }

    public String getError() {
        return error;
    }

    /**
     * 8.83 对齐（JSEngine.parseHomeCallBack 失败回调语义）：预抓取数失败时把真因
     * 记进引擎错误通道，供调用方空态/错误展示；求值不会再跑，error 不会被次生错误覆盖。
     */
    public void noteFetchFailure(String msg) {
        error = msg == null || msg.isEmpty() ? "取数失败" : msg;
    }

    /**
     * JS 引擎最后一次 fetch 的真实 URL（图片自动 Referer 用，对齐 8.83 GlideUtil.getGlideUrl 语义）。
     * 注意：setContext 时会先被置为 MY_URL（含 hiker://empty## 前缀的模板 URL），
     * 只有 fetch 成功后才是真实 http(s) URL；调用方需自行校验 scheme。
     */
    public String getLastUrl() {
        return lastUrl;
    }

    /**
     * 设置 @rule= js: 预检时的响应正文（调用方先 HkHttp.get(baseUrl) 取回）。
     * getResCode()/getCode() 在无 CALLBACK_KEY 时会返回此值；传 null 清除。
     */
    public void setEvalResCodeBody(String body) {
        evalResCodeBody = body;
    }

    /**
     * 取异常链上第一条非空 message（ExecutionException 包裝后的根因），
     * 供 parseList/parseSearch 在 JS 求值失败时记录，避免"加载失败"无从查起。
     */
    private static String jsErrorMsg(Throwable e) {
        Throwable t = e;
        while (t != null) {
            String m = t.getMessage();
            if (m != null && !m.trim().isEmpty()) {
                String s = m.trim();
                // Rhino 异常常带堆栈，截短到一行
                int nl = s.indexOf('\n');
                if (nl > 0) s = s.substring(0, nl);
                return s.length() > 160 ? s.substring(0, 160) : s;
            }
            t = t.getCause();
        }
        return e == null ? "" : String.valueOf(e.getClass().getSimpleName());
    }

    /**
     * 取走 refreshPage 请求标记（读后清零）。供 HkRouter.evalTab 等在 JS 求值后检查：
     * 若规则回调里调了 refreshPage，上层应重刷当前列表。
     */
    public boolean consumeRefreshRequest() {
        boolean r = refreshRequested;
        refreshRequested = false;
        return r;
    }

    /**
     * 当前 results 数量快照。供 @rule=js: 点击动作在 evalLazy 前后成对调用，
     * 判断规则是否就地改了列表（如自绘"加载更多"按钮调 addItemBefore/deleteItemByCls 追加条目）。
     * 必须在 evalLazy 同一后台线程调用（evalLazy 内部 submit().get() 已同步 JS 线程）。
     */
    public int actionResultCount() {
        return results.size();
    }

    /**
     * 当前 results 拷贝。供 @rule=js: 点击动作取回规则就地追加的条目，
     * 上层按 url+title 去重后并入当前列表（8.83 addItemBefore 就地更新同款）。
     */
    public List<HkItem> snapshotActionResults() {
        return new ArrayList<>(results);
    }

    /**
     * 取走 goTo 导航请求（读后清零）。供 @rule=js: 等动作 JS 求值后检查：
     * 若规则调了 goTo(url)，上层应导航到该 URL（如 hiker://page/detail 打开 V4）。
     * @return 目标 URL，无请求时返回 ""。
     */
    public String consumeGoToRequest() {
        String r = goToUrl == null ? "" : goToUrl;
        goToUrl = null;
        return r;
    }

    /** refreshPage 请求标记当前是否为 true（不读后清零，供调用方做"变化检测"）。 */
    public boolean isRefreshRequested() {
        return refreshRequested;
    }

    /**
     * 设置下一次 evalLazy 的 input 覆盖值（入口口令/输入框流程用）。
     * 官方语义：input 条目点击时用户输入的文本作为 input 变量，而非 pageUrl。
     */
    public void setNextInputOverride(String input) {
        this.nextInputOverride = input;
    }

    public boolean isRefreshToTop() {
        return refreshToTop;
    }

    public void destroy() {
        destroyed = true;
        clearAllTimers();
        try {
            submit(() -> {
                try {
                    // Rhino Context 与线程绑定：enter/exit 必须在同一线程配对（即本单线程 executor）
                    if (rhinoCx != null) Context.exit();
                } catch (Throwable ignored) {
                }
                rhinoCx = null;
                scope = null;
                return null;
            }).get(DESTROY_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        } finally {
            executor.shutdownNow();
            try {
                timerScheduler.shutdownNow();
            } catch (Throwable ignored) {
            }
        }
    }

    // ================= 内部实现 =================

    private void setContext(String myUrl) {
        lastUrl = myUrl == null ? "" : myUrl;
        ScriptableObject.putProperty(scope, "MY_URL", lastUrl);
        ScriptableObject.putProperty(scope, "MY_HOME", homeOf(lastUrl));
        ScriptableObject.putProperty(scope, "MY_PAGE", page);
        ScriptableObject.putProperty(scope, "MY_TICKET", "");
        ScriptableObject.putProperty(scope, "MOBILE_UA", UA_MOBILE);
        ScriptableObject.putProperty(scope, "PC_UA", UA_PC);
        // P1：补全官方注入变量
        ScriptableObject.putProperty(scope, "MY_TYPE", myType == null ? "" : myType);
        ScriptableObject.putProperty(scope, "MY_CLASS_URL", myClassUrl == null ? "" : myClassUrl);
        ScriptableObject.putProperty(scope, "MY_CLASS_NAME", myClassName == null ? "" : myClassName);
        ScriptableObject.putProperty(scope, "MY_NAME", rule == null ? "" : rule.getTitle());
        // 官方 generateMyParams：MY_PARAMS 是 JSON 对象不是字符串（空时为 {}）
        try {
            String mp = myParams == null || myParams.isEmpty() ? "{}" : myParams;
            // 照 8.83 generateMyParams：整串 JSON.parse 一次，字符串值永远保持字符串。
            // 曾自加「形似 JSON 的字符串还原成对象」的启发式，会把合法字符串参数
            // 误变成对象/数组污染全部规则，已撤（类型保真靠 extra 管道本身）。
            ScriptableObject.putProperty(scope, "MY_PARAMS", parseJson(mp));
        } catch (Throwable ignored) {
            ScriptableObject.putProperty(scope, "MY_PARAMS", rhinoCx.newObject(scope));
        }
        ScriptableObject.putProperty(scope, "MY_AREA", myArea == null ? "" : myArea);
        ScriptableObject.putProperty(scope, "MY_YEAR", myYear == null ? "" : myYear);
        ScriptableObject.putProperty(scope, "MY_SORT", mySort == null ? "" : mySort);
        // MY_YEAR_xxx / MY_AREA_xxx / MY_SORT_xxx：按当前取值动态注入（如 MY_YEAR_2024）
        if (myYear != null && !myYear.isEmpty())
            ScriptableObject.putProperty(scope, "MY_YEAR_" + myYear, myYear);
        if (myArea != null && !myArea.isEmpty())
            ScriptableObject.putProperty(scope, "MY_AREA_" + myArea, myArea);
        if (mySort != null && !mySort.isEmpty())
            ScriptableObject.putProperty(scope, "MY_SORT_" + mySort, mySort);
        try {
            Map<String, String> ua = new HashMap<>();
            ua.put("mobileUa", UA_MOBILE);
            ua.put("pcUa", UA_PC);
            ScriptableObject.putProperty(scope, "MY_UA", parseJson(GSON.toJson(ua)));
        } catch (Throwable ignored) {
        }
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", rule == null ? UA_MOBILE : rule.resolvedUa());
            ScriptableObject.putProperty(scope, "MY_HEADERS", parseJson(GSON.toJson(headers)));
        } catch (Throwable ignored) {
        }
        try {
            // W3：序列化的是 bindRule 绑定的当前页规则，不再是实例创建时规则。
            ScriptableObject.putProperty(scope, "MY_RULE", parseJson(GSON.toJson(rule)));
        } catch (Throwable e) {
            ScriptableObject.putProperty(scope, "MY_RULE", rhinoCx.newObject(scope));
        }
    }

    /**
     * 设置列表页上下文（HkEngine.home/search 在 parseList/parseSearch 之前调用），
     * 供 setContext 注入 MY_TYPE/MY_CLASS_URL/MY_CLASS_NAME/MY_PARAMS 等官方变量。
     */
    public void setListContext(String type, String classUrl, String className,
                              String params, String area, String year, String sort) {
        this.myType = type == null ? "" : type;
        this.myClassUrl = classUrl == null ? "" : classUrl;
        this.myClassName = className == null ? "" : className;
        this.myParams = params == null ? "" : params;
        this.myArea = area == null ? "" : area;
        this.myYear = year == null ? "" : year;
        this.mySort = sort == null ? "" : sort;
    }

    /**
     * 设置详情页 params（官方 dealRule 把点击条目的 extra 设为新规则的 params，
     * detail_find_rule 里通过 MY_PARAMS 取用）。extra 是 8.83 语义的原始 JSON 串
     * （ArticleList.extra），直接存，注入时 JSON.parse 一次，数字保持数字。
     */
    public void setDetailParams(String extraJson) {
        this.myParams = extraJson == null ? "" : extraJson;
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
        com.github.catvod.crawler.SpiderDebug.log("hk", "prerule start rule=%s", rule.getTitle());
        try {
            // preRule 的 MY_URL 同样传原始 url（保留 hiker://empty# 前缀）。
            setContext(HkHttp.expandUrl(rule.getUrl(), "", "", "", "", 1, false));
            evalJs(pre.trim().startsWith("js:") ? pre.trim().substring(3) : pre);
            Logger.t(TAG).d("preRule done for %s", rule.getTitle());
        } catch (Throwable e) {
            Logger.t(TAG).d("preRule failed for %s: %s", rule.getTitle(), e.getMessage());
            com.github.catvod.crawler.SpiderDebug.log("hk", "prerule failed rule=%s err=%s", rule.getTitle(), String.valueOf(e));
            // 之前静默吞掉，动态域名类规则（如 18AV）拿不到域名时用户只能看到"加载失败"。
            // 记下来供排障/UI 展示；不抛异常，保持原有"失败也继续加载"的行为。
            preRuleError = jsErrorMsg(e);
            // 规则在 preRule 里 showLoading 后异常退出时 hideLoading 不会再被调到，
            // 不兜底关掉的话加载框会永远挂在页面上（18AV 真机截图）
            try {
                HkLoading.hide(rule.getTitle());
            } catch (Throwable ignored) {
            }
        }
    }

    /** preRule 失败根因（null 表示 preRule 不存在或执行成功）。 */
    public String getPreRuleError() {
        return preRuleError;
    }

    /**
     * 注入海阔规则配置对象 {@code config}（海阔"长按规则→设置"的 key-value，如 config.host）。
     * 配置持久化在 plugins/hk/config/&lt;规则名&gt;.json；文件不存在时注入空对象，保证
     * {@code config.xxx} 不抛 ReferenceError（取值为 undefined）。
     */
    private void loadConfig() {
        try {
            String json = HkRuleManager.get().loadRuleConfig(rule.getTitle());
            Object obj = parseJson(json);
            ScriptableObject.putProperty(scope, "config", obj);
        } catch (Throwable e) {
            try {
                ScriptableObject.putProperty(scope, "config", rhinoCx.newObject(scope));
            } catch (Throwable ignored) {
            }
        }
    }

    private void collectResult(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) return;
        try {
            String json = args[0] instanceof NativeArray
                    ? jsStringify(args[0])
                    : argStr(args[0]);
            if (json == null) return;
            String t = json.trim();
            // 官方 callbackHomeResult 兼容：setResult({data:[...]}) 的对象形式。
            // 之前只认顶层数组，对象形式会被 GSON 抛错静默丢弃 → 整个列表为空（"加载失败"）。
            if (t.startsWith("{")) {
                Map<String, Object> obj = GSON.fromJson(t, new TypeToken<Map<String, Object>>() {}.getType());
                Object data = obj == null ? null : obj.get("data");
                if (data instanceof List) {
                    appendResultItems(GSON.toJson(data));
                } else {
                    Logger.t(TAG).d("collectResult: object without data array");
                }
                return;
            }
            appendResultItems(t);
        } catch (Throwable e) {
            Logger.t(TAG).d("collectResult failed: %s", e.getMessage());
        }
    }

    /** 把 JSON 数组字符串解析为条目并追加到 results（collectResult 的实际落子逻辑）。 */
    private void appendResultItems(String jsonArray) {
        List<Map<String, Object>> list = GSON.fromJson(jsonArray, MAP_LIST_TYPE);
        if (list == null) return;
            for (Map<String, Object> m : list) {
                if (collectRaw) {
                    Map<String, String> raw = new HashMap<>();
                    for (Map.Entry<String, Object> e : m.entrySet()) {
                        raw.put(e.getKey(), e.getValue() == null ? "" : stringifyValue(e.getValue()));
                    }
                    rawResults.add(raw);
                    continue;
                }
                HkItem item = new HkItem();
                item.setTitle(str(m, "title"));
                item.setUrl(str(m, "url"));
                // 8.83 JSEngine.convertToArticleList 顺序：img > pic > pic_url（按键存在判定）；
                // 其余兼容字段只在三者都不存在时才兜底，不得改变只给单一字段的规则的现有结果。
                String pic;
                if (m.containsKey("img")) pic = str(m, "img");
                else if (m.containsKey("pic")) pic = str(m, "pic");
                else pic = str(m, "pic_url");
                // 兼容更多封面字段名（规则写法不统一）
                if (TextUtils.isEmpty(pic)) pic = str(m, "image");
                if (TextUtils.isEmpty(pic)) pic = str(m, "cover");
                if (TextUtils.isEmpty(pic)) pic = str(m, "thumbnail");
                if (TextUtils.isEmpty(pic)) pic = str(m, "poster");
                if (TextUtils.isEmpty(pic)) pic = str(m, "imgUrl");
                if (TextUtils.isEmpty(pic)) pic = str(m, "picUrl");
                item.setPic(pic);
                item.setDesc(str(m, "desc"));
                String colType = str(m, "col_type");
                if (TextUtils.isEmpty(colType)) colType = str(m, "colType");
                if (TextUtils.isEmpty(colType)) colType = str(m, "coltype");
                item.setColType(colType);
                // P1：补全官方条目字段 extra/content/line（updateItem/deleteItem/findItemsByCls 用）
                item.setContent(str(m, "content"));
                item.setLine(str(m, "line"));
                // 8.83 DetailPage 字段：findRule/group/pages/preRule/rule（Gson 直映，键名与原版一致）
                String findRule = str(m, "findRule");
                if (TextUtils.isEmpty(findRule)) findRule = str(m, "find_rule");
                item.setFindRule(findRule);
                item.setGroup(str(m, "group"));
                item.setPages(str(m, "pages"));
                String preRule = str(m, "preRule");
                if (TextUtils.isEmpty(preRule)) preRule = str(m, "pre_rule");
                item.setPreRule(preRule);
                item.setRule(str(m, "rule"));
                // 8.83 JSEngine.convertToArticleList 的 extra 语义：String 原样 / null 置空 /
                // 其他 JSON.toJSONString——数字等保持 JSON 原始类型，不逐值 String.valueOf。
                Object extraObj = m.get("extra");
                if (extraObj instanceof String) {
                    item.setExtra((String) extraObj);
                } else if (extraObj != null) {
                    item.setExtra(GSON.toJson(extraObj));
                } else {
                    // 兼容：id/cls 等顶层字段也并入 extra，保证按 id/url/cls 匹配可用
                    Map<String, String> extra = new HashMap<>();
                    for (String k : new String[]{"id", "cls", "pageTitle", "newWindow", "lineVisible", "textAlign"}) {
                        String v = str(m, k);
                        if (!v.isEmpty()) extra.put(k, v);
                    }
                    if (!extra.isEmpty()) item.setExtra(GSON.toJson(extra));
                }
                results.add(item);
            }
    }

    /**
     * 海阔首页专用：{@code setHomeResult(res)}，其中 {@code res = {data: [...]}}，
     * data 是条目数组。直接传数组的写法也兼容。
     */
    private void collectHomeResult(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) return;
        try {
            if (args[0] instanceof NativeArray) {
                collectResult(args);
                return;
            }
            String json = args[0] instanceof NativeObject
                    ? jsStringify(args[0])
                    : argStr(args[0]).trim();
            if (json.startsWith("{")) {
                Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> map = GSON.fromJson(json, mapType);
                Object data = map == null ? null : map.get("data");
                if (data instanceof List) {
                    collectResult(new Object[]{GSON.toJson(data)});
                    return;
                }
                Logger.t(TAG).d("setHomeResult: no data array");
                return;
            }
            collectResult(new Object[]{json});
        } catch (Throwable e) {
            Logger.t(TAG).d("collectHomeResult failed: %s", e.getMessage());
        }
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : stringifyValue(v);
    }

    /** 对照原版 fastjson 的 getString：嵌套对象/数组取标准 JSON 文本，不取 Java 的 toString。 */
    private String stringifyValue(Object v) {
        if (v instanceof Map || v instanceof List) return GSON.toJson(v);
        // extra 里嵌套的 JS 对象/数组（如子规则 RULE）是 Rhino 对象，不是 Java Map：
        // 以前 String.valueOf 只剩 "[object Object]"，子页 MY_PARAMS 里整个丢了
        if (v instanceof org.mozilla.javascript.Scriptable) return jsStringify(v);
        return String.valueOf(v);
    }

    private List<HkItem> drainResults() {
        List<HkItem> out = new ArrayList<>(results);
        results.clear();
        return out;
    }

    /**
     * 搜索专用 drain：把收集到的 HkItem 按 8.83 callbackSearchResult 映射为
     * 独立 SearchResult（title/url/desc/img/content/extra，type 硬编码 "video"）。
     * 8.83 extra 语义：String 原样 / null 置 null / 其他 JSON.toJSONString。
     */
    private List<SearchResult> drainSearchResults() {
        List<SearchResult> out = new ArrayList<>(results.size());
        for (HkItem it : results) {
            if (it == null) continue;
            SearchResult sr = new SearchResult();
            sr.setTitle(nz(it.getTitle()));
            sr.setUrl(nz(it.getUrl()));
            sr.setDesc(nz(it.getDesc()));
            sr.setImg(nz(it.getPic()));
            sr.setContent(nz(it.getContent()));
            sr.setType("video");
            if (it.getExtra() != null && !it.getExtra().isEmpty()) {
                // extra 已是 8.83 语义的原始 JSON 串，直接透传，不再二次序列化。
                sr.setExtra(it.getExtra());
            }
            out.add(sr);
        }
        results.clear();
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /**
     * 执行详情的 js: 规则，返回原始条目（含 line/col_type 等扩展字段，M3）。
     * setResult 项约定：title/url/pic_url/desc + 可选 line（线路名）/col_type。
     */
    public List<Map<String, String>> parseDetailRaw(String jsCode, String myUrl) throws Exception {
        final HkRule boundRule = this.rule;
        return submit(() -> {
            if (boundRule != null) this.rule = boundRule;
            rawResults.clear();
            results.clear();
            error = null;
            collectRaw = true;
            try {
                setContext(myUrl);
                evalJs(stripJsPrefix(jsCode));
            } catch (Throwable e) {
                // 详情页 JS 异常也要记录，供 V4 空态展示真实原因（之前只记 log，用户看到"0条线路"无法反馈）
                error = jsErrorMsg(e);
                throw e;
            } finally {
                collectRaw = false;
            }
            List<Map<String, String>> out = new ArrayList<>(rawResults);
            rawResults.clear();
            results.clear();
            return out;
        }).get(EVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** 带当前页规则的入口：先重绑再走原路（详情求值的 MY_RULE 须为当前页规则）。 */
    public List<Map<String, String>> parseDetailRaw(HkRule rule, String jsCode, String myUrl) throws Exception {
        synchronized (ruleEvalLock) {
            bindRule(rule);
            return parseDetailRaw(jsCode, myUrl);
        }
    }

    private String stringifyArg(Object arg) {
        try {
            // 必须是 Scriptable 而非只有 NativeObject：JS 数组是 NativeArray，
            // 漏了它数组会被 String.valueOf 压成逗号串/"[object Object]"存进
            // putMyVar/setItem，下次读回不是数组、结构全烂（UU视频 setTabs
            // 读缓存 tabs 时 data.names 为空、读 [0] 崩，真机堆栈实证）。
            if (arg instanceof org.mozilla.javascript.Scriptable) return jsStringify(arg);
        } catch (Throwable ignored) {
        }
        return String.valueOf(arg);
    }

    /**
     * storage0.get*：存的是 JSON 对象/数组则解析回 JS 对象返回，否则原样返回字符串。
     * 缺省值（第 2 参数）有则原样返回，不做解析。
     */
    private Object storage0Get(java.util.Map<String, String> map, Object[] args) {
        if (args == null || args.length == 0) return "";
        return storage0GetByKey(map, argStr(args[0]), args);
    }

    /**
     * P0：按已算好的键取值的 storage0.get*（供 storage0.getMyVar 用：
     * 键走与全局版相同的 myVarKeyFor 命名空间，表走 myVarsFor，解析语义不变）。
     */
    private Object storage0GetByKey(java.util.Map<String, String> map, String key, Object[] args) {
        String v = map.get(key);
        if (v == null) {
            if (args.length > 1 && args[1] != null) return args[1];
            return "";
        }
        String t = v.trim();
        // 存量双重编码自愈：旧链把已序列化好的 JSON 串又 stringify 一层，存成了
        // "\"[{...}]\" 这种 JSON 字符串字面量。规则读回拿到的是整串文本而不是
        // 数组，setTabs 逐字迭代读 names[0] 直接崩（UU视频真机实证+本地复现命中）。
        // 只解一层包：外层是字符串字面量、内层是对象/数组 JSON 才解。
        if (t.startsWith("\"")) {
            // " 开头 = 旧链双重编码的 JSON 字符串字面量（如 "\"[object Object]\""）。
            // 只接受"内层是合法对象/数组 JSON"这一种自愈并返回解析结果；凡外层解析失败、
            // 内层不是合法对象/数组，一律按未命中处理（回默认值），绝不 return v 把原始串
            // 漏给规则——B 型毒值 "[object Object]" 就是从这里的穿透口漏出去的（UU视频真机实证）。
            Object outer = parseJsonStrict(t);
            if (outer instanceof String) {
                String inner = ((String) outer).trim();
                if ((inner.startsWith("{") && inner.endsWith("}")) || (inner.startsWith("[") && inner.endsWith("]"))) {
                    Object parsedInner = parseJsonStrict(inner);
                    if (parsedInner != null) return parsedInner;
                }
            }
            return args.length > 1 && args[1] != null ? args[1] : "";
        }
        if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) {
            Object parsed = parseJsonStrict(v);
            if (parsed != null) return parsed;
            // 长得像 JSON 却解析不了 = 存量坏缓存（旧版数组被压成 "[object Object]"
            // 那批；parseJson 本身吞异常回空对象，靠它判断不了，必须走严格版）。
            // 当未命中处理，让规则重建，别把垃圾回给规则（UU视频真机实证）。
            return args.length > 1 && args[1] != null ? args[1] : "";
        }
        return v;
    }

    /**
     * N1: 8.83 的 request/postRequest 默认补移动端 UA（JSEngine.smali:22856），
     * post/postPC 补桌面端 UA（post0 经 generateRequestOptions(op,true)，smali:8148/21369），
     * 裸 fetch 不补。若 options 的 headers 里已有 User-Agent（不区分大小写）则不覆盖。
     */
    private static String injectMobileUa(String optionsJson) {
        try {
            com.google.gson.JsonObject obj;
            if (TextUtils.isEmpty(optionsJson)) {
                obj = new com.google.gson.JsonObject();
            } else {
                try {
                    obj = com.google.gson.JsonParser.parseString(optionsJson).getAsJsonObject();
                } catch (Throwable t) {
                    return optionsJson;
                }
            }
            com.google.gson.JsonObject headers = null;
            if (obj.has("headers") && obj.get("headers").isJsonObject()) {
                headers = obj.getAsJsonObject("headers");
            }
            boolean hasUa = false;
            if (headers != null) {
                for (String k : headers.keySet()) {
                    if ("User-Agent".equalsIgnoreCase(k)) {
                        hasUa = true;
                        break;
                    }
                }
            }
            if (!hasUa) {
                if (headers == null) {
                    headers = new com.google.gson.JsonObject();
                    obj.add("headers", headers);
                }
                headers.addProperty("User-Agent", UA_MOBILE);
            }
            return GSON.toJson(obj);
        } catch (Throwable e) {
            return optionsJson;
        }
    }

    private static String mergeMethod(String optionsJson, String method) {
        try {
            // N2: 8.83 用 Map<String,Object>（HttpHelper.smali:2790），保留嵌套的 body/headers 对象。
            // 之前用 Map<String,String> 中转，嵌套对象被 safeString 置空，导致 POST 发空 body（摸鱼日报根因）。
            com.google.gson.JsonObject obj;
            try {
                obj = com.google.gson.JsonParser.parseString(optionsJson).getAsJsonObject();
            } catch (Throwable t) {
                obj = new com.google.gson.JsonObject();
            }
            obj.addProperty("method", method);
            return GSON.toJson(obj);
        } catch (Throwable e) {
            return "{\"method\":\"" + method + "\"}";
        }
    }

    // 8.83 JSEngine.xpath 系列同款：namespaceAware 的 DOM 解析 + XPath 求值
    private static org.w3c.dom.Document parseXmlDoc(String html) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        javax.xml.parsers.DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new java.io.ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private Object xpathArrayImpl(Object[] args) {
        // 8.83 JSEngine.java:2982：失败 throws Exception
        if (args == null || args.length < 2) throw new RuntimeException("xpathArray：参数不足");
        try {
            javax.xml.xpath.XPath xp = javax.xml.xpath.XPathFactory.newInstance().newXPath();
            org.w3c.dom.NodeList nodes = (org.w3c.dom.NodeList) xp.evaluate(
                    argStr(args[1]), parseXmlDoc(argStr(args[0])), javax.xml.xpath.XPathConstants.NODESET);
            List<String> out = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) out.add(nodes.item(i).getNodeValue());
            return toJsArray(out);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException("xpathArray 解析失败：" + e.getMessage());
        }
    }

    // 8.83 HttpHelper.generatePostOps：post/postRequest/postPC 共用——body 是对象转表单串
    // （buildParamStr），是其它非字符串转 JSON 串，method 强制 POST。
    // 普通 fetch 不走这里：fetch 的对象 body 在 fetch0 里是转 JSON 串（见 Req.normalizeBody）。
    private static String applyGeneratePostOps(String optionsJson) {
        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(optionsJson).getAsJsonObject();
            com.google.gson.JsonElement body = obj.get("body");
            if (body != null && !body.isJsonNull()
                    && !(body.isJsonPrimitive() && body.getAsJsonPrimitive().isString())) {
                if (body.isJsonObject()) obj.addProperty("body", buildParamStr(body.getAsJsonObject()));
                else obj.addProperty("body", GSON.toJson(body));
            }
            obj.addProperty("method", "POST");
            return GSON.toJson(obj);
        } catch (Throwable e) {
            return mergeMethod(optionsJson, "post");
        }
    }

    // 8.83 HttpHelper.buildParamStr：key=value 以 & 连接，值是字符串原样、其它转 JSON，不做 URL 编码
    private static String buildParamStr(com.google.gson.JsonObject json) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, com.google.gson.JsonElement> e : json.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=');
            com.google.gson.JsonElement v = e.getValue();
            sb.append(v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()
                    ? v.getAsString() : GSON.toJson(v));
        }
        return sb.toString();
    }

    // 8.83 fetch 开关取值（Map 取值强转 Boolean）：布尔 true 或数字非 0 视为开
    private static boolean optFlag(com.google.gson.JsonObject obj, String key) {
        if (obj == null) return false;
        com.google.gson.JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive()) return false;
        if (e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
        if (e.getAsJsonPrimitive().isNumber()) return e.getAsInt() != 0;
        return false;
    }

    // 8.83 返回包装（HttpHelper:349-356）：五字段 body/headers/url/statusCode/error，headers 为多值表
    private static String wrapFetch(String body, Map<String, List<String>> headers, String realUrl, int statusCode, String error) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("body", body);
        out.put("headers", headers);
        out.put("url", realUrl);
        out.put("statusCode", statusCode);
        out.put("error", error);
        return GSON.toJson(out);
    }

    /**
     * 同步 fetch（跑在 JS 单线程上，直接阻塞等待）。
     * options 为 JSON：{headers, body/data, method, timeout, withHeaders}。
     */
    private String fetchSync(String url, String optionsJson) {
        if (TextUtils.isEmpty(url)) return "";
        // hiker://empty 等非 http 协议直接返回空（由上层按形态分流）
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "";
        // UA 口径：不无条件补（8.83 裸 fetch 亦不补、无条件补移动 UA 的旧做法已撤），
        // 但规则自己声明了 ua 时按声明补——见下方注入段。
        boolean withHeaders = false;
        boolean withStatusCode = false;
        boolean onlyHeaders = false;
        try {
            // 开关一律解析 options JSON 取值；此前用子串扫整个 options 字符串，
            // 正文里含同名 JSON 键会被误判进包装分支
            com.google.gson.JsonObject optObj = null;
            try {
                if (!TextUtils.isEmpty(optionsJson)) {
                    com.google.gson.JsonElement parsedOpt = com.google.gson.JsonParser.parseString(optionsJson);
                    if (parsedOpt != null && parsedOpt.isJsonObject()) optObj = parsedOpt.getAsJsonObject();
                }
            } catch (Throwable ignored) {
            }
            // 规则声明了 ua 且 options 没自带 User-Agent 时，按规则声明补上。
            // 站点常按 UA 分模板：漫小肆声明 mobile，其章节页只有移动模板里
            // 有 #cp_img 容器，桌面/okhttp UA 拿到的是另一套模板、选择器取空
            // → pics:// 空体、漫画零图（本机实测对照：移动 UA 取 262 图，
            // 桌面与 okhttp UA 取 0 图；pdfa 用本仓 Parser 实跑无误）。
            // 未声明 ua 的规则不碰，保持原行为；页面直取那条路（HkEngine/
            // HkRouter）早就用 resolvedUa，JS fetch 此前是唯一漏掉的一条。
            HkRule boundRule = rule;
            if (boundRule != null && !TextUtils.isEmpty(boundRule.getUa())) {
                if (optObj == null) optObj = new com.google.gson.JsonObject();
                com.google.gson.JsonObject hdrs = null;
                if (optObj.has("headers") && optObj.get("headers").isJsonObject()) {
                    hdrs = optObj.getAsJsonObject("headers");
                } else if (optObj.has("header") && optObj.get("header").isJsonObject()) {
                    hdrs = optObj.getAsJsonObject("header");
                }
                boolean hasUa = false;
                if (hdrs != null) {
                    for (String k : hdrs.keySet()) {
                        if ("User-Agent".equalsIgnoreCase(k)) {
                            hasUa = true;
                            break;
                        }
                    }
                }
                if (!hasUa) {
                    if (hdrs == null) {
                        hdrs = new com.google.gson.JsonObject();
                        optObj.add("headers", hdrs);
                    }
                    hdrs.addProperty("User-Agent", boundRule.resolvedUa());
                    optionsJson = GSON.toJson(optObj);
                }
            }
            Req req = TextUtils.isEmpty(optionsJson) ? Req.objectFrom("{}") : Req.objectFrom(optionsJson);
            withHeaders = optFlag(optObj, "withHeaders");
            withStatusCode = optFlag(optObj, "withStatusCode");
            boolean toHex = optFlag(optObj, "toHex");
            onlyHeaders = optFlag(optObj, "onlyHeaders");
            if (onlyHeaders) withHeaders = true;
            String method = req.getMethod();
            if (method.equalsIgnoreCase("head") || method.equalsIgnoreCase("header")) withHeaders = true;
            try (Response res = HkHttp.to(url, req).execute()) {
                resCode = res.code();
                lastUrl = url;
                if (res.code() < 200 || res.code() >= 300) {
                    recordFetchDiag(url, res.code(), null, false);
                }
                Map<String, List<String>> headers = res.headers().toMultimap();
                String realUrl = res.request().url().toString();
                if (onlyHeaders) {
                    return wrapFetch(null, headers, realUrl, res.code(), null);
                }
                ResponseBody body = res.body();
                // 8.83：Content-Length 超 20MB 直接抛错走失败路径，不整包读进内存
                if (body != null && body.contentLength() > 20L * 1024 * 1024) throw new java.io.IOException("response too large");
                byte[] bytes = body == null ? new byte[0] : body.bytes();
                if (bytes.length == 0 && res.code() >= 200 && res.code() < 300) {
                    // 诊断：200 但空 body（JSON.parse("") 抛 Empty JSON 的源头）
                    recordFetchDiag(url, res.code(), null, true);
                }
                String content;
                if (toHex) {
                    StringBuilder sb = new StringBuilder(bytes.length * 2);
                    for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
                    content = sb.toString();
                } else {
                    // N4: 8.83 的 rejectCoding 是请求侧标志（POST 时 body 原样发送，跳过编码处理，
                    // 见 HkHttpHelper.buildPostBody），不是响应侧。响应侧一律按 charset 解码，
                    // 8.83 没有 ISO-8859-1 字节映射行为。
                    content = new String(bytes, req.getCharset());
                }
                if (res.code() < 200 || res.code() >= 300) {
                    // 非 2xx 定案诊断：把实际发出的 UA、带的 cookie 数、路径形状与
                    // 错误页正文头记进文件日志（福建兄妹播放页 403：同 App 里 XO 能播、
                    // 就它不行，日志里没这些就永远定不了是站方拦法还是地址拼错）。
                    try {
                        String ua = res.request().header("User-Agent");
                        String cookie = res.request().header("Cookie");
                        int cookieCount = 0;
                        if (cookie != null && !cookie.isEmpty()) cookieCount = cookie.split(";").length;
                        String head = content == null ? "" : content.replaceAll("\\s+", " ").trim();
                        if (head.length() > 120) head = head.substring(0, 120);
                        com.github.catvod.crawler.SpiderDebug.log("hk",
                                "fetch non2xx host=%s code=%d ua=%s cookies=%d path=%s body=%s",
                                hostOfQuiet(url), res.code(), ua == null ? "" : ua, cookieCount,
                                maskedPath(url), head);
                    } catch (Throwable ignored) {
                    }
                }
                if (withHeaders || withStatusCode) return wrapFetch(content, headers, realUrl, res.code(), null);
                return content;
            }
        } catch (Throwable e) {
            Logger.t(TAG).d("fetch failed %s: %s", url, e.getMessage());
            recordFetchDiag(url, 0, e, false);
            resCode = 0;
            // 8.83：要求了包装时失败也要回带 error 的包装 JSON，读 .error 的规则才能感知失败原因；
            // 此前一律返回 ""，规则只会撞上 Empty JSON
            if (withHeaders || withStatusCode || onlyHeaders) {
                return wrapFetch(null, new HashMap<>(), url, 0, e.getMessage());
            }
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
            if (map != null) {
                kv.putAll(map);
                // 一次性毒值自检（每个规则只跑一次，记号落盘）：只摘"认死了"的坏值，
                // 逐条留底备份，绝不删整规则数据、不碰小程序本体。
                sanitizeKvPoisonOnce();
            }
        } catch (Throwable ignored) {
        }
    }

    /** 认死了的毒值形状（历史写 bug 的产物，当前写路径已修好、只会是存量）： */
    private static boolean isKvPoison(String v) {
        if (v == null) return false;
        // ① BOM 前缀：trim() 去不掉，1.3.26~1.3.28 的清残全看不见它
        if (v.startsWith("\uFEFF")) return true;
        // ② Java toString 残留：1.3.21 前 stringifyArg 不认 NativeArray，数组走 String.valueOf 写坏的
        if (v.startsWith("org.mozilla.javascript.") && v.contains("@")) return true;
        // ③ 双重编码垃圾：现行 stringifyArg 存字符串从不带引号，" 开头的值不可能是合法写入
        String t = v.trim();
        return t.startsWith("\"");
    }

    /** 一次性毒值清理：只摘坏值、逐条备份、留记号。 */
    private void sanitizeKvPoisonOnce() {
        try {
            File dir = kvFile().getParentFile();
            if (dir == null) return;
            File marker = new File(dir, "kv-poison-cleaned.marker");
            if (marker.exists()) return;
            Map<String, String> removed = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : kv.entrySet()) {
                if (isKvPoison(e.getValue())) removed.put(e.getKey(), e.getValue());
            }
            if (!removed.isEmpty()) {
                // 先留底再摘：按规则名/键名备份被清的值，证据可查
                File bakDir = new File(dir, "kv-poison-backup");
                bakDir.mkdirs();
                String ts = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                        .format(new java.util.Date());
                Map<String, Object> bak = new LinkedHashMap<>();
                String title = rule != null ? rule.getTitle() : "";
                bak.put("rule", title);
                bak.put("time", ts);
                bak.put("removed", removed);
                java.nio.file.Files.write(new File(bakDir, "backup-" + ts + ".json").toPath(),
                        GSON.toJson(bak).getBytes("UTF-8"));
                for (String k : removed.keySet()) kv.remove(k);
                saveKv();
            }
            marker.createNewFile();
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
// ============ D1: 8.83 JSEngine P0 core port ============
// 8.83 JSEngine.smali P0 (D1): eval core / script wrap / require / parse entries /
// network bottom + infra helpers. Faithful smali->Java ports; substitutions documented
// in workspace/jsengine-d1/*_notes.md. New methods have no live callers yet (D3 wires them).

    // ---- D1 nested types (8.83 JSEngine$*) ----
    /** 8.83 JSEngine$ReturnType (enum, $values order). */
    public enum ReturnType { VOID, STRING, JSON, BOOL, Num, OBJECT }

    /** 8.83 JSEngine$JSAnnotation. */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    public @interface JSAnnotation {
        String alias() default "";
        ReturnType returnType() default ReturnType.VOID;
    }

    /** 8.83 JSEngine$Parameter. */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.PARAMETER)
    public @interface Parameter {
        int defaultInt() default -1;
        String defaultValue() default "";
        String value() default "";
    }

    /** 8.83 JSEngine$OnFindCallBack mirror. */
    private interface JSEngineCallback<T> {
        void onSuccess(String key, T result);
        void onUpdate(String key, String value);
        void showErr(String err);
    }

    /** 8.83 NoSecurityController (rhino/NoSecurityController.smali, 42 lines). */
    private static class HkNoSecurityController extends SecurityController {
        @Override
        public GeneratedClassLoader createClassLoader(ClassLoader parent, Object securityDomain) {
            return new GeneratedClassLoader() {
                private final HkDefiningLoader loader = new HkDefiningLoader(parent);
                @Override public Class<?> defineClass(String name, byte[] data) {
                    return loader.define(name, data);
                }
                @Override public void linkClass(Class<?> cl) {
                    loader.link(cl);
                }
            };
        }
        @Override public Object getDynamicSecurityDomain(Object securityDomain) { return null; }
    }

    /**
     * ClassLoader 桥：Rhino 的 GeneratedClassLoader 是 interface，没有可 super
     * 调用的 defineClass(String, byte[], int, int)，故用具名 ClassLoader 子类
     * 承载真正的 defineClass/resolveClass。
     */
    private static class HkDefiningLoader extends ClassLoader {
        HkDefiningLoader(ClassLoader parent) { super(parent); }
        Class<?> define(String name, byte[] data) { return super.defineClass(name, data, 0, data.length); }
        void link(Class<?> cl) { super.resolveClass(cl); }
    }

    /** 8.83 JSEngine$MyRequire: Rhino Require with function name "requirejs". */
    private static class MyRequire extends org.mozilla.javascript.commonjs.module.Require {
        MyRequire(Context cx, Scriptable scope,
                  org.mozilla.javascript.commonjs.module.ModuleScriptProvider provider,
                  Script preExec, Script postExec, boolean sandboxed) {
            super(cx, scope, provider, preExec, postExec, sandboxed);
        }
        @Override
        public String getFunctionName() {
            return "requirejs";
        }
    }

    /** Minimal page-rule model for hiker://page/ JSON (GSON). 8.83: ArticleListPageRule. */
    private static class HkPageRule {
        String rule;
        String getRule() { return rule; }
    }

    /** 8.83 RequireDescription model (GSON). */
    private static class RequireDescription {
        String url; int version;
        RequireDescription(String url, int version) { this.url = url; this.version = version; }
        int getVersion() { return version; }
    }

    /** 8.83 RequireItem model (GSON). */
    private static class RequireItem {
        String url, proxy, file; long accessTime;
        RequireItem(String url, String proxy, String file, long accessTime) {
            this.url = url; this.proxy = proxy; this.file = file; this.accessTime = accessTime;
        }
        String getUrl() { return url; }
        long getAccessTime() { return accessTime; }
        void setAccessTime(long t) { accessTime = t; }
        void setProxy(String p) { proxy = p; }
    }


    // ---- D1 fields (8.83 JSEngine state) ----
    private final Map<String, Object> globalMap = new HashMap<>();
    private final HkRc4 rc4 = new HkRc4();
    private final HkMyBase64 myBase64 = new HkMyBase64();
    private final Map<String, JSEngineCallback<?>> callbackMap = new HashMap<>();
    private final Map<String, String> resCodeMap = new HashMap<>();
    private final List<String> methodList = new ArrayList<>();
    private volatile StrongCachingModuleScriptProvider cachingModuleScriptProvider;
    private String allFunctions = "";
    private final Class<?> clazz = HkJsRuntime.class;
    private static final Map<String, String> ruleTicketMap = new HashMap<>();
    private static final Map<String, String> ticketMap = new HashMap<>();


    // ---- D1 small helpers (merger-written) ----
    /** 8.83 RhinoAndroidHelper.enterContext() equivalent: NoSecurityController + Context.enter(). */
    private static Context enterRhino() {
        if (!SecurityController.hasGlobal()) {
            SecurityController.initGlobal(new HkNoSecurityController());
        }
        Context cx = Context.enter();
        // 8.83 initRhino 同款：语言版本 200
        cx.setLanguageVersion(200);
        return cx;
    }

    /** 8.83 UriUtils.getRootDir(ctx): getExternalFilesDir(DOCUMENTS), mkdirs. */
    private static String hkRootDir() {
        File docs = App.get().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (docs != null && !docs.exists()) docs.mkdirs();
        return docs != null ? docs.getAbsolutePath() : "";
    }

    /** 8.83 JSEngine.getFilesDir(): rootDir + "/files" (smali 3563-3621). */
    private String getFilesDir() {
        return hkRootDir() + File.separator + "files";
    }

    /** 8.83 UUIDUtil.genUUID(): dashed-stripped UUID (verified in smali). */
    private static String genUuid() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    /**
     * 8.83 每次求值的 CALLBACK_KEY 装配（JSEngine source 213/233/245/264）：
     * fresh UUID → 登记回调表与正文表 → 返回 uuid，调用方把 getMyCallbackKey(uuid) 拼进脚本文本。
     * resBody 为本次求值对应的响应正文（列表/搜索路=预抓正文，input 回调路=输入串），
     * 规则 getResCode()/getCode() 凭 CALLBACK_KEY 查表即得。
     */
    private String armCallbackKey(String resBody) {
        String uuid = genUuid();
        callbackMap.put(uuid, callbackKeySink());
        if (resBody != null) resCodeMap.put(uuid, resBody);
        return uuid;
    }

    /** 与无回调时的宿主 Toast 回退同行为（setErrorImpl 原有分支），仅保证键存在、可按原版移除。 */
    private JSEngineCallback<?> callbackKeySink() {
        return new JSEngineCallback<Object>() {
            @Override public void onSuccess(String key, Object result) {}
            @Override public void onUpdate(String key, String value) {}
            @Override public void showErr(String err) {
                try {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            ToastUtil.show(App.get(), err == null ? "" : err,
                                    android.widget.Toast.LENGTH_LONG));
                } catch (Throwable ignored) {}
            }
        };
    }

    /** 8.83 JSEngine.generateTicket (smali 2998-3045): title -> stable ticket uuid. */
    private static String generateTicket(String title) {
        if (TextUtils.isEmpty(title)) return title;
        String t = ruleTicketMap.get(title);
        if (!TextUtils.isEmpty(t)) return t;
        String uuid = genUuid();
        ticketMap.put(uuid, title);
        ruleTicketMap.put(title, uuid);
        return uuid;
    }

    /** 8.83 JSEngine.log(Object, Object) JS API (D3 ports full version; D1: log it). */
    private void log(Object msg, Object tag) {
        Logger.t(TAG).d(String.valueOf(msg));
    }

    /** 8.83 4-arg setError (smali 24004, 530 lines). D2: 完整实现，委托 setErrorImpl。 */
    private void setError(Object o, Object callbackKey, Object ruleKey, Object extra) {
        setErrorImpl(new Object[]{o, callbackKey, ruleKey, extra});
    }

    private static InputStream hkFileToInputStream(String path) throws java.io.IOException {
        return new java.io.FileInputStream(path);
    }

    private static byte[] hkFileToBytes(String path) throws java.io.IOException {
        java.io.File f = new java.io.File(path);
        byte[] b = new byte[(int) f.length()];
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
        }
        return b;
    }

    /** hiker://assets/ -> app assets (8.83 HikerRuleUtil.getAssetsFileByHiker; D1 mapping). */
    private String hkGetAssetsFile(String url) {
        try {
            String p = url.substring("hiker://assets/".length());
            return loadAsset(p);
        } catch (Exception e) {
            return "";
        }
    }

    /** hiker:// rule URLs (8.83 HikerRuleUtil.getRulesByHiker)：hiker://home@<规则标题> → 按标题查规则库返回规则 JSON。 */
    private String hkGetRulesByHiker(String url) {
        try {
            if (TextUtils.isEmpty(url) || !url.startsWith("hiker://")) return "";
            String p0 = url.replace("hiker://", "");
            if (p0.startsWith("home")) {
                // 8.83: split("@") 取 parts[1] 为标题查库；宿主用 HkRuleManager.loadRuleJson 同款语义
                String[] parts = p0.split("@");
                if (parts.length > 1) {
                    String json = HkRuleManager.get().loadRuleJson(parts[1]);
                    return json == null ? "" : json;
                }
                return "";
            }
            // 8.83 其余分支（bookmark 等）为 D3 范畴，暂返回 ""
            return "";
        } catch (Exception e) {
            Logger.t(TAG).e(e, "hkGetRulesByHiker");
            return "";
        }
    }


    // ---- D1-A: eval core (smali 13914-15069, 26009-26128) ----
    // (ReturnType/JSAnnotation/Parameter defined once as nested types above.)

// 1. evalJS(String, InputStream) — smali lines 13914-14288.
// Runs the script in a FRESH Rhino context/scope, injecting input/_globalMap/rc4/
// _base64/javaContext/javaLoader plus the allFunctions prelude; returns the eval
// result as InputStream (unwrapping NativeJavaObject), closing the passed-in
// stream when the result is a different InputStream. Any Exception -> Context.exit
// and return the original stream; any Throwable -> Context.exit and rethrow.
public InputStream evalJS(String p1, InputStream p2) {
    String v1;
    if (p1.contains("let my_rule = '")) {
        v1 = "";
    } else {
        v1 = getMyRule(null); // MISSING (8.83 static)
    }
    String script = new StringBuilder()
            .append(v1)
            .append(getMyType("eval")) // MISSING
            .append(getMyCallbackKey(genUuid())) // MISSING; 8.83 external
            .append(p1)
            .toString();
    currentMyType = "eval";
    String v3 = getReplaceJS(appendLineVar(script)); // MISSING, MISSING
    Context v1cx = Context.enter(); // 8.83 RhinoAndroidHelper 等价：Context.enter()
    initRhino(v1cx); // MISSING
    try {
        ScriptableObject v2 = v1cx.initStandardObjects();
        org.mozilla.javascript.ImporterTopLevel.init(v1cx, v2, false);
        initRequire(v1cx, v2); // MISSING
        installJavaAdapterShim(v2);
        ScriptableObject.putProperty(v2, "input", Context.javaToJS(p2, v2));
        ScriptableObject.putProperty(v2, "_globalMap", Context.javaToJS(globalMap, v2)); // field MISSING
        ScriptableObject.putProperty(v2, "globalMap0", Context.javaToJS(globalMap, v2)); // 8.83 _globalMap 别名（skill 文档名）
        ScriptableObject.putProperty(v2, "rc4", Context.javaToJS(rc4, v2)); // field MISSING
        ScriptableObject.putProperty(v2, "_base64", Context.javaToJS(myBase64, v2)); // field MISSING
        ScriptableObject.putProperty(v2, "javaContext", Context.javaToJS(this, v2));
        ScriptableObject.putProperty(v2, "javaLoader", Context.javaToJS(clazz.getClassLoader(), v2)); // field MISSING
        registerApiInto(v2);
        if (!v3.contains("eval(getJsPlugin());\n")) {
            v1cx.evaluateString(v2, allFunctions + "\neval(getJsPlugin());\n", clazz.getSimpleName(), 1, null); // field MISSING
        } else {
            v1cx.evaluateString(v2, allFunctions, clazz.getSimpleName(), 1, null);
        }
        Object p1res = v1cx.evaluateString(v2, v3, clazz.getSimpleName(), 1, null);
        if (p1res instanceof InputStream) {
            if (p1res != p2 && p2 != null) {
                try {
                    p2.close();
                } catch (Exception e) {
                    Logger.t(TAG).e(e.getMessage()); // 8.83 external; target uses Logger
                }
            }
            InputStream result = (InputStream) p1res;
            Context.exit();
            return result;
        }
        if (p1res instanceof NativeJavaObject) {
            p1res = ((NativeJavaObject) p1res).unwrap();
            if (p1res instanceof InputStream && p1res != p2 && p2 != null) {
                try {
                    p2.close();
                } catch (Exception e) {
                    Logger.t(TAG).e(e.getMessage()); // 8.83 external; target uses Logger
                }
            }
            if (p1res instanceof InputStream) {
                p2 = (InputStream) p1res;
            }
            Context.exit();
            return p2;
        }
        Context.exit();
        return p2;
    } catch (Exception e) {
        Context.exit();
        return p2;
    } catch (Throwable t) {
        // smali :catchall_0 — exit then rethrow (NOT a finally: normal paths exit explicitly)
        Context.exit();
        throw t;
    }
}

// 2. evalJS(String, String) — smali lines 14289-14301. Delegates with decode=true.
public String evalJS(String p1, String p2) {
    return evalJS(p1, p2, true);
}

// 3. evalJS(String, String, boolean) — smali lines 14302-14735.
// Same fresh-context shape as (1) but injects `var input=...` (getMyInput) instead of
// the "input" property; when p3 is true the script is passed through
// StringUtil.decodeConflictStr. Result mapping: String -> itself;
// Undefined -> "undefined"; NativeObject/NativeArray -> argsNativeObjectAdjust +
// JSON stringify; anything else -> String.valueOf. Exception -> "error:"+message;
// Throwable -> Context.exit and rethrow.
public String evalJS(String p1, String p2, boolean p3) {
    String errPrefix = "error:";
    String empty = "";
    String v4;
    if (p1.contains("let my_rule = '")) {
        v4 = empty;
    } else {
        v4 = getMyRule(null); // MISSING (8.83 static)
    }
    String v4b;
    {
        // 8.83 evalJS 同款：每次求值 fresh CALLBACK_KEY；登记回调表与正文表（正文=输入串 p2），
        // 脚本文本注入 var CALLBACK_KEY（原先只注入未登记，getResCode 查表 miss）。
        String cbUuid = genUuid();
        callbackMap.put(cbUuid, callbackKeySink());
        if (p2 != null) resCodeMap.put(cbUuid, p2);
        v4b = new StringBuilder()
                .append(getMyInput(p2))
                .append(v4)
                .append(getMyType("eval"))
                .append(getMyCallbackKey(cbUuid))
                .toString();
        currentMyType = "eval";
    }
    String v0;
    if (p3) {
        v0 = v4b + decodeConflictStr(p1); // 8.83 external
    } else {
        v0 = v4b + p1;
    }
    String v8 = getReplaceJS(appendLineVar(v0)); // MISSING, MISSING
    Context v6 = Context.enter(); // 8.83 RhinoAndroidHelper 等价：Context.enter()
    initRhino(v6); // MISSING
    try {
        ScriptableObject v7 = v6.initStandardObjects();
        org.mozilla.javascript.ImporterTopLevel.init(v6, v7, false);
        initRequire(v6, v7); // MISSING
        installJavaAdapterShim(v7);
        ScriptableObject.putProperty(v7, "_globalMap", Context.javaToJS(globalMap, v7)); // field MISSING
        ScriptableObject.putProperty(v7, "globalMap0", Context.javaToJS(globalMap, v7)); // 8.83 _globalMap 别名
        ScriptableObject.putProperty(v7, "rc4", Context.javaToJS(rc4, v7)); // field MISSING
        ScriptableObject.putProperty(v7, "_base64", Context.javaToJS(myBase64, v7)); // field MISSING
        ScriptableObject.putProperty(v7, "javaContext", Context.javaToJS(this, v7));
        ScriptableObject.putProperty(v7, "javaLoader", Context.javaToJS(clazz.getClassLoader(), v7)); // field MISSING
        registerApiInto(v7);
        if (!v8.contains("eval(getJsPlugin());\n")) {
            v6.evaluateString(v7, allFunctions + "\neval(getJsPlugin());\n", clazz.getSimpleName(), 1, null); // field MISSING
        } else {
            v6.evaluateString(v7, allFunctions, clazz.getSimpleName(), 1, null);
        }
        Object v0res = v6.evaluateString(v7, v8, clazz.getSimpleName(), 1, null);
        if (v0res instanceof String) {
            Context.exit();
            return (String) v0res;
        }
        if (v0res instanceof Undefined) {
            Context.exit();
            return "undefined";
        }
        if (v0res instanceof NativeObject || v0res instanceof NativeArray) {
            Object adjusted = argsNativeObjectAdjust(v0res); // exists in HkJsRuntime
            // 8.83: GSON.toJson(adjusted); fastjson absent in
            // target — argsNativeObjectAdjust yields LinkedHashMap/ArrayList, so GSON is equivalent.
            String json = GSON.toJson(adjusted);
            Context.exit();
            return json;
        }
        String s = new StringBuilder().append(v0res).append(empty).toString();
        Context.exit();
        return s;
    } catch (Exception e) {
        logEvalError("evalJS", v8, e);
        String err = errPrefix + e.getMessage();
        Context.exit();
        return err;
    } catch (Throwable t) {
        // smali :catchall_0 — exit then rethrow (NOT a finally: normal paths exit explicitly)
        Context.exit();
        throw t;
    }
}

/** 求值失败落盘日志：带行号与出错行原文，供导出调试日志定位（原先异常只回字符串不落盘，无从查起）。 */
private static void logEvalError(String where, String script, Throwable e) {
    try {
        int line = -1;
        if (e instanceof org.mozilla.javascript.RhinoException) {
            line = ((org.mozilla.javascript.RhinoException) e).lineNumber();
        }
        String excerpt = "";
        if (script != null && line > 0) {
            String[] ls = script.split("\n", -1);
            if (line <= ls.length) excerpt = ls[line - 1].trim();
            if (excerpt.length() > 200) excerpt = excerpt.substring(0, 200);
        }
        Logger.t(TAG).e("eval fail [%s] line=%d >> %s | %s", where, line, excerpt, String.valueOf(e));
    } catch (Throwable ignored) {
    }
}

// 4. evalJSForObj(String, Object) — smali lines 14736-15032.
// Same fresh-context shape as (1) ("input" property injected, NOT getMyInput).
// Returns the raw eval result after argsNativeObjectAdjust (+ NativeJavaObject
// unwrap); any Exception -> Context.exit and return null; Throwable -> exit, rethrow.
public Object evalJSForObj(String p1, Object p2) {
    String v1;
    if (p1.contains("let my_rule = '")) {
        v1 = "";
    } else {
        v1 = getMyRule(null); // MISSING (8.83 static)
    }
    String script = new StringBuilder()
            .append(v1)
            .append(getMyType("eval")) // MISSING
            .append(getMyCallbackKey(genUuid())) // MISSING; 8.83 external
            .append(p1)
            .toString();
    currentMyType = "eval";
    String v5 = getReplaceJS(appendLineVar(script)); // MISSING, MISSING
    Context v3 = Context.enter(); // 8.83 RhinoAndroidHelper 等价：Context.enter()
    initRhino(v3); // MISSING
    try {
        ScriptableObject v4 = v3.initStandardObjects();
        org.mozilla.javascript.ImporterTopLevel.init(v3, v4, false);
        initRequire(v3, v4); // MISSING
        installJavaAdapterShim(v4);
        ScriptableObject.putProperty(v4, "input", Context.javaToJS(p2, v4));
        ScriptableObject.putProperty(v4, "_globalMap", Context.javaToJS(globalMap, v4)); // field MISSING
        ScriptableObject.putProperty(v4, "globalMap0", Context.javaToJS(globalMap, v4)); // 8.83 _globalMap 别名
        ScriptableObject.putProperty(v4, "rc4", Context.javaToJS(rc4, v4)); // field MISSING
        ScriptableObject.putProperty(v4, "_base64", Context.javaToJS(myBase64, v4)); // field MISSING
        ScriptableObject.putProperty(v4, "javaContext", Context.javaToJS(this, v4));
        ScriptableObject.putProperty(v4, "javaLoader", Context.javaToJS(clazz.getClassLoader(), v4)); // field MISSING
        registerApiInto(v4);
        if (!v5.contains("eval(getJsPlugin());\n")) {
            v3.evaluateString(v4, allFunctions + "\neval(getJsPlugin());\n", clazz.getSimpleName(), 1, null); // field MISSING
        } else {
            v3.evaluateString(v4, allFunctions, clazz.getSimpleName(), 1, null);
        }
        Object p1res = v3.evaluateString(v4, v5, clazz.getSimpleName(), 1, null);
        p1res = argsNativeObjectAdjust(p1res); // exists in HkJsRuntime
        if (p1res instanceof NativeJavaObject) {
            p1res = ((NativeJavaObject) p1res).unwrap();
        }
        Context.exit();
        return p1res;
    } catch (Exception e) {
        logEvalError("evalJSForObj", v5, e);
        Context.exit();
        return null;
    } catch (Throwable t) {
        // smali :catchall_0 — exit then rethrow (NOT a finally: normal paths exit explicitly)
        Context.exit();
        throw t;
    }
}

// 5. evalLazyRule(Object, String, String) — smali lines 15033-15069.
// Builds getMyRule(p1) + decodeConflictStr(p2), then evalJS(script, p3, false)
// (decode=false: the lazy rule was already decoded).
public String evalLazyRule(Object p1, String p2, String p3) {
    String script = new StringBuilder()
            .append(getMyRule(p1)) // MISSING (8.83 static)
            .append(decodeConflictStr(p2)) // 8.83 external
            .toString();
    return evalJS(script, p3, false);
}

// 6. syncExecute(Object) — smali lines 26009-26128, declared-synchronized.
// JS-bridge entry (8.83 @JSAnnotation(returnType=OBJECT), @Parameter("task")):
// if the arg is null/undefined -> null; else argsNativeObjectAdjust(arg, true)
// (8.83 casts to fastjson JSONObject — adapted to java.util.Map since the target's
// adjuster returns LinkedHashMap). Takes "func" (BaseFunction) and "param" from the
// map and invokes func.call(currentContext, func.getParentScope(), func, [param]).
@JSAnnotation(returnType = ReturnType.OBJECT)
public synchronized Object syncExecute(@Parameter("task") Object task) {
    Object p1 = task;
    if (p1 != null) {
        if (isUndefined(p1)) { // exists in HkJsRuntime; matches 8.83 smali 5714-5738
            p1 = null;
        } else {
            // 8.83: check-cast to com.alibaba.fastjson.JSONObject (fastjson absent in
            // target); the target's two-arg adjuster returns LinkedHashMap, so Map is equivalent.
            // （扫查待裁 22：adjust 结果在此先落 Object，下方 instanceof 定型，不裸强转。）
            p1 = argsNativeObjectAdjust(p1, true); // two-arg exists in HkJsRuntime
        }
    }
    // 扫查待裁 22：安全取值——adjust 结果非 Map、func 缺失/非函数时原裸强转＋
    // 空解引用直接 NPE/CCE（且与本方法头注「null/undefined -> null」契约相悖）；
    // 无可调用函数即按无任务返回 null。
    java.util.Map<?, ?> jo = p1 instanceof java.util.Map ? (java.util.Map<?, ?>) p1 : null;
    BaseFunction v1 = null;
    if (jo != null) {
        Object funcObj = jo.get("func");
        if (funcObj instanceof BaseFunction) v1 = (BaseFunction) funcObj;
    }
    if (v1 == null) return null;
    Object v0 = jo.get("param");
    Scriptable parentScope = v1.getParentScope();
    Context v2 = Context.getCurrentContext();
    return v1.call(v2, parentScope, v1, new Object[]{v0});
}


    // ---- D1-B: script wrap (smali 3622-4574, 9142-9479, 9963-9991) ----


// ---- methods ----------------------------------------------------------------

/**
 * Port of JSEngine.getFunctionStr(String, JSEngine$ReturnType, String, Method).
 * Smali lines 3622-4574. Builds the JS injection template for one ScriptAPI
 * method: " var method_<name> = ScriptAPI.getMethod(\"<name>\"[,types]);" plus
 * " function <name>(param0,...){ ... }", honouring @Parameter annotations
 * (ruleTitleKey/evalKey/dirKey/typeKey/urlKey/ruleKey/callbackKey, plus
 * defaultValue/defaultInt fallbacks). All const-string literals reproduced
 * character-for-character from the smali.
 */
private String getFunctionStr(String prefix, ReturnType type, String alias, java.lang.reflect.Method method) {
    String name = method.getName();
    Class<?>[] paramTypes = method.getParameterTypes();
    String types;
    String invokeArgs;
    String paramNames;
    if (paramTypes.length > 0) {
        String[] typeNames = new String[paramTypes.length];
        String[] names = new String[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            typeNames[i] = paramTypes[i].getName();
            names[i] = "param" + i;
        }
        types = String.format(",[%s]", TextUtils.join(",", typeNames));
        paramNames = TextUtils.join(",", names);
        invokeArgs = "," + paramNames;
    } else {
        types = "";
        invokeArgs = "";
        paramNames = "";
    }
    StringBuilder sb = new StringBuilder();
    java.lang.annotation.Annotation[][] paramAnnotations = method.getParameterAnnotations();
    for (int i = 0; i < paramAnnotations.length; i++) {
        for (java.lang.annotation.Annotation annotation : paramAnnotations[i]) {
            if (Parameter.class.equals(annotation.annotationType())) {
                Parameter param = (Parameter) annotation;
                String value = param.value();
                int which = -1;
                switch (value) {
                    case "ruleTitleKey":
                        which = 0;
                        break;
                    case "evalKey":
                        which = 1;
                        break;
                    case "dirKey":
                        which = 2;
                        break;
                    case "typeKey":
                        which = 3;
                        break;
                    case "urlKey":
                        which = 4;
                        break;
                    case "ruleKey":
                        which = 5;
                        break;
                    case "callbackKey":
                        which = 6;
                        break;
                }
                switch (which) {
                    case 0:
                        sb.append("param").append(i).append(" = MY_TICKET;\n");
                        break;
                    case 1:
                        sb.append("param").append(i).append(" = eval;\n");
                        break;
                    case 2:
                        sb.append("param").append(i).append(" = param").append(i).append(" || MY_DIR;\n");
                        break;
                    case 3:
                        sb.append("param").append(i).append(" = MY_TYPE;\n");
                        break;
                    case 4:
                        sb.append("param").append(i).append(" = param").append(i).append(" || MY_URL;\n");
                        break;
                    case 5:
                        sb.append("param").append(i).append(" = MY_RULE;\n");
                        break;
                    case 6:
                        sb.append("param").append(i).append(" = CALLBACK_KEY;\n");
                        break;
                }
                String defaultValue = param.defaultValue();
                if (!TextUtils.isEmpty(defaultValue)) {
                    sb.append("param").append(i).append(" = param").append(i)
                            .append(" || \"").append(escapeJsString(defaultValue)).append("\";\n");
                }
                int defaultInt = param.defaultInt();
                if (defaultInt >= 0) {
                    sb.append("param").append(i).append(" = param").append(i)
                            .append(" != null ? param").append(i).append(" : ").append(defaultInt).append(";\n");
                }
            }
        }
    }
    String retPrefix = method.getReturnType().getSimpleName().equals("void") ? "" : "return";
    String methodDecl = String.format(" var method_%s = ScriptAPI.getMethod(\"%s\"%s);\n", name, name, types);
    String functionStr;
    if (type == ReturnType.STRING) {
        functionStr = String.format(new StringBuilder(" function %s(%s){\n")
                .append(sb.toString())
                .append("    var retStr = method_%s.invoke(javaContext%s);\n"
                        + "    try {\n"
                        + "       if(retStr instanceof java.io.InputStream) return retStr;\n"
                        + "    } catch(e){}\n"
                        + "    return retStr == null ? retStr : retStr + '';\n"
                        + " }\n")
                .toString(), name, paramNames, name, invokeArgs);
    } else if (type == ReturnType.JSON) {
        functionStr = String.format(new StringBuilder(" function %s(%s){\n")
                .append(sb.toString())
                .append("    var retStr = method_%s.invoke(javaContext%s);\n"
                        + "    return JSON.parse(retStr);\n"
                        + " }\n")
                .toString(), name, paramNames, name, invokeArgs);
    } else if (type == ReturnType.OBJECT) {
        functionStr = String.format(new StringBuilder(" function %s(%s){\n")
                .append(sb.toString())
                .append("    return method_%s.invoke(javaContext%s);\n"
                        + " }\n")
                .toString(), name, paramNames, name, invokeArgs);
    } else if (type == ReturnType.BOOL) {
        functionStr = String.format(new StringBuilder(" function %s(%s){\n")
                .append(sb.toString())
                .append("    var retStr = method_%s.invoke(javaContext%s);\n"
                        + "    return retStr + '' == 'true';\n"
                        + " }\n")
                .toString(), name, paramNames, name, invokeArgs);
    } else if (type == ReturnType.Num) {
        functionStr = String.format(new StringBuilder(" function %s(%s){\n")
                .append(sb.toString())
                .append("    var retStr = method_%s.invoke(javaContext%s);\n"
                        + "    return parseInt(retStr);\n"
                        + " }\n")
                .toString(), name, paramNames, name, invokeArgs);
    } else {
        functionStr = String.format(new StringBuilder(" function %s(%s){\n")
                .append(sb.toString())
                .append("    %s method_%s.invoke(javaContext%s);\n"
                        + " }\n")
                .toString(), name, paramNames, retPrefix, name, invokeArgs);
    }
    String result = prefix + methodDecl + functionStr;
    methodList.add(name);
    if (!TextUtils.isEmpty(alias)) {
        result += String.format("\nvar %s = %s;", alias, name);
        methodList.add(alias);
    }
    return result;
}

/**
 * Port of JSEngine.runScript(String, String). Smali lines 9142-9152.
 * Delegates to the 3-arg overload with throwOnError=false.
 */
private void runScript(String script, String key) {
    runScript(script, key, false);
}

/**
 * Port of JSEngine.runScript(String, String, boolean). Smali lines 9153-9479.
 * Builds the final script via appendLineVar + getReplaceJS, enters a fresh
 * Rhino context (RhinoAndroidHelper), installs the standard 8.83 scope
 * bindings (_globalMap, rc4, _base64, javaContext, javaLoader), evaluates
 * allFunctions (+ "\neval(getJsPlugin());\n" when the script lacks the
 * "eval(getJsPlugin());\n" marker) and then the script itself. On Exception:
 * setError("JS编译出错：" + msg, key, new JSONObject()) unless throwOnError,
 * in which case the exception is rethrown. Context.exit() always runs
 * (smali allcatch -> finally). Afterwards resCodeMap/callbackMap entries for
 * key are removed (each path has its own try/catch -> Timber.e, faithfully
 * collapsed here into the single straight-line cleanup).
 */
private void runScript(String script, String key, boolean throwOnError) {
    String replaced = getReplaceJS(appendLineVar(script), throwOnError);
    Context cx = Context.enter(); // 8.83 RhinoAndroidHelper 等价：Context.enter()
    initRhino(cx);
    try {
        ScriptableObject scope = cx.initStandardObjects();
        org.mozilla.javascript.ImporterTopLevel.init(cx, scope, false);
        initRequire(cx, scope);
        installJavaAdapterShim(scope);
        ScriptableObject.putProperty(scope, "_globalMap",
                Context.javaToJS(globalMap, scope));
        ScriptableObject.putProperty(scope, "globalMap0",
                Context.javaToJS(globalMap, scope)); // 8.83 _globalMap 别名（skill 文档名）
        ScriptableObject.putProperty(scope, "rc4",
                Context.javaToJS(rc4, scope));
        ScriptableObject.putProperty(scope, "_base64",
                Context.javaToJS(myBase64, scope));
        ScriptableObject.putProperty(scope, "javaContext",
                Context.javaToJS(this, scope));
        ScriptableObject.putProperty(scope, "javaLoader",
                Context.javaToJS(clazz.getClassLoader(), scope));
        registerApiInto(scope);
        if (!script.contains("eval(getJsPlugin());\n")) {
            cx.evaluateString(scope, allFunctions + "\neval(getJsPlugin());\n",
                    clazz.getSimpleName(), 1, null);
        } else {
            cx.evaluateString(scope, allFunctions, clazz.getSimpleName(), 1, null);
        }
        cx.evaluateString(scope, replaced, clazz.getSimpleName(), 1, null);
    } catch (Exception e) {
        logEvalError("runScript", replaced, e);
        if (!throwOnError) {
            setError("JS编译出错：" + e.getMessage(), key, new HashMap<String, Object>());
        } else {
            throw e;
        }
    } finally {
        Context.exit();
    }
    try {
        if (!TextUtils.isEmpty(key)) {
            resCodeMap.remove(key);
            callbackMap.remove(key);
        }
    } catch (Exception e) {
        Logger.t(TAG).e(e.getMessage());
    }
}

/**
 * Port of JSEngine.wrapTryScript(String). Smali lines 9963-9991.
 * Returns "try {" + script + "}catch(err){}".
 */
private String wrapTryScript(String script) {
    return new StringBuilder("try {").append(script).append("}catch(err){}").toString();
}


    // ---- D1-C: require (smali 5400-5484, 8192-8237, 8468-9141) ----
    /**
     * 8.83 JSEngine.require0(Object p1, Object p2, int p3, Object p4,
     *                        Object p5, boolean p6, String p7, int p8) — smali 8468-9141.
     * $.require 的核心模块加载器。参数角色按寄存器用法：
     *   p1 options     — 传给 proxyRequire 的选项对象（可含 proxy 字段）
     *   p2 name        — 经 tryGetString 转字符串，作模块名/描述（updateDescription/generateRequireMap 用）
     *   p3 expireHours — 本地缓存过期小时数：<0 只比版本；==0 永远重下；>0 版本不够重下，否则按 3600000*p3 过期
     *   p4 fetchOpts   — 透传给 requestForCode 的请求选项对象
     *   p5 callback    — Function；p6=true 时把代码字符串作唯一实参回调，其 parentScope 作调用 scope
     *   p6 useCallback — true=回调模式（返回 Function.call 结果），false=返回代码字符串
     *   p7 ruleTitle   — 规则标题（hiker://page/ 查库 + generateRequireMap 用）
     *   p8 reqVersion  — 期望依赖版本号（与 <md5>.json 里存的版本比）
     */
    private Object require0(Object options, Object name, int expireHours, Object fetchOpts,
                            Object callback, boolean useCallback, String ruleTitle, int reqVersion) throws Exception {
        // smali: v1=p4(fetchOpts), v2=p7(ruleTitle), v3=tryGetString(p2), v4=p8(reqVersion)
        String desc = tryGetString(name);            // v3, .line 3626
        String url = proxyRequire(options, desc);    // v5, .line 3627

        // .line 3628: isNotEmpty(url) && (startsWith("file://") || startsWith("hiker://")) -> :cond_0
        if (!TextUtils.isEmpty(url) && (url.startsWith("file://") || url.startsWith("hiker://"))) {
            // :cond_0
            Object ruleObj = null;                   // v7
            if (url.startsWith("hiker://page/")) {
                // 8.83: LitePal.where("title = ?", ruleTitle).limit(1).findFirst(ArticleListRule.class)
                ruleObj = HkRuleManager.get().getInstalledRule(ruleTitle);
            }
            // :cond_1
            // 体检批六判型：原版裸 check-cast String；非文本结果（哨兵/流）按原版
            // 收口故意抛 ClassCastException，不静默当代码用。null 仍按原版过转。
            Object rawCode = fetchByHiker(url, ruleObj, false, false);  // v2
            if (rawCode != null && !(rawCode instanceof String)) {
                throw new ClassCastException("require fetchByHiker non-text result: "
                        + rawCode.getClass().getName());
            }
            String code = (String) rawCode;
            if (!useCallback) {
                return code;                         // :cond_4
            }
            if (url.startsWith("hiker://page/")) {
                // 8.83: com.alibaba.fastjson.JSON.parseObject(code, HkPageRule.class)
                HkPageRule pageRule = GSON.fromJson(code, HkPageRule.class);
                if (pageRule != null) {
                    String rule = pageRule.getRule();
                    if (!TextUtils.isEmpty(rule)) {
                        code = rule.startsWith("js:") ? rule.substring(3) : rule;  // v8=3
                    }
                }
            }
            // :goto_0 — Function.call(cx, parentScope, parentScope, new Object[]{code})
            // 体检批六判型：原版裸 check-cast Scriptable/Function；回调模式下 callback
            // 非 Function 按原版收口故意抛 ClassCastException（Function 必是 Scriptable，
            // 一道判型覆盖两转）。
            if (!(callback instanceof Function)) {
                throw new ClassCastException("require callback is not a Function: "
                        + (callback == null ? "null" : callback.getClass().getName()));
            }
            Context cx = Context.getCurrentContext();
            Scriptable parentScope = ((Scriptable) callback).getParentScope();
            return ((Function) callback).call(cx, parentScope, parentScope, new Object[]{code});
        }

        // :cond_5 — 非 file/hiker 协议：必须 http 开头，否则抛
        if (TextUtils.isEmpty(url) || !url.startsWith("http")) {
            // :cond_12
            throw new Exception("require地址必须为http地址：" + url);
        }

        // http(s) 分支：本地缓存 libs/<md5(url)>.js + 版本文件 <md5(url)>.json
        Context cx = Context.getCurrentContext();                          // v6
        // 体检批六判型：parentScope 只在回调模式的 finishRequire 里消费；原版无条件
        // 裸转，非 Scriptable 的 callback 在非回调模式也会 CCE。非 Scriptable 时置
        // null（该模式不消费），回调模式的判型在 finishRequire 处故意抛错收口。
        Scriptable parentScope = callback instanceof Scriptable
                ? ((Scriptable) callback).getParentScope() : null;          // v9
        String md5 = Util.md5(url);                                        // v10, 8.83: StringUtil.md5
        String libsDir = hkRootDir() + File.separator + "libs";  // v12
        String jsPath = libsDir + File.separator + md5 + ".js";            // v11
        String jsonPath = libsDir + File.separator + md5 + ".json";        // v10
        File jsFile = new File(jsPath);                                    // v12
        int savedVer = getRequireVersion(jsonPath, reqVersion); // v13
        // v15 = "获取远程依赖失败："

        // 缓存有效性：smali 3667-3672 的 if-gez/if-ge/if-lt/if-eqz/cmp-long 判定链
        //   p3 < 0：只比版本（savedVer >= reqVersion 用本地，否则下载）
        //   p3 == 0：永远重新下载
        //   p3 > 0：版本不够直接下载；版本够则 age <= 3600000*p3 用本地，否则下载
        boolean useLocal;
        if (!jsFile.exists()) {
            useLocal = false;
        } else if (expireHours < 0) {
            useLocal = savedVer >= reqVersion;
        } else if (expireHours == 0) {
            useLocal = false;
        } else if (savedVer < reqVersion) {
            useLocal = false;
        } else {
            long age = System.currentTimeMillis() - jsFile.lastModified();
            useLocal = age <= 3600000L * expireHours;
        }

        String code;  // v1
        if (useLocal) {
            // :cond_b / :goto_1 — 缓存有效，直接读本地
            code = readFileContent(jsPath);          // 8.83: FileUtil.fileToString(jsPath)
        } else {
            code = requestForCode(url, fetchOpts);   // :cond_7 / :cond_f 下载
            if (!jsFile.exists()) {
                // :cond_f — 本地无文件：无内容校验，直接落盘
                if (TextUtils.isEmpty(code)) {
                    // :cond_11 — 原版抛错；星辰按真机结论软失败（见 registerApi require 处注释：
                    // 裸名取不到整页全灭的回归），记日志回 null。
                    Logger.t(TAG).d("require0 miss (soft): %s", url);
                    return null;
                }
                writeFileContent(jsPath, code);      // 8.83: FileUtil.stringToFile(code, jsPath)
                updateDescription(jsonPath, desc, reqVersion);
            } else if (useCallback && !isJsCode(code)) {
                // :cond_7 回调模式：下载内容被判定为非 JS 代码 → 打日志后回退本地
                log("url: " + url + "获取的内容被判定为非JS代码", null);  // v7=null
                code = readFileContent(jsPath);      // goto :goto_1
            } else if (!useCallback && url.contains("hiker.nokia.press")
                    && (TextUtils.isEmpty(code) || code.length() < 3 || code.contains("非法猎鹰"))) {
                // :cond_8 — nokia.press 拦截页（空/过短/含"非法猎鹰"）→ 回退本地
                code = readFileContent(jsPath);      // goto :goto_1
            } else if (giteePoisoned(url, code)) {
                // :cond_9 + :cond_a — gitee 坏内容 → 回退本地
                code = readFileContent(jsPath);      // goto :goto_1
            } else if (TextUtils.isEmpty(code)) {
                // :cond_c — 空内容：原版抛错，星辰软失败回 null（同上）。
                Logger.t(TAG).d("require0 miss (soft): %s", url);
                return null;
            } else {
                // :cond_c — 内容可用，落盘并更新版本描述
                writeFileContent(jsPath, code);      // 8.83: FileUtil.stringToFile(code, jsPath)
                updateDescription(jsonPath, desc, reqVersion);
            }
        }
        // :goto_2 公共收尾
        return finishRequire(ruleTitle, desc, url, jsPath, code, cx, parentScope, callback, useCallback);
    }

    /**
     * :cond_9 + :cond_a 的 gitee 内容校验（结构辅助方法，非 8.83 独立方法）：
     * url 含 gitee.com/ 且（内容空 / 长度<3 / 含 "The Repository is invisible"）→ true（回退本地）。
     * 不含 gitee.com/ → false（走 :cond_c）。
     */
    private boolean giteePoisoned(String url, String code) {
        if (!url.contains("gitee.com/")) return false;  // :cond_9 !contains -> :cond_a -> !contains -> :cond_c
        if (TextUtils.isEmpty(code)) return true;       // :cond_9 空 -> :goto_1
        if (code.length() < 3) return true;             // :cond_9 长度<3 -> :goto_1（v8=3）
        return code.contains("The Repository is invisible");  // :cond_a
    }

    /**
     * :goto_2 公共收尾（结构辅助方法，非 8.83 独立方法）：
     * generateRequireMap 登记模块；useCallback=true 时调 Function 回调并返回其结果，否则返回代码字符串。
     */
    private Object finishRequire(String ruleTitle, String desc, String url, String jsPath, String code,
                                 Context cx, Scriptable parentScope, Object callback, boolean useCallback) {
        generateRequireMap(ruleTitle, desc, url, jsPath);
        if (useCallback) {
            // 体检批六判型：原版裸 check-cast Function；非 Function 按原版收口故意抛
            // ClassCastException（与 require0 文件分支同口径）。
            if (!(callback instanceof Function)) {
                throw new ClassCastException("require callback is not a Function: "
                        + (callback == null ? "null" : callback.getClass().getName()));
            }
            return ((Function) callback).call(cx, parentScope, parentScope, new Object[]{code});
        }
        return code;
    }

    /**
     * 8.83 JSEngine.proxyRequire(Object p1, String p2) — smali 8192-8237 (.line 3590-3596)。
     * p2 为空（null/""）直接返回 p2；否则把 p1 经 argsNativeObjectAdjust 转换，
     * 是 JSONObject（本移植：Map，见 argsNativeObjectAdjust 注释）则取 "proxy" 字段，
     * 走 proxyUrl(p2, proxy) 拼出代理地址返回；不是则原样返回 p2。
     */
    private String proxyRequire(Object options, String url) {
        if (url == null || url.isEmpty()) return url;   // :cond_1 / :goto_0
        // :cond_0
        Object adjusted = argsNativeObjectAdjust(options);  // .line 3593
        // 8.83: adjusted instanceof com.alibaba.fastjson.JSONObject；本移植已转 Map
        if (adjusted instanceof Map) {                  // .line 3594
            Object proxy = ((Map<?, ?>) adjusted).get("proxy");  // .line 3595
            return proxyUrl(url, proxy == null ? null : String.valueOf(proxy));  // .line 3596
        }
        return url;                                     // :cond_1
    }

    // ---- JavaAdapter 垫片（星辰适配，2026-10-16）----
    // 原生 JavaAdapter 靠运行时生成字节码，安卓 ART 上抛 java.lang.Error：穿透 JS 的
    // try/catch、也穿透宿主 catch(Exception)，红果代理真机实证回 200 空包。海阔规则
    // 常用 new JavaAdapter(接口, js对象)（如 Enumeration 懒解密流）——接口情形一律走
    // java.lang.reflect.Proxy（ART 支持），回调时在调用线程进 Context 执行 JS 函数；
    // 非接口情形回落原生实现，保持原行为。
    static final class HkJavaAdapterFn extends BaseFunction {
        private final Object original;

        HkJavaAdapterFn(Object original) {
            this.original = original;
        }

        @Override
        public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            return adapt(cx, scope, args);
        }

        @Override
        public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
            Object r = adapt(cx, scope, args);
            if (r instanceof Scriptable) return (Scriptable) r;
            return cx.getWrapFactory().wrapAsJavaObject(cx, scope, r, null);
        }

        private Object adapt(Context cx, Scriptable scope, Object[] args) {
            try {
                if (args != null && args.length >= 2 && args[1] instanceof Scriptable) {
                    Class<?> cls = null;
                    Object a0 = args[0];
                    if (a0 instanceof org.mozilla.javascript.NativeJavaClass) {
                        cls = ((org.mozilla.javascript.NativeJavaClass) a0).getClassObject();
                    } else if (a0 instanceof NativeJavaObject) {
                        Object u = ((NativeJavaObject) a0).unwrap();
                        if (u instanceof Class) cls = (Class<?>) u;
                    }
                    if (cls != null && cls.isInterface()) {
                        ClassLoader cl = cls.getClassLoader();
                        if (cl == null) cl = HkJsRuntime.class.getClassLoader();
                        return java.lang.reflect.Proxy.newProxyInstance(cl,
                                new Class<?>[]{cls}, new JsProxyHandler((Scriptable) args[1]));
                    }
                }
            } catch (Throwable ignored) {
            }
            if (original instanceof org.mozilla.javascript.Function) {
                return ((org.mozilla.javascript.Function) original).call(cx, scope, scope, args);
            }
            throw Context.reportRuntimeError("JavaAdapter unavailable");
        }
    }

    /** JS 对象 → Java 接口代理：方法调用时进 Context 执行同名 JS 函数。 */
    static final class JsProxyHandler implements java.lang.reflect.InvocationHandler {
        private final Scriptable jsObj;

        JsProxyHandler(Scriptable jsObj) {
            this.jsObj = jsObj;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] mArgs) {
            String name = method.getName();
            if ("toString".equals(name) && (mArgs == null || mArgs.length == 0)) return "HkJsProxy";
            if ("hashCode".equals(name) && (mArgs == null || mArgs.length == 0)) return System.identityHashCode(proxy);
            if ("equals".equals(name) && mArgs != null && mArgs.length == 1) return proxy == mArgs[0];
            return ContextFactory.getGlobal().call(cx -> {
                Object fn = ScriptableObject.getProperty(jsObj, name);
                if (!(fn instanceof org.mozilla.javascript.Function)) {
                    throw Context.reportRuntimeError("js proxy missing fn: " + name);
                }
                org.mozilla.javascript.Function f = (org.mozilla.javascript.Function) fn;
                Scriptable fnScope = f.getParentScope();
                if (fnScope == null) fnScope = jsObj;
                Object[] jsArgs = new Object[mArgs == null ? 0 : mArgs.length];
                for (int i = 0; i < jsArgs.length; i++) jsArgs[i] = Context.javaToJS(mArgs[i], fnScope);
                Object r = f.call(cx, fnScope, jsObj, jsArgs);
                Class<?> rt = method.getReturnType();
                if (rt == void.class || rt == Void.class) return null;
                Object u = r;
                if (u instanceof NativeJavaObject) u = ((NativeJavaObject) u).unwrap();
                if (u == null || u == Undefined.instance) {
                    if (rt == boolean.class) return false;
                    if (rt.isPrimitive()) return 0;
                    return null;
                }
                if (rt == boolean.class || rt == Boolean.class) return Context.toBoolean(u);
                if (rt.isPrimitive() || Number.class.isAssignableFrom(rt)) return Context.toNumber(u);
                if (rt == String.class) return Context.toString(u);
                return u;
            });
        }
    }

    private void installJavaAdapterShim(Scriptable scope) {
        Object orig = ScriptableObject.getProperty(scope, "JavaAdapter");
        if (orig instanceof HkJavaAdapterFn) return;
        ScriptableObject.putProperty(scope, "JavaAdapter", new HkJavaAdapterFn(orig));
    }

    /**
     * 8.83 JSEngine.initRequire(Context p1, Scriptable p2) — smali 5400-5484 (.line 540-558)。
     * CommonJS requirejs 桥：StrongCachingModuleScriptProvider（UrlModuleSourceProvider 指向
     * getDirectory() 即 <rootDir>/libs 的 file:// URI）双重检查锁单例；
     * new MyRequire(cx, scope, provider, null, null, false) 挂到 scope.requirejs。
     * 8.83 锁的是 JSEngine.class，本移植锁 HkJsRuntime.class（移植类）。
     */
    private String requireProviderKey;  // 星辰适配：provider 按规则缓存的键

    private void initRequire(Context cx, Scriptable scope) {
        // 星辰适配：8.83 的模块基只有全局 libs；星辰规则包的自带库落在规则自己的
        // data/libs（导入落盘口径），按名字取组件（require('crypto') 等）必须连规则
        // 自己的库目录一起查，否则规则自带的组件永远取不到。故基目录=全局 libs +
        // 当前规则 libs，provider 按规则标题缓存（近似 8.83 的单例语义、避免每页重编模块）。
        String key = rule == null ? "" : String.valueOf(rule.getTitle());
        if (cachingModuleScriptProvider == null || !key.equals(requireProviderKey)) {
            synchronized (HkJsRuntime.class) {
                if (cachingModuleScriptProvider == null || !key.equals(requireProviderKey)) {
                    java.util.List<java.net.URI> bases = new java.util.ArrayList<>();
                    bases.add(getDirectory());
                    try {
                        if (rule != null) {
                            File rl = new File(HkRuleManager.get().getDataDir(rule.getTitle()), "libs");
                            if (!rl.exists()) rl.mkdirs();
                            bases.add(java.net.URI.create("file://" + rl.getAbsolutePath() + File.separator));
                        }
                    } catch (Throwable ignored) {
                    }
                    UrlModuleSourceProvider sourceProvider = new UrlModuleSourceProvider(bases, null);
                    cachingModuleScriptProvider = new StrongCachingModuleScriptProvider(sourceProvider);
                    requireProviderKey = key;
                }
            }
        }
        // :cond_1 / :goto_0 — .line 550: new MyRequire(cx, scope, provider, null, null, false)
        MyRequire require = new MyRequire(cx, scope, cachingModuleScriptProvider, null, null, false);
        // .line 558
        ScriptableObject.putProperty(scope, "requirejs", require);
    }


    // ---- D1-D: parse entries (smali 7945-8147, 20401-21179) ----
    // JSEngineCallback / callbackMap / resCodeMap 已在上方 D1 fields 区定义（3256/3319-3320），此处不重复。

    // ================= 1. parseHome (smali 20401-20560) =================
    /**
     * 8.83: parseHome(String url, String resCode, ArticleListRule rule, String js,
     * Map injectMap, OnFindCallBack&lt;List&lt;ArticleList&gt;&gt; callback)。
     * 脚本拼接顺序：\n + getMyRule + generateInjectMap + generateMyParams(myParams)
     * + getMyType("home") + getMyCallbackKey + getMyUrl + getMyJs；runScript(script, uuid)。
     */
    public void parseHome(String url, String resCode, HkRule rule, String js,
                          Map<String, Object> injectMap, JSEngineCallback<List<?>> callback) {
        String uuid = genUuid();
        callbackMap.put(uuid, callback);
        resCodeMap.put(uuid, resCode);
        try {
            String script = "\n"
                    + getMyRule(rule)
                    + generateInjectMap(injectMap)
                    + generateMyParams(myParams)
                    + getMyType("home")
                    + getMyCallbackKey(uuid)
                    + getMyUrl(url)
                    + getMyJs(js);
            currentMyType = "home";
            runScript(script, uuid);
        } catch (Exception e) {
            setError("运行出错：" + e.getMessage(), uuid, GSON.toJson(rule));
            Logger.t(TAG).e(e, "parseHome: ");
        }
    }

    // ================= 2. parseLastChapterRule (smali 20561-20701) =================
    /**
     * 8.83: parseLastChapterRule(String url, String resCode, ArticleListRule rule, String js,
     * Map injectMap, OnFindCallBack&lt;String&gt; callback)。
     * 拼接顺序：getMyRule + generateMyParams(myParams) + generateInjectMap
     * + getMyType("lastChapter") + getMyCallbackKey + getMyUrl + getMyJs；
     * runScript(script, uuid, true)。异常走 callback.showErr（不 setError）。
     */
    public void parseLastChapterRule(String url, String resCode, HkRule rule, String js,
                                     Map<String, Object> injectMap, JSEngineCallback<String> callback) {
        String uuid = genUuid();
        callbackMap.put(uuid, callback);
        resCodeMap.put(uuid, resCode);
        try {
            String script = ""
                    + getMyRule(rule)
                    + generateMyParams(myParams)
                    + generateInjectMap(injectMap)
                    + getMyType("lastChapter")
                    + getMyCallbackKey(uuid)
                    + getMyUrl(url)
                    + getMyJs(js);
            currentMyType = "lastChapter";
            runScript(script, uuid, true);
        } catch (Exception e) {
            callback.showErr(e.getMessage());
            Logger.t(TAG).e(e, "parseLastChapter: ");
        }
    }

    // ================= 3. parsePreRule(SearchEngine) (smali 20725-20823) =================
    /**
     * 8.83: parsePreRule(SearchEngine engine)。
     * 拼接顺序：getMyCallbackKey + getMyType("preEngine") + getMyRule + engine.getPreRule()；
     * runScript(script, uuid)。因 HkRule 统一了 SearchEngine/ArticleListRule 模型，
     * 与下方的 parsePreRule(HkRule) 签名冲突，故更名为 parsePreEngineRule（见 notes）。
     */
    public void parsePreEngineRule(HkRule rule) {
        String uuid = genUuid();
        try {
            String script = ""
                    + getMyCallbackKey(uuid)
                    + getMyType("preEngine")
                    + getMyRule(rule)
                    + rule.getPreRule();
            currentMyType = "preEngine";
            runScript(script, uuid);
        } catch (Exception e) {
            setError("运行出错：" + e.toString(), uuid, GSON.toJson(rule));
            Logger.t(TAG).e(e, "parsePreRule: ");
        }
    }

    // ================= 4. parsePreRule(ArticleListRule) (smali 20824-20834) =================
    /** 8.83: parsePreRule(ArticleListRule rule) -> parsePreRule(rule, false)。 */
    public void parsePreRule(HkRule rule) throws Exception {
        parsePreRule(rule, false);
    }

    // ================= 5. parsePreRule(ArticleListRule, boolean) (smali 20835-20955) =================
    /**
     * 8.83: parsePreRule(ArticleListRule rule, boolean throwOnError)。
     * 拼接顺序：getMyCallbackKey + getMyType("preHome") + getMyRule
     * + generateMyParams(myParams) + rule.getPreRule()；
     * runScript(script, uuid, throwOnError)。
     * catch 内先 Timber.e；throwOnError=false 时 setError 后返回，true 时原样 throw。
     */
    public void parsePreRule(HkRule rule, boolean throwOnError) throws Exception {
        String uuid = genUuid();
        try {
            String script = ""
                    + getMyCallbackKey(uuid)
                    + getMyType("preHome")
                    + getMyRule(rule)
                    + generateMyParams(myParams)
                    + rule.getPreRule();
            currentMyType = "preHome";
            runScript(script, uuid, throwOnError);
        } catch (Exception e) {
            Logger.t(TAG).e(e, "parsePreRule: ");
            if (!throwOnError) {
                setError("运行出错：" + e.getMessage(), uuid, GSON.toJson(rule));
            } else {
                throw e;
            }
        }
    }

    // ================= 6. parseSearchRes public (smali 21021-21059) =================
    /**
     * 8.83: parseSearchRes(String url, String resCode, SearchEngine engine,
     * Map injectMap, OnFindCallBack&lt;List&lt;SearchResult&gt;&gt; callback)
     * 先 engine.toMovieRule() 再调私有 parseSearchRes。
     * HkRule 统一模型后 toMovieRule 投影被吸收，直接委托私有版本。
     */
    public void parseSearchRes(String url, String resCode, HkRule rule,
                               Map<String, Object> injectMap, JSEngineCallback<List<?>> callback) {
        parseSearchResInternal(url, resCode, rule, injectMap, callback);
    }

    // ================= 7. parseStr (smali 21060-21179) =================
    /**
     * 8.83: parseStr(String resCode, String js, MovieRule rule, OnFindCallBack&lt;String&gt; callback)。
     * 注意：p1 是 resCode（进 resCodeMap），p2 是 js。
     * 拼接顺序：getMyCallbackKey + getMyType("string") + getMyRule + getMyJs(js)；
     * runScript(script, uuid)。
     */
    public void parseStr(String resCode, String js, HkRule rule, JSEngineCallback<String> callback) {
        String uuid = genUuid();
        callbackMap.put(uuid, callback);
        resCodeMap.put(uuid, resCode);
        try {
            String script = ""
                    + getMyCallbackKey(uuid)
                    + getMyType("string")
                    + getMyRule(rule)
                    + getMyJs(js);
            currentMyType = "string";
            runScript(script, uuid);
        } catch (Exception e) {
            setError("运行出错：" + e.getMessage(), uuid, GSON.toJson(rule));
            Logger.t(TAG).e(e, "parseStr: ");
        }
    }

    // ================= 8. parseSearchRes private (smali 7945-8147) =================
    /**
     * 8.83 私有 parseSearchRes(String url, String resCode, MovieRule rule,
     * Map injectMap, OnFindCallBack&lt;List&lt;SearchResult&gt;&gt; callback)。
     * searchFind 非 "js:" 开头时直接 callback.showErr(title + "---搜索结果解析失败！请检查规则") 并返回。
     * 拼接顺序：getMyCallbackKey + getMyRule + generateInjectMap
     * + generateMyParams(myParams) + getMyUrl(url) + getMyType("search")
     * + getMyJs(rule.getSearchFind())；runScript(script, uuid)。
     * catch 内先 Timber.e 再 setError（用 e.toString() 而非 getMessage()）。
     */
    private void parseSearchResInternal(String url, String resCode, HkRule rule,
                                        Map<String, Object> injectMap, JSEngineCallback<List<?>> callback) {
        String uuid = genUuid();
        callbackMap.put(uuid, callback);
        resCodeMap.put(uuid, resCode);
        if (!rule.getSearchFind().startsWith("js:")) {
            callback.showErr(rule.getTitle() + "---搜索结果解析失败！请检查规则");
            return;
        }
        try {
            String script = ""
                    + getMyCallbackKey(uuid)
                    + getMyRule(rule)
                    + generateInjectMap(injectMap)
                    + generateMyParams(myParams)
                    + getMyUrl(url)
                    + getMyType("search")
                    + getMyJs(rule.getSearchFind());
            currentMyType = "search";
            runScript(script, uuid);
        } catch (Exception e) {
            Logger.t(TAG).e(e, "parseSearchRes: ");
            setError("运行出错：" + e.toString(), uuid, GSON.toJson(rule));
        }
    }


    // ---- D1-E: network (smali 2061-2381, 8148-8191, 15903-15919) ----

// ---------------------------------------------------------------------------
// 8.83: JSEngine$$ExternalSyntheticLambda15 / lambda$fetch0$9
// HttpHelper$RuleFetchDelegate.fetch(String url, boolean toHex, boolean inputStream)
// captures (JSEngine, ruleKey): argsNativeObjectAdjust(ruleKey) -> fetchByHiker(...)
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// 8.83: JSEngine.fetch0(String url, Object options, Object ruleKey,
//                        HttpHelper$HeadersInterceptor)  [smali 2061-2143]
// Called by the JS fetch API path. resCode/lastUrl are captured inside
// HttpHelper.fetch0 (NOT HERE); this method only builds options and delegates.
// ---------------------------------------------------------------------------
private Object fetch0(String url, Object options, Object ruleKey, HkHttpHelper.HeadersInterceptor interceptor) {
    try {
        if (TextUtils.isEmpty(url)) return "";
        url = proxyRequire(ruleKey, url);
        java.util.Map<String, Object> opts = null;
        if (options != null && !isUndefined(options)) {
            Object adj = argsNativeObjectAdjust(options);
            // 体检批六判型：原版裸 check-cast Map、CCE 被本函数 catchall 吞成回 ""；
            // 非 Map（数组/字符串 options）照原口径直接回 ""，不发无选项请求；
            // adj 为 null 时原版过转得 null opts，照旧。
            if (adj != null && !(adj instanceof java.util.Map)) return "";
            opts = (java.util.Map<String, Object>) adj;
        }
        final Object ruleKey0 = ruleKey;
        return HkHttpHelper.fetch0(url, opts, interceptor, new HkHttpHelper.RuleFetchDelegate() {
            @Override
            public Object fetch(String u, boolean toHex, boolean inputStream) {
                return fetchByHiker(u, argsNativeObjectAdjust(ruleKey0), toHex, inputStream);
            }
        });
    } catch (Throwable t) {
        Logger.t(TAG).e(t, "fetch0");
        return "";
    }
}

// ---------------------------------------------------------------------------
// 8.83: JSEngine.fetchByHiker(String url, Object options, boolean toHex,
//                             boolean inputStream)  [smali 2144-2381]
// Handles the hiker:// and file:// pseudo-protocols for fetch.
// NOTE the signature swap: at smali :cond_0 p3 ("hiker://page/") is reused as a
// boolean flag AFTER the page branch, i.e. (toHex, inputStream) order.
// ---------------------------------------------------------------------------
private Object fetchByHiker(String url, Object options, boolean toHex, boolean inputStream) {
    if (url.startsWith("hiker://files/") || url.startsWith("file://")) {
        // file branch
        String path = getFilePath(url);
        java.io.File f = new java.io.File(path);
        // 种子回补（命中种子表才动作，见 hkSeedAssetFor）：目标缺失或
        // 只剩失败回写的空壳时，先按内置种子原样补回再走原读路。
        hkSeedRestore(url, f);
        if (!f.exists()) {
            // $.require(http) 全局 libs 缓存 miss 时回退按规则 libs（.hkzip 自带库）
            String alt = resolvePerRuleLibFile(url);
            if (alt != null) {
                path = alt;
                f = new java.io.File(path);
            }
            if (!f.exists()) return "";
        }
        if (inputStream) {
            try {
                return hkFileToInputStream(path);
            } catch (java.io.IOException e) {
                Logger.t(TAG).e(e, "fetchByHiker file inputStream");
                return "";
            }
        }
        if (toHex) {
            try {
                byte[] bytes = hkFileToBytes(path);
                return hkBytesToHexString(bytes);
            } catch (java.io.IOException e) {
                Logger.t(TAG).e(e, "fetchByHiker file toHex");
                return "";
            }
        }
        return fileToString(f, null, true);
    }
    if (url.startsWith("hiker://page/")) {
        // 8.83: PageParser.parsePageRule(rule, url)；宿主已移植为 handlePageRequest(url)（同文件）。
        try {
            return handlePageRequest(url);
        } catch (Exception e) {
            Logger.t(TAG).e(e, "fetchByHiker page");
            return "";
        }
    }
    if (url.startsWith("hiker://")) {
        if (url.startsWith("hiker://assets/")) {
            try {
                return hkGetAssetsFile(url);
            } catch (Exception e) {
                return "";
            }
        }
        try {
            return hkGetRulesByHiker(url);
        } catch (Exception e) {
            return "";
        }
    }
    return Boolean.FALSE; // 8.83 clobbers p4 to 0x0 before :cond_2, so this is always FALSE
}

// ---------------------------------------------------------------------------
// 8.83: JSEngine.post0(String url, Object options, Object ruleKey,
//                      boolean flag)  [smali 8148-8191]
// NOTE: the trailing boolean is passed INVERTED (xor 1) to
// generateRequestOptions(ops, !flag). 8.83 callers: post/postRequest pass
// true, postPC passes false — keep the same values at the call sites.
// ---------------------------------------------------------------------------
private Object post0(String url, Object options, Object ruleKey, boolean flag) {
    Object postOps = argsNativeObjectAdjust(options);
    if (isUndefined(postOps)) {
        postOps = null;
    }
    if (postOps != null) {
        // 体检批六判型：原版裸 check-cast Map 且本方法无 try，CCE 直穿桥面；
        // 非 Map 按原版收口故意抛 ClassCastException，不把畸形 options 当无选项发请求。
        if (!(postOps instanceof java.util.Map)) {
            throw new ClassCastException("post0 options is not a Map: "
                    + postOps.getClass().getName());
        }
        postOps = HkHttpHelper.generatePostOps((java.util.Map<String, Object>) postOps);
    }
    Object requestOptions = generateRequestOptions(postOps, !flag);
    return fetch0(url, requestOptions, ruleKey, null);
}

// ---------------------------------------------------------------------------
// 8.83: JSEngine.fetchWithHeadersInterceptor(String, Object, Object,
//                                            HttpHelper$HeadersInterceptor)
//                                              [smali 15903-15919] (public)
// Applies generateHeadersOptions(options) first, then fetch0.
// ---------------------------------------------------------------------------
public String fetchWithHeadersInterceptor(String url, Object options, Object ruleKey,
                                          HkHttpHelper.HeadersInterceptor interceptor) {
    Object headerOpts = generateHeadersOptions(options);
    Object result = fetch0(url, headerOpts, ruleKey, interceptor);
    // 体检批六判型：原版裸 check-cast String；非文本结果（流/字节）按原版收口
    // 故意抛 ClassCastException，不把流对象 toString 当正文回。null 照原版过转。
    if (result != null && !(result instanceof String)) {
        throw new ClassCastException("fetchWithHeadersInterceptor non-text result: "
                + result.getClass().getName());
    }
    return (String) result;
}


    // ---- D1-F: eval infra helpers ----
/**
 * Port of JSEngine.initRhino(Lorg/mozilla/javascript/Context;)V.
 * Smali lines 5485-5505. Note: the Context here is Context.
 */
private void initRhino(Context cx) {
    cx.setOptimizationLevel(-1);
    cx.setLanguageVersion(200);
    cx.setTrackUnhandledPromiseRejections(true);
}

// ---- 2. getMyRule (smali 4936-5112) ------------------------------------------

/**
 * Port of JSEngine.getMyRule(Ljava/lang/Object;)Ljava/lang/String; (public static).
 * Smali lines 4936-5112.
 *
 * DIVERGENCE (documented, not simplified): 8.83 dispatches on three model
 * classes (ArticleListRule / SearchEngine / MovieRule), each contributing
 * getTitle() + getDisplayName(). The target has a single HkRule type with
 * getTitle() and no display-name concept, so the dispatch collapses to one
 * instanceof and displayName stays null (the _displayName branch is kept
 * verbatim and simply never fires).
 */
public static String getMyRule(Object rule) {
    String title = "";
    String displayName = null;
    if (rule instanceof HkRule) {
        title = ((HkRule) rule).getTitle();
    }
    StringBuilder sb = new StringBuilder("let my_rule = '");
    sb.append(escapeJsString(GSON.toJson(rule)));
    sb.append("';\n const MY_RULE = JSON.parse(my_rule);\n my_rule = null;\n ");
    if (!TextUtils.isEmpty(displayName)) {
        sb.append("const _displayName = '").append(escapeJsString(displayName)).append("';\n");
    }
    sb.append("const MY_TICKET = '");
    sb.append(escapeJsString(generateTicket(title)));
    sb.append("';\neval(getJsPlugin());\neval(getJsLazyPlugin());\n");
    return sb.toString();
}

// ---- 3. getMyType (smali 5113-5139) --------------------------------------------

/**
 * Port of JSEngine.getMyType(Ljava/lang/String;)Ljava/lang/String; (private).
 * Smali lines 5113-5139. Note: 8.83 does NOT escape the type value here.
 */
private String getMyType(String type) {
    return "var MY_TYPE = '" + type + "';\n";
}

// ---- 4. getMyCallbackKey (smali 4863-4889) ---------------------------------------

/**
 * Port of JSEngine.getMyCallbackKey(Ljava/lang/String;)Ljava/lang/String; (private).
 * Smali lines 4863-4889. Note: 8.83 does NOT escape the key here.
 */
private String getMyCallbackKey(String callbackKey) {
    return "var CALLBACK_KEY = '" + callbackKey + "';\n";
}

// ---- 5. getMyInput (smali 4890-4920) ----------------------------------------------

/**
 * Port of JSEngine.getMyInput(Ljava/lang/String;)Ljava/lang/String; (private).
 * Smali lines 4890-4920.
 */
private String getMyInput(String input) {
    return "var input = '" + escapeJsString(input) + "';\n";
}

// ---- 6. getMyJs (smali 4921-4935) -------------------------------------------------

/**
 * Port of JSEngine.getMyJs(Ljava/lang/String;)Ljava/lang/String; (private).
 * Smali lines 4921-4935. 8.83 uses Apache StringUtils.replaceOnce("js:", "");
 * the indexOf/substring form below is the same first-occurrence replacement.
 */
private String getMyJs(String js) {
    int i = js.indexOf("js:");
    return i < 0 ? js : js.substring(0, i) + js.substring(i + 3);
}

// ---- 7. getMyUrl (smali 17338-17389) -----------------------------------------------

/**
 * Port of JSEngine.getMyUrl(Ljava/lang/String;)Ljava/lang/String; (public in 8.83).
 * Smali lines 17338-17389.
 * QUIRK PRESERVED: 8.83 escapes the URL first, then computes MY_HOME from the
 * ESCAPED string (StringUtil.getHome is called on the already-escaped value).
 */
public String getMyUrl(String url) {
    if (url == null) {
        url = "";
    }
    url = escapeJsString(url);
    return "var MY_URL = '" + url + "';\nvar MY_HOME = '" + getHomeUrl(url) + "';\n";
}

// ---- 8. appendLineVar (smali 555-703) -----------------------------------------------

/**
 * Port of JSEngine.appendLineVar(Ljava/lang/String;)Ljava/lang/String; (private).
 * Smali lines 555-703. Counts the leading run of blank lines plus lines that
 * declare script-injected vars (const/var my_*, const/var MY_*, "var input =",
 * "var CALLBACK_", "eval(getJs", "const _displayName"); the FIRST other line
 * stops the count. Emits "const MY_PRE_LINE = <count>;\n" ahead of the script.
 * Returns the input unchanged when it is empty.
 */
private String appendLineVar(String script) {
    if (!TextUtils.isEmpty(script)) {
        String[] lines = script.split("\n");
        int count = 0;
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty()
                    && !t.startsWith("const my_")
                    && !t.startsWith("var my_")
                    && !t.startsWith("const MY_")
                    && !t.startsWith("var MY_")
                    && !t.startsWith("var input =")
                    && !t.startsWith("var CALLBACK_")
                    && !t.startsWith("eval(getJs")
                    && !t.startsWith("const _displayName")) {
                break;
            }
            count++;
        }
        script = "const MY_PRE_LINE = " + count + ";\n" + script;
    }
    return script;
}

// ---- 9. getReplaceJS (smali 5212-5224) ------------------------------------------------

/**
 * Port of JSEngine.getReplaceJS(Ljava/lang/String;)Ljava/lang/String; (private).
 * Smali lines 5212-5224. Delegates with raw=false.
 */
private String getReplaceJS(String js) {
    return getReplaceJS(js, false);
}

// ---- 10. getReplaceJS (smali 5225-5273) -------------------------------------------------

/**
 * Port of JSEngine.getReplaceJS(Ljava/lang/String;Z)Ljava/lang/String; (private).
 * Smali lines 5225-5273. Rewrites the Java-style "b.length()" to the JS
 * "b.length"; when raw is false (the usual path) wraps the whole script in
 * "try{\n ... \n}catch(e){\nsetError(e);\n}". Empty input is returned as-is.
 */
private String getReplaceJS(String js, boolean raw) {
    if (!TextUtils.isEmpty(js)) {
        js = js.replace("if (b != null && b.length() > 0) {", "if (b != null && b.length > 0) {");
        if (raw) {
            return js;
        }
        js = "try{\n" + js + "\n}catch(e){\nsetError(e);\n}";
    }
    return js;
}

// ---- 11. getAllFunctions (smali 3046-3105) -----------------------------------------------

/**
 * Port of JSEngine.getAllFunctions()Ljava/lang/String; (private).
 * Smali lines 3046-3105. Reflects over the engine class for methods annotated
 * with @JSAnnotation and folds getFunctionStr(template, returnType, alias, method)
 * over them, starting from the " var ScriptAPI = java.lang.Class.forName(...)"
 * template. Returns the template unchanged when no method is annotated.
 * DIVERGENCE: 8.83 uses this.getClass(); per the port spec this reflects over
 * HkJsRuntime.class explicitly.
 */
private String getAllFunctions() {
    java.lang.reflect.Method[] methods = HkJsRuntime.class.getDeclaredMethods();
    String result = " var ScriptAPI = java.lang.Class.forName(\"%s\", true, javaLoader);\n";
    for (java.lang.reflect.Method method : methods) {
        JSAnnotation annotation = method.getAnnotation(JSAnnotation.class);
        if (annotation == null) {
            continue;
        }
        result = getFunctionStr(result, annotation.returnType(), annotation.alias(), method);
    }
    return result;
}

// ---- 12. getUaObject (smali 17809-17847) ---------------------------------------------------

/**
 * Port of JSEngine.getUaObject()Ljava/lang/String; (public, @JSAnnotation STRING).
 * Smali lines 17809-17847. 8.83 builds a fastjson JSONObject; the target has
 * no fastjson, so a LinkedHashMap + Gson is used (same key order, same JSON).
 */
@JSAnnotation(returnType = ReturnType.STRING)
public String getUaObject() {
    java.util.Map<String, String> ua = new java.util.LinkedHashMap<>();
    ua.put("mobileUa", UA_MOBILE);
    ua.put("pcUa", UA_PC);
    return GSON.toJson(ua);
}

// ---- 13. setError 3-arg (smali 23978-24003) ----------------------------------------------------

/**
 * Port of JSEngine.setError(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
 * (public). Smali lines 23978-24003. Delegates to the 4-arg overload with a
 * null 4th argument. @Parameter annotations kept as in 8.83.
 */
public void setError(@Parameter("o") Object o,
                    @Parameter("callbackKey") Object callbackKey,
                    @Parameter("ruleKey") Object ruleKey) {
    setError(o, callbackKey, ruleKey, null);
}

// ---- 14. tryGetString (smali 9929-9962) ---------------------------------------------------------

/**
 * Port of JSEngine.tryGetString(Ljava/lang/Object;)Ljava/lang/String; (private).
 * Smali lines 9929-9962. Throws Exception("参数类型只能是字符串") when the
 * (native-object-adjusted) argument is not a String.
 */
private String tryGetString(Object o) throws Exception {
    o = argsNativeObjectAdjust(o);
    if (o instanceof String) {
        return (String) o;
    }
    throw new Exception("参数类型只能是字符串");
}

// ---- 15. generateMyParams (smali 2836-2879) ---------------------------------------------------------

/**
 * Port of JSEngine.generateMyParams(Ljava/lang/String;)Ljava/lang/String; (private).
 * Smali lines 2836-2879. Empty input yields "var MY_PARAMS = {};\n"; otherwise
 * the JSON text is embedded escaped and parsed as MY_PARAMS.
 */
private String generateMyParams(String params) {
    if (TextUtils.isEmpty(params)) {
        return "var MY_PARAMS = {};\n";
    }
    return "var _my_params = '" + escapeJsString(params) + "';\n var MY_PARAMS = JSON.parse(_my_params);\n";
}

// ---- 16. generateInjectMap (smali 2600-2835) ------------------------------------------------------------

/**
 * Port of JSEngine.generateInjectMap(Ljava/util/Map;)Ljava/lang/String;
 * (private static). Smali lines 2600-2835. Emits one "var <key> = <value>;"
 * line per entry: String values are single-quoted + escaped, null becomes
 * "null;", Integer/Float/Boolean are appended raw, anything else becomes
 * "JSON.parse('<escaped fastjson/gson json>');". Null map yields "".
 * (escapeJsString is private static in the target, so this static method can
 * call it directly.)
 */
private static String generateInjectMap(java.util.Map<String, Object> map) {
    String result = "";
    if (map == null) {
        return result;
    }
    for (java.util.Map.Entry<String, Object> entry : map.entrySet()) {
        Object value = entry.getValue();
        result = result + "var " + entry.getKey() + " = ";
        if (value instanceof String) {
            result = result + "'" + escapeJsString((String) value) + "';\n";
        } else if (value == null) {
            result = result + "null;\n";
        } else if (value instanceof Integer || value instanceof Float || value instanceof Boolean) {
            result = result + value + ";\n";
        } else {
            result = result + "JSON.parse('" + escapeJsString(GSON.toJson(value)) + "');\n";
        }
    }
    return result;
}

// ---- 17. decodeConflictStr (StringUtil.smali 1421-1469) -----------------------------------------------

/**
 * Port of StringUtil.decodeConflictStr(Ljava/lang/String;)Ljava/lang/String;
 * (public static). StringUtil.smali lines 1421-1469. Maps doubled full-width
 * punctuation back to ASCII: ？？→? ＆＆→& ；；→; ，，→,. Empty input returned as-is.
 */
private static String decodeConflictStr(String s) {
    if (TextUtils.isEmpty(s)) {
        return s;
    }
    s = s.replace("？？", "?");
    s = s.replace("＆＆", "&");
    s = s.replace("；；", ";");
    s = s.replace("，，", ",");
    return s;
}


    // ---- D1-G: require/net helpers ----
public static String proxyUrl(String url, String proxyConfig) {
    if (!TextUtils.isEmpty(proxyConfig)) {
        String[] lines = proxyConfig.split("\\n");
        for (String line : lines) {
            String[] kv = line.split("=>");
            if (kv.length < 2) continue;
            String key = kv[0].trim();
            if (!url.contains(key)) continue;
            String replacement = kv[1].trim();
            String newUrl = url.replace(key, replacement);
            Logger.t(TAG).d("proxyRequire: old: %s, new: %s", url, newUrl);
            return newUrl;
        }
    }
    return url;
}

// ---------------------------------------------------------------------------
// 8.83: JSEngine.requestForCode(String, Object)String  [JSEngine.smali 8374-8467]
// private. Requests url with withHeaders options; the body must be a JSON
// object {"error":..., "statusCode":..., "body":...}: returns body only when
// error is empty/"null" and statusCode starts with "2"; else null.
// fastjson JSONObject access is mapped to Rhino Scriptable access (see notes).
// ---------------------------------------------------------------------------
private String requestForCode(String url, Object options) {
    try {
        Object headersOptions = generateHeadersOptions(options);
        Object rawResult = fetch0(url, generateRequestOptions(headersOptions, false), null, null); // MISSING: JSEngine.request(String,Object,Object)
        // 体检批六判型＋补诊断：原版两转都在 try 内、类型不符 CCE 被吞成回 null
        // 无日志；这里判型后同口径回 null，但记一行日志留诊断。正常路径不变。
        if (!(rawResult instanceof String)) {
            Logger.t(TAG).d("requestForCode non-text result: %s",
                    rawResult == null ? "null" : rawResult.getClass().getName());
            return null;
        }
        String result = (String) rawResult;
        Object parsed = parseJson(result);
        if (!(parsed instanceof Scriptable)) {
            Logger.t(TAG).d("requestForCode non-object json: %s",
                    parsed == null ? "null" : parsed.getClass().getName());
            return null;
        }
        Scriptable json = (Scriptable) parsed;
        if (json != null) {
            String error = getString(ScriptableObject.getProperty(json, "error"));
            if (!TextUtils.isEmpty(error) && !"null".equals(error)) return null;
            String statusCode = String.valueOf((int) toDouble(ScriptableObject.getProperty(json, "statusCode")));
            if (!statusCode.startsWith("2")) return null;
            return getString(ScriptableObject.getProperty(json, "body"));
        }
    } catch (Exception e) {
        Logger.t(TAG).e(e, "requestForCode failed");
    }
    return null;
}

// ---------------------------------------------------------------------------
// 8.83: JSEngine.isJsCode(String)Z  [JSEngine.smali 5506-5713]
// private. False for empty input and for HTML/XML documents; true when the
// text contains JS keywords or any name from the methodList field.
// ---------------------------------------------------------------------------
private boolean isJsCode(String code) {
    if (TextUtils.isEmpty(code)) return false;
    code = code.trim();
    for (String prefix : new String[]{"<!DOCTYPE", "<html", "<?xml"}) {
        if (code.startsWith(prefix)) return false;
    }
    for (String suffix : new String[]{"</html>", "</rss>"}) {
        if (code.endsWith(suffix)) return false;
    }
    for (String keyword : new String[]{"var ", "let ", "function", "eval(", "call(", "eval (", "call (", " => ", ")=>"}) {
        if (code.contains(keyword)) return true;
    }
    if (this.methodList != null) { // MISSING: 8.83 JSEngine field List<String> methodList
        for (String name : this.methodList) {
            if (code.contains(name)) return true;
        }
    }
    return false;
}

// ---------------------------------------------------------------------------
// 8.83: JSEngine.getDirectory()URI  [JSEngine.smali 3311-3361]
// private. 8.83: URI.create("file://" + UriUtils.getRootDir(ctx) + "/libs/").
// Substituted per task: App.get().getExternalFilesDir(DIRECTORY_DOCUMENTS),
// mkdirs if missing.
// ---------------------------------------------------------------------------
private URI getDirectory() {
    File docs = App.get().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
    if (docs != null && !docs.exists()) docs.mkdirs();
    String root = docs != null ? docs.getAbsolutePath() : "";
    return URI.create("file://" + root + File.separator + "libs" + File.separator);
}

// ---------------------------------------------------------------------------
// 8.83: JSEngine.generateRequestOptions(Object, boolean)Object
// [JSEngine.smali 2880-2997]  private. NOTE: exact signature takes (Object,
// boolean) — the boolean selects PC vs mobile UA.
// Normalizes options to a Map (headers/header fallback), filling in a
// default User-Agent when absent.
// ---------------------------------------------------------------------------
@SuppressWarnings("unchecked")
private Object generateRequestOptions(Object options, boolean isPc) {
    if (options != null && !isUndefined(options)) {
        Object adjusted = argsNativeObjectAdjust(options);
        Map<String, Object> map = adjusted instanceof Map ? (Map<String, Object>) adjusted : new HashMap<>();
        Object headersObj = map.get("headers");
        if (!(headersObj instanceof Map)) headersObj = map.get("header");
        Map<String, Object> headers = headersObj instanceof Map ? (Map<String, Object>) headersObj : null;
        if (headers == null) {
            headers = new HashMap<>();
            map.put("headers", headers);
        }
        if (!headers.containsKey("User-Agent")) {
            // MISSING: 8.83 com.example.hikerview.constants.UA_PC
            // MISSING: 8.83 com.example.hikerview.ui.setting.model.UA_MOBILE
            headers.put("User-Agent", isPc ? UA_PC : UA_MOBILE);
        }
        return map;
    }
    Map<String, Object> map = new HashMap<>();
    Map<String, Object> headers = new HashMap<>();
    // MISSING: UA_PC / UA_MOBILE (same as above)
    headers.put("User-Agent", isPc ? UA_PC : UA_MOBILE);
    map.put("headers", headers);
    return map;
}

// ---------------------------------------------------------------------------
// 8.83: JSEngine.generateHeadersOptions(Object)Object  [JSEngine.smali 2555-2599]
// private. NOTE: exact signature takes a single Object and returns Object.
// Adds withHeaders=true to the options map (fresh map when options is
// null/undefined).
// ---------------------------------------------------------------------------
@SuppressWarnings("unchecked")
private Object generateHeadersOptions(Object options) {
    Boolean withHeaders = Boolean.valueOf(true);
    if (options != null && !isUndefined(options)) {
        Object adjusted = argsNativeObjectAdjust(options);
        Map<String, Object> map = adjusted instanceof Map ? (Map<String, Object>) adjusted : new HashMap<>();
        map.put("withHeaders", withHeaders);
        return map;
    }
    Map<String, Object> map = new HashMap<>();
    map.put("withHeaders", withHeaders);
    return map;
}

// ---------------------------------------------------------------------------
// 8.83: JSEngine.getFilePath(String)String  [JSEngine.smali 3490-3562]
// NOTE: smali declares this public static (not private as described in the
// task); kept public static. "hiker://files/<p>" -> rootDir/<p>;
// "file://<p>" -> <p> (prefix stripped).
// UriUtils.getRootDir substituted per task (same as getDirectory).
// ---------------------------------------------------------------------------
public static String getFilePath(String path) {
    if (path.startsWith("hiker://files/")) {
        path = path.replace("hiker://files/", "");
        File docs = App.get().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (docs != null && !docs.exists()) docs.mkdirs();
        String root = docs != null ? docs.getAbsolutePath() : "";
        return root + File.separator + path;
    }
    if (path.startsWith("file://")) {
        path = path.replace("file://", "");
    }
    return path;
}

// ---------------------------------------------------------------------------
// 8.83: batchExecute(Object tasks, Object listener0, Object success, Object ruleKey)V
// [JSEngine.smali 11276-11680]  D2 真实实现。
// tasks: [{id, func(BaseFunction), param}]；listener0: {func, param} 或 null/undefined；
// success: 触发回调的完成数阈值（默认 tasks.size()）。
// 每个任务在独立线程 + 独立 Rhino Context 里执行 func(param)；
// 完成后若 listener 存在且计数 <= success，回调 listener.func(param, id, false, result)。
// ---------------------------------------------------------------------------
@SuppressWarnings("unchecked")
private void batchExecuteImpl(Object[] args) {
    try {
        Object tasksArg = args != null && args.length > 0 ? argsNativeObjectAdjust(args[0], true) : null;
        if (!(tasksArg instanceof List)) {
            Logger.t(TAG).d("be: tasks not a list");
            SpiderDebug.log("hk", "be: tasks not a list");
            return;
        }
        List<Object> tasks = (List<Object>) tasksArg;
        // listener0: {func, param}
        BaseFunction listenerFunc = null;
        Object listenerParam = null;
        if (args.length > 1 && !isUndefined(args[1])) {
            Object lo = argsNativeObjectAdjust(args[1], true);
            if (lo instanceof Map) {
                Map<String, Object> lm = (Map<String, Object>) lo;
                Object f = lm.get("func");
                if (f instanceof BaseFunction) {
                    listenerFunc = (BaseFunction) f;
                    listenerParam = lm.get("param");
                }
            }
        }
        // success 阈值
        int success = tasks.size();
        if (args.length > 2 && !isUndefined(args[2])) {
            Object s = argsNativeObjectAdjust(args[2]);
            if (s instanceof Number) success = ((Number) s).intValue();
        }
        if (success <= 0 || success > tasks.size()) success = tasks.size();
        final int successLimit = success;
        final BaseFunction finalListenerFunc = listenerFunc;
        final Object finalListenerParam = listenerParam;

        int poolSize = Math.min(tasks.size(), 16);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(poolSize, poolSize, 1L,
                TimeUnit.SECONDS, new LinkedBlockingDeque<>(4096));
        CountDownLatch latch = new CountDownLatch(tasks.size());
        AtomicInteger counter = new AtomicInteger(0);
        // 8.83 JSEngine.batchExecute: String tag = UUIDUtil.genUUID()（本机无 UUIDUtil，等价替代）
        final String beTag = UUID.randomUUID().toString();
        SpiderDebug.log("hk", "be: start tasks=%d success=%d", tasks.size(), successLimit);

        for (Object t : tasks) {
            if (!(t instanceof Map)) {
                latch.countDown();
                continue;
            }
            final Map<String, Object> task = (Map<String, Object>) t;
            final Object funcObj = task.get("func");
            if (!(funcObj instanceof BaseFunction)) {
                latch.countDown();
                continue;
            }
            final BaseFunction taskFunc = (BaseFunction) funcObj;
            final Object taskParam = task.get("param");
            final String taskId = task.get("id") == null ? "" : String.valueOf(task.get("id"));
            pool.execute(() -> {
                // 8.83: jsExecutorService.execute 先 HttpHelper.addTagForThread(tag)
                HkHttpHelper.addTagForThread(beTag);
                // 8.83: latch.getCount()>0 且 counter < successLimit 才执行，否则直接 countDown
                if (latch.getCount() <= 0 || counter.get() >= successLimit) {
                    latch.countDown();
                    return;
                }
                Context cx = Context.enter();
                try {
                    // 8.83 JSEngine.initRhino 同款：池线程 Context 初始化（原版: setOptimizationLevel(-1) + setLanguageVersion(200)）
                    cx.setOptimizationLevel(-1);
                    cx.setLanguageVersion(200);
                    Object result = null;
                    // 8.83 JSEngine.batchExecute 同款：error 传给 listener（成功 null，失败为异常信息）
                    String error = null;
                    try {
                        Scriptable taskScope = taskFunc.getParentScope();
                        result = taskFunc.call(cx, taskScope, taskScope, new Object[]{taskParam});
                    } catch (Throwable e) {
                        error = e.getMessage();
                        Logger.t(TAG).e(e, "be task failed: %s", taskId);
                        SpiderDebug.log("hk", "be: task failed id=%s err=%s", taskId, e);
                    }
                    counter.incrementAndGet();
                    // 8.83: synchronized(latch) 回调 listener
                    if (finalListenerFunc != null) {
                        synchronized (latch) {
                            if (counter.get() <= successLimit && latch.getCount() > 0) {
                                try {
                                    Scriptable ls = finalListenerFunc.getParentScope();
                                    Object r = finalListenerFunc.call(cx, ls, ls,
                                            new Object[]{finalListenerParam, taskId, error, result});
                                    Object adjusted = argsNativeObjectAdjust(r);
                                    if ("break".equals(adjusted)) {
                                        counter.getAndSet(successLimit);
                                    }
                                } catch (Throwable e) {
                                    Logger.t(TAG).e(e, "be listener failed");
                                    SpiderDebug.log("hk", "be: listener failed id=%s err=%s", taskId, e);
                                }
                            }
                        }
                    }
                } finally {
                    try {
                        if (latch.getCount() > 0) latch.countDown();
                    } catch (Throwable ignored) {
                    }
                    Context.exit();
                }
            });
        }
        // 8.83: await(size/16*30+30)s
        long timeoutSec = tasks.size() / 16 * 30L + 30L;
        try {
            latch.await(timeoutSec, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 8.83: await 后 HttpHelper.cancelByTag(tag)，超时未回的本批请求一并取消
        HkHttpHelper.cancelByTag(beTag);
        try {
            if (!pool.isShutdown()) pool.shutdownNow();
        } catch (Throwable e) {
            Logger.t(TAG).e(e, "be pool shutdown failed");
            SpiderDebug.log("hk", "be: pool shutdown failed err=%s", e);
        }
        BATCH_EXECUTE_READY = true;
        Logger.t(TAG).d("be: done, %d tasks", tasks.size());
        SpiderDebug.log("hk", "be: done tasks=%d", tasks.size());
    } catch (Throwable e) {
        Logger.t(TAG).e(e, "batchExecute failed");
        SpiderDebug.log("hk", "be: batchExecute failed err=%s", e);
    }
}

/**
 * 8.83: realLineNumber(int preLine, int lineNumber, Object sourceName)I [smali].
 * sourceName 含 "eval" -> 直接返回 lineNumber；否则 preLine>0 时 lineNumber-preLine。
 */
private int realLineNumber(int preLine, int lineNumber, Object sourceName) {
    String sn = sourceName == null ? "" : String.valueOf(sourceName);
    if (sn.contains("eval")) return lineNumber;
    if (preLine > 0) {
        int r = lineNumber - preLine;
        return r > 0 ? r : lineNumber;
    }
    return lineNumber;
}

/**
 * 8.83: setError(Object o, Object callbackKey, Object ruleKey, Object evalKey)V 完整实现。
 * 明细格式："\n来源：{source}\n代码：{lineSource}\n行数：{n}\n详情：{msg}"；
 * 超 2000 字截断并追加 "...文本过长被截断"；最终消息 = ("解析失败！" | title+"解析失败！") + 明细。
 * 有 callbackKey 对应回调 -> callback.showErr(msg)；否则宿主 Toast。
 */
private void setErrorImpl(Object[] args) {
    try {
        Object o = args != null && args.length > 0 ? argsNativeObjectAdjust(args[0]) : null;
        Object callbackKey = args != null && args.length > 1 ? args[1] : null;
        Object ruleKey = args != null && args.length > 2 ? args[2] : null;
        Object evalKey = args != null && args.length > 3 ? args[3] : null;

        // evalKey 为 Scriptable 时取父 scope 的 MY_PRE_LINE
        int preLine = 0;
        if (evalKey instanceof Scriptable) {
            Scriptable parent = ((Scriptable) evalKey).getParentScope();
            if (parent != null) {
                Object v = parent.get("MY_PRE_LINE", parent);
                if (v != null && !isUndefined(v)) {
                    try {
                        int n = Integer.parseInt(String.valueOf(argsNativeObjectAdjust(v)));
                        if (n > 0) preLine = n + 1;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        String detail;
        if (o instanceof String) {
            detail = (String) o;
        } else if (o instanceof org.mozilla.javascript.JavaScriptException) {
            org.mozilla.javascript.JavaScriptException je = (org.mozilla.javascript.JavaScriptException) o;
            int ln = realLineNumber(preLine, je.lineNumber(), je.sourceName());
            detail = "\n来源：" + je.sourceName() + "\n代码：" + je.lineSource()
                    + "\n行数：" + ln + "\n详情：" + o.toString();
        } else if (o instanceof org.mozilla.javascript.RhinoException) {
            org.mozilla.javascript.RhinoException re = (org.mozilla.javascript.RhinoException) o;
            int ln = realLineNumber(preLine, re.lineNumber(), re.sourceName());
            detail = "\n来源：" + re.sourceName() + "\n代码：" + re.lineSource()
                    + "\n行数：" + ln + "\n详情：" + o.toString();
        } else if (o instanceof ScriptableObject) {
            ScriptableObject so = (ScriptableObject) o;
            Object fileName = so.get("fileName");
            Object lineNumber = so.get("lineNumber");
            Object lineSource = so.get("lineSource");
            int ln = 0;
            try {
                ln = Integer.parseInt(String.valueOf(argsNativeObjectAdjust(lineNumber)));
            } catch (Throwable ignored) {
            }
            ln = realLineNumber(preLine, ln, fileName);
            detail = "\n来源：" + fileName + "\n代码：" + lineSource
                    + "\n行数：" + ln + "\n详情：" + o.toString();
        } else {
            detail = o == null ? "" : String.valueOf(argsNativeObjectAdjust(o));
        }

        if (detail != null && detail.length() > 2000) {
            detail = detail.substring(0, 2000) + "...文本过长被截断";
        }
        error = detail == null ? "" : detail;
        Logger.t(TAG).d("setError: %s", error);

        Object rk = argsNativeObjectAdjust(ruleKey);
        String ck = String.valueOf(argsNativeObjectAdjust(callbackKey));
        JSEngineCallback<?> callback = callbackMap.get(ck);

        // ruleKey 为 Map 且有非空 title -> title + "解析失败！"
        String prefix = "解析失败！";
        if (rk instanceof Map) {
            Object title = ((Map<?, ?>) rk).get("title");
            if (title != null && !String.valueOf(title).isEmpty()) {
                prefix = title + "解析失败！";
            }
        }
        final String msg = prefix + error;
        if (callback == null) {
            try {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        ToastUtil.show(App.get(), msg, android.widget.Toast.LENGTH_LONG));
            } catch (Throwable ignored) {
            }
        } else {
            callbackMap.remove(ck);
            try {
                callback.showErr(msg);
            } catch (Throwable e) {
                Logger.t(TAG).e(e, "setError callback.showErr failed");
            }
        }
    } catch (Throwable e) {
        Logger.t(TAG).e(e, "setError failed");
    }
}

// ---------------------------------------------------------------------------
// 8.83: JSEngine.fileToString(File, String, boolean)String
// [JSEngine.smali 2382-2554]  private.
// NOTE: exact signature is (File, String, boolean), not (String).
// Binary extensions (.zip/.db/.apk/.mp4/.png/.jpg) and anything under a
// "backup" dir return "". When charset arg is non-empty: encrypted=false ->
// charset-detect read; encrypted=true -> fetch the "i--" item as AES key and
// decrypt. Empty charset arg + path inside getFilesDir() -> "".
// ---------------------------------------------------------------------------
private String fileToString(File file, String charset, boolean encrypted) {
    if (!file.exists()) return "";
    String path = file.getAbsolutePath();
    if (path.endsWith(".zip") || path.endsWith(".db") || path.endsWith(".apk")
            || path.endsWith(".mp4") || path.endsWith(".png") || path.endsWith(".jpg")) {
        return "";
    }
    if (path.contains(File.separator + "backup" + File.separator)) return "";
    if (!TextUtils.isEmpty(charset)) {
        // D2: 8.83 语义补全。charset 非空：
        //   encrypted=false -> 字符集探测读取；
        //   encrypted=true  -> 取 "i--" 项为 AES key 解密文件内容。
        if (!encrypted) return getContentByCharsetDetect(path);
        String key = kv != null ? kv.get("i--") : null;
        if (!TextUtils.isEmpty(key)) {
            return aesDecode(key, readFileContent(path));
        }
    } else {
        if (path.contains(getFilesDir())) return "";
    }
    return getContentByCharsetDetect(path);
}

/**
 * 8.83: JSEngine.getContentByCharsetDetect(String) [smali 2669-2674] +
 * FileUtil.getContentByCharsetDetect [FileUtil.smali]。
 * .js/.json 直接按平台默认字符集读；其余用 juniversalchardet 探测编码
 * （org.mozilla.universalchardet，与 8.83 的 EncodeUtilKt.getEncode 同款库），
 * UTF-8（含探测失败回退）走默认读取，否则按探测到的编码解码。
 */
private String getContentByCharsetDetect(String path) {
    if (path.endsWith(".js") || path.endsWith(".json")) {
        return readFileContent(path);
    }
    String encode = detectCharset(path);
    if (encode == null || encode.equalsIgnoreCase("UTF-8")) {
        return readFileContent(path);
    }
    try {
        byte[] bytes = java.nio.file.Files.readAllBytes(new File(path).toPath());
        if (bytes == null) return "";
        return new String(bytes, encode);
    } catch (Throwable e) {
        Logger.t(TAG).e(e, "getContentByCharsetDetect failed: %s", path);
        return readFileContent(path);
    }
}

/** juniversalchardet 探测文件编码（8.83 EncodeUtilKt.getEncode 等价）。 */
private String detectCharset(String path) {
    org.mozilla.universalchardet.UniversalDetector detector = null;
    java.io.InputStream in = null;
    try {
        detector = new org.mozilla.universalchardet.UniversalDetector(null);
        in = new java.io.BufferedInputStream(new java.io.FileInputStream(path));
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0 && !detector.isDone()) {
            detector.handleData(buf, 0, n);
        }
        detector.dataEnd();
        return detector.getDetectedCharset();
    } catch (Throwable e) {
        return null;
    } finally {
        try {
            if (in != null) in.close();
        } catch (Throwable ignored) {
        }
    }
}

// ---------------------------------------------------------------------------
// 8.83: getRequireVersion(String, int)int
// [RequireUtils.smali 866-913]  public static.
// DEVIATION: target has no RequireUtils class; ported as private static here.
// Reads the description file and returns its version; Math.min(version, 0)
// when the file is missing/unparseable.
// ---------------------------------------------------------------------------
private static int getRequireVersion(String path, int version) {
    if (new File(path).exists()) {
        String content = readFileContentStatic(path);
        // fastjson JSON.parseObject -> GSON.fromJson substitution.
        RequireDescription desc = GSON.fromJson(content, RequireDescription.class);
        if (desc != null) return desc.getVersion();
    }
    return Math.min(version, 0);
}

// ---------------------------------------------------------------------------
// 8.83: updateDescription(String, String, int)void
// [RequireUtils.smali 946-975]  public static.
// DEVIATION: target has no RequireUtils class; ported as private static here.
// Writes new RequireDescription(url, version) JSON to path; IOException is
// logged and swallowed.
// ---------------------------------------------------------------------------
private static void updateDescription(String path, String url, int version) {
    try {
        // MISSING: 8.83 com.example.hikerview.ui.rules.model.RequireDescription
        RequireDescription desc = new RequireDescription(url, version);
        // 8.83 FileUtil.stringToFile -> plain write; fastjson JSON.toJSONString -> GSON.toJson.
        writeFileContentStatic(path, GSON.toJson(desc));
    } catch (Exception e) {
        Logger.t(TAG).e(e, "updateDescription failed");
    }
}

// ---------------------------------------------------------------------------
// 8.83: generateRequireMap(String, String, String, String)void
// [RequireUtils.smali 431-461]  public static.
// DEVIATION: target has no RequireUtils class; ported as private static here.
// (dirName, url, proxy, file). 8.83 wraps generateRequireMapSync in a synthetic
// lambda class and posts it to HeavyTaskUtil; expressed here as a Java lambda.
// NOTE: the guard below is a literal port — 8.83 posts ONLY when dirName is
// empty AND url is non-empty (see notes; semantics look inverted).
// ---------------------------------------------------------------------------
private static void generateRequireMapSync(String dirName, String url, String proxy, String file) {
    String path = getFilesDirStatic() + dirName + File.separator + "require.json";
    java.util.List<RequireItem> items;
    if (new File(path).exists()) {
        try {
            String json = readFileContentStatic(path);
            java.lang.reflect.Type t = new TypeToken<java.util.List<RequireItem>>() {}.getType();
            items = GSON.fromJson(json, t);
            if (items == null) items = new ArrayList<>();
        } catch (Exception e) {
            Logger.t(TAG).e(e.getMessage());
            items = new ArrayList<>();
        }
    } else {
        items = new ArrayList<>();
    }
    generateRequireMap(items, dirName, url, proxy, file);
}

private static String getFilesDirStatic() {
    return hkRootDir() + File.separator + "files";
}

private static String readFileContentStatic(String path) {
    try {
        java.io.File f = new java.io.File(path);
        byte[] b = new byte[(int) f.length()];
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
        }
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
        return "";
    }
}

private static void writeFileContentStatic(String path, String content) {
    try (java.io.OutputStream out = new java.io.FileOutputStream(path)) {
        out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (Exception e) {
        Logger.t(TAG).e(e, "writeFileContent failed");
    }
}

private static void generateRequireMap(String dirName, String url, String proxy, String file) {
    if (TextUtils.isEmpty(dirName) && !TextUtils.isEmpty(url)) {
        // 8.83 posts to HeavyTaskUtil queue; here a plain thread (generateRequireMapSync ported above).
        new Thread(() -> generateRequireMapSync(dirName, url, proxy, file)).start();
    }
}

// ---------------------------------------------------------------------------
// 8.83: generateRequireMap(List<RequireItem>, String, String,
//   String, String)void  [RequireUtils.smali 462-648]  private static.
// DEVIATION: target has no RequireUtils class; kept private static here.
// (items, dirName, url, proxy, file): refresh accessTime/proxy of the entry
// whose url matches, else append new RequireItem(url, proxy, file, now);
// drop entries untouched for 7 days when the list exceeds 100; persist to
// <getFilesDir()>/<dirName>/require.json.
// ---------------------------------------------------------------------------
private static void generateRequireMap(List<RequireItem> items, String dirName, String url, String proxy, String file) {
    long now = System.currentTimeMillis();
    boolean found = false;
    // MISSING: 8.83 com.example.hikerview.ui.rules.model.RequireItem
    //   (url, proxy, file, accessTime; RequireItem.smali ctor verified)
    for (RequireItem item : items) {
        if (item.getUrl().equals(url)) {
            item.setAccessTime(now);
            // 8.83: org.apache.commons.lang3.StringUtils.equals(url, proxy) -> null-safe; TextUtils.equals used.
            if (TextUtils.equals(url, proxy)) proxy = null;
            item.setProxy(proxy);
            found = true;
            break;
        }
    }
    if (!found) {
        items.add(new RequireItem(url, TextUtils.equals(url, proxy) ? null : proxy, file, now));
    }
    if (items.size() > 100) {
        long threshold = now - 604800000L; // 8.83 literal 0x240c8400 = 7 days in ms
        // 8.83: annimon Stream.of(items).filter(i -> i.getAccessTime() > threshold).toList()
        List<RequireItem> kept = new ArrayList<>();
        for (RequireItem item : items) {
            if (item.getAccessTime() > threshold) kept.add(item);
        }
        items = kept;
    }
    try {
        String path = getFilesDirStatic() + dirName + File.separator + "require.json";
        // fastjson JSON.toJSONString -> GSON.toJson substitution.
        writeFileContentStatic(path, GSON.toJson(items));
    } catch (Exception e) {
        Logger.t(TAG).e(e, "generateRequireMap failed");
    }
}



    // ---- D1-H: crypto (RC4 / MyBase64) ----
private static class HkRc4 {

    public HkRc4() {
    }

    public String encode(String data, String key) {
        return enc(data, key, "UTF-8");
    }

    public String encode(String data, String key, String charset) {
        return enc(data, key, charset);
    }

    public static String decode(String hex, String key) {
        return dec(hex, key, "UTF-8");
    }

    public static String decode(String hex, String key, String charset) {
        return dec(hex, key, charset);
    }

    public static String enc(String data, String key, String charset) {
        if (data == null || key == null) {
            return "";
        }
        try {
            char[] digits = "0123456789ABCDEF".toCharArray();
            byte[] cipher = rc4Base(data.getBytes(charset), key, charset);
            char[] out = new char[cipher.length * 2];
            int j = 0;
            for (byte b : cipher) {
                out[j++] = digits[(b >>> 4) & 0xF];
                out[j++] = digits[b & 0xF];
            }
            return new String(out);
        } catch (Exception e) {
            return "";
        }
    }

    public static String dec(String hex, String key, String charset) {
        if (hex == null || key == null) {
            return "";
        }
        try {
            byte[] cipher = hexString2Bytes(hex, charset);
            byte[] plain = rc4Base(cipher, key, charset);
            return new String(plain, charset);
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] hexString2Bytes(String hex, String charset) {
        try {
            byte[] src = hex.getBytes(charset);
            byte[] out = new byte[hex.length() / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = uniteBytes(src[i * 2], src[i * 2 + 1]);
            }
            return out;
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static byte[] rc4Base(byte[] data, String key, String charset) {
        byte[] s = initKey(key, charset);
        byte[] out = new byte[data.length];
        int i = 0;
        int j = 0;
        for (int n = 0; n < data.length; n++) {
            i = (i + 1) & 0xFF;
            byte si = s[i];
            j = (j + (si & 0xFF)) & 0xFF;
            byte sj = s[j];
            s[i] = sj;
            s[j] = si;
            int t = ((s[i] & 0xFF) + (si & 0xFF)) & 0xFF;
            out[n] = (byte) (data[n] ^ s[t]);
        }
        return out;
    }

    private static byte[] initKey(String key, String charset) {
        try {
            byte[] keyBytes = key.getBytes(charset);
            byte[] s = new byte[256];
            for (int i = 0; i < 256; i++) {
                s[i] = (byte) i;
            }
            if (keyBytes == null || keyBytes.length == 0) {
                return null;
            }
            int j = 0;
            for (int i = 0; i < 256; i++) {
                j = (j + (keyBytes[i % keyBytes.length] & 0xFF) + (s[i] & 0xFF)) & 0xFF;
                byte tmp = s[i];
                s[i] = s[j];
                s[j] = tmp;
            }
            return s;
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static byte uniteBytes(byte b1, byte b2) {
        int high = Byte.decode("0x" + new String(new byte[]{b1})).byteValue();
        int low = Byte.decode("0x" + new String(new byte[]{b2})).byteValue();
        return (byte) (((char) high << 4) ^ (char) low);
    }
}

// Fragment for HkJsRuntime.java — paste inside the class body.
// Source: 8.83 decompile of com.example.hikerview.utils.encrypt.MyBase64 (+ MyBase64$Coder/Decoder/Encoder).
// Finding: it is a verbatim port of AOSP android.util.Base64 — same flag constants, same
// standard alphabet (A–Z a–z 0–9 +/, and -_ under URL_SAFE), same 76-char wrap, same padding
// rules, same "bad base-64" IllegalArgumentException. Delegating to android.util.Base64 is
// therefore bit-identical; the full streaming Encoder/Decoder/Coder state machine was not
// re-ported by hand to avoid transcription drift. See frag_crypto_notes.md.

private static class HkMyBase64 {

    public final int DEFAULT = 0;
    public final int NO_PADDING = 1;
    public final int NO_WRAP = 2;
    public final int CRLF = 4;
    public final int URL_SAFE = 8;
    public final int NO_CLOSE = 16;

    public HkMyBase64() {
    }

    public byte[] decode(String str, int flags) {
        return decode(str.getBytes(), flags);
    }

    public byte[] decode(byte[] input, int flags) {
        return decode(input, 0, input.length, flags);
    }

    public byte[] decode(byte[] input, int offset, int len, int flags) {
        return android.util.Base64.decode(input, offset, len, flags);
    }

    public byte[] encode(byte[] input, int flags) {
        return encode(input, 0, input.length, flags);
    }

    public byte[] encode(byte[] input, int offset, int len, int flags) {
        return android.util.Base64.encode(input, offset, len, flags);
    }

    public String encodeToString(byte[] input, int flags) {
        return android.util.Base64.encodeToString(input, flags);
    }

    public String encodeToString(byte[] input, int offset, int len, int flags) {
        return android.util.Base64.encodeToString(input, offset, len, flags);
    }
}


    // ---- D1 merge shims ----
    /** 8.83 EcdsaSha256Kt.sign 等价：AndroidKeyStore 生成 EC 密钥对，SHA256withECDSA 签名。 */
    private static String ecdsaSignDevice(String message) throws Exception {
        java.security.KeyPairGenerator kpg =
                java.security.KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
        android.security.keystore.KeyGenParameterSpec spec =
                new android.security.keystore.KeyGenParameterSpec.Builder(
                        "XC_ECDSA_KEY_PAIR", android.security.keystore.KeyProperties.PURPOSE_SIGN)
                        .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256,
                                android.security.keystore.KeyProperties.DIGEST_SHA512)
                        .setKeySize(256)
                        .build();
        kpg.initialize(spec);
        java.security.KeyPair kp = kpg.generateKeyPair();
        java.security.Signature sig = java.security.Signature.getInstance("SHA256withECDSA");
        sig.initSign(kp.getPrivate());
        sig.update(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String sigHex = hkBytesToHexString(sig.sign());
        String sigTail = sigHex.length() > 128 ? sigHex.substring(sigHex.length() - 128) : sigHex;
        return sigTail + "01##04" + hkBytesToHexString(kp.getPublic().getEncoded());
    }

    /** 8.83 HttpHelper.bytesToHexString (in HkHttpHelper as private; local copy for fetchByHiker). */
    private static String hkBytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String h = Integer.toHexString(b & 0xFF);
            if (h.length() == 1) sb.append('0');
            sb.append(h);
        }
        return sb.toString();
    }


}
