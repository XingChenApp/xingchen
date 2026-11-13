package com.fongmi.android.tv.api.hk;

import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fongmi.android.tv.api.ThirdPartyKeys;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 海阔云剪贴板兼容客户端（对照 8.83 RuleImporterManager#getImporter packed-switch 原文）。
 *
 * 映射关系（8.83 原文）：
 * 云1 Num1  → PastebinImporter  (pastebin.com)
 * 云2 Num2  → NetCutImporter     (netcut.cn / textdb.online)
 * 云5 Num5  → CmdImporter        (cmd.im)
 * 云6 Num6  → BailanImporter     (pasteme.tyrantg.com)
 * 云7 Num7  → NotesImporter      (note.ms)
 * 云9 Num9  → TxtPadImporter     (txtpad.cn)
 * 云10 Num10 → HastebinImporter  (hassdtebin.com / hastebin.com)
 *
 * 云3、云4、云8 在 8.83 中无实现（getImporter 走 default 返回 null），此处亦不支持。
 *
 * 注意：均为第三方公开服务，接口可能变更，不保证长期可用。
 */
public class HkCloudClip {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType TEXT = MediaType.get("text/plain; charset=utf-8");

    private static final String UA = "Mozilla/5.0 (Linux; Android 15; 2407FRK8EC Build/AP3A.240617.008; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.127 Mobile Safari/537.36";

    public static String name(int clipNum) {
        switch (clipNum) {
            case 1: return "云剪贴板1";
            case 2: return "云剪贴板2";
            case 5: return "云剪贴板5";
            case 6: return "云剪贴板6";
            case 7: return "云剪贴板7";
            case 9: return "云剪贴板9";
            case 10: return "云剪贴板10";
            default: return "云剪贴板" + clipNum;
        }
    }

    /** 是否支持该编号（8.83 原文：1、2、5、6、7、9、10）。 */
    public static boolean supported(int clipNum) {
        return clipNum == 1 || clipNum == 2 || clipNum == 5 || clipNum == 6
                || clipNum == 7 || clipNum == 9 || clipNum == 10;
    }

    /** 上传规则 JSON，返回分享 URL（取件码）。 */
    public static String upload(String ruleJson, int clipNum) throws Exception {
        switch (clipNum) {
            case 1: return pastebinUpload(ruleJson);
            case 2: return netcutUpload(ruleJson);
            case 5: return cmdUpload(ruleJson);
            case 6: return pastemeUpload(ruleJson);
            case 7: return notemsUpload(ruleJson);
            case 9: return txtpadUpload(ruleJson);
            case 10: return hastebinUpload(ruleJson);
            default: throw new IllegalArgumentException("不支持的云剪贴板编号: " + clipNum);
        }
    }

    /** 用取件码（分享 URL）下载，返回规则 JSON。 */
    public static String download(String code, int clipNum) throws Exception {
        switch (clipNum) {
            case 1: return pastebinDownload(code);
            case 2: return netcutDownload(code);
            case 5: return cmdDownload(code);
            case 6: return pastemeDownload(code);
            case 7: return notemsDownload(code);
            case 9: return txtpadDownload(code);
            case 10: return hastebinDownload(code);
            default: throw new IllegalArgumentException("不支持的云剪贴板编号: " + clipNum);
        }
    }

    // ---------- 云1: pastebin.com ----------

    private static String pastebinUpload(String content) throws Exception {
        FormBody body = new FormBody.Builder()
                .add("api_dev_key", ThirdPartyKeys.PASTEBIN_DEV_KEY)
                .add("api_option", "paste")
                .add("api_paste_code", content)
                .add("api_paste_private", "1")
                .add("api_paste_expire_date", "N")
                .add("api_paste_format", "javascript")
                .build();
        String resp = post("https://pastebin.com/api/api_post.php", body, null);
        if (resp == null || resp.startsWith("Bad API")) {
            throw new Exception("Pastebin 上传失败: " + resp);
        }
        return resp.trim();
    }

    private static String pastebinDownload(String code) throws Exception {
        String id = lastPath(code);
        return get("https://pastebin.com/raw/" + id, null);
    }

    // ---------- 云2: netcut.cn ----------

    private static String netcutUpload(String content) throws Exception {
        String noteId = "hk" + System.currentTimeMillis() % 1000000;
        FormBody body = new FormBody.Builder()
                .add("note_id", noteId)
                .add("note_content", content)
                .build();
        String resp = post("https://netcut.cn/api/note2/create/", body, null);
        if (resp == null || !resp.contains("success")) {
            throw new Exception("NetCut 上传失败: " + resp);
        }
        return "https://netcut.cn/p/" + noteId;
    }

    private static String netcutDownload(String code) throws Exception {
        String id = lastPath(code);
        String resp = get("https://netcut.cn/api/note2/info/?note_id=" + id, null);
        String c = jsonString(resp, "note_content");
        if (c == null) throw new Exception("NetCut 下载失败");
        return c;
    }

    // ---------- 云5: cmd.im ----------

    private static String cmdUpload(String content) throws Exception {
        FormBody body = new FormBody.Builder()
                .add("content", content)
                .build();
        String resp = post("https://cmd.im/api/save", body, null);
        String url = jsonString(resp, "url");
        if (url == null) url = jsonString(resp, "key");
        if (url == null) throw new Exception("Cmd 上传失败: " + resp);
        return url.startsWith("http") ? url : "https://cmd.im/" + url;
    }

