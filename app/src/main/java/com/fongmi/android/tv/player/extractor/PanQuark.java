package com.fongmi.android.tv.player.extractor;

import android.net.Uri;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.Source;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.Iterator;

/**
 * 夸克网盘分享链接解析与播放。
 * <p>
 * 支持 https://pan.quark.cn/s/xxxx（提取码可放在链接尾部 ?pwd= 提取码）。
 * 详情页的分享链接会被 {@link Parser} 展开成选集（每个视频文件一集），点击后由本 extractor 取直链播放。
 * 取直链走夸克分享页的匿名接口（sharepage/token），无需登录。
 */
public class PanQuark implements Source.Extractor {

    private static final String UA = "Mozilla/5.0 (Linux; Android 13; 2304FPN6DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 quark-cloud-drive/6.0.0";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String API_TOKEN = "https://drive-h.quark.cn/1/clouddrive/share/sharepage/token";
    private static final String API_DETAIL = "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/detail";
    private static final String API_PREVIEW = "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/video_preview";
    private static final Pattern SHARE_URL = Pattern.compile("https?://(?:[a-z0-9-]+\\.)?quark\\.cn/s/([A-Za-z0-9]+)");
    private static final List<String> VIDEO_EXT = Arrays.asList(
            "mp4", "mkv", "avi", "rmvb", "rm", "mov", "wmv", "flv", "webm",
            "m3u8", "ts", "m2ts", "mpg", "mpeg", "3gp", "f4v", "asf", "vob");

    public static boolean isShareUrl(String url) {
        return url != null && SHARE_URL.matcher(url).find();
    }

    @Override
    public boolean match(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        String host = String.valueOf(uri.getHost()).toLowerCase(Locale.US);
        if (!host.equals("quark.cn") && !host.endsWith(".quark.cn")) return false;
        return String.valueOf(uri.getPath()).startsWith("/s/");
    }

    @Override
    public String fetch(Result result) throws Exception {
        String playable = fetch(result.getUrl().v());
        Map<String, String> headers = result.getHeader();
        headers.put("User-Agent", UA);
        headers.put("Referer", "https://pan.quark.cn/");
        result.setHeader(headers);
        return playable;
    }

    @Override
    public String fetch(String url) throws Exception {
        ShareInfo info = parse(url);
        if (info.pwdId.isEmpty()) throw new ExtractException("无法解析夸克分享链接");
        String stoken = fetchToken(info.pwdId, info.passcode);
        if (info.fid.isEmpty()) {
            List<ShareFile> files = listFiles(info.pwdId, stoken, "0");
            if (files.isEmpty()) throw new ExtractException("该夸克分享中没有视频文件");
            if (files.size() > 1) throw new ExtractException("该夸克分享包含多个视频，请从选集中选择播放");
            info.fid = files.get(0).fid;
            info.fidToken = files.get(0).fidToken;
        }
        return videoPreview(info, stoken);
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }

    static ShareInfo parse(String url) {
        ShareInfo info = new ShareInfo();
        if (url == null) return info;
        Matcher m = SHARE_URL.matcher(url);
        if (m.find()) info.pwdId = m.group(1);
        try {
            Uri uri = Uri.parse(url);
            String pwd = uri.getQueryParameter("pwd");
            if (pwd != null) info.passcode = pwd;
            String fid = uri.getQueryParameter("quarkfid");
            if (fid != null) info.fid = fid;
            String fidToken = uri.getQueryParameter("quarkfidtoken");
            if (fidToken != null) info.fidToken = fidToken;
        } catch (Throwable ignored) {
        }
        return info;
    }

    static String fetchToken(String pwdId, String passcode) throws Exception {
        Map<String, String> headers = baseHeaders();
        headers.put("Content-Type", "application/json");
        JSONObject req = new JSONObject();
        req.put("pwd_id", pwdId);
        req.put("passcode", passcode);
        RequestBody body = RequestBody.create(req.toString(), JSON);
        String resp;
        try (Response res = OkHttp.newCall(API_TOKEN, headers, body).execute()) {
            resp = res.body().string();
        }
        JSONObject obj = new JSONObject(resp);
        if (obj.optInt("status", -1) != 200 || obj.optInt("code", -1) != 0) {
            String msg = obj.optString("message", "");
            if (msg.contains("passcode") || msg.contains("提取码") || msg.contains("密码")) {
                throw new ExtractException(passcode.isEmpty()
                        ? "该夸克分享需要提取码（请在链接后添加 ?pwd=提取码）"
                        : "夸克提取码错误或已失效");
            }
            throw new ExtractException("夸克分享无效或已失效" + (msg.isEmpty() ? "" : "：" + msg));
        }
        JSONObject data = obj.optJSONObject("data");
        String stoken = data == null ? "" : data.optString("stoken", "");
        if (stoken.isEmpty()) throw new ExtractException("夸克分享：获取访问令牌失败");
        return stoken;
    }

