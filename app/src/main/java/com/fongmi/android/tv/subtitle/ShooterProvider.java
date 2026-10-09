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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 射手网（assrt.net）：官方 API。
 * 搜索：GET https://api.assrt.net/v1/sub/search?token={token}&q={keyword}&cnt=15
 * 需要用户在 assrt.net 注册后把 API Token 填进字幕设置。
 */
public class ShooterProvider implements SubtitleProvider {

    private static final String API = "https://api.assrt.net";
    private static final Pattern URL_HARVEST = Pattern.compile(
            "\"(https?://[^\"']+\\.(?:srt|ass|ssa|vtt|zip|rar|7z)[^\"']*)\"");

    @Override
    public String getId() {
        return "shooter";
    }

    @Override
    public String getName() {
        return "射手网";
    }

    @Override
    public boolean isAvailable() {
        return SubtitleSetting.isSrcEnabled("shooter") && !TextUtils.isEmpty(SubtitleSetting.getAssrtToken());
    }

    /** 未填 Token 时也视为"需要配置"（给 UI 展示用） */
    public static boolean needsToken() {
        return SubtitleSetting.isSrcEnabled("shooter") && TextUtils.isEmpty(SubtitleSetting.getAssrtToken());
    }

    @Override
    public List<SubtitleInfo> search(String query) {
        List<SubtitleInfo> result = new ArrayList<>();
        if (TextUtils.isEmpty(query) || !isAvailable()) return result;
        try {
            String url = API + "/v1/sub/search?token=" + Uri.encode(SubtitleSetting.getAssrtToken())
                    + "&q=" + Uri.encode(query) + "&cnt=15";
            String body = OkHttp.string(url, headers());
            if (TextUtils.isEmpty(body)) return result;
            JsonObject root = App.gson().fromJson(body, JsonObject.class);
            if (root == null || !root.has("status") || root.get("status").getAsInt() != 0) return result;
            JsonObject sub = root.has("sub") ? root.getAsJsonObject("sub") : null;
            JsonArray subs = sub != null && sub.has("subs") ? sub.getAsJsonArray("subs") : new JsonArray();
            for (JsonElement e : subs) {
                try {
                    JsonObject o = e.getAsJsonObject();
                    long id = o.has("id") ? o.get("id").getAsLong() : 0;
                    if (id <= 0) continue;
                    String name = o.has("native_name") ? o.get("native_name").getAsString() : "";
                    if (TextUtils.isEmpty(name) && o.has("videoname")) name = o.get("videoname").getAsString();
                    if (TextUtils.isEmpty(name)) name = "射手字幕 " + id;
                    String lang = mapLang(o);
                    result.add(new SubtitleInfo(name, lang, "assrt://" + id, getId()));
                    if (result.size() >= 30) break;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    /**
     * 解析详情拿真实下载地址。
     * downloadUrl 格式：assrt://{id}
     */
    public static String resolveDownloadUrl(String stored) {
        try {
            if (!stored.startsWith("assrt://")) return stored;
            String id = stored.substring("assrt://".length());
            String token = SubtitleSetting.getAssrtToken();
            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(token)) return "";
            String url = API + "/v1/sub/detail?token=" + Uri.encode(token) + "&id=" + Uri.encode(id);
            String body = OkHttp.string(url, headers());
            if (TextUtils.isEmpty(body)) return "";
            Matcher m = URL_HARVEST.matcher(body);
            if (m.find()) return m.group(1).replace("\\/", "/");
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String mapLang(JsonObject o) {
        try {
            JsonObject lang = o.has("lang") ? o.getAsJsonObject("lang") : null;
            String desc = lang != null && lang.has("desc") ? lang.get("desc").getAsString() : "";
            if (desc.contains("双语") || desc.contains("中英")) return "zh_en";
            JsonObject list = lang != null && lang.has("langlist") ? lang.getAsJsonObject("langlist") : null;
            boolean chs = hasLang(list, "langchs");
            boolean cht = hasLang(list, "langcht");
            boolean eng = hasLang(list, "langeng");
            if ((chs || cht) && eng) return "zh_en";
            if (cht) return "zh_cht";
            if (chs) return "zh_chs";
            if (eng) return "en";
            if (desc.contains("繁")) return "zh_cht";
            if (desc.contains("英")) return "en";
        } catch (Throwable ignored) {
        }
        return "zh_chs";
    }

    private static boolean hasLang(JsonObject list, String key) {
        try {
            return list != null && list.has(key) && list.get(key).getAsBoolean();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
        h.put("Accept", "application/json");
        return h;
    }
}
