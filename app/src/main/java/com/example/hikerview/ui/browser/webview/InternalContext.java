package com.example.hikerview.ui.browser.webview;

import android.content.Context;
import android.content.MutableContextWrapper;

/**
 * 照官 8.83 本体逐字转写（官源 smali_classes2/com/example/hikerview/ui/browser/webview/InternalContext.smali，
 * 全类照落；访问级照官为 protected，本包 MultiWindowManager 同包调用）。
 */
public class InternalContext {

    private static InternalContext instance;
    private MutableContextWrapper mutableContext;

    protected static InternalContext getInstance() {
        if (instance == null) {
            instance = new InternalContext();
        }
        return instance;
    }

    protected MutableContextWrapper getMutableContext() {
        return this.mutableContext;
    }

    protected void setBaseContext(Context context) {
        if (this.mutableContext == null) {
            this.mutableContext = new MutableContextWrapper(context);
        }
        this.mutableContext.setBaseContext(context);
    }
}
