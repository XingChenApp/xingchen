package com.fongmi.android.tv.utils;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ViewProgressBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class Notify {

    public static final String DEFAULT = "default";
    public static final int ID = 9527;
    private AlertDialog mDialog;

    private static class Loader {
        static volatile Notify INSTANCE = new Notify();
    }

    private static Notify get() {
        return Loader.INSTANCE;
    }

    public static void createChannel() {
        NotificationManagerCompat notifyMgr = NotificationManagerCompat.from(App.get());
        notifyMgr.createNotificationChannel(new NotificationChannelCompat.Builder(DEFAULT, NotificationManagerCompat.IMPORTANCE_LOW).setName("TV").build());
    }

    /** 媒体会话通知所用渠道 id（media3 DefaultMediaNotificationProvider 的 DEFAULT_CHANNEL_ID 字面） */
    public static final String MEDIA_CHANNEL_ID = "default_channel_id";

    /** 通知自检：可用 */
    public static final int NOTIF_OK = 0;
    /** 通知自检：运行时权限未授权（Android 13+） */
    public static final int NOTIF_NO_PERMISSION = 1;
    /** 通知自检：应用通知总开关被关闭 */
    public static final int NOTIF_APP_OFF = 2;
    /** 通知自检：媒体渠道重要性被关（IMPORTANCE_NONE） */
    public static final int NOTIF_CHANNEL_OFF = 3;
    /** 通知自检：媒体渠道尚不存在（未就绪） */
    public static final int NOTIF_CHANNEL_MISSING = 4;

    /**
     * 通知可用性自检（只读系统状态，轻量同步，不改任何通知设置）：渠道一经创建
     * 参数 App 改不动，只能检测。顺序：运行时权限 → 应用总开关 → 媒体渠道
     * （不存在按未就绪论、重要性 NONE 按被关论），任一命中即不可用。
     */
    public static int notificationBlockReason(Context context) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return NOTIF_NO_PERMISSION;
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return NOTIF_APP_OFF;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = manager == null ? null : manager.getNotificationChannel(MEDIA_CHANNEL_ID);
            if (channel == null) return NOTIF_CHANNEL_MISSING;
            if (channel.getImportance() == NotificationManager.IMPORTANCE_NONE) return NOTIF_CHANNEL_OFF;
        }
        return NOTIF_OK;
    }

    /** 跳系统「应用通知」设置页（本应用那页）；个别系统打不开时退到应用详情页。 */
    public static void openNotificationSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            context.startActivity(intent);
        } catch (Throwable ignored) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                context.startActivity(intent);
            } catch (Throwable ignored2) {
            }
        }
    }

    public static String getError(int resId, Throwable e) {
        if (TextUtils.isEmpty(e.getMessage())) return ResUtil.getString(resId);
        return ResUtil.getString(resId) + "\n" + e.getMessage();
    }

    public static void show(Notification notification) {
        if (ContextCompat.checkSelfPermission(App.get(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManagerCompat.from(App.get()).notify(ID, notification);
    }

    public static void show(int resId) {
        if (resId != 0) show(ResUtil.getString(resId));
    }

    public static void show(String text) {
        if (TextUtils.isEmpty(text)) return;
        // 全应用提示统一走 ToastUtil（实底白字），系统浅色 Toast 在亮色画面上看不清
        ToastUtil.show(App.get(), text, android.widget.Toast.LENGTH_LONG);
    }

    public static void progress(Context context) {
        dismiss();
        get().create(context);
    }

    public static void dismiss() {
        try {
            if (get().mDialog != null) get().mDialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    public static void dismissToast() {
        ToastUtil.dismiss();
    }

    private void create(Context context) {
        ViewProgressBinding binding = ViewProgressBinding.inflate(LayoutInflater.from(context));
        mDialog = new MaterialAlertDialogBuilder(context).setView(binding.getRoot()).create();
        mDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        // 进出统一口径：淡入淡出 200ms（与 BaseAlertDialog 基类同一套 HkDialogFade）
        if (mDialog.getWindow() != null) mDialog.getWindow().setWindowAnimations(R.style.HkDialogFade);
        mDialog.show();
    }
}
