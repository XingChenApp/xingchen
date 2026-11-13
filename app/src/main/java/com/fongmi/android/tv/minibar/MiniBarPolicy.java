package com.fongmi.android.tv.minibar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 底部迷你条槽位策略（纯 Java，零安卓依赖）：
 * 同一时刻只露一条（谁后开始播谁占位）；听书「消失＝关闭」三条关闭路径；
 * 暂停超 10 秒自动关闭；槽位让位≠关闭（被顶替的会话不停播）。
 *
 * 副作用全部经由 Actions 回调出去，本类只做状态机；安卓壳（ListenMiniBar）
 * 与离线回放共用同一份逻辑。
 */
public class MiniBarPolicy {

    /** 暂停后自动关闭的毫秒数（用户定的 10 秒）。 */
    public static final long AUTO_CLOSE_MS = 10_000L;

    /** 会话标识（听书：audiobook / tts）。 */
    public interface Session {
        String id();

        /**
         * 本会话是否吃「暂停超 10 秒自动关闭」。默认吃（听书口径：消失＝关闭）；
         * 音乐会话覆写为 false——音乐迷你条是首页常驻条，有当前曲/队列时必须在，
         * 暂停自动关会在用户已回首页后才到期开火、把刚补注册回来的条连根摘除，
         * 且摘除后停住的播放器不再发任何事件、没有同步能把它带回来（1.4.45 真机实证）。
         */
        default boolean autoCloseOnPause() {
            return true;
        }
    }

    /** 策略触发的副作用，由宿主实现。 */
    public interface Actions {
        /** 真关闭某会话：停播＋进度落盘＋注销（三种关闭路径都走这里）。 */
        void onCloseSession(String id);

        /** 可见条变化：visibleId 为 null 表示隐藏。 */
        void onVisibleChanged(String visibleId);
    }

    public interface Clock {
        long now();
    }

    /** 单次定时器：schedule 会先取消上一次（同会话只会有一个计时在跑）。 */
    public interface Timer {
        void schedule(long delayMs, Runnable task);

        void cancel();
    }

    private static class State {
        Session session;
        boolean pageForeground;
        boolean playing;
        /** 暂停起始时刻（-1＝没在暂停计时）；恢复播放/回页时清零。 */
        long pauseStartMs = -1;
    }

    private final Map<String, State> sessions = new LinkedHashMap<>();
    private String visibleId;
    private final Actions actions;
    private final Clock clock;
    private final Timer timer;

    public MiniBarPolicy(Actions actions, Clock clock, Timer timer) {
        this.actions = actions;
        this.clock = clock;
        this.timer = timer;
    }

    /** 当前露出的会话 id（null＝无）。 */
    public synchronized String visibleId() {
        return visibleId;
    }

    /** 注册会话（开始播）：后来者占位，先注册的让位但不关闭。 */
    public synchronized void register(Session s) {
        State st = sessions.get(s.id());
        if (st == null) {
            st = new State();
            sessions.put(s.id(), st);
        }
        st.session = s;
        st.playing = true;
        st.pauseStartMs = -1;
        // 扫描 H9：不再无条件 cancel 共享定时器——那会把另一会话（如听书）已武装的
        // 「暂停 10 秒自动关闭」计时打掉且无人再补；改走统一补武装（见 rearmEarliestPausedLocked）
        rearmEarliestPausedLocked();
        updateVisible();
    }

    /** 注销会话（真关闭后调用；不触发 onCloseSession，避免递归）。 */
    public synchronized void unregister(String id) {
        if (sessions.remove(id) == null) return;
        rearmEarliestPausedLocked();
        updateVisible();
    }

    /** 播放页 onPause：页不在前台；若已暂停，按暂停起始时刻补计时。 */
    public synchronized void pagePaused(String id) {
        State st = sessions.get(id);
        if (st == null) return;
        st.pageForeground = false;
        armPauseTimerLocked(st);
        updateVisible();
    }

    /** 播放页 onResume：回页取消自动关闭计时，条隐藏（页自己就是播放器）。 */
    public synchronized void pageResumed(String id) {
        State st = sessions.get(id);
        if (st == null) return;
        st.pageForeground = true;
        st.pauseStartMs = -1;
        rearmEarliestPausedLocked(); // 本会话计时取消，但别连坐其它会话（H9）
        updateVisible();
    }

