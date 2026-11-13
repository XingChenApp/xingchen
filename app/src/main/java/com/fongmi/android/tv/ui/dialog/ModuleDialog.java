package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogModuleBinding;
import com.fongmi.android.tv.model.ModuleViewModel;
import com.fongmi.android.tv.utils.ModuleUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.RuntimeInstaller;

// 模块详情弹窗：补 ModuleActivity / ModuleFragment 调用的 ModuleDialog。
// 上游取件里没有这个文件（见交回卡点），这里按两处调用点合同实现：
// create(index).show(activity)，index 顺序与 ModuleUtil.MODULES 一致。
// 弹窗里的下载/卸载/检测/取消全走 ModuleViewModel，不另起下载链；
// 待确认态（READY / REMOVAL_READY）的「确认」与管理端同接法：调
// RuntimeInstaller.confirmInstallation 拉起系统确认（确认意图由安装器
// 在提交会话时保存）。index 走 arguments，系统重建弹窗时能原样恢复。
public final class ModuleDialog extends BaseBottomSheetDialog {

    private static final String ARG_INDEX = "index";

    private int index = -1;
    private DialogModuleBinding binding;
    private ModuleViewModel model;
    private ModuleViewModel.State lastState;
    private ModuleViewModel.Health lastHealth;

    public ModuleDialog() {
    }

    public static ModuleDialog create(int index) {
        if (index < 0 || index >= ModuleUtil.MODULES.length) throw new IllegalArgumentException("Invalid module index");
        ModuleDialog dialog = new ModuleDialog();
        Bundle arguments = new Bundle();
        arguments.putInt(ARG_INDEX, index);
        dialog.setArguments(arguments);
        return dialog;
    }

    private int index() {
        if (index < 0) {
            Bundle arguments = getArguments();
            if (arguments != null) index = arguments.getInt(ARG_INDEX, -1);
        }
        return index;
    }

    public void show(FragmentActivity activity) {
        int index = index();
        if (index < 0 || index >= ModuleUtil.MODULES.length) throw new IllegalStateException("ModuleDialog missing module index");
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) if (fragment instanceof ModuleDialog) return;
        model = ModuleViewModel.get(activity, ModuleUtil.MODULES[index]);
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogModuleBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (model == null) {
            // 系统重建弹窗时 show() 不会重调：按 arguments 的 index 把模型接回来，
            // 接不回就关窗，不许裸解 model 崩在重建路上
            FragmentActivity activity = getActivity();
            int idx = index();
            if (activity == null || idx < 0 || idx >= ModuleUtil.MODULES.length) {
                dismissAllowingStateLoss();
                return;
            }
            model = ModuleViewModel.get(activity, ModuleUtil.MODULES[idx]);
        }
        binding.moduleTitle.setText(model.getTitle());
        model.getState().observe(getViewLifecycleOwner(), state -> {
            lastState = state;
            render();
        });
        model.getHealth().observe(getViewLifecycleOwner(), health -> {
            lastHealth = health;
            render();
        });
        // 重开时别停在上次的失败态：模型自己会判断要不要重新检测，
        // 检测失败的原因照样显示在弹窗里，安装钮也能再点。
        model.refresh();
    }

    @Override
    protected void initEvent() {
        binding.btnTest.setOnClickListener(v -> model.checkRuntime());
        binding.btnInstall.setOnClickListener(v -> {
            if (lastState != null && lastState.isPending()) confirmPending();
            else model.install();
        });
        binding.btnRemove.setOnClickListener(v -> model.remove());
        binding.btnCancel.setOnClickListener(v -> {
            ModuleViewModel.State state = lastState;
            if (state != null && state.phase() == ModuleViewModel.Phase.REMOVAL_READY) {
                // 待确认卸载的取消：先让模型取消会话并复位状态，随即关窗，
                // 不留一个亮着却点了没反应的取消钮。
                model.cancel();
                dismissAllowingStateLoss();
            } else if (state != null && (state.isBusy() || state.isPending())) model.cancel();
            else dismissAllowingStateLoss();
        });
    }

    // 待确认态的「确认」：接法与 ModuleFragment / ModuleActivity 的
    // confirmInstallation 一致——先提示当前状态，再让安装器拿已保存的确认意图
    // 拉起系统确认；READY 是确认安装、REMOVAL_READY 是确认卸载，走同一条链。
    // 待确认态的取消仍走 model.cancel()（取消待确认的会话）。
    private void confirmPending() {
        if (lastState != null) Notify.show(model.getStatusText(lastState));
        FragmentActivity activity = getActivity();
        if (activity != null) RuntimeInstaller.confirmInstallation(activity);
    }

    private void render() {
        if (binding == null || lastState == null) return;
        binding.moduleStatus.setText(model.getStatusText(lastState));
        String error = lastState.error();
        if (error == null && lastHealth != null && lastHealth.phase() == ModuleViewModel.HealthPhase.FAILED) error = lastHealth.error();
        if (error == null) {
            binding.moduleError.setVisibility(View.GONE);
        } else {
            binding.moduleError.setVisibility(View.VISIBLE);
            binding.moduleError.setText(error);
        }
        boolean downloading = lastState.phase() == ModuleViewModel.Phase.INSTALLING && lastState.total() > 0 && lastState.downloaded() >= 0;
        binding.moduleProgress.setVisibility(downloading ? View.VISIBLE : View.GONE);
        binding.moduleProgressText.setVisibility(downloading ? View.VISIBLE : View.GONE);
        if (downloading) {
            int percent = (int) (100L * lastState.downloaded() / lastState.total());
            binding.moduleProgress.setProgress(percent);
            binding.moduleProgressText.setText(percent + "%");
        }
        binding.btnTest.setEnabled(lastState.canTest());
        // 待确认态安装钮变确认钮：READY 显示「确认安装」、REMOVAL_READY 显示
        // 「确认卸载」，点击走 confirmPending()；其余状态恢复「安装」与原口径。
        boolean pending = lastState.isPending();
        binding.btnInstall.setEnabled(pending || lastState.canInstall());
        binding.btnInstall.setText(pending
                ? lastState.phase() == ModuleViewModel.Phase.REMOVAL_READY ? R.string.module_confirm_remove : R.string.module_confirm_install
                : R.string.module_install);
        binding.btnRemove.setEnabled(lastState.canRemove());
        // 取消钮只在点了真有效时才亮：忙态里 REMOVING / CANCELLING 模型明确
        // 不受理取消（一个已在收尾、一个已在取消），置灰免得亮着无效；
        // 其余忙态与待确认态点了都真取消。
        ModuleViewModel.Phase phase = lastState.phase();
        binding.btnCancel.setEnabled(pending
                || lastState.isBusy() && phase != ModuleViewModel.Phase.REMOVING && phase != ModuleViewModel.Phase.CANCELLING);
    }
}
