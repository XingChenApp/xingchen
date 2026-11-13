package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.viewbinding.ViewBinding;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityCrashBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;

import cat.ereza.customactivityoncrash.CustomActivityOnCrash;
import cat.ereza.customactivityoncrash.config.CaocConfig;

public class CrashActivity extends BaseActivity {

    private ActivityCrashBinding mBinding;


    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCrashBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initEvent() {
        mBinding.details.setOnClickListener(v -> showError());
        mBinding.restart.setOnClickListener(v -> restart());
    }

    private void restart() {
        CaocConfig config = CustomActivityOnCrash.getConfigFromIntent(getIntent());
        if (config != null) {
            CustomActivityOnCrash.restartApplication(this, config);
            return;
        }
        // intent 没带崩溃配置时（理论窄路）不许 requireNonNull 把崩溃页
        // 自己再崩一次：退回启动器意图重启，拿不到就只关掉本页。
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch == null) launch = getPackageManager().getLeanbackLaunchIntentForPackage(getPackageName());
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(launch);
        }
        finishAndRemoveTask();
    }


    private void showError() {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.crash_details_title)
                .setMessage(CustomActivityOnCrash.getAllErrorDetailsFromIntent(this, getIntent()))
                .setPositiveButton(R.string.crash_details_close, null)
                .show();
    }
}
