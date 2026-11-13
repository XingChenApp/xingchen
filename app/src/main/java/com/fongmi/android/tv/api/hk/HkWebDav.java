package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 8.83 ui/setting/webdav/WebDavHolder 的星辰等价（G42 buildWebDav 的承接面）：
 * build(url, user, password) 建根、child/delete/download/list/makeDir/upload 六法。
 * 传输走 OkHttp PROPFIND（Depth:1）＋ Basic 认证；list() 回 WebDavFileObj 字段集
 * {dir, fileName, modifyTime, modifyTimeStr, modifyTimestamp, name, playUrl, url,
 * urlName} 的 JSON 数组（官 listFiles 后 reverse，咱同序）。
 * 第九阶段复核订正后与官的剩余记名差（见回执）：playUrl 的认证后缀在无账号
 * （authHeader 为 null）时官形未抠、咱家回裸地址；PROPFIND 应答以 XmlPullParser
 * 解析（官自带 HTTP 层逐字段读）。modifyTimeStr 已按官 TimeUtil.formatTime
 * "yyyy/MM/dd HH:mm:ss" 落、urlName 已按官回 href 全文、playUrl 已按官
 * buildDownloadUrl 拼「父址/显示名;{Authorization@头}」、name 已按官取 href
 * 末段、upload 已按官 (name, filePath) 序、delete/list 失败形已按官收。
 */
public final class HkWebDav {

    private static final String PROPFIND_BODY = "<?xml version=\"1.0\"?>\n"
            + "<a:propfind xmlns:a=\"DAV:\">\n<a:prop>\n<a:displayname/>\n"
            + "<a:resourcetype/>\n<a:getcontentlength/>\n<a:creationdate/>\n"
            + "<a:getlastmodified/>\n</a:prop>\n</a:propfind>";

    private final String baseUrl;
    private final String authHeader;

    private HkWebDav(String baseUrl, String authHeader) {
        this.baseUrl = baseUrl;
        this.authHeader = authHeader;
    }

    public static HkWebDav build(String url, String username, String password) {
        String auth = null;
        if (!TextUtils.isEmpty(username)) {
            auth = okhttp3.Credentials.basic(username == null ? "" : username,
                    password == null ? "" : password);
        }
        return new HkWebDav(stripTrailingSlash(url == null ? "" : url), auth);
    }

    public HkWebDav child(String name) {
        return new HkWebDav(resolve(name), authHeader);
    }

    public void delete(String name) {
        // 官 holder.delete：内部 boolean 结果被丢弃、失败不抛（WebDavHolder:218-240）
        try {
            execute(builder(resolve(name)).method("DELETE", null));
        } catch (Throwable ignored) {
        }
    }

