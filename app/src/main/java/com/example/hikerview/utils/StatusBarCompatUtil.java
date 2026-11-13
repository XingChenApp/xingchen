package com.example.hikerview.utils;

import android.app.Activity;
import android.os.Build;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.githang.statusbar.StatusBarCompat;

/**
 * 官 {@code com.example.hikerview.utils.StatusBarCompatUtil} 照官 smali 逐字转写
 * （smali_classes2/com/example/hikerview/utils/StatusBarCompatUtil.smali：
 * isDark、setStatusBarColor、setStatusBarColor2、setStatusBarLight 全四法）。
 * 依赖外部库 com.githang:status-bar-compat:0.7（官包内嵌版 BuildConfig
 * VERSION_NAME 0.7，本批已引，见 app/build.gradle）。灰度阈值 225 照官。
 */
public class StatusBarCompatUtil {

    private static boolean isDark(Activity activity) {
        return (activity.getResources().getConfiguration().uiMode & 0x30) == 0x20;
    }

    public static void setStatusBarColor(Activity activity, int color) {
        if (isDark(activity)) {
            StatusBarCompat.setStatusBarColor(activity, color, StatusBarCompat.toGrey(color) <= 225);
        } else {
            StatusBarCompat.setStatusBarColor(activity, color);
        }
        if (Build.VERSION.SDK_INT >= 30 && !isDark(activity)) {
            setStatusBarLight(activity, StatusBarCompat.toGrey(color) > 225);
        }
    }

    public static void setStatusBarColor2(Activity activity, int color) {
        StatusBarCompat.setStatusBarColor(activity, color);
        if (Build.VERSION.SDK_INT >= 30) {
            setStatusBarLight(activity, StatusBarCompat.toGrey(color) > 225);
        }
    }

    public static void setStatusBarLight(Activity activity, boolean light) {
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(activity.getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(light);
        }
    }
}
