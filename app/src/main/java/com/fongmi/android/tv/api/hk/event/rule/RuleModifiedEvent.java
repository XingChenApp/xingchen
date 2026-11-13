package com.fongmi.android.tv.api.hk.event.rule;

import com.fongmi.android.tv.api.hk.HkRule;

/**
 * 8.83 com.example.hikerview.event.rule.RuleModifiedEvent 的等价实现：
 * setItemNow 在规则 storage 落盘后 post，载体＝被写规则（官为 ArticleListRule，
 * 咱家为 HkRule）。官消费方 MainActivity.onRuleChanged 把事件规则的 storage
 * 同步进首页规则列表的缓存副本；咱家 kv 独立文件、读时现取（kvMapForTitle），
 * 已装规则对象不持 storage 副本，无同形消费点——发送端照官补齐，消费面记名
 * （硬门账 D 环37 §6.3）。
 */
public class RuleModifiedEvent {

    private HkRule rule;

    public RuleModifiedEvent(HkRule rule) {
        this.rule = rule;
    }

    public HkRule getRule() {
        return rule;
    }

    public void setRule(HkRule rule) {
        this.rule = rule;
    }
}
