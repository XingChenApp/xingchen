package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityProxyBinding;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ProxyActivity extends BaseActivity {

    private ActivityProxyBinding binding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, ProxyActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityProxyBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.swProxy.setChecked(Setting.isShellProxy());
        binding.swProxy.setOnCheckedChangeListener((btn, checked) -> {
            Setting.putShellProxy(checked);
            refreshSubs();
            Notify.show(checked ? "代理已启用" : "代理已关闭");
        });
        binding.cardProxyUrl.setOnClickListener(v -> showUrlInput());
        binding.cardProxyRules.setOnClickListener(v -> showRulesInput());
        refreshSubs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSubs();
    }

    private void refreshSubs() {
        binding.swProxy.setChecked(Setting.isShellProxy());
        String url = Setting.getShellProxyUrl();
        binding.tvProxyUrlSub.setText(TextUtils.isEmpty(url) ? "未配置" : url);
        String rules = Setting.getShellProxyRules();
        if (TextUtils.isEmpty(rules)) {
            binding.tvProxyRulesSub.setText("未配置");
        } else {
            int count = ProxySetting.count();
            binding.tvProxyRulesSub.setText(count > 0 ? count + " 条规则" : "规则无效");
        }
    }

    private void showUrlInput() {
        EditText input = new EditText(this);
        input.setText(Setting.getShellProxyUrl());
        input.setSingleLine(true);
        input.setHint("http://127.0.0.1:8080");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle("代理地址")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(url) && !ProxySetting.isValid(url)) {
                        Notify.show("代理地址无效");
                        return;
                    }
                    Setting.putShellProxyUrl(url);
                    refreshSubs();
                    Notify.show("已保存");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRulesInput() {
        EditText input = new EditText(this);
        input.setText(Setting.getShellProxyRules());
        input.setMinLines(6);
        input.setHint("每行一条：域名 代理地址\n例：example.com http://127.0.0.1:8080");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle("代理规则")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String rules = input.getText().toString().trim();
                    if (!ProxySetting.isValidRules(rules, Setting.getShellProxyUrl())) {
                        Notify.show("规则无效");
                        return;
                    }
                    Setting.putShellProxyRules(rules);
                    refreshSubs();
                    Notify.show("已保存");
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
