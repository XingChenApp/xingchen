package com.fongmi.android.tv.ui.activity;
import com.fongmi.android.tv.utils.ToastUtil;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.fongmi.android.tv.legado.LegadoAudio;
import com.fongmi.android.tv.legado.LegadoSource;
import com.fongmi.android.tv.legado.LegadoStore;
import com.fongmi.android.tv.localbook.LocalBookImporter;
import com.fongmi.android.tv.read.ReadDialogs;
import com.fongmi.android.tv.read.ReadingRouter;
import com.fongmi.android.tv.read.ReadingSession;
import com.fongmi.android.tv.read.ReadingStore;
import com.fongmi.android.tv.tts.EdgeVoice;
import com.fongmi.android.tv.tts.EdgeVoiceCatalog;
import com.fongmi.android.tv.tts.TtsJob;
import com.fongmi.android.tv.utils.ImgUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.fongmi.android.tv.R;

/**
 * 书架首页（对稿 reading-ui-mockup-v2/home-novel、home-comic）：
 * 小说栏＝在看的小说卡片（封面/进度/继续读）＋听书分区＋听书卡＋我的小说源聚合卡；
 * 漫画栏＝在看的漫画卡片（连载徽）＋我的漫画源聚合卡；
 * 听书栏＝在听的听书卡片＋我的听书源聚合卡。各栏只说各栏的字。
 * 顶栏不设叉（用户规矩：所有页面不要叉，走系统返回）。
 */
public class ReadingShelfActivity extends AppCompatActivity {

    public static void start(Context context) {
        context.startActivity(new Intent(context, ReadingShelfActivity.class));
    }

    /** 落签附加（start 带签位版专用）：新建实例时落到指定签。 */
    private static final String EXTRA_TAB = "shelf_tab";

