package com.fongmi.android.tv.hongguo.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 评论单（打回补修重落，定稿浅色 .sheet）：白底顶圆 22＋grab，
 * 头＝「评论」＋计数 span＋关闭圆钮，体＝草稿 .cmt 行（头像 34、
 * 名 13 粗 ink、正文 13 #33333A、脚行 11.5 sub、行下 1px line
 * 分隔），脚＝真输入行（#FBFAF8 底＋顶线）。与选集单/合集单同
 * 口径，旧深色面整件弃用（账 hongguo-page-redo 补修②）。
 * 能发（2026-10-31 他亲定「能登录就能发」，只看定版作废）：本
 * 集评论列表＋回复展开＋翻页＋发顶层评论——输入行文案照官方
 * 「有什么想法，展开说说」＋发送钮，发送走 DramaCommentPoster
 * （带账号会话头），未登录点输入行/发送先跳登录页（与收藏/点
 * 赞/关注/发弹幕同门），成功本地插顶回显＋计数 +1、失败给明确
 * 失败态。读数据走 HongguoCommentApi（匿名读口），真实 total
 * 经 OnTotal 回写宿主（D6）。
 * 日期行（T-06，他亲裁「加」）：Comment.created 已验为秒级
 * create_timestamp，脚行首段按官方同位格式渲染日期（HgCommentMeta）；
 * 属地段无已验字段（咱家读口回包全键普查无此字段），不渲染、
 * 不留空位（账 t06-comment-date-region-mentions-20261031）。
 * 表情钮（T-06）：真功能，HgEmojiPanel 点格插光标处；@ 钮查明
 * 无可用功能路（发送链无提及字段、读侧 text_exts 无真人提及
 * 样本、选人目录无已验接口），按「不摆占位」不落（同账）。
 */
public final class HgCommentsSheet {

    private HgCommentsSheet() {
    }

    /** 草稿 cmtHTML 的 AV_COLORS（无头像图时字母头像底色）。 */
    private static final int[] AV_COLORS = {
            0xFFE63A2E, 0xFFE8A13A, 0xFF3E8E6E, 0xFF4A6FD4,
            0xFF8A5FC0, 0xFFC2507A, 0xFF2E8EA6, 0xFF7A6A53};

    private static final class Row {
        final HongguoCommentApi.Comment comment;
        final boolean reply;
        boolean expanded;
        /** 已展开回复的分页态：已载子行数、下一页游标、还有没有下一页。 */
        int replyLoaded;
        String replyCursor;
        boolean replyMore;

        Row(HongguoCommentApi.Comment c, boolean reply) {
            comment = c;
            this.reply = reply;
        }
    }

    /** 评论真实 total 回调（D6：面板拉到 total 后回写宿主条目计数）。 */
    public interface OnTotal {
        void onTotal(long total);
    }

    private static int navBarHeight(AppCompatActivity a) {
        int id = a.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? a.getResources().getDimensionPixelSize(id) : 0;
    }

    public static void show(AppCompatActivity activity, String seriesId, String vid, String title) {
        show(activity, seriesId, vid, title, null);
    }

    public static void show(AppCompatActivity activity, String seriesId, String vid,
                            String title, OnTotal onTotal) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        java.util.function.IntUnaryOperator dp = v -> (int) (v * density + 0.5f);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(Color.WHITE);
        float rr = dp.applyAsInt(22);
        rootBg.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
        root.setBackground(rootBg);

