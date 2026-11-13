package com.fongmi.android.tv.api.hk.event.rule;

/**
 * 8.83 com.example.hikerview.event.home.OnRefreshWebViewEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 规则 JS 调 refreshX5WebView(url) 时发布；列表页订阅后把行内 WebView 指到新地址。
 * 与 ui/hk 的 HkDetailBridge.RefreshWebViewEvent（x5WebView:// 协议支）是同一消费面的两个来源，不合并以免动既有链。
 */
public class RefreshWebViewEvent {

    private final String url;

    public RefreshWebViewEvent(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
