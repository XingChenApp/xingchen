package com.fongmi.android.tv.ui.lab;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

/**
 * 开机（App 启动）恢复：先恢复还活着的进程状态，再按各自标记拉起服务。
 * 自启口径（2026-10-29 他亲定，取代 1.4.27 的总开关口径）：实验室首页的
 * 自启总开关摘除，自启只认每个包自己的标记——包声明 autoStartDefault
 * （导入时由清单 auto_execute 落入）／用户覆盖 autoStart，合看 autoStartOf；
 * 没标记的一律不拉。这正是上游口径：上游没有总开关，自启就是每条命令的
 * auto_execute 在启动时跑。历史注：1.4.27 曾改成总开关全量拉起（当时单服务
 * 标记是第二道门、没开过的服务静默不拉），本版按他的新令回到按包标记。
 */
public final class LabBoot {

    private static boolean scheduled;

    private LabBoot() {
    }

    public static synchronized void schedule(Context ctx) {
        if (scheduled) return;
        scheduled = true;
        final Context c = ctx.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                boot(c);
            } catch (Exception ignored) {
            }
        }, 3000);
    }

    /**
     * 立即补跑一遍自启。调用点：App.onCreate（主源集经反射调进来，
     * 抢在 HomeActivity 首读已选配置之前——已选配置若是实验室服务的本地
     * 回环地址，服务没起来时首读必然连不上、必弹「配置取得失败」）。
     * 门槛：包标记为自启＋环境已装＋没在跑，失败经 LabRunner 落运行记录。
     */
    public static void kick(Context ctx) {
        // NodeLabInfo 登记走显式直调挂在本链上（同步段，先于任何页面查询）：
        // 1.4.59 成品实证只靠 LabNodeInfo 静态块自登记会被 R8 吃掉 clinit，
        // 登记永不触发、模块页 NODEJS 行恒显未装。register 幂等、开销只是一个
        // Provider 实例，boot 漏斗里再兜一遍（schedule 单腿路径也覆盖）。
        LabNodeInfo.register();
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                boot(c);
            } catch (Exception ignored) {
            }
        }).start();
    }

    private static void boot(Context c) {
        LabNodeInfo.register();
        // 先恢复进程状态（死了的自动从注册表里清掉）
        LabProc.recoveredAliveKeys(c);
        LabRegistry reg = LabRegistry.get(c);
        List<LabRegistry.Svc> pkgs = reg.allServices();
        for (LabRegistry.Svc p : pkgs) {
            try {
                if (!LabRegistry.autoStartOf(p)) continue;
                if (LabRunner.isRunning(c, p.id)) continue;
                String runtime = p.runtime == null ? "none" : p.runtime;
                if (!"none".equals(runtime) && !LabRuntimeManager.isInstalled(c, runtime)) continue;
                LabRunner.start(c, p.id, null);
            } catch (Exception ignored) {
            }
        }
        // 自定义命令里开了开机自动运行的，也拉起来（变量值走缓存）。
        // 只看命令自己的 autoExecute（上游 auto_execute 同义），不叠包级标记。
        for (LabRegistry.Svc p : pkgs) {
            try {
                List<LabCustomCommands.CustomCommand> customs = LabCustomCommands.list(c, p.id);
                if (customs.isEmpty()) continue;
                java.util.Map<String, String> cached = LabCustomCommands.varCache(c, p.id);
                for (LabCustomCommands.CustomCommand cc : customs) {
                    try {
                        if (!cc.autoExecute) continue;
                        if (LabRunner.isCommandRunning(c, p.id, cc.id)) continue;
                        LabRunner.startCommand(c, p.id, LabCustomCommands.toCommand(cc), cached, null);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }
        LabKeepAlive.refresh(c);
    }
}
