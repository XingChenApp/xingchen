package com.fongmi.android.tv.tts;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 系统 TTS 合成器（第二家供应商，他 2026-10-27 令「不许只押一家」）：
 * Edge 合成失败时由 TtsEngine 调它把一段文本合成 wav 落盘，走与 Edge
 * 段完全相同的播放链（PlaybackService＋PlayerManager），听书永不哑。
 * 初始化在主线程做（部分厂商引擎要求），合成在引擎 worker 线程阻塞等。
 */
public class SystemTtsSynth {

    /** 系统语音不可用的分型（供引擎拼人话错误）：无问题／没引擎／没中文嗓。 */
    public static final int PROBLEM_NONE = 0;
    public static final int PROBLEM_NO_ENGINE = 1;
    public static final int PROBLEM_NO_CHINESE = 2;

    private final Context appContext;
    private volatile TextToSpeech tts;
    private volatile boolean initDone;
    private volatile boolean initOk;
    private volatile boolean released;
    private volatile int problem = PROBLEM_NONE;
    // 初始化代际守卫（体检类4）：同一时刻只许一代在建，后来者等同一代
    // 的 latch，不许再 new 一个覆盖 tts；每代有代号，迟到回调只认自己
    // 那代，不是当前代（已被 shutdown 顶掉）的回调不许改写状态。
    private final Object initLock = new Object();
    private boolean initTrying;
    private CountDownLatch pendingLatch;
    private int initGen;

