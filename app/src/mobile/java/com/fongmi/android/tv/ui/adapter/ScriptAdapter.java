package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.R;
import java.io.File;
import java.util.List;

public class ScriptAdapter extends RecyclerView.Adapter<ScriptAdapter.ViewHolder> {
    private final List<File> files;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(File file);
    }

    public ScriptAdapter(List<File> files, OnItemClickListener listener) {
        this.files = files;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_script, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File f = files.get(position);
        holder.name.setText(f.getName());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(f);
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_script_name);
        }
    }
}