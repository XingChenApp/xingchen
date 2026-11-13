package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.fongmi.android.tv.App;
import com.google.android.material.snackbar.Snackbar;

/**
 * 全应用提示统一入口：优先用实底白字的 Snackbar。
 * 他手机上系统 Toast 是浅色透底，盖在亮色画面上字看不清；自定义 Toast 视图在高版本
 * 系统上会被忽略回系统样式，改了也白改，所以不再走 Toast 自定义视图。
 * 没有可用 Activity 时才回落系统 Toast。
 */
public class ToastUtil {

    private static Snackbar current;

    public static void show(Context context, CharSequence text) {
        show(context, text, Toast.LENGTH_SHORT);
    }

    public static void show(Context context, CharSequence text, int duration) {
        if (context == null || text == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            App.post(() -> show(context, text, duration));
            return;
        }
        try {
            Activity activity = context instanceof Activity ? (Activity) context : App.activity();
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                View anchor = activity.findViewById(android.R.id.content);
                if (anchor != null) {
                    if (current != null) current.dismiss();
                    int len = duration == Toast.LENGTH_LONG ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT;
                    Snackbar bar = Snackbar.make(anchor, text, len);
                    View sbView = bar.getView();
                    float density = activity.getResources().getDisplayMetrics().density;
                    int screenW = activity.getResources().getDisplayMetrics().widthPixels;
                    // 2026-10-22 潇洒哥纠正：提示不许贴底盖 tab 栏、不许满宽大条。
                    // 改回老 Toast 的形态：居中小胶囊，字白底深；位置浮在 tab 栏上方（有 tab 栏时按其实高＋底边距＋8dp 缝顶上去，
                    // 无 tab 栏时按老 Toast 的 64dp 底边距），不再是贴底满宽大条。
                    GradientDrawable pill = new GradientDrawable();
                    pill.setColor(Color.parseColor("#FF262626"));
                    pill.setCornerRadius(24 * density);
                    sbView.setBackground(pill);
                    int padH = Math.round(10 * density);
                    int padV = Math.round(5 * density);
                    sbView.setPadding(padH, padV, padH, padV);
                    TextView tv = sbView.findViewById(com.google.android.material.R.id.snackbar_text);
                    if (tv != null) {
                        tv.setTextColor(Color.WHITE);
                        tv.setMaxLines(5);
                        tv.setMaxWidth(Math.round(screenW * 0.85f));
                    }
                    int bottomMargin = Math.round(64 * density);
                    int navId = activity.getResources().getIdentifier("navigation", "id", activity.getPackageName());
                    View tabBar = navId != 0 ? activity.findViewById(navId) : null;
                    if (tabBar != null && tabBar.getVisibility() == View.VISIBLE) {
                        int tabH = tabBar.getHeight();
                        if (tabH <= 0) {
                            int dimId = activity.getResources().getIdentifier("xc_bottombar_height", "dimen", activity.getPackageName());
                            tabH = dimId != 0 ? activity.getResources().getDimensionPixelSize(dimId) : Math.round(56 * density);
                        }
                        int tabBottomMargin = 0;
                        ViewGroup.LayoutParams tlp = tabBar.getLayoutParams();
                        if (tlp instanceof ViewGroup.MarginLayoutParams) tabBottomMargin = ((ViewGroup.MarginLayoutParams) tlp).bottomMargin;
                        if (tabBottomMargin <= 0) {
                            int dimId = activity.getResources().getIdentifier("xc_bottombar_margin_bottom", "dimen", activity.getPackageName());
                            if (dimId != 0) tabBottomMargin = activity.getResources().getDimensionPixelSize(dimId);
                        }
                        bottomMargin = tabH + tabBottomMargin + Math.round(8 * density);
                    }
                    ViewGroup.LayoutParams lp = sbView.getLayoutParams();
                    if (lp instanceof FrameLayout.LayoutParams) {
                        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                        flp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                        flp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                        flp.bottomMargin = bottomMargin;
                        sbView.setLayoutParams(flp);
                    } else if (lp instanceof CoordinatorLayout.LayoutParams) {
                        CoordinatorLayout.LayoutParams clp = (CoordinatorLayout.LayoutParams) lp;
                        clp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                        clp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                        clp.bottomMargin = bottomMargin;
                        sbView.setLayoutParams(clp);
                    }
                    current = bar;
                    bar.show();
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            // 兜底必须是系统 Toast 原样调用，不许再走 ToastUtil.show，否则自递归
            android.widget.Toast.makeText(context.getApplicationContext(), text, duration).show();
        } catch (Throwable ignored) {
        }
    }

    public static void dismiss() {
        try {
            if (current != null) current.dismiss();
        } catch (Throwable ignored) {
        }
        current = null;
    }
}
