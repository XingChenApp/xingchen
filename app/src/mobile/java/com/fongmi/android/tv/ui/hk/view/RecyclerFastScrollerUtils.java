package com.fongmi.android.tv.ui.hk.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.view.View;

/** 照抄 8.83 内嵌的 lqtk RecyclerFastScrollerUtils（isRTL 的返回值与字面语义相反是原库笔误，照抄）。 */
public class RecyclerFastScrollerUtils {

    public static int convertDpToPx(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    public static boolean isRTL(Context context) {
        return context.getResources().getConfiguration().getLayoutDirection() == 0;
    }

    public static int resolveColor(Context context, int attr) {
        TypedArray array = context.obtainStyledAttributes(new int[]{attr});
        int color = array.getColor(0, 0);
        array.recycle();
        return color;
    }

    public static void setViewBackground(View view, Drawable drawable) {
        view.setBackground(drawable);
    }
}
