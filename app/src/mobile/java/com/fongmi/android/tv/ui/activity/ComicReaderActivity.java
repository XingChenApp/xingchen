package com.fongmi.android.tv.ui.activity;
import com.fongmi.android.tv.utils.ToastUtil;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.hk.HkPlay;
import com.fongmi.android.tv.api.hk.HkRouter;
import com.fongmi.android.tv.read.ReadingGate;
import com.fongmi.android.tv.read.ReadingSession;
import com.fongmi.android.tv.read.ReadingStore;
import com.fongmi.android.tv.read.ReadDialogs;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.XcFadeTransition;

import java.util.ArrayList;
import java.util.List;
import com.fongmi.android.tv.R;

/**
 * 漫画阅读器：全新设计，不抄 8.83 PictureListActivity 的交互。
 * 竖滑连读（默认）/ 横向可切、Glide 预加载、章节抽屉、滚动进度、点按呼出控制栏。
 * 内容区干净：控制栏默认隐藏，点按才出（该藏的藏）。
 */
public class ComicReaderActivity extends AppCompatActivity {

    private static final String PREFS = "reading_pref";
    private static final String KEY_DIRECTION = "comic_direction_h";
    private static final String KEY_RTL = "comic_rtl";
    private static final String KEY_DUAL_PAGE = "comic_dual_page";
    private static final String KEY_VOLUME_PAGE = "comic_volume_page";
    private static final String KEY_KEEP_SCREEN = "comic_keep_screen";

