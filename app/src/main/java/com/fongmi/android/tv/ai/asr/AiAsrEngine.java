package com.fongmi.android.tv.ai.asr;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 本地语音识别引擎（高层封装）。
 *
 * 职责：
 * - 协调插件加载（AiAsrPluginManager）与模型就绪（AiAsrModelManager）
 * - 管理 OnlineRecognizer 的流生命周期
 * - 音频喂入 → 解码 → 结果回调（主线程）
 *
 * 使用流程：
 *   AiAsrEngine.get().prepare(context, callback)  // 确保插件+模型就绪
 *   AiAsrEngine.get().start()                      // 开始识别
 *   AiAsrEngine.get().feedPcm(float[])             // 喂 16kHz PCM（AudioProcessor 调用）
 *   AiAsrEngine.get().stop()                       // 停止
 */
public class AiAsrEngine {

    private static volatile AiAsrEngine instance;

    public static AiAsrEngine get() {
        if (instance == null) {
            synchronized (AiAsrEngine.class) {
                if (instance == null) instance = new AiAsrEngine();
            }
        }
        return instance;
    }

    /** 识别结果回调（主线程） */
    public interface Listener {
        /** 中间结果（实时更新） */
        void onPartial(String text);
        /** 一句话结束（端点检测） */
        void onFinal(String text);
        /** 错误 */
        void onError(String msg);
    }

    /** 准备状态回调 */
    public interface PrepareCallback {
        void onProgress(String stage, int percent);
        void onReady();
        void onError(String msg);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor();

    private IAiAsrBridge bridge;
    private long streamPtr = 0;
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** start 与 release 的状态锁（低-09）：防 start 抢在 release 队列任务置空 bridge 之前建流。 */
    private final Object stateLock = new Object();
    /** 释放中标记（低-02，由 stateLock 守）：release() 同步置位、新桥装入时清零；置位期间 start() 一律失败返回。 */
    private boolean releasing;
    private Listener listener;
    private String lastText = "";

    private AiAsrEngine() {}

