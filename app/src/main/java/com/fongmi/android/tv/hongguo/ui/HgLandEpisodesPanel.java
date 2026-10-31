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
 */
public final class HgLandEpisodesPanel {

    /** 层宽占比（与横屏评论面板同值，自家定值、待裁）。 */
    public static final float WIDTH_RATIO = HgLandCommentsPanel.WIDTH_RATIO;

    private static final int COLS = 5;

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
    private final RecyclerView grid;

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
    }

    public View view() {
        return root;
    }

    /** 绑一剧的集表（与竖屏单 show 同源同法：总数/完结态/看过界）。 */
    public void bind(DramaDetail detail, int currentEpisode) {
        if (detail == null) return;
        int total = detail.getEpisodes().size();
        boolean finished = detail.getSeriesStatus() != null
                && detail.getSeriesStatus() == 1;
        title.setText(finished
                ? "选集 · 全 " + total + " 集"
                : "选集 · 更新至 " + total + " 集");
        HgStore.Progress prog = detail.getSeriesId() == null ? null
                : HgStore.progressOf(detail.getSeriesId());
        int seen = prog == null ? 0 : prog.episodeIndex;
        grid.setAdapter(new EpAdapter(total, currentEpisode, seen,
                idx -> {
                    if (host != null) host.onPick(idx);
                }));
        if (currentEpisode > 1) {
            grid.scrollToPosition(currentEpisode - 1);
        }
    }

    /** 集号格（形态逐项沿竖屏选集单 EpAdapter 定版，见类头注）。 */
    private final class EpAdapter extends RecyclerView.Adapter<EpAdapter.H> {
        private final int total;
        private final int current;
        private final int seen;
        private final HgEpisodeSheet.OnPick onPick;

        EpAdapter(int total, int current, int seen, HgEpisodeSheet.OnPick onPick) {
            this.total = total;
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
            int ep = position + 1;
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
            return total;
        }
    }
}
