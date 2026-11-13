package com.fongmi.android.tv.api.hk.event.web;

/**
 * 8.83 com.example.hikerview.event.web.FindMagnetsEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 网页桥 fy_bridge_app.findMagnetsNotify(data) 时发布；官由 WebViewActivity 订阅后弹磁力列表，
 * 咱家暂无对应消费面（记名，见第八阶段回执）。
 */
public class FindMagnetsEvent {

    private final String data;

    public FindMagnetsEvent(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }
}
