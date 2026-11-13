package com.fongmi.android.tv.tts;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.fongmi.android.tv.App;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 音色目录：线上拉取（微软音色列表接口）＋内置名单兜底。
 * 线上是权威（有多少收多少）；拉不到时用内置（约 30 个中文音色）。
 * 默认音色与用户选择存 TTS 私有偏好。
 */
public class EdgeVoiceCatalog {

    private static final String PREFS = "tts_voice";
    private static final String KEY_DEFAULT = "default_voice";
    public static final String DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural";

    private static final String VOICES_URL =
            "https://api.msedgeservices.com/tts/cognitiveservices/voices/list";

    /** 试听文案（固定一小段）。 */
    public static final String TRIAL_TEXT =
            "夜之雾，灯之火。观澜把最后一盏灯挂到檐下，雾里隐约晃出一点船影。";

    private static SharedPreferences prefs() {
        return App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String defaultVoice() {
        return prefs().getString(KEY_DEFAULT, DEFAULT_VOICE);
    }

    public static void setDefaultVoice(String voiceId) {
        if (!TextUtils.isEmpty(voiceId)) {
            prefs().edit().putString(KEY_DEFAULT, voiceId).apply();
        }
    }

    /** 内置兜底名单（接口拉不到时用）。 */
    public static List<EdgeVoice> builtIn() {
        List<EdgeVoice> list = new ArrayList<>();
        // 普通话女声
        list.add(new EdgeVoice("zh-CN-XiaoxiaoNeural", "晓晓", "zh-CN", "F", "温润女声", false));
        list.add(new EdgeVoice("zh-CN-XiaoyiNeural", "晓伊", "zh-CN", "F", "邻家女声", false));
        list.add(new EdgeVoice("zh-CN-XiaohanNeural", "晓涵", "zh-CN", "F", "温柔知性", false));
        list.add(new EdgeVoice("zh-CN-XiaomengNeural", "晓梦", "zh-CN", "F", "清亮少女", false));
        list.add(new EdgeVoice("zh-CN-XiaomoNeural", "晓墨", "zh-CN", "F", "成熟御姐", false));
        list.add(new EdgeVoice("zh-CN-XiaoqiuNeural", "晓秋", "zh-CN", "F", "知性沉稳", false));
        list.add(new EdgeVoice("zh-CN-XiaoruiNeural", "晓睿", "zh-CN", "F", "干练女声", false));
        list.add(new EdgeVoice("zh-CN-XiaoshuangNeural", "晓双", "zh-CN", "F", "活泼开朗", false));
        list.add(new EdgeVoice("zh-CN-XiaoyanNeural", "晓颜", "zh-CN", "F", "温柔小姐姐", false));
        list.add(new EdgeVoice("zh-CN-XiaoyouNeural", "晓悠", "zh-CN", "F", "稚嫩童声", false));
        list.add(new EdgeVoice("zh-CN-XiaozhenNeural", "晓臻", "zh-CN", "F", "大气女声", false));
        // 普通话男声
        list.add(new EdgeVoice("zh-CN-YunjianNeural", "云健", "zh-CN", "M", "低沉磁性·讲故事感", true));
        list.add(new EdgeVoice("zh-CN-YunyangNeural", "云扬", "zh-CN", "M", "播音男声", false));
        list.add(new EdgeVoice("zh-CN-YunxiNeural", "云希", "zh-CN", "M", "阳光少年", false));
        list.add(new EdgeVoice("zh-CN-YunxiaNeural", "云夏", "zh-CN", "M", "清朗少年", false));
        list.add(new EdgeVoice("zh-CN-YunhaoNeural", "云皓", "zh-CN", "M", "温暖青年", false));
        list.add(new EdgeVoice("zh-CN-YunfengNeural", "云枫", "zh-CN", "M", "磁性大叔·讲故事感", true));
        list.add(new EdgeVoice("zh-CN-YunyeNeural", "云野", "zh-CN", "M", "豪爽男声·讲故事感", true));
        list.add(new EdgeVoice("zh-CN-YunzeNeural", "云泽", "zh-CN", "M", "沉稳男声", false));
        // 方言
        list.add(new EdgeVoice("zh-CN-liaoning-XiaobeiNeural", "晓北（辽宁）", "zh-CN", "F", "辽宁话女声", false));
        list.add(new EdgeVoice("zh-CN-shaanxi-XiaoniNeural", "晓妮（陕西）", "zh-CN", "F", "陕西话女声", false));
        list.add(new EdgeVoice("zh-CN-sichuan-YunxiNeural", "云希（四川）", "zh-CN", "M", "四川话男声", false));
        // 粤语
        list.add(new EdgeVoice("zh-HK-HiuMaanNeural", "晓曼（香港）", "zh-HK", "F", "粤语女声", false));
        list.add(new EdgeVoice("zh-HK-WanLungNeural", "云龙（香港）", "zh-HK", "M", "粤语男声", false));
        list.add(new EdgeVoice("zh-HK-HiuGaaiNeural", "晓佳（香港）", "zh-HK", "F", "粤语女声", false));
        // 台腔
        list.add(new EdgeVoice("zh-TW-HsiaoChenNeural", "晓辰（台湾）", "zh-TW", "F", "台湾女声", false));
        list.add(new EdgeVoice("zh-TW-YunJheNeural", "云哲（台湾）", "zh-TW", "M", "台湾男声", false));
        list.add(new EdgeVoice("zh-TW-HsiaoYuNeural", "晓雨（台湾）", "zh-TW", "F", "台湾女声", false));
        return list;
    }

    /**
     * 解析线上音色列表 JSON（纯函数，可离线单测）。
     * 返回：中文音色；与内置同 id 的保留内置画像，未知的按性别给通用画像。
     */
    public static List<EdgeVoice> parseRemote(String json) {
        Map<String, EdgeVoice> byId = new LinkedHashMap<>();
        for (EdgeVoice v : builtIn()) byId.put(v.id, v);
        if (TextUtils.isEmpty(json)) return new ArrayList<>(byId.values());
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String shortName = o.optString("ShortName", "");
                if (TextUtils.isEmpty(shortName)) continue;
                String locale = o.optString("Locale", "");
                if (!shortName.startsWith("zh")) continue;
                if (byId.containsKey(shortName)) continue;
                EdgeVoice v = new EdgeVoice();
                v.id = shortName;
                v.locale = locale;
                String g = o.optString("Gender", "");
                v.gender = "Male".equalsIgnoreCase(g) ? "M" : "F";
                v.name = guessName(o.optString("FriendlyName", ""), shortName);
                v.portrait = portraitFor(locale, v.gender);
                byId.put(shortName, v);
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(byId.values());
    }

    private static String guessName(String friendly, String shortName) {
        if (!TextUtils.isEmpty(friendly)) {
            // "Microsoft Xiaoxiao Online ..." → 取第二段
            String[] parts = friendly.split("\\s+");
            if (parts.length >= 2) return parts[1];
        }
        int dash = shortName.lastIndexOf('-');
        String tail = dash > 0 ? shortName.substring(dash + 1) : shortName;
        return tail.replace("Neural", "");
    }

    private static String portraitFor(String locale, String gender) {
        String base = "M".equals(gender) ? "男声" : "女声";
        if (locale.startsWith("zh-HK")) return "粤语" + base;
        if (locale.startsWith("zh-TW")) return "台湾" + base;
        return "普通话" + base;
    }

    /** 拉取线上目录（阻塞调用，调方自开线程）；失败返回内置名单。 */
    public static List<EdgeVoice> fetchRemote() {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            Request req = new Request.Builder().url(VOICES_URL)
                    .header("User-Agent", EdgeTtsClient.EDGE_UA).get().build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    List<EdgeVoice> list = parseRemote(resp.body().string());
                    if (!list.isEmpty()) return list;
                }
            }
        } catch (Throwable ignored) {
        }
        return builtIn();
    }

    public static EdgeVoice find(List<EdgeVoice> list, String id) {
        if (list == null || TextUtils.isEmpty(id)) return null;
        for (EdgeVoice v : list) if (id.equals(v.id)) return v;
        return null;
    }
}
