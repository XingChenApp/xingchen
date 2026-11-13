package com.example.hikerview.ui.view.popup;

/**
 * 照官 8.83 本体转写（官 smali_classes2/com/example/hikerview/ui/view/
 * popup/OnSrcViewUpdateListener.smali，全文 1 法）：查看器切页时回告
 * 源图控件更新的监听，供 {@link MyImageViewerPopupView} 的
 * PhotoViewAdapter.onPageSelected 分发。
 */
public interface OnSrcViewUpdateListener {

    void onSrcViewUpdate(MyImageViewerPopupView popupView, int position);
}
