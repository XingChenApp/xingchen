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
 * 官类其余法（all/clear/contains/get/getAll/getFloat/getImage/putImage/remove 等）未落、记名在案。
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
