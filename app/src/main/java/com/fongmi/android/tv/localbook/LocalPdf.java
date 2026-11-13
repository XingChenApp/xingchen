package com.fongmi.android.tv.localbook;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF：系统 PdfRenderer 逐页渲染成图，走漫画阅读器。
 * 页按 30 页一组分章，读章时只渲染本章的页，不整本预渲。
 * 带密码/损坏的 PDF 拒收并给人话提示。
 */
public class LocalPdf {

    /** 每章页数（写死）：章节对话框不至于太长，读章渲染量有界。 */
    public static final int PAGES_PER_CHAPTER = 30;
    /** 渲染长边上限 px（防超大页爆内存）。 */
    private static final int MAX_SIDE = 2048;

    public static class PdfPasswordException extends Exception {
        public PdfPasswordException() {
            super("password");
        }
    }

    public static class PdfCorruptException extends Exception {
        public PdfCorruptException() {
            super("corrupt");
        }
    }

    /** 打开渲染器。带密码抛 PdfPasswordException，损坏抛 PdfCorruptException。 */
    public static PdfRenderer open(File pdf) throws PdfPasswordException, PdfCorruptException {
        ParcelFileDescriptor pfd = null;
        try {
            pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
            return new PdfRenderer(pfd);
        } catch (SecurityException se) {
            // 带密码的 PDF：PdfRenderer 构造抛 SecurityException
            closeQuiet(pfd);
            throw new PdfPasswordException();
        } catch (Throwable t) {
            closeQuiet(pfd);
            throw new PdfCorruptException();
        }
    }

    public static int pageCount(File pdf) throws PdfPasswordException, PdfCorruptException {
        PdfRenderer r = open(pdf);
        try {
            int n = r.getPageCount();
            if (n <= 0) throw new PdfCorruptException();
            return n;
        } finally {
            r.close();
        }
    }

    /** 按页数组章：startPage/endPage 记在 ChapterRef（0 起，含两端）。 */
    public static List<LocalBook.ChapterRef> chaptersOf(int pageCount) {
        List<LocalBook.ChapterRef> out = new ArrayList<>();
        int from = 0;
        while (from < pageCount) {
            int to = Math.min(from + PAGES_PER_CHAPTER - 1, pageCount - 1);
            LocalBook.ChapterRef c = new LocalBook.ChapterRef();
            c.startPage = from;
            c.endPage = to;
            c.imageCount = to - from + 1;
            c.name = (from == 0 && to == pageCount - 1)
                    ? ("全" + pageCount + "页")
                    : ("第" + (from + 1) + "-" + (to + 1) + "页");
            out.add(c);
            from = to + 1;
        }
        return out;
    }

    /**
     * 渲染 [fromPage, toPage] 到目录（0001.jpg…），渲染器只开一次。
     * 返回渲染出的文件绝对路径（按页序）。
     */
    public static List<String> renderPages(File pdf, int fromPage, int toPage, File dir)
            throws PdfPasswordException, PdfCorruptException {
        List<String> out = new ArrayList<>();
        PdfRenderer r = open(pdf);
        try {
            int n = r.getPageCount();
            int to = Math.min(toPage, n - 1);
            for (int i = Math.max(0, fromPage); i <= to; i++) {
                File target = new File(dir, String.format("%04d.jpg", i));
                try {
                    renderOne(r, i, target);
                } catch (Exception e) {
                    throw new PdfCorruptException();
                }
                out.add(target.getAbsolutePath());
            }
        } finally {
            r.close();
        }
        return out;
    }

    private static void renderOne(PdfRenderer r, int index, File target) throws Exception {
        PdfRenderer.Page page = r.openPage(index);
        Bitmap bmp = null;
        try {
            int w = page.getWidth();
            int h = page.getHeight();
            if (w <= 0 || h <= 0) return;
            float scale = Math.min(2.0f, (float) MAX_SIDE / Math.max(w, h));
            int rw = Math.max(1, Math.round(w * scale));
            int rh = Math.max(1, Math.round(h * scale));
            bmp = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            try (FileOutputStream fos = new FileOutputStream(target)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            }
        } finally {
            if (bmp != null) bmp.recycle();
            page.close();
        }
    }

    private static void closeQuiet(ParcelFileDescriptor pfd) {
        if (pfd == null) return;
        try {
            pfd.close();
        } catch (Throwable ignored) {
        }
    }
}
