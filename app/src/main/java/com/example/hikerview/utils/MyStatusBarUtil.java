package com.example.hikerview.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

/**
 * 官 {@code com.example.hikerview.utils.MyStatusBarUtil} 照官 jadx 转写。
 * 记名差异：官 setColor/setColorNoTranslucent 委托官 StatusBarCompatUtil
 * （未移植），两法未落——插件面只有 getStatusBarHeight 被
 * hikerPop.updateRecordsBottom 实调；官 exoplayer IcyHeaders 常量
 * REQUEST_HEADER_ENABLE_METADATA_VALUE 按其值 "1" 内联；Timber 改 Log。
 */
public class MyStatusBarUtil {

    public static boolean isDark(int i) {
        return isDark(Double.valueOf((16711680 & i) >> 16), Double.valueOf((65280 & i) >> 8), Double.valueOf(i & 255));
    }

    public static boolean isDark(Double d, Double d2, Double d3) {
        return ((d.doubleValue() * 0.299d) + (d2.doubleValue() * 0.578d)) + (d3.doubleValue() * 0.114d) < 192.0d;
    }

    public static int getStatusBarHeight(Context context) {
        try {
            Class<?> cls = Class.forName("com.android.internal.R$dimen");
            return context.getResources().getDimensionPixelSize(Integer.parseInt(cls.getField("status_bar_height").get(cls.newInstance()).toString()));
        } catch (Exception e) {
            Log.e("MyStatusBarUtil", "getStatusBarHeight", e);
            int dpToPx = DisplayUtil.dpToPx(context, 20);
            try {
                int identifier = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
                return identifier > 0 ? context.getResources().getDimensionPixelSize(identifier) : dpToPx;
            } catch (Resources.NotFoundException e2) {
                Log.e("MyStatusBarUtil", "getStatusBarHeight", e2);
                return dpToPx;
            }
        }
    }

    public static int getNavigationBarHeight(Context context) {
        if (context.getResources().getIdentifier("config_showNavigationBar", "bool", "android") == 0) {
            return 0;
        }
        return context.getResources().getDimensionPixelSize(context.getResources().getIdentifier("navigation_bar_height", "dimen", "android"));
    }

    public static boolean checkDeviceHasNavigationBar(Context context) {
        Resources resources = context.getResources();
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

    public static int getVirtualBarHeight(Context context) {
        if (!checkDeviceHasNavigationBar(context)) {
            return 0;
        }
        WindowManager windowManager = (WindowManager) context.getSystemService("window");
        Display defaultDisplay = windowManager != null ? windowManager.getDefaultDisplay() : null;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        try {
            Class.forName("android.view.Display").getMethod("getRealMetrics", DisplayMetrics.class).invoke(defaultDisplay, displayMetrics);
            return displayMetrics.heightPixels - defaultDisplay.getHeight();
        } catch (Exception e) {
            Log.e("MyStatusBarUtil", "getVirtualBarHeight", e);
            return 0;
        }
    }

    public static boolean checkNavigationBarShow(Activity activity) {
        Display defaultDisplay = activity.getWindowManager().getDefaultDisplay();
        Point point = new Point();
        Point point2 = new Point();
        defaultDisplay.getSize(point);
        defaultDisplay.getRealSize(point2);
        return point2.y != point.y;
    }

    public static int getBottomStatusHeight(Activity activity) {
        if (checkNavigationBarShow(activity)) {
            return getDpi(activity) - getScreenHeight(activity);
        }
        return 0;
    }

    public static int getDpi(Context context) {
        Display defaultDisplay = ((WindowManager) context.getSystemService("window")).getDefaultDisplay();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        try {
            Class.forName("android.view.Display").getMethod("getRealMetrics", DisplayMetrics.class).invoke(defaultDisplay, displayMetrics);
            return displayMetrics.heightPixels;
        } catch (Exception e) {
            Log.e("MyStatusBarUtil", "getDpi", e);
            return 0;
        }
    }

    public static int getScreenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }
}
