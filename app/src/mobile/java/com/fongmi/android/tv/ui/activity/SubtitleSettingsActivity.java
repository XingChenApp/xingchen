package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySubtitleSettingsBinding;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.SubtitleSetting;
import com.fongmi.android.tv.ui.base.BaseActivity;

public class SubtitleSettingsActivity extends BaseActivity {

    private ActivitySubtitleSettingsBinding binding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SubtitleSettingsActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivitySubtitleSettingsBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        boolean subEnable = SubtitleSetting.isEnabled();
        binding.switchSubEnable.setChecked(subEnable);
        binding.switchSubEnable.setOnCheckedChangeListener((b, c) -> {
            SubtitleSetting.putEnabled(c);
            PlayerButtonSetting.putVisible(PlayerButtonSetting.TEXT, c);
            updateSubCardsVisibility(c);
        });
        updateSubCardsVisibility(subEnable);

        updateSubSizeUI();
        binding.subSizeSmall.setOnClickListener(v -> {
            SubtitleSetting.putSizeMode("small");
            updateSubSizeUI();
        });
        binding.subSizeMedium.setOnClickListener(v -> {
            SubtitleSetting.putSizeMode("medium");
            updateSubSizeUI();
        });
        binding.subSizeLarge.setOnClickListener(v -> {
            SubtitleSetting.putSizeMode("large");
            updateSubSizeUI();
        });

        updateSubColorUI();
        binding.colorWhite.setOnClickListener(v -> {
            SubtitleSetting.putColor(Color.WHITE);
            updateSubColorUI();
        });
        binding.colorYellow.setOnClickListener(v -> {
            SubtitleSetting.putColor(Color.YELLOW);
            updateSubColorUI();
        });
        binding.colorRed.setOnClickListener(v -> {
            SubtitleSetting.putColor(Color.RED);
            updateSubColorUI();
        });
        binding.colorGreen.setOnClickListener(v -> {
            SubtitleSetting.putColor(Color.GREEN);
            updateSubColorUI();
        });
        binding.colorCyan.setOnClickListener(v -> {
            SubtitleSetting.putColor(Color.CYAN);
            updateSubColorUI();
        });
        binding.colorBlue.setOnClickListener(v -> {
            SubtitleSetting.putColor(Color.BLUE);
            updateSubColorUI();
        });

        updateSubPosUI();
        binding.posTop.setOnClickListener(v -> {
            SubtitleSetting.putPositionMode("top");
            updateSubPosUI();
        });
        binding.posMiddle.setOnClickListener(v -> {
            SubtitleSetting.putPositionMode("middle");
            updateSubPosUI();
        });
        binding.posBottom.setOnClickListener(v -> {
            SubtitleSetting.putPositionMode("bottom");
            updateSubPosUI();
        });

        updateSubDelayUI();
        binding.delayM2.setOnClickListener(v -> {
            SubtitleSetting.putDelayMs(-2000L);
            updateSubDelayUI();
        });
        binding.delayM1.setOnClickListener(v -> {
            SubtitleSetting.putDelayMs(-1000L);
            updateSubDelayUI();
        });
        binding.delay0.setOnClickListener(v -> {
            SubtitleSetting.putDelayMs(0L);
            updateSubDelayUI();
        });
        binding.delayP1.setOnClickListener(v -> {
            SubtitleSetting.putDelayMs(1000L);
            updateSubDelayUI();
        });
        binding.delayP2.setOnClickListener(v -> {
            SubtitleSetting.putDelayMs(2000L);
            updateSubDelayUI();
        });

        binding.switchSubAutomatch.setChecked(SubtitleSetting.isAutoMatch());
        binding.switchSubAutomatch.setOnCheckedChangeListener((b, c) -> SubtitleSetting.putAutoMatch(c));

        updateSubLangUI();
        binding.langCnFirst.setOnClickListener(v -> {
            SubtitleSetting.putLang("cn_first");
            updateSubLangUI();
        });
        binding.langCnS.setOnClickListener(v -> {
            SubtitleSetting.putLang("cn_s");
            updateSubLangUI();
        });
        binding.langCnT.setOnClickListener(v -> {
            SubtitleSetting.putLang("cn_t");
            updateSubLangUI();
        });
        binding.langEn.setOnClickListener(v -> {
            SubtitleSetting.putLang("en");
            updateSubLangUI();
        });

