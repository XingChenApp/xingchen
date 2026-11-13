package com.fongmi.android.tv.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * AI 服务商本地存储（设计文档 §2.1）。
 * SharedPreferences("xingchen")：
 * - ai_providers：服务商 JSON 数组
 * - ai_provider_default：全局默认服务商 id
 * - ai_provider_<feature>：各功能绑定的服务商 id（空=跟随默认）
 */
public class AiStore {

    private static final String PREFS = "xingchen";
    private static final String KEY_PROVIDERS = "ai_providers";
    private static final String KEY_DEFAULT = "ai_provider_default";
    private static final String KEY_FLOAT_ENABLED = "xingchen.ai_float_enabled";
    private static final String KEY_FLOAT_X = "xingchen.ai_float_x";
    private static final String KEY_FLOAT_Y = "xingchen.ai_float_y";

    public static final String FEATURE_SMART_SEARCH = "smart_search";
    public static final String FEATURE_SUBTITLE = "subtitle";
    public static final String FEATURE_PLOT_QA = "plot_qa";
    public static final String FEATURE_VOICE = "voice";
    public static final String FEATURE_STT = "stt";

    private static final Gson GSON = new Gson();

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<AiProvider> getProviders(Context context) {
        List<AiProvider> list = parseProviders(prefs(context).getString(KEY_PROVIDERS, null));
        return list == null ? new ArrayList<>() : list;
    }

    /**
     * 宽容解析（低-07）：整表 parse 失败时逐元素抢救，跳过坏元素保住好服务商；
     * 一个都救不回且原文非空 → 返回 null 表「存储已损坏」，写路径据此中止，
     * 不许拿空表回写把用户配置整表冲掉（此前解析失败回空表，增删一次全丢）。
     */
    private static List<AiProvider> parseProviders(String json) {
        if (TextUtils.isEmpty(json)) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<AiProvider>>() {}.getType();
            List<AiProvider> list = GSON.fromJson(json, type);
            if (list != null) return list;
        } catch (Exception ignored) {
        }
        try {
            com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(json);
            if (root == null || !root.isJsonArray()) return null;
            List<AiProvider> out = new ArrayList<>();
            for (com.google.gson.JsonElement el : root.getAsJsonArray()) {
                try {
                    AiProvider p = GSON.fromJson(el, AiProvider.class);
                    if (p != null) out.add(p);
                } catch (Exception ignored) {
                }
            }
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    public static void saveProviders(Context context, List<AiProvider> list) {
        prefs(context).edit().putString(KEY_PROVIDERS, GSON.toJson(list)).apply();
    }

    /** @return true=已保存；false=存储已损坏且救不回、未写入（低-05，调用方据此提示） */
    public static boolean addOrUpdate(Context context, AiProvider provider) {
        List<AiProvider> list = parseProviders(prefs(context).getString(KEY_PROVIDERS, null));
        if (list == null) return false; // 存储已损坏且救不回：中止写入，别拿空表覆盖原配置
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (TextUtils.equals(list.get(i).id, provider.id)) {
                provider.touch();
                list.set(i, provider);
                found = true;
                break;
            }
        }
        if (!found) list.add(provider);
        saveProviders(context, list);
        // 第一个添加的自动设为默认
        if (TextUtils.isEmpty(getDefaultId(context))) {
            setDefaultId(context, provider.id);
        }
        return true;
    }

    /** @return true=已删除；false=存储已损坏且救不回、未写入（低-05，调用方据此提示） */
    public static boolean delete(Context context, String id) {
        List<AiProvider> list = parseProviders(prefs(context).getString(KEY_PROVIDERS, null));
        if (list == null) return false; // 同 addOrUpdate：损坏且救不回时不回写，保住原存储
        Iterator<AiProvider> it = list.iterator();
        while (it.hasNext()) {
            if (TextUtils.equals(it.next().id, id)) {
                it.remove();
                break;
            }
        }
        saveProviders(context, list);
        if (TextUtils.equals(getDefaultId(context), id)) {
            setDefaultId(context, list.isEmpty() ? "" : list.get(0).id);
        }
        // 清理各功能绑定
        for (String f : new String[]{FEATURE_SMART_SEARCH, FEATURE_SUBTITLE, FEATURE_PLOT_QA, FEATURE_VOICE, FEATURE_STT}) {
            if (TextUtils.equals(getFeatureProviderId(context, f), id)) {
                setFeatureProviderId(context, f, "");
            }
        }
        return true;
    }

    public static AiProvider findById(Context context, String id) {
        if (TextUtils.isEmpty(id)) return null;
        for (AiProvider p : getProviders(context)) {
            if (TextUtils.equals(p.id, id)) return p;
        }
        return null;
    }

    /** 启用的 chat 服务商 */
    public static List<AiProvider> enabledChats(Context context) {
        List<AiProvider> out = new ArrayList<>();
        for (AiProvider p : getProviders(context)) {
            if (p.enabled && AiProvider.KIND_CHAT.equals(p.kind)
                    && AiProvider.PROTOCOL_OPENAI.equals(p.protocol)) {
                out.add(p);
            }
        }
        return out;
    }

    public static String getDefaultId(Context context) {
        return prefs(context).getString(KEY_DEFAULT, "");
    }

    public static void setDefaultId(Context context, String id) {
        prefs(context).edit().putString(KEY_DEFAULT, id == null ? "" : id).apply();
    }

    public static AiProvider getDefault(Context context) {
        AiProvider p = findById(context, getDefaultId(context));
        if (p != null && p.enabled) return p;
        List<AiProvider> chats = enabledChats(context);
        return chats.isEmpty() ? null : chats.get(0);
    }

    /** 某功能实际使用的服务商（空=跟随默认） */
    public static AiProvider resolveForFeature(Context context, String feature) {
        AiProvider p = findById(context, getFeatureProviderId(context, feature));
        if (p != null && p.enabled) return p;
        return getDefault(context);
    }

    public static String getFeatureProviderId(Context context, String feature) {
        return prefs(context).getString("ai_provider_" + feature, "");
    }

    public static void setFeatureProviderId(Context context, String feature, String id) {
        prefs(context).edit().putString("ai_provider_" + feature, id == null ? "" : id).apply();
    }

    public static String summary(Context context) {
        List<AiProvider> list = getProviders(context);
        if (list.isEmpty()) return "未配置";
        int enabled = 0;
        for (AiProvider p : list) if (p.enabled) enabled++;
        return "已配置 " + list.size() + " 个";
    }

    public static boolean hasAny(Context context) {
        return !enabledChats(context).isEmpty();
    }

    /** 悬浮球开关（默认开，但无配置时不显示） */
    public static boolean isFloatEnabled(Context context) {
        return prefs(context).getBoolean(KEY_FLOAT_ENABLED, true);
    }

    public static void setFloatEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_FLOAT_ENABLED, enabled).apply();
    }

    /** 是否应显示悬浮球：开关开 + 有可用服务商 */
    public static boolean shouldShowFloat(Context context) {
        return isFloatEnabled(context) && hasAny(context);
    }

    public static int getFloatX(Context context, int def) {
        return prefs(context).getInt(KEY_FLOAT_X, def);
    }

    public static int getFloatY(Context context, int def) {
        return prefs(context).getInt(KEY_FLOAT_Y, def);
    }

    public static void saveFloatPos(Context context, int x, int y) {
        prefs(context).edit().putInt(KEY_FLOAT_X, x).putInt(KEY_FLOAT_Y, y).apply();
    }
}
