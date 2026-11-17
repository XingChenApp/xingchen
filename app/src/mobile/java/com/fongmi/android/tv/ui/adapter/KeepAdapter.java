package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.AdapterVodBinding;
import com.fongmi.android.tv.utils.HistoryProgressFormatter;
import com.fongmi.android.tv.utils.ImgUtil;

public class KeepAdapter extends BaseDiffAdapter<Keep, KeepAdapter.ViewHolder> {

    private final OnClickListener listener;
    private int width, height;
    private boolean delete;

    public KeepAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(Keep item);

        void onItemDelete(Keep item);

        boolean onLongClick();
    }

    public void setSize(int[] size) {
        this.width = size[0];
        this.height = size[1];
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void clear() {
        super.clear();
        setDelete(false);
        Keep.deleteAll();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(AdapterVodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.image.getLayoutParams().height = height;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Keep item = getItem(position);
        // 集/进度副行（K4-2）：与历史格同形绑定——数据由 KeepActivity 按 History 行补齐到条目上，
        // 没看过的收藏（无补齐数据）副行与进度条自然不露；删除态同历史格一并藏副行
        boolean same = TextUtils.equals(item.getVodName(), item.getVodRemarks());
        holder.binding.name.setText(item.getVodName());
        holder.binding.remark.setText(item.getVodRemarks());
        holder.binding.remark.setVisibility(delete || same || TextUtils.isEmpty(item.getVodRemarks()) ? View.GONE : View.VISIBLE);
        holder.binding.site.setVisibility(View.VISIBLE);
        holder.binding.site.setText(item.getSiteName());
        String watchedTime = HistoryProgressFormatter.format(item.getPosition(), item.getDuration());
        holder.binding.historyProgress.setText(watchedTime.isEmpty() ? "" : holder.itemView.getContext().getString(R.string.history_watched_time, watchedTime));
        holder.binding.historyProgress.setVisibility(delete || watchedTime.isEmpty() ? View.GONE : View.VISIBLE);
        int duration = (int) Math.min(Integer.MAX_VALUE, Math.max(0, item.getDuration()));
        int progress = (int) Math.min(Integer.MAX_VALUE, Math.max(0, item.getPosition()));
        holder.binding.progress.setMax(duration > 0 ? duration : 1);
        holder.binding.progress.setProgress(duration > 0 ? Math.min(progress, duration) : 0);
        holder.binding.progress.setVisibility(duration > 0 ? View.VISIBLE : View.GONE);
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
        setClickListener(holder.binding.getRoot(), item);
    }

    private void setClickListener(View root, Keep item) {
        root.setOnLongClickListener(view -> listener.onLongClick());
        root.setOnClickListener(view -> {
            if (isDelete()) listener.onItemDelete(item);
            else listener.onItemClick(item);
        });
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterVodBinding binding;

        ViewHolder(@NonNull AdapterVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
