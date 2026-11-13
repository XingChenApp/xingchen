package com.fongmi.android.tv.ai.asr;

/**
 * sherpa-onnx 插件桥接接口。
 *
 * 设计说明（插件化）：
 * - sherpa-onnx AAR 以 compileOnly 方式参与编译，不打包进 APK
 * - 运行时从插件（dex + so，按需下载）加载实现类 SherpaBridge
 * - App 代码只依赖此接口，不直接引用 com.k2fsa.sherpa.onnx.*，
 *   避免插件未下载时出现 NoClassDefFoundError
 *
 * SherpaBridge 实现类在插件 dex 中，编译时与 AAR 一起打包，
 * 运行时通过 AiAsrPluginManager 加载。
 */
public interface IAiAsrBridge {

    /**
     * 初始化识别器
     * @param modelDir 模型目录（包含 encoder.int8.onnx / decoder.onnx / joiner.int8.onnx / tokens.txt）
     * @param numThreads 推理线程数（建议 2-4）
     * @return true=初始化成功
     */
    boolean init(String modelDir, int numThreads);

    /** 释放识别器 */
    void release();

    /** 是否已初始化 */
    boolean isReady();

    /**
     * 创建一个新的流式识别流（每段音频一个流）
     * @return 流句柄（long，插件内部指针），0=失败
     */
    long createStream();

    /** 释放流 */
    void releaseStream(long streamPtr);

    /**
     * 喂入 PCM 音频数据
     * @param streamPtr 流句柄
     * @param samples 16kHz 单声道 float PCM，范围 [-1, 1]
     */
    void acceptWaveform(long streamPtr, float[] samples);

    /** 对流执行一轮解码 */
    void decode(long streamPtr);

    /** 流是否已有可读结果 */
    boolean isReady(long streamPtr);

    /** 是否检测到端点（一段话说完） */
    boolean isEndpoint(long streamPtr);

    /** 获取当前识别结果文本 */
    String getResult(long streamPtr);

    /** 通知流输入结束 */
    void inputFinished(long streamPtr);
}
