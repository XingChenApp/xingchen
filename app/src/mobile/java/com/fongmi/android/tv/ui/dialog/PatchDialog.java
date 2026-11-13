package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogPatchUpdateBinding;
import com.fongmi.android.tv.patch.PatchInstaller;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

import java.io.File;

/**
 * 补丁更新弹窗：五门进度逐门亮状态，失败明说停在哪一门、为什么。
 * 星辰本体浅面毛玻璃皮（与设置页同一家），字与底对比按看得清口径。
 * 链未走完不许关——半截状态不许摆给用户；失败后现有版本不受任何影响。
 */
public final class PatchDialog {

    private PatchDialog() {
    }

    public static void show(FragmentActivity activity, Uri patchUri) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        DialogPatchUpdateBinding binding = DialogPatchUpdateBinding.inflate(LayoutInflater.from(activity));
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(binding.getRoot());
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        TextView[] states = {binding.stageState1, binding.stageState2, binding.stageState3, binding.stageState4, binding.stageState5};
        boolean[] done = {false};
        binding.patchClose.setOnClickListener(v -> {
            if (done[0]) dialog.dismiss();
        });
        binding.patchInfo.setText("正在读取补丁文件…");

        dialog.show();
        configureWindow(activity, dialog);

        PatchInstaller.start(activity, patchUri, new PatchInstaller.Listener() {
            @Override
            public void onMeta(String fromToText) {
                binding.patchInfo.setText("将从 " + fromToText + " 更新");
            }

            @Override
            public void onStage(int stage, int state) {
                if (stage < 1 || stage > states.length) return;
                TextView view = states[stage - 1];
                if (state == PatchInstaller.STATE_RUNNING) {
                    view.setText("进行中");
                    view.setTextColor(0xFF8A5A00);
                } else if (state == PatchInstaller.STATE_PASS) {
                    view.setText("通过");
                    view.setTextColor(0xFF1E7A34);
                } else if (state == PatchInstaller.STATE_FAIL) {
                    view.setText("没过");
                    view.setTextColor(0xFFB3261E);
                }
            }

            @Override
            public void onDetail(String text) {
                binding.patchDetail.setText(text);
            }

            @Override
            public void onSuccess(File outApk) {
                done[0] = true;
                binding.patchProgress.setVisibility(View.GONE);
                binding.patchDetail.setText("拼好了，正在打开安装界面，按提示点安装就行。");
                binding.patchClose.setText("知道了");
                try {
                    PatchInstaller.launchInstall(activity, outApk);
                    dialog.dismiss();
                    Notify.show("拼包完成，按提示安装");
                } catch (Exception e) {
                    binding.patchError.setVisibility(View.VISIBLE);
                    binding.patchError.setText("包拼好了，但安装界面没打开起来（" + e.getMessage() + "）。把这句原样发我看。");
                }
            }

            @Override
            public void onFailure(int stage, String reason) {
                done[0] = true;
                binding.patchProgress.setVisibility(View.GONE);
                binding.patchDetail.setText("");
                binding.patchError.setVisibility(View.VISIBLE);
                binding.patchError.setText("停在第 " + stage + " 步：" + reason + "\n你现在装的版本一点没动。");
                binding.patchClose.setText("知道了");
            }
        });
    }

    private static void configureWindow(FragmentActivity activity, Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(activity) * (ResUtil.isLand(activity) ? 0.62f : 0.92f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        params.dimAmount = 0.5f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
        window.setWindowAnimations(R.style.HkDialogFade);
    }
}
