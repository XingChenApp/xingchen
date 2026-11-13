package com.fongmi.android.tv.ui.minibar;

import android.content.Context;
import android.content.Intent;

import com.fongmi.android.tv.minibar.MiniBarPolicy;
import com.fongmi.android.tv.service.PlaybackService;

import java.lang.ref.WeakReference;

/**
 * 听书迷你条会话（AudioBook 音频书 / TtsRead 神经朗读共用）。
 * 页 alive 时走 Control（直连页）；页销毁后走 DetachedOps（壳代管，只做播放/暂停/真关闭，
 * 绝不留孤儿声）。只持有 POJO 与 WeakReference，不漏 Activity。
 */
public class ListenSession implements MiniBarPolicy.Session {

    /** 页 alive 时的控制（实现里只调页方法）。 */
    public interface Control {
        boolean isPlaying();

        void toggle();

        void next();

        boolean canNext();

        void prev();

        boolean canPrev();

        void saveProgress();

        void stopPlayback();

        /** 真关闭时的页侧清理：注销锁屏回调、清任务、停引擎等。 */
        void onClosed();
    }

    /** 页销毁后的兜底（只允许停播＋落盘＋重开页，不碰页实例）。 */
    public interface DetachedOps {
        boolean isPlaying(PlaybackService svc);

        void toggle(PlaybackService svc);

        /** 条上暂停后落盘当前位置（默认空：没实现的会话退回旧行为）。 */
        default void saveProgress(Context ctx, PlaybackService svc) {
        }

        /** 真关闭：停播＋进度落盘（实现只用快照数据＋svc 读位置）。 */
        void close(Context ctx, PlaybackService svc);

        /** 点条身回播放页（实现用快照重建，不依赖旧页）。 */
        void reopen(Context ctx);
    }

    private final String id;
    private String title = "";
    private String sub = "";
    private String coverUrl = "";
    /** 播放键前缀（audiobook: / tts:），用于 detached 判活。 */
    private String keyPrefix = "";
    private Intent reopenIntent;
    private WeakReference<Control> controlRef;
    private DetachedOps detached;

    public ListenSession(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSub() {
        return sub;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setMeta(String title, String sub, String coverUrl) {
        this.title = title == null ? "" : title;
        this.sub = sub == null ? "" : sub;
        this.coverUrl = coverUrl == null ? "" : coverUrl;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
    }

    public void setControl(Control control) {
        this.controlRef = control == null ? null : new WeakReference<>(control);
    }

    public void setDetached(DetachedOps detached) {
        this.detached = detached;
    }

    public void setReopenIntent(Intent intent) {
        this.reopenIntent = intent;
    }

    public Intent getReopenIntent() {
        return reopenIntent;
    }

    /** 包内可见：壳层取页控制（页销毁后返回 null，走兜底）。 */
    Control control() {
        return controlRef == null ? null : controlRef.get();
    }

    public boolean isControlAlive() {
        return control() != null;
    }

    public boolean canNext() {
        Control c = control();
        return c != null && c.canNext();
    }

    public boolean canPrev() {
        Control c = control();
        return c != null && c.canPrev();
    }

    /** 真关闭：页在走页，不在走兜底；之后一律从策略注销。 */
    void performClose(Context ctx, PlaybackService svc) {
        Control c = control();
        try {
            if (c != null) {
                c.saveProgress();
                c.stopPlayback();
                c.onClosed();
            } else if (detached != null) {
                detached.close(ctx, svc);
            } else if (svc != null) {
                // 兜底的兜底：声音不能没主
                try {
                    svc.player().stop();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    boolean detachedPlaying(PlaybackService svc) {
        try {
            return detached != null && svc != null && detached.isPlaying(svc);
        } catch (Throwable t) {
            return false;
        }
    }

    void detachedToggle(PlaybackService svc) {
        try {
            if (detached != null && svc != null) detached.toggle(svc);
        } catch (Throwable ignored) {
        }
    }

    void detachedSave(Context ctx, PlaybackService svc) {
        try {
            if (detached != null && svc != null) detached.saveProgress(ctx, svc);
        } catch (Throwable ignored) {
        }
    }

    void reopen(Context ctx) {
        Control c = control();
        try {
            if (c != null) {
                // 页还在：直接把它搬到前台
                if (reopenIntent != null) {
                    Intent it = new Intent(reopenIntent);
                    it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    ctx.startActivity(it);
                    return;
                }
            }
            if (detached != null) detached.reopen(ctx);
            else if (reopenIntent != null) ctx.startActivity(reopenIntent);
        } catch (Throwable ignored) {
        }
    }
}
