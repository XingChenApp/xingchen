package com.fongmi.android.tv.cloud.drive115;

import android.text.TextUtils;
import android.util.Base64;

import com.fongmi.android.tv.cloud.CloudDrive;
import com.fongmi.android.tv.cloud.CloudFile;
import com.fongmi.android.tv.cloud.CloudTask;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 115 网盘原生客户端
 * 来源：115_tvbox_4_mx3q.py（Api 类，3305 行，真机验证）
 */
public class Drive115 implements CloudDrive {

    public static final String ID = "115";

    private static final String UA_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String UA_115 = "Mozilla/5.0 115Browser/27.0.5.7";

    private static final String API_SSO = "https://passportapi.115.com/app/1.0/web/1.0/check/sso";
    private static final String API_NAV = "https://my.115.com/?ct=ajax&ac=nav";
    private static final String API_BASE_INFO = "https://webapi.115.com/user/base_info";
    private static final String API_FILES = "https://webapi.115.com/files";
    private static final String API_SEARCH = "https://webapi.115.com/files/search";
    private static final String API_FILE_INFO = "https://webapi.115.com/files/get_info";
    private static final String API_M3U8 = "https://115.com/api/video/m3u8/%s.m3u8";
    private static final String API_VIDEO = "https://webapi.115.com/files/video";
    private static final String API_DOWNURL = "https://proapi.115.com/app/chrome/downurl";
    private static final String API_LIXIAN = "https://115.com/web/lixian/";

    private final OkHttpClient client;
    private String cookie = "";
    private String userName = "";
    private long userId = 0;
    /** 账号信息（fetchAccountInfo 填充，未取到时 vipText 为空、容量为 -1） */
    private String vipText = "";
    private long spaceUsed = -1;
    private long spaceTotal = -1;
    private String spaceText = "";
    /** 会话 cookie 罐（对照 PY Api._jar）：115 CDN 门票 cookie 全量吸收 */
    private final CookieJar115 cookieJar = new CookieJar115();
    /** 播放地址构造器（本机代理） */
    private PlayUrlBuilder115 playUrlBuilder;

