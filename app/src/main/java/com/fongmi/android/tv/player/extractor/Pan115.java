package com.fongmi.android.tv.player.extractor;

import android.net.Uri;
import android.util.Base64;

import androidx.collection.ArrayMap;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.PanAuth;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;

import okhttp3.Response;

/**
 * 115 分享链接解析与播放。
 * <p>
 * 支持 https://115.com/s/xxxx / https://115cdn.com/s/xxxx / https://anxia.com/s/xxxx（提取码可放在 ?password= 或 #fragment）。
 * 详情页的分享链接会被 {@link Parser} 展开成选集（每个视频文件一集），点击后由本 extractor 取直链播放。
 * 取直链需要 115 登录态：优先读应用偏好 pan115_cookie，其次复用 115 网盘爬虫的 115_cookie.json。
 */
public class Pan115 implements Source.Extractor {

    private static final String UA_115 = "Mozilla/5.0 115Browser/27.0.5.7";
    private static final String UA_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final String API_SNAP = "https://webapi.115.com/share/snap";
    private static final String API_DOWNURL = "https://proapi.115.com/app/chrome/downurl";
    private static final Pattern SHARE_URL = Pattern.compile("https?://(?:www\\.)?(?:115\\.com|115cdn\\.com|anxia\\.com)/s/([^/?#\\s]+)");
    private static final List<String> VIDEO_EXT = Arrays.asList(
            "mp4", "mkv", "avi", "rmvb", "rm", "mov", "wmv", "flv", "webm",
            "m3u8", "ts", "m2ts", "mpg", "mpeg", "3gp", "f4v", "asf", "vob");
    private static final Pattern SHARE_CODE_TEXT =
            Pattern.compile("(?:提取码|访问码|密码)\\s*(?:[:：]\\s*)?([A-Za-z0-9]{4})(?![A-Za-z0-9])");
    private static final int MAX_SHARE_DEPTH = 8;

    // 分享错误文案四象限：需要访问码 / 访问码错误 / 分享已失效 / 检测失败（不确定）
    private static final String MSG_NEED_CODE = "该 115 分享需要访问码（请在链接后添加 ?password=访问码）";
    private static final String MSG_BAD_CODE = "115 访问码错误或已失效";
    private static final String MSG_BAD_SHARE = "该 115 分享已失效或被取消";
    private static final String MSG_UNCERTAIN = "115 分享检测失败，请稍后重试";

    public static boolean isShareUrl(String url) {
        return url != null && SHARE_URL.matcher(url).find();
    }

    @Override
    public boolean match(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        String host = String.valueOf(uri.getHost()).toLowerCase(Locale.US);
        if (!host.equals("115.com") && !host.equals("www.115.com") && !host.equals("115cdn.com")
                && !host.equals("anxia.com") && !host.equals("www.anxia.com")) return false;
        return String.valueOf(uri.getPath()).startsWith("/s/");
    }

    @Override
    public String fetch(Result result) throws Exception {
        String playable = fetch(result.getUrl().v());
        Map<String, String> headers = result.getHeader();
        headers.put("User-Agent", UA_115);
        headers.put("Referer", "https://115.com/");
        String cookie = getCookie();
        if (!cookie.isEmpty()) headers.put("Cookie", cookie);
        result.setHeader(headers);
        return playable;
    }

    @Override
    public String fetch(String url) throws Exception {
        ShareInfo info = parse(url);
        if (info.shareCode.isEmpty()) throw new ExtractException("无法解析 115 分享链接");
        String pickcode = info.pickcode;
        if (pickcode.isEmpty()) {
            List<ShareFile> files = listFiles(info.shareCode, info.receiveCode);
            ShareFile target = null;
            if (!info.fid.isEmpty()) {
                for (ShareFile f : files) if (info.fid.equals(f.fid)) target = f;
            } else if (files.size() == 1) {
                target = files.get(0);
            } else if (files.isEmpty()) {
                throw new ExtractException("该 115 分享中没有视频文件");
            } else {
                throw new ExtractException("该 115 分享包含多个视频，请从选集中选择播放");
            }
            if (target == null) throw new ExtractException("在 115 分享中找不到该文件");
            pickcode = target.pickcode.isEmpty() ? target.fid : target.pickcode;
        }
        return downloadUrl(pickcode);
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
        if (m.find()) info.shareCode = m.group(1);
        info.receiveCode = queryParam(url, "password");
        info.fid = queryParam(url, "115fid");
        info.pickcode = queryParam(url, "115pc");
        if (info.receiveCode.isEmpty()) {
            int hash = url.indexOf('#');
            if (hash >= 0) {
                String frag = url.substring(hash + 1);
                int amp = frag.indexOf('&');
                if (amp >= 0) frag = frag.substring(0, amp);
                int sp = indexOfSpace(frag);
                if (sp >= 0) frag = frag.substring(0, sp);
                if (frag.matches("[A-Za-z0-9]{4}")) info.receiveCode = frag;
            }
        }
        if (info.receiveCode.isEmpty()) {
            Matcher pm = SHARE_CODE_TEXT.matcher(url);
            if (pm.find()) info.receiveCode = pm.group(1);
        }
        return info;
    }

