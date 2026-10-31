package com.fongmi.android.tv.hongguo.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.hongguo.api.HgCallback;
import com.fongmi.android.tv.hongguo.drama.DramaClient;
import com.fongmi.android.tv.hongguo.drama.DramaUserProfile;
import com.fongmi.android.tv.hongguo.drama.DramaWork;

import java.util.ArrayList;
import java.util.List;

/**
 * 作者页（2026-10-30 从零重建，简单口径）：头像/名字/简介＋作品
 * 网格，一页一件事。作品点开：成剧的进详情页、单条 UGC 进集流直播。
 *
 * <p>关注钮：没登录时代曾整件摘除；2026-10-31 15:07 他终裁加回——
 * 官方形态（普查卡 23）＝三数行右侧「＋关注」浅底橙字胶囊、已关注
 * 灰态、canFollow=false 与本人页不显。账号口径同当日终定：未登录
 * 点关注跳登录页；登录态初态以资料回包 relation_type 校准本地镜像
 * （HgStore 作者关注三法），关注提交口未验到、只落本地态（见
 * hongguo-rulings-20261031.md）。</p>
 */
public class HongguoAuthorActivity extends AppCompatActivity {

    public static final String EXTRA_UID = "hg_author_uid";
    public static final String EXTRA_NAME = "hg_author_name";
    public static final String EXTRA_AVATAR = "hg_author_avatar";

    public static void start(Context context, String uid, String name, String avatar) {
        Intent intent = new Intent(context, HongguoAuthorActivity.class);
        intent.putExtra(EXTRA_UID, uid);
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(EXTRA_AVATAR, avatar);
        HgTransition.push(context, intent);
    }

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

