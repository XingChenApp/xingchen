package com.fongmi.android.tv.api.hk.event.home;

/**
 * 8.83 com.example.hikerview.event.OnRefreshEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 直接搬运：官原文为空类（无字段、无显式构造），照原样。包差记名：官在
 * event 根包，咱家随宿主生产方（簇5b HkListActivity 页签双击投递）与
 * home 事件族同置 home 子包。
 * 官生产方在 MainActivity（双击当前页签/刷新动作多处投递）；咱家订阅在
 * HkArticleFragment.onRefresh（回顶＋autoRefresh，官源行 1159-1161）。
 */
public class OnRefreshEvent {
}
