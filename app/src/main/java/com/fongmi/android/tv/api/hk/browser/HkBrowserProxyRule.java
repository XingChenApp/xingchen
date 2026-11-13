package com.fongmi.android.tv.api.hk.browser;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * 8.83 com.example.hikerview.ui.browser.model.BrowserProxyRule 的等价结构（字段照 smali 抠实）：
 * name 规则名（增删的键）、match 请求 URL 全串正则、replace 目标改写（多行 DSL）、
 * requestHeaders 请求头增补表、responseHeaders 响应头改写表、force 是否对静态资源也生效。
 * 注意：这不是网络层代理（没有 host/port），是宿主替 WebView 抓取并改写网页请求的规则。
 */
public class HkBrowserProxyRule {

    @SerializedName("name")
    public String name;

    @SerializedName("match")
    public String match;

    @SerializedName("replace")
    public String replace;

    @SerializedName("requestHeaders")
    public Map<String, String> requestHeaders;

    @SerializedName("responseHeaders")
    public List<HkBrowserProxyHeader> responseHeaders;

    @SerializedName("force")
    public boolean force;

    public HkBrowserProxyRule() {
    }
}
