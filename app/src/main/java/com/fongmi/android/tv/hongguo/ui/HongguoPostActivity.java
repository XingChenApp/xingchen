package com.fongmi.android.tv.hongguo.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.hongguo.api.HgCallback;
import com.fongmi.android.tv.hongguo.drama.DramaClient;
import com.fongmi.android.tv.hongguo.drama.DramaDetail;
import com.fongmi.android.tv.hongguo.drama.DramaWork;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 帖子页（2026-10-31 W-08 他亲裁「接上呗」恢复落点；旧帖子页
 * 2026-10-29 落地、2026-10-30 重建时随旧栈整删，本页按其定版
 * 形态在新栈复原，搜索帖卡自此有落点）。
 *
 * <p>官方去处定版：帖子回包自带 post_schema＝dragon8662://
 * seriesVideoList?post_id=…&amp;post_type=22&amp;title=…（存档 tab11
 * 真回包逐字），即帖子有独立承载面、按 post_id 进页；正文/图集/
 * 关联剧/计数在列表回包里全量自带（post_data 的 pure_content＋
 * content 字符串化 materials、digg/reply/favorite 计数），本页直接
 * 用条目数据成形、不另拉正文。关联剧卡才回源（DramaClient
 * .fetchDetails 补标题/封面/集数），回不到不摆空卡、点卡进播放页。</p>
 *
 * <p>构成（旧页定版逐项）：作者行（头像 40、名 15 粗、时间 12 灰，
 * yyyy-MM-dd HH:mm）＋标题（17 粗，与正文同文只显正文、防一句
 * 两遍）＋正文（15、行距 1.35）＋图集逐张（全宽、原图比、张距 8）
 * ＋关联剧卡（浅灰底、封面 48×64、剧名 14 粗、集数 12 灰）＋计数行
 * （点赞/收藏/评论 13 灰，只展示）。白底深字与搜索/作者页同族；
 * 顶栏只放圆返回钮（新栈各页同款），不自带标题字（旧页同口径）。</p>
 *
 * <p>未落（记账，与旧页同口径）：帖子评论区——帖子评论的
 * group_type 候选（UgcCommentGroupType.Post=11）未实拉验通，不接
 * 现行评论链硬凑；点赞/收藏动作——帖子维度提交口未验通，计数行
 * 只展示、不摆假按钮。</p>
 */
public class HongguoPostActivity extends AppCompatActivity {

    private static final String EXTRA_POST_ID = "hg_post_id";
    private static final String EXTRA_TITLE = "hg_post_title";
    private static final String EXTRA_TEXT = "hg_post_text";
    private static final String EXTRA_IMAGES = "hg_post_images";
    private static final String EXTRA_SERIES = "hg_post_series_ids";
    private static final String EXTRA_DIGG = "hg_post_digg";
    private static final String EXTRA_COLLECT = "hg_post_collect";
    private static final String EXTRA_COMMENTS = "hg_post_comments";
    private static final String EXTRA_AUTHOR_NAME = "hg_post_author_name";
    private static final String EXTRA_AUTHOR_AVATAR = "hg_post_author_avatar";
    private static final String EXTRA_AUTHOR_UID = "hg_post_author_uid";
    private static final String EXTRA_TIME = "hg_post_time";

    /** 从搜索帖行进页：料全取条目本身（postBody 缺失时降级为只有标题）。 */
    public static void start(Context context, DramaWork post, String authorUid) {
        Intent intent = new Intent(context, HongguoPostActivity.class);
        intent.putExtra(EXTRA_POST_ID, post.id);
        intent.putExtra(EXTRA_TITLE, post.title);
        DramaWork.PostBody body = post.postBody;
        intent.putExtra(EXTRA_TEXT, body == null ? "" : body.text);
        if (body != null) {
            intent.putStringArrayListExtra(EXTRA_IMAGES, new ArrayList<>(body.imageUrls));
            intent.putStringArrayListExtra(EXTRA_SERIES, new ArrayList<>(body.seriesIds));
            intent.putExtra(EXTRA_COLLECT, body.collectCount);
            intent.putExtra(EXTRA_COMMENTS, body.commentCount);
        }
        intent.putExtra(EXTRA_DIGG, post.diggCount);
        intent.putExtra(EXTRA_AUTHOR_NAME, post.authorName);
        intent.putExtra(EXTRA_AUTHOR_AVATAR, post.authorAvatar);
        intent.putExtra(EXTRA_AUTHOR_UID, authorUid == null ? "" : authorUid);
        intent.putExtra(EXTRA_TIME, post.createTime);
        HgTransition.push(context, intent);
    }

