package com.fongmi.android.tv.ui.activity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
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
        binding.cardVod.setOnClickListener(v -> ConfigDialog.create().vod().edit().show(getSupportFragmentManager(), null));
        binding.cardLive.setOnClickListener(v -> ConfigDialog.create().live().edit().show(getSupportFragmentManager(), null));
    }
    @Override
    protected void onResume() {
        super.onResume();
        updateCards();
    }
    private void updateCards() {
        updateVodCard();
        updateLiveCard();
    }
    private void updateVodCard() {
        try {
            Config config = VodConfig.get().getConfig();
            String url = config == null ? "" : config.getUrl();
            String name = config == null ? "" : config.getName();
            if (url == null || url.isEmpty()) {
                binding.tvVodEmpty.setVisibility(View.VISIBLE);
                binding.tvVodName.setVisibility(View.GONE);
                binding.tvVodUrl.setVisibility(View.GONE);
            } else {
                binding.tvVodEmpty.setVisibility(View.GONE);
                if (name != null && !name.isEmpty()) {
                    binding.tvVodName.setText(name);
                    binding.tvVodName.setVisibility(View.VISIBLE);
                } else {
                    binding.tvVodName.setVisibility(View.GONE);
                }
                binding.tvVodUrl.setText(url);
                binding.tvVodUrl.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            binding.tvVodEmpty.setVisibility(View.VISIBLE);
            binding.tvVodName.setVisibility(View.GONE);
            binding.tvVodUrl.setVisibility(View.GONE);
        }
    }
    private void updateLiveCard() {
        try {
            Config config = LiveConfig.get().getConfig();
            String url = config == null ? "" : config.getUrl();
            String name = config == null ? "" : config.getName();
            if (url == null || url.isEmpty()) {
                binding.tvLiveEmpty.setVisibility(View.VISIBLE);
                binding.tvLiveName.setVisibility(View.GONE);
                binding.tvLiveUrl.setVisibility(View.GONE);
            } else {
                binding.tvLiveEmpty.setVisibility(View.GONE);
                if (name != null && !name.isEmpty()) {
                    binding.tvLiveName.setText(name);
                    binding.tvLiveName.setVisibility(View.VISIBLE);
                } else {
                    binding.tvLiveName.setVisibility(View.GONE);
                }
                binding.tvLiveUrl.setText(url);
                binding.tvLiveUrl.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            binding.tvLiveEmpty.setVisibility(View.VISIBLE);
            binding.tvLiveName.setVisibility(View.GONE);
            binding.tvLiveUrl.setVisibility(View.GONE);
        }
    }
}
