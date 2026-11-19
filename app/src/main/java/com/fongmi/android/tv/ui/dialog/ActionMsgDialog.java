package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.Html;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogActionMsgBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

/**
 * 动作协议 msgbox 的呈现面：指令形 {'action': {type:'msgbox', title, htmlMsg}}
 * 与卡片形（vod_id 即 msgbox 配置）共用。正文两形：htmlMsg 按 HTML 渲染
 * （自家形），msg 按纯文本渲染（OK影视/ys 形）；带 imageUrl 时在正文上方
 * 画图（imageHeight 为图高，单位 dp），扫码备选路靠它把码画出来。
 * 内层 py 的文字结果靠它才真的弹出来（协议 E10）。样式与点播域玻璃弹窗同一家
 * （shape_dialog_glass_white_panel 浅面毛玻璃＋深字），窗口口径照 AboutDialog。
 * 带 onConfirm 时「确定」先回派再关窗：流程中间步的 msgbox（扫码待确认等）
 * 靠它在关窗时通知蜘蛛续办；不带时确定只关窗，行为与旧版逐字相同。
 */
public final class ActionMsgDialog {

    private ActionMsgDialog() {
    }

    public static boolean show(Activity activity, String title, String body, boolean bodyIsHtml, String imageUrl, int imageHeightDp) {
        return show(activity, title, body, bodyIsHtml, imageUrl, imageHeightDp, null);
    }

    public static boolean show(Activity activity, String title, String body, boolean bodyIsHtml, String imageUrl, int imageHeightDp, Runnable onConfirm) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        DialogActionMsgBinding binding = DialogActionMsgBinding.inflate(LayoutInflater.from(activity));
        if (title == null || title.isEmpty()) binding.actionMsgTitle.setVisibility(View.GONE);
        else binding.actionMsgTitle.setText(title);
        if (imageUrl != null && !imageUrl.isEmpty()) {
            binding.actionMsgImage.setVisibility(View.VISIBLE);
            if (imageHeightDp > 0) {
                ViewGroup.LayoutParams params = binding.actionMsgImage.getLayoutParams();
                params.height = ResUtil.dp2px(imageHeightDp);
                binding.actionMsgImage.setLayoutParams(params);
            }
            ImgUtil.load("", imageUrl, binding.actionMsgImage, false);
        }
        binding.actionMsgText.setText(bodyIsHtml ? fromHtml(body == null ? "" : body) : body == null ? "" : body);
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(binding.getRoot());
        binding.actionMsgConfirm.setOnClickListener(v -> {
            if (onConfirm != null) onConfirm.run();
            dialog.dismiss();
        });
        // 摆位在 show 之前落定（原 show 后才 configureWindow：首帧默认宽、随后跳到终宽）
        configureWindow(activity, dialog);
        dialog.show();
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
