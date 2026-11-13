package com.example.hikerview.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.transition.Transition;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.example.hikerview.R;
import com.example.hikerview.ui.browser.model.UrlDetector;
import com.example.hikerview.ui.view.popup.MyImageViewerPopupView;
import com.example.hikerview.ui.view.popup.XPopupImageLoader;
import com.example.hikerview.utils.FileUtil;
import com.example.hikerview.utils.GlideUtil;
import com.example.hikerview.utils.ImgUtil;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.lxj.xpopup.photoview.PhotoView;
import com.lxj.xpopup.util.ImageDownloadTarget;
import com.lxj.xpopup.util.XPopupUtils;

import java.io.File;

import timber.log.Timber;

/**
 * 照官 8.83 本体转写（官 smali_classes2/com/example/hikerview/ui/view/
 * PopImageLoaderNoView.smali 及 $1–$4 内部类）：无宿主 View 依赖的图片
 * 加载器，查看器 {@link MyImageViewerPopupView} 的默认加载实现，
 * ImgUtil 存相册族在授权回调里构造它。原图走
 * {@link SubsamplingScaleImageView}（subscaleview 库，本批按官引
 * com.davemorrissey.labs:subsampling-scale-image-view-androidx:3.10.0）。
 * 等价映射一处：官 JSEngine.getFilePath 按本树既有先例接
 * {@link HkJsRuntime#getFilePath(String)}（树内各 hk 件同口径）。
 */
public class PopImageLoaderNoView extends BaseImageLoader implements XPopupImageLoader {

    private String baseUrl;

    public PopImageLoaderNoView(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public View loadImage(int position, Object url, MyImageViewerPopupView popupView, PhotoView snapshotView, ProgressBar progressBar) {
        PhotoView photoView = buildPhotoView(popupView, snapshotView, position);
        photoView.setTag(R.id.tv_save, url);
        boolean isOriginal = false;
        Object model = url;
        Object tag;
        if (url instanceof String) {
            String urlStr = (String) url;
            isOriginal = urlStr.contains("#originalSize#");
            tag = urlStr.replace("#originalSize#", "");
            model = GlideUtil.getGlideUrl(baseUrl, UrlDetector.clearTag(urlStr));
            if (isOriginal) {
                View view = loadOriginalImage(position, model, popupView, snapshotView, true);
                if (tagListener != null) {
                    tagListener.update(snapshotView, tag, "");
                }
                return view;
            }
        } else {
            tag = url;
        }
        if (isOriginal) {
            Glide.with(photoView).load(model).apply(new RequestOptions()
                    .placeholder(photoView.getContext().getResources().getDrawable(R.drawable.ic_loading_bg))
                    .override(Integer.MIN_VALUE, Integer.MIN_VALUE)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)).into(photoView);
            if (tagListener != null) {
                tagListener.update(photoView, tag, "");
            }
        } else {
            RequestOptions options = new RequestOptions()
                    .skipMemoryCache(true)
                    .placeholder(photoView.getContext().getResources().getDrawable(R.drawable.ic_loading_bg))
                    .diskCacheStrategy(DiskCacheStrategy.ALL);
            GlideUtil.loadFullPicDrawable(photoView.getContext(), photoView, model, options,
                    s -> {
                        if (tagListener != null) {
                            tagListener.update(photoView, tag, s);
                        }
                    });
        }
        return photoView;
    }

