package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;

public class XingChenDoh {

    public static final String KEY = "xingchen.doh_provider";
    public static final String[] NAMES = {"系统", "腾讯", "阿里", "360"};
    private static final String[] URLS = {"", "https://doh.pub/dns-query", "https://dns.alidns.com/dns-query", "https://doh.360.cn/dns-query"};

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("xingchen", Context.MODE_PRIVATE);
    }

    public static int getIndex(Context context) {
        int i = prefs(context).getInt(KEY, 0);
        return (i >= 0 && i < NAMES.length) ? i : 0;
    }

    public static String getName(Context context) {
        return NAMES[getIndex(context)];
    }

    public static Doh toDoh(Context context) {
        int i = getIndex(context);
        return new Doh().name(NAMES[i]).url(URLS[i]);
    }

    public static void apply(Context context) {
        try {
            OkHttp.dns().setDoh(toDoh(context));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void setIndex(Context context, int index) {
        prefs(context).edit().putInt(KEY, index).apply();
        apply(context);
    }
}
