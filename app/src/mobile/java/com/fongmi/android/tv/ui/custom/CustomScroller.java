package com.fongmi.android.tv.ui.custom;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Result;

public class CustomScroller extends RecyclerView.OnScrollListener {

    /** 提前触发下一页的阈值：距离底部还剩这么多个 item 时就开始加载 */
    private static final int PREFETCH_THRESHOLD = 10;

    private final Callback callback;
    private boolean loading;
    private boolean enable;
    private int page;

    public CustomScroller(Callback callback) {
        this.callback = callback;
        this.enable = true;
        this.page = 1;
    }

    @Override
    public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
        if (view.getScrollState() == RecyclerView.SCROLL_STATE_IDLE || dy <= 0) return;
        if (isNearBottom(view)) loadMore();
    }

    /**
     * 是否接近底部：最后一个可见 item 距离总数在阈值内时返回 true，
     * 不再等到绝对底部（canScrollVertically(1)==false）才触发。
     */
    private boolean isNearBottom(RecyclerView view) {
        RecyclerView.LayoutManager lm = view.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            LinearLayoutManager llm = (LinearLayoutManager) lm;
            int total = llm.getItemCount();
            if (total == 0) return false;
            int lastVisible = llm.findLastVisibleItemPosition();
            if (lastVisible == RecyclerView.NO_POSITION) return false;
            return total - lastVisible <= PREFETCH_THRESHOLD;
        }
        return !view.canScrollVertically(1);
    }

    public void checkMore(RecyclerView view) {
        if (view != null && view.canScrollVertically(1)) return;
        loadMore();
    }

    private void loadMore() {
        if (isDisable() || isLoading() || callback == null) return;
        if (callback.onLoadMore(String.valueOf(page + 1))) {
            page++;
            setLoading(true);
        }
    }

    public void reset() {
        loading = false;
        enable = true;
        page = 1;
    }

    public boolean first() {
        return page == 1;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public boolean isLoading() {
        return loading;
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
    }

    public boolean isDisable() {
        return !enable;
    }

    public void setEnable(int pageCount) {
        this.enable = page < pageCount || pageCount == 0;
    }

    public void endLoading(Result result) {
        if (result.getList().isEmpty()) page--;
        setEnable(result.getPageCount());
        setLoading(false);
    }

    public interface Callback {
        boolean onLoadMore(String page);
    }
}
