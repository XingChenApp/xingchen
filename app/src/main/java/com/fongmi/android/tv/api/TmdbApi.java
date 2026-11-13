package com.fongmi.android.tv.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TMDB 年份/评分：按片名搜索 TMDB，取首个 movie/tv 结果的年份与评分。
 * 两级缓存：内存 + SharedPreferences（30 天有效，最多 500 条）。
 * 所有网络在后台线程执行，结果回调到主线程。失败/未找到时回调 null。
 */
public class TmdbApi {

    public static final String PREFS = "xingchen";
    public static final String KEY_API_KEY = "xingchen.tmdb_api_key";
    private static final String CACHE_PREFS = "tmdb_cache";
    private static final String[] HOSTS = {"https://api.themoviedb.org/3", "https://api.tmdb.org/3"};
    private static final long TTL_MS = 30L * 24 * 60 * 60 * 1000;
    private static final int MAX_CACHE = 500;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<String, TmdbInfo> MEM = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<Callback>> INFLIGHT = new ConcurrentHashMap<>();
    private static volatile boolean diskLoaded = false;

    public static class TmdbInfo {
        public String year = "";
        public double rating = 0;
        public long ts = 0;

        public TmdbInfo() {
        }

        TmdbInfo(String year, double rating, long ts) {
            this.year = year == null ? "" : year;
            this.rating = rating;
            this.ts = ts;
        }

        boolean expired() {
            return System.currentTimeMillis() - ts > TTL_MS;
        }
    }

    public interface Callback {
        void onResult(TmdbInfo info);
    }

    public static String getApiKey(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API_KEY, "");
    }

    public static void putApiKey(Context context, String key) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_API_KEY, key == null ? "" : key.trim()).apply();
    }

    public static boolean isConfigured(Context context) {
        return !TextUtils.isEmpty(getApiKey(context));
    }

    public static void fetch(Context context, String title, Callback cb) {
        String key = title == null ? "" : title.trim();
        if (key.isEmpty()) {
            post(cb, null);
            return;
        }
        TmdbInfo hit = getCached(context, key);
        if (hit != null && !hit.expired()) {
            post(cb, hit);
            return;
        }
        if (!isConfigured(context)) {
            post(cb, null);
            return;
        }
        final Context appCtx = context.getApplicationContext();
        INFLIGHT.compute(key, (k, list) -> {
            if (list == null) {
                List<Callback> nl = new CopyOnWriteArrayList<>();
                nl.add(cb);
                EXECUTOR.execute(() -> doFetch(appCtx, k));
                return nl;
            }
            list.add(cb);
            return list;
        });
    }

    private static void doFetch(Context context, String title) {
        String apiKey = getApiKey(context);
        TmdbInfo info = new TmdbInfo("", 0, System.currentTimeMillis());
        if (!TextUtils.isEmpty(apiKey)) {
            for (String host : HOSTS) {
                try {
                    String url = host + "/search/multi?api_key=" + apiKey + "&query=" + Uri.encode(title) + "&language=zh-CN&page=1&include_adult=false";
                    TmdbInfo parsed = parse(OkHttp.string(url));
                    if (parsed != null && (!parsed.year.isEmpty() || parsed.rating > 0)) {
                        info = parsed;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        putCached(context, title, info);
        List<Callback> cbs = INFLIGHT.remove(title);
        if (cbs != null) {
            for (Callback cb : cbs) post(cb, info);
        }
    }

    private static TmdbInfo parse(String json) {
        try {
            if (TextUtils.isEmpty(json)) return null;
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray results = root.getAsJsonArray("results");
            if (results == null) return new TmdbInfo("", 0, System.currentTimeMillis());
            for (JsonElement el : results) {
                JsonObject o = el.getAsJsonObject();
                String type = opt(o, "media_type");
                if (!"movie".equals(type) && !"tv".equals(type)) continue;
                String date = "movie".equals(type) ? opt(o, "release_date") : opt(o, "first_air_date");
                String year = date.length() >= 4 ? date.substring(0, 4) : "";
                double rating = 0;
                try {
                    if (o.has("vote_average") && !o.get("vote_average").isJsonNull()) {
                        rating = o.get("vote_average").getAsDouble();
                    }
                } catch (Exception ignored) {
                }
                return new TmdbInfo(year, rating, System.currentTimeMillis());
            }
        } catch (Exception ignored) {
        }
        return new TmdbInfo("", 0, System.currentTimeMillis());
    }

    private static String opt(JsonObject o, String k) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static TmdbInfo getCached(Context context, String title) {
        TmdbInfo m = MEM.get(title);
        if (m != null) return m;
        ensureDiskLoaded(context);
        return MEM.get(title);
    }

    private static synchronized void ensureDiskLoaded(Context context) {
        if (diskLoaded) return;
        diskLoaded = true;
        try {
            String json = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).getString("data", null);
            if (TextUtils.isEmpty(json)) return;
            Type type = new TypeToken<LinkedHashMap<String, TmdbInfo>>() {
            }.getType();
            LinkedHashMap<String, TmdbInfo> map = App.gson().fromJson(json, type);
            if (map == null) return;
            for (Map.Entry<String, TmdbInfo> e : map.entrySet()) {
                if (e.getValue() != null && !e.getValue().expired()) MEM.putIfAbsent(e.getKey(), e.getValue());
            }
        } catch (Exception ignored) {
        }
    }

    private static void putCached(Context context, String title, TmdbInfo info) {
        MEM.put(title, info);
        EXECUTOR.execute(() -> {
            try {
                SharedPreferences sp = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
                String json = sp.getString("data", null);
                Type type = new TypeToken<LinkedHashMap<String, TmdbInfo>>() {
                }.getType();
                LinkedHashMap<String, TmdbInfo> map = TextUtils.isEmpty(json) ? new LinkedHashMap<>() : App.gson().fromJson(json, type);
                if (map == null) map = new LinkedHashMap<>();
                map.put(title, info);
                while (map.size() > MAX_CACHE) {
                    String oldest = null;
                    long oldestTs = Long.MAX_VALUE;
                    for (Map.Entry<String, TmdbInfo> e : map.entrySet()) {
                        if (e.getValue() != null && e.getValue().ts < oldestTs) {
                            oldestTs = e.getValue().ts;
                            oldest = e.getKey();
                        }
                    }
                    if (oldest == null) break;
                    map.remove(oldest);
                }
                sp.edit().putString("data", App.gson().toJson(map)).apply();
            } catch (Exception ignored) {
            }
        });
    }

    private static void post(Callback cb, TmdbInfo info) {
        MAIN.post(() -> {
            try {
                cb.onResult(info);
            } catch (Exception ignored) {
            }
        });
    }
}
