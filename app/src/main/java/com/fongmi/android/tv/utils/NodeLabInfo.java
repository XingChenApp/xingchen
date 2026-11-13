package com.fongmi.android.tv.utils;

import androidx.annotation.Nullable;

/**
 * 实验室 Node 环境信息登记口（模块页 NODEJS 行的版本显示用，桥接设计
 * 映射表 15）：实验室在 mobile 源集，主源集不能直引 ui.lab 类（leanback
 * 变体没有实验室）。登记主路由是 mobile 侧的显式直调——LabBoot 在自启
 * 链上（App.onCreate 反射 kick 的同步段＋boot 漏斗）直接调
 * LabNodeInfo.register() 把 Provider 递进来，真实代码引用保活；本类的
 * Class.forName＋实现类静态块只留作兜底触发。口径变更缘由：1.4.59 成品
 * 解包实证，旧路由只靠反射触发类加载、指望 {@code <clinit>} 自登记，
 * LabNodeInfo 类名虽被 keep 保住、clinit 却被 R8 吃掉，登记永不触发、
 * 模块页恒显未装。未登记（leanback／实验室不在）一律按没装处理，
 * 任何路径不许把没装吞成可用假装已装。
 */
public final class NodeLabInfo {

    public interface Provider {

        boolean installed();

        @Nullable
        String installedVersion();

        @Nullable
        String availableVersion();
    }

    private static final String IMPL_CLASS = "com.fongmi.android.tv.ui.lab.LabNodeInfo";

    private static volatile Provider provider;
    private static volatile boolean loadAttempted;

    private NodeLabInfo() {
    }

    public static void register(Provider impl) {
        provider = impl;
    }

    public static boolean installed() {
        Provider impl = provider();
        if (impl == null) return false;
        try {
            return impl.installed();
        } catch (Throwable e) {
            return false;
        }
    }

    @Nullable
    public static String installedVersion() {
        Provider impl = provider();
        if (impl == null) return null;
        try {
            return impl.installedVersion();
        } catch (Throwable e) {
            return null;
        }
    }

    @Nullable
    public static String availableVersion() {
        Provider impl = provider();
        if (impl == null) return null;
        try {
            return impl.availableVersion();
        } catch (Throwable e) {
            return null;
        }
    }

    private static Provider provider() {
        if (provider == null && !loadAttempted) {
            synchronized (NodeLabInfo.class) {
                if (provider == null && !loadAttempted) {
                    loadAttempted = true;
                    try {
                        Class.forName(IMPL_CLASS);
                    } catch (Throwable ignored) {
                        // leanback 没有实验室实现类：保持未登记，按没装处理。
                    }
                }
            }
        }
        return provider;
    }
}
