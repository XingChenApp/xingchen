package com.example.hikerview.ui.view.popup;

import android.content.Context;
import android.graphics.Color;
import android.widget.ImageView;

import com.example.hikerview.R;
import com.lxj.xpopup.core.PopupInfo;
import com.lxj.xpopup.impl.InputConfirmPopupView;
import com.lxj.xpopup.impl.MyInputConfirmPopupView;
import com.lxj.xpopup.interfaces.OnCancelListener;
import com.lxj.xpopup.interfaces.OnInputConfirmListener;

import java.util.List;

/**
 * 官 {@code com.example.hikerview.ui.view.popup.MyXpopup} 照官 smali 转写
 * （官源 smali_classes2/com/example/hikerview/ui/view/popup/MyXpopup.smali）：
 * 构造、Builder、三配置法（borderRadius/autoOpenSoftInput/autoFocusEditText/
 * isDestroyOnDismiss）、asInputConfirm 两重载、asInputMyConfirm 三重载。
 * 官默认布局 id 0x7f0d01e7 经官 public.xml 核为库布局 xpopup_confirm_input，
 * 咱家引 com.example.hikerview.R.layout.xpopup_confirm_input（官布局原名落咱家 res，上游 2.9.0 库内同面布局已改名 _xpopup_center_impl_confirm、无此名）。
 * 官 asImageViewer 四重载（官行 86/108/132/155）随图库接线批补落：
 * 依赖的 MyImageViewerPopupView/XPopupImageLoader 已由图片团落树、
 * 带 View 加载器 PopImageLoader 同批落树（账 hk-img-viewer-wiring-20261114）。
 */
public class MyXpopup {

    private Context context;
    private final PopupInfo popupInfo = new PopupInfo();

    public MyXpopup() {
    }

    public MyXpopup Builder(Context context) {
        this.context = context;
        return this;
    }

    public MyXpopup borderRadius(float f) {
        this.popupInfo.borderRadius = f;
        return this;
    }

    public MyXpopup autoOpenSoftInput(Boolean bool) {
        this.popupInfo.autoOpenSoftInput = bool;
        return this;
    }

    public MyXpopup autoFocusEditText(boolean z) {
        this.popupInfo.autoFocusEditText = z;
        return this;
    }

    public MyXpopup isDestroyOnDismiss(boolean z) {
        this.popupInfo.isDestroyOnDismiss = z;
        return this;
    }

    public MyImageViewerPopupView asImageViewer(ImageView srcView, Object url, XPopupImageLoader loader) {
        MyImageViewerPopupView popupView = new MyImageViewerPopupView(this.context)
                .setSingleSrcView(srcView, url)
                .setXPopupImageLoader(loader);
        popupView.popupInfo = this.popupInfo;
        return popupView;
    }

    public MyImageViewerPopupView asImageViewer(ImageView srcView, Object url, boolean isInfinite,
                                                int placeholderColor, int placeholderStrokeColor,
                                                int placeholderRadius, boolean isShowSaveButton,
                                                int bgColor, XPopupImageLoader loader) {
        MyImageViewerPopupView popupView = new MyImageViewerPopupView(this.context)
                .setSingleSrcView(srcView, url)
                .isInfinite(isInfinite)
                .setPlaceholderColor(placeholderColor)
                .setPlaceholderStrokeColor(placeholderStrokeColor)
                .setPlaceholderRadius(placeholderRadius)
                .isShowSaveButton(isShowSaveButton)
                .setBgColor(bgColor)
                .setXPopupImageLoader(loader);
        popupView.popupInfo = this.popupInfo;
        return popupView;
    }

    public MyImageViewerPopupView asImageViewer(ImageView srcView, int position, List<Object> urls,
                                                OnSrcViewUpdateListener listener, XPopupImageLoader loader) {
        return asImageViewer(srcView, position, urls, false, true, -1, -1, -1, true,
                Color.rgb(32, 36, 46), listener, loader);
    }

