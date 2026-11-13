package com.fongmi.android.tv.api.hk.util;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.HkFavoriteStore;
import com.fongmi.android.tv.api.hk.HkParserKit;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.db.AppDatabase;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 移植自 8.83 {@code com.example.hikerview.service.http.HikerRuleUtil}（smali 1042 行）。
 * <p>
 * 8.83 原文：
 * <ul>
 *   <li>{@code getAssetsFileByHiker(url)}：{@code hiker://assets/} 开头时，按后缀返回内置 asset 文本
 *      （home.js / beautify.js / crypto-java.js / wasm.js / translate/ / localmanager.txt），
 *       非 hiker://assets/ 或空返回 ""。</li>
 *   <li>{@code getRulesByHiker(url)}：{@code hiker://home@<标题>} → 按标题查规则库（LitePal）返回规则 JSON；
 *       另有异步版本（listener 回调）。星辰用 {@link HkRuleManager#loadRuleJson(String)} 同款语义。</li>
 * </ul>
 */
public final class HkHikerRuleUtil {

    private HkHikerRuleUtil() {
    }

    /**
     * 8.83 HikerRuleUtil.getAssetsFileByHiker(String) 语义移植（smali 16-173）。
     *
     * @param url 形如 hiker://assets/home.js
     * @return asset 文本内容；非 hiker://assets/ 或空返回 ""
     */
    public static String getAssetsFileByHiker(String url) {
        if (TextUtils.isEmpty(url)) return "";
        final String prefix = "hiker://assets/";
        if (!url.startsWith(prefix)) return "";
        String p0 = url.replace(prefix, "");
        // 8.83 白名单：home.js / beautify.js / crypto-java.js / wasm.js / translate/ 前缀 / localmanager.txt
        boolean allowed = "home.js".equals(p0)
                || "beautify.js".equals(p0)
                || "crypto-java.js".equals(p0)
                || "wasm.js".equals(p0)
                || p0.startsWith("translate/")
                || "localmanager.txt".equals(p0);
        if (!allowed) return "";
        String code = loadAsset(p0);
        return code == null ? "" : code;
    }

    /**
     * 环 17-1 剔除键集（复核订正裁定）：官 shareFilter 15 名（storage/pageList/id/
     * order/gmtCreate/gmtModified/lastUseTime/displayName 及 LitePal 基类内部位
     * associatedModelsMapForJoinTable/WithFK/WithoutFK、fieldsToSetToDefault、
     * listToClearAssociatedFK/listToClearSelfFK、saved）∪ 咱实体比官实体多出的
     * weight、adBlockUrls 两名（官 ArticleListRule 实体无此二字段、经其实体序列化
     * 恒不出；adBlockUrls 只在 JO 面）。共 17 名。
     */
    private static final java.util.Set<String> SHARE_EXCLUDED = new java.util.HashSet<>(
            java.util.Arrays.asList("storage", "pageList", "id", "order", "gmtCreate",
                    "gmtModified", "lastUseTime", "displayName",
                    "associatedModelsMapForJoinTable", "associatedModelsMapWithFK",
                    "associatedModelsMapWithoutFK", "fieldsToSetToDefault",
                    "listToClearAssociatedFK", "listToClearSelfFK", "saved",
                    "weight", "adBlockUrls"));

    /**
     * home@ 序列化口（同步/异步共用，复核订正）：以<b>存量规则 JSON 为源</b>，解析为
     * JsonObject 后剔除 {@link #SHARE_EXCLUDED} 键集、其余键原样保留再序列化——不经
     * HkRule 实体回环。批2 首落的实体回环三处偏官：无值也恒出 weight:0、有值多出
     * adBlockUrls；存量 JSON 里实体没有的键（orderIndex/batchDeleting/selected 等）
     * 被回环吞掉；version 数值经实体 String 字段回环变字符串。现形键集恒＝源 JSON
     * 键集减剔除集、值形态原样（version 保持数值形）。未命中（loadRuleJson 回
     * null）、畸形/非对象 JSON → 回字符串 "null"（四字符）——官查不到时序列化
     * null 字面量，catch→"null" 吞异常形保持现状（记名 15，不扩大）。
     */
    private static String shareSerializeRule(String rawJson) {
        if (rawJson == null) return "null";
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(rawJson);
            if (parsed == null || !parsed.isJsonObject()) return "null";
            com.google.gson.JsonObject obj = parsed.getAsJsonObject();
            for (String key : SHARE_EXCLUDED) obj.remove(key);
            String out = new com.google.gson.Gson().toJson(obj);
            return out == null ? "null" : out;
        } catch (Exception e) {
            return "null";
        }
    }

    /**
     * 8.83 HikerRuleUtil.getRulesByHiker(String) 同步版语义移植（smali 174-590）。
     *
     * @param url 形如 hiker://home@&lt;规则标题&gt;
     * @return 规则 JSON；查不到回 "null"（环 17-1），格式不对返回 ""
     */
    public static String getRulesByHiker(String url) {
        if (TextUtils.isEmpty(url) || !url.startsWith("hiker://")) return "";
        String p0 = url.replace("hiker://", "");
        if (p0.startsWith("home")) {
            // 8.83：split("@") 取 parts[1] 为标题查库；无标题返回全部规则（已排序）
            String[] parts = p0.split("@");
            if (parts.length > 1) {
                return shareSerializeRule(HkRuleManager.get().loadRuleJson(parts[1]));
            }
            return allRulesJson();
        }
        if (p0.startsWith("collection")) {
            // 对齐 8.83：返回收藏库记录 JSON（原版 home.js 收藏卡片用 picUrl/length）
            return HkFavoriteStore.toJson();
        }
        if (p0.startsWith("bookmark") || p0.startsWith("download")) {
            // 环 17-5/17-6 记名关账：官 Bookmark / DownloadRecord 两库在咱家
            // hk 侧无存储、无写入方（全树已核：只有页面路由消费、无 fetch
            // 取数消费），等价于空库，恒回 "[]"，不硬造库。
            return "[]";
        }
        if (p0.startsWith("history")) {
            // 环 17-7：官 findAll(ViewHistory)，非空 Collections.sort 后经
            // shareFilter 序列化；咱家数据源＝星辰 History 表 hk 条目
            // 无窗全量（DAO findAll＋hk 命名空间筛，不借 getHk 的当前
            // cid＋60 天窗口径），逐字段映射见 historyJson。
            return historyJson();
        }
        return "";
    }

    /**
     * 官 ViewHistory 的中间形（环 17-7）：只承载 shareFilter 序列化口径下
     * 会出现的字段集——selected/selecting 在官实体上标 serialize=false、
     * LitePal 基类内部位被 shareFilter 排除（环 17-1 键集同源），故不入本形。
     * time 带可空（官 Date 字段可为 null），{@link #compareTo} 逐支转写官
     * ViewHistory.compareTo（smali 78-127 亲验）：other==null→0；
     * other.time==null→1；this.time==null→-1；余 other.time.compareTo(this.time)
     * （时刻倒序、空时刻排最前）。
     */
    private static final class HkViewHistoryRow implements Comparable<HkViewHistoryRow> {
        String extraData;
        String group;
        String lastClick;
        String params;
        String picUrl;
        String ruleBaseUrl;
        java.util.Date time;
        String title;
        String type;
        String url;
        String videoUrl;

        @Override
        public int compareTo(HkViewHistoryRow other) {
            if (other == null) return 0;
            if (other.time == null) return 1;
            if (this.time == null) return -1;
            return other.time.compareTo(this.time);
        }
    }

    /**
     * 环 17-7 history 支本体（同步版；异步版经既有 HEAVY_TASK 转发同步版，
     * 两面同一实现）：History 表无窗全量中的 hk 条目逐条映射成官
     * ViewHistory 字段名回形（官 findAll 无当前 cid、无时间窗；
     * History.getHk 带当前 cid＋60 天窗、是海阔历史页的读取口径，
     * 本支不借用、getHk 本体不动），
     * 非空照官 Collections.sort（比较器即 {@link HkViewHistoryRow#compareTo}），
     * 序列化与 collection 支同模式（LinkedHashMap 组装＋Gson；无值字段不出现，
     * 对齐官 FastJSON 缺省不序列化 null 字段）。
     * <p>
     * 逐字段映射（官写入方 HeavyTaskUtil.saveHisSync／lambda$saveHistory$2/$3 语义）：
     * title←vodName；picUrl←vodPic；url←历史键内 vodId（详情页/条目地址，
     * 即当时浏览的页面地址）；videoUrl←episodeUrl（该记录实播的集地址，
     * 无集时不出现）；ruleBaseUrl←官式 getHomeUrl(url)（官写入时即
     * StringUtil.getHomeUrl(页面地址)，非规则地址）；type←"二级列表"
     * （官规则页记录的恒定 type 值，咱 hk 条目全部由规则详情/播放页产生，
     * 同类）；time←createTime 转 Date（0 视为无时刻、置 null 走官空时刻支），
     * 序列化为毫秒数（官 FastJSON 对 Date 的缺省形，与 collection 支
     * time 给毫秒数同形）。extraData/group/lastClick/params 四字段咱家
     * History 表无源——不编造，恒不出现（记名）。selected/selecting 两
     * 字段官实体带 @JSONField(serialize=false)、官回形恒不含，咱家省略
     * 即精确对位（非无源记名，C4 复核订正）。
     */
    private static String historyJson() {
        try {
            java.util.List<HkViewHistoryRow> rows = new java.util.ArrayList<>();
            for (History h : AppDatabase.get().getHistoryDao().findAll()) {
                if (History.isHk(h)) rows.add(toViewHistoryRow(h));
            }
            if (!rows.isEmpty()) java.util.Collections.sort(rows);
            java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
            for (HkViewHistoryRow r : rows) {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                if (r.extraData != null) m.put("extraData", r.extraData);
                if (r.group != null) m.put("group", r.group);
                if (r.lastClick != null) m.put("lastClick", r.lastClick);
                if (r.params != null) m.put("params", r.params);
                if (r.picUrl != null) m.put("picUrl", r.picUrl);
                if (r.ruleBaseUrl != null) m.put("ruleBaseUrl", r.ruleBaseUrl);
                if (r.time != null) m.put("time", r.time.getTime());
                if (r.title != null) m.put("title", r.title);
                if (r.type != null) m.put("type", r.type);
                if (r.url != null) m.put("url", r.url);
                if (r.videoUrl != null) m.put("videoUrl", r.videoUrl);
                out.add(m);
            }
            return new com.google.gson.Gson().toJson(out);
        } catch (Throwable ignored) {
            return "[]";
        }
    }

    private static HkViewHistoryRow toViewHistoryRow(History h) {
        HkViewHistoryRow row = new HkViewHistoryRow();
        row.title = h.getVodName();
        row.picUrl = h.getVodPic();
        String pageUrl = extractPageUrl(h);
        row.url = TextUtils.isEmpty(pageUrl) ? null : pageUrl;
        row.ruleBaseUrl = row.url == null ? null : HkParserKit.getHomeUrl(row.url);
        String episodeUrl = h.getEpisodeUrl();
        row.videoUrl = TextUtils.isEmpty(episodeUrl) ? null : episodeUrl;
        row.type = "二级列表";
        row.time = h.getCreateTime() > 0 ? new java.util.Date(h.getCreateTime()) : null;
        return row;
    }

    /**
     * 历史键 = 站点段@@@vodId@@@cid；vodId 本身可能含 @@@（如带 @lazyRule 的
     * 地址），按首尾剥（与 HkHistoryActivity.extractVodId 同口径）。
     */
    private static String extractPageUrl(History h) {
        String key = h.getKey() == null ? "" : h.getKey();
        if (key.isEmpty()) return h.getVodId();
        String siteKey = h.getSiteKey();
        String sym = AppDatabase.SYMBOL;
        if (siteKey.isEmpty() || !key.startsWith(siteKey + sym)) return h.getVodId();
        String rest = key.substring(siteKey.length() + sym.length());
        int tail = rest.lastIndexOf(sym);
        return tail >= 0 ? rest.substring(0, tail) : rest;
    }

    /** 环 17-8：官 HeavyTask 等价的后台执行器（异步版重活支投此再回调）。 */
    private static final java.util.concurrent.ExecutorService HEAVY_TASK =
            java.util.concurrent.Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "hk-hiker-rule-heavy");
                t.setDaemon(true);
                return t;
            });

    /**
     * 8.83 HikerRuleUtil.getRulesByHiker(String, OnCodeGetListener) 异步版语义移植（smali 591-848）。
     * 分支顺序与原文一致：empty → page → home → bookmark/download/collection/history → ""。
     * 环 17-8（硬门审计 B＋复核订正，官 HU 本体与 lambda$0/$1 亲读同构）：home/bookmark/
     * download/collection/history 五支在官是投 HeavyTask 后台线程执行、就在后台线程回调
     * onSuccess（查库＋shareFilter 序列化都在后台）；empty/page 两支直回、尾支恒
     * onSuccess("")、异步版无隐私支。失败形照官分界（复核订正）：官外层 try 只罩
     * <b>同步分发段</b>（含 404 判定、empty/page 直回、HeavyTask 提交、findAllAsync 登记），
     * 其 catch→onFailure(500,"获取数据失败："+msg) 只接分发段异常；取数支本体
     * （lambda$0/$1）<b>无 try/catch</b>，支内抛错＝异常随执行器线程消散、回调永不达。
     * 批2 首落在支内 try/catch 把 getRulesByHiker 异常转 onFailure(500)，与官正反，已摘：
     * 支内不 catch；分发段（含 HEAVY_TASK.execute 提交本身失败）保留 onFailure(500) 形。
     */
    public static void getRulesByHiker(String url, HkCodeUtil.OnCodeGetListener listener) {
        try {
            if (TextUtils.isEmpty(url) || !url.startsWith("hiker://")) {
                listener.onFailure(404, url + "格式有误");
                return;
            }
            String p0 = url.replace("hiker://", "");
            if (p0.startsWith("empty")) {
                listener.onSuccess(HkParserKit.replaceOnce(p0, "empty", ""));
                return;
            }
            if (p0.startsWith("page")) {
                listener.onSuccess(p0);
                return;
            }
            if (p0.startsWith("home") || p0.startsWith("bookmark") || p0.startsWith("download")
                    || p0.startsWith("collection") || p0.startsWith("history")) {
                // 取数支本体照官 lambda$0/$1 无 try/catch：支内异常不转回调。
                HEAVY_TASK.execute(() -> listener.onSuccess(getRulesByHiker(url)));
                return;
            }
            listener.onSuccess("");
        } catch (Exception e) {
            listener.onFailure(500, "获取数据失败：" + e.getMessage());
        }
    }

    /** 全部已安装规则 JSON 数组（8.83 home 无标题：findAll + sort + 逐条 share 过滤序列化）。 */
    private static String allRulesJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        try {
            for (HkRule r : HkRuleManager.get().getRules()) {
                String json = HkRuleManager.get().loadRuleJson(r.getTitle());
                if (json == null) continue;
                if (!first) sb.append(",");
                sb.append(shareSerializeRule(json));
                first = false;
            }
        } catch (Exception ignored) {
        }
        return sb.append("]").toString();
    }

    private static String loadAsset(String assetPath) {
        try {
            InputStream is = App.get().getAssets().open(assetPath);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            String code = bos.toString("UTF-8");
            return TextUtils.isEmpty(code) ? null : code;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
