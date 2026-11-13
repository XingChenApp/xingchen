package com.fongmi.android.tv.legado;

import android.content.Context;
import android.content.SharedPreferences;

import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 书源存储与管理底座：导入（文本/文件/链接）· 列表 · 启用停用 · 删除。
 * <p>
 * 读回一律 TypeToken（R8 泛型关卡）；type 1/3 只存不跑，UI 标「暂不支持」。
 * 不内置任何书源：只做执行器。
 */
public class LegadoStore {

    private static final String PREFS = "legado_sources";
    private static final String KEY = "sources";
    private static final Gson GSON = new Gson();
    private static final Gson SOURCE_GSON = new GsonBuilder()
            .registerTypeAdapter(LegadoSource.class, new SourceDeserializer()).create();
    private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {
    }.getType();
    private static final Type HEADER_MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    /**
     * header 两种形态都吃（生态源常见写法）：对象照常；字符串是 JSON 就解析成表，
     * 不是 JSON 就忽略该字段。别再让 header 形态杀整包。
     */
    static class SourceDeserializer implements JsonDeserializer<LegadoSource> {
        private final Gson plain = new Gson();

        @Override
        public LegadoSource deserialize(JsonElement json, Type typeOfT,
                                         JsonDeserializationContext context) {
            JsonObject o = json.getAsJsonObject();
            JsonElement h = o.get("header");
            Map<String, String> headerMap = null;
            boolean stripped = false;
            if (h != null && h.isJsonPrimitive() && h.getAsJsonPrimitive().isString()) {
                stripped = true;
                String hs = h.getAsString().trim();
                if (hs.startsWith("{")) {
                    try {
                        headerMap = context.deserialize(JsonParser.parseString(hs), HEADER_MAP_TYPE);
                    } catch (Throwable ignored) {
                    }
                }
                o.remove("header");
            }
            LegadoSource s = plain.fromJson(o, LegadoSource.class);
            if (stripped) s.header = headerMap;
            return s;
        }
    }

    /** 存储条目：源 JSON 原文＋开关。 */
    public static class Entry {
        public String json = "";
        public boolean enabled = true;
        public long addedAt = 0L;
        /** 解析缓存（不持久化）。 */
        transient LegadoSource parsed;
    }

    private static SharedPreferences prefs() {
        return App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<Entry> all() {
        String raw = prefs().getString(KEY, "[]");
        try {
            List<Entry> list = GSON.fromJson(raw, LIST_TYPE);
            return list == null ? new ArrayList<>() : list;
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    private static synchronized void saveAll(List<Entry> list) {
        for (Entry e : list) e.parsed = null;
        prefs().edit().putString(KEY, GSON.toJson(list, LIST_TYPE)).apply();
    }

    /** 去掉 BOM 头（部分编辑器/网站导出的文件带）。 */
    private static String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '﻿') return s.substring(1);
        return s;
    }

    public static LegadoSource parse(String json) {
        List<LegadoSource> all = parseList(json);
        return all.get(0);
    }

    /** 逐个解析的结果：进了的＋坏项跳过的计数。 */
    public static class ParseResult {
        public final List<LegadoSource> ok = new ArrayList<>();
        /**
         * 与 ok 平行的原始 JSON 对象（导入存库用）：LegadoSource 只建模子集字段，
         * ruleExplore/loginUrl/loginUi 等未建模的标准字段经「解析→重序列化」会整批
         * 丢失，存原文才保得住。header 按 SourceDeserializer 同口径归一（字符串
         * JSON→对象、非 JSON 字符串→去掉），其余字段一字不动。
         */
        public final List<JsonObject> raw = new ArrayList<>();
        public int skipped = 0;
    }

    /** 原始对象的 header 归一（与 SourceDeserializer 对已建模字段的处理同口径） */
    private static JsonObject normalizedRaw(JsonObject o, LegadoSource parsed) {
        if (o == null) return null;
        JsonObject copy = o.deepCopy();
        JsonElement h = copy.get("header");
        // 对象形态的 header 原样保留；字符串形态在解析时已被反序列化器摘掉并转成
        // parsed.header 表——转出表就以对象形态写回原文，转不出（非 JSON 字符串）
        // 就去掉，与反序列化器的最终结果保持一致，不把两种形态混存
        if (h == null || !h.isJsonObject()) {
            if (parsed != null && parsed.header != null) copy.add("header", GSON.toJsonTree(parsed.header));
            else if (h != null) copy.remove("header");
        }
        return copy;
    }

    /**
     * 解析书源 JSON：支持裸对象 {...} 与数组 [{...}, {...}] 两种形态。
     * 对齐原版「阅读」：只认纯 JSON，不从说明文字里抠。
     * 数组逐个解析：坏项跳过并计数，不再整包陪葬。
     * 标准书源文件/订阅链接几乎全是数组，老写法只认裸对象是导入失败的根因之一。
     */
    public static ParseResult parseListTolerant(String json) {
        String t = stripBom(json == null ? "" : json.trim());
        if (t.isEmpty()) throw new LegadoException("内容为空");
        ParseResult r = new ParseResult();
        try {
            if (t.startsWith("[")) {
                JsonArray arr = JsonParser.parseString(t).getAsJsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    try {
                        LegadoSource s = SOURCE_GSON.fromJson(arr.get(i), LegadoSource.class);
                        if (s == null || s.bookSourceUrl == null || s.bookSourceUrl.trim().isEmpty()) {
                            throw new LegadoException("缺少 bookSourceUrl");
                        }
                        r.ok.add(s);
                        r.raw.add(arr.get(i).isJsonObject() ? normalizedRaw(arr.get(i).getAsJsonObject(), s) : null);
                    } catch (Throwable ignored) {
                        r.skipped++;
                    }
                }
            } else {
                JsonElement single = JsonParser.parseString(t);
                LegadoSource s = SOURCE_GSON.fromJson(single, LegadoSource.class);
                if (s == null || s.bookSourceUrl == null || s.bookSourceUrl.trim().isEmpty()) {
                    throw new LegadoException("不是合法书源：缺少 bookSourceUrl");
                }
                r.ok.add(s);
                r.raw.add(single.isJsonObject() ? normalizedRaw(single.getAsJsonObject(), s) : null);
            }
        } catch (LegadoException e) {
            throw e;
        } catch (Throwable th) {
            throw new LegadoException("书源 JSON 解析失败"
                    + truncatedHint(t) + ": " + th.getMessage());
        }
        if (r.ok.isEmpty() && r.skipped == 0) throw new LegadoException("内容为空");
        return r;
    }

