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

    public static ConfigHistoryDialog create(int type) {
        ConfigHistoryDialog dialog = new ConfigHistoryDialog();
        Bundle args = new Bundle();
        args.putInt("type", type);
        dialog.setArguments(args);
        return dialog;
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

    private void initView() {
        List<Config> items = AppDatabase.get().getConfigDao().findByType(type);
        binding.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        binding.recycler.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        adapter = new ConfigHistoryAdapter(items, this::onUse);
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
    }

    private void onUse(Config config) {
        dismiss();
        if (getParentFragmentManager() != null) {
            ConfigDialog.create().vod().edit().show(getParentFragmentManager(), "config");
        }
    }

    private void onClear() {
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("清空历史")
            .setMessage("确定要清空全部历史记录吗？")
            .setPositiveButton("确定", (d, w) -> {
                java.util.List<Config> items = AppDatabase.get().getConfigDao().findByType(type);
                for (Config c : items) AppDatabase.get().getConfigDao().delete(c.getUrl(), type);
                adapter = new ConfigHistoryAdapter(java.util.Collections.emptyList(), this::onUse);
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