    static List<ShareFile> listFiles(String pwdId, String stoken, String pdirFid) throws Exception {
        List<ShareFile> files = new ArrayList<>();
        int page = 1;
        while (files.size() < 500) {
            String api = API_DETAIL + "?pwd_id=" + Uri.encode(pwdId)
                    + "&stoken=" + Uri.encode(stoken)
                    + "&pdir_fid=" + Uri.encode(pdirFid)
                    + "&_page=" + page + "&_size=50&_sort=file_type:asc,updated_at:desc";
            String resp;
            try (Response res = OkHttp.newCall(api, baseHeaders()).execute()) {
                resp = res.body().string();
            }
            JSONObject obj = new JSONObject(resp);
            JSONObject data = obj.optJSONObject("data");
            JSONArray list = data == null ? null : data.optJSONArray("list");
            if (list == null || list.length() == 0) break;
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i);
                if (o == null) continue;
                if ("dir".equals(o.optString("file_type", ""))) {
                    if (pdirFid.equals("0")) {
                        files.addAll(listFiles(pwdId, stoken, o.optString("fid", "")));
                    }
                    continue;
                }
                ShareFile f = ShareFile.from(o);
                if (f != null && f.isVideo()) files.add(f);
            }
            if (list.length() < 50) break;
            page++;
        }
        return files;
    }

    static String videoPreview(ShareInfo info, String stoken) throws Exception {
        String api = API_PREVIEW + "?fid=" + Uri.encode(info.fid)
                + "&fid_token=" + Uri.encode(info.fidToken)
                + "&stoken=" + Uri.encode(stoken)
                + "&pwd_id=" + Uri.encode(info.pwdId);
        String resp;
        try (Response res = OkHttp.newCall(api, baseHeaders()).execute()) {
            resp = res.body().string();
        }
        JSONObject obj = new JSONObject(resp);
        if (obj.optInt("status", -1) != 200 || obj.optInt("code", -1) != 0) {
            throw new ExtractException("夸克取播放地址失败：" + obj.optString("message", "未知错误"));
        }
        String play = findStreamUrl(obj);
        if (play.isEmpty()) throw new ExtractException("夸克分享：该文件暂无可播放的视频地址");
        SpiderDebug.log("panquark", "preview %s -> %s", info.fid, play);
        return play;
    }

    private static String findStreamUrl(JSONObject obj) {
        String best = "";
        java.util.ArrayDeque<Object> stack = new java.util.ArrayDeque<>();
        stack.push(obj);
        while (!stack.isEmpty()) {
            Object cur = stack.pop();
            if (cur instanceof JSONObject) {
                JSONObject o = (JSONObject) cur;
                for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                    String key = it.next();
                    Object v = o.opt(key);
                    if (v instanceof String) {
                        String s = (String) v;
                        String lower = key.toLowerCase(Locale.US);
                        if (lower.contains("thumbnail") || lower.contains("cover") || lower.contains("poster")) continue;
                        if (s.contains(".m3u8")) return s;
                        if (s.endsWith(".mp4") && best.isEmpty()) best = s;
                    } else if (v instanceof JSONObject || v instanceof JSONArray) {
                        stack.push(v);
                    }
                }
            } else if (cur instanceof JSONArray) {
                JSONArray a = (JSONArray) cur;
                for (int i = 0; i < a.length(); i++) stack.push(a.opt(i));
            }
        }
        return best;
    }

    private static Map<String, String> baseHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Referer", "https://pan.quark.cn/");
        headers.put("Accept", "application/json, text/plain, */*");
        return headers;
    }

    static class ShareInfo {
        String pwdId = "";
        String passcode = "";
        String fid = "";
        String fidToken = "";
    }

    static class ShareFile {
        String fid = "";
        String fidToken = "";
        String name = "";
        long size = 0;

        static ShareFile from(JSONObject o) {
            try {
                ShareFile f = new ShareFile();
                f.fid = o.optString("fid", "");
                f.fidToken = o.optString("fid_token", "");
                f.name = o.optString("file_name", "");
                f.size = o.optLong("size", 0);
                return f;
            } catch (Throwable ignored) {
                return null;
            }
        }

        boolean isVideo() {
            if (size <= 0) return false;
            int dot = name.lastIndexOf('.');
            if (dot < 0) return false;
            return VIDEO_EXT.contains(name.substring(dot + 1).toLowerCase(Locale.US));
        }
    }

    public record Parser(String url) implements Callable<List<Episode>> {

        public static boolean match(String url) {
            return PanQuark.isShareUrl(url);
        }

        public static Parser get(String url) {
            return new Parser(url);
        }

        @Override
        public List<Episode> call() {
            List<Episode> episodes = new ArrayList<>();
            try {
                ShareInfo info = PanQuark.parse(url);
                if (info.pwdId.isEmpty()) return episodes;
                String stoken = PanQuark.fetchToken(info.pwdId, info.passcode);
                List<ShareFile> files = PanQuark.listFiles(info.pwdId, stoken, "0");
                files.sort(NATURAL);
                for (ShareFile f : files) {
                    String token = "https://pan.quark.cn/s/" + info.pwdId
                            + "?pwd=" + Uri.encode(info.passcode)
                            + "&quarkfid=" + Uri.encode(f.fid)
                            + "&quarkfidtoken=" + Uri.encode(f.fidToken);
                    episodes.add(Episode.create(f.name, token));
                }
                if (episodes.isEmpty()) {
                    episodes.add(Episode.create("该夸克分享中没有视频文件", url));
                }
            } catch (Throwable e) {
                SpiderDebug.log("panquark", "parser error=%s", e.getMessage());
                String reason = e.getMessage() == null ? "解析失败" : e.getMessage();
                episodes.add(Episode.create("夸克分享" + reason, url));
            }
            return episodes;
        }
    }

    static final Comparator<ShareFile> NATURAL = (a, b) -> compareNatural(a.name, b.name);

    static int compareNatural(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int ia = i, jb = j;
                while (ia < a.length() && Character.isDigit(a.charAt(ia))) ia++;
                while (jb < b.length() && Character.isDigit(b.charAt(jb))) jb++;
                long na = Long.parseLong(a.substring(i, ia));
                long nb = Long.parseLong(b.substring(j, jb));
                if (na != nb) return Long.compare(na, nb);
                i = ia;
                j = jb;
            } else {
                if (ca != cb) return Character.compare(ca, cb);
                i++;
                j++;
            }
        }
        return Integer.compare(a.length(), b.length());
    }
}
