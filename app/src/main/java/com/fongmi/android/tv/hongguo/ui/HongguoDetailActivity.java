package com.fongmi.android.tv.hongguo.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import android.text.method.LinkMovementMethod;
import com.fongmi.android.tv.api.HongguoCommentApi;
import com.fongmi.android.tv.hongguo.api.HgCallback;
import com.fongmi.android.tv.hongguo.drama.DramaClient;
import com.fongmi.android.tv.hongguo.drama.DramaDetail;
import com.fongmi.android.tv.hongguo.drama.DramaItem;
import com.fongmi.android.tv.hongguo.drama.DramaUserProfile;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 剧详情页（2026-10-30 UI 同步批照定稿整页重排）：整页随海报主色走
 * 暖棕渐变；顶栏返回/分享/竖三点；海报＋剧名＋集数；作者行（无关注钮）；
 * 大收藏钮；三格数据（红果热度/累计收藏/上架时间——数据面没有的整格
 * 不显，不编数：上架时间详情面无源，见 hongguo-ui-sync 账）；基本信息
 * （简介可展开＋标签筹＋出品方）；剧评（分数卡只展示、无打星；评论卡
 * 横排、只看、无点赞心、无写剧评）；相关作品横排（官方 plan 口
 * 547 cell 的同 IP 绑定表＋合集兄弟季补齐，两路皆空整段不显）；
 * 底部固定开始播放＋收藏。详情页无选集入口（定稿）。
 */
public class HongguoDetailActivity extends AppCompatActivity {

    public static final String EXTRA_SERIES_ID = "hg_detail_series";
    public static final String EXTRA_TITLE = "hg_detail_title";
    public static final String EXTRA_ALBUM_ID = "hg_detail_album";

    public static void start(Context context, String seriesId, String title, String albumId) {
        Intent intent = new Intent(context, HongguoDetailActivity.class);
        intent.putExtra(EXTRA_SERIES_ID, seriesId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_ALBUM_ID, albumId);
        HgTransition.push(context, intent);
    }

    @Override
    public void finish() {
        super.finish();
        HgTransition.applyPushExit(this);
    }

