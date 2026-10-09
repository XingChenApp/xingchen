package com.fongmi.android.tv.ui.activity;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.databinding.ActivitySkipSettingsBinding;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.ui.base.BaseActivity;
public class SkipSettingsActivity extends BaseActivity {
    private ActivitySkipSettingsBinding binding;
    private SharedPreferences prefs;
    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SkipSettingsActivity.class));
    }
    @Override
    protected ViewBinding getBinding() {
        binding = ActivitySkipSettingsBinding.inflate(getLayoutInflater());
        return binding;
    }
    private SharedPreferences prefs() {
        if (prefs == null) prefs = getSharedPreferences("xingchen", MODE_PRIVATE);
        return prefs;
    }
    @Override
    protected void initView(Bundle savedInstanceState) {
        boolean opening = prefs().getBoolean("skip_opening_show", true);
        PlayerButtonSetting.putVisible(PlayerButtonSetting.OPENING, opening);
        binding.switchSkipOpening.setChecked(opening);
        binding.switchSkipOpening.setOnCheckedChangeListener((b, c) -> {
            prefs().edit().putBoolean("skip_opening_show", c).apply();
            PlayerButtonSetting.putVisible(PlayerButtonSetting.OPENING, c);
        });
        boolean ending = prefs().getBoolean("skip_ending_show", true);
        PlayerButtonSetting.putVisible(PlayerButtonSetting.ENDING, ending);
        binding.switchSkipEnding.setChecked(ending);
        binding.switchSkipEnding.setOnCheckedChangeListener((b, c) -> {
            prefs().edit().putBoolean("skip_ending_show", c).apply();
            PlayerButtonSetting.putVisible(PlayerButtonSetting.ENDING, c);
        });
    }
}
