package com.xingchen.tv;

import android.app.Application;
import com.xingchen.tv.theme.ThemeManager;

public class XingChenApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.init(this);
    }
}
