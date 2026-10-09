package com.fongmi.android.tv.ui.activity;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.databinding.ActivityDanmakuSettingsBinding;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.ui.base.BaseActivity;
public class DanmakuSettingsActivity extends BaseActivity {
    private ActivityDanmakuSettingsBinding binding;
    private SharedPreferences prefs;
    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, DanmakuSettingsActivity.class));
    }
    @Override
    protected ViewBinding getBinding() {
        binding = ActivityDanmakuSettingsBinding.inflate(getLayoutInflater());
        return binding;
    }
    private SharedPreferences prefs() {
        if (prefs == null) prefs = getSharedPreferences("xingchen", MODE_PRIVATE);
        return prefs;
    }
    @Override
    protected void initView(Bundle savedInstanceState) {
        boolean load = prefs().getBoolean("danmu_load", true);
        DanmakuSetting.putLoad(load);
        binding.switchDanmuLoad.setChecked(load);
        binding.switchDanmuLoad.setOnCheckedChangeListener((b, c) -> { prefs().edit().putBoolean("danmu_load", c).apply(); DanmakuSetting.putLoad(c); });
        binding.cardDanmuApi.setOnClickListener(v -> {});
        boolean auto = prefs().getBoolean("danmu_autosearch", true);
        DanmakuSetting.putAuto(auto);
        binding.switchDanmuAutosearch.setChecked(auto);
        binding.switchDanmuAutosearch.setOnCheckedChangeListener((b, c) -> { prefs().edit().putBoolean("danmu_autosearch", c).apply(); DanmakuSetting.putAuto(c); });
        boolean spiderFirst = prefs().getBoolean("danmu_spider_first", false);
        DanmakuSetting.putSpiderFirst(spiderFirst);
        binding.switchDanmuSpiderFirst.setChecked(spiderFirst);
        binding.switchDanmuSpiderFirst.setOnCheckedChangeListener((b, c) -> { prefs().edit().putBoolean("danmu_spider_first", c).apply(); DanmakuSetting.putSpiderFirst(c); });
        boolean enable = prefs().getBoolean("danmu_enable", true);
        DanmakuSetting.putShow(enable);
        PlayerButtonSetting.putVisible(PlayerButtonSetting.DANMAKU, enable);
        binding.switchDanmuEnable.setChecked(enable);
        binding.switchDanmuEnable.setOnCheckedChangeListener((b, c) -> {
            prefs().edit().putBoolean("danmu_enable", c).apply();
            DanmakuSetting.putShow(c);
            PlayerButtonSetting.putVisible(PlayerButtonSetting.DANMAKU, c);
            updateSubCardsVisibility(c);
        });
        updateSubCardsVisibility(enable);
        int alpha = prefs().getInt("danmu_alpha", 70);
        binding.seekDanmuAlpha.setProgress(alpha);
        binding.tvDanmuAlpha.setText(alpha + "%");
        binding.seekDanmuAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                binding.tvDanmuAlpha.setText(p + "%");
                if (fromUser) prefs().edit().putInt("danmu_alpha", p).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        updateSpeedUI();
        binding.speedSlow.setOnClickListener(v -> { setSpeed("slow"); updateSpeedUI(); });
        binding.speedNormal.setOnClickListener(v -> { setSpeed("normal"); updateSpeedUI(); });
        binding.speedFast.setOnClickListener(v -> { setSpeed("fast"); updateSpeedUI(); });
        updateSizeUI();
        binding.sizeSmall.setOnClickListener(v -> { setSize("small"); updateSizeUI(); });
        binding.sizeMedium.setOnClickListener(v -> { setSize("medium"); updateSizeUI(); });
        binding.sizeLarge.setOnClickListener(v -> { setSize("large"); updateSizeUI(); });
        updateAreaUI();
        binding.areaTop.setOnClickListener(v -> { setArea("top"); updateAreaUI(); });
        binding.areaFull.setOnClickListener(v -> { setArea("full"); updateAreaUI(); });
        binding.areaBottom.setOnClickListener(v -> { setArea("bottom"); updateAreaUI(); });
    }
    private void updateSubCardsVisibility(boolean enable) {
        int v = enable ? View.VISIBLE : View.GONE;
        binding.cardDanmuAlpha.setVisibility(v);
        binding.cardDanmuSpeed.setVisibility(v);
        binding.cardDanmuSize.setVisibility(v);
        binding.cardDanmuArea.setVisibility(v);
    }
    private void updateSpeedUI() {
        String s = prefs().getString("danmu_speed", "normal");
        binding.speedSlow.setBackgroundResource("slow".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.speedNormal.setBackgroundResource("normal".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.speedFast.setBackgroundResource("fast".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void setSpeed(String s) { prefs().edit().putString("danmu_speed", s).apply(); }
    private void updateSizeUI() {
        String s = prefs().getString("danmu_size", "medium");
        binding.sizeSmall.setBackgroundResource("small".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.sizeMedium.setBackgroundResource("medium".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.sizeLarge.setBackgroundResource("large".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void setSize(String s) { prefs().edit().putString("danmu_size", s).apply(); }
    private void updateAreaUI() {
        String s = prefs().getString("danmu_area", "top");
        binding.areaTop.setBackgroundResource("top".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.areaFull.setBackgroundResource("full".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.areaBottom.setBackgroundResource("bottom".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void setArea(String s) { prefs().edit().putString("danmu_area", s).apply(); }
}
