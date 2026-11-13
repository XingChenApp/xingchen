package com.fongmi.android.tv.ui.minibar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 音乐迷你条封面外圈进度环（从零重写）：满框画环——浅色轨道＋红色进度弧，
 * 进度由 ListenMiniBar 每 500ms 现拉位置喂 setFraction。
 * 无进度（duration<=0）时 fraction=0，只剩轨道，不编假进度。
 */
public class HomeProgressRingView extends View {

    private static final int TRACK_COLOR = 0x1A000000;
    private static final int PROGRESS_COLOR = 0xFFFA243C;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBox = new RectF();
    private float fraction;

    public HomeProgressRingView(Context context) {
        super(context);
        init();
    }

    public HomeProgressRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float stroke = 2.5f * getResources().getDisplayMetrics().density;
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setColor(TRACK_COLOR);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(stroke);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(PROGRESS_COLOR);
    }

    /** 喂进度（0..1，越界钳制）；值变了才重绘。 */
    public void setFraction(float value) {
        float v = Math.max(0f, Math.min(1f, value));
        if (Math.abs(v - fraction) < 0.0005f) return;
        fraction = v;
        invalidate();
    }

    public float getFraction() {
        return fraction;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float inset = progressPaint.getStrokeWidth() / 2f + 1f;
        arcBox.set(inset, inset, w - inset, h - inset);
        canvas.drawOval(arcBox, trackPaint);
        if (fraction > 0f) {
            canvas.drawArc(arcBox, -90f, 360f * fraction, false, progressPaint);
        }
    }
}
