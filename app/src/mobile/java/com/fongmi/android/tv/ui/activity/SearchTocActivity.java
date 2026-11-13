package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.legado.LegadoException;
import com.fongmi.android.tv.legado.LegadoPipeline;
import com.fongmi.android.tv.legado.LegadoSource;
import com.fongmi.android.tv.legado.LegadoStore;
import com.fongmi.android.tv.read.LegadoSearchFlow;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import com.fongmi.android.tv.R;

/**
 * 目录页（搜书页点一本进来）：一行一章/集，不许再弹 options 列表。
 * 进来先一行「拉取目录中…」，拉到了渲染章节，拉不到就一行人话。
 * 点章进阅读/听书（LegadoSearchFlow.openChapter，现有逻辑复用）。
 * 返回走系统返回；页关了后台回调一律丢弃。
 */
public class SearchTocActivity extends AppCompatActivity {

    private static final String EXTRA_TYPE = "type";
    private static final String EXTRA_SOURCE = "source";
    private static final String EXTRA_BOOK = "book";

    public static void start(Activity activity, int bookSourceType,
                            String sourceJson, String bookJson) {
        Intent i = new Intent(activity, SearchTocActivity.class);
        i.putExtra(EXTRA_TYPE, bookSourceType);
        i.putExtra(EXTRA_SOURCE, sourceJson);
        i.putExtra(EXTRA_BOOK, bookJson);
        activity.startActivity(i);
    }

    private int type;
    private LegadoSource source;
    private TextView title;
    private TextView status;
    private LinearLayout box;
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        type = getIntent().getIntExtra(EXTRA_TYPE, LegadoSource.TYPE_TEXT);
        source = LegadoStore.parse(getIntent().getStringExtra(EXTRA_SOURCE));
        LegadoPipeline.Book book = new Gson().fromJson(
                getIntent().getStringExtra(EXTRA_BOOK), LegadoPipeline.Book.class);
        if (source == null || book == null) {
            finish();
            return;
        }
        ReadingSkin.enableEdgeToEdge(this, true);
        buildUi(book);
        fetchToc(book);

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
        super.onDestroy();
    }

    private int dp(int v) {
        return ReadingSkin.dp(this, v);
    }

    private void buildUi(LegadoPipeline.Book book) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(ReadingSkin.pageBackground(this));

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
        title = new TextView(this);
        title.setText(book.name == null ? "" : book.name);
        title.setTextSize(20);
        title.getPaint().setFakeBoldText(true);
        title.setTextColor(ReadingSkin.TEXT);
        titles.addView(title);
        TextView sub = new TextView(this);
        sub.setText("目录 · " + LegadoSearchFlow.sourceName(source));
        sub.setTextSize(12);
        sub.setTextColor(ReadingSkin.SUB);
        sub.setPadding(0, dp(2), 0, 0);
        titles.addView(sub);
        topBar.addView(titles);
        root.addView(topBar);

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
        status.setText("拉取目录中…");
        root.addView(status);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), 0, dp(16), dp(24));
        sv.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv);

        setContentView(root);
    }

    /** 拉目录 → 章节列表（原 LegadoSearchFlow.openBook 前半段，原样搬迁；弹窗改成了页内行）。 */
    private void fetchToc(LegadoPipeline.Book book) {
        new Thread(() -> {
            LegadoPipeline.BookInfo info = null;
            List<LegadoPipeline.TocChapter> chapters = new ArrayList<>();
            String err = null;
            try {
                LegadoPipeline p = new LegadoPipeline(source);
                info = p.bookInfo(book.bookUrl);
                if (info.name.isEmpty()) info.name = book.name == null ? "" : book.name;
                if (info.coverUrl.isEmpty()) info.coverUrl = book.coverUrl == null ? "" : book.coverUrl;
                String tocUrl = info.tocUrl.isEmpty() ? book.bookUrl : info.tocUrl;
                chapters = p.toc(tocUrl);
                if (chapters.isEmpty()) throw new LegadoException("目录为空");
            } catch (Throwable t) {
                android.util.Log.e("Legado", "目录拉取失败 raw=" + t, t);
                err = "这本书的目录拉不下来。多半是源地址失效或者网络不通。换个源再试试。";
            }
            final LegadoPipeline.BookInfo fi = info;
            final List<LegadoPipeline.TocChapter> fc = chapters;
            final String fe = err;
            runOnUiThread(() -> {
                if (destroyed) return;
                if (fe != null) {
                    status.setText(fe);
                    return;
                }
                renderChapters(fi, fc);
            });
        }, "LegadoToc").start();
    }

    private void renderChapters(LegadoPipeline.BookInfo info,
                                List<LegadoPipeline.TocChapter> chapters) {
        String unit = type == LegadoSource.TYPE_AUDIO ? "集" : "章";
        title.setText((info.name == null || info.name.isEmpty() ? title.getText() : info.name)
                + "（" + chapters.size() + unit + "）");
        status.setText("共 " + chapters.size() + unit);
        box.removeAllViews();
        for (int i = 0; i < chapters.size(); i++) {
            final int which = i;
            LegadoPipeline.TocChapter c = chapters.get(i);
            TextView row = new TextView(this);
            row.setText(c == null || c.name == null ? "" : c.name);
            row.setTextSize(16);
            row.setTextColor(ReadingSkin.TEXT);
            row.setPadding(dp(4), dp(12), dp(4), dp(12));
            row.setOnClickListener(v -> {
                if (destroyed) return;
                LegadoSearchFlow.openChapter(this, source, info, chapters, which);
            });
            box.addView(row);
            View div = new View(this);
            div.setBackgroundColor(0x14FFFFFF);
            box.addView(div, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
    }
}
