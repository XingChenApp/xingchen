package com.fongmi.android.tv.hongguo.ui;

import android.app.AlertDialog;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.hongguo.api.HgAccountStore;
import com.fongmi.android.tv.hongguo.api.HgAccountSync;

import java.util.ArrayList;
import java.util.List;

/**
 * 我的页（2026-10-31 按定稿 mine_tab_v6 恢复）：白色底栏第三签的落点
 * 也是首页面——白底、无橙框、无设置行、无右上齿轮；账号行白底直排
 * （头像＋昵称＋红果号＋右侧浅橙底钮，未登录→短信登录页、已登录→
 * 退出登录）；五格（历史/收藏/点赞/预约/追更，追更在预约之后）；
 * 五格下整片历史海报墙（三列格，与历史列表同口径、断点续播）。
 * 底栏白色三签只此页、只 tab 入口显。
 *
 * <p>列表模式：历史/收藏/点赞/预约/追更＝白底三列 3:4 海报格（圆角、
 * 右上类型角标、海报下剧名一行＋灰字子行）、顶行筹单选过滤（历史＝
 * 全部/已看完/未看完，其余＝全部/真人剧/漫剧/AI剧）、右上「编辑」进
 * 独立编辑页（HongguoMineEditActivity，五模式全开——他 2026-10-31
 * 亲定：历史/收藏/点赞/预约/追更都要有编辑删除口，追更与收藏同源
 * 同删法），长按任意一格同落点且该格预勾。
 * 小说历史维持原列表行与深底。</p>
 */
public class HongguoMineActivity extends AppCompatActivity {

    private static final int MODE_HOME = 0;
    static final int MODE_HISTORY = 1;
    static final int MODE_FAV = 2;
    private static final int MODE_NOVEL = 3;
    static final int MODE_LIKE = 4;
    static final int MODE_RESERVE = 5;
    static final int MODE_FOLLOW = 6;

    /** 直达入口（定稿：历史页/收藏页是两个独立页）：带此附加打开即落列表、返回即退页。 */
    public static final String EXTRA_INITIAL = "hg_mine_initial";

    /** 底栏第三签入口标记：带此附加打开落定稿首页面并显白色三签底栏。 */
    public static final String EXTRA_TAB = "hg_mine_tab";

    private static final int REQ_EDIT = 41;

    public static void startHistory(android.content.Context context) {
        android.content.Intent intent = new android.content.Intent(context, HongguoMineActivity.class);
        intent.putExtra(EXTRA_INITIAL, MODE_HISTORY);
        HgTransition.push(context, intent);
    }

    public static void startFav(android.content.Context context) {
        android.content.Intent intent = new android.content.Intent(context, HongguoMineActivity.class);
        intent.putExtra(EXTRA_INITIAL, MODE_FAV);
        HgTransition.push(context, intent);
    }

    /** 底栏第三签入口：无 EXTRA_INITIAL、带 tab 标记，落首页面＋白色三签底栏。 */
    public static void startTab(android.content.Context context) {
        android.content.Intent intent = new android.content.Intent(context, HongguoMineActivity.class);
        intent.putExtra(EXTRA_TAB, true);
        // 签位口径与另两签同轨（切换手感扫查）：三签互切一律
        // fade，此前独此签走 push 滑入、与首页/剧场淡入混轨。
        HgTransition.fade(context, intent);
    }

    @Override
    public void finish() {
        super.finish();
        // tab 入口的退出回签位 fade；抽屉纵深入口（历史/收藏）保持 push 退出。
        if (tabEntry) HgTransition.applyFadeExit(this);
        else HgTransition.applyPushExit(this);
    }

    private LinearLayout root;
    private LinearLayout topBar;
    private TextView backBtn;
    private TextView titleText;
    private LinearLayout homeBox;
    private LinearLayout chipsBar;
    private TextView editBtn;
    /** 追更入口砖右上红点（官方 id_mine_follow 同位），showHome 时重算。 */
    private View followTileDot;
    private RecyclerView list;
    private RowAdapter adapter;
    private GridAdapter gridAdapter;
    private TextView emptyText;
    private LinearLayout tabBar;
    private ImageView accountAvatar;
    private TextView accountName;
    private TextView accountSub;
    private TextView accountBtn;
    /** 底栏第三签入口进来：首页面底下挂白色三签底栏（只此页、只 home 态显）。 */
    private boolean tabEntry;
    private int mode = MODE_HOME;
    /** 直达列表模式（抽屉两行进入）：返回直接退页，不回「我的」格。 */
    private boolean directList;
    private final List<Row> rows = new ArrayList<>();
    /** 海报格全量行（未过滤）与当前过滤下标（历史/收藏共用一套筹机制）。 */
    private final List<GridRow> allRows = new ArrayList<>();
    private final List<GridRow> viewRows = new ArrayList<>();
    private final List<TextView> chipViews = new ArrayList<>();
    private int filterIndex = 0;

    private static final class Row {
        String id;
        String title;
        String sub;
        String cover;
    }

