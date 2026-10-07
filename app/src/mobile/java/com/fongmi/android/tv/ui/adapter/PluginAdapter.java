package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Plugin;

import java.util.ArrayList;
import java.util.List;

public class PluginAdapter extends RecyclerView.Adapter<PluginAdapter.ViewHolder> {

    private List<Plugin> plugins = new ArrayList<>();
    private OnPluginListener listener;

    public interface OnPluginListener {
        void onToggle(Plugin plugin, boolean enabled);
        void onDelete(Plugin plugin);
        void onSelectChanged();
    }

    public void setListener(OnPluginListener listener) {
        this.listener = listener;
    }

    public void setPlugins(List<Plugin> plugins) {
        this.plugins = plugins != null ? plugins : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<Plugin> getPlugins() {
        return plugins;
    }

    public List<Plugin> getSelected() {
        List<Plugin> selected = new ArrayList<>();
        for (Plugin p : plugins) {
            if (p.isSelected()) selected.add(p);
        }
        return selected;
    }

    public void selectAll(boolean select) {
        for (Plugin p : plugins) p.setSelected(select);
        notifyDataSetChanged();
        if (listener != null) listener.onSelectChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plugin, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Plugin plugin = plugins.get(position);
        holder.tvName.setText(plugin.getName());
        holder.tvPath.setText(plugin.getPath());
        holder.cbSelect.setChecked(plugin.isSelected());
        holder.swEnable.setChecked(plugin.isEnabled());

        holder.cbSelect.setOnCheckedChangeListener((v, checked) -> {
            plugin.setSelected(checked);
            if (listener != null) listener.onSelectChanged();
        });

        holder.swEnable.setOnCheckedChangeListener((v, checked) -> {
            plugin.setEnabled(checked);
            if (listener != null) listener.onToggle(plugin, checked);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(plugin);
        });
    }

    @Override
    public int getItemCount() {
        return plugins.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbSelect;
        TextView tvName, tvPath;
        Switch swEnable;
        ImageButton btnDelete;

        ViewHolder(View view) {
            super(view);
            cbSelect = view.findViewById(R.id.cbSelect);
            tvName = view.findViewById(R.id.tvName);
            tvPath = view.findViewById(R.id.tvPath);
            swEnable = view.findViewById(R.id.swEnable);
            btnDelete = view.findViewById(R.id.btnDelete);
        }
    }
}
