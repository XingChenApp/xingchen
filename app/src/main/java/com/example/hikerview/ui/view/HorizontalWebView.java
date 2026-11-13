package com.example.hikerview.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;

/**
 * 照官 8.83 本体按需转写（官源 smali_classes2/com/example/hikerview/ui/view/HorizontalWebView.smali）。
 * 本批只落 HeavyTaskUtil.getTabHistory 与 MultiWindowManager.getUsedWebViewList 用到的成员：
 * 三构造（官构造本体含设置初始化，未落、记名）、used/parentWebView 两字段与
 * isUsed/setUsed/getParentWebView/setParentWebView（逐字照官）；getUrl 用 WebView 官面自带。
 * 官类其余成员与 IVideoWebView 接口未落、记名在案。
 */
public class HorizontalWebView extends WebView {

    private HorizontalWebView parentWebView;
    private boolean used;

    public HorizontalWebView(Context context) {
        super(context);
    }

    public HorizontalWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HorizontalWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public boolean isUsed() {
        return this.used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public HorizontalWebView getParentWebView() {
        return this.parentWebView;
    }

    public void setParentWebView(HorizontalWebView parentWebView) {
        this.parentWebView = parentWebView;
    }
}
