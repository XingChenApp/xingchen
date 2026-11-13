package com.fongmi.android.tv.ui.dialog;
import com.fongmi.android.tv.utils.ToastUtil;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSourceSelectBinding;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.utils.VodExtConfig;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SourceSelectDialog extends Dialog {

    private DialogSourceSelectBinding binding;
    private List<Site> allSites;
    private List<Site> filteredSites;
    private SourceSelectAdapter adapter;
    private boolean isSingleColumn = false;
    private boolean pinnedForEmpty;
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
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            // 进出统一口径：淡入淡出 200ms（与 BaseAlertDialog 基类同一套 HkDialogFade）
            window.setWindowAnimations(R.style.HkDialogFade);
        }
        // 窗口宽高与重力不在这里设：onCreate 末尾由 pinWindowToFinalSize 按终值一次钉死

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

        pinWindowToFinalSize();
    }

    /**
     * 摆位只此一趟，show 之前按终值一次算清（2026-10-29 他真机点名「打开会重摆一次」）：
     * 旧法窗口高 WRAP_CONTENT、重力走默认居中，高度要等 RecyclerView 布局落定才定型——
     * 首帧先按矮高出帧（他实拍 113612 里列表区高为 0、整卡只剩搜索行且按矮高居中），
     * 列表撑起后窗口被重新居中，就是那一下。现：宽＝屏宽减两侧各 24dp（他的定值）；
     * 高＝按终宽把内容整体预先测一遍得到的实测高；重力显式 CENTER；三值在 show 前
     * 一次写入窗口属性。show 之后窗口尺寸不再变，打开全程零二次位移；进场动画只是
     * HkDialogFade 的纯 alpha 淡入，不带位移，保留不动。
     * 注：布局根上的 layout_marginHorizontal 经 ViewBinding 无父 inflate 会被丢弃、
     * 从未生效（实拍卡片几乎贴边即为证），左右留白改由这里的窗口宽承担。
     */
    private void pinWindowToFinalSize() {
        Window window = getWindow();
        if (window == null || binding == null) return;
        android.util.DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        int width = Math.max(0, dm.widthPixels - Math.round(48 * dm.density));
        View root = binding.getRoot();
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.AT_MOST));
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = width;
        lp.height = root.getMeasuredHeight();
        lp.gravity = Gravity.CENTER;
        window.setAttributes(lp);
        pinnedForEmpty = adapter == null || adapter.getItemCount() == 0;
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
                // 进出统一口径：淡入淡出 200ms（与 BaseAlertDialog 基类同一套 HkDialogFade）
                dialog.getWindow().setWindowAnimations(R.style.HkDialogFade);
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                // 终宽在 show 前一次算清（屏宽减两侧各 24dp）：旧法 show 之后再改宽＝二次摆位，已摘除
                float d = ctx.getResources().getDisplayMetrics().density;
                lp.width = (int) (ctx.getResources().getDisplayMetrics().widthPixels - 48 * d);
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
                            ToastUtil.show(ctx, "JSON 格式不正确", Toast.LENGTH_SHORT);
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
                ToastUtil.show(ctx, "已保存", Toast.LENGTH_SHORT);
                dialog.dismiss();
            });

            dialog.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateLayoutManager() {
        int span = isSingleColumn ? 1 : 2;
        binding.recycler.setLayoutManager(new GridLayoutManager(getContext(), span));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 弹窗开着时配置重载也能刷新列表：VodConfig 成功时发 ConfigEvent.vod（X-02 起只在成功时发），
        // 事件管线是干净的（VodConfig.postEvent → EventBus），弹窗此前只是没订阅。
        try {
            EventBus.getDefault().register(this);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onStop() {
        try {
            EventBus.getDefault().unregister(this);
        } catch (Exception ignored) {
        }
        super.onStop();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event != null && event.isVod()) refreshSites();
    }

    private void refreshSites() {
        try {
            // A7：刷新前存 LayoutManager 滚动状态，刷新后恢复；adapter 重建本身不动（currentKey 仍需重建时带入）。
            Parcelable scrollState = null;
            if (binding != null && binding.recycler.getLayoutManager() != null) {
                scrollState = binding.recycler.getLayoutManager().onSaveInstanceState();
            }
            allSites = new ArrayList<>(VodConfig.get().getSites());
            applyVodExtOverrides(getContext());
            try {
                com.fongmi.android.tv.bean.Site home = VodConfig.get().getHome();
                if (home != null && home.getKey() != null) currentKey = home.getKey();
            } catch (Exception ignored) {
            }
            adapter = new SourceSelectAdapter(filteredSites, currentKey, site -> {
                dismiss();
                if (listener != null) listener.onSelect(site);
            });
            adapter.setOnItemLongClickListener(this::showExtConfigDialog);
            if (binding != null) {
                binding.recycler.setAdapter(adapter);
                // 保留用户已输入的搜索关键字再过滤，不把列表打回未过滤态
                filter(binding.search.getText() == null ? "" : binding.search.getText().toString());
                if (scrollState != null && binding.recycler.getLayoutManager() != null) {
                    binding.recycler.getLayoutManager().onRestoreInstanceState(scrollState);
                }
                // 仅限「开时列表为空、配置后到」这一路补钉一次高度；开时非空的永不重钉
                if (pinnedForEmpty && adapter.getItemCount() > 0) {
                    pinnedForEmpty = false;
                    pinWindowToFinalSize();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