    @Override
    public void loadImage(int position, Object url, MyImageViewerPopupView popupView, ImageView imageView) {
        boolean isOriginal = false;
        Object model = url;
        Object tag;
        if (url instanceof String) {
            String urlStr = (String) url;
            isOriginal = urlStr.contains("#originalSize#");
            tag = urlStr.replace("#originalSize#", "");
            model = GlideUtil.getGlideUrl(baseUrl, UrlDetector.clearTag(urlStr));
            if (isOriginal) {
                View view = loadOriginalImage(position, model, popupView, imageView, false);
                ViewGroup parent = (ViewGroup) imageView.getParent();
                int index = parent.indexOfChild(imageView);
                parent.removeView(imageView);
                parent.addView(view, index, imageView.getLayoutParams());
                if (tagListener != null) {
                    tagListener.update(imageView, tag, "");
                }
                return;
            }
        } else {
            tag = url;
        }
        RequestOptions options = new RequestOptions()
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.ALL);
        if (isOriginal) {
            Glide.with(imageView).load(model).apply(options.override(Integer.MIN_VALUE, Integer.MIN_VALUE)).into(imageView);
            if (tagListener != null) {
                tagListener.update(imageView, tag, "");
            }
        } else {
            GlideUtil.loadFullPicDrawable(imageView.getContext(), imageView, model, options,
                    s -> {
                        if (tagListener != null) {
                            tagListener.update(imageView, tag, s);
                        }
                    });
        }
    }

    private View loadOriginalImage(int position, Object url, MyImageViewerPopupView popupView, ImageView imageView, boolean useSnapshot) {
        SubsamplingScaleImageView bigImageView = buildBigImageView(popupView, position);
        if (imageView != null && imageView.getDrawable() != null
                && (!useSnapshot || (Integer) imageView.getTag() == position)) {
            bigImageView.setImage(ImageSource.bitmap(XPopupUtils.view2Bitmap(imageView)));
        } else {
            Drawable drawable = ContextCompat.getDrawable(bigImageView.getContext(), R.drawable.ic_loading_bg);
            if (drawable != null) {
                bigImageView.setImage(ImageSource.bitmap(ImgUtil.drawableToBitmap(drawable)));
            }
        }
        Glide.with(bigImageView).downloadOnly().load(url).into(new ImageDownloadTarget() {
            @Override
            public void onLoadFailed(Drawable errorDrawable) {
                super.onLoadFailed(errorDrawable);
            }

            @Override
            public void onResourceReady(File file, Transition<? super File> transition) {
                super.onResourceReady(file, transition);
                bigImageView.setImage(ImageSource.uri(Uri.fromFile(file)));
            }
        });
        return bigImageView;
    }

    @Override
    public File getImageFile(Context context, Object url) {
        try {
            if (url instanceof String) {
                url = GlideUtil.getGlideUrl(baseUrl, UrlDetector.clearTag((String) url));
            }
            if (url instanceof Bitmap) {
                String fileName = "_fileSelect_" + System.currentTimeMillis() + ".jpg";
                String path = HkJsRuntime.getFilePath("hiker://files/cache/" + fileName);
                File file = new File(path);
                FileUtil.bitmapToFile((Bitmap) url, file);
                return file;
            }
            return Glide.with(context).downloadOnly().load(url)
                    .submit(Integer.MIN_VALUE, Integer.MIN_VALUE).get();
        } catch (Exception e) {
            Timber.e(e);
            return null;
        }
    }

    @Override
    public void loadSnapshot(Object url, PhotoView photoView, ImageView imageView) {
        if (url instanceof String) {
            url = GlideUtil.getGlideUrl(baseUrl, UrlDetector.clearTag((String) url));
        }
        if (imageView != null && imageView.getDrawable() != null) {
            try {
                photoView.setImageDrawable(imageView.getDrawable().getConstantState().newDrawable());
            } catch (Exception e) {
            }
            return;
        }
        GlideUtil.loadFullPicDrawable(photoView.getContext(), photoView, url, new RequestOptions());
    }

    private PhotoView buildPhotoView(MyImageViewerPopupView popupView, PhotoView snapshotView, int position) {
        PhotoView photoView = new PhotoView(popupView.getContext());
        photoView.setOnMatrixChangeListener(rect -> {
            if (snapshotView != null) {
                Matrix matrix = new Matrix();
                photoView.getSuppMatrix(matrix);
                snapshotView.setSuppMatrix(matrix);
            }
        });
        photoView.setOnClickListener(v -> popupView.dismiss());
        if (popupView.longPressListener != null) {
            photoView.setOnLongClickListener(v -> {
                popupView.longPressListener.onLongPressed(popupView, position);
                return false;
            });
        }
        return photoView;
    }

    private SubsamplingScaleImageView buildBigImageView(MyImageViewerPopupView popupView, int position) {
        SubsamplingScaleImageView imageView = new SubsamplingScaleImageView(popupView.getContext());
        imageView.setMinimumDpi(1);
        imageView.setMaximumDpi(320);
        imageView.setDoubleTapZoomDuration(250);
        imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER);
        imageView.setOnStateChangedListener(new SubsamplingScaleImageView.DefaultOnStateChangedListener() {
            @Override
            public void onCenterChanged(PointF newCenter, int origin) {
                super.onCenterChanged(newCenter, origin);
            }
        });
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupView.dismiss();
            }
        });
        if (popupView.longPressListener != null) {
            imageView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    popupView.longPressListener.onLongPressed(popupView, position);
                    return false;
                }
            });
        }
        return imageView;
    }
}
