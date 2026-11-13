package com.fongmi.android.tv.ui.hk;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.ui.hk.view.DrawableInlineTextView;
import com.fongmi.android.tv.ui.hk.view.HkCenterListPopup;
import com.fongmi.android.tv.ui.hk.view.DrawableTextView;
import com.fongmi.android.tv.ui.hk.view.ObservableScrollView;
import com.google.android.flexbox.FlexboxLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.codeboy.android.aligntextview.AlignTextView;

/**
 * 照抄 8.83 com.example.hikerview.ui.home.ArticleListAdapter 的核心机制（逐方法转写）：
 * 条目分组（updatePolyList）、视图类型分发（getItemViewType，含 poly 占位 -1/-10 与
 * icon_small_3 的 -3 特例）。Holder 的创建与绑定按原版各 *Holder 逐个转写在本类内。
 *
 * 与原版的对应关系：ArticleList ↔ HkItem（getType() ↔ getColType()）；
 * isLand() ↔ 当前是否为横向（星辰手机竖屏为主，原版分支照抄保留）。
 */
public abstract class HkArticleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** 照抄原版 ArticleListAdapter$OnItemClickListener 的六个回调。 */
    public interface OnItemClickListener {
        void onClassClick(View view, String title, String cls);
        void onClick(View view, int position);
        void onCodeClick(View view, int position, String code);
        void onLoadMore(View view, int position);
        void onLongClick(View view, int position);
        void onUrlClick(View view, int position, String url);
    }

    /**
     * 照抄原版 IBaseHolder（IBaseHolder.smali：getImageView()Landroid/widget/ImageView;）：
     * onViewRecycled 据此取图清 Glide 请求；实现面与原版 23 个带图 Holder 逐个对应（见各 Holder 的 getImageView）。
     */
    public interface HkImageHolder {
        ImageView getImageView();
    }

    protected final List<HkItem> list;
    protected final List<List<HkItem>> polyList = new ArrayList<>();
    protected final Map<Integer, Integer> polyPositionMap = new HashMap<>();
    protected final Map<String, HeaderHolder> headerHolders = new HashMap<>(3);
    protected final android.content.Context context;
    protected final RecyclerView recyclerView;
    protected int lineHeight = 0;
    /** 对应原版 articleListRule.getUrl()：当前页 URL，图片 Referer 推导与 @js= 求值用。 */
    protected String pageUrl;
    /** 对应原版 articleListRule.getTitle()：当前规则名（“我的主页”图标改名特判用），宿主灌入。 */
    protected String ruleTitle = "";
    protected OnItemClickListener onItemClickListener;

    // 照抄原版构造器里的五个 RequestOptions（ArticleListAdapter.smali:173-180）
    protected final com.bumptech.glide.request.RequestOptions options = new com.bumptech.glide.request.RequestOptions();
    protected final com.bumptech.glide.request.RequestOptions optionsFull = new com.bumptech.glide.request.RequestOptions()
            .skipMemoryCache(true)
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL);
    protected final com.bumptech.glide.request.RequestOptions options2 = new com.bumptech.glide.request.RequestOptions()
            .override(320, 480)
            .skipMemoryCache(true)
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL);
    protected final com.bumptech.glide.request.RequestOptions optionsNoCache = new com.bumptech.glide.request.RequestOptions()
            .skipMemoryCache(false)
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE);
    protected final com.bumptech.glide.request.RequestOptions skipCacheOption = new com.bumptech.glide.request.RequestOptions()
            .skipMemoryCache(true)
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE);

    protected HkArticleAdapter(android.content.Context context, RecyclerView recyclerView, List<HkItem> list) {
        this.context = context;
        this.recyclerView = recyclerView;
        this.list = list;
        this.dividerItem = new MyDividerItem();
    }

    /** 照抄原版 MyDividerItem：leftRight 按 viewType 查枚举再 dpToPx。 */
    public class MyDividerItem extends com.fongmi.android.tv.ui.hk.view.HkBaseDividerItem {
        @Override
        public int getLeftRight(int viewType) {
            return com.fongmi.android.tv.utils.ResUtil.dp2px(HkColType.leftRightByItemType(viewType));
        }
    }

    protected final MyDividerItem dividerItem;

    public MyDividerItem getDividerItem() {
        return dividerItem;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    public void setRuleTitle(String ruleTitle) {
        this.ruleTitle = ruleTitle == null ? "" : ruleTitle;
    }

    protected String ruleUrl = "";
    protected String ruleFirstHeader = "";

    protected long inputSuggestTicket;

    /** 原版 onInputChange 直调 JSEngine.evalJS(getMyRule+onChange, text)；转写侧由宿主接引擎单例。 */
    public interface OnInputEvalListener {
        void evalInput(String onChangeJs, String text);
    }

    protected OnInputEvalListener inputEvalListener;

    public void setOnInputEvalListener(OnInputEvalListener listener) {
        this.inputEvalListener = listener;
    }

    /** 原版 TextWatcher 直调 SuggestModel.loadFromRemote（百度建议词）；转写侧由宿主实现。 */
    public interface SuggestLoader {
        void loadFromRemote(String query, java.util.function.Consumer<List<String>> consumer);
    }

    protected SuggestLoader suggestLoader;

    public void setSuggestLoader(SuggestLoader loader) {
        this.suggestLoader = loader;
    }

    /** 原版 rich_text 图片点击走 XPopup 图片查看器/PlayerChooser、code:// 文案先过 AutoImportHelper；转写侧由宿主承接。 */
    public interface OnRichTextActionListener {
        void onImageClick(List<String> urls, int position);

        boolean checkCodeImport(String trimmedText);
    }

    protected OnRichTextActionListener richTextActionListener;

    public void setOnRichTextActionListener(OnRichTextActionListener listener) {
        this.richTextActionListener = listener;
    }

    /** 原版 adapter 的 webViewHolder（ArticleWebViewHolder）：宿主在有行内 WebView 时灌入。 */
    protected HkWebViewHolder webViewHolder;

    public void setWebViewHolder(HkWebViewHolder webViewHolder) {
        this.webViewHolder = webViewHolder;
    }

    /** 等价原版 InputExtra 挂在条目上的运行时状态：[0]=defaultValue（当前文本） [1]=lastText（onChange 去重）。 */
    private final Map<HkItem, String[]> inputStates = new java.util.WeakHashMap<>();

    private String[] inputStateOf(HkItem item) {
        if (item == null || item.getExtra().isEmpty()) return null;
        String[] st = inputStates.get(item);
        if (st == null) {
            st = new String[]{item.getExtra("defaultValue"), null};
            inputStates.put(item, st);
        }
        return st;
    }

    /** 宿主灌入当前规则的 url 与 firstHeader（原版 articleListRule 的对应字段，header 选中态判定用）。 */
    public void setRuleHeaderInfo(String ruleUrl, String ruleFirstHeader) {
        this.ruleUrl = ruleUrl == null ? "" : ruleUrl;
        this.ruleFirstHeader = ruleFirstHeader == null ? "" : ruleFirstHeader;
    }

    /** 照抄原版 isLand（ArticleListAdapter.smali:9597）：当前配置是否为横向。 */
    protected boolean isLand() {
        return context.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    /** 照抄原版 isNight（ArticleListAdapter.smali:9627）。 */
    protected boolean isNight() {
        return (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * 照抄原版 getOptions（ArticleListAdapter.smali:9328，java 2386-2399）。
     * 唯一的用户令偏差：占位图原版是 shape_placeholder_radius（灰色圆角），
     * 潇洒哥 2026-10-13 明令占位透明，这里用透明占位，其余分支与原版一致。
     */
    protected com.bumptech.glide.request.RequestOptions getOptions(String pic) {
        android.graphics.drawable.Drawable ph = new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT);
        if (android.text.TextUtils.isEmpty(pic) || "*".equals(pic)) {
            return (com.bumptech.glide.request.RequestOptions) options.clone().placeholder(ph);
        }
        if (pic.contains("glideCache=skip")) {
            return (com.bumptech.glide.request.RequestOptions) skipCacheOption.clone().placeholder(ph);
        }
        if (pic.startsWith("file://") || pic.startsWith("hiker://")) {
            return (com.bumptech.glide.request.RequestOptions) optionsNoCache.clone().placeholder(ph);
        }
        return (com.bumptech.glide.request.RequestOptions) options.clone().placeholder(ph);
    }

    /** 照抄原版 bindImageView(ImageView, ArticleList, int)（smali:9014）。 */
    protected void bindImageView(ImageView imageView, HkItem item, int position) {
        Object url = HkGlideUtil.getGlideUrl(pageUrl, item.getPic());
        HkGlideUtil.loadPicDrawable(context, imageView, url, getOptions(item.getPic()));
    }

    /**
     * 衬底卡面（用户定版 2026-11-02）：原版写死在布局里的衬底（白 pill/白衬底/米灰底/白框等）
     * 统一换 HkCardFace 卡面（小程序页按主色淡染的同一套构建函数），不用纯白、不用页签玻璃。
     * 静态面取条目主色：pic 色值定义优先，否则标题色板兜底。布局里的 background 仅作绑定前占位，绑定即覆盖。
     */
    protected void applyCardFace(View target, HkItem item) {
        if (target == null || item == null) return;
        target.setBackground(HkCardFace.face(context, HkCardFace.baseColorFor(item)));
    }

    /**
     * 封面衬底（用户定版 2026-11-02）：先以兜底色卡面垫着，封面图到后取其主色
     * （CoverColor.dominantColor，后台线程）把同一层重染成封面色；图失败/无图保持兜底面。
     * 复用守卫：回调落点时绑定位置仍是本条且 pic 未变才重染。
     */
    protected void applyCoverFace(RecyclerView.ViewHolder holder, HkItem item, int position, View... layers) {
        if (item == null) return;
        for (View v : layers) {
            if (v != null) v.setBackground(HkCardFace.face(context, HkCardFace.baseColorFor(item)));
        }
        String pic = item.getPic();
        if (android.text.TextUtils.isEmpty(pic) || "*".equals(pic) || pic.startsWith("#")) return;
        Object model = HkGlideUtil.getGlideUrl(pageUrl, pic);
        if (model == null) return;
        View anchor = null;
        for (View v : layers) if (v != null) { anchor = v; break; }
        if (anchor == null) return;
        final View anchorF = anchor;
        com.bumptech.glide.Glide.with(context).asBitmap().load(model)
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull android.graphics.Bitmap resource,
                                                  @Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                        com.fongmi.android.tv.utils.Task.execute(() -> {
                            int color = com.fongmi.android.tv.utils.CoverColor.dominantColor(resource);
                            anchorF.post(() -> {
                                if (holder.getBindingAdapterPosition() != position) return;
                                if (position < 0 || position >= list.size()) return;
                                HkItem cur = list.get(position);
                                if (cur == null || !pic.equals(cur.getPic())) return;
                                for (View v : layers) {
                                    if (v != null) v.setBackground(HkCardFace.face(context, color));
                                }
                            });
                        });
                    }

                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                    }
                });
    }

    /**
     * 照抄原版 bindImageView(ImageView, TextView, ArticleList, int, BaseRequestOptions)（smali:8920，java 1340-1432）
     * 及其 $4 号 CustomTarget：tag=pic 做视图复用守卫；开始时以标题首字拼音大写字母作文字图标，
     * 成功时大图（>100MB）先缩小再显示，失败时按 position%6 加载 shape_placeholder_radius_N 圆形图。
     */
    protected void bindImageView(ImageView imageView, TextView iconTextView, HkItem item, int position,
                                 com.bumptech.glide.request.BaseRequestOptions<?> options) {
        String tag = item.getPic() == null ? "" : item.getPic();
        iconTextView.setTag(tag);
        com.bumptech.glide.Glide.with(context).asDrawable()
                .load(HkGlideUtil.getGlideUrl(pageUrl, item.getPic()))
                .apply(options)
                .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e,
                                                Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                boolean isFirstResource) {
                        com.github.catvod.crawler.SpiderDebug.log("hkimage", "封面解码失败 host=%s 错=%s",
                                hostOf(tag), e == null ? "" : String.valueOf(e.getMessage()));
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                                                  com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                  com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        int bytes = 0;
                        if (resource instanceof android.graphics.drawable.BitmapDrawable) {
                            android.graphics.Bitmap b = ((android.graphics.drawable.BitmapDrawable) resource).getBitmap();
                            if (b != null) bytes = b.getByteCount();
                        }
                        com.github.catvod.crawler.SpiderDebug.log("hkimage", "封面解码成功 host=%s 字节=%d 来源=%s",
                                hostOf(tag), bytes, dataSource);
                        return false;
                    }

                    private String hostOf(String url) {
                        try {
                            String u = url == null ? "" : url;
                            int p = u.indexOf("://");
                            if (p >= 0) u = u.substring(p + 3);
                            int q = u.indexOf('/');
                            String h = q >= 0 ? u.substring(0, q) : u;
                            int at = h.indexOf('@');
                            return at >= 0 ? h.substring(0, at) : h;
                        } catch (Throwable t) {
                            return "";
                        }
                    }
                })
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull android.graphics.drawable.Drawable resource,
                                                  @Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.drawable.Drawable> transition) {
                        if (!tag.equals(iconTextView.getTag())) return;
                        if (resource instanceof android.graphics.drawable.BitmapDrawable) {
                            android.graphics.Bitmap bmp = ((android.graphics.drawable.BitmapDrawable) resource).getBitmap();
                            if (bmp != null && bmp.getByteCount() > 0x6400000) {
                                float scale = 1.048576E8f / bmp.getByteCount();
                                int ivW = imageView.getWidth();
                                int ivH = imageView.getHeight();
                                if (ivW > 0 && ivH > 0) {
                                    float s = Math.min(ivW / (float) bmp.getWidth(), ivH / (float) bmp.getHeight());
                                    if (s < scale) scale = s;
                                }
                                int w = Math.round(bmp.getWidth() * scale);
                                int h = Math.round(bmp.getHeight() * scale);
                                imageView.setImageBitmap(android.graphics.Bitmap.createScaledBitmap(
                                        bmp, Math.max(1, Math.min(w / 12, 512)),
                                        Math.max(1, Math.min(h / 12, 512)), true));
                                iconTextView.setVisibility(View.GONE);
                                return;
                            }
                        }
                        imageView.setImageDrawable(resource);
                        iconTextView.setVisibility(View.GONE);
                    }

                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                        if (tag.equals(iconTextView.getTag())) {
                            imageView.setImageDrawable(placeholder);
                            iconTextView.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onLoadStarted(@Nullable android.graphics.drawable.Drawable placeholder) {
                        super.onLoadStarted(placeholder);
                        if (!tag.equals(iconTextView.getTag())) return;
                        imageView.setImageDrawable(placeholder);
                        String letter;
                        try {
                            String t = item.getTitle() == null ? "" : item.getTitle();
                            String plain;
                            if (!t.contains("<b") && !t.contains("<s") && !t.contains("<f") && !t.contains("</")) {
                                plain = t;
                            } else {
                                plain = org.jsoup.Jsoup.parse(t).text();
                            }
                            letter = plain.substring(0, 1);
                        } catch (Exception e) {
                            letter = "H";
                        }
                        iconTextView.setText(HkPinyinUtil.getPinyin2(letter).toUpperCase());
                        iconTextView.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                        super.onLoadFailed(errorDrawable);
                        if (!tag.equals(iconTextView.getTag())) return;
                        int resId;
                        switch (position % 6) {
                            case 1: resId = R.drawable.shape_placeholder_radius_1; break;
                            case 2: resId = R.drawable.shape_placeholder_radius_2; break;
                            case 3: resId = R.drawable.shape_placeholder_radius_3; break;
                            case 4: resId = R.drawable.shape_placeholder_radius_4; break;
                            case 5: resId = R.drawable.shape_placeholder_radius_5; break;
                            default: resId = R.drawable.shape_placeholder_radius_0; break;
                        }
                        com.bumptech.glide.Glide.with(context).load(Integer.valueOf(resId)).circleCrop().into(imageView);
                    }
                });
    }

    /** 照抄原版 buildUrlListener + lambda$buildUrlListener$6（smali:9284/11693）。 */
    protected java.util.function.Consumer<String> buildUrlListener(RecyclerView.ViewHolder holder) {
        return url -> {
            try {
                int pos = holder.getAdapterPosition();
                if (pos >= 0 && pos < list.size() && onItemClickListener != null) {
                    onItemClickListener.onUrlClick(holder.itemView, pos, url);
                }
            } catch (Exception ignored) {
            }
        };
    }

    /**
     * 照抄原版 bindClick + lambda$bindClick$28/$29（smali:992/9870/9918，java 1974-1993）：
     * view 的 tag 存偏移量，点击时长按以实时 getAdapterPosition()+偏移回调。
     * 原版异常走 Timber.e，此处同 bindBgClick 静默吞；长按恒返回 true。
     */
    protected void bindClick(RecyclerView.ViewHolder holder, View view, int offset) {
        view.setTag(Integer.valueOf(offset));
        view.setOnClickListener(v -> {
            try {
                int pos = holder.getAdapterPosition();
                if (pos >= 0) {
                    int off = (Integer) v.getTag();
                    if (onItemClickListener != null) onItemClickListener.onClick(v, pos + off);
                }
            } catch (Exception ignored) {
            }
        });
        view.setOnLongClickListener(v -> {
            try {
                int pos = holder.getAdapterPosition();
                if (pos >= 0) {
                    int off = (Integer) v.getTag();
                    if (onItemClickListener != null) onItemClickListener.onLongClick(v, pos + off);
                }
            } catch (Exception ignored) {
            }
            return true;
        });
    }

    /** 照抄原版 bindCircleCropImageView(ImageView, TextView, ArticleList, int)（smali:756，java 1336）：getOptions 克隆后加 CircleCrop 再走 5 参 bindImageView。 */
    protected void bindCircleCropImageView(ImageView imageView, TextView iconTextView, HkItem item, int position) {
        com.bumptech.glide.request.RequestOptions options = (com.bumptech.glide.request.RequestOptions)
                getOptions(item.getPic()).clone().transform(new com.bumptech.glide.load.resource.bitmap.CircleCrop());
        bindImageView(imageView, iconTextView, item, position, options);
    }

    /** 照抄原版 bindCircleCropImageView(ImageView, ArticleList)（smali:797，java 1319-1323）：直载圆形图。 */
    protected void bindCircleCropImageView(ImageView imageView, HkItem item) {
        com.bumptech.glide.request.RequestOptions options = (com.bumptech.glide.request.RequestOptions)
                getOptions(item.getPic()).clone().transform(new com.bumptech.glide.load.resource.bitmap.CircleCrop());
        com.bumptech.glide.Glide.with(context).asDrawable()
                .load(HkGlideUtil.getGlideUrl(pageUrl, item.getPic()))
                .apply(options)
                .into(imageView);
    }

    /** 照抄原版 bindCircleCropImageView(DrawableTextView, ArticleList, int)（smali:864，java 1327）：拆出图与文字图标转四参版。 */
    protected void bindCircleCropImageView(DrawableTextView drawableTextView, HkItem item, int position) {
        bindCircleCropImageView(drawableTextView.getImageView(), drawableTextView.getIconTextView(), item, position);
    }

    /** 照抄原版 DisplayUtil.dpToPx(Context, float)（DisplayUtil.smali:16）：≤0 直转，否则 dp×density+0.5 取整。 */
    protected int dpToPx(float dp) {
        if (dp <= 0) return (int) dp;
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * 照抄原版 bindNoCropImageView（smali:9223，java 1331-1332）：先走一遍圆形裁剪版加载，
     * 再以无 transform 的克隆 options 走 5 参版覆盖（原版双加载行为照抄，后者为最终请求）。
     */
    protected void bindNoCropImageView(DrawableTextView drawableTextView, HkItem item, int position) {
        bindCircleCropImageView(drawableTextView.getImageView(), drawableTextView.getIconTextView(), item, position);
        com.bumptech.glide.request.RequestOptions options = (com.bumptech.glide.request.RequestOptions)
                getOptions(item.getPic()).clone();
        bindImageView(drawableTextView.getImageView(), drawableTextView.getIconTextView(), item, position, options);
    }

    /**
     * 照抄原版 bindImageViewWithNothing（smali:9050，java 1305-1315）：
     * 缩略图 = icon_load_failed 圆形裁剪；主请求 apply(getOptions(pic).placeholder(null))。
     */
    protected void bindImageViewWithNothing(ImageView imageView, HkItem item) {
        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> thumb =
                com.bumptech.glide.Glide.with(context).asDrawable()
                        .load(Integer.valueOf(R.drawable.icon_load_failed))
                        .circleCrop();
        com.bumptech.glide.request.RequestOptions options = (com.bumptech.glide.request.RequestOptions)
                getOptions(item.getPic()).placeholder((android.graphics.drawable.Drawable) null);
        com.bumptech.glide.Glide.with(context).asDrawable()
                .load(HkGlideUtil.getGlideUrl(pageUrl, item.getPic()))
                .thumbnail(thumb)
                .apply(options)
                .into(imageView);
    }

    /**
     * 照抄原版 bindCircleCropImageViewWithNothing（smali:881，java 1439-1450）：
     * 同上，但主请求 options 克隆后加 CircleCrop。
     */
    protected void bindCircleCropImageViewWithNothing(ImageView imageView, HkItem item) {
        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> thumb =
                com.bumptech.glide.Glide.with(context).asDrawable()
                        .load(Integer.valueOf(R.drawable.icon_load_failed))
                        .circleCrop();
        com.bumptech.glide.request.RequestOptions options = (com.bumptech.glide.request.RequestOptions)
                getOptions(item.getPic()).clone().placeholder((android.graphics.drawable.Drawable) null)
                        .transform(new com.bumptech.glide.load.resource.bitmap.CircleCrop());
        com.bumptech.glide.Glide.with(context).asDrawable()
                .load(HkGlideUtil.getGlideUrl(pageUrl, item.getPic()))
                .thumbnail(thumb)
                .apply(options)
                .into(imageView);
    }

    /** 照抄原版 bindLineExtra（smali:9143）：extra.lineVisible（缺省 true）决定底背景。 */
    protected void bindLineExtra(View view, HkItem item) {
        boolean lineVisible = !"false".equals(item.getExtra("lineVisible"));
        view.setBackgroundResource(lineVisible ? R.drawable.shape_bottom_split : R.drawable.ripple_trans2);
    }

    /** 照抄原版 setTextGravity（smali:11855）：extra.textAlign=left 时左对齐，否则居中；父容器同步。 */
    protected void setTextGravity(TextView textView, HkItem item) {
        if (textView.getParent() == null) return;
        int gravity = "left".equals(item.getExtra("textAlign")) ? 0x800013 : 0x11;
        textView.setGravity(gravity);
        android.view.ViewParent parent = textView.getParent();
        if (parent instanceof LinearLayout) {
            ((LinearLayout) parent).setGravity(gravity);
        } else if (parent instanceof RelativeLayout) {
            ((RelativeLayout) parent).setGravity(gravity);
        }
    }

    /** 对应原版 ScreenUtil.getScreenWidth(Activity)：屏幕宽度像素。 */
    protected int screenWidth() {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    /**
     * 照抄原版 onBindViewHolder 的 instanceof 分发（smali:13273，java 506+）。
     * 各 bindFor* 方法逐个按原版转写，未转写的类型随批次补齐。
     */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MyViewHolder) {
            // 衬底定版：footer 底换卡面（页级无条目标题，以规则名走色板兜底）
            holder.itemView.setBackground(HkCardFace.face(context, HkCardFace.paletteColorFor(ruleTitle)));
            // 照抄原版 lambda$onBindViewHolder$0：footer 点击以实时 adapterPosition 走 onLoadMore
            ((MyViewHolder) holder).footer.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos >= 0 && onItemClickListener != null) onItemClickListener.onLoadMore(v, pos);
            });
        } else if (holder instanceof HeaderHolder) {
            bindForHeaderHolder(holder, position);
        } else if (holder instanceof ToolsHolder) {
            bindForToolsHolder(holder, position);
        } else if (holder instanceof TextOneColHolder) {
            bindForTextOneColHolder(holder, position);
        } else if (holder instanceof LongTextHolder) {
            bindForLongTextHolder(holder, position);
        } else if (holder instanceof ChannelHolder) {
            bindForChannelHolder(holder, position);
        } else if (holder instanceof TextThreeHolder) {
            bindForTextThreeHolder(holder, position);
        } else if (holder instanceof TextFourHolder) {
            bindForTextFourHolder(holder, position);
        } else if (holder instanceof TextFiveHolder) {
            bindForTextFiveHolder(holder, position);
        } else if (holder instanceof MovieOneColHolder) {
            bindForMovieOneColHolder(holder, position);
        } else if (holder instanceof MovieOneBlurColHolder) {
            bindForMovieOneBlurColHolder(holder, position);
        } else if (holder instanceof PicOneHolder) {
            bindForPicOneHolder(holder, position);
        } else if (holder instanceof PicOneFullHolder) {
            bindForPicOneFullHolder(holder, position);
        } else if (holder instanceof PicOneCenterHolder) {
            bindForPicOneCenterHolder(holder, position);
        } else if (holder instanceof PicTwoHolder) {
            bindForPicTwoHolder(holder, position);
        } else if (holder instanceof PicThreeHolder) {
            bindForPicThreeHolder(holder, position);
        } else if (holder instanceof PicThreeSquareHolder) {
            bindForPicThreeSquareHolder(holder, position);
        } else if (holder instanceof PicTwoCardHolder) {
            bindForPicTwoCardHolder(holder, position);
        } else if (holder instanceof CardPic2Holder) {
            bindForCardPic2Holder(holder, position);
        } else if (holder instanceof CardPic22Holder) {
            bindForCardPic22Holder(holder, position);
        } else if (holder instanceof CardPic3Holder) {
            bindForCardPic3Holder(holder, position);
        } else if (holder instanceof IconFourHolder) {
            bindForIconFourHolder(holder, position);
        } else if (holder instanceof IconFourCardHolder) {
            bindForIconFourCardHolder(holder, position);
        } else if (holder instanceof IconFourRoundHolder) {
            bindForIconFourRoundHolder(holder, position);
        } else if (holder instanceof AvatarHolder) {
            bindForAvatarHolder(holder, position);
        } else if (holder instanceof IconFiveHolder) {
            bindForIconFiveHolder(holder, position);
        } else if (holder instanceof IconThreeFillHolder) {
            bindForIconThreeFillHolder(holder, position);
        } else if (holder instanceof IconThreeHolder) {
            bindForIconThreeHolder(holder, position);
        } else if (holder instanceof IconTwoHolder) {
            bindForIconTwoHolder(holder, position);
        } else if (holder instanceof IconTwoRoundHolder) {
            bindForIconTwoRoundHolder(holder, position);
        } else if (holder instanceof IconOneSearchHolder) {
            bindForIconOneSearchHolder(holder, position);
        } else if (holder instanceof FlexButtonHolder) {
            bindForFlexButtonHolder(holder, position);
        } else if (holder instanceof ScrollButtonHolder) {
            bindForScrollButtonHolder(holder, position);
        } else if (holder instanceof InputHolder) {
            bindForInputHolder(holder, position);
        } else if (holder instanceof RichTextHolder) {
            bindForRichTextHolder(holder, position);
        } else if (holder instanceof X5WebViewHolder) {
            bindForX5WebViewHolder(holder, position);
        }
    }

    /**
     * 照抄原版 bindForFlexButtonHolder（smali:2171，java 1038-1101）：按 poly 组重建按钮排；
     * 组标签（各条 type/title/pic/url/desc 拼接）未变则跳过；每钮 inflate item_article_flex_btn_item，
     * extra.backgroundColor 存在时以 GradientDrawable(圆角 8dp)+Ripple(grey_black) 覆盖底，异常/无则 ripple_gray_corners；
     * 点击/长按与 bindClick 同体（实时位置+钮下标）。
     */
    protected void bindForFlexButtonHolder(RecyclerView.ViewHolder holder, int position) {
        FlexButtonHolder h = (FlexButtonHolder) holder;
        FlexboxLayout flex = h.flexboxLayout;
        Integer g = polyPositionMap.get(Integer.valueOf(position));
        if (g == null) return;
        java.util.List<HkItem> group = polyList.get(g.intValue());
        String groupTag = groupTagOf(group);
        if (flex.getTag() != null && groupTag.equals(String.valueOf(flex.getTag()))) return;
        flex.setTag(groupTag);
        flex.removeAllViews();
        int m2 = com.fongmi.android.tv.utils.ResUtil.dp2px(2);
        if (group != null && !group.isEmpty()) {
            for (int i = 0; i < group.size(); i++) {
                HkItem child = group.get(i);
                View childView = android.view.LayoutInflater.from(context)
                        .inflate(R.layout.item_article_flex_btn_item, null);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                lp.setMargins(m2, 0, m2, 0);
                childView.setLayoutParams(lp);
                TextView tv = (TextView) childView.findViewById(R.id.item_chapter_jishu);
                HkTextViewUtils.setSpanText(tv, child.getTitle(), buildUrlListener(holder));
                String bgColor = child.getExtra("backgroundColor");
                boolean colored = false;
                if (bgColor != null && !bgColor.isEmpty()) {
                    try {
                        int color = android.graphics.Color.parseColor(bgColor);
                        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                        gd.setShape(0);
                        gd.setCornerRadius((float) com.fongmi.android.tv.utils.ResUtil.dp2px(8));
                        gd.setColor(color);
                        android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(
                                context.getResources().getColorStateList(R.color.grey_black), gd, null);
                        tv.setBackground(ripple);
                        colored = true;
                    } catch (Exception ignored) {
                    }
                }
                if (!colored) {
                    tv.setBackground(context.getResources().getDrawable(R.drawable.ripple_gray_corners));
                }
                bindClick(holder, childView, i);
                flex.addView(childView);
            }
        }
    }

    /**
     * 照抄原版 bindForScrollButtonHolder（smali:7387）：先装展开按钮点击；再按 poly 组重建横排钮；
     * 子钮 inflate item_article_header_item_empty（根即 TextView 本人），tag="下标@@@标题"，
     * extra.backgroundColor 着色成功时 tag 再尾加"@@@色串"；每钮恒设文字色 black_666 与 padding(10,3,10,3)；
     * 组末恢复 10 秒内由子点击/弹窗选择保存的横向滚动位。
     */
    protected void bindForScrollButtonHolder(RecyclerView.ViewHolder holder, int position) {
        ScrollButtonHolder h = (ScrollButtonHolder) holder;
        bindScrollButtonExpandClick(holder);
        Integer g = polyPositionMap.get(Integer.valueOf(position));
        if (g == null) return;
        java.util.List<HkItem> group = polyList.get(g.intValue());
        String groupTag = groupTagOf(group);
        if (h.linearLayout.getTag() != null && groupTag.equals(String.valueOf(h.linearLayout.getTag()))) return;
        h.linearLayout.setTag(groupTag);
        h.linearLayout.removeAllViews();
        int m10 = dpToPx(10f);
        int m3 = dpToPx(3f);
        int m2 = dpToPx(2f);
        int m7 = dpToPx(7f);
        if (group != null && !group.isEmpty()) {
            for (int i = 0; i < group.size(); i++) {
                HkItem child = group.get(i);
                TextView tv = (TextView) android.view.LayoutInflater.from(context)
                        .inflate(R.layout.item_article_header_item_empty, null);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(m7, 0, i == group.size() - 1 ? m2 * 2 : m2, 0);
                tv.setLayoutParams(lp);
                String title = child.getTitle();
                tv.setTag(i + "@@@" + title);
                HkTextViewUtils.setSpanText(tv, title, buildUrlListener(holder));
                String bgColor = child.getExtra("backgroundColor");
                boolean colored = false;
                if (bgColor != null && !bgColor.isEmpty()) {
                    try {
                        int color = android.graphics.Color.parseColor(bgColor);
                        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                        gd.setShape(0);
                        gd.setCornerRadius((float) com.fongmi.android.tv.utils.ResUtil.dp2px(8));
                        gd.setColor(color);
                        android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(
                                context.getResources().getColorStateList(R.color.grey_black), gd, null);
                        tv.setBackground(ripple);
                        tv.setTag(i + "@@@" + title + "@@@" + bgColor);
                        colored = true;
                    } catch (Exception ignored) {
                    }
                }
                if (!colored) {
                    tv.setBackground(context.getResources().getDrawable(R.drawable.ripple_gray_corners));
                }
                tv.setTextColor(context.getResources().getColor(R.color.black_666));
                tv.setPadding(m10, m3, m10, m3);
                h.linearLayout.addView(tv);
                tv.setOnClickListener(v -> {
                    try {
                        h.horizontalScrollView.setTag(R.id.res_header_scroll, new android.util.Pair<>(
                                Integer.valueOf(h.horizontalScrollView.getScrollX()),
                                Long.valueOf(System.currentTimeMillis())));
                        int pos = holder.getAdapterPosition();
                        if (pos >= 0) {
                            int idx = Integer.parseInt(String.valueOf(v.getTag()).split("@@@")[0]);
                            if (onItemClickListener != null) onItemClickListener.onClick(v, pos + idx);
                        }
                    } catch (Exception ignored) {
                    }
                });
                tv.setOnLongClickListener(v -> {
                    try {
                        int pos = holder.getAdapterPosition();
                        if (pos >= 0) {
                            int idx = Integer.parseInt(String.valueOf(v.getTag()).split("@@@")[0]);
                            if (onItemClickListener != null) onItemClickListener.onLongClick(v, pos + idx);
                        }
                    } catch (Exception ignored) {
                    }
                    return true;
                });
                if (tv.getParent() != null) {
                    h.linearLayout.removeView(tv);
                    h.linearLayout.addView(tv);
                }
            }
        }
        Object saved = h.horizontalScrollView.getTag(R.id.res_header_scroll);
        if (saved instanceof android.util.Pair) {
            android.util.Pair<?, ?> pair = (android.util.Pair<?, ?>) saved;
            if (pair.first instanceof Integer && pair.second instanceof Long
                    && System.currentTimeMillis() - ((Long) pair.second).longValue() < 10000) {
                h.horizontalScrollView.scrollTo(((Integer) pair.first).intValue(), 0);
                h.horizontalScrollView.setTag(R.id.res_header_scroll, null);
            }
        }
    }

    /**
     * 照抄原版 bindScrollButtonExpandClick 与其展开 lambda（smali:11460+）：
     * 逐子钮读 tag 拼出选项文案（深色底的钮去引号标记后取纯文本），弹居中列表（标题"请选择"，3 列）；
     * 选中后按"前一条的 left"存位并滚动，再 performClick 该子钮；异常走居中短 Toast"出错：…"。
     * 弹窗本体原版是 XPopup 的 CustomCenterRecyclerViewPopup，星辰无 XPopup，用等价 HkCenterListPopup。
     */
    protected void bindScrollButtonExpandClick(RecyclerView.ViewHolder holder) {
        ScrollButtonHolder h = (ScrollButtonHolder) holder;
        h.expandView.setOnClickListener(v -> {
            if (context == null || h.linearLayout.getChildCount() <= 0) return;
            try {
                java.util.List<String> labels = new java.util.ArrayList<>();
                for (int i = 0; i < h.linearLayout.getChildCount(); i++) {
                    String tag = String.valueOf(h.linearLayout.getChildAt(i).getTag());
                    if (tag.endsWith("@@@")) {
                        labels.add("");
                        continue;
                    }
                    String[] parts = tag.split("@@@");
                    if (parts.length == 3) {
                        try {
                            int color = android.graphics.Color.parseColor(parts[2]);
                            if (isColorDeep(color)) {
                                String clean = parts[1].replace("““", "").replace("””", "")
                                        .replace("‘‘", "").replace("’’", "");
                                labels.add(org.jsoup.Jsoup.parse(clean).text());
                            } else {
                                labels.add(parts[1]);
                            }
                        } catch (Exception e) {
                            labels.add(parts[1]);
                        }
                    } else {
                        labels.add(parts[1]);
                    }
                }
                new HkCenterListPopup(context).withTitle("请选择").with(labels, 3,
                        new HkCenterListPopup.ClickListener() {
                            @Override
                            public void click(String text, int pos) {
                                try {
                                    if (context == null || h.linearLayout == null
                                            || pos >= h.linearLayout.getChildCount()) return;
                                    View child = h.linearLayout.getChildAt(pos);
                                    int x = pos == 0 ? 0 : h.linearLayout.getChildAt(pos - 1).getLeft();
                                    h.horizontalScrollView.setTag(R.id.res_header_scroll, new android.util.Pair<>(
                                            Integer.valueOf(x), Long.valueOf(System.currentTimeMillis())));
                                    h.horizontalScrollView.scrollTo(x, 0);
                                    child.performClick();
                                } catch (Exception e) {
                                    toastCenter("出错：" + e.getMessage());
                                }
                            }

                            @Override
                            public void onLongClick(String text, int pos) {
                            }
                        }).show();
            } catch (Exception e) {
                toastCenter("出错：" + e.getMessage());
            }
        });
    }

    /** 照抄原版 isColorDeep（smali:9534）：相对亮度补数 ≥0.5 判深色。 */
    protected boolean isColorDeep(int color) {
        double darkness = 1.0 - (0.299 * android.graphics.Color.red(color)
                + 0.587 * android.graphics.Color.green(color)
                + 0.114 * android.graphics.Color.blue(color)) / 255.0;
        return darkness >= 0.5;
    }

    /** 等价原版 ToastMgr.shortCenter：居中短 Toast。 */
    protected void toastCenter(String msg) {
        com.fongmi.android.tv.utils.ToastUtil.show(context, msg, android.widget.Toast.LENGTH_SHORT);
    }

    /**
     * 照抄原版 bindForHeaderHolder（smali:2492）：同标题同旧位置的 HeaderHolder 已登记则跳过；
     * 标题按 "&" 拆分类名、url 按 "&" 拆分类链接，逐个 inflate item_article_header_item；
     * 选中项 = 条目 pic（空则首项）；选中态样式规则见 applyHeaderTabStyle；展开钮走 onHeaderExpandClick。
     */
    protected void bindForHeaderHolder(RecyclerView.ViewHolder holder, int position) {
        HeaderHolder h = (HeaderHolder) holder;
        HkItem item = list.get(position);
        String title = item.getTitle();
        HeaderHolder existing = headerHolders.get(title);
        if (existing != null && existing.getOldPosition() == position) return;
        headerHolders.put(title, h);
        h.linearLayout.removeAllViews();
        String[] titles = title.split("&");
        String[] urls = item.getUrl().split("&");
        int m10 = com.fongmi.android.tv.utils.ResUtil.dp2px(10);
        int m3 = com.fongmi.android.tv.utils.ResUtil.dp2px(3);
        int m5 = com.fongmi.android.tv.utils.ResUtil.dp2px(5);
        String selected = item.getPic();
        if (android.text.TextUtils.isEmpty(selected)) selected = titles[0];
        final String sel = selected;
        h.expandView.setOnClickListener(v -> onHeaderExpandClick(titles, h));
        for (int i = 0; i < titles.length; i++) {
            View childView = View.inflate(context, R.layout.item_article_header_item, null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i == titles.length - 1) lp.setMargins(m5, 0, m5, 0);
            else lp.setMargins(m5, 0, 0, 0);
            childView.setLayoutParams(lp);
            TextView tv = (TextView) childView.findViewById(R.id.res_header_item_text);
            tv.setTag(i + "@@@" + titles[i]);
            tv.setText(titles[i]);
            applyHeaderTabStyle(tv, titles[i], sel, item);
            tv.setPadding(m10, m3, m10, m3);
            final int idx = i;
            tv.setOnClickListener(v -> onHeaderItemClick(h, urls, idx, titles, v));
            h.linearLayout.addView(childView);
        }
    }

    /** 照抄原版选中态判定（bindForHeaderHolder 与 updateHeaderHolder 共用同一段）：选中且（规则 url 不含 fyAll，或 firstHeader 与条目 desc 相同）才给选中底+绿字，否则默认底+black_666。 */
    private void applyHeaderTabStyle(TextView tv, String tabTitle, String selected, HkItem item) {
        boolean useSelected = tabTitle.equals(selected)
                && (!ruleUrl.contains("fyAll") || ruleFirstHeader.equals(item.getDesc()));
        if (useSelected) {
            // 液态玻璃：选中态同 P1（奶油渐变胶囊＋金下划线＋金字 #8A6400）
            tv.setBackground(context.getResources().getDrawable(R.drawable.hk_chip_selected));
            tv.setTextColor(0xFF8A6400);
        } else {
            tv.setBackground(context.getResources().getDrawable(R.drawable.tab_item_default));
            tv.setTextColor(context.getResources().getColor(R.color.black_666));
        }
    }

    /**
     * 照抄原版 lambda$bindForHeaderHolder$12（子钮点击）：先 onClassClick(链接, 当前条目 desc)，
     * 再把条目 pic 改为所点分类名；随后刷新相关 HeaderHolder 的选中态——
     * 非 fyAll 或 firstHeader 与当前条目 desc 相同只刷当前行，否则先刷"前 4 条里 desc==firstHeader 且 type==header"那行再刷当前行。
     */
    private void onHeaderItemClick(HeaderHolder holder, String[] urls, int idx, String[] titles, View view) {
        com.github.catvod.crawler.SpiderDebug.log("hk", "headerClick entry idx=%d title=%s url=%s listener=%s",
                idx, idx >= 0 && idx < titles.length ? titles[idx] : "",
                idx >= 0 && idx < urls.length ? urls[idx] : "", onItemClickListener != null);
        if (onItemClickListener == null) return;
        int pos = holder.getAdapterPosition();
        if (pos < 0 || pos >= list.size()) return;
        String firstHeader = ruleFirstHeader;
        HkItem cur = list.get(pos);
        onItemClickListener.onClassClick(view, urls[idx], cur.getDesc());
        cur.setPic(titles[idx]);
        int found = 0;
        for (int k = 0; k < 4; k++) {
            if (k >= list.size()) break;
            if (firstHeader != null && firstHeader.equals(list.get(k).getDesc())
                    && "header".equals(list.get(k).getType())) {
                found = k;
                break;
            }
        }
        if (!ruleUrl.contains("fyAll") || firstHeader.equals(cur.getDesc())) {
            updateHeaderHolder(headerHolders.get(cur.getTitle()), cur);
        } else {
            HkItem foundItem = list.get(found);
            updateHeaderHolder(headerHolders.get(foundItem.getTitle()), foundItem);
            updateHeaderHolder(headerHolders.get(cur.getTitle()), cur);
        }
    }

    /** 照抄原版 updateHeaderHolder（smali:11927）：不重建，只按条目当前 pic 逐个重刷已有子钮的选中态。 */
    private void updateHeaderHolder(HeaderHolder holder, HkItem item) {
        if (holder == null || item == null || item.getTitle() == null) return;
        String[] titles = item.getTitle().split("&");
        String selected = item.getPic();
        if (android.text.TextUtils.isEmpty(selected)) selected = titles[0];
        for (int i = 0; i < titles.length; i++) {
            View child = holder.linearLayout.getChildAt(i);
            if (child == null) break;
            View text = child.findViewById(R.id.res_header_item_text);
            if (!(text instanceof TextView)) continue;
            applyHeaderTabStyle((TextView) text, titles[i], selected, item);
        }
    }

    /**
     * 照抄原版 lambda$bindForHeaderHolder$11 + ArticleListAdapter$3（展开钮点击）：
     * 弹居中列表（标题"请选择分类"，3 列，选项=分类名全量）；选中先 performClick 对应子钮，
     * 再按"前一条 left"平滑滚动；onLongClick 空实现；异常走居中短 Toast"出错：…"。
     */
    private void onHeaderExpandClick(String[] titles, HeaderHolder h) {
        if (context == null) return;
        try {
            new HkCenterListPopup(context).withTitle("请选择分类").with(
                    new ArrayList<>(java.util.Arrays.asList(titles)), 3,
                    new HkCenterListPopup.ClickListener() {
                        @Override
                        public void click(String text, int pos) {
                            try {
                                if (context == null || h.linearLayout == null
                                        || pos >= h.linearLayout.getChildCount()) return;
                                h.linearLayout.getChildAt(pos).performClick();
                                int x = pos == 0 ? 0 : h.linearLayout.getChildAt(pos - 1).getLeft();
                                h.horizontalScrollView.smoothScrollTo(x, 0);
                            } catch (Exception e) {
                                toastCenter("出错：" + e.getMessage());
                            }
                        }

                        @Override
                        public void onLongClick(String text, int pos) {
                        }
                    }).show();
        } catch (Exception e) {
            toastCenter("出错：" + e.getMessage());
        }
    }

    /**
     * 照抄原版 bindForInputHolder（smali:4430）：hint=desc（空则"请输入内容"），提交钮文=title（空则"确定"）；
     * extra 给出 InputExtra（onChange/titleVisible/highlight/type/height/defaultValue）：
     * highlight 时钮文变"&lt;/&gt;" 且强制 titleVisible；titleVisible=false 时钮与分隔线 INVISIBLE；
     * type=number/textarea/password 决定 inputType（2 / 0x20001 / 0x81+密码变换），height 决定 textarea 行数（-1=自适应、0→3）；
     * 旧 TextWatcher 存 divider 的 tag 里、重绑先摘除；默认值灌入后立即触发一次 onInputChange。
     * extra 在星辰是 Map，转写以等价键读取（titleVisible 缺省 true、height 缺省 0）。
     */
    protected void bindForInputHolder(RecyclerView.ViewHolder holder, int position) {
        InputHolder h = (InputHolder) holder;
        HkItem item = list.get(position);
        if (item.getDesc() != null && !item.getDesc().isEmpty()) h.edit.setHint(item.getDesc());
        else h.edit.setHint("请输入内容");
        if (item.getTitle() != null && !item.getTitle().isEmpty()) h.search.setText(item.getTitle());
        else h.search.setText("确定");
        boolean titleVisible = true;
        int inputType = 1;
        int heightLines = 3;
        String onChange = null;
        String[] state = inputStateOf(item);
        if (state != null) {
            onChange = item.getExtra("onChange");
            if (onChange != null && onChange.isEmpty()) onChange = null;
            titleVisible = !"false".equals(item.getExtra("titleVisible"));
            boolean highlight = "true".equals(item.getExtra("highlight"));
            if (highlight) {
                h.search.setText("</>");
                titleVisible = true;
            }
            if (!titleVisible) {
                h.search.setText("");
                h.search.setPadding(0, 0, 0, 0);
                h.search.setVisibility(View.INVISIBLE);
                h.divider.setVisibility(View.INVISIBLE);
            }
            String type = item.getExtra("type");
            if ("number".equals(type)) {
                inputType = 2;
            } else if ("textarea".equals(type)) {
                inputType = 0x20001;
            } else if ("password".equals(type)) {
                h.edit.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
                inputType = 0x81;
            } else {
                inputType = 1;
            }
            int height = 0;
            try {
                height = Integer.parseInt(item.getExtra("height"));
            } catch (Exception ignored) {
            }
            if (height == -1) heightLines = -1;
            else if (height > 0) heightLines = height;
            else heightLines = 3;
        }
        Object oldWatcher = h.divider.getTag();
        if (oldWatcher instanceof android.text.TextWatcher) {
            h.edit.removeTextChangedListener((android.text.TextWatcher) oldWatcher);
        }
        if (titleVisible) {
            int m15 = com.fongmi.android.tv.utils.ResUtil.dp2px(15);
            h.search.setPadding(m15, 0, m15, 0);
            h.divider.setVisibility(View.VISIBLE);
            h.search.setVisibility(View.VISIBLE);
        }
        if (inputType == 0x20001) {
            h.edit.setMaxLines(Integer.MAX_VALUE);
            h.edit.setMinLines(1);
            h.edit.setSingleLine(false);
            h.edit.setGravity(0x800003);
            h.edit.setImeOptions(1);
            h.edit.setPadding(com.fongmi.android.tv.utils.ResUtil.dp2px(5),
                    com.fongmi.android.tv.utils.ResUtil.dp2px(10),
                    com.fongmi.android.tv.utils.ResUtil.dp2px(30),
                    com.fongmi.android.tv.utils.ResUtil.dp2px(10));
        } else {
            h.edit.setMaxLines(1);
            h.edit.setMinLines(1);
            h.edit.setSingleLine();
            h.edit.setImeOptions(6);
            h.edit.setGravity(0x10);
            h.edit.setPadding(com.fongmi.android.tv.utils.ResUtil.dp2px(5), 0,
                    com.fongmi.android.tv.utils.ResUtil.dp2px(30), 0);
        }
        int bgHeight = com.fongmi.android.tv.utils.ResUtil.dp2px(inputType == 0x20001 ? heightLines * 44 : 44);
        h.edit.setInputType(inputType);
        ViewGroup.LayoutParams bgRaw = h.pop_input_edit_bg.getLayoutParams();
        LinearLayout.LayoutParams bgLp = bgRaw instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) bgRaw
                : new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (inputType == 0x20001 && heightLines == -1) bgHeight = -2;
        bgLp.height = bgHeight;
        h.pop_input_edit_bg.setLayoutParams(bgLp);
        android.text.TextWatcher watcher = new InputWatcher(h, position, inputType, item, onChange);
        h.pop_input_edit_bg.setTag(Integer.valueOf(position));
        h.divider.setTag(watcher);
        h.edit.addTextChangedListener(watcher);
        String defaultValue = state == null ? null : state[0];
        if (defaultValue != null && !defaultValue.isEmpty()) {
            h.edit.setText(defaultValue);
            if (onChange != null && !onChange.isEmpty()) onInputChange(item, h.edit, defaultValue, onChange);
        } else {
            h.edit.setText("");
            if (onChange != null && !onChange.isEmpty()) onInputChange(item, h.edit, "", onChange);
        }
        if (inputType == 0x20001) {
            h.clearBtn.setVisibility(View.GONE);
            h.edit.setOnEditorActionListener(null);
        } else {
            setOnEnterClickListener(h.edit, s -> h.search.performClick());
            if (defaultValue != null && !defaultValue.isEmpty()) h.clearBtn.setVisibility(View.VISIBLE);
            else h.clearBtn.setVisibility(View.INVISIBLE);
            h.clearBtn.setOnClickListener(v -> {
                h.edit.setText("");
                String[] st = inputStateOf(item);
                if (st != null) st[0] = "";
            });
        }
        h.search.setTag(h.edit);
        h.search.setOnClickListener(v -> {
            try {
                int pos = h.getAdapterPosition();
                if (pos >= 0 && pos < list.size() && onItemClickListener != null) {
                    onItemClickListener.onClick(v, pos);
                }
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * 照抄原版 onInputChange（smali:11800）：onChange 为空不跑；与上次文本相同不跑（lastText 去重）；
     * 实际求值丢后台线程（原版 HeavyTaskUtil.executeNewTask → JSEngine.evalJS(getMyRule+onChange, text)，
     * 转写侧经 inputEvalListener 交宿主引擎）。
     */
    protected void onInputChange(HkItem item, View view, String text, String onChange) {
        if (onChange == null || onChange.isEmpty()) return;
        String[] st = inputStateOf(item);
        if (st != null) {
            if (text == null ? st[1] == null : text.equals(st[1])) return;
            st[1] = text;
        }
        final String js = onChange;
        final String value = text;
        com.fongmi.android.tv.utils.Task.execute(() -> {
            if (inputEvalListener != null) inputEvalListener.evalInput(js, value);
        });
    }

    /** 照抄原版 ViewTool.setOnEnterClickListener：IME 的搜索/前往/发送/完成或回车键按下时把当前文本交给 consumer。 */
    protected void setOnEnterClickListener(EditText edit, java.util.function.Consumer<String> consumer) {
        edit.setOnEditorActionListener((v, actionId, event) -> {
            boolean matched = actionId == 3 || actionId == 2 || actionId == 4 || actionId == 6
                    || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER);
            if (!matched) return false;
            if (event == null || event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                CharSequence text = v.getText();
                consumer.accept(text == null ? "" : text.toString());
            }
            return true;
        });
    }

    /**
     * 照抄原版 ArticleListAdapter$1（输入框 TextWatcher）：文本变化先刷新建议票据；
     * 位置标签对不上直接返回；非空且非 textarea 时显示清除钮，title=="搜索" 且普通文本类型时拉远程建议词；
     * 文本为空时清建议排；最后走 onInputChange 并把当前文本写回 defaultValue。
     */
    private class InputWatcher implements android.text.TextWatcher {
        private final InputHolder holder;
        private final int position;
        private final int finalInputType;
        private final HkItem item;
        private final String finalOnChange;

        InputWatcher(InputHolder holder, int position, int finalInputType, HkItem item, String finalOnChange) {
            this.holder = holder;
            this.position = position;
            this.finalInputType = finalInputType;
            this.item = item;
            this.finalOnChange = finalOnChange;
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
            inputSuggestTicket = System.currentTimeMillis();
            Object tag = holder.pop_input_edit_bg.getTag();
            if (!(tag instanceof Integer) || ((Integer) tag).intValue() != position) return;
            if (s != null && !s.toString().isEmpty() && finalInputType != 0x20001) {
                holder.clearBtn.setVisibility(View.VISIBLE);
                if (finalInputType == 1 && "搜索".equals(item.getTitle()) && suggestLoader != null) {
                    final long ticket = inputSuggestTicket;
                    suggestLoader.loadFromRemote(s.toString(),
                            list -> new android.os.Handler(android.os.Looper.getMainLooper())
                                    .post(() -> applyInputSuggestions(ticket, holder, list)));
                }
            } else {
                holder.clearBtn.setVisibility(finalInputType != 0x20001 ? View.INVISIBLE : View.GONE);
                if (holder.search_suggest.getChildCount() > 0) holder.search_suggest.removeAllViews();
            }
            String text = s == null ? null : s.toString();
            if (finalOnChange != null && !finalOnChange.isEmpty()) {
                onInputChange(item, holder.edit, text, finalOnChange);
            }
            String[] st = inputStateOf(item);
            if (st != null) st[0] = text;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    /**
     * 照抄原版 lambda$afterTextChanged$1：票据过期丢弃；建议排滚回起点后按列表重建
     * item_input_suggestion 芯片（点芯片把文案灌回输入框）。
     */
    private void applyInputSuggestions(long ticket, InputHolder holder, List<String> suggestions) {
        if (ticket != inputSuggestTicket) return;
        if (suggestions == null) return;
        android.widget.HorizontalScrollView scroller =
                (android.widget.HorizontalScrollView) holder.search_suggest.getParent();
        scroller.scrollTo(0, 0);
        holder.search_suggest.removeAllViews();
        int m10 = com.fongmi.android.tv.utils.ResUtil.dp2px(10);
        int m3 = com.fongmi.android.tv.utils.ResUtil.dp2px(3);
        int m5 = com.fongmi.android.tv.utils.ResUtil.dp2px(5);
        for (int i = 0; i < suggestions.size(); i++) {
            View chip = View.inflate(context, R.layout.item_input_suggestion, null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i == suggestions.size() - 1) lp.setMargins(0, 0, 0, m10);
            else lp.setMargins(0, 0, m5, m10);
            chip.setLayoutParams(lp);
            TextView tv = (TextView) chip.findViewById(R.id.res_header_item_text);
            String suggestion = suggestions.get(i);
            tv.setText(suggestion);
            tv.setTag(suggestion);
            tv.setTextColor(context.getResources().getColor(R.color.black_666));
            tv.setPadding(m10, m3, m10, m3);
            holder.search_suggest.addView(chip);
            chip.setOnClickListener(v -> {
                try {
                    holder.edit.setText((String) v.getTag());
                } catch (Exception ignored) {
                }
            });
        }
    }

    /**
     * 照抄原版 bindForRichTextHolder（smali:7154）：extra 给出 RichTextExtra（textSize 缺省 16、lineSpacing 缺省 10、tc 缺省 0xFF222222）；
     * lineSpacing 负数钳 0，行距 = dpToPx(lineSpacing)×1.0f，字号以 DIP 设入，文字色 tc 直设；
     * 适配器 lineHeight 首算 = 控件行高 + dpToPx(lineSpacing)/2。正文走 zzhoujay RichText：
     * cache(all) + singleLoad(false) + sync(true) + OkHttpImageDownloader + 图片/链接点击 + 占位/错误图（浅灰块，宽=控件宽、高=宽/2）。
     * 点击防抖：图片点击时给标题打毫秒 tag，正文点击在 100ms 内吞掉（lambda$22）。
     */
    protected void bindForRichTextHolder(RecyclerView.ViewHolder holder, int position) {
        RichTextHolder h = (RichTextHolder) holder;
        HkItem item = list.get(position);
        String title = item.getTitle();
        if (title == null || title.isEmpty()) title = "";
        h.title.setOnLongClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos >= 0 && pos < list.size() && onItemClickListener != null) {
                onItemClickListener.onLongClick(v, pos);
            }
            return true;
        });
        h.title.setOnClickListener(v -> {
            Object tag = v.getTag();
            if (tag instanceof Long && System.currentTimeMillis() - ((Long) tag).longValue() < 100) return;
            int pos = h.getAdapterPosition();
            if (pos >= 0 && pos < list.size() && onItemClickListener != null) {
                onItemClickListener.onClick(v, pos);
            }
        });
        int lineSpacing = 10;
        int textSize = 16;
        int tc = 0xff222222;
        if (!item.getExtra().isEmpty()) {
            lineSpacing = parseExtraInt(item.getExtra("lineSpacing"), 10);
            textSize = parseExtraInt(item.getExtra("textSize"), 16);
            tc = parseExtraInt(item.getExtra("tc"), 0xff222222);
        }
        if (lineSpacing < 0) lineSpacing = 0;
        h.title.setLineSpacing((float) com.fongmi.android.tv.utils.ResUtil.dp2px(lineSpacing), 1.0f);
        h.title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, (float) textSize);
        h.title.setTextColor(tc);
        if (this.lineHeight <= 0) {
            this.lineHeight = h.title.getLineHeight()
                    + com.fongmi.android.tv.utils.ResUtil.dp2px(lineSpacing) / 2;
        }
        com.zzhoujay.richtext.RichText.from(title)
                .cache(com.zzhoujay.richtext.CacheType.all)
                .singleLoad(false)
                .sync(true)
                .imageDownloader(new HkOkHttpImageDownloader())
                .imageClick((urls, pos) -> {
                    h.title.setTag(Long.valueOf(System.currentTimeMillis()));
                    if (urls == null || urls.isEmpty() || pos < 0 || pos >= urls.size()) return;
                    if (richTextActionListener != null) richTextActionListener.onImageClick(urls, pos);
                })
                .urlClick(url -> onRichUrlClick(h, position, url))
                .placeHolder((imageHolder, config, textView) -> richPlaceholder(textView))
                .errorImage((imageHolder, config, textView) -> richPlaceholder(textView))
                .into(h.title);
    }

    /** 照抄原版 lambda$bindForRichTextHolder$25/$26：占位与错误图同体——0xFFF7F7F7 色块，bounds=(0,0,控件宽,控件宽/2)。 */
    private android.graphics.drawable.Drawable richPlaceholder(TextView textView) {
        android.graphics.drawable.ColorDrawable drawable =
                new android.graphics.drawable.ColorDrawable(0xfff7f7f7);
        int width = textView.getWidth();
        drawable.setBounds(0, 0, width, width / 2);
        return drawable;
    }

    /**
     * 照抄原版 lambda$bindForRichTextHolder$24：空链接吞掉（返回 true）；
     * code:// 先剥前缀，海阔视界文案过宿主导入检查命中则止步，否则 onCodeClick（传剥前缀未 trim 的原文）；
     * 其余走 onUrlClick；恒返回 true。
     */
    private boolean onRichUrlClick(RichTextHolder h, int position, String url) {
        if (url == null || url.isEmpty()) return true;
        if (url.startsWith("code://")) {
            String code = url.substring("code://".length());
            String trimmed = code.trim();
            if (trimmed.startsWith("海阔视界") && richTextActionListener != null
                    && richTextActionListener.checkCodeImport(trimmed)) {
                return true;
            }
            if (onItemClickListener != null) onItemClickListener.onCodeClick(h.title, position, code);
            return true;
        }
        if (onItemClickListener != null) onItemClickListener.onUrlClick(h.title, position, url);
        return true;
    }

    /**
     * 照抄原版 bindForX5WebViewHolder（smali:8761）：无 webViewHolder 或其模式为 FLOAT 时只清容器；
     * 否则按条目 desc 里的高度规则（getHeightByExtra）定行高并回写 holder，
     * 容器为空时把 WebView 从旧父级摘下挂进来（已有子视图则不动）。
     */
    protected void bindForX5WebViewHolder(RecyclerView.ViewHolder holder, int position) {
        X5WebViewHolder h = (X5WebViewHolder) holder;
        if (webViewHolder != null && webViewHolder.getMode() != HkWebViewHolder.Mode.FLOAT) {
            ViewGroup.LayoutParams rawLp = h.resultBg.getLayoutParams();
            androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                    rawLp instanceof androidx.recyclerview.widget.GridLayoutManager.LayoutParams
                            ? (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) rawLp
                            : new androidx.recyclerview.widget.GridLayoutManager.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int heightDp = HkWebViewHolder.getHeightByExtra(context, webViewHolder.getUrl(),
                    list.get(position).getDesc());
            lp.height = dpToPx((float) heightDp);
            webViewHolder.setHeight(lp.height);
            h.resultBg.setLayoutParams(lp);
            if (webViewHolder.getWebView() != null && h.resultBg.getChildCount() <= 0) {
                if (webViewHolder.getWebView().getParent() instanceof ViewGroup) {
                    ((ViewGroup) webViewHolder.getWebView().getParent())
                            .removeView(webViewHolder.getWebView());
                }
                h.resultBg.addView(webViewHolder.getWebView());
            }
            return;
        }
        if (h.resultBg.getChildCount() > 0) h.resultBg.removeAllViews();
    }

    private int parseExtraInt(String value, int fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** poly 组标签：等价原版 fastjson 整组序列化后的比较串（字段拼接即可达到同等判变效果）。 */
    protected String groupTagOf(java.util.List<HkItem> group) {
        if (group == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (HkItem it : group) {
            if (it == null) {
                sb.append("~null");
                continue;
            }
            sb.append('~').append(it.getType()).append('|').append(it.getTitle()).append('|')
                    .append(it.getPic()).append('|').append(it.getUrl()).append('|').append(it.getDesc());
        }
        return sb.toString();
    }

    /** 照抄原版 bindForIconThreeHolder（smali:4051，java 2158-2166）：标题 Html 直入内联控件，图走 3 参版。 */
    protected void bindForIconThreeHolder(RecyclerView.ViewHolder holder, int position) {
        IconThreeHolder h = (IconThreeHolder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int m5 = com.fongmi.android.tv.utils.ResUtil.dp2px(5);
        lp.width = (screenWidth() - m5 * 8) / 3;
        lp.setMargins(0, m5 * 3, 0, m5 * 2);
        h.resultBg.setLayoutParams(lp);
        HkItem item = list.get(position);
        applyCardFace(h.resultBg, item);
        String title = item.getTitle() == null ? "" : item.getTitle();
        h.resultBg.setText(android.text.Html.fromHtml(title));
        bindImageView(h.resultBg.getImageView(), item, position);
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForIconTwoHolder（smali:4178，java 1866-1875）。 */
    protected void bindForIconTwoHolder(RecyclerView.ViewHolder holder, int position) {
        IconTwoHolder h = (IconTwoHolder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int m5 = com.fongmi.android.tv.utils.ResUtil.dp2px(5);
        int cols = 60 / (isLand() ? HkColType.ICON_2.landSpanCount : HkColType.ICON_2.spanCount);
        lp.width = (screenWidth() - m5 * 2 * (cols + 1)) / cols;
        lp.setMargins(0, m5, 0, m5);
        h.resultBg.setLayoutParams(lp);
        HkItem item = list.get(position);
        applyCardFace(h.clickBg, item);
        HkTextViewUtils.setSpanText(h.textView, item.getTitle());
        bindImageViewWithNothing(h.imageView, item);
        bindBgClick(holder, h.clickBg);
    }

    /**
     * 照抄原版 bindForIconTwoRoundHolder（smali:4304，java 1879-1888）：
     * 列数横屏取 ICON_2_ROUND 的 landSpanCount、竖屏却取 ICON_2 的 spanCount（原版笔误照抄）。
     */
    protected void bindForIconTwoRoundHolder(RecyclerView.ViewHolder holder, int position) {
        IconTwoRoundHolder h = (IconTwoRoundHolder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int m5 = com.fongmi.android.tv.utils.ResUtil.dp2px(5);
        int cols = 60 / (isLand() ? HkColType.ICON_2_ROUND.landSpanCount : HkColType.ICON_2.spanCount);
        lp.width = (screenWidth() - m5 * 2 * (cols + 1)) / cols;
        lp.setMargins(0, m5, 0, m5);
        h.resultBg.setLayoutParams(lp);
        HkItem item = list.get(position);
        applyCardFace(h.clickBg, item);
        HkTextViewUtils.setSpanText(h.textView, item.getTitle());
        bindCircleCropImageViewWithNothing(h.imageView, item);
        bindBgClick(holder, h.clickBg);
    }

    /**
     * 照抄原版 bindForIconOneSearchHolder（smali:3825，java 1892-1915）：标题纯文本；
     * 无图则隐藏头像；尾部图标点击 = 申请相机权限后以请求码 0x961 打开扫码页
     * （原版 MyCaptureActivity，此处对应星辰 ScanActivity；拒绝时原版 Toast“没有相机权限！”——
     * 由宿主 onRequestPermissionsResult(0x961) 承接，见 Fragment 批）。
     */
    protected void bindForIconOneSearchHolder(RecyclerView.ViewHolder holder, int position) {
        IconOneSearchHolder h = (IconOneSearchHolder) holder;
        HkItem item = list.get(position);
        applyCardFace(h.clickBg, item);
        h.textView.setText(item.getTitle());
        if (item.getPic() == null || item.getPic().isEmpty()) {
            h.imageView.setVisibility(View.GONE);
        } else {
            h.imageView.setVisibility(View.VISIBLE);
            bindCircleCropImageView(h.imageView, item);
        }
        h.imageViewEnd.setOnClickListener(v -> {
            android.app.Activity act = activity();
            if (act == null) return;
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                act.startActivityForResult(new android.content.Intent(act,
                        com.fongmi.android.tv.ui.activity.ScanActivity.class), 0x961);
            } else {
                androidx.core.app.ActivityCompat.requestPermissions(act,
                        new String[]{android.Manifest.permission.CAMERA}, 0x961);
            }
        });
        bindBgClick(holder, h.clickBg);
    }

    /**
     * 照抄原版 bindForIconThreeFillHolder（smali:3917，java 2170-2183）：三列定宽；
     * 标题走无监听版 setSpanText；ICON_3_FILL 走直图版、其余（ROUND_FILL）走圆形版；点击绑在 resultBg2 上。
     */
    protected void bindForIconThreeFillHolder(RecyclerView.ViewHolder holder, int position) {
        IconThreeFillHolder h = (IconThreeFillHolder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int m5 = com.fongmi.android.tv.utils.ResUtil.dp2px(5);
        lp.width = (screenWidth() - m5 * 8) / 3;
        lp.setMargins(0, m5, 0, m5);
        h.resultBg.setLayoutParams(lp);
        HkItem item = list.get(position);
        applyCardFace(h.resultBg2, item);
        HkTextViewUtils.setSpanText(h.textView, item.getTitle());
        if (HkColType.byCode(item.getType()) == HkColType.ICON_3_FILL) {
            bindImageViewWithNothing(h.imageView, item);
        } else {
            bindCircleCropImageViewWithNothing(h.imageView, item);
        }
        bindBgClick(holder, h.resultBg2);
    }

    /**
     * 照抄原版 bindForIconFiveHolder（smali:2945，java 2187-2214）：五列定宽；
     * 内置图（home_bookmark2/download/history2/setting/tools）图标 32dp，其余 38.4dp；
     * icon_5_no_crop 走无裁剪版，其余走圆形裁剪版。
     */
    protected void bindForIconFiveHolder(RecyclerView.ViewHolder holder, int position) {
        IconFiveHolder h = (IconFiveHolder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int m5 = com.fongmi.android.tv.utils.ResUtil.dp2px(5);
        lp.width = (screenWidth() - m5 * 12) / 5;
        lp.setMargins(0, m5 * 3, 0, m5 * 2);
        h.resultBg.setLayoutParams(lp);
        HkItem item = list.get(position);
        String pic = item.getPic();
        int imgSize;
        if ("hiker://images/home_bookmark2".equals(pic) || "hiker://images/home_download".equals(pic)
                || "hiker://images/home_history2".equals(pic) || "hiker://images/home_setting".equals(pic)
                || "hiker://images/home_tools".equals(pic)) {
            imgSize = com.fongmi.android.tv.utils.ResUtil.dp2px(32);
        } else {
            imgSize = dpToPx(38.4f);
        }
        RelativeLayout.LayoutParams ilp = (RelativeLayout.LayoutParams) h.resultBg.getImageView().getLayoutParams();
        ilp.width = imgSize;
        ilp.height = imgSize;
        h.resultBg.getImageView().setLayoutParams(ilp);
        HkTextViewUtils.setSpanText(h.resultBg.getTextView(), item.getTitle(), buildUrlListener(holder));
        if (!HkColType.ICON_5_NO_CROP.code.equals(item.getType())) {
            bindCircleCropImageView(h.resultBg, item, position);
        } else {
            bindNoCropImageView(h.resultBg, item, position);
        }
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForAvatarHolder（smali:1019，java 2146-2154）：标题/描述走 Html.fromHtml，图走圆形裁剪版。 */
    protected void bindForAvatarHolder(RecyclerView.ViewHolder holder, int position) {
        AvatarHolder h = (AvatarHolder) holder;
        HkItem item = list.get(position);
        String title = item.getTitle() == null ? "" : item.getTitle();
        h.textView.setText(android.text.Html.fromHtml(title));
        if (h.descView != null) {
            String desc = item.getDesc() == null ? "" : item.getDesc();
            h.descView.setText(android.text.Html.fromHtml(desc));
        }
        bindCircleCropImageView(h.imageView, h.shortcut_icon_text, item, position);
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForIconFourCardHolder（smali:3253，java 2243-2257）：卡片版四图标，标题空则隐藏。 */
    protected void bindForIconFourCardHolder(RecyclerView.ViewHolder holder, int position) {
        IconFourCardHolder h = (IconFourCardHolder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int m5 = com.fongmi.android.tv.utils.ResUtil.dp2px(5);
        int cols = 60 / (isLand() ? HkColType.ICON_4_CARD.landSpanCount : HkColType.ICON_4_CARD.spanCount);
        lp.width = (screenWidth() - m5 * 2 * (cols + 1)) / cols;
        lp.setMargins(0, m5 * 3, 0, m5 * 2);
        h.resultBg.setLayoutParams(lp);
        HkItem item = list.get(position);
        if (item.getTitle() == null || item.getTitle().isEmpty()) {
            h.textView.setVisibility(View.GONE);
        } else {
            h.textView.setVisibility(View.VISIBLE);
        }
        HkTextViewUtils.setSpanText(h.textView, item.getTitle(), buildUrlListener(holder));
        bindImageView(h.img, h.shortcut_icon_text, item, position, getOptions(item.getPic()));
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForIconFourRoundHolder（smali:3689，java 2133-2142）：圆图标四列，图走圆形裁剪版，无改名特判。 */
    protected void bindForIconFourRoundHolder(RecyclerView.ViewHolder holder, int position) {
        IconFourRoundHolder h = (IconFourRoundHolder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int m5 = com.fongmi.android.tv.utils.ResUtil.dp2px(5);
        int cols = 60 / (isLand() ? HkColType.ICON_4_ROUND.landSpanCount : HkColType.ICON_4_ROUND.spanCount);
        lp.width = (screenWidth() - m5 * 2 * (cols + 1)) / cols;
        lp.setMargins(0, m5 * 3, 0, m5 * 2);
        h.resultBg.setLayoutParams(lp);
        HkItem item = list.get(position);
        HkTextViewUtils.setSpanText(h.resultBg.getTextView(), item.getTitle(), buildUrlListener(holder));
        bindCircleCropImageView(h.resultBg, item, position);
        bindBgClick(holder, h.resultBg);
    }

    /**
     * 照抄原版 bindForIconFourHolder（smali:3444，java 2218-2239）。
     * “我的主页”规则下标题改名特判（主页设置→主题、我的书签→书签、我的下载→下载、历史记录→历史）照抄。
     */
    protected void bindForIconFourHolder(RecyclerView.ViewHolder holder, int position) {
        IconFourHolder h = (IconFourHolder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int m5 = com.fongmi.android.tv.utils.ResUtil.dp2px(5);
        int cols = 60 / (isLand() ? HkColType.ICON_4.landSpanCount : HkColType.ICON_4.spanCount);
        lp.width = (screenWidth() - m5 * 2 * (cols + 1)) / cols;
        lp.setMargins(0, m5 * 3, 0, m5 * 2);
        h.resultBg.setLayoutParams(lp);
        HkItem item = list.get(position);
        String title = item.getTitle();
        if ("我的主页".equals(ruleTitle)) {
            if ("主页设置".equals(title)) title = "主题";
            else if ("我的书签".equals(title)) title = "书签";
            else if ("我的下载".equals(title)) title = "下载";
            else if ("历史记录".equals(title)) title = "历史";
        }
        HkTextViewUtils.setSpanText(h.resultBg.getTextView(), title, buildUrlListener(holder));
        bindImageView(h.resultBg.getImageView(), h.resultBg.getIconTextView(), item, position, getOptions(item.getPic()));
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForCardPic3Holder（smali:1817，java 2038-2053）：imgBg 线性布局定宽高，标题先纯文本再 SpanText 覆盖。 */
    protected void bindForCardPic3Holder(RecyclerView.ViewHolder holder, int position) {
        CardPic3Holder h = (CardPic3Holder) holder;
        ViewGroup.LayoutParams cardRaw = h.imgBg.getLayoutParams();
        LinearLayout.LayoutParams lp = cardRaw instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) cardRaw
                : new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int cols = 60 / (isLand() ? HkColType.CARD_PIC_3.landSpanCount : HkColType.CARD_PIC_3.spanCount);
        lp.width = (screenWidth() - com.fongmi.android.tv.utils.ResUtil.dp2px(15) * (cols + 1)) / cols;
        lp.height = lp.width;
        HkItem item = list.get(position);
        if (item.getTitle() == null || item.getTitle().isEmpty()) {
            h.textView.setVisibility(View.GONE);
        } else {
            h.textView.setVisibility(View.VISIBLE);
            h.textView.setText(item.getTitle());
        }
        h.imgBg.setLayoutParams(lp);
        bindImageView(h.img, item, position);
        HkTextViewUtils.setSpanText(h.textView, item.getTitle(), buildUrlListener(holder));
        bindBgClick(holder, h.resultBg);
    }

    /**
     * 照抄原版 bindForCardPic22Holder（smali:1140，java 1919-1967）：一格双条目。
     * 组标签防重复绑：原版用 fastjson 序列化整组作 tag，本类无 fastjson，
     * 以组内各条 type/title/pic/url/desc 拼接串等价替代（仅作同内容判等）。
     * desc 解析为正整数时以 dp 覆盖高度；LEFT 型两格点击偏移镜像（原版公式照抄）。
     */
    protected void bindForCardPic22Holder(RecyclerView.ViewHolder holder, int position) {
        CardPic22Holder h = (CardPic22Holder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int cols = 60 / (isLand() ? HkColType.CARD_PIC_2_2.landSpanCount : HkColType.CARD_PIC_2_2.spanCount);
        lp.width = (screenWidth() - com.fongmi.android.tv.utils.ResUtil.dp2px(15) * (cols + 1)) / cols;
        lp.height = lp.width;
        int m7 = com.fongmi.android.tv.utils.ResUtil.dp2px(7);
        lp.setMargins(0, m7, 0, m7);
        String desc = list.get(position).getDesc();
        int heightOverride = -1;
        if (desc != null && !desc.isEmpty()) {
            try {
                heightOverride = Integer.parseInt(desc);
            } catch (NumberFormatException e) {
                heightOverride = -1;
            }
        }
        if (heightOverride > 0) {
            lp.height = com.fongmi.android.tv.utils.ResUtil.dp2px(heightOverride);
        }
        h.resultBg.setLayoutParams(lp);
        Integer g = polyPositionMap.get(position);
        if (g == null) return;
        List<HkItem> group = polyList.get(g);
        String tagJson = groupTag(group);
        Object tag = h.resultBg.getTag();
        if (tag != null && tagJson.equals(tag.toString())) return;
        h.resultBg.setTag(tagJson);
        if (group == null || group.isEmpty()) return;
        int count = group.size() >= 2 ? 2 : 1;
        int itemType0 = HkColType.itemTypeByCode(group.get(0).getType());
        HkTextViewUtils.setSpanText(h.textView1, group.get(0).getTitle(), buildUrlListener(holder));
        bindImageView(h.img1, group.get(0), position);
        if (itemType0 == HkColType.CARD_PIC_2_2_LEFT.itemType) {
            bindClick(holder, h.resultBg1, 1 - count);
        } else {
            bindClick(holder, h.resultBg1, 0);
        }
        if (group.size() <= 1) {
            h.resultBg2.setVisibility(View.GONE);
            return;
        }
        HkTextViewUtils.setSpanText(h.textView2, group.get(1).getTitle(), buildUrlListener(holder));
        bindImageView(h.img2, group.get(1), position);
        if (itemType0 == HkColType.CARD_PIC_2_2_LEFT.itemType) {
            bindClick(holder, h.resultBg2, -Math.abs(2 - group.size()));
        } else {
            bindClick(holder, h.resultBg2, 1);
        }
        h.resultBg2.setVisibility(View.VISIBLE);
    }

    private String groupTag(List<HkItem> group) {
        if (group == null) return "";
        StringBuilder sb = new StringBuilder();
        for (HkItem it : group) {
            sb.append(it.getType()).append('\u0001')
                    .append(it.getTitle()).append('\u0001')
                    .append(it.getPic()).append('\u0001')
                    .append(it.getUrl()).append('\u0001')
                    .append(it.getDesc()).append('\u0002');
        }
        return sb.toString();
    }

    /** 照抄原版 bindForCardPic2Holder（smali:1543，java 1999-2034）：desc 解析为 blur 半径（0 走直载）。 */
    protected void bindForCardPic2Holder(RecyclerView.ViewHolder holder, int position) {
        CardPic2Holder h = (CardPic2Holder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int cols = 60 / (isLand() ? HkColType.CARD_PIC_2.landSpanCount : HkColType.CARD_PIC_2.spanCount);
        lp.width = (screenWidth() - com.fongmi.android.tv.utils.ResUtil.dp2px(15) * (cols + 1)) / cols;
        lp.height = lp.width;
        HkItem item = list.get(position);
        if (HkColType.CARD_PIC_1.code.equals(item.getType())) {
            lp.width = -1;
            if (isLand() && "hiker://images/home_bg".equals(item.getPic())) {
                lp.height = 0;
            }
        }
        int m7 = com.fongmi.android.tv.utils.ResUtil.dp2px(7);
        lp.setMargins(0, m7, 0, m7);
        h.resultBg.setLayoutParams(lp);
        int radius = 15;
        String desc = item.getDesc();
        if (desc != null && !desc.isEmpty()) {
            int parsed = 15;
            boolean ok = true;
            try {
                parsed = Integer.parseInt(desc);
            } catch (NumberFormatException e) {
                ok = false;
            }
            if (ok && parsed >= 0 && parsed <= 25) radius = parsed;
        }
        if (radius == 0) {
            bindImageView(h.img, item, position);
        } else {
            bindBlurImg(h.img, radius, item.getPic());
        }
        HkTextViewUtils.setSpanText(h.textView, item.getTitle(), buildUrlListener(holder));
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForPicTwoCardHolder（smali:6642，java 587-612）。 */
    protected void bindForPicTwoCardHolder(RecyclerView.ViewHolder holder, int position) {
        PicTwoCardHolder h = (PicTwoCardHolder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int margin = com.fongmi.android.tv.utils.ResUtil.dp2px(6);
        int cols = 60 / (isLand() ? HkColType.PIC_2_CARD.landSpanCount : HkColType.PIC_2_CARD.spanCount);
        lp.setMargins(0, 0, 0, margin * 2);
        if (activity() != null) {
            lp.width = (screenWidth() - com.fongmi.android.tv.utils.ResUtil.dp2px(10) * (cols + 1)) / cols;
        } else {
            lp.width = (screenWidth() - margin * 2 * (cols + 1)) / cols;
        }
        lp.height = lp.width * 16 / 9;
        h.resultBg.setLayoutParams(lp);
        HkItem item = list.get(position);
        if (item.getTitle() == null || item.getTitle().isEmpty()) {
            h.title.setVisibility(View.GONE);
        } else {
            h.title.setVisibility(View.VISIBLE);
            h.title.setText(item.getTitle());
        }
        com.bumptech.glide.Glide.with(context).asDrawable()
                .load(HkGlideUtil.getGlideUrl(pageUrl, item.getPic()))
                .apply(options2.clone().placeholder(new android.graphics.drawable.ColorDrawable(
                        android.graphics.Color.TRANSPARENT)))
                .into(h.img);
        bindBgClick(holder, h.img);
    }

    /** 照抄原版 bindForPicThreeSquareHolder（smali:6361，java 2261-2291）：方形三列、按组内列位调边距。 */
    protected void bindForPicThreeSquareHolder(RecyclerView.ViewHolder holder, int position) {
        PicThreeSquareHolder h = (PicThreeSquareHolder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int m3 = com.fongmi.android.tv.utils.ResUtil.dp2px(3);
        int mA = com.fongmi.android.tv.utils.ResUtil.dp2px(7) + m3;
        lp.width = (screenWidth() - mA * 2 - m3 * 2) / 3;
        lp.height = lp.width;
        int col = 0;
        try {
            Integer g = polyPositionMap.get(position);
            if (g != null) {
                col = polyList.get(g).indexOf(list.get(position)) % 3 - 1;
            }
        } catch (Exception ignored) {
        }
        if (col == -1) {
            lp.setMargins(mA, m3, -com.fongmi.android.tv.utils.ResUtil.dp2px(4) / 3, 0);
        } else if (col == 1) {
            lp.setMargins(-com.fongmi.android.tv.utils.ResUtil.dp2px(4) / 3, m3, mA, 0);
        } else {
            lp.setMargins(com.fongmi.android.tv.utils.ResUtil.dp2px(13) / 3, m3,
                    com.fongmi.android.tv.utils.ResUtil.dp2px(13) / 3, 0);
        }
        h.resultBg.setLayoutParams(lp);
        HkItem item = list.get(position);
        com.bumptech.glide.Glide.with(context).asDrawable()
                .load(HkGlideUtil.getGlideUrl(pageUrl, item.getPic()))
                .apply(options2.clone().placeholder(new android.graphics.drawable.ColorDrawable(
                        android.graphics.Color.TRANSPARENT)))
                .into(h.img);
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForPicThreeHolder（smali:6165，java 2337-2354）：直载 options2＋占位（原版 gray_rice 色占位，按用户令改透明）。 */
    protected void bindForPicThreeHolder(RecyclerView.ViewHolder holder, int position) {
        PicThreeHolder h = (PicThreeHolder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int marginHalf = com.fongmi.android.tv.utils.ResUtil.dp2px(6) / 2;
        lp.width = (screenWidth() - marginHalf * 4) / 3;
        lp.setMargins(0, marginHalf, 0, 0);
        if (activity() != null) {
            lp.height = (screenWidth() - com.fongmi.android.tv.utils.ResUtil.dp2px(40)) / 2;
        } else {
            lp.height = com.fongmi.android.tv.utils.ResUtil.dp2px(150);
        }
        h.resultBg.setLayoutParams(lp);
        HkItem item = list.get(position);
        com.bumptech.glide.Glide.with(context).asDrawable()
                .load(HkGlideUtil.getGlideUrl(pageUrl, item.getPic()))
                .apply(options2.clone().placeholder(new android.graphics.drawable.ColorDrawable(
                        android.graphics.Color.TRANSPARENT)))
                .into(h.img);
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForPicTwoHolder（smali:6914，java 2358-2382；movie_2/pic_2 共用）。 */
    protected void bindForPicTwoHolder(RecyclerView.ViewHolder holder, int position) {
        PicTwoHolder h = (PicTwoHolder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int margin = com.fongmi.android.tv.utils.ResUtil.dp2px(6);
        int cols = 60 / (isLand() ? HkColType.PIC_2.landSpanCount : HkColType.PIC_2.spanCount);
        lp.width = (screenWidth() - margin * 2 * (cols + 1)) / cols;
        lp.setMargins(0, margin, 0, margin);
        h.resultBg.setLayoutParams(lp);
        android.widget.FrameLayout.LayoutParams clp =
                (android.widget.FrameLayout.LayoutParams) h.containner.getLayoutParams();
        clp.height = lp.width * 3 / 5;
        h.containner.setLayoutParams(clp);
        HkItem item = list.get(position);
        applyCoverFace(holder, item, position, h.containner, h.img);
        bindImageView(h.img, item, position);
        String desc = item.getDesc();
        if (!android.text.TextUtils.isEmpty(desc) && !"null".equals(desc)) {
            h.desc.setVisibility(View.VISIBLE);
            h.desc.setText(desc);
        } else {
            h.desc.setVisibility(View.GONE);
        }
        String title = item.getTitle();
        if (desc != null && !desc.isEmpty() && (android.text.TextUtils.isEmpty(title) || "null".equals(title))) {
            h.title.setVisibility(View.GONE);
        } else {
            h.title.setVisibility(View.VISIBLE);
            h.title.setText(title);
        }
        bindBgClick(holder, h.containner);
    }

    /** 照抄原版 bindForPicOneHolder（smali:5972，java 1825-1847）。 */
    protected void bindForPicOneHolder(RecyclerView.ViewHolder holder, int position) {
        PicOneHolder h = (PicOneHolder) holder;
        HkItem item = list.get(position);
        h.title.setText(item.getTitle());
        if (android.text.TextUtils.isEmpty(item.getDesc())) {
            h.desc.setVisibility(View.GONE);
        } else {
            h.desc.setVisibility(View.VISIBLE);
            h.desc.setText(item.getDesc());
        }
        String pic = item.getPic();
        if (!android.text.TextUtils.isEmpty(pic) && !"null".equals(pic) && !"*".equals(pic)) {
            h.containner.setVisibility(View.VISIBLE);
            bindImageView(h.img, item, position);
            android.view.ViewGroup.LayoutParams lp = h.containner.getLayoutParams();
            if (activity() != null) {
                lp.height = (screenWidth() - com.fongmi.android.tv.utils.ResUtil.dp2px(20)) / 2;
            } else {
                lp.height = com.fongmi.android.tv.utils.ResUtil.dp2px(160);
            }
            h.containner.setLayoutParams(lp);
        } else {
            h.containner.setVisibility(View.GONE);
        }
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForPicOneFullHolder（smali:5898，java 1851-1855）。占位按用户令改透明（原版为 mipmap/placeholder）。 */
    protected void bindForPicOneFullHolder(RecyclerView.ViewHolder holder, int position) {
        PicOneFullHolder h = (PicOneFullHolder) holder;
        HkItem item = list.get(position);
        Object model = HkGlideUtil.getGlideUrl(pageUrl, item.getPic());
        HkGlideUtil.loadFullPicDrawable(context, h.imgView, model,
                optionsFull.clone().placeholder(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)));
        bindBgClick(holder, h.imgView);
    }

    /** 照抄原版 bindForPicOneCenterHolder（smali:5868，java 1859-1862）。 */
    protected void bindForPicOneCenterHolder(RecyclerView.ViewHolder holder, int position) {
        PicOneCenterHolder h = (PicOneCenterHolder) holder;
        HkItem item = list.get(position);
        bindImageView(h.imgView, item, position);
        bindBgClick(holder, h.imgView);
    }

    // ===== 沉浸式主题标记（原版 adapter 的 immersiveTheme 字段，由页面侧设置） =====
    private boolean immersiveTheme;

    public void setImmersiveTheme(boolean immersiveTheme) {
        this.immersiveTheme = immersiveTheme;
    }

    /** 对应原版 MyStatusBarUtil.getStatusBarHeight。 */
    protected int statusBarHeight() {
        int id = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? context.getResources().getDimensionPixelSize(id) : 0;
    }

    private android.app.Activity activity() {
        return context instanceof android.app.Activity ? (android.app.Activity) context : null;
    }

    /** 照抄原版 addShadowForMovieOne（smali:12376，java 1523-1538）。 */
    public android.graphics.Bitmap addShadowForMovieOne(boolean gradient, android.graphics.Bitmap bitmap) {
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        android.graphics.drawable.ColorDrawable shadow = new android.graphics.drawable.ColorDrawable(isNight() ? 0x80000000 : 0x81000000);
        shadow.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        shadow.draw(canvas);
        if (gradient) {
            int[] colors = {0x00ffffff, 0x00ffffff, 0x00ffffff, 0x81000000, 0xffffffff, 0xffffffff};
            if ((context.getResources().getConfiguration().uiMode & 0x30) == 0x20) {
                colors = new int[]{0, 0, 0, 0x801b1b1a, 0xff1b1b1b, 0xff1b1b1b};
            }
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, colors);
            gd.setGradientType(0);
            gd.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            gd.draw(canvas);
        }
        return bitmap;
    }

    /** 照抄原版 bindBlurImg(int, String, Consumer, Consumer)（smali:708）：先过 getGlideUrl。 */
    protected void bindBlurImg(int radius, String pic, java.util.function.Consumer<android.graphics.Bitmap> bmpConsumer,
                               java.util.function.Consumer<android.graphics.drawable.Drawable> drawableConsumer) {
        Object model = HkGlideUtil.getGlideUrl(pageUrl, pic);
        bindBlurImg(radius, model, bmpConsumer, drawableConsumer);
    }

    /** 照抄原版 bindBlurImg(int, Object, Consumer, Consumer)（smali:633）+ ArticleListAdapter$6 Target。 */
    protected void bindBlurImg(int radius, Object model, java.util.function.Consumer<android.graphics.Bitmap> bmpConsumer,
                               java.util.function.Consumer<android.graphics.drawable.Drawable> drawableConsumer) {
        com.bumptech.glide.Glide.with(context).asBitmap().load(model)
                .apply(options2.clone().placeholder(new android.graphics.drawable.ColorDrawable(
                        android.graphics.Color.TRANSPARENT)))
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                    @Override
                    public void onResourceReady(android.graphics.Bitmap resource, com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                        android.app.Activity act = activity();
                        if (act == null || act.isFinishing()) return;
                        android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(resource,
                                Math.max(1, Math.min(resource.getWidth() / 12, 512)),
                                Math.max(1, Math.min(resource.getHeight() / 12, 512)), false);
                        android.graphics.Bitmap blurred;
                        try {
                            blurred = jp.wasabeef.glide.transformations.internal.RSBlur.blur(context, scaled, radius);
                        } catch (android.renderscript.RSRuntimeException e) {
                            blurred = jp.wasabeef.glide.transformations.internal.FastBlur.blur(scaled, radius, true);
                        }
                        bmpConsumer.accept(blurred);
                    }

                    @Override
                    public void onLoadFailed(android.graphics.drawable.Drawable errorDrawable) {
                        android.app.Activity act = activity();
                        if (act != null && !act.isFinishing()) {
                            drawableConsumer.accept(new android.graphics.drawable.ColorDrawable(
                                    android.graphics.Color.TRANSPARENT));
                        }
                        super.onLoadFailed(errorDrawable);
                    }

                    @Override
                    public void onLoadCleared(android.graphics.drawable.Drawable placeholder) {
                    }
                });
    }

    /** 照抄原版 bindBlurImg(ImageView, int, String)（smali:749 段，java 2115-2128）+ $30/$31/$32 lambda。 */
    protected void bindBlurImg(ImageView imageView, int radius, String pic) {
        java.util.function.Consumer<android.graphics.Bitmap> shadowSet = bitmap -> {
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            android.graphics.drawable.ColorDrawable shadow = new android.graphics.drawable.ColorDrawable(0x81000000);
            shadow.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            shadow.draw(canvas);
            imageView.setImageBitmap(bitmap);
        };
        bindBlurImg(radius, pic, shadowSet, drawable ->
                bindBlurImg(25, (Object) Integer.valueOf(R.drawable.nothing), shadowSet, imageView::setImageDrawable));
    }

    /** 照抄原版 bindForMovieOneBlurColHolder（smali:5404，java 1478-1519）。 */
    protected void bindForMovieOneBlurColHolder(RecyclerView.ViewHolder holder, int position) {
        MovieOneBlurColHolder h = (MovieOneBlurColHolder) holder;
        HkItem item = list.get(position);
        boolean gradient = "true".equals(item.getExtra("gradient"));
        int topPad = com.fongmi.android.tv.utils.ResUtil.dp2px(85);
        if (immersiveTheme) {
            topPad = statusBarHeight();
            if (topPad <= 0) topPad = com.fongmi.android.tv.utils.ResUtil.dp2px(21);
            topPad += com.fongmi.android.tv.utils.ResUtil.dp2px(64);
        }
        h.resultBg.setPadding(0, topPad, 0, com.fongmi.android.tv.utils.ResUtil.dp2px(12));
        java.util.function.Consumer<android.graphics.Bitmap> bmpConsumer = bitmap ->
                h.resultBg.setBackground(new android.graphics.drawable.BitmapDrawable(
                        context.getResources(), addShadowForMovieOne(gradient, bitmap)));
        if (!android.text.TextUtils.isEmpty(item.getPic()) && !"*".equals(item.getPic())) {
            bindImageView(h.img, item, position);
            bindBlurImg(25, item.getPic(), bmpConsumer, drawable ->
                    bindBlurImg(25, (Object) Integer.valueOf(R.drawable.nothing), bmpConsumer, d2 -> {
                    }));
        } else {
            com.bumptech.glide.Glide.with(context).asDrawable().load(Integer.valueOf(R.drawable.nothing))
                    .apply(options.clone().placeholder(new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT)))
                    .into(h.img);
            bindBlurImg(25, (Object) Integer.valueOf(R.drawable.nothing), bmpConsumer, d -> {
            });
        }
        HkTextViewUtils.setSpanText(h.title, item.getTitle(), buildUrlListener(holder));
        HkTextViewUtils.setSpanText(h.desc, item.getDesc(), buildUrlListener(holder));
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForMovieOneColHolder（smali:5673，java 1454-1474）。 */
    protected void bindForMovieOneColHolder(RecyclerView.ViewHolder holder, int position) {
        MovieOneColHolder h = (MovieOneColHolder) holder;
        HkItem item = list.get(position);
        if (!android.text.TextUtils.isEmpty(item.getPic()) && !"*".equals(item.getPic())) {
            applyCoverFace(holder, item, position, h.img);
            bindImageView(h.img, item, position);
        } else {
            applyCardFace(h.img, item);
            com.bumptech.glide.Glide.with(context).asDrawable().load(Integer.valueOf(R.drawable.nothing))
                    .apply(options.clone().placeholder(new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT)))
                    .into(h.img);
        }
        HkTextViewUtils.setSpanText(h.title, item.getTitle(), buildUrlListener(holder));
        if ((item.getDesc() == null || item.getDesc().isEmpty())
                && HkColType.itemTypeByCode(item.getType()) == HkColType.ICON_1_LEFT_PIC.itemType) {
            h.desc.setVisibility(View.GONE);
        } else {
            h.desc.setVisibility(View.VISIBLE);
            HkTextViewUtils.setSpanText(h.desc, item.getDesc(), buildUrlListener(holder));
        }
        bindLineExtra(h.resultBg, item);
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForTextThreeHolder（smali:8411）：宽=(屏宽−边距×8)/3。 */
    protected void bindForTextThreeHolder(RecyclerView.ViewHolder holder, int position) {
        TextThreeHolder h = (TextThreeHolder) holder;
        HkItem item = list.get(position);
        applyCardFace(h.resultBg, item);
        h.title.setTextColor(context.getResources().getColor(item.isSel() ? R.color.yellowAction : R.color.text_color_tertiary));
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int margin = com.fongmi.android.tv.utils.ResUtil.dp2px(5);
        lp.width = (screenWidth() - margin * 8) / 3;
        lp.setMargins(0, margin, 0, margin);
        h.resultBg.setLayoutParams(lp);
        setTextGravity(h.title, item);
        HkTextViewUtils.setSpanText(h.title, item.getTitle(), buildUrlListener(holder));
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForTextFourHolder（smali:8102）：宽=(屏宽−边距×10)/4。 */
    protected void bindForTextFourHolder(RecyclerView.ViewHolder holder, int position) {
        TextFourHolder h = (TextFourHolder) holder;
        HkItem item = list.get(position);
        applyCardFace(h.resultBg, item);
        h.title.setTextColor(context.getResources().getColor(item.isSel() ? R.color.yellowAction : R.color.text_color_tertiary));
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int margin = com.fongmi.android.tv.utils.ResUtil.dp2px(5);
        lp.width = (screenWidth() - margin * 10) / 4;
        lp.setMargins(0, margin, 0, margin);
        h.resultBg.setLayoutParams(lp);
        setTextGravity(h.title, item);
        HkTextViewUtils.setSpanText(h.title, item.getTitle(), buildUrlListener(holder));
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForTextFiveHolder（smali:7944）：宽=(屏宽−横边距×6)/5，纵边距 3dp。 */
    protected void bindForTextFiveHolder(RecyclerView.ViewHolder holder, int position) {
        TextFiveHolder h = (TextFiveHolder) holder;
        HkItem item = list.get(position);
        applyCardFace(h.resultBg, item);
        h.title.setTextColor(context.getResources().getColor(item.isSel() ? R.color.yellowAction : R.color.text_color_tertiary));
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int marginH = com.fongmi.android.tv.utils.ResUtil.dp2px(5);
        int marginV = com.fongmi.android.tv.utils.ResUtil.dp2px(3);
        lp.width = (screenWidth() - marginH * 6) / 5;
        lp.setMargins(0, marginV, 0, marginV);
        h.resultBg.setLayoutParams(lp);
        setTextGravity(h.title, item);
        HkTextViewUtils.setSpanText(h.title, item.getTitle(), buildUrlListener(holder));
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForToolsHolder（smali:8560，java 1275-1295）。 */
    protected void bindForToolsHolder(RecyclerView.ViewHolder holder, int position) {
        ToolsHolder h = (ToolsHolder) holder;
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int margin = com.fongmi.android.tv.utils.ResUtil.dp2px(6);
        int cols = 60 / (isLand() ? HkColType.MOVIE_3.landSpanCount : HkColType.MOVIE_3.spanCount);
        int width = (screenWidth() - margin * 2 * (cols + 1)) / cols;
        lp.width = width;
        android.widget.FrameLayout.LayoutParams clp =
                (android.widget.FrameLayout.LayoutParams) h.containner.getLayoutParams();
        clp.height = lp.width * 17 / 12;
        h.containner.setLayoutParams(clp);
        lp.setMargins(0, margin, 0, margin);
        h.resultBg.setLayoutParams(lp);
        HkItem item = list.get(position);
        applyCoverFace(holder, item, position, h.containner, h.img);
        bindImageView(h.img, item, position);
        h.title.setText(item.getTitle());
        h.title.setSelected(true);
        String desc = item.getDesc();
        if (desc == null || desc.isEmpty() || "null".equals(desc)) {
            h.desc.setVisibility(View.INVISIBLE);
        } else {
            h.desc.setVisibility(View.VISIBLE);
            h.desc.setText(desc);
        }
        bindBgClick(holder, h.containner);
    }

    /** 照抄原版 bindForTextOneColHolder（smali:8251，java 1552-1569）。 */
    protected void bindForTextOneColHolder(RecyclerView.ViewHolder holder, int position) {
        TextOneColHolder h = (TextOneColHolder) holder;
        HkItem item = list.get(position);
        h.title.setTextColor(context.getResources().getColor(item.isSel() ? R.color.yellowAction : R.color.blackText));
        HkTextViewUtils.setSpanText(h.title, item.getTitle(), buildUrlListener(holder));
        String desc = item.getDesc();
        if (!android.text.TextUtils.isEmpty(desc) && !"*".equals(desc) && !"null".equals(desc)) {
            h.desc.setVisibility(View.VISIBLE);
            HkTextViewUtils.setSpanText(h.desc, desc, buildUrlListener(holder));
        } else {
            h.desc.setVisibility(View.GONE);
        }
        bindLineExtra(h.resultBg, item);
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindForLongTextHolder（smali:5239，java 1587-1625）。 */
    protected void bindForLongTextHolder(RecyclerView.ViewHolder holder, int position) {
        LongTextHolder h = (LongTextHolder) holder;
        HkItem item = list.get(position);
        String title = item.getTitle();
        if (title == null || title.isEmpty()) title = "";
        String check = title.length() < 300 ? title : title.substring(0, 300);
        if (check.contains("<p>")) {
            h.title.setText(android.text.Html.fromHtml(title));
        } else {
            h.title.setText(title);
        }
        String sizeStr = item.getExtra("textSize");
        int textSize = 0;
        try {
            textSize = sizeStr == null || sizeStr.isEmpty() ? 0 : Integer.parseInt(sizeStr.trim());
        } catch (Exception ignored) {
        }
        if (textSize > 0) {
            h.title.setTextSize(2, textSize);
        } else {
            h.title.setTextSize(2, 16.5f);
        }
        h.title.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos >= 0 && pos < list.size() && onItemClickListener != null) onItemClickListener.onClick(v, pos);
        });
        h.title.setOnLongClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos >= 0 && pos < list.size() && onItemClickListener != null) onItemClickListener.onLongClick(v, pos);
            return true;
        });
    }

    /** 照抄原版 bindForChannelHolder（smali:1991，java 1733-1748）。 */
    protected void bindForChannelHolder(RecyclerView.ViewHolder holder, int position) {
        ChannelHolder h = (ChannelHolder) holder;
        HkItem item = list.get(position);
        applyCardFace(h.resultBg, item);
        h.title.setTextColor(context.getResources().getColor(item.isSel() ? R.color.yellowAction : R.color.text_color_tertiary));
        setTextGravity(h.title, item);
        androidx.recyclerview.widget.GridLayoutManager.LayoutParams lp =
                (androidx.recyclerview.widget.GridLayoutManager.LayoutParams) h.resultBg.getLayoutParams();
        int margin = com.fongmi.android.tv.utils.ResUtil.dp2px(5);
        int cols = 60 / (isLand() ? HkColType.TEXT_2.landSpanCount : HkColType.TEXT_2.spanCount);
        lp.width = (screenWidth() - margin * 2 * (cols + 1)) / cols;
        lp.setMargins(0, margin, 0, margin);
        h.resultBg.setLayoutParams(lp);
        HkTextViewUtils.setSpanText(h.title, item.getTitle(), buildUrlListener(holder));
        bindBgClick(holder, h.resultBg);
    }

    /** 照抄原版 bindBgClick（smali:613）+ 两个 lambda（smali:9695/9730）。 */
    protected void bindBgClick(RecyclerView.ViewHolder holder, View view) {
        view.setOnClickListener(v -> {
            try {
                int pos = holder.getAdapterPosition();
                if (pos >= 0 && onItemClickListener != null) onItemClickListener.onClick(v, pos);
            } catch (Exception ignored) {
            }
        });
        view.setOnLongClickListener(v -> {
            try {
                int pos = holder.getAdapterPosition();
                if (pos >= 0 && onItemClickListener != null) onItemClickListener.onLongClick(v, pos);
            } catch (Exception ignored) {
            }
            return true;
        });
    }

    public List<HkItem> getList() {
        return list;
    }

    public List<List<HkItem>> getPolyList() {
        return polyList;
    }

    public Map<Integer, Integer> getPolyPositionMap() {
        return polyPositionMap;
    }

    /** 数据变更后重算分组（对应原版 setList/notify 路径里的 updatePolyList 调用点）。 */
    public void refreshGroups() {
        updatePolyList();
    }

    /** 照抄原版 notifyDataChanged：先重算 poly 分组，再整表刷新。 */
    public void notifyDataChanged() {
        updatePolyList();
        notifyDataSetChanged();
    }

    /**
     * 照抄原版整套通知重写（ArticleListAdapter.smali:13012-13280）：
     * 原版每个通知入口都先 updatePolyList() 重算分组再调父类——少了这一步，
     * 分组表永远停在旧数据，flex_button/scroll_button 组条目绑定时查不到组、
     * bindForFlexButtonHolder/bindForScrollButtonHolder 直接返回，整行空白不显示。
     * notifyChanged：目标条目是 scroll_button/flex_button 时改走整表刷新。
     */
    public void notifyChanged(int position) {
        updatePolyList();
        if (position < 0 || position >= list.size()) {
            notifyDataSetChanged();
            return;
        }
        String type = list.get(position).getType();
        if (HkColType.SCROLL_BUTTON.code.equals(type) || HkColType.FLEX_BUTTON.code.equals(type)) {
            notifyDataSetChanged();
        } else {
            super.notifyItemChanged(position);
        }
    }

    public void notifyInserted(int position) {
        updatePolyList();
        super.notifyItemInserted(position);
    }

    public void notifyRangeChanged(int start, int count) {
        updatePolyList();
        super.notifyItemRangeChanged(start, count);
    }

    public void notifyRangeInserted(int start, int count) {
        updatePolyList();
        super.notifyItemRangeInserted(start, count);
    }

    public void notifyRangeRemoved(int start, int count) {
        updatePolyList();
        super.notifyItemRangeRemoved(start, count);
    }

    public void notifyRemoved(HkItem item, int position) {
        updatePolyList();
        super.notifyItemRemoved(position);
    }

    /** 照抄原版 hasFlex(int, int)：区间内任一条目是 scroll_button/flex_button 即 true（逐条 try/catch）。 */
    public boolean hasFlex(int start, int count) {
        for (int i = start; i < start + count; i++) {
            try {
                String type = list.get(i).getType();
                if (HkColType.SCROLL_BUTTON.code.equals(type)
                        || HkColType.FLEX_BUTTON.code.equals(type)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * 照抄原版 updatePolyList（ArticleListAdapter.smali:12152，java 190-215）：
     * 把列表按"连续同 type"切成分组，polyPositionMap 记录每条所属组下标。
     */
    private void updatePolyList() {
        polyList.clear();
        polyPositionMap.clear();
        if (list == null || list.isEmpty()) return;
        for (int i = 0; i < list.size(); i++) {
            if (polyList.isEmpty()) {
                polyList.add(new ArrayList<>());
            }
            List<HkItem> last = polyList.get(polyList.size() - 1);
            if (last.isEmpty()) {
                last.add(list.get(i));
                polyPositionMap.put(i, 0);
            } else if (equalsType(last.get(0), list.get(i))) {
                last.add(list.get(i));
                polyPositionMap.put(i, polyList.size() - 1);
            } else {
                List<HkItem> g = new ArrayList<>();
                g.add(list.get(i));
                polyList.add(g);
                polyPositionMap.put(i, polyList.size() - 1);
            }
        }
    }

    /** 原版用 StringUtils.equals(last.getType(), item.getType())（commons-lang3，null 安全）。 */
    private static boolean equalsType(HkItem a, HkItem b) {
        String x = a == null ? null : a.getColType();
        String y = b == null ? null : b.getColType();
        return x == null ? y == null : x.equals(y);
    }

    /**
     * 照抄原版 getItemViewType（ArticleListAdapter.smali:12548，java 309-362）：
     * 先按 code 取 itemType；flex/scroll 组内非首条 → -1（占位）；
     * card_pic_2_2 非首条 → -1（横向 -10）；card_pic_2_2_left 非末条 → -1（横向 -10）；
     * icon_small_3 同组 ≥4 条 → -3；其余原样返回 itemType。
     */
    @Override
    public int getItemViewType(int position) {
        int type = HkColType.itemTypeByCode(list.get(position).getColType());
        if (type == HkColType.FLEX_BUTTON.itemType || type == HkColType.SCROLL_BUTTON.itemType) {
            try {
                Integer gi = polyPositionMap.get(position);
                if (gi != null) {
                    HkItem first = polyList.get(gi).get(0);
                    if (first != list.get(position)) return -1;
                }
            } catch (Exception ignored) {
                // 原版此处 Timber.e 记录后继续走默认返回
            }
            return type;
        }
        if (type == HkColType.CARD_PIC_2_2.itemType) {
            try {
                Integer gi = polyPositionMap.get(position);
                if (gi != null) {
                    HkItem first = polyList.get(gi).get(0);
                    if (first != list.get(position)) return isLand() ? -10 : -1;
                }
            } catch (Exception ignored) {
            }
            return type;
        }
        if (type == HkColType.CARD_PIC_2_2_LEFT.itemType) {
            try {
                Integer gi = polyPositionMap.get(position);
                if (gi != null) {
                    List<HkItem> g = polyList.get(gi);
                    HkItem last = g.get(g.size() - 1);
                    if (last != list.get(position)) return isLand() ? -10 : -1;
                }
            } catch (Exception ignored) {
            }
            return type;
        }
        if (type == HkColType.ICON_3_SMALL.itemType) {
            try {
                Integer gi = polyPositionMap.get(position);
                if (gi != null) {
                    List<HkItem> g = polyList.get(gi);
                    if (g != null && !g.isEmpty() && g.size() >= 4) return -3;
                }
            } catch (Exception ignored) {
            }
            return type;
        }
        return type;
    }

    /** 对应原版 Fragment 侧 SpanSizeLookup 的组合：getSpanCountByItemType(getItemViewType(position))。 */
    public int spanFor(int position) {
        return HkColType.spanCountByItemType(getItemViewType(position));
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    /**
     * 照抄原版 onCreateViewHolder（ArticleListAdapter.smali:13683，java 364-480）：
     * 分发表见 hk-verbatim/holder_layout_map.txt（寄存器状态追踪实证）。
     * 布局均为 8.83 原版布局文件（已原样拷入 res/layout）。
     */
    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        // 照抄原版 onViewAttachedToWindow（ArticleListAdapter.smali 第 481-485 行）：
        // LongText 行 attach 时先禁用再启用标题，复位 AlignTextView 内部状态。
        if (holder instanceof LongTextHolder) {
            AlignTextView title = ((LongTextHolder) holder).title;
            if (title != null) {
                title.setEnabled(false);
                title.setEnabled(true);
            }
        }
        // 液态玻璃：内容卡按压反馈（发亮＋微缩 6%＋回弹），不碰原版绑定逻辑；内嵌 WebView 卡不碰
        if (holder instanceof X5WebViewHolder) return;
        HkPressFeedback.attach(holder.itemView);
    }

    /**
     * 照抄原版 onViewRecycled（ArticleListAdapter.smali 第 490-498 行）：
     * 带图 Holder（IBaseHolder）回收时，若其图非空且宿主 Activity 未 finishing，
     * Glide.with(context).clear(imageView) 清掉挂在该图上的请求，异常只记日志。
     */
    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof HkImageHolder) {
            ImageView imageView = ((HkImageHolder) holder).getImageView();
            android.app.Activity activity = activity();
            if (imageView != null && activity != null && !activity.isFinishing()) {
                try {
                    com.bumptech.glide.Glide.with(context).clear(imageView);
                } catch (Exception e) {
                    com.github.catvod.crawler.SpiderDebug.log("hk", "onViewRecycled clear failed: %s", e.getMessage());
                }
            }
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        java.util.function.IntFunction<View> inflate = res -> inflater.inflate(res, parent, false);
        if (viewType == HkColType.FOOTER.itemType) return new MyViewHolder(inflate.apply(R.layout.view_load_more_footer));
        if (viewType == HkColType.HEADER.itemType) return new HeaderHolder(inflate.apply(R.layout.view_article_list_header));
        if (viewType == HkColType.MOVIE_1.itemType) return new MovieOneColHolder(inflate.apply(R.layout.item_movie_one_col));
        if (viewType == HkColType.MOVIE_1_LEFT_PIC.itemType) return new MovieOneColHolder(inflate.apply(R.layout.item_movie_one_col_left_pic));
        if (viewType == HkColType.ICON_1_LEFT_PIC.itemType) return new MovieOneColHolder(inflate.apply(R.layout.item_icon_one_col_left_pic));
        if (viewType == HkColType.MOVIE_1_VERTICAL_PIC.itemType) return new MovieOneColHolder(inflate.apply(R.layout.item_movie_one_col_vertical_pic));
        if (viewType == HkColType.MOVIE_1_VERTICAL_PIC_BLUR.itemType) return new MovieOneBlurColHolder(inflate.apply(R.layout.item_movie_one_col_vertical_pic_blur));
        if (viewType == HkColType.TEXT_1.itemType) return new TextOneColHolder(inflate.apply(R.layout.item_movie_one_col_nopic));
        if (viewType == HkColType.TEXT_CENTER_1.itemType) return new TextOneColHolder(inflate.apply(R.layout.item_movie_one_col_nopic_center));
        if (viewType == HkColType.TEXT_2.itemType) return new ChannelHolder(inflate.apply(R.layout.item_card_rect));
        if (viewType == HkColType.TEXT_3.itemType) return new TextThreeHolder(inflate.apply(R.layout.item_card_rect));
        if (viewType == HkColType.TEXT_4.itemType) return new TextFourHolder(inflate.apply(R.layout.item_card_rect));
        if (viewType == HkColType.TEXT_5.itemType) return new TextFiveHolder(inflate.apply(R.layout.item_card_rect_5));
        if (viewType == HkColType.LONG_TEXT.itemType) return new LongTextHolder(inflate.apply(R.layout.item_long_text));
        if (viewType == HkColType.RICH_TEXT.itemType) return new RichTextHolder(inflate.apply(R.layout.item_rich_text));
        if (viewType == HkColType.PIC_1.itemType) return new PicOneHolder(inflate.apply(R.layout.item_pic_one_col));
        if (viewType == HkColType.PIC_1_CARD.itemType) return new PicOneHolder(inflate.apply(R.layout.item_pic_one_card_col));
        if (viewType == HkColType.PIC_1_FULL.itemType) return new PicOneFullHolder(inflate.apply(R.layout.item_pic));
        if (viewType == HkColType.PIC_1_CENTER.itemType) return new PicOneCenterHolder(inflate.apply(R.layout.item_pic_center));
        if (viewType == HkColType.PIC_2.itemType) return new PicTwoHolder(inflate.apply(R.layout.item_pic_two_col));
        if (viewType == HkColType.PIC_3.itemType) return new PicThreeHolder(inflate.apply(R.layout.item_pic_three_col));
        if (viewType == HkColType.PIC_2_CARD.itemType) return new PicTwoCardHolder(inflate.apply(R.layout.item_pic_two_card_col));
        if (viewType == HkColType.PIC_3_SQUARE.itemType) return new PicThreeSquareHolder(inflate.apply(R.layout.item_pic_three_col));
        if (viewType == HkColType.MOVIE_2.itemType) return new PicTwoHolder(inflate.apply(R.layout.item_movie_two_col));
        if (viewType == HkColType.ICON_4.itemType) return new IconFourHolder(inflate.apply(R.layout.item_icon_four_col));
        if (viewType == HkColType.ICON_4_CARD.itemType) return new IconFourCardHolder(inflate.apply(R.layout.item_icon_four_card));
        if (viewType == HkColType.ICON_4_SMALL.itemType) return new IconFourHolder(inflate.apply(R.layout.item_icon_four_col_small));
        if (viewType == -3) return new IconThreeHolder(inflate.apply(R.layout.item_icon_three_col_small2));
        if (viewType == HkColType.ICON_3_SMALL.itemType) return new IconThreeHolder(inflate.apply(R.layout.item_icon_three_col_small));
        if (viewType == HkColType.ICON_3_ROUND_FILL.itemType) return new IconThreeFillHolder(inflate.apply(R.layout.item_icon_3_fill));
        if (viewType == HkColType.ICON_3_FILL.itemType) return new IconThreeFillHolder(inflate.apply(R.layout.item_icon_3_fill));
        if (viewType == HkColType.ICON_4_ROUND.itemType) return new IconFourRoundHolder(inflate.apply(R.layout.item_icon_four_col));
        if (viewType == HkColType.ICON_4_ROUND_SMALL.itemType) return new IconFourRoundHolder(inflate.apply(R.layout.item_icon_four_col_small));
        if (viewType == HkColType.ICON_5.itemType || viewType == HkColType.ICON_5_NO_CROP.itemType)
            return new IconFiveHolder(inflate.apply(R.layout.item_icon_four_col_small));
        if (viewType == HkColType.ICON_2.itemType) return new IconTwoHolder(inflate.apply(R.layout.item_icon_two_col));
        if (viewType == HkColType.ICON_2_ROUND.itemType) return new IconTwoRoundHolder(inflate.apply(R.layout.item_icon_two_round_col));
        if (viewType == HkColType.ICON_1_SEARCH.itemType) return new IconOneSearchHolder(inflate.apply(R.layout.item_icon_one_search_col));
        if (viewType == HkColType.LINE.itemType) return new LineHolder(inflate.apply(R.layout.item_line_col));
        if (viewType == HkColType.LINE_BLANK.itemType) return new LineHolder(inflate.apply(R.layout.item_line_blank_col));
        if (viewType == HkColType.MOVIE_3_MARQUEE.itemType) return new ToolsHolder(inflate.apply(R.layout.item_movie_tools_marquee));
        if (viewType == HkColType.AVATAR.itemType) return new AvatarHolder(inflate.apply(R.layout.item_avatar));
        if (viewType == HkColType.TEXT_ICON.itemType) return new AvatarHolder(inflate.apply(R.layout.item_text_icon));
        if (viewType == HkColType.X5_WEB_VIEW.itemType || viewType == HkColType.VIDEO.itemType)
            return new X5WebViewHolder(inflate.apply(R.layout.item_x5_webview));
        if (viewType == HkColType.BLANK_BLOCK.itemType) return new BlankBlockHolder(inflate.apply(R.layout.item_blank_block));
        if (viewType == HkColType.BIG_BLANK_BLOCK.itemType) return new BlankBlockHolder(inflate.apply(R.layout.item_big_blank_block));
        if (viewType == HkColType.BIG_BIG_BLANK_BLOCK.itemType) return new BlankBlockHolder(inflate.apply(R.layout.item_big_big_blank_block));
        if (viewType == HkColType.FLEX_BUTTON.itemType) return new FlexButtonHolder(inflate.apply(R.layout.item_flex_box));
        if (viewType == HkColType.SCROLL_BUTTON.itemType) return new ScrollButtonHolder(inflate.apply(R.layout.view_article_list_header));
        if (viewType == HkColType.CARD_PIC_3.itemType) return new CardPic3Holder(inflate.apply(R.layout.item_pic_square3));
        if (viewType == HkColType.CARD_PIC_3_CENTER.itemType) return new CardPic3Holder(inflate.apply(R.layout.item_pic_square3_center));
        if (viewType == HkColType.CARD_PIC_2_2.itemType || viewType == HkColType.CARD_PIC_2_2_LEFT.itemType)
            return new CardPic22Holder(inflate.apply(R.layout.item_pic_square2));
        if (viewType == HkColType.CARD_PIC_2.itemType || viewType == HkColType.CARD_PIC_1.itemType)
            return new CardPic2Holder(inflate.apply(R.layout.item_pic_square));
        if (viewType == HkColType.input.itemType) return new InputHolder(inflate.apply(R.layout.item_input));
        if (viewType == -1) return new BlankBlockHolder(inflate.apply(R.layout.item_flex_box_empty));
        if (viewType == -10) return new BlankBlockHolder(inflate.apply(R.layout.item_empty));
        return new ToolsHolder(inflate.apply(R.layout.item_movie_tools));
    }

    // ===== Holders（照抄各 ArticleListAdapter$*Holder 的字段与构造绑定） =====

    public static class MyViewHolder extends RecyclerView.ViewHolder {
        public TextView footer;
        public MyViewHolder(View itemView) {
            super(itemView);
            footer = itemView.findViewById(R.id.loadmre_footer);
        }
    }

    public static class HeaderHolder extends RecyclerView.ViewHolder {
        public TextView expandView;
        public LinearLayout linearLayout;
        public ObservableScrollView horizontalScrollView;
        public HeaderHolder(View itemView) {
            super(itemView);
            expandView = itemView.findViewById(R.id.res_header_item_expand);
            linearLayout = itemView.findViewById(R.id.res_header_linearlayout);
            horizontalScrollView = itemView.findViewById(R.id.res_header_scroll);
        }
    }

    public static class MovieOneColHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public TextView title;
        public TextView desc;
        public ImageView img;
        public View resultBg;
        @Override
        public ImageView getImageView() {
            return img;
        }

        public MovieOneColHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.movie_one_title);
            desc = itemView.findViewById(R.id.movie_one_desc);
            img = itemView.findViewById(R.id.item_reult_img);
            resultBg = itemView.findViewById(R.id.result_bg);
        }
    }

    public static class MovieOneBlurColHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public TextView title;
        public TextView desc;
        public ImageView img;
        public View resultBg;
        @Override
        public ImageView getImageView() {
            return img;
        }

        public MovieOneBlurColHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.movie_one_title);
            desc = itemView.findViewById(R.id.movie_one_desc);
            img = itemView.findViewById(R.id.item_reult_img);
            resultBg = itemView.findViewById(R.id.result_bg);
        }
    }

    public static class TextOneColHolder extends RecyclerView.ViewHolder {
        public TextView title;
        public TextView desc;
        public View resultBg;
        public TextOneColHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.movie_one_title);
            desc = itemView.findViewById(R.id.movie_one_desc);
            resultBg = itemView.findViewById(R.id.result_bg);
        }
    }

    public static class ChannelHolder extends RecyclerView.ViewHolder {
        public TextView title;
        public View resultBg;
        public ChannelHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.item_chapter_jishu);
            resultBg = itemView.findViewById(R.id.bg);
        }
    }

    public static class TextThreeHolder extends RecyclerView.ViewHolder {
        public TextView title;
        public View resultBg;
        public TextThreeHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.item_chapter_jishu);
            resultBg = itemView.findViewById(R.id.bg);
        }
    }

    public static class TextFourHolder extends RecyclerView.ViewHolder {
        public TextView title;
        public View resultBg;
        public TextFourHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.item_chapter_jishu);
            resultBg = itemView.findViewById(R.id.bg);
        }
    }

    public static class TextFiveHolder extends RecyclerView.ViewHolder {
        public TextView title;
        public View resultBg;
        public TextFiveHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.item_chapter_jishu);
            resultBg = itemView.findViewById(R.id.bg);
        }
    }

    public static class LongTextHolder extends RecyclerView.ViewHolder {
        public AlignTextView title;
        public LongTextHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.movie_one_title);
        }
    }

    public static class RichTextHolder extends RecyclerView.ViewHolder {
        public TextView title;
        public RichTextHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.movie_one_title);
        }
    }

    public static class PicOneHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public TextView title;
        public TextView desc;
        public ImageView img;
        public View containner;
        public View resultBg;
        @Override
        public ImageView getImageView() {
            return img;
        }

        public PicOneHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.reult_tools_title);
            desc = itemView.findViewById(R.id.reult_tools_desc);
            img = itemView.findViewById(R.id.item_reult_img);
            containner = itemView.findViewById(R.id.reult_tools_RelativeLayout);
            resultBg = itemView.findViewById(R.id.result_bg);
        }
    }

    public static class PicOneFullHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public ImageView imgView;
        @Override
        public ImageView getImageView() {
            return imgView;
        }

        public PicOneFullHolder(View itemView) {
            super(itemView);
            imgView = itemView.findViewById(R.id.item_reult_img);
        }
    }

    public static class PicOneCenterHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public ImageView imgView;
        @Override
        public ImageView getImageView() {
            return imgView;
        }

        public PicOneCenterHolder(View itemView) {
            super(itemView);
            imgView = itemView.findViewById(R.id.item_reult_img);
        }
    }

    public static class PicTwoHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public TextView title;
        public TextView desc;
        public ImageView img;
        public RelativeLayout containner;
        public View resultBg;
        @Override
        public ImageView getImageView() {
            return img;
        }

        public PicTwoHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.reult_tools_title);
            desc = itemView.findViewById(R.id.reult_tools_desc);
            img = itemView.findViewById(R.id.item_reult_img);
            containner = itemView.findViewById(R.id.reult_tools_RelativeLayout);
            resultBg = itemView.findViewById(R.id.result_bg);
        }
    }

    public static class PicThreeHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public ImageView img;
        public View resultBg;
        @Override
        public ImageView getImageView() {
            return img;
        }

        public PicThreeHolder(View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.item_reult_img);
            resultBg = itemView.findViewById(R.id.result_bg);
        }
    }

    public static class PicTwoCardHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public TextView title;
        public ImageView img;
        public View resultBg;
        @Override
        public ImageView getImageView() {
            return img;
        }

        public PicTwoCardHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.reult_tools_title);
            img = itemView.findViewById(R.id.item_reult_img);
            resultBg = itemView.findViewById(R.id.result_bg);
        }
    }

    public static class PicThreeSquareHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public ImageView img;
        public View resultBg;
        @Override
        public ImageView getImageView() {
            return img;
        }

        public PicThreeSquareHolder(View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.item_reult_img);
            resultBg = itemView.findViewById(R.id.result_bg);
        }
    }

    public static class IconFourHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public DrawableTextView resultBg;
        @Override
        public ImageView getImageView() {
            return resultBg == null ? null : resultBg.getImageView();
        }

        public IconFourHolder(View itemView) {
            super(itemView);
            resultBg = itemView.findViewById(R.id.bg);
        }
    }

    public static class IconFourCardHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public View resultBg;
        public ImageView img;
        public TextView textView;
        public TextView shortcut_icon_text;
        @Override
        public ImageView getImageView() {
            return img;
        }

        public IconFourCardHolder(View itemView) {
            super(itemView);
            resultBg = itemView.findViewById(R.id.bg);
            img = itemView.findViewById(R.id.item_reult_img);
            textView = itemView.findViewById(R.id.textView);
            shortcut_icon_text = itemView.findViewById(R.id.shortcut_icon_text);
        }
    }

    public static class IconThreeHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public DrawableInlineTextView resultBg;
        @Override
        public ImageView getImageView() {
            return resultBg == null ? null : resultBg.getImageView();
        }

        public IconThreeHolder(View itemView) {
            super(itemView);
            resultBg = itemView.findViewById(R.id.bg);
        }
    }

    public static class IconThreeFillHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public View resultBg;
        public View resultBg2;
        public ImageView imageView;
        public TextView textView;
        @Override
        public ImageView getImageView() {
            return imageView;
        }

        public IconThreeFillHolder(View itemView) {
            super(itemView);
            resultBg = itemView.findViewById(R.id.shortcut_bg);
            resultBg2 = itemView.findViewById(R.id.shortcut_bg2);
            imageView = itemView.findViewById(R.id.shortcut_icon);
            textView = itemView.findViewById(R.id.shortcut_text);
        }
    }

    public static class IconFourRoundHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public DrawableTextView resultBg;
        @Override
        public ImageView getImageView() {
            return resultBg == null ? null : resultBg.getImageView();
        }

        public IconFourRoundHolder(View itemView) {
            super(itemView);
            resultBg = itemView.findViewById(R.id.bg);
        }
    }

    public static class IconFiveHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public DrawableTextView resultBg;
        @Override
        public ImageView getImageView() {
            return resultBg == null ? null : resultBg.getImageView();
        }

        public IconFiveHolder(View itemView) {
            super(itemView);
            resultBg = itemView.findViewById(R.id.bg);
        }
    }

    public static class IconTwoHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public View resultBg;
        public View clickBg;
        public ImageView imageView;
        public TextView textView;
        @Override
        public ImageView getImageView() {
            return imageView;
        }

        public IconTwoHolder(View itemView) {
            super(itemView);
            resultBg = itemView.findViewById(R.id.bg);
            clickBg = itemView.findViewById(R.id.clickBg);
            imageView = itemView.findViewById(R.id.item_reult_img);
            textView = itemView.findViewById(R.id.textView);
        }
    }

    public static class IconTwoRoundHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public View resultBg;
        public View clickBg;
        public ImageView imageView;
        public TextView textView;
        @Override
        public ImageView getImageView() {
            return imageView;
        }

        public IconTwoRoundHolder(View itemView) {
            super(itemView);
            resultBg = itemView.findViewById(R.id.bg);
            clickBg = itemView.findViewById(R.id.clickBg);
            imageView = itemView.findViewById(R.id.item_reult_img);
            textView = itemView.findViewById(R.id.textView);
        }
    }

    public static class IconOneSearchHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public View resultBg;
        public View clickBg;
        public ImageView imageView;
        public ImageView imageViewEnd;
        public TextView textView;
        @Override
        public ImageView getImageView() {
            return imageView;
        }

        public IconOneSearchHolder(View itemView) {
            super(itemView);
            resultBg = itemView.findViewById(R.id.bg);
            clickBg = itemView.findViewById(R.id.clickBg);
            imageView = itemView.findViewById(R.id.item_reult_img);
            imageViewEnd = itemView.findViewById(R.id.item_reult_img_end);
            textView = itemView.findViewById(R.id.textView);
        }
    }

    public static class LineHolder extends RecyclerView.ViewHolder {
        public LineHolder(View itemView) {
            super(itemView);
        }
    }

    public static class ToolsHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public TextView title;
        public TextView desc;
        public ImageView img;
        public RelativeLayout containner;
        public View resultBg;
        public ToolsHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.reult_tools_title);
            desc = itemView.findViewById(R.id.reult_tools_desc);
            img = itemView.findViewById(R.id.item_reult_img);
            containner = itemView.findViewById(R.id.reult_tools_RelativeLayout);
            resultBg = itemView.findViewById(R.id.result_bg);
        }

        public ImageView getImageView() {
            return img;
        }
    }

    public static class AvatarHolder extends RecyclerView.ViewHolder implements HkImageHolder {
        public View resultBg;
        public ImageView imageView;
        public TextView textView;
        public TextView descView;
        public TextView shortcut_icon_text;
        @Override
        public ImageView getImageView() {
            return imageView;
        }

        public AvatarHolder(View itemView) {
            super(itemView);
            resultBg = itemView.findViewById(R.id.bg);
            imageView = itemView.findViewById(R.id.item_reult_img);
            textView = itemView.findViewById(R.id.textView);
            descView = itemView.findViewById(R.id.descView);
            shortcut_icon_text = itemView.findViewById(R.id.shortcut_icon_text);
        }
    }

    public static class BlankBlockHolder extends RecyclerView.ViewHolder {
        public BlankBlockHolder(View itemView) {
            super(itemView);
        }
    }

    public static class FlexButtonHolder extends RecyclerView.ViewHolder {
        public FlexboxLayout flexboxLayout;
        public FlexButtonHolder(View itemView) {
            super(itemView);
            flexboxLayout = itemView.findViewById(R.id.flexboxLayout);
        }
    }

    public static class ScrollButtonHolder extends RecyclerView.ViewHolder {
        public TextView expandView;
        public LinearLayout linearLayout;
        public ObservableScrollView horizontalScrollView;
        public boolean layoutFinish;
        public boolean isLayoutFinish() {
            return layoutFinish;
        }
        public void setLayoutFinish(boolean layoutFinish) {
            this.layoutFinish = layoutFinish;
        }
        public ScrollButtonHolder(View itemView) {
            super(itemView);
            expandView = itemView.findViewById(R.id.res_header_item_expand);
            linearLayout = itemView.findViewById(R.id.res_header_linearlayout);
            horizontalScrollView = itemView.findViewById(R.id.res_header_scroll);
        }
    }

    public static class CardPic3Holder extends RecyclerView.ViewHolder implements HkImageHolder {
        public View resultBg;
        public View imgBg;
        public ImageView img;
        public TextView textView;
        @Override
        public ImageView getImageView() {
            return img;
        }

        public CardPic3Holder(View itemView) {
            super(itemView);
            resultBg = itemView.findViewById(R.id.item_bg);
            imgBg = itemView.findViewById(R.id.img_bg);
            img = itemView.findViewById(R.id.item_reult_img);
            textView = itemView.findViewById(R.id.textView);
        }
    }

    public static class CardPic22Holder extends RecyclerView.ViewHolder implements HkImageHolder {
        public View resultBg;
        public View resultBg1;
        public View resultBg2;
        public ImageView img1;
        public ImageView img2;
        public TextView textView1;
        public TextView textView2;
        @Override
        public ImageView getImageView() {
            return img1;
        }

        public CardPic22Holder(View itemView) {
            super(itemView);
            resultBg = itemView.findViewById(R.id.item_bg);
            resultBg1 = itemView.findViewById(R.id.item_bg1);
            resultBg2 = itemView.findViewById(R.id.item_bg2);
            img1 = itemView.findViewById(R.id.item_reult_img);
            img2 = itemView.findViewById(R.id.item_reult_img2);
            textView1 = itemView.findViewById(R.id.textView);
            textView2 = itemView.findViewById(R.id.textView2);
        }
    }

    public static class CardPic2Holder extends RecyclerView.ViewHolder implements HkImageHolder {
        public View resultBg;
        public ImageView img;
        public TextView textView;
        @Override
        public ImageView getImageView() {
            return img;
        }

        public CardPic2Holder(View itemView) {
            super(itemView);
            resultBg = itemView.findViewById(R.id.item_bg);
            img = itemView.findViewById(R.id.item_reult_img);
            textView = itemView.findViewById(R.id.textView);
        }
    }

    public static class InputHolder extends RecyclerView.ViewHolder {
        public TextView search;
        public EditText edit;
        public View clearBtn;
        public View divider;
        public View pop_input_edit_bg;
        public LinearLayout search_suggest;
        public InputHolder(View itemView) {
            super(itemView);
            search = itemView.findViewById(R.id.search);
            edit = itemView.findViewById(R.id.edit);
            clearBtn = itemView.findViewById(R.id.clearBtn);
            divider = itemView.findViewById(R.id.divider);
            pop_input_edit_bg = itemView.findViewById(R.id.pop_input_edit_bg);
            search_suggest = itemView.findViewById(R.id.search_suggest);
        }
    }

    public static class X5WebViewHolder extends RecyclerView.ViewHolder {
        public RelativeLayout resultBg;
        public X5WebViewHolder(View itemView) {
            super(itemView);
            resultBg = itemView.findViewById(R.id.bg);
        }
    }
}
