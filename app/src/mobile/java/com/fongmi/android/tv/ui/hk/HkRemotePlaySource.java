package com.fongmi.android.tv.ui.hk;

import java.util.Objects;

/**
 * 照抄 8.83 RemotePlaySource（smali_classes2/ui/video/model/RemotePlaySource.smali）：
 * 字段 title/url/sort/isParseFinish/parseResult。字段名与原版一致，Gson 序列化出的
 * 世界直播批量投屏 JSON 与原版同构。
 * 1.4.16 修复（扫查中3）：compareTo 与 equals 口径对齐——equals/hashCode 比 sort+url，
 * compareTo 以 sort 为主序、sort 相同再比 url，使 compareTo==0 与 equals 等价
 * （原版 compareTo 在 sort 相同、url 不同时也返回 0，会被 TreeSet 判成同一条丢条）。
 */
public class HkRemotePlaySource implements Comparable<HkRemotePlaySource> {

    private String title;
    private String url;
    private int sort;
    private boolean isParseFinish;
    private String parseResult;

    public HkRemotePlaySource() {
    }

    public HkRemotePlaySource(String title, String url, int sort, boolean isParseFinish, String parseResult) {
        this.title = title;
        this.url = url;
        this.sort = sort;
        this.isParseFinish = isParseFinish;
        this.parseResult = parseResult;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getSort() {
        return sort;
    }

    public void setSort(int sort) {
        this.sort = sort;
    }

    public boolean isParseFinish() {
        return isParseFinish;
    }

    public void setParseFinish(boolean parseFinish) {
        isParseFinish = parseFinish;
    }

    public String getParseResult() {
        return parseResult;
    }

    public void setParseResult(String parseResult) {
        this.parseResult = parseResult;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HkRemotePlaySource other = (HkRemotePlaySource) o;
        return sort == other.sort && Objects.equals(url, other.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, sort);
    }

    @Override
    public int compareTo(HkRemotePlaySource other) {
        int bySort = Integer.compare(sort, other.sort);
        if (bySort != 0) return bySort;
        if (Objects.equals(url, other.url)) return 0;
        if (url == null) return -1;
        if (other.url == null) return 1;
        return url.compareTo(other.url);
    }
}
