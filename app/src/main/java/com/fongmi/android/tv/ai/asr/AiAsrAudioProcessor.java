package com.fongmi.android.tv.ai.asr;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * AI 字幕音频采集器（Media3 AudioProcessor）。
 *
 * 原理：
 * - 串在 ExoPlayer 的音频处理链中，对播放无影响（passthrough）
 * - 旁路采集 PCM，转换为 16kHz 单声道 float，喂给 AiAsrEngine
 * - 只在 AI 字幕开关打开时采集，关闭时零开销（直接透传）
 *
 * 支持的输入格式：PCM_16BIT（最常见）、PCM_FLOAT
 */
public class AiAsrAudioProcessor implements AudioProcessor {

    private static final int TARGET_SAMPLE_RATE = 16000;

    private AudioFormat inputFormat = AudioFormat.NOT_SET;
    private ByteBuffer outputBuffer = EMPTY_BUFFER;

    /** 是否启用采集（由 AiAsrController 控制） */
    private volatile boolean captureEnabled = false;

    /** 重采样状态（线性插值） */
    private double resamplePos = 0;

    /** 流尾标记：queueEndOfStream 后置真，flush/reset 时清零（照上游 PlaybackMediaAudioProcessor 口径） */
    private boolean inputEnded = false;

    public void setCaptureEnabled(boolean enabled) {
        this.captureEnabled = enabled;
        if (!enabled) {
            resamplePos = 0;
        }
    }

    public boolean isCaptureEnabled() {
        return captureEnabled;
    }

    @Override
    public AudioFormat configure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
        int encoding = inputAudioFormat.encoding;
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw new UnhandledAudioFormatException(inputAudioFormat);
        }
        inputFormat = inputAudioFormat;
        resamplePos = 0;
        // passthrough：输出格式与输入一致，不影响播放
        return inputAudioFormat;
    }

    @Override
    public boolean isActive() {
        // 始终 active（passthrough），避免频繁 configure
        return inputFormat != AudioFormat.NOT_SET;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!inputBuffer.hasRemaining()) return;

        // 旁路采集（不阻塞音频线程太久，只做轻量拷贝转换）
        // 注意：先采集，此时 inputBuffer 的 position 还在数据开头
        if (captureEnabled && AiAsrEngine.get().isRunning()) {
            tapAudio(inputBuffer);
        }

        // passthrough：原样输出（消费 inputBuffer，position 会推进到 limit）
        int remaining = inputBuffer.remaining();
        if (outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder());
        } else {
            outputBuffer.clear();
        }
        outputBuffer.put(inputBuffer);
        outputBuffer.flip();
    }

    /**
     * 采集音频：转换为 16kHz 单声道 float，喂给识别引擎。
     * 在音频线程调用，只做必要的转换，耗时操作交给引擎的后台线程。
     */
    private void tapAudio(ByteBuffer inputBuffer) {
        try {
            int encoding = inputFormat.encoding;
            int channels = inputFormat.channelCount;
            int sampleRate = inputFormat.sampleRate;
            if (channels <= 0 || sampleRate <= 0) return;

            int pos = inputBuffer.position();
            int limit = inputBuffer.limit();
            int bytesPerSample = encoding == C.ENCODING_PCM_16BIT ? 2 : 4;
            int frameCount = (limit - pos) / (bytesPerSample * channels);
            if (frameCount <= 0) return;

            // 1. 转为 float 单声道（原采样率）
            float[] mono = new float[frameCount];
            if (encoding == C.ENCODING_PCM_16BIT) {
                for (int i = 0; i < frameCount; i++) {
                    float sum = 0;
                    for (int c = 0; c < channels; c++) {
                        int offset = pos + (i * channels + c) * 2;
                        short s = (short) ((inputBuffer.get(offset) & 0xFF)
                                | (inputBuffer.get(offset + 1) << 8));
                        sum += s / 32768.0f;
                    }
                    mono[i] = sum / channels;
                }
            } else { // PCM_FLOAT
                for (int i = 0; i < frameCount; i++) {
                    float sum = 0;
                    for (int c = 0; c < channels; c++) {
                        int offset = pos + (i * channels + c) * 4;
                        sum += inputBuffer.getFloat(offset);
                    }
                    mono[i] = sum / channels;
                }
            }

            // 2. 重采样到 16kHz（线性插值）
            float[] resampled;
            if (sampleRate == TARGET_SAMPLE_RATE) {
                resampled = mono;
                resamplePos = 0;
            } else {
                double ratio = (double) sampleRate / TARGET_SAMPLE_RATE;
                int outLen = (int) ((frameCount - resamplePos) / ratio);
                if (outLen <= 0) return;
                resampled = new float[outLen];
                for (int i = 0; i < outLen; i++) {
                    double srcIdx = resamplePos + i * ratio;
                    int idx = (int) srcIdx;
                    double frac = srcIdx - idx;
                    float s0 = mono[Math.min(idx, frameCount - 1)];
                    float s1 = mono[Math.min(idx + 1, frameCount - 1)];
                    resampled[i] = (float) (s0 + (s1 - s0) * frac);
                }
                resamplePos += outLen * ratio;
                // 防止浮点累积越界
                if (resamplePos >= frameCount) resamplePos -= frameCount;
            }

            // 3. 喂给引擎（引擎内部会拷贝，这里直接传）
            if (resampled.length > 0) {
                AiAsrEngine.get().feedPcm(resampled);
            }
        } catch (Exception ignored) {
            // 采集失败不影响播放
        }
    }

    @Override
    public ByteBuffer getOutput() {
        ByteBuffer out = outputBuffer;
        outputBuffer = EMPTY_BUFFER;
        return out;
    }

    @Override
    public boolean isEnded() {
        return inputEnded && !outputBuffer.hasRemaining();
    }

    @Override
    public void flush() {
        outputBuffer = EMPTY_BUFFER;
        resamplePos = 0;
        inputEnded = false;
    }

    @Override
    public void queueEndOfStream() {
        inputEnded = true;
    }

    @Override
    public void reset() {
        flush();
        inputFormat = AudioFormat.NOT_SET;
    }
}
