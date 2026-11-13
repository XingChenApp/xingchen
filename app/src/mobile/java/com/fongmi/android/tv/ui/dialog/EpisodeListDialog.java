package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.databinding.DialogEpisodeListBinding;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeGroupAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.EpisodeTitleCompact;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.XcFadeTransition;

import java.util.ArrayList;
import java.util.List;

public class EpisodeListDialog extends AppCompatDialogFragment implements FlagAdapter.OnClickListener, EpisodeGroupAdapter.OnClickListener, EpisodeAdapter.OnClickListener {

    private DialogEpisodeListBinding binding;
    private EpisodeGroupAdapter groupAdapter;
    private EpisodeAdapter episodeAdapter;
    private SpaceItemDecoration episodeDecoration;
    private FlagAdapter flagAdapter;
    private List<Flag> flags;
    private int episodeSpanCount = 4;
    private boolean reverse;
    private boolean shortDrama;
    private boolean exiting;

    public static EpisodeListDialog create() {
        return new EpisodeListDialog();
    }

    public EpisodeListDialog shortDrama(boolean shortDrama) {
        this.shortDrama = shortDrama;
        args().putBoolean("shortDrama", shortDrama);
        return this;
    }

    public EpisodeListDialog flags(List<Flag> flags) {
        this.flags = flags;
        // 线路表走 arguments 存一份：系统重建弹窗时实例字段会丢，arguments 能原样恢复（Flag 是 Parcelable）。
        args().putParcelableArrayList("flags", flags == null ? null : new ArrayList<>(flags));
        return this;
    }

    public EpisodeListDialog reverse(boolean reverse) {
        this.reverse = reverse;
        args().putBoolean("reverse", reverse);
        return this;
    }

