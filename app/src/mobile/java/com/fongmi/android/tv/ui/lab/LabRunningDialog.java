package com.fongmi.android.tv.ui.lab;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * 运行中对话框：列出所有运行中的命令/服务/终端，一行一个「名字＋停止」。
 * 没有运行中的给一句人话。深底自绘，不走系统白底 AlertDialog。走系统返回。
 */
public final class LabRunningDialog {

    private LabRunningDialog() {
    }

    public static void show(Activity activity) {
        List<String[]> entries = LabRunner.runningEntries(activity);
        int dp16 = LabSkin.dp(activity, 16);
        int dp8 = LabSkin.dp(activity, 8);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp16, dp16, dp16, dp16);
        scroll.addView(box, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (entries.isEmpty()) {
            TextView t = new TextView(activity);
            t.setText("现在没有在跑的东西");
            t.setTextSize(15);
            t.setTextColor(LabSkin.TEXT_ON_DARK_DIM);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp16, 0, dp16);
            box.addView(t);
        }
        final Dialog[] holder = new Dialog[1];
        for (String[] e : entries) {
            final String key = e[0];
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp8, 0, dp8);
            TextView name = new TextView(activity);
            name.setText(e[1]);
            name.setTextSize(15);
            name.setTextColor(LabSkin.TEXT_ON_DARK);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(name);
            TextView stop = new TextView(activity);
            stop.setText("停止");
            stop.setTextSize(14);
            stop.setTextColor(LabSkin.TEXT_ON_DARK);
            stop.setBackground(LabSkin.dialogNeutral(activity));
            stop.setPadding(dp16, dp8, dp16, dp8);
            stop.setClickable(true);
            stop.setFocusable(true);
            stop.setOnClickListener(v -> {
                LabRunner.stopKey(activity, key);
                if (holder[0] != null) holder[0].dismiss();
                show(activity);
            });
            row.addView(stop);
            box.addView(row);
        }

        holder[0] = LabInstallDialog.showView(activity, "运行中", scroll, "知道了");
    }
}