    public SystemTtsSynth(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** 当前不可用分型；引擎/初始化未跑完时为 PROBLEM_NONE。 */
    public int problem() {
        return problem;
    }

    /** 确保已初始化（阻塞，至多等约 6 秒）。无引擎/初始化失败返回 false。 */
    private boolean ensureInit() {
        if (initDone) return initOk;
        final CountDownLatch latch;
        final int gen;
        synchronized (initLock) {
            if (initDone) return initOk;
            if (initTrying) {
                // 已有一代在建：等同一代的结果，绝不另起一代覆盖 tts
                // （旧实现超时后每次调用都再 new 一个，旧实例永不
                // shutdown、迟到回调还按字段乱写状态——体检类4）。
                latch = pendingLatch;
                gen = -1;
            } else {
                initTrying = true;
                gen = ++initGen;
                latch = pendingLatch = new CountDownLatch(1);
            }
        }
        if (gen > 0) {
            Runnable create = () -> createTts(gen, latch);
            if (Looper.myLooper() == Looper.getMainLooper()) {
                create.run();
            } else {
                new Handler(Looper.getMainLooper()).post(create);
            }
        }
        try {
            latch.await(6, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return initDone && initOk && tts != null;
    }

    /** 建一代 TTS 实例（主线程执行）；初始化回调只把结果交给本代的 finishInit。 */
    private void createTts(int gen, CountDownLatch latch) {
        try {
            final java.util.concurrent.atomic.AtomicReference<TextToSpeech> self =
                    new java.util.concurrent.atomic.AtomicReference<>();
            TextToSpeech created = new TextToSpeech(appContext,
                    status -> finishInit(gen, self.get(), status, latch));
            self.set(created);
            tts = created;
            if (released) {
                // shutdown 抢先过：这一代不接管，回调按过期代自己收尾；
                // 这里先关掉避免回调迟迟不到时实例悬着。
                try {
                    created.shutdown();
                } catch (Throwable ignored) {
                }
                if (tts == created) tts = null;
            }
        } catch (Throwable t) {
            finishInit(gen, null, TextToSpeech.ERROR, latch);
        }
    }

    /**
     * 一代初始化的收口：只有当代且未释放才接管状态（原回调里的中文嗓
     * 定版逻辑照旧，只是改读本代实例、不再读会被覆盖的字段）；迟到或
     * 已释放的代不许改写 initOk/problem/initDone，自己关掉实例防漏。
     */
    private void finishInit(int gen, TextToSpeech instance, int status, CountDownLatch latch) {
        boolean adopt;
        synchronized (initLock) {
            adopt = gen == initGen && !released;
            if (adopt) {
                initTrying = false;
                pendingLatch = null;
                initOk = status == TextToSpeech.SUCCESS;
                if (initOk) {
                    // 中文嗓定版：有中文才算能读；没有就标 PROBLEM_NO_CHINESE，
                    // 不许拿外语嗓把中文读成乱音还报成功。
                    boolean zh = false;
                    try {
                        TextToSpeech t = instance != null ? instance : tts;
                        if (t != null && t.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE)
                                >= TextToSpeech.LANG_AVAILABLE) {
                            t.setLanguage(Locale.SIMPLIFIED_CHINESE);
                            zh = true;
                        } else if (t != null && t.isLanguageAvailable(Locale.CHINESE)
                                >= TextToSpeech.LANG_AVAILABLE) {
                            t.setLanguage(Locale.CHINESE);
                            zh = true;
                        }
                    } catch (Throwable ignored) {
                    }
                    problem = zh ? PROBLEM_NONE : PROBLEM_NO_CHINESE;
                } else {
                    problem = PROBLEM_NO_ENGINE;
                }
                initDone = true;
            }
        }
        if (!adopt && instance != null) {
            try {
                instance.shutdown();
            } catch (Throwable ignored) {
            }
        }
        latch.countDown();
    }

    /**
     * 合成一段 → wav 文件（阻塞，引擎 worker 线程调）。
     * 成功且文件非空返回 true；任何失败返回 false（调方按双家全败处理）。
     */
    public boolean synthesize(String text, float rate, File outFile) {
        if (released || text == null || text.trim().isEmpty()) return false;
        if (!ensureInit()) {
            if (problem == PROBLEM_NONE) problem = PROBLEM_NO_ENGINE;
            return false;
        }
        if (problem == PROBLEM_NO_CHINESE) return false;
        TextToSpeech t = tts;
        if (t == null) return false;
        try {
            t.setSpeechRate(rate > 0 ? rate : 1.0f);
        } catch (Throwable ignored) {
        }
        // 音色真传到合成端（2026-11-01 定段补）：每段按当前选择套嗓，
        // 选了哪把、以哪把出声，日志可查；没选/没找到＝引擎缺省嗓。
        String appliedVoice = SysVoiceCatalog.applySelected(t);
        Log.i("SystemTtsSynth", "合成一段：系统音色="
                + (appliedVoice.isEmpty() ? "(引擎缺省)" : appliedVoice)
                + " 语速=" + rate);
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicBoolean ok = new AtomicBoolean(false);
        final String uid = "sys_" + System.nanoTime();
        try {
            t.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                }

                @Override public void onDone(String utteranceId) {
                    if (uid.equals(utteranceId)) {
                        ok.set(true);
                        done.countDown();
                    }
                }

                @Override public void onError(String utteranceId) {
                    if (uid.equals(utteranceId)) done.countDown();
                }
            });
            File parent = outFile.getParentFile();
            if (parent != null) parent.mkdirs();
            if (outFile.exists()) outFile.delete();
            int rc = t.synthesizeToFile(text, new Bundle(), outFile, uid);
            if (rc != TextToSpeech.SUCCESS) return false;
            // 超时按文本量给：约每秒 8 字打底 15 秒、封顶 60 秒
            long timeoutSec = Math.min(60, Math.max(15, text.length() / 8 + 10));
            if (!done.await(timeoutSec, TimeUnit.SECONDS)) {
                try {
                    t.stop();
                } catch (Throwable ignored) {
                }
                return false;
            }
            return ok.get() && outFile.exists() && outFile.length() > 0;
        } catch (Throwable e) {
            return false;
        }
    }

    public void shutdown() {
        released = true;
        synchronized (initLock) {
            // 顶掉当前代：在建那代的迟到回调回来时按过期代处理
            // （finishInit 不接管、自己关实例），不许把已释放的状态改活。
            initGen++;
            initTrying = false;
            pendingLatch = null;
        }
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
        } catch (Throwable ignored) {
        }
        tts = null;
    }
}
