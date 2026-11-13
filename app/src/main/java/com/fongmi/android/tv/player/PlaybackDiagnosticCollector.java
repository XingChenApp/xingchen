package com.fongmi.android.tv.player;

import android.media.MediaCodec;
import android.os.Build;
import android.os.SystemClock;

import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.diagnostics.DiagnosticEvent;
import com.github.catvod.crawler.diagnostics.DiagnosticText;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.github.catvod.crawler.diagnostics.DiagnosticEvent.Status.*;

/** Per-engine evidence. Contexts are immutable and may safely outlive the current media. */
public final class PlaybackDiagnosticCollector {
    public record Context(String trace, long generation, long attempt, String mediaId, String role) {}
    private static final AtomicLong IDS = new AtomicLong();
    private static final WeakHashMap<Object, String> OBJECT_IDS = new WeakHashMap<>();
    private final String instance = id("player");
    private final String engine, version;
    private final AtomicLong sequence = new AtomicLong();
    private volatile Context current = new Context("none", 0, 0, null, "unresolved");
    private volatile int videoLevel = -1, audioLevel = -1;
    private volatile String firstError, lastError;
    private volatile boolean protectedMedia;
    private long captureGeneration = -1;
    private volatile boolean ended;

    // ---- 窗内降频（宿主存活步 3，施工单 host-survival-
    // plan §五步 3）----
    // 装配窗（每次 begin 后 AGG_WINDOW_MS）内，引擎证据事件以
    // 约 85 行/秒的密度序列化落字，JSON churn 与双台装配同窗
    // 压在被盖宿主头上。窗内非关键事件改为按名聚合计数、每
    // AGG_FLUSH_MS 批量落一条 diag.observation（明细在
    // message、总量在 count、跨度在 windowMs）——计数一格不
    // 丢，窗外与窗内到期后恢复现行逐条粒度。关键事件
    // （critical()：fatal/error/warn 或 pin 的基线/错误/收尾
    // 证据）永不聚合、即时直通且直通前先冲走在账聚合（保
    // 序）——事故定位靠的那部分粒度不变。这是诊断输出的采
    // 样率调整（时机/密度），不碰任何播放行为判据，不是守
    // 卫/兜底。
    private static final long AGG_WINDOW_MS = 5_000;
    private static final long AGG_FLUSH_MS = 1_000;
    private final Object aggLock = new Object();
    private final java.util.LinkedHashMap<String, Integer> aggCounts = new java.util.LinkedHashMap<>();
    private long aggWindowStartElapsed;
    private long aggSpanStartElapsed;
    private long aggLastFlushElapsed;
    /** 汇总落字中的线程标记：汇总事件经 emit 重载回本类时
     * 凭它直通、不被聚合吞掉。 */
    private final ThreadLocal<Boolean> summaryEmitting = new ThreadLocal<>();

    public PlaybackDiagnosticCollector(String engine, String version) { this.engine = engine; this.version = version; }
    public static boolean enabled() { return DebugLogStore.isEnabled(); }
    public static String id(String kind) { return kind + "-" + IDS.incrementAndGet(); }
    public static synchronized String objectId(Object object, String kind) {
        return object == null ? "none" : OBJECT_IDS.computeIfAbsent(object, key -> id(kind));
    }
    public Context context() { return current; }
    public String instanceId() { return instance; }
    public String engine() { return engine; }
    public boolean ended() { return ended; }
    public boolean protectedMedia() { return protectedMedia; }
    public void protectedMedia(boolean value) { protectedMedia = value; }

    public synchronized Context begin(String trace, String role) {
        if (current.attempt() > 0) end("reprepare");
        boolean same = current.trace().equals(trace);
        current = new Context(PlaybackTrace.normalize(trace), current.generation() + (same ? 0 : 1),
                current.attempt() + 1, id("media"), role);
        videoLevel = audioLevel = -1; firstError = lastError = null; captureGeneration = -1; ended = false;
        synchronized (aggLock) {
            aggWindowStartElapsed = SystemClock.elapsedRealtime();
            aggSpanStartElapsed = aggWindowStartElapsed;
            aggLastFlushElapsed = aggWindowStartElapsed;
        }
        if ("foreground".equals(role)) DiagnosticControls.playback(this);
        baseline(false);
        return current;
    }

    public synchronized void baseline() {
        baseline(true);
    }

