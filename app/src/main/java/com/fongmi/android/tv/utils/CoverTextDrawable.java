package com.fongmi.android.tv.utils;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

/**
 * 无封面素图（用户定死的封面规矩）：只有「封面地址真为空」时才用——
 * 素灰底＋名字排字。有地址的永远透明（加载中/失败都透明），不许拿它兜底，
 * 所以本类只许在各绑定点判空后使用，加载失败回调里不许出现。
 *
 * firstCharOnly：小容器（迷你条小圆）只排首字；否则 StaticLayout 排全名（最多 3 行）。
 * cornerRadiusPx：圆角半径（像素）；传负数＝圆形（半径取短边一半）。
 */
public class CoverTextDrawable extends Drawable {

    private static final int BG_TOP = 0xFF3D4450;
    private static final int BG_BOTTOM = 0xFF262B33;
    private static final int TEXT_COLOR = 0xFFD9DEE6;

    private final String text;
    private final boolean firstCharOnly;
    private final float cornerRadiusPx;
    /** 逐角圆角（8 个值：左上/右上/右下/左下各 x,y）；非 null 时优先于统一圆角。 */
    private final float[] cornerRadiiPx;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private int alpha = 255;

    public CoverTextDrawable(String text, boolean firstCharOnly, float cornerRadiusPx) {
        this.text = text == null ? "" : text;
        this.firstCharOnly = firstCharOnly;
        this.cornerRadiusPx = cornerRadiusPx;
        this.cornerRadiiPx = null;
        textPaint.setColor(TEXT_COLOR);
        textPaint.setFakeBoldText(true);
    }

    /** 只圆上面两角这类场景用：素图圆角必须与成功图的变换逐角对齐。 */
    public CoverTextDrawable(String text, boolean firstCharOnly, float[] cornerRadiiPx) {
        this.text = text == null ? "" : text;
        this.firstCharOnly = firstCharOnly;
        this.cornerRadiusPx = 0;
        this.cornerRadiiPx = cornerRadiiPx;
        textPaint.setColor(TEXT_COLOR);
        textPaint.setFakeBoldText(true);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        clipPath.reset();
        if (cornerRadiiPx != null) {
            clipPath.addRoundRect(new RectF(b), cornerRadiiPx, Path.Direction.CW);
        } else {
            float radius = cornerRadiusPx < 0 ? Math.min(b.width(), b.height()) / 2f : cornerRadiusPx;
            clipPath.addRoundRect(new RectF(b), radius, radius, Path.Direction.CW);
        }
        canvas.save();
        canvas.clipPath(clipPath);
        bgPaint.setShader(new LinearGradient(b.left, b.top, b.left, b.bottom,
                BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP));
        bgPaint.setAlpha(alpha);
        canvas.drawRect(b, bgPaint);
        textPaint.setAlpha(alpha);
        if (firstCharOnly) {
            String ch = text.isEmpty() ? "♪" : text.substring(0, 1);
            textPaint.setTextSize(Math.min(b.width(), b.height()) * 0.5f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float baseline = b.centerY() - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(ch, b.centerX(), baseline, textPaint);
        } else if (!text.isEmpty()) {
            float textSize = Math.max(12f, b.height() * 0.16f);
            textPaint.setTextSize(textSize);
            int width = Math.max(1, (int) (b.width() * 0.84f));
            StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(3)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setLineSpacing(0f, 1.15f)
                    .build();
            canvas.translate(b.left + (b.width() - width) / 2f,
                    b.top + (b.height() - layout.getHeight()) / 2f);
            layout.draw(canvas);
        }
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        textPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
