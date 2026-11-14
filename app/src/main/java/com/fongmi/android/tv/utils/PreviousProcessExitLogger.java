package com.fongmi.android.tv.utils;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.diagnostics.DiagnosticEvent;
import com.github.catvod.utils.Prefers;

import java.util.ArrayList;
import java.util.List;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class PreviousProcessExitLogger {

    private static final String KEY_LAST_EXIT_TIMESTAMP = "debug_last_process_exit_timestamp";
    // 「上次退出」设置页展示组：只落最新一条主进程记录的展示字段，与水位键分开，
    // 不参与 fresh/水位判定；写入点只在 logApi30 已读到的 main 表上，不新造采集链。
    private static final String KEY_DISPLAY_EXIT_TS = "debug_last_exit_ts";
    private static final String KEY_DISPLAY_EXIT_REASON = "debug_last_exit_reason";
    private static final String KEY_DISPLAY_EXIT_IMPORTANCE = "debug_last_exit_importance";
    private static final String KEY_DISPLAY_EXIT_RSS = "debug_last_exit_rss";

    private PreviousProcessExitLogger() {
    }

    public static void log(Context context) {
        if (!SpiderDebug.isEnabled()) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            DebugLogStore.event(event().unknown("exitReason", DiagnosticEvent.Status.NOT_SUPPORTED));
            return;
        }
        Context app = context.getApplicationContext();
        Task.execute(() -> {
            if (!SpiderDebug.isEnabled()) return;
            try { logApi30(app); }
            catch (Throwable error) {
                DebugLogStore.event(event().unknown("exitReason", DiagnosticEvent.Status.READ_ERROR)
                        .observed("javaClass", error.getClass().getSimpleName()));
                SpiderDebug.log("process-exit", "query failed error=%s", error.getClass().getSimpleName());
            }
        });
    }

    /** 取回记录上限：系统按包返回全部进程的记录，多取一些才够筛出主进程自己的。 */
    private static final int MAX_FETCH = 16;
    /** 单次启动最多补打的主进程死因条数（只记最新若干条）。 */
    private static final int MAX_LOG = 8;

    @RequiresApi(Build.VERSION_CODES.R)
    private static void logApi30(Context context) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager == null) { DebugLogStore.event(event().unknown("exitReason", DiagnosticEvent.Status.UNAVAILABLE)); return; }
        List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, MAX_FETCH);
        // 系统按包返回全部进程的记录：WebView 隔离子进程死得最频、恒排最前（描述
        // [ISOLATED NOT NEEDED]、PSS 恒 0）。旧版只取第 1 条，主进程自己的死因永远
        // 轮不上、还被它的时间戳水位永久跳过。必须按进程名精确筛主进程本身
        // （主进程名＝包名，子进程带 :后缀）。
        List<ApplicationExitInfo> main = new ArrayList<>();
        for (ApplicationExitInfo exit : exits) {
            if (context.getPackageName().equals(exit.getProcessName())) main.add(exit);
        }
        if (main.isEmpty()) { DebugLogStore.event(event().unknown("exitReason", DiagnosticEvent.Status.UNAVAILABLE)); return; }
        // 设置页「上次退出」展示落盘：取最新一条（返回序新→旧，main.get(0)），
        // 在水位逻辑之前落，保证即使本条已被水位跳过、展示也是最新实况。
        // 整段围住，失败不许影响原日志流与水位语义。
        try {
            ApplicationExitInfo latest = main.get(0);
            Prefers.put(KEY_DISPLAY_EXIT_TS, latest.getTimestamp());
            Prefers.put(KEY_DISPLAY_EXIT_REASON, latest.getReason());
            Prefers.put(KEY_DISPLAY_EXIT_IMPORTANCE, latest.getImportance());
            Prefers.put(KEY_DISPLAY_EXIT_RSS, latest.getRss());
        } catch (Throwable ignored) {
        }
        long watermark = Prefers.getLong(KEY_LAST_EXIT_TIMESTAMP, 0);
        List<ApplicationExitInfo> fresh = new ArrayList<>();
        for (ApplicationExitInfo exit : main) {
            if (exit.getTimestamp() > watermark) fresh.add(exit);
        }
        if (fresh.isEmpty()) return;
        // 返回序为新→旧：只留最新 MAX_LOG 条，水位先推到最新一条防重复打，
        // 再按旧→新逐条打进导出日志（通道 diag-exit，一条一次主进程死亡）。
        int logged = Math.min(fresh.size(), MAX_LOG);
        Prefers.put(KEY_LAST_EXIT_TIMESTAMP, fresh.get(0).getTimestamp());
        for (int i = logged - 1; i >= 0; i--) {
            ApplicationExitInfo exit = fresh.get(i);
            SpiderDebug.log("diag-exit",
                    "main-process exit reason=%s(%d) status=%d importance=%d pid=%d time=%s ts=%d pss=%d rss=%d description=%s",
                    reasonName(exit.getReason()), exit.getReason(), exit.getStatus(), exit.getImportance(),
                    exit.getPid(), formatTime(exit.getTimestamp()), exit.getTimestamp(),
                    exit.getPss(), exit.getRss(), safe(exit.getDescription()));
        }
        if (fresh.size() > logged) SpiderDebug.log("diag-exit", "older main-process exits skipped count=%d (kept newest %d)", fresh.size() - logged, logged);
        ApplicationExitInfo exit = fresh.get(0);
        long timestamp = exit.getTimestamp();
        SpiderDebug.log("process-exit",
                "previous reason=%s(%d) status=%d importance=%d timestamp=%d pss=%d rss=%d description=%s",
                reasonName(exit.getReason()), exit.getReason(), exit.getStatus(), exit.getImportance(),
                timestamp, exit.getPss(), exit.getRss(), safe(exit.getDescription()));
        DebugLogStore.event(event().observed("exitReason", reasonName(exit.getReason())).observed("exitStatus", exit.getStatus())
                .observed("firstSeenMs", timestamp).unknown("previousRun", DiagnosticEvent.Status.UNKNOWN)
                .observed("reason", "previous-process-exit; correlate with journal, not current trace").pin("previous-process-exit"));
        captureTrace(exit);
    }

    private static String formatTime(long timestamp) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(new java.util.Date(timestamp));
    }

    private static DiagnosticEvent event() {
        return new DiagnosticEvent("process.recovery", "none", "process", 0, 0)
                .source("android", String.valueOf(Build.VERSION.SDK_INT), "app", "ApplicationExitInfo", "previous-process; trace-unresolved",
                        null, null, 0, android.os.SystemClock.elapsedRealtimeNanos());
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static void captureTrace(ApplicationExitInfo exit) {
        try (InputStream input = exit.getTraceInputStream()) {
            if (input == null) {
                DebugLogStore.event(event().unknown("traceAvailable", DiagnosticEvent.Status.UNAVAILABLE));
                return;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] part = new byte[1024];
            int read;
            while (bytes.size() < 8192 && (read = input.read(part, 0, Math.min(part.length, 8192 - bytes.size()))) > 0) bytes.write(part, 0, read);
            boolean text = exit.getReason() == ApplicationExitInfo.REASON_ANR;
            DebugLogStore.event(event().observed("traceAvailable", true).observed("bytes", bytes.size())
                    .observed("format", text ? "anr-text-prefix" : "platform-trace; binary content not exported")
                    .observed("truncated", bytes.size() == 8192));
            if (text) {
                String trace = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                int chunks = (trace.length() + 999) / 1000;
                for (int i = 0; i < chunks; i++) DebugLogStore.event(event().observed("chunk", i).observed("chunkCount", chunks)
                        .message(trace.substring(i * 1000, Math.min(trace.length(), (i + 1) * 1000))));
            }
        } catch (Exception error) {
            DebugLogStore.event(event().unknown("traceAvailable", DiagnosticEvent.Status.READ_ERROR)
                    .observed("javaClass", error.getClass().getSimpleName()));
        }
    }

    /**
     * 设置页「上次退出」一行人话：读回展示组四字段拼成
     * 「yyyy-MM-dd HH:mm:ss · <reason 人话> · 重要性 <importance> · RSS <x.x MB>」；
     * 无记录（时间戳未落过）返 null，调用方显示「无记录」。
     * reason 常量是编译期内联 int，本法不加载 ApplicationExitInfo 类，低版本可安全调用（恒无记录）。
     */
    public static String lastExitSummary() {
        long ts = Prefers.getLong(KEY_DISPLAY_EXIT_TS, 0);
        if (ts <= 0) return null;
        int reason = Prefers.getInt(KEY_DISPLAY_EXIT_REASON, ApplicationExitInfo.REASON_OTHER);
        int importance = Prefers.getInt(KEY_DISPLAY_EXIT_IMPORTANCE, 0);
        long rss = Prefers.getLong(KEY_DISPLAY_EXIT_RSS, 0);
        String rssMb = String.format(java.util.Locale.ROOT, "%.1f", rss / 1048576.0);
        return formatTime(ts) + " · " + reasonCn(reason) + " · 重要性 " + importance + " · RSS " + rssMb + " MB";
    }

    private static String reasonCn(int reason) {
        return switch (reason) {
            case ApplicationExitInfo.REASON_EXIT_SELF -> "主动退出";
            case ApplicationExitInfo.REASON_SIGNALED -> "被信号终止";
            case ApplicationExitInfo.REASON_LOW_MEMORY -> "内存不足被系统回收";
            case ApplicationExitInfo.REASON_CRASH -> "崩溃";
            case ApplicationExitInfo.REASON_CRASH_NATIVE -> "崩溃（底层）";
            case ApplicationExitInfo.REASON_ANR -> "卡死无响应";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "初始化失败";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "权限变更被终止";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "资源占用过高被终止";
            case ApplicationExitInfo.REASON_USER_REQUESTED -> "手动强停";
            case ApplicationExitInfo.REASON_USER_STOPPED -> "被停止运行";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "依赖进程死亡连带";
            case ApplicationExitInfo.REASON_OTHER -> "其他原因";
            default -> "未知";
        };
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static String reasonName(int reason) {
        return switch (reason) {
            case ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF";
            case ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED";
            case ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY";
            case ApplicationExitInfo.REASON_CRASH -> "CRASH";
            case ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE";
            case ApplicationExitInfo.REASON_ANR -> "ANR";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE";
            case ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED";
            case ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED";
            case ApplicationExitInfo.REASON_OTHER -> "OTHER";
            default -> "UNKNOWN";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
