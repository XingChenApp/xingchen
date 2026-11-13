package com.example.hikerview.utils;

import android.content.Context;
import android.os.Looper;
import android.widget.Toast;

/**
 * 8.83 {@code com.example.hikerview.utils.ToastMgr} 的同包名同签名补齐
 * （官包 jadx 反编译原样；lambda 还原为匿名类同语义）。
 * 非主线程转交 {@link ThreadTool#INSTANCE} 的 runOnUI（同批补齐）。
 */
public class ToastMgr {
    public static void shortBottomCenter(final Context context, final String str) {
        if (context == null) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void shortCenter(final Context context, final String str) {
        if (context == null) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast makeText = Toast.makeText(context, str, Toast.LENGTH_SHORT);
                makeText.setGravity(17, 0, 0);
                makeText.show();
            }
        });
    }

    public static void longCenter(final Context context, final String str) {
        if (context == null) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast makeText = Toast.makeText(context, str, Toast.LENGTH_LONG);
                makeText.setGravity(17, 0, 0);
                makeText.show();
            }
        });
    }

    public static void longBottomCenter(final Context context, final String str) {
        if (context == null) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, str, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void runOnUiThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            ThreadTool.INSTANCE.runOnUI(runnable);
        }
    }
}
