package com.fongmi.android.tv.ui.dialog;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.Nullable;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.DialogHistoryBinding;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.ui.adapter.ConfigAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class HistoryDialog extends BaseAlertDialog implements ConfigAdapter.OnClickListener {

    private DialogHistoryBinding binding;
    private ConfigListener listener;
    private ConfigAdapter adapter;

    private int type;
    private boolean readOnly;

    public static HistoryDialog create() {
        return new HistoryDialog();
    }

    public HistoryDialog vod() {
        type = 0;
        return this;
    }

    public HistoryDialog live() {
        type = 1;
        return this;
    }

    public HistoryDialog wall() {
        type = 2;
        return this;
    }

    public HistoryDialog readOnly() {
        readOnly = true;
        return this;
    }

    public void show(FragmentActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || activity.getSupportFragmentManager().isStateSaved()) return;
        show(activity.getSupportFragmentManager(), null);
    }

    public void show(Fragment fragment) {
        FragmentActivity activity = fragment == null ? null : fragment.getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || fragment.getChildFragmentManager().isStateSaved()) return;
        show(fragment.getChildFragmentManager(), null);
    }

    private boolean isFull() {
        return getParentFragment() == null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Object host = isFull() ? context : getParentFragment();
        listener = host instanceof ConfigListener configListener ? configListener : null;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new ConfigAdapter(this);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        if (isFull()) binding.recycler.setMaxHeight(ResUtil.dp2px(264));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.setAdapter(adapter.readOnly(readOnly).addAll(type, getConfig()));
    }

    private Config getConfig() {
        return switch (type) {
            case 0 -> VodConfig.get().getConfig();
            case 1 -> LiveConfig.get().getConfig();
            case 2 -> WallConfig.get().getConfig();
            default -> Config.create(type);
        };
    }

    @Override
    public void onTextClick(Config item) {
        if (listener != null) listener.setConfig(item);
        dismiss();
    }

    @Override
    public void onDeleteClick(Config item) {
        if (adapter.remove(item, type) == 0) dismiss();
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        // 摆位在 show 之前落定（原 onStart 里 configureWindow 是出框后改宽，会动一下）；
        // 列表为空时 onStart 仍会自关，宽先落不影响那条路
        configureWindow(dialog);
        return dialog;
    }

    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
    }

    private void configureWindow(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        int width = Math.min(Math.round(ResUtil.getScreenWidth(requireContext()) * (land ? 0.5f : 0.84f)), ResUtil.dp2px(520));
        params.width = Math.max(width, ResUtil.dp2px(320));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }
}
