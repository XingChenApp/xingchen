package com.github.catvod.crawler;

import android.os.SystemClock;

import com.github.catvod.Init;
import com.github.catvod.crawler.diagnostics.DiagnosticEvent;
import com.github.catvod.crawler.diagnostics.DiagnosticLogBuffer;
import com.github.catvod.crawler.diagnostics.RollingDiagnosticFile;
import com.github.catvod.crawler.diagnostics.JournaledDiagnosticFile;
import com.github.catvod.crawler.diagnostics.DiagnosticCategories;
import com.github.catvod.utils.Prefers;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DebugLogStore {
    private static final String PREF_ENABLED = "debug_log";
    private static volatile boolean enabled;
    private static volatile DiagnosticLogBuffer buffer;
    private static final String PREF_CATEGORIES = "debug_log_categories";
    private static volatile int categories = DiagnosticCategories.ALL;
    private static boolean crashHandlerInstalled;

    public static boolean isEnabled() { return enabled; }
    public static int categories() { return categories; }
    public static boolean categoryEnabled(DiagnosticCategories.Category category) {
        return enabled && DiagnosticCategories.accepts(categories, category);
    }
    public static boolean acceptsEvent(String name) {
        return enabled && (categories == DiagnosticCategories.ALL || name.startsWith("mpv.")
                || DiagnosticCategories.accepts(categories, DiagnosticCategories.event(name)));
    }
    public static boolean acceptsTag(String tag) {
        return enabled && (categories == DiagnosticCategories.ALL || DiagnosticCategories.accepts(categories, DiagnosticCategories.text(tag)));
    }
    public static synchronized void setCategory(DiagnosticCategories.Category category, boolean value) {
        categories = value ? categories | category.bit : categories & ~category.bit;
        Prefers.put(PREF_CATEGORIES, categories);
        logCategories("category-changed");
    }
    private static void logCategories(String reason) {
        event(new DiagnosticEvent("diag.session.begin", "none", "process", 0, 0)
                .observed("reason", reason).observed("config", DiagnosticCategories.summary(categories))
                .observed("mode", "full-selected-categories").pin("diagnostic-categories"));
    }

    private static synchronized DiagnosticLogBuffer create() {
        if (buffer == null) {
            DiagnosticLogBuffer.Limits limits = DiagnosticLogBuffer.Limits.standard();
            buffer = new DiagnosticLogBuffer(limits, new com.github.catvod.crawler.diagnostics.IncidentDiagnosticFile(new JournaledDiagnosticFile(
                    new RollingDiagnosticFile(Init.context().getCacheDir(), limits), Init.context().getFilesDir()), Init.context().getCacheDir()),
                    new DiagnosticLogBuffer.Clock() {
                        @Override public long wallMillis() { return System.currentTimeMillis(); }
                        @Override public long monotonicNanos() { return SystemClock.elapsedRealtimeNanos(); }
                        @Override public int processId() { return android.os.Process.myPid(); }
                    });
            installCrashHandler();
        }
        return buffer;
    }

    private static void installCrashHandler() {
        if (crashHandlerInstalled) return;
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        if (previous == null) return;
        try {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                if (enabled) {
                    event(new DiagnosticEvent("process.recovery", "none", "process", 0, 0).severity("error")
                            .observed("reason", "java-uncaught").observed("javaClass", error.getClass().getName())
                            .message(error.getMessage()).pin("process-crash"));
                    add("process-crash", com.github.catvod.crawler.diagnostics.DiagnosticText.throwable(error), true);
                    DiagnosticLogBuffer current = buffer;
                    if (current != null) current.flushBestEffort(150);
                }
            } catch (Throwable ignored) {
                // The original crash handler always retains ownership of termination/reporting.
            } finally { previous.uncaughtException(thread, error); }
        });
        crashHandlerInstalled = true;
        } catch (RuntimeException ignored) { collectorFailure(); }
    }

    public static synchronized void setEnabled(boolean value) {
        categories = Prefers.getInt(PREF_CATEGORIES, DiagnosticCategories.ALL) & DiagnosticCategories.ALL;
        if (!value) com.github.catvod.crawler.diagnostics.DiagnosticCapture.stop("diagnostics-disabled");
        if (value) create().start(false);
        enabled = value;
        Prefers.put(PREF_ENABLED, value);
        if (value) beginCollection("enable");
        else if (buffer != null) buffer.disable();
    }

    public static synchronized void restoreEnabled() {
        categories = Prefers.getInt(PREF_CATEGORIES, DiagnosticCategories.ALL) & DiagnosticCategories.ALL;
        // 星辰：调试日志默认开（webhtv 原版默认关，用户找不到日志文件）；用户显式关过则尊重存储值
        enabled = Prefers.getBoolean(PREF_ENABLED, true);
        if (!enabled) return;
        create().start(true);
        beginCollection("restore");
    }

    private static void beginCollection(String reason) {
        logCategories(reason);
        event(new DiagnosticEvent("diag.session.begin", "none", "process", 0, 0)
                .observed("mode", "standard").observed("reason", reason)
                .unknown("captureStartedLate", DiagnosticEvent.Status.UNKNOWN)
                .coverage("session", DiagnosticEvent.Status.KNOWN)
                .coverage("health", DiagnosticEvent.Status.KNOWN)
                .coverage("decoder", DiagnosticEvent.Status.NOT_COLLECTED)
                .coverage("surface", DiagnosticEvent.Status.NOT_COLLECTED)
                .coverage("audioOutput", DiagnosticEvent.Status.NOT_COLLECTED)
                .pin("process-session"));
    }

    public static void add(String tag, String message) { add(tag, message, false); }

    static void add(String tag, String message, boolean critical) {
        DiagnosticLogBuffer current = buffer;
        if (!acceptsTag(tag) || current == null) return;
        current.add(tag, message, critical);
    }

    /**
     * 强制记录通道：只供「jar 装载初始化失败」与「蜘蛛初始化失败」两类事件使用
     * （入口收在 SpiderDebug.forceLog，调用方只许 JarLoader 的失败分支）。
     * 无视总开关与分类过滤：开关关时缓冲没建就现建、没启动就启动，失败事件
     * 直接落进与普通日志同一个缓冲与同一份落盘文件；普通日志仍走 add/event
     * 的开关口径，不许借道。本方法绝不抛异常——记日志失败必须静默，
     * 不能反噬装载流程。
     */
    public static void force(String tag, String message) {
        if (message == null || message.isEmpty()) return;
        try {
            DiagnosticLogBuffer current = buffer;
            if (current == null) current = create();
            if (current == null) return;
            if (!current.isEnabled()) current.start(false);
            current.add(tag, message, true);
        } catch (Throwable ignored) {
            // 强制通道自身失败静默：装载流程优先。
        }
    }

    public static void event(DiagnosticEvent event) {
        DiagnosticLogBuffer current = buffer;
        if (enabled && current != null && DiagnosticCategories.accepts(categories, event.category())) current.event(event);
    }

    public static void collectorFailure() {
        DiagnosticLogBuffer current = buffer;
        if (enabled && current != null) current.collectorFailure();
    }

    /** Producer-published immutable cache; export never calls back into a player. */
    public static void collectorHealth(String id, DiagnosticEvent event, boolean partial, long generation) {
        DiagnosticLogBuffer current = buffer;
        if (enabled && current != null) current.collectorHealth(id, event, partial, generation);
    }

    public static String text() {
        if (!enabled) {
            // 开关关时普通日志不记，但强制通道落下的失败记录必须能当场读回：
            // 事后再开开关会清缓冲并删落盘段，等开完再读就什么都没了。
            // 缓冲里有内容才给内容，没有才回未开启，旧口径不变。
            DiagnosticLogBuffer.Snapshot forced = incremental(-1, "", -1);
            if (forced == null || forced.lines().isEmpty()) return "调试日志未开启";
            return DiagnosticLogBuffer.header(forced.health()) + forced.text();
        }
        DiagnosticLogBuffer.Snapshot snapshot = incremental(-1, "", -1);
        if (snapshot == null) return "暂无调试日志";
        return DiagnosticLogBuffer.header(snapshot.health()) + snapshot.text();
    }

    public static List<String> snapshot() {
        DiagnosticLogBuffer.Snapshot snapshot = incremental(-1, "", -1);
        return snapshot == null ? List.of() : snapshot.lines();
    }

    public static List<String> observedOrigins() {
        DiagnosticLogBuffer current = buffer;
        return !enabled || current == null ? List.of() : current.origins();
    }

    public static DiagnosticLogBuffer.Snapshot incremental(long afterSeq, String runId, long generation) {
        DiagnosticLogBuffer current = buffer;
        return current == null ? null : current.snapshot(afterSeq, runId, generation);
    }

    public static DiagnosticLogBuffer.Export export() {
        DiagnosticLogBuffer current = buffer;
        if (enabled && current != null) return current.export(750);
        // 同 text()：开关关时若缓冲里有强制通道落下的失败记录，照常导出那一份。
        if (current != null && size() > 0) return current.export(750);
        byte[] bytes = "调试日志未开启".getBytes(StandardCharsets.UTF_8);
        return new DiagnosticLogBuffer.Export(new ByteArrayInputStream(bytes), bytes.length, true, () -> new ByteArrayInputStream(bytes));
    }

    public static int size() {
        DiagnosticLogBuffer.Snapshot snapshot = incremental(-1, "", -1);
        return snapshot == null ? 0 : snapshot.lines().size();
    }

    public static long bytes() {
        DiagnosticLogBuffer current = buffer;
        return current == null ? 0 : current.health().get("diskBytes").getAsLong();
    }

    public static long version() { return buffer == null ? 0 : buffer.version(); }
    public static long captureGeneration() { return buffer == null ? 0 : buffer.generation(); }

    public static synchronized void clear() {
        com.github.catvod.crawler.diagnostics.DiagnosticCapture.stop("diagnostics-cleared");
        DiagnosticLogBuffer current = buffer;
        if (current == null) return;
        current.clear();
        if (enabled) beginCollection("clear");
    }
}
