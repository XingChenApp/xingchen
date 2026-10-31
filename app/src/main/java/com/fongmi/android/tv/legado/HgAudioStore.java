package com.fongmi.android.tv.legado;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * 红果听书自己的进度状态（2026-10-30 分家令：只此一份）。
 * 名单显示、播放页进度、续播位置全读这一份；普通听书的 ReadingStore
 * 与它互不读写。按 bookId 存一条：章（itemId＋章名＋序号）、位置、书名、
 * 封面、更新时间。播放/暂停状态不落盘——那是播放器本体的实时状态，
 * 任何呈现都现读播放器，不许存副本（存了必和本体打架）。
 * 枚举口 listAll 供阅读板块「红果听书 · 在听」列表面读全量（按最近
 * 收听倒序）；remove 是该面的删除口（此前在位零调用，2026-10-31 接上）。
 * 本类只存数据、不含红果本体逻辑，放桥边（legado）是因为红果播放页在
 * 朋友版会被整包剥掉，留在剥离侧的调用方需要一个不消失的落点；
 * 朋友版没有红果听书源，这份存储永远为空，无副作用。
 */
public final class HgAudioStore {

    private static final String PREFS = "hg_audio_progress";

    public static final class Entry {
        /** 所在条目的 bookId（get/listAll 回填；落盘 JSON 里不存、键即 id）。 */
        public String bookId = "";
        public String title = "";
        public String coverUrl = "";
        public String itemId = "";
        public String chapterName = "";
        public int chapterIndex;
        public int position;
        public long updatedAt;
    }

    private HgAudioStore() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized void save(Context ctx, String bookId, String title,
                                         String coverUrl, String itemId,
                                         String chapterName, int chapterIndex,
                                         int position) {
        if (ctx == null || bookId == null || bookId.isEmpty()) return;
        try {
            JSONObject o = new JSONObject();
            o.put("title", title == null ? "" : title);
            o.put("cover", coverUrl == null ? "" : coverUrl);
            o.put("item", itemId == null ? "" : itemId);
            o.put("chName", chapterName == null ? "" : chapterName);
            o.put("chIdx", chapterIndex);
            o.put("pos", Math.max(0, position));
            o.put("ts", System.currentTimeMillis());
            prefs(ctx).edit().putString(bookId, o.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    public static synchronized Entry get(Context ctx, String bookId) {
        if (ctx == null || bookId == null || bookId.isEmpty()) return null;
        try {
            String raw = prefs(ctx).getString(bookId, null);
            if (raw == null || raw.isEmpty()) return null;
            JSONObject o = new JSONObject(raw);
            Entry e = new Entry();
            e.bookId = bookId;
            e.title = o.optString("title", "");
            e.coverUrl = o.optString("cover", "");
            e.itemId = o.optString("item", "");
            e.chapterName = o.optString("chName", "");
            e.chapterIndex = o.optInt("chIdx", 0);
            e.position = o.optInt("pos", 0);
            e.updatedAt = o.optLong("ts", 0);
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 全量枚举（在听列表面专用）：prefs 里每个键即一条在听书目，
     * 逐条走 get 同口径解析（坏条目跳过、不伤整表），按更新时间
     * （最近收听）倒序。只读，不改任何落盘形态，老存档直读。
     */
    public static synchronized java.util.List<Entry> listAll(Context ctx) {
        java.util.List<Entry> out = new java.util.ArrayList<>();
        if (ctx == null) return out;
        try {
            for (String key : prefs(ctx).getAll().keySet()) {
                Entry e = get(ctx, key);
                if (e != null) out.add(e);
            }
        } catch (Throwable ignored) {
        }
        out.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return out;
    }

    public static synchronized void remove(Context ctx, String bookId) {
        if (ctx == null || bookId == null || bookId.isEmpty()) return;
        try {
            prefs(ctx).edit().remove(bookId).apply();
        } catch (Throwable ignored) {
        }
    }
}
