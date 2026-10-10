package com.fongmi.android.tv.login;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.QRCode;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;

/**
 * 壳子统一登录框：115 扫码登录。
 * 深色毛玻璃风格（用户不喜欢白底），二维码居中，状态文字实时更新。
 * 源（py / js / 海阔规则）不用自己弹框，直接调 {@link LoginManager#login115()}。
 */
public class QrLoginDialog extends DialogFragment {

    private Qr115Provider provider = new Qr115Provider();
    private LoginMethod method = LoginMethod.WEB_QR;

    private ImageView qrView;
    private TextView tvStatus;
    private MaterialButton btnRefresh;
    private MaterialButton btnCancel;
    private LoginManager.LoginCallback callback;
    private volatile boolean stopped;
    private volatile boolean refreshRequested;
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

    public static QrLoginDialog create() {
        return create(LoginMethod.WEB_QR);
    }

    public static QrLoginDialog create(LoginMethod method) {
        QrLoginDialog d = new QrLoginDialog();
        d.method = method != null ? method : LoginMethod.WEB_QR;
        d.provider = new Qr115Provider(d.method);
        return d;
    }

    public QrLoginDialog callback(LoginManager.LoginCallback callback) {
        this.callback = callback;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof QrLoginDialog) return;
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
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = Math.max(dp(300), Math.min(metrics.widthPixels - dp(48), dp(400)));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);
    }

    @Override
    public void onResume() {
        super.onResume();
        startWorker();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        stopWorker();
        super.onDismiss(dialog);
    }

    // ---------------- UI ----------------

    private LinearLayout createContentView() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.setBackground(round(Color.parseColor("#F226282C"), 24));

        root.addView(createHeader(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

        TextView hint = new TextView(requireContext());
        hint.setText(method.desc());
        hint.setTextColor(Color.parseColor("#B3FFFFFF"));
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.setMargins(0, dp(4), 0, dp(14));
        root.addView(hint, hintParams);

        LinearLayout qrBox = new LinearLayout(requireContext());
        qrBox.setGravity(Gravity.CENTER);
        qrBox.setBackground(round(Color.WHITE, 16));
        int qrPad = dp(14);
        qrBox.setPadding(qrPad, qrPad, qrPad, qrPad);
        qrView = new ImageView(requireContext());
        qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrBox.addView(qrView, new LinearLayout.LayoutParams(dp(220), dp(220)));
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        boxParams.gravity = Gravity.CENTER;
        root.addView(qrBox, boxParams);

        tvStatus = new TextView(requireContext());
        tvStatus.setText("正在获取二维码…");
        tvStatus.setTextColor(Color.parseColor("#B3FFFFFF"));
        tvStatus.setTextSize(14);
        tvStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(14), 0, dp(16));
        root.addView(tvStatus, statusParams);

        root.addView(createButtonRow(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private LinearLayout createHeader() {
        TextView title = new TextView(requireContext());
        title.setText("115 · " + method.title());
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
        btnRefresh = cardButton("刷新二维码", false);
        btnRefresh.setEnabled(false);
        btnRefresh.setOnClickListener(v -> refreshRequested = true);

        btnCancel = cardButton("取消", false);
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
        row.addView(btnRefresh, p1);
        row.addView(btnCancel, p2);
        return row;
    }

    /** 独立卡片按钮：有间距、深色字看得清 */
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

    // ---------------- 扫码流程 ----------------

    private void startWorker() {
        stopWorker();
        stopped = false;
        refreshRequested = false;
        worker = new Thread(this::loop, "Qr115Login");
        worker.start();
    }

    private void stopWorker() {
        stopped = true;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    private void loop() {
        while (!stopped) {
            refreshRequested = false;
            Qr115Provider.QrSession session;
            try {
                session = provider.requestQr();
            } catch (Exception e) {
                postStatus("网络错误：" + safeMsg(e), true);
                waitForRefreshOrStop();
                continue;
            }
            Bitmap qr = QRCode.getBitmap(session.content, 220, 2);
            App.post(() -> {
                if (stopped || qrView == null) return;
                qrView.setImageBitmap(qr);
                setStatus("等待扫码", false);
                if (btnRefresh != null) btnRefresh.setEnabled(true);
            });
            // 轮询
            boolean done = false;
            while (!stopped && !refreshRequested && !session.expired()) {
                sleep(2000);
                if (stopped || refreshRequested) break;
                int st;
                try {
                    st = provider.poll(session);
                } catch (Exception ignored) {
                    continue;
                }
                if (st == Qr115Provider.ST_CONFIRMED) {
                    done = confirm(session);
                    break;
                } else if (st == Qr115Provider.ST_EXPIRED) {
                    postStatus("二维码已过期，正在刷新…", false);
                    break;
                } else if (st == Qr115Provider.ST_CANCELLED) {
                    postStatus("已取消", false);
                    App.post(() -> {
                        fireCancel();
                        dismissAllowingStateLoss();
                    });
                    return;
                } else {
                    postStatus(Qr115Provider.statusText(st), false);
                }
            }
            if (done) return;
            if (stopped) return;
            if (!refreshRequested && !session.expired()) {
                // 既不是刷新也不是过期，短暂等待后继续外层循环
                sleep(500);
            }
        }
    }

    private boolean confirm(Qr115Provider.QrSession session) {
        postStatus("正在登录…", false);
        String cookie;
        try {
            cookie = provider.confirm(session);
        } catch (Exception e) {
            postStatus("登录失败：" + safeMsg(e) + "，正在刷新二维码…", false);
            sleep(1500);
            return false;
        }
        if (TextUtils.isEmpty(cookie)) {
            postStatus("没拿到登录凭据，正在刷新…", false);
            sleep(1500);
            return false;
        }
        postStatus("登录成功", true);
        Notify.show("115 登录成功");
        App.post(() -> fireSuccess(cookie));
        sleep(800);
        App.post(this::dismissAllowingStateLoss);
        return true;
    }

    private void postStatus(String text, boolean ok) {
        App.post(() -> setStatus(text, ok));
    }

    private void setStatus(String text, boolean ok) {
        if (stopped || tvStatus == null) return;
        tvStatus.setText(text);
        tvStatus.setTextColor(Color.parseColor(ok ? "#7ED492" : "#B3FFFFFF"));
    }

    private void waitForRefreshOrStop() {
        while (!stopped && !refreshRequested) sleep(500);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private String safeMsg(Exception e) {
        String m = e == null ? "" : e.getMessage();
        if (TextUtils.isEmpty(m)) return "未知错误";
        return m.length() > 60 ? m.substring(0, 60) : m;
    }

    @Override
    public void onDestroyView() {
        stopWorker();
        if (!provider.isLoggedIn()) fireCancel();
        super.onDestroyView();
    }
}
