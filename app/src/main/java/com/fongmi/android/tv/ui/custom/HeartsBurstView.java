package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 用途：爱心粒子爆发覆盖层——透明、不可点透传。两套 GZ 实现分开：
 * 封面双击收藏爆发（BurstHeart，随机参数，1200ms 线性时间线）与
 * 收藏按钮爆发（HeartBurstParticles，8 颗固定参数表，620ms）。
 * 参数出处：GZ 反编译参数对照表第 5 节（PlayerScreenKt／FavoriteBurstButtonKt），2026-10-21 对齐。
 */
public class HeartsBurstView extends View {

    // ===== 常量区 =====
    /** 爱心填充色（GZ AccentRed 0xFFFA243C 原值，SheetTokens/PlayerToolsSheetKt 实证）。 */
    public static final int HEART_COLOR = 0xFFFA243C;

    // 5A 封面爆发（GZ PlayerScreenKt genBurstHearts＋粒子合成 :5363-5420）
    /** 数量：大爆发 8 颗／普通 5 颗。 */
    private static final int COVER_COUNT_BIG = 8;
    private static final int COVER_COUNT_NORMAL = 5;
    /** 爆发总时长：1200ms 线性时间线（bs4.g）。 */
    private static final long COVER_DURATION_MS = 1200L;

    // 5B 收藏按钮爆发（GZ FavoriteBurstButtonKt，8 颗固定 BurstRay）
    /** 爆发总时长：620ms，缓动 cubicBezier(0.2,0,0,1)（bs4.a）。 */
    private static final long FAVORITE_DURATION_MS = 620L;
    /** 粒子绘制盒：22dp（FavoriteBurstButtonKt:432）。 */
    private static final float FAVORITE_BOX_DP = 22f;
    /**
     * 位移基距：46dp 常量（近似）。GZ 原口径为「容器边长＝触发点所在按钮边长 ×1.35」再由此导出基距 E0，
     * 本 View 与调用方按钮尺寸无关、无从得知，故按源码量级取 46dp 近似，见 FavoriteBurstButtonKt:247/417-431。
     */
    private static final float FAVORITE_BASE_DISTANCE_DP = 46f;
    /** 8 颗固定参数：（角度°, 距离倍率, 大小倍率, 自旋°, alpha 倍率），顺序照 GZ 参数表。 */
    private static final float[][] FAVORITE_RAYS = {
            {-95f, 1.00f, 0.42f, -28f, 1.00f},
            {-52f, 0.92f, 0.36f, 18f, 0.85f},
            {-12f, 1.08f, 0.48f, -12f, 1.00f},
            {28f, 0.95f, 0.34f, 22f, 0.80f},
            {68f, 1.05f, 0.40f, -16f, 0.90f},
            {112f, 0.88f, 0.30f, 30f, 0.75f},
            {158f, 0.78f, 0.28f, -20f, 0.70f},
            {-140f, 0.82f, 0.32f, 14f, 0.80f},
    };

