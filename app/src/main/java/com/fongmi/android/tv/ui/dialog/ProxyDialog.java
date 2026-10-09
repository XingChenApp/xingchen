package com.fongmi.android.tv.ui.dialog;

import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogProxyBinding;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Json;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProxyDialog extends BaseAlertDialog {

    private DialogProxyBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean editMode = false;
    private String workingRules = "";

    public static ProxyDialog create() {
        return new ProxyDialog();
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof ProxyDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        binding = DialogProxyBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(binding.getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        window.setGravity(Gravity.CENTER);
        WindowManager.LayoutParams params = window.getAttributes();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        float density = getResources().getDisplayMetrics().density;
        params.width = Math.min((int) (screenWidth * 0.94), (int) (700 * density));
        window.setAttributes(params);
    }

    @Override
    protected void initView() {
        binding.etProxyUrl.setText(Setting.getShellProxyUrl());
        workingRules = Setting.getShellProxyRules();
        if (TextUtils.isEmpty(workingRules)) workingRules = "{\"proxy\":[]}";
        refreshTextView();
        refreshToggleBtn();
        setEditMode(false);
    }

    @Override
    protected void initEvent() {
        binding.btnReverse.setOnClickListener(v -> reverseRules());
        binding.btnToggle.setOnClickListener(v -> toggleProxy());
        binding.btnSuggest.setOnClickListener(v -> autoSuggest());
        binding.btnDetect.setOnClickListener(v -> autoDetect());
        binding.btnTest.setOnClickListener(v -> testProxy());
        binding.tabText.setOnClickListener(v -> setEditMode(false));
        binding.tabEdit.setOnClickListener(v -> setEditMode(true));
        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnConfirm.setOnClickListener(v -> saveAndClose());
    }

    private void setEditMode(boolean edit) {
        if (edit && !editMode) {
            editMode = true;
            binding.etJsonEdit.setText(workingRules);
            binding.etJsonEdit.setVisibility(View.VISIBLE);
            binding.svText.setVisibility(View.GONE);
            binding.tabEdit.setBackgroundResource(R.drawable.shape_shield_tab_selected);
            binding.tabEdit.setTextColor(0xFFFFFFFF);
            binding.tabText.setBackground(null);
            binding.tabText.setTextColor(0xB3000000);
        } else if (!edit && editMode) {
            editMode = false;
            workingRules = binding.etJsonEdit.getText().toString().trim();
            if (TextUtils.isEmpty(workingRules)) workingRules = "{\"proxy\":[]}";
            refreshTextView();
            binding.etJsonEdit.setVisibility(View.GONE);
            binding.svText.setVisibility(View.VISIBLE);
            binding.tabText.setBackgroundResource(R.drawable.shape_shield_tab_selected);
            binding.tabText.setTextColor(0xFFFFFFFF);
            binding.tabEdit.setBackground(null);
            binding.tabEdit.setTextColor(0xB3000000);
        } else if (!edit) {
            binding.tabText.setBackgroundResource(R.drawable.shape_shield_tab_selected);
            binding.tabText.setTextColor(0xFFFFFFFF);
            binding.tabEdit.setBackground(null);
            binding.tabEdit.setTextColor(0xB3000000);
        }
    }

    private void refreshTextView() {
        try {
            JsonElement element = Json.parse(workingRules);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            binding.tvJsonText.setText(gson.toJson(element));
        } catch (Exception e) {
            binding.tvJsonText.setText(workingRules);
        }
    }

    private void refreshToggleBtn() {
        binding.btnToggle.setText(Setting.isShellProxy() ? "启用" : "禁用");
    }

    private void toggleProxy() {
        boolean enabled = !Setting.isShellProxy();
        Setting.putShellProxy(enabled);
        refreshToggleBtn();
        Notify.show(enabled ? "代理已启用" : "代理已禁用");
    }

    private void reverseRules() {
        try {
            String json = editMode ? binding.etJsonEdit.getText().toString().trim() : workingRules;
            JsonElement element = Json.parse(json);
            JsonArray array = null;
            JsonObject wrapper = null;
            if (element.isJsonObject() && element.getAsJsonObject().has("proxy")) {
                wrapper = element.getAsJsonObject();
                array = wrapper.getAsJsonArray("proxy");
            } else if (element.isJsonArray()) {
                array = element.getAsJsonArray();
            }
            if (array != null) {
                List<JsonElement> list = new ArrayList<>();
                for (JsonElement e : array) list.add(e);
                Collections.reverse(list);
                JsonArray reversed = new JsonArray();
                for (JsonElement e : list) reversed.add(e);
                if (wrapper != null) {
                    wrapper.add("proxy", reversed);
                    workingRules = wrapper.toString();
                } else {
                    workingRules = reversed.toString();
                }
                if (editMode) binding.etJsonEdit.setText(workingRules);
                else refreshTextView();
                Notify.show("已倒序");
            } else {
                Notify.show("没有可倒序的规则");
            }
        } catch (Exception e) {
            Notify.show("规则格式错误");
        }
    }

    private void autoSuggest() {
        executor.execute(() -> {
            try {
                Site site = VodConfig.get().getHome();
                ProxySetting.Suggestion suggestion = ProxySetting.suggest(site);
                handler.post(() -> {
                    if (suggestion.isEmpty()) {
                        Notify.show("未找到可建议的域名");
                        return;
                    }
                    String defaultUrl = binding.etProxyUrl.getText().toString().trim();
                    if (TextUtils.isEmpty(defaultUrl)) defaultUrl = Setting.getShellProxyUrl();
                    JsonArray array = new JsonArray();
                    JsonObject obj = new JsonObject();
                    JsonArray hosts = new JsonArray();
                    for (String host : suggestion.hosts()) hosts.add(host);
                    obj.add("hosts", hosts);
                    if (!TextUtils.isEmpty(defaultUrl)) {
                        JsonArray urls = new JsonArray();
                        urls.add(defaultUrl);
                        obj.add("urls", urls);
                    }
                    array.add(obj);
                    JsonObject wrapper = new JsonObject();
                    wrapper.add("proxy", array);
                    workingRules = wrapper.toString();
                    if (editMode) binding.etJsonEdit.setText(workingRules);
                    else refreshTextView();
                    Notify.show("已填入 " + suggestion.hosts().size() + " 个建议域名");
                });
            } catch (Exception e) {
                handler.post(() -> Notify.show("建议失败"));
            }
        });
    }

    private void autoDetect() {
        executor.execute(() -> {
            try {
                List<String> found = new ArrayList<>();
                for (Site site : VodConfig.get().getSites()) {
                    ProxySetting.Suggestion s = ProxySetting.suggest(site);
                    for (String url : s.urls()) {
                        if (!found.contains(url)) found.add(url);
                    }
                }
                handler.post(() -> {
                    if (found.isEmpty()) {
                        Notify.show("未识别到代理地址");
                        return;
                    }
                    binding.etProxyUrl.setText(found.get(0));
                    Notify.show("已识别: " + found.get(0));
                });
            } catch (Exception e) {
                handler.post(() -> Notify.show("识别失败"));
            }
        });
    }

    private void testProxy() {
        String url = binding.etProxyUrl.getText().toString().trim();
        String rules = editMode ? binding.etJsonEdit.getText().toString().trim() : workingRules;
        if (TextUtils.isEmpty(url) && TextUtils.isEmpty(rules)) {
            Notify.show("请先配置代理地址或规则");
            return;
        }
        if (!TextUtils.isEmpty(url) && !ProxySetting.isValid(url)) {
            Notify.show("代理地址格式无效");
            return;
        }
        binding.btnTest.setText("测试中…");
        binding.btnTest.setEnabled(false);
        executor.execute(() -> {
            boolean ok = false;
            String testHost = "";
            try {
                List<Proxy> proxies = ProxySetting.getRules(rules, url);
                testHost = ProxySetting.firstTestHost(proxies);
                if (TextUtils.isEmpty(testHost)) testHost = "www.baidu.com";
                okhttp3.OkHttpClient client = buildTestClient(proxies);
                okhttp3.Request request = new okhttp3.Request.Builder().url("https://" + testHost).head().build();
                try (okhttp3.Response response = client.newCall(request).execute()) {
                    ok = response.isSuccessful() || response.code() == 405;
                }
            } catch (Exception e) {
                ok = false;
            }
            boolean result = ok;
            String host = testHost;
            handler.post(() -> {
                binding.btnTest.setText("测试代理");
                binding.btnTest.setEnabled(true);
                Notify.show(result ? "代理可用 (" + host + ")" : "代理不可用");
            });
        });
    }

    private okhttp3.OkHttpClient buildTestClient(List<Proxy> proxies) {
        okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder();
        if (!proxies.isEmpty()) {
            Proxy first = proxies.get(0);
            first.init();
            if (!first.getProxies().isEmpty()) {
                builder.proxy(first.getProxies().get(0));
            }
        }
        builder.connectTimeout(10, TimeUnit.SECONDS);
        builder.readTimeout(10, TimeUnit.SECONDS);
        return builder.build();
    }

    private void saveAndClose() {
        String url = binding.etProxyUrl.getText().toString().trim();
        String rules = editMode ? binding.etJsonEdit.getText().toString().trim() : workingRules;
        if (!TextUtils.isEmpty(url) && !ProxySetting.isValid(ProxySetting.cleanUrl(url))) {
            Notify.show("代理地址格式无效");
            return;
        }
        if (!ProxySetting.isValidRules(rules, url)) {
            Notify.show("代理规则无效");
            return;
        }
        Setting.putShellProxyConfig(url, rules);
        Notify.show("代理已保存");
        dismiss();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