    public boolean download(String remote, String local) {
        try (Response resp = executeResp(builder(resolve(remote)).get())) {
            if (!resp.isSuccessful() || resp.body() == null) return false;
            File target = new File(HkJsRuntime.getFilePath(local));
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (InputStream in = resp.body().byteStream();
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public HkWebDav makeDir(String name) {
        execute(builder(resolve(name)).method("MKCOL", null));
        return child(name);
    }

    /** 官 upload(name, filePath)：第一参是远程子名（拼目标址）、第二参是本地路径。 */
    public void upload(String name, String filePath) {
        File source = new File(HkJsRuntime.getFilePath(filePath));
        RequestBody body = RequestBody.create(source,
                MediaType.parse("application/octet-stream"));
        execute(builder(resolve(name)).put(body));
    }

    public String list() {
        RequestBody body = RequestBody.create(PROPFIND_BODY,
                MediaType.parse("application/xml; charset=utf-8"));
        String xml;
        try (Response resp = executeResp(builder(baseUrl)
                .method("PROPFIND", body).header("Depth", "1"))) {
            if (resp.body() == null) return "[]";
            xml = resp.body().string();
        } catch (Throwable t) {
            // 官 list 失败回 "[]"（不抛）
            return "[]";
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (DavEntry e : parseEntries(xml)) {
            if (e.href == null) continue;
            String childUrl = absoluteOf(e.href);
            if (stripTrailingSlash(childUrl).equals(baseUrl)) continue; // 自身不入表
            Map<String, Object> obj = new LinkedHashMap<>();
            String segment = lastSegment(e.href);
            // 官 name 取 href 末段（解码后显示名）；urlName 置 href 全文
            String display = urlDecode(segment);
            long ts = parseGmt(e.lastModified);
            obj.put("dir", e.directory);
            obj.put("fileName", segment);
            obj.put("modifyTime", e.lastModified == null ? "" : e.lastModified);
            obj.put("modifyTimeStr", ts > 0 ? formatLocal(ts) : "");
            obj.put("modifyTimestamp", ts);
            obj.put("name", display);
            // 官 playUrl＝父 holder.buildDownloadUrl(displayName, authHeader)：displayName
            // 是 href 末段原文（未解码），拼「父址/末段;{Authorization@<头>}」
            obj.put("playUrl", authHeader != null
                    ? baseUrl + "/" + segment + ";{Authorization@" + authHeader + "}"
                    : childUrl);
            obj.put("url", childUrl);
            // 官 urlName：文件支 href 全文；目录支先去尾 "/" 再入
            obj.put("urlName", e.directory && e.href.endsWith("/")
                    ? e.href.substring(0, e.href.length() - 1) : e.href);
            out.add(obj);
        }
        Collections.reverse(out);
        return new com.google.gson.Gson().toJson(out);
    }

    // ── 传输与解析 ──────────────────────────────────────────────────────

    private Request.Builder builder(String url) {
        Request.Builder b = new Request.Builder().url(url);
        if (authHeader != null) b.header("Authorization", authHeader);
        return b;
    }

    private void execute(Request.Builder b) {
        try (Response resp = executeResp(b)) {
            if (!resp.isSuccessful()) {
                throw new RuntimeException("WebDAV request failed: " + resp.code());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("WebDAV request failed: " + t.getMessage());
        }
    }

    private Response executeResp(Request.Builder b) throws Exception {
        return HkHttp.client().newCall(b.build()).execute();
    }

    private String resolve(String name) {
        if (name == null || name.isEmpty()) return baseUrl;
        if (name.startsWith("http://") || name.startsWith("https://")) return name;
        String n = name;
        while (n.startsWith("/")) n = n.substring(1);
        return baseUrl + "/" + n;
    }

    private String absoluteOf(String href) {
        if (href.startsWith("http://") || href.startsWith("https://")) return stripTrailingSlash(href);
        // href 是服务器绝对路径：取 base 的 scheme+authority 拼接
        try {
            java.net.URL base = new java.net.URL(baseUrl);
            String path = href.startsWith("/") ? href : "/" + href;
            return stripTrailingSlash(base.getProtocol() + "://" + base.getAuthority() + path);
        } catch (Throwable t) {
            return stripTrailingSlash(href);
        }
    }

    private static String stripTrailingSlash(String url) {
        String u = url == null ? "" : url;
        while (u.endsWith("/") && u.length() > 1) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static String lastSegment(String href) {
        String h = stripTrailingSlash(href);
        int slash = h.lastIndexOf('/');
        return slash >= 0 ? h.substring(slash + 1) : h;
    }

    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }

    private static long parseGmt(String text) {
        if (TextUtils.isEmpty(text)) return 0;
        String[] patterns = {"EEE, dd MMM yyyy HH:mm:ss 'GMT'", "yyyy-MM-dd'T'HH:mm:ss'Z'"};
        for (String p : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("GMT"));
                Date d = f.parse(text.trim());
                if (d != null) return d.getTime();
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static String formatLocal(long ts) {
        // 官 TimeUtil.formatTime＝"yyyy/MM/dd HH:mm:ss"
        SimpleDateFormat f = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
        return f.format(new Date(ts));
    }

    private static final class DavEntry {
        String href;
        String displayName;
        String lastModified;
        boolean directory;
    }

    private static List<DavEntry> parseEntries(String xml) {
        List<DavEntry> out = new ArrayList<>();
        if (TextUtils.isEmpty(xml)) return out;
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xml));
            DavEntry current = null;
            String text = null;
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (tag.endsWith("response")) {
                        current = new DavEntry();
                    } else if (current != null && tag.endsWith("collection")) {
                        current.directory = true;
                    }
                    text = null;
                } else if (event == XmlPullParser.TEXT) {
                    text = parser.getText();
                } else if (event == XmlPullParser.END_TAG && current != null) {
                    String tag = parser.getName();
                    if (tag.endsWith("response")) {
                        out.add(current);
                        current = null;
                    } else if (tag.endsWith("href")) {
                        current.href = text;
                    } else if (tag.endsWith("displayname")) {
                        current.displayName = text;
                    } else if (tag.endsWith("getlastmodified")) {
                        current.lastModified = text;
                    }
                }
                event = parser.next();
            }
        } catch (Throwable t) {
            return out;
        }
        return out;
    }
}
