package com.fongmi.android.tv.api.hk;
import com.fongmi.android.tv.utils.ToastUtil;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.fongmi.android.tv.R;

/**
 * 扫码结果处理：对照 8.83 FilmListActivity.dealQRContent(1054)。
 * 8.83：onActivityResult 收到 "codedContent" → runOnUI → dealQRContent：
 * 内容以 http 开头 → WebUtil.goWeb 打开网页（8.83 内置浏览器）；
 * 否则弹 XPopup 确认框（标题"扫描结果"，按钮"取消"/"复制"，复制走 ClipboardUtil）。
 * 星辰无内置浏览器，http 走系统浏览器 ACTION_VIEW；弹窗用原生 Dialog 实现。
 */
public class HkQrUtil {

    /** 处理扫码得到的文本内容（8.83 dealQRContent 语义）。 */
    public static void dealQRContent(Activity activity, String content) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (TextUtils.isEmpty(content)) return;
        activity.runOnUiThread(() -> {
            try {
                if (content.startsWith("http")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(content));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                } else {
                    showResultDialog(activity, content);
                }
            } catch (Throwable t) {
                try {
                    ToastUtil.show(activity, "处理失败", Toast.LENGTH_SHORT);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** 8.83 的 XPopup asConfirm("扫描结果", 内容, "取消", "复制") 对齐。 */
    private static void showResultDialog(Activity activity, String content) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.dialog_glass_dark);
        int pad = dp(activity, 16);
        root.setPadding(pad, pad, pad, pad / 2);

        TextView titleView = new TextView(activity);
        titleView.setText("扫描结果");
        titleView.setTextSize(17);
        titleView.setTextColor(0xFFF2F4F8);
        titleView.getPaint().setFakeBoldText(true);
        titleView.setGravity(Gravity.CENTER);
        root.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(activity);
        TextView contentView = new TextView(activity);
        contentView.setText(content);
        contentView.setTextSize(15);
        contentView.setTextColor(0xFFA8B0C0);
        contentView.setTextIsSelectable(true);
        int cp = dp(activity, 4);
        contentView.setPadding(cp, dp(activity, 12), cp, dp(activity, 12));
        scrollView.addView(contentView);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 180));
        slp.topMargin = dp(activity, 8);
        root.addView(scrollView, slp);

        View divider = new View(activity);
        divider.setBackgroundColor(0x33FFFFFF);
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);

        TextView btnCancel = new TextView(activity);
        btnCancel.setText("取消");
        btnCancel.setTextSize(16);
        btnCancel.setTextColor(0xFFF2F4F8);
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setBackgroundResource(android.R.drawable.list_selector_background);
        int bp = dp(activity, 12);
        btnCancel.setPadding(bp * 2, bp, bp * 2, bp);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(btnCancel);

        TextView btnCopy = new TextView(activity);
        btnCopy.setText("复制");
        btnCopy.setTextSize(16);
        btnCopy.setTextColor(0xFFF2F4F8);
        btnCopy.setGravity(Gravity.CENTER);
        btnCopy.setBackgroundResource(android.R.drawable.list_selector_background);
        btnCopy.setPadding(bp * 2, bp, bp * 2, bp);
        btnCopy.setOnClickListener(v -> {
            try {
                ClipboardManager cm = (ClipboardManager)
                        activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("scan", content));
                ToastUtil.show(activity, "已复制", Toast.LENGTH_SHORT);
            } catch (Throwable ignored) {
            }
            dialog.dismiss();
        });
        buttons.addView(btnCopy);
        root.addView(buttons);

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private static int dp(Activity activity, int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
