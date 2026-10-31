package com.fongmi.android.tv.hongguo.api;

import com.fongmi.android.tv.hongguo.sign.HgNativeSign;
import com.fongmi.android.tv.hongguo.sign.HgSign;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 红果 passport 短信登录客户端（原生短信链，非网页登录，全链无 WebView）。
 *
 * <p>证据链：组参/xor/宿主照 passport-login-dig.md（宿主 security.snssdk.com、
 * 端点 /passport/mobile/send_code/v1/ 与 /passport/mobile/sms_login/、
 * xz0/g.H / xz0/d.H 组参、StringUtils.encryptWithXor 本体）；传输门照
 * login-route-dig-20261028.md 的探针实证（login-probe-20261028/ 六份回包
 * 原文）：旧客户端当年 403 的死因是两道门叠加——①设备号是 freshQuery
 * 现编的随机假号（未在服务端注册，网关直接 403 空体）；②无签名头
 * （过注册门后回 error_code=14「请使用SDK接入」）。本客户端两道门都走
 * 现行栈：设备号走 HgDevice 注册档案（经 HgApiClient.ensureDevice 的
 * 同一注册门，不许再用随机号）、请求走 HgSign.signPost 签名（与
 * HgApiClient.postSigned 同路）后经 HgNativeSign.resign 原生优先重签六头
 * （原生失败自动回退仿算头），探针以仿算形状两道门全过、走到手机号
 * 校验（1003）与验证码校验（1203）两关。</p>
 *
 * <p>回包判定按探针真回包形状（与 dcfa2bea 原版的区别，证据在探针存档）：
 * 真回包的 error_code / description 在 data 对象内、顶层 message 为
 * "error"/"success"；原版只读顶层 error_code 会把真错误误判成功，
 * 本版先查 data.error_code、再查顶层（防御）。错误文案一律翻成人话
 * （见 humanError），不把错误码、英文与 desc_url 糊到用户脸上。</p>
 *
 * <p>未定（如实）：①真号发码是否触发图形验证码/shark 二次验证由
 * 服务端风险决定，假号验不到，触发时回包 data.captcha 非空，本版
 * 先给人话提示、票据路待续挖；②登录成功回包→会话落盘未验（无真
 * 码）：本客户端只组 HgAccountStore.Session 回调（data.session_key
 * 组 sessionid，Set-Cookie 优先；账号信息与 x-tt-token / csrf 按
 * metasec-integration-design-20261031.md §5 定版摘取），落盘由调用
 * 方走 HgAccountStore.save。</p>
 *
 * <p>2026-10-31 登录对齐（照 hongguo-assistant 的 account.rs 链，
 * 审表补记口径）：sms_login 表单改明文 mobile/code＋type=0＋
 * unbind_exist（HgRequests.smsLoginForm）；发码回包 Set-Cookie 与
 * x-tt-passport-csrf-token 抓存、验码回带；发请求前预热原生签名
 * 并留一行诊断日志（本发采纳原生还是仿算）；失败回包 http/错误码
 * 留痕。发码表单维持 SDK 形（今晨真机实证码真到，见 HgRequests
 * .sendCodeForm 注）。</p>
 */
public final class HgPassportClient {

    private static final MediaType FORM = MediaType.parse("application/x-www-form-urlencoded; charset=utf-8");

    /** 只借它的设备注册门（ensureDevice），注册身份与全 App 红果链同一份 HgDevice 档案。 */
    private final HgApiClient deviceGate = new HgApiClient();

    /**
     * 发码回包的会话件（登录对齐，照 hongguo-assistant 链恢复——
     * 2026-10-29 旧版有此回带、1.4.67 复活重写时整件丢失，审表嫌疑
     * 二）：发码响应 Set-Cookie 的名值对串与 x-tt-passport-csrf-token
     * 抓存于此，验码请求原样回带（Cookie 头＋csrf 头）。
     */
    private volatile String sendCodeCookie;
    private volatile String sendCodeCsrf;

    public HgPassportClient() {
    }

    /**
     * 发送短信验证码。成功回调重试倒计时秒数（回包 data.retry_time，
     * 服务端优先、缺失/异常缺省 60，定版见
     * metasec-integration-design-20261031.md §5）；失败回调人话原因
     * （不吞错、不贴原始码）。
     */
    public void sendSmsCode(String mobile, HgCallback<Integer> cb) {
        postForm("/passport/mobile/send_code/v1/", HgRequests.sendCodeForm(mobile), null,
                (text, setCookie, ttToken, csrf) -> {
                    // 会话件先抓存（成败都存：验码要回带的是发码这一
                    // 发建立的会话上下文，照 assistant 会话捕获口径）
                    if (setCookie != null && !setCookie.isEmpty()) sendCodeCookie = setCookie;
                    if (csrf != null && !csrf.isEmpty()) sendCodeCsrf = csrf;
                    String err = passportError(text);
                    if (err == null) cb.onSuccess(retrySeconds(text));
                    else cb.onError(-2, err);
                }, cb::onError);
    }

