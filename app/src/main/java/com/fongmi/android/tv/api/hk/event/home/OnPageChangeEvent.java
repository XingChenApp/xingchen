package com.fongmi.android.tv.api.hk.event.home;

/**
 * 8.83 com.example.hikerview.event.home.OnPageChangeEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 直接搬运：字段、构造方法、getter/setter 与 8.83 原文一致（官源 OnPageChangeEvent.java）。
 * 官生产方为 MainActivity 页签切换（标题合本页→WebView 恢复、不合→mute＋暂停）；
 * 咱家生产点见 HkArticleFragment 的 F5 落点（本页被盖住/重现的生命周期等价点）。
 */
public class OnPageChangeEvent {

    private String title;

    public OnPageChangeEvent(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
