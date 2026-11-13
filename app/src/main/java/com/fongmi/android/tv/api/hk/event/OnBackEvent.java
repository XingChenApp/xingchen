package com.fongmi.android.tv.api.hk.event;

/**
 * 8.83 com.example.hikerview.event.OnBackEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 直接搬运：字段、构造方法、getter/setter 与 8.83 原文一致。
 */
public class OnBackEvent {

    private boolean refreshPage;
    private boolean scrollTop;

    public OnBackEvent(boolean boolean0, boolean boolean1) {
        this.refreshPage = boolean0;
        this.scrollTop = boolean1;
    }

    public boolean isRefreshPage() {
        return refreshPage;
    }

    public boolean isScrollTop() {
        return scrollTop;
    }

}