package com.example.hikerview.event;

/**
 * 官 {@code com.example.hikerview.event.OnTopEvent} 原样：空事件类。
 * 插件 hikerPop.scrollToPageTop 经 EventBus 发送；官订阅方是
 * ArticleListFragment（源行 881-884：宿主非 FilmListActivity 时 scrollTop）。
 * 星辰订阅方已补：HkArticleFragment.onTopEvent（2026-11-14 136 批 F7，
 * 门＝宿主非 HkListActivity）；旧注「无订阅方」作废。
 */
public class OnTopEvent {
}
