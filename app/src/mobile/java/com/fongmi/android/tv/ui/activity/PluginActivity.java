package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.viewbinding.ViewBinding;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.fongmi.android.tv.databinding.ActivityPluginBinding;
import com.fongmi.android.tv.ui.adapter.PluginAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.bean.Plugin;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PluginActivity extends BaseActivity {
    private ActivityPluginBinding binding;
    private PluginAdapter adapter;
    private boolean isPy = true;
    private List<Plugin> plugins = new ArrayList<>();

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
        binding.btnBack.setOnClickListener(v -> finish());
        binding.tabPy.setOnClickListener(v -> switchTab(true));
        binding.tabJs.setOnClickListener(v -> switchTab(false));
        binding.btnImport.setOnClickListener(v -> importPlugins());
        binding.btnDeleteSelected.setOnClickListener(v -> deleteSelected());
        binding.cbSelectAll.setOnCheckedChangeListener((btn, checked) -> {
            if (adapter != null) adapter.selectAll(checked);
        });
        
        binding.rvPlugins.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PluginAdapter(plugins, this::onPluginChanged);
        binding.rvPlugins.setAdapter(adapter);
        
        switchTab(true);
    }

    private void switchTab(boolean py) {
        isPy = py;
        // Update tab UI
        binding.tabPy.setBackgroundResource(py ? com.fongmi.android.tv.R.drawable.seg_selected : 0);
        binding.tabJs.setBackgroundResource(!py ? com.fongmi.android.tv.R.drawable.seg_selected : 0);
        binding.tabPy.setTextColor(py ? 0xFFFFFFFF : 0xFF5A5F69);
        binding.tabJs.setTextColor(!py ? 0xFFFFFFFF : 0xFF5A5F69);
        loadPlugins();
    }

    private void loadPlugins() {
        plugins.clear();
        File dir = new File(getFilesDir(), isPy ? "plugins/py" : "plugins/js");
        String ext = isPy ? ".py" : ".js";
        if (dir.exists()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(ext));
            if (files != null) {
                for (File f : files) {
                    plugins.add(new Plugin(f.getName(), f.getAbsolutePath(), true));
                }
            }
        }
        adapter.notifyDataSetChanged();
        binding.tvCount.setText("共 " + plugins.size() + " 个插件");
    }

    private void importPlugins() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, 1001);
    }

    private void deleteSelected() {
        List<Plugin> toDelete = adapter.getSelected();
        for (Plugin p : toDelete) {
            new File(p.getPath()).delete();
        }
        loadPlugins();
    }

    private void onPluginChanged() {
        binding.tvCount.setText("共 " + plugins.size() + " 个插件");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            // Handle file import - simplified
            loadPlugins();
        }
    }
}