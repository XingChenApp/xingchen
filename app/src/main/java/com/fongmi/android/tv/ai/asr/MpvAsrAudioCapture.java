package com.fongmi.android.tv.ai.asr;

import android.content.Context;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * MPV 播放器的 AI 本地字幕音频采集器。
 *
 * 背景：
 * - ExoPlayer 侧通过 Media3 AudioProcessor（AiAsrAudioProcessor）直接从音频链路旁路采集 PCM。
 * - MPV 侧由 libmpv 在 native 层经 JNI 自己创建 AudioTrack 输出音频，Java 层的 MPVLib
 *   wrapper 没有暴露任何音频帧回调，预编译的 libmpv .so 也无法加钩子。
 * - Android 的 Visualizer 需要音频 sessionId（拿不到）、AudioPlaybackCapture 需要
 *   MediaProjection 录屏授权（太重）、REMOTE_SUBMIX 需要系统签名权限——此路皆不通。
 *
 * 方案（影子播放器）：
 * - 起一个静音、仅音频的 ExoPlayer，用同样的 URL + headers 播放同一路音频流；
 * - 它的音频链注入独立的 AiAsrAudioProcessor，旁路采集 16kHz 单声道 PCM，
 *   喂给同一个 AiAsrEngine——识别、显示逻辑与 ExoPlayer 路径完全复用；
 * - 通过 MPV 的 time-pos（VideoActivity 每秒 onTimeChanged 同步）做漂移校正：
 *   位置差 > 2.5s 则 seek 影子播放器；播放/暂停状态镜像。
 *
 * 代价：多一路纯音频的网络流量（音频码率占比很小）与软解/硬解音频解码开销。
 * 所有失败都静默处理，绝不影响 MPV 主播放。
 */
public class MpvAsrAudioCapture {

    /** 位置漂移阈值：超过则 seek 影子播放器追上 MPV */
    private static final long DRIFT_THRESHOLD_MS = 2500;

    private ExoPlayer shadowPlayer;
    private AiAsrAudioProcessor processor;
    private volatile boolean started = false;

    /**
     * 启动影子采集。
     *
     * @param context    任意 Context（内部取 ApplicationContext）
     * @param url        与 MPV 相同的播放地址
     * @param headers    与 MPV 相同的请求头（可空）
     * @param positionMs MPV 当前播放位置，用于影子播放器初始 seek
     * @return true=影子播放器已启动
     */
    public boolean start(Context context, String url, Map<String, String> headers, long positionMs) {
        try {
            if (started) return true;
            if (context == null || url == null || url.isEmpty()) return false;
            Context appContext = context.getApplicationContext();

            processor = new AiAsrAudioProcessor();

            // 音频链只挂我们的采集器（passthrough，不影响影子播放器自身）
            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(appContext) {
                @Override
                protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
                    return new DefaultAudioSink.Builder(context)
                            .setAudioProcessors(new androidx.media3.common.audio.AudioProcessor[]{processor})
                            .build();
                }
            };

            // 请求头：MPV 与 Exo 走各自网络栈，影子播放器需要同样的鉴权头
            DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory();
            if (headers != null && !headers.isEmpty()) {
                Map<String, String> safe = new HashMap<>();
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) safe.put(e.getKey(), e.getValue());
                }
                if (!safe.isEmpty()) httpFactory.setDefaultRequestProperties(safe);
            }
            DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(appContext, httpFactory);

            shadowPlayer = new ExoPlayer.Builder(appContext, renderersFactory)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                    // 不抢音频焦点，避免影响 MPV 播放或其他应用
                    .setAudioAttributes(AudioAttributes.DEFAULT, false)
                    .build();
            // 静音 + 仅音频：不创建 surface，视频轨道直接禁用
            shadowPlayer.setVolume(0f);
            shadowPlayer.setTrackSelectionParameters(
                    shadowPlayer.getTrackSelectionParameters().buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                            .build());
            shadowPlayer.setMediaItem(new MediaItem.Builder().setUri(url).build());
            shadowPlayer.prepare();
            if (positionMs > 0) shadowPlayer.seekTo(positionMs);

            processor.setCaptureEnabled(true);
            shadowPlayer.play();
            started = true;
            return true;
        } catch (Throwable t) {
            stop();
            return false;
        }
    }

    /**
     * 与 MPV 主播放同步（VideoActivity 每秒调用一次）。
     *
     * @param mpvPositionMs MPV 当前位置
     * @param mpvPlaying    MPV 是否正在播放
     */
    public void sync(long mpvPositionMs, boolean mpvPlaying) {
        try {
            if (!started || shadowPlayer == null) return;
            // 镜像播放/暂停状态
            if (mpvPlaying && !shadowPlayer.isPlaying()) {
                shadowPlayer.play();
            } else if (!mpvPlaying && shadowPlayer.isPlaying()) {
                shadowPlayer.pause();
            }
            // 漂移校正
            if (mpvPositionMs > 0) {
                long shadowPos = shadowPlayer.getCurrentPosition();
                if (Math.abs(shadowPos - mpvPositionMs) > DRIFT_THRESHOLD_MS) {
                    shadowPlayer.seekTo(mpvPositionMs);
                }
            }
        } catch (Throwable ignored) {
            // 同步失败不影响主播放
        }
    }

    /** 停止并释放影子播放器 */
    public void stop() {
        try {
            if (processor != null) processor.setCaptureEnabled(false);
        } catch (Throwable ignored) {}
        try {
            if (shadowPlayer != null) {
                shadowPlayer.stop();
                shadowPlayer.release();
            }
        } catch (Throwable ignored) {}
        shadowPlayer = null;
        processor = null;
        started = false;
    }

    public boolean isStarted() {
        return started;
    }
}
