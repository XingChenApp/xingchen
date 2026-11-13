package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.DialogEpisodeGridBinding;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.fragment.EpisodeFragment;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class EpisodeGridDialog extends BaseBottomSheetDialog {

    private final List<String> titles;
    private DialogEpisodeGridBinding binding;
    private List<Episode> episodes;
    private boolean reverse;
    private boolean shortDrama;
    private int spanCount;
    private int itemCount;

    public EpisodeGridDialog() {
        this.titles = new ArrayList<>();
        this.spanCount = 5;
    }

    public static EpisodeGridDialog create() {
        return new EpisodeGridDialog();
    }

    public EpisodeGridDialog reverse(boolean reverse) {
        this.reverse = reverse;
        return this;
    }

    public EpisodeGridDialog episodes(List<Episode> episodes) {
        this.episodes = episodes;
        return this;
    }

    public EpisodeGridDialog shortDrama(boolean shortDrama) {
        this.shortDrama = shortDrama;
        return this;
    }

    public void show(FragmentActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || activity.getSupportFragmentManager().isStateSaved()) return;
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof EpisodeGridDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogEpisodeGridBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (shortDrama) {
            initShortDramaView();
        } else {
            // 非短剧模式口径 min(内容高, 半屏)：集数少时弹窗只随内容高，不留半屏空区；
            // 集数多时仍封顶半屏，不盖整屏。短剧分支不动（其列表靠 weight 撑满定高，
            // 根布局先放 wrap 会量出 0 高，min 口径不适用，仍用定高半屏）。
            setCappedContentHeight();
            setSpanCount();
            setTitles();
            setPager();
        }
    }

    /** 半屏高度：仅短剧分支用，与竖屏 EpisodeListDialog 的底部半屏同一口径。 */
    private void setHalfScreenHeight() {
        ViewGroup.LayoutParams params = binding.getRoot().getLayoutParams();
        if (params != null) {
            params.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.5);
            binding.getRoot().setLayoutParams(params);
        }
    }

    /**
     * 非短剧 min(内容高, 半屏)：根布局先放 wrap_content 随内容长，布局变化时
     * 一旦量出高度超过半屏就一次性收成半屏（之后高度参数不再是 wrap，不再反复触发）。
     * 监听留在根 view 上、随弹窗销毁而丢，不另记引用；分页面板是异步贴上的，
     * 贴上后布局变化同样走这道封顶，不会在集数多时先胀满屏。
     * 集数少时高度参数始终是 wrap，弹窗只占内容高，不留空区。
     */
    private void setCappedContentHeight() {
        View root = binding.getRoot();
        ViewGroup.LayoutParams params = root.getLayoutParams();
        if (params == null) return;
        int half = (int) (getResources().getDisplayMetrics().heightPixels * 0.5);
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        root.setLayoutParams(params);
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (binding == null || v != binding.getRoot()) return;
                ViewGroup.LayoutParams p = v.getLayoutParams();
                if (p == null || p.height != ViewGroup.LayoutParams.WRAP_CONTENT) return;
                int height = bottom - top;
                if (height > half) {
                    p.height = half;
                    v.setLayoutParams(p);
                }
            }
        });
    }

    private void initShortDramaView() {
        // 短剧模式：隐藏分页，显示直接滚动列表，半屏高度
        binding.tabs.setVisibility(View.GONE);
        binding.pager.setVisibility(View.GONE);
        binding.rvShortDrama.setVisibility(View.VISIBLE);
        binding.column.setVisibility(View.GONE);
        // 设置半屏高度
        setHalfScreenHeight();
        // 设置 RecyclerView
        binding.rvShortDrama.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(getContext(), 4));
        java.util.ArrayList<Episode> items = new java.util.ArrayList<>();
        if (episodes != null) {
            items.addAll(episodes);
            if (reverse) java.util.Collections.reverse(items);
        }
        EpisodeAdapter.OnClickListener listener = episode -> {
            if (requireActivity() instanceof EpisodeAdapter.OnClickListener) ((EpisodeAdapter.OnClickListener) requireActivity()).onItemClick(episode);
            dismiss();
        };
        EpisodeAdapter adapter = new EpisodeAdapter(listener, com.fongmi.android.tv.ui.base.ViewType.GRID, items);
        binding.rvShortDrama.setAdapter(adapter);
    }

    @Override
    protected void initEvent() {
        binding.column.setOnClickListener(this::onColumnToggle);
        getChildFragmentManager().setFragmentResultListener("result", this, (requestKey, bundle) -> {
            if (requireActivity() instanceof EpisodeAdapter.OnClickListener) ((EpisodeAdapter.OnClickListener) requireActivity()).onItemClick(bundle.getParcelable("episode"));
            dismiss();
        });
    }

    private void onColumnToggle(View view) {
        PlayerSetting.putEpisodeColumn(spanCount == 1 ? 2 : 1);
        setSpanCount();
        setTitles();
        setPager();
    }

    private void setSpanCount() {
        int avg = (int) Math.ceil(episodes.stream().mapToInt(e -> e.getName().length()).average().orElse(0));
        int max = episodes.stream().mapToInt(e -> e.getDesc().concat(e.getName()).length()).max().orElse(0);
        boolean longTitle = avg >= 8 || max >= 12;
        if (longTitle) spanCount = PlayerSetting.getEpisodeColumn();
        else if (avg >= 4) spanCount = 3;
        else if (avg >= 2) spanCount = 4;
        else spanCount = 5;
        itemCount = episodes.size() <= 60 ? 20 : spanCount * (ResUtil.isLand(requireActivity()) ? 5 : 10);
        binding.column.setVisibility(longTitle ? View.VISIBLE : View.GONE);
        binding.column.setImageResource(spanCount == 1 ? R.drawable.ic_site_double_column : R.drawable.ic_site_single_column);
    }

    private void setTitles() {
        titles.clear();
        if (reverse) for (int i = episodes.size(); i > 0; i -= itemCount) titles.add(i + " - " + Math.max(i - itemCount + 1, 1));
        else for (int i = 0; i < episodes.size(); i += itemCount) titles.add((i + 1) + " - " + Math.min(i + itemCount, episodes.size()));
    }

    private void setPager() {
        binding.tabs.removeAllTabs();
        binding.pager.setAdapter(new PageAdapter(this));
        new TabLayoutMediator(binding.tabs, binding.pager, (tab, position) -> tab.setText(titles.get(position))).attach();
        setCurrentPage();
    }

    private void setCurrentPage() {
        for (int i = 0; i < episodes.size(); i++) {
            if (episodes.get(i).isSelected()) {
                binding.pager.setCurrentItem(i / itemCount);
                break;
            }
        }
    }

    class PageAdapter extends FragmentStateAdapter {

        public PageAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return EpisodeFragment.newInstance(spanCount, episodes.subList(position * itemCount, Math.min(position * itemCount + itemCount, episodes.size())));
        }

        @Override
        public int getItemCount() {
            return titles.size();
        }
    }
}
