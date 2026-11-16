package com.fongmi.android.tv.ui.hk;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.hk.HkFavoriteStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 海阔收藏弹窗：对齐 8.83 FilmListActivity 的收藏交互
 * （BookmarkEditPopup：新增收藏/更新收藏——标题可改、分组可选；
 * 已收藏时：取消收藏 / 更新收藏）。
 * 皮走 HkDialogs 玻璃主题，选项独立卡片框。
 */
public class HkFavoriteDialogs {

    public interface OnConfirm {
        void onConfirm(String title, String group);
    }

    public interface OnSelect {
        void onSelect(int which);
    }

    /**
     * 清空确认弹窗（2026-11-15 他亲定形态）：屏幕居中小弹窗，文案
     * 「清空全部」，下方左「取消」右「确认」。历史页与收藏页共用。
     * 装法照海阔域居中弹窗既有口径（HkDetailBridge 选项弹窗同款）：
     * 普通 Dialog＋卡片自带深玻璃底（dialog_glass_dark）、窗口透明、
     * 宽＝屏宽 90%、高自适应、居中。143 首落用 AlertDialog 承载，
     * 窗口被主题撑成整屏（真机截图：全宽顶条形），本批改此装法。
     */
    public static void showClearAll(Context ctx, Runnable onConfirm) {
        if (HkDialogs.isHostGone(ctx)) return;
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.dialog_glass_dark);
        root.setPadding(dp(ctx, 20), dp(ctx, 18), dp(ctx, 20), dp(ctx, 14));

        TextView tv = new TextView(ctx);
        tv.setText("清空全部");
        tv.setTextColor(0xFFF2F4F8);
        tv.setTextSize(16);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        root.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = dp(ctx, 16);

