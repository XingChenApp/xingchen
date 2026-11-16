package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityDownloadBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.download.DownloadManager;
import com.fongmi.android.tv.utils.download.DownloadTask;

import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;
import java.util.Set;

/**
 * 下载列表页（件 8 整页定版）：副行计数＋占用、目录/线程数与
 * 同时下载/容量上限两条分栏卡、管理模式（点「全选」进入）。
 * 全部列表选择弹窗统一居中、宽高在代码写死（全应用居中框口径）。
 */
public class DownloadActivity extends BaseActivity {

    private ActivityDownloadBinding binding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, DownloadActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityDownloadBinding.inflate(getLayoutInflater());
        return binding;
    }

    private DownloadTaskAdapter adapter;
    private final DownloadManager.Listener downloadListener = task -> refreshTasks();
    /** 同步全选框状态时屏蔽监听回环 */
    private boolean updatingSelectAll = false;

    @Override
    protected void initView(Bundle savedInstanceState) {
        // 件 1 恢复触发点之一：首开本页先对账灌回（幂等，异步进行，
        // 灌回后经下载监听自动刷新列表）
        DownloadManager.get().restore();
        binding.rvDownloads.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DownloadTaskAdapter();
        binding.rvDownloads.setAdapter(adapter);
        adapter.setOnSelectionChangeListener((selectedCount, totalCount) -> {
            updatingSelectAll = true;
            binding.cbSelectAll.setChecked(totalCount > 0 && selectedCount == totalCount);
            updatingSelectAll = false;
            binding.tvSelected.setText("已选 " + selectedCount + " 个");
        });
        binding.cbSelectAll.setOnCheckedChangeListener((btn, checked) -> {
            if (updatingSelectAll || adapter == null) return;
            if (checked) adapter.selectAll();
            else adapter.clearSelection();
        });
        binding.btnManage.setOnClickListener(v -> setManageMode(adapter != null && !adapter.isManageMode()));
        binding.btnDeleteSelected.setOnClickListener(v -> {
            if (adapter == null) return;
            Set<String> ids = adapter.getSelectedIds();
            if (ids.isEmpty()) {
                Notify.show("请先勾选要删除的任务");
                return;
            }
            showDeleteSelectedConfirm(ids);
        });
        refreshDirSub();
        binding.cardDownloadThreads.setOnClickListener(v -> showThreadDialog());
        binding.cardDownloadConcurrent.setOnClickListener(v -> showConcurrentDialog());
        binding.cardDownloadCapacity.setOnClickListener(v -> showCapacityDialog());
        refreshThreadSub();
        refreshConcurrentSub();
        refreshCapacitySub();
        refreshTasks();
        DownloadManager.get().addListener(downloadListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        DownloadManager.DownloadActivityForeground.setForeground(true);
        maybeShowCapacityReport();
    }

    @Override
    protected void onPause() {
        DownloadManager.DownloadActivityForeground.setForeground(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DownloadManager.get().removeListener(downloadListener);
    }

    /** 管理模式开关：进入时行上才出勾选框，退出（完成/删完）收起并清选中 */
    private void setManageMode(boolean manage) {
        if (adapter != null) adapter.setManageMode(manage);
        binding.btnManage.setText(manage ? "完成" : "全选");
        binding.cbSelectAll.setVisibility(manage ? View.VISIBLE : View.GONE);
        binding.tvSelected.setVisibility(manage ? View.VISIBLE : View.GONE);
        binding.btnDeleteSelected.setVisibility(manage ? View.VISIBLE : View.GONE);
        if (manage && adapter != null) binding.tvSelected.setText("已选 " + adapter.getSelectedCount() + " 个");
    }

    private void refreshTasks() {
        List<DownloadTask> tasks = DownloadManager.get().getTasks();
        binding.tvEmpty.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvDownloads.setVisibility(tasks.isEmpty() ? View.GONE : View.VISIBLE);
        if (adapter != null) adapter.setTasks(tasks);
        // 副行「共 N 个任务 · 占用 X」：占用含文件 stat，后台算完回填
        Task.execute(() -> {
            long usage = DownloadManager.get().totalUsageBytes();
            com.fongmi.android.tv.App.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                binding.tvCount.setText("共 " + tasks.size() + " 个任务 · 占用 " + DownloadTask.formatSize(usage));
            });
        });
        maybeShowCapacityReport();
    }

    /** 件 6 清理报告：留置清单在前台时弹页内弹窗（取后即清，只弹一次） */
    private void maybeShowCapacityReport() {
        if (isFinishing() || isDestroyed()) return;
        DownloadManager.CapacityReport report = DownloadManager.takePendingReport();
        if (report == null) return;
        StringBuilder msg = new StringBuilder();
        msg.append("已按容量上限（").append(DownloadTask.formatSize(report.limitBytes))
                .append("）自动清理 ").append(report.names.size())
                .append(" 个最久未看的下载，共 ").append(DownloadTask.formatSize(report.freedBytes)).append("：");
        for (String name : report.names) msg.append("\n· ").append(name);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_download_info, null);
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        if (tvTitle != null) tvTitle.setText("下载容量自动清理");
        android.widget.TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        if (tvMessage != null) tvMessage.setText(msg.toString());
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setView(dialogView).create();
        dialogView.findViewById(R.id.btn_ok).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        centerDialog(dialog, 320);
    }

    /** 居中框统一口径：位置居中、宽在代码写死（dp 换算），不靠系统自适应 */
    private void centerDialog(android.app.Dialog dialog, int widthDp) {
        android.view.Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        android.view.WindowManager.LayoutParams params = window.getAttributes();
        params.gravity = android.view.Gravity.CENTER;
        params.width = (int) (widthDp * getResources().getDisplayMetrics().density);
        params.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
    }

    private void refreshDirSub() {
        // 件 7：目录卡纯信息展示——文本＝Manager 唯一真源的真实落盘路径
        binding.tvDownloadDirSub.setText(DownloadManager.getDownloadDirPath(this));
    }

    private void refreshThreadSub() {
        binding.tvThreadsSub.setText(DownloadManager.getThreadCount(this) + " 线程");
    }

    private void refreshConcurrentSub() {
        binding.tvConcurrentSub.setText(DownloadManager.getMaxConcurrent(this) + " 个任务");
    }

    private void refreshCapacitySub() {
        long max = DownloadManager.getMaxBytes(this);
        binding.tvCapacitySub.setText(max <= 0 ? "不限" : DownloadTask.formatSize(max));
    }

    /** 通用列表选择窗（线程数/同时下载/容量共用）：居中、宽写死 320dp */
    private void showChoiceDialog(String title, String[] labels, int current, java.util.function.IntConsumer onPick) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_download_threads);
        android.widget.TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
        if (tvTitle != null) tvTitle.setText(title);
        androidx.recyclerview.widget.RecyclerView recycler = dialog.findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
                android.view.View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download_thread, parent, false);
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {};
            }
            @Override
            public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                com.google.android.material.textview.MaterialTextView tv = holder.itemView.findViewById(R.id.text);
                androidx.appcompat.widget.AppCompatRadioButton radio = holder.itemView.findViewById(R.id.radio);
                tv.setText(labels[position]);
                radio.setChecked(position == current);
                holder.itemView.setOnClickListener(v -> {
                    dialog.dismiss();
                    onPick.accept(position);
                });
            }
            @Override
            public int getItemCount() {
                return labels.length;
            }
        });
        if (current > 0) recycler.post(() -> recycler.scrollToPosition(current));
        dialog.findViewById(R.id.cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        centerDialog(dialog, 320);
    }

    private void showThreadDialog() {
        int current = DownloadManager.getThreadCount(this);
        String[] labels = new String[32];
        for (int i = 0; i < 32; i++) labels[i] = (i + 1) + " 线程";
        showChoiceDialog("下载线程数", labels, current - 1, pos -> {
            DownloadManager.setThreadCount(this, pos + 1);
            refreshThreadSub();
            Notify.show("已设为 " + (pos + 1) + " 线程，新任务生效");
        });
    }

    private void showConcurrentDialog() {
        int current = DownloadManager.getMaxConcurrent(this);
        String[] labels = new String[5];
        for (int i = 0; i < 5; i++) labels[i] = (i + 1) + " 个任务";
        showChoiceDialog("同时下载", labels, current - 1, pos -> {
            DownloadManager.setMaxConcurrent(this, pos + 1);
            refreshConcurrentSub();
            Notify.show("已设为同时下载 " + (pos + 1) + " 个任务");
        });
    }

    private void showCapacityDialog() {
        long[] options = {0L, 5L << 30, 10L << 30, 20L << 30, 50L << 30};
        String[] labels = {"不限", "5GB", "10GB", "20GB", "50GB"};
        long current = DownloadManager.getMaxBytes(this);
        int currentPos = 0;
        for (int i = 0; i < options.length; i++) if (options[i] == current) currentPos = i;
        showChoiceDialog("容量上限", labels, currentPos, pos -> {
            DownloadManager.setMaxBytes(this, options[pos]);
            refreshCapacitySub();
            Notify.show(options[pos] <= 0 ? "容量上限已设为不限" : "容量上限已设为 " + labels[pos]);
        });
    }

    /** 批量删除选中：白毛玻璃确认弹窗（居中、宽写死 320dp） */
    private void showDeleteSelectedConfirm(Set<String> ids) {
        int count = ids.size();
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_download_delete_confirm, null);
        android.widget.TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        if (tvMessage != null) tvMessage.setText("确定删除选中的 " + count + " 个任务及已下载文件吗？");
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setView(dialogView).create();
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            for (String id : ids) DownloadManager.get().delete(id);
            setManageMode(false);
            Notify.show("已删除 " + count + " 个任务");
            dialog.dismiss();
        });
        dialog.show();
        centerDialog(dialog, 320);
    }
}
