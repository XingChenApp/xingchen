package com.fongmi.android.tv.player.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 环34 复核订正 B2：海阔 PlayData 伴随面（audioUrls / lyric）的终端消费
 * 纯逻辑口——判定与分形在此一处，装配点（ExoUtil / MediaItemFactory /
 * MediaSourceFactory）与歌词接入口（VideoActivity 经 HkLyricLoader）只读
 * 本口，不各自再判。
 *
 * <p>相碰口径：本口的输入只有 PlaySpec.hkAudioUrls / hkLyric 两字段，两者
 * 只由海阔面写（SiteApi.hkPlayerContent ← HkPlay/HkPlayData），其他源
 * （点播 py/js、磁力、115、直播）的 Result 从不写这两字段、恒为 null。
 * 本口对 null/空输入恒回「无伴随」结论，装配点据此走原路一字不变。
 */
public final class HkPlayCompanion {

    /** MediaItem.requestMetadata.extras 里伴声音轨表的键（装配点之间共用一口）。 */
    public static final String EXTRA_AUDIO_URLS = "hk_audio_urls";

    private HkPlayCompanion() {
    }

    /** 伴声音轨表（官 setAudioUrls 的整表语义）：滤 null/空串、保序，其余原样。 */
    public static List<String> companionAudioUrls(List<String> audioUrls) {
        List<String> out = new ArrayList<>();
        if (audioUrls == null) return out;
        for (String u : audioUrls) {
            if (u != null && !u.trim().isEmpty()) out.add(u);
        }
        return out;
    }

    /** 是否有伴声音轨要合流（空/null 恒 false＝装配无-op 判据）。 */
    public static boolean hasCompanionAudio(List<String> audioUrls) {
        return !companionAudioUrls(audioUrls).isEmpty();
    }

    /**
     * 按线路下标取本线伴随音轨（官消费形态亲读：audioUrls 与 urls 按下标
     * 平行配对——DLNA 写 DlanUrlDTO 时取 audioUrls[switchIndex]，下标越界
     * 回首条，见 VideoPlayerActivity lambda$startWebDlan$91 / refreshDLNA；
     * 播放器 setAudioUrls 整表＋setPlayUri(switchIndex) 同配对）。咱家
     * hkPlayerContent 已按 line 选定主址，交接面据此只把本线一条伴随址
     * 入 PlaySpec，装配处再合流。回表恒 0 或 1 条。
     */
    public static List<String> companionForLine(List<String> audioUrls, int line) {
        List<String> all = companionAudioUrls(audioUrls);
        List<String> out = new ArrayList<>();
        if (all.isEmpty()) return out;
        out.add(line >= 0 && line < all.size() ? all.get(line) : all.get(0));
        return out;
    }

    /** 官 loadLyric（VideoPlayerActivity.loadLyric 亲读）的值分形。 */
    public enum LyricKind {
        /** 空值：官清歌词面（loadLyric(空表)）。 */
        EMPTY,
        /** hiker:// / file:// / "/" 开头：官读文件文本作歌词（lambda$loadLyric$57）。 */
        LOCAL_FILE,
        /** http:// / https://：官带 PlayData 请求头取文本作歌词（lambda$loadLyric$59）。 */
        REMOTE,
        /** 其余：值本身就是歌词文本，官直喂 LyricViewX.loadLyric(值, null)。 */
        INLINE_TEXT
    }

    /** lyric 值分形判定（官支序：本地支先于 http 支，余者为文本）。 */
    public static LyricKind classifyLyric(String lyric) {
        if (lyric == null || lyric.isEmpty()) return LyricKind.EMPTY;
        if (lyric.startsWith("hiker://") || lyric.startsWith("file://") || lyric.startsWith("/")) {
            return LyricKind.LOCAL_FILE;
        }
        if (lyric.startsWith("http://") || lyric.startsWith("https://")) return LyricKind.REMOTE;
        return LyricKind.INLINE_TEXT;
    }
}
