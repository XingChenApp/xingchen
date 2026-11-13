package com.fongmi.android.tv.login;

import android.text.TextUtils;
import android.util.Base64;

import androidx.collection.ArrayMap;

import com.fongmi.android.tv.utils.PanAuth;
import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;

import okhttp3.Response;

/**
 * 115 账号密码直登（对照胖5 src/auth/password.rs 的活链，服务端无凭证探针实测闭环）：
 * <ol>
 *   <li>GET passportapi /app/1.0/android/1.0/login/getKey → data.key（Base64 包的 PEM RSA 公钥）；</li>
 *   <li>密码 RSA/ECB/PKCS1Padding 加密后 Base64；</li>
 *   <li>POST /app/1.0/android/1.0/login/login，表单 {account, passwd} →
 *       data.cookie{UID,CID,SEID,KID}＋data.user_id。</li>
 * </ol>
 * 探针实据（2026-10-26 本机）：假账号＋错密码回「用户名或密码错误」(40101045)，
 * 说明字段名与加密口径已达凭据校验环；cipher_ver 服务端不要求（带与不带回包一致），不发。
 * 密码只在内存里过手：不落盘、不进日志、不进任何缓存。
 * <p>
 * 二步验证：账号开了短信二步时回包有 user_id 无 cookie（胖5 侧 errno 40101010 /
 * login_from_two_step），本类抛 {@link TwoStepException} 让界面明示，
 * 完成二步的短信校验字段无凭证探不死，不在此硬编（见 LoginMethod 注释）。
 */
public class Pwd115Provider {

    private static final String API_GET_KEY = "https://passportapi.115.com/app/1.0/android/1.0/login/getKey";
    private static final String API_LOGIN = "https://passportapi.115.com/app/1.0/android/1.0/login/login";
    private static final String UA_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    /** 账号开了短信二步验证：密码已对，但还要短信码才能落 Cookie */
    public static class TwoStepException extends Exception {
        public final String userId;

        public TwoStepException(String userId, String message) {
            super(message);
            this.userId = userId;
        }
    }

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA_WEB);
        h.put("Referer", "https://115.com/");
        h.put("Accept", "application/json, text/plain, */*");
        h.put("Accept-Language", "zh-CN,zh;q=0.9");
        return h;
    }

    /**
     * 账号密码登录。成功返回 cookie（已写入 PanAuth）；
     * 失败抛 Exception（message 是服务端原文）；触发二步抛 TwoStepException。
     */
    public String login(String account, String password) throws Exception {
        String keyBody;
        try (Response res = OkHttp.newCall(API_GET_KEY, headers()).execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new Exception("网络错误 " + res.code());
            keyBody = res.body().string();
        }
        JSONObject keyData = new JSONObject(keyBody).optJSONObject("data");
        String key = keyData == null ? "" : keyData.optString("key");
        if (TextUtils.isEmpty(key)) throw new Exception("拿登录密钥失败，请重试");
        String cipher = rsaEncrypt(password, key);

        ArrayMap<String, String> form = new ArrayMap<>();
        form.put("account", account);
        form.put("passwd", cipher);
        String body;
        try (Response res = OkHttp.newCall(API_LOGIN, headers(), OkHttp.toBody(form)).execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new Exception("网络错误 " + res.code());
            body = res.body().string();
        }
        JSONObject root = new JSONObject(body);
        JSONObject data = root.optJSONObject("data");
        JSONObject ck = data == null ? null : data.optJSONObject("cookie");
        if (ck == null) {
            String userId = data == null ? "" : data.optString("user_id");
            int errno = root.optInt("errno", root.optInt("code", 0));
            if (!TextUtils.isEmpty(userId) || errno == 40101010) {
                throw new TwoStepException(userId, "账号开启了短信二步验证");
            }
            String msg = root.optString("message", root.optString("error", ""));
            throw new Exception(TextUtils.isEmpty(msg) ? "登录失败，请重试" : msg);
        }
        StringBuilder sb = new StringBuilder();
        for (String k : new String[]{"UID", "CID", "SEID", "KID"}) {
            String v = ck.optString(k, "");
            if (!TextUtils.isEmpty(v)) {
                if (sb.length() > 0) sb.append(";");
                sb.append(k).append("=").append(v);
            }
        }
        String cookie = sb.toString();
        if (!PanAuth.is115CookieValid(cookie)) throw new Exception("登录成功但凭据无效，请改用扫码登录");
        PanAuth.put115Cookie(cookie);
        return cookie;
    }

    /** getKey 的 key 是 Base64(PEM)：解包 → X509 公钥 → PKCS#1 v1.5 加密 → Base64 */
    private static String rsaEncrypt(String plain, String keyB64) throws Exception {
        String pem = new String(Base64.decode(keyB64, Base64.DEFAULT), StandardCharsets.UTF_8);
        String der = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] derBytes = Base64.decode(der, Base64.DEFAULT);
        PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(derBytes));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, pub);
        return Base64.encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }
}
