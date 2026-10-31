package com.fongmi.android.tv.api;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 红果评论壳子抓取器：详情/播放侧只认本类吐出的评论结构，不碰 fqnovel 字段名。
 * 走壳子全局 OkHttp 直连（境内服务），不挂任何源归属标记、不登记任何认领表（设计红线）。
 * 全部方法同步执行、失败返回 null（静默降级），绝不抛异常进播放链。
 * 接口形态逐字段照 hongguo-comments-shell-design.md §1（双证锁定），签名见 FqCommentSigner。
 */
public class HongguoCommentApi {

    private static final String HOST = "https://api5-normal-sinfonlineb.fqnovel.com";
    private static final String UA = "com.phoenix.read/73532 (Linux; U; Android 16; zh_CN; 25053RT47C; Build/BP2A.250605.031.A3; Cronet/TTNetVersion:04657795 2026-01-23 QuicVersion:c67e9834 2025-09-08)";
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static volatile OkHttpClient client;
    private static volatile String deviceId;
    private static volatile String iid;

    public static class Comment {
        public final String id;
        public final String author;
        public final String avatar;
        public final String content;
        public final long created;
        public final long likes;
        /** 回复数（comment.stat.reply_count，2026-10-24 真响应验实）。 */
        public final long replyCount;
        /** 仅剧评条目有值（十分制字符串），其余为 null。 */
        public final String score;
        /** 仅回复条目有值：被回复人昵称，其余为 null。 */
        public final String replyTo;

        Comment(String id, String author, String avatar, String content, long created, long likes, long replyCount, String score, String replyTo) {
            this.id = id;
            this.author = author;
            this.avatar = avatar;
            this.content = content;
            this.created = created;
            this.likes = likes;
            this.replyCount = replyCount;
            this.score = score;
            this.replyTo = replyTo;
        }
    }

    /**
     * 本地回显条目（发评论成功后即时插顶上屏用）：id 为服务端评论
     * id（摘不到传空串占位，下次回拉校准）；created 置 0——评论行
     * 日期段对 0 不渲染（HgCommentMeta），不拿本地时间编造日期；
     * 点赞/回复数从 0 起。
     */
    public static Comment localEcho(String id, String author, String avatar, String content) {
        return new Comment(id == null ? "" : id,
                author == null ? "" : author,
                avatar == null ? "" : avatar,
                content == null ? "" : content,
                0, 0, 0, null, null);
    }

    public static class Page {
        public final List<Comment> items;
        public final long total;
        public final String nextCursor;
        public final boolean hasMore;
        /** 仅剧评首页可能有值：剧均评分与评分人数，其余为 null / 0。 */
        public final String score;
        public final long scoreCount;

        Page(List<Comment> items, long total, String nextCursor, boolean hasMore, String score, long scoreCount) {
            this.items = items;
            this.total = total;
            this.nextCursor = nextCursor;
            this.hasMore = hasMore;
            this.score = score;
            this.scoreCount = scoreCount;
        }
    }

    // ---------- 识别 ----------

    /** 剥非数字前缀（如 hg-series-v1:）后校验 ^[0-9]{6,30}$，对不上返回空串。 */
    public static String cleanSeriesId(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replaceFirst("^[^0-9]+", "");
        return isId(s) ? s : "";
    }

