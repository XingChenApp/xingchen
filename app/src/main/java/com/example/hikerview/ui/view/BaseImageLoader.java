package com.example.hikerview.ui.view;

import android.widget.ImageView;

/**
 * 照官 8.83 本体转写（官 smali_classes2/com/example/hikerview/ui/view/
 * BaseImageLoader.smali 及 $TagListener）：图片加载器基类，只持一个
 * TagListener 供加载完成时回写标签（查看器据此切「保存/原图」钮文案）。
 */
public abstract class BaseImageLoader {

    protected TagListener tagListener;

    public void setTagListener(TagListener tagListener) {
        this.tagListener = tagListener;
    }

    public interface TagListener {
        void update(ImageView imageView, Object url, String tag);
    }
}
