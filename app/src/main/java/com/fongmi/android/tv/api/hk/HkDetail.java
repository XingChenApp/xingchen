package com.fongmi.android.tv.api.hk;

import java.util.ArrayList;
import java.util.List;

/**
 * 海阔详情页数据模型。
 * 8.83 原版：详情页直接渲染规则返回的原始条目（rawItems），不做线路/选集分类。
 * title/pic/content 来自点击条目回退；动作标记（directPlayUrl/dealKind/tabSwitch
 * /textResult/lazyParseFailed）供调用方分流。
 */
public class HkDetail {

    private String title = "";
    private String pic = "";
    private String content = "";
    /**
     * 直接播放标记：条目 URL 自带 @lazyRule= 且求值结果含 #isVideo=true#
     * 时设置。调用方见到非空应跳过 V4，直接走播放链（HkRouter.play 解析）。
     */
    private String directPlayUrl = "";

    /**
     * 8.83 原版：详情页直接渲染规则返回的原始条目，不做线路/选集分类。
     * 明细条目由调用方丢进通用 ContentAdapter 按 col_type 渲染。
     */
    private List<HkDetailItem> rawItems = new ArrayList<>();

    /** 详情页自身 URL（HkRouter 取数时写入）：选集分拣剔自链、提取本片编号用。 */
    private String pageUrl = "";

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        if (pageUrl != null && !pageUrl.isEmpty()) this.pageUrl = pageUrl;
    }

    public List<HkDetailItem> getRawItems() {
        return rawItems;
    }

    public void setRawItems(List<HkDetailItem> rawItems) {
        this.rawItems = rawItems == null ? new ArrayList<>() : rawItems;
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

    /**
     * 纯文本结果：文本型条目（按钮/文本行）的 js: 求值返回非 URL 文本时设置。
     * 调用方见到非空应弹窗显示文本，不进 V4/播放（官方文本按钮语义）。
     */
    private String textResult = "";

    public String getTextResult() {
        return textResult;
    }

    public void setTextResult(String textResult) {
        if (textResult != null) this.textResult = textResult;
    }

    /**
     * 动作 URL：@lazyRule= 求值返回 toast://、copy:// 等动作 URL 时设置。
     * 调用方见到非空应走 handleActionUrl 处理，不进 V4/播放。
     * （8.83 StringUtil 动作 URL 表同款；星辰旧逻辑误判为直接播放地址导致进播放器）
     */
    private String actionUrl = "";

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        if (actionUrl != null) this.actionUrl = actionUrl;
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

    public boolean isEmpty() {
        // 8.83 原版：无原始条目且无基本信息时为空
        return rawItems.isEmpty() && !hasBasicInfo();
    }

    /** 是否有基本信息（标题/封面/简介），无线路时也可用它渲染 V4 而不是"加载失败"。 */
    public boolean hasBasicInfo() {
        return (title != null && !title.isEmpty())
                || (pic != null && !pic.isEmpty())
                || (content != null && !content.isEmpty());
    }
}
