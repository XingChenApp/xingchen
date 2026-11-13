package com.fongmi.android.tv.cloud.drivepikpak;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;

import com.fongmi.android.tv.clash.ClashSettings;
import com.fongmi.android.tv.clash.MihomoManager;
import com.fongmi.android.tv.clash.SourceProxyPolicy;
import com.fongmi.android.tv.cloud.CloudAccountStore;
import com.fongmi.android.tv.cloud.CloudDrive;
import com.fongmi.android.tv.cloud.CloudFile;
import com.fongmi.android.tv.cloud.CloudTask;
import com.github.catvod.net.GlobalProxy;
import com.github.catvod.net.SourceGate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.net.ProxySelector;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * PikPak 网盘原生客户端
 * 来源：deepdarkpaw/pikpak-sdk（Go，MIT，API map 最完备）
 * 认证：OAuth2 账号密码，user.mypikpak.net
 * API：api-drive.mypikpak.net，Bearer 认证
 */
public class DrivePikPak implements CloudDrive {

    public static final String ID = "pikpak";

    // 来源：pikpak-sdk/config.go（DefaultUserBaseURL/DefaultAPIBaseURL）
    // 注意：调研报告写的是 .com，SDK 用的是 .net，以 SDK 为准
    private static final String USER_HOST = "https://user.mypikpak.net";
    private static final String API_HOST = "https://api-drive.mypikpak.net";

    // 来源：pikpak-sdk/config.go（DefaultWebClientID/DefaultWebClientSecret）
    private static final String CLIENT_ID = "YUMx5nI8ZU8Ap8pm";
    private static final String CLIENT_SECRET = "dbw2OtmVEeuUvIptb1Coyg";
    private static final String CLIENT_VERSION = "2.0.0";
    private static final String PACKAGE_NAME = "mypikpak.com";
    private static final String CAPTCHA_REDIRECT_URI = "https://api.mypikpak.com/v1/auth/callback";

    /**
     * 人机验证签名 salt 表（逆向自 mypikpak.com 网页 JS 的 calculateCaptchaSign）。
     * 算法：sign = clientId + clientVersion + packageName + deviceId + timestamp；
     * 依次对每个 salt 做 sign = md5(sign + salt)；最终 captcha_sign = "1." + sign。
     * salt 取自 2026-10-11 当日线上 drive 主 chunk（含 algorithms 的那个），共 15 个。
     * 若日后登录又报 captcha_required 且自动取 token 失败，优先怀疑 salt 已轮换，
     * 按本文件头注释的逆向步骤重新抓取即可（搜 calculateCaptchaSign）。
     */
    private static final String[] CAPTCHA_SALTS = {
            "C9qPpZLN8ucRTaTiUMWYS9cQvWOE",
            "+r6CQVxjzJV6LCV",
            "F",
            "pFJRC",
            "9WXYIDGrwTCz2OiVlgZa90qpECPD6olt",
            "/750aCr4lm/Sly/c",
            "RB+DT/gZCrbV",
            "",
            "CyLsf7hdkIRxRm215hl",
            "7xHvLi2tOYP0Y92b",
            "ZGTXXxu8E/MIWaEDB+Sm/",
            "1UI3",
            "E7fP5Pfijd+7K+t6Tg/NhuLq0eEUVChpJSkrKxpO",
            "ihtqpG6FMt65+Xk+tWUH2",
            "NhXXU9rg4XXdzo7u5o",
    };
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36";

    // 接口限流 5rps（来源：pikpak-sdk/config.go DefaultRateLimitConfig）
    private static final long MIN_INTERVAL_MS = 220;

    /**
     * drive 接口的 captcha_token 缓存（按 action 分桶）。
     * 风控口径（对照 pikpakapi/pikpak-sdk 的公开实现）：api-drive 的接口调用要在
     * X-Captcha-Token 头带一枚经 shield/captcha/init 按「METHOD:路径」现取的 token，
     * token 有效期约 300 秒；缺失/失效时服务端回 HTTP 400、error_code=9
     * （captcha_invalid / "captcha_token is empty"，2026-10-26 真机实证：
     * 列表请求从不带 token、恒死在这一步）。本地缓存 240 秒，留 60 秒余量。
     */
    private static final long CAPTCHA_TTL_MS = 240_000L;
    private static final int MAX_CAPTCHA_RETRY = 1;

    private static final class CaptchaEntry {
        final String token;
        final long expiresAt;

        CaptchaEntry(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }

    private final java.util.Map<String, CaptchaEntry> captchaCache = new java.util.HashMap<>();

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Context context;

    private String accessToken = "";
    private String refreshToken = "";
    private String userId = "";
    private String deviceId = "";
    private String userName = "";
    private long expiresAt = 0;
    private long lastReqTime = 0;

    /** 验证码异常：登录可能触发 captcha，需用户手动过（来源：pikpak-sdk auth.go ManualVerificationError） */
    public static class CaptchaRequiredException extends Exception {
        private final String verifyUrl;
        private final String captchaToken;
        public CaptchaRequiredException(String verifyUrl, String captchaToken) {
            super("需要人机验证");
            this.verifyUrl = verifyUrl;
            this.captchaToken = captchaToken == null ? "" : captchaToken;
        }
        public String getVerifyUrl() { return verifyUrl; }
        /** 服务端下发的 captcha_token（会话标识）：用户在验证页完成后，带着它重试登录 */
        public String getCaptchaToken() { return captchaToken; }
    }

    public DrivePikPak(Context context) {
        this.context = context.getApplicationContext();
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                // 节点门控接线：自有 OkHttp 也挂壳子的 SourceGate 选择器（与 HkHttp
                // 同款两层包装），请求线程有 pikpak 打点时按策略表走本地混合口、否则直连。
                // 仅挂选择器不够：决策靠线程打点，见 beginMark()。
                .proxySelector(SourceGate.wrapSelector(GlobalProxy.wrapSelector(ProxySelector.getDefault())))
                .cookieJar(new WebViewCookieJar())
                .build();
        loadSession();
    }

    // ---------- 节点门控打点 ----------

    private static final long NODE_START_RETRY_MS = 60_000L;
    private static volatile long nodeStartFailedAt = 0L;

