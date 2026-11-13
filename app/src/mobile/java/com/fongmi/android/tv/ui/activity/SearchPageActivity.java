package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.legado.LegadoPipeline;
import com.fongmi.android.tv.legado.LegadoSource;
import com.fongmi.android.tv.read.LegadoSearchFlow;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.read.ReadDialogs;
import com.fongmi.android.tv.read.SearchOrchestrator;
import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.fongmi.android.tv.R;

/**
 * 搜书页（1.3.50 起搜书整条链的唯一呈现，不许再套弹窗）：
 * 顶上一个搜框（输入框＋搜索钮，回车也触发，进来聚焦弹键盘），作用域跟进来时的页签走；
 * 搜框下一排源片（照音乐搜索的分源）：「全部」＋一源一片、按本页类型各列各的，
 * 选择按类型记住；「全部」是原混搜（多源并行、谁先回来谁先添、不翻页），
 * 定住单个源则只搜它一个、滑到底自动翻页（空页/整页重复/失败即止）；
 * 结果就在本页列表里，一行一本（左封面＋右书名/作者（源名））；
 * 搜索中状态只在列表顶上一行字，不转圈弹窗、不 Toast；
 * 全部回来或 25 秒总时限到点收尾，失败的源在列表末尾点名一行，一条没有就一行「没有搜到」。
 * 点一本进目录页（SearchTocActivity）。返回走系统返回。
 */
public class SearchPageActivity extends AppCompatActivity {

    private static final String EXTRA_TYPE = "type";

    public static void start(Activity activity, int bookSourceType) {
        Intent i = new Intent(activity, SearchPageActivity.class);
        i.putExtra(EXTRA_TYPE, bookSourceType);
        activity.startActivity(i);
    }

    /** 分源选择按类型各记一份：值是源的 bookSourceUrl，空＝「全部」混搜。 */
    private static final String PREF_SRC_PREFIX = "legado_search_src_";

    private int type;
    private EditText et;
    private TextView status;
    private LinearLayout resultsBox;
    private ScrollView sv;
    private HorizontalScrollView chipsScroll;
    private LinearLayout chipsBox;
    /** 源片名单（与 chipsBox 子件同序）：第 0 片是「全部」，其后一源一片。 */
    private final List<LegadoSearchFlow.Src> chipSrcs = new ArrayList<>();
    /** 当前选中的源 URL，空串＝「全部」。 */
    private String selectedUrl = "";

    /** 页已关：所有后台回调一律丢弃，不许往已关的页里塞东西。 */
    private volatile boolean destroyed;
    /** 搜索代数：重开一轮就加一，旧轮的回调一律丢弃。 */
    private volatile int searchGen;

    private SearchOrchestrator orchestrator;
    private int bookCount;
    private int total;
    private int returned;

    // ===== 单源翻页状态（只在定住单个源时用；混搜不走这套） =====
    private ExecutorService pagePool;
    /** 有一页正在要：防滚动连发。只在 UI 线程读写（投递前置位、回调里清除）。 */
    private boolean paging;
    /** 已到底：空页或整页都是已有的书，不再要下一页。 */
    private boolean noMore;
    private int nextPage;
    private String curKey;
    private LegadoSearchFlow.Src curSrc;
    /** 本轮已上屏的 bookUrl 集（只在 UI 线程读写）：整页重复＝源不认页码，判到底。 */
    private final Set<String> seenUrls = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        type = getIntent().getIntExtra(EXTRA_TYPE, LegadoSource.TYPE_TEXT);
        ReadingSkin.enableEdgeToEdge(this, true);
        buildUi();
        et.requestFocus();
        et.post(() -> {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
        });

