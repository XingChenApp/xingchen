package com.example.hikerview.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;

/**
 * 照官 8.83 本体按需转写（官源 smali_classes2/com/example/hikerview/utils/PreferenceMgr.smali）。
 * 本批落 HeavyTaskUtil 等解锁面实际用到的法：put 两重载、getBoolean/getInt/getString/getLong
 * 各两重载，逐字照官（含 getLong 的 ClassCastException 回退 int 并回写、put 的类型分发与
 * null 写空串、失败语义照官）；内类 SharedPreferencesCompat 全落（反射 apply、失败 commit 照官）。
 * 2026-11-19 PY源盒子对接批续补两法（账 hk-pybox-usable-20261119 件 5）：get 四参通用形
 * （官 smali:142 按 default 类型分发，本文件以既有 typed get 法承接同阶梯）、remove 三参
 * （官 smali:1012，edit().remove 后走 SharedPreferencesCompat.apply）——盒子标准基类
 * base/hiker.py 的缓存三法（setCache/getCache/delCache）按名直调这两法。
 * 官类其余法（all/clear/contains/get 三参形/getAll/getFloat/getImage/putImage 等）仍未落、记名在案。
 * 默认文件 FILE_NAME="share_data" 照官；SETTING_CONFIG="setting_config" 照官。
 */
public class PreferenceMgr {

    private static String FILE_NAME = "share_data";
    public static String SETTING_CONFIG = "setting_config";

    public static void put(Context context, String key, Object object) {
        put(context, FILE_NAME, key, object);
    }

    public static void put(Context context, String fileName, String key, Object object) {
        SharedPreferences sp = context.getSharedPreferences(fileName, 0);
        SharedPreferences.Editor editor = sp.edit();
        if (object instanceof String) {
            editor.putString(key, (String) object);
        } else if (object instanceof Integer) {
            editor.putInt(key, ((Integer) object).intValue());
        } else if (object instanceof Boolean) {
            editor.putBoolean(key, ((Boolean) object).booleanValue());
        } else if (object instanceof Float) {
            editor.putFloat(key, ((Float) object).floatValue());
        } else if (object instanceof Long) {
            editor.putLong(key, ((Long) object).longValue());
        } else if (object instanceof Collection) {
            editor.putStringSet(key, new HashSet<>((Collection) object));
        } else {
            editor.putString(key, object == null ? "" : object.toString());
        }
        SharedPreferencesCompat.apply(editor);
    }

    public static boolean getBoolean(Context context, String key, boolean defaultValue) {
        return getBoolean(context, FILE_NAME, key, defaultValue);
    }

    public static boolean getBoolean(Context context, String fileName, String key, boolean defaultValue) {
        SharedPreferences sp = context.getSharedPreferences(fileName, 0);
        return sp.getBoolean(key, defaultValue);
    }

    public static int getInt(Context context, String key, int defaultValue) {
        try {
            return getInt(context, FILE_NAME, key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static int getInt(Context context, String fileName, String key, int defaultValue) {
        try {
            SharedPreferences sp = context.getSharedPreferences(fileName, 0);
            return sp.getInt(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static String getString(Context context, String key, String defaultValue) {
        return getString(context, FILE_NAME, key, defaultValue);
    }

    public static String getString(Context context, String fileName, String key, String defaultValue) {
        SharedPreferences sp = context.getSharedPreferences(fileName, 0);
        return sp.getString(key, defaultValue);
    }

    public static long getLong(Context context, String key, long defaultValue) {
        try {
            return getLong(context, FILE_NAME, key, defaultValue);
        } catch (ClassCastException e) {
            long value = getInt(context, FILE_NAME, key, (int) defaultValue);
            put(context, FILE_NAME, key, Long.valueOf(value));
            return value;
        }
    }

    public static long getLong(Context context, String fileName, String key, long defaultValue) {
        try {
            SharedPreferences sp = context.getSharedPreferences(fileName, 0);
            return sp.getLong(key, defaultValue);
        } catch (ClassCastException e) {
            long value = getInt(context, fileName, key, (int) defaultValue);
            put(context, fileName, key, Long.valueOf(value));
            return value;
        }
    }

    /**
     * 官 get(Context, String, String, Object) 通用形：按 default 的类型分发到对应
     * typed 取法（String/Integer/Boolean/Float/Long/Set 阶梯，官 smali:142 实证），
     * 阶梯外回 default 本身。PY源盒子 base/hiker.py 的 getCache 经此法（default 恒 String）。
     */
    public static Object get(Context context, String fileName, String key, Object defaultValue) {
        if (defaultValue instanceof String) {
            return getString(context, fileName, key, (String) defaultValue);
        } else if (defaultValue instanceof Integer) {
            return getInt(context, fileName, key, (Integer) defaultValue);
        } else if (defaultValue instanceof Boolean) {
            return getBoolean(context, fileName, key, (Boolean) defaultValue);
        } else if (defaultValue instanceof Float) {
            SharedPreferences sp = context.getSharedPreferences(fileName, 0);
            return sp.getFloat(key, (Float) defaultValue);
        } else if (defaultValue instanceof Long) {
            return getLong(context, fileName, key, (Long) defaultValue);
        } else if (defaultValue instanceof java.util.Set) {
            SharedPreferences sp = context.getSharedPreferences(fileName, 0);
            @SuppressWarnings("unchecked")
            java.util.Set<String> def = (java.util.Set<String>) defaultValue;
            return sp.getStringSet(key, def);
        }
        return defaultValue;
    }

    /** 官 remove(Context, String, String)：摘单键后经 SharedPreferencesCompat 落盘。 */
    public static void remove(Context context, String fileName, String key) {
        SharedPreferences sp = context.getSharedPreferences(fileName, 0);
        SharedPreferences.Editor editor = sp.edit();
        editor.remove(key);
        SharedPreferencesCompat.apply(editor);
    }

    /** 官 PreferenceMgr$SharedPreferencesCompat 逐字转写。 */
    private static class SharedPreferencesCompat {

        private static final Method sApplyMethod = findApplyMethod();

        private static Method findApplyMethod() {
            try {
                return SharedPreferences.Editor.class.getMethod("apply", new Class[0]);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        public static void apply(SharedPreferences.Editor editor) {
            try {
                if (sApplyMethod != null) {
                    sApplyMethod.invoke(editor, new Object[0]);
                    return;
                }
            } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            }
            editor.commit();
        }
    }
}
