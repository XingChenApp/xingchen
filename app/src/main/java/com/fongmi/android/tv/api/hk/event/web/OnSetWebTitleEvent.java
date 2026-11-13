package com.fongmi.android.tv.api.hk.event.web;

/**
 * 8.83 OnSetWebTitleEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 网页桥 fy_bridge_app.setWebTitle(title) 时发布；官由 WebViewActivity 订阅后改标题栏，
 * 咱家暂无对应消费面（记名，见第八阶段回执）。
 */
public class OnSetWebTitleEvent {

    private final String title;

    public OnSetWebTitleEvent(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