    /**
     * 短信验证码登录。成功回调组好的 HgAccountStore.Session（Cookie 走
     * loginCookie 旧口径；userId/name/avatar 取回包 data.user 的
     * user_id / screen_name（优先，否则 user_name）/ avatar_url；
     * ttToken/csrf 取响应头 x-tt-token / x-tt-passport-csrf-token）。
     * 关键件（Cookie、userId、ttToken）缺失按旧口径判失败走人话错误；
     * csrf 缺失存空串（捕获点未定根，见 sessionFrom 注）。客户端只回
     * Session、不直接落盘（落盘由调用方 save）。
     */
    public void smsLogin(String mobile, String code, HgCallback<HgAccountStore.Session> cb) {
        Map<String, String> extra = new HashMap<>();
        if (sendCodeCookie != null && !sendCodeCookie.isEmpty()) {
            extra.put("Cookie", sendCodeCookie);
        }
        if (sendCodeCsrf != null && !sendCodeCsrf.isEmpty()) {
            extra.put("x-tt-passport-csrf-token", sendCodeCsrf);
        }
        postForm("/passport/mobile/sms_login/", HgRequests.smsLoginForm(mobile, code), extra,
                (text, setCookie, ttToken, csrf) -> {
            String cookie = loginCookie(text, setCookie);
            HgAccountStore.Session session = cookie == null ? null : sessionFrom(text, cookie, ttToken, csrf);
            if (session != null) cb.onSuccess(session);
            else {
                String err = passportError(text);
                if (err == null) {
                    // 失败分类留痕（件三追加）：回包是成功形状（业务
                    // 码 0）却组不出会话——缺 Cookie/session_key、
                    // user_id、x-tt-token 之一，与业务码拒是两类错，
                    // 单独记一行，下次真机一发即可分段。
                    android.util.Log.w("HgPassport", "path=/passport/mobile/sms_login/"
                            + " shape=success-but-session-incomplete cookie="
                            + (cookie != null) + " ttToken="
                            + (ttToken != null && !ttToken.isEmpty()));
                }
                cb.onError(-2, err != null ? err : "登录没成功，请稍后再试");
            }
        }, cb::onError);
    }

    /** 发码回包 → 重试倒计时秒数：data.retry_time 优先，缺失/异常缺省 60。 */
    private static int retrySeconds(String text) {
        try {
            JsonObject root = parseRoot(text);
            JsonObject data = root != null && root.has("data") && root.get("data").isJsonObject()
                    ? root.getAsJsonObject("data") : null;
            JsonElement e = data == null ? null : data.get("retry_time");
            int v = 0;
            if (e != null && e.isJsonPrimitive()) {
                v = e.getAsJsonPrimitive().isNumber() ? e.getAsInt()
                        : (int) Long.parseLong(e.getAsString().trim());
            }
            return v > 0 ? v : 60;
        } catch (Exception e) {
            return 60;
        }
    }

