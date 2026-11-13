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
    // 环43（批C3）：8.83 JSEngine.classloaders 同位——findJavaClass/loadJavaClass 经
    // DexClassLoader 加载过的 dex 加载器登记表，loadJavaInstance 未命中应用加载器时
    // 先依次试本表、再新建（官 loadJavaInstance smali 19244 同序）。
    // 作用域记名（批C3复核订正）：官本表是 JSEngine 实例字段，但官 JSEngine 是进程
    // 单例、实例字段即进程一份；咱家 HkJsRuntime 非单例（HkEngineManager 按规则名
    // 缓存多实例、HkEngine 另有自建实例、图片单例又一份），改实例字段会把登记表按
    // 运行时拆散、跨运行时加载的 dex 类彼此不可见，与官单例语义分叉，故保留 static
    // 作进程一份的同位形；「loadClass 成功后才登记」一半照官落（见 loadJavaInstance）。
    private static final java.util.List<ClassLoader> DEX_CLASSLOADERS =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

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
            Object m = cx.getThreadLocal(LIST_MARK_KEY);
            if (m instanceof ListEvalMark) {
                // 列表求值（parseList）对齐 8.83：原版无墙钟看门狗，慢而正当的串行
                // 取数（多站超时叠加）不许误杀；只受「顶替」打断——见 ListEvalMark。
                // 2026-10-31 橘汁复诊补：顶替之外另留一道 600 秒的远界（非官方物，
                // 纯壳子安全网）——真死循环/永挂的列表求值仍要能被打断，否则单线程
                // executor 被占死、该规则后续所有求值全灭；界值须远大于正当慢求值
                // 的最坏串行超时叠加（见 ListEvalMark.deadlineMs 的测算）。
                ListEvalMark lm = (ListEvalMark) m;
                if (lm.superseded) {
                    throw new Error("hk js list eval superseded");
                }
                if (System.currentTimeMillis() > lm.deadlineMs) {
                    throw new Error("hk js list eval timeout (" + LIST_EVAL_BUDGET_MS + "ms)");
                }
                return;
            }
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

    // ---- 列表求值顶替（新链加载段，2026-10-31 橘汁案对照 8.83 定根）----
    // 8.83 的列表求值在调用线程（Dispatchers.IO）各起一份 Context+scope 并发跑：
    // 后到的加载不等先到的，先到的结果被 ArticleListModel 的 ticket 作废即可，
    // 页面最终由最新一发加载出结果。星辰是串行单线程引擎（一运行时一 JS 线程
    // ＋结果收集是实例字段），做不到真并发，此前的落法是后到者排队等先到者整段
    // 跑完（含其全部网络超时，最长 120 秒看门狗/150 秒等待）：先到者结果注定被
    // ticket 作废，却把当前加载堵死；重入几发就永远轮不到当前加载，真机形态
    // ＝load start 反复出现、零完成、零报错（橘汁 page=1 11 秒 5 发全灭）。
    // 串行引擎的等价落法：新列表求值进来先顶掉在跑的旧列表求值（观察者在下个
    // 指令 tick 打断它，残余仅限它正堵在 Java 桥调用里的时间，各桥调用自带超时）；
    // 已排队未起跑的旧列表求值按代次直接跳过。顶替只作用于 parseList 两入口的
    // 列表求值；init/懒规则/详情/搜索与缓存代码重建（parseListUnmarked）不进此机制。
    // 2026-11-11 首拉空案订正（账 hk-import-firstload-empty-dig §五/§六）：顶替的
    // 判据此前是运行时全局代次——同规则任一其他面（子页/旧宿主页）的一发提交就能
    // 把首拉顶掉，且被顶的排队者直接回一张伪造的空表冒充求值结果；作废票据的作用
    // 域却是页面模型，两者不一致时空表被当真结果落页＝导入后首开静默空白、下拉
    // （无并发）才出数据。官 8.83 无伪造结果这条路：每发回真结果，票据只挑上屏。
    // 现改：顶替绑页面加载身份（ListEvalMark.scope，与票据同作用域——新链是页面
    // 模型实例、旧宿主是 HkEngine 实例），只有同身份的继任者能顶替前任；跨身份
    // 提交各排各队、跑出各自的真结果，上不上屏归调用方票据。被同身份顶掉的一发
    // 按取消语义收口（ListEvalSupersededException），伪造空表整支废除。
    private static final String LIST_MARK_KEY = "hk.eval.listMark";

    /**
     * 列表求值的远界（壳子安全网、非 8.83 语义；8.83 是同步等待、无任何墙钟）：
     * 上一轮（6f4ef11f3）拆了列表求值的 120 秒指令看门狗，却把等价的墙留在
     * parseListInternal 的 Future.get(150 秒) 上——坏网下正当慢求值会被它拦腰
     * 截断：调用方先拿 TimeoutException 报「解析失败」，后台求值即便随后跑完、
     * 结果也无人接收（橘汁 1.4.69 真机依旧不行的剩余断点）。测算口径：橘汁
     * home 一发串行取数 = zone 公钥 POST（仅 jz_n 缺失时）＋分类 GET＋列表 POST
     * ＋最多 3 次补图，每次 HJ 调用在 java 直连（连接 10s＋读 12s）失败后还要
     * 再走桥 fetch 回落（POST 两变体、每次 DNS≤10s＋连接/读各按规则 12s），
     * 单次最坏约 86 秒、整发最坏 150–400 秒——150 秒墙正落在其分布中间。
     * 故列表求值的观察者远界放 600 秒（只杀真死循环/永挂，见观察者），
     * 调用方等待放 660 秒（须大于观察者远界，让 JS 侧错误先于裸 TimeoutException
     * 浮出）；非列表求值的 120 秒预算/150 秒等待一律不动。
     */
    private static final long LIST_EVAL_BUDGET_MS = 600_000L;
    private static final long LIST_EVAL_WAIT_SECONDS = 660;

    /**
     * 列表求值被同页面身份的继任者顶替时的取消信号——不是求值错误：调用方收到
     * 它应按「本发已被更新的加载接管」静默丢弃（新链由继任发的回调落页）。
     * 与「回空表冒充结果」的分界：取消是明示的控制流，结果真假不被伪造。
     */
    public static final class ListEvalSupersededException extends Exception {
        ListEvalSupersededException() {
            super("hk js list eval superseded by same-scope successor");
        }
    }

    private static final class ListEvalMark {
        final long gen;
        /**
         * 页面加载身份（顶替的作用域，与调用方票据同域）：新链＝页面模型
         * （HkArticleListModel）实例的令牌，旧宿主＝HkEngine 实例的令牌；
         * 无身份的入口每发自成一个身份（不顶别人、也不被顶，排队跑完回真结果）。
         */
        final Object scope;
        volatile boolean superseded;
        /** 本发列表求值的远界时刻（生成时刻＋LIST_EVAL_BUDGET_MS），观察者据此兜底。 */
        final long deadlineMs;

        ListEvalMark(long gen, Object scope) {
            this.gen = gen;
            this.scope = scope;
            this.deadlineMs = System.currentTimeMillis() + LIST_EVAL_BUDGET_MS;
        }
    }

    private final java.util.concurrent.atomic.AtomicLong listGenSeq =
            new java.util.concurrent.atomic.AtomicLong();
    private volatile ListEvalMark activeListMark;
    /** 各页面身份当前最新一发：排队者起跑时据此判同身份是否已有继任者。守 synchronized(本表)。 */
    private final java.util.Map<Object, ListEvalMark> latestListMarkByScope =
            new java.util.HashMap<>();

    /** 发一枚新 mark：登记为本身份最新一发，并顶掉在跑的同身份旧发。 */
    private ListEvalMark newListMark(Object scope) {
        ListEvalMark mark = new ListEvalMark(listGenSeq.incrementAndGet(), scope);
        synchronized (latestListMarkByScope) {
            latestListMarkByScope.put(scope, mark);
        }
        supersedeActiveListEval(mark);
        return mark;
    }

    /**
     * 顶掉在跑的旧列表求值：只顶同一页面身份、且代次更老的一发——身份守卫防
     * 跨面提交误顶（首拉空案断点），代次守卫防并发迟到者误杀比它更新的求值
     * （先发代次、后抢到锁的迟到者不许反杀已在跑的新求值）。
     */
    private void supersedeActiveListEval(ListEvalMark mark) {
        ListEvalMark m = activeListMark;
        if (m != null && m.scope == mark.scope && m.gen < mark.gen) m.superseded = true;
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
    /** G25/G26：8.83 JSEngine.varMap（JE:154）——引擎进程单例的实例字段，
     * 即全进程一表、纯内存不落盘；putVar 系用裸键、putMyVar 系用
     * ruleTitle@key 共用此表。咱家运行时按规则多实例，故收成静态一表。 */
    private static final Map<String, String> varMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<HkItem> results;
    /** 明细原始条目收集（parseDetailRaw 用，保留 line/col_type 等扩展字段）。 */
    private final List<Map<String, String>> rawResults = new ArrayList<>();
    private volatile boolean collectRaw;

    /** 照官 putVar2(key, value)（JE:21861）：key 非 String 或空→不写；值非 String→JSON 序列化后存。 */
    private void putVar2Impl(String key, Object value) {
        if (key == null || key.isEmpty()) return;
        String s;
        if (value instanceof String) s = (String) value;
        else {
            try { s = stringifyArg(value); }
            catch (Throwable ignored) { s = String.valueOf(value); }
        }
        varMap.put(key, s);
    }

    /** Java 侧设置规则变量（供 HkPageActivity 等调用）。 */
    public void putMyVar(String key, String value) {
        if (key != null) varMap.put(myVarKey(key), value == null ? "" : value);
    }

    /** Java 侧读取规则变量。 */
    public String getMyVar(String key) {
        String v = varMap.get(myVarKey(key));
        return v == null ? "" : v;
    }

    /**
     * E8/G26: myVar key 恒为 ruleTitle@key（8.83 JSEngine.smali:21694
     * putMyVar 无条件拼前缀；旧「key 已带 @ 则直接用」支官无、且 varMap
     * 纯内存无存量数据可兼容，第七阶段收回官形）。
     */
    private String myVarKey(String key) {
        return myVarKeyFor(key, null);
    }

    /** W4：8.83 getRuleTitle(ruleTitleKey)（smali:17390）；隐参解析走 resolveRuleTitle
     * （票据还原＋直名路缺位显式补当前标题，环37）。 */
    private String myVarKeyFor(String key, String ruleTitleKey) {
        if (key == null) return "";
        return resolveRuleTitle(ruleTitleKey) + "@" + key;
    }

    /** G26：官 varMap 全进程一表，跨规则变量表定位恒回同一张表
     *（旧「查对方常驻运行时分表」随合并退役，方法保留供调用点不改形）。 */
    private Map<String, String> myVarsFor(String ruleTitleKey) {
        return varMap;
    }
    /** 跨规则公共持久化（setPublicItem/getPublicItem）。D3：并发图片求值可能同时读写，用并发 map。 */
    private final Map<String, String> publicKv = new java.util.concurrent.ConcurrentHashMap<>();
    /** fc/rc 的内存缓存（url → 数据+过期时间）。 */
    private final Map<String, MemCache> memCache = new HashMap<>();
    /** 官式一次性交付记账（G51，见 HkFindDelivery）：parseList/parseSearch 每发按 CALLBACK_KEY 登记汇点。 */
    private final HkFindDelivery findDelivery = new HkFindDelivery();
    /** 当前线程正在跑的 find 求值的汇点（条目修改面在求值期改汇点的合成表）。 */
    private final ThreadLocal<HkFindDelivery.Sink> activeFindSink = new ThreadLocal<>();
    /** 环23 回落分界账：曾登记过汇点的 callbackKey 全集（登记点在 parseListInternal/
     * parseSearch 的 register 处）。汇点已摘后仍按此判「find 语境迟到交付」——
     * 官 callbackMap miss 即丢；从未登记的 key 是非 find 语境的宿主同步 API
     * 契约（collectResult 旧路），保留回落。 */
    private final java.util.Set<String> findRegisteredKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 环 25-1（硬门审计 B）：官 JsEngineBridge$1.onUpdate 的宿主承接面——
     * addListener 的 onUpdate(key, value) 在官是 new 条目（仅 title=value）后
     * callback.bindObjectToView(key, item) 交页面按 key 分发（updatePage/
     * onRefresh/refresh/onClose/close，页面本体 HkArticleFragment 已有同形分发）。
     * 桥层（HkEngineBridge）在 find 求值前后置/清本监听；未置时（如 HkEngine
     * 同步路、详情求值）onUpdate 无页面承接、只记账不动作（与官无页面回调时
     * 同位）。与搜索路 HkSearchModel 的 SearchCallback.onUpdate 同位。
     */
    public interface BindObjectToViewListener {
        void bindObjectToView(String key, HkItem item);
    }

    private volatile BindObjectToViewListener bindObjectListener;

    /** 桥层装配口：find 求值前置入页面回调、求值后置 null 清除。 */
    public void setBindObjectToViewListener(BindObjectToViewListener listener) {
        this.bindObjectListener = listener;
    }
    /** refreshPage 请求标记（JS 线程写，宿主线程读后清零）。 */
    private volatile boolean refreshRequested;
    private volatile boolean refreshToTop;
    /** refreshPage 节流（照官方 canTouchUI 的 200ms）：上次发刷新事件的时间。 */
    private volatile long lastRefreshPageAt;
    /** 8.83 JSEngine.uiUseMap 同位：canTouchUI 的按 type@key 上次放行时刻表。 */
    private final Map<String, Long> uiUseMap = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 8.83 JSEngine.canTouchUI(String, String)（smali 4842）：默认 200ms 窗口转三参形。
     * 官方法在 JSEngine 类上声明（private），规则侧库（如 hikerPop 的节流封装）经
     * javaContext.getClass().getDeclaredMethod("canTouchUI", String, String, long)
     * 反射调入——咱家此前类上无此方法，反射同位抛 NoSuchMethodException、弹窗链
     * 整条断（聚阅「发现」钮实证，账 hiker-continue-a-drpy-juyue-20261112）。
     * 此处公开声明：反射查找与 JS 成员直调两形都落得到，回值与行为与官逐字一致。
     */
    public boolean canTouchUI(String type, String key) {
        return canTouchUI(type, key, 200);
    }

    /**
     * 8.83 JSEngine.canTouchUI(String, String, long)（smali 4854）：键＝type+"@"+key；
     * 表里有上次放行时刻且距今不足 interval 时回 false（且不刷新时刻）；否则记下
     * 当前时刻回 true。
     */
    public boolean canTouchUI(String type, String key, long interval) {
        String k = type + "@" + key;
        Long last = uiUseMap.get(k);
        long now = System.currentTimeMillis();
        if (last != null && now - last < interval) return false;
        uiUseMap.put(k, now);
        return true;
    }

    // ================= 桥面补齐（2026-11-12 全面对账批） =================
    // 官 JSEngine public 方法全集 208（带 @JSAnnotation 160＋不带注解 41＋
    // synthetic 7）逐名对咱家：注解面 160 早已全等；不带注解面里规则经
    // javaContext 直调/反射可达而咱家缺的，在本节逐个照官补齐。宿主型
    // 参数（Activity/AccountPwd/AuthResult/ArticleList）四件记名不补，
    // 见账 hk-bridge-surface-fill-20261112。

    /** 最近被宿主领用的运行时（8.83 JSEngine.engine 单例的咱家同位：咱家多实例架构无真单例）。 */
    private static volatile HkJsRuntime lastActiveInstance;

    /** 8.83 JSEngine.getInstance()：回引擎本体（咱家为最近领用的一只）。
     * 构造器不登记：HkEngineManager 的 putIfAbsent 竞败弃子与图片引擎都会抢登记、
     * 回半成品/错引擎（复核实证）；登记只走领用点 setActiveInstance。 */
    public static HkJsRuntime getInstance() {
        return lastActiveInstance;
    }

    /** 宿主领用登记（官单例无此对应物，多实例架构的领用钩，记名）：
     * HkEngine 两构造与 HkEngineManager.runtimeFor 胜出者紧挨既有 setCurrentRuntime 登记调本法。 */
    public static void setActiveInstance(HkJsRuntime rt) {
        lastActiveInstance = rt;
    }

    /** 8.83 JSEngine.isTraceLog()：回 traceLog 闸当前值。 */
    public boolean isTraceLog() {
        return traceLog;
    }

    /** 8.83 JSEngine.getMethodList()：回 getFunctionStr 生成时逐个登记的方法名活表（官同为活表直回）。 */
    public List<String> getMethodList() {
        return methodList;
    }

    /** 8.83 JSEngine.getLogs()：回 log() 落的日志活表（官同为活表直回）。 */
    public List<String> getLogs() {
        return logs;
    }

    /** 8.83 JSEngine.clearModuleCache()（smali）：把模块脚本缓存提供者置空，下次 initRequire 重建。咱家提供者是进程静态单例（cachingModuleScriptProvider），同位置空。 */
    public void clearModuleCache() {
        cachingModuleScriptProvider = null;
    }

    /** 8.83 JSEngine.clearVarByRule(String)（smali）：快照 varMap 键集，逐个摘以「<规则名>@」开头的键（putMyVar 键形），空键跳过。 */
    public void clearVarByRule(String ruleTitle) {
        java.util.Set<String> keys = new java.util.HashSet<>(varMap.keySet());
        String prefix = ruleTitle + "@";
        for (String key : keys) {
            if (key != null && !key.isEmpty() && key.startsWith(prefix)) {
                varMap.remove(key);
            }
        }
    }

    /**
     * 8.83 JSEngine.generateMY(String, Object)（smali 753-759）：
     * null→「var X = null;」；String→引号＋escapeJavaScriptString 转义；
     * Integer→裸数；余→小写名存 JSON 串＋原名 JSON.parse 回读双行。
     */
    public String generateMY(String var, Object value) {
        if (value == null) {
            return "var " + var + " = null;\n";
        }
        if (value instanceof String) {
            return "var " + var + " = '" + escapeJsString((String) value) + "';\n";
        }
        if (value instanceof Integer) {
            return "var " + var + " = " + value + ";\n";
        }
        String lower = var.toLowerCase(Locale.getDefault());
        // 官 JSON.toJSONString（fastjson）：Java Map/List 出真 JSON。jsStringify 对 Java
        // Map/List 回 "null"（复核实测），走 stringifyValue 既有分工（同 generateInjectMap 口径）。
        return "var " + lower + " = '" + escapeJsString(stringifyValue(value)) + "';\n var "
                + var + " = JSON.parse(" + lower + ");\n";
    }

    /**
     * 8.83 JSEngine.generateRangeHeadersOptions(Object, int)（源码 1879）：
     * options 调整成 Map 后确保 headers 里有 Range: bytes=0-<range>
     * （headers 缺时先看 header 键、再新建并回填 headers 键）；
     * options 空/undefined 时新建 Map 同置。回调整后的 options 本体。
     */
    public Object generateRangeHeadersOptions(Object options, int range) {
        if (options != null && !isUndefined(options)) {
            Map op = (Map) argsNativeObjectAdjust(options);
            Map<String, String> headerMap = (Map<String, String>) op.get("headers");
            if (headerMap == null) {
                headerMap = (Map<String, String>) op.get("header");
            }
            if (headerMap == null) {
                headerMap = new HashMap<>();
                headerMap.put("Range", "bytes=0-" + range);
                op.put("headers", headerMap);
            } else {
                headerMap.put("Range", "bytes=0-" + range);
            }
            return op;
        } else {
            Map op = new HashMap<>();
            Map<String, String> headerMap = new HashMap<>();
            headerMap.put("Range", "bytes=0-" + range);
            op.put("headers", headerMap);
            return op;
        }
    }

    /**
     * 8.83 JSEngine.updateRequire(String)（源码 2833／smali 同形）：远程码经
     * requestForCode 取回，空即抛「获取远程依赖失败：<url>」，否则写全局
     * libs/<md5(url)>.js（咱家全局 libs 根与官 rootDir/libs 同位，
     * HkRuleManager.getGlobalLibsDir）。
     */
    public void updateRequire(String url) throws Exception {
        String fileName = hkMd5(url) + ".js";
        String code = requestForCode(url, null);
        if (code == null || code.isEmpty()) {
            throw new Exception("获取远程依赖失败：" + url);
        }
        hkStringToFile(code,
                new File(HkRuleManager.get().getGlobalLibsDir(), fileName).getAbsolutePath(),
                null, false);
    }

    /** 8.83 JSEngine.inJsDir(String)：官 public static 本体语义已在 hkInJsDir 照官落，此公开名转调之。空入参官为 NPE、咱家 hkInJsDir 回 false（记名微差）。 */
    public static boolean inJsDir(String path) {
        return hkInJsDir(path);
    }

    /** 8.83 JSEngine.putVar(Object)（源码 630）：单参形转两参形（第二参 null）。 */
    public void putVar(Object o) {
        putVar(o, null);
    }

    /** 8.83 JSEngine.fetch(String, Object)（smali 2421）：两参形转三参形（ruleKey=null）并按 String 收形（官 check-cast 同形）。 */
    public String fetch(String url, Object options) {
        return (String) fetch(url, options, null);
    }

    /** 8.83 JSEngine.loadJavaInstance(String)（smali）：Class.forName 后无参实例化；任何异常记日志回 null。 */
    public Object loadJavaInstance(String className) {
        try {
            return Class.forName(className).newInstance();
        } catch (Throwable e) {
            Logger.t(TAG).e(e, "loadJavaInstance");
            return null;
        }
    }

    /** 8.83 JSEngine.loadJavaInstance(String, String, String, boolean)：转本类五参本体。canDex 以 p 非空为准（官四参形本无此参，此为五参本体的内部闸，p 空时 app/登记表两段照跑、终局同回 null；与 resolveJavaClass 的传参并不一致，是新形更贴官四参形——复核订正原注释口径）。 */
    public Object loadJavaInstance(String p, String className, String so, boolean returnClass) {
        return loadJavaInstance(p, className, so, returnClass, p != null && !p.isEmpty());
    }

    /** 8.83 JSEngine.onDestroy()（JE:254-258）：官只 schedulePool.destroy()（timer.cancel），引擎本体存活可继续注册任务；咱家同位物是 clearAllTimers（清 jsTimers+jsTasks）。不许转 destroy()——那是终局死亡（destroyed/Context.exit/线程池关停），复核订正。 */
    public void onDestroy() {
        clearAllTimers();
    }
    /** goTo(url) 导航请求（JS 线程写，宿主线程读后清零）。规则如 xhotvideo 用 goTo('hiker://page/detail?...') 跳 V4。 */
    private volatile String goToUrl;
    /** setPageTitle/getPageTitle 的页标题状态。 */
    private String pageTitle = "";
    /** setPagePicUrl：官方 SetPagePicEvent 语义，宿主存字段供上层取用。 */
    private String pagePicUrl = "";
    /** 8.83 JSEngine.privacyModeUnlocked 同位：checkPrivacyPassword 命中时置真（第九阶段）。 */
    private boolean privacyModeUnlocked;
    /** 8.83 JSEngine.traceLog 同位：log 闸，构造时经 updateTraceLog 同步（第十阶段 G34）。 */
    private boolean traceLog;
    /** 8.83 JSEngine.logs 同位：log() 闸开时逐条落表（满 10000 丢首），getLogs() 读。
     * 官 logs 属单例实例字段＝全进程一表；咱家多实例，收 static 与 varMap 同例（复核订正）。 */
    private static final List<String> logs = new ArrayList<>();
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

    /**
     * 8.83 SettingConfig.getMobileUA（SettingConfig.smali:317-345 亲读）：先读用户
     * 配置 sysUa（PreferenceMgr），非空用之，空才回落 UAEnum.MOBILE（UAEnum.smali:79，
     * 与 UA_MOBILE 常量逐字相同）。官 PreferenceMgr 文件名＝"share_data"
     * （PreferenceMgr.smali:15 亲读，咱家 ppwd 已同文件互证）；旧形误读自造的
     * "hiker" 文件、通道整体是死的（全树 sysUa 无写点），2026-11-11 硬门审计 B
     * 环 24-1 照官改回 "share_data"。官有设置页改此值、咱家暂无该设置项
     * 入口（记名），取值函数先行照官。
     */
    static String getMobileUa() {
        // 官本体无 catch（SettingConfig.smali:317-345）：SP 读取异常直接上抛，不静默回落
        String v = App.get().getSharedPreferences("share_data", android.content.Context.MODE_PRIVATE)
                .getString("sysUa", null);
        if (v != null && !v.isEmpty()) return v;
        return UA_MOBILE;
    }
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
    /**
     * 当发求值 scope 登记（线程本地，入出按栈恢复）：官方每发求值里 MY_RULE
     * 是与规则代码同 scope 的变量，聚合壳在求值内赋值换成子规则对象后，
     * 取页（handlePageRequest/findPageCode）读到的就是子规则——咱家常驻
     * 主 scope＋每发子 scope 的结构下，JS 侧赋值只落在当发子 scope（原型
     * 遮蔽），主 scope 那份恒为宿主规则，G1 只读主 scope 故子规则页全灭
     * （2026-11-11 真机 Empty JSON 案，Rhino 1.7.15 夹具三形态实证）。
     * 覆盖面点名：主路三咽喉（evalJs/evalJsInScope/evalJsInSharedScope）
     * 与转录四路（evalJS 两形/evalJSForObj/runScript 的新 scope）在求值前
     * 登记当发 scope、finally 恢复，myRuleFromScope 先读登记 scope
     * （getProperty 语义与 JS 读 MY_RULE 同果：自有劫持值优先、未劫持经
     * 原型链命中主 scope 宿主值），读不到再回落主 scope。池线程支覆盖
     * （2026-11-11 B-4 补）：batchFetch/batchExecute 提交任务时在调用线程
     * 捕获当发登记、随任务带入池线程登记、finally 栈式恢复（捕获不到＝
     * null 时不登记、回落主 scope 与改前同）。不在覆盖内（与改前等价、
     * 回落主 scope）：定时器/syncExecute/JsProxyHandler 的 Function.call
     * 路、图片 scope 求值、反射路 require 的 callerScope 近似——
     * 2026-11-11 独立复核记名。
     * 环24-11 簇二补（批C4-C）：函数形定时器（fireTimerCallback）与
     * JsProxyHandler 触发时改以函数定义 scope 登记 ambient 再调用（见各
     * 落点注），已不在「不在覆盖内」之列；registerTask 串形回调另走
     * nullRuleScope 的 MY_RULE≡null 官形（登记 scope 上 MY_RULE 在场为
     * null 时 myRuleObjFromScope 以 null 为权威、不回落主 scope）。
     * syncExecute 维持不在覆盖内（账 §1.3(c) 同步同发恒等、已核销）。
     */
    private final ThreadLocal<Scriptable> ambientEvalScope = new ThreadLocal<>();

    private Scriptable enterAmbientScope(Scriptable s) {
        Scriptable prev = ambientEvalScope.get();
        if (s != null) ambientEvalScope.set(s);
        return prev;
    }

    private void exitAmbientScope(Scriptable prev) {
        if (prev != null) ambientEvalScope.set(prev);
        else ambientEvalScope.remove();
    }

    // 新鲜作用域补注册用：非空时 putFunction 改写到该作用域（见 registerApiInto）。
    private ScriptableObject apiRegTarget;
    private final Object apiRegisterLock = new Object();
    private Map<String, String> kv;
    private String error;
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
     * G2：JS 可见名的实现体登记表（putFunctionEx 咽喉同步登记）。
     * 8.83 的桥方法全是引擎本人的 @JSAnnotation 方法，预置段经
     * method_<名>.invoke(javaContext, …) 反射调入；咱家实现体是 registerApi
     * 的 lambda 集，本表是 ScriptAPI 委托法（见 putFunctionEx 后「G2 包装层委托」节）
     * 按名分发到实现体的唯一账。注册目标 scope 换来换去，实现体只有一份。
     */
    private final Map<String, JsFuncEx> apiBodies = new java.util.concurrent.ConcurrentHashMap<>();

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
        apiBodies.put(name, fn);
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


    // ================= G2 包装层委托（ScriptAPI 反射面） =================
    // 8.83 的桥方法全是 JSEngine 本人的 @JSAnnotation 方法：预置段（getAllFunctions
    // 照 getFunctionStr 模板生成）给出 var method_<名> = ScriptAPI.getMethod(…)
    // 与同名 JS 包装函数，调用经 method_X.invoke(javaContext, …) 反射进入 Java，
    // 包装函数在调入前按 @Parameter 名覆写隐参（callbackKey=CALLBACK_KEY、
    // ruleKey=MY_RULE、urlKey/typeKey/dirKey/evalKey/ruleTitleKey 同理）、调入后
    // 按 returnType 桥接返回值。咱家实现体是 registerApi 的 lambda 集，本节为
    // 160 个官名逐个立带官注解的公开委托法（签名/参数名/默认值/别名/桥型逐项
    // 照官包 JSEngine.smali 注解表机器提取比对），委托体把实参原样交实现体
    // （dispatchApi），返回值按官 Java 签名收形；setError/getUaObject/
    // syncExecute/log 四名用既有真方法挂注解（见各自定义处），不在本节重复。
    // 规则自带「劫持包」直接调 method_X.invoke(javaContext, …) 的形态
    // （阅动漫/阅漫君 pages 实证）靠本节句柄成立。

    /**
     * 委托分发：按名取实现体执行。Undefined→null 与 putFunctionEx 同口径；
     * 异常收口同 putFunctionEx（RhinoException 原样、余 RuntimeException 裹
     * WrappedException——反射路的 InvocationTargetException 由 Rhino 解包，
     * 净效果与官反射注册一致）。
     */
    private Object dispatchApi(String name, Object[] args) {
        JsFuncEx body = apiBodies.get(name);
        if (body == null) return null;
        Object[] a = args == null ? new Object[0] : args;
        for (int i = 0; i < a.length; i++) {
            // 反射路（method_X.invoke）下 JS 的 undefined 到 Java 形参是字符串
            // "undefined"（Rhino 反射打包形态，官 argsNativeObjectAdjust 专设
            // "undefined".equals 一支正为此）；与 Undefined 对象同归一为 null，
            // 和直名路 putFunctionEx 的既有口径一致，实现体两侧所见相同。
            if (a[i] == Context.getUndefinedValue() || "undefined".equals(a[i])) a[i] = null;
        }
        Scriptable callerScope = scope;
        // require/requireCache 的调用方作用域：官 require0 经 evalKey 回调在调用方
        // 作用域链里求值；反射路没有 BaseFunction.call 的 scope 可拿，取 evalKey
        // 函数的父作用域（主路恒为主 scope，与官「每发新 scope 的顶层」等价面；
        // 直名路仍走 BaseFunction 的真调用方作用域，不经此近似）。
        int ekIdx = "require".equals(name) ? 4 : "requireCache".equals(name) ? 5
                : "evalPrivateJS".equals(name) ? 1 : -1;
        if (ekIdx >= 0 && a.length > ekIdx && a[ekIdx] instanceof Scriptable) {
            Scriptable ps = ((Scriptable) a[ekIdx]).getParentScope();
            if (ps != null) callerScope = ps;
        }
        Context cx = Context.getCurrentContext();
        try {
            return body.call(cx, callerScope, a);
        } catch (org.mozilla.javascript.RhinoException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new org.mozilla.javascript.WrappedException(e);
        }
    }

    /** STRING 签名收形：实现体回串直用、回对象按 JSON 串化、余按字面。 */
    private String strApi(Object r) {
        if (r == null || r instanceof String) return (String) r;
        if (r instanceof Scriptable || r instanceof Map || r instanceof java.util.List) return jsStringify(r);
        return String.valueOf(r);
    }

    /** JSON 桥且 Java 签名为 Object 的两名（_findItem/_findItemsByCls）收形：回串直用、余串化成 JSON 串待桥解析。 */
    private Object jsonApi(Object r) {
        if (r == null || r instanceof String) return r;
        return jsStringify(r);
    }

    /** int 签名收形（copyFiles/getPrivacyPasswordLen）。 */
    private int intApi(Object r) {
        if (r instanceof Number) return ((Number) r).intValue();
        if (r instanceof Boolean) return ((Boolean) r) ? 1 : 0;
        if (r == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(r).trim());
        } catch (Throwable e) {
            return 0;
        }
    }

    /** boolean 签名收形（checkPrivacyPassword/ipping）。 */
    private boolean boolApi(Object r) {
        if (r instanceof Boolean) return (Boolean) r;
        if (r instanceof Number) return ((Number) r).doubleValue() != 0;
        if (r == null) return false;
        return "true".equals(String.valueOf(r));
    }

    /**
     * G21：8.83 JSEngine.addListener（smali:10505-10585）本体。
     * callbackKey 先 argsNativeObjectAdjust 后无条件强转 String（包装层恒覆写为
     * 本发 CALLBACK_KEY，undefined 到不了这里——补正件三定论）；查 callbackMap
     * 无回调静默回；event/listener 都 adjust 后同为 String 才 onUpdate 转发，
     * 函数形 listener（未经 plugin.js 串化直调）过不了双闸、静默空转，与官同形。
     * 旧形「只记 listeners 表不触发」整支摘除（该表全树无消费方）。
     */
    private void addListenerImpl(Object event, Object listener, Object callbackKey) {
        Object ck = argsNativeObjectAdjust(callbackKey);
        String key = (String) ck;
        JSEngineCallback<?> callback = callbackMap.get(key);
        if (callback == null) return;
        Object ev = argsNativeObjectAdjust(event);
        Object li = argsNativeObjectAdjust(listener);
        if (ev instanceof String && li instanceof String) {
            callback.onUpdate((String) ev, (String) li);
        }
    }

    @JSAnnotation(returnType = ReturnType.JSON)
    public Object _findItem(@Parameter(value = "o") Object param0) {
        return jsonApi(dispatchApi("_findItem", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.JSON)
    public Object _findItemsByCls(@Parameter(value = "o") Object param0) {
        return jsonApi(dispatchApi("_findItemsByCls", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String _pd(@Parameter(value = "o1") Object param0, @Parameter(value = "o2") Object param1, @Parameter(value = "o3") Object param2) {
        return strApi(dispatchApi("_pd", new Object[]{param0, param1, param2}));
    }

    @JSAnnotation(returnType = ReturnType.JSON)
    public String _pdfa(@Parameter(value = "o1") Object param0, @Parameter(value = "o2") Object param1) {
        return strApi(dispatchApi("_pdfa", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String _pdfh(@Parameter(value = "o1") Object param0, @Parameter(value = "o2") Object param1) {
        return strApi(dispatchApi("_pdfh", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.JSON)
    public String _pdfl(@Parameter(value = "o1") Object param0, @Parameter(value = "o2") Object param1, @Parameter(value = "o3") Object param2, @Parameter(value = "o4") Object param3, @Parameter(value = "o5") Object param4) {
        return strApi(dispatchApi("_pdfl", new Object[]{param0, param1, param2, param3, param4}));
    }

    @JSAnnotation()
    public void addItemAfter(@Parameter(value = "id") Object param0, @Parameter(value = "o") Object param1) {
        dispatchApi("addItemAfter", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void addItemBefore(@Parameter(value = "id") Object param0, @Parameter(value = "o") Object param1) {
        dispatchApi("addItemBefore", new Object[]{param0, param1});
    }

    @JSAnnotation(alias = "listen")
    public void addListener(@Parameter(value = "event") Object param0, @Parameter(value = "listener") Object param1, @Parameter(value = "callbackKey") Object param2) {
        dispatchApi("addListener", new Object[]{param0, param1, param2});
    }

    @JSAnnotation()
    public void addWebProxyRule(@Parameter(value = "c") Object param0) {
        dispatchApi("addWebProxyRule", new Object[]{param0});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String aesDecode(@Parameter(value = "o1") Object param0, @Parameter(value = "o2") Object param1) {
        return strApi(dispatchApi("aesDecode", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String aesEncode(@Parameter(value = "o1") Object param0, @Parameter(value = "o2") Object param1) {
        return strApi(dispatchApi("aesEncode", new Object[]{param0, param1}));
    }

    @JSAnnotation()
    public void back(@Parameter(value = "refreshPage") Object param0) {
        dispatchApi("back", new Object[]{param0});
    }

    @JSAnnotation()
    public void backToHome() {
        dispatchApi("backToHome", new Object[]{});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String base64Decode(@Parameter(value = "o1") Object param0) {
        return strApi(dispatchApi("base64Decode", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String base64Encode(@Parameter(value = "o1") Object param0) {
        return strApi(dispatchApi("base64Encode", new Object[]{param0}));
    }

    @JSAnnotation(alias = "bcm", returnType = ReturnType.JSON)
    public String batchCacheM3u8(@Parameter(value = "params") Object param0, @Parameter(value = "ruleKey") Object param1) {
        return strApi(dispatchApi("batchCacheM3u8", new Object[]{param0, param1}));
    }

    @JSAnnotation(alias = "be")
    public void batchExecute(@Parameter(value = "tasks") Object param0, @Parameter(value = "listener0") Object param1, @Parameter(value = "success", defaultInt = 0) Object param2, @Parameter(value = "ruleKey") Object param3) {
        dispatchApi("batchExecute", new Object[]{param0, param1, param2, param3});
    }

    @JSAnnotation(alias = "bf", returnType = ReturnType.JSON)
    public String batchFetch(@Parameter(value = "params") Object param0, @Parameter(value = "threadNum") Object param1, @Parameter(value = "ruleKey") Object param2) {
        return strApi(dispatchApi("batchFetch", new Object[]{param0, param1, param2}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String buildUrl(String param0, Object param1) {
        return strApi(dispatchApi("buildUrl", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.OBJECT)
    public Object buildWebDav(@Parameter(value = "url") String param0, @Parameter(value = "user") String param1, @Parameter(value = "pwd") String param2) {
        return dispatchApi("buildWebDav", new Object[]{param0, param1, param2});
    }

    @JSAnnotation()
    public void cacheCode0(@Parameter(value = "code") String param0) {
        dispatchApi("cacheCode0", new Object[]{param0});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String cacheM3u8(@Parameter(value = "url") String param0, @Parameter(value = "options") Object param1, @Parameter(value = "fileName", defaultValue = "a") String param2, @Parameter(value = "ruleKey") Object param3) {
        return strApi(dispatchApi("cacheM3u8", new Object[]{param0, param1, param2, param3}));
    }

    @JSAnnotation(returnType = ReturnType.BOOL)
    public boolean checkPrivacyPassword(@Parameter(value = "p") Object param0) {
        return boolApi(dispatchApi("checkPrivacyPassword", new Object[]{param0}));
    }

    @JSAnnotation()
    public void clearItem(@Parameter(value = "key") Object param0, @Parameter(value = "ruleTitleKey") Object param1) {
        dispatchApi("clearItem", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void clearMyVar(@Parameter(value = "o") Object param0, @Parameter(value = "ruleTitleKey") Object param1) {
        dispatchApi("clearMyVar", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void clearPublicItem(@Parameter(value = "key") Object param0) {
        dispatchApi("clearPublicItem", new Object[]{param0});
    }

    @JSAnnotation()
    public void clearVar(@Parameter(value = "o") Object param0) {
        dispatchApi("clearVar", new Object[]{param0});
    }

    @JSAnnotation()
    public void closeMe(@Parameter(value = "obj") Object param0) {
        dispatchApi("closeMe", new Object[]{param0});
    }

    @JSAnnotation()
    public void confirm(@Parameter(value = "ev") Object param0, @Parameter(value = "ruleTitleKey") Object param1) {
        dispatchApi("confirm", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void copy(@Parameter(value = "o") Object param0, @Parameter(value = "ruleTitleKey") Object param1) {
        dispatchApi("copy", new Object[]{param0, param1});
    }

    @JSAnnotation(returnType = ReturnType.Num)
    public int copyFiles(@Parameter(value = "p") Object param0, @Parameter(value = "p2") Object param1, @Parameter(value = "dirKey") Object param2) {
        return intApi(dispatchApi("copyFiles", new Object[]{param0, param1, param2}));
    }

    @JSAnnotation()
    public void createQRCode(@Parameter(value = "text") Object param0, Object param1) {
        dispatchApi("createQRCode", new Object[]{param0, param1});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String createQRCodeToFile(@Parameter(value = "text") Object param0) {
        return strApi(dispatchApi("createQRCodeToFile", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String decodeStr(@Parameter(value = "o1") Object param0, @Parameter(value = "o2", defaultValue = "UTF-8") Object param1) {
        return strApi(dispatchApi("decodeStr", new Object[]{param0, param1}));
    }

    @JSAnnotation()
    public void deleteCache(@Parameter(value = "c", defaultValue = "-1") Object param0, @Parameter(value = "ruleTitleKey") Object param1) {
        dispatchApi("deleteCache", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void deleteFile(@Parameter(value = "filePath") String param0, @Parameter(value = "ruleTitleKey") Object param1) {
        dispatchApi("deleteFile", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void deleteItem(@Parameter(value = "o") Object param0, @Parameter(value = "ruleKey") Object param1) {
        dispatchApi("deleteItem", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void deleteItemByCls(@Parameter(value = "o") Object param0) {
        dispatchApi("deleteItemByCls", new Object[]{param0});
    }

    @JSAnnotation()
    public void downloadFile(@Parameter(value = "u") String param0, @Parameter(value = "p") String param1, @Parameter(value = "headers") Object param2) {
        dispatchApi("downloadFile", new Object[]{param0, param1, param2});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String encodeStr(@Parameter(value = "o1") Object param0, @Parameter(value = "o2", defaultValue = "UTF-8") Object param1) {
        return strApi(dispatchApi("encodeStr", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.OBJECT)
    public Object evalPrivateJS(@Parameter(value = "c") Object param0, @Parameter(value = "evalKey") Object param1) {
        return dispatchApi("evalPrivateJS", new Object[]{param0, param1});
    }

    @JSAnnotation(alias = "ewr", returnType = ReturnType.STRING)
    public String executeWebRule(@Parameter(value = "url") String param0, @Parameter(value = "rule") String param1, @Parameter(value = "options") Object param2) {
        return strApi(dispatchApi("executeWebRule", new Object[]{param0, param1, param2}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public Object fetch(@Parameter(value = "url") String param0, @Parameter(value = "options") Object param1, @Parameter(value = "ruleKey") Object param2) {
        return dispatchApi("fetch", new Object[]{param0, param1, param2});
    }

    @JSAnnotation(alias = "fc", returnType = ReturnType.STRING)
    public String fetchCache(@Parameter(value = "c") Object param0, @Parameter(value = "h") Object param1, @Parameter(value = "options") Object param2, @Parameter(value = "v", defaultInt = 0) Object param3, @Parameter(value = "ruleTitleKey") Object param4, @Parameter(value = "evalKey") Object param5) {
        return strApi(dispatchApi("fetchCache", new Object[]{param0, param1, param2, param3, param4, param5}));
    }

    @JSAnnotation(alias = "fcbw", returnType = ReturnType.STRING)
    public String fetchCodeByWebView(@Parameter(value = "url") String param0, @Parameter(value = "options") Object param1) {
        return strApi(dispatchApi("fetchCodeByWebView", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String fetchCookie(@Parameter(value = "url") String param0, @Parameter(value = "options") Object param1, @Parameter(value = "ruleKey") Object param2) {
        return strApi(dispatchApi("fetchCookie", new Object[]{param0, param1, param2}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public Object fetchPC(@Parameter(value = "url") String param0, @Parameter(value = "options") Object param1, @Parameter(value = "ruleKey") Object param2) {
        return dispatchApi("fetchPC", new Object[]{param0, param1, param2});
    }

    @JSAnnotation(alias = "exist", returnType = ReturnType.BOOL)
    public String fileExist(@Parameter(value = "path") Object param0, @Parameter(value = "ruleTitleKey") Object param1) {
        return strApi(dispatchApi("fileExist", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.OBJECT)
    public Object findJavaClass(@Parameter(value = "p") String param0, @Parameter(value = "c") String param1, @Parameter(value = "so") String param2, @Parameter(value = "ruleTitleKey") Object param3) {
        return dispatchApi("findJavaClass", new Object[]{param0, param1, param2, param3});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String fixM3u8(@Parameter(value = "url") String param0, @Parameter(value = "content") String param1) {
        return strApi(dispatchApi("fixM3u8", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.Num)
    public String getAppVersion() {
        return strApi(dispatchApi("getAppVersion", new Object[]{}));
    }

    @JSAnnotation(returnType = ReturnType.JSON)
    public String getColTypes() {
        return strApi(dispatchApi("getColTypes", new Object[]{}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getCookie(@Parameter(value = "o") Object param0) {
        return strApi(dispatchApi("getCookie", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getCpuAbi() {
        return strApi(dispatchApi("getCpuAbi", new Object[]{}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getCryptoJS() {
        return strApi(dispatchApi("getCryptoJS", new Object[]{}));
    }

    @JSAnnotation(returnType = ReturnType.OBJECT)
    public Object getCurrentActivity(@Parameter(value = "ruleTitleKey") Object param0) {
        return dispatchApi("getCurrentActivity", new Object[]{param0});
    }

    @JSAnnotation(returnType = ReturnType.JSON)
    public String getEpubChapters(@Parameter(value = "path") Object param0) {
        return strApi(dispatchApi("getEpubChapters", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getEpubContent0(@Parameter(value = "path") Object param0, @Parameter(value = "url") Object param1) {
        return strApi(dispatchApi("getEpubContent0", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.JSON)
    public String getEpubMetadata(@Parameter(value = "path") Object param0) {
        return strApi(dispatchApi("getEpubMetadata", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getHome(@Parameter(value = "url") String param0, @Parameter(value = "urlKey") Object param1) {
        return strApi(dispatchApi("getHome", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.JSON)
    public String getHomeSub() {
        return strApi(dispatchApi("getHomeSub", new Object[]{}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getIP() {
        return strApi(dispatchApi("getIP", new Object[]{}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getItem(@Parameter(value = "key") Object param0, @Parameter(value = "def") Object param1, @Parameter(value = "ruleTitleKey") Object param2) {
        return strApi(dispatchApi("getItem", new Object[]{param0, param1, param2}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getJsLazyPlugin() {
        return strApi(dispatchApi("getJsLazyPlugin", new Object[]{}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getJsPlugin() {
        return strApi(dispatchApi("getJsPlugin", new Object[]{}));
    }

    @JSAnnotation(returnType = ReturnType.JSON)
    public String getLastRules(@Parameter(value = "c") Object param0) {
        return strApi(dispatchApi("getLastRules", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getMyVar(@Parameter(value = "o") Object param0, @Parameter(value = "defaultVal") Object param1, @Parameter(value = "ruleTitleKey") Object param2) {
        return strApi(dispatchApi("getMyVar", new Object[]{param0, param1, param2}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getPageTitle() {
        return strApi(dispatchApi("getPageTitle", new Object[]{}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public Object getParam(@Parameter(value = "key") Object param0, @Parameter(value = "defaultValue") Object param1, @Parameter(value = "urlKey") Object param2) {
        return dispatchApi("getParam", new Object[]{param0, param1, param2});
    }

    @JSAnnotation(returnType = ReturnType.JSON)
    public String getPastes() {
        return strApi(dispatchApi("getPastes", new Object[]{}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getPath(@Parameter(value = "filePath") String param0) {
        return strApi(dispatchApi("getPath", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.Num)
    public int getPrivacyPasswordLen() {
        return intApi(dispatchApi("getPrivacyPasswordLen", new Object[]{}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getPrivateJS(@Parameter(value = "c") Object param0) {
        return strApi(dispatchApi("getPrivateJS", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getPublicItem(@Parameter(value = "key") Object param0, @Parameter(value = "v") Object param1) {
        return strApi(dispatchApi("getPublicItem", new Object[]{param0, param1}));
    }

    @JSAnnotation(alias = "getCode", returnType = ReturnType.STRING)
    public String getResCode(@Parameter(value = "callbackKey") Object param0) {
        return strApi(dispatchApi("getResCode", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getRuleCount() {
        return strApi(dispatchApi("getRuleCount", new Object[]{}));
    }

    @JSAnnotation(returnType = ReturnType.Num)
    public String getSearchMode() {
        return strApi(dispatchApi("getSearchMode", new Object[]{}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getUrl(@Parameter(value = "urlKey") Object param0) {
        return strApi(dispatchApi("getUrl", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String getVar(@Parameter(value = "o") Object param0, @Parameter(value = "defaultVal") Object param1) {
        return strApi(dispatchApi("getVar", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.BOOL)
    public String hasHomeSub(@Parameter(value = "url") Object param0) {
        return strApi(dispatchApi("hasHomeSub", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String hexToBase64(@Parameter(value = "c") Object param0) {
        return strApi(dispatchApi("hexToBase64", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.OBJECT)
    public Object hexToBytes(@Parameter(value = "c") Object param0, @Parameter(value = "evalKey") Object param1) {
        return dispatchApi("hexToBytes", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void hideLoading() {
        dispatchApi("hideLoading", new Object[]{});
    }

    @JSAnnotation()
    public void initChaquopy(@Parameter(value = "u") Object param0) {
        dispatchApi("initChaquopy", new Object[]{param0});
    }

    @JSAnnotation(returnType = ReturnType.BOOL)
    public boolean ipping(@Parameter(value = "ip") Object param0, @Parameter(value = "h", defaultInt = 0) Object param1) {
        return boolApi(dispatchApi("ipping", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.BOOL)
    public String isLogin() {
        return strApi(dispatchApi("isLogin", new Object[]{}));
    }

    @JSAnnotation(returnType = ReturnType.BOOL)
    public String isShorthand(@Parameter(value = "u") Object param0) {
        return strApi(dispatchApi("isShorthand", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.BOOL)
    public String isVideoOrMusic(@Parameter(value = "u") Object param0) {
        return strApi(dispatchApi("isVideoOrMusic", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String joinUrl(@Parameter(value = "o1") Object param0, @Parameter(value = "o2") Object param1) {
        return strApi(dispatchApi("joinUrl", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String justTestSign(@Parameter(value = "appId") Object param0, @Parameter(value = "devId") Object param1, @Parameter(value = "userId") Object param2) {
        return strApi(dispatchApi("justTestSign", new Object[]{param0, param1, param2}));
    }

    @JSAnnotation(returnType = ReturnType.JSON)
    public String listMyVarKeys(@Parameter(value = "ruleTitleKey") Object param0) {
        return strApi(dispatchApi("listMyVarKeys", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.OBJECT)
    public Object loadJavaClass(@Parameter(value = "p") String param0, @Parameter(value = "c") String param1, @Parameter(value = "so") String param2, @Parameter(value = "ruleTitleKey") Object param3) {
        return dispatchApi("loadJavaClass", new Object[]{param0, param1, param2, param3});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String md5(@Parameter(value = "c") Object param0) {
        return strApi(dispatchApi("md5", new Object[]{param0}));
    }

    @JSAnnotation(returnType = ReturnType.BOOL)
    public String openAppIntent(@Parameter(value = "p") Object param0, @Parameter(value = "a") Object param1, @Parameter(value = "param") Object param2, @Parameter(value = "ruleTitleKey") Object param3) {
        return strApi(dispatchApi("openAppIntent", new Object[]{param0, param1, param2, param3}));
    }

    @JSAnnotation(alias = "pd", returnType = ReturnType.STRING)
    public String parseDom(@Parameter(value = "o1") Object param0, @Parameter(value = "o2") Object param1, @Parameter(value = "urlKey") Object param2) {
        return strApi(dispatchApi("parseDom", new Object[]{param0, param1, param2}));
    }

    @JSAnnotation(alias = "pdfa", returnType = ReturnType.JSON)
    public String parseDomForArray(@Parameter(value = "o1") Object param0, @Parameter(value = "o2") Object param1) {
        return strApi(dispatchApi("parseDomForArray", new Object[]{param0, param1}));
    }

    @JSAnnotation(alias = "pdfh", returnType = ReturnType.STRING)
    public String parseDomForHtml(@Parameter(value = "o1") Object param0, @Parameter(value = "o2") Object param1) {
        return strApi(dispatchApi("parseDomForHtml", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String parsePaste(@Parameter(value = "url") Object param0) {
        return strApi(dispatchApi("parsePaste", new Object[]{param0}));
    }

    @JSAnnotation()
    public String png2Ts(@Parameter(value = "c") Object param0, @Parameter(value = "headers") Object param1) {
        return strApi(dispatchApi("png2Ts", new Object[]{param0, param1}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public Object post(String param0, Object param1, Object param2) {
        return dispatchApi("post", new Object[]{param0, param1, param2});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public Object postPC(String param0, Object param1, Object param2) {
        return dispatchApi("postPC", new Object[]{param0, param1, param2});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public Object postRequest(String param0, Object param1, Object param2) {
        return dispatchApi("postRequest", new Object[]{param0, param1, param2});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String proxyClearM3u8(@Parameter(value = "u") Object param0, @Parameter(value = "h") Object param1, @Parameter(value = "r") Object param2) {
        return strApi(dispatchApi("proxyClearM3u8", new Object[]{param0, param1, param2}));
    }

    @JSAnnotation()
    public void publishRule(@Parameter(value = "c") Object param0, @Parameter(value = "ruleTitleKey") Object param1) {
        dispatchApi("publishRule", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void putMyVar(@Parameter(value = "o") Object param0, @Parameter(value = "o2") Object param1, @Parameter(value = "ruleTitleKey") Object param2) {
        dispatchApi("putMyVar", new Object[]{param0, param1, param2});
    }

    @JSAnnotation()
    public void putVar(@Parameter(value = "o") Object param0, @Parameter(value = "o2") Object param1) {
        dispatchApi("putVar", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void putVar2(@Parameter(value = "o1") Object param0, @Parameter(value = "o2") Object param1) {
        dispatchApi("putVar2", new Object[]{param0, param1});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String readFile(@Parameter(value = "filePath") String param0, @Parameter(value = "mode", defaultInt = 1) Object param1, @Parameter(value = "ruleTitleKey") Object param2) {
        return strApi(dispatchApi("readFile", new Object[]{param0, param1, param2}));
    }

    @JSAnnotation(alias = "refresh")
    public void refreshPage(@Parameter(value = "scrollTop") Object param0, @Parameter(value = "ruleTitleKey") Object param1) {
        dispatchApi("refreshPage", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void refreshReadData(@Parameter(value = "p") Object param0) {
        dispatchApi("refreshReadData", new Object[]{param0});
    }

    @JSAnnotation()
    public void refreshVideoSource(@Parameter(value = "url") Object param0, @Parameter(value = "headers") Object param1) {
        dispatchApi("refreshVideoSource", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void refreshVideoUrl(@Parameter(value = "c") Object param0, @Parameter(value = "r") Object param1) {
        dispatchApi("refreshVideoUrl", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void refreshWebProxyRule() {
        dispatchApi("refreshWebProxyRule", new Object[]{});
    }

    @JSAnnotation()
    public void refreshX5Desc(@Parameter(value = "desc") Object param0) {
        dispatchApi("refreshX5Desc", new Object[]{param0});
    }

    @JSAnnotation()
    public void refreshX5WebView(@Parameter(value = "url") Object param0) {
        dispatchApi("refreshX5WebView", new Object[]{param0});
    }

    @JSAnnotation()
    public void registerDNS(@Parameter(value = "options") Object param0, @Parameter(value = "ruleTitleKey") Object param1) {
        dispatchApi("registerDNS", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void registerTask(@Parameter(value = "id") Object param0, @Parameter(value = "time", defaultInt = 10000) Object param1, @Parameter(value = "js") Object param2) {
        dispatchApi("registerTask", new Object[]{param0, param1, param2});
    }

    @JSAnnotation()
    public void removeWebProxyRule(@Parameter(value = "c") Object param0) {
        dispatchApi("removeWebProxyRule", new Object[]{param0});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public Object request(@Parameter(value = "url") String param0, @Parameter(value = "options") Object param1, @Parameter(value = "ruleKey") Object param2) {
        return dispatchApi("request", new Object[]{param0, param1, param2});
    }

    @JSAnnotation(returnType = ReturnType.OBJECT)
    public Object require(@Parameter(value = "c") Object param0, @Parameter(value = "options") Object param1, @Parameter(value = "v", defaultInt = 0) Object param2, @Parameter(value = "ruleTitleKey") Object param3, @Parameter(value = "evalKey") Object param4) {
        return dispatchApi("require", new Object[]{param0, param1, param2, param3, param4});
    }

    @JSAnnotation(alias = "rc", returnType = ReturnType.OBJECT)
    public Object requireCache(@Parameter(value = "c") Object param0, @Parameter(value = "h") Object param1, @Parameter(value = "options") Object param2, @Parameter(value = "v", defaultInt = 0) Object param3, @Parameter(value = "ruleTitleKey") Object param4, @Parameter(value = "evalKey") Object param5) {
        return dispatchApi("requireCache", new Object[]{param0, param1, param2, param3, param4, param5});
    }

    @JSAnnotation()
    public void requireDownload(@Parameter(value = "u") String param0, @Parameter(value = "p") String param1, @Parameter(value = "headers") Object param2) {
        dispatchApi("requireDownload", new Object[]{param0, param1, param2});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String rsaDecrypt(@Parameter(value = "encryptBase64Data") Object param0, @Parameter(value = "key") Object param1, @Parameter(value = "options") Object param2) {
        return strApi(dispatchApi("rsaDecrypt", new Object[]{param0, param1, param2}));
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String rsaEncrypt(@Parameter(value = "data") Object param0, @Parameter(value = "key") Object param1, @Parameter(value = "options") Object param2) {
        return strApi(dispatchApi("rsaEncrypt", new Object[]{param0, param1, param2}));
    }

    @JSAnnotation()
    public void saveFile(@Parameter(value = "filePath") String param0, @Parameter(value = "content") String param1, @Parameter(value = "mode", defaultInt = 1) Object param2, @Parameter(value = "ruleTitleKey") Object param3) {
        dispatchApi("saveFile", new Object[]{param0, param1, param2, param3});
    }

    @JSAnnotation()
    public void saveImage(@Parameter(value = "url") String param0, @Parameter(value = "path") Object param1) {
        dispatchApi("saveImage", new Object[]{param0, param1});
    }

    @JSAnnotation(returnType = ReturnType.BOOL)
    public String searchContains(@Parameter(value = "text") Object param0, @Parameter(value = "key") Object param1, @Parameter(value = "ignoreCase") Object param2) {
        return strApi(dispatchApi("searchContains", new Object[]{param0, param1, param2}));
    }

    @JSAnnotation()
    public void setHomeResult(@Parameter(value = "o") Object param0, @Parameter(value = "callbackKey") Object param1, @Parameter(value = "ruleKey") Object param2, @Parameter(value = "typeKey") Object param3) {
        dispatchApi("setHomeResult", new Object[]{param0, param1, param2, param3});
    }

    @JSAnnotation()
    public void setItem(@Parameter(value = "key") Object param0, @Parameter(value = "v") Object param1, @Parameter(value = "ruleTitleKey") Object param2) {
        dispatchApi("setItem", new Object[]{param0, param1, param2});
    }

    @JSAnnotation()
    public void setLastChapterResult(@Parameter(value = "o") Object param0, @Parameter(value = "callbackKey") Object param1, @Parameter(value = "ruleKey") Object param2) {
        dispatchApi("setLastChapterResult", new Object[]{param0, param1, param2});
    }

    @JSAnnotation()
    public void setLastChapterRule(@Parameter(value = "rule") String param0) {
        dispatchApi("setLastChapterRule", new Object[]{param0});
    }

    @JSAnnotation()
    public void setPageParams(@Parameter(value = "t") Object param0) {
        dispatchApi("setPageParams", new Object[]{param0});
    }

    @JSAnnotation()
    public void setPagePicUrl(@Parameter(value = "title") String param0) {
        dispatchApi("setPagePicUrl", new Object[]{param0});
    }

    @JSAnnotation(alias = "refresh")
    public void setPageReverse(@Parameter(value = "reverse") Object param0) {
        dispatchApi("setPageReverse", new Object[]{param0});
    }

    @JSAnnotation()
    public void setPageTitle(@Parameter(value = "title") String param0) {
        dispatchApi("setPageTitle", new Object[]{param0});
    }

    @JSAnnotation()
    public void setPreResult(@Parameter(value = "o") Object param0, @Parameter(value = "callbackKey") Object param1, @Parameter(value = "ruleKey") Object param2, @Parameter(value = "typeKey") Object param3) {
        dispatchApi("setPreResult", new Object[]{param0, param1, param2, param3});
    }

    @JSAnnotation()
    public void setPublicItem(@Parameter(value = "key") Object param0, @Parameter(value = "v") Object param1) {
        dispatchApi("setPublicItem", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void setResult(@Parameter(value = "o") Object param0, @Parameter(value = "callbackKey") Object param1, @Parameter(value = "ruleKey") Object param2, @Parameter(value = "typeKey") Object param3) {
        dispatchApi("setResult", new Object[]{param0, param1, param2, param3});
    }

    @JSAnnotation()
    public void setSearchMode(@Parameter(value = "mode", defaultInt = 0) Object param0) {
        dispatchApi("setSearchMode", new Object[]{param0});
    }

    @JSAnnotation()
    public void setSearchResult(@Parameter(value = "o") Object param0, @Parameter(value = "callbackKey") Object param1, @Parameter(value = "ruleKey") Object param2, @Parameter(value = "typeKey") Object param3) {
        dispatchApi("setSearchResult", new Object[]{param0, param1, param2, param3});
    }

    @JSAnnotation()
    public void setStrResult(@Parameter(value = "o") Object param0, @Parameter(value = "callbackKey") Object param1, @Parameter(value = "ruleKey") Object param2) {
        dispatchApi("setStrResult", new Object[]{param0, param1, param2});
    }

    @JSAnnotation()
    public void shareDirectory(@Parameter(value = "p") Object param0, @Parameter(value = "p2") Object param1) {
        dispatchApi("shareDirectory", new Object[]{param0, param1});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String sharePaste(@Parameter(value = "content") Object param0, @Parameter(value = "paste") Object param1) {
        return strApi(dispatchApi("sharePaste", new Object[]{param0, param1}));
    }

    @JSAnnotation()
    public void showLoading(@Parameter(value = "o") Object param0, @Parameter(value = "ruleTitleKey") Object param1) {
        dispatchApi("showLoading", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void showSelectOptions(@Parameter(value = "ev") Object param0, @Parameter(value = "ruleTitleKey") Object param1) {
        dispatchApi("showSelectOptions", new Object[]{param0, param1});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String startProxyServer(@Parameter(value = "js") Object param0) {
        return strApi(dispatchApi("startProxyServer", new Object[]{param0}));
    }

    @JSAnnotation()
    public void startQRScanPage() {
        dispatchApi("startQRScanPage", new Object[]{});
    }

    @JSAnnotation(returnType = ReturnType.STRING)
    public String toCorrectJSONString(@Parameter(value = "text") Object param0) {
        return strApi(dispatchApi("toCorrectJSONString", new Object[]{param0}));
    }

    @JSAnnotation()
    public void toast(@Parameter(value = "str") Object param0, @Parameter(value = "ruleTitleKey") Object param1) {
        dispatchApi("toast", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void unRegisterTask(@Parameter(value = "id") Object param0) {
        dispatchApi("unRegisterTask", new Object[]{param0});
    }

    @JSAnnotation()
    public void updateItem(@Parameter(value = "id") Object param0, @Parameter(value = "o") Object param1) {
        dispatchApi("updateItem", new Object[]{param0, param1});
    }

    @JSAnnotation()
    public void writeFile(@Parameter(value = "filePath") String param0, @Parameter(value = "content") String param1, @Parameter(value = "mode", defaultInt = 1) Object param2) {
        dispatchApi("writeFile", new Object[]{param0, param1, param2});
    }

    @JSAnnotation()
    public void writeHexFile(@Parameter(value = "filePath") String param0, @Parameter(value = "content") String param1) {
        dispatchApi("writeHexFile", new Object[]{param0, param1});
    }

    @JSAnnotation(returnType = ReturnType.OBJECT)
    public Object xpath(@Parameter(value = "html") Object param0, @Parameter(value = "exp") String param1, @Parameter(value = "type", defaultValue = "string") String param2) {
        return dispatchApi("xpath", new Object[]{param0, param1, param2});
    }

    @JSAnnotation(alias = "xpa", returnType = ReturnType.JSON)
    public String xpathArray(@Parameter(value = "html") String param0, @Parameter(value = "exp") String param1) {
        return strApi(dispatchApi("xpathArray", new Object[]{param0, param1}));
    }
    /**
     * 在当发求值作用域里求值库代码（8.83 require0 useCallback=true 同款：回调以
     * 当发求值 scope 为 parentScope 调 Function.call，库代码与当发脚本同 scope；
     * 规则侧顶层 let/var 声明对库可见）。
     */
    /** 求值失败留痕的认领凭据：本线程已记过账的根 Rhino 异常（嵌套求值只记最内层一笔）。 */
    private final ThreadLocal<Throwable> evalLoggedFailure = new ThreadLocal<>();
    /** 留痕认领的生命周期：进入任一已挂留痕的求值口 +1，最外层退出时连认领一并清。 */
    private final ThreadLocal<Integer> evalLogDepth = ThreadLocal.withInitial(() -> 0);

    private void enterEvalLog() {
        evalLogDepth.set(evalLogDepth.get() + 1);
    }

    private void exitEvalLog() {
        int d = evalLogDepth.get() - 1;
        if (d <= 0) {
            evalLogDepth.remove();
            evalLoggedFailure.remove();
        } else {
            evalLogDepth.set(d);
        }
    }

    /**
     * 求值失败留痕（2026-11-12 他亲令）：规则代码在手机上拼出来跑，出错时此前
     * 只留一句报错、拼出的原文没留，下次再犯对不上行号。故在各求值口的失败
     * 出口，把规则名＋出错行号＋拼装原文出错行前后各一段记进 hk 通道（随现行
     * 日志导出走）。只在失败时记，平时零输出。覆盖面＝全部规则码求值口：
     * evalJsInScope／evalJsRooted／evalIntoScope 三咽喉、转录 evalJS 三形与
     * runScript、图片码求值（evalImageJs 的 code 支）。边界如实：与主串同 try
     * 的宿主预载评入（allFunctions 等）失败落同一 catch 时，摘录仍记主串——
     * 其 asmLine 多半越出 totalLines，离线可辨；经 evalIntoScope 评的宿主资产
     * 串失败也在此面（多记不漏记）。摘录总量约 6000 字封顶（单行截 400 字、
     * 累计 5600 停）。
     * 行号双口径并记：asmLine＝异常在实际递给 Rhino 的拼装串里的行号，摘录即
     * 按此行序截取、离线拿拼装串可直接对上；srcLine＝asmLine 减去拼装时在规则
     * 源码前多加的行数（lineShift：经 appendLineVar 的口 +1、再经 getReplaceJS
     * 非 raw 包裹的口再 +1），与 setError 校正后的源码行号同口径。嵌套求值按
     * 根异常对象认领：最内层记一笔，外层重抛不重复记；认领在日志真写出后才
     * 落置、随最外层求值口退出即清（enterEvalLog/exitEvalLog），不跨发残留。
     * 回值＝本口是否真记了一笔（终局 catch 清认领时判用）。
     */
    private boolean logEvalFailure(String evaluated, Throwable e, int lineShift) {
        try {
            org.mozilla.javascript.RhinoException rhino = null;
            Throwable cur = e;
            while (cur != null) {
                if (cur instanceof org.mozilla.javascript.RhinoException) {
                    rhino = (org.mozilla.javascript.RhinoException) cur;
                    break;
                }
                cur = cur.getCause();
            }
            if (rhino == null) return false;
            if (evalLoggedFailure.get() == rhino) return false;
            int asmLine = rhino.lineNumber();
            String[] lines = (evaluated == null ? "" : evaluated).split("\n", -1);
            int center = asmLine >= 1 && asmLine <= lines.length ? asmLine : 1;
            int from = Math.max(1, center - 12);
            int to = Math.min(lines.length, center + 12);
            StringBuilder sb = new StringBuilder();
            for (int i = from; i <= to && sb.length() < 5600; i++) {
                String l = lines[i - 1];
                if (l.length() > 400) l = l.substring(0, 400) + "…";
                sb.append(i).append(':').append(l).append('\n');
            }
            com.github.catvod.crawler.SpiderDebug.log("hk",
                    "eval-fail rule=%s asmLine=%d srcLine=%d totalLines=%d\n%s",
                    rule != null && rule.getTitle() != null ? rule.getTitle() : "",
                    asmLine, asmLine > 0 ? asmLine - lineShift : asmLine,
                    lines.length, sb.toString());
            evalLoggedFailure.set(rhino);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object evalJsInScope(Context cx, Scriptable callerScope, String js, String sourceName) {
        // 落点对官（$ 未定义考卷，账 hiker-dollar-undefined-receipt-20261111）：
        // 官 require0 回调以「当发求值 scope」为 parentScope（require0 :goto_0），
        // $ 等预置全局正是注入在那一层（getMyRule 尾 eval 双插件与当发脚本同
        // scope）。咱家旧形直吃 callerScope：parseList 里裸 require 经 plugin.js
        // 的 windows.require 包装转手时，BaseFunction 拿到的是包装函数的瞬时
        // NativeCall 激活对象——库顶层声明落激活即丢（2026-11-10 同规则
        // 「yiji 未定义」hk#8 实证），存活下来的函数捕获的也是激活链、与当发
        // 注入层错位（2026-11-11 「$ 未定义」hk-plugin#16）。改：落点收当发
        // 求值 scope——ambientEvalScope 有登记（主路三咽喉＋转录段各入口均
        // 登记、栈式恢复）以登记为准；无登记时沿 NativeCall 激活链上溯到首个
        // 真 var scope。求值名、deadline、mark 语义不动。
        Scriptable target = callerScope;
        Scriptable amb = ambientEvalScope.get();
        if (amb != null) {
            target = amb;
        } else if (target != null) {
            while (target instanceof org.mozilla.javascript.NativeCall) {
                Scriptable parent = target.getParentScope();
                if (parent == null) break;
                target = parent;
            }
        }
        Object oldDeadline = armEvalDeadline();
        SourceGate.Mark prevMark = swapHkMark();
        Scriptable prevAmb = enterAmbientScope(target);
        enterEvalLog();
        try {
            return cx.evaluateString(target, js, sourceName, 1, null);
        } catch (Throwable e) {
            logEvalFailure(js, e, 0);
            throw e;
        } finally {
            exitEvalLog();
            exitAmbientScope(prevAmb);
            restoreMark(prevMark);
            restoreEvalDeadline(oldDeadline);
        }
    }

    /** B#6：appendLineVar 产物首行 "const MY_PRE_LINE = <n>;" 里的 n（无前置声明回 -1）。 */
    private static int preLineOf(String prepared) {
        String head = "const MY_PRE_LINE = ";
        if (prepared == null || !prepared.startsWith(head)) return -1;
        int end = prepared.indexOf(';', head.length());
        if (end < 0) return -1;
        try {
            return Integer.parseInt(prepared.substring(head.length(), end).trim());
        } catch (Throwable e) {
            return -1;
        }
    }

    /** 求值（替代 ctx.evaluate）。每次用新 scope，避免规则顶层 const 污染共享 scope。 */
    // 按源定向打点：JS 求值期间，本线程发出的请求归属当前规则（kind=hk、id=规则名），
    // HkHttp 的选择器链会据此查 SourceProxyPolicy。求值可嵌套，进出按栈恢复。
    private SourceGate.Mark swapHkMark() {
        SourceGate.Mark prev = SourceGate.current();
        if (rule != null) {
            SourceGate.mark("hk", rule.getTitle());
            // 规则主域名先登记认领（与阅读共用认领表）：本规则后续在
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
        return evalJsRooted(js, scope);
    }

    /**
     * evalJs 的指定根 scope 形（环19 复核订正2）：常规求值根＝当前发 scope；
     * 字符串形定时器/registerTask 回调在登记时捕获登记发 scope、触发时以
     * 捕获 scope 为根求值——登记发的状态（$ 的 Module._cache、var/let 顶层
     * 声明）随捕获 scope 活着，不受其后各发 arm 换 scope 影响。函数形回调
     * 不走此路（Rhino 函数自带定义 scope，见 fireTimerCallback）。
     */
    private Object evalJsRooted(String js, Scriptable root) {
        if (root == null) root = scope;
        // G24：根 scope 求值的唯一咽喉——首调先把 plugin.js 评入
        // 根 scope（裸名 storage0 恒可达，见 ensurePluginIn）。
        ensurePluginIn(root);
        // 新 scope 以根 scope 为原型，API 函数经原型链可访问，但顶层 const/let 声明隔离在新 scope 内
        Scriptable newScope = rhinoCx.newObject(root);
        newScope.setPrototype(root);
        newScope.setParentScope(null);
        // B#6：官 runScript/evalJS 每发正文先经 appendLineVar 前置
        // "const MY_PRE_LINE = <前缀行数>;"——setError 经 evalKey（包装层覆写
        // 恒为 eval 函数）的父 scope 读它还原真实行号。主路此前直评、无此段，
        // MY_PRE_LINE 恒缺、行号还原恒落空。官每发新 scope、const 落本发
        // scope 即 eval 的父 scope；咱家 eval 的父 scope 为根 scope，故同一
        // 数值改挂根 scope 属性承载（子 scope 读它经原型链命中，
        // 与官同值）。求值串首行不用 const 而用同名赋值行保住 +1 行偏移
        // （setErrorImpl 的行号校准按含首行的行数算）：同名 const 在子 scope
        // 声明会撞主 scope 已有属性——Rhino 的 const 重声明检查连原型链一起
        // 查、整发 TypeError，离线夹具已实证。
        String prepared = appendLineVar(js);
        int preLine = preLineOf(prepared);
        // 环20 A（A-#5 残存）：MY_PRE_LINE 发内保存/恢复——本咽喉是全主路
        // 唯一求值口，嵌套片段（find_rule 内 require 后再 evalJs、选择器
        // .js: 经 eval→evalJs 等）每次都覆写根值，旧形跑完停在最内层片段
        // 的 count，外层正文其后再 setError 时按内层 count 错修正行号。
        // 官每发 const 落各自 scope、嵌套 evalJS 另 scope 不动外层值；
        // 咱以求值前存旧值、求值后恢复（原本无值则摘除）同果。
        Object prevPreLine = root != null
                ? ScriptableObject.getProperty(root, "MY_PRE_LINE") : Scriptable.NOT_FOUND;
        boolean preLineWritten = false;
        if (preLine >= 0 && root != null) {
            ScriptableObject.putProperty(root, "MY_PRE_LINE", preLine);
            prepared = "MY_PRE_LINE = " + preLine + ";\n" + js;
            preLineWritten = true;
        }
        Object oldDeadline = armEvalDeadline();
        SourceGate.Mark prevMark = swapHkMark();
        Scriptable prevAmb = enterAmbientScope(newScope);
        enterEvalLog();
        try {
            return rhinoCx.evaluateString(newScope, prepared, "hk", 1, null);
        } catch (Throwable e) {
            logEvalFailure(prepared, e, 1);
            throw e;
        } finally {
            exitEvalLog();
            if (preLineWritten && root != null) {
                try {
                    if (prevPreLine == Scriptable.NOT_FOUND) {
                        if (root instanceof ScriptableObject) {
                            ((ScriptableObject) root).delete("MY_PRE_LINE");
                        }
                    } else {
                        ScriptableObject.putProperty(root, "MY_PRE_LINE", prevPreLine);
                    }
                } catch (Throwable ignored) {
                }
            }
            exitAmbientScope(prevAmb);
            // 【不变量·不许在此清 Module._cache】环19 前此注记名「缓存须跨求值
            // 活着、生命周期＝运行时」——那是常驻主 scope 模型的配套口径；环19
            // 迁移后缓存生命周期＝本发 scope（官每发鲜 scope 同形：Module._cache
            // 随发新建、随发消亡，跨发状态走 _globalMap/varMap/文件官道）。
            // 本注的不变量只剩一条仍成立：不许在 evalJs 中途清缓存——同一发内
            // find_rule→require→页模块多次 evalJs 共用本发 $ 的 Module._cache，
            // 中途清＝本发数据全丢（1.4.46 实证的教训面在本发内继续有效）。
            restoreMark(prevMark);
            restoreEvalDeadline(oldDeadline);
        }
    }

    /**
     * 环24-11 簇二：MY_RULE≡null 等价 scope（官 registerTask 回调形——
     * SchedulePool 以单例引擎 evalJS 求值、前缀 getMyRule(null) 落
     * 「const MY_RULE = JSON.parse('null')」）。以基底 scope 为原型建一层
     * 薄 scope、自有 MY_RULE 属性置 JS null：JS 变量读与 getProperty 都先
     * 命中此层（本机 Rhino 探针实证 null 在场遮蔽原型值），API 面经原型链
     * 仍全在；evalJsRooted 再在其上建当发子 scope 并登记 ambient，
     * myRuleObjFromScope 读到在场 null 即权威（不回落宿主规则），回调内
     * fetch 取页基底恒为 null 规则、恒取不到——官形本身。
     */
    private Scriptable nullRuleScope(Scriptable base) {
        Scriptable b = base != null ? base : scope;
        if (b == null || rhinoCx == null) return b;
        Scriptable s = rhinoCx.newObject(b);
        s.setPrototype(b);
        s.setParentScope(null);
        ScriptableObject.putProperty(s, "MY_RULE", null);
        return s;
    }

    /** 在共享 scope 上直接求值（用于插件 JS 如 Hikerurl.js，需将 $ 等挂到共享 scope 供规则经原型链访问）。 */
    private Object evalJsInSharedScope(String js) {
        return evalIntoScope(scope, js, "hk-plugin");
    }

    /** 在指定 scope 上直接求值（装配/插件评入用）：deadline、源定向标记、ambient 登记与 evalJsInSharedScope 同式。 */
    private Object evalIntoScope(Scriptable target, String js, String sourceName) {
        Object oldDeadline = armEvalDeadline();
        SourceGate.Mark prevMark = swapHkMark();
        Scriptable prevAmb = enterAmbientScope(target);
        enterEvalLog();
        try {
            return rhinoCx.evaluateString(target, js, sourceName, 1, null);
        } catch (Throwable e) {
            logEvalFailure(js, e, 0);
            throw e;
        } finally {
            exitEvalLog();
            exitAmbientScope(prevAmb);
            restoreMark(prevMark);
            restoreEvalDeadline(oldDeadline);
        }
    }

    /**
     * 环19 对官装配（8.83 JSEngine.runScript 每发装配形）：新建鲜 scope——
     * initStandardObjects＋ImporterTopLevel.init(cx, scope, false) 静态形
     * （importer 契约面＝{JavaImporter, Packages, getClass, JavaAdapter}，
     * 无自由 importClass/importPackage，与官鲜 scope 探针同形）＋整套 API
     * 注册＋_globalMap（与图片 scope 共用同一张进程表 imageGlobalMap，跨发
     * 官道）/rc4/javaContext/javaLoader＋initRequire（类级单例＋单基址）＋
     * 每发评 allFunctions、CryptoJS、RequireUtils 嫁接段、Hikerurl.js。
     * 自造别名 globalMap0 随本批退役、不进鲜 scope（官只有 _globalMap）；
     * _base64 由 registerApi 装 HkMyBase64（记名适配，与官 put 名同物异）。
     * 调用点＝各自主路入口的 arm 处（parseListInternal/parseSearch/
     * evalLazy/详情/选择器独立发），一发一 scope：var/let、$.exports、
     * Module._cache 不再跨发残留，跨发状态只许走 _globalMap/varMap/文件。
     * 须在 JS 线程调用（init 的 submit 内或各入口的任务内）。
     */
    private ScriptableObject assembleFreshScope() {
        ScriptableObject fresh = rhinoCx.initStandardObjects();
        org.mozilla.javascript.ImporterTopLevel.init(rhinoCx, fresh, false);
        registerApiInto(fresh);
        ScriptableObject.putProperty(fresh, "_globalMap",
                Context.javaToJS(imageGlobalMap, fresh));
        ScriptableObject.putProperty(fresh, "rc4",
                Context.javaToJS(rc4, fresh));
        ScriptableObject.putProperty(fresh, "javaContext",
                Context.javaToJS(this, fresh));
        ScriptableObject.putProperty(fresh, "javaLoader",
                Context.javaToJS(HkJsRuntime.class.getClassLoader(), fresh));
        // 环19 复核订正6：装配段失败即本发失败（官装配失败同形）——
        // initRequire/CryptoJS/RequireUtils 嫁接/Hikerurl 任一评入失败或资产
        // 缺失都直接抛出，不许静默缺 requirejs 或 $ 继续跑本发。
        initRequire(rhinoCx, fresh);
        try { installJavaAdapterShim(fresh); } catch (Throwable t) { Logger.t(TAG).d("JavaAdapterShim fresh: " + t.getMessage()); }
        // CALLBACK_KEY 先声明为 Undefined（无键缺省），arm 时以真键覆写——
        // 包装函数的 callbackKey 覆写读定义 scope 的此名（官每发同体）。
        ScriptableObject.putProperty(fresh, "CALLBACK_KEY", Context.getUndefinedValue());
        // MY_TICKET 同须装配先置（1.4.124 回归订正）：Hikerurl.js 顶层
        // IIFE 求值期即调 getMyVar/clearMyVar，两者的 ruleTitleKey 包装
        // 首行读 MY_TICKET；官 runScript 单串求值、同串 const 声明在
        // 脚本实例化时先建绑定（读在声明行前回 undefined、不抛），咱家
        // 分开发评且装配时 setContext 还没跑（arm 序＝assemble→
        // setContext、init 更是无 setContext），鲜 scope 里此名无绑定
        // 即 ReferenceError、整台 init 失败、全部规则打不开。照
        // setContext 同式先置普通属性：generateTicket 按标题缓存、
        // 与 setContext 后置的是同一个值，覆写无冲突。带 const 前缀
        // 的 port runScript/evalJS 三口评的是脚本串本身、禁预置（预置
        // 普通属性后再评 const 同名声明，Rhino 抛 TypeError:
        // redeclaration of var MY_TICKET，实测），那几口不动。
        String asmTicket = generateTicket(rule != null ? rule.getTitle() : null);
        ScriptableObject.putProperty(fresh, "MY_TICKET", asmTicket == null ? "" : asmTicket);
        if (allFunctions != null && !allFunctions.isEmpty()) {
            evalIntoScope(fresh, allFunctions, "hk-allfunctions");
        }
        String cryptoJs = loadAsset("aes.js");
        if (TextUtils.isEmpty(cryptoJs)) throw new IllegalStateException("assembleFreshScope: aes.js asset missing");
        evalIntoScope(fresh, cryptoJs, "hk-plugin");
        evalIntoScope(fresh,
            "var com = (typeof com !== 'undefined') ? com : {};\n" +
            "com.example = com.example || {};\n" +
            "com.example.hikerview = com.example.hikerview || {};\n" +
            "com.example.hikerview.ui = com.example.hikerview.ui || {};\n" +
            "com.example.hikerview.ui.rules = com.example.hikerview.ui.rules || {};\n" +
            "com.example.hikerview.ui.rules.service = com.example.hikerview.ui.rules.service || {};\n" +
            "com.example.hikerview.ui.rules.service.require = com.example.hikerview.ui.rules.service.require || {};\n" +
            "com.example.hikerview.ui.rules.service.require.RequireUtils = " +
            "com.example.hikerview.ui.rules.service.require.RequireUtils || { generateRequireMap: function() {} };\n",
            "hk-plugin");
        String hikerUrlJs = loadAsset("Hikerurl.js");
        if (TextUtils.isEmpty(hikerUrlJs)) throw new IllegalStateException("assembleFreshScope: Hikerurl.js asset missing");
        evalIntoScope(fresh, hikerUrlJs, "hk-plugin");
        // config.<规则设置> 是规则面可见状态：旧常驻 scope 由 init 的 loadConfig
        // 挂入，鲜 scope 每发同挂当前绑定规则的那份（文件为跨发官道、值随发读）。
        loadConfigInto(fresh);
        return fresh;
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
        // 环19 复核订正2：登记时捕获登记发 scope，字符串回调触发时在捕获
        // scope 上求值（其后各发 arm 换 scope 不影响本回调的可见状态）。
        final Scriptable regScope = scope;
        Runnable fire = () -> {
            try {
                submit(() -> {
                    try {
                        if (destroyed) return null;
                        synchronized (jsTimers) {
                            if (!jsTimers.containsKey(id)) return null;
                            if (!repeat) jsTimers.remove(id);
                        }
                        fireTimerCallback(cb, cbArgs, regScope);
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

    /**
     * 在 JS 线程上执行定时器回调：Function 直接调用（Rhino 函数自带其定义
     * scope 为父 scope，维持原路）；字符串在登记发捕获 scope 上求值
     * （环19 复核订正2）。
     */
    private void fireTimerCallback(Object cb, Object[] cbArgs, Scriptable regScope) {
        try {
            if (cb instanceof Function) {
                // 环24-11 簇二（函数形）：旧形全程不登记 ambient，回调内
                // fetch 的取页基底回落触发时刻字段 scope（触发时宿主规则），
                // 与回调 JS 可见 MY_RULE（定义发的值）跨发分叉。改：触发时
                // 先以函数定义 scope 登记 ambient 再调用（登记/恢复形与
                // evalJsInScope 的 ambient 优先形同款），调用本体不变
                // （Rhino 函数自带定义 scope 链）。
                Function fn = (Function) cb;
                Scriptable defScope = fn.getParentScope();
                Scriptable prevAmb = enterAmbientScope(defScope);
                try {
                    fn.call(rhinoCx, scope, scope, cbArgs);
                } finally {
                    exitAmbientScope(prevAmb);
                }
            } else {
                String code = argStr(cb);
                if (!TextUtils.isEmpty(code)) evalJsRooted(code, regScope);
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

    /** MY_RULE 序列化排除集：官 JSONPreFilter.getSimpleFilter（filter0 十三名＋storage，共十四名）。 */
    private static final String[] MY_RULE_FILTER_EXCLUDES = {
            "associatedModelsMapForJoinTable", "associatedModelsMapWithFK",
            "associatedModelsMapWithoutFK", "fieldsToSetToDefault",
            "listToClearAssociatedFK", "listToClearSelfFK", "saved", "order",
            "id", "gmtCreate", "gmtModified", "lastUseTime", "displayName", "storage"};

    /** Gson 树过 simple 滤镜（排除集十四名整集，见 MY_RULE_FILTER_EXCLUDES）。 */
    private static com.google.gson.JsonObject filteredRuleTree(Object o) {
        com.google.gson.JsonElement el = GSON.toJsonTree(o);
        if (!el.isJsonObject()) return new com.google.gson.JsonObject();
        com.google.gson.JsonObject obj = el.getAsJsonObject();
        for (String k : MY_RULE_FILTER_EXCLUDES) obj.remove(k);
        return obj;
    }

    /**
     * MY_RULE 注入用序列化（G40）：官 getMyRule 以 JSONPreFilter.getSimpleFilter
     * 序列化规则对象（官包 JSONPreFilter.smali:16-273）；咱此前 GSON 直出未过滤。
     * HkRule 当前实际会带出的只有 displayName/storage 两键，排除集仍照官十四名
     * 整集落——模型日后加同名字段自动跟随，不靠逐键点名。
     * 环20 A：pageList 与 version 两键按官 ArticleListRule 实体形补齐——
     * pageList 是 getPageList() 派生键、simple 滤镜不排除、恒出（空亦 []）；
     * version 是 int 字段、恒数值出。
     */
    private static String myRuleJson(HkRule r) {
        if (r == null) return "null";
        com.google.gson.JsonObject obj = filteredRuleTree(r);
        // version 数值形：官实体 version 为 int（ArticleListRule.smali:268），
        // 咱 HkRule 以串收（VersionAdapter），此口收成数值——非纯整数串按
        // HkRule.getVersion() 的收值规归 0（官 int 字段收值同果）。
        obj.addProperty("version", r.getVersion());
        // pageList 派生（官 ArticleListRule.getPageList）：pages 非空且以 "["
        // 开头才 parseArray，余/异常恒回空数组；元素经 fastjson 出键恒为
        // {col_type,name,parent,path,rule} 的非空子集（params/parentRule
        // 带 serialize=false 不出，与环10 fetch page 窄键集同一份映射）。
        // parent 补回（复核订正）：官 ArticleListPageRule.parent 是普通字段
        //（ArticleListPageRule.smali:30 无 serialize 注解，仅 params 与
        // parentRule 带 serialize=false），fastjson 解析 parent 进值、出键；
        // 咱 HkPage.parent 是 transient，Gson 解析时被跳过，getPageList 派生
        // 的元素恒缺此键——故在此派生口以原始 pages JSON 为源、按下标对位
        // 补回：元素有 parent 才出、值原样，null 不出（官 fastjson null 不
        // 出同果）。HkPage.parent 的 transient 归属不动（getPageList 其余
        // 消费面未核）。
        com.google.gson.JsonArray pageList = new com.google.gson.JsonArray();
        String pages = r.getPages();
        if (pages != null && !pages.isEmpty() && pages.startsWith("[")) {
            com.google.gson.JsonArray rawPages = null;
            try {
                com.google.gson.JsonElement rawRoot =
                        com.google.gson.JsonParser.parseString(pages);
                if (rawRoot != null && rawRoot.isJsonArray()) {
                    rawPages = rawRoot.getAsJsonArray();
                }
            } catch (Throwable ignored) {
            }
            try {
                int idx = 0;
                for (HkPageParser.HkPage p : HkPageParser.getPageList(r)) {
                    com.google.gson.JsonElement rawEl = rawPages != null && idx < rawPages.size()
                            ? rawPages.get(idx) : null;
                    idx++;
                    if (p == null) {
                        pageList.add(com.google.gson.JsonNull.INSTANCE);
                        continue;
                    }
                    com.google.gson.JsonObject el = new com.google.gson.JsonObject();
                    if (p.getCol_type() != null) el.addProperty("col_type", p.getCol_type());
                    if (p.getName() != null) el.addProperty("name", p.getName());
                    com.google.gson.JsonElement parentEl = rawEl != null && rawEl.isJsonObject()
                            ? rawEl.getAsJsonObject().get("parent") : null;
                    if (parentEl != null && !parentEl.isJsonNull()) {
                        el.add("parent", parentEl);
                    } else if (p.getParent() != null) {
                        el.addProperty("parent", p.getParent());
                    }
                    if (p.getPath() != null) el.addProperty("path", p.getPath());
                    if (p.getRule() != null) el.addProperty("rule", p.getRule());
                    pageList.add(el);
                }
            } catch (Throwable ignored) {
                pageList = new com.google.gson.JsonArray();
            }
        }
        obj.add("pageList", pageList);
        return GSON.toJson(obj);
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

    /** List<?>（元素可为 null）-> JS 数组：batchFetch 缺位项保 null 用（G8）。 */
    private NativeArray toJsArrayObj(List<?> items) {
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

    /**
     * 8.83 JSEngine.updateTraceLog（JE:26805）：traceLog 字段自 BigTextDO.getTraceLog()
     * 同步，构造时调用。咱家 BigTextDO 语义面以 SharedPreferences "hiker" 承接
     * （与 getSearchMode 的 smode 同文件先例），读 "trace_log" 键、"true" 为开；
     * 咱家无写此键的设置面（官为设置页开关），缺省关与官缺省一致（第十阶段 G34 记名）。
     */
    public void updateTraceLog() {
        try {
            String v = App.get().getSharedPreferences("hiker", android.content.Context.MODE_PRIVATE)
                    .getString("trace_log", "");
            traceLog = "true".equals(v);
        } catch (Throwable e) {
            traceLog = false;
        }
    }

    /** log/xlog 共用的打日志实现（1.3.42 聚阅案：xlog 是宿主全局别名）。
     * 8.83 JSEngine.log（JE:19819）首句查 traceLog、假即 return——照官加闸（G34）；
     * 闸开时出口仍走宿主 Logger（官为 trace 文件面，咱家无此面，记名）。 */
    private void logImpl(Object[] args) {
        if (!traceLog) return;
        Object o = args != null && args.length > 0 ? args[0] : null;
        Logger.t(TAG).d("%s", argStr(o));
        // 8.83 log 同步落 logs 表（JE:19819 后段）：time＝HH:mm:ss.SSS（官 SimpleDateFormat
        // 无 Locale）、msg＝字符串直用余 JSON 串化、ruleKey 有 title 时三段式，满 10000 丢首。
        // 串化走 stringifyValue（Map/List→GSON、Scriptable→NativeJSON）：官 fastjson 对
        // Map/List 出真 JSON，jsStringify 对 Java Map/List 只回 "null"（复核实测订正）。
        // title 只认 Map 一形（官只认 JSONObject，adjust 后 Map 即其同位形）；官本体无
        // try/catch，串化/取 title 抛错即穿出，此处同不吞（复核订正）。
        Object res = argsNativeObjectAdjust(o);
        Object rule = args != null && args.length > 1 ? argsNativeObjectAdjust(args[1]) : null;
        String time = new java.text.SimpleDateFormat("HH:mm:ss.SSS")
                .format(new java.util.Date());
        String msg = res instanceof String ? (String) res : stringifyValue(res);
        String title = null;
        if (rule instanceof Map) {
            Object t = ((Map) rule).get("title");
            if (t != null) title = String.valueOf(t);
        }
        String line = (rule == null || isUndefined(rule) || title == null || title.isEmpty())
                ? String.format("%s: %s", time, msg)
                : String.format("%s: %s: %s", time, title, msg);
        synchronized (logs) {
            if (logs.size() >= 10000) logs.remove(0);
            logs.add(line);
        }
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
        return (String) fetchSync(url, null);
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
     * 对等原文 id 解析：id = id2 || id || (存储封装.getMyVar('二级源接口信息')
     * || 存储封装.getMyVar('一级源接口信息')).id。
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
            Object v = jsonGetByKey(m, myVarKeyFor("二级源接口信息", null), new Object[0]);
            if (v == null || "".equals(v)) {
                v = jsonGetByKey(m, myVarKeyFor("一级源接口信息", null), new Object[0]);
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
     * 读写走 fetch/writeFile（hiker:// 语义与原文一致），id 解析走存储封装的 getMyVar
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

    /**
     * 环43（批C3，复核订正）：8.83 findJavaClass/loadJavaClass 两入口的 c 回落
     * （smali 16049 .line 3287／19007 .line 3260-3262 逐支对位）后调
     * loadJavaInstance(p, c, so, Z)：findJavaClass 以 Z=1 调（回 Class 本身）、
     * loadJavaClass 以 Z=0 调（回实例）。c 回落分入口——find 侧：c 空或
     * undefined 即 c＝p；load 侧：c 空即 c＝p，c 为 undefined 时仅当 p 不以
     * .dex 结尾才 c＝p（p 以 .dex 结尾时官不回落、c 仍为 undefined——咱家桥
     * 把 undefined 归一为 null 槽位，据此判 undefined；此形下无类名可加载，
     * resolve 以 className 空直接回 null，与官该形终局同为无果，唯官在 dex
     * 文件存在时多一条终局 toast，宿主面差异记名）。
     * 单参形 p 即类名，聚阅 PythonHiker 的 findJavaClass("com.chaquo.python.Python")
     * 即此形。
     */
    private Object resolveJavaClass(Object[] args, boolean returnClass) {
        String p = args != null && args.length > 0 ? argStr(args[0]) : "";
        Object rawC = args != null && args.length > 1 ? args[1] : null;
        String c = rawC != null ? argStr(rawC) : "";
        boolean cUndefined = isUndefined(rawC) || "undefined".equals(c)
                || (rawC == null && args != null && args.length > 1);
        boolean cEmpty = !cUndefined && (c == null || c.isEmpty());
        String className;
        if (returnClass) {
            className = (cEmpty || cUndefined) ? p : c;
        } else if (cEmpty || (cUndefined && (p == null || !p.endsWith(".dex")))) {
            className = p;
        } else {
            className = cUndefined ? "" : c;
        }
        if (className == null || className.isEmpty()) return null;
        String so = args != null && args.length > 2 ? argStr(args[2]) : "";
        boolean canDex = args != null && args.length > 1 && p != null && !p.isEmpty();
        return loadJavaInstance(p, className, so, returnClass, canDex);
    }

    /**
     * 环43（批C3复核订正）：8.83 JSEngine.newInstanceOrClass（smali 7854）逐支
     * 对位：Z=1 回 Class 本身；Z=0 回 newInstance() 实例，仅当实例化异常的
     * message 含「has no zero argument constructor」或「is not accessible」
     * 时回落 Class，其余 InstantiationException/IllegalAccessException 原样抛。
     */
    private static Object newInstanceOrClass(Class<?> clazz, boolean returnClass)
            throws InstantiationException, IllegalAccessException {
        if (returnClass) return clazz;
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("has no zero argument constructor")
                    || msg.contains("is not accessible"))) {
                return clazz;
            }
            throw e;
        }
    }

    /**
     * 环43（批C3复核订正）：8.83 JSEngine.loadJavaInstance(p, c, so, Z)
     * （smali 19244-19816）逐段对位：
     * 一段 Class.forName(c)（应用类加载器——咱家自带 Chaquopy 的
     * com.chaquo.python.* 在此命中，与官「插件 dex 父委托先命中宿主类」同果）
     * →newInstanceOrClass；只 catch ClassNotFoundException 入二段，非 CNF 异常
     * 记日志后跳过登记表直入 dex 段（官 catchall→goto_2）。
     * 二段依次试 DEX_CLASSLOADERS 登记表（Class.forName(c, true, loader)，逐个
     * 只 catch CNF 续试）→newInstanceOrClass；非 CNF 异常记日志入 dex 段。
     * dex 段：p 为 dex 路径（不存在回 null），so 拷进应用 libs 目录作本地库
     * 搜索路径后建 DexClassLoader；构造/so 拷贝异常不在本段 catch、向外传播
     * （官同形，由两 wrapper 的宿主面 catch 记日志回 null）。DexClassLoader
     * 建成后 soPath 非空时经 RetroLoadLibrary.installNativeLibraryPath 把本地
     * 库路径装进新加载器与应用类加载器两处（各自失败只记日志续行，官
     * .line 3402-3416）；终局以 loader.loadClass(c)（不初始化）加载、成功后
     * 才登记 DEX_CLASSLOADERS（官 .line 3416-3417）、再 newInstanceOrClass；
     * 终局 CNF/IllegalAccess/Instantiation 与其余异常均 toast
     * 「加载dex/so失败：<msg>」＋回 null（官 .line 3422-3430，含 P2PClass
     * doxstarthttpd 特例文案），toast 走本类既有 hkToast 宿主面。
     */
    private Object loadJavaInstance(String p, String className, String so,
                                    boolean returnClass, boolean canDex) {
        try {
            Class<?> clazz = Class.forName(className);
            return newInstanceOrClass(clazz, returnClass);
        } catch (ClassNotFoundException e) {
            Logger.t(TAG).e(e, "loadJavaInstance app loader miss");
            try {
                Class<?> found = null;
                synchronized (DEX_CLASSLOADERS) {
                    for (ClassLoader cl : DEX_CLASSLOADERS) {
                        try {
                            found = Class.forName(className, true, cl);
                            break;
                        } catch (ClassNotFoundException miss) {
                            Logger.t(TAG).e(miss, "loadJavaInstance registry miss");
                        }
                    }
                }
                if (found != null) return newInstanceOrClass(found, returnClass);
            } catch (Throwable t) {
                Logger.t(TAG).e(t, "loadJavaInstance registry failed");
            }
        } catch (Throwable t) {
            Logger.t(TAG).e(t, "loadJavaInstance app loader failed");
        }
        if (!canDex) return null;
        String dexPath = getFilePath(p);
        File dexFile = new File(dexPath);
        if (!dexFile.exists()) return null;
        File optDir = new File(getFilePath("hiker://files/cache/java"));
        if (!optDir.exists()) optDir.mkdirs();
        String soPath = null;
        if (so != null && !so.isEmpty() && !"undefined".equals(so)) {
            String soResolved = getFilePath(so);
            File libsDir = App.get().getDir("libs", 0);
            if (!libsDir.exists()) {
                // 官 cond_9 落点：libs 目录不在场时 soPath 保留原 so 路径
                soPath = soResolved;
            } else {
                File soFile = new File(soResolved);
                if (soFile.isDirectory()) {
                    File[] sos = soFile.listFiles();
                    if (sos == null) {
                        // 官 cond_9 落点：目录列举失败时 soPath 保留原 so 路径，不改定 libs 目录
                        soPath = soResolved;
                    } else {
                        for (File s : sos) {
                            if (s.getName().endsWith(".so")) {
                                copyFileQuiet(s, new File(libsDir, s.getName()));
                            }
                        }
                        soPath = libsDir.getAbsolutePath();
                    }
                } else if (soFile.exists()) {
                    copyFileQuiet(soFile, new File(libsDir, soFile.getName()));
                    soPath = libsDir.getAbsolutePath();
                }
            }
        }
        dalvik.system.DexClassLoader loader = new dalvik.system.DexClassLoader(
                dexPath, optDir.getAbsolutePath(), soPath,
                HkJsRuntime.class.getClassLoader());
        try {
            if (soPath != null && !soPath.isEmpty()) {
                String nativePath = soPath;
                File soPathFile = new File(soPath);
                if (!soPathFile.isDirectory()) {
                    nativePath = soPathFile.getParent();
                }
                try {
                    RetroLoadLibrary.installNativeLibraryPath(loader, new File(nativePath));
                } catch (Throwable t) {
                    Logger.t(TAG).e(t, "installNativeLibraryPath dex loader failed");
                }
                try {
                    RetroLoadLibrary.installNativeLibraryPath(
                            HkJsRuntime.class.getClassLoader(), new File(nativePath));
                } catch (Throwable t) {
                    Logger.t(TAG).e(t, "installNativeLibraryPath app loader failed");
                }
            }
            Class<?> clazz = loader.loadClass(className);
            DEX_CLASSLOADERS.add(loader);
            if (clazz != null) return newInstanceOrClass(clazz, returnClass);
            return null;
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            Logger.t(TAG).e(e, "loadJavaInstance dex failed");
            hkToast("加载dex/so失败：" + e.getMessage());
            return null;
        } catch (Throwable t) {
            Logger.t(TAG).e(t, "loadJavaInstance dex failed");
            String msg = t.getMessage();
            if (msg != null && msg.contains("com.p2p.P2PClass.doxstarthttpd")) {
                hkToast("P2PClass加载so失败");
                return null;
            }
            hkToast("加载dex/so失败：" + msg);
            return null;
        }
    }

    /** loadJavaInstance dex 段的 so 拷贝（失败静默，官 FileUtil.copy 同位尽力形）。 */
    private static void copyFileQuiet(File src, File dst) {
        try {
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            java.nio.file.Files.copy(src.toPath(), dst.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ignored) {
        }
    }

    public HkJsRuntime(HkRule rule) {
        this.rule = rule;
        this.executor = Executors.newSingleThreadExecutor();
        this.parser = new Parser();
        this.parser.setJsEvaluator(this);
        this.results = new ArrayList<>();
        this.kv = new HashMap<>();
        loadPublicKv();
        updateTraceLog();
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
            // G3（环19 订正）：官四处求值点每发必装 ImporterTopLevel（JE runScript:9194、
            // evalJS:14013/:14459/:14835）——其落点就是 initStandardObjects＋
            // ImporterTopLevel.init(cx, scope, false) 静态形：咱家 stock Rhino
            // 1.7.15 探针与官 fork smali 双证鲜 scope 的 importer 契约面＝
            // {JavaImporter, Packages, getClass, JavaAdapter}、无自由
            // importClass/importPackage（2026-11-10 本机同版 Rhino 探针三形态对照实证：
            // 普通 scope 与普通+静态 init 两形 importClass 皆 undefined 且调用抛
            // ReferenceError，ImporterTopLevel 实例形皆为 function 且可调用）。
            // 环19 结构迁移（主断点）：旧形在此建 ImporterTopLevel 实例形
            // 常驻主 scope、allFunctions 只评一次——状态跨发残留（var/let、
            // $.exports、Module._cache 全进程一份）、importClass/importPackage
            // 超集、globalMap0 自造别名皆出于此。官形＝每发鲜 scope 装配
            // （runScript：initStandardObjects＋ImporterTopLevel.init(cx,scope,
            // false) 静态形＋initRequire＋每发评 allFunctions/plugin）。
            // 改：init 只建 allFunctions 模板串并装配首个鲜 scope（供 init 后、
            // 首个主路入口前的零散求值），此后 parseListInternal/parseSearch/
            // evalLazy/详情/选择器独立发在各自 arm 处 assembleFreshScope 换新
            // （见 assembleFreshScope 注）。装配内 allFunctions 评失败不吞：
            // 异常直穿 init 的 Future 出口、init 整体按失败显形（与旧口径同）。
            try {
                allFunctions = String.format(getAllFunctions(), HkJsRuntime.class.getName())
                        + "\n var MY_UA = JSON.parse(getUaObject());\n var MOBILE_UA =  MY_UA.mobileUa;\n var PC_UA = MY_UA.pcUa";
            } catch (Throwable t) {
                Logger.t(TAG).e(t, "build allFunctions");
                allFunctions = "";
            }
            scope = assembleFreshScope();
            com.github.catvod.crawler.SpiderDebug.log("hk", "init step=api rule=%s", rule.getTitle());
            loadKv();
            com.github.catvod.crawler.SpiderDebug.log("hk", "init step=config rule=%s", rule.getTitle());
            // G35：init 不再自跑 preRule——官 JSEngine 无 init 求值，preRule 只经
            // parsePreRule(ArticleListRule)/parsePreRule(SearchEngine) 两入口由
            // 上层按场景调用（每调一次跑一次、无引擎次数闸）；旧一次性闩＋
            // 共享 scope 直跑已整支摘除（见 parsePreRule 两入口）。
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

    /**
     * getResCode/getCode 共用本体（环20 追记 B 定死）：官唯一口径＝发内登记值
     * 经包装层覆写为 callbackKey 入参，本体只一句 resCodeMap.get(入参)
     * （JSEngine.smali:17733-17760），缺位回 null。规则常写 getResCode() || ''，
     * 回 0 会导致空内容，故回的是正文串不是状态码。
     * 旧形的二路「读实例字段 scope 现值的 CALLBACK_KEY」是咱家独有 ambient
     * 读法、官包不存在（官无 scope/callbackKey 实例字段）：转录发求值期间
     * 字段仍指上一主路发的 scope，会按上一发的键查表，已整支摘除。
     * 入参未命中后回 evalResCodeBody（@rule= js: 预检正文）是既有兼容路
     * （dig2 件3 判 C 记名），非 scope 读取，保留不动。
     */
    private Object resCodeByCallbackKey(Object[] args) {
        if (args != null && args.length > 0 && args[0] != null) {
            try {
                String key = String.valueOf(argsNativeObjectAdjust(args[0]));
                String v = resCodeMap.get(key);
                if (v != null) return v;
            } catch (Throwable ignored) {
            }
        }
        if (evalResCodeBody != null) return evalResCodeBody;
        return null;
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
            } else if ("search".equals(typeKey)) {
                // 官 setResult（JE:24963）：typeKey="search"→callbackSearchResult，
                // 余→callbackHomeResult（G27 路由，与下两名同口径）。
                deliverResult(args, false, true, true);
            } else {
                deliverResult(args, false, false, true);
            }
            return null;
        });
        // G27 typeKey 路由（官 setHomeResult JE:24534／setSearchResult JE:25115）：
        // setHomeResult 仅 typeKey="search" 转 search 支、余走 home 支；
        // setSearchResult 仅 typeKey="home" 转 home 支、余走 search 支。
        // typeKey 取值与官包装层同源（setPreResult 同口径）：JS 显式传参优先，
        // 否则本发汇点类型，再否则 currentMyType——包装层恒以 MY_TYPE 覆写，
        // 故三名在官侧本就按本发类型归一（官方文档「三者可混用」即此）。
        putFunction("setHomeResult", args -> {
            deliverResult(args, false, "search".equals(resultTypeKey(args)), true);
            return null;
        });
        putFunction("setSearchResult", args -> {
            deliverResult(args, false, !"home".equals(resultTypeKey(args)), true);
            return null;
        });
        // setPreResult：预返回（官方要求与 setResult 成对、顺序固定）。
        // 官形（JE:24850）：typeKey 为 lastChapter/search 时不交付；余走 callbackHomeResult
        // 的 PRE 支——打 ARTICLE_LIST_PRE、记 preCalled、不摘回调，正式交付到达时打
        // ARTICLE_LIST_AFTER 追加（官消费端分段处理，无"整体替换"）。旧形把 PRE 暂存进
        // 共享 results 与正式交付压成一表，是列表重复的结构口子（账 G51），已换官式交付口。
        putFunction("setPreResult", args -> {
            // typeKey 取值与官包装层同源：JS 显式传参优先，否则本发汇点类型
            // （parseList=home/parseSearch=search），再否则 currentMyType。
            String typeKey = (args != null && args.length > 3 && args[3] != null)
                    ? argStr(args[3]) : null;
            if (typeKey == null) {
                HkFindDelivery.Sink s = activeFindSink.get();
                typeKey = s != null ? s.type() : currentMyType;
            }
            if ("lastChapter".equals(typeKey) || "search".equals(typeKey)) return null;
            deliverResult(args, true, false, true);
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
            String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : null;
            // 环24-11 簇一：args[2] 即官 ruleKey（包装层覆写 MY_RULE、直名路
            // JS 显式传参同位）——此前从不读、取页只靠 ambient；现透传到
            // fetchByHiker 的 ruleKey 位，显式优先、ambient 退兜底。
            Object ruleKey = args.length > 2 ? args[2] : null;
            // 8.83 fetchByHiker 同款：hiker://page/、hiker://files/、file://、通用 hiker://
            // 走内部协议分发返回内容，不再直接掉进 fetchSync 返回 ""（曾导致 JSON.parse("") 抛 Empty JSON string）；
            // toHex/inputStream 旗自 options 透传（fetch0→fetchByHiker 同链，官包全量抠账 G5）
            if (url.startsWith("hiker://") || url.startsWith("file://")) {
                Object r = fetchByHikerWithFlags(url, options, ruleKey);
                return r == null ? "" : r;
            }
            return fetchSync(url, options);
        });
        putFunction("post", args -> {
            if (args == null || args.length == 0) return "";
            String url = argStr(args[0]);
            String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : "{}";
            // 环24-11 簇一：args[2] ruleKey 透传（同 fetch 注）
            Object ruleKey = args.length > 2 ? args[2] : null;
            // 8.83 post0→fetch0 经 RuleFetchDelegate→fetchByHiker 拦截同款：内部协议走分发
            if (url.startsWith("hiker://") || url.startsWith("file://")) {
                Object r = fetchByHikerWithFlags(url, options, ruleKey);
                return r == null ? "" : r;
            }
            // 8.83 post（JE:21353-21367 入口 flag=true）→post0 内 generatePostOps 后
            // xor-int/lit8 反转为 false→generateRequestOptions(op, false)——缺 UA 时
            // 补移动端 UA（JE:8148-8186 post0 亲读；官包全量抠账 A 册 §1 post 行）。
            // 此前误读 flag 补了桌面 UA，与官正反。
            options = applyGeneratePostOps(options);
            return fetchSync(url, injectMobileUa(options));
        });
        putFunction("request", args -> {
            if (args == null || args.length == 0) return "";
            String url = argStr(args[0]);
            // 环24-11 簇一：args[2] ruleKey（同 fetch 注）；page 支直达
            // handlePageRequest 时在此转 HkRule 显式入参。
            Object ruleKey = args.length > 2 ? args[2] : null;
            // hiker://page/<path> 内部协议：从规则 pages 里按 path 取页面规则，返回 {"rule": "..."}
            if (url.startsWith("hiker://page/")) return handlePageRequest(url, ruleFromExplicitKey(ruleKey));
            // D2：hiker://assets/ 走内置 asset（官方 $.require 经 getScript→request() 取脚本同款）
            if (url.startsWith("hiker://assets/")) return loadAssetText(url);
            String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : null;
            // 8.83 fetchByHiker 同款：hiker://files/、file://、通用 hiker:// 走内部协议分发；
            // toHex/inputStream 旗自 options 透传（同 fetch）
            if (url.startsWith("hiker://") || url.startsWith("file://")) {
                Object r = fetchByHikerWithFlags(url, options, ruleKey);
                return r == null ? "" : r;
            }
            // N1: 8.83 的 request 默认补移动端 UA（JSEngine.smali:22856），裸 fetch 才不补
            options = injectMobileUa(options);
            return fetchSync(url, options);
        });
        putFunction("getRule", args -> {
            // 8.83 JSEngine.getRule：恒等返回传入的规则字符串（此前缺失，调用的规则直接 ReferenceError）
            return args == null || args.length == 0 || args[0] == null ? "" : argStr(args[0]);
        });
        // getResCode/getCode 共用同一本体（官 getCode 只是 getResCode 的
        // alias，JSEngine.smali:17733 @JSAnnotation(alias="getCode")，一法两名；
        // 咱旧形是两份复制本体，已收成一份 resCodeByCallbackKey）。
        putFunction("getResCode", args -> resCodeByCallbackKey(args));
        putFunction("getCode", args -> resCodeByCallbackKey(args));
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
            // 环37 照官 putVar(o, o2)（JE:21775）：o2 非 null/非 undefined 且 o 为
            // String → putVar2(o, o2)；否则 o 为 JSONObject → 取其 key/value 的
            // getString 入表；余皆不写。旧形双参路 key 经 argStr 强转、value 为
            // null/undefined 亦写 ""，与官分叉（审计 §4 细差），整支收官形。
            if (args == null || args.length == 0) return null;
            Object o = argsNativeObjectAdjust(args[0]);
            Object o2 = args.length > 1 ? args[1] : null;
            boolean o2Present = o2 != null && !isUndefined(o2);
            if (o2Present && o instanceof String) {
                putVar2Impl((String) o, o2);
                return null;
            }
            java.util.Map<?, ?> m = toStringMap(args[0]);
            if (m != null && m.get("key") instanceof String) {
                Object val = m.get("value");
                String s = val instanceof String ? (String) val
                        : (val == null ? null : stringifyArg(val));
                if (s != null) varMap.put((String) m.get("key"), s);
            }
            return null;
        });
        // 官方 putVar2：值非字符串时 JSON 序列化后存入（与 putVar 区别仅在此）
        putFunction("putVar2", args -> {
            if (args == null || args.length < 2) return null;
            Object k = argsNativeObjectAdjust(args[0]);
            if (!(k instanceof String)) return null;
            putVar2Impl((String) k, args[1]);
            return null;
        });
        putFunction("getVar", args -> {
            // 环37 照官 getVar(key, def)（JE:17869）：key 经 adjust 非 String 回 def
            // （def 调整后 null/undefined→""）；旧形 argStr 强转查表与官分叉。
            if (args == null || args.length == 0) return "";
            Object k = argsNativeObjectAdjust(args[0]);
            String def = "";
            if (args.length > 1 && args[1] != null && !isUndefined(args[1])) def = argStr(args[1]);
            if (!(k instanceof String)) return def;
            String v = varMap.get((String) k);
            return v == null ? def : v;
        });
        // 8.83 JE:24587 setItem(key, v, ruleTitleKey)：key/value 经 tryGetString，
        // 非字符串抛错；第三参经 getRuleTitle 解析后走 setItemNow 写目标规则
        // storage（B#2 第三参通道；本规则即本实例 kv）。
        putFunction("setItem", args -> {
            String k = mustBeString(args, 0);
            String v = mustBeString(args, 1);
            // 环37：第三参隐参解析走 resolveRuleTitle（票据→标题还原；直名路
            // 缺位补当前标题）。旧形 hkRuleTitle 不还原票据，主 scope 注入的
            // MY_TICKET 使 title 恒为票据串、与当前标题不等而走跨规则支后
            // 静默不写。统一经 setItemNow 落（含大小闸与 RuleModifiedEvent）。
            String title = resolveRuleTitle(args != null && args.length > 2 ? getString(args[2]) : null);
            setItemNow(k, v, title);
            // 诊断：口令记号存（星集使用前须知门）。只记键名与长度，不记值本身。
            if ("xj_firstopen".equals(k) && title.equals(currentRuleTitle())) {
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
        // B#2：8.83 JE:17072 getItem(key, def, ruleTitleKey)＝getItemNow(getString(key),
        // getString(def), getRuleTitle(getString(第三参)))——第三参通道可读他规则
        // storage（规则不存在回 ""、不是 def；storage 空/无键回 def）。
        // 旧「字面 "undefined" 存量值当缺键」解毒支官无，整支摘除；存量毒值改走
        // loadKv 一次性清理（kv-undefined-cleaned 记号，备份后摘）。
        putFunction("getItem", args -> {
            if (args == null || args.length == 0) return null;
            String key = getString(args[0]);
            String def = args.length > 1 ? getString(args[1]) : null;
            String title = resolveRuleTitle(args.length > 2 ? getString(args[2]) : null);
            // 诊断：口令记号读（星集使用前须知门）。只记命中与长度，不记值本身。
            if ("xj_firstopen".equals(key) && title.equals(currentRuleTitle())) {
                String v = kv.get(key);
                com.github.catvod.crawler.SpiderDebug.log("hk", "getItem xj_firstopen hit=%s len=%d ruleVerLen=%d",
                        v != null, v == null ? -1 : v.length(),
                        rule == null ? -1 : rule.getVersionRaw().length());
            }
            return getItemNow(key, def, title);
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
        // 存储封装.getMyVar、xlog/logImpl、parseJson/jsStringify），try/catch 包住、
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
            // G29：8.83 JE:10953 base64Encode——adjust 后非 String 回 ""；
            // 平台缺省字符集 getBytes、Base64 flags=2 (NO_WRAP)、
            // new String(缺省字符集) 收形。
            Object o = args != null && args.length > 0 ? argsNativeObjectAdjust(args[0]) : null;
            if (!(o instanceof String)) return "";
            return new String(Base64.encode(((String) o).getBytes(), Base64.NO_WRAP));
        });
        putFunction("base64Decode", args -> {
            // G29：8.83 JE:10863 base64Decode——adjust 后非 String 回 ""；
            // Base64.decode(s, 2) 无 catch（非法入参的
            // IllegalArgumentException 照官穿桥）、new String(缺省字符集)。
            Object o = args != null && args.length > 0 ? argsNativeObjectAdjust(args[0]) : null;
            if (!(o instanceof String)) return "";
            return new String(Base64.decode((String) o, Base64.NO_WRAP));
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
        // 环 16-3：aesUtilCrypt 已回官 null 失败形，消费点摘 catch 直回结果
        // （失败→null，与官 JS 侧收到 null 同形；旧形 catch 回 ""）。
        putFunction("aesEncode", args -> {
            if (args == null || args.length < 2 || args[0] == null || args[1] == null) return "";
            return aesUtilCrypt(true, String.valueOf(args[0]), String.valueOf(args[1]));
        });
        putFunction("aesDecode", args -> {
            if (args == null || args.length < 2 || args[0] == null || args[1] == null) return "";
            return aesUtilCrypt(false, String.valueOf(args[0]), String.valueOf(args[1]));
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
        // 取码口径（2026-11-07 语法错误案归属后的收编）：此前 http 远程库走 fetchSync 裸取——
        // fetchSync 对非 2xx 回错误页正文（fetch() 语义本身没错），require 拿到什么就喂解析器，
        // 远程库被拦/挂掉时错误页被当 JS 跑、爆「语法错误 (hk-plugin#3)」。原版 require 就是
        // require0 本人（JSEngine.java:2764-2768：hour=-1、eval=true、v 缺省 0），取码经
        // requestForCode（非 2xx 或含 error 字段判失败）、本地有缓存时新内容过 isJsCode
        // 非 JS 回退本地。故 http/file/hiker 三路收编既有 require0 等价路，只把原版 evalKey
        // 的「回调求值」接回调用方作用域；本地 .hkzip 库（loadLibLocal）与裸名等其余名的
        // 现行软失败路维持不动。require0 的原版抛错点已照官恢复（阶段五 require 软化），
        // 软失败只收在本入口 catch 一层（B1 教训的收口层在此、不在 require0 本体）。
        putFunctionEx("require", (cx, callerScope, args) -> {
            if (args == null || args.length == 0) return null;
            String libUrl = argStr(args[0]);
            String code = loadLibLocal(libUrl);
            if (code != null) {
                return evalJsInScope(cx, callerScope, code, "hk-plugin");
            }
            if (libUrl != null && (libUrl.startsWith("http")
                    || libUrl.startsWith("file://") || libUrl.startsWith("hiker://"))) {
                Object options = args.length > 1 ? args[1] : null;
                int reqVersion = args.length > 2 && args[2] instanceof Number
                        ? ((Number) args[2]).intValue() : 0;
                String ruleTitle = rule != null ? rule.getTitle() : "";
                Function evalCallback = new BaseFunction() {
                    @Override
                    public Object call(Context c, Scriptable s, Scriptable t, Object[] a) {
                        if (a == null || a.length == 0 || a[0] == null) return null;
                        return evalJsInScope(cx, callerScope, String.valueOf(a[0]), "hk-plugin");
                    }
                };
                try {
                    return require0(options, libUrl, -1, options, evalCallback, true,
                            ruleTitle, reqVersion);
                } catch (Throwable e) {
                    Logger.t(TAG).d("require failed (soft): %s -> %s", libUrl, e.getMessage());
                    com.github.catvod.crawler.SpiderDebug.log("hk", "require miss soft name=%s rule=%s",
                            libUrl, rule == null ? "" : rule.getTitle());
                    return null;
                }
            }
            code = (String) fetchSync(libUrl, null);
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
            // G26：官 putMyVar＝putVar2(getRuleTitle(第三参)+"@"+key) 写全进程
            // varMap 一表（JE:21694）；key 经 adjust 非 String 直接不写（官同）。
            if (args != null && args.length > 1 && argsNativeObjectAdjust(args[0]) instanceof String) {
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
            // G26：8.83 clearMyVar(key, ruleTitleKey)（JE:12188）：key 经 adjust
            // 非 String 直接回；删 varMap 的 getRuleTitle(第二参)+"@"+key——
            // 全进程一表，可清任意规则；无"无参清空全表"行为。
            if (args != null && args.length > 0) {
                Object k = argsNativeObjectAdjust(args[0]);
                if (k instanceof String) {
                    String title = resolveRuleTitle(args.length > 1 ? getString(args[1]) : null);
                    varMap.remove(title + "@" + k);
                }
            }
            return null;
        });
        // listMyVarKeys：8.83（smali:18947，lambda:7220）：按 getRuleTitle(ruleTitleKey)+"@"
        // 过滤本规则的 key，strip 前缀（split("@")[1]）后序列化。旧实现全表裸 keySet。
        putFunction("listMyVarKeys", args -> {
            // 环37：入参同为 ruleTitleKey 隐参（官 lambda 以 getRuleTitle(参) 定前缀），
            // 走 resolveRuleTitle——旧形直吃明文/票据原串，票据入参时前缀恒不中、回空表。
            String title = resolveRuleTitle(args != null && args.length > 0 ? getString(args[0]) : null);
            String prefix = title + "@";
            List<String> out = new ArrayList<>();
            for (String k : varMap.keySet()) {
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
            if (args != null && args.length > 0) varMap.remove(argStr(args[0]));
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

        // G24：宿主 Java 版存储对象已整支摘除——官 JSEngine 无此注册
        //（补正负证 #6），该对象只由 plugin.js 在 windows 上定义的一面
        // 提供（assets/plugin.js:308-345，与官逐字节同），其八法经宿主全局
        // getItem/setItem/getPublicItem/setPublicItem/getVar/putVar/
        // getMyVar/putMyVar 落地。此前宿主版与 JS 版双占同名、生效面随
        // 求值序漂移。Java 侧 juItem 的 JSON 回解析取值助手改名 jsonGetByKey
        // 保留（不依赖 JS 对象）。
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

        // ---- 当前结果列表的动态修改（官方经 EventBus 改 UI 列表；本宿主直接改 currentItems() 工作表） ----
        putFunction("updateItem", args -> {
            // 8.83 updateItem（JE:26507-26672）：首参多态——String＝锚 id、
            // JSONObject＝条目本体；条目须为 JSON 对象（NativeObject/Map），
            // 非对象（含 JSON 字符串）抛"updateItem：格式有误，只支持json"。
            // 锚序：①显式字符串 id ②条目 extra.id——url 不参与（旧形先 url
            // 后 extra.id、无写回，与官序不符，G28 订正）。显式 id 按官
            // isNotEmpty 判（JE:26507-26672）：空串视同无显式 id、回落
            // extra.id（F2 补改）。
            String anchor = null;
            boolean explicitId = false;
            Object obj = null;
            if (args != null && args.length >= 1) {
                if (args[0] instanceof String) {
                    anchor = (String) args[0];
                    explicitId = !TextUtils.isEmpty(anchor);
                    if (args.length >= 2) obj = args[1];
                } else {
                    obj = args[0];
                }
            }
            if (!(obj instanceof NativeObject) && !(obj instanceof Map))
                throw new RuntimeException("updateItem：格式有误，只支持json");
            Map<String, Object> m = toStrMap(obj);
            if (m == null) throw new RuntimeException("updateItem：格式有误，只支持json");
            if (!explicitId) anchor = extraIdOf(m);
            // 写回（官 :1521-1528）：显式 id 在、条目 extra 非空且其 id 空
            // → 把显式 id 写回条目 extra JSON 后再交付。
            String writeBackExtra = null;
            if (explicitId && !TextUtils.isEmpty(anchor)) {
                Map<String, Object> exMap = extraMapOf(m);
                if (exMap != null && TextUtils.isEmpty(str(exMap, "id"))) {
                    exMap.put("id", anchor);
                    writeBackExtra = GSON.toJson(exMap);
                }
            }
            // 环30 照官分工：引擎只造事件、恒 post（旧形「工作表命中才 post」摘除，
            // D30-1）——条目体经 convertItem 交付、锚＝显式 id 或条目 extra.id、
            // url 不参与；展示表由页面消费方按锚扫表定夺（扫不到静默无-op）。
            HkItem eventItem = convertItem(m);
            if (writeBackExtra != null) eventItem.setExtra(writeBackExtra);
            Logger.t(TAG).d("updateItem: %s", anchor);
            postItemModifyEvent(eventItem,
                    com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action.UPDATE, null, anchor, m);
            // 工作表同步（咱家 find 求值期交付机制，G51）：命中才同步，未命中不补位。
            if (!TextUtils.isEmpty(anchor)) {
                int idx = findItemIndexByExtraId(anchor);
                if (idx >= 0) {
                    HkItem target = currentItems().get(idx);
                    applyUpdateFields(target, m);
                    if (writeBackExtra != null) target.setExtra(writeBackExtra);
                }
                if (collectRaw) {
                    for (Map<String, String> raw : rawResults) {
                        if (anchor.equals(rawExtraId(raw))) {
                            for (Map.Entry<String, Object> e : m.entrySet()) {
                                if (!"extra".equals(e.getKey())) raw.put(e.getKey(), String.valueOf(e.getValue()));
                            }
                            if (writeBackExtra != null) raw.put("extra", writeBackExtra);
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
            // 环30 照官 deleteItem（JE:13478）：id 语义恒为 extra.id（旧形按 url
            // 找条目、聚阅实样恒不中，D30-2）；引擎造桩条目（extra={id:串}）恒 post
            // ——单串一桩、数组拼 list 一次 post，展示表由页面消费方按 extra.id 删。
            List<HkItem> stubs = new ArrayList<>();
            for (String id : ids) {
                HkItem stub = new HkItem();
                stub.setExtra("{\"id\":" + GSON.toJson(id) + "}");
                stubs.add(stub);
                int idx = findItemIndexByExtraId(id);
                if (idx >= 0) currentItems().remove(idx);
                if (collectRaw) rawResults.removeIf(r -> id.equals(rawExtraId(r)));
                Logger.t(TAG).d("deleteItem: %s", id);
            }
            if (stubs.size() == 1) {
                postItemModifyEvent(stubs.get(0),
                        com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action.DELETE, null);
            } else if (!stubs.isEmpty()) {
                postItemModifyListEvent(stubs,
                        com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action.DELETE, null, false);
            }
            return null;
        });
        // 8.83 JSEngine.deleteItemByCls（JE:13643）：cls 非字符串抛
        // "deleteItemByCls：格式有误，请传入cls字符串"；引擎侧只 post
        // ItemModifyEvent(DELETE)+setCls，**不删任何条目**——删除由页面层
        // onItemModify 按 extra.cls 空格多值包含消费（ArticleListFragment
        // 源行 5019 起）。旧形在引擎侧先按 col_type 相等删工作表/raw 再发
        // 事件，与官分工不符（判据错位：col_type≠extra.cls、且页面无消费方
        // 时展示表删不到），G37 摘自删、页面侧补消费（HkArticleFragment）。
        putFunction("deleteItemByCls", args -> {
            if (args == null || args.length == 0 || !(args[0] instanceof String))
                throw new RuntimeException("deleteItemByCls：格式有误，请传入cls字符串");
            String cls = (String) args[0];
            Logger.t(TAG).d("deleteItemByCls: %s", cls);
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

        // ---- 私有加密 JS：8.83（JSEngine.smali:15070-15113，.line 3438-3442）：
        // evalPrivateJS(c, evalKey)＝AesUtil.decrypt(AES_DEFAULT_KEY, c) 后取
        // evalKey 的 getParentScope() 为作用域、调用 evalKey 本人在该作用域
        // 求值明文，异常向 JS 调用方传播（官方法声明 Throws）。
        // 旧形走 putFunction＋evalJs：evalJs 每次新建脱离 scope 求值，解密代码
        // 里的 $ 解析到主 scope 的 $，与 Module.load 在调用方作用域备好的那份
        // 不是一只——$.exports 写彼读此恒为 {}，$.require('ku').home() 报
        // 「在对象 [object Object] 中找不到函数 home」；嵌套取页在脱离 scope
        // 里 MY_RULE 串到宿主规则、同被 catch 全吞静默回 null（账
        // hk-hkzip-home-missing-dig-20261111 §二.3 两刀）。同族 rc/requireCache
        // 早已 putFunctionEx 走调用方作用域，此名对齐：evalKey 在位（反射路
        // 官包装覆写）照官原式以 evalKey 在其父作用域调用；evalKey 缺位
        // （直名路规则只传 c）以调用方作用域为落点、经 evalJsInScope 求值。
        // 异常不吞：桥咽喉 putFunctionEx 统一收口（RhinoException 原样、
        // 余裹 WrappedException），与官「向 JS 调用方传播」同效。
        putFunctionEx("evalPrivateJS", (cx, callerScope, args) -> {
            if (args == null || args.length == 0 || args[0] == null) return null;
            String plain = aesUtilCrypt(false, AES_DEFAULT_KEY, argStr(args[0]));
            if (TextUtils.isEmpty(plain)) return null;
            Object evalKey = args.length > 1 ? args[1] : null;
            if (evalKey instanceof Function) {
                Scriptable parentScope = ((Scriptable) evalKey).getParentScope();
                if (parentScope == null) parentScope = callerScope;
                // 环24-11 簇一④（包装路格）：evalKey 的 parentScope 是鲜
                // root，明文落 root 求值时 JS 可见 MY_RULE＝宿主规则、与调用
                // 发子 scope 的劫持值分叉（官单层 scope 无此层差、evalKey 的
                // parentScope 即调用方 scope 本人）。落点改 ambient 优先
                // （与 evalJsInScope 的既定对官落点同形）：当发登记在场以当
                // 发 scope 为求值落点，明文与调用方同 scope、两读同源；不在
                // 场维持官字面 parentScope。本机 Rhino 探针实证：eval.call
                // 以子 scope 入参时变量读与 var 落点均随该 scope。
                Scriptable amb = ambientEvalScope.get();
                Scriptable evalScope = amb != null ? amb : parentScope;
                return ((Function) evalKey).call(cx, evalScope, evalScope, new Object[]{plain});
            }
            return evalJsInScope(cx, callerScope, plain, "hk");
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
            // 环24-11 簇一：args[2] ruleKey 透传（同 fetch 注；旧注「本处不
            // 另开通道」随显式通道接通作废）
            Object ruleKey = args.length > 2 ? args[2] : null;
            // 8.83 fetchPC→fetch0→RuleFetchDelegate→fetchByHiker（JE:21353-21420
            // 区亲读）：内部协议同走分发，与 fetch/post 同款（G8）
            if (url.startsWith("hiker://") || url.startsWith("file://")) {
                Object r = fetchByHikerWithFlags(url, options, ruleKey);
                return r == null ? "" : r;
            }
            return fetchSync(url, mergeDesktopUa(options));
        });
        // 官方 postPC：桌面 UA 的 POST
        putFunction("postPC", args -> {
            if (args == null || args.length == 0) return "";
            String url = argStr(args[0]);
            String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : "{}";
            // 环24-11 簇一：args[2] ruleKey 透传（同 fetch 注）
            Object ruleKey = args.length > 2 ? args[2] : null;
            // 8.83 postPC→post0→fetch0 同链分发（与 fetchPC 同款，G8）
            if (url.startsWith("hiker://") || url.startsWith("file://")) {
                Object r = fetchByHikerWithFlags(url, options, ruleKey);
                return r == null ? "" : r;
            }
            options = applyGeneratePostOps(options);
            return fetchSync(url, mergeDesktopUa(options));
        });
        // 官方 fetchCookie(url, options)：带 withHeaders 抓取，返回 set-cookie
        putFunction("fetchCookie", args -> {
            if (args == null || args.length == 0) return "";
            try {
                String url = argStr(args[0]);
                String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : "{}";
                Object fetched;
                // 8.83 fetchCookie→fetch 本体（JE:15691-15866 亲读）：内部协议先经
                // fetchByHiker 分发，分发回串不直回——与普通 fetch 回串同走下方
                // 提取链（JSON→headers→set-cookie）；回串实为页面内容 JSON 或
                // 文件原文、非 cookie 包装形，进不了提取链，官恒回 ""（G8）
                if (url.startsWith("hiker://") || url.startsWith("file://")) {
                    // 环24-11 簇一：args[2] ruleKey 透传（同 fetch 注）
                    fetched = fetchByHiker(url, args.length > 2 ? args[2] : null, false, false);
                } else {
                    // 8.83 fetchCookie 本体（JE:15691-15866 亲读）：options 归一为对象，
                    // headers 无精确键 "Cookie" 时预置 Cookie:"#noCookie#"——经咱家已同款
                    // 在位的 ContentTypePre/AfterInterceptor 对（HkHttp.client() 已装）：
                    // Pre 改名 Cookie-Temp、After 见哨兵值摘头，自动 cookie 池不注入、
                    // 哨兵不上 wire，正是为「只取服务端 set-cookie、不带既有 cookie」
                    // 而设（G15）。此前只拼 withHeaders、无预置，与普通 fetch 无别。
                    // 另置 withHeaders=true（官同）。
                    // 官在 options Map 上直接操作、无解析失败回退支：options 非法
                    // （非 JSON 对象）时 getAsJsonObject 直接抛，归外层 catch 回 ""（G15）
                    com.google.gson.JsonObject optObj = com.google.gson.JsonParser.parseString(options).getAsJsonObject();
                    // 官只认 "headers" 键（map["headers"]，无则新建放回）；精确键
                    // "Cookie"（大小写敏感 containsKey）不在才预置
                    com.google.gson.JsonObject hdrs = null;
                    if (optObj.has("headers") && optObj.get("headers").isJsonObject()) {
                        hdrs = optObj.getAsJsonObject("headers");
                    }
                    if (hdrs == null) {
                        hdrs = new com.google.gson.JsonObject();
                        optObj.add("headers", hdrs);
                    }
                    if (!hdrs.has("Cookie")) hdrs.addProperty("Cookie", "#noCookie#");
                    optObj.addProperty("withHeaders", true);
                    options = GSON.toJson(optObj);
                    fetched = fetchSync(url, options);
                }
                if (!(fetched instanceof String)) {
                    // 两路正常恒回正文串；规则 options 混入 inputStream 时回流/Map
                    // 非其可消费——关流回空
                    closeFetchStream(fetched);
                    return "";
                }
                String result = (String) fetched;
                if (TextUtils.isEmpty(result)) return "";
                Map<String, Object> map = GSON.fromJson(result, new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
                if (map == null) return "";
                Object headers = map.get("headers");
                if (!(headers instanceof Map)) return "";
                Map<String, Object> hm = (Map<String, Object>) headers;
                // 环 24-4（硬门审计 B，官 JE fetchCookie 段亲读）：withHeaders 取回后按
                // "set-cookie" → "set-Cookie" → "Set-Cookie" 三拼写序取首个命中的
                // JSONArray、toJSONString 直回；无命中回 ""。旧形遍历全表
                // equalsIgnoreCase 把所有拼写合并成一个数组——仅当响应头里多种拼写
                // 并存时分叉（值集与顺序都可能不同），已照官改序取首命中。
                for (String spelling : new String[]{"set-cookie", "set-Cookie", "Set-Cookie"}) {
                    if (!hm.containsKey(spelling)) continue;
                    Object v = hm.get(spelling);
                    if (v == null) continue;
                    List<String> cookies = new ArrayList<>();
                    if (v instanceof List) {
                        for (Object one : (List<?>) v) {
                            if (one != null) cookies.add(String.valueOf(one));
                        }
                    } else {
                        cookies.add(String.valueOf(v));
                    }
                    return GSON.toJson(cookies);
                }
                return "";
            } catch (Throwable e) {
                Logger.t(TAG).d("fetchCookie failed: %s", e.getMessage());
                return "";
            }
        });
        putFunction("postRequest", args -> {
            if (args == null || args.length == 0) return "";
            String url = argStr(args[0]);
            String options = args.length > 1 && args[1] != null ? stringifyArg(args[1]) : "{}";
            // 环24-11 簇一：args[2] ruleKey 透传（同 fetch 注）
            Object ruleKey = args.length > 2 ? args[2] : null;
            // 环 24-10（硬门审计 B）：官 postRequest 与 fetch 同链经 RuleFetchDelegate
            // →fetchByHiker 分发，旧形独漏此名、hiker:// 必回 ""。按 post 同款加前置
            // 分发（hiker:// 与 file:// 两前缀、toHex/inputStream 旗透传）。
            if (url.startsWith("hiker://") || url.startsWith("file://")) {
                Object r = fetchByHikerWithFlags(url, options, ruleKey);
                return r == null ? "" : r;
            }
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

        // ---- 文件（G20：8.83 JE:21929/26827/23698——mode{defaultInt=1}＋
        // fileToString/stringToFile 的黑名单/私有区/i-- 加密支；第 2 参旧
        // charset 双重解释整支摘除，readFile 三参归官位 (filePath, mode,
        // ruleTitleKey)） ----
        putFunction("writeFile", args -> {
            if (args != null && args.length > 1) {
                int mode = args.length > 2 ? hkIntValue(args[2], 1) : 1;
                hkStringToFile(argStr(args[1]), resolveHikerPath(argStr(args[0])), null, mode == 1);
            }
            return null;
        });
        putFunction("readFile", args -> {
            if (args == null || args.length == 0) return "";
            String p = argStr(args[0]);
            int mode = args.length > 1 ? hkIntValue(args[1], 1) : 1;
            String title = hkRuleTitle(args.length > 2 ? getString(args[2]) : null);
            if (p.contains(File.separator)) {
                return hkFileToString(new File(resolveHikerPath(p)), null, mode == 1);
            }
            return hkFileToString(new File(ruleDirForTitle(title), p), title, mode == 1);
        });
        putFunction("fileExist", args -> {
            // 8.83 JSEngine.java:1574 @JSAnnotation(returnType=BOOL)：JS 侧拿到的是真布尔。
            // 曾回字符串 "false"——JS 里非空字符串为真，短剧代理 if(!fileExist(...)) 永不成立、
            // 密文从不下载、只回 45 字节报错文本（真机+字节级实证）。
            // 环13：包内 libs 已按官落全局 libs/，全局路径直查即命中；旧自造
            // 「全局 miss 回退规则 libs」层随落点修回一并摘除（官无此层）。
            if (args == null || args.length == 0) return Boolean.FALSE;
            String p = argStr(args[0]);
            return new File(resolveHikerPath(p)).exists() ? Boolean.TRUE : Boolean.FALSE;
        });
        // 官方别名：exist 即 fileExist
        putFunction("exist", args -> {
            if (args == null || args.length == 0) return Boolean.FALSE;
            String p = argStr(args[0]);
            return new File(resolveHikerPath(p)).exists() ? Boolean.TRUE : Boolean.FALSE;
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
            // 8.83 JE:23698：(filePath, content, mode{defaultInt=1}, ruleTitleKey)；
            // hiker://files/ 开头走公开支（title=null），余取尾段名落规则私有目录。
            if (args != null && args.length > 1) {
                String p = argStr(args[0]);
                String content = argStr(args[1]);
                int mode = args.length > 2 ? hkIntValue(args[2], 1) : 1;
                String title = hkRuleTitle(args.length > 3 ? getString(args[3]) : null);
                if (p.startsWith("hiker://files/")) {
                    hkStringToFile(content, resolveHikerPath(p), null, mode == 1);
                } else {
                    String[] parts = p.split(File.separator);
                    String base = parts.length > 0 ? parts[parts.length - 1] : p;
                    hkStringToFile(content, new File(ruleDirForTitle(title), base).getAbsolutePath(), title, mode == 1);
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
            // 8.83 JSEngine.cacheM3u8（JE:11871）：fileName 空或等于 "a"（包装缺参填充值）
            // → md5(url)+".m3u8"；余以 M3u8Utils.downloadM3u8 落盘清洗后的列表文本，
            // 回 "file://<路径>##<原url>"、结果等于原 url 时直回 url。官无 options.type
            // 分支、无分片下载（第九阶段首落误加整片支，复核亲读官包后摘除订正）。
            String url = args != null && args.length > 0 ? argStr(args[0]) : "";
            String options = args != null && args.length > 1 && args[1] != null ? stringifyArg(args[1]) : null;
            String fileName = args != null && args.length > 2 && args[2] != null ? argStr(args[2]) : "";
            if (TextUtils.isEmpty(fileName) || "a".equals(fileName)) fileName = md5(url) + ".m3u8";
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
        // 8.83 JSEngine.getHome（官包 JSEngine.smali:16978-17035）：(url, urlKey)
        // 双通道——url 非空取 getHome(url)；否则 urlKey 经 argsNativeObjectAdjust
        // 转 String（非 String 照官 check-cast 抛错）、非空取 getHome(urlKey)；
        // 皆空回 ""。旧形只吃第一参，第二参传对象键时恒丢（G32）。
        putFunction("getHome", args -> {
            String url = args != null && args.length > 0 ? argStr(args[0]) : null;
            if (!TextUtils.isEmpty(url)) return homeOf(url);
            if (args != null && args.length > 1 && args[1] != null) {
                Object adj = argsNativeObjectAdjust(args[1]);
                String urlKey = adj == null ? null : (String) adj;
                if (!TextUtils.isEmpty(urlKey)) return homeOf(urlKey);
            }
            return "";
        });
        // 8.83 JSEngine.java:1966：不做 URL 编码（HttpHelper.buildParamStr：k=v&k2=v2，非字符串值 JSON 序列化）；
        // url 为空只返回参数串（无前导 ?）
        putFunction("buildUrl", args -> {
            if (args == null || args.length == 0) return "";
            String url = argStr(args[0]);
            String body = "";
            if (args.length >= 2 && args[1] != null) {
                try {
                    // 环 24-3（硬门审计 B）：官 buildUrl 本体即走 HttpHelper.buildParamStr，
                    // 无第二份实现。旧内联副本与共享正本分叉（null 值被跳过、值不做
                    // "&"→"＆＆" 替换），整段删、改调共享 buildParamStr（null→"null"、
                    // 替换在位，与 HH:347-452 同）。
                    String json = stringifyArg(args[1]);
                    com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(json);
                    if (el != null && el.isJsonObject()) {
                        body = buildParamStr(el.getAsJsonObject());
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
        // 返回口径（G46）：官 getColTypes→ArticleColTypeEnum.getCodeArray()，在
        // 57 码全集上剔除 HEADER(「header」)/FOOTER(「footer」) 两码共 55 码、
        // 枚举序不变——旧形直回 57 码全集，多出的两码 JS 侧可见即差。
        putFunction("getColTypes", args -> toJsArray(colTypeCodes()));

        // ---- 编解码补充 ----
        putFunction("md5", args -> {
            // G45：8.83 JE:19991 md5(c)——c 经 tryGetString（非 String
            // 抛错）；本体 StringUtil.md5 全形（文件分支）在 hkMd5。
            return hkMd5(mustBeString(args, 0));
        });
        // G23：8.83 _base64 为 JSEngine 注入的 MyBase64 Java 实例一面
        // （DEFAULT/NO_WRAP 等常量＋九个编解码法；补正件四逐法核：
        // 咱 HkMyBase64 九法与官委托等价）。主 scope 旧 JS 垫片
        // （__b64* 三助手＋对象字面量）整支摘除、收成 Java 面一轨，
        // 与 D1/图片 scope 同形同口（图片面在 evalImageJsInternal
        // 另装同类实例、互不相干）。
        ScriptableObject b64Target = apiRegTarget != null ? apiRegTarget : scope;
        if (b64Target != null) {
            ScriptableObject.putProperty(b64Target, "_base64",
                    Context.javaToJS(new HkMyBase64(), b64Target));
        }
        putFunction("hexToBase64", args -> {
            if (args == null || args.length == 0) return "";
            return hexToB64(argStr(args[0]));
        });
        putFunction("hexToBytes", args -> {
            // G30：8.83 JE:18148 hexToBytes(c, evalKey)——c 经 tryGetString
            // （非 String 抛错）；拼官模板串经 evalKey 在其父 scope 调用
            // 求值、回真 Uint8Array；非法字符在首个非法处截断（模板内
            // break＋slice 收口）。evalKey 非 Function 照官裸转抛
            // ClassCastException。
            String c = mustBeString(args, 0);
            Object evalKey = args != null && args.length > 1 ? args[1] : null;
            if (!(evalKey instanceof Function)) {
                throw new ClassCastException("hexToBytes evalKey is not a Function: "
                        + (evalKey == null ? "null" : evalKey.getClass().getName()));
            }
            String script = "let hexString = \"" + c + "\";\n"
                    + "let MAP_HEX = {\n"
                    + "    0: 0,\n    1: 1,\n    2: 2,\n    3: 3,\n    4: 4,\n"
                    + "    5: 5,\n    6: 6,\n    7: 7,\n    8: 8,\n    9: 9,\n"
                    + "    a: 10,\n    b: 11,\n    c: 12,\n    d: 13,\n    e: 14,\n    f: 15,\n"
                    + "    A: 10,\n    B: 11,\n    C: 12,\n    D: 13,\n    E: 14,\n    F: 15\n};\n"
                    + "    let bytes = new Uint8Array(Math.floor((hexString || \"\").length / 2));\n"
                    + "    let i;\n"
                    + "    for (i = 0; i < bytes.length; i++) {\n"
                    + "        let a = MAP_HEX[hexString[i * 2]];\n"
                    + "        let b = MAP_HEX[hexString[i * 2 + 1]];\n"
                    + "        if (a === undefined || b === undefined) {\n"
                    + "            break;\n"
                    + "        }\n"
                    + "        bytes[i] = (a << 4) | b;\n"
                    + "    }\n"
                    + "    i === bytes.length ? bytes : bytes.slice(0, i)";
            Context cx = Context.getCurrentContext();
            Scriptable parentScope = ((Scriptable) evalKey).getParentScope();
            return ((Function) evalKey).call(cx, parentScope, parentScope, new Object[]{script});
        });
        putFunction("rsaEncrypt", args -> rsaWithOptions(true, args));
        putFunction("rsaDecrypt", args -> rsaWithOptions(false, args));
        putFunction("getCryptoJS", args -> {
            // 官方语义：返回内置 CryptoJS 库（aes.js）源码，规则 eval(getCryptoJS()) 后使用
            String code = loadAsset("aes.js");
            if (code == null) {
                Logger.t(TAG).d("getCryptoJS: aes.js missing in assets");
                return "";
            }
            return code;
        });
        // 8.83 JSEngine.toCorrectJSONString（JE:26129，第九阶段亲读订正）：守卫为正常语义——
        // isUndefined 真才回 null（旧 B32 注记把 if-nez 极性读反、误判官 bug，已废）；
        // null→null；tryGetString 非串抛「参数类型只能是字符串」；空串→""；
        // JSON2.parseObject 仅对象解析（非对象/坏 JSON 抛、无 catch 上抛）；
        // 解析回 null（字面 "null"）→回串 "null"；余回规范化重序列化。
        // 咱家以 org.json 承接 parseObject 的对象限定与抛形。
        putFunction("toCorrectJSONString", args -> {
            if (args == null || args.length == 0 || args[0] == null) return null;
            Object o = args[0];
            // 官 isUndefined 含字面串 "undefined" 支（JE:5718），与 Undefined 实例同回 null
            if (o instanceof Undefined || "undefined".equals(o)) return null;
            // 官首步 argsNativeObjectAdjust＋tryGetString（Rhino ConsString 等经 adjust 后可过）
            String t = hkTryGetString(o);
            if (t.isEmpty()) return "";
            // fastjson2 parseObject 对纯空白/带空格 "null" 回 null → 官回串 "null"
            String trimmed = t.trim();
            if (trimmed.isEmpty() || "null".equals(trimmed)) return "null";
            try {
                return new org.json.JSONObject(t).toString();
            } catch (org.json.JSONException e) {
                throw new RuntimeException(e.getMessage());
            }
        });
        // 8.83 smali:18729 justTestSign(appId, devId, userId)：三参 tryGetString；
        // 拼 "appId:devId:userId:0" 后用设备 AndroidKeyStore 的 EC 密钥做 SHA256withECDSA 签名，
        // 返回 <签名hex后128位>01##04<公钥hex>（EcdsaSha256Kt.sign 等价）。
        // 环 25-7（硬门审计 B，官本体逐寄存器亲读）：官 catch 支不是回 ""——主签
        // （EcdsaSha256Kt.sign）抛错或回空/回 null 时走回退支：临时生成 secp256k1
        // 键对自签（starkbank Ecdsa.sign＝SHA-256 后标准 ECDSA、DER 编码），拼尾
        // ＝DER hex 尾 128 字符＋"01##"＋公钥串（"04"＋X‖Y 各 32 字节定长 hex，
        // starkbank PublicKey.toByteString 形）；回退支无 catch、异常原样穿出。
        // 旧形 catch 回 "" 把回退支整支吞掉，照官补回（字节形待真机，审计验法注明）。
        putFunction("justTestSign", args -> {
            String appId = mustBeString(args, 0);
            String devId = mustBeString(args, 1);
            String userId = mustBeString(args, 2);
            String message = appId + ":" + devId + ":" + userId + ":0";
            String signed = null;
            try {
                signed = ecdsaSignDevice(message);
            } catch (Throwable e) {
                signed = null;
            }
            if (signed != null && !signed.isEmpty()) return signed;
            return ecdsaSignFallback(message);
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

        // ---- 事件监听：G21 官形（addListenerImpl 本体，见 G2 委托节） ----
        // 直名路（预置段包装未覆盖的求值）与反射路共用同一本体：callbackKey 查
        // callbackMap、双 String 闸、onUpdate 活转发。旧「只记账不触发」已摘。
        putFunction("addListener", args -> {
            addListenerImpl(args != null && args.length > 0 ? args[0] : null,
                    args != null && args.length > 1 ? args[1] : null,
                    args != null && args.length > 2 ? args[2] : null);
            return null;
        });
        // 官方别名：listen 即 addListener
        putFunction("listen", args -> {
            addListenerImpl(args != null && args.length > 0 ? args[0] : null,
                    args != null && args.length > 1 ? args[1] : null,
                    args != null && args.length > 2 ? args[2] : null);
            return null;
        });

        // ---- 选择弹窗：8.83 JSEngine.showSelectOptions（JE:25689）----
        // SelectExtra 本体 post EventBus，页面（FilmListActivity）订阅弹框，选中以
        // MY_INDEX 与 js、input=选中标题回求值。第九阶段接线：解析走本仓
        // HkDetailBridge.SelectExtra（与条目点击路共用），post ShowSelectOptionsEvent，
        // 消费方 HkArticleFragment.onShowSelectOptions 弹 HkCenterListPopup 回求值。
        // 官 canTouchUI 门咱家无对应面，记名。
        putFunction("showSelectOptions", args -> {
            try {
                if (args == null || args.length == 0 || args[0] == null) return null;
                String json = stringifyArg(args[0]);
                if (TextUtils.isEmpty(json)) return null;
                com.fongmi.android.tv.ui.hk.HkDetailBridge.SelectExtra extra =
                        com.fongmi.android.tv.ui.hk.HkDetailBridge.SelectExtra.parse(json);
                if (extra == null) return null;
                boolean hasOptions = extra.options != null && !extra.options.isEmpty();
                boolean hasOptions2 = extra.options2 != null && !extra.options2.isEmpty();
                // 环 25-5（硬门审计 B）：官对 JSONObject 入参无条件 toSelectExtra 后
                // post、双空也发事件（弹窗空列表形）；旧形的双空门拦截把事件吞掉、
                // 与官分叉，删。options 首项为对象时整组迁 options2、options 置空，
                // 消费端走 options2 支（第九阶段复核订正，与本项不冲突）。官另有
                // canTouchUI 频控门（2 秒窗＋Activity 栈顶判），咱家无对应面——
                // 只记名不落（审计边界明定）。
                com.fongmi.android.tv.api.hk.event.rule.ShowSelectOptionsEvent ev =
                        new com.fongmi.android.tv.api.hk.event.rule.ShowSelectOptionsEvent();
                ev.setTitle(extra.title);
                ev.setOptions(extra.options);
                if (hasOptions2) {
                    java.util.List<String> titles = new ArrayList<>();
                    java.util.List<String> icons = new ArrayList<>();
                    for (com.fongmi.android.tv.ui.hk.HkDetailBridge.SelectOption o : extra.options2) {
                        titles.add(o == null ? "" : o.title);
                        icons.add(o == null ? "" : o.icon);
                    }
                    ev.setOptions2Titles(titles);
                    ev.setOptions2Icons(icons);
                }
                ev.setJs(extra.js);
                ev.setCol(extra.col);
                ev.setSelectedIndex(extra.selectedIndex);
                ev.setBottom(extra.bottom);
                ev.setAttachView(extra.attachView);
                ev.setRuleTitle(rule != null ? rule.getTitle() : "");
                EventBus.getDefault().post(ev);
            } catch (Throwable e) {
                Logger.t(TAG).d("showSelectOptions failed: %s", e.getMessage());
            }
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
            // 环30：非串参抛官文案（JE findItem 格式门）；未命中回 null 与官
            // "null" 串同形。表源（工作表 vs 官 adapter 表）与 3 秒时延差记名维持（D30-5）。
            if (args == null || args.length == 0 || !(argsNativeObjectAdjust(args[0]) instanceof String))
                throw new RuntimeException("findItem：格式有误，请传入ID字符串");
            String id = (String) argsNativeObjectAdjust(args[0]);
            for (HkItem it : currentItems()) {
                if (id.equals(it.getExtra("id"))) {
                    return parseJson(GSON.toJson(itemToFullMap(it)));
                }
            }
            return null;
        });
        // findItemsByCls/deleteItemByCls：按条目 extra.cls 匹配当前已收集结果
        // B35：8.83 _findItemsByCls 回 ArticleList 完整序列化（含 content/extra/type 等），此处按 HkItem 现有字段补齐
        // returnType=JSON：原版桥接层 JSON.parse 后是真数组，此处走 parseJson（1.3.42 UU 案同图）。
        // 环30（D30-5）：判据改官 hasCls 整词包含（旧形等值匹配，聚阅多值 cls
        // 'Juloadlist loading_gif' 恒回空）；非串参抛官文案；无命中回 null
        // （官 UI 空结果不 countDown、引擎等满后回 "null" 串、桥 JSON.parse 成
        // null——咱家表源是工作表、无 3 秒等待，时延与表源差记名维持）。
        putFunction("_findItemsByCls", args -> {
            if (args == null || args.length == 0 || !(argsNativeObjectAdjust(args[0]) instanceof String))
                throw new RuntimeException("findItemsByCls：格式有误，请传入cls字符串");
            String cls = (String) argsNativeObjectAdjust(args[0]);
            List<Map<String, Object>> out = new ArrayList<>();
            for (HkItem it : currentItems()) {
                if (hasCls(it.getExtra("cls"), cls)) out.add(itemToFullMap(it));
            }
            if (out.isEmpty()) return null;
            return parseJson(GSON.toJson(out));
        });

        // ---- xpath/xpathArray（xpa）：8.83 JSEngine 同款 javax.xml 真 XPath 求值 ----
        // 此前是空桩（借口宿主 Parser 不支持），XML/RSS 类规则必死
        // 8.83 JSEngine.xpath（官包 JSEngine.smali:26933-27103，@Throws、无内部 catch）：
        // 三参 (html, exp, type)，type 注解缺省 "string"；html 是 String 才走
        // HtmlCleaner→DomSerializer 管线，非 String（如上一次 xpath 回的 Node）
        // 原样作求值输入；type 按 QName localPart 小写与 type 原串 equals 依次选
        // NODE/NODESET/NUMBER/BOOLEAN、余皆 STRING；回 evaluate 原生结果
        // （ReturnType.OBJECT 桥形），失败抛错穿桥给 JS 侧 catch（G31）。
        putFunction("xpath", args -> {
            if (args == null || args.length < 2) throw new RuntimeException("xpath：参数不足");
            try {
                Object src = argsNativeObjectAdjust(args[0]);
                Object input = src instanceof String ? parseXmlDoc((String) src) : src;
                String type = args.length > 2 ? argStr(args[2]) : "string";
                javax.xml.namespace.QName qname;
                if (javax.xml.xpath.XPathConstants.NODE.getLocalPart().toLowerCase().equals(type)) {
                    qname = javax.xml.xpath.XPathConstants.NODE;
                } else if (javax.xml.xpath.XPathConstants.NODESET.getLocalPart().toLowerCase().equals(type)) {
                    qname = javax.xml.xpath.XPathConstants.NODESET;
                } else if (javax.xml.xpath.XPathConstants.NUMBER.getLocalPart().toLowerCase().equals(type)) {
                    qname = javax.xml.xpath.XPathConstants.NUMBER;
                } else if (javax.xml.xpath.XPathConstants.BOOLEAN.getLocalPart().toLowerCase().equals(type)) {
                    qname = javax.xml.xpath.XPathConstants.BOOLEAN;
                } else {
                    qname = javax.xml.xpath.XPathConstants.STRING;
                }
                javax.xml.xpath.XPath xp = javax.xml.xpath.XPathFactory.newInstance().newXPath();
                return xp.evaluate(argStr(args[1]), input, qname);
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
        // openAppIntent(p, a, param, ruleTitleKey)：8.83 JSEngine.openAppIntent
        // （JE:20047 本体＋$16 确认＋$15 拼装，账 hk-fullchain-dig2 §3）。
        // 门序照官 §3.2：先取 ruleTitle，空→直回 Boolean.FALSE（不验
        // p/a、不走任何门，C4 复核订正补落）；其后定型；宿主口径保留咱家
        // HkAuthManager 授权门（已裁记名）——官的 canTouchUI 频控
        // （1000ms、通过即占槽）与 $16 每回确认弹窗两件由授权门替代，
        // 不落；官 latch 1 分钟是本体等 $16 确认
        // 结果的结果锁存（await 后读 VarHolder），属确认机器、随门替代记名，
        // 咱家授权门自带 120 秒 latch 同位承接。其余照 $15 原文落：
        // p/a 经 tryGetString 定型（非串抛「参数类型只能是字符串」穿桥成
        // JS 异常、不回 false——官定型在 $16 确认弹窗之前，咱家定型在
        // 标题空支之后、授权门之前，同位）；action 恒
        // VIEW、flags 恒 0x10008000、组件＝包名＋类名；装机门在 param 消费
        // 之前；param 仅 JSONObject 时逐键拼 extra；启动段异常 toast
        // 「出错：」＋msg。回形：官 returnType＝BOOL——实现体回 Boolean 本体
        // （直名路 Rhino 转真布尔；包装路 retStr+''=='true' 同果），旧形回
        // "false" 字符串在直名路是真值串、已正。
        putFunction("openAppIntent", args -> {
            // 官门序第一步：先取标题，空直回 FALSE——不验 p/a、不走门。
            String ruleTitle = rule != null ? rule.getTitle() : "";
            if (ruleTitle.isEmpty()) return Boolean.FALSE;
            final String pkg = hkTryGetString(args != null && args.length > 0 ? args[0] : null);
            final String cls = hkTryGetString(args != null && args.length > 1 ? args[1] : null);
            try {
                final boolean[] result = {false};
                boolean allowed = HkAuthManager.checkAuth(ruleTitle, "openAppIntent",
                        "打开其他应用", () -> {
                            android.content.Intent intent = new android.content.Intent();
                            intent.setAction(android.content.Intent.ACTION_VIEW);
                            intent.addFlags(0x10008000);
                            // $15 装机门（param 消费之前）：未装 toast 回假、
                            // param 根本不读。
                            if (!appInstalled(pkg)) {
                                hkToast("找不到对应的软件，请确认已安装");
                                return;
                            }
                            // $15 param 消费：adjust 后仅 Map（官 JSONObject）
                            // 逐键迭代；非对象整表跳过、零 extra 零 data。
                            Object adjParam = args != null && args.length > 2
                                    ? argsNativeObjectAdjust(args[2]) : null;
                            if (adjParam instanceof Map) {
                                for (Map.Entry<?, ?> e : ((Map<?, ?>) adjParam).entrySet()) {
                                    String k = String.valueOf(e.getKey());
                                    Object v = e.getValue();
                                    if ("intentData".equals(k)) {
                                        intent.setData(android.net.Uri.parse(paramStringValue(v)));
                                    } else if (v instanceof Integer) {
                                        intent.putExtra(k, ((Integer) v).intValue());
                                    } else if (v instanceof Double || v instanceof Boolean) {
                                        intent.putExtra(k, (java.io.Serializable) v);
                                    } else {
                                        intent.putExtra(k, paramStringValue(v));
                                    }
                                }
                            }
                            // $15 组件与启动（try_start，catch Exception）：
                            // 成功置真；异常 toast「出错：」＋msg、保持假。
                            try {
                                intent.setComponent(new android.content.ComponentName(pkg, cls));
                                App.get().startActivity(intent);
                                result[0] = true;
                            } catch (Exception e) {
                                Logger.t(TAG).d("openAppIntent failed: %s", e.getMessage());
                                hkToast("出错：" + e.getMessage());
                            }
                        });
                return allowed && result[0] ? Boolean.TRUE : Boolean.FALSE;
            } catch (Throwable e) {
                Logger.t(TAG).d("openAppIntent failed: %s", e.getMessage());
                return Boolean.FALSE;
            }
        });
        // publishRule(ruleJson, ruleTitleKey)：8.83 JSEngine.publishRule（JE:20781）——
        // ruleTitle 空直返；余弹确认（标题「温馨提示」、正文「小程序『<标题>』想要提交上传
        // 规则或口令到云仓库，确定允许提交？」）后走官云仓库上传。第九阶段：授权说明改官
        // 口径「提交上传规则或口令到云仓库」、摘旧「规则发布功能开发中」假 toast；
        // 上传落点是官生态后端、星辰无此服务，确认后动作记名未落（见回执）。
        // 咱家授权弹窗本体（HkAuthManager/HkAuthDialog）为本仓既有形态，与官文案形不同，记名。
        putFunction("publishRule", args -> {
            try {
                // 官标题来源（JE:21659、getRuleTitle JE:5274）：getRuleTitle(getString(
                // 第二参 ruleTitleKey))——key 空回 ""、非空查票据表未中回 key 本身；
                // 标题空直返（key 为空时官恒直返，不看当前规则）。咱家无票据表
                // （G20 MY_TICKET 记名），非空取 key 本身；首落误用当前规则名已正。
                String key = "";
                if (args != null && args.length > 1 && args[1] != null
                        && !(args[1] instanceof Undefined)) {
                    Object a1 = argsNativeObjectAdjust(args[1]);
                    if (a1 instanceof CharSequence) key = a1.toString();
                }
                if (TextUtils.isEmpty(key)) return null;
                HkAuthManager.checkAuth(key, "publishRule",
                        "提交上传规则或口令到云仓库", () -> {
                            // 官：确认后上传规则/口令到云仓库；星辰无对应后端，落点记名未落。
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
                                // 官 findJavaClass 以 Z=1 调 loadJavaInstance：回 Class 本身
                                result[0] = resolveJavaClass(args, true);
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
                                // 官 loadJavaClass 以 Z=0 调 loadJavaInstance：回实例（回落条件见 newInstanceOrClass）
                                result[0] = resolveJavaClass(args, false);
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
        // getSearchMode()：8.83 JSEngine.getSearchMode（ReturnType.Num，Java 签名
        // 回串、预置段 parseInt）：值＝Preference "smode"（默认 0）。2026-11-11
        // 代码库批 F2 同型：实现体回 Integer，直名路 JS 得数字与官一致；
        // 反射路经 strApi 收串不变。
        putFunction("getSearchMode", args -> {
            try {
                // 环 25-9（硬门审计 B）：官 PreferenceMgr 文件＝"share_data"（JE:17783
                // 亲读），旧形误读自造 "hiker" 文件、与宿主/官口径不互通，照官改回。
                android.content.SharedPreferences sp = App.get().getSharedPreferences("share_data", android.content.Context.MODE_PRIVATE);
                int mode = sp.getInt("smode", 0);
                return Integer.valueOf(mode);
            } catch (Throwable e) {
                return Integer.valueOf(0);
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
                    // 环 25-9：同 getSearchMode，SP 文件照官 "share_data"。
                    App.get().getSharedPreferences("share_data", android.content.Context.MODE_PRIVATE)
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
        // setPageParams：8.83 JSEngine.setPageParams：String 入参直 post SetPageParamsEvent(t)；
        // 非串（对象）支走 JSONObject 串化再 post——旧形 argStr 对对象回 "" 等于丢参，照官补（第九阶段）。
        putFunction("setPageParams", args -> {
            try {
                Object a0 = args != null && args.length > 0 ? args[0] : null;
                String params;
                if (a0 == null) params = "";
                else if (a0 instanceof String) params = (String) a0;
                else params = GSON.toJson(argsNativeObjectAdjust(a0));
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
                // 环44：启动逻辑收进服务本体口 startFromJs（落 JS/求值器→在跑复用、
                // 未跑走端口回退启动）；本体未跑起回 null。
                HkProxyServer server = HkProxyServer.startFromJs(js, this);
                // 体检批六：getServerUrl 只按绑定端口拼串、未启动也非空，全端口启动
                // 失败时不许把假地址回给 JS（原版启动异常直接抛、函数不回地址）；
                // 未真跑起一律按本函数既有失败口径回 ""。
                if (server == null || !server.isRunning()) return "";
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
                // 环 25-3（硬门审计 B）：换号条件照官删 isEmpty 一格——官只认
                // null/"undefined"/"null" 三形换 UUID，空串是合法 id（注册后能被
                // unRegisterTask("") 注销）；旧形把空串也换号，空串注册/注销配对
                // 永远对不上。unRegisterTask 按 id 直查 jsTasks、无换号逻辑，
                // 对称复核无改动。
                if (id == null || "undefined".equals(id) || "null".equals(id)) {
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
                // 环19 复核订正2：捕获登记发 scope；环24-11 簇二后它只作
                // nullRuleScope 的原型基底（API 面来源），不再作规则语境。
                final Scriptable regScope = scope;
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
                                    // 环24-11 簇二（行为变化，官形）：官
                                    // registerTask 不捕获任何 scope/规则，
                                    // 到期以 getMyRule(null) 前缀求值、回调内
                                    // MY_RULE≡null。旧形以登记发 scope 求值、
                                    // 回调按登记时宿主规则取页，与官恒分叉；
                                    // 改以 nullRuleScope(regScope) 为根求值
                                    // （API 面经原型仍在、取页基底恒 null）。
                                    evalJsRooted(taskJs, nullRuleScope(regScope));
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
        // 环 16-3：摘 catch 直回 aesUtilCrypt 结果（失败回 null 照官，不再压 ""）。
        putFunction("getPrivateJS", args -> {
            if (args == null || args.length == 0 || args[0] == null) return "";
            return aesUtilCrypt(true, AES_DEFAULT_KEY, argStr(args[0]));
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
        // getAppVersion：8.83 JSEngine.getAppVersion（JE:16554，ReturnType.Num）——
        // 值＝versionCode，取不到时 v0 保持 0（旧形误回 versionName、异常回 ""，
        // 第十阶段 G22 照官正）。2026-11-11 代码库批 F2：实现体回 Integer——
        // 反射路经委托法 strApi 收串后由预置段 parseInt 成数字，两路不变；
        // 直名路（putFunction 的 BaseFunction）把返回值原样交 Rhino，
        // 回 String 时 JS 得字符串、与官数字面不符（dollar 夹具实测主路命中
        // 直名路），回 Integer 才与官一致。
        putFunction("getAppVersion", args -> {
            int code = 0;
            try {
                android.content.pm.PackageInfo pi = App.get().getPackageManager()
                        .getPackageInfo(App.get().getPackageName(), 0);
                if (pi != null) code = pi.versionCode;
            } catch (Throwable e) {
                Logger.t(TAG).d("getAppVersion failed: %s", e.getMessage());
            }
            return Integer.valueOf(code);
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
            ua.put("mobileUa", getMobileUa());
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
            // 环 25-6（硬门审计 B）：入参改官 tryGetString 同语义——无参/args[0] 为
            // null/Undefined 先行回 false；余走 hkTryGetString（非串抛「参数类型只能
            // 是字符串」）后送 detector；旧形 argStr 强转＋catch 全压 false，非串入参
            // 被静默判 false 与官分叉。detector 本体（HkRouter.isVideoOrMusicUrl）不动。
            if (args == null || args.length == 0 || args[0] == null
                    || args[0] instanceof Undefined) return Boolean.FALSE;
            return HkRouter.isVideoOrMusicUrl(hkTryGetString(args[0]));
        });
        putFunction("checkPrivacyPassword", args -> {
            // 8.83 JSEngine.checkPrivacyPassword（JE:11980）照官重做（第九阶段复核订正）：
            // tryGetString 非串抛「参数类型只能是字符串」；魔术串 "88888888088888888"
            // 是拒绝支——相等直接回 false、不解锁不查 ppwd（首落误读成直过支已正）；
            // 余以 StringUtils.equals 比 PreferenceMgr("share_data") 的 "ppwd"（缺省 null、
            // 无空串门：ppwd 显式存空串且入参空串时官回真），相等置 privacyModeUnlocked
            // 回真。官置的是 SettingConfig 静态位并有 UI 收尾 lambda，咱家无隐私模式
            // 消费方，以本运行时字段承接、UI 半记名（见回执）。
            Object a0 = args != null && args.length > 0 ? args[0] : null;
            Object adj = a0 == null ? null : argsNativeObjectAdjust(a0);
            if (!(adj instanceof CharSequence)) throw new RuntimeException("参数类型只能是字符串");
            String p = adj.toString();
            if ("88888888088888888".equals(p)) return Boolean.FALSE;
            String saved = App.get().getSharedPreferences("share_data", 0).getString("ppwd", null);
            boolean eq = p.equals(saved);
            if (eq) privacyModeUnlocked = true;
            return eq ? Boolean.TRUE : Boolean.FALSE;
        });
        putFunction("getPrivacyPasswordLen", args -> {
            // 8.83 JSEngine.getPrivacyPasswordLen：回 "share_data" 文件 "ppwd" 串长（未设 0）。
            try {
                String saved = App.get().getSharedPreferences("share_data", 0).getString("ppwd", "");
                return saved == null ? 0 : saved.length();
            } catch (Throwable e) {
                return 0;
            }
        });
        putFunction("copyFiles", args -> {
            // 8.83 JSEngine.copyFiles(p, p2, dirKey)（JE:12654）照官重做（第九阶段，复核订正）：
            // 三参任一 undefined/null 回 0；非串经 tryGetString 抛「参数类型只能是字符串」；
            // 任一串为空 toast「文件路径不能为空」回 0；源＝File(getFilePath(dirKey), p)，
            // 不存在回 0；目标——getFilePath(p2) 以 "/" 结尾才拼源名、不结尾时 p2 本身
            // 即目标全名（首落恒拼已正）；目录走 FileUtil.copyDirectiory 递归回文件计数、
            // 单文件 FileUtil.copy 回 1。
            if (args == null || args.length < 3) return 0;
            if (args[0] == null || args[1] == null || args[2] == null
                    || args[0] instanceof Undefined || args[1] instanceof Undefined
                    || args[2] instanceof Undefined) return 0;
            String p = hkTryGetString(args[0]);
            String p2 = hkTryGetString(args[1]);
            String dirKey = hkTryGetString(args[2]);
            if (p.isEmpty() || p2.isEmpty() || dirKey.isEmpty()) {
                hkToast("文件路径不能为空");
                return 0;
            }
            File src = new File(resolveHikerPath(dirKey), p);
            if (!src.exists()) return 0;
            String dstBase = resolveHikerPath(p2);
            File dst = dstBase.endsWith("/") ? new File(dstBase, src.getName()) : new File(dstBase);
            if (src.isDirectory()) {
                // 目录支保持吞异常回计数（官 FileUtil.copyDirectiory 内部自收口）。
                try {
                    return copyDirectoryCount(src, dst);
                } catch (Throwable e) {
                    Logger.t(TAG).d("copyFiles dir failed: %s", e.getMessage());
                    return 0;
                }
            }
            // 环 25-4（硬门审计 B）：文件支移出 catch-all——官 FileUtil.copy 的
            // IOException 经反射桥穿出成 JS 异常，旧形 catch 全压回 0、规则判不出
            // 拷贝失败。咱家以 RuntimeException 包裹 IOException 穿出 putFunction
            // 咽喉（裹成 WrappedException 成 JS 异常，与官等价）。路径口径不动
            // （环 16-2 未裁，维持 resolveHikerPath 现行）。
            try {
                if (dst.getParentFile() != null) dst.getParentFile().mkdirs();
                java.nio.file.Files.copy(src.toPath(), dst.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return 1;
            } catch (java.io.IOException ioe) {
                throw new RuntimeException(ioe);
            }
        });
        putFunction("writeHexFile", args -> {
            // 8.83 JSEngine.writeHexFile(filePath, content)（JE:26874）照官重做（第九阶段
            // 复核订正）：闸是排除形——getFilePath(path) contains(getFilesDir()) 或
            // inJsDir(path) 命中任一即静默不写，两处都不在才写（与 stringToFile 同闸，
            // hkStringToFile 的 title 空支同形）；首落误读成白名单且 filesDir 取错宿主
            // 内部路径，已按官收回（filesDir 走 hkFilesDirPrefix 官形转写）。
            // hex 解码照 StringUtil.hexToBytes：空串回 null 不写、不去空白、
            // 奇数长/坏字抛异常穿出（此处不吞）。
            if (args == null || args.length < 2) return null;
            String path = resolveHikerPath(argStr(args[0]));
            if (path.contains(hkFilesDirPrefix()) || hkInJsDir(path)) return null;
            String hex = argStr(args[1]);
            if (hex.isEmpty()) return null;
            byte[] data = new byte[hex.length() / 2];
            for (int i = 0; i < hex.length(); i += 2) {
                // 奇数长末位 substring(i, i+2) 越界抛 StringIndexOutOfBounds，与官同穿出
                data[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
            }
            try {
                java.nio.file.Path p = new File(path).toPath();
                if (p.getParent() != null) java.nio.file.Files.createDirectories(p.getParent());
                java.nio.file.Files.write(p, data);
            } catch (java.io.IOException e) {
                Logger.t(TAG).d("writeHexFile failed: %s", e.getMessage());
            }
            return null;
        });
        putFunction("shareDirectory", args -> {
            // 8.83 JSEngine.shareDirectory(p, p2)（JE:25303）照官重做（第九阶段，复核订正）：
            // p undefined/null 静默回；空串 toast「文件夹路径不能为空」；路径非目录
            // （含不存在）toast「<名>文件夹不存在」；空目录 toast「<名>文件夹为空」；
            // zip 名——p2 非空原样作全名、p2 缺/空才拼「目录名.hk合集.zip」（首落恒加
            // 后缀已正），落 hiker://files/_cache（已存在先删），压缩失败 toast
            // 「压缩文件夹失败」；成后 UI 线程 ShareUtil 形分享（ACTION_SEND、type 按
            // zip 扩展名取 MIME、chooser 标题「请选择应用」）。
            try {
                if (args == null || args.length == 0) return null;
                if (args[0] == null || args[0] instanceof Undefined) return null;
                String p = argStr(args[0]);
                // 官 isUndefined 含字面串 "undefined" 支：同静默回
                if ("undefined".equals(p)) return null;
                if (TextUtils.isEmpty(p)) {
                    hkToast("文件夹路径不能为空");
                    return null;
                }
                File dir = new File(resolveHikerPath(p));
                if (!dir.isDirectory()) {
                    hkToast(dir.getName() + "文件夹不存在");
                    return null;
                }
                File[] files = dir.listFiles();
                if (files == null || files.length == 0) {
                    hkToast(dir.getName() + "文件夹为空");
                    return null;
                }
                String zipName = args.length > 1 && args[1] != null && !(args[1] instanceof Undefined)
                        && !TextUtils.isEmpty(argStr(args[1]))
                        ? argStr(args[1]) : dir.getName() + ".hk合集.zip";
                File zip = new File(resolveHikerPath("hiker://files/_cache/" + zipName));
                if (zip.getParentFile() != null) zip.getParentFile().mkdirs();
                if (zip.exists()) zip.delete();
                if (!zipDirectoryContents(dir, zip)) {
                    hkToast("压缩文件夹失败");
                    return null;
                }
                String mime = android.webkit.MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension("zip");
                android.content.Intent it = new android.content.Intent(android.content.Intent.ACTION_SEND);
                it.setType(mime != null ? mime : "application/zip");
                it.putExtra(android.content.Intent.EXTRA_STREAM,
                        com.fongmi.android.tv.utils.FileUtil.getShareUri(zip));
                it.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                android.content.Intent chooser = android.content.Intent.createChooser(it, "请选择应用");
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try {
                        App.get().startActivity(chooser);
                    } catch (Throwable ignored) {
                    }
                });
            } catch (Throwable e) {
                Logger.t(TAG).d("shareDirectory failed: %s", e.getMessage());
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
            // 8.83 JSEngine.buildWebDav(url, user, pwd)：构造 WebDavHolder 供 JS 链式调用
            // （child/list/upload/download/makeDir/delete）。咱家 HkWebDav 以 OkHttp
            // PROPFIND/WebDAV 方法承接同名同形（第九阶段；字段集与差量见回执）。
            if (args == null || args.length < 3) return null;
            return HkWebDav.build(argStr(args[0]), argStr(args[1]), argStr(args[2]));
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
            // 8.83 JSEngine.createQRCodeToFile(text)（JE:13030）照官（第九阶段，复核订正）：
            // 文本空/"null"/undefined → 无条件回 "toast://二维码内容不能为空"（官无
            // Activity 判据，首落误加已正）；边长＝屏宽 min/3*2（ScreenUtil.getScreenMin
            // 公式）；图生成失败回 "toast://生成二维码失败"、写文件失败回
            // "toast://生成二维码文件失败"（官两串分立）；落点 UriUtils.getTempCachePath
            // ＝hiker://files/_cache/<毫秒>.jpeg，回 "file://"+路径。
            String text = args != null && args.length > 0 ? argStr(args[0]) : "";
            // 官 getString 后 isEmpty/"null"/isUndefined 三支并归空（isUndefined 含字面串支）
            if (TextUtils.isEmpty(text) || "null".equals(text) || "undefined".equals(text)) {
                return "toast://二维码内容不能为空";
            }
            android.util.DisplayMetrics dm = App.get().getResources().getDisplayMetrics();
            int size = Math.min(dm.widthPixels, dm.heightPixels) / 3 * 2;
            android.graphics.Bitmap bmp = com.fongmi.android.tv.utils.QRCode.getBitmap(text, size, 2);
            if (bmp == null) return "toast://生成二维码失败";
            File f = new File(resolveHikerPath(
                    "hiker://files/_cache/" + System.currentTimeMillis() + ".jpeg"));
            try {
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out);
                out.close();
            } catch (Throwable e) {
                return "toast://生成二维码文件失败";
            }
            return "file://" + f.getAbsolutePath();
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
            // 8.83 JSEngine.getEpubChapters：returnType=JSON，LocalEpub 章节表
            // {title,url,index,nextUrl,startFragmentId,endFragmentId}。咱家 HkEpubBridge
            // 以本仓 LocalEpub spine 形承接（第九阶段；fragment 两字段回空、内容回 XHTML 原文）。
            String path = args != null && args.length > 0 ? resolveHikerPath(argStr(args[0])) : "";
            return parseJson(HkEpubBridge.chaptersJson(path));
        });
        putFunction("getEpubContent0", args -> {
            // 8.83 JSEngine.getEpubContent0(path, url)：回章节 XHTML 原文（HkEpubBridge 承接）。
            String path = args != null && args.length > 0 ? resolveHikerPath(argStr(args[0])) : "";
            String url = args != null && args.length > 1 ? argStr(args[1]) : "";
            return HkEpubBridge.content(path, url);
        });
        putFunction("getEpubMetadata", args -> {
            // 8.83 JSEngine.getEpubMetadata：returnType=JSON，元数据对象（HkEpubBridge 承接 {title}）。
            String path = args != null && args.length > 0 ? resolveHikerPath(argStr(args[0])) : "";
            return parseJson(HkEpubBridge.metadataJson(path));
        });
        putFunction("initChaquopy", args -> {
            // 8.83 JSEngine.initChaquopy（smali 18247，returnType=VOID）：入参
            // undefined/null 静默返回；否则 getFilePath 归一路径后装载插件自备的
            // chaquopy.apk（官包本体直读订正：root 前缀＋是否含 /_cache/、
            // _fileSelect_ 三段判据决定的是「直用原路径还是先拷到 <根>/chaquopy.apk
            // 再装载」，不是硬门账旧述的「不合闸静默跳过」；装载异常只 Timber.e）。
            // 咱家路线（批C3 定）：改走星辰自带 Chaquopy（:chaquo 模块，与点播 py
            // 蜘蛛同一运行时，Python 3.10）——包内无外部 apk 可装，装载动作等价为
            // 「确保自带 Python 运行时已启动」；入参为空仍照官静默、启动失败照官
            // catch 形只记日志不抛（JS 侧无从知晓，后续 findJavaClass 取不到类才暴露）。
            if (args == null || args.length == 0 || args[0] == null || isUndefined(args[0])) return null;
            try {
                // warmUp 幂等（Loader 内部 synchronized＋单例判空，已启动时空转），
                // 与 App 冷启动预热同一口；未启动时在此把自带运行时拉起。
                com.fongmi.chaquo.Loader.warmUp();
            } catch (Throwable t) {
                Logger.t(TAG).e(t, "initChaquopy(builtin) failed");
            }
            return null;
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
     * base64 出入（Android Base64 NO_WRAP）；UTF-8。原版无 SHA1PRNG、无 hex。
     * 环 16-3（硬门审计 B）：失败语义照官 AesUtil——内部 catch 回 null 不抛
     * （官 encrypt/decrypt 双向同形）；旧形 throws 穿出、再由各消费点自设 catch
     * 回 "" 顶掉，与官的 null 语义在 evalPrivateJS（null→不求值回 null）、
     * hkFileToString（null 直回）、hkStringToFile（null 交底层写抛 NPE）三处分叉。 */
    private String aesUtilCrypt(boolean encode, String key, String data) {
        try {
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
        } catch (Throwable e) {
            return null;
        }
    }

    /** W2：原版 fc 语义（require0，useCallback=false）——返回代码字符串，不求值。
     * 参数：url, h(小时，默认24，0=强制重下), options, v(版本号，默认0)。
     * require0 已有忠实移植（D1-C），fc 直接复用：js:/file:///hiker://page/ 前缀、
     * 本地 libs/<md5>.js 缓存＋版本校验、按 3600000*h 过期、h==0 永远重下。
     * B-10 参数对位（官 fetchCache 本体 JE:15415-15435 亲读）：官调 require0 前
     * 先自 evalKey（fc 第六参）的 parentScope 取 MY_RULE 作 require0 的 p1、
     * ruleTitle＝getRuleTitle(getString(第五参 ruleTitleKey))；require0 全参表
     * p1=MY_RULE、p2=c、p3=h、p4=options、p5=evalKey、p6=false、p7=ruleTitle、
     * p8=v。咱家旧形 p1 误传 fetch options（options 无规则 proxy、fc 下载路
     * 恒读不到规则级 proxy）、ruleTitle 误读实例 rule 字段、第五/六参根本不读。
     * 改：p1 经 myRuleObjFromScope 取当发求值 scope 的 MY_RULE 原对象（与
     * myRuleFromScope 同路：ambient 登记优先、回落主 scope——官 evalKey 的
     * parentScope 在咱家主路无对应物，以当发登记 scope 为同位取法）、ruleTitle
     * 经 fcRuleTitle 读第五参隐参通道、p5 透传第六参 evalKey（useCallback=false
     * 时 require0 不消费此参、仅对位）。v 的取值法与失败收口已由硬门审计 B
     * 环 24-8/24-9 接管落码（见 fcImpl 内注）。 */
    private String fcImpl(Object[] args) {
        if (args == null || args.length == 0) return "";
        // 环 24-8/24-9（硬门审计 B）：v 的取值改 hkIntValue 同语义（官桥层 defaultInt
        // 转换：null/undefined→缺省 0、Number→intValue、数字串→parseInt、非数字串
        // NumberFormatException 穿出；旧 (int) toDouble 对 "3" 得 3.0→3、对 "abc" 得
        // NaN→0 静默，与官分叉）。catch-回-"" 整段摘除：官 fetchCache 本体无 catch、
        // require0 异常经桥层成 JS 异常上抛，旧形把过期/下载/求值失败全压成 ""
        // （规则 typeof 判不出失败）。咱家内部直调方仅 fc/fetchCache 两注册点
        // （putFunction 咽喉自会把穿出的异常裹成 JS 异常），无依赖 "" 收口的调用方。
        String url = argStr(args[0]);
        int hours = args.length > 1 ? cacheHoursValue(args[1]) : 24;
        Object options = args.length > 2 ? args[2] : null;
        int v = args.length > 3 ? hkIntValue(args[3], 0) : 0;
        Object myRule = myRuleObjFromScope();
        Object evalKey = args.length > 5 ? args[5] : null;
        String ruleTitle = fcRuleTitle(args);
        // 复核订正 24-9 真编：require0 声明 throws Exception，本体无 catch 无 throws
        // 直调时 javac 报 unreported exception（真编不过）。fc/fetchCache 的注册
        // lambda 走 BaseFunction.call、无 checked 异常出口，故在此 catch 后以
        // RuntimeException 包裹重抛——putFunctionEx 咽喉会把 RuntimeException 裹成
        // WrappedException 成 JS 异常，官「require0 异常穿出到规则 catch」的失败
        // 呈现不变。不许吞异常回 ""（那正是 24-9 旧病）。
        Object r;
        try {
            r = require0(myRule, url, hours, options, evalKey, false, ruleTitle, v);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return r == null ? "" : String.valueOf(r);
    }

    /** B-10：fc 的 ruleTitle 取法——官 getRuleTitle(getString(ruleTitleKey))
     * （JE:15441、getRuleTitle JE:5274-5319）：隐参缺位（解析回 null）或为空时
     * 照官原样回 key 本身（null / ""），官无任何标题补位支；隐参非空时先查
     * ticketMap（票据→标题，本类 generateTicket 同表）、未中/票据空回隐参本身。 */
    private String fcRuleTitle(Object[] args) {
        String key = args != null && args.length > 4 ? getString(args[4]) : null;
        // 与 hkRuleTitle 同一还原口（环37 收口：两处同表同逻辑，不许再分叉）。
        return hkRuleTitle(key);
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
            // B-4 捕获点：提交任务前在调用线程（当发求值线程）捕获当发登记
            // scope，随任务带入池线程；捕获不到（null）时池线程不登记、
            // myRuleFromScope 回落主 scope，与改前行为同。
            final Scriptable capturedScope = ambientEvalScope.get();
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
                        // B-4 登记点：把捕获的当发 scope 登记为本池线程的
                        // ambient scope，任务执行期 fetchByHiker→handlePageRequest
                        // 经 myRuleFromScope 读到的即当发子规则而非宿主规则；
                        // finally 恢复点与主路 enter/exitAmbientScope 同形栈式恢复。
                        Scriptable ambPrev = enterAmbientScope(capturedScope);
                        try {
                            // 8.83 batchFetch 每项调 fetch 本体：内部协议项同走
                            // fetchByHiker 分发（JE:11648 区；G8）
                            if (u.startsWith("hiker://") || u.startsWith("file://")) {
                                Object r = fetchByHiker(u, null, optFlagFromJson(opt, "toHex"), optFlagFromJson(opt, "inputStream"));
                                if (r instanceof String) results.put(idx, (String) r);
                                else { closeFetchStream(r); results.put(idx, ""); }
                            } else {
                                Object r = fetchSync(u, opt);
                                if (r instanceof String) results.put(idx, (String) r);
                                else { closeFetchStream(r); results.put(idx, ""); }
                            }
                        }
                        catch (Throwable t) { results.put(idx, ""); }
                        finally {
                            exitAmbientScope(ambPrev);
                            latch.countDown();
                        }
                    });
                }
                // 原版：await(n/16*10+10, SECONDS)（smali:2260）
                latch.await((long) (n / 16 * 10 + 10), TimeUnit.SECONDS);
                // 8.83：缺位项保持 null（JE 组装段亲读）——此前补 ""，规则分不清
                // 「取到空正文」与「没取到」（G8）
                List<Object> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) out.add(results.get(i));
                return toJsArrayObj(out);
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

    /** 环22 对官（8.83 JSEngine.requireCache，smali:22956）：与 require 同路调
     * require0 本体——p1＝当发 scope 的 MY_RULE、h 经 cacheHoursValue 官式
     * adjust（Integer/Double 取 intValue、余恒 24）、options、useCallback=true、
     * v、ruleTitle 经 fcRuleTitle 同 require/fc 路（第五参 ruleTitleKey）。
     * 旧形走自造 memCache 路（进程内存表代官磁盘 libs）：options.proxy 不读、
     * v 不读、无 md5 磁盘缓存/描述文件、无毒内容回退、无登记副作用，与
     * require0/fc 落盘的库互不可见——已整支收编，memCache 的 rc 路退役。
     * 失败呈现：require0 本体不加软化（官抛错点原样）；软回只留在本入口层，
     * 与 require 入口既定口径同层同口径（断点 2 记名口径，见 require 处注）。
     * 回调求值照 require 入口同式：BaseFunction 在调用方作用域求值库代码，
     * 库顶层声明与调用方互见（require0 useCallback=true 的官分工）。 */
    private Object requireCacheEval(Object[] args, Context cx, Scriptable callerScope) {
        if (args == null || args.length == 0) return null;
        try {
            String url = argStr(args[0]);
            int hours = args.length > 1 ? cacheHoursValue(args[1]) : 24;
            Object options = args.length > 2 ? args[2] : null;
            // 复核订正7：v 改走 hkIntValue（官 getIntValue 同形），不再 toDouble 截断。
            int v = args.length > 3 ? hkIntValue(args[3], 0) : 0;
            Object myRule = myRuleObjFromScope();
            String ruleTitle = fcRuleTitle(args);
            Function evalCallback = new BaseFunction() {
                @Override
                public Object call(Context c, Scriptable s, Scriptable t, Object[] a) {
                    if (a == null || a.length == 0 || a[0] == null) return null;
                    return evalJsInScope(cx, callerScope, String.valueOf(a[0]), "hk-plugin");
                }
            };
            return require0(myRule, url, hours, options, evalCallback, true, ruleTitle, v);
        } catch (Throwable e) {
            Logger.t(TAG).d("requireCache failed (soft): %s", e.getMessage());
            return null;
        }
    }

    /** 8.83 JSEngine.tryGetString 等价：先走 argsNativeObjectAdjust 整理（ConsString 取真串、
     * NativeJavaObject 拆包、NativeObject/Array 转集合），整理后仍非字符串（含缺参/undefined）抛错。 */
    private static String mustBeString(Object[] args, int i) {
        if (args == null || args.length <= i) throw new RuntimeException("参数类型只能是字符串");
        Object o = argsNativeObjectAdjust(args[i]);
        if (!(o instanceof String)) throw new RuntimeException("参数类型只能是字符串");
        return (String) o;
    }

    /** G45：8.83 StringUtil.md5(String) 全形（smali_classes2
     * StringUtil:4595-4756）——非空且以 file:/// 或 hiker://files/
     * 开头时经 getFilePath 定位、FileInputStream 以 10MiB 缓冲
     * 分段 update；余走字符串 getBytes()（平台缺省字符集）；
     * 输出 convertByte2String 小写 hex。任一步异常时官本体自带
     * 收口：空串回 ""、非空回原串的 Base64(NO_WRAP) 串（此为官
     * catch 原形、随官落，非自加兜底）。 */
    private static String hkMd5(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            if (s != null && !s.isEmpty()
                    && (s.startsWith("file:///") || s.startsWith("hiker://files/"))) {
                String path = getFilePath(s);
                try (java.io.FileInputStream fis =
                             new java.io.FileInputStream(new java.io.File(path))) {
                    byte[] buf = new byte[0xa00000];
                    int n;
                    while ((n = fis.read(buf)) > 0) md.update(buf, 0, n);
                }
            } else {
                md.update(s.getBytes());
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xf, 16));
                sb.append(Character.forDigit(b & 0xf, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            if (s == null || s.isEmpty()) return "";
            return new String(Base64.encode(s.getBytes(), Base64.NO_WRAP));
        }
    }

    /** 8.83 JSEngine.getRuleTitle(ruleTitleKey) 等价：空则取当前规则标题，否则按给定标题取规则数据目录。
     * 环 16-1（硬门审计 B）：落点改官形私有根 hkRootDir()+"/rules/files/"+标题原串+"/"
     * （与 hkFilesDirPrefix 同根）——旧形走 HkRuleManager.getDataDir 的内部根，与
     * hiker://files/ 的外部根跨面契约对不上：saveFile 写的裸名件 fetch 取不回、
     * fileToString 的私有封锁也封不住它。消费点仅 readFile/saveFile/deleteFile 三处
     * 裸名支。HkRuleManager.getDataDir 本体不动（storage/删规则/kvFile 维持内部根）。
     * 本项不做存量迁移：旧内部根里的裸名件一次性重置（账面记名知会）。 */
    private File ruleDirForTitle(String rt) {
        String title = (rt == null || rt.isEmpty()) ? (rule != null ? rule.getTitle() : "") : rt;
        return new File(hkRootDir() + "/rules/files/" + title + "/");
    }

    /**
     * 8.83 M3u8Utils.downloadM3u8 等价：只缓存索引文件（片段不下载），相对路径改写为绝对地址，
     * 嵌套子 m3u8 递归跟进；写到 hiker://files/_cache/<fileName>，返回 "file://<绝对路径>"。
     * 非 m3u8 地址（mp4/flv/m4a/mp3）直接回原 url。
     * 环 25-2（硬门审计 B）失败形照官重排：官 downloadM3u8 全形回原 url 不抛——
     * url 空回 url；fetch 失败回 url；响应头门（官落点在 HttpHelper$1 拦截器位：
     * content-type 含 text/html 时不读体）→ body 空回 url；body 以 url 起头回 url；
     * realUrl 非 http 开头回 url；body 不含 "#EXT" 回 url；落盘前对改写后内容再过
     * contains("#EXT") 门。旧形三处抛 RuntimeException（地址为空/下载失败/内容
     * 为空）与官分叉，cacheM3u8 规则侧会收到异常而非原 url。cacheM3u8 与
     * batchCacheM3u8 共用本 helper，一次落齐（batch 单项 catch 置 null 保留）。
     */
    private String downloadM3u8File(String url, String fileName, String optionsJson) {
        if (TextUtils.isEmpty(url)) return url;
        if (isNotM3u8Url(url)) return url;
        String result;
        try {
            result = (String) fetchSync(url, withHeadersOpt(optionsJson));
        } catch (Throwable e) {
            return url;
        }
        String body = "";
        String realUrl = url;
        boolean htmlGate = false;
        try {
            Map<String, Object> map = GSON.fromJson(result,
                    new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
            if (map != null) {
                // 响应头门：headers 里 content-type 含 text/html → 不读体（按 body 空支回 url）
                Object hs = map.get("headers");
                if (hs instanceof Map) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) hs).entrySet()) {
                        if (e.getKey() != null && "content-type".equalsIgnoreCase(String.valueOf(e.getKey()))
                                && e.getValue() != null
                                && String.valueOf(e.getValue()).contains("text/html")) {
                            htmlGate = true;
                            break;
                        }
                    }
                }
                if (!htmlGate) {
                    Object b = map.get("body");
                    if (b != null) body = String.valueOf(b);
                }
                Object u = map.get("url");
                if (u != null && !TextUtils.isEmpty(String.valueOf(u))) realUrl = String.valueOf(u);
            }
        } catch (Throwable ignored) {
        }
        if (TextUtils.isEmpty(body)) return url;
        if (body.startsWith(url)) return url;
        if (!realUrl.startsWith("http")) return url;
        if (!body.contains("#EXT")) return url;
        String content = convertM3u8Paths(body, realUrl, fileName, optionsJson);
        if (content == null || !content.contains("#EXT")) return url;
        // 落点照官 M3u8Utils.downloadM3u8：getFilePath("hiker://files/_cache/"+fileName)
        // （旧形写 hiker://files/cache/，第九阶段复核订正）。
        String abs = resolveHikerPath("hiker://files/_cache/" + fileName);
        File f = new File(abs);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        writeFileContent(abs, content);
        Logger.t(TAG).d("downloadM3u8File ok: %s", abs);
        return "file://" + abs;
    }


    /** 8.83 tryGetString 等价：adjust 后须为 CharSequence，非串抛「参数类型只能是字符串」。 */
    private static String hkTryGetString(Object o) {
        Object adj = o == null ? null : argsNativeObjectAdjust(o);
        if (!(adj instanceof CharSequence)) throw new RuntimeException("参数类型只能是字符串");
        return adj.toString();
    }

    /** openAppIntent $15 装机门等价（官 PlayerChooser.appInstalledOrNot）：
     * PackageManager 查包在场与否；空包名/异常恒不在场。 */
    private static boolean appInstalled(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            return App.get().getPackageManager().getPackageInfo(pkg, 0) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    /** openAppIntent $15 的 getString(k) 等价（官 fastjson JSONObject.getString
     * 收敛）：String 原样、Map/List 出 JSON 文本、null 回 null、余 String.valueOf
     * （数字/布尔的词形）。入参为 argsNativeObjectAdjust 后的值。 */
    private static String paramStringValue(Object v) {
        if (v == null) return null;
        if (v instanceof String) return (String) v;
        if (v instanceof Map || v instanceof java.util.List) return GSON.toJson(v);
        return String.valueOf(v);
    }

    /** 主线程短 toast（与 putFunction("toast") 同形）。 */
    private static void hkToast(String msg) {
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    ToastUtil.show(App.get(), msg, android.widget.Toast.LENGTH_SHORT));
        } catch (Throwable ignored) {
        }
    }

    /** FileUtil.copyDirectiory 等价：递归拷贝目录，回成功拷贝的文件数。 */
    private static int copyDirectoryCount(File src, File dst) throws java.io.IOException {
        int count = 0;
        File[] children = src.listFiles();
        if (children == null) return 0;
        dst.mkdirs();
        for (File child : children) {
            File target = new File(dst, child.getName());
            if (child.isDirectory()) {
                count += copyDirectoryCount(child, target);
            } else {
                java.nio.file.Files.copy(child.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        }
        return count;
    }

    /** 目录递归打包（条目名相对目录本身）；任一失败回 false。 */
    private static boolean zipDirectoryContents(File dir, File zipFile) {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                new java.io.FileOutputStream(zipFile))) {
            zipInto(dir, dir, zos);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static void zipInto(File root, File current, java.util.zip.ZipOutputStream zos) {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = root.toPath().relativize(child.toPath()).toString()
                    .replace(File.separatorChar, '/');
            if (child.isDirectory()) {
                File[] sub = child.listFiles();
                if (sub == null || sub.length == 0) {
                    // 官 ZipUtils 对空子目录发「子目录/」目录条目
                    try {
                        zos.putNextEntry(new java.util.zip.ZipEntry(name + "/"));
                        zos.closeEntry();
                    } catch (Throwable ignored) {
                    }
                } else {
                    zipInto(root, child, zos);
                }
            } else {
                // 官 ZipUtils 逐文件吞错、整体仍回真
                try {
                    zos.putNextEntry(new java.util.zip.ZipEntry(name));
                    java.nio.file.Files.copy(child.toPath(), zos);
                    zos.closeEntry();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 8.83 M3u8Utils.isNotM3u8 等价。环 25-2 补 "#isM3u8#" 支：含之恒判 m3u8
     * （强制标记，先于五检——带标记的 mp4/flv 地址也按 m3u8 下载清洗）。 */
    private static boolean isNotM3u8Url(String url) {
        if (url.contains("#isM3u8#")) return false;
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
        String result = (String) fetchSync(url, withHeadersOpt(optionsJson));
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
    /** 当发求值 scope 的 MY_RULE 原对象（JS 对象本体）：读序与 myRuleFromScope
     * 同路——先 ambientEvalScope 登记的当发 scope、读不到回落主 scope。
     * myRuleFromScope 把它转 HkRule 供取页；fc→require0 的 p1 须传原对象
     * （proxyRequire 经 argsNativeObjectAdjust 读其 "proxy" 键，B-10）。 */
    private Object myRuleObjFromScope() {
        try {
            Scriptable amb = ambientEvalScope.get();
            if (amb != null) {
                Object v = ScriptableObject.getProperty(amb, "MY_RULE");
                if (v != Scriptable.NOT_FOUND && !(v instanceof Undefined)) {
                    // 在场即权威（含 JS null）：registerTask 官形回调的
                    // MY_RULE≡null 语境（环24-11 簇二）靠此成立——值为 null
                    // 时回 null、不许回落主 scope 把宿主规则带回来；缺位
                    // （NOT_FOUND）/Undefined 才回落，与改前同。
                    return v;
                }
            }
            if (scope == null) return null;
            Object v = ScriptableObject.getProperty(scope, "MY_RULE");
            if (v == null || v == Scriptable.NOT_FOUND || v instanceof Undefined) return null;
            return v;
        } catch (Throwable e) {
            return null;
        }
    }

    /** ambient 登记 scope 上 MY_RULE 在场且为 JS null（getProperty 回 Java
     * null 而非 NOT_FOUND，本机 Rhino 探针实证）：取页基底须维持 null 规则、
     * 不许回落宿主规则（环24-11 簇二 registerTask 官形）。 */
    private boolean ambientRuleIsNull() {
        try {
            Scriptable amb = ambientEvalScope.get();
            if (amb == null) return false;
            return ScriptableObject.getProperty(amb, "MY_RULE") == null;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 环24-11 簇一：显式 ruleKey → HkRule（官 fetch0 的 delegate 捕获形同位）。
     * 包装层覆写的 MY_RULE 原对象（Scriptable）经 jsStringify、转录 delegate
     * 已 adjust 的 Map/List 经 GSON 串化、String 直用，统一走 ruleFromJsJson
     * （与 myRuleFromScope 同一转换口，含官 FastJSON 宽容收敛）；Java 侧已成
     * HkRule 的（require0 的 ruleObj 路）直用。不在场/不可转回 null，调用方
     * 回落 ambient。
     */
    private HkRule ruleFromExplicitKey(Object ruleKey) {
        try {
            if (ruleKey == null || ruleKey instanceof Undefined) return null;
            if (ruleKey instanceof HkRule) return (HkRule) ruleKey;
            String json;
            if (ruleKey instanceof Scriptable) json = jsStringify(ruleKey);
            else if (ruleKey instanceof Map || ruleKey instanceof java.util.List) json = GSON.toJson(ruleKey);
            else if (ruleKey instanceof String) json = (String) ruleKey;
            else return null;
            if (json == null || json.isEmpty() || "null".equals(json)) return null;
            return ruleFromJsJson(json);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * G1（官方 JSEngine.fetchByHiker 的 ruleKey 通道）：官方包装层每发把 JS 侧
     * MY_RULE 对象当 ruleKey 显式传进 fetch 管线，page 支按该对象（而非宿主当前
     * 规则）的 pageList 取页——聚合壳子规则 find_rule 里 MY_RULE 已被换成子规则
     * 对象，request('hiker://page/hs') 无 ?rule= 参数时取的就是子规则自己的页。
     * 咱家主路此前 fetchByHiker 传 null、handlePageRequest 只认 ?rule= 标题查库、
     * 无参回退宿主规则 → 子规则页查不到回 "" → JSON.parse("") 引爆（Empty JSON 案）。
     * 此处从当前 scope 读回 JS 侧 MY_RULE 对象转 HkRule 作取页基底。
     *
     * 2026-11-11 订正（122 真机同案复发钉根）：「当前 scope」必须是当发求值
     * scope 而非主 scope——主 scope 的 MY_RULE 由 setContext 写入、恒为宿主
     * 规则；聚合壳在求值内的劫持赋值落在当发子 scope（原型遮蔽），只读主
     * scope 永远读不到子规则，G1 初版因此只在数据层验过、真机仍回 ""。
     * 改读 ambientEvalScope 登记的当发 scope（见字段注），读不到再回落主
     * scope（与订正前行为同）。
     */
    private HkRule myRuleFromScope() {
        try {
            Object v = myRuleObjFromScope();
            if (v == null) return null;
            String json = jsStringify(v);
            if (json == null || json.isEmpty()) return null;
            return ruleFromJsJson(json);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * JS 侧 MY_RULE 对象串 → HkRule 的转换（myRuleFromScope 专用），照官
     * FastJSON 语义收敛 String 字段：官 fetchByHiker 以 FastJSON
     * parseObject(ruleKey 串, ArticleListRule.class) 转换，其 String 字段
     * 反序列化（DefaultJSONParser.parseString，官包 .line 1549–1561）遇非串
     * token 一律宽容收敛、永不抛错；咱家 Gson 遇 BEGIN_OBJECT 直接抛
     * Expected-a-string，聚合壳 hijackEnv 的 {@code MY_RULE.params = {}}
     * （详情支是 el.extra 对象）即整单毒死回 null（钉根见
     * worknotes/hiker-route-agg-subpage-receipt-20261111 钉根 1）。
     * 故先把 JSON 树里 HkRule 的 String 型字段按同语义收敛成串再交给 Gson
     * （收敛细则见 coerceStringFields 与各 helper 注：整数原文、浮点经
     * Double 归一化、对象/数组经 HashMap 承载重序列化、布尔 "true"/"false"；
     * 本就是字符串的值逐字不动）。按字段类型处理、不点名字段；带
     * @JsonAdapter 的字段（如 version）自有宽松适配器、永不因非串值抛错，
     * 不纳入收敛。真坏 JSON 仍在 JsonParser/Gson 处抛错、由
     * myRuleFromScope 照旧回 null。
     * <p>末步刻意走回 GSON.fromJson(String) 的 Reader 路而非树路：官非
     * String 数值字段并不宽容——weight(int) 在官走 IntegerCodec（官包
     * ParserConfig 把 Integer.TYPE 注册给 IntegerCodec.instance）：浮点
     * token 经 BigDecimal.intValueExact() 非整数即抛（IntegerCodec
     * .line 102–107）、整数 token 溢出经 JSONLexer.intValue() 抛
     * NumberFormatException 再裹成 JSONException（.line 94–97），整单失败；
     * 旧 Gson 串路同为抛错回 null，而树路 JsonTreeReader 会静默截断/回绕
     * （7.9→7、2147483648→-2147483648）。收敛后的 String 字段已是串值、
     * 走哪条路结果不变，故末步回串路把非 String 字段的旧语义一并恢复。
     */
    private static HkRule ruleFromJsJson(String json) {
        com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(json);
        if (el != null && el.isJsonObject()) {
            coerceStringFields(el.getAsJsonObject());
        }
        if (el == null) return null;
        return GSON.fromJson(el.toString(), HkRule.class);
    }

    /** 把 JSON 对象里 HkRule 的 String 型字段（按 @SerializedName 名匹配）的
     * 非串值收敛成串：对象/数组→官形 JSON 文本（fastJsonText），数字→官
     * parseString 词形（fastJsonScalarText），布尔→"true"/"false"；字符串
     * 与 null 原样不动。字段清单以反射读 HkRule 声明为准，新增 String 字段
     * 自动纳入；@JsonAdapter 字段跳过（见 ruleFromJsJson 注）。 */
    private static void coerceStringFields(com.google.gson.JsonObject obj) {
        for (java.lang.reflect.Field f : HkRule.class.getDeclaredFields()) {
            if (f.getType() != String.class) continue;
            if (f.isAnnotationPresent(com.google.gson.annotations.JsonAdapter.class)) continue;
            com.google.gson.annotations.SerializedName sn =
                    f.getAnnotation(com.google.gson.annotations.SerializedName.class);
            String name = sn != null ? sn.value() : f.getName();
            if (!obj.has(name)) continue;
            com.google.gson.JsonElement v = obj.get(name);
            if (v == null || v.isJsonNull()) continue;
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) continue;
            if (v.isJsonObject() || v.isJsonArray()) obj.addProperty(name, fastJsonText(v));
            else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber())
                obj.addProperty(name, fastJsonScalarText(v.getAsString()));
            else obj.addProperty(name, v.getAsString());
        }
    }

    /** 官 parseString 的数字支（DefaultJSONParser.smali .line 1549–1561）：
     * 整数 token（词法无 '.' 且无指数部，scanNumber 的 isDouble 未置位）走
     * numberString() 取原文；浮点 token 落 parse()→Object.toString()，而
     * parse() 的浮点支（.line 1409–1411）是 decimalValue(UseBigDecimal=
     * false)——末字 'F' 取 Float、'D' 取 Double，其余取 Double（JSON 数字
     * 无 F/D 后缀，故恒 Double 支）；decimalValue 的短尾数优化支是整数尾数
     * ÷10 的整数次幂的精确商取最近 double，与 Double.parseDouble 的正确
     * 舍入结果等值。故等值变换＝非整数词形 Double.parseDouble 后
     * Double.toString（如 1.50→"1.5"、1e+21→"1.0E21"、1e-7→"1.0E-7"），
     * 整数词形逐字不变。 */
    private static String fastJsonScalarText(String raw) {
        if (raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0) return raw;
        return Double.toString(Double.parseDouble(raw));
    }

    /** 官 parseString 对对象/数组 token 的产出：parse() 先把值读成
     * JSONObject/JSONArray 再 Object.toString()。JSONObject 无参构造是
     * HashMap(16) 承载（JSONObject.smali <init>(IZ)：ordered=false 支
     * new HashMap(16)），toString 经 MapSerializer 按 entrySet 哈希序出键；
     * 嵌套值同出 parse()：串→String、整数→Integer/Long/BigInteger 量级
     * （序列化同为十进制原文）、浮点→Double、布尔→Boolean、null→null、
     * 对象/数组递归。故此处把 JsonElement 递归转 HashMap/ArrayList 承载
     * 后再按官序列化写出，不再用 JsonElement.toString() 保序保词形。 */
    private static String fastJsonText(com.google.gson.JsonElement el) {
        StringBuilder sb = new StringBuilder();
        writeFastJsonValue(toFastJsonValue(el), sb);
        return sb.toString();
    }

    private static Object toFastJsonValue(com.google.gson.JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonObject()) {
            Map<String, Object> map = new HashMap<>(16);
            for (Map.Entry<String, com.google.gson.JsonElement> e : el.getAsJsonObject().entrySet()) {
                map.put(e.getKey(), toFastJsonValue(e.getValue()));
            }
            return map;
        }
        if (el.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (com.google.gson.JsonElement c : el.getAsJsonArray()) list.add(toFastJsonValue(c));
            return list;
        }
        com.google.gson.JsonPrimitive p = el.getAsJsonPrimitive();
        if (p.isString()) return p.getAsString();
        if (p.isBoolean()) return p.getAsBoolean();
        String raw = p.getAsString();
        if (raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
            return Double.parseDouble(raw);
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return new java.math.BigInteger(raw);
        }
    }

    /** 官 JSONObject/JSONArray 的 toString 写出：键值间无空格；Double 经
     * NumberCodec.write（NumberCodec.smali .line 83–99）＝Double.toString
     * 后剥尾 ".0"（如嵌套 1e5→"100000"、2.0→"2"，与顶层标量支的不剥尾
     * 不同）；Double 的非有限支同出 NumberCodec：isNaN 先判写 null
     * （.line 85–86）、isInfinite 次判写 null（.line 87–88），故嵌套
     * 1e400→null、NaN→null（顶层标量支不经 NumberCodec、仍出
     * "Infinity"，两支不对称是官形）；串的转义照
     * SerializeWriter.writeStringWithDoubleQuote 的
     * specicalFlags_doubleQuotes/replaceChars 表（见 writeFastJsonString）。 */
    private static void writeFastJsonValue(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            writeFastJsonString((String) v, sb);
        } else if (v instanceof Double) {
            double d = (Double) v;
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else {
                String s = Double.toString(d);
                if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
                sb.append(s);
            }
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v.toString());
        } else if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeFastJsonString(String.valueOf(e.getKey()), sb);
                sb.append(':');
                writeFastJsonValue(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object c : (List<?>) v) {
                if (!first) sb.append(',');
                first = false;
                writeFastJsonValue(c, sb);
            }
            sb.append(']');
        }
    }

    /** 官 SerializeWriter 双引号串转义（官包 SerializeWriter.smali 的
     * writeStringWithDoubleQuote＋<clinit> 两表）：判据次序以缓冲路径
     * （JSONObject.toString() 走 JSON.toJSONString、SerializeWriter 以
     * Writer=null 构造、恒走缓冲路）为准——先判 specicalFlags==4 写
     * unicode 转义（四位 hex）、次才落 replaceChars。specicalFlags_doubleQuotes 中
     * flag=1 的只有 \b、\t、\n、\f、\r、\"、\\，走 replaceChars 短转义；
     * flag=4 的是 0x00–0x07、0x0B、0x0E–0x1F、0x7F–0x9F——0x0B 虽在
     * replaceChars 里有 'v'，次序在后轮不到，官形是 \u000B、不是 \v
     * （全表逐字复核：同类被次序坑的只有 0x0B 一处，flag=1 七字与
     * flag=4 集合不重叠）；四位 hex 用 DIGITS（<clinit> :array_5＝
     * '0'–'9','A'–'F' 大写）；U+2028 走 \u2028；
     * '/' 默认不转义（WriteSlashAsSpecial 未开），其余字符（含 0xA0 起）
     * 原样直出。 */
    private static void writeFastJsonString(String s, StringBuilder sb) {
        final char[] DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7',
                '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\t': sb.append("\\t"); break;
                case '\n': sb.append("\\n"); break;
                case '\f': sb.append("\\f"); break;
                case '\r': sb.append("\\r"); break;
                case '\u2028': sb.append("\\u2028"); break;
                default:
                    if (c < 0x20 || (c >= 0x7F && c < 0xA0)) {
                        sb.append("\\u");
                        sb.append(DIGITS[(c >> 12) & 0xF]);
                        sb.append(DIGITS[(c >> 8) & 0xF]);
                        sb.append(DIGITS[(c >> 4) & 0xF]);
                        sb.append(DIGITS[c & 0xF]);
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private String handlePageRequest(String url) {
        return handlePageRequest(url, null);
    }

    /** explicitRule 非空时为取页基底（环24-11 簇一显式 ruleKey 通道，优先于
     * ambient）；为空走 ambient/宿主既有路，但 ambient 的 MY_RULE 在场为
     * null 时维持 null 基底（簇二官形，不回落宿主规则）。 */
    private String handlePageRequest(String url, HkRule explicitRule) {
        try {
            // G17：照官 PageParser.parsePageRule（官包 PageParser.smali 私有三参版
            // :544-816；fetchByHiker 走公有两参版、prefix=null 不回填 rule）逐序落：
            // params＝HttpParser.getParamsByUrl（宿主既有移植 HkHttpParser 同名法）；
            // path＝url.split(";")[0] → splitUrlByQuestionMark 取 [0] → replaceOnce
            // 去 "hiker://page/" 前缀 → UrlDetector.clearTag（HkParserKit 既有移植）。
            Map<String, String> params = HkHttpParser.getParamsByUrl(url);
            String path = HkParserKit.clearTag(HkParserKit.replaceOnce(
                    HkParserKit.splitUrlByQuestionMark(url.split(";")[0])[0], "hiker://page/", ""));
            // hikerPop.js：星辰原生重实现（规则自带的依赖 8.83 内部类，跑不起来）。
            // 返回合成页面，rule 里 $.exports.updateRecordsBottom 调原生 __hkShowUpdateLog 弹底部弹窗。
            if ("hikerPop.js".equals(path)) {
                // 环10：合成页同走官窄键集（见下方注），不另带 params。
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("path", path);
                out.put("rule", "js:$.exports.updateRecordsBottom = function(data){ __hkShowUpdateLog(JSON.stringify(data)); };");
                if (rule != null && rule.getTitle() != null) out.put("parent", rule.getTitle());
                return GSON.toJson(out);
            }
            // 取页规则：缺省＝JS 侧 MY_RULE（官 ruleKey 通道，G1）；读不到才用宿主当前规则。
            // ?rule= 在场时照官换规则：值为空→仍用基底、parent 取基底标题；值非空→
            // 按标题查库，查不到照官 ParseException 形回 ""（G17：摘掉旧形「查不到
            // 回退基底规则」——官无此回退，回退会把基底的同名页误当目标页交付）。
            HkRule baseRule = explicitRule != null ? explicitRule : myRuleFromScope();
            if (baseRule == null && !ambientRuleIsNull()) baseRule = rule;
            HkRule targetRule = baseRule;
            String parentTitle = baseRule != null ? baseRule.getTitle() : null;
            if (params.containsKey("rule")) {
                String ruleTitle = params.get("rule");
                if (ruleTitle != null && !ruleTitle.isEmpty()) {
                    HkRule found = HkRuleManager.get().getInstalledRule(ruleTitle);
                    if (found == null) {
                        Logger.t(TAG).d("handlePageRequest: rule title not found: %s", ruleTitle);
                        return "";
                    }
                    targetRule = found;
                    parentTitle = found.getTitle();
                }
            }
            if (targetRule == null) return "";
            String pagesJson = targetRule.getPages();
            if (!TextUtils.isEmpty(pagesJson)) {
                List<Map<String, Object>> pages = GSON.fromJson(pagesJson, MAP_LIST_TYPE);
                if (pages != null) {
                    for (Map<String, Object> p : pages) {
                        if (path.equals(String.valueOf(p.get("path")))) {
                            // 环10 对官（8.83 fetchByHiker→JSON.toJSONString(pageRule)）：
                            // ArticleListPageRule 可序列化键只有 {col_type,name,parent,
                            // path,rule}——params 与 parentRule 两字段带
                            // @JSONField(serialize=false)、fastjson 默认略 null。
                            // 旧形以 pages 原始 Map 为底透出作者写的任何额外键、再嵌
                            // params 与 parentRule 全量（整数经 Map 回读成 Double 数型
                            // 漂移），键集比官宽；改官窄键集：五键非空才出、全串值。
                            Map<String, Object> out = new LinkedHashMap<>();
                            Object colType = p.get("col_type");
                            if (colType != null) out.put("col_type", String.valueOf(colType));
                            Object name = p.get("name");
                            if (name != null) out.put("name", String.valueOf(name));
                            if (parentTitle != null) out.put("parent", parentTitle);
                            out.put("path", path);
                            Object ruleVal = p.get("rule");
                            if (ruleVal != null) out.put("rule", String.valueOf(ruleVal));
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

    /**
     * $.require('hiker://page/xxx') 的页面代码查找：从规则 pages（JSON 数组）按 path 取 rule 字段。
     */
    private String findPageCode(String path) {
        return findPageCode(path, null);
    }

    /** explicitRule 非空时只认其 pages（环24-11 簇一显式优先）；为空走
     * G1 同通道（ambient 的 MY_RULE 为在场 null 时恒回 null，簇二官形）。 */
    private String findPageCode(String path, HkRule explicitRule) {
        try {
            String pagesJson;
            if (explicitRule != null) {
                pagesJson = explicitRule.getPages();
            } else if (ambientRuleIsNull()) {
                return null;
            } else {
                // G1 同通道：优先 JS 侧 MY_RULE（子规则）的 pages，读不到再用宿主规则
                HkRule baseRule = myRuleFromScope();
                pagesJson = baseRule != null && !TextUtils.isEmpty(baseRule.getPages())
                        ? baseRule.getPages() : rule.getPages();
            }
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

    /**
     * 条目修改面（updateItem/deleteItem/addItem/_findItem 等）的工作表：
     * find 求值期是本发汇点的合成表（已交付条目），其余语境（详情/lazy/展示后）
     * 仍是 results——与官形「改已经交付给页面的那张表」同义（G51）。
     */
    private List<HkItem> currentItems() {
        HkFindDelivery.Sink s = activeFindSink.get();
        return s != null ? s.items() : results;
    }

    /** 在当前工作表里按 extra.id 找条目下标（官 ItemModifyEvent 的锚定口径，G28）。
     * 环30：旧 url 锚 findResultIndex 随 deleteItem/addItem 改 extra.id 后无消费点，整支删除。 */
    private int findItemIndexByExtraId(String anchor) {
        List<HkItem> items = currentItems();
        for (int i = 0; i < items.size(); i++) {
            if (anchor.equals(items.get(i).getExtra("id"))) return i;
        }
        return -1;
    }

    /** 条目 Map 的 extra 子表（Map 原样/JSON 串解析）；无 extra 或不可解析回 null。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extraMapOf(Map<String, Object> m) {
        Object ex = m == null ? null : m.get("extra");
        if (ex == null) return null;
        if (ex instanceof Map) return (Map<String, Object>) ex;
        return toStrMap(ex);
    }

    /** 条目 Map 的 extra.id（官 ArticleList.getBaseExtra().getId() 口径），缺省 ""。 */
    private String extraIdOf(Map<String, Object> m) {
        Map<String, Object> exMap = extraMapOf(m);
        return exMap == null ? "" : str(exMap, "id");
    }

    /** raw 通道条目的 extra.id：extra JSON 串里的 id 优先，其次顶层 id 键（合成 extra 前的原形）。 */
    private String rawExtraId(Map<String, String> raw) {
        String ex = raw.get("extra");
        if (ex != null && !ex.isEmpty()) {
            Map<String, Object> exMap = toStrMap(ex);
            String id = exMap == null ? "" : str(exMap, "id");
            if (!TextUtils.isEmpty(id)) return id;
        }
        String top = raw.get("id");
        return top == null ? "" : top;
    }

    /** 照官 hasCls（ArticleListFragment 源行 5011）：任一为空回 false；两边包空格整词包含。 */
    private static boolean hasCls(String itemCls, String cls) {
        if (itemCls == null || itemCls.isEmpty() || cls == null || cls.isEmpty()) return false;
        return (" " + itemCls + " ").contains(" " + cls + " ");
    }

    /**
     * 环30 照官 convertToArticleList（JSEngine:1879）逐键组装新条目：
     * title/pic/desc/url 用 getString 口径（缺键/JSON null 留 null，不归一空串——
     * 消费方 lambda60 靠 null 判「不覆盖」）；pic 按键存在序 img ＞ pic ＞ pic_url；
     * content 仅 containsKey 时才 set；extra 仅 containsKey 时三态（String 原样 /
     * null 置空 / 其余 JSON 序列化），不读任何顶层键（旧 mergeItem 的 pic 逆序、
     * isEmpty 判空、extra 不并三差与顶层键合成 extra 一并摘除，D30-4/D30-6）。
     * col_type 非空才 setType。findRule/group/pages/preRule/rule 为咱家 HkItem
     * 扩展字段（官 ArticleList 无此面，D30-4④ 记名），键存在且非 null 时照旧带。
     */
    private HkItem convertItem(Map<String, Object> m) {
        HkItem item = new HkItem();
        if (m == null) return item;
        if (m.containsKey("title") && m.get("title") != null) item.setTitle(str(m, "title"));
        if (m.containsKey("url") && m.get("url") != null) item.setUrl(str(m, "url"));
        String picKey = m.containsKey("img") ? "img" : m.containsKey("pic") ? "pic" : m.containsKey("pic_url") ? "pic_url" : null;
        if (picKey != null && m.get(picKey) != null) item.setPic(str(m, picKey));
        if (m.containsKey("desc") && m.get("desc") != null) item.setDesc(str(m, "desc"));
        if (m.containsKey("content") && m.get("content") != null) item.setContent(str(m, "content"));
        String colType = str(m, "col_type");
        if (!TextUtils.isEmpty(colType)) item.setColType(colType);
        if (m.containsKey("extra")) {
            Object ex = m.get("extra");
            if (ex instanceof String) item.setExtra((String) ex);
            else if (ex != null) item.setExtra(GSON.toJson(ex));
        }
        if (m.containsKey("findRule") && m.get("findRule") != null) item.setFindRule(str(m, "findRule"));
        else if (m.containsKey("find_rule") && m.get("find_rule") != null) item.setFindRule(str(m, "find_rule"));
        if (m.containsKey("group") && m.get("group") != null) item.setGroup(str(m, "group"));
        if (m.containsKey("pages") && m.get("pages") != null) item.setPages(str(m, "pages"));
        if (m.containsKey("preRule") && m.get("preRule") != null) item.setPreRule(str(m, "preRule"));
        else if (m.containsKey("pre_rule") && m.get("pre_rule") != null) item.setPreRule(str(m, "pre_rule"));
        if (m.containsKey("rule") && m.get("rule") != null) item.setRule(str(m, "rule"));
        if (m.containsKey("line") && m.get("line") != null) item.setLine(str(m, "line"));
        return item;
    }

    /**
     * 环30 照官 lambda60 的 UPDATE 合并语义：源 Map 里键存在且值非 null 才覆盖
     * （空串是有效覆盖值），字段集与 convertItem 同（title/url/pic/desc/content/
     * col_type/extra＋扩展字段）。工作表条目与页面条目共用此口径。
     */
    private void applyUpdateFields(HkItem item, Map<String, Object> m) {
        if (item == null || m == null) return;
        if (m.containsKey("title") && m.get("title") != null) item.setTitle(str(m, "title"));
        if (m.containsKey("url") && m.get("url") != null) item.setUrl(str(m, "url"));
        String picKey = m.containsKey("img") ? "img" : m.containsKey("pic") ? "pic" : m.containsKey("pic_url") ? "pic_url" : null;
        if (picKey != null && m.get(picKey) != null) item.setPic(str(m, picKey));
        if (m.containsKey("desc") && m.get("desc") != null) item.setDesc(str(m, "desc"));
        if (m.containsKey("content") && m.get("content") != null) item.setContent(str(m, "content"));
        if (m.containsKey("col_type") && m.get("col_type") != null) item.setColType(str(m, "col_type"));
        if (m.containsKey("extra") && m.get("extra") != null) {
            Object ex = m.get("extra");
            if (ex instanceof String) item.setExtra((String) ex);
            else item.setExtra(GSON.toJson(ex));
        }
        if (m.containsKey("findRule") && m.get("findRule") != null) item.setFindRule(str(m, "findRule"));
        else if (m.containsKey("find_rule") && m.get("find_rule") != null) item.setFindRule(str(m, "find_rule"));
        if (m.containsKey("group") && m.get("group") != null) item.setGroup(str(m, "group"));
        if (m.containsKey("pages") && m.get("pages") != null) item.setPages(str(m, "pages"));
        if (m.containsKey("preRule") && m.get("preRule") != null) item.setPreRule(str(m, "preRule"));
        else if (m.containsKey("pre_rule") && m.get("pre_rule") != null) item.setPreRule(str(m, "pre_rule"));
        if (m.containsKey("rule") && m.get("rule") != null) item.setRule(str(m, "rule"));
        if (m.containsKey("line") && m.get("line") != null) item.setLine(str(m, "line"));
    }

    /**
     * 8.83 JSEngine item 系列 API（updateItem/deleteItem/deleteItemByCls）经 EventBus
     * post ItemModifyEvent，内容页收到后就地更新已展示列表（对齐 8.83 FilmListActivity/
     * ArticleListFragment.onItemModify）。parse 时直接改 results 已够；展示后的规则
     * 回调（如点击动作里调 updateItem）需经此链同步 UI。
     */
    private void postItemModifyEvent(HkItem item,
            com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action action, String cls) {
        postItemModifyEvent(item, action, cls, null);
    }

    /** 同上，带锚 id 版（官 ItemModifyEvent.setAnchorId，updateItem 用，G28）。 */
    private void postItemModifyEvent(HkItem item,
            com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action action, String cls,
            String anchorId) {
        postItemModifyEvent(item, action, cls, anchorId, null);
    }

    /** 带源 Map 版（环30）：UPDATE 消费方按源 Map 逐键判「非 null 才覆盖」（lambda60）。 */
    private void postItemModifyEvent(HkItem item,
            com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action action, String cls,
            String anchorId, Map<String, Object> sourceMap) {
        try {
            com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent e =
                    new com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent(item, action);
            if (cls != null) e.setCls(cls);
            if (anchorId != null) e.setAnchorId(anchorId);
            if (sourceMap != null) e.setSourceMap(sourceMap);
            org.greenrobot.eventbus.EventBus.getDefault().post(e);
        } catch (Throwable t) {
            Logger.t(TAG).d("postItemModifyEvent failed: %s", t.getMessage());
        }
    }

    /** 批量版（环30）：addItem 数组形与 deleteItem 数组形共用——官 setArticleLists＋setAfter。 */
    private void postItemModifyListEvent(List<HkItem> items,
            com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action action,
            String anchorId, boolean after) {
        try {
            List<Object> list = new ArrayList<>(items);
            com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent e =
                    new com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent(list, action);
            if (anchorId != null) e.setAnchorId(anchorId);
            e.setAfter(after);
            org.greenrobot.eventbus.EventBus.getDefault().post(e);
        } catch (Throwable t) {
            Logger.t(TAG).d("postItemModifyEvent failed: %s", t.getMessage());
        }
    }

    /** ADD 单条版（环30）：官 addItem0 的 setAnchorId＋setAfter。 */
    private void postItemModifyAddEvent(HkItem item, String anchorId, boolean after) {
        try {
            com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent e =
                    new com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent(item,
                            com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action.ADD);
            if (anchorId != null) e.setAnchorId(anchorId);
            e.setAfter(after);
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
        // 环30 照官 addItem0（JE:391）：条目经 convert 全字段（含 extra）；数组
        // 含 null 元素跳过、空表直接 return 不发事件；锚按 extra.id（旧形按 url、
        // 新条目不带 extra、url 空自填、锚未命中追加末尾、数组逐条逆序五差，
        // D30-3，一并摘除）；引擎恒 post 一次 ADD（单条 articleList／数组 list，
        // setAnchorId＋setAfter），展示表由页面消费方定夺。
        List<HkItem> items = new ArrayList<>();
        Object o = args[1];
        boolean arrayForm = o instanceof NativeArray;
        if (arrayForm) {
            NativeArray na = (NativeArray) o;
            for (int i = 0; i < na.size(); i++) {
                Object el = na.get(i);
                if (el == null) continue;
                Map<String, Object> m = toStrMap(el);
                if (m == null) throw new RuntimeException("addItem：格式有误，只支持json");
                items.add(convertItem(m));
            }
        } else {
            Map<String, Object> m = toStrMap(o);
            if (m == null) throw new RuntimeException("addItem：格式有误，只支持json");
            items.add(convertItem(m));
        }
        if (items.isEmpty()) return;
        if (arrayForm) {
            postItemModifyListEvent(items,
                    com.fongmi.android.tv.api.hk.event.rule.ItemModifyEvent.Action.ADD, id, after);
        } else {
            postItemModifyAddEvent(items.get(0), id, after);
        }
        // 工作表同步：锚命中才整段保序插入，未命中不插（官静默无-op）。
        int idx = findItemIndexByExtraId(id);
        if (idx >= 0) {
            currentItems().addAll(after ? idx + 1 : idx, items);
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

    // 环22：memCached 已随 requireCacheEval 收编 require0 一并删除——其唯一
    // 调用者就是旧 rc 自造路；memCache 表本体保留（deleteCache 的清表语义在用）。

    /** 8.83 fetchCache/requireCache 的小时参口径（JE fetchCache .line 3557-3562、
     * requireCache .line 3524-3529）：h 经 argsNativeObjectAdjust 后，Integer 取 intValue、
     * Double 取 intValue，其余（数字串/普通串/缺参/对象）一律回落 24。
     * 不走 toDouble——官对数字串不解析：fc(url,"5") 官 24、咱旧形 5（阶段五 fc 细差）。 */
    private static int cacheHoursValue(Object h) {
        Object a = argsNativeObjectAdjust(h);
        if (a instanceof Integer) return ((Integer) a).intValue();
        if (a instanceof Double) return ((Double) a).intValue();
        return 24;
    }

    private static double toDouble(Object o) {
        try {
            if (o instanceof Number) return ((Number) o).doubleValue();
            return Double.parseDouble(String.valueOf(o));
        } catch (Throwable e) {
            return 0;
        }
    }

    /**
     * fetchPC/postPC 的 UA 补入（8.83 JSEngine.generateRequestOptions 桌面支，
     * JE:2880 亲读）：头表先取 map["headers"]，为 null 才回落 map["header"] 单数键，
     * 两者皆无才新建并放回 "headers"；UA 在否判据＝containsKey("User-Agent") 精确键
     * （大小写敏感——小写 "user-agent" 在场官仍补官方 UA、双 UA 并存）；补入写进
     * 最终生效的那份头表（回落支写回 "header"那份，不新建 "headers" 堵死 fetchSync
     * 的 Req 单数映射）。旧形只看复数键＋equalsIgnoreCase 宽判，环 24-2（硬门审计 B）
     * 照官改。mergeMobileUa 旧份同形但无调用方（grep 仅定义），已随本批删。
     */
    private static String mergeDesktopUa(String optionsJson) {
        try {
            Map<String, Object> map = GSON.fromJson(
                    TextUtils.isEmpty(optionsJson) ? "{}" : optionsJson,
                    new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
            if (map == null) map = new HashMap<>();
            Object headers = map.get("headers");
            if (headers == null) headers = map.get("header");
            Map<String, Object> hm;
            if (headers instanceof Map) {
                hm = (Map<String, Object>) headers;
            } else {
                hm = new HashMap<>();
                map.put("headers", hm);
            }
            if (!hm.containsKey("User-Agent")) {
                hm.put("User-Agent", UA_PC);
            }
            return GSON.toJson(map);
        } catch (Throwable e) {
            return "{\"headers\":{\"User-Agent\":\"" + UA_PC + "\"}}";
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

    /**
     * 照官 8.83 JSEngine.getRuleTitle（JE:5274）：入参空→原样回（null/""），
     * 非空→查 ticketMap（票据→标题，本类 generateTicket 同表，与 fcRuleTitle
     * 同逻辑收成一口），命中且非空回标题，未命中/异常回入参本身。
     * 官此函数无「空→当前标题」补位支：旧形在此补位，使主 scope 经
     * allFunctions 包装注入的 MY_TICKET 票据恒不还原——JS 侧 var 键落
     * 「票据@」空间、setItem/getItem 按票据查规则恒 miss（硬门账 D 环37 §4
     * 定谳）。凡调用点语义上要当前规则标题的，改走 {@link #currentRuleTitle()}
     * 或 {@link #resolveRuleTitle(String)} 显式补位，不许在此留补位支。
     */
    private String hkRuleTitle(String rt) {
        if (rt == null || rt.isEmpty()) return rt;
        try {
            String resolved = ticketMap.get(rt);
            return resolved != null && !resolved.isEmpty() ? resolved : rt;
        } catch (Throwable ignored) {
            return rt;
        }
    }

    /** 当前规则标题（显式补位口：旧 hkRuleTitle(null) 的语义，只许调用点明用）。 */
    private String currentRuleTitle() {
        try {
            return rule != null && rule.getTitle() != null ? rule.getTitle() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * 隐参解析口（var/item 家族调用点专用）：隐参在官桥上恒由 allFunctions
     * 包装注入当前规则票据，缺位只发生在咱家直名路（非主 scope）——该路的
     * 等价票据即当前规则，故缺位/空显式补当前标题；非空走 hkRuleTitle 还原
     * （票据→标题、明文标题未命中原样回）。
     */
    private String resolveRuleTitle(String rt) {
        if (rt == null || rt.isEmpty()) return currentRuleTitle();
        return hkRuleTitle(rt);
    }

    /** 8.83 getIntValue（JE:4624）＋@Parameter defaultInt 补缺：参数缺位
     * 或为 null（JS undefined 经桥归一）时取缺省；Number→intValue、
     * String→parseInt（坏串照官抛）、余→0。 */
    private static int hkIntValue(Object o, int defaultInt) {
        if (o == null) return defaultInt;
        Object a = argsNativeObjectAdjust(o);
        if (a instanceof Number) return ((Number) a).intValue();
        if (a instanceof String) return Integer.parseInt((String) a);
        return 0;
    }

    /** 8.83 JSEngine.getFilesDir()（JE:3563）：UriUtils.getRootDir
     * ＋"/rules/files/"（尾分隔符）；咱家 rootDir＝hkRootDir()
     * （Documents 根，与官 getRootDir 同位）。旧形换成内部 data
     * 根、与官封锁的拼写族对不上、守卫成死码（第七阶段复核
     * 不过项），照官收回。 */
    private static String hkFilesDirPrefix() {
        return hkRootDir() + File.separator + "rules" + File.separator + "files" + File.separator;
    }

    /** 8.83 JSEngine.inJsDir（JE:5344）：path.contains(getJsDir())
     * 且 FileUtil.getName(文件名) 含 "." 或以 "global" 开头；
     * getJsDir＝rootDir/rules/js/（JE:4804，尾分隔符），咱家根
     * 同 hkRootDir()。旧形以 "/libs/" 段判区锚错根（真库在
     * data/<标题>/libs/、与封锁无关），照官收回。 */
    private static boolean hkInJsDir(String path) {
        if (path == null) return false;
        String jsPrefix = hkRootDir() + File.separator + "rules" + File.separator + "js" + File.separator;
        if (!path.contains(jsPrefix)) return false;
        String name = com.example.hikerview.utils.FileUtil.getName(new File(path).getName());
        return name.contains(".") || name.startsWith("global");
    }

    /** 8.83 FileUtil.fileToString（FileUtil.smali:1412）：逐行以 "\n" 拼、
     * 无尾随换行；IOException 回已积累串。 */
    private static String hkRawFileToString(String path) {
        StringBuilder sb = new StringBuilder();
        java.io.BufferedReader r = null;
        try {
            r = new java.io.BufferedReader(new java.io.FileReader(path));
            String line;
            boolean first = true;
            while ((line = r.readLine()) != null) {
                if (!first) sb.append('\n');
                sb.append(line);
                first = false;
            }
        } catch (java.io.IOException ignored) {
        } finally {
            if (r != null) {
                try { r.close(); } catch (java.io.IOException ignored) {}
            }
        }
        return sb.toString();
    }

    /** 8.83 FileUtil.stringToFile（FileUtil.smali:3353）：父目录不存在则
     * mkdirs，FileWriter 写+flush，IOException 吞。 */
    /** 8.83 FileUtil.stringToFile 本体形（FileUtil.smali:3353）：
     * 父目录先 mkdirs、BufferedWriter 写后自吞 IOException；
     * throws 声明照官保留（官本体同为声明在、异常不外穿），
     * 使调用方 hkStringToFile 的 catch(IOException) 官形成立。 */
    private static void hkRawStringToFile(String content, String path) throws java.io.IOException {
        File target = new File(path);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        java.io.BufferedWriter w = null;
        try {
            w = new java.io.BufferedWriter(new java.io.FileWriter(target));
            w.write(content);
            w.flush();
        } catch (java.io.IOException ignored) {
        } finally {
            if (w != null) {
                try { w.close(); } catch (java.io.IOException ignored) {}
            }
        }
    }

    /** 8.83 EncodeUtilKt.getEncode（EncodeUtilKt.smali:124）：juniversalchardet
     * 探测，失败回 null（调用方归一 UTF-8，同官 getEncode 的 null→UTF-8）。 */
    private static String hkDetectCharset(String path) {
        org.mozilla.universalchardet.UniversalDetector detector;
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
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
        }
    }

    /** 8.83 FileUtil.getContentByCharsetDetect（FileUtil.smali:1813）：
     * UTF-8 走 fileToString；余按探测编码解码，编码不支持回 ""。 */
    private String hkCharsetDetectRead(String path) {
        // 8.83 JSEngine.getContentByCharsetDetect 包装（JE:3271）：
        // 路径以 .js/.json 结尾先直走 FileUtil.fileToString、
        // 不做字符集探测（续核新挖的同函数官形缺失——同仓转录
        // 段的旧实现保留着此支、主路独缺）。
        if (path.endsWith(".js") || path.endsWith(".json")) return hkRawFileToString(path);
        // 8.83 FileUtil.getContentByCharsetDetect（FileUtil.smali
        // :1811）：探测=UTF-8 走 fileToString；余读字节组串，
        // 只 catch UnsupportedEncodingException 回 ""（第七阶段
        // 复核点名：旧形 catch Throwable 放宽，与官分叉）。
        // 字节读照官 fileToBytes 形：IOException 收 null。
        String enc = hkDetectCharset(path);
        if (enc == null || "UTF-8".equalsIgnoreCase(enc)) return hkRawFileToString(path);
        byte[] bytes;
        try {
            bytes = java.nio.file.Files.readAllBytes(new File(path).toPath());
        } catch (java.io.IOException e) {
            bytes = null;
        }
        try {
            return new String(bytes, enc);
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }

    /** 8.83 fileToString/stringToFile 共用黑名单（JE:2395-2476、9742-9821）：
     * 后缀 .zip/.db/.apk/.mp4/.png/.jpg 或路径含 "/backup/"。 */
    private static boolean hkFileBlacklisted(String path) {
        if (path.endsWith(".zip") || path.endsWith(".db") || path.endsWith(".apk")
                || path.endsWith(".mp4") || path.endsWith(".png") || path.endsWith(".jpg")) return true;
        return path.contains(File.separator + "backup" + File.separator);
    }

    /** 8.83 JSEngine.fileToString(File, title, Z)（JE:2382）：黑名单回 ""；
     * title 非空时 Z 且存有 "i--" 键走 AesUtil 解密读、余走字符集探测读；
     * title 空时路径落私有区回 ""（防私有区）。 */
    private String hkFileToString(File file, String title, boolean z) {
        if (!file.exists()) return "";
        String path = file.getAbsolutePath();
        if (hkFileBlacklisted(path)) return "";
        if (title != null && !title.isEmpty()) {
            if (!z) return hkCharsetDetectRead(path);
            String key = getItemNow("i--", "", title);
            if (key != null && !key.isEmpty()) {
                // 环 16-3：直回 aesUtilCrypt 结果（解密失败＝官 AesUtil 回 null 即
                // 本函数回 null；旧形 catch 回 ""，JS 侧判不出失败）。
                return aesUtilCrypt(false, key, hkRawFileToString(path));
            }
            return hkCharsetDetectRead(path);
        }
        if (path.contains(hkFilesDirPrefix())) return "";
        return hkCharsetDetectRead(path);
    }

    /** 8.83 JSEngine.stringToFile(content, path, title, Z)（JE:9738）：
     * 黑名单静默不写；title 非空且 Z 时按 "i--" 键 AesUtil 加密落盘
     * （无键以当前毫秒串为缺省查键、相等即原本无键→setItemNow 建键；
     * 键查回 ""＝规则不存在→明文）；title 空时私有区/js 区静默不写。 */
    private void hkStringToFile(String content, String path, String title, boolean z) {
        try {
            if (hkFileBlacklisted(path)) return;
            if (title != null && !title.isEmpty()) {
                if (!z) {
                    hkRawStringToFile(content, path);
                    return;
                }
                String def = String.valueOf(System.currentTimeMillis());
                String key = getItemNow("i--", def, title);
                if (key != null && !key.isEmpty()) {
                    if (def.equals(key)) setItemNow("i--", def, title);
                    // 环 16-3：删 enc="" 善后——加密失败 aesUtilCrypt 回 null，照官
                    // 交给 hkRawStringToFile（其 BufferedWriter.write((String) null)
                    // 抛 NPE，本函数只 catch IOException 故 NPE 穿出，与官同形）。
                    String enc = aesUtilCrypt(true, key, content);
                    hkRawStringToFile(enc, path);
                } else {
                    hkRawStringToFile(content, path);
                }
                return;
            }
            if (path.contains(hkFilesDirPrefix())) return;
            if (hkInJsDir(path)) return;
            hkRawStringToFile(content, path);
        } catch (java.io.IOException ignored) {
            // 官 JE stringToFile 只 catch IOException（JE:9738 尾）；
            // 旧形 catch Throwable 把非 IO 异常一并吞掉与官分叉，
            // 照官收窄（第七阶段复核点名）。
        }
    }

    /** B#2：8.83 JSEngine.getItemNow(key, def, title)（JE:4695/4746）：
     * key/title 空回 def；按 title 的规则不存在回 ""；在：storage 空或
     * 无键回 def、有键回值。当前规则走本实例 kv（官当前规则恒在库）。 */
    private String getItemNow(String key, String def, String title) {
        if (key == null || key.isEmpty() || title == null || title.isEmpty()) return def;
        Map<String, String> m = kvMapForTitle(title);
        if (m == null) return "";
        if (m.isEmpty()) return def;
        String v = m.get(key);
        return v != null ? v : def;
    }

    /**
     * 环37 照官 setItemNow(Rule) 大小闸（JE:9480，源行 3116–3132）：写入前先量
     * 现有 storage 序列化字节数，&gt; 0x100000（1MB）抛 RuntimeException 官原文案；
     * 再量新 key/value 字节数，任一 &gt; 0x80000（512K）抛 RuntimeException 官原文案。
     * 咱家 storage 载体＝kv 表的 GSON 序列化串（与落盘字节同形）。JS 侧可 catch、
     * 宿主不吞（调用链经桥裹成 JS 异常，与官抛错同形）。
     */
    private static void checkSetItemSize(Map<String, String> storage, String key, String value) {
        String existing = storage == null || storage.isEmpty() ? "" : GSON.toJson(storage);
        if (existing.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 0x100000) {
            throw new RuntimeException("私有存储内容过大（1MB），无法继续使用setItem写入");
        }
        int keyBytes = key == null ? 0 : key.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int valueBytes = value == null ? 0 : value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (valueBytes > 0x80000 || keyBytes > 0x80000) {
            throw new RuntimeException("内容过大（512K），无法使用setItem写入");
        }
    }

    /** 环37：setItem 成功落盘后照官 post RuleModifiedEvent（载体＝被写规则，官序在 save 之后）。 */
    private void postRuleModifiedEvent(String title) {
        try {
            HkRule target = title != null && title.equals(currentRuleTitle()) && rule != null
                    ? rule : HkRuleManager.get().getInstalledRule(title);
            if (target == null) return;
            org.greenrobot.eventbus.EventBus.getDefault().post(
                    new com.fongmi.android.tv.api.hk.event.rule.RuleModifiedEvent(target));
        } catch (Throwable ignored) {
        }
    }

    /** B#2：8.83 JSEngine.setItemNow(key, value, title)（JE:9480/9601）：
     * key/title 空或规则不存在不写；在则写入其 storage（咱 kv）并落盘，
     * 写入前过大小闸、落盘后发 RuleModifiedEvent（环37 补齐，旧形无闸无事件）。 */
    private void setItemNow(String key, String value, String title) {
        if (key == null || key.isEmpty() || title == null || title.isEmpty()) return;
        if (title.equals(currentRuleTitle())) {
            checkSetItemSize(kv, key, value);
            kv.put(key, value);
            saveKv();
            postRuleModifiedEvent(title);
            return;
        }
        HkJsRuntime rt = runtimeForTitle(title);
        if (rt != null) {
            checkSetItemSize(rt.kv, key, value);
            rt.kv.put(key, value);
            rt.saveKv();
            postRuleModifiedEvent(title);
            return;
        }
        if (HkRuleManager.get().getInstalledRule(title) == null) return;
        File f = new File(HkRuleManager.get().getDataDir(title), "kv.json");
        Map<String, String> m = readKvFile(f);
        checkSetItemSize(m, key, value);
        m.put(key, value);
        writeKvFile(f, m);
        postRuleModifiedEvent(title);
    }

    /** G43 网页桥的 BigTextDO 桶面（BigTextDO.smali getItem:812 / listItems:1317 /
     * removeItem:1367 / setItem:1636）：桶＝一行、键为 "js@"+bucket、值是 JSON 对象串，
     * getItem(bucket, field) 取对象字段、setItem 写字段、listItems 出字段名集、
     * removeItem 删字段，与 JE 裸键 getItem/setItem 是两个命名空间（复核 M1 订正：
     * 此前直拿字段当裸键读写、与 JE 侧同表互相污染，账面说法亦已订正）。
     * 咱家载体＝桶名规则的 kv 表内 "js@"+bucket 一行（写入走 setItemNow 三路）；
     * 官 BigTextDO 可建自由桶，咱 kv 只认已装规则名作桶（与 setItemNow 的规则
     * 存在门一致，记名见第八阶段回执）。 */
    public String bridgeKvGet(String bucket, String field) {
        if (bucket == null || bucket.isEmpty() || field == null) return null;
        org.json.JSONObject obj = bridgeKvObject(bucket);
        if (obj == null || !obj.has(field)) return null;
        Object v = obj.opt(field);
        if (v == null || v == org.json.JSONObject.NULL) return null;
        return v instanceof String ? (String) v : String.valueOf(v);
    }

    /** BigTextDO.setItem(bucket, field, value)：字段并入桶 JSON 对象后整行回写。 */
    public void bridgeKvSet(String bucket, String field, String value) {
        if (bucket == null || bucket.isEmpty() || field == null || field.isEmpty()) return;
        org.json.JSONObject obj = bridgeKvObject(bucket);
        if (obj == null) obj = new org.json.JSONObject();
        try {
            obj.put(field, value == null ? org.json.JSONObject.NULL : value);
        } catch (Throwable ignored) {
            return;
        }
        setItemNow("js@" + bucket, obj.toString(), bucket);
    }

    /** BigTextDO.listItems(bucket)：桶 JSON 对象的字段名全集；缺桶回空表。 */
    public java.util.List<String> bridgeKvKeys(String bucket) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (bucket == null || bucket.isEmpty()) return out;
        org.json.JSONObject obj = bridgeKvObject(bucket);
        if (obj != null) {
            java.util.Iterator<String> it = obj.keys();
            while (it.hasNext()) out.add(it.next());
        }
        return out;
    }

    /** BigTextDO.removeItem(bucket, field)：自桶 JSON 对象删字段后整行回写。 */
    public void bridgeKvRemove(String bucket, String field) {
        if (bucket == null || bucket.isEmpty() || field == null || field.isEmpty()) return;
        org.json.JSONObject obj = bridgeKvObject(bucket);
        if (obj == null) return;
        obj.remove(field);
        setItemNow("js@" + bucket, obj.toString(), bucket);
    }

    /** 桶 JSON 对象读取：kv 表内 "js@"+bucket 一行，非 JSON/缺行回 null。 */
    private org.json.JSONObject bridgeKvObject(String bucket) {
        Map<String, String> m = kvMapForTitle(bucket);
        if (m == null) return null;
        String raw = m.get("js@" + bucket);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return new org.json.JSONObject(raw);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 按 title 取 storage 表：当前规则＝本实例 kv；他规则＝常驻运行时
     * 的 kv，无常驻则读其 kv.json（文件不存在＝空表）；规则不存在回 null。 */
    private Map<String, String> kvMapForTitle(String title) {
        if (title.equals(currentRuleTitle())) return kv;
        HkJsRuntime rt = runtimeForTitle(title);
        if (rt != null) return rt.kv;
        if (HkRuleManager.get().getInstalledRule(title) == null) return null;
        File f = new File(HkRuleManager.get().getDataDir(title), "kv.json");
        if (!f.exists()) return new HashMap<>();
        return readKvFile(f);
    }

    /** 按 title 取常驻运行时（不触发创建；规则不存在/异常回 null）。 */
    private HkJsRuntime runtimeForTitle(String title) {
        try {
            HkRule other = HkRuleManager.get().getInstalledRule(title);
            if (other == null) return null;
            return com.fongmi.android.tv.ui.hk.HkEngineManager.runtimeIfPresent(other);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Map<String, String> readKvFile(File f) {
        Map<String, String> m = new HashMap<>();
        try {
            if (f.exists()) {
                byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> map = GSON.fromJson(new String(bytes, "UTF-8"), type);
                if (map != null) m.putAll(map);
            }
        } catch (Throwable ignored) {
        }
        return m;
    }

    private static void writeKvFile(File f, Map<String, String> m) {
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            java.nio.file.Files.write(f.toPath(), GSON.toJson(m).getBytes("UTF-8"));
        } catch (Throwable ignored) {
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
    /**
     * G19：8.83 JSEngine.rsaEncrypt/rsaDecrypt（JE:23423/23148）——data/key
     * 经 argsNativeObjectAdjust 后非 String 回 ""；options 非空非 undefined
     * 时强转 Map（非 Map 照官抛 ClassCast），四键 config/type/long/block
     * 各自 try/catch 解析、坏值只记日志保缺省（config=null、type=1、
     * long=1、block=true；type/long 经 Double.intValue、block 经 Boolean）。
     * 分发：encrypt type=1 公钥/2 私钥、decrypt 反之，余值回 ""；
     * config 非空走带 transformation 的五参形、空走缺省 PKCS1Padding 形。
     * RSAEncrypt 本体失败回 null（官 RSAEncrypt 内部 catch 后回 null、
     * JE 原样上抛，桥层 JS 见 null），JE 外层 catch 只兜本体外异常回 ""。
     */
    private String rsaWithOptions(boolean encrypt, Object[] args) {
        Object dataO = args != null && args.length > 0 ? argsNativeObjectAdjust(args[0]) : null;
        Object keyO = args != null && args.length > 1 ? argsNativeObjectAdjust(args[1]) : null;
        if (!(dataO instanceof String) || !(keyO instanceof String)) return "";
        String data = (String) dataO;
        String key = (String) keyO;
        String config = null;
        int type = 1;
        int lng = 1;
        boolean block = true;
        Object optO = args != null && args.length > 2 ? args[2] : null;
        if (optO != null && !isUndefined(optO)) {
            Map<?, ?> opt = (Map<?, ?>) argsNativeObjectAdjust(optO);
            if (opt.containsKey("config")) {
                try {
                    config = (String) opt.get("config");
                } catch (Exception e) {
                    Logger.t(TAG).e(e, "rsa options config");
                }
            }
            if (opt.containsKey("type")) {
                try {
                    type = ((Double) opt.get("type")).intValue();
                } catch (Exception e) {
                    Logger.t(TAG).e(e, "rsa options type");
                }
            }
            if (opt.containsKey("long")) {
                try {
                    lng = ((Double) opt.get("long")).intValue();
                } catch (Exception e) {
                    Logger.t(TAG).e(e, "rsa options long");
                }
            }
            if (opt.containsKey("block")) {
                try {
                    block = (Boolean) opt.get("block");
                } catch (Exception e) {
                    Logger.t(TAG).e(e, "rsa options block");
                }
            }
        }
        try {
            if (encrypt) {
                if (type == 1) return rsaCrypt(true, true, data, key, config, lng, block);
                if (type == 2) return rsaCrypt(true, false, data, key, config, lng, block);
                return "";
            }
            if (type == 1) return rsaCrypt(false, false, data, key, config, lng, block);
            if (type == 2) return rsaCrypt(false, true, data, key, config, lng, block);
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * G19：8.83 RSAEncrypt 四法合体（encrypt/decryptByPublic/PrivateKey，
     * RSAEncrypt.smali:54/205/356/507）：键经 DataUtils.base64Decode
     * （NO_WRAP）＋X509（公）/PKCS8（私）；long==1 单发，余分段——加密块长
     * block 时 modulus/8-11、否 117（0x75）；解密块长 block 时 modulus/8、
     * 否 128（0x80）；加密出串 Base64 NO_WRAP，解密出串 UTF-8（官单发支
     * 用平台缺省字符集，Android 恒 UTF-8、等价）。异常照官回 null。
     */
    private static String rsaCrypt(boolean encrypt, boolean pub, String data, String keyB64,
                                   String config, int lng, boolean block) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(
                    config != null ? config : "RSA/ECB/PKCS1Padding");
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
            java.security.Key key;
            java.math.BigInteger modulus;
            if (pub) {
                java.security.PublicKey pk = kf.generatePublic(new java.security.spec.X509EncodedKeySpec(
                        Base64.decode(keyB64, Base64.NO_WRAP)));
                key = pk;
                modulus = ((java.security.interfaces.RSAPublicKey) pk).getModulus();
            } else {
                java.security.PrivateKey pk = kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(
                        Base64.decode(keyB64, Base64.NO_WRAP)));
                key = pk;
                modulus = ((java.security.interfaces.RSAPrivateKey) pk).getModulus();
            }
            cipher.init(encrypt ? javax.crypto.Cipher.ENCRYPT_MODE : javax.crypto.Cipher.DECRYPT_MODE, key);
            if (encrypt) {
                byte[] in = data.getBytes(Charset.forName("UTF-8"));
                if (lng == 1) return Base64.encodeToString(cipher.doFinal(in), Base64.NO_WRAP);
                int chunk = block ? modulus.bitLength() / 8 - 11 : 117;
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                for (int off = 0; off < in.length; ) {
                    int n = Math.min(chunk, in.length - off);
                    byte[] seg = cipher.doFinal(in, off, n);
                    out.write(seg, 0, seg.length);
                    off += n;
                }
                return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            }
            byte[] in = Base64.decode(data, Base64.NO_WRAP);
            if (lng == 1) return new String(cipher.doFinal(in), Charset.forName("UTF-8"));
            int chunk = block ? modulus.bitLength() / 8 : 128;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            for (int off = 0; off < in.length; ) {
                int n = Math.min(chunk, in.length - off);
                byte[] seg = cipher.doFinal(in, off, n);
                out.write(seg, 0, seg.length);
                off += n;
            }
            return new String(out.toByteArray(), Charset.forName("UTF-8"));
        } catch (Exception e) {
            return null;
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

    /**
     * 官方 ArticleColTypeEnum 的全部 col_type code（57 码全集＝枚举本体，本身不动）。
     * 排列＝官枚举序号序（G46 订正：旧表是家族分组序，与官 getCodeArray 的
     * 枚举序在 40 处不同位——码集虽同，JS 按下标取即差，故照官序号重排；
     * 序号据官包 ArticleColTypeEnum.&lt;clinit&gt; 逐条构造序号 0..56 提取）。
     */
    private static final List<String> COL_TYPES = java.util.Arrays.asList(
            "text_1", "text_2", "text_3", "text_4", "text_5", "text_center_1", "movie_1",
            "movie_2", "movie_3", "movie_1_left_pic", "movie_1_vertical_pic", "movie_1_vertical_pic_blur", "movie_3_marquee", "pic_1",
            "pic_2", "pic_3", "pic_1_full", "pic_1_center", "pic_3_square", "pic_1_card", "pic_2_card",
            "icon_1_search", "icon_2", "icon_2_round", "icon_3_round_fill", "icon_3_fill", "icon_small_3", "icon_4",
            "icon_4_card", "icon_small_4", "icon_round_4", "icon_round_small_4", "icon_5", "icon_5_no_crop", "long_text",
            "rich_text", "avatar", "text_icon", "x5_webview_single", "video", "header", "footer",
            "line", "line_blank", "blank_block", "big_blank_block", "flex_button", "scroll_button", "card_pic_2",
            "card_pic_3", "card_pic_3_center", "card_pic_1", "card_pic_2_2", "card_pic_2_2_left", "input", "big_big_blank_block",
            "icon_1_left_pic"
    );

    /**
     * getColTypes 的返回码集（G46，照官 ArticleColTypeEnum.getCodeArray 逐行）：
     * 遍历全集、跳过 HEADER/FOOTER 两码、余者按原序收集，共 55 码。
     */
    private static List<String> colTypeCodes() {
        List<String> out = new java.util.ArrayList<>();
        for (String code : COL_TYPES) {
            if ("header".equals(code) || "footer".equals(code)) continue;
            out.add(code);
        }
        return out;
    }

    /** DlanWebController.getColTypes 端点用（与桥内 getColTypes 同集，G44）。 */
    public static List<String> hkColTypeCodes() {
        return colTypeCodes();
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
     * require() 本地库优先：全局 libs/&lt;md5(url)&gt;.js 命中则直接加载
     * （.hkzip 导入时已按官落全局，无网络也能用）；未命中返回 null 走远程。
     * 环13 复核订正1：旧形读规则数据目录 data/&lt;规则名&gt;/libs/ 的 per-rule
     * 支已退役（存量经 HkRuleManager.get() 首建时一次性迁移到全局），与
     * fileExist/exist/fetchByHiker、$.require/require0 同读全局一根。
     */
    private String loadLibLocal(String libUrl) {
        try {
            if (libUrl == null || !libUrl.startsWith("http")) return null;
            String md5 = Util.md5(libUrl);
            if (TextUtils.isEmpty(md5)) return null;
            File f = new File(HkRuleManager.get().getGlobalLibsDir(), md5 + ".js");
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
     * 供 fetch()/request()/fetchForShim()/图片 scope __imageRequireAsset 调用
     * （官方 $.require 经 getScript→request() 取脚本，不是 __hkRequirePage）。
     * 白名单与读取直调正表 {@code HkHikerRuleUtil.getAssetsFileByHiker}
     * （8.83 HikerRuleUtil.getAssetsFileByHiker，smali 16-173）：仅六类
     * （home.js/beautify.js/crypto-java.js/wasm.js/translate/ 前缀/localmanager.txt，
     * equals/startsWith 均大小写敏感、前缀以 replace 全量剥离）可读，表外回 ""
     * 不抛错；本处不再自拼名单、不再剥 query/fragment、不再 trim——名带
     * query/空白即与官同形对不上白名单回 ""。找不到返回 ""（不抛异常）。
     */
    private String loadAssetText(String url) {
        try {
            return com.fongmi.android.tv.api.hk.util.HkHikerRuleUtil.getAssetsFileByHiker(url);
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
            // 毒文件根治（短剧冷播可行性 §四）：最终路径「存在即完整」。
            // 同目标的历史临时文件先清（60 分钟前的 .part-<唯一> 残留；在途的并发下载因年轻跳过；
            // P0 预热的固定名 X.part 不带 "-<唯一>" 后缀，不会被扫到）。
            sweepStalePartFiles(f);
            // 临时名带唯一后缀：同目标多线程并发下载互不踩；与最终文件同目录，原子改名要求同文件系统。
            tmp = new File(f.getParentFile(),
                    f.getName() + ".part-" + System.nanoTime() + "-" + Thread.currentThread().getId());
            String opt = "{\"headers\":{}}";
            Map<String, String> flat = null;
            try {
                Map<String, Object> m = GSON.fromJson(headersJson,
                        new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
                if (m != null) {
                    Object h = m.get("headers");
                    if (h == null) h = m.get("header");
                    Map<String, Object> hm = h instanceof Map ? (Map<String, Object>) h : m;
                    flat = new HashMap<>();
                    for (Map.Entry<String, Object> e : hm.entrySet()) {
                        if (e.getValue() != null) flat.put(e.getKey(), String.valueOf(e.getValue()));
                    }
                    Map<String, Object> wrap = new HashMap<>();
                    wrap.put("headers", flat);
                    opt = GSON.toJson(wrap);
                }
            } catch (Throwable ignored) {
            }
            // 断点续传起点（规则自带 Range 头时才有）：本方法自身从不拼 Range，
            // 416 只可能来自规则传的 Range 起点已达/越过远端总长（见下方处置）。
            final long rangeStart = rangeStartOf(flat);
            boolean retried416 = false;
            while (true) {
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
                        break;
                    }
                    if (res.code() == 416 && rangeStart >= 0 && !retried416) {
                        // HTTP 416＝续传起点已达/越过远端总长。本地最终文件若与远端
                        // 总长等长（Content-Range: bytes */总长），或总长未知而与规则
                        // 续传起点等长，就是已经下完了——按成功交回，不许报失败
                        // （2026-10-31 真机案：下完的文件再点下载回 416 被当失败抛错）。
                        // 对不上长＝本地是旧/别的文件，摘掉 Range 整包重下一回。
                        long total = contentRangeTotalOf(res.header("Content-Range"));
                        if (f.isFile() && ((total >= 0 && f.length() == total)
                                || (total < 0 && f.length() == rangeStart))) {
                            Logger.t(TAG).d("downloadToFile 416 already complete: %s",
                                    f.getAbsolutePath());
                            return;
                        }
                        retried416 = true;
                        opt = optWithoutRange(flat);
                        continue;
                    }
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

    /** 规则 headers 里的 Range 起点（bytes=N- / bytes=N-M）；无 Range 或不可解析回 -1。 */
    private static long rangeStartOf(Map<String, String> flat) {
        if (flat == null) return -1;
        for (Map.Entry<String, String> e : flat.entrySet()) {
            if (e.getKey() == null || !"Range".equalsIgnoreCase(e.getKey())) continue;
            String v = e.getValue();
            if (v == null) return -1;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?i)^\\s*bytes\\s*=\\s*(\\d+)\\s*-").matcher(v);
            if (!m.find()) return -1;
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ex) {
                return -1;
            }
        }
        return -1;
    }

    /** 416 回包 Content-Range 头（末段斜杠后的远端总长）；没带或不可解析回 -1。 */
    private static long contentRangeTotalOf(String contentRange) {
        if (contentRange == null) return -1;
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0) return -1;
        try {
            return Long.parseLong(contentRange.substring(slash + 1).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 摘掉 Range 头重拼 downloadToFile 的 opt（416 处置第二发：整包重下）。 */
    private static String optWithoutRange(Map<String, String> flat) {
        Map<String, String> kept = new HashMap<>();
        if (flat != null) {
            for (Map.Entry<String, String> e : flat.entrySet()) {
                if (e.getKey() != null && "Range".equalsIgnoreCase(e.getKey())) continue;
                kept.put(e.getKey(), e.getValue());
            }
        }
        Map<String, Object> wrap = new HashMap<>();
        wrap.put("headers", kept);
        return GSON.toJson(wrap);
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
     * 无页面身份：本发自成一个顶替作用域——不顶别人、也不被顶，排队跑完回真结果。
     */
    public List<HkItem> parseList(String jsCode, String myUrl, int page) throws Exception {
        return parseList(jsCode, myUrl, page, new Object());
    }

    /**
     * 带页面加载身份的入口：顶替只许发生在同一身份内（见 ListEvalMark.scope）。
     * 被同身份继任者顶掉时抛 {@link ListEvalSupersededException}（取消语义），
     * 不回空表冒充结果。
     */
    public List<HkItem> parseList(String jsCode, String myUrl, int page, Object listScope) throws Exception {
        // 先发代次、先顶掉在跑的同身份旧发再排队：它的结果必被同页 ticket 作废
        ListEvalMark mark = newListMark(listScope);
        return parseListInternal(jsCode, myUrl, page, mark);
    }

    /**
     * 缓存代码重建专用入口（HkEngineBridge.evalCacheCode）：跑的是 setResult(...)
     * 合成脚本、不是列表加载——不顶替在跑的列表求值，也不被顶替，与改前串行语义一致。
     */
    public List<HkItem> parseListUnmarked(HkRule rule, String jsCode, String myUrl, int page) throws Exception {
        synchronized (ruleEvalLock) {
            bindRule(rule);
            return parseListInternal(jsCode, myUrl, page, null);
        }
    }

    private List<HkItem> parseListInternal(String jsCode, String myUrl, int page, ListEvalMark mark) throws Exception {
        // 提交时捕获当前绑定，任务在引擎线程开头重申：防提交与执行之间
        // 别的页 bindRule 换掉 this.rule 造成串页（与带规则入口的锁互补）
        final HkRule boundRule = this.rule;
        java.util.concurrent.Future<List<HkItem>> future = submit(() -> {
            if (boundRule != null) this.rule = boundRule;
            if (mark != null) {
                // 排队期间已被同一页面身份的继任者接管：按取消语义收口——抛
                // ListEvalSupersededException 让调用方丢弃本发，绝不回空表冒充
                // 求值结果（伪造空表已废，账 hk-import-firstload-empty-dig §六）。
                // 跨身份的提交不在此列：各排各队、跑出自己的真结果，上不上屏
                // 由调用方票据定（官 8.83 形态：结果真假与上屏与否分离）。
                synchronized (latestListMarkByScope) {
                    if (latestListMarkByScope.get(mark.scope) != mark) {
                        throw new ListEvalSupersededException();
                    }
                }
                activeListMark = mark;
                rhinoCx.putThreadLocal(LIST_MARK_KEY, mark);
            }
            HkFindDelivery.Sink findSink = null;
            try {
                results.clear();
                error = null;
                // R6: 求值前清掉 refreshPage 标记，避免上次残留被误判为本次的新请求
                refreshRequested = false;
                refreshToTop = false;
                this.page = Math.max(1, page);
                // 环19 arm：本发换鲜 scope（官每发装配形，见 assembleFreshScope）——
                // setContext/CALLBACK_KEY/求值全部落本发 scope，上一发的 var/let、
                // $.exports、Module._cache 不再残留进本发。
                scope = assembleFreshScope();
                setContext(myUrl);
                try {
                    // 8.83 parseHome 同款：每次求值注入 fresh CALLBACK_KEY 并登记正文表
                    //（正文=预抓正文，HkEngine.home() 经 setEvalResCodeBody 传入）
                    String cbKey = armCallbackKey(evalResCodeBody);
                    // G2：同一 key 同挂主 scope 属性——预置段包装函数的
                    // callbackKey 覆写读的是定义作用域（主 scope）的 CALLBACK_KEY，
                    // 求值串里的 var 只落子 scope，包装层读不到；官每发新 scope
                    // 两者同体，主路常驻 scope 以属性补同体（getResCode 等既有
                    // scope.get 读法也自此真命中）。
                    ScriptableObject.putProperty(scope, "CALLBACK_KEY", cbKey);
                    // G51 官式交付：按本发 CALLBACK_KEY 登记汇点，setResult 一族交付进
                    // 汇点（PRE/AFTER phase 记账、正式交付即摘），求值结束按交付序合成
                    // 返回表——交付不再经共享 results 累加器（账 G51 三环口子）。
                    findSink = findDelivery.register(cbKey, "home");
                    if (cbKey != null) findRegisteredKeys.add(cbKey);
                    activeFindSink.set(findSink);
                    // 8.83 parseHome 的求值串以 getMyRule 前缀起头，前缀里的
                    // eval(getJsPlugin())/eval(getJsLazyPlugin()) 每发重评 Hikerurl.js/
                    // plugin.js——$ 与 $.require 的 Module 缓存随本发全新。此前本路只拼
                    // CALLBACK_KEY+正文，$ 恒为 init 装入共享 scope 的那一份、模块缓存
                    // 跨页存活：BestJav 这类「模块级 d 数组攒条目、setResult(Apollo.d)」
                    // 的规则，子页求值拿到的是缓存里的旧模块，d 里还躺着首页的条目，
                    // 子页一打开先摆一整个首页（点封面重开首页的字面成因）。补回重评，
                    // 本发内 $.require 重新装载页模块（d 归零），与本体一致；
                    // 跨求值的持久状态仍走 varMap/kv 等既有通道，不受影响。
                    // 环20 双评防护：plugin.js 只许评一遍——本发首个 evalJs 经
                    // evalJsRooted 的 ensurePluginIn 已把 plugin.js 评进本发 scope
                    // （改名支在那一遍以 _displayName 还原 MY_RULE.title、原别名
                    // 退到 _title）；脚本头若再 eval(getJsLazyPlugin()) 评第二遍，
                    // 改名支会把 _title 覆写成已还原的 title、别名丢失（官每发
                    // 只经 getMyRule 尾巴评一遍、无此问题）。故脚本头只重评
                    // Hikerurl.js（$ 与 Module 缓存随本发全新），不再重评 plugin.js。
                    evalJs("eval(getJsPlugin());\n"
                            + getMyCallbackKey(cbKey) + stripJsPrefix(jsCode));
                } catch (Throwable e) {
                    if (mark != null && mark.superseded) {
                        // 在跑途中被同身份继任者打断（观察者以 superseded 旗中止
                        // 求值）：取消语义，不记求值错误、不上抛原 Error——调用方
                        // 按 ListEvalSupersededException 丢弃本发。
                        throw new ListEvalSupersededException();
                    }
                    // 之前异常直接上抛到 HkEngine.home() 被吞掉只剩 debug 日志，
                    // UI 永远只显示"加载失败"。先把根因记下来，供 getError()/空态展示。
                    error = jsErrorMsg(e);
                    throw e;
                }
                synchronized (findSink) {
                    return new ArrayList<>(findSink.items());
                }
            } finally {
                if (findSink != null) {
                    findDelivery.unregister(findSink);
                    // 环23 复核订正3：分界键集随 unregister 一并清，防单调增长
                    // （键经汇点自带 key 取，cbKey 是 try 内局部、finally 不可见）。
                    String regKey = findSink.key();
                    if (regKey != null) findRegisteredKeys.remove(regKey);
                    activeFindSink.remove();
                }
                if (mark != null) {
                    rhinoCx.removeThreadLocal(LIST_MARK_KEY);
                    if (activeListMark == mark) activeListMark = null;
                    synchronized (latestListMarkByScope) {
                        if (latestListMarkByScope.get(mark.scope) == mark) {
                            latestListMarkByScope.remove(mark.scope);
                        }
                    }
                }
            }
        });
        try {
            // 列表求值（mark!=null）的等待对齐其观察者远界（见 LIST_EVAL_BUDGET_MS
            // 测算）：150 秒会在坏网正当慢求值中途把调用方截断报错、结果被丢弃；
            // 缓存重建（mark==null）维持原 150 秒串行语义不变。
            return future.get(mark != null ? LIST_EVAL_WAIT_SECONDS : EVAL_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException ee) {
            // 取消信号解包直达调用方，不许埋在 ExecutionException 里被当成求值失败
            if (ee.getCause() instanceof ListEvalSupersededException) {
                throw (ListEvalSupersededException) ee.getCause();
            }
            throw ee;
        }
    }

    /** 带当前页规则的入口（8.83 parseHome 形态：规则随求值带入）：先重绑再走原路。无页面身份版。 */
    public List<HkItem> parseList(HkRule rule, String jsCode, String myUrl, int page) throws Exception {
        return parseList(rule, jsCode, myUrl, page, new Object());
    }

    /**
     * 带当前页规则与页面加载身份的入口：顶替只许发生在同一身份内
     * （见 ListEvalMark.scope），被顶掉抛 {@link ListEvalSupersededException}。
     */
    public List<HkItem> parseList(HkRule rule, String jsCode, String myUrl, int page, Object listScope) throws Exception {
        // 先发代次、先顶替再抢锁：旧列表求值的调用方正持锁在 Future.get 上长等，
        // 先在锁外把在跑的同身份旧求值打断，锁才放得出来、当前求值才不必等它整段跑完。
        ListEvalMark mark = newListMark(listScope);
        synchronized (ruleEvalLock) {
            bindRule(rule);
            return parseListInternal(jsCode, myUrl, page, mark);
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
            // R6: 求值前清掉 refreshPage 标记，避免上次残留被误判
            refreshRequested = false;
            refreshToTop = false;
            this.page = Math.max(1, page);
            // 环19 arm：同 parseListInternal，本发换鲜 scope 后再 setContext。
            scope = assembleFreshScope();
            setContext(myUrl);
            ScriptableObject.putProperty(scope, "MY_KEYWORD", keyword == null ? "" : keyword);
            HkFindDelivery.Sink findSink = null;
            try {
                // 8.83 parseSearch 同款：每次求值注入 fresh CALLBACK_KEY 并登记正文表
                //（正文=搜索预抓正文，调用方经 setEvalResCodeBody 传入）
                String cbKey = armCallbackKey(evalResCodeBody);
                // G2：同 parseListInternal——CALLBACK_KEY 同挂主 scope 属性，
                // 供预置段包装函数的 callbackKey 覆写读取（官同体语义）。
                ScriptableObject.putProperty(scope, "CALLBACK_KEY", cbKey);
                // G51 官式交付：与 parseListInternal 同口径登记汇点、按交付序合成。
                findSink = findDelivery.register(cbKey, "search");
                if (cbKey != null) findRegisteredKeys.add(cbKey);
                activeFindSink.set(findSink);
                currentMyType = "search";
                // 8.83 parseSearchRes 同款：求值串以 getMyRule 前缀起头，前缀里的
                // eval(getJsPlugin())/eval(getJsLazyPlugin()) 每发重评 Hikerurl.js/
                // plugin.js——$ 与 $.require 的 Module 缓存随本发全新。本路此前只拼
                // CALLBACK_KEY+正文，$ 恒为 init 装入共享 scope 的那一份、模块缓存
                // 跨发存活：模块级数组攒条目的规则（如 BestJav 的 Apollo.d），第二发
                // 搜索拿到缓存旧模块、把第一发的结果一并 setResult 交出。补回重评
                // 与 parseListInternal 同口径；跨求值的持久状态仍走 varMap/
                // 存储封装/kv 等既有通道，不受影响。plugin.js 不在此重评
                // （环20 双评防护，理由见 parseListInternal 同处注）。
                evalJs("eval(getJsPlugin());\n"
                        + getMyCallbackKey(cbKey) + stripJsPrefix(jsCode));
            } catch (Throwable e) {
                error = jsErrorMsg(e);
                throw e;
            } finally {
                if (findSink != null) {
                    findDelivery.unregister(findSink);
                    // 环23 复核订正3：分界键集随 unregister 一并清，防单调增长
                    // （键经汇点自带 key 取，cbKey 是 try 内局部、finally 不可见）。
                    String regKey = findSink.key();
                    if (regKey != null) findRegisteredKeys.remove(regKey);
                    activeFindSink.remove();
                }
            }
            if (findSink == null) return new ArrayList<SearchResult>();
            synchronized (findSink) {
                return toSearchResults(new ArrayList<>(findSink.items()));
            }
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
                // 环19 arm：经提交的独立发换鲜 scope；已在 JS 线程的直调支是
                // 当发求值内的选择器回调，与当发同 scope（官同形），不换。
                scope = assembleFreshScope();
                ScriptableObject.putProperty(scope, "input", input == null ? "" : input);
                // 环20 A：CALLBACK_KEY 独立发补发（同 evalLazy 口径：只补
                // 串行与根属性，不登记回查两表；JS 线程直调支是当发内回调、
                // 读当发键、官同形，不在此发新键）。
                String cbKey = genUuid();
                ScriptableObject.putProperty(scope, "CALLBACK_KEY", cbKey);
                Object r = evalJs(getMyCallbackKey(cbKey) + js);
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
     * registerApiInto 的 apiRegisterLock 照常保护「注册目标切换」
     * （注册期串行、求值期并行）；注册发生在已 enter 的调用线程内，
     * currentContext 即本次的 cx。
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
        // MY_TICKET 同前置（与 assembleFreshScope 同根）：下方重评
        // Hikerurl.js 时其顶层 IIFE 即读此名，本 scope 无 getMyRule
        // 前缀串托底，无绑定即抛（此前被 catch 吞成 $ 残缺、只记日志）。
        String imgTicket = generateTicket(ruleTitle);
        ScriptableObject.putProperty(imageScope, "MY_TICKET", imgTicket == null ? "" : imgTicket);
        long t0 = android.os.SystemClock.elapsedRealtime();
        String code = "";
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
            // G24：图片 scope 每发新 scope（官 evalJS 同形），随发评
            // 入 plugin.js 使裸名 storage0 可达（官 getMyRule 尾巴
            // 恒带 eval(getJsLazyPlugin())）；plugin.js 首行读
            // MY_RULE，先装当前规则（序不可反）。MY_RULE 本体在
            // 本 scope 内以 JSON 字面量求值建成——图片单例的字段
            // scope 为 null，宿主 parseJson（读字段 scope）在此
            // 必 NPE、会连评入一起堵死（续核不过因），两步分开
            // try，一步失败不堵另一步。
            try {
                Object myRuleObj = cx.evaluateString(imageScope,
                        "(" + myRuleJson(rule) + ")", "myRule", 1, null);
                ScriptableObject.putProperty(imageScope, "MY_RULE", myRuleObj);
            } catch (Throwable t) {
                Logger.t(TAG).d("evalImageJs: set MY_RULE failed: %s", t.getMessage());
            }
            try {
                String lazyPlugin = loadAsset("plugin.js");
                if (!TextUtils.isEmpty(lazyPlugin)) {
                    cx.evaluateString(imageScope, lazyPlugin, "plugin.js", 1, null);
                }
            } catch (Throwable t) {
                Logger.t(TAG).d("evalImageJs: eval plugin.js failed: %s", t.getMessage());
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
            code = generateMyHeaders(headers) + getMyUrlJs(urlString) + getMyTitleJs(jsCode, ruleTitle) + ensureDollarShim() + wrapImageJs(jsCode);
            Object r = cx.evaluateString(imageScope, code, "imageJs", 1, null);
            InputStream out = coerceImageStream(r, input, urlString);
            SpiderDebug.log("hkimage", "图片解密完成 host=%s 耗时=%dms 结果=%s",
                    hostOfUrl(urlString), android.os.SystemClock.elapsedRealtime() - t0,
                    out == null ? "null" : out.getClass().getSimpleName() + (out == input ? "(回退原字节)" : ""));
            return out;
        } catch (Throwable t) {
            // 规则图片码拼装串留痕（与主路同口，2026-11-12）：code 已拼出才记，
            // 宿主资产预载失败时 code 仍为空、不记（那是另一条日志面）。本口是
            // 终局 catch（吞异常回原图），记完即清认领、不许带进下一发。
            if (!TextUtils.isEmpty(code)) {
                if (logEvalFailure(code, t, 0)) evalLoggedFailure.remove();
            }
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

    /** JS 字符串转义（adblockplus Utils.escapeJavaScriptString 原样：\n \r \" \' \\ 转义 + U+2028/U+2029）。
     * 公开给 UI 层合成规则拼串共用：凡把外部值嵌进 JS 字符串字面量（如 x5 承载页合成
     * find_rule 的 url），必须先经此转义——裸拼时值里的双引号会提前闭合字面量、
     * 整段在解析期崩（hk#5 实证）。 */
    public static String escapeJsString(String s) {
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
        return evalLazyInternal(jsCode, pageUrl, true);
    }

    /**
     * 点击动作探针专用求值：与 {@link #evalLazy} 同作用域同注入，唯一区别是
     * 求值为空时不 drain 共用 results 表。evalLazy 的 drain 是「只要一个地址」
     * 语境的契约（JS 没返回字符串就取第一条 url 当地址），探针的判定依据恰恰
     * 是 results 前后增量——drain 会把证据连同条目一起清掉（花都点封面死路：
     * detail() 交出 19 条又被 drain 排空、快照看不见增长、最后落进空壳详情）。
     * 本方法返回原始求值字符串（空/undefined/null 一律回 ""），results 原样保留，
     * 由调用方用 actionResultCount/snapshotActionResults 判定。
     */
    public String evalActionProbe(String jsCode, String pageUrl) throws Exception {
        return evalLazyInternal(jsCode, pageUrl, false);
    }

    private String evalLazyInternal(String jsCode, String pageUrl, boolean drainOnEmpty) throws Exception {
        final HkRule boundRule = this.rule;
        return submit(() -> {
            if (boundRule != null) this.rule = boundRule;
            error = null;
            // 环19 arm：lazy 在官形是独立一发 runScript，本发换鲜 scope。
            scope = assembleFreshScope();
            setContext(pageUrl);
            // 入口口令/输入框：若有一次性 input 覆盖（用户输入的文本），优先使用；
            // 否则按原逻辑用 pageUrl。读后清零，保证只生效一次。
            String inputVal = nextInputOverride != null ? nextInputOverride : (pageUrl == null ? "" : pageUrl);
            nextInputOverride = null;
            ScriptableObject.putProperty(scope, "input", inputVal);
            // 环20 A：CALLBACK_KEY 独立发补发——官每发 evalJS 无条件发新
            // UUID 串行（JSEngine.smali:1025-1044，含 evalLazy 这形 runScript 独立
            // 发），本发旧形只剩 assemble 预置的 Undefined 占位、规则脚本里
            // typeof CALLBACK_KEY 恒 'undefined' 与官不符。只补发串行本身＋
            // 根属性承载，不登记回查两表（登记是 parse 系带正文的三发的事，
            // 官 evalJS 同形，见 dig2 件3 边界）。
            String cbKey = genUuid();
            ScriptableObject.putProperty(scope, "CALLBACK_KEY", cbKey);
            Object r;
            try {
                r = evalJs(getMyCallbackKey(cbKey) + stripJsPrefix(jsCode));
            } catch (Exception e) {
                logEvalError("evalLazy", jsCode, e);
                throw e;
            }
            // 同 eval()：用 argStr() 处理数字/布尔返回值
            String s = argStr(r);
            s = s.trim();
            if (s.isEmpty() || "undefined".equals(s) || "null".equals(s)) {
                if (!drainOnEmpty) return "";
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
        // 8.83 MY_TICKET＝按规则标题生成的稳定票据（JSEngine
        // generateTicket：title→UUID、ticketMap 缓存）——旧形恒
        // 空串，规则直接读它当身份令牌时拿到假值（08 销账表
        // D9）。转录段的 generateTicket 本就在本类，直接用。
        String ticket = generateTicket(rule != null ? rule.getTitle() : null);
        ScriptableObject.putProperty(scope, "MY_TICKET", ticket == null ? "" : ticket);
        ScriptableObject.putProperty(scope, "MOBILE_UA", getMobileUa());
        ScriptableObject.putProperty(scope, "PC_UA", UA_PC);
        // P1：补全官方注入变量
        ScriptableObject.putProperty(scope, "MY_TYPE", myType == null ? "" : myType);
        ScriptableObject.putProperty(scope, "MY_CLASS_URL", myClassUrl == null ? "" : myClassUrl);
        ScriptableObject.putProperty(scope, "MY_CLASS_NAME", myClassName == null ? "" : myClassName);
        // MY_NAME 照官值：官 JSEngine 不注入此名，唯一来源是 plugin.js 的
        // windows.MY_NAME='海阔视界'（应用身份名）。旧形误填规则标题——同名
        // 不同值：hk-plugin 层经共享 scope 链读到的恒是规则标题，规则侧以
        // MY_NAME 判原版环境的检查在此恒落空（「请用原作者代码库」一族，
        // 账 hiker-dollar-undefined-receipt-20261111 Q2）。改填官值；规则
        // 标题的正经通道是 MY_RULE.title，不经此名。
        ScriptableObject.putProperty(scope, "MY_NAME", "海阔视界");
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
            ua.put("mobileUa", getMobileUa());
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
            // G40：序列化走 myRuleJson（官 JSONPreFilter.getSimpleFilter 过滤）。
            ScriptableObject.putProperty(scope, "MY_RULE", parseJson(myRuleJson(rule)));
        } catch (Throwable e) {
            ScriptableObject.putProperty(scope, "MY_RULE", rhinoCx.newObject(scope));
        }
        // 环20 A：_displayName 主路注入（官 getMyRule 在 MY_RULE 串后、
        // MY_TICKET 前以 const 声明同值，JSEngine.smali:5045-5079）——
        // 仅 displayName 非空才注入：为空时官整段不拼、无此名，plugin.js
        // 改名支的 typeof _displayName != 'undefined' 判据由此成立；
        // 主路旧形恒无此名、改名支在主路（搜索临时规则的主场景）永不触发。
        putDisplayNameInto(scope);
    }

    /** _displayName 注入一口（setContext 与 ensurePluginIn 的 MY_RULE 补位共用）：
     * 当前绑定规则 displayName 非空才以属性承载同值进目标 scope，空则不写。 */
    private void putDisplayNameInto(Scriptable target) {
        if (target == null) return;
        try {
            String dn = rule != null ? rule.getDisplayName() : "";
            if (!TextUtils.isEmpty(dn)) {
                ScriptableObject.putProperty(target, "_displayName", dn);
            }
        } catch (Throwable ignored) {
        }
    }

    /** G24 补结构（第七阶段复核不过项）：官 evalJS 每发经
     * getMyRule 尾巴 eval(getJsLazyPlugin()) 把 plugin.js 评进
     * 当发 scope——裸名 storage0（plugin.js:308 挂 windows＝
     * 当发全局）由此在每条求值路恒可达；宿主 Java 版 storage0
     * 已按 G24 摘除，只剩三条自带前缀的解析路能接住，其余路
     * （选择器 .js: 独行、registerTask 触发、定时器串回调、
     * lazy/详情 raw）裸名必 ReferenceError。落点收在 evalJs——
     * 主路全部求值路的唯一咽喉（解析/选择器/任务/定时器皆经此）。
     * 环19 后判据按 scope 走：当前 scope 无 storage0 才评入——鲜
     * scope 每发首次 evalJs 时评入本发 scope（与官每发评 plugin
     * 同频），同发内后续 evalJs 见 storage0 已在不再重评。plugin.js
     * 首行读 MY_RULE：装前先补 MY_RULE 缺位（以当前绑定规则，与
     * setContext 同式；已有不覆）。列表/搜索路自带的前缀重评维持
     * 原样（与改前同频）。 */
    private void ensureLazyPluginInstalled() {
        ensurePluginIn(scope);
    }

    /** plugin.js 按目标 scope 装（环19 复核订正2：字符串定时器/任务在捕获的
     * 登记发 scope 上求值时，插件面也须落在那一发 scope 上）。 */
    private void ensurePluginIn(Scriptable target) {
        if (target == null || rhinoCx == null) return; // 未 init：留待首个真求值
        try {
            if (ScriptableObject.getProperty(target, "storage0") != Scriptable.NOT_FOUND) return;
            if (ScriptableObject.getProperty(target, "MY_RULE") == Scriptable.NOT_FOUND) {
                try {
                    ScriptableObject.putProperty(target, "MY_RULE", parseJson(myRuleJson(rule)));
                } catch (Throwable e) {
                    ScriptableObject.putProperty(target, "MY_RULE", rhinoCx.newObject(target));
                }
                // 环20：MY_RULE 补位时 _displayName 同批补（与 setContext 同口径），
                // 否则 plugin.js 在本路首评时改名支仍拿不到别名。
                putDisplayNameInto(target);
            }
            String lazyPlugin = loadAsset("plugin.js");
            if (!TextUtils.isEmpty(lazyPlugin)) evalIntoScope(target, lazyPlugin, "hk-plugin");
        } catch (Throwable e) {
            Logger.t(TAG).d("install plugin.js failed: " + e.getMessage());
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

    // G35/G41：旧「init 末尾一次性闩＋共享 scope 直跑＋失败静默记账」整支
    // 已摘——preRule 只经 parsePreRule 两入口（转录段）按官形跑：失败走
    // setError 出口（false 形）或原样抛给上层（true 形），引擎内不再有
    // 静默记账支（旧跑法与旧记账字段全树 grep 零命中为验收口径）。

    /**
     * 注入海阔规则配置对象 {@code config}（海阔"长按规则→设置"的 key-value，如 config.host）。
     * 配置持久化在 plugins/hk/config/&lt;规则名&gt;.json；文件不存在时注入空对象，保证
     * {@code config.xxx} 不抛 ReferenceError（取值为 undefined）。
     */
    private void loadConfig() {
        loadConfigInto(scope);
    }

    /** loadConfig 的目标 scope 形（环19：鲜 scope 每发装配时把当前绑定规则的 config 评入本发 scope）。 */
    private void loadConfigInto(Scriptable target) {
        if (target == null) return;
        try {
            String json = HkRuleManager.get().loadRuleConfig(rule.getTitle());
            Object obj = parseJson(json);
            ScriptableObject.putProperty(target, "config", obj);
        } catch (Throwable e) {
            try {
                ScriptableObject.putProperty(target, "config", rhinoCx.newObject(target));
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * setResult 一族的 typeKey 取值（与 setPreResult 本体同口径、官包装层同源）：
     * JS 显式传参（args[3]）优先，否则本发汇点类型（parseList=home/parseSearch=search），
     * 再否则 currentMyType。
     */
    private String resultTypeKey(Object[] args) {
        String typeKey = (args != null && args.length > 3 && args[3] != null)
                ? argStr(args[3]) : null;
        if (typeKey == null) {
            HkFindDelivery.Sink s = activeFindSink.get();
            typeKey = s != null ? s.type() : currentMyType;
        }
        return typeKey;
    }

    /**
     * setResult 一族的交付口（G51 官式一次性回调，见 HkFindDelivery）：
     * find 求值（parseList/parseSearch）已按 CALLBACK_KEY 登记汇点时，交付进汇点
     * ——PRE 只记账不摘、正式交付打 phase 后即摘，同 key 二次交付被丢；
     * 不在 find 求值里（evalLazy 兜底、点击探针、parseDetailRaw 的 raw 通道）
     * 沿旧路入 results/rawResults，各自有通道的契约不变。
     * searchBranch（G27）：正式交付走 search 支——一次性摘汇点但不消费 phase
     * 记账（官 callbackSearchResult 不碰 preCalled）；非汇点旧路无 phase 概念、
     * 两支同归 collectResult。
     * canFallback（环23 复核订正5）：官四入口恒 true、回退重入时 false——
     * 对象无 data 的 ClassCast 形在 home/search 两支间互转重跑（官
     * callbackHomeResult:1743 与 callbackSearchResult catch 同形）。
     */
    private void deliverResult(Object[] args, boolean pre, boolean searchBranch, boolean canFallback) {
        if (collectRaw) {
            collectResult(args);
            return;
        }
        // 环23 复核订正3：分界取键改读交付自带的 callbackKey 隐参（args[1]，
        // 包装层覆写值），不再读当下 scope 的 CALLBACK_KEY 残留值——那会误伤
        // 在 find 发 scope 残留期到达的非 find 语境交付。（环20 追记 B：旧
        // currentCallbackKey 读者已整支摘除——官无此读者，三源集零调用。）
        String key = deliveryCallbackKey(args);
        HkFindDelivery.Sink sink = activeFindSink.get();
        if (sink == null) {
            if (key != null) sink = findDelivery.sink(key);
        }
        if (sink == null) {
            // 环23 断点2 回落分界：该 callbackKey 曾登记过汇点（find 语境）而
            // 汇点已摘除＝迟到交付，官 callbackMap miss 即丢，不许再写共享
            // 累加器被下一发 collectRaw 读走（G51 类迟到串表口）；从未登记过
            // 的 key 是非 find 语境的宿主同步 API 契约（evalLazy/详情），保留
            // collectResult 旧路。键集随 parse 入口 unregister 一并清（订正3）。
            if (key != null && findRegisteredKeys.contains(key)) {
                Logger.t(TAG).d("deliverResult: late delivery dropped (find key %s)", key);
                return;
            }
            collectResult(args);
            return;
        }
        // 环23 断点1 三形分界（官 callbackHomeResult/callbackSearchResult 原文）：
        // 非对象载荷／对象无 data／正常，三形先分清再决定记账与 showErr。
        String[] arrayJson = new String[1];
        Object[] objBox = new Object[1];
        String[] errBox = new String[1];
        int kind = classifyPayload(args, arrayJson, objBox, errBox);
        if (kind == PAYLOAD_OBJECT_DATA_STRING_ERR) {
            // 批1清尾·环23 边角（硬门审计 B）：data 为字符串且不可解析——官 fastjson
            // getJSONArray 对 String 走 JSON.parse 后 check-cast，解析失败抛
            // JSONException，官 catch 后 showErr 带其 message、恒不回退 search
            // （回退只认 ClassCastException）。记账同无 data 形（PRE 先记、正式记空段）。
            if (searchBranch) {
                showFindError(searchDescFallback() + "---搜索结果解析失败！请检查规则");
                findDelivery.drop(sink);
            } else {
                if (pre) findDelivery.deliverPre(sink, null);
                if (!pre) findDelivery.deliver(sink, null);
                showFindError("---分类结果解析失败！请检查规则：" + errBox[0]);
            }
            return;
        }
        if (kind == PAYLOAD_NON_OBJECT) {
            // 官：showErr；PRE 不记 preCalled、不摘汇点（后续正式交付 phase
            // 仍为 null）；正式 showErr＋摘汇点（remove 语义，不产生交付段）。
            if (searchBranch) {
                showFindError(searchDescFallback() + "---搜索结果解析失败！请检查规则");
                findDelivery.drop(sink);
            } else {
                showFindError("---分类结果解析失败！请检查规则：setHomeResult is not JSONObject");
                if (!pre) findDelivery.drop(sink);
            }
            return;
        }
        if (kind == PAYLOAD_OBJECT_NO_DATA) {
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = (Map<String, Object>) objBox[0];
            deliverNoData(args, sink, pre, searchBranch, canFallback, obj);
            return;
        }
        List<HkItem> items = searchBranch ? buildSearchItems(arrayJson[0]) : buildResultItems(arrayJson[0]);
        if (pre) {
            findDelivery.deliverPre(sink, items);
        } else if (searchBranch) {
            findDelivery.deliverSearch(sink, items);
        } else {
            findDelivery.deliver(sink, items);
        }
    }

    /** 交付自带的 callbackKey 隐参（setResult 一族第二参，包装层以本发键覆写）；缺参/空/字面 null 回 null。 */
    private String deliveryCallbackKey(Object[] args) {
        if (args == null || args.length <= 1 || args[1] == null) return null;
        Object adj = argsNativeObjectAdjust(args[1]);
        if (adj == null) return null;
        String k = String.valueOf(adj);
        return (k.isEmpty() || "null".equals(k)) ? null : k;
    }

    /**
     * 环23 复核订正5：对象无 data 形的官式收口（官 callbackHomeResult
     * :1726 起 try/catch 原文）。异常形态按官 fastjson 路径同形取真异常：
     * data 缺失＝getJSONArray 回 null 后 .size() 的 NullPointerException；
     * data 在而非数组＝强转的 ClassCastException。home 支：记账（phase 计算）
     * 在解析之前已发生——PRE 先记 preCalled；ClassCast 且 canFallback 时转
     * callbackSearchResult 同载荷重跑（p4=false，官 :1743-1744），余走
     * showErr（"---分类结果解析失败！请检查规则："＋异常 getMessage()）。
     * search 支：ClassCast 且 canFallback 时反向转 home 支（官 callbackSearchResult
     * catch 同形，p4=false），余走 showErr（规则名＋搜索失败文、无异常后缀）
     * ＋摘汇点。canFallback 取值：官四入口（setResult/setHomeResult/
     * setSearchResult/setPreResult）经 smali 实查恒传 true（setPreResult 五参
     * 形 v4=1），回退只在重入时关 false。
     * 正式支的空段记账近似沿批1 记名（官失败支无 onSuccess 段）。
     */
    private void deliverNoData(Object[] args, HkFindDelivery.Sink sink, boolean pre,
                               boolean searchBranch, boolean canFallback, Map<String, Object> obj) {
        Object data = obj == null ? null : obj.get("data");
        boolean castShape = data != null;
        Exception failure;
        if (castShape) {
            ClassCastException cce = null;
            try {
                List<?> probe = (List<?>) data;
                if (probe == null) throw new ClassCastException("data is not an array");
            } catch (ClassCastException e) {
                cce = e;
            }
            failure = cce;
        } else {
            NullPointerException npe = null;
            try {
                List<?> absent = null;
                absent.size();
            } catch (NullPointerException e) {
                npe = e;
            }
            failure = npe;
        }
        if (searchBranch) {
            if (castShape && canFallback) {
                deliverResult(args, pre, false, false);
                return;
            }
            showFindError(searchDescFallback() + "---搜索结果解析失败！请检查规则");
            findDelivery.drop(sink);
            return;
        }
        if (pre) findDelivery.deliverPre(sink, null);
        if (castShape && canFallback) {
            deliverResult(args, false, true, false);
            return;
        }
        if (!pre) findDelivery.deliver(sink, null);
        showFindError("---分类结果解析失败！请检查规则："
                + (failure == null ? "" : String.valueOf(failure.getMessage())));
    }

    private static final int PAYLOAD_NON_OBJECT = 0;
    private static final int PAYLOAD_OBJECT_NO_DATA = 1;
    private static final int PAYLOAD_NORMAL = 2;
    private static final int PAYLOAD_OBJECT_DATA_STRING_ERR = 3;

    /**
     * 环23 载荷三形判别（官 callbackHomeResult 入口分界）：顶层数组＝正常形
     * （官包成 {data:数组} 递归）；对象含 data 数组＝正常形；对象无 data
     * 数组＝OBJECT_NO_DATA；余（串/数/null/不可解析）＝NON_OBJECT。
     * 正常形时把条目数组 JSON 写入 outArray[0]；对象形时把解析后的对象图
     * 写入 outObj[0]（供 deliverNoData 按官异常形态分 data 缺失/非数组）。
     * data 为字符串的子情形（批1清尾，官 fastjson getJSONArray 亲读：String →
     * JSON.parse 后 check-cast JSONArray）：解析出数组＝正常形；解析出非数组
     * 值＝ClassCast 形（走 OBJECT_NO_DATA 的回退链）；解析出 null＝data 缺失
     * 形（把 data 置 null 后走 OBJECT_NO_DATA）；解析失败＝JSONException 形
     * （PAYLOAD_OBJECT_DATA_STRING_ERR，message 写入 outErr[0]，不回退）。
     */
    private int classifyPayload(Object[] args, String[] outArray, Object[] outObj, String[] outErr) {
        try {
            if (args == null || args.length == 0 || args[0] == null) return PAYLOAD_NON_OBJECT;
            String json = (args[0] instanceof NativeArray || args[0] instanceof NativeObject)
                    ? jsStringify(args[0])
                    : argStr(args[0]);
            if (json == null) return PAYLOAD_NON_OBJECT;
            String t = json.trim();
            if (t.startsWith("[")) {
                List<?> probe = GSON.fromJson(t, List.class);
                if (probe == null) return PAYLOAD_NON_OBJECT;
                outArray[0] = t;
                return PAYLOAD_NORMAL;
            }
            if (t.startsWith("{")) {
                Map<String, Object> obj = GSON.fromJson(t, new TypeToken<Map<String, Object>>() {}.getType());
                if (obj == null) return PAYLOAD_NON_OBJECT;
                outObj[0] = obj;
                Object data = obj.get("data");
                if (data instanceof List) {
                    outArray[0] = GSON.toJson(data);
                    return PAYLOAD_NORMAL;
                }
                if (data instanceof String) {
                    String ds = ((String) data).trim();
                    try {
                        com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(ds);
                        if (parsed != null && parsed.isJsonArray()) {
                            outArray[0] = ds;
                            return PAYLOAD_NORMAL;
                        }
                        if (parsed != null && parsed.isJsonNull()) {
                            obj.put("data", null);
                            return PAYLOAD_OBJECT_NO_DATA;
                        }
                        return PAYLOAD_OBJECT_NO_DATA;
                    } catch (Throwable parseErr) {
                        outErr[0] = String.valueOf(parseErr.getMessage());
                        return PAYLOAD_OBJECT_DATA_STRING_ERR;
                    }
                }
                return PAYLOAD_OBJECT_NO_DATA;
            }
            return PAYLOAD_NON_OBJECT;
        } catch (Throwable e) {
            return PAYLOAD_NON_OBJECT;
        }
    }

    /** find 语境的错误呈现口（官 OnFindCallBack.showErr 的宿主等价面）：记进本发错误通道供页面呈现，并留日志。 */
    private void showFindError(String msg) {
        error = msg;
        Logger.t(TAG).d("showFindError: %s", msg);
    }

    /**
     * 环23 断点3：search 支条目映射分家（官 callbackSearchResult，JE:1170 起，
     * 逐项对原文）——title/url 直取；img 序 img&gt;pic&gt;pic_url（复核订正4：
     * 按键存在判定、与 home 支 buildResultItems 统一，img="" 也赢；
     * buildResultItems 的 image/cover 等官无兼容字段不许进本支）；desc 暂存
     * 条目 desc（合成时映射 descMore，见 toSearchResults）；content 直取；
     * extra 串原样／null 置 null／余 JSON 串化；type 恒 "video"（合成时置）。
     */
    private List<HkItem> buildSearchItems(String jsonArray) {
        List<Map<String, Object>> list = GSON.fromJson(jsonArray, MAP_LIST_TYPE);
        if (list == null) return null;
        List<HkItem> out = new ArrayList<>(list.size());
        for (Map<String, Object> m : list) {
            if (m == null) continue;
            HkItem item = new HkItem();
            item.setTitle(str(m, "title"));
            item.setUrl(str(m, "url"));
            String pic;
            if (m.containsKey("img")) pic = str(m, "img");
            else if (m.containsKey("pic")) pic = str(m, "pic");
            else pic = str(m, "pic_url");
            item.setPic(pic);
            item.setDesc(str(m, "desc"));
            item.setContent(str(m, "content"));
            Object extraObj = m.get("extra");
            if (extraObj instanceof String) {
                item.setExtra((String) extraObj);
            } else if (extraObj != null) {
                item.setExtra(GSON.toJson(extraObj));
            } else {
                item.setExtra(null);
            }
            out.add(item);
        }
        return out;
    }

    /**
     * 官 callbackSearchResult 的 desc 基值：ruleKey（当发 MY_RULE）的 title，
     * 其 displayName 非空时覆盖 title（JE:1774-1780 原文）。当发规则读不到
     * 时回落宿主绑定规则。
     */
    private String searchDescFallback() {
        HkRule r = myRuleFromScope();
        if (r == null) r = rule;
        if (r == null) return "";
        String dn = r.getDisplayName();
        if (dn != null && !dn.isEmpty()) return dn;
        return r.getTitle() == null ? "" : r.getTitle();
    }

    /** setResult 一族的载荷归一：JS 值（NativeArray/NativeObject/JSON 串）→ 条目数组 JSON 串。 */
    private String resultPayloadJson(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) return null;
        String json = (args[0] instanceof NativeArray || args[0] instanceof NativeObject)
                ? jsStringify(args[0])
                : argStr(args[0]);
        if (json == null) return null;
        String t = json.trim();
        // 官方 callbackHomeResult 兼容：setResult({data:[...]}) 的对象形式。
        // 之前只认顶层数组，对象形式会被 GSON 抛错静默丢弃 → 整个列表为空（"加载失败"）。
        if (t.startsWith("{")) {
            Map<String, Object> obj = GSON.fromJson(t, new TypeToken<Map<String, Object>>() {}.getType());
            Object data = obj == null ? null : obj.get("data");
            if (data instanceof List) return GSON.toJson(data);
            Logger.t(TAG).d("collectResult: object without data array");
            return null;
        }
        return t;
    }

    private void collectResult(Object[] args) {
        try {
            String json = resultPayloadJson(args);
            if (json != null) appendResultItems(json);
        } catch (Throwable e) {
            Logger.t(TAG).d("collectResult failed: %s", e.getMessage());
        }
    }

    /** 载荷 → 条目表（只转换不落表）：官式交付的落子逻辑，与 collectResult 同归一口径。 */
    private List<HkItem> extractResultItems(Object[] args) {
        try {
            String json = resultPayloadJson(args);
            return json == null ? null : buildResultItems(json);
        } catch (Throwable e) {
            Logger.t(TAG).d("extractResultItems failed: %s", e.getMessage());
            return null;
        }
    }

    /** 把 JSON 数组字符串解析为条目并追加到 results（collectResult 的实际落子逻辑）。 */
    private void appendResultItems(String jsonArray) {
        if (collectRaw) {
            List<Map<String, Object>> list = GSON.fromJson(jsonArray, MAP_LIST_TYPE);
            if (list == null) return;
            for (Map<String, Object> m : list) {
                Map<String, String> raw = new HashMap<>();
                for (Map.Entry<String, Object> e : m.entrySet()) {
                    raw.put(e.getKey(), e.getValue() == null ? "" : stringifyValue(e.getValue()));
                }
                rawResults.add(raw);
            }
            return;
        }
        List<HkItem> items = buildResultItems(jsonArray);
        if (items != null) results.addAll(items);
    }

    /** 条目数组 JSON → HkItem 表（8.83 convertToArticleList 字段口径逐项，只转换不落表）。 */
    private List<HkItem> buildResultItems(String jsonArray) {
        List<Map<String, Object>> list = GSON.fromJson(jsonArray, MAP_LIST_TYPE);
        if (list == null) return null;
        List<HkItem> out = new ArrayList<>(list.size());
            for (Map<String, Object> m : list) {
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
                }
                // 环30 D30-6：官 convert 不读任何顶层键——旧形在 extra 缺失/null 时
                // 把顶层 id/cls/pageTitle/newWindow/lineVisible/textAlign 合成 extra
                // 已整支摘除（会造出官没有的锚与 cls，deleteItemByCls/_findItem 跟着偏）。
                out.add(item);
            }
        return out;
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
     * 搜索结果映射：把汇点合成的 HkItem 按 8.83 callbackSearchResult 映射为
     * 独立 SearchResult（title/url/desc/img/content/extra，type 硬编码 "video"）。
     * 8.83 extra 语义：String 原样 / null 置 null / 其他 JSON.toJSONString。
     */
    private List<SearchResult> toSearchResults(List<HkItem> items) {
        // 环23 对官 callbackSearchResult 合成口径：desc＝规则 title（displayName
        // 非空覆盖，见 searchDescFallback），条目 desc 落 descMore；img 已由
        // buildSearchItems 按 img>pic>pic_url 取定；extra 原样（空即官 null
        // 语义，HkItem 归一为 "" 时不置）；type 恒 "video"。
        String fallbackDesc = searchDescFallback();
        List<SearchResult> out = new ArrayList<>(items.size());
        for (HkItem it : items) {
            if (it == null) continue;
            SearchResult sr = new SearchResult();
            sr.setTitle(nz(it.getTitle()));
            sr.setUrl(nz(it.getUrl()));
            sr.setDesc(fallbackDesc);
            sr.setDescMore(nz(it.getDesc()));
            sr.setImg(nz(it.getPic()));
            sr.setContent(nz(it.getContent()));
            sr.setType("video");
            if (it.getExtra() != null && !it.getExtra().isEmpty()) {
                sr.setExtra(it.getExtra());
            }
            out.add(sr);
        }
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
                // 环19 arm：详情求值同为独立一发，本发换鲜 scope。
                scope = assembleFreshScope();
                setContext(myUrl);
                // 环20 A：CALLBACK_KEY 独立发补发（同 evalLazy 口径：只补
                // 串行与根属性，不登记回查两表）。
                String cbKey = genUuid();
                ScriptableObject.putProperty(scope, "CALLBACK_KEY", cbKey);
                evalJs(getMyCallbackKey(cbKey) + stripJsPrefix(jsCode));
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
     * G24 改名保留：JSON 回解析取值（原存储封装 get* 的读语义，宿主对象
     * 摘除后仅 juItem 的 id 解析在 Java 侧直用）：存的是 JSON 对象/数组
     * 则解析回 JS 对象返回，否则原样返回字符串；缺省值（第 2 参数）有
     * 则原样返回，不做解析。键走 myVarKeyFor 命名空间、表走 myVarsFor。
     */
    private Object jsonGetByKey(java.util.Map<String, String> map, String key, Object[] args) {
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
     * 裸 fetch 不补。UA 在否照官精确键 "User-Agent" 判（环 24-2），小写键在场仍补。
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
            // 环 24-2（硬门审计 B，官 generateRequestOptions JE:2880）：头表先取
            // "headers"，缺位才回落 "header" 单数键，两者皆无才新建放回 "headers"；
            // UA 判据＝精确键 "User-Agent"（JsonObject.has 即精确键）；补入写进最终
            // 生效的那份头表——回落支写回 "header" 那份，不新建 "headers"，否则
            // fetchSync 的 Req 归一见 "headers" 在场便不再做单数映射、规则的单数头
            // 整组丢失。旧形只看复数键＋equalsIgnoreCase 宽判，已照官改。
            com.google.gson.JsonObject headers = null;
            if (obj.has("headers") && obj.get("headers").isJsonObject()) {
                headers = obj.getAsJsonObject("headers");
            } else if (obj.has("header") && obj.get("header").isJsonObject()) {
                headers = obj.getAsJsonObject("header");
            }
            if (headers == null) {
                headers = new com.google.gson.JsonObject();
                obj.add("headers", headers);
            }
            if (!headers.has("User-Agent")) {
                headers.addProperty("User-Agent", getMobileUa());
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

    // 8.83 JSEngine.xpath/xpathArray 同款管线（JE:26933-27216）：先 HtmlCleaner.clean()
    // 清洗成 TagNode、再 DomSerializer.createDOM 成 w3c Document，才交给 XPath 求值。
    // 官形从不拿原始 HTML 直喂严格 XML 解析——不规范 HTML（错位闭合等）在清洗段被
    // 修复；旧形直吃原始串严格解析必抛（2026-11-10 他真机 xpath 噎死案，账 G50）。
    private static org.w3c.dom.Document parseXmlDoc(String html) throws Exception {
        org.htmlcleaner.TagNode node = new org.htmlcleaner.HtmlCleaner().clean(html);
        return new org.htmlcleaner.DomSerializer(new org.htmlcleaner.CleanerProperties()).createDOM(node);
    }

    private Object xpathArrayImpl(Object[] args) {
        // 8.83 JSEngine.java:2982：失败 throws Exception
        if (args == null || args.length < 2) throw new RuntimeException("xpathArray：参数不足");
        try {
            javax.xml.xpath.XPath xp = javax.xml.xpath.XPathFactory.newInstance().newXPath();
            org.w3c.dom.NodeList nodes = (org.w3c.dom.NodeList) xp.evaluate(
                    argStr(args[1]), parseXmlDoc(argStr(args[0])), javax.xml.xpath.XPathConstants.NODESET);
            List<String> out = new ArrayList<>();
            // 8.83 xpathArray（官包 JSEngine.smali:27105-27216）：取值循环整段
            // try/catch(Throwable)，异常只记 Timber、回已取到的部分表，不上抛（G31）。
            try {
                for (int i = 0; i < nodes.getLength(); i++) out.add(nodes.item(i).getNodeValue());
            } catch (Throwable e) {
                Logger.t(TAG).e(e, "xpathArray 取值中断，回部分结果");
            }
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

    // 8.83 HttpHelper.buildParamStr（官包 HttpHelper.smali:347-452 亲读）：key=value
    // 以 & 连接，值是字符串原样、其它转 JSON.toJSONString，不做 URL 编码；值整体经
    // replace("&","＆＆")（U+FF06×2，smali:423）防表单二次切分。HH 版早有此转义，
    // JS 路此前独缺（官包全量抠账 G12）
    private static String buildParamStr(com.google.gson.JsonObject json) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, com.google.gson.JsonElement> e : json.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=');
            com.google.gson.JsonElement v = e.getValue();
            String vs = v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()
                    ? v.getAsString() : GSON.toJson(v);
            sb.append(vs == null ? "null" : vs.replace("&", "＆＆"));
        }
        return sb.toString();
    }

    // 环 24-12（硬门审计 B，本轮钉死）：8.83 fetch 开关统一经 HttpHelper.tryGetBool
    // （HH:3295）——tryGet 取值后 check-cast Boolean：值为 Boolean 直用、null/缺位
    // 回缺省（false）、其余类型（数字/字符串/对象）抛 ClassCastException，被 fetch0
    // 全局 catch 压成失败形（裸回 ""、包装回带 error 的 JSON）。旧注释自称「布尔
    // true 或数字非 0 视为开」是误账（数字 1 旧形当开、字符串 "true" 旧形当关且照常
    // 发网），已订正并照官改 check-cast 语义：CCE 不在此吞，任其穿到 fetchSync 的
    // 失败形装配。
    private static boolean optFlag(com.google.gson.JsonObject obj, String key) {
        if (obj == null) return false;
        com.google.gson.JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull()) return false;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
        throw new ClassCastException("tryGetBool: " + key + " is not a Boolean");
    }

    /**
     * 环 24-12 复核订正（hiker 子路）：注册点分发前取旗＋fetchByHiker 分发的统一收口。
     * 官 fetch0 里 tryGetBool 的 ClassCastException（旗值非 Boolean）被 fetch0 全局
     * catch 压成失败形（裸回 ""）；咱家六个注册点（fetch/post/request/fetchPC/postPC/
     * postRequest）的分发调用在 fetchSync 失败形装配之外，CCE 直调会穿桥成 JS 可见
     * 异常，与官分叉。故取旗段在此收口：CCE 时该次调用回 ""（不发分发、与官失败形
     * 同）；取旗之外的分发异常不在此吞、原样穿出。fetchSync 内既有失败形不动。
     */
    private Object fetchByHikerWithFlags(String url, String optionsJson) {
        return fetchByHikerWithFlags(url, optionsJson, null);
    }

    /** 环24-11 簇一：ruleKey 显式参透传形（注册点 args[2]），余与两参版同。 */
    private Object fetchByHikerWithFlags(String url, String optionsJson, Object ruleKey) {
        boolean toHex;
        boolean inputStream;
        try {
            toHex = optFlagFromJson(optionsJson, "toHex");
            inputStream = optFlagFromJson(optionsJson, "inputStream");
        } catch (ClassCastException e) {
            return "";
        }
        return fetchByHiker(url, ruleKey, toHex, inputStream);
    }

    /** 自 options JSON 串直取布尔旗（注册点分流透传 toHex/inputStream 用）。
     * 与 optFlag 同 check-cast 语义：ClassCastException 不吞、穿出到调用方失败形。 */
    private static boolean optFlagFromJson(String optionsJson, String key) {
        if (TextUtils.isEmpty(optionsJson)) return false;
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(optionsJson);
            if (parsed != null && parsed.isJsonObject()) return optFlag(parsed.getAsJsonObject(), key);
        } catch (ClassCastException cce) {
            throw cce;
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** fetchSync 回的流本体/含流组装 Map 在自消费面（fetchCookie/batchFetch）弃置时关流，防连接泄漏。 */
    private static void closeFetchStream(Object r) {
        try {
            if (r instanceof java.io.InputStream) ((java.io.InputStream) r).close();
            else if (r instanceof Map) {
                Object b = ((Map<?, ?>) r).get("body");
                if (b instanceof java.io.InputStream) ((java.io.InputStream) b).close();
            }
        } catch (Throwable ignored) {
        }
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
     * 从 fetch options 里提取「字节数组 body」：body 是 JSON 数组且元素全为
     * -128..255 的整数时按字节还原（值 & 0xFF，兼容 JS 无符号数组与 Java
     * byte[] 的有符号序列化两形态）；其余形态（字符串/对象/含非字节元素）
     * 返回 null、维持原 String body 路径。见 fetchSync 内的复诊注释。
     */
    private static byte[] extractArrayBody(com.google.gson.JsonObject optObj) {
        if (optObj == null || !optObj.has("body")) return null;
        com.google.gson.JsonElement body = optObj.get("body");
        if (body == null || !body.isJsonArray()) return null;
        com.google.gson.JsonArray arr = body.getAsJsonArray();
        byte[] out = new byte[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            com.google.gson.JsonElement e = arr.get(i);
            if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
            double dv = e.getAsDouble();
            long lv = e.getAsLong();
            if (dv != lv || lv < -128 || lv > 255) return null;
            out[i] = (byte) (lv & 0xFF);
        }
        return out;
    }

    /**
     * 同步 fetch（跑在 JS 单线程上，直接阻塞等待）。
     * options 为 JSON：{headers, body/data, method, timeout, withHeaders}。
     */
    // 回值形态随 options 而变（8.83 fetch0 同构）：缺省正文串；withHeaders/
    // withStatusCode 时五字段包装串；inputStream 时流本体或含流的组装 Map
    private Object fetchSync(String url, String optionsJson) {
        if (TextUtils.isEmpty(url)) return "";
        // hiker://empty 等非 http 协议直接返回空（由上层按形态分流）
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "";
        // UA 口径：不无条件补（8.83 裸 fetch 亦不补、无条件补移动 UA 的旧做法已撤），
        // 但规则自己声明了 ua 时按声明补——见下方注入段。
        boolean withHeaders = false;
        boolean withStatusCode = false;
        boolean onlyHeaders = false;
        // 失败分层三值（8.83 FetchResponse 口径）：响应回来后在执行段登记实
        // headers/realUrl/实码；execute 本身失败时维持初值（headers=null、
        // realUrl=入参 url、code=0）——catch 据此组失败包装（G9）。声明在
        // try 外，catch 才可见
        Map<String, List<String>> respHeaders = null;
        String respRealUrl = url;
        int respCode = 0;
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
            // 二进制 body 识别（2026-10-31 橘汁复诊）：规则以字节数组作 body 时
            // （JS 数字数组或 _base64.decode 回的 Java byte[]，经 stringifyArg 后
            // 都是 JSON 数字数组，本机 Rhino 1.7.15 实测），8.83 语义是原样字节
            // 发送；此前 Req 把数组归一成 "[137,80,...]" 文本当正文，protobuf
            // 类接口（橘汁 zone/play 的公钥/取流 POST）收到的全是坏体、必失败。
            // 在此把数组还原成原始字节、走 HkHttp.toRaw 原样发送；元素须全为
            // -128..255 的整数（Java byte[] 序列化为有符号值），否则不认、维持原路。
            byte[] rawBody = extractArrayBody(optObj);
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
            boolean inputStream = optFlag(optObj, "inputStream");
            onlyHeaders = optFlag(optObj, "onlyHeaders");
            if (onlyHeaders) withHeaders = true;
            String method = req.getMethod();
            // 8.83：HEAD/DELETE 共用寄存器置 withHeaders=true（HHs:724,1038-1110 区）；
            // header 是咱家主路自认的 HEAD 别名（官无，存量保留）
            if (method.equalsIgnoreCase("head") || method.equalsIgnoreCase("header")
                    || method.equalsIgnoreCase("delete")) withHeaders = true;
            if (inputStream) {
                // 8.83 fetch0 inputStream 支（HHs:920 区取键、FetchCallback 在
                // interceptor 前取 byteStream 入 FetchResponse；咱家 HH 版同构）：
                // 响应不经 try-with 关闭，流随连接存活、规则侧 closeMe 关。组装
                // （HH FetchCallback:组装段）：withHeaders/withStatusCode 任一开 →
                // Map{body=流,headers,url,statusCode,error}，否则直回流本体。
                Response res = (rawBody != null ? HkHttp.toRaw(url, req, rawBody) : HkHttp.to(url, req)).execute();
                resCode = res.code();
                lastUrl = url;
                if (res.code() < 200 || res.code() >= 300) recordFetchDiag(url, res.code(), null, false);
                Map<String, List<String>> sHeaders = res.headers().toMultimap();
                String sRealUrl = res.request().url().toString();
                int sCode = res.code();
                ResponseBody sBody = res.body();
                if (sBody == null) {
                    res.close();
                    if (withHeaders || withStatusCode) return wrapFetch(null, sHeaders, sRealUrl, sCode, null);
                    return "";
                }
                java.io.InputStream stream = sBody.byteStream();
                if (withHeaders || withStatusCode) {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("body", stream);
                    m.put("headers", sHeaders);
                    m.put("url", sRealUrl);
                    m.put("statusCode", sCode);
                    m.put("error", null);
                    return m;
                }
                return stream;
            }
            try (Response res = (rawBody != null ? HkHttp.toRaw(url, req, rawBody) : HkHttp.to(url, req)).execute()) {
                resCode = res.code();
                lastUrl = url;
                if (res.code() < 200 || res.code() >= 300) {
                    recordFetchDiag(url, res.code(), null, false);
                }
                Map<String, List<String>> headers = res.headers().toMultimap();
                String realUrl = res.request().url().toString();
                respHeaders = headers;
                respRealUrl = realUrl;
                respCode = res.code();
                if (onlyHeaders) {
                    return wrapFetch(null, headers, realUrl, res.code(), null);
                }
                ResponseBody body = res.body();
                // 8.83 CharsetStringConvert：响应头 Content-Length 超 0x1400000 抛
                // IOException，文案逐字 "content-length is too big: N"（官包该类
                // smali:72-99 亲读），进失败包装的 error
                if (body != null && body.contentLength() > 20L * 1024 * 1024) throw new java.io.IOException("content-length is too big: " + body.contentLength());
                byte[] bytes;
                try {
                    bytes = body == null ? new byte[0] : body.bytes();
                } catch (java.io.IOException readErr) {
                    // 8.83 CharsetStringConvert：读体 IOException 在 converter 内
                    // 吞掉回 ""（error 不置位），不进失败包装（G9 分层）
                    bytes = null;
                }
                if (bytes != null && bytes.length == 0 && res.code() >= 200 && res.code() < 300) {
                    // 诊断：200 但空 body（JSON.parse("") 抛 Empty JSON 的源头）
                    recordFetchDiag(url, res.code(), null, true);
                }
                String content;
                // 环 24-13（硬门审计 B）：显式 charset 解码失败的附带 error（官
                // CharsetStringConvert 失败形：error 照置、正文以 ResponseBody.string()
                // 重读，见下方解码支）；无此失败时维持 null。
                String decodeError = null;
                if (bytes == null) {
                    content = "";
                } else if (toHex) {
                    StringBuilder sb = new StringBuilder(bytes.length * 2);
                    for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
                    content = sb.toString();
                } else {
                    // N4: 8.83 的 rejectCoding 是请求侧标志（POST 时 body 原样发送，跳过编码处理，
                    // 见 HkHttpHelper.buildPostBody），不是响应侧。响应侧按 CharsetStringConvert
                    // 分流（官包该类 smali:57-207 亲读）：请求头 content-type 显式声明了
                    // charset/charst → 按其解码；未声明 → ResponseBody.string() 本体语义
                    // （BOM 嗅探＋响应 Content-Type 的 charset、缺省 UTF-8）。此前一律
                    // req.getCharset()（未声明硬回 UTF-8），响应声明 GBK 的页面必乱码。
                    String explicitCs = req.getCharsetOrNull();
                    if (explicitCs != null && !explicitCs.isEmpty()) {
                        // 环 24-13（硬门审计 B，本轮钉死，订正旧注「上穿成失败包装」）：
                        // 官 CharsetStringConvert 显式 charset 支抛
                        // UnsupportedEncodingException 穿出 converter 后，fetch0 同步
                        // 装配段见结果为空且 rawResponse 在——以 rawResponse.body()
                        // .string()（OkHttp 推断解码）重读原文作正文，error 另置
                        // （HH:2130-2300＋尾段 :2380-2560 亲读）。咱家 bytes 与响应
                        // contentType 已在手，等价法＝ResponseBody.create(bytes,
                        // contentType).string() 重读；error 经 decodeError 带到包装
                        // 装配，裸回路径同步得重读串（旧形外层 catch 把正文压成
                        // body=null/裸回 ""，正文丢失）。
                        try {
                            content = new String(bytes, explicitCs);
                        } catch (java.io.UnsupportedEncodingException uee) {
                            decodeError = uee.getMessage();
                            try {
                                content = ResponseBody.create(bytes, body == null ? null : body.contentType()).string();
                            } catch (java.io.IOException readErr) {
                                // 同读体分层：重读的 IOException 官在 converter 内吞掉回 ""
                                content = "";
                            }
                        }
                    } else {
                        try {
                            content = ResponseBody.create(bytes, body == null ? null : body.contentType()).string();
                        } catch (java.io.IOException readErr) {
                            // 同读体分层：body.string() 的 IOException 官在
                            // converter 内吞掉回 ""
                            content = "";
                        }
                    }
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
                if (withHeaders || withStatusCode) return wrapFetch(content, headers, realUrl, res.code(), decodeError);
                return content;
            }
        } catch (Throwable e) {
            Logger.t(TAG).d("fetch failed %s: %s", url, e.getMessage());
            recordFetchDiag(url, 0, e, false);
            resCode = 0;
            // 8.83：要求了包装时失败也要回带 error 的包装 JSON，读 .error 的规则才能感知失败原因；
            // 此前一律返回 ""，规则只会撞上 Empty JSON。包装三值按失败分层（G9）：
            // execute 本身失败＝headers null/入参 url/0；响应已回后失败（超限、
            // charset 名非法）＝实 headers/realUrl/实码。此前一 catch 全压成
            // 空 headers 表＋入参 url＋0，已回响应的失败全错位
            if (withHeaders || withStatusCode || onlyHeaders) {
                return wrapFetch(null, respHeaders, respRealUrl, respCode, e.getMessage());
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
                sanitizeKvUndefinedOnce();
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

    /** B#2 配套一次性清理：旧版 setItem 把 JS undefined 经 argStr 存成字面量
     * "undefined" 的存量毒值（旧写 bug 产物，现行 setItem 经 tryGetString
     * 已不可能再写出）。getItem 读路解毒支摘除后由数据层收口：与
     * kv-poison 同形——先备份再摘、独立记号只跑一次。 */
    private void sanitizeKvUndefinedOnce() {
        try {
            File dir = kvFile().getParentFile();
            if (dir == null) return;
            File marker = new File(dir, "kv-undefined-cleaned.marker");
            if (marker.exists()) return;
            Map<String, String> removed = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : kv.entrySet()) {
                if ("undefined".equals(e.getValue())) removed.put(e.getKey(), e.getValue());
            }
            if (!removed.isEmpty()) {
                File bakDir = new File(dir, "kv-poison-backup");
                bakDir.mkdirs();
                String ts = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                        .format(new java.util.Date());
                Map<String, Object> bak = new LinkedHashMap<>();
                String title = rule != null ? rule.getTitle() : "";
                bak.put("rule", title);
                bak.put("time", ts);
                bak.put("removed", removed);
                java.nio.file.Files.write(new File(bakDir, "backup-undefined-" + ts + ".json").toPath(),
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

    /* 8.83 RequireDescription / RequireItem 模型：2026-11-11 代码库批 F3 已按
     * 官包名补为真类（com.example.hikerview.ui.rules.model.*），旧私有副本
     * 随登记层移植改委托一并摘除。 */


    // ---- D1 fields (8.83 JSEngine state) ----
    // _globalMap 全进程一本账（照官收一，08 销账表记名项）：8.83
    // 只有 JSEngine.globalMap 一张；咱家此前主/图片作用域用静态
    // 进程表 imageGlobalMap、转录 runScript 与 D1 evalJS 用本实
    // 例 HashMap，两张表跨面读写互相读不到。本字段直接指进程
    // 表，本类全部注入点（runScript＋三个 evalJS）随之同表。
    private final Map<String, Object> globalMap = imageGlobalMap;
    private final HkRc4 rc4 = new HkRc4();
    private final HkMyBase64 myBase64 = new HkMyBase64();
    // G21：官 callbackMap 为 ConcurrentHashMap（JSEngine:56＋构造 :267-272）——
    // get(null) 抛 NPE 的官口径靠它成立（补正件三兜底层），同改并发安全。
    private final Map<String, JSEngineCallback<?>> callbackMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, String> resCodeMap = new HashMap<>();
    private final List<String> methodList = new ArrayList<>();
    /** 环19：官 initRequire 的 provider 是类级单例（双重检查锁），改 static 与官同级。 */
    private static volatile StrongCachingModuleScriptProvider cachingModuleScriptProvider;
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

    /** 8.83 JSEngine.getFilesDir()（JE:3563）：rootDir＋"/rules/files/"（尾分隔符），
     * 与 hkFilesDirPrefix 同值。旧形少 "rules" 段且无尾分隔符，含此值的封锁闸
     * （decrypt 路径 contains 判）对真规则路径恒不中，照官收回；官为 public static，同收。 */
    public static String getFilesDir() {
        return hkFilesDirPrefix();
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

    /** 主路装配回调（armCallbackKey 与 evalJS 装配共用）：showErr 与无回调时的宿主
     * Toast 回退同行为（setErrorImpl 原有分支）；onUpdate 环 25-1 换真回调——旧形
     * 空实现把 addListener 在主路登记的监听全数吞掉（搜索路另有真回调、主路独缺）。
     * 照官 JsEngineBridge$1.onUpdate：以仅 title=value 的条目经 bindObjectListener
     * 绑回页面按 key 分发；监听未置时记账了事（见 bindObjectListener 字段注）。 */
    private JSEngineCallback<?> callbackKeySink() {
        return new JSEngineCallback<Object>() {
            @Override public void onSuccess(String key, Object result) {}
            @Override public void onUpdate(String key, String value) {
                BindObjectToViewListener listener = bindObjectListener;
                if (listener == null) {
                    Logger.t(TAG).d("onUpdate without page listener: key=%s value=%s", key, value);
                    return;
                }
                HkItem item = new HkItem();
                item.setTitle(value);
                try {
                    listener.bindObjectToView(key, item);
                } catch (Throwable e) {
                    Logger.t(TAG).d("bindObjectToView failed: %s", e.getMessage());
                }
            }
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

    /**
     * 8.83 JSEngine.log(Object, Object) JS API（smali:19819，@Parameter o/ruleKey）。
     * G2：本法即官注解法本人（公开＋注解，预置段生成 method_log 句柄与包装函数），
     * 体走实现体登记表（registerApi 的 log lambda → logImpl）；转录段内部
     * 调用（evalPrivateJS 区的非 JS 代码提示）同经此法，与官一致。
     */
    @JSAnnotation()
    public void log(@Parameter(value = "o") Object o, @Parameter(value = "ruleKey") Object ruleKey) {
        dispatchApi("log", new Object[]{o, ruleKey});
    }

    /**
     * 8.83 4-arg setError (smali 24004, 530 lines). D2: 完整实现，委托 setErrorImpl。
     * G2：本法即官注解法本人（公开＋@JSAnnotation alias="error"＋四 @Parameter，
     * 预置段生成 method_setError 句柄与包装函数，包装层恒覆写 callbackKey/
     * ruleKey/evalKey——B#6 的行号还原靠 evalKey=eval 这条覆写成立）。
     */
    @JSAnnotation(alias = "error")
    public void setError(@Parameter(value = "o") Object o,
                         @Parameter(value = "callbackKey") Object callbackKey,
                         @Parameter(value = "ruleKey") Object ruleKey,
                         @Parameter(value = "evalKey") Object extra) {
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

    /**
     * hiker://assets/ -> app assets：直调正表
     * {@code HkHikerRuleUtil.getAssetsFileByHiker}（8.83 同名方法，官
     * fetchByHiker assets 支 JE:2042-2045 同款委托），与 loadAssetText
     * 共用同一份六类白名单判据，不再直读任意资产；表外回 ""、表内
     * 缺失亦回 ""（官 getAssetsString 异常吞后回空串），不再回 null。
     */
    private String hkGetAssetsFile(String url) {
        try {
            return com.fongmi.android.tv.api.hk.util.HkHikerRuleUtil.getAssetsFileByHiker(url);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * hiker:// 规则取数（8.83 HikerRuleUtil.getRulesByHiker 同步版，官包
     * HikerRuleUtil.smali:174-590）：剥 hiker:// 后六支——home@标题查单条、
     * home 无标题回全规则列表、bookmark/download/collection/history、
     * 余（含 empty）回 ""。宿主全支移植已在 util.HkHikerRuleUtil（CodeUtil
     * 路在用），fetch 路此前只自拼 home@标题一支、余支全回 ""（G18）；
     * 此处改委托同一份移植，两条路共用一本账，不再各拼一份。
     */
    private String hkGetRulesByHiker(String url) {
        try {
            return com.fongmi.android.tv.api.hk.util.HkHikerRuleUtil.getRulesByHiker(url);
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
    Scriptable ambPrev = null;
    boolean ambEntered = false;
    enterEvalLog();
    try {
        ScriptableObject v2 = v1cx.initStandardObjects();
        org.mozilla.javascript.ImporterTopLevel.init(v1cx, v2, false);
        initRequire(v1cx, v2); // MISSING
        installJavaAdapterShim(v2);
        ScriptableObject.putProperty(v2, "input", Context.javaToJS(p2, v2));
        ScriptableObject.putProperty(v2, "_globalMap", Context.javaToJS(globalMap, v2)); // field MISSING
        ScriptableObject.putProperty(v2, "rc4", Context.javaToJS(rc4, v2)); // field MISSING
        ScriptableObject.putProperty(v2, "_base64", Context.javaToJS(myBase64, v2)); // field MISSING
        ScriptableObject.putProperty(v2, "javaContext", Context.javaToJS(this, v2));
        ScriptableObject.putProperty(v2, "javaLoader", Context.javaToJS(clazz.getClassLoader(), v2)); // field MISSING
        registerApiInto(v2);
        ambPrev = enterAmbientScope(v2);
        ambEntered = true;
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
        logEvalFailure(v3, e, 2);
        Context.exit();
        return p2;
    } catch (Throwable t) {
        // smali :catchall_0 — exit then rethrow (NOT a finally: normal paths exit explicitly)
        Context.exit();
        throw t;
    } finally {
        exitEvalLog();
        if (ambEntered) exitAmbientScope(ambPrev);
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
    Scriptable ambPrev = null;
    boolean ambEntered = false;
    enterEvalLog();
    try {
        ScriptableObject v7 = v6.initStandardObjects();
        org.mozilla.javascript.ImporterTopLevel.init(v6, v7, false);
        initRequire(v6, v7); // MISSING
        installJavaAdapterShim(v7);
        ScriptableObject.putProperty(v7, "_globalMap", Context.javaToJS(globalMap, v7)); // field MISSING
        ScriptableObject.putProperty(v7, "rc4", Context.javaToJS(rc4, v7)); // field MISSING
        ScriptableObject.putProperty(v7, "_base64", Context.javaToJS(myBase64, v7)); // field MISSING
        ScriptableObject.putProperty(v7, "javaContext", Context.javaToJS(this, v7));
        ScriptableObject.putProperty(v7, "javaLoader", Context.javaToJS(clazz.getClassLoader(), v7)); // field MISSING
        registerApiInto(v7);
        ambPrev = enterAmbientScope(v7);
        ambEntered = true;
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
        logEvalFailure(v8, e, 2);
        logEvalError("evalJS", v8, e);
        String err = errPrefix + e.getMessage();
        Context.exit();
        return err;
    } catch (Throwable t) {
        // smali :catchall_0 — exit then rethrow (NOT a finally: normal paths exit explicitly)
        Context.exit();
        throw t;
    } finally {
        exitEvalLog();
        if (ambEntered) exitAmbientScope(ambPrev);
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
    Scriptable ambPrev = null;
    boolean ambEntered = false;
    enterEvalLog();
    try {
        ScriptableObject v4 = v3.initStandardObjects();
        org.mozilla.javascript.ImporterTopLevel.init(v3, v4, false);
        initRequire(v3, v4); // MISSING
        installJavaAdapterShim(v4);
        ScriptableObject.putProperty(v4, "input", Context.javaToJS(p2, v4));
        ScriptableObject.putProperty(v4, "_globalMap", Context.javaToJS(globalMap, v4)); // field MISSING
        ScriptableObject.putProperty(v4, "rc4", Context.javaToJS(rc4, v4)); // field MISSING
        ScriptableObject.putProperty(v4, "_base64", Context.javaToJS(myBase64, v4)); // field MISSING
        ScriptableObject.putProperty(v4, "javaContext", Context.javaToJS(this, v4));
        ScriptableObject.putProperty(v4, "javaLoader", Context.javaToJS(clazz.getClassLoader(), v4)); // field MISSING
        registerApiInto(v4);
        ambPrev = enterAmbientScope(v4);
        ambEntered = true;
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
        logEvalFailure(v5, e, 2);
        logEvalError("evalJSForObj", v5, e);
        Context.exit();
        return null;
    } catch (Throwable t) {
        // smali :catchall_0 — exit then rethrow (NOT a finally: normal paths exit explicitly)
        Context.exit();
        throw t;
    } finally {
        exitEvalLog();
        if (ambEntered) exitAmbientScope(ambPrev);
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
    Scriptable ambPrev = null;
    boolean ambEntered = false;
    enterEvalLog();
    try {
        ScriptableObject scope = cx.initStandardObjects();
        org.mozilla.javascript.ImporterTopLevel.init(cx, scope, false);
        initRequire(cx, scope);
        installJavaAdapterShim(scope);
        ScriptableObject.putProperty(scope, "_globalMap",
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
        ambPrev = enterAmbientScope(scope);
        ambEntered = true;
        if (!script.contains("eval(getJsPlugin());\n")) {
            cx.evaluateString(scope, allFunctions + "\neval(getJsPlugin());\n",
                    clazz.getSimpleName(), 1, null);
        } else {
            cx.evaluateString(scope, allFunctions, clazz.getSimpleName(), 1, null);
        }
        cx.evaluateString(scope, replaced, clazz.getSimpleName(), 1, null);
    } catch (Exception e) {
        logEvalFailure(replaced, e, throwOnError ? 1 : 2);
        logEvalError("runScript", replaced, e);
        if (!throwOnError) {
            setError("JS编译出错：" + e.getMessage(), key, new HashMap<String, Object>());
        } else {
            throw e;
        }
    } finally {
        exitEvalLog();
        if (ambEntered) exitAmbientScope(ambPrev);
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
                    // :cond_11 — 照官抛错（JE require0 :cond_11，throw Exception("获取远程依赖失败：" + url)）。
                    // B1 软失败由消费方承担：require 入口自带 catch 收口（registerApi require 处），本体不软化。
                    throw new Exception("获取远程依赖失败：" + url);
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
                // :cond_c → :cond_e — 照官抛错（JE require0 :cond_e，文案与 :cond_11 同）。
                throw new Exception("获取远程依赖失败：" + url);
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
    // try/catch、也穿透宿主 catch(Exception)，短剧代理真机实证回 200 空包。海阔规则
    // 常用 new JavaAdapter(接口, js对象)（如 Enumeration 懒解密流）——接口情形一律走
    // java.lang.reflect.Proxy（ART 支持），回调时在调用线程进 Context 执行 JS 函数；
    // 非接口情形回落原生实现，保持原行为。
    static final class HkJavaAdapterFn extends BaseFunction {
        private final Object original;
        /** 环24-11 簇二：所属运行时的 ambient 登记表（installJavaAdapterShim
         * 在实例方法内构造本垫片时带入），透传 JsProxyHandler 在回调触发时
         * 登记定义 scope；无带入时为 null、Handler 维持旧形不登记。 */
        private final ThreadLocal<Scriptable> ambientTl;

        HkJavaAdapterFn(Object original) {
            this(original, null);
        }

        HkJavaAdapterFn(Object original, ThreadLocal<Scriptable> ambientTl) {
            this.original = original;
            this.ambientTl = ambientTl;
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
                                new Class<?>[]{cls}, new JsProxyHandler((Scriptable) args[1], ambientTl));
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
        /** 环24-11 簇二：所属运行时的 ambient 登记表（可为 null，见
         * HkJavaAdapterFn.ambientTl 注）。invoke 可由任意 Java 回调线程在
         * 无求值语境下调入，旧形 ambient 缺位、fetch 取页回落最新鲜 root，
         * 与回调 JS 可见 MY_RULE（定义 scope 的值）分叉（账 §1.3(c)）。 */
        private final ThreadLocal<Scriptable> ambientTl;

        JsProxyHandler(Scriptable jsObj) {
            this(jsObj, null);
        }

        JsProxyHandler(Scriptable jsObj, ThreadLocal<Scriptable> ambientTl) {
            this.jsObj = jsObj;
            this.ambientTl = ambientTl;
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
                // 环24-11 簇二：触发调用时先以函数定义 scope（fnScope）登记
                // ambient 再调、finally 栈式恢复（与 enterAmbientScope/
                // exitAmbientScope 同纪律；本类为静态嵌套类、经构造带入的
                // ThreadLocal 本人操作）。登记只罩 f.call 本体，返回值收形
                // 在恢复后进行，与改前同。
                Scriptable prevAmb = null;
                boolean ambSet = false;
                if (ambientTl != null && fnScope != null) {
                    prevAmb = ambientTl.get();
                    ambientTl.set(fnScope);
                    ambSet = true;
                }
                Object r;
                try {
                    r = f.call(cx, fnScope, jsObj, jsArgs);
                } finally {
                    if (ambSet) {
                        if (prevAmb != null) ambientTl.set(prevAmb);
                        else ambientTl.remove();
                    }
                }
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
        // 环24-11 簇二：把本运行时的 ambient 登记表带入垫片，JsProxyHandler
        // 触发时据此登记定义 scope（见 JsProxyHandler.ambientTl 注）。
        ScriptableObject.putProperty(scope, "JavaAdapter", new HkJavaAdapterFn(orig, ambientEvalScope));
    }

    /**
     * 8.83 JSEngine.initRequire(Context p1, Scriptable p2) — smali 5400-5484 (.line 540-558)。
     * CommonJS requirejs 桥：StrongCachingModuleScriptProvider（UrlModuleSourceProvider 指向
     * getDirectory() 即 <rootDir>/libs 的 file:// URI）双重检查锁单例；
     * new MyRequire(cx, scope, provider, null, null, false) 挂到 scope.requirejs。
     * 8.83 锁的是 JSEngine.class，本移植锁 HkJsRuntime.class（移植类）。
     */
    private void initRequire(Context cx, Scriptable scope) {
        // 环19 对官：provider 改官类级单例＋单基址——UrlModuleSourceProvider
        // 只含 getDirectory()（全局 libs）一个基址，双重检查锁只建一次、全
        // 运行时共用。旧自造适配（全局 libs＋当前规则 data/libs 双基址、
        // provider 按规则标题缓存）随本批退役：环13 后包内库按官落全局
        // libs，单基址即命中，第二基址的消费面消灭。
        if (cachingModuleScriptProvider == null) {
            synchronized (HkJsRuntime.class) {
                if (cachingModuleScriptProvider == null) {
                    UrlModuleSourceProvider sourceProvider = new UrlModuleSourceProvider(
                            java.util.Collections.singletonList(getDirectory()), null);
                    cachingModuleScriptProvider = new StrongCachingModuleScriptProvider(sourceProvider);
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
            // 环13：旧自造「全局 miss 回退规则 libs」层已摘——包内 libs 按官
            // 落全局 libs/ 后此路直查即命中，miss 即官形回 ""。
            return "";
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
        // 8.83: PageParser.parsePageRule(rule, url)；宿主已移植为 handlePageRequest（同文件）。
        // 环24-11 簇一：第二参 options 位即官 ruleKey——显式转 HkRule 作取页
        // 基底透传（delegate 捕获的 ruleKey、直名路 args[2]、require0 的
        // ruleObj 都经此位入），不在场/不可转才回落 ambient（handlePageRequest
        // 内部既有路）。
        try {
            return handlePageRequest(url, ruleFromExplicitKey(options));
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
 * getTitle() + getDisplayName(). The target's rule models are HkRule and
 * HkSearchEngine, both carrying getTitle() + getDisplayName() (HkRule.java:602 /
 * HkSearchEngine.java:47); the dispatch covers both. (2026-11-11 代码库批 F1:
 * earlier port never extracted displayName, so the _displayName branch never
 * fired and plugin.js's title rewrite stayed dead — see
 * worknotes/hiker-codelib-ledger-20261111.md.)
 */
public static String getMyRule(Object rule) {
    String title = "";
    String displayName = null;
    if (rule instanceof HkRule) {
        title = ((HkRule) rule).getTitle();
        displayName = ((HkRule) rule).getDisplayName();
    } else if (rule instanceof HkSearchEngine) {
        title = ((HkSearchEngine) rule).getTitle();
        displayName = ((HkSearchEngine) rule).getDisplayName();
    }
    StringBuilder sb = new StringBuilder("let my_rule = '");
    // G40：HkRule 走 myRuleJson（官 getSimpleFilter 过滤），与其余注入口径一致。
    // 环20：SearchEngine 支同走 simple 滤镜（官 getMyRule 对三型同用
    // getSimpleFilter，displayName/order 等名同剔；旧形未过滤直出）。
    sb.append(escapeJsString(rule instanceof HkRule ? myRuleJson((HkRule) rule)
            : rule instanceof HkSearchEngine ? GSON.toJson(filteredRuleTree(rule))
            : GSON.toJson(rule)));
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
    ua.put("mobileUa", getMobileUa());
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
            headers.put("User-Agent", isPc ? UA_PC : getMobileUa());
        }
        return map;
    }
    Map<String, Object> map = new HashMap<>();
    Map<String, Object> headers = new HashMap<>();
    // MISSING: UA_PC / UA_MOBILE (same as above)
    headers.put("User-Agent", isPc ? UA_PC : getMobileUa());
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
            // B-4 同型池支捕获点：提交前在调用线程捕获当发登记 scope，随任务
            // 带入池线程（任务函数内 fetch('hiker://page/…') 同经 myRuleFromScope）；
            // 捕获不到（null）时不登记、回落主 scope 与改前同。
            final Scriptable capturedScope = ambientEvalScope.get();
            pool.execute(() -> {
                // 8.83: jsExecutorService.execute 先 HttpHelper.addTagForThread(tag)
                HkHttpHelper.addTagForThread(beTag);
                // 8.83: latch.getCount()>0 且 counter < successLimit 才执行，否则直接 countDown
                if (latch.getCount() <= 0 || counter.get() >= successLimit) {
                    latch.countDown();
                    return;
                }
                Context cx = Context.enter();
                Scriptable ambPrev = null;
                boolean ambEntered = false;
                try {
                    // B-4 补改登记点（复核账 B-4 不过 A）：登记移进 try 内、排在
                    // Context.enter() 之后紧贴任务体——enter 抛错时 try 未进入、
                    // 登记尚未发生，无需恢复；登记覆盖任务函数与 listener 回调两处调用。
                    ambPrev = enterAmbientScope(capturedScope);
                    ambEntered = true;
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
                    // B-4 补改恢复点（复核账 B-4 不过 B）：恢复排在 Context.exit()
                    // 之前先行——exit 抛错不影响恢复已完成；ambEntered 守卫与转录
                    // 路同形（登记未成不恢复）。
                    if (ambEntered) exitAmbientScope(ambPrev);
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
// 8.83 RequireUtils 登记层：2026-11-11 代码库批 F3 起，真类已按官包名补回
// （com.example.hikerview.ui.rules.service.require.RequireUtils，模型
// RequireItem/RequireDescription 同包名补齐），本节三法改委托真类——宿主
// require0 路与 JS 直调路共用同一实现、同一份 require.json（存储根随真类
// 归官位 <root>/rules/files/；旧移植根 hkRootDir()+"/files" 因守卫抄反从未
// 真写过盘，无存量可迁）。账：worknotes/hiker-codelib-ledger-20261111.md。
// ---------------------------------------------------------------------------

// 8.83: getRequireVersion(String, int)int [RequireUtils.smali 866-913]
private static int getRequireVersion(String path, int version) {
    return com.example.hikerview.ui.rules.service.require.RequireUtils
            .getRequireVersion(path, version);
}

// 8.83: updateDescription(String, String, int)void [RequireUtils.smali 946-974]
private static void updateDescription(String path, String url, int version) {
    com.example.hikerview.ui.rules.service.require.RequireUtils
            .updateDescription(path, url, version);
}

// ---------------------------------------------------------------------------
// 8.83: generateRequireMap(String, String, String, String)void
// [RequireUtils.smali 431-461]  public static.
// 形参约定沿 fc 移植旧口径 (dirName=规则标题, url=模块名, proxy=代理后 URL,
// file=落盘路径)，与官 require0 调用点寄存器实义一致（JSEngine:8468 起：
// v2=p7 标题、v3=tryGetString(p2) 模块名、v5=proxyRequire 代理后 URL、
// v11=落盘路径）。旧移植守卫抄反（官「标题与键皆非空才投递」，旧形反作
// 「标题为空才投递」，标题恒非空 ⇒ 登记永不触发）；真类内已按官改正，
// 本委托不再带守卫。
// ---------------------------------------------------------------------------
private static void generateRequireMap(String dirName, String url, String proxy, String file) {
    com.example.hikerview.ui.rules.service.require.RequireUtils
            .generateRequireMap(dirName, url, proxy, file);
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
            // RC4 分叉收官：8.83 RC4.enc（RC4.smali:242-249）输出
            // char 数组按入参字符数×2 预分配——多字节 charset 下
            // 密文字节数大于字符数、写溢出抛 AIOOBE 被官 catch
            // 收成 ""。咱家旧形按密文字节数×2 分配、非 ASCII 能
            // 正常出串，与官分叉；照官收回字符数口径。
            char[] out = new char[data.length() * 2];
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
// 委托等价（第十阶段 D#7 口径订正）：8.83 com.example.hikerview.utils.encrypt.MyBase64
// 本体是 AOSP android.util.Base64 的逐字搬运（同 flag 常量、同标准字母表与 URL_SAFE
// 变体、同 76 字符折行、同 padding 规则、同 "bad base-64" 异常），本类不复刻其
// Encoder/Decoder/Coder 状态机、直接委托 android.util.Base64——是委托关系而非
// 搬运关系，结果逐位等价的依据是官本体与 AOSP 的同源性。See frag_crypto_notes.md.

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

    /** 8.83 MyBase64.decodeToString 原形（smali 177-225）：解码后经
     * fastjson 把 byte[] 序列化成带引号的标准 base64 串、再去掉
     * 引号——不是解成文本。本项目无 fastjson，等价式＝解码后以
     * NO_WRAP 标准 base64 重编码（带 padding、无换行）。此前本
     * 类唯独缺这两个 overload，图片 @js 里调到即抛错被吞、按
     * 未解密原流出图（08 销账表 4-2）。 */
    public String decodeToString(String str, int flags) {
        return decodeToString(str.getBytes(), flags);
    }

    public String decodeToString(byte[] input, int flags) {
        // android.util.Base64.decode 永不回 null（坏输入抛 IllegalArgumentException），
        // 旧形 decoded==null 回 null 是死支，摘除（第十阶段 D#7）
        byte[] decoded = decode(input, flags);
        return android.util.Base64.encodeToString(decoded, android.util.Base64.NO_WRAP);
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

    /**
     * 环 25-7 回退支（官 justTestSign catch 支，JE:18729-18946 亲读）：临时 secp256k1
     * 键对自签——JCA 的 SHA256withECDSA 本身即「SHA-256 后 ECDSA、DER 输出」，
     * 与 starkbank Ecdsa.sign 同形；结果＝DER hex 尾 128 字符＋"01##"＋公钥串
     * （"04"＋X‖Y 坐标各补足 32 字节的 hex，starkbank PublicKey.toByteString 形）。
     * 官回退支无 catch：异常以 RuntimeException 包裹原样穿出（经 putFunction 咽喉
     * 成 JS 异常）。主签别名差（咱 XC_／官 MY_）JS 不可见，记名不动。
     */
    private static String ecdsaSignFallback(String message) {
        try {
            java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("EC");
            kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256k1"));
            java.security.KeyPair kp = kpg.generateKeyPair();
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withECDSA");
            sig.initSign(kp.getPrivate());
            sig.update(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String sigHex = hkBytesToHexString(sig.sign());
            String sigTail = sigHex.length() > 128 ? sigHex.substring(sigHex.length() - 128) : sigHex;
            java.security.interfaces.ECPublicKey pub = (java.security.interfaces.ECPublicKey) kp.getPublic();
            String pubHex = "04" + hkBigInt32Hex(pub.getW().getAffineX()) + hkBigInt32Hex(pub.getW().getAffineY());
            return sigTail + "01##" + pubHex;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** EC 坐标补足 32 字节定长 hex（starkbank toByteString 的坐标段形）。 */
    private static String hkBigInt32Hex(java.math.BigInteger v) {
        byte[] b = v.toByteArray();
        byte[] out = new byte[32];
        int len = Math.min(b.length, 32);
        System.arraycopy(b, b.length - len, out, 32 - len, len);
        return hkBytesToHexString(out);
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