    /**
     * PikPak 在策略表里开了「走节点」、而内核没在跑时，发请求前同步把内核拉起来
     * （FreeJsBridge.ensureNodeForMusic 同款口径：
     * 单源开了即生效，不要求总开关先开）。拉起失败不堵请求：照常发（门控会判直连），
     * 但留一条 clash 日志说明本次为什么没走成节点，60 秒内不重复尝试。
     * 本类调用方都在后台线程，ensureRunning 的同步阻塞可接受。
     */
    private void ensureNodeForPikPak() {
        try {
            if (!SourceProxyPolicy.isEnabled(context, SourceProxyPolicy.KIND_PIKPAK, ID)) return;
            if (MihomoManager.get().isRunning()) return;
            long now = System.currentTimeMillis();
            if (now - nodeStartFailedAt < NODE_START_RETRY_MS) return;
            if (!MihomoManager.get().ensureRunning(context)) {
                nodeStartFailedAt = now;
                try {
                    com.github.catvod.crawler.SpiderDebug.log("clash",
                            "gate pikpak node-start-failed id=%s （内核没拉起来，本次请求直连）", ID);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 走节点打点：全部走节点开（总开关开且全局模式）→
     * 不打点，走选择器的无归属全局路径；否则按 pikpak 单源开关打点。
     * 打点前先按需拉起内核。返回打点前的旧归属，调用方在 finally 里
     * SourceGate.pop(prev) 恢复。
     */
    private SourceGate.Mark beginMark() {
        try {
            ensureNodeForPikPak();
            boolean global = ClashSettings.isGlobalMode(context) && ClashSettings.isEnabled(context);
            if (!global) return SourceGate.push(SourceProxyPolicy.KIND_PIKPAK, ID);
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 与系统 WebView 共享 Cookie。
     * 人机验证在 WebView 内完成时服务端可能下发会话 Cookie，
     * 让后续 API 请求带上它们，否则验证状态传不到 OkHttp。
     */
    private static class WebViewCookieJar implements CookieJar {
        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            try {
                android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
                for (Cookie c : cookies) cm.setCookie(url.toString(), c.toString());
                cm.flush();
            } catch (Exception ignored) {}
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            try {
                android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
                String header = cm.getCookie(url.toString());
                if (TextUtils.isEmpty(header)) return Collections.emptyList();
                List<Cookie> out = new ArrayList<>();
                for (String pair : header.split(";")) {
                    int eq = pair.indexOf('=');
                    if (eq <= 0) continue;
                    String name = pair.substring(0, eq).trim();
                    String value = pair.substring(eq + 1).trim();
                    if (name.isEmpty()) continue;
                    out.add(new Cookie.Builder().name(name).value(value).domain(url.host()).build());
                }
                return out;
            } catch (Exception e) {
                com.github.catvod.crawler.SpiderDebug.log("pikpak", "load webview cookies failed url=%s error=%s", url.host(), e.getMessage());
                return Collections.emptyList();
            }
        }
    }

    // ---------- 会话持久化 ----------

    private void loadSession() {
        accessToken = CloudAccountStore.getValue(context, ID, "access_token");
        refreshToken = CloudAccountStore.getValue(context, ID, "refresh_token");
        userId = CloudAccountStore.getValue(context, ID, "user_id");
        deviceId = CloudAccountStore.getValue(context, ID, "device_id");
        userName = CloudAccountStore.getUserName(context, ID);
        expiresAt = CloudAccountStore.getLong(context, ID, "expires_at");
        if (TextUtils.isEmpty(deviceId)) {
            deviceId = UUID.randomUUID().toString().replace("-", "");
            CloudAccountStore.saveValue(context, ID, "device_id", deviceId);
        }
    }

    private void saveSession() {
        CloudAccountStore.saveValue(context, ID, "access_token", accessToken);
        CloudAccountStore.saveValue(context, ID, "refresh_token", refreshToken);
        CloudAccountStore.saveValue(context, ID, "user_id", userId);
        CloudAccountStore.saveUserName(context, ID, userName);
        CloudAccountStore.saveLong(context, ID, "expires_at", expiresAt);
    }

    private void clearSession() {
        accessToken = "";
        refreshToken = "";
        userId = "";
        userName = "";
        expiresAt = 0;
        CloudAccountStore.clear(context, ID);
        // deviceId 保留，下次登录复用
        CloudAccountStore.saveValue(context, ID, "device_id", deviceId);
    }

    // ---------- 底层请求 ----------

    private final Object rateLock = new Object();

    /**
     * 限流：控制接口 5rps。旧实现是 synchronized 方法、还在锁里 sleep 满间隔：
     * 一个线程等间隔时，其它线程连排队都排不上（refresh() 用同一把实例锁、
     * 且在锁里走网络），页面刚进入时列表线程与账号信息线程的 4 发请求被叠成
     * 串行楼梯，列表那一发排到第 2–4 位才起跑。现改为锁内只领一个起跑时刻、
     * 锁外睡到点：相邻请求起跑间隔 ≥220ms 的口径不变，等待不再互堵。
     */
    private void rateLimit() {
        long start;
        synchronized (rateLock) {
            long now = System.currentTimeMillis();
            start = Math.max(now, lastReqTime + MIN_INTERVAL_MS);
            lastReqTime = start;
        }
        long wait = start - System.currentTimeMillis();
        if (wait > 0) {
            try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
        }
    }

    // 401 刷新后重试上限：对齐网盘同类口径——Proxy115 的 MAX_RETRY_403＝3、
    // DriveQuark.getPlayUrl 的 attempt<=3（均为首发＋最多重试 3 次，超限抛既有失败异常）。
    private static final int MAX_AUTH_RETRY = 3;

    private JSONObject api(String method, String path, JSONObject query, JSONObject body, boolean auth) throws Exception {
        return api(method, path, query, body, auth, 0, 0);
    }

    private JSONObject api(String method, String path, JSONObject query, JSONObject body, boolean auth, int authRetry, int captchaRetry) throws Exception {
        rateLimit();
        HttpUrl.Builder ub = HttpUrl.parse(API_HOST + path).newBuilder();
        if (query != null) {
            JSONArray names = query.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String k = names.getString(i);
                    ub.addQueryParameter(k, query.optString(k));
                }
            }
        }
        Request.Builder rb = new Request.Builder().url(ub.build());
        rb.header("User-Agent", UA);
        rb.header("Accept", "application/json");
        rb.header("X-Device-ID", deviceId);
        rb.header("X-Client-Version", CLIENT_VERSION);
        rb.header("X-Package-Name", PACKAGE_NAME);
        // 分享读写接口要求该头（来源：pikpak-kotlin ShareEndpoint，官方 web 客户端每个请求都带）
        rb.header("X-Client-Id", CLIENT_ID);
        if (auth) {
            ensureToken();
            rb.header("Authorization", "Bearer " + accessToken);
        }
        // drive 接口风控：X-Captcha-Token 按本次动作（METHOD:路径）现取并缓存，
        // 缺这枚头服务端恒回 error_code=9（见 CAPTCHA_TTL_MS 处注释）。
        String action = method + ":" + path;
        String captchaToken = cachedCaptchaToken(action);
        if (!TextUtils.isEmpty(captchaToken)) {
            rb.header("X-Captcha-Token", captchaToken);
        }
        if ("POST".equals(method) || "PATCH".equals(method)) {
            String json = body != null ? body.toString() : "{}";
            rb.method(method, RequestBody.create(json, JSON));
        } else if ("DELETE".equals(method)) {
            rb.delete();
        } else {
            rb.get();
        }
        SourceGate.Mark prev = beginMark();
        try (Response resp = client.newCall(rb.build()).execute()) {
            String txt = resp.body() != null ? resp.body().string() : "";
            if (resp.code() == 401 && auth && authRetry < MAX_AUTH_RETRY) {
                // token 过期，刷新后重试（上限 MAX_AUTH_RETRY 次）；超限不再刷新/递归，
                // 落到下面既有的接口错误出口，避免服务端持续 401 时无限递归刷令牌。
                refresh();
                return api(method, path, query, body, true, authRetry + 1, captchaRetry);
            }
            if (!resp.isSuccessful()) {
                if (isCaptchaInvalid(txt) && captchaRetry < MAX_CAPTCHA_RETRY) {
                    // token 被判失效：清掉本动作的缓存、重取新 token 再试一次（只一次，
                    // 免得服务端持续判错时来回打转）。
                    invalidateCaptcha(action);
                    return api(method, path, query, body, auth, authRetry, captchaRetry + 1);
                }
                throw new Exception("PikPak 接口错误 " + resp.code() + "：" + errorDetail(resp.code(), txt));
            }
            return new JSONObject(txt.isEmpty() ? "{}" : txt);
        } finally {
            SourceGate.pop(prev);
        }
    }

    /**
     * captcha_token 可用宽限：服务端 token 有效期约 300 秒、本地缓存 240 秒，
     * 缓存过期后 55 秒内 token 的实际年龄仍不到 300 秒、照常可用——这段里先用
     * 旧的并后台换新，不让开文件夹那一下干等一发 init。真被判失效
     * （error_code=9）有 api() 里的清缓存＋重取＋重试一次兜底，机制没弱化。
     */
    private static final long CAPTCHA_GRACE_MS = 55_000L;
    /** 缓存剩不到这么久就提前后台换新，正常浏览节奏下不必再同步等 init。 */
    private static final long CAPTCHA_REFRESH_AHEAD_MS = 45_000L;
    /** 后台换新失败后的退避，免得网络坏时每发请求都白打一发 init。 */
    private static final long CAPTCHA_REFRESH_BACKOFF_MS = 30_000L;

    private final java.util.Map<String, java.util.concurrent.FutureTask<String>> captchaInFlight = new java.util.HashMap<>();
    private final java.util.Map<String, Long> captchaRefreshFailAt = new java.util.HashMap<>();

    /**
     * 取本动作的 captcha_token：新鲜缓存直接用；临期/宽限内先用旧的并后台
     * 换新；完全没有才同步取（同动作并发只取一发，其余等那一发的结果）；
     * 取失败回空串（照常发请求，服务端判 9 时再走重试）。
     */
    private String cachedCaptchaToken(String action) {
        CaptchaEntry e;
        synchronized (captchaCache) {
            e = captchaCache.get(action);
        }
        long now = System.currentTimeMillis();
        if (e != null && now < e.expiresAt + CAPTCHA_GRACE_MS) {
            if (now > e.expiresAt - CAPTCHA_REFRESH_AHEAD_MS) refreshCaptchaAsync(action);
            return e.token;
        }
        return fetchCaptchaSingleFlight(action);
    }

    /** 同步取 token 的单飞口：同动作只允许一发在飞，其余调用等它完成再读结果。 */
    private String fetchCaptchaSingleFlight(String action) {
        java.util.concurrent.FutureTask<String> task;
        boolean owner;
        synchronized (captchaCache) {
            java.util.concurrent.FutureTask<String> existing = captchaInFlight.get(action);
            if (existing != null) {
                task = existing;
                owner = false;
            } else {
                task = new java.util.concurrent.FutureTask<>(() -> doFetchCaptcha(action));
                captchaInFlight.put(action, task);
                owner = true;
            }
        }
        if (owner) task.run();
        try {
            String token = task.get();
            return token == null ? "" : token;
        } catch (Exception ex) {
            return "";
        } finally {
            if (owner) {
                synchronized (captchaCache) {
                    captchaInFlight.remove(action);
                }
            }
        }
    }

    /** 真正打 shield/captcha/init 的一发：成功落缓存并回 token，失败回空串并记退避时刻。 */
    private String doFetchCaptcha(String action) {
        try {
            String token = fetchCaptchaToken(action);
            if (!TextUtils.isEmpty(token)) {
                synchronized (captchaCache) {
                    captchaCache.put(action, new CaptchaEntry(token, System.currentTimeMillis() + CAPTCHA_TTL_MS));
                    captchaRefreshFailAt.remove(action);
                }
                return token;
            }
        } catch (Exception ex) {
            android.util.Log.w("DrivePikPak", "captcha_token fetch failed action=" + action, ex);
        }
        synchronized (captchaCache) {
            captchaRefreshFailAt.put(action, System.currentTimeMillis());
        }
        return "";
    }

    /** 后台换新：缓存还新鲜、已有在飞的、或刚失败还在退避内的不重复打；只在后台线程跑，不堵调用方。 */
    private void refreshCaptchaAsync(String action) {
        synchronized (captchaCache) {
            CaptchaEntry e = captchaCache.get(action);
            if (e != null && System.currentTimeMillis() < e.expiresAt - CAPTCHA_REFRESH_AHEAD_MS) return;
            if (captchaInFlight.containsKey(action)) return;
            Long failAt = captchaRefreshFailAt.get(action);
            if (failAt != null && System.currentTimeMillis() - failAt < CAPTCHA_REFRESH_BACKOFF_MS) return;
        }
        new Thread(() -> fetchCaptchaSingleFlight(action), "pikpak-captcha").start();
    }

    /**
     * 页面打开时预热热动作的 captcha_token（与首屏列表请求并行跑），让列表
     * 那一发不必先串行等 init。token 机制本身（按「METHOD:路径」现取、缓存
     * 240 秒、判 9 清缓存重取重试）一字未动，只是把「现取」提前到点开
     * 文件夹之前——action 键仍是路径级，同一路径的所有文件夹共用一枚，
     * 不存在每层一发的浪费。
     */
    public void warmUpCaptcha() {
        refreshCaptchaAsync("GET:/drive/v1/files");
        refreshCaptchaAsync("GET:/drive/v1/about");
    }

    private void invalidateCaptcha(String action) {
        synchronized (captchaCache) {
            captchaCache.remove(action);
        }
    }

    /** 服务端错误体是否在判 captcha 失效：error_code=9 或 error=captcha_invalid。 */
    private static boolean isCaptchaInvalid(String txt) {
        if (txt == null || txt.isEmpty()) return false;
        try {
            JSONObject d = new JSONObject(txt);
            return d.optInt("error_code", -1) == 9 || "captcha_invalid".equals(d.optString("error", ""));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 接口错误的人话明细：原文整段进调试日志（tag=pikpak），脸上只给可读文案，
     * 不再把整段 JSON 糊给用户（2026-10-26 截图实证：列表失败提示是整段错误 JSON）。
     */
    private static String errorDetail(int httpCode, String txt) {
        String raw = txt == null ? "" : txt;
        try {
            com.github.catvod.crawler.SpiderDebug.log("pikpak", "api error http=%d body=%s", httpCode, raw);
        } catch (Throwable ignored) {
        }
        try {
            JSONObject d = new JSONObject(raw);
            if (d.optInt("error_code", -1) == 9 || "captcha_invalid".equals(d.optString("error", ""))) {
                return "人机验证未通过，请稍后重试";
            }
            String desc = d.optString("error_description", "");
            if (!desc.isEmpty()) return desc;
            String err = d.optString("error", "");
            if (!err.isEmpty()) return err;
        } catch (Exception ignored) {
        }
        return "请稍后重试";
    }

    /** 人机验证调试日志：把触发验证时的服务端完整返回写到文件，定位验证页 URL 字段 */
    private void dumpVerifyResponse(String rawJson) {
        try {
            File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "XingChen");
            if (!dir.exists()) dir.mkdirs();
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File f = new File(dir, "pikpak_verify_" + ts + ".json");
            FileWriter w = new FileWriter(f);
            w.write(rawJson == null ? "" : rawJson);
            w.close();
        } catch (Exception ignored) {
        }
    }

    private JSONObject userApi(String path, JSONObject query, JSONObject body, String captchaToken) throws Exception {
        rateLimit();
        HttpUrl.Builder ub = HttpUrl.parse(USER_HOST + path).newBuilder();
        if (query != null) {
            JSONArray names = query.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String k = names.getString(i);
                    ub.addQueryParameter(k, query.optString(k));
                }
            }
        }
        Request.Builder rb = new Request.Builder().url(ub.build());
        rb.header("User-Agent", UA);
        rb.header("Accept", "application/json");
        rb.header("X-Device-ID", deviceId);
        if (!TextUtils.isEmpty(captchaToken)) {
            rb.header("X-Captcha-Token", captchaToken);
        }
        String json = body != null ? body.toString() : "{}";
        rb.post(RequestBody.create(json, JSON));
        SourceGate.Mark prev = beginMark();
        try (Response resp = client.newCall(rb.build()).execute()) {
            String txt = resp.body() != null ? resp.body().string() : "";
            JSONObject d = new JSONObject(txt.isEmpty() ? "{}" : txt);
            // 检测 captcha（来源：pikpak-sdk ManualVerificationError）
            if (d.has("captcha_token") || d.optString("error_code", "").contains("captcha")
                    || d.optString("error", "").contains("captcha")) {
                dumpVerifyResponse(txt);  // 调试：落盘完整返回，定位验证页 URL 字段
                String url = d.optString("url", d.optString("verification_url", ""));
                throw new CaptchaRequiredException(url, d.optString("captcha_token", ""));
            }
            if (!resp.isSuccessful()) {
                throw new Exception("登录失败：" + d.optString("error_description", d.optString("error", txt)));
            }
            return d;
        } finally {
            SourceGate.pop(prev);
        }
    }

    // ---------- 认证 ----------

    /**
     * 账号密码登录（来源：pikpak-sdk/auth.go Login）
     * POST /v1/auth/signin?client_id=...
     * Body: {captcha_token, client_id, client_secret, username, password}
     */
    public void login(String username, String password) throws Exception {
        login(username, password, "");
    }

    public void login(String username, String password, String captchaToken) throws Exception {
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            throw new Exception("用户名和密码不能为空");
        }
        if (TextUtils.isEmpty(captchaToken)) {
            // 全自动人机验证：先调 shield/captcha/init 取 token（算法逆向自官网 JS，
            // 见本文件头 CAPTCHA_SALTS 注释），登录时经 body + X-Captcha-Token 带上。
            // 若取 token 失败则按空继续，服务端仍要求验证时会走 CaptchaRequiredException 兜底。
            try {
                captchaToken = fetchCaptchaToken("POST:/v1/auth/signin");
            } catch (Exception e) {
                // 取 token 失败则按空继续，服务端仍要求验证时会走 CaptchaRequiredException 兜底；
                // 打日志方便定位（salt 轮换 / 网络被墙都会走到这里）
                android.util.Log.w("DrivePikPak", "auto captcha_token fetch failed, continue without token", e);
                captchaToken = "";
            }
        }
        JSONObject query = new JSONObject().put("client_id", CLIENT_ID);
        JSONObject body = new JSONObject()
                .put("captcha_token", captchaToken == null ? "" : captchaToken)
                .put("client_id", CLIENT_ID)
                .put("client_secret", CLIENT_SECRET)
                .put("username", username)
                .put("password", password);
        JSONObject d = userApi("/v1/auth/signin", query, body, captchaToken);
        String at = d.optString("access_token");
        String rt = d.optString("refresh_token");
        if (TextUtils.isEmpty(at) || TextUtils.isEmpty(rt)) {
            throw new Exception("登录返回缺 token（可能是接口变更）");
        }
        accessToken = at;
        refreshToken = rt;
        userId = d.optString("sub");
        long expiresIn = d.optLong("expires_in", 7200);
        // 提前 5 分钟视为过期（来源：pikpak-sdk TokenSkew）
        expiresAt = System.currentTimeMillis() + (expiresIn - 300) * 1000;
        userName = username;
        saveSession();
    }

    /** 提交验证码 token（用户手动过验证后调用，来源：pikpak-sdk SubmitCaptcha） */
    public void submitCaptchaToken(String captchaToken) {
        // captcha token 在下次 login 时通过 X-Captcha-Token 带上
        CloudAccountStore.saveValue(context, ID, "captcha_token", captchaToken);
    }

    /**
     * WebView 登录成功后调用：把网页版拿到的凭证导入为 App 会话。
     * 网页版与 App 使用同一 OAuth client_id（YUMx5nI8ZU8Ap8pm），
     * refresh_token 可直接复用 refresh() 接口，后续流程零改动。
     * userId 为空时会尝试从 access_token（JWT）的 sub 字段解析。
     *
     * @param refreshToken 网页 localStorage credentials* 里的 refresh_token（必需）
     * @param accessToken  网页凭证里的 access_token（可空，为空时调用方应随后调 refresh() 换取）
     * @param expiresInSec access_token 有效期秒数（<=0 时按 7200）
     * @param userId       用户 id（可空）
     */
    public void importWebSession(String refreshToken, String accessToken, long expiresInSec, String userId) {
        if (TextUtils.isEmpty(refreshToken)) {
            throw new IllegalArgumentException("refresh_token 不能为空");
        }
        this.refreshToken = refreshToken;
        this.accessToken = accessToken == null ? "" : accessToken;
        if (!TextUtils.isEmpty(userId)) {
            this.userId = userId;
        } else if (!TextUtils.isEmpty(this.accessToken)) {
            String sub = parseJwtSub(this.accessToken);
            if (!TextUtils.isEmpty(sub)) this.userId = sub;
        }
        long expiresIn = expiresInSec > 0 ? expiresInSec : 7200;
        // 提前 5 分钟视为过期（与 login() 一致）
        this.expiresAt = System.currentTimeMillis() + (expiresIn - 300) * 1000;
        saveSession();
    }

    /** access_token 是 JWT 时，从 payload 的 sub 字段取 user_id */
    private static String parseJwtSub(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) return "";
            String payload = parts[1].replace('-', '+').replace('_', '/');
            int pad = (4 - payload.length() % 4) % 4;
            StringBuilder sb = new StringBuilder(payload);
            for (int i = 0; i < pad; i++) sb.append('=');
            String json = new String(android.util.Base64.decode(sb.toString(), android.util.Base64.DEFAULT), "UTF-8");
            return new JSONObject(json).optString("sub", "");
        } catch (Exception e) {
            return "";
        }
    }

