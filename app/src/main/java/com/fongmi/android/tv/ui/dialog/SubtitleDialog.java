package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.ui.SubtitleView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogSubtitleBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.SubtitleSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.bassaer.library.MDColor;

public final class SubtitleDialog extends BaseBottomSheetDialog {

    private static final float DEFAULT_TEXT_SIZE = 0.0533f;
    private static final float TEXT_STEP = 0.002f;
    private static final float POSITION_STEP = 0.005f;

    private DialogSubtitleBinding binding;
    private SubtitleView subtitleView;
    private PlayerManager player;

    public static SubtitleDialog create() {
        return new SubtitleDialog();
    }

    public SubtitleDialog view(SubtitleView subtitleView) {
        this.subtitleView = subtitleView;
        return this;
    }

    public SubtitleDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof SubtitleDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    private boolean isFull() {
        return Util.isFullscreen(getActivity());
    }

    @Override
    protected boolean transparent() {
        return isFull();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogSubtitleBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (!isFull()) return;
        int count = binding.getRoot().getChildCount();
        for (int i = 0; i < count; i++) {
            View child = binding.getRoot().getChildAt(i);
            if (child instanceof ImageView imageView && imageView.getDrawable() != null) imageView.getDrawable().setTint(MDColor.WHITE);
        }
    }

    @Override
    protected void initEvent() {
        binding.up.setOnClickListener(this::onUp);
        binding.down.setOnClickListener(this::onDown);
        binding.large.setOnClickListener(this::onLarge);
        binding.small.setOnClickListener(this::onSmall);
        binding.reset.setOnClickListener(this::onReset);
        binding.aiSubtitle.setOnClickListener(this::onAiSubtitle);
        refreshAiSubtitle();
    }

    private void onAiSubtitle(View view) {
        if (getActivity() == null) return;
        // 只在支持的设备上显示
        if (!com.fongmi.android.tv.ai.asr.AiAsrPluginManager.isSupported()) {
            com.fongmi.android.tv.utils.Notify.show("当前设备不支持本地语音识别");
            return;
        }
        com.fongmi.android.tv.ai.asr.AiAsrController.get().toggle(getActivity(),
                new com.fongmi.android.tv.ai.asr.AiAsrController.ToggleCallback() {
                    @Override
                    public void onPreparing(String msg) {
                        com.fongmi.android.tv.utils.Notify.show(msg);
                    }

                    @Override
                    public void onEnabled() {
                        com.fongmi.android.tv.utils.Notify.show("AI 本地字幕已开启");
                        refreshAiSubtitle();
                    }

                    @Override
                    public void onDisabled() {
                        com.fongmi.android.tv.utils.Notify.show("AI 本地字幕已关闭");
                        refreshAiSubtitle();
                    }

                    @Override
                    public void onError(String msg) {
                        com.fongmi.android.tv.utils.Notify.show(msg);
                    }
                });
    }

    private void refreshAiSubtitle() {
        if (getActivity() == null || binding.aiSubtitle == null) return;
        try {
            boolean enabled = com.fongmi.android.tv.ai.asr.AiAsrController.get()
                    .isEnabled(getActivity());
            binding.aiSubtitle.setText(enabled ? "AI实时字幕·开" : "AI实时字幕");
            binding.aiSubtitle.setTextColor(enabled ? 0xFFE8A33D : 0xB3000000);
        } catch (Exception ignored) {}
    }

    private void onUp(View view) {
        if (isNativeSubtitleStyle()) {
            setNativePosition(PlayerSetting.getSubtitlePosition() + POSITION_STEP);
            return;
        }
        if (subtitleView == null) return;
        subtitleView.addPosition(POSITION_STEP);
        SubtitleSetting.putCustomPosition(subtitleView.getPosition());
    }

    private void onDown(View view) {
        if (isNativeSubtitleStyle()) {
            setNativePosition(PlayerSetting.getSubtitlePosition() - POSITION_STEP);
            return;
        }
        if (subtitleView == null) return;
        subtitleView.subPosition(POSITION_STEP);
        SubtitleSetting.putCustomPosition(subtitleView.getPosition());
    }

    private void onLarge(View view) {
        if (isNativeSubtitleStyle()) {
            setNativeTextSize(currentTextSize() + TEXT_STEP);
            return;
        }
        if (subtitleView == null) return;
        subtitleView.addTextSize(TEXT_STEP);
        SubtitleSetting.putCustomTextSize(subtitleView.getTextSize());
    }

    private void onSmall(View view) {
        if (isNativeSubtitleStyle()) {
            setNativeTextSize(currentTextSize() - TEXT_STEP);
            return;
        }
        if (subtitleView == null) return;
        subtitleView.subTextSize(TEXT_STEP);
        SubtitleSetting.putCustomTextSize(subtitleView.getTextSize());
    }

    private void onReset(View view) {
        SubtitleSetting.putCustomTextSize(0.0f);
        SubtitleSetting.putCustomPosition(0.0f);
        if (isNativeSubtitleStyle()) {
            player.setSubtitleStyle(0.0f, 0.0f);
        } else if (subtitleView != null) {
            subtitleView.reset();
        }
    }

    private boolean isNativeSubtitleStyle() {
        return player != null && player.supportsSubtitleStyle();
    }

    private float currentTextSize() {
        float value = PlayerSetting.getSubtitleTextSize();
        return value <= 0 ? DEFAULT_TEXT_SIZE : value;
    }

    private void setNativeTextSize(float value) {
        value = Math.max(0.02f, Math.min(0.12f, value));
        SubtitleSetting.putCustomTextSize(value);
        player.setSubtitleStyle(value, PlayerSetting.getSubtitlePosition());
    }

    private void setNativePosition(float value) {
        value = Math.max(-0.5f, Math.min(1.0f, value));
        SubtitleSetting.putCustomPosition(value);
        player.setSubtitleStyle(PlayerSetting.getSubtitleTextSize(), value);
    }

    @Override
    public void onResume() {
        super.onResume();
        getDialog().getWindow().setLayout(ResUtil.dp2px(isFull() ? 232 : 216), -1);
    }
}
