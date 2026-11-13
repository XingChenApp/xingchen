package com.fongmi.android.tv.ui.custom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

public class CustomKeyDown extends GestureDetector.SimpleOnGestureListener implements ScaleGestureDetector.OnScaleGestureListener {

    private static final int DISTANCE = 100;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector detector;
    private final AudioManager manager;
    private final Listener listener;
    private final Activity activity;
    private final View videoView;
    private boolean changeBright;
    private boolean changeVolume;
    private boolean changeSpeed;
    private boolean changeScale;
    private boolean changeTime;
    private boolean changePage;
    private boolean pageSettling;
    private boolean pageFlingHandled;
    private boolean multiTouch;
    private boolean animating;
    private float pageDy;
    private float pageVelocity;
    private long pageLastTime;
    private boolean touch;
    private boolean lock;
    private float bright;
    private float currentBright;
    private float volume;
    private float scale;
    private long time;

    public static CustomKeyDown create(Activity activity, View videoView) {
        return new CustomKeyDown(activity, videoView);
    }

    private CustomKeyDown(Activity activity, View videoView) {
        this.manager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        this.scaleDetector = new ScaleGestureDetector(activity, this);
        this.detector = new GestureDetector(activity, this);
        this.listener = (Listener) activity;
        this.videoView = videoView;
        this.activity = activity;
        this.scale = 1.0f;
        applyBrightness();
    }

    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            multiTouch = false;
            pageFlingHandled = false;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) multiTouch = true;
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && changePage) {
            changePage = false;
            pageFlingHandled = true;
            settlePage(action == MotionEvent.ACTION_UP && shouldCommitPage());
        }
        if (action == MotionEvent.ACTION_UP) listener.onTouchEnd();
        if (changeBright && action == MotionEvent.ACTION_UP) PlayerSetting.putBrightness(currentBright);
        if (changeSpeed && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) listener.onSpeedEnd();
        if (changeTime && action == MotionEvent.ACTION_UP) listener.onSeekEnd(time);
        return e.getPointerCount() == 2 ? scaleDetector.onTouchEvent(e) : detector.onTouchEvent(e);
    }

    private void applyBrightness() {
        float brightness = PlayerSetting.getBrightness();
        if (brightness < 0) return;
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        attributes.screenBrightness = brightness;
        activity.getWindow().setAttributes(attributes);
    }

    public void resetScale() {
        if (scale == 1.0f) return;
        videoView.animate().scaleX(1.0f).scaleY(1.0f).translationX(0f).translationY(0f).setDuration(250).withEndAction(() -> {
            videoView.setPivotY(videoView.getHeight() / 2.0f);
            videoView.setPivotX(videoView.getWidth() / 2.0f);
            scale = 1.0f;
        }).start();
    }

    public void setLock(boolean lock) {
        this.lock = lock;
    }

    public float getScale() {
        return scale;
    }

    private boolean isMultiple(MotionEvent e) {
        return e.getPointerCount() > 1;
    }

    private boolean isEdge(MotionEvent e) {
        return ResUtil.isEdge(App.get(), e, ResUtil.dp2px(24));
    }

    private boolean isSide(MotionEvent e) {
        int four = ResUtil.getScreenWidth(App.get()) / 4;
        float x = e.getRawX();
        return x <= four || x >= four * 3;
    }

    private void reset() {
        time = 0;
        touch = true;
        changeTime = false;
        changeSpeed = false;
        changeBright = false;
        changeVolume = false;
        changePage = false;
        pageDy = 0;
        pageVelocity = 0;
        pageLastTime = 0;
        bright = Util.getBrightness(activity);
        currentBright = bright;
        volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    @Override
    public boolean onDown(@NonNull MotionEvent e) {
        if (isMultiple(e) || isEdge(e) || changeScale || lock) return true;
        reset();
        return true;
    }

    @Override
    public void onLongPress(@NonNull MotionEvent e) {
        if (multiTouch || isEdge(e) || changeScale || lock) return;
        if (!listener.isLongPressSpeedEnabled()) return;
        listener.onSpeedUp();
        changeSpeed = true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
        if (isMultiple(e1) || isEdge(e1) || changeScale || lock || changeSpeed || pageSettling) return true;
        float deltaX = e2.getX() - e1.getX();
        float deltaY = e1.getY() - e2.getY();
        if (touch) checkFunc(Math.abs(deltaX), Math.abs(deltaY), e2);
        if (changeTime) listener.onSeeking(time = (long) (deltaX * 50));
        if (changeBright) setBright(deltaY);
        if (changeVolume) setVolume(deltaY);
        if (changePage) dragPage(e1, e2);
        return true;
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent e) {
        if (isMultiple(e) || isEdge(e) || changeScale) return true;
        listener.onDoubleTap();
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
        if (isMultiple(e) || changeScale) return true;
        listener.onSingleTap(e.getRawX(), ResUtil.getScreenWidth(App.get()));
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
        if (isMultiple(e1) || isEdge(e1) || isSide(e1) || changeScale || lock || animating || pageSettling || pageFlingHandled) return true;
        checkFunc(e1, e2);
        return true;
    }

    private void checkFunc(float distanceX, float distanceY, MotionEvent e2) {
        if ((float) Math.sqrt(distanceX * distanceX + distanceY * distanceY) < ResUtil.dp2px(20)) return;
        if (distanceX >= distanceY) changeTime = true;
        else if (isSide(e2)) checkSide(e2);
        else if (listener.isPageSwipeEnabled()) changePage = true;
        touch = false;
    }

    // 短剧滑动：手指跟随位移，无相邻集的方向加阻尼，松手按位移/速度决定切集或回弹
    private void dragPage(MotionEvent e1, MotionEvent e2) {
        float dy = e2.getY() - e1.getY();
        boolean can = dy < 0 ? listener.canPageUp() : listener.canPageDown();
        long now = e2.getEventTime();
        if (pageLastTime > 0 && now > pageLastTime) {
            float velocity = (dy - pageDy) / (now - pageLastTime) * 1000f;
            pageVelocity = pageVelocity * 0.5f + velocity * 0.5f;
        }
        pageLastTime = now;
        pageDy = dy;
        float applied = can ? dy : dy * 0.35f;
        videoView.setTranslationY(applied);
        listener.onPageDrag(applied);
    }

    private boolean shouldCommitPage() {
        boolean can = pageDy < 0 ? listener.canPageUp() : listener.canPageDown();
        if (!can) return false;
        int height = videoView.getHeight();
        if (height > 0 && Math.abs(pageDy) >= height * 0.25f) return true;
        return Math.abs(pageDy) > DISTANCE && Math.abs(pageVelocity) > 800 && (pageVelocity < 0) == (pageDy < 0);
    }

    private void settlePage(boolean commit) {
        pageSettling = true;
        boolean up = pageDy < 0;
        int height = videoView.getHeight();
        float target = commit ? (up ? -height : height) : 0f;
        ValueAnimator animator = ValueAnimator.ofFloat(videoView.getTranslationY(), target);
        animator.setDuration(commit ? 220 : 200);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            videoView.setTranslationY(value);
            listener.onPageDrag(value);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                pageSettling = false;
                if (commit) listener.onPageCommit(up);
                listener.onPageDragEnd(commit);
            }
        });
        animator.start();
    }

    private void checkFunc(MotionEvent e1, MotionEvent e2) {
        float dx = e2.getX() - e1.getX();
        float dy = e2.getY() - e1.getY();
        double angle = Math.toDegrees(Math.atan2(Math.abs(dy), Math.abs(dx)));
        if (angle > 70 && e1.getY() - e2.getY() > DISTANCE) {
            videoView.animate().translationYBy(ResUtil.dp2px(LiveSetting.isInvert() ? 24 : -24)).setDuration(150).withStartAction(() -> animating = true).withEndAction(() -> videoView.animate().translationY(0).setDuration(100).withStartAction(listener::onFlingUp).withEndAction(() -> animating = false).start()).start();
        } else if (angle > 70 && e2.getY() - e1.getY() > DISTANCE) {
            videoView.animate().translationYBy(ResUtil.dp2px(LiveSetting.isInvert() ? -24 : 24)).setDuration(150).withStartAction(() -> animating = true).withEndAction(() -> videoView.animate().translationY(0).setDuration(100).withStartAction(listener::onFlingDown).withEndAction(() -> animating = false).start()).start();
        }
    }

    private void checkSide(MotionEvent e2) {
        int half = ResUtil.getScreenWidth(App.get()) / 2;
        if (e2.getRawX() > half) changeVolume = true;
        else changeBright = true;
    }

    private void setBright(float deltaY) {
        int height = videoView.getMeasuredHeight();
        float brightness = deltaY * 2.0f / height + bright;
        if (brightness < 0) brightness = 0f;
        if (brightness > 1.0f) brightness = 1.0f;
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        attributes.screenBrightness = brightness;
        activity.getWindow().setAttributes(attributes);
        currentBright = brightness;
        listener.onBright((int) (brightness * 100));
    }

    private void setVolume(float deltaY) {
        int height = videoView.getMeasuredHeight();
        int maxVolume = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float deltaV = deltaY * 2.0f / height * maxVolume;
        float index = volume + deltaV;
        if (index > maxVolume) index = maxVolume;
        if (index < 0) index = 0;
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) index, 0);
        listener.onVolume((int) (index / maxVolume * 100.0f));
    }

    @Override
    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
        if (changeBright || changeVolume || changeSpeed || changeTime || lock) return changeScale = false;
        return changeScale = true;
    }

    @Override
    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
        App.post(() -> changeScale = false, 500);
    }

    @Override
    public boolean onScale(@NonNull ScaleGestureDetector detector) {
        scale *= detector.getScaleFactor();
        scale = Math.max(1.0f, Math.min(scale, 5.0f));
        videoView.setPivotX(detector.getFocusX());
        videoView.setPivotY(detector.getFocusY());
        videoView.setScaleX(scale);
        videoView.setScaleY(scale);
        return true;
    }

    public interface Listener {

        void onSeeking(long time);

        void onSeekEnd(long time);

        void onSpeedUp();

        void onSpeedEnd();

        /** 长按加速是否启用（宿主按自己的设置口径决定，默认启用）。 */
        default boolean isLongPressSpeedEnabled() {
            return true;
        }

        void onBright(int progress);

        void onVolume(int progress);

        void onFlingUp();

        void onFlingDown();

        /** 是否启用竖滑翻页（短剧全屏）：默认关，其余页面沿用原 fling 弹跳切集。 */
        default boolean isPageSwipeEnabled() {
            return false;
        }

        /** 该方向是否有相邻集可切（决定跟随位移是否加阻尼、松手是否允许切集）。 */
        default boolean canPageUp() {
            return true;
        }

        default boolean canPageDown() {
            return true;
        }

        /** 翻页位移回调（拖动与松手动画逐帧）：宿主据此摆放相邻集预览层。 */
        default void onPageDrag(float dy) {
        }

        /** 松手吸附成立、动画到位：走原切集链。 */
        default void onPageCommit(boolean up) {
            if (up) onFlingUp();
            else onFlingDown();
        }

        /** 翻页手势收尾：未切集时宿主撤除预览层。 */
        default void onPageDragEnd(boolean committed) {
        }

        void onSingleTap();

        default void onSingleTap(float x, float width) {
            onSingleTap();
        }

        void onDoubleTap();

        void onTouchEnd();
    }
}