    /** 登录回包＋响应头 → Session；关键件缺失返 null（由调用处按失败走人话）。 */
    private static HgAccountStore.Session sessionFrom(String text, String cookie, String ttToken, String csrf) {
        try {
            JsonObject root = parseRoot(text);
            JsonObject data = root != null && root.has("data") && root.get("data").isJsonObject()
                    ? root.getAsJsonObject("data") : null;
            JsonObject user = data != null && data.has("user") && data.get("user").isJsonObject()
                    ? data.getAsJsonObject("user") : null;
            String userId = user == null ? null : str(user, "user_id");
            if (userId == null || userId.isEmpty()) return null;
            if (ttToken == null || ttToken.isEmpty()) return null;
            // csrf 不作成功门：本批无消费点，且其捕获点（登录回包头/发码
            // 回包/cookie）未定根——缺了存空串，不拿它把真登录判失败。
            HgAccountStore.Session s = new HgAccountStore.Session();
            s.userId = userId;
            String name = str(user, "screen_name");
            if (name == null || name.isEmpty()) name = str(user, "user_name");
            s.name = name;
            s.avatar = str(user, "avatar_url");
            s.cookie = cookie;
            s.ttToken = ttToken;
            s.csrf = csrf == null ? "" : csrf;
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * passport 回包错误码：0＝成功形状；正数＝服务端 error_code（先查
     * data.error_code——探针真回包所在层，再查顶层作防御）；-1＝回包不是
     * 可解析的 JSON 对象；-2＝顶层 message=error 但无码可取。
     * 公开仅为离线单测可达。
     */
    public static int passportErrorCode(String text) {
        JsonObject root = parseRoot(text);
        if (root == null) return -1;
        JsonObject data = root.has("data") && root.get("data").isJsonObject()
                ? root.getAsJsonObject("data") : null;
        int code = errorCodeIn(data);
        if (code != 0) return code;
        code = errorCodeIn(root);
        if (code != 0) return code;
        String msg = str(root, "message");
        if ("error".equals(msg)) return -2;
        return 0;
    }

    /**
     * passport 回包判错＋人话化：成功返回 null；失败返回可直接上屏的人话
     * （已知码按码翻，余者取服务端中文 description，再兜底通用句）。
     * 公开仅为离线单测可达。
     */
    public static String passportError(String text) {
        int code = passportErrorCode(text);
        if (code == 0) return null;
        JsonObject root = parseRoot(text);
        if (root == null) return "服务暂时不可用，请稍后再试";
        JsonObject data = root.has("data") && root.get("data").isJsonObject()
                ? root.getAsJsonObject("data") : null;
        // 风控要图形验证码时 data.captcha 非空：SDK 票据路未接，先给人话，不贴字段。
        String captcha = str(data, "captcha");
        if (captcha != null && !captcha.isEmpty()) return "需要先完成安全验证，请稍后再试";
        switch (code) {
            case 7:
                return "操作太频繁了，请稍等一会儿再试";
            case 14:
                return "登录通道暂时异常，请稍后再试";
            case 1003:
                return "手机号好像不对，请检查一下再填";
            case 1105:
                return "验证码不对，请重新输入";
            case 1203:
                return "验证码不对或者已经过期了，请重新获取一个";
            default:
                break;
        }
        String desc = str(data, "description");
        if (desc == null) desc = str(root, "description");
        if (desc == null) {
            String msg = str(root, "message");
            if (msg != null && !"error".equals(msg) && !"success".equals(msg)) desc = msg;
        }
        if (desc != null && !desc.isEmpty() && desc.length() <= 60
                && !desc.contains("http") && !desc.contains("SDK")) {
            return desc;
        }
        return "操作没成功，请稍后再试";
    }

    /**
     * 登录回包 → 托管 Cookie：优先服务端 Set-Cookie 里真发的会话键
     * （传输层已把多条 Set-Cookie 摘成 名=值 对串传入）；没有则用
     * data.session_key 组 sessionid（passport 会话键命名，与
     * HongguoLoginProvider 的格式判定对齐）。失败回包返回 null。
     */
    public static String loginCookie(String text, String setCookie) {
        if (passportErrorCode(text) != 0) return null;
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonObject data = root.has("data") && root.get("data").isJsonObject()
                    ? root.getAsJsonObject("data") : null;
            String sessionKey = data == null ? null : str(data, "session_key");
            StringBuilder sb = new StringBuilder();
            boolean hasSession = false;
            if (setCookie != null && !setCookie.isEmpty()) {
                sb.append(setCookie);
                hasSession = setCookie.contains("sessionid=") || setCookie.contains("sessionid_ss=");
            }
            if (!hasSession && sessionKey != null && !sessionKey.isEmpty()) {
                if (sb.length() > 0) sb.append("; ");
                sb.append("sessionid=").append(sessionKey);
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 传输层（注册门＋签名，与 HgApiClient.postSigned 同路） ----------

    private interface BodyHandler {
        void handle(String text, String setCookie, String ttToken, String csrf);
    }

    private interface FailHandler {
        void fail(int code, String msg);
    }

    private void postForm(String path, Map<String, String> form, Map<String, String> extraHeaders,
                          BodyHandler ok, FailHandler fail) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        final byte[] formBytes = body.toString().getBytes(StandardCharsets.UTF_8);
        deviceGate.ensureDevice(new HgCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean registered) {
                List<String[]> pairs = new ArrayList<>(HgDevice.deviceQueryPairs());
                pairs.add(new String[]{"passport-sdk-version", "5051452"});
                pairs.add(new String[]{"account_sdk_source", "app"});
                long ticket = System.currentTimeMillis();
                HgSign.Signed signed = HgSign.signPost(path, pairs, ticket,
                        HgDevice.profile().deviceId, HgDevice.VERSION_NAME, formBytes, null);
                Map<String, String> headers = new HashMap<>();
                headers.put("user-agent", HgDevice.userAgent());
                headers.putAll(signed.headers);
                // Content-Type 由 RequestBody 承载（表单口径），签名头里的 JSON 值摘掉（与 postSigned 同处理）。
                headers.remove("Content-Type");
                headers.put("x-ss-dp", "8662");
                if (extraHeaders != null) headers.putAll(extraHeaders);
                String url = HgRequests.PASSPORT_HOST + path + "?" + signed.query;
                // 发请求前预热原生签名（审表嫌疑一：此前 init 全靠
                // resign 懒触发、失败置 DEAD 后无观测点）；重签后留
                // 一行诊断日志，把本发实际采纳的签名集记下来。
                boolean nativeReady = HgNativeSign.warmUp();
                headers = HgNativeSign.resign(url, headers, formBytes);
                android.util.Log.i("HgPassport", "path=" + path
                        + " nativeReady=" + nativeReady
                        + " signedBy=" + (HgNativeSign.lastResignWasNative() ? "native" : "simulated"));
                RequestBody rb = RequestBody.create(FORM, body.toString());
                Call call = OkHttp.newCall(url, headers, rb);
                if (call == null) {
                    fail.fail(-1, "服务暂时不可用，请稍后再试");
                    return;
                }
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call c, IOException e) {
                        // 失败分类留痕（件三追加）：传输层失败与业务
                        // 码拒/HTTP 拒分开记，下次真机一发即可分段。
                        android.util.Log.w("HgPassport", "path=" + path
                                + " kind=transport error=" + e.getClass().getSimpleName());
                        fail.fail(-1, "网络不太顺，请检查网络后再试");
                    }

                    @Override
                    public void onResponse(Call c, Response response) {
                        try (Response r = response) {
                            ResponseBody rb2 = r.body();
                            String text = rb2 == null ? "" : rb2.string();
                            if (!r.isSuccessful() || text.isEmpty()) {
                                // 失败留痕（复活批丢件之一，审表六节附带
                                // 发现）：连失败码都不留，下次真机失败就
                                // 无从定段——http 码＋回包错误码各记一行。
                                android.util.Log.w("HgPassport", "path=" + path
                                        + " http=" + r.code() + " bodyEmpty=" + text.isEmpty());
                                fail.fail(r.code(), "服务暂时不可用，请稍后再试");
                            } else {
                                int ec = passportErrorCode(text);
                                if (ec != 0) {
                                    // 失败分类留痕（件三追加）：业务码＋
                                    // message/description 原文一并记——
                                    // 上屏文案对未映射码是兜底句、反推
                                    // 不出码值，真机定段靠这一行。
                                    JsonObject root = parseRoot(text);
                                    JsonObject data = root != null && root.has("data")
                                            && root.get("data").isJsonObject()
                                            ? root.getAsJsonObject("data") : null;
                                    String dsc = str(data, "description");
                                    if (dsc == null) dsc = str(root, "description");
                                    android.util.Log.w("HgPassport", "path=" + path
                                            + " error_code=" + ec
                                            + " message=" + str(root, "message")
                                            + " description=" + dsc);
                                }
                                ok.handle(text, cookiePairs(r),
                                        r.header("x-tt-token"), r.header("x-tt-passport-csrf-token"));
                            }
                        } catch (Exception e) {
                            fail.fail(-1, "网络不太顺，请检查网络后再试");
                        }
                    }
                });
            }

            @Override
            public void onError(int httpCode, String message) {
                fail.fail(httpCode, "服务暂时不可用，请稍后再试");
            }
        });
    }

    /** 多条 Set-Cookie 头摘成 名=值 对串（去掉 path/expires 等属性），无则 null。 */
    private static String cookiePairs(Response r) {
        List<String> all = r.headers("Set-Cookie");
        if (all == null || all.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (String h : all) {
            if (h == null) continue;
            int semi = h.indexOf(';');
            String pair = (semi >= 0 ? h.substring(0, semi) : h).trim();
            if (pair.isEmpty()) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(pair);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static JsonObject parseRoot(String text) {
        try {
            JsonElement el = JsonParser.parseString(text);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static int errorCodeIn(JsonObject o) {
        if (o == null) return 0;
        JsonElement e = o.get("error_code");
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            return e.getAsInt();
        }
        return 0;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static String enc(String s) {
        if (s == null) return "";
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }
}