    /** 定稿名字字体：Noto Serif SC 900 的系统近似（serif 粗体）。 */
    private static final android.graphics.Typeface SERIF_BLACK =
            android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD);

    private String uid;
    /** 销毁旗（与 Feed/Detail/Theater 同口径）：异步回包落地先查此旗，销毁后不许再动视图/Glide。 */
    private volatile boolean destroyed;
    private LinearLayout rootView;
    private ImageView avatarView;
    private ImageView vipView;
    private TextView nameView;
    private TextView badgeView;
    private ImageView badgeIconView;
    private TextView subTitleView;
    private TextView descView;
    private LinearLayout statsRow;
    private TextView statFollow;
    private TextView statFans;
    private TextView statDigg;
    private LinearLayout socialRow;
    private TextView followBtn;
    /** 本人页（会话 uid＝本页 uid）：官方口径不显关注钮。 */
    private boolean selfPage;
    /** 关注态：开页先以本地镜像占位，资料回包 followedByMe 到后校准。 */
    private boolean authorFollowed;
    /** 可关注旗：回包前按可关注计，回包 canFollow=false 收钮。 */
    private boolean authorCanFollow = true;
    private LinearLayout tabRow;
    private android.widget.HorizontalScrollView tabScrollView;
    private TextView stateText;
    private WorksAdapter adapter;
    private final List<DramaWork> works = new ArrayList<>();
    private String cursor;
    private boolean hasMore;
    private boolean loading;
    /** 顶签（11 剧作／5 动态）：先问 11，空包自动换 5 重试一次。 */
    private int topTabType = 11;
    private boolean triedAltTab;
    /** 当前子档 tab_type（0＝首包未带参，对应子档表第一档「全部」）。 */
    private int activeSubTab;
    private boolean subTabsBuilt;
    private final List<TextView> tabChips = new ArrayList<>();
    private final List<Integer> tabChipTypes = new ArrayList<>();

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 三数行一格（值粗体在上、签在下），返回值件供资料回填。 */
    private TextView addStatCell(LinearLayout row, String label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView value = new TextView(this);
        value.setTextColor(0xFF1B1B1F);
        value.setTextSize(15.5f);
        value.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        value.setGravity(Gravity.CENTER);
        cell.addView(value);
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextColor(0xFF8A8A93);
        lab.setTextSize(11.5f);
        lab.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.topMargin = dp(2);
        cell.addView(lab, llp);
        row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        return value;
    }

    private static String statText(int n) {
        return n < 0 ? "–" : HongguoFeedActivity.HgTimeTextCount.format(n);
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
        uid = getIntent().getStringExtra(EXTRA_UID);

        // 页面层重做（定稿 #authorOverlay）：浅底，顶栏浅底圆返回钮
        // ＋「作者主页」，头部居中列，作品三列格圆 13。
        LinearLayout root = new LinearLayout(this);
        rootView = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFAF9F7);

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
        TextView topTitle = new TextView(this);
        topTitle.setText("作者主页");
        topTitle.setTextColor(0xFF1B1B1F);
        topTitle.setTextSize(16);
        topTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams ttlp = new LinearLayout.LayoutParams(-2, -2);
        ttlp.leftMargin = dp(10);
        top.addView(topTitle, ttlp);
        root.addView(top);

        // 头部：居中列（头像 74 圆、名 Serif 21、meta 12.5 sub 居中）；
        // 2026-10-31 补齐官方头部件：头像右下 V 角标、名旁入驻签、
        // 红果号行（副标题段，点行复制号）、三数行（关注/粉丝/获赞）。
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setPadding(dp(30), dp(20), dp(30), dp(4));
        android.widget.FrameLayout avatarWrap = new android.widget.FrameLayout(this);
        avatarView = new ImageView(this);
        avatarView.setBackground(roundBg(0xFFE8E6E2, dp(37)));
        avatarWrap.addView(avatarView, new android.widget.FrameLayout.LayoutParams(dp(74), dp(74)));
        vipView = new ImageView(this);
        vipView.setVisibility(View.GONE);
        android.widget.FrameLayout.LayoutParams vlp =
                new android.widget.FrameLayout.LayoutParams(dp(22), dp(22));
        vlp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        avatarWrap.addView(vipView, vlp);
        head.addView(avatarWrap, new LinearLayout.LayoutParams(dp(74), dp(74)));
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nrlp = new LinearLayout.LayoutParams(-2, -2);
        nrlp.topMargin = dp(13);
        nrlp.bottomMargin = dp(5);
        nameView = new TextView(this);
        nameView.setTextColor(0xFF1B1B1F);
        nameView.setTextSize(21);
        nameView.setTypeface(SERIF_BLACK);
        nameView.setGravity(Gravity.CENTER);
        nameView.setMaxLines(1);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameRow.addView(nameView, new LinearLayout.LayoutParams(-2, -2));
        badgeView = new TextView(this);
        badgeView.setTextColor(0xFF3D7BE0);
        badgeView.setTextSize(10.5f);
        badgeView.setPadding(dp(6), dp(2), dp(6), dp(2));
        badgeView.setBackground(roundBg(0xFFE3EDFD, dp(5)));
        badgeView.setVisibility(View.GONE);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.leftMargin = dp(7);
        nameRow.addView(badgeView, blp);
        badgeIconView = new ImageView(this);
        badgeIconView.setVisibility(View.GONE);
        LinearLayout.LayoutParams bilp = new LinearLayout.LayoutParams(dp(35), dp(16));
        bilp.leftMargin = dp(7);
        nameRow.addView(badgeIconView, bilp);
        head.addView(nameRow, nrlp);
        subTitleView = new TextView(this);
        subTitleView.setTextColor(0xFF8A8A93);
        subTitleView.setTextSize(12);
        subTitleView.setGravity(Gravity.CENTER);
        subTitleView.setMaxLines(1);
        subTitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subTitleView.setVisibility(View.GONE);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(-1, -2);
        stlp.bottomMargin = dp(4);
        head.addView(subTitleView, stlp);
        descView = new TextView(this);
        descView.setTextColor(0xFF8A8A93);
        descView.setTextSize(12.5f);
        descView.setGravity(Gravity.CENTER);
        // 定稿头部 meta 行高 1.7，按全单字体换算（≈×0.83）落 1.41
        descView.setLineSpacing(0, 1.41f);
        descView.setMaxLines(3);
        descView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(descView);
        statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(-1, -2);
        srlp.topMargin = dp(14);
        statFollow = addStatCell(statsRow, "关注");
        statFans = addStatCell(statsRow, "粉丝");
        statDigg = addStatCell(statsRow, "获赞");
        // 社交行：三数（权 1）＋右侧关注钮（官方卡 23 页头形态：
        // 数据行右侧「＋关注」浅底橙字胶囊）
        socialRow = new LinearLayout(this);
        socialRow.setOrientation(LinearLayout.HORIZONTAL);
        // END 兜底：三数行未显（计数没到）时关注钮仍靠右，不贴左
        socialRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        socialRow.setVisibility(View.GONE);
        socialRow.addView(statsRow, new LinearLayout.LayoutParams(0, -2, 1f));
        followBtn = new TextView(this);
        followBtn.setTextSize(13.5f);
        followBtn.setGravity(Gravity.CENTER);
        followBtn.setPadding(dp(16), dp(7), dp(16), dp(7));
        followBtn.setOnClickListener(v -> onFollowClicked());
        LinearLayout.LayoutParams fblp = new LinearLayout.LayoutParams(-2, -2);
        fblp.leftMargin = dp(12);
        socialRow.addView(followBtn, fblp);
        head.addView(socialRow, srlp);
        root.addView(head);

        LinearLayout worksHead = new LinearLayout(this);
        worksHead.setOrientation(LinearLayout.HORIZONTAL);
        worksHead.setBaselineAligned(true);
        worksHead.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
        wlp.topMargin = dp(24);
        wlp.bottomMargin = dp(2);
        TextView worksLabel = new TextView(this);
        worksLabel.setText("作品");
        worksLabel.setTextColor(0xFF1B1B1F);
        worksLabel.setTextSize(16);
        worksLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        worksLabel.setIncludeFontPadding(false);
        worksHead.addView(worksLabel);
        root.addView(worksHead, wlp);

        // 子档签行（服务端 sub_tabs 下发后才建：全部/合集/剧评…，
        // 官方同款浅橙选中态）；没下发就不显，保持旧单流形态。
        tabScrollView = new android.widget.HorizontalScrollView(this);
        tabScrollView.setHorizontalScrollBarEnabled(false);
        tabScrollView.setVisibility(View.GONE);
        tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(dp(18), 0, dp(18), 0);
        tabScrollView.addView(tabRow, new android.view.ViewGroup.LayoutParams(-2, -2));
        LinearLayout.LayoutParams tslp = new LinearLayout.LayoutParams(-1, -2);
        tslp.topMargin = dp(12);
        root.addView(tabScrollView, tslp);

        RecyclerView grid = new RecyclerView(this);
        GridLayoutManager glm = new GridLayoutManager(this, 3);
        // 合集条目整行宽（span 3），普通作品一格（span 1）
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position < works.size()
                        && works.get(position).kind == DramaWork.KIND_ALBUM ? 3 : 1;
            }
        });
        grid.setLayoutManager(glm);
        grid.setPadding(dp(13), dp(16), dp(13), dp(16) + navBarHeight());
        grid.setClipToPadding(false);
        adapter = new WorksAdapter();
        grid.setAdapter(adapter);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0 && !loading && hasMore
                        && glm.findLastVisibleItemPosition() >= works.size() - 6) {
                    loadWorks(false);
                }
            }
        });
        // 状态字（加载中/暂无作品/失败）盖在网格区正中，与剧场同改
        // （他 2026-10-31 点名：加载中不许在最底下）
        android.widget.FrameLayout stage = new android.widget.FrameLayout(this);
        stage.addView(grid, new android.widget.FrameLayout.LayoutParams(-1, -1));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        stateText = new TextView(this);
        stateText.setTextColor(0xFF8A8A93);
        stateText.setTextSize(13.5f);
        stateText.setGravity(Gravity.CENTER);
        stateText.setPadding(0, dp(10), 0, dp(10));
        stateText.setVisibility(View.GONE);
        stateText.setOnClickListener(v -> {
            if (works.isEmpty()) loadWorks(true);
        });
        stage.addView(stateText, new android.widget.FrameLayout.LayoutParams(-1, -1));

        setContentView(root);

        String name = getIntent().getStringExtra(EXTRA_NAME);
        nameView.setText(name == null ? "" : name);
        String avatar = getIntent().getStringExtra(EXTRA_AVATAR);
        if (avatar != null && !avatar.isEmpty()) {
            Glide.with(this).load(avatar).circleCrop().into(avatarView);
        }
        // 关注钮初态：本人页判定（会话 uid 对本页 uid）＋本地镜像
        // 占位上屏，资料回包 followedByMe 到后再校准（见 loadProfile）
        com.fongmi.android.tv.hongguo.api.HgAccountStore.Session session =
                com.fongmi.android.tv.hongguo.api.HgAccountStore.load();
        selfPage = session != null && session.userId != null
                && uid != null && uid.equals(session.userId);
        authorFollowed = HgStore.isAuthorFollowed(uid);
        paintFollowBtn();
        refreshSocialRow();
        loadProfile();
        loadWorks(true);
    }

    /** 关注钮两态上色（与本页收藏钮同色板：橙胶囊／灰胶囊）。 */
    private void paintFollowBtn() {
        followBtn.setText(authorFollowed ? "已关注" : "＋关注");
        followBtn.setTextColor(authorFollowed ? 0xFF8A8A93 : 0xFFE86A17);
        followBtn.setBackground(roundBg(authorFollowed ? 0xFFF0EFED : 0xFFFFEFE3, dp(15)));
    }

    /**
     * 社交行显隐：关注钮（非本人页＋可关注＋uid 在）或三数任一在才整行显。
     * 三数行自身的显隐仍由资料回包计数决定，本法只管行容器与钮。
     */
    private void refreshSocialRow() {
        boolean btnOn = uid != null && !selfPage && authorCanFollow;
        followBtn.setVisibility(btnOn ? View.VISIBLE : View.GONE);
        boolean rowOn = btnOn || statsRow.getVisibility() == View.VISIBLE;
        socialRow.setVisibility(rowOn ? View.VISIBLE : View.GONE);
    }

    private void onFollowClicked() {
        if (uid == null) return;
        // 账号终定口径（与收藏/点赞五入口同门）：未登录跳登录页、不落本地
        if (!com.fongmi.android.tv.hongguo.api.HgAccountStore.isLoggedIn()) {
            HongguoLoginActivity.start(this);
            return;
        }
        authorFollowed = !authorFollowed;
        HgStore.setAuthorFollowed(uid, authorFollowed);
        paintFollowBtn();
        // 关注提交口未验到：只落本地镜像态，不猜接口（见 rulings 账）
    }

    private void loadProfile() {
        if (uid == null) return;
        DramaClient.fetchUserProfile(uid, new HgCallback<DramaUserProfile>() {
            @Override
            public void onSuccess(DramaUserProfile p) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    if (p.name != null && !p.name.isEmpty()) nameView.setText(p.name);
                    if (p.avatar != null && !p.avatar.isEmpty()) {
                        Glide.with(HongguoAuthorActivity.this).load(p.avatar)
                                .circleCrop().into(avatarView);
                    }
                    // 头衔徽标：图标地址在就用图（服务端定版尺寸），
                    // 否则文字蓝签（解析侧两样都抽到了，此前没渲染）
                    if (p.titleBadgeIcon != null && !p.titleBadgeIcon.isEmpty()) {
                        int bw = p.titleBadgeIconW > 0 ? p.titleBadgeIconW : 35;
                        int bh = p.titleBadgeIconH > 0 ? p.titleBadgeIconH : 16;
                        android.view.ViewGroup.LayoutParams bp =
                                badgeIconView.getLayoutParams();
                        bp.width = dp(bw);
                        bp.height = dp(bh);
                        badgeIconView.setLayoutParams(bp);
                        Glide.with(HongguoAuthorActivity.this).load(p.titleBadgeIcon)
                                .into(badgeIconView);
                        badgeIconView.setVisibility(View.VISIBLE);
                    } else if (p.titleBadge != null && !p.titleBadge.isEmpty()) {
                        badgeView.setText(p.titleBadge);
                        badgeView.setVisibility(View.VISIBLE);
                    }
                    // V 角标（头像右下，服务端圆 V 图直用）
                    if (p.blueVip && p.vipIconUrl != null && !p.vipIconUrl.isEmpty()) {
                        Glide.with(HongguoAuthorActivity.this).load(p.vipIconUrl)
                                .circleCrop().into(vipView);
                        vipView.setVisibility(View.VISIBLE);
                    }
                    // 红果号行（副标题段）：点行复制红果号（官方同款）
                    if (p.subTitleLine != null && !p.subTitleLine.isEmpty()) {
                        subTitleView.setText(p.subTitleLine);
                        subTitleView.setVisibility(View.VISIBLE);
                        if (p.hongguoId != null && !p.hongguoId.isEmpty()) {
                            subTitleView.setOnClickListener(v -> {
                                android.content.ClipboardManager cm =
                                        (android.content.ClipboardManager)
                                                getSystemService(CLIPBOARD_SERVICE);
                                if (cm != null) {
                                    cm.setPrimaryClip(android.content.ClipData
                                            .newPlainText("红果号", p.hongguoId));
                                    android.widget.Toast.makeText(
                                            HongguoAuthorActivity.this,
                                            "红果号已复制", android.widget.Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                    // 简介回归简介本身；粉丝数挪去三数行
                    descView.setText(p.desc == null ? "" : p.desc);
                    if (p.followUserNum >= 0 || p.fansNum >= 0 || p.recvDiggedCount >= 0) {
                        statFollow.setText(statText(p.followUserNum));
                        statFans.setText(statText(p.fansNum));
                        statDigg.setText(statText(p.recvDiggedCount));
                        statsRow.setVisibility(View.VISIBLE);
                    }
                    // 关注钮校准：服务端关系态（followedByMe）判读得出
                    // 才算数——覆盖开页的本地镜像占位，并把镜像同步成
                    // 服务端态；canFollow=false 收钮（官方卡 23 口径）
                    authorCanFollow = p.canFollow;
                    if (p.followedByMe != null) {
                        authorFollowed = p.followedByMe;
                        HgStore.setAuthorFollowed(uid, authorFollowed);
                    }
                    paintFollowBtn();
                    refreshSocialRow();
                    // 页头暖色底（服务端 bg_color：顶暖渐变到页底色）
                    if (p.bgColor != null && !p.bgColor.isEmpty() && rootView != null) {
                        try {
                            int c = android.graphics.Color.parseColor(p.bgColor);
                            GradientDrawable gd = new GradientDrawable(
                                    GradientDrawable.Orientation.TOP_BOTTOM,
                                    new int[]{c, 0xFFFAF9F7});
                            gd.setGradientType(GradientDrawable.LINEAR_GRADIENT);
                            rootView.setBackground(gd);
                        } catch (Exception ignored) {
                        }
                    }
                });
            }

            @Override
            public void onError(int code, String msg) {
            }
        });
    }

    private void loadWorks(boolean first) {
        if (loading || uid == null) return;
        loading = true;
        if (first) {
            stateText.setText("加载中…");
            stateText.setVisibility(View.VISIBLE);
        }
        DramaClient.fetchAuthorWorks(uid, first ? null : cursor, topTabType, activeSubTab,
                new HgCallback<DramaWork.Page>() {
            @Override
            public void onSuccess(DramaWork.Page page) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    loading = false;
                    // 问错顶签的合法空包（剧作签对 UGC 作者就是空）：
                    // 条目与子档表全空时自动换动态签重试一次
                    if (first && works.isEmpty() && page.items.isEmpty()
                            && page.subTabs.isEmpty() && !triedAltTab && topTabType == 11) {
                        triedAltTab = true;
                        topTabType = 5;
                        loadWorks(true);
                        return;
                    }
                    if (!page.subTabs.isEmpty() && !subTabsBuilt) {
                        buildSubTabs(page.subTabs);
                    }
                    works.addAll(page.items);
                    cursor = page.nextCursor;
                    hasMore = page.hasMore;
                    adapter.notifyDataSetChanged();
                    stateText.setVisibility(works.isEmpty() ? View.VISIBLE : View.GONE);
                    if (works.isEmpty()) stateText.setText("暂无作品");
                });
            }

            @Override
            public void onError(int code, String msg) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    loading = false;
                    if (works.isEmpty()) {
                        stateText.setText("取数据失败，点按重试");
                        stateText.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    /** 子档签建行：第一档默认选中（首包未带 sub_tab_type 即其内容）。 */
    private void buildSubTabs(List<DramaWork.SubTab> subs) {
        subTabsBuilt = true;
        tabChips.clear();
        tabChipTypes.clear();
        tabRow.removeAllViews();
        for (int i = 0; i < subs.size(); i++) {
            DramaWork.SubTab st = subs.get(i);
            TextView chip = new TextView(this);
            chip.setText(st.name);
            chip.setTextSize(13.5f);
            chip.setPadding(dp(15), dp(6), dp(15), dp(6));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) clp.leftMargin = dp(8);
            chip.setOnClickListener(v -> selectSubTab(st.tabType));
            tabRow.addView(chip, clp);
            tabChips.add(chip);
            tabChipTypes.add(st.tabType);
        }
        paintSubTabs();
        tabScrollView.setVisibility(View.VISIBLE);
    }

    private void paintSubTabs() {
        for (int i = 0; i < tabChips.size(); i++) {
            boolean on = activeSubTab == 0 ? i == 0
                    : tabChipTypes.get(i) == activeSubTab;
            TextView chip = tabChips.get(i);
            chip.setTextColor(on ? 0xFFE86A17 : 0xFF6E6E76);
            chip.setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
            chip.setBackground(roundBg(on ? 0xFFFFEFE3 : 0xFFF0EFED, dp(15)));
        }
    }

    private void selectSubTab(int tabType) {
        int want = tabType;
        if (activeSubTab == 0 && !tabChipTypes.isEmpty()
                && tabChipTypes.get(0) == want) return;
        if (want == activeSubTab) return;
        activeSubTab = want;
        paintSubTabs();
        works.clear();
        cursor = null;
        hasMore = false;
        adapter.notifyDataSetChanged();
        loadWorks(true);
    }

    /** 打开合集（点卡头进首集、点集缩略落那集；走合集页全队列）。 */
    private void openAlbum(DramaWork work, String vid) {
        if (work.album == null) return;
        // 点赞数带入本集的（#15）：集缩略自带 digg_cnt，找不到对
        // 应集时回落作品级计数，都没有为 -1。
        long digg = -1;
        for (DramaWork.AlbumEp e : work.album.episodes) {
            if (e != null && vid != null && vid.equals(e.vid)) {
                digg = e.diggCount;
                break;
            }
        }
        if (digg < 0) digg = work.diggCount;
        com.fongmi.android.tv.hongguo.drama.DramaItem item =
                com.fongmi.android.tv.hongguo.drama.DramaItem.fromDetail(
                        work.album.albumId, work.title, work.cover, work.intro,
                        work.episodeCount, vid == null ? "" : vid,
                        work.album.seriesStatus,
                        nameView.getText() == null ? "" : nameView.getText().toString(),
                        getIntent().getStringExtra(EXTRA_AVATAR), uid, 1001, -1, digg);
        HongguoFeedActivity.startVideoAlbum(this, item);
    }

    private void paintFavBtn(TextView btn, boolean on) {
        btn.setText(on ? "已收藏" : "收藏");
        btn.setTextColor(on ? 0xFF8A8A93 : 0xFFE86A17);
        btn.setBackground(roundBg(on ? 0xFFF0EFED : 0xFFFFEFE3, dp(15)));
    }

    private class WorksAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        class H extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView score;
            final TextView title;
            final TextView meta;

            H(LinearLayout cell) {
                super(cell);
                cover = cell.findViewWithTag("cover");
                score = cell.findViewWithTag("score");
                title = cell.findViewWithTag("title");
                meta = cell.findViewWithTag("meta");
            }
        }

        /** 合集卡（整行）：头行（小封面＋名＋状态行＋收藏钮）＋集缩略横条。 */
        class AH extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView name;
            final TextView stick;
            final TextView meta;
            final TextView favBtn;
            final LinearLayout stripRow;
            final LinearLayout headerRow;

            AH(LinearLayout card) {
                super(card);
                headerRow = card.findViewWithTag("ahead");
                thumb = card.findViewWithTag("athumb");
                name = card.findViewWithTag("aname");
                stick = card.findViewWithTag("astick");
                meta = card.findViewWithTag("ameta");
                favBtn = card.findViewWithTag("afav");
                stripRow = card.findViewWithTag("astripRow");
            }
        }

        @Override
        public int getItemViewType(int position) {
            return works.get(position).kind == DramaWork.KIND_ALBUM ? 1 : 0;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 1) return new AH(buildAlbumCard());
            // 定稿作品格：海报 aspect 3/4.1、圆 13、占位 #E8E6E2；
            // 标题 13.5/700 ink、meta 11.5 sub 单行；右上评分角标
            // 与剧场卡同式（有分才显——作品回包无评分字段，见账）。
            LinearLayout cell = new LinearLayout(HongguoAuthorActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            android.widget.FrameLayout coverBox = new android.widget.FrameLayout(
                    HongguoAuthorActivity.this);
            ImageView cover = new ImageView(HongguoAuthorActivity.this);
            cover.setTag("cover");
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(roundBg(0xFFE8E6E2, dp(13)));
            coverBox.addView(cover, new android.widget.FrameLayout.LayoutParams(-1, -1));
            TextView score = new TextView(HongguoAuthorActivity.this);
            score.setTag("score");
            score.setTextColor(0xFFFFC44D);
            score.setTextSize(10.5f);
            score.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            score.setPadding(dp(7), dp(3), dp(7), dp(3));
            score.setBackground(roundBg(0x850A0806, dp(7)));
            score.setVisibility(View.GONE);
            android.widget.FrameLayout.LayoutParams sclp =
                    new android.widget.FrameLayout.LayoutParams(-2, -2);
            sclp.gravity = Gravity.TOP | Gravity.RIGHT;
            sclp.rightMargin = dp(7);
            sclp.topMargin = dp(7);
            coverBox.addView(score, sclp);
            int w = (getResources().getDisplayMetrics().widthPixels - dp(56)) / 3;
            cell.addView(coverBox, new LinearLayout.LayoutParams(-1, (int) (w * (4.1f / 3f))));
            TextView title = new TextView(HongguoAuthorActivity.this);
            title.setTag("title");
            title.setTextColor(0xFF1B1B1F);
            title.setTextSize(13.5f);
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.topMargin = dp(8);
            tlp.leftMargin = dp(1);
            cell.addView(title, tlp);
            TextView meta = new TextView(HongguoAuthorActivity.this);
            meta.setTag("meta");
            meta.setTextColor(0xFF8A8A93);
            meta.setTextSize(11.5f);
            meta.setMaxLines(1);
            meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
            mlp.topMargin = dp(3);
            mlp.leftMargin = dp(1);
            cell.addView(meta, mlp);
            return new H(cell);
        }

        private LinearLayout buildAlbumCard() {
            LinearLayout card = new LinearLayout(HongguoAuthorActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(5), dp(2), dp(5), dp(18));
            LinearLayout headRow = new LinearLayout(HongguoAuthorActivity.this);
            headRow.setTag("ahead");
            headRow.setOrientation(LinearLayout.HORIZONTAL);
            headRow.setGravity(Gravity.CENTER_VERTICAL);
            ImageView thumb = new ImageView(HongguoAuthorActivity.this);
            thumb.setTag("athumb");
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackground(roundBg(0xFFE8E6E2, dp(9)));
            headRow.addView(thumb, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout mid = new LinearLayout(HongguoAuthorActivity.this);
            mid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams midlp = new LinearLayout.LayoutParams(0, -2, 1f);
            midlp.leftMargin = dp(10);
            LinearLayout nameRow = new LinearLayout(HongguoAuthorActivity.this);
            nameRow.setOrientation(LinearLayout.HORIZONTAL);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView aname = new TextView(HongguoAuthorActivity.this);
            aname.setTag("aname");
            aname.setTextColor(0xFF1B1B1F);
            aname.setTextSize(15);
            aname.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            aname.setMaxLines(1);
            aname.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nameRow.addView(aname, new LinearLayout.LayoutParams(-2, -2));
            TextView stick = new TextView(HongguoAuthorActivity.this);
            stick.setTag("astick");
            stick.setText("置顶");
            stick.setTextColor(0xFFE86A17);
            stick.setTextSize(10);
            stick.setPadding(dp(5), dp(1), dp(5), dp(1));
            stick.setBackground(roundBg(0xFFFFEFE3, dp(4)));
            stick.setVisibility(View.GONE);
            LinearLayout.LayoutParams stlp2 = new LinearLayout.LayoutParams(-2, -2);
            stlp2.leftMargin = dp(6);
            nameRow.addView(stick, stlp2);
            mid.addView(nameRow);
            TextView meta = new TextView(HongguoAuthorActivity.this);
            meta.setTag("ameta");
            meta.setTextColor(0xFF8A8A93);
            meta.setTextSize(12);
            meta.setMaxLines(1);
            meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams amlp = new LinearLayout.LayoutParams(-1, -2);
            amlp.topMargin = dp(4);
            mid.addView(meta, amlp);
            headRow.addView(mid, midlp);
            TextView favBtn = new TextView(HongguoAuthorActivity.this);
            favBtn.setTag("afav");
            favBtn.setTextSize(13);
            favBtn.setPadding(dp(16), dp(7), dp(16), dp(7));
            headRow.addView(favBtn, new LinearLayout.LayoutParams(-2, -2));
            card.addView(headRow);
            android.widget.HorizontalScrollView stripScroll =
                    new android.widget.HorizontalScrollView(HongguoAuthorActivity.this);
            stripScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout stripRow = new LinearLayout(HongguoAuthorActivity.this);
            stripRow.setTag("astripRow");
            stripRow.setOrientation(LinearLayout.HORIZONTAL);
            stripScroll.addView(stripRow, new android.view.ViewGroup.LayoutParams(-2, -2));
            LinearLayout.LayoutParams sslp = new LinearLayout.LayoutParams(-1, -2);
            sslp.topMargin = dp(10);
            card.addView(stripScroll, sslp);
            return card;
        }

        private void bindAlbum(AH h, DramaWork work) {
            DramaWork.AlbumInfo al = work.album;
            if (al == null) return;
            if (!work.cover.isEmpty()) {
                HgCoverClip.round(h.thumb, dp(9));
                Glide.with(HongguoAuthorActivity.this).load(work.cover).into(h.thumb);
            } else {
                h.thumb.setImageDrawable(null);
            }
            h.name.setText(work.title);
            h.stick.setVisibility(al.stickTop ? View.VISIBLE : View.GONE);
            StringBuilder m = new StringBuilder(al.seriesStatus == 1 ? "已完结" : "连载中");
            if (!al.hotText.isEmpty()) m.append(" · ").append(al.hotText);
            m.append(" · ").append(al.seriesStatus == 1
                    ? "全" + work.episodeCount + "集"
                    : "更新至" + work.episodeCount + "集");
            h.meta.setText(m.toString());
            paintFavBtn(h.favBtn, HgStore.isFaved(al.albumId));
            h.favBtn.setOnClickListener(v -> {
                // 账号终定口径（#22）：收藏绑账号，未登录跳登录页
                if (!com.fongmi.android.tv.hongguo.api.HgAccountStore.isLoggedIn()) {
                    HongguoLoginActivity.start(HongguoAuthorActivity.this);
                    return;
                }
                boolean now = HgStore.toggleFaved(al.albumId);
                paintFavBtn(h.favBtn, now);
                com.fongmi.android.tv.hongguo.api.HgAccountSync.reportShelf(al.albumId, now);
            });
            h.headerRow.setOnClickListener(v -> openAlbum(work, null));
            h.stripRow.removeAllViews();
            for (int i = 0; i < al.episodes.size(); i++) {
                DramaWork.AlbumEp ep = al.episodes.get(i);
                android.widget.FrameLayout box = new android.widget.FrameLayout(
                        HongguoAuthorActivity.this);
                ImageView cov = new ImageView(HongguoAuthorActivity.this);
                cov.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cov.setBackground(roundBg(0xFFE8E6E2, dp(10)));
                if (!ep.cover.isEmpty()) {
                    HgCoverClip.round(cov, dp(10));
                    Glide.with(HongguoAuthorActivity.this).load(ep.cover).into(cov);
                }
                box.addView(cov, new android.widget.FrameLayout.LayoutParams(-1, -1));
                TextView epChip = new TextView(HongguoAuthorActivity.this);
                epChip.setText("第" + (ep.index + 1) + "集");
                epChip.setTextColor(0xFFFFFFFF);
                epChip.setTextSize(10);
                epChip.setPadding(dp(5), dp(2), dp(5), dp(2));
                epChip.setBackground(roundBg(0x850A0806, dp(6)));
                android.widget.FrameLayout.LayoutParams eclp =
                        new android.widget.FrameLayout.LayoutParams(-2, -2);
                eclp.gravity = Gravity.TOP | Gravity.LEFT;
                eclp.leftMargin = dp(5);
                eclp.topMargin = dp(5);
                box.addView(epChip, eclp);
                if (ep.diggCount > 0) {
                    TextView digg = new TextView(HongguoAuthorActivity.this);
                    digg.setText("♥ " + HongguoFeedActivity.HgTimeTextCount.format(ep.diggCount));
                    digg.setTextColor(0xFFFFFFFF);
                    digg.setTextSize(10.5f);
                    digg.setShadowLayer(dp(2), 0, dp(1), 0xB3000000);
                    android.widget.FrameLayout.LayoutParams dlp =
                            new android.widget.FrameLayout.LayoutParams(-2, -2);
                    dlp.gravity = Gravity.BOTTOM | Gravity.LEFT;
                    dlp.leftMargin = dp(6);
                    dlp.bottomMargin = dp(5);
                    box.addView(digg, dlp);
                }
                box.setOnClickListener(v -> openAlbum(work, ep.vid));
                LinearLayout.LayoutParams boxlp = new LinearLayout.LayoutParams(dp(92), dp(120));
                if (i > 0) boxlp.leftMargin = dp(8);
                h.stripRow.addView(box, boxlp);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {
            DramaWork work = works.get(position);
            if (vh instanceof AH) {
                bindAlbum((AH) vh, work);
                return;
            }
            H h = (H) vh;
            int col = position % 3;
            h.itemView.setPadding(col == 0 ? 0 : dp(5), 0,
                    col == 2 ? 0 : dp(5), dp(14));
            if (work.cover != null && !work.cover.isEmpty()) {
                HgCoverClip.round(h.cover, dp(13));
                Glide.with(HongguoAuthorActivity.this).load(work.cover).into(h.cover);
            } else {
                h.cover.setImageDrawable(null);
            }
            // 评分角标：有分才显（DramaWork.score 仅剧评条目带分，
            // 视频作品回包无评分字段、恒 -1，不编数）
            if (work.score > 0) {
                h.score.setVisibility(View.VISIBLE);
                h.score.setText(String.valueOf(work.score));
            } else {
                h.score.setVisibility(View.GONE);
            }
            h.title.setText(work.title == null ? "" : work.title);
            // 角标文案：服务端副标题段优先（自带完结态，如「更新至12集」），
            // 无则回退集数/播放量（DramaWork 无完结态字段，不编完结）
            String meta = null;
            if (work.subTitles != null && !work.subTitles.isEmpty()
                    && work.subTitles.get(0) != null && !work.subTitles.get(0).isEmpty()) {
                meta = work.subTitles.get(0);
            } else if (work.episodeCount > 0) {
                meta = work.episodeCount + "集";
            } else if (work.playCount > 0) {
                meta = HongguoFeedActivity.HgTimeTextCount.format(work.playCount) + " 播放";
            }
            h.meta.setText(meta == null ? "" : meta);
            h.itemView.setOnClickListener(v -> {
                if (work.seriesId != null && !work.seriesId.isEmpty()
                        && work.kind != DramaWork.KIND_VIDEO) {
                    HongguoDetailActivity.start(HongguoAuthorActivity.this,
                            work.seriesId, work.title, null);
                } else if (work.id != null && !work.id.isEmpty()) {
                    HongguoFeedActivity.startUgc(HongguoAuthorActivity.this, work.id,
                            work.seriesId, work.title, work.cover, 1001);
                }
            });
        }

        @Override
        public int getItemCount() {
            return works.size();
        }
    }
}
