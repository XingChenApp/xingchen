package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSourceSelectBinding;
import com.fongmi.android.tv.utils.VodExtConfig;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SourceSelectDialog extends Dialog {

    private DialogSourceSelectBinding binding;
    private List<Site> allSites;
    private List<Site> filteredSites;
    private SourceSelectAdapter adapter;
    private boolean isSingleColumn = false;
    private String currentKey;

    public SourceSelectDialog(@NonNull Context context, List<Site> sites, String currentKey) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        this.allSites = new ArrayList<>(sites);
        this.filteredSites = new ArrayList<>(sites);
        this.currentKey = currentKey;
        setCanceledOnTouchOutside(true);
        setCancelable(true);
        applyVodExtOverrides(context);
    }

    private void applyVodExtOverrides(Context context) {
        try {
            for (Site site : allSites) {
                if (site == null || site.getKey() == null) continue;
                String saved = VodExtConfig.load(context, site.getKey());
                if (saved != null && !saved.isEmpty()) {
                    site.setExt(saved);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DialogSourceSelectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        adapter = new SourceSelectAdapter(filteredSites, currentKey, site -> {
            dismiss();
            if (listener != null) listener.onSelect(site);
        });
        adapter.setOnItemLongClickListener(this::showExtConfigDialog);
        binding.recycler.setAdapter(adapter);
        updateLayoutManager();

        binding.btnToggle.setOnClickListener(v -> {
            isSingleColumn = !isSingleColumn;
            updateLayoutManager();
        });

        binding.search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void showExtConfigDialog(Site site) {
        try {
            Context ctx = getContext();
            String key = site.getKey();
            String displayName = site.getName();

            String saved = VodExtConfig.load(ctx, key);
            if ((saved == null || saved.isEmpty()) && site.getExt() != null && !site.getExt().isEmpty()) {
                saved = site.getExt();
            }
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

            TextView tvTitle = dialog.findViewById(R.id.tv_title);
            tvTitle.setText("配置点播源");
            TextView tvScriptName = dialog.findViewById(R.id.tv_script_name);
            TextView tvToggle = dialog.findViewById(R.id.tv_toggle);
            View boxSimple = dialog.findViewById(R.id.box_simple);
            View boxAdvanced = dialog.findViewById(R.id.box_advanced);
            EditText etAccount = dialog.findViewById(R.id.et_account);
            EditText etPassword = dialog.findViewById(R.id.et_password);
            EditText etJson = dialog.findViewById(R.id.et_json);
            View btnCancel = dialog.findViewById(R.id.btn_cancel);
            View btnSave = dialog.findViewById(R.id.btn_save);

            tvScriptName.setText(displayName);
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
                VodExtConfig.save(ctx, key, json);
                try {
                    site.setExt(json);
                } catch (Exception e) { e.printStackTrace(); }
                Toast.makeText(ctx, "已保存", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });

            dialog.show();
            if (dialog.getWindow() != null) {
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                float d = ctx.getResources().getDisplayMetrics().density;
                lp.width = (int) (ctx.getResources().getDisplayMetrics().widthPixels - 48 * d);
                dialog.getWindow().setAttributes(lp);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateLayoutManager() {
        int span = isSingleColumn ? 1 : 2;
        binding.recycler.setLayoutManager(new GridLayoutManager(getContext(), span));
    }

    private void filter(String keyword) {
        filteredSites.clear();
        if (keyword == null || keyword.isEmpty()) {
            filteredSites.addAll(allSites);
        } else {
            for (Site site : allSites) {
                if (site.getName() != null && site.getName().contains(keyword)) {
                    filteredSites.add(site);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private OnSelectListener listener;
    public void setOnSelectListener(OnSelectListener listener) {
        this.listener = listener;
    }

    public interface OnSelectListener {
        void onSelect(Site site);
    }
}
