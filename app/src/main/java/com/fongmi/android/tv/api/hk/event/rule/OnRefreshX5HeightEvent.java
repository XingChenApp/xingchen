package com.fongmi.android.tv.api.hk.event.rule;

/**
 * 8.83 com.example.hikerview.event.home.OnRefreshX5HeightEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 直接搬运：字段、构造方法、getter/setter 与 8.83 原文一致。
 * 本类由旧名 RefreshX5DescEvent 改名而来（F12）：咱家事件包落点沿旧类所在
 * rule 子包（官在 home 包，包差记名）；生产方（HkJsRuntime.refreshX5Desc /
 * HkWebkitHolder）在主线原子改名时一并切到本名，见分账置顶缝。
 */
public class OnRefreshX5HeightEvent {

    private String desc;

    public OnRefreshX5HeightEvent(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