        binding.switchSrcOpensub.setChecked(SubtitleSetting.isSrcEnabled("opensubtitles"));
        binding.switchSrcOpensub.setOnCheckedChangeListener((b, c) -> SubtitleSetting.putSrcEnabled("opensubtitles", c));
        binding.switchSrcSubhd.setChecked(SubtitleSetting.isSrcEnabled("subhd"));
        binding.switchSrcSubhd.setOnCheckedChangeListener((b, c) -> SubtitleSetting.putSrcEnabled("subhd", c));
        binding.switchSrcShooter.setChecked(SubtitleSetting.isSrcEnabled("shooter"));
        binding.switchSrcShooter.setOnCheckedChangeListener((b, c) -> SubtitleSetting.putSrcEnabled("shooter", c));
        binding.switchSrcZimuku.setChecked(SubtitleSetting.isSrcEnabled("zimuku"));
        binding.switchSrcZimuku.setOnCheckedChangeListener((b, c) -> SubtitleSetting.putSrcEnabled("zimuku", c));

        updateOpensubUI();
        binding.btnOpensubSave.setOnClickListener(v -> {
            SubtitleSetting.putOpenSubtitlesKey(binding.etOpensubKey.getText().toString());
            updateOpensubUI();
        });
        binding.btnOpensubClear.setOnClickListener(v -> {
            SubtitleSetting.putOpenSubtitlesKey("");
            binding.etOpensubKey.setText("");
            updateOpensubUI();
        });
    }

    private void updateSubCardsVisibility(boolean enable) {
        int v = enable ? View.VISIBLE : View.GONE;
        binding.cardSubSize.setVisibility(v);
        binding.cardSubColor.setVisibility(v);
        binding.cardSubPos.setVisibility(v);
        binding.cardSubDelay.setVisibility(v);
        binding.cardSubAutomatch.setVisibility(v);
        binding.cardSubLang.setVisibility(v);
        binding.cardSubSrc.setVisibility(v);
        binding.cardSubOpensub.setVisibility(v);
    }

    private void updateSubSizeUI() {
        String s = SubtitleSetting.getSizeMode();
        binding.subSizeSmall.setBackgroundResource("small".equals(s) ? R.drawable.xc_seg_selected : 0);
        binding.subSizeMedium.setBackgroundResource("medium".equals(s) ? R.drawable.xc_seg_selected : 0);
        binding.subSizeLarge.setBackgroundResource("large".equals(s) ? R.drawable.xc_seg_selected : 0);
    }

    private void updateSubColorUI() {
        int c = SubtitleSetting.hasCustomColor() ? SubtitleSetting.getColor() : Color.WHITE;
        float sel = 1.0f, unsel = 0.45f;
        binding.colorWhite.setAlpha(c == Color.WHITE ? sel : unsel);
        binding.colorYellow.setAlpha(c == Color.YELLOW ? sel : unsel);
        binding.colorRed.setAlpha(c == Color.RED ? sel : unsel);
        binding.colorGreen.setAlpha(c == Color.GREEN ? sel : unsel);
        binding.colorCyan.setAlpha(c == Color.CYAN ? sel : unsel);
        binding.colorBlue.setAlpha(c == Color.BLUE ? sel : unsel);
    }

    private void updateSubPosUI() {
        String p = SubtitleSetting.getPositionMode();
        binding.posTop.setBackgroundResource("top".equals(p) ? R.drawable.xc_seg_selected : 0);
        binding.posMiddle.setBackgroundResource("middle".equals(p) ? R.drawable.xc_seg_selected : 0);
        binding.posBottom.setBackgroundResource("bottom".equals(p) ? R.drawable.xc_seg_selected : 0);
    }

    private void updateSubDelayUI() {
        int d = (int) (SubtitleSetting.getDelayMs() / 1000L);
        binding.delayM2.setBackgroundResource(d == -2 ? R.drawable.xc_seg_selected : 0);
        binding.delayM1.setBackgroundResource(d == -1 ? R.drawable.xc_seg_selected : 0);
        binding.delay0.setBackgroundResource(d == 0 ? R.drawable.xc_seg_selected : 0);
        binding.delayP1.setBackgroundResource(d == 1 ? R.drawable.xc_seg_selected : 0);
        binding.delayP2.setBackgroundResource(d == 2 ? R.drawable.xc_seg_selected : 0);
    }

    private void updateSubLangUI() {
        String l = SubtitleSetting.getLang();
        binding.langCnFirst.setBackgroundResource("cn_first".equals(l) ? R.drawable.xc_seg_selected : 0);
        binding.langCnS.setBackgroundResource("cn_s".equals(l) ? R.drawable.xc_seg_selected : 0);
        binding.langCnT.setBackgroundResource("cn_t".equals(l) ? R.drawable.xc_seg_selected : 0);
        binding.langEn.setBackgroundResource("en".equals(l) ? R.drawable.xc_seg_selected : 0);
    }

    private void updateOpensubUI() {
        String key = SubtitleSetting.getOpenSubtitlesKey();
        if (key == null || key.isEmpty()) {
            binding.tvOpensubStatus.setText("未配置，OpenSubtitles 源暂不可用");
        } else {
            binding.tvOpensubStatus.setText("已配置");
            if (binding.etOpensubKey.getText().toString().isEmpty()) binding.etOpensubKey.setText(key);
        }
    }
}
