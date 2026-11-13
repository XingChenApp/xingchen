package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.cloud.drivepikpak.DrivePikPak;
import com.fongmi.android.tv.databinding.ActivityPikpakWebLoginBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PanAuth;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * PikPak 网页登录。
 *
 * 背景：OkHttp 直调 user.mypikpak.net/v1/auth/signin 被风控在 TCP 层掐连接
 * （connection closed），而 WebView 走 Chromium 网络栈、TLS 指纹与 Chrome 一致，
 * 可正常打开 PikPak 网页版并完成登录（含滑块验证）。
 *
 * 流程：WebView 加载 mypikpak.com → 用户在网页内完成登录 →
 * 定时扫描 localStorage 中 credentials* 项 → 提取 refresh_token
 * （网页与 App 共用同一 OAuth client_id，refresh_token 可直接复用 App 的刷新接口）→
 * 经 DrivePikPak.importWebSession() 存入 CloudAccountStore，后续 API 照旧走 OkHttp。
 */
public class PikPakWebLoginActivity extends BaseActivity {

    private static final String TAG = "PikPakWebLogin";
    private static final String LOGIN_URL = "https://mypikpak.com/";
    private static final long POLL_INTERVAL_MS = 2000;

    /** 扫描 localStorage 里的 credentials* 凭证 + document.cookie，经 JS 接口回传 */
    private static final String SCAN_JS = "(function(){"
            + "try{"
            + "var creds=[];"
            + "for(var i=0;i<localStorage.length;i++){"
            + "var k=localStorage.key(i);"
            + "if(k&&k.indexOf('credentials')===0){creds.push({k:k,v:localStorage.getItem(k)});}"
            + "}"
            + "if(window.XingChenPikPak){window.XingChenPikPak.onScanResult(JSON.stringify({creds:creds,cookie:document.cookie}));}"
            + "}catch(e){}"
            + "})();";

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, PikPakWebLoginActivity.class));
    }

    private ActivityPikpakWebLoginBinding binding;
    private WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean done = false;

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityPikpakWebLoginBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        web = binding.webPikpak;
        binding.btnPikpakWebClose.setOnClickListener(v -> finish());
        initWebView();
        web.loadUrl(LOGIN_URL);
        handler.postDelayed(pollTask, POLL_INTERVAL_MS);
    }

    private void initWebView() {
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);   // localStorage 必需
        ws.setDatabaseEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setSupportMultipleWindows(false);
        // 正常 Chrome 移动端 UA（去掉 "; wv" 标记，否则可能被风控拦截）
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        try {
            cm.setAcceptThirdPartyCookies(web, true);
        } catch (Exception ignored) {}
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onScanResult(String json) {
                if (!done) parseAndImport(json);
            }
        }, "XingChenPikPak");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 进入 /drive 说明已登录，立即扫一次，缩短等待
                if (!done && url != null && url.contains("/drive")) {
                    view.evaluateJavascript(SCAN_JS, null);
                }
            }
        });
    }

    /** 定时轮询：登录成功不依赖 URL 跳转规则，PikPak 改版登录页也不易失效 */
    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (done || web == null) return;
            try {
                web.evaluateJavascript(SCAN_JS, null);
            } catch (Exception ignored) {}
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    /**
     * 解析 JS 回传的扫描结果。运行在 JS 接口的后台线程。
     * 找到 refresh_token 即判定登录成功，停掉轮询并导入会话。
     */
    private void parseAndImport(String json) {
        if (done || TextUtils.isEmpty(json)) return;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray creds = root.optJSONArray("creds");
            String refreshToken = "";
            String accessToken = "";
            String userId = "";
            long expiresIn = 0;
            if (creds != null) {
                for (int i = 0; i < creds.length(); i++) {
                    JSONObject item = creds.optJSONObject(i);
                    if (item == null) continue;
                    String v = item.optString("v");
                    if (TextUtils.isEmpty(v)) continue;
                    try {
                        JSONObject c = new JSONObject(v);
                        String rt = c.optString("refresh_token");
                        if (TextUtils.isEmpty(rt)) continue;
                        refreshToken = rt;
                        accessToken = c.optString("access_token");
                        expiresIn = c.optLong("expires_in", 0);
                        userId = c.optString("user_id",
                                c.optString("userId", c.optString("sub", "")));
                        break;
                    } catch (Exception ignored) {}
                }
            }
            if (TextUtils.isEmpty(refreshToken)) return; // 还没登录，继续轮询
            if (TextUtils.isEmpty(userId)) {
                // user_id 兜底：cookie 里的 pp_user_id
                userId = parseCookie(root.optString("cookie"), "pp_user_id");
            }
            done = true;
            handler.removeCallbacks(pollTask);
            final String rt = refreshToken;
            final String at = accessToken;
            final String uid = userId;
            final long exp = expiresIn;
            runOnUiThread(() -> binding.tvPikpakWebStatus.setText("已获取登录凭证，正在导入…"));
            new Thread(() -> doImport(rt, at, exp, uid)).start();
        } catch (Exception e) {
            android.util.Log.w(TAG, "scan result parse failed", e);
        }
    }

    private static String parseCookie(String cookieHeader, String name) {
        if (TextUtils.isEmpty(cookieHeader) || TextUtils.isEmpty(name)) return "";
        for (String pair : cookieHeader.split(";")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            if (pair.substring(0, eq).trim().equals(name)) {
                return pair.substring(eq + 1).trim();
            }
        }
        return "";
    }

    /** 后台线程：导入会话；网页凭证里没有 access_token 时用 refresh_token 换一个 */
    private void doImport(String refreshToken, String accessToken, long expiresIn, String userId) {
        try {
            DrivePikPak drive = new DrivePikPak(this);
            drive.importWebSession(refreshToken, accessToken, expiresIn, userId);
            if (TextUtils.isEmpty(accessToken)) {
                try {
                    drive.refresh();
                } catch (Exception re) {
                    // 刷新失败（可能 OkHttp 连 token 接口也被风控）：把 refresh_token 存回去，
                    // 登录态保留，后续 API 按降级策略处理
                    android.util.Log.w(TAG, "refresh after import failed, keep refresh_token", re);
                    drive.importWebSession(refreshToken, "", expiresIn, userId);
                }
            }
            PanAuth.invalidatePikPakCache();
            runOnUiThread(this::showSuccessTip);
        } catch (Exception e) {
            android.util.Log.w(TAG, "import session failed", e);
            runOnUiThread(() -> {
                binding.tvPikpakWebStatus.setText("导入失败：" + e.getMessage());
                Notify.show("导入失败：" + e.getMessage());
            });
        }
    }

    /**
     * R1 缓解：refresh_token 是一次性的，刷新后旧 token 作废。
     * 导入成功后提醒用户不要同时在网页端操作，否则 App 会提示重新登录。
     */
    private void showSuccessTip() {
        if (isFinishing() || isDestroyed()) return;
        setResult(RESULT_OK);
        new MaterialAlertDialogBuilder(this, com.fongmi.android.tv.R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle("PikPak 登录成功")
                .setMessage("已通过网页登录导入 PikPak 会话。\n\n注意：App 与网页共用同一登录凭证，刷新后旧凭证会作废。请尽量不要同时在网页端操作 PikPak，否则 App 会提示重新登录。")
                .setPositiveButton("知道了", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(pollTask);
        if (web != null) {
            try {
                web.stopLoading();
                ViewGroup parent = (ViewGroup) web.getParent();
                if (parent != null) parent.removeView(web);
                web.destroy();
            } catch (Exception ignored) {}
            web = null;
        }
        super.onDestroy();
    }
}
