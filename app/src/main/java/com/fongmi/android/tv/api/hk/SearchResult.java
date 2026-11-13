package com.fongmi.android.tv.api.hk;

/**
 * 搜索结果模型。
 * <p>
 * 一比一对照 8.83 反编译原文：
 * {@code smali_classes2/com/example/hikerview/ui/home/model/SearchResult.smali}。
 * 12 个实例字段名/类型/顺序与原版一致；两个构造方法（无参默认 page=0、
 * (title, desc)）与原版一致；全部 getter/setter 与原版一致。
 * <p>
 * 原版继承 {@code BaseResultItem}（带 {@code extra}/{@code extraObj}/
 * {@code getBaseExtra()}/{@code requireExtra()}，依赖 fastjson 注解、
 * Timber 和 8.83 的 BaseExtra 体系）。搜索链路实际只用到 {@code extra}
 *（JSEngine.callbackSearchResult 里 setExtra），故此处直接内联 {@code extra}
 * 字段；{@code extraObj}/{@code getBaseExtra}/{@code requireExtra} 未移植。
 */
public class SearchResult {

    private String content;
    private String desc;
    private String descMore;
    private int fromMovieInfo;
    private String img;
    private int page;
    private String size;
    private String time;
    private String title;
    private String type;
    private String url;
    private String url2;
    /** 原版 BaseResultItem.extra：搜索链路 setExtra 实际写入。 */
    private String extra;

    public SearchResult() {
        this.page = 0;
    }

    public SearchResult(String title, String desc) {
        this.page = 0;
        this.title = title;
        this.desc = desc;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getDescMore() {
        return descMore;
    }

    public void setDescMore(String descMore) {
        this.descMore = descMore;
    }

    public int getFromMovieInfo() {
        return fromMovieInfo;
    }

    public void setFromMovieInfo(int fromMovieInfo) {
        this.fromMovieInfo = fromMovieInfo;
    }

    public String getImg() {
        return img;
    }

    public void setImg(String img) {
        this.img = img;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl2() {
        return url2;
    }

    public void setUrl2(String url2) {
        this.url2 = url2;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }
}
