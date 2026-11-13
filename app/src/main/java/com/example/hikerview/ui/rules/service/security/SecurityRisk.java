package com.example.hikerview.ui.rules.service.security;

/**
 * 官 {@code SecurityRisk} 照官转写（官源 smali_classes2/com/example/hikerview/ui/rules/
 * service/security/SecurityRisk.smali）：两字段 level/description，构造
 * (level, description) 序照官 .line 11-13，getter/setter 四法照官 .line 21-33。
 */
public class SecurityRisk {
    private String description;
    private SecurityRiskLevelEnum level;

    public SecurityRisk(SecurityRiskLevelEnum level, String description) {
        this.level = level;
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public SecurityRiskLevelEnum getLevel() {
        return level;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setLevel(SecurityRiskLevelEnum level) {
        this.level = level;
    }
}
