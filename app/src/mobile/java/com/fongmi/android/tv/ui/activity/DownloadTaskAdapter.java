package com.fongmi.android.tv.ui.activity;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.download.DownloadManager;
import com.fongmi.android.tv.utils.download.DownloadTask;

import java.io.File;
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
        // 点击暂停/继续/播放
        holder.itemView.setOnClickListener(v -> {
            if (task.getStatus() == DownloadTask.STATUS_DOWNLOADING || task.getStatus() == DownloadTask.STATUS_WAITING) {
                DownloadManager.get().pause(task.getId());
            } else if (task.getStatus() == DownloadTask.STATUS_PAUSED || task.getStatus() == DownloadTask.STATUS_ERROR) {
                DownloadManager.get().resume(task.getId());
            } else if (task.getStatus() == DownloadTask.STATUS_COMPLETED) {
                openCompletedFile(v, task);
            }
        });
        // 长按删除（先弹确认框）
        holder.itemView.setOnLongClickListener(v -> {
            showDeleteConfirm(v, task);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    /** 长按删除确认：白毛玻璃弹窗，确认后才删除任务及文件 */
    private void showDeleteConfirm(View view, DownloadTask task) {
        Context context = view.getContext();
        if (!(context instanceof FragmentActivity)) return;
        FragmentActivity activity = (FragmentActivity) context;
        if (activity.isFinishing()) return;
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_download_delete_confirm, null);
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(dialogView).create();
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            DownloadManager.get().delete(task.getId());
            Notify.show("已删除");
            dialog.dismiss();
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.88f);
            window.setAttributes(params);
        }
    }

    /** 已完成：用应用内播放器打开本地文件 */
    private void openCompletedFile(View view, DownloadTask task) {
        String filePath = task.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            Notify.show("文件路径为空");
            return;
        }
        File file = new File(filePath);
        if (!file.exists()) {
            Notify.show("文件不存在，可能已被删除");
            return;
        }
        Context context = view.getContext();
        if (context instanceof FragmentActivity) {
            VideoActivity.file((FragmentActivity) context, filePath);
        } else {
            Notify.show("无法打开播放器");
        }
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
