package com.fongmi.android.tv.ui.hk;

import android.content.Context;
import android.content.SharedPreferences;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.HkRule;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 环40：官 FilmListActivity 四事件的异步持久化半在咱家的承载——官写回
 * ViewCollection（按 CUrl=规则 url＋MTitle=页面标题定位）与 ViewHistory
 * （按 url＋旧标题定位）两张表；咱家无这两张表的子系统（收藏星标面未移植、
 * 历史走宿主自有链），故以本存储作同形承载：收藏记录＝整份规则 JSON
 * （键＝规则 url＋页面标题），历史记录＝{title,pic,params}（键＝规则
 * url＋标题）。回写语义逐项照官：定位未命中静默无-op、解析失败只记日志
 * 不回滚内存、params/last_chapter_rule 回写前先解析记录内规则 JSON 改字段
 * 再整串存回。官序列化带 JSONPreFilter 分享过滤，咱家记录为本机私有
 * 存储、全字段保存（形态差记名，见 census 环40 追记）。
 *
 * <p>存储口抽成 {@link Store} 接口：生产走 SharedPreferences，夹具走内存表
 * 验键形与字段回写往返。</p>
 */
public final class HkPageRecordStore {

    /** 最小存取口（生产＝SharedPreferences，夹具＝内存 Map）。 */
    public interface Store {
        String get(String key);

        void put(String key, String value);
    }

    private static final String SP_NAME = "hk_page_records";

    private final Store store;

    public HkPageRecordStore(Store store) {
        this.store = store;
    }

    /** 生产单例：SharedPreferences 承载。 */
    public static HkPageRecordStore shared() {
        return new HkPageRecordStore(new Store() {
            @Override
            public String get(String key) {
                return prefs().getString(key, null);
            }

            @Override
            public void put(String key, String value) {
                if (value == null) prefs().edit().remove(key).apply();
                else prefs().edit().putString(key, value).apply();
            }
        });
    }

