package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.HongguoCommentApi;
import com.fongmi.android.tv.databinding.DialogHongguoCommentBinding;
import com.fongmi.android.tv.ui.adapter.HongguoCommentAdapter;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;

/**
 * 红果评论面板（壳子直抓，只读）。
 * 形态两套：竖屏播放页/详情页＝底部拉起半屏（BaseBottomSheetDialog 既有口径）；
 * 横屏播放页＝右侧划出（占右半宽、整高，视频在左侧继续播，点视频区即窗口外点击自动收回，
 * 在面板上向右划过阈值也收回）。
 * 双页签「剧评／本集评论」，游标翻页与回复展开的纪律全在 HongguoCommentApi 内，
 * 本类只管分页状态与渲染；抓取失败只在面板内给一行可重试提示，不外溢到播放本体。
 */
public class HongguoCommentDialog extends BaseBottomSheetDialog implements HongguoCommentAdapter.Listener {

    public static final String TAG = "HongguoCommentDialog";

    private static final int TAB_SERIES = 0;
    private static final int TAB_EPISODE = 1;

    /** 一个页签的分页状态：游标只许按服务端回传原样推进（纪律在 Api 侧校验，本类只保存）。 */
    private static class TabState {
        final List<HongguoCommentApi.Comment> items = new ArrayList<>();
        String cursor = "";
        boolean hasMore;
        boolean loaded;
        boolean loading;
        boolean failed;
        long total;

        void reset() {
            items.clear();
            cursor = "";
            hasMore = false;
            loaded = false;
            loading = false;
            failed = false;
            total = 0;
        }
    }

    private DialogHongguoCommentBinding binding;
    private HongguoCommentAdapter adapter;
    private ExecutorService executor;

    private String seriesId = "";
    private String vid = "";
    private boolean rightSide;
    private HongguoCommentApi.Page cachedSeriesPage;
    private HongguoCommentApi.Page cachedEpisodePage;

    private final TabState seriesState = new TabState();
    private final TabState episodeState = new TabState();
    private final Map<String, HongguoCommentAdapter.ReplyBox> replyBoxes = new HashMap<>();
    private final Map<String, String> replyCursors = new HashMap<>();
    private int activeTab = TAB_SERIES;
    private int generation;
    private boolean alive;
    /**
     * 拉取在途句柄槽（S3b 补齐，与三面同形）：本面只拉无发、
     * 只立 fetchCall 一槽（三口共用，单线程 executor 串行、
     * 同时至多一发在 execute 中）；onDestroyView 时 cancel
     * 断阻塞读。cached 预抓支不发请求、不经此槽。
     */
    private final AtomicReference<Call> fetchCall = new AtomicReference<>();

    public static HongguoCommentDialog create() {
        return new HongguoCommentDialog();
    }

    public HongguoCommentDialog seriesId(String seriesId) {
        this.seriesId = seriesId == null ? "" : seriesId;
        return this;
    }

    public HongguoCommentDialog episode(String vid) {
        this.vid = vid == null ? "" : vid;
        return this;
    }

    /** 横屏播放页形态：右侧划出。详情页/竖屏恒传 false。 */
    public HongguoCommentDialog rightSide(boolean rightSide) {
        this.rightSide = rightSide;
        return this;
    }

    /** 预抓已拿到的首屏（剧评＋单集各一份，可为 null）：面板直接用，不重复发请求。 */
    public HongguoCommentDialog cached(HongguoCommentApi.Page seriesPage, HongguoCommentApi.Page episodePage) {
        this.cachedSeriesPage = seriesPage;
        this.cachedEpisodePage = episodePage;
        return this;
    }

