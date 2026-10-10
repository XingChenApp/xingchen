package com.fongmi.android.tv.api.hk;

import java.util.HashMap;
import java.util.Map;

/**
 * 海阔播放解析结果（M4）。
 *
 * <p>由 {@link HkRouter#play(String)} 的分流链产出：真播放地址 + 随地址的请求头
 * （如 {@code ;{Cookie@..&&Referer@..}} 拆出来的）+ 可选字幕地址。</p>
 */
public class HkPlay {

    private String url = "";
    private final Map<String, String> headers = new HashMap<>();
    private String subtitle = "";
    /** P1：#isM3u8# 强制按 m3u8 处理。 */
    private boolean forceM3u8;
    /** P1：#concat# 多段地址拼接播放（url 里以 && 或换行分隔的多段）。 */
    private boolean concat;
    /** P1：#fastPlayMode# / #threads=N# 极速模式标记（透传给播放器）。 */
    private boolean fastPlayMode;
    private int threads;
    /** P1：{urls:[], audioUrls:[]} 音视频分离时的音频地址。 */
    private String audioUrl = "";
    /** P1：pics:// 漫画多图模式的图片列表（非视频，UI 提示不支持）。 */
    private final java.util.List<String> pics = new java.util.ArrayList<>();
    /** P1：ed2k/magnet/thunder/ftp 等特殊协议类型（透传给播放器前识别）。 */
    private String playType = "";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url == null ? "" : url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void putHeader(String key, String value) {
        if (key != null && value != null) headers.put(key, value);
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle == null ? "" : subtitle;
    }

    public boolean isForceM3u8() {
        return forceM3u8;
    }

    public void setForceM3u8(boolean forceM3u8) {
        this.forceM3u8 = forceM3u8;
    }

    public boolean isConcat() {
        return concat;
    }

    public void setConcat(boolean concat) {
        this.concat = concat;
    }

    public boolean isFastPlayMode() {
        return fastPlayMode;
    }

    public void setFastPlayMode(boolean fastPlayMode) {
        this.fastPlayMode = fastPlayMode;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public String getAudioUrl() {
        return audioUrl == null ? "" : audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl == null ? "" : audioUrl;
    }

    public java.util.List<String> getPics() {
        return pics;
    }

    public void addPic(String pic) {
        if (pic != null && !pic.isEmpty()) pics.add(pic);
    }

    public String getPlayType() {
        return playType == null ? "" : playType;
    }

    public void setPlayType(String playType) {
        this.playType = playType == null ? "" : playType;
    }

    public boolean isEmpty() {
        return (url == null || url.isEmpty()) && pics.isEmpty();
    }
}
