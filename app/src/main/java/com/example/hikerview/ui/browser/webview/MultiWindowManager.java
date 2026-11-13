package com.example.hikerview.ui.browser.webview;

import android.app.Activity;

import com.example.hikerview.ui.browser.view.BaseWebViewActivity;
import com.example.hikerview.ui.view.HorizontalWebView;

import java.util.ArrayList;
import java.util.List;

/**
 * 照官 8.83 本体按需转写（官源 smali_classes2/com/example/hikerview/ui/browser/webview/MultiWindowManager.smali）。
 * 本批落 HeavyTaskUtil.getTabHistory 链用到的成员，逐字照官：字段（sInstance/webViewList）、
 * 私有构造（webViewList 初值 ArrayList）、instance()、instance(Activity)（双重检查单例＋
 * BaseWebViewActivity 支）、getWebViewList、getUsedWebViewList（无子窗者入列的官判定）。
 * 官类其余窗口管理法（addWebView/selectWebView/releaseAllWebview 等）未落、记名在案。
 */
public class MultiWindowManager {

    private static final String TAG = "MultiWindowManager";

    private static volatile MultiWindowManager sInstance;

    private BaseWebViewActivity activity;
    private List<HorizontalWebView> webViewList;

    private MultiWindowManager() {
        this.webViewList = new ArrayList<>();
    }

    public static MultiWindowManager instance() {
        return instance(null);
    }

    public static MultiWindowManager instance(Activity activity) {
        if (sInstance == null) {
            synchronized (MultiWindowManager.class) {
                if (sInstance == null) {
                    sInstance = new MultiWindowManager();
                }
            }
        }
        if (activity instanceof BaseWebViewActivity) {
            InternalContext.getInstance().setBaseContext(activity);
            sInstance.activity = (BaseWebViewActivity) activity;
        }
        return sInstance;
    }

    public List<HorizontalWebView> getWebViewList() {
        return this.webViewList;
    }

    public List<HorizontalWebView> getUsedWebViewList() {
        ArrayList<HorizontalWebView> list = new ArrayList<>();
        for (HorizontalWebView webView : this.webViewList) {
            boolean isParent = false;
            for (HorizontalWebView other : this.webViewList) {
                if (webView == other.getParentWebView()) {
                    isParent = true;
                    break;
                }
            }
            if (!isParent) {
                list.add(webView);
            }
        }
        return list;
    }

    public void setActivity(BaseWebViewActivity activity) {
        this.activity = activity;
    }
}
