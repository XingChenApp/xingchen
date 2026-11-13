package com.example.hikerview.ui.view.popup;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.lxj.xpopup.photoview.PhotoView;

import java.io.File;

/**
 * 照官 8.83 本体转写（官 smali_classes2/com/example/hikerview/ui/view/
 * popup/XPopupImageLoader.smali，全文 4 法）：海阔自家 popup 版图片加载
 * 接口，签名嵌 {@link MyImageViewerPopupView}，不是 lxj 版同名接口
 * （lxj 版在 interfaces 包、3 法且签名用 lxj 自家查看器类，对不上不冒充，
 * 见 hk-14-fill-parts/c2.md 第 6 条核查）。
 */
public interface XPopupImageLoader {

    File getImageFile(Context context, Object url);

    View loadImage(int position, Object url, MyImageViewerPopupView popupView, PhotoView snapshotView, ProgressBar progressBar);

    void loadImage(int position, Object url, MyImageViewerPopupView popupView, ImageView imageView);

    void loadSnapshot(Object url, PhotoView snapshotView, ImageView imageView);
}
