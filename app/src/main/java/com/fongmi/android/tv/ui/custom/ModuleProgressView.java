package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

// 模块行进度条：补 ModuleActivity / ModuleFragment 调用的 progress.setWaveSpeed()。
// 上游 item 布局里的 progress 就是带波速的自定义条，本树此前没有这个类，
// 布局里若放普通 ProgressBar，那两处 onDestroy 的 setWaveSpeed(0) 编不过。
// 画法只做两件事：按 fraction 画填充；waveSpeed > 0 时画一道移动高光并循环刷新，
// 0 则停。fraction < 0 表示总量未知，只画底轨（忙时仍有高光动效）。
public class ModuleProgressView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float fraction = 0f;
    private int waveSpeed = 0;
    private float wavePhase = 0f;

    public ModuleProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        trackPaint.setColor(0xFFE5E7EB);
        fillPaint.setColor(0xFF2F7CF6);
        wavePaint.setColor(0x66FFFFFF);
    }

    public void setWaveSpeed(int speed) {
        waveSpeed = Math.max(0, speed);
        if (waveSpeed > 0) postInvalidate();
        else invalidate();
    }

    public void setProgress(float fraction) {
        this.fraction = fraction < 0f ? -1f : Math.min(1f, fraction);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        waveSpeed = 0;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float radius = h / 2f;
        rect.set(0f, 0f, w, h);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);
        if (fraction > 0f) {
            rect.set(0f, 0f, w * fraction, h);
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
        }
        if (waveSpeed > 0) {
            float span = w * 0.3f;
            float left = (wavePhase % 1.3f - 0.3f) * w;
            rect.set(left, 0f, left + span, h);
            canvas.drawRoundRect(rect, radius, radius, wavePaint);
            wavePhase += 0.05f * waveSpeed;
            if (isAttachedToWindow()) postInvalidateDelayed(80);
        }
    }
}
