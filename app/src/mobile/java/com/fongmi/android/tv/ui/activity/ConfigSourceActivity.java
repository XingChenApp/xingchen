package com.fongmi.android.tv.ui.activity;
import com.fongmi.android.tv.utils.ToastUtil;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.ActivityConfigSourceBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.adapter.ConfigHistoryAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.annotation.NonNull;
public class ConfigSourceActivity extends BaseActivity {
    private ActivityConfigSourceBinding binding;
    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, ConfigSourceActivity.class));
    }
    @Override
    protected ViewBinding getBinding() {
        binding = ActivityConfigSourceBinding.inflate(getLayoutInflater());
        return binding;
    }
    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.cardVod.setOnClickListener(v -> ConfigDialog.create().vod().show(getSupportFragmentManager(), null));
        binding.cardLive.setOnClickListener(v -> ConfigDialog.create().live().show(getSupportFragmentManager(), null));
    }
    @Override
    protected void onResume() {
        super.onResume();
        updateCards();
        updateHistory();
    }
    public void updateCards() {
        updateVodCard();
        updateLiveCard();
    }
    private void updateVodCard() {
        try {
            Config config = VodConfig.get().getConfig();
            String url = config == null ? "" : config.getUrl();
            String name = config == null ? "" : config.getName();
            if (url == null || url.isEmpty()) {
                binding.tvVodEmpty.setVisibility(View.VISIBLE);
                binding.tvVodName.setVisibility(View.GONE);
                binding.tvVodUrl.setVisibility(View.GONE);
            } else {
                binding.tvVodEmpty.setVisibility(View.GONE);
                if (name != null && !name.isEmpty()) {
                    binding.tvVodName.setText(name);
                    binding.tvVodName.setVisibility(View.VISIBLE);
                } else {
                    binding.tvVodName.setVisibility(View.GONE);
                }
                binding.tvVodUrl.setText(url);
                binding.tvVodUrl.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            binding.tvVodEmpty.setVisibility(View.VISIBLE);
            binding.tvVodName.setVisibility(View.GONE);
            binding.tvVodUrl.setVisibility(View.GONE);
        }
    }
    private void updateLiveCard() {
        try {
            Config config = LiveConfig.get().getConfig();
            String url = config == null ? "" : config.getUrl();
            String name = config == null ? "" : config.getName();
            if (url == null || url.isEmpty()) {
                binding.tvLiveEmpty.setVisibility(View.VISIBLE);
                binding.tvLiveName.setVisibility(View.GONE);
                binding.tvLiveUrl.setVisibility(View.GONE);
            } else {
                binding.tvLiveEmpty.setVisibility(View.GONE);
                if (name != null && !name.isEmpty()) {
                    binding.tvLiveName.setText(name);
                    binding.tvLiveName.setVisibility(View.VISIBLE);
                } else {
                    binding.tvLiveName.setVisibility(View.GONE);
                }
                binding.tvLiveUrl.setText(url);
                binding.tvLiveUrl.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            binding.tvLiveEmpty.setVisibility(View.VISIBLE);
            binding.tvLiveName.setVisibility(View.GONE);
            binding.tvLiveUrl.setVisibility(View.GONE);
        }
    }

    private ConfigHistoryAdapter historyAdapter;

    private void updateHistory() {
        try {
            android.content.Context ctx = this;
            java.util.List<com.fongmi.android.tv.bean.Config> items = com.fongmi.android.tv.db.AppDatabase.get().getConfigDao().findByType(0);
            if (binding.rvHistory == null) return;
            if (historyAdapter == null) {
                historyAdapter = new ConfigHistoryAdapter(items, config -> {
                    // Use: set as current VOD config (async load, refresh UI on success)
                    switchVodConfig(config);
                });
                binding.rvHistory.setLayoutManager(new LinearLayoutManager(ctx));
                binding.rvHistory.setAdapter(historyAdapter);
                ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }
                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                        int pos = vh.getAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION) return;
                        com.fongmi.android.tv.bean.Config c = historyAdapter.getItem(pos);
                        // 名单内亲手删条目走全清（Config.deleteEntry）：行、派生直播行、
                        // 当前指针、已清空标记一次清干净；删中当前条目时内存态一起清，
                        // 不许冷启被剩余行顶替出已删来源的公告，也不许本会话还供着已删配置。
                        boolean wasCurrent = c.getUrl() != null && c.getUrl().equals(VodConfig.get().getConfig().getUrl());
                        boolean slotHit = com.fongmi.android.tv.bean.Config.deleteEntry(c.getUrl(), 0, wasCurrent);
                        com.fongmi.android.tv.api.CatSource.discardBundleIfOrphaned(c.getUrl());
                        historyAdapter.remove(pos);
                        if (slotHit) clearCurrentVod();
                        com.fongmi.android.tv.utils.Notify.show("已删除");
                    }
                });
                helper.attachToRecyclerView(binding.rvHistory);
                binding.tvHistoryClear.setOnClickListener(v -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx, com.fongmi.android.tv.R.style.ThemeOverlay_WebHTV_LightDialog)
                        .setTitle("清空历史")
                        .setMessage("确定要清空全部历史记录吗？")
                        .setPositiveButton("确定", (d, w) -> {
                            // 清空同样走全清：逐条 deleteEntry，当前条目在内时落已清空标记、
                            // 清内存态；猫源 bundle 孤儿清理等行全删完再做。
                            String currentUrl = VodConfig.get().getConfig().getUrl();
                            boolean slotHit = false;
                            java.util.List<com.fongmi.android.tv.bean.Config> all = com.fongmi.android.tv.db.AppDatabase.get().getConfigDao().findByType(0);
                            for (com.fongmi.android.tv.bean.Config c : all) {
                                slotHit |= com.fongmi.android.tv.bean.Config.deleteEntry(c.getUrl(), 0, c.getUrl() != null && c.getUrl().equals(currentUrl));
                            }
                            for (com.fongmi.android.tv.bean.Config c : all) com.fongmi.android.tv.api.CatSource.discardBundleIfOrphaned(c.getUrl());
                            if (slotHit) clearCurrentVod();
                            updateHistory();
                            com.fongmi.android.tv.utils.Notify.show("已清空");
                        })
                        .setNegativeButton("取消", null)
                        .show();
                });
            } else {
                // Refresh data
                historyAdapter = new ConfigHistoryAdapter(items, config -> {
                    switchVodConfig(config);
                });
                binding.rvHistory.setAdapter(historyAdapter);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 名单里删掉的正是当前点播条目：内存态清回无配置（Config.vod() 已因
     * deleteEntry 落的已清空标记回空，与冷启同一个状态），发与切线成功同一条
     * ConfigEvent 通路，首页落到空首页，不许停在已删配置的旧内容上。
     */
    private void clearCurrentVod() {
        VodConfig.get().clear().config(com.fongmi.android.tv.bean.Config.vod());
        com.fongmi.android.tv.event.ConfigEvent.vod();
    }

    /**
     * Switch to the given VOD config. VodConfig.load() is async: show a loading
     * indicator, and refresh the cards/history only after the load succeeds.
     */
    private void switchVodConfig(com.fongmi.android.tv.bean.Config config) {
        android.app.ProgressDialog progress = new android.app.ProgressDialog(this, com.fongmi.android.tv.R.style.Theme_WebHTV_LightDialog);
        progress.setMessage("正在切换线路…");
        progress.setCancelable(false);
        progress.show();
        com.fongmi.android.tv.api.config.VodConfig.load(config, new com.fongmi.android.tv.impl.Callback() {
            @Override
            public void success() {
                try { progress.dismiss(); } catch (Exception e) {}
                updateCards();
                updateHistory();
            }
            @Override
            public void error(String msg) {
                try { progress.dismiss(); } catch (Exception e) {}
                ToastUtil.show(ConfigSourceActivity.this,
                        msg == null || msg.isEmpty() ? "切换失败" : msg,
                        android.widget.Toast.LENGTH_SHORT);
                updateCards();
            }
        });
    }

}