package com.fongmi.android.tv.api.hk.event.web;

/**
 * 8.83 ShowTranslateEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 网页桥 fy_bridge_app.translate(text) 时发布；官由 WebViewActivity 订阅后弹翻译面，
 * 咱家暂无对应消费面（记名，见第八阶段回执）。
 */
public class ShowTranslateEvent {

    private final String text;

    public ShowTranslateEvent(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
