package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PyExtConfig {
    private static final String PREFS = "py_ext_config";

    public static void save(Context context, String key, String json) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (json == null || json.isEmpty()) {
                sp.edit().remove(key).apply();
            } else {
                sp.edit().putString(key, json).apply();
            }
        } catch (Exception e) { e.printStackTrace(); }
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
