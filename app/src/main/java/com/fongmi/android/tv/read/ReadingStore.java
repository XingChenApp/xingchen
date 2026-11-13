package com.fongmi.android.tv.read;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

/**
 * 书架/阅读进度存储：照 HkFavoriteStore 模式（SharedPreferences + Gson）。
 * 查找键：在线书 kind + sourceName + bookTitle；本地书 local|localId
 * （拼键只许走 keyOf 一处，find/remove/contains 与 Record.key() 共用）。
 * <p>
 * R8 注意：读回必须用 TypeToken<List<Record>>（AGENTS.md 第一道关卡），
 * 裸 List.class 读回的是 LinkedTreeMap，会在取值时崩。
 */
public class ReadingStore {

    private static final String PREFS = "reading_shelf";
    private static final String KEY_RECORDS = "records";

    /** 书架记录。 */
    public static class Record {
        /** "COMIC" / "NOVEL"。 */
        public String kind = "";
        public String sourceName = "";
        public String bookTitle = "";
        public String coverUrl = "";
        public String chapterName = "";
        public int chapterIndex = 0;
        /** 当前章地址（继续读时重建会话用）。 */
        public String chapterUrl = "";
        /** py 源 key（继续读后按章重取用）。 */
        public String siteKey = "";
        /** py 小说：书的详情 id（有它才能重建章节表；老记录为空）。 */
        public String detailId = "";
        /** py 小说：记录时的线路 flag 名（多线路时重建选同一线路）。 */
        public String playFlag = "";
        /** Legado 书源 bookSourceUrl（继续读后按章重取用）。 */
        public String legadoKey = "";
        /** 来源：""=在线源，"local"=本地书（纯加法，老记录读回为空）。 */
        public String origin = "";
        /** 本地书 id（origin=local 时有效）。 */
        public String localId = "";
        /** 小说正文快照（py 小说继续读时用，海阔小说按章重抓）。 */
        public String content = "";
        /** 漫画：看到第几张图（下标）；小说：进度万分比（0~10000，换字号也准）。 */
        public int position = 0;
        /** 神经朗读：当前章内第几段（kind="TTS" 时有效）。 */
        public int segIndex = 0;
        /** 连载追更：记录的书地址（Legado 书源 bookUrl，有它才能查更新）。 */
        public String bookUrl = "";
        /** 海阔改道来源的规则名（节点归属换算用）。 */
        public String ruleTitle = "";
        /** 连载追更：上次查到的总章节数。 */
        public int totalChapters = 0;
        /** 连载追更：上次检查时间（节流用）。 */
        public long lastCheckedAt = 0L;
        /** 连载追更：是否有未读新章。 */
        public boolean hasUpdate = false;
        public long updatedAt = 0L;

        public String key() {
            return keyOf(kind, sourceName, bookTitle, origin, localId);
        }
    }

    /**
     * 拼键唯一口径：本地书可能重名，按书 id 区分（local|localId）；
     * 在线书走老键 kind|sourceName|bookTitle（行为不变）。
     * 此前 find/remove/contains 各自内联拼三段键，本地书记录的 key()
     * 是 local|localId，两边永远对不上——本地书查不回、删不掉。
     */
    public static String keyOf(String kind, String sourceName, String bookTitle,
                               String origin, String localId) {
        if ("local".equals(origin) && !TextUtils.isEmpty(localId)) {
            return "local|" + localId;
        }
        return kind + "|" + sourceName + "|" + bookTitle;
    }