    public void show(FragmentActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || activity.getSupportFragmentManager().isStateSaved()) return;
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof HongguoCommentDialog) return;
        show(activity.getSupportFragmentManager(), TAG);
    }

    /** 切集跟随：播放页切了集且面板还开着时由宿主调用，集维度整体换新、剧评不动。 */
    public void refreshEpisode(String newVid) {
        String next = newVid == null ? "" : newVid;
        if (next.equals(vid)) return;
        vid = next;
        generation++;
        episodeState.reset();
        if (binding == null) return;
        binding.tabEpisode.setVisibility(vid.isEmpty() ? View.GONE : View.VISIBLE);
        if (vid.isEmpty() && activeTab == TAB_EPISODE) switchTab(TAB_SERIES);
        else if (activeTab == TAB_EPISODE) loadFirst(episodeState, TAB_EPISODE);
        updateTitle();
    }

    // ---------- 形态 ----------

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (!rightSide) return super.onCreateDialog(savedInstanceState);
        // 右侧形态不用 BottomSheetDialog：普通 Dialog＋窗口靠右半宽整高，动画走 HongguoCommentRightAnim
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (binding != null) {
            // 根高度在此定死：底部形态半屏（58%），右侧形态整高；onViewCreated 时
            // LayoutParams 可能还没生成，挪到 onStart 视图已挂载后设才稳。
            ViewGroup.LayoutParams lp = binding.getRoot().getLayoutParams();
            if (lp != null) {
                lp.height = rightSide ? ViewGroup.LayoutParams.MATCH_PARENT
                        : (int) (ResUtil.getScreenHeight(requireContext()) * 0.58f);
                binding.getRoot().setLayoutParams(lp);
            }
        }
        if (!rightSide) return;
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setDimAmount(0f);
        WindowManager.LayoutParams params = window.getAttributes();
        params.gravity = Gravity.END;
        params.width = ResUtil.getScreenWidth(requireContext()) / 2;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.windowAnimations = R.style.HongguoCommentRightAnim;
        window.setAttributes(params);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogHongguoCommentBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        alive = true;
        executor = Executors.newSingleThreadExecutor();
        if (rightSide) binding.panelRoot.setBackgroundResource(R.drawable.shape_hongguo_comment_right);
        adapter = new HongguoCommentAdapter(this);
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setItemAnimator(null);
        binding.recycler.setAdapter(adapter);
        binding.tabEpisode.setVisibility(vid.isEmpty() ? View.GONE : View.VISIBLE);
        if (cachedSeriesPage != null) applyFirstPage(seriesState, cachedSeriesPage);
        if (cachedEpisodePage != null) applyFirstPage(episodeState, cachedEpisodePage);
        switchTab(TAB_SERIES);
    }

    @Override
    protected void initEvent() {
        binding.close.setOnClickListener(v -> dismissAllowingStateLoss());
        binding.tabSeries.setOnClickListener(v -> switchTab(TAB_SERIES));
        binding.tabEpisode.setOnClickListener(v -> switchTab(TAB_EPISODE));
        binding.status.setOnClickListener(v -> {
            TabState state = activeState();
            if (state.failed && !state.loading) loadFirst(state, activeTab);
        });
        binding.recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (manager == null) return;
                if (manager.findLastVisibleItemPosition() >= adapter.getItemCount() - 4) loadMore(activeState(), activeTab);
            }
        });
        if (rightSide) initSwipeDismiss();
    }

    /** 横屏右侧面板的右划收回：在列表上或页眉上横向划过阈值即关（关闭动画本身就是整窗右滑出）。 */
    private void initSwipeDismiss() {
        final float[] down = new float[2];
        final boolean[] fired = {false};
        View.OnTouchListener headerSwipe = (v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                down[0] = event.getRawX();
                down[1] = event.getRawY();
                fired[0] = false;
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !fired[0]) {
                float dx = event.getRawX() - down[0];
                float dy = Math.abs(event.getRawY() - down[1]);
                if (dx > ResUtil.dp2px(56) && dx > dy * 2) {
                    fired[0] = true;
                    dismissAllowingStateLoss();
                }
            }
            return false;
        };
        binding.title.setOnTouchListener(headerSwipe);
        binding.tabSeries.setOnTouchListener(headerSwipe);
        binding.tabEpisode.setOnTouchListener(headerSwipe);
        binding.recycler.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                    fired[0] = false;
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !fired[0]) {
                    float dx = event.getRawX() - down[0];
                    float dy = Math.abs(event.getRawY() - down[1]);
                    if (dx > ResUtil.dp2px(56) && dx > dy * 2) {
                        fired[0] = true;
                        dismissAllowingStateLoss();
                    }
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroyView() {
        alive = false;
        generation++;
        HongguoCommentApi.cancelInFlight(fetchCall);
        if (executor != null) executor.shutdownNow();
        super.onDestroyView();
    }

    // ---------- 页签与分页 ----------

    private TabState activeState() {
        return activeTab == TAB_SERIES ? seriesState : episodeState;
    }

    private void switchTab(int tab) {
        activeTab = tab;
        applyTabStyle();
        TabState state = activeState();
        adapter.setItems(state.items);
        adapter.setFooter(state.loading && state.loaded ? HongguoCommentAdapter.FOOTER_LOADING : HongguoCommentAdapter.FOOTER_NONE);
        updateTitle();
        updateStatus();
        if (!state.loaded && !state.loading) loadFirst(state, tab);
    }

    private void applyTabStyle() {
        styleTab(binding.tabSeries, activeTab == TAB_SERIES);
        styleTab(binding.tabEpisode, activeTab == TAB_EPISODE);
    }

    private void styleTab(View tab, boolean selected) {
        com.google.android.material.textview.MaterialTextView view = (com.google.android.material.textview.MaterialTextView) tab;
        view.setBackgroundResource(selected ? R.drawable.shape_hongguo_tab_selected : android.R.color.transparent);
        view.setTextColor(selected ? 0xE6000000 : 0x73000000);
        view.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void updateTitle() {
        long total = activeState().total;
        binding.title.setText(total > 0
                ? getString(R.string.hongguo_comment) + " · " + total
                : getString(R.string.hongguo_comment));
    }

    private void updateStatus() {
        TabState state = activeState();
        if (!state.items.isEmpty()) {
            binding.status.setVisibility(View.GONE);
            return;
        }
        binding.status.setVisibility(View.VISIBLE);
        if (state.loading) binding.status.setText(R.string.hongguo_comment_loading);
        else if (state.failed) binding.status.setText(R.string.hongguo_comment_failed);
        else binding.status.setText(R.string.hongguo_comment_empty);
    }

    private void applyFirstPage(TabState state, HongguoCommentApi.Page page) {
        state.items.clear();
        state.items.addAll(page.items);
        state.total = page.total;
        state.cursor = page.nextCursor;
        state.hasMore = page.hasMore;
        state.loaded = true;
        state.failed = false;
    }

    private void loadFirst(TabState state, int tab) {
        if (state.loading) return;
        state.loading = true;
        state.failed = false;
        updateStatus();
        int gen = generation;
        executor.execute(() -> {
            HongguoCommentApi.Page page = tab == TAB_SERIES
                    ? HongguoCommentApi.seriesComments(seriesId, "", fetchCall)
                    : HongguoCommentApi.episodeComments(seriesId, vid, "", fetchCall);
            App.post(() -> {
                if (!alive || gen != generation) return;
                state.loading = false;
                if (page == null) {
                    state.failed = true;
                } else {
                    applyFirstPage(state, page);
                }
                if (tab == activeTab) {
                    adapter.setItems(state.items);
                    adapter.setFooter(HongguoCommentAdapter.FOOTER_NONE);
                    updateTitle();
                    updateStatus();
                }
            });
        });
    }

    private void loadMore(TabState state, int tab) {
        if (state.loading || !state.loaded || !state.hasMore) return;
        state.loading = true;
        if (tab == activeTab) adapter.setFooter(HongguoCommentAdapter.FOOTER_LOADING);
        int gen = generation;
        String cursor = state.cursor;
        executor.execute(() -> {
            HongguoCommentApi.Page page = tab == TAB_SERIES
                    ? HongguoCommentApi.seriesComments(seriesId, cursor, fetchCall)
                    : HongguoCommentApi.episodeComments(seriesId, vid, cursor, fetchCall);
            App.post(() -> {
                if (!alive || gen != generation) return;
                state.loading = false;
                if (page == null) {
                    if (tab == activeTab) adapter.setFooter(HongguoCommentAdapter.FOOTER_ERROR);
                    return;
                }
                state.items.addAll(page.items);
                state.cursor = page.nextCursor;
                state.hasMore = page.hasMore;
                state.total = Math.max(state.total, page.total);
                if (tab == activeTab) {
                    adapter.appendItems(page.items);
                    adapter.setFooter(HongguoCommentAdapter.FOOTER_NONE);
                    updateTitle();
                }
            });
        });
    }

    // ---------- 回复展开（Adapter.Listener） ----------

    @Override
    public HongguoCommentAdapter.ReplyBox replyBoxOf(String commentId) {
        return replyBoxes.get(commentId);
    }

    @Override
    public void onToggleReplies(HongguoCommentApi.Comment item) {
        HongguoCommentAdapter.ReplyBox box = replyBoxes.computeIfAbsent(item.id, k -> new HongguoCommentAdapter.ReplyBox());
        box.expanded = !box.expanded;
        adapter.refreshComment(item.id);
        if (box.expanded && box.items.isEmpty() && !box.loading) loadReplies(item, true);
    }

    @Override
    public void onMoreReplies(HongguoCommentApi.Comment item) {
        HongguoCommentAdapter.ReplyBox box = replyBoxes.get(item.id);
        if (box == null || box.loading) return;
        if (box.failed || box.hasMore) loadReplies(item, false);
    }

    @Override
    public void onFooterRetry() {
        TabState state = activeState();
        adapter.setFooter(HongguoCommentAdapter.FOOTER_NONE);
        if (state.loaded) loadMore(state, activeTab);
        else loadFirst(state, activeTab);
    }

    private void loadReplies(HongguoCommentApi.Comment item, boolean first) {
        HongguoCommentAdapter.ReplyBox box = replyBoxes.get(item.id);
        if (box == null || box.loading) return;
        box.loading = true;
        box.failed = false;
        adapter.refreshComment(item.id);
        int gen = generation;
        int tab = activeTab;
        String cursor = first ? "" : replyCursors.getOrDefault(item.id, "");
        executor.execute(() -> {
            HongguoCommentApi.Page page = tab == TAB_SERIES
                    ? HongguoCommentApi.seriesReplies(seriesId, item.id, cursor, fetchCall)
                    : HongguoCommentApi.episodeReplies(seriesId, vid, item.id, cursor, fetchCall);
            App.post(() -> {
                if (!alive || gen != generation) return;
                box.loading = false;
                if (page == null) {
                    box.failed = true;
                } else {
                    if (first) box.items.clear();
                    box.items.addAll(page.items);
                    box.hasMore = page.hasMore;
                    replyCursors.put(item.id, page.nextCursor);
                }
                adapter.refreshComment(item.id);
            });
        });
    }
}
