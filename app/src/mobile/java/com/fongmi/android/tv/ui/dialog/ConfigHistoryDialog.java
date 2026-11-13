package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.DialogConfigHistoryBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.ai.AiFloatManager;
import com.fongmi.android.tv.api.CatSource;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.ui.adapter.ConfigHistoryAdapter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.List;

public class ConfigHistoryDialog extends DialogFragment {

    private DialogConfigHistoryBinding binding;
    private ConfigHistoryAdapter adapter;
    private int type;
    private OnSwitchListener switchListener;

    public interface OnSwitchListener {
        void onSwitch(Config config);
    }

    public static ConfigHistoryDialog create(int type) {
        ConfigHistoryDialog dialog = new ConfigHistoryDialog();
        Bundle args = new Bundle();
        args.putInt("type", type);
        dialog.setArguments(args);
        return dialog;
    }

    public ConfigHistoryDialog setOnSwitchListener(OnSwitchListener listener) {
        this.switchListener = listener;
        return this;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) type = getArguments().getInt("type", 0);
        setStyle(STYLE_NO_TITLE, R.style.ThemeOverlay_WebHTV_LightDialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogConfigHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView();
        initEvent();
    }

    private ConfigHistoryAdapter createAdapter(List<Config> items) {
        ConfigHistoryAdapter adapter = new ConfigHistoryAdapter(items, this::onUse);
        adapter.setOnManageListener(new ConfigHistoryAdapter.OnManageListener() {
            @Override
            public void onEdit(Config config) {
                openEditor(config);
            }

            @Override
            public void onDelete(Config config) {
                ConfigHistoryDialog.this.onDelete(config);
            }
        });
        return adapter;
    }

