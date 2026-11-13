package com.fongmi.android.tv.tts;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 朗读合成引擎（后台单线程）：
 * 按 (章, 段) 队列逐段合成音频落缓存，当前段优先、后段预取。
 * 回调跑在 worker 线程，调用方自行切回主线程。
 * 供应商两家（他 2026-10-27 令「不许只押一家」）：Edge 神经音色优先，
 * 失败即切系统 TTS 兜底（兜底的那一章整章系统声、不中途换嗓，
 * 下一章再试 Edge），两家全败才报人话错误并停工。
 * 引擎模式可在设置里选（自动/Edge/系统）。
 */
public class TtsEngine {

    private static final String TAG = "TtsEngine";

    public static final int MODE_AUTO = 0;
    public static final int MODE_EDGE = 1;
    public static final int MODE_SYSTEM = 2;

    /** 章正文提供方（调方实现，可阻塞）。 */
    public interface TextProvider {
        String textOf(int chapterIndex);
    }

    public interface Listener {
        void onSegmentReady(int chapterIndex, int segIndex, File file);

        void onChapterEmpty(int chapterIndex);

        void onTtsError(String humanMsg);

        /** Edge 失败、已切系统语音兜底；reason 是 Edge 的人话错误，供页面明示卡点。 */
        default void onFallbackToSystem(String reason) {
        }
    }

    private static class Need {
        final int chapter;
        final int seg;

        Need(int c, int s) {
            chapter = c;
            seg = s;
        }

        String key() {
            return chapter + ":" + seg;
        }
    }

    private final File root;
    private final android.content.Context appContext;
    private final TextProvider provider;
    private final Listener listener;
    private SystemTtsSynth systemSynth;
    private volatile boolean edgeDown;
    private volatile long edgeRetryAt;
    /** 兜底落在哪一章：本章内不中途换嗓（逐段重试会 Edge/系统来回跳），到下一章才再试 Edge。 */
    private volatile int edgeDownChapter = -1;
    private volatile int engineMode = MODE_AUTO;
    private final Deque<Need> queue = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private final Map<Integer, List<String>> segCache = new HashMap<>();
    private final Map<Integer, String> textCache = new HashMap<>();

    private volatile String voiceId;
    private volatile float rate;
    private volatile boolean running = true;
    private volatile boolean errorSent;
    private final Thread worker;

    public TtsEngine(Context context, String bookKey, TextProvider provider, Listener listener) {
        this.provider = provider;
        this.listener = listener;
        this.appContext = context.getApplicationContext();
        this.voiceId = EdgeVoiceCatalog.defaultVoice();
        this.rate = 1.0f;
        String safe = bookKey.replaceAll("[^a-zA-Z0-9]", "_");
        if (safe.length() > 48) safe = safe.substring(0, 48);
        this.root = new File(new File(context.getCacheDir(), "tts"), safe);
        purgeOld(new File(context.getCacheDir(), "tts"));
        worker = new Thread(this::loop, "tts-engine");
        worker.start();
    }

    public void setVoice(String voiceId, float rate) {
        if (voiceId != null) this.voiceId = voiceId;
        this.rate = rate;
    }

    /** 引擎模式（自动/Edge/系统）：页面设置项调；切模式后由页面重备当前段。 */
    public void setEngineMode(int mode) {
        this.engineMode = mode;
        if (mode == MODE_EDGE) {
            edgeDown = false;
            edgeDownChapter = -1;
            edgeRetryAt = 0;
        }
    }

    public int engineMode() {
        return engineMode;
    }

    /** 当前实际在用系统语音（模式=系统，或自动模式已兜底）。 */
    public boolean usingSystem() {
        return engineMode == MODE_SYSTEM || edgeDown;
    }

    public String voiceId() {
        return voiceId;
    }

    public float rate() {
        return rate;
    }

    /** 请求一段：本段优先插队，后段自动预取。 */
    public void request(int chapterIndex, int segIndex) {
        synchronized (queue) {
            Need n = new Need(chapterIndex, segIndex);
            if (!queued.contains(n.key())) {
                queue.addFirst(n);
                queued.add(n.key());
                queue.notify();
            }
        }
    }

    /** 该章的段表（确定性切分；空章返回空表）。 */
    public List<String> segmentsOf(int chapterIndex) {
        synchronized (segCache) {
            List<String> segs = segCache.get(chapterIndex);
            if (segs == null) {
                String text = textOf(chapterIndex);
                segs = TtsSegmenter.split(text);
                if (segCache.size() > 8) segCache.clear();
                segCache.put(chapterIndex, segs);
            }
            return segs;
        }
    }

    private String textOf(int chapterIndex) {
        synchronized (textCache) {
            String t = textCache.get(chapterIndex);
            if (t == null) {
                try {
                    t = provider.textOf(chapterIndex);
                } catch (Throwable ignored) {
                    t = "";
                }
                if (t == null) t = "";
                if (textCache.size() > 8) textCache.clear();
                textCache.put(chapterIndex, t);
            }
            return t;
        }
    }

