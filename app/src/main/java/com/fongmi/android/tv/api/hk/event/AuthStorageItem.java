package com.fongmi.android.tv.api.hk.event;

/**
 * 8.83 com.example.hikerview.service.auth.AuthStorageItem 的咱家同位存储项
 * （官源 smali/com/example/hikerview/service/auth/AuthStorageItem.smali，
 * 字段逐字）：method＝方法名、result＝该方法的持久裁决
 * （AuthBridgeEvent.AuthResult）。
 * <p>
 * 官存储形态：一个规则的全部裁决是一张 AuthStorageItem JSON 数组，经
 * aesEncode(时间戳密钥) 后存进规则的 item "_auth"（JSEngine.getAuth/
 * updateAuth:3106/26308）；缺项时官以 AuthStorageItem(method, NONE) 兜底
 * 返回、不落盘。本类只承载数据，存取路（getAuth/updateAuth 等价实现）
 * 在 HkJsRuntime 侧，属主线缝（见 AuthBridgeEvent 类头注的契约账）。
 */
public class AuthStorageItem {

    private String method;
    private AuthBridgeEvent.AuthResult result;

    public AuthStorageItem() {
    }

    public AuthStorageItem(String method, AuthBridgeEvent.AuthResult result) {
        this.method = method;
        this.result = result;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public AuthBridgeEvent.AuthResult getResult() {
        return result;
    }

    public void setResult(AuthBridgeEvent.AuthResult result) {
        this.result = result;
    }
}
