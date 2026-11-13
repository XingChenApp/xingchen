package com.fongmi.android.tv.api.hk.event.rule;

/**
 * 8.83 com.example.hikerview.ui.home.model.CommonReadPageData 的等价实现——原版 refreshReadData
 * 把入参对象 parseObject 成这个 bean 后「把 bean 本身当事件 post」（JSEngine smali 22151，
 * 无单独事件类）。字段照 smali 抠实，仅四个 String：currentUrl、id、nextPage、title。
 * 消费侧：HkArticleFragment.onRefreshReadData（原版 ArticleListFragment.refreshReadData）。
 */
public class RefreshReadDataEvent {

    private String currentUrl;
    private String id;
    private String nextPage;
    private String title;

    public RefreshReadDataEvent() {
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public void setCurrentUrl(String currentUrl) {
        this.currentUrl = currentUrl;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNextPage() {
        return nextPage;
    }

    public void setNextPage(String nextPage) {
        this.nextPage = nextPage;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
