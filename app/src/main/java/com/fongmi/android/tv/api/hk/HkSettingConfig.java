package com.fongmi.android.tv.api.hk;

/**
 * 8.83 com.example.hikerview.ui.setting.model.SettingConfig 的最小对位：
 * 目前只落 nowLoadRulesTime 一位（SettingConfig.smali:41 public static long）。
 * 官消费方 MainActivity 以此位判规则列表是否需要重载；咱家规则页的感知面是
 * HkRuleManager.saveStamp（既有），本位只照官承接事件构造副作用的写点，
 * 不另造消费面（记名）。
 */
public final class HkSettingConfig {

    /** 官 SettingConfig.nowLoadRulesTime：最近一次「规则集已变更」事件的构造时刻。 */
    public static long nowLoadRulesTime = 0L;

    private HkSettingConfig() {
    }
}
