package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Plugin;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PluginAdapter extends RecyclerView.Adapter<PluginAdapter.ViewHolder> {
    private final List<Plugin> plugins;
    private final Runnable onChanged;

    public PluginAdapter(List<Plugin> plugins, Runnable onChanged) {
        this.plugins = plugins;
        this.onChanged = onChanged;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plugin, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Plugin p = plugins.get(position);
        holder.tvName.setText(p.getName());
        holder.tvVersion.setText(p.isEnabled() ? "已启用" : "已停用");
        holder.cbSelect.setChecked(p.isSelected());
        holder.switchEnable.setChecked(p.isEnabled());
        
        holder.cbSelect.setOnCheckedChangeListener((btn, checked) -> {
            p.setSelected(checked);
        });
        
        holder.switchEnable.setOnCheckedChangeListener((btn, checked) -> {
            p.setEnabled(checked);
            holder.tvVersion.setText(checked ? "已启用" : "已停用");
        });
        
        holder.tvDelete.setOnClickListener(v -> {
            new File(p.getPath()).delete();
            plugins.remove(position);
            notifyItemRemoved(position);
            if (onChanged != null) onChanged.run();
        });
    }

    @Override
    public int getItemCount() {
        return plugins.size();
    }

    public void selectAll(boolean checked) {
        for (Plugin p : plugins) p.setSelected(checked);
        notifyDataSetChanged();
    }

    public List<Plugin> getSelected() {
        List<Plugin> result = new ArrayList<>();
        for (Plugin p : plugins) if (p.isSelected()) result.add(p);
        return result;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbSelect;
        TextView tvName, tvVersion, tvDelete;
        Switch switchEnable;
        ViewHolder(View itemView) {
            super(itemView);
            cbSelect = itemView.findViewById(R.id.cb_select);
            tvName = itemView.findViewById(R.id.tv_name);
            tvVersion = itemView.findViewById(R.id.tv_version);
            tvDelete = itemView.findViewById(R.id.tv_delete);
            switchEnable = itemView.findViewById(R.id.switch_enable);
        }
    }
}