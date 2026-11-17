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
                // 管理态长按不再出单删确认（DL4-8）：管理态删除走顶部「删除选中」的批量口，
                // 长按在此只消费不出框——旧形管理态长按仍弹单删，与页内批量语义打架
                if (manageMode) return true;
                showDeleteConfirm(v, task);
                return true;
            });
            holder.btnToggle.setOnClickListener(v -> onToggle(task));
            holder.btnDelete.setOnClickListener(v -> showDeleteConfirm(v, task));
        }
        // 同任务再绑与全绑共用：只刷状态/数据/进度/钮四处（封面零闪烁）
        holder.tvStatus.setText(task.getStatusText());
        // 状态行按状态上色（DL3-5）：旧形六态同吃布局默认 #1B2431，扫一眼分不出成败——
        // 完成绿/出错红取深一档压浅玻璃底保对比，其余回布局默认色，皮与字号不动
        holder.tvStatus.setTextColor(statusColor(task.getStatus()));
        // 出错行看全文的口（DL5-3）：状态行单行截断，长报错在行上看不全——点状态行弹全文框；
        // 非出错行不挂监听，点击仍归整行（暂停/继续/打开）
        if (task.getStatus() == DownloadTask.STATUS_ERROR) {
            holder.tvStatus.setOnClickListener(v -> showErrorDetail(v, task));
        } else {
            holder.tvStatus.setOnClickListener(null);
        }
        String sizeText = task.getSizeText();
        holder.tvSize.setText(sizeText);
        // 行高钉死（DL4-2）：大小行/进度条隐时用 INVISIBLE 占位，不再 GONE 塌高——原形随状态在约 82↔86dp 间跳
        holder.tvSize.setVisibility(sizeText.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        int status = task.getStatus();
        holder.progress.setProgress(task.getProgress());
        holder.progress.setVisibility(
                status == DownloadTask.STATUS_DOWNLOADING || status == DownloadTask.STATUS_MERGING
                        || status == DownloadTask.STATUS_WAITING || status == DownloadTask.STATUS_PAUSED
                        ? View.VISIBLE : View.INVISIBLE);
        if (status == DownloadTask.STATUS_DOWNLOADING || status == DownloadTask.STATUS_WAITING) {
            holder.btnToggle.setVisibility(View.VISIBLE);
            holder.btnToggle.setEnabled(true);
            holder.btnToggle.setImageAlpha(255);
            holder.btnToggle.setImageResource(R.drawable.ic_music_pause);
            holder.btnToggle.setContentDescription("暂停下载");
        } else if (status == DownloadTask.STATUS_PAUSED || status == DownloadTask.STATUS_ERROR) {
            holder.btnToggle.setVisibility(View.VISIBLE);
            holder.btnToggle.setEnabled(true);
            holder.btnToggle.setImageAlpha(255);
            holder.btnToggle.setImageResource(R.drawable.ic_music_play);
            holder.btnToggle.setContentDescription("继续下载");
        } else if (status == DownloadTask.STATUS_MERGING) {
            // 合并中行不再零操作口（DL4-6）：合并无暂停/取消语义，钮以禁用淡态明示「在忙」，
            // 点行另有说明（onRowClick 合并分支）；旧形钮 GONE＋点行无定义，长合并像卡死
            holder.btnToggle.setVisibility(View.VISIBLE);
            holder.btnToggle.setEnabled(false);
            holder.btnToggle.setImageAlpha(90);
            holder.btnToggle.setImageResource(R.drawable.ic_music_pause);
            holder.btnToggle.setContentDescription("正在合并");
        } else {
            holder.btnToggle.setVisibility(View.GONE);
        }
    }

    /** 状态行颜色（DL3-5）：完成/出错两态着色，其余回布局默认 #1B2431 */
    private static int statusColor(int status) {
        switch (status) {
            case DownloadTask.STATUS_COMPLETED:
                return 0xFF2E7D32;
            case DownloadTask.STATUS_ERROR:
                return 0xFFC62828;
            default:
                return 0xFF1B2431;
        }
    }

    /** 出错全文框（DL5-3）：与容量报告同布局 dialog_download_info，居中＋宽写死 320dp（全应用居中框口径） */
    private void showErrorDetail(View view, DownloadTask task) {
        Context context = view.getContext();
        if (!(context instanceof FragmentActivity)) return;
        FragmentActivity activity = (FragmentActivity) context;
        if (activity.isFinishing()) return;
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_download_info, null);
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        if (tvTitle != null) tvTitle.setText("下载出错");
        android.widget.TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        if (tvMessage != null) tvMessage.setText(task.getStatusText());
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(dialogView).create();
        dialogView.findViewById(R.id.btn_ok).setOnClickListener(v -> dialog.dismiss());
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
            case DownloadTask.STATUS_MERGING:
                // 合并中点行给说明（DL4-6）：不落播放/不落空转，明示在合并、完后可播
                Notify.show("正在合并，完成后即可播放");
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
