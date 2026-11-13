package com.fongmi.android.tv.cloud.drive115;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * /files/video 返回解析（对照 Pang5 活链结构 + PY _pick_tiers 防御口径）。
 *
 * 两路候选：
 * 1. 结构化：data.definition_list 多档（每档 url + definition_note 等名字键）、
 *    data.origin_file_url 原画；
 * 2. 结构化挖不到档时，走 PY _pick_tiers 的全树防御漫游——115 返回里字幕/音轨/
 *    语言版本也带 url，混进选集点开播不动，两道卡：键名词根整枝跳过 +
 *    地址本身必须是可播形态。
 *
 * 字段全表未经真机对死：结构化键名按对照图实锤字段（definition_list /
 * origin_file_url）加同族候选键防御取值，漫游路作兜底。
 */
public class TierParser115 {

    public static class Tier {
        public final String name;
        public final String url;

        public Tier(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    public static class VideoInfo {
        /** 原画直链（origin_file_url），没有为空串 */
        public String originUrl = "";
        /** 转码多档（已按 url 去重，不含 originUrl 本身） */
        public final List<Tier> tiers = new ArrayList<>();
    }

    // 键名里带这些词根的，整个分支都不是视频流（字幕 / 音轨 / 语言版本 / 歌词）
    private static final Set<String> SKIP_TOKENS = new HashSet<>(Arrays.asList(
            "sub", "subs", "subtitle", "subtitles", "lang", "language", "languages",
            "caption", "captions", "cc", "audio", "audios", "track", "tracks",
            "lyric", "lyrics", "srt", "vtt", "ass", "ssa"));
    // 只在这些键上认 url
    private static final Set<String> URL_KEYS = new HashSet<>(Arrays.asList(
            "url", "m3u8_url", "m3u8", "play_url", "playurl",
            "video_url", "video_play_url", "path"));
    // 画质节点上一般有这些键，字幕 / 语言节点上没有
    private static final Set<String> DEF_KEYS = new HashSet<>(Arrays.asList(
            "definition", "definition_note", "definition_order", "quality",
            "defn", "vn", "resolution"));
    // 名字本身就像视频流的键，节点带这几个键就不当字幕节点处理
    private static final Set<String> MEDIA_KEYS = new HashSet<>(Arrays.asList(
            "video_url", "m3u8_url", "m3u8", "play_url", "path"));
    // 画质名从这些键上取（故意不含 lang / language，免得选出「中文」这种字幕轨名字）
    private static final String[] NAME_KEYS = {
            "definition_note", "definition", "name", "title",
            "quality", "defn", "vn", "note", "desc"};
    // 结构化档位条目里 url 的候选键（按序取第一个非空）
    private static final String[] TIER_URL_KEYS = {
            "url", "play_url", "m3u8_url", "video_url", "video_play_url", "path"};
    // 字幕 / 清单类后缀，带这种后缀的地址一律不当视频流
    private static final String[] SUB_SUFFIX = {
            ".srt", ".ass", ".ssa", ".vtt", ".sub", ".smi", ".dfxp", ".ttml", ".xml", ".json"};
    // 真视频流的地址后缀
    private static final String[] PLAY_SUFFIX = {
            ".m3u8", ".mp4", ".mkv", ".flv", ".ts", ".mov", ".avi",
            ".m4v", ".webm", ".f4v", ".wmv", ".rmvb", ".mpg", ".mpeg",
            ".m2ts", ".mts", ".vob", ".asf", ".3gp", ".rm", ".iso",
            ".dat", ".m2v", ".mpe", ".m2t", ".3g2", ".ogv", ".ogm",
            ".mod", ".tod", ".dvr-ms", ".wtv", ".tp", ".trp", ".divx",
            ".qt", ".amv", ".vro"};
    // 路径里带这些词的，是字幕 / 音轨 / 封面，不是视频流
    private static final String[] URL_SKIP_WORDS = {
            "subtitle", "sub_url", "subtitles", "/sub/", "audio",
            "/lang/", "language", "poster", "cover", "thumb", "sprite"};
    // 键名本身就是「视频/HLS 地址」的，允许地址不带后缀
    private static final Set<String> STRONG_URL_KEYS = new HashSet<>(Arrays.asList(
            "m3u8", "m3u8_url", "video_url", "video_play_url", "play_url", "playurl"));

    public static VideoInfo parse(JSONObject root) {
        VideoInfo info = new VideoInfo();
        if (root == null) return info;
        JSONObject data = root.optJSONObject("data");
        JSONObject scope = data != null ? data : root;

        // ---- 结构化路 ----
        String origin = scope.optString("origin_file_url", "").trim();
        if (isHttp(origin) && !isSubUrl(origin) && !hasSkipWord(origin)) {
            info.originUrl = origin;
        }
        JSONArray defs = scope.optJSONArray("definition_list");
        if (defs != null) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < defs.length(); i++) {
                JSONObject item = defs.optJSONObject(i);
                if (item == null || nodeSkip(item)) continue;
                String urlKey = "";
                String url = "";
                for (String k : TIER_URL_KEYS) {
                    String v = item.optString(k, "").trim();
                    if (!v.isEmpty()) {
                        urlKey = k;
                        url = v;
                        break;
                    }
                }
                if (url.isEmpty() || !looksPlayable(url, STRONG_URL_KEYS.contains(urlKey))) continue;
                if (url.equals(info.originUrl) || !seen.add(url)) continue;
                info.tiers.add(new Tier(nameOf(item), url));
            }
        }
        if (!info.tiers.isEmpty()) return info;

