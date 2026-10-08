package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.adapter.ScriptAdapter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SourceScriptsDialog extends Dialog {
    private final OnScriptSelectListener listener;

    public interface OnScriptSelectListener {
        void onScriptSelect(File file, boolean isPy);
    }

    public SourceScriptsDialog(@NonNull Context context, OnScriptSelectListener listener) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        this.listener = listener;
        setCanceledOnTouchOutside(true);
        setCancelable(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_source_scripts);


        RecyclerView rvPy = findViewById(R.id.rv_py);
        RecyclerView rvJs = findViewById(R.id.rv_js);
        LinearLayout colPy = findViewById(R.id.column_py);
        LinearLayout colJs = findViewById(R.id.column_js);
        LinearLayout layoutColumns = findViewById(R.id.layout_columns);
        TextView tvEmpty = findViewById(R.id.tv_empty);

        File pyDir = new File(getContext().getFilesDir(), "plugins/py");
        File jsDir = new File(getContext().getFilesDir(), "plugins/js");

        List<File> pyFiles = listScripts(pyDir, ".py");
        List<File> jsFiles = listScripts(jsDir, ".js");

        boolean hasPy = !pyFiles.isEmpty();
        boolean hasJs = !jsFiles.isEmpty();

        if (!hasPy && !hasJs) {
            layoutColumns.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            layoutColumns.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);

            if (hasPy && hasJs) {
                // Both: show two columns
                colPy.setVisibility(View.VISIBLE);
                colJs.setVisibility(View.VISIBLE);
            } else if (hasPy) {
                // Only PY: single column, buttons expand
                colPy.setVisibility(View.VISIBLE);
                colJs.setVisibility(View.GONE);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) colPy.getLayoutParams();
                lp.weight = 1;
                lp.setMarginEnd(0);
                colPy.setLayoutParams(lp);
            } else {
                // Only JS: single column
                colPy.setVisibility(View.GONE);
                colJs.setVisibility(View.VISIBLE);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) colJs.getLayoutParams();
                lp.weight = 1;
                lp.setMarginStart(0);
                colJs.setLayoutParams(lp);
            }

            rvPy.setLayoutManager(new LinearLayoutManager(getContext()));
            rvJs.setLayoutManager(new LinearLayoutManager(getContext()));

            ScriptAdapter pyAdapter = new ScriptAdapter(new ArrayList<>(pyFiles), f -> {
                if (listener != null) listener.onScriptSelect(f, true);
                dismiss();
            });
            ScriptAdapter jsAdapter = new ScriptAdapter(new ArrayList<>(jsFiles), f -> {
                if (listener != null) listener.onScriptSelect(f, false);
                dismiss();
            });
            rvPy.setAdapter(pyAdapter);
            rvJs.setAdapter(jsAdapter);

            EditText etSearch = findViewById(R.id.et_search);
            if (etSearch != null) {
                etSearch.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override public void afterTextChanged(Editable s) {
                        String keyword = s.toString().trim().toLowerCase();
                        List<File> filteredPy = new ArrayList<>();
                        List<File> filteredJs = new ArrayList<>();
                        for (File f : pyFiles) {
                            if (f.getName().toLowerCase().contains(keyword)) filteredPy.add(f);
                        }
                        for (File f : jsFiles) {
                            if (f.getName().toLowerCase().contains(keyword)) filteredJs.add(f);
                        }
                        pyAdapter.updateData(filteredPy);
                        jsAdapter.updateData(filteredJs);
                        boolean hasPy = !filteredPy.isEmpty();
                        boolean hasJs = !filteredJs.isEmpty();
                        colPy.setVisibility(hasPy ? View.VISIBLE : View.GONE);
                        colJs.setVisibility(hasJs ? View.VISIBLE : View.GONE);
                        layoutColumns.setVisibility((hasPy || hasJs) ? View.VISIBLE : View.GONE);
                        tvEmpty.setVisibility((hasPy || hasJs) ? View.GONE : View.VISIBLE);
                        if ((hasPy || hasJs)) tvEmpty.setText("无匹配脚本");
                    }
                });
            }
        }

        // Note: Do not set click listener on content view - it interferes with RecyclerView item clicks
    }

    private List<File> listScripts(File dir, String ext) {
        List<File> result = new ArrayList<>();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(ext) || (".js".equals(ext) && name.endsWith(".wv")));
            if (files != null) {
                for (File f : files) result.add(f);
            }
        }
        return result;
    }
}