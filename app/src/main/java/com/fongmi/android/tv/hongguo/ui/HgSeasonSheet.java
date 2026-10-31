package com.fongmi.android.tv.hongguo.ui;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.hongguo.drama.DramaItem;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

/**
 * 系列剧季名单（簇七 S-01）：多季正剧在首页流里的季切换单。
 * 名单来源只认真数据两路——条目自带 siblings（回包 album_data_list
 * 解析，DramaParser.asSeriesItem depth 0）或专辑详情 series_list
 * （DramaClient.fetchAlbumDetail），行集数即 series_list[].episode_cnt
 * （S-03 的集数正位）。骨架与 HgAlbumSheet 定稿 .sheet/.hrow 同套
 * （白底顶圆 22＋grab＋行卡＋红圆播放钮），头/脚文案按季口径：
 * 头＝剧名＋「共 N 季」（N 优先条目 albumSerialCount、缺时行数），
 * 当前季行冠「当前季」。官方季单的点开形态未抠到（另一线在抠，
 * 账 hongguo-official-values），本单形态属自家定版骨架复用，
 * 抠值回后若官方形态不同再校——落点分工见簇七账 B-01 留账注。
 */
public final class HgSeasonSheet {

    public interface OnPick {
        void onPick(DramaItem season);
    }

    private HgSeasonSheet() {
    }

    private static int navBarHeight(AppCompatActivity a) {
        int id = a.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? a.getResources().getDimensionPixelSize(id) : 0;
    }

