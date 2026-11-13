package com.fongmi.android.tv.api.hk;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 海阔规则本地播放落盘视频自动清理。
 *
 * 规格来源：潇洒哥 2026-10-18 定——短剧这类小程序（签名接口→下载→CENC 解密→本地流播放）
 * 落到手机上的完整视频文件，按 6 小时／8 个文件／400MB 三条线自动清理，
 * 任一超线就从修改时间最旧的开始删。
 *
 * 设计要点（对照 本机审计账）：
 * 1. 只删视频扩展名（mp4/ts/m4s，不分大小写）；规则插件（Documents/libs/*.js/.json）、
 *    规则数据（rules/、plugins/）以及 m3u8 索引等一律不动——永不进入这些目录。
 * 2. 60 秒内修改过的文件跳过，防删正在写入的半成品。
 * 3. 删除失败只跳过、不抛异常、不影响主流程；清理全跑后台线程。
 * 4. 8.83 原版没有这套清理（对差缺口而非星辰新规格），照做即可。
 *
 * 本类只依赖 java.io / java.util，可独立编译离线验证。
 */
public final class HkMediaCacheCleaner {

    /** 超过 6 小时没动过的视频文件删掉。 */
    public static final long MAX_AGE_MS = 6L * 3600L * 1000L;
    /** 视频文件最多保留 8 个。 */
    public static final int MAX_COUNT = 8;
    /** 视频文件总大小上限 400MB。 */
    public static final long MAX_TOTAL_BYTES = 400L * 1024L * 1024L;
    /** 写入保护期：60 秒内修改过的文件视为正在写入，跳过。 */
    public static final long WRITE_GRACE_MS = 60L * 1000L;

    private static final String[] VIDEO_EXTS = {"mp4", "ts", "m4s"};
    /** 扫描时永不进入的目录名（规则插件、规则数据、m3u8 索引缓存等）。 */
    private static final String[] PROTECTED_DIRS = {"libs", "rules", "plugins"};

    /** 防并发重叠：同时只跑一轮清理。 */
    private static volatile boolean cleaning;

    private HkMediaCacheCleaner() {
    }

    /**
     * 后台跑一次清理。任何异常内部消化，绝不抛到调用方、
     * 绝不阻塞播放与页面线程。
     */
    public static void scheduleClean(final File root) {
        if (root == null) return;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    clean(root);
                } catch (Throwable ignored) {
                }
            }
        }, "hk-media-clean");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** 同步执行清理（纯逻辑，离线验证与调用方共用）。 */
    static void clean(File root) {
        if (root == null || cleaning) return;
        cleaning = true;
        try {
            List<File> videos = collectVideos(root);
            if (videos.isEmpty()) return;
            long now = System.currentTimeMillis();
            // 从旧到新排，超线时删的一定是最旧的。
            Collections.sort(videos, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return Long.compare(a.lastModified(), b.lastModified());
                }
            });
            // ① 年龄线：超过 6 小时没动过的删掉。
            List<File> rest = new ArrayList<>(videos.size());
            for (File f : videos) {
                if (now - f.lastModified() > MAX_AGE_MS) {
                    tryDelete(f);
                } else {
                    rest.add(f);
                }
            }
            // ② 数量线：超过 8 个，从最旧的开始删到只剩 8 个。
            int over = rest.size() - MAX_COUNT;
            for (int i = 0; i < over; i++) {
                tryDelete(rest.get(i));
            }
            if (over > 0) rest = rest.subList(over, rest.size());
            // ③ 大小线：总大小超过 400MB，从最旧的开始删到降到线内。
            long total = totalBytes(rest);
            for (int i = 0; i < rest.size() && total > MAX_TOTAL_BYTES; i++) {
                File f = rest.get(i);
                long len = f.length();
                if (tryDelete(f)) total -= len;
            }
        } finally {
            cleaning = false;
        }
    }

    /** 递归收集视频文件：跳过保护目录，跳过 60 秒内改过的（正在写）。 */
    static List<File> collectVideos(File dir) {
        List<File> out = new ArrayList<>();
        collectInto(dir, out);
        return out;
    }

    private static void collectInto(File dir, List<File> out) {
        File[] children;
        try {
            children = dir.listFiles();
        } catch (Throwable ignored) {
            return;
        }
        if (children == null) return;
        long now = System.currentTimeMillis();
        for (File f : children) {
            try {
                if (f.isDirectory()) {
                    if (isProtectedDir(f.getName())) continue;
                    collectInto(f, out);
                } else if (isVideoFile(f.getName()) && now - f.lastModified() >= WRITE_GRACE_MS) {
                    out.add(f);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isProtectedDir(String name) {
        String n = name.toLowerCase(Locale.US);
        for (String p : PROTECTED_DIRS) {
            if (n.equals(p)) return true;
        }
        return false;
    }

    private static boolean isVideoFile(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        for (String v : VIDEO_EXTS) {
            if (ext.equals(v)) return true;
        }
        return false;
    }

    private static long totalBytes(List<File> files) {
        long total = 0;
        for (File f : files) {
            try {
                total += f.length();
            } catch (Throwable ignored) {
            }
        }
        return total;
    }

    /** 删一个文件：成功回 true，失败不抛、只跳过。 */
    private static boolean tryDelete(File f) {
        try {
            return f.delete();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
