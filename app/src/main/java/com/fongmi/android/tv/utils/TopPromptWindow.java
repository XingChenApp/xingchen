package com.fongmi.android.tv.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.fongmi.android.tv.App;

/**
 * 顶层提示窗：全应用「提示」的最上层落点（账 app-toast-layer-toastmgr-20261115）。
 *
 * 为什么需要它：系统 Toast 的窗口由 ROM 管，层级 App 说了不算——在他这台荣耀
 * （Android 16 / MagicOS）上，系统 Toast 渲染在 XPopup 全屏对话框（FullScreenDialog，
 * 独立 Dialog 窗）之下，只剩一层鬼影（真机截图亮度实测：提示字 ~40 vs 同屏
 * 查看器自家字 255）。136 给 ToastUtil 抬 Z 只治到 Activity 窗内，压不过任何
 * Dialog 窗。App 自己能控制、且确定在一切 Dialog 窗之上的层，只有自家的
 * TYPE_APPLICATION_OVERLAY 窗（2038，高于 Dialog 窗、也高于系统 Toast 窗 2005）；
 * 桌面歌词窗（DesktopLyricsWindow）已用同型窗＋同款参数在树里跑通，本件照它装配。
 *
 * 形态口径：胶囊与 ToastUtil 定版同脸（#FF262626 实底、圆角 24dp、白字、
 * 最大宽 85%、最多 5 行）——系统 Toast 的浅透底样子他 2026-10-22 已全应用否掉，
 * ToastMgr 裸系统 Toast 是移植残留，本批收编。位置与时长由调用方按原设施语义给
 * （底部居中＝系统 Toast 默认位、底距 64dp；居中＝Gravity.CENTER），一字不改。
 *
 * 可用性：无悬浮窗权限（Settings.canDrawOverlays 为假）或建窗失败时返回不可用，
 * 调用方走各自原有渲染（ToastMgr＝原系统 Toast、ToastUtil＝Activity 内 Snackbar），
 * 那是权限未授时的存量形态、不是本件的兜底设计；授权入口树里已有
 * （VideoActivity 桌面歌词的 openOverlayPermission）。
 *
 * 线程：除 isAvailable 外都必须在主线程调用（ToastMgr/ToastUtil 均已自带转交）。
 */
public final class TopPromptWindow {

    /** 系统 Toast 的名义时长（SHORT/LONG），调用方按原设施语义传入。 */
    public static final int DURATION_SHORT_MS = 2000;
    public static final int DURATION_LONG_MS = 3500;

    private static FrameLayout root;
    private static TextView capsule;
    private static Handler handler;
    private static boolean attachFailed;

    private static final Runnable HIDE = () -> {
        synchronized (TopPromptWindow.class) {
            if (capsule != null) capsule.setVisibility(View.GONE);
            releaseIfIdle();
        }
    };

    private static final Runnable RECHECK = () -> {
        synchronized (TopPromptWindow.class) {
            releaseIfIdle();
        }
    };

    private TopPromptWindow() {
    }

    /** 悬浮窗权限是否已授（低版本恒真，同 DesktopLyricsWindow 口径）。 */
    public static boolean isAvailable() {
        if (attachFailed) return false;
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(App.get());
    }

    /**
     * 顶层窗的根视图（供 ToastUtil 把 Snackbar 挂进来，与本件自带胶囊同窗同层）。
     * 主线程调用；不可用或建窗失败返回 null。
     */
    public static synchronized FrameLayout hostRoot() {
        if (!isAvailable()) return null;
        return ensureAttached() ? root : null;
    }

    /**
     * 在顶层窗显示一条文本提示。gravity 取 Gravity.BOTTOM|CENTER_HORIZONTAL
     * （配 bottomMarginDp）或 Gravity.CENTER；durationMs 后自消。
     * 返回 false＝顶层窗不可用，调用方走原有渲染。
     */
    public static synchronized boolean showText(CharSequence text, int gravity, int bottomMarginDp, int durationMs) {
        if (text == null || !isAvailable() || !ensureAttached()) return false;
        if (capsule == null) {
            capsule = buildCapsule(App.get());
            root.addView(capsule);
        }
        capsule.setText(text);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if ((gravity & Gravity.CENTER) == Gravity.CENTER) {
            lp.gravity = Gravity.CENTER;
        } else {
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            float density = App.get().getResources().getDisplayMetrics().density;
            lp.bottomMargin = Math.round(bottomMarginDp * density);
        }
        capsule.setLayoutParams(lp);
        capsule.setVisibility(View.VISIBLE);
        capsule.bringToFront();
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        handler.removeCallbacks(HIDE);
        handler.postDelayed(HIDE, durationMs);
        return true;
    }

    /** 立即收起自带胶囊（宿主内容如 Snackbar 不受影响）。 */
    public static synchronized void dismissText() {
        if (handler != null) handler.removeCallbacks(HIDE);
        if (capsule != null) capsule.setVisibility(View.GONE);
        releaseIfIdle();
    }

    /**
     * 根内无可见内容时撤窗（不留常驻空窗占合成层）：自带胶囊已隐、且没有
     * 宿主挂着的子视图（Snackbar 在场时其视图是根的子视图）时才撤。
     * 宿主内容消失后由其回调调用本法收口。
     */
    public static synchronized void releaseIfIdle() {
        if (root == null) return;
        if (capsule != null && capsule.getVisibility() == View.VISIBLE) return;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child != capsule && child.getVisibility() == View.VISIBLE) {
                // 宿主内容可能正在消失动画中（Snackbar 回调先于视图摘除）：
                // 延迟一拍复查，别把空窗常驻留下。
                if (handler == null) handler = new Handler(Looper.getMainLooper());
                handler.removeCallbacks(RECHECK);
                handler.postDelayed(RECHECK, 400);
                return;
            }
        }
        detach();
    }

    private static boolean ensureAttached() {
        if (root != null) return true;
        try {
            Context app = App.get();
            FrameLayout frame = new FrameLayout(app);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            params.format = PixelFormat.TRANSLUCENT;
            params.gravity = Gravity.TOP | Gravity.START;
            params.windowAnimations = 0;
            WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                attachFailed = true;
                return false;
            }
            wm.addView(frame, params);
            root = frame;
            return true;
        } catch (Throwable t) {
            attachFailed = true;
            root = null;
            return false;
        }
    }

    private static void detach() {
        try {
            WindowManager wm = (WindowManager) App.get().getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && root != null) wm.removeView(root);
        } catch (Throwable ignored) {
        }
        root = null;
        capsule = null;
    }

    /** 胶囊本体：与 ToastUtil 定版同脸（数值逐项同 ToastUtil.show 的装配）。 */
    private static TextView buildCapsule(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int screenW = context.getResources().getDisplayMetrics().widthPixels;
        TextView tv = new TextView(context);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Color.parseColor("#FF262626"));
        pill.setCornerRadius(24 * density);
        tv.setBackground(pill);
        tv.setPadding(Math.round(10 * density), Math.round(5 * density),
                Math.round(10 * density), Math.round(5 * density));
        tv.setTextColor(Color.WHITE);
        tv.setMaxLines(5);
        tv.setMaxWidth(Math.round(screenW * 0.85f));
        tv.setVisibility(View.GONE);
        return tv;
    }
}