    private static SharedPreferences prefs() {
        return App.get().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    // ---------- 键形 ----------

    static String collectionKey(String ruleUrl, String pageTitle) {
        return "C\n" + nz(ruleUrl) + "\n" + nz(pageTitle);
    }

    static String historyKey(String ruleUrl, String title) {
        return "H\n" + nz(ruleUrl) + "\n" + nz(title);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ---------- 规则 JSON 字段回写（官：解析→改字段→整串存回） ----------

    /** 记录内规则 JSON 的字段改写；解析失败回 null（调用方静默放弃）。 */
    static String mergeRuleField(String ruleJson, String field, String value) {
        try {
            JsonObject obj = JsonParser.parseString(ruleJson).getAsJsonObject();
            if (value == null) obj.remove(field);
            else obj.addProperty(field, value);
            return obj.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 读记录内规则 JSON 的字段（缺失/解析失败回 null）。 */
    static String readRuleField(String ruleJson, String field) {
        try {
            JsonObject obj = JsonParser.parseString(ruleJson).getAsJsonObject();
            if (!obj.has(field) || obj.get(field).isJsonNull()) return null;
            return obj.get(field).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 收藏记录（官 ViewCollection 等价） ----------

    // 复核订正 B6：收藏记录的产生面已摘——旧形 initData 对每个进页无条件
    // 快照建记录（snapshotCollectionOnLoad），等于人人都是收藏、SP 随浏览
    // 无界增长。官口径：ViewCollection 记录只源于真实收藏行为；咱家该页
    // （HkListActivity/HkArticleFragment）现无收藏动作入口（宿主收藏星标面
    // 未搬，见 HkListActivity 类注），故本批不接产生面、记名「收藏记录产生
    // 面缺位、写口待其接上才生效」——下方收藏写口命中已有记录才写、无
    // 记录恒无-op；将来收藏入口落码时由它建记录，本类写口随即生效。

    /** 再访回读：记录内的 last_chapter_rule/params 覆盖到当前页规则（无记录无-op）。 */
    public void applyCollectionTo(HkRule rule, String pageTitle) {
        if (rule == null || rule.getUrl() == null) return;
        String json = store.get(collectionKey(rule.getUrl(), pageTitle));
        if (json == null) return;
        String lcr = readRuleField(json, "last_chapter_rule");
        if (lcr != null) rule.setLastChapterRule(lcr);
        String params = readRuleField(json, "params");
        if (params != null) rule.setParams(params);
    }

    /** SetPageLastChapterRuleEvent 持久化半：收藏记录内规则改 last_chapter_rule 存回。 */
    public void writeCollectionLastChapter(String ruleUrl, String pageTitle, String lastChapterRule) {
        writeCollectionField(ruleUrl, pageTitle, "last_chapter_rule", lastChapterRule);
    }

    /**
     * SetPageParamsEvent 持久化半（收藏侧）：同上改 params。官门（复核订正
     * B6）：收藏 params 写须 params 非空才写——空 params 直接无-op，不进记录。
     */
    public void writeCollectionParams(String ruleUrl, String pageTitle, String params) {
        if (params == null || params.isEmpty()) return;
        writeCollectionField(ruleUrl, pageTitle, "params", params);
    }

    /**
     * SetPagePicEvent 持久化半（收藏侧）：官写 ViewCollection 的 picUrl 列
     * （与规则 JSON 并列的记录列）；咱家记录把该列存为同对象 "pic" 键
     * （HkRule 无此字段名、回读规则时 Gson 忽略，不污染规则本体）。
     */
    public void writeCollectionPic(String ruleUrl, String pageTitle, String picUrl) {
        writeCollectionField(ruleUrl, pageTitle, "pic", picUrl);
    }

    private void writeCollectionField(String ruleUrl, String pageTitle, String field, String value) {
        String key = collectionKey(ruleUrl, pageTitle);
        String json = store.get(key);
        if (json == null) return; // 官定位未命中：静默无-op
        String merged = mergeRuleField(json, field, value);
        if (merged == null) return; // 解析失败：只记日志位（此处静默），不回滚内存
        store.put(key, merged);
    }

    // ---------- 历史记录（官 ViewHistory 等价） ----------

    /** 进页登记：无记录时以当前标题/图/params 建历史记录。 */
    public void upsertHistoryOnLoad(HkRule rule, String pageTitle, String picUrl) {
        if (rule == null || rule.getUrl() == null) return;
        String key = historyKey(rule.getUrl(), pageTitle);
        if (store.get(key) != null) return;
        JsonObject obj = new JsonObject();
        obj.addProperty("title", nz(pageTitle));
        obj.addProperty("pic", nz(picUrl));
        obj.addProperty("params", rule.getParams());
        store.put(key, obj.toString());
    }

    /**
     * $64 路历史半：官 {@code lambda$refreshReadData$64}（源行 5405）对
     * ViewHistory 是 setTitle＋setTime 两件——按旧标题定位、改标题并换键、
     * time 随标题一并刷新（与 $17 两半已写 time 的口径同，批 C2 复核订正补齐）。
     * 仅 HkArticleFragment 的 $64 调用点（onRefreshReadData）走此口。
     */
    public void writeHistoryTitle(String ruleUrl, String oldTitle, String newTitle) {
        writeHistoryTitleInternal(ruleUrl, oldTitle, newTitle, true);
    }

    /**
     * 环 40 SetPageTitleEvent 路历史半：官
     * {@code FilmListActivity.lambda$onSetTitle$10}（源行 730-733）对
     * ViewHistory 只 setTitle＋save——按旧标题定位、改标题并换键，
     * 不写 time（不刷新历史时间、不影响按时间排序）。批 C2 复验残留 R-1
     * 订正：此前与 $64 路共用 {@link #writeHistoryTitle} 同写 time、与官不同位。
     * 仅 HkArticleFragment 的环 40 调用点（onSetPageTitle）走此口。
     */
    public void writeHistoryTitleOnly(String ruleUrl, String oldTitle, String newTitle) {
        writeHistoryTitleInternal(ruleUrl, oldTitle, newTitle, false);
    }

    private void writeHistoryTitleInternal(String ruleUrl, String oldTitle, String newTitle, boolean refreshTime) {
        String oldKey = historyKey(ruleUrl, oldTitle);
        String json = store.get(oldKey);
        if (json == null) return;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            obj.addProperty("title", nz(newTitle));
            if (refreshTime) obj.addProperty("time", System.currentTimeMillis());
            store.put(historyKey(ruleUrl, newTitle), obj.toString());
            if (!nz(oldTitle).equals(nz(newTitle))) store.put(oldKey, null);
        } catch (Exception ignored) {
        }
    }

    /** SetPagePicEvent 持久化半（历史侧）：按标题定位改 pic。 */
    public void writeHistoryPic(String ruleUrl, String title, String picUrl) {
        writeHistoryField(ruleUrl, title, "pic", picUrl);
    }

    /** SetPageParamsEvent 持久化半（历史侧）：按标题定位改 params。 */
    public void writeHistoryParams(String ruleUrl, String title, String params) {
        writeHistoryField(ruleUrl, title, "params", params);
    }

    private void writeHistoryField(String ruleUrl, String title, String field, String value) {
        String key = historyKey(ruleUrl, title);
        String json = store.get(key);
        if (json == null) return;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            obj.addProperty(field, nz(value));
            store.put(key, obj.toString());
        } catch (Exception ignored) {
        }
    }

    // ---------- 环42 #autoReader# 续看位改写（官 lambda$autoReader$17／lambda$refreshReadData$64） ----------

    /**
     * 官 lambda$autoReader$17 的历史半：以旧 currentPageUrl 定位 ViewHistory，
     * url 改新址、params 反解规则改 url 再重打包、time 刷新存回。咱载体以
     * （规则 url＋页面标题）为键：命中记录换键到新 url；params 若是含 "url"
     * 字段的 JSON 对象（官 buildRuleToParams 产物同形）同步改该字段，其余
     * 形（纯参数串）无 url 可改、原样保留（载体差记名）。未命中静默无-op。
     */
    public void rewriteHistoryUrl(String oldUrl, String pageTitle, String newUrl) {
        if (oldUrl == null || newUrl == null || oldUrl.equals(newUrl)) return;
        String oldKey = historyKey(oldUrl, pageTitle);
        String json = store.get(oldKey);
        if (json == null) return;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("params") && obj.get("params").isJsonPrimitive()
                    && obj.get("params").getAsJsonPrimitive().isString()) {
                String rebuilt = mergeRuleField(obj.get("params").getAsString(), "url", newUrl);
                if (rebuilt != null) obj.addProperty("params", rebuilt);
            }
            obj.addProperty("time", System.currentTimeMillis());
            store.put(historyKey(newUrl, pageTitle), obj.toString());
            store.put(oldKey, null);
        } catch (Exception ignored) {
        }
    }

    /** 官 lambda$autoReader$17 的收藏半：ViewCollection.cUrl 改新址、params 同法重打包、time 刷新。 */
    public void rewriteCollectionUrl(String oldUrl, String pageTitle, String newUrl) {
        if (oldUrl == null || newUrl == null || oldUrl.equals(newUrl)) return;
        String oldKey = collectionKey(oldUrl, pageTitle);
        String json = store.get(oldKey);
        if (json == null) return;
        String merged = mergeRuleField(json, "url", newUrl);
        if (merged == null) return;
        try {
            JsonObject obj = JsonParser.parseString(merged).getAsJsonObject();
            obj.addProperty("time", System.currentTimeMillis());
            store.put(collectionKey(newUrl, pageTitle), obj.toString());
            store.put(oldKey, null);
        } catch (Exception ignored) {
        }
    }

    /**
     * 官 lambda$refreshReadData$64 的收藏半：ViewCollection.setTime＋setMTitle
     * 两件（批 C2 复核订正补齐 time，与 $17 两半已写 time 的口径同）。
     * 咱收藏记录＝整份规则 JSON，MTitle 以同对象 "mTitle" 键承载
     * （与 "pic" 键同先例，Gson 回读规则时忽略未知键），time 同对象并写。
     */
    public void writeCollectionMTitle(String ruleUrl, String pageTitle, String chapterTitle) {
        String key = collectionKey(ruleUrl, pageTitle);
        String json = store.get(key);
        if (json == null) return; // 官定位未命中：静默无-op
        String merged = mergeRuleField(json, "mTitle", chapterTitle);
        if (merged == null) return; // 解析失败：静默放弃（与 writeCollectionField 同口径）
        try {
            JsonObject obj = JsonParser.parseString(merged).getAsJsonObject();
            obj.addProperty("time", System.currentTimeMillis());
            store.put(key, obj.toString());
        } catch (Exception ignored) {
        }
    }
}
