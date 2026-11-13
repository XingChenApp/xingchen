package com.example.hikerview.ui.view.toast;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.hikerview.R;
import com.google.android.material.snackbar.ContentViewCallback;

/**
 * 照官 8.83 本体移植（jadx 官包源，Kotlin 类）：Snackbar 内容视图，
 * 构造时把 pop_open_message 布局涨进自身；进出动画回调官为空实现。
 */
public class ChefSnackbarView extends ConstraintLayout implements ContentViewCallback {

    public ChefSnackbarView(Context context) {
        this(context, null, 0);
    }

    public ChefSnackbarView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public ChefSnackbarView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        View.inflate(context, R.layout.pop_open_message, this);
    }

    @Override
    public void animateContentIn(int delay, int duration) {
    }

    @Override
    public void animateContentOut(int delay, int duration) {
    }
}