    private static android.graphics.drawable.GradientDrawable roundBg(int color, float radius) {
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    public static void show(AppCompatActivity activity, String title,
                            List<DramaItem> rows, String currentSeriesId,
                            int serialCount, OnPick onPick) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (rows == null || rows.isEmpty()) return;
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        java.util.function.IntUnaryOperator dp = v -> (int) (v * density + 0.5f);
        String headTitle = title == null ? "" : title;
        int total = serialCount > 0 ? serialCount : rows.size();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable rootBg =
                new android.graphics.drawable.GradientDrawable();
        rootBg.setColor(Color.WHITE);
        float rr = dp.applyAsInt(22);
        rootBg.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
        root.setBackground(rootBg);

        View grab = new View(activity);
        grab.setBackground(roundBg(0x291B1B1F, dp.applyAsInt(2)));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                dp.applyAsInt(38), dp.applyAsInt(4));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.topMargin = dp.applyAsInt(9);
        glp.bottomMargin = dp.applyAsInt(2);
        root.addView(grab, glp);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp.applyAsInt(18), dp.applyAsInt(10), dp.applyAsInt(18), dp.applyAsInt(8));
        TextView headTitleView = new TextView(activity);
        headTitleView.setText(headTitle);
        headTitleView.setTextColor(0xFF1B1B1F);
        headTitleView.setTextSize(16);
        headTitleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        headTitleView.setMaxLines(1);
        headTitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(headTitleView, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView headInfo = new TextView(activity);
        headInfo.setText("共 " + total + " 季");
        headInfo.setTextColor(0xFF8A8A93);
        headInfo.setTextSize(12.5f);
        LinearLayout.LayoutParams hilp = new LinearLayout.LayoutParams(-2, -2);
        hilp.leftMargin = dp.applyAsInt(5);
        head.addView(headInfo, hilp);
        ImageView close = new ImageView(activity);
        close.setImageResource(R.drawable.ic_drama_close);
        close.setColorFilter(0xFF1B1B1F);
        android.graphics.drawable.GradientDrawable closeBg =
                new android.graphics.drawable.GradientDrawable();
        closeBg.setColor(0x0D1B1B1F);
        closeBg.setCornerRadius(dp.applyAsInt(19));
        close.setBackground(closeBg);
        close.setPadding(dp.applyAsInt(10), dp.applyAsInt(10),
                dp.applyAsInt(10), dp.applyAsInt(10));
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(
                dp.applyAsInt(38), dp.applyAsInt(38)));
        root.addView(head);

        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setPadding(dp.applyAsInt(18), dp.applyAsInt(2),
                dp.applyAsInt(18), dp.applyAsInt(10));
        list.setClipToPadding(false);
        int currentIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (currentSeriesId != null && currentSeriesId.equals(rows.get(i).getSeriesId())) {
                currentIndex = i;
                break;
            }
        }
        final int curIdx = currentIndex;
        list.setAdapter(new RowAdapter(activity, rows, currentIndex, idx -> {
            dialog.dismiss();
            onPick.onPick(rows.get(idx));
        }, dp));
        root.addView(list, new LinearLayout.LayoutParams(-1, -2));
        if (curIdx > 0) list.scrollToPosition(curIdx);

        LinearLayout foot = new LinearLayout(activity);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setBackgroundColor(0xFFFBFAF8);
        View footLine = new View(activity);
        footLine.setBackgroundColor(0x121B1B1F);
        foot.addView(footLine, new LinearLayout.LayoutParams(-1, 1));
        TextView footText = new TextView(activity);
        footText.setText(headTitle + " · 共 " + total + " 季");
        footText.setTextColor(0xFF8A8A93);
        footText.setTextSize(12.5f);
        footText.setGravity(Gravity.CENTER);
        footText.setMaxLines(1);
        footText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        footText.setPadding(dp.applyAsInt(18), dp.applyAsInt(11), dp.applyAsInt(18),
                dp.applyAsInt(13) + navBarHeight(activity));
        foot.addView(footText);
        root.addView(foot);

        // 几何与三单同口径：列表 wrap 自然高、零实量零回写。
        dialog.setContentView(root);
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.setBackgroundColor(Color.TRANSPARENT);
        }
        dialog.show();
    }

    private static final class RowAdapter extends RecyclerView.Adapter<RowAdapter.H> {
        private final AppCompatActivity activity;
        private final List<DramaItem> rows;
        private final int currentIndex;
        private final HgAlbumSheet.OnPick onPick;
        private final java.util.function.IntUnaryOperator dp;

        RowAdapter(AppCompatActivity activity, List<DramaItem> rows, int currentIndex,
                   HgAlbumSheet.OnPick onPick, java.util.function.IntUnaryOperator dp) {
            this.activity = activity;
            this.rows = rows;
            this.currentIndex = currentIndex;
            this.onPick = onPick;
            this.dp = dp;
        }

        class H extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView name;
            final TextView sub;

            H(LinearLayout row) {
                super(row);
                cover = row.findViewWithTag("cover");
                name = row.findViewWithTag("name");
                sub = row.findViewWithTag("sub");
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 行卡与 HgAlbumSheet .hrow 同装配（白底＋line 边＋圆 16、
            // 拇图 58×76 圆 10、标题 14.5/700、副行 12、38 红圆钮）
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp.applyAsInt(10), dp.applyAsInt(10),
                    dp.applyAsInt(10), dp.applyAsInt(10));
            android.graphics.drawable.GradientDrawable rowBg =
                    new android.graphics.drawable.GradientDrawable();
            rowBg.setColor(Color.WHITE);
            rowBg.setStroke(dp.applyAsInt(1), 0x121B1B1F);
            rowBg.setCornerRadius(dp.applyAsInt(16));
            row.setBackground(rowBg);
            row.setElevation(dp.applyAsInt(1));
            RecyclerView.LayoutParams rlp = new RecyclerView.LayoutParams(-1, -2);
            rlp.bottomMargin = dp.applyAsInt(8);
            row.setLayoutParams(rlp);
            ImageView cover = new ImageView(activity);
            cover.setTag("cover");
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(roundBg(0xFFE8E6E2, dp.applyAsInt(10)));
            row.addView(cover, new LinearLayout.LayoutParams(
                    dp.applyAsInt(58), dp.applyAsInt(76));
            LinearLayout info = new LinearLayout(activity);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = new TextView(activity);
            name.setTag("name");
            name.setTextColor(0xFF1B1B1F);
            name.setTextSize(14.5f);
            name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(name);
            TextView sub = new TextView(activity);
            sub.setTag("sub");
            sub.setTextColor(0xFF8A8A93);
            sub.setTextSize(12);
            sub.setMaxLines(1);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = dp.applyAsInt(4);
            info.addView(sub, slp);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1f);
            ilp.leftMargin = dp.applyAsInt(12);
            row.addView(info, ilp);
            FrameLayout resume = new FrameLayout(activity);
            android.graphics.drawable.GradientDrawable resBg =
                    new android.graphics.drawable.GradientDrawable();
            resBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            resBg.setColor(0xFFE63A2E);
            resume.setBackground(resBg);
            ImageView play = new ImageView(activity);
            play.setImageResource(R.drawable.ic_drama_play);
            play.setColorFilter(Color.WHITE);
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                    dp.applyAsInt(15), dp.applyAsInt(15));
            plp.gravity = Gravity.CENTER;
            resume.addView(play, plp);
            LinearLayout.LayoutParams resLp = new LinearLayout.LayoutParams(
                    dp.applyAsInt(38), dp.applyAsInt(38));
            resLp.leftMargin = dp.applyAsInt(12);
            row.addView(resume, resLp);
            return new H(row);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            DramaItem item = rows.get(position);
            boolean on = position == currentIndex;
            h.name.setText(item.getTitle() == null ? "" : item.getTitle());
            if (item.getCoverUrl() != null && !item.getCoverUrl().isEmpty()) {
                HgCoverClip.round(h.cover, dp.applyAsInt(10));
                Glide.with(activity).load(item.getCoverUrl()).into(h.cover);
            } else {
                h.cover.setImageDrawable(null);
            }
            // 副行：当前季冠「当前季」，其后当季集数（series_list
            // 的 episode_cnt，S-03 正位来源，两件都是回包实有值）
            StringBuilder sb = new StringBuilder();
            if (on) sb.append("当前季");
            boolean rowFinished = item.getSeriesStatus() != null
                    && item.getSeriesStatus() == 1;
            String rowCount = HgPanelLogic.countText(rowFinished, item.getEpisodeCount());
            if (!rowCount.isEmpty()) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(rowCount);
            }
            h.sub.setText(sb.toString());
            h.sub.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
            h.itemView.setOnClickListener(v -> onPick.onPick(position));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
