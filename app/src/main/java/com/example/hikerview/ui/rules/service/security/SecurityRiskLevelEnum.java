package com.example.hikerview.ui.rules.service.security;

/**
 * 官 {@code SecurityRiskLevelEnum} 照官转写（官源 smali_classes2/com/example/hikerview/
 * ui/rules/service/security/SecurityRiskLevelEnum.smali）：四档枚举序与中文名逐字——
 * SAFE（安全）/WARN（警告）/HIGH（高风险）/FORBIDDEN（禁止导入）；name 字段经
 * 私有构造写入，getName() 回之（官 .line 14-19）。
 */
public enum SecurityRiskLevelEnum {
    SAFE("安全"),
    WARN("警告"),
    HIGH("高风险"),
    FORBIDDEN("禁止导入");

    private final String name;

    SecurityRiskLevelEnum(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