    private void loop() {
        while (running) {
            Need n;
            synchronized (queue) {
                while (queue.isEmpty() && running) {
                    try {
                        queue.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
                if (!running) return;
                n = queue.pollFirst();
                if (n != null) queued.remove(n.key());
            }
            if (n != null) process(n);
        }
    }

    private void process(Need n) {
        List<String> segs = segmentsOf(n.chapter);
        if (segs.isEmpty()) {
            listener.onChapterEmpty(n.chapter);
            return;
        }
        if (n.seg < 0 || n.seg >= segs.size()) return;
        File f = fileFor(n.chapter, n.seg);
        if (f == null) {
            if (!synthesize(n, segs.get(n.seg))) return; // 已报错停工
            f = fileFor(n.chapter, n.seg);
            if (f == null) return;
        }
        // 已 shutdown 就不再回调/预取：在途段是 shutdown 前起的活，
        // 合成返回时页面多半已退出，回调摸已销毁宿主没有意义。
        if (!running) return;
        listener.onSegmentReady(n.chapter, n.seg, f);
        // 预取：本章后两段
        prefetch(n.chapter, n.seg + 1);
        prefetch(n.chapter, n.seg + 2);
        // 章尾：下章前两段
        if (n.seg + 2 >= segs.size()) {
            prefetch(n.chapter + 1, 0);
            prefetch(n.chapter + 1, 1);
        }
    }

    private void prefetch(int chapter, int seg) {
        if (chapter < 0 || seg < 0) return;
        synchronized (queue) {
            Need n = new Need(chapter, seg);
            if (!queued.contains(n.key())) {
                queue.addLast(n);
                queued.add(n.key());
                queue.notify();
            }
        }
    }

    /**
     * 合成一段：按引擎模式走 Edge / 系统 / 自动（Edge 先行、败了切系统兜底）。
     * 兜底不钉死整会话，也不逐段乱跳音色：落兜底的那一章整章走系统声，
     * 到下一章且过了 60 秒冷却下限才再试 Edge，成了恢复 Edge、败了新章
     * 继续整章兜底。Edge 单段只试一次（12 秒快收），不静默重试同段——
     * 干挂重试是 2026-10-28 定根的病象。两家全败才 fail()。
     */
    private boolean synthesize(Need n, String text) {
        boolean wantSystemOnly = engineMode == MODE_SYSTEM;
        boolean tryEdge = !wantSystemOnly;
        if (tryEdge && edgeDown) {
            if (n.chapter != edgeDownChapter
                    && System.currentTimeMillis() >= edgeRetryAt) {
                edgeDown = false;
            } else {
                tryEdge = false;
            }
        }
        if (tryEdge) {
            String edgeErr = null;
            try {
                Log.i(TAG, "合成一段：Edge 音色=" + voiceId + " 语速=" + rate
                        + " 章=" + n.chapter + " 段=" + n.seg);
                byte[] mp3 = new EdgeTtsClient().synthesize(text, voiceId, rate);
                File f = edgeFile(n.chapter, n.seg);
                f.getParentFile().mkdirs();
                File tmp = new File(f.getParent(), f.getName() + ".part");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(mp3);
                }
                if (!tmp.renameTo(f)) {
                    tmp.delete();
                    throw new EdgeTtsClient.TtsException(EdgeTtsClient.ERR_OFFLINE);
                }
                return true;
            } catch (EdgeTtsClient.TtsException e) {
                edgeErr = e.getMessage();
            } catch (Throwable t) {
                edgeErr = EdgeTtsClient.ERR_OFFLINE;
            }
            if (engineMode == MODE_EDGE) {
                fail(edgeErr);
                return false;
            }
            // 自动模式：切系统声兜底并带原因上报；本章整章兜底，下一章再试 Edge
            edgeDown = true;
            edgeDownChapter = n.chapter;
            edgeRetryAt = System.currentTimeMillis() + 60_000L;
            // 回落不许静默冒充（2026-11-01 定段补）：所选 Edge 音色本章
            // 不出声、改走系统嗓，原因落日志，回看有据（页面另有 toast）。
            Log.w(TAG, "Edge 合成失败，本章回落系统语音：原因=" + edgeErr
                    + " 被替下的 Edge 音色=" + voiceId + " 章=" + n.chapter);
            try {
                listener.onFallbackToSystem(edgeErr);
            } catch (Throwable ignored) {
            }
        }
        // 系统语音（模式=系统 / 自动兜底）
        Log.i(TAG, "合成一段：系统语音（模式=" + engineMode + "）章=" + n.chapter
                + " 段=" + n.seg + "，系统音色选择="
                + (SysVoiceCatalog.selected().isEmpty()
                        ? "(引擎缺省)" : SysVoiceCatalog.selected()));
        File sf = sysFile(n.chapter, n.seg);
        File tmp = new File(sf.getParent(), sf.getName() + ".part.wav");
        boolean ok = false;
        try {
            if (systemSynth == null) systemSynth = new SystemTtsSynth(appContext);
            ok = systemSynth.synthesize(text, rate, tmp);
        } catch (Throwable ignored) {
            ok = false;
        }
        if (ok) {
            try {
                sf.getParentFile().mkdirs();
                if (sf.exists()) sf.delete();
                if (tmp.renameTo(sf)) return true;
                tmp.delete();
            } catch (Throwable ignored) {
            }
        } else {
            try {
                tmp.delete();
            } catch (Throwable ignored) {
            }
        }
        fail(bothDownMessage());
        return false;
    }

    /** 两家全败的人话：系统侧按分型说清（没引擎/没中文嗓/合成失败），不许糊成一句「不可用」。 */
    private String bothDownMessage() {
        int prob = systemSynth == null ? SystemTtsSynth.PROBLEM_NO_ENGINE
                : systemSynth.problem();
        String sysPart;
        if (prob == SystemTtsSynth.PROBLEM_NO_CHINESE) {
            sysPart = "系统语音没有中文声音";
        } else if (prob == SystemTtsSynth.PROBLEM_NO_ENGINE) {
            sysPart = "系统语音不可用";
        } else {
            sysPart = "系统语音合成失败";
        }
        if (engineMode == MODE_SYSTEM) {
            return sysPart + "，请到系统设置里给语音引擎装好中文语音后再试";
        }
        return "朗读服务连不上，" + sysPart + "，请检查网络或系统语音设置后重试";
    }

    private void fail(String msg) {
        if (!errorSent) {
            errorSent = true;
            listener.onTtsError(msg == null ? EdgeTtsClient.ERR_OFFLINE : msg);
        }
        running = false;
        synchronized (queue) {
            queue.notify();
        }
    }

    private File edgeFile(int chapter, int seg) {
        String v = voiceId.replaceAll("[^a-zA-Z0-9]", "_");
        String rateTag = EdgeTtsClient.rateTag(rate).replace("+", "p").replace("%", "").replace("-", "m");
        File dir = new File(root, v + "_" + rateTag);
        return new File(dir, "ch" + chapter + "_seg" + seg + ".mp3");
    }

    /** 系统语音段文件：语速由合成时定（文件名带语速档，同 Edge 口径分目录）。 */
    private File sysFile(int chapter, int seg) {
        String rateTag = String.valueOf(Math.round(rate * 100));
        File dir = new File(root, "sys_" + rateTag);
        return new File(dir, "ch" + chapter + "_seg" + seg + ".wav");
    }

    /**
     * 摘掉某段已落盘的系统语音文件（换系统音色后重合成用）：系统段
     * 按语速分目录、不含音色，不摘旧文件 fileFor 会直接命中旧嗓播
     * （换了跟没换一样）；Edge 段按音色分目录，不受影响。
     */
    public void discardSegmentAudio(int chapter, int seg) {
        try {
            File sf = sysFile(chapter, seg);
            if (sf.exists()) sf.delete();
        } catch (Throwable ignored) {
        }
    }

    /** 已落盘的段文件（按当前实际引擎优先，防跨会话混音色）；都没有返回 null。 */
    private File fileFor(int chapter, int seg) {
        File e = edgeFile(chapter, seg);
        File s = sysFile(chapter, seg);
        if (usingSystem()) {
            if (s.exists()) return s;
            if (e.exists()) return e;
        } else {
            if (e.exists()) return e;
            if (s.exists()) return s;
        }
        return null;
    }

    /** 清 3 天前的旧缓存（有界）。 */
    private void purgeOld(File ttsRoot) {
        try {
            if (!ttsRoot.exists()) return;
            long cutoff = System.currentTimeMillis() - 3L * 24 * 3600 * 1000;
            List<File> all = new ArrayList<>();
            collect(ttsRoot, all);
            long total = 0;
            for (File f : all) total += f.length();
            // 超 150MB 或超期都清（旧的先清）
            all.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (File f : all) {
                if (f.lastModified() < cutoff || total > 150L * 1024 * 1024) {
                    total -= f.length();
                    f.delete();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void collect(File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().endsWith(".mp3") || f.getName().endsWith(".wav")) out.add(f);
        }
    }

    public void shutdown() {
        running = false;
        synchronized (queue) {
            queue.notify();
        }
        if (systemSynth != null) {
            try {
                systemSynth.shutdown();
            } catch (Throwable ignored) {
            }
            systemSynth = null;
        }
        // 只置旗时 worker 若正卡在在途合成的网络调用里，要等整段返回才
        // 退出；interrupt 让等待中的 worker 立刻醒，在途段返回后也会被
        // synthesize 的 running 检查与 process 的回调闸拦下，不再往下走。
        worker.interrupt();
    }
}
