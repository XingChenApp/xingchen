package com.fongmi.android.tv.subtitle;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.SubtitleSetting;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;

/**
 * OpenSubtitles REST API v1：https://api.opensubtitles.com/api/v1
 * 需要用户在设置页填写 API Key（https://www.opensubtitles.com/consumers 免费申请）。
 */
public class OpenSubtitlesProvider implements SubtitleProvider {

    private static final String BASE = "https://api.opensubtitles.com/api/v1";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    public String getId() {
        return "opensubtitles";
    }

    @Override
    public String getName() {
        return "OpenSubtitles";
    }

    @Override
    public boolean isAvailable() {
        return SubtitleSetting.isSrcEnabled("opensubtitles") && !TextUtils.isEmpty(SubtitleSetting.getOpenSubtitlesKey());
    }

    @Override
    public List<SubtitleInfo> search(String query) {
        List<SubtitleInfo> result = new ArrayList<>();
        if (TextUtils.isEmpty(query) || !isAvailable()) return result;
        try {
            String langs = toApiLangs(SubtitleSetting.getLang());
            String url = BASE + "/subtitles?query=" + Uri.encode(query) + "&languages=" + langs + "&order_by=download_count";
            String body = OkHttp.string(url, headers());
            if (TextUtils.isEmpty(body)) return result;
            JsonObject root = App.gson().fromJson(body, JsonObject.class);
            JsonArray data = root.has("data") ? root.getAsJsonArray("data") : new JsonArray();
            for (JsonElement e : data) {
                try {
                    JsonObject attr = e.getAsJsonObject().getAsJsonObject("attributes");
                    String language = attr.has("language") ? attr.get("language").getAsString() : "";
                    JsonArray files = attr.has("files") ? attr.getAsJsonArray("files") : new JsonArray();
                    for (JsonElement f : files) {
                        JsonObject file = f.getAsJsonObject();
                        long fileId = file.has("file_id") ? file.get("file_id").getAsLong() : 0;
                        String fileName = file.has("file_name") ? file.get("file_name").getAsString() : "";
                        if (fileId > 0) {
                            // downloadUrl 存 file_id，下载时再换真实链接（链接是临时的）
                            result.add(new SubtitleInfo(fileName, toInternalLang(language), "os://download/" + fileId, getId()));
                        }
                        if (result.size() >= 20) break;
                    }
                } catch (Throwable ignored) {
                }
                if (result.size() >= 20) break;
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    /**
     * 用 file_id 换取真实下载链接（POST /download）。
     * @return 真实下载 URL，失败返回空字符串
     */
    public static String resolveDownloadUrl(long fileId) {
        try {
            String key = SubtitleSetting.getOpenSubtitlesKey();
            if (TextUtils.isEmpty(key) || fileId <= 0) return "";
            JsonObject payload = new JsonObject();
            payload.addProperty("file_id", fileId);
            RequestBody body = RequestBody.create(payload.toString(), JSON);
            try (okhttp3.Response res = OkHttp.newCall(BASE + "/download", headers(), body).execute()) {
                if (res.body() == null) return "";
                String resp = res.body().string();
                if (TextUtils.isEmpty(resp)) return "";
                JsonObject root = App.gson().fromJson(resp, JsonObject.class);
                return root.has("link") ? root.get("link").getAsString() : "";
            }
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("Api-Key", SubtitleSetting.getOpenSubtitlesKey());
        h.put("User-Agent", "XingChen v1.0");
        h.put("Accept", "application/json");
        h.put("Content-Type", "application/json");
        return h;
    }

    private static String toApiLangs(String pref) {
        if ("cn_s".equals(pref)) return "zh-CN";
        if ("cn_t".equals(pref)) return "zh-TW";
        if ("en".equals(pref)) return "en";
        return "zh-CN,zh-TW,en";
    }

    private static String toInternalLang(String apiLang) {
        if (apiLang == null) return "zh_chs";
        String l = apiLang.toLowerCase();
        if (l.startsWith("zh-tw") || l.startsWith("zho-tw")) return "zh_cht";
        if (l.startsWith("zh") || l.startsWith("zho")) return "zh_chs";
        if (l.startsWith("en")) return "en";
        return "zh_chs";
    }
}
