package com.fongmi.android.tv.api.hk;

import java.util.List;
import java.util.Map;

/**
 * 直接移植自海阔 8.83 {@code com.example.hikerview.ui.video.model.PlayData}（do not rewrite）。
 * <p>
 * 9 字段全集（PlayData.smali 逐字段）：url/urls/names/header/headers/audioUrls/
 * danmu/lyric/subtitle。旧形只 5 字段，附带四字段在 Gson 直填时无处落（G49）。
 */
public class HkPlayData {

    private String url;
    private List<String> urls;
    private List<String> names;
    private Map<String, String> header;
    private List<Map<String, String>> headers;
    private List<String> audioUrls;
    private String danmu;
    private String lyric;
    private String subtitle;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
    }

    public List<String> getNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }

    public Map<String, String> getHeader() {
        return header;
    }

    public void setHeader(Map<String, String> header) {
        this.header = header;
    }

    public List<Map<String, String>> getHeaders() {
        return headers;
    }

    public void setHeaders(List<Map<String, String>> headers) {
        this.headers = headers;
    }

    public List<String> getAudioUrls() {
        return audioUrls;
    }

    public void setAudioUrls(List<String> audioUrls) {
        this.audioUrls = audioUrls;
    }

    public String getDanmu() {
        return danmu;
    }

    public void setDanmu(String danmu) {
        this.danmu = danmu;
    }

    public String getLyric() {
        return lyric;
    }

    public void setLyric(String lyric) {
        this.lyric = lyric;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }
}
