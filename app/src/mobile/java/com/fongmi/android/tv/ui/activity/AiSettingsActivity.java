package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.ai.AiProvider;
import com.fongmi.android.tv.ai.AiStore;
import com.fongmi.android.tv.databinding.ActivityAiSettingsBinding;
import com.fongmi.android.tv.databinding.ItemAiProviderBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.ChoiceDialog;
import com.fongmi.android.tv.utils.Notify;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 设置主页：服务商列表、默认选择、各功能绑定。
 */
public class AiSettingsActivity extends BaseActivity {

    private ActivityAiSettingsBinding binding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, AiSettingsActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityAiSettingsBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.cardAdd.setOnClickListener(v -> AiProviderEditActivity.start(this, null));
        binding.cardDefault.setOnClickListener(v -> showDefaultPicker());
        binding.cardFeatureSearch.setOnClickListener(v -> showFeaturePicker(AiStore.FEATURE_SMART_SEARCH, "智能搜片"));
        binding.cardFeatureSubtitle.setOnClickListener(v -> showFeaturePicker(AiStore.FEATURE_SUBTITLE, "字幕翻译"));
        binding.cardFeatureQa.setOnClickListener(v -> showFeaturePicker(AiStore.FEATURE_PLOT_QA, "剧情问答"));
        binding.cardFeatureVoice.setOnClickListener(v -> showFeaturePicker(AiStore.FEATURE_VOICE, "语音控制"));
        binding.cardAiAsr.setOnClickListener(v -> {
            if (AiAsrSettingsActivity.isSupported()) {
                AiAsrSettingsActivity.start(this);
            } else {
                Notify.show("当前设备不支持本地语音识别");
            }
        });
        binding.swFloat.setOnCheckedChangeListener((btn, checked) -> {
            AiStore.setFloatEnabled(this, checked);
            com.fongmi.android.tv.ai.AiFloatManager.get().refresh(this);
            Notify.show(checked ? "AI 悬浮球已开启" : "AI 悬浮球已关闭");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        binding.swFloat.setChecked(AiStore.isFloatEnabled(this));
        refreshDefault();
        refreshList();
        refreshFeatures();
        refreshAiAsr();
    }

    private void refreshDefault() {
        AiProvider def = AiStore.getDefault(this);
        if (def == null) {
            binding.tvDefaultName.setText("未配置");
            binding.tvDefaultModel.setText("点击添加 AI 服务商");
        } else {
            binding.tvDefaultName.setText(def.name);
            binding.tvDefaultModel.setText(def.displayModel());
        }
    }

    private void refreshList() {
        binding.containerProviders.removeAllViews();
        List<AiProvider> list = AiStore.getProviders(this);
        binding.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        String defaultId = AiStore.getDefaultId(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (AiProvider p : list) {
            ItemAiProviderBinding ib = ItemAiProviderBinding.inflate(inflater, binding.containerProviders, false);
            ib.tvName.setText(p.name);
            ib.tvModel.setText(p.displayModel());
            ib.vStatus.setBackgroundResource(p.enabled
                    ? com.fongmi.android.tv.R.drawable.xc_dot_green
                    : com.fongmi.android.tv.R.drawable.xc_dot_gray);
            ib.tvDefaultBadge.setVisibility(p.id.equals(defaultId) ? View.VISIBLE : View.GONE);
            ib.getRoot().setOnClickListener(v -> AiProviderEditActivity.start(this, p.id));
            ib.ivMenu.setOnClickListener(v -> showProviderMenu(p));
            binding.containerProviders.addView(ib.getRoot());
        }
    }

    private void showProviderMenu(AiProvider p) {
        List<String> items = new ArrayList<>();
        items.add(p.enabled ? "禁用" : "启用");
        items.add("测试连接");
        items.add("删除");
        ChoiceDialog.showSingle(getSupportFragmentManager(), p.name,
                items.toArray(new CharSequence[0]), -1, which -> {
                    if (which == 0) toggleEnabled(p);
                    else if (which == 1) testConnection(p);
                    else if (which == 2) confirmDelete(p);
                });
    }

    private void toggleEnabled(AiProvider p) {
        p.enabled = !p.enabled;
        if (!AiStore.addOrUpdate(this, p)) {
            p.enabled = !p.enabled; // 没存上：内存里的状态也退回去，与存储保持一致
            Notify.show("存储损坏，操作未保存");
            refresh();
            return;
        }
        refresh();
        Notify.show(p.enabled ? "已启用" : "已禁用");
    }

    private void testConnection(AiProvider p) {
        Notify.show("正在测试 " + p.name + "…");
        new Thread(() -> {
            try {
                com.fongmi.android.tv.ai.AiClient.ChatResult r =
                        com.fongmi.android.tv.ai.AiClient.testConnection(p);
                runOnUiThread(() -> Notify.show("连接成功" + (r.text.isEmpty() ? "" : "：" + r.text)));
            } catch (Exception e) {
                runOnUiThread(() -> Notify.show("连接失败：" + e.getMessage()));
            }
        }).start();
    }

    private void confirmDelete(AiProvider p) {
        ChoiceDialog.showSingle(getSupportFragmentManager(), "删除「" + p.name + "」？",
                new CharSequence[]{"确认删除", "取消"}, 1, which -> {
                    if (which == 0) {
                        if (!AiStore.delete(this, p.id)) {
                            Notify.show("存储损坏，操作未保存");
                            return;
                        }
                        refresh();
                        Notify.show("已删除");
                    }
                });
    }

    private void showDefaultPicker() {
        List<AiProvider> chats = AiStore.enabledChats(this);
        if (chats.isEmpty()) {
            Notify.show("请先添加 AI 服务商");
            return;
        }
        CharSequence[] names = new CharSequence[chats.size()];
        String cur = AiStore.getDefaultId(this);
        int selected = 0;
        for (int i = 0; i < chats.size(); i++) {
            names[i] = chats.get(i).name + "（" + chats.get(i).displayModel() + "）";
            if (chats.get(i).id.equals(cur)) selected = i;
        }
        ChoiceDialog.showSingle(getSupportFragmentManager(), "默认服务商", names, selected, which -> {
            AiStore.setDefaultId(this, chats.get(which).id);
            refresh();
        });
    }

    private void showFeaturePicker(String feature, String featureName) {
        List<AiProvider> chats = AiStore.enabledChats(this);
        if (chats.isEmpty()) {
            Notify.show("请先添加 AI 服务商");
            return;
        }
        List<String> names = new ArrayList<>();
        names.add("跟随默认");
        for (AiProvider p : chats) names.add(p.name + "（" + p.displayModel() + "）");
        String cur = AiStore.getFeatureProviderId(this, feature);
        int selected = 0;
        for (int i = 0; i < chats.size(); i++) {
            if (chats.get(i).id.equals(cur)) {
                selected = i + 1;
                break;
            }
        }
        ChoiceDialog.showSingle(getSupportFragmentManager(), featureName + " 使用",
                names.toArray(new CharSequence[0]), selected, which -> {
                    AiStore.setFeatureProviderId(this, feature, which == 0 ? "" : chats.get(which - 1).id);
                    refreshFeatures();
                });
    }

    private void refreshFeatures() {
        binding.tvFeatureSearchSub.setText(featureSub(AiStore.FEATURE_SMART_SEARCH));
        binding.tvFeatureSubtitleSub.setText(featureSub(AiStore.FEATURE_SUBTITLE));
        binding.tvFeatureQaSub.setText(featureSub(AiStore.FEATURE_PLOT_QA));
        binding.tvFeatureVoiceSub.setText(featureSub(AiStore.FEATURE_VOICE));
    }

    private void refreshAiAsr() {
        // 本地字幕入口：只在支持的设备上显示（自 FeaturesActivity 迁入，口径不变）
        boolean supported = AiAsrSettingsActivity.isSupported();
        binding.cardAiAsr.setVisibility(supported ? View.VISIBLE : View.GONE);
        if (supported) {
            boolean modelReady = com.fongmi.android.tv.ai.asr.AiAsrModelManager.isModelReady(this);
            boolean enabled = com.fongmi.android.tv.ai.asr.AiAsrController.get().isEnabled(this);
            if (enabled) {
                binding.tvAiAsrSub.setText("已开启 · 本地识别中");
            } else if (modelReady) {
                binding.tvAiAsrSub.setText("模型已下载 · 点击管理");
            } else {
                binding.tvAiAsrSub.setText("无字幕时本地识别");
            }
        }
    }

    private String featureSub(String feature) {
        String id = AiStore.getFeatureProviderId(this, feature);
        if (id == null || id.isEmpty()) return "跟随默认";
        AiProvider p = AiStore.findById(this, id);
        return p == null ? "跟随默认" : p.name;
    }
}
