package com.fongmi.android.tv.ui.activity;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.download.DownloadManager;
import com.fongmi.android.tv.utils.download.DownloadTask;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 下载任务列表适配器（件 8 行定版）：小封面＋状态只出一处＋数据行
 * （含速度/ETA）＋3dp 进度＋暂停/继续明示钮＋行尾小删除钮；勾选框
 * 只在管理模式出现。性能护栏：高频进度回调下同任务再绑只刷文字与
 * 进度条，跳过封面重载与勾选监听重绑，封面零闪烁。
 */
public class DownloadTaskAdapter extends RecyclerView.Adapter<DownloadTaskAdapter.Holder> {

    private final List<DownloadTask> tasks = new ArrayList<>();
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private OnSelectionChangeListener selectionListener;
    /** 管理模式：平时行上无勾选框，点顶部「全选」进入后才出行首勾选框 */
    private boolean manageMode;

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int selectedCount, int totalCount);
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener l) {
        selectionListener = l;
    }

    public void setManageMode(boolean manage) {
        if (manageMode == manage) return;
        manageMode = manage;
        if (!manage) selectedIds.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public boolean isManageMode() {
        return manageMode;
    }

    public void setTasks(List<DownloadTask> list) {
        tasks.clear();
        if (list != null) tasks.addAll(list);
        // 清理已不存在任务的选中态
        Set<String> alive = new LinkedHashSet<>();
        for (DownloadTask t : tasks) alive.add(t.getId());
        selectedIds.retainAll(alive);
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    /** 全选 */
    public void selectAll() {
        selectedIds.clear();
        for (DownloadTask t : tasks) selectedIds.add(t.getId());
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    /** 清空选中 */
    public void clearSelection() {
        if (selectedIds.isEmpty()) return;
        selectedIds.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public Set<String> getSelectedIds() {
        return new LinkedHashSet<>(selectedIds);
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) selectionListener.onSelectionChanged(selectedIds.size(), tasks.size());
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
        // 管理模式翻转也算换绑（勾选框显隐在全绑段里，不翻会留旧显隐）
        boolean sameTask = task.getId().equals(holder.boundTaskId)
                && holder.boundManage == (manageMode ? 1 : 0);
        if (!sameTask) {
            // 全绑：封面走 ImgUtil 统一口——pic 空/加载中/失败三态都落文字封面
            holder.boundTaskId = task.getId();
            holder.boundManage = manageMode ? 1 : 0;
            holder.tvName.setText(task.getName());
            ImgUtil.load(task.getName(), task.getPic(), holder.cover);
            holder.cbSelect.setVisibility(manageMode ? View.VISIBLE : View.GONE);
            holder.cbSelect.setOnCheckedChangeListener(null);
            holder.cbSelect.setChecked(selectedIds.contains(task.getId()));
            holder.cbSelect.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) selectedIds.add(task.getId());
                else selectedIds.remove(task.getId());
                notifySelectionChanged();
            });
            holder.itemView.setOnClickListener(v -> onRowClick(v, task));
            holder.itemView.setOnLongClickListener(v -> {
                showDeleteConfirm(v, task);
                return true;
            });
            holder.btnToggle.setOnClickListener(v -> onToggle(task));
            holder.btnDelete.setOnClickListener(v -> showDeleteConfirm(v, task));
        }
        // 同任务再绑与全绑共用：只刷状态/数据/进度/钮四处（封面零闪烁）
        holder.tvStatus.setText(task.getStatusText());
        String sizeText = task.getSizeText();
        holder.tvSize.setText(sizeText);
        holder.tvSize.setVisibility(sizeText.isEmpty() ? View.GONE : View.VISIBLE);
        int status = task.getStatus();
        holder.progress.setProgress(task.getProgress());
        holder.progress.setVisibility(
                status == DownloadTask.STATUS_DOWNLOADING || status == DownloadTask.STATUS_MERGING
                        || status == DownloadTask.STATUS_WAITING || status == DownloadTask.STATUS_PAUSED
                        ? View.VISIBLE : View.GONE);
        if (status == DownloadTask.STATUS_DOWNLOADING || status == DownloadTask.STATUS_WAITING) {
            holder.btnToggle.setVisibility(View.VISIBLE);
            holder.btnToggle.setImageResource(R.drawable.ic_music_pause);
            holder.btnToggle.setContentDescription("暂停下载");
        } else if (status == DownloadTask.STATUS_PAUSED || status == DownloadTask.STATUS_ERROR) {
            holder.btnToggle.setVisibility(View.VISIBLE);
            holder.btnToggle.setImageResource(R.drawable.ic_music_play);
            holder.btnToggle.setContentDescription("继续下载");
        } else {
            holder.btnToggle.setVisibility(View.GONE);
        }
    }

    /** 行点击＝钮点击同口：下载中/等待暂停、暂停/出错继续、已完成打开播放 */
    private void onRowClick(View view, DownloadTask task) {
        if (manageMode) return;
        switch (task.getStatus()) {
            case DownloadTask.STATUS_DOWNLOADING:
            case DownloadTask.STATUS_WAITING:
                DownloadManager.get().pause(task.getId());
                break;
            case DownloadTask.STATUS_PAUSED:
            case DownloadTask.STATUS_ERROR:
                DownloadManager.get().resume(task.getId());
                break;
            case DownloadTask.STATUS_COMPLETED:
                openCompletedFile(view, task);
                break;
            default:
                break;
        }
    }

    private void onToggle(DownloadTask task) {
        switch (task.getStatus()) {
            case DownloadTask.STATUS_DOWNLOADING:
            case DownloadTask.STATUS_WAITING:
                DownloadManager.get().pause(task.getId());
                break;
            case DownloadTask.STATUS_PAUSED:
            case DownloadTask.STATUS_ERROR:
                DownloadManager.get().resume(task.getId());
                break;
            default:
                break;
        }
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    /** 删除确认（行尾小钮与长按共用）：白毛玻璃弹窗，居中＋宽写死 320dp（全应用居中框口径） */
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
            params.gravity = android.view.Gravity.CENTER;
            params.width = (int) (320 * activity.getResources().getDisplayMetrics().density);
            window.setAttributes(params);
        }
    }

    /** 已完成：APK 交系统安装器安装（下载器要支持下 APK 装应用，送进视频播放器装不上）；
     * 其余文件仍用应用内播放器打开本地文件；打开即回写最近播放时刻（件 6 口径，
     * VideoActivity.file 内同口已接，这里再保一口直达路径） */
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
        DownloadManager.get().touchPlayed(filePath);
        Context context = view.getContext();
        if (filePath.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(com.fongmi.android.tv.utils.FileUtil.getShareUri(file),
                        "application/vnd.android.package-archive");
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(intent);
            } catch (Throwable t) {
                Notify.show("打开安装器失败");
            }
            return;
        }
        if (context instanceof FragmentActivity) {
            VideoActivity.file((FragmentActivity) context, filePath);
        } else {
            Notify.show("无法打开播放器");
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        String boundTaskId;
        int boundManage = -1;
        CheckBox cbSelect;
        ImageView cover;
        TextView tvName;
        TextView tvStatus;
        TextView tvSize;
        ProgressBar progress;
        ImageButton btnToggle;
        ImageButton btnDelete;

        Holder(@NonNull View itemView) {
            super(itemView);
            cbSelect = itemView.findViewById(R.id.cb_select);
            cover = itemView.findViewById(R.id.iv_cover);
            tvName = itemView.findViewById(R.id.tv_task_name);
            tvStatus = itemView.findViewById(R.id.tv_task_status);
            tvSize = itemView.findViewById(R.id.tv_task_size);
            progress = itemView.findViewById(R.id.pb_task_progress);
            btnToggle = itemView.findViewById(R.id.btn_toggle);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            btnDelete.setImageAlpha(140);
        }
    }
}
