package com.fongmi.android.tv.ui.activity;
import com.fongmi.android.tv.utils.ToastUtil;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.read.ReadingBookmarkStore;
import com.fongmi.android.tv.read.ReadingRouter;
import com.fongmi.android.tv.read.ReadingStore;
import com.fongmi.android.tv.read.ReadingUpdateChecker;
import com.fongmi.android.tv.utils.ImgUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.fongmi.android.tv.R;

/**
 * 全部小说页：书架首页「在看的小说」横滑只管接着读，书多了来这里——
 * 全量竖排、按最近读的排、带搜索。只收小说，漫画不在这里。
 * 不设叉，走系统返回。
 */
public class AllNovelsActivity extends AppCompatActivity {

    private LinearLayout listBox;
    private EditText searchBox;
    private final List<ReadingStore.Record> all = new ArrayList<>();

    private int dp(int v) {
        return ReadingSkin.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ReadingSkin.enableEdgeToEdge(this, true);
        buildUi();

        // 页面进出统一渐入渐出（与 BaseActivity 一致）
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
}

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
    }


    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(ReadingSkin.pageBackground(this));

        // 顶栏：标题；不设叉，走系统返回
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
        title.setText("全部小说");
        title.setTextSize(22);
        title.getPaint().setFakeBoldText(true);
        title.setTextColor(ReadingSkin.TEXT);
        titles.addView(title);
        TextView sub = new TextView(this);
        sub.setText("按最近读的排，长按移出书架");
        sub.setTextSize(12);
        sub.setTextColor(ReadingSkin.SUB);
        sub.setPadding(0, dp(2), 0, 0);
        titles.addView(sub);
        topBar.addView(titles);
        root.addView(topBar);

        // 搜索：按书名过滤
        searchBox = new EditText(this);
        searchBox.setHint("搜索书名");
        searchBox.setHintTextColor(ReadingSkin.FAINT);
        searchBox.setTextColor(ReadingSkin.TEXT);
        searchBox.setTextSize(15);
        searchBox.setSingleLine(true);
        searchBox.setBackground(ReadingSkin.glassCard(this, 12));
        searchBox.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(16);
        slp.rightMargin = dp(16);
        slp.topMargin = dp(4);
        slp.bottomMargin = dp(4);
        searchBox.setLayoutParams(slp);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { render(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(searchBox);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(0, dp(6), 0, dp(24));
        sv.addView(listBox, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv);

        setContentView(root);
    }

    /** 取全量小说，按最近读的排在前。 */
    private void reload() {
        all.clear();
        for (ReadingStore.Record r : ReadingStore.list()) {
            if ("NOVEL".equals(r.kind)) all.add(r);
        }
        // 本地导入的小说只落 LocalBookStore，没读过就没有 ReadingStore 记录，
        // 只读记录会漏掉未读的本地小说（书架页 D1 同病同法）：按 localId 对账
        // 补列——已有记录的以记录为准（带进度），没记录的用注册表元数据
        // 合成一张同型卡，并入后仍按 updatedAt 倒序；点击仍走 openRecord
        // 的本地分支（LocalBookSession 全表重建），不另造打开路径。
        java.util.Set<String> recordedLocal = new java.util.HashSet<>();
        for (ReadingStore.Record r : all) {
            if ("local".equals(r.origin) && !TextUtils.isEmpty(r.localId)) {
                recordedLocal.add(r.localId);
            }
        }
        for (com.fongmi.android.tv.localbook.LocalBook b
                : com.fongmi.android.tv.localbook.LocalBookStore.list()) {
            if (b == null || TextUtils.isEmpty(b.id) || recordedLocal.contains(b.id)) continue;
            // 本页只收小说：漫画本地书归书架漫画栏（与书架页 kind 映射同口径）
            if ("COMIC".equals(b.kind)) continue;
            ReadingStore.Record r = new ReadingStore.Record();
            r.kind = "NOVEL";
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
            all.add(r);
        }
        all.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        render();
    }

    private void render() {
        listBox.removeAllViews();
        String q = searchBox == null ? "" : searchBox.getText().toString().trim();
        int n = 0;
        for (ReadingStore.Record r : all) {
            if (!q.isEmpty() && (r.bookTitle == null || !r.bookTitle.contains(q))) continue;
            listBox.addView(bookCard(r));
            n++;
        }
        if (n == 0) {
            TextView tv = new TextView(this);
            tv.setText(q.isEmpty() ? "书架上还没有小说" : "没有搜到「" + q + "」");
            tv.setTextSize(15);
            tv.setTextColor(ReadingSkin.FAINT);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, dp(64), 0, dp(64));
            listBox.addView(tv);
        }
    }

    /** 书卡样式与书架小说卡同口径：封面＋书名＋看到第 X 章·全 Y＋进度条＋百分比·人话时间＋继续读。 */
    private View bookCard(ReadingStore.Record r) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(14);
        item.setPadding(pad, pad, pad, pad);
        item.setBackground(ReadingSkin.glassCard(this, 16));
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

        FrameLayout coverWrap = new FrameLayout(this);
        int cw = dp(64);
        coverWrap.setLayoutParams(new LinearLayout.LayoutParams(cw, (int) (cw * 1.32)));
        ImageView cover = new ImageView(this);
        cover.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        TextView coverTitle = new TextView(this);
        plainCover(cover, coverTitle);
        ReadingSkin.roundClip(cover, this, 8);
        coverWrap.addView(cover);
        coverWrap.addView(coverTitle, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        item.addView(coverWrap);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        mlp.leftMargin = dp(14);
        mid.setLayoutParams(mlp);

        TextView tvTitle = new TextView(this);
        tvTitle.setTextSize(16);
        tvTitle.getPaint().setFakeBoldText(true);
        tvTitle.setTextColor(ReadingSkin.TEXT);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        tvTitle.setText(r.bookTitle);
        mid.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setTextSize(13);
        tvSub.setTextColor(ReadingSkin.SUB);
        tvSub.setSingleLine(true);
        tvSub.setEllipsize(TextUtils.TruncateAt.END);
        tvSub.setPadding(0, dp(6), 0, 0);
        tvSub.setText("看到第 " + (r.chapterIndex + 1) + " 章"
                + (r.totalChapters > 0 ? " · 全 " + r.totalChapters + " 章" : ""));
        mid.addView(tvSub);

        android.widget.ProgressBar bar =
                new android.widget.ProgressBar(this, null,
                        android.R.attr.progressBarStyleHorizontal);
        bar.setProgressDrawable(ReadingSkin.goldProgressBar(this));
        bar.setMax(100);
        int pct = progressPct(r);
        bar.setProgress(Math.max(0, Math.min(100, pct)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        blp.topMargin = dp(10);
        mid.addView(bar, blp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        TextView tvProgress = new TextView(this);
        tvProgress.setTextSize(12);
        tvProgress.setTextColor(ReadingSkin.SUB);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tvProgress.setLayoutParams(plp);
        tvProgress.setText((pct >= 0 ? pct + "% · " : "")
                + humanTime(r.updatedAt) + (r.hasUpdate ? " · 有更新" : ""));
        row.addView(tvProgress);
        TextView btnGo = new TextView(this);
        btnGo.setText("继续读");
        btnGo.setTextSize(13);
        btnGo.getPaint().setFakeBoldText(true);
        btnGo.setTextColor(0xFF0A2115);
        btnGo.setBackground(ReadingSkin.goldCapsule(this));
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
            ReadingUpdateChecker.clearUpdate(r);
            ReadingRouter.openRecord(AllNovelsActivity.this, r);
        };
        item.setOnClickListener(open);
        btnGo.setOnClickListener(open);
        // 定案：长按只是移出书架；本地书文件留在私有目录，不删（全 App 统一口径）
        item.setOnLongClickListener(v -> {
            // 按记录本体删：本地书的键是 local|localId，三段拼法删不掉
            ReadingStore.remove(r);
            ReadingBookmarkStore.removeBook(r.key());
            ToastUtil.show(AllNovelsActivity.this, "已从书架移除",
                    Toast.LENGTH_SHORT);
            reload();
            return true;
        });
        return item;
    }

    /** 素封面垫（没封面时用）：深暖灰纯色底＋书名居中暖白色正经排字，不花哨。 */
    private void plainCover(ImageView cover, TextView title) {
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2E2822);
        cover.setBackground(bg);
        title.setTextSize(12);
        title.setTextColor(0xFFF5E9D6);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(8), dp(8), dp(8), dp(8));
    }

    private int progressPct(ReadingStore.Record r) {
        if (r.totalChapters > 0) {
            return Math.round((r.chapterIndex + 1) * 100f / r.totalChapters);
        }
        return -1;
    }

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
}
