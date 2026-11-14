package com.example.hikerview.utils;

import android.content.Context;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Toast;

import com.fongmi.android.tv.utils.TopPromptWindow;

/**
 * 8.83 {@code com.example.hikerview.utils.ToastMgr} 的同包名同签名补齐
 * （官包 jadx 反编译原样；lambda 还原为匿名类同语义）。
 * 非主线程转交 {@link ThreadTool#INSTANCE} 的 runOnUI（同批补齐）。
 *
 * 层级订正（2026-11-15，账 app-toast-layer-toastmgr-20261115，他亲定
 * 「提示必须在当前最上面的页面上」）：官形是裸系统 Toast，但系统 Toast 的窗口
 * 由 ROM 管——在他这台荣耀上渲染在 XPopup 全屏对话框窗之下只剩鬼影
 * （「已加载原图」案），且系统 Toast 浅透底的样子他 2026-10-22 已全应用否掉。
 * 本批四法先走 {@link TopPromptWindow} 顶层窗（位置、时长按本类原语义：
 * BottomCenter＝底部居中、底距 64dp 与系统默认位一致；Center＝居中；
 * short/long＝系统 Toast 名义时长），顶层窗不可用（无悬浮窗权限）时
 * 回落下方原官形系统 Toast 本体一字不动——那是权限未授时的存量形态。
 */
public class ToastMgr {
    public static void shortBottomCenter(final Context context, final String str) {
        if (context == null) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (TopPromptWindow.showText(str, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                        64, TopPromptWindow.DURATION_SHORT_MS)) {
                    return;
                }
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
                if (TopPromptWindow.showText(str, Gravity.CENTER, 0,
                        TopPromptWindow.DURATION_SHORT_MS)) {
                    return;
                }
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
                if (TopPromptWindow.showText(str, Gravity.CENTER, 0,
                        TopPromptWindow.DURATION_LONG_MS)) {
                    return;
                }
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
                if (TopPromptWindow.showText(str, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                        64, TopPromptWindow.DURATION_LONG_MS)) {
                    return;
                }
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
