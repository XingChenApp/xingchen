package com.fongmi.android.tv.localbook;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地书注册表：SharedPreferences + Gson，读回走 TypeToken（R8 关卡）。
 * 书文件本体在 files/localbook/&lt;id&gt;/，注册表只记元数据＋章节索引。
 */
public class LocalBookStore {

    private static final String PREFS = "localbook_registry";
    private static final String KEY = "books";

    private static SharedPreferences prefs() {
        return App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static List<LocalBook> readAll() {
        try {
            String json = prefs().getString(KEY, "");
            if (TextUtils.isEmpty(json)) return new ArrayList<>();
            List<LocalBook> list = new Gson().fromJson(json,
                    new TypeToken<List<LocalBook>>() {
                    }.getType());
            return list == null ? new ArrayList<>() : list;
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    private static void writeAll(List<LocalBook> list) {
        try {
            prefs().edit().putString(KEY, new Gson().toJson(list)).apply();
        } catch (Throwable ignored) {
        }
    }

    public static void save(LocalBook book) {
        if (book == null || TextUtils.isEmpty(book.id)) return;
        book.addedAt = System.currentTimeMillis();
        List<LocalBook> list = readAll();
        boolean hit = false;
        for (int i = 0; i < list.size(); i++) {
            if (book.id.equals(list.get(i).id)) {
                list.set(i, book);
                hit = true;
                break;
            }
        }
        if (!hit) list.add(book);
        writeAll(list);
    }

    public static LocalBook get(String id) {
        if (TextUtils.isEmpty(id)) return null;
        for (LocalBook b : readAll()) {
            if (id.equals(b.id)) return b;
        }
        return null;
    }

    public static List<LocalBook> list() {
        List<LocalBook> list = readAll();
        list.sort((a, b) -> Long.compare(b.addedAt, a.addedAt));
        return list;
    }

    /** 删除注册＋删文件目录。 */
    public static void remove(String id) {
        if (TextUtils.isEmpty(id)) return;
        List<LocalBook> list = readAll();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (id.equals(list.get(i).id)) list.remove(i);
        }
        writeAll(list);
        try {
            deleteDir(new File(booksDir(), id));
        } catch (Throwable ignored) {
        }
        try {
            // 章节图缓存一并删（低-03）：目录口径与 LocalBookReader.chapterCache 实际
            // 用的一致——cacheDir()/<bookId>（其下再按章分目录），只删书名目录会留孤儿。
            deleteDir(new File(cacheDir(), id));
        } catch (Throwable ignored) {
        }
    }

    public static File booksDir() {
        File d = new File(App.get().getFilesDir(), "localbook");
        d.mkdirs();
        return d;
    }

    /** 图片懒解包缓存（按章提取，用前清空）。 */
    public static File cacheDir() {
        File d = new File(App.get().getFilesDir(), "localbook-cache");
        d.mkdirs();
        return d;
    }

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
