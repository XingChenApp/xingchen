package com.fongmi.android.tv.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.activity.DownloadActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.download.DownloadManager;
import com.fongmi.android.tv.utils.download.DownloadTask;

import java.util.List;

/**
 * 下载前台服务（下载外壳件 2）：只负责把进程抬到前台优先级＋挂聚合
 * 通知，下载线程仍归 DownloadManager/下载器所有，本服务不接管线程。
 * 生命周期契约：服务存在 ⟺ 至少 1 个任务在途（DOWNLOADING/MERGING）；
 * DownloadManager 每次状态迁移后调 sync() 对齐。START_NOT_STICKY、
 * 不注册开机广播、不定时唤醒、不自启——进程被杀任务靠落盘恢复翻
 * 暂停，等他点继续，不自动续传。
 */
public class DownloadService extends Service {

    private static final String ACTION_PAUSE_ALL = "com.fongmi.android.tv.download.PAUSE_ALL";
    /** Notify.ID 偏移占用：+1 DLNA、+2 ManageService、+5 RemoteAgent，本服务 +3 */
    private static final int NOTIFICATION_ID = Notify.ID + 3;

    private static volatile boolean started;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!started) return;
            refreshNotification();
            App.post(this, 1000);
        }
    };

    /** Manager 状态迁移后调：有在途任务确保服务在、无在途确保服务停 */
    public static void sync() {
        boolean anyActive = false;
        for (DownloadTask task : DownloadManager.get().getTasks()) {
            if (task.getStatus() == DownloadTask.STATUS_DOWNLOADING
                    || task.getStatus() == DownloadTask.STATUS_MERGING) {
                anyActive = true;
                break;
            }
        }
        Context context = App.get();
        if (anyActive && !started) {
            started = true;
            try {
                ContextCompat.startForegroundService(context,
                        new Intent(context, DownloadService.class));
            } catch (Exception e) {
                // 前台服务启动被系统拒（极端后台态）：下载退化为现行
                // 进程内线程继续，不许因为服务起不来把下载拖死。
                started = false;
            }
        } else if (!anyActive && started) {
            started = false;
            context.stopService(new Intent(context, DownloadService.class));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        started = true;
        // 件 1 恢复触发点之一（与 DownloadActivity 首开谁先到谁跑，幂等）
        DownloadManager.get().restore();
        // startForegroundService 后 5 秒内必须 startForeground：先挂占位通知
        startForegroundCompat(buildNotification());
        App.post(ticker, 1000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_PAUSE_ALL.equals(intent.getAction())) {
            DownloadManager.get().pauseAllActive();
        }
        refreshNotification();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        started = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void refreshNotification() {
        if (!started) return;
        try {
            startForegroundCompat(buildNotification());
        } catch (Exception ignored) {
            // 刷新失败不许拖垮服务本体；下一拍还会再刷
        }
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    /** 聚合一条通知（不逐任务刷屏）：N 个任务 · 总进度 · 合计速度（件 4） */
    private Notification buildNotification() {
        List<DownloadTask> tasks = DownloadManager.get().getTasks();
        int active = 0;
        long downloaded = 0;
        long total = 0;
        long speed = 0;
        boolean unknownTotal = false;
        for (DownloadTask task : tasks) {
            if (task.getStatus() != DownloadTask.STATUS_DOWNLOADING
                    && task.getStatus() != DownloadTask.STATUS_MERGING) continue;
            active++;
            speed += Math.max(0, task.getSpeedBps());
            if (task.isM3u8()) {
                unknownTotal = true;
            } else if (task.getTotalBytes() > 0) {
                total += task.getTotalBytes();
                downloaded += task.getDownloadedBytes();
            } else {
                unknownTotal = true;
            }
        }
        String text;
        if (active == 0) {
            text = "正在准备…";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(active).append(" 个任务");
            if (!unknownTotal && total > 0) sb.append(" · 总进度 ").append(downloaded * 100 / total).append("%");
            if (speed > 0) sb.append(" · ").append(DownloadTask.formatSize(speed)).append("/s");
            text = sb.toString();
        }
        Intent openIntent = new Intent(this, DownloadActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent pauseIntent = new Intent(this, DownloadService.class).setAction(ACTION_PAUSE_ALL);
        PendingIntent pausePending = PendingIntent.getService(this, 1, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Notify.DEFAULT)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("星辰下载中")
                .setContentText(text)
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(0, "全部暂停", pausePending);
        if (!unknownTotal && total > 0) {
            builder.setProgress(100, (int) (downloaded * 100 / total), false);
        } else {
            builder.setProgress(0, 0, true);
        }
        return builder.build();
    }
}