        // 页面进出统一渐入渐出（与 BaseActivity 一致）
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
}

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
    }


    @Override
    protected void onDestroy() {
        destroyed = true;
        stopOrchestrator();
        stopPaging();
        super.onDestroy();
    }

    private int dp(int v) {
        return ReadingSkin.dp(this, v);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(ReadingSkin.pageBackground(this));

        // 顶栏：只留标题，不设叉、不放返回箭头，走系统返回
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(8), dp(12), dp(4));
        ReadingSkin.applyTopInset(topBar, this, 8);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = dp(4);
        titles.setLayoutParams(tlp);
        TextView title = new TextView(this);
        title.setText("搜书");
        title.setTextSize(20);
        title.getPaint().setFakeBoldText(true);
        title.setTextColor(ReadingSkin.TEXT);
        titles.addView(title);
        TextView sub = new TextView(this);
        sub.setText(type == LegadoSource.TYPE_IMAGE ? "搜漫画源"
                : type == LegadoSource.TYPE_AUDIO ? "搜听书源" : "搜小说源");
        sub.setTextSize(12);
        sub.setTextColor(ReadingSkin.SUB);
        sub.setPadding(0, dp(2), 0, 0);
        titles.addView(sub);
        topBar.addView(titles);
        root.addView(topBar);

        // 搜框行：输入框＋搜索钮
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.leftMargin = dp(16);
        rlp.rightMargin = dp(16);
        rlp.topMargin = dp(6);
        searchRow.setLayoutParams(rlp);
        et = ReadDialogs.input(this, "输入书名/作者", false);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        elp.rightMargin = dp(10);
        et.setLayoutParams(elp);
        et.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        et.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                doSearch();
                return true;
            }
            return false;
        });
        searchRow.addView(et);
        TextView go = new TextView(this);
        go.setText("搜索");
        go.setTextSize(15);
        go.getPaint().setFakeBoldText(true);
        go.setTextColor(0xFF0A2115);
        go.setGravity(Gravity.CENTER);
        go.setBackground(ReadingSkin.goldCapsule(this));
        go.setPadding(dp(22), dp(12), dp(22), dp(12));
        go.setOnClickListener(v -> doSearch());
        searchRow.addView(go);
        root.addView(searchRow);

        // 源片行：照音乐搜索的分源——「全部」＋一源一片，点哪片只搜哪个源。
        // 名单按本页类型取（小说/漫画/听书各走各的），样式走阅读域分段件。
        chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams cslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cslp.topMargin = dp(10);
        chipsScroll.setLayoutParams(cslp);
        chipsBox = new LinearLayout(this);
        chipsBox.setOrientation(LinearLayout.HORIZONTAL);
        chipsBox.setPadding(dp(16), 0, dp(16), 0);
        chipsScroll.addView(chipsBox, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(chipsScroll);

        // 状态行：搜索中/共几本/没有搜到，只在这一行字里说
        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(ReadingSkin.SUB);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(20);
        slp.rightMargin = dp(20);
        slp.topMargin = dp(12);
        slp.bottomMargin = dp(6);
        status.setLayoutParams(slp);
        root.addView(status);

        // 结果列表
        sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        resultsBox.setPadding(dp(16), 0, dp(16), dp(24));
        sv.addView(resultsBox, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // 单源翻页：滑到接近底部自动要下一页（混搜不翻页，maybeLoadMore 内把关）
        sv.setOnScrollChangeListener((v, sx, sy, ox, oy) -> maybeLoadMore());
        root.addView(sv);

        buildChips();
        setContentView(root);
    }

    /** 开搜：上一轮没跑完先停掉，结果区清掉重来。定住单源走翻页路，「全部」走原混搜。 */
    private void doSearch() {
        String key = et.getText().toString().trim();
        if (key.isEmpty()) return;
        hideKeyboard();
        stopOrchestrator();
        stopPaging();
        final int gen = ++searchGen;

        List<LegadoSearchFlow.Src> srcs = LegadoSearchFlow.enabledSources(type);
        // 记选的源还在（没删没停）就定住它；已失效回落「全部」，不许对着空源翻页
        LegadoSearchFlow.Src single = null;
        if (!selectedUrl.isEmpty()) {
            for (LegadoSearchFlow.Src s : srcs) {
                if (selectedUrl.equals(s.source.bookSourceUrl)) {
                    single = s;
                    break;
                }
            }
        }
        total = srcs.size();
        returned = 0;
        bookCount = 0;
        seenUrls.clear();
        resultsBox.removeAllViews();
        if (total == 0) {
            curSrc = null;
            curKey = null;
            status.setText("还没装这种源");
            return;
        }
        if (single != null) {
            startPagedSearch(gen, key, single);
            return;
        }
        curSrc = null;
        curKey = null;
        status.setText("搜索中…（0/" + total + "）");

        orchestrator = new SearchOrchestrator(srcs, key, new SearchOrchestrator.Listener() {
            @Override
            public void onSourceOk(int index, LegadoSource source,
                                   List<LegadoPipeline.Book> books) {
                runOnUiThread(() -> {
                    if (destroyed || gen != searchGen) return;
                    returned++;
                    if (books != null) {
                        for (LegadoPipeline.Book b : books) {
                            if (b == null) continue;
                            addBookRow(srcs.get(index), b);
                            bookCount++;
                        }
                    }
                    refreshStatus();
                });
            }

            @Override
            public void onSourceFail(int index, LegadoSource source, boolean timeout) {
                runOnUiThread(() -> {
                    if (destroyed || gen != searchGen) return;
                    returned++;
                    refreshStatus();
                });
            }

            @Override
            public void onSettled() {
                runOnUiThread(() -> {
                    if (destroyed || gen != searchGen) return;
                    List<String> fails = orchestrator == null
                            ? new ArrayList<>() : orchestrator.failNames();
                    if (!fails.isEmpty()) addFailRow(fails);
                    if (bookCount == 0 && fails.isEmpty()) {
                        status.setText("没有搜到，换个词试试。");
                    } else {
                        status.setText("共 " + bookCount + " 本"
                                + (fails.isEmpty() ? "" : "，" + fails.size() + " 个源失败"));
                    }
                });
            }
        });
        orchestrator.start();
    }

    private void refreshStatus() {
        status.setText("搜索中…（" + returned + "/" + total + "）");
    }

    private void stopOrchestrator() {
        if (orchestrator != null) {
            orchestrator.stop();
            orchestrator = null;
        }
    }

    private void stopPaging() {
        paging = false;
        if (pagePool != null) {
            pagePool.shutdownNow();
            pagePool = null;
        }
    }

    // ================= 分源（源片） =================

    /** 源片名单：按本页类型取已启用源，「全部」恒在第一片；记选的源不在名单里回落「全部」。 */
    private void buildChips() {
        chipSrcs.clear();
        chipSrcs.addAll(LegadoSearchFlow.enabledSources(type));
        selectedUrl = Prefers.getString(PREF_SRC_PREFIX + type, "");
        boolean remembered = false;
        for (LegadoSearchFlow.Src s : chipSrcs) {
            if (selectedUrl.equals(s.source.bookSourceUrl)) {
                remembered = true;
                break;
            }
        }
        if (!remembered) selectedUrl = "";
        chipsBox.removeAllViews();
        addChip("全部", -1);
        for (int i = 0; i < chipSrcs.size(); i++) {
            addChip(LegadoSearchFlow.sourceName(chipSrcs.get(i).source), i);
        }
        chipsScroll.setVisibility(chipSrcs.isEmpty() ? View.GONE : View.VISIBLE);
        styleChips();
    }

    private void addChip(String label, int srcIndex) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(13);
        chip.setSingleLine(true);
        chip.setMaxWidth(dp(220));
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setPadding(dp(14), dp(7), dp(14), dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        chip.setLayoutParams(lp);
        chip.setTag(srcIndex);
        chip.setOnClickListener(v -> selectChip(srcIndex));
        chipsBox.addView(chip);
    }

    /** 点片：按类型记下选择；输入框里有现成词就按新范围重搜，没词只切选择不惊动结果区。 */
    private void selectChip(int srcIndex) {
        String url = srcIndex < 0 ? "" : chipSrcs.get(srcIndex).source.bookSourceUrl;
        if (url.equals(selectedUrl)) return;
        selectedUrl = url;
        Prefers.put(PREF_SRC_PREFIX + type, url);
        styleChips();
        if (!et.getText().toString().trim().isEmpty()) doSearch();
    }

    /** 源片两态：选中＝青绿 tint＋亮绿字加粗，未选＝暗底灰描边＋灰字（阅读域分段件）。 */
    private void styleChips() {
        for (int i = 0; i < chipsBox.getChildCount(); i++) {
            TextView chip = (TextView) chipsBox.getChildAt(i);
            int srcIndex = (int) chip.getTag();
            String url = srcIndex < 0 ? "" : chipSrcs.get(srcIndex).source.bookSourceUrl;
            boolean sel = url.equals(selectedUrl);
            chip.setBackground(sel
                    ? ReadingSkin.segSelectedTint(this) : ReadingSkin.segContainer(this));
            chip.setTextColor(sel ? ReadingSkin.GOLD_TEXT : ReadingSkin.SUB);
            chip.getPaint().setFakeBoldText(sel);
        }
    }

    // ================= 单源翻页 =================

    /** 单源第一页：翻页状态复位，从第 1 页开始要。 */
    private void startPagedSearch(int gen, String key, LegadoSearchFlow.Src src) {
        curKey = key;
        curSrc = src;
        nextPage = 1;
        noMore = false;
        status.setText("搜索中…");
        loadNextPage(gen);
    }

    /**
     * 要下一页：代数不符/正在要/已到底直接返回。
     * 止步三条：回空页；整页的书都已上屏（源不认页码、反复回第一页）；请求失败
     * （第 1 页点名失败，后续页静默到底）。页码只在整页有新书时才递增，
     * 换词/换源/切「全部」都经 doSearch 复位，页码与结果不会串。
     */
    private void loadNextPage(int gen) {
        if (destroyed || gen != searchGen || paging || noMore || curSrc == null) return;
        paging = true;
        final int page = nextPage;
        final String key = curKey;
        final LegadoSearchFlow.Src src = curSrc;
        if (pagePool == null || pagePool.isShutdown()) {
            pagePool = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "LegadoSearchPage");
                t.setDaemon(true);
                return t;
            });
        }
        pagePool.execute(() -> {
            List<LegadoPipeline.Book> books = null;
            boolean ok = false;
            try {
                books = new LegadoPipeline(src.source).search(key, page);
                ok = true;
            } catch (Throwable ignored) {
                // 单个源坏了只记失败，不许拖垮整页
            }
            final List<LegadoPipeline.Book> got = books;
            final boolean success = ok;
            runOnUiThread(() -> {
                if (destroyed || gen != searchGen) return;
                paging = false;
                if (!success) {
                    noMore = true;
                    if (page == 1) {
                        List<String> fails = new ArrayList<>();
                        fails.add(LegadoSearchFlow.sourceName(src.source));
                        addFailRow(fails);
                        status.setText("共 0 本，1 个源失败");
                    } else {
                        status.setText("共 " + bookCount + " 本");
                    }
                    return;
                }
                int added = 0;
                if (got != null) {
                    for (LegadoPipeline.Book b : got) {
                        if (b == null) continue;
                        if (b.bookUrl != null && !b.bookUrl.isEmpty()
                                && !seenUrls.add(b.bookUrl)) continue;
                        addBookRow(src, b);
                        added++;
                    }
                }
                bookCount += added;
                if (added == 0) {
                    noMore = true;
                    status.setText(bookCount == 0
                            ? "没有搜到，换个词试试。" : "共 " + bookCount + " 本");
                } else {
                    nextPage = page + 1;
                    status.setText("共 " + bookCount + " 本");
                    // 结果没铺满一屏时没有滚动事件可等，贴完一页主动探一次底
                    resultsBox.post(() -> maybeLoadMore());
                }
            });
        });
    }

    /** 滑近底部且单源还没到底就续下一页；混搜（curSrc 为空）永不触发。 */
    private void maybeLoadMore() {
        if (destroyed || paging || noMore || curSrc == null || curKey == null) return;
        if (sv == null || resultsBox == null) return;
        View child = sv.getChildAt(0);
        if (child == null) return;
        int distance = child.getBottom() - (sv.getHeight() + sv.getScrollY());
        if (distance <= dp(160)) loadNextPage(searchGen);
    }

    /**
     * 一行一本：左封面＋右书名/作者（源名）。封面照书架装图口径：有封面
     * 装图（ImgUtil），没封面走素垫（深暖灰底＋书名暖白排字，与书架
     * plainCover 同口径）；此前只摆纯文字行、把 pipeline 已解析的
     * coverUrl 扔了（他 2026-10-28 点名：搜书/搜听书/搜漫画都该带封面，
     * 三种搜共用本页、一处修齐）。搜索结果模型没有简介字段，不编。
     */
    private void addBookRow(LegadoSearchFlow.Src src, LegadoPipeline.Book b) {
        String name = b.name == null ? "" : b.name;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));

        FrameLayout coverWrap = new FrameLayout(this);
        coverWrap.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(62)));
        ImageView cover = new ImageView(this);
        cover.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF2E2822);
        bg.setCornerRadius(dp(6));
        cover.setBackground(bg);
        ReadingSkin.roundClip(cover, this, 6);
        TextView coverTitle = new TextView(this);
        coverTitle.setTextSize(11);
        coverTitle.setTextColor(0xFFE9DFD0);
        coverTitle.setGravity(Gravity.CENTER);
        coverTitle.setPadding(dp(4), dp(4), dp(4), dp(4));
        coverTitle.setMaxLines(4);
        coverTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        coverTitle.setText(name);
        coverWrap.addView(cover);
        coverWrap.addView(coverTitle, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (b.coverUrl != null && !b.coverUrl.isEmpty()) {
            coverTitle.setVisibility(View.GONE);
            ImgUtil.load(name, b.coverUrl, cover, true);
        }
        row.addView(coverWrap);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(dp(12));
        texts.setLayoutParams(tlp);
        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(16);
        title.setTextColor(ReadingSkin.TEXT);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);
        TextView sub = new TextView(this);
        String author = b.author == null ? "" : b.author;
        sub.setText((author.isEmpty() ? "" : author + " ") + "（" + src.source.tag() + "）");
        sub.setTextSize(13);
        sub.setTextColor(ReadingSkin.SUB);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(4);
        sub.setLayoutParams(slp);
        texts.addView(sub);
        row.addView(texts);

        row.setOnClickListener(v -> {
            if (destroyed) return;
            SearchTocActivity.start(this, type, src.json, new Gson().toJson(b));
        });
        resultsBox.addView(row);
        View div = new View(this);
        div.setBackgroundColor(0x14FFFFFF);
        resultsBox.addView(div, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    /** 失败点名行：统一坠在列表末尾。 */
    private void addFailRow(List<String> failNames) {
        String msg = failNames.size() == 1
                ? "「" + failNames.get(0) + "」搜书失败，换个源试试。"
                : failNames.size() + " 个源搜书失败（"
                + android.text.TextUtils.join("、", failNames) + "），换个源试试。";
        TextView t = new TextView(this);
        t.setText(msg);
        t.setTextSize(13);
        t.setTextColor(ReadingSkin.SUB);
        t.setPadding(dp(4), dp(14), dp(4), dp(14));
        resultsBox.addView(t);
    }

    private void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && et != null) imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
    }
}
