package com.example.hikerview.ui.rules.service.security.dogs;

import com.example.hikerview.ui.rules.service.security.RuleChecker;
import com.example.hikerview.ui.rules.service.security.SecurityRisk;
import com.example.hikerview.ui.rules.service.security.SecurityRiskLevelEnum;

/**
 * 官 {@code dogs.EncodeDog} 照官转写（官源 smali_classes2/com/example/hikerview/ui/rules/
 * service/security/dogs/EncodeDog.smali）：marks＝{"jsjiami.com"}（{@code <clinit>} .line 14）；
 * getRisk（.line 18-20）：文本含任一 mark 回 HIGH 风险（述文照官逐字），余回 null。
 */
public class EncodeDog implements RuleChecker.IRuleChecker {

    private static final String[] marks = {"jsjiami.com"};

    @Override
    public SecurityRisk getRisk(String str) {
        for (String mark : marks) {
            if (str.contains(mark)) {
                return new SecurityRisk(SecurityRiskLevelEnum.HIGH,
                        "包含复杂加密格式，大多数规则不需要这种复杂的加密方式，可能为病毒规则，请谨慎导入！");
            }
        }
        return null;
    }
}