    private Bundle args() {
        Bundle args = getArguments();
        if (args == null) {
            args = new Bundle();
            setArguments(args);
        }
        return args;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            if (args.containsKey("flags")) flags = args.getParcelableArrayList("flags");
            reverse = args.getBoolean("reverse", reverse);
            shortDrama = args.getBoolean("shortDrama", shortDrama);
        }
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof EpisodeListDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        configureWindow(dialog);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow(getDialog());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogEpisodeListBinding.inflate(inflater, container, false);
        FrameLayout overlay = new FrameLayout(requireContext());
        overlay.setBackgroundColor(Color.TRANSPARENT);
        overlay.setOnClickListener(view -> dismiss());
        binding.getRoot().setClickable(true);
        overlay.addView(binding.getRoot(), getPanelParams());
        return overlay;
    }

    // 面板形态只认实际方向：竖屏底部半屏抽屉（右侧整高面板在手机竖屏下宽 360dp、占屏九成，
    // 会直接盖住播放画面），横屏右侧整高面板。短剧标记只影响分组行去留（内容差异），
    // 不许再参与形态判定——否则竖屏短剧转横屏会把底部抽屉带进横屏（1.4.12 真机实证）。
    private FrameLayout.LayoutParams getPanelParams() {
        if (!ResUtil.isLand(requireContext())) {
            return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.getScreenHeight(requireContext()) / 2, Gravity.BOTTOM);
        }
        return new FrameLayout.LayoutParams(getWidth(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (binding == null) return;
        // 弹窗开着时旋转：宿主以 configChanges 自行处理方向、不重建，形态参数不会自动重算，
        // 必须按新方向当场重套，否则先开窗再旋转同样会把旧方向的形态留住。
        if (binding.getRoot().getParent() instanceof FrameLayout) binding.getRoot().setLayoutParams(getPanelParams());
        if (episodeAdapter != null) updateEpisodeSpan(episodeAdapter.getItems());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView();
        animatePanelIn();
    }

    // 进出过渡（统一 200ms）：窗是全屏透明层，动画落面板本体——竖屏自下而上、横屏自右而入，均带淡入。
    private void animatePanelIn() {
        View panel = binding != null ? binding.getRoot() : null;
        if (panel == null) return;
        panel.post(() -> {
            if (!isAdded() || exiting || !XcFadeTransition.isAnimEnabled(requireContext())) return;
            panel.setAlpha(0f);
            if (ResUtil.isLand(requireContext())) panel.setTranslationX(panel.getWidth());
            else panel.setTranslationY(panel.getHeight());
            panel.animate().alpha(1f).translationX(0f).translationY(0f)
                    .setDuration(XcFadeTransition.DURATION_MS)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        });
    }

    @Override
    public void dismiss() {
        View panel = binding != null ? binding.getRoot() : null;
        if (exiting || panel == null || !isAdded() || !XcFadeTransition.isAnimEnabled(requireContext())) {
            super.dismiss();
            return;
        }
        exiting = true;
        android.view.ViewPropertyAnimator animator = panel.animate().alpha(0f)
                .setDuration(XcFadeTransition.DURATION_MS)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    try {
                        super.dismiss();
                    } catch (Throwable ignored) {
                    }
                });
        if (ResUtil.isLand(requireContext())) animator.translationX(panel.getWidth());
        else animator.translationY(panel.getHeight());
        animator.start();
    }

    private int getWidth() {
        int screen = ResUtil.getScreenWidth(requireContext());
        return Math.max(ResUtil.dp2px(360), Math.min(ResUtil.dp2px(560), Math.round(screen * 0.44f)));
    }

    private void configureWindow(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setDimAmount(0f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        Util.hideSystemUI(window);
    }

    private void initView() {
        setRecyclerView();
        flagAdapter.addAll(flags == null ? new ArrayList<>() : flags);
        setGroups(getSelectedFlag());
        binding.flag.scrollToPosition(flagAdapter.getPosition());
    }

    private void setRecyclerView() {
        binding.flag.setHasFixedSize(true);
        binding.flag.setItemAnimator(null);
        binding.flag.setAdapter(flagAdapter = new FlagAdapter(this));
        if (shortDrama) {
            ViewGroup parent = (ViewGroup) binding.group.getParent();
            if (parent != null) parent.removeView(binding.group);
        } else {
            binding.group.setHasFixedSize(true);
            binding.group.setItemAnimator(null);
            binding.group.setAdapter(groupAdapter = new EpisodeGroupAdapter(this));
        }
        binding.episode.setHasFixedSize(true);
        binding.episode.setItemAnimator(null);
        binding.episode.setLayoutManager(new GridLayoutManager(requireContext(), episodeSpanCount));
        binding.episode.addItemDecoration(episodeDecoration = new SpaceItemDecoration(episodeSpanCount, 8));
        binding.episode.setAdapter(episodeAdapter = new EpisodeAdapter(this, ViewType.GRID));
        binding.episode.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                syncEpisodeGroupByScroll();
            }
        });
    }

    private Flag getSelectedFlag() {
        if (flagAdapter.isEmpty()) return null;
        return flagAdapter.get(flagAdapter.getPosition());
    }

    private void setGroups(Flag flag) {
        if (flag == null) return;
        List<Episode> episodes = flag.getEpisodes();
        if (groupAdapter == null) {
            setEpisodes(episodes);
            int position = episodeAdapter.getPosition();
            if (position >= 0) binding.episode.scrollToPosition(position);
            return;
        }
        groupAdapter.addAll(EpisodeGroupAdapter.build(episodes.size(), getSelectedEpisodePosition(episodes), reverse));
        setEpisodes(episodes);
        binding.group.scrollToPosition(groupAdapter.getPosition());
        int position = episodeAdapter.getPosition();
        if (position >= 0) binding.episode.scrollToPosition(position);
    }

    private void setEpisodes(List<Episode> episodes) {
        updateEpisodeSpan(episodes);
        episodeAdapter.addAll(episodes);
        int position = episodeAdapter.getPosition();
        if (position >= 0) selectEpisodeGroupByPosition(position);
    }

    private void updateEpisodeSpan(List<Episode> episodes) {
        int span = getEpisodeSpan(episodes);
        if (span == episodeSpanCount) return;
        episodeSpanCount = span;
        binding.episode.setLayoutManager(new GridLayoutManager(requireContext(), episodeSpanCount));
        if (episodeDecoration != null) binding.episode.removeItemDecoration(episodeDecoration);
        binding.episode.addItemDecoration(episodeDecoration = new SpaceItemDecoration(episodeSpanCount, 8));
    }

    private int getEpisodeSpan(List<Episode> episodes) {
        EpisodeTitleCompact.apply(episodes);
        int maxLen = 0;
        for (Episode item : episodes) maxLen = Math.max(maxLen, item.getDisplayName().length());
        if (maxLen >= 12) return PlayerSetting.getEpisodeColumn();
        int ideal = maxLen >= 10 ? 130 : maxLen >= 7 ? 104 : 80;
        int available = Math.max(ResUtil.dp2px(240), getPanelContentWidth() - ResUtil.dp2px(28));
        int span = available / ResUtil.dp2px(ideal);
        return Math.max(2, Math.min(4, span));
    }

    // 列数按当前形态的真实面板宽算：横屏右侧面板宽走 getWidth() 口径，
    // 竖屏底部抽屉是整屏宽——此前一律拿横屏面板宽算，竖屏（尤其宽屏/折叠屏）会少数列。
    private int getPanelContentWidth() {
        return ResUtil.isLand(requireContext()) ? getWidth() : ResUtil.getScreenWidth(requireContext());
    }

    private int getSelectedEpisodePosition(List<Episode> episodes) {
        for (int i = 0; i < episodes.size(); i++) if (episodes.get(i).isSelected()) return i;
        return 0;
    }

    @Override
    public void onItemClick(Flag item) {
        if (requireActivity() instanceof FlagAdapter.OnClickListener) ((FlagAdapter.OnClickListener) requireActivity()).onItemClick(item);
        flagAdapter.notifyItemRangeChanged(0, flagAdapter.getItemCount());
        setGroups(item);
    }

    @Override
    public void onItemClick(EpisodeGroupAdapter.Group item) {
        groupAdapter.setSelected(item);
        scrollEpisodeToPosition(item.start);
        binding.group.scrollToPosition(groupAdapter.getPosition());
    }

    private void syncEpisodeGroupByScroll() {
        RecyclerView.LayoutManager manager = binding.episode.getLayoutManager();
        if (!(manager instanceof GridLayoutManager)) return;
        int position = getEpisodeGroupSyncPosition((GridLayoutManager) manager);
        if (position == RecyclerView.NO_POSITION) return;
        selectEpisodeGroupByPosition(position);
    }

    private int getEpisodeGroupSyncPosition(GridLayoutManager manager) {
        if (!binding.episode.canScrollVertically(1) && binding.episode.canScrollVertically(-1)) {
            return manager.findLastVisibleItemPosition();
        }
        return manager.findFirstVisibleItemPosition();
    }

    private void selectEpisodeGroupByPosition(int position) {
        if (groupAdapter == null || groupAdapter.isEmpty()) return;
        int current = groupAdapter.getPosition();
        List<EpisodeGroupAdapter.Group> groups = groupAdapter.getItems();
        for (int i = 0; i < groups.size(); i++) {
            EpisodeGroupAdapter.Group group = groups.get(i);
            if (position < group.start || position >= group.end) continue;
            if (i != current) {
                groupAdapter.setSelected(group);
                binding.group.scrollToPosition(i);
            }
            return;
        }
    }

    private void scrollEpisodeToPosition(int position) {
        RecyclerView.LayoutManager manager = binding.episode.getLayoutManager();
        if (manager instanceof GridLayoutManager) ((GridLayoutManager) manager).scrollToPositionWithOffset(position, 0);
        else binding.episode.scrollToPosition(position);
    }

    @Override
    public void onItemClick(Episode item) {
        if (requireActivity() instanceof EpisodeAdapter.OnClickListener) ((EpisodeAdapter.OnClickListener) requireActivity()).onItemClick(item);
        dismiss();
    }
}
