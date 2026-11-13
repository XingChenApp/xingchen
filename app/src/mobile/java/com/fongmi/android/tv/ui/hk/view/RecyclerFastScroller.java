package com.fongmi.android.tv.ui.hk.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;

/**
 * 照抄 8.83 内嵌的 lqtk RecyclerFastScroller：右侧快速滚动条。
 * 滚动时 show(true) 滑入，停 1500ms 后自动滑出隐藏；拖动 handle 按比例 scrollBy 列表。
 * 原库笔误照抄：Utils.isRTL 返回的是布局方向==0(LTR)，onLayout 里留有一行 eventprint 调试日志。
 */
public class RecyclerFastScroller extends FrameLayout {

    private static final int DEFAULT_AUTO_HIDE_DELAY = 0x5dc;

    public interface ScrollListener {
        void scroll(int offset);
    }

    public interface onHandlePressedListener {
        void handle(boolean pressed);
    }

    private boolean isDrawable;
    private RecyclerView.Adapter mAdapter;
    private final RecyclerView.AdapterDataObserver mAdapterObserver;
    boolean mAnimatingIn;
    AnimatorSet mAnimator;
    int mAppBarLayoutOffset;
    protected final View mBar;
    private int mBarColor;
    private int mBarInset;
    protected final View mHandle;
    private int mHandleNormalColor;
    private int mHandlePressedColor;
    private int mHiddenTranslationX;
    private final Runnable mHide;
    private int mHideDelay;
    private boolean mHideOverride;
    private boolean mHidingEnabled;
    private int mMaxScrollHandleHeight;
    private int mMinScrollHandleHeight;
    protected View.OnTouchListener mOnTouchListener;
    RecyclerView mRecyclerView;
    private int mTouchTargetMaxWidth;
    private int mTouchTargetWidth;
    private int margiLeft = 20;
    private int minItemCount = 200;
    private Drawable normalDrawable;
    private Drawable pressedDrawable;
    private onHandlePressedListener pressedListener;
    private ScrollListener scrollListener;

    public RecyclerFastScroller(Context context) {
        this(context, null);
    }

