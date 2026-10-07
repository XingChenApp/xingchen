package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
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
        // Position like design: upper-middle, below top bar
        try {
            if (getWindow() != null) {
                getWindow().setGravity(android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
                android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = android.view.WindowManager.LayoutParams.MATCH_PARENT;
                getWindow().setAttributes(lp);
                // Add top margin to the content view
                android.view.View content = findViewById(android.R.id.content);
                if (content != null) {
                    content.setPadding(0, (int) (100 * getContext().getResources().getDisplayMetrics().density), 0, 0);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }

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
            rvPy.setAdapter(new ScriptAdapter(pyFiles, f -> {
                if (listener != null) listener.onScriptSelect(f, true);
                dismiss();
            }));

            rvJs.setLayoutManager(new LinearLayoutManager(getContext()));
            rvJs.setAdapter(new ScriptAdapter(jsFiles, f -> {
                if (listener != null) listener.onScriptSelect(f, false);
                dismiss();
            }));
        }

        // Dismiss on outside touch
        findViewById(android.R.id.content).setOnClickListener(v -> dismiss());
    }

    private List<File> listScripts(File dir, String ext) {
        List<File> result = new ArrayList<>();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(ext));
            if (files != null) {
                for (File f : files) result.add(f);
            }
        }
        return result;
    }
}