    private static String cmdDownload(String code) throws Exception {
        String id = lastPath(code);
        return get("https://cmd.im/" + id, null);
    }

    // ---------- 云6: pasteme.tyrantg.com ----------

    private static String pastemeUpload(String content) throws Exception {
        FormBody body = new FormBody.Builder()
                .add("content", content)
                .add("lang", "plain")
                .add("password", "")
                .build();
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Referer", "https://pasteme.tyrantg.com/");
        String resp = post("https://pasteme.tyrantg.com/api/create", body, headers);
        String id = jsonString(resp, "id");
        if (id == null) id = jsonString(resp, "key");
        if (id == null) throw new Exception("Pasteme 上传失败: " + resp);
        return "https://pasteme.tyrantg.com/" + id;
    }

    private static String pastemeDownload(String code) throws Exception {
        String id = lastPath(code);
        String resp = get("https://pasteme.tyrantg.com/api/getContent/" + id, null);
        String c = jsonString(resp, "content");
        if (c == null) throw new Exception("Pasteme 下载失败");
        return c;
    }

    // ---------- 云7: note.ms ----------

    private static String notemsUpload(String content) throws Exception {
        String note = "hk" + System.currentTimeMillis() % 1000000;
        FormBody body = new FormBody.Builder()
                .add("note", note)
                .add("content", content)
                .build();
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Referer", "https://note.ms/");
        headers.put("Origin", "https://note.ms/");
        String resp = post("https://note.ms/api/notes", body, headers);
        if (resp == null || resp.contains("error")) {
            throw new Exception("Note.ms 上传失败: " + resp);
        }
        return "https://note.ms/" + note;
    }

    private static String notemsDownload(String code) throws Exception {
        String id = lastPath(code);
        return get("https://note.ms/" + id, null);
    }

    // ---------- 云9: txtpad.cn ----------

    private static String txtpadUpload(String content) throws Exception {
        String name = "hk" + new java.text.SimpleDateFormat("HHMMdd", java.util.Locale.US).format(new java.util.Date());
        FormBody body = new FormBody.Builder()
                .add("txt_name", name)
                .add("txt_content", content)
                .add("password", "")
                .add("title", name)
                .build();
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Referer", "https://txtpad.cn/");
        String resp = post("https://a6.qikekeji.com/txt/data/save/", body, headers);
        String id = jsonString(resp, "id");
        if (id == null) id = jsonString(resp, "key");
        if (id == null) id = jsonString(resp, "txt_id");
        if (id == null) throw new Exception("TxtPad 上传失败: " + resp);
        return "https://txtpad.cn/" + id;
    }

    private static String txtpadDownload(String code) throws Exception {
        String id = lastPath(code);
        return get("https://txtpad.cn/" + id, null);
    }

    // ---------- 云10: hastebin ----------

    private static String hastebinUpload(String content) throws Exception {
        RequestBody body = RequestBody.create(content, TEXT);
        String resp = post("https://hastebin.com/documents", body, null);
        String key = jsonString(resp, "key");
        if (key == null) throw new Exception("Hastebin 上传失败: " + resp);
        return "https://hastebin.com/" + key;
    }

    private static String hastebinDownload(String code) throws Exception {
        String id = lastPath(code);
        if (code.contains("hassdtebin.com")) {
            return get("https://hassdtebin.com/raw/" + id, null);
        }
        return get("https://hastebin.com/raw/" + id, null);
    }

    // ---------- HTTP helpers ----------

    private static OkHttpClient client() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    private static String post(String url, RequestBody body, java.util.Map<String, String> headers) throws Exception {
        Request.Builder b = new Request.Builder().url(url).post(body)
                .header("User-Agent", UA);
        if (headers != null) {
            for (java.util.Map.Entry<String, String> e : headers.entrySet()) {
                b.header(e.getKey(), e.getValue());
            }
        }
        try (Response r = client().newCall(b.build()).execute()) {
            ResponseBody rb = r.body();
            if (!r.isSuccessful() || rb == null) {
                throw new Exception("HTTP " + r.code() + " " + url);
            }
            return rb.string();
        }
    }

    private static String get(String url, java.util.Map<String, String> headers) throws Exception {
        Request.Builder b = new Request.Builder().url(url).get()
                .header("User-Agent", UA);
        if (headers != null) {
            for (java.util.Map.Entry<String, String> e : headers.entrySet()) {
                b.header(e.getKey(), e.getValue());
            }
        }
        try (Response r = client().newCall(b.build()).execute()) {
            ResponseBody rb = r.body();
            if (!r.isSuccessful() || rb == null) {
                throw new Exception("HTTP " + r.code() + " " + url);
            }
            return rb.string();
        }
    }

    private static String lastPath(String url) {
        if (url == null) return "";
        String s = url.trim();
        int q = s.indexOf('?');
        if (q > 0) s = s.substring(0, q);
        int h = s.indexOf('#');
        if (h > 0) s = s.substring(0, h);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private static String jsonString(String json, String key) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
        }
        Pattern p2 = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
        Matcher m2 = p2.matcher(json);
        if (m2.find()) return m2.group(1);
        return null;
    }
}