    /** md5 十六进制小写（captcha 签名用） */
    private static String md5Hex(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 计算 captcha_sign（对着 mypikpak.com 网页 JS 的 calculateCaptchaSign 逆向，
     * 2026-10-11 用当日线上 JS 实测验证通过）。
     * sign = clientId + clientVersion + packageName + deviceId + timestamp，
     * 对 CAPTCHA_SALTS 逐个做 sign = md5(sign + salt)，最后拼 "1." 前缀。
     */
    private String calcCaptchaSign(String timestamp) {
        String sign = CLIENT_ID + CLIENT_VERSION + PACKAGE_NAME + deviceId + timestamp;
        for (String salt : CAPTCHA_SALTS) {
            sign = md5Hex(sign + salt);
        }
        return "1." + sign;
    }

    /**
     * 取 captcha_token（对着 lyqingye/pikpak-go 的 CaptchaToken 实现）。
     * POST /v1/shield/captcha/init?client_id=... ，action 形如 "POST:/v1/auth/signin"。
     * 返回的 token 有效期约 300 秒，登录时经 body.captcha_token + X-Captcha-Token 头带上。
     */
    private String fetchCaptchaToken(String action) throws Exception {
        rateLimit();
        String ts = String.valueOf(System.currentTimeMillis());
        JSONObject meta = new JSONObject()
                .put("captcha_sign", calcCaptchaSign(ts))
                .put("client_version", CLIENT_VERSION)
                .put("package_name", PACKAGE_NAME)
                .put("timestamp", ts)
                .put("user_id", userId == null ? "" : userId);
        JSONObject body = new JSONObject()
                .put("action", action)
                .put("client_id", CLIENT_ID)
                .put("device_id", deviceId)
                .put("meta", meta)
                .put("redirect_uri", CAPTCHA_REDIRECT_URI);
        HttpUrl url = HttpUrl.parse(USER_HOST + "/v1/shield/captcha/init").newBuilder()
                .addQueryParameter("client_id", CLIENT_ID).build();
        Request req = new Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("X-Device-ID", deviceId)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        SourceGate.Mark prev = beginMark();
        try (Response resp = client.newCall(req).execute()) {
            String txt = resp.body() != null ? resp.body().string() : "";
            JSONObject d = new JSONObject(txt.isEmpty() ? "{}" : txt);
            String token = d.optString("captcha_token");
            if (TextUtils.isEmpty(token)) {
                throw new Exception("取 captcha_token 失败：" + txt);
            }
            return token;
        } finally {
            SourceGate.pop(prev);
        }
    }

    /** 刷新 token（OAuth2 标准 refresh_token 模式） */
    public synchronized void refresh() throws Exception {
        if (TextUtils.isEmpty(refreshToken)) {
            throw new Exception("没有 refresh_token，需要重新登录");
        }
        rateLimit();
        JSONObject body = new JSONObject()
                .put("client_id", CLIENT_ID)
                .put("client_secret", CLIENT_SECRET)
                .put("grant_type", "refresh_token")
                .put("refresh_token", refreshToken);
        HttpUrl url = HttpUrl.parse(USER_HOST + "/v1/auth/token").newBuilder()
                .addQueryParameter("client_id", CLIENT_ID).build();
        Request req = new Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("X-Device-ID", deviceId)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        SourceGate.Mark prev = beginMark();
        try (Response resp = client.newCall(req).execute()) {
            String txt = resp.body() != null ? resp.body().string() : "";
            JSONObject d = new JSONObject(txt.isEmpty() ? "{}" : txt);
            if (!resp.isSuccessful()) {
                // refresh 失败 → 需要重新登录（来源：pikpak-sdk ErrReauthRequired）
                clearSession();
                throw new Exception("登录已过期，请重新登录");
            }
            String at = d.optString("access_token");
            String rt = d.optString("refresh_token");
            if (TextUtils.isEmpty(at)) {
                clearSession();
                throw new Exception("刷新 token 失败，请重新登录");
            }
            accessToken = at;
            if (!TextUtils.isEmpty(rt)) refreshToken = rt;  // refresh_token 轮换
            long expiresIn = d.optLong("expires_in", 7200);
            expiresAt = System.currentTimeMillis() + (expiresIn - 300) * 1000;
            saveSession();
        } finally {
            SourceGate.pop(prev);
        }
    }

    private void ensureToken() throws Exception {
        if (TextUtils.isEmpty(accessToken)) {
            throw new Exception("未登录");
        }
        if (System.currentTimeMillis() > expiresAt) {
            refresh();
        }
    }

    // ---------- CloudDrive 接口 ----------

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "PikPak";
    }

