package com.fongmi.android.tv.web;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.PluginKinds;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.custom.CustomWebView;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.WebViewUtil;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HomeWebController {

    private static final String BRIDGE = "fongmiBridge";
    private static final int SLOW_KEY_MS = 24;
    private static final long LOAD_TIMEOUT_MS = 15000;
    private static HomeWebController active;

    private final Listener listener;
    private final Activity activity;
    private final boolean debugTools;
    private WebView webView;
    private final float density;
    private Site site;
    private String defaultUserAgent;
    private String homePage;
    private volatile String lastPageUrl;
    private WebHomeRawAdapter rawAdapter;
    private WebHomeViewport viewport = WebHomeViewport.EMPTY;
    private String lastViewportKey;
    private long pauseAt;
    private long lastKeyAt;
    private int inlineEvaluationCount;
    private int loadToken;
    private int loadTimeoutRecoveries;
    private boolean sdkReady;
    private boolean paused;
    private volatile boolean destroyed;
    private HomeWebBridge bridge;
    private volatile DialectSourceTag dialectTag;
    private volatile boolean bridgeInvokeSeen;
    private volatile boolean bridgePlaySeen;
    private CustomWebView takeoverView;
    private volatile boolean takeoverBusy;
    private volatile int takeoverSeq;

    public HomeWebController(Activity activity, WebView webView, Listener listener) {
        this(activity, webView, listener, false);
    }

    public HomeWebController(Activity activity, WebView webView, Listener listener, boolean debugTools) {
        this.activity = activity;
        this.webView = webView;
        this.listener = listener;
        this.debugTools = debugTools;
        this.density = activity.getResources().getDisplayMetrics().density;
        active = this;
        init();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void init() {
        if (debugTools) WebView.setWebContentsDebuggingEnabled(true);
        WebViewUtil.configureHome(webView);
        defaultUserAgent = webView.getSettings().getUserAgentString();
        if (Util.isLeanback()) webView.setNextFocusUpId(R.id.title);
        webView.setBackgroundColor(Color.TRANSPARENT);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setOnFocusChangeListener((v, hasFocus) -> SpiderDebug.log("webhome-focus", "webview focus=%s visible=%s url=%s", hasFocus, isVisible(), webView.getUrl()));
        webView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> injectViewport());
        bridge = new HomeWebBridge(this, activity, webView);
        webView.addJavascriptInterface(bridge, BRIDGE);
        webView.setWebViewClient(client());
        webView.setWebChromeClient(chrome());
        WebViewUtil.logProvider("webhome");
    }

    public boolean load(Site site) {
        return load(site, false);
    }

    public boolean load(Site site, boolean force) {
        if (!site.hasHomePage() && !site.isWebHomeApi()) return false;
        if (Setting.isWebHomeFullscreen()) listener.applyDefaultChrome(site);
        else listener.setChrome(normalChrome());
        Server.get().start();
        String url = getHomePage(site);
        boolean reload = force || !url.equals(homePage);
        this.site = site;
        refreshDialectTag();
        if (dialectTag == null && PluginKinds.HTML.equals(PluginKinds.kindOfKey(site.getKey()))) {
            // S1 静态打标预热：导入 HTML 源在加载期先把 fm.* 差集扫出来，
            // 点播接管判据（judgeTakeover）与出站钩子装配都只读缓存结果。
            Site current = site;
            Task.execute(() -> DialectSourceTag.resolve(current.getKey(), current.getApi()));
        }
        rawAdapter = WebHomeRawAdapter.create(url, site.getHeader());
        if (reload) {
            sdkReady = false;
            lastViewportKey = "";
            bridgeInvokeSeen = false;
            bridgePlaySeen = false;
            cancelTakeover();
            homePage = url;
            loadUrl(force ? reloadUrl(homePage) : homePage);
        }
        show();
        return true;
    }

    public void reload() {
        if (TextUtils.isEmpty(homePage)) {
            webView.reload();
        } else {
            webView.clearCache(false);
            loadUrl(reloadUrl(homePage));
        }
    }

    private void loadUrl(String url) {
        Map<String, String> headers = site == null ? Collections.emptyMap() : site.getHeader();
        String userAgent = header(headers, HttpHeaders.USER_AGENT);
        if (!TextUtils.isEmpty(userAgent)) webView.getSettings().setUserAgentString(userAgent);
        else if (!TextUtils.isEmpty(defaultUserAgent)) webView.getSettings().setUserAgentString(defaultUserAgent);
        Map<String, String> requestHeaders = requestHeaders(url, headers);
        lastPageUrl = url;
        int token = ++loadToken;
        SpiderDebug.log("webhome-webview", "load url=%s ua=%s headers=%s", url, !TextUtils.isEmpty(userAgent), requestHeaders.keySet());
        if (requestHeaders.isEmpty()) webView.loadUrl(url);
        else webView.loadUrl(url, requestHeaders);
        webView.postDelayed(() -> handleLoadTimeout(token, url), LOAD_TIMEOUT_MS);
    }

    private void handleLoadTimeout(int token, String url) {
        if (token != loadToken || !isVisible() || activity.isFinishing() || activity.isDestroyed()) return;
        SpiderDebug.log("webhome-webview", "load timeout url=%s current=%s title=%s recoveries=%s", url, webView.getUrl(), webView.getTitle(), loadTimeoutRecoveries);
        if (TextUtils.isEmpty(homePage) || loadTimeoutRecoveries++ > 0) {
            listener.onWebError();
            return;
        }
        String target = !TextUtils.isEmpty(lastPageUrl) && !isEmptyDocumentUrl(lastPageUrl) ? lastPageUrl : homePage;
        recreateWebView();
        listener.onWebLoading();
        loadUrl(reloadUrl(target, true));
    }

    private Map<String, String> requestHeaders(String url, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return Collections.emptyMap();
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (TextUtils.isEmpty(key) || value == null) continue;
            if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key)) continue;
            if (HttpHeaders.COOKIE.equalsIgnoreCase(key)) {
                CookieManager.getInstance().setCookie(url, value);
                continue;
            }
            result.put(key, value);
        }
        return result;
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) return "";
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        }
        return "";
    }

    boolean isDestroyed() {
        return destroyed;
    }

    /** 当前承载的站点（WebCall 给 fm.req 打源归属点用；未 load 时为 null）。 */
    Site currentSite() {
        return site;
    }

    public void evaluate(String script, ValueCallback<String> callback) {
        // 销毁后不许再往已销毁的 WebView 投脚本：post 排队期间也可能
        // 正好赶上 destroy，进队前后各查一次销毁旗。
        if (destroyed) return;
        webView.post(() -> {
            if (!destroyed) webView.evaluateJavascript(script, callback);
        });
    }

    public void dispatchDebugConsole(String level, String message) {
        if (!debugTools) return;
        String text = (TextUtils.isEmpty(level) ? "log" : level).toUpperCase(Locale.ROOT) + " " + (message == null ? "" : message);
        App.post(() -> listener.onWebConsole(text));
    }

    public void dispatchDebugNetwork(String type, String method, String url, int status, long durationMs, String detail) {
        if (!debugTools) return;
        App.post(() -> listener.onWebNetwork(type, method, url, status, durationMs, detail));
    }

    public void show() {
        active = this;
        webView.setVisibility(View.VISIBLE);
        focusWebView("show");
    }

    public void hide() {
        webView.setVisibility(View.GONE);
    }

    public boolean isVisible() {
        return webView.getVisibility() == View.VISIBLE;
    }

    public boolean handleBack() {
        if (!isVisible()) return false;
        if (!webView.canGoBack()) return false;
        String current = webView.getUrl();
        if (samePage(current, homePage)) {
            SpiderDebug.log("webhome-webview", "back home boundary current=%s", current);
            return false;
        }
        String previous = previousHistoryUrl();
        if (!sameSite(current, previous)) {
            SpiderDebug.log("webhome-webview", "back boundary current=%s previous=%s", current, previous);
            return false;
        }
        webView.goBack();
        return true;
    }

    private String previousHistoryUrl() {
        try {
            WebBackForwardList list = webView.copyBackForwardList();
            int index = list.getCurrentIndex() - 1;
            WebHistoryItem item = index >= 0 ? list.getItemAtIndex(index) : null;
            return item == null ? "" : item.getUrl();
        } catch (Throwable e) {
            SpiderDebug.log("webhome-webview", "back history unavailable error=%s", e.getMessage());
            return "";
        }
    }

    private boolean sameSite(String current, String target) {
        if (TextUtils.isEmpty(current) || TextUtils.isEmpty(target)) return false;
        Uri currentUri = Uri.parse(current);
        Uri targetUri = Uri.parse(target);
        String currentScheme = UrlUtil.scheme(currentUri);
        String targetScheme = UrlUtil.scheme(targetUri);
        String currentHost = UrlUtil.host(currentUri);
        String targetHost = UrlUtil.host(targetUri);
        if (currentHost.isEmpty() || targetHost.isEmpty()) return current.equals(target);
        return currentScheme.equals(targetScheme) && currentHost.equals(targetHost) && port(currentUri) == port(targetUri);
    }

    private boolean samePage(String current, String target) {
        if (!sameSite(current, target)) return false;
        Uri currentUri = Uri.parse(current);
        Uri targetUri = Uri.parse(target);
        return path(currentUri).equals(path(targetUri))
                && cleanQuery(currentUri).equals(cleanQuery(targetUri))
                && fragment(currentUri).equals(fragment(targetUri));
    }

    private String path(Uri uri) {
        String path = uri.getEncodedPath();
        return TextUtils.isEmpty(path) ? "/" : path;
    }

    private String fragment(Uri uri) {
        String fragment = uri.getEncodedFragment();
        return fragment == null ? "" : fragment;
    }

    private String cleanQuery(Uri uri) {
        String query = uri.getEncodedQuery();
        if (TextUtils.isEmpty(query)) return "";
        StringBuilder result = new StringBuilder();
        for (String part : query.split("&")) {
            int index = part.indexOf('=');
            String name = index >= 0 ? part.substring(0, index) : part;
            if ("_fm_reload".equals(name) || "_fm_restore".equals(name)) continue;
            if (result.length() > 0) result.append('&');
            result.append(part);
        }
        return result.toString();
    }

    private int port(Uri uri) {
        int port = uri.getPort();
        if (port >= 0) return port;
        String scheme = UrlUtil.scheme(uri);
        if ("http".equals(scheme)) return 80;
        if ("https".equals(scheme)) return 443;
        return -1;
    }

    public void setToolbar(boolean visible) {
        if (!Setting.isWebHomeFullscreen()) {
            listener.setChrome(normalChrome());
            return;
        }
        listener.setToolbar(visible);
    }

    public void setChrome(JsonObject payload) {
        if (!Setting.isWebHomeFullscreen()) {
            listener.setChrome(normalChrome());
            return;
        }
        listener.setChrome(importedHtmlEdgeToNormal(payload));
    }

    /**
     * 导入的 HTML 源一律带框（潇洒哥 2026-11-11 亲裁，账
     * html-first-open-frameless-20261111）：页面自发 setChrome 摘框本是官形
     * （默影视同），但导入源按他的定版偏离一步——这类源上页面发来的 edge
     * 改判 normal，immersive（播放沉浸）与其余字段照原样透传；非导入源
     * （配置站等其余 WebHome 承载）零影响、保持官形。判据只认当前承载
     * Site 的动态源 key 前缀（PluginKinds），不看域名与页面内容。此口是
     * 页面 fm.ui.setChrome 的唯一分发点；壳子自身的 chrome 调用直达监听器
     * （VodFragment.onWebHomeFullscreen、返回链 requestNormalChrome），
     * 不经此口、不受影响。
     */
    private JsonObject importedHtmlEdgeToNormal(JsonObject payload) {
        if (payload == null || site == null) return payload;
        if (!PluginKinds.HTML.equals(PluginKinds.kindOfKey(site.getKey()))) return payload;
        if (!payload.has("mode") || !payload.get("mode").isJsonPrimitive()) return payload;
        // 与下游 WebHomeChrome.normalize 同口径（去空白＋小写）判 edge。
        String mode = payload.get("mode").getAsString().trim().toLowerCase(Locale.ROOT);
        if (!WebHomeChrome.EDGE.equals(mode)) return payload;
        payload.addProperty("mode", WebHomeChrome.NORMAL);
        return payload;
    }

    public void restoreChrome() {
        if (!Setting.isWebHomeFullscreen()) {
            listener.setChrome(normalChrome());
            return;
        }
        listener.restoreChrome();
    }

    private JsonObject normalChrome() {
        JsonObject object = new JsonObject();
        object.addProperty("mode", WebHomeChrome.NORMAL);
        return object;
    }

    public String getViewportJson() {
        return viewport.json(density, webView.getWidth(), webView.getHeight());
    }

    public void setViewport(WebHomeViewport viewport) {
        this.viewport = viewport == null ? WebHomeViewport.EMPTY : viewport;
        injectViewport();
    }

    public void openVod() {
        listener.openVod();
    }

    public void openSetting() {
        listener.openSetting();
    }

    public void onResume() {
        paused = false;
        synchronized (this) {
            inlineEvaluationCount = 0;
        }
        webView.onResume();
        webView.resumeTimers();
        recoverAfterResume();
    }

    public void onPause() {
        paused = true;
        pauseAt = System.currentTimeMillis();
        dispatchLifecycle("fmpause", "{time:" + pauseAt + "}");
        webView.onPause();
    }

    public boolean beginInlineEvaluation() {
        synchronized (this) {
            if (!paused) return false;
            inlineEvaluationCount++;
            if (inlineEvaluationCount > 1) return true;
        }
        App.post(() -> {
            if (!paused) return;
            SpiderDebug.log("webhome-inline", "resume WebView for inline evaluation url=%s", webView.getUrl());
            webView.onResume();
            webView.resumeTimers();
        });
        return true;
    }

    public void endInlineEvaluation(boolean active) {
        if (!active) return;
        boolean pause;
        synchronized (this) {
            if (inlineEvaluationCount > 0) inlineEvaluationCount--;
            pause = paused && inlineEvaluationCount == 0;
        }
        if (!pause) return;
        App.post(() -> {
            if (!paused) return;
            SpiderDebug.log("webhome-inline", "pause WebView after inline evaluation url=%s", webView.getUrl());
            webView.onPause();
        });
    }

    public void destroy() {
        destroyed = true;
        cancelTakeover();
        rawAdapter = null;
        webView.stopLoading();
        webView.destroy();
        if (debugTools) WebView.setWebContentsDebuggingEnabled(false);
        if (active == this) active = null;
    }

    private void recreateWebView() {
        ViewGroup parent = webView.getParent() instanceof ViewGroup ? (ViewGroup) webView.getParent() : null;
        if (parent == null) return;
        int index = parent.indexOfChild(webView);
        int id = webView.getId();
        int visibility = webView.getVisibility();
        ViewGroup.LayoutParams params = webView.getLayoutParams();
        try {
            webView.stopLoading();
            parent.removeView(webView);
            webView.destroy();
        } catch (Throwable ignored) {
        }
        webView = new WebView(activity);
        webView.setId(id);
        webView.setVisibility(visibility);
        parent.addView(webView, Math.max(0, index), params);
        init();
    }

    private void recoverAfterResume() {
        if (!isVisible()) return;
        if (recoverEmptyDocument()) return;
        webView.setBackgroundColor(Color.TRANSPARENT);
        focusWebView("resume");
        webView.requestLayout();
        webView.invalidate();
        webView.postInvalidateOnAnimation();
        nudgeCompositor();
        dispatchResume(0);
        dispatchResume(80);
        dispatchResume(260);
    }

    private boolean recoverEmptyDocument() {
        String current = webView.getUrl();
        if (!isEmptyDocumentUrl(current) || TextUtils.isEmpty(homePage)) return false;
        String target = !TextUtils.isEmpty(lastPageUrl) && !isEmptyDocumentUrl(lastPageUrl) ? lastPageUrl : homePage;
        SpiderDebug.log("webhome-webview", "restore reload reason=empty-url current=%s target=%s", current, target);
        listener.onWebLoading();
        sdkReady = false;
        lastViewportKey = "";
        loadUrl(reloadUrl(target, true));
        return true;
    }

    private boolean isEmptyDocumentUrl(String url) {
        return TextUtils.isEmpty(url) || "about:blank".equalsIgnoreCase(url);
    }

    private void dispatchResume(long delay) {
        webView.postDelayed(() -> {
            injectViewport();
            long now = System.currentTimeMillis();
            long pausedMs = pauseAt > 0 ? Math.max(0, now - pauseAt) : 0;
            dispatchLifecycle("fmresume", "{time:" + now + ",pausedMs:" + pausedMs + "}");
        }, delay);
    }

    private void nudgeCompositor() {
        webView.setAlpha(0.99f);
        webView.postDelayed(() -> {
            webView.setAlpha(1f);
            webView.invalidate();
            webView.postInvalidateOnAnimation();
        }, 50);
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!isVisible() || !Util.isLeanback() || !isRemoteKey(event)) return false;
        long start = System.currentTimeMillis();
        long gap = lastKeyAt > 0 ? start - lastKeyAt : -1;
        lastKeyAt = start;
        focusWebView("key");
        boolean handled = webView.dispatchKeyEvent(event);
        long cost = System.currentTimeMillis() - start;
        if (cost >= SLOW_KEY_MS || (KeyUtil.isActionDown(event) && event.getRepeatCount() > 0 && cost >= 12)) {
            SpiderDebug.log("webhome-key", "slow action=%s key=%s repeat=%s handled=%s cost=%sms gap=%sms focus=%s url=%s",
                    event.getAction(), event.getKeyCode(), event.getRepeatCount(), handled, cost, gap, webView.hasFocus(), webView.getUrl());
        }
        return handled;
    }

    public boolean requestFocus(String reason) {
        return focusWebView(reason);
    }

    private boolean isRemoteKey(KeyEvent event) {
        return KeyUtil.isUpKey(event)
                || KeyUtil.isDownKey(event)
                || KeyUtil.isLeftKey(event)
                || KeyUtil.isRightKey(event)
                || KeyUtil.isEnterKey(event);
    }

    private boolean focusWebView(String reason) {
        if (webView.hasFocus()) return true;
        boolean ok = webView.requestFocus();
        SpiderDebug.log("webhome-focus", "request reason=%s ok=%s visible=%s width=%s height=%s url=%s", reason, ok, isVisible(), webView.getWidth(), webView.getHeight(), webView.getUrl());
        return ok;
    }

    private void dispatchLifecycle(String event, String detail) {
        if (destroyed) return;
        String script = "(function(){try{window.dispatchEvent(new CustomEvent('" + event + "',{detail:" + detail + "}));}catch(e){}})();";
        webView.post(() -> {
            if (!destroyed) webView.evaluateJavascript(script, null);
        });
    }

    private WebViewClient client() {
        return new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                SpiderDebug.log("webhome-webview", "page started url=%s", url);
                listener.onWebRequest("PAGE", url, true);
                lastPageUrl = url;
                bridgeInvokeSeen = false;
                bridgePlaySeen = false;
                sdkReady = false;
                lastViewportKey = "";
                listener.onWebLoading();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                SpiderDebug.log("webhome-webview", "page finished url=%s title=%s", url, view.getTitle());
                loadToken++;
                loadTimeoutRecoveries = 0;
                lastPageUrl = url;
                injectSdk();
                focusWebView("page-finished");
                listener.onWebReady();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                SpiderDebug.log("webhome-webview", "resource error main=%s code=%s desc=%s url=%s", request.isForMainFrame(), error.getErrorCode(), error.getDescription(), request.getUrl());
                listener.onWebConsole("ERROR " + error.getErrorCode() + " " + error.getDescription() + " " + request.getUrl());
                if (request.isForMainFrame()) {
                    loadToken++;
                    homePage = null;
                    rawAdapter = null;
                    // 不把 Chromium 原始错误描述（net::ERR_* 英文串）直接弹给用户：
                    // 只弹人话，原始码留日志与控制台通道（上方两行已记）。
                    Notify.show("页面加载失败，请稍后重试");
                    listener.onWebError();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                listener.onWebRequest(request.getMethod(), request.getUrl().toString(), request.isForMainFrame(), request.getRequestHeaders());
                WebResourceResponse raw = rawAdapter == null ? null : rawAdapter.intercept(request);
                return raw == null ? super.shouldInterceptRequest(view, request) : raw;
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                SpiderDebug.log("webhome-webview", "render process gone didCrash=%s priority=%s", detail.didCrash(), detail.rendererPriorityAtExit());
                recreateWebView();
                if (!TextUtils.isEmpty(homePage)) {
                    listener.onWebLoading();
                    loadUrl(reloadUrl(homePage, true));
                } else {
                    listener.onWebError();
                }
                return true;
            }
        };
    }

    private WebChromeClient chrome() {
        return new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                if (message != null) {
                    String line = String.format(Locale.ROOT, "%s %s:%s %s", message.messageLevel(), message.sourceId(), message.lineNumber(), message.message());
                    SpiderDebug.log("webhome-console", line);
                    listener.onWebConsole(line);
                }
                return super.onConsoleMessage(message);
            }
        };
    }

    private void injectSdk() {
        if (destroyed) return;
        injectViewport();
        webView.evaluateJavascript(getSdk(), value -> {
            if (destroyed) return;
            sdkReady = true;
            injectOpenGateHook();
        });
    }

    // ===== 桥方言源点播接管（账 html-jhs-playbtn-bridge-20261111 §六/§七）=====
    // 这类源（S1 静态打标 DialectSourceTag）把点播收敛到 fm.openUrl 或其
    // window.open('_blank') 回落上，裸 WebView 里出站即死页。接管口径三齐才动：
    // 已打标源（S1）＋S4 上下文（已进详情路由、目标同内容主域且为 detail/play
    // 形态路径、此前无桥播放调用）＋用户手势内 _blank 出站（S3，手势与详情路由
    // 的页面侧信号在钩子里判，原生侧以 from 与真实文档基址对位复核）；命中才把
    // 目标 URL 交现行嗅探会话（CustomWebView）在原站页会话内跑完签名认址，
    // 认到媒体址交原生播放器。回落（主会话裁定，账 §七留白处）：window.open 路
    // 嗅探失败→放行原出站（主视图直达目标 URL，与未接管时被同窗强转同路），
    // 不许保页冒充接管；fm.openUrl 路原路本不是导航，判据不命中或嗅探失败都
    // 无出站可放、只记日志留在原页。未命中判据的一切出站照旧放行。

    private static final String OPEN_GATE_HOOK = """
            (function(){
              if(window.__fmOpenGate)return;
              window.__fmOpenGate=true;
              var gestureAt=0;
              var mark=function(){gestureAt=Date.now();};
              ['pointerdown','touchstart','mousedown','keydown','click'].forEach(function(t){window.addEventListener(t,mark,true);});
              var rawOpen=window.open;
              var DETAIL=/detail|play|movie|video|watch/i;
              window.open=function(url,target){
                try{
                  var t=(target===undefined||target===null)?'':String(target);
                  var u=(url===undefined||url===null)?'':String(url);
                  var route=location.pathname+(location.hash||'');
                  if(t==='_blank'&&u&&Date.now()-gestureAt<=3000&&DETAIL.test(route)){
                    var verdict=fongmiBridge.gateOpenUrl(JSON.stringify({url:u,target:t,from:location.href,detail:true}));
                    if(verdict==='takeover')return null;
                  }
                }catch(e){}
                return rawOpen.apply(window,arguments);
              };
            })();
            """;

    private void refreshDialectTag() {
        dialectTag = site == null ? null : DialectSourceTag.peek(site.getKey(), site.getApi());
    }

    /** 桥调用记账（S4 的「此前无桥播放调用」证据）：任一 invoke 记 invokeSeen，点播类 invoke 记 playSeen。 */
    void noteBridgeInvoke(String method) {
        if (TextUtils.isEmpty(method)) return;
        bridgeInvokeSeen = true;
        if ("player.playUrl".equals(method) || "player.playVod".equals(method)
                || "player.playVodInline".equals(method) || "pan.play".equals(method)) bridgePlaySeen = true;
    }

    /** 只给已打标源装 window.open 出站钩子；打标未就绪时后台扫完再补装（SPA 单文档页靠这条补上）。 */
    private void injectOpenGateHook() {
        if (destroyed || site == null) return;
        refreshDialectTag();
        if (dialectTag != null) {
            if (!dialectTag.isDialect()) return;
            webView.evaluateJavascript(OPEN_GATE_HOOK, null);
            SpiderDebug.log("webhome-takeover", "gate armed site=%s diff=%s host=%s", site.getKey(), dialectTag.getDialectNames(), dialectTag.getContentHost());
            return;
        }
        if (!PluginKinds.HTML.equals(PluginKinds.kindOfKey(site.getKey()))) return;
        Site current = site;
        Task.execute(() -> {
            DialectSourceTag.resolve(current.getKey(), current.getApi());
            App.post(() -> {
                if (!destroyed && site == current) injectOpenGateHook();
            });
        });
    }

    /** 切源/销毁时作废在飞的接管：停嗅探会话、放忙旗、代次递增让旧回调自弃，防跨源相碰。 */
    private void cancelTakeover() {
        takeoverSeq++;
        takeoverBusy = false;
        if (takeoverView != null) {
            takeoverView.stop(false);
            takeoverView = null;
        }
    }

    /** window.open 钩子的同步裁决口（桥线程）：接管真启动才返回 "takeover" 吞出站，其余（判据不命中/忙）一律返回空串、钩子放行原出站。 */
    String gateOpenUrl(String payloadJson) {
        JsonObject payload = WebCall.object(payloadJson);
        String url = Json.safeString(payload, "url");
        String from = Json.safeString(payload, "from");
        boolean detail = payload.has("detail") && payload.get("detail").isJsonPrimitive() && payload.get("detail").getAsJsonPrimitive().isBoolean() && payload.get("detail").getAsBoolean();
        if (!detail) return denyTakeover("window.open", "no-detail-signal", url);
        if (!judgeTakeover(url, from, "window.open")) return "";
        return startTakeover(url, "window.open", true) ? "takeover" : "";
    }

    /** fm.openUrl 别名口：与出站接管同一判据、同一接管链（账 §七：别名只是方言兼容附带）；判据不命中即无动作——别名原路不是导航，没有可放行的出站，不许壳子替页面改道主视图。 */
    String openUrlFromBridge(JsonObject payload) {
        String url = Json.safeString(payload, "url");
        String from = Json.safeString(payload, "from");
        if (judgeTakeover(url, from, "fm.openUrl")) startTakeover(url, "fm.openUrl", false);
        return "{}";
    }

    private boolean judgeTakeover(String targetUrl, String from, String via) {
        if (destroyed || site == null) {
            denyTakeover(via, "no-site", targetUrl);
            return false;
        }
        refreshDialectTag();
        if (dialectTag == null) dialectTag = DialectSourceTag.resolve(site.getKey(), site.getApi());
        if (!dialectTag.isDialect()) {
            denyTakeover(via, "not-dialect", targetUrl);
            return false;
        }
        if (TextUtils.isEmpty(targetUrl) || !(targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))) {
            denyTakeover(via, "not-http", targetUrl);
            return false;
        }
        if (bridgePlaySeen) {
            denyTakeover(via, "bridge-play-seen", targetUrl);
            return false;
        }
        if (!isDetailRoute(from)) {
            denyTakeover(via, "not-detail-route from=" + from, targetUrl);
            return false;
        }
        // from 是页面自报：文档基址（去查询/片段）须与主视图真实在载页对得上，
        // 防页面内任意脚本伪造详情路由绕开钩子侧判定（SPA 的 hash 路由属页面
        // 自身状态、基址同文档无法再分，只认到文档这一层，余项靠目标形态与同域）。
        // 两边先 Uri.decode 归一：location.href 对中文文件名是百分号编码形，
        // lastPageUrl 是加载时的原始串，不归一主样本（鉴黄师.html）会被误杀。
        if (!TextUtils.isEmpty(lastPageUrl) && !baseOf(Uri.decode(from)).equals(baseOf(Uri.decode(lastPageUrl)))) {
            denyTakeover(via, "from-mismatch from=" + from + " page=" + lastPageUrl, targetUrl);
            return false;
        }
        if (!isDetailRoute(targetUrl)) {
            denyTakeover(via, "target-not-detail-form", targetUrl);
            return false;
        }
        String contentHost = dialectTag.getContentHost();
        if (TextUtils.isEmpty(contentHost)) {
            denyTakeover(via, "no-content-host", targetUrl);
            return false;
        }
        String host = UrlUtil.host(Uri.parse(targetUrl));
        if (TextUtils.isEmpty(host) || !(host.equals(contentHost) || host.endsWith("." + contentHost))) {
            denyTakeover(via, "cross-host host=" + host + " content=" + contentHost, targetUrl);
            return false;
        }
        SpiderDebug.log("webhome-takeover", "judge pass via=%s site=%s invokeSeen=%s url=%s", via, site.getKey(), bridgeInvokeSeen, targetUrl);
        return true;
    }

    private String denyTakeover(String via, String reason, String url) {
        SpiderDebug.log("webhome-takeover", "deny via=%s reason=%s url=%s", via, reason, url);
        return "";
    }

    /** S4 详情形态：路径＋片段（＋查询）里带 detail/play/movie/video/watch 任一词；来源路由与目标 URL 两侧都验。 */
    private boolean isDetailRoute(String url) {
        String route = routePart(url).toLowerCase(Locale.ROOT);
        return route.contains("detail") || route.contains("play") || route.contains("movie")
                || route.contains("video") || route.contains("watch");
    }

    private static String routePart(String url) {
        if (TextUtils.isEmpty(url)) return "";
        int scheme = url.indexOf("://");
        int start = scheme >= 0 ? url.indexOf('/', scheme + 3) : 0;
        return start >= 0 ? url.substring(start) : url;
    }

    private static String baseOf(String url) {
        if (TextUtils.isEmpty(url)) return "";
        int cut = url.length();
        int hash = url.indexOf('#');
        if (hash >= 0) cut = Math.min(cut, hash);
        int query = url.indexOf('?');
        if (query >= 0) cut = Math.min(cut, query);
        return url.substring(0, cut);
    }

    /**
     * 启动嗅探接管；返回 false 表示没接下（已销毁/无源/已有接管在飞），调用方须
     * 放行原出站、不许冒充接管。releaseOnError＝嗅探失败时是否放行原出站
     * （window.open 路为真；fm.openUrl 路原路不是导航、为假，只记日志）。
     */
    private boolean startTakeover(String targetUrl, String via, boolean releaseOnError) {
        final Site current;
        final int seq;
        final Map<String, String> headers;
        synchronized (this) {
            if (destroyed || site == null) return false;
            if (takeoverBusy) {
                SpiderDebug.log("webhome-takeover", "busy skip via=%s url=%s", via, targetUrl);
                return false;
            }
            takeoverBusy = true;
            seq = ++takeoverSeq;
            current = site;
            headers = current.getHeader() == null ? new HashMap<>() : new HashMap<>(current.getHeader());
        }
        String siteKey = current.getKey();
        SpiderDebug.log("webhome-takeover", "takeover start via=%s site=%s url=%s", via, siteKey, targetUrl);
        App.post(() -> {
            if (destroyed || seq != takeoverSeq) return;
            try {
                ParseCallback callback = new ParseCallback() {
                    @Override
                    public void onParseSuccess(Map<String, String> sniffHeaders, String mediaUrl, String from) {
                        if (!finishTakeover(seq)) return;
                        String playUrl = mediaUrl;
                        if (sniffHeaders != null && !sniffHeaders.isEmpty() && bridge != null) {
                            // 与 fm.play 带头播放同形：认址回的头（UA/Referer 等）经 /webResource 代理带给播放器。
                            JsonObject options = new JsonObject();
                            options.add("headers", App.gson().toJsonTree(sniffHeaders));
                            playUrl = bridge.resourceUrl(mediaUrl, options.toString());
                        }
                        String title = current.getName();
                        SpiderDebug.log("webhome-takeover", "sniff ok site=%s media=%s", siteKey, mediaUrl);
                        String playTitle = TextUtils.isEmpty(title) ? playUrl : title;
                        String startUrl = playUrl;
                        App.post(() -> {
                            if (!destroyed && seq == takeoverSeq) VideoActivity.start(activity, SiteApi.PUSH, startUrl, playTitle, null, null, null, null);
                        });
                    }

                    @Override
                    public void onParseError() {
                        if (!finishTakeover(seq)) return;
                        if (releaseOnError) {
                            SpiderDebug.log("webhome-takeover", "sniff failed site=%s fallback url=%s", siteKey, targetUrl);
                            App.post(() -> {
                                if (!destroyed && seq == takeoverSeq) webView.loadUrl(targetUrl);
                            });
                        } else {
                            SpiderDebug.log("webhome-takeover", "sniff failed site=%s via=%s dropped url=%s", siteKey, via, targetUrl);
                        }
                    }
                };
                CustomWebView view = CustomWebView.create(App.get());
                takeoverView = view;
                view.start(siteKey, "", headers, targetUrl, "", callback, true);
                synchronized (this) {
                    if (seq == takeoverSeq) bridgePlaySeen = true;
                }
            } catch (Throwable e) {
                SpiderDebug.log("webhome-takeover", "sniff start failed site=%s error=%s", siteKey, e.getMessage());
                CustomWebView stuck;
                synchronized (this) {
                    stuck = seq == takeoverSeq ? takeoverView : null;
                    if (seq == takeoverSeq) {
                        takeoverBusy = false;
                        takeoverView = null;
                    }
                }
                if (stuck != null) stuck.stop(false);
                if (releaseOnError && !destroyed) {
                    App.post(() -> {
                        if (!destroyed && seq == takeoverSeq) webView.loadUrl(targetUrl);
                    });
                }
            }
        });
        return true;
    }

    /** 接管回调认领：代次仍当前且未销毁才算数，顺手放忙旗、摘视图引用；切源/销毁后的过期回调自弃返回 false。 */
    private boolean finishTakeover(int seq) {
        synchronized (this) {
            if (destroyed || seq != takeoverSeq) return false;
            takeoverBusy = false;
            takeoverView = null;
            return true;
        }
    }

    private void injectViewport() {
        if (destroyed) return;
        if (webView.getWidth() <= 0 || webView.getHeight() <= 0) return;
        String key = viewport.key(density, webView.getWidth(), webView.getHeight());
        if (key.equals(lastViewportKey)) return;
        lastViewportKey = key;
        String script = viewport.script(density, webView.getWidth(), webView.getHeight());
        webView.post(() -> {
            if (!destroyed) webView.evaluateJavascript(script, null);
        });
    }

    private String reloadUrl(String url) {
        return reloadUrl(url, false);
    }

    private String reloadUrl(String url, boolean restore) {
        try {
            Uri.Builder builder = Uri.parse(url).buildUpon().appendQueryParameter("_fm_reload", String.valueOf(System.currentTimeMillis()));
            if (restore) builder.appendQueryParameter("_fm_restore", "1");
            return builder.build().toString();
        } catch (Throwable e) {
            return url + (url.contains("?") ? "&" : "?") + "_fm_reload=" + System.currentTimeMillis() + (restore ? "&_fm_restore=1" : "");
        }
    }

    private String getHomePage(Site site) {
        String url = site.webHomeUrl();
        if (UrlUtil.scheme(url).isEmpty()) url = UrlUtil.resolve(VodConfig.getUrl(), url);
        return UrlUtil.convert(url);
    }

    private String getSdk() {
        return String.format(Locale.ROOT, """
                (function(){
                  if(window.fm&&window.fongmi){window.dispatchEvent(new CustomEvent('fmsdk'));return;}
                  if(document&&document.documentElement)document.documentElement.classList.add('fm-native');
                  window.fongmiClient={mode:'%s',isLeanback:%s};
                  const callbacks={};
                  let seq=0;
                  function invoke(method,payload){
                    return new Promise((resolve,reject)=>{
                      const id='fm_'+Date.now()+'_'+(++seq);
                      callbacks[id]={resolve,reject};
                      fongmiBridge.invoke(id,method,JSON.stringify(payload||{}));
                    });
                  }
                  function hydrate(data){
                    if(!data||!data.__fmResultId)return data;
                    const resultId=data.__fmResultId;
                    const length=fongmiBridge.resultLength(resultId);
                    let text='';
                    for(let start=0;start<length;start+=60000)text+=fongmiBridge.resultChunk(resultId,start);
                    fongmiBridge.clearResult(resultId);
                    return JSON.parse(text);
                  }
                  window.fongmiNative={
                    resolve:(id,data)=>{ if(callbacks[id]){ callbacks[id].resolve(hydrate(data)); delete callbacks[id]; } },
                    reject:(id,error)=>{ if(callbacks[id]){ callbacks[id].reject(new Error(error||'')); delete callbacks[id]; } }
                  };
                  if(!window.__fmUrlHook&&window.history){
                    window.__fmUrlHook=true;
                    const emit=()=>window.dispatchEvent(new CustomEvent('fmurlchange',{detail:{url:location.href}}));
                    const rawPush=history.pushState;
                    const rawReplace=history.replaceState;
                    history.pushState=function(){const r=rawPush.apply(this,arguments);emit();return r;};
                    history.replaceState=function(){const r=rawReplace.apply(this,arguments);emit();return r;};
                    window.addEventListener('popstate',emit);
                  }
                  %s
                  const player={
                    playUrl:(url,title,options)=>invoke('player.playUrl',Object.assign({},options||{},{url,title})),
                    playVod:(siteKey,vodId,title,pic,options)=>invoke('player.playVod',Object.assign({},options||{},{siteKey,vodId,title,pic})),
                    playVodInline:(payload)=>invoke('player.playVodInline',payload||{}),
                    preloadArtwork:(pic,wallPic)=>invoke('player.preloadArtwork',{pic,wallPic}),
                    control:(action)=>invoke('player.control',{action}),
                    status:()=>invoke('player.status',{})
                  };
                  const net={
                    request:(url,options)=>invoke('net.request',Object.assign({},options||{},{url})),
                    resourceUrl:(url,options)=>fongmiBridge.resourceUrl(url,JSON.stringify(options||{}))
                  };
                  const cache={
                    get:(key,rule)=>invoke('cache.get',{key,rule}),
                    set:(key,value,rule)=>invoke('cache.set',{key,value,rule}),
                    del:(key,rule)=>invoke('cache.del',{key,rule})
                  };
                  const pan={
                    check:(items)=>invoke('pan.check',{items}),
                    play:(payload)=>invoke('pan.play',payload||{})
                  };
                  const ui={
                    setToolbar:(visible)=>invoke('ui.setToolbar',{visible:visible!==false}),
                    setChrome:(options)=>invoke('ui.setChrome',options||{}),
                    restoreChrome:()=>invoke('ui.restoreChrome',{}),
                    getViewport:()=>invoke('ui.getViewport',{})
                  };
                  window.fongmi={invoke,player,net,cache,
                    app:{
                      search:(keyword,options)=>invoke('app.search',Object.assign({},options||{},{keyword})),
                      openVod:()=>invoke('app.openVod',{}),
                      openLive:()=>invoke('app.openLive',{}),
                      openKeep:()=>invoke('app.openKeep',{}),
                      openSetting:()=>invoke('app.openSetting',{}),
                      history:()=>invoke('app.history',{})
                    },
                    pan,
                    device:{info:()=>invoke('device.info',{})},
                    site:{info:()=>invoke('site.info',{})},
                    config:{info:()=>invoke('config.info',{})},
                    ui,
                    navigation:{
                      back:()=>invoke('navigation.back',{}),
                      reload:()=>invoke('navigation.reload',{})
                    }
                  };
                  window.fm={
                    req:net.request,
                    res:net.resourceUrl,
                    play:player.playUrl,
                    vod:player.playVod,
                    vodInline:player.playVodInline,
                    preloadArtwork:player.preloadArtwork,
                    ctrl:player.control,
                    stat:player.status,
                    search:window.fongmi.app.search,
                    openVod:window.fongmi.app.openVod,
                    openLive:window.fongmi.app.openLive,
                    openKeep:window.fongmi.app.openKeep,
                    openSetting:window.fongmi.app.openSetting,
                    history:window.fongmi.app.history,
                    pan,
                    check:window.fongmi.pan.check,
                    cache,
                    ui,
                    device:window.fongmi.device.info,
                    site:window.fongmi.site.info,
                    config:window.fongmi.config.info,
                    back:window.fongmi.navigation.back,
                    reload:window.fongmi.navigation.reload,
                    openUrl:(url)=>invoke('player.openUrl',{url:String(url||''),from:location.href}),
                    toast:(message)=>invoke('ext.toast',{message:String(message||'')})
                  };
                  window.dispatchEvent(new CustomEvent('fmsdk'));
                })();
                """, com.fongmi.android.tv.BuildConfig.FLAVOR_mode, com.fongmi.android.tv.utils.Util.isLeanback(), debugTools ? debugSdkHook() : "");
    }

    private String debugSdkHook() {
        return """
                  if(!window.__fmConsoleHook){
                    window.__fmConsoleHook=true;
                    ['log','info','warn','error','debug'].forEach(function(level){
                      const raw=console[level]||console.log;
                      console[level]=function(){
                        const args=Array.prototype.slice.call(arguments);
                        try{fongmiBridge.console(level,args.map(function(v){try{return typeof v==='string'?v:JSON.stringify(v);}catch(e){return String(v);}}).join(' '));}catch(e){}
                        return raw&&raw.apply(console,args);
                      };
                    });
                  }
                  if(!window.__fmNetworkHook){
                    window.__fmNetworkHook=true;
                    const absolute=function(url){try{return new URL(String(url),location.href).href;}catch(e){return String(url||'');}};
                    const clip=function(value){value=String(value||'');return value.length>2000?value.slice(0,2000)+'\\n...truncated':value;};
                    const bodyText=function(body){
                      if(!body)return '';
                      if(typeof body==='string')return 'payload:\\n'+clip(body);
                      if(body instanceof URLSearchParams)return 'payload:\\n'+clip(body.toString());
                      if(body instanceof FormData){
                        const out=[];
                        try{body.forEach(function(v,k){out.push(k+'='+(v&&v.name?'[file '+v.name+']':String(v)));});}catch(e){}
                        return 'payload:\\n'+clip(out.join('\\n'));
                      }
                      return 'payloadType='+Object.prototype.toString.call(body)+'\\npayloadBytes='+String(body).length;
                    };
                    const headers=function(headers){
                      const out=[];
                      try{
                        if(headers&&headers.forEach)headers.forEach(function(v,k){out.push(k+': '+v);});
                        else if(headers)Object.keys(headers).forEach(function(k){out.push(k+': '+headers[k]);});
                      }catch(e){}
                      return out.join('\\n');
                    };
                    const rawFetch=window.fetch;
                    if(rawFetch){
                      window.fetch=function(input,init){
                        const started=Date.now();
                        const method=(init&&init.method)||(input&&input.method)||'GET';
                        const url=absolute(input&&input.url?input.url:input);
                        const requestHeaders=headers((init&&init.headers)||(input&&input.headers));
                        const body=bodyText(init&&init.body);
                        try{fongmiBridge.network('FETCH_START',method,url,0,0,[requestHeaders,body].filter(Boolean).join('\\n'));}catch(e){}
                        return rawFetch.apply(this,arguments).then(function(resp){
                          try{fongmiBridge.network('FETCH_DONE',method,url,resp.status||0,Date.now()-started,['type='+(resp.type||''),'headers:',headers(resp.headers)].join('\\n'));}catch(e){}
                          return resp;
                        }).catch(function(err){
                          try{fongmiBridge.network('FETCH_ERROR',method,url,0,Date.now()-started,String(err&&err.message||err));}catch(e){}
                          throw err;
                        });
                      };
                    }
                    const RawXHR=window.XMLHttpRequest;
                    if(RawXHR&&RawXHR.prototype){
                      const rawOpen=RawXHR.prototype.open;
                      const rawSend=RawXHR.prototype.send;
                      RawXHR.prototype.open=function(method,url){
                        this.__fmMethod=method||'GET';
                        this.__fmUrl=absolute(url);
                        return rawOpen.apply(this,arguments);
                      };
                      RawXHR.prototype.send=function(){
                        const xhr=this;
                        const started=Date.now();
                        const body=arguments.length?bodyText(arguments[0]):'';
                        try{fongmiBridge.network('XHR_START',xhr.__fmMethod||'GET',xhr.__fmUrl||'',0,0,body);}catch(e){}
                        xhr.addEventListener('loadend',function(){
                          try{fongmiBridge.network('XHR_DONE',xhr.__fmMethod||'GET',xhr.__fmUrl||'',xhr.status||0,Date.now()-started,[xhr.statusText||'',xhr.getAllResponseHeaders&&xhr.getAllResponseHeaders()||''].filter(Boolean).join('\\n'));}catch(e){}
                        });
                        xhr.addEventListener('error',function(){
                          try{fongmiBridge.network('XHR_ERROR',xhr.__fmMethod||'GET',xhr.__fmUrl||'',xhr.status||0,Date.now()-started,'error');}catch(e){}
                        });
                        return rawSend.apply(this,arguments);
                      };
                    }
                  }
                """;
    }

    public interface Listener {

        void onWebLoading();

        void onWebReady();

        void onWebError();

        default void setToolbar(boolean visible) {
        }

        default void applyDefaultChrome(Site site) {
        }

        default void setChrome(JsonObject payload) {
        }

        default void restoreChrome() {
        }

        default WebHomeViewport getViewport() {
            return WebHomeViewport.EMPTY;
        }

        default void openVod() {
        }

        default void openSetting() {
        }

        default void onWebConsole(String line) {
        }

        default void onWebRequest(String method, String url, boolean mainFrame) {
        }

        default void onWebRequest(String method, String url, boolean mainFrame, Map<String, String> headers) {
            onWebRequest(method, url, mainFrame);
        }

        default void onWebNetwork(String type, String method, String url, int status, long durationMs, String detail) {
        }
    }
}
