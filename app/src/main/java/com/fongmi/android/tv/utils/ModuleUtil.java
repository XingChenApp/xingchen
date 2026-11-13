package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.databinding.ItemModuleBinding;
import com.fongmi.android.tv.model.ModuleViewModel;
import com.github.catvod.utils.RuntimeModule;

// 模块行渲染：补 ModuleActivity / ModuleFragment 调用的 ModuleUtil。
// 上游取件里没有这个文件（见交回卡点），这里按两处调用点合同实现，
// 不声称上游原文：isBusy(models)、render(binding, model, state)。
// MODULES 顺序必须与 ModuleActivity / ModuleFragment 的 models 数组一致，
// ModuleDialog.create(index) 也按同一顺序取模块，改一处就要三处一起改。
// 迅雷已改回内置主包，不再走外置模块链，此列不含 THUNDER（见 Updater 对
// checkModules 的同口径排除）。
public final class ModuleUtil {

    public static final RuntimeModule[] MODULES = {
            RuntimeModule.BDJ, RuntimeModule.PYTHON, RuntimeModule.NODEJS,
            RuntimeModule.QUICKJS, RuntimeModule.MPV
    };

    private ModuleUtil() {
    }

    public static boolean isBusy(ModuleViewModel[] models) {
        if (models == null) return false;
        for (ModuleViewModel model : models) {
            if (model == null) continue;
            ModuleViewModel.State state = model.getState().getValue();
            if (state != null && state.isBusy()) return true;
        }
        return false;
    }

    public static void render(ItemModuleBinding binding, ModuleViewModel model, ModuleViewModel.State state) {
        if (binding == null || model == null || state == null) return;
        binding.status.setText(model.getStatusText(state));
        binding.progress.setWaveSpeed(state.isBusy() ? 1 : 0);
        if (state.total() > 0 && state.downloaded() >= 0) {
            binding.progress.setProgress((float) state.downloaded() / (float) state.total());
        } else {
            binding.progress.setProgress(0f);
        }
    }
}
