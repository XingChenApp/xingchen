package com.fongmi.android.tv.hongguo.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.HongguoCommentApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 横屏评论面板（2026-10-31 他亲定形态，出处＝他发的官方截图
 * 113856）：右侧停靠框出、播放画面挤到左侧继续播——不是底部弹
 * 层、不是盖层。面板宽＝屏宽 40%（截图实测 0.4004，普查分册与
 * 官方包均无代码原值，待裁）。深底形态按截图：顶「N条评论」＋
 * 右上 ×、评论列表、底部浏览注脚行。
 * 行形态与行为复用竖屏评论单 HgCommentsSheet 的定版：头像 34
 * （无图色块字母兜底）＋名 13 粗＋正文 13＋脚行（赞 N · 展开回
 * 复，点脚行展开、回复缩进 44），字号行距逐项沿用，仅底色与字
 * 色按横屏深形态换。数据同源 HongguoCommentApi（episodeComments
 * ＋episodeReplies 分页）。
 * 能发（2026-10-31 他亲定「能登录就能发」，只看口径作废）：底部
 * 真输入行（文案照官方「有什么想法，展开说说」＋发送钮），发送
 * 走 DramaCommentPoster（带账号会话头）；未登录点输入行/发送先
 * 跳登录页（与收藏/点赞/关注/发弹幕同门）；成功本地插顶回显、
 * 计数 +1，失败留文案给明确失败态、不装成功。
 * 竖屏评论单与本件各自成件，只共享数据层与发送链。
 */
public final class HgLandCommentsPanel {

    /** 面板宽占比（截图实测 0.4004 取 0.40，官方原值未抠到、待裁）。 */
    public static final float WIDTH_RATIO = 0.40f;

    /** 宿主回调：真实 total 回写（评论钮计数签同源）＋关闭请求。 */
    public interface Host {
        void onTotal(long total);

        void onClose();
    }

    private static final int[] AV_COLORS = {
            0xFFE63A2E, 0xFFE8A13A, 0xFF3E8E6E, 0xFF4A6FD4,
            0xFF8A5FC0, 0xFFC2507A, 0xFF2E8EA6, 0xFF7A6A53};

    private static final class Row {
        final HongguoCommentApi.Comment comment;
        final boolean reply;
        boolean expanded;

        Row(HongguoCommentApi.Comment c, boolean reply) {
            comment = c;
            this.reply = reply;
        }
    }

    private final AppCompatActivity activity;
    private final Host host;
    private final ExecutorService worker;
    private final LinearLayout root;
    private final TextView title;
    private final LinearLayout footRow;
    private final android.widget.EditText input;
    private final TextView sendBtn;
    private final RecyclerView list;
    private final LandAdapter adapter;
    private String seriesId;
    private String vid;
    private boolean bound;
    private boolean destroyed;
    private boolean sending;

