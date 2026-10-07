package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterCheckableBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CheckableAdapter extends RecyclerView.Adapter<CheckableAdapter.ViewHolder> {

    private final List<String> items;
    private final Set<Integer> selected = new HashSet<>();

    public CheckableAdapter(List<String> items) {
        this.items = items != null ? items : new ArrayList<>();
        // Select all by default
        for (int i = 0; i < this.items.size(); i++) selected.add(i);
    }

    public void selectAll(boolean select) {
        selected.clear();
        if (select) {
            for (int i = 0; i < items.size(); i++) selected.add(i);
        }
        notifyDataSetChanged();
    }

    public List<String> getSelected() {
        List<String> result = new ArrayList<>();
        for (int i : selected) result.add(items.get(i));
        return result;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterCheckableBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.checkbox.setText(items.get(position));
        holder.binding.checkbox.setChecked(selected.contains(position));
        holder.binding.checkbox.setOnCheckedChangeListener((v, checked) -> {
            if (checked) selected.add(holder.getAdapterPosition());
            else selected.remove(holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final AdapterCheckableBinding binding;
        ViewHolder(AdapterCheckableBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
