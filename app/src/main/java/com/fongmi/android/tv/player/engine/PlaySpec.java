package com.fongmi.android.tv.player.engine;

import android.net.Uri;

import androidx.media3.common.MediaMetadata;

import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.danmaku.live.LiveRoomResolver;
import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.UrlUtil;
import com.google.common.net.HttpHeaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaySpec {

    private Map<String, String> headers;
    private List<Danmaku> danmakus;
    private MediaMetadata metadata;
    private List<Sub> subs;
    private Result parseResult;
    private String format;
    private String key;
    private String detailVodId = "";
    private PlaybackRoute.Resolution playbackRoute = PlaybackRoute.resolve(null);
    private String playbackTraceId = PlaybackTrace.NONE;
    private String url;
    private Drm drm;
    private boolean parseSource;
    private boolean parseUseParse;
    /**
     * 海阔 PlayData 伴声音轨/歌词（环34，复核订正 B2 已接终端消费）：
     * from(Result) 时从 Result 携带，其他源恒空。消费面——hkAudioUrls 由
     * 交接面按线路下标收成一条（HkPlayCompanion.companionForLine），经
     * ExoUtil/MediaItemFactory 写进 MediaItem extras，MediaSourceFactory
     * 装配处 MergingMediaSource 合流（MPV 路 MpvPlayer audio-add）；hkLyric
     * 由 VideoActivity 经 HkLyricLoader 按官 loadLyric 值分形喂歌词位。
     * 两字段为空时全链恒无-op（见 census 环34 批A复核订正追记相碰清单）。
     */
    private List<String> hkAudioUrls;
    private String hkLyric;

    public static PlaySpec from(String key, String url, Map<String, String> headers, MediaMetadata metadata) {
        return new PlaySpec(key, url, headers, null, null, null, null, metadata);
    }

    public static PlaySpec from(Result result, String key, MediaMetadata metadata) {
        PlaySpec spec = new PlaySpec(key, result.getRealUrl(), result.getHeader(), result.getFormat(), result.getDrm(), result.getSubs(), result.getDanmaku(), metadata).setSource(result, false, false);
        spec.hkAudioUrls = result.getHkAudioUrls();
        spec.hkLyric = result.getHkLyric();
        return spec;
    }

    /** 海阔 PlayData 伴声音轨（只读消费面，未设为 null）。 */
    public List<String> getHkAudioUrls() {
        return hkAudioUrls;
    }

    /** 海阔 PlayData 歌词地址（未设为 null）。 */
    public String getHkLyric() {
        return hkLyric;
    }

    public static PlaySpec fromParse(Result result, String key, MediaMetadata metadata) {
        return fromParse(result, key, metadata, false);
    }

    public static PlaySpec fromParse(Result result, String key, MediaMetadata metadata, boolean useParse) {
        return new PlaySpec(key, null, null, result.getFormat(), result.getDrm(), result.getSubs(), result.getDanmaku(), metadata).setSource(result, true, useParse);
    }

    private PlaySpec(String key, String url, Map<String, String> headers, String format, Drm drm, List<Sub> subs, List<Danmaku> danmakus, MediaMetadata metadata) {
        this.key = key;
        setUrl(url);
        this.drm = drm;
        this.subs = subs;
        this.format = format;
        this.headers = headers;
        this.danmakus = danmakus;
        this.metadata = metadata;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
        this.playbackRoute = PlaybackRoute.resolve(url);
    }

    public Uri getUri() {
        return UrlUtil.uri(url);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public PlaySpec copyWithFormat(String format) {
        PlaySpec copy = new PlaySpec(key, url, headers, format, drm, subs, danmakus, metadata).setSource(parseResult, parseSource, parseUseParse);
        copy.playbackRoute = playbackRoute;
        copy.playbackTraceId = playbackTraceId;
        copy.detailVodId = detailVodId;
        copy.hkAudioUrls = hkAudioUrls;
        copy.hkLyric = hkLyric;
        return copy;
    }

    /** 详情 vod id（详情页起播装配时写入）：直播平台弹幕识别的真 id 所在，play 结果的 Vod 恒空、靠不住。 */
    public String getDetailVodId() {
        return detailVodId;
    }

    public PlaySpec setDetailVodId(String detailVodId) {
        this.detailVodId = detailVodId == null ? "" : detailVodId;
        return this;
    }

    /**
     * 直播平台弹幕识别快照（起播装配点旁路用）：vodId 优先取详情 vod id，
     * 缺失才退 parseResult 的 Vod（play 结果不带 list 时它恒为空 Vod、id 恒空，
     * 只靠它识别必然落空——见 douyu-live-danmaku-noshow-trace.md）。
     * 其余字段口径与原 PlayerManager 组快照一致，不动 Resolver 的强信号纪律。
     */
    public LiveRoomResolver.Snapshot liveDanmakuSnapshot() {
        Vod vod = parseResult == null ? null : parseResult.getVod();
        MediaMetadata md = metadata;
        String title = md == null || md.title == null ? "" : md.title.toString();
        String id = !detailVodId.isEmpty() ? detailVodId : (vod == null ? "" : vod.getId());
        return new LiveRoomResolver.Snapshot(
                key,
                id,
                vod == null ? "" : vod.getPlayUrl(),
                vod == null ? "" : vod.getPlayFrom(),
                vod == null ? title : vod.getName(),
                url);
    }

    public PlaybackRoute.Resolution getPlaybackRoute() {
        return playbackRoute;
    }

    public void refreshPlaybackRoute() {
        playbackRoute = PlaybackRoute.resolve(url);
    }

    public String getPlaybackTraceId() {
        return PlaybackTrace.normalize(playbackTraceId);
    }

    public void setPlaybackTraceId(String playbackTraceId) {
        this.playbackTraceId = PlaybackTrace.normalize(playbackTraceId);
    }

    public Drm getDrm() {
        return drm;
    }

    public List<Sub> getSubs() {
        return subs;
    }

    public List<Danmaku> getDanmakus() {
        return danmakus;
    }

    public MediaMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(MediaMetadata metadata) {
        this.metadata = metadata;
    }

    public Result getParseResult() {
        return parseResult;
    }

    public boolean isParseUseParse() {
        return parseUseParse;
    }

    public boolean isParseSource() {
        return parseSource;
    }

    public boolean canReparse() {
        return parseResult != null;
    }

    private PlaySpec setSource(Result result, boolean parseSource, boolean useParse) {
        this.parseResult = result;
        this.parseSource = parseSource;
        this.parseUseParse = useParse;
        return this;
    }

    public PlaySpec checkUa() {
        if (headers == null) headers = new HashMap<>();
        if (headers.keySet().stream().noneMatch(HttpHeaders.USER_AGENT::equalsIgnoreCase)) headers.put(HttpHeaders.USER_AGENT, Setting.getUa().isEmpty() ? PlayerHelper.getDefaultUa() : Setting.getUa());
        return this;
    }

    public void setSub(Sub sub) {
        if (subs == null) subs = new ArrayList<>();
        if (sub != null && !subs.contains(sub)) subs.add(0, sub);
    }

    public void setDanmaku(Danmaku item) {
        if (danmakus == null) danmakus = new ArrayList<>();
        if (!item.isEmpty() && !danmakus.contains(item)) danmakus.add(0, item);
        danmakus.forEach(danmaku -> danmaku.setSelected(danmaku.getUrl().equals(item.getUrl())));
    }

    public void addDanmaku(Danmaku item) {
        if (danmakus == null) danmakus = new ArrayList<>();
        if (!item.isEmpty() && !danmakus.contains(item)) danmakus.add(item);
    }
}