    /**
     * 带签位回书架（红果听书播放页「回书架」专用口）：栈里已有书架
     * 就回那一个（CLEAR_TOP 保它原签与原位），没有才新建并落到指定
     * 签——红果组在小说签，播放页回书架钉小说签才找得到刚听的书
     * （红果组「红果听书 · 在听」列表）。既有 start(Context) 口径不变，
     * 普通听书/HomeActivity 调用点一字未动。
     */
    public static void start(Context context, String tab) {
        Intent i = new Intent(context, ReadingShelfActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (tab != null) i.putExtra(EXTRA_TAB, tab);
        context.startActivity(i);
    }

    /** 当前栏：小说/漫画/听书分开归栏，取值 NOVEL/COMIC/AUDIO。 */
    private static final String STATE_CURRENT_TAB = "currentTab";
    private String currentTab = "NOVEL";
    private TextView tabNovel;
    private TextView tabComic;
    private TextView tabAudio;
    private TextView importBtn;
    private LinearLayout contentBox;

    private final ActivityResultLauncher<android.content.Intent> filePicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                List<android.net.Uri> uris = new ArrayList<>();
                if (result.getData().getClipData() != null) {
                    int n = result.getData().getClipData().getItemCount();
                    for (int i = 0; i < n; i++) {
                        uris.add(result.getData().getClipData().getItemAt(i).getUri());
                    }
                } else if (result.getData().getData() != null) {
                    uris.add(result.getData().getData());
                }
                importFiles(uris);
            });

    private final ActivityResultLauncher<android.content.Intent> treePicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null
                        || result.getData().getData() == null) return;
                importTree(result.getData().getData());
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 旋转重建：恢复旋转前的栏（buildUi/refresh 都按 currentTab 建页，须在建页前恢复）
        if (savedInstanceState != null) {
            String tab = savedInstanceState.getString(STATE_CURRENT_TAB);
            if ("NOVEL".equals(tab) || "COMIC".equals(tab) || "AUDIO".equals(tab)) currentTab = tab;
        }
        // 带签位新建（回书架专用口）：无重建存档时按附加落签
        if (savedInstanceState == null) {
            String tab = getIntent().getStringExtra(EXTRA_TAB);
            if ("NOVEL".equals(tab) || "COMIC".equals(tab) || "AUDIO".equals(tab)) currentTab = tab;
        }
        ReadingSkin.enableEdgeToEdge(this, true);
        // AI 口令"继续读"中转：直接进最近一本，不在书架停留
        if (getIntent().getBooleanExtra("auto_continue", false)) {
            ReadingRouter.continueReading(this);
            finish();
            return;
        }
        buildUi();
        refresh();

        // 主导航整页跳转（主页↔阅读）：进出都走页签交叉淡出（250ms＋缓动），与底栏页签互切同款
        overridePendingTransition(R.anim.xc_tab_fade_in, R.anim.xc_tab_fade_out);
}

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_CURRENT_TAB, currentTab);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.xc_tab_fade_in, R.anim.xc_tab_fade_out);
    }


    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(ReadingSkin.pageBackground(this));

        // 顶栏：标题＋本地导入；不设叉，走系统返回
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(12);
        topBar.setPadding(pad, dp(16), pad, pad);
        ReadingSkin.applyTopInset(topBar, this, 16);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = dp(8);
        titles.setLayoutParams(tlp);
        TextView title = new TextView(this);
        title.setText("阅读");
        title.setTextSize(22);
        title.getPaint().setFakeBoldText(true);
        title.setTextColor(ReadingSkin.TEXT);
        titles.addView(title);
        TextView sub = new TextView(this);
        sub.setText("小说 · 漫画 · 听书，各看各的");
        sub.setTextSize(12);
        sub.setTextColor(ReadingSkin.SUB);
        sub.setPadding(0, dp(2), 0, 0);
        titles.addView(sub);
        topBar.addView(titles);

        importBtn = new TextView(this);
        importBtn.setText("本地");
        importBtn.setTextSize(16);
        importBtn.setTextColor(ReadingSkin.GOLD_TEXT);
        importBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        importBtn.setOnClickListener(v -> showImportMenu());
        // 搜书：从书源管理页搬过来；搜什么跟着当前页签走（小说→小说源，漫画→漫画源）
        TextView searchBtn = new TextView(this);
        searchBtn.setText("搜书");
        searchBtn.setTextSize(16);
        searchBtn.setTextColor(ReadingSkin.GOLD_TEXT);
        searchBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        searchBtn.setOnClickListener(v -> showHomeSearch());
        topBar.addView(searchBtn);
        topBar.addView(importBtn);
        root.addView(topBar);

        // 小说/漫画分栏：胶囊切换（选中金字＋金描边）
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackground(ReadingSkin.segContainer(this));
        LinearLayout.LayoutParams tabslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tabslp.leftMargin = dp(20);
        tabslp.rightMargin = dp(20);
        tabslp.topMargin = dp(12);
        tabs.setLayoutParams(tabslp);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabNovel = makeTab("小 说", "NOVEL");
        tabComic = makeTab("漫 画", "COMIC");
        tabAudio = makeTab("听 书", "AUDIO");
        tabs.addView(tabNovel);
        tabs.addView(tabComic);
        tabs.addView(tabAudio);
        root.addView(tabs);
        refreshTabs();

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        sv.setLayoutParams(svlp);
        contentBox = new LinearLayout(this);
        contentBox.setOrientation(LinearLayout.VERTICAL);
        contentBox.setPadding(0, 0, 0, dp(24));
        sv.addView(contentBox, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv);

        setContentView(root);
    }

    /**
     * 分家清存量（2026-10-30 分家令第 3 条）：ReadingStore 里 legadoKey 是
     * 红果听书哨兵的 AUDIO 记录，是分家前串线写进来的，不属于普通听书
     * 名单——刷新时逐条清出：清之前把位置按 bookId/itemId 迁进红果自己
     * 的 HgAudioStore（他的进度不丢，只是回家），再从普通名单删除。
     * 迁完即净，后续刷新扫不到，天然只跑一轮。
     */
    private void purgeHgStrays() {
        try {
            for (ReadingStore.Record r : ReadingStore.list()) {
                if (!"AUDIO".equals(r.kind)) continue;
                if (!com.fongmi.android.tv.legado.HgBookBridge.URL_AUDIO
                        .equals(r.legadoKey)) continue;
                String bookId = com.fongmi.android.tv.legado.HgBookBridge
                        .bookIdOf(r.chapterUrl);
                if (!bookId.isEmpty()) {
                    com.fongmi.android.tv.legado.HgAudioStore.save(this, bookId,
                            r.bookTitle, r.coverUrl,
                            com.fongmi.android.tv.legado.HgBookBridge
                                    .itemIdOf(r.chapterUrl),
                            r.chapterName, r.chapterIndex,
                            Math.max(0, r.position));
                }
                ReadingStore.remove(r);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 整页按当前栏重建（书卡片＋分区＋源聚合卡）。 */
    private void refresh() {
        purgeHgStrays();
        List<ReadingStore.Record> novels = new ArrayList<>();
        List<ReadingStore.Record> comics = new ArrayList<>();
        List<ReadingStore.Record> audios = new ArrayList<>();
        ReadingStore.Record latestAudio = null;
        for (ReadingStore.Record r : ReadingStore.list()) {
            if ("NOVEL".equals(r.kind)) novels.add(r);
            else if ("COMIC".equals(r.kind)) comics.add(r);
            else if ("AUDIO".equals(r.kind) || "TTS".equals(r.kind)) {
                if (latestAudio == null || r.updatedAt > latestAudio.updatedAt) latestAudio = r;
                audios.add(r);
            }
        }
        audios.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        // 本地导入的书只落 LocalBookStore，没读过就没有 ReadingStore 记录，
        // 上面的列表只读 ReadingStore——导入完在书架里凭空消失（D1）。
        // 按 localId 对账补列：已有记录的以记录为准（带进度），没记录的用
        // 注册表元数据合成一张卡；点击仍走 openRecord 的本地分支
        // （LocalBookSession 全表重建），不另造打开路径。
        java.util.Set<String> recordedLocal = new java.util.HashSet<>();
        for (ReadingStore.Record r : novels) {
            if ("local".equals(r.origin) && !TextUtils.isEmpty(r.localId)) recordedLocal.add(r.localId);
        }
        for (ReadingStore.Record r : comics) {
            if ("local".equals(r.origin) && !TextUtils.isEmpty(r.localId)) recordedLocal.add(r.localId);
        }
        for (com.fongmi.android.tv.localbook.LocalBook b
                : com.fongmi.android.tv.localbook.LocalBookStore.list()) {
            if (b == null || TextUtils.isEmpty(b.id) || recordedLocal.contains(b.id)) continue;
            ReadingStore.Record r = new ReadingStore.Record();
            r.kind = "COMIC".equals(b.kind) ? "COMIC" : "NOVEL";
            r.sourceName = "本地书";
            r.bookTitle = b.title == null ? "" : b.title;
            r.coverUrl = TextUtils.isEmpty(b.coverPath) ? "" : "file://" + b.coverPath;
            r.origin = "local";
            r.localId = b.id;
            r.chapterIndex = 0;
            if (b.chapters != null && !b.chapters.isEmpty()) {
                r.chapterName = b.chapters.get(0).name == null ? "" : b.chapters.get(0).name;
            }
            r.updatedAt = b.addedAt;
            if ("COMIC".equals(r.kind)) comics.add(r);
            else novels.add(r);
        }
        novels.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        comics.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        boolean novel = "NOVEL".equals(currentTab);
        boolean comic = "COMIC".equals(currentTab);
        // 听书栏没有本地导入：隐藏"本地"按钮，小说漫画栏照旧
        importBtn.setVisibility(novel || comic ? View.VISIBLE : View.GONE);
        contentBox.removeAllViews();
        if (novel) {
            // 在看的小说：标题栏右侧「全部 ›」进完整小说页（横滑只管接着读，书多了走全量页）
            LinearLayout novelHeader = ReadingSkin.sectionHeader(this, "在看的小说", "长按移除", null);
            TextView allBtn = new TextView(this);
            allBtn.setText("全部 ›");
            allBtn.setTextSize(13);
            allBtn.setTextColor(ReadingSkin.GOLD_TEXT);
            allBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
            allBtn.setOnClickListener(v ->
                    startActivity(new android.content.Intent(this, AllNovelsActivity.class)));
            novelHeader.addView(allBtn);
            contentBox.addView(novelHeader);
            if (novels.isEmpty()) contentBox.addView(emptyView(
                    "还没有在看的小说\n导入一本 txt / epub，或去看一本在线小说，或去听一本书"));
            else if (novels.size() <= 3) {
                for (ReadingStore.Record r : novels) contentBox.addView(bookCard(r, false));
            } else {
                // 在看区只露 3 本的高度，多的在区域内滑动看（他 2026-10-31
                // 亲口纠正：留 3＋多的可以滑动；标题栏「全部 ›」是另一个
                // 独立入口、跟本区的滑动不搭界，不是谁的折叠去处——初版
                // 只渲染 3 本、拿全部页当折叠去处，是助手会错意，已重落）。
                // 内层 NestedScrollView 嵌在页面 ScrollView 里：手势一落
                // 在本区，先禁外层拦截，保证拖动滚的是书单而不是整页。
                LinearLayout novelList = new LinearLayout(this);
                novelList.setOrientation(LinearLayout.VERTICAL);
                for (ReadingStore.Record r : novels) novelList.addView(bookCard(r, false));
                NestedScrollView nsv = new NestedScrollView(this) {
                    @Override
                    public boolean onInterceptTouchEvent(MotionEvent ev) {
                        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return super.onInterceptTouchEvent(ev);
                    }
                };
                nsv.addView(novelList, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                contentBox.addView(nsv, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                // 布局完成后把区域高度钉成前 3 本（含卡间距）的总高
                nsv.post(() -> {
                    int h = 0;
                    for (int i = 0; i < 3 && i < novelList.getChildCount(); i++) {
                        View child = novelList.getChildAt(i);
                        ViewGroup.MarginLayoutParams mlp =
                                (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                        h += child.getHeight() + mlp.topMargin + mlp.bottomMargin;
                    }
                    if (h > 0) {
                        ViewGroup.LayoutParams lp = nsv.getLayoutParams();
                        if (lp != null && lp.height != h) {
                            lp.height = h;
                            nsv.setLayoutParams(lp);
                        }
                    }
                });
            }
            if (latestAudio != null) {
                contentBox.addView(ReadingSkin.sectionHeader(this, "听书", "小说直接听", null));
                contentBox.addView(audioCard(latestAudio));
            }
            contentBox.addView(ximaCard());
            contentBox.addView(ReadingSkin.sectionHeader(this, "我的小说源", null, null));
            contentBox.addView(sourceCard(true));
            // 红果组（他 2026-10-31 亲裁：红果来的内容在阅读面里自成
            // 一组、与普通书源和在看列表分隔明示，不散插混排；红果
            // 听书入口将来同组并排）。本组两行与下方 hgNovelHistoryCard
            // 方法为一整块，朋友版剥离时整块删净、阅读面照常运行。
            contentBox.addView(ReadingSkin.sectionHeader(this, "红果", null, null));
            contentBox.addView(hgNovelHistoryCard());
            contentBox.addView(hgAudioEntryCard());
            contentBox.addView(hgAudioListeningCard());
        } else if (comic) {
            contentBox.addView(ReadingSkin.sectionHeader(this, "在看的漫画", "长按移除", null));
            if (comics.isEmpty()) contentBox.addView(emptyView(
                    "还没有在看的漫画\n导入一个图包 / 图片文件夹，或去看一本在线漫画"));
            else contentBox.addView(comicGrid(comics));
            contentBox.addView(ReadingSkin.sectionHeader(this, "我的漫画源", null, null));
            contentBox.addView(sourceCard(false));
            TextView note = new TextView(this);
            note.setText("漫画栏只收漫画：源、书架、添加入口都在这边，"
                    + "小说那边的东西不会串过来。");
            note.setTextSize(12);
            note.setTextColor(ReadingSkin.FAINT);
            note.setLineSpacing(dp(4), 1f);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.leftMargin = dp(20);
            nlp.rightMargin = dp(20);
            nlp.topMargin = dp(8);
            note.setLayoutParams(nlp);
            contentBox.addView(note);
        } else {
            contentBox.addView(ReadingSkin.sectionHeader(this, "在听的听书", "长按移除", null));
            if (audios.isEmpty()) contentBox.addView(emptyView(
                    "还没有在听的听书\n去搜一本，或先装一个听书源"));
            else for (ReadingStore.Record r : audios) contentBox.addView(audioCard(r));
            contentBox.addView(ReadingSkin.sectionHeader(this, "我的听书源", null, null));
            int[] counts = sourceCounts();
            contentBox.addView(audioSourceCard(counts[1]));
            if (counts[1] == 0) {
                TextView note = new TextView(this);
                note.setText("还没装听书源，点上面的卡片进去添加。");
                note.setTextSize(12);
                note.setTextColor(ReadingSkin.FAINT);
                note.setLineSpacing(dp(4), 1f);
                LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                nlp.leftMargin = dp(20);
                nlp.rightMargin = dp(20);
                nlp.topMargin = dp(8);
                note.setLayoutParams(nlp);
                contentBox.addView(note);
            }
        }
        List<ReadingStore.Record> cands = new ArrayList<>(novels);
        cands.addAll(comics);
        checkUpdates(cands);
    }

    private TextView emptyView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(ReadingSkin.FAINT);
        tv.setGravity(Gravity.CENTER);
        tv.setLineSpacing(dp(6), 1f);
        tv.setPadding(0, dp(48), 0, dp(48));
        return tv;
    }

    /** 素封面垫（没封面时用）：深暖灰纯色底＋书名居中暖白色正经排字，不花哨。 */
    private void plainCover(Context c, ImageView cover, TextView title, int radiusDp) {
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2E2822);
        bg.setCornerRadius(dp(radiusDp));
        cover.setBackground(bg);
        title.setTextSize(13);
        title.setTextColor(0xFFE9DFD0);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(8), dp(8), dp(8), dp(8));
        title.setMaxLines(4);
        title.setEllipsize(TextUtils.TruncateAt.END);
    }

    /** 漫画栏：在看的漫画＝三列海报网格（封面大图＋图下书名/话数/进度），跟小说栏文字列表一眼分家。 */
    private View comicGrid(List<ReadingStore.Record> comics) {
        Context c = this;
        android.widget.GridLayout grid = new android.widget.GridLayout(c);
        int cols = 3;
        grid.setColumnCount(cols);
        int gap = dp(12);
        int cellW = (getResources().getDisplayMetrics().widthPixels - dp(32) - gap * (cols - 1)) / cols;
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.leftMargin = dp(16);
        glp.rightMargin = dp(16);
        glp.bottomMargin = dp(4);
        grid.setLayoutParams(glp);
        for (int i = 0; i < comics.size(); i++) {
            grid.addView(comicPoster(comics.get(i), cellW, i % cols != cols - 1 ? gap : 0, gap));
        }
        return grid;
    }

    /** 漫画海报卡：大封面（连载徽）＋图下书名＋看到第 X 话＋细进度条。点击继续看，长按移除。 */
    private View comicPoster(ReadingStore.Record r, int cellW, int gapRight, int gapBottom) {
        Context c = this;
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        android.widget.GridLayout.LayoutParams clp = new android.widget.GridLayout.LayoutParams();
        clp.width = cellW;
        clp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        clp.rightMargin = gapRight;
        clp.bottomMargin = gapBottom;
        card.setLayoutParams(clp);

        FrameLayout coverWrap = new FrameLayout(c);
        coverWrap.setLayoutParams(new LinearLayout.LayoutParams(cellW, (int) (cellW * 1.33)));
        ImageView cover = new ImageView(c);
        cover.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView coverTitle = new TextView(c);
        plainCover(c, cover, coverTitle, 10);
        ReadingSkin.roundClip(cover, c, 10);
        coverWrap.addView(cover);
        coverWrap.addView(coverTitle, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (r.totalChapters > 0) {
            TextView badge = new TextView(c);
            badge.setText(r.chapterIndex + 1 >= r.totalChapters ? "已完结" : "连载中");
            badge.setTextSize(10);
            badge.setTextColor(0xFFFFFFFF);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(6), dp(3), dp(6), dp(3));
            GradientDrawable bbg = new GradientDrawable();
            bbg.setColor(0x99000000);
            bbg.setCornerRadius(dp(6));
            badge.setBackground(bbg);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.leftMargin = dp(6);
            blp.topMargin = dp(6);
            blp.gravity = Gravity.START | Gravity.TOP;
            coverWrap.addView(badge, blp);
        }
        card.addView(coverWrap);

        TextView tvTitle = new TextView(c);
        tvTitle.setTextSize(12);
        tvTitle.getPaint().setFakeBoldText(true);
        tvTitle.setTextColor(ReadingSkin.TEXT);
        tvTitle.setMaxLines(2);
        // 定高（卡片固定口径）：标题恒占两行高，1 行标题不比 2 行的矮一截，同排三格等高
        tvTitle.setMinLines(2);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        tvTitle.setPadding(0, dp(6), 0, 0);
        tvTitle.setText(r.bookTitle);
        card.addView(tvTitle);

        TextView tvSub = new TextView(c);
        tvSub.setTextSize(10);
        tvSub.setTextColor(ReadingSkin.SUB);
        tvSub.setSingleLine(true);
        tvSub.setEllipsize(TextUtils.TruncateAt.END);
        tvSub.setPadding(0, dp(2), 0, 0);
        tvSub.setText("看到第 " + (r.chapterIndex + 1) + " 话"
                + (r.totalChapters > 0 ? " · 全 " + r.totalChapters + " 话" : ""));
        card.addView(tvSub);

        int pct = progressPct(r);
        android.widget.ProgressBar bar = new android.widget.ProgressBar(c, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setProgressDrawable(ReadingSkin.goldProgressBar(c));
        bar.setMax(100);
        bar.setProgress(Math.max(0, Math.min(100, pct)));
        // 定高（卡片固定口径）：无进度数据时以 INVISIBLE 占住这一行，免同排卡片一张高一张矮
        if (pct < 0) bar.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        blp2.topMargin = dp(6);
        card.addView(bar, blp2);

        if (!TextUtils.isEmpty(r.coverUrl)) {
            ImgUtil.load(r.bookTitle, r.coverUrl, cover, true);
            coverTitle.setVisibility(View.GONE);
        } else {
            cover.setImageDrawable(null);
            coverTitle.setVisibility(View.VISIBLE);
            coverTitle.setText(r.bookTitle);
        }
        View.OnClickListener open = v -> {
            com.fongmi.android.tv.read.ReadingUpdateChecker.clearUpdate(r);
            ReadingRouter.openRecord(ReadingShelfActivity.this, r);
        };
        card.setOnClickListener(open);
        card.setOnLongClickListener(v -> {
            removeRecord(r);
            return true;
        });
        return card;
    }

    /** 书卡片：封面＋书名＋看到第 X 章/话·全 Y＋金色进度条＋百分比·人话时间＋继续读。 */
    private View bookCard(ReadingStore.Record r, boolean comic) {
        Context c = this;
        LinearLayout item = new LinearLayout(c);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(14);
        item.setPadding(pad, pad, pad, pad);
        item.setBackground(ReadingSkin.glassCard(c, 16));
        try {
            item.setElevation(dp(4));
        } catch (Throwable ignored) {
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.bottomMargin = dp(12);
        item.setLayoutParams(lp);

        // 封面：渐变占位＋白字书名（无图时）；漫画加连载徽
        FrameLayout coverWrap = new FrameLayout(c);
        int cw = dp(64);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(cw,
                (int) (cw * 1.32));
        coverWrap.setLayoutParams(clp);
        ImageView cover = new ImageView(c);
        cover.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        TextView coverTitle = new TextView(c);
        plainCover(c, cover, coverTitle, 8);
        ReadingSkin.roundClip(cover, c, 8);
        coverWrap.addView(cover);
        coverWrap.addView(coverTitle, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (comic && r.totalChapters > 0) {
            TextView badge = new TextView(c);
            badge.setText(r.chapterIndex + 1 >= r.totalChapters ? "已完结" : "连载中");
            badge.setTextSize(10);
            badge.setTextColor(0xFFFFFFFF);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(6), dp(3), dp(6), dp(3));
            GradientDrawable bbg = new GradientDrawable();
            bbg.setColor(0x99000000);
            bbg.setCornerRadius(dp(6));
            badge.setBackground(bbg);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.leftMargin = dp(4);
            blp.topMargin = dp(4);
            blp.gravity = Gravity.START | Gravity.TOP;
            coverWrap.addView(badge, blp);
        }
        item.addView(coverWrap);

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        mlp.leftMargin = dp(14);
        mid.setLayoutParams(mlp);

        TextView tvTitle = new TextView(c);
        tvTitle.setTextSize(16);
        tvTitle.getPaint().setFakeBoldText(true);
        tvTitle.setTextColor(ReadingSkin.TEXT);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        tvTitle.setText(r.bookTitle);
        mid.addView(tvTitle);

        String unit = comic ? "话" : "章";
        TextView tvSub = new TextView(c);
        tvSub.setTextSize(13);
        tvSub.setTextColor(ReadingSkin.SUB);
        tvSub.setSingleLine(true);
        tvSub.setEllipsize(TextUtils.TruncateAt.END);
        tvSub.setPadding(0, dp(6), 0, 0);
        tvSub.setText("看到第 " + (r.chapterIndex + 1) + " " + unit
                + (r.totalChapters > 0 ? " · 全 " + r.totalChapters + " " + unit : ""));
        mid.addView(tvSub);

        android.widget.ProgressBar bar =
                new android.widget.ProgressBar(c, null,
                        android.R.attr.progressBarStyleHorizontal);
        bar.setProgressDrawable(ReadingSkin.goldProgressBar(c));
        bar.setMax(100);
        int pct = progressPct(r);
        bar.setProgress(Math.max(0, Math.min(100, pct)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        blp.topMargin = dp(10);
        mid.addView(bar, blp);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        TextView tvProgress = new TextView(c);
        tvProgress.setTextSize(12);
        tvProgress.setTextColor(ReadingSkin.SUB);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tvProgress.setLayoutParams(plp);
        tvProgress.setText((pct >= 0 ? pct + "% · " : "")
                + humanTime(r.updatedAt) + (r.hasUpdate ? " · 有更新" : ""));
        row.addView(tvProgress);
        TextView btnGo = new TextView(c);
        btnGo.setText("继续读");
        btnGo.setTextSize(13);
        btnGo.getPaint().setFakeBoldText(true);
        btnGo.setTextColor(0xFF0A2115);
        btnGo.setBackground(ReadingSkin.goldCapsule(c));
        btnGo.setPadding(dp(16), dp(8), dp(16), dp(8));
        row.addView(btnGo);
        mid.addView(row);

        item.addView(mid);

        if (!TextUtils.isEmpty(r.coverUrl)) {
            ImgUtil.load(r.bookTitle, r.coverUrl, cover, true);
            coverTitle.setVisibility(View.GONE);
        } else {
            cover.setImageDrawable(null);
            coverTitle.setVisibility(View.VISIBLE);
            coverTitle.setText(r.bookTitle);
        }
        View.OnClickListener open = v -> {
            com.fongmi.android.tv.read.ReadingUpdateChecker.clearUpdate(r);
            ReadingRouter.openRecord(ReadingShelfActivity.this, r);
        };
        item.setOnClickListener(open);
        btnGo.setOnClickListener(open);
        item.setOnLongClickListener(v -> {
            removeRecord(r);
            return true;
        });
        return item;
    }

    /** 进度百分比：优先按章节数算，缺总数时按千分比回退，缺数据时 -1。 */
    private int progressPct(ReadingStore.Record r) {
        if (r.totalChapters > 0) {
            return Math.round((r.chapterIndex + 1) * 100f / r.totalChapters);
        }
        if (r.position > 0) return Math.round(r.position / 100f);
        return -1;
    }

    /** 听书卡：圆盘＋书名·章节＋上次听到＋音色标签＋大金色播放钮。 */
    private View audioCard(ReadingStore.Record r) {
        Context c = this;
        boolean isTts = "TTS".equals(r.kind);
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(14);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(ReadingSkin.glassCard(c, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        // 圆盘：深色盘面＋金色盘心
        FrameLayout disc = new FrameLayout(c);
        int ds = dp(60);
        disc.setLayoutParams(new LinearLayout.LayoutParams(ds, ds));
        GradientDrawable discBg = new GradientDrawable();
        discBg.setShape(GradientDrawable.OVAL);
        discBg.setColor(0xFF2B2B34);
        discBg.setStroke(dp(1), 0xFF454550);
        disc.setBackground(discBg);
        View hub = new View(c);
        int hs = dp(17);
        GradientDrawable hubBg = new GradientDrawable();
        hubBg.setShape(GradientDrawable.OVAL);
        hubBg.setColor(ReadingSkin.GOLD);
        hub.setBackground(hubBg);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(hs, hs);
        hlp.gravity = Gravity.CENTER;
        disc.addView(hub, hlp);
        card.addView(disc);

        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        mlp.leftMargin = dp(14);
        mlp.rightMargin = dp(8);
        meta.setLayoutParams(mlp);
        TextView at = new TextView(c);
        at.setTextSize(17);
        at.getPaint().setFakeBoldText(true);
        at.setTextColor(ReadingSkin.TEXT);
        at.setSingleLine(true);
        at.setEllipsize(TextUtils.TruncateAt.END);
        at.setText(r.bookTitle + " · " + (TextUtils.isEmpty(r.chapterName)
                ? "" : r.chapterName));
        meta.addView(at);
        TextView ak = new TextView(c);
        ak.setTextSize(13);
        ak.setTextColor(ReadingSkin.SUB);
        ak.setPadding(0, dp(6), 0, 0);
        ak.setText(r.position > 0 ? "上次听到 " + fmtMs(r.position) : "还没开始听");
        meta.addView(ak);
        if (isTts) {
            TextView pill = new TextView(c);
            pill.setTextSize(12);
            pill.setTextColor(ReadingSkin.GOLD_TEXT);
            pill.setPadding(dp(10), dp(4), dp(10), dp(4));
            GradientDrawable pbg = new GradientDrawable();
            pbg.setCornerRadius(dp(999));
            pbg.setStroke(dp(2), 0x8045C08A);
            pbg.setColor(0x1A45C08A);
            pill.setBackground(pbg);
            pill.setText(defaultVoiceLabel());
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.topMargin = dp(8);
            pill.setLayoutParams(plp);
            meta.addView(pill);
        }
        card.addView(meta);

        TextView play = new TextView(c);
        play.setText("▶");
        play.setTextSize(20);
        play.setTextColor(0xFF0A2115);
        play.setGravity(Gravity.CENTER);
        int ps = dp(52);
        GradientDrawable playBg = new GradientDrawable();
        playBg.setShape(GradientDrawable.OVAL);
        playBg.setColors(new int[]{0xFF63D99D, 0xFF379B63});
        playBg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        play.setBackground(playBg);
        play.setLayoutParams(new LinearLayout.LayoutParams(ps, ps));
        play.setOnClickListener(v -> {
            if (isTts) openTtsRecord(r);
            else openAudioRecord(r);
        });
        card.addView(play);
        card.setOnClickListener(v -> {
            if (isTts) openTtsRecord(r);
            else openAudioRecord(r);
        });
        // 长按移除（与小说签同口径，2026-10-30 补）：听书签此前没有任何
        // 删除口。顺序不能反——先关小条会话（删的正是条上挂的那条时，
        // 真关闭的落盘会把记录写回），再删记录本体（进度一并清）。
        card.setOnLongClickListener(v -> {
            try {
                com.fongmi.android.tv.ui.minibar.ListenMiniBar.get(this)
                        .closeSessionIfTitle(isTts ? "tts" : "audiobook",
                                r.bookTitle);
            } catch (Throwable ignored) {
            }
            removeRecord(r);
            return true;
        });
        return card;
    }

    /** 听书卡：去听书源列表。 */
    private View ximaCard() {
        Context c = this;
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(ReadingSkin.glassCard(c, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        meta.setLayoutParams(mlp);
        TextView n = new TextView(c);
        n.setText("听书");
        n.setTextSize(15);
        n.getPaint().setFakeBoldText(true);
        n.setTextColor(ReadingSkin.TEXT);
        meta.addView(n);
        card.addView(meta);

        // 右侧小字：照书源仓库卡那行抄（20sp/FAINT），文案「管理 ›」
        TextView go = new TextView(c);
        go.setText("管理 ›");
        go.setTextSize(20);
        go.setTextColor(ReadingSkin.FAINT);
        card.addView(go);

        View.OnClickListener open = v -> LegadoActivity.start(ReadingShelfActivity.this,
                LegadoSource.TYPE_AUDIO);
        card.setOnClickListener(open);
        return card;
    }

    /**
     * 红果组·小说历史入口卡（整块可剥）：卡面构建与点击处理全在本
     * 方法内自成一段，对红果本体的引用只此一处且用全限定名、无
     * import；朋友版剥离时整段删本方法＋小说签末尾红果组两行
     * addView，阅读面其余部分零牵连、照常编译运行。卡面照本面
     * 入口卡同款（glassCard 玻璃卡＋15sp 粗标题＋右侧小字），点开
     * 直达红果小说历史面（深底列表＋删除口现成）。
     */
    private View hgNovelHistoryCard() {
        Context c = this;
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(ReadingSkin.glassCard(c, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        meta.setLayoutParams(mlp);
        TextView n = new TextView(c);
        n.setText("红果小说历史");
        n.setTextSize(15);
        n.getPaint().setFakeBoldText(true);
        n.setTextColor(ReadingSkin.TEXT);
        meta.addView(n);
        card.addView(meta);

        TextView go = new TextView(c);
        go.setText("查看 ›");
        go.setTextSize(20);
        go.setTextColor(ReadingSkin.FAINT);
        card.addView(go);

        card.setOnClickListener(v ->
                com.fongmi.android.tv.hongguo.ui.HongguoMineActivity
                        .startNovelHistory(ReadingShelfActivity.this));
        return card;
    }

    /**
     * 红果组·听书入口卡（整块可剥）：与上方小说历史卡同组并排（他
     * 2026-10-31 亲裁：红果小说、红果听书都在阅读那边、自成一组）。
     * 卡面构建与点击处理全在本方法内自成一段；点开落点是红果书城
     * 听书面（现行唯一的红果听书挑书主面，此前只挂在书源管理页的
     * 红果听书源行上）。对红果本体的引用只此一处且用类名字符串
     * （照 LegadoActivity 既有先例）、零 import——朋友版剥离时整段
     * 删本方法＋红果组内本行 addView，阅读面照常编译运行。卡面照
     * 本组小说历史卡同款（glassCard 玻璃卡＋15sp 粗标题＋右侧小字）。
     */
    private View hgAudioEntryCard() {
        Context c = this;
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(ReadingSkin.glassCard(c, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        meta.setLayoutParams(mlp);
        TextView n = new TextView(c);
        n.setText("红果听书");
        n.setTextSize(15);
        n.getPaint().setFakeBoldText(true);
        n.setTextColor(ReadingSkin.TEXT);
        meta.addView(n);
        card.addView(meta);

        TextView go = new TextView(c);
        go.setText("查看 ›");
        go.setTextSize(20);
        go.setTextColor(ReadingSkin.FAINT);
        card.addView(go);

        card.setOnClickListener(v -> {
            Intent i = new Intent();
            i.setClassName(ReadingShelfActivity.this,
                    "com.fongmi.android.tv." + "hongguo.book.HgBookBrowseActivity");
            i.putExtra("hg_book_audio", true);
            startActivity(i);
        });
        return card;
    }

    /**
     * 红果组·听书在听入口卡（整块可剥）：与上方两卡同组并排，排在
     * 听书（书城）卡之后（他 2026-10-31 亲裁「造一个呗、反正单独
     * 的听书」——给红果听书造专属在听列表）。卡面构建与点击处理
     * 全在本方法内自成一段，对红果本体的引用只此一处且用全限定
     * 名、无 import；朋友版剥离时整段删本方法＋红果组内本行
     * addView，与另两卡的剥离块互不交叉，阅读面照常编译运行。
     * 卡面照本组两卡同款（glassCard 玻璃卡＋15sp 粗标题＋右侧
     * 小字），点开直达红果听书在听列表面（深底列表＋编辑/长按
     * 删除套，返回直退本面）。
     */
    private View hgAudioListeningCard() {
        Context c = this;
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(ReadingSkin.glassCard(c, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        meta.setLayoutParams(mlp);
        TextView n = new TextView(c);
        n.setText("红果听书 · 在听");
        n.setTextSize(15);
        n.getPaint().setFakeBoldText(true);
        n.setTextColor(ReadingSkin.TEXT);
        meta.addView(n);
        card.addView(meta);

        TextView go = new TextView(c);
        go.setText("查看 ›");
        go.setTextSize(20);
        go.setTextColor(ReadingSkin.FAINT);
        card.addView(go);

        card.setOnClickListener(v ->
                com.fongmi.android.tv.hongguo.ui.HongguoMineActivity
                        .startAudioHistory(ReadingShelfActivity.this));
        return card;
    }

    /** 书源聚合卡：书源仓库·N 个源，点进书源管理。 */
    private View sourceCard(boolean novelTab) {
        Context c = this;
        int[] counts = sourceCounts();
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(ReadingSkin.glassCard(c, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        TextView icon = new TextView(c);
        icon.setText(novelTab ? "书" : "漫");
        icon.setTextSize(20);
        icon.getPaint().setFakeBoldText(true);
        icon.setTextColor(ReadingSkin.GOLD_TEXT);
        icon.setGravity(Gravity.CENTER);
        int is = dp(44);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setCornerRadius(dp(11));
        ibg.setColors(new int[]{0x4D45C08A, 0x1445C08A});
        ibg.setOrientation(GradientDrawable.Orientation.TL_BR);
        ibg.setStroke(dp(2), 0x6645C08A);
        icon.setBackground(ibg);
        icon.setLayoutParams(new LinearLayout.LayoutParams(is, is));
        card.addView(icon);

        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        mlp.leftMargin = dp(12);
        meta.setLayoutParams(mlp);
        TextView a = new TextView(c);
        a.setTextSize(15);
        a.getPaint().setFakeBoldText(true);
        a.setTextColor(ReadingSkin.TEXT);
        int titleCount = novelTab ? counts[0] : counts[2];
        a.setText((novelTab ? "书源仓库 · " : "漫画源 · ") + titleCount + " 个源");
        meta.addView(a);
        TextView b = new TextView(c);
        b.setTextSize(12);
        b.setTextColor(ReadingSkin.SUB);
        b.setPadding(0, dp(4), 0, 0);
        String sub = novelTab
                ? ("文本源 " + counts[0] + " · 听书源 " + counts[1])
                : ("图片源 " + counts[2]);
        String sync = syncText();
        b.setText(sub + (TextUtils.isEmpty(sync) ? "" : " · " + sync));
        meta.addView(b);
        card.addView(meta);

        TextView arrow = new TextView(c);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setTextColor(ReadingSkin.FAINT);
        card.addView(arrow);

        card.setOnClickListener(v -> LegadoActivity.start(ReadingShelfActivity.this,
                novelTab ? LegadoSource.TYPE_TEXT : LegadoSource.TYPE_IMAGE));
        return card;
    }

    /** 听书源聚合卡：听书源 · N 个源，点进听书源管理（与书源聚合卡同口径）。 */
    private View audioSourceCard(int count) {
        Context c = this;
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(ReadingSkin.glassCard(c, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        TextView icon = new TextView(c);
        icon.setText("听");
        icon.setTextSize(20);
        icon.getPaint().setFakeBoldText(true);
        icon.setTextColor(ReadingSkin.GOLD_TEXT);
        icon.setGravity(Gravity.CENTER);
        int is = dp(44);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setCornerRadius(dp(11));
        ibg.setColors(new int[]{0x4D45C08A, 0x1445C08A});
        ibg.setOrientation(GradientDrawable.Orientation.TL_BR);
        ibg.setStroke(dp(2), 0x6645C08A);
        icon.setBackground(ibg);
        icon.setLayoutParams(new LinearLayout.LayoutParams(is, is));
        card.addView(icon);

        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        mlp.leftMargin = dp(12);
        meta.setLayoutParams(mlp);
        TextView a = new TextView(c);
        a.setTextSize(15);
        a.getPaint().setFakeBoldText(true);
        a.setTextColor(ReadingSkin.TEXT);
        a.setText("听书源 · " + count + " 个源");
        meta.addView(a);
        // 副行只留状态（同步时间），与标题重复的「听书源 N」删掉；无同步信息时不占位。
        String sync = syncText();
        if (!TextUtils.isEmpty(sync)) {
            TextView b = new TextView(c);
            b.setText(sync);
            b.setTextSize(12);
            b.setTextColor(ReadingSkin.SUB);
            b.setPadding(0, dp(4), 0, 0);
            meta.addView(b);
        }
        card.addView(meta);

        TextView arrow = new TextView(c);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setTextColor(ReadingSkin.FAINT);
        card.addView(arrow);

        card.setOnClickListener(v -> LegadoActivity.start(ReadingShelfActivity.this,
                LegadoSource.TYPE_AUDIO));
        return card;
    }

    /** 书源计数：[文本, 听书, 图片]（只计启用的）。 */
    private int[] sourceCounts() {
        int[] r = new int[3];
        try {
            for (LegadoStore.Entry e : LegadoStore.all()) {
                if (!e.enabled) continue;
                LegadoSource s = LegadoStore.parse(e.json);
                if (s == null) continue;
                if (s.bookSourceType == LegadoSource.TYPE_TEXT) r[0]++;
                else if (s.bookSourceType == LegadoSource.TYPE_AUDIO) r[1]++;
                else if (s.bookSourceType == LegadoSource.TYPE_IMAGE) r[2]++;
            }
        } catch (Throwable ignored) {
        }
        return r;
    }

    /** 同步文案：按最近一次添加源的人话时间；无源时空。 */
    private String syncText() {
        try {
            long latest = 0;
            for (LegadoStore.Entry e : LegadoStore.all()) {
                if (e.enabled && e.addedAt > latest) latest = e.addedAt;
            }
            if (latest <= 0) return "";
            return humanTime(latest) + "同步";
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** 人话时间：刚刚 / X 分钟前 / X 小时前 / 昨天 HH:mm / X 天前 / MM-dd。 */
    private String humanTime(long ts) {
        if (ts <= 0) return "";
        long d = System.currentTimeMillis() - ts;
        long min = d / 60000;
        if (min < 1) return "刚刚";
        if (min < 60) return min + " 分钟前";
        long h = min / 60;
        if (h < 24) return h + " 小时前";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        Calendar now = Calendar.getInstance();
        Calendar y = (Calendar) now.clone();
        y.add(Calendar.DAY_OF_YEAR, -1);
        if (c.get(Calendar.YEAR) == y.get(Calendar.YEAR)
                && c.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)) {
            return "昨天 " + new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(new Date(ts));
        }
        long days = h / 24;
        if (days < 30) return days + " 天前";
        return new SimpleDateFormat("MM-dd", Locale.getDefault()).format(new Date(ts));
    }

    /** 默认朗读的音色标签（续播将用的音色）。 */
    private String defaultVoiceLabel() {
        try {
            String id = EdgeVoiceCatalog.defaultVoice();
            EdgeVoice v = EdgeVoiceCatalog.find(EdgeVoiceCatalog.builtIn(), id);
            if (v != null) return v.label();
        } catch (Throwable ignored) {
        }
        return "神经朗读";
    }

    /**
     * 连载更新检查：低频（每书 6 小时节流）、失败静默，有新章给角标。
     * 只查书源的书（本地书/听书不查）。
     */
    private void checkUpdates(List<ReadingStore.Record> cands) {
        java.util.List<ReadingStore.Record> list = new java.util.ArrayList<>();
        for (ReadingStore.Record r : cands) {
            if (!android.text.TextUtils.isEmpty(r.legadoKey)
                    && !android.text.TextUtils.isEmpty(r.bookUrl)) {
                list.add(r);
            }
        }
        if (list.isEmpty()) return;
        android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        com.fongmi.android.tv.read.ReadingUpdateChecker.check(list, ui, changed -> {
            if (changed) refresh();
        });
    }

    private TextView makeTab(String text, String tab) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.getPaint().setFakeBoldText(true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(24), dp(12), dp(24), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> {
            currentTab = tab;
            refreshTabs();
            refresh();
        });
        return tv;
    }

    private void refreshTabs() {
        styleTab(tabNovel, "NOVEL".equals(currentTab));
        styleTab(tabComic, "COMIC".equals(currentTab));
        styleTab(tabAudio, "AUDIO".equals(currentTab));
    }

    /** 胶囊切换：选中金字＋金描边深色胶囊（稿语言）。 */
    private void styleTab(TextView tv, boolean selected) {
        if (selected) {
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable
                                    .Orientation.TOP_BOTTOM,
                            new int[]{0x4D45C08A, 0x2945C08A});
            bg.setCornerRadius(dp(999));
            bg.setStroke(dp(2), 0x8C45C08A);
            tv.setBackground(bg);
            tv.setTextColor(ReadingSkin.GOLD_TEXT);
        } else {
            tv.setBackgroundColor(Color.TRANSPARENT);
            tv.setTextColor(ReadingSkin.SUB);
        }
    }

    /** 当前栏对应的书源类型：小说栏→文本源，漫画栏→图片源，听书栏→听书源。 */
    private int currentType() {
        if ("COMIC".equals(currentTab)) return LegadoSource.TYPE_IMAGE;
        if ("AUDIO".equals(currentTab)) return LegadoSource.TYPE_AUDIO;
        return LegadoSource.TYPE_TEXT;
    }

    /** 搜书：作用域跟着当前页签走（小说→小说源，漫画→漫画源），各搜各的不混。 */
    private void showHomeSearch() {
        SearchPageActivity.start(this, currentType());
    }

    private void showImportMenu() {        boolean comic = "COMIC".equals(currentTab);
        String[] items = comic
                ? new String[]{"导入文件（zip / cbz / cbr / cb7 图包）", "导入图片文件夹"}
                : new String[]{"导入文件（txt / epub / pdf / mobi / azw3 / zip / cbz / cbr / cb7）",
                        "导入图片文件夹"};
        ReadDialogs.options(this, comic ? "导入本地漫画" : "导入本地书", items,
                which -> {
                    if (which == 0) pickFiles(comic);
                    else pickTree();
                });
    }

    private void pickFiles(boolean comic) {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        it.putExtra(Intent.EXTRA_MIME_TYPES, comic ? new String[]{
                "application/zip",
                "application/x-cbz", "application/x-cbr", "application/x-cb7",
                "application/x-zip-compressed"} : new String[]{
                "text/plain", "application/epub+zip", "application/pdf",
                "application/x-mobipocket-ebook", "application/zip",
                "application/x-cbz", "application/x-cbr", "application/x-cb7",
                "application/x-zip-compressed"});
        it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            filePicker.launch(it);
        } catch (Throwable t) {
            ToastUtil.show(this, "打不开文件选择器（选书）", Toast.LENGTH_SHORT);
        }
    }

    private void pickTree() {
        try {
            treePicker.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE));
        } catch (Throwable t) {
            ToastUtil.show(this, "打不开文件夹选择器（选书）", Toast.LENGTH_SHORT);
        }
    }

    private void setImporting(boolean on) {
        importBtn.setEnabled(!on);
        importBtn.setText(on ? "导入中…" : "本地");
    }

    private void importFiles(List<android.net.Uri> uris) {
        if (uris.isEmpty()) return;
        setImporting(true);
        new Thread(() -> {
            int ok = 0;
            String err = "";
            StringBuilder warnAcc = new StringBuilder();
            for (android.net.Uri u : uris) {
                try {
                    LocalBookImporter.Result r = LocalBookImporter.importUri(getApplicationContext(), u);
                    if (r != null && r.warn != null && !r.warn.isEmpty()) {
                        if (warnAcc.length() > 0) warnAcc.append("；");
                        warnAcc.append(r.warn);
                    }
                    ok++;
                } catch (Exception e) {
                    err = e.getMessage();
                }
            }
            final int fOk = ok;
            final String fErr = err;
            final String fWarn = warnAcc.toString();
            runOnUiThread(() -> {
                setImporting(false);
                if (fOk > 0) {
                    ToastUtil.show(ReadingShelfActivity.this,
                            "导入成功 " + fOk + " 本" + (fErr.isEmpty() ? "" : "；失败：" + fErr)
                                    + (fWarn.isEmpty() ? "" : "；" + fWarn),
                            Toast.LENGTH_LONG);
                } else {
                    ToastUtil.show(ReadingShelfActivity.this, "导入失败：" + fErr,
                            Toast.LENGTH_LONG);
                }
                refresh();
            });
        }).start();
    }

    private void importTree(android.net.Uri treeUri) {
        setImporting(true);
        new Thread(() -> {
            String err = "";
            int ok = 0;
            try {
                getContentResolver().takePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {
            }
            try {
                LocalBookImporter.importTree(getApplicationContext(), treeUri);
                ok = 1;
            } catch (Exception e) {
                err = e.getMessage();
            }
            final int fOk = ok;
            final String fErr = err;
            runOnUiThread(() -> {
                setImporting(false);
                ToastUtil.show(ReadingShelfActivity.this,
                        fOk > 0 ? "导入成功" : "导入失败：" + fErr, Toast.LENGTH_LONG);
                refresh();
            });
        }).start();
    }

    private void removeRecord(ReadingStore.Record r) {
        // 按记录本体删：本地书的键是 local|localId，三段拼法删不掉
        ReadingStore.remove(r);
        com.fongmi.android.tv.read.ReadingBookmarkStore.removeBook(r.key());
        // 定案：长按只是移出书架；本地书文件留在私有目录，不删（改口：2026-10-18）
        ToastUtil.show(ReadingShelfActivity.this, "已从书架移除",
                Toast.LENGTH_SHORT);
        refresh();
    }

    /**
     * 听书续播：按记录重建单集专辑（与阅读续读同口径：章节表缺失时单章模式）。
     * AudioBookActivity 进页后按 AUDIO 记录自动恢复位置。
     */
    private void openAudioRecord(ReadingStore.Record r) {
        try {
            LegadoAudio.AudioAlbum a = new LegadoAudio.AudioAlbum();
            a.legadoKey = r.legadoKey == null ? "" : r.legadoKey;
            // 海阔电台记录：legadoKey 是 "hk:<规则名>" 身份键，专辑必须带回
            // hkRule/hkLine 才走得通海阔解析（与 ReadingRouter.openAudioRecord
            // 同口径）。此前本处漏带：续播重建的电台专辑丢了海阔身份、被
            // 普通页当成无名书源专辑按 legadoKey 找 Legado 源——来源身份
            // 就是在这一层被认丢的（2026-10-30 纠偏）。
            if (a.legadoKey.startsWith("hk:")) {
                a.hkRule = a.legadoKey.substring(3);
                a.hkLine = r.playFlag == null ? "" : r.playFlag;
            }
            a.sourceName = r.sourceName == null ? "" : r.sourceName;
            a.title = r.bookTitle == null ? "" : r.bookTitle;
            a.coverUrl = r.coverUrl == null ? "" : r.coverUrl;
            a.chapters.add(new LegadoAudio.Chapter(
                    r.chapterName == null ? "" : r.chapterName,
                    r.chapterUrl == null ? "" : r.chapterUrl));
            a.index = 0;
            AudioBookActivity.start(this, LegadoAudio.put(a));
        } catch (Throwable t) {
            ToastUtil.show(this, "打开失败", Toast.LENGTH_SHORT);
        }
    }

    /**
     * 朗读续播：按记录重建最小朗读任务（与听书续播同口径：章节表缺失时单章模式）。
     * TtsReadActivity 进页后按同书 TTS 记录自动恢复章节/段。
     */
    private void openTtsRecord(ReadingStore.Record r) {
        try {
            TtsJob job = new TtsJob();
            job.bookTitle = r.bookTitle == null ? "" : r.bookTitle;
            job.coverUrl = r.coverUrl == null ? "" : r.coverUrl;
            job.sourceName = r.sourceName == null ? "" : r.sourceName;
            job.siteKey = r.siteKey == null ? "" : r.siteKey;
            job.legadoKey = r.legadoKey == null ? "" : r.legadoKey;
            job.ruleTitle = r.ruleTitle == null ? "" : r.ruleTitle;
            job.localId = r.localId == null ? "" : r.localId;
            job.inlineText = r.content == null ? "" : r.content;
            job.chapters.add(new ReadingSession.Chapter(
                    r.chapterName == null ? "" : r.chapterName,
                    r.chapterUrl == null ? "" : r.chapterUrl));
            job.startChapter = 0;
            job.startFraction = 0;
            job.voiceId = EdgeVoiceCatalog.defaultVoice();
            job.rate = 1.0f;
            TtsReadActivity.start(this, TtsJob.put(job));
        } catch (Throwable t) {
            ToastUtil.show(this, "打开失败", Toast.LENGTH_SHORT);
        }
    }

    private String fmtMs(int ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private int dp(int v) {
        return ReadingSkin.dp(this, v);
    }
}