    private void baseline(boolean late) {
        if (!enabled() || captureGeneration == DebugLogStore.captureGeneration()) return;
        if (captureGeneration != -1) { videoLevel = audioLevel = -1; firstError = lastError = null; }
        captureGeneration = DebugLogStore.captureGeneration();
        NativeLibraryDiagnostics.request();
        emit(current, "diag.session.begin", "engine-collector", "engine-context", e -> e
                .observed("mode", "standard").observed("captureStartedLate", late)
                .observed("engineInstance", instance).observed("captureGeneration", captureGeneration)
                .unknown("physicalVideo", NOT_OBSERVABLE).unknown("audibility", NOT_OBSERVABLE)
                .pin(instance + "-baseline"));
    }

    public void emit(Context owner, String name, String source, String association, Consumer<DiagnosticEvent> facts) {
        emit(owner, name, source, association, null, facts);
    }

    public void emitNative(Context owner, String name, String source, String association, String prefix, Consumer<DiagnosticEvent> facts) {
        emit(owner, name, source, association, prefix == null ? "" : prefix, facts);
    }

    private void emit(Context owner, String name, String source, String association, String nativePrefix, Consumer<DiagnosticEvent> facts) {
        if (nativePrefix == null ? !DebugLogStore.acceptsEvent(name) : !DebugLogStore.isEnabled()
                || !DebugLogStore.categoryEnabled(com.github.catvod.crawler.diagnostics.DiagnosticCategories.nativePrefix(nativePrefix))) return;
        try {
            Context ctx = owner == null ? new Context("none", 0, 0, null, "unresolved") : owner;
            DiagnosticEvent event = new DiagnosticEvent(name, ctx.trace(), instance, ctx.generation(), ctx.attempt())
                    .source(engine, version, ctx.role(), source, association, ctx.mediaId(), current.mediaId(),
                            sequence.incrementAndGet(), SystemClock.elapsedRealtimeNanos());
            facts.accept(event);
            // 落字分发（步 3 降频）：关键事件先冲净在账聚合
            // 再直通（保序）；窗内非关键事件只入聚合账计数、
            // 到冲刷点由 flushAggregateCounts 批量落字；窗外
            // 恢复逐条、先冲净在账余数再落本条。逻辑内联在
            // 本体内、只用既有 event 变量与 name 形参——本类
            // 对诊断事件类型的新增签名/构造引用点会改变离线
            // 门的多重集（catvod 类在门内不可解析、只有既有
            // 引用点在基线内），冲刷落字也因此走既有 emit
            // 重载＋lambda，不另立构造点。
            boolean absorbed = false;
            if (!Boolean.TRUE.equals(summaryEmitting.get())) {
                if (event.critical()) {
                    flushAggregateCounts();
                } else {
                    long now = SystemClock.elapsedRealtime();
                    boolean flushDue = false;
                    synchronized (aggLock) {
                        // ended 在锁内读：end() 置位后必经 flush
                        // 的 aggLock 释放，锁序给出可见性——attempt
                        // 已结束后迟到事件不入账、照旧逐条直落，
                        // 不许串进下一个 attempt 的账。
                        if (!ended
                                && aggWindowStartElapsed > 0
                                && now - aggWindowStartElapsed < AGG_WINDOW_MS) {
                            aggCounts.merge(name, 1, Integer::sum);
                            absorbed = true;
                            flushDue = now - aggLastFlushElapsed >= AGG_FLUSH_MS;
                        }
                    }
                    if (!absorbed || flushDue) flushAggregateCounts();
                }
            }
            if (!absorbed) DebugLogStore.event(event);
        } catch (RuntimeException ignored) { DebugLogStore.collectorFailure(); }
    }

