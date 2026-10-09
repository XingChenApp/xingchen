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
        updateLongPressUI();
        binding.lpOff.setOnClickListener(v -> { setLongPress(0f); updateLongPressUI(); });
        binding.lp2x.setOnClickListener(v -> { setLongPress(2f); updateLongPressUI(); });
        binding.lp4x.setOnClickListener(v -> { setLongPress(4f); updateLongPressUI(); });
        updateBgPipUI();
        binding.bpOff.setOnClickListener(v -> { setBgPip("off"); updateBgPipUI(); });
        binding.bpBg.setOnClickListener(v -> { setBgPip("bg"); updateBgPipUI(); });
        binding.bpPip.setOnClickListener(v -> { setBgPip("pip"); updateBgPipUI(); });
        binding.cardDanmu.setOnClickListener(v -> DanmakuSettingsActivity.start(this));
        binding.cardSubtitle.setOnClickListener(v -> SubtitleSettingsActivity.start(this));
        binding.cardSkipSettings.setOnClickListener(v -> SkipSettingsActivity.start(this));
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

    private void updateLongPressUI() {
        float s = getSharedPreferences("xingchen", MODE_PRIVATE).getFloat("longpress_speed", 0f);
        binding.lpOff.setBackgroundResource(s == 0f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.lp2x.setBackgroundResource(s == 2f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.lp4x.setBackgroundResource(s == 4f ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void setLongPress(float s) {
        getSharedPreferences("xingchen", MODE_PRIVATE).edit().putFloat("longpress_speed", s).apply();
    }
    private void updateBgPipUI() {
        String m = getSharedPreferences("xingchen", MODE_PRIVATE).getString("bg_pip_mode", "off");
        binding.bpOff.setBackgroundResource("off".equals(m) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.bpBg.setBackgroundResource("bg".equals(m) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.bpPip.setBackgroundResource("pip".equals(m) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void setBgPip(String m) {
        getSharedPreferences("xingchen", MODE_PRIVATE).edit().putString("bg_pip_mode", m).apply();
    }
}