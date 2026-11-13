package com.fongmi.android.tv.ui.hk;

import com.fongmi.android.tv.api.hk.HkImage;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对应 8.83 JSEngine 进程单例语义（审计 A1）：
 * 运行时按规则名缓存、进程内永不销毁；init 每实例只跑一次。
 * 旧实现每开一页 new+destroy 导致每次全量重初始化，此管理器取代之。
 * 实例虽按规则名缓存，规则绑定每次取用都按当前页规则重绑
 * （{@link HkJsRuntime#bindRule}）——8.83 引擎无规则字段、上下文随页现拼，
 * 缓存的只是引擎实例，不是规则对象（块一 W3 口径）。
 */
public final class HkEngineManager {

    private static final Map<String, HkJsRuntime> RUNTIMES = new ConcurrentHashMap<>();
    private static final Set<HkJsRuntime> INITIALIZED = ConcurrentHashMap.newKeySet();
    /** 每个常驻运行时创建时规则文件的 mtime；与现文件不一致 = 规则已被新导入覆盖。 */
    private static final Map<String, Long> RUNTIME_FILE_MTIMES = new ConcurrentHashMap<>();
    private static final ThreadLocal<HkRule> CURRENT_RULE = new ThreadLocal<>();

    private HkEngineManager() {
    }

    /** 规则隔离键：原版按规则名（变量以 "规则名@key" 隔离）；名为空时退化用 url。 */
    public static String ruleKey(HkRule rule) {
        if (rule == null) return "";
        String title = rule.getTitle();
        if (title != null && !title.isEmpty()) return title;
        String url = rule.getUrl();
        return url == null ? "" : url;
    }

    /** 取（或建）规则的常驻运行时；不 init、不销毁。 */
    public static HkJsRuntime runtimeFor(HkRule rule) {
        String key = ruleKey(rule);
        // 规则文件被新导入覆盖后（mtime 变了），旧常驻运行时必须作废：
        // init/preRule 每实例只跑一次，不作废的话新版 preRule 永不重跑，
        // 动态域名/cookie 还停在旧版，看着就像导入没生效。文件不存在（mtime=-1）不判。
        long mtime = -1;
        try {
            mtime = HkRuleManager.get().ruleModified(rule == null ? null : rule.getTitle());
        } catch (Throwable ignored) {
        }
        HkJsRuntime rt = RUNTIMES.get(key);
        if (rt != null && mtime != -1) {
            Long seen = RUNTIME_FILE_MTIMES.get(key);
            if (seen != null && seen.longValue() != mtime && RUNTIMES.remove(key, rt)) {
                INITIALIZED.remove(rt);
                RUNTIME_FILE_MTIMES.remove(key);
                rt = null;
            }
        }
        if (rt == null) {
            HkJsRuntime created = new HkJsRuntime(rule);
            HkJsRuntime prev = RUNTIMES.putIfAbsent(key, created);
            rt = prev == null ? created : prev;
            if (prev == null) RUNTIME_FILE_MTIMES.put(key, mtime);
        }
        // 块一 W3 接缝：按当前页规则重绑，求值上下文（MY_RULE 等）随页现拼，
        // 不许停留在实例创建时按 title 缓存的旧规则对象（子页与首页同 title）。
        rt.bindRule(rule);
        CURRENT_RULE.set(rule);
        // 图片 @js= 求值靠 HkImage 找当前规则运行时（8.83 是引擎单例天然可用）；
        // 新模块此前从未登记，抓取器拿不到运行时只能把加密原流交回，图全部解码失败
        HkImage.setCurrentRuntime(rt);
        // getInstance() 领用登记只认胜出者：putIfAbsent 竞败弃子不许抢（复核订正）。
        HkJsRuntime.setActiveInstance(rt);
        return rt;
    }

    /** W4：只查不建——跨规则变量共享（getMyVar/putMyVar 第三参）读别规则变量表时用，
     * 不在就当没有，不触发创建（避免副作用）。 */
    public static HkJsRuntime runtimeIfPresent(HkRule rule) {
        return RUNTIMES.get(ruleKey(rule));
    }

    /** init 每实例只执行一次（原版 JSEngine 单例 init 一次）；失败不标记，下次重试。 */
    public static void ensureInit(HkJsRuntime rt) throws Exception {
        if (INITIALIZED.contains(rt)) return;
        synchronized (rt) {
            if (INITIALIZED.contains(rt)) return;
            rt.init();
            INITIALIZED.add(rt);
        }
    }

    /** 供无 rule 参数的接缝（如 HkLazyRuleParser.JsRuntime.evalJSForObj）定位当前运行时。 */
    public static HkRule currentRule() {
        return CURRENT_RULE.get();
    }

    public static void setCurrentRule(HkRule rule) {
        CURRENT_RULE.set(rule);
    }
}
