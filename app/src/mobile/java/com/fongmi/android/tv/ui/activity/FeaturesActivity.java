package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.clash.ClashSettings;
import com.fongmi.android.tv.clash.KernelDownloader;
import com.fongmi.android.tv.clash.NodeStore;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityFeaturesBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;

public class FeaturesActivity extends BaseActivity {

    private static final String PREFS = "xingchen";
    private static final String KEY_MINIAPP = "xingchen.miniapp_enabled";

    private ActivityFeaturesBinding binding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, FeaturesActivity.class));
    }

    public static boolean isMiniAppEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MINIAPP, false);
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityFeaturesBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        refreshAdBlock();
        refreshNode();
        refreshTmdb();
        refreshPanLogin();
        refreshMiniApp();
        refreshAi();
        refreshWebHomeFullscreen();
        binding.swWebhomeFullscreen.setOnCheckedChangeListener((btn, checked) -> {
            Setting.putWebHomeFullscreen(checked);
            refreshWebHomeFullscreen();
        });
        binding.swMiniapp.setOnCheckedChangeListener((btn, checked) -> {
            getPrefs().edit().putBoolean(KEY_MINIAPP, checked).apply();
            refreshMiniApp();
            Notify.show(checked ? "小程序已启用" : "小程序已关闭");
        });
        binding.cardAdblock.setOnClickListener(v -> AdBlockActivity.start(this));
        binding.cardNode.setOnClickListener(v -> NodeManageActivity.start(this));
        binding.cardTmdb.setOnClickListener(v -> TmdbActivity.start(this));
        binding.cardPanlogin.setOnClickListener(v -> PanLoginActivity.start(this));
        binding.cardAi.setOnClickListener(v -> AiSettingsActivity.start(this));
        // X-04 兜底入口：小程序丢了底栏格时（实验室优先占格），功能页这张卡点开也能进小程序首页，
        // 不靠底栏也能到；未启用时点卡无动作，开关才是启用控制。
        binding.cardMiniapp.setOnClickListener(v -> {
            if (isMiniAppEnabled(this)) HkPageActivity.start(this);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAdBlock();
        refreshNode();
        refreshTmdb();
        refreshPanLogin();
        refreshMiniApp();
        refreshAi();
        refreshWebHomeFullscreen();
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void refreshAdBlock() {
        boolean enabled = AdBlockActivity.isEnabled(this);
        binding.tvAdblockSub.setText(enabled ? "已启用" : "未启用");
    }

    private void refreshNode() {
        boolean on = ClashSettings.isEnabled(this);
        if (!on) {
            binding.tvNodeSub.setText("未启用");
            return;
        }
        if (!KernelDownloader.isReady(this)) {
            binding.tvNodeSub.setText("开·内核未下载");
            return;
        }
        NodeStore.Node n = NodeStore.get(this).getSelected(this);
        binding.tvNodeSub.setText("开·" + (n == null || n.name == null || n.name.isEmpty() ? "未选节点" : n.name));
    }

    private void refreshTmdb() {
        String key = TmdbActivity.getApiKey(this);
        binding.tvTmdbSub.setText(key == null || key.isEmpty() ? "未配置" : "已配置");
    }

    private void refreshPanLogin() {
        binding.tvPanloginSub.setText(PanLoginActivity.loginSummary(this));
    }

    private void refreshMiniApp() {
        boolean enabled = isMiniAppEnabled(this);
        binding.swMiniapp.setChecked(enabled);
        binding.tvMiniappSub.setText(enabled ? "已启用，点击打开" : "未启用");
    }

    private void refreshAi() {
        binding.tvAiSub.setText(com.fongmi.android.tv.ai.AiStore.summary(this));
    }

    private void refreshWebHomeFullscreen() {
        boolean on = Setting.isWebHomeFullscreen();
        binding.swWebhomeFullscreen.setChecked(on);
        binding.tvWebhomeFullscreenSub.setText(on ? R.string.setting_enable : R.string.setting_disable);
    }
}
