package com.fongmi.android.tv.ui.activity;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.databinding.ActivityPlayerSettingsBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.Notify;
public class PlayerSettingsActivity extends BaseActivity {
    private ActivityPlayerSettingsBinding binding;
    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, PlayerSettingsActivity.class));
    }
    @Override
    protected ViewBinding getBinding() {
        binding = ActivityPlayerSettingsBinding.inflate(getLayoutInflater());
        return binding;
    }
    @Override
    protected void initView(Bundle savedInstanceState) {
        updateKernelUI();
        updateDecodeUI();
        binding.kernelExo.setOnClickListener(v -> { setKernel("exo"); updateKernelUI(); });
        binding.kernelMpv.setOnClickListener(v -> { setKernel("mpv"); updateKernelUI(); });
        binding.kernelIjk.setOnClickListener(v -> { setKernel("ijk"); updateKernelUI(); });
        binding.decodeHard.setOnClickListener(v -> { setDecode("hard"); updateDecodeUI(); });
        binding.decodeSoft.setOnClickListener(v -> { setDecode("soft"); updateDecodeUI(); });
        binding.switchAutonext.setChecked(getSharedPreferences("xingchen", MODE_PRIVATE).getBoolean("auto_next", true));
        binding.switchAutonext.setOnCheckedChangeListener((b, c) -> { getSharedPreferences("xingchen", MODE_PRIVATE).edit().putBoolean("auto_next", c).apply(); PlayerSetting.putAutoPlay(c); });
        binding.switchSkip.setChecked(getSharedPreferences("xingchen", MODE_PRIVATE).getBoolean("skip_intro", false));
        binding.switchSkip.setOnCheckedChangeListener((b, c) -> getSharedPreferences("xingchen", MODE_PRIVATE).edit().putBoolean("skip_intro", c).apply());
        updateSpeedUI();
        binding.speed075.setOnClickListener(v -> { setSpeed(0.75f); updateSpeedUI(); });
        binding.speed100.setOnClickListener(v -> { setSpeed(1.0f); updateSpeedUI(); });
        binding.speed125.setOnClickListener(v -> { setSpeed(1.25f); updateSpeedUI(); });
        binding.speed150.setOnClickListener(v -> { setSpeed(1.5f); updateSpeedUI(); });
        binding.speed200.setOnClickListener(v -> { setSpeed(2.0f); updateSpeedUI(); });
        binding.cardDanmu.setOnClickListener(v -> {});
        binding.cardSubtitle.setOnClickListener(v -> {});
    }
    private void updateSpeedUI() {
        float s = getSharedPreferences("xingchen", MODE_PRIVATE).getFloat("player_speed", 1.0f);
        binding.speed075.setBackgroundResource(s == 0.75f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.speed100.setBackgroundResource(s == 1.0f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.speed125.setBackgroundResource(s == 1.25f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.speed150.setBackgroundResource(s == 1.5f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.speed200.setBackgroundResource(s == 2.0f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void setSpeed(float s) {
        getSharedPreferences("xingchen", MODE_PRIVATE).edit().putFloat("player_speed", s).apply();
        PlayerSetting.putDefaultSpeed(s);
    }
    private void updateKernelUI() {
        String k = getSharedPreferences("xingchen", MODE_PRIVATE).getString("player_kernel", "exo");
        binding.kernelExo.setBackgroundResource("exo".equals(k) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.kernelMpv.setBackgroundResource("mpv".equals(k) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.kernelIjk.setBackgroundResource("ijk".equals(k) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void updateDecodeUI() {
        String d = getSharedPreferences("xingchen", MODE_PRIVATE).getString("player_decode", "hard");
        binding.decodeHard.setBackgroundResource("hard".equals(d) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.decodeSoft.setBackgroundResource("soft".equals(d) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void setKernel(String k) {
        getSharedPreferences("xingchen", MODE_PRIVATE).edit().putString("player_kernel", k).apply();
        int p = "mpv".equals(k) ? PlayerSetting.MPV : "ijk".equals(k) ? PlayerSetting.IJK : PlayerSetting.EXO;
        PlayerSetting.putPlayer(p);
    }
    private void setDecode(String d) {
        getSharedPreferences("xingchen", MODE_PRIVATE).edit().putString("player_decode", d).apply();
        PlayerSetting.putVideoPrefer("hard".equals(d));
    }
}
