package com.fongmi.android.tv.localbook;

import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * 阅读器懒取面：小说取正文、漫画取图片，都只读当前章，不整本进内存。
 * 图包图片按章解到各自的缓存子目录（只清本章），图片文件夹直接给 file://。
 */
public class LocalBookReader {

    /** 单章图片上限（防畸形包）。 */
    private static final int MAX_IMAGES_PER_CHAPTER = 3000;

    public static boolean isLocal(String url) {
        return LocalBook.isLocalUrl(url);
    }

    private static LocalBook bookOf(String url) {
        String[] p = LocalBook.parseUrl(url);
        if (p == null) return null;
        return LocalBookStore.get(p[0]);
    }

    private static LocalBook.ChapterRef chapterOf(LocalBook book, String url) {
        try {
            String[] p = LocalBook.parseUrl(url);
            int idx = Integer.parseInt(p[1]);
            if (idx < 0 || idx >= book.chapters.size()) return null;
            return book.chapters.get(idx);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 小说：取某章正文。 */
    public static String textOf(String url) {
        try {
            LocalBook book = bookOf(url);
            if (book == null) return "";
            LocalBook.ChapterRef ref = chapterOf(book, url);
            if (ref == null) return "";
            if ("MOBI".equals(book.format) || "AZW3".equals(book.format)) {
                // mobi/azw3：正文已抽成 UTF-8 的 book.txt，章节是 charMode 偏移
                File f = new File(book.dir, "book.txt");
                if (!f.exists()) return "";
                return LocalTxtIndex.readChapterCharMode(f, "UTF-8", ref);
            }
            if ("TXT".equals(book.format)) {
                File f = new File(book.dir, book.fileName);
                if (!f.exists()) return "";
                return ref.charMode
                        ? LocalTxtIndex.readChapterCharMode(f, book.charset, ref)
                        : LocalTxtIndex.readChapter(f, book.charset, ref);
            }
            if ("EPUB".equals(book.format)) {
                File f = new File(book.dir, book.fileName);
                if (!f.exists() || TextUtils.isEmpty(ref.entry)) return "";
                LocalEpub.Book eb = LocalEpub.parse(f);
                for (LocalEpub.SpineItem item : eb.spine) {
                    if (item.path.equals(ref.entry)) return LocalEpub.textOf(f, item);
                }
            }
            return "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** 漫画：取某章图片（file:// 绝对路径，阅读器直接加载）。 */
    public static List<String> imagesOf(String url) {
        List<String> out = new ArrayList<>();
        try {
            LocalBook book = bookOf(url);
            if (book == null) return out;
            LocalBook.ChapterRef ref = chapterOf(book, url);
            if (ref == null) return out;
            if ("DIR".equals(book.format)) {
                File files = new File(book.dir, "files");
                if (!files.exists()) return out;
                out.addAll(LocalBookImporter.dirImages(files, ref.entry));
            } else if ("ZIP".equals(book.format) || "CBZ".equals(book.format)) {
                File f = new File(book.dir, book.fileName);
                if (!f.exists()) return out;
                out.addAll(extractChapterImages(f, book, ref));
            } else if (LocalArchive2.isCbrCb7(book.format)) {
                File f = new File(book.dir, book.fileName);
                if (!f.exists()) return out;
                out.addAll(extractArchive2ChapterImages(f, book, ref));
            } else if ("PDF".equals(book.format)) {
                File f = new File(book.dir, book.fileName);
                if (!f.exists()) return out;
                out.addAll(renderPdfChapter(f, book, ref));
            } else if ("EPUB".equals(book.format)) {
                File f = new File(book.dir, book.fileName);
                if (!f.exists() || TextUtils.isEmpty(ref.entry)) return out;
                out.addAll(extractEpubChapterImages(f, book, ref));
            }
            if (out.size() > MAX_IMAGES_PER_CHAPTER) {
                return new ArrayList<>(out.subList(0, MAX_IMAGES_PER_CHAPTER));
            }
            return out;
        } catch (Throwable t) {
            return out;
        }
    }

    private static List<String> extractChapterImages(File zipFile, LocalBook book,
                                                     LocalBook.ChapterRef ref) throws Exception {
        List<String> out = new ArrayList<>();
        File cache = chapterCache(book.id, ref.entry);
        try (ZipFile zip = new ZipFile(zipFile)) {
            List<String> entries = LocalArchive.imagesOf(zip, ref);
            int i = 0;
            for (String name : entries) {
                if (i >= MAX_IMAGES_PER_CHAPTER) break;
                String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".jpg";
                File target = new File(cache, String.format("%04d%s", i, ext));
                LocalArchive.extractTo(zip, name, target);
                out.add("file://" + target.getAbsolutePath());
                i++;
            }
        }
        return out;
    }

    private static List<String> extractEpubChapterImages(File epubFile, LocalBook book,
                                                         LocalBook.ChapterRef ref) throws Exception {
        List<String> out = new ArrayList<>();
        File cache = chapterCache(book.id, ref.entry);
        LocalEpub.Book eb = LocalEpub.parse(epubFile);
        LocalEpub.SpineItem hit = null;
        for (LocalEpub.SpineItem item : eb.spine) {
            if (item.path.equals(ref.entry)) {
                hit = item;
                break;
            }
        }
        if (hit == null) return out;
        List<String> entries = LocalEpub.imagesOf(epubFile, hit, eb.opfDir);
        try (ZipFile zip = new ZipFile(epubFile)) {
            int i = 0;
            for (String name : entries) {
                if (i >= MAX_IMAGES_PER_CHAPTER) break;
                byte[] raw = LocalEpub.rawOf(zip, name);
                if (raw == null || raw.length == 0) continue;
                String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".jpg";
                File target = new File(cache, String.format("%04d%s", i, ext));
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(target)) {
                    fos.write(raw);
                }
                out.add("file://" + target.getAbsolutePath());
                i++;
            }
        }
        return out;
    }

    private static List<String> extractArchive2ChapterImages(File archFile, LocalBook book,
                                                            LocalBook.ChapterRef ref) throws Exception {
        List<String> out = new ArrayList<>();
        File cache = chapterCache(book.id, ref.entry);
        List<String> entries = LocalArchive2.imagesOf(archFile, ref, book.format);
        int i = 0;
        for (String name : entries) {
            if (i >= MAX_IMAGES_PER_CHAPTER) break;
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".jpg";
            File target = new File(cache, String.format("%04d%s", i, ext));
            LocalArchive2.extractTo(archFile, name, target, book.format);
            out.add("file://" + target.getAbsolutePath());
            i++;
        }
        return out;
    }

    /** PDF：只渲染本章的页（30 页一组），不整本预渲。 */
    private static List<String> renderPdfChapter(File pdfFile, LocalBook book,
                                                 LocalBook.ChapterRef ref) throws Exception {
        File cache = chapterCache(book.id, "pdf_" + ref.startPage + "_" + ref.endPage);
        List<String> paths = LocalPdf.renderPages(pdfFile, ref.startPage, ref.endPage, cache);
        List<String> out = new ArrayList<>();
        for (String p : paths) out.add("file://" + p);
        return out;
    }

    /**
     * 章节图片缓存：按「书＋章」分目录（低-14）。此前一本书共用一个目录、读任何一章
     * 先全清，预读/并发读相邻章会把正在看的这章图片删掉（满屏空白）。现在只清
     * 本章自己的目录，别的章互不影响；缓存总量仍按章数自然有界。
     */
    private static File chapterCache(String bookId, String chapterKey) {
        File cache = new File(new File(LocalBookStore.cacheDir(), bookId), sanitizeKey(chapterKey));
        // 只清本章目录（重进本章时刷掉旧图）
        File[] fs = cache.listFiles();
        if (fs != null) {
            for (File f : fs) f.delete();
        } else {
            cache.mkdirs();
        }
        return cache;
    }

    private static String sanitizeKey(String key) {
        if (key == null || key.isEmpty()) return "chapter";
        String s = key.replaceAll("[^a-zA-Z0-9._-]", "_");
        // 纯点串（"."、".."、"..."）带路径导航语义（低-04）：原样当目录名会让章节
        // 缓存目录逃到父级——".." 时 chapterCache 指向 localbook-cache 本身，清目录
        // 会误伤他书、图也写进缓存根。映射成固定安全名，正常键行为不变。
        boolean allDots = true;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '.') {
                allDots = false;
                break;
            }
        }
        if (allDots) return "_";
        if (s.length() > 80) s = s.substring(0, 60) + "_" + (key.hashCode() & 0x7fffffff);
        return s;
    }
}