        View grab = new View(activity);
        GradientDrawable grabBg = new GradientDrawable();
        grabBg.setColor(0x291B1B1F);
        grabBg.setCornerRadius(dp.applyAsInt(2));
        grab.setBackground(grabBg);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                dp.applyAsInt(38), dp.applyAsInt(4));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.topMargin = dp.applyAsInt(9);
        glp.bottomMargin = dp.applyAsInt(2);
        root.addView(grab, glp);

        // 头（定稿 .s-head）：标题 16/900＋计数 span 12.5 sub＋关闭圆钮
        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp.applyAsInt(18), dp.applyAsInt(10), dp.applyAsInt(18), dp.applyAsInt(8));
        TextView headText = new TextView(activity);
        headText.setText("评论");
        headText.setTextColor(0xFF1B1B1F);
        headText.setTextSize(16);
        headText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(headText, new LinearLayout.LayoutParams(-2, -2));
        TextView headInfo = new TextView(activity);
        headInfo.setTextColor(0xFF8A8A93);
        headInfo.setTextSize(12.5f);
        LinearLayout.LayoutParams hilp = new LinearLayout.LayoutParams(-2, -2);
        hilp.leftMargin = dp.applyAsInt(5);
        head.addView(headInfo, hilp);
        View headSpacer = new View(activity);
        head.addView(headSpacer, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView close = new ImageView(activity);
        close.setImageResource(R.drawable.ic_drama_close);
        close.setColorFilter(0xFF1B1B1F);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0x0D1B1B1F);
        closeBg.setCornerRadius(dp.applyAsInt(19));
        close.setBackground(closeBg);
        close.setPadding(dp.applyAsInt(10), dp.applyAsInt(10),
                dp.applyAsInt(10), dp.applyAsInt(10));
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(
                dp.applyAsInt(38), dp.applyAsInt(38)));
        root.addView(head);

        // 体（定稿 .s-body）：评论列表，行间 1px line 分隔（草稿 .cmt 下边线）
        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setPadding(dp.applyAsInt(18), dp.applyAsInt(2),
                dp.applyAsInt(18), dp.applyAsInt(10));
        list.setClipToPadding(false);
        root.addView(list, new LinearLayout.LayoutParams(-1, -2));

        // 脚（定稿 .s-foot 底＋顶线＋手势区让位）：真输入行——文案
        // 照官方「有什么想法，展开说说」＋发送钮；接线（登录门＋
        // 发送）在适配器备好后经 wireComposer 统一挂（见下方）。
        LinearLayout foot = new LinearLayout(activity);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setBackgroundColor(0xFFFBFAF8);
        View footLine = new View(activity);
        footLine.setBackgroundColor(0x121B1B1F);
        foot.addView(footLine, new LinearLayout.LayoutParams(-1, 1));
        LinearLayout footRow = new LinearLayout(activity);
        footRow.setOrientation(LinearLayout.HORIZONTAL);
        footRow.setGravity(Gravity.CENTER_VERTICAL);
        footRow.setPadding(dp.applyAsInt(18), dp.applyAsInt(10), dp.applyAsInt(18),
                dp.applyAsInt(12) + navBarHeight(activity));
        android.widget.EditText input = new android.widget.EditText(activity);
        input.setHint("有什么想法，展开说说");
        input.setTextColor(0xFF1B1B1F);
        input.setHintTextColor(0xFF8A8A93);
        input.setTextSize(13.5f);
        input.setSingleLine(true);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(0x0D1B1B1F);
        inputBg.setCornerRadius(dp.applyAsInt(18));
        input.setBackground(inputBg);
        input.setPadding(dp.applyAsInt(14), dp.applyAsInt(8),
                dp.applyAsInt(14), dp.applyAsInt(8));
        footRow.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView sendBtn = new TextView(activity);
        sendBtn.setText("发送");
        sendBtn.setTextColor(0xFF1B1B1F);
        sendBtn.setTextSize(14);
        sendBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        sendBtn.setPadding(dp.applyAsInt(14), dp.applyAsInt(6),
                dp.applyAsInt(2), dp.applyAsInt(6));
        footRow.addView(sendBtn, new LinearLayout.LayoutParams(-2, -2));
        foot.addView(footRow);
        root.addView(foot);

        final boolean[] dismissed = {false};
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "hg-comments");
            t.setDaemon(true);
            return t;
        });
        dialog.setOnDismissListener(d -> {
            dismissed[0] = true;
            worker.shutdownNow();
        });

        // 适配器必须在 show 之前装好（与选集单 HgEpisodeSheet 同序）：
        // 此前是 show 之后才 setAdapter，首帧测量时列表无适配器、
        // 高度按 0 落定，数据回来 notify 也救不回布局——真机评论面
        // 只剩计数、列表全白的定根候选（账 hongguo-comments-blank）。
        // 状态行（加载中…）自首帧就在项数里，测量口径与选集单一致。
        CommentsAdapter adapter = new CommentsAdapter(activity, dp, worker, seriesId, vid, headInfo, onTotal, dismissed);
        list.setAdapter(adapter);
        wireComposer(activity, input, sendBtn, adapter, list, seriesId, vid, dismissed);
        list.addItemDecoration(new RecyclerView.ItemDecoration() {
            private final Paint linePaint = new Paint();

            {
                linePaint.setColor(0x121B1B1F);
            }

            @Override
            public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
                int rows = adapter.rowsSize();
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    int pos = parent.getChildAdapterPosition(child);
                    // 评论行下边线（草稿 .cmt border-bottom），最后一行评论不画
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
                if (lm != null && dy > 0 && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                    adapter.loadMore();
                }
            }
        });

        // 几何（回炉定根，账 fix168-episodesheet-diag）：摘除
        // fitListHeight 脱窗实量——列表高改写死常数：0.72 屏高
        // 减头脚构造常数（头 56dp＋脚 64dp），不取任何 measure
        // 返回值（照老版评论单能用口径）。评论异步增量到，行
        // 在常数高内滚动加载。
        android.util.DisplayMetrics cdm = activity.getResources().getDisplayMetrics();
        int listH = Math.max(0, (int) (cdm.heightPixels * 0.72f) - dp.applyAsInt(56 + 64));
        ViewGroup.LayoutParams listLp = list.getLayoutParams();
        if (listLp != null) {
            listLp.height = listH;
            list.setLayoutParams(listLp);
        }
        dialog.setContentView(root);
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.setBackgroundColor(Color.TRANSPARENT);
        }
        dialog.show();
        adapter.loadMore();
    }

    private static GradientDrawable ovalBg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /**
     * 输入行接线（与横屏面板同口径）：登录门——未登录点输入行在落
     * 焦前拦下跳登录页（不弹键盘）、点发送同门（#22 口径）；发送
     * 走 DramaCommentPoster，发送中禁再发防连点重发；成功清文案
     * ＋本地插顶回显（计数 +1），失败保留文案给「没发出去」明确
     * 失败态、不装成功；单已关/宿主已毁后回调一律不触视图。
     */
    private static void wireComposer(AppCompatActivity activity,
                                     android.widget.EditText input, TextView sendBtn,
                                     CommentsAdapter adapter, RecyclerView list,
                                     String seriesId, String vid, boolean[] dismissed) {
        final boolean[] sending = {false};
        Runnable send = () -> {
            if (dismissed[0] || sending[0]) return;
            if (!com.fongmi.android.tv.hongguo.api.HgAccountStore.isLoggedIn()) {
                HongguoLoginActivity.start(activity);
                return;
            }
            String text = input.getText() == null ? "" : input.getText().toString().trim();
            if (text.isEmpty()) {
                input.setError("先写点什么");
                return;
            }
            sending[0] = true;
            sendBtn.setEnabled(false);
            com.fongmi.android.tv.hongguo.drama.DramaCommentPoster.publish(
                    seriesId, vid, text,
                    new com.fongmi.android.tv.hongguo.api.HgCallback<String>() {
                        @Override
                        public void onSuccess(String commentId) {
                            activity.runOnUiThread(() -> {
                                sending[0] = false;
                                sendBtn.setEnabled(true);
                                if (dismissed[0] || activity.isFinishing()
                                        || activity.isDestroyed()) return;
                                input.setText("");
                                com.fongmi.android.tv.hongguo.api.HgAccountStore.Session s =
                                        com.fongmi.android.tv.hongguo.api.HgAccountStore.load();
                                adapter.prependMine(HongguoCommentApi.localEcho(commentId,
                                        s == null ? "" : s.name,
                                        s == null ? "" : s.avatar, text));
                                list.scrollToPosition(0);
                            });
                        }

                        @Override
                        public void onError(int code, String msg) {
                            activity.runOnUiThread(() -> {
                                sending[0] = false;
                                sendBtn.setEnabled(true);
                                if (dismissed[0] || activity.isFinishing()
                                        || activity.isDestroyed()) return;
                                android.widget.Toast.makeText(activity,
                                        "没发出去，再试试",
                                        android.widget.Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
        };
        sendBtn.setOnClickListener(v -> send.run());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                send.run();
                return true;
            }
            return false;
        });
        input.setOnTouchListener((v, e) -> {
            if (dismissed[0]) return false;
            if (!com.fongmi.android.tv.hongguo.api.HgAccountStore.isLoggedIn()) {
                if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                    HongguoLoginActivity.start(activity);
                }
                return true;
            }
            return false;
        });
    }

    private static final class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.H> {
        private final AppCompatActivity activity;
        private final java.util.function.IntUnaryOperator dp;
        private final ExecutorService worker;
        private final String seriesId;
        private final String vid;
        private final TextView headInfo;
        private final OnTotal onTotal;
        private final boolean[] dismissed;
        private final List<Row> rows = new ArrayList<>();
        private String cursor;
        private boolean hasMore = true;
        private boolean loading;
        private boolean firstLoaded;
        private boolean loadFailed;
        private long lastTotal = -1;

        CommentsAdapter(AppCompatActivity a, java.util.function.IntUnaryOperator dp,
                        ExecutorService worker, String seriesId, String vid, TextView headInfo,
                        OnTotal onTotal, boolean[] dismissed) {
            activity = a;
            this.dp = dp;
            this.worker = worker;
            this.seriesId = seriesId;
            this.vid = vid;
            this.headInfo = headInfo;
            this.onTotal = onTotal;
            this.dismissed = dismissed;
        }

        /**
         * 池投递收口（P0 同型）：单关 dismiss 即 shutdownNow，滚动/回包
         * 迟到的 loadMore、展开回复再投递必撞 RejectedExecutionException；
         * 单已关＝投递无意义，静默丢弃。
         */
        private void exec(Runnable task) {
            if (dismissed[0]) return;
            try {
                worker.execute(task);
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
            }
        }

        int rowsSize() {
            return rows.size();
        }

        /** 自己刚发的评论本地插顶回显＋计数 +1（计数回写单独隔离，与 loadMore 同纪律）。 */
        void prependMine(HongguoCommentApi.Comment c) {
            rows.add(0, new Row(c, false));
            notifyItemInserted(0);
            if (lastTotal >= 0) {
                lastTotal++;
                try {
                    headInfo.setText("· " + HongguoFeedActivity.HgTimeTextCount.format(lastTotal) + " 条");
                    if (onTotal != null) onTotal.onTotal(lastTotal);
                } catch (Throwable ignored) {
                }
            }
        }

        void loadMore() {
            if (loading || !hasMore) return;
            loading = true;
            final String cur = cursor;
            exec(() -> {
                HongguoCommentApi.Page page = HongguoCommentApi.episodeComments(seriesId, vid, cur);
                activity.runOnUiThread(() -> {
                    // 销毁复核（C-24/F3）：exec 只管投递不撞死池，UI 回
                    // 调此前无复核——宿主销毁/单已关时仍写 headInfo 并
                    // 经 OnTotal 摸宿主页 adapter。与 wireComposer 两
                    // 回调同口径：dismissed＋isFinishing＋isDestroyed。
                    if (dismissed[0] || activity.isFinishing()
                            || activity.isDestroyed()) return;
                    loading = false;
                    if (page == null) {
                        // 拉取失败（数据层失败一律回 null）须与真空态
                        // 分开：旧写法当成到底、锁死 hasMore 并上
                        // 「还没有评论」——网络抖一次整面作废且无重试
                        // 路。改为不锁 hasMore、游标不前进，状态行给
                        // 失败文案＋点按重试；已有行时滚动近底亦会再
                        // 试（账 fix168-comment-surfaces）。
                        loadFailed = true;
                        notifyDataSetChanged();
                        return;
                    }
                    loadFailed = false;
                    firstLoaded = true;
                    if (page.total > 0) {
                        lastTotal = page.total;
                        // 计数回写单独隔离：格式化/宿主回调哪一步抛了，
                        // 都不许连坐下面的入列与 notify（否则面板只剩
                        // 计数、列表全白——真机空白面板的候选链之一，
                        // 账 hongguo-comments-blank-20261031）。
                        try {
                            headInfo.setText("· " + HongguoFeedActivity.HgTimeTextCount.format(page.total) + " 条");
                            // D6：真实 total 回写宿主当前条，关面板后右列
                            // 计数与面板同源，不再停在 cell 下发旧数
                            if (onTotal != null) onTotal.onTotal(page.total);
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

        private boolean repliesLoading;

        /**
         * 回复展开三态（官方截图有展开/收起两态，旧实现只有展开）：
         * 首拉、续拉（回复多于一页时旧实现游标恒传空串、只看得到
         * 第一页）、收起。展开失败或回空页时还原未展开——旧实现先
         * 把 expanded 置真再拉，失败即把行锁进「已展开回复」假态、
         * 点不动也收不回（账 fix168-comment-surfaces 断点二）。
         */
        private void loadReplies(Row row, boolean first) {
            if (repliesLoading) return;
            repliesLoading = true;
            if (first) {
                row.expanded = true;
                int at = rows.indexOf(row);
                if (at >= 0) notifyItemChanged(at);
            }
            final String cid = row.comment.id;
            final String cur = first ? "" : (row.replyCursor == null ? "" : row.replyCursor);
            exec(() -> {
                HongguoCommentApi.Page page = HongguoCommentApi.episodeReplies(seriesId, vid, cid, cur);
                activity.runOnUiThread(() -> {
                    // 销毁复核（C-24/F3）：与 loadMore 同口径。
                    if (dismissed[0] || activity.isFinishing()
                            || activity.isDestroyed()) return;
                    repliesLoading = false;
                    int pos = rows.indexOf(row);
                    // 拉取途中被收起：迟到回包整页丢弃，不往已收起的行下塞
                    if (pos < 0 || !row.expanded) return;
                    if (page == null || (first && page.items.isEmpty())) {
                        if (first) {
                            row.expanded = false;
                            notifyItemChanged(pos);
                        }
                        return;
                    }
                    List<Row> sub = new ArrayList<>();
                    for (HongguoCommentApi.Comment c : page.items) sub.add(new Row(c, true));
                    int at = pos + 1 + row.replyLoaded;
                    rows.addAll(at, sub);
                    row.replyLoaded += sub.size();
                    row.replyCursor = page.nextCursor;
                    row.replyMore = page.hasMore;
                    if (!sub.isEmpty()) notifyItemRangeInserted(at, sub.size());
                    notifyItemChanged(pos);
                });
            });
        }

        private void collapseReplies(Row row) {
            int pos = rows.indexOf(row);
            if (pos < 0) return;
            int n = row.replyLoaded;
            if (n > 0) {
                rows.subList(pos + 1, pos + 1 + n).clear();
                notifyItemRangeRemoved(pos + 1, n);
            }
            row.expanded = false;
            row.replyLoaded = 0;
            row.replyCursor = null;
            row.replyMore = false;
            notifyItemChanged(pos);
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
            name.setTextColor(0xFF1B1B1F);
            name.setTextSize(13);
            name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            col.addView(name);
            TextView text = new TextView(activity);
            text.setTag("text");
            text.setTextColor(0xFF33333A);
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
                // 底部状态行（加载/空/失败/到底）：失败态只在空表
                // 时上文案并可点重试，有行时不打扰（滚动自会再试）
                h.name.setText("");
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
            // 草稿 cmtHTML：无图时色块字母头像（色号按名首字＋名长取 AV_COLORS）
            h.letter.setText(author.isEmpty() ? "" : author.substring(0, 1));
            int seed = (author.isEmpty() ? 0 : author.charAt(0)) + author.length();
            h.letter.setBackground(ovalBg(AV_COLORS[seed % AV_COLORS.length]));
            if (c.avatar != null && !c.avatar.isEmpty()) {
                Glide.with(activity).load(c.avatar).circleCrop().into(h.avatar);
            } else {
                h.avatar.setImageDrawable(null);
            }
            h.name.setText(author);
            String body = c.content == null ? "" : c.content;
            if (c.replyTo != null && !c.replyTo.isEmpty()) {
                body = "回复 @" + c.replyTo + "：" + body;
            }
            h.text.setText(body);
            StringBuilder meta = new StringBuilder();
            // 日期段（T-06）：官方同位首段；created 未验/缺失（本
            // 地回显置 0）时不拼、不留空位（HgCommentMeta 回空串）
            String date = HgCommentMeta.dateText(c.created);
            if (!date.isEmpty()) meta.append(date);
            if (c.likes > 0) {
                if (meta.length() > 0) meta.append("  ·  ");
                meta.append("赞 ").append(c.likes);
            }
            if (!row.reply && c.replyCount > 0) {
                if (meta.length() > 0) meta.append("  ·  ");
                if (!row.expanded) meta.append("展开 ").append(c.replyCount).append(" 条回复");
                else if (row.replyMore) meta.append("展开更多回复");
                else meta.append("收起");
            }
            h.meta.setText(meta.toString());
            h.meta.setOnClickListener(!row.reply && c.replyCount > 0 ? v -> {
                if (!row.expanded) loadReplies(row, true);
                else if (row.replyMore) loadReplies(row, false);
                else collapseReplies(row);
            } : null);
        }

        @Override
        public int getItemCount() {
            return rows.size() + 1;
        }
    }
}