        TextView cancel = new TextView(ctx);
        cancel.setText("取消");
        cancel.setTextColor(0xFFA8B0C0);
        cancel.setTextSize(15);
        cancel.setPadding(dp(ctx, 8), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10));
        btnRow.addView(cancel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        android.view.View spacer = new android.view.View(ctx);
        btnRow.addView(spacer, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView ok = new TextView(ctx);
        ok.setText("确认");
        ok.setTextColor(0xFFD9A420);
        ok.setTypeface(null, Typeface.BOLD);
        ok.setTextSize(15);
        ok.setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 8), dp(ctx, 10));
        btnRow.addView(ok, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(btnRow, brLp);

        android.app.Dialog dialog = new android.app.Dialog(ctx);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
            window.setWindowAnimations(R.style.HkDialogFade);
            window.setGravity(Gravity.CENTER);
            int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = ctx.getResources().getDisplayMetrics().heightPixels;
            int width = (int) (screenWidth * 0.9f);
            // 定高照同域成例 HkCenterListPopup.show()：先按目标宽预量
            // 内容自然高、再显式像素设高。本主题链无 dialogTheme，裸
            // Dialog 退回活动主题生成全帧窗，WRAP_CONTENT 高被顶满、
            // 玻璃底涂满整窗成整屏罩（账 hk-clear-dialog-route-20261116，
            // 144 重做代码与宽/居中都对、只高没收住）。
            root.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,
                            android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(0,
                            android.view.View.MeasureSpec.UNSPECIFIED));
            int height = Math.min(root.getMeasuredHeight(), (int) (screenHeight * 0.85f));
            window.setLayout(width, height);
        }
        cancel.setOnClickListener(v -> dialog.dismiss());
        ok.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirm != null) onConfirm.run();
        });
        dialog.show();
    }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    /** 选项弹窗：取消收藏 / 更新收藏（对齐原版 cancelCollectOrUpdate 的请选择操作）。 */
    public static void showOptions(Context ctx, String title, String[] labels, OnSelect cb) {
        if (HkDialogs.isHostGone(ctx)) return;
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16));
        TextView tv = new TextView(ctx);
        tv.setText(title);
        tv.setTextColor(0xFFF2F4F8);
        tv.setTextSize(16);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        root.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new HkDialogs(ctx).setView(root).create();
        for (int i = 0; i < labels.length; i++) {
            TextView item = new TextView(ctx);
            item.setText(labels[i]);
            item.setTextColor(0xFF1A1D24);
            item.setTextSize(16);
            item.setGravity(Gravity.CENTER);
            item.setBackgroundResource(R.drawable.dialog_option_card_light);
            item.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(ctx, 10);
            final int which = i;
            item.setOnClickListener(v -> {
                dialog.dismiss();
                if (cb != null) cb.onSelect(which);
            });
            root.addView(item, lp);
        }
        dialog.show();
    }

    /**
     * 新增/更新收藏：标题输入框 + 分组 chips（已有分组 + 默认分组）。
     * 对齐原版 BookmarkEditPopup（groupEditable，标题不能为空）。
     */
    public static void showEdit(Context ctx, String dialogTitle, String initTitle, String initGroup, OnConfirm cb) {
        if (HkDialogs.isHostGone(ctx)) return;
        List<String> groups = new ArrayList<>();
        groups.add(HkFavoriteStore.DEFAULT_GROUP);
        for (String g : HkFavoriteStore.groups()) {
            if (!groups.contains(g)) groups.add(g);
        }
        final String[] selected = {TextUtils.isEmpty(initGroup) ? HkFavoriteStore.DEFAULT_GROUP : initGroup};
        if (!groups.contains(selected[0])) groups.add(0, selected[0]);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16));

        TextView title = new TextView(ctx);
        title.setText(dialogTitle);
        title.setTextColor(0xFFF2F4F8);
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText et = new EditText(ctx);
        et.setText(initTitle == null ? "" : initTitle);
        et.setSelection(et.getText().length());
        et.setTextColor(0xFF1A1D24);
        et.setHintTextColor(0xFF8A8F98);
        et.setTextSize(15);
        et.setHint("请输入标题，不能为空");
        et.setBackgroundResource(R.drawable.dialog_option_card_light);
        et.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12));
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etLp.topMargin = dp(ctx, 12);
        root.addView(et, etLp);

        TextView groupLabel = new TextView(ctx);
        groupLabel.setText("分组");
        groupLabel.setTextColor(0xFFA8B0C0);
        groupLabel.setTextSize(13);
        LinearLayout.LayoutParams glLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        glLp.topMargin = dp(ctx, 12);
        root.addView(groupLabel, glLp);

        LinearLayout chips = new LinearLayout(ctx);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        final List<TextView> chipViews = new ArrayList<>();
        for (String g : groups) {
            TextView chip = new TextView(ctx);
            chip.setText(g);
            chip.setTextSize(14);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = dp(ctx, 8);
            cp.topMargin = dp(ctx, 8);
            final String gv = g;
            chip.setOnClickListener(v -> {
                selected[0] = gv;
                refreshChips(chipViews, groups, selected[0], ctx);
            });
            chipViews.add(chip);
            chips.addView(chip, cp);
        }
        refreshChips(chipViews, groups, selected[0], ctx);
        root.addView(chips, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new HkDialogs(ctx).setView(root).create();

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = dp(ctx, 16);

        TextView cancel = new TextView(ctx);
        cancel.setText("取消");
        cancel.setTextColor(0xFFA8B0C0);
        cancel.setTextSize(15);
        cancel.setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10));
        cancel.setOnClickListener(v -> dialog.dismiss());

        TextView ok = new TextView(ctx);
        ok.setText("确定");
        ok.setTextColor(0xFFD9A420);
        ok.setTypeface(null, Typeface.BOLD);
        ok.setTextSize(15);
        ok.setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10));
        ok.setOnClickListener(v -> {
            String t = et.getText().toString().trim();
            if (TextUtils.isEmpty(t)) {
                et.setError("标题不能为空");
                return;
            }
            dialog.dismiss();
            if (cb != null) cb.onConfirm(t, selected[0]);
        });
        btnRow.addView(cancel);
        btnRow.addView(ok);
        root.addView(btnRow, brLp);

        dialog.show();
    }

    private static void refreshChips(List<TextView> views, List<String> groups, String selected, Context ctx) {
        for (int i = 0; i < views.size(); i++) {
            TextView chip = views.get(i);
            boolean on = groups.get(i).equals(selected);
            chip.setTextColor(on ? 0xFF7A5A00 : 0xFF1A1D24);
            chip.setBackgroundResource(on ? R.drawable.hk_group_tag : R.drawable.dialog_option_card_light);
        }
    }
}
