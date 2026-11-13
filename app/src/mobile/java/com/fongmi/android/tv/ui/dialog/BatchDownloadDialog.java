package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.DialogBatchDownloadBinding;
import com.fongmi.android.tv.databinding.ItemBatchDownloadBinding;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 批量下载勾选窗（点播详情页多集时点「下载」弹出）。
 * 本体浅面毛玻璃口径：做实浅底圆角卡＋深字；集条目与详情页选集网格同款格子
 * （activated＝品牌橙、未选近白，落在列表托底上），选中态只认勾选数组一处、格子与
 * 计数与全选钮全由它推导；无返回箭头，系统返回／点外关闭。打开瞬间快照当前线路
 * 选集，弹窗内不换线路、不切集。
 */
public class BatchDownloadDialog extends Dialog {

    public interface OnConfirmListener {
        void onConfirm(List<Episode> selected);
    }

    private final List<Episode> episodes;
    private final String vodName;
    private final OnConfirmListener listener;
    private DialogBatchDownloadBinding binding;
    private BatchAdapter adapter;
    private boolean syncingCheckAll;
    private ViewTreeObserver.OnGlobalLayoutListener sizeWatcher;
    private int pinnedWidth = -1;
    private int pinnedHeight = -1;

    public BatchDownloadDialog(@NonNull Context context, String vodName, List<Episode> episodes, OnConfirmListener listener) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        this.vodName = vodName == null ? "" : vodName;
        this.episodes = new ArrayList<>(episodes);
        this.listener = listener;
        setCanceledOnTouchOutside(true);
        setCancelable(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DialogBatchDownloadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // 进出统一口径：淡入淡出 200ms（与 BaseAlertDialog 基类同一套 HkDialogFade）
            window.setWindowAnimations(R.style.HkDialogFade);
        }
        binding.subtitle.setText(vodName);
        adapter = new BatchAdapter();
        // 与详情页选集网格同一套网格口径：列数照 EpisodeGridLayoutPolicy 的值（竖屏 4、
        // 横屏/平板 6，该类是 activity 包私有、这里照值复刻）、间距 8dp 同 SpaceItemDecoration 口径
        boolean land = getContext().getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int span = land || ResUtil.isPad() ? 6 : 4;
        binding.recycler.setLayoutManager(new GridLayoutManager(getContext(), span));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(span, 8));
        binding.recycler.setAdapter(adapter);
        binding.checkAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (syncingCheckAll) return;
            adapter.setAll(isChecked);
            refreshState();
        });
        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnStart.setOnClickListener(v -> {
            List<Episode> selected = adapter.getSelected();
            if (listener != null) listener.onConfirm(selected);
            dismiss();
        });
        refreshState();
        pinWindow();
        // 转屏时页面不重建、弹窗尺寸不会自己跟：盯住布局，屏幕宽高一变就按新值复钉一遍
        sizeWatcher = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (binding == null) return;
                android.util.DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
                if (dm.widthPixels != pinnedWidth || dm.heightPixels != pinnedHeight) pinWindow();
            }
        };
        binding.getRoot().getViewTreeObserver().addOnGlobalLayoutListener(sizeWatcher);
    }

    /**
     * 摆位：宽＝屏宽减两侧各 24dp；高按内容实测，但列表封顶（屏高 45%）在测量钉位之前先生效——
     * 先把列表放回自适应测一遍，超封顶就把定高设进列表布局参数再测一遍，钉下去的窗口高恒等于
     * 卡片真实高，不留透明死区。转屏复钉走同一口径，封顶随新屏高重算。
     */
    private void pinWindow() {
        Window window = getWindow();
        if (window == null || binding == null) return;
        android.util.DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        int width = Math.max(0, dm.widthPixels - Math.round(48 * dm.density));
        int maxListHeight = Math.round(ResUtil.getScreenHeight(getContext()) * 0.45f);
        ViewGroup.LayoutParams listLp = binding.recycler.getLayoutParams();
        if (listLp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            listLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            binding.recycler.setLayoutParams(listLp);
        }
        View root = binding.getRoot();
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.AT_MOST));
        if (binding.recycler.getMeasuredHeight() > maxListHeight) {
            listLp.height = maxListHeight;
            binding.recycler.setLayoutParams(listLp);
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.AT_MOST));
        }
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = width;
        lp.height = root.getMeasuredHeight();
        lp.gravity = Gravity.CENTER;
        window.setAttributes(lp);
        pinnedWidth = dm.widthPixels;
        pinnedHeight = dm.heightPixels;
    }

    private void refreshState() {
        if (binding == null || adapter == null) return;
        int count = adapter.selectedCount();
        binding.selectedCount.setText("已选 " + count + " / " + episodes.size() + " 集");
        // 全选钮自己的态也只从这份计数推：满选＝勾亮＋文案「取消全选」，未满（含部分选）
        // ＝不勾＋文案「全选」——和格子亮不亮、计数多少同源同帧，不各走各的
        boolean all = count == episodes.size() && count > 0;
        syncingCheckAll = true;
        binding.checkAll.setChecked(all);
        binding.checkAll.setText(all ? "取消全选" : "全选");
        syncingCheckAll = false;
        binding.btnStart.setEnabled(count > 0);
        binding.btnStart.setAlpha(count > 0 ? 1f : 0.45f);
    }

    @Override
    protected void onStop() {
        if (binding != null && sizeWatcher != null) {
            binding.getRoot().getViewTreeObserver().removeOnGlobalLayoutListener(sizeWatcher);
        }
        sizeWatcher = null;
        binding = null;
        super.onStop();
    }

    private class BatchAdapter extends RecyclerView.Adapter<BatchAdapter.Holder> {

        private final boolean[] checked = new boolean[episodes.size()];

        BatchAdapter() {
            // 缺省勾上当前在播集，进窗即可直接补下这一集
            Episode current = null;
            for (Episode episode : episodes) if (episode != null && episode.isSelected()) current = episode;
            if (current != null) checked[episodes.indexOf(current)] = true;
        }

        void setAll(boolean value) {
            java.util.Arrays.fill(checked, value);
            notifyDataSetChanged();
        }

        int selectedCount() {
            int count = 0;
            for (boolean b : checked) if (b) count++;
            return count;
        }

        List<Episode> getSelected() {
            Set<Episode> picked = new LinkedHashSet<>();
            for (int i = 0; i < episodes.size(); i++) if (checked[i]) picked.add(episodes.get(i));
            return new ArrayList<>(picked);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemBatchDownloadBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Episode episode = episodes.get(position);
            holder.binding.name.setText(episode == null ? "" : episode.getDisplayName());
            // 格子选中态只从勾选数组推导（activated 驱动 shape_detail_episode 的品牌橙底），
            // 每次绑定都重推——滚动复用不串态
            holder.binding.name.setActivated(checked[position]);
            holder.binding.name.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                checked[pos] = !checked[pos];
                notifyItemChanged(pos);
                refreshState();
            });
        }

        @Override
        public int getItemCount() {
            return episodes.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemBatchDownloadBinding binding;

            Holder(ItemBatchDownloadBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
