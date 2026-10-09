package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityDanmakuSettingsBinding;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.ui.base.BaseActivity;

public class DanmakuSettingsActivity extends BaseActivity {

    private ActivityDanmakuSettingsBinding binding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, DanmakuSettingsActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityDanmakuSettingsBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.switchDanmuLoad.setChecked(DanmakuSetting.isLoad());
        binding.switchDanmuLoad.setOnCheckedChangeListener((b, c) -> DanmakuSetting.putLoad(c));

        updateApiDesc();
        binding.cardDanmuApi.setOnClickListener(v -> showApiDialog());

        binding.switchDanmuAutosearch.setChecked(DanmakuSetting.isAuto());
        binding.switchDanmuAutosearch.setOnCheckedChangeListener((b, c) -> DanmakuSetting.putAuto(c));

        binding.switchDanmuSpiderFirst.setChecked(DanmakuSetting.isSpiderFirst());
        binding.switchDanmuSpiderFirst.setOnCheckedChangeListener((b, c) -> DanmakuSetting.putSpiderFirst(c));

        boolean enable = DanmakuSetting.isShow();
        binding.switchDanmuEnable.setChecked(enable);
        binding.switchDanmuEnable.setOnCheckedChangeListener((b, c) -> {
            DanmakuSetting.putShow(c);
            PlayerButtonSetting.putVisible(PlayerButtonSetting.DANMAKU, c);
            updateSubCardsVisibility(c);
        });
        updateSubCardsVisibility(enable);

        int alpha = Math.round((1f - DanmakuSetting.getTransparency()) * 100f);
        binding.seekDanmuAlpha.setProgress(alpha);
        binding.tvDanmuAlpha.setText(alpha + "%");
        binding.seekDanmuAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                binding.tvDanmuAlpha.setText(p + "%");
                if (fromUser) DanmakuSetting.putTransparency(1f - p / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });

        updateSpeedUI();
        binding.speedSlow.setOnClickListener(v -> {
            DanmakuSetting.putDurationMs(12000L);
            updateSpeedUI();
        });
        binding.speedNormal.setOnClickListener(v -> {
            DanmakuSetting.putDurationMs(8000L);
            updateSpeedUI();
        });
        binding.speedFast.setOnClickListener(v -> {
            DanmakuSetting.putDurationMs(5000L);
            updateSpeedUI();
        });

        updateSizeUI();
        binding.sizeSmall.setOnClickListener(v -> {
            DanmakuSetting.putTextScale(0.8f);
            updateSizeUI();
        });
        binding.sizeMedium.setOnClickListener(v -> {
            DanmakuSetting.putTextScale(1.0f);
            updateSizeUI();
        });
        binding.sizeLarge.setOnClickListener(v -> {
            DanmakuSetting.putTextScale(1.25f);
            updateSizeUI();
        });

        updateAreaUI();
        binding.areaTop.setOnClickListener(v -> {
            DanmakuSetting.putAreaMode("top");
            updateAreaUI();
        });
        binding.areaFull.setOnClickListener(v -> {
            DanmakuSetting.putAreaMode("full");
            updateAreaUI();
        });
        binding.areaBottom.setOnClickListener(v -> {
            DanmakuSetting.putAreaMode("bottom");
            updateAreaUI();
        });
    }

    private void updateApiDesc() {
        String url = DanmakuSetting.getEffectiveApiUrl();
        binding.tvDanmuApiDesc.setText(url == null || url.isEmpty() ? "未设置" : url);
    }

    private void showApiDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(DanmakuSetting.getApiUrl());
        input.setHint("https://");
        input.setSelection(input.getText().length());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("弹幕搜索接口")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    DanmakuSetting.putApiUrl(input.getText().toString());
                    updateApiDesc();
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("清空", (d, w) -> {
                    DanmakuSetting.putApiUrl("");
                    updateApiDesc();
                })
                .show();
    }

    private void updateSubCardsVisibility(boolean enable) {
        int v = enable ? View.VISIBLE : View.GONE;
        binding.cardDanmuAlpha.setVisibility(v);
        binding.cardDanmuSpeed.setVisibility(v);
        binding.cardDanmuSize.setVisibility(v);
        binding.cardDanmuArea.setVisibility(v);
    }

    private String currentSpeed() {
        long d = DanmakuSetting.getDurationMs();
        if (d >= 10000L) return "slow";
        if (d <= 6000L) return "fast";
        return "normal";
    }

    private void updateSpeedUI() {
        String s = currentSpeed();
        binding.speedSlow.setBackgroundResource("slow".equals(s) ? R.drawable.xc_seg_selected : 0);
        binding.speedNormal.setBackgroundResource("normal".equals(s) ? R.drawable.xc_seg_selected : 0);
        binding.speedFast.setBackgroundResource("fast".equals(s) ? R.drawable.xc_seg_selected : 0);
    }

    private String currentSize() {
        float s = DanmakuSetting.getTextScale();
        if (s < 0.9f) return "small";
        if (s > 1.1f) return "large";
        return "medium";
    }

    private void updateSizeUI() {
        String s = currentSize();
        binding.sizeSmall.setBackgroundResource("small".equals(s) ? R.drawable.xc_seg_selected : 0);
        binding.sizeMedium.setBackgroundResource("medium".equals(s) ? R.drawable.xc_seg_selected : 0);
        binding.sizeLarge.setBackgroundResource("large".equals(s) ? R.drawable.xc_seg_selected : 0);
    }

    private void updateAreaUI() {
        String s = DanmakuSetting.getAreaMode();
        binding.areaTop.setBackgroundResource("top".equals(s) ? R.drawable.xc_seg_selected : 0);
        binding.areaFull.setBackgroundResource("full".equals(s) ? R.drawable.xc_seg_selected : 0);
        binding.areaBottom.setBackgroundResource("bottom".equals(s) ? R.drawable.xc_seg_selected : 0);
    }
}
