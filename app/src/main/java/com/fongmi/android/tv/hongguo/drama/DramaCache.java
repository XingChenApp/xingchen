package com.fongmi.android.tv.hongguo.drama;

import com.fongmi.android.tv.hongguo.api.HgDevice;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 播放模型落盘缓存（2026-10-30 从零重建）：vid → video_model 原串
 * （DramaVideoModel.sourceJson）＋过期时刻，Prefers 单键存一本 JSON 账。
 * 命中且未过期直接以 DramaParser.buildModel 重建（与回包解析同路）；
 * 过期按 url_expire 语义失效（模型自带过期优先，缺失时给 10 分钟保底）。
 * 条数上限 400，超限淘汰最早写入的。
 *
 * <p>脸戳（A-13）：每条落盘时记写入时的设备指纹种子（HgDevice
 * Profile.fpSeed，每次档案重置＝换脸必重掷）；读时戳不符即按未
 * 命中淘汰——旧脸签出的直链模型不再被新脸在 TTL 内续用。存量无
 * 戳条目一律作废（读时淘汰）。</p>
 *
 * <p>fplay 落盘（A-12）：除「有可播档」的模型外，带 fallback_api
 * 的模型与 fplay 直取回的成品模型也可经 {@link #putFor} 按请求
 * vid 落盘——无可播档集不再每次重走 multi＋单集＋fplay 全链。</p>
 */
public final class DramaCache {

    private static final String KEY = "drama_models_v1";
    private static final int MAX_ENTRIES = 400;
    private static final long FALLBACK_TTL_MS = 10L * 60 * 1000;

    private DramaCache() {
    }

    /** 当前脸戳：设备档案的指纹种子；取不到（异常）返回空串，按不符处置（宁弃不续用旧链）。 */
    private static String faceStamp() {
        try {
            HgDevice.Profile p = HgDevice.profile();
            return p == null || p.fpSeed == null ? "" : p.fpSeed;
        } catch (Throwable t) {
            return "";
        }
    }

    public static synchronized DramaVideoModel get(String vid) {
        if (vid == null || vid.isEmpty()) return null;
        JsonObject book = load();
        JsonElement el = book.get(vid);
        if (el == null || !el.isJsonObject()) return null;
        JsonObject e = el.getAsJsonObject();
        long exp = e.has("exp") ? e.get("exp").getAsLong() : 0;
        if (exp > 0 && System.currentTimeMillis() > exp) {
            book.remove(vid);
            save(book);
            return null;
        }
        // 脸戳不符＝这条是旧脸签出的链（或存量无戳条目）：淘汰，不给新脸续用。
        String face = e.has("face") ? e.get("face").getAsString() : null;
        if (face == null || !face.equals(faceStamp())) {
            book.remove(vid);
            save(book);
            return null;
        }
        String json = e.has("json") ? e.get("json").getAsString() : null;
        if (json == null || json.isEmpty()) return null;
        return DramaParser.restoreModel(vid, json, 0, 0);
    }

    public static synchronized void put(DramaVideoModel model) {
        if (model == null || model.getSourceJson() == null
                || model.firstPlayableEntry() == null) return;
        String vid = model.getRequestedId() != null ? model.getRequestedId() : model.getVideoId();
        if (vid == null || vid.isEmpty()) return;
        store(vid, model);
    }

    /**
     * 按请求 vid 落盘（fplay 链专用）：fplay 成品模型自带 requestedId
     * 为 null、其 videoId 是内部 id，按模型自带键存会让下次按请求 vid
     * 查不到。故由调用方（知道请求 vid 的那层）显式给键。准入门比
     * {@link #put} 宽一格：有可播档，或带 fallback_api（下次起播可
     * 直奔 fplay、跳过 multi＋单集两发）。
     */
    public static synchronized void putFor(String vid, DramaVideoModel model) {
        if (vid == null || vid.isEmpty() || model == null
                || model.getSourceJson() == null) return;
        boolean usable = model.firstPlayableEntry() != null
                || (model.getFallbackApi() != null && !model.getFallbackApi().isEmpty());
        if (!usable) return;
        store(vid, model);
    }

    private static void store(String vid, DramaVideoModel model) {
        JsonObject book = load();
        JsonObject e = new JsonObject();
        e.addProperty("json", model.getSourceJson());
        long exp = model.getUrlExpire() > 0
                ? model.getUrlExpire() * 1000L
                : System.currentTimeMillis() + FALLBACK_TTL_MS;
        e.addProperty("exp", exp);
        e.addProperty("face", faceStamp());
        book.remove(vid);
        book.add(vid, e);
        while (book.size() > MAX_ENTRIES) {
            String first = book.keySet().iterator().next();
            book.remove(first);
        }
        save(book);
    }

    public static synchronized void clear() {
        Prefers.put(KEY, "");
    }

    private static JsonObject load() {
        String raw = Prefers.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return new JsonObject();
        try {
            JsonElement el = JsonParser.parseString(raw);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static void save(JsonObject book) {
        Prefers.put(KEY, book.toString());
    }
}
