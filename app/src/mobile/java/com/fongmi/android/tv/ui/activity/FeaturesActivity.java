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

import com.fongmi.android.tv.databinding.ActivityFeaturesBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;

public class FeaturesActivity extends BaseActivity {

    private static final String PREFS = "xingchen";
    private static final String KEY_READING = "xingchen.reading_enabled";
    private static final String KEY_READING_SOURCE = "xingchen.reading_source";

    private ActivityFeaturesBinding binding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, FeaturesActivity.class));
    }

    public static boolean isReadingEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_READING, false);
    }

    public static String getReadingSource(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_READING_SOURCE, "");
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityFeaturesBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        refreshReading();
        refreshAdBlock();
        refreshTmdb();
        binding.swReading.setOnCheckedChangeListener((btn, checked) -> {
            getPrefs().edit().putBoolean(KEY_READING, checked).apply();
            refreshReading();
            Notify.show(checked ? "阅读已启用" : "阅读已关闭");
        });
        binding.cardReading.setOnClickListener(v -> showReadingSourceInput());
        binding.cardAdblock.setOnClickListener(v -> AdBlockActivity.start(this));
        binding.cardTmdb.setOnClickListener(v -> TmdbActivity.start(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshReading();
        refreshAdBlock();
        refreshTmdb();
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void refreshReading() {
        boolean enabled = getPrefs().getBoolean(KEY_READING, false);
        binding.swReading.setChecked(enabled);
        String source = getPrefs().getString(KEY_READING_SOURCE, "");
        if (enabled) {
            binding.tvReadingSub.setText(source == null || source.isEmpty() ? "已启用，点击添加阅读源" : source);
        } else {
            binding.tvReadingSub.setText("未启用");
        }
    }

    private void refreshAdBlock() {
        boolean enabled = AdBlockActivity.isEnabled(this);
        binding.tvAdblockSub.setText(enabled ? "已启用" : "未启用");
    }

    private void refreshTmdb() {
        String key = TmdbActivity.getApiKey(this);
        binding.tvTmdbSub.setText(key == null || key.isEmpty() ? "未配置" : "已配置");
    }

    private void showReadingSourceInput() {
        EditText input = new EditText(this);
        input.setText(getPrefs().getString(KEY_READING_SOURCE, ""));
        input.setSingleLine(true);
        input.setHint("阅读源地址");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("阅读源")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    getPrefs().edit().putString(KEY_READING_SOURCE, input.getText().toString().trim()).apply();
                    refreshReading();
                    Notify.show("已保存");
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
