package com.example.hikerview.ui.view.toast;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

/**
 * 照官 8.83 本体移植（jadx 官包源，Kotlin 顶层函数类）：自下而上找
 * 合适的 Snackbar 挂载父容器——CoordinatorLayout 优先，其次
 * android.R.id.content 的 FrameLayout，再次最近的 FrameLayout。
 */
public final class ViewExtensionsKt {

    public static ViewGroup findSuitableParent(View view) {
        ViewGroup viewGroup = null;
        while (!(view instanceof CoordinatorLayout)) {
            if (view instanceof FrameLayout) {
                if (((FrameLayout) view).getId() == 16908290) {
                    return (ViewGroup) view;
                }
                viewGroup = (ViewGroup) view;
            }
            if (view != null) {
                Object parent = view.getParent();
                view = parent instanceof View ? (View) parent : null;
            }
            if (view == null) {
                return viewGroup;
            }
        }
        return (ViewGroup) view;
    }
}