    /** 定稿剧名字体：Noto Serif SC 900 的系统近似（serif 粗体）。 */
    private static final android.graphics.Typeface SERIF_BLACK =
            android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD);

    private String seriesId;
    private String albumId;
    private DramaDetail detail;
    private int currentEpisode = 1;

    private FrameLayout rootFrame;
    private ScrollView scroll;
    private ImageView backdropView;
    private TextView titleText;
    private TextView countText;
    private ImageView coverView;
    private LinearLayout authorRow;
    private ImageView authorAvatar;
    private TextView authorName;
    private LinearLayout bigFavBtn;
    private ImageView bigFavStar;
    private TextView bigFavText;
    private LinearLayout reserveBtn;
    private TextView reserveText;
    private LinearLayout statsRow;
    private TextView descText;
    private String descFull;
    private boolean descExpanded;
    private FlowLayout tagsRow;
    private TextView publisherText;
    private TextView dueText;
    private android.widget.HorizontalScrollView celebScroll;
    private LinearLayout celebRow;
    private TextView scoreCard;
    private TextView scoreMidCount;
    private LinearLayout scoreBox;
    private RecyclerView commentList;
    private CommentCardAdapter commentAdapter;
    /** 主详情加载失败态（簇六 C-08）：整面居中「取数据失败，点按重试」。 */
    private TextView loadStateText;
    private LinearLayout relatedBox;
    private RecyclerView relatedList;
    private RelatedAdapter relatedAdapter;
    /** 相关作品两路快照：null＝未回；空表＝回了但无条目。 */
    private List<DramaDetail.RelatedWork> planRelated;
    private List<DramaDetail.RelatedWork> albumRelated;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hg-detail");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean destroyed;

    /** 池投递唯一收口（与竖屏页同型，P0 定根见 HongguoFeedActivity.safeExecute）。 */
    private void safeExecute(Runnable task) {
        if (destroyed || worker.isShutdown()) return;
        try {
            worker.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
        }
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
        HgWindowStyle.apply(this, false, false);
        seriesId = getIntent().getStringExtra(EXTRA_SERIES_ID);
        albumId = getIntent().getStringExtra(EXTRA_ALBUM_ID);

        rootFrame = new FrameLayout(this);
        applyGradient(null);
        // 海报糊底（定稿 .d-bg）：封面放大垫底，31+ 用系统模糊
        // （半径 34）、低版本退化为半透明原图＋渐变压暗。
        backdropView = new ImageView(this);
        backdropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdropView.setAlpha(0.5f);
        backdropView.setScaleX(1.4f);
        backdropView.setScaleY(1.4f);
        // 定稿 .d-bg 滤镜全套：blur 34＋saturate(1.15)＋brightness(.72)
        // （打回⑨：旧落码只有效果里的模糊一段，饱和/亮度未落）
        android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
        cm.setSaturation(1.15f);
        android.graphics.ColorMatrix dim = new android.graphics.ColorMatrix(new float[]{
                0.72f, 0, 0, 0, 0,
                0, 0.72f, 0, 0, 0,
                0, 0, 0.72f, 0, 0,
                0, 0, 0, 1, 0});
        cm.postConcat(dim);
        android.graphics.ColorMatrixColorFilter toneFilter =
                new android.graphics.ColorMatrixColorFilter(cm);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            backdropView.setRenderEffect(android.graphics.RenderEffect.createChainEffect(
                    android.graphics.RenderEffect.createBlurEffect(
                            34, 34, android.graphics.Shader.TileMode.CLAMP),
                    android.graphics.RenderEffect.createColorFilterEffect(toneFilter)));
        } else {
            backdropView.setColorFilter(toneFilter);
        }
        rootFrame.addView(backdropView, new FrameLayout.LayoutParams(-1, -1));

        scroll = new ScrollView(this);
        // 件八定根（逐帧账 D6–D8＋hongguo-detail-check 双图实证）：
        // 不许 fillViewport 把列强量成视口高——见下方 col 的 gravity
        // 注释，列被拉高＋内容不足视口高是首开整列塌陷的发作条件。
        scroll.setFillViewport(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        // 程序化 LinearLayout 的默认 gravity 含 CENTER_VERTICAL：
        // 竖列总高小于列高时整列竖向居中（AOSP layoutVertical 按
        // majorGravity 摆栈）。本页初始内容大半 GONE、总高远小于
        // 视口，旧骨架又开 fillViewport 把列拉满视口高——首量即把
        // 整列居中：顶栏钮落到屏高近半、其余孩子被挤出可视区，
        // bind 后内容仍不足视口高时居中不解除（1.4.68 录屏里两进
        // 详情页全程空壳即此）；切后台回前台偶然归位是系统全量重
        // 排撞上内容已长高、不是治法。钉死 TOP：列永远自顶摆栈。
        col.setGravity(Gravity.TOP);
        col.setPadding(0, statusBarHeight() + dp(6), 0, dp(28) + navBarHeight());

        // 顶栏（定稿 .d-top）：三枚玻璃圆钮 38dp（底 rgba(20,12,6,
        // .28)＋内环），行内距 14。
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), 0, dp(14), 0);
        ImageView back = glassBtn(R.drawable.ic_drama_back);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(38), dp(38)));
        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView share = glassBtn(android.R.drawable.ic_menu_share);
        share.setOnClickListener(v -> shareDrama());
        LinearLayout.LayoutParams shlp = new LinearLayout.LayoutParams(dp(38), dp(38));
        shlp.rightMargin = dp(10);
        top.addView(share, shlp);
        ImageView more = glassBtn(R.drawable.ic_drama_more);
        more.setOnClickListener(v -> showMoreSheet());
        top.addView(more, new LinearLayout.LayoutParams(dp(38), dp(38)));
        col.addView(top);

        // 头部（定稿 .d-head）：海报 106×147 圆 12＋信息列（剧名
        // Serif 21.5/集数行/作者行/大收藏钮全列内），行内距 18。
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = dp(12);
        coverView = new ImageView(this);
        coverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverView.setBackground(roundBg(0x33FFFFFF, dp(12)));
        head.addView(coverView, new LinearLayout.LayoutParams(dp(106), dp(147)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -1, 1f);
        ilp.leftMargin = dp(14);
        titleText = new TextView(this);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(21.5f);
        titleText.setTypeface(SERIF_BLACK);
        titleText.setLineSpacing(0, 1.06f);
        titleText.setMaxLines(3);
        titleText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        String t = getIntent().getStringExtra(EXTRA_TITLE);
        titleText.setText(t == null ? "" : t);
        info.addView(titleText);
        countText = new TextView(this);
        countText.setTextColor(0x9FFFFFFF);
        countText.setTextSize(13);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = dp(5);
        info.addView(countText, clp);
        head.addView(info, ilp);
        col.addView(head, hlp);

        // 作者行（信息列内：头像 24＋名 14sp＋chevron，无关注钮；
        // 有 uid 才可点进作者页）
        authorRow = new LinearLayout(this);
        authorRow.setOrientation(LinearLayout.HORIZONTAL);
        authorRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams arlp = new LinearLayout.LayoutParams(-1, -2);
        arlp.topMargin = dp(10);
        authorAvatar = new ImageView(this);
        authorAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        authorRow.addView(authorAvatar, new LinearLayout.LayoutParams(dp(24), dp(24)));
        authorName = new TextView(this);
        authorName.setTextColor(0xEBFFFFFF);
        authorName.setTextSize(14);
        authorName.setMaxLines(1);
        authorName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams anlp = new LinearLayout.LayoutParams(0, -2, 1f);
        anlp.leftMargin = dp(8);
        authorRow.addView(authorName, anlp);
        ImageView authorArrow = new ImageView(this);
        authorArrow.setImageResource(R.drawable.ic_drama_back);
        authorArrow.setColorFilter(0x80FFFFFF);
        authorArrow.setRotation(180);
        authorRow.addView(authorArrow, new LinearLayout.LayoutParams(dp(14), dp(14)));
        authorRow.setVisibility(View.GONE);
        info.addView(authorRow, arlp);

        // 大收藏钮（信息列底，金渐变：星 17＋字 15/700 字距 .06em；
        // 权重空格顶到底与海报底对齐，定稿 margin-top:auto）
        View infoSpacer = new View(this);
        info.addView(infoSpacer, new LinearLayout.LayoutParams(-1, 0, 1f));
        bigFavBtn = new LinearLayout(this);
        bigFavBtn.setOrientation(LinearLayout.HORIZONTAL);
        bigFavBtn.setGravity(Gravity.CENTER);
        bigFavBtn.setPadding(0, dp(11), 0, dp(11));
        bigFavStar = new ImageView(this);
        bigFavStar.setImageResource(R.drawable.ic_drama_star);
        bigFavBtn.addView(bigFavStar, new LinearLayout.LayoutParams(dp(17), dp(17)));
        bigFavText = new TextView(this);
        bigFavText.setTextSize(15);
        bigFavText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        bigFavText.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams bftlp = new LinearLayout.LayoutParams(-2, -2);
        bftlp.leftMargin = dp(6);
        bigFavBtn.addView(bigFavText, bftlp);
        bigFavBtn.setOnClickListener(v -> toggleFav());
        info.addView(bigFavBtn);

        // 预约钮（未上线剧专属，缺省藏、bind 见 hasAppointment 才亮）：
        // 官方未上线剧整页形态未抠到（批三 A 账 R2），落点取头部信息
        // 列收藏大钮下方同列同宽，是全页唯一预约动作；文案 预约/已预约
        // 与状态色对借官方本体钮 ks3 色对（橘底白字/透明白底＋#66FFFFFF 字）。
        reserveBtn = new LinearLayout(this);
        reserveBtn.setOrientation(LinearLayout.HORIZONTAL);
        reserveBtn.setGravity(Gravity.CENTER);
        reserveBtn.setPadding(0, dp(11), 0, dp(11));
        reserveText = new TextView(this);
        reserveText.setTextSize(15);
        reserveText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        reserveText.setLetterSpacing(0.06f);
        reserveBtn.addView(reserveText);
        reserveBtn.setOnClickListener(v -> toggleReserve());
        reserveBtn.setVisibility(View.GONE);
        LinearLayout.LayoutParams rblp = new LinearLayout.LayoutParams(-1, -2);
        rblp.topMargin = dp(10);
        info.addView(reserveBtn, rblp);
        // 上线时间行（普查接线）：dueStartText 回包已解析此前零
        // 显示——未上线剧只见预约钮、不知道何时上。挂预约钮同列
        // 下方，文案用回包原文，有预约且有文案才显。
        dueText = new TextView(this);
        dueText.setTextColor(0x99FFFFFF);
        dueText.setTextSize(12);
        dueText.setVisibility(View.GONE);
        LinearLayout.LayoutParams dtlp = new LinearLayout.LayoutParams(-1, -2);
        dtlp.topMargin = dp(6);
        info.addView(dueText, dtlp);

        // 三格数据（有数据的格才建；上架时间无源恒不显，见账）
        statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(-1, -2);
        srlp.leftMargin = dp(18);
        srlp.rightMargin = dp(18);
        srlp.topMargin = dp(20);
        statsRow.setVisibility(View.GONE);
        col.addView(statsRow, srlp);

        // 基本信息（定稿 .d-sec）：简介 14sp 行高 1.78、展开/收起是
        // 简介末尾的行内字（renderDesc）；标签筹换行排；出品方行。
        col.addView(sectionLabel("基本信息"));
        descText = new TextView(this);
        descText.setTextColor(0xD9FFFFFF);
        descText.setTextSize(14);
        descText.setLineSpacing(0, 1.48f);
        descText.setPadding(dp(18), 0, dp(18), 0);
        descText.setMovementMethod(LinkMovementMethod.getInstance());
        col.addView(descText);
        tagsRow = new FlowLayout(this);
        tagsRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams tglp = new LinearLayout.LayoutParams(-1, -2);
        tglp.leftMargin = dp(18);
        tglp.rightMargin = dp(18);
        tglp.topMargin = dp(14);
        col.addView(tagsRow, tglp);
        publisherText = new TextView(this);
        publisherText.setTextColor(0x80FFFFFF);
        publisherText.setTextSize(12.5f);
        publisherText.setPadding(dp(18), 0, dp(18), 0);
        publisherText.setVisibility(View.GONE);
        LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(-1, -2);
        pblp.topMargin = dp(14);
        col.addView(publisherText, pblp);
        // 演员段（普查接线）：celebrities 回包早已解析、此前零渲染。
        // 横滑一排：圆头像＋昵称＋饰演行，没带整段不显。
        celebScroll = new android.widget.HorizontalScrollView(this);
        celebScroll.setHorizontalScrollBarEnabled(false);
        celebScroll.setVisibility(View.GONE);
        celebRow = new LinearLayout(this);
        celebRow.setOrientation(LinearLayout.HORIZONTAL);
        celebScroll.addView(celebRow, new ViewGroup.LayoutParams(-2, -2));
        LinearLayout.LayoutParams celp = new LinearLayout.LayoutParams(-1, -2);
        celp.leftMargin = dp(18);
        celp.rightMargin = dp(18);
        celp.topMargin = dp(14);
        col.addView(celebScroll, celp);

        // 剧评（定稿）：段头右带「全部剧评」入口（开全量评论面）；
        // 分数卡＝大分＋左线中列（红果分＋N 人评分）。右注两行是
        // 草稿演示语、无数据含义，不落。
        LinearLayout reviewHead = new LinearLayout(this);
        reviewHead.setOrientation(LinearLayout.HORIZONTAL);
        reviewHead.setGravity(Gravity.CENTER_VERTICAL);
        reviewHead.setPadding(0, 0, dp(18), 0);
        TextView reviewLabel = sectionLabel("剧评");
        reviewLabel.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        reviewHead.addView(reviewLabel);
        View rhSpacer = new View(this);
        reviewHead.addView(rhSpacer, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView allReviews = new TextView(this);
        allReviews.setText("全部剧评");
        allReviews.setTextColor(0x8CFFFFFF);
        allReviews.setTextSize(12.5f);
        reviewHead.addView(allReviews);
        ImageView allArrow = new ImageView(this);
        allArrow.setImageResource(R.drawable.ic_drama_back);
        allArrow.setColorFilter(0x8CFFFFFF);
        allArrow.setRotation(180);
        LinearLayout.LayoutParams aalp = new LinearLayout.LayoutParams(dp(11), dp(11));
        aalp.leftMargin = dp(3);
        reviewHead.addView(allArrow, aalp);
        View.OnClickListener openAll = v -> openCommentsSheet();
        allReviews.setOnClickListener(openAll);
        allArrow.setOnClickListener(openAll);
        LinearLayout.LayoutParams rhlp = new LinearLayout.LayoutParams(-1, -2);
        rhlp.topMargin = dp(24);
        rhlp.bottomMargin = dp(12);
        col.addView(reviewHead, rhlp);
        scoreBox = new LinearLayout(this);
        scoreBox.setOrientation(LinearLayout.HORIZONTAL);
        scoreBox.setGravity(Gravity.CENTER_VERTICAL);
        scoreBox.setBackground(roundBgStroke(0x1AFFFFFF, dp(15), 0x12FFFFFF));
        scoreBox.setPadding(dp(16), dp(15), dp(16), dp(15));
        scoreBox.setVisibility(View.GONE);
        scoreCard = new TextView(this);
        scoreCard.setTextColor(Color.WHITE);
        scoreCard.setTextSize(33);
        scoreCard.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        scoreBox.addView(scoreCard);
        View scoreLine = new View(this);
        scoreLine.setBackgroundColor(0x24FFFFFF);
        LinearLayout.LayoutParams sllp = new LinearLayout.LayoutParams(1, dp(34));
        sllp.leftMargin = dp(15);
        scoreBox.addView(scoreLine, sllp);
        LinearLayout scoreMid = new LinearLayout(this);
        scoreMid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams smlp = new LinearLayout.LayoutParams(-2, -2);
        smlp.leftMargin = dp(15);
        TextView scoreMidTitle = new TextView(this);
        scoreMidTitle.setText("红果分");
        scoreMidTitle.setTextColor(0xEBFFFFFF);
        scoreMidTitle.setTextSize(13.5f);
        scoreMidTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        scoreMid.addView(scoreMidTitle);
        scoreMidCount = new TextView(this);
        scoreMidCount.setTextColor(0x8CFFFFFF);
        scoreMidCount.setTextSize(12);
        LinearLayout.LayoutParams smclp = new LinearLayout.LayoutParams(-2, -2);
        smclp.topMargin = dp(4);
        scoreMid.addView(scoreMidCount, smclp);
        scoreBox.addView(scoreMid, smlp);
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(-1, -2);
        sblp.leftMargin = dp(18);
        sblp.rightMargin = dp(18);
        col.addView(scoreBox, sblp);
        commentList = new RecyclerView(this);
        commentList.setLayoutManager(new LinearLayoutManager(this,
                LinearLayoutManager.HORIZONTAL, false));
        commentList.setNestedScrollingEnabled(false);
        commentList.setPadding(dp(18), 0, dp(18), 0);
        commentList.setClipToPadding(false);
        commentAdapter = new CommentCardAdapter();
        commentList.setAdapter(commentAdapter);
        commentList.setVisibility(View.GONE);
        LinearLayout.LayoutParams cmlp = new LinearLayout.LayoutParams(-1, -2);
        cmlp.topMargin = dp(12);
        col.addView(commentList, cmlp);

        // 相关作品（同合集兄弟季横排；无合集整段不显）
        relatedBox = new LinearLayout(this);
        relatedBox.setOrientation(LinearLayout.VERTICAL);
        relatedBox.setVisibility(View.GONE);
        relatedBox.addView(sectionLabel("相关作品"));
        relatedList = new RecyclerView(this);
        relatedList.setLayoutManager(new LinearLayoutManager(this,
                LinearLayoutManager.HORIZONTAL, false));
        relatedList.setNestedScrollingEnabled(false);
        relatedList.setPadding(dp(18), 0, dp(18), 0);
        relatedList.setClipToPadding(false);
        relatedAdapter = new RelatedAdapter();
        relatedList.setAdapter(relatedAdapter);
        relatedBox.addView(relatedList);
        col.addView(relatedBox);

        scroll.addView(col);
        rootFrame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // 页内失败态（簇六 C-08，账 D28）：主详情加载失败时整面
        // 居中提示＋点按重试，与作者页 stateText 同型口径（失败不
        // 许装空、要有重试出口）；此前只 Toast 一闪、页面停在
        // Intent 标题＋全空骨架，只能退出重进。成功 bind 时收起。
        loadStateText = new TextView(this);
        loadStateText.setTextColor(0xCCFFFFFF);
        loadStateText.setTextSize(14);
        loadStateText.setGravity(Gravity.CENTER);
        loadStateText.setPadding(dp(32), 0, dp(32), 0);
        loadStateText.setVisibility(View.GONE);
        loadStateText.setOnClickListener(v -> {
            loadStateText.setVisibility(View.GONE);
            load();
        });
        rootFrame.addView(loadStateText, new FrameLayout.LayoutParams(-1, -1));

        // 底部固定条已摘（他 2026-10-31 亲令：最底下的开始播放跟收藏
        // 拿掉——收藏用头部金钮、播放入口另定）。页面滚到底即可，
        // 底部留白只剩手势区让位＋收尾间距（见 col 底部 padding）。

        setContentView(rootFrame);
        styleFav(HgStore.isFaved(seriesId));
        load();
        loadRelatedWorks();
    }

    private TextView sectionLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(24);
        lp.bottomMargin = dp(12);
        t.setLayoutParams(lp);
        return t;
    }

    /** 顶栏玻璃圆钮（定稿 .iconbtn 玻璃版）：深棕半透底＋内环。 */
    private ImageView glassBtn(int res) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x47140C06);
        g.setStroke(1, 0x24FFFFFF);
        iv.setBackground(g);
        iv.setPadding(dp(9), dp(9), dp(9), dp(9));
        return iv;
    }

    private GradientDrawable roundBgStroke(int color, float radius, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        g.setStroke(dp(1), strokeColor);
        return g;
    }

    /** 打开全量评论面（与评论卡点按同一路、同一集口径）。 */
    private void openCommentsSheet() {
        if (detail == null || detail.getEpisodes().isEmpty()) return;
        int idx = Math.min(Math.max(1, currentEpisode), detail.getEpisodes().size()) - 1;
        HgCommentsSheet.show(this, seriesId,
                detail.getEpisodes().get(idx).getVideoId(), detail.getTitle());
    }

    /**
     * 简介渲染（定稿）：折叠三行、末尾行内跟「展开」粗体白字；展开
     * 全文末尾跟「收起」。折叠靠布局后取第三行行尾裁字拼回，真行内、
     * 不是独立行小钮。
     */
    private void renderDesc() {
        if (descText == null || descFull == null) return;
        boolean needToggle = descFull.length() > 90;
        if (!needToggle) {
            descText.setMaxLines(3);
            descText.setText(descFull);
            return;
        }
        if (descExpanded) {
            descText.setMaxLines(Integer.MAX_VALUE);
            descText.setText(withToggleSuffix(descFull, "收起"));
            return;
        }
        descText.setMaxLines(3);
        descText.setText(descFull);
        descText.post(() -> {
            if (descExpanded || descText == null) return;
            android.text.Layout layout = descText.getLayout();
            if (layout == null || layout.getLineCount() < 3) return;
            int end = layout.getLineEnd(2);
            if (end <= 0 || end >= descFull.length()) return;
            int cut = Math.max(0, end - 4);
            String head = descFull.substring(0, cut).trim() + "…";
            descText.setText(withToggleSuffix(head, "展开"));
        });
    }

    private CharSequence withToggleSuffix(String body, String label) {
        android.text.SpannableString s = new android.text.SpannableString(body + " " + label);
        int start = body.length() + 1;
        int flags = android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        s.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                start, s.length(), flags);
        s.setSpan(new android.text.style.ForegroundColorSpan(Color.WHITE),
                start, s.length(), flags);
        s.setSpan(new android.text.style.ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                descExpanded = !descExpanded;
                renderDesc();
            }

            @Override
            public void updateDrawState(@NonNull android.text.TextPaint ds) {
                ds.setUnderlineText(false);
            }
        }, start, s.length(), flags);
        return s;
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        worker.shutdownNow();
        super.onDestroy();
    }

    // ---------- 动作 ----------

    private void toggleFav() {
        if (detail == null || seriesId == null) return;
        // 账号终定口径（#22）：收藏绑账号，未登录跳登录页、不落本地
        if (!com.fongmi.android.tv.hongguo.api.HgAccountStore.isLoggedIn()) {
            HongguoLoginActivity.start(this);
            return;
        }
        boolean now = HgStore.toggleFaved(seriesId);
        HgStore.putMeta(seriesId, detail.getTitle(), detail.getCoverUrl(),
                detail.getEpisodeCount());
        // 详情面判不出真人/漫剧/AI（无类型类字段），只落未上线预告态，
        // 其余不写、留给流条目在手的写点补（putType 判不出不冲已有值）。
        HgStore.putType(seriesId, detail.hasAppointment() ? HgStore.TYPE_PREVIEW : null);
        styleFav(now);
        // 账号动作上报追剧架（#22：未登录已在入口跳登录页）
        com.fongmi.android.tv.hongguo.api.HgAccountSync.reportShelf(seriesId, now);
    }

    /** 预约钮点击：翻转 reserved 集＋落元信息/预告类型码＋登录态上报。 */
    private void toggleReserve() {
        if (detail == null || seriesId == null) return;
        boolean now = HgStore.toggleReserved(seriesId);
        HgStore.putMeta(seriesId, detail.getTitle(), detail.getCoverUrl(),
                detail.getEpisodeCount());
        HgStore.putType(seriesId, HgStore.TYPE_PREVIEW);
        styleReserve(now);
        com.fongmi.android.tv.hongguo.api.HgAccountSync.reportReserve(seriesId, now);
    }

    /** 预约钮两态（官方本体钮 ks3 色对）：未预约橘底白字、已预约透明白底＋#66FFFFFF 字。 */
    private void styleReserve(boolean reserved) {
        if (reserveBtn == null) return;
        reserveText.setText(reserved ? "已预约" : "预约");
        if (reserved) {
            GradientDrawable g = roundBg(0x26FFFFFF, dp(11));
            g.setStroke(dp(1), 0x59FFFFFF);
            reserveBtn.setBackground(g);
            reserveText.setTextColor(0x66FFFFFF);
        } else {
            reserveBtn.setBackground(roundBg(0xFFF07D13, dp(11)));
            reserveText.setTextColor(Color.WHITE);
        }
    }

    private void styleFav(boolean faved) {
        if (bigFavBtn != null) {
            bigFavText.setText(faved ? "已收藏" : "收藏");
            // 定稿 .btn-fav-big 字/星色：未收藏 #FFF、已收藏 #FFF8EC
            //（旧恒深棕 0xFF402A12 是打回⑥）
            bigFavText.setTextColor(faved ? 0xFFFFF8EC : 0xFFFFFFFF);
            bigFavStar.setColorFilter(faved ? 0xFFFFF8EC : 0xFFFFFFFF);
            bigFavBtn.setBackground(goldBg(faved));
        }
    }

    /** 大收藏钮金渐变（定稿 .btn-fav-big）：未收藏暖金、已收藏亮金。 */
    private GradientDrawable goldBg(boolean faved) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                faved ? new int[]{0xF2F0C482, 0xF2CD9150}
                        : new int[]{0xD9D8A46A, 0xD9B27842});
        g.setCornerRadius(dp(11));
        return g;
    }

    private void shareDrama() {
        // 簇六 C-07（账 D25）：详情未回时也要有反馈，此前静默 return
        // 像钮死了；顶栏分享与更多菜单分享行共用此点，一处收口。
        if (detail == null) {
            Toast.makeText(this, "详情还没加载完，请稍后再试",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, detail.getTitle() == null ? ""
                : "我在看《" + detail.getTitle() + "》");
        startActivity(Intent.createChooser(send, "分享"));
    }

    private void showMoreSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(roundBg(0xFF1C1C1E, dp(18)));
        root.setPadding(dp(18), dp(14), dp(18), dp(14));
        root.addView(sheetRow("分享", v -> {
            dialog.dismiss();
            shareDrama();
        }));
        root.addView(sheetRow("清除本剧观看记录", v -> {
            dialog.dismiss();
            if (seriesId != null) {
                HgStore.removeHistory(seriesId);
                currentEpisode = 1;
                Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show();
            }
        }));
        dialog.setContentView(root);
        dialog.show();
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) sheet.setBackgroundColor(Color.TRANSPARENT);
    }

    private TextView sheetRow(String text, View.OnClickListener click) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFEEEEEE);
        t.setTextSize(15);
        t.setPadding(0, dp(13), 0, dp(13));
        t.setOnClickListener(click);
        return t;
    }

    // ---------- 数据 ----------

    private void load() {
        if (seriesId == null || seriesId.isEmpty()) {
            Toast.makeText(this, "剧集不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        DramaClient.fetchDetail(seriesId, new HgCallback<DramaDetail>() {
            @Override
            public void onSuccess(DramaDetail d) {
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    bind(d);
                });
            }

            @Override
            public void onError(int code, String msg) {
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    // 簇六 C-08：失败落页内态（点按重试），不再只
                    // Toast 一闪把用户留在空骨架上（账 D28）。
                    loadStateText.setText("取数据失败，点按重试");
                    loadStateText.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    /** 演员段渲染：圆头像＋昵称＋饰演行（subTitle 优先、roleName 兜底拼「饰 X」）。 */
    private void renderCelebrities(DramaDetail d) {
        celebRow.removeAllViews();
        java.util.List<DramaDetail.Celebrity> cast = d.getCelebrities();
        if (cast == null || cast.isEmpty()) {
            celebScroll.setVisibility(View.GONE);
            return;
        }
        celebScroll.setVisibility(View.VISIBLE);
        for (DramaDetail.Celebrity c : cast) {
            if (c.getNickname() == null || c.getNickname().isEmpty()) continue;
            LinearLayout unit = new LinearLayout(this);
            unit.setOrientation(LinearLayout.VERTICAL);
            unit.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView av = new ImageView(this);
            av.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (c.getAvatar() != null && !c.getAvatar().isEmpty()) {
                Glide.with(this).load(c.getAvatar()).circleCrop().into(av);
            }
            unit.addView(av, new LinearLayout.LayoutParams(dp(52), dp(52)));
            TextView nm = new TextView(this);
            nm.setText(c.getNickname());
            nm.setTextColor(0xEBFFFFFF);
            nm.setTextSize(12);
            nm.setMaxLines(1);
            LinearLayout.LayoutParams nmlp = new LinearLayout.LayoutParams(-2, -2);
            nmlp.topMargin = dp(5);
            unit.addView(nm, nmlp);
            String role = c.getSubTitle();
            if ((role == null || role.isEmpty()) && c.getRoleName() != null
                    && !c.getRoleName().isEmpty()) {
                role = "饰 " + c.getRoleName();
            }
            if (role != null && !role.isEmpty()) {
                TextView rl = new TextView(this);
                rl.setText(role);
                rl.setTextColor(0x80FFFFFF);
                rl.setTextSize(10.5f);
                rl.setMaxLines(1);
                unit.addView(rl);
            }
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(dp(64), -2);
            ulp.rightMargin = dp(6);
            celebRow.addView(unit, ulp);
        }
        if (celebRow.getChildCount() == 0) celebScroll.setVisibility(View.GONE);
    }

    private void bind(DramaDetail d) {
        detail = d;
        // 簇六 C-08：重试成功落定即收失败态（首开成功时本就 GONE）。
        if (loadStateText != null) loadStateText.setVisibility(View.GONE);
        renderRelated();
        applyGradient(d.getColorHex());
        if (d.getTitle() != null) titleText.setText(d.getTitle());
        if (d.getCoverUrl() != null && !d.getCoverUrl().isEmpty()) {
            HgCoverClip.round(coverView, dp(12));
            Glide.with(this).load(d.getCoverUrl()).into(coverView);
            Glide.with(this).load(d.getCoverUrl()).into(backdropView);
        }
        int total = d.getEpisodeCount() > 0 ? d.getEpisodeCount() : d.getEpisodes().size();
        boolean finished = d.getSeriesStatus() != null && d.getSeriesStatus() == 1;
        countText.setText(total > 0 ? HgPanelLogic.countText(finished, total) : "");

        // 作者行：有名才显；有 uid 才可点＋拉头像
        String name = d.getAuthorName();
        if (name != null && !name.isEmpty()) {
            authorRow.setVisibility(View.VISIBLE);
            authorName.setText(name);
            String uid = d.getAuthorUid();
            if (uid != null && !uid.isEmpty()) {
                authorRow.setOnClickListener(v -> HongguoAuthorActivity.start(
                        this, uid, d.getAuthorName(), null));
                DramaClient.fetchUserProfile(uid, new HgCallback<DramaUserProfile>() {
                    @Override
                    public void onSuccess(DramaUserProfile p) {
                        runOnUiThread(() -> {
                            if (destroyed || isFinishing()) return;
                            if (p.avatar != null && !p.avatar.isEmpty()) {
                                Glide.with(HongguoDetailActivity.this).load(p.avatar)
                                        .circleCrop().into(authorAvatar);
                            }
                            if ((name == null || name.isEmpty()) && p.name != null) {
                                authorName.setText(p.name);
                            }
                        });
                    }

                    @Override
                    public void onError(int code, String msg) {
                        // P-02：本拉取只补作者行头像/名（主详情已上屏、
                        // 作者名已有详情侧值），失败不出弹提示打扰，
                        // 但不许静默——记日志留痕，头像缺位即其可见态
                        android.util.Log.w("HongguoDetail",
                                "作者资料拉取失败 uid=" + uid + " code=" + code
                                        + " " + msg);
                    }
                });
            }
        }

        // 三格数据（热度/累计收藏有值才建格；上架时间无源不建）
        statsRow.removeAllViews();
        boolean anyStat = false;
        if (d.getHotScore() > 0) {
            addStat(HgTimeTextCount.format(d.getHotScore()), "红果热度");
            anyStat = true;
        }
        if (d.getFollowedCnt() >= 0) {
            addStat(HgTimeTextCount.format(d.getFollowedCnt()), "累计收藏");
            anyStat = true;
        }
        statsRow.setVisibility(anyStat ? View.VISIBLE : View.GONE);

        // 基本信息：简介＋标签筹＋出品方
        descFull = d.getDesc() == null || d.getDesc().isEmpty() ? "暂无简介" : d.getDesc();
        descExpanded = false;
        renderDesc();
        tagsRow.removeAllViews();
        if (d.getTags() != null && !d.getTags().isEmpty()) {
            tagsRow.setVisibility(View.VISIBLE);
            for (String tag : d.getTags()) {
                // 定稿 .tg：13sp、底 rgba(255,255,255,.13)、圆 8、
                // padding 7/9/7/11＋12px chevron
                LinearLayout chip = new LinearLayout(this);
                chip.setOrientation(LinearLayout.HORIZONTAL);
                chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setBackground(roundBg(0x21FFFFFF, dp(8)));
                chip.setPadding(dp(11), dp(7), dp(9), dp(7));
                TextView chipText = new TextView(this);
                chipText.setText(tag);
                chipText.setTextColor(0xEBFFFFFF);
                chipText.setTextSize(13);
                chip.addView(chipText);
                ImageView chipArrow = new ImageView(this);
                chipArrow.setImageResource(R.drawable.ic_drama_back);
                chipArrow.setColorFilter(0x99FFFFFF);
                chipArrow.setRotation(180);
                LinearLayout.LayoutParams calp = new LinearLayout.LayoutParams(dp(12), dp(12));
                calp.leftMargin = dp(4);
                chip.addView(chipArrow, calp);
                // 点搜接通（2026-10-30）：带词直达搜索页出结果
                chip.setOnClickListener(v ->
                        HongguoSearchActivity.startWithKeyword(this, tag));
                ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(-2, -2);
                lp.rightMargin = dp(8);
                lp.bottomMargin = dp(8);
                tagsRow.addView(chip, lp);
            }
        } else {
            tagsRow.setVisibility(View.GONE);
        }
        String publisher = publisherOf(d);
        if (publisher != null && !publisher.isEmpty()) {
            // 定稿文案「出品方 · X」；主创段详情模型无字段，不编（账记）
            publisherText.setText("出品方 · " + publisher);
            publisherText.setVisibility(View.VISIBLE);
        } else {
            publisherText.setVisibility(View.GONE);
        }
        renderCelebrities(d);

        HgStore.Progress p = HgStore.progressOf(seriesId);
        currentEpisode = p != null ? Math.max(1, p.episodeIndex) : 1;
        if (total > 0) currentEpisode = Math.min(currentEpisode, total);
        styleFav(HgStore.isFaved(seriesId));
        if (d.hasAppointment()) {
            // 预约态以回包 appointment.is_subscribed 校准回写本地镜像
            // （批二 followed 校准同型，批三 A 账 §2 定版）
            HgStore.setReserved(seriesId, d.isAppointmentSubscribed());
            reserveBtn.setVisibility(View.VISIBLE);
            styleReserve(HgStore.isReserved(seriesId));
            if (d.getDueStartText() != null && !d.getDueStartText().isEmpty()) {
                dueText.setText(d.getDueStartText());
                dueText.setVisibility(View.VISIBLE);
            } else {
                dueText.setVisibility(View.GONE);
            }
        } else {
            reserveBtn.setVisibility(View.GONE);
            dueText.setVisibility(View.GONE);
        }
        loadComments();
        // 首开塌陷已在骨架定根（件八）：塌陷源是 col 默认 gravity
        // 居中＋fillViewport 拉高（onCreate 建列处注释），结构已
        // 摘除；此前在此 post(requestLayout) 的兜底对已自洽的错
        // 误度量无效（规格不变、旧度量复用，1.4.68 录屏实证整段
        // 停留仍塌），随定根一并摘除，不留假保险。
    }

    private void addStat(String value, String label) {
        // 定稿 .d-stats：值 17.5sp 900、签 12sp rgba(.52) 上距 5；
        // 非首格带左线 rgba(255,255,255,.14)、格左距 12（首格 2）。
        // 值前 15px 图标草稿有、热度焰图标无现成 drawable，不画字形
        // 凑数（账记数据/资源缺口）。
        boolean first = statsRow.getChildCount() == 0;
        if (!first) {
            View div = new View(this);
            div.setBackgroundColor(0x24FFFFFF);
            statsRow.addView(div, new LinearLayout.LayoutParams(1, -1));
        }
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(first ? dp(2) : dp(12), 0, dp(4), 0);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Color.WHITE);
        v.setTextSize(17.5f);
        v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        cell.addView(v);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(0x85FFFFFF);
        l.setTextSize(12);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.topMargin = dp(5);
        cell.addView(l, llp);
        statsRow.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
    }

    /**
     * 整页渐变（定稿）：三段暖棕固定值——顶色取剧主色
     * （series_color_hex，无则定版 #6A4630），中段 #4A2E1B 落
     * 46%、底段 #33200F（打回⑨：旧按顶色 ×0.70/×0.47 推导作废）。
     * 带停点的 setColors 是 API 29+ 接口，低版本退回三色等距。
     */
    private void applyGradient(String colorHex) {
        int top = 0xFF6A4630;
        if (colorHex != null && !colorHex.isEmpty()) {
            try {
                top = Color.parseColor(colorHex.startsWith("#") ? colorHex : "#" + colorHex);
            } catch (Exception ignored) {
            }
        }
        GradientDrawable g = new GradientDrawable();
        g.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            g.setColors(new int[]{top, 0xFF4A2E1B, 0xFF33200F},
                    new float[]{0f, 0.46f, 1f});
        } else {
            g.setColors(new int[]{top, 0xFF4A2E1B, 0xFF33200F});
        }
        if (rootFrame != null) rootFrame.setBackground(g);
    }

    private static int scaleColor(int c, float f) {
        return Color.rgb(Math.round(Color.red(c) * f),
                Math.round(Color.green(c) * f), Math.round(Color.blue(c) * f));
    }

    /** 出品方：详情模型无此字段，从原回包 video_list[0].copyright 容忍抽取。 */
    private String publisherOf(DramaDetail d) {
        try {
            JsonObject raw = d.getRaw();
            if (raw == null) return null;
            JsonObject data = raw.has("data") && raw.get("data").isJsonObject()
                    ? raw.getAsJsonObject("data") : raw;
            JsonObject vd = data;
            if (!vd.has("video_list")) {
                for (java.util.Map.Entry<String, JsonElement> e : data.entrySet()) {
                    if (e.getValue().isJsonObject()
                            && e.getValue().getAsJsonObject().has("video_list")) {
                        vd = e.getValue().getAsJsonObject();
                        break;
                    }
                }
            }
            JsonArray list = vd.has("video_list") && vd.get("video_list").isJsonArray()
                    ? vd.getAsJsonArray("video_list") : null;
            if (list == null || list.size() == 0 || !list.get(0).isJsonObject()) return null;
            JsonObject first = list.get(0).getAsJsonObject();
            if (first.has("copyright") && first.get("copyright").isJsonPrimitive()) {
                String c = first.get("copyright").getAsString();
                return c == null || c.isEmpty() ? null : c;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 剧评（只看） ----------

    /** 大分＋小号「分」单位（定稿 .score-big 的 small 形态）。 */
    private CharSequence scoreWithUnit(String score) {
        android.text.SpannableString s = new android.text.SpannableString(score + "分");
        s.setSpan(new android.text.style.RelativeSizeSpan(0.42f),
                score.length(), score.length() + 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                score.length(), score.length() + 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private void loadComments() {
        final String sid = seriesId;
        safeExecute(() -> {
            HongguoCommentApi.Page page;
            try {
                page = HongguoCommentApi.seriesComments(sid, null);
            } catch (Exception e) {
                return;
            }
            if (page == null) return;
            runOnUiThread(() -> {
                if (destroyed || isFinishing() || detail == null) return;
                if (page.score != null && !page.score.isEmpty()) {
                    scoreBox.setVisibility(View.VISIBLE);
                    scoreCard.setText(scoreWithUnit(page.score));
                    scoreMidCount.setText(page.scoreCount > 0
                            ? HgTimeTextCount.format(page.scoreCount) + " 人评分" : "");
                }
                if (page.items != null && !page.items.isEmpty()) {
                    commentAdapter.setItems(page.items);
                    commentList.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private class CommentCardAdapter extends RecyclerView.Adapter<CommentCardAdapter.H> {

        private final List<HongguoCommentApi.Comment> data = new ArrayList<>();

        void setItems(List<HongguoCommentApi.Comment> items) {
            data.clear();
            data.addAll(items);
            notifyDataSetChanged();
        }

        class H extends RecyclerView.ViewHolder {
            final ImageView avatar;
            final TextView name;
            final ImageView[] stars;
            final LinearLayout starRow;
            final TextView content;

            H(LinearLayout cell) {
                super(cell);
                avatar = cell.findViewWithTag("avatar");
                name = cell.findViewWithTag("name");
                starRow = cell.findViewWithTag("stars");
                stars = new ImageView[5];
                for (int i = 0; i < 5; i++) stars[i] = (ImageView) starRow.getChildAt(i);
                content = cell.findViewWithTag("content");
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 定稿评论卡：宽 80% 封顶 302、底 rgba(255,255,255,.1)
            // 圆 15 padding 14/15；头行头像 27＋名 13.5/700＋右星排。
            LinearLayout cell = new LinearLayout(HongguoDetailActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setBackground(roundBgStroke(0x1AFFFFFF, dp(15), 0x0FFFFFFF));
            cell.setPadding(dp(15), dp(14), dp(15), dp(14));
            int cardW = Math.min(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.8f), dp(302));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(cardW, -2);
            lp.rightMargin = dp(12);
            cell.setLayoutParams(lp);
            LinearLayout headRow = new LinearLayout(HongguoDetailActivity.this);
            headRow.setOrientation(LinearLayout.HORIZONTAL);
            headRow.setGravity(Gravity.CENTER_VERTICAL);
            ImageView avatar = new ImageView(HongguoDetailActivity.this);
            avatar.setTag("avatar");
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            headRow.addView(avatar, new LinearLayout.LayoutParams(dp(27), dp(27)));
            TextView name = new TextView(HongguoDetailActivity.this);
            name.setTag("name");
            name.setTextColor(0xEBFFFFFF);
            name.setTextSize(13.5f);
            name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, -2, 1f);
            nlp.leftMargin = dp(8);
            headRow.addView(name, nlp);
            LinearLayout starRow = new LinearLayout(HongguoDetailActivity.this);
            starRow.setTag("stars");
            starRow.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < 5; i++) {
                ImageView s = new ImageView(HongguoDetailActivity.this);
                s.setImageResource(R.drawable.ic_drama_star);
                LinearLayout.LayoutParams slp2 = new LinearLayout.LayoutParams(
                        dp(13), dp(13));
                if (i > 0) slp2.leftMargin = dp(1);
                starRow.addView(s, slp2);
            }
            headRow.addView(starRow);
            cell.addView(headRow);
            TextView content = new TextView(HongguoDetailActivity.this);
            content.setTag("content");
            content.setTextColor(0xE6FFFFFF);
            content.setTextSize(13.5f);
            content.setMaxLines(3);
            content.setEllipsize(android.text.TextUtils.TruncateAt.END);
            content.setLineSpacing(0, 1.38f);
            LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(-1, -2);
            clp2.topMargin = dp(10);
            cell.addView(content, clp2);
            return new H(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            HongguoCommentApi.Comment c = data.get(position);
            h.name.setText(c.author == null ? "" : c.author);
            h.content.setText(c.content == null ? "" : c.content);
            if (c.avatar != null && !c.avatar.isEmpty()) {
                Glide.with(HongguoDetailActivity.this).load(c.avatar)
                        .circleCrop().into(h.avatar);
            } else {
                h.avatar.setImageDrawable(null);
            }
            // 星排：条目自带十分制剧评分（Comment.score），折五星；
            // 无分条目整排不显。集注行草稿有、Comment 无集号字段，
            // 不编（账记）。
            if (c.score != null && !c.score.isEmpty()) {
                h.starRow.setVisibility(View.VISIBLE);
                double sc = -1;
                try {
                    sc = Double.parseDouble(c.score);
                } catch (Exception ignored) {
                }
                int filled = sc < 0 ? 0 : (int) Math.round(sc / 2.0);
                for (int i = 0; i < 5; i++) {
                    h.stars[i].setColorFilter(i < filled ? 0xFFFFC44D : 0x47FFFFFF);
                }
            } else {
                h.starRow.setVisibility(View.GONE);
            }
            h.itemView.setOnClickListener(v -> openCommentsSheet());
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    // ---------- 相关作品（官方 plan 547 同 IP 绑定表＋合集兄弟季补齐） ----------

    /**
     * 两路并行取，回来一路渲一路：plan 547 cell 是官方相关作品本体
     * （同 IP 绑定：其它季＋衍生＋漫剧形态，官方序）；合集兄弟季只补
     * plan 没带到的季（他定版「多少季都在相关作品里面」），不抢序、
     * 不重复。两路皆空整段不显。任一路失败按空表处理（另一路照常）。
     */
    private void loadRelatedWorks() {
        DramaClient.fetchRelatedWorks(seriesId, new HgCallback<List<DramaDetail.RelatedWork>>() {
            @Override
            public void onSuccess(List<DramaDetail.RelatedWork> works) {
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    planRelated = works == null ? new ArrayList<>() : works;
                    renderRelated();
                });
            }

            @Override
            public void onError(int code, String msg) {
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    planRelated = new ArrayList<>();
                    renderRelated();
                });
            }
        });
        if (albumId == null || albumId.isEmpty()) {
            albumRelated = new ArrayList<>();
            return;
        }
        DramaClient.fetchAlbum(albumId, new HgCallback<List<DramaItem>>() {
            @Override
            public void onSuccess(List<DramaItem> seasons) {
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    List<DramaDetail.RelatedWork> rows = new ArrayList<>();
                    if (seasons != null) {
                        for (DramaItem s : seasons) {
                            if (s.getSeriesId() != null) {
                                rows.add(DramaDetail.RelatedWork.fromAlbumItem(s));
                            }
                        }
                    }
                    albumRelated = rows;
                    renderRelated();
                });
            }

            @Override
            public void onError(int code, String msg) {
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    albumRelated = new ArrayList<>();
                    renderRelated();
                });
            }
        });
    }

    /** 合并两路快照渲染：plan 序在前、合集补季在后，按剧 id 去重并排自身。 */
    private void renderRelated() {
        if (relatedAdapter == null) return;
        List<DramaDetail.RelatedWork> merged = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (seriesId != null) seen.add(seriesId);
        if (planRelated != null) {
            for (DramaDetail.RelatedWork w : planRelated) {
                if (w.getSeriesId() != null && seen.add(w.getSeriesId())) merged.add(w);
            }
        }
        if (albumRelated != null) {
            for (DramaDetail.RelatedWork w : albumRelated) {
                if (w.getSeriesId() != null && seen.add(w.getSeriesId())) merged.add(w);
            }
        }
        if (detail != null) detail.attachRelatedWorks(merged);
        if (merged.isEmpty()) {
            relatedBox.setVisibility(View.GONE);
            return;
        }
        relatedAdapter.setItems(merged);
        relatedBox.setVisibility(View.VISIBLE);
    }

    private class RelatedAdapter extends RecyclerView.Adapter<RelatedAdapter.H> {

        private final List<DramaDetail.RelatedWork> data = new ArrayList<>();

        void setItems(List<DramaDetail.RelatedWork> items) {
            data.clear();
            data.addAll(items);
            notifyDataSetChanged();
        }

        class H extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView badge;
            final TextView title;

            H(LinearLayout cell) {
                super(cell);
                cover = cell.findViewWithTag("cover");
                badge = cell.findViewWithTag("badge");
                title = cell.findViewWithTag("title");
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 定稿 .rel-item：宽 102、海报 aspect 3/4.05 圆 11、角标
            // 右上、标题 12.5 两行；无 meta 行（定稿无）。
            LinearLayout cell = new LinearLayout(HongguoDetailActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(dp(102), -2);
            lp.rightMargin = dp(12);
            cell.setLayoutParams(lp);
            FrameLayout coverBox = new FrameLayout(HongguoDetailActivity.this);
            ImageView cover = new ImageView(HongguoDetailActivity.this);
            cover.setTag("cover");
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(roundBg(0x33FFFFFF, dp(11)));
            coverBox.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            TextView badge = new TextView(HongguoDetailActivity.this);
            badge.setTag("badge");
            badge.setTextColor(0xFFFFC44D);
            badge.setTextSize(10.5f);
            badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            badge.setBackground(roundBg(0x850A0806, dp(7)));
            badge.setPadding(dp(7), dp(3), dp(7), dp(3));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2);
            blp.gravity = Gravity.TOP | Gravity.RIGHT;
            blp.rightMargin = dp(5);
            blp.topMargin = dp(5);
            coverBox.addView(badge, blp);
            cell.addView(coverBox, new LinearLayout.LayoutParams(-1, dp(138)));
            TextView title = new TextView(HongguoDetailActivity.this);
            title.setTag("title");
            title.setTextColor(0xF2FFFFFF);
            title.setTextSize(12.5f);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setLineSpacing(0, 1.2f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.topMargin = dp(8);
            tlp.leftMargin = dp(1);
            cell.addView(title, tlp);
            return new H(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            DramaDetail.RelatedWork item = data.get(position);
            if (item.getCoverUrl() != null && !item.getCoverUrl().isEmpty()) {
                HgCoverClip.round(h.cover, dp(11));
                Glide.with(HongguoDetailActivity.this).load(item.getCoverUrl()).into(h.cover);
            } else {
                h.cover.setImageDrawable(null);
            }
            // 角标位按草稿＝右上评分位；本地只有 tagText（角标文案）
            // 可用，落此位呈现，无则不显（账记数据源差异）。
            if (item.getTagText() != null && !item.getTagText().isEmpty()) {
                h.badge.setVisibility(View.VISIBLE);
                h.badge.setText(item.getTagText());
            } else {
                h.badge.setVisibility(View.GONE);
            }
            h.title.setText(item.getTitle() == null ? "" : item.getTitle());
            h.itemView.setOnClickListener(v -> {
                if (item.isComingSoon()) {
                    HongguoDetailActivity.start(HongguoDetailActivity.this,
                            item.getSeriesId(), item.getTitle(), albumId);
                } else {
                    HongguoFeedActivity.startSeries(HongguoDetailActivity.this,
                            item.getSeriesId(), item.getTitle(), 1);
                }
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    /** 简易换行布局（详情标签筹用）：按行打包，行距/格距由子件
     * margin 承担（草稿 .tags2 是 wrap 排布）。 */
    private static class FlowLayout extends ViewGroup {

        FlowLayout(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int width = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
            int x = 0;
            int y = 0;
            int rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                measureChildWithMargins(child, wSpec, 0, hSpec, 0);
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                int cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
                int ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
                if (x > 0 && x + cw > width) {
                    x = 0;
                    y += rowH;
                    rowH = 0;
                }
                x += cw;
                rowH = Math.max(rowH, ch);
            }
            setMeasuredDimension(MeasureSpec.getSize(wSpec),
                    y + rowH + getPaddingTop() + getPaddingBottom());
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l - getPaddingLeft() - getPaddingRight();
            int x = getPaddingLeft();
            int y = getPaddingTop();
            int rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                int cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
                int ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
                if (x > getPaddingLeft() && x - getPaddingLeft() + cw > width) {
                    x = getPaddingLeft();
                    y += rowH;
                    rowH = 0;
                }
                child.layout(x + lp.leftMargin, y + lp.topMargin,
                        x + lp.leftMargin + child.getMeasuredWidth(),
                        y + lp.topMargin + child.getMeasuredHeight());
                x += cw;
                rowH = Math.max(rowH, ch);
            }
        }

        @Override
        protected LayoutParams generateDefaultLayoutParams() {
            return new MarginLayoutParams(-2, -2);
        }

        @Override
        protected LayoutParams generateLayoutParams(LayoutParams p) {
            return new MarginLayoutParams(p);
        }

        @Override
        protected boolean checkLayoutParams(LayoutParams p) {
            return p instanceof MarginLayoutParams;
        }
    }

    /** 计数简写（与流页同口径，万级一位小数去尾零）。 */
    static final class HgTimeTextCount {
        static String format(long n) {
            if (n < 10000) return String.valueOf(n);
            double w = n / 10000.0;
            String s = w >= 100 ? String.valueOf(Math.round(w)) : String.format(
                    java.util.Locale.US, "%.1f", w).replaceAll("\\.0$", "");
            return s + "万";
        }
    }
}
