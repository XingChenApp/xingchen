package com.example.hikerview.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.webkit.CookieManager;
import android.widget.ImageView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.annimon.stream.function.Consumer;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.BaseRequestOptions;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.hikerview.R;
import com.example.hikerview.constants.ImageUrlMapEnum;
import com.example.hikerview.ui.setting.model.SettingConfig;

import java.io.File;
import java.util.Map;

import timber.log.Timber;

/**
 * 照官 8.83 本体移植（jadx 官包源）：图片地址加工与全图加载。
 * 与官的记名差异：①getImageUrl(String) 单参重载依赖官浏览器栈
 * （WebViewHelper/MultiWindowManager）未落，双参重载照官全量；
 * ②官 ScanDeviceUtil.HTTP 常量即 "http://"、SyntaxKey
 * .KEY_HEADER_SINGLE 即 "#"，按字面值落；③org.apache.commons
 * .lang3.StringUtils.equals 以等义空安全比较落（星辰未引该库）；
 * ④SettingConfig.rootDir 经最小落点的官公式现算（见该类注）。
 */
public class GlideUtil {

    private static final String TAG = "GlideUtil";

    private static boolean strEquals(CharSequence a, CharSequence b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public static String getImageUrl(String str, Map<String, String> map) {
        if (map != null) {
            if (map.containsKey("User-Agent")) {
                str = str + "@User-Agent=" + map.get("User-Agent");
            }
            if (map.containsKey("Referer")) {
                str = str + "@Referer=" + map.get("Referer");
            }
        }
        String cookie = CookieManager.getInstance().getCookie(str);
        return StringUtil.isNotEmpty(cookie) ? str + "@Cookie=" + cookie : str;
    }

    public static Object getGlideUrl(String str, String str2) {
        String str3;
        String str4;
        String str5;
        int idByUrl = ImageUrlMapEnum.getIdByUrl(str2);
        if (idByUrl > 0) {
            return Integer.valueOf(idByUrl);
        }
        if (StringUtil.isNotEmpty(str2) && str2.startsWith("#")) {
            try {
                return new ColorDrawable(Color.parseColor(str2));
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        if (StringUtil.isEmpty(str) || str2 == null || !str2.startsWith("http")) {
            if (StringUtil.isNotEmpty(str2) && str2.startsWith("hiker://files/")) {
                return "file://" + SettingConfig.getRootDirPath() + File.separator + str2.replace("hiker://files/", "");
            }
            try {
                return StringUtil.isNotEmpty(str2) ? str2.replace("#originalSize#", "") : str2;
            } catch (Exception e2) {
                Timber.e(e2);
                return str2;
            }
        }
        try {
            str2 = str2.replace("#originalSize#", "");
            if (str2.contains("gitee.com") && !str2.contains("@Referer=")) {
                str2 = str2 + "@Referer=https://gitee.com";
            }
        } catch (Exception e3) {
            Timber.e(e3);
        }
        LazyHeaders.Builder builder = new LazyHeaders.Builder();
        String dom = StringUtil.getDom(str);
        String dom2 = StringUtil.getDom(str2);
        if (!strEquals(dom, dom2) && ((dom == null || dom2 == null || !dom.contains(dom2)) && (dom == null || dom2 == null || !dom2.contains(dom)))) {
            str3 = "";
        } else if (str.startsWith("https")) {
            str3 = "https://" + dom + "/";
        } else {
            str3 = "http://" + dom + "/";
        }
        String glideUA = SettingConfig.getGlideUA();
        String[] split = str2.split("@js=");
        if (split.length > 1) {
            str2 = split[0];
            builder.addHeader("js", split[1]);
        }
        String[] split2 = str2.split("@headers=");
        if (split2.length > 1 && split2[1].startsWith("{") && split2[1].endsWith("}")) {
            try {
                str2 = split2[0];
                JSONObject parseObject = JSON.parseObject(split2[1]);
                for (String str6 : parseObject.keySet()) {
                    builder.addHeader(str6, parseObject.getString(str6));
                }
            } catch (Exception e4) {
                Timber.e(e4);
            }
        }
        String[] split3 = str2.split("@Cookie=");
        if (split3.length > 1) {
            str2 = split3[0];
            builder.addHeader("Cookie", split3[1]);
        }
        String[] split4 = str2.split("@Referer=");
        if (split4.length > 1) {
            if (split4[0].contains("@User-Agent=")) {
                str5 = split4[1];
                str4 = split4[0].split("@User-Agent=")[0];
                glideUA = split4[0].split("@User-Agent=")[1];
            } else if (split4[1].contains("@User-Agent=")) {
                str5 = split4[1].split("@User-Agent=")[0];
                str4 = split4[0];
                glideUA = split4[1].split("@User-Agent=")[1];
            } else {
                str5 = split4[1];
                str4 = split4[0];
            }
        } else {
            if (str2.contains("@Referer=")) {
                str2 = str2.replace("@Referer=", "");
                str3 = "";
            }
            if (str2.contains("@User-Agent=")) {
                glideUA = str2.split("@User-Agent=")[1];
                str2 = str2.split("@User-Agent=")[0];
            }
            String str7 = str3;
            str4 = str2;
            str5 = str7;
        }
        if (strEquals(str, str4) && !str4.contains("@Referer=")) {
            str5 = "";
        }
        String str8 = (!StringUtil.isNotEmpty(str5) || str5.startsWith("http")) ? str5 : "";
        if (!StringUtil.isEmpty(glideUA)) {
            if (StringUtil.isEmpty(str8)) {
                return new GlideUrl(str4, builder.addHeader("User-Agent", glideUA).build());
            }
            return new GlideUrl(str4, builder.addHeader("User-Agent", glideUA).addHeader("Referer", str8).build());
        }
        if (StringUtil.isEmpty(str8)) {
            return new GlideUrl(str4, builder.build());
        }
        return new GlideUrl(str4, builder.addHeader("Referer", str8).build());
    }

    public static int[] smartCompress(Context context, Drawable drawable) {
        // 注：d/d2 初值官 jadx 不可见，按 d=宽、d2=1.0 落（见账）
        double d = 0;
        double d2 = 1.0d;
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
            int byteCount = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap().getByteCount();
            Timber.d("Bitmap size: %s", Integer.valueOf(byteCount));
            float f = intrinsicWidth;
            d = f;
            float screenMin = ScreenUtil.getScreenMin(context) / f;
            if (intrinsicWidth <= ScreenUtil.getScreenMin(context)) {
                if (byteCount > 52428800) {
                    d = f;
                    d2 = 1.5d;
                    intrinsicWidth = (int) (d / d2);
                    intrinsicHeight = (int) (intrinsicHeight / d2);
                }
            } else if (byteCount >= 10485760) {
                if ((byteCount / screenMin) / screenMin < 5242880.0f) {
                    intrinsicWidth = ScreenUtil.getScreenMin(context);
                    intrinsicHeight = (int) ((drawable.getIntrinsicHeight() / drawable.getIntrinsicWidth()) * intrinsicWidth);
                } else {
                    if (byteCount > 104857600) {
                        d = f;
                        d2 = 4.5d;
                    } else if (byteCount > 52428800) {
                        d = f;
                        d2 = 3.3d;
                    } else if (byteCount > 20971520) {
                        intrinsicHeight = (int) (intrinsicHeight / 2.0f);
                        intrinsicWidth = (int) (f / 2.0f);
                    }
                    intrinsicWidth = (int) (d / d2);
                    intrinsicHeight = (int) (intrinsicHeight / d2);
                }
            }
        }
        if (intrinsicWidth > ScreenUtil.getScreenMin(context)) {
            intrinsicWidth = ScreenUtil.getScreenMin(context);
            intrinsicHeight = (int) ((drawable.getIntrinsicHeight() / drawable.getIntrinsicWidth()) * intrinsicWidth);
        }
        return new int[]{intrinsicWidth, intrinsicHeight};
    }

    public static int[] smartCompress(Context context, android.graphics.Bitmap bitmap) {
        double d = 0;
        double d2 = 1.0d;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int byteCount = bitmap.getByteCount();
        Timber.d("Bitmap size: %s", Integer.valueOf(byteCount));
        float f = width;
        d = f;
        float screenMin = ScreenUtil.getScreenMin(context) / f;
        if (width <= ScreenUtil.getScreenMin(context)) {
            if (byteCount > 52428800) {
                d = f;
                d2 = 1.5d;
                width = (int) (d / d2);
                height = (int) (height / d2);
            }
        } else if (byteCount >= 10485760) {
            if ((byteCount / screenMin) / screenMin < 5242880.0f) {
                width = ScreenUtil.getScreenMin(context);
                height = (int) ((bitmap.getHeight() / bitmap.getWidth()) * width);
            } else {
                if (byteCount > 104857600) {
                    d = f;
                    d2 = 4.5d;
                } else if (byteCount > 52428800) {
                    d = f;
                    d2 = 3.3d;
                } else if (byteCount > 20971520) {
                    height = (int) (height / 2.0f);
                    width = (int) (f / 2.0f);
                }
                width = (int) (d / d2);
                height = (int) (height / d2);
            }
        }
        if (width > ScreenUtil.getScreenMin(context)) {
            width = ScreenUtil.getScreenMin(context);
            height = (int) ((bitmap.getHeight() / bitmap.getWidth()) * width);
        }
        return new int[]{width, height};
    }

    public static void loadFullPicDrawable(Context context, ImageView imageView, Object obj, RequestOptions requestOptions) {
        loadFullPicDrawable(context, imageView, obj, requestOptions, null);
    }

    public static void loadFullPicDrawable(Context context, ImageView imageView, Object obj, RequestOptions requestOptions, Consumer<String> consumer) {
        if (obj == null || "".equals(obj)) {
            imageView.setImageDrawable(null);
            return;
        }
        Long valueOf = Long.valueOf(System.currentTimeMillis());
        imageView.setTag(R.id.custom_tag, valueOf);
        Glide.with(context).asDrawable().apply((BaseRequestOptions<?>) requestOptions).load(obj).into(new FullPicTarget(context, imageView, valueOf, consumer, requestOptions));
    }

    private static class FullPicTarget extends MyCustomTarget {
        private final Context val$context;
        private final ImageView val$imageView;
        private final RequestOptions val$options;
        private final Consumer<String> val$tagListener;
        private final Object val$ticket;

        FullPicTarget(Context context, ImageView imageView, Object ticket, Consumer<String> consumer, RequestOptions requestOptions) {
            super(context, imageView);
            this.val$ticket = ticket;
            this.val$imageView = imageView;
            this.val$context = context;
            this.val$tagListener = consumer;
            this.val$options = requestOptions;
        }

        @Override
        public void onResourceReady(final Drawable drawable, Transition<? super Drawable> transition) {
            if (this.val$ticket != this.val$imageView.getTag(R.id.custom_tag)) {
                return;
            }
            int[] smartCompress = GlideUtil.smartCompress(this.val$context, drawable);
            final int i = smartCompress[0];
            final int i2 = smartCompress[1];
            if (i != drawable.getIntrinsicWidth()) {
                this.val$imageView.setTag(drawable.getIntrinsicWidth() + "×" + drawable.getIntrinsicHeight() + "/" + i + "×" + i2);
                Consumer<String> consumer = this.val$tagListener;
                if (consumer != null) {
                    consumer.accept((String) this.val$imageView.getTag());
                }
                final Context context = this.val$context;
                final Object obj = this.val$ticket;
                final ImageView imageView = this.val$imageView;
                final RequestOptions requestOptions = this.val$options;
                HeavyTaskUtil.executeNewTask(() -> {
                    final Drawable compressImage = ImgUtil.compressImage(context.getResources(), drawable, i, i2);
                    ThreadTool.INSTANCE.runOnUI(() -> {
                        if (obj != imageView.getTag(R.id.custom_tag)) {
                            return;
                        }
                        GlideUtil.loadFullPicDrawable(context, imageView, compressImage, requestOptions, i, i2);
                    });
                });
                return;
            }
            try {
                if (drawable instanceof GifDrawable) {
                    ((GifDrawable) drawable).setLoopCount(-1);
                    ((GifDrawable) drawable).start();
                }
            } catch (Exception e) {
                Timber.e(e);
            }
            this.val$imageView.setTag(drawable.getIntrinsicWidth() + "×" + drawable.getIntrinsicHeight());
            this.val$imageView.setPadding(0, 0, 0, 0);
            this.val$imageView.setImageDrawable(drawable);
            this.val$imageView.setAdjustViewBounds(true);
            Consumer<String> consumer2 = this.val$tagListener;
            if (consumer2 != null) {
                consumer2.accept((String) this.val$imageView.getTag());
            }
        }
    }

    public static void loadFullPicDrawable(Context context, final ImageView imageView, Object obj, RequestOptions requestOptions, int i, int i2) {
        if (obj == null || "".equals(obj)) {
            imageView.setImageDrawable(null);
            return;
        }
        final Long valueOf = Long.valueOf(System.currentTimeMillis());
        imageView.setTag(R.id.custom_tag, valueOf);
        Glide.with(context).asDrawable().override(i, i2).apply((BaseRequestOptions<?>) requestOptions.skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.ALL)).load(obj).into(new MyCustomTarget(context, imageView) {
            @Override
            public void onResourceReady(Drawable drawable, Transition<? super Drawable> transition) {
                if (valueOf != imageView.getTag(R.id.custom_tag)) {
                    return;
                }
                try {
                    if (drawable instanceof GifDrawable) {
                        ((GifDrawable) drawable).setLoopCount(-1);
                        ((GifDrawable) drawable).start();
                    }
                } catch (Exception e) {
                    Timber.e(e);
                }
                imageView.setPadding(0, 0, 0, 0);
                imageView.setImageDrawable(drawable);
                imageView.setAdjustViewBounds(true);
            }
        });
    }

    public static void loadPicDrawable(Context context, ImageView imageView, Object obj, RequestOptions requestOptions) {
        if (obj == null || "".equals(obj)) {
            imageView.setImageDrawable(null);
            return;
        }
        Long valueOf = Long.valueOf(System.currentTimeMillis());
        imageView.setTag(R.id.custom_tag, valueOf);
        Glide.with(context).asDrawable().apply((BaseRequestOptions<?>) requestOptions).load(obj).into(new PicTarget(context, imageView, valueOf, requestOptions));
    }

    private static class PicTarget extends MyCustomTarget {
        private final Context val$context;
        private final ImageView val$imageView;
        private final RequestOptions val$options;
        private final Object val$ticket;

        PicTarget(Context context, ImageView imageView, Object ticket, RequestOptions requestOptions) {
            super(context, imageView);
            this.val$ticket = ticket;
            this.val$imageView = imageView;
            this.val$context = context;
            this.val$options = requestOptions;
        }

        @Override
        public void onLoadStarted(Drawable drawable) {
            if (this.val$ticket == this.val$imageView.getTag(R.id.custom_tag) && drawable != null) {
                this.val$imageView.setImageDrawable(drawable);
            }
        }

        @Override
        public void onResourceReady(final Drawable drawable, Transition<? super Drawable> transition) {
            if (this.val$ticket != this.val$imageView.getTag(R.id.custom_tag)) {
                return;
            }
            int[] smartCompress = GlideUtil.smartCompress(this.val$context, drawable);
            final int i = smartCompress[0];
            final int i2 = smartCompress[1];
            if (i != drawable.getIntrinsicWidth()) {
                final Context context = this.val$context;
                final Object obj = this.val$ticket;
                final ImageView imageView = this.val$imageView;
                final RequestOptions requestOptions = this.val$options;
                HeavyTaskUtil.executeNewTask(() -> {
                    final Drawable compressImage = ImgUtil.compressImage(context.getResources(), drawable, i, i2);
                    ThreadTool.INSTANCE.runOnUI(() -> {
                        if (obj != imageView.getTag(R.id.custom_tag)) {
                            return;
                        }
                        GlideUtil.loadPicDrawable(context, imageView, compressImage, requestOptions, i, i2);
                    });
                });
                return;
            }
            try {
                if (drawable instanceof GifDrawable) {
                    ((GifDrawable) drawable).setLoopCount(-1);
                    ((GifDrawable) drawable).start();
                }
            } catch (Exception e) {
                Timber.e(e);
            }
            this.val$imageView.setImageDrawable(drawable);
        }
    }

    public static void loadPicDrawable(Context context, final ImageView imageView, Object obj, RequestOptions requestOptions, int i, int i2) {
        if (obj == null || "".equals(obj)) {
            imageView.setImageDrawable(null);
            return;
        }
        final Long valueOf = Long.valueOf(System.currentTimeMillis());
        imageView.setTag(R.id.custom_tag, valueOf);
        Glide.with(context).asDrawable().override(i, i2).apply((BaseRequestOptions<?>) requestOptions.skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.ALL)).load(obj).into(new MyCustomTarget(context, imageView) {
            @Override
            public void onLoadStarted(Drawable drawable) {
                if (valueOf == imageView.getTag(R.id.custom_tag) && drawable != null) {
                    imageView.setImageDrawable(drawable);
                }
            }

            @Override
            public void onResourceReady(Drawable drawable, Transition<? super Drawable> transition) {
                if (valueOf != imageView.getTag(R.id.custom_tag)) {
                    return;
                }
                try {
                    if (drawable instanceof GifDrawable) {
                        ((GifDrawable) drawable).setLoopCount(-1);
                        ((GifDrawable) drawable).start();
                    }
                } catch (Exception e) {
                    Timber.e(e);
                }
                imageView.setImageDrawable(drawable);
            }
        });
    }

    private static abstract class MyCustomTarget extends CustomTarget<Drawable> {
        private Context context;
        private ImageView imageView;

        public MyCustomTarget(Context context, ImageView imageView) {
            this.context = context;
            this.imageView = imageView;
        }

        @Override
        public void onLoadStarted(Drawable drawable) {
            if (drawable != null) {
                this.imageView.setPadding(0, 0, 0, DisplayUtil.dpToPx(this.context, 5));
                this.imageView.setImageDrawable(drawable);
                this.imageView.setAdjustViewBounds(true);
            }
        }

        @Override
        public void onLoadCleared(Drawable drawable) {
            try {
                if (drawable instanceof GifDrawable) {
                    ((GifDrawable) drawable).stop();
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }
}
