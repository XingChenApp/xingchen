package com.fongmi.android.tv.api;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

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
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 红果评论壳子抓取器：详情/播放侧只认本类吐出的评论结构，不碰 fqnovel 字段名。
 * 走壳子全局 OkHttp 直连（境内服务），不挂任何源归属标记、不登记任何认领表（设计红线）。
 * 全部方法同步执行，绝不抛异常进播放链。失败返回 null 是失败信号、与
 * 「真没有」的空 Page 分开：调用方据 null 亮失败态/重试（评论面各口
 * 已分开呈现），不许把 null 当空集；失败原因经 log() 记下供定案。
 * 接口形态逐字段照 hongguo-comments-shell-design.md §1（双证锁定），签名见 FqCommentSigner。
 */
public class HongguoCommentApi {

    private static final String HOST = "https://api5-normal-sinfonlineb.fqnovel.com";
    private static final String UA = "com.phoenix.read/73532 (Linux; U; Android 16; zh_CN; 25053RT47C; Build/BP2A.250605.031.A3; Cronet/TTNetVersion:04657795 2026-01-23 QuicVersion:c67e9834 2025-09-08)";
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static volatile OkHttpClient client;

    /** 失败打点（与 HongguoDanmakuApi 同口径）：只记日志、绝不影响行为——日志通道自身出问题也吞掉，不许日志反过来打断取数。 */
    private static void log(String msg, Object... args) {
        try {
            SpiderDebug.log("hongguo-comment", msg, args);
        } catch (Throwable ignored) {
        }
    }

    public static class Comment {
        public final String id;
        public final String author;
        public final String avatar;
        public final String content;
        public final long created;
        /** 点赞数（stat.digg_count）：行内点赞乐观翻转要改写，故非 final。 */
        public long likes;
        /** 回复数（comment.stat.reply_count，2026-10-24 真响应验实）。 */
        public final long replyCount;
        /** 仅剧评条目有值（十分制字符串），其余为 null。 */
        public final String score;
        /** 仅回复条目有值：被回复人昵称，其余为 null。 */
        public final String replyTo;
        /** 作者 user_id（base_info.user_id，回复发送的 reply_to_userid 用）。 */
        public final String authorId;
        /** 本人已赞（comment.user_action.user_digg，实拉样本逐字键）；行内翻转改写，非 final。 */
        public boolean userDigg;
        /**
         * 行点赞在途旗（06-07 域 S4，官 UiState.isRequesting 同位）：
         * 动作防重归位 UI 态层——置旗后同条评论再点整发忽略、回执
         * 交付清旗；只活在本体对象上、五面行包装共读一份，不入任
         * 何账、不随拉取落盘。
         */
        public boolean isRequesting;
        /** 作者标识（user_action.author≠0 或 user_tag.is_author，实拉样本键）。 */
        public final boolean authorTag;
        /** 图评图片（content.image_data_list.image_data[] 的 expand_web_url 优先，实拉样本键）。 */
        public final List<String> imageUrls;
        /** 评论标签（expand.comment_tag_list[].tag_name，仅剧评样本见，实拉样本键）。 */
        public final List<String> commentTags;
        /** 评分后缀文案（expand.score_suffix_text，仅剧评）。 */
        public final String scoreSuffix;
        /**
         * 评论本体 service_id（common.service_id 原样）：do_action 点赞的
         * comment_type 就取它（官方 playlet 评论行 getCommentType()＝模型
         * getServiceId()，2026-11-04 官方 caller 逐个实证，账
         * hg-comment-like-diff-20261104 落码节）。短剧评论实测恒 4
         * （拉取样本 13/13），字段缺失时以此为缺省。
         */
        public static final int DEFAULT_SERVICE_ID = 4;
        public final int serviceId;

        Comment(String id, String author, String avatar, String content, long created, long likes, long replyCount, String score, String replyTo) {
            this(id, author, avatar, content, created, likes, replyCount, score, replyTo,
                    "", false, false, null, null, null, DEFAULT_SERVICE_ID);
        }

