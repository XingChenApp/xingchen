package com.fongmi.android.tv.player.lyrics;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AudioPlaylistStore {

    private static final String KEY_PLAYLISTS = "audio_playlists";
    private static final String KEY_ACTIVE = "audio_playlist_active";
    private static final String KEY_METADATA = "audio_playlist_metadata";
    private static final String DEFAULT_NAME = "默认歌单";
    private static final int MAX_METADATA = 500;
    /**
     * 内层显式定型：外层 List&lt;Playlist&gt; 的 TypeToken 管不到 Playlist.items 的元素类型，
     * items 元素类型只靠字段泛型签名；R8 若剥掉签名，items 元素读回成 LinkedTreeMap，
     * normalize 里 for (Entry entry : item.items) 当场 ClassCastException，list() 的 catch
     * 吞掉后返回默认歌单、后续一次 upsert 还可能把默认歌单写回去覆盖原数据（1.4.8 榜单
     * 同款口子在歌单嵌套层的残留）。故 items 不走字段泛型读回，改按 JsonArray 逐个
     * 用显式 TypeToken&lt;List&lt;Entry&gt;&gt; 定型；外层 Playlist 本体只读标量字段。
     */
    private static final Type ENTRY_LIST_TYPE = new TypeToken<List<Entry>>() {
    }.getType();

    private AudioPlaylistStore() {
    }

    public static synchronized List<Playlist> list() {
        try {
            return normalize(parsePlaylists(Prefers.getString(KEY_PLAYLISTS)));
        } catch (Exception e) {
            return normalize((List<Playlist>) null);
        }
    }

    /**
     * 逐字段解析歌单 JSON：Playlist 只读 id／name 标量，items 数组用 ENTRY_LIST_TYPE 显式定型。
     * 存量救援（1.4.27 查修批）：1.4.26 及更早的 R8 成品没有保名，save() 反射写盘写的是
     * 改名键（Playlist a/b/c、Entry a–g），字面键读回全取不到、只剩幻影歌单，且第一次
     * 写回就把存量覆盖掉。映射真值取自 1.4.26 成品反编译（类 Ltl;/Lsl;/Lql;/Lrl;）的
     * 代码语义锚点＋声明序交叉验证：Playlist a=id（与 active 键比对、create 里赋 UUID）、
     * b=name（赋默认歌单名）、c=items（List）；Entry a=name、b=url（normalize 里与
     * name/title 互填、upsert 按它判重、空即丢弃）、c=playFlag、d=title、e=artist、
     * f=pic、g=lyrics。对象没有字面 id 键但有 a 键时按存量格式逐键回落读；救回后
     * 下一次 save() 按现行格式写盘、自动完成迁移。现行格式数据一字不走此路。
     */
    private static List<Playlist> parsePlaylists(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(json);
        if (root == null || !root.isJsonArray()) return null;
        List<Playlist> result = new ArrayList<>();
        for (com.google.gson.JsonElement el : root.getAsJsonArray()) {
            if (el == null || !el.isJsonObject()) continue;
            com.google.gson.JsonObject obj = el.getAsJsonObject();
            boolean legacy = !obj.has("id") && obj.has("a");
            Playlist item = new Playlist();
            item.id = optString(obj, legacy ? "a" : "id");
            item.name = optString(obj, legacy ? "b" : "name");
            com.google.gson.JsonElement itemsEl = obj.get(legacy ? "c" : "items");
            if (itemsEl != null && itemsEl.isJsonArray()) {
                if (legacy) {
                    item.items = parseLegacyEntries(itemsEl.getAsJsonArray());
                } else {
                    List<Entry> entries = App.gson().fromJson(itemsEl, ENTRY_LIST_TYPE);
                    item.items = entries == null ? new ArrayList<>() : entries;
                }
            } else {
                item.items = new ArrayList<>();
            }
            result.add(item);
        }
        return result;
    }

    /** 存量 Entry 逐键回落读（键映射见 parsePlaylists 注）。 */
    private static List<Entry> parseLegacyEntries(com.google.gson.JsonArray array) {
        List<Entry> result = new ArrayList<>();
        for (com.google.gson.JsonElement el : array) {
            if (el == null || !el.isJsonObject()) continue;
            com.google.gson.JsonObject obj = el.getAsJsonObject();
            Entry entry = new Entry();
            entry.name = optString(obj, "a");
            entry.url = optString(obj, "b");
            entry.playFlag = optString(obj, "c");
            entry.title = optString(obj, "d");
            entry.artist = optString(obj, "e");
            entry.pic = optString(obj, "f");
            entry.lyrics = optString(obj, "g");
            result.add(entry);
        }
        return result;
    }

    private static String optString(com.google.gson.JsonObject obj, String name) {
        if (obj == null || !obj.has(name) || obj.get(name).isJsonNull()) return "";
        try {
            return obj.get(name).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    public static synchronized Playlist active() {
        String id = Prefers.getString(KEY_ACTIVE);
        List<Playlist> items = list();
        for (Playlist item : items) if (TextUtils.equals(item.id, id)) return item;
        Playlist first = items.get(0);
        Prefers.put(KEY_ACTIVE, first.id);
        return first;
    }

    public static synchronized Playlist create(String name) {
        List<Playlist> items = list();
        Playlist item = new Playlist();
        item.id = UUID.randomUUID().toString();
        item.name = TextUtils.isEmpty(name) ? DEFAULT_NAME : name.trim();
        item.items = new ArrayList<>();
        items.add(item);
        save(items);
        Prefers.put(KEY_ACTIVE, item.id);
        return item;
    }

    public static synchronized void setActive(String id) {
        if (TextUtils.isEmpty(id)) return;
        for (Playlist item : list()) {
            if (!TextUtils.equals(item.id, id)) continue;
            Prefers.put(KEY_ACTIVE, id);
            return;
        }
    }

    public static synchronized void upsertItem(Entry entry) {
        if (entry == null || TextUtils.isEmpty(entry.url)) return;
        Playlist playlist = active();
        boolean updated = false;
        for (int i = 0; i < playlist.items.size(); i++) {
            Entry current = playlist.items.get(i);
            if (!TextUtils.equals(current.url, entry.url)) continue;
            playlist.items.set(i, normalize(entry));
            updated = true;
            break;
        }
        if (!updated) playlist.items.add(normalize(entry));
        upsertPlaylist(playlist);
    }

    public static synchronized void removeItem(String url) {
        if (TextUtils.isEmpty(url)) return;
        Playlist playlist = active();
        playlist.items.removeIf(item -> TextUtils.equals(item.url, url));
        upsertPlaylist(playlist);
    }

    public static synchronized void upsertPlaylist(Playlist playlist) {
        if (playlist == null) return;
        List<Playlist> items = list();
        Playlist normalized = normalize(playlist);
        for (int i = 0; i < items.size(); i++) {
            if (!TextUtils.equals(items.get(i).id, normalized.id)) continue;
            items.set(i, normalized);
            save(items);
            return;
        }
        items.add(normalized);
        save(items);
    }

    public static synchronized Metadata getMetadata(String url) {
        if (TextUtils.isEmpty(url)) return null;
        return getMetadata().get(url);
    }

    public static synchronized void putMetadata(String url, String title, String artist) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(title)) return;
        LinkedHashMap<String, Metadata> items = getMetadata();
        Metadata item = items.get(url);
        if (item == null) item = new Metadata();
        item.title = title.trim();
        if (!TextUtils.isEmpty(artist)) item.artist = artist.trim();
        item.updatedAt = System.currentTimeMillis();
        items.put(url, item);
        trimMetadata(items);
        Prefers.put(KEY_METADATA, App.gson().toJson(items));
    }

    /**
     * D-21 定型读回（批 5 F2 补齐）：此前整表走 TypeToken&lt;LinkedHashMap&lt;String, Metadata&gt;&gt;，
     * 值类型 Metadata 只靠 Map 泛型签名——与 items 同款口子：R8 若剥签名，值读回成 LinkedTreeMap，
     * entry.getValue().title 当场 ClassCastException 被外层 catch 吞掉、整表元数据静默丢。
     * 改按 JsonObject 逐项解析：键直接取，值用 Metadata.class 逐个定型（字段全是标量，不依赖任何泛型签名），
     * 单项坏只丢单项、不连坐整表；存储格式一字不变，存量数据直接读通。
     */
    private static LinkedHashMap<String, Metadata> getMetadata() {
        LinkedHashMap<String, Metadata> result = new LinkedHashMap<>();
        try {
            String json = Prefers.getString(KEY_METADATA);
            if (json == null || json.trim().isEmpty()) return result;
            com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(json);
            if (root == null || !root.isJsonObject()) return result;
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject().entrySet()) {
                if (TextUtils.isEmpty(entry.getKey()) || entry.getValue() == null
                        || !entry.getValue().isJsonObject()) continue;
                try {
                    Metadata item = App.gson().fromJson(entry.getValue(), Metadata.class);
                    if (item != null && TextUtils.isEmpty(item.title)
                            && entry.getValue().getAsJsonObject().has("a")) {
                        // 存量救援（同 parsePlaylists）：1.4.26 成品写的是改名键
                        // a=title、b=artist、c=updatedAt（Lrl; 语义锚点＋类型核对），
                        // 字面读回 title 恒空会被整项丢掉，按改名键回落读。
                        com.google.gson.JsonObject raw = entry.getValue().getAsJsonObject();
                        item.title = optString(raw, "a");
                        item.artist = optString(raw, "b");
                        try {
                            item.updatedAt = raw.has("c") ? raw.get("c").getAsLong() : 0L;
                        } catch (Exception ignored2) {
                            item.updatedAt = 0L;
                        }
                    }
                    if (item == null || TextUtils.isEmpty(item.title)) continue;
                    result.put(entry.getKey(), item);
                } catch (Exception ignored) {
                    // 单项坏丢单项，不许连坐整表
                }
            }
            return result;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static void trimMetadata(LinkedHashMap<String, Metadata> items) {
        while (items.size() > MAX_METADATA) {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, Metadata> entry : items.entrySet()) {
                long updatedAt = entry.getValue() == null ? 0 : entry.getValue().updatedAt;
                if (oldestKey != null && updatedAt >= oldestTime) continue;
                oldestKey = entry.getKey();
                oldestTime = updatedAt;
            }
            if (oldestKey == null) return;
            items.remove(oldestKey);
        }
    }

    private static void save(List<Playlist> items) {
        Prefers.put(KEY_PLAYLISTS, App.gson().toJson(normalize(items)));
    }

    private static List<Playlist> normalize(List<Playlist> items) {
        List<Playlist> result = new ArrayList<>();
        if (items != null) {
            for (Playlist item : items) {
                Playlist normalized = normalize(item);
                if (normalized != null) result.add(normalized);
            }
        }
        if (result.isEmpty()) {
            Playlist item = new Playlist();
            item.id = UUID.randomUUID().toString();
            item.name = DEFAULT_NAME;
            item.items = new ArrayList<>();
            result.add(item);
        }
        return result;
    }

    private static Playlist normalize(Playlist item) {
        if (item == null) return null;
        if (TextUtils.isEmpty(item.id)) item.id = UUID.randomUUID().toString();
        if (TextUtils.isEmpty(item.name)) item.name = DEFAULT_NAME;
        if (item.items == null) item.items = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        for (Entry entry : item.items) {
            Entry normalized = normalize(entry);
            if (normalized != null) entries.add(normalized);
        }
        item.items = entries;
        return item;
    }

    private static Entry normalize(Entry entry) {
        if (entry == null || TextUtils.isEmpty(entry.url)) return null;
        if (TextUtils.isEmpty(entry.name)) entry.name = entry.title;
        if (TextUtils.isEmpty(entry.title)) entry.title = entry.name;
        return entry;
    }

    public static class Playlist {
        public String id = "";
        public String name = "";
        public List<Entry> items = new ArrayList<>();
    }

    public static class Entry {
        public String name = "";
        public String url = "";
        public String playFlag = "";
        public String title = "";
        public String artist = "";
        public String pic = "";
        public String lyrics = "";
    }

    public static class Metadata {
        public String title = "";
        public String artist = "";
        public long updatedAt;
    }
}
