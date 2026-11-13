package com.fongmi.android.tv.api.hk;

import com.google.gson.Gson;
import com.orhanobut.logger.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

/**
 * HkHttpHelper — 8.83 {@code com.example.hikerview.service.parser.HttpHelper} 网络底座移植。
 *
 * <p>本文件仅依赖 okhttp3 / GSON / 纯 Java（另见 {@link HkHttp}），不引用 OkGo / joor / fastjson /
 * commons-lang3。分支逻辑与 8.83 smali 逐分支对照（见 {@code jsengine-d1/frag_httpphelper_notes.md}
 * 的 D1-I2 rewrite notes）：</p>
 * <ul>
 * <li>{@code fetch0(String, Map, HeadersInterceptor, RuleFetchDelegate)}（4 参，smali:662-688）</li>
 * <li>{@code fetch0(String, Map, HeadersInterceptor, RuleFetchDelegate, OkHttpClient)}（5 参，smali:689-2789）</li>
 * <li>{@code generatePostOps(Map)}（smali:2790-2876）</li>
 * <li>{@code FetchResponse / HeadersInterceptor / RuleFetchDelegate}（内部类 smali 全量）</li>
 * <li>匿名回调 {@code HttpHelper$1}（smali 全量）→ {@link FetchCallback}</li>
 * <li>{@code ByteHexConvert / CharsetStringConvert}（smali 全量）→ 私有静态内部类（去 OkGo Converter 接口）</li>
 * <li>{@code HttpUtilKt.toRequestBody1}（smali 全量）→ {@link #toRequestBody1(String, String)} 纯 Java 移植</li>
 * <li>被引用的小工具（isEmpty/isNotEmpty/tryGet/tryGetBool/decodeConflictStr/bytesToHexString/
 * getContentLength/getTagFromThread/buildParamStr/arrayToString）→ 私有静态方法原样移植</li>
 * </ul>
 *
 * <p>HTTP 客户端：{@link HkHttp#client()}（8.83 buildOkHttpClient 移植版，CookieJar / 拦截器；DNS 系统默认）。
 * 需要 per-request 禁止重定向或自定义超时时，用 {@code HkHttp.client().newBuilder()} 派生
 * （见 {@link #resolveClient(String, OkHttpClient, boolean, int)}）。</p>
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class HkHttpHelper {

    /** GSON 单例：替代 8.83 的 fastjson {@code JSON.toJSONString}。 */
    private static final Gson GSON = new Gson();
    /** 8.83 OkGo {@code HttpParams.MEDIA_TYPE_JSON}（"application/json;charset=utf-8"，upJson 用）。 */
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json;charset=utf-8");

    //======================================================================
    // 8.83 内部类型（HttpHelper$FetchResponse / $HeadersInterceptor / $RuleFetchDelegate）
    //======================================================================

    /** 8.83 HttpHelper$FetchResponse 原样移植（smali: 全文件）。 */
    public static class FetchResponse {
        public String error;
        public String fetchResult;
        public Map<String, ?> headers;
        public InputStream inputStream;
        public String realUrl;
        public int statusCode;

        public FetchResponse() {
            this.headers = null;
            this.statusCode = 0;
            this.error = null;
        }
    }

    /** 8.83 HttpHelper$HeadersInterceptor 原样移植：boolean intercept(Map<String, List<String>>)。 */
    public interface HeadersInterceptor {
        boolean intercept(Map<String, List<String>> headers);
    }

    /** 8.83 HttpHelper$RuleFetchDelegate 原样移植：Object fetch(String url, boolean toHex, boolean inputStream)。 */
    public interface RuleFetchDelegate {
        Object fetch(String url, boolean toHex, boolean inputStream);
    }

    //======================================================================
    // 私有静态小工具（8.83 HttpHelper 私有/公有小方法原样移植，仅 fetch0 内部使用）
    //======================================================================

    /** 8.83 HttpHelper.threadMap（getTagFromThread/addTagForThread 共用）。 */
    private static final ThreadLocal<Map<String, Object>> threadMap = new ThreadLocal<>();

    /** 8.83 HttpHelper.getTagFromThread 原样移植（smali:3085-3114）。 */
    public static Object getTagFromThread() {
        Map<String, Object> map = threadMap.get();
        if (map == null) return null;
        return map.get("_tag");
    }

    /** 8.83 HttpHelper.addTagForThread 原样移植（smali:449-…）：给当前线程打 tag，后续请求自动带上。 */
    public static void addTagForThread(Object tag) {
        Map<String, Object> map = threadMap.get();
        if (map == null) {
            map = new HashMap<>();
            threadMap.set(map);
        }
        map.put("_tag", tag);
    }

    /**
     * 8.83 HttpHelper.cancelByTag 原样移植（smali:524-…）。
     * 8.83 版取消 OkGo 上同 tag 的请求；okhttp3 直写版改为取消 Dispatcher 上同 tag 的排队/执行中请求。
     * HkHttp.client() 的派生 client 共用同一 Dispatcher，一并覆盖；imageClient 单独处理。
     */
    public static void cancelByTag(Object tag) {
        if (tag == null) return;
        try {
            cancelTagged(HkHttp.client(), tag);
        } catch (Throwable ignored) {
        }
        try {
            cancelTagged(HkHttp.imageClient(), tag);
        } catch (Throwable ignored) {
        }
    }

    private static void cancelTagged(OkHttpClient client, Object tag) {
        if (client == null) return;
        okhttp3.Dispatcher dispatcher = client.dispatcher();
        for (Call call : dispatcher.queuedCalls()) {
            if (tag.equals(call.request().tag(Object.class)) || tag.equals(call.request().tag())) call.cancel();
        }
        for (Call call : dispatcher.runningCalls()) {
            if (tag.equals(call.request().tag(Object.class)) || tag.equals(call.request().tag())) call.cancel();
        }
    }

    /** 8.83 HttpHelper.isEmpty(CharSequence) 原样移植（smali:3114-3141）。 */
    private static boolean isEmpty(CharSequence s) {
        return s == null || s.length() == 0;
    }

    /** 8.83 HttpHelper.isEmpty(Collection) 原样移植（smali:3141-3175）。 */
    private static boolean isEmpty(java.util.Collection<?> c) {
        return c == null || c.isEmpty();
    }

    /** 8.83 HttpHelper.isNotEmpty(CharSequence) 原样移植（smali:3175-3188）。 */
    private static boolean isNotEmpty(CharSequence s) {
        return !isEmpty(s);
    }

    /** 8.83 HttpHelper.isNotEmpty(Collection) 原样移植（smali:3188-3208）。 */
    private static boolean isNotEmpty(java.util.Collection<?> c) {
        return !isEmpty(c);
    }

    /** 8.83 HttpHelper.tryGet 原样移植（smali:3257-3295）。 */
    private static <T> T tryGet(Map<String, Object> map, String key, T def) {
        if (map.containsKey(key)) {
            try {
                return (T) map.get(key);
            } catch (Throwable ignored) {
            }
        }
        return def;
    }

    /** 扫查待裁 21：规则 JS 作者数据安全取值——非 Map 一律当无（原裸强转，
     * 畸形规则数据会 CCE 把整条请求打成静默失败；外层 catch 兜底不崩但功能丢）。 */
    private static Map asMap(Object o) {
        return o instanceof Map ? (Map) o : null;
    }

    /** 同上：非 String 一律当无。 */
    private static String asString(Object o) {
        return o instanceof String ? (String) o : null;
    }

    /** 8.83 HttpHelper.tryGetBool 移植（smali:3295-3308）。
     * 与原版的差异（体检批六）：原版对 tryGet 结果裸 check-cast Boolean，
     * 规则 JS 给字符串/数字等非 Boolean 值时 CCE 直接打死整条请求（调用点不在 try 内）；
     * 这里加判型，非 Boolean 一律按 def 回落，Boolean 正常路径与原版一字不变。 */
    private static boolean tryGetBool(Map<String, Object> map, String key, Boolean def) {
        Object raw = tryGet(map, key, def);
        Boolean v = raw instanceof Boolean ? (Boolean) raw : def;
        if (v == null) v = def;
        return v;
    }

    /** 8.83 HttpHelper.decodeConflictStr 原样移植（smali:558-607）。 */
    private static String decodeConflictStr(String s) {
        if (isEmpty(s)) return s;
        s = s.replace("？？", "?");
        s = s.replace("＆＆", "&");
        s = s.replace("；；", ";");
        s = s.replace("，，", ",");
        return s;
    }

    /** 8.83 HttpHelper.bytesToHexString 原样移植（smali:452-524）。 */
    private static String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        if (bytes == null || bytes.length <= 0) return null;
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() < 2) sb.append(0);
            sb.append(hex);
        }
        return sb.toString();
    }

    /** 8.83 HttpHelper.getContentLength(Headers) 原样移植（smali:2909-2950）。 */
    private static long getContentLength(Headers headers) {
        String v = headers.get("Content-Length");
        if (v != null && !v.isEmpty()) {
            try {
                return Long.parseLong(v);
            } catch (Throwable t) {
                Logger.e(t.toString());
            }
        }
        return 0;
    }

    /** 8.83 HttpHelper.arrayToString(String[], int, String) 原样移植（smali:177-198）。 */
    private static String arrayToString(String[] arr, int start, String sep) {
        int end = arr == null ? 0 : arr.length;
        return arrayToString(arr, start, end, sep);
    }

    /** 8.83 HttpHelper.arrayToString(String[], int, int, String) 原样移植（smali:100-177）。 */
    private static String arrayToString(String[] arr, int start, int end, String sep) {
        StringBuilder sb = new StringBuilder();
        if (arr == null || arr.length <= start) return "";
        if (arr.length <= 1) return arr[0];
        sb.append(arr[start]);
        start += 1;
        while (start < arr.length && start < end) {
            sb.append(sep);
            sb.append(arr[start]);
            start += 1;
        }
        return sb.toString();
    }

    /**
     * 8.83 HttpHelper.buildParamStr 原样移植（smali:347-452，仅 generatePostOps 使用）。
     * fastjson {@code JSONObject} → 纯 {@link Map}；{@code JSON.toJSONString} → {@link Gson#toJson}。
     */
    private static String buildParamStr(Map<String, Object> obj) {
        java.util.Set<String> keys = obj.keySet();
        String[] parts = new String[keys.size()];
        int i = 0;
        for (String k : keys) {
            Object v = obj.get(k);
            String vs = v instanceof String ? (String) v : GSON.toJson(v);
            parts[i++] = k + "=" + vs.replace("&", "＆＆");
        }
        return arrayToString(parts, 0, "&");
    }

    /**
     * 纯 Java 版 {@code StringUtils.replaceOnce(text, search, replacement)}（commons-lang3 未引入）。
     * 仅下划线域名重试分支使用（8.83 smali:405）。
     */
    private static String replaceOnce(String text, String search, String replacement) {
        if (text == null || search == null || search.isEmpty()) return text;
        int idx = text.indexOf(search);
        if (idx < 0) return text;
        return text.substring(0, idx) + replacement + text.substring(idx + search.length());
    }

    /**
     * 8.83 {@code HttpUtilKt.toRequestBody1(String, String)} 纯 Java 移植（smali 全量）。
     * 语义：body 按 contentType 里 charset 参数（缺省 UTF-8）取字节，RequestBody 的 mediaType
     * 就是 contentType 本体（注意：并不是真正的 multipart 组装，8.83 亦如此）。
     */
    private static RequestBody toRequestBody1(String body, String contentType) {
        MediaType mt = MediaType.get(contentType);
        Charset cs = StandardCharsets.UTF_8;
        String lower = contentType.toLowerCase();
        int i = lower.indexOf("charset=");
        if (i >= 0) {
            String name = contentType.substring(i + 8).split("[;\\s]", 2)[0].replace("\"", "").trim();
            try {
                if (!name.isEmpty()) cs = Charset.forName(name);
            } catch (Exception ignored) {
            }
        }
        return RequestBody.create(body.getBytes(cs), mt);
    }

    //======================================================================
    // 8.83 HttpHelper$ByteHexConvert / HttpHelper$CharsetStringConvert 原样移植
    // （去掉 OkGo Converter 接口，保留 convertResponse(okhttp3.Response) 形态）
    //======================================================================

    /** 8.83 HttpHelper$ByteHexConvert 原样移植（smali 全文件）。 */
    private static class ByteHexConvert {
        public String convertResponse(Response response) throws Throwable {
            ResponseBody body = response.body();
            try {
                if (body == null) return "";
                return bytesToHexString(body.bytes());
            } finally {
                if (body != null) body.close();
            }
        }
    }

    /** 8.83 HttpHelper$CharsetStringConvert 原样移植（smali 全文件）。 */
    private static class CharsetStringConvert {
        private final String charset;

        CharsetStringConvert(String charset) {
            this.charset = charset;
        }

        public String convertResponse(Response response) throws Throwable {
            ResponseBody body = response.body();
            try {
                long contentLength = getContentLength(response.headers());
                if (contentLength > 0x1400000L) {
                    throw new IOException("content-length is too big: " + contentLength);
                }
                if (isEmpty(charset)) {
                    if (body != null) {
                        try {
                            return body.string();
                        } catch (IOException e) {
                            Logger.e(e.toString());
                        }
                    }
                    return "";
                }
                byte[] bytes = new byte[0];
                if (body != null) {
                    try {
                        bytes = body.bytes();
                    } catch (IOException e) {
                        Logger.e(e.toString());
                        return "";
                    }
                }
                return new String(bytes, charset);
            } finally {
                if (body != null) body.close();
            }
        }
    }

    //======================================================================
    // 8.83 HttpHelper$1（fetch0 内匿名 Callback）原样移植
    //======================================================================

    /**
     * 8.83 HttpHelper$1 原样移植（smali 全文件）。8.83 的匿名类本来就是
     * {@code okhttp3.Callback} 实现，这里保持不变。
     * 构造参数顺序与 smali 一致：(FetchResponse, CountDownLatch, finalInputStream,
     * finalOnlyHeaders, headersInterceptor, finalToHex, finalCharset)。
     */
    private static class FetchCallback implements Callback {
        private final FetchResponse fetchResponse;
        private final CountDownLatch countDownLatch;
        private final boolean finalInputStream;
        private final boolean finalOnlyHeaders;
        private final HeadersInterceptor headersInterceptor;
        private final boolean finalToHex;
        private final String finalCharset;

        FetchCallback(FetchResponse fetchResponse, CountDownLatch countDownLatch,
                      boolean finalInputStream, boolean finalOnlyHeaders,
                      HeadersInterceptor headersInterceptor, boolean finalToHex, String finalCharset) {
            this.fetchResponse = fetchResponse;
            this.countDownLatch = countDownLatch;
            this.finalInputStream = finalInputStream;
            this.finalOnlyHeaders = finalOnlyHeaders;
            this.headersInterceptor = headersInterceptor;
            this.finalToHex = finalToHex;
            this.finalCharset = finalCharset;
        }

        @Override
        public void onFailure(Call call, IOException e) {
            Logger.e(e.toString());
            fetchResponse.error = e.getMessage();
            countDownLatch.countDown();
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            try {
                fetchResponse.headers = response.headers().toMultimap();
                if (finalInputStream) {
                    fetchResponse.inputStream = response.body().byteStream();
                } else if (!finalOnlyHeaders
                        && !headersInterceptor.intercept((Map<String, List<String>>) fetchResponse.headers)) {
                    try {
                        if (finalToHex) {
                            fetchResponse.fetchResult = new ByteHexConvert().convertResponse(response);
                        } else {
                            fetchResponse.fetchResult = new CharsetStringConvert(finalCharset).convertResponse(response);
                        }
                    } catch (Throwable t) {
                        Logger.e(t.toString());
                        fetchResponse.error = t.getMessage();
                    }
                }
                fetchResponse.realUrl = response.request().url().toString();
                fetchResponse.statusCode = response.code();
            } catch (Throwable t) {
                countDownLatch.countDown();
                try {
                    if (!finalInputStream) call.cancel();
                } catch (Exception e) {
                    Logger.e(e.toString());
                }
                throw t;
            }
            countDownLatch.countDown();
            try {
                if (!finalInputStream) call.cancel();
            } catch (Exception e) {
                Logger.e(e.toString());
            }
        }
    }

    //======================================================================
    // fetch0 4 参（smali:662-688）：直接委托 5 参，client 传 null
    //======================================================================

    /** 8.83 HttpHelper.fetch0 4 参原样移植：client 传 null 委托 5 参版本。 */
    public static Object fetch0(String url, Map<String, Object> opts,
                                HeadersInterceptor interceptor, RuleFetchDelegate delegate) {
        return fetch0(url, opts, interceptor, delegate, null);
    }

    //======================================================================
    // fetch0 5 参（smali:689-2789）：网络底座全量移植（okhttp3 直写）
    //======================================================================

    /**
     * 8.83 HttpHelper.fetch0 5 参原样移植（smali:689-2789），OkGo 请求装配改为直接构建
     * {@code okhttp3.Request}。
     *
     * <p>分支保持与 smali 一致：opts 解析 → delegate 短路 → method 分发（HEAD/DELETE/GET）→
     * body 装配（POST/PUT/DELETE：rejectCoding 表单 / multipart / json / text / form；
     * GET/HEAD：仅 multipart）→ 非 UTF-8 charset 的 FormBody 重建 → headers 装配 →
     * client 选择（显式 client / HkHttp.client() / 超时派生 / 禁止重定向派生）→
     * 同步（直接 execute + converter）或异步（enqueue + CountDownLatch 等待）→
     * 返回值组装（inputStream 直返 / withHeaders|withStatusCode → JSON / fetchResult 或 ""），
     * 以及 "Android internal error" + statusCode==-1 时的下划线域名重试。</p>
     */
    public static Object fetch0(String url, Map<String, Object> opts,
                                HeadersInterceptor interceptor, RuleFetchDelegate delegate,
                                OkHttpClient client) {
        Logger.d("fetch, %s", url);
        long startTime = System.currentTimeMillis();
        try {
            if (isEmpty(url)) return "";
            // smali try_start_0/catchall_6 与外层 catchall_4 行为一致（log + return ""），合并为一层
            return fetch0Inner(url, opts, interceptor, delegate, client, startTime);
        } catch (Throwable t) {
            Logger.e(t.toString());
            return "";
        }
    }

    private static Object fetch0Inner(String url, Map<String, Object> opts,
                                      HeadersInterceptor interceptor, RuleFetchDelegate delegate,
                                      OkHttpClient client, long startTime) throws Throwable {
        // 8.83 本地 formParams：LinkedHashMap<String, String>（重复 key 后者覆盖，前者丢失）
        Map<String, String> formParams = new LinkedHashMap<>();

        String charset = null;
        boolean withHeaders = false;
        boolean onlyHeaders = false;
        boolean withStatusCode = false;
        boolean redirect = true;
        int timeout = -1;
        boolean toHex = false;
        boolean inputStream = false;
        boolean rejectCoding = false;
        String dns = null; // 8.83 dns 选项（per-request）：解析后在 client 派生段接回（G6）
        Map headers = null; // 8.83：raw Map，取值时逐项 check-cast（smali:122-157）
        String contentType = null;
        String body = null;
        String method = null;

        // okhttp3 直接装配：请求方法名 + 请求体（代替 OkGo 的 GetRequest/HeadRequest/PostRequest 等）
        String reqMethod;
        RequestBody reqBody;

        if (opts != null) {
            Map optHeaders = asMap(opts.get("headers"));
            if (optHeaders == null) optHeaders = asMap(opts.get("header"));
            headers = optHeaders;

            withHeaders = tryGetBool(opts, "withHeaders", Boolean.FALSE);
            onlyHeaders = tryGetBool(opts, "onlyHeaders", Boolean.FALSE);
            if (onlyHeaders) withHeaders = true;
            withStatusCode = tryGetBool(opts, "withStatusCode", Boolean.FALSE);
            redirect = tryGetBool(opts, "redirect", Boolean.TRUE);
            if (opts.containsKey("timeout")) {
                try {
                    timeout = Integer.parseInt(GSON.toJson(opts.get("timeout")));
                } catch (Exception e) {
                    Logger.e(e.toString());
                    timeout = -1;
                }
            }
            toHex = tryGetBool(opts, "toHex", Boolean.FALSE);
            inputStream = tryGetBool(opts, "inputStream", Boolean.FALSE);
            rejectCoding = tryGetBool(opts, "rejectCoding", Boolean.FALSE);
            dns = asString(tryGet(opts, "dns", null));

            // 8.83：先走规则 delegate，String/InputStream 直接短路返回（smali:144-145）
            Object delegateResult = delegate.fetch(url, toHex, inputStream);
            if (delegateResult instanceof String || delegateResult instanceof InputStream) {
                return delegateResult;
            }

            if (headers != null) {
                contentType = asString(headers.get("content-type"));
                if (isEmpty(contentType)) contentType = asString(headers.get("Content-Type"));
                if (isEmpty(contentType)) contentType = asString(headers.get("Content-type"));
                body = asString(headers.get("body"));
            }
            method = asString(opts.get("method"));
            // 8.83 method 分发：HEAD → OkGo.head / DELETE → OkGo.delete / 其它 → OkGo.get（smali:1038-1110）
            if ("HEAD".equalsIgnoreCase(method)) {
                reqMethod = "HEAD";
                withHeaders = true; // 8.83：HEAD/DELETE 分支复用 withHeaders 寄存器并置 true
            } else if ("DELETE".equalsIgnoreCase(method)) {
                reqMethod = "DELETE";
                withHeaders = true;
            } else {
                reqMethod = "GET";
            }
            reqBody = null;

            if (opts.containsKey("body")) {
                Object bodyObj = opts.get("body");
                if (bodyObj instanceof String) {
                    body = (String) bodyObj;
                } else {
                    // 8.83：body 非 String 且 method 不是 PUT/GET/HEAD/DELETE 时 method 改写为 POST
                    if (!"PUT".equalsIgnoreCase(method) && !"GET".equalsIgnoreCase(method)
                            && !"HEAD".equalsIgnoreCase(method) && !"DELETE".equalsIgnoreCase(method)) {
                        method = "POST";
                    }
                    body = GSON.toJson(bodyObj);
                }
            }

            if (contentType != null) {
                String[] parts = contentType.split("charset=");
                if (parts.length > 1) charset = parts[1];
            }
            if (charset == null && contentType != null) {
                String[] parts = contentType.split("charst=");
                if (parts.length > 1) charset = parts[1];
            }

            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                    || "DELETE".equalsIgnoreCase(method)) {
                // 8.83：POST/PUT/DELETE 另起 bodyRequest（OkGo.post/put/delete）替换 request（smali:1180-1520）
                reqMethod = "POST".equalsIgnoreCase(method) ? "POST"
                        : "PUT".equalsIgnoreCase(method) ? "PUT" : "DELETE";
                reqBody = buildPostBody(reqMethod, body, contentType, rejectCoding, formParams);
            } else {
                // 8.83：GET/HEAD 等非 POST 系 method，有 body 时仅当 multipart 才装配
                // （smali:122-124 对 GetRequest/HeadRequest 做 BodyRequest 强转——OkGo 里该强转必抛
                // ClassCastException；此处保留分支意图：把 body 挂到请求上。见 notes。）
                if (!isEmpty(body) && contentType != null
                        && contentType.contains("multipart/form-data; boundary")) {
                    reqBody = toRequestBody1(body, contentType);
                }
            }
        } else {
            // 8.83：opts == null 分支（smali:228-232）
            Object delegateResult = delegate.fetch(url, false, false);
            if (delegateResult instanceof String || delegateResult instanceof InputStream) {
                return delegateResult;
            }
            reqMethod = "GET";
            reqBody = null;
            // 其余保持初值：redirect=true, timeout=-1, 其余 false/null
        }

        // ---- 非 UTF-8 charset 的 FormBody 重建（smali:245-263）----
        // 8.83：charset 非空且非 UTF-8、method 为 POST/PUT 时，用指定 charset 从
        // request.getParams().urlParamsMap 重建 FormBody → upRequestBody → removeAllParams。
        // urlParamsMap 内容即上面装配的 formParams（已坍缩），此处直接重建并替换 reqBody。
        if (isNotEmpty(charset) && !"UTF-8".equalsIgnoreCase(charset)
                && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
            FormBody.Builder fb = new FormBody.Builder(Charset.forName(charset));
            for (Map.Entry<String, String> e : formParams.entrySet()) {
                fb.add(e.getKey(), e.getValue());
            }
            reqBody = fb.build();
            formParams.clear();
        }

        // ---- okhttp3.Request 装配（代替 OkGo 的 headers()/tag()/client()/retryCount() 链）----
        Request.Builder rb = new Request.Builder().url(url);
        if (reqBody != null) {
            rb.method(reqMethod, reqBody);
        } else if ("HEAD".equals(reqMethod)) {
            rb.head();
        } else if ("DELETE".equals(reqMethod)) {
            rb.delete();
        } else {
            rb.get();
        }
        // 8.83：headers 逐条装配（smali:239-243 原为 check-cast；扫查待裁 21 改安全
        // 取值：键/值非 String 的条目跳过，不再一条畸形头打死整条请求）
        Headers.Builder hb = new Headers.Builder();
        if (headers != null && !headers.isEmpty()) {
            for (Object o : headers.entrySet()) {
                Map.Entry e = (Map.Entry) o;
                String hk = asString(e.getKey());
                String hv = asString(e.getValue());
                if (hk == null || hv == null) continue;
                hb.add(hk, hv);
            }
        }
        rb.headers(hb.build());
        // 8.83：request.retryCount(0) 是 OkGo 层面的重试；okhttp3 默认对连接失败做一次透明重试，
        // 此处不派生 client 去关（行为差异见 notes）。
        Object tag = getTagFromThread();
        if (tag != null) rb.tag(Object.class, tag);

        OkHttpClient effectiveClient = resolveClient(url, client, redirect, timeout);
        // 8.83 装配末段（client 选定后、retry/执行前）：buildAutoDNSClient(dns, url,
        // client)（HHs:1943-2049 亲读）——per-request dns 此前解析后丢弃（G6）
        if (dns != null && !dns.trim().isEmpty()) {
            effectiveClient = HkHttp.buildAutoDNSClient(dns, url, effectiveClient);
        }
        Request request = rb.build();

        FetchResponse fetchResponse = new FetchResponse();
        fetchResponse.realUrl = url;

        // ---- 执行：8.83 原形——异步（enqueue 回调）当 onlyHeaders || interceptor != null，
        // 其余（普通抓取）走同步。旧移植把 interceptor 一条写反：普通抓取被赶进异步回调、
        // 回调里对 null 的 interceptor 调 intercept() 当场空指针（1.4.116 真机实证）；
        // 带 interceptor 的抓取反而走同步、interceptor 永不执行。inputStream 是咱家
        // 回调自带的流式取向，只能走异步（回调里流分支在 intercept 之前，解引用安全）。
        if (!onlyHeaders && interceptor == null && !inputStream) {
            // 8.83：request.converter(converter).adapt().execute()（smali:356-376）。
            // okhttp3 直调：execute() 抛 IOException 即网络失败（8.83 体现为 okResp.getException()）。
            try (Response response = effectiveClient.newCall(request).execute()) {
                fetchResponse.headers = response.headers().toMultimap();
                try {
                    if (toHex) {
                        fetchResponse.fetchResult = new ByteHexConvert().convertResponse(response);
                    } else {
                        fetchResponse.fetchResult = new CharsetStringConvert(charset).convertResponse(response);
                    }
                } catch (Throwable t) {
                    // 8.83：converter 抛错 → error 置 message，fetchResult 保持 null（smali:359-375）
                    Logger.e(t.toString());
                    fetchResponse.error = t.getMessage();
                }
                fetchResponse.realUrl = response.request().url().toString();
                fetchResponse.statusCode = response.code();
            } catch (IOException e) {
                Logger.e(e.toString());
                fetchResponse.error = e.getMessage();
            }
        } else {
            // ---- 异步：enqueue + CountDownLatch 等待（smali:295-354）----
            // 8.83：request.converter(converter).getRawCall() + enqueue(HttpHelper$1)。
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Call rawCall = effectiveClient.newCall(request);
                FetchCallback callback = new FetchCallback(fetchResponse, latch, inputStream,
                        onlyHeaders, interceptor, toHex, charset);
                rawCall.enqueue(callback);
            } catch (Exception e) {
                Logger.e(e.toString());
                fetchResponse.error = e.getMessage();
                try {
                    latch.countDown();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            // 8.83：timeout<=1000ms 等 10 秒，否则等 timeout/1000 秒
            long awaitSecs = timeout <= 1000 ? 10 : timeout / 1000L;
            latch.await(awaitSecs, TimeUnit.SECONDS);
        }

        Logger.d("js, fetch: consume=%s, %s", System.currentTimeMillis() - startTime, url);

        // ---- 返回值组装（smali:380-421）----
        if (inputStream) {
            if (!withHeaders && !withStatusCode) {
                return fetchResponse.inputStream;
            }
            // 8.83：fastjson JSONObject 组装后直接返回对象（不序列化）；此处用 LinkedHashMap。
            // key 顺序与 smali 一致：body, headers, url, statusCode, error。
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("body", fetchResponse.inputStream);
            json.put("headers", fetchResponse.headers);
            json.put("url", fetchResponse.realUrl);
            json.put("statusCode", fetchResponse.statusCode);
            json.put("error", fetchResponse.error);
            return json;
        }
        if (withHeaders || withStatusCode) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("body", fetchResponse.fetchResult);
            json.put("headers", fetchResponse.headers);
            json.put("url", fetchResponse.realUrl);
            json.put("statusCode", fetchResponse.statusCode);
            json.put("error", fetchResponse.error);
            fetchResponse.fetchResult = GSON.toJson(json);
        }
        try {
            // 8.83：error 含 "Android internal error" 且 statusCode==-1 时，下划线域名转连字符重试
            if (fetchResponse.error != null && fetchResponse.error.contains("Android internal error")
                    && fetchResponse.statusCode == -1) {
                String prefix = url.toLowerCase().startsWith("https") ? "https://" : "http://";
                String host = replaceOnce(url, prefix, "").split("/")[0];
                if (host.contains("_")) {
                    String newHost = host.replace("_", "-");
                    // 注意：8.83 此处用的是 newHost.length()（"_" 与 "-" 等长，结果一致）
                    String newUrl = prefix + newHost + url.substring(prefix.length() + newHost.length());
                    if (!newUrl.equals(url)) {
                        return fetch0(newUrl, opts, interceptor, delegate, client);
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(e.toString());
        }
        return fetchResponse.fetchResult != null ? fetchResponse.fetchResult : "";
    }

    /**
     * 8.83 POST/PUT/DELETE 的 body 装配分支（smali:1180-1520），OkGo {@code BodyRequest}
     * 方法映射为直接的 {@code RequestBody} 构造。环 24-14（硬门审计 B＋复核订正，
     * 2026-11-11）照官 fetch0 分发序重排，要点（cond 号为官 HttpHelper.smali 实位）：
     * <ul>
     * <li>reject 原样支＝cond_14：rejectCoding≠0 <b>且</b> {@code "POST".equalsIgnoreCase(method)}
     * 才命中（官在 cond_14 直读：先判 v0=rejectCoding、再判 v8="POST" 与 method 的
     * equalsIgnoreCase，两关皆过才原样 create；批2 首落整摘 POST 门是错的，复核
     * 直读裁定恢复本门）。命中即 {@code RequestBody.create(body, mt)}（mt=contentType，
     * 缺则 form-urlencoded）——支内<b>无</b> multipart 子支（官 cond_14 命中即原样
     * create，批2 首落的 toRequestBody1 子支是自创、已删）。消费点两处的方法名形态：
     * 本文件 :575 传规范化大写名、HkHttp:1101 传 Req 原串（可小写），故本门必须用
     * equalsIgnoreCase、不可大小写敏感比对。</li>
     * <li>非 reject 支序（cond_16 起）：multipart 检居第 2 位（reject 支之后、json/text
     * 之前），条件复官＝contentType 含 {@code "multipart/form-data; boundary"}（带
     * boundary 后缀的整串，批2 首落的无 boundary 宽检与 {@code body.contains("--")}
     * 自创检都已删——后者使含 "--" 的普通表单体误入本支、ct 为 null 时
     * toRequestBody1 内 MediaType.get(null) 抛 NPE 整请求崩）→ {@link #toRequestBody1}；
     * 余序：application/json → text/ → 形如 JSON（upJson）→ params 支。</li>
     * <li>params 支：按 {@code &} / 首个 {@code =} 切分、两边过 decodeConflictStr 装入
     * FormBody（formParams 出参照旧回填保留）；mediaType 按官 Reflect 点处置——
     * contentType 非空且 MediaType.parse 成功 → 把 FormBody 包一层 contentType()
     * 返回解析所得的 RequestBody（writeTo 委托 FormBody），parse 失败保持 FormBody
     * 本体（旧注「wire 无效」与审计 Reflect 点读法冲突，以审计施工单为准）。</li>
     * <li>body 为空 → 空 FormBody（OkGo generateRequestBody 无内容时同样落到空 FormBody）</li>
     * </ul>
     */
    static RequestBody buildPostBody(String reqMethod, String body, String contentType,
                                     boolean rejectCoding, Map<String, String> formParams) {
        if (isEmpty(body)) {
            return new FormBody.Builder().build();
        }
        if (rejectCoding && "POST".equalsIgnoreCase(reqMethod)) {
            MediaType mt = contentType == null
                    ? MediaType.parse("application/x-www-form-urlencoded")
                    : MediaType.parse(contentType);
            return RequestBody.create(body, mt);
        }
        if (contentType != null && contentType.contains("multipart/form-data; boundary")) {
            return toRequestBody1(body, contentType);
        }
        if (contentType != null && contentType.contains("application/json")) {
            return RequestBody.create(body, MediaType.parse(contentType));
        }
        if (contentType != null && contentType.contains("text/")) {
            return RequestBody.create(body, MediaType.parse(contentType));
        }
        if ((body.startsWith("{") && body.endsWith("}"))
                || (body.startsWith("[") && body.endsWith("]"))) {
            return RequestBody.create(body, JSON_MEDIA_TYPE);
        }
        for (String kv : body.split("&")) {
            int idx = kv.indexOf("=");
            // 8.83：substring(0, idx)/substring(idx+1)——kv 里没有 '=' 时抛异常，
            // 被外层 handler 捕获后整个 fetch 返回 ""。此处保持不设防，原样抛出。
            formParams.put(decodeConflictStr(kv.substring(0, idx)),
                    decodeConflictStr(kv.substring(idx + 1)));
        }
        FormBody.Builder fb = new FormBody.Builder();
        for (Map.Entry<String, String> e : formParams.entrySet()) {
            fb.add(e.getKey(), e.getValue());
        }
        FormBody formBody = fb.build();
        // params 支 mediaType（官 Reflect→Companion.parse 点）：contentType 可解析时
        // 包一层 contentType() 覆写、writeTo 委托 FormBody；解析失败保持本体。
        if (contentType != null && !contentType.isEmpty()) {
            MediaType parsed = null;
            try {
                parsed = MediaType.parse(contentType);
            } catch (Throwable ignored) {
            }
            if (parsed != null) {
                final FormBody delegate = formBody;
                final MediaType mediaType = parsed;
                return new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return mediaType;
                    }

                    @Override
                    public void writeTo(BufferedSink sink) throws java.io.IOException {
                        delegate.writeTo(sink);
                    }
                };
            }
        }
        return formBody;
    }

    /**
     * 8.83 的 client 选择分支（smali:267-286）：
     * <ul>
     * <li>显式传入 client → 直接用（8.83：request.client(client)；不叠加 timeout/redirect 定制）</li>
     * <li>timeout &gt; 0 → 对应 {@code buildOkHttpClient(timeout, redirect)}：
     * 用 {@code HkHttp.client().newBuilder()} 派生，read/write/connect 设为 timeout 毫秒，
     * redirect=false 时 followRedirects/followSslRedirects 置 false</li>
     * <li>timeout &lt;= 0 且 redirect=false → 对应 {@code noRedirectHttpClient}：
     * 同样派生，仅关重定向</li>
     * <li>其余 → 按归属裁决的 client（{@code HkHttp.clientFor(url)}：判走节点为显式
     * proxy 派生 client，判直连/无裁决为 {@code HkHttp.client()} 本体）</li>
     * </ul>
     * 派生 client 与本体共享连接池 / dispatcher / CookieJar / 拦截器
     * （okhttp3 {@code newBuilder()} 语义；DNS 系统默认）。
     *
     * <p>8.83 的 DoH 分支（{@code DohProvidersKt.buildAutoDNSClient / shouldUseAutoDNS}）：
     * 图片路径已按原版口径移植为 HkHttp.HK_IMAGE_DNS（默认系统解析，仅 haikuoshijie
     * 类地址在开 DoH 时走 DoH）；取数路径 DNS 为系统默认（照原版）。见 notes。</p>
     */
    private static OkHttpClient resolveClient(String url, OkHttpClient client, boolean redirect, int timeout) {
        if (client != null) {
            return client;
        }
        // 无显式 client 时与 HkHttp.to()/HkCodeUtil 同口径走 clientFor(url)：判走节点用
        // 显式 proxy 派生 client，与直连池连接天然不同 Address、互不复用。此前本路只用
        // client() 本体（仅选择器链），池里已有同 host 直连连接时 fetch0 的请求会直接
        // 复用、绕过网关——f17ca631 给 to()/get() 治了连接池污染，漏了本路，一并补齐。
        OkHttpClient base = url == null || url.isEmpty() ? HkHttp.client() : HkHttp.clientFor(url);
        if (timeout > 0 || !redirect) {
            OkHttpClient.Builder b = base.newBuilder();
            if (timeout > 0) {
                b.readTimeout(timeout, TimeUnit.MILLISECONDS);
                b.writeTimeout(timeout, TimeUnit.MILLISECONDS);
                b.connectTimeout(timeout, TimeUnit.MILLISECONDS);
            }
            if (!redirect) {
                b.followRedirects(false);
                b.followSslRedirects(false);
            }
            return b.build();
        }
        return base;
    }

    //======================================================================
    // generatePostOps（smali:2790-2876）
    //======================================================================

    /**
     * 8.83 HttpHelper.generatePostOps 原样移植。
     * opts 为 null → 返回仅含 method=POST 的新 map；否则若含 body 则按类型归一化为
     * String（Map → buildParamStr，其它 → GSON.toJson），再置 method=POST。
     */
    public static Object generatePostOps(Map<String, Object> postOps) {
        if (postOps == null) {
            Map<String, Object> map = new HashMap<>();
            map.put("method", "POST");
            return map;
        }
        if (postOps.containsKey("body")) {
            Object body = postOps.get("body");
            if (!(body instanceof String)) {
                if (body instanceof Map) {
                    body = buildParamStr((Map<String, Object>) body);
                } else {
                    body = GSON.toJson(body);
                }
            }
            postOps.put("body", body);
        }
        postOps.put("method", "POST");
        return postOps;
    }
}
