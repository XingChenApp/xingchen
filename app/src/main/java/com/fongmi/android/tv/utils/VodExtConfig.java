package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class VodExtConfig {
    private static final String PREFS = "vod_ext_config";

    public static boolean save(Context context, String key, String json) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            // commit 同步落盘并回成败：apply 的异步写失败无从感知，配置会静默没存上。
            boolean ok;
            if (json == null || json.isEmpty()) {
                ok = sp.edit().remove(key).commit();
            } else {
                ok = sp.edit().putString(key, json).commit();
            }
            if (!ok) android.util.Log.e("VodExtConfig", "save failed key=" + key);
            return ok;
        } catch (Exception e) {
            android.util.Log.e("VodExtConfig", "save failed key=" + key, e);
            return false;
        }
    }

    public static String load(Context context, String key) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String v = sp.getString(key, "");
            return v == null ? "" : v;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
