package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.AdapterEpisodeGridBinding;
import com.fongmi.android.tv.databinding.AdapterEpisodeHoriBinding;
import com.fongmi.android.tv.ui.base.BaseEpisodeHolder;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.holder.EpisodeGridHolder;
import com.fongmi.android.tv.ui.holder.EpisodeHoriHolder;
import com.fongmi.android.tv.utils.EpisodeTitleCompact;

import java.util.ArrayList;
import java.util.List;

public class EpisodeAdapter extends RecyclerView.Adapter<BaseEpisodeHolder> {

    private final OnClickListener listener;
    private final List<Episode> mItems;
    private final int viewType;
    private boolean compactEnabled = true;

    public EpisodeAdapter(OnClickListener listener, int viewType) {
        this(listener, viewType, new ArrayList<>());
    }

    public EpisodeAdapter(OnClickListener listener, int viewType, ArrayList<Episode> items) {
        this.listener = listener;
        this.viewType = viewType;
        this.mItems = items;
    }

    public void setCompactEnabled(boolean enabled) {
        this.compactEnabled = enabled;
    }

    public interface OnClickListener {

        void onItemClick(Episode item);

        /** 长按（默认空实现）。 */
        default void onItemLongClick(Episode item) {
        }
    }

    public void addAll(List<Episode> items) {
        if (compactEnabled) EpisodeTitleCompact.apply(items);
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    /**
     * 无选中不再回落第 1 集：与网格“无一集变金”（EpisodeGridHolder 以 isSelected 定金底）同一口径。
     * 无选中返回 -1，调用方（VideoActivity.getEpisode／分组定位／滚动定位）各自接住，见各处注释。
     */
    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return -1;
    }

    public int getPosition(Episode item) {
        return mItems.indexOf(item);
    }

    /** 无选中或空表返回 null，不再返回第 1 集。 */
    public Episode getActivated() {
        int position = getPosition();
        return position < 0 ? null : mItems.get(position);
    }

    /** 无选中或空表返回 null；有选中时末集仍钳位回自身（旧口径不变）。 */
    public Episode getNext() {
        int current = getPosition();
        if (current < 0 || getItemCount() == 0) return null;
        int max = getItemCount() - 1;
        current = ++current > max ? max : current;
        return mItems.get(current);
    }

    /** 无选中或空表返回 null；有选中时首集仍钳位回自身（旧口径不变）。 */
    public Episode getPrev() {
        int current = getPosition();
        if (current < 0 || getItemCount() == 0) return null;
        current = --current < 0 ? 0 : current;
        return mItems.get(current);
    }

    public List<Episode> getItems() {
        return mItems;
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        return viewType;
    }

    @Override
    public void onBindViewHolder(@NonNull BaseEpisodeHolder holder, int position) {
        holder.initView(mItems.get(position));
    }

    @NonNull
    @Override
    public BaseEpisodeHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ViewType.HORI) {
            return new EpisodeHoriHolder(AdapterEpisodeHoriBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener);
        } else {
            return new EpisodeGridHolder(AdapterEpisodeGridBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener);
        }
    }
}
