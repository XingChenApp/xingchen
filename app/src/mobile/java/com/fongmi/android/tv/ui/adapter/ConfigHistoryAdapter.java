package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.ItemConfigHistoryBinding;

import java.util.List;

public class ConfigHistoryAdapter extends RecyclerView.Adapter<ConfigHistoryAdapter.ViewHolder> {

    private final List<Config> items;
    private final OnItemClickListener listener;
    private String currentUrl;

    public interface OnItemClickListener {
        void onUse(Config config);
    }

    public ConfigHistoryAdapter(List<Config> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
        try {
            currentUrl = com.fongmi.android.tv.api.config.VodConfig.get().getConfig().getUrl();
        } catch (Exception e) {
            currentUrl = "";
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemConfigHistoryBinding binding = ItemConfigHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Config config = items.get(position);
        String name = config.getName();
        holder.binding.name.setText(name == null || name.isEmpty() ? "未命名" : name);
        holder.binding.url.setText(config.getUrl() == null ? "" : config.getUrl());
        // Highlight the currently active config
        boolean active = config.getUrl() != null && config.getUrl().equals(currentUrl);
        holder.binding.use.setText(active ? "使用中" : "使用");
        holder.binding.use.setEnabled(!active);
        holder.binding.use.setAlpha(active ? 0.5f : 1.0f);
        holder.binding.use.setOnClickListener(v -> {
            if (listener != null) listener.onUse(config);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public Config getItem(int position) {
        return items.get(position);
    }

    public void remove(int position) {
        items.remove(position);
        notifyItemRemoved(position);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemConfigHistoryBinding binding;
        ViewHolder(ItemConfigHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}