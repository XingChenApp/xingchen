package com.fongmi.android.tv.ui.dialog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 脚本目录列举（纯文件系统、无 Android 依赖，弹窗与离线验证共用同一份）：
 * 列 plugins/py（*.py）、plugins/js（*.js、*.wv）与 plugins/html（*.html），过滤口径与
 * SourceScriptsDialog 历史实现逐字一致（大小写敏感、按后缀）。
 */
public final class ScriptFiles {

    private ScriptFiles() {
    }

    public static List<File> list(File dir, String ext) {
        List<File> result = new ArrayList<>();
        if (dir != null && dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name != null && (name.endsWith(ext) || (".js".equals(ext) && name.endsWith(".wv"))));
            if (files != null) {
                for (File f : files) result.add(f);
            }
        }
        return result;
    }

    /** 目录监听事件里的文件名是否属于该列脚本（后缀口径与 list 一致）。 */
    public static boolean isScriptName(String name, boolean pyDir) {
        return isScriptName(name, pyDir ? ".py" : ".js");
    }

    /** 按列后缀判名（.js 列兼收 .wv，其余列只认本后缀）。 */
    public static boolean isScriptName(String name, String ext) {
        if (name == null || ext == null) return false;
        if (name.endsWith(ext)) return true;
        return ".js".equals(ext) && name.endsWith(".wv");
    }
}
