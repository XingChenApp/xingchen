package com.example.hikerview.ui.setting.model;

import android.os.Environment;

import com.fongmi.android.tv.App;

import java.io.File;

/**
 * 官 8.83 SettingConfig 的最小落点：官类是千行配置总汇，本批只落
 * GlideUtil 实际用到的两名成员，语义照官——
 * rootDir：官在 init 时置为 UriUtils.getRootDir(ctx)，即
 * getExternalFilesDir(DOCUMENTS) 的路径（与星辰引擎 HkJsRuntime
 * 的 hiker://files 根同一口径），此处按官公式现算；
 * glideUA：官为静态字段（初值 null，init 时从偏好读、空则取
 * WebView UA），此处保留字段与读写，默认空串——未接官的偏好/WebView
 * 注入，空值时 GlideUtil 走无 UA 头分支，与官未初始化时同形。
 * 其余配置成员随用到再逐名补入，不整类搬。
 */
public class SettingConfig {

    public static String rootDir = "";

    private static String glideUA = "";

    public static String getRootDirPath() {
        if (rootDir == null || rootDir.isEmpty()) {
            File docs = App.get().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (docs != null) {
                if (!docs.exists()) {
                    docs.mkdirs();
                }
                rootDir = docs.getAbsolutePath();
            }
        }
        return rootDir;
    }

    public static String getGlideUA() {
        return glideUA;
    }

    public static void setGlideUA(String glideUA) {
        SettingConfig.glideUA = glideUA;
    }
}
