package com.fongmi.android.tv.ui.hk;

import java.util.List;
import java.util.Map;

/**
 * 照 8.83 com.example.hikerview.ui.browser.webview.WebViewWrapper 接口（ArticleWebkitHolder$3 实现的方法集）转写。
 * 供 fy_bridge_app 桥对象（原版 JsBridgeHolder/VideoInterface）访问宿主 WebView 状态。
 */
public interface HkWebViewWrapper {

    void addJavascriptInterface(Object object, String name);

    void evaluateJavascript(String js);

    String getCookie(String url);

    String getMyUrl();

    String getRefererPolicy();

    Map<String, Map<String, String>> getRequestHeaderMap();

    String getSystemUa();

    String getTitle();

    String getUrl();

    List<String> getUrls();

    String getUserAgentString();

    boolean isOnPause();

    void loadUrl(String url);

    void openThirdApp(String url);

    void reload();

    void setAppBarColor(String color1, String color2);

    void setUserAgentString(String ua);

    void storeFaviconUrl(String url);

    void storeRefererPolicy(String policy);

    void updateLastDom(String dom);
}
