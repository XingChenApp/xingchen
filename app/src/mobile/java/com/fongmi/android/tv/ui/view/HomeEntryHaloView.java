package com.fongmi.android.tv.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.Build;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * 音乐首页三彩卡光晕（瓜子 HomeEntryHalo，浅色分支 only：星辰首页固定浅色，深色分支不适用）。
 * 铺满整卡、画在文字／图标后面（布局里放卡片 FrameLayout 的第一个子 View，后续子 View 盖在其上），
 * 与星辰现行 84dp 半出环（C10）无关、互不替代：环保留不动，光晕是另加的一层。
 * 平时就是静态光晕（脉动值固定 0.5）；只有随心听卡在 z5 为真时才起脉动，
 * 且幅度极小（半径 ±1%、弧起角只摆 3°），不是闪烁、不是转圈、不新增图标。
 * 参数出处：redo3/round4/round4-oa3-z5.md 第 3、4 节（中心纵坐标取手机档 0.75，
 * 0.73 是另一设备档位值，未确证哪侧为手机，故表里写清取 0.75 的口径）。
 */
public class HomeEntryHaloView extends View {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private int haloColor = 0xFFEAF989;
    private float pulse = 0.5f;
    private boolean pulsing;
    private ValueAnimator pulseAnimator;

    public HomeEntryHaloView(Context context) {
        super(context);
        init();
    }

    public HomeEntryHaloView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HomeEntryHaloView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        fillPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(0.8f * density);
        ringPaint.setStrokeCap(Paint.Cap.BUTT);
        ringPaint.setStrokeJoin(Paint.Join.MITER);
        ringPaint.setColor(withAlpha(0xFFFFFFFF, 0.30f));
        dotPaint.setStyle(Paint.Style.FILL);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** 光晕色按卡取（猜 #FFE8F3／榜 #9DF3EE／随心 #EAF989；随心色是独立常量，不是渐变末段色）。 */
    public void setHaloColor(int color) {
        if (haloColor != color) {
            haloColor = color;
            invalidate();
        }
    }

    /**
     * 脉动门控：调用方只传 z5（来源 shuffle_listen 且正在播），系统动画开关在此再判，
     * 关时不启动画、脉动值固定 0.5 画静态光晕；不播时同样固定 0.5。
     */
    public void setPulsing(boolean want) {
        boolean enabled = want && animatorsEnabled();
        if (enabled == pulsing) return;
        pulsing = enabled;
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        if (pulsing) {
            pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
            pulseAnimator.setDuration(4000);
            pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                pulseAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
            }
            pulseAnimator.addUpdateListener(animation -> {
                pulse = (float) animation.getAnimatedValue();
                invalidate();
            });
            pulseAnimator.start();
        } else {
            pulse = 0.5f;
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        pulsing = false;
        super.onDetachedFromWindow();
    }

    private boolean animatorsEnabled() {
        try {
            return Settings.Global.getFloat(getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(255f * alpha);
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /**
     * 注意：不许在这里 override onMeasure 量 0。光晕是 match_parent 子项，ScrollView 不定高量测链下
     * 首遍自然量 0（不撑卡高），FrameLayout 二次测量再按卡高 EXACTLY 重量、铺满卡面；量 0 会把
     * 二次测量也归零，光晕直接不画。之前卡拉满屏的根子在根 LinearLayout 的 AT_MOST 剩余高度，
     * 不是光晕本身，根改 ScrollView 后已断链。
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float p = pulsing ? pulse : 0.5f;
        float cx = 0.66f * w;
        float cy = 0.75f * h;
        float cap = Math.min(0.29f * w, 0.25f * h);
        float a = (p * 0.02f + 0.99f) * cap;
        // 画 1：径向渐变圆，半径 1.75A，本色 0.85 → 本色 0.425 → 透明（等分三停点）。
        fillPaint.setShader(new RadialGradient(cx, cy, 1.75f * a,
                new int[]{withAlpha(haloColor, 0.85f), withAlpha(haloColor, 0.425f), 0x00000000},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, 1.75f * a, fillPaint);
        fillPaint.setShader(null);
        // 画 2：白细环，半径 1.5A，白 0.30，描边 0.8dp。
        canvas.drawCircle(cx, cy, 1.5f * a, ringPaint);
        // 画 3：实心圆，半径 A，本色 0.16。
        dotPaint.setColor(withAlpha(haloColor, 0.16f));
        canvas.drawCircle(cx, cy, a, dotPaint);
        // 画 4：扫掠渐变弧，扫过 260°，起角 p×3＋10°，描边 0.15A 圆头帽，渐变透明 → 白 0.90 → 透明。
        arcPaint.setStrokeWidth(0.15f * a);
        arcPaint.setShader(new SweepGradient(cx, cy,
                new int[]{0x00000000, withAlpha(0xFFFFFFFF, 0.90f), 0x00000000}, null));
        arcRect.set(cx - a, cy - a, cx + a, cy + a);
        canvas.drawArc(arcRect, p * 3f + 10f, 260f, false, arcPaint);
        arcPaint.setShader(null);
    }
}
