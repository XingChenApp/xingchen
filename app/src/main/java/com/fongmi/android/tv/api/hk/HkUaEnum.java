package com.fongmi.android.tv.api.hk;

/**
 * 直接移植自海阔 8.83 {@code com.example.hikerview.constants.UAEnum}（do not rewrite）。
 */
public enum HkUaEnum {
    AUTO("auto", "自动提取（不建议使用）", ""),
    MOBILE("mobile", "移动端（mobile、Android）", "Mozilla/5.0 (Linux; Android 15; 2407FRK8EC Build/AP3A.240617.008; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.127 Mobile Safari/537.36"),
    PC("pc", "电脑端（pc、Windows）", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");

    private final String code;
    private final String content;
    private final String name;

    HkUaEnum(String code, String name, String content) {
        this.code = code;
        this.name = name;
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getContent() {
        return content;
    }

    public static HkUaEnum getByName(String name) {
        if (name == null) {
            return AUTO;
        }
        for (HkUaEnum e : values()) {
            if (e.getName().equals(name)) {
                return e;
            }
        }
        return AUTO;
    }

    public static HkUaEnum getByCode(String code) {
        if (code == null) {
            return AUTO;
        }
        for (HkUaEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return AUTO;
    }
}
