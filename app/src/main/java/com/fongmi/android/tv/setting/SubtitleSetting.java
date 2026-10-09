package com.fongmi.android.tv.setting;

import android.graphics.Color;

import com.github.catvod.utils.Prefers;

/**
 * 星辰字幕设置后端：字幕设置的唯一真实来源（default SharedPreferences）。
 * 设置页只读写这里；播放器在 setPlayerView / applySubtitleStyle / 字幕延迟应用时从这里取值。
 * 字号/位置会同步写入 PlayerSetting，保证 Exo 与 MPV 的现有应用路径生效。
 */
public class SubtitleSetting {

    public static boolean isEnabled() {
        return Prefers.getBoolean("subtitle_enabled", true);
    }

    public static void putEnabled(boolean value) {
        Prefers.put("subtitle_enabled", value);
    }

    public static String getSizeMode() {
        return Prefers.getString("subtitle_size_mode", "medium");
    }

    public static void putSizeMode(String mode) {
        if (mode == null) mode = "medium";
        Prefers.put("subtitle_size_mode", mode);
        PlayerSetting.putSubtitleTextSize(toTextSize(mode));
    }

    public static float toTextSize(String mode) {
        if ("small".equals(mode)) return 0.04f;
        if ("large".equals(mode)) return 0.07f;
        return 0.0533f;
    }

    public static boolean hasCustomColor() {
        return Prefers.getBoolean("subtitle_color_custom", false);
    }

    public static int getColor() {
        return Prefers.getInt("subtitle_color", Color.WHITE);
    }

    public static void putColor(int color) {
        Prefers.put("subtitle_color_custom", true);
        Prefers.put("subtitle_color", color);
    }

    public static String getPositionMode() {
        return Prefers.getString("subtitle_pos_mode", "bottom");
    }

    public static void putPositionMode(String mode) {
        if (mode == null) mode = "bottom";
        Prefers.put("subtitle_pos_mode", mode);
        PlayerSetting.putSubtitlePosition(toPosition(mode));
    }

    public static float toPosition(String mode) {
        if ("top".equals(mode)) return 1.0f;
        if ("middle".equals(mode)) return 0.5f;
        return 0.0f;
    }

    public static long getDelayMs() {
        return Prefers.getLong("subtitle_delay_ms", 0L);
    }

    public static void putDelayMs(long ms) {
        Prefers.put("subtitle_delay_ms", ms);
    }

    public static boolean isAutoMatch() {
        return Prefers.getBoolean("subtitle_automatch", true);
    }

    public static void putAutoMatch(boolean value) {
        Prefers.put("subtitle_automatch", value);
    }

    public static String getLang() {
        return Prefers.getString("subtitle_lang", "cn_first");
    }

    public static void putLang(String lang) {
        Prefers.put("subtitle_lang", lang == null ? "cn_first" : lang);
    }

    public static boolean isSrcEnabled(String src) {
        return Prefers.getBoolean("subtitle_src_" + src, true);
    }

    public static void putSrcEnabled(String src, boolean value) {
        Prefers.put("subtitle_src_" + src, value);
    }

    public static String getOpenSubtitlesKey() {
        return Prefers.getString("subtitle_opensubtitles_key", "");
    }

    public static void putOpenSubtitlesKey(String key) {
        Prefers.put("subtitle_opensubtitles_key", key == null ? "" : key.trim());
    }
}
