package com.example.hikerview.ui.rules.service.security.dogs;

import com.example.hikerview.ui.rules.service.security.RuleChecker;
import com.example.hikerview.ui.rules.service.security.SecurityRisk;

/**
 * 官 {@code dogs.VirusDog} 照官转写（官源 smali_classes2/com/example/hikerview/ui/rules/
 * service/security/dogs/VirusDog.smali，全 62 行）：marks 静态表照官 {@code <clinit>}
 * （.line 14）＝{"ENV$", "aHR0cHM6Ly9ldmFseC52ZXJjZWwuYXBwL2pz", "vercel.app"}；
 * 但官 getRisk 本体即无条件回 null（.locals 0、无判定，marks 在官本体未被消费），
 * 照官原样落，不自补判定。
 */
public class VirusDog implements RuleChecker.IRuleChecker {

    private static final String[] marks = {"ENV$", "aHR0cHM6Ly9ldmFseC52ZXJjZWwuYXBwL2pz", "vercel.app"};

    @Override
    public SecurityRisk getRisk(String str) {
        return null;
    }
}
