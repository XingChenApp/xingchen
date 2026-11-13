package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.fongmi.android.tv.R;

/**
 * 渐入渐出统一入口（海阔试水批先行，全 App 推广时复用同一套）。
 * 只管过渡动画：200ms 纯 alpha，加速曲线柔和；
 * 用户在系统里关了动画（或省电模式把动画缩放清零）时自动降级为无动画直达，不闪不跳。
 * 动画与页面加载并行（只改透明度、不阻塞任何加载链），不拖打开速度体感。
 */
public final class XcFadeTransition {

    /** 全 App 统一时长：200ms（180–240ms 区间内选档）。 */
    public static final int DURATION_MS = 200;

    private XcFadeTransition() {
    }

    // 动画代次（深挖渐入渐出②③）：fadeOut 的结束回调必须确认自己仍是该视图
    // 最新一代动画才许置 GONE，否则淡出途中被 fadeIn 打断后旧回调会把刚唤出的
    // 视图打回 GONE；fadeIn 重入也不许把 alpha 清零重升（闪烁根源）。
    private static final java.util.WeakHashMap<View, Integer> GENERATIONS = new java.util.WeakHashMap<>();

    private static int bumpGeneration(View v) {
        synchronized (GENERATIONS) {
            int gen = GENERATIONS.getOrDefault(v, 0) + 1;
            GENERATIONS.put(v, gen);
            return gen;
        }
    }

    private static boolean isCurrentGeneration(View v, int gen) {
        synchronized (GENERATIONS) {
            return GENERATIONS.getOrDefault(v, 0) == gen;
        }
    }

    private static final java.util.WeakHashMap<View, Boolean> FADING_OUT = new java.util.WeakHashMap<>();

    /** 该视图是否正在淡出途中：显隐判定（如点屏唤栏）应把淡出中的视图当作「未显示」，
     * 否则 200ms 窗口内的点击会走隐藏分支被吞（深挖渐入渐出①）。 */
    public static boolean isFadingOut(View v) {
        if (v == null) return false;
        synchronized (GENERATIONS) {
            return Boolean.TRUE.equals(FADING_OUT.get(v));
        }
    }

    private static void setFadingOut(View v, boolean fadingOut) {
        synchronized (GENERATIONS) {
            if (fadingOut) FADING_OUT.put(v, Boolean.TRUE);
            else FADING_OUT.remove(v);
        }
    }

    /** 系统动画开关是否允许动画（任一缩放为 0 即视为关闭）。 */
    public static boolean isAnimEnabled(Context context) {
        if (context == null) return false;
        try {
            ContentResolver cr = context.getContentResolver();
            float animator = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            float transition = Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f);
            float window = Settings.Global.getFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, 1f);
            return animator > 0f && transition > 0f && window > 0f;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** 视图淡入（关闭动画时直接显示）。 */
    public static void fadeIn(View v) {
        if (v == null) return;
        bumpGeneration(v);
        setFadingOut(v, false);
        boolean inFlight = v.getVisibility() == View.VISIBLE && v.getAlpha() > 0f && v.getAlpha() < 1f;
        // 先 cancel 再判直达：同帧刚起过 fadeOut（alpha 还没来得及降）时若直接 return，
        // 那条淡出动画会继续把 alpha 压到 0、视图留成「VISIBLE 但全透明」（调用方
        // 先藏后显的组合必中）。cancel 对无动画在飞的视图是空操作，不影响直达口径。
        v.animate().cancel();
        if (v.getVisibility() == View.VISIBLE && v.getAlpha() >= 1f) return;
        if (!isAnimEnabled(v.getContext())) {
            v.setAlpha(1f);
            v.setVisibility(View.VISIBLE);
            return;
        }
        if (inFlight) {
            // 淡入/淡出进行中被重入：从当前 alpha 续升，不清零、不重来
            float current = v.getAlpha();
            long duration = Math.max(1L, (long) (DURATION_MS * (1f - current)));
            v.animate().alpha(1f).setDuration(duration)
                    .setInterpolator(new AccelerateDecelerateInterpolator()).start();
            return;
        }
        v.setAlpha(0f);
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).setDuration(DURATION_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    /** 视图淡出后 GONE（关闭动画时直接 GONE）。 */
    public static void fadeOutThenGone(View v) {
        if (v == null) return;
        int generation = bumpGeneration(v);
        v.animate().cancel();
        if (v.getVisibility() == View.GONE || !isAnimEnabled(v.getContext())) {
            setFadingOut(v, false);
            v.setVisibility(View.GONE);
            return;
        }
        setFadingOut(v, true);
        v.animate().alpha(0f).setDuration(DURATION_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (isCurrentGeneration(v, generation)) {
                        setFadingOut(v, false);
                        v.setVisibility(View.GONE);
                    }
                }).start();
    }

    /** 内页切换：新页淡入、旧页淡出（并行，不互相等待）。 */
    public static void swapViews(View show, View... hides) {
        if (hides != null) {
            for (View h : hides) {
                if (h != null && h != show) fadeOutThenGone(h);
            }
        }
        if (show != null) fadeIn(show);
    }

    /** Activity 进场淡入（动画关闭时框架自己会跳过，仍安全）。 */
    public static void applyEnter(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
    }

    /** Activity 退场淡出。 */
    public static void applyExit(Activity activity) {
        if (activity == null) return;
        activity.overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
    }
}
