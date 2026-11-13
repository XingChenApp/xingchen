package com.fongmi.android.tv.ui.lab;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.NodeLabInfo;

/**
 * NodeLabInfo 的实验室实现。登记主路由是显式直调：LabBoot 在自启链上
 * （kick 同步段＋boot 漏斗）直接调本类 register()——真实代码引用保活，
 * 不再只靠类加载副作用。静态块保留为兜底触发（NodeLabInfo 按类名
 * Class.forName 懒加载时仍能自登记）。教训在前：1.4.59 成品解包实证，
 * 本类类名靠 keep 保住、{@code <clinit>} 却被 R8 吃掉，只剩私有构造器，
 * 登记永不触发、模块页 NODEJS 行恒显未装——keep 类名不等于保住
 * clinit 的登记副作用。判据只读实验室自己的账——在场走
 * LabRuntimeManager.isInstalled、已装版本走 LabRegistry 登记、可用版本走
 * LabRuntimeManager.catalogVersion。
 *
 * <p>1.4.60 复发定根（账 r8-swallow-20261029）：这回 {@code <clinit>}
 * 与 register() 签名都在，体却被整段摘空。根子在读方先死——模块页
 * 读链唯一入口（设置页运行模块卡调 showModuleFragment）早在 1.4.11
 * 补批删卡时被连带删除，ModuleViewModel／NodeLabInfo 整条读链被 R8
 * 判不可达整类删除，provider 字段无人读，「new 匿名 Provider＋写
 * 字段」便被判无副作用。两道加固：一是读写整条图谱已在
 * proguard-rules.pro 钉为入口点；二是本类改静态具名实现＋静态字段
 * 持单例，消灭「无字段持有的匿名类」这一可被整类摘除的形态。
 */
public final class LabNodeInfo {

    private static final NodeLabInfo.Provider IMPL = new ProviderImpl();

    static {
        register();
    }

    /**
     * 显式登记入口（幂等：重复调递的都是同一个单例）。
     * 调用点只有 LabBoot 的自启链——该链自身靠保名＋HomeActivity 直调
     * 保活，登记代码挂在它身上即有真实引用，不会被 R8 当死代码剥离。
     */
    public static void register() {
        NodeLabInfo.register(IMPL);
    }

    private static final class ProviderImpl implements NodeLabInfo.Provider {

        @Override
        public boolean installed() {
            return LabRuntimeManager.isInstalled(App.get(), "nodejs");
        }

        @Override
        public String installedVersion() {
            if (!installed()) return null;
            LabRegistry.Runtime runtime = LabRegistry.get(App.get()).runtime("nodejs");
            return runtime == null ? null : runtime.version;
        }

        @Override
        public String availableVersion() {
            return LabRuntimeManager.catalogVersion(App.get(), "nodejs");
        }
    }

    private LabNodeInfo() {
    }
}
