package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Player;

/**
 * 播放器顶部细进度条。
 * 贴屏幕最上沿、高 3dp：半透明白底轨＋金色已播段，无圆点无时间字。
 * 只负责画线与跟随进度；显隐由调用方按 {@link TopProgressRule#shouldShow} 判定。
 * 进度监听口径与 CustomSeekView 同源（逐帧 tick，无拖动逻辑）。
 */
public class TopProgressView extends View implements Player.Listener {

    private static final long TICK_MS = 500;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable tick = this::onTick;
    private Player player;
    private float ratio;

    public TopProgressView(Context context) {
        this(context, null);
    }

    public TopProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        trackPaint.setColor(0x38FFFFFF);
        fillPaint.setColor(0xFFF2B33D);
        setWillNotDraw(false);
    }

    /** 与 CustomSeekView.setPlayer 同口径：此处仅做进度读取。 */
    public void setPlayer(@Nullable Player player) {
        setProgressPlayer(player);
    }

    /** 与 CustomSeekView.setProgressPlayer 同口径。 */
    public void setProgressPlayer(@Nullable Player player) {
        if (this.player != null) this.player.removeListener(this);
        this.player = player;
        if (player != null) player.addListener(this);
        update();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postDelayed(tick, TICK_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        if (player != null) player.removeListener(this);
        super.onDetachedFromWindow();
    }

    private void onTick() {
        update();
        postDelayed(tick, TICK_MS);
    }

    private void update() {
        Player p = player;
        long duration = p == null ? 0 : p.getDuration();
        long position = p == null ? 0 : Math.max(0, p.getCurrentPosition());
        ratio = (duration != C.TIME_UNSET && duration > 0) ? Math.min(1f, (float) position / duration) : 0f;
        if (getVisibility() == VISIBLE) invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        canvas.drawRect(0, 0, w, h, trackPaint);
        canvas.drawRect(0, 0, w * ratio, h, fillPaint);
    }
}
