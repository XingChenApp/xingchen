package com.example.hikerview.ui.view.toast;

import android.view.View;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * 照官 8.83 本体移植（jadx 官包源，Kotlin 文件级函数类）：
 * make(view, text, duration) 一站式：起手、设时长、设文案、
 * 取消钮固定「取消」空回调。
 */
public final class ChefSnackbarKt {

    public static ChefSnackbar make(View view, String text, int i) {
        return ChefSnackbar.Companion.make(view).setDuration(i).setText(text).setCancelButton("取消", (Function1<View, Unit>) view2 -> Unit.INSTANCE);
    }
}
