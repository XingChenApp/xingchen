package com.fongmi.android.tv.hongguo.ui;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import androidx.media3.ui.PlayerView;

/**
 * 播放面当前帧抓取（切集末帧冻结用）：把 PlayerView 正在显示的
 * 这一帧复制成 Bitmap，交给切集流程在新集首帧到达前留屏。
 *
 * <p>口径：TextureView 走 getBitmap（同步）；SurfaceView 走
 * PixelCopy（API 26+，异步、回调在传入 Handler 上）；其余情形
 * （低版本/面未就绪/复制失败）一律回 null，调用方按无冻结走
 * 常路，不许拿黑位图冒充帧——PixelCopy 成功但内容不可知，
 * 回调只在 SUCCESS 时给图。</p>
 *
 * <p>结果日志（2026-10-31 件二补）：每次抓帧恰一行 {@link #TAG}
 * 日志——ok（带路径与尺寸）／fail（带原因：no-surface／texture／
 * pixelcopy result 码／alloc／unsupported）；等帧超时由竖屏调用方
 * 以同一 TAG 记 timeout 行。真机 Logcat 过滤 HgFreeze 即可分段
 * 冻结到底是抓成了、抓败了还是等超时了（此前全程无日志、成败
 * 不可见，账 fix168-livepath-audit 取证清单第 2 项）。</p>
 */
public final class HgFrameCapture {

    /** 抓帧结果日志 TAG（竖屏等帧超时行也用它，保持单一过滤口）。 */
    public static final String TAG = "HgFreeze";

    public interface Callback {
        /** bmp 为 null 表示没抓到（调用方不冻结、走常路）。 */
        void onFrame(Bitmap bmp);
    }

    private HgFrameCapture() {
    }

    public static void capture(PlayerView playerView, Handler handler, Callback cb) {
        if (playerView == null || cb == null) return;
        View sv = playerView.getVideoSurfaceView();
        if (sv == null || sv.getWidth() <= 0 || sv.getHeight() <= 0) {
            Log.i(TAG, "capture fail: no-surface");
            cb.onFrame(null);
            return;
        }
        if (sv instanceof TextureView) {
            Bitmap bmp = null;
            try {
                bmp = ((TextureView) sv).getBitmap();
            } catch (Throwable ignored) {
            }
            if (bmp != null) {
                Log.i(TAG, "capture ok: texture " + bmp.getWidth() + "x" + bmp.getHeight());
            } else {
                Log.i(TAG, "capture fail: texture null");
            }
            cb.onFrame(bmp);
            return;
        }
        if (sv instanceof SurfaceView && Build.VERSION.SDK_INT >= 26 && handler != null) {
            final Bitmap bmp;
            try {
                bmp = Bitmap.createBitmap(sv.getWidth(), sv.getHeight(),
                        Bitmap.Config.ARGB_8888);
            } catch (Throwable t) {
                Log.i(TAG, "capture fail: alloc");
                cb.onFrame(null);
                return;
            }
            try {
                PixelCopy.request((SurfaceView) sv, bmp, result -> {
                    if (result == PixelCopy.SUCCESS) {
                        Log.i(TAG, "capture ok: pixelcopy " + bmp.getWidth() + "x" + bmp.getHeight());
                        cb.onFrame(bmp);
                    } else {
                        Log.i(TAG, "capture fail: pixelcopy result=" + result);
                        bmp.recycle();
                        cb.onFrame(null);
                    }
                }, handler);
            } catch (Throwable t) {
                Log.i(TAG, "capture fail: pixelcopy throw");
                bmp.recycle();
                cb.onFrame(null);
            }
            return;
        }
        Log.i(TAG, "capture fail: unsupported");
        cb.onFrame(null);
    }
}
