package com.fongmi.android.tv.hongguo.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.hongguo.api.HgCallback;
import com.fongmi.android.tv.hongguo.drama.DramaCellPage;
import com.fongmi.android.tv.hongguo.drama.DramaClient;
import com.fongmi.android.tv.hongguo.drama.DramaItem;
import com.fongmi.android.tv.hongguo.drama.DramaItemKind;
import com.fongmi.android.tv.hongguo.drama.DramaSearchRow;
import com.fongmi.android.tv.hongguo.drama.DramaWork;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 搜索页（2026-10-30 按官方实图与他的定版重排）：白底，返回＋浅灰搜索框
 * （带清除叉）＋「搜索」字钮；落地态只显搜索历史（本地、可清）。
 *
 * <p>签构成是他亲口定死的两签——剧集（tab_type 29，默认落此签）／
 * 视频（tab_type 30），其余签（综合/小说/用户/讨论/听书）全摘不摆；
 * 数据只接这两签既有口（{@link DramaClient#searchTab}），分页走回包
 * 服务端游标 next_offset，不按条数自算。</p>
 *
 * <p>剧集签＝两列网格海报卡（照官方搜索实图卡式）：封面左下热度标
 * （rec_text）、左上原生角标（tag_info，如「爆剧」），封面下剧名
 * （命中词橙染）、再下一排小筹（infoChips 筹＋状态筹「全N集/更新至
 * N集」＋「x万收藏」筹）；点卡直进播放页。视频签＝两列瀑布视频卡：
 * 封面（按 vertical 定横竖比）、文案、作者头像＋名字行；点卡进视频页
 * （合集走专辑路）；签内混入的正剧行回剧集卡承接（B-17：卡型与落点
 * 同型，落点仍是剧集播放页）。帖行（post_data）只出卡不落点——帖子
 * 页已不存在，不编造落点。</p>
 */
public class HongguoSearchActivity extends AppCompatActivity {

    private static final String EXTRA_KEYWORD = "hg_search_keyword";

    private static final int TAB_DRAMA = 29;
    private static final int TAB_VIDEO = 30;

    private static final int TYPE_POSTER = 1;
    private static final int TYPE_CLIP = 2;

    private static final int INK = 0xFF1C1C20;
    private static final int SUB = 0xFF8A8A91;
    private static final int KW_ORANGE = 0xFFF59A23;

    private EditText input;
    private ImageView clearBtn;
    private LinearLayout historyBox;
    private LinearLayout tabRow;
    private TextView[] tabViews;
    private int[] tabTypes;
    private RecyclerView list;
    private ResultAdapter adapter;
    private TextView stateText;

    private final List<DramaSearchRow> rows = new ArrayList<>();
    private String keyword = "";
    private int tab = TAB_DRAMA;
    /** 切签后首批结果到达要整表淡入承接（到达前为 true）。 */
    private boolean pendingContentFade;
    private boolean loading;
    private boolean hasMore;
    private int nextOffset;
    /** 销毁旗＋搜索代次（B-22）：此前回调无代次无销毁守卫——同词
     * 同签的在途旧回包与新回包先后到达会重复 append；页销毁后回包
     * 仍动 adapter。新搜/切签先 bump 代次并放下 loading（顶替在途），
     * 回调代次不符或已销毁整发丢弃（不动新代的 loading）。 */
    private volatile boolean destroyed;
    private int searchGen;
    /** 上一发回包是否失败（B-04/B-19）：失败与真空此前同文案
     * 「没有找到相关内容」且无重试——失败被装成没搜到。口径与评论
     * 面（c3781149d）同型：失败只置此旗，不拿失败当到底；空表时
     * 状态行按此旗分文案，失败文案可点重试。 */
    private boolean searchFailed;
    /** 进程重建待恢复的列表滚动状态：首批结果落数后消费一次。 */
    private Parcelable pendingLmState;

    /** 带词直达（详情页标签筹点搜的落点）：打开即填词出结果。 */
    public static void startWithKeyword(Context context, String kw) {
        Intent intent = new Intent(context, HongguoSearchActivity.class);
        if (kw != null && !kw.trim().isEmpty()) {
            intent.putExtra(EXTRA_KEYWORD, kw.trim());
        }
        HgTransition.push(context, intent);
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(0, statusBarHeight(), 0, navBarHeight());

        // 搜索栏（照官方实图）：返回＋浅灰圆框（放大镜＋输入＋清除叉）＋「搜索」
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(12), dp(6));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_drama_back);
        back.setColorFilter(0xFF222226);
        back.setPadding(dp(6), dp(8), dp(10), dp(8));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(roundBg(0xFFF2F2F4, dp(19)));
        box.setPadding(dp(12), 0, dp(8), 0);
        ImageView mag = new ImageView(this);
        mag.setImageResource(R.drawable.ic_drama_search);
        mag.setColorFilter(0xFF9A9AA2);
        box.addView(mag, new LinearLayout.LayoutParams(dp(18), dp(18)));
        input = new EditText(this);
        input.setHint("搜剧名、作者…");
        input.setHintTextColor(0xFFAAAAAA);
        input.setTextColor(INK);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackground(null);
        input.setPadding(dp(8), dp(8), dp(4), dp(8));
        input.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch();
                return true;
            }
            return false;
        });
        box.addView(input, new LinearLayout.LayoutParams(0, dp(38), 1f));
        clearBtn = new ImageView(this);
        clearBtn.setImageResource(R.drawable.ic_drama_close);
        clearBtn.setColorFilter(0xFFB9B9BE);
        clearBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        clearBtn.setVisibility(View.GONE);
        clearBtn.setOnClickListener(v -> {
            input.setText("");
            input.requestFocus();
        });
        box.addView(clearBtn, new LinearLayout.LayoutParams(dp(28), dp(28)));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                clearBtn.setVisibility(s != null && s.length() > 0
                        ? View.VISIBLE : View.GONE);
                // 词清空即回落地态（B-18）：此前搜过一次后 historyBox
                // 恒 GONE，清词/删字都不恢复，历史只能退出重进才看得到。
                if ((s == null || s.length() == 0) && !keyword.isEmpty()) {
                    restoreLanding();
                }
            }
        });
        bar.addView(box, new LinearLayout.LayoutParams(0, dp(38), 1f));

        TextView go = new TextView(this);
        go.setText("搜索");
        go.setTextColor(0xFF222226);
        go.setTextSize(15.5f);
        go.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        go.setPadding(dp(12), dp(8), dp(2), dp(8));
        go.setOnClickListener(v -> doSearch());
        bar.addView(go);
        root.addView(bar);

        // 两签（他定死：只剧集＋视频）
        tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(dp(6), 0, dp(12), dp(2));
        tabRow.setVisibility(View.GONE);
        tabTypes = new int[]{TAB_DRAMA, TAB_VIDEO};
        String[] names = {"剧集", "视频"};
        tabViews = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            final int type = tabTypes[i];
            TextView t = new TextView(this);
            t.setText(names[i]);
            t.setTextSize(16);
            t.setPadding(dp(10), dp(8), dp(10), dp(8));
            t.setOnClickListener(v -> {
                if (tab != type) {
                    tab = type;
                    styleTabs(); // 签高亮立即跟手，列表走淡交不硬切
                    pendingContentFade = true;
                    HgTransition.fadeSwap(() -> {
                        applyLayoutManager();
                        searchGen++;
                        loading = false; // 顶替旧签在途回包（B-22）
                        runSearch(true);
                    }, list, stateText);
                }
            });
            tabViews[i] = t;
            tabRow.addView(t);
        }
        root.addView(tabRow);

        // 历史
        historyBox = new LinearLayout(this);
        historyBox.setOrientation(LinearLayout.VERTICAL);
        historyBox.setPadding(dp(16), dp(10), dp(16), dp(10));
        root.addView(historyBox);
        bindHistory();

        list = new RecyclerView(this);
        list.setClipToPadding(false);
        list.setPadding(dp(7), dp(4), dp(7), dp(10));
        adapter = new ResultAdapter();
        list.setAdapter(adapter);
        list.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect,
                                       @NonNull View view, @NonNull RecyclerView parent,
                                       @NonNull RecyclerView.State state) {
                outRect.left = dp(5);
                outRect.right = dp(5);
                outRect.bottom = dp(14);
            }
        });
        applyLayoutManager();
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || !hasMore || loading) return;
                int last = lastVisible();
                if (last >= rows.size() - 4) runSearch(false);
            }
        });
        // 状态字盖在结果区正中（与剧场/作者页同改：加载中不许在最底下）
        android.widget.FrameLayout stage = new android.widget.FrameLayout(this);
        stage.addView(list, new android.widget.FrameLayout.LayoutParams(-1, -1));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        stateText = new TextView(this);
        stateText.setTextColor(0xFF9A9AA2);
        stateText.setTextSize(13.5f);
        stateText.setGravity(Gravity.CENTER);
        stateText.setPadding(0, dp(14), 0, dp(14));
        stateText.setVisibility(View.GONE);
        stage.addView(stateText, new android.widget.FrameLayout.LayoutParams(-1, -1));

        setContentView(root);

        if (savedInstanceState != null) {
            // 进程重建存档口（B-05 搜索面）：configChanges 补齐后旋转
            // 不再重建，这里兜系统回收——恢复词/签后按原词重搜（结果
            // 走服务端重拉），滚动状态在首批落数后消费。
            int savedTab = savedInstanceState.getInt("hg_tab", TAB_DRAMA);
            tab = savedTab == TAB_VIDEO ? TAB_VIDEO : TAB_DRAMA;
            String savedInput = savedInstanceState.getString("hg_input");
            if (savedInput != null && !savedInput.isEmpty()) {
                input.setText(savedInput);
                input.setSelection(savedInput.length());
            }
            pendingLmState = savedInstanceState.getParcelable("hg_scroll");
            String savedKw = savedInstanceState.getString("hg_keyword");
            if (savedKw != null && !savedKw.isEmpty()) {
                keyword = savedKw;
                historyBox.setVisibility(View.GONE);
                tabRow.setVisibility(View.VISIBLE);
                styleTabs();
                applyLayoutManager();
                runSearch(true);
                return;
            }
        }
        String preset = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_KEYWORD);
        if (preset != null && !preset.isEmpty()) {
            input.setText(preset);
            input.setSelection(preset.length());
            doSearch();
        } else {
            input.requestFocus();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("hg_input", input == null || input.getText() == null
                ? "" : input.getText().toString());
        outState.putString("hg_keyword", keyword);
        outState.putInt("hg_tab", tab);
        if (list != null && list.getLayoutManager() != null) {
            outState.putParcelable("hg_scroll", list.getLayoutManager().onSaveInstanceState());
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    private void applyLayoutManager() {
        RecyclerView.LayoutManager lm;
        if (tab == TAB_DRAMA) {
            lm = new GridLayoutManager(this, 2);
        } else {
            lm = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        }
        list.setLayoutManager(lm);
    }

    private int lastVisible() {
        RecyclerView.LayoutManager lm = list.getLayoutManager();
        if (lm instanceof StaggeredGridLayoutManager) {
            int[] pos = ((StaggeredGridLayoutManager) lm).findLastVisibleItemPositions(null);
            int max = -1;
            for (int p : pos) max = Math.max(max, p);
            return max;
        }
        if (lm instanceof GridLayoutManager) {
            return ((GridLayoutManager) lm).findLastVisibleItemPosition();
        }
        return -1;
    }

    private void styleTabs() {
        for (int i = 0; i < tabViews.length; i++) {
            boolean on = tabTypes[i] == tab;
            tabViews[i].setTextColor(on ? INK : SUB);
            tabViews[i].setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
        }
    }

    private void bindHistory() {
        historyBox.removeAllViews();
        List<String> history = HgStore.searchHistory();
        if (history.isEmpty()) {
            historyBox.setVisibility(View.GONE);
            return;
        }
        historyBox.setVisibility(View.VISIBLE);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(this);
        label.setText("搜索历史");
        label.setTextColor(0xFF55555C);
        label.setTextSize(13.5f);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView clear = new TextView(this);
        clear.setText("清空");
        clear.setTextColor(SUB);
        clear.setTextSize(12.5f);
        clear.setOnClickListener(v -> {
            HgStore.clearSearchHistory();
            bindHistory();
        });
        head.addView(clear);
        historyBox.addView(head);
        // 单筹宽上限（B-26）：筹 width=-2 无 maxWidth、父行横排不约束，
        // 超长词（或三词皆长）能把一行撑出屏宽。按一行三筹封顶——
        // 行宽＝屏宽减 historyBox 左右内距 16dp×2，再扣两道筹间距。
        int chipMaxW = Math.max(dp(80), (getResources().getDisplayMetrics().widthPixels
                - dp(32) - dp(16)) / 3);
        LinearLayout row = null;
        for (int i = 0; i < history.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.topMargin = dp(8);
                historyBox.addView(row, rlp);
            }
            final String word = history.get(i);
            TextView chip = new TextView(this);
            chip.setText(word);
            chip.setTextColor(0xFF33333A);
            chip.setTextSize(13);
            chip.setMaxLines(1);
            chip.setMaxWidth(chipMaxW);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setBackground(roundBg(0xFFF2F2F4, dp(14)));
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            chip.setOnClickListener(v -> {
                input.setText(word);
                input.setSelection(word.length());
                doSearch();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            row.addView(chip, lp);
        }
    }

    @Override
    public void finish() {
        super.finish();
        HgTransition.applyPushExit(this);
    }

    /**
     * 回落地态（B-18）：清结果/签行/状态字、历史按 HgStore 现值重绑，
     * 并 bump 代次放下在途回包（迟到的结果不许把落地态再顶掉）。
     * 触发点只有输入词变空（清除叉与逐字删空同路）。
     */
    private void restoreLanding() {
        searchGen++;
        loading = false;
        keyword = "";
        rows.clear();
        adapter.notifyDataSetChanged();
        hasMore = false;
        nextOffset = 0;
        searchFailed = false;
        pendingContentFade = false;
        stateText.setVisibility(View.GONE);
        stateText.setOnClickListener(null);
        tabRow.setVisibility(View.GONE);
        bindHistory();
    }

    private void doSearch() {
        String kw = input.getText() == null ? "" : input.getText().toString().trim();
        if (kw.isEmpty()) return;
        keyword = kw;
        HgStore.addSearchHistory(kw);
        bindHistory();
        historyBox.setVisibility(View.GONE);
        tabRow.setVisibility(View.VISIBLE);
        styleTabs();
        applyLayoutManager();
        searchGen++;
        loading = false; // 顶替同词在途的旧回包（B-22）
        runSearch(true);
    }

    private void runSearch(boolean first) {
        if (loading || keyword.isEmpty()) return;
        if (first) {
            rows.clear();
            adapter.notifyDataSetChanged();
            hasMore = false;
            nextOffset = 0;
            stateText.setText("搜索中…");
            stateText.setOnClickListener(null);
            stateText.setVisibility(View.VISIBLE);
        }
        loading = true;
        final int offset = first ? 0 : nextOffset;
        final boolean isFirst = first;
        final int gen = searchGen;
        DramaClient.searchTab(keyword, offset, tab, new HgCallback<DramaCellPage>() {
            @Override
            public void onSuccess(DramaCellPage page) {
                runOnUiThread(() -> {
                    if (destroyed || gen != searchGen) return;
                    loading = false;
                    searchFailed = false;
                    for (DramaSearchRow r : page.getSearchRows()) {
                        if (r.kind == DramaSearchRow.KIND_SERIES && r.series != null) {
                            rows.add(r);
                        } else if (tab == TAB_VIDEO && r.kind == DramaSearchRow.KIND_POST
                                && r.post != null) {
                            rows.add(r);
                        }
                    }
                    hasMore = page.isHasMore();
                    int no = parseOffset(page.getNextOffset());
                    if (no > offset) nextOffset = no;
                    else hasMore = false;
                    afterRows();
                });
            }

            @Override
            public void onError(int code, String msg) {
                runOnUiThread(() -> {
                    if (destroyed || gen != searchGen) return;
                    loading = false;
                    // 失败不当到底（B-04/B-19）：只置失败旗；首发失败
                    // 才锁续拉，续拉失败保留 hasMore——已有行时不打扰，
                    // 滚动近底自然再试（与评论面同型）。
                    searchFailed = true;
                    if (isFirst) hasMore = false;
                    afterRows();
                });
            }
        });
    }

    private int parseOffset(String s) {
        if (s == null) return -1;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void afterRows() {
        // 切签后首批结果：整表淡入承接（HgTransition 口径），不许砸显
        boolean arriveFade = pendingContentFade && !rows.isEmpty();
        pendingContentFade = false;
        if (arriveFade) list.setAlpha(0f);
        adapter.notifyDataSetChanged();
        if (pendingLmState != null && list.getLayoutManager() != null) {
            list.getLayoutManager().onRestoreInstanceState(pendingLmState);
            pendingLmState = null;
        }
        if (arriveFade) HgTransition.contentIn(list);
        stateText.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        if (!rows.isEmpty()) {
            stateText.setOnClickListener(null);
        } else if (searchFailed) {
            // 失败装空是 B-04/B-19 的断点：文案与真空分开，且可点重试
            stateText.setText("没拉到结果，点这里再试");
            stateText.setOnClickListener(v -> {
                if (!loading && !keyword.isEmpty()) runSearch(true);
            });
        } else {
            stateText.setText("没有找到相关内容");
            stateText.setOnClickListener(null);
        }
    }

    /** 计数简写（与详情页 HgTimeTextCount 同口径：万级一位小数去尾零）。 */
    private static String countText(long n) {
        if (n < 10000) return String.valueOf(n);
        double w = n / 10000.0;
        String s = w >= 100 ? String.valueOf(Math.round(w)) : String.format(
                Locale.US, "%.1f", w).replaceAll("\\.0$", "");
        return s + "万";
    }

    /** 剧名命中词橙染（官方实图形态）。 */
    private CharSequence highlighted(String title) {
        if (title == null) return "";
        if (keyword.isEmpty()) return title;
        SpannableString ss = new SpannableString(title);
        String lower = title.toLowerCase(Locale.US);
        String kw = keyword.toLowerCase(Locale.US);
        int from = 0;
        int idx;
        while ((idx = lower.indexOf(kw, from)) >= 0) {
            ss.setSpan(new ForegroundColorSpan(KW_ORANGE), idx, idx + kw.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = idx + kw.length();
        }
        return ss;
    }

    /** 按宽高比定高的封面容器（宽由父布局给，高按比例出）。 */
    private static class RatioLayout extends FrameLayout {
        float ratio = 0.75f; // 宽/高

        RatioLayout(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            int h = (int) (w / ratio);
            super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }

    private class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.H> {

        class H extends RecyclerView.ViewHolder {
            final LinearLayout card;
            final RatioLayout coverBox;
            final ImageView cover;
            final TextView heat;
            final TextView badge;
            final TextView title;
            final LinearLayout chips;
            final TextView caption;
            final LinearLayout authorRow;
            final ImageView avatar;
            final TextView authorName;

            H(LinearLayout card) {
                super(card);
                this.card = card;
                coverBox = card.findViewWithTag("coverBox");
                cover = card.findViewWithTag("cover");
                heat = card.findViewWithTag("heat");
                badge = card.findViewWithTag("badge");
                title = card.findViewWithTag("title");
                chips = card.findViewWithTag("chips");
                caption = card.findViewWithTag("caption");
                authorRow = card.findViewWithTag("authorRow");
                avatar = card.findViewWithTag("avatar");
                authorName = card.findViewWithTag("authorName");
            }
        }

        @Override
        public int getItemViewType(int position) {
            DramaSearchRow r = rows.get(position);
            if (tab == TAB_DRAMA) return TYPE_POSTER;
            // 视频签混入的正剧行（B-17）：此前整签一律短片卡——正剧
            // 被按短片卡渲染（封面比/文案位），点开却走 startSeries，
            // 卡型与落点分型不一致。正剧行回剧集卡（落点即剧集播放
            // 页），真视频（短片/合集/帖）维持短片卡。
            return isSeriesRow(r) ? TYPE_POSTER : TYPE_CLIP;
        }

        /** 行内条目是否为正剧（卡型与落点同判据，绑定分发共用）。 */
        private boolean isSeriesRow(DramaSearchRow r) {
            return r.kind == DramaSearchRow.KIND_SERIES && r.series != null
                    && r.series.kind == DramaItemKind.SERIES;
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return viewType == TYPE_POSTER ? buildPoster() : buildClip();
        }

        private TextView smallTag(String tag) {
            TextView t = new TextView(HongguoSearchActivity.this);
            t.setTag(tag);
            return t;
        }

        private H buildPoster() {
            LinearLayout card = new LinearLayout(HongguoSearchActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            RatioLayout box = new RatioLayout(HongguoSearchActivity.this);
            box.setTag("coverBox");
            box.ratio = 0.75f;
            ImageView cover = new ImageView(HongguoSearchActivity.this);
            cover.setTag("cover");
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(roundBg(0xFFF0F0F2, dp(8)));
            box.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            View shade = new View(HongguoSearchActivity.this);
            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{0x8C000000, 0x00000000});
            shade.setBackground(gd);
            FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, dp(34));
            slp.gravity = Gravity.BOTTOM;
            box.addView(shade, slp);
            TextView heat = smallTag("heat");
            heat.setTextColor(Color.WHITE);
            heat.setTextSize(10.5f);
            heat.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            heat.setShadowLayer(dp(2), 0, dp(1), 0x99000000);
            heat.setPadding(dp(7), 0, 0, dp(6));
            FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-2, -2);
            hlp.gravity = Gravity.BOTTOM | Gravity.START;
            box.addView(heat, hlp);
            TextView badge = smallTag("badge");
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(10);
            badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            badge.setBackground(roundBg(0xFFFF4D2E, dp(4)));
            badge.setPadding(dp(5), dp(2), dp(5), dp(2));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2);
            blp.gravity = Gravity.TOP | Gravity.START;
            blp.leftMargin = dp(6);
            blp.topMargin = dp(6);
            box.addView(badge, blp);
            card.addView(box, new LinearLayout.LayoutParams(-1, -2));

            TextView title = smallTag("title");
            title.setTextColor(INK);
            title.setTextSize(15);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.topMargin = dp(7);
            card.addView(title, tlp);

            LinearLayout chips = new LinearLayout(HongguoSearchActivity.this);
            chips.setTag("chips");
            chips.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
            clp.topMargin = dp(5);
            card.addView(chips, clp);

            TextView caption = smallTag("caption");
            caption.setVisibility(View.GONE);
            card.addView(caption);
            LinearLayout authorRow = new LinearLayout(HongguoSearchActivity.this);
            authorRow.setTag("authorRow");
            authorRow.setVisibility(View.GONE);
            card.addView(authorRow);
            return new H(card);
        }

        private H buildClip() {
            LinearLayout card = new LinearLayout(HongguoSearchActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            RatioLayout box = new RatioLayout(HongguoSearchActivity.this);
            box.setTag("coverBox");
            ImageView cover = new ImageView(HongguoSearchActivity.this);
            cover.setTag("cover");
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(roundBg(0xFFF0F0F2, dp(8)));
            box.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            card.addView(box, new LinearLayout.LayoutParams(-1, -2));

            TextView caption = smallTag("caption");
            caption.setTextColor(0xFF26262B);
            caption.setTextSize(14.5f);
            caption.setMaxLines(2);
            caption.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(-1, -2);
            capLp.topMargin = dp(7);
            card.addView(caption, capLp);

            LinearLayout authorRow = new LinearLayout(HongguoSearchActivity.this);
            authorRow.setTag("authorRow");
            authorRow.setOrientation(LinearLayout.HORIZONTAL);
            authorRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
            alp.topMargin = dp(6);
            ImageView avatar = new ImageView(HongguoSearchActivity.this);
            avatar.setTag("avatar");
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            authorRow.addView(avatar, new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView name = new TextView(HongguoSearchActivity.this);
            name.setTag("authorName");
            name.setTextColor(SUB);
            name.setTextSize(12.5f);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
            nlp.leftMargin = dp(6);
            authorRow.addView(name, nlp);
            card.addView(authorRow, alp);

            TextView title = smallTag("title");
            title.setVisibility(View.GONE);
            card.addView(title);
            LinearLayout chips = new LinearLayout(HongguoSearchActivity.this);
            chips.setTag("chips");
            chips.setVisibility(View.GONE);
            card.addView(chips);
            TextView heat = smallTag("heat");
            heat.setVisibility(View.GONE);
            card.addView(heat);
            TextView badge = smallTag("badge");
            badge.setVisibility(View.GONE);
            card.addView(badge);
            return new H(card);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            DramaSearchRow r = rows.get(position);
            // 绑定分发与卡型同一判据（B-17）：剧集卡只喂正剧行。
            if (getItemViewType(position) == TYPE_POSTER) bindPoster(h, r.series);
            else if (r.kind == DramaSearchRow.KIND_POST) bindPost(h, r.post);
            else bindClip(h, r.series);
        }

        private void bindPoster(H h, DramaItem item) {
            if (item == null) return;
            bindCover(h.cover, item.getCoverUrl());
            String rec = item.getRecText();
            h.heat.setText(rec == null || rec.isEmpty() ? "" : "🔥 " + rec);
            h.heat.setVisibility(rec == null || rec.isEmpty() ? View.GONE : View.VISIBLE);
            String badge = item.getBadge();
            h.badge.setText(badge == null ? "" : badge);
            h.badge.setVisibility(badge == null || badge.isEmpty()
                    ? View.GONE : View.VISIBLE);
            h.title.setText(highlighted(item.getTitle()));
            bindChips(h.chips, item);
            h.card.setOnClickListener(v -> openItem(item));
        }

        /** 小筹行：infoChips 前两枚＋状态筹＋收藏筹，单行封顶三枚。 */
        private void bindChips(LinearLayout box, DramaItem item) {
            box.removeAllViews();
            List<String> texts = new ArrayList<>();
            List<String> pool = item.infoChips != null && !item.infoChips.isEmpty()
                    ? item.infoChips : item.getTags();
            if (pool != null) {
                for (String s : pool) {
                    if (s != null && !s.isEmpty() && texts.size() < 2) texts.add(s);
                }
            }
            if (item.getEpisodeCount() > 0) {
                texts.add(item.isFinished() ? "全" + item.getEpisodeCount() + "集"
                        : "更新至" + item.getEpisodeCount() + "集");
            }
            if (item.followedCnt > 0) {
                texts.add(countText(item.followedCnt) + "收藏");
            }
            int n = Math.min(texts.size(), 3);
            for (int i = 0; i < n; i++) {
                TextView chip = new TextView(HongguoSearchActivity.this);
                chip.setText(texts.get(i));
                chip.setTextSize(10.5f);
                chip.setMaxLines(1);
                chip.setEllipsize(TextUtils.TruncateAt.END);
                if (i == 0) {
                    chip.setTextColor(0xFFD98A1F);
                    chip.setBackground(roundBg(0xFFFDF1DC, dp(4)));
                } else {
                    chip.setTextColor(0xFF77777F);
                    chip.setBackground(roundBg(0xFFF4F4F5, dp(4)));
                }
                chip.setPadding(dp(7), dp(3), dp(7), dp(3));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                if (i > 0) lp.leftMargin = dp(5);
                box.addView(chip, lp);
            }
            box.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        }

        private void bindClip(H h, DramaItem item) {
            if (item == null) return;
            h.coverBox.ratio = Boolean.FALSE.equals(item.getVertical()) ? 4f / 3f : 0.75f;
            bindCover(h.cover, item.getCoverUrl());
            h.caption.setText(item.getTitle() == null ? "" : item.getTitle());
            bindAuthor(h, item.getAuthorAvatar(), item.getAuthorName());
            h.card.setOnClickListener(v -> openItem(item));
        }

        private void bindPost(H h, DramaWork post) {
            if (post == null) return;
            h.coverBox.ratio = 4f / 3f;
            bindCover(h.cover, post.cover);
            String text = post.title;
            if ((text == null || text.isEmpty()) && post.postBody != null) {
                text = post.postBody.text;
            }
            h.caption.setText(text == null ? "" : text);
            bindAuthor(h, post.authorAvatar, post.authorName);
            // 帖子页已不存在：只出卡、不落点，不编造落点
            h.card.setOnClickListener(null);
            h.card.setClickable(false);
        }

        private void bindAuthor(H h, String avatar, String name) {
            boolean has = (name != null && !name.isEmpty())
                    || (avatar != null && !avatar.isEmpty());
            h.authorRow.setVisibility(has ? View.VISIBLE : View.GONE);
            if (!has) return;
            h.authorName.setText(name == null ? "" : name);
            if (avatar != null && !avatar.isEmpty()) {
                Glide.with(HongguoSearchActivity.this).load(avatar)
                        .circleCrop().into(h.avatar);
            } else {
                h.avatar.setImageDrawable(null);
            }
        }

        private void openItem(DramaItem item) {
            if (item == null) return;
            if (item.kind == DramaItemKind.ALBUM) {
                HongguoFeedActivity.startVideoAlbum(HongguoSearchActivity.this, item);
            } else if (item.kind == DramaItemKind.UGC_CLIP) {
                HongguoFeedActivity.startVideo(HongguoSearchActivity.this, item);
            } else if (item.getSeriesId() != null) {
                // 定稿口径：点封面直进播放页开播，不绕详情页
                HongguoFeedActivity.startSeries(HongguoSearchActivity.this,
                        item.getSeriesId(), item.getTitle(), 0);
            }
        }

        private void bindCover(ImageView iv, String url) {
            if (url == null || url.isEmpty()) {
                iv.setImageDrawable(null);
                return;
            }
            HgCoverClip.round(iv, dp(8));
            Glide.with(HongguoSearchActivity.this).load(url).into(iv);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
