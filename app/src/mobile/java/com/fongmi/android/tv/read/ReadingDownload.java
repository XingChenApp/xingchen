package com.fongmi.android.tv.read;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;

import com.fongmi.android.tv.read.ReadingMatch;
import com.fongmi.android.tv.read.ReadingSession;
import com.fongmi.android.tv.ui.activity.ComicReaderActivity;
import com.fongmi.android.tv.ui.activity.NovelReaderActivity;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.GlobalProxy;
import com.github.catvod.net.SourceGate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 章节缓存：按书把后续章节落到私有目录，供离线看。
 * 小说存正文 txt；漫画存图片文件＋manifest。
 * 有界：全书缓存上限 150MB，超了删最久没碰的书。
 */
public final class ReadingDownload {

    private ReadingDownload() {
    }

    /**
     * 章节缓存下载专用 OkHttp：挂 SourceGate 选择器链（与 HkHttp/Exo 同门），
     * 调用线程的打点（书源/规则/站点归属）决定走节点还是直连。
     * 原先的裸 HttpURLConnection 走系统默认选择器，门管不到，一直裸奔。
     */
    private static volatile okhttp3.OkHttpClient dlClient;

    private static okhttp3.OkHttpClient dlClient() {
        okhttp3.OkHttpClient c = dlClient;
        if (c == null) {
            synchronized (ReadingDownload.class) {
                c = dlClient;
                if (c == null) {
                    c = new okhttp3.OkHttpClient.Builder()
                            .proxySelector(SourceGate.wrapSelector(
                                    GlobalProxy.wrapSelector(java.net.ProxySelector.getDefault())))
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .build();
                    dlClient = c;
                }
            }
        }
        return c;
    }

    /** 全量上限 150MB。 */
    private static final long QUOTA = 150L * 1024 * 1024;

    public interface Progress {
        void onDone(int ok, int total);
    }

