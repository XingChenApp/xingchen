package com.fongmi.android.tv.login;

import android.os.Looper;
import android.text.TextUtils;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.PanAuth;
import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Response;

/**
 * 115 API 网关：带自动登录拦截。
 * <p>
 * py / js / 海阔规则调 115 接口时走这里，不用自己写任何登录代码：
 * <pre>
 * from java import jclass
 * LM = jclass("com.fongmi.android.tv.login.LoginManager")
 * body = LM.api115("https://webapi.115.com/files?aid=1&cid=0")  # JSON 字符串
 * </pre>
 * <p>
 * 流程：自动带上 115 cookie 发请求 → 返回 401 或 115 报 state=false
 * → 在 UI 线程弹壳子统一登录框并阻塞等待 → 登录成功后用新 cookie 自动重发一次
 * → 把结果原样返回。py 全程无感。
 */
public class AutoLogin115 {

    /** 登录等待超时（毫秒）：扫码框 4 分钟过期，这里多留 1 分钟 */
    private static final long LOGIN_WAIT_MS = 300_000L;

    /** 115 接口未登录的典型标记 */
    private static boolean isAuthFailure(int code, String body, String url) {
        if (code == 401) return true;
        if (TextUtils.isEmpty(body) || url == null) return false;
        // 115 登录/扫码接口自身不做登录拦截，避免递归
        if (url.contains("qrcodeapi.115.com") || url.contains("passportapi.115.com")) return false;
        String t = body.trim();
        if (!t.startsWith("{")) return false;
        try {
            JSONObject o = new JSONObject(t);
            if (o.has("state")) {
                Object s = o.get("state");
                if (s instanceof Boolean) return !(Boolean) s;
                if (s instanceof Number) return ((Number) s).intValue() == 0;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    private static Map<String, String> headersWithCookie(Map<String, String> extra) {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        h.put("Accept", "application/json, text/plain, */*");
        if (extra != null) h.putAll(extra);
        String cookie = PanAuth.get115Cookie();
        if (!TextUtils.isEmpty(cookie)) h.put("Cookie", cookie);
        return h;
    }

    private static String executeOnce(String url, Map<String, String> headers, String postBody) throws Exception {
        try (Response res = postBody == null
                ? OkHttp.newCall(url, headersWithCookie(headers)).execute()
                : OkHttp.newCall(url, headersWithCookie(headers), formBody(postBody)).execute()) {
            int code = res.code();
            String body = res.body() == null ? "" : res.body().string();
            if (isAuthFailure(code, body, url)) throw new AuthException(code, body);
            if (!res.isSuccessful()) throw new Exception("HTTP " + code);
            return body;
        }
    }

    private static okhttp3.RequestBody formBody(String json) {
        androidx.collection.ArrayMap<String, String> form = new androidx.collection.ArrayMap<>();
        if (!TextUtils.isEmpty(json)) {
            try {
                JSONObject o = new JSONObject(json);
                for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                    String k = it.next();
                    form.put(k, o.optString(k, ""));
                }
            } catch (Exception ignored) {
            }
        }
        return OkHttp.toBody(form);
    }

    /**
     * 阻塞等待用户完成登录。必须在子线程调用，主线程直接返回 false。
     *
     * @return true=登录成功，false=失败/取消/超时/主线程
     */
    public static boolean blockingLogin() {
        if (isMainThread()) return false;
        if (LoginManager.is115LoggedIn()) return true;
        FragmentActivity activity = currentActivity();
        if (activity == null) return false;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        App.post(() -> LoginManager.get().login115(activity, new LoginManager.LoginCallback() {
            @Override
            public void onSuccess(String providerId, String cookie) {
                ok.set(true);
                latch.countDown();
            }

            @Override
            public void onError(String msg) {
                latch.countDown();
            }

            @Override
            public void onCancel() {
                latch.countDown();
            }
        }));
        try {
            latch.await(LOGIN_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ok.get() && LoginManager.is115LoggedIn();
    }

    private static FragmentActivity currentActivity() {
        android.app.Activity a = App.activity();
        return a instanceof FragmentActivity ? (FragmentActivity) a : null;
    }

    /**
     * 同步 GET 115 接口，自动处理未登录（弹框 → 重试一次）。
     *
     * @return 响应体；网络错误抛异常给 py
     */
    public static String get(String url) throws Exception {
        return get(url, null);
    }

    public static String get(String url, Map<String, String> headers) throws Exception {
        // 先看本地 cookie 是否有效，无效直接先登录，避免一次注定失败的请求
        boolean triedLogin = false;
        if (!LoginManager.is115LoggedIn()) {
            triedLogin = true;
            if (!blockingLogin()) throw new AuthException(-1, "{\"state\":false,\"error\":\"115 未登录\"}");
        }
        try {
            return executeOnce(url, headers, null);
        } catch (AuthException e) {
            if (triedLogin) throw e;
            if (!blockingLogin()) throw e;
            return executeOnce(url, headers, null);
        }
    }

    /**
     * 同步 POST 表单到 115 接口，自动处理未登录。
     *
     * @param formJson 表单字段的 JSON，如 {"account":"uid","app":"web"}
     */
    public static String post(String url, String formJson) throws Exception {
        return post(url, null, formJson);
    }

    public static String post(String url, Map<String, String> headers, String formJson) throws Exception {
        boolean triedLogin = false;
        if (!LoginManager.is115LoggedIn()) {
            triedLogin = true;
            if (!blockingLogin()) throw new AuthException(-1, "{\"state\":false,\"error\":\"115 未登录\"}");
        }
        try {
            return executeOnce(url, headers, formJson);
        } catch (AuthException e) {
            if (triedLogin) throw e;
            if (!blockingLogin()) throw e;
            return executeOnce(url, headers, formJson);
        }
    }

    /** 未登录异常：py 可捕获后自行处理，code=-1 表示连弹框机会都没有（无界面/主线程） */
    public static class AuthException extends Exception {
        public final int code;
        public final String body;

        public AuthException(int code, String body) {
            super("115 未登录" + (code > 0 ? " (HTTP " + code + ")" : ""));
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }

    /** 给 py 的便利方法：确保已登录并返回 cookie（阻塞等待），失败返回空串 */
    public static String ensureCookie() {
        if (LoginManager.is115LoggedIn()) return LoginManager.get115Cookie();
        return blockingLogin() ? LoginManager.get115Cookie() : "";
    }

    /** 供调试：当前线程是否允许阻塞登录 */
    public static boolean canBlockingLogin() {
        return !isMainThread() && currentActivity() != null;
    }
}
