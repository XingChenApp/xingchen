package com.example.hikerview.utils;

import android.os.Handler;
import android.os.Looper;

import com.annimon.stream.function.Consumer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 8.83 {@code com.example.hikerview.utils.ThreadTool}（Kotlin object）的同包名补齐
 * （官包 jadx 反编译对位）。本树未引入 kotlinx-coroutines，协程落点按本树既有
 * 口径（HkLazyRuleParser 同例）以执行器等价实现：CoroutineScope → 本类
 * {@link ScopeHandle}（一片可取消的调度执行器），Job 返回值 JS 侧不用、作废；
 * async(Function2 协程块) 一法无 Java 对应形态，不补（JS 无法构造该入参）。
 * 语义面对齐官版：runOnUI 主线程直跑、否则投主线程；postDelayed 走后台、
 * postUIDelayed 走主线程；loadScheduleTask 按 holder 复用同一片、周期跑；
 * getStrOnUIThread 非主线程时投主线程执行并等 5 秒。
 */
public final class ThreadTool {

    public static final ThreadTool INSTANCE = new ThreadTool();

    /** 协程作用域的执行器等价物：一片可取消的调度执行器。 */
    public static final class ScopeHandle {
        private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hk-threadtool");
            t.setDaemon(true);
            return t;
        });
        private volatile boolean cancelled;

        public boolean isActive() {
            return !cancelled && !exec.isShutdown();
        }

        public void cancel() {
            cancelled = true;
            exec.shutdownNow();
        }
    }

    private static final ScheduledExecutorService IO = Executors.newScheduledThreadPool(4, r -> {
        Thread t = new Thread(r, "hk-threadtool-io");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Map<Object, ScopeHandle> scopeMap = new HashMap<>();

    private ThreadTool() {
    }

    public Map<Object, ScopeHandle> getScopeMap() {
        return scopeMap;
    }

    public void executeNewTask(Runnable command) {
        IO.execute(command);
    }

    public void async(Runnable task) {
        executeNewTask(task);
    }

    public void async(final Runnable task, final Consumer<Exception> uiTask) {
        IO.execute(() -> {
            try {
                task.run();
            } catch (final Exception e) {
                if (uiTask != null) {
                    runOnUI(() -> uiTask.accept(e));
                }
            }
        });
    }

    public ScopeHandle newScope() {
        return new ScopeHandle();
    }

    public ScopeHandle newScope(Object holder) {
        ScopeHandle scope = new ScopeHandle();
        synchronized (scopeMap) {
            scopeMap.put(holder, scope);
        }
        return scope;
    }

    public void cancelScope(ScopeHandle scope) {
        try {
            if (scope != null) scope.cancel();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public void launch(ScopeHandle scope, Runnable task) {
        try {
            if (scope != null && scope.isActive()) scope.exec.execute(task);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public void postDelayed(long delayMillSec, Runnable task) {
        try {
            IO.schedule(task, delayMillSec, TimeUnit.MILLISECONDS);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public void postUIDelayed(long delayMillSec, Runnable task) {
        try {
            MAIN.postDelayed(task, delayMillSec);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public void runOnUI(Runnable task) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(task);
        } else {
            task.run();
        }
    }

    public void runOnUI(ScopeHandle scope, Runnable task) {
        try {
            runOnUI(task);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public ScopeHandle loadScheduleTask(Object holder, long scheduleGap, Runnable runnable) {
        synchronized (scopeMap) {
            ScopeHandle existing = scopeMap.get(holder);
            if (existing != null && existing.isActive()) {
                scheduleLoop(existing, runnable, scheduleGap);
                return existing;
            }
        }
        ScopeHandle scope = newScope(holder);
        scheduleLoop(scope, runnable, scheduleGap);
        return scope;
    }

    private void scheduleLoop(ScopeHandle scope, Runnable runnable, long gap) {
        scope.exec.scheduleWithFixedDelay(() -> {
            try {
                runnable.run();
            } catch (Throwable ignored) {
            }
        }, 0, Math.max(1, gap), TimeUnit.MILLISECONDS);
    }

    public void cancelTasks(Object holder) {
        try {
            synchronized (scopeMap) {
                ScopeHandle scope = scopeMap.get(holder);
                if (scope != null) scope.cancel();
                scopeMap.remove(holder);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getStrOnUIThread(final Consumer<UrlHolder> runnable) {
        final UrlHolder urlHolder = new UrlHolder();
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            runnable.accept(urlHolder);
            return urlHolder.getUrl();
        }
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        runOnUI(() -> {
            runnable.accept(urlHolder);
            countDownLatch.countDown();
        });
        try {
            countDownLatch.await(5L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return urlHolder.getUrl();
    }

    /** 8.83 ThreadTool$UrlHolder 同形。 */
    public static final class UrlHolder {
        private String url;

        public String getUrl() {
            return this.url;
        }

        public void setUrl(String str) {
            this.url = str;
        }
    }
}
