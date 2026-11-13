package com.example.hikerview.ui.setting.office;

import android.content.Context;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.hikerview.ui.ActivityManager;
import com.example.hikerview.utils.ToastMgr;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

/**
 * 官 {@code com.example.hikerview.ui.setting.office.MoreSettingOfficer}
 * 的插件面移植：只落 hikerPop.checkByBiometric 实调的
 * {@code INSTANCE.checkByBiometric(Context, Function0)}，照官 jadx 逐行。
 * 官其余成员是官 App「更多设置」菜单机器（BaseSettingActivity 栈），
 * 非插件面、星辰无此栈，未落。
 */
public final class MoreSettingOfficer {

    public static final MoreSettingOfficer INSTANCE = new MoreSettingOfficer();

    private MoreSettingOfficer() {
    }

    public final void checkByBiometric(final Context context, final Function0<Unit> callback) {
        BiometricManager from = BiometricManager.from(context);
        int canAuthenticate = from.canAuthenticate();
        if (canAuthenticate == 0) {
            BiometricPrompt.PromptInfo build = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("生物识别认证")
                    .setSubtitle("请使用您的生物特征进行认证")
                    .setNegativeButtonText("取消")
                    .build();
            FragmentActivity currentActivity = (FragmentActivity) ActivityManager.getInstance().getCurrentActivity();
            new BiometricPrompt(currentActivity, ContextCompat.getMainExecutor(context),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            callback.invoke();
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            ToastMgr.shortBottomCenter(context, "生物识别出错：" + errString);
                        }
                    }).authenticate(build);
            return;
        }
        if (canAuthenticate == 1) {
            ToastMgr.shortBottomCenter(context, "生物识别当前不可用");
        } else if (canAuthenticate == 11) {
            ToastMgr.shortBottomCenter(context, "生物识别信息未录入");
        } else {
            if (canAuthenticate != 12) {
                return;
            }
            ToastMgr.shortBottomCenter(context, "当前设备没有对应硬件设施");
        }
    }
}