    public static File root(Context ctx) {
        File f = new File(ctx.getFilesDir(), "reading_cache");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File bookDir(Context ctx, String bookKey) {
        File f = new File(root(ctx), ReadingMatch.safeFileKey(bookKey));
        if (!f.exists()) f.mkdirs();
        return f;
    }

    // ---------------- 小说 ----------------

    private static File novelFile(Context ctx, String bookKey, int chapterIndex) {
        return new File(bookDir(ctx, bookKey), "n_" + chapterIndex + ".txt");
    }

    public static boolean hasNovelChapter(Context ctx, String bookKey, int chapterIndex) {
        File f = novelFile(ctx, bookKey, chapterIndex);
        return f.exists() && f.length() > 0;
    }

    public static String loadNovelChapter(Context ctx, String bookKey, int chapterIndex) {
        try {
            File f = novelFile(ctx, bookKey, chapterIndex);
            if (!f.exists()) return "";
            byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
            return new String(b, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** 读完即写回：下次打开/重进同章直接命中文件，不再重跑源解析。 */
    public static void saveNovelChapter(Context ctx, String bookKey, int chapterIndex, String text) {
        try {
            File f = novelFile(ctx, bookKey, chapterIndex);
            File tmp = new File(f.getParent(), f.getName() + ".part");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(f)) tmp.delete();
        } catch (Throwable ignored) {
        }
    }

    /** 删单章缓存：越界写入的自愈用（正常流程不会调到它）。 */
    public static void deleteNovelChapter(Context ctx, String bookKey, int chapterIndex) {
        try {
            File f = novelFile(ctx, bookKey, chapterIndex);
            if (f.exists()) f.delete();
            File part = new File(f.getParent(), f.getName() + ".part");
            if (part.exists()) part.delete();
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 漫画 ----------------

    private static File comicDir(Context ctx, String bookKey, int chapterIndex) {
        File f = new File(bookDir(ctx, bookKey), "c_" + chapterIndex);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static boolean hasComicChapter(Context ctx, String bookKey, int chapterIndex) {
        File dir = comicDir(ctx, bookKey, chapterIndex);
        File mf = new File(dir, "manifest.txt");
        return mf.exists() && mf.length() > 0;
    }

    /** 缓存的章节图片（本地 file:// 地址，阅读器直接加载）。 */
    public static List<String> loadComicChapter(Context ctx, String bookKey, int chapterIndex) {
        List<String> out = new java.util.ArrayList<>();
        try {
            File dir = comicDir(ctx, bookKey, chapterIndex);
            File mf = new File(dir, "manifest.txt");
            if (!mf.exists()) return out;
            for (String line : new String(
                    java.nio.file.Files.readAllBytes(mf.toPath()),
                    StandardCharsets.UTF_8).split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                File img = new File(dir, line);
                if (img.exists()) out.add("file://" + img.getAbsolutePath());
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String downloadToFile(String url, String referer, File target) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return null;
        File tmp = new File(target.getParent(), target.getName() + ".part");
        try {
            okhttp3.Request.Builder rb = new okhttp3.Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)");
            if (!TextUtils.isEmpty(referer)) rb.header("Referer", referer);
            try (okhttp3.Response resp = dlClient().newCall(rb.build()).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    tmp.delete();
                    return null;
                }
                try (InputStream in = resp.body().byteStream();
                     FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[32768];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
            if (tmp.renameTo(target)) return target.getAbsolutePath();
            tmp.delete();
            return null;
        } catch (Throwable ignored) {
            tmp.delete();
            return null;
        }
    }

    // ---------------- 状态与清理 ----------------

    /** 已缓存章节数（小说 txt 数＋漫画 manifest 数）。 */
    public static int cachedChapters(Context ctx, String bookKey) {
        int n = 0;
        try {
            File dir = new File(root(ctx), ReadingMatch.safeFileKey(bookKey));
            if (!dir.exists()) return 0;
            File[] fs = dir.listFiles();
            if (fs == null) return 0;
            for (File f : fs) {
                if (f.isFile() && f.getName().startsWith("n_") && f.getName().endsWith(".txt")) n++;
                if (f.isDirectory() && new File(f, "manifest.txt").exists()) n++;
            }
        } catch (Throwable ignored) {
        }
        return n;
    }

    public static String statusText(Context ctx, String bookKey, int total) {
        int n = cachedChapters(ctx, bookKey);
        long bytes = totalSize(ctx);
        String size = bytes > 1048576
                ? String.format(Locale.US, "%.1fMB", bytes / 1048576f)
                : (bytes / 1024) + "KB";
        return "已缓存 " + n + (total > 0 ? "/" + total : "") + " 章 · 共用 " + size;
    }

    public static long totalSize(Context ctx) {
        return dirSize(root(ctx));
    }

    private static long dirSize(File dir) {
        long s = 0;
        try {
            File[] fs = dir.listFiles();
            if (fs == null) return 0;
            for (File f : fs) s += f.isDirectory() ? dirSize(f) : f.length();
        } catch (Throwable ignored) {
        }
        return s;
    }

    public static void clearBook(Context ctx, String bookKey) {
        deleteDir(new File(root(ctx), ReadingMatch.safeFileKey(bookKey)));
    }

    public static void clearAll(Context ctx) {
        deleteDir(root(ctx));
        root(ctx);
    }

    private static void deleteDir(File d) {
        try {
            if (!d.exists()) return;
            File[] fs = d.listFiles();
            if (fs != null) for (File f : fs) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
            d.delete();
        } catch (Throwable ignored) {
        }
    }

    /** 超 150MB 删最久没动过的书。 */
    public static void enforceQuota(Context ctx) {
        try {
            File r = root(ctx);
            if (dirSize(r) <= QUOTA) return;
            File[] books = r.listFiles();
            if (books == null) return;
            java.util.Arrays.sort(books, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (File b : books) {
                deleteDir(b);
                if (dirSize(r) <= QUOTA) break;
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 后台下载 ----------------

    /** 缓存小说后续章节（不含当前章）。 */
    public static void downloadNextNovel(Context ctx, ReadingSession session,
                                         String bookKey, int count, Handler ui, Progress cb) {
        Task.execute(() -> {
            int ok = 0, total = 0;
            // 节点打点：整批下载归属本书的来源（书源/规则/站点），内层各抓取点
            // 按自己的精确归属覆盖（push/pop 栈恢复，不吞外层）。
            String[] own = ReadingProxy.ownership(session);
            SourceGate.Mark prev = ReadingProxy.push(own);
            try {
                int from = session.chapterIndex + 1;
                int to = Math.min(session.chapters.size() - 1, from + count - 1);
                for (int i = from; i <= to; i++) {
                    total++;
                    if (hasNovelChapter(ctx, bookKey, i)) {
                        ok++;
                        continue;
                    }
                    ReadingSession.Chapter ch = session.chapters.get(i);
                    String text = NovelReaderActivity.resolveText(session, ch);
                    if (!TextUtils.isEmpty(text)) {
                        saveNovelChapter(ctx, bookKey, i, text);
                        ok++;
                    }
                }
                enforceQuota(ctx);
            } catch (Throwable ignored) {
            } finally {
                ReadingProxy.pop(own, prev);
            }
            final int fOk = ok, fTotal = total;
            ui.post(() -> cb.onDone(fOk, fTotal));
        });
    }

    /** 缓存漫画后续章节（不含当前章）。 */
    public static void downloadNextComic(Context ctx, ReadingSession session,
                                         String bookKey, int count, Handler ui, Progress cb) {
        Task.execute(() -> {
            int ok = 0, total = 0;
            // 节点打点：同上，整批下载归属本书的来源。
            String[] own = ReadingProxy.ownership(session);
            SourceGate.Mark prev = ReadingProxy.push(own);
            try {
                int from = session.chapterIndex + 1;
                int to = Math.min(session.chapters.size() - 1, from + count - 1);
                for (int i = from; i <= to; i++) {
                    total++;
                    if (hasComicChapter(ctx, bookKey, i)) {
                        ok++;
                        continue;
                    }
                    ReadingSession.Chapter ch = session.chapters.get(i);
                    List<String> imgs = ComicReaderActivity.resolveImages(session, ch, false);
                    if (imgs == null || imgs.isEmpty()) continue;
                    File dir = comicDir(ctx, bookKey, i);
                    StringBuilder mf = new StringBuilder();
                    int n = 0;
                    for (int p = 0; p < imgs.size(); p++) {
                        String name = "img_" + p + ".jpg";
                        if (downloadToFile(imgs.get(p), session.referer, new File(dir, name)) != null) {
                            mf.append(name).append("\n");
                            n++;
                        }
                    }
                    if (n == imgs.size()) {
                        try (FileOutputStream out = new FileOutputStream(new File(dir, "manifest.txt"))) {
                            out.write(mf.toString().getBytes(StandardCharsets.UTF_8));
                        }
                        ok++;
                    } else {
                        // 缺页不许报整章成功（低-19）：此前下到任意一张就写 manifest 计 ok，
                        // hasComicChapter 从此恒真、缺的页永远补不回来。缺页整章作废：
                        // 不写 manifest、不计 ok、删掉已下文件，下次重试整章重下。
                        File[] partials = dir.listFiles();
                        if (partials != null) {
                            for (File pf : partials) pf.delete();
                        }
                    }
                }
                enforceQuota(ctx);
            } catch (Throwable ignored) {
            } finally {
                ReadingProxy.pop(own, prev);
            }
            final int fOk = ok, fTotal = total;
            ui.post(() -> cb.onDone(fOk, fTotal));
        });
    }
}
