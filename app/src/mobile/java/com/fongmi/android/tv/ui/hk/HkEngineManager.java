package com.fongmi.android.tv.ui.hk;

import com.fongmi.android.tv.api.hk.HkImage;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 8.83 JSEngine 进程单例（一台桥）——2026-11-14 官形收口（账
 * hk-engine-singleton-land）：此前按规则名缓存整台 HkJsRuntime 常驻
 * （每台一份 executor 线程＋Context＋全装配 scope 图，开过的规则数＝
 * 常驻台数、无上限），与官「一台桥、求值现建现弃、规则存量只是 Java
 * 侧分键字符串」正相反。本管理器现只持一台桥：
 * <ul>
 *   <li>桥内求值纪律（每发鲜 scope、发后释放、kv 按标题切换）在
 *   HkJsRuntime 的 sharedBridge 标记下生效；</li>
 *   <li>规则绑定每次取用按当前页规则重绑（{@link HkJsRuntime#bindRule}）
 *   ——引擎无规则字段、上下文随页现拼，桥不缓存规则对象；</li>
 *   <li>规则文件被新导入覆盖（mtime 变）＝官规则管理面的「改」：照官
 *   两口清账（clearVarByRule＋clearModuleCache），新版 preRule/库代码
 *   不被旧账挡住；删规则的两口在 HkRuleManager.delete 直调
 *   HkJsRuntime.onRuleDeleted。</li>
 * </ul>
 */
public final class HkEngineManager {

    /** 进程唯一桥（懒建，双检锁）。 */
    private static volatile HkJsRuntime BRIDGE;
    /** 桥 init 只跑一次（官单例 init 一次）；失败不标记，下次重试。 */
    private static volatile boolean BRIDGE_INITIALIZED;
    /** 每规则文件上次见到的 mtime；与现文件不一致 = 规则已被新导入覆盖。 */
    private static final Map<String, Long> RULE_FILE_MTIMES = new ConcurrentHashMap<>();
    private static final ThreadLocal<HkRule> CURRENT_RULE = new ThreadLocal<>();
    /**
     * 每规则的活页面面数（掉帧账 S2 落修）：规则页宿主（HkListActivity /
     * HkPageActivity）onCreate 记一面、onDestroy 销一面；归零＝该规则再无
     * 页面在场，此时才清它名下的 JS 定时器/任务（HkJsRuntime.clearTimersForRule）。
     * 播放页不算面：进播放时规则页还压在栈下（面数 ≥1），播中要用的刷新
     * 定时器不会被误清；用户退完播放再退出规则页，归零清账。键口径 ruleKey。
     */
    private static final Map<String, Integer> RULE_SURFACES = new ConcurrentHashMap<>();

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

    /** 取进程唯一桥并按当前页规则重绑；不 init（init 走 ensureInit 一次口）。 */
    public static HkJsRuntime runtimeFor(HkRule rule) {
        HkJsRuntime rt = BRIDGE;
        if (rt == null) {
            synchronized (HkEngineManager.class) {
                rt = BRIDGE;
                if (rt == null) {
                    rt = new HkJsRuntime(rule);
                    rt.setSharedBridge(true);
                    // 删规则清账口（HkRuleManager.delete → onRuleDeleted）靠此登记找桥。
                    HkJsRuntime.registerSharedBridge(rt);
                    BRIDGE = rt;
                }
            }
        }
        // 规则文件被新导入覆盖后（mtime 变了），照官删改两口清该规则的账：
        // 不清的话新版 require 库还命中旧模块缓存、旧变量还躺在 varMap 里，
        // 看着就像导入没生效。文件不存在（mtime=-1）不判。
        String key = ruleKey(rule);
        long mtime = -1;
        try {
            mtime = HkRuleManager.get().ruleModified(rule == null ? null : rule.getTitle());
        } catch (Throwable ignored) {
        }
        if (mtime != -1) {
            Long seen = RULE_FILE_MTIMES.get(key);
            if (seen == null) {
                RULE_FILE_MTIMES.put(key, mtime);
            } else if (seen.longValue() != mtime) {
                RULE_FILE_MTIMES.put(key, mtime);
                rt.clearVarByRule(key);
                rt.clearModuleCache();
            }
        }
        // W3 接缝：按当前页规则重绑，求值上下文（MY_RULE 等）随页现拼，
        // 不许停留在桥创建时按 title 缓存的旧规则对象（子页与首页同 title）。
        rt.bindRule(rule);
        CURRENT_RULE.set(rule);
        // 图片 @js= 求值靠 HkImage 找当前规则运行时（8.83 是引擎单例天然可用）。
        HkImage.setCurrentRuntime(rt);
        // getInstance() 领用登记：桥恒为最近领用者。
        HkJsRuntime.setActiveInstance(rt);
        return rt;
    }

    /** 只查不建：桥已建则回桥，未建回 null（旧形是查该规则的常驻台；
     * HkPageActivity 网页桥接线用此口优先拿现成运行时、不触发建台）。 */
    public static HkJsRuntime runtimeIfPresent(HkRule rule) {
        return BRIDGE;
    }

    /** 桥 init 每进程只执行一次（原版 JSEngine 单例 init 一次）；失败不标记，下次重试。 */
    public static void ensureInit(HkJsRuntime rt) throws Exception {
        if (rt == null || BRIDGE_INITIALIZED) return;
        synchronized (HkEngineManager.class) {
            if (BRIDGE_INITIALIZED) return;
            rt.init();
            BRIDGE_INITIALIZED = true;
        }
    }

    /** 规则页宿主创建时记一面（S2 落修）。rule 为空/无键不记。 */
    public static void surfaceOpened(HkRule rule) {
        String key = ruleKey(rule);
        if (key.isEmpty()) return;
        RULE_SURFACES.merge(key, 1, Integer::sum);
    }

    /**
     * 规则页宿主销毁时销一面（S2 落修）：归零即该规则再无页面在场，清其
     * 名下定时器/任务。计数下限钳 0（配对外的多销不许把别次打开的面销穿）。
     * 桥未建时无定时器可清、只销账。
     */
    public static void surfaceClosed(HkRule rule) {
        String key = ruleKey(rule);
        if (key.isEmpty()) return;
        boolean zero = RULE_SURFACES.compute(key, (k, v) -> {
            if (v == null || v <= 1) return null;
            return v - 1;
        }) == null;
        if (!zero) return;
        HkJsRuntime rt = BRIDGE;
        if (rt == null) return;
        try {
            rt.clearTimersForRule(key);
        } catch (Throwable ignored) {
        }
    }

    /** 供无 rule 参数的接缝（如 HkLazyRuleParser.JsRuntime.evalJSForObj）定位当前规则。 */
    public static HkRule currentRule() {
        return CURRENT_RULE.get();
    }

    public static void setCurrentRule(HkRule rule) {
        CURRENT_RULE.set(rule);
    }
}
