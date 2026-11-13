package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.R;

/**
 * 海阔弹窗统一入口：深色毛玻璃主题 + 统一淡入淡出。
 * 用法：把 new AlertDialog.Builder(...) 换成 new HkDialogs(...)，其余链式调用一字不动。
 * 只换弹窗的皮（主题背景/圆角/按钮与列表字色/淡入动画）；文案、按钮个数与顺序、点击回调逻辑一律不动。
 * 选项类弹窗走 cards(...)：白底独立圆角卡片 + 间距（深色底上突出），条目/顺序/点击语义与 setItems 对等。
 */
public class HkDialogs extends AlertDialog.Builder {

    public HkDialogs(@NonNull Context context) {
        super(context, R.style.ThemeOverlay_XingChen_GlassDialog_Dark);
    }

    /** 宿主 Activity 已结束/已销毁时返回 true：此时再 show 弹窗会抛 BadToken/状态异常，调用方应直接放弃。 */
    public static boolean isHostGone(Context context) {
        Context ctx = context;
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) {
                Activity activity = (Activity) ctx;
                return activity.isFinishing() || activity.isDestroyed();
            }
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return false;
    }

    @NonNull
    @Override
    public AlertDialog create() {
        AlertDialog dialog = super.create();
        Window window = dialog.getWindow();
        if (window != null) window.setWindowAnimations(R.style.HkDialogFade);
        return dialog;
    }

    /**
     * 内容自带整卡背景（浅色卡片自成一面）时必调：清掉窗口的暗色玻璃底，只剩卡片本身。
     * 否则主题的 windowBackground（dialog_glass_dark）比卡片大一圈，从卡片边缘（尤其左上与圆角外）
     * 探出黑层——「选择小程序」弹窗漏黑的根因（用户 2026-10-23 截图亲指：上面一层比下面一层小）。
     * 在 create() 后、show() 前调用。
     */
    public static void clearWindowBackground(@Nullable AlertDialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
        }
    }

    /**
     * 选项卡片弹窗（对等 setItems）：每项一张白底独立圆角卡片，项与项之间带间距；
     * 点选后自动关闭再调 onPick，语义与 setItems 一致。
     *
     * @param title        可空标题
     * @param items        选项文案，顺序不动
     * @param selectedIndex 选中项下标（单选语义，选中项金字）；&lt; 0 表示无选中态
     * @param negativeText 可空底部按钮文案（如"取消"/"关闭"），null 则不放
     * @param onPick       点选回调（which 为选项下标）
     */
    public static AlertDialog cards(@NonNull Context context, @Nullable String title, String[] items,
                                   int selectedIndex, @Nullable String negativeText,
                                   DialogInterface.OnClickListener onPick) {
        float density = context.getResources().getDisplayMetrics().density;
        int dp10 = (int) (10 * density + 0.5f);
        int dp14 = (int) (14 * density + 0.5f);
        int dp16 = (int) (16 * density + 0.5f);
        int dp20 = (int) (20 * density + 0.5f);

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HkDialogs b = new HkDialogs(context);
        if (title != null) b.setTitle(title);
        if (negativeText != null) b.setNegativeButton(negativeText, (d, w) -> d.dismiss());
        final AlertDialog[] holder = new AlertDialog[1];
        AlertDialog dialog = b.setView(scroll).create();
        holder[0] = dialog;
        for (int i = 0; i < items.length; i++) {
            TextView tv = new TextView(context);
            tv.setText(items[i]);
            tv.setTextColor(i == selectedIndex ? 0xFFB8890F : 0xFF101216);
            tv.setTextSize(16);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.dialog_option_card_light);
            tv.setPadding(dp16, dp14, dp16, dp14);
            final int which = i;
            tv.setOnClickListener(v -> {
                holder[0].dismiss();
                if (onPick != null) onPick.onClick(holder[0], which);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = dp10;
            list.addView(tv, lp);
        }
        // 上下留白与现有手写选项弹窗一致
        scroll.setPadding(dp20, dp16, dp20, dp16);
        if (!isHostGone(context)) dialog.show();
        return dialog;
    }

    /** 无选中态的选项卡片弹窗。 */
    public static AlertDialog cards(@NonNull Context context, @Nullable String title, String[] items,
                                   @Nullable String negativeText, DialogInterface.OnClickListener onPick) {
        return cards(context, title, items, -1, negativeText, onPick);
    }
}