    private static SharedPreferences prefs() {
        return App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static List<Record> readAll() {
        try {
            String json = prefs().getString(KEY_RECORDS, "");
            if (TextUtils.isEmpty(json)) return new ArrayList<>();
            // R8 关卡：必须 TypeToken，否则读回 LinkedTreeMap
            List<Record> list = new Gson().fromJson(json, new TypeToken<List<Record>>() {
            }.getType());
            return list == null ? new ArrayList<>() : list;
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    private static void writeAll(List<Record> list) {
        try {
            prefs().edit().putString(KEY_RECORDS, new Gson().toJson(list)).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 书架全部（按最近阅读倒序）。 */
    public static synchronized List<Record> list() {
        List<Record> list = readAll();
        list.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return list;
    }

    public static synchronized Record find(String kind, String sourceName, String bookTitle) {
        if (kind == null || sourceName == null || bookTitle == null) return null;
        return findByKey(keyOf(kind, sourceName, bookTitle, "", ""));
    }

    /** 按记录本体找：本地书必须走这条（三段拼法找不到 local|localId 的记录）。 */
    public static synchronized Record find(Record probe) {
        if (probe == null) return null;
        return findByKey(probe.key());
    }

    private static Record findByKey(String k) {
        for (Record r : readAll()) {
            if (k.equals(r.key())) return r;
        }
        return null;
    }

    public static synchronized boolean contains(String kind, String sourceName, String bookTitle) {
        return find(kind, sourceName, bookTitle) != null;
    }

    /** 按记录本体判在不在书架（本地书走这条）。 */
    public static synchronized boolean contains(Record probe) {
        return find(probe) != null;
    }

    /** 按本地书 id 找记录。 */
    public static synchronized Record findLocal(String localId) {
        if (TextUtils.isEmpty(localId)) return null;
        for (Record r : readAll()) {
            if ("local".equals(r.origin) && localId.equals(r.localId)) return r;
        }
        return null;
    }

    /** 新增或更新进度（同键覆盖）。 */
    public static synchronized void save(Record rec) {
        if (rec == null || TextUtils.isEmpty(rec.bookTitle)) return;
        rec.updatedAt = System.currentTimeMillis();
        List<Record> list = readAll();
        boolean hit = false;
        for (int i = 0; i < list.size(); i++) {
            if (rec.key().equals(list.get(i).key())) {
                list.set(i, rec);
                hit = true;
                break;
            }
        }
        if (!hit) list.add(rec);
        writeAll(list);
    }

    /**
     * 合并写进度（两个阅读器存进度专用）：同键已有记录时，只更新进度字段，
     * 会话建立时写入的字段（bookUrl/totalChapters/lastCheckedAt/hasUpdate 追更四件、
     * detailId/playFlag/ruleTitle 等重建钥匙）patch 没带（空/0）时保旧值——
     * 此前整条覆盖把 Legado 的 bookUrl 抹掉，追更在第一次阅读后永久失效（R1）。
     * 正文快照与章节绑死：本次没带快照且章节已换，旧快照作废，不许串章喂错正文。
     */
    public static synchronized void saveMerged(Record patch) {
        if (patch == null || TextUtils.isEmpty(patch.bookTitle)) return;
        for (Record old : readAll()) {
            if (!patch.key().equals(old.key())) continue;
            if (TextUtils.isEmpty(patch.bookUrl)) patch.bookUrl = old.bookUrl;
            if (patch.totalChapters <= 0) patch.totalChapters = old.totalChapters;
            if (patch.lastCheckedAt <= 0) patch.lastCheckedAt = old.lastCheckedAt;
            if (!patch.hasUpdate) patch.hasUpdate = old.hasUpdate;
            if (TextUtils.isEmpty(patch.detailId)) patch.detailId = old.detailId;
            if (TextUtils.isEmpty(patch.playFlag)) patch.playFlag = old.playFlag;
            if (TextUtils.isEmpty(patch.ruleTitle)) patch.ruleTitle = old.ruleTitle;
            if (TextUtils.isEmpty(patch.siteKey)) patch.siteKey = old.siteKey;
            if (TextUtils.isEmpty(patch.legadoKey)) patch.legadoKey = old.legadoKey;
            if (TextUtils.isEmpty(patch.coverUrl)) patch.coverUrl = old.coverUrl;
            if (TextUtils.isEmpty(patch.content)) {
                patch.content = TextUtils.equals(patch.chapterUrl, old.chapterUrl)
                        ? old.content : "";
            }
            break;
        }
        save(patch);
    }

    public static synchronized void remove(String kind, String sourceName, String bookTitle) {
        if (kind == null || sourceName == null || bookTitle == null) return;
        removeByKey(keyOf(kind, sourceName, bookTitle, "", ""));
    }

    /** 按记录本体删：本地书必须走这条（三段拼法删不掉 local|localId 的记录）。 */
    public static synchronized void remove(Record rec) {
        if (rec == null) return;
        removeByKey(rec.key());
    }

    private static void removeByKey(String k) {
        List<Record> list = readAll();
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (k.equals(list.get(i).key())) {
                list.remove(i);
                changed = true;
            }
        }
        if (changed) writeAll(list);
    }

    /** 最近在读的一本（「继续读」用）。 */
    public static synchronized Record latest() {
        List<Record> list = list();
        return list.isEmpty() ? null : list.get(0);
    }
}
