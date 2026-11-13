package com.fongmi.android.tv.ui.hk.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

/**
 * 照抄 8.83 MyWebProgress（ui/browser/view）：网页式细进度条。
 * show() 后匀速爬向 95（先加速到 30 再匀速），setProgress 跟随外部进度，
 * 到 95 以上或 hide() 走收尾：减速到 95 → 95→100 与 alpha 1→0 并行（330ms)，结束 GONE 并清零。
 * CURRENT_MAX_* 时长随控件宽占屏宽比例缩放（onSizeChanged)。
 */
public class MyWebProgress extends FrameLayout {

    private static int CURRENT_MAX_DECELERATE_SPEED_DURATION = 0x15e;
    private static int CURRENT_MAX_UNIFORM_SPEED_DURATION = 0x1f40;
    public static final int DO_END_ALPHA_DURATION = 0x14a;
    public static final int DO_END_PROGRESS_DURATION = 0xc8;
    public static final int FINISH = 0x2;
    public static final int MAX_DECELERATE_SPEED_DURATION = 0x15e;
    public static final int MAX_UNIFORM_SPEED_DURATION = 0x1f40;
    public static final int STARTED = 0x1;
    public static final int UN_START = 0x0;
    public static String WEB_PROGRESS_COLOR = "#2483D9";
    public static int WEB_PROGRESS_DEFAULT_HEIGHT = 0x3;

    private int TAG = UN_START;
    private boolean isShow = false;
    private Animator mAnimator;
    private final ValueAnimator.AnimatorUpdateListener mAnimatorUpdateListener;
    private int mColor;
    private float mCurrentProgress = 0f;
    private Paint mPaint;
    private int mTargetHeight;
    private int mTargetWidth = 0;
    private long ticket = 0L;

    public MyWebProgress(Context context) {
        this(context, null);
    }

