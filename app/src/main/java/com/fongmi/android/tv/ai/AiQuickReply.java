package com.fongmi.android.tv.ai;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import java.lang.ref.WeakReference;

/**
 * 轻量 AI 快捷回复浮层：按住说话松开后展示 AI 回答。
 * 比 AiChatDialog 轻得多：只是一张底部卡片，无历史、无输入框。
 * 点击空白处或超时自动消失。
 */
public class AiQuickReply {

    private static WeakReference<View> overlayRef;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable autoDismiss;
    private static final long AUTO_DISMISS_MS = 15000;

    /** 显示"思考中"状态 */
    public static void showThinking(Activity activity, String question) {
        dismiss();
        if (activity == null || activity.isFinishing()) return;
        try {
            FrameLayout root = new FrameLayout(activity);
            root.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // 点击空白处关闭
            root.setOnClickListener(v -> dismiss());

            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xF01A1D24);
            bg.setCornerRadius(dp(activity, 16));
            bg.setStroke(dp(activity, 1), 0x4DFFFFFF);
            card.setBackground(bg);
            int pad = dp(activity, 16);
            card.setPadding(pad, pad, pad, pad);
            // 卡片本身点击不关闭
            card.setOnClickListener(v -> {});

            TextView qView = new TextView(activity);
            qView.setText("🎤 " + question);
            qView.setTextColor(0xFF9CA3AF);
            qView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            qView.setMaxLines(2);
            qView.setEllipsize(android.text.TextUtils.TruncateAt.END);

            TextView aView = new TextView(activity);
            aView.setTag("answer");
            aView.setText("思考中…");
            aView.setTextColor(Color.WHITE);
            aView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            aView.setLineSpacing(dp(activity, 4), 1.0f);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.topMargin = dp(activity, 8);
            aView.setLayoutParams(alp);

            ScrollView scroll = new ScrollView(activity);
            scroll.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 200)));
            scroll.addView(aView);

            TextView moreBtn = new TextView(activity);
            moreBtn.setText("进入完整对话 →");
            moreBtn.setTextColor(0xFFFBBF24);
            moreBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            moreBtn.setGravity(Gravity.END);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = dp(activity, 8);
            moreBtn.setLayoutParams(blp);
            moreBtn.setOnClickListener(v -> {
                Activity act = activityRefGet();
                dismiss();
                if (act instanceof FragmentActivity && !act.isFinishing()) {
                    try {
                        com.fongmi.android.tv.ui.dialog.AiChatDialog.show(
                                ((FragmentActivity) act).getSupportFragmentManager());
                    } catch (Exception ignored) {
                    }
                }
            });

            card.addView(qView);
            card.addView(scroll);
            card.addView(moreBtn);

            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.gravity = Gravity.BOTTOM;
            int margin = dp(activity, 16);
            clp.leftMargin = margin;
            clp.rightMargin = margin;
            clp.bottomMargin = dp(activity, 100);
            card.setLayoutParams(clp);

            root.addView(card);
            ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            decor.addView(root);
            overlayRef = new WeakReference<>(root);
            // 记住 activity 用于"进入完整对话"
            activityHolder = new WeakReference<>(activity);

            scheduleAutoDismiss();
        } catch (Exception ignored) {
        }
    }

    private static WeakReference<Activity> activityHolder;

    private static Activity activityRefGet() {
        return activityHolder != null ? activityHolder.get() : null;
    }

    /** 更新回答内容 */
    public static void updateAnswer(String answer) {
        handler.post(() -> {
            try {
                View root = overlayRef != null ? overlayRef.get() : null;
                if (root == null) return;
                TextView aView = root.findViewWithTag("answer");
                if (aView != null) {
                    aView.setText(answer == null || answer.isEmpty() ? "（空回复）" : answer);
                }
                scheduleAutoDismiss();
            } catch (Exception ignored) {
            }
        });
    }

    /** 显示错误 */
    public static void showError(String msg) {
        updateAnswer("出错了：" + msg);
    }

    /** 关闭浮层 */
    public static void dismiss() {
        handler.removeCallbacks(autoDismiss);
        try {
            View root = overlayRef != null ? overlayRef.get() : null;
            if (root != null) {
                ViewGroup parent = (ViewGroup) root.getParent();
                if (parent != null) parent.removeView(root);
            }
        } catch (Exception ignored) {
        }
        overlayRef = null;
        activityHolder = null;
    }

    private static void scheduleAutoDismiss() {
        handler.removeCallbacks(autoDismiss);
        autoDismiss = AiQuickReply::dismiss;
        handler.postDelayed(autoDismiss, AUTO_DISMISS_MS);
    }

    private static int dp(Activity activity, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                activity.getResources().getDisplayMetrics());
    }
}