    private static final int KIND_COVER = 0;
    private static final int KIND_FAVORITE = 1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path heartPath = buildHeartPath();
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private final Choreographer choreographer = Choreographer.getInstance();
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            framePosted = false;
            stepFrame();
        }
    };
    private boolean framePosted;

    public HeartsBurstView(Context context) {
        this(context, null);
    }

    public HeartsBurstView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HeartsBurstView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint.setColor(HEART_COLOR);
        paint.setStyle(Paint.Style.FILL);
        setClickable(false);
        setFocusable(false);
        // 覆盖层不拦截触摸，事件透传给下层。
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        return false;
    }

    /** 旧入口保留：转发封面普通爆发（可能有引用，别删）。 */
    public void burst(float cx, float cy) {
        burstCover(cx, cy, false);
    }

    /**
     * 5A 封面双击收藏爆发：在 (cx, cy) 喷出 big?8:5 颗爱心，向上扇形扩散，1200ms 线性时间线。
     * 每颗随机：角度 rand×120−60°、行程 rand×48＋42dp、大小 rand×10＋9dp、旋转 rand×50−25°、
     * 延迟 rand×0.08＋序号×0.035（GZ genBurstHearts，种子对应 GZ 的 nonce，此处用 Random 等价）。
     */
    public void burstCover(float cx, float cy, boolean big) {
        float density = getResources().getDisplayMetrics().density;
        long now = android.os.SystemClock.elapsedRealtime();
        int count = big ? COVER_COUNT_BIG : COVER_COUNT_NORMAL;
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.kind = KIND_COVER;
            p.cx = cx;
            p.cy = cy;
            p.bornMs = now;
            p.lifeMs = COVER_DURATION_MS;
            p.angleDeg = random.nextFloat() * 120f - 60f;
            p.travelPx = (random.nextFloat() * 48f + 42f) * density;
            p.sizePx = (random.nextFloat() * 10f + 9f) * density;
            p.rotationDeg = random.nextFloat() * 50f - 25f;
            p.delayFraction = random.nextFloat() * 0.08f + i * 0.035f;
            particles.add(p);
        }
        ensureFrameLoop();
        invalidate();
    }

    /** 5B 收藏按钮爆发：在 (cx, cy) 按 8 颗固定参数表喷出，620ms，缓动 cubicBezier(0.2,0,0,1)。 */
    public void burstFavorite(float cx, float cy) {
        long now = android.os.SystemClock.elapsedRealtime();
        for (float[] ray : FAVORITE_RAYS) {
            Particle p = new Particle();
            p.kind = KIND_FAVORITE;
            p.cx = cx;
            p.cy = cy;
            p.bornMs = now;
            p.lifeMs = FAVORITE_DURATION_MS;
            p.angleDeg = ray[0];
            p.distMul = ray[1];
            p.sizeMul = ray[2];
            p.rotationDeg = ray[3];
            p.alphaMul = ray[4];
            particles.add(p);
        }
        ensureFrameLoop();
        invalidate();
    }

    private void ensureFrameLoop() {
        if (framePosted || particles.isEmpty()) return;
        framePosted = true;
        choreographer.postFrameCallback(frameCallback);
    }

    private void stepFrame() {
        long now = android.os.SystemClock.elapsedRealtime();
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            if (now - p.bornMs >= p.lifeMs) it.remove();
        }
        invalidate();
        if (!particles.isEmpty()) {
            framePosted = true;
            choreographer.postFrameCallback(frameCallback);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (particles.isEmpty()) return;
        long now = android.os.SystemClock.elapsedRealtime();
        float density = getResources().getDisplayMetrics().density;
        for (Particle p : particles) {
            float progress = clamp((now - p.bornMs) / (float) p.lifeMs, 0f, 1f);
            if (p.kind == KIND_COVER) drawCoverParticle(canvas, p, progress);
            else drawFavoriteParticle(canvas, p, progress, density);
        }
        paint.setAlpha(255);
    }

    /** 5A 单颗合成（GZ PlayerScreenKt:5363-5420）：局部进度 v、easeOutQuad 位移、回弹缩放、alpha 分段、旋转。 */
    private void drawCoverParticle(Canvas canvas, Particle p, float progress) {
        float v = clamp((progress - p.delayFraction) / (1f - p.delayFraction), 0f, 1f);
        if (v <= 0.001f) return;
        // 距离＝行程 ×(1−(1−v)²)；方向 x=sin、y=−cos（角度 0 为正上方）。
        float distance = p.travelPx * (1f - (1f - v) * (1f - v));
        double radians = Math.toRadians(p.angleDeg);
        float dx = (float) Math.sin(radians) * distance;
        float dy = -(float) Math.cos(radians) * distance;
        // 缩放＝(1−clamp(v−0.35,0,1)×0.3) ×贝塞尔(0.34,1.56,0.64,1)(clamp(v/0.16))。
        float scale = (1f - clamp(v - 0.35f, 0f, 1f) * 0.3f)
                * cubicBezier(0.34f, 1.56f, 0.64f, 1f, clamp(v / 0.16f, 0f, 1f));
        float size = p.sizePx * scale;
        // alpha：v<0.06 淡入 v/0.06；v>0.55 时 (1−v)/0.45 淡出；否则 1。
        float alphaF = v < 0.06f ? v / 0.06f : (v > 0.55f ? clamp((1f - v) / 0.45f, 0f, 1f) : 1f);
        // 旋转：GZ 源码 lambda$48$9$3$0 实证为 rotationDeg ×局部进度 v（参数表写作「×alpha 进度值」，以源码为准）。
        float rotation = p.rotationDeg * v;
        drawHeart(canvas, p.cx + dx, p.cy + dy, size, rotation, alphaF);
    }

    /** 5B 单颗合成（GZ FavoriteBurstButtonKt:417-431）：e 为缓动后的展开进度，位移／上抬／缩放／旋转／alpha 照源码。 */
    private void drawFavoriteParticle(Canvas canvas, Particle p, float progress, float density) {
        float e = cubicBezier(0.2f, 0f, 0f, 1f, clamp(progress / 0.55f, 0f, 1f));
        float basePx = FAVORITE_BASE_DISTANCE_DP * density;
        double radians = Math.toRadians(p.angleDeg);
        // 位移＝dist×基距×e（x=cos、y=sin，屏幕 y 向下故负角朝上）；
        // 额外上抬 0.22×基距×e，上浮抑制 (1−e×0.35) 乘在上抬量上（源码 sin 分量实证）。
        float d = p.distMul * basePx * e;
        float dx = (float) Math.cos(radians) * d;
        float dy = (float) Math.sin(radians) * d - (1f - e * 0.35f) * (0.22f * basePx * e);
        // 缩放＝((1−e)×0.35＋0.85)×size 倍率，作用在 22dp 粒子盒上。
        float size = FAVORITE_BOX_DP * density * (((1f - e) * 0.35f + 0.85f) * p.sizeMul);
        float rotation = p.rotationDeg * e;
        float alphaF = p.alphaMul * (progress < 0.35f ? 1f : clamp(1f - (progress - 0.35f) / 0.65f, 0f, 1f));
        drawHeart(canvas, p.cx + dx, p.cy + dy, size, rotation, alphaF);
    }

    private void drawHeart(Canvas canvas, float x, float y, float sizePx, float rotationDeg, float alphaF) {
        if (sizePx <= 0f || alphaF <= 0f) return;
        paint.setAlpha(Math.round(255f * clamp(alphaF, 0f, 1f)));
        int save = canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotationDeg);
        canvas.scale(sizePx, sizePx);
        canvas.drawPath(heartPath, paint);
        canvas.restoreToCount(save);
    }

    /** 三次贝塞尔缓动求值：解 x(t)＝x 反出 t 再算 y(t)（二分 12 次，与 GZ cubicBezier 解法同量级）。 */
    static float cubicBezier(float x1, float y1, float x2, float y2, float x) {
        float input = clamp(x, 0f, 1f);
        if (input <= 0f) return 0f;
        if (input >= 1f) return 1f;
        float lo = 0f;
        float hi = 1f;
        for (int i = 0; i < 12; i++) {
            float mid = (lo + hi) * 0.5f;
            if (bezierAxis(x1, x2, mid) < input) lo = mid;
            else hi = mid;
        }
        return bezierAxis(y1, y2, (lo + hi) * 0.5f);
    }

    private static float bezierAxis(float p1, float p2, float t) {
        float u = 1f - t;
        return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 单位心形路径（坐标约在 −0.55..0.55，绘制时按粒子大小缩放）。 */
    private static Path buildHeartPath() {
        Path path = new Path();
        path.moveTo(0f, 0.38f);
        path.cubicTo(-0.58f, 0.02f, -0.36f, -0.52f, 0f, -0.20f);
        path.cubicTo(0.36f, -0.52f, 0.58f, 0.02f, 0f, 0.38f);
        path.close();
        return path;
    }

    @Override
    protected void onDetachedFromWindow() {
        choreographer.removeFrameCallback(frameCallback);
        framePosted = false;
        particles.clear();
        super.onDetachedFromWindow();
    }

    private static final class Particle {
        int kind;
        float cx;
        float cy;
        long bornMs;
        long lifeMs;
        float angleDeg;
        // 封面爆发参数
        float travelPx;
        float sizePx;
        float delayFraction;
        // 收藏按钮爆发参数（与封面共用 angleDeg／rotationDeg，后者在 5B 即自旋 spin）
        float distMul;
        float sizeMul;
        float alphaMul;
        float rotationDeg;
    }
}
