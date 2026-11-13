package com.fongmi.android.tv.ui.activity;
import com.fongmi.android.tv.utils.ToastUtil;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.read.HkNovelFetcher;
import com.fongmi.android.tv.read.ReadDialogs;
import com.fongmi.android.tv.read.ReadingSession;
import com.fongmi.android.tv.read.ReadingStore;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.XcFadeTransition;
import com.fongmi.android.tv.R;

/**
 * 小说阅读器：全新设计。纵滑顺滑、字号可调、三套背景主题、章节抽屉、记位置。
 * 进度按万分比存，换字号/换主题后恢复不错位。
 */
public class NovelReaderActivity extends AppCompatActivity {

    private static final String PREFS = "reading_pref";
    private static final String KEY_TEXT_SIZE = "novel_text_size";
    private static final String KEY_THEME = "novel_theme";
    private static final String KEY_PAGE_MODE = "novel_page_mode"; // 0纵滑 1覆盖 2滑动
    private static final String KEY_FONT = "novel_font"; // 0默认 1衬线 2等宽 3导入
    private static final String KEY_FONT_PATH = "novel_font_path";
    private static final String KEY_SPACING = "novel_spacing"; // 0紧凑 1标准 2疏朗
    private static final String KEY_VOLUME_PAGE = "novel_volume_page";
    private static final String KEY_KEEP_SCREEN = "novel_keep_screen";

    // 主题（2026-10-18 他拍板 8 个全上）：老用户 pref 值 0/1/2 含义不变
    // （0纯白/1米纸/2墨黑夜），新主题 pref 值 3-7 追加；展示按浅到深。
    private static final String[] THEME_NAMES =
            {"纯白", "米纸", "羊皮纸", "豆绿", "青灰", "浅灰", "深灰夜", "墨黑夜"};
    private static final int[] THEME_BG =
            {0xFFFFFFFF, 0xFFF6ECD9, 0xFFEFE0BC, 0xFFDDE8D0,
             0xFFD8DEE2, 0xFFE8E8E8, 0xFF2A2A2E, 0xFF141414};
    private static final int[] THEME_FG =
            {0xFF222222, 0xFF4E3A28, 0xFF4A3A24, 0xFF2E4630,
             0xFF2C3438, 0xFF333333, 0xFFB9B9B9, 0xFFCFCFCF};
    /** 展示序（浅→深）→ pref 值。 */
    private static final int[] THEME_PREF = {0, 1, 3, 4, 5, 6, 7, 2};

    private ReadingSession session;
    private SharedPreferences prefs;
    private FrameLayout root;
    private ScrollView scrollView;
    private TextView tvContent;
    private TextView tvChapterHead;
    private View topBar;
    private View bottomBar;
    private TextView tvStatus;
    private TextView tvBack;
    private TextView tvToc;
    private TextView tvProgress;
    private TextView tvChapterBottom;
    private TextView tvChapterMid;
    private android.widget.ProgressBar progressBar;
    private TextView tvFontSize;
    private LinearLayout themeRow;
    private HorizontalScrollView themeScroll;
    private LinearLayout pageSeg;
    private TextView tvTtsSub;
    private boolean barsShown = false;
    private boolean loading = false;
    private String currentText = "";
    private volatile long lastSaveAt = 0; // saveProgressAt 跑在后台线程，加 volatile
    // 章末自动接下一章：只在"刚滑到底"的跳变时触发一次，避免打开恢复到底部时误触
    private boolean wasBottom = false;
    private int autoAdvancedFor = -1;
    private long loadUiDoneAt = 0;
    // 本章内是否观察到过"未到底"：恢复进度打开就停在章末的不算读完，不自动接
    private boolean sawNotBottom = false;
    private final Runnable autoNextRecheck = this::maybeAutoNext;
    private int preloadingFor = -1;
    // 无缝切章（仅纵滑模式，他 2026-10-28 定案）：tvContent 里顺流拼着的
    // 章块——seamText 是工作副本（每次拼/剪后整本重 set），seamBlocks 记
    // 每块的 [章下标, 起始字符偏移]、seamTexts 同序存各块未样式化正文。
    // 单块时全部口径退化回整章（loadChapter 复位），覆盖/滑动模式不启用。
    private android.text.SpannableStringBuilder seamText;
    private final java.util.List<int[]> seamBlocks = new java.util.ArrayList<>();
    private final java.util.List<String> seamTexts = new java.util.ArrayList<>();
    private boolean seamAppending = false;
    private int seamKnownChapter = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // prefs 必须先于任何 theme()/pageMode() 等读取：1.3.56 曾在它之后调
        // isNightPref(theme()) 导致 getInt 掉空指针，阅读页每次起屏必崩。
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        ReadingSkin.enableEdgeToEdge(this, isNightPref(theme()));
        session = ReadingSession.get(getIntent().getStringExtra("session"));
        if (session == null) {
            ToastUtil.show(this, "阅读会话已失效", Toast.LENGTH_SHORT);
            finish();
            return;
        }
        buildUi();
        applyKeepScreen();
        loadChapter(session.chapterIndex, true);

