package com.fongmi.android.tv.player.extractor;

import android.net.Uri;

// 转接层，非定稿原文：定稿取链源头是整套分发器（取链器清单与树里不一致，类名大小写也不同，
// 原文照搬编不过），点播批只调下面三个口径，故转接树里现成播放源头；导入行不动，方法签名与定稿一致。
public class Source {

    private static final Source INSTANCE = new Source();

    private Source() {
    }

    public static Source get() {
        return INSTANCE;
    }

    public static boolean isPush(String url) {
        return com.fongmi.android.tv.player.Source.isPush(url);
    }

    public static boolean isBuiltin(String url) {
        return com.fongmi.android.tv.player.Source.isBuiltin(url);
    }

    public boolean canPreload(Uri uri) {
        return com.fongmi.android.tv.player.Source.get().canPreload(uri);
    }
}
