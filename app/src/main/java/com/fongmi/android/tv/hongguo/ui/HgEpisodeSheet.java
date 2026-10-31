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
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * 选集单（打回重落，定稿浅色 .sheet）：白底顶圆 22＋grab，头＝
 * 「选集」＋集数 span＋关闭圆钮，体＝6 列集号格（白底＋line 边＋
 * 圆 10，当前格主题红 #E63A2E 白字，看过格 sub 字＋右上 5dp
 * faint 点），脚＝剧名居中（#FBFAF8 底＋顶线）。看过界取本地
 * 断点（HgStore.progressOf 的集序号，1 起）。旧深色面整件弃用
 * （账 hongguo-page-redo 打回销账①）。竖屏与横屏共用同一事件链。
 */
public final class HgEpisodeSheet {

    public interface OnPick {
        void onPick(int episodeIndex);
    }

    private HgEpisodeSheet() {
    }

    private static int navBarHeight(AppCompatActivity a) {
        int id = a.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? a.getResources().getDimensionPixelSize(id) : 0;
    }

    public static void show(AppCompatActivity activity, DramaDetail detail,
                            int currentEpisode, OnPick onPick) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        java.util.function.IntUnaryOperator dp = v -> (int) (v * density + 0.5f);

        int total = detail.getEpisodes().size();
        boolean finished = detail.getSeriesStatus() != null && detail.getSeriesStatus() == 1;
        HgStore.Progress prog = detail.getSeriesId() == null ? null
                : HgStore.progressOf(detail.getSeriesId());
        int seen = prog == null ? 0 : prog.episodeIndex;

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(Color.WHITE);
        float rr = dp.applyAsInt(22);
        rootBg.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
        root.setBackground(rootBg);

