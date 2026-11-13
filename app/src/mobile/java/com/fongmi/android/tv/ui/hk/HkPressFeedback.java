package com.fongmi.android.tv.ui.hk;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * 液态玻璃按压反馈（施工图 §3.3，一次封装多处复用）：
 * DOWN → 微缩 0.94 ＋ 亮度 1.25（180ms）；
 * UP → 回弹：0.94 → 1.025 → 1.0（共 220ms）；
 * CANCEL（滚动接管）→ 立刻复位。
 * 返回 false，不吞点击，不干扰 RecyclerView 滚动。
 */
public class HkPressFeedback implements View.OnTouchListener {

    private static final HkPressFeedback INSTANCE = new HkPressFeedback();

    public static HkPressFeedback get() {
        return INSTANCE;
    }

    /** 把按压反馈接到视图上（adapter 的 itemView／顶栏圆钮／底钮）。 */
    public static void attach(View v) {
        if (v != null) v.setOnTouchListener(INSTANCE);
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                v.animate().cancel();
                v.animate().scaleX(0.94f).scaleY(0.94f)
                        .setDuration(180)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                setBrightness(v, 1.25f);
                break;
            case MotionEvent.ACTION_UP:
                v.animate().cancel();
                // 两段回弹：先冲到 1.025（110ms），再落回 1.0（110ms），共 220ms
                v.animate().scaleX(1.025f).scaleY(1.025f)
                        .setDuration(110)
                        .setInterpolator(new DecelerateInterpolator())
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                v.animate().scaleX(1f).scaleY(1f)
                                        .setDuration(110)
                                        .setInterpolator(new DecelerateInterpolator())
                                        .setListener(null)
                                        .start();
                            }
                        })
                        .start();
                setBrightness(v, 1f);
                break;
            case MotionEvent.ACTION_CANCEL:
                // 滚动接管：强制复位，不留半缩状态
                v.animate().cancel();
                v.setScaleX(1f);
                v.setScaleY(1f);
                setBrightness(v, 1f);
                break;
            default:
                break;
        }
        return false;
    }

    private void setBrightness(View v, float b) {
        Drawable bg = v.getBackground();
        if (bg == null) return;
        if (b == 1f) {
            bg.clearColorFilter();
            return;
        }
        ColorMatrix cm = new ColorMatrix();
        cm.setScale(b, b, b, 1f);
        bg.setColorFilter(new ColorMatrixColorFilter(cm));
    }
}
