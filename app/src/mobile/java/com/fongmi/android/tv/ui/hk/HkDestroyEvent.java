package com.fongmi.android.tv.ui.hk;

/**
 * 照 8.83 com.example.hikerview.event.web.DestroyEvent 转写（构造序 url/mode/ticket）。
 * WebView 嗅探桥 fy_bridge_app.finishParse(url, type, ticket) 经 EventBus 投递此事件，
 * HkArticleFragment.finishParse 订阅后按 mode 路由到 HkWebkitParser/HkX5WebViewParser.finishParse。
 */
public class HkDestroyEvent {

    private String mode;
    private String ticket;
    private String url;

    public HkDestroyEvent(String url, String mode, String ticket) {
        this.url = url;
        this.mode = mode;
        this.ticket = ticket;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
