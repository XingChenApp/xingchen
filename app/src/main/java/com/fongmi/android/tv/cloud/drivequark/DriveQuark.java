package com.fongmi.android.tv.cloud.drivequark;

import android.text.TextUtils;

import com.fongmi.android.tv.cloud.CloudDrive;
import com.fongmi.android.tv.cloud.CloudFile;
import com.fongmi.android.tv.cloud.CloudTask;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 夸克网盘原生客户端
 * 来源：h1d3rone/quarkmanager（已验证）
 */
public class DriveQuark implements CloudDrive {

    public static final String ID = "quark";

    // 来源：h1d3rone/quarkmanager（已验证）
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/94.0.4606.71 Safari/537.36 Core/1.94.225.400 QQBrowser/12.2.5544.400";
    private static final String ORIGIN = "https://pan.quark.cn";
    private static final String REFERER = "https://pan.quark.cn/";

    private static final String BASE = "https://drive-pc.quark.cn/1/clouddrive";
    private static final String API_SORT = BASE + "/file/sort";
    private static final String API_DELETE = BASE + "/file/delete";
    private static final String API_DOWNLOAD = BASE + "/file/download";
    private static final String API_MEMBER = BASE + "/member";
    private static final String API_SHARE_TOKEN = BASE + "/share/sharepage/token";
    private static final String API_SHARE_DETAIL = BASE + "/share/sharepage/detail";
    private static final String API_SHARE_SAVE = BASE + "/share/sharepage/save";
    private static final String API_TASK = BASE + "/task";
    // 全局搜索（来源：quarklite 的 QuarkClient.searchFiles，其搜索走 drive.quark.cn 而非 drive-pc）
    private static final String API_SEARCH = "https://drive.quark.cn/1/clouddrive/file/search";

    // 二维码登录（来源：h1d3rone/quarkmanager，已验证）
    // 注意：旧的 getQrcodeToken 是空接口（返回空 200），正确接口是 getTokenForQrcodeLogin
    private static final String API_QR_TOKEN = "https://uop.quark.cn/cas/ajax/getTokenForQrcodeLogin";
    private static final String API_QR_TICKET = "https://uop.quark.cn/cas/ajax/getServiceTicketByQrcodeToken";
    private static final String API_ACCOUNT_INFO = "https://pan.quark.cn/account/info";

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Map<String, String> jar = new LinkedHashMap<>();
    private String userName = "";
    /** 账号信息（fetchAccountInfo 填充，未取到时 vipText 为空、容量为 -1） */
    private String vipText = "";
    private long acctUsed = -1;
    private long acctTotal = -1;