        Comment(String id, String author, String avatar, String content, long created,
                long likes, long replyCount, String score, String replyTo,
                String authorId, boolean userDigg, boolean authorTag,
                List<String> imageUrls, List<String> commentTags, String scoreSuffix,
                int serviceId) {
            this.id = id;
            this.author = author;
            this.avatar = avatar;
            this.content = content;
            this.created = created;
            this.likes = likes;
            this.replyCount = replyCount;
            this.score = score;
            this.replyTo = replyTo;
            this.authorId = authorId == null ? "" : authorId;
            this.userDigg = userDigg;
            this.authorTag = authorTag;
            this.imageUrls = imageUrls;
            this.commentTags = commentTags;
            this.scoreSuffix = scoreSuffix;
            this.serviceId = serviceId;
        }
    }

    /** 图评图片抽取：content.image_data_list.image_data[]，地址取 expand_web_url→web_url→web_uri 首个非空。 */
    private static List<String> imageUrlsOf(JSONObject contentObj) {
        if (contentObj == null) return null;
        JSONObject wrap = contentObj.optJSONObject("image_data_list");
        JSONArray arr = wrap == null ? null : wrap.optJSONArray("image_data");
        if (arr == null || arr.length() == 0) return null;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < arr.length() && out.size() < 9; i++) {
            JSONObject im = arr.optJSONObject(i);
            if (im == null) continue;
            String u = im.optString("expand_web_url");
            if (u.isEmpty()) u = im.optString("web_url");
            if (u.isEmpty()) u = im.optString("web_uri");
            if (!u.isEmpty() && u.length() <= 4096
                    && (u.startsWith("https://") || u.startsWith("http://"))) out.add(u);
        }
        return out.isEmpty() ? null : out;
    }

    /** 评论标签抽取：expand.comment_tag_list[].tag_name。 */
    private static List<String> commentTagsOf(JSONObject expand) {
        if (expand == null) return null;
        JSONArray arr = expand.optJSONArray("comment_tag_list");
        if (arr == null || arr.length() == 0) return null;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < arr.length() && out.size() < 6; i++) {
            JSONObject t = arr.optJSONObject(i);
            if (t == null) continue;
            String n = t.optString("tag_name");
            if (!n.isEmpty()) out.add(n);
        }
        return out.isEmpty() ? null : out;
    }

    /** 作者标识：user_action.author≠0 或 user_info.user_tag.is_author。 */
    private static boolean authorTagOf(JSONObject comment, JSONObject common) {
        JSONObject ua = comment == null ? null : comment.optJSONObject("user_action");
        if (ua != null && ua.optInt("author", 0) != 0) return true;
        JSONObject ui = common == null ? null : common.optJSONObject("user_info");
        JSONObject tag = ui == null ? null : ui.optJSONObject("user_tag");
        return tag != null && tag.optBoolean("is_author", false);
    }

    /**
     * 引用条目工厂（S3/G15 连带处置）：弹幕动作面「回复」路组装
     * publishReply 的 parent 引用对象用——只承载身份三件（id＝
     * 弹幕条 rowId、authorId＝条目 userId、正文＝条目文本），其余
     * 计数/时间皆 0、不冒充服务端评论行、不上屏渲染。它不是回显
     * （旧回复回显工厂被借作此用、随 G15 摘除后由本工厂接替）。
     */
    public static Comment parentRef(String id, String content, String authorId) {
        return new Comment(id == null ? "" : id,
                "", "",
                content == null ? "" : content,
                0, 0, 0, null, null,
                authorId == null ? "" : authorId, false, false, null, null, null,
                Comment.DEFAULT_SERVICE_ID);
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
        return episodeComments(seriesId, vid, cursor, null);
    }

    /**
     * 带在途句柄槽的单集评论一页（S3 第 0 步）：执行期把 Call 写
     * 进 slot、结束摘除，评论面关面时经 cancelInFlight 断掉阻塞
     * 读。slot 传 null 即旧口径。页长 count=10（官条 33，S3/B4
     * 订正旧 20）。
     */
    public static Page episodeComments(String seriesId, String vid, String cursor,
                                       AtomicReference<Call> slot) {
        if (!isId(seriesId) || !isId(vid)) return null;
        String cur = cursor == null ? "" : cursor;
        if (validateJsonCursor(cur) < 0) return null;
        try {
            JSONObject result = post("comment/list/" + vid,
                    episodePayload(seriesId, vid, cur, null), 4, 18, slot);
            return parseList(result, seriesId, vid, cur, false);
        } catch (Exception e) {
            log("episodeComments failed vid=%s err=%s", vid, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 发评成功后的首屏重拉（官三件之一，条 33/37）：cursor 空、
     * business_param.insert_comment_ids=[新评论 id]（CommentBusinessParam
     * 官字段、List 形），服务端把新条钉在首屏首行——跳顶靠它实现，
     * 不再本地造条。
     */
    public static Page episodeCommentsInserted(String seriesId, String vid, String insertId,
                                               AtomicReference<Call> slot) {
        if (!isId(seriesId) || !isId(vid)) return null;
        if (insertId == null || insertId.isEmpty()) return null;
        try {
            JSONObject result = post("comment/list/" + vid,
                    episodePayload(seriesId, vid, "", insertId), 4, 18, slot);
            return parseList(result, seriesId, vid, "", false);
        } catch (Exception e) {
            log("episodeCommentsInserted failed vid=%s err=%s", vid, e.getClass().getSimpleName());
            return null;
        }
    }

    private static JSONObject episodePayload(String seriesId, String vid, String cursor,
                                             String insertId) throws Exception {
        JSONObject business = new JSONObject().put("book_id", seriesId).put("req_type", 0);
        if (insertId != null && !insertId.isEmpty()) {
            business.put("insert_comment_ids", new JSONArray().put(insertId));
        }
        return new JSONObject()
                .put("comment_source", 4).put("server_channel", 18)
                .put("group_id", vid).put("group_type", 30)
                .put("comment_type", 4).put("sort", 1).put("need_count", true)
                .put("count", 10).put("cursor", cursor).put("aid", 8662).put("compliance_status", 0)
                .put("business_param", business);
    }

    /** 整剧剧评一页。cursor 首页传空串（请求体不带 cursor 键），翻页传 nextCursor。 */
    public static Page seriesComments(String seriesId, String cursor) {
        return seriesComments(seriesId, cursor, null);
    }

    /** 带在途句柄槽的剧评一页（口径同 episodeComments 句柄版）。 */
    public static Page seriesComments(String seriesId, String cursor,
                                      AtomicReference<Call> slot) {
        if (!isId(seriesId)) return null;
        String cur = cursor == null ? "" : cursor;
        if (validateJsonCursor(cur) < 0) return null;
        try {
            JSONObject result = post("comment/list/" + seriesId,
                    seriesPayload(seriesId, cur, null), 1, 34, slot);
            return parseList(result, seriesId, seriesId, cur, true);
        } catch (Exception e) {
            log("seriesComments failed series=%s err=%s", seriesId, e.getClass().getSimpleName());
            return null;
        }
    }

    /** 发剧评成功后的首屏重拉（官三件，insert_comment_ids 口径同单集侧）。 */
    public static Page seriesCommentsInserted(String seriesId, String insertId,
                                              AtomicReference<Call> slot) {
        if (!isId(seriesId)) return null;
        if (insertId == null || insertId.isEmpty()) return null;
        try {
            JSONObject result = post("comment/list/" + seriesId,
                    seriesPayload(seriesId, "", insertId), 1, 34, slot);
            return parseList(result, seriesId, seriesId, "", true);
        } catch (Exception e) {
            log("seriesCommentsInserted failed series=%s err=%s", seriesId, e.getClass().getSimpleName());
            return null;
        }
    }

    private static JSONObject seriesPayload(String seriesId, String cursor,
                                            String insertId) throws Exception {
        JSONObject business = new JSONObject().put("book_id", seriesId)
                .put("need_count", true).put("real_level", 2).put("req_type", 0);
        if (insertId != null && !insertId.isEmpty()) {
            business.put("insert_comment_ids", new JSONArray().put(insertId));
        }
        JSONObject payload = new JSONObject()
                .put("business_param", business)
                .put("comment_source", 1).put("comment_type", 2)
                .put("count", 10).put("group_id", seriesId).put("group_type", 1)
                .put("server_channel", 34).put("sort", 1);
        if (cursor != null && !cursor.isEmpty()) payload.put("cursor", cursor);
        return payload;
    }

    /**
     * 帖子评论一页（批三 U-09）：group_id＝post_id、group_type＝11
     * （UgcCommentGroupType.Post，官方枚举候选——帖子页旧账记的
     * 候选值，未活体回包验，失败即整段不显、不硬凑）。体形照
     * episodeComments 同构（comment_source/server_channel 帖子面
     * 无定版值，取 0/0 不挂头口径，与写操作同规）。回验闸只验
     * group_id（帖子评论 expand 无 book_id 语义，不套剧面闸）。
     */
    public static Page postComments(String postId, String cursor) {
        return postComments(postId, cursor, null);
    }

    /** 带在途句柄槽的帖子评论一页（S3b 补口，口径同 episodeComments 句柄版；体形逐字同旧口、只换带槽传输）。 */
    public static Page postComments(String postId, String cursor,
                                    AtomicReference<Call> slot) {
        if (!isId(postId)) return null;
        String cur = cursor == null ? "" : cursor;
        if (validateJsonCursor(cur) < 0) return null;
        try {
            JSONObject payload = new JSONObject()
                    .put("comment_source", 0).put("server_channel", 0)
                    .put("group_id", postId).put("group_type", 11)
                    .put("comment_type", 4).put("sort", 1).put("need_count", true)
                    .put("count", 20).put("cursor", cur).put("aid", 8662)
                    .put("compliance_status", 0);
            JSONObject result = post("comment/list/" + postId, payload, 0, 0, slot);
            return parsePostList(result, postId, cur);
        } catch (Exception e) {
            log("postComments failed post=%s err=%s", postId, e.getClass().getSimpleName());
            return null;
        }
    }

    /** 帖子评论解析：与 parseList 同行形，归属闸只认 group_id。 */
    static Page parsePostList(JSONObject result, String postId, String cursor) throws Exception {
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
            if (!postId.equals(common.optString("group_id"))) return null;
            if (common.optInt("status", -1) != 1) continue;
            String id = comment.optString("comment_id");
            JSONObject contentObj = common.optJSONObject("content");
            String content = contentObj == null ? "" : contentObj.optString("text").trim();
            if (id.isEmpty() || id.length() > 120 || content.isEmpty() || !seen.add(id)) continue;
            JSONObject base = optBaseInfo(common);
            String avatar = base == null ? "" : base.optString("user_avatar");
            if (avatar.length() > 4096 || !isAvatarUrl(avatar)) avatar = "";
            JSONObject stat = comment.optJSONObject("stat");
            long likes = stat != null && stat.has("digg_count")
                    ? stat.optLong("digg_count", 0) : 0;
            long replyCount = stat == null ? 0 : Math.max(0, stat.optLong("reply_count", 0));
            JSONObject userActionP = comment.optJSONObject("user_action");
            items.add(new Comment(id,
                    base == null ? "" : truncate(base.optString("user_name"), 200),
                    avatar, truncate(content, 20000),
                    Math.max(0, common.optLong("create_timestamp", 0)),
                    Math.max(0, likes), replyCount, null, null,
                    base == null ? "" : base.optString("user_id"),
                    userActionP != null && userActionP.optBoolean("user_digg", false),
                    authorTagOf(comment, common),
                    imageUrlsOf(contentObj), null, null,
                    common.optInt("service_id", Comment.DEFAULT_SERVICE_ID)));
        }
        return new Page(items, Math.max(0, info.optLong("total", 0)),
                more ? next : "", more, null, 0);
    }

    /** 单集评论的回复一页。cursor 首页传空串。 */
    public static Page episodeReplies(String seriesId, String vid, String commentId, String cursor) {
        return episodeReplies(seriesId, vid, commentId, cursor, null);
    }

    /** 带在途句柄槽的单集回复一页（口径同 episodeComments 句柄版）。 */
    public static Page episodeReplies(String seriesId, String vid, String commentId, String cursor,
                                      AtomicReference<Call> slot) {
        if (!isId(seriesId) || !isId(vid) || commentId == null || commentId.isEmpty()) return null;
        String cur = cursor == null ? "" : cursor;
        if (validateNumCursor(cur) < 0) return null;
        try {
            JSONObject result = post("reply/list/" + commentId,
                    episodeRepliesPayload(seriesId, vid, commentId, cur, null), 504, 18, slot);
            return parseReplies(result, seriesId, vid, cur);
        } catch (Exception e) {
            log("episodeReplies failed comment=%s err=%s", commentId, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 回复成功后的回复区首屏重拉（官三件的回复侧，条 42）：
     * business_param.insert_reply_ids=[新回复 id]（ReplyBusinessParam
     * 官字段、List 形），服务端把新回复钉在回复区首位。
     */
    public static Page episodeRepliesInserted(String seriesId, String vid, String commentId,
                                              String insertId, AtomicReference<Call> slot) {
        if (!isId(seriesId) || !isId(vid) || commentId == null || commentId.isEmpty()) return null;
        if (insertId == null || insertId.isEmpty()) return null;
        try {
            JSONObject result = post("reply/list/" + commentId,
                    episodeRepliesPayload(seriesId, vid, commentId, "", insertId), 504, 18, slot);
            return parseReplies(result, seriesId, vid, "");
        } catch (Exception e) {
            log("episodeRepliesInserted failed comment=%s err=%s", commentId, e.getClass().getSimpleName());
            return null;
        }
    }

    private static JSONObject episodeRepliesPayload(String seriesId, String vid, String commentId,
                                                    String cursor, String insertId) throws Exception {
        JSONObject business = new JSONObject().put("book_id", seriesId).put("need_count", false);
        if (insertId != null && !insertId.isEmpty()) {
            business.put("insert_reply_ids", new JSONArray().put(insertId));
        }
        return new JSONObject()
                .put("aid", 8662)
                .put("business_param", business)
                .put("comment_id", commentId).put("comment_source", 504).put("comment_type", 4)
                .put("compliance_status", 0).put("count", 10).put("cursor", cursor)
                .put("group_id", vid).put("group_type", 30).put("server_channel", 18);
    }

    /** 剧评的回复一页。cursor 首页传空串（请求体不带 cursor 键）。 */
    public static Page seriesReplies(String seriesId, String commentId, String cursor) {
        return seriesReplies(seriesId, commentId, cursor, null);
    }

    /** 带在途句柄槽的剧评回复一页（口径同 episodeComments 句柄版）。 */
    public static Page seriesReplies(String seriesId, String commentId, String cursor,
                                     AtomicReference<Call> slot) {
        if (!isId(seriesId) || commentId == null || commentId.isEmpty()) return null;
        String cur = cursor == null ? "" : cursor;
        if (validateNumCursor(cur) < 0) return null;
        try {
            JSONObject result = post("reply/list/" + commentId,
                    seriesRepliesPayload(seriesId, commentId, cur, null), 501, 34, slot);
            return parseReplies(result, seriesId, seriesId, cur);
        } catch (Exception e) {
            log("seriesReplies failed comment=%s err=%s", commentId, e.getClass().getSimpleName());
            return null;
        }
    }

    /** 回复成功后的剧评回复区首屏重拉（insert_reply_ids 口径同单集侧）。 */
    public static Page seriesRepliesInserted(String seriesId, String commentId,
                                             String insertId, AtomicReference<Call> slot) {
        if (!isId(seriesId) || commentId == null || commentId.isEmpty()) return null;
        if (insertId == null || insertId.isEmpty()) return null;
        try {
            JSONObject result = post("reply/list/" + commentId,
                    seriesRepliesPayload(seriesId, commentId, "", insertId), 501, 34, slot);
            return parseReplies(result, seriesId, seriesId, "");
        } catch (Exception e) {
            log("seriesRepliesInserted failed comment=%s err=%s", commentId, e.getClass().getSimpleName());
            return null;
        }
    }

    private static JSONObject seriesRepliesPayload(String seriesId, String commentId,
                                                   String cursor, String insertId) throws Exception {
        JSONObject business = new JSONObject().put("book_id", seriesId)
                .put("need_count", cursor == null || cursor.isEmpty()).put("real_level", 2);
        if (insertId != null && !insertId.isEmpty()) {
            business.put("insert_reply_ids", new JSONArray().put(insertId));
        }
        JSONObject payload = new JSONObject()
                .put("business_param", business)
                .put("comment_id", commentId).put("comment_source", 501).put("comment_type", 2)
                .put("count", 10).put("group_id", seriesId).put("group_type", 1)
                .put("server_channel", 34);
        if (cursor != null && !cursor.isEmpty()) payload.put("cursor", cursor);
        return payload;
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
            JSONObject userAction = comment.optJSONObject("user_action");
            String suffix = null;
            if (seriesDim) {
                String sx = expand.optString("score_suffix_text");
                if (!sx.isEmpty()) suffix = sx;
            }
            items.add(new Comment(id,
                    base == null ? "" : truncate(base.optString("user_name"), 200),
                    avatar, truncate(content, 20000),
                    Math.max(0, common.optLong("create_timestamp", 0)), Math.max(0, likes), replyCount, score, null,
                    base == null ? "" : base.optString("user_id"),
                    userAction != null && userAction.optBoolean("user_digg", false),
                    authorTagOf(comment, common),
                    imageUrlsOf(contentObj), commentTagsOf(expand), suffix,
                    common.optInt("service_id", Comment.DEFAULT_SERVICE_ID)));
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
                JSONObject rowAction = row.optJSONObject("user_action");
                items.add(new Comment(id,
                        base == null ? "" : truncate(base.optString("user_name"), 200),
                        avatar, truncate(content, 20000),
                        Math.max(0, common.optLong("create_timestamp", 0)), likes, replyCount, null, replyTo,
                        base == null ? "" : base.optString("user_id"),
                        rowAction != null && rowAction.optBoolean("user_digg", false),
                        authorTagOf(row, common),
                        imageUrlsOf(contentObj), null, null,
                        common.optInt("service_id", Comment.DEFAULT_SERVICE_ID)));
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

    /**
     * 关面 cancel 口（S3/G18 收口）：摘下并 cancel 槽内在途拉取
     * Call——同步 execute 的阻塞读只认 Call.cancel，线程中断打不
     * 断（旧 shutdownNow 实情）。槽为空即无在途、无操作。
     */
    public static void cancelInFlight(AtomicReference<Call> slot) {
        if (slot == null) return;
        Call c = slot.getAndSet(null);
        if (c != null) c.cancel();
    }

    /** 同包共用传输（HongguoDanmakuApi 复用同一签名器/设备号/直连口径），非同包不可见。 */
    static JSONObject post(String path, JSONObject payload, int source, int channel) throws Exception {
        return post(path, payload, source, channel, null);
    }

    /**
     * 带在途句柄槽的传输：execute 前把 Call 写进 slot、返回/抛错
     * 后摘除（compareAndSet 只摘自己这发，不误摘后来者）；关面
     * cancel 后 execute 抛 IOException，上层按失败 null 收口。
     */
    static JSONObject post(String path, JSONObject payload, int source, int channel,
                           AtomicReference<Call> slot) throws Exception {
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
        Call call = httpClient().newCall(request.build());
        if (slot != null) slot.set(call);
        try (Response response = call.execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IllegalStateException("http " + response.code());
            byte[] bytes = readLimited(response.body().byteStream(), MAX_BYTES);
            if (bytes.length > MAX_BYTES) throw new IllegalStateException("response too large");
            return new JSONObject(new String(bytes, "UTF-8"));
        } finally {
            if (slot != null) slot.compareAndSet(call, null);
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

    // ---------- 设备号（一张脸，§六十一第 142 项） ----------
    // 旧第二张脸整件摘除：fq_comment_device_id/fq_comment_iid 独立
    // 键＋fq.comment.* 系统属性测试钩＋SecureRandom 现编落盘，与
    // 注册档案互不相干。现统一读全局唯一身份（档案未注册时为空
    // 串——主链进红果即 ensureDevice 注册、评论链在其后，不在
    // 此处另编新号）。
    // 读口（§六十六）：本类是朋友版保留的共用件，不许直引红果
    // 包（剥离终检全树禁红果包名），改经中性桥 DeviceFaceBridge
    // 直读同一份档案落盘——同键同字段同回退，与档案本体的
    // signDeviceId/signInstallId 同源同值。

    private static String deviceId() {
        return DeviceFaceBridge.deviceId();
    }

    private static String iid() {
        return DeviceFaceBridge.installId();
    }
}