    public static boolean isId(String s) {
        if (s == null || s.length() < 6 || s.length() > 30) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    /**
     * 头像地址放行判据：红果接口约半数头像是 http:// 明文下发（本机实测 301 升
     * https 后全 200 真图，样本统计见 hongguo-avatar-partial-trace.md），旧守卫只认
     * https:// 把这批在解析期清零、评论里成批只剩占位。http 与 https 均放行，
     * 长度上限在调用处另判。
     */
    static boolean isAvatarUrl(String s) {
        return s != null && (s.startsWith("https://") || s.startsWith("http://"));
    }

    /** 从选集条目 URL（hg-episode-v1:<清晰度>:<vid>）拆单集 vid，拆不到返回空串。 */
    public static String episodeVid(String episodeUrl) {
        if (episodeUrl == null || !episodeUrl.startsWith("hg-episode-v1:")) return "";
        String[] parts = episodeUrl.split(":");
        if (parts.length != 3) return "";
        String vid = parts[2].trim();
        return isId(vid) ? vid : "";
    }

    // ---------- 抓取入口（失败一律 null） ----------

    /** 单集评论一页。cursor 首页传空串，翻页传上一页 Page.nextCursor 原样回传。 */
    public static Page episodeComments(String seriesId, String vid, String cursor) {
        if (!isId(seriesId) || !isId(vid)) return null;
        String cur = cursor == null ? "" : cursor;
        if (validateJsonCursor(cur) < 0) return null;
        try {
            JSONObject payload = new JSONObject()
                    .put("comment_source", 4).put("server_channel", 18)
                    .put("group_id", vid).put("group_type", 30)
                    .put("comment_type", 4).put("sort", 1).put("need_count", true)
                    .put("count", 20).put("cursor", cur).put("aid", 8662).put("compliance_status", 0)
                    .put("business_param", new JSONObject().put("book_id", seriesId).put("req_type", 0));
            JSONObject result = post("comment/list/" + vid, payload, 4, 18);
            return parseList(result, seriesId, vid, cur, false);
        } catch (Exception e) {
            return null;
        }
    }

    /** 整剧剧评一页。cursor 首页传空串（请求体不带 cursor 键），翻页传 nextCursor。 */
    public static Page seriesComments(String seriesId, String cursor) {
        if (!isId(seriesId)) return null;
        String cur = cursor == null ? "" : cursor;
        if (validateJsonCursor(cur) < 0) return null;
        try {
            JSONObject payload = new JSONObject()
                    .put("business_param", new JSONObject().put("book_id", seriesId).put("need_count", true).put("real_level", 2).put("req_type", 0))
                    .put("comment_source", 1).put("comment_type", 2)
                    .put("count", 10).put("group_id", seriesId).put("group_type", 1)
                    .put("server_channel", 34).put("sort", 1);
            if (!cur.isEmpty()) payload.put("cursor", cur);
            JSONObject result = post("comment/list/" + seriesId, payload, 1, 34);
            return parseList(result, seriesId, seriesId, cur, true);
        } catch (Exception e) {
            return null;
        }
    }

    /** 单集评论的回复一页。cursor 首页传空串。 */
    public static Page episodeReplies(String seriesId, String vid, String commentId, String cursor) {
        if (!isId(seriesId) || !isId(vid) || commentId == null || commentId.isEmpty()) return null;
        String cur = cursor == null ? "" : cursor;
        if (validateNumCursor(cur) < 0) return null;
        try {
            JSONObject payload = new JSONObject()
                    .put("aid", 8662)
                    .put("business_param", new JSONObject().put("book_id", seriesId).put("need_count", false))
                    .put("comment_id", commentId).put("comment_source", 504).put("comment_type", 4)
                    .put("compliance_status", 0).put("count", 10).put("cursor", cur)
                    .put("group_id", vid).put("group_type", 30).put("server_channel", 18);
            JSONObject result = post("reply/list/" + commentId, payload, 504, 18);
            return parseReplies(result, seriesId, vid, cur);
        } catch (Exception e) {
            return null;
        }
    }

    /** 剧评的回复一页。cursor 首页传空串（请求体不带 cursor 键）。 */
    public static Page seriesReplies(String seriesId, String commentId, String cursor) {
        if (!isId(seriesId) || commentId == null || commentId.isEmpty()) return null;
        String cur = cursor == null ? "" : cursor;
        if (validateNumCursor(cur) < 0) return null;
        try {
            JSONObject payload = new JSONObject()
                    .put("business_param", new JSONObject().put("book_id", seriesId).put("need_count", cur.isEmpty()).put("real_level", 2))
                    .put("comment_id", commentId).put("comment_source", 501).put("comment_type", 2)
                    .put("count", 10).put("group_id", seriesId).put("group_type", 1)
                    .put("server_channel", 34);
            if (!cur.isEmpty()) payload.put("cursor", cur);
            JSONObject result = post("reply/list/" + commentId, payload, 501, 34);
            return parseReplies(result, seriesId, seriesId, cur);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 解析（含回验闸） ----------

    static Page parseList(JSONObject result, String series, String group, String cursor, boolean seriesDim) throws Exception {
        if (result == null || result.optInt("code", -1) != 0) return null;
        long previous = validateJsonCursor(cursor);
        JSONObject data = result.getJSONObject("data");
        JSONObject info = data.getJSONObject("common_list_info");
        boolean more = info.getBoolean("has_more");
        String next = info.optString("cursor");
        if (more && (next.equals(cursor) || validateJsonCursor(next) <= previous)) return null;
        JSONArray rows = data.getJSONArray("data_list");
        List<Comment> items = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < Math.min(rows.length(), 100); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            JSONObject comment = row.optJSONObject("comment");
            if (comment == null) continue;
            JSONObject common = comment.optJSONObject("common");
            if (common == null) continue;
            JSONObject expand = comment.optJSONObject("expand");
            if (expand == null) continue;
            // 回验闸：归属对不上整页作废，防误抓别家评论
            if (!group.equals(common.optString("group_id")) || !series.equals(expand.optString("book_id"))) return null;
            if (common.optInt("status", -1) != 1) continue;
            String id = comment.optString("comment_id");
            JSONObject contentObj = common.optJSONObject("content");
            String content = contentObj == null ? "" : contentObj.optString("text").trim();
            if (id.isEmpty() || id.length() > 120 || content.isEmpty() || !seen.add(id)) continue;
            JSONObject base = optBaseInfo(common);
            String avatar = base == null ? "" : base.optString("user_avatar");
            if (avatar.length() > 4096 || !isAvatarUrl(avatar)) avatar = "";
            JSONObject stat = comment.optJSONObject("stat");
            long likes = stat != null && stat.has("digg_count") ? stat.optLong("digg_count", 0) : likesOf(row, common, expand);
            long replyCount = stat == null ? 0 : Math.max(0, stat.optLong("reply_count", 0));
            String score = null;
            if (seriesDim) {
                String s = expand.optString("score");
                if (!s.isEmpty()) score = s;
            }
            items.add(new Comment(id,
                    base == null ? "" : truncate(base.optString("user_name"), 200),
                    avatar, truncate(content, 20000),
                    Math.max(0, common.optLong("create_timestamp", 0)), Math.max(0, likes), replyCount, score, null));
        }
        String score = null;
        long scoreCount = 0;
        if (seriesDim) {
            JSONObject extra = data.optJSONObject("extra");
            if (extra != null) {
                JSONObject bookInfo = extra.optJSONObject("book_info");
                String s = bookInfo == null ? "" : bookInfo.optString("score");
                if (s.isEmpty()) s = extra.optString("credibility_score");
                if (!s.isEmpty()) score = s;
                scoreCount = extra.optLong("score_cnt", 0);
                if (scoreCount <= 0) scoreCount = extra.optLong("credibility_score_count", 0);
            }
        }
        return new Page(items, Math.max(0, info.optLong("total", 0)), more ? next : "", more, score, Math.max(0, scoreCount));
    }

    static Page parseReplies(JSONObject result, String series, String group, String cursor) throws Exception {
        if (result == null || result.optInt("code", -1) != 0) return null;
        long previous = validateNumCursor(cursor);
        JSONObject data = result.getJSONObject("data");
        // 回验闸：父评论在场时验归属
        JSONObject parent = data.optJSONObject("comment");
        if (parent != null) {
            JSONObject pExpand = parent.optJSONObject("expand");
            if (pExpand != null && pExpand.has("book_id") && !series.equals(pExpand.optString("book_id"))) return null;
            JSONObject pCommon = parent.optJSONObject("common");
            if (pCommon != null && pCommon.has("group_id") && !group.equals(pCommon.optString("group_id"))) return null;
        }
        JSONObject info = data.getJSONObject("comment_list_info");
        boolean more = info.optBoolean("has_more", false);
        String next = info.optString("cursor");
        if (more && validateNumCursor(next) <= previous) return null;
        JSONArray rows = data.optJSONArray("reply_list");
        List<Comment> items = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (rows != null) {
            for (int i = 0; i < Math.min(rows.length(), 100); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                JSONObject common = row.optJSONObject("Common");
                if (common == null) common = row.optJSONObject("common");
                if (common == null) continue;
                String id = row.optString("reply_id");
                JSONObject contentObj = common.optJSONObject("content");
                String content = contentObj == null ? "" : contentObj.optString("text").trim();
                if (id.isEmpty() || id.length() > 120 || content.isEmpty() || !seen.add(id)) continue;
                JSONObject base = optBaseInfo(common);
                String avatar = "";
                if (base != null) {
                    avatar = base.optString("user_avatar");
                    if (avatar.isEmpty()) avatar = base.optString("expand_user_avatar");
                    if (avatar.length() > 4096 || !isAvatarUrl(avatar)) avatar = "";
                }
                JSONObject stat = row.optJSONObject("stat");
                long likes = stat == null ? 0 : Math.max(0, stat.optLong("digg_count", 0));
                long replyCount = stat == null ? 0 : Math.max(0, stat.optLong("reply_count", 0));
                String replyTo = null;
                JSONObject toInfo = row.optJSONObject("reply_to_user_info");
                if (toInfo != null) {
                    JSONObject toBase = toInfo.optJSONObject("base_info");
                    if (toBase != null && !toBase.optString("user_name").isEmpty()) replyTo = toBase.optString("user_name");
                }
                items.add(new Comment(id,
                        base == null ? "" : truncate(base.optString("user_name"), 200),
                        avatar, truncate(content, 20000),
                        Math.max(0, common.optLong("create_timestamp", 0)), likes, replyCount, null, replyTo));
            }
        }
        return new Page(items, Math.max(0, info.optLong("total", 0)), more ? next : "", more, null, 0);
    }

    private static JSONObject optBaseInfo(JSONObject common) {
        JSONObject userInfo = common.optJSONObject("user_info");
        return userInfo == null ? null : userInfo.optJSONObject("base_info");
    }

    private static long likesOf(JSONObject row, JSONObject common, JSONObject expand) {
        JSONObject stat = row.optJSONObject("stat");
        if (stat != null && stat.has("digg_count")) return stat.optLong("digg_count", 0);
        if (expand.has("digg_count")) return expand.optLong("digg_count", 0);
        return common.optLong("digg_count", 0);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** JSON 游标（comment/list 用）：空串合法（offset 0），非法返回 -1。 */
    private static long validateJsonCursor(String cursor) {
        if (cursor == null || cursor.length() > 8192) return -1;
        if (cursor.trim().isEmpty()) return 0;
        try {
            JSONObject data = new JSONObject(cursor);
            String session = data.getString("session_id");
            if (session.isEmpty() || session.length() > 200) return -1;
            long offset = data.getLong("offset");
            return offset >= 0 && offset <= 10000000 ? offset : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 数字游标（reply/list 用）：空串合法（0），非法返回 -1。 */
    private static long validateNumCursor(String cursor) {
        if (cursor == null || cursor.length() > 32) return -1;
        if (cursor.trim().isEmpty()) return 0;
        try {
            long v = Long.parseLong(cursor.trim());
            return v >= 0 && v <= 10000000 ? v : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    // ---------- 传输 ----------

    /** 同包共用传输（HongguoDanmakuApi 复用同一签名器/设备号/直连口径），非同包不可见。 */
    static JSONObject post(String path, JSONObject payload, int source, int channel) throws Exception {
        long now = System.currentTimeMillis();
        Map<String, String> query = new TreeMap<>();
        query.put("aid", "8662");
        query.put("app_name", "novelread");
        query.put("version_code", "73532");
        query.put("version_name", "7.3.5.32");
        query.put("manifest_version_code", "73532");
        query.put("update_version_code", "73532");
        query.put("channel", "update_64");
        query.put("device_platform", "android");
        query.put("os", "android");
        query.put("ssmix", "a");
        query.put("device_type", "25053RT47C");
        query.put("device_brand", "Redmi");
        query.put("language", "zh");
        query.put("os_api", "36");
        query.put("os_version", "16");
        query.put("resolution", "1280*2772");
        query.put("dpi", "520");
        query.put("ac", "wifi");
        query.put("device_id", deviceId());
        query.put("iid", iid());
        query.put("_rticket", String.valueOf(now));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : query.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        String encoded = sb.toString();
        Request.Builder request = new Request.Builder()
                .url(HOST + "/novel/commentapi/" + path + "/v1/?" + encoded)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .header("X-XS-From-Web", "0")
                .header("Sdk-Version", "2")
                .header("Comment-Source", String.valueOf(source))
                .header("Server-Channel", String.valueOf(channel))
                .post(RequestBody.create(payload.toString(), JSON));
        for (Map.Entry<String, String> e : FqCommentSigner.headers(encoded, now, randomNonce()).entrySet()) {
            request.header(e.getKey(), e.getValue());
        }
        try (Response response = httpClient().newCall(request.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IllegalStateException("http " + response.code());
            byte[] bytes = readLimited(response.body().byteStream(), MAX_BYTES);
            if (bytes.length > MAX_BYTES) throw new IllegalStateException("response too large");
            return new JSONObject(new String(bytes, "UTF-8"));
        }
    }

    private static byte[] randomNonce() {
        byte[] nonce = new byte[11];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    private static byte[] readLimited(InputStream in, int limit) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (out.size() <= limit) {
            int count = in.read(buffer, 0, Math.min(buffer.length, limit + 1 - out.size()));
            if (count < 0) break;
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private static OkHttpClient httpClient() {
        if (client != null) return client;
        synchronized (HongguoCommentApi.class) {
            if (client != null) return client;
            OkHttpClient built;
            try {
                built = OkHttp.client().newBuilder()
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .writeTimeout(20, TimeUnit.SECONDS)
                        .callTimeout(20, TimeUnit.SECONDS)
                        .followRedirects(false)
                        .build();
            } catch (Throwable t) {
                // 非 Android 运行时（本机 JVM 验证）回退裸 client；设备上恒走壳子全局 client
                built = new OkHttpClient.Builder()
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .writeTimeout(20, TimeUnit.SECONDS)
                        .callTimeout(20, TimeUnit.SECONDS)
                        .followRedirects(false)
                        .build();
            }
            client = built;
            return built;
        }
    }

    /** 仅供本机 JVM 验证注入裸 client，生产永不调用。 */
    static void setClientForTest(OkHttpClient testClient) {
        client = testClient;
    }

    // ---------- 设备号（首用随机生成、持久化复用） ----------

    private static String deviceId() {
        if (deviceId != null) return deviceId;
        synchronized (HongguoCommentApi.class) {
            if (deviceId == null) deviceId = loadOrCreate("fq_comment_device_id", "fq.comment.device_id");
            return deviceId;
        }
    }

    private static String iid() {
        if (iid != null) return iid;
        synchronized (HongguoCommentApi.class) {
            if (iid == null) iid = loadOrCreate("fq_comment_iid", "fq.comment.iid");
            return iid;
        }
    }

    private static String loadOrCreate(String key, String prop) {
        String fromProp = System.getProperty(prop);
        if (fromProp != null && isId(fromProp)) return fromProp;
        try {
            String saved = Prefers.getString(key);
            if (isId(saved)) return saved;
        } catch (Throwable ignored) {
        }
        String created = randomDeviceNumber();
        try {
            Prefers.put(key, created);
        } catch (Throwable ignored) {
        }
        return created;
    }

    private static String randomDeviceNumber() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(19);
        sb.append(7);
        for (int i = 0; i < 18; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }
}
