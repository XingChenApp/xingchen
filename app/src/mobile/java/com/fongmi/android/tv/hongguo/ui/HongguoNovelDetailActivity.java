package com.fongmi.android.tv.hongguo.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.hongguo.api.HgApiClient;
import com.fongmi.android.tv.hongguo.api.HgCallback;
import com.fongmi.android.tv.hongguo.book.HgBookClient;
import com.fongmi.android.tv.legado.HgBookBridge;
import com.fongmi.android.tv.legado.LegadoPipeline;
import com.fongmi.android.tv.legado.LegadoSource;
import com.fongmi.android.tv.legado.LegadoStore;
import com.fongmi.android.tv.read.HgBookSeeder;
import com.fongmi.android.tv.read.LegadoSearchFlow;
import com.fongmi.android.tv.read.ReadingStore;
import com.fongmi.android.tv.ui.activity.ReadingSkin;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 红果小说详情页（搜索小说签书卡的落点）：封面/书名/作者/分类/简介＋章节目录，
 * 点章把章节数据递给现行小说阅读器（LegadoSearchFlow→LegadoReading→
 * NovelReaderActivity，只调用不混）；断点落 HgStore 小说命名空间
 * （hongguo_novel_*，与短剧历史分家）。本页只在正式版存在：朋友版由
 * strip-hongguo.sh 整目录剥离（mobile hongguo/ui）＋Manifest 登记一并删。
 * 链路实证（2026-10-30 本机探针）：搜索 tab_type=1 → detail → directory
 * → registerkey 解密正文，首/中/末章全文可读（账 hongguo-novel-open-20261030）。
 */
public class HongguoNovelDetailActivity extends AppCompatActivity {

    public static final String EXTRA_BOOK_ID = "hg_novel_book_id";
    public static final String EXTRA_TITLE = "hg_novel_title";

    private static final int BOOK_BG = 0xFFFAFAFA;
    private static final int BOOK_TEXT = 0xFF000000;
    private static final int BOOK_SUB = 0x66000000;
    private static final int BOOK_BRAND = 0xFFFA6725;

    private String bookId = "";
    private final HgBookClient client = new HgBookClient();
    private final HgApiClient api = new HgApiClient();
    private HgBookClient.Entry detail;
    private final List<HgBookClient.Chapter> chapters = new ArrayList<>();
    private volatile boolean destroyed;
    private volatile boolean opening;

    private ImageView coverView;
    private TextView titleView;
    private TextView authorView;
    private TextView metaView;
    private TextView introView;
    private TextView readBtn;
    private TextView lastReadView;
    private TextView tocCountView;
    private TextView statusView;
    private ChapterAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** 回写补刷：阅读器存进度是异步落盘，返回瞬间可能还没落——延一拍再读一次。 */
    private final Runnable markResync = () -> {
        if (destroyed) return;
        syncMarkFromReader();
        if (detail != null) bindDetail();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bookId = getIntent().getStringExtra(EXTRA_BOOK_ID);
        if (bookId == null) bookId = "";
        ReadingSkin.enableEdgeToEdge(this, false);
        buildUi();
        String hintTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (!TextUtils.isEmpty(hintTitle)) titleView.setText(hintTitle);
        if (bookId.isEmpty()) {
            setStatus("这本书的信息不完整，打不开");
        } else {
            load();
        }
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
        mainHandler.removeCallbacks(markResync);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从阅读器返回：先把阅读器侧的最新章位回写进 HgStore 断点
        //（D-01），再重绑头面（D-15 的返回刷新落点）；阅读器异步落盘
        // 可能迟到，延一拍再补刷一次
        syncMarkFromReader();
        if (detail != null) bindDetail();
        mainHandler.removeCallbacks(markResync);
        mainHandler.postDelayed(markResync, 900);
    }

