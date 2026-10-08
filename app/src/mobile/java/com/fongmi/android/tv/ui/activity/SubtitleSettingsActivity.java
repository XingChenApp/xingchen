package com.fongmi.android.tv.ui.activity;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.databinding.ActivitySubtitleSettingsBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
public class SubtitleSettingsActivity extends BaseActivity {
    private ActivitySubtitleSettingsBinding binding;
    private SharedPreferences prefs;
    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SubtitleSettingsActivity.class));
    }
    @Override
    protected ViewBinding getBinding() {
        binding = ActivitySubtitleSettingsBinding.inflate(getLayoutInflater());
        return binding;
    }
    private SharedPreferences prefs() {
        if (prefs == null) prefs = getSharedPreferences("xingchen", MODE_PRIVATE);
        return prefs;
    }
    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.switchSubEnable.setChecked(prefs().getBoolean("sub_enable", true));
        binding.switchSubEnable.setOnCheckedChangeListener((b, c) -> prefs().edit().putBoolean("sub_enable", c).apply());
        updateSubSizeUI();
        binding.subSizeSmall.setOnClickListener(v -> { setSubSize("small"); updateSubSizeUI(); });
        binding.subSizeMedium.setOnClickListener(v -> { setSubSize("medium"); updateSubSizeUI(); });
        binding.subSizeLarge.setOnClickListener(v -> { setSubSize("large"); updateSubSizeUI(); });
        updateSubColorUI();
        binding.colorWhite.setOnClickListener(v -> { setSubColor("white"); updateSubColorUI(); });
        binding.colorYellow.setOnClickListener(v -> { setSubColor("yellow"); updateSubColorUI(); });
        binding.colorRed.setOnClickListener(v -> { setSubColor("red"); updateSubColorUI(); });
        binding.colorGreen.setOnClickListener(v -> { setSubColor("green"); updateSubColorUI(); });
        binding.colorCyan.setOnClickListener(v -> { setSubColor("cyan"); updateSubColorUI(); });
        binding.colorBlue.setOnClickListener(v -> { setSubColor("blue"); updateSubColorUI(); });
        updateSubPosUI();
        binding.posTop.setOnClickListener(v -> { setSubPos("top"); updateSubPosUI(); });
        binding.posMiddle.setOnClickListener(v -> { setSubPos("middle"); updateSubPosUI(); });
        binding.posBottom.setOnClickListener(v -> { setSubPos("bottom"); updateSubPosUI(); });
        updateSubDelayUI();
        binding.delayM2.setOnClickListener(v -> { setSubDelay(-2); updateSubDelayUI(); });
        binding.delayM1.setOnClickListener(v -> { setSubDelay(-1); updateSubDelayUI(); });
        binding.delay0.setOnClickListener(v -> { setSubDelay(0); updateSubDelayUI(); });
        binding.delayP1.setOnClickListener(v -> { setSubDelay(1); updateSubDelayUI(); });
        binding.delayP2.setOnClickListener(v -> { setSubDelay(2); updateSubDelayUI(); });
        binding.switchSubAutomatch.setChecked(prefs().getBoolean("sub_automatch", true));
        binding.switchSubAutomatch.setOnCheckedChangeListener((b, c) -> prefs().edit().putBoolean("sub_automatch", c).apply());
        updateSubLangUI();
        binding.langCnFirst.setOnClickListener(v -> { setSubLang("cn_first"); updateSubLangUI(); });
        binding.langCnS.setOnClickListener(v -> { setSubLang("cn_s"); updateSubLangUI(); });
        binding.langCnT.setOnClickListener(v -> { setSubLang("cn_t"); updateSubLangUI(); });
        binding.langEn.setOnClickListener(v -> { setSubLang("en"); updateSubLangUI(); });
        binding.switchSrcOpensub.setChecked(prefs().getBoolean("sub_src_opensub", true));
        binding.switchSrcOpensub.setOnCheckedChangeListener((b, c) -> prefs().edit().putBoolean("sub_src_opensub", c).apply());
        binding.switchSrcSubhd.setChecked(prefs().getBoolean("sub_src_subhd", true));
        binding.switchSrcSubhd.setOnCheckedChangeListener((b, c) -> prefs().edit().putBoolean("sub_src_subhd", c).apply());
        binding.switchSrcShooter.setChecked(prefs().getBoolean("sub_src_shooter", true));
        binding.switchSrcShooter.setOnCheckedChangeListener((b, c) -> prefs().edit().putBoolean("sub_src_shooter", c).apply());
        binding.switchSrcZimuku.setChecked(prefs().getBoolean("sub_src_zimuku", true));
        binding.switchSrcZimuku.setOnCheckedChangeListener((b, c) -> prefs().edit().putBoolean("sub_src_zimuku", c).apply());
        updateOpensubUI();
        binding.btnOpensubSave.setOnClickListener(v -> {
            String key = binding.etOpensubKey.getText().toString().trim();
            prefs().edit().putString("sub_opensub_key", key).apply();
            updateOpensubUI();
        });
        binding.btnOpensubClear.setOnClickListener(v -> {
            prefs().edit().remove("sub_opensub_key").apply();
            binding.etOpensubKey.setText("");
            updateOpensubUI();
        });
    }
    private void updateSubSizeUI() {
        String s = prefs().getString("sub_size", "medium");
        binding.subSizeSmall.setBackgroundResource("small".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.subSizeMedium.setBackgroundResource("medium".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.subSizeLarge.setBackgroundResource("large".equals(s) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void setSubSize(String s) { prefs().edit().putString("sub_size", s).apply(); }
    private void updateSubColorUI() {
        String c = prefs().getString("sub_color", "white");
        float sel = 1.0f, unsel = 0.45f;
        binding.colorWhite.setAlpha("white".equals(c) ? sel : unsel);
        binding.colorYellow.setAlpha("yellow".equals(c) ? sel : unsel);
        binding.colorRed.setAlpha("red".equals(c) ? sel : unsel);
        binding.colorGreen.setAlpha("green".equals(c) ? sel : unsel);
        binding.colorCyan.setAlpha("cyan".equals(c) ? sel : unsel);
        binding.colorBlue.setAlpha("blue".equals(c) ? sel : unsel);
    }
    private void setSubColor(String c) { prefs().edit().putString("sub_color", c).apply(); }
    private void updateSubPosUI() {
        String p = prefs().getString("sub_pos", "bottom");
        binding.posTop.setBackgroundResource("top".equals(p) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.posMiddle.setBackgroundResource("middle".equals(p) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.posBottom.setBackgroundResource("bottom".equals(p) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void setSubPos(String p) { prefs().edit().putString("sub_pos", p).apply(); }
    private void updateSubDelayUI() {
        int d = prefs().getInt("sub_delay", 0);
        binding.delayM2.setBackgroundResource(d == -2 ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.delayM1.setBackgroundResource(d == -1 ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.delay0.setBackgroundResource(d == 0 ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.delayP1.setBackgroundResource(d == 1 ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.delayP2.setBackgroundResource(d == 2 ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void setSubDelay(int d) { prefs().edit().putInt("sub_delay", d).apply(); }
    private void updateSubLangUI() {
        String l = prefs().getString("sub_lang", "cn_first");
        binding.langCnFirst.setBackgroundResource("cn_first".equals(l) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.langCnS.setBackgroundResource("cn_s".equals(l) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.langCnT.setBackgroundResource("cn_t".equals(l) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
        binding.langEn.setBackgroundResource("en".equals(l) ? com.fongmi.android.tv.R.drawable.xc_seg_selected : 0);
    }
    private void setSubLang(String l) { prefs().edit().putString("sub_lang", l).apply(); }
    private void updateOpensubUI() {
        String key = prefs().getString("sub_opensub_key", "");
        if (key == null || key.isEmpty()) {
            binding.tvOpensubStatus.setText("未配置，OpenSubtitles 源暂不可用");
        } else {
            binding.tvOpensubStatus.setText("已配置");
            if (binding.etOpensubKey.getText().toString().isEmpty()) binding.etOpensubKey.setText(key);
        }
    }
}
