package com.fongmi.android.tv.localbook;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.ToastUtil;
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

    // ---- 失败不再静默（349 阅读组）：与 ReadingStore 同结构 ----
    /** 上次成功读到的书单：解析失败时回这份，不许拿空表冒充真书单再写回去冲掉原数据。 */
    private static List<LocalBook> lastGood;
    /** 解析失败且从未成功读到过：盘上原文可能还在，写回必须拒绝。 */
    private static boolean readDegraded;
    private static volatile String lastReadError;
    private static volatile String lastWriteError;
    private static long lastFailToastAt;

    public static String lastReadError() {
        return lastReadError;
    }

    public static String lastWriteError() {
        return lastWriteError;
    }

    private static void noteFailure(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastFailToastAt < 60_000L) return;
        lastFailToastAt = now;
        try {
            ToastUtil.show(App.get(), msg, android.widget.Toast.LENGTH_LONG);
        } catch (Throwable ignored) {
        }
    }

    private static List<LocalBook> readAll() {
        try {
            String json = prefs().getString(KEY, "");
            if (TextUtils.isEmpty(json)) {
                lastGood = new ArrayList<>();
                readDegraded = false;
                lastReadError = null;
                return new ArrayList<>();
            }
            List<LocalBook> list = new Gson().fromJson(json,
                    new TypeToken<List<LocalBook>>() {
                    }.getType());
            list = list == null ? new ArrayList<>() : list;
            lastGood = new ArrayList<>(list);
            readDegraded = false;
            lastReadError = null;
            return list;
        } catch (Throwable t) {
            lastReadError = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            readDegraded = lastGood == null;
            noteFailure("本地书单读取失败，原来的书还在，已先按上次名单显示");
            return lastGood == null ? new ArrayList<>() : new ArrayList<>(lastGood);
        }
    }

    /** @return 是否真落盘；失败时旧值原样留在盘上，并已提示用户。 */
    private static boolean writeAll(List<LocalBook> list) {
        if (readDegraded) {
            lastWriteError = "原书单解析失败，拒绝用空表覆盖";
            noteFailure("本地书保存失败：原数据读取异常，已保留原数据不覆盖");
            return false;
        }
        try {
            String json = new Gson().toJson(list);
            // apply() 异步落盘、失败无从知道；commit() 才验得到（349 补结构）
            if (!prefs().edit().putString(KEY, json).commit()) {
                lastWriteError = "写入返回失败";
                noteFailure("本地书保存失败，刚导入的书可能没存上");
                return false;
            }
            lastWriteError = null;
            lastGood = new ArrayList<>(list);
            return true;
        } catch (Throwable t) {
            lastWriteError = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            noteFailure("本地书保存失败，刚导入的书可能没存上");
            return false;
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
