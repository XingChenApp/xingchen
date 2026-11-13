package com.fongmi.android.tv.ui.hk;

import android.content.Context;
import android.content.SharedPreferences;

import android.util.Log;

import com.fongmi.android.tv.App;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 照 8.83 com.example.hikerview.model.BigTextDO 的表单存档面转写（环26 落地面：
 * FyBridge.saveFormInputItem 的委托与 onPageFinished 回填消费）。
 * 官载体＝LitePal BigTextDO 一行：key＝"form@"＋getUrlByHref(url)、value＝
 * 表单项 Map 的 JSON 串；咱家载体对位 SharedPreferences「hk_form_inputs」
 * （键＝同款 bucket 键、值＝同款 JSON 串）。
 * <p>
 * 逐支照官（BigTextDO.smali:1413 saveFormInputs／:517 getFormInputs／:1093
 * getUrlByHref）：
 * - getUrlByHref：先按 "?" 取首段；含 "/#/" 时整段保留、否则再按 "#" 取首段。
 * - saveFormInputs 的值闸：value 长 &gt;50、或全中文（isAllChinese：每字
 *   0x4E00–0x9FA5）且长 &gt;10 时不存——新桶直接回、已有桶则把该 key 摘掉
 *   （摘空删整桶）；不触闸才 put 后整桶写回。整体包 try、异常只记不抛。
 * - getFormInputs：无桶回空 Map；桶值解析失败回 null 与真空区分（处置订正
 *   2026-11-14，偏官一处：官 parse 支 catch 后回新建空表，与真空同形属假
 *   留痕同罪，故改失败信号）。
 * 回填注入脚本在 HkWebkitHolder.onPageFinished（照 WebViewHelper$5
 * lambda$onPageFinished$2 原文，含「条目数 &gt;1 才回填」的官门）。
 */
public final class HkFormInputStore {

    private static final String PREFS = "hk_form_inputs";

    private HkFormInputStore() {
    }

    /** 官 BigTextDO.getUrlByHref 转写。 */
    public static String getUrlByHref(String url) {
        if (url == null) return null;
        String s = url.split("\\?")[0];
        if (s.contains("/#/")) return s;
        return s.split("#")[0];
    }

    private static SharedPreferences sp() {
        try {
            Context c = App.get();
            return c == null ? null : c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 官 StringUtil.isAllChinese 转写（null 按官形：charAt 不触发、恒 true 的等价——空串亦 true）。 */
    static boolean isAllChinese(String value) {
        if (value == null) return true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x4E00 || c > 0x9FA5) return false;
        }
        return true;
    }

    /** 官 saveFormInputs 的值闸：长 &gt;50、或全中文且长 &gt;10 时判拦。 */
    static boolean isValueBlocked(String value) {
        if (value == null) return false;
        return value.length() > 50 || (isAllChinese(value) && value.length() > 10);
    }

    /**
     * 纯核（离线夹具与 SP 面共用）：store＝桶键→桶内表单项 Map，就地改，
     * 返回是否发生了需要落盘的变化。桶键、值闸、摘空删桶全在此。
     */
    static boolean saveInto(Map<String, Map<String, String>> store, String url, String key, String value) {
        String bucketKey = "form@" + getUrlByHref(url);
        Map<String, String> bucket = store.get(bucketKey);
        if (bucket == null) {
            if (isValueBlocked(value)) return false;
            bucket = new HashMap<>();
            bucket.put(key, value);
            store.put(bucketKey, bucket);
            return true;
        }
        if (isValueBlocked(value)) {
            if (bucket.remove(key) == null) return false;
            if (bucket.isEmpty()) store.remove(bucketKey);
            return true;
        }
        bucket.put(key, value);
        return true;
    }

    /**
     * 官 BigTextDO.saveFormInputs 转写（SP 载体对位）。
     * 处置订正（2026-11-14）：改回 boolean 报成败并写后回读校验——此前失败
     * 只记日志，用户填过的内容静默没存上、调用方无从知道。官形无变化
     * （值闸拦下/空串桶不动）回 true；存档不可用、已有桶解析失败、写后
     * 回读不一致、异常回 false。
     */
    public static boolean saveFormInputs(String url, String key, String value) {
        try {
            SharedPreferences sp = sp();
            if (sp == null) {
                Log.e("HkFormInputStore", "表单输入保存失败（存档不可用）");
                return false;
            }
            String bucketKey = "form@" + getUrlByHref(url);
            String raw = sp.getString(bucketKey, null);
            // 官 cond_6：已有行 value 为空串时整支不动。
            if (raw != null && raw.isEmpty()) return true;
            Map<String, Map<String, String>> store = new HashMap<>();
            if (raw != null) {
                Map<String, String> bucket = parseBucket(raw);
                if (bucket == null) {
                    // 已有桶解析失败＝盘上可能还有用户填过的数据：中止本次保存、
                    // 原桶原样保留，不许拿只含新键的桶覆盖掉（批 fix349）。
                    Log.e("HkFormInputStore", "表单存档解析失败，已保留原桶并中止保存：" + bucketKey);
                    return false;
                }
                store.put(bucketKey, bucket);
            }
            if (!saveInto(store, url, key, value)) return true;
            Map<String, String> bucket = store.get(bucketKey);
            if (bucket == null) {
                sp.edit().remove(bucketKey).apply();
                if (sp.getString(bucketKey, null) != null) {
                    Log.e("HkFormInputStore", "表单输入保存回读不一致（删桶未生效）：" + bucketKey);
                    return false;
                }
            } else {
                String json = new JSONObject(bucket).toString();
                sp.edit().putString(bucketKey, json).apply();
                if (!json.equals(sp.getString(bucketKey, null))) {
                    Log.e("HkFormInputStore", "表单输入保存回读不一致：" + bucketKey);
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            Log.e("HkFormInputStore", "表单输入保存失败", t);
            return false;
        }
    }

    /**
     * 官 BigTextDO.getFormInputs 转写：无桶回空 Map。
     * 处置订正（2026-11-14）：读取/解析失败改回 null 与真空区分——此前失败
     * 回空 Map 与从没填过同形，已存内容静默读不出来。既有消费方
     * HkWebkitHolder.fillFormInputs 已判 null（不回填），终态安全。
     */
    public static Map<String, String> getFormInputs(String url) {
        Map<String, String> out = new HashMap<>();
        try {
            SharedPreferences sp = sp();
            if (sp == null) return null;
            String raw = sp.getString("form@" + getUrlByHref(url), null);
            if (raw == null || raw.isEmpty()) return out;
            Map<String, String> bucket = parseBucket(raw);
            if (bucket == null) return null;
            out.putAll(bucket);
            return out;
        } catch (Throwable t) {
            Log.e("HkFormInputStore", "表单输入读取失败", t);
            return null;
        }
    }

    private static Map<String, String> parseBucket(String raw) {
        try {
            JSONObject obj = new JSONObject(raw);
            Map<String, String> map = new HashMap<>();
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                map.put(k, obj.optString(k, ""));
            }
            return map;
        } catch (Throwable t) {
            Log.e("HkFormInputStore", "表单存档桶解析失败", t);
            return null;
        }
    }
}
