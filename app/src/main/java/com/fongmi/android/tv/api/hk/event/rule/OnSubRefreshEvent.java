package com.fongmi.android.tv.api.hk.event.rule;

/**
 * 8.83 com.example.hikerview.event.OnSubRefreshEvent 的等价实现：
 * 订阅 checkUpdateAsync／batchCheckUpdate 跑完后 post（官口径：有订阅者时
 * 才 post，EventBus 无订阅者时 post 本为无操作）。官消费方是订阅管理面
 * （RulesSubscribePopup 刷新列表）——咱家管理 UI 面未落（环39 UI 面 C），
 * 发送端照官补齐，消费面记名。
 */
public class OnSubRefreshEvent {
}
