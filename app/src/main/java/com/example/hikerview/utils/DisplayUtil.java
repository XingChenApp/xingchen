package com.example.hikerview.utils;

import android.content.Context;

/**
 * 8.83 {@code com.example.hikerview.utils.DisplayUtil} 的同包名同签名补齐
 * （官包 jadx 反编译原样，全类仅四法）。
 */
public class DisplayUtil {
    public static int pxToDp(Context context, int i) {
        if (context == null || i <= 0) {
            return i;
        }
        return (int) ((i / context.getResources().getDisplayMetrics().density) + 0.5f);
    }

    public static int dpToPx(Context context, int i) {
        if (context == null || i <= 0) {
            return i;
        }
        return (int) ((i * context.getResources().getDisplayMetrics().density) + 0.5f);
    }

    public static int dpToPx(Context context, float f) {
        if (context != null && f > 0.0f) {
            return (int) ((f * context.getResources().getDisplayMetrics().density) + 0.5f);
        }
        return (int) f;
    }

    public static int sp2px(Context context, float f) {
        return (int) ((f * context.getResources().getDisplayMetrics().scaledDensity) + 0.5f);
    }
}
