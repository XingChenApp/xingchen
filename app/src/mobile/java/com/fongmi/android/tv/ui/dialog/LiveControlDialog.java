package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityLiveBinding;
import com.fongmi.android.tv.databinding.DialogLiveControlBinding;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.ui.activity.LiveActivity;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Arrays;
import java.util.List;

public class LiveControlDialog extends BaseBottomSheetDialog {

    private final String[] scale;
    private DialogLiveControlBinding binding;
    private ActivityLiveBinding parent;
    private List<TextView> scales;

    public LiveControlDialog() {
        this.scale = ResUtil.getStringArray(R.array.select_scale);
    }

    public static LiveControlDialog create() {
        return new LiveControlDialog();
    }

    public LiveControlDialog parent(ActivityLiveBinding parent) {
        this.parent = parent;
        return this;
    }

    // parent 是宿主的视图 binding，没法走 arguments：系统重建后从宿主 LiveActivity 重新取
    // （与 listener() 从 requireActivity 取的口径一致）；取不到说明宿主已不是直播页，返回 null 由调用方关窗。
    private ActivityLiveBinding parent() {
        if (parent == null && getActivity() instanceof LiveActivity live) parent = live.liveBinding();
        return parent;
    }

    public LiveControlDialog show(FragmentActivity activity) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) if (fragment instanceof LiveControlDialog) return this;
        show(activity.getSupportFragmentManager(), null);
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        configureWindow(dialog);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow(getDialog());
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        binding = DialogLiveControlBinding.inflate(inflater, container, false);
        scales = Arrays.asList(binding.scale0, binding.scale1, binding.scale2, binding.scale3, binding.scale4);
        return binding;
    }

    @Override
    protected void initView() {
        ActivityLiveBinding parent = parent();
        if (parent == null) {
            dismissAllowingStateLoss();
            return;
        }
        binding.sheetWall.setVisibility(View.GONE);
        binding.player.setText(parent.control.action.player.getText());
        binding.decode.setText(parent.control.action.decode.getText());
        binding.playParams.setSelected(parent.control.action.playParams.isSelected());
        binding.invert.setSelected(parent.control.action.invert.isSelected());
        binding.across.setSelected(parent.control.action.across.isSelected());
        binding.change.setSelected(parent.control.action.change.isSelected());
        setTrackVisible();
        setListStyleSelected();
        setScaleText();
        binding.controlScroll.post(() -> binding.controlScroll.scrollTo(0, 0));
    }

    @Override
    protected void initEvent() {
        ActivityLiveBinding parent = parent();
        if (parent == null) return;
        binding.config.setOnClickListener(v -> {
            Listener listener = listener();
            if (listener != null) listener.onLiveConfigPanel();
            dismiss();
        });
        binding.source.setOnClickListener(v -> {
            Listener listener = listener();
            if (listener != null) listener.onLiveSourcePanel();
        });
        binding.epg.setOnClickListener(v -> {
            Listener listener = listener();
            if (listener != null) listener.onLiveEpgPanel();
            dismiss();
        });
        binding.cast.setOnClickListener(v -> {
            Listener listener = listener();
            if (listener != null) listener.onLiveCastPanel();
        });
        binding.pip.setOnClickListener(v -> {
            Listener listener = listener();
            if (listener != null) listener.onLivePiPPanel();
            dismiss();
        });
        binding.background.setOnClickListener(v -> {
            Listener listener = listener();
            if (listener != null) listener.onLiveBackgroundPanel();
            dismiss();
        });
        binding.invert.setOnClickListener(v -> active(binding.invert, parent.control.action.invert));
        binding.across.setOnClickListener(v -> active(binding.across, parent.control.action.across));
        binding.change.setOnClickListener(v -> active(binding.change, parent.control.action.change));
        binding.listTransparent.setOnClickListener(v -> setListStyle(true));
        binding.listReadable.setOnClickListener(v -> setListStyle(false));
        binding.player.setOnClickListener(v -> dismiss(parent.control.action.player));
        binding.player.setOnLongClickListener(v -> longClick(binding.player, parent.control.action.player));
        binding.decode.setOnClickListener(v -> click(binding.decode, parent.control.action.decode));
        binding.playParams.setOnClickListener(v -> dismiss(parent.control.action.playParams));
        binding.text.setOnClickListener(v -> onTrack(binding.text));
        binding.audio.setOnClickListener(v -> onTrack(binding.audio));
        binding.video.setOnClickListener(v -> onTrack(binding.video));
        for (TextView view : scales) view.setOnClickListener(this::setScale);
    }

    private Listener listener() {
        return requireActivity() instanceof Listener listener ? listener : null;
    }

    private void onTrack(View view) {
        Integer type = parseTagInt(view);
        if (type == null) return;
        Listener listener = listener();
        if (listener != null) listener.onLiveTrackPanel(type);
    }

    private Integer parseTagInt(View view) {
        Object tag = view.getTag();
        if (tag == null) return null;
        try {
            return Integer.parseInt(tag.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void setTrackVisible() {
        ActivityLiveBinding parent = parent();
        if (parent == null) return;
        binding.text.setVisibility(parent.control.action.text.getVisibility());
        binding.audio.setVisibility(parent.control.action.audio.getVisibility());
        binding.video.setVisibility(parent.control.action.video.getVisibility());
        boolean visible = binding.text.getVisibility() != View.GONE || binding.audio.getVisibility() != View.GONE || binding.video.getVisibility() != View.GONE;
        binding.trackText.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.trackRow.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setScaleText() {
        for (int i = 0; i < scales.size() && i < scale.length; i++) {
            scales.get(i).setText(scale[i]);
            scales.get(i).setSelected(i == LiveSetting.getScale());
        }
    }

    private void setListStyleSelected() {
        boolean classic = LiveSetting.isListStyleClassic();
        binding.listTransparent.setSelected(classic);
        binding.listReadable.setSelected(!classic);
    }

    private void setListStyle(boolean classic) {
        Listener listener = listener();
        if (listener != null) listener.onLiveListStylePanel(classic);
        setListStyleSelected();
    }

    public void setPlayer() {
        ActivityLiveBinding parent = parent();
        if (binding == null || parent == null) return;
        binding.player.setText(parent.control.action.player.getText());
        binding.decode.setText(parent.control.action.decode.getText());
        binding.playParams.setSelected(parent.control.action.playParams.isSelected());
        setTrackVisible();
    }

    private void setScale(View view) {
        Integer tag = parseTagInt(view);
        if (tag == null) return;
        for (TextView textView : scales) textView.setSelected(false);
        Listener listener = listener();
        if (listener != null) listener.onLiveScalePanel(tag);
        view.setSelected(true);
    }

    private void active(TextView view, TextView target) {
        target.performClick();
        view.setSelected(target.isSelected());
    }

    private void click(TextView view, TextView target) {
        target.performClick();
        view.setText(target.getText());
    }

    private void dismiss(View target) {
        target.postDelayed(target::performClick, 200);
        dismiss();
    }

    private boolean longClick(TextView view, TextView target) {
        target.performLongClick();
        view.setText(target.getText());
        return true;
    }

    private void configureWindow(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setDimAmount(0f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        WindowCompat.setDecorFitsSystemWindows(window, true);
    }

    @Override
    protected boolean transparent() {
        return true;
    }

    @Override
    protected boolean stableOverlay() {
        return true;
    }

    @Override
    protected void setBehavior(BottomSheetDialog dialog) {
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        sheet.setBackgroundColor(ResUtil.getColor(R.color.transparent));
        int height = getPanelHeight();
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.height = height;
        sheet.setLayoutParams(params);
        BottomSheetBehavior<FrameLayout> behavior = behaviorOf(sheet);
        if (behavior != null) {
            behavior.setPeekHeight(height);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setDraggable(false);
        }
    }

    private int getPanelHeight() {
        int screen = ResUtil.getScreenHeight(requireContext());
        if (ResUtil.isLand(requireContext())) return Math.max(ResUtil.dp2px(260), Math.min(ResUtil.dp2px(420), Math.round(screen * 0.82f)));
        return Math.max(ResUtil.dp2px(330), Math.min(ResUtil.dp2px(520), Math.round(screen * 0.52f)));
    }

    public interface Listener {

        void onLiveConfigPanel();

        void onLiveSourcePanel();

        void onLiveEpgPanel();

        void onLiveCastPanel();

        void onLivePiPPanel();

        void onLiveBackgroundPanel();

        void onLiveListStylePanel(boolean classic);

        void onLiveScalePanel(int scale);

        void onLiveTrackPanel(int type);
    }
}
