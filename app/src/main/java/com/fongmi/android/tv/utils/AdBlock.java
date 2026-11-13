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
 * 开关与关键词只有一套：统一走 Setting 的 adblock / adblock_keywords（与播放流内去广告、
 * 弹幕去广告共用同一开关与同一份关键词），关键词由 AdBlockActivity 管理。
 * 旧版存放在 "xingchen" 文件里的 xingchen.adblock_* 已废弃，首次 refresh 时把旧关键词
 * 并入统一存储并清掉旧键，此后不再读。
 */
public class AdBlock {

    private static final String LEGACY_PREFS = "xingchen";
    private static final String LEGACY_KEY_ENABLED = "xingchen.adblock_enabled";
    private static final String LEGACY_KEY_KEYWORDS = "xingchen.adblock_keywords";

    private static volatile boolean loaded;
    private static volatile Set<String> keywords = new HashSet<>();

    /** 重新加载关键词（开关不缓存，每次直接读 Setting，两处设置页改完立即一致） */
    public static void refresh(Context context) {
        try {
            migrateLegacy(context);
            Set<String> clean = new HashSet<>();
            for (String k : com.fongmi.android.tv.setting.Setting.getAdblockKeywords()) {
                if (k != null && !k.trim().isEmpty()) clean.add(k.trim());
            }
            keywords = clean;
        } catch (Exception ignored) {
        } finally {
            loaded = true;
        }
    }

    /** 旧 xingchen 存储一次性迁移：旧关键词并入统一列表，旧开关/关键词键清掉。 */
    private static void migrateLegacy(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
            if (!prefs.contains(LEGACY_KEY_ENABLED) && !prefs.contains(LEGACY_KEY_KEYWORDS)) return;
            Set<String> legacy = prefs.getStringSet(LEGACY_KEY_KEYWORDS, new HashSet<>());
            if (legacy != null) {
                for (String k : legacy) {
                    if (k != null && !k.trim().isEmpty()) com.fongmi.android.tv.setting.Setting.addAdblockKeyword(k.trim());
                }
            }
            prefs.edit().remove(LEGACY_KEY_ENABLED).remove(LEGACY_KEY_KEYWORDS).apply();
        } catch (Exception ignored) {
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
        return com.fongmi.android.tv.setting.Setting.isAdblock();
    }

    /** 文本是否命中广告关键词（不区分大小写） */
    public static boolean containsAd(String text) {
        ensureLoaded();
        if (!isEnabled() || text == null || text.isEmpty() || keywords.isEmpty()) return false;
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
