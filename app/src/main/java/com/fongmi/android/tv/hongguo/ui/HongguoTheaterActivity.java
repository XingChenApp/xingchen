package com.fongmi.android.tv.hongguo.ui;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.PathParser;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.hongguo.api.HgCallback;
import com.fongmi.android.tv.hongguo.drama.DramaCellPage;
import com.fongmi.android.tv.hongguo.drama.DramaClient;
import com.fongmi.android.tv.hongguo.drama.DramaFilterRow;
import com.fongmi.android.tv.hongguo.drama.DramaItem;
import com.fongmi.android.tv.hongguo.drama.DramaItemKind;
import com.fongmi.android.tv.hongguo.model.HgContinuation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 剧场页（2026-10-30 从零重建，他亲定底栏第二签）：简单分类浏览。
 * 大分类四格（他 2026-10-30 改口径，旧「AI 归漫剧侧」作废）：真人剧/
 * 漫剧/AI剧/视频，各配两行小分类筹（主题＋设定、跨行单选、无数据
 * 整行不显）＋网格/瀑布。
 * 数据分法（分类探针实拉配对定版，账 hongguo-theater-ai-20261030）：
 * 服务端类型类 content_category_type——「1」真人剧走 39 频道、
 * 「1004」漫剧走 36 频道（漫剧格剔除 AI 类收纯）、「1004_ai_video」
 * AI剧在 36 频道里实拉为零、只在发现流（-1,16）成量出现，AI 格即
 * 发现流按该字段过滤成格；ai_usage_type 是作者声明标记、两类都带，
 * 不是分家字段。小分类筹走官方筛选面（distribution landpage，他
 * 2026-10-30 发官方实图亲证、探针账 hongguo-chip-fix-20261030）：
 * 面板按格 scene 下发维度表，筹落两行——全部主题
 * （category_dim_theme）＋全部设定（category_dim_role），跨行单选
 * （他亲令），点筹走同口内容路按 scene 分格取数；面板拉不到筹行
 * 整行不显、频道流照常。频道/发现流续页：开页 tab 级 has_more 恒
 * false，靠续页凭证在位判有下页＋continuation 逐页推进（旧版两处
 * 皆断、网格卡死开页，同账 §三）。
 * 视频格：UGC 短片/合集双列瀑布卡。点封面直接进播放页开播，不绕
 * 详情页（定稿口径）；底栏白色（定稿），与首页黑块底栏分开。
 */
public class HongguoTheaterActivity extends AppCompatActivity {

