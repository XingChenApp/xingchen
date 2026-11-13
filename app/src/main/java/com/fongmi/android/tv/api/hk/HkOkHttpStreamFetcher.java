package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.HttpException;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.util.ContentLengthInputStream;
import com.github.catvod.crawler.SpiderDebug;
import com.orhanobut.logger.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 8.83 {@code OkHttpStreamFetcher} 语义的 Glide 图片抓取器。
 *
 * <p>请求头中 {@code js}/{@code timeout} 不发给服务器（8.83 loadData line 55 同款）。
 * {@code @js=} 求值规则（8.83 onResponse/onFailure 同款）：</p>
 * <ul>
 *   <li>HTTP 2xx 且有 js：{@code input} = 响应流；</li>
 *   <li>HTTP 非 2xx 或请求异常且有 js：{@code input} = null，给规则一次自救机会；</li>
 *   <li>JS 返回 InputStream 才采用，非流/异常 → 用原流；无 js 时走原逻辑。</li>
 * </ul>
 *
 * <p>星辰加的一环兜底（8.83 无此环，其名单覆盖面靠服务端维护）：首发没走
 * 自动 DNS 时，请求失败（同 client 自重试用尽后）、HTTP 错误或 2xx 却回了
 * 解码必败的 HTML 拦截页（系统解析给错 IP 的典型形态），用
 * {@link HkHttp#buildAutoDNSClient} 派生的备用解析 client 把同一请求再发
 * 一次，只一次、不循环；成败都在 hkimage 日志里以 retried=true 留痕。
 * 已取消（页面切换的 Canceled）绝不重试；成功路径行为不变。</p>
 */
public class HkOkHttpStreamFetcher implements DataFetcher<InputStream>, okhttp3.Callback {

    private static final String TAG = "HkImageFetch";

    private static final int MAX_ATTEMPTS = 3;

    private final Call.Factory client;
    private final GlideUrl url;
    /** 首发用的原 client（未派生自动 DNS 前），换解析重试的派生基底；可为 null。 */
    private final OkHttpClient baseClient;
    /** 首发是否已走自动 DNS：已走则换解析重试无意义，不再触发。 */
    private final boolean autoDnsUsed;
    private boolean autoRetried;
    private int attempts;
    private volatile boolean cancelled;
    private volatile Call call;
    private InputStream stream;
    private ResponseBody responseBody;
    private DataCallback<? super InputStream> callback;

    public HkOkHttpStreamFetcher(Call.Factory client, GlideUrl url) {
        this(client, url, null, false);
    }

    public HkOkHttpStreamFetcher(Call.Factory client, GlideUrl url, OkHttpClient baseClient, boolean autoDnsUsed) {
        this.client = client;
        this.url = url;
        this.baseClient = baseClient;
        this.autoDnsUsed = autoDnsUsed;
    }

    @Override
    public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
        Request.Builder builder = new Request.Builder().url(url.toStringUrl());
        for (Map.Entry<String, String> entry : url.getHeaders().entrySet()) {
            String key = entry.getKey();
            if ("js".equals(key) || "timeout".equals(key)) continue;
            builder.addHeader(key, entry.getValue());
        }
        Request request = builder.build();
        this.callback = callback;
        attempts++;
        call = client.newCall(request);
        SpiderDebug.log("hkimage", "图片请求发出 host=%s 带解密头=%s", hostOf(), jsBody() != null);
        call.enqueue(this);
    }

    /** 重新组装并发出同一图片请求（loadData 的请求构建逻辑复用一遍）。 */
    private void refire() {
        Request.Builder builder = new Request.Builder().url(url.toStringUrl());
        for (Map.Entry<String, String> entry : url.getHeaders().entrySet()) {
            String key = entry.getKey();
            if ("js".equals(key) || "timeout".equals(key)) continue;
            builder.addHeader(key, entry.getValue());
        }
        attempts++;
        call = client.newCall(builder.build());
        SpiderDebug.log("hkimage", "图片请求重试 host=%s 第%d次", hostOf(), attempts);
        call.enqueue(this);
    }

    /**
     * 失败换解析重试（全生命周期只一次）：首发没走自动 DNS、自动 DNS 总开关
     * 开着（用户指定 DoH 时不覆盖其设置）、且未被取消时，用原 client 派生的
     * 备用解析 client 把同一请求再发一次。返回 true 表示已接管本次失败，
     * 后续成败仍走本类的 onResponse/onFailure（autoRetried 已置位，不会再环）。
     */
    private boolean tryAutoRetry(String reason) {
        if (cancelled || autoRetried || autoDnsUsed || baseClient == null) return false;
        if (!HkHttp.autoDnsEnabled()) return false;
        Call.Factory factory;
        try {
            factory = HkHttp.buildAutoDNSClient(url.toStringUrl(), baseClient);
        } catch (Throwable t) {
            return false;
        }
        if (factory == null) return false;
        try {
            Request.Builder builder = new Request.Builder().url(url.toStringUrl());
            for (Map.Entry<String, String> entry : url.getHeaders().entrySet()) {
                String key = entry.getKey();
                if ("js".equals(key) || "timeout".equals(key)) continue;
                builder.addHeader(key, entry.getValue());
            }
            attempts++;
            call = factory.newCall(builder.build());
            call.enqueue(this);
            autoRetried = true;
            SpiderDebug.log("hkimage", "图片换解析重试 host=%s retried=true 原因=%s", hostOf(), reason);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 换解析重试的成败留痕（retried=true 明记）；没重试过时零日志，原路径不变。 */
    private void logRetryOutcome(boolean ok) {
        if (!autoRetried) return;
        SpiderDebug.log("hkimage", "图片换解析重试%s host=%s retried=true", ok ? "成功" : "失败", hostOf());
    }

    /**
     * 2xx 但解码必败的明显坏响应（解析给错 IP 时常见的拦截/占位 HTML 页）：
     * 只认 Content-Type 明示 text/html 或正文以 HTML 标记开头。探测用
     * peekBody 只窥前段、不消费正文；带 js 的图正文是密文、本就非图，
     * 调用方不会拿它来判。
     */
    private boolean looksLikeHtmlBlock(Response response) {
        try {
            if (responseBody == null) return false;
            String ct = response.header("Content-Type");
            if (ct != null && ct.toLowerCase().contains("text/html")) return true;
            byte[] head = response.peekBody(256).bytes();
            int i = 0;
            if (head.length >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) i = 3;
            while (i < head.length && (head[i] == ' ' || head[i] == '\n' || head[i] == '\r' || head[i] == '\t')) i++;
            if (i >= head.length || head[i] != '<') return false;
            String s = new String(head, i, Math.min(head.length - i, 64),
                    java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase();
            return s.startsWith("<!doctype") || s.startsWith("<html")
                    || s.startsWith("<head") || s.startsWith("<body");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 关掉当前响应体（换解析重试接管前释放连接）；cleanup 仍按字段兜底。 */
    private void closeResponseQuietly() {
        try {
            if (responseBody != null) responseBody.close();
        } catch (Throwable ignored) {
        }
        responseBody = null;
    }

    /** GlideUrl headers 中的 js 体（8.83 以 header "js" 传递 @js= 代码）。 */
    private String jsBody() {
        String js = url.getHeaders().get("js");
        return TextUtils.isEmpty(js) ? null : js;
    }

    /** 只记元信息（主机名等），不记内容：用于可导出日志断链。 */
    private String hostOf() {
        try {
            okhttp3.HttpUrl u = okhttp3.HttpUrl.parse(url.toStringUrl());
            return u != null ? u.host() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** W3：调图片求值进程单例（8.83 JSEngine.getInstance() 同款）；单例起不来时退化为原 input。 */
    private InputStream evalImageJs(String jsBody, InputStream input, Map<String, List<String>> headers) {
        try {
            HkJsRuntime img = HkJsRuntime.imageEngine();
            if (img == null) {
                SpiderDebug.log("hkimage", "图片单例初始化失败 host=%s（退化为原流）", hostOf());
                return input;
            }
            return img.evalImageJs(jsBody, input, headers, url.toString(), HkImage.currentTitle());
        } catch (Throwable t) {
            String cls = t.getClass().getSimpleName();
            String msg = t.getMessage();
            SpiderDebug.log("hkimage", "图片求值异常 host=%s 类型=%s 信息=%s", hostOf(), cls, msg == null ? "" : msg);
            Logger.t(TAG).d("evalImageJs failed: %s", t.getMessage());
            return input;
        }
    }

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        String cls = e.getClass().getSimpleName();
        String msg = e.getMessage();
        SpiderDebug.log("hkimage", "图片请求失败 host=%s 类型=%s 信息=%s", hostOf(), cls, msg == null ? "" : msg);
        // 瞬时网络故障（连接被掐/重置/超时）先自重试：换新连接再试，试满才走下面的自救/失败。
        // 图床类站点在直连下首连常被掐断，重试一两次就能拿到图（真机日志实证：同批图前几张
        // Connection reset、随后成功）。Canceled 是 Glide 主动取消，不重试。
        if (!cancelled && !autoRetried && attempts < MAX_ATTEMPTS && !"Canceled".equals(msg)) {
            try {
                refire();
                return;
            } catch (Throwable ignored) {
            }
        }
        // 同 client 自重试用尽仍失败：换备用解析再试一次（Canceled 已在上面排除，
        // tryAutoRetry 内再查 cancelled；接管后成败走重试请求的回调）。
        if (!"Canceled".equals(msg) && tryAutoRetry("请求失败 " + cls)) return;
        String js = jsBody();
        if (js != null) {
            InputStream s = evalImageJs(js, null, Collections.emptyMap());
            if (s != null) {
                stream = s;
                SpiderDebug.log("hkimage", "图片就绪(失败自救) host=%s 流=%s", hostOf(),
                        s.getClass().getSimpleName());
                // 走到自救说明（含换解析重试在内）取数都失败了，重试环记失败
                logRetryOutcome(false);
                callback.onDataReady(s);
                return;
            }
        }
        logRetryOutcome(false);
        callback.onLoadFailed(e);
    }

    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) {
        responseBody = response.body();
        long len = -1;
        try {
            if (responseBody != null) len = responseBody.contentLength();
        } catch (Throwable ignored) {
        }
        SpiderDebug.log("hkimage", "图片响应 host=%s 状态=%d 声明字节=%d 带解密头=%s",
                hostOf(), response.code(), len, jsBody() != null);
        String js = jsBody();
        try {
            if (response.isSuccessful()) {
                if (js != null && responseBody != null) {
                    InputStream input = responseBody.byteStream();
                    InputStream s = evalImageJs(js, input, response.headers().toMultimap());
                    stream = s;
                    SpiderDebug.log("hkimage", "图片就绪 host=%s 流=%s", hostOf(),
                            s == null ? "null" : s.getClass().getSimpleName());
                    logRetryOutcome(true);
                    callback.onDataReady(s);
                    return;
                }
                // 2xx 却回了 HTML 拦截页（解码必败）：多半是解析给错 IP。
                // 换备用解析重试一次；重试不可用时照旧把原流交出去，行为不变。
                if (js == null && !cancelled && looksLikeHtmlBlock(response)
                        && tryAutoRetry("坏响应HTML 状态=" + response.code())) {
                    closeResponseQuietly();
                    return;
                }
                InputStream s = responseBody == null ? null
                        : ContentLengthInputStream.obtain(responseBody.byteStream(), responseBody.contentLength());
                stream = s;
                SpiderDebug.log("hkimage", "图片就绪(无解密) host=%s 流=%s", hostOf(),
                        s == null ? "null" : s.getClass().getSimpleName());
                logRetryOutcome(true);
                callback.onDataReady(s);
                return;
            }
            // HTTP 错误：先换备用解析重试一次，接管后成败走重试请求的回调；
            // 不接管（已重试过/开关关/无基底）才落回下面的 js 自救与失败。
            if (!cancelled && tryAutoRetry("HTTP " + response.code())) {
                closeResponseQuietly();
                return;
            }
            if (js != null) {
                InputStream s = evalImageJs(js, null, response.headers().toMultimap());
                if (s != null) {
                    stream = s;
                    // 自救产出：取数（含换解析重试）没拿到可用响应，重试环记失败
                    logRetryOutcome(false);
                    callback.onDataReady(s);
                    return;
                }
            }
            logRetryOutcome(false);
            callback.onLoadFailed(new HttpException(response.message(), response.code()));
        } catch (Throwable t) {
            logRetryOutcome(false);
            callback.onLoadFailed(new IOException("Hk image fetch failed: " + t.getMessage(), t));
        }
    }

    @Override
    public void cleanup() {
        try {
            if (stream != null) stream.close();
        } catch (Throwable ignored) {
        }
        try {
            if (responseBody != null) responseBody.close();
        } catch (Throwable ignored) {
        }
        stream = null;
        responseBody = null;
        callback = null;
    }

    @Override
    public void cancel() {
        cancelled = true;
        Call c = call;
        if (c != null) c.cancel();
    }

    @NonNull
    @Override
    public Class<InputStream> getDataClass() {
        return InputStream.class;
    }

    @NonNull
    @Override
    public DataSource getDataSource() {
        return DataSource.REMOTE;
    }
}