    /** 销毁旗（与 Author/Detail 同口径）：回包落地先查，销毁后不动视图。 */
    private volatile boolean destroyed;
    private LinearLayout seriesBox;

    @Override
    public void finish() {
        super.finish();
        HgTransition.applyPushExit(this);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int navBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private GradientDrawable roundBg(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HgWindowStyle.apply(this, true, true);
        Intent intent = getIntent();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);

        // 顶栏：只放圆返回钮（作者页同款件），不自带标题字
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), statusBarHeight() + dp(8), dp(14), dp(2));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_drama_back);
        back.setColorFilter(0xFF1B1B1F);
        back.setBackground(roundBg(0x0D1B1B1F, dp(19)));
        back.setPadding(dp(9), dp(9), dp(9), dp(9));
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(38), dp(38)));
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(12), dp(16), dp(40) + navBarHeight());
        scroll.addView(col);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        // 作者行（头像 40/名 15 粗/时间 12 灰）
        String authorName = orEmpty(intent.getStringExtra(EXTRA_AUTHOR_NAME));
        String authorAvatar = orEmpty(intent.getStringExtra(EXTRA_AUTHOR_AVATAR));
        LinearLayout authorRow = new LinearLayout(this);
        authorRow.setOrientation(LinearLayout.HORIZONTAL);
        authorRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView avatar = new ImageView(this);
        avatar.setBackground(roundBg(0xFFE8E6E2, dp(20)));
        authorRow.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));
        if (!authorAvatar.isEmpty()) {
            Glide.with(this).load(authorAvatar).circleCrop().into(avatar);
        }
        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nclp = new LinearLayout.LayoutParams(0, -2, 1f);
        nclp.leftMargin = dp(10);
        TextView nameView = new TextView(this);
        nameView.setText(authorName);
        nameView.setTextColor(0xFF1A1A1A);
        nameView.setTextSize(15);
        nameView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        nameCol.addView(nameView);
        TextView timeView = new TextView(this);
        long time = intent.getLongExtra(EXTRA_TIME, 0);
        if (time > 0) {
            timeView.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                    .format(new Date(time * 1000L)));
        } else {
            timeView.setVisibility(View.GONE);
        }
        timeView.setTextColor(0xFF999999);
        timeView.setTextSize(12);
        LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(-2, -2);
        tvlp.topMargin = dp(2);
        nameCol.addView(timeView, tvlp);
        authorRow.addView(nameCol, nclp);
        col.addView(authorRow);

        String title = orEmpty(intent.getStringExtra(EXTRA_TITLE));
        String text = orEmpty(intent.getStringExtra(EXTRA_TEXT));
        List<String> images = intent.getStringArrayListExtra(EXTRA_IMAGES);
        boolean hasImages = images != null && !images.isEmpty();

        // 标题与正文同文时只显正文，避免一进页同一句话出现两遍
        if (!title.isEmpty() && !title.equals(text)) {
            TextView titleView = new TextView(this);
            titleView.setText(title);
            titleView.setTextColor(0xFF1A1A1A);
            titleView.setTextSize(17);
            titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            titleView.setLineSpacing(0, 1.25f);
            LinearLayout.LayoutParams ttlp = new LinearLayout.LayoutParams(-1, -2);
            ttlp.topMargin = dp(14);
            col.addView(titleView, ttlp);
        }
        TextView textView = new TextView(this);
        if (!text.isEmpty()) {
            textView.setText(text);
            textView.setTextColor(0xFF333333);
            textView.setTextSize(15);
            textView.setLineSpacing(0, 1.35f);
        } else if (title.isEmpty() && !hasImages) {
            // 料残缺到正文/图/标题全无：明说打不开，不摆一页空白
            textView.setText("帖子内容为空");
            textView.setTextColor(0xFF999999);
            textView.setTextSize(14);
        } else {
            textView.setVisibility(View.GONE);
        }
        LinearLayout.LayoutParams txlp = new LinearLayout.LayoutParams(-1, -2);
        txlp.topMargin = dp(8);
        col.addView(textView, txlp);

        // 图集逐张（全宽、原图比）
        if (images != null) {
            for (String url : images) {
                if (url == null || url.isEmpty()) continue;
                ImageView iv = new ImageView(this);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
                ilp.topMargin = dp(8);
                col.addView(iv, ilp);
                Glide.with(this).load(url).into(iv);
            }
        }

        // 关联剧（回源补卡，没回到不摆）
        seriesBox = new LinearLayout(this);
        seriesBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(-1, -2);
        sblp.topMargin = dp(8);
        col.addView(seriesBox, sblp);
        List<String> seriesIds = intent.getStringArrayListExtra(EXTRA_SERIES);
        if (seriesIds != null && !seriesIds.isEmpty()) loadSeriesCards(seriesIds);

        // 计数行（只展示：点赞/收藏/评论动作口未验通，不摆假按钮）
        LinearLayout counts = new LinearLayout(this);
        counts.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = dp(20);
        counts.addView(countText("点赞 " + HongguoFeedActivity.HgTimeTextCount.format(
                intent.getLongExtra(EXTRA_DIGG, 0))), new LinearLayout.LayoutParams(-2, -2));
        TextView collect = countText("收藏 " + HongguoFeedActivity.HgTimeTextCount.format(
                intent.getLongExtra(EXTRA_COLLECT, 0)));
        LinearLayout.LayoutParams colp = new LinearLayout.LayoutParams(-2, -2);
        colp.leftMargin = dp(24);
        counts.addView(collect, colp);
        TextView comment = countText("评论 " + HongguoFeedActivity.HgTimeTextCount.format(
                intent.getLongExtra(EXTRA_COMMENTS, 0)));
        LinearLayout.LayoutParams cmlp = new LinearLayout.LayoutParams(-2, -2);
        cmlp.leftMargin = dp(24);
        counts.addView(comment, cmlp);
        col.addView(counts, clp);
    }

    private TextView countText(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFF666666);
        t.setTextSize(13);
        return t;
    }

    /** 关联剧卡：批量详情回源补标题/封面/集数，没回到的剧不摆空卡。 */
    private void loadSeriesCards(List<String> seriesIds) {
        DramaClient.fetchDetails(seriesIds, new HgCallback<Map<String, DramaDetail>>() {
            @Override
            public void onSuccess(Map<String, DramaDetail> map) {
                if (destroyed || map == null) return;
                for (String sid : seriesIds) {
                    DramaDetail detail = map.get(sid);
                    if (detail == null || detail.getTitle() == null) continue;
                    addSeriesCard(sid, detail);
                }
            }

            @Override
            public void onError(int code, String msg) {
                // 回源失败就不出卡（帖子本体照常看），不摆只有 id 的空卡
            }
        });
    }

    private void addSeriesCard(String seriesId, DramaDetail detail) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(roundBg(0xFFF5F5F5, 0));
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.topMargin = dp(8);
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        HgCoverClip.round(cover, dp(6));
        card.addView(cover, new LinearLayout.LayoutParams(dp(48), dp(64)));
        if (detail.getCoverUrl() != null && !detail.getCoverUrl().isEmpty()) {
            Glide.with(this).load(detail.getCoverUrl()).into(cover);
        }
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tclp = new LinearLayout.LayoutParams(0, -2, 1f);
        tclp.leftMargin = dp(12);
        TextView titleView = new TextView(this);
        titleView.setText(detail.getTitle());
        titleView.setTextColor(0xFF1A1A1A);
        titleView.setTextSize(14);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(titleView);
        if (detail.getEpisodeCount() > 0) {
            TextView meta = new TextView(this);
            meta.setText("全" + detail.getEpisodeCount() + "集");
            meta.setTextColor(0xFF999999);
            meta.setTextSize(12);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
            mlp.topMargin = dp(4);
            textCol.addView(meta, mlp);
        }
        card.addView(textCol, tclp);
        card.setOnClickListener(v -> HongguoFeedActivity.startSeries(
                this, seriesId, detail.getTitle(), 0));
        seriesBox.addView(card, cardLp);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
