package com.fongmi.android.tv.api.hk;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.History;
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
    private static final String KEY_RECORDS_CORRUPT = "records_corrupt_backup";
    private static final String TAG = "HkFavoriteStore";
    public static final String DEFAULT_GROUP = "默认";

    private static SharedPreferences prefs() {
        return App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * 严格读：无数据回空表；已存 JSON 解析失败回 null（与真空可区分），
     * 原始串先备份到 KEY_RECORDS_CORRUPT 再报错——写路径遇 null 必须中止，
     * 不许拿空表覆盖盘上可能完好的收藏（批 fix349 补结构，原 catch 静默回空）。
     */
    private static List<HkFavorite> tryReadAll() {
        String json;
        try {
            json = prefs().getString(KEY_RECORDS, "");
        } catch (Throwable t) {
            Log.e(TAG, "收藏记录读取失败", t);
            return null;
        }
        if (TextUtils.isEmpty(json)) return new ArrayList<>();
        try {
            List<HkFavorite> list = new Gson().fromJson(json,
                    TypeToken.getParameterized(List.class, HkFavorite.class).getType());
            return list == null ? new ArrayList<>() : list;
        } catch (Throwable t) {
            Log.e(TAG, "收藏记录解析失败，已备份原始数据并中止本次读写", t);
            try {
                if (!json.equals(prefs().getString(KEY_RECORDS_CORRUPT, ""))) {
                    prefs().edit().putString(KEY_RECORDS_CORRUPT, json).apply();
                }
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    private static List<HkFavorite> readAll() {
        List<HkFavorite> list = tryReadAll();
        return list == null ? new ArrayList<>() : list;
    }

    private static boolean writeAll(List<HkFavorite> list) {
        try {
            prefs().edit().putString(KEY_RECORDS, new Gson().toJson(list)).apply();
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "收藏记录写入失败", t);
            return false;
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

    /**
     * 由海阔历史行组收藏记录（收藏产生面的统一构建口：历史页长按与借面
     * 会话内的收藏动作共用）：键口径与详情页星标 onHkKeep 同——episodeUrl
     * 存行自带的 vodId（详情形＝条目址、直达形＝集址），direct 按行上集址
     * 与 vodId 是否相等判（与 HkHistoryActivity.openRecord 的 directForm
     * 同一口径），线路名、标题、封面全随行。
     */
    public static HkFavorite fromHistory(History h) {
        if (h == null) return null;
        String siteKey = h.getSiteKey() == null ? "" : h.getSiteKey();
        if (!siteKey.startsWith(HkDetailBridge.KEY_PREFIX)) return null;
        String ruleTitle = siteKey.substring(HkDetailBridge.KEY_PREFIX.length());
        String vodId = extractVodId(h);
        if (TextUtils.isEmpty(ruleTitle) || TextUtils.isEmpty(vodId)) return null;
        String episodeUrl = h.getEpisodeUrl() == null ? "" : h.getEpisodeUrl();
        HkFavorite f = new HkFavorite();
        f.setRuleTitle(ruleTitle);
        f.setEpisodeUrl(vodId);
        f.setDirect(!episodeUrl.isEmpty() && episodeUrl.equals(vodId));
        f.setFlag(h.getVodFlag() == null ? "" : h.getVodFlag());
        f.setTitle(h.getVodName() == null ? "" : h.getVodName());
        f.setPicUrl(h.getVodPic() == null ? "" : h.getVodPic());
        f.setGroup(DEFAULT_GROUP);
        f.setTime(System.currentTimeMillis());
        HkRule rule = HkRuleManager.get().getInstalledRule(ruleTitle);
        if (rule != null) f.setRuleUrl(rule.getUrl());
        return f;
    }

    /** 历史键 = 站点段@@@vodId@@@cid；vodId 本身可能含 @@@（如带 @lazyRule 的地址），按首尾剥（与 HkHistoryActivity.extractVodId 同口径）。 */
    private static String extractVodId(History h) {
        String key = h.getKey() == null ? "" : h.getKey();
        String siteKey = h.getSiteKey() == null ? "" : h.getSiteKey();
        String sym = AppDatabase.SYMBOL;
        if (siteKey.isEmpty() || !key.startsWith(siteKey + sym)) {
            return h.getVodId() == null ? "" : h.getVodId();
        }
        String rest = key.substring(siteKey.length() + sym.length());
        int tail = rest.lastIndexOf(sym);
        return tail >= 0 ? rest.substring(0, tail) : rest;
    }

    /**
     * 收藏切换（与详情页星标 onHkKeep 同语义）：已收藏→取消回 false；
     * 未收藏→存入回 true；入参非法或写盘失败回 null。
     */
    public static Boolean toggle(HkFavorite fav) {
        if (fav == null || TextUtils.isEmpty(fav.getRuleTitle())
                || TextUtils.isEmpty(fav.getEpisodeUrl())) return null;
        if (find(fav.getRuleTitle(), fav.getEpisodeUrl()) != null) {
            return remove(fav.getRuleTitle(), fav.getEpisodeUrl()) ? Boolean.FALSE : null;
        }
        return addOrUpdate(fav) ? Boolean.TRUE : null;
    }

    /** 新增或更新（同键覆盖，时间刷新）。盘上数据解析失败时中止并回 false，不覆盖旧档。 */
    public static boolean addOrUpdate(HkFavorite fav) {
        if (fav == null) return false;
        if (TextUtils.isEmpty(fav.getGroup())) fav.setGroup(DEFAULT_GROUP);
        List<HkFavorite> list = tryReadAll();
        if (list == null) return false;
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
        return writeAll(list);
    }

    public static boolean remove(String ruleTitle, String episodeUrl) {
        if (ruleTitle == null || episodeUrl == null) return false;
        List<HkFavorite> list = tryReadAll();
        if (list == null) return false;
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            HkFavorite f = list.get(i);
            if (ruleTitle.equals(f.getRuleTitle()) && episodeUrl.equals(f.getEpisodeUrl())) {
                list.remove(i);
                changed = true;
            }
        }
        return !changed || writeAll(list);
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
            // 任一条迁移失败都不许打完成标记：失败条目留在 Keep 里，
            // 下次启动整轮重试，不许单条静默永不进新 store（批 residual）。
            boolean anyFailed = false;
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
                            if (!addOrUpdate(f)) {
                                // 没存进新 store 就不许删 Keep 原行，更不许
                                // 冒充迁完：置失败标，整轮不打完成标记。
                                anyFailed = true;
                                Log.e(TAG, "单条旧收藏迁移存入失败，保留原记录待重试：" + ruleTitle);
                                continue;
                            }
                            k.delete();
                            moved++;
                        } catch (Throwable t) {
                            anyFailed = true;
                            Log.e(TAG, "单条旧收藏迁移失败，保留原记录待重试", t);
                        }
                    }
                }
            } catch (Throwable t) {
                // 迁移整体失败不打完成标记：下次启动重试，不许静默当已迁（批 fix349）。
                Log.e(TAG, "旧收藏迁移失败，下次启动重试", t);
                return;
            }
            if (anyFailed) {
                Log.e(TAG, "旧收藏迁移有条目失败，不打完成标记，下次启动重试（已迁 " + moved + " 条）");
                return;
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
