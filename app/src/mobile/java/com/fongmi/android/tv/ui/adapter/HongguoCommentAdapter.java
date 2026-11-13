package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Path;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.HongguoCommentApi;
import com.fongmi.android.tv.databinding.AdapterHongguoCommentBinding;
import com.fongmi.android.tv.databinding.AdapterHongguoFooterBinding;
import com.fongmi.android.tv.databinding.AdapterHongguoReplyBinding;
import com.github.catvod.net.OkHttp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 红果评论列表 Adapter：评论行（头像/名字/时间/正文/评分/点赞/回复）＋页脚行
 * （加载中/失败重试）。回复以内嵌展开区呈现，展开状态由宿主（Dialog）持有，
 * 本类只负责按状态渲染——含每条评论的回复分页游标纪律在宿主侧。
 */
public class HongguoCommentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int FOOTER_NONE = 0;
    public static final int FOOTER_LOADING = 1;
    public static final int FOOTER_ERROR = 2;

    private static final int TYPE_COMMENT = 0;
    private static final int TYPE_FOOTER = 1;

    /** 单条评论的回复展开状态（宿主持久持有，Adapter 只读渲染）。 */
    public static class ReplyBox {
        public boolean expanded;
        public boolean loading;
        public boolean failed;
        public boolean hasMore;
        public final List<HongguoCommentApi.Comment> items = new ArrayList<>();
    }

    public interface Listener {
        /** 取某条评论的回复展开状态，没有则返回 null（视为未展开）。 */
        ReplyBox replyBoxOf(String commentId);

        /** 点了「回复 N」：展开/收起由宿主处理，处理完回调 notify。 */
        void onToggleReplies(HongguoCommentApi.Comment item);

        /** 点了回复区底部的「展开更多回复」。 */
        void onMoreReplies(HongguoCommentApi.Comment item);

        /** 点了页脚的失败重试行。 */
        void onFooterRetry();
    }

    private final Listener listener;
    private final List<HongguoCommentApi.Comment> mItems = new ArrayList<>();
    private int footer = FOOTER_NONE;

    public HongguoCommentAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<HongguoCommentApi.Comment> items) {
        mItems.clear();
        if (items != null) mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void appendItems(List<HongguoCommentApi.Comment> items) {
        if (items == null || items.isEmpty()) return;
        int start = mItems.size();
        mItems.addAll(items);
        notifyItemRangeInserted(start, items.size());
    }

    public List<HongguoCommentApi.Comment> items() {
        return mItems;
    }

    public void setFooter(int state) {
        if (footer == state) return;
        int old = footer;
        footer = state;
        // 页脚行级通知（稳定性扫查二轮 (13)）：页脚恒在末位（位
        // ＝条目数），旧形任一页脚翻转全刷整表——续拉/到底/失败
        // 每翻一次全行重绑。改按旧→新形态发插入/变更/移除。
        int footPos = mItems.size();
        if (old == FOOTER_NONE && state != FOOTER_NONE) {
            notifyItemInserted(footPos);
        } else if (old != FOOTER_NONE && state == FOOTER_NONE) {
            notifyItemRemoved(footPos);
        } else {
            notifyItemChanged(footPos);
        }
    }

    public int getFooter() {
        return footer;
    }

    /** 某条评论的回复状态变化后刷新该行。 */
    public void refreshComment(String commentId) {
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).id.equals(commentId)) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return position < mItems.size() ? TYPE_COMMENT : TYPE_FOOTER;
    }

    @Override
    public int getItemCount() {
        return mItems.size() + (footer == FOOTER_NONE ? 0 : 1);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_FOOTER) return new FooterHolder(AdapterHongguoFooterBinding.inflate(inflater, parent, false));
        return new CommentHolder(AdapterHongguoCommentBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FooterHolder) {
            FooterHolder footerHolder = (FooterHolder) holder;
            boolean error = footer == FOOTER_ERROR;
            footerHolder.binding.text.setText(error ? R.string.hongguo_comment_failed : R.string.hongguo_comment_loading);
            footerHolder.binding.text.setOnClickListener(error ? v -> listener.onFooterRetry() : null);
            return;
        }
        CommentHolder commentHolder = (CommentHolder) holder;
        HongguoCommentApi.Comment item = mItems.get(position);
        AdapterHongguoCommentBinding binding = commentHolder.binding;
        Context context = binding.getRoot().getContext();
        loadAvatar(binding.avatar, item.avatar);
        binding.author.setText(item.author);
        binding.time.setText(formatTime(item.created));
        if (item.score != null && !item.score.isEmpty()) {
            binding.score.setVisibility(View.VISIBLE);
            binding.score.setText(item.score + " 分");
        } else {
            binding.score.setVisibility(View.GONE);
        }
        if (item.replyTo != null && !item.replyTo.isEmpty()) {
            binding.content.setText(context.getString(R.string.hongguo_comment_reply_to, item.replyTo) + "：" + item.content);
        } else {
            binding.content.setText(item.content);
        }
        binding.likes.setText(item.likes > 0 ? context.getString(R.string.hongguo_comment_likes, item.likes) : "赞");
        ReplyBox box = listener.replyBoxOf(item.id);
        boolean expanded = box != null && box.expanded;
        if (item.replyCount > 0) {
            binding.replies.setVisibility(View.VISIBLE);
            binding.replies.setText(context.getString(R.string.hongguo_comment_replies) + " " + item.replyCount + (expanded ? " ▴" : " ▾"));
            binding.replies.setOnClickListener(v -> listener.onToggleReplies(item));
        } else {
            binding.replies.setVisibility(View.GONE);
            binding.replies.setOnClickListener(null);
        }
        bindRepliesBox(commentHolder, binding, item, box);
    }

    /** 回复子表内容键：父 id＋展开态＋已载条数＋首尾 id＋脚状态——任一变即真变化。 */
    private static String repliesKeyOf(HongguoCommentApi.Comment item, ReplyBox box) {
        if (box == null || !box.expanded) return item.id + "|closed";
        int n = box.items.size();
        String first = n > 0 ? box.items.get(0).id : "";
        String last = n > 0 ? box.items.get(n - 1).id : "";
        return item.id + "|open|" + n + "|" + first + "|" + last
                + "|" + box.loading + "|" + box.failed + "|" + box.hasMore;
    }

    private void bindRepliesBox(CommentHolder holder, AdapterHongguoCommentBinding binding, HongguoCommentApi.Comment item, ReplyBox box) {
        ViewGroup container = binding.repliesBox;
        // 内容键守卫（稳定性扫查二轮 (13)）：旧形每次 bind 都清
        // 容器全量重建回复子表——本行任何重绑（点赞/页脚翻转连
        // 带）都把子表推倒重来、高度抖一遍。键同直接留旧子表；
        // 折叠态走 GONE 分支同受守卫（键含展开态）。
        String key = repliesKeyOf(item, box);
        if (key.equals(holder.repliesKey)) {
            container.setVisibility(box != null && box.expanded ? View.VISIBLE : View.GONE);
            return;
        }
        holder.repliesKey = key;
        container.removeAllViews();
        if (box == null || !box.expanded) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        Context context = container.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (HongguoCommentApi.Comment reply : box.items) {
            AdapterHongguoReplyBinding row = AdapterHongguoReplyBinding.inflate(inflater, container, false);
            row.author.setText(reply.author);
            row.time.setText(formatTime(reply.created));
            if (reply.replyTo != null && !reply.replyTo.isEmpty()) {
                row.content.setText(context.getString(R.string.hongguo_comment_reply_to, reply.replyTo) + "：" + reply.content);
            } else {
                row.content.setText(reply.content);
            }
            row.likes.setText(reply.likes > 0 ? context.getString(R.string.hongguo_comment_likes, reply.likes) : "赞");
            container.addView(row.getRoot());
        }
        TextView foot = new TextView(context);
        foot.setTextSize(12);
        foot.setTextColor(0x80000000);
        foot.setPadding(0, 12, 0, 4);
        if (box.loading) {
            foot.setText(R.string.hongguo_comment_loading);
            container.addView(foot);
        } else if (box.failed) {
            foot.setText(R.string.hongguo_comment_failed);
            foot.setOnClickListener(v -> listener.onMoreReplies(item));
            container.addView(foot);
        } else if (box.hasMore) {
            foot.setText(R.string.hongguo_comment_more_replies);
            foot.setOnClickListener(v -> listener.onMoreReplies(item));
            container.addView(foot);
        }
    }

    // ---------- 头像加载（HEIC 专用路，不走 Glide） ----------

    /**
     * 红果头像不能走 Glide：fqnovelpic 对头像一律回 image/heic（地址写 jpeg、请求带
     * Accept 协商都改不了，主会话实测在案），而壳子的 Glide 5.0.7 在 header 解析层
     * 根本没有 HEIF 类型（ImageType 枚举无 HEIF、DefaultImageHeaderParser 只嗅 AVIF
     * 品牌，字节码实证见 danmaku-avatar-dialog-fixes.md），HEIC 头像必然解不出、只剩占位，
     * jpeg/png 的能出——正是用户看到的「有些头像加载不出来」。
     * 改走原始字节＋平台 BitmapFactory 解码（Android 10+ 平台原生支持 HEIF）再圆形裁切，
     * jpeg/png 同路走；只动评论头像这一处，封面加载链不牵连。失败保留默认占位，不白空。
     */
    private static final int AVATAR_PX = 144;
    private static final LruCache<String, Bitmap> AVATAR_CACHE = new LruCache<>(96);
    private static final ExecutorService AVATAR_EXECUTOR = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "hongguo-avatar");
        thread.setDaemon(true);
        return thread;
    });

    private static void loadAvatar(ImageView view, String url) {
        loadAvatar(view, url, false);
    }

    private static void loadAvatar(ImageView view, String url, boolean retried) {
        view.setTag(url);
        if (url == null || url.isEmpty()) {
            view.setImageResource(R.drawable.ic_avatar_placeholder);
            return;
        }
        Bitmap cached = AVATAR_CACHE.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(R.drawable.ic_avatar_placeholder);
        AVATAR_EXECUTOR.execute(() -> {
            Bitmap bitmap = fetchAvatar(url);
            if (bitmap != null) {
                AVATAR_CACHE.put(url, bitmap);
                view.post(() -> {
                    // 列表复用守卫：view 已绑到别的评论就别把旧头像盖上去
                    if (url.equals(view.getTag())) view.setImageBitmap(bitmap);
                });
            } else if (!retried) {
                // 失败不入缓存、重绑时会重拉；但行停在屏上不滚动就永不翻身，
                // 补一次绑定内延迟重试（只此一次）：排程后、触发时都再核 tag，
                // view 已复用给别的评论就作罢；再失败保持占位，不再循环。
                view.postDelayed(() -> {
                    if (url.equals(view.getTag())) loadAvatar(view, url, true);
                }, 1500);
            }
        });
    }

    private static Bitmap fetchAvatar(String url) {
        try {
            Request request = new Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build();
            try (Response response = OkHttp.client().newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                byte[] bytes = response.body().bytes();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                int sample = 1;
                while (bounds.outWidth / (sample * 2) >= AVATAR_PX && bounds.outHeight / (sample * 2) >= AVATAR_PX) sample *= 2;
                opts.inSampleSize = sample;
                Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                return decoded == null ? null : circleCrop(decoded);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /** 中心方裁＋圆形遮罩（等价原 Glide circleCrop 的观感）。 */
    private static Bitmap circleCrop(Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        if (size <= 0) return src;
        Bitmap square = Bitmap.createBitmap(src, (src.getWidth() - size) / 2, (src.getHeight() - size) / 2, size, size);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Path path = new Path();
        path.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CCW);
        canvas.clipPath(path);
        canvas.drawBitmap(square, 0, 0, null);
        return out;
    }

    static String formatTime(long epochSeconds) {
        if (epochSeconds <= 0) return "";
        Date date = new Date(epochSeconds * 1000L);
        Date now = new Date();
        String pattern = new SimpleDateFormat("yyyy", Locale.US).format(date).equals(new SimpleDateFormat("yyyy", Locale.US).format(now))
                ? "MM-dd HH:mm" : "yyyy-MM-dd";
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(date);
    }

    static class CommentHolder extends RecyclerView.ViewHolder {
        final AdapterHongguoCommentBinding binding;
        /** 回复子表内容键（稳定性扫查二轮 (13)）：键同重绑跳过子表重建。 */
        String repliesKey;

        CommentHolder(AdapterHongguoCommentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class FooterHolder extends RecyclerView.ViewHolder {
        final AdapterHongguoFooterBinding binding;

        FooterHolder(AdapterHongguoFooterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
