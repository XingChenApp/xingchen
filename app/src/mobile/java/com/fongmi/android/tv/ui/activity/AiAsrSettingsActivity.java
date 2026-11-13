package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.ai.asr.AiAsrController;
import com.fongmi.android.tv.ai.asr.AiAsrEngine;
import com.fongmi.android.tv.ai.asr.AiAsrModelManager;
import com.fongmi.android.tv.ai.asr.AiAsrPluginManager;
import com.fongmi.android.tv.databinding.ActivityAiAsrSettingsBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.ChoiceDialog;
import com.fongmi.android.tv.utils.Notify;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 本地字幕设置页。
 *
 * 对标默影视"AI 服务"页：
 * - 实时识别语言：普通话模型（约31MB），点击下载/删除
 * - 识别引擎插件：按需下载（首次使用时自动下载）
 *
 * 本地识别不消耗云端 API 额度。
 */
public class AiAsrSettingsActivity extends BaseActivity {

    private ActivityAiAsrSettingsBinding binding;
    private boolean downloading = false;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, AiAsrSettingsActivity.class));
    }

    /** 是否支持（arm64/armv7） */
    public static boolean isSupported() {
        return AiAsrPluginManager.isSupported();
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityAiAsrSettingsBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.cardModel.setOnClickListener(v -> onModelClick());
        binding.cardPlugin.setOnClickListener(v -> onPluginClick());
        binding.cardDelete.setOnClickListener(v -> onDeleteClick());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        // 模型状态
        boolean modelReady = AiAsrModelManager.isModelReady(this);
        String modelSize = AiAsrModelManager.getModelSizeText(this);
        if (downloading) {
            // 下载中由进度回调更新
        } else if (modelReady) {
            binding.tvModelSub.setText("普通话（" + modelSize + "）· 已下载");
            binding.tvModelAction.setText("已下载");
        } else {
            binding.tvModelSub.setText("普通话（约31MB）· 未下载（点击下载）");
            binding.tvModelAction.setText("下载");
        }

        // 插件状态
        boolean pluginReady = AiAsrPluginManager.isPluginDownloaded(this);
        long pluginSize = AiAsrPluginManager.getPluginSize(this);
        if (pluginReady) {
            binding.tvPluginSub.setText("识别引擎已就绪（" + formatSize(pluginSize) + "）");
        } else {
            binding.tvPluginSub.setText("首次使用时自动下载（约23MB）");
        }

        // 删除按钮显隐
        binding.cardDelete.setVisibility(
                (modelReady || pluginReady) ? View.VISIBLE : View.GONE);
    }

    private void onModelClick() {
        if (downloading) {
            Notify.show("正在下载中，请稍候");
            return;
        }
        if (AiAsrModelManager.isModelReady(this)) {
            Notify.show("模型已下载");
            return;
        }
        downloading = true;
        binding.tvModelAction.setText("0%");
        binding.progressModel.setVisibility(View.VISIBLE);
        AiAsrModelManager.downloadModel(this, new AiAsrModelManager.Callback() {
            @Override
            public void onProgress(String stage, int percent) {
                binding.tvModelSub.setText(stage + " " + percent + "%");
                binding.tvModelAction.setText(percent + "%");
                binding.progressModel.setProgress(percent);
            }

            @Override
            public void onSuccess(java.io.File modelDir) {
                downloading = false;
                binding.progressModel.setVisibility(View.GONE);
                Notify.show("语音模型下载完成");
                refresh();
            }

            @Override
            public void onError(String msg) {
                downloading = false;
                binding.progressModel.setVisibility(View.GONE);
                Notify.show(msg);
                refresh();
            }
        });
    }

    private void onPluginClick() {
        if (AiAsrPluginManager.isPluginDownloaded(this)) {
            Notify.show("识别引擎已就绪");
            return;
        }
        Notify.show("识别引擎将在首次使用时自动下载");
    }

    private void onDeleteClick() {
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (AiAsrModelManager.isModelReady(this)) {
            items.add("删除语音模型（" + AiAsrModelManager.getModelSizeText(this) + "）");
            actions.add(() -> {
                AiAsrModelManager.deleteModel(this);
                Notify.show("已删除语音模型");
                refresh();
            });
        }
        if (AiAsrPluginManager.isPluginDownloaded(this)) {
            items.add("删除识别引擎（" + formatSize(AiAsrPluginManager.getPluginSize(this)) + "）");
            actions.add(() -> {
                // 删插件前先停引擎：别让识别中的喂入/收尾任务还攥着正被释放的桥
                AiAsrEngine.get().stop();
                AiAsrPluginManager.deletePlugin(this);
                Notify.show("已删除识别引擎");
                refresh();
            });
        }
        if (items.isEmpty()) return;
        CharSequence[] names = items.toArray(new CharSequence[0]);
        ChoiceDialog.showSingle(getSupportFragmentManager(), "删除本地文件",
                names, 0, which -> actions.get(which).run());
    }

    private String formatSize(long size) {
        if (size <= 0) return "0KB";
        if (size < 1024 * 1024) return (size / 1024) + "KB";
        return String.format("%.0fMB", size / 1024.0 / 1024.0);
    }
}
