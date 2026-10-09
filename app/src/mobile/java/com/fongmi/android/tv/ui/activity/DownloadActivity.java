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

import com.fongmi.android.tv.databinding.ActivityDownloadBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;

import java.io.File;

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

    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.tvCount.setText("共 0 个任务");
        binding.tvEmpty.setVisibility(View.VISIBLE);
        binding.rvDownloads.setVisibility(View.GONE);
        binding.cbSelectAll.setOnCheckedChangeListener((btn, checked) -> Notify.show(checked ? "全选" : "取消全选"));
        binding.btnDeleteSelected.setOnClickListener(v -> Notify.show("暂无可删除任务"));
        refreshDirSub();
        binding.cardDownloadDir.setOnClickListener(v -> openDirPicker());
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