    public DriveQuark() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
        // 从加密存储恢复登录态（供文件浏览器等 UI 直接 new 即用）
        try {
            String c = com.fongmi.android.tv.utils.PanAuth.getQuarkCookie();
            if (c != null && !c.isEmpty()) setCookie(c);
        } catch (Throwable ignored) {
        }
    }

    /** 设置 Cookie（参考 Drive115 的 setCookie/getCookie 模式） */
    public void setCookie(String cookie) {
        jar.clear();
        if (cookie != null) {
            for (String c : cookie.split(";")) {
                String kv = c.trim();
                int eq = kv.indexOf('=');
                if (eq > 0) jar.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
        }
    }

    public String getCookie() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : jar.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    // ---------- 底层请求 ----------

    /** 默认 query 参数：每个 clouddrive 请求都带（来源：h1d3rone/quarkmanager） */
    private static Map<String, String> defaultParams() {
        Map<String, String> p = new HashMap<>();
        p.put("pr", "ucpro");
        p.put("fr", "pc");
        p.put("uc_param_str", "");
        return p;
    }

    private void absorbCookies(List<String> setCookies) {
        if (setCookies == null) return;
        for (String h : setCookies) {
            String kv = h.split(";", 2)[0].trim();
            int eq = kv.indexOf('=');
            if (eq > 0) jar.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
        }
    }

    /** 只吸收 quark.cn 域的 cookie（扫码登录 account/info 用） */
    private void absorbQuarkCookies(Response resp) {
        for (String h : resp.headers("Set-Cookie")) {
            Matcher m = Pattern.compile("domain=([^;\\s]+)", Pattern.CASE_INSENSITIVE).matcher(h);
            if (m.find() && !m.group(1).toLowerCase().contains("quark.cn")) continue;
            String kv = h.split(";", 2)[0].trim();
            int eq = kv.indexOf('=');
            if (eq > 0) jar.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
        }
    }

    private void addHeaders(Request.Builder rb) {
        rb.header("User-Agent", UA);
        rb.header("Origin", ORIGIN);
        rb.header("Referer", REFERER);
        rb.header("Accept", "application/json, text/plain, */*");
        String ck = getCookie();
        if (!TextUtils.isEmpty(ck)) rb.header("Cookie", ck);
    }

    /** clouddrive 接口 GET（带默认 params） */
    private JSONObject reqJsonGet(String url, Map<String, String> params) throws Exception {
        HttpUrl.Builder ub = HttpUrl.parse(url).newBuilder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                ub.addQueryParameter(e.getKey(), e.getValue());
            }
        }
        Request.Builder rb = new Request.Builder().url(ub.build());
        addHeaders(rb);
        rb.get();
        try (Response resp = client.newCall(rb.build()).execute()) {
            absorbCookies(resp.headers("Set-Cookie"));
            return toJson(resp);
        }
    }

    /** clouddrive 接口 JSON POST（带默认 params） */
    private JSONObject reqJsonPost(String url, JSONObject body) throws Exception {
        HttpUrl.Builder ub = HttpUrl.parse(url).newBuilder();
        for (Map.Entry<String, String> e : defaultParams().entrySet()) {
            ub.addQueryParameter(e.getKey(), e.getValue());
        }
        Request.Builder rb = new Request.Builder().url(ub.build());
        addHeaders(rb);
        rb.post(RequestBody.create(body.toString(), JSON_MEDIA));
        try (Response resp = client.newCall(rb.build()).execute()) {
            absorbCookies(resp.headers("Set-Cookie"));
            return toJson(resp);
        }
    }

    /** 裸 GET（uop.quark.cn 二维码接口用，不带 clouddrive 默认 params） */
    private JSONObject reqRawJson(String url) throws Exception {
        Request.Builder rb = new Request.Builder().url(url);
        rb.header("User-Agent", UA);
        rb.header("Referer", REFERER);
        rb.header("Accept", "application/json, text/plain, */*");
        String ck = getCookie();
        if (!TextUtils.isEmpty(ck)) rb.header("Cookie", ck);
        try (Response resp = client.newCall(rb.build()).execute()) {
            absorbCookies(resp.headers("Set-Cookie"));
            return toJson(resp);
        }
    }

    /**
     * 统一收口：先查 HTTP 状态码再解析 JSON。
     * 此前三条 reqJson 全程不看状态码，401/403 的响应体多为非 JSON，
     * new JSONObject 直接抛解析错，用户看到的是「解析失败」而非登录过期，名不副实。
     */
    private JSONObject toJson(Response resp) throws Exception {
        int code = resp.code();
        String txt = resp.body() != null ? resp.body().string() : "";
        if (code == 401 || code == 403) {
            throw new Exception("登录已过期或被拒绝（HTTP " + code + "），请重新登录夸克网盘");
        }
        if (code < 200 || code >= 300) {
            throw new Exception("夸克接口请求失败（HTTP " + code + "）");
        }
        return new JSONObject(txt.trim());
    }

    private static String q(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    // ---------- CloudDrive 接口 ----------

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "夸克网盘";
    }

    @Override
    public boolean isLogin() {
        return !TextUtils.isEmpty(getCookie());
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public void logout() {
        jar.clear();
        userName = "";
    }

    /** 登录态校验（来源：h1d3rone/quarkmanager） */
    public boolean check() {
        try {
            return storageInfo().optInt("status", -1) == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** 存储容量信息（来源：h1d3rone/quarkmanager），UI 用来显示容量 */
    public JSONObject storageInfo() throws Exception {
        return reqJsonGet(API_MEMBER, defaultParams());
    }

    /**
     * 取账号信息：/member 一次拿全容量与会员状态，解析进字段。
     * 已用容量真实键名是 use_capacity（此前 storageSummary 的候选键里没有它，
     * 容量摘要因此恒为空）；会员看 member_type，到期按多路候选键防御解析，
     * 并把关键字段打进调试日志，真机一跑即可对字段。
     *
     * @return 拿到 data 返回 true
     */
    public boolean fetchAccountInfo() {
        try {
            Map<String, String> p = defaultParams();
            // 与网页端同一组参数：带上订阅与身份子对象，到期信息在里面
            p.put("fetch_subscribe", "true");
            p.put("fetch_identity", "true");
            p.put("_ch", "home");
            JSONObject d = reqJsonGet(API_MEMBER, p).optJSONObject("data");
            if (d == null) return false;
            acctTotal = pickLong(d, "total_capacity", "total", "total_cap");
            acctUsed = pickLong(d, "use_capacity", "used_capacity", "used", "use_cap");
            vipText = parseVip(d);
            try {
                com.github.catvod.crawler.SpiderDebug.log("panacct",
                        "quark member keys=%s member_type=%s total=%s use=%s",
                        String.valueOf(d.names()), d.optString("member_type"),
                        String.valueOf(d.opt("total_capacity")), String.valueOf(d.opt("use_capacity")));
            } catch (Throwable ignored) {
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 会员文案：member_type 映射＋到期；普通用户回空串 */
    private static String parseVip(JSONObject d) {
        String type = d.optString("member_type", "").trim();
        if (type.isEmpty() || "NORMAL".equalsIgnoreCase(type)) return "";
        String base;
        String upper = type.toUpperCase(java.util.Locale.US);
        if (upper.contains("SUPER")) base = "超级VIP";
        else if (upper.contains("VIP")) base = "VIP";
        else base = type;
        String expire = vipExpire(d.opt("super_vip_exp_at"), d.opt("vip_exp_at"),
                d.opt("exp_at"), d.opt("expire_at"), d.opt("member_exp_at"));
        if (expire.isEmpty()) {
            JSONArray subs = d.optJSONArray("subscribe");
            if (subs != null && subs.length() > 0) {
                JSONObject s0 = subs.optJSONObject(0);
                if (s0 != null) expire = vipExpire(s0.opt("expire_at"), s0.opt("exp_at"));
            }
        }
        return expire.isEmpty() ? base : base + " · " + expire + " 到期";
    }

    /** 到期值归一成 yyyy-MM-dd：数字按秒/毫秒时间戳换算，字符串取前 10 位 */
    private static String vipExpire(Object... candidates) {
        for (Object v : candidates) {
            if (v == null) continue;
            if (v instanceof Number) {
                long n = ((Number) v).longValue();
                if (n > 0) {
                    return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(new java.util.Date(n < 100000000000L ? n * 1000 : n));
                }
            } else if (v instanceof String) {
                String s = ((String) v).trim();
                if (s.length() >= 10) return s.substring(0, 10);
                try {
                    long n = Long.parseLong(s);
                    if (n > 0) {
                        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                .format(new java.util.Date(n < 100000000000L ? n * 1000 : n));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return "";
    }

    /** VIP 文案（如「超级VIP · 2027-01-01 到期」），须先 fetchAccountInfo */
    public String getVipText() {
        return vipText;
    }

    /** 容量摘要（数据来自 fetchAccountInfo 的 /member 解析，未取过先取一次） */
    @Override
    public String storageSummary() throws Exception {
        if (acctTotal < 0) fetchAccountInfo();
        if (acctTotal <= 0) return "";
        return "已用 " + formatSize(Math.max(acctUsed, 0)) + " / 共 " + formatSize(acctTotal);
    }

    private static long pickLong(JSONObject o, String... keys) {
        for (String k : keys) {
            long v = o.optLong(k, -1);
            if (v >= 0) return v;
        }
        return 0;
    }

    private static String formatSize(long size) {
        if (size < 1024) return size + "B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format(java.util.Locale.US, "%.1f%sB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    /**
     * 文件列表（来源：h1d3rone/quarkmanager，已验证）
     * GET file/sort，响应 {status:200, data:{list:[...]}, metadata:{...}}
     * 文件字段：fid, file_name, dir(bool), size, file_type(0=文件夹)；cid="0" 为根目录
     */
    @Override
    public List<CloudFile> listDir(String cid, int offset, int limit) throws Exception {
        if (limit <= 0) limit = 50;
        int page = offset / limit + 1;
        Map<String, String> p = defaultParams();
        p.put("pdir_fid", TextUtils.isEmpty(cid) ? "0" : cid);
        p.put("_page", String.valueOf(page));
        p.put("_size", String.valueOf(limit));
        p.put("_sort", "file_type:asc,updated_at:desc");
        JSONObject d = reqJsonGet(API_SORT, p);
        if (d.optInt("status", -1) != 200) {
            throw new Exception("取文件列表失败：" + d.optString("message", d.optString("msg", "status=" + d.optInt("status", -1))));
        }
        List<CloudFile> files = new ArrayList<>();
        JSONObject data = d.optJSONObject("data");
        JSONArray arr = data != null ? data.optJSONArray("list") : null;
        if (arr == null) return files;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            CloudFile f = new CloudFile();
            // dir 为 bool；file_type=0 也是文件夹，兜底
            f.setDir(o.optBoolean("dir", o.optInt("file_type", 1) == 0));
            f.setId(o.optString("fid"));
            f.setName(o.optString("file_name"));
            f.setSize(o.optLong("size", 0));
            f.setParentCid(TextUtils.isEmpty(cid) ? "0" : cid);
            // 缩略图（来源：OpenList quark_uc types.go 的 File.Thumbnail `json:"thumbnail"`——
            // 条目里有这个字段，OpenList 模型里注释保留未用，星辰此前也没取；
            // 真实回包未在本机抓到，命中情况打 pancover 日志待真机对死）
            f.setThumbUrl(o.optString("thumbnail", ""));
            files.add(f);
        }
        logCoverKeys("list", arr);
        return files;
    }

    /** 封面对账日志：首条条目的键名＋本页有几个条目带缩略图（tag pancover，真机一跑即可对字段）。 */
    private static void logCoverKeys(String where, JSONArray arr) {
        try {
            if (arr == null || arr.length() == 0) return;
            int thumbs = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && !TextUtils.isEmpty(o.optString("thumbnail", ""))) thumbs++;
            }
            JSONObject first = arr.optJSONObject(0);
            com.github.catvod.crawler.SpiderDebug.log("pancover",
                    "quark %s keys=%s thumbs=%s/%s",
                    where, first == null ? "null" : String.valueOf(first.names()),
                    thumbs, arr.length());
        } catch (Throwable ignored) {
        }
    }

    /**
     * 全盘搜索（来源：quarklite 的 QuarkClient.searchFiles，已上线 App 的活口径）：
     * GET drive.quark.cn/1/clouddrive/file/search，参数 q＋_page＋_size＋_fetch_total＋_sort＋_is_hl，
     * 响应与 file/sort 同构（{status:200, data:{list:[...]}}，条目字段同列表）。
     * _is_hl=1 时 file_name 带高亮标签，解析时剥掉再用；parentCid 取条目自带 pdir_fid。
     * 响应字段未经真机对死，顶层键与条数打调试日志（tag pansrc），真机一跑即可对字段。
     */
    @Override
    public List<CloudFile> searchFiles(String keyword, int offset, int limit) throws Exception {
        if (limit <= 0) limit = 50;
        int page = offset / limit + 1;
        Map<String, String> p = defaultParams();
        p.put("q", keyword == null ? "" : keyword.trim());
        p.put("_page", String.valueOf(page));
        p.put("_size", String.valueOf(limit));
        p.put("_fetch_total", "1");
        p.put("_sort", "file_type:asc,updated_at:desc");
        p.put("_is_hl", "1");
        JSONObject d = reqJsonGet(API_SEARCH, p);
        if (d.optInt("status", -1) != 200) {
            throw new Exception("搜索失败：" + d.optString("message", d.optString("msg", "status=" + d.optInt("status", -1))));
        }
        List<CloudFile> files = new ArrayList<>();
        JSONObject data = d.optJSONObject("data");
        JSONArray arr = data != null ? data.optJSONArray("list") : null;
        try {
            com.github.catvod.crawler.SpiderDebug.log("pansrc",
                    "quark kw=%s keys=%s got=%s",
                    keyword, String.valueOf(d.names()),
                    arr == null ? "null" : String.valueOf(arr.length()));
        } catch (Throwable ignored) {
        }
        if (arr == null) return files;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            CloudFile f = new CloudFile();
            f.setDir(o.optBoolean("dir", o.optInt("file_type", 1) == 0));
            f.setId(o.optString("fid"));
            f.setName(o.optString("file_name").replaceAll("<[^>]+>", ""));
            f.setSize(o.optLong("size", 0));
            f.setParentCid(o.optString("pdir_fid", "0"));
            // 缩略图字段与列表同名（thumbnail，证据见 listDir 处注释）
            f.setThumbUrl(o.optString("thumbnail", ""));
            files.add(f);
        }
        logCoverKeys("search", arr);
        return files;
    }

    /** 文件总数：从 file/sort 的 metadata 读（来源：h1d3rone/quarkmanager） */
    @Override
    public int getTotal(String cid) throws Exception {
        Map<String, String> p = defaultParams();
        p.put("pdir_fid", TextUtils.isEmpty(cid) ? "0" : cid);
        p.put("_page", "1");
        p.put("_size", "1");
        p.put("_sort", "file_type:asc,updated_at:desc");
        JSONObject d = reqJsonGet(API_SORT, p);
        JSONObject meta = d.optJSONObject("metadata");
        if (meta == null) {
            JSONObject data = d.optJSONObject("data");
            meta = data != null ? data.optJSONObject("metadata") : null;
        }
        if (meta != null) {
            if (meta.has("_total")) return meta.optInt("_total", -1);
            if (meta.has("total")) return meta.optInt("total", -1);
            if (meta.has("count")) return meta.optInt("count", -1);
        }
        return -1;
    }

    /** 删除（来源：h1d3rone/quarkmanager）：POST file/delete，body {action_type:2, filelist:[ids], exclude_fids:[]} */
    @Override
    public boolean delete(List<String> ids, boolean isDir) throws Exception {
        if (ids == null || ids.isEmpty()) throw new Exception("没有要删除的文件");
        JSONObject body = new JSONObject();
        body.put("action_type", 2);
        body.put("filelist", new JSONArray(ids));
        body.put("exclude_fids", new JSONArray());
        JSONObject d = reqJsonPost(API_DELETE, body);
        return d.optInt("status", -1) == 200;
    }

    /** 夸克磁力离线接口未验证，暂不支持 */
    @Override
    public boolean offlineDownload(String url, String cid) throws Exception {
        throw new Exception("夸克暂不支持磁力离线，请用115/PikPak");
    }

    /** 夸克无离线任务管理，返回空列表 */
    @Override
    public List<CloudTask> taskList(int page) throws Exception {
        return new ArrayList<>();
    }

    /** 夸克无离线任务管理 */
    @Override
    public boolean taskDelete(List<String> hashes) throws Exception {
        throw new Exception("夸克暂不支持离线任务管理");
    }

    /**
     * 取播放直链（来源：h1d3rone/quarkmanager，已验证）
     * POST file/download，body {fids:[fid]}，从 data 取 download_url
     */
    @Override
    public String getPlayUrl(CloudFile file) throws Exception {
        if (file == null || TextUtils.isEmpty(file.getId())) {
            throw new Exception("文件 id 为空");
        }
        // 直链过期自愈（对照 115 的 403 重铸最多 3 次）：取到链先探活，
        // 403 说明这条链已死，重新取一条再探；探活本身出错（网络抖动等）
        // 不拦路，照常把链交给播放器，别让探活把能播的也挡下。
        for (int attempt = 0; attempt <= 3; attempt++) {
            String url = mintDownloadUrl(file.getId());
            if (!probeForbidden(url)) return url;
        }
        throw new Exception("夸克直链已过期（403），多次重新获取仍失败，请稍后重试");
    }

    /** 现取一条 download_url（file/download） */
    private String mintDownloadUrl(String fid) throws Exception {
        JSONObject body = new JSONObject();
        JSONArray fids = new JSONArray();
        fids.put(fid);
        body.put("fids", fids);
        JSONObject d = reqJsonPost(API_DOWNLOAD, body);
        if (d.optInt("status", -1) != 200) {
            throw new Exception("取下载地址失败：" + d.optString("message", d.optString("msg", "")));
        }
        String url = parseDownloadUrl(d);
        if (TextUtils.isEmpty(url)) throw new Exception("取下载地址失败：响应里没有 download_url");
        return url;
    }

    /** 探活：与播放器同样的无 cookie 直连条件发一个 1 字节 Range 请求，只有明确 403 才算链已死 */
    private boolean probeForbidden(String url) {
        try {
            Request.Builder rb = new Request.Builder().url(url);
            rb.header("User-Agent", UA);
            rb.header("Referer", REFERER);
            rb.header("Range", "bytes=0-0");
            try (Response resp = client.newCall(rb.build()).execute()) {
                return resp.code() == 403;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** 防御性解析 download_url：data[0].download_url / data.list[0].download_url / data.download_url */
    private static String parseDownloadUrl(JSONObject d) {
        JSONObject data = d.optJSONObject("data");
        if (data != null) {
            JSONArray list = data.optJSONArray("list");
            if (list != null && list.length() > 0) {
                JSONObject first = list.optJSONObject(0);
                String u = first != null ? first.optString("download_url") : "";
                if (!TextUtils.isEmpty(u)) return u;
            }
            String u = data.optString("download_url");
            if (!TextUtils.isEmpty(u)) return u;
        }
        JSONArray arr = d.optJSONArray("data");
        if (arr != null && arr.length() > 0) {
            JSONObject first = arr.optJSONObject(0);
            String u = first != null ? first.optString("download_url") : "";
            if (!TextUtils.isEmpty(u)) return u;
        }
        return "";
    }

    /**
     * 分享转存（夸克网页端 sharepage 活口径，drive-pc.quark.cn）：
     * token(pwd_id＋passcode) 换 stoken → detail 取分享根层文件的 fid＋share_fid_token
     * （文件夹带 fid 整夹转存）→ save 落到目标目录 → 轮询 task 至完成。
     */
    @Override
    public boolean saveShare(String shareUrl, String password, String toCid) throws Exception {
        String pwdId = null;
        if (!TextUtils.isEmpty(shareUrl)) {
            Matcher m = Pattern.compile("pan\\.quark\\.cn/s/([A-Za-z0-9]+)").matcher(shareUrl);
            if (m.find()) pwdId = m.group(1);
        }
        if (TextUtils.isEmpty(pwdId)) throw new Exception("解析分享链接失败：拿不到 pwd_id");
        // 1) 换 stoken
        JSONObject tokenBody = new JSONObject()
                .put("pwd_id", pwdId)
                .put("passcode", password == null ? "" : password.trim());
        JSONObject td = reqJsonPost(API_SHARE_TOKEN, tokenBody);
        if (td.optInt("status", -1) != 200) {
            throw new Exception("打开分享失败：" + td.optString("message", td.optString("msg", "")));
        }
        JSONObject tData = td.optJSONObject("data");
        String stoken = tData != null ? tData.optString("stoken") : "";
        if (TextUtils.isEmpty(stoken)) throw new Exception("打开分享失败：没有拿到 stoken");
        // 2) 取分享根层文件（翻页取全）
        List<String> fidList = new ArrayList<>();
        List<String> fidTokenList = new ArrayList<>();
        for (int page = 1; page <= 20; page++) {
            Map<String, String> p = defaultParams();
            p.put("pwd_id", pwdId);
            p.put("stoken", stoken);
            p.put("pdir_fid", "0");
            p.put("_page", String.valueOf(page));
            p.put("_size", "100");
            p.put("_sort", "file_type:asc,updated_at:desc");
            JSONObject dd = reqJsonGet(API_SHARE_DETAIL, p);
            if (dd.optInt("status", -1) != 200) {
                throw new Exception("获取分享内容失败：" + dd.optString("message", dd.optString("msg", "")));
            }
            JSONObject data = dd.optJSONObject("data");
            JSONArray list = data != null ? data.optJSONArray("list") : null;
            if (list == null || list.length() == 0) break;
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i);
                if (o == null) continue;
                String fid = o.optString("fid");
                String ft = o.optString("share_fid_token");
                if (!TextUtils.isEmpty(fid) && !TextUtils.isEmpty(ft)) {
                    fidList.add(fid);
                    fidTokenList.add(ft);
                }
            }
            if (list.length() < 100) break;
        }
        if (fidList.isEmpty()) throw new Exception("分享为空或已失效");
        // 3) save 转存到目标目录（空＝根目录 "0"）
        JSONObject saveBody = new JSONObject();
        saveBody.put("fid_list", new JSONArray(fidList));
        saveBody.put("fid_token_list", new JSONArray(fidTokenList));
        saveBody.put("to_pdir_fid", TextUtils.isEmpty(toCid) ? "0" : toCid);
        saveBody.put("pwd_id", pwdId);
        saveBody.put("stoken", stoken);
        saveBody.put("pdir_fid", "0");
        saveBody.put("scene", "link");
        JSONObject sd = reqJsonPost(API_SHARE_SAVE, saveBody);
        if (sd.optInt("status", -1) != 200) {
            throw new Exception("转存失败：" + sd.optString("message", sd.optString("msg", "")));
        }
        JSONObject sData = sd.optJSONObject("data");
        String taskId = sData != null ? sData.optString("task_id") : "";
        if (TextUtils.isEmpty(taskId)) return true;
        // 4) 轮询任务（与 PikPak 同口径：超时不假报成功，提示去网盘查看）
        for (int i = 0; i < 15; i++) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }
            if (shareTaskDone(taskId)) return true;
        }
        throw new Exception("转存超时，请稍后在网盘中查看");
    }

    /** 转存任务是否完成：GET /task，data.status==2 为完成；查询失败按未完成继续等 */
    private boolean shareTaskDone(String taskId) {
        try {
            Map<String, String> p = defaultParams();
            p.put("task_id", taskId);
            p.put("retry_index", "0");
            JSONObject d = reqJsonGet(API_TASK, p);
            JSONObject data = d.optJSONObject("data");
            return data != null && data.optInt("status", -1) == 2;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- 二维码登录（来源：h1d3rone/quarkmanager，已验证；参考 Drive115 的 QrSession 模式） ----------

    public static class QrSession {
        public String token;
        public String qrUrl;
        public String ticket;
        public long deadline;
    }

    /** 开始二维码登录：拿 token，拼出二维码 URL */
    public QrSession qrStart() throws Exception {
        String url = API_QR_TOKEN + "?client_id=532&v=1.2&request_id=" + UUID.randomUUID().toString();
        JSONObject d = reqRawJson(url);
        JSONObject data = d.optJSONObject("data");
        JSONObject members = data != null ? data.optJSONObject("members") : null;
        String token = members != null ? members.optString("token") : "";
        if (TextUtils.isEmpty(token)) {
            throw new Exception("拿二维码失败：" + d.optString("message", d.optString("msg", "未知错误")));
        }
        QrSession s = new QrSession();
        s.token = token;
        s.qrUrl = "https://su.quark.cn/4_eMHBJ?token=" + q(token)
                + "&client_id=532&ssb=weblogin&uc_param_str="
                + "&uc_biz_str=S:custom|OPT:SAREA@0|OPT:IMMERSIVE@1|OPT:BACK_BTN_STYLE@0";
        s.deadline = System.currentTimeMillis() + 300000;
        return s;
    }

    /**
     * 轮询扫码状态
     * @return 2=扫码成功（拿到 service_ticket） 0=等待中 -1=失败/过期
     */
    public int qrPoll(QrSession s) throws Exception {
        String url = API_QR_TICKET + "?client_id=532&v=1.2&token=" + q(s.token)
                + "&request_id=" + UUID.randomUUID().toString();
        JSONObject d = reqRawJson(url);
        int status = d.optInt("status", -1);
        if (status == 2000000) {
            JSONObject data = d.optJSONObject("data");
            JSONObject members = data != null ? data.optJSONObject("members") : null;
            String ticket = members != null ? members.optString("service_ticket") : "";
            if (!TextUtils.isEmpty(ticket)) {
                s.ticket = ticket;
                return 2;
            }
            return 0;
        }
        if (status == 50004002 || status == 50004003 || status == 50004004) return -1;
        return 0;
    }

    /** 扫码确认后换取 cookie：GET pan.quark.cn/account/info?st={ticket}&lw=scan */
    public boolean qrLogin(QrSession s) throws Exception {
        if (TextUtils.isEmpty(s.ticket)) {
            if (qrPoll(s) != 2) return false;
        }
        String url = API_ACCOUNT_INFO + "?st=" + q(s.ticket) + "&lw=scan";
        Request.Builder rb = new Request.Builder().url(url);
        rb.header("User-Agent", UA);
        rb.header("Referer", REFERER);
        rb.header("Accept", "application/json, text/plain, */*");
        try (Response resp = client.newCall(rb.build()).execute()) {
            absorbQuarkCookies(resp);
            String body = resp.body() != null ? resp.body().string() : "";
            try {
                JSONObject d = new JSONObject(body.trim());
                JSONObject data = d.optJSONObject("data");
                if (data != null) userName = data.optString("nickname", data.optString("name", userName));
            } catch (Exception ignored) {
            }
        }
        return !TextUtils.isEmpty(getCookie());
    }
}
