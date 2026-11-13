package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class WebDialog {

    private final AlertDialog dialog;

    public static WebDialog create(View view) {
        return new WebDialog(view);
    }

    private WebDialog(View view) {
        this.dialog = new MaterialAlertDialogBuilder(view.getContext(), com.fongmi.android.tv.R.style.ThemeOverlay_WebHTV_LightDialog).setView(view).create();
        if (view instanceof DialogInterface.OnDismissListener listener) this.dialog.setOnDismissListener(listener);
    }

    public WebDialog show() {
        initDialog();
        return this;
    }

    public void dismiss() {
        dialog.setOnDismissListener(null);
        dialog.dismiss();
    }

    private void initDialog() {
        dialog.show();
    }
}
