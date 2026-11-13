package com.fongmi.android.tv.api.hk.browser;

import com.google.gson.annotations.SerializedName;

/**
 * 8.83 com.example.hikerview.ui.browser.model.BrowserProxyHeader 的等价结构（字段照 smali 抠实）：
 * 响应头改写一对——match 以全串正则匹配响应头「名」，命中后 replace 为空（或与原名相同）则删掉该头，
 * 否则把头名改成 replace（值不变）。语义见 BrowserProxy.proxy 的响应头处理段（smali 2415 起）。
 */
public class HkBrowserProxyHeader {

    @SerializedName("match")
    public String match;

    @SerializedName("replace")
    public String replace;

    public HkBrowserProxyHeader() {
    }

    public HkBrowserProxyHeader(String match, String replace) {
        this.match = match;
        this.replace = replace;
    }
}
