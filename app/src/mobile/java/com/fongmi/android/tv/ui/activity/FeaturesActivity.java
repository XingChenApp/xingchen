package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.ActivityFeaturesBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;

public class FeaturesActivity extends BaseActivity {

    private static final String PREFS = "xingchen";
    private static final String KEY_MINIAPP = "xingchen.miniapp_enabled";

    private ActivityFeaturesBinding binding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, FeaturesActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityFeaturesBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        refreshAdBlock();
        refreshTmdb();
        refreshMiniApp();
        binding.swMiniapp.setOnCheckedChangeListener((btn, checked) -> {
            getPrefs().edit().putBoolean(KEY_MINIAPP, checked).apply();
            refreshMiniApp();
            Notify.show(checked ? "小程序已启用" : "小程序已关闭");
        });
        binding.cardAdblock.setOnClickListener(v -> AdBlockActivity.start(this));
        binding.cardTmdb.setOnClickListener(v -> TmdbActivity.start(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAdBlock();
        refreshTmdb();
        refreshMiniApp();
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void refreshAdBlock() {
        boolean enabled = AdBlockActivity.isEnabled(this);
        binding.tvAdblockSub.setText(enabled ? "已启用" : "未启用");
    }

    private void refreshTmdb() {
        String key = TmdbActivity.getApiKey(this);
        binding.tvTmdbSub.setText(key == null || key.isEmpty() ? "未配置" : "已配置");
    }

    private void refreshMiniApp() {
        boolean enabled = getPrefs().getBoolean(KEY_MINIAPP, false);
        binding.swMiniapp.setChecked(enabled);
        binding.tvMiniappSub.setText(enabled ? "已启用" : "未启用");
    }
}
