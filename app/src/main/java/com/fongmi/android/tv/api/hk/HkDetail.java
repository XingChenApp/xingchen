package com.fongmi.android.tv.api.hk;

import java.util.ArrayList;
import java.util.List;

/**
 * 海阔详情页数据模型（M3，设计文档 §5）。
 * title/pic/content 来自条目回退 + 详情解析；lines 为线路/选集。
 */
public class HkDetail {

    public static class Episode {
        private final String name;
        private final String url;

        public Episode(String name, String url) {
            this.name = name == null ? "" : name;
            this.url = url == null ? "" : url;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }
    }

    public static class Line {
        private final String name;
        private final List<Episode> episodes = new ArrayList<>();

        public Line(String name) {
            this.name = name == null || name.isEmpty() ? "默认" : name;
        }

        public String getName() {
            return name;
        }

        public List<Episode> getEpisodes() {
            return episodes;
        }

        public void addEpisode(String name, String url) {
            if (url == null || url.isEmpty() || "javascript:;".equalsIgnoreCase(url.trim())) return;
            episodes.add(new Episode(name == null || name.isEmpty() ? ("第" + (episodes.size() + 1) + "集") : name, url));
        }
    }

    private String title = "";
    private String pic = "";
    private String content = "";
    private final List<Line> lines = new ArrayList<>();
    /**
     * 直接播放标记：条目 URL 自带 @lazyRule= 且求值结果含 #isVideo=true#
     * 时设置。调用方见到非空应跳过 V4，直接走播放链（HkRouter.play 解析）。
     * 注意 isEmpty() 只看 lines，检查本字段必须在 isEmpty() 之前。
     */
    private String directPlayUrl = "";

    public String getDirectPlayUrl() {
        return directPlayUrl;
    }

    public void setDirectPlayUrl(String directPlayUrl) {
        if (directPlayUrl != null) this.directPlayUrl = directPlayUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title != null && !title.isEmpty()) this.title = title;
    }

    public String getPic() {
        return pic;
    }

    public void setPic(String pic) {
        if (pic != null && !pic.isEmpty()) this.pic = pic;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        if (content != null && !content.isEmpty()) this.content = content;
    }

    public List<Line> getLines() {
        return lines;
    }

    public Line getOrCreateLine(String name) {
        String n = name == null || name.isEmpty() ? "默认" : name;
        for (Line line : lines) if (line.getName().equals(n)) return line;
        Line line = new Line(n);
        lines.add(line);
        return line;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