    public HgLandCommentsPanel(AppCompatActivity activity, Host host) {
        this.activity = activity;
        this.host = host;
        float density = activity.getResources().getDisplayMetrics().density;
        java.util.function.IntUnaryOperator dp = v -> (int) (v * density + 0.5f);
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "hg-land-comments");
            t.setDaemon(true);
            return t;
        });

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1D1D20);

        // 头：标题居中「N条评论」＋右上 ×（官方截图形态）
        FrameLayout head = new FrameLayout(activity);
        title = new TextView(activity);
        title.setText("评论");
        title.setTextColor(0xFFF2F2F4);
        title.setTextSize(15);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new FrameLayout.LayoutParams(-1, -1));
        ImageView close = new ImageView(activity);
        close.setImageResource(R.drawable.ic_drama_close);
        close.setColorFilter(Color.WHITE);
        close.setPadding(dp.applyAsInt(10), dp.applyAsInt(10),
                dp.applyAsInt(10), dp.applyAsInt(10));
        close.setOnClickListener(v -> {
            if (host != null) host.onClose();
        });
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                dp.applyAsInt(38), dp.applyAsInt(38));
        closeLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        closeLp.rightMargin = dp.applyAsInt(6);
        head.addView(close, closeLp);
        root.addView(head, new LinearLayout.LayoutParams(-1, dp.applyAsInt(52)));
        View headLine = new View(activity);
        headLine.setBackgroundColor(0x14FFFFFF);
        root.addView(headLine, new LinearLayout.LayoutParams(-1, 1));

        // 体：评论列表（行形态沿评论单，见类头注）
        list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setPadding(dp.applyAsInt(16), dp.applyAsInt(4),
                dp.applyAsInt(16), dp.applyAsInt(10));
        list.setClipToPadding(false);
        adapter = new LandAdapter(dp);
        list.setAdapter(adapter);
        list.addItemDecoration(new RecyclerView.ItemDecoration() {
            private final Paint linePaint = new Paint();

            {
                linePaint.setColor(0x14FFFFFF);
            }

            @Override
            public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
                int rows = adapter.rowsSize();
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    int pos = parent.getChildAdapterPosition(child);
                    if (pos < 0 || pos >= rows - 1) continue;
                    float y = child.getBottom() - 0.5f;
                    c.drawRect(child.getLeft(), y, child.getRight(), y + 1f, linePaint);
                }
            }
        });
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm != null && dy > 0
                        && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                    adapter.loadMore();
                }
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 脚：真输入行（官方截图同位，文案照官方「有什么想法，
        // 展开说说」＋发送钮；手势区让位由宿主 insets 喂入下边距）。
        // 登录门与竖屏评论单同口径：未登录点输入行先跳登录页（触摸
        // 在落焦前拦下，不弹键盘）、点发送同门；发送走 trySend。
        View footLine = new View(activity);
        footLine.setBackgroundColor(0x14FFFFFF);
        root.addView(footLine, new LinearLayout.LayoutParams(-1, 1));
        footRow = new LinearLayout(activity);
        footRow.setOrientation(LinearLayout.HORIZONTAL);
        footRow.setGravity(Gravity.CENTER_VERTICAL);
        footRow.setPadding(dp.applyAsInt(16), dp.applyAsInt(10),
                dp.applyAsInt(16), dp.applyAsInt(13));
        input = new android.widget.EditText(activity);
        input.setHint("有什么想法，展开说说");
        input.setTextColor(0xFFF2F2F4);
        input.setHintTextColor(0xFF8A8A93);
        input.setTextSize(13.5f);
        input.setSingleLine(true);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(0x14FFFFFF);
        inputBg.setCornerRadius(dp.applyAsInt(18));
        input.setBackground(inputBg);
        input.setPadding(dp.applyAsInt(14), dp.applyAsInt(8),
                dp.applyAsInt(14), dp.applyAsInt(8));
        input.setOnTouchListener((v, e) -> {
            if (destroyed) return false;
            if (!com.fongmi.android.tv.hongguo.api.HgAccountStore.isLoggedIn()) {
                if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                    HongguoLoginActivity.start(activity);
                }
                return true;
            }
            return false;
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                trySend();
                return true;
            }
            return false;
        });
        footRow.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        sendBtn = new TextView(activity);
        sendBtn.setText("发送");
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setTextSize(14);
        sendBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        sendBtn.setPadding(dp.applyAsInt(14), dp.applyAsInt(6),
                dp.applyAsInt(2), dp.applyAsInt(6));
        sendBtn.setOnClickListener(v -> trySend());
        footRow.addView(sendBtn, new LinearLayout.LayoutParams(-2, -2));
        root.addView(footRow);
    }

    /**
     * 发送顶层评论（登录门同 #22 口径）：未登录跳登录页；空文案在
     * 输入行报错不发；发送中禁再发（连点不重发）；成功清文案＋本
     * 地插顶回显（面板已切到别的集则不插、等回拉校准）＋计数 +1；
     * 失败保留文案、给「没发出去」明确失败态、不装成功。
     */
    private void trySend() {
        if (destroyed || sending) return;
        if (!com.fongmi.android.tv.hongguo.api.HgAccountStore.isLoggedIn()) {
            HongguoLoginActivity.start(activity);
            return;
        }
        String text = input.getText() == null ? "" : input.getText().toString().trim();
        if (text.isEmpty()) {
            input.setError("先写点什么");
            return;
        }
        if (seriesId == null || vid == null) return;
        final String sid = seriesId;
        final String v = vid;
        sending = true;
        sendBtn.setEnabled(false);
        com.fongmi.android.tv.hongguo.drama.DramaCommentPoster.publish(
                sid, v, text, new com.fongmi.android.tv.hongguo.api.HgCallback<String>() {
                    @Override
                    public void onSuccess(String commentId) {
                        activity.runOnUiThread(() -> {
                            sending = false;
                            sendBtn.setEnabled(true);
                            if (destroyed) return;
                            input.setText("");
                            if (sid.equals(seriesId) && v.equals(vid)) {
                                com.fongmi.android.tv.hongguo.api.HgAccountStore.Session s =
                                        com.fongmi.android.tv.hongguo.api.HgAccountStore.load();
                                adapter.prependMine(HongguoCommentApi.localEcho(commentId,
                                        s == null ? "" : s.name,
                                        s == null ? "" : s.avatar, text));
                                list.scrollToPosition(0);
                            }
                        });
                    }

                    @Override
                    public void onError(int code, String msg) {
                        activity.runOnUiThread(() -> {
                            sending = false;
                            sendBtn.setEnabled(true);
                            if (destroyed) return;
                            android.widget.Toast.makeText(activity,
                                    "没发出去，再试试", android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    public View view() {
        return root;
    }

    /** 手势区让位：宿主 insets 捕获的底部系统栏高喂进来补输入行下边距。 */
    public void setBottomInset(int px) {
        float density = activity.getResources().getDisplayMetrics().density;
        int base = (int) (13 * density + 0.5f);
        footRow.setPadding(footRow.getPaddingLeft(), footRow.getPaddingTop(),
                footRow.getPaddingRight(), base + px);
    }

    public boolean isBound() {
        return bound;
    }

    /**
     * 绑到某集：与当前所绑不同才整表重拉（切集跟随、重复开面板
     * 不重拉）。计数与列表与竖屏评论单同源同法。
     */
    public void bind(String sid, String v) {
        if (destroyed || sid == null || v == null) return;
        if (bound && sid.equals(seriesId) && v.equals(vid)) return;
        bound = true;
        seriesId = sid;
        vid = v;
        title.setText("评论");
        adapter.reset(sid, v);
        adapter.loadMore();
    }

    public void destroy() {
        destroyed = true;
        worker.shutdownNow();
    }

    private static GradientDrawable ovalBg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** 与评论单 CommentsAdapter 同构的分页/展开逻辑（深形态行）。 */
    private final class LandAdapter extends RecyclerView.Adapter<LandAdapter.H> {
        private final java.util.function.IntUnaryOperator dp;
        private final List<Row> rows = new ArrayList<>();
        private String sid;
        private String v;
        private String cursor;
        private boolean hasMore = true;
        private boolean loading;
        private boolean firstLoaded;
        private boolean loadFailed;
        private int generation;
        private long lastTotal = -1;

        LandAdapter(java.util.function.IntUnaryOperator dp) {
            this.dp = dp;
        }

        int rowsSize() {
            return rows.size();
        }

        void reset(String s, String vv) {
            sid = s;
            v = vv;
            generation++;
            rows.clear();
            cursor = null;
            hasMore = true;
            loading = false;
            firstLoaded = false;
            loadFailed = false;
            lastTotal = -1;
            notifyDataSetChanged();
        }

        /**
         * 池投递收口（与竖屏评论单 exec 同口径）：destroy 已关池后
         * 的迟到投递静默丢弃，RejectedExecutionException catch 兜
         * 竞态窗，不让评论面成为 P0 同型漏网。
         */
        private void exec(Runnable task) {
            if (destroyed) return;
            try {
                worker.execute(task);
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
            }
        }

        /** 自己刚发的评论本地插顶回显＋计数 +1（标题与宿主计数签同源回写，单独隔离）。 */
        void prependMine(HongguoCommentApi.Comment c) {
            rows.add(0, new Row(c, false));
            notifyItemInserted(0);
            if (lastTotal >= 0) {
                lastTotal++;
                try {
                    title.setText(HongguoFeedActivity.HgTimeTextCount.format(lastTotal)
                            + "条评论");
                    if (host != null) host.onTotal(lastTotal);
                } catch (Throwable ignored) {
                }
            }
        }

        void loadMore() {
            if (loading || !hasMore || destroyed || sid == null) return;
            loading = true;
            final int gen = generation;
            final String cur = cursor;
            final String s = sid;
            final String vv = v;
            exec(() -> {
                HongguoCommentApi.Page page = HongguoCommentApi.episodeComments(s, vv, cur);
                activity.runOnUiThread(() -> {
                    if (destroyed || gen != generation) return;
                    loading = false;
                    if (page == null) {
                        // 失败态与真空态分离（与竖屏评论单同口径）：
                        // 数据层失败一律回 null，旧写法锁死 hasMore
                        // 并上「还没有评论」——抖一次整面作废且无重
                        // 试路。改不锁 hasMore、游标不前进，状态行
                        // 给失败文案＋点按重试（账 fix168-comment-surfaces）
                        loadFailed = true;
                        notifyDataSetChanged();
                        return;
                    }
                    loadFailed = false;
                    firstLoaded = true;
                    if (page.total > 0) {
                        lastTotal = page.total;
                        // 计数回写单独隔离（与评论单同纪律）：标题与
                        // 宿主计数签哪一步抛了都不连坐入列
                        try {
                            title.setText(HongguoFeedActivity.HgTimeTextCount.format(page.total)
                                    + "条评论");
                            if (host != null) host.onTotal(page.total);
                        } catch (Throwable ignored) {
                        }
                    }
                    for (HongguoCommentApi.Comment c : page.items) rows.add(new Row(c, false));
                    cursor = page.nextCursor;
                    hasMore = page.hasMore;
                    notifyDataSetChanged();
                });
            });
        }

        private void expandReplies(Row row) {
            // P0 同型收口：面板 destroy 已 shutdownNow，迟到的展开投递
            // 撞死池即崩——与 loadMore 同口径先查 destroyed（同在主线程、
            // destroy 亦在主线程，查与投之间无竞态窗）。
            if (destroyed) return;
            row.expanded = true;
            int at = rows.indexOf(row);
            if (at >= 0) notifyItemChanged(at);
            final int gen = generation;
            final String s = sid;
            final String vv = v;
            final String cid = row.comment.id;
            exec(() -> {
                HongguoCommentApi.Page page = HongguoCommentApi.episodeReplies(s, vv, cid, "");
                activity.runOnUiThread(() -> {
                    if (destroyed || gen != generation || page == null) return;
                    int pos = rows.indexOf(row);
                    if (pos < 0) return;
                    List<Row> sub = new ArrayList<>();
                    for (HongguoCommentApi.Comment c : page.items) sub.add(new Row(c, true));
                    rows.addAll(pos + 1, sub);
                    notifyItemRangeInserted(pos + 1, sub.size());
                });
            });
        }

        class H extends RecyclerView.ViewHolder {
            final ImageView avatar;
            final TextView letter;
            final TextView name;
            final TextView text;
            final TextView meta;

            H(LinearLayout row) {
                super(row);
                FrameLayout avaBox = row.findViewWithTag("avatarBox");
                avatar = avaBox.findViewWithTag("avatar");
                letter = avaBox.findViewWithTag("letter");
                name = row.findViewWithTag("name");
                text = row.findViewWithTag("text");
                meta = row.findViewWithTag("meta");
            }
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp.applyAsInt(11), 0, dp.applyAsInt(11));
            FrameLayout avaBox = new FrameLayout(activity);
            avaBox.setTag("avatarBox");
            TextView letter = new TextView(activity);
            letter.setTag("letter");
            letter.setGravity(Gravity.CENTER);
            letter.setTextColor(Color.WHITE);
            letter.setTextSize(13);
            letter.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            avaBox.addView(letter, new FrameLayout.LayoutParams(
                    dp.applyAsInt(34), dp.applyAsInt(34)));
            ImageView avatar = new ImageView(activity);
            avatar.setTag("avatar");
            avaBox.addView(avatar, new FrameLayout.LayoutParams(
                    dp.applyAsInt(34), dp.applyAsInt(34)));
            row.addView(avaBox, new LinearLayout.LayoutParams(
                    dp.applyAsInt(34), dp.applyAsInt(34)));
            LinearLayout col = new LinearLayout(activity);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
            clp.leftMargin = dp.applyAsInt(10);
            TextView name = new TextView(activity);
            name.setTag("name");
            name.setTextColor(0xFFC9C9CF);
            name.setTextSize(13);
            name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            col.addView(name);
            TextView text = new TextView(activity);
            text.setTag("text");
            text.setTextColor(0xFFF2F2F4);
            text.setTextSize(13);
            text.setLineSpacing(0, 1.33f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.topMargin = dp.applyAsInt(2);
            col.addView(text, tlp);
            TextView meta = new TextView(activity);
            meta.setTag("meta");
            meta.setTextColor(0xFF8A8A93);
            meta.setTextSize(11.5f);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
            mlp.topMargin = dp.applyAsInt(4);
            col.addView(meta, mlp);
            row.addView(col, clp);
            return new H(row);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            if (position >= rows.size()) {
                // 底部状态行（加载/空/失败/到底），与评论单同文同
                // 口径：失败态空表时上文案并可点重试
                h.name.setText("");
                h.text.setTextColor(0xFF8A8A93);
                h.text.setText(loadFailed && rows.isEmpty() ? "没拉到评论，点这里再试"
                        : !firstLoaded ? "加载中…" : (rows.isEmpty() ? "还没有评论" : ""));
                h.meta.setText("");
                h.letter.setText("");
                h.letter.setBackground(null);
                h.avatar.setImageDrawable(null);
                h.itemView.setOnClickListener(
                        loadFailed && rows.isEmpty() ? v -> loadMore() : null);
                return;
            }
            h.itemView.setOnClickListener(null);
            Row row = rows.get(position);
            HongguoCommentApi.Comment c = row.comment;
            if (row.reply) {
                h.itemView.setPadding(dp.applyAsInt(44), dp.applyAsInt(8), 0, dp.applyAsInt(8));
            } else {
                h.itemView.setPadding(0, dp.applyAsInt(11), 0, dp.applyAsInt(11));
            }
            String author = c.author == null ? "" : c.author;
            h.letter.setText(author.isEmpty() ? "" : author.substring(0, 1));
            int seed = (author.isEmpty() ? 0 : author.charAt(0)) + author.length();
            h.letter.setBackground(ovalBg(AV_COLORS[seed % AV_COLORS.length]));
            if (c.avatar != null && !c.avatar.isEmpty()) {
                Glide.with(activity).load(c.avatar).circleCrop().into(h.avatar);
            } else {
                h.avatar.setImageDrawable(null);
            }
            h.name.setText(author);
            h.text.setTextColor(0xFFF2F2F4);
            String body = c.content == null ? "" : c.content;
            if (c.replyTo != null && !c.replyTo.isEmpty()) {
                body = "回复 @" + c.replyTo + "：" + body;
            }
            h.text.setText(body);
            StringBuilder meta = new StringBuilder();
            if (c.likes > 0) meta.append("赞 ").append(c.likes);
            if (!row.reply && c.replyCount > 0) {
                if (meta.length() > 0) meta.append("  ·  ");
                meta.append(row.expanded ? "已展开回复" : "展开 " + c.replyCount + " 条回复");
            }
            h.meta.setText(meta.toString());
            h.meta.setOnClickListener(!row.reply && c.replyCount > 0 && !row.expanded
                    ? v -> expandReplies(row) : null);
        }

        @Override
        public int getItemCount() {
            return rows.size() + 1;
        }
    }
}
