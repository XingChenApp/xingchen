package com.fongmi.android.tv.api.hk.event.home;

import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

/**
 * 8.83 com.example.hikerview.event.home.ShowFileChooserEvent 的等价实现（EventBus 事件类）。
 * 官字段为 webViewHolder（ArticleWebViewHolder 身份）＋filePathCallback；咱家
 * HkWebViewHolder 与其行内 WebView 一对一（ensureWebView 时唯一创建），身份以
 * WebView 实例承载（Fragment 订阅侧与本页 holder 的 WebView 比对，同官身份合判据）。
 * fileChooserParams 随事件带过（官 web 版 OnShowFileChooserEvent 同名字段），
 * 供订阅侧需要时按 accept 类型收窄选择器；官 Fragment 消费支不用它（恒全类型）。
 */
public class ShowFileChooserEvent {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private WebChromeClient.FileChooserParams fileChooserParams;

    public ShowFileChooserEvent(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                WebChromeClient.FileChooserParams fileChooserParams) {
        this.webView = webView;
        this.filePathCallback = filePathCallback;
        this.fileChooserParams = fileChooserParams;
    }

    public WebView getWebView() {
        return webView;
    }

    public void setWebView(WebView webView) {
        this.webView = webView;
    }

    public ValueCallback<Uri[]> getFilePathCallback() {
        return filePathCallback;
    }

    public void setFilePathCallback(ValueCallback<Uri[]> filePathCallback) {
        this.filePathCallback = filePathCallback;
    }

    public WebChromeClient.FileChooserParams getFileChooserParams() {
        return fileChooserParams;
    }

    public void setFileChooserParams(WebChromeClient.FileChooserParams fileChooserParams) {
        this.fileChooserParams = fileChooserParams;
    }
}