    /** 定稿标题字体：Noto Serif SC 900 的系统近似（serif 粗体）。 */
    private static final android.graphics.Typeface SERIF_BLACK =
            android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD);

    private static final int[][] CHANNELS = {{39, 39}, {36, 36}};
    private static final String[] CHANNEL_NAMES = {"真人剧", "漫剧", "AI剧", "视频"};
    private static final int TAB_MANJU = 1;
    private static final int TAB_AI = 2;
    private static final int TAB_CLIPS = 3;
    /** AI 格自动连拉（B-15）：攒够首屏的目标条数（两列网格首屏
     * 约 3–4 行）与单轮连拉页数预算（防密度极低时无休止拉）。 */
    private static final int AI_FILL_TARGET = 8;
    private static final int AI_CHAIN_MAX = 10;

    private RecyclerView grid;
    private GridAdapter gridAdapter;
    private ClipAdapter clipAdapter;
    private TextView[] tabViews;
    private TextView stateText;
    private LinearLayout chipArea;
    private HorizontalScrollView themeScroll;
    private HorizontalScrollView roleScroll;
    private LinearLayout themeRow;
    private LinearLayout roleRow;
    private final List<TextView> themeChipViews = new ArrayList<>();
    private final List<TextView> roleChipViews = new ArrayList<>();
    /** 筛选面面板按格缓存（key＝格下标）；pending 防重入、dead 拉败后本页内不重试。 */
    private final Map<Integer, List<DramaFilterRow>> panelCache = new HashMap<>();
    private final Set<Integer> panelPending = new HashSet<>();
    private final Set<Integer> panelDead = new HashSet<>();
    /** 跨行单选的当前筛选（维度键＋值），皆 null＝未筛。 */
    private String activeDimKey;
    private String activeDimValue;
    private final List<DramaItem> items = new ArrayList<>();
    private final List<DramaItem> clips = new ArrayList<>();
    private int channel = 0;
    private HgContinuation continuation;
    private boolean hasMore;
    private boolean loading;
    private boolean reqFilter;
    private int filterOffset;
    private String filterSession;
    private final List<String> filterSeen = new ArrayList<>();
    private int clipChain;
    private int aiChain;
    /** 填充续拉连空转计数（回包零新增连着来时封顶，见 maybeFill）。 */
    private int fillChain;
    /** 切格/点筹后首批数据到达要整表淡入承接（到达前为 true）。 */
    private boolean pendingContentFade;
    /** 分格状态保留（常驻口径，他 2026-10-30 定）：切格离开时整份
     * 存下，再切回来原样恢复、不重拉；没存过/上次没拉成的格才重拉。 */
    private final Map<Integer, TabState> tabStates = new HashMap<>();
    /** 当前格是否已成功落过一页（存档凭据；load(true) 起复位）。 */
    private boolean pageSettled;
    /** 请求代次：切格后旧格的在途回包一律作废，防串进当前格。 */
    private int loadGen;
    /** 销毁旗（B-21）：loadGen 只防切格串入，防不了页销毁——在途
     * 回包在销毁后仍 runOnUiThread 动 adapter/筹面。置位后全回调
     * 先查此旗（与 Feed/Detail 的 destroyed 同口径）。 */
    private volatile boolean destroyed;
    /** 进程重建恢复的滚动位（B-24）：onCreate 从存档口读入，首个
     * onPage 落数后消费一次（夹到已拉条数内），消费完置 -1。 */
    private int restoreScrollPos = -1;

    private static final class TabState {
        final List<DramaItem> items = new ArrayList<>();
        final List<DramaItem> clips = new ArrayList<>();
        HgContinuation continuation;
        boolean hasMore;
        boolean reqFilter;
        int filterOffset;
        String filterSession;
        final List<String> filterSeen = new ArrayList<>();
        int clipChain;
        int aiChain;
        int fillChain;
        String dimKey;
        String dimValue;
        boolean settled;
        Parcelable scrollState;
    }

    /** 入口（短剧首页底栏）：与首页同口径常驻——已有实例提到前台
     * 复用，格/筹/滚动位置全留住；没有实例才新建。 */
    public static void start(android.content.Context context) {
        Intent intent = new Intent(context, HongguoTheaterActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        HgTransition.fade(context, intent); // 签位口径：淡入复用，不硬切
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HgWindowStyle.apply(this, true, true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFAF9F7);

        // 页头（页面层重做，定稿 .page-head）：Serif 大标题＋副题同
        // 行，右侧浅底圆搜索钮 38dp。
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), statusBarHeight() + dp(14), dp(18), 0);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setBaselineAligned(true);
        TextView title = new TextView(this);
        title.setText("剧场");
        title.setTextColor(0xFF1B1B1F);
        title.setTextSize(25);
        title.setTypeface(SERIF_BLACK);
        title.setIncludeFontPadding(false);
        titleRow.addView(title);
        top.addView(titleRow, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView search = new ImageView(this);
        search.setImageResource(R.drawable.ic_drama_search);
        search.setColorFilter(0xFF1B1B1F);
        search.setBackground(roundBg(0x0D1B1B1F, dp(19)));
        search.setPadding(dp(9), dp(9), dp(9), dp(9));
        search.setOnClickListener(v -> HgTransition.push(this,
                new Intent(this, HongguoSearchActivity.class)));
        top.addView(search, new LinearLayout.LayoutParams(dp(38), dp(38)));
        root.addView(top);

        // 四格分段（定稿 .catseg）：浅灰槽底，选中白格浮起＋深字重。
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackground(roundBg(0x0E1B1B1F, dp(14)));
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(-1, -2);
        tabsLp.leftMargin = dp(18);
        tabsLp.rightMargin = dp(18);
        tabsLp.topMargin = dp(16);
        tabViews = new TextView[CHANNEL_NAMES.length];
        for (int i = 0; i < CHANNEL_NAMES.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(CHANNEL_NAMES[i]);
            t.setTextSize(14.5f);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> {
                if (channel != idx) switchTab(idx);
            });
            tabViews[i] = t;
            tabs.addView(t, new LinearLayout.LayoutParams(0, dp(41), 1f));
        }
        root.addView(tabs, tabsLp);

        chipArea = new LinearLayout(this);
        chipArea.setOrientation(LinearLayout.VERTICAL);
        chipArea.setVisibility(View.GONE);
        chipArea.setPadding(0, dp(14), 0, 0);
        themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeRow.setPadding(dp(18), 0, dp(18), 0);
        themeScroll = new HorizontalScrollView(this);
        themeScroll.setHorizontalScrollBarEnabled(false);
        themeScroll.addView(themeRow);
        chipArea.addView(themeScroll);
        roleRow = new LinearLayout(this);
        roleRow.setOrientation(LinearLayout.HORIZONTAL);
        roleRow.setPadding(dp(18), 0, dp(18), 0);
        roleScroll = new HorizontalScrollView(this);
        roleScroll.setHorizontalScrollBarEnabled(false);
        roleScroll.addView(roleRow);
        LinearLayout.LayoutParams roleLp = new LinearLayout.LayoutParams(-1, -2);
        roleLp.topMargin = dp(16);
        chipArea.addView(roleScroll, roleLp);
        root.addView(chipArea);

        grid = new RecyclerView(this);
        grid.setPadding(dp(12), dp(14), dp(12), dp(8));
        grid.setClipToPadding(false);
        gridAdapter = new GridAdapter();
        clipAdapter = new ClipAdapter();
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || !hasMore) return;
                int last = lastVisiblePosition();
                int count = channel == TAB_CLIPS ? clips.size() : items.size();
                if (last >= count - 6) load(false);
            }
        });
        // 状态字（加载中/暂无/失败）盖在网格区正中——原先它排在网格
        // 后面、被顶到屏幕最底下（他 2026-10-31 点名：加载中该在中间）
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
            if (!(channel == TAB_CLIPS ? clips : items).isEmpty()) return;
            // AI 格连拉预算耗尽但服务端还有下页（B-15）：接着往后拉，
            // 不从头重拉——重拉只会再撞同一批低密度页。
            if (channel == TAB_AI && !reqFilter && hasMore && continuation != null) {
                aiChain = 0;
                load(false);
            } else {
                load(true);
            }
        });
        stage.addView(stateText, new android.widget.FrameLayout.LayoutParams(-1, -1));

        root.addView(buildBottomBar(), new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        if (savedInstanceState != null) {
            // 进程重建存档口（B-24）：configChanges 补齐后旋转/暗色
            // 不再重建，这里只兜系统回收后的进程级重建——恢复当时
            // 的格与筛选（数据按恢复后的格/筛选重拉首批，与冷启该
            // 格同内容），滚动位在首个 onPage 落数后夹位恢复。
            int savedChannel = savedInstanceState.getInt("hg_tab", 0);
            channel = savedChannel >= 0 && savedChannel < CHANNEL_NAMES.length
                    ? savedChannel : 0;
            activeDimKey = savedInstanceState.getString("hg_dim_key");
            activeDimValue = savedInstanceState.getString("hg_dim_value");
            restoreScrollPos = savedInstanceState.getInt("hg_scroll", -1);
        }
        selectTab();
        applyLayout();
        load(true);
        ensurePanel(channel);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("hg_tab", channel);
        outState.putString("hg_dim_key", activeDimKey);
        outState.putString("hg_dim_value", activeDimValue);
        outState.putInt("hg_scroll", firstVisiblePosition());
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    private int firstVisiblePosition() {
        RecyclerView.LayoutManager lm = grid == null ? null : grid.getLayoutManager();
        if (lm instanceof GridLayoutManager) {
            return ((GridLayoutManager) lm).findFirstVisibleItemPosition();
        }
        if (lm instanceof StaggeredGridLayoutManager) {
            int[] pos = ((StaggeredGridLayoutManager) lm)
                    .findFirstVisibleItemPositions(null);
            int min = -1;
            for (int p : pos) if (p >= 0 && (min < 0 || p < min)) min = p;
            return min;
        }
        return -1;
    }

    /** 重建滚动位消费：首个回包落数后夹位滚回，普通翻页不触发。 */
    private void consumeRestoreScroll() {
        if (restoreScrollPos < 0) return;
        int count = channel == TAB_CLIPS ? clips.size() : items.size();
        int target = restoreScrollPos;
        restoreScrollPos = -1;
        if (count > 0 && target > 0) {
            grid.scrollToPosition(Math.min(target, count - 1));
        }
    }

    private int lastVisiblePosition() {
        RecyclerView.LayoutManager lm = grid.getLayoutManager();
        if (lm instanceof GridLayoutManager) {
            return ((GridLayoutManager) lm).findLastVisibleItemPosition();
        }
        if (lm instanceof StaggeredGridLayoutManager) {
            int[] pos = ((StaggeredGridLayoutManager) lm)
                    .findLastVisibleItemPositions(null);
            int max = -1;
            for (int p : pos) max = Math.max(max, p);
            return max;
        }
        return -1;
    }

    private void applyLayout() {
        if (channel == TAB_CLIPS) {
            grid.setLayoutManager(new StaggeredGridLayoutManager(2,
                    StaggeredGridLayoutManager.VERTICAL));
            grid.setAdapter(clipAdapter);
            chipArea.setVisibility(View.GONE);
        } else {
            grid.setLayoutManager(new GridLayoutManager(this, 2));
            grid.setAdapter(gridAdapter);
            rebuildChips();
        }
    }

    private View buildBottomBar() {
        // 底栏（他定案：纯文字签、不要图标；剧场版浅白底，定稿
        // .tabbar）：顶线＋浅白底，选中靠字色＋字重分；底部 padding
        // 给系统手势区让位，文字不掉进手势区。三签均分全宽（件四，
        // 他 17:2x 点名分布不均）：旧落码签组左右收 64dp、且 wrap 与
        // bar 都是默认 wrap 宽入父，权重分配没锚在窗宽上——左右垫
        // 摘除、两层容器宽度都锚满、三格 weight 等分（与首页/我的页
        // 同口径，定稿 v6 均分三等分）。
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(0xFFFCFBF9);
        View line = new View(this);
        line.setBackgroundColor(0x121B1B1F);
        wrap.addView(line, new LinearLayout.LayoutParams(-1, 1));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, dp(8), 0, dp(10) + navBarHeight());
        bar.addView(bottomCell("首页", false, v -> goHomeFeed()),
                new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(bottomCell("剧场", true, null),
                new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(bottomCell("我的", false,
                        v -> HongguoMineActivity.startTab(this)),
                new LinearLayout.LayoutParams(0, -2, 1f));
        wrap.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private View bottomCell(String label, boolean selected, View.OnClickListener click) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(16.5f);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(10), 0, dp(10));
        t.setLetterSpacing(0.04f);
        t.setTextColor(selected ? 0xFF1B1B1F : 0xFFA3A3AB);
        if (selected) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        if (click != null) t.setOnClickListener(click);
        return t;
    }

    /** 回短剧首页（底栏「首页」格专用）：不销毁本页，把首页提到
     * 前台，本页留在任务栈里常驻（与首页同口径），再进剧场原样
     * 回来。注意返回键不走这里——#5 定根：旧 onBackPressed 复用
     * 本法不 finish，本页就恒垫在所有签层页正下方，哪儿按返回都
     * 落剧场；返回键已改回标准 finish，从哪进回哪去。 */
    private void goHomeFeed() {
        HongguoFeedActivity.start(this);
    }

    @Override
    public void finish() {
        super.finish();
        HgTransition.applyFadeExit(this);
    }

    /** 切格：离开的格整份存下，目标格有存货就原样恢复（数据/筹/
     * 滚动都不动、不重拉），没存货或上次没拉成才整格新拉。 */
    private void switchTab(int idx) {
        saveTabState(channel);
        loadGen++;
        loading = false;
        channel = idx;
        selectTab(); // 签高亮立即跟手，内容区走淡交、不硬切
        TabState st = tabStates.get(idx);
        if (st != null && st.settled) {
            // 有存货整份恢复也走淡交（HgTransition 共享口径）：整表
            // 绑定/换布局/滚动恢复全落在淡出后的不可见帧，不许原地砸出
            HgTransition.fadeSwap(() -> restoreTabState(st), grid, chipArea);
        } else {
            activeDimKey = null;
            activeDimValue = null;
            pendingContentFade = true;
            HgTransition.fadeSwap(() -> {
                rebuildChips();
                applyLayout();
                load(true);
            }, grid, chipArea);
        }
        ensurePanel(idx);
    }

    private void saveTabState(int ch) {
        TabState s = new TabState();
        s.items.addAll(items);
        s.clips.addAll(clips);
        s.continuation = continuation;
        s.hasMore = hasMore;
        s.reqFilter = reqFilter;
        s.filterOffset = filterOffset;
        s.filterSession = filterSession;
        s.filterSeen.addAll(filterSeen);
        s.clipChain = clipChain;
        s.aiChain = aiChain;
        s.fillChain = fillChain;
        s.dimKey = activeDimKey;
        s.dimValue = activeDimValue;
        s.settled = pageSettled;
        RecyclerView.LayoutManager lm = grid.getLayoutManager();
        s.scrollState = lm == null ? null : lm.onSaveInstanceState();
        tabStates.put(ch, s);
    }

    private void restoreTabState(TabState s) {
        items.clear();
        items.addAll(s.items);
        clips.clear();
        clips.addAll(s.clips);
        continuation = s.continuation;
        hasMore = s.hasMore;
        reqFilter = s.reqFilter;
        filterOffset = s.filterOffset;
        filterSession = s.filterSession;
        filterSeen.clear();
        filterSeen.addAll(s.filterSeen);
        clipChain = s.clipChain;
        aiChain = s.aiChain;
        fillChain = s.fillChain;
        activeDimKey = s.dimKey;
        activeDimValue = s.dimValue;
        pageSettled = s.settled;
        loading = false;
        selectTab();
        applyLayout();
        gridAdapter.notifyDataSetChanged();
        clipAdapter.notifyDataSetChanged();
        boolean empty = channel == TAB_CLIPS ? clips.isEmpty() : items.isEmpty();
        stateText.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) stateText.setText("暂时没有内容，点按重试");
        if (s.scrollState != null && grid.getLayoutManager() != null) {
            grid.getLayoutManager().onRestoreInstanceState(s.scrollState);
        }
        maybeFill();
    }

    private void selectTab() {
        for (int i = 0; i < tabViews.length; i++) {
            boolean on = i == channel;
            tabViews[i].setTextColor(on ? 0xFF1B1B1F : 0xFF6D6D75);
            tabViews[i].setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
            tabViews[i].setBackground(on ? roundBg(Color.WHITE, dp(10)) : null);
            tabViews[i].setElevation(on ? dp(2) : 0);
        }
    }

    private GradientDrawable roundBg(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    // ---------- 小分类筹（官方筛选面两行：主题＋设定） ----------

    private static final String DIM_THEME = "category_dim_theme";
    private static final String DIM_ROLE = "category_dim_role";

    /** 格下标→体裁（筛选面 genre 取值兼 scene 映射源）；视频格无筛选面。 */
    private static String genreOf(int ch) {
        if (ch == 0) return "short_play";
        if (ch == TAB_MANJU) return "comic_series";
        if (ch == TAB_AI) return "ai_series";
        return null;
    }

    private DramaFilterRow panelRow(int ch, String dimKey) {
        List<DramaFilterRow> rows = panelCache.get(ch);
        if (rows == null) return null;
        for (DramaFilterRow r : rows) if (dimKey.equals(r.key)) return r;
        return null;
    }

    /** 面板按格拉一份（内存缓存）；拉败不显筹、频道流照常，本页内不重试。 */
    private void ensurePanel(final int ch) {
        String genre = genreOf(ch);
        if (genre == null || panelCache.containsKey(ch)
                || panelPending.contains(ch) || panelDead.contains(ch)) return;
        panelPending.add(ch);
        DramaClient.fetchFilterPanel(genre, new HgCallback<List<DramaFilterRow>>() {
            @Override
            public void onSuccess(List<DramaFilterRow> rows) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    panelPending.remove(ch);
                    panelCache.put(ch, rows);
                    if (channel == ch) {
                        boolean chipWasShown = chipArea.getVisibility() == View.VISIBLE;
                        rebuildChips();
                        // 筹行后到：整行淡入，不许凭空砸出（切格淡交
                        // 进行中时 alpha 归淡交统一管，这里不插手）
                        if (!chipWasShown && chipArea.getVisibility() == View.VISIBLE
                                && grid.getAlpha() > 0.99f) {
                            HgTransition.fadeIn(chipArea, 150);
                        }
                    }
                });
            }

            @Override
            public void onError(int code, String msg) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    panelPending.remove(ch);
                    panelDead.add(ch);
                });
            }
        });
    }

    private void rebuildChips() {
        themeRow.removeAllViews();
        roleRow.removeAllViews();
        themeChipViews.clear();
        roleChipViews.clear();
        if (channel == TAB_CLIPS) {
            chipArea.setVisibility(View.GONE);
            return;
        }
        boolean themeShown = fillChipRow(themeRow, themeChipViews, panelRow(channel, DIM_THEME));
        boolean roleShown = fillChipRow(roleRow, roleChipViews, panelRow(channel, DIM_ROLE));
        themeScroll.setVisibility(themeShown ? View.VISIBLE : View.GONE);
        roleScroll.setVisibility(roleShown ? View.VISIBLE : View.GONE);
        chipArea.setVisibility(themeShown || roleShown ? View.VISIBLE : View.GONE);
        styleChips();
    }

    /** 一行筹：行首「全部」（清本行筛选）＋面板选项；行无选项整行不显。 */
    private boolean fillChipRow(LinearLayout rowView, List<TextView> views, DramaFilterRow row) {
        if (row == null || row.options.isEmpty()) return false;
        final String dimKey = row.key;
        views.add(makeChip(dimKey, null, "全部"));
        rowView.addView(views.get(0));
        for (DramaFilterRow.Option op : row.options) {
            TextView t = makeChip(dimKey, op.value, op.name);
            views.add(t);
            rowView.addView(t);
        }
        return true;
    }

    private TextView makeChip(final String dimKey, final String value, String name) {
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(13.5f);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(7), dp(14), dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        t.setTag(value);
        t.setOnClickListener(v -> {
            // 跨行单选（他亲令）：选任一维度的值即按它筛、另一行清选；
            // 点任一行「全部」清筛选回本格默认流；同值再点无动作。
            if (value == null) {
                if (activeDimKey == null) return;
                activeDimKey = null;
                activeDimValue = null;
            } else {
                if (dimKey.equals(activeDimKey) && value.equals(activeDimValue)) return;
                activeDimKey = dimKey;
                activeDimValue = value;
            }
            styleChips();
            pendingContentFade = true;
            HgTransition.fadeSwap(() -> load(true), grid);
        });
        return t;
    }

    private void styleChips() {
        styleChipRow(themeChipViews, DIM_THEME);
        styleChipRow(roleChipViews, DIM_ROLE);
    }

    private void styleChipRow(List<TextView> views, String dimKey) {
        for (int i = 0; i < views.size(); i++) {
            TextView t = views.get(i);
            boolean on = i == 0 ? activeDimKey == null
                    : dimKey.equals(activeDimKey) && String.valueOf(activeDimValue).equals(t.getTag());
            t.setTextColor(on ? Color.WHITE : 0xFF4C4C54);
            t.setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
            t.setBackground(roundBg(on ? 0xFF1B1B1F : 0x0B1B1B1F, dp(20)));
        }
    }

    // ---------- 取数 ----------

    private void load(boolean first) {
        if (loading) return;
        loading = true;
        final int gen = ++loadGen;
        reqFilter = activeDimValue != null && channel != TAB_CLIPS;
        if (first) {
            pageSettled = false;
            items.clear();
            clips.clear();
            gridAdapter.notifyDataSetChanged();
            clipAdapter.notifyDataSetChanged();
            continuation = null;
            filterOffset = 0;
            filterSession = null;
            filterSeen.clear();
            clipChain = 0;
            aiChain = 0;
            fillChain = 0;
            hasMore = false;
            stateText.setText("加载中…");
            stateText.setVisibility(View.VISIBLE);
        }
        HgCallback<DramaCellPage> cb = new HgCallback<DramaCellPage>() {
            @Override
            public void onSuccess(DramaCellPage page) {
                runOnUiThread(() -> {
                    if (!destroyed && gen == loadGen) onPage(page, first);
                });
            }

            @Override
            public void onError(int code, String msg) {
                runOnUiThread(() -> {
                    if (destroyed || gen != loadGen) return;
                    loading = false;
                    pendingContentFade = false;
                    if ((channel == TAB_CLIPS ? clips : items).isEmpty()) {
                        stateText.setText("取数据失败，点按重试");
                        stateText.setVisibility(View.VISIBLE);
                    }
                });
            }
        };
        if (channel == TAB_CLIPS) {
            if (first || continuation == null) DramaClient.fetchChannel(-1, 16, cb);
            else DramaClient.fetchChannelMore(continuation, cb);
        } else if (reqFilter) {
            // 筛选路：官方筛选面内容口，游标（offset/session/已展示表）
            // 由本页持有递进；服务端按 scene 分格，回包不再客户端分格。
            DramaClient.fetchFilterList(genreOf(channel), activeDimKey, activeDimValue,
                    first ? 0 : filterOffset, first ? null : filterSession,
                    new ArrayList<>(filterSeen), cb);
        } else if (channel == TAB_AI) {
            // AI剧格数据源＝发现流（与视频格同口），回包在 onPage 按
            // 服务端类型类 1004_ai_video 过滤成格。
            if (first || continuation == null) DramaClient.fetchChannel(-1, 16, cb);
            else DramaClient.fetchChannelMore(continuation, cb);
        } else if (first || continuation == null) {
            int[] ch = CHANNELS[channel];
            DramaClient.fetchChannel(ch[0], ch[1], cb);
        } else {
            DramaClient.fetchChannelMore(continuation, cb);
        }
    }

    /**
     * 频道/发现流续页推进（旧版两断已修，账 hongguo-chip-fix-20261030
     * §三）：开页 tab 级 has_more 恒 false，靠续页凭证在位判有下页；
     * 续页回包自带真实 has_more 与 next_offset，凭证逐页推进
     * （HgContinuation.advanced——旧版从不推进、续拉恒拉同一页）。
     */
    private void advanceContinuation(DramaCellPage page, boolean first) {
        if (page.getContinuation() != null) {
            continuation = page.getContinuation();
        } else if (!first && continuation != null && page.getNextOffset() != null) {
            continuation = continuation.advanced(page.getNextOffset(), page.getSessionId());
        }
        hasMore = page.isHasMore() || (first && continuation != null);
    }

    /**
     * 切格/点筹后首批真出货的数据到达：整表淡入承接（HgTransition
     * 口径），不许数据直接砸进可见表；整页滤空自动续拉时不消费，
     * 把淡入留给真正出货的那一页。
     */
    private void arriveFadeIn(boolean addedAny) {
        if (!pendingContentFade || !addedAny) return;
        pendingContentFade = false;
        grid.setAlpha(0f);
        HgTransition.contentIn(grid);
    }

    /**
     * 填充续拉（1.4.63 真机定根，账 hongguo-1463-triage2-20261030）：
     * 频道开页每格只带 3 条精选（探针实测 tab39/tab36 开页各 3 条、
     * 续页凭证在位、hasMore 靠凭证判 true），3 条一格不满屏——网格
     * 根本滑不动，滚动监听（dy&gt;0 才查尾）永不触发，链死在第一页、
     * 下面整片黑。出页/恢复后若已在尾位（或内容不满屏）且还有下页，
     * 不等滚动直接续拉补满；零新增连着来封顶 3 次防死循环，有新增
     * 即清零。与 clipChain/aiChain 的整页滤空续拉并存（那两条在本
     * 方法检查前已同步发车时，loading 在位、本检查自动让路）。
     */
    private void maybeFill() {
        grid.post(() -> {
            if (loading || !hasMore || fillChain >= 3) return;
            int count = channel == TAB_CLIPS ? clips.size() : items.size();
            if (count == 0) return;
            if (lastVisiblePosition() >= count - 6) load(false);
        });
    }

    private void onPage(DramaCellPage page, boolean first) {
        loading = false;
        pageSettled = true;
        if (channel == TAB_CLIPS) {
            advanceContinuation(page, first);
            Set<String> seen = new HashSet<>();
            for (DramaItem it : clips) {
                String key = it.getVid() != null ? it.getVid() : it.getSeriesId();
                if (key != null) seen.add(key);
            }
            int added = 0;
            for (DramaItem it : page.getItems()) {
                if (!isVideoSide(it)) continue;
                String key = it.getVid() != null ? it.getVid() : it.getSeriesId();
                if (key == null || !seen.add(key)) continue;
                clips.add(it);
                added++;
            }
            clipAdapter.notifyDataSetChanged();
            consumeRestoreScroll();
            arriveFadeIn(added > 0);
            stateText.setVisibility(clips.isEmpty() ? View.VISIBLE : View.GONE);
            if (clips.isEmpty()) stateText.setText("暂时没有内容，点按重试");
            // 整页被过滤光但还有下页：自动续拉（最多连 3 页），免得短片格假空。
            if (added == 0 && hasMore && clipChain < 3) {
                clipChain++;
                load(false);
            }
            fillChain = added > 0 ? 0 : fillChain + 1;
            maybeFill();
            return;
        }
        if (reqFilter) {
            // 筛选游标递进：offset/session 取回包；已展示 id 全数登记
            // （含被下方去重跳过的——服务端按 filter_ids 排除，登记
            // 越全翻页越不重）。筛选回包按 scene 天然分格，不再走
            // 下面的客户端分格过滤。
            String no = page.getNextOffset();
            if (no != null) {
                try {
                    filterOffset = Integer.parseInt(no);
                } catch (NumberFormatException e) {
                    filterOffset += page.getItems().size();
                }
            } else {
                filterOffset += page.getItems().size();
            }
            if (page.getSessionId() != null) filterSession = page.getSessionId();
            hasMore = page.isHasMore();
            for (DramaItem it : page.getItems()) {
                if (it.getSeriesId() != null) filterSeen.add(it.getSeriesId());
            }
        } else {
            advanceContinuation(page, first);
        }
        // 分格过滤（服务端类型类为准，不靠标题猜）：AI 格只收
        // 1004_ai_video（频道面另加 SERIES 型门），漫剧格剔除 AI 类收纯。
        List<DramaItem> src = page.getItems();
        if (!reqFilter && channel == TAB_AI) {
            List<DramaItem> f = new ArrayList<>();
            for (DramaItem it : src) {
                if (it.kind == DramaItemKind.SERIES && it.isAiDrama()) f.add(it);
            }
            src = f;
        } else if (!reqFilter && channel == TAB_MANJU) {
            List<DramaItem> f = new ArrayList<>();
            for (DramaItem it : src) if (!it.isAiDrama()) f.add(it);
            src = f;
        }
        Set<String> seen = new HashSet<>();
        for (DramaItem it : items) if (it.getSeriesId() != null) seen.add(it.getSeriesId());
        int added = 0;
        for (DramaItem it : src) {
            String sid = it.getSeriesId();
            if (sid == null || !seen.add(sid)) continue;
            items.add(it);
            added++;
        }
        gridAdapter.notifyDataSetChanged();
        consumeRestoreScroll();
        arriveFadeIn(added > 0);
        // AI 格续拉策略（B-15）：数据源是发现流、靠客户端过滤成格，
        // 密度低时整页只出一两条——旧法只在整页滤空（added==0）才
        // 连拉且封顶 4 页，低密度页一出货就停链，首屏攒不满；且空
        // 表期每页都先挂「暂时没有内容」＝假空。改按目标攒：未满
        // AI_FILL_TARGET 且有下页就继续连拉，单轮预算 AI_CHAIN_MAX
        // 页封顶；攒够即清预算，此后走滚动续拉常路。
        boolean aiChaining = false;
        if (channel == TAB_AI && !reqFilter) {
            if (items.size() >= AI_FILL_TARGET) {
                aiChain = 0;
            } else if (hasMore && aiChain < AI_CHAIN_MAX) {
                aiChain++;
                aiChaining = true;
                load(false);
            }
        }
        stateText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        if (items.isEmpty()) {
            // 连拉途中只挂「加载中…」，不许先报假空（B-15）
            stateText.setText(aiChaining ? "加载中…" : "暂时没有内容，点按重试");
        }
        fillChain = added > 0 ? 0 : fillChain + 1;
        maybeFill();
    }

    /**
     * 视频格放行判据（纯度单 hongguo-video-purity-20261030，他定
     * 「只要视频流不混入短剧就行」、宁可错杀）：kind 是解析入口的
     * 定型结论，这里再用条目自带的协议原值复核一遍——剧类
     * content_type（1/1004/1007）或剧类服务端类型类（cct＝
     * 1/1004/1004_ai_video）一票否决，防定型被单字段带偏时短剧
     * 漏进；短片须 kind＝UGC_CLIP 且 content_type＝1001 双合，
     * 合集须 kind＝ALBUM 且专辑身份在位，其余（定型含糊、字段
     * 缺失）一律不进本格。
     */
    private static boolean isVideoSide(DramaItem it) {
        if (it.contentType == 1 || it.contentType == 1004 || it.contentType == 1007) return false;
        String cct = it.contentCategoryType;
        if ("1".equals(cct) || "1004".equals(cct) || "1004_ai_video".equals(cct)) return false;
        if (it.kind == DramaItemKind.UGC_CLIP) return it.contentType == 1001;
        if (it.kind == DramaItemKind.ALBUM) return it.getAlbumId() != null;
        return false;
    }

    /** 定稿：点封面直接进播放页开播（从断点集），不绕详情页。 */
    private void openDrama(DramaItem item) {
        if (item.getSeriesId() == null) return;
        HongguoFeedActivity.startSeries(this, item.getSeriesId(), item.getTitle(), 0);
    }

    /** 瀑布封面真比例缓存（封面 URL → 高/宽）：回包无封面宽高字段
     * （cover_size 是样式旗、已实拉核过），真比例取封面图自身
     * 宽高比，载入后回填，滚回不再跳变。 */
    private final Map<String, Float> clipRatioCache = new HashMap<>();

    /**
     * 定稿热度焰（草稿 flameSvg：24 视框 path、填充 #FFB03A）自绘件：
     * 树里无现成 drawable，按草稿 path 原文绘制，不跳过。
     */
    static final class FlameDrawable extends Drawable {
        private static final String PATH_DATA =
                "M12 2s1 2.7-1.2 5.2C9.2 9 7.5 10.3 7.5 13a4.5 4.5 0 0 0 9 0"
                        + "c0-1.2-.5-2.2-1-3.2-.3 1-1 1.9-2 2.4.3-2.7-.5-7.4-1.5-10.2z"
                        + "M12 22a6.8 6.8 0 0 1-6.8-6.8c0-2.4 1.2-4.2 2.6-5.9"
                        + ".3 1 .9 1.9 1.7 2.5C9.8 9.9 10.6 6.9 12 2c1.6 3.1 2.2 6.1 1.9 8.7"
                        + ".8-.6 1.4-1.4 1.7-2.4 1 1.5 2.2 3.6 2.2 6A6.8 6.8 0 0 1 12 22z";
        private final Path path = PathParser.createPathFromPathData(PATH_DATA);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        FlameDrawable() {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFB03A);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            canvas.save();
            canvas.translate(b.left, b.top);
            canvas.scale(b.width() / 24f, b.height() / 24f);
            canvas.drawPath(path, paint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    /** 定稿：视频签点卡进视频页——合集走专辑队列、单条走单条形态。 */
    private void openClip(DramaItem item) {
        if (item.kind == DramaItemKind.ALBUM) {
            HongguoFeedActivity.startVideoAlbum(this, item);
            return;
        }
        HongguoFeedActivity.startVideo(this, item);
    }

    // ---------- 正剧网格 ----------

    private class GridAdapter extends RecyclerView.Adapter<GridAdapter.H> {

        class H extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView score;
            final View strip;
            final TextView eps;
            final ImageView flame;
            final TextView heat;
            final TextView title;
            final TextView desc;

            H(LinearLayout cell) {
                super(cell);
                cover = cell.findViewWithTag("cover");
                score = cell.findViewWithTag("score");
                strip = cell.findViewWithTag("strip");
                eps = cell.findViewWithTag("eps");
                flame = cell.findViewWithTag("flame");
                heat = cell.findViewWithTag("heat");
                title = cell.findViewWithTag("title");
                desc = cell.findViewWithTag("desc");
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 海报 aspect 1.43（实测封面 400×572）、整框裁圆 16、
            // 占位 #E8E6E2；右上评分角标（金字深底）；底部渐变条左
            // 集数右热度；卡下标题 15/700＋简介两行 12.5 sub。
            LinearLayout cell = new LinearLayout(HongguoTheaterActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            FrameLayout box = new FrameLayout(HongguoTheaterActivity.this);
            box.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
                }
            });
            box.setClipToOutline(true);
            ImageView cover = new ImageView(HongguoTheaterActivity.this);
            cover.setTag("cover");
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(roundBg(0xFFE8E6E2, dp(16)));
            box.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            TextView score = new TextView(HongguoTheaterActivity.this);
            score.setTag("score");
            score.setTextColor(0xFFFFC44D);
            score.setTextSize(10.5f);
            score.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            score.setPadding(dp(7), dp(3), dp(7), dp(3));
            score.setBackground(roundBg(0x850A0806, dp(7)));
            FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2);
            slp.gravity = Gravity.TOP | Gravity.RIGHT;
            slp.rightMargin = dp(7);
            slp.topMargin = dp(7);
            box.addView(score, slp);
            LinearLayout strip = new LinearLayout(HongguoTheaterActivity.this);
            strip.setTag("strip");
            strip.setOrientation(LinearLayout.HORIZONTAL);
            strip.setGravity(Gravity.BOTTOM);
            strip.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x00000000, 0x61080605, 0xBD080605}));
            strip.setPadding(dp(9), dp(22), dp(9), dp(7));
            TextView eps = new TextView(HongguoTheaterActivity.this);
            eps.setTag("eps");
            eps.setTextColor(Color.WHITE);
            eps.setTextSize(10.5f);
            eps.setMaxLines(1);
            strip.addView(eps, new LinearLayout.LayoutParams(0, -2, 1f));
            // 热度前焰（定稿 flameSvg 11px #FFB03A，树里无 drawable，
            // 按草稿 path 自绘 FlameDrawable，不得跳过）
            ImageView flame = new ImageView(HongguoTheaterActivity.this);
            flame.setTag("flame");
            flame.setImageDrawable(new FlameDrawable());
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(dp(11), dp(11));
            flp.rightMargin = dp(3);
            strip.addView(flame, flp);
            TextView heat = new TextView(HongguoTheaterActivity.this);
            heat.setTag("heat");
            heat.setTextColor(Color.WHITE);
            heat.setTextSize(10.5f);
            heat.setMaxLines(1);
            strip.addView(heat);
            FrameLayout.LayoutParams stripLp = new FrameLayout.LayoutParams(-1, -2);
            stripLp.gravity = Gravity.BOTTOM;
            box.addView(strip, stripLp);
            int w = (getResources().getDisplayMetrics().widthPixels - dp(48)) / 2;
            cell.addView(box, new LinearLayout.LayoutParams(-1, (int) (w * 1.43f)));
            TextView title = new TextView(HongguoTheaterActivity.this);
            title.setTag("title");
            title.setTextColor(0xFF1B1B1F);
            title.setTextSize(15);
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.topMargin = dp(10);
            tlp.leftMargin = dp(1);
            cell.addView(title, tlp);
            TextView desc = new TextView(HongguoTheaterActivity.this);
            desc.setTag("desc");
            desc.setTextColor(0xFF8A8A93);
            desc.setTextSize(12.5f);
            desc.setMaxLines(2);
            desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
            dlp.topMargin = dp(3);
            dlp.leftMargin = dp(1);
            cell.addView(desc, dlp);
            return new H(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            DramaItem item = items.get(position);
            // 两列格距 12：左列右半距、右列左半距（格内 padding 承担）
            h.itemView.setPadding(position % 2 == 0 ? 0 : dp(6), 0,
                    position % 2 == 0 ? dp(6) : 0, dp(18));
            if (item.getCoverUrl() != null && !item.getCoverUrl().isEmpty()) {
                HgCoverClip.round(h.cover, dp(16));
                Glide.with(HongguoTheaterActivity.this).load(item.getCoverUrl())
                        .into(h.cover);
            } else {
                h.cover.setImageDrawable(null);
            }
            String score = item.scoreChipPos >= 0 && item.infoChips != null
                    && item.scoreChipPos < item.infoChips.size()
                    ? item.infoChips.get(item.scoreChipPos) : null;
            h.score.setVisibility(score != null && !score.isEmpty()
                    ? View.VISIBLE : View.GONE);
            if (score != null) h.score.setText(score);
            int total = item.getEpisodeCount();
            String epsText = total > 0
                    ? (item.isFinished() ? "全 " + total + " 集" : "更新至 " + total + " 集")
                    : "";
            h.eps.setText(epsText);
            String heatText = item.getRecText() == null ? "" : item.getRecText();
            h.heat.setText(heatText);
            h.flame.setVisibility(heatText.isEmpty() ? View.GONE : View.VISIBLE);
            h.strip.setVisibility(epsText.isEmpty() && heatText.isEmpty()
                    ? View.GONE : View.VISIBLE);
            h.title.setText(item.getTitle() == null ? "" : item.getTitle());
            h.desc.setText(item.getDesc() == null ? "" : item.getDesc());
            h.itemView.setOnClickListener(v -> openDrama(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    // ---------- 短片瀑布 ----------

    private class ClipAdapter extends RecyclerView.Adapter<ClipAdapter.H> {

        class H extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView score;
            final TextView title;
            final TextView sub;

            H(LinearLayout cell) {
                super(cell);
                cover = cell.findViewWithTag("cover");
                score = cell.findViewWithTag("score");
                title = cell.findViewWithTag("title");
                sub = cell.findViewWithTag("sub");
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout cell = new LinearLayout(HongguoTheaterActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            FrameLayout frame = new FrameLayout(HongguoTheaterActivity.this);
            ImageView cover = new ImageView(HongguoTheaterActivity.this);
            cover.setTag("cover");
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(roundBg(0xFFE8E6E2, dp(16)));
            frame.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            TextView score = new TextView(HongguoTheaterActivity.this);
            score.setTag("score");
            score.setTextColor(0xFFFFC44D);
            score.setTextSize(10.5f);
            score.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            score.setPadding(dp(7), dp(3), dp(7), dp(3));
            score.setBackground(roundBg(0x850A0806, dp(7)));
            FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2);
            slp.gravity = Gravity.TOP | Gravity.RIGHT;
            slp.rightMargin = dp(7);
            slp.topMargin = dp(7);
            frame.addView(score, slp);
            cell.addView(frame, new LinearLayout.LayoutParams(-1, -2));
            frame.setTag("frame");
            TextView title = new TextView(HongguoTheaterActivity.this);
            title.setTag("title");
            title.setTextColor(0xFF1B1B1F);
            title.setTextSize(14.5f);
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.topMargin = dp(9);
            tlp.leftMargin = dp(1);
            cell.addView(title, tlp);
            TextView sub = new TextView(HongguoTheaterActivity.this);
            sub.setTag("sub");
            sub.setTextColor(0xFF8A8A93);
            sub.setTextSize(12);
            sub.setMaxLines(1);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
            subLp.topMargin = dp(2);
            subLp.leftMargin = dp(1);
            cell.addView(sub, subLp);
            return new H(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            DramaItem item = clips.get(position);
            h.itemView.setPadding(position % 2 == 0 ? 0 : dp(6), 0,
                    position % 2 == 0 ? dp(6) : 0, dp(16));
            // 封面真比例（打回工单⑧：禁按 id 哈希派值）：回包无封面
            // 宽高字段，真比例取封面图自身宽高比；载入前先用条目
            // 横竖向（vertical 字段）占位，图到后按真比例回填并缓存。
            int w = (getResources().getDisplayMetrics().widthPixels - dp(48)) / 2;
            final View frame = (View) h.cover.getParent();
            String coverUrl = item.getCoverUrl();
            Float cached = coverUrl == null ? null : clipRatioCache.get(coverUrl);
            float ratio = cached != null ? cached
                    : (Boolean.FALSE.equals(item.getVertical()) ? 0.75f : 4f / 3f);
            ViewGroup.LayoutParams flp = frame.getLayoutParams();
            flp.height = (int) (w * ratio);
            frame.setLayoutParams(flp);
            if (coverUrl != null && !coverUrl.isEmpty()) {
                HgCoverClip.round(h.cover, dp(16));
                Glide.with(HongguoTheaterActivity.this).load(coverUrl)
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                          Target<Drawable> target, boolean first) {
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Drawable resource, Object model,
                                                           Target<Drawable> target,
                                                           DataSource source, boolean first) {
                                int iw = resource.getIntrinsicWidth();
                                int ih = resource.getIntrinsicHeight();
                                if (iw > 0 && ih > 0 && model != null) {
                                    float r = ih / (float) iw;
                                    r = Math.max(0.6f, Math.min(1.9f, r));
                                    Float old = clipRatioCache.put(String.valueOf(model), r);
                                    if (old == null || Math.abs(old - r) > 0.01f) {
                                        ViewGroup.LayoutParams lp = frame.getLayoutParams();
                                        lp.height = (int) (w * r);
                                        frame.setLayoutParams(lp);
                                    }
                                }
                                return false;
                            }
                        })
                        .into(h.cover);
            } else {
                h.cover.setImageDrawable(null);
            }
            String score = item.scoreChipPos >= 0 && item.infoChips != null
                    && item.scoreChipPos < item.infoChips.size()
                    ? item.infoChips.get(item.scoreChipPos) : null;
            h.score.setVisibility(score != null && !score.isEmpty()
                    ? View.VISIBLE : View.GONE);
            if (score != null) h.score.setText(score);
            h.title.setText(item.getTitle() == null ? "" : item.getTitle());
            // 副行（「类 · 热度文案」）：合集以「合集」领起、单条
            // 以首个分类签领起；后段是 recText 热度文案原文（形如
            // 「208万热度」），照原文显。W-10/B-16 语义纠正（他
            // 亲裁「改」）：此前剥尾「热度」二字再拼「 播放」，把
            // 热度值冒充播放量；recText 本义就是热度，首页流热度
            // 筹、搜索卡、正剧卡皆原文直显，此处对齐同一口径。
            boolean album = item.kind == DramaItemKind.ALBUM;
            String lead = album ? "合集"
                    : (item.getTags() != null && !item.getTags().isEmpty()
                            ? item.getTags().get(0) : null);
            String heat = item.getRecText();
            StringBuilder sb = new StringBuilder();
            if (lead != null && !lead.isEmpty()) sb.append(lead);
            if (heat != null && !heat.isEmpty()) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(heat);
            }
            h.sub.setText(sb.toString());
            h.itemView.setOnClickListener(v -> openClip(item));
        }

        @Override
        public int getItemCount() {
            return clips.size();
        }
    }
}
