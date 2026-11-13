package com.fongmi.android.tv.api.hk.event.web;

/**
 * 8.83 OnSaveAdBlockRuleEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 网页桥 fy_bridge_app.saveAdBlockRule(rule) 时发布；官由 WebViewActivity 订阅后存广告拦截规则，
 * 咱家暂无对应消费面（记名，见第八阶段回执）。
 */
public class OnSaveAdBlockRuleEvent {

    private final String rule;

    public OnSaveAdBlockRuleEvent(String rule) {
        this.rule = rule;
    }

    public String getRule() {
        return rule;
    }
}
