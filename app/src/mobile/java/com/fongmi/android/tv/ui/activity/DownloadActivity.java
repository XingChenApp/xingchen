package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;

import androidx.annotation.NonNull;
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
 * 同时下载/容量上限两条分栏卡、管理模式（点「管理」进入）。
 * 全部列表选择弹窗统一居中、宽高在代码写死（全应用居中框口径）。
 */
public class DownloadActivity extends BaseActivity {

    private static final int REQ_PICK_DIR = 0xD1;

    private ActivityDownloadBinding binding;

    public static void start(Activity activity) {
        // 入队跳页不叠实例（DL-H2-2）：manifest 缺省 standard，每次入队跳页新压一层——
        // 详情→下载页→点行播→再下载，栈上可叠多层下载页。REORDER_TO_FRONT 把已有实例
        // 提前到顶、不新建也不销毁栈上其他页，无实例时照常新建
        activity.startActivity(new Intent(activity, DownloadActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityDownloadBinding.inflate(getLayoutInflater());
        return binding;
    }

    private DownloadTaskAdapter adapter;
    private final DownloadManager.Listener downloadListener = new DownloadManager.Listener() {
        @Override
        public void onTaskChanged(DownloadTask task) {
            refreshTasksThrottled();
        }

        @Override
        public void onRestoreCompleted() {
            // 件 1 恢复收口补刷：首入时压住的空态/计数靠这发落定
            if (!isDestroyed()) refreshTasks();
        }
    };
    /** 进度刷新节流（DL4-10）：进度回调每分片一发，原形每发全量重绑＋全表 stat 重扫。
     *  500ms 尾沿合并——取值口径：同 App 节流族内较疏值（短剧关注链 throttleFirst 500ms 同档；
     *  KTV 音高轨 350ms 是单条进度发射、比整表重绑轻，故不取），500ms 内多次回调只补刷一次。 */
    private static final long REFRESH_THROTTLE_MS = 500;
    private long lastRefreshAtMs;
    private boolean trailingRefreshPosted;
    private final Runnable trailingRefresh = () -> {
        trailingRefreshPosted = false;
        if (!isDestroyed()) refreshTasks();
    };

    private void refreshTasksThrottled() {
        long elapsed = android.os.SystemClock.uptimeMillis() - lastRefreshAtMs;
        if (elapsed >= REFRESH_THROTTLE_MS) {
            refreshTasks();
        } else if (!trailingRefreshPosted) {
            trailingRefreshPosted = true;
            binding.rvDownloads.postDelayed(trailingRefresh, REFRESH_THROTTLE_MS - elapsed);
        }
    }
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
        binding.btnEmptyBrowse.setOnClickListener(v -> finish());
        applyContentWidthCap();
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
        binding.cardDownloadDir.setOnClickListener(v -> openDirPicker());
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
        binding.rvDownloads.removeCallbacks(trailingRefresh);
        DownloadManager.get().removeListener(downloadListener);
    }

    /** 管理模式开关：进入时行上才出勾选框，退出（完成/删完）收起并清选中。
     * 入口钮文案＝「管理」（DL2-3）：旧文案「全选」与旁边真全选框同名同字、实为进管理态，
     * 按下并不全选——文案随实改名，进态后钮面转「完成」退出 */
    private void setManageMode(boolean manage) {
        if (adapter != null) adapter.setManageMode(manage);
        binding.btnManage.setText(manage ? "完成" : "管理");
        binding.cbSelectAll.setVisibility(manage ? View.VISIBLE : View.GONE);
        binding.tvSelected.setVisibility(manage ? View.VISIBLE : View.GONE);
        binding.btnDeleteSelected.setVisibility(manage ? View.VISIBLE : View.GONE);
        if (manage && adapter != null) binding.tvSelected.setText("已选 " + adapter.getSelectedCount() + " 个");
    }

    private void refreshTasks() {
        lastRefreshAtMs = android.os.SystemClock.uptimeMillis();
        List<DownloadTask> tasks = DownloadManager.get().getTasks();
        boolean restored = DownloadManager.get().isRestoreCompleted();
        // 件 1 恢复时机：对账未完成时内存表还没灌回，空表不作数——不画空态、
        // 不露「共 0 个任务」，等 onRestoreCompleted 补刷落定；进程内再入
        // restore 早已完成，照旧即时画。灌回途中逐条到的任务照常先上列表。
        binding.tvEmpty.setVisibility(restored && tasks.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvDownloads.setVisibility(tasks.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvCount.setVisibility(restored ? View.VISIBLE : View.GONE);
        if (adapter != null) adapter.setTasks(tasks);
        if (restored) {
            // 副行「共 N 个任务 · 占用 X」：占用含文件 stat，后台算完回填
            Task.execute(() -> {
                long usage = DownloadManager.get().totalUsageBytes();
                com.fongmi.android.tv.App.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    binding.tvCount.setText("共 " + tasks.size() + " 个任务 · 占用 " + DownloadTask.formatSize(usage));
                });
            });
        }
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
        // 摆位在 show 之前落定（原 show 后才 centerDialog：首帧默认宽、随后跳到 320dp）
        centerDialog(dialog, 320);
        dialog.show();
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

    /** 内容列最大宽（DL7-3）：横屏单列原全宽拉满——封顶 720dp 居中，超出以左右留白收掉；
     * 本页 configChanges 自接管旋转，initView 与 onConfigurationChanged 两处同口调用 */
    private void applyContentWidthCap() {
        float density = getResources().getDisplayMetrics().density;
        int widthPx = getResources().getDisplayMetrics().widthPixels;
        int capPx = (int) (720 * density);
        int side = Math.max((int) (16 * density), (widthPx - capPx) / 2);
        binding.contentContainer.setPadding(side, binding.contentContainer.getPaddingTop(), side, binding.contentContainer.getPaddingBottom());
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyContentWidthCap();
    }

    private void refreshDirSub() {
        // 目录卡文本＝Manager 唯一真源的真实落盘路径；点卡换目录（openDirPicker）
        binding.tvDownloadDirSub.setText(DownloadManager.getDownloadDirPath(this));
    }

    /** 换下载目录：拉起系统目录选择，选定走 onActivityResult 落值 */
    private void openDirPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        String current = DownloadManager.getCustomDirUri(this);
        if (!current.isEmpty()) {
            try {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(current));
            } catch (Exception ignored) {
            }
        }
        try {
            startActivityForResult(intent, REQ_PICK_DIR);
        } catch (Exception e) {
            Notify.show("当前设备不支持系统目录选择");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_DIR) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            Notify.show("已取消，下载目录不变");
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            Notify.show("没拿到这个目录的访问权限，下载目录不变");
            return;
        }
        String path = DownloadManager.pathFromTreeUri(uri);
        if (path == null) {
            Notify.show("这个位置不能当下载目录，请选内部存储或 SD 卡里的文件夹");
            return;
        }
        // 可写探针要碰盘，放后台跑；不过关不落值、不改现行目录
        Task.execute(() -> {
            boolean ok = DownloadManager.probeWritable(new java.io.File(path));
            com.fongmi.android.tv.App.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!ok) {
                    Notify.show("这个目录写不进去，下载目录不变，换一个再试");
                    return;
                }
                DownloadManager.setCustomDir(this, path, uri.toString());
                refreshDirSub();
                // 换根当场收编（DL-H3-4）：恢复对账每进程只跑一次，新根里的无记录存量
                // 不等下次启动、本会话内就补建条目（收编口径与 restore 一致）
                DownloadManager.get().adoptNewRoot(new java.io.File(path));
                Notify.show("下载目录已换，新下载会存到新目录");
            });
        });
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

    /**
     * 通用列表选择窗（线程数/同时下载/容量共用）：居中、宽写死 320dp。
     * listCapDp 大于 0 时列表封顶该高度（dp 写死）、超出框内滚动；0＝不封顶、高随内容。
     * 封顶值 380dp 出处＝布局 dialog_download_threads.xml 既有 maxHeight 定值
     * （清晰度框布局同值）——但 RecyclerView 测量不认 maxHeight 属性、封顶从未
     * 生效，32 个线程选项全量测高把框撑满屏（154 真机截图），故改由代码写死。
     */
    private void showChoiceDialog(String title, String[] labels, int current, java.util.function.IntConsumer onPick, int listCapDp) {
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
        if (listCapDp > 0) {
            // 列表高在 show 之前钉死：按终宽（320dp 减根左右各 16dp）实测一条条目，
            // 总高超顶即把列表高写死为顶值（约八行可见、框内滚动，他 2026-11-16 定案）。
            // 旧法在 post 里等布局完再压高——框先按全量高出帧、随后缩下来，就是动一下。
            float density = getResources().getDisplayMetrics().density;
            int capPx = (int) (listCapDp * density);
            android.view.View item = getLayoutInflater().inflate(R.layout.item_download_thread, recycler, false);
            item.measure(android.view.View.MeasureSpec.makeMeasureSpec((int) (288 * density), android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
            if (item.getMeasuredHeight() * labels.length > capPx) {
                android.view.ViewGroup.LayoutParams lp = recycler.getLayoutParams();
                lp.height = capPx;
                recycler.setLayoutParams(lp);
            }
        }
        // 摆位在 show 之前落定（原 show 后才 centerDialog：首帧默认宽、随后跳到 320dp）
        centerDialog(dialog, 320);
        dialog.show();
    }

    private void showThreadDialog() {
        int current = DownloadManager.getThreadCount(this);
        String[] labels = new String[32];
        for (int i = 0; i < 32; i++) labels[i] = (i + 1) + " 线程";
        showChoiceDialog("下载线程数", labels, current - 1, pos -> {
            DownloadManager.setThreadCount(this, pos + 1);
            refreshThreadSub();
            Notify.show("已设为 " + (pos + 1) + " 线程，新任务生效");
        }, 380);
    }

    private void showConcurrentDialog() {
        int current = DownloadManager.getMaxConcurrent(this);
        String[] labels = new String[5];
        for (int i = 0; i < 5; i++) labels[i] = (i + 1) + " 个任务";
        showChoiceDialog("同时下载", labels, current - 1, pos -> {
            DownloadManager.setMaxConcurrent(this, pos + 1);
            refreshConcurrentSub();
            Notify.show("已设为同时下载 " + (pos + 1) + " 个任务");
        }, 0);
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
        }, 0);
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
        // 摆位在 show 之前落定（原 show 后才 centerDialog：首帧默认宽、随后跳到 320dp）
        centerDialog(dialog, 320);
        dialog.show();
    }
}