    public Drive115() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                // 取链与取流必须同一 OkHttp 实例（签名绑出口 IP，对照 PY L420）
                // 代理取流时手动跟重定向，这里关自动重定向由代理层手动处理
                .build();
        // 从加密存储恢复登录态（供文件浏览器等 UI 直接 new 即用）
        try {
            String c = com.fongmi.android.tv.utils.PanAuth.get115Cookie();
            if (c != null && !c.isEmpty()) setCookie(c);
        } catch (Throwable ignored) {
        }
    }

    public void setCookie(String cookie) {
        this.cookie = cookie == null ? "" : cookie;
        // 9dd365a0 曾在这里解析 UID 还原 userId，但 115 返回的 UID 值可能带引号
        // （如 UID="12345"，py 端 parse_cookie 专门 strip('"') 就是证据），
        // parseLong 直接抛 NumberFormatException -> userId=0 -> isLogin() 恒 false，
        // 而卡片用 contains("UID=") 判定为已登录，于是点卡片无限跳回登录页。
        // parseUidFromCookie 已做去引号兼容；userId 仅供 check()/nav() 参考。
        this.userId = parseUidFromCookie(this.cookie);
    }

    private static long parseUidFromCookie(String cookie) {
        if (cookie == null) return 0;
        for (String pair : cookie.split(";")) {
            String p = pair.trim();
            if (p.regionMatches(true, 0, "UID=", 0, 4)) {
                String v = p.substring(4).trim();
                // 115 有时会返回带引号的值：UID="12345"，先去引号再解析
                if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                    v = v.substring(1, v.length() - 1).trim();
                }
                try {
                    return Long.parseLong(v);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    public String getCookie() {
        return cookie;
    }

    // ---------- 底层请求（对照 PY Api.req） ----------

    private String req(String url, Map<String, String> params, Map<String, String> form,
                       String ua, String referer) throws Exception {
        HttpUrl.Builder ub = HttpUrl.parse(url).newBuilder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                ub.addQueryParameter(e.getKey(), e.getValue());
            }
        }
        Request.Builder rb = new Request.Builder().url(ub.build());
        rb.header("User-Agent", ua != null ? ua : UA_WEB);
        rb.header("Referer", referer != null ? referer : "https://115.com/");
        rb.header("Accept", "application/json, text/plain, */*");
        rb.header("Accept-Language", "zh-CN,zh;q=0.9");
        if (!TextUtils.isEmpty(cookie)) {
            rb.header("Cookie", cookie);
        }
        if (form != null && !form.isEmpty()) {
            FormBody.Builder fb = new FormBody.Builder();
            for (Map.Entry<String, String> e : form.entrySet()) {
                fb.add(e.getKey(), e.getValue());
            }
            rb.post(fb.build());
        } else {
            rb.get();
        }
        try (Response resp = client.newCall(rb.build()).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            // 自动吸 cookie（对照 PY Api._absorb_cookies）：全量进罐，同名覆盖、空值删除
            // 115 的文件 CDN 门票 cookie 是某次接口响应悄悄下发的，只看单次响应会时有时无
            java.util.List<String> scList = resp.headers("Set-Cookie");
            if (scList != null && !scList.isEmpty()) {
                cookieJar.absorb(scList);
            }
            // 兼容旧逻辑：登录 cookie（UID/CID/SEID/KID）仍单独维护
            String setCookie = resp.header("Set-Cookie");
            if (!TextUtils.isEmpty(setCookie)) {
                mergeCookie(setCookie);
            }
            return body.trim();
        }
    }

    private JSONObject reqJson(String url, Map<String, String> params, Map<String, String> form,
                               String ua, String referer) throws Exception {
        String txt = req(url, params, form, ua, referer);
        return new JSONObject(txt);
    }

    private void mergeCookie(String setCookie) {
        // 简单合并：提取 key=value 部分
        String[] parts = setCookie.split(";");
        if (parts.length > 0) {
            String kv = parts[0].trim();
            if (kv.contains("=") && !cookie.contains(kv.split("=")[0] + "=")) {
                cookie = cookie.isEmpty() ? kv : cookie + "; " + kv;
                // cookie 变了，userId 重新推导，保持两者一致
                userId = parseUidFromCookie(cookie);
            }
        }
    }

    private static String nowMs() {
        return String.valueOf(System.currentTimeMillis());
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
        return "115网盘";
    }

    @Override
    public boolean isLogin() {
        // 单一真相源：卡片显示和进文件判断必须用同一套依据。
        // 之前这里用 (!isEmpty(cookie) && userId > 0)，userId 从 cookie 解析而来，
        // 一旦 cookie 格式稍有偏差（引号/空值/非数字 UID），就与卡片的
        // PanAuth.is115LoggedIn() 不一致，导致"显示已登录但点卡片无限返回"。
        // userId 字段保留，供 check()/nav() 参考。
        return com.fongmi.android.tv.utils.PanAuth.is115LoggedIn();
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public void logout() {
        cookie = "";
        userName = "";
        userId = 0;
    }

    /** 登录态校验（对照 PY Api.check） */
    public boolean check() throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("_", nowMs());
        JSONObject d = reqJson(API_SSO, p, null, null, null);
        JSONObject data = d.optJSONObject("data");
        long uid = data != null ? data.optLong("user_id", 0) : 0;
        if (uid > 0 && d.optInt("code", -1) == 0) {
            userId = uid;
            return true;
        }
        return false;
    }

    /** 取用户信息（对照 PY Api.nav） */
    public void nav() {
        fetchAccountInfo();
    }

    /**
     * 取账号信息，两条腿各管一段、互不连坐：
     * 一是 nav（对照 PY Api.nav）取用户名/user_id——它是网页导航接口，回包里没有
     * 容量与 VIP 明细（nap511 等真实消费者也只从它取 user_name/face/expire），
     * 它身上的 parseSpace/parseVip 只作兜底（is_vip 能蒙出裸「VIP」）。
     * 二是 webapi 的 /user/base_info 取容量与 VIP 全量——这是第三方 115 客户端
     * Pang5 的活口径（其 src/api/user.rs：GET /user/base_info 一次拿 user_name、
     * vip、is_forever_vip、expire_date、size_used、size_total，另带 _text/_raw
     * 两种形态），与文件列表同域（webapi.115.com）同 Cookie，本类文件链真机已通。
     *
     * 前两轮为什么恒空（留档防再犯）：e0e2c32f 把 SDK 的 rt_space_info 键名安到
     * nav 头上解析——键是对的、接口不对，nav 根本不带这套数据；846f51d0 改打
     * proapi.115.com/app/user/info——它引的 115-sdk-go 里这条接口真身是
     * /open/user/info 且走 OAuth 令牌（AuthRequest），路径差一段、鉴权体系不符，
     * 网页 Cookie 打过去永远拿不到 data。两轮都没拿到过一回真回包。
     *
     * @return base_info 拿到 data 返回 true；失败返回 false，让 PanAccountInfo
     *         走 5 分钟重试而不是把空空间冻进 6 小时缓存
     */
    public boolean fetchAccountInfo() {
        try {
            Map<String, String> p = new HashMap<>();
            p.put("_", nowMs());
            JSONObject d = reqJson(API_NAV, p, null, null, null);
            JSONObject data = d.optJSONObject("data");
            if (data != null) {
                userName = data.optString("user_name", data.optString("uname", ""));
                if (data.optLong("user_id", 0) > 0) userId = data.optLong("user_id");
                parseSpace(data);
                vipText = parseVip(data);
            }
        } catch (Exception ignored) {
        }
        boolean infoOk = false;
        try {
            JSONObject d = reqJson(API_BASE_INFO, null, null, null, null);
            JSONObject data = d.optJSONObject("data");
            if (data != null) {
                infoOk = true;
                if (userName.isEmpty()) userName = data.optString("user_name", "");
                parseBaseInfo(data);
                try {
                    com.github.catvod.crawler.SpiderDebug.log("panacct",
                            "115 baseinfo keys=%s size_used=%s size_total=%s vip=%s expire_date=%s",
                            String.valueOf(data.names()),
                            String.valueOf(data.opt("size_used")),
                            String.valueOf(data.opt("size_total")),
                            String.valueOf(data.opt("vip")),
                            String.valueOf(data.opt("expire_date")));
                } catch (Throwable ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return infoOk;
    }

    /**
     * base_info 解析（Pang5 src/api/user.rs 活口径）：容量 size_used/size_total，
     * 值可能是字节数、数字串或格式化串，回包另带 size_used_text/size_total_text
     * 文案与 size_used_raw/size_total_raw 原始值——能拿字节数就自己拼，
     * 拿不到用文案兜底；VIP 看 vip 旗/名、is_forever_vip、expire_date。
     * 只在解析出非空结果时才覆盖 nav 兜底，缺哪截不拼哪截、不编假数。
     */
    private void parseBaseInfo(JSONObject data) {
        long used = baseInfoBytes(data, "size_used");
        long total = baseInfoBytes(data, "size_total");
        if (total > 0 && used >= 0) {
            spaceUsed = used;
            spaceTotal = total;
            spaceText = "已用 " + formatSize(used) + " / 共 " + formatSize(total);
        } else {
            String usedStr = data.optString("size_used_text", "");
            String totalStr = data.optString("size_total_text", "");
            if (usedStr.isEmpty()) usedStr = spaceStr(data.opt("size_used"));
            if (totalStr.isEmpty()) totalStr = spaceStr(data.opt("size_total"));
            if (!usedStr.isEmpty() && !totalStr.isEmpty()) {
                spaceText = "已用 " + usedStr + " / 共 " + totalStr;
            }
        }
        String v = parseBaseInfoVip(data);
        if (!v.isEmpty()) vipText = v;
    }

    /** base_info 单项字节数：优先 _raw 原始值，其次本体（数字/数字串/{size} 对象都收） */
    private static long baseInfoBytes(JSONObject data, String key) {
        long raw = spaceBytes(data.opt(key + "_raw"));
        if (raw >= 0) return raw;
        return spaceBytes(data.opt(key));
    }

    /** base_info 的 VIP 文案：vip 可能是旗（数字/布尔）或等级名字符串 */
    private static String parseBaseInfoVip(JSONObject data) {
        boolean forever = flagOn(data.opt("is_forever_vip"));
        Object vip = data.opt("vip");
        String name = "";
        boolean isVip = forever || flagOn(vip);
        if (vip instanceof String) {
            String s = ((String) vip).trim();
            if (!s.isEmpty() && !"0".equals(s) && !"false".equalsIgnoreCase(s)) {
                isVip = true;
                if (!"1".equals(s) && !"true".equalsIgnoreCase(s)) name = s;
            }
        }
        if (!isVip) return "";
        String base = name.isEmpty() ? "VIP" : name;
        if (forever) return base + " · 永久";
        String expire = data.optString("expire_date", "").trim();
        if (expire.length() >= 10) expire = expire.substring(0, 10);
        return expire.isEmpty() ? base : base + " · " + expire + " 到期";
    }

    /** 空间：data.rt_space_info 的 all_total/all_use（真键，SDK 活证据），每项是 {size,size_format} 对象；
     * 旧臆测的 space_info 只作兜底。单项也可为数字、字符串或 {size,str} 对象 */
    private void parseSpace(JSONObject data) {
        spaceUsed = -1;
        spaceTotal = -1;
        spaceText = "";
        JSONObject si = data.optJSONObject("rt_space_info");
        if (si == null) si = data.optJSONObject("space_info");
        if (si == null) return;
        spaceTotal = spaceBytes(si.opt("all_total"));
        spaceUsed = spaceBytes(si.opt("all_use"));
        if (spaceTotal > 0 && spaceUsed >= 0) {
            spaceText = "已用 " + formatSize(spaceUsed) + " / 共 " + formatSize(spaceTotal);
            return;
        }
        // 字节数拿不到时退而求其次：用服务端给的格式化字符串拼
        String totalStr = spaceStr(si.opt("all_total"));
        String usedStr = spaceStr(si.opt("all_use"));
        if (!totalStr.isEmpty() && !usedStr.isEmpty()) {
            spaceText = "已用 " + usedStr + " / 共 " + totalStr;
        }
    }

    private static long spaceBytes(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof JSONObject) {
            JSONObject o = (JSONObject) v;
            long s = o.optLong("size", -1);
            if (s >= 0) return s;
            return spaceBytes(o.opt("total"));
        }
        if (v instanceof String) {
            try {
                return Long.parseLong(((String) v).trim());
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }

    private static String spaceStr(Object v) {
        if (v instanceof JSONObject) {
            JSONObject o = (JSONObject) v;
            // 真键 size_format（SDK 活证据）；str/fmt_size 是旧臆测键，只作兜底
            String s = o.optString("size_format", o.optString("str", o.optString("fmt_size", "")));
            return s == null ? "" : s.trim();
        }
        if (v instanceof String) {
            // 单项直接给格式化串（如「2TB」）时原样用；纯数字串归字节数路拼，不进这里
            String s = ((String) v).trim();
            try {
                Long.parseLong(s);
                return "";
            } catch (Exception e) {
                return s;
            }
        }
        return "";
    }

    /** VIP：优先 data.vip_info 对象，回退 data 上的直挂字段；判不出就回空串 */
    private static String parseVip(JSONObject data) {
        JSONObject vi = data.optJSONObject("vip_info");
        String name = "";
        String expire = "";
        boolean vip = false;
        if (vi != null) {
            name = vi.optString("name", vi.optString("level_name",
                    vi.optString("title", vi.optString("vip_name", ""))));
            // 到期真键是 expire（秒级时间戳，SDK 活证据）；旧候选键只作兜底
            expire = vipExpire(vi.opt("expire"), vi.opt("expire_time"), vi.opt("expire_date"),
                    vi.opt("end_time"), vi.opt("vip_expire_time"));
            vip = flagOn(vi.opt("is_vip")) || flagOn(vi.opt("vip"))
                    || !name.isEmpty() || !expire.isEmpty();
        }
        if (!vip) {
            vip = flagOn(data.opt("is_vip")) || flagOn(data.opt("vip"));
            if (vip && expire.isEmpty()) {
                expire = vipExpire(data.opt("vip_expire_time"), data.opt("expire_time"),
                        data.opt("vip_end_time"), data.opt("expire_date"));
            }
        }
        if (!vip) return "";
        String base = name.isEmpty() ? "VIP" : name;
        return expire.isEmpty() ? base : base + " · " + expire + " 到期";
    }

    private static boolean flagOn(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        if (v instanceof String) {
            String s = ((String) v).trim();
            return "1".equals(s) || "true".equalsIgnoreCase(s);
        }
        return false;
    }

    /** 到期值归一成 yyyy-MM-dd：字符串取前 10 位，数字按秒/毫秒时间戳换算 */
    private static String vipExpire(Object... candidates) {
        for (Object v : candidates) {
            if (v == null) continue;
            if (v instanceof Number) {
                long n = ((Number) v).longValue();
                if (n > 0) return formatDate(n < 100000000000L ? n * 1000 : n);
            } else if (v instanceof String) {
                String s = ((String) v).trim();
                if (s.length() >= 10) return s.substring(0, 10);
                try {
                    long n = Long.parseLong(s);
                    if (n > 0) return formatDate(n < 100000000000L ? n * 1000 : n);
                } catch (Exception ignored) {
                }
            }
        }
        return "";
    }

    private static String formatDate(long epochMs) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date(epochMs));
    }

    private static String formatSize(long size) {
        if (size < 0) return "";
        if (size < 1024) return size + "B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format(java.util.Locale.US, "%.1f%sB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    /** VIP 文案（如「VIP · 2027-01-01 到期」），须先 fetchAccountInfo */
    public String getVipText() {
        return vipText;
    }

    /** 容量摘要（如「已用 12GB / 共 2TB」），未取到返回空 */
    public String getSpaceText() {
        return spaceText;
    }

    @Override
    public String storageSummary() {
        if (spaceText.isEmpty()) fetchAccountInfo();
        return spaceText;
    }

    @Override
    public List<CloudFile> listDir(String cid, int offset, int limit) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("aid", "1");
        p.put("cid", cid == null ? "0" : cid);
        p.put("o", "user_ptime");
        p.put("asc", "0");
        p.put("offset", String.valueOf(offset));
        p.put("limit", String.valueOf(limit));
        p.put("show_dir", "1");
        p.put("snap", "0");
        p.put("natsort", "0");
        p.put("record_open_time", "0");
        p.put("format", "json");
        p.put("fc_mix", "0");
        p.put("count_folders", "1");
        p.put("custom_order", "1");
        JSONObject d = reqJson(API_FILES, p, null, null, null);
        if (!d.optBoolean("state", false) && d.has("error")) {
            throw new Exception(d.optString("error"));
        }
        List<CloudFile> files = new ArrayList<>();
        JSONArray arr = d.optJSONArray("data");
        if (arr == null) return files;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            CloudFile f = parseEntry(o);
            f.setParentCid(cid);
            files.add(f);
        }
        return files;
    }

    /**
     * 回包条目解析（listDir/searchFiles 共用）：短键与长键两套方言都认。
     * 短键＝PY 活口径（n/s/pc/sha/ico、fid/cid）；长键＝胖5 客户端在收的
     * 形态（file_name/file_size/pick_code、fid/file_id）。服务端按请求
     * 形态回哪套静态定不死，双认后哪套来了都不空表。parentCid 由调用方
     * 按各自口径回填（列表＝请求 cid、搜索＝条目自带归属）。
     * 文件判定：有 fid/file_id 即文件（搜索回包里文件的 cid 是其所在
     * 目录、不是自身），否则有 cid 即目录——短键旧口径原样保留。
     */
    private static CloudFile parseEntry(JSONObject o) {
        CloudFile f = new CloudFile();
        String fid = o.optString("fid");
        if (fid.isEmpty()) fid = o.optString("file_id");
        boolean isDir = fid.isEmpty() && o.has("cid");
        f.setDir(isDir);
        f.setId(isDir ? o.optString("cid") : fid);
        String name = o.optString("n");
        if (name.isEmpty()) name = o.optString("file_name");
        f.setName(name);
        long size = o.optLong("s", 0);
        if (size == 0) size = o.optLong("file_size", 0);
        f.setSize(size);
        String pc = o.optString("pc");
        if (pc.isEmpty()) pc = o.optString("pick_code");
        f.setPickCode(pc);
        String sha = o.optString("sha");
        if (sha.isEmpty()) sha = o.optString("sha1");
        f.setSha1(sha);
        f.setIcon(o.optString("ico"));
        if (!isDir) f.setThumbUrl(thumbOf(o));
        return f;
    }

    @Override
    public int getTotal(String cid) throws Exception {
        // listDir 返回的 count，这里简化处理
        return -1;
    }

    /** 回包条目的缩略图键：PY 活口径取 u、备选 thumb（115 给视频文件生成的封面直链，列表/搜索回包同构）。 */
    private static String thumbOf(JSONObject o) {
        String u = o.optString("u");
        if (u.isEmpty()) u = o.optString("thumb");
        return u;
    }

    /**
     * 全盘搜索文件（115 网页端搜索口径：webapi/files/search + search_value，
     * 参数集与 listDir 同构）。type 是服务端的类型过滤（0 全部／1 文件夹／
     * 2 文档／3 图片／4 视频／5 音频／6 压缩包，胖5 内置 115 工具表实证）：
     * 不传 type 时服务端只回文件夹、文件永远搜不到，必须显式传 0。
     * 本口径（type=0 全类型＋show_dir=1）是音乐搜 115 音频（CloudMusicSource
     * 靠全类型回包自己筛音频）的地基，口径不许变；网盘页内搜只出视频走
     * searchVideoFiles，别在这里改 type 连坐音乐链。
     */
    @Override
    public List<CloudFile> searchFiles(String keyword, int offset, int limit) throws Exception {
        return searchByType(keyword, offset, limit, "0", "1");
    }

    /**
     * 只搜视频（网盘页内搜专用，他 2026-10-28 亲点「不能只搜视频」）：
     * 服务端先按 type=4 只回视频类、show_dir=0 不带文件夹；回包再按本地
     * 口径兜底过滤一遍（只留非文件夹且 isVideo 的条目），服务端类目漏网的
     * 非视频/文件夹不许混进列表。offset/limit 与 searchFiles 同为服务端
     * 原始分页空间、1:1 透传，调用方的多翻页记账不变。
     * 待真机首验：type=4 的回包形状本机拿不到（cookie 已过期）；万一服务端
     * 类目与本地视频表对不齐、满页被兜底过滤成不满页，调用方会提前认尾页
     * ——装机验时重点看长结果列表会不会提前收尾。
     */
    public List<CloudFile> searchVideoFiles(String keyword, int offset, int limit) throws Exception {
        List<CloudFile> all = searchByType(keyword, offset, limit, "4", "0");
        List<CloudFile> videos = new ArrayList<>();
        for (CloudFile f : all) if (!f.isDir() && f.isVideo()) videos.add(f);
        try {
            com.github.catvod.crawler.SpiderDebug.log("pan115search",
                    "videoOnly kw=%s parsed=%s kept=%s", keyword, all.size(), videos.size());
        } catch (Throwable ignored) {
        }
        return videos;
    }

    /**
     * 搜索请求与解析主体（searchFiles/searchVideoFiles 共用，差别只在
     * type 与 show_dir 两个参数）。搜索结果的文件项里 cid 是其所在目录、
     * 目录项里 pid 是其上级目录，parentCid 按此回填（供从结果直达所在目录）。
     * 缩略图按 PY 活口径取 u（备选 thumb）；顶层键、首条目键名与缩略图有无
     * 随调试日志打出，真机一跑即可对死。
     */
    private List<CloudFile> searchByType(String keyword, int offset, int limit, String type, String showDir) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("aid", "1");
        p.put("cid", "0");
        p.put("search_value", keyword == null ? "" : keyword.trim());
        p.put("o", "user_ptime");
        p.put("asc", "0");
        p.put("offset", String.valueOf(offset));
        p.put("limit", String.valueOf(limit));
        p.put("show_dir", showDir);
        p.put("type", type);
        p.put("format", "json");
        JSONObject d = reqJson(API_SEARCH, p, null, null, null);
        if (!d.optBoolean("state", false) && d.has("error")) {
            throw new Exception(d.optString("error"));
        }
        List<CloudFile> files = new ArrayList<>();
        JSONArray arr = d.optJSONArray("data");
        if (arr == null) {
            // PY 活口径防御：个别响应 data 是对象，列表在其 data/list 里
            JSONObject dataObj = d.optJSONObject("data");
            if (dataObj != null) {
                arr = dataObj.optJSONArray("data");
                if (arr == null) arr = dataObj.optJSONArray("list");
            }
        }
        if (arr == null) return files;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            CloudFile f = parseEntry(o);
            // 结果不在当前目录下，parentCid 只能取结果项自带的归属，别沿用 listDir 的请求 cid
            f.setParentCid(f.isDir() ? o.optString("pid") : o.optString("cid"));
            files.add(f);
        }
        try {
            String itemKeys = "";
            boolean itemThumb = false;
            if (arr.length() > 0) {
                JSONObject first = arr.optJSONObject(0);
                if (first != null) {
                    itemKeys = String.valueOf(first.names());
                    itemThumb = !thumbOf(first).isEmpty();
                }
            }
            com.github.catvod.crawler.SpiderDebug.log("pan115search",
                    "kw=%s keys=%s count=%s got=%s itemKeys=%s itemThumb=%s",
                    keyword, String.valueOf(d.names()),
                    String.valueOf(d.opt("count")), files.size(), itemKeys, itemThumb);
        } catch (Throwable ignored) {
        }
        return files;
    }

    @Override
    public boolean delete(List<String> ids, boolean isDir) throws Exception {
        // 115 web API：POST https://webapi.115.com/rb/delete
        // PY 未实现，按 web 端标准接口
        Map<String, String> form = new HashMap<>();
        form.put("aid", "1");
        for (int i = 0; i < ids.size(); i++) {
            form.put((isDir ? "cid" : "fid") + "[" + i + "]", ids.get(i));
        }
        JSONObject d = reqJson("https://webapi.115.com/rb/delete", null, form, null, null);
        return d.optBoolean("state", false);
    }

    /**
     * 新建文件夹（webapi /files/add，胖5 files.rs 同名口）：pid＋cname。
     * 回包 data 里带新目录的 cid/file_name，取不到就按入参回填（列表随后会刷新）。
     * 参数与回包形状待新版装机首验（本机 cookie 已过期、拿不到真回包）。
     */
    @Override
    public CloudFile createFolder(String parentId, String name) throws Exception {
        Map<String, String> form = new HashMap<>();
        form.put("pid", parentId == null ? "0" : parentId);
        form.put("cname", name == null ? "" : name.trim());
        JSONObject d = reqJson("https://webapi.115.com/files/add", null, form, null, null);
        if (!d.optBoolean("state", false)) {
            throw new Exception(d.optString("error", "新建文件夹失败"));
        }
        CloudFile f = new CloudFile();
        f.setDir(true);
        JSONObject data = d.optJSONObject("data");
        String cid = data == null ? "" : data.optString("cid");
        if (cid.isEmpty()) cid = d.optString("cid");
        f.setId(cid);
        String rn = data == null ? "" : data.optString("file_name");
        f.setName(rn.isEmpty() ? (name == null ? "" : name.trim()) : rn);
        f.setParentCid(parentId == null ? "0" : parentId);
        return f;
    }

    /** 重命名（webapi /files/edit）：文件用 fid、目录用 cid（与 delete 的键口径同），新名走 file_name。待装机首验。 */
    public boolean rename(CloudFile file, String newName) throws Exception {
        Map<String, String> form = new HashMap<>();
        form.put(file.isDir() ? "cid" : "fid", file.getId());
        form.put("file_name", newName == null ? "" : newName.trim());
        JSONObject d = reqJson("https://webapi.115.com/files/edit", null, form, null, null);
        return d.optBoolean("state", false);
    }

    /** 移动（webapi /files/move）：pid 是目标目录，条目键与 delete 同口径。待装机首验。 */
    public boolean move(CloudFile file, String targetCid) throws Exception {
        Map<String, String> form = new HashMap<>();
        form.put("pid", targetCid == null ? "0" : targetCid);
        form.put((file.isDir() ? "cid" : "fid") + "[0]", file.getId());
        JSONObject d = reqJson("https://webapi.115.com/files/move", null, form, null, null);
        return d.optBoolean("state", false);
    }

    /** 复制（webapi /files/copy）：形态与 move 同，只换口。待装机首验。 */
    public boolean copy(CloudFile file, String targetCid) throws Exception {
        Map<String, String> form = new HashMap<>();
        form.put("pid", targetCid == null ? "0" : targetCid);
        form.put((file.isDir() ? "cid" : "fid") + "[0]", file.getId());
        JSONObject d = reqJson("https://webapi.115.com/files/copy", null, form, null, null);
        return d.optBoolean("state", false);
    }

    @Override
    public boolean offlineDownload(String url, String cid) throws Exception {
        url = url == null ? "" : url.trim();
        if (url.isEmpty()) throw new Exception("链接是空的");
        String low = url.toLowerCase();
        if (!(low.startsWith("magnet:") || low.startsWith("ed2k://")
                || low.startsWith("http://") || low.startsWith("https://")
                || low.startsWith("thunder://") || low.startsWith("ftp://"))) {
            throw new Exception("这不像磁力 / ed2k / 直链");
        }
        Map<String, String> form = new HashMap<>();
        form.put("url", url);
        form.put("wp_path_id", cid == null ? "0" : cid);
        form.put("savepath", "");
        JSONObject d = lixian("add_task_url", form, null);
        return d.optBoolean("state", false);
    }

    private JSONObject lixian(String ac, Map<String, String> form, Map<String, String> params) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("ct", "lixian");
        p.put("ac", ac);
        p.put("_", nowMs());
        if (params != null) p.putAll(params);
        return reqJson(API_LIXIAN, p, form, UA_WEB, "https://115.com/?ct=lixian");
    }

    @Override
    public List<CloudTask> taskList(int page) throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("page", String.valueOf(page));
        JSONObject d = lixian("task_lists", null, p);
        List<CloudTask> tasks = new ArrayList<>();
        JSONArray arr = d.optJSONArray("tasks");
        if (arr == null) return tasks;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            CloudTask t = new CloudTask();
            t.setHash(o.optString("info_hash"));
            t.setName(o.optString("name"));
            t.setSize(o.optLong("size", 0));
            t.setStatus(o.optInt("status", 0));
            t.setPercent(o.optInt("percentDone", 0));
            tasks.add(t);
        }
        return tasks;
    }

    @Override
    public boolean taskDelete(List<String> hashes) throws Exception {
        Map<String, String> form = new HashMap<>();
        for (int i = 0; i < hashes.size(); i++) {
            form.put("hash[" + i + "]", hashes.get(i));
        }
        JSONObject d = lixian("task_del", form, null);
        return d.optBoolean("state", false);
    }

    /**
     * 取原画直链（对照 PY Api.down_url）
     * 需要 115 接口加密（m115_encode）
     */
    public String downUrl(String pickcode) throws Exception {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        String payload = new JSONObject().put("pickcode", pickcode).toString();
        String data = Crypto115.m115Encode(payload.getBytes("UTF-8"), key);
        Map<String, String> params = new HashMap<>();
        params.put("t", nowMs());
        Map<String, String> form = new HashMap<>();
        form.put("data", data);
        JSONObject d = reqJson(API_DOWNURL, params, form, UA_115, "https://115.com/");
        if (!d.optBoolean("state", false)) {
            throw new Exception("取直链失败：" + d.optString("error", d.optString("msg", "")));
        }
        String enc = d.optString("data");
        if (TextUtils.isEmpty(enc)) throw new Exception("取直链失败：返回数据为空");
        String json = new String(Crypto115.m115Decode(enc, key), "UTF-8");
        JSONObject info = new JSONObject(json);
        JSONArray names = info.names();
        if (names == null || names.length() == 0) throw new Exception("取直链失败：解析结果为空");
        JSONObject item = info.optJSONObject(names.getString(0));
        if (item == null) throw new Exception("取直链失败：结构异常");
        JSONObject u = item.optJSONObject("url");
        String url = u != null ? u.optString("url") : item.optString("url");
        if (TextUtils.isEmpty(url)) throw new Exception("取直链失败：没有下载地址");
        return url;
    }

    /** m3u8 地址（对照 PY Api.m3u8_url） */
    public String m3u8Url(String pickcode) {
        return String.format(API_M3U8, q(pickcode));
    }

    @Override
    public String getPlayUrl(CloudFile file) throws Exception {
        if (file == null || TextUtils.isEmpty(file.getPickCode())) {
            throw new Exception("文件 pick_code 为空");
        }
        // 走本机代理（对照 PY _play_result）：播放器永远不直连 115
        // 返回 http://127.0.0.1:PORT/v?t=TOKEN&u=BASE64(真地址)，playUrl 为空
        if (playUrlBuilder == null) {
            playUrlBuilder = new PlayUrlBuilder115(this, client, cookieJar);
        }
        // 确保代理已启动（对照 PY _via_proxy）
        Proxy115.getInstance(client, cookieJar).startProxy();
        PlayUrlBuilder115.PlayRequest req = playUrlBuilder.build(file, "auto");
        return req.proxyUrl;
    }

    /**
     * 取原画播放地址（供用户手动选原画）
     */
    public String getPlayUrlOrg(CloudFile file) throws Exception {
        if (file == null || TextUtils.isEmpty(file.getPickCode())) {
            throw new Exception("文件 pick_code 为空");
        }
        if (playUrlBuilder == null) {
            playUrlBuilder = new PlayUrlBuilder115(this, client, cookieJar);
        }
        Proxy115.getInstance(client, cookieJar).startProxy();
        PlayUrlBuilder115.PlayRequest req = playUrlBuilder.build(file, "org");
        return req.proxyUrl;
    }

    /** /files/video 解析结果缓存（对照 PY _all_tiers：同一文件 15 分钟内只问一次接口） */
    private static class VideoInfoEntry {
        final long time;
        final TierParser115.VideoInfo info;

        VideoInfoEntry(long time, TierParser115.VideoInfo info) {
            this.time = time;
            this.info = info;
        }
    }

    private final Map<String, VideoInfoEntry> videoInfoCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long VIDEO_INFO_CACHE_MS = 15 * 60 * 1000;

    /**
     * 取 /files/video 解析结果（画质多档 + 原画，TierParser115 解析）。
     * 端点按 PY 移植源的活口径：webapi.115.com/files/video?pickcode=；
     * 请求失败不进缓存（下次可重试），解析成功（含空结果）缓存 15 分钟。
     * 字段未真机对死：响应顶层键与解析结果打调试日志，真机一跑即可对字段。
     */
    public TierParser115.VideoInfo videoInfo(String pickcode) {
        TierParser115.VideoInfo empty = new TierParser115.VideoInfo();
        if (TextUtils.isEmpty(pickcode)) return empty;
        VideoInfoEntry hit = videoInfoCache.get(pickcode);
        if (hit != null && (System.currentTimeMillis() - hit.time) < VIDEO_INFO_CACHE_MS) {
            return hit.info;
        }
        try {
            Map<String, String> p = new HashMap<>();
            p.put("pickcode", pickcode);
            JSONObject d = reqJson(API_VIDEO, p, null, UA_115, "https://115.com/");
            TierParser115.VideoInfo info = TierParser115.parse(d);
            videoInfoCache.put(pickcode, new VideoInfoEntry(System.currentTimeMillis(), info));
            StringBuilder names = new StringBuilder();
            for (TierParser115.Tier t : info.tiers) {
                if (names.length() > 0) names.append(",");
                names.append(t.name);
            }
            android.util.Log.d("Drive115", "videoInfo pc=" + pickcode
                    + " keys=" + String.valueOf(d.names())
                    + " tiers=" + info.tiers.size() + " [" + names + "]"
                    + " origin=" + (!info.originUrl.isEmpty()));
            return info;
        } catch (Exception e) {
            android.util.Log.d("Drive115", "videoInfo 失败: " + e.getMessage());
            return empty;
        }
    }

    /**
     * 转码画质列表（对照 PY _all_tiers，15 分钟缓存）
     * @return List of {name, url}，url 是原始直链（未包代理，包代理在 PlayUrlBuilder115）
     */
    public List<Map<String, String>> videoTiers(String pickcode) {
        List<Map<String, String>> result = new ArrayList<>();
        for (TierParser115.Tier t : videoInfo(pickcode).tiers) {
            Map<String, String> m = new HashMap<>();
            m.put("name", t.name);
            m.put("url", t.url);
            result.add(m);
        }
        return result;
    }

    /** /files/video 给的原画直链（origin_file_url），没有返回空串 */
    public String videoOriginUrl(String pickcode) {
        return videoInfo(pickcode).originUrl;
    }

    /**
     * 播放清单（共两条：原画＋转码最高档一条，地址已包本机代理，默认起播原画）：
     * 供网盘文件点击走自己的播放会话（选集即画质档）。
     */
    public PlayUrlBuilder115.PlayList playList(CloudFile file) throws Exception {
        if (file == null || TextUtils.isEmpty(file.getPickCode())) {
            throw new Exception("文件 pick_code 为空");
        }
        if (playUrlBuilder == null) {
            playUrlBuilder = new PlayUrlBuilder115(this, client, cookieJar);
        }
        Proxy115.getInstance(client, cookieJar).startProxy();
        return playUrlBuilder.buildPlayList(file);
    }

    /** 暴露 cookie 罐（供代理层用） */
    public CookieJar115 getCookieJar() {
        return cookieJar;
    }

    /** 暴露 OkHttpClient（取链取流同一实例，对照 PY L420） */
    public OkHttpClient getClient() {
        return client;
    }

    /**
     * 转存分享链接（best-effort：端点来自 115driver 开源常量 + 115 web 常规流程，未经真机全验证）
     * 分享链接形如 https://115.com/s/{share_code} 或带 #receive_code
     */
    @Override
    public boolean saveShare(String shareUrl, String password, String toCid) throws Exception {
        String url = shareUrl == null ? "" : shareUrl.trim();
        if (url.isEmpty()) throw new Exception("分享链接是空的");
        // 提取 share_code：取 /s/ 之后、?/# 之前的部分
        String code = url;
        int si = code.indexOf("/s/");
        if (si >= 0) code = code.substring(si + 3);
        int qi = code.indexOf('?');
        if (qi >= 0) code = code.substring(0, qi);
        int hi = code.indexOf('#');
        String receiveCode = password == null ? "" : password.trim();
        if (hi >= 0) {
            if (receiveCode.isEmpty()) receiveCode = code.substring(hi + 1);
            code = code.substring(0, hi);
        }
        code = code.trim();
        if (code.isEmpty()) throw new Exception("解析不出分享码");
        // 1) snap 取分享信息
        Map<String, String> p = new HashMap<>();
        p.put("share_code", code);
        p.put("receive_code", receiveCode);
        JSONObject snap = reqJson("https://webapi.115.com/share/snap", p, null, null, null);
        if (!snap.optBoolean("state", false)) {
            throw new Exception("获取分享信息失败：" + snap.optString("error", snap.optString("msg", "")));
        }
        // 2) receive 转存到 toCid
        Map<String, String> form = new HashMap<>();
        form.put("share_code", code);
        form.put("receive_code", receiveCode);
        form.put("cid", toCid == null ? "0" : toCid);
        JSONObject d = reqJson("https://webapi.115.com/share/receive", null, form, null, null);
        if (!d.optBoolean("state", false)) {
            throw new Exception("转存失败：" + d.optString("error", d.optString("msg", "")));
        }
        return true;
    }
}
