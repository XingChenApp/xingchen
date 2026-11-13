package com.fongmi.android.tv.ui.lab;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * 实验室前台保活服务：有服务在跑时常驻一条通知（「N 个服务正在运行」），
 * 降低被系统回收的概率。服务全停后自己退。
 */
public class LabKeepAlive extends Service {

    private static final int NID = 0x1ab0;
    private static final String CHANNEL = "lab";

    public static void refresh(Context ctx) {
        Context c = ctx.getApplicationContext();
        int n = LabRunner.runningCount(c);
        Intent i = new Intent(c, LabKeepAlive.class);
        if (n > 0) {
            i.setAction("refresh");
            i.putExtra("count", n);
            try {
                // API 26+ 从后台 startService 会被系统拒（IllegalStateException）；
                // 本服务本来就是前台服务（onStartCommand 里 startForeground），走专用入口
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    c.startForegroundService(i);
                } else {
                    c.startService(i);
                }
            } catch (Exception e) {
                android.util.Log.e("LabKeepAlive", "保活服务起不来", e);
            }
        } else {
            try {
                c.stopService(i);
            } catch (Exception e) {
                android.util.Log.e("LabKeepAlive", "保活服务停不下来", e);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int n = intent == null ? LabRunner.runningCount(this) : intent.getIntExtra("count", LabRunner.runningCount(this));
        if (n <= 0) {
            stopSelf();
            return START_NOT_STICKY;
        }
        ensureChannel(this);
        Intent open = new Intent(this, LabHomeActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("实验室")
                .setContentText(n + " 个服务正在运行")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(NID, notification);
        return START_STICKY;
    }

    private static void ensureChannel(Context c) {
        try {
            NotificationManagerCompat mgr = NotificationManagerCompat.from(c);
            mgr.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL,
                    NotificationManager.IMPORTANCE_LOW).setName("实验室").build());
        } catch (Exception ignored) {
        }
    }
}
