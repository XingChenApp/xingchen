package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.AdapterSourceSelectBinding;

import java.util.List;

public class SourceSelectAdapter extends RecyclerView.Adapter<SourceSelectAdapter.ViewHolder> {

    private List<Site> sites;
    private String currentKey;
    private OnItemClickListener listener;
    private OnItemLongClickListener longClickListener;

    public SourceSelectAdapter(List<Site> sites, String currentKey, OnItemClickListener listener) {
        this.sites = sites;
        this.currentKey = currentKey;
        this.listener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener l) {
        this.longClickListener = l;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AdapterSourceSelectBinding binding = AdapterSourceSelectBinding.inflate(
            LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Site site = sites.get(position);
        holder.binding.name.setText(site.getName());
        boolean isCurrent = site.getKey() != null && site.getKey().equals(currentKey);
        holder.binding.badge.setVisibility(View.GONE);
        if (isCurrent) {
            holder.binding.getRoot().setBackgroundResource(R.drawable.xc_capsule_orange);
        } else {
            holder.binding.getRoot().setBackgroundResource(R.drawable.xc_capsule_light);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(site);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(site);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return sites.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        AdapterSourceSelectBinding binding;
        ViewHolder(AdapterSourceSelectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public interface OnItemClickListener {
        void onClick(Site site);
    }

    public interface OnItemLongClickListener {
        void onLongClick(Site site);
    }
}
