package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.content.Context;
import android.webkit.WebView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.HkParserKit;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import android.util.Log;

/**
 * 照 8.83 com.example.hikerview.service.parser.WebkitParser 转写（单例 + ticket 校验 + parse/finishParseInner）。
 * url 形态：webRule://页面地址@取址JS表达式，extra 为条目 extra(JSON)；取址脚本经 HkWebkitHolder 注入页面，
 * 结果由 fy_bridge_app.finishParse → HkDestroyEvent → HkArticleFragment.finishParse 回到 finishParse。
 */
public class HkWebkitParser extends HkIWebViewParser {
    private static final String TAG = "HkWebkitParser";

    private static HkWebkitParser instance;

    private HkWebkitHolder webViewHolder;

    public static boolean canParse(String url) {
        return HkStringUtil.isNotEmpty(url) && url.startsWith("webRule://");
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

    public static HkWebkitParser newInstance() {
        if (instance != null) instance.destroy();
        instance = new HkWebkitParser();
        return instance;
    }

    public static synchronized boolean parse0(Activity activity, String url, String extra,
                                              Consumer<String> consumer) {
        if (!canParse(url)) return false;
        destroy0();
        return newInstance().parse(activity, url, extra, consumer);
    }

    /**
     * 背景嗅探入口（掉帧账 S1 落修，下一集预解析专用）：与 parse0 同一解析器、
     * 同一装配、同 ticket 口径，只换宿主——WebView 以应用上下文建（脱窗，
     * HkWebkitFetcher 取页一线同形）、holder 走 initWebViewBackground
     * （不登记当前页 Wrapper）；reference 语境取当下前台页（可为 null），
     * 只作桥内门控的存活来源，不作窗口宿主。
     */
    public static synchronized boolean parseBackground0(String url, String extra,
                                                        Consumer<String> consumer) {
        if (!canParse(url)) return false;
        destroy0();
        return newInstance().parseBackground(url, extra, consumer);
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
        return parseHost(activity, activity, false, url, extra, consumer);
    }

    /** 背景形（S1）：宿主＝应用上下文，reference 语境＝当下前台页（可空）。 */
    public boolean parseBackground(String url, String extra, Consumer<String> consumer) {
        return parseHost(App.get(), App.activity(), true, url, extra, consumer);
    }

    private boolean parseHost(Context host, Activity refActivity, boolean background,
                              String url, String extra, Consumer<String> consumer) {
        String stripped = HkStringUtil.replaceOnceIgnoreCase(url, "webRule://", "");
        String[] parts = stripped.split("@");
        destroy();
        if (parts.length < 2) {
            HkRouteBlocker.toastShortBottom(host, "规则有误！" + url);
            return false;
        }
        this.consumer = consumer;
        String target = parts[0];
        String jsExpr = HkParserKit.arrayToString(parts, 1, "@");
        String ticket = String.valueOf(hashCode());
        HkX5Extra x5Extra = generateExtra(extra, jsExpr, "webkit", ticket);
        WebView webView = new WebView(host);
        webViewHolder = new HkWebkitHolder();
        webViewHolder.setSniffMode(true);
        webViewHolder.setWebView(webView);
        if (background) webViewHolder.initWebViewBackground(host, refActivity);
        else webViewHolder.initWebView(refActivity);
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
        webViewHolder.setX5Extra(x5Extra);
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
