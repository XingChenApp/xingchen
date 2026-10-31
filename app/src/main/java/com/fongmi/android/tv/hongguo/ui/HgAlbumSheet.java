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
import com.fongmi.android.tv.hongguo.drama.DramaAlbumDetail;
import com.fongmi.android.tv.hongguo.drama.DramaItem;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * 视频页合集列表单（打回重落，定稿浅色 .sheet）：与选集单同骨——
 * 白底顶圆 22＋grab，头＝「合集」＋「 · 合集名」span＋关闭圆钮，
 * 体＝.hrow 行卡（白底＋line 边＋圆 16、拇图 58×76 圆 10、标题
 * 14.5/700、副行 12 sub、右侧 38 主题红圆播放钮），脚＝合集条
 * 文案居中。副行组装：当前行冠「正在播放 · 」，其后集数文案＋
 * 首枚标签（行回包无时长字段，时长段不编、见打回销账①）。
 * 旧深色面（序号列＋64×40 封面）整件弃用。
 */
public final class HgAlbumSheet {

    public interface OnPick {
        void onPick(int index);
    }

    private HgAlbumSheet() {
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

    public static void show(AppCompatActivity activity, DramaAlbumDetail album,
                            int currentIndex, OnPick onPick) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        java.util.function.IntUnaryOperator dp = v -> (int) (v * density + 0.5f);

        boolean finished = album.seriesStatus != null && album.seriesStatus == 1;
        int total = album.episodeCount > 0 ? album.episodeCount : album.rows.size();
        String count = HgPanelLogic.countText(finished, total);
        String albumTitle = album.title == null ? "" : album.title;

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
        TextView headTitle = new TextView(activity);
        headTitle.setText("合集");
        headTitle.setTextColor(0xFF1B1B1F);
        headTitle.setTextSize(16);
        headTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(headTitle, new LinearLayout.LayoutParams(-2, -2));
        TextView headInfo = new TextView(activity);
        headInfo.setText(" · " + albumTitle);
        headInfo.setTextColor(0xFF8A8A93);
        headInfo.setTextSize(12.5f);
        headInfo.setMaxLines(1);
        headInfo.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hilp = new LinearLayout.LayoutParams(0, -2, 1f);
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

        // 页头信息行（普查接线）：封面＋简介＋热度/播放/收藏。
        // DramaAlbumDetail 的 cover/intro/hotScoreText/playCnt/
        // followedCnt 早已解析，此前头部只用 title＋集数、整组
        // 字段零读取。有任一才建行。
        java.util.List<String> metaParts = new java.util.ArrayList<>();
        if (album.hotScoreText != null && !album.hotScoreText.isEmpty()) {
            metaParts.add(album.hotScoreText);
        }
        if (album.playCnt > 0) {
            metaParts.add("播放 " + HongguoFeedActivity.HgTimeTextCount.format(album.playCnt));
        }
        if (album.followedCnt >= 0) {
            metaParts.add("收藏 " + HongguoFeedActivity.HgTimeTextCount.format(album.followedCnt));
        }
        boolean hasCover = album.cover != null && !album.cover.isEmpty();
        boolean hasIntro = album.intro != null && !album.intro.isEmpty();
        if (hasCover || hasIntro || !metaParts.isEmpty()) {
            LinearLayout meta = new LinearLayout(activity);
            meta.setOrientation(LinearLayout.HORIZONTAL);
            meta.setPadding(dp.applyAsInt(18), dp.applyAsInt(4),
                    dp.applyAsInt(18), dp.applyAsInt(8));
            if (hasCover) {
                ImageView cov = new ImageView(activity);
                cov.setScaleType(ImageView.ScaleType.CENTER_CROP);
                HgCoverClip.round(cov, dp.applyAsInt(6));
                Glide.with(activity).load(album.cover).into(cov);
                meta.addView(cov, new LinearLayout.LayoutParams(
                        dp.applyAsInt(54), dp.applyAsInt(72)));
            }
            LinearLayout texts = new LinearLayout(activity);
            texts.setOrientation(LinearLayout.VERTICAL);
            if (hasIntro) {
                TextView intro = new TextView(activity);
                intro.setText(album.intro);
                intro.setTextColor(0xFF55555C);
                intro.setTextSize(12.5f);
                intro.setMaxLines(2);
                intro.setEllipsize(android.text.TextUtils.TruncateAt.END);
                texts.addView(intro);
            }
            if (!metaParts.isEmpty()) {
                TextView metaText = new TextView(activity);
                metaText.setText(String.join(" · ", metaParts));
                metaText.setTextColor(0xFF8A8A93);
                metaText.setTextSize(12);
                metaText.setMaxLines(1);
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
                if (hasIntro) mlp.topMargin = dp.applyAsInt(5);
                texts.addView(metaText, mlp);
            }
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (hasCover) tlp.leftMargin = dp.applyAsInt(10);
            meta.addView(texts, tlp);
            root.addView(meta);
        }

        if (album.rows.isEmpty()) {
            // 空态（C-23）：合集行表为空时此前开出一张只有标题的
            // 空白单——挂一行空态文案，与选集单 C-21 同口径。
            TextView empty = new TextView(activity);
            empty.setText("暂无内容");
            empty.setTextColor(0xFF8A8A93);
            empty.setTextSize(13.5f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp.applyAsInt(18), dp.applyAsInt(26),
                    dp.applyAsInt(18), dp.applyAsInt(26));
            root.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        } else {
            RecyclerView list = new RecyclerView(activity);
            list.setLayoutManager(new LinearLayoutManager(activity));
            list.setPadding(dp.applyAsInt(18), dp.applyAsInt(2),
                    dp.applyAsInt(18), dp.applyAsInt(10));
            list.setClipToPadding(false);
            list.setAdapter(new RowAdapter(activity, album, currentIndex, idx -> {
                dialog.dismiss();
                onPick.onPick(idx);
            }, dp));
            root.addView(list, new LinearLayout.LayoutParams(-1, -2));
            if (currentIndex > 0) list.scrollToPosition(currentIndex);
        }

        // 脚（定稿 .s-foot）：合集条文案＝collBarText 口径
        LinearLayout foot = new LinearLayout(activity);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setBackgroundColor(0xFFFBFAF8);
        View footLine = new View(activity);
        footLine.setBackgroundColor(0x121B1B1F);
        foot.addView(footLine, new LinearLayout.LayoutParams(-1, 1));
        TextView footText = new TextView(activity);
        footText.setText("合集 · " + albumTitle
                + (count.isEmpty() ? "" : " · " + count));
        footText.setTextColor(0xFF8A8A93);
        footText.setTextSize(12.5f);
        footText.setGravity(Gravity.CENTER);
        footText.setMaxLines(1);
        footText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        footText.setPadding(dp.applyAsInt(18), dp.applyAsInt(11), dp.applyAsInt(18),
                dp.applyAsInt(13) + navBarHeight(activity));
        foot.addView(footText);
        root.addView(foot);

        // 几何（回炉定根，账 fix168-episodesheet-diag）：列表入
        // 根即 (-1,-2) wrap 自然高，行高由行内容构造决定，整单
        // 零实量零回写（旧 fitListHeight 已整步摘除）。
        dialog.setContentView(root);
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.setBackgroundColor(Color.TRANSPARENT);
        }
        dialog.show();
    }

    private static final class RowAdapter extends RecyclerView.Adapter<RowAdapter.H> {
        private final AppCompatActivity activity;
        private final DramaAlbumDetail album;
        private final int currentIndex;
        private final OnPick onPick;
        private final java.util.function.IntUnaryOperator dp;

        RowAdapter(AppCompatActivity activity, DramaAlbumDetail album, int currentIndex,
                   OnPick onPick, java.util.function.IntUnaryOperator dp) {
            this.activity = activity;
            this.album = album;
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
            // 定稿 .hrow：整卡一行（白底＋line 边＋圆 16、内距 10、
            // 行距 8），拇图 58×76 圆 10、信息列、38 红圆钮
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
                    dp.applyAsInt(58), dp.applyAsInt(76)));
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
            DramaItem item = album.rows.get(position);
            boolean on = position == currentIndex;
            h.name.setText(item.getTitle() == null ? "" : item.getTitle());
            if (item.getCoverUrl() != null && !item.getCoverUrl().isEmpty()) {
                HgCoverClip.round(h.cover, dp.applyAsInt(10));
                Glide.with(activity).load(item.getCoverUrl()).into(h.cover);
            } else {
                h.cover.setImageDrawable(null);
            }
            // 副行（草稿 dur·cat 两段）：行回包无时长字段，首段以
            // 集数文案代（有才显），次段取首枚标签；当前行冠正在播放
            StringBuilder sb = new StringBuilder();
            if (on) sb.append("正在播放");
            boolean rowFinished = item.getSeriesStatus() != null
                    && item.getSeriesStatus() == 1;
            String rowCount = HgPanelLogic.countText(rowFinished, item.getEpisodeCount());
            if (!rowCount.isEmpty()) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(rowCount);
            }
            if (item.getTags() != null && !item.getTags().isEmpty()
                    && item.getTags().get(0) != null && !item.getTags().get(0).isEmpty()) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(item.getTags().get(0));
            }
            h.sub.setText(sb.toString());
            h.sub.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
            h.itemView.setOnClickListener(v -> onPick.onPick(position));
        }

        @Override
        public int getItemCount() {
            return album.rows.size();
        }
    }
}
