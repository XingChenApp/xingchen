package com.fongmi.android.tv.ui.activity;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Web;
import com.fongmi.android.tv.databinding.ActivityWebBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

// 蜘蛛开网页面（模板考试缺口④补齐）：
// - 现行面照 FongMi：openWeb(String json)（bean.Web 口径校验）＋getUserAgent()。
// - 模板兼容面：open(url)／open(url, cookieName)，内部组装成 Web 再走同一条开页路。
// - 页内只做「开网页＋cookie 登录监视」：FongMi 的 BrowserWebView 全屏视频、
//   文件选择、下载、系统栏取色、spider.html 特殊页不在本面内（见 template-gaps-fix.md）。
public final class WebActivity extends BaseActivity {

    private static final String EXTRA_WEB = "web";

    private ActivityWebBinding binding;
    private WebView webView;
    private Web options;
    private final Runnable loginCheck = this::checkLogin;

    public static boolean openWeb(String json) {
        return openWeb(App.activity(), Web.objectFrom(json));
    }

    // 动作结果的地板消费口（照 FongMi WebActivity.handleAction 原文复原）：
    // 结果带 web 就开页（开不成弹错收口），再弹 msg。动作协议的其余键
    // （toast/list/refresh/指令/表单）由 ActionResult 在同一观察链里消费。
    public static void handleAction(Activity activity, Result result) {
        if (result == null) return;
        Web options = result.takeWeb();
        if (options != null && !openWeb(activity, options)) {
            Notify.show(R.string.error_file_open);
            return;
        }
        Notify.show(result.getMsg());
    }

    private static boolean openWeb(Activity activity, Web options) {
        if (activity == null || options == null || !options.isValid()) return false;
        App.post(() -> start(activity, options));
        return true;
    }

    public static boolean open(String url) {
        return open(url, null);
    }

    public static boolean open(String url, String cookieName) {
        if (url == null || url.isEmpty()) return false;
        JsonObject json = new JsonObject();
        json.addProperty("url", url);
        if (cookieName != null && !cookieName.isEmpty()) {
            JsonObject group = new JsonObject();
            group.addProperty("url", url);
            JsonArray keys = new JsonArray();
            keys.add(cookieName);
            group.add("keys", keys);
            JsonArray cookie = new JsonArray();
            cookie.add(group);
            json.add("cookie", cookie);
        }
        return openWeb(json.toString());
    }

    public static String getUserAgent() {
        String userAgent = Setting.getUa();
        return userAgent.isEmpty() ? WebSettings.getDefaultUserAgent(App.get()) : userAgent;
    }

    private static void start(Activity activity, Web options) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        Intent intent = new Intent(activity, WebActivity.class).putExtra(EXTRA_WEB, App.gson().toJson(options));
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = ActivityWebBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        options = Web.objectFrom(getIntent().getStringExtra(EXTRA_WEB));
        if (options == null) {
            finish();
            return;
        }
        options.snapshot(url -> CookieManager.getInstance().getCookie(url));
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(getUserAgent());
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient());
        binding.container.addView(webView, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        webView.requestFocus();
        webView.loadUrl(options.getUrl());
        if (options.hasCookies()) webView.postDelayed(loginCheck, 1000);
    }

    private void checkLogin() {
        if (webView == null || options == null || !options.hasCookies() || isFinishing()) return;
        if (options.isComplete(webView.getUrl(), url -> CookieManager.getInstance().getCookie(url))) {
            CookieManager.getInstance().flush();
            finish();
            return;
        }
        webView.postDelayed(loginCheck, 1000);
    }

    @Override
    protected void onBackInvoked() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackInvoked();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.removeCallbacks(loginCheck);
            if (options != null && options.hasCookies()) webView.postDelayed(loginCheck, 1000);
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.removeCallbacks(loginCheck);
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeCallbacks(loginCheck);
            binding.container.removeView(webView);
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
