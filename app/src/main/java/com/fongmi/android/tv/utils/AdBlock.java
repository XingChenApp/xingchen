package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Vod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 去广告引擎：总开关 + 广告关键词。
 * 开启后：列表中标题命中关键词的条目会被过滤掉；
 * 简介中命中关键词的行会被删除。
 * 关键词由 AdBlockActivity 管理（xingchen.adblock_keywords）。
 */
public class AdBlock {

    private static final String PREFS = "xingchen";
    private static final String KEY_ENABLED = "xingchen.adblock_enabled";
    private static final String KEY_KEYWORDS = "xingchen.adblock_keywords";

    private static volatile boolean loaded;
    private static volatile boolean enabled;
    private static volatile Set<String> keywords = new HashSet<>();

    /** 重新从 SharedPreferences 加载开关与关键词 */
    public static void refresh(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            enabled = prefs.getBoolean(KEY_ENABLED, false);
            Set<String> set = prefs.getStringSet(KEY_KEYWORDS, new HashSet<>());
            Set<String> clean = new HashSet<>();
            if (set != null) {
                for (String k : set) {
                    if (k != null && !k.trim().isEmpty()) clean.add(k.trim());
                }
            }
            keywords = clean;
        } catch (Exception ignored) {
        } finally {
            loaded = true;
        }
    }

    private static void ensureLoaded() {
        if (!loaded) {
            try {
                App app = App.get();
                if (app != null) refresh(app);
                else loaded = true;
            } catch (Exception ignored) {
                loaded = true;
            }
        }
    }

    public static boolean isEnabled() {
        ensureLoaded();
        return enabled;
    }

    /** 文本是否命中广告关键词（不区分大小写） */
    public static boolean containsAd(String text) {
        ensureLoaded();
        if (!enabled || text == null || text.isEmpty() || keywords.isEmpty()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : keywords) {
            String k = kw.toLowerCase(Locale.ROOT);
            if (!k.isEmpty() && lower.contains(k)) return true;
        }
        return false;
    }

    /**
     * 过滤列表：去掉标题命中关键词的条目。
     * 单条及以下不处理——详情页走单条结果，不能把正片过滤掉。
     */
    public static List<Vod> filter(List<Vod> items) {
        if (!isEnabled() || items == null || items.size() <= 1) return items;
        List<Vod> result = new ArrayList<>(items.size());
        for (Vod item : items) {
            if (item == null || !containsAd(item.getName())) result.add(item);
        }
        return result;
    }

    /** 清洗多行文本：删除命中关键词的行（用于简介） */
    public static String cleanLines(String text) {
        if (!isEnabled() || text == null || text.isEmpty()) return text;
        if (!text.contains("\n")) return containsAd(text) ? "" : text;
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (containsAd(line)) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString().trim();
    }
}
