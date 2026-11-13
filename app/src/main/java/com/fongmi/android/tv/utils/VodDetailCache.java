package com.fongmi.android.tv.utils;

import android.content.Context;
import android.os.Parcel;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Vod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class VodDetailCache {

    private static final String DIR = "vod_detail_cache";
    private static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_FILES = 300;

    private VodDetailCache() {
    }

    public static Vod get(Context context, String key, String id) {
        try {
            File file = fileFor(context, key, id);
            if (!file.exists()) return null;
            if (System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS) {
                file.delete();
                return null;
            }
            byte[] data = readAll(file);
            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(data, 0, data.length);
            parcel.setDataPosition(0);
            Vod vod = Vod.CREATOR.createFromParcel(parcel);
            parcel.recycle();
            if (isShell(vod)) {
                // 自愈：读到毒缓存（劣化网络下解析失败写进来的空壳）即作废并删文件，
                // 这次走现拉；写侧已拒收空壳，删后不会再被污染，用户零操作。
                file.delete();
                return null;
            }
            // 与 put 的守卫对称：旧版本写下的缓存里若还留着裸磁力/电驴原链，
            // 说明那是解析没产出的单链结果，读时同样作废并删文件，逼这次重新解析。
            for (Flag flag : vod.getFlags()) {
                for (Episode episode : flag.getEpisodes()) {
                    String url = episode.getUrl();
                    if (url == null) continue;
                    String lower = url.trim().toLowerCase(java.util.Locale.ROOT);
                    if (lower.startsWith("magnet:") || lower.startsWith("ed2k:") || lower.startsWith("thunder:")) {
                        file.delete();
                        return null;
                    }
                }
            }
            return vod;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 空壳判定：解析失败/未产出的 vod——无线路，或线路下无任何选集。
     * 这类结果不写缓存、读到即作废：一次劣化网络不能污染 7 天缓存。
     */
    private static boolean isShell(Vod vod) {
        if (vod == null || vod.getFlags() == null || vod.getFlags().isEmpty()) return true;
        for (Flag flag : vod.getFlags()) {
            if (flag != null && flag.getEpisodes() != null && !flag.getEpisodes().isEmpty()) return false;
        }
        return true;
    }

    public static void put(Context context, String key, String id, Vod vod) {
        // 空壳拒收：解析失败的 vod 不落盘，否则一次劣化网络就能污染 7 天缓存，
        // 之后每次打开都先吃空壳（越用越坏、重启也不好）。
        if (isShell(vod)) return;
        // 选集里还留着裸磁力/电驴原链 = 种子解析这次没产出（回退单集或未完成），
        // 不写缓存：否则 7 天内每次打开同一条都先吃这份单链结果，重试不了解析。
        for (Flag flag : vod.getFlags()) {
            for (Episode episode : flag.getEpisodes()) {
                String url = episode.getUrl();
                if (url == null) continue;
                String lower = url.trim().toLowerCase(java.util.Locale.ROOT);
                if (lower.startsWith("magnet:") || lower.startsWith("ed2k:") || lower.startsWith("thunder:")) return;
            }
        }
        String cacheKey = key;
        String cacheId = id;
        Task.execute(() -> {
            try {
                File dir = dir(context.getApplicationContext());
                File file = fileFor(dir, cacheKey, cacheId);
                Parcel parcel = Parcel.obtain();
                vod.writeToParcel(parcel, 0);
                byte[] data = parcel.marshall();
                parcel.recycle();
                writeAll(file, data);
                prune(dir);
            } catch (Throwable ignored) {
            }
        });
    }

    private static File dir(Context context) {
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File fileFor(Context context, String key, String id) {
        return fileFor(dir(context), key, id);
    }

    private static File fileFor(File dir, String key, String id) {
        return new File(dir, safe(key) + "__" + safe(id) + ".bin");
    }

    private static String safe(String s) {
        if (s == null) s = "";
        String r = s.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        return r.length() > 80 ? r.substring(0, 80) : r;
    }

    private static byte[] readAll(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int off = 0;
            int n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            return data;
        }
    }

    private static void writeAll(File file, byte[] data) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
            out.flush();
        }
    }

    private static void prune(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long now = System.currentTimeMillis();
        List<File> valid = new ArrayList<>();
        for (File f : files) {
            if (now - f.lastModified() > MAX_AGE_MS) f.delete();
            else valid.add(f);
        }
        if (valid.size() > MAX_FILES) {
            valid.sort(Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < valid.size() - MAX_FILES; i++) valid.get(i).delete();
        }
    }
}
