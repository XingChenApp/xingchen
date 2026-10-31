package com.fongmi.android.tv.hongguo.drama;

import com.fongmi.android.tv.hongguo.api.HgCallback;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;



/**
 * 发弹幕（2026-10-30 定版，2026-10-31 随 #21 乙恢复——当年摘除是
 * 因为没登录，如今登录在建、他亲裁按官方加回）：POST
 * /novel/commentapi/comment/add/v1/，载荷与传输口径照定版
 *（danmakuPostPayload＋评论栈 postRaw）——设备号与壳子评论栈共用
 * 同一 Prefers 键（fq_comment_device_id / fq_comment_iid，同一设备
 * 身份），签名走 FqCommentSigner；本类不带账号 Cookie，登录门在
 * 调用方（Feed/Land 入口点击先查登录态、未登录走登录页，照官方
 * 口径与 A25 旧行为）。发送真回包从未活体验过，待真机首发。
 *
 * <p>回包判定（旧 writeResult 口径）：code 数值 0 或字符串
 * "0"/"SUCCESS" 为成功；结构化非 0＝服务端拒绝；解不出＝失败。</p>
 */
public final class DramaDanmakuSender {

    private DramaDanmakuSender() {
    }

    public static void publish(String seriesId, String vid, String text, long offsetMs,
                               HgCallback<Void> cb) {
        if (!isId(seriesId) || !isId(vid)) {
            cb.onError(-1, "id 非法");
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            cb.onError(-1, "弹幕为空");
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("commit_source", 1500);
        payload.addProperty("group_id", vid);
        payload.addProperty("group_type", 30);
        payload.addProperty("data_type", 20);
        payload.addProperty("text", text);
        payload.addProperty("aid", 8662);
        JsonObject business = new JsonObject();
        business.addProperty("book_id", seriesId);
        business.addProperty("offset", Math.max(0, offsetMs));
        business.addProperty("ignore_urge_rule", false);
        JsonObject shark = new JsonObject();
        shark.addProperty("type", "short_play");
        shark.addProperty("aid", "8662");
        business.add("shark_param", shark);
        payload.add("business_param", business);

        // 传输头传 0/0：发弹幕是写操作，官方写口不挂 Comment-Source/
        // Server-Channel 头（do_action 惯例 0/0，f3aad982d 定版）。
        DramaCommentTransport.post("comment/add", payload.toString(), 0, 0, new DramaCommentTransport.Sink() {
            @Override
            public void onText(String resp) {
                int r = writeResult(resp);
                if (r < 0) cb.onError(-2, "发弹幕回包解析失败");
                else if (r == 0) cb.onError(-2, "发弹幕被服务端拒绝");
                else cb.onSuccess(null);
            }

            @Override
            public void onFail(int code, String msg) {
                cb.onError(code, msg);
            }
        });
    }

    /** 回包成败（旧 HgCommentParser.writeResult 口径）：1 成功 / 0 拒绝 / -1 解不出。 */
    static int writeResult(String text) {
        if (text == null || text.isEmpty()) return -1;
        try {
            com.google.gson.JsonElement el = JsonParser.parseString(text);
            if (el == null || !el.isJsonObject()) return -1;
            JsonObject root = el.getAsJsonObject();
            if (!root.has("code")) return -1;
            com.google.gson.JsonElement code = root.get("code");
            boolean ok;
            if (code.isJsonPrimitive() && code.getAsJsonPrimitive().isNumber()) {
                ok = code.getAsInt() == 0;
            } else {
                String s = code.getAsString();
                ok = "0".equals(s) || "SUCCESS".equalsIgnoreCase(s);
            }
            return ok ? 1 : 0;
        } catch (Exception e) {
            return -1;
        }
    }

    static boolean isId(String s) {
        return DramaCommentTransport.isId(s);
    }
}