    /**
     * 解析书源 JSON（严格版）：坏项仍抛错。存量条目回读（parse）走这个，
     * 语义不变。
     */
    public static List<LegadoSource> parseList(String json) {
        ParseResult r = parseListTolerant(json);
        if (r.ok.isEmpty()) throw new LegadoException("没有可用的书源");
        return r.ok;
    }

    /** 截断提示：起手是 [/{、结尾却不是 ]/}，大概率粘贴时没贴全。 */
    private static String truncatedHint(String t) {
        char first = t.charAt(0);
        char last = t.charAt(t.length() - 1);
        if ((first == '[' && last != ']') || (first == '{' && last != '}')) {
            return "（内容像没贴全，检查结尾是不是被截断了）";
        }
        return "";
    }

    /**
     * 导入 JSON 文本；同 bookSourceUrl 覆盖。数组逐个入库（每源独立条目，
     * 列表/开关/删除等下游仍按单源 JSON 走）。
     * 返回：单个源且无跳过→源名；否则报清「进了几个、跳过几个」。
     */
    public static synchronized String importText(String json) {
        ParseResult pr = parseListTolerant(json);
        List<LegadoSource> srcs = pr.ok;
        List<Entry> list = all();
        // 去重索引：bookSourceUrl → 首个条目。旧写法每源扫全表重解析，3907 源整包
        // 约 8 分钟（平方复杂度）；一次建表后整包为线性，语义不变（首个命中覆盖）。
        Map<String, Entry> index = new HashMap<>();
        for (Entry e : list) {
            try {
                String u = parse(e.json).bookSourceUrl;
                if (u != null && !index.containsKey(u)) index.put(u, e);
            } catch (Throwable ignored) {
            }
        }
        int added = 0, replaced = 0;
        for (int i = 0; i < srcs.size(); i++) {
            LegadoSource s = srcs.get(i);
            // 存原始 JSON 而非子集类重序列化：未建模字段（ruleExplore/loginUrl 等）原样保全；
            // 原文缺失（异常形态）才退回旧口径
            JsonObject rawObj = i < pr.raw.size() ? pr.raw.get(i) : null;
            String one = rawObj != null ? GSON.toJson(rawObj) : GSON.toJson(s);
            Entry hit = s.bookSourceUrl == null ? null : index.get(s.bookSourceUrl);
            if (hit != null) {
                hit.json = one;
                replaced++;
            } else {
                Entry e = new Entry();
                e.json = one;
                e.enabled = true;
                e.addedAt = System.currentTimeMillis();
                list.add(e);
                if (s.bookSourceUrl != null) index.put(s.bookSourceUrl, e);
                added++;
            }
        }
        saveAll(list);
        if (srcs.size() == 1 && pr.skipped == 0) return srcs.get(0).tag();
        int total = added + replaced;
        String msg = "进了 " + total + " 个";
        if (pr.skipped > 0) msg += "，跳过 " + pr.skipped + " 个";
        return msg;
    }

