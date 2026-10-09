package com.fongmi.android.tv.utils;

import android.content.Context;
import android.os.Parcel;

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
            if (vod.getFlags().isEmpty()) return null;
            return vod;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void put(Context context, String key, String id, Vod vod) {
        if (vod == null || vod.getFlags().isEmpty()) return;
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
