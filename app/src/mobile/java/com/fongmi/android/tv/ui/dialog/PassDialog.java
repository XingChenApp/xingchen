package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogPassBinding;
import com.fongmi.android.tv.impl.PassListener;
import com.fongmi.android.tv.utils.ResUtil;

public class PassDialog extends BaseBottomSheetDialog {

    private DialogPassBinding binding;

    public static PassDialog create() {
        return new PassDialog();
    }

    public void show(FragmentActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || activity.getSupportFragmentManager().isStateSaved()) return;
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof PassDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogPassBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initEvent() {
        binding.pass.setOnEditorActionListener(this::onDone);
    }

    private void onPass() {
        String pass = binding.pass.getText().toString().trim();
        if (!pass.isEmpty() && requireActivity() instanceof PassListener) ((PassListener) requireActivity()).setPass(pass);
        dismiss();
    }

    private boolean onDone(TextView view, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) onPass();
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) dialog.getWindow().setLayout(ResUtil.dp2px(250), -1);
    }
}