    @Override
    public boolean isLogin() {
        return !TextUtils.isEmpty(accessToken) && !TextUtils.isEmpty(userId);
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public void logout() {
        clearSession();
    }

    // ---------- 账号信息（容量 / 会员 / 用户名） ----------

    /** 账号信息（fetchAccountInfo 填充，未取到时 vipText 为空、容量为 -1） */
    private String vipText = "";
    private long spaceUsed = -1;
    private long spaceTotal = -1;
    private String spaceText = "";
    private boolean acctTried = false;

    /** 带 Bearer 的 USER_HOST GET（userApi 只支持 POST，这里补 GET 形态，401 刷新后重试一次） */
    private JSONObject userGet(String path) throws Exception {
        return userGet(path, 0);
    }

    private JSONObject userGet(String path, int authRetry) throws Exception {
        rateLimit();
        ensureToken();
        Request.Builder rb = new Request.Builder().url(USER_HOST + path);
        rb.header("User-Agent", UA);
        rb.header("Accept", "application/json");
        rb.header("X-Device-ID", deviceId);
        rb.header("Authorization", "Bearer " + accessToken);
        rb.get();
        SourceGate.Mark prev = beginMark();
        try (Response resp = client.newCall(rb.build()).execute()) {
            String txt = resp.body() != null ? resp.body().string() : "";
            if (resp.code() == 401 && authRetry < 1) {
                refresh();
                return userGet(path, authRetry + 1);
            }
            if (!resp.isSuccessful()) {
                throw new Exception("PikPak 用户接口错误 " + resp.code() + "：" + errorDetail(resp.code(), txt));
            }
            return new JSONObject(txt.isEmpty() ? "{}" : txt);
        } finally {
            SourceGate.pop(prev);
        }
    }

    /**
     * 取账号信息：GET /drive/v1/about 拿配额与会员到期；用户名为空时
     * （网页登录路径从不写用户名，此前恒空）再用 /v1/user/me 补名并存档。
     * about 响应形态未经真机验过，解析按字符串/数字两形态防御，并留痕待对。
     *
     * @return about 拿到并完成解析返回 true
     */
    public boolean fetchAccountInfo() {
        acctTried = true;
        boolean ok = false;
        try {
            JSONObject about = api("GET", "/drive/v1/about", null, null, true);
            JSONObject quota = about.optJSONObject("quota");
            if (quota != null) {
                spaceTotal = bytesOf(quota.opt("limit"));
                spaceUsed = bytesOf(quota.opt("usage"));
                if (spaceTotal > 0 && spaceUsed >= 0) {
                    spaceText = "已用 " + formatSize(spaceUsed) + " / 共 " + formatSize(spaceTotal);
                } else if (spaceUsed >= 0) {
                    spaceText = "已用 " + formatSize(spaceUsed);
                }
            }
            String expiresAt = about.optString("expires_at", "").trim();
            if (!expiresAt.isEmpty()) {
                vipText = "会员 · " + (expiresAt.length() >= 10 ? expiresAt.substring(0, 10) : expiresAt) + " 到期";
            }
            try {
                com.github.catvod.crawler.SpiderDebug.log("panacct", "pikpak about=%s", String.valueOf(about));
            } catch (Throwable ignored) {
            }
            ok = quota != null;
        } catch (Exception ignored) {
        }
        if (TextUtils.isEmpty(userName)) {
            try {
                JSONObject me = userGet("/v1/user/me");
                String name = me.optString("name", me.optString("email", "")).trim();
                if (!name.isEmpty()) {
                    userName = name;
                    saveSession();
                }
                try {
                    com.github.catvod.crawler.SpiderDebug.log("panacct", "pikpak user/me keys=%s",
                            String.valueOf(me.names()));
                } catch (Throwable ignored) {
                }
            } catch (Exception ignored) {
            }
        }
        return ok;
    }

    private static long bytesOf(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            try {
                return Long.parseLong(((String) v).trim());
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }

    private static String formatSize(long size) {
        if (size < 0) return "";
        if (size < 1024) return size + "B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format(Locale.US, "%.1f%sB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    /** VIP 文案（如「会员 · 2027-01-01 到期」），须先 fetchAccountInfo */
    public String getVipText() {
        return vipText;
    }

    /** 容量摘要（如「已用 12GB / 共 10TB」），未取到返回空 */
    public String getSpaceText() {
        return spaceText;
    }

    @Override
    public String storageSummary() {
        if (!acctTried) fetchAccountInfo();
        return spaceText;
    }

    /**
     * 按目录缓存的 page_token 链：offset/limit 兑页号（page = offset / limit），
     * 每页响应里的 next_page_token 记下来供下一页用；offset==0 时重建该目录的链。
     * 只能顺序翻页（UI 滚动续取与音乐页遍历都是顺序取页）；跳号取页（链上无对应 token）
     * 返回空表，不回第一页冒充，避免重复文件混进列表。
     */
    private final java.util.Map<String, List<String>> pageTokensByCid = new java.util.HashMap<>();

    /**
     * 文件列表（来源：pikpak-sdk/files.go List）
     * GET /drive/v1/files?parent_id=&limit=&page_token=&filters=
     */
    @Override
    public List<CloudFile> listDir(String cid, int offset, int limit) throws Exception {
        int pageSize = limit > 0 ? limit : 100;
        String key = cid == null ? "" : cid;
        String pageToken;
        int pageIndex = Math.max(0, offset) / pageSize;
        synchronized (pageTokensByCid) {
            if (pageIndex == 0) {
                List<String> chain = new ArrayList<>();
                chain.add("");
                pageTokensByCid.put(key, chain);
                pageToken = "";
            } else {
                List<String> chain = pageTokensByCid.get(key);
                if (chain == null || pageIndex >= chain.size()) return new ArrayList<>();
                pageToken = chain.get(pageIndex);
            }
        }
        JSONObject query = new JSONObject()
                .put("parent_id", cid == null ? "" : cid)
                .put("limit", String.valueOf(pageSize))
                .put("page_token", pageToken)
                .put("thumbnail_size", "SIZE_SMALL")
                .put("with_audit", "true")
                .put("filters", new JSONObject()
                        .put("phase", new JSONObject().put("eq", "PHASE_TYPE_COMPLETE"))
                        .put("trashed", new JSONObject().put("eq", false))
                        .toString());
        JSONObject d = api("GET", "/drive/v1/files", query, null, true);
        String nextToken = d.optString("next_page_token", "");
        if (!TextUtils.isEmpty(nextToken)) {
            synchronized (pageTokensByCid) {
                List<String> chain = pageTokensByCid.get(key);
                if (chain != null && chain.size() == pageIndex + 1) chain.add(nextToken);
            }
        }
        List<CloudFile> files = new ArrayList<>();
        JSONArray arr = d.optJSONArray("files");
        if (arr == null) return files;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            CloudFile f = new CloudFile();
            boolean isDir = "drive#folder".equals(o.optString("kind"));
            f.setDir(isDir);
            f.setId(o.optString("id"));
            f.setName(o.optString("name"));
            f.setSize(o.optLong("size", 0));
            f.setSha1(o.optString("hash"));
            f.setParentCid(o.optString("parent_id", cid));
            // 缩略图（来源：pikpak-kotlin Models.kt FileStat.thumbnail_link）
            f.setThumbUrl(o.optString("thumbnail_link", ""));
            // pickCode 字段复用存 mime_type（播放时用 file_id 取直链）
            f.setPickCode(o.optString("mime_type"));
            files.add(f);
        }
        return files;
    }

    /** 搜索专用的 page_token 链（按关键词分链，与文件列表的目录链分开，互不串扰）。 */
    private final java.util.Map<String, List<String>> searchTokensByKw = new java.util.HashMap<>();

    /**
     * 全盘搜索（来源：pikpak-sdk/files.go Search，本类列表/离线同源 SDK）：
     * GET /drive/v1/files，不带 parent_id（全盘），filters＝{"name":{"prefix":关键词},"trashed":{"eq":false}}。
     * 注意这是官方口径的「名字前缀」匹配：关键词须是文件名的开头，搜中间词搜不到，与 115／夸克的包含搜索不同。
     * 分页走 page_token 链（offset/limit 兑页号，口径同 listDir，链未起/已翻完返回空表不冒充第一页）。
     * 响应字段未经真机对死，条数打调试日志（tag pansrc），真机一跑即可对字段。
     */
    @Override
    public List<CloudFile> searchFiles(String keyword, int offset, int limit) throws Exception {
        int pageSize = limit > 0 ? limit : 100;
        String kw = keyword == null ? "" : keyword.trim();
        int pageIndex = Math.max(0, offset) / pageSize;
        String pageToken;
        synchronized (searchTokensByKw) {
            if (pageIndex == 0) {
                List<String> chain = new ArrayList<>();
                chain.add("");
                searchTokensByKw.put(kw, chain);
                pageToken = "";
            } else {
                List<String> chain = searchTokensByKw.get(kw);
                if (chain == null || pageIndex >= chain.size()) return new ArrayList<>();
                pageToken = chain.get(pageIndex);
            }
        }
        JSONObject query = new JSONObject()
                .put("limit", String.valueOf(pageSize))
                .put("page_token", pageToken)
                .put("thumbnail_size", "SIZE_SMALL")
                .put("with_audit", "true")
                .put("filters", new JSONObject()
                        .put("name", new JSONObject().put("prefix", kw))
                        .put("trashed", new JSONObject().put("eq", false))
                        .toString());
        JSONObject d = api("GET", "/drive/v1/files", query, null, true);
        String nextToken = d.optString("next_page_token", "");
        if (!TextUtils.isEmpty(nextToken)) {
            synchronized (searchTokensByKw) {
                List<String> chain = searchTokensByKw.get(kw);
                if (chain != null && chain.size() == pageIndex + 1) chain.add(nextToken);
            }
        }
        List<CloudFile> files = new ArrayList<>();
        JSONArray arr = d.optJSONArray("files");
        try {
            com.github.catvod.crawler.SpiderDebug.log("pansrc",
                    "pikpak kw=%s got=%s next=%s",
                    kw, arr == null ? "null" : String.valueOf(arr.length()),
                    TextUtils.isEmpty(nextToken) ? "0" : "1");
        } catch (Throwable ignored) {
        }
        if (arr == null) return files;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            CloudFile f = new CloudFile();
            boolean isDir = "drive#folder".equals(o.optString("kind"));
            f.setDir(isDir);
            f.setId(o.optString("id"));
            f.setName(o.optString("name"));
            f.setSize(o.optLong("size", 0));
            f.setSha1(o.optString("hash"));
            f.setParentCid(o.optString("parent_id", ""));
            f.setThumbUrl(o.optString("thumbnail_link", ""));
            f.setPickCode(o.optString("mime_type"));
            files.add(f);
        }
        return files;
    }

    @Override
    public int getTotal(String cid) throws Exception {
        return -1;  // PikPak 分页用 page_token，不返回总数
    }

    /**
     * 删除（进回收站）（来源：pikpak-sdk/files.go Trash → batchTrash）
     * POST /drive/v1/files:batchTrash {ids: []}
     */
    @Override
    public boolean delete(List<String> ids, boolean isDir) throws Exception {
        if (ids == null || ids.isEmpty()) return true;
        JSONObject body = new JSONObject().put("ids", new JSONArray(ids));
        api("POST", "/drive/v1/files:batchTrash", null, body, true);
        return true;
    }

    /**
     * 离线下载（来源：pikpak-sdk/offline.go Create）
     * POST /drive/v1/files
     * Body: {kind: "drive#file", name, upload_type: "UPLOAD_TYPE_URL",
     *        url: {url}, parent_id, folder_type: "DOWNLOAD"}
     */
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
        JSONObject body = new JSONObject()
                .put("kind", "drive#file")
                .put("name", "")
                .put("upload_type", "UPLOAD_TYPE_URL")
                .put("url", new JSONObject().put("url", url))
                .put("parent_id", cid == null ? "" : cid);
        if (TextUtils.isEmpty(cid)) {
            body.put("folder_type", "DOWNLOAD");
        }
        JSONObject d = api("POST", "/drive/v1/files", null, body, true);
        JSONObject task = d.optJSONObject("task");
        String taskId = task != null ? task.optString("id") : d.optString("task_id");
        if (TextUtils.isEmpty(taskId)) {
            // 有些返回直接是文件（秒传），也算成功；file 缺失时 optJSONObject 回 null，
            // 直取 optString 必 NPE，先判空再取 id，缺失走下方既有失败口径。
            JSONObject file = d.optJSONObject("file");
            if (file != null && !TextUtils.isEmpty(file.optString("id"))) return true;
            throw new Exception("离线任务创建失败（可能是接口变更）");
        }
        return true;
    }

