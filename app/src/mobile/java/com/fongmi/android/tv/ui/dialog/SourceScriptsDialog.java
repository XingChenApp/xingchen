package com.fongmi.android.tv.ui.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
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
            ScriptAdapter pyAdapter = new ScriptAdapter(pyFiles, f -> {
                if (listener != null) listener.onScriptSelect(f, true);
                dismiss();
            });
            pyAdapter.setOnItemLongClickListener(f -> showExtConfigDialog(f, true));
            rvPy.setAdapter(pyAdapter);

            rvJs.setLayoutManager(new LinearLayoutManager(getContext()));
            ScriptAdapter jsAdapter = new ScriptAdapter(jsFiles, f -> {
                if (listener != null) listener.onScriptSelect(f, false);
                dismiss();
            });
            jsAdapter.setOnItemLongClickListener(f -> showExtConfigDialog(f, false));
            rvJs.setAdapter(jsAdapter);

            scrollToCurrent(rvPy, pyFiles);
            scrollToCurrent(rvJs, jsFiles);
        }

        findViewById(android.R.id.content).setOnClickListener(v -> dismiss());
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

            float d = ctx.getResources().getDisplayMetrics().density;
            int pad = (int)(20 * d);

            LinearLayout layout = new LinearLayout(ctx);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(pad, (int)(8*d), pad, (int)(8*d));

            TextView tvSub = new TextView(ctx);
            tvSub.setText(name);
            tvSub.setTextSize(14);
            tvSub.setTextColor(0xFF888888);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.bottomMargin = (int)(4*d);
            layout.addView(tvSub, subLp);

            TextView tvToggle = new TextView(ctx);
            tvToggle.setTextSize(13);
            tvToggle.setTextColor(0xFF007AFF);
            tvToggle.setPadding(0, (int)(4*d), 0, (int)(10*d));
            layout.addView(tvToggle);

            LinearLayout simpleBox = new LinearLayout(ctx);
            simpleBox.setOrientation(LinearLayout.VERTICAL);

            TextView tvAccLabel = new TextView(ctx);
            tvAccLabel.setText("账号");
            tvAccLabel.setTextSize(14);
            tvAccLabel.setTextColor(0xFF333333);
            simpleBox.addView(tvAccLabel);

            EditText etAccount = new EditText(ctx);
            etAccount.setHint("请输入账号");
            etAccount.setText(account);
            etAccount.setTextSize(16);
            etAccount.setSingleLine(true);
            LinearLayout.LayoutParams accLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            accLp.bottomMargin = (int)(12*d);
            simpleBox.addView(etAccount, accLp);

            TextView tvPwdLabel = new TextView(ctx);
            tvPwdLabel.setText("密码");
            tvPwdLabel.setTextSize(14);
            tvPwdLabel.setTextColor(0xFF333333);
            simpleBox.addView(tvPwdLabel);

            EditText etPassword = new EditText(ctx);
            etPassword.setHint("请输入密码");
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            etPassword.setText(password);
            etPassword.setTextSize(16);
            etPassword.setSingleLine(true);
            simpleBox.addView(etPassword);

            layout.addView(simpleBox);

            LinearLayout advBox = new LinearLayout(ctx);
            advBox.setOrientation(LinearLayout.VERTICAL);

            TextView tvJsonLabel = new TextView(ctx);
            tvJsonLabel.setText("JSON 参数");
            tvJsonLabel.setTextSize(14);
            tvJsonLabel.setTextColor(0xFF333333);
            advBox.addView(tvJsonLabel);

            EditText etJson = new EditText(ctx);
            etJson.setHint("{\"cookie\": \"UID=xxx;CID=xxx;SEID=xxx;KID=xxx\"}");
            etJson.setTextSize(13);
            etJson.setTypeface(android.graphics.Typeface.MONOSPACE);
            etJson.setMinLines(4);
            etJson.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
            etJson.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            if (startAdvanced && saved != null) {
                etJson.setText(saved);
            }
            advBox.addView(etJson);

            layout.addView(advBox);

            final boolean[] isAdvanced = new boolean[]{startAdvanced};
            Runnable refreshMode = new Runnable() {
                @Override
                public void run() {
                    boolean adv = isAdvanced[0];
                    simpleBox.setVisibility(adv ? View.GONE : View.VISIBLE);
                    advBox.setVisibility(adv ? View.VISIBLE : View.GONE);
                    tvToggle.setText(adv ? "◂ 简单模式" : "高级 ▸");
                }
            };
            refreshMode.run();
            tvToggle.setOnClickListener(v -> {
                isAdvanced[0] = !isAdvanced[0];
                refreshMode.run();
            });

            AlertDialog dialog = new AlertDialog.Builder(ctx)
                    .setTitle("配置 PY 源")
                    .setView(layout)
                    .setPositiveButton("保存", null)
                    .setNegativeButton("取消", null)
                    .create();
            dialog.setOnShowListener(dlg -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
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
            });
            dialog.show();
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
