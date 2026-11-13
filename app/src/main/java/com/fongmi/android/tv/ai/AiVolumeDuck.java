package com.fongmi.android.tv.ai;

import android.content.Context;
import android.media.AudioManager;

/**
 * AI 对话压音（2026-10-13 定案：AI 对话时视频声音变小、说完恢复）。
 * 走系统 STREAM_MUSIC 音量，与 AI 音量口令（VideoActivity.aiVolumeUp/Down）同一条通道：
 * 进入对话时把当前音量存下并压到两成，退出时原样恢复。
 * 引用计数：悬浮球录音与对话弹窗可能叠开，只在最外层进/出时真正改音量。
 * 没有任何声音在播（isMusicActive 为假）时不动音量，避免无视频场景下误改用户音量。
 */
public final class AiVolumeDuck {

    private static int depth;
    private static int savedVolume = -1;

    private AiVolumeDuck() {
    }

    public static synchronized void duck(Context context) {
        if (depth++ > 0) return;
        savedVolume = -1;
        try {
            AudioManager am = audioManager(context);
            if (am == null || !am.isMusicActive()) return;
            int current = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int target = Math.max(0, Math.round(current * 0.2f));
            if (target == current) return;
            savedVolume = current;
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
        } catch (Exception ignored) {
        }
    }

    public static synchronized void restore(Context context) {
        if (depth == 0) return;
        if (--depth > 0) return;
        int restore = savedVolume;
        savedVolume = -1;
        if (restore < 0) return;
        try {
            AudioManager am = audioManager(context);
            if (am != null) am.setStreamVolume(AudioManager.STREAM_MUSIC, restore, 0);
        } catch (Exception ignored) {
        }
    }

    private static AudioManager audioManager(Context context) {
        Context base = context == null ? null : context.getApplicationContext();
        if (base == null) base = context;
        if (base == null) base = com.fongmi.android.tv.App.get();
        if (base == null) return null;
        Object service = base.getSystemService(Context.AUDIO_SERVICE);
        return service instanceof AudioManager ? (AudioManager) service : null;
    }
}
