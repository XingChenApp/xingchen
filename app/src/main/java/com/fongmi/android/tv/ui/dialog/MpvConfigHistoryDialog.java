package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogHistoryBinding;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.fongmi.android.tv.ui.adapter.MpvConfigHistoryAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MpvConfigHistoryDialog extends BaseAlertDialog implements MpvConfigHistoryAdapter.OnClickListener {

    private DialogHistoryBinding binding;
    private MpvConfigHistoryAdapter adapter;
    private Listener listener;
    private String target;

    public static void show(FragmentManager manager, String target, Listener listener) {
        MpvConfigHistoryDialog dialog = new MpvConfigHistoryDialog();
        dialog.target = target;
        dialog.listener = listener;
        dialog.show(manager, null);
    }

    public interface Listener {

        void onHistoryClick(int position);

        void onHistoryChanged();
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        MaterialAlertDialogBuilder builder = Util.isLeanback() ? builder() : new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog);
        return builder.setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new MpvConfigHistoryAdapter(MpvConfigStore.historyLabels(target), this);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, Util.isLeanback() ? 16 : 8));
        binding.recycler.setAdapter(adapter);
    }

    @Override
    public void onTextClick(int position) {
        if (listener != null) listener.onHistoryClick(position);
        dismiss();
    }

    @Override
    public void onDeleteClick(int position) {
        if (!MpvConfigStore.removeHistory(target, position)) return;
        adapter.remove(position);
        if (listener != null) listener.onHistoryChanged();
        if (adapter.getItemCount() == 0) dismiss();
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        // 摆位在 show 之前落定（原 onStart 里 setWidth/configureWindow 是出框后改宽，会动一下）；
        // 列表为空时 onStart 仍会自关，宽先落不影响那条路
        if (Util.isLeanback()) setWidth(dialog, 0.4f);
        else configureWindow(dialog);
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
