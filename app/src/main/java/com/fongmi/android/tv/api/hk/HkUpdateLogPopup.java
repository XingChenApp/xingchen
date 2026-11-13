package com.fongmi.android.tv.api.hk;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 更新日志底部弹窗：星辰原生重实现，对照 8.83 UpdateRecordsBottom。
 * 8.83：XPopup BottomPopupView，从底部滑出，标题"更新日志"，列表展示各版本 records，
 * 右下"知道啦"按钮关闭。规则的 hikerPop.updateRecordsBottom 依赖 8.83 内部类，
 * 在星辰包名下跑不起来，由 __hkShowUpdateLog 桥接到此处。
 */
public class HkUpdateLogPopup {

    /** 显示更新日志弹窗。jsonData：[{title:"2026/09/02", records:["..."]}, ...] */
    public static void show(String jsonData) {
        Activity activity = App.activity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        List<VersionLog> logs = parse(jsonData);
        if (logs.isEmpty()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_hk_update_log, null);
        dialog.setContentView(view);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
            // 底部滑入动画
            window.setWindowAnimations(android.R.style.Animation_InputMethod);
        }

        LinearLayout content = view.findViewById(R.id.log_content);
        for (VersionLog log : logs) {
            // 版本标题（如 2026/09/02）
            TextView titleView = new TextView(activity);
            titleView.setText(log.title);
            titleView.setTextSize(16);
            titleView.setTextColor(0xFFF2F4F8);
            titleView.getPaint().setFakeBoldText(true);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            titleLp.topMargin = dp(12);
            titleLp.bottomMargin = dp(4);
            content.addView(titleView, titleLp);
            // 每条记录
            for (String record : log.records) {
                TextView recordView = new TextView(activity);
                recordView.setText("• " + record);
                recordView.setTextSize(14);
                recordView.setTextColor(0xFFA8B0C0);
                recordView.setLineSpacing(dp(2), 1.0f);
                LinearLayout.LayoutParams recordLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                recordLp.bottomMargin = dp(4);
                recordLp.leftMargin = dp(8);
                content.addView(recordView, recordLp);
            }
        }

        view.findViewById(R.id.btn_ok).setOnClickListener(v -> dialog.dismiss());
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private static int dp(int dp) {
        float density = App.get().getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private static List<VersionLog> parse(String jsonData) {
        List<VersionLog> logs = new ArrayList<>();
        if (TextUtils.isEmpty(jsonData)) return logs;
        try {
            JSONArray arr = new JSONArray(jsonData);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                VersionLog log = new VersionLog();
                log.title = obj.optString("title", "");
                JSONArray records = obj.optJSONArray("records");
                if (records != null) {
                    for (int j = 0; j < records.length(); j++) {
                        String r = records.optString(j, "");
                        if (!TextUtils.isEmpty(r)) log.records.add(r);
                    }
                }
                // 兼容 records 为字符串数组之外的格式
                if (log.records.isEmpty()) {
                    String rs = obj.optString("records", "");
                    if (!TextUtils.isEmpty(rs)) log.records.add(rs);
                }
                if (!TextUtils.isEmpty(log.title) || !log.records.isEmpty()) logs.add(log);
            }
        } catch (Throwable ignored) {
        }
        return logs;
    }

    private static class VersionLog {
        String title = "";
        List<String> records = new ArrayList<>();
    }
}
