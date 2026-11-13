package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.content.Context;
import android.webkit.WebView;

import com.fongmi.android.tv.api.hk.HkParserKit;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import android.util.Log;

/**
 * 照 8.83 com.example.hikerview.service.parser.X5WebViewParser 转写（单例 + ticket 校验 + parse/finishParseInner）。
 * url 形态：x5Rule://页面地址@取址JS表达式，extra 为条目 extra(JSON)。
 * 环境差异（已注明）：8.83 内置腾讯 TBS SDK 用 com.tencent.smtt.sdk.WebView，星辰未集成 TBS；
 * TBS 内核未加载时 smtt WebView 本就回落系统 WebView，故此处与原版 ArticleWebkitHolder 同路用系统 WebView，
 * generateExtra 的 type 仍传 "x5"（DestroyEvent 按 mode 路由回本类 finishParse）。
 * 原版 QbSdk.forceSysWebView/unForceSysWebView（disableX5 时）在此无对应物，语义已被系统 WebView 天然覆盖。
 * parse 内 setX5Extra 的调用顺序照 X5 原版（在 setWebView/initWebView 之前）。
 */
public class HkX5WebViewParser extends HkIWebViewParser {
    private static final String TAG = "HkX5WebViewParser";

    private static volatile HkX5WebViewParser instance;

    private HkWebkitHolder webViewHolder;

    public static boolean canParse(String url) {
        return HkStringUtil.isNotEmpty(url) && url.startsWith("x5Rule://");
    }

    public static synchronized void destroy0() {
        if (instance != null) {
            instance.destroy();
            instance = null;
        }
    }

    public static synchronized boolean finishParse(Context context, String url, String ticket) {
        if (instance != null) {
            if (String.valueOf(instance.hashCode()).equals(ticket)) {
                Log.d(TAG, String.format("finishParse, ticket: %s", ticket));
                return instance.finishParseInner(context, url);
            }
            Log.d(TAG, String.format("ticket已失效: %s, new: %s", ticket, String.valueOf(instance.hashCode())));
        }
        return false;
    }

    public static HkX5WebViewParser newInstance() {
        if (instance != null) instance.destroy();
        instance = new HkX5WebViewParser();
        return instance;
    }

    public static synchronized boolean parse0(Activity activity, String url, String extra,
                                              Consumer<String> consumer) {
        if (!canParse(url)) return false;
        destroy0();
        return newInstance().parse(activity, url, extra, consumer);
    }

    @Override
    public void destroy() {
        if (webViewHolder != null && webViewHolder.getWebView() != null) {
            webViewHolder.onDestroy();
            webViewHolder = null;
        }
    }

    @Override
    public boolean finishParseInner(Context context, String url) {
        if (webViewHolder != null && webViewHolder.getWebView() != null) {
            destroy();
            if (consumer != null) consumer.accept(url);
            if (HkStringUtil.isEmpty(url)) {
                HkRouteBlocker.toastShortBottom(context, "解析失败，链接为空");
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean parse(Activity activity, String url, String extra, Consumer<String> consumer) {
        String stripped = HkStringUtil.replaceOnceIgnoreCase(url, "x5Rule://", "");
        String[] parts = stripped.split("@");
        destroy();
        if (parts.length < 2) {
            HkRouteBlocker.toastShortBottom(activity, "规则有误！" + url);
            return false;
        }
        this.consumer = consumer;
        String target = parts[0];
        String jsExpr = HkParserKit.arrayToString(parts, 1, "@");
        String ticket = String.valueOf(hashCode());
        HkX5Extra x5Extra = generateExtra(extra, jsExpr, "x5", ticket);
        // 原版：x5Extra.isDisableX5() 时 QbSdk.forceSysWebView() 后建 smtt WebView、再 unForceSysWebView()；星辰恒系统 WebView，等价覆盖。
        WebView webView = new WebView(activity);
        webViewHolder = new HkWebkitHolder();
        webViewHolder.setSniffMode(true);
        webViewHolder.setX5Extra(x5Extra);
        webViewHolder.setWebView(webView);
        webViewHolder.initWebView(activity);
        // G43：解析器即官 WebkitExecutor 位，补注册 fy_bridge_appd 并供给当前规则运行时作桥引擎面
        webViewHolder.registerExecutorBridge();
        try {
            com.fongmi.android.tv.api.hk.HkRule cur = HkEngineManager.currentRule();
            if (cur != null) {
                com.fongmi.android.tv.api.hk.HkJsRuntime brt = HkEngineManager.runtimeIfPresent(cur);
                if (brt != null) webViewHolder.setBridgeEngine(() -> brt);
            }
        } catch (Throwable ignored) {
        }
        if (HkStringUtil.isNotEmpty(x5Extra.getUa())) {
            webViewHolder.getWebView().getSettings().setUserAgentString(x5Extra.getUa());
        }
        if (HkStringUtil.isNotEmpty(x5Extra.getReferer())) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", x5Extra.getReferer());
            webViewHolder.getWebView().loadUrl(target, headers);
        } else {
            webViewHolder.getWebView().loadUrl(target);
        }
        return true;
    }
}
