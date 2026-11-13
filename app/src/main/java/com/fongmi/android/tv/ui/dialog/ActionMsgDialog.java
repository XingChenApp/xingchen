package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.Html;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogActionMsgBinding;
import com.fongmi.android.tv.utils.ResUtil;

/**
 * 动作协议 msgbox 指令的呈现面：{'action': {type:'msgbox', title, htmlMsg}}。
 * 内层 py 的文字结果靠它才真的弹出来（协议 E10）。样式与点播域玻璃弹窗同一家
 * （shape_dialog_glass_white_panel 浅面毛玻璃＋深字），窗口口径照 AboutDialog。
 */
public final class ActionMsgDialog {

    private ActionMsgDialog() {
    }

    public static boolean show(Activity activity, String title, String htmlMsg) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        DialogActionMsgBinding binding = DialogActionMsgBinding.inflate(LayoutInflater.from(activity));
        if (title == null || title.isEmpty()) binding.actionMsgTitle.setVisibility(android.view.View.GONE);
        else binding.actionMsgTitle.setText(title);
        binding.actionMsgText.setText(fromHtml(htmlMsg == null ? "" : htmlMsg));
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(binding.getRoot());
        binding.actionMsgConfirm.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        configureWindow(activity, dialog);
        binding.actionMsgConfirm.requestFocus();
        return true;
    }

    private static CharSequence fromHtml(String html) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        return Html.fromHtml(html);
    }

    private static void configureWindow(Activity activity, Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(activity) * (ResUtil.isLand(activity) ? 0.62f : 0.92f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
        window.setWindowAnimations(R.style.HkDialogFade);
    }
}
