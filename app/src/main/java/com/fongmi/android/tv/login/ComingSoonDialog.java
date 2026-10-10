package com.fongmi.android.tv.login;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;

/**
 * 小毛玻璃提示框：某登录方式正在接入中。
 */
public class ComingSoonDialog extends DialogFragment {

    private LoginMethod method = LoginMethod.PASSWORD;

    public static ComingSoonDialog create(LoginMethod method) {
        ComingSoonDialog d = new ComingSoonDialog();
        if (method != null) d.method = method;
        return d;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(createContentView());
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        Window.LayoutParams params = window.getAttributes();
        params.width = Math.max(dp(280), Math.min(metrics.widthPixels - dp(96), dp(340)));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);
    }

    private LinearLayout createContentView() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.setBackground(round(Color.parseColor("#F226282C"), 20));

        TextView msg = new TextView(requireContext());
        msg.setText("「" + method.title() + "」正在接入中，\n请先用扫码或 Cookie 导入登录");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(15);
        msg.setGravity(Gravity.CENTER);
        msg.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.setMargins(0, 0, 0, dp(16));
        root.addView(msg, msgParams);

        MaterialButton ok = new MaterialButton(requireContext());
        ok.setText("我知道了");
        ok.setTextSize(15);
        ok.setAllCaps(false);
        ok.setMinWidth(0);
        ok.setInsetTop(0);
        ok.setInsetBottom(0);
        ok.setCornerRadius(dp(12));
        ok.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F5C518")));
        ok.setTextColor(Color.parseColor("#101216"));
        ok.setStrokeWidth(0);
        ok.setOnClickListener(v -> dismissAllowingStateLoss());
        root.addView(ok, new LinearLayout.LayoutParams(dp(160), dp(46)));
        ((LinearLayout.LayoutParams) ok.getLayoutParams()).gravity = Gravity.CENTER;
        return root;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(int v) {
        return ResUtil.dp2px(v);
    }
}
