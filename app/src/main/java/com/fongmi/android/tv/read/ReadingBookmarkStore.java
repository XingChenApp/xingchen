package com.fongmi.android.tv.read;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.ToastUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

/**
 * 书签存储：小说/漫画通用。按书 key 归组，章内位置书签。
 * R8 关卡：读回必须 TypeToken<List<Mark>>。
 */
public class ReadingBookmarkStore {

    private static final String PREFS = "reading_bookmark";
    private static final String KEY_MARKS = "marks";

    /** 单条书签。 */
    public static class Mark {
        /** 书 key（ReadingStore.Record.key() 口径）。 */
        public String bookKey = "";
        public String bookTitle = "";
        public String kind = ""; // NOVEL / COMIC
        public String chapterName = "";
        public int chapterIndex = 0;
        /** 小说=万分比进度；漫画=页下标。 */
        public int position = 0;
        /** 摘要（≤60字，列表里一眼认出）。 */
        public String excerpt = "";
        public long createdAt = 0L;
    }

    private static SharedPreferences prefs() {
        return App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- 失败不再静默（349 阅读组）：与 ReadingStore 同结构 ----
    /** 上次成功读到的书签：解析失败时回这份，不许拿空表冒充真名单再写回去冲掉原数据。 */
    private static List<Mark> lastGood;
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

    private static List<Mark> readAll() {
        try {
            String json = prefs().getString(KEY_MARKS, "");
            if (TextUtils.isEmpty(json)) {
                lastGood = new ArrayList<>();
                readDegraded = false;
                lastReadError = null;
                return new ArrayList<>();
            }
            List<Mark> list = new Gson().fromJson(json, new TypeToken<List<Mark>>() {
            }.getType());
            list = list == null ? new ArrayList<>() : list;
            lastGood = new ArrayList<>(list);
            readDegraded = false;
            lastReadError = null;
            return list;
        } catch (Throwable t) {
            lastReadError = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            readDegraded = lastGood == null;
            noteFailure("书签读取失败，原来的书签还在，已先按上次名单显示");
            return lastGood == null ? new ArrayList<>() : new ArrayList<>(lastGood);
        }
    }

    /** @return 是否真落盘；失败时旧值原样留在盘上，并已提示用户。 */
    private static boolean writeAll(List<Mark> list) {
        if (readDegraded) {
            lastWriteError = "原书签解析失败，拒绝用空表覆盖";
            noteFailure("书签保存失败：原数据读取异常，已保留原数据不覆盖");
            return false;
        }
        try {
            String json = new Gson().toJson(list);
            // apply() 异步落盘、失败无从知道；commit() 才验得到（349 补结构）
            if (!prefs().edit().putString(KEY_MARKS, json).commit()) {
                lastWriteError = "写入返回失败";
                noteFailure("书签保存失败，刚加的书签可能没存上");
                return false;
            }
            lastWriteError = null;
            lastGood = new ArrayList<>(list);
            return true;
        } catch (Throwable t) {
            lastWriteError = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            noteFailure("书签保存失败，刚加的书签可能没存上");
            return false;
        }
    }

    /** 摘要裁剪（纯逻辑，脚手架可断言）。 */
    public static String excerptOf(String text) {
        if (text == null) return "";
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() > 60 ? t.substring(0, 60) + "…" : t;
    }

    public static void add(Mark m) {
        if (m == null || TextUtils.isEmpty(m.bookKey)) return;
        m.createdAt = System.currentTimeMillis();
        List<Mark> list = readAll();
        list.add(m);
        // 同一本书超过 200 条，删最老的
        List<Mark> mine = new ArrayList<>();
        for (Mark x : list) if (m.bookKey.equals(x.bookKey)) mine.add(x);
        if (mine.size() > 200) {
            mine.sort((a, b) -> Long.compare(a.createdAt, b.createdAt));
            for (int i = 0; i < mine.size() - 200; i++) list.remove(mine.get(i));
        }
        writeAll(list);
    }

    /** 某本书的书签（按时间倒序）。 */
    public static List<Mark> list(String bookKey) {
        List<Mark> out = new ArrayList<>();
        if (TextUtils.isEmpty(bookKey)) return out;
        for (Mark m : readAll()) {
            if (bookKey.equals(m.bookKey)) out.add(m);
        }
        out.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return out;
    }

    public static void remove(String bookKey, long createdAt) {
        List<Mark> list = readAll();
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            Mark m = list.get(i);
            if (bookKey.equals(m.bookKey) && m.createdAt == createdAt) {
                list.remove(i);
                changed = true;
            }
        }
        if (changed) writeAll(list);
    }

    /** 某本书被删时连带清书签。 */
    public static void removeBook(String bookKey) {
        if (TextUtils.isEmpty(bookKey)) return;
        List<Mark> list = readAll();
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (bookKey.equals(list.get(i).bookKey)) {
                list.remove(i);
                changed = true;
            }
        }
        if (changed) writeAll(list);
    }
}