    /**
     * 续读位回写口（D-01）：阅读器内翻章只写它自己的 ReadingStore
     * （键＝NOVEL|源名|书名，NovelReaderActivity.saveProgressAt），
     * HgStore 小说断点此前没有任何回写——在阅读器里读得越远、回来
     * 「上次读到」错得越多。现按同键读回：章名对位优先（阅读器存了
     * chapterName）、下标兜底，对不上就不动，不许把断点写歪。
     * 只读阅读器的公开存储，不改阅读器本体（组件分家：只调用不混）。
     */
    private void syncMarkFromReader() {
        if (bookId.isEmpty() || chapters.isEmpty()) return;
        String title = detail != null && !detail.title.isEmpty()
                ? detail.title : getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null || title.isEmpty()) return;
        ReadingStore.Record rec;
        try {
            rec = ReadingStore.find("NOVEL", readerSourceName(), title);
        } catch (Throwable t) {
            return;
        }
        if (rec == null) return;
        int target = -1;
        if (rec.chapterName != null && !rec.chapterName.isEmpty()) {
            for (int i = 0; i < chapters.size(); i++) {
                if (rec.chapterName.equals(chapters.get(i).title)) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0 && rec.chapterIndex >= 0 && rec.chapterIndex < chapters.size()) {
            target = rec.chapterIndex;
        }
        if (target < 0) return;
        HgStore.NovelMark mark = HgStore.novelMarkOf(bookId);
        if (mark == null || mark.chapterIndex != target) {
            HgStore.saveNovelMark(bookId, target, chapters.get(target).itemId);
        }
    }

    /** 建会话用的哨兵源名（与 LegadoReading.buildSession 的 source.tag() 同源）；取不到回落种子定名。 */
    private String readerSourceName() {
        String json = findNovelSourceJson();
        if (json != null) {
            LegadoSource s = LegadoStore.parse(json);
            if (s != null) return s.tag();
        }
        return HgBookBridge.NAME_NOVEL;
    }

