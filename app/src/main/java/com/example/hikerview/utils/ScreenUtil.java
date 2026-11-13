package com.example.hikerview.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.smarx.notchlib.NotchScreenManager;

/**
 * 照官 8.83 本体移植（jadx 官包源）：屏幕尺寸/状态栏工具，全量照官。
 * setDisplayInNotch 已于 2026-11-14 外部小库批照官落：notchlib 经
 * JitPack 引 com.github.smarxpan:NotchScreenTool:0.0.1（官包内嵌版
 * com.smarx.notchlib，BuildConfig VERSION_NAME 1.0）；
 * PreferenceMgr.getBoolean 不另立类，按 SettingConfig 批先例直连
 * "share_data" 文件读 "useNotch"（默认 true，照官）。
 */
public class ScreenUtil {

    public static int getWindowHeight(Activity activity) {
        Rect rect = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
        return rect.height();
    }

    public static int getScreenHeight(Activity activity) {
        WindowManager windowManager = activity.getWindowManager();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        int i = displayMetrics.heightPixels;
        return (isOrientationLand(activity) || displayMetrics.widthPixels <= i) ? i : displayMetrics.widthPixels;
    }

    public static int getScreenHeight2(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService("window");
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.heightPixels;
    }

    public static int getScreenWidth(Activity activity) {
        WindowManager windowManager = activity.getWindowManager();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        int i = displayMetrics.widthPixels;
        return (isOrientationLand(activity) || displayMetrics.heightPixels >= i) ? i : displayMetrics.heightPixels;
    }

    public static int getScreenWidth3(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService("window");
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }

    public static int getScreenMin(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService("window");
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        return Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);
    }

    public static int getScreenMax(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService("window");
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        return Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
    }

    public static int[] getScreenSize(Activity activity) {
        View decorView = activity.getWindow().getDecorView();
        return new int[]{decorView.getWidth(), decorView.getHeight()};
    }

    public static int getStatusBarHeight(Activity activity) {
        Resources resources = activity.getResources();
        return resources.getDimensionPixelSize(resources.getIdentifier("status_bar_height", "dimen", "android"));
    }

    public static int getNavigationBarHeight(Activity activity) {
        Resources resources = activity.getResources();
        int identifier = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (identifier <= 0 || !hasNavigationBar(activity)) {
            return 0;
        }
        return resources.getDimensionPixelSize(identifier);
    }

    private static boolean hasNavigationBar(Activity activity) {
        Resources resources = activity.getResources();
        int identifier = resources.getIdentifier("config_showNavigationBar", "bool", "android");
        boolean z = false;
        boolean z2 = identifier > 0 ? resources.getBoolean(identifier) : false;
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            String str = (String) cls.getMethod("get", String.class).invoke(cls, "qemu.hw.mainkeys");
            if (!"1".equals(str)) {
                z = "0".equals(str) ? true : z2;
            }
            return z;
        } catch (Exception unused) {
            return z2;
        }
    }

    public static DisplayMetrics getDisplayMetrics(Activity activity) {
        return activity.getResources().getDisplayMetrics();
    }

    public static int toPixels(Resources resources, float f) {
        return (int) (f * resources.getDisplayMetrics().density);
    }

    public static int toScreenPixels(Resources resources, float f) {
        return (int) TypedValue.applyDimension(2, f, resources.getDisplayMetrics());
    }

    public static boolean isRtl(Resources resources) {
        return resources.getConfiguration().getLayoutDirection() == 1;
    }

    public static boolean isTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout & 15) >= 3 && isPad(context);
    }

    public static boolean isPad(Context context) {
        Display defaultDisplay = ((WindowManager) context.getSystemService("window")).getDefaultDisplay();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        defaultDisplay.getMetrics(displayMetrics);
        return Math.sqrt(Math.pow((double) (((float) displayMetrics.widthPixels) / displayMetrics.xdpi), 2.0d) + Math.pow((double) (((float) displayMetrics.heightPixels) / displayMetrics.ydpi), 2.0d)) >= 7.0d;
    }

    public static boolean isOrientationLand(Context context) {
        return (context == null || context.getResources() == null || context.getResources().getConfiguration().orientation != 2) ? false : true;
    }

    public static void setStatusBarVisibility(Activity activity, int i, boolean z) {
        Window window = activity.getWindow();
        window.getAttributes();
        if (!z) {
            activity.getWindow().setFlags(1024, 1024);
            WindowInsetsControllerCompat windowInsetsController = getWindowInsetsController(activity);
            if (windowInsetsController != null) {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
                return;
            }
        } else {
            activity.getWindow().clearFlags(1024);
            WindowInsetsControllerCompat windowInsetsController2 = getWindowInsetsController(activity);
            if (windowInsetsController2 != null) {
                windowInsetsController2.show(WindowInsetsCompat.Type.systemBars());
                return;
            }
        }
        if (!z) {
            window.getDecorView().setSystemUiVisibility(5638);
        } else {
            window.getDecorView().setSystemUiVisibility(i);
        }
    }

    /** 官 setDisplayInNotch（ScreenUtil.smali，.line 254-256）：useNotch 为真才调 notchlib。 */
    public static void setDisplayInNotch(Activity activity) {
        if (activity.getSharedPreferences("share_data", Context.MODE_PRIVATE).getBoolean("useNotch", true)) {
            NotchScreenManager.getInstance().setDisplayInNotch(activity);
        }
    }

    private static WindowInsetsControllerCompat getWindowInsetsController(Activity activity) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            return ViewCompat.getWindowInsetsController(activity.getWindow().getDecorView());
        }
        return WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
    }
}