    /** 播放/暂停变化：恢复播放取消计时；在别处暂停则起计时。 */
    public synchronized void playingChanged(String id, boolean playing) {
        State st = sessions.get(id);
        if (st == null) return;
        st.playing = playing;
        if (playing) {
            st.pauseStartMs = -1;
            rearmEarliestPausedLocked(); // 同 pageResumed：只撤自己的计时（H9）
        } else {
            if (st.pauseStartMs < 0) st.pauseStartMs = clock.now();
            armPauseTimerLocked(st);
        }
    }


    /**
     * 外部请求关闭某会话（迷你条封面点击、播放页自己的关闭钮）：
     * 真关闭，走 onCloseSession。
     */
    public synchronized void closeSession(String id) {
        if (!sessions.containsKey(id)) return;
        sessions.remove(id);
        rearmEarliestPausedLocked();
        updateVisible();
        actions.onCloseSession(id);
    }

    /** 定时器到期：仍暂停、仍不在前台、会话还在 → 真关闭。 */
    private synchronized void onTimerFire(String id) {
        State st = sessions.get(id);
        if (st == null) return;
        if (!st.session.autoCloseOnPause()) return;
        if (st.playing || st.pageForeground || st.pauseStartMs < 0) return;
        if (clock.now() - st.pauseStartMs < AUTO_CLOSE_MS) {
            // 被中途打断又重计时过，补剩余时间（防御）
            armPauseTimerLocked(st);
            return;
        }
        sessions.remove(id);
        updateVisible();
        // 关掉一个后，其余仍在暂停计时的会话要接上单定时器（H9）
        rearmEarliestPausedLocked();
        actions.onCloseSession(id);
    }

    /** 按「暂停起始时刻」起 10 秒计时；只在「别处暂停」时跑。 */
    private void armPauseTimerLocked(State st) {
        if (!st.session.autoCloseOnPause()) return;
        if (st.playing || st.pageForeground || st.pauseStartMs < 0) return;
        rearmEarliestPausedLocked();
    }

    /**
     * 共享单定时器的统一武装口（扫描 H9）：先取消现挂任务，再在全部会话里挑
     * 「别处暂停中（已暂停、不在前台、吃自动关闭）」且最早到期的一家挂上。
     * 单 Timer 同一时刻只能挂一个任务——挂最早到期者不会漏关：到期回调
     * onTimerFire 按 id 复核自家状态、关掉后本方法会被再调一次接上下一家；
     * 任何 cancel 点（注册/回页/恢复播放/注销/关闭）都改走这里，别家的计时
     * 不再被连坐打掉。已过期的直接走 onTimerFire 收口。
     */
    private void rearmEarliestPausedLocked() {
        timer.cancel();
        State best = null;
        long bestRemain = Long.MAX_VALUE;
        for (State st : sessions.values()) {
            if (st.session == null || !st.session.autoCloseOnPause()) continue;
            if (st.playing || st.pageForeground || st.pauseStartMs < 0) continue;
            long remain = AUTO_CLOSE_MS - (clock.now() - st.pauseStartMs);
            if (remain < bestRemain) {
                bestRemain = remain;
                best = st;
            }
        }
        if (best == null) return;
        if (bestRemain <= 0) {
            onTimerFire(best.session.id());
            return;
        }
        final String id = best.session.id();
        timer.schedule(bestRemain, () -> onTimerFire(id));
    }

    /**
     * 槽位仲裁：可见＝最后注册且仍存活的会话。
     * 让位只是不显示，不触发关闭（与 closeSession/musixPageEnter 的真关闭区分）。
     */
    private void updateVisible() {
        List<String> ids = new ArrayList<>(sessions.keySet());
        String top = ids.isEmpty() ? null : ids.get(ids.size() - 1);
        if (top == null ? visibleId == null : top.equals(visibleId)) return;
        // 只有「顶层会话的页在前台」时才隐藏；其它页在前台都显示
        State topState = top == null ? null : sessions.get(top);
        String show = (topState != null && topState.pageForeground) ? null : top;
        String cur = visibleId;
        boolean showingNow = cur != null;
        boolean showNow = show != null;
        if (showingNow == showNow && (cur == null || cur.equals(show))) {
            visibleId = show;
            return;
        }
        visibleId = show;
        actions.onVisibleChanged(show);
    }

    /** 当前是否有存活会话（供壳判断要不要绑服务）。 */
    public synchronized boolean hasSessions() {
        return !sessions.isEmpty();
    }
}
