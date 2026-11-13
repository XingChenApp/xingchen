package com.fongmi.android.tv.api.hk.event.rule;

import com.fongmi.android.tv.api.hk.HkSettingConfig;

/**
 * 照 8.83 com.example.hikerview.event.OnArticleListRuleChangedEvent 本体转写
 * （OnArticleListRuleChangedEvent.smali 全文）：
 * - 字段四件：updateFromSub(int)／rule(String)／update(boolean)／fromClazz(String)，
 *   各带官同名 getter/setter（update 的读法官名 isUpdate）。
 * - 构造三形：() 与 (int updateFromSub) 均先写
 *   HkSettingConfig.nowLoadRulesTime＝System.currentTimeMillis()
 *   （官写 SettingConfig.nowLoadRulesTime），(int) 形再存 updateFromSub；
 *   (String rule, boolean update) 形只存两字段、无时间副作用（官此构造直调
 *   Object.&lt;init&gt;、不经前两形）。
 * 旧形只有自造 count 字段、与官字段集不符，C1 复核订正已换。
 * 消费实况（订正旧注「等价实现」过报）：官消费方 MainActivity
 * .lambda$onArticleListRuleChanged$139 读 updateFromSub/rule/update 并刷新
 * 列表，其中提示支另有 isOnPause 不弹之门；咱家无 MainActivity 对位消费方，
 * 事件的订阅提示半由 HkHomeSubService.notifyRulesChanged 在发送侧同口径承接
 * （静默偏好＋官文案），isOnPause 门无宿主可挂、记名不硬造；列表刷新走
 * HkRuleManager.saveStamp 既有感知面。
 */
public class OnArticleListRuleChangedEvent {

    private String fromClazz;
    private String rule;
    private boolean update;
    private int updateFromSub;

    public OnArticleListRuleChangedEvent() {
        HkSettingConfig.nowLoadRulesTime = System.currentTimeMillis();
    }

    public OnArticleListRuleChangedEvent(int updateFromSub) {
        HkSettingConfig.nowLoadRulesTime = System.currentTimeMillis();
        this.updateFromSub = updateFromSub;
    }

    public OnArticleListRuleChangedEvent(String rule, boolean update) {
        this.rule = rule;
        this.update = update;
    }

    public String getFromClazz() {
        return fromClazz;
    }

    public void setFromClazz(String fromClazz) {
        this.fromClazz = fromClazz;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public boolean isUpdate() {
        return update;
    }

    public void setUpdate(boolean update) {
        this.update = update;
    }

    public int getUpdateFromSub() {
        return updateFromSub;
    }

    public void setUpdateFromSub(int updateFromSub) {
        this.updateFromSub = updateFromSub;
    }
}
