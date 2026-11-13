package com.fongmi.android.tv.login;

import com.fongmi.android.tv.R;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;

/**
 * 壳子统一登录框：115 账号密码直登（{@link Pwd115Provider}）。
 * 与扫码框同套深色玻璃口径；密码只在内存过手，不落盘不进日志。
 */
public class PwdLoginDialog extends DialogFragment {

    private final Pwd115Provider provider = new Pwd115Provider();

    private EditText etAccount;
    private EditText etPassword;
    private TextView tvStatus;
    private MaterialButton btnLogin;
    private LoginManager.LoginCallback callback;
    private volatile boolean stopped;
    private volatile boolean callbackFired;
    private Thread worker;

    private void fireCancel() {
        if (callbackFired) return;
        callbackFired = true;
        if (callback != null) callback.onCancel();
    }

    private void fireSuccess(String cookie) {
        if (callbackFired) return;
        callbackFired = true;
        if (callback != null) callback.onSuccess("115", cookie);
    }

    public static PwdLoginDialog create() {
        return new PwdLoginDialog();
    }

    public PwdLoginDialog callback(LoginManager.LoginCallback callback) {
        this.callback = callback;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof PwdLoginDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        if (dialog.getWindow() != null) dialog.getWindow().setWindowAnimations(R.style.HkDialogFade); // 统一淡入淡出 200ms
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
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = Math.max(dp(300), Math.min(metrics.widthPixels - dp(48), dp(400)));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        stopped = true;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        super.onDismiss(dialog);
    }

    // ---------------- UI ----------------

    private LinearLayout createContentView() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.setBackground(new LoginGlassDrawable(requireContext(), 24f));

        root.addView(createHeader(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

        TextView hint = new TextView(requireContext());
        hint.setText("输入 115 账号和密码登录");
        hint.setTextColor(Color.parseColor("#B3FFFFFF"));
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.setMargins(0, dp(4), 0, dp(14));
        root.addView(hint, hintParams);

        etAccount = input("手机号 / 邮箱 / 账号", InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_NEXT);
        root.addView(etAccount, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        etPassword = input("密码", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE);
        LinearLayout.LayoutParams pwdParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        pwdParams.setMargins(0, dp(10), 0, 0);
        root.addView(etPassword, pwdParams);
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doLogin();
                return true;
            }
            return false;
        });

        tvStatus = new TextView(requireContext());
        tvStatus.setText("");
        tvStatus.setTextColor(Color.parseColor("#B3FFFFFF"));
        tvStatus.setTextSize(14);
        tvStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(14), 0, dp(16));
        root.addView(tvStatus, statusParams);

        root.addView(createButtonRow(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private EditText input(String hint, int inputType, int imeOptions) {
        EditText et = new EditText(requireContext());
        et.setHint(hint);
        et.setHintTextColor(Color.parseColor("#80FFFFFF"));
        et.setTextColor(Color.WHITE);
        et.setTextSize(15);
        et.setSingleLine(true);
        et.setInputType(inputType);
        et.setImeOptions(imeOptions);
        et.setPadding(dp(14), 0, dp(14), 0);
        et.setBackground(round(Color.parseColor("#3A3F45"), 12));
        return et;
    }

    private LinearLayout createHeader() {
        TextView title = new TextView(requireContext());
        title.setText("115 · 账号密码");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);

        MaterialButton close = ghostButton("✕");
        close.setOnClickListener(v -> {
            fireCancel();
            dismissAllowingStateLoss();
        });

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        row.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
        return row;
    }

    private LinearLayout createButtonRow() {
        btnLogin = cardButton("登录", true);
        btnLogin.setOnClickListener(v -> doLogin());

        MaterialButton btnCancel = cardButton("取消", false);
        btnCancel.setOnClickListener(v -> {
            fireCancel();
            dismissAllowingStateLoss();
        });

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(46), 1);
        p1.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(46), 1);
        p2.setMargins(dp(6), 0, 0, 0);
        row.addView(btnLogin, p1);
        row.addView(btnCancel, p2);
        return row;
    }

    /** 独立卡片按钮：有间距、深色字看得清（与扫码框同套） */
    private MaterialButton cardButton(String text, boolean primary) {
        MaterialButton b = new MaterialButton(requireContext());
        b.setText(text);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setCornerRadius(dp(12));
        b.setStrokeWidth(dp(1));
        if (primary) {
            b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0B57D0")));
            b.setTextColor(Color.WHITE);
            b.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#0B57D0")));
        } else {
            b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3A3F45")));
            b.setTextColor(Color.WHITE);
            b.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#5A626E")));
        }
        return b;
    }

    private MaterialButton ghostButton(String text) {
        MaterialButton b = new MaterialButton(requireContext());
        b.setText(text);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinHeight(dp(36));
        b.setMinimumHeight(dp(36));
        b.setPadding(0, 0, 0, 0);
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setCornerRadius(dp(18));
        b.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        b.setTextColor(Color.parseColor("#B3FFFFFF"));
        b.setStrokeWidth(0);
        return b;
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

    // ---------------- 登录流程 ----------------

    private void doLogin() {
        String account = etAccount.getText() == null ? "" : etAccount.getText().toString().trim();
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString();
        if (TextUtils.isEmpty(account) || TextUtils.isEmpty(password)) {
            setStatus("账号和密码不能为空", false);
            return;
        }
        stopped = false;
        btnLogin.setEnabled(false);
        setStatus("正在登录…", false);
        worker = new Thread(() -> {
            String cookie;
            try {
                cookie = provider.login(account, password);
            } catch (Pwd115Provider.TwoStepException e) {
                App.post(() -> {
                    if (stopped) return;
                    btnLogin.setEnabled(true);
                    setStatus("账号开启了短信二步验证，请改用扫码登录", false);
                });
                return;
            } catch (Exception e) {
                String msg = safeMsg(e);
                App.post(() -> {
                    if (stopped) return;
                    btnLogin.setEnabled(true);
                    setStatus("登录失败：" + msg, false);
                });
                return;
            }
            App.post(() -> {
                if (stopped) return;
                setStatus("登录成功", true);
                Notify.show("115 登录成功");
                fireSuccess(cookie);
                dismissAllowingStateLoss();
            });
        }, "Pwd115Login");
        worker.start();
    }

    private void setStatus(String text, boolean ok) {
        if (tvStatus == null) return;
        tvStatus.setText(text);
        tvStatus.setTextColor(Color.parseColor(ok ? "#7ED492" : "#B3FFFFFF"));
    }

    private String safeMsg(Exception e) {
        String m = e == null ? "" : e.getMessage();
        if (TextUtils.isEmpty(m)) return "未知错误";
        return m.length() > 60 ? m.substring(0, 60) : m;
    }

    @Override
    public void onDestroyView() {
        stopped = true;
        if (!new Qr115Provider().isLoggedIn()) fireCancel();
        super.onDestroyView();
    }
}
