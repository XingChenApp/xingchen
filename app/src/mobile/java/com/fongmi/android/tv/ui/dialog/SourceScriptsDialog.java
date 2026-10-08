package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.adapter.ScriptAdapter;
import com.fongmi.android.tv.utils.PyExtConfig;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SourceScriptsDialog extends Dialog {
    private final OnScriptSelectListener listener;
    private File currentFile;
    private List<File> allPyFiles = new ArrayList<>();
    private List<File> allJsFiles = new ArrayList<>();
    private ScriptAdapter pyAdapter;
    private ScriptAdapter jsAdapter;

    public interface OnScriptSelectListener {
        void onScriptSelect(File file, boolean isPy);
    }

    public SourceScriptsDialog(@NonNull Context context, OnScriptSelectListener listener) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        this.listener = listener;
        setCanceledOnTouchOutside(true);
        setCancelable(true);
    }

    public void setCurrentFile(File file) {
        this.currentFile = file;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_source_scripts);
        if (getWindow() != null) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        }
        try {
            if (getWindow() != null) {
                getWindow().setGravity(android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
                android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = android.view.WindowManager.LayoutParams.MATCH_PARENT;
                lp.softInputMode = android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
                getWindow().setAttributes(lp);
                android.view.View content = findViewById(android.R.id.content);
                if (content != null) {
                    float d = getContext().getResources().getDisplayMetrics().density;
                    content.setPadding((int)(24*d), (int)(60*d), (int)(12*d), 0);
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

        allPyFiles = listScripts(pyDir, ".py");
        allJsFiles = listScripts(jsDir, ".js");

        boolean hasPy = !allPyFiles.isEmpty();
        boolean hasJs = !allJsFiles.isEmpty();

        if (!hasPy && !hasJs) {
            layoutColumns.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            layoutColumns.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);

            if (hasPy && hasJs) {
                colPy.setVisibility(View.VISIBLE);
                colJs.setVisibility(View.VISIBLE);
            } else if (hasPy) {
                colPy.setVisibility(View.VISIBLE);
                colJs.setVisibility(View.GONE);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) colPy.getLayoutParams();
                lp.weight = 1;
                lp.setMarginEnd(0);
                colPy.setLayoutParams(lp);
            } else {
                colPy.setVisibility(View.GONE);
                colJs.setVisibility(View.VISIBLE);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) colJs.getLayoutParams();
                lp.weight = 1;
                lp.setMarginStart(0);
                colJs.setLayoutParams(lp);
            }

            rvPy.setLayoutManager(new LinearLayoutManager(getContext()));
            pyAdapter = new ScriptAdapter(allPyFiles, f -> {
                if (listener != null) listener.onScriptSelect(f, true);
                dismiss();
            });
            pyAdapter.setOnItemLongClickListener(f -> showExtConfigDialog(f, true));
            rvPy.setAdapter(pyAdapter);

            rvJs.setLayoutManager(new LinearLayoutManager(getContext()));
            jsAdapter = new ScriptAdapter(allJsFiles, f -> {
                if (listener != null) listener.onScriptSelect(f, false);
                dismiss();
            });
            jsAdapter.setOnItemLongClickListener(f -> showExtConfigDialog(f, false));
            rvJs.setAdapter(jsAdapter);

            scrollToCurrent(rvPy, allPyFiles);
            scrollToCurrent(rvJs, allJsFiles);
        }

        EditText etSearch = findViewById(R.id.et_search);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                applyFilter(s.toString());
            }
        });

        findViewById(android.R.id.content).setOnClickListener(v -> dismiss());
    }

    private void applyFilter(String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        if (pyAdapter != null) pyAdapter.updateData(filterFiles(allPyFiles, kw));
        if (jsAdapter != null) jsAdapter.updateData(filterFiles(allJsFiles, kw));
    }

    private List<File> filterFiles(List<File> files, String kw) {
        if (kw.isEmpty()) return new ArrayList<>(files);
        List<File> result = new ArrayList<>();
        for (File f : files) {
            if (f.getName().toLowerCase().contains(kw)) result.add(f);
        }
        return result;
    }

    private void showExtConfigDialog(File file, boolean isPy) {
        try {
            Context ctx = getContext();
            String name = file.getName();
            String baseName = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
            String key = (isPy ? "py_" : "js_") + baseName;

            String saved = PyExtConfig.load(ctx, key);
            String account = "";
            String password = "";
            boolean startAdvanced = false;
            try {
                if (saved != null && !saved.isEmpty()) {
                    JSONObject jo = new JSONObject(saved);
                    boolean onlyUserPass = true;
                    java.util.Iterator<String> keys = jo.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        if (!"username".equals(k) && !"password".equals(k)) {
                            onlyUserPass = false;
                            break;
                        }
                    }
                    if (onlyUserPass) {
                        account = jo.optString("username", "");
                        password = jo.optString("password", "");
                    } else {
                        startAdvanced = true;
                    }
                }
            } catch (Exception e) {
                startAdvanced = true;
            }

            Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
            dialog.setContentView(R.layout.dialog_py_ext_config);
            dialog.setCanceledOnTouchOutside(true);
            dialog.setCancelable(true);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
                lp.gravity = android.view.Gravity.CENTER;
                dialog.getWindow().setAttributes(lp);
            }

            TextView tvScriptName = dialog.findViewById(R.id.tv_script_name);
            TextView tvToggle = dialog.findViewById(R.id.tv_toggle);
            View boxSimple = dialog.findViewById(R.id.box_simple);
            View boxAdvanced = dialog.findViewById(R.id.box_advanced);
            EditText etAccount = dialog.findViewById(R.id.et_account);
            EditText etPassword = dialog.findViewById(R.id.et_password);
            EditText etJson = dialog.findViewById(R.id.et_json);
            View btnCancel = dialog.findViewById(R.id.btn_cancel);
            View btnSave = dialog.findViewById(R.id.btn_save);

            tvScriptName.setText(name);
            etAccount.setText(account);
            etPassword.setText(password);
            if (startAdvanced && saved != null) {
                etJson.setText(saved);
            }

            final boolean[] isAdvanced = new boolean[]{startAdvanced};
            Runnable refreshMode = new Runnable() {
                @Override
                public void run() {
                    boolean adv = isAdvanced[0];
                    boxSimple.setVisibility(adv ? View.GONE : View.VISIBLE);
                    boxAdvanced.setVisibility(adv ? View.VISIBLE : View.GONE);
                    tvToggle.setText(adv ? "◂ 简单模式" : "高级 ▸");
                }
            };
            refreshMode.run();
            tvToggle.setOnClickListener(v -> {
                isAdvanced[0] = !isAdvanced[0];
                if (isAdvanced[0] && etJson.getText().toString().trim().isEmpty()) {
                    etJson.setText("{\"cookie\":\"\"}");
                    etJson.setSelection("{\"cookie\":\"".length());
                }
                refreshMode.run();
            });

            btnCancel.setOnClickListener(v -> dialog.dismiss());
            btnSave.setOnClickListener(v -> {
                String json = "";
                if (isAdvanced[0]) {
                    String raw = etJson.getText().toString().trim();
                    if (!raw.isEmpty()) {
                        try {
                            new JSONObject(raw);
                            json = raw;
                        } catch (Exception e) {
                            Toast.makeText(ctx, "JSON 格式不正确", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                } else {
                    String a = etAccount.getText().toString().trim();
                    String p = etPassword.getText().toString().trim();
                    if (!a.isEmpty() || !p.isEmpty()) {
                        try {
                            JSONObject jo = new JSONObject();
                            jo.put("username", a);
                            jo.put("password", p);
                            json = jo.toString();
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
                PyExtConfig.save(ctx, key, json);
                Toast.makeText(ctx, "已保存", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });

            dialog.show();
            // Dialog width: match parent with side margins
            if (dialog.getWindow() != null) {
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                float d = ctx.getResources().getDisplayMetrics().density;
                lp.width = (int) (ctx.getResources().getDisplayMetrics().widthPixels - 48 * d);
                dialog.getWindow().setAttributes(lp);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }


    private void scrollToCurrent(RecyclerView rv, List<File> files) {
        if (currentFile == null || files == null || files.isEmpty()) return;
        try {
            String cur = currentFile.getAbsolutePath();
            for (int i = 0; i < files.size(); i++) {
                if (cur.equals(files.get(i).getAbsolutePath())) {
                    rv.scrollToPosition(i);
                    break;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
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