    private static String queryParam(String url, String key) {
        try {
            int q = url.indexOf('?');
            if (q < 0) return "";
            int end = url.indexOf('#', q);
            String query = url.substring(q + 1, end >= 0 ? end : url.length());
            for (String part : query.split("&")) {
                int eq = part.indexOf('=');
                if (eq > 0 && part.substring(0, eq).equals(key)) {
                    String v = Uri.decode(part.substring(eq + 1));
                    int sp = indexOfSpace(v);
                    return sp >= 0 ? v.substring(0, sp) : v;
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static int indexOfSpace(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') return i;
        }
        return -1;
    }

    static List<ShareFile> listFiles(String shareCode, String receiveCode) throws Exception {
        List<ShareFile> files = new ArrayList<>();
        listDir(shareCode, receiveCode, "", files, new HashSet<String>(), 0);
        SpiderDebug.log("pan115", "share %s files=%s", shareCode, files.size());
        return files;
    }

    private static void listDir(String shareCode, String receiveCode, String cid, List<ShareFile> files, Set<String> visited, int depth) throws Exception {
        if (depth > MAX_SHARE_DEPTH || !visited.add(cid)) return;
        int offset = 0;
        int limit = 100;
        while (files.size() < 500) {
            String api = API_SNAP + "?share_code=" + Uri.encode(shareCode)
                    + "&receive_code=" + Uri.encode(receiveCode)
                    + "&offset=" + offset + "&limit=" + limit + "&cid=" + Uri.encode(cid);
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", UA_WEB);
            headers.put("Referer", "https://115cdn.com/s/" + shareCode + "?password=" + receiveCode + "&");
            headers.put("X-Requested-With", "XMLHttpRequest");
            headers.put("Accept", "application/json, text/plain, */*");
            String body;
            int httpCode;
            try (Response res = OkHttp.newCall(api, headers).execute()) {
                httpCode = res.code();
                body = res.body() == null ? "" : res.body().string();
            } catch (IOException e) {
                throw new ExtractException(MSG_UNCERTAIN);
            }
            if (httpCode < 200 || httpCode >= 300) throw new ExtractException(MSG_UNCERTAIN);
            JSONObject obj;
            try {
                obj = new JSONObject(body);
            } catch (JSONException e) {
                throw new ExtractException(MSG_UNCERTAIN);
            }
            if (!obj.optBoolean("state", false)) {
                String error = obj.optString("error", "");
                int errno = obj.optInt("errno", obj.optInt("code", 0));
                throw new ExtractException(shareErrorMessage(error, errno, receiveCode.isEmpty()));
            }
            JSONObject data = obj.optJSONObject("data");
            if (data == null) break;
            JSONArray list = data.optJSONArray("list");
            if (list == null || list.length() == 0) break;
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i);
                if (o == null) continue;
                if (ShareFile.isDir(o)) {
                    if (depth < MAX_SHARE_DEPTH) {
                        listDir(shareCode, receiveCode, ShareFile.dirFid(o), files, visited, depth + 1);
                    }
                    continue;
                }
                ShareFile f = ShareFile.from(o);
                if (f != null && f.isVideo()) files.add(f);
            }
            if (list.length() < limit) break;
            offset += limit;
        }
    }

    /**
     * 分享错误文案四象限：需要访问码 / 访问码错误 / 分享已失效 / 检测失败（不确定）。
     * 判定口径= lanmo 的文本判定（error 含「访问码」）＋我们的 errno 判定（4100012/4100008）合并。
     */
    static String shareErrorMessage(String error, int errno, boolean needCode) {
        if (isPasswordError(error, errno)) return needCode ? MSG_NEED_CODE : MSG_BAD_CODE;
        return MSG_BAD_SHARE + (error.isEmpty() ? "" : "：" + error);
    }

    private static boolean isPasswordError(String error, int errno) {
        if (errno == 4100012 || errno == 4100008) return true;
        String lower = error.toLowerCase(Locale.US);
        return error.contains("密码") || error.contains("提取码") || error.contains("访问码")
                || lower.contains("receive_code") || lower.contains("password");
    }

    static String downloadUrl(String pickcode) throws Exception {
        String cookie = getCookie();
        if (cookie.isEmpty()) throw new ExtractException("115 分享播放需要登录 115 账号：请先用 115 网盘爬虫扫码登录");
        byte[] key16 = M115.newKey();
        String data = M115.encode("{\"pickcode\":\"" + pickcode + "\"}", key16);
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA_115);
        headers.put("Referer", "https://115.com/");
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Cookie", cookie);
        ArrayMap<String, String> form = new ArrayMap<>();
        form.put("data", data);
        String url = API_DOWNURL + "?t=" + System.currentTimeMillis();
        String resp;
        try (Response res = OkHttp.newCall(url, headers, OkHttp.toBody(form)).execute()) {
            resp = res.body().string();
        }
        JSONObject obj = new JSONObject(resp);
        if (!obj.optBoolean("state", false)) {
            throw new ExtractException("115 取播放地址失败：" + obj.optString("error", obj.optString("msg", "未知错误")));
        }
        String enc = obj.optString("data", "");
        if (enc.isEmpty()) throw new ExtractException("115 取播放地址失败：返回为空");
        JSONObject info;
        try {
            info = new JSONObject(new String(M115.decode(enc, key16), StandardCharsets.UTF_8));
        } catch (Throwable e) {
            throw new ExtractException("115 取播放地址失败：解密失败");
        }
        Iterator<String> keys = info.keys();
        if (!keys.hasNext()) throw new ExtractException("115 取播放地址失败：解析为空");
        JSONObject item = info.optJSONObject(keys.next());
        if (item == null) throw new ExtractException("115 取播放地址失败：结构异常");
        Object u = item.opt("url");
        String cdn = u instanceof JSONObject ? ((JSONObject) u).optString("url", "") : String.valueOf(u);
        if (cdn == null || cdn.isEmpty() || "null".equals(cdn)) throw new ExtractException("115 取播放地址失败：没有下载地址");
        SpiderDebug.log("pan115", "downloadUrl ok pickcode=%s", pickcode);
        return cdn;
    }

