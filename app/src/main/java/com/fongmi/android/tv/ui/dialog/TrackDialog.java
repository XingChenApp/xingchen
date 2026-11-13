package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;
import androidx.media3.ui.DefaultTrackNameProvider;
import androidx.media3.ui.TrackNameProvider;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ai.AiProvider;
import com.fongmi.android.tv.ai.AiStore;
import com.fongmi.android.tv.ai.AiSubtitleTranslator;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.databinding.DialogTrackBinding;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.ui.adapter.TrackAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TrackDialog extends BaseBottomSheetDialog implements TrackAdapter.OnClickListener {

    private final TrackNameProvider provider;
    private final TrackAdapter adapter;
    private DialogTrackBinding binding;
    private PlayerManager player;
    private boolean secondarySubtitle;
    private int type;
    public static TrackDialog create() {
        return new TrackDialog();
    }

    public TrackDialog() {
        this.adapter = new TrackAdapter(this);
        this.provider = new DefaultTrackNameProvider(App.get().getResources());
    }

    public TrackDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public TrackDialog type(int type) {
        this.type = type;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof TrackDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    private boolean hasChoose() {
        return !secondarySubtitle && type == C.TRACK_TYPE_TEXT && player.isVod();
    }

    private boolean hasText() {
        return !secondarySubtitle && type == C.TRACK_TYPE_TEXT && player.haveTrack(type);
    }

    private boolean hasAudio() {
        return type == C.TRACK_TYPE_AUDIO && player.haveTrack(type);
    }

    private boolean hasSecondarySubtitle() {
        return type == C.TRACK_TYPE_TEXT && player.supportsSecondarySubtitle() && player.haveTrack(type);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogTrackBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        refreshTrackList();
    }

    @Override
    protected void initEvent() {
        binding.secondary.setOnClickListener(this::onSecondary);
        binding.offset.setOnClickListener(this::onOffset);
        binding.choose.setOnClickListener(this::onChoose);
        binding.subtitle.setOnClickListener(this::onSubtitle);
        binding.search.setOnClickListener(this::onSearch);
        binding.aiRealtime.setOnClickListener(this::onAiRealtime);
        binding.aiTranslate.setOnClickListener(this::onAiTranslate);
    }

    private void refreshTrackList() {
        adapter.replaceAll(getTrack());
        binding.title.setText(getTitle());
        binding.secondary.setText(secondarySubtitle ? R.string.play_track_primary_subtitle : R.string.play_track_secondary_subtitle);
        binding.secondary.setContentDescription(binding.secondary.getText());
        binding.secondary.setVisibility(hasSecondarySubtitle() ? View.VISIBLE : View.GONE);
        binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelected()));
        binding.recycler.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        binding.offset.setVisibility(hasText() || hasAudio() ? View.VISIBLE : View.GONE);
        binding.choose.setVisibility(hasChoose() ? View.VISIBLE : View.GONE);
        binding.subtitle.setVisibility(hasText() ? View.VISIBLE : View.GONE);
        binding.search.setVisibility(hasChoose() ? View.VISIBLE : View.GONE);
        binding.aiTranslate.setVisibility(hasText() && AiStore.hasAny(App.get()) ? View.VISIBLE : View.GONE);
        // AI 实时字幕：只在字幕轨框显示（音轨/视频轨不显示；第二字幕模式不显示）
        binding.aiRealtime.setVisibility(type == C.TRACK_TYPE_TEXT && !secondarySubtitle ? View.VISIBLE : View.GONE);
        refreshAiRealtime();
        // 已翻译状态：高亮按钮提示可恢复
        binding.aiTranslate.setAlpha(AiSubtitleTranslator.isTranslatedActive() ? 1.0f : 0.7f);
    }

    private String getTitle() {
        if (secondarySubtitle) return getString(R.string.select_secondary_subtitle_track);
        return ResUtil.getStringArray(R.array.select_track)[type - 1];
    }

    private void onSecondary(View view) {
        secondarySubtitle = !secondarySubtitle;
        refreshTrackList();
    }

    private void onOffset(View view) {
        OffsetDialog.create().player(player).type(type).show(requireActivity());
    }

    private void onChoose(View view) {
        FileChooser.from(launcher).show(new String[]{MimeTypes.APPLICATION_SUBRIP, MimeTypes.TEXT_SSA, MimeTypes.TEXT_VTT, MimeTypes.APPLICATION_TTML, "audio/*", "text/*", "application/octet-stream"});
        player.pause();
    }

    private void onSubtitle(View view) {
        if (requireActivity() instanceof Listener) {
            Listener listener = (Listener) requireActivity();
            App.post(listener::onSubtitleClick, 100);
        }
        dismiss();
    }

    private void onSearch(View view) {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        dismissAllowingStateLoss();
        SubtitleSearchDialog.create().player(player).restoreParent(true).show(activity);
    }

    // ---------------- AI 实时字幕 ----------------

    private void onAiRealtime(View view) {
        if (getActivity() == null) return;
        // 只在支持的设备上开启
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
                        refreshAiRealtime();
                    }

                    @Override
                    public void onDisabled() {
                        com.fongmi.android.tv.utils.Notify.show("AI 本地字幕已关闭");
                        refreshAiRealtime();
                    }

                    @Override
                    public void onError(String msg) {
                        com.fongmi.android.tv.utils.Notify.show(msg);
                    }
                });
    }

    private void refreshAiRealtime() {
        if (getActivity() == null || binding.aiRealtime == null) return;
        try {
            boolean enabled = com.fongmi.android.tv.ai.asr.AiAsrController.get()
                    .isEnabled(getActivity());
            binding.aiRealtime.setText(enabled ? "AI实时字幕·开" : "AI实时字幕");
            binding.aiRealtime.setTextColor(enabled ? 0xFFE8A33D : 0xB3000000);
        } catch (Exception ignored) {
        }
    }

    // ---------------- AI 字幕翻译 ----------------

    private AlertDialog translateProgressDialog;
    private ProgressBar translateProgressBar;
    private TextView translateProgressText;
    // 进度窗挂在宿主 Activity 上（本弹窗在翻译开始时已自关）：宿主销毁
    // 必须连带收窗，否则进度窗成孤窗、回调还摸已销毁的宿主。
    private FragmentActivity translateHost;
    private final LifecycleEventObserver translateHostObserver = (source, event) -> {
        if (event == Lifecycle.Event.ON_DESTROY) dismissTranslateProgress();
    };

    private void onAiTranslate(View view) {
        Context ctx = getContext();
        if (ctx == null) return;
        AiProvider provider = AiStore.resolveForFeature(ctx, AiStore.FEATURE_SUBTITLE);
        if (provider == null) {
            Notify.show("请先在 AI 设置中配置服务商");
            return;
        }
        if (AiSubtitleTranslator.isTranslatedActive()) {
            FragmentActivity activity = getActivity();
            if (activity == null) return;
            ChoiceDialog.showSingle(activity.getSupportFragmentManager(), "AI 字幕翻译",
                    new CharSequence[]{"恢复原文", "重新翻译"}, -1, which -> {
                        if (which == 0) restoreOriginalSubtitle();
                        else startTranslateFlow();
                    });
            return;
        }
        startTranslateFlow();
    }

    private void startTranslateFlow() {
        File subFile = AiSubtitleTranslator.getCurrentSubFile();
        if (subFile == null || !subFile.exists()) {
            Notify.show("请先加载外挂字幕文件");
            return;
        }
        List<AiSubtitleTranslator.SubtitleLine> lines =
                AiSubtitleTranslator.parseSrt(AiSubtitleTranslator.readFile(subFile));
        if (lines.isEmpty()) {
            Notify.show("字幕解析失败，仅支持 SRT 格式");
            return;
        }
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        final List<AiSubtitleTranslator.SubtitleLine> finalLines = lines;
        final File finalFile = subFile;
        showLangPairDialog(activity, (srcLang, targetLang) -> confirmTranslate(finalLines, finalFile, srcLang, targetLang));
    }

    /** 翻译语言选择：源语言下拉 + 目标语言下拉 */
    private void showLangPairDialog(FragmentActivity activity, OnLangPairSelected onSelected) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = ResUtil.dp2px(24);
        layout.setPadding(pad, pad / 2, pad, pad);

        TextView srcLabel = new TextView(activity);
        srcLabel.setText("源语言");
        srcLabel.setTextSize(13);
        srcLabel.setPadding(0, 0, 0, ResUtil.dp2px(6));
        layout.addView(srcLabel);
        Spinner srcSpinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, AiSubtitleTranslator.LANG_NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        srcSpinner.setAdapter(adapter);
        // 默认源语言：English
        for (int i = 0; i < AiSubtitleTranslator.LANG_CODES.length; i++) {
            if (AiSubtitleTranslator.LANG_EN.equals(AiSubtitleTranslator.LANG_CODES[i])) {
                srcSpinner.setSelection(i);
                break;
            }
        }
        layout.addView(srcSpinner);

        TextView dstLabel = new TextView(activity);
        dstLabel.setText("目标语言");
        dstLabel.setTextSize(13);
        dstLabel.setPadding(0, ResUtil.dp2px(14), 0, ResUtil.dp2px(6));
        layout.addView(dstLabel);
        Spinner dstSpinner = new Spinner(activity);
        dstSpinner.setAdapter(adapter);
        // 默认目标语言：中文
        dstSpinner.setSelection(0);
        layout.addView(dstSpinner);

        new AlertDialog.Builder(activity)
                .setTitle("选择翻译语言")
                .setView(layout)
                .setNegativeButton("取消", null)
                .setPositiveButton("下一步", (dialog, which) -> {
                    String srcLang = AiSubtitleTranslator.LANG_CODES[srcSpinner.getSelectedItemPosition()];
                    String targetLang = AiSubtitleTranslator.LANG_CODES[dstSpinner.getSelectedItemPosition()];
                    if (srcLang.equals(targetLang)) {
                        Notify.show("源语言与目标语言相同，请重新选择");
                        return;
                    }
                    onSelected.onSelected(srcLang, targetLang);
                })
                .create()
                .show();
    }

    private interface OnLangPairSelected {
        void onSelected(String srcLang, String targetLang);
    }

    private void confirmTranslate(List<AiSubtitleTranslator.SubtitleLine> lines, File subFile, String srcLang, String targetLang) {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        int batches = AiSubtitleTranslator.getBatchCount(lines.size());
        String msg = "共 " + lines.size() + " 条字幕（" + AiSubtitleTranslator.langDisplayName(srcLang)
                + " → " + AiSubtitleTranslator.langDisplayName(targetLang) + "），将消耗约 " + batches + " 次 AI 调用，是否继续？";
        ChoiceDialog.showConfirm(activity.getSupportFragmentManager(), "AI 字幕翻译", msg, "开始翻译", () -> doTranslate(lines, subFile, srcLang, targetLang));
    }

    private void doTranslate(List<AiSubtitleTranslator.SubtitleLine> lines, File subFile, String srcLang, String targetLang) {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        dismissAllowingStateLoss();
        showTranslateProgress(lines.size());
        String tag = srcLang + "2" + targetLang;
        AiSubtitleTranslator.translate(activity, lines, srcLang, targetLang, new AiSubtitleTranslator.Callback() {
            @Override
            public void onProgress(int done, int total) {
                // TrackDialog 已关闭，用捕获的 activity 引用更新进度；
                // 宿主已销毁/正退出时不再摸它的视图（进度窗由生命周期观察者收）。
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    updateTranslateProgress(done, total);
                });
            }

            @Override
            public void onDone(List<AiSubtitleTranslator.SubtitleLine> translated) {
                activity.runOnUiThread(() -> {
                    dismissTranslateProgress();
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    applyTranslated(activity, translated, subFile, tag);
                });
            }

            @Override
            public void onError(String msg) {
                activity.runOnUiThread(() -> {
                    dismissTranslateProgress();
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    Notify.show("翻译失败：" + msg);
                });
            }
        });
    }

    private void applyTranslated(FragmentActivity activity, List<AiSubtitleTranslator.SubtitleLine> translated, File original, String tag) {
        String srt = AiSubtitleTranslator.toSrt(translated);
        File out = AiSubtitleTranslator.writeTranslatedSrt(activity, srt, tag);
        if (out == null) {
            Notify.show("字幕文件写入失败");
            return;
        }
        AiSubtitleTranslator.onTranslated(out, original);
        if (player != null) player.setSub(Sub.from(out.getAbsolutePath()));
        Notify.show("翻译完成，已替换字幕");
    }

    private void restoreOriginalSubtitle() {
        File original = AiSubtitleTranslator.getOriginalFile();
        if (original == null || !original.exists()) {
            Notify.show("原文文件不存在");
            return;
        }
        AiSubtitleTranslator.onRestored();
        if (player != null) player.setSub(Sub.from(original.getAbsolutePath()));
        Notify.show("已恢复原文");
        dismissAllowingStateLoss();
    }

    private void showTranslateProgress(int total) {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = ResUtil.dp2px(24);
        layout.setPadding(pad, pad / 2, pad, pad);
        translateProgressText = new TextView(activity);
        translateProgressText.setText("正在翻译 (0/" + total + ")…");
        translateProgressText.setTextSize(14);
        translateProgressText.setPadding(0, 0, 0, ResUtil.dp2px(12));
        layout.addView(translateProgressText);
        translateProgressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        translateProgressBar.setMax(total);
        translateProgressBar.setProgress(0);
        layout.addView(translateProgressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        translateProgressDialog = new AlertDialog.Builder(activity)
                .setTitle("AI 字幕翻译")
                .setView(layout)
                .setCancelable(false)
                .create();
        translateProgressDialog.show();
        translateHost = activity;
        activity.getLifecycle().addObserver(translateHostObserver);
    }

    private void updateTranslateProgress(int done, int total) {
        if (translateProgressBar != null) translateProgressBar.setProgress(done);
        if (translateProgressText != null) translateProgressText.setText("正在翻译 (" + done + "/" + total + ")…");
    }

    private void dismissTranslateProgress() {
        if (translateHost != null) {
            translateHost.getLifecycle().removeObserver(translateHostObserver);
            translateHost = null;
        }
        if (translateProgressDialog != null) {
            try {
                translateProgressDialog.dismiss();
            } catch (Exception ignored) {
            }
            translateProgressDialog = null;
        }
        translateProgressBar = null;
        translateProgressText = null;
    }

    private List<Track> getTrack() {
        List<Track> items = new ArrayList<>();
        addTrack(items);
        return items;
    }

    private void addTrack(List<Track> items) {
        List<Tracks.Group> groups = player.getCurrentTracks().getGroups();
        for (int i = 0; i < groups.size(); i++) {
            Tracks.Group trackGroup = groups.get(i);
            if (trackGroup.getType() != type) continue;
            for (int j = 0; j < trackGroup.length; j++) {
                Format format = trackGroup.getTrackFormat(j);
                // One subtitle cannot occupy both roles (the same rule is enforced by MPV).
                if (type == C.TRACK_TYPE_TEXT && (secondarySubtitle
                        ? player.isPrimarySubtitleSelected(format) : player.isSecondarySubtitleSelected(format))) continue;
                String name = provider.getTrackName(format);
                Log.d("TrackDialog", "track type=" + type + " id=" + format.id + " label=" + format.label + " lang=" + format.language + " codec=" + format.codecs + " mime=" + format.sampleMimeType + " name=" + name);
                // Keep the player's native track id with the visible item. Runtime track
                // switching must target this stable id directly; the formatted description
                // is only persisted for restoring a preference on the next playback.
                Track item = new Track(type, name, PlayerHelper.describeFormat(format)).playerId(format.id);
                item.setSelected(type == C.TRACK_TYPE_TEXT
                        ? secondarySubtitle ? player.isSecondarySubtitleSelected(format) : player.isPrimarySubtitleSelected(format)
                        : trackGroup.isTrackSelected(j));
                items.add(item);
            }
        }
    }

    @Override
    public void onItemClick(Track item) {
        if (secondarySubtitle) {
            player.setSecondarySubtitleTrack(item);
            dismiss();
            return;
        }
        player.setTrack(Arrays.asList(item.key(player.getKey()).save()));
        dismiss();
    }

    @Override
    protected boolean transparent() {
        return !Util.isLeanback();
    }

    @Override
    protected boolean stableOverlay() {
        return !Util.isLeanback();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        player.setSub(Sub.from(path));
        // 记录当前外挂字幕，供 AI 翻译使用
        if (path != null) AiSubtitleTranslator.setCurrentSubFile(new File(path));
        dismiss();
    });

    public interface Listener {

        void onSubtitleClick();
    }
}
