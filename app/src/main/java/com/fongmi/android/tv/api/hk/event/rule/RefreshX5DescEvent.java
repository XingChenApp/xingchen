package com.fongmi.android.tv.api.hk.event.rule;

/**
 * 8.83 com.example.hikerview.event.home.OnRefreshX5HeightEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 规则 JS 调 refreshX5Desc(desc) 时发布；列表页订阅后按 desc 更新 WebView 的模式（FLOAT/LIST）与高度。
 */
public class RefreshX5DescEvent {

    private final String desc;

    public RefreshX5DescEvent(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
