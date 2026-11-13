package com.fongmi.android.tv.api.hk.event.web;

/**
 * 8.83 ShowSearchEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 网页桥 fy_bridge_app.searchBySelect(text) 时发布；官由 WebViewActivity 订阅后弹搜索框，
 * 咱家暂无对应消费面（记名，见第八阶段回执）。
 */
public class ShowSearchEvent {

    private final String text;

    public ShowSearchEvent(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
