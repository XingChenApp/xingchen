package com.fongmi.android.tv.ui.dialog;

import android.view.View;
import android.widget.CheckBox;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSearchSourceBinding;
import com.fongmi.android.tv.ui.adapter.CheckableAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchSourceDialog extends BaseAlertDialog {

    private DialogSearchSourceBinding binding;
    private CheckableAdapter vodAdapter;
    private CheckableAdapter pyAdapter;
    private CheckableAdapter jsAdapter;

    public static void show(Fragment fragment) {
        new SearchSourceDialog().show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSearchSourceBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        setupVodColumn();
        setupPyColumn();
        setupJsColumn();
        setWidth(0.92f);
    }

    private void setupVodColumn() {
        List<Site> sites = VodConfig.get().getSites();
        if (sites == null || sites.isEmpty()) {
            binding.vodColumn.setVisibility(View.GONE);
            return;
        }
        List<String> names = new ArrayList<>();
        for (Site site : sites) names.add(site.getName());
        binding.vodRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.vodRecycler.setAdapter(vodAdapter = new CheckableAdapter(names));
        binding.vodSelectAll.setOnCheckedChangeListener((v, checked) -> vodAdapter.selectAll(checked));
    }

    private void setupPyColumn() {
        List<String> names = listScriptNames("py", ".py");
        if (names.isEmpty()) {
            binding.pyColumn.setVisibility(View.GONE);
            return;
        }
        // Adjust margins if vod is gone
        if (binding.vodColumn.getVisibility() == View.GONE) {
            ((android.widget.LinearLayout.LayoutParams) binding.pyColumn.getLayoutParams()).setMarginStart(0);
        }
        binding.pyRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.pyRecycler.setAdapter(pyAdapter = new CheckableAdapter(names));
        binding.pySelectAll.setOnCheckedChangeListener((v, checked) -> pyAdapter.selectAll(checked));
    }

    private void setupJsColumn() {
        List<String> names = listScriptNames("js", ".js");
        if (names.isEmpty()) {
            binding.jsColumn.setVisibility(View.GONE);
            return;
        }
        binding.jsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.jsRecycler.setAdapter(jsAdapter = new CheckableAdapter(names));
        binding.jsSelectAll.setOnCheckedChangeListener((v, checked) -> jsAdapter.selectAll(checked));
    }

    private List<String> listScriptNames(String dir, String ext) {
        List<String> result = new ArrayList<>();
        File folder = new File(requireContext().getFilesDir(), "plugins/" + dir);
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles((d, name) -> name.endsWith(ext) || ("js".equals(dir) && name.endsWith(".wv")));
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    // Strip extension (.js or .wv)
                    int dot = name.lastIndexOf('.');
                    result.add(dot > 0 ? name.substring(0, dot) : name);
                }
            }
        }
        return result;
    }

    @Override
    protected void initEvent() {
        binding.close.setOnClickListener(v -> dismiss());
        binding.confirm.setOnClickListener(v -> onConfirm());
    }

    /** Selected source keys for search. Empty means search all. */
    private static final Set<String> selectedKeys = new HashSet<>();
    private static boolean hasSelection = false;

    public static Set<String> getSelectedKeys() {
        return selectedKeys;
    }

    public static boolean hasSelection() {
        return hasSelection;
    }

    public static void clearSelection() {
        selectedKeys.clear();
        hasSelection = false;
    }

    private void onConfirm() {
        selectedKeys.clear();
        hasSelection = true;
        // VOD sources: map names to site keys
        if (vodAdapter != null) {
            List<String> names = vodAdapter.getSelected();
            for (Site site : VodConfig.get().getSites()) {
                if (names.contains(site.getName())) selectedKeys.add(site.getKey());
            }
        }
        // PY scripts: build py_ keys
        if (pyAdapter != null) {
            for (String name : pyAdapter.getSelected()) {
                selectedKeys.add("py_" + name);
            }
        }
        // JS scripts: not supported yet
        if (jsAdapter != null && !jsAdapter.getSelected().isEmpty()) {
            Toast.makeText(requireContext(), "JS 暂未支持", Toast.LENGTH_SHORT).show();
        }
        dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }
}
