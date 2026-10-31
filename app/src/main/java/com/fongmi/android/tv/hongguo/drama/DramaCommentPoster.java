package com.fongmi.android.tv.hongguo.drama;

import com.fongmi.android.tv.hongguo.api.HgCallback;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 发评论（2026-10-31 他亲定「能登录就能发」，评论只看口径自此
 * 作废）：POST /novel/commentapi/comment/add/v1/（端点出自
 * hongguo-assistant 端点表 B 互动组，与评论 list/reply 同族）。
 * 与发弹幕（{@link DramaDanmakuSender}）是两条链、互不混动：弹幕
 * 走匿名 post，本链走 {@link DramaCommentTransport#postAuthed}
 * 带账号会话头（Cookie＋x-tt-token＋csrf），评论归属登录账号；
 * 登录门在调用方（两面输入行/发送钮点击先查登录态、未登录跳
 * 登录页，与收藏/点赞/关注/发弹幕同门）。
 *
 * <p>载荷字段逐项有出处、不编造：group_id/group_type/text/aid
 * 出自弹幕 add 定版（DramaDanmakuSender.publish）；comment_source
 * =4、server_channel=18、comment_type=4、compliance_status=0、
 * business_param{book_id, req_type=0} 出自单集评论 list 定版体
 * （HongguoCommentApi.episodeComments，与本链同属单集评论维度）。
 * 弹幕专属字段（commit_source/data_type/business_param.offset/
 * shark_param）不带。字段组合未经活体验证：add 真回包形状未验，
 * 成败判定沿弹幕链 writeResult 口径（code 数值 0 或 "0"/"SUCCESS"
 * 为成功、结构化非 0 为服务端拒绝、解不出为失败）；服务端评论 id
 * 尽力从回包 data 摘（comment_id／comment.comment_id 两拼写），
 * 摘不到回空串，调用方本地回显用占位 id、服务端回拉校准。</p>
 *
 * <p>回复发送（/novel/commentapi/reply/add/v1/）请求体结构无已
 * 验样本，本类不接、标待验；本类只发顶层评论。</p>
 */
public final class DramaCommentPoster {

    private DramaCommentPoster() {
    }

    /**
     * 发一条顶层评论。成功回调服务端评论 id（摘不到为空串）；
     * 失败回调错误码/消息，调用方给明确失败态、不装成功。
     */
    public static void publish(String seriesId, String vid, String text,
                               HgCallback<String> cb) {
        if (!DramaCommentTransport.isId(seriesId) || !DramaCommentTransport.isId(vid)) {
            cb.onError(-1, "id 非法");
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            cb.onError(-1, "评论为空");
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("comment_source", 4);
        payload.addProperty("server_channel", 18);
        payload.addProperty("group_id", vid);
        payload.addProperty("group_type", 30);
        payload.addProperty("comment_type", 4);
        payload.addProperty("text", text.trim());
        payload.addProperty("aid", 8662);
        payload.addProperty("compliance_status", 0);
        JsonObject business = new JsonObject();
        business.addProperty("book_id", seriesId);
        business.addProperty("req_type", 0);
        payload.add("business_param", business);

        // 传输头同传 4/18：与体 comment_source/server_channel 同值，
        // 照单集评论面 list 口的头=体惯例（HongguoCommentApi 同面口径）。
        DramaCommentTransport.postAuthed("comment/add", payload.toString(), 4, 18,
                new DramaCommentTransport.Sink() {
                    @Override
                    public void onText(String resp) {
                        int r = DramaDanmakuSender.writeResult(resp);
                        if (r < 0) cb.onError(-2, "发评论回包解析失败");
                        else if (r == 0) cb.onError(-2, "发评论被服务端拒绝");
                        else cb.onSuccess(extractCommentId(resp));
                    }

                    @Override
                    public void onFail(int code, String msg) {
                        cb.onError(code, msg);
                    }
                });
    }

    /** 服务端评论 id 尽力摘取：data.comment_id 或 data.comment.comment_id；摘不到回空串。 */
    static String extractCommentId(String resp) {
        try {
            JsonElement el = JsonParser.parseString(resp);
            if (el == null || !el.isJsonObject()) return "";
            JsonObject data = el.getAsJsonObject().getAsJsonObject("data");
            if (data == null) return "";
            String id = optId(data, "comment_id");
            if (!id.isEmpty()) return id;
            JsonObject comment = data.getAsJsonObject("comment");
            return comment == null ? "" : optId(comment, "comment_id");
        } catch (Exception e) {
            return "";
        }
    }

    private static String optId(JsonObject obj, String key) {
        JsonElement v = obj.get(key);
        if (v == null || !v.isJsonPrimitive()) return "";
        String s = v.getAsString();
        return s == null ? "" : s.trim();
    }
}