    /**
     * 离线任务列表的 page_token 链状态（与文件列表 listDir 同口径）：
     * taskList(1) 重置链、从空 token 起；后续页用上一页响应存下的 token 续拉；
     * 响应里没有 next_page_token ＝已翻完，置空。链未起/已翻完时再调后续页返回空表，不回第一页冒充。
     */
    private String taskNextPageToken = "";

    /**
     * 离线任务列表（来源：pikpak-sdk/offline.go List）
     * GET /drive/v1/tasks?type=offline&limit=100&page_token=&filters=
     */
    @Override
    public List<CloudTask> taskList(int page) throws Exception {
        String pageToken;
        if (page <= 1) {
            taskNextPageToken = "";
            pageToken = "";
        } else {
            pageToken = taskNextPageToken;
            if (TextUtils.isEmpty(pageToken)) return new ArrayList<>();
        }
        JSONObject query = new JSONObject()
                .put("type", "offline")
                .put("limit", "100")
                .put("page_token", pageToken)
                .put("thumbnail_size", "SIZE_SMALL")
                .put("with", "reference_resource")
                .put("filters", new JSONObject()
                        .put("phase", new JSONObject().put("in",
                                "PHASE_TYPE_RUNNING,PHASE_TYPE_ERROR,PHASE_TYPE_PENDING,PHASE_TYPE_COMPLETE"))
                        .toString());
        JSONObject d = api("GET", "/drive/v1/tasks", query, null, true);
        // 存下一页 token 供续页用；响应没有＝已翻完，置空
        taskNextPageToken = d.optString("next_page_token", "");
        List<CloudTask> tasks = new ArrayList<>();
        JSONArray arr = d.optJSONArray("tasks");
        if (arr == null) return tasks;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            CloudTask t = new CloudTask();
            t.setHash(o.optString("id"));  // 用 task_id 当 hash
            t.setName(o.optString("name", o.optString("file_name")));
            t.setSize(o.optLong("file_size", 0));
            String phase = o.optString("phase");
            t.setStatus("PHASE_TYPE_COMPLETE".equals(phase) ? 2 : "PHASE_TYPE_ERROR".equals(phase) ? 3 : 1);
            t.setPercent(o.optInt("progress", 0));
            t.setStatusText(o.optString("message", phase));
            tasks.add(t);
        }
        return tasks;
    }

    /**
     * 删除离线任务（来源：pikpak-sdk/offline.go Delete）
     * DELETE /drive/v1/tasks?task_ids=&delete_files=false
     */
    @Override
    public boolean taskDelete(List<String> hashes) throws Exception {
        if (hashes == null || hashes.isEmpty()) return true;
        for (String taskId : hashes) {
            JSONObject query = new JSONObject()
                    .put("task_ids", taskId)
                    .put("delete_files", "false");
            api("DELETE", "/drive/v1/tasks", query, null, true);
        }
        return true;
    }

    /**
     * 取播放地址（来源：pikpak-sdk/files.go Get → web_content_link / medias）
     * GET /drive/v1/files/{id}?_magic=2021&usage=FETCH
     * 优先 medias 里 is_origin 的 URL，否则 web_content_link
     */
    @Override
    public String getPlayUrl(CloudFile file) throws Exception {
        if (file == null || TextUtils.isEmpty(file.getId())) {
            throw new Exception("文件 id 为空");
        }
        JSONObject query = new JSONObject()
                .put("_magic", "2021")
                .put("usage", "FETCH")
                .put("thumbnail_size", "SIZE_SMALL");
        JSONObject d = api("GET", "/drive/v1/files/" + file.getId(), query, null, true);
        // 优先原画媒体流
        JSONArray medias = d.optJSONArray("medias");
        if (medias != null) {
            String fallback = "";
            for (int i = 0; i < medias.length(); i++) {
                JSONObject m = medias.optJSONObject(i);
                if (m == null) continue;
                String url = m.optString("link", m.optString("url"));
                if (TextUtils.isEmpty(url)) continue;
                if (m.optBoolean("is_origin", false)) return url;
                if (TextUtils.isEmpty(fallback)) fallback = url;
            }
            if (!TextUtils.isEmpty(fallback)) return fallback;
        }
        String link = d.optString("web_content_link");
        if (!TextUtils.isEmpty(link)) return link;
        throw new Exception("取播放地址失败（文件可能未转码完成）");
    }

    // ---------- 扩展：供 UI 调用 ----------

    /** 新建文件夹（来源：pikpak-sdk/files.go CreateFolder） */
    public CloudFile createFolder(String parentId, String name) throws Exception {
        JSONObject body = new JSONObject()
                .put("kind", "drive#folder")
                .put("parent_id", parentId == null ? "" : parentId)
                .put("name", name);
        JSONObject d = api("POST", "/drive/v1/files", null, body, true);
        JSONObject f = d.optJSONObject("file");
        if (f == null || TextUtils.isEmpty(f.optString("id"))) {
            throw new Exception("新建文件夹失败（可能是接口变更）");
        }
        CloudFile cf = new CloudFile();
        cf.setDir(true);
        cf.setId(f.optString("id"));
        cf.setName(f.optString("name"));
        cf.setParentCid(f.optString("parent_id"));
        return cf;
    }

    /** 重命名（来源：pikpak-sdk/files.go Rename → PATCH） */
    public boolean rename(String fileId, String name) throws Exception {
        JSONObject body = new JSONObject().put("name", name);
        api("PATCH", "/drive/v1/files/" + fileId, null, body, true);
        return true;
    }

    /** 查询离线任务状态（来源：pikpak-sdk/offline.go Get） */
    public CloudTask getOfflineTask(String taskId) throws Exception {
        JSONObject d = api("GET", "/drive/v1/tasks/" + taskId, null, null, true);
        CloudTask t = new CloudTask();
        t.setHash(d.optString("id"));
        t.setName(d.optString("name", d.optString("file_name")));
        t.setSize(d.optLong("file_size", 0));
        String phase = d.optString("phase");
        t.setStatus("PHASE_TYPE_COMPLETE".equals(phase) ? 2 : "PHASE_TYPE_ERROR".equals(phase) ? 3 : 1);
        t.setPercent(d.optInt("progress", 0));
        t.setStatusText(d.optString("message", phase));
        return t;
    }

    /** 离线任务完成后取 file_id（轮询用） */
    public String getOfflineFileId(String taskId) throws Exception {
        JSONObject d = api("GET", "/drive/v1/tasks/" + taskId, null, null, true);
        // 完成后 file_id 在 file_id 或 reference_resource 里
        String fid = d.optString("file_id");
        if (TextUtils.isEmpty(fid)) {
            JSONObject ref = d.optJSONObject("reference_resource");
            if (ref != null) fid = ref.optString("id");
        }
        return fid;
    }

    /**
     * 转存 PikPak 分享链接（来源：pikpak-kotlin ShareEndpoint.kt，MIT，2026-09-24 经 piko 实测验证）
     * 流程：解析 shareId → GET /drive/v1/share 拿 pass_code_token → POST /drive/v1/share/restore → 轮询任务
     */
    @Override
    public boolean saveShare(String shareUrl, String password, String toCid) throws Exception {
        String url = shareUrl == null ? "" : shareUrl.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^https?://(?:www\\.)?mypikpak\\.com/s/([A-Za-z0-9_-]+)")
                .matcher(url);
        if (!m.find()) throw new Exception("不是有效的 PikPak 分享链接（应为 mypikpak.com/s/…）");
        String shareId = m.group(1);
        String passCode = password == null ? "" : password.trim();
        // 1) 打开分享，拿 pass_code_token（分享读写接口需 X-Client-Id 头，已在 api() 统一加）
        JSONObject q = new JSONObject()
                .put("share_id", shareId)
                .put("limit", "100")
                .put("thumbnail_size", "SIZE_SMALL");
        if (!passCode.isEmpty()) q.put("pass_code", passCode);
        JSONObject info = api("GET", "/drive/v1/share", q, null, true);
        String status = info.optString("share_status");
        if (!"OK".equals(status)) {
            if ("PASS_CODE_EMPTY".equals(status)) throw new Exception("该分享需要提取码");
            if ("PASS_CODE_ERROR".equals(status)) throw new Exception("提取码错误");
            throw new Exception("分享不可用：" + status + " " + info.optString("share_status_text"));
        }
        String token = info.optString("pass_code_token");
        JSONArray files = info.optJSONArray("files");
        List<String> fileIds = new ArrayList<>();
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.optJSONObject(i);
                if (f != null && !TextUtils.isEmpty(f.optString("id"))) fileIds.add(f.optString("id"));
            }
        }
        if (fileIds.isEmpty()) throw new Exception("分享为空或已失效");
        // 2) restore 转存到 toCid（空=根目录）
        JSONObject body = new JSONObject()
                .put("share_id", shareId)
                .put("pass_code_token", token)
                .put("file_ids", new JSONArray(fileIds))
                .put("ancestor_ids", new JSONArray())
                .put("parent_id", toCid == null ? "" : toCid)
                .put("specify_parent_id", true);
        JSONObject r = api("POST", "/drive/v1/share/restore", null, body, true);
        String rs = r.optString("share_status");
        if (!rs.isEmpty() && !"OK".equals(rs)) {
            throw new Exception("转存失败：" + r.optString("share_status_text", rs));
        }
        String taskId = r.optString("restore_task_id");
        if (TextUtils.isEmpty(taskId)) return true;
        // 3) 轮询任务（restore 任务秒级完成）
        for (int i = 0; i < 30; i++) {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            CloudTask t = getOfflineTask(taskId);
            if (t.getStatus() == 2) return true;
            if (t.getStatus() == 3) throw new Exception("转存失败：" + t.getStatusText());
        }
        throw new Exception("转存超时，请稍后在网盘中查看");
    }
}
