package com.fongmi.android.tv.login;

import android.app.Dialog;
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
import com.fongmi.android.tv.utils.PanAuth;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 115 Cookie 导入登录：粘贴 Cookie（含 UID / CID / SEID 即可），本地校验后保存。
 * 深色毛玻璃风格。
 */
public class CookieImportDialog extends DialogFragment {

    private static final Pattern PAIR = Pattern.compile("(UID|CID|SEID|KID)\\s*=\\s*([^;,\\s\"']+)");

    private EditText input;
    private LoginManager.LoginCallback callback;
    private volatile boolean callbackFired;

    public static CookieImportDialog create() {
        return new CookieImportDialog();
    }

    public CookieImportDialog callback(LoginManager.LoginCallback callback) {
        this.callback = callback;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
            if (f instanceof CookieImportDialog) return;
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
        params.width = Math.max(dp(300), Math.min(metrics.widthPixels - dp(48), dp(420)));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        window.setAttributes(params);
    }

    private LinearLayout createContentView() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.setBackground(round(Color.parseColor("#F226282C"), 24));

        root.addView(createHeader(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

        TextView hint = new TextView(requireContext());
        hint.setText("粘贴 115 Cookie（需包含 UID、CID、SEID）");
        hint.setTextColor(Color.parseColor("#B3FFFFFF"));
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.setMargins(0, dp(4), 0, dp(12));
        root.addView(hint, hintParams);

        input = new EditText(requireContext());
        input.setHint("UID=…;CID=…;SEID=…");
        input.setHintTextColor(Color.parseColor("#6F7782"));
        input.setTextColor(Color.WHITE);
        input.setTextSize(14);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(4);
        input.setMaxLines(6);
        input.setGravity(Gravity.START | Gravity.TOP);
        int pad = dp(12);
        input.setPadding(pad, pad, pad, pad);
        input.setBackground(round(Color.parseColor("#2A2E35"), 12));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.setMargins(0, 0, 0, dp(16));
        root.addView(input, inputParams);

        root.addView(createButtonRow(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private LinearLayout createHeader() {
        TextView title = new TextView(requireContext());
        title.setText("115 · Cookie 导入");
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
        MaterialButton ok = cardButton("导入登录", true);
        ok.setOnClickListener(v -> doImport());

        MaterialButton cancel = cardButton("取消", false);
        cancel.setOnClickListener(v -> {
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
        row.addView(ok, p1);
        row.addView(cancel, p2);
        return row;
    }

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

    private void doImport() {
        String raw = input == null || input.getText() == null ? "" : input.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) {
            Notify.show("请先粘贴 Cookie");
            return;
        }
        String cookie = normalize(raw);
        if (!PanAuth.is115CookieValid(cookie)) {
            Notify.show("Cookie 无效：缺少 UID / CID / SEID");
            return;
        }
        PanAuth.put115Cookie(cookie);
        Notify.show("115 登录成功");
        App.post(() -> {
            if (!callbackFired) {
                callbackFired = true;
                if (callback != null) callback.onSuccess("115", cookie);
            }
            dismissAllowingStateLoss();
        });
    }

    /** 从粘贴文本中提取 UID / CID / SEID / KID，拼成标准格式 */
    private String normalize(String raw) {
        String uid = "", cid = "", seid = "", kid = "";
        Matcher m = PAIR.matcher(raw);
        while (m.find()) {
            String k = m.group(1);
            String v = m.group(2);
            if ("UID".equals(k)) uid = v;
            else if ("CID".equals(k)) cid = v;
            else if ("SEID".equals(k)) seid = v;
            else if ("KID".equals(k)) kid = v;
        }
        StringBuilder sb = new StringBuilder();
        if (!uid.isEmpty()) sb.append("UID=").append(uid);
        if (!cid.isEmpty()) sb.append(sb.length() > 0 ? ";" : "").append("CID=").append(cid);
        if (!seid.isEmpty()) sb.append(sb.length() > 0 ? ";" : "").append("SEID=").append(seid);
        if (!kid.isEmpty()) sb.append(sb.length() > 0 ? ";" : "").append("KID=").append(kid);
        return sb.toString();
    }

    private void fireCancel() {
        if (callbackFired) return;
        callbackFired = true;
        if (callback != null) callback.onCancel();
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
