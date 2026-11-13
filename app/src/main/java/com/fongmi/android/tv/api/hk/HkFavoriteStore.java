package com.fongmi.android.tv.api.hk;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.db.AppDatabase;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 海阔详情收藏存储：对齐 8.83 ViewCollection（LitePal 表）的宿主面。
 * 原版：详情页星标 → ViewCollection.save()，查找键 CUrl + MTitle；
 * 星辰：SharedPreferences + Gson 持久化，查找键 ruleTitle + episodeUrl。
 * 收藏只进海阔侧，不再混入主 App 的 Keep（首页收藏）。
 */
public class HkFavoriteStore {

    private static final String PREFS = "hk_favorite";
    private static final String KEY_RECORDS = "records";
    private static final String KEY_MIGRATED = "migrated_keep_v1";
    public static final String DEFAULT_GROUP = "默认";

    private static SharedPreferences prefs() {
        return App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static List<HkFavorite> readAll() {
        try {
            String json = prefs().getString(KEY_RECORDS, "");
            if (TextUtils.isEmpty(json)) return new ArrayList<>();
            List<HkFavorite> list = new Gson().fromJson(json,
                    TypeToken.getParameterized(List.class, HkFavorite.class).getType());
            return list == null ? new ArrayList<>() : list;
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    private static void writeAll(List<HkFavorite> list) {
        try {
            prefs().edit().putString(KEY_RECORDS, new Gson().toJson(list)).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 全部收藏（按收藏时间倒序）。 */
    public static List<HkFavorite> list() {
        List<HkFavorite> list = readAll();
        list.sort((a, b) -> Long.compare(b.getTime(), a.getTime()));
        return list;
    }

    /** 已有分组（按首次出现顺序）。 */
    public static List<String> groups() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (HkFavorite f : readAll()) {
            String g = f.getGroup();
            if (!TextUtils.isEmpty(g)) set.add(g);
        }
        return new ArrayList<>(set);
    }

    public static HkFavorite find(String ruleTitle, String episodeUrl) {
        if (ruleTitle == null || episodeUrl == null) return null;
        for (HkFavorite f : readAll()) {
            if (ruleTitle.equals(f.getRuleTitle()) && episodeUrl.equals(f.getEpisodeUrl())) return f;
        }
        return null;
    }

    public static boolean isFavorite(String ruleTitle, String episodeUrl) {
        return find(ruleTitle, episodeUrl) != null;
    }

    /** 新增或更新（同键覆盖，时间刷新）。 */
    public static void addOrUpdate(HkFavorite fav) {
        if (fav == null) return;
        if (TextUtils.isEmpty(fav.getGroup())) fav.setGroup(DEFAULT_GROUP);
        List<HkFavorite> list = readAll();
        boolean hit = false;
        for (int i = 0; i < list.size(); i++) {
            HkFavorite f = list.get(i);
            if (fav.getRuleTitle().equals(f.getRuleTitle()) && fav.getEpisodeUrl().equals(f.getEpisodeUrl())) {
                list.set(i, fav);
                hit = true;
                break;
            }
        }
        if (!hit) list.add(fav);
        writeAll(list);
    }

    public static void remove(String ruleTitle, String episodeUrl) {
        if (ruleTitle == null || episodeUrl == null) return;
        List<HkFavorite> list = readAll();
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            HkFavorite f = list.get(i);
            if (ruleTitle.equals(f.getRuleTitle()) && episodeUrl.equals(f.getEpisodeUrl())) {
                list.remove(i);
                changed = true;
            }
        }
        if (changed) writeAll(list);
    }

    /**
     * 历史数据迁移（一次性）：此前海阔详情星标误写入主 App Keep 的记录
     * （key = hk_规则名@@@集地址@@@cid）搬回海阔收藏，原 Keep 行删除。
     * 后台线程执行，幂等（prefs 标记）。
     */
    public static void migrateIfNeeded() {
        if (prefs().getBoolean(KEY_MIGRATED, false)) return;
        new Thread(() -> {
            int moved = 0;
            try {
                List<Keep> keeps = AppDatabase.get().getKeepDao().findAll();
                if (keeps != null) {
                    for (Keep k : keeps) {
                        try {
                            if (k.getType() != 0) continue;
                            String key = k.getKey();
                            if (key == null || !key.startsWith(HkDetailBridge.KEY_PREFIX)) continue;
                            String rest = key.substring(HkDetailBridge.KEY_PREFIX.length());
                            String[] parts = rest.split(AppDatabase.SYMBOL);
                            if (parts.length < 2 || TextUtils.isEmpty(parts[0])) continue;
                            String ruleTitle = parts[0];
                            String episodeUrl = parts[1];
                            if (isFavorite(ruleTitle, episodeUrl)) {
                                k.delete();
                                continue;
                            }
                            HkFavorite f = new HkFavorite();
                            f.setTitle(k.getVodName());
                            f.setRuleTitle(ruleTitle);
                            f.setEpisodeUrl(episodeUrl);
                            f.setPicUrl(k.getVodPic());
                            f.setGroup(DEFAULT_GROUP);
                            f.setTime(k.getCreateTime());
                            HkRule rule = HkRuleManager.get().getInstalledRule(ruleTitle);
                            if (rule != null) f.setRuleUrl(rule.getUrl());
                            addOrUpdate(f);
                            k.delete();
                            moved++;
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            prefs().edit().putBoolean(KEY_MIGRATED, true).apply();
        }).start();
    }

    /**
     * 供规则 JS fetch('hiker://collection')：对齐 8.83 返回收藏记录 JSON。
     * 原版 home.js 只用 picUrl 与 length；字段同时给原版名（MTitle）与直白名。
     */
    public static String toJson() {
        try {
            List<java.util.Map<String, Object>> out = new ArrayList<>();
            for (HkFavorite f : list()) {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("MTitle", f.getTitle());
                m.put("title", f.getTitle());
                m.put("picUrl", f.getPicUrl());
                m.put("CUrl", f.getRuleUrl());
                m.put("url", f.getEpisodeUrl());
                m.put("ruleTitle", f.getRuleTitle());
                m.put("group", f.getGroup());
                m.put("time", f.getTime());
                out.add(m);
            }
            return new Gson().toJson(out);
        } catch (Throwable ignored) {
            return "[]";
        }
    }
}
