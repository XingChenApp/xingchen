package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.App;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 视频宽高缓存：后台预取的视频尺寸，供进入全屏前判断横竖屏，避免先横后竖闪一下。
 * <p>
 * 内存 LRU（100 条）+ 磁盘持久化（app cache dir 下 properties 文件）。
 * Key 为视频 URL 的 MD5，Value 为 "widthxheight"。
 */
public class VideoDimensionCache {

    private static final int MAX_MEMORY = 100;
    private static final String DISK_FILE = "video_dimensions.properties";

    private static final Map<String, int[]> MEMORY = Collections.synchronizedMap(
            new LinkedHashMap<String, int[]>(MAX_MEMORY, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest) {
                    return size() > MAX_MEMORY;
                }
            });

    private static volatile Properties diskProps;
    private static volatile boolean diskLoaded;

    /** 写盘收口到一条进程级守护线程：原来每次 put 都新建一条无句柄非守护
     * 线程，高频预取时线程成串起落；单线程执行器串行执行，saveDisk 本身
     * 是 synchronized，语义不变。 */
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "video-dim-save");
        thread.setDaemon(true);
        return thread;
    });

    private static String key(String url) {
        if (TextUtils.isEmpty(url)) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }

    private static synchronized Properties disk() {
        if (diskProps != null) return diskProps;
        diskProps = new Properties();
        try {
            File f = new File(App.get().getCacheDir(), DISK_FILE);
            if (f.exists()) {
                FileInputStream in = new FileInputStream(f);
                try {
                    diskProps.load(in);
                } finally {
                    in.close();
                }
            }
        } catch (Exception ignored) {
        }
        diskLoaded = true;
        return diskProps;
    }

    private static synchronized void saveDisk() {
        try {
            File f = new File(App.get().getCacheDir(), DISK_FILE);
            FileOutputStream out = new FileOutputStream(f);
            try {
                disk().store(out, null);
            } finally {
                out.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static int[] parse(String value) {
        if (TextUtils.isEmpty(value)) return null;
        try {
            String[] parts = value.split("x");
            if (parts.length != 2) return null;
            int w = Integer.parseInt(parts[0]);
            int h = Integer.parseInt(parts[1]);
            if (w <= 0 || h <= 0) return null;
            return new int[]{w, h};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 取缓存的宽高，返回 int[2]{width, height}，无缓存返回 null。
     */
    public static int[] get(String url) {
        String k = key(url);
        if (k == null) return null;
        int[] mem = MEMORY.get(k);
        if (mem != null) return mem;
        String diskVal = disk().getProperty(k);
        int[] parsed = parse(diskVal);
        if (parsed != null) MEMORY.put(k, parsed);
        return parsed;
    }

    /** 是否为竖屏视频（高 > 宽）。无缓存返回 null。 */
    public static Boolean isPortrait(String url) {
        int[] wh = get(url);
        if (wh == null) return null;
        return wh[1] > wh[0];
    }

    public static void put(String url, int width, int height) {
        if (TextUtils.isEmpty(url) || width <= 0 || height <= 0) return;
        String k = key(url);
        if (k == null) return;
        int[] wh = new int[]{width, height};
        MEMORY.put(k, wh);
        disk().setProperty(k, width + "x" + height);
        // 磁盘写入放后台，避免阻塞
        SAVE_EXECUTOR.execute(VideoDimensionCache::saveDisk);
    }

    public static void clear() {
        MEMORY.clear();
        try {
            disk().clear();
            saveDisk();
        } catch (Exception ignored) {
        }
    }
}
