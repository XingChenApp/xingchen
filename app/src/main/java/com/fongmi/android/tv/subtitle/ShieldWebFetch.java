package com.fongmi.android.tv.subtitle;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.WebViewUtil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 过盾取页：用 WebView（真浏览器栈）在后台加载页面，轮询源码，
 * 盾页特征消失后返回渲染后的 HTML。
 *
 * 为什么不用「WebView 验证＋OkHttp 带 Cookie 重放」：Cloudflare 的
 * 通行证绑访问指纹，OkHttp 的请求指纹与浏览器不同，验证过后
 * 搜索照样被拦（潇洒哥真机实证「过验证还是不行」，2026-10-31）。
 * 让请求本身从浏览器栈发出，盾才不会拦。取到的 Cookie 顺手存回
 * ShieldBypass，给详情页 ajax 这类次级请求复用。
 *
 * 必须在后台线程调用（内部同步等主线程 WebView）；主线程调用直接返回 null。
 */
public final class ShieldWebFetch {

    private ShieldWebFetch() {
    }

    /** 轮询间隔（ms）：等盾页自解/跳转的节奏。 */
    private static final int POLL_MS = 800;

    public static String fetch(String providerId, String url, int timeoutMs) {
        if (TextUtils.isEmpty(url) || Looper.myLooper() == Looper.getMainLooper()) return null;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>(null);
        Handler main = new Handler(Looper.getMainLooper());
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 3000);
        main.post(() -> {
            WebView wv = null;
            try {
                wv = new WebView(App.get());
                WebViewUtil.configureBase(wv, "shield-fetch");
                wv.getSettings().setUserAgentString(ShieldBypass.userAgent(providerId));
                CookieManager cm = CookieManager.getInstance();
                cm.setAcceptCookie(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cm.setAcceptThirdPartyCookies(wv, true);
                }
                WebView finalWv = wv;
                wv.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String u) {
                        poll(view, providerId, url, deadline, result, latch);
                    }
                });
                wv.loadUrl(url);
                // 兜底：onPageFinished 不回来（纯盾页卡住）时也开始轮询，
                // 盾页自身的 JS 跳转后源码自然变真页。
                main.postDelayed(() -> {
                    if (latch.getCount() > 0) poll(finalWv, providerId, url, deadline, result, latch);
                }, 2500);
            } catch (Throwable t) {
                latch.countDown();
                if (wv != null) destroyQuietly(wv);
            }
        });
        try {
            latch.await(Math.max(timeoutMs, 3000) + 2000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result.get();
    }

    /** 在主线程上轮询页面源码：非盾页即收口；超时把最后一次源码（若非盾）交回。 */
    private static void poll(WebView wv, String providerId, String url, long deadline,
                             AtomicReference<String> result, CountDownLatch latch) {
        if (latch.getCount() == 0) return;
        try {
            wv.evaluateJavascript("(function(){return document.documentElement.outerHTML;})();", raw -> {
                if (latch.getCount() == 0) return;
                String html = unquote(raw);
                if (!TextUtils.isEmpty(html) && !ShieldBypass.isShieldPage(html)) {
                    finish(wv, providerId, url, html, result, latch);
                    return;
                }
                if (System.currentTimeMillis() >= deadline) {
                    finish(wv, providerId, url, null, result, latch);
                    return;
                }
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> poll(wv, providerId, url, deadline, result, latch), POLL_MS);
            });
        } catch (Throwable t) {
            finish(wv, providerId, url, null, result, latch);
        }
    }

    private static void finish(WebView wv, String providerId, String url, String html,
                               AtomicReference<String> result, CountDownLatch latch) {
        if (latch.getCount() == 0) return;
        try {
            CookieManager cm = CookieManager.getInstance();
            try {
                cm.flush();
            } catch (Throwable ignored) {
            }
            String current = wv.getUrl();
            String cookieUrl = !TextUtils.isEmpty(current) ? current : url;
            String cookie = cm.getCookie(cookieUrl);
            if (!TextUtils.isEmpty(cookie)) {
                ShieldBypass.saveCookie(ShieldBypass.hostOf(cookieUrl), cookie);
            }
        } catch (Throwable ignored) {
        }
        result.set(html);
        latch.countDown();
        destroyQuietly(wv);
    }

    private static void destroyQuietly(WebView wv) {
        try {
            wv.stopLoading();
            wv.setWebViewClient(null);
            wv.destroy();
        } catch (Throwable ignored) {
        }
    }

    /** evaluateJavascript 的返回是 JSON 转义字符串，去引号还原。 */
    private static String unquote(String raw) {
        if (raw == null || raw.equals("null")) return null;
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            try {
                return new org.json.JSONTokener(raw).nextString('"');
            } catch (Throwable t) {
                return raw.substring(1, raw.length() - 1);
            }
        }
        return raw;
    }
}