    private int dp(int v) {
        return ReadingSkin.dp(this, v);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BOOK_BG);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(16), dp(8), dp(16), dp(12));
        ReadingSkin.applyTopInset(head, this, 8);

        LinearLayout bookRow = new LinearLayout(this);
        bookRow.setOrientation(LinearLayout.HORIZONTAL);
        coverView = new ImageView(this);
        coverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverView.setBackgroundResource(R.drawable.bg_hg_cover_r6);
        coverView.setClipToOutline(true);
        bookRow.addView(coverView, new LinearLayout.LayoutParams(dp(84), dp(112)));

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setPadding(dp(12), dp(2), 0, 0);
        titleView = new TextView(this);
        titleView.setTextSize(17);
        titleView.getPaint().setFakeBoldText(true);
        titleView.setTextColor(BOOK_TEXT);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        infoCol.addView(titleView);
        authorView = new TextView(this);
        authorView.setTextSize(13);
        authorView.setTextColor(BOOK_SUB);
        authorView.setPadding(0, dp(6), 0, 0);
        authorView.setMaxLines(1);
        authorView.setEllipsize(TextUtils.TruncateAt.END);
        infoCol.addView(authorView);
        metaView = new TextView(this);
        metaView.setTextSize(12);
        metaView.setTextColor(BOOK_SUB);
        metaView.setPadding(0, dp(4), 0, 0);
        infoCol.addView(metaView);
        bookRow.addView(infoCol, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        head.addView(bookRow);

        introView = new TextView(this);
        introView.setTextSize(13);
        introView.setTextColor(0x99000000);
        introView.setLineSpacing(0, 1.3f);
        introView.setMaxLines(5);
        introView.setEllipsize(TextUtils.TruncateAt.END);
        introView.setPadding(0, dp(12), 0, 0);
        introView.setVisibility(View.GONE);
        head.addView(introView);

        readBtn = new TextView(this);
        readBtn.setText("开始阅读");
        readBtn.setTextSize(15);
        readBtn.getPaint().setFakeBoldText(true);
        readBtn.setTextColor(0xFFFFFFFF);
        readBtn.setGravity(Gravity.CENTER);
        readBtn.setBackgroundResource(R.drawable.bg_hg_follow_btn);
        readBtn.setPadding(0, dp(11), 0, dp(11));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp(14);
        readBtn.setLayoutParams(btnLp);
        readBtn.setOnClickListener(v -> {
            HgStore.NovelMark mark = HgStore.novelMarkOf(bookId);
            openChapterAt(mark != null ? mark.chapterIndex : 0);
        });
        head.addView(readBtn);

        lastReadView = new TextView(this);
        lastReadView.setTextSize(12);
        lastReadView.setTextColor(BOOK_SUB);
        lastReadView.setGravity(Gravity.CENTER);
        lastReadView.setPadding(0, dp(8), 0, 0);
        lastReadView.setVisibility(View.GONE);
        head.addView(lastReadView);

        LinearLayout tocHead = new LinearLayout(this);
        tocHead.setOrientation(LinearLayout.HORIZONTAL);
        tocHead.setGravity(Gravity.CENTER_VERTICAL);
        tocHead.setPadding(0, dp(16), 0, dp(4));
        TextView tocTitle = new TextView(this);
        tocTitle.setText("目录");
        tocTitle.setTextSize(15);
        tocTitle.getPaint().setFakeBoldText(true);
        tocTitle.setTextColor(BOOK_TEXT);
        tocHead.addView(tocTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tocCountView = new TextView(this);
        tocCountView.setTextSize(12);
        tocCountView.setTextColor(BOOK_SUB);
        tocHead.addView(tocCountView);
        head.addView(tocHead);

        statusView = new TextView(this);
        statusView.setTextSize(13);
        statusView.setTextColor(BOOK_SUB);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(10), 0, dp(2));
        statusView.setVisibility(View.GONE);
        head.addView(statusView);
        root.addView(head);

        ListView list = new ListView(this);
        list.setBackgroundColor(BOOK_BG);
        list.setDivider(null);
        list.setDividerHeight(0);
        adapter = new ChapterAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> openChapterAt(position));
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void setStatus(String text) {
        statusView.setText(text == null ? "" : text);
        statusView.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void load() {
        setStatus("正在打开这本书…");
        new Thread(() -> {
            try {
                ensureDeviceSync();
                HgBookClient.Entry d = client.detail(bookId);
                List<HgBookClient.Chapter> toc = client.toc(bookId);
                if (!destroyed) {
                    HgStore.putNovelMeta(bookId, d.title, d.author, d.coverUrl);
                }
                runOnUiThread(() -> {
                    if (destroyed) return;
                    detail = d;
                    chapters.clear();
                    chapters.addAll(toc);
                    bindDetail();
                    setStatus(toc.isEmpty() ? "这本书暂时取不到目录" : null);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!destroyed) setStatus("加载失败：" + msg(e));
                });
            }
        }, "hg-novel-detail").start();
    }

    private void ensureDeviceSync() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        String[] err = new String[1];
        api.ensureDevice(new HgCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean ok) {
                latch.countDown();
            }

            @Override
            public void onError(int code, String message) {
                err[0] = message;
                latch.countDown();
            }
        });
        if (!latch.await(60, TimeUnit.SECONDS)) throw new Exception("设备注册超时");
        if (err[0] != null) throw new Exception(err[0]);
    }

    private void bindDetail() {
        if (detail == null) return;
        titleView.setText(detail.title);
        authorView.setText(detail.author);
        authorView.setVisibility(detail.author.isEmpty() ? View.GONE : View.VISIBLE);
        StringBuilder meta = new StringBuilder();
        if (!detail.category.isEmpty()) meta.append(detail.category);
        if (!chapters.isEmpty()) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append("共 ").append(chapters.size()).append(" 章");
        }
        if (detail.score > 0) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append("评分 ").append(trimScore(detail.score));
        }
        metaView.setText(meta.toString());
        if (!detail.intro.isEmpty()) {
            introView.setText(detail.intro);
            introView.setVisibility(View.VISIBLE);
        }
        if (!detail.coverUrl.isEmpty()) {
            ImgUtil.load(detail.title, detail.coverUrl, coverView);
        }
        tocCountView.setText(chapters.isEmpty() ? "" : "共 " + chapters.size() + " 章");
        HgStore.NovelMark mark = HgStore.novelMarkOf(bookId);
        if (mark != null && mark.chapterIndex >= 0 && mark.chapterIndex < chapters.size()) {
            readBtn.setText("继续阅读");
            lastReadView.setText("上次读到：第 " + (mark.chapterIndex + 1) + " 章 · "
                    + chapters.get(mark.chapterIndex).title);
            lastReadView.setVisibility(View.VISIBLE);
        } else {
            readBtn.setText("开始阅读");
            lastReadView.setVisibility(View.GONE);
        }
        adapter.notifyDataSetChanged();
    }

    private static String trimScore(double score) {
        return score == Math.rint(score)
                ? String.valueOf((int) score)
                : String.valueOf(score);
    }

    /**
     * 点章进阅读器：断点先落盘，再在后台把哨兵小说源的 BookInfo/目录组好，
     * 回主线程走 LegadoSearchFlow.openChapter（组件规矩：只递数据、阅读器自理）。
     */
    private void openChapterAt(int index) {
        if (destroyed || opening || chapters.isEmpty()) return;
        final int ix = Math.max(0, Math.min(index, chapters.size() - 1));
        HgStore.saveNovelMark(bookId, ix, chapters.get(ix).itemId);
        opening = true;
        setStatus("正在打开章节…");
        new Thread(() -> {
            try {
                String sourceJson = findNovelSourceJson();
                if (sourceJson == null) {
                    HgBookSeeder.ensureSeeded();
                    sourceJson = findNovelSourceJson();
                }
                if (sourceJson == null) throw new Exception("阅读器还没准备好，稍后再试");
                LegadoSource source = LegadoStore.parse(sourceJson);
                if (source == null) throw new Exception("阅读器还没准备好，稍后再试");
                LegadoPipeline.BookInfo info =
                        HgBookBridge.bookInfo(source, HgBookBridge.bookUrl(bookId));
                List<LegadoPipeline.TocChapter> toc =
                        HgBookBridge.toc(source, info.tocUrl);
                if (toc == null || toc.isEmpty()) throw new Exception("章节取不到");
                final int at = Math.min(ix, toc.size() - 1);
                runOnUiThread(() -> {
                    opening = false;
                    if (destroyed) return;
                    setStatus(null);
                    LegadoSearchFlow.openChapter(
                            HongguoNovelDetailActivity.this, source, info, toc, at);
                    // 从阅读器返回时断点行可能已变（页内自记），重绑一次头面
                    bindDetail();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    opening = false;
                    if (destroyed) return;
                    setStatus(null);
                    Toast.makeText(HongguoNovelDetailActivity.this,
                            "打开失败：" + msg(e), Toast.LENGTH_SHORT).show();
                });
            }
        }, "hg-novel-open").start();
    }

    private String findNovelSourceJson() {
        for (LegadoStore.Entry en : LegadoStore.all()) {
            try {
                LegadoSource s = LegadoStore.parse(en.json);
                if (s != null && HgBookBridge.URL_NOVEL.equals(s.bookSourceUrl)) {
                    return en.json;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String msg(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? "网络不通或接口异常" : m;
    }

    private final class ChapterAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return chapters.size();
        }

        @Override
        public Object getItem(int position) {
            return chapters.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView title;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                title = (TextView) row.getChildAt(0);
            } else {
                row = new LinearLayout(HongguoNovelDetailActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                title = new TextView(HongguoNovelDetailActivity.this);
                title.setTextSize(14);
                title.setMaxLines(1);
                title.setEllipsize(TextUtils.TruncateAt.END);
                title.setPadding(dp(16), dp(13), dp(16), dp(13));
                row.addView(title);
                View line = new View(HongguoNovelDetailActivity.this);
                line.setBackgroundColor(0x0D000000);
                row.addView(line, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
            HgStore.NovelMark mark = HgStore.novelMarkOf(bookId);
            boolean isMark = mark != null && mark.chapterIndex == position;
            title.setText(chapters.get(position).title);
            title.setTextColor(isMark ? BOOK_BRAND : 0xDE000000);
            title.getPaint().setFakeBoldText(isMark);
            return row;
        }
    }
}
