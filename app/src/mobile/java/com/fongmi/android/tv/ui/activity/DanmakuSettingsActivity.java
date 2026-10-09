package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

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
        binding.cardDanmuApi.setOnClickListener(v -> showServerDialog());

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
        binding.tvDanmuApiDesc.setText(DanmakuSetting.hasSourceServer() ? "弹弹play" : "弹弹play（未配置）");
    }

    private void showServerDialog() {
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        int dark = Color.parseColor("#202124");
        int darkGray = Color.parseColor("#5F6368");
        int accent = Color.parseColor("#F0A400");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundResource(R.drawable.shape_dialog_glass_white_panel);
        layout.setPadding(pad, pad, pad, pad);

        TextView titleView = new TextView(this);
        titleView.setText("弹幕源");
        titleView.setTextSize(18);
        titleView.setTextColor(dark);
        layout.addView(titleView);

        TextView defaultView = new TextView(this);
        defaultView.setText("默认：弹弹play");
        defaultView.setTextSize(16);
        defaultView.setTextColor(dark);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = pad / 2;
        layout.addView(defaultView, dlp);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(DanmakuSetting.getSourceServer());
        input.setHint("自定义源（可选填）");
        input.setTextColor(dark);
        input.setHintTextColor(darkGray);
        input.setSelection(input.getText().length());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 2;
        layout.addView(input, lp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.topMargin = pad;
        layout.addView(actions, alp);

        TextView cancelBtn = makeGlassActionBtn(accent, "取消");
        TextView saveBtn = makeGlassActionBtn(accent, "保存");
        actions.addView(cancelBtn);
        actions.addView(saveBtn);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(layout)
                .create();
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        saveBtn.setOnClickListener(v -> {
            DanmakuSetting.putSourceServer(input.getText().toString());
            updateApiDesc();
            dialog.dismiss();
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private TextView makeGlassActionBtn(int color, String text) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(15);
        btn.setTextColor(color);
        int hp = (int) (16 * getResources().getDisplayMetrics().density);
        int vp = (int) (8 * getResources().getDisplayMetrics().density);
        btn.setPadding(hp, vp, hp, vp);
        return btn;
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
