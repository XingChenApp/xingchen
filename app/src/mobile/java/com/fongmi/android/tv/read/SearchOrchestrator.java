package com.fongmi.android.tv.read;

import com.fongmi.android.tv.legado.LegadoPipeline;
import com.fongmi.android.tv.legado.LegadoSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 搜书并行编排器（纯 Java，不依赖 Android 视图层，可上 JVM 试验台实测）：
 * 各源并行搜（线程池，上限 {@link #MAX_CONCURRENCY}），各源独立 try/catch；
 * 全部回来或 {@link #TOTAL_TIMEOUT_MS} 到点就收尾（settle），没回来的源按「超时」点名；
 * GBK 兜底重试发生在单个源任务内部，自然计入总时限。
 * 回调在工作线程上触发，调用方自行切 UI 线程；stop() 后不再产生任何回调。
 */
public class SearchOrchestrator {

    /** 并发上限：源再多也不许把手机打满。 */
    public static final int MAX_CONCURRENCY = 8;
    /** 总时限：全部回来或到点就收尾给反馈。 */
    public static final long TOTAL_TIMEOUT_MS = 25_000L;

    /** 回调（工作线程上触发，UI 调用方自行 runOnUiThread）。 */
    public interface Listener {
        void onSourceOk(int index, LegadoSource source, List<LegadoPipeline.Book> books);

        void onSourceFail(int index, LegadoSource source, boolean timeout);

        /** 收尾：失败名单已定（超时源已按「超时」计入），可读 failNames()。 */
        void onSettled();
    }

    private final List<LegadoSearchFlow.Src> srcs;
    private final String key;
    private final Listener listener;
    private final long timeoutMs;

    private ExecutorService pool;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    /** 0=没回 1=回了 2=失败 3=超时；只在 orchestrator 锁里读写。 */
    private final int[] state;
    private final List<String> failNames = new ArrayList<>();
    private int returned;

    public SearchOrchestrator(List<LegadoSearchFlow.Src> srcs, String key, Listener listener) {
        this(srcs, key, listener, TOTAL_TIMEOUT_MS);
    }

    /** timeoutMs 仅试验台用（生产走默认 25 秒）。 */
    public SearchOrchestrator(List<LegadoSearchFlow.Src> srcs, String key,
                              Listener listener, long timeoutMs) {
        this.srcs = srcs;
        this.key = key;
        this.listener = listener;
        this.timeoutMs = timeoutMs;
        this.state = new int[srcs.size()];
    }

    public synchronized void start() {
        int n = srcs.size();
        if (n == 0) {
            listener.onSettled();
            return;
        }
        pool = Executors.newFixedThreadPool(Math.min(n, MAX_CONCURRENCY), r -> {
            Thread t = new Thread(r, "LegadoSearch");
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.execute(() -> {
                List<LegadoPipeline.Book> books = null;
                boolean ok = false;
                try {
                    if (!stopped.get()) {
                        books = new LegadoPipeline(srcs.get(idx).source).search(key);
                        ok = true;
                    }
                } catch (Throwable ignored) {
                    // 单个源坏了只记失败，不许拖垮整轮
                }
                List<Event> evs;
                synchronized (SearchOrchestrator.this) {
                    evs = onOneLocked(idx, books, ok);
                }
                if (evs != null) {
                    for (Event e : evs) dispatch(e);
                }
            });
        }
        Thread settleThread = new Thread(() -> {
            long deadline = System.currentTimeMillis() + timeoutMs;
            List<Event> evs = null;
            synchronized (SearchOrchestrator.this) {
                while (!stopped.get() && returned < srcs.size()) {
                    long wait = deadline - System.currentTimeMillis();
                    if (wait <= 0) break;
                    try {
                        SearchOrchestrator.this.wait(wait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (!stopped.get() && returned < srcs.size()) {
                    evs = settleLocked(true);
                }
            }
            if (evs != null) {
                for (Event e : evs) dispatch(e);
            }
        }, "LegadoSearchSettle");
        settleThread.setDaemon(true);
        settleThread.start();
    }

    /** 停掉：不再产生回调（页关了用）。 */
    public synchronized void stop() {
        stopped.set(true);
        if (pool != null) pool.shutdownNow();
        SearchOrchestrator.this.notifyAll();
    }

    /** 收尾时的失败名单（含「（超时）」后缀），onSettled 之后读。 */
    public synchronized List<String> failNames() {
        return new ArrayList<>(failNames);
    }

    /** 锁内事件（只做状态变更，不调 listener）。 */
    private static final class Event {
        int kind; // 1=ok 2=fail 3=settled
        int index;
        LegadoSource source;
        List<LegadoPipeline.Book> books;
        boolean timeout;
    }

    private Event oneEvent(int kind, int idx, List<LegadoPipeline.Book> books, boolean timeout) {
        Event ev = new Event();
        ev.kind = kind;
        ev.index = idx;
        ev.source = srcs.get(idx).source;
        ev.books = books;
        ev.timeout = timeout;
        return ev;
    }

    /**
     * 必须在 orchestrator 锁内调用。
     * 返回待派发的事件列：单个源事件；若它是最后一个回来的，附带 settle 事件。
     */
    private List<Event> onOneLocked(int idx, List<LegadoPipeline.Book> books, boolean ok) {
        if (stopped.get() || state[idx] != 0) return null;
        state[idx] = ok ? 1 : 2;
        returned++;
        List<Event> evs = new ArrayList<>(2);
        if (ok) {
            evs.add(oneEvent(1, idx, books, false));
        } else {
            failNames.add(failName(idx, false));
            evs.add(oneEvent(2, idx, null, false));
        }
        if (returned >= srcs.size()) {
            evs.addAll(settleLocked(false));
        } else {
            SearchOrchestrator.this.notifyAll();
        }
        return evs;
    }

    /**
     * 必须在 orchestrator 锁内调用：超时点名＋停池，返回 settle 事件。
     * timedOut=false 时只是正常收尾（停池＋settle 事件），不改失败名单。
     */
    private List<Event> settleLocked(boolean timedOut) {
        List<Event> evs = new ArrayList<>(2);
        if (stopped.get()) return evs;
        if (timedOut) {
            for (int i = 0; i < state.length; i++) {
                if (state[i] == 0) {
                    state[i] = 3;
                    returned++;
                    failNames.add(failName(i, true));
                }
            }
        }
        if (pool != null) pool.shutdownNow();
        Event ev = new Event();
        ev.kind = 3;
        evs.add(ev);
        SearchOrchestrator.this.notifyAll();
        return evs;
    }

    private String failName(int i, boolean timeout) {
        return LegadoSearchFlow.sourceName(srcs.get(i).source) + (timeout ? "（超时）" : "");
    }

    /** 锁外派发回调。 */
    private void dispatch(Event ev) {
        if (stopped.get()) return;
        switch (ev.kind) {
            case 1:
                listener.onSourceOk(ev.index, ev.source, ev.books);
                break;
            case 2:
                listener.onSourceFail(ev.index, ev.source, ev.timeout);
                break;
            case 3:
                listener.onSettled();
                break;
            default:
                break;
        }
    }
}
