package com.fongmi.android.tv.hongguo.book;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.hongguo.model.HgContinuation;
import com.fongmi.android.tv.legado.HgBookBridge;
import com.fongmi.android.tv.legado.LegadoPipeline;
import com.fongmi.android.tv.legado.LegadoSource;
import com.fongmi.android.tv.legado.LegadoStore;
import com.fongmi.android.tv.read.HgBookSeeder;
import com.fongmi.android.tv.ui.activity.ReadingSkin;
import com.fongmi.android.tv.ui.activity.SearchTocActivity;
import com.fongmi.android.tv.utils.ImgUtil;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import com.fongmi.android.tv.R;

/**
 * 红果书城浏览页（内置小说/听书源「点进去」的面）：频道推荐流（cell/change 续拉）
 * ＋分类筹（频道回包里的分类入口 → landing）＋页内搜索；点书进现成的目录页
 * （SearchTocActivity），后面读/听全走阅读管线，不另造一套。
 */
public class HgBookBrowseActivity extends AppCompatActivity {

    private static final String EXTRA_AUDIO = "hg_book_audio";

    public static void start(Activity activity, boolean audio) {
        Intent i = new Intent(activity, HgBookBrowseActivity.class);
        i.putExtra(EXTRA_AUDIO, audio);
        activity.startActivity(i);
    }

    private static final int MODE_FEED = 0;
    private static final int MODE_CATEGORY = 1;
    private static final int MODE_SEARCH = 2;

