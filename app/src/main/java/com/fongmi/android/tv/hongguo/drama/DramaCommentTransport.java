package com.fongmi.android.tv.hongguo.drama;

import com.fongmi.android.tv.api.FqCommentSigner;
import com.fongmi.android.tv.api.HongguoCommentApi;
import com.fongmi.android.tv.hongguo.api.HgAccountStore;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 评论栈写口与作者作品流共用传输（2026-10-30 从零重建）：POST
 * /novel/commentapi/<path>/v1/，设备号与壳子评论栈共用同一 Prefers
 * 键（fq_comment_device_id / fq_comment_iid，同一设备身份），签名走
 * FqCommentSigner。实承载三路：发弹幕（DramaDanmakuSender，匿名
 * post）、作者作品流（DramaClient.fetchAuthorWorks，匿名 post）、
 * 发评论（DramaCommentPoster，postAuthed 加挂账号会话头）。评论/弹幕
 * 的拉取不在本类，走壳子 HongguoCommentApi（同步口）。
 *
 * <p>Comment-Source/Server-Channel 头口径（A-18 统一，2026-10-31）：
 * 与壳子传输同一条规则——头值＝调用方给的 source/channel、&gt;0 才
 * 挂、0 即不挂（旧 HgCommentClient.postRaw 定版语义）。各路取值：
 * 发评论 4/18（与单集评论 list 同面、头与体 comment_source/
 * server_channel 同值，照 HongguoCommentApi 惯例）；发弹幕与作者
 * 作品流 0/0——官方此二口本就不挂这二头（作品口 GetUserProfile
 * 组参无此二头、写操作 do_action 惯例 0/0，均 f3aad982d 定版实证），
 * 缺头是照官方、不是漏挂。</p>
 */
public final class DramaCommentTransport {

    static final String HOST = "https://api5-normal-sinfonlineb.fqnovel.com";
    static final String UA = "com.phoenix.read/73532 (Linux; U; Android 16; zh_CN; 25053RT47C; Build/BP2A.250605.031.A3; Cronet/TTNetVersion:04657795 2026-01-23 QuicVersion:c67e9834 2025-09-08)";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public interface Sink {
        void onText(String text);

        void onFail(int code, String msg);
    }

    private DramaCommentTransport() {
    }

    /**
     * 匿名 POST（发弹幕/作者作品流用）。source/channel 按类头口径
     * 显式传：&gt;0 才挂 Comment-Source/Server-Channel 头，官方不挂
     * 此二头的口传 0/0、不许省略成默认。
     */
    public static void post(String path, String payloadJson, int source, int channel, Sink sink) {
        post(path, payloadJson, source, channel, null, sink);
    }

    /**
     * 带账号会话头的 POST（发评论专用，DramaCommentPoster 用）：在
     * post 的同一传输上加挂登录落盘的会话头（Cookie＋x-tt-token＋
     * x-tt-passport-csrf-token，照 HgAccountSync 会话头口径）。匿名
     * 的 post（发弹幕/作者作品流）不经此口、行为逐字不变。
     */
    public static void postAuthed(String path, String payloadJson, int source, int channel, Sink sink) {
        Map<String, String> extra = new LinkedHashMap<>();
        HgAccountStore.Session session = HgAccountStore.load();
        if (session != null) {
            if (session.cookie != null && !session.cookie.isEmpty()) {
                extra.put("Cookie", session.cookie);
            }
            if (session.ttToken != null && !session.ttToken.isEmpty()) {
                extra.put("x-tt-token", session.ttToken);
            }
            if (session.csrf != null && !session.csrf.isEmpty()) {
                extra.put("x-tt-passport-csrf-token", session.csrf);
            }
        }
        post(path, payloadJson, source, channel, extra, sink);
    }

    private static void post(String path, String payloadJson, int source, int channel,
                             Map<String, String> extraHeaders, Sink sink) {
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
            sb.append(e.getKey()).append('=').append(enc(e.getValue()));
        }
        String encoded = sb.toString();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Accept", "application/json");
        headers.put("X-XS-From-Web", "0");
        headers.put("Sdk-Version", "2");
        // 头口径与壳子传输统一：source/channel >0 才挂、0 即不挂（类头注）。
        if (source > 0) headers.put("Comment-Source", String.valueOf(source));
        if (channel > 0) headers.put("Server-Channel", String.valueOf(channel));
        byte[] nonce = new byte[11];
        new SecureRandom().nextBytes(nonce);
        headers.putAll(FqCommentSigner.headers(encoded, now, nonce));
        if (extraHeaders != null) headers.putAll(extraHeaders);
        Call call = OkHttp.newCall(HOST + "/novel/commentapi/" + path + "/v1/?" + encoded,
                headers, RequestBody.create(payloadJson, JSON));
        if (call == null) {
            sink.onFail(-1, "无法创建请求");
            return;
        }
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call c, IOException e) {
                sink.onFail(-1, e.getMessage());
            }

            @Override
            public void onResponse(Call c, Response response) {
                try (Response r = response) {
                    ResponseBody rb = r.body();
                    String text = rb == null ? "" : rb.string();
                    if (!r.isSuccessful()) sink.onFail(r.code(), "HTTP " + r.code());
                    else if (text.isEmpty()) sink.onFail(-2, "回包空体");
                    else sink.onText(text);
                } catch (Exception e) {
                    sink.onFail(-1, e.getMessage());
                }
            }
        });
    }

    static boolean isId(String s) {
        return HongguoCommentApi.isId(s);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s == null ? "" : s;
        }
    }

    // ---------- 设备号（与壳子评论栈共用同一 Prefers 键＝同一设备身份） ----------

    private static volatile String deviceId;
    private static volatile String iid;

    private static String deviceId() {
        if (deviceId != null) return deviceId;
        synchronized (DramaCommentTransport.class) {
            if (deviceId == null) deviceId = loadOrCreate("fq_comment_device_id");
            return deviceId;
        }
    }

    private static String iid() {
        if (iid != null) return iid;
        synchronized (DramaCommentTransport.class) {
            if (iid == null) iid = loadOrCreate("fq_comment_iid");
            return iid;
        }
    }

    private static String loadOrCreate(String key) {
        try {
            String saved = Prefers.getString(key);
            if (isId(saved)) return saved;
        } catch (Throwable ignored) {
        }
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(19);
        sb.append(7);
        for (int i = 0; i < 18; i++) sb.append(random.nextInt(10));
        String created = sb.toString();
        try {
            Prefers.put(key, created);
        } catch (Throwable ignored) {
        }
        return created;
    }
}
