package com.fongmi.android.tv.ui.activity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.download.DownloadManager;
import com.fongmi.android.tv.utils.download.DownloadTask;

import java.util.ArrayList;
import java.util.List;

/**
 * 下载任务列表适配器
 */
public class DownloadTaskAdapter extends RecyclerView.Adapter<DownloadTaskAdapter.Holder> {

    private final List<DownloadTask> tasks = new ArrayList<>();

    public void setTasks(List<DownloadTask> list) {
        tasks.clear();
        if (list != null) tasks.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download_task, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DownloadTask task = tasks.get(position);
        holder.tvName.setText(task.getName());
        holder.tvStatus.setText(task.getStatusText());
        holder.progress.setProgress(task.getProgress());
        holder.progress.setVisibility(task.getStatus() == DownloadTask.STATUS_DOWNLOADING ? View.VISIBLE : View.GONE);
        // 点击暂停/继续
        holder.itemView.setOnClickListener(v -> {
            if (task.getStatus() == DownloadTask.STATUS_DOWNLOADING || task.getStatus() == DownloadTask.STATUS_WAITING) {
                DownloadManager.get().pause(task.getId());
            } else if (task.getStatus() == DownloadTask.STATUS_PAUSED || task.getStatus() == DownloadTask.STATUS_ERROR) {
                DownloadManager.get().resume(task.getId());
            }
        });
        // 长按删除
        holder.itemView.setOnLongClickListener(v -> {
            DownloadManager.get().delete(task.getId());
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvStatus;
        ProgressBar progress;

        Holder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_task_name);
            tvStatus = itemView.findViewById(R.id.tv_task_status);
            progress = itemView.findViewById(R.id.pb_task_progress);
        }
    }
}
