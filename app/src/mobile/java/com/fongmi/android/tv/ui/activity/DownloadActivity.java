package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.ActivityDownloadBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;

import java.io.File;

public class DownloadActivity extends BaseActivity {

    private static final String PREFS = "xingchen";
    private static final String KEY_DIR = "xingchen.download_dir";

    private ActivityDownloadBinding binding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, DownloadActivity.class));
    }

    public static String getDownloadDir(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String dir = prefs.getString(KEY_DIR, "");
        if (dir == null || dir.isEmpty()) {
            dir = new File(context.getFilesDir(), "Download").getAbsolutePath();
        }
        return dir;
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityDownloadBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.tvCount.setText("共 0 个任务");
        binding.tvEmpty.setVisibility(View.VISIBLE);
        binding.rvDownloads.setVisibility(View.GONE);
        binding.cbSelectAll.setOnCheckedChangeListener((btn, checked) -> Notify.show(checked ? "全选" : "取消全选"));
        binding.btnDeleteSelected.setOnClickListener(v -> Notify.show("暂无可删除任务"));
        refreshDirSub();
        binding.cardDownloadDir.setOnClickListener(v -> showDirInput());
    }

    private void refreshDirSub() {
        binding.tvDownloadDirSub.setText(getDownloadDir(this));
    }

    private void showDirInput() {
        EditText input = new EditText(this);
        input.setText(getDownloadDir(this));
        input.setSingleLine(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("下载目录")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String dir = input.getText().toString().trim();
                    if (!dir.isEmpty()) {
                        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DIR, dir).apply();
                        refreshDirSub();
                        Notify.show("已保存");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