    public RecyclerFastScroller(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecyclerFastScroller(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RecyclerFastScroller(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);
        mAdapterObserver = new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                updateHandleColorsAndInset();
                requestLayout();
            }
        };
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.RecyclerFastScroller,
                defStyleAttr, defStyleRes);
        mBarColor = array.getColor(R.styleable.RecyclerFastScroller_rfs_barColor,
                RecyclerFastScrollerUtils.resolveColor(context, androidx.appcompat.R.attr.colorControlNormal));
        mHandleNormalColor = array.getColor(R.styleable.RecyclerFastScroller_rfs_handleNormalColor,
                RecyclerFastScrollerUtils.resolveColor(context, androidx.appcompat.R.attr.colorControlNormal));
        mHandlePressedColor = array.getColor(R.styleable.RecyclerFastScroller_rfs_handlePressedColor,
                RecyclerFastScrollerUtils.resolveColor(context, androidx.appcompat.R.attr.colorAccent));
        normalDrawable = context.getResources().getDrawable(R.drawable.change_scroll_press);
        pressedDrawable = context.getResources().getDrawable(R.drawable.change_scroll_unpress);
        mTouchTargetWidth = array.getDimensionPixelSize(R.styleable.RecyclerFastScroller_rfs_touchTargetWidth,
                RecyclerFastScrollerUtils.convertDpToPx(context, 40.0f));
        mHideDelay = array.getInt(R.styleable.RecyclerFastScroller_rfs_hideDelay, DEFAULT_AUTO_HIDE_DELAY);
        mHidingEnabled = array.getBoolean(R.styleable.RecyclerFastScroller_rfs_hidingEnabled, true);
        array.recycle();
        int size86 = RecyclerFastScrollerUtils.convertDpToPx(context, 86.0f);
        setLayoutParams(new ViewGroup.LayoutParams(size86, -1));
        mBar = new View(context);
        mHandle = new View(context);
        addView(mBar);
        addView(mHandle);
        mMinScrollHandleHeight = size86;
        mMaxScrollHandleHeight = size86;
        mTouchTargetMaxWidth = size86;
        margiLeft = RecyclerFastScrollerUtils.convertDpToPx(context, (float) margiLeft);
        refreshView(mTouchTargetWidth);
        int sign = RecyclerFastScrollerUtils.isRTL(context) ? 1 : -1;
        mHiddenTranslationX = sign * mTouchTargetWidth;
        mHide = () -> {
            if (!mHandle.isPressed()) {
                if (mAnimator != null && mAnimator.isStarted()) mAnimator.cancel();
                mAnimator = new AnimatorSet();
                ObjectAnimator animator = ObjectAnimator.ofFloat(RecyclerFastScroller.this,
                        View.TRANSLATION_X, (float) mHiddenTranslationX);
                animator.setInterpolator(new FastOutLinearInInterpolator());
                animator.setDuration(150);
                mHandle.setEnabled(false);
                mAnimator.play(animator);
                mAnimator.start();
            }
        };
        mHandle.setOnTouchListener(new HandleTouchListener());
        setTranslationX((float) mHiddenTranslationX);
    }

    private void refreshView(int width) {
        if (mTouchTargetWidth > mTouchTargetMaxWidth) mTouchTargetWidth = mTouchTargetMaxWidth;
        if (mTouchTargetMaxWidth < margiLeft) {
            mBarInset = 0;
        } else {
            mBarInset = margiLeft;
        }
        setTranslationXInternal();
        mBar.setLayoutParams(new FrameLayout.LayoutParams(width, -1, 0x800005));
        mHandle.setLayoutParams(new FrameLayout.LayoutParams(width, -2, 0x800005));
        updateHandleColorsAndInset();
        updateBarColorAndInset();
    }

    private void setTranslationXInternal() {
        int sign = RecyclerFastScrollerUtils.isRTL(getContext()) ? 1 : -1;
        mHiddenTranslationX = sign * (mBarInset + mTouchTargetWidth);
    }

    private void updateBarColorAndInset() {
        InsetDrawable inset;
        if (RecyclerFastScrollerUtils.isRTL(getContext())) {
            inset = new InsetDrawable(new ColorDrawable(mBarColor), 0, 0, mBarInset, 0);
        } else {
            inset = new InsetDrawable(new ColorDrawable(mBarColor), mBarInset, 0, 0, 0);
        }
        inset.setAlpha(0x39);
        RecyclerFastScrollerUtils.setViewBackground(mBar, inset);
    }

    private void updateHandleColorsAndInset() {
        StateListDrawable states = new StateListDrawable();
        boolean rtl = RecyclerFastScrollerUtils.isRTL(getContext());
        Drawable pressed = isTouchDrawable() ? pressedDrawable : new ColorDrawable(mHandlePressedColor);
        Drawable normal = isTouchDrawable() ? normalDrawable : new ColorDrawable(mHandleNormalColor);
        if (rtl) {
            states.addState(View.PRESSED_ENABLED_STATE_SET, new InsetDrawable(pressed, 0, 0, mBarInset, 0));
            states.addState(View.EMPTY_STATE_SET, new InsetDrawable(normal, 0, 0, mBarInset, 0));
        } else {
            states.addState(View.PRESSED_ENABLED_STATE_SET, new InsetDrawable(pressed, mBarInset, 0, 0, 0));
            states.addState(View.EMPTY_STATE_SET, new InsetDrawable(normal, mBarInset, 0, 0, 0));
        }
        RecyclerFastScrollerUtils.setViewBackground(mHandle, states);
    }

    public void attachAdapter(RecyclerView.Adapter adapter) {
        if (mAdapter == adapter) return;
        if (mAdapter != null) mAdapter.unregisterAdapterDataObserver(mAdapterObserver);
        if (adapter != null) adapter.registerAdapterDataObserver(mAdapterObserver);
        mAdapter = adapter;
        updateHandleColorsAndInset();
    }

    public void attachRecyclerView(RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                show(true);
            }
        });
        if (recyclerView.getAdapter() != null) attachAdapter(recyclerView.getAdapter());
    }

    public int getBarColor() {
        return mBarColor;
    }

    public int getHandleNormalColor() {
        return mHandleNormalColor;
    }

    public int getHandlePressedColor() {
        return mHandlePressedColor;
    }

    public int getHideDelay() {
        return mHideDelay;
    }

    public onHandlePressedListener getPressedListener() {
        return pressedListener;
    }

    public ScrollListener getScrollListener() {
        return scrollListener;
    }

    public int getTouchTargetWidth() {
        return mTouchTargetWidth;
    }

    public boolean isHidingEnabled() {
        return mHidingEnabled;
    }

    public boolean isTouchDrawable() {
        return isDrawable;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mRecyclerView == null) return;
        int offset = mRecyclerView.computeVerticalScrollOffset() + mAppBarLayoutOffset;
        if (scrollListener != null) scrollListener.scroll(offset);
        int range = mRecyclerView.computeVerticalScrollRange() + mRecyclerView.getPaddingBottom();
        int barHeight = mBar.getHeight();
        if (range <= 0 || barHeight <= 0) {
            setTranslationX((float) mHiddenTranslationX);
            mHideOverride = true;
            return;
        }
        float ratio = range > barHeight ? (float) offset / (float) (range - barHeight) : 0f;
        int handleHeight = (int) ((float) barHeight / (float) range * (float) barHeight);
        if (handleHeight < mMinScrollHandleHeight) handleHeight = mMinScrollHandleHeight;
        if (handleHeight > mMaxScrollHandleHeight) handleHeight = mMaxScrollHandleHeight;
        if (handleHeight >= barHeight) {
            setTranslationX((float) mHiddenTranslationX);
            mHideOverride = true;
            return;
        }
        mHideOverride = false;
        int top = (int) (ratio * (float) (barHeight - handleHeight));
        Log.d("eventprint", mHandle.getBottom() + ",2");
        mHandle.layout(mHandle.getLeft(), top, mHandle.getRight(), top + handleHeight);
    }

    void postAutoHide() {
        if (mRecyclerView != null && mHidingEnabled) {
            mRecyclerView.removeCallbacks(mHide);
            mRecyclerView.postDelayed(mHide, (long) mHideDelay);
        }
    }

    public void setBarColor(int color) {
        mBarColor = color;
        updateBarColorAndInset();
    }

    public void setDrawable(Drawable normal, Drawable pressed) {
        normalDrawable = normal;
        pressedDrawable = pressed;
        isDrawable = true;
        updateHandleColorsAndInset();
    }

    public void setHandleNormalColor(int color) {
        mHandleNormalColor = color;
        updateHandleColorsAndInset();
    }

    public void setHandlePressedColor(int color) {
        mHandlePressedColor = color;
        updateHandleColorsAndInset();
    }

    public void setHideDelay(int delay) {
        mHideDelay = delay;
    }

    public void setHidingEnabled(boolean enabled) {
        mHidingEnabled = enabled;
        if (enabled) postAutoHide();
    }

    public void setMarginLeft(int dp) {
        int px = RecyclerFastScrollerUtils.convertDpToPx(getContext(), (float) dp);
        mTouchTargetWidth = mTouchTargetWidth - margiLeft + px;
        margiLeft = px;
        refreshView(mTouchTargetWidth);
    }

    public void setMaxScrollHandleHeight(int height) {
        mMaxScrollHandleHeight = height;
    }

    public void setMinItemCount(int count) {
        minItemCount = count;
    }

    public void setOnHandleTouchListener(View.OnTouchListener listener) {
        mOnTouchListener = listener;
    }

    public void setPressedListener(onHandlePressedListener listener) {
        pressedListener = listener;
    }

    public void setScrollListener(ScrollListener listener) {
        scrollListener = listener;
    }

    public void setTouchTargetWidth(int dp) {
        int px = RecyclerFastScrollerUtils.convertDpToPx(getContext(), (float) dp);
        mTouchTargetWidth = px + margiLeft;
        refreshView(mTouchTargetWidth);
    }

    public void setmTouchTargetMaxWidth(int dp) {
        if (getContext() != null) {
            mTouchTargetMaxWidth = RecyclerFastScrollerUtils.convertDpToPx(getContext(), (float) dp);
        } else {
            mTouchTargetMaxWidth = dp;
        }
        refreshView(mTouchTargetWidth);
    }

    public void show(final boolean animate) {
        requestLayout();
        post(() -> {
            if (mHideOverride) return;
            if (mAdapter != null && mAdapter.getItemCount() < minItemCount) return;
            mHandle.setEnabled(true);
            if (animate) {
                if (!mAnimatingIn && getTranslationX() != 0f) {
                    if (mAnimator != null && mAnimator.isStarted()) mAnimator.cancel();
                    mAnimator = new AnimatorSet();
                    ObjectAnimator animator = ObjectAnimator.ofFloat(RecyclerFastScroller.this,
                            View.TRANSLATION_X, 0f);
                    animator.setInterpolator(new LinearOutSlowInInterpolator());
                    animator.setDuration(100);
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            mAnimatingIn = false;
                        }
                    });
                    mAnimatingIn = true;
                    mAnimator.play(animator);
                    mAnimator.start();
                }
            } else {
                setTranslationX(0f);
            }
            postAutoHide();
        });
    }

    public void touchIsDrawable(boolean drawable) {
        isDrawable = drawable;
        updateHandleColorsAndInset();
    }

    void updateRvScroll(int dy) {
        if (mRecyclerView != null && mHandle != null) {
            try {
                mRecyclerView.scrollBy(0, dy);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    /** 照抄原版 $3：handle 的触摸处理（按下停滚+嵌套滚动，拖动按 bar 高比例换算列表滚动，抬起自动隐藏）。 */
    private class HandleTouchListener implements View.OnTouchListener {
        private float mInitialBarHeight;
        private int mLastAppBarLayoutOffset;
        private float mLastPressedYAdjustedToInitial;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mRecyclerView == null) return false;
            if (mOnTouchListener != null) mOnTouchListener.onTouch(v, event);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                mHandle.setPressed(true);
                if (pressedListener != null) pressedListener.handle(true);
                mRecyclerView.stopScroll();
                mRecyclerView.startNestedScroll(2);
                mInitialBarHeight = (float) mBar.getHeight();
                mLastPressedYAdjustedToInitial = event.getY() + mHandle.getY() + mBar.getY();
                mLastAppBarLayoutOffset = mAppBarLayoutOffset;
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (mInitialBarHeight <= 0) return true;
                float y = event.getY() + mHandle.getY() + mBar.getY()
                        + (mInitialBarHeight - (float) mBar.getHeight());
                int dy = (int) ((y - mLastPressedYAdjustedToInitial) / mInitialBarHeight
                        * (float) mRecyclerView.computeVerticalScrollRange())
                        + mLastAppBarLayoutOffset - mAppBarLayoutOffset;
                updateRvScroll(dy);
                mLastPressedYAdjustedToInitial = y;
                mLastAppBarLayoutOffset = mAppBarLayoutOffset;
            } else if (action == MotionEvent.ACTION_UP) {
                mLastPressedYAdjustedToInitial = -1.0f;
                mRecyclerView.stopNestedScroll();
                mHandle.setPressed(false);
                if (pressedListener != null) pressedListener.handle(false);
                postAutoHide();
            }
            return true;
        }
    }
}