        // ---- 防御漫游路（PY _pick_tiers） ----
        List<Tier> walked = new ArrayList<>();
        walk(root, "", walked);
        Set<String> seen = new HashSet<>();
        for (Tier t : walked) {
            if (t.url.equals(info.originUrl)) continue;
            if (seen.add(t.url)) info.tiers.add(t);
        }
        return info;
    }

    // ---------- PY _pick_tiers 漫游移植 ----------

    private static void walk(Object node, String inheritedName, List<Tier> out) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            if (nodeSkip(obj)) return;
            String own = nameOf(obj);
            if (own.isEmpty()) own = inheritedName;
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (keySkip(k)) continue;
                Object v = obj.opt(k);
                if (v instanceof String) {
                    String kl = k.toLowerCase();
                    if (kl.equals("url") || kl.endsWith("_url") || URL_KEYS.contains(kl) || kl.equals("src")) {
                        String s = ((String) v).trim();
                        if (looksPlayable(s, STRONG_URL_KEYS.contains(kl))) {
                            out.add(new Tier(own, s));
                        }
                    }
                } else if (v instanceof JSONObject || v instanceof JSONArray) {
                    walk(v, own, out);
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                Object v = arr.opt(i);
                if (v instanceof JSONObject || v instanceof JSONArray) walk(v, inheritedName, out);
            }
        }
    }

    /** 键名按 _ / - 拆开看词根：subtitle_url、language_list、audio_track 这些都算 */
    private static boolean keySkip(String key) {
        if (key == null) return false;
        for (String tok : key.toLowerCase().replace('-', '_').split("_")) {
            if (SKIP_TOKENS.contains(tok)) return true;
        }
        return false;
    }

    /** 整个节点就是一个字幕 / 音轨 / 某个语言版本时，整枝丢掉 */
    private static boolean nodeSkip(JSONObject node) {
        boolean anySkip = false;
        boolean anyDef = false;
        boolean anyMedia = false;
        Iterator<String> keys = node.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (keySkip(k)) anySkip = true;
            String kl = k.toLowerCase();
            if (DEF_KEYS.contains(kl)) anyDef = true;
            if (MEDIA_KEYS.contains(kl)) anyMedia = true;
        }
        if (!anySkip) return false;
        if (anyDef) return false;
        if (anyMedia) return false;
        return true;
    }

    private static String nameOf(JSONObject node) {
        for (String k : NAME_KEYS) {
            Object v = node.opt(k);
            if (v instanceof String || v instanceof Number) {
                String s = clean(String.valueOf(v));
                if (!s.isEmpty()) return s;
            }
        }
        return "";
    }

    // ---------- 地址形态判定（PY _looks_playable） ----------

    private static boolean isHttp(String s) {
        return s != null && s.toLowerCase().startsWith("http");
    }

    private static boolean isSubUrl(String u) {
        String p = (u == null ? "" : u).trim().toLowerCase();
        int qi = p.indexOf('?');
        if (qi >= 0) p = p.substring(0, qi);
        int hi = p.indexOf('#');
        if (hi >= 0) p = p.substring(0, hi);
        for (String suf : SUB_SUFFIX) if (p.endsWith(suf)) return true;
        return false;
    }

    private static boolean hasSkipWord(String low) {
        for (String w : URL_SKIP_WORDS) if (low.contains(w)) return true;
        return false;
    }

    /**
     * 光看字符串判断这地址像不像一条真能播的视频流（不发请求）。
     * strong=true 表示键名本身就是「视频/HLS 地址」，允许地址不带后缀；
     * 键名只是笼统的 url 就必须是 .m3u8 / .mp4 这类。
     */
    private static boolean looksPlayable(String u, boolean strong) {
        String s = u == null ? "" : u.trim();
        String low = s.toLowerCase();
        if (!low.startsWith("http")) return false;
        String path = low;
        int qi = path.indexOf('?');
        if (qi >= 0) path = path.substring(0, qi);
        int hi = path.indexOf('#');
        if (hi >= 0) path = path.substring(0, hi);
        if (isSubUrl(low)) return false;
        if (hasSkipWord(low)) return false;
        for (String suf : PLAY_SUFFIX) if (path.endsWith(suf)) return true;
        return strong;
    }

    /** 对照 PY _clean：尖括号/空白归一 */
    private static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("[<>\\r\\n\\t]", " ").replaceAll("\\s+", " ").trim();
    }
}
