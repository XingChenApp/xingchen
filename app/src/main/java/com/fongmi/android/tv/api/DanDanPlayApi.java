package com.fongmi.android.tv.api;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 弹弹play（dandanplay）弹幕源：经公共代理访问官方 v2 接口。
 * 搜索番剧 -> 取分集 -> 拉取评论 -> 转 Bilibili XML -> 缓存为本地文件。
 * 所有网络操作在后台线程执行，结果回调到主线程。
 *
 * 传输策略：先走 App 共享 client（尊重用户 DoH 设置 + DNS 清洗），失败则
 * 用一个干净的 stock client（系统 DNS、默认配置）重试一次，覆盖用户
 * DoH/DNS 配置本身损坏导致解析失败的情况。回调通过 networkError 区分
 * "网络失败" 与 "接口返回空"，以便 UI 给出可重试的错误提示。
 */
public class DanDanPlayApi {

    private static final String PROXY = "https://api.danmaku.weeblify.app/ddp/v1?path=";
    private static final long TIMEOUT_MS = 15000;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static volatile OkHttpClient plainClient;

    public static class Anime {
        public final long animeId;
        public final String title;

        public Anime(long animeId, String title) {
            this.animeId = animeId;
            this.title = title == null ? "" : title;
        }
    }

    public static class Episode {
        public final long episodeId;
        public final String title;

        public Episode(long episodeId, String title) {
            this.episodeId = episodeId;
            this.title = title == null ? "" : title;
        }
    }

    public interface AnimeCallback {
        void onResult(List<Anime> items, boolean networkError);
    }

    public interface EpisodeCallback {
        void onResult(List<Episode> items, boolean networkError);
    }

    public interface FileCallback {
        void onDone(File file);
    }

    /**
     * GET with fallback transport. Returns null only when both attempts fail,
     * so callers can tell a network failure apart from an empty API response.
     */
    private static String doGet(String url) {
        String body = OkHttp.string(url, TIMEOUT_MS);
        if (!TextUtils.isEmpty(body)) return body;
        try {
            if (plainClient == null) {
                plainClient = new OkHttpClient.Builder()
                        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .build();
            }
            Request request = new Request.Builder().url(url).build();
            try (Response res = plainClient.newCall(request).execute()) {
                if (res.isSuccessful() && res.body() != null) {
                    String s = res.body().string();
                    if (!TextUtils.isEmpty(s)) return s;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 按关键词搜索番剧 */
    public static void searchAnime(String keyword, AnimeCallback callback) {
        EXECUTOR.execute(() -> {
            List<Anime> out = new ArrayList<>();
            boolean networkError = false;
            String url = PROXY + Uri.encode("/v2/search/anime?keyword=" + keyword);
            String body = doGet(url);
            if (body == null) {
                networkError = true;
            } else {
                try {
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    JsonArray animes = root.has("animes") ? root.getAsJsonArray("animes") : null;
                    if (animes != null) {
                        for (JsonElement e : animes) {
                            JsonObject o = e.getAsJsonObject();
                            long id = o.has("animeId") ? o.get("animeId").getAsLong() : 0;
                            String title = o.has("animeTitle") ? o.get("animeTitle").getAsString() : "";
                            if (id > 0 && !TextUtils.isEmpty(title)) out.add(new Anime(id, title));
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            List<Anime> result = out;
            boolean failed = networkError;
            App.post(() -> callback.onResult(result, failed));
        });
    }

    /** 取番剧分集列表 */
    public static void getEpisodes(long animeId, EpisodeCallback callback) {
        EXECUTOR.execute(() -> {
            List<Episode> out = new ArrayList<>();
            boolean networkError = false;
            String url = PROXY + Uri.encode("/v2/bangumi/" + animeId);
            String body = doGet(url);
            if (body == null) {
                networkError = true;
            } else {
                try {
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    JsonObject bangumi = root.has("bangumi") ? root.getAsJsonObject("bangumi") : null;
                    JsonArray episodes = bangumi != null && bangumi.has("episodes") ? bangumi.getAsJsonArray("episodes") : null;
                    if (episodes != null) {
                        for (JsonElement e : episodes) {
                            JsonObject o = e.getAsJsonObject();
                            long id = o.has("episodeId") ? o.get("episodeId").getAsLong() : 0;
                            String title = o.has("episodeTitle") ? o.get("episodeTitle").getAsString() : "";
                            if (id > 0) out.add(new Episode(id, title));
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            List<Episode> result = out;
            boolean failed = networkError;
            App.post(() -> callback.onResult(result, failed));
        });
    }

    /** 拉取分集弹幕评论，转 XML 并缓存为本地文件 */
    public static void downloadDanmaku(long episodeId, String name, FileCallback callback) {
        EXECUTOR.execute(() -> {
            File file = null;
            String url = PROXY + Uri.encode("/v2/comment/" + episodeId + "?from=0&withRelated=true&chConvert=0");
            String body = doGet(url);
            if (body != null) {
                try {
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    JsonArray comments = root.has("comments") ? root.getAsJsonArray("comments") : null;
                    if (comments != null && comments.size() > 0) {
                        file = new File(App.get().getCacheDir(), "dandanplay_" + episodeId + ".xml");
                        writeXml(file, comments);
                    }
                } catch (Throwable ignored) {
                    file = null;
                }
            }
            File result = file;
            App.post(() -> callback.onDone(result));
        });
    }

    private static void writeXml(File file, JsonArray comments) throws Exception {
        StringBuilder sb = new StringBuilder(comments.size() * 64 + 64);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><i>");
        for (JsonElement e : comments) {
            JsonObject o = e.getAsJsonObject();
            String p = o.has("p") ? o.get("p").getAsString() : "";
            String m = o.has("m") ? o.get("m").getAsString() : "";
            if (TextUtils.isEmpty(m)) continue;
            String[] parts = p.split(",");
            if (parts.length < 3) continue;
            String time = parts[0];
            String mode = parts[1];
            String color;
            try {
                color = String.valueOf(Integer.parseInt(parts[2]));
            } catch (Throwable t) {
                color = "16777215";
            }
            sb.append("<d p=\"").append(time).append(',').append(mode).append(",25,").append(color).append(",0,0,,0\">")
                    .append(escape(m)).append("</d>");
        }
        sb.append("</i>");
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
