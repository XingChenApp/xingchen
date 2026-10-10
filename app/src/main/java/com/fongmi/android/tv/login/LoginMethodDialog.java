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
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;

/**
 * 壳子统一登录：先选登录方式，再进具体流程。
 * 深色毛玻璃风格，每个方式独立卡片、有间距。
 */
public class LoginMethodDialog extends DialogFragment {

    public interface OnPick {
        void onPick(LoginMethod method);
    }

    private OnPick onPick;
    private LoginManager.LoginCallback callback;

    public static LoginMethodDialog create() {
        return new LoginMethodDialog();
    }

    public LoginMethodDialog onPick(OnPick onPick) {
        this.onPick = onPick;
        return this;
    }

    public LoginMethodDialog callback(LoginManager.LoginCallback callback) {
        this.callback = callback;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
            if (f instanceof LoginMethodDialog) return;
        }
        show(activity.getSupportFragmentManager(), null);
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
        params.width = Math.max(dp(300), Math.min(metrics.widthPixels - dp(48), dp(400)));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);
    }

    private LinearLayout createContentView() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.setBackground(round(Color.parseColor("#F226282C"), 24));

        root.addView(createHeader(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

        TextView sub = new TextView(requireContext());
        sub.setText("选择登录方式");
        sub.setTextColor(Color.parseColor("#B3FFFFFF"));
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.setMargins(0, dp(4), 0, dp(12));
        root.addView(sub, subParams);

        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        for (LoginMethod m : LoginMethod.values()) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (list.getChildCount() > 0) p.setMargins(0, dp(8), 0, 0);
            list.addView(createCard(m), p);
        }

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(ScrollView.OVER_SCROLL_NEVER);
        scroll.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360));
        root.addView(scroll, scrollParams);
        return root;
    }

    private LinearLayout createHeader() {
        TextView title = new TextView(requireContext());
        title.setText("115 登录");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);

        MaterialButton close = new MaterialButton(requireContext());
        close.setText("✕");
        close.setTextSize(16);
        close.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        close.setAllCaps(false);
        close.setMinWidth(0);
        close.setMinHeight(dp(36));
        close.setMinimumHeight(dp(36));
        close.setPadding(0, 0, 0, 0);
        close.setInsetTop(0);
        close.setInsetBottom(0);
        close.setCornerRadius(dp(18));
        close.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        close.setTextColor(Color.parseColor("#B3FFFFFF"));
        close.setStrokeWidth(0);
        close.setOnClickListener(v -> dismissAllowingStateLoss());

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        row.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
        return row;
    }

    /** 每个登录方式一张独立卡片 */
    private LinearLayout createCard(LoginMethod m) {
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(m.title());
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvTitle.setSingleLine(true);

        TextView tvDesc = new TextView(requireContext());
        tvDesc.setText(m.desc());
        tvDesc.setTextColor(Color.parseColor("#B3FFFFFF"));
        tvDesc.setTextSize(13);
        tvDesc.setSingleLine(true);

        LinearLayout text = new LinearLayout(requireContext());
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.addView(tvTitle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(0, dp(2), 0, 0);
        text.addView(tvDesc, descParams);

        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(round(Color.parseColor("#3A3F45"), 14));
        card.setClickable(true);
        card.setFocusable(true);
        card.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (!m.isImplemented()) {
            TextView badge = new TextView(requireContext());
            badge.setText("即将上线");
            badge.setTextColor(Color.parseColor("#FFD54F"));
            badge.setTextSize(12);
            badge.setSingleLine(true);
            card.addView(badge, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            TextView arrow = new TextView(requireContext());
            arrow.setText("›");
            arrow.setTextColor(Color.parseColor("#B3FFFFFF"));
            arrow.setTextSize(22);
            card.addView(arrow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        card.setOnClickListener(v -> onCardClick(m));
        return card;
    }

    private void onCardClick(LoginMethod m) {
        if (m.isImplemented()) {
            dismissAllowingStateLoss();
            App.post(() -> {
                if (onPick != null) onPick.onPick(m);
            });
            return;
        }
        showComingSoon(m);
    }

    /** 未接入方式：小毛玻璃提示框，不伪造流程 */
    private void showComingSoon(LoginMethod m) {
        FragmentActivity activity = (FragmentActivity) getActivity();
        if (activity == null || activity.isFinishing()) return;
        ComingSoonDialog.create(m).show(activity.getSupportFragmentManager(), null);
    }

    LoginManager.LoginCallback getCallback() {
        return callback;
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