    public MyImageViewerPopupView asImageViewer(ImageView srcView, int position, List<Object> urls,
                                                boolean isInfinite, boolean isShowPlaceholder,
                                                int placeholderColor, int placeholderStrokeColor,
                                                int placeholderRadius, boolean isShowSaveButton,
                                                int bgColor, OnSrcViewUpdateListener listener,
                                                XPopupImageLoader loader) {
        MyImageViewerPopupView popupView = new MyImageViewerPopupView(this.context)
                .setSrcView(srcView, position)
                .setImageUrls(urls)
                .isInfinite(isInfinite)
                .isShowPlaceholder(isShowPlaceholder)
                .setPlaceholderColor(placeholderColor)
                .setPlaceholderStrokeColor(placeholderStrokeColor)
                .setPlaceholderRadius(placeholderRadius)
                .isShowSaveButton(isShowSaveButton)
                .setBgColor(bgColor)
                .setSrcViewUpdateListener(listener)
                .setXPopupImageLoader(loader);
        popupView.popupInfo = this.popupInfo;
        return popupView;
    }

    public InputConfirmPopupView asInputConfirm(CharSequence charSequence, CharSequence charSequence2,
                                                CharSequence charSequence3, CharSequence charSequence4,
                                                OnInputConfirmListener onInputConfirmListener,
                                                OnCancelListener onCancelListener) {
        return asInputConfirm(charSequence, charSequence2, charSequence3, charSequence4,
                onInputConfirmListener, onCancelListener, R.layout.xpopup_confirm_input);
    }

    public InputConfirmPopupView asInputConfirm(CharSequence charSequence, CharSequence charSequence2,
                                                CharSequence charSequence3, CharSequence charSequence4,
                                                OnInputConfirmListener onInputConfirmListener,
                                                OnCancelListener onCancelListener, int i) {
        InputConfirmPopupView inputConfirmPopupView = new InputConfirmPopupView(this.context, i);
        inputConfirmPopupView.setTitleContent(charSequence, charSequence2, charSequence4);
        inputConfirmPopupView.inputContent = charSequence3;
        inputConfirmPopupView.setListener(onInputConfirmListener, onCancelListener);
        inputConfirmPopupView.popupInfo = this.popupInfo;
        return inputConfirmPopupView;
    }

    public MyInputConfirmPopupView asInputMyConfirm(CharSequence charSequence, CharSequence charSequence2,
                                                    CharSequence charSequence3, CharSequence charSequence4,
                                                    OnInputConfirmListener onInputConfirmListener,
                                                    OnCancelListener onCancelListener) {
        return asInputMyConfirm(charSequence, charSequence2, charSequence3, charSequence4,
                onInputConfirmListener, onCancelListener, R.layout.xpopup_confirm_input);
    }

    public MyInputConfirmPopupView asInputMyConfirm(CharSequence charSequence, CharSequence charSequence2,
                                                    CharSequence charSequence3, CharSequence charSequence4,
                                                    OnInputConfirmListener onInputConfirmListener,
                                                    OnCancelListener onCancelListener, int i) {
        return asInputMyConfirm(charSequence, charSequence2, charSequence3, charSequence4,
                onInputConfirmListener, onCancelListener, i, null);
    }

    public MyInputConfirmPopupView asInputMyConfirm(CharSequence charSequence, CharSequence charSequence2,
                                                    CharSequence charSequence3, CharSequence charSequence4,
                                                    OnInputConfirmListener onInputConfirmListener,
                                                    OnCancelListener onCancelListener, int i, String str) {
        MyInputConfirmPopupView myInputConfirmPopupView = new MyInputConfirmPopupView(this.context, i);
        myInputConfirmPopupView.setTitleContent(charSequence, charSequence2, charSequence4);
        myInputConfirmPopupView.inputContent = charSequence3;
        myInputConfirmPopupView.setListener(onInputConfirmListener, onCancelListener);
        myInputConfirmPopupView.setCancelText(str);
        myInputConfirmPopupView.popupInfo = this.popupInfo;
        return myInputConfirmPopupView;
    }
}