        View grab = new View(activity);
        GradientDrawable grabBg = new GradientDrawable();
        grabBg.setColor(0x291B1B1F);
        grabBg.setCornerRadius(dp.applyAsInt(2));
        grab.setBackground(grabBg);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                dp.applyAsInt(38), dp.applyAsInt(4));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.topMargin = dp.applyAsInt(9);
        glp.bottomMargin = dp.applyAsInt(2);
        root.addView(grab, glp);

        // 头（定稿 .s-head）：标题 16/900＋span 12.5 sub＋关闭圆钮
        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp.applyAsInt(18), dp.applyAsInt(10), dp.applyAsInt(18), dp.applyAsInt(8));
        TextView headTitle = new TextView(activity);
        headTitle.setText("选集");
        headTitle.setTextColor(0xFF1B1B1F);
        headTitle.setTextSize(16);
        headTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(headTitle, new LinearLayout.LayoutParams(-2, -2));
        TextView headInfo = new TextView(activity);
        headInfo.setText(finished ? "全 " + total + " 集" : "更新至 " + total + " 集");
        headInfo.setTextColor(0xFF8A8A93);
        headInfo.setTextSize(12.5f);
        LinearLayout.LayoutParams hilp = new LinearLayout.LayoutParams(-2, -2);
        hilp.leftMargin = dp.applyAsInt(5);
        head.addView(headInfo, hilp);
        View headSpacer = new View(activity);
        head.addView(headSpacer, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView close = new ImageView(activity);
        close.setImageResource(R.drawable.ic_drama_close);
        close.setColorFilter(0xFF1B1B1F);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0x0D1B1B1F);
        closeBg.setCornerRadius(dp.applyAsInt(19));
        close.setBackground(closeBg);
        close.setPadding(dp.applyAsInt(10), dp.applyAsInt(10),
                dp.applyAsInt(10), dp.applyAsInt(10));
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(
                dp.applyAsInt(38), dp.applyAsInt(38)));
        root.addView(head);

        // 体（定稿 .s-body＋.epgrid）：6 列、格距 8
        RecyclerView grid = new RecyclerView(activity);
        grid.setLayoutManager(new GridLayoutManager(activity, 6));
        grid.setPadding(dp.applyAsInt(18), dp.applyAsInt(2),
                dp.applyAsInt(18), dp.applyAsInt(10));
        grid.setClipToPadding(false);
        final int gap = dp.applyAsInt(8);
        grid.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent,
                                       @NonNull RecyclerView.State state) {
                int pos = parent.getChildAdapterPosition(view);
                if (pos < 0) return;
                int col = pos % 6;
                outRect.left = col * gap / 6;
                outRect.right = gap - (col + 1) * gap / 6;
                outRect.top = pos < 6 ? 0 : gap;
            }
        });
        if (total > 0) {
            grid.setAdapter(new EpAdapter(activity, total, currentEpisode, seen, idx -> {
                dialog.dismiss();
                onPick.onPick(idx);
            }, dp));
            root.addView(grid, new LinearLayout.LayoutParams(-1, -2));
            if (currentEpisode > 1) {
                grid.scrollToPosition(currentEpisode - 1);
            }
        } else {
            // 空态（C-21）：detail 在手但集表为空时此前照开一张
            // 只有「更新至 0 集」头行的空白单——挂一行空态文案，
            // 不再让用户对着空网格猜。
            TextView empty = new TextView(activity);
            empty.setText("暂无集数");
            empty.setTextColor(0xFF8A8A93);
            empty.setTextSize(13.5f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp.applyAsInt(18), dp.applyAsInt(26),
                    dp.applyAsInt(18), dp.applyAsInt(26));
            root.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }

        // 脚（定稿 .s-foot）：剧名居中，#FBFAF8 底＋顶线，下沿给
        // 系统手势区让位
        LinearLayout foot = new LinearLayout(activity);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setBackgroundColor(0xFFFBFAF8);
        View footLine = new View(activity);
        footLine.setBackgroundColor(0x121B1B1F);
        foot.addView(footLine, new LinearLayout.LayoutParams(-1, 1));
        TextView footText = new TextView(activity);
        footText.setText(detail.getTitle() == null ? "" : detail.getTitle());
        footText.setTextColor(0xFF8A8A93);
        footText.setTextSize(12.5f);
        footText.setGravity(Gravity.CENTER);
        footText.setMaxLines(1);
        footText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        footText.setPadding(dp.applyAsInt(18), dp.applyAsInt(11), dp.applyAsInt(18),
                dp.applyAsInt(13) + navBarHeight(activity));
        foot.addView(footText);
        root.addView(foot);

        // 几何（回炉定根，账 fix168-episodesheet-diag）：网格入根
        // 即 (-1,-2) wrap 自然高、格高走构造定数（见 EpAdapter），
        // 整单零脱窗实量、零高度回写——旧 fitListHeight 的开单前
        // 实量定高在真机上把三单的纵向排布一并锁坏（头在底、身
        // 空白、脚出界），已整步摘除。
        dialog.setContentView(root);
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.setBackgroundColor(Color.TRANSPARENT);
        }
        dialog.show();
    }

    private static final class EpAdapter extends RecyclerView.Adapter<EpAdapter.H> {
        private final AppCompatActivity activity;
        private final int total;
        private final int current;
        private final int seen;
        private final OnPick onPick;
        private final java.util.function.IntUnaryOperator dp;

        EpAdapter(AppCompatActivity activity, int total, int current, int seen,
                  OnPick onPick, java.util.function.IntUnaryOperator dp) {
            this.activity = activity;
            this.total = total;
            this.current = current;
            this.seen = seen;
            this.onPick = onPick;
            this.dp = dp;
        }

        class H extends RecyclerView.ViewHolder {
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
            // 格高写死构造定数 48dp（现行皮肤格内距＋字高定，照能
            // 用版 46dp 的做法）：网格自然高＝行数×定数＋行距＋内
            // 距，由挂窗后的正式布局一趟算出，不依赖任何实量；
            // 集数多到超屏时容器 AT_MOST 封顶、网格在封顶内滚。
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, dp.applyAsInt(48)));
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
            // 边框/底挂格根（文字件只管字），FrameLayout 根承载背景
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
