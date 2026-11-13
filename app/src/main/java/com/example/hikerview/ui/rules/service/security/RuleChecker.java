package com.example.hikerview.ui.rules.service.security;

import android.content.Context;

import com.example.hikerview.ui.rules.service.security.dogs.EncodeDog;
import com.example.hikerview.ui.rules.service.security.dogs.PornographicDog;
import com.example.hikerview.ui.rules.service.security.dogs.VirusDog;
import com.example.hikerview.utils.DisplayUtil;
import com.example.hikerview.utils.StringUtil;
import com.lxj.xpopup.XPopup;

/**
 * 官 {@code RuleChecker} 照官转写（官源 smali_classes2/com/example/hikerview/ui/rules/
 * service/security/RuleChecker.smali，302 行）：
 * 静态 dogs 数组照官 {@code <clinit>} 序＝{PornographicDog, VirusDog, EncodeDog}
 * （同目录 OldApiDog 官未注册进数组，本批不落、记名在分团账）；
 * getRisk（.line 27-32）：空串回 null，逐 dog 取首个非 null 且 level≠SAFE 的风险，全无回 null；
 * check（.line 40-58）：无风险直跑 runnable；FORBIDDEN 时 XPopup 确认框
 * 「风险提示／检测到异常内容，风险级别：&lt;名&gt;，风险提示：&lt;述&gt;」＋空确认回调
 * （lambda$check$0 官本体即空体）；余档同框尾加「确定要继续导入吗？」＋确认回调跑
 * runnable（lambda$check$1）。borderRadius＝dpToPx(16) 照官。
 */
public class RuleChecker {

    public interface IRuleChecker {
        SecurityRisk getRisk(String str);
    }

    private static final IRuleChecker[] dogs = {new PornographicDog(), new VirusDog(), new EncodeDog()};

    public static void check(Context context, String str, Runnable runnable) {
        SecurityRisk risk = getRisk(str);
        if (risk != null) {
            if (risk.getLevel() == SecurityRiskLevelEnum.FORBIDDEN) {
                new XPopup.Builder(context)
                        .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                        .asConfirm("风险提示", "检测到异常内容，风险级别：" + risk.getLevel().getName()
                                + "，风险提示：" + risk.getDescription(), () -> {
                        })
                        .show();
                return;
            }
            new XPopup.Builder(context)
                    .borderRadius((float) DisplayUtil.dpToPx(context, 16))
                    .asConfirm("风险提示", "检测到异常内容，风险级别：" + risk.getLevel().getName()
                            + "，风险提示：" + risk.getDescription() + "确定要继续导入吗？", () -> runnable.run())
                    .show();
            return;
        }
        runnable.run();
    }

    public static SecurityRisk getRisk(String str) {
        if (StringUtil.isEmpty(str)) {
            return null;
        }
        for (IRuleChecker dog : dogs) {
            SecurityRisk risk = dog.getRisk(str);
            if (risk != null && risk.getLevel() != SecurityRiskLevelEnum.SAFE) {
                return risk;
            }
        }
        return null;
    }
}
