package com.fongmi.android.tv.utils;

import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static android.widget.ImageView.ScaleType.FIT_CENTER;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.utils.Json;
import com.google.common.net.HttpHeaders;
import com.orhanobut.logger.Logger;

import org.json.JSONObject;

import java.io.File;
import java.util.Map;

public class ImgUtil {

    private static final String TAG = "ImgUtil";

    public static void logo(ImageView view) {
        try {
            Glide.with(view).load(UrlUtil.convert(VodConfig.get().getConfig().getLogo())).circleCrop().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).error(R.drawable.ic_logo).into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(String url, CustomTarget<Bitmap> target) {
        load(url, 96, target);
    }

    /** 带目标尺寸的位图取图：大图面（CD 盘面／唱片架袖套，显示径可达 400dp）须按显示尺寸取，
     *  96dp 默认档贴到大面必然糊。默认重载保持 96 不动（首页小卡／榜单等小图面共用）。 */
    public static void load(String url, int sizeDp, CustomTarget<Bitmap> target) {
        try {
            Glide.with(App.get()).asBitmap().load(getUrl(url)).override(ResUtil.dp2px(sizeDp), ResUtil.dp2px(sizeDp)).error(R.drawable.artwork).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(Context context, String url, CustomTarget<Drawable> target) {
        try {
            // 大图面目标路：把 200ms crossfade 过渡交给目标，目标经 setWithTransition 落图才渐显；
            // 忽略过渡的目标行为不变。
            Glide.with(context).load(getUrl(url)).override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).error(R.drawable.artwork).transition(DrawableTransitionOptions.withCrossFade(200)).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(Context context, String url, int width, int height, CustomTarget<Drawable> target) {
        try {
            Glide.with(context).load(getUrl(url)).override(width, height).error(R.drawable.artwork).transition(DrawableTransitionOptions.withCrossFade(200)).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /** 目标路落图：有过渡时按 Glide 过渡把新图渐显进 ImageView（与旧图交叉淡化），否则直接贴。 */
    public static void setWithTransition(ImageView view, Drawable resource, com.bumptech.glide.request.transition.Transition<? super Drawable> transition) {
        if (view == null) return;
        if (resource == null || transition == null) {
            view.setImageDrawable(resource);
            return;
        }
        transition.transition(resource, new com.bumptech.glide.request.transition.Transition.ViewAdapter() {
            @Override
            public View getView() {
                return view;
            }

            @Override
            public Drawable getCurrentDrawable() {
                return view.getDrawable();
            }

            @Override
            public void setDrawable(Drawable drawable) {
                view.setImageDrawable(drawable);
            }
        });
    }

    public static void preload(Context context, String url) {
        if (TextUtils.isEmpty(url)) return;
        try {
            Glide.with(context).load(getUrl(url)).override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).preload();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /** 带页面上下文的预加载（漫画阅读器用）：与显示同路组装，不再降级走单参路。 */
    public static void preload(Context context, String url, String ruleUrl, Map<String, String> fillHeaders) {
        if (TextUtils.isEmpty(url)) return;
        try {
            Glide.with(context).load(getUrl(url, ruleUrl, fillHeaders)).override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).preload();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(String text, String url, ImageView view) {
        load(text, url, view, true);
    }

    public static void load(String text, String url, ImageView view, boolean vod) {
        load(text, url, view, vod, 0, 0);
    }

    public static void load(String text, String url, ImageView view, int width, int height) {
        load(text, url, view, true, width, height);
    }

    public static void load(String text, String url, ImageView view, boolean vod, int width, int height) {
        load(text, url, view, vod, width, height, null);
    }

    /** 海阔列表：ruleUrl 为当前规则页 URL，用于 8.83 同款自动 Referer（同域防盗链）。 */
    public static void load(String text, String url, ImageView view, String ruleUrl) {
        load(text, url, view, true, 0, 0, ruleUrl);
    }

    public static void load(String text, String url, ImageView view, boolean vod, int width, int height, String ruleUrl) {
        load(text, url, view, vod, width, height, ruleUrl, null);
    }

    /** 大图面渐显版（与三参 load 同路组装）：图到时 200ms crossfade 渐显。
     *  只许非回收的大图面调用（详情/面板大图等）；列表回收行走普通 load 即贴，不许走这条（回收重绑渐显会闪）。 */
    public static void loadFade(String text, String url, ImageView view) {
        load(text, url, view, true, 0, 0, null, null, true);
    }

    /** 在 {@link #load(String, String, ImageView, boolean, int, int, String)} 基础上补会话请求头（漫画阅读器用）。 */
    public static void load(String text, String url, ImageView view, boolean vod, int width, int height, String ruleUrl, Map<String, String> fillHeaders) {
        load(text, url, view, vod, width, height, ruleUrl, fillHeaders, false);
    }

    private static void load(String text, String url, ImageView view, boolean vod, int width, int height, String ruleUrl, Map<String, String> fillHeaders, boolean fade) {
        view.setScaleType(vod ? CENTER_CROP : FIT_CENTER);
        if (!vod) view.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
        if (TextUtils.isEmpty(url)) showEmptyCover(view, text, false, 0);
        else try {
            RequestBuilder<Drawable> builder = Glide.with(view).load(getUrl(url, ruleUrl, fillHeaders)).listener(getListener(text, url, view, vod));
            if (width > 0 && height > 0) builder.override(width, height);
            if (fade) builder.transition(DrawableTransitionOptions.withCrossFade(200));
            builder.placeholder(getTextDrawable(text, vod));
            // D4：海阔列表分支（ruleUrl 非空）与 8.83 ArticleListRuleAdapter 对齐，
            // 用 DiskCacheStrategy.ALL；点播/主 App（ruleUrl 为空）保持默认 AUTOMATIC 不动。
            if (ruleUrl != null) builder.diskCacheStrategy(DiskCacheStrategy.ALL);
            if (vod) builder.centerCrop().into(view);
            else builder.fitCenter().into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * 圆形裁剪加载（对照 8.83 icon_round_4 / icon_round_small_4 的 bindCircleCropImageView）。
     */
    public static void loadCircle(String text, String url, ImageView view) {
        loadCircle(text, url, view, null);
    }

    /** 圆形封面渐显版：只许非回收的单图面调用（随心听唱片封面等）；回收行仍走 loadCircle 即贴。 */
    public static void loadCircleFade(String text, String url, ImageView view) {
        view.setScaleType(CENTER_CROP);
        if (TextUtils.isEmpty(url)) showEmptyCover(view, text, true, -1);
        else try {
            Glide.with(view).load(getUrl(url)).listener(getListener(text, url, view, true)).placeholder(getTextDrawable(text, true)).transition(DrawableTransitionOptions.withCrossFade(200)).circleCrop().into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /** 海阔列表：ruleUrl 为当前规则页 URL，用于 8.83 同款自动 Referer。 */
    public static void loadCircle(String text, String url, ImageView view, String ruleUrl) {
        view.setScaleType(CENTER_CROP);
        if (TextUtils.isEmpty(url)) showEmptyCover(view, text, true, -1);
        else try {
            // 与其余变体同口径补透明 placeholder：不补时加载中不清空视图，
            // 回收行会短暂残留上一行的旧图（失败口径仍走透明，不变）。
            Glide.with(view).load(getUrl(url, ruleUrl)).listener(getListener(text, url, view, true)).placeholder(getTextDrawable(text, true)).circleCrop().into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * 顶部圆角加载（Vod.Rect 网格：上 8dp，下直角）。
     * 二期优化：替代 ShapeableImageView 的 GPU 实时裁剪，解码时一次裁好。
     */
    public static void loadTopRounded(String text, String url, ImageView view, int width, int height) {
        view.setScaleType(CENTER_CROP);
        // 素图圆角必须与成功图的 GranularRoundedCorners(8,8,0,0) 逐角一致：
        // 只圆上两角，底部两角保持直角，否则素图底角多圆一圈透出底色。
        if (TextUtils.isEmpty(url)) {
            float r = ResUtil.dp2px(8);
            showEmptyCover(view, text, false, new float[]{r, r, r, r, 0, 0, 0, 0});
        }
        else try {
            int r = ResUtil.dp2px(8);
            RequestBuilder<Drawable> builder = Glide.with(view).load(getUrl(url)).listener(getListener(text, url, view, true));
            if (width > 0 && height > 0) builder.override(width, height);
            builder.placeholder(getTextDrawable(text, true));
            builder.transform(new CenterCrop(), new GranularRoundedCorners(r, r, 0, 0));
            builder.into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * 全圆角加载（Vod.List 列表：四角 8dp）。
     * 二期优化：替代 ShapeableImageView 的 GPU 实时裁剪，解码时一次裁好。
     */
    public static void loadRounded(String text, String url, ImageView view) {
        view.setScaleType(CENTER_CROP);
        if (TextUtils.isEmpty(url)) showEmptyCover(view, text, false, ResUtil.dp2px(8));
        else try {
            int r = ResUtil.dp2px(8);
            RequestBuilder<Drawable> builder = Glide.with(view).load(getUrl(url)).listener(getListener(text, url, view, true));
            builder.placeholder(getTextDrawable(text, true));
            builder.transform(new CenterCrop(), new RoundedCorners(r));
            builder.into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /** 第 9 项：猜你喜欢叠卡单张圆角 16dp（loadRounded 写死 8dp，不够用，单张照 GZ 16dp 另起一个） */
    public static void loadRounded16(String text, String url, ImageView view) {
        view.setScaleType(CENTER_CROP);
        if (TextUtils.isEmpty(url)) showEmptyCover(view, text, false, ResUtil.dp2px(16));
        else try {
            int r = ResUtil.dp2px(16);
            RequestBuilder<Drawable> builder = Glide.with(view).load(getUrl(url)).listener(getListener(text, url, view, true));
            builder.placeholder(getTextDrawable(text, true));
            builder.transform(new CenterCrop(), new RoundedCorners(r));
            builder.into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /** 大图面渐显版 loadRounded16：只许非回收的大图面调用（榜单详情页头图/猜你喜欢大卡）；回收行走 loadRounded16 即贴。 */
    public static void loadRounded16Fade(String text, String url, ImageView view) {
        view.setScaleType(CENTER_CROP);
        if (TextUtils.isEmpty(url)) showEmptyCover(view, text, false, ResUtil.dp2px(16));
        else try {
            int r = ResUtil.dp2px(16);
            RequestBuilder<Drawable> builder = Glide.with(view).load(getUrl(url)).listener(getListener(text, url, view, true));
            builder.placeholder(getTextDrawable(text, true));
            builder.transition(DrawableTransitionOptions.withCrossFade(200));
            builder.transform(new CenterCrop(), new RoundedCorners(r));
            builder.into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /** 第 3 项：榜单详情行封面圆角 10dp（loadRounded 写死 8dp，榜单详情行照 GZ 10dp 另起一个）。 */
    public static void loadRounded10(String text, String url, ImageView view) {
        view.setScaleType(CENTER_CROP);
        if (TextUtils.isEmpty(url)) showEmptyCover(view, text, false, ResUtil.dp2px(10));
        else try {
            int r = ResUtil.dp2px(10);
            RequestBuilder<Drawable> builder = Glide.with(view).load(getUrl(url)).listener(getListener(text, url, view, true));
            builder.placeholder(getTextDrawable(text, true));
            builder.transform(new CenterCrop(), new RoundedCorners(r));
            builder.into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * 图标失败记忆（用户 2026-10-23 亲定，推翻旧「失败不拉黑、重绑重试」口径）：
     * 拉不到的图标不许每次进页/每次绑定都重试——失败一次记 24 小时（进程内集＋落盘），
     * 命中者直接显示默认块、不发请求；到期才许再试一次；成功即清除记忆。
     * 只记 http(s) 图标（data:/本地文件不记）。注意：这是兜底，不是失败根因的修复。
     */
    private static final long ICON_FAIL_TTL_MS = 24L * 60 * 60 * 1000;
    private static final java.util.Map<String, Long> ICON_FAIL_MEM = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String ICON_FAIL_PREFS = "hk_icon_fail";

    private static boolean iconFailedRecently(String url) {
        try {
            Long t = ICON_FAIL_MEM.get(url);
            if (t == null) {
                long stored = App.get().getSharedPreferences(ICON_FAIL_PREFS, Context.MODE_PRIVATE).getLong(iconFailKey(url), 0);
                if (stored > 0) {
                    t = stored;
                    ICON_FAIL_MEM.put(url, t);
                }
            }
            return t != null && System.currentTimeMillis() - t < ICON_FAIL_TTL_MS;
        } catch (Throwable e) {
            return false;
        }
    }

    private static void markIconFailed(String url) {
        try {
            long now = System.currentTimeMillis();
            ICON_FAIL_MEM.put(url, now);
            App.get().getSharedPreferences(ICON_FAIL_PREFS, Context.MODE_PRIVATE).edit().putLong(iconFailKey(url), now).apply();
        } catch (Throwable ignored) {
        }
    }

    private static void clearIconFailed(String url) {
        try {
            if (ICON_FAIL_MEM.remove(url) != null) {
                App.get().getSharedPreferences(ICON_FAIL_PREFS, Context.MODE_PRIVATE).edit().remove(iconFailKey(url)).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    private static String iconFailKey(String url) {
        return Integer.toHexString(url.hashCode()) + "_" + url.length();
    }

    /**
     * 小程序列表图标：URL 图标加载。对照 8.83
     * ArticleListRuleAdapter.smali:194（icon_mini_program_icon）：失败时用默认图标占位。
     * 失败重试口径按用户 2026-10-23 定：走上方失败记忆（24 小时），不再每次绑定重试。
     */
    public static void loadRuleIcon(String title, String url, ImageView view) {
        view.setScaleType(CENTER_CROP);
        view.setContentDescription(title);
        if (TextUtils.isEmpty(url)) {
            clearRequest(view);
            view.setImageDrawable(defaultMiniIcon(view));
            return;
        }
        boolean httpIcon = url.startsWith("http://") || url.startsWith("https://");
        // 命中失败记忆：直接稳稳显示默认块，不发请求不闪
        if (httpIcon && iconFailedRecently(url)) {
            clearRequest(view);
            view.setImageDrawable(defaultMiniIcon(view));
            return;
        }
        try {
            // 8.83 ArticleListRuleAdapter: getGlideUrl(getIcon(), getIcon())——图标自身 URL 当 ruleUrl，
            // 走完整后缀链（@js= 剥离+流式解密、@Headers=/@Cookie=/@Referer=/@User-Agent= 剥离组装）。
            // line173 边界：最终 URL==ruleUrl 时自动 Referer 置空，与原版逐行一致，不自创。
            // data:/本地文件等非 http 沿用原单参数逻辑，行为不变。
            Object model = httpIcon ? getUrl(url, url) : getUrl(url);
            if (model == null) {
                clearRequest(view);
                view.setImageDrawable(defaultMiniIcon(view));
                return;
            }
            final String failKey = httpIcon ? url : null;
            Glide.with(view).load(model).placeholder(defaultMiniIcon(view)).listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                    if (failKey != null) markIconFailed(failKey);
                    view.setImageDrawable(defaultMiniIcon(view));
                    return true;
                }

                @Override
                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                    if (failKey != null) clearIconFailed(failKey);
                    return false;
                }
            }).centerCrop().into(view);
        } catch (Throwable e) {
            e.printStackTrace();
            clearRequest(view);
            view.setImageDrawable(defaultMiniIcon(view));
        }
    }

    /** 8.83 icon_mini_program_icon 同款默认图标。 */
    private static Drawable defaultMiniIcon(ImageView view) {
        try {
            return androidx.appcompat.content.res.AppCompatResources.getDrawable(view.getContext(), R.drawable.icon_mini_program_icon);
        } catch (Throwable e) {
            return new ColorDrawable(Color.TRANSPARENT);
        }
    }

    /**
     * 壳子行为对标默影视（照抄）：解析 @Headers=/@Cookie=/@Referer=/@User-Agent= 后缀，
     * 剥离后组装 GlideUrl。之前走 getUrl(url, null) 直接返回原串，@后缀留在 URL 里发给服务器导致 404。
     */
    public static Object getUrl(String url) {
        String param = null;
        url = UrlUtil.convert(url);
        if (url.startsWith("data:")) return url;
        // content:// 是本地内容地址（MediaStore 相册封面等），Glide 有原生加载器能开；
        // 一旦包成 GlideUrl 就会被当 http 取图（new URL 直接抛未知协议）必然失败——
        // 本地音乐封面全黑的根因。原样返回字符串，交给 Glide 的 content 加载器。
        if (url.startsWith("content://") || url.startsWith("android.resource://")) return url;
        // 本地文件绝对路径（网盘截帧封面等落盘图）：原样返回交给 Glide 的文件加载器；
        // 包成 GlideUrl 会被当 http 取图必然失败——同 content:// 口径。只认真实存在的文件，
        // 其余以 / 开头的串维持原行为。
        if (url.startsWith("/") && new File(url).isFile()) return url;
        LazyHeaders.Builder builder = new LazyHeaders.Builder();
        // @js=（与双参版对齐补上）：此前单参路不认这个后缀，只带 @js= 的图四个后缀
        // 都不命中、整串连后缀当 URL 发出必 404。剥后缀并把代码组进 "js" 头，
        // 由 HkOkHttpStreamFetcher 做流式解密（该头不发服务器，抓图器口径）。
        if (url.contains("@js=")) {
            String[] jsParts = url.split("@js=");
            if (jsParts.length > 1) {
                url = jsParts[0];
                builder.addHeader("js", jsParts[1]);
            }
        }
        if (url.contains("@Headers=")) addHeader(builder, param = url.split("@Headers=")[1].split("@")[0]);
        if (url.contains("@Cookie=")) builder.addHeader(HttpHeaders.COOKIE, param = url.split("@Cookie=")[1].split("@")[0]);
        if (url.contains("@Referer=")) builder.addHeader(HttpHeaders.REFERER, param = url.split("@Referer=")[1].split("@")[0]);
        if (url.contains("@User-Agent=")) builder.addHeader(HttpHeaders.USER_AGENT, param = url.split("@User-Agent=")[1].split("@")[0]);
        url = param == null ? url : url.split("@")[0];
        return TextUtils.isEmpty(url) ? null : new GlideUrl(url, builder.build());
    }

    private static void addHeader(LazyHeaders.Builder builder, String header) {
        Map<String, String> map = Json.toMap(Json.parse(header));
        for (Map.Entry<String, String> entry : map.entrySet()) builder.addHeader(UrlUtil.fixHeader(entry.getKey()), entry.getValue());
    }

    /**
     * 8.83 {@code GlideUtil.getGlideUrl(ruleUrl, picUrl)} 语义移植。
     *
     * <p>处理顺序（对照反编译行号）：hiker://images/ → drawable（宿主无 8.83 的
     * ImageUrlMapEnum R 表，返回 null 走文字占位，不猜映射）；#rrggbb → ColorDrawable；
     * 非 http：hiker://files/ → file://、去 #originalSize#；http(s)：去 #originalSize#、
     * gitee 自动 Referer、同域自动 Referer、默认 UA，解析 @js=/@headers=/@Cookie=/
     * @Referer=/@User-Agent= 后缀组装 GlideUrl。@js= 由 {@code HkOkHttpStreamFetcher}
     * 做 8.83 同款流式解密（header "js" 不发给服务器）。</p>
     */
    public static Object getUrl(String picUrl, String ruleUrl) {
        if (picUrl == null) return null;
        // 壳子路径（ruleUrl 为空）：走默影视逻辑（@后缀解析剥离），不走 8.83 的"原串返回"
        if (TextUtils.isEmpty(ruleUrl)) return getUrl(picUrl);
        try {
            // 1. hiker://images/* → 8.83 查 ImageUrlMapEnum 得 drawable id；
            //    宿主无法还原 8.83 的 R 表映射，返回 null 走文字占位（不猜）。
            if (picUrl.startsWith("hiker://images/")) return null;
            // 2. # 开头 → 纯色块
            if (!picUrl.isEmpty() && picUrl.startsWith("#")) {
                try {
                    return new ColorDrawable(Color.parseColor(picUrl));
                } catch (Throwable t) {
                    Logger.t(TAG).d("parseColor failed: %s", picUrl);
                }
            }
            String url = UrlUtil.convert(picUrl);
            if (url.startsWith("data:")) return url;
            boolean http = url.startsWith("http");
            // 3. 非 http 分支（8.83 line 80-89）
            if (!http) {
                if (!TextUtils.isEmpty(url) && url.startsWith("hiker://files/")) {
                    // 8.83: "file://" + SettingConfig.rootDir(=getExternalFilesDir(DOCUMENTS)) + rest，
                    // 与 HkJsRuntime.getFilePath / fetchByHiker 保持同一目录（之前误用内部 filesDir 导致找不到文件）
                    String rest = url.replace("hiker://files/", "");
                    File docs = App.get().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                    String root = (docs != null) ? docs.getAbsolutePath() : App.get().getFilesDir().getAbsolutePath();
                    return "file://" + root + File.separator + rest;
                }
                try {
                    if (!TextUtils.isEmpty(url)) url = url.replace("#originalSize#", "");
                } catch (Throwable t) {
                    Logger.t(TAG).d("strip originalSize failed");
                }
                return url;
            }
            // 4. http(s) 主分支
            url = url.replace("#originalSize#", "");
            if (url.contains("gitee.com") && !url.contains("@Referer=")) url += "@Referer=https://gitee.com";
            LazyHeaders.Builder builder = new LazyHeaders.Builder();
            // 自动 Referer（8.83 line 103-116）
            String ruleDom = getDom(ruleUrl);
            String picDom = getDom(url);
            String autoReferer;
            if (TextUtils.equals(ruleDom, picDom)
                    || (!TextUtils.isEmpty(ruleDom) && !TextUtils.isEmpty(picDom)
                        && (ruleDom.contains(picDom) || picDom.contains(ruleDom)))) {
                autoReferer = (ruleUrl.startsWith("https") ? "https://" : "http://") + ruleDom + "/";
            } else {
                autoReferer = "";
            }
            String ua = defaultUa();
            // @js=（小写，8.83 line 119-124）：只取第 2 段
            String[] jsParts = url.split("@js=");
            if (jsParts.length > 1) {
                url = jsParts[0];
                builder.addHeader("js", jsParts[1]);
            }
            // @Headers=（大写，TVBox/PY 约定；壳子 getUrl(String) 已支持，小程序路径补上——
            // 否则后缀留在 URL 里发给服务器导致 404。JSON 体取到下一个 @ 或结尾，兼容组合后缀）
            if (url.contains("@Headers=")) {
                String after = url.split("@Headers=", 2)[1];
                // 取平衡的 JSON 对象（兼容值内含 @ 的极端情况）
                int brace = after.indexOf('{');
                if (brace >= 0) {
                    int depth = 0, end = -1;
                    for (int i = brace; i < after.length(); i++) {
                        char c = after.charAt(i);
                        if (c == '{') depth++;
                        else if (c == '}') { depth--; if (depth == 0) { end = i; break; } }
                    }
                    if (end > brace) {
                        String json = after.substring(brace, end + 1);
                        url = url.split("@Headers=")[0] + after.substring(end + 1);
                        try {
                            JSONObject o = new JSONObject(json);
                            java.util.Iterator<String> it = o.keys();
                            while (it.hasNext()) {
                                String k = it.next();
                                builder.addHeader(UrlUtil.fixHeader(k), o.optString(k, ""));
                            }
                        } catch (Throwable t) {
                            Logger.t(TAG).d("parse @Headers= failed");
                        }
                    }
                }
            }
            // @headers=（8.83 line 124-138）：[1] 须以 { 开头 } 结尾
            String[] hParts = url.split("@headers=");
            if (hParts.length > 1 && hParts[1].startsWith("{") && hParts[1].endsWith("}")) {
                url = hParts[0];
                try {
                    JSONObject o = new JSONObject(hParts[1]);
                    java.util.Iterator<String> it = o.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        builder.addHeader(k, o.optString(k, ""));
                    }
                } catch (Throwable t) {
                    Logger.t(TAG).d("parse @headers= failed");
                }
            }
            // @Cookie=（8.83 line 139-145）
            String[] cParts = url.split("@Cookie=");
            if (cParts.length > 1) {
                url = cParts[0];
                builder.addHeader(HttpHeaders.COOKIE, cParts[1]);
            }
            // @Referer= / @User-Agent= 组合（8.83 line 146-171）
            // C6：split 用 -1 保留尾空串，显式 @Referer=（空值）清掉自动 Referer
            String referer;
            String finalUa;
            String[] rParts = url.split("@Referer=", -1);
            if (rParts.length > 1) {
                url = rParts[0];
                if (url.contains("@User-Agent=")) {
                    // @User-Agent= 在 @Referer= 之前
                    referer = rParts[1];
                    String[] uParts = url.split("@User-Agent=");
                    url = uParts[0];
                    finalUa = uParts[1];
                } else if (rParts[1].contains("@User-Agent=")) {
                    // @User-Agent= 在 @Referer= 之后
                    String[] uParts = rParts[1].split("@User-Agent=");
                    referer = uParts[0];
                    finalUa = uParts[1];
                } else {
                    referer = rParts[1];
                    finalUa = ua;
                }
            } else {
                if (url.contains("@User-Agent=")) {
                    String[] uParts = url.split("@User-Agent=");
                    finalUa = uParts[1];
                    url = uParts[0];
                } else {
                    finalUa = ua;
                }
                referer = autoReferer;
            }
            // 边界（8.83 line 173）：最终 URL == ruleUrl 且原串无 @Referer= → Referer 置空
            if (TextUtils.equals(ruleUrl, url) && !url.contains("@Referer=")) referer = "";
            // Referer 非空但不是 http 开头 → 置空（8.83 line 176）
            String refererHeader = (!TextUtils.isEmpty(referer) && referer.startsWith("http")) ? referer : "";
            // 8.83：glideUA 为空时不拼 User-Agent 头（之前无条件拼空 UA）
            if (!TextUtils.isEmpty(finalUa)) builder.addHeader(HttpHeaders.USER_AGENT, finalUa);
            if (!TextUtils.isEmpty(refererHeader)) builder.addHeader(HttpHeaders.REFERER, refererHeader);
            return TextUtils.isEmpty(url) ? null : new GlideUrl(url, builder.build());
        } catch (Throwable t) {
            Logger.t(TAG).d("getUrl failed: %s", t.getMessage());
            return null;
        }
    }

    /**
     * 双参组装 + 会话请求头补缺（漫画阅读器用）：先按 {@link #getUrl(String, String)}
     * 与文章页同路组装，再把 fill 里图串自身没给出的头补进 GlideUrl——
     * 已组装出的头（图串 @后缀、自动 Referer、默认 UA）一律不覆盖，键名大小写不敏感。
     * 跨域图床算不出自动 Referer 时，就靠这一步把 play 的 Referer/UA/Cookie 递过去。
     * 结果不是 GlideUrl（data:/色块/本地文件等）时原样返回，fill 不适用。
     */
    public static Object getUrl(String picUrl, String ruleUrl, Map<String, String> fill) {
        Object model = getUrl(picUrl, ruleUrl);
        if (fill == null || fill.isEmpty() || !(model instanceof GlideUrl)) return model;
        GlideUrl gu = (GlideUrl) model;
        Map<String, String> existing;
        try {
            existing = gu.getHeaders();
        } catch (Throwable t) {
            return model;
        }
        boolean anyMissing = false;
        for (Map.Entry<String, String> e : fill.entrySet()) {
            if (e.getKey() == null || TextUtils.isEmpty(e.getValue())) continue;
            if (!hasHeaderKey(existing, e.getKey())) {
                anyMissing = true;
                break;
            }
        }
        if (!anyMissing) return model;
        LazyHeaders.Builder builder = new LazyHeaders.Builder();
        if (existing != null) {
            for (Map.Entry<String, String> e : existing.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) builder.addHeader(e.getKey(), e.getValue());
            }
        }
        for (Map.Entry<String, String> e : fill.entrySet()) {
            if (e.getKey() == null || TextUtils.isEmpty(e.getValue())) continue;
            if (!hasHeaderKey(existing, e.getKey())) builder.addHeader(UrlUtil.fixHeader(e.getKey()), e.getValue());
        }
        return new GlideUrl(gu.toStringUrl(), builder.build());
    }

    private static boolean hasHeaderKey(Map<String, String> headers, String key) {
        if (headers == null || key == null) return false;
        for (String k : headers.keySet()) {
            if (k != null && k.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    /** 8.83 默认 UA（SettingConfig.getGlideUA 同款语义：默认为 null，不发 UA 头）。 */
    private static String defaultUa() {
        try {
            String ua = Setting.getUa();
            return ua == null ? "" : ua;
        } catch (Throwable t) {
            return "";
        }
    }

    /** 8.83 StringUtil.getDom 语义：取 host（含 getRealUrl 预处理；特殊 scheme 直接返回原串）。 */
    private static String getDom(String url) {
        if (TextUtils.isEmpty(url)) return url;
        try {
            String u = getRealUrl(url);
            // 8.83 getDom0：thunder/magnet/ftp/ed2k/file 直接返回原串，不取 host
            if (u.startsWith("thunder://") || u.startsWith("magnet:") || u.startsWith("ftp://")
                    || u.startsWith("ed2k:") || u.startsWith("file://")) {
                return u;
            }
            String[] parts = u.split("://");
            if (parts.length > 1) return parts[1].split("/")[0];
            parts = u.split("//");
            if (parts.length > 1) return parts[1].split("/")[0];
            return u.replaceFirst("http://", "").replaceFirst("https://", "").replaceFirst("hiker://", "").split("/")[0];
        } catch (Throwable t) {
            return "";
        }
    }

    /** 8.83 StringUtil.getRealUrl 简化版：去 @lazyRule=/@rule= 后缀与 hiker://empty 前缀。 */
    private static String getRealUrl(String url) {
        if (TextUtils.isEmpty(url)) return url;
        try {
            String u = url.split("@lazyRule=")[0].split("@rule=")[0];
            if (u.startsWith("hiker://empty#http")) u = replaceOnce(u, "hiker://empty#", "");
            else if (u.startsWith("webview://")) u = u.replaceFirst("webview://", "");
            else if (u.startsWith("hiker://empty##http") || u.startsWith("hiker://empty##https%3A%2F%2F") || u.startsWith("hiker://empty##http%3A%2F%2F"))
                u = replaceOnce(u, "hiker://empty##", "");
            else if (u.startsWith("hiker://empty$http")) u = replaceOnce(u, "hiker://empty$", "");
            else if (u.startsWith("hiker://empty$$http")) u = replaceOnce(u, "hiker://empty$$", "");
            int d = u.indexOf("$$$");
            if (d >= 0) u = u.substring(0, d);
            return u;
        } catch (Throwable t) {
            return url;
        }
    }

    private static String replaceOnce(String s, String from, String to) {
        int i = s.indexOf(from);
        return i < 0 ? s : s.substring(0, i) + to + s.substring(i + from.length());
    }

    /**
     * 判空分支专用素图（用户 2026-10-19 定案）：只有封面地址真为空才用——
     * 素灰底＋剧名排字（CoverTextDrawable，口径与音乐侧一致）。只许各 load 变体在
     * 判空分支调用；有地址的 placeholder 与加载失败回调按定案保持透明，走
     * getTextDrawable，不许调这个。
     */
    private static Drawable getEmptyCoverDrawable(String text, boolean firstCharOnly, float cornerRadiusPx) {
        return new CoverTextDrawable(text, firstCharOnly, cornerRadiusPx);
    }

    private static Drawable getEmptyCoverDrawable(String text, boolean firstCharOnly, float[] cornerRadiiPx) {
        return new CoverTextDrawable(text, firstCharOnly, cornerRadiiPx);
    }

    /**
     * 清掉该 ImageView 的在途 Glide 请求：回收行重绑到无封面条目时，只
     * setImageDrawable 不清请求，上一行迟到的图/迟到失败回调会把刚设的
     * 素图盖掉。判空分支设任何静态图前都必须先清。
     */
    private static void clearRequest(ImageView view) {
        try {
            Glide.with(view).clear(view);
        } catch (Throwable ignored) {
        }
    }

    private static void showEmptyCover(ImageView view, String text, boolean firstCharOnly, float cornerRadiusPx) {
        clearRequest(view);
        view.setImageDrawable(getEmptyCoverDrawable(text, firstCharOnly, cornerRadiusPx));
    }

    private static void showEmptyCover(ImageView view, String text, boolean firstCharOnly, float[] cornerRadiiPx) {
        clearRequest(view);
        view.setImageDrawable(getEmptyCoverDrawable(text, firstCharOnly, cornerRadiiPx));
    }

    private static Drawable getTextDrawable(String text, boolean vod) {
        // 有地址专用透明占位：加载中（placeholder）与加载失败（onLoadFailed）都保持透明，
        // 不显示任何占位图，等图片加载完成直接显示。判空分支不走这里，走素图。
        android.graphics.drawable.ColorDrawable drawable = new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT);
        return drawable;
    }

    private static RequestListener<Drawable> getListener(String text, String url, ImageView view, boolean vod) {
        return new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                view.setImageDrawable(getTextDrawable(text, vod));
                return true;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                if (resource != null) CoverRatioCache.put(url, resource.getIntrinsicWidth(), resource.getIntrinsicHeight());
                return false;
            }
        };
    }
}
