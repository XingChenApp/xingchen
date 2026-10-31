package com.fongmi.android.tv.hongguo.ui;

import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.hongguo.drama.DramaDetail;

/**
 * 横屏选集页内层（2026-10-31）：官方横屏选集是页内集数层
 * （EpisodeLayoutType 两型，普查卡 10/抠查账 §五），咱家此前
 * 把竖屏底部弹层 HgEpisodeSheet 原样搬进横屏——屏底升起、铺
 * 屏宽，与画面框无任何关系（账 hongguo-land-layout-audit-
 * 20261031 弹层·选集行）。本件照横屏评论面板
 * HgLandCommentsPanel 的停靠做法：右侧停靠的页内层，宽占比
 * 与评论面板同值 0.40（同为自家定值，官方层内布局未抠到、
 * 待裁），深底沿横屏页自身语言；头＝标题居中＋右上 ×（与评
 * 论面板同形），体＝集号格。
 * 格子形态逐项沿竖屏选集单定版（白卡＋line 边＋圆 10、当前
 * 格主题红白字粗体、看过格灰字＋右上 5dp faint 点），列数
 * 5 是按层宽折算的自家定值（竖屏单全宽 6 列）。
 * 数据链不动：集数取 DramaDetail 集表、看过界取 HgStore
 * 本地断点，与竖屏单同源；点格经 Host 回宿主走既有
 * switchEpisode。竖屏选集单本体一行未动——两面各自成件，
 * 只共享数据层。无线程无异步，宿主销毁随视图树回收。
 * 分组 C-18（官证，账 hongguo-official-values-20261031 §三）：
 * 官方选集件是「区间页签＋分页」形态——页签文案＝1 起区间串
 * （起-止）、序号模式页长 30。本层即集号格形态（相当官方序
 * 号模式），故落 30/页区间页签：总集数 >30 才出页签栏，格只
 * 渲染当前页。页签样式从本层既有语言（当前集红 #E63A2E），
 * 官方层内布局未抠到之限不变。C-19 倒序：官方短剧选集全树
 * 无此件（正/倒序只在小说章节目录），不做。
 */
public final class HgLandEpisodesPanel {

    /** 层宽占比（与横屏评论面板同值，自家定值、待裁）。 */
    public static final float WIDTH_RATIO = HgLandCommentsPanel.WIDTH_RATIO;

    private static final int COLS = 5;

    /** 分组页长（官值：序号模式手机恒 30/页，C-18）。 */
    private static final int PAGE_SIZE = 30;

    /** 宿主回调：点格切集＋关闭请求。 */
    public interface Host {
        void onPick(int episodeIndex);

        void onClose();
    }

    private final AppCompatActivity activity;
    private final Host host;
    private final java.util.function.IntUnaryOperator dp;
    private final LinearLayout root;
    private final TextView title;
    private final RecyclerView tabBar;
    private final RecyclerView grid;
    private final TextView emptyView;

    // 分组态（bind 时落）：总数/当前集/看过界/当前页序与页签适配器。
    private int total;
    private int current;
    private int seen;
    private int page;
    private TabAdapter tabAdapter;

    public HgLandEpisodesPanel(AppCompatActivity activity, Host host) {
        this.activity = activity;
        this.host = host;
        float density = activity.getResources().getDisplayMetrics().density;
        dp = v -> (int) (v * density + 0.5f);

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1D1D20);

