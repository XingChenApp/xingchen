package com.fongmi.android.tv.login;

import android.content.Context;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.utils.ResUtil;

/**
 * 登录域共享玻璃面（主集对称壳）：网盘/壳子统一登录的弹窗都在主集，够不着音乐域
 * 的 MusicGlassDialogs，本类按同口径落一份——烟熏透底＋整圈亮边（顶边最亮）＋
 * 顶部流光＋底部压影，数值对 MusicGlassDrawable：亮边三段 8C/57/38 白、顶部
 * 高光 2E 白渐隐、底影黑渐隐。登录链所有弹窗面板一律用它，不许各写一套。
 * 静态面：不采身后内容（登录弹窗浮在任意页面上，主集没有音乐域的共享背板）。
 */
public class LoginGlassDrawable extends Drawable {

    // 烟熏透底：与登录四弹窗既有面板同族（#26282C），做成上轻下沉的竖渐变
    private static final int[] FILL = {0xF230343A, 0xF226282C, 0xF2212327};
    // 亮边：顶最亮、往下收（MusicGlassDrawable STROKE 同值）
    private static final int[] STROKE = {0x8CFFFFFF, 0x57FFFFFF, 0x38FFFFFF};
    private static final float[] STROKE_POS = {0f, 0.55f, 1f};

    private final float radius;
    private final float strokeWidth;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clipPath = new Path();

    public LoginGlassDrawable(Context context, float radiusDp) {
        radius = ResUtil.dp2px((int) radiusDp);
        strokeWidth = Math.max(1f, ResUtil.dp2px(1));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeWidth);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        rect.set(bounds);
        float w = Math.max(1f, rect.width());
        float h = Math.max(1f, rect.height());
        fillPaint.setShader(new LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
                FILL, null, Shader.TileMode.CLAMP));
        // 顶部流光：白 2E 渐隐，吃上段三成（MusicGlassDrawable 顶部高光同值）
        sheenPaint.setShader(new LinearGradient(rect.left, rect.top, rect.left, rect.top + h * 0.3f,
                new int[]{0x2EFFFFFF, 0x00000000}, null, Shader.TileMode.CLAMP));
        // 底部压影：黑渐变沉底，托分层
        shadePaint.setShader(new LinearGradient(rect.left, rect.bottom - h * 0.45f, rect.left, rect.bottom,
                new int[]{0x00000000, 0x33000000}, null, Shader.TileMode.CLAMP));
        strokePaint.setShader(new LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
                STROKE, STROKE_POS, Shader.TileMode.CLAMP));
        clipPath.reset();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (rect.width() <= 0 || rect.height() <= 0) return;
        canvas.drawRoundRect(rect, radius, radius, fillPaint);
        int save = canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawRect(rect, sheenPaint);
        canvas.drawRect(rect, shadePaint);
        canvas.restoreToCount(save);
        float half = strokeWidth / 2f;
        RectF edge = new RectF(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half);
        float r = Math.max(0f, radius - half);
        canvas.drawRoundRect(edge, r, r, strokePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        sheenPaint.setAlpha(alpha);
        shadePaint.setAlpha(alpha);
        strokePaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
