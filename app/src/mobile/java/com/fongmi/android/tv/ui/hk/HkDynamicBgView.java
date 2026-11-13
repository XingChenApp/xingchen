package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.fongmi.android.tv.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 海阔液态玻璃动态背景（施工图 §3.1，一次定死）：
 * 共享 1/4 降采样离屏缓冲——对角淡色渐变 ＋ 4 个径向色团（羽化），
 * 漂移周期 44s、幅度 ±25dp、缩放 ±3%，2fps 重绘；页面不可见自停。
 * 玻璃件（HkGlassDrawable）从同一份缓冲按自身位置裁剪，天然"透底模糊"。
 * 全 API 可用（minSdk 24），零 RenderEffect。
 */
public class HkDynamicBgView extends View {

    // ---------- 共享缓冲（进程单例态） ----------
    private static final Object LOCK = new Object();
    private static Bitmap sBuffer;              // 1/4 降采样缓冲
    private static int sBufferW, sBufferH;
    private static int sBufferVersion = 0;      // 每次重绘 +1，玻璃件据此重裁
    private static long sStartNanos = 0;        // 动画起点（只设一次，相位连续）
    private static boolean sTickerRunning = false;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static final List<WeakReference<HkDynamicBgView>> sViews = new ArrayList<>();

    private static final long CYCLE_MS = 44000; // 漂移周期 44s（样张 28–34s，比样张再轻）
    private static final long TICK_MS = 500;    // 2fps：幅度小到看不出跳帧

    // 色团：颜色 + 基准位置（缓冲占比）+ 相位
    // 淡蓝 rgba(168,196,232,.55)／淡紫 rgba(196,181,228,.50)／淡青 rgba(166,213,205,.45)／淡粉 rgba(240,201,178,.38)
    private static final int[] BLOB_COLORS = {0x8CA8C4E8, 0x80C4B5E4, 0x73A6D5CD, 0x61F0C9B2};
    private static final float[] BLOB_X = {0.18f, 0.88f, 0.12f, 0.85f};
    private static final float[] BLOB_Y = {0.12f, 0.28f, 0.82f, 0.88f};
    private static final float[] BLOB_PHASE = {0f, 1.7f, 3.4f, 5.1f};

    private boolean mUserPaused = false;        // Activity onPause 兜底
    private boolean mWindowVisible = true;      // 窗口可见性（被全屏页盖住时为 false）
    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    public HkDynamicBgView(Context context) {
        super(context);
        init();
    }

