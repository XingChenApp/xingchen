package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public abstract class BaseAlertDialog extends DialogFragment {

    protected abstract ViewBinding getBinding();

    protected abstract MaterialAlertDialogBuilder getBuilder();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = getBuilder().create();
        // 与海阔/阅读弹窗同一口径：出入淡入淡出 200ms（HkDialogFade）。
        if (dialog.getWindow() != null) dialog.getWindow().setWindowAnimations(R.style.HkDialogFade);
        initView();
        initEvent();
        return dialog;
    }

    protected MaterialAlertDialogBuilder builder() {
        return new MaterialAlertDialogBuilder(requireActivity());
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected void setWidth(float factor) {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * factor);
        getDialog().getWindow().setAttributes(params);
    }

    /**
     * onCreateDialog 专用法：彼时 getDialog() 尚为空，只能对 super 返回的本地
     * dialog 落宽。居中框摆位口径（2026-11-16 定案＋2026-11-20 全修批）：宽高与
     * 位置必须在 show 之前落定、首帧即终态——子类把原 onStart 里的 setWidth /
     * setLayout 改到 onCreateDialog 里对本地 dialog 调用本法或同形语句，
     * 不再在 onStart 里改窗。
     */
    protected void setWidth(Dialog dialog, float factor) {
        if (dialog == null || dialog.getWindow() == null) return;
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * factor);
        dialog.getWindow().setAttributes(params);
    }
}