    /** 导入文件（.json/.txt）。 */
    public static String importFile(String path) {
        File f = new File(path);
        if (!f.exists() || f.length() > 2 * 1024 * 1024) throw new LegadoException("文件不存在或过大（>2MB）");
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        } catch (Throwable t) {
            throw new LegadoException("文件读取失败: " + t.getMessage());
        }
        return importText(sb.toString());
    }

    /**
     * 导入链接（拉取后按文本导入）。对齐原版：支持 {"sourceUrls":[...]} 订阅列表，
     * 逐个拉取导入；普通 JSON 走 importText。
     */
    public static String importUrl(String url) {
        return importUrl(url, new HashSet<>(), 0);
    }

    private static final int IMPORT_URL_MAX_DEPTH = 10;

    private static String importUrl(String url, Set<String> visited, int depth) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new LegadoException("链接格式不对");
        }
        if (depth > IMPORT_URL_MAX_DEPTH || !visited.add(url)) {
            throw new LegadoException("订阅链接重复或层级过深，已跳过");
        }
        String text;
        try {
            LegadoHttp.Result r = LegadoHttpAndroid.get().fetch(url, "GET", null, null);
            text = new String(r.body, StandardCharsets.UTF_8);
        } catch (LegadoException e) {
            throw e;
        } catch (Throwable t) {
            throw new LegadoException("链接拉取失败: " + t.getMessage());
        }
        List<String> subs = sourceUrls(text);
        if (subs != null) {
            int ok = 0;
            StringBuilder fails = new StringBuilder();
            for (String u : subs) {
                try {
                    importUrl(u, visited, depth + 1);
                    ok++;
                } catch (Throwable t) {
                    if (fails.length() > 0) fails.append("；");
                    fails.append(u).append("：").append(shortMsg(t));
                }
            }
            if (ok == 0) throw new LegadoException("订阅列表全部拉取失败：" + fails);
            return "已导入 " + ok + " 个订阅源"
                    + (fails.length() > 0 ? "，失败：" + fails : "");
        }
        return importText(text);
    }

    /** 订阅列表形态 {"sourceUrls":[...]}；不是则返回 null。 */
    private static List<String> sourceUrls(String text) {
        try {
            String t = stripBom(text == null ? "" : text.trim());
            if (!t.startsWith("{")) return null;
            Type t2 = new TypeToken<java.util.Map<String, List<String>>>() {
            }.getType();
            java.util.Map<String, List<String>> m = GSON.fromJson(t, t2);
            if (m == null) return null;
            List<String> urls = m.get("sourceUrls");
            if (urls == null || urls.isEmpty()) return null;
            List<String> out = new ArrayList<>();
            for (String u : urls) {
                if (u != null && !u.trim().isEmpty()) out.add(u.trim());
            }
            return out.isEmpty() ? null : out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String shortMsg(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.isEmpty()) m = t.getClass().getSimpleName();
        return m.length() > 60 ? m.substring(0, 60) : m;
    }

    public static synchronized LegadoSource get(String bookSourceUrl) {
        for (Entry e : all()) {
            try {
                LegadoSource s = parse(e.json);
                if (s.bookSourceUrl.equals(bookSourceUrl)) {
                    e.parsed = s;
                    return s;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static synchronized boolean isEnabled(String bookSourceUrl) {
        for (Entry e : all()) {
            try {
                if (parse(e.json).bookSourceUrl.equals(bookSourceUrl)) return e.enabled;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static synchronized void setEnabled(String bookSourceUrl, boolean enabled) {
        List<Entry> list = all();
        for (Entry e : list) {
            try {
                if (parse(e.json).bookSourceUrl.equals(bookSourceUrl)) e.enabled = enabled;
            } catch (Throwable ignored) {
            }
        }
        saveAll(list);
    }

    public static synchronized void delete(String bookSourceUrl) {
        List<Entry> list = all();
        List<Entry> keep = new ArrayList<>();
        for (Entry e : list) {
            // 解析失败的条目不许连带删（中-10）：它不是要删的目标，只是解析不了，
            // 删掉等于把用户那个源无声弄丢；只有明确命中目标 url 的才丢弃。
            boolean drop = false;
            try {
                drop = parse(e.json).bookSourceUrl.equals(bookSourceUrl);
            } catch (Throwable ignored) {
                drop = false;
            }
            if (!drop) keep.add(e);
        }
        saveAll(keep);
    }

    /** 可跑的源（已启用＋type 0/2）。 */
    public static List<LegadoSource> runnable() {
        List<LegadoSource> out = new ArrayList<>();
        for (Entry e : all()) {
            if (!e.enabled) continue;
            try {
                LegadoSource s = parse(e.json);
                if (LegadoSource.runnable(s.bookSourceType)) out.add(s);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }
}