        // 头：标题居中＋右上 ×（与横屏评论面板同形）
        FrameLayout head = new FrameLayout(activity);
        title = new TextView(activity);
        title.setText("选集");
        title.setTextColor(0xFFF2F2F4);
        title.setTextSize(15);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new FrameLayout.LayoutParams(-1, -1));
        ImageView close = new ImageView(activity);
        close.setImageResource(R.drawable.ic_drama_close);
        close.setColorFilter(Color.WHITE);
        close.setPadding(dp.applyAsInt(10), dp.applyAsInt(10),
                dp.applyAsInt(10), dp.applyAsInt(10));
        close.setOnClickListener(v -> {
            if (host != null) host.onClose();
        });
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                dp.applyAsInt(38), dp.applyAsInt(38));
        closeLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        closeLp.rightMargin = dp.applyAsInt(6);
        head.addView(close, closeLp);
        root.addView(head, new LinearLayout.LayoutParams(-1, dp.applyAsInt(52)));
        View headLine = new View(activity);
        headLine.setBackgroundColor(0x14FFFFFF);
        root.addView(headLine, new LinearLayout.LayoutParams(-1, 1));

        // 分组页签栏（C-18）：横向滚动、文案「起-止」；只在页数
        // >1 时显（bind 内定），单页不占位。
        tabBar = new RecyclerView(activity);
        tabBar.setLayoutManager(new LinearLayoutManager(
                activity, LinearLayoutManager.HORIZONTAL, false));
        tabBar.setPadding(dp.applyAsInt(16), dp.applyAsInt(10),
                dp.applyAsInt(16), dp.applyAsInt(2));
        tabBar.setClipToPadding(false);
        tabBar.setVisibility(View.GONE);
        root.addView(tabBar, new LinearLayout.LayoutParams(-1, -2));

        // 体：集号格（格距/格形沿竖屏选集单定版，见类头注）
        grid = new RecyclerView(activity);
        grid.setLayoutManager(new GridLayoutManager(activity, COLS));
        grid.setPadding(dp.applyAsInt(16), dp.applyAsInt(10),
                dp.applyAsInt(16), dp.applyAsInt(16));
        grid.setClipToPadding(false);
        final int gap = dp.applyAsInt(8);
        grid.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent,
                                       @NonNull RecyclerView.State state) {
                int pos = parent.getChildAdapterPosition(view);
                if (pos < 0) return;
                int col = pos % COLS;
                outRect.left = col * gap / COLS;
                outRect.right = gap - (col + 1) * gap / COLS;
                outRect.top = pos < COLS ? 0 : gap;
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 空态（C-21 同型，与竖屏选集单同文案）：集表为空时显，
        // 与网格互斥切换（bind 内定）。
        emptyView = new TextView(activity);
        emptyView.setText("暂无集数");
        emptyView.setTextColor(0xFF8A8A93);
        emptyView.setTextSize(13.5f);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    public View view() {
        return root;
    }

    /** 绑一剧的集表（与竖屏单 show 同源同法：总数/完结态/看过界）。 */
    public void bind(DramaDetail detail, int currentEpisode) {
        if (detail == null) return;
        total = detail.getEpisodes().size();
        boolean finished = detail.getSeriesStatus() != null
                && detail.getSeriesStatus() == 1;
        title.setText(finished
                ? "选集 · 全 " + total + " 集"
                : "选集 · 更新至 " + total + " 集");
        HgStore.Progress prog = detail.getSeriesId() == null ? null
                : HgStore.progressOf(detail.getSeriesId());
        seen = prog == null ? 0 : prog.episodeIndex;
        current = currentEpisode;
        boolean empty = total == 0;
        grid.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        // 分组装配（C-18）：当前集所在页为初始页，页签只在多页时出。
        int pages = pageCount();
        page = pages == 0 ? 0 : Math.min(Math.max(
                (currentEpisode - 1) / PAGE_SIZE, 0), pages - 1);
        tabAdapter = pages > 1 ? new TabAdapter(pages) : null;
        tabBar.setAdapter(tabAdapter);
        tabBar.setVisibility(pages > 1 ? View.VISIBLE : View.GONE);
        showPage(page);
        int within = currentEpisode - 1 - page * PAGE_SIZE;
        if (within > 0) {
            grid.scrollToPosition(within);
        }
        if (pages > 1) {
            tabBar.scrollToPosition(page);
        }
    }

    private int pageCount() {
        return total <= 0 ? 0 : (total + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    /** 切到第 p 页：格只渲染该页区间、页签高亮跟随。 */
    private void showPage(int p) {
        page = p;
        int start = p * PAGE_SIZE;
        int count = Math.max(0, Math.min(PAGE_SIZE, total - start));
        grid.setAdapter(new EpAdapter(start, count, current, seen,
                idx -> {
                    if (host != null) host.onPick(idx);
                }));
        if (tabAdapter != null) tabAdapter.notifyDataSetChanged();
    }

    /** 分组页签（C-18）：文案＝该页 1 起区间「起-止」，点签切页。 */
    private final class TabAdapter extends RecyclerView.Adapter<TabAdapter.H> {
        private final int pages;

        TabAdapter(int pages) {
            this.pages = pages;
        }

        final class H extends RecyclerView.ViewHolder {
            final TextView text;

            H(TextView t) {
                super(t);
                text = t;
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView t = new TextView(activity);
            t.setTextSize(13);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp.applyAsInt(12), dp.applyAsInt(6),
                    dp.applyAsInt(12), dp.applyAsInt(6));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-2, -2);
            lp.rightMargin = dp.applyAsInt(8);
            t.setLayoutParams(lp);
            return new H(t);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            int start = position * PAGE_SIZE + 1;
            int end = Math.min(total, (position + 1) * PAGE_SIZE);
            h.text.setText(start + "-" + end);
            boolean on = position == page;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp.applyAsInt(8));
            if (on) {
                bg.setColor(0xFFE63A2E);
                h.text.setTextColor(Color.WHITE);
                h.text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            } else {
                bg.setColor(0x14FFFFFF);
                h.text.setTextColor(0xFFF2F2F4);
                h.text.setTypeface(android.graphics.Typeface.DEFAULT);
            }
            h.text.setBackground(bg);
            final int p = position;
            h.itemView.setOnClickListener(v -> {
                if (p == page) return;
                showPage(p);
                grid.scrollToPosition(0);
            });
        }

        @Override
        public int getItemCount() {
            return pages;
        }
    }

    /** 集号格（形态逐项沿竖屏选集单 EpAdapter 定版，见类头注）。 */
    private final class EpAdapter extends RecyclerView.Adapter<EpAdapter.H> {
        private final int pageStart;
        private final int count;
        private final int current;
        private final int seen;
        private final HgEpisodeSheet.OnPick onPick;

        EpAdapter(int pageStart, int count, int current, int seen,
                  HgEpisodeSheet.OnPick onPick) {
            this.pageStart = pageStart;
            this.count = count;
            this.current = current;
            this.seen = seen;
            this.onPick = onPick;
        }

        final class H extends RecyclerView.ViewHolder {
            final TextView text;
            final View dot;

            H(FrameLayout cell) {
                super(cell);
                text = (TextView) cell.getChildAt(0);
                dot = cell.getChildAt(1);
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout cell = new FrameLayout(activity);
            TextView t = new TextView(activity);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(13.5f);
            t.setPadding(0, dp.applyAsInt(9), 0, dp.applyAsInt(9));
            cell.addView(t, new FrameLayout.LayoutParams(-1, -2));
            View dot = new View(activity);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(0xFFC9C9CF);
            dot.setBackground(dotBg);
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                    dp.applyAsInt(5), dp.applyAsInt(5));
            dlp.gravity = Gravity.TOP | Gravity.RIGHT;
            dlp.topMargin = dp.applyAsInt(5);
            dlp.rightMargin = dp.applyAsInt(5);
            cell.addView(dot, dlp);
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new H(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            int ep = pageStart + position + 1;
            boolean on = ep == current;
            boolean watched = !on && ep <= seen;
            h.text.setText(String.valueOf(ep));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp.applyAsInt(10));
            if (on) {
                bg.setColor(0xFFE63A2E);
                h.text.setTextColor(Color.WHITE);
                h.text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            } else {
                bg.setColor(Color.WHITE);
                bg.setStroke(dp.applyAsInt(1), 0x121B1B1F);
                h.text.setTextColor(watched ? 0xFF8A8A93 : 0xFF1B1B1F);
                h.text.setTypeface(android.graphics.Typeface.DEFAULT);
            }
            h.itemView.setBackground(bg);
            h.dot.setVisibility(watched ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> onPick.onPick(ep));
        }

        @Override
        public int getItemCount() {
            return count;
        }
    }
}
