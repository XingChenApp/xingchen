package com.fongmi.android.tv.api.hk.event;

import java.util.concurrent.CountDownLatch;

/**
 * 8.83 com.example.hikerview.service.auth.AuthBridgeEvent 的咱家同位事件
 * （官源 smali/com/example/hikerview/service/auth/AuthBridgeEvent.smali，
 * 字段与构造逐字）：rule＝规则标题、title＝申请事项显示名、method＝被申请
 * 的方法名、lock＝生产方阻塞等待的 CountDownLatch(1)、result＝宿主裁决
 * （AuthResult，初始 null；官存储层 AuthStorageItem 的缺省裁决是 NONE）。
 *
 * <p>官链全形（全官包 grep＋JSEngine.smali 亲读，2026-11-14 簇4）：
 * 生产方<b>不是</b> WebView JS 桥（VideoInterface/JsBridgeHolder 全文 0
 * 命中）——是规则 JS 引擎 JSEngine 的三法在「存储裁决为 NONE」时 post：
 * findJavaClass（method="loadJavaClass"，title="动态加载Java代码（{p}）"，
 * JE:16152）、loadJavaClass（method="loadJavaClass"，title 同前，
 * JE:19136）、registerDNS（method="registerDNS"，title=
 * "自定义DNS解析（{域名以、拼接}）"，JE:22574）。三法 post 后同形：
 * lock.await(1, MINUTES) → 回读 getAuth(rule, method) → 仅 AGREE 放行。
 * 订阅方是五个宿主 Activity 的 onAuthRequest(@Subscribe MAIN)：
 * FilmListActivity/MainActivity/RuleWindowActivity/SearchActivity/
 * SearchInOneRuleActivity，五处同文（FilmListActivity.smali:5932 为据）：
 * isOnPause 或 isFinishing 时不弹（生产方 latch 超时后按未 AGREE 收场）；
 * 余弹 AlertDialog「授权提示」＋正文「{rule}申请{title}，此方法非常规
 * 情况下不需要使用，确认要使用请点击授权执行」，setCancelable(false)，
 * 三钮——「授权执行」→JSEngine.updateAuth(rule, method, AGREE)＋
 * lock.countDown()；「拒绝执行」→updateAuth(..., DISAGREE)＋countDown；
 * 「暂不授权」→只 countDown（不写存储，下回再问）。
 *
 * <p>updateAuth 契约（JSEngine.updateAuth:26308，落点在 HkJsRuntime、
 * 非本簇文件，只记契约留主线缝）：updateAuth(ruleTitle, method, result)
 * 按 title 查规则（LitePal where title=? limit 1，无则直回）；取规则
 * item "_auth"（以 item "i--" 的时间戳串为 AES 密钥 aesDecode）解出
 * AuthStorageItem{method, result} JSON 数组，按 method 命中改 result、
 * 未命中新增一条，再 aesEncode 回写 "_auth"。getAuth(rule, method) 同路
 * 读回，缺省 AuthStorageItem(method, NONE)。即官的持久裁决存的是规则
 * item "_auth" 的加密表，不是 SharedPreferences——咱家 HkAuthManager
 * 现行 hk_auth SP 是等价承接面，主线接 updateAuth 时按此契约对位。
 *
 * <p>咱家承接：订阅方落独立 handler
 * com.fongmi.android.tv.ui.hk.HkAuthBridgeHandler（mobile 源集），其
 * ResultSink 即 updateAuth 的回桥口（主线注册一行把裁决转给
 * HkJsRuntime 的 updateAuth 等价实现）；生产方三法在 HkJsRuntime 内的
 * 改造（NONE 时 post 本事件＋await 1 分钟＋回读裁决）同属主线缝。
 */
public class AuthBridgeEvent {

    /** 官 AuthBridgeEvent$AuthResult（smali 同文件内类）：AGREE/DISAGREE/NONE，序逐字。 */
    public enum AuthResult {
        AGREE,
        DISAGREE,
        NONE
    }

    private String rule;
    private String title;
    private String method;
    private CountDownLatch lock;
    private AuthResult result;

    public AuthBridgeEvent(String rule, String title, String method, CountDownLatch lock) {
        this.rule = rule;
        this.title = title;
        this.method = method;
        this.lock = lock;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public CountDownLatch getLock() {
        return lock;
    }

    public void setLock(CountDownLatch lock) {
        this.lock = lock;
    }

    public AuthResult getResult() {
        return result;
    }

    public void setResult(AuthResult result) {
        this.result = result;
    }
}
