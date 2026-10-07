package com.fongmi.android.tv.ui.activity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.databinding.ActivityConfigSourceBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
public class ConfigSourceActivity extends BaseActivity {
    private ActivityConfigSourceBinding binding;
    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, ConfigSourceActivity.class));
    }
    @Override
    protected ViewBinding getBinding() {
        binding = ActivityConfigSourceBinding.inflate(getLayoutInflater());
        return binding;
    }
    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.cardVod.setOnClickListener(v -> ConfigDialog.create().vod().show(getSupportFragmentManager(), null));
        binding.cardLive.setOnClickListener(v -> ConfigDialog.create().live().show(getSupportFragmentManager(), null));
    }
}
