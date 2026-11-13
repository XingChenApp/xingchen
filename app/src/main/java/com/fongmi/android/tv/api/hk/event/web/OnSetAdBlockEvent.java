package com.fongmi.android.tv.api.hk.event.web;

/**
 * 8.83 OnSetAdBlockEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 网页桥 fy_bridge_app.setAdBlock(html, rule) 时发布；官由 WebViewActivity 订阅后对当前页置拦截规则，
 * 咱家暂无对应消费面（记名，见第八阶段回执）。
 */
public class OnSetAdBlockEvent {

    private final String html;
    private final String rule;

    public OnSetAdBlockEvent(String html, String rule) {
        this.html = html;
        this.rule = rule;
    }

    public String getHtml() {
        return html;
    }

    public String getRule() {
        return rule;
    }
}