    public MyWebProgress(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MyWebProgress(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mAnimatorUpdateListener = animation -> {
            mCurrentProgress = (Float) animation.getAnimatedValue();
            invalidate();
        };
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        mPaint = new Paint();
        mColor = Color.parseColor(WEB_PROGRESS_COLOR);
        mPaint.setAntiAlias(true);
        mPaint.setColor(mColor);
        mPaint.setDither(true);
        mPaint.setStrokeCap(Paint.Cap.SQUARE);
        mTargetWidth = context.getResources().getDisplayMetrics().widthPixels;
        mTargetHeight = dip2px((float) WEB_PROGRESS_DEFAULT_HEIGHT);
    }

    private int dip2px(float dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private void doEnd() {
        setVisibility(GONE);
        mCurrentProgress = 0f;
        setAlpha(1.0f);
        TAG = UN_START;
    }

    private void setFinish() {
        isShow = false;
        TAG = FINISH;
    }

    private void startAnim(boolean isFinish) {
        float target = isFinish ? 100.0f : 95.0f;
        if (mAnimator != null && mAnimator.isStarted()) mAnimator.cancel();
        if (mCurrentProgress == 0f) mCurrentProgress = 1.0E-8f;
        if (!isFinish) {
            setAlpha(1.0f);
        } else if (!(getAlpha() < 0.5f)) {
            setAlpha(1.0f);
        }
        ticket = System.currentTimeMillis();
        if (!isFinish) {
            float cur = mCurrentProgress;
            if (cur < 30.0f) {
                ValueAnimator stage1 = ValueAnimator.ofFloat(cur, 30.0f);
                long total = (long) ((1.0f - cur / 100.0f - 0.05f) * CURRENT_MAX_UNIFORM_SPEED_DURATION);
                long d1 = Math.min((long) ((float) total * 0.3f), 200L);
                long d2 = total - d1;
                stage1.setInterpolator(new AccelerateInterpolator());
                stage1.setDuration(d1);
                stage1.addUpdateListener(mAnimatorUpdateListener);
                ValueAnimator stage2 = ValueAnimator.ofFloat(30.0f, target);
                stage2.setInterpolator(new LinearInterpolator());
                stage2.setDuration(d2);
                stage2.addUpdateListener(mAnimatorUpdateListener);
                AnimatorSet set = new AnimatorSet();
                set.playSequentially(stage1, stage2);
                set.start();
                mAnimator = set;
            } else {
                ValueAnimator anim = ValueAnimator.ofFloat(cur, target);
                anim.setInterpolator(new LinearInterpolator());
                anim.setDuration((long) ((1.0f - cur / 100.0f - 0.05f) * CURRENT_MAX_UNIFORM_SPEED_DURATION));
                anim.addUpdateListener(mAnimatorUpdateListener);
                anim.start();
                mAnimator = anim;
            }
        } else {
            ValueAnimator toNinetyFive = null;
            float cur = mCurrentProgress;
            if (cur < 95.0f) {
                toNinetyFive = ValueAnimator.ofFloat(cur, 95.0f);
                toNinetyFive.setInterpolator(new LinearInterpolator());
                toNinetyFive.setDuration((long) ((1.0f - cur / 100.0f - 0.05f) * CURRENT_MAX_DECELERATE_SPEED_DURATION));
                toNinetyFive.setInterpolator(new DecelerateInterpolator());
                toNinetyFive.addUpdateListener(mAnimatorUpdateListener);
            }
            ObjectAnimator alpha = ObjectAnimator.ofFloat(this, "alpha", 1.0f, 0.0f);
            alpha.setDuration(DO_END_ALPHA_DURATION);
            ValueAnimator tail = ValueAnimator.ofFloat(95.0f, 100.0f);
            tail.setDuration(DO_END_PROGRESS_DURATION);
            tail.addUpdateListener(mAnimatorUpdateListener);
            AnimatorSet tailSet = new AnimatorSet();
            tailSet.playTogether(alpha, tail);
            AnimatorSet set = tailSet;
            if (toNinetyFive != null) {
                set = new AnimatorSet();
                set.play(tailSet).after(toNinetyFive);
            }
            final long bindTicket = ticket;
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (bindTicket == ticket) doEnd();
                }
            });
            set.start();
            mAnimator = set;
        }
        TAG = STARTED;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        float right = mCurrentProgress / 100.0f * getWidth();
        canvas.drawRect(0f, 0f, right, (float) getHeight(), mPaint);
    }

    public void hide() {
        setWebProgress(100);
    }

    public FrameLayout.LayoutParams offerLayoutParams() {
        return new FrameLayout.LayoutParams(mTargetWidth, mTargetHeight);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAnimator != null && mAnimator.isStarted()) {
            mAnimator.cancel();
            mAnimator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (widthMode == MeasureSpec.AT_MOST) {
            int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
            width = Math.min(width, screenWidth);
        }
        if (heightMode == MeasureSpec.AT_MOST) {
            height = mTargetHeight;
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mTargetWidth = getMeasuredWidth();
        int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        if (mTargetWidth >= screenWidth) {
            CURRENT_MAX_DECELERATE_SPEED_DURATION = MAX_DECELERATE_SPEED_DURATION;
            CURRENT_MAX_UNIFORM_SPEED_DURATION = MAX_UNIFORM_SPEED_DURATION;
        } else {
            float ratio = (float) mTargetWidth / (float) screenWidth;
            CURRENT_MAX_UNIFORM_SPEED_DURATION = (int) (8000.0f * ratio);
            CURRENT_MAX_DECELERATE_SPEED_DURATION = (int) (ratio * 350.0f);
        }
    }

    public void reset() {
        mCurrentProgress = 0f;
        if (mAnimator != null && mAnimator.isStarted()) mAnimator.cancel();
    }

    public void setColor(int color) {
        mColor = color;
        mPaint.setColor(color);
    }

    public void setColor(int startColor, int endColor) {
        LinearGradient gradient = new LinearGradient(0f, 0f, (float) mTargetWidth, (float) mTargetHeight,
                startColor, endColor, Shader.TileMode.CLAMP);
        mPaint.setShader(gradient);
    }

    public void setColor(String color) {
        setColor(Color.parseColor(color));
    }

    public void setColor(String startColor, String endColor) {
        setColor(Color.parseColor(startColor), Color.parseColor(endColor));
    }

    public MyWebProgress setHeight(int heightDp) {
        mTargetHeight = dip2px((float) heightDp);
        return this;
    }

    public void setProgress(float progress) {
        if (TAG == UN_START && progress == 100.0f) {
            setVisibility(GONE);
            return;
        }
        if (TAG == FINISH && progress == 100.0f) return;
        if (getVisibility() == GONE) setVisibility(VISIBLE);
        if (progress < 95.0f) {
            if (progress > 30.0f) {
                float delta = progress - mCurrentProgress;
                if (delta > 3.0f) {
                    if (mAnimator != null && mAnimator.isStarted()) mAnimator.cancel();
                    if (mCurrentProgress == 0f) mCurrentProgress = 1.0E-8f;
                    setAlpha(1.0f);
                    long total = (long) ((1.0f - mCurrentProgress / 100.0f - 0.05f)
                            * CURRENT_MAX_UNIFORM_SPEED_DURATION);
                    long d1 = Math.min((long) ((float) total * progress / 100.0f), 200L);
                    long d2 = total - d1;
                    ValueAnimator stage1 = ValueAnimator.ofFloat(mCurrentProgress, progress);
                    stage1.setInterpolator(new AccelerateInterpolator());
                    stage1.setDuration(d1);
                    stage1.addUpdateListener(mAnimatorUpdateListener);
                    ValueAnimator stage2 = ValueAnimator.ofFloat(progress, 95.0f);
                    stage2.setInterpolator(new LinearInterpolator());
                    stage2.setDuration(d2);
                    stage2.addUpdateListener(mAnimatorUpdateListener);
                    AnimatorSet set = new AnimatorSet();
                    set.playSequentially(stage1, stage2);
                    set.start();
                    mAnimator = set;
                }
            }
            return;
        }
        if (TAG != FINISH) startAnim(true);
    }

    public void setProgress(int progress) {
        setProgress((float) progress);
    }

    public void setWebProgress(int progress) {
        if (progress >= 0 && progress < 95) {
            if (!isShow) {
                show();
            } else {
                setProgress(progress);
            }
        } else {
            setProgress(100);
            setFinish();
        }
    }

    public void show() {
        isShow = true;
        setVisibility(VISIBLE);
        mCurrentProgress = 0f;
        startAnim(false);
    }
}
