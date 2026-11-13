package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.ai.AiClient;
import com.fongmi.android.tv.ai.AiProvider;
import com.fongmi.android.tv.ai.AiStore;
import com.fongmi.android.tv.databinding.ActivityAiProviderEditBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.ChoiceDialog;
import com.fongmi.android.tv.utils.Notify;

import java.util.List;

/**
 * 添加 / 编辑 AI 服务商。
 */
public class AiProviderEditActivity extends BaseActivity {

    private static final String EXTRA_ID = "provider_id";

    private ActivityAiProviderEditBinding binding;
    private AiProvider editing;
    private String protocol = AiProvider.PROTOCOL_OPENAI;
    private boolean testing = false;

    public static void start(Activity activity, String providerId) {
        Intent i = new Intent(activity, AiProviderEditActivity.class);
        if (providerId != null) i.putExtra(EXTRA_ID, providerId);
        activity.startActivity(i);
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityAiProviderEditBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        String id = getIntent().getStringExtra(EXTRA_ID);
        if (!TextUtils.isEmpty(id)) {
            editing = AiStore.findById(this, id);
        }
        if (editing != null) {
            binding.tvPageTitle.setText("编辑 AI 服务商");
            binding.etName.setText(editing.name);
            binding.etBaseUrl.setText(editing.baseUrl);
            binding.etApiKey.setText(editing.apiKey);
            binding.etModel.setText(editing.model);
            protocol = editing.protocol;
        }
        refreshProtocol();
        binding.etApiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        binding.cardProtocol.setOnClickListener(v -> showProtocolPicker());
        binding.cardTemplate.setOnClickListener(v -> showTemplatePicker());
        binding.btnFetchModels.setOnClickListener(v -> fetchModels());
        binding.btnTest.setOnClickListener(v -> testConnection());
        binding.btnCancel.setOnClickListener(v -> finish());
        binding.btnSave.setOnClickListener(v -> save());
    }

    private void refreshProtocol() {
        if (AiProvider.PROTOCOL_OPENAI.equals(protocol)) {
            binding.tvProtocol.setText("OpenAI 兼容");
        } else if (AiProvider.PROTOCOL_ANTHROPIC.equals(protocol)) {
            binding.tvProtocol.setText("Anthropic（即将支持）");
        } else if (AiProvider.PROTOCOL_GEMINI.equals(protocol)) {
            binding.tvProtocol.setText("Gemini（即将支持）");
        } else {
            binding.tvProtocol.setText("自定义");
        }
    }

    private void showProtocolPicker() {
        CharSequence[] items = {"OpenAI 兼容", "Anthropic（即将支持）", "Gemini（即将支持）"};
        int selected = 0;
        if (AiProvider.PROTOCOL_ANTHROPIC.equals(protocol)) selected = 1;
        else if (AiProvider.PROTOCOL_GEMINI.equals(protocol)) selected = 2;
        ChoiceDialog.showSingle(getSupportFragmentManager(), "协议", items, selected, which -> {
            if (which == 1) {
                Notify.show("Anthropic 协议即将支持");
                return;
            }
            if (which == 2) {
                Notify.show("Gemini 协议即将支持");
                return;
            }
            protocol = AiProvider.PROTOCOL_OPENAI;
            refreshProtocol();
        });
    }

    private void showTemplatePicker() {
        AiProvider.Template[] ts = AiProvider.templates();
        CharSequence[] names = new CharSequence[ts.length];
        for (int i = 0; i < ts.length; i++) names[i] = ts[i].name;
        ChoiceDialog.showSingle(getSupportFragmentManager(), "一键模板", names, -1, which -> {
            AiProvider.Template t = ts[which];
            binding.tvTemplate.setText(t.name);
            binding.etBaseUrl.setText(t.baseUrl);
            binding.etModel.setText(t.model);
            protocol = AiProvider.PROTOCOL_OPENAI;
            refreshProtocol();
        });
    }

    private void fetchModels() {
        String baseUrl = binding.etBaseUrl.getText().toString().trim();
        String apiKey = binding.etApiKey.getText().toString().trim();
        if (TextUtils.isEmpty(baseUrl)) {
            Notify.show("请先填写 Base URL");
            return;
        }
        if (!baseUrl.startsWith("http")) {
            Notify.show("Base URL 须以 http(s) 开头");
            return;
        }
        binding.btnFetchModels.setText("获取中…");
        binding.btnFetchModels.setClickable(false);
        new Thread(() -> {
            try {
                List<String> models = AiClient.fetchModels(baseUrl, apiKey);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    binding.btnFetchModels.setText("获取");
                    binding.btnFetchModels.setClickable(true);
                    if (models.isEmpty()) {
                        Notify.show("未获取到模型，请手动填写");
                        return;
                    }
                    showModelPicker(models);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    binding.btnFetchModels.setText("获取");
                    binding.btnFetchModels.setClickable(true);
                    Notify.show("获取失败：" + e.getMessage());
                });
            }
        }).start();
    }

    private void showModelPicker(List<String> models) {
        if (isFinishing() || isDestroyed()) return;
        CharSequence[] names = models.toArray(new CharSequence[0]);
        String cur = binding.etModel.getText().toString().trim();
        int selected = -1;
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).equals(cur)) {
                selected = i;
                break;
            }
        }
        ChoiceDialog.showSingle(getSupportFragmentManager(), "选择模型（" + models.size() + " 个）",
                names, selected, which -> binding.etModel.setText(models.get(which)));
    }

    private void testConnection() {
        if (testing) return;
        AiProvider p = collect(false);
        if (p == null) return;
        testing = true;
        binding.tvTest.setText("测试中…");
        new Thread(() -> {
            try {
                AiClient.ChatResult r = AiClient.testConnection(p);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    testing = false;
                    binding.tvTest.setText("测试连接");
                    Notify.show("连接成功" + (r.text.isEmpty() ? "" : "：" + r.text));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    testing = false;
                    binding.tvTest.setText("测试连接");
                    Notify.show("连接失败：" + e.getMessage());
                });
            }
        }).start();
    }

    /** 收集表单；validate=true 时做保存校验 */
    private AiProvider collect(boolean validate) {
        String name = binding.etName.getText().toString().trim();
        String baseUrl = binding.etBaseUrl.getText().toString().trim();
        String apiKey = binding.etApiKey.getText().toString().trim();
        String model = binding.etModel.getText().toString().trim();
        if (validate) {
            if (TextUtils.isEmpty(name)) {
                Notify.show("请填写名称");
                return null;
            }
            if (TextUtils.isEmpty(baseUrl) || !baseUrl.startsWith("http")) {
                Notify.show("Base URL 须以 http(s) 开头");
                return null;
            }
            if (TextUtils.isEmpty(apiKey)) {
                Notify.show("请填写 API Key");
                return null;
            }
            if (TextUtils.isEmpty(model)) {
                Notify.show("请填写模型名（可用\"获取\"按钮拉取）");
                return null;
            }
        }
        AiProvider p = editing != null ? editing : new AiProvider();
        p.name = name;
        p.protocol = protocol;
        p.baseUrl = baseUrl;
        p.apiKey = apiKey;
        p.model = model;
        p.kind = AiProvider.KIND_CHAT;
        p.enabled = true;
        return p;
    }

    private void save() {
        AiProvider p = collect(true);
        if (p == null) return;
        if (!AiStore.addOrUpdate(this, p)) {
            Notify.show("存储损坏，操作未保存");
            return;
        }
        Notify.show("已保存");
        finish();
    }
}
