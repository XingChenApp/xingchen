package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityDownloadBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.download.DownloadManager;
import com.fongmi.android.tv.utils.download.DownloadTask;

import androidx.recyclerview.widget.LinearLayoutManager;

import java.io.File;
import java.util.List;
import java.util.Set;

public class DownloadActivity extends BaseActivity {

    private static final String PREFS = "xingchen";
    /** SAF tree uri chosen via system directory picker (new). */
    private static final String KEY_DIR_URI = "xingchen.download_dir_uri";
    /** Legacy plain-text path, kept as fallback for users who typed one before. */
    private static final String KEY_DIR = "xingchen.download_dir";

    private static final int REQ_PICK_DIR = 0xD1;

    private ActivityDownloadBinding binding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, DownloadActivity.class));
    }

    /** Persisted SAF tree uri, or null if the user never picked one. */
    public static Uri getDownloadDirUri(Context context) {
        String s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DIR_URI, "");
        return (s == null || s.isEmpty()) ? null : Uri.parse(s);
    }

    /** Human-readable download directory for display. */
    public static String getDownloadDir(Context context) {
        Uri uri = getDownloadDirUri(context);
        if (uri != null) {
            String name = getDisplayName(context, uri);
            if (name != null) return name;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String dir = prefs.getString(KEY_DIR, "");
        if (dir == null || dir.isEmpty()) {
            dir = new File(context.getFilesDir(), "Download").getAbsolutePath();
        }
        return dir;
    }

    private static String getDisplayName(Context context, Uri uri) {
        Cursor c = null;
        try {
            c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        try {
            String seg = uri.getLastPathSegment();
            if (seg != null) {
                int i = seg.indexOf(':');
                return i >= 0 ? seg.substring(i + 1) : seg;
            }
        } catch (Exception ignored) {
        }
        return null;
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
        binding.rvDownloads.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DownloadTaskAdapter();
        binding.rvDownloads.setAdapter(adapter);
        adapter.setOnSelectionChangeListener((selectedCount, totalCount) -> {
            updatingSelectAll = true;
            binding.cbSelectAll.setChecked(totalCount > 0 && selectedCount == totalCount);
            updatingSelectAll = false;
        });
        binding.cbSelectAll.setOnCheckedChangeListener((btn, checked) -> {
            if (updatingSelectAll || adapter == null) return;
            if (checked) adapter.selectAll();
            else adapter.clearSelection();
        });
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
        refreshThreadSub();
        if (binding.cardDownloadThreads != null) {
            binding.cardDownloadThreads.setOnClickListener(v -> showThreadDialog());
        }
        refreshTasks();
        DownloadManager.get().addListener(downloadListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DownloadManager.get().removeListener(downloadListener);
    }

    private void refreshTasks() {
        List<DownloadTask> tasks = DownloadManager.get().getTasks();
        binding.tvCount.setText("共 " + tasks.size() + " 个任务");
        binding.tvEmpty.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvDownloads.setVisibility(tasks.isEmpty() ? View.GONE : View.VISIBLE);
        if (adapter != null) adapter.setTasks(tasks);
    }

    private void refreshThreadSub() {
        if (binding.tvThreadsSub != null) {
            binding.tvThreadsSub.setText(DownloadManager.getThreadCount(this) + " 线程");
        }
    }

    private void showThreadDialog() {
        int current = DownloadManager.getThreadCount(this);
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_download_threads);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.85), android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
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
                int count = position + 1;
                com.google.android.material.textview.MaterialTextView tv = holder.itemView.findViewById(R.id.text);
                androidx.appcompat.widget.AppCompatRadioButton radio = holder.itemView.findViewById(R.id.radio);
                tv.setText(count + " 线程");
                radio.setChecked(count == current);
                holder.itemView.setOnClickListener(v -> {
                    DownloadManager.setThreadCount(DownloadActivity.this, count);
                    refreshThreadSub();
                    dialog.dismiss();
                    Notify.show("已设为 " + count + " 线程，新任务生效");
                });
            }
            @Override
            public int getItemCount() {
                return 32;
            }
        });
        // Scroll to current selection
        recycler.post(() -> recycler.scrollToPosition(current - 1));
        android.view.View cancel = dialog.findViewById(R.id.cancel);
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /** 批量删除选中：白毛玻璃确认弹窗 */
    private void showDeleteSelectedConfirm(Set<String> ids) {
        int count = ids.size();
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_download_delete_confirm, null);
        android.widget.TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        if (tvMessage != null) tvMessage.setText("确定删除选中的 " + count + " 个任务及已下载文件吗？");
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setView(dialogView).create();
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            for (String id : ids) DownloadManager.get().delete(id);
            if (adapter != null) adapter.clearSelection();
            Notify.show("已删除 " + count + " 个任务");
            dialog.dismiss();
        });
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            window.setAttributes(params);
        }
    }

    private void refreshDirSub() {
        binding.tvDownloadDirSub.setText(getDownloadDir(this));
    }

    private void openDirPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        Uri current = getDownloadDirUri(this);
        if (current != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, current);
        try {
            startActivityForResult(intent, REQ_PICK_DIR);
        } catch (Exception e) {
            Notify.show("当前设备不支持系统目录选择");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_DIR || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            Notify.show("无法保留目录访问权限");
            return;
        }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DIR_URI, uri.toString()).apply();
        refreshDirSub();
        Notify.show("已保存");
    }
}
