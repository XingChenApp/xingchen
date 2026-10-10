package com.fongmi.android.tv.login;

import android.text.TextUtils;

import androidx.collection.ArrayMap;

import com.fongmi.android.tv.utils.PanAuth;
import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import okhttp3.Response;

/**
 * 115 扫码登录（壳子统一登录的第一个提供方）。
 * 协议与 115 网盘 py 爬虫的 QrLogin 一致：
 * 取二维码 token → 轮询扫码状态 → 确认后换 cookie。
 */
public class Qr115Provider implements LoginProvider {

    private static final String API_QR_TOKEN = "https://qrcodeapi.115.com/api/1.0/web/1.0/token/";
    private static final String API_QR_STATUS = "https://qrcodeapi.115.com/get/status/";
    private static final String API_QR_LOGIN = "https://passportapi.115.com/app/1.0/web/1.0/login/qrcode";
    private static final String APP = "web";
    private static final String UA_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /** 扫码状态 */
    public static final int ST_WAITING = 0;
    public static final int ST_SCANNED = 1;
    public static final int ST_CONFIRMED = 2;
    public static final int ST_EXPIRED = -1;
    public static final int ST_CANCELLED = -2;
    /** 二维码有效期（秒），与 py 端保持一致 */
    public static final long QR_TTL_MS = 240_000L;

    /** 一次扫码会话 */
    public static class QrSession {
        public String uid = "";
        public long time;
        public String sign = "";
        /** 扫码内容：https://115.com/scan/dg-{uid}，手机 115 App 可直接扫 */
        public String content = "";
        public long deadline;

        public boolean expired() {
            return System.currentTimeMillis() > deadline;
        }
    }

    @Override
    public String id() {
        return "115";
    }

    @Override
    public String name() {
        return "115网盘";
    }

    @Override
    public boolean isLoggedIn() {
        return PanAuth.is115LoggedIn();
    }

    @Override
    public String getCookie() {
        return PanAuth.get115Cookie();
    }

    @Override
    public void clear() {
        PanAuth.clear115();
    }

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA_WEB);
        h.put("Referer", "https://115.com/");
        h.put("Accept", "application/json, text/plain, */*");
        h.put("Accept-Language", "zh-CN,zh;q=0.9");
        return h;
    }

    private String get(String url, ArrayMap<String, String> params) throws Exception {
        StringBuilder sb = new StringBuilder(url);
        if (params != null && !params.isEmpty()) {
            sb.append(url.contains("?") ? "&" : "?");
            boolean first = true;
            for (int i = 0; i < params.size(); i++) {
                if (!first) sb.append("&");
                first = false;
                sb.append(params.keyAt(i)).append("=")
                        .append(java.net.URLEncoder.encode(String.valueOf(params.valueAt(i)), "UTF-8"));
            }
        }
        try (Response res = OkHttp.newCall(sb.toString(), headers()).execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new Exception("网络错误 " + res.code());
            return res.body().string();
        }
    }

    /** 申请二维码，返回扫码会话 */
    public QrSession requestQr() throws Exception {
        String body = get(API_QR_TOKEN, null);
        JSONObject data = new JSONObject(body).optJSONObject("data");
        if (data == null || TextUtils.isEmpty(data.optString("uid"))) {
            throw new Exception("拿二维码失败：" + new JSONObject(body).optString("message", "未知错误"));
        }
        QrSession s = new QrSession();
        s.uid = data.optString("uid");
        s.time = data.optLong("time");
        s.sign = data.optString("sign");
        s.content = data.optString("qrcode");
        if (TextUtils.isEmpty(s.content)) s.content = "https://115.com/scan/dg-" + s.uid;
        s.deadline = System.currentTimeMillis() + QR_TTL_MS;
        return s;
    }

    /** 轮询扫码状态，返回 ST_* 常量 */
    public int poll(QrSession s) throws Exception {
        ArrayMap<String, String> p = new ArrayMap<>();
        p.put("uid", s.uid);
        p.put("time", String.valueOf(s.time));
        p.put("sign", s.sign);
        p.put("_", String.valueOf(System.currentTimeMillis()));
        String body = get(API_QR_STATUS, p);
        JSONObject data = new JSONObject(body).optJSONObject("data");
        if (data == null) return ST_WAITING;
        return data.optInt("status", ST_WAITING);
    }

    /**
     * 用户在手机上确认后，换取登录 cookie 并保存。
     * @return cookie 字符串（UID=..;CID=..;SEID=..;KID=..）
     */
    public String confirm(QrSession s) throws Exception {
        ArrayMap<String, String> form = new ArrayMap<>();
        form.put("account", s.uid);
        form.put("app", APP);
        String body;
        try (Response res = OkHttp.newCall(API_QR_LOGIN, headers(), OkHttp.toBody(form)).execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new Exception("网络错误 " + res.code());
            body = res.body().string();
        }
        JSONObject data = new JSONObject(body).optJSONObject("data");
        JSONObject ck = data == null ? null : data.optJSONObject("cookie");
        if (ck == null) throw new Exception("确认成功但没拿到凭据");
        StringBuilder sb = new StringBuilder();
        for (String k : new String[]{"UID", "CID", "SEID", "KID"}) {
            String v = ck.optString(k, "");
            if (!TextUtils.isEmpty(v)) {
                if (sb.length() > 0) sb.append(";");
                sb.append(k).append("=").append(v);
            }
        }
        String cookie = sb.toString();
        if (!PanAuth.is115CookieValid(cookie)) throw new Exception("凭据无效，请重试");
        PanAuth.put115Cookie(cookie);
        return cookie;
    }

    public static String statusText(int status) {
        switch (status) {
            case ST_SCANNED:
                return "已扫码，请在手机上点确认";
            case ST_EXPIRED:
                return "二维码已过期";
            case ST_CANCELLED:
                return "已取消";
            default:
                return "等待扫码";
        }
    }
}
