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

    public boolean isEmpty() {
        return url == null || url.isEmpty();
    }
}