    public HkDynamicBgView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
    }

    // ---------- 对外：Activity 在 decor content 挂背景（LinearLayout 根页面用，XML 法见 activity_hk） ----------
    public static void attachTo(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        if (content.findViewById(R.id.hk_dynamic_bg) != null) return; // 已有，不重复挂
        HkDynamicBgView v = new HkDynamicBgView(activity);
        v.setId(R.id.hk_dynamic_bg);
        content.addView(v, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Activity onPause 兜底：停本页动画。 */
    public void pause() {
        mUserPaused = true;
        maybeStopTicker();
    }

    /** Activity onResume：恢复本页动画。 */
    public void resume() {
        mUserPaused = false;
        if (getWindowVisibility() == VISIBLE) ensureTicker();
    }

    // ---------- 生命周期：页面不可见自停 ----------
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        synchronized (LOCK) {
            sViews.add(new WeakReference<>(this));
        }
        mWindowVisible = getWindowVisibility() == VISIBLE;
        if (mWindowVisible && !mUserPaused) ensureTicker();
    }

    @Override
    protected void onDetachedFromWindow() {
        synchronized (LOCK) {
            for (Iterator<WeakReference<HkDynamicBgView>> it = sViews.iterator(); it.hasNext(); ) {
                HkDynamicBgView v = it.next().get();
                if (v == null || v == this) it.remove();
            }
        }
        maybeStopTicker();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mWindowVisible = visibility == VISIBLE;
        if (mWindowVisible && !mUserPaused) ensureTicker();
        else maybeStopTicker();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Bitmap buf;
        synchronized (LOCK) {
            buf = sBuffer;
        }
        if (buf != null && !buf.isRecycled()) {
            canvas.drawBitmap(buf, null,
                    new android.graphics.RectF(0, 0, getWidth(), getHeight()), mPaint);
        } else {
            canvas.drawColor(0xFFF4F6F8); // 缓冲未就绪兜底：淡色场顶色
        }
    }

    // ---------- 共享 ticker ----------
    private static void ensureTicker() {
        synchronized (LOCK) {
            if (sTickerRunning) return;
            ensureBufferSizeLocked();
            if (sStartNanos == 0) sStartNanos = System.nanoTime();
            sTickerRunning = true;
            sHandler.post(sTick);
        }
    }

    private static void maybeStopTicker() {
        synchronized (LOCK) {
            for (Iterator<WeakReference<HkDynamicBgView>> it = sViews.iterator(); it.hasNext(); ) {
                HkDynamicBgView v = it.next().get();
                if (v == null) {
                    it.remove();
                    continue;
                }
                // 还有存活、窗口可见、未被用户暂停的视图就继续跑
                if (v.isAttachedToWindow() && v.mWindowVisible && !v.mUserPaused) return;
            }
            sTickerRunning = false;
            sHandler.removeCallbacks(sTick);
        }
    }

    private static final Runnable sTick = new Runnable() {
        @Override
        public void run() {
            synchronized (LOCK) {
                if (!sTickerRunning) return;
                drawBufferLocked();
                sBufferVersion++;
                for (Iterator<WeakReference<HkDynamicBgView>> it = sViews.iterator(); it.hasNext(); ) {
                    HkDynamicBgView v = it.next().get();
                    if (v == null) it.remove();
                    else v.postInvalidateOnAnimation();
                }
                sHandler.postDelayed(this, TICK_MS);
            }
        }
    };

    /** 缓冲尺寸与当前屏幕对不上（旋转后宽高互换）就重建，否则旧缓冲会被拉伸铺满。调用方已持 LOCK。 */
    private static void ensureBufferSizeLocked() {
        android.util.DisplayMetrics dm = android.content.res.Resources.getSystem().getDisplayMetrics();
        int w = Math.max(2, dm.widthPixels / 4);
        int h = Math.max(2, dm.heightPixels / 4);
        if (sBuffer != null && !sBuffer.isRecycled() && w == sBufferW && h == sBufferH) return;
        Bitmap old = sBuffer;
        sBufferW = w;
        sBufferH = h;
        sBuffer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        if (old != null && old != sBuffer) old.recycle();
        sBufferVersion++;
    }

    /** 玻璃件用：缓冲是否就绪。 */
    public static boolean hasBuffer() {
        synchronized (LOCK) {
            return sBuffer != null && !sBuffer.isRecycled();
        }
    }

    /** 玻璃件用：当前缓冲（只读，不许改）。 */
    public static Bitmap getBuffer() {
        synchronized (LOCK) {
            return sBuffer;
        }
    }

    /** 玻璃件用：缓冲版本号，变了就重裁。 */
    public static int getBufferVersion() {
        synchronized (LOCK) {
            return sBufferVersion;
        }
    }

    public static int bufferWidth() {
        synchronized (LOCK) {
            return sBufferW;
        }
    }

    public static int bufferHeight() {
        synchronized (LOCK) {
            return sBufferH;
        }
    }

    private static final Paint sBufPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 在共享缓冲上重绘一帧：对角渐变 ＋ 4 色团漂移。调用方已持 LOCK。 */
    private static void drawBufferLocked() {
        ensureBufferSizeLocked();
        Canvas c = new Canvas(sBuffer);
        float w = sBufferW, h = sBufferH;
        float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        float amp = 25f * density / 4f; // ±25dp，缓冲内按 1/4 折算

        double t = (System.nanoTime() - sStartNanos) / 1_000_000.0 / CYCLE_MS;

        // 底：165° 对角线性渐变 #f4f6f8 → #eef2f5 → #f2f0f4
        sBufPaint.setShader(new LinearGradient(w * 0.85f, 0, w * 0.15f, h,
                new int[]{0xFFF4F6F8, 0xFFEEF2F5, 0xFFF2F0F4}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, sBufPaint);

        // 4 色团：直径约 1.3 倍屏宽，径向渐变羽化；漂移 ±25dp、缩放 ±3%
        float radius = 0.65f * w;
        for (int i = 0; i < 4; i++) {
            double ph = BLOB_PHASE[i];
            float dx = (float) (amp * Math.sin(2 * Math.PI * t + ph));
            float dy = (float) (amp * Math.cos(2 * Math.PI * t * 0.9 + ph));
            float scale = 1f + 0.03f * (float) Math.sin(2 * Math.PI * t + ph * 2);
            float cx = BLOB_X[i] * w + dx;
            float cy = BLOB_Y[i] * h + dy;
            float r = radius * scale;
            sBufPaint.setShader(new RadialGradient(cx, cy, r,
                    new int[]{BLOB_COLORS[i], 0x00000000}, null, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, sBufPaint);
        }
        sBufPaint.setShader(null);
    }
}
