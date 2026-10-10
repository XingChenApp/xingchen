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
    /** copy:// 条目：复制按钮（name 为按钮文案，text 为待复制文本）。 */
    private final List<CopyItem> copyItems = new ArrayList<>();
    /**
     * 直接播放标记：条目 URL 自带 @lazyRule= 且求值结果含 #isVideo=true#
     * 时设置。调用方见到非空应跳过 V4，直接走播放链（HkRouter.play 解析）。
     * 注意 isEmpty() 只看 lines，检查本字段必须在 isEmpty() 之前。
     */
    private String directPlayUrl = "";

    public static class CopyItem {
        private final String name;
        private final String text;

        public CopyItem(String name, String text) {
            this.name = name == null || name.isEmpty() ? "复制" : name;
            this.text = text == null ? "" : text;
        }

        public String getName() {
            return name;
        }

        public String getText() {
            return text;
        }
    }

    public void addCopyItem(String name, String text) {
        if (text == null || text.isEmpty()) return;
        copyItems.add(new CopyItem(name, text));
    }

    public List<CopyItem> getCopyItems() {
        return copyItems;
    }

    public String getDirectPlayUrl() {
        return directPlayUrl;
    }

    public void setDirectPlayUrl(String directPlayUrl) {
        if (directPlayUrl != null) this.directPlayUrl = directPlayUrl;
    }

    /**
     * dealWithUrl 分流种类（官方 ArticleListFragment.dealWithUrl）：
     * video=直接播放 / pics=漫画图片 / x5=webview规则 / web=网页 / image=图片查看 / magnet=磁力分享 / ""=未知。
     * 非空时调用方不应进 V4，按种类分流。
     */
    private String dealKind = "";
    private String dealUrl = "";

    public String getDealKind() {
        return dealKind;
    }

    public void setDealKind(String dealKind) {
        if (dealKind != null) this.dealKind = dealKind;
    }

    public String getDealUrl() {
        return dealUrl;
    }

    public void setDealUrl(String dealUrl) {
        if (dealUrl != null) this.dealUrl = dealUrl;
    }

    /**
     * 分类切换标记：条目 URL 的 @lazyRule= 求值时调了 refreshPage（官方语义 =
     * putMyVar 设分类变量后刷新列表，如探色 Cate tab、粉嫩小BB 分类行）。
     * 为 true 时调用方不应进 V4，而应重刷当前列表（对官方 OnRefreshPageEvent 的等价实现）。
     */
    private boolean tabSwitch;

    public boolean isTabSwitch() {
        return tabSwitch;
    }

    public void setTabSwitch(boolean tabSwitch) {
        this.tabSwitch = tabSwitch;
    }

    /**
     * lazyRule 解析失败标记：条目 URL 自带 @lazyRule=（期望直接播放），但求值
     * 返回 null/空或抛错时设置。调用方见到此标记不应推空 V4，而应直接报错，
     * 避免"0条线路 · 共0集"的空详情页。
     */
    private boolean lazyParseFailed;

    public boolean isLazyParseFailed() {
        return lazyParseFailed;
    }

    public void setLazyParseFailed(boolean lazyParseFailed) {
        this.lazyParseFailed = lazyParseFailed;
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
        if (content != null && !content.isEmpty()) this.content = cleanContent(content);
    }

    /** 追加一段简介/文本（多条 long_text/text_1 时换行拼接，而非覆盖）。 */
    public void appendContent(String c) {
        if (c == null || c.isEmpty()) return;
        c = cleanContent(c);
        if (c.isEmpty()) return;
        this.content = this.content.isEmpty() ? c : this.content + "\n" + c;
    }

    /**
     * 简介导航文字过滤：规则详情解析常把页面导航（如"首页 日韩AV 国产系列 欧美 目录"）
     * 带进简介。启发式：找到"首页"后若紧跟一串短词并以"目录/导航/分类"收尾，则整段视为导航删除；
     * 兜底去掉孤立的"首页"/"目录"等导航词。
     */
    public static String cleanContent(String c) {
        if (c == null) return "";
        String s = c.trim();
        if (s.isEmpty()) return s;
        // "首页 ... 目录/导航/分类"：中间为 1~15 个短词（每词 ≤8 字）则整段删除
        s = s.replaceAll("首页(\\s+\\S{1,8}){1,15}?\\s+(目录|导航|分类)(?=\\s|$)", " ");
        // 残留的孤立导航词（保留前导空白/行首）
        s = s.replaceAll("(^|\\s)(首页|目录|导航)(?=\\s|$)", "$1");
        // 连续空白归一
        s = s.replaceAll("\\s{2,}", " ").trim();
        return s;
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

    /** 是否有基本信息（标题/封面/简介），无线路时也可用它渲染 V4 而不是"加载失败"。 */
    public boolean hasBasicInfo() {
        return (title != null && !title.isEmpty())
                || (pic != null && !pic.isEmpty())
                || (content != null && !content.isEmpty());
    }
}
