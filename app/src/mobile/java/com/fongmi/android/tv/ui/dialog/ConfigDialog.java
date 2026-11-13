package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.databinding.DialogConfigBinding;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ConfigDialog extends BaseAlertDialog {

    private DialogConfigBinding binding;
    private android.app.ProgressDialog loadingDialog;
    private boolean append = true;
    private boolean edit;
    private String ori;
    private int type;
    private Config target;

    public static ConfigDialog create() {
        return new ConfigDialog();
    }

    public ConfigDialog vod() {
        type = 0;
        return this;
    }

    public ConfigDialog live() {
        type = 1;
        return this;
    }

    public ConfigDialog wall() {
        type = 2;
        return this;
    }

    public ConfigDialog edit() {
        edit = true;
        return this;
    }

    /**
     * Edit a specific config (e.g. from the line-management dialog),
     * instead of the currently active one.
     */
    public ConfigDialog edit(Config config) {
        edit = true;
        target = config;
        return this;
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogConfigBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        Config config = target != null ? target : getConfig();
        binding.title.setText(getDialogTitle());
        binding.positive.setText(edit ? R.string.dialog_edit : R.string.dialog_positive);
        String name = (edit && config != null && config.getName() != null) ? config.getName() : "";
        binding.name.setText(name);
        String url = (config != null && config.getUrl() != null) ? config.getUrl() : "";
        binding.url.setText(ori = url);
        binding.url.setSelection(TextUtils.isEmpty(ori) ? 0 : ori.length());
    }

    @Override
    protected void initEvent() {
        binding.negative.setOnClickListener(v -> dismiss());
        binding.positive.setOnClickListener(v -> onPositive());
        binding.choose.setEndIconOnClickListener(this::onChoose);
        binding.url.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.url.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive();
            return true;
        });
        binding.name.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive();
            return true;
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow();
        binding.url.requestFocus();
        if (binding != null && binding.getRoot() != null) {
            try {
                binding.getRoot().setBackground(requireActivity().getDrawable(R.drawable.dialog_glass));
            } catch (Exception e) {}
        }
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        // Notify host (e.g. line-management dialog) to refresh its list
        try {
            getParentFragmentManager().setFragmentResult("xsg_config_changed", new Bundle());
        } catch (Exception e) {}
    }

    private Config getConfig() {
        return switch (type) {
            case 0 -> VodConfig.get().getConfig();
            case 1 -> LiveConfig.get().getConfig();
            case 2 -> WallConfig.get().getConfig();
            default -> null;
        };
    }

    private int getTypeName() {
        return switch (type) {
            case 0 -> R.string.setting_vod;
            case 1 -> R.string.setting_live;
            case 2 -> R.string.setting_wall;
            default -> R.string.remote_trust_config_type;
        };
    }

    private String getDialogTitle() {
        int action = edit ? R.string.remote_trust_config_edit : R.string.remote_trust_config_add;
        return getString(R.string.setting_config_dialog_title, getString(action), getString(getTypeName()));
    }

    private void onChoose(View view) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
                    startActivity(intent);
                    com.fongmi.android.tv.utils.Notify.show("请授予所有文件访问权限后重试");
                } catch (Exception e) {
                    android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
                return;
            }
        }
        FileChooser.from(launcher).show();
    }

    private void detect(String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ttp://");
        } else if (append && "f".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ile://");
        } else if (append && "a".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ssets://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }

    private void onPositive() {
        String url = binding.url.getText().toString().trim();
        String name = binding.name.getText().toString().trim();
        if (url.isEmpty()) {
            // 空地址确认＝清槽位（2026-10-23 定案）：不再弹「没有url」拦人。
            clearSlot();
            if (getActivity() instanceof com.fongmi.android.tv.ui.activity.ConfigSourceActivity) {
                ((com.fongmi.android.tv.ui.activity.ConfigSourceActivity) getActivity()).updateCards();
            }
            dismiss();
            return;
        }
        Config config = saveConfig(url, name);
        loadConfig(config);
        if (getActivity() instanceof com.fongmi.android.tv.ui.activity.ConfigSourceActivity) {
            ((com.fongmi.android.tv.ui.activity.ConfigSourceActivity) getActivity()).updateCards();
        }
        dismiss();
    }

    /**
     * 删空确认的收尾＝「撤下」（2026-10-30 他亲口定案，覆盖旧「清槽位删行」口径）：
     * 点播接口（当前在用配置）与名单条目在此解耦——撤下只清在用状态，名单条目
     * （Config 行）必须保留：线路管理弹窗与设置页历史记录是同一份名单、两个入口，
     * 撤下后条目两处都还在，在名单里点「使用」能原样重新设为在用并生效。
     * 条目只有在名单内亲手删除（线路管理弹窗/历史记录区的删除、左滑、清空）才消失，
     * 本弹窗不许再删行。撤的是当前生效那条时：落 Config.markCleared 持久化「亲手清过」
     * （当前配置的取值是名单里 time 最新一行，只清内存态的话重进会被顶上来），
     * 再清内存态并按 type 发 ConfigEvent，首页落到空首页（与冷启未加线路同一个状态）；
     * 不自动切别的线路。撤的不是当前在用那条（编辑别的条目时清空了地址）同样什么都不动。
     * 全程静默：不弹任何提示（他亲口定的——撤下就是撤下，不需要提示）；
     * 「已删除」只许出现在名单内亲手删条目/清空名单的动作上。
     */
    private void clearSlot() {
        if (TextUtils.isEmpty(ori)) return;
        Config active = getConfig();
        boolean wasActive = active != null && ori.equals(active.getUrl());
        if (!wasActive) return;
        Config.markCleared(type);
        switch (type) {
            case 0 -> {
                VodConfig.get().clear().config(Config.create(0));
                com.fongmi.android.tv.event.ConfigEvent.vod();
            }
            case 1 -> {
                LiveConfig.get().clear().config(Config.create(1));
                com.fongmi.android.tv.event.ConfigEvent.live();
            }
            case 2 -> {
                WallConfig.get().config(Config.create(2));
                com.fongmi.android.tv.event.ConfigEvent.wall();
            }
        }
    }

    private Config saveConfig(String url, String name) {
        Config config;
        if (edit) {
            config = Config.find(ori, type).url(url).name(name).update();
        } else {
            Config exists = AppDatabase.get().getConfigDao().find(url, type);
            config = exists != null ? exists.name(name).update() : Config.create(type).url(url).name(name).update();
        }
        return config;
    }

    private void configureWindow() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        int width = Math.min(Math.round(ResUtil.getScreenWidth(requireContext()) * (land ? 0.58f : 0.92f)), ResUtil.dp2px(560));
        params.width = Math.max(width, ResUtil.dp2px(320));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String name = binding.name.getText().toString().trim();
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) return;
        String url = "file:/" + path.replace(Path.rootPath(), "");
        loadConfig(saveConfig(url, name));
        dismiss();
    });

    // D-09：按 type 分发加载，原先只有 type==1 走 Live、其余全走 Vod，
    // wall(type=2) 被误送 VodConfig.load。wall 走 SettingFragment/leanback 同款口径：先复位壁纸序号再 WallConfig.load。
    private void loadConfig(Config config) {
        com.fongmi.android.tv.impl.Callback callback = new com.fongmi.android.tv.impl.Callback() {
            @Override
            public void start() {
                // 输入这条路原先全程零提示：贴完配置只能干等，失败才弹字。
                // 照配置来源页切换线路的口径补加载中提示，只补提示、不动加载逻辑。
                dismissLoading();
                Activity activity = getActivity();
                if (activity == null) return;
                try {
                    android.app.ProgressDialog progress = new android.app.ProgressDialog(activity);
                    progress.setMessage("正在加载配置…");
                    progress.setCancelable(false);
                    progress.show();
                    loadingDialog = progress;
                } catch (Exception ignored) {
                }
            }

            @Override
            public void success() {
                dismissLoading();
            }

            @Override
            public void error(String msg) {
                dismissLoading();
                Notify.show(getFriendlyError(msg));
            }
        };
        if (type == 1) {
            LiveConfig.load(config, callback);
        } else if (type == 2) {
            Setting.putWall(0);
            WallConfig.load(config, callback);
        } else {
            VodConfig.load(config, callback);
        }
    }



    private void dismissLoading() {
        if (loadingDialog == null) return;
        try {
            loadingDialog.dismiss();
        } catch (Exception ignored) {
        }
        loadingDialog = null;
    }

    @Override
    public void onDestroy() {
        dismissLoading();
        super.onDestroy();
    }

    private String getFriendlyError(String msg) {
        if (msg == null) return "配置加载失败，请检查配置是否正确";
        // 猫源链的报错自带真实原因（取校验值的 HTTP 码/异常/内容不符），整句透传，
        // 不许被下面的关键字映射吞成通用文案——他看报错原文就能定案，不用再发日志。
        if (msg.contains("猫源")) return msg;
        if (msg.contains("MalformedJsonException")) return "配置文件格式错误，请检查 JSON 格式是否正确";
        if (msg.contains("UnknownHostException") || msg.contains("ConnectException")) return "网络连接失败，请检查网络设置";
        if (msg.contains("FileNotFoundException")) return "文件不存在，请检查路径是否正确";
        if (msg.contains("SocketTimeoutException")) return "连接超时，请稍后重试";
        if (msg.contains("JsonSyntaxException")) return "配置文件格式错误，请检查 JSON 格式是否正确";
        return "配置加载失败，请检查配置是否正确";
    }

}
