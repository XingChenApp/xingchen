package com.fongmi.android.tv.ui.lab;

import android.app.Activity;

/**
 * 添加服务三选一：选文件／粘贴文本／链接——实验室首页「＋」与环境页「＋」共用这一份。
 * 三路落点：文件→安装页、粘贴→粘贴导入页、链接→粘贴导入页预填。
 * defaultEnv（环境页传当前环境）：导入内容自己声明了运行时的听声明，
 * 没声明的才落这个环境；首页传 null，保持原行为。
 * 深底自绘，不走系统白底 AlertDialog。
 */
public final class LabAddMenu {

    private LabAddMenu() {
    }

    public static void show(Activity a) {
        show(a, null);
    }

    public static void show(Activity a, String defaultEnv) {
        LabInstallDialog.showChoice(a, "添加服务",
                new String[]{"选文件", "粘贴文本", "链接"}, which -> {
                    if (which == 0) {
                        LabInstallActivity.pickFile(a);
                    } else if (which == 1) {
                        if (defaultEnv == null) LabPasteImportActivity.start(a);
                        else LabPasteImportActivity.start(a, defaultEnv);
                    } else {
                        if (defaultEnv == null) LabPasteImportActivity.startLink(a);
                        else LabPasteImportActivity.startLink(a, defaultEnv);
                    }
                });
    }
}
