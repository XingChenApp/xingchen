package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.bean.Plugin;
import com.fongmi.android.tv.databinding.ActivityPluginBinding;
import com.fongmi.android.tv.ui.adapter.PluginAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PluginActivity extends BaseActivity {

    private ActivityPluginBinding binding;
    private PluginAdapter pyAdapter;
    private PluginAdapter jsAdapter;
    private String currentTab = "py";
    private SharedPreferences prefs;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, PluginActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityPluginBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        prefs = getSharedPreferences("plugins", MODE_PRIVATE);

        pyAdapter = new PluginAdapter();
        jsAdapter = new PluginAdapter();

        binding.rvPy.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPy.setAdapter(pyAdapter);
        binding.rvJs.setLayoutManager(new LinearLayoutManager(this));
        binding.rvJs.setAdapter(jsAdapter);

        PluginAdapter.OnPluginListener listener = new PluginAdapter.OnPluginListener() {
            @Override
            public void onToggle(Plugin plugin, boolean enabled) {
                saveEnabledState(plugin, enabled);
            }

            @Override
            public void onDelete(Plugin plugin) {
                deletePlugin(plugin);
            }

            @Override
            public void onSelectChanged() {
                updateCounts();
            }
        };
        pyAdapter.setListener(listener);
        jsAdapter.setListener(listener);

        binding.btnTabPy.setOnClickListener(v -> switchTab("py"));
        binding.btnTabJs.setOnClickListener(v -> switchTab("js"));
        binding.btnImportPy.setOnClickListener(v -> importLauncher.launch(new String[]{"*/*"}));
        binding.btnImportJs.setOnClickListener(v -> importLauncher.launch(new String[]{"*/*"}));
        binding.cbSelectAllPy.setOnCheckedChangeListener((v, checked) -> pyAdapter.selectAll(checked));
        binding.cbSelectAllJs.setOnCheckedChangeListener((v, checked) -> jsAdapter.selectAll(checked));
        binding.btnBatchDelPy.setOnClickListener(v -> batchDelete(pyAdapter));
        binding.btnBatchDelJs.setOnClickListener(v -> batchDelete(jsAdapter));

        loadPlugins();
        switchTab("py");
    }

    private void switchTab(String tab) {
        currentTab = tab;
        boolean isPy = "py".equals(tab);
        binding.panePy.setVisibility(isPy ? View.VISIBLE : View.GONE);
        binding.paneJs.setVisibility(isPy ? View.GONE : View.VISIBLE);
        binding.btnTabPy.setBackgroundTintList(getColorStateList(isPy ? android.R.color.holo_orange_dark : android.R.color.darker_gray));
        binding.btnTabJs.setBackgroundTintList(getColorStateList(isPy ? android.R.color.darker_gray : android.R.color.holo_orange_dark));
    }

    private File getPluginDir(String type) {
        File dir = new File(getFilesDir(), "plugins/" + type);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void loadPlugins() {
        pyAdapter.setPlugins(scanPlugins("py"));
        jsAdapter.setPlugins(scanPlugins("js"));
        updateCounts();
    }

    private List<Plugin> scanPlugins(String type) {
        List<Plugin> list = new ArrayList<>();
        File dir = getPluginDir(type);
        File[] files = dir.listFiles();
        if (files != null) {
            Set<String> disabled = prefs.getStringSet("disabled_" + type, new HashSet<>());
            for (File f : files) {
                if (f.isFile()) {
                    Plugin p = new Plugin(f.getName(), f.getAbsolutePath(), type);
                    p.setEnabled(!disabled.contains(f.getName()));
                    list.add(p);
                }
            }
        }
        return list;
    }

    private void saveEnabledState(Plugin plugin, boolean enabled) {
        Set<String> disabled = new HashSet<>(prefs.getStringSet("disabled_" + plugin.getType(), new HashSet<>()));
        if (enabled) {
            disabled.remove(plugin.getName());
        } else {
            disabled.add(plugin.getName());
        }
        prefs.edit().putStringSet("disabled_" + plugin.getType(), disabled).apply();
    }

    private void deletePlugin(Plugin plugin) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage("确定删除 " + plugin.getName() + "？")
            .setPositiveButton("删除", (d, w) -> {
                new File(plugin.getPath()).delete();
                loadPlugins();
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void batchDelete(PluginAdapter adapter) {
        List<Plugin> selected = adapter.getSelected();
        if (selected.isEmpty()) {
            Toast.makeText(this, "请先选择插件", Toast.LENGTH_SHORT).show();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage("确定删除选中的 " + selected.size() + " 个插件？")
            .setPositiveButton("删除", (d, w) -> {
                for (Plugin p : selected) new File(p.getPath()).delete();
                loadPlugins();
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void updateCounts() {
        int pyTotal = pyAdapter.getPlugins().size();
        int pySel = pyAdapter.getSelected().size();
        binding.tvPyCount.setText(pySel > 0 ? "已选 " + pySel + " / " + pyTotal + " 个" : "共 " + pyTotal + " 个插件");

        int jsTotal = jsAdapter.getPlugins().size();
        int jsSel = jsAdapter.getSelected().size();
        binding.tvJsCount.setText(jsSel > 0 ? "已选 " + jsSel + " / " + jsTotal + " 个" : "共 " + jsTotal + " 个插件");
    }

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenMultipleDocuments(),
        uris -> {
            if (uris == null || uris.isEmpty()) return;
            String type = currentTab;
            File dir = getPluginDir(type);
            int count = 0;
            for (Uri uri : uris) {
                try {
                    String name = getFileName(uri);
                    if (name == null) continue;
                    // Check extension
                    if ("py".equals(type) && !name.endsWith(".py")) continue;
                    if ("js".equals(type) && !name.endsWith(".js")) continue;
                    File dest = new File(dir, name);
                    try (InputStream in = getContentResolver().openInputStream(uri);
                         FileOutputStream out = new FileOutputStream(dest)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                        count++;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (count > 0) {
                Toast.makeText(this, "导入 " + count + " 个插件", Toast.LENGTH_SHORT).show();
                loadPlugins();
            }
        }
    );

    private String getFileName(Uri uri) {
        String name = null;
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception e) {}
        if (name == null) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut >= 0) name = path.substring(cut + 1);
            }
        }
        return name;
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPlugins();
    }
}