    private void initView() {
        List<Config> items = AppDatabase.get().getConfigDao().findByType(type);
        binding.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        binding.recycler.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        adapter = createAdapter(items);
        binding.recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recycler.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                Config config = adapter.getItem(pos);
                boolean wasCurrent = isCurrentConfig(config);
                // 名单内亲手删条目走全清（Config.deleteEntry）：行、派生行、当前指针、
                // 已清空标记一次清干净，不许冷启再被剩余行顶替出已删来源的公告；
                // 猫源条目再清 bundle 落盘与在服务进程。
                boolean slotHit = Config.deleteEntry(config.getUrl(), type, wasCurrent);
                if (type == 0) CatSource.discardBundleIfOrphaned(config.getUrl());
                if (slotHit && type == 1) clearCurrentLiveConfig();
                adapter.remove(pos);
                if (adapter.getItemCount() == 0) {
                    binding.empty.setVisibility(View.VISIBLE);
                    binding.recycler.setVisibility(View.GONE);
                }
                // 左滑删行与确认删行同款处理：删的是当前线路也要切到剩余线路；
                // 删的是普通条目同样提示「已删除」（提示只许出现在真删除上，撤接口那条路不许有）
                if (wasCurrent) switchAfterActiveDelete();
                else Notify.show("已删除");
            }
        });
        helper.attachToRecyclerView(binding.recycler);
    }

    private void initEvent() {
        binding.clear.setOnClickListener(v -> onClear());
        binding.add.setOnClickListener(v -> onAdd());
    }

    private void refreshList() {
        if (binding == null) return;
        List<Config> items = AppDatabase.get().getConfigDao().findByType(type);
        binding.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        binding.recycler.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.updateItems(items);
    }

    /**
     * 添加线路：打开新增弹窗，关闭后刷新列表。
     */
    private void onAdd() {
        try {
            getChildFragmentManager().setFragmentResultListener("xsg_config_changed", getViewLifecycleOwner(), (key, bundle) -> refreshList());
            ConfigDialog.create().vod().show(this);
        } catch (Exception e) {
            e.printStackTrace();
            Notify.show("添加线路弹窗打开失败");
        }
    }

    /**
     * 编辑指定线路：预填该线路的名称和地址，保存后刷新列表。
     */
    private void openEditor(Config config) {
        try {
            getChildFragmentManager().setFragmentResultListener("xsg_config_changed", getViewLifecycleOwner(), (key, bundle) -> refreshList());
            ConfigDialog.create().vod().edit(config).show(this);
        } catch (Exception e) {
            e.printStackTrace();
            Notify.show("编辑线路弹窗打开失败");
        }
    }

    /**
     * 删除指定线路（二次确认）。删的是当前线路时切到剩余第一条（走 switchVodConfig，
     * 成功后 ConfigEvent → 首页刷新）；无剩余时把生效配置清回无配置状态，首页落空首页。
     * 注意：当前性按 VodConfig 的实际 url 判，不按 adapter.isActive。
     */
    private void onDelete(Config config) {
        boolean active = adapter != null && adapter.isActive(config);
        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_WebHTV_LightDialog)
            .setTitle("删除线路")
            .setMessage("确定要删除该线路吗？" + (active ? "\n（这是当前正在使用的线路）" : "") + "\n" + config.getDesc())
            .setPositiveButton("删除", (d, w) -> {
                boolean wasCurrent = isCurrentConfig(config);
                boolean slotHit = Config.deleteEntry(config.getUrl(), type, wasCurrent);
                if (type == 0) CatSource.discardBundleIfOrphaned(config.getUrl());
                if (slotHit && type == 1) clearCurrentLiveConfig();
                refreshList();
                if (wasCurrent) switchAfterActiveDelete();
                else Notify.show("已删除");
            })
            .setNegativeButton("取消", null)
            .create();
        if (dialog.getWindow() != null) dialog.getWindow().setWindowAnimations(R.style.HkDialogFade); // 统一淡入淡出 200ms
        dialog.show();
    }

    private boolean isCurrentVodConfig(Config config) {
        try {
            String url = VodConfig.get().getConfig().getUrl();
            return config.getUrl() != null && config.getUrl().equals(url);
        } catch (Exception e) {
            return false;
        }
    }

    /** 按本名单的类型判「删的是不是当前在用条目」（删除全清的 wasCurrent 入参）。 */
    private boolean isCurrentConfig(Config config) {
        if (type == 1) {
            try {
                String url = LiveConfig.get().getConfig().getUrl();
                return config.getUrl() != null && config.getUrl().equals(url);
            } catch (Exception e) {
                return false;
            }
        }
        return isCurrentVodConfig(config);
    }

    /** 删掉的正是当前直播条目：内存态清回无配置（Config.live() 已因已清空标记回空），不许停在已删配置上。 */
    private void clearCurrentLiveConfig() {
        LiveConfig.get().clear().config(Config.live());
        ConfigEvent.live();
    }

    private void switchAfterActiveDelete() {
        if (type != 0) {
            Notify.show("已删除当前线路，请重新选择");
            return;
        }
        List<Config> rest;
        try {
            rest = AppDatabase.get().getConfigDao().findByType(type);
        } catch (Exception e) {
            Notify.show("已删除当前线路，请重新选择");
            return;
        }
        if (rest == null || rest.isEmpty()) {
            clearCurrentVodConfig();
            Notify.show("已删除，暂无可用线路");
            return;
        }
        Config next = rest.get(0);
        if (switchListener != null) {
            dismiss();
            switchListener.onSwitch(next);
            return;
        }
        switchVodConfig(next);
    }

    /**
     * 删掉当前点播线路且一条不剩时的收尾：把 VodConfig 清回「无配置」状态
     * （库里已零行，Config.vod() 即冷启未加线路时的 Config.create(0)，与冷启同一个状态），
     * 再发与切线成功同一条 ConfigEvent 通路，首页据此落到空首页，
     * 不许停在已删线路的旧内容上。确认删与左滑删共用本收尾。
     */
    private void clearCurrentVodConfig() {
        VodConfig.get().clear().config(Config.vod());
        ConfigEvent.vod();
    }

    private void onUse(Config config) {
        dismiss();
        // 排队刷一次悬浮球，切线落定后球按最新页面状态恢复显示。
        try {
            App.post(() -> {
                try {
                    android.app.Activity top = App.activity();
                    if (top != null) AiFloatManager.get().refresh(top);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
        if (switchListener != null) {
            switchListener.onSwitch(config);
            return;
        }
        // Default: switch VOD config with loading UI, then notify home to refresh
        if (type == 0) {
            switchVodConfig(config);
        }
    }

    /**
     * Shared VOD config switch: async load with progress, posts ConfigEvent on success
     * (HomeActivity listens and refreshes). Used by long-press title shortcut too.
     */
    public static void switchVodConfig(androidx.fragment.app.Fragment fragment, Config config) {
        android.content.Context ctx = fragment.getContext();
        if (ctx == null) ctx = App.get();
        android.app.Activity activity = fragment.getActivity();
        android.app.ProgressDialog progress = null;
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            progress = new android.app.ProgressDialog(ctx);
            progress.setMessage("正在切换线路…");
            progress.setCancelable(false);
            progress.show();
        }
        final android.app.ProgressDialog shown = progress;
        com.fongmi.android.tv.api.config.VodConfig.load(config, new com.fongmi.android.tv.impl.Callback() {
            @Override
            public void success() {
                try { if (shown != null) shown.dismiss(); } catch (Exception e) {}
                Notify.show("已切换");
            }
            @Override
            public void error(String msg) {
                try { if (shown != null) shown.dismiss(); } catch (Exception e) {}
                Notify.show(msg == null || msg.isEmpty() ? "切换失败" : msg);
            }
        });
    }

    private void switchVodConfig(Config config) {
        switchVodConfig(this, config);
    }

    private void onClear() {
        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_WebHTV_LightDialog)
            .setTitle("清空线路")
            .setMessage("确定要删除全部线路吗？")
            .setPositiveButton("确定", (d, w) -> {
                java.util.List<Config> items = AppDatabase.get().getConfigDao().findByType(type);
                // 删前先抓当前生效线路的 url：清空会把当前线路一并删掉，
                // 它在被删之列时须同删到一条不剩的口径清回无配置，别留首页停在旧内容上。
                String currentUrl = null;
                try {
                    currentUrl = type == 1 ? LiveConfig.get().getConfig().getUrl() : VodConfig.get().getConfig().getUrl();
                } catch (Exception ignored) {
                }
                boolean currentCleared = false;
                if (currentUrl != null) {
                    for (Config c : items) {
                        if (currentUrl.equals(c.getUrl())) {
                            currentCleared = true;
                            break;
                        }
                    }
                }
                // 清空同样走全清：逐条 deleteEntry（当前条目落已清空标记、指针清掉），
                // 猫源 bundle 的孤儿清理等行全删完再做（引用检查要看最终名单）。
                for (Config c : items) Config.deleteEntry(c.getUrl(), type, c.getUrl() != null && c.getUrl().equals(currentUrl));
                if (type == 0) for (Config c : items) CatSource.discardBundleIfOrphaned(c.getUrl());
                if (type == 0 && currentCleared) clearCurrentVodConfig();
                if (type == 1 && currentCleared) clearCurrentLiveConfig();
                adapter = createAdapter(new java.util.ArrayList<>());
                binding.recycler.setAdapter(adapter);
                binding.empty.setVisibility(View.VISIBLE);
                binding.recycler.setVisibility(View.GONE);
                Notify.show("已清空");
            })
            .setNegativeButton("取消", null)
            .create();
        if (dialog.getWindow() != null) dialog.getWindow().setWindowAnimations(R.style.HkDialogFade); // 统一淡入淡出 200ms
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_glass);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            window.setWindowAnimations(R.style.HkDialogFade); // 统一淡入淡出 200ms
            WindowManager.LayoutParams params = window.getAttributes();
            boolean land = ResUtil.isLand(requireContext());
            int width = Math.min(Math.round(ResUtil.getScreenWidth(requireContext()) * (land ? 0.58f : 0.92f)), ResUtil.dp2px(560));
            params.width = Math.max(width, ResUtil.dp2px(320));
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.getDecorView().setPadding(0, 0, 0, 0);
            window.setAttributes(params);
            binding.getRoot().setBackgroundResource(R.drawable.dialog_glass);
        }
    }
}