    /** 海报格一行：数据全走本地 HgStore；badge/type 本地没存就不显不分档。 */
    private static final class GridRow {
        String id;
        String title;
        String sub;
        String cover;
        String badge;
        String typeCode;
        boolean finished;
        /** 追更格：本地最新已知集数（meta 口径），点开标已见用。 */
        int latestCount;
        /** 追更格：最新集数越过已见快照——有新集未看，挂更新红点。 */
        boolean hasUpdate;
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

    /**
     * 底栏让位改读真实手势区（#4b）：navigation_bar_height 是静态
     * dimen、手势导航下与真实值对不上。窗口 insets 一到就把真值
     * 回填到 tabBar 底部 padding（tabBar 是常驻字段、可直接重设）。
     */
    private void watchNavInsets() {
        getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
            int real = insets.getSystemWindowInsetBottom();
            if (tabBar != null) tabBar.setPadding(0, 0, 0, real);
            return insets;
        });
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HgWindowStyle.apply(this, false, false);
        tabEntry = getIntent().getBooleanExtra(EXTRA_TAB, false);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF111114);
        // tab 入口时底部让位由三签底栏自己带（56dp＋导航区底衬），root 不再叠垫
        root.setPadding(0, 0, 0, tabEntry ? 0 : navBarHeight());

        topBar = new LinearLayout(this);
        LinearLayout top = topBar;
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), statusBarHeight() + dp(6), dp(16), dp(8));
        backBtn = new TextView(this);
        backBtn.setText("‹");
        backBtn.setTextColor(Color.WHITE);
        backBtn.setTextSize(26);
        backBtn.setGravity(Gravity.CENTER);
        backBtn.setOnClickListener(v -> onBackRow());
        top.addView(backBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
        titleText = new TextView(this);
        titleText.setText("我的");
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(18);
        titleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(titleText);
        root.addView(top);

        chipsBar = new LinearLayout(this);
        chipsBar.setOrientation(LinearLayout.HORIZONTAL);
        chipsBar.setGravity(Gravity.CENTER_VERTICAL);
        chipsBar.setPadding(dp(16), dp(8), dp(16), dp(2));
        chipsBar.setVisibility(View.GONE);
        root.addView(chipsBar);

        homeBox = new LinearLayout(this);
        homeBox.setOrientation(LinearLayout.VERTICAL);
        homeBox.setPadding(dp(16), dp(6), dp(16), 0);
        homeBox.addView(buildAccountRow());
        homeBox.addView(buildTilesRow());
        root.addView(homeBox);

        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RowAdapter();
        gridAdapter = new GridAdapter();
        list.setAdapter(adapter);
        list.setVisibility(View.GONE);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        emptyText = new TextView(this);
        emptyText.setTextColor(0xFF9E9EA7);
        emptyText.setTextSize(13.5f);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setVisibility(View.GONE);
        root.addView(emptyText, new LinearLayout.LayoutParams(-1, 0, 1f));

        tabBar = buildTabBar();
        watchNavInsets();
        // 宽度锚满（件四）：默认 wrap 宽入父时三签权重分配不锚窗宽、
        // 真机整组偏左（他截图 113861 实测中心 3%/37%/70%），显式满宽。
        root.addView(tabBar, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        int initial = getIntent().getIntExtra(EXTRA_INITIAL, MODE_HOME);
        if (initial == MODE_HISTORY) {
            directList = true;
            openList(MODE_HISTORY, "观看历史");
        } else if (initial == MODE_FAV) {
            directList = true;
            openList(MODE_FAV, "我的收藏");
        } else {
            showHome();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 返回本页（登录页回来/播放页回来/编辑页回来）整面按落盘现态
        // 重拉（D-06，此前只刷账号行、墙格与已开列表停在旧数据须重进页）
        reloadCurrentView();
        // 落盘会话进面补拉（D-12）：冷启靠落盘会话恢复登录态时四面
        // 同步从不触发——进程内只补一轮，落盘后同口再重拉一次本面，
        // 他端新增的收藏/历史这次进得来、也看得见
        HgAccountSync.syncOnEnter(() -> runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) reloadCurrentView();
        }));
    }

    /**
     * 当前面整表重拉（D-04/D-06 共用口）：账号行＋当前模式的数据面
     * 一并按 HgStore 现态重读。登出清镜像后调它，五格/海报墙立即
     * 不再摆上一账号的数据；纯本地读、无网络，onResume 每次调可承受。
     */
    private void reloadCurrentView() {
        refreshAccountRow();
        if (mode == MODE_HOME) {
            if (followTileDot != null) {
                followTileDot.setVisibility(anyFollowUpdate() ? View.VISIBLE : View.GONE);
            }
            loadHomeWall();
        } else if (mode == MODE_NOVEL) {
            reloadNovelRows();
        } else {
            loadGridRows(mode);
            applyFilter();
        }
    }

    /** 小说历史行重建（openList 小说支与 reloadCurrentView 共用）。 */
    private void reloadNovelRows() {
        rows.clear();
        for (String id : HgStore.novelHistoryIds()) {
            String[] meta = HgStore.novelMetaOf(id);
            Row row = new Row();
            row.id = id;
            row.title = meta == null ? "" : meta[0];
            row.sub = meta == null ? "" : meta[1];
            row.cover = meta == null ? null : meta[2];
            rows.add(row);
        }
        adapter.notifyDataSetChanged();
        list.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        emptyText.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        if (rows.isEmpty()) emptyText.setText("还没有小说记录");
    }

    // ---------- 定稿首页面：账号行 ----------

    /** 账号行：56dp 圆头像＋昵称/红果号两行＋右侧浅橙底钮（白底直排、无橙框）。 */
    private View buildAccountRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(16));
        accountAvatar = new ImageView(this);
        accountAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(accountAvatar, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        accountName = new TextView(this);
        accountName.setTextColor(0xFF1B1B1E);
        accountName.setTextSize(17);
        accountName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        accountName.setMaxLines(1);
        accountName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(accountName);
        accountSub = new TextView(this);
        accountSub.setTextColor(0xFF9B9BA1);
        accountSub.setTextSize(13);
        accountSub.setMaxLines(1);
        accountSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(4);
        texts.addView(accountSub, subLp);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(0, -2, 1f);
        textsLp.leftMargin = dp(12);
        row.addView(texts, textsLp);
        accountBtn = new TextView(this);
        accountBtn.setTextSize(14);
        accountBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        accountBtn.setTextColor(0xFFF07D13);
        accountBtn.setGravity(Gravity.CENTER);
        accountBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        accountBtn.setBackground(roundBg(0xFFFFF0DB, dp(20)));
        accountBtn.setOnClickListener(v -> onAccountBtn());
        row.addView(accountBtn);
        return row;
    }

    /** 账号行按落盘会话重画：onCreate 尾与 onResume 各调一次（登录回来即新态）。 */
    private void refreshAccountRow() {
        if (accountAvatar == null) return;
        HgAccountStore.Session session = HgAccountStore.load();
        if (HgAccountStore.isLoggedIn() && session != null) {
            accountName.setText(session.name == null || session.name.isEmpty()
                    ? "用户" : session.name);
            accountSub.setText("红果号：" + (session.userId == null ? "" : session.userId));
            accountBtn.setText("退出登录");
            if (session.avatar != null && !session.avatar.isEmpty()) {
                accountAvatar.setBackground(null);
                accountAvatar.setPadding(0, 0, 0, 0);
                accountAvatar.setColorFilter(null);
                Glide.with(this).load(session.avatar).circleCrop().into(accountAvatar);
            } else {
                setAvatarPlaceholder();
            }
        } else {
            accountName.setText("未登录");
            accountSub.setText("登录后同步收藏与观看历史");
            accountBtn.setText("立即登录");
            setAvatarPlaceholder();
        }
    }

    /** 未登录/无头像：浅米圆底＋ic_drama_mine 橙色垫底。 */
    private void setAvatarPlaceholder() {
        Glide.with(this).clear(accountAvatar);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(0xFFFBEFD5);
        accountAvatar.setBackground(oval);
        accountAvatar.setImageResource(R.drawable.ic_drama_mine);
        accountAvatar.setColorFilter(0xFFF59E0B);
        accountAvatar.setPadding(dp(13), dp(13), dp(13), dp(13));
    }

    private void onAccountBtn() {
        if (!HgAccountStore.isLoggedIn()) {
            HongguoLoginActivity.start(this);
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("退出登录？")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出", (d, w) -> {
                    HgAccountStore.clear();
                    // 账号数据随账号走（#22）：登出把本地账号镜像
                    //（收藏/点赞/关注/预约四套）一并清掉，防换号串号
                    // ——clearAccountMirrors 此前零调用、此处接上。
                    HgStore.clearAccountMirrors();
                    // 登出后整面重拉（D-04）：五格/海报墙/已开列表
                    // 此前不重拉、仍摆上一账号拉回的数据直到重进页
                    reloadCurrentView();
                    Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ---------- 定稿首页面：五格 ----------

    /** 五格行：48dp 浅色砖（圆角 12）＋字形＋下方 13sp 标签，签序历史/收藏/点赞/预约/追更。 */
    private View buildTilesRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(2), 0, dp(14));
        int[] modes = {MODE_HISTORY, MODE_FAV, MODE_LIKE, MODE_RESERVE, MODE_FOLLOW};
        String[] labels = {"历史", "收藏", "点赞", "预约", "追更"};
        int[] icons = {R.drawable.ic_mine_clock, R.drawable.ic_drama_star,
                R.drawable.ic_mine_heart, R.drawable.ic_mine_calendar, R.drawable.ic_mine_bell};
        int[] brickColors = {0xFFFDF0DC, 0xFFFDECEC, 0xFFFDE8F1, 0xFFE7EFFD, 0xFFE4F5EC};
        int[] iconColors = {0xFFF59E0B, 0xFFE8501C, 0xFFE75480, 0xFF3B82F6, 0xFF1FA97A};
        for (int i = 0; i < labels.length; i++) {
            final int target = modes[i];
            final String label = labels[i];
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout brick = new FrameLayout(this);
            brick.setBackground(roundBg(brickColors[i], dp(12)));
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setColorFilter(iconColors[i]);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(26), dp(26));
            ilp.gravity = Gravity.CENTER;
            brick.addView(icon, ilp);
            if (target == MODE_FOLLOW) {
                // 追更入口红点（官方 MineRedDotManager 的 id_mine_follow
                // POINT 态同位）：有任一追更剧越过已见快照才亮
                followTileDot = new View(this);
                followTileDot.setBackground(roundBg(0xFFFF3B30, dp(5)));
                FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(10), dp(10));
                dlp.gravity = Gravity.TOP | Gravity.END;
                dlp.topMargin = dp(6);
                dlp.rightMargin = dp(6);
                followTileDot.setVisibility(View.GONE);
                brick.addView(followTileDot, dlp);
            }
            cell.addView(brick, new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView text = new TextView(this);
            text.setText(label);
            text.setTextColor(0xFF1B1B1E);
            text.setTextSize(13);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
            tlp.topMargin = dp(6);
            cell.addView(text, tlp);
            cell.setOnClickListener(v -> HgTransition.fadeSwap(
                    () -> openList(target, label), homeBox, list, emptyText));
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        return row;
    }

    // ---------- 定稿首页面：白色三签底栏 ----------

    /** 白色三签底栏：纯文字格，选中（我的）黑粗、无橙点；只 tab 入口的 home 态显。
     * 三签均分全宽（定稿 v6 三等分；件四他点名分布不均后锚死）：row 满宽入 bar、
     * bar 满宽入 root（调用点），三格 weight=1 等分，左右不留空不贴边。 */
    private LinearLayout buildTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        bar.setPadding(0, 0, 0, navBarHeight());
        bar.setVisibility(View.GONE);
        View divider = new View(this);
        divider.setBackgroundColor(0x14000000);
        bar.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(tabCell("首页", false, v -> {
            HongguoFeedActivity.start(this);
            finish();
        }), new LinearLayout.LayoutParams(0, dp(56), 1f));
        row.addView(tabCell("剧场", false, v -> {
            HongguoTheaterActivity.start(this);
            finish();
        }), new LinearLayout.LayoutParams(0, dp(56), 1f));
        row.addView(tabCell("我的", true, null), new LinearLayout.LayoutParams(0, dp(56), 1f));
        bar.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return bar;
    }

    private View tabCell(String label, boolean selected, View.OnClickListener click) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        // 定稿收口（#4b）：橙点删净（含未选中格的隐形占位），字号
        // 按定稿 16.5；选中仍黑粗、未选灰。
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(16.5f);
        text.setTextColor(selected ? 0xFF1B1B1E : 0xFF8A8A93);
        if (selected) text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        cell.addView(text);
        if (click != null) cell.setOnClickListener(click);
        return cell;
    }

    // ---------- 首页面装配 ----------

    /** 落首页面（定稿面）：浅色 chrome、顶栏无返回＋大字「我的」、墙走历史全量格。 */
    private void showHome() {
        mode = MODE_HOME;
        titleText.setText("我的");
        titleText.setTextSize(23);
        backBtn.setVisibility(View.GONE);
        topBar.setPadding(dp(16), statusBarHeight() + dp(6), dp(16), dp(8));
        applyChrome(true);
        chipsBar.setVisibility(View.GONE);
        homeBox.setVisibility(View.VISIBLE);
        tabBar.setVisibility(tabEntry ? View.VISIBLE : View.GONE);
        refreshAccountRow();
        if (followTileDot != null) {
            followTileDot.setVisibility(anyFollowUpdate() ? View.VISIBLE : View.GONE);
        }
        // 预约上线召回也挂在我的页入口（首触面只播种/标记，不重复弹）
        maybePromptReserveRecall();
        list.setPadding(dp(12), dp(6), dp(12), dp(16));
        list.setLayoutManager(new GridLayoutManager(this, 3));
        list.setAdapter(gridAdapter);
        loadHomeWall();
    }

    /** 海报墙＝历史全量（不挂 chips/过滤）：allRows→viewRows 直通，空则「暂无历史」。 */
    private void loadHomeWall() {
        loadGridRows(MODE_HISTORY);
        viewRows.clear();
        viewRows.addAll(allRows);
        gridAdapter.notifyDataSetChanged();
        list.setVisibility(viewRows.isEmpty() ? View.GONE : View.VISIBLE);
        emptyText.setVisibility(viewRows.isEmpty() ? View.VISIBLE : View.GONE);
        if (viewRows.isEmpty()) emptyText.setText("暂无历史");
    }

    /** 海报格模式空文案（历史/收藏保持旧文案，三新模式各一份）。 */
    private static String emptyTextFor(int target) {
        switch (target) {
            case MODE_HISTORY:
                return "暂无历史";
            case MODE_LIKE:
                return "暂无点赞";
            case MODE_RESERVE:
                return "暂无预约";
            case MODE_FOLLOW:
                return "暂无追更";
            default:
                return "暂无收藏";
        }
    }

    private void onBackRow() {
        if (mode != MODE_HOME && directList) {
            finish();
        } else if (mode != MODE_HOME) {
            HgTransition.fadeSwap(this::showHome, homeBox, list, emptyText);
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (mode != MODE_HOME) onBackRow();
        else super.onBackPressed();
    }

    /** 页面明暗切换：浅底＝定稿画布底 #F7F7F8（D-05，此前落纯白 #FFFFFF 差一档）＋深字＋深色系统栏图标；其余维持深底。 */
    private void applyChrome(boolean light) {
        root.setBackgroundColor(light ? 0xFFF7F7F8 : 0xFF111114);
        int fg = light ? 0xFF1B1B1E : Color.WHITE;
        backBtn.setTextColor(fg);
        titleText.setTextColor(fg);
        emptyText.setTextColor(light ? 0xFF8E8E96 : 0xFF9E9EA7);
        HgWindowStyle.apply(this, light, light);
    }

    private void openList(int target, String title) {
        mode = target;
        titleText.setText(title);
        // 列表模式恢复现行顶栏形态（返回钮＋18sp 标题），底栏退场
        titleText.setTextSize(18);
        backBtn.setVisibility(View.VISIBLE);
        topBar.setPadding(dp(6), statusBarHeight() + dp(6), dp(16), dp(8));
        homeBox.setVisibility(View.GONE);
        tabBar.setVisibility(View.GONE);
        if (target == MODE_NOVEL) {
            applyChrome(false);
            chipsBar.setVisibility(View.GONE);
            list.setPadding(0, 0, 0, 0);
            list.setLayoutManager(new LinearLayoutManager(this));
            list.setAdapter(adapter);
            reloadNovelRows();
            return;
        }
        // 历史/收藏：白底海报格＋筹行
        applyChrome(true);
        filterIndex = 0;
        loadGridRows(target);
        buildChips(target);
        chipsBar.setVisibility(View.VISIBLE);
        list.setPadding(dp(12), dp(6), dp(12), dp(16));
        list.setLayoutManager(new GridLayoutManager(this, 3));
        list.setAdapter(gridAdapter);
        applyFilter();
        if (target == MODE_RESERVE) maybePromptReserveRecall();
    }

    /**
     * 预约上线召回（终裁接线，普查#5）：官方是召回弹窗——预约剧新进
     * 「已上线」档时弹一次。咱家无服务端下发，判定只用既有字段做
     * 播种＋差集（HgStore 召回四法的落盘语义）：已上线集
     * （reservedOnlineIds，同步回写）∩ 我的预约 − 已提示集；首轮
     * 只播种存量不弹（存量已上线不是新上线）。已提示先落盘再弹窗，
     * 弹窗失败/页面销毁也不重复打扰。提醒文案为功能文案，官方逐字
     * 未抠到（待裁，见 rulings 账）。
     */
    private void maybePromptReserveRecall() {
        java.util.Set<String> online = HgStore.reservedOnlineIds();
        java.util.Set<String> mine = new java.util.HashSet<>(HgStore.reservedIds());
        java.util.List<String> fresh = new java.util.ArrayList<>();
        for (String id : online) {
            if (mine.contains(id)) fresh.add(id);
        }
        if (!HgStore.recallSeeded()) {
            HgStore.addRecallPrompted(fresh);
            HgStore.markRecallSeeded();
            return;
        }
        java.util.Set<String> prompted = HgStore.recallPromptedIds();
        fresh.removeIf(prompted::contains);
        if (fresh.isEmpty()) return;
        HgStore.addRecallPrompted(fresh);
        if (isFinishing() || isDestroyed()) return;
        StringBuilder msg = new StringBuilder("你预约的剧已上线：");
        for (int i = 0; i < fresh.size(); i++) {
            String[] meta = HgStore.metaOf(fresh.get(i));
            String t = meta == null || meta[0] == null || meta[0].isEmpty()
                    ? "新剧" : meta[0];
            if (i > 0) msg.append("、");
            msg.append("《").append(t).append("》");
        }
        try {
            new AlertDialog.Builder(this)
                    .setTitle("预约上线提醒")
                    .setMessage(msg.toString())
                    .setNegativeButton("知道了", null)
                    .setPositiveButton("去看看", (d, w) -> {
                        if (mode != MODE_RESERVE) openList(MODE_RESERVE, "预约");
                    })
                    .show();
        } catch (Exception ignored) {
            // 销毁竞态下弹窗失败静默：已提示已落盘，不重复打扰
        }
    }

    // ---------- 海报格（历史/收藏） ----------

    private void loadGridRows(int target) {
        allRows.clear();
        if (target == MODE_HISTORY) {
            for (HgStore.HistoryRecord r : HgStore.historyRecords()) {
                GridRow row = new GridRow();
                row.id = r.seriesId;
                row.title = r.title == null ? "" : r.title;
                row.cover = r.cover;
                row.typeCode = HgStore.typeOf(r.seriesId);
                row.badge = HgStore.badgeTextOf(row.typeCode);
                int ep = Math.max(1, r.episodeIndex);
                row.finished = r.episodeCount > 0 && r.episodeIndex >= r.episodeCount;
                row.sub = progressText(ep, r.episodeCount);
                allRows.add(row);
            }
        } else {
            // 收藏架同路四模式：各自 id 集＋同一 metaOf 分支（收藏/点赞/预约/追更）
            java.util.List<String> ids = target == MODE_LIKE ? HgStore.likedIds()
                    : target == MODE_RESERVE ? HgStore.reservedIds()
                    : target == MODE_FOLLOW ? HgStore.followedIds() : HgStore.favedIds();
            for (String id : ids) {
                String[] meta = HgStore.metaOf(id);
                GridRow row = new GridRow();
                row.id = id;
                row.title = meta == null ? "" : meta[0];
                row.cover = meta == null ? null : meta[1];
                int count = -1;
                if (meta != null) {
                    try {
                        count = Integer.parseInt(meta[2]);
                    } catch (Exception ignored) {
                    }
                }
                row.typeCode = HgStore.typeOf(id);
                row.badge = HgStore.badgeTextOf(row.typeCode);
                HgStore.Progress p = HgStore.progressOf(id);
                if (p != null) {
                    row.sub = progressText(Math.max(1, p.episodeIndex), count);
                } else {
                    row.sub = count > 0 ? "共" + count + "集" : "";
                }
                // 预约分档消费（普查接线）：同步早已把「已上线」集
                // 写进 reservedOnlineIds、此前零读者——预约格里给
                // 已上线条目在副行领起标出来，与待上线分开。
                if (target == MODE_RESERVE
                        && HgStore.reservedOnlineIds().contains(id)) {
                    row.sub = row.sub == null || row.sub.isEmpty()
                            ? "已上线" : "已上线 · " + row.sub;
                }
                // 追更更新判定（终裁接线，普查#4）：已见快照
                // （followSeenCount）之后有新集即挂更新红点。计数源
                // 只用既有字段——最新集数＝meta 最新已知值（同步/
                // 详情/播放回写刷新）；从没落过快照的先按字段语义
                // 播种基线（进面时的最新集数）、本轮不报更新。
                if (target == MODE_FOLLOW) {
                    row.latestCount = count;
                    int seen = HgStore.followSeenCount(id);
                    if (count > 0 && seen < 0) {
                        HgStore.markFollowSeen(id, count);
                    } else if (count > 0 && count > seen) {
                        row.hasUpdate = true;
                    }
                }
                allRows.add(row);
            }
        }
    }

    /** 子行进度文案（官方格口径「N集/M集」）；总数未知只写看到哪集。 */
    private static String progressText(int ep, int total) {
        return total > 0 ? ep + "集/" + total + "集" : "看到第" + ep + "集";
    }

    private void buildChips(int target) {
        chipsBar.removeAllViews();
        chipViews.clear();
        String[] labels = target == MODE_HISTORY
                ? new String[]{"全部", "已看完", "未看完"}
                : new String[]{"全部", "真人剧", "漫剧", "AI剧"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            chip.setTextSize(13.5f);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(13), dp(6), dp(13), dp(6));
            chip.setOnClickListener(v -> {
                if (filterIndex == index) return;
                filterIndex = index;
                styleChips();
                HgTransition.fadeSwap(this::applyFilter, list, emptyText);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.leftMargin = dp(8);
            chipsBar.addView(chip, lp);
            chipViews.add(chip);
        }
        View spacer = new View(this);
        chipsBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        editBtn = new TextView(this);
        editBtn.setText("编辑");
        editBtn.setTextColor(0xFF1B1B1E);
        editBtn.setTextSize(14.5f);
        editBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        editBtn.setGravity(Gravity.CENTER);
        editBtn.setPadding(dp(10), dp(6), dp(2), dp(6));
        editBtn.setOnClickListener(v -> openEditPage(null));
        chipsBar.addView(editBtn);
        styleChips();
    }

    private void styleChips() {
        for (int i = 0; i < chipViews.size(); i++) {
            TextView chip = chipViews.get(i);
            boolean on = i == filterIndex;
            chip.setBackground(roundBg(on ? 0xFFFFF0DB : 0xFFF4F4F5, dp(16)));
            chip.setTextColor(on ? 0xFFF07D13 : 0xFF3A3A3E);
            chip.setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
        }
        if (editBtn != null) {
            // 五模式全有编辑删除口（他 2026-10-31 亲定，编辑页五套删法齐）
            boolean editable = mode == MODE_HISTORY || mode == MODE_FAV || mode == MODE_FOLLOW
                    || mode == MODE_LIKE || mode == MODE_RESERVE;
            editBtn.setVisibility(editable && !allRows.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void applyFilter() {
        viewRows.clear();
        for (GridRow row : allRows) {
            if (mode == MODE_HISTORY) {
                if (filterIndex == 1 && !row.finished) continue;
                if (filterIndex == 2 && row.finished) continue;
            } else if (mode != MODE_HISTORY && filterIndex > 0) {
                // 类型过滤泛化到全部非历史海报格模式（收藏/点赞/预约/追更同筹）
                String want = filterIndex == 1 ? HgStore.TYPE_LIVE
                        : filterIndex == 2 ? HgStore.TYPE_COMIC : HgStore.TYPE_AI;
                if (!want.equals(row.typeCode)) continue;
            }
            viewRows.add(row);
        }
        gridAdapter.notifyDataSetChanged();
        list.setVisibility(viewRows.isEmpty() ? View.GONE : View.VISIBLE);
        emptyText.setVisibility(viewRows.isEmpty() ? View.VISIBLE : View.GONE);
        if (viewRows.isEmpty()) {
            emptyText.setText(emptyTextFor(mode));
        }
    }

    /** 进独立编辑页（点「编辑」与长按一格同一个落点，长按的那格预勾）。 */
    private void openEditPage(@Nullable String preselectId) {
        openEditPageFor(mode, preselectId);
    }

    /** 指定编辑模式进编辑页：首页面墙格长按走历史删法（墙即历史）。 */
    private void openEditPageFor(int editMode, @Nullable String preselectId) {
        Intent intent = new Intent(this, HongguoMineEditActivity.class);
        intent.putExtra(HongguoMineEditActivity.EXTRA_MODE, editMode);
        if (preselectId != null) {
            intent.putExtra(HongguoMineEditActivity.EXTRA_PRESELECT, preselectId);
        }
        startActivityForResult(intent, REQ_EDIT);
        overridePendingTransition(R.anim.hg_push_in, R.anim.hg_hold);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 编辑页删完回来：本地记录已变，整表按现行过滤重读（删的要消失）
        if (requestCode == REQ_EDIT && mode == MODE_HOME) {
            loadHomeWall();
        } else if (requestCode == REQ_EDIT
                && (mode == MODE_HISTORY || mode == MODE_FAV || mode == MODE_FOLLOW
                || mode == MODE_LIKE || mode == MODE_RESERVE)) {
            loadGridRows(mode);
            styleChips();
            applyFilter();
        }
    }

    private void openRow(Row row) {
        Intent intent = new Intent(this, HongguoNovelDetailActivity.class);
        intent.putExtra(HongguoNovelDetailActivity.EXTRA_BOOK_ID, row.id);
        intent.putExtra(HongguoNovelDetailActivity.EXTRA_TITLE, row.title);
        HgTransition.push(this, intent);
    }

    private void openGridRow(GridRow row) {
        // 追更格点开即标已见（终裁接线）：已见快照推进到当前最新
        // 集数，本条的更新红点下次进面即灭
        if (mode == MODE_FOLLOW && row.latestCount > 0) {
            HgStore.markFollowSeen(row.id, row.latestCount);
        }
        // 预约分档落点（D-03）：不在「已上线」档里的预约条目直进播放页
        // 必落失败面（剧还没上线、取不到链）——先落详情页看信息/预约态；
        // 已上线档由同步回写 reservedOnlineIds 为准，在档内照旧带断点播。
        if (mode == MODE_RESERVE
                && !HgStore.reservedOnlineIds().contains(row.id)) {
            HongguoDetailActivity.start(this, row.id, row.title, null);
            return;
        }
        // 首页面墙与历史列表同路：带断点续播
        HgStore.Progress p = mode == MODE_HISTORY || mode == MODE_HOME
                ? HgStore.progressOf(row.id) : null;
        int ep = p == null ? 0 : p.episodeIndex;
        HongguoFeedActivity.startSeries(this, row.id, row.title, ep);
    }

    /** 任一追更剧有越过已见快照的新集（只读判定，不播种基线）。 */
    private boolean anyFollowUpdate() {
        for (String id : HgStore.followedIds()) {
            String[] meta = HgStore.metaOf(id);
            if (meta == null) continue;
            int count = -1;
            try {
                count = Integer.parseInt(meta[2]);
            } catch (Exception ignored) {
            }
            if (count <= 0) continue;
            int seen = HgStore.followSeenCount(id);
            if (seen >= 0 && count > seen) return true;
        }
        return false;
    }

    private GradientDrawable roundBg(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    /** 海报格适配器：三列 3:4、等比裁切、右上角标、海报下剧名＋灰字子行。 */
    private class GridAdapter extends RecyclerView.Adapter<GridAdapter.H> {

        private final int posterW;
        private final int posterH;

        GridAdapter() {
            posterW = (getResources().getDisplayMetrics().widthPixels - dp(48)) / 3;
            posterH = posterW * 4 / 3;
        }

        class H extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView badge;
            final View updateDot;
            final TextView title;
            final TextView sub;

            H(LinearLayout cell) {
                super(cell);
                cover = cell.findViewWithTag("cover");
                badge = cell.findViewWithTag("badge");
                updateDot = cell.findViewWithTag("udot");
                title = cell.findViewWithTag("title");
                sub = cell.findViewWithTag("sub");
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout cell = new LinearLayout(HongguoMineActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(dp(4), dp(6), dp(4), dp(8));
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            FrameLayout posterBox = new FrameLayout(HongguoMineActivity.this);
            posterBox.setBackground(roundBg(0xFFEDEDEF, dp(8)));
            ImageView cover = new ImageView(HongguoMineActivity.this);
            cover.setTag("cover");
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            posterBox.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            TextView badge = new TextView(HongguoMineActivity.this);
            badge.setTag("badge");
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(10);
            badge.setPadding(dp(5), dp(2), dp(5), dp(2));
            badge.setBackground(roundBg(0x8C000000, dp(4)));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2);
            blp.gravity = Gravity.TOP | Gravity.END;
            blp.topMargin = dp(5);
            blp.rightMargin = dp(5);
            posterBox.addView(badge, blp);
            // 更新红点（追更格专用，官方红点 POINT 态）：落封面左上，
            // 与右上类型角标错开；其余模式恒隐
            View udot = new View(HongguoMineActivity.this);
            udot.setTag("udot");
            udot.setBackground(roundBg(0xFFFF3B30, dp(5)));
            udot.setVisibility(View.GONE);
            FrameLayout.LayoutParams ulp = new FrameLayout.LayoutParams(dp(10), dp(10));
            ulp.gravity = Gravity.TOP | Gravity.START;
            ulp.topMargin = dp(6);
            ulp.leftMargin = dp(6);
            posterBox.addView(udot, ulp);
            cell.addView(posterBox, new LinearLayout.LayoutParams(posterW, posterH));
            TextView title = new TextView(HongguoMineActivity.this);
            title.setTag("title");
            title.setTextColor(0xFF1B1B1E);
            title.setTextSize(14);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.topMargin = dp(6);
            cell.addView(title, tlp);
            TextView sub = new TextView(HongguoMineActivity.this);
            sub.setTag("sub");
            sub.setTextColor(0xFF9B9BA1);
            sub.setTextSize(11.5f);
            sub.setMaxLines(1);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams slp2 = new LinearLayout.LayoutParams(-1, -2);
            slp2.topMargin = dp(2);
            cell.addView(sub, slp2);
            return new H(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            GridRow row = viewRows.get(position);
            if (row.cover != null && !row.cover.isEmpty()) {
                HgCoverClip.round(h.cover, dp(8));
                Glide.with(HongguoMineActivity.this).load(row.cover)
                        .into(h.cover);
            } else {
                h.cover.setImageDrawable(null);
            }
            if (row.badge != null && !row.badge.isEmpty()) {
                h.badge.setVisibility(View.VISIBLE);
                h.badge.setText(row.badge);
            } else {
                h.badge.setVisibility(View.GONE);
            }
            h.updateDot.setVisibility(row.hasUpdate ? View.VISIBLE : View.GONE);
            h.title.setText(row.title);
            h.sub.setText(row.sub == null ? "" : row.sub);
            h.itemView.setOnClickListener(v -> openGridRow(row));
            h.itemView.setOnLongClickListener(v -> {
                // 五模式长按同口径落编辑页（他 2026-10-31 亲定）；墙格长按走历史编辑
                if (mode == MODE_HOME) openEditPageFor(MODE_HISTORY, row.id);
                else openEditPage(row.id);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return viewRows.size();
        }
    }

    private class RowAdapter extends RecyclerView.Adapter<RowAdapter.H> {

        class H extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView title;
            final TextView sub;

            H(LinearLayout cell) {
                super(cell);
                cover = cell.findViewWithTag("cover");
                title = cell.findViewWithTag("title");
                sub = cell.findViewWithTag("sub");
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout cell = new LinearLayout(HongguoMineActivity.this);
            cell.setOrientation(LinearLayout.HORIZONTAL);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setPadding(dp(16), dp(9), dp(16), dp(9));
            ImageView cover = new ImageView(HongguoMineActivity.this);
            cover.setTag("cover");
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(roundBg(0xFF232328, dp(6)));
            cell.addView(cover, new LinearLayout.LayoutParams(dp(46), dp(62)));
            LinearLayout texts = new LinearLayout(HongguoMineActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(HongguoMineActivity.this);
            title.setTag("title");
            title.setTextColor(0xFFF2F2F4);
            title.setTextSize(14.5f);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(title);
            TextView sub = new TextView(HongguoMineActivity.this);
            sub.setTag("sub");
            sub.setTextColor(0xFF8E8E96);
            sub.setTextSize(12);
            sub.setMaxLines(1);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
            slp.topMargin = dp(4);
            texts.addView(sub, slp);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
            tlp.leftMargin = dp(12);
            cell.addView(texts, tlp);
            return new H(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            Row row = rows.get(position);
            if (row.cover != null && !row.cover.isEmpty()) {
                HgCoverClip.round(h.cover, dp(8));
                Glide.with(HongguoMineActivity.this).load(row.cover).into(h.cover);
            } else {
                h.cover.setImageDrawable(null);
            }
            h.title.setText(row.title);
            h.sub.setText(row.sub == null ? "" : row.sub);
            h.itemView.setOnClickListener(v -> openRow(row));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