        // 页面进出统一渐入渐出（与 BaseActivity 一致）
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
}

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
    }


    // ---------------- UI ----------------

    private void buildUi() {
        root = new FrameLayout(this);
        applyTheme();

        scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout contentCol = new LinearLayout(this);
        contentCol.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        contentCol.setPadding(pad, dp(56), pad, dp(64));
        tvChapterHead = new TextView(this);
        tvChapterHead.setGravity(Gravity.CENTER);
        tvChapterHead.setTextSize(21);
        tvChapterHead.getPaint().setFakeBoldText(true);
        tvChapterHead.setTextColor(fg());
        tvChapterHead.setPadding(0, 0, 0, dp(14));
        contentCol.addView(tvChapterHead);
        View divider = new View(this);
        android.graphics.drawable.GradientDrawable divBg =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{0x0045C08A, 0xFF45C08A, 0x0045C08A});
        divBg.setCornerRadius(dp(2));
        divider.setBackground(divBg);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                dp(120), dp(3));
        dlp.gravity = Gravity.CENTER_HORIZONTAL;
        dlp.bottomMargin = dp(26);
        contentCol.addView(divider, dlp);
        tvContent = new TextView(this);
        tvContent.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvContent.setTextSize(prefs.getFloat(KEY_TEXT_SIZE, 18));
        applyTextStyle();
        tvContent.setTextColor(fg());
        contentCol.addView(tvContent);
        scrollView.addView(contentCol);
        root.addView(scrollView);

        GestureDetector gd = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        return onContentTap(e.getX());
                    }
                });
        scrollView.setOnTouchListener((v, e) -> {
            gd.onTouchEvent(e);
            // 覆盖/滑动模式：手势被翻页接管，不自由滚动
            return pageMode() != 0;
        });
        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            updateProgressLabel();
            maybeAutoNext();
        });

        topBar = buildTopBar();
        root.addView(topBar);
        bottomBar = buildBottomBar();
        root.addView(bottomBar);
        setBarsVisible(false);
        setContentView(root);
    }

    private int theme() {
        return prefs.getInt(KEY_THEME, 0);
    }

    /** pref 值→纸色：0/1/2 老含义不变，新值 3-7 追加。 */
    private static int bgForPref(int pref) {
        switch (pref) {
            case 1: return 0xFFF6ECD9;
            case 2: return 0xFF141414;
            case 3: return 0xFFEFE0BC;
            case 4: return 0xFFDDE8D0;
            case 5: return 0xFFD8DEE2;
            case 6: return 0xFFE8E8E8;
            case 7: return 0xFF2A2A2E;
            default: return 0xFFFFFFFF;
        }
    }

    private static int fgForPref(int pref) {
        switch (pref) {
            case 1: return 0xFF4E3A28;
            case 2: return 0xFFCFCFCF;
            case 3: return 0xFF4A3A24;
            case 4: return 0xFF2E4630;
            case 5: return 0xFF2C3438;
            case 6: return 0xFF333333;
            case 7: return 0xFFB9B9B9;
            default: return 0xFF222222;
        }
    }

    /** 是否夜主题（2 墨黑夜 / 7 深灰夜）：状态栏图标切浅色。 */
    private static boolean isNightPref(int pref) {
        return pref == 2 || pref == 7;
    }

    private int bg() {
        return bgForPref(theme());
    }

    private int fg() {
        return fgForPref(theme());
    }

    private void applyTheme() {
        root.setBackgroundColor(bg());
        // 窗口底恒为壁纸（fitWindowBackground 已废弃）；这里只跟纸色切系统栏图标明暗
        ReadingSkin.setSystemBarIconAppearance(this, isNightPref(theme()));
        int tfg = topBarFg();
        if (tvBack != null) tvBack.setTextColor(tfg);
        if (tvStatus != null) tvStatus.setTextColor(tfg);
        if (tvToc != null) tvToc.setTextColor(tfg);
        if (tvContent != null) {
            tvContent.setTextColor(fg());
        }
    }

    /** 顶栏（稿）：‹ 返回 ｜ 第 N 章 · x% ｜ 目录，灰字浮于内容上。 */
    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        bar.setLayoutParams(lp);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(8);
        bar.setPadding(pad, dp(8), pad, pad);
        ReadingSkin.applyTopInset(bar, this, 8);

        TextView back = new TextView(this);
        back.setText("‹ 返回");
        back.setTextSize(14);
        back.setTextColor(topBarFg());
        back.setPadding(dp(12), dp(12), dp(12), dp(12));
        back.setOnClickListener(v -> finish());
        tvBack = back;
        bar.addView(back);

        tvStatus = new TextView(this);
        tvStatus.setTextColor(topBarFg());
        tvStatus.setTextSize(13);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setSingleLine(true);
        tvStatus.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tvStatus.setLayoutParams(slp);
        bar.addView(tvStatus);

        TextView toc = new TextView(this);
        toc.setText("目录");
        toc.setTextSize(14);
        toc.setTextColor(topBarFg());
        toc.setPadding(dp(12), dp(12), dp(12), dp(12));
        toc.setOnClickListener(v -> showChapters());
        tvToc = toc;
        bar.addView(toc);
        refreshTitle();
        return bar;
    }

    /** 顶栏灰字跟纸色走：浅纸深灰、深纸浅灰（透明底浮于内容上，对比度≥6）。 */
    private int topBarFg() {
        return isNightPref(theme()) ? 0xFFD9D3C6 : 0xFF5A5A5A;
    }

    /** 底栏（稿）：毛玻璃设置面板。所有原有功能一个不少，只是换了皮。 */
    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        bar.setLayoutParams(lp);
        bar.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        // C7 太透：浅纸正文会透进面板跟面板字打架（2026-10-31 录屏），提到 E0
        bg.setColor(0xE0121219);
        bg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        bg.setStroke(dp(2), 0x2EFFFFFF);
        bar.setBackground(bg);
        bar.setPadding(dp(20), dp(8), dp(20), dp(12));
        ReadingSkin.applyBottomInset(bar, this, 12);

        // 顶部小灰横条
        View grab = new View(this);
        android.graphics.drawable.GradientDrawable grabBg =
                new android.graphics.drawable.GradientDrawable();
        grabBg.setColor(0x40FFFFFF);
        grabBg.setCornerRadius(dp(4));
        grab.setBackground(grabBg);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                dp(60), dp(4));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.bottomMargin = dp(6);
        bar.addView(grab, glp);

        // 上一章 / 章名 / 下一章
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = sheetBtn("上一章");
        prev.setOnClickListener(v -> goChapter(session.chapterIndex - 1));
        row.addView(prev);
        tvChapterMid = new TextView(this);
        tvChapterMid.setTextColor(ReadingSkin.TEXT);
        tvChapterMid.setTextSize(14);
        tvChapterMid.setGravity(Gravity.CENTER);
        tvChapterMid.setSingleLine(true);
        tvChapterMid.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tvChapterMid.setLayoutParams(mlp);
        row.addView(tvChapterMid);
        TextView next = sheetBtn("下一章");
        next.setOnClickListener(v -> goChapter(session.chapterIndex + 1));
        row.addView(next);
        bar.addView(row);

        // 字号：− / 金色数字 / ＋ ＋ 右侧 13 - 26（范围与行为不动）
        LinearLayout fsRow = new LinearLayout(this);
        fsRow.setOrientation(LinearLayout.HORIZONTAL);
        fsRow.setGravity(Gravity.CENTER_VERTICAL);
        fsRow.setPadding(0, dp(12), 0, 0);
        fsRow.addView(sheetLabel("字号"));
        LinearLayout stepper = new LinearLayout(this);
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = stepBtn("－");
        minus.setOnClickListener(v -> changeTextSize(-1));
        stepper.addView(minus);
        tvFontSize = new TextView(this);
        tvFontSize.setTextSize(19);
        tvFontSize.getPaint().setFakeBoldText(true);
        tvFontSize.setTextColor(ReadingSkin.GOLD_TEXT);
        tvFontSize.setGravity(Gravity.CENTER);
        tvFontSize.setMinWidth(dp(48));
        tvFontSize.setPadding(dp(8), 0, dp(8), 0);
        stepper.addView(tvFontSize);
        TextView plus = stepBtn("＋");
        plus.setOnClickListener(v -> changeTextSize(1));
        stepper.addView(plus);
        fsRow.addView(stepper);
        TextView range = new TextView(this);
        range.setText("13 - 26");
        range.setTextSize(12);
        range.setTextColor(ReadingSkin.FAINT);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        range.setLayoutParams(rlp);
        range.setGravity(Gravity.END);
        fsRow.addView(range);
        bar.addView(fsRow);
        refreshFontSize();

        // 主题：8 纸色单行横滑（圆点 44dp 与旧三圆一致，不缩小），
        // 选中青绿描边＋青绿字，打开面板滚动定位到选中项
        LinearLayout thRow = new LinearLayout(this);
        thRow.setOrientation(LinearLayout.HORIZONTAL);
        thRow.setGravity(Gravity.CENTER_VERTICAL);
        thRow.setPadding(0, dp(12), 0, 0);
        thRow.addView(sheetLabel("主题"));
        themeScroll = new HorizontalScrollView(this);
        themeScroll.setHorizontalScrollBarEnabled(false);
        themeScroll.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeScroll.addView(themeRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        thRow.addView(themeScroll);
        bar.addView(thRow);
        refreshThemeRow();

        // 翻页：三档全保留（纵滑/覆盖/滑动），同语言补样式
        LinearLayout pgRow = new LinearLayout(this);
        pgRow.setOrientation(LinearLayout.HORIZONTAL);
        pgRow.setGravity(Gravity.CENTER_VERTICAL);
        pgRow.setPadding(0, dp(12), 0, 0);
        pgRow.addView(sheetLabel("翻页"));
        pageSeg = new LinearLayout(this);
        pageSeg.setOrientation(LinearLayout.HORIZONTAL);
        pageSeg.setBackground(ReadingSkin.segContainer(this));
        pageSeg.setPadding(dp(3), dp(3), dp(3), dp(3));
        pgRow.addView(pageSeg);
        bar.addView(pgRow);
        refreshPageSeg();

        // 朗读本章：金色大钮（点＝朗读）＋副（点＝换音色）
        LinearLayout ttsBtn = new LinearLayout(this);
        ttsBtn.setOrientation(LinearLayout.VERTICAL);
        ttsBtn.setGravity(Gravity.CENTER_HORIZONTAL);
        ttsBtn.setBackground(ReadingSkin.goldCapsule(this));
        try {
            ttsBtn.setElevation(dp(6));
        } catch (Throwable ignored) {
        }
        LinearLayout.LayoutParams ttslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ttslp.topMargin = dp(16);
        ttsBtn.setLayoutParams(ttslp);
        ttsBtn.setPadding(0, dp(14), 0, dp(12));
        ttsBtn.setOnClickListener(v -> startTts());
        TextView ttsT = new TextView(this);
        ttsT.setText("▶  朗读本章");
        ttsT.setTextSize(17);
        ttsT.getPaint().setFakeBoldText(true);
        ttsT.setTextColor(0xFF0A2115);
        ttsT.setGravity(Gravity.CENTER);
        ttsBtn.addView(ttsT);
        tvTtsSub = new TextView(this);
        tvTtsSub.setTextSize(12);
        tvTtsSub.setTextColor(0xA60A2115);
        tvTtsSub.setGravity(Gravity.CENTER);
        tvTtsSub.setPadding(0, dp(4), 0, 0);
        tvTtsSub.setOnClickListener(v -> pickVoice());
        ttsBtn.addView(tvTtsSub);
        bar.addView(ttsBtn);
        refreshTtsSub();

        // 目录 / 书签 / 换源 / 设置（功能保留）
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        row2.setPadding(0, dp(10), 0, 0);
        TextView chapters = sheetBtn("目录");
        chapters.setOnClickListener(v -> showChapters());
        row2.addView(chapters);
        TextView markBtn = sheetBtn("书签");
        markBtn.setOnClickListener(v -> showBookmarks());
        row2.addView(markBtn);
        TextView csBtn = sheetBtn("换源");
        csBtn.setOnClickListener(v -> changeSource());
        row2.addView(csBtn);
        TextView setBtn = sheetBtn("设置");
        setBtn.setOnClickListener(v -> showSettings());
        row2.addView(setBtn);
        bar.addView(row2);

        // 底部：章节 / 金进度条 / 百分比
        LinearLayout prog = new LinearLayout(this);
        prog.setOrientation(LinearLayout.HORIZONTAL);
        prog.setGravity(Gravity.CENTER_VERTICAL);
        prog.setPadding(0, dp(10), 0, 0);
        tvChapterBottom = new TextView(this);
        tvChapterBottom.setTextSize(12);
        tvChapterBottom.setTextColor(ReadingSkin.SUB);
        prog.addView(tvChapterBottom);
        progressBar = new android.widget.ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progressBar.setProgressDrawable(ReadingSkin.goldProgressBar(this));
        progressBar.setMax(10000);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0,
                dp(5), 1);
        plp.leftMargin = dp(12);
        plp.rightMargin = dp(12);
        prog.addView(progressBar, plp);
        tvProgress = new TextView(this);
        tvProgress.setTextColor(ReadingSkin.SUB);
        tvProgress.setTextSize(12);
        prog.addView(tvProgress);
        bar.addView(prog);
        updateProgressLabel();
        return bar;
    }

    private TextView sheetLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setTextColor(ReadingSkin.TEXT);
        t.setPadding(0, 0, dp(16), 0);
        return t;
    }

    private TextView sheetBtn(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(ReadingSkin.TEXT);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private TextView stepBtn(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(20);
        t.setTextColor(ReadingSkin.TEXT);
        t.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(0x0FFFFFFF);
        bg.setStroke(dp(2), 0x4DFFFFFF);
        t.setBackground(bg);
        int sz = dp(44);
        t.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private void refreshFontSize() {
        if (tvFontSize != null) {
            tvFontSize.setText(
                    String.valueOf(Math.round(prefs.getFloat(KEY_TEXT_SIZE, 18))));
        }
    }

    private void refreshThemeRow() {
        if (themeRow == null) return;
        themeRow.removeAllViews();
        int curPref = theme();
        int curDisp = 0;
        for (int i = 0; i < THEME_PREF.length; i++) {
            if (THEME_PREF[i] == curPref) curDisp = i;
        }
        for (int i = 0; i < THEME_NAMES.length; i++) {
            final int pref = THEME_PREF[i];
            final boolean sel = (i == curDisp);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(6), 0, dp(6), 0);
            View dot = new View(this);
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bg.setColor(THEME_BG[i]);
            bg.setStroke(dp(sel ? 3 : 2),
                    sel ? ReadingSkin.GOLD : 0x40FFFFFF);
            dot.setBackground(bg);
            int sz = dp(44);
            cell.addView(dot, new LinearLayout.LayoutParams(sz, sz));
            TextView l = new TextView(this);
            l.setText(THEME_NAMES[i]);
            l.setTextSize(11);
            l.setTextColor(sel ? ReadingSkin.GOLD_TEXT : ReadingSkin.FAINT);
            l.setPadding(0, dp(4), 0, 0);
            cell.addView(l);
            cell.setOnClickListener(v -> {
                prefs.edit().putInt(KEY_THEME, pref).apply();
                applyTheme();
                refreshThemeRow();
                ToastUtil.show(this, "已切换主题", Toast.LENGTH_SHORT);
            });
            themeRow.addView(cell);
            if (sel && themeScroll != null) {
                // 定位到当前主题项（打开面板时滚过来）
                final LinearLayout selCell = cell;
                themeScroll.post(() -> themeScroll.smoothScrollTo(
                        Math.max(0, selCell.getLeft()
                                - themeScroll.getWidth() / 3), 0));
            }
        }
    }

    private void refreshPageSeg() {
        if (pageSeg == null) return;
        pageSeg.removeAllViews();
        String[] modes = {"纵滑", "覆盖", "滑动"};
        int cur = pageMode();
        for (int i = 0; i < modes.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(modes[i]);
            t.setTextSize(14);
            t.setPadding(dp(20), dp(10), dp(20), dp(10));
            t.setGravity(Gravity.CENTER);
            if (i == cur) {
                t.setBackground(ReadingSkin.segSelectedTint(this));
                t.setTextColor(ReadingSkin.GOLD_TEXT);
                t.getPaint().setFakeBoldText(true);
            } else {
                t.setBackgroundColor(Color.TRANSPARENT);
                t.setTextColor(ReadingSkin.SUB);
            }
            t.setOnClickListener(v -> {
                int prev = pageMode();
                float frac = currentFraction();
                prefs.edit().putInt(KEY_PAGE_MODE, idx).apply();
                refreshPageSeg();
                // 离开纵滑时无缝拼接作废：拼着的多章文本对翻页模式是错位
                // 账本，按当前章内进度整章重载回单块（切回纵滑再重新起流）
                if (prev == 0 && idx != 0 && seamBlocks.size() > 1) {
                    goChapter(session.chapterIndex, Math.round(frac * 10000));
                }
                ToastUtil.show(this, "已切换为" + modes[idx] + "翻页",
                        Toast.LENGTH_SHORT);
            });
            pageSeg.addView(t);
        }
    }

    /** 朗读钮副文案：真·当前默认音色（画像），点副文案换音色。 */
    private void refreshTtsSub() {
        if (tvTtsSub == null) return;
        String id = com.fongmi.android.tv.tts.EdgeVoiceCatalog.defaultVoice();
        com.fongmi.android.tv.tts.EdgeVoice v =
                com.fongmi.android.tv.tts.EdgeVoiceCatalog.find(
                        com.fongmi.android.tv.tts.EdgeVoiceCatalog.builtIn(), id);
        String name = v == null ? "默认音色" : v.name;
        String portrait = v == null ? "" : v.portrait;
        tvTtsSub.setText("神经人声 · " + name
                + (portrait.isEmpty() ? "" : "（" + portrait + "）")
                + " · 可换音色");
    }

    /** 换音色：与 TtsReadActivity 同口径（改共享默认，本段起生效）。 */
    private void pickVoice() {
        dismissBarsForDialog();
        java.util.List<com.fongmi.android.tv.tts.EdgeVoice> voices =
                com.fongmi.android.tv.tts.EdgeVoiceCatalog.builtIn();
        if (voices.isEmpty()) {
            ToastUtil.show(this, "音色列表为空", Toast.LENGTH_SHORT);
            return;
        }
        String cur = com.fongmi.android.tv.tts.EdgeVoiceCatalog.defaultVoice();
        String[] items = new String[voices.size()];
        int checked = 0;
        for (int i = 0; i < voices.size(); i++) {
            com.fongmi.android.tv.tts.EdgeVoice v = voices.get(i);
            items[i] = v.label() + (v.storyteller ? " ★" : "");
            if (v.id.equals(cur)) checked = i;
        }
        ReadDialogs.optionsChecked(this, "选择音色（神经人声）", items, checked,
                which -> {
                    com.fongmi.android.tv.tts.EdgeVoice v = voices.get(which);
                    com.fongmi.android.tv.tts.EdgeVoiceCatalog
                            .setDefaultVoice(v.id);
                    refreshTtsSub();
                    ToastUtil.show(this, "已换「" + v.name + "」，下次朗读生效",
                            Toast.LENGTH_SHORT);
                });
    }

    // ---------------- 设置 ----------------

    private void showSettings() {
        dismissBarsForDialog();
        String[] items = {
                "翻页模式：" + new String[]{"纵滑", "覆盖", "滑动"}[pageMode()],
                "字体：" + new String[]{"默认", "衬线", "等宽", "导入的字体"}[prefs.getInt(KEY_FONT, 0)],
                "行距：" + new String[]{"紧凑", "标准", "疏朗"}[prefs.getInt(KEY_SPACING, 1)],
                "音量键翻页：" + (prefs.getBoolean(KEY_VOLUME_PAGE, false) ? "开" : "关"),
                "屏幕常亮：" + (prefs.getBoolean(KEY_KEEP_SCREEN, true) ? "开" : "关"),
                "清除本书缓存"
        };
        ReadDialogs.options(this, "阅读设置", items, which -> {
            switch (which) {
                case 0: pickPageMode(); break;
                case 1: pickFont(); break;
                case 2: pickSpacing(); break;
                case 3:
                    prefs.edit().putBoolean(KEY_VOLUME_PAGE,
                            !prefs.getBoolean(KEY_VOLUME_PAGE, false)).apply();
                    showSettings();
                    break;
                case 4:
                    prefs.edit().putBoolean(KEY_KEEP_SCREEN,
                            !prefs.getBoolean(KEY_KEEP_SCREEN, true)).apply();
                    applyKeepScreen();
                    showSettings();
                    break;
                case 5:
                    com.fongmi.android.tv.read.ReadingDownload.clearBook(this, bookKey());
                    ToastUtil.show(this, "本书缓存已清除", Toast.LENGTH_SHORT);
                    break;
            }
        });
    }

    private void pickPageMode() {
        String[] modes = {"纵滑", "覆盖", "滑动"};
        ReadDialogs.optionsChecked(this, "翻页模式", modes, pageMode(), which -> {
            int prev = pageMode();
            float frac = currentFraction();
            prefs.edit().putInt(KEY_PAGE_MODE, which).apply();
            if (prev == 0 && which != 0 && seamBlocks.size() > 1) {
                goChapter(session.chapterIndex, Math.round(frac * 10000));
            }
            ToastUtil.show(this, "已切换为" + modes[which] + "翻页", Toast.LENGTH_SHORT);
        });
    }

    private void pickFont() {
        String[] fonts = {"默认", "衬线", "等宽", "导入 ttf 字体…"};
        ReadDialogs.options(this, "字体", fonts, which -> {
            if (which == 3) {
                pickFontFile();
                return;
            }
            prefs.edit().putInt(KEY_FONT, which).apply();
            applyTextStyle();
        });
    }

    private void pickSpacing() {
        String[] items = {"紧凑", "标准", "疏朗"};
        ReadDialogs.optionsChecked(this, "行距", items, prefs.getInt(KEY_SPACING, 1),
                which -> {
                    prefs.edit().putInt(KEY_SPACING, which).apply();
                    float frac = currentFraction();
                    applyTextStyle();
                    scrollView.post(() -> scrollToFraction(frac));
                });
    }

    // ---------------- 屏幕常亮 ----------------

    private void applyKeepScreen() {
        boolean on = prefs.getBoolean(KEY_KEEP_SCREEN, true);
        if (on) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ---------------- 书签 ----------------

    /** 书 key：与 ReadingStore.Record.key() 同口径。 */
    private String bookKey() {
        if (!TextUtils.isEmpty(session.localId)) return "local|" + session.localId;
        return "NOVEL|" + session.sourceName + "|" + session.bookTitle;
    }

    private void showBookmarks() {
        dismissBarsForDialog();
        java.util.List<com.fongmi.android.tv.read.ReadingBookmarkStore.Mark> marks =
                com.fongmi.android.tv.read.ReadingBookmarkStore.list(bookKey());
        com.fongmi.android.tv.read.ReaderDialogs.showBookmarks(this, session.bookTitle, marks,
                this::addBookmarkAtCurrent,
                m -> goChapter(m.chapterIndex, m.position),
                m -> com.fongmi.android.tv.read.ReadingBookmarkStore.remove(m.bookKey, m.createdAt));
    }

    private void addBookmarkAtCurrent() {
        com.fongmi.android.tv.read.ReadingBookmarkStore.Mark m =
                new com.fongmi.android.tv.read.ReadingBookmarkStore.Mark();
        m.bookKey = bookKey();
        m.bookTitle = session.bookTitle;
        m.kind = "NOVEL";
        m.chapterName = session.currentChapter().name;
        m.chapterIndex = session.chapterIndex;
        m.position = Math.round(currentFraction() * 10000);
        m.excerpt = com.fongmi.android.tv.read.ReadingBookmarkStore.excerptOf(currentText);
        com.fongmi.android.tv.read.ReadingBookmarkStore.add(m);
        ToastUtil.show(this, "书签已保存", Toast.LENGTH_SHORT);
    }

    /** AI 口令"书签"：打开书签列表。 */
    public void aiBookmarks() {
        setBarsVisible(false);
        showBookmarks();
    }

    // ---------------- 换源 ----------------

    private void changeSource() {
        if (!com.fongmi.android.tv.read.ReadingChangeSource.supported(session)) {
            ToastUtil.show(this, "当前这本书不支持换源（仅书源的书可换）",
                    Toast.LENGTH_SHORT);
            return;
        }
        ToastUtil.show(this, "正在其它书源里找这本书…", Toast.LENGTH_SHORT);
        android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        com.fongmi.android.tv.read.ReadingChangeSource.search(this, session, ui, candidates ->
                com.fongmi.android.tv.read.ReadingChangeSource.pickAndSwitch(
                        this, session, candidates, session.chapterIndex,
                        currentFraction(), 0));
    }

    /** AI 口令"换源"。 */
    public void aiChangeSource() {
        setBarsVisible(false);
        changeSource();
    }

    /** 朗读本章：把会话递给朗读播放页（调用关系，阅读器本身不动）。 */
    private void startTts() {
        try {
            com.fongmi.android.tv.tts.TtsJob job =
                    new com.fongmi.android.tv.tts.TtsJob();
            job.bookTitle = session.bookTitle;
            job.coverUrl = session.coverUrl;
            job.sourceName = session.sourceName;
            job.siteKey = session.siteKey;
            job.legadoKey = session.legadoKey;
            job.ruleTitle = session.ruleTitle;
            job.localId = session.localId;
            job.inlineText = session.text;
            if (session.chapters != null && !session.chapters.isEmpty()) {
                job.chapters.addAll(session.chapters);
            } else {
                job.chapters.add(session.currentChapter());
            }
            job.startChapter = Math.max(0, Math.min(session.chapterIndex,
                    job.chapters.size() - 1));
            job.startFraction = Math.round(currentFraction() * 10000);
            job.voiceId = com.fongmi.android.tv.tts.EdgeVoiceCatalog.defaultVoice();
            job.rate = 1.0f;
            String id = com.fongmi.android.tv.tts.TtsJob.put(job);
            com.fongmi.android.tv.ui.activity.TtsReadActivity.start(this, id);
            setBarsVisible(false);
        } catch (Throwable t) {
            ToastUtil.show(this, "朗读启动失败", Toast.LENGTH_SHORT);
        }
    }

    private void toggleBars() {
        setBarsVisible(!barsShown);
    }

    // ---------------- 翻页模式 ----------------

    private int pageMode() {
        return prefs.getInt(KEY_PAGE_MODE, 0);
    }

    /**
     * 内容区点按：纵滑模式点哪都呼出控制栏；
     * 覆盖/滑动模式左1/3上一页、右1/3下一页、中间呼出控制栏。
     */
    private boolean onContentTap(float x) {
        if (pageMode() == 0) {
            toggleBars();
            return true;
        }
        float w = scrollView.getWidth();
        if (x < w / 3f) pageBy(-1);
        else if (x > w * 2f / 3f) pageBy(1);
        else toggleBars();
        return true;
    }

    /** 整屏翻页：覆盖=直接跳，滑动=带动画。 */
    private void pageBy(int dir) {
        int amount = Math.max(1, scrollView.getHeight() - dp(48));
        if (pageMode() == 2) scrollView.smoothScrollBy(0, dir * amount);
        else scrollView.scrollBy(0, dir * amount);
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (prefs.getBoolean(KEY_VOLUME_PAGE, false)
                && (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) {
            pageBy(keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ? 1 : -1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------------- 字体与行距 ----------------

    /** 字体：0默认 1衬线 2等宽 3导入ttf。 */
    private void applyTextStyle() {
        if (tvContent == null) return;
        int font = prefs.getInt(KEY_FONT, 0);
        android.graphics.Typeface tf = android.graphics.Typeface.DEFAULT;
        if (font == 1) tf = android.graphics.Typeface.SERIF;
        else if (font == 2) tf = android.graphics.Typeface.MONOSPACE;
        else if (font == 3) {
            String path = prefs.getString(KEY_FONT_PATH, "");
            try {
                if (!TextUtils.isEmpty(path) && new java.io.File(path).exists()) {
                    tf = android.graphics.Typeface.createFromFile(path);
                }
            } catch (Throwable ignored) {
            }
        }
        tvContent.setTypeface(tf);
        int sp = prefs.getInt(KEY_SPACING, 1);
        if (sp == 0) tvContent.setLineSpacing(0, 1.0f);       // 紧凑
        else if (sp == 2) tvContent.setLineSpacing(dp(14), 1.35f); // 疏朗
        else tvContent.setLineSpacing(dp(8), 1.15f);          // 标准
    }

    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> fontPicker =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null
                                || result.getData().getData() == null) return;
                        android.net.Uri uri = result.getData().getData();
                        try {
                            java.io.File dir = new java.io.File(getFilesDir(), "fonts");
                            if (!dir.exists()) dir.mkdirs();
                            java.io.File out = new java.io.File(dir, "custom.ttf");
                            try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                                 java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                                byte[] buf = new byte[32768];
                                int n;
                                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                            }
                            prefs.edit().putInt(KEY_FONT, 3)
                                    .putString(KEY_FONT_PATH, out.getAbsolutePath()).apply();
                            applyTextStyle();
                            ToastUtil.show(this, "已应用导入的字体", Toast.LENGTH_SHORT);
                        } catch (Throwable t) {
                            ToastUtil.show(this, "字体导入失败", Toast.LENGTH_SHORT);
                        }
                    });

    private void pickFontFile() {
        try {
            android.content.Intent it = new android.content.Intent(
                    android.content.Intent.ACTION_OPEN_DOCUMENT);
            it.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            it.setType("*/*");
            fontPicker.launch(it);
        } catch (Throwable t) {
            ToastUtil.show(this, "打不开文件选择器", Toast.LENGTH_SHORT);
        }
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
        String name = TextUtils.isEmpty(c.name) ? session.bookTitle : c.name;
        if (tvChapterHead != null) {
            tvChapterHead.setText(name);
            tvChapterHead.setTextColor(fg());
        }
        updateProgressLabel();
    }

    /** 首字下沉（稿）：首个非空白字放大＋金色，纯皮，不改正文。 */
    private CharSequence dropCap(String text) {
        android.text.SpannableString ss =
                new android.text.SpannableString(text);
        int i = 0;
        while (i < text.length()
                && Character.isWhitespace(text.charAt(i))) i++;
        if (i < text.length()) {
            ss.setSpan(new android.text.style.RelativeSizeSpan(2.0f), i,
                    i + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new android.text.style.ForegroundColorSpan(
                    ReadingSkin.GOLD), i, i + 1,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ss;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    // ---------------- 数据 ----------------

    private void loadChapter(int index, boolean restoreProgress) {
        if (loading) return;
        // 越界守卫：py 小说按记录重建是单章会话（chapters.size()==1），旧写法
        // 「size()>1 && index>=size」对单章放行了 index=1，随后 currentChapter()
        // 的 fallback 拿第一章地址重跑解析——转圈半天最后还是第一章。这里直接拦掉。
        // size==0 时 index=0 仍放行（fallback 靠 chapterUrl 取文，老口径不变）。
        int maxIndex = (session.chapters == null || session.chapters.isEmpty())
                ? 0 : session.chapters.size() - 1;
        if (index < 0 || index > maxIndex) {
            ToastUtil.show(this, index < 0 ? "已经是第一章" : "已经是最后一章", Toast.LENGTH_SHORT);
            // 自愈：越界槽位里的缓存只可能是上面这个 bug 写进去的错章正文，清掉
            if (index > maxIndex && session.chapters != null && !session.chapters.isEmpty()) {
                com.fongmi.android.tv.read.ReadingDownload.deleteNovelChapter(
                        this, bookKey(), index);
            }
            return;
        }
        loading = true;
        session.chapterIndex = Math.max(0, index);
        ReadingSession.Chapter ch = session.currentChapter();
        session.chapterUrl = ch.url;
        tvContent.setText("加载中…");
        refreshTitle();
        Task.execute(() -> {
            String text = cachedOrResolve(ch);
            // 进度恢复查记录：整 blob 读写放后台，主线程只管按算好的位置滚动
            float restoreFrac = -1f;
            if (restoreProgress) {
                com.fongmi.android.tv.read.ReadingStore.Record prog =
                        !TextUtils.isEmpty(session.localId)
                                ? com.fongmi.android.tv.read.ReadingStore.findLocal(session.localId)
                                : com.fongmi.android.tv.read.ReadingStore.find(
                                        "NOVEL", session.sourceName, session.bookTitle);
                if (prog != null && prog.position > 0) {
                    // 全表会话按下标记对位；单章会话（书架重开的海阔/Legado/py 老
                    // 记录）下标恒 0 对不上记录里的真下标，改按章节地址认同一章，
                    // 章内进度（万分比）才恢复得回来。
                    boolean sameChapter = prog.chapterIndex == session.chapterIndex
                            || (session.chapters.size() <= 1 && !TextUtils.isEmpty(prog.chapterUrl)
                                    && prog.chapterUrl.equals(session.chapterUrl));
                    if (sameChapter) restoreFrac = prog.position / 10000f;
                }
            }
            final float frac0 = restoreFrac;
            runOnUiThread(() -> {
                loading = false;
                // 新章上屏：自动接下一章的状态复位（防本章滚动事件串扰）
                wasBottom = false;
                sawNotBottom = false;
                autoAdvancedFor = -1;
                loadUiDoneAt = System.currentTimeMillis();
                preloadNextChapter();
                currentText = text;
                // 无缝状态复位回单块（跳章/书签/换源都从这里重新起流）
                seamBlocks.clear();
                seamTexts.clear();
                seamText = null;
                seamKnownChapter = session.chapterIndex;
                if (TextUtils.isEmpty(text)) {
                    tvContent.setText("正文抓取失败，换一章试试");
                } else {
                    seamText = new android.text.SpannableStringBuilder(dropCap(text));
                    tvContent.setText(seamText);
                    seamBlocks.add(new int[]{session.chapterIndex, 0});
                    seamTexts.add(text);
                }
                scrollView.post(() -> {
                    if (session.startFraction >= 0) {
                        // 换源/书签带入的进度优先
                        scrollToFraction(session.startFraction / 10000f);
                        session.startFraction = -1;
                    } else if (restoreProgress) {
                        if (frac0 >= 0) scrollToFraction(frac0);
                        else scrollView.scrollTo(0, 0);
                    } else {
                        scrollView.scrollTo(0, 0);
                    }
                    updateProgressLabel();
                });
                saveProgress();
            });
        });
    }

    /**
     * 取正文：缓存 → 本地书 → 会话自带（py 内联首章）→ py 按章重跑解析 → 海阔按规则抓。
     * 静态化供朗读引擎与章节缓存复用（同口径，不另起炉灶）。
     */
    public static String resolveText(ReadingSession session, ReadingSession.Chapter ch) {
        // 本地书：按章从私有目录懒读
        if (com.fongmi.android.tv.localbook.LocalBookReader.isLocal(ch.url)) {
            return com.fongmi.android.tv.localbook.LocalBookReader.textOf(ch.url);
        }
        // py 小说首章：正文随会话内联
        if (!TextUtils.isEmpty(session.text) && ch == session.currentChapter()
                && session.chapterIndex == 0) {
            return session.text;
        }
        // py 小说其它章：按章重跑 playerContent（判别层会再次命中正文分支）
        if (!TextUtils.isEmpty(session.siteKey) && !TextUtils.isEmpty(ch.url)) {
            try {
                com.fongmi.android.tv.bean.Result r =
                        com.fongmi.android.tv.api.SiteApi.playerContentParseOnly(session.siteKey, "", ch.url);
                ReadingSession rs = com.fongmi.android.tv.read.ReadingGate.extract(r);
                if (rs != null && rs.kind == ReadingSession.Kind.NOVEL && !TextUtils.isEmpty(rs.text)) {
                    return rs.text;
                }
            } catch (Throwable ignored) {
            }
        }
        // Legado 书源：按书源规则抓正文
        if (!TextUtils.isEmpty(session.legadoKey) && !TextUtils.isEmpty(ch.url)) {
            String t = com.fongmi.android.tv.legado.LegadoReading.fetchText(session.legadoKey, ch.url);
            if (!TextUtils.isEmpty(t)) return t;
        }
        // 海阔：按规则抓正文
        if (!TextUtils.isEmpty(session.ruleTitle) && !TextUtils.isEmpty(ch.url)) {
            String t = HkNovelFetcher.fetchText(session.ruleTitle, ch.url);
            if (!TextUtils.isEmpty(t)) return t;
        }
        return "";
    }

    /** 取正文：先看缓存（本地书本身就在本地，不走缓存）。未命中时解析完写回缓存。 */
    private String cachedOrResolve(ReadingSession.Chapter ch) {
        if (TextUtils.isEmpty(session.localId)) {
            String cached = com.fongmi.android.tv.read.ReadingDownload.loadNovelChapter(
                    this, bookKey(), session.chapterIndex);
            if (!TextUtils.isEmpty(cached)) return cached;
            String text = resolveText(session, ch);
            if (!TextUtils.isEmpty(text)) {
                // 读完即写回：重进/切回同章不再重跑源解析（配额与原子写由 ReadingDownload 管）
                com.fongmi.android.tv.read.ReadingDownload.saveNovelChapter(
                        this, bookKey(), session.chapterIndex, text);
            }
            return text;
        }
        return resolveText(session, ch);
    }

    private void goChapter(int index) {
        loadChapter(index, false);
    }

    /**
     * 章末自动接下一章：覆盖/滑动模式用（翻到最后一屏后整章替换）。
     * 纵滑模式已改走无缝拼接（maybeSeamless），不经此路。
     * 只在"刚滑到底"的跳变触发，且本次加载 1.5 秒后才生效——打开时恢复到底部
     * 进度、加载完的初始滚动都不会误触。
     */
    private void maybeAutoNext() {
        if (session == null || loading) return;
        java.util.List<ReadingSession.Chapter> chs = session.chapters;
        if (chs == null || chs.size() <= 1) return;
        // 纵滑模式走无缝拼接（下一章直接接在本章后面连续下滑，不跳页）；
        // 覆盖/滑动模式维持下面的整章替换自动接
        if (pageMode() == 0 && !seamBlocks.isEmpty()) {
            maybeSeamless();
            return;
        }
        if (session.chapterIndex >= chs.size() - 1) {
            wasBottom = true;
            return;
        }
        boolean bottom = currentFraction() >= 0.995f;
        if (!bottom) {
            wasBottom = false;
            sawNotBottom = true;
            return;
        }
        // 已到底。加载后 1.5 秒内到底多半是短章快滑或恢复进度：旧写法在这里把
        // wasBottom 一并置真、把这次到底"吃掉"，此后停在章末再无跳变，自动接
        // 永远不再触发（真机"不灵"的定罪点）。改：窗口内不吞事件、排一次复查；
        // 复查时仍在章末、且本章内确实滚动过（sawNotBottom），才接下一章。
        long sinceLoad = System.currentTimeMillis() - loadUiDoneAt;
        if (sinceLoad <= 1500) {
            scrollView.removeCallbacks(autoNextRecheck);
            scrollView.postDelayed(autoNextRecheck, 1500 - sinceLoad + 100);
            return;
        }
        if (!wasBottom && sawNotBottom && autoAdvancedFor != session.chapterIndex) {
            autoAdvancedFor = session.chapterIndex;
            ToastUtil.show(this, "已自动进入下一章", Toast.LENGTH_SHORT);
            goChapter(session.chapterIndex + 1);
        }
        wasBottom = true;
    }

    // ---------------- 无缝切章（仅纵滑） ----------------

    /**
     * 无缝驱动（滚动事件里调）：先跟当前章（跨界就落进度/预存），
     * 再看离内容底还剩多少——不足一屏半就把下一章拼上来。
     */
    private void maybeSeamless() {
        if (seamText == null || seamBlocks.isEmpty()) return;
        trackSeamChapter();
        if (seamAppending) return;
        int lastIdx = seamBlocks.get(seamBlocks.size() - 1)[0];
        if (lastIdx >= session.chapters.size() - 1) return;
        View child = scrollView.getChildAt(0);
        if (child == null) return;
        int remaining = child.getHeight() - (scrollView.getScrollY() + scrollView.getHeight());
        if (remaining > scrollView.getHeight() * 3 / 2) return;
        appendNextSeamChapter(lastIdx + 1);
    }

    /** 拼下一章：取文口径与预存同路（缓存按目标章下标、未命中解析后回存）。抓不到不拼、停在章末。 */
    private void appendNextSeamChapter(int nextIdx) {
        seamAppending = true;
        final ReadingSession s = session;
        final ReadingSession.Chapter ch = s.chapters.get(nextIdx);
        final String key = bookKey();
        Task.execute(() -> {
            String text = "";
            try {
                if (TextUtils.isEmpty(s.localId)) {
                    text = com.fongmi.android.tv.read.ReadingDownload
                            .loadNovelChapter(this, key, nextIdx);
                    if (TextUtils.isEmpty(text)) {
                        text = resolveText(s, ch);
                        if (!TextUtils.isEmpty(text)) {
                            com.fongmi.android.tv.read.ReadingDownload
                                    .saveNovelChapter(this, key, nextIdx, text);
                        }
                    }
                } else {
                    text = resolveText(s, ch);
                }
            } catch (Throwable ignored) {
                // 取文异常按抓不到处理：不拼、停在章末（seamAppending 由 UI 段复位）
                text = "";
            }
            final String body = text;
            runOnUiThread(() -> {
                seamAppending = false;
                // 期间被跳章/换源复位过：seamText 已换代或尾块已不是上一章，弃拼
                if (session != s || seamText == null || seamBlocks.isEmpty()
                        || seamBlocks.get(seamBlocks.size() - 1)[0] != nextIdx - 1) return;
                if (TextUtils.isEmpty(body)) return;
                int start = seamText.length();
                android.text.SpannableStringBuilder seg =
                        new android.text.SpannableStringBuilder();
                seg.append("\n\n");
                int nameStart = seg.length();
                seg.append(ch.name == null ? "" : ch.name);
                seg.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        nameStart, seg.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                seg.setSpan(new android.text.style.RelativeSizeSpan(1.3f),
                        nameStart, seg.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                seg.append("\n\n");
                seg.append(dropCap(body));
                seamText.append(seg);
                tvContent.setText(seamText);
                seamBlocks.add(new int[]{nextIdx, start});
                seamTexts.add(body);
            });
        });
    }

    /** 块顶 Y（滚动内容坐标）：tvContent 在内容列里的 top＋该块起始偏移所在行的行顶。 */
    private int seamBlockTopY(android.text.Layout layout, int blockPos) {
        int offset = seamBlocks.get(blockPos)[1];
        int line = layout.getLineForOffset(Math.min(offset, Math.max(0, layout.getText().length() - 1)));
        return tvContent.getTop() + layout.getLineTop(line);
    }

    private int seamBlockPosOf(int chapterIndex) {
        for (int i = 0; i < seamBlocks.size(); i++) {
            if (seamBlocks.get(i)[0] == chapterIndex) return i;
        }
        return -1;
    }

    /**
     * 跨界跟踪：当前章＝最后一个「块顶已滚过视口顶」的块。跨界时把会话
     * 章下标/地址/当前正文切过去并落进度、预存再下一章、顺手修剪窗口。
     * 页首章名头（tvChapterHead）不跟着改——它标的是全文第一块的章，
     * 后续各章的章名在流内自带标题行。
     */
    private void trackSeamChapter() {
        if (seamBlocks.size() <= 1 || seamText == null) return;
        android.text.Layout layout = tvContent.getLayout();
        if (layout == null) return;
        int y = scrollView.getScrollY();
        int pos = 0;
        for (int i = 0; i < seamBlocks.size(); i++) {
            if (seamBlockTopY(layout, i) <= y + dp(4)) pos = i;
            else break;
        }
        int chapter = seamBlocks.get(pos)[0];
        if (chapter == seamKnownChapter) return;
        seamKnownChapter = chapter;
        session.chapterIndex = chapter;
        ReadingSession.Chapter ch = session.currentChapter();
        session.chapterUrl = ch.url;
        currentText = seamTexts.get(pos);
        updateProgressLabel();
        saveProgress();
        preloadNextChapter();
        pruneSeamBlocks();
    }

    /** 窗口修剪：只留最近 3 块且当前块在最末才剪首块（剪完 scrollY 同步减首块高，视口不跳）。 */
    private void pruneSeamBlocks() {
        if (seamBlocks.size() < 3 || seamText == null) return;
        if (seamBlockPosOf(session.chapterIndex) != seamBlocks.size() - 1) return;
        android.text.Layout layout = tvContent.getLayout();
        if (layout == null) return;
        int cut = seamBlocks.get(1)[1];
        if (cut <= 0) return;
        int cutHeight = seamBlockTopY(layout, 1) - seamBlockTopY(layout, 0);
        seamText.delete(0, cut);
        seamBlocks.remove(0);
        seamTexts.remove(0);
        for (int[] b : seamBlocks) b[1] -= cut;
        tvContent.setText(seamText);
        scrollView.scrollTo(0, Math.max(0, scrollView.getScrollY() - cutHeight));
    }

    /** 预存下一章：当前章上屏后后台把下一章正文抓进章节缓存，接章时直接命中。 */
    private void preloadNextChapter() {
        if (session == null || !TextUtils.isEmpty(session.localId)) return;
        java.util.List<ReadingSession.Chapter> chs = session.chapters;
        if (chs == null || session.chapterIndex + 1 >= chs.size()) return;
        final ReadingSession s = session;
        final int nextIdx = session.chapterIndex + 1;
        if (preloadingFor == nextIdx) return;
        preloadingFor = nextIdx;
        final String key = bookKey();
        Task.execute(() -> {
            try {
                if (!TextUtils.isEmpty(com.fongmi.android.tv.read.ReadingDownload
                        .loadNovelChapter(this, key, nextIdx))) return;
                String text = resolveText(s, chs.get(nextIdx));
                if (!TextUtils.isEmpty(text)) {
                    com.fongmi.android.tv.read.ReadingDownload
                            .saveNovelChapter(this, key, nextIdx, text);
                }
            } catch (Throwable ignored) {
            } finally {
                if (preloadingFor == nextIdx) preloadingFor = -1;
            }
        });
    }

    /** 书签跳转：可带章内进度。 */
    private void goChapter(int index, int fraction) {
        session.startFraction = fraction;
        loadChapter(index, false);
    }

    /**
     * 弹窗与顶/底栏互斥：栏和弹窗都是半透深色，叠在一起正文、栏字、弹窗字
     * 三层互相透（他 2026-10-31 录屏点名）。凡从栏里点开的弹窗，开前先收栏；
     * 弹窗关后不自动恢复——正文区点一下栏就回来，读完一章回干净页面正好。
     */
    private void dismissBarsForDialog() {
        if (barsShown) setBarsVisible(false);
    }

    private void showChapters() {
        dismissBarsForDialog();
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

    /** 缓存范围选择：后续 20 / 50 / 全部。 */
    private void askCacheRange() {
        String[] items = {"后续 20 章", "后续 50 章", "全部剩余章节"};
        int[] counts = {20, 50, Integer.MAX_VALUE};
        ReadDialogs.options(this, "缓存章节（离线看）", items, which -> {
            ToastUtil.show(this, "后台缓存中，完成后会提示", Toast.LENGTH_SHORT);
                    android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
                    com.fongmi.android.tv.read.ReadingDownload.downloadNextNovel(
                            this, session, bookKey(), counts[which], ui,
                            (ok, total) -> ToastUtil.show(this,
                                    total == 0 ? "没有可缓存的新章节"
                                            : "已缓存 " + ok + " / " + total + " 章",
                                    Toast.LENGTH_SHORT));
        });
    }

    private void changeTextSize(float delta) {
        float cur = prefs.getFloat(KEY_TEXT_SIZE, 18);
        float next = Math.max(13, Math.min(26, cur + delta));
        prefs.edit().putFloat(KEY_TEXT_SIZE, next).apply();
        float frac = currentFraction();
        tvContent.setTextSize(next);
        refreshFontSize();
        scrollView.post(() -> scrollToFraction(frac));
    }

    // ---------------- 进度 ----------------

    private float currentFraction() {
        // 无缝态（多块）：口径是「当前章内」的进度——滚动枢纽（进度标/
        // 书签/听书起点/存进度）都按章记，整篇口径会让跨界后进度倒退
        if (pageMode() == 0 && seamBlocks.size() > 1 && seamText != null) {
            try {
                android.text.Layout layout = tvContent.getLayout();
                int pos = seamBlockPosOf(session.chapterIndex);
                if (layout != null && pos >= 0) {
                    int top = seamBlockTopY(layout, pos);
                    int bottom = pos + 1 < seamBlocks.size()
                            ? seamBlockTopY(layout, pos + 1)
                            : tvContent.getTop() + tvContent.getHeight();
                    int span = bottom - top;
                    if (span <= scrollView.getHeight()) return 1f;
                    int denom = Math.max(1, span - scrollView.getHeight());
                    return Math.min(1f, Math.max(0f,
                            (scrollView.getScrollY() - top) * 1f / denom));
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            // 一屏装得下的短章：没有可滚距离，本身就是已到底（旧口径算成 0，
            // 进度恒 0%、自动接也永远判不到底）
            if (tvContent.getHeight() <= scrollView.getHeight()) return 1f;
            int max = Math.max(1, tvContent.getHeight() - scrollView.getHeight());
            return Math.min(1f, Math.max(0f, scrollView.getScrollY() * 1f / max));
        } catch (Throwable t) {
            return 0f;
        }
    }

    private void scrollToFraction(float frac) {
        // 无缝态：frac 是章内进度，滚到当前块内的对应位置（字号变更后重定位用）
        if (pageMode() == 0 && seamBlocks.size() > 1 && seamText != null) {
            try {
                android.text.Layout layout = tvContent.getLayout();
                int pos = seamBlockPosOf(session.chapterIndex);
                if (layout != null && pos >= 0) {
                    int top = seamBlockTopY(layout, pos);
                    int bottom = pos + 1 < seamBlocks.size()
                            ? seamBlockTopY(layout, pos + 1)
                            : tvContent.getTop() + tvContent.getHeight();
                    int span = Math.max(1, (bottom - top) - scrollView.getHeight());
                    scrollView.scrollTo(0, top + (int) (frac * span));
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            int max = Math.max(1, tvContent.getHeight() - scrollView.getHeight());
            scrollView.scrollTo(0, (int) (frac * max));
        } catch (Throwable ignored) {
        }
    }

    private void updateProgressLabel() {
        if (session == null) return;
        int pct = Math.round(currentFraction() * 100);
        ReadingSession.Chapter c = session.currentChapter();
        String name = TextUtils.isEmpty(c.name) ? "" : c.name;
        if (tvStatus != null) {
            tvStatus.setText((name.isEmpty() ? "" : name + " · ") + pct + "%");
        }
        if (tvChapterMid != null) tvChapterMid.setText(name);
        if (tvChapterBottom != null) tvChapterBottom.setText(name);
        if (tvProgress != null) tvProgress.setText(pct + "%");
        if (progressBar != null) {
            progressBar.setProgress(
                    Math.round(currentFraction() * 10000));
        }
    }

    /**
     * 存进度：滚动位置在主线程取（要读视图），整 blob 的读写串行放单线程后台，
     * 不许在主线程做——记录里带正文快照时 blob 可达数 MB。
     */
    private void saveProgress() {
        // 换源已把本书交给新源：旧页退出存进度会按旧源键把旧书架记录写回来，不存
        if (session != null && session.shelfSuperseded) return;
        final float frac = currentFraction();
        Task.scheduler().execute(() -> saveProgressAt(frac));
    }

    private void saveProgressAt(float fraction) {
        // 异步落盘再查一次：排队期间刚换源成功的，同样不许写回旧记录
        if (session != null && session.shelfSuperseded) return;
        long now = System.currentTimeMillis();
        if (now - lastSaveAt < 2000) return;
        lastSaveAt = now;
        try {
            ReadingStore.Record r = new ReadingStore.Record();
            r.kind = "NOVEL";
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
            r.position = Math.round(fraction * 10000);
            // py 小说：正文存快照，继续读时不用重跑解析（本地书从磁盘懒读，不存快照）
            if (TextUtils.isEmpty(session.ruleTitle) && TextUtils.isEmpty(session.localId)
                    && !TextUtils.isEmpty(currentText)) {
                r.content = currentText.length() > 60000
                        ? currentText.substring(0, 60000) : currentText;
            }
            // 合并写：整条覆盖会抹掉建会话时写入的 bookUrl 等追更字段（R1）
            ReadingStore.saveMerged(r);
        } catch (Throwable ignored) {
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
        refreshTtsSub();
    }
}
