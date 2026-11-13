package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public abstract class BaseBottomSheetDialog extends BottomSheetDialogFragment {

    protected abstract ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            if (!isAdded() || getContext() == null) return;
            setBehavior(dialog);
        });
        Window window = dialog.getWindow();
        if (window == null) return dialog;
        // 进出统一口径：滑上滑下＋淡入淡出 200ms（与裸 Dialog 底部面板的 XcBottomSheetSlide 同一套）。
        // 只换窗口动画，Material 的拖拽/行为动画不走窗口动画、不受影响；子类若自设窗口动画，
        // 其 onCreateDialog 在 super 之后执行、仍以子类为准。
        window.setWindowAnimations(R.style.XcBottomSheetSlide);
        if (stableOverlay()) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setDimAmount(0f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            WindowCompat.setDecorFitsSystemWindows(window, true);
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            if (Util.isFullscreen(getActivity())) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return getBinding(inflater, container).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView();
        initEvent();
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected boolean transparent() {
        return false;
    }

    protected boolean stableOverlay() {
        return false;
    }

    /**
     * 全子类共用的 from() 兜底：BottomSheetBehavior.from() 在 sheet 未关联
     * BottomSheetBehavior 时抛 IllegalArgumentException（主线程未捕获即崩），
     * 日志里没有出事 dialog 的类名，说明任何子类都可能撞上——所以兜底必须放在
     * 基类、由全部 9 个子类共用，而不是只修队列面板那一处。拿不到 behavior 就
     * 降级：对话框照常可用，只是不强制展开／跳过折叠态，绝不崩。
     */
    protected static <V extends View> BottomSheetBehavior<V> behaviorOf(V sheet) {
        if (sheet == null) return null;
        try {
            return BottomSheetBehavior.from(sheet);
        } catch (IllegalArgumentException e) {
            SpiderDebug.log("bottomsheet", "behavior unavailable, degraded: %s", e.getMessage());
            return null;
        }
    }

    protected void setBehavior(BottomSheetDialog dialog) {
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        sheet.setClickable(true);
        sheet.setFocusable(true);
        if (transparent()) {
            int color = ResUtil.getColor(R.color.transparent);
            sheet.setBackgroundColor(color);
            if (sheet.getParent() instanceof View) ((View) sheet.getParent()).setBackgroundColor(color);
            Window window = dialog.getWindow();
            if (window != null) window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        BottomSheetBehavior<FrameLayout> behavior = behaviorOf(sheet);
        if (behavior == null) return;
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
    }
}