    private ReadingSession session;
    private RecyclerView rv;
    private ImgAdapter adapter;
    private final List<String> images = new ArrayList<>();
    private View topBar;
    private View bottomBar;
    private TextView tvTitle;
    private TextView tvSub;
    private TextView tvPageLeft;
    private TextView tvPct;
    private SeekBar seekBar;
    private LinearLayout modeSeg;
    private TextView dirSegA;
    private TextView dirSegB;
    private boolean barsShown = false;
    private boolean horizontal = false;
    private boolean loading = false;
    private boolean firstLoad = true;
    private long lastSaveAt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // fitWindowBackground 已废弃（窗口底恒被 ThemeManager.doApply 重设为壁纸）：
        // 透明状态栏与图标明暗由下一行的 ReadingSkin.enableEdgeToEdge 负责（深色页固定浅图标）
        ReadingSkin.enableEdgeToEdge(this, true);
        session = ReadingSession.get(getIntent().getStringExtra("session"));
        if (session == null) {
            ToastUtil.show(this, "阅读会话已失效", Toast.LENGTH_SHORT);
            finish();
            return;
        }
        horizontal = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DIRECTION, false);
        buildUi();
        applyKeepScreen();
        loadChapter(session.chapterIndex, Math.max(0, session.startPage));

        // 页面进出统一渐入渐出（与 BaseActivity 一致）
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
}

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
    }


    // ---------------- 布局：方向 / 从右往左 / 横屏双页 ----------------

    private boolean rtl() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_RTL, false);
    }

    private boolean dualPage() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DUAL_PAGE, false);
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * 横向：从右往左=反向布局；竖向：横屏＋双页开关=双列。
     * 切布局时尽量保住当前页。
     */
    private void applyLayoutManager() {
        int pos = 0;
        try {
            if (rv.getLayoutManager() != null) {
                pos = Math.max(0, ((LinearLayoutManager) rv.getLayoutManager())
                        .findFirstVisibleItemPosition());
            }
        } catch (Throwable ignored) {
        }
        androidx.recyclerview.widget.RecyclerView.LayoutManager lm;
        if (horizontal) {
            lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, rtl());
        } else if (dualPage() && isLandscape()) {
            lm = new androidx.recyclerview.widget.GridLayoutManager(this, 2,
                    LinearLayoutManager.VERTICAL, false);
        } else {
            lm = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        }
        if (lm instanceof LinearLayoutManager) {
            ((LinearLayoutManager) lm).setInitialPrefetchItemCount(4);
        }
        rv.setLayoutManager(lm);
        if (adapter != null) {
            rv.setAdapter(adapter);
            final int fp = Math.min(pos, Math.max(0, images.size() - 1));
            rv.post(() -> scrollToPage(fp));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_VOLUME_PAGE, false)
                && (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) {
            int dir = keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ? 1 : -1;
            if (rtl() && horizontal) dir = -dir;
            scrollToPage(currentPage() + dir);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void applyKeepScreen() {
        boolean on = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_KEEP_SCREEN, true);
        if (on) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ---------------- UI ----------------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        rv = new RecyclerView(this);
        rv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        applyLayoutManager();
        adapter = new ImgAdapter();
        rv.setAdapter(adapter);
        root.addView(rv);

        // 点按（非滚动）呼出/隐藏控制栏
        GestureDetector gd = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        toggleBars();
                        return true;
                    }
                });
        rv.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(RecyclerView r, MotionEvent e) {
                gd.onTouchEvent(e);
                return false;
            }
        });

        topBar = buildTopBar();
        root.addView(topBar);
        bottomBar = buildBottomBar();
        root.addView(bottomBar);
        setBarsVisible(false);

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView r, int dx, int dy) {
                updatePageLabel();
            }

            @Override
            public void onScrollStateChanged(RecyclerView r, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    preloadAhead();
                    saveProgress();
                }
            }
        });

        setContentView(root);
    }

    /** 顶栏（稿）：书名白大＋话数灰，渐隐底。 */
    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        bar.setLayoutParams(lp);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{0xEB08080C, 0x0008080C});
        bar.setBackground(bg);
        bar.setPadding(dp(8), dp(8), dp(16), dp(28));
        ReadingSkin.applyTopInset(bar, this, 8);

        ImageButton back = new ImageButton(this);
        back.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setColorFilter(0xA6F4F2EC);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = dp(4);
        titles.setLayoutParams(tlp);
        tvTitle = new TextView(this);
        tvTitle.setTextColor(ReadingSkin.TEXT);
        tvTitle.setTextSize(17);
        tvTitle.getPaint().setFakeBoldText(true);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(tvTitle);
        tvSub = new TextView(this);
        tvSub.setTextColor(ReadingSkin.SUB);
        tvSub.setTextSize(13);
        tvSub.setSingleLine(true);
        tvSub.setEllipsize(TextUtils.TruncateAt.END);
        tvSub.setPadding(0, dp(4), 0, 0);
        titles.addView(tvSub);
        bar.addView(titles);
        refreshTitle();
        return bar;
    }

    /** 底栏（稿·呼出态）：金进度行＋上一话/目录/下一话＋竖滑/横滑。 */
    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        bar.setLayoutParams(lp);
        bar.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                        new int[]{0xF208080C, 0x0008080C});
        bar.setBackground(bg);
        bar.setPadding(dp(20), dp(28), dp(20), dp(12));
        ReadingSkin.applyBottomInset(bar, this, 12);

        // 进度行：「36 / 52」金左、金进度条、「69%」金右
        LinearLayout prow = new LinearLayout(this);
        prow.setOrientation(LinearLayout.HORIZONTAL);
        prow.setGravity(Gravity.CENTER_VERTICAL);
        tvPageLeft = new TextView(this);
        tvPageLeft.setTextColor(ReadingSkin.GOLD_TEXT);
        tvPageLeft.setTextSize(13);
        prow.addView(tvPageLeft);
        seekBar = new SeekBar(this);
        ReadingSkin.styleGoldSeekBar(this, seekBar);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        slp.leftMargin = dp(12);
        slp.rightMargin = dp(12);
        prow.addView(seekBar, slp);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                scrollToPage(s.getProgress());
            }
        });
        tvPct = new TextView(this);
        tvPct.setTextColor(ReadingSkin.GOLD_TEXT);
        tvPct.setTextSize(13);
        prow.addView(tvPct);
        bar.addView(prow);

        // 上一话 / 目录 / 下一话：深色圆钮
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER);
        btns.setPadding(0, dp(18), 0, 0);
        TextView prev = roundBtn("⏮", dp(56), dp(56));
        prev.setOnClickListener(v -> goChapter(session.chapterIndex - 1));
        TextView toc = roundBtn("目录", dp(84), dp(56));
        toc.setOnClickListener(v -> showChapters());
        TextView next = roundBtn("⏭", dp(56), dp(56));
        next.setOnClickListener(v -> goChapter(session.chapterIndex + 1));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.leftMargin = dp(22);
        gap.rightMargin = dp(22);
        btns.addView(prev);
        btns.addView(toc, gap);
        btns.addView(next);
        bar.addView(btns);

        // 竖滑 / 横滑：两段切换（选中金底深字）
        LinearLayout modeWrap = new LinearLayout(this);
        modeWrap.setOrientation(LinearLayout.HORIZONTAL);
        modeWrap.setGravity(Gravity.CENTER);
        modeWrap.setPadding(0, dp(16), 0, 0);
        modeSeg = new LinearLayout(this);
        modeSeg.setOrientation(LinearLayout.HORIZONTAL);
        modeSeg.setBackground(ReadingSkin.segContainer(this));
        modeSeg.setPadding(dp(3), dp(3), dp(3), dp(3));
        dirSegA = segItem("竖滑");
        dirSegB = segItem("横滑");
        dirSegA.setOnClickListener(v -> setDirection(false));
        dirSegB.setOnClickListener(v -> setDirection(true));
        modeSeg.addView(dirSegA);
        modeSeg.addView(dirSegB);
        modeWrap.addView(modeSeg);
        bar.addView(modeWrap);
        refreshModeSeg();

        // 书签 / 换源 / 设置（功能保留，素字）
        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setGravity(Gravity.CENTER);
        row3.setPadding(0, dp(12), 0, 0);
        TextView markBtn = plainBtn("书签");
        markBtn.setOnClickListener(v -> showBookmarks());
        row3.addView(markBtn);
        TextView csBtn = plainBtn("换源");
        csBtn.setOnClickListener(v -> changeSource());
        row3.addView(csBtn);
        TextView setBtn = plainBtn("设置");
        setBtn.setOnClickListener(v -> showSettings());
        row3.addView(setBtn);
        bar.addView(row3);
        return bar;
    }

    private TextView roundBtn(String s, int w, int h) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setTextColor(ReadingSkin.TEXT);
        t.setGravity(Gravity.CENTER);
        t.setBackground(ReadingSkin.darkCircle(this));
        t.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private TextView segItem(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setPadding(dp(22), dp(9), dp(22), dp(9));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void refreshModeSeg() {
        boolean h = horizontal;
        styleSegItem(dirSegA, !h);
        styleSegItem(dirSegB, h);
    }

    private void styleSegItem(TextView t, boolean selected) {
        if (selected) {
            t.setBackground(ReadingSkin.segSelectedSolid(this));
            t.setTextColor(0xFF0A2115);
            t.getPaint().setFakeBoldText(true);
        } else {
            t.setBackgroundColor(Color.TRANSPARENT);
            t.setTextColor(ReadingSkin.SUB);
            t.getPaint().setFakeBoldText(false);
        }
    }

    private void setDirection(boolean h) {
        if (horizontal == h) return;
        horizontal = h;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_DIRECTION, horizontal).apply();
        refreshModeSeg();
        applyLayoutManager();
    }

    private TextView plainBtn(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(ReadingSkin.SUB);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    // ---------------- 设置 ----------------

    private void showSettings() {
        if (barsShown) setBarsVisible(false);
        String[] items = {
                "从右往左：" + (rtl() ? "开" : "关") + "（横向阅读时）",
                "横屏双页：" + (dualPage() ? "开" : "关"),
                "音量键翻页：" + (getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getBoolean(KEY_VOLUME_PAGE, false) ? "开" : "关"),
                "屏幕常亮：" + (getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getBoolean(KEY_KEEP_SCREEN, true) ? "开" : "关"),
                "清除本书缓存"
        };
        ReadDialogs.options(this, "漫画设置", items, which -> {
                    android.content.SharedPreferences p =
                            getSharedPreferences(PREFS, MODE_PRIVATE);
                    switch (which) {
                        case 0:
                            p.edit().putBoolean(KEY_RTL, !rtl()).apply();
                            applyLayoutManager();
                            ToastUtil.show(this, rtl() ? "已切换为从右往左" : "已切换为从左往右",
                                    Toast.LENGTH_SHORT);
                            break;
                        case 1:
                            p.edit().putBoolean(KEY_DUAL_PAGE, !dualPage()).apply();
                            applyLayoutManager();
                            showSettings();
                            break;
                        case 2:
                            p.edit().putBoolean(KEY_VOLUME_PAGE,
                                    !p.getBoolean(KEY_VOLUME_PAGE, false)).apply();
                            showSettings();
                            break;
                        case 3:
                            p.edit().putBoolean(KEY_KEEP_SCREEN,
                                    !p.getBoolean(KEY_KEEP_SCREEN, true)).apply();
                            applyKeepScreen();
                            showSettings();
                            break;
                        case 4:
                            com.fongmi.android.tv.read.ReadingDownload.clearBook(this, bookKey());
                            ToastUtil.show(this, "本书缓存已清除", Toast.LENGTH_SHORT);
                            break;
                    }
        });
    }

    // ---------------- 书签 ----------------

    private String bookKey() {
        if (!TextUtils.isEmpty(session.localId)) return "local|" + session.localId;
        return "COMIC|" + session.sourceName + "|" + session.bookTitle;
    }

    private void showBookmarks() {
        // 与小说阅读器同令：弹窗开前先收栏，免半透层叠透（2026-10-31）
        if (barsShown) setBarsVisible(false);
        java.util.List<com.fongmi.android.tv.read.ReadingBookmarkStore.Mark> marks =
                com.fongmi.android.tv.read.ReadingBookmarkStore.list(bookKey());
        com.fongmi.android.tv.read.ReaderDialogs.showBookmarks(this, session.bookTitle, marks,
                this::addBookmarkAtCurrent,
                m -> loadChapter(m.chapterIndex, m.position),
                m -> com.fongmi.android.tv.read.ReadingBookmarkStore.remove(m.bookKey, m.createdAt));
    }

    private void addBookmarkAtCurrent() {
        com.fongmi.android.tv.read.ReadingBookmarkStore.Mark m =
                new com.fongmi.android.tv.read.ReadingBookmarkStore.Mark();
        m.bookKey = bookKey();
        m.bookTitle = session.bookTitle;
        m.kind = "COMIC";
        m.chapterName = session.currentChapter().name;
        m.chapterIndex = session.chapterIndex;
        m.position = currentPage();
        m.excerpt = m.chapterName + " 第" + (m.position + 1) + "张";
        com.fongmi.android.tv.read.ReadingBookmarkStore.add(m);
        ToastUtil.show(this, "书签已保存", Toast.LENGTH_SHORT);
    }

    /** AI 口令"书签"。 */
    public void aiBookmarks() {
        setBarsVisible(false);
        showBookmarks();
    }

    // ---------------- 换源 ----------------

    private void changeSource() {
        if (!com.fongmi.android.tv.read.ReadingChangeSource.supported(session)) {
            ToastUtil.show(this, "当前这本不支持换源（仅书源的漫画可换）",
                    Toast.LENGTH_SHORT);
            return;
        }
        ToastUtil.show(this, "正在其它书源里找这本…", Toast.LENGTH_SHORT);
        android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        com.fongmi.android.tv.read.ReadingChangeSource.search(this, session, ui, candidates ->
                com.fongmi.android.tv.read.ReadingChangeSource.pickAndSwitch(
                        this, session, candidates, session.chapterIndex, 0f, currentPage()));
    }

    /** AI 口令"换源"。 */
    public void aiChangeSource() {
        setBarsVisible(false);
        changeSource();
    }

    private void toggleBars() {
        setBarsVisible(!barsShown);
    }

    private void setBarsVisible(boolean show) {
        barsShown = show;
        // 未挂载（开页建栏后那次收起）直切，不走动画防开页闪；已上屏的唤出/收起走统一淡入淡出 200ms
        if (!topBar.isAttachedToWindow()) {
            topBar.setVisibility(show ? View.VISIBLE : View.GONE);
            bottomBar.setVisibility(show ? View.VISIBLE : View.GONE);
            return;
        }
        if (show) {
            XcFadeTransition.fadeIn(topBar);
            XcFadeTransition.fadeIn(bottomBar);
        } else {
            XcFadeTransition.fadeOutThenGone(topBar);
            XcFadeTransition.fadeOutThenGone(bottomBar);
        }
    }

    private void refreshTitle() {
        ReadingSession.Chapter c = session.currentChapter();
        tvTitle.setText(session.bookTitle);
        int total = session.chapters.size();
        String chName = TextUtils.isEmpty(c.name) ? "" : c.name;
        tvSub.setText(chName + (total > 1
                ? "（" + (session.chapterIndex + 1) + " / " + total + " 话）"
                : ""));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    // ---------------- 数据 ----------------

    /** 切章：取图（懒取）→ 换列表 → 回顶 → 存进度。 */
    private void loadChapter(int index, int startPage) {
        if (loading) return;
        // 越界守卫（与小说侧同口径）：旧写法「size()>1 && index>=size」对单章会话
        // 放行 index=1，随后 currentChapter() 的 fallback 拿本章地址重解析——转圈
        // 半天还是本章，还把 chapterIndex=1 写进进度记录。这里直接拦掉并明示。
        // size==0 时 index=0 仍放行（fallback 靠 chapterUrl 取图，老口径不变）。
        int maxIndex = (session.chapters == null || session.chapters.isEmpty())
                ? 0 : session.chapters.size() - 1;
        if (index < 0 || index > maxIndex) {
            ToastUtil.show(this, index < 0 ? "已经是第一话" : "已经是最后一话", Toast.LENGTH_SHORT);
            return;
        }
        loading = true;
        session.chapterIndex = Math.max(0, index);
        ReadingSession.Chapter ch = session.currentChapter();
        session.chapterUrl = ch.url;
        Task.execute(() -> {
            List<String> list = cachedOrResolve(ch, firstLoad);
            runOnUiThread(() -> {
                loading = false;
                firstLoad = false;
                images.clear();
                if (list != null) images.addAll(list);
                adapter.notifyDataSetChanged();
                refreshTitle();
                updateSeekMax();
                if (images.isEmpty()) {
                    ToastUtil.show(this, "这一章没有取到图片", Toast.LENGTH_SHORT);
                } else {
                    scrollToPage(Math.min(startPage, images.size() - 1));
                    preloadAhead();
                }
                saveProgress();
            });
        });
    }

    /**
     * 解析某章图片：缓存 → 会话自带（首载）→ 本地书 → py 内嵌串 → 海阔懒取 → 书源规则。
     * 静态化供章节缓存复用（同口径，不另起炉灶）。
     * <p>
     * 含节点认领：解析出的图片 URL 的 host 登记到书源/规则/站点归属，
     * Glide 取图跑在无打点线程，SourceGate 的 HostClaim 按 host 反查
     * 本地书 file:// 无 host，不登记。
     */
    public static List<String> resolveImages(ReadingSession session,
                                             ReadingSession.Chapter ch,
                                             boolean preferSessionImages) {
        List<String> out = resolveImagesInner(session, ch, preferSessionImages);
        try {
            com.fongmi.android.tv.read.ReadingProxy.recordUrls(out, session);
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static List<String> resolveImagesInner(ReadingSession session,
                                             ReadingSession.Chapter ch,
                                             boolean preferSessionImages) {
        // 0. 缓存优先（阅读器侧按 bookKey 查，见 loadChapter）
        // 1. 首载用会话自带（判别层/入口已带入）
        if (preferSessionImages && !session.images.isEmpty()) {
            return new ArrayList<>(session.images);
        }
        // 2. 本地书：按章从私有目录懒读（图包按需解到缓存）
        if (com.fongmi.android.tv.localbook.LocalBookReader.isLocal(ch.url)) {
            return com.fongmi.android.tv.localbook.LocalBookReader.imagesOf(ch.url);
        }
        // 2. py：章节 url 本身就是图片串
        List<String> fromUrl = ReadingGate.imagesOfChapter(ch);
        if (!fromUrl.isEmpty()) return fromUrl;
        // 2b. py 单图章：章节地址本身就是一张图（图串口径要 ≥2 段，单图漏网）
        if (ReadingGate.isImageUrl(ch.url)) {
            List<String> one = new ArrayList<>();
            one.add(ch.url.trim());
            return one;
        }
        // 2c. py 漫画：章节地址需再解析才出图的，切章重跑 playerContent（与小说侧
        // resolveText 按章重跑同型，不再只认「地址即图串」约定）；判别层段数门槛
        // （≥3 段）没命中的，重跑产出按图串口径拆，1~2 图的章也能出图。
        // 海阔/书源会话各有自己的懒取路（下面 3/4），不进此分支。
        if (!TextUtils.isEmpty(session.siteKey) && TextUtils.isEmpty(session.ruleTitle)
                && TextUtils.isEmpty(session.legadoKey) && !TextUtils.isEmpty(ch.url)) {
            try {
                com.fongmi.android.tv.bean.Result r =
                        SiteApi.playerContentParseOnly(session.siteKey, "", ch.url);
                ReadingSession rs = ReadingGate.extract(r);
                if (rs != null && rs.kind == ReadingSession.Kind.COMIC
                        && rs.images != null && !rs.images.isEmpty()) {
                    return new ArrayList<>(rs.images);
                }
                String raw = r == null || r.getUrl() == null ? "" : r.getUrl().v();
                List<String> segs = ReadingGate.splitImages(raw);
                boolean allImage = !segs.isEmpty();
                for (String u : segs) {
                    if (!ReadingGate.isImageUrl(u)) {
                        allImage = false;
                        break;
                    }
                }
                if (allImage) return segs;
            } catch (Throwable ignored) {
            }
        }
        // 3. 海阔：router.play 取 pics
        if (!TextUtils.isEmpty(session.ruleTitle) && !TextUtils.isEmpty(ch.url)) {
            try {
                HkRouter router = SiteApi.getHkRouter(session.ruleTitle);
                if (router != null) {
                    HkPlay play = router.play(ch.url);
                    if (play != null && !play.getPics().isEmpty()) {
                        // 切章刷新来源：本章页地址与整套 play 请求头随 pics 一并换新，
                        // 后续组图不吃入口那一刻的旧值（旧值多半为空或规则主页，图必死）。
                        // 页地址只认 play 记录的产出页且须 http(s)（与 ReadingGate.fromHkComic
                        // 同款判定）：集地址 ch.url 可能是 pics:// 串全文，拿它冒充时组装层
                        // getDom 算出首图碎片当域、自动 Referer 恒不成立；产出页取不到
                        // 合格值就保留旧值，不拿假地址污染判定。
                        String producedPage = play.getPageUrl();
                        if (isHttpPage(producedPage)) session.pageUrl = producedPage;
                        session.playHeaders = new java.util.HashMap<>(play.getHeaders());
                        String ref = play.getHeaders().get("Referer");
                        if (ref != null) session.referer = ref;
                        return new ArrayList<>(play.getPics());
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        // Legado 书源：按书源规则取图片
        if (!TextUtils.isEmpty(session.legadoKey) && !TextUtils.isEmpty(ch.url)) {
            try {
                java.util.List<String> list =
                        com.fongmi.android.tv.legado.LegadoReading.fetchImages(session.legadoKey, ch.url);
                if (list != null && !list.isEmpty()) return new ArrayList<>(list);
            } catch (Throwable ignored) {
            }
        }
        return new ArrayList<>();
    }

    /** 取图：先看缓存（本地书本身就在本地，不走缓存）。 */
    private List<String> cachedOrResolve(ReadingSession.Chapter ch, boolean first) {
        if (TextUtils.isEmpty(session.localId)) {
            List<String> cached = com.fongmi.android.tv.read.ReadingDownload.loadComicChapter(
                    this, bookKey(), session.chapterIndex);
            if (!cached.isEmpty()) return cached;
        }
        return resolveImages(session, ch, first);
    }

    private void goChapter(int index) {
        loadChapter(index, 0);
    }

    private void showChapters() {
        if (barsShown) setBarsVisible(false);
        if (session.chapters.isEmpty()) {
            ToastUtil.show(this, "没有章节列表", Toast.LENGTH_SHORT);
            return;
        }
        java.util.List<String> names = new java.util.ArrayList<>(session.chapters.size());
        for (ReadingSession.Chapter c : session.chapters) names.add(c.name);
        boolean local = !TextUtils.isEmpty(session.localId);
        String cacheStatus = local ? null : com.fongmi.android.tv.read.ReadingDownload
                .statusText(this, bookKey(), session.chapters.size());
        com.fongmi.android.tv.read.ReaderDialogs.showChapters(this, session.bookTitle, names,
                session.chapterIndex, cacheStatus,
                local ? null : this::askCacheRange,
                this::goChapter);
    }

    private void askCacheRange() {
        String[] items = {"后续 20 章", "后续 50 章", "全部剩余章节"};
        int[] counts = {20, 50, Integer.MAX_VALUE};
        ReadDialogs.options(this, "缓存章节（离线看）", items, which -> {
            ToastUtil.show(this, "后台缓存中，完成后会提示", Toast.LENGTH_SHORT);
                    android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
                    com.fongmi.android.tv.read.ReadingDownload.downloadNextComic(
                            this, session, bookKey(), counts[which], ui,
                            (ok, total) -> ToastUtil.show(this,
                                    total == 0 ? "没有可缓存的新章节"
                                            : "已缓存 " + ok + " / " + total + " 章",
                                    Toast.LENGTH_SHORT));
        });
    }

    // ---------------- 进度 ----------------

    private int currentPage() {
        try {
            LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
            return Math.max(0, lm.findFirstVisibleItemPosition());
        } catch (Throwable t) {
            return 0;
        }
    }

    private void scrollToPage(int pos) {
        if (pos < 0 || pos >= images.size()) return;
        rv.scrollToPosition(pos);
        updatePageLabel();
        saveProgress();
    }

    private void updatePageLabel() {
        int total = images.size();
        int cur = total == 0 ? 0 : Math.min(currentPage() + 1, total);
        if (tvPageLeft != null) tvPageLeft.setText(cur + " / " + total);
        if (tvPct != null) {
            tvPct.setText(total == 0 ? "0%"
                    : Math.round(cur * 100f / total) + "%");
        }
        if (seekBar != null && total > 0) {
            seekBar.setProgress(Math.min(currentPage(), total - 1));
        }
    }

    private void updateSeekMax() {
        if (seekBar != null) seekBar.setMax(Math.max(0, images.size() - 1));
        updatePageLabel();
    }

    /** 真实页面地址判定（与 ReadingGate.isHttpPage 同款）：http(s) 全地址才认，pics:// 串、相对地址一律不认。 */
    private static boolean isHttpPage(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String t = url.trim().toLowerCase(java.util.Locale.ROOT);
        return t.startsWith("http://") || t.startsWith("https://");
    }

    /** 组图用的页面上下文：优先会话存的章节页地址，回退旧 referer 字段（入口乙/丙的存量会话）。 */
    private String sessionPageUrl() {
        if (session == null) return null;
        if (!TextUtils.isEmpty(session.pageUrl)) return session.pageUrl;
        return TextUtils.isEmpty(session.referer) ? null : session.referer;
    }

    /** 预加载后面 3 张（跟手的关键）。与显示同路组装，不走降级单参路。 */
    private void preloadAhead() {
        try {
            int cur = currentPage();
            for (int i = 1; i <= 3; i++) {
                int p = cur + i;
                if (p < images.size()) ImgUtil.preload(this, images.get(p), sessionPageUrl(), session.playHeaders);
            }
        } catch (Throwable ignored) {
        }
    }

    private void saveProgress() {
        // 换源已把本书交给新源：旧页退出存进度会按旧源键把旧书架记录写回来，不存
        if (session != null && session.shelfSuperseded) return;
        long now = System.currentTimeMillis();
        if (now - lastSaveAt < 2000) return;
        lastSaveAt = now;
        try {
            ReadingStore.Record r = new ReadingStore.Record();
            r.kind = "COMIC";
            r.sourceName = session.sourceName;
            r.bookTitle = session.bookTitle;
            r.coverUrl = session.coverUrl;
            r.chapterName = session.currentChapter().name;
            r.chapterIndex = session.chapterIndex;
            r.chapterUrl = session.chapterUrl;
            r.siteKey = session.siteKey;
            r.detailId = session.detailId;
            r.playFlag = session.playFlag;
            r.legadoKey = session.legadoKey;
            r.localId = session.localId;
            r.origin = TextUtils.isEmpty(session.localId) ? "" : "local";
            r.position = currentPage();
            // 合并写：整条覆盖会抹掉建会话时写入的 bookUrl 等追更字段（R1）
            ReadingStore.saveMerged(r);
        } catch (Throwable ignored) {
            // 没存上：旧进度记录未被覆盖（合并写未完成）；清掉节流戳，下一次
            // 存进度（切章／退页）立即重试，并明示用户这一次没存上。
            lastSaveAt = 0;
            ToastUtil.show(this, "阅读进度保存失败", Toast.LENGTH_SHORT);
        }
    }

    @Override
    protected void onPause() {
        lastSaveAt = 0;
        saveProgress();
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyKeepScreen();
    }

    // ---------------- 适配器 ----------------

    private class ImgAdapter extends RecyclerView.Adapter<ImgAdapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final ImageView iv;

            Holder(ImageView iv) {
                super(iv);
                this.iv = iv;
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return new Holder(iv);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            String url = images.get(position);
            // 按屏宽缩放：宽铺满、高按比例，不跳版
            // 与海阔文章页同路组装：真章节页地址走双参路（@js=/@headers= 解析＋自动 Referer），
            // 图串没带的头由会话 play 请求头补缺——旧口径拿 referer 值冒充页面地址，两头落空
            ImgUtil.load("", url, holder.iv, false, 0, 0, sessionPageUrl(), session.playHeaders);
        }

        @Override
        public int getItemCount() {
            return images.size();
        }
    }
}
