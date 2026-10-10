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
                onDelete(config);
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
                Config config = adapter.getItem(pos);
                AppDatabase.get().getConfigDao().delete(config.getUrl(), type);
                adapter.remove(pos);
                if (adapter.getItemCount() == 0) {
                    binding.empty.setVisibility(View.VISIBLE);
                    binding.recycler.setVisibility(View.GONE);
                }
            }
        });
        helper.attachToRecyclerView(binding.recycler);
    }

    private void initEvent() {
        binding.clear.setOnClickListener(v -> onClear());
        binding.add.setOnClickListener(v -> onAdd());
    }

    private void refreshList() {
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
            getChildFragmentManager().setFragmentResultListener("xsg_config_changed", this, (key, bundle) -> refreshList());
            ConfigDialog.create().vod().show(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 编辑指定线路：预填该线路的名称和地址，保存后刷新列表。
     */
    private void openEditor(Config config) {
        try {
            getChildFragmentManager().setFragmentResultListener("xsg_config_changed", this, (key, bundle) -> refreshList());
            ConfigDialog.create().vod().edit(config).show(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 删除指定线路（二次确认）。
     */
    private void onDelete(Config config) {
        boolean active = adapter != null && adapter.isActive(config);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("删除线路")
            .setMessage("确定要删除该线路吗？" + (active ? "\n（这是当前正在使用的线路）" : "") + "\n" + config.getDesc())
            .setPositiveButton("删除", (d, w) -> {
                AppDatabase.get().getConfigDao().delete(config.getUrl(), type);
                refreshList();
                Notify.show(active ? "已删除当前线路，请重新选择" : "已删除");
            })
            .setNegativeButton("取消", null)
            .create()
            .show();
    }

    private void onUse(Config config) {
        dismiss();
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
        android.content.Context ctx = fragment.requireContext();
        android.app.ProgressDialog progress = new android.app.ProgressDialog(ctx);
        progress.setMessage("正在切换线路…");
        progress.setCancelable(false);
        progress.show();
        com.fongmi.android.tv.api.config.VodConfig.load(config, new com.fongmi.android.tv.impl.Callback() {
            @Override
            public void success() {
                try { progress.dismiss(); } catch (Exception e) {}
                Notify.show("已切换");
            }
            @Override
            public void error(String msg) {
                try { progress.dismiss(); } catch (Exception e) {}
                Notify.show(msg == null || msg.isEmpty() ? "切换失败" : msg);
            }
        });
    }

    private void switchVodConfig(Config config) {
        switchVodConfig(this, config);
    }

    private void onClear() {
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("清空线路")
            .setMessage("确定要删除全部线路吗？")
            .setPositiveButton("确定", (d, w) -> {
                java.util.List<Config> items = AppDatabase.get().getConfigDao().findByType(type);
                for (Config c : items) AppDatabase.get().getConfigDao().delete(c.getUrl(), type);
                adapter = createAdapter(new java.util.ArrayList<>());
                binding.recycler.setAdapter(adapter);
                binding.empty.setVisibility(View.VISIBLE);
                binding.recycler.setVisibility(View.GONE);
                Notify.show("已清空");
            })
            .setNegativeButton("取消", null)
            .create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_glass);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            Window window = dialog.getWindow();
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