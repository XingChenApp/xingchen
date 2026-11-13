package com.fongmi.android.tv.ui.hk;

import android.content.Context;
import android.graphics.Bitmap;
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
import android.view.View;

/**
 * 液态玻璃件（施工图 §3.2，一次定死）：
 * 背景＝从共享背景缓冲按自身在窗口中的位置裁剪（缓冲已是 1/4 模糊采样，天然透底模糊），
 * 再叠三层：白渐变罩（.58→.34→.46）＋ 1dp 白描边高光（75%）＋ 底部内软影。
 * 缓冲每 500ms 更新一帧时按版本号重裁；零 RenderEffect。
 * 注：软影做在界内（底部内渐变），因卡片在 RecyclerView 内会被父容器裁剪，界外投影不可行。
 */
public class HkGlassDrawable extends Drawable {

    private final float mRadius;          // px
    private final float mDensity;

    private final Paint mBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint mOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Rect mSrc = new Rect();
    private final RectF mDst = new RectF();
    private final RectF mTmp = new RectF();
    private final Path mClip = new Path();
    private final int[] mHostLoc = new int[2];
    private final int[] mBgLoc = new int[2];

    private int mLastVersion = -1;
    private boolean mLastOk = false;

    // 白玻璃默认：白渐变罩 .58→.34→.46 ＋ 1dp 白描边（75%）
    private static final int[] OVERLAY_WHITE = {0x94FFFFFF, 0x57FFFFFF, 0x75FFFFFF};
    // 奶油玻璃：奶油渐变底 ＋ 1dp 金描边（底钮用）
    private static final int[] OVERLAY_CREAM = {0xFFFFFBF0, 0xFFFFF6E2, 0xFFFFEECB};

    private int[] mOverlayColors = OVERLAY_WHITE;
    private float[] mOverlayStops = {0f, 0.55f, 1f};

    public HkGlassDrawable(Context context, float cornerRadiusDp) {
        this(context, cornerRadiusDp, OVERLAY_WHITE, 0xBFFFFFFF);
    }

    /** 变体：自定义罩色与描边色（如奶油玻璃底钮：奶油渐变＋金描边）。 */
    public HkGlassDrawable(Context context, float cornerRadiusDp, int[] overlayColors, int strokeColor) {
        mDensity = context.getResources().getDisplayMetrics().density;
        mRadius = cornerRadiusDp * mDensity;
        if (overlayColors != null && overlayColors.length == 3) mOverlayColors = overlayColors;
        mFallbackPaint.setColor(0x00000000); // 兜底改透明：裁剪失败时透出页底，不再整片刷白
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(1f * mDensity);
        mStrokePaint.setColor(strokeColor);
    }

    /** 奶油玻璃底钮快捷构造：奶油渐变＋1dp 金描边 #D9A420。 */
    public static HkGlassDrawable creamButton(Context context, float cornerRadiusDp) {
        return new HkGlassDrawable(context, cornerRadiusDp, OVERLAY_CREAM, 0xFFD9A420);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mLastVersion = -1; // 界变了就重裁
        // 罩色 shader（界相关，缓存）：默认白玻璃 .58→.34→.46，奶油变体走构造参数
        mOverlayPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                mOverlayColors, mOverlayStops, Shader.TileMode.CLAMP));
        // 底部内软影：rgba(80,90,120,.20) 向上羽化 14dp
        float sh = 14f * mDensity;
        mShadowPaint.setShader(new LinearGradient(0, bounds.bottom - sh, 0, bounds.bottom,
                new int[]{0x00000000, 0x33505A78}, null, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        mTmp.set(b);
        mClip.reset();
        mClip.addRoundRect(mTmp, mRadius, mRadius, Path.Direction.CW);

        // 1) 裁剪缓冲：只在版本号变化或上次失败时重算
        int ver = HkDynamicBgView.getBufferVersion();
        if (ver != mLastVersion) {
            mLastOk = computeCrop(b);
            mLastVersion = ver;
        }

        canvas.save();
        canvas.clipPath(mClip);
        if (mLastOk) {
            Bitmap buf = HkDynamicBgView.getBuffer();
            mDst.set(b);
            canvas.drawBitmap(buf, mSrc, mDst, mBitmapPaint);
        } else {
            canvas.drawRect(mTmp, mFallbackPaint); // 兜底：白透底
        }
        // 2) 白渐变罩 3) 底部内软影
        canvas.drawRect(mTmp, mOverlayPaint);
        canvas.drawRect(mTmp, mShadowPaint);
        canvas.restore();
        // 4) 边缘高光（clip 外画，避免被切掉半个描边）
        float half = mStrokePaint.getStrokeWidth() / 2f;
        mTmp.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
        canvas.drawRoundRect(mTmp, Math.max(0, mRadius - half), Math.max(0, mRadius - half), mStrokePaint);
    }

    /** 按宿主在窗口中的位置，把界映射到缓冲坐标。失败返回 false（走兜底）。 */
    private boolean computeCrop(Rect b) {
        if (!(getCallback() instanceof View)) return false;
        View host = (View) getCallback();
        View root = host.getRootView();
        if (root == null) return false;
        HkDynamicBgView bg = root.findViewById(com.fongmi.android.tv.R.id.hk_dynamic_bg);
        if (bg == null || !HkDynamicBgView.hasBuffer()) return false;
        int bgW = bg.getWidth(), bgH = bg.getHeight();
        if (bgW <= 0 || bgH <= 0) return false;
        host.getLocationInWindow(mHostLoc);
        bg.getLocationInWindow(mBgLoc);
        float sx = (float) HkDynamicBgView.bufferWidth() / bgW;
        float sy = (float) HkDynamicBgView.bufferHeight() / bgH;
        int x0 = (int) ((mHostLoc[0] + b.left - mBgLoc[0]) * sx);
        int y0 = (int) ((mHostLoc[1] + b.top - mBgLoc[1]) * sy);
        int x1 = (int) ((mHostLoc[0] + b.right - mBgLoc[0]) * sx);
        int y1 = (int) ((mHostLoc[1] + b.bottom - mBgLoc[1]) * sy);
        // clamp 进缓冲界
        int bw = HkDynamicBgView.bufferWidth(), bh = HkDynamicBgView.bufferHeight();
        x0 = Math.max(0, Math.min(x0, bw - 1));
        y0 = Math.max(0, Math.min(y0, bh - 1));
        x1 = Math.max(x0 + 1, Math.min(x1, bw));
        y1 = Math.max(y0 + 1, Math.min(y1, bh));
        mSrc.set(x0, y0, x1, y1);
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
        mBitmapPaint.setAlpha(alpha);
        mOverlayPaint.setAlpha(alpha);
        mStrokePaint.setAlpha(alpha);
        mShadowPaint.setAlpha(alpha);
        mFallbackPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mBitmapPaint.setColorFilter(colorFilter);
        mOverlayPaint.setColorFilter(colorFilter);
        mStrokePaint.setColorFilter(colorFilter);
        mShadowPaint.setColorFilter(colorFilter);
        mFallbackPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
