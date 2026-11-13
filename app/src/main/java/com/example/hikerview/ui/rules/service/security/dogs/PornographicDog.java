package com.example.hikerview.ui.rules.service.security.dogs;

import com.example.hikerview.ui.rules.service.security.RuleChecker;
import com.example.hikerview.ui.rules.service.security.SecurityRisk;

/**
 * 官 {@code dogs.PornographicDog} 照官转写（官源 smali_classes2/com/example/hikerview/ui/
 * rules/service/security/dogs/PornographicDog.smali，全 27 行）：官 getRisk 本体即
 * 无条件回 null（.locals 0、无判定），照官原样落，不自补判定。
 */
public class PornographicDog implements RuleChecker.IRuleChecker {

    @Override
    public SecurityRisk getRisk(String str) {
        return null;
    }
}