    /**
     * 取桥现判（低-04）：本类另持的 bridge 与插件管理器是同一实例；插件被删时
     * 管理器先 release 并置空自己的引用，本类若继续拿着旧引用调用，就是摸已释放
     * 的桥。故每次用桥前先与管理器当前持有的实例对一遍：对不上＝桥已被释放/替换，
     * 本地引用一并清掉、按无桥处理，绝不再调用它。
     */
    private IAiAsrBridge liveBridge() {
        IAiAsrBridge b = bridge;
        if (b == null) return null;
        if (b != AiAsrPluginManager.getBridge()) {
            bridge = null;
            return null;
        }
        return b;
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 准备引擎：确保插件已加载且模型已下载，然后初始化识别器。
     */
    public void prepare(Context context, PrepareCallback callback) {
        IAiAsrBridge ready = liveBridge();
        if (ready != null && ready.isReady()) {
            callback.onReady();
            return;
        }
        Context appContext = context.getApplicationContext();

        // 1. 确保模型已下载
        if (!AiAsrModelManager.isModelReady(appContext)) {
            callback.onProgress("下载语音模型", 0);
            AiAsrModelManager.downloadModel(appContext, new AiAsrModelManager.Callback() {
                @Override
                public void onProgress(String stage, int percent) {
                    callback.onProgress(stage, percent / 2); // 模型占 0-50%
                }

                @Override
                public void onSuccess(File modelDir) {
                    loadPluginAndInit(appContext, callback);
                }

                @Override
                public void onError(String msg) {
                    callback.onError(msg);
                }
            });
        } else {
            loadPluginAndInit(appContext, callback);
        }
    }

    private void loadPluginAndInit(Context context, PrepareCallback callback) {
        // 2. 确保插件已加载
        callback.onProgress("加载识别引擎", 55);
        AiAsrPluginManager.ensurePlugin(context, new AiAsrPluginManager.Callback() {
            @Override
            public void onProgress(String stage, int percent) {
                callback.onProgress(stage, 50 + percent / 2); // 插件占 50-100%
            }

            @Override
            public void onSuccess(IAiAsrBridge b) {
                // 3. 初始化识别器（后台线程，模型加载较慢）
                new Thread(() -> {
                    try {
                        File modelDir = AiAsrModelManager.modelDir(context);
                        int threads = Math.min(4, Math.max(2,
                                Runtime.getRuntime().availableProcessors() - 1));
                        boolean ok = b.init(modelDir.getAbsolutePath(), threads);
                        if (!ok) {
                            postError(callback, "识别器初始化失败");
                            return;
                        }
                        synchronized (stateLock) {
                            bridge = b;
                            releasing = false; // 新桥装入：解除 release() 留下的释放中标记
                        }
                        mainHandler.post(callback::onReady);
                    } catch (Exception e) {
                        postError(callback, "初始化失败：" + e.getMessage());
                    }
                }).start();
            }

            @Override
            public void onError(String msg) {
                postError(callback, msg);
            }
        });
    }

    private void postError(PrepareCallback callback, String msg) {
        mainHandler.post(() -> callback.onError(msg));
    }

    /**
     * 开始识别（创建新流）。
     * 必须在 prepare 成功后调用。
     */
    public boolean start() {
        synchronized (stateLock) {
            // 取桥与建流同锁核对（低-02）：释放中直接失败，不许在已被摘下的旧桥上建流
            if (releasing) return false;
            IAiAsrBridge b = liveBridge();
            if (b == null || !b.isReady()) return false;
            if (!running.compareAndSet(false, true)) return true; // 已在运行
            try {
                streamPtr = b.createStream();
                if (streamPtr == 0) {
                    running.set(false);
                    return false;
                }
                lastText = "";
                return true;
            } catch (Exception e) {
                running.set(false);
                return false;
            }
        }
    }

    /**
     * 喂入 PCM 音频（16kHz 单声道 float，范围 [-1, 1]）。
     * 由 AiAsrAudioProcessor 在音频线程调用。
     */
    public void feedPcm(float[] samples) {
        final IAiAsrBridge b = liveBridge();
        if (!running.get() || b == null || streamPtr == 0) return;
        if (samples == null || samples.length == 0) return;
        final long ptr = streamPtr;
        final float[] copy = samples.clone(); // 避免调用方复用 buffer
        decodeExecutor.execute(() -> {
            // 排队期间桥可能被删插件释放：与管理器当前持有的桥对不上就不摸
            if (!running.get() || ptr != streamPtr || AiAsrPluginManager.getBridge() != b) return;
            try {
                b.acceptWaveform(ptr, copy);
                b.decode(ptr);
                String text = b.getResult(ptr);
                if (text != null && !text.equals(lastText)) {
                    lastText = text;
                    final String t = text;
                    // 端点检测：一句话说完
                    if (b.isEndpoint(ptr)) {
                        mainHandler.post(() -> {
                            if (listener != null) listener.onFinal(t);
                        });
                        // 端点后重置流，开始下一句
                        resetStream();
                    } else if (!t.isEmpty()) {
                        mainHandler.post(() -> {
                            if (listener != null) listener.onPartial(t);
                        });
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    /** 端点后重置流 */
    private void resetStream() {
        decodeExecutor.execute(() -> {
            if (!running.get()) return;
            IAiAsrBridge b = liveBridge();
            if (b == null) return;
            try {
                long oldPtr = streamPtr;
                long newPtr = b.createStream();
                if (newPtr != 0) {
                    streamPtr = newPtr;
                    b.releaseStream(oldPtr);
                    lastText = "";
                }
            } catch (Exception ignored) {
            }
        });
    }

    /** 停止识别 */
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        final long ptr = streamPtr;
        streamPtr = 0;
        final IAiAsrBridge b = liveBridge();
        decodeExecutor.execute(() -> {
            try {
                // 排队期间桥被删插件释放过：流随桥一起没了，不再摸已释放的桥
                if (b != null && ptr != 0 && AiAsrPluginManager.getBridge() == b) {
                    b.inputFinished(ptr);
                    b.releaseStream(ptr);
                }
            } catch (Exception ignored) {}
        });
    }

    /** 释放引擎（App 退出时） */
    public void release() {
        // 先同步摘桥（低-02）：在 stateLock 内当场打释放中标记、置空 bridge、置
        // running=false 并摘下当前流——摘桥在 release() 返回前就完成，此后 start()
        // 同锁核对只能见到 releasing 或空桥，必失败返回，不可能再在旧桥上建流并报
        // 成功、随后被队列任务回滚（低-09 只排队收尾时正是这个窗口）。
        final IAiAsrBridge oldBridge;
        final long oldStream;
        synchronized (stateLock) {
            releasing = true;
            oldBridge = bridge;
            bridge = null;
            oldStream = streamPtr;
            streamPtr = 0;
            running.set(false);
        }
        // 真 release 是重活，仍排队列：任务只碰上面捕获的旧桥/旧流，不再读写的
        // bridge、running、streamPtr 等共享状态，排在它前面的喂入任务也只会因
        // running=false／流指针已清而自行跳过。
        decodeExecutor.execute(() -> {
            // 幂等（低-04）：管理器已不持有这只桥＝删插件时已由管理器 release 过，
            // 这里再碰就是二次释放；只有管理器还持有的才归本类收尾。
            if (oldBridge == null || oldBridge != AiAsrPluginManager.getBridge()) return;
            try {
                if (oldStream != 0) {
                    oldBridge.inputFinished(oldStream);
                    oldBridge.releaseStream(oldStream);
                }
            } catch (Exception ignored) {}
            try {
                oldBridge.release();
            } catch (Exception ignored) {}
        });
    }
}