    private boolean audio;
    private final HgBookClient client = new HgBookClient();
    private final List<HgBookClient.Entry> entries = new ArrayList<>();
    private BookAdapter adapter;
    private TextView status;
    private LinearLayout chipsBox;
    private volatile boolean destroyed;
    private boolean loading;
    private int mode = MODE_FEED;
    private HgContinuation cont;
    private boolean feedHasMore;
    private String categoryId = "";
    private int listOffset;
    private String keyword = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audio = getIntent().getBooleanExtra(EXTRA_AUDIO, false);
        // 种源触发面补本页（D-19）：此前书城页能开的前提是先路过任一
        // 阅读面种过源，没种过时点书必走 D-17 的无声死路——本页进门
        // 先自种（已存在不重复，朋友版无实现自动不种）
        HgBookSeeder.ensureSeeded();
        // 官方书城是浅色面（skin_color_bg_fa_light #FAFAFA），系统栏图标随之切深色
        ReadingSkin.enableEdgeToEdge(this, false);
        buildUi();
        loadFeedFirst();
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
        super.onDestroy();
    }

    private int dp(int v) {
        return ReadingSkin.dp(this, v);
    }

    // 官方书城口径（包 7.2.5.32 skin 色板）：页底 #FAFAFA、题黑、副字黑 40%
    private static final int BOOK_BG = 0xFFFAFAFA;
    private static final int BOOK_TEXT = 0xFF000000;
    private static final int BOOK_SUB = 0x66000000;
    private static final int BOOK_BRAND = 0xFFFA6725;

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BOOK_BG);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(8), dp(12), dp(4));
        ReadingSkin.applyTopInset(topBar, this, 8);
        TextView title = new TextView(this);
        title.setText(audio ? HgBookBridge.NAME_AUDIO : HgBookBridge.NAME_NOVEL);
        title.setTextSize(20);
        title.getPaint().setFakeBoldText(true);
        title.setTextColor(BOOK_TEXT);
        topBar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(topBar);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(16), dp(6), dp(16), dp(6));
        // 搜索栏（官方 skin_bg_search_light：36dp 高、17dp 圆角、#08000000 底，
        // 左放大镜矢量图 ic_hg_search_gray＝官方图标色 #4D000000，不用字形）
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackgroundResource(R.drawable.bg_hg_book_searchbar);
        searchBar.setPadding(dp(12), 0, dp(8), 0);
        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageResource(R.drawable.ic_hg_search_gray);
        searchIcon.setContentDescription(null);
        searchBar.addView(searchIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        EditText input = new EditText(this);
        input.setHint(audio ? "搜听书" : "搜小说");
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setTextColor(0xB3000000);
        input.setHintTextColor(BOOK_SUB);
        input.setBackground(null);
        input.setPadding(dp(6), 0, dp(4), 0);
        searchBar.addView(input, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        searchRow.addView(searchBar, new LinearLayout.LayoutParams(
                0, dp(36), 1));
        TextView go = new TextView(this);
        go.setText("搜索");
        go.setTextSize(15);
        go.getPaint().setFakeBoldText(true);
        go.setTextColor(BOOK_BRAND);
        go.setPadding(dp(14), dp(6), dp(4), dp(6));
        go.setOnClickListener(v -> {
            String kw = input.getText().toString().trim();
            if (!kw.isEmpty()) loadSearch(kw);
        });
        searchRow.addView(go);
        root.addView(searchRow);

        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipsBox = new LinearLayout(this);
        chipsBox.setOrientation(LinearLayout.HORIZONTAL);
        chipsBox.setPadding(dp(16), dp(4), dp(16), dp(4));
        chipScroll.addView(chipsBox);
        root.addView(chipScroll);

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(BOOK_SUB);
        status.setPadding(dp(20), dp(8), dp(20), dp(4));
        status.setText("加载中…");
        root.addView(status);

        ListView list = new ListView(this);
        // 行间发丝分隔（官方 gray_08_light #14000000），代原先的无分隔全平
        list.setDivider(new android.graphics.drawable.ColorDrawable(0x14000000));
        list.setDividerHeight(1);
        adapter = new BookAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < entries.size()) openBook(entries.get(position));
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
                if (loading || totalItemCount == 0) return;
                if (firstVisibleItem + visibleItemCount >= totalItemCount - 3) loadMore();
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    // ---------- 数据 ----------

    private void loadFeedFirst() {
        mode = MODE_FEED;
        cont = null;
        feedHasMore = false;
        entries.clear();
        adapter.notifyDataSetChanged();
        setStatus("加载中…");
        loading = true;
        new Thread(() -> {
            try {
                HgBookClient.Page page = client.channel(audio, null);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    loading = false;
                    entries.addAll(page.entries);
                    cont = page.next;
                    feedHasMore = page.hasMore && cont != null;
                    buildChips(page.categories);
                    adapter.notifyDataSetChanged();
                    setStatus(entries.isEmpty() ? "暂时没拉到书，再下拉试试" : "");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    loading = false;
                    setStatus("书单没拉下来：" + msg(e));
                });
            }
        }, "HgBookFeed").start();
    }

    private void loadSearch(String kw) {
        mode = MODE_SEARCH;
        keyword = kw;
        listOffset = 0;
        entries.clear();
        adapter.notifyDataSetChanged();
        setStatus("搜索中…");
        loading = true;
        new Thread(() -> {
            try {
                List<HgBookClient.Entry> found = client.search(audio, kw, 0);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    loading = false;
                    entries.addAll(found);
                    listOffset = found.size();
                    adapter.notifyDataSetChanged();
                    setStatus(found.isEmpty() ? "没搜到相关的书" : "");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    loading = false;
                    setStatus("搜索失败：" + msg(e));
                });
            }
        }, "HgBookSearch").start();
    }

    private void loadCategory(HgBookClient.Category c) {
        mode = MODE_CATEGORY;
        categoryId = c.id;
        listOffset = 0;
        entries.clear();
        adapter.notifyDataSetChanged();
        setStatus("加载「" + c.name + "」…");
        loading = true;
        new Thread(() -> {
            try {
                List<HgBookClient.Entry> found = client.categoryLanding(c.id, 0);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    loading = false;
                    entries.addAll(found);
                    listOffset = found.size();
                    adapter.notifyDataSetChanged();
                    setStatus(found.isEmpty() ? "这个分类暂时没拉到书" : "");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    loading = false;
                    setStatus("分类没拉下来：" + msg(e));
                });
            }
        }, "HgBookCategory").start();
    }

    private void loadMore() {
        if (mode == MODE_FEED) {
            if (!feedHasMore || cont == null) return;
            loading = true;
            final HgContinuation use = cont;
            new Thread(() -> {
                try {
                    HgBookClient.Page page = client.channel(audio, use);
                    runOnUiThread(() -> {
                        if (destroyed) return;
                        loading = false;
                        entries.addAll(page.entries);
                        cont = page.next;
                        feedHasMore = page.hasMore && page.next != null;
                        adapter.notifyDataSetChanged();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (!destroyed) loading = false;
                    });
                }
            }, "HgBookMore").start();
        } else if (mode == MODE_SEARCH || mode == MODE_CATEGORY) {
            loading = true;
            final int offset = listOffset;
            new Thread(() -> {
                try {
                    List<HgBookClient.Entry> found = mode == MODE_SEARCH
                            ? client.search(audio, keyword, offset)
                            : client.categoryLanding(categoryId, offset);
                    runOnUiThread(() -> {
                        if (destroyed) return;
                        loading = false;
                        entries.addAll(found);
                        listOffset += found.size();
                        adapter.notifyDataSetChanged();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (!destroyed) loading = false;
                    });
                }
            }, "HgBookMore").start();
        }
    }

    private void buildChips(List<HgBookClient.Category> categories) {
        chipsBox.removeAllViews();
        TextView recommend = chip("推荐", mode == MODE_FEED);
        recommend.setOnClickListener(v -> loadFeedFirst());
        chipsBox.addView(recommend);
        for (HgBookClient.Category c : categories) {
            TextView chip = chip(c.name, false);
            chip.setOnClickListener(v -> loadCategory(c));
            chipsBox.addView(chip);
        }
    }

    private TextView chip(String text, boolean active) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        // 分类筹（官方 recommend tag 口径：4dp 圆角 #08000000 底）；选中＝品牌橙
        // #FA6725 粗体（代原阅读青绿 #45C08A），未选＝黑 70%（search_word 口径）
        tv.setBackgroundResource(R.drawable.bg_hg_book_chip);
        tv.setTextColor(active ? BOOK_BRAND : 0xB3000000);
        tv.getPaint().setFakeBoldText(active);
        tv.setPadding(dp(10), dp(5), dp(10), dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        tv.setLayoutParams(lp);
        return tv;
    }

    /** 按源址找已入库的哨兵源 JSON（与小说详情页 findNovelSourceJson 同法）。 */
    private String findSourceJson() {
        String want = audio ? HgBookBridge.URL_AUDIO : HgBookBridge.URL_NOVEL;
        for (LegadoStore.Entry en : LegadoStore.all()) {
            try {
                LegadoSource s = LegadoStore.parse(en.json);
                if (s != null && want.equals(s.bookSourceUrl)) return en.json;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void openBook(HgBookClient.Entry e) {
        String sourceJson = findSourceJson();
        if (sourceJson == null) {
            // 兜底种源（D-17，与小说详情页同口径）：此前找不到源直接
            // 静默 return、点书毫无反应；种完再找一次，仍没有才出声
            HgBookSeeder.ensureSeeded();
            sourceJson = findSourceJson();
        }
        if (sourceJson == null) {
            Toast.makeText(this, "书源还没准备好，稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        LegadoPipeline.Book b = new LegadoPipeline.Book();
        b.name = e.title;
        b.author = e.author;
        b.coverUrl = e.coverUrl;
        b.bookUrl = HgBookBridge.bookUrl(e.bookId);
        SearchTocActivity.start(this,
                audio ? LegadoSource.TYPE_AUDIO : LegadoSource.TYPE_TEXT,
                sourceJson, new Gson().toJson(b));
    }

    private void setStatus(String text) {
        status.setText(text == null ? "" : text);
        status.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static String msg(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? "网络不通或接口异常" : m;
    }

    // ---------- 列表 ----------

    private class BookAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public Object getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            ImageView cover;
            TextView title;
            TextView sub;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                cover = (ImageView) row.getChildAt(0);
                LinearLayout texts = (LinearLayout) row.getChildAt(1);
                title = (TextView) texts.getChildAt(0);
                sub = (TextView) texts.getChildAt(1);
            } else {
                row = new LinearLayout(HgBookBrowseActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                // 行内距＝官方列表行节奏（左右 16dp／上下 12dp）
                row.setPadding(dp(16), dp(12), dp(16), dp(12));
                cover = new ImageView(HgBookBrowseActivity.this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                // 封面圆角裁剪（既有 bg_hg_cover_r6），代原先的直角贴图
                cover.setBackgroundResource(R.drawable.bg_hg_cover_r6);
                cover.setClipToOutline(true);
                row.addView(cover, new LinearLayout.LayoutParams(dp(46), dp(62)));
                LinearLayout texts = new LinearLayout(HgBookBrowseActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                tlp.leftMargin = dp(12);
                texts.setLayoutParams(tlp);
                title = new TextView(HgBookBrowseActivity.this);
                title.setTextSize(16);
                title.setTextColor(BOOK_TEXT);
                title.getPaint().setFakeBoldText(true);
                title.setMaxLines(2);
                texts.addView(title);
                sub = new TextView(HgBookBrowseActivity.this);
                sub.setTextSize(12.5f);
                sub.setTextColor(BOOK_SUB);
                sub.setPadding(0, dp(4), 0, 0);
                texts.addView(sub);
                row.addView(texts);
            }
            HgBookClient.Entry e = entries.get(position);
            title.setText(e.title);
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(e.author)) sb.append(e.author);
            if (e.episodeCount > 0) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append("共").append(e.episodeCount).append(audio ? "集" : "章");
            }
            sub.setText(sb.toString());
            if (!TextUtils.isEmpty(e.coverUrl)) {
                ImgUtil.load(e.title, e.coverUrl, cover, true);
            } else {
                cover.setImageDrawable(null);
            }
            return row;
        }
    }
}