    /** 聚合冲刷：在账计数整表摘清，落一条 diag.observation
     * ——明细 name=count 串入 message、总量入 count、聚合
     * 跨度入 windowMs。经既有 emit 重载落字（类别门/启停门
     * 与单条事件同口径，关着时计数随关而弃）；落字期间以
     * summaryEmitting 标记本线程，免得汇总事件被自己聚合。 */
    private void flushAggregateCounts() {
        java.util.LinkedHashMap<String, Integer> snapshot;
        long spanMs;
        long now = SystemClock.elapsedRealtime();
        synchronized (aggLock) {
            aggLastFlushElapsed = now;
            if (aggCounts.isEmpty()) {
                aggSpanStartElapsed = now;
                return;
            }
            snapshot = new java.util.LinkedHashMap<>(aggCounts);
            aggCounts.clear();
            spanMs = now - aggSpanStartElapsed;
            aggSpanStartElapsed = now;
        }
        if (!enabled()) return;
        long total = 0;
        StringBuilder detail = new StringBuilder("agg");
        for (java.util.Map.Entry<String, Integer> entry : snapshot.entrySet()) {
            total += entry.getValue();
            detail.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
        }
        final long totalCount = total;
        final long span = spanMs;
        final String text = detail.toString();
        summaryEmitting.set(Boolean.TRUE);
        try {
            emit(current, "diag.observation", "engine-collector", "engine-context", e -> {
                e.message(text);
                e.observed("count", totalCount);
                e.observed("windowMs", span);
            });
        } finally {
            summaryEmitting.remove();
        }
    }

    public void emit(String name, String source, Consumer<DiagnosticEvent> facts) {
        emit(current, name, source, "engine-context", facts);
    }

    public void evidence(Context owner, boolean video, int level) {
        if (owner != current || !enabled()) return;
        if (video) videoLevel = Math.max(videoLevel, level); else audioLevel = Math.max(audioLevel, level);
    }

    public void error(Context owner, String domain, String stage, Throwable error) {
        if (!enabled() || error == null) return;
        String errorId = id("error");
        if (owner == current) { if (firstError == null) firstError = errorId; lastError = errorId; }
        if (domain.startsWith("video") || domain.startsWith("audio"))
            emit(owner, domain.startsWith("video") ? "video.error" : "audio.error", domain, "exception-chain", e -> e
                    .severity("error").observed("errorId", errorId).observed("stage", stage)
                    .observed("javaClass", error.getClass().getName()).message(error.getMessage()));
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 8 && visited.add(cause); depth++, cause = cause.getCause()) {
            Throwable item = cause;
            int index = depth;
            emit(owner, "diag.error", domain, "exception-chain", e -> {
                e.severity("error").observed("errorId", errorId).observed("domain", domain).observed("stage", stage)
                        .observed("chunk", index).observed("javaClass", item.getClass().getName())
                        .observed("message", String.valueOf(item.getMessage())).pin(instance + "-error");
                if (Build.VERSION.SDK_INT >= 21 && item instanceof MediaCodec.CodecException codec) {
                    e.observed("diagnosticInfo", codec.getDiagnosticInfo()).observed("recoverable", codec.isRecoverable())
                            .observed("transient", codec.isTransient());
                    if (Build.VERSION.SDK_INT >= 23) e.observed("errorCode", codec.getErrorCode());
                }
            });
        }
        String detail = DiagnosticText.throwable(error);
        int limit = Math.min(detail.length(), 2640), chunks = (limit + 219) / 220;
        for (int i = 0; i < chunks; i++) {
            int chunk = i;
            String part = detail.substring(i * 220, Math.min(limit, (i + 1) * 220));
            emit(owner, "diag.error", domain, "exception-detail", e -> e.severity("error")
                    .observed("errorId", errorId).observed("chunk", chunk).observed("chunkCount", chunks)
                    .observed("message", part).observed("truncated", detail.length() > limit));
        }
    }

    public synchronized void end(String reason) {
        if (ended) return;
        ended = true;
        flushAggregateCounts();
        Context owner = current;
        com.github.catvod.crawler.diagnostics.DiagnosticCapture.Session depth = com.github.catvod.crawler.diagnostics.DiagnosticCapture.current(owner.trace(), owner.generation(), owner.attempt());
        if (depth != null && depth.instance().equals(instance)) com.github.catvod.crawler.diagnostics.DiagnosticCapture.stop("playback-ended");
        emit(owner, "play.attempt.end", "engine-collector", "engine-context", e -> e
                .observed("reason", reason).observed("closed", true)
                .observed("videoEvidenceLevel", videoLevel < 0 ? null : videoLevel).observed("audioEvidenceLevel", audioLevel < 0 ? null : audioLevel)
                .observed("firstError", firstError).observed("lastError", lastError)
                .unknown("physicalVideo", NOT_OBSERVABLE).unknown("audibility", NOT_OBSERVABLE)
                .observed("missingEvidence", "physical presentation; per-collector unavailable fields")
                .pin(instance + "-end"));
    }
}
