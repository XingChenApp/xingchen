package com.fongmi.android.tv.localbook;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * 本地书导入：把源文件拷进应用私有目录后再建索引。
 * 导入即入库——源文件之后被删/被移走，不影响已导入的书。
 * 上限：单文件 2GB；图包/图文件夹图片数 ≤20000；单张图片 ≤100MB。
 */
public class LocalBookImporter {

    public static class ImportException extends Exception {
        public ImportException(String msg) {
            super(msg);
        }
    }

    public static class Result {
        public LocalBook book;
        public String warn = "";
    }

    /** 纯函数：按文件名判格式；不支持的抛 ImportException（人话）。供离线测试。 */
    public static String formatOf(String display) throws ImportException {
        String lower = display == null ? "" : display.toLowerCase();
        if (lower.endsWith(".txt")) return "TXT";
        if (lower.endsWith(".epub")) return "EPUB";
        if (lower.endsWith(".cbz")) return "CBZ";
        if (lower.endsWith(".zip")) return "ZIP";
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".mobi")) return "MOBI";
        if (lower.endsWith(".azw") || lower.endsWith(".azw3")) return "AZW3";
        if (lower.endsWith(".cbr")) return "CBR";
        if (lower.endsWith(".cb7")) return "CB7";
        if (lower.endsWith(".umd")) {
            throw new ImportException("umd 是已淘汰的老格式，星辰暂不支持（txt/epub/mobi 可正常导入）");
        }
        if (lower.endsWith(".chm")) {
            throw new ImportException("chm 格式暂不支持（可转成 pdf/epub 再导入）");
        }
        throw new ImportException("不支持的格式：" + display
                + "（收 txt/epub/pdf/mobi/azw3/zip/cbz/cbr/cb7）");
    }

    /** 从文件选择器 Uri 导入（txt/epub/zip/cbz/pdf/mobi/azw3/cbr/cb7）。 */
    public static Result importUri(Context ctx, Uri uri) throws ImportException {
        String display = displayName(ctx, uri);
        if (TextUtils.isEmpty(display)) display = "未命名";
        String format = formatOf(display);

        String id = LocalBook.newId();
        File dir = new File(LocalBookStore.booksDir(), id);
        dir.mkdirs();
        String ext = format.toLowerCase().equals("azw3") ? "azw3" : format.toLowerCase();
        File target = new File(dir, "book." + ext);
        try {
            copyUri(ctx, uri, target, 2L * 1024 * 1024 * 1024);
        } catch (ImportException e) {
            throw e;
        } catch (Throwable t) {
            throw new ImportException("文件拷入失败：" + t.getMessage());
        }

        String title = display.replaceAll("(?i)\\.(txt|epub|zip|cbz|pdf|mobi|azw3?|cbr|cb7)$", "");
        try {
            StringBuilder warn = new StringBuilder();
            LocalBook book = buildFromFile(id, dir, target, format, title, warn);
            LocalBookStore.save(book);
            Result r = new Result();
            r.book = book;
            r.warn = warn.toString();
            return r;
        } catch (ImportException e) {
            LocalBookStore.remove(id);
            throw e;
        } catch (Throwable t) {
            LocalBookStore.remove(id);
            throw new ImportException("解析失败：" + t.getMessage());
        }
    }

    /** 从文件夹树 Uri 导入图片文件夹。 */
    public static Result importTree(Context ctx, Uri treeUri) throws ImportException {
        String id = LocalBook.newId();
        File dir = new File(LocalBookStore.booksDir(), id);
        File files = new File(dir, "files");
        files.mkdirs();
        try {
            int count = copyTree(ctx, treeUri, files, "");
            if (count == 0) throw new ImportException("文件夹里没有图片");
            LocalBook book = new LocalBook();
            book.id = id;
            book.format = "DIR";
            book.kind = "COMIC";
            book.dir = dir.getAbsolutePath();
            book.fileName = "files";
            book.title = treeName(ctx, treeUri);
            book.chapters = dirChapters(files, "");
            book.chapterCount = book.chapters.size();
            book.coverPath = firstImageCover(files, book.chapters, dir);
            LocalBookStore.save(book);
            Result r = new Result();
            r.book = book;
            return r;
        } catch (ImportException e) {
            LocalBookStore.remove(id);
            throw e;
        } catch (Throwable t) {
            LocalBookStore.remove(id);
            throw new ImportException("文件夹导入失败：" + t.getMessage());
        }
    }

    private static LocalBook buildFromFile(String id, File dir, File target,
                                           String format, String title,
                                           StringBuilder warn) throws Exception {
        LocalBook book = new LocalBook();
        book.id = id;
        book.format = format;
        book.dir = dir.getAbsolutePath();
        book.fileName = target.getName();
        book.size = target.length();
        if ("TXT".equals(format)) {
            LocalTxtIndex.Result idx = LocalTxtIndex.index(target, title);
            book.kind = "NOVEL";
            book.charset = idx.charset;
            book.title = title;
            book.chapters = idx.chapters;
            if (idx.truncated) warn.append("文件太大，后半部分未收录（只收了前 256MB）");
        } else if ("EPUB".equals(format)) {
            try (ZipFile zip = new ZipFile(target)) {
                if (LocalEpub.hasDrm(zip)) {
                    throw new ImportException("这本 epub 带 DRM 加密，星辰打不开（换无 DRM 的版本）");
                }
            }
            LocalEpub.Book eb = LocalEpub.parse(target);
            LocalEpub.analyze(target, eb);
            book.title = TextUtils.isEmpty(eb.title) ? title : eb.title;
            boolean comic = eb.imageCount >= 5 && eb.textChars < 3000;
            book.kind = comic ? "COMIC" : "NOVEL";
            for (int i = 0; i < eb.spine.size(); i++) {
                LocalEpub.SpineItem item = eb.spine.get(i);
                LocalBook.ChapterRef c = new LocalBook.ChapterRef();
                c.name = TextUtils.isEmpty(item.title) ? ("第" + (i + 1) + "节") : item.title;
                c.entry = item.path;
                book.chapters.add(c);
            }
            if (book.chapters.isEmpty()) throw new ImportException("epub 里没有可读章节");
            if (!TextUtils.isEmpty(eb.coverEntry)) {
                book.coverPath = extractCoverEpub(target, eb.coverEntry, dir);
            }
            if (TextUtils.isEmpty(book.coverPath) && comic) {
                book.coverPath = extractFirstComicImage(book, dir);
            }
        } else if ("PDF".equals(format)) {
            int pages;
            try {
                pages = LocalPdf.pageCount(target);
            } catch (LocalPdf.PdfPasswordException pe) {
                throw new ImportException("这本 PDF 带密码，星辰打不开（去密码后再导入）");
            } catch (LocalPdf.PdfCorruptException ce) {
                throw new ImportException("这本 PDF 已损坏，打不开");
            }
            book.kind = "COMIC";
            book.title = title;
            book.chapters = LocalPdf.chaptersOf(pages);
            book.coverPath = renderPdfCover(target, dir);
        } else if ("MOBI".equals(format) || "AZW3".equals(format)) {
            File txtOut = new File(dir, "book.txt");
            LocalMobi.Book mb;
            try {
                mb = LocalMobi.extract(target, txtOut);
            } catch (LocalMobi.DrmException de) {
                throw new ImportException("这本" + ("MOBI".equals(format) ? " mobi " : " azw3 ")
                        + "带 DRM 加密，星辰打不开（换无 DRM 的版本）");
            } catch (LocalMobi.UnsupportedException ue) {
                throw new ImportException(ue.getMessage());
            }
            if (mb.chapters.isEmpty()) throw new ImportException("mobi 里没有可读章节");
            book.kind = "NOVEL";
            book.charset = "UTF-8";
            book.title = TextUtils.isEmpty(mb.title) ? title : mb.title;
            for (LocalMobi.Chapter mc : mb.chapters) {
                LocalBook.ChapterRef c = new LocalBook.ChapterRef();
                c.name = mc.name;
                c.charMode = true;
                c.charStart = mc.charStart;
                c.charLength = mc.charLength;
                book.chapters.add(c);
            }
        } else if ("CBR".equals(format) || "CB7".equals(format)) {
            List<LocalBook.ChapterRef> chapters;
            try {
                if (LocalArchive2.isEncrypted(target, format)) {
                    throw new ImportException("这是加密压缩包，星辰打不开（换未加密的版本）");
                }
                chapters = LocalArchive2.chaptersOf(target, format);
            } catch (LocalRar.Rar5Exception r5) {
                throw new ImportException("这是 RAR5 格式的压缩包，暂不支持（换 RAR4/zip 版）");
            } catch (LocalSevenZ.EncryptedException ee) {
                throw new ImportException("这是加密压缩包，星辰打不开（换未加密的版本）");
            } catch (ImportException e) {
                throw e;
            } catch (Throwable t) {
                throw new ImportException("压缩包已损坏或格式不对");
            }
            if (chapters.isEmpty()) throw new ImportException("压缩包里没有图片");
            book.kind = "COMIC";
            book.title = title;
            book.chapters = chapters;
            book.coverPath = extractCoverArchive2(target, format, chapters, dir);
        } else {
            // ZIP / CBZ
            try (ZipFile zip = new ZipFile(target)) {
                if (LocalArchive.isEncrypted(zip)) {
                    throw new ImportException("这是加密压缩包，星辰打不开（换未加密的版本）");
                }
                List<LocalBook.ChapterRef> chapters = LocalArchive.chaptersOf(zip);
                if (chapters.isEmpty()) throw new ImportException("压缩包里没有图片");
                book.kind = "COMIC";
                book.title = title;
                book.chapters = chapters;
                book.coverPath = extractCoverZip(target, chapters, dir);
            } catch (java.util.zip.ZipException ze) {
                throw new ImportException("压缩包已损坏或格式不对");
            }
        }
        book.chapterCount = book.chapters.size();
        return book;
    }

    /** 图片文件夹：子目录=章节；根目录散图并入"全一册"。 */
    private static List<LocalBook.ChapterRef> dirChapters(File files, String rel) {
        List<LocalBook.ChapterRef> out = new ArrayList<>();
        File[] subs = files.listFiles(File::isDirectory);
        List<String> names = new ArrayList<>();
        if (subs != null) {
            for (File s : subs) {
                if (hasImage(s)) names.add(s.getName());
            }
            names.sort(LocalBook::naturalCompare);
        }
        boolean rootHas = hasImage(files);
        for (String n : names) {
            LocalBook.ChapterRef c = new LocalBook.ChapterRef();
            c.name = n;
            c.entry = rel.isEmpty() ? n : rel + "/" + n;
            out.add(c);
        }
        if (out.isEmpty() && rootHas) {
            LocalBook.ChapterRef c = new LocalBook.ChapterRef();
            c.name = "全一册";
            c.entry = rel;
            out.add(c);
        } else if (rootHas && !out.isEmpty()) {
            // 根散图并入第一章（entry="" 时读整棵树）
            out.get(0).entry = "";
        }
        return out;
    }

    private static boolean hasImage(File dir) {
        File[] fs = dir.listFiles((d, n) -> LocalBook.isImageName(n));
        return fs != null && fs.length > 0;
    }

    /** 文件夹图片：entry=子目录相对路径（""=根，读整棵树）。 */
    public static List<String> dirImages(File files, String entry) {
        List<String> out = new ArrayList<>();
        File base = TextUtils.isEmpty(entry) ? files : new File(files, entry);
        collectImages(base, files, out);
        out.sort(LocalBook::naturalCompare);
        return out;
    }

    private static void collectImages(File dir, File root, List<String> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        String rootPath = root.getAbsolutePath();
        for (File f : fs) {
            if (f.isDirectory()) collectImages(f, root, out);
            else if (LocalBook.isImageName(f.getName())) {
                String abs = f.getAbsolutePath();
                out.add("file://" + abs);
            }
        }
    }

    // ---------- 封面 ----------

    private static String extractCoverEpub(File epub, String coverEntry, File dir) {
        try (ZipFile zip = new ZipFile(epub)) {
            byte[] raw = LocalEpub.rawOf(zip, coverEntry);
            if (raw == null || raw.length == 0) return "";
            File out = new File(dir, "cover.jpg");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(raw);
            }
            return out.getAbsolutePath();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String extractCoverZip(File zipFile, List<LocalBook.ChapterRef> chapters, File dir) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            List<String> images = LocalArchive.imagesOf(zip, chapters.get(0));
            if (images.isEmpty()) return "";
            File out = new File(dir, "cover.jpg");
            LocalArchive.extractTo(zip, images.get(0), out);
            return out.getAbsolutePath();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String extractCoverArchive2(File archFile, String format,
                                               List<LocalBook.ChapterRef> chapters, File dir) {
        try {
            List<String> images = LocalArchive2.imagesOf(archFile, chapters.get(0), format);
            if (images.isEmpty()) return "";
            File out = new File(dir, "cover.jpg");
            LocalArchive2.extractTo(archFile, images.get(0), out, format);
            return out.getAbsolutePath();
        } catch (Throwable t) {
            return "";
        }
    }

    /** PDF 封面：渲染第 1 页。 */
    private static String renderPdfCover(File pdf, File dir) {
        try {
            File cache = new File(dir, "cover-tmp");
            cache.mkdirs();
            List<String> pages = LocalPdf.renderPages(pdf, 0, 0, cache);
            if (pages.isEmpty()) return "";
            File out = new File(dir, "cover.jpg");
            copyFile(new File(pages.get(0)), out);
            deleteDir(cache);
            return out.getAbsolutePath();
        } catch (Throwable t) {
            return "";
        }
    }

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    private static String extractFirstComicImage(LocalBook book, File dir) {
        try {
            File epub = new File(dir, book.fileName);
            LocalEpub.Book eb = LocalEpub.parse(epub);
            try (ZipFile zip = new ZipFile(epub)) {
                for (LocalEpub.SpineItem item : eb.spine) {
                    List<String> imgs = LocalEpub.imagesOf(epub, item, eb.opfDir);
                    if (!imgs.isEmpty()) {
                        byte[] raw = LocalEpub.rawOf(zip, imgs.get(0));
                        if (raw == null || raw.length == 0) continue;
                        File out = new File(dir, "cover.jpg");
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            fos.write(raw);
                        }
                        return out.getAbsolutePath();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String firstImageCover(File files, List<LocalBook.ChapterRef> chapters, File dir) {
        try {
            List<String> imgs = dirImages(files, chapters.isEmpty() ? "" : chapters.get(0).entry);
            if (imgs.isEmpty()) return "";
            String src = imgs.get(0).substring("file://".length());
            File out = new File(dir, "cover.jpg");
            copyFile(new File(src), out);
            return out.getAbsolutePath();
        } catch (Throwable t) {
            return "";
        }
    }

    // ---------- 拷贝 ----------

    private static void copyUri(Context ctx, Uri uri, File target, long cap) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        try (InputStream in = cr.openInputStream(uri);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new Exception("打不开这个文件");
            byte[] buf = new byte[64 * 1024];
            int r;
            long total = 0;
            while ((r = in.read(buf)) > 0) {
                total += r;
                if (total > cap) throw new ImportException("文件太大（超过 2GB），导不了");
                out.write(buf, 0, r);
            }
            if (total == 0) throw new ImportException("这是个空文件");
        }
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new java.io.FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        }
    }

    /** 遍历 SAF 树，只拷图片（保留相对结构），返回图片数。 */
    private static int copyTree(Context ctx, Uri treeUri, File destRoot, String rel) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        String docId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId);
        int count = 0;
        Cursor c = null;
        try {
            c = cr.query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            if (c == null) return 0;
            while (c.moveToNext()) {
                String childId = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                boolean isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                Uri childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                if (isDir) {
                    File sub = new File(destRoot, sanitize(name));
                    sub.mkdirs();
                    count += copyTree(ctx, childUri, sub,
                            rel.isEmpty() ? sanitize(name) : rel + "/" + sanitize(name));
                } else if (LocalBook.isImageName(name)) {
                    if (count >= 20000) throw new ImportException("图片太多（超过 20000 张），导不了");
                    File target = new File(destRoot, sanitize(name));
                    copyUri(ctx, childUri, target, 100L * 1024 * 1024);
                    count++;
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return count;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[/\\\\]", "_");
    }

    private static String displayName(Context ctx, Uri uri) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        String p = uri.getLastPathSegment();
        return p == null ? "" : p;
    }

    private static String treeName(Context ctx, Uri treeUri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            int colon = docId.indexOf(':');
            String name = colon >= 0 ? docId.substring(colon + 1) : docId;
            if (name.isEmpty() || "/".equals(name)) return "图片文件夹";
            int slash = name.lastIndexOf('/');
            return slash >= 0 ? name.substring(slash + 1) : name;
        } catch (Throwable t) {
            return "图片文件夹";
        }
    }
}