    static String getCookie() {
        return PanAuth.get115CookieForParser();
    }

    private static String readAll(File f) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }

    static class ShareInfo {
        String shareCode = "";
        String receiveCode = "";
        String fid = "";
        String pickcode = "";
    }

    static class ShareFile {
        String fid = "";
        String pickcode = "";
        String name = "";
        long size = 0;

        static ShareFile from(JSONObject o) {
            if (o == null) return null;
            ShareFile f = new ShareFile();
            f.fid = first(o, "fid", "file_id", "id");
            f.pickcode = first(o, "pickcode", "pc");
            f.name = first(o, "file_name", "fn", "n", "name");
            f.size = o.optLong("file_size", o.optLong("fs", o.optLong("s", 0)));
            if (isDir(o)) return null;
            return f.name.isEmpty() ? null : f;
        }

        static boolean isDir(JSONObject o) {
            if (o == null) return false;
            if (o.optInt("fc", 1) != 0) return false;
            return first(o, "fid", "file_id", "id").isEmpty();
        }

        static String dirFid(JSONObject o) {
            return o == null ? "" : first(o, "cid", "folder_id", "dir_id");
        }

        private static String first(JSONObject o, String... keys) {
            for (String k : keys) {
                String v = o.optString(k, "");
                if (!v.isEmpty()) return v;
            }
            return "";
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
            return Pan115.isShareUrl(url);
        }

        public static Parser get(String url) {
            return new Parser(url);
        }

        @Override
        public List<Episode> call() {
            List<Episode> episodes = new ArrayList<>();
            try {
                ShareInfo info = Pan115.parse(url);
                if (info.shareCode.isEmpty()) return episodes;
                List<ShareFile> files = Pan115.listFiles(info.shareCode, info.receiveCode);
                for (ShareFile f : files) {
                    String token = "https://115.com/s/" + info.shareCode
                            + "?password=" + Uri.encode(info.receiveCode)
                            + "&115fid=" + Uri.encode(f.fid)
                            + "&115pc=" + Uri.encode(f.pickcode);
                    episodes.add(Episode.create(f.name, token));
                }
                if (episodes.isEmpty()) {
                    episodes.add(Episode.create("该 115 分享中没有视频文件", url));
                }
            } catch (Throwable e) {
                SpiderDebug.log("pan115", "parser error=%s", e.getMessage());
                String reason = e.getMessage() == null ? "解析失败" : e.getMessage();
                episodes.add(Episode.create("115 分享" + reason, url));
            }
            return episodes;
        }
    }

    static class M115 {

        private static final String RSA_N_HEX =
                "8686980c0f5a24c4b9d43020cd2c22703ff3f450756529058b1cf88f09b86021"
              + "36477198a6e2683149659bd122c33592fdb5ad47944ad1ea4d36c6b172aad633"
              + "8c3bb6ac6227502d010993ac967d1aef00f0c8e038de2e4d3bc2ec368af2e9f1"
              + "0a6f1eda4f7262f136420c07c331b871bf139f74f3010e3c4fe57df3afb71683";
        private static final BigInteger RSA_N = new BigInteger(RSA_N_HEX, 16);
        private static final BigInteger RSA_E = BigInteger.valueOf(0x10001);
        private static final int RSA_KL = 128;

        private static final byte[] XOR_SEED = {
            (byte) 0xf0, (byte) 0xe5, (byte) 0x69, (byte) 0xae, (byte) 0xbf, (byte) 0xdc, (byte) 0xbf, (byte) 0x8a,
            (byte) 0x1a, (byte) 0x45, (byte) 0xe8, (byte) 0xbe, (byte) 0x7d, (byte) 0xa6, (byte) 0x73, (byte) 0xb8,
            (byte) 0xde, (byte) 0x8f, (byte) 0xe7, (byte) 0xc4, (byte) 0x45, (byte) 0xda, (byte) 0x86, (byte) 0xc4,
            (byte) 0x9b, (byte) 0x64, (byte) 0x8b, (byte) 0x14, (byte) 0x6a, (byte) 0xb4, (byte) 0xf1, (byte) 0xaa,
            (byte) 0x38, (byte) 0x01, (byte) 0x35, (byte) 0x9e, (byte) 0x26, (byte) 0x69, (byte) 0x2c, (byte) 0x86,
            (byte) 0x00, (byte) 0x6b, (byte) 0x4f, (byte) 0xa5, (byte) 0x36, (byte) 0x34, (byte) 0x62, (byte) 0xa6,
            (byte) 0x2a, (byte) 0x96, (byte) 0x68, (byte) 0x18, (byte) 0xf2, (byte) 0x4a, (byte) 0xfd, (byte) 0xbd,
            (byte) 0x6b, (byte) 0x97, (byte) 0x8f, (byte) 0x4d, (byte) 0x8f, (byte) 0x89, (byte) 0x13, (byte) 0xb7,
            (byte) 0x6c, (byte) 0x8e, (byte) 0x93, (byte) 0xed, (byte) 0x0e, (byte) 0x0d, (byte) 0x48, (byte) 0x3e,
            (byte) 0xd7, (byte) 0x2f, (byte) 0x88, (byte) 0xd8, (byte) 0xfe, (byte) 0xfe, (byte) 0x7e, (byte) 0x86,
            (byte) 0x50, (byte) 0x95, (byte) 0x4f, (byte) 0xd1, (byte) 0xeb, (byte) 0x83, (byte) 0x26, (byte) 0x34,
            (byte) 0xdb, (byte) 0x66, (byte) 0x7b, (byte) 0x9c, (byte) 0x7e, (byte) 0x9d, (byte) 0x7a, (byte) 0x81,
            (byte) 0x32, (byte) 0xea, (byte) 0xb6, (byte) 0x33, (byte) 0xde, (byte) 0x3a, (byte) 0xa9, (byte) 0x59,
            (byte) 0x34, (byte) 0x66, (byte) 0x3b, (byte) 0xaa, (byte) 0xba, (byte) 0x81, (byte) 0x60, (byte) 0x48,
            (byte) 0xb9, (byte) 0xd5, (byte) 0x81, (byte) 0x9c, (byte) 0xf8, (byte) 0x6c, (byte) 0x84, (byte) 0x77,
            (byte) 0xff, (byte) 0x54, (byte) 0x78, (byte) 0x26, (byte) 0x5f, (byte) 0xbe, (byte) 0xe8, (byte) 0x1e,
            (byte) 0x36, (byte) 0x9f, (byte) 0x34, (byte) 0x80, (byte) 0x5c, (byte) 0x45, (byte) 0x2c, (byte) 0x9b,
            (byte) 0x76, (byte) 0xd5, (byte) 0x1b, (byte) 0x8f, (byte) 0xcc, (byte) 0xc3, (byte) 0xb8, (byte) 0xf5,
        };
        private static final byte[] XOR_CLIENT = {
            (byte) 0x78, (byte) 0x06, (byte) 0xad, (byte) 0x4c, (byte) 0x33, (byte) 0x86,
            (byte) 0x5d, (byte) 0x18, (byte) 0x4c, (byte) 0x01, (byte) 0x3f, (byte) 0x46
        };

        static byte[] newKey() {
            byte[] k = new byte[16];
            new SecureRandom().nextBytes(k);
            return k;
        }

        static String encode(String plainJson, byte[] key16) throws Exception {
            byte[] plain = plainJson.getBytes(StandardCharsets.UTF_8);
            byte[] buf = new byte[16 + plain.length];
            System.arraycopy(key16, 0, buf, 0, 16);
            System.arraycopy(plain, 0, buf, 16, plain.length);
            byte[] body = Arrays.copyOfRange(buf, 16, buf.length);
            xorTransform(body, xorDerive(key16, 4));
            reverse(body);
            xorTransform(body, XOR_CLIENT);
            System.arraycopy(body, 0, buf, 16, body.length);
            return Base64.encodeToString(rsaEncrypt(buf), Base64.NO_WRAP);
        }

        static byte[] decode(String cipherB64, byte[] key16) throws Exception {
            byte[] data = Base64.decode(cipherB64, Base64.DEFAULT);
            byte[] raw = rsaDecrypt(data);
            if (raw.length <= 16) return new byte[0];
            byte[] keyPart = Arrays.copyOfRange(raw, 0, 16);
            byte[] out = Arrays.copyOfRange(raw, 16, raw.length);
            xorTransform(out, xorDerive(keyPart, 12));
            reverse(out);
            xorTransform(out, xorDerive(key16, 4));
            return out;
        }

        private static byte[] rsaEncrypt(byte[] data) throws Exception {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey());
            return cipher.doFinal(data);
        }

        private static byte[] rsaDecrypt(byte[] data) throws Exception {
            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, publicKey());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int i = 0; i < data.length; i += RSA_KL) {
                int end = Math.min(i + RSA_KL, data.length);
                byte[] chunk = new byte[RSA_KL];
                System.arraycopy(data, i, chunk, RSA_KL - (end - i), end - i);
                byte[] raw = cipher.doFinal(chunk);
                int s = 0;
                while (s < raw.length && raw[s] == 0) s++;
                for (int j = s + 1; j < raw.length; j++) {
                    if (raw[j] == 0) {
                        out.write(raw, j + 1, raw.length - j - 1);
                        break;
                    }
                }
            }
            return out.toByteArray();
        }

        private static RSAPublicKey publicKey() throws Exception {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(new RSAPublicKeySpec(RSA_N, RSA_E));
        }

        private static byte[] xorDerive(byte[] seed, int size) {
            if (seed.length < size) return new byte[0];
            byte[] k = new byte[size];
            for (int i = 0; i < size; i++) {
                int v = ((seed[i] & 0xFF) + (XOR_SEED[size * i] & 0xFF)) & 0xFF;
                k[i] = (byte) (v ^ (XOR_SEED[size * (size - i - 1)] & 0xFF));
            }
            return k;
        }

        private static void xorTransform(byte[] buf, byte[] key) {
            int n = buf.length, k = key.length;
            if (n == 0 || k == 0) return;
            int mod = n % 4;
            for (int i = 0; i < mod; i++) buf[i] ^= key[i % k];
            for (int i = mod; i < n; i++) buf[i] ^= key[(i - mod) % k];
        }

        private static void reverse(byte[] buf) {
            for (int i = 0, j = buf.length - 1; i < j; i++, j--) {
                byte t = buf[i];
                buf[i] = buf[j];
                buf[j] = t;
            }
        }
    }
}
