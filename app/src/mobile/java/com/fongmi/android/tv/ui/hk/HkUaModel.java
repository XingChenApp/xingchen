package com.fongmi.android.tv.ui.hk;

import android.content.Context;
import android.content.SharedPreferences;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.HkUaEnum;

import java.util.HashMap;
import java.util.Map;

/**
 * 照 8.83 com.example.hikerview.ui.browser.model.UAModel 的按域 UA 存档面转写
 * （环26 落地面：setWebUa 的按域存档委托＋checkUa 的 UaHook 默认实现消费）。
 * 官载体＝LitePal UaRuleDO（dom/ua 两列）＋进程内 uaMap 缓存；咱家载体对位
 * SharedPreferences「hk_ua_model」（键＝dom、值＝ua），缓存形照官保留。
 * <p>
 * 逐支照官（UAModel.smali：updateUa :1187／getAdjustUaNow :447／
 * getAdjustUaItem :194）：
 * - updateUa(url, ua)：url 空直接回；dom＝getDom(url)，uaMap 先 put；
 *   存量行在位时 ua 空→删行、非空→改存；无存量行时 ua 非空才新建。
 * - getAdjustUaNow(url)：url 空回 null；uaMap 命中且值非 null 直接回
 *   （含负缓存 ""）；未命中查存量，命中回填缓存并回值，无则缓存 "" 回 null。
 * - getAdjustUa(url, 第二参)：照 getAdjustUaItem 三落点查法——先 exact
 *   （getAdjustUaNow 非空即回）；再段数&gt;3 时查末 3 段；再段数≥3 时查
 *   末 2 段。三落点全空才进尾段三支（见下）。旧形逐级全后缀 walk 与官
 *   不符（存 b.c.d.e 时查 a.b.c.d.e 会误命中 b.c.d.e，官回 null），
 *   C1 复核订正已摘。
 * - 尾段三支（getAdjustUaItem cond_5 起，逐支照官）：
 *   ①url 含 newPlanHome.html → 回 UAEnum.MOBILE 串，无 useUa 前置；
 *   ②useUa 为空且第二参非空、末 2 段候选域恰为 baidu.com、url 含 /s?
 *   → 回第二参＋" XiaoMi/MiuiBrowser/17.8.70926 swan-mibrowser"；
 *   ③useUa 为空且第二参非空、末 2 段候选域含 google → 回第二参去 "; wv"。
 *   第二参＝官调用方 WebViewHelper$5.onPageStarted 传的 systemUA
 *   （:827，恒非空）；useUa＝官 UAModel.useUa 静态位（用户自设 UA），
 *   咱家无其设置面恒 ""——故②③前置（useUa 为空）在本链成立、两支是
 *   活支（旧注记作「useUa 非空恒不成立」系读反，C1 复核订正）。
 * clearAdjustUa／管理弹窗面不在本面，维持记名。
 */
public final class HkUaModel {

    private static final String PREFS = "hk_ua_model";
    private static final Map<String, String> UA_MAP = new HashMap<>();

    /** 官 UAModel.useUa 静态位（初值 ""，setUseUa 写、getUseUa 读，UAModel.smali:29/:895/:599）。 */
    private static String useUa = "";

    public static String getUseUa() {
        return useUa;
    }

    public static void setUseUa(String ua) {
        useUa = ua;
    }

    private HkUaModel() {
    }

    private static SharedPreferences sp() {
        try {
            Context c = App.get();
            return c == null ? null : c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 官 UAModel.updateUa 转写。 */
    public static void updateUa(String url, String ua) {
        if (url == null || url.isEmpty()) return;
        String dom = HkStringUtil.getDom(url);
        UA_MAP.put(dom, ua);
        SharedPreferences sp = sp();
        if (sp == null) return;
        try {
            if (sp.contains(dom)) {
                if (ua == null || ua.isEmpty()) {
                    sp.edit().remove(dom).apply();
                } else {
                    sp.edit().putString(dom, ua).apply();
                }
            } else if (ua != null && !ua.isEmpty()) {
                sp.edit().putString(dom, ua).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    /** 官 UAModel.getAdjustUaNow 转写（exact 域查，带负缓存）。 */
    private static String getAdjustUaNow(String url) {
        if (url == null || url.isEmpty()) return null;
        String dom = HkStringUtil.getDom(url);
        if (UA_MAP.containsKey(dom)) {
            String cached = UA_MAP.get(dom);
            if (cached != null) return cached;
        }
        SharedPreferences sp = sp();
        String stored = null;
        try {
            stored = sp == null ? null : sp.getString(dom, null);
        } catch (Throwable ignored) {
        }
        if (stored != null) {
            UA_MAP.put(dom, stored);
            return stored;
        }
        UA_MAP.put(dom, "");
        return null;
    }

    /** 官 UAModel.getAdjustUa(String) 转写（UAModel.smali:160：第二参恒传 null）。 */
    public static String getAdjustUa(String url) {
        return getAdjustUa(url, null);
    }

    /**
     * 官 UAModel.getAdjustUaItem 转写（只取 ua 串，AdjustUaItem 的 dom 咱家无消费）：
     * 三落点（exact／段数&gt;3 时末 3 段／段数≥3 时末 2 段）＋尾段三支，
     * 落点与支序逐项照 smali :194-430（见类注）。
     */
    public static String getAdjustUa(String url, String systemUa) {
        if (url == null || url.isEmpty()) return null;
        String now = getAdjustUaNow(url);
        String dom = HkStringUtil.getDom(url);
        if (HkStringUtil.isNotEmpty(now)) return now;
        if (dom == null) dom = "";
        String[] parts = dom.split("\\.");
        String cand;
        if (parts.length > 3) {
            cand = com.fongmi.android.tv.api.hk.HkParserKit.arrayToString(parts, parts.length - 3, ".");
        } else {
            cand = dom;
        }
        String ua = getAdjustUaNow(cand);
        if (HkStringUtil.isNotEmpty(ua)) return ua;
        if (parts.length >= 3) {
            cand = com.fongmi.android.tv.api.hk.HkParserKit.arrayToString(parts, parts.length - 2, ".");
        }
        ua = getAdjustUaNow(cand);
        if (HkStringUtil.isNotEmpty(ua)) return ua;
        // cond_5：newPlanHome 支无 useUa 前置。
        if (url.contains("newPlanHome.html")) {
            return HkUaEnum.MOBILE.getContent();
        }
        // cond_6：baidu 支（useUa 为空且第二参非空、候选域恰 baidu.com、url 含 /s?）。
        if (HkStringUtil.isEmpty(useUa) && HkStringUtil.isNotEmpty(systemUa)
                && "baidu.com".equals(cand) && url.contains("/s?")) {
            return systemUa + " XiaoMi/MiuiBrowser/17.8.70926 swan-mibrowser";
        }
        // cond_7：google 支（useUa 为空且第二参非空、候选域含 google）。
        if (HkStringUtil.isEmpty(useUa) && HkStringUtil.isNotEmpty(systemUa)
                && cand.contains("google")) {
            return systemUa.replace("; wv", "");
        }
        return null;
    }

    /** 离线夹具用：清进程缓存并复位 useUa（生产路径不调）。 */
    static void clearCacheForTest() {
        UA_MAP.clear();
        useUa = "";
    }
}
