package com.fongmi.android.tv.localbook;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * mobi / azw / azw3（无 DRM）解析：PalmDB 容器 → PalmDOC 解压 → HTML 正文。
 * 正文抽成纯文本写 book.txt，章节带字符偏移（charMode），复用 TXT 懒读通道。
 * DRM：PalmDOC encryptionType != 0 或 EXTH 125-128 存在 → 拒收。
 * azw3：只走 mobi6 内容记录；KF8 独立结构（无 mobi6 部分）如实报不支持。
 */
public class LocalMobi {

    /** 原始文件上限（mobi 一般几 MB，256MB 足够防畸形）。 */
    private static final long MAX_FILE = 256L * 1024 * 1024;
    private static final int MAX_CHAPTERS = 5000;

    public static class DrmException extends Exception {
        public DrmException() {
            super("drm");
        }
    }

    public static class UnsupportedException extends Exception {
        public UnsupportedException(String reason) {
            super(reason);
        }
    }

    public static class Chapter {
        public String name = "";
        public long charStart = 0;
        public long charLength = 0;
        /** 纯文本（仅 extract 内部用，不进注册表）。 */
        public String text = "";
    }

    public static class Book {
        public String title = "";
        public List<Chapter> chapters = new ArrayList<>();
    }

    /**
     * 解析 mobi 并把纯文本写到 txtOut（UTF-8）。
     * 返回章节表（charMode 偏移，指向 txtOut）。
     */
    public static Book extract(File mobiFile, File txtOut)
            throws DrmException, UnsupportedException, Exception {
        if (!mobiFile.exists() || mobiFile.length() == 0) {
            throw new Exception("文件为空或不存在");
        }
        if (mobiFile.length() > MAX_FILE) throw new Exception("文件太大，导不了");
        byte[] data = readAll(mobiFile);
        int numRecords = u16(data, 76);
        if (numRecords < 2) throw new Exception("不是有效的 mobi（记录数异常）");
        int[] offsets = new int[numRecords + 1];
        for (int i = 0; i < numRecords; i++) {
            offsets[i] = (int) u32(data, 78 + i * 8);
        }
        offsets[numRecords] = data.length;

        byte[] rec0 = slice(data, offsets[0], offsets[1]);
        int compression = u16(rec0, 0);
        int encryptionType = u16(rec0, 12);
        if (encryptionType != 0) throw new DrmException();
        if (compression != 1 && compression != 2) {
            throw new UnsupportedException("这种压缩方式解析不了（type=" + compression + "）");
        }
        if (rec0.length < 16 + 4 || !"MOBI".equals(ascii(rec0, 16, 4))) {
            throw new Exception("不是有效的 mobi（缺 MOBI 头）");
        }
        int mobiHeaderLen = (int) u32(rec0, 20);
        int encoding = (int) u32(rec0, 16 + 0x0C);
        int firstContent = (int) u32(rec0, 16 + 0x50);
        int lastContent = (int) u32(rec0, 16 + 0x54);
        if (firstContent < 1 || lastContent < firstContent || lastContent >= numRecords) {
            throw new Exception("mobi 内容记录号异常");
        }
        checkExthDrm(rec0, 16 + mobiHeaderLen);
        String title = readTitle(rec0, 16 + mobiHeaderLen, encoding);

        Charset cs = encoding == 65001 ? StandardCharsets.UTF_8 : Charset.forName("windows-1252");
        StringBuilder html = new StringBuilder();
        for (int i = firstContent; i <= lastContent; i++) {
            byte[] rec = slice(data, offsets[i], offsets[i + 1]);
            if (rec.length == 0) continue;
            byte[] raw = compression == 2 ? palmDocDecompress(rec) : rec;
            if (encoding == 65001) raw = stripTrailingIncompleteUtf8(raw);
            html.append(new String(raw, cs));
            if (html.length() > MAX_FILE) break;
        }
        String htmlStr = html.toString();
        if (htmlStr.isEmpty()) throw new Exception("正文为空，解析失败");
        if (!htmlStr.contains("<") && !htmlStr.contains(">")) {
            // KF8 独立结构：内容记录是二进制片段，不是 HTML
            throw new UnsupportedException("这种 AZW3 是 KF8 独立结构，暂不支持（换 mobi 版）");
        }

        Book book = new Book();
        book.title = title;
        List<Chapter> chapters = splitChapters(htmlStr);
        // 写 book.txt（UTF-8），章节记字符偏移
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(txtOut), StandardCharsets.UTF_8)) {
            long pos = 0;
            for (Chapter c : chapters) {
                String block = c.name + "\n\n" + c.text + "\n\n";
                w.write(block);
                c.charStart = pos;
                c.charLength = block.length();
                pos += block.length();
            }
        }
        book.chapters = chapters;
        return book;
    }

    /** HTML → 按 h1-h6 切章；标题不足 2 个则整本一章。 */
    private static List<Chapter> splitChapters(String html) {
        List<Chapter> out = new ArrayList<>();
        try {
            Document d = Jsoup.parseBodyFragment(html);
            Elements heads = d.select("h1, h2, h3, h4, h5, h6");
            List<String> headTexts = new ArrayList<>();
            for (Element h : heads) {
                String t = h.text().trim();
                if (!t.isEmpty()) headTexts.add(t);
            }
            if (headTexts.size() >= 2) {
                StringBuilder cur = new StringBuilder();
                String curName = "开头";
                boolean started = false;
                for (Element e : d.getAllElements()) {
                    String tag = e.tagName();
                    // head 元数据不进正文
                    if ("title".equals(tag) || "script".equals(tag) || "style".equals(tag)) continue;
                    boolean isHead = tag.length() == 2 && tag.charAt(0) == 'h'
                            && tag.charAt(1) >= '1' && tag.charAt(1) <= '6';
                    if (isHead && !e.text().trim().isEmpty()) {
                        if (started) {
                            out.add(makeChapter(curName, cur.toString()));
                            if (out.size() >= MAX_CHAPTERS) break;
                        }
                        curName = trimTitle(e.text().trim());
                        cur.setLength(0);
                        started = true;
                    }
                    String own = e.ownText();
                    if (!own.isEmpty()) {
                        if (!started) {
                            started = true;
                            curName = "开头";
                        }
                        cur.append(own).append('\n');
                    }
                }
                if (started && cur.length() > 0 && out.size() < MAX_CHAPTERS) {
                    out.add(makeChapter(curName, cur.toString()));
                }
                if (!out.isEmpty()) return out;
            }
        } catch (Throwable ignored) {
        }
        // 回退：整本一章
        Chapter c = new Chapter();
        c.name = "全文";
        try {
            Document d = Jsoup.parseBodyFragment(html);
            c.text = d.body() != null ? d.body().text() : html;
        } catch (Throwable t) {
            c.text = html;
        }
        out.add(c);
        return out;
    }

    private static Chapter makeChapter(String name, String text) {
        Chapter c = new Chapter();
        c.name = name;
        c.text = text.trim();
        return c;
    }

    private static String trimTitle(String t) {
        t = t.replaceAll("\\s+", " ").trim();
        return t.length() > 48 ? t.substring(0, 48) : t;
    }

    /** EXTH 扫描：125/126/127/128 存在即 DRM。 */
    private static void checkExthDrm(byte[] rec0, int exthOff) throws DrmException {
        try {
            if (exthOff + 12 > rec0.length) return;
            if (!"EXTH".equals(ascii(rec0, exthOff, 4))) return;
            int count = (int) u32(rec0, exthOff + 8);
            int p = exthOff + 12;
            for (int i = 0; i < count && p + 8 <= rec0.length; i++) {
                int type = (int) u32(rec0, p);
                int len = (int) u32(rec0, p + 4);
                if (len < 8) break;
                if (type == 125 || type == 126 || type == 127 || type == 128) {
                    throw new DrmException();
                }
                p += len;
            }
        } catch (DrmException e) {
            throw e;
        } catch (Throwable ignored) {
        }
    }

    /** EXTH 503 取标题；没有则用 PalmDB 文件名（32 字节）。 */
    private static String readTitle(byte[] rec0, int exthOff, int encoding) {
        try {
            if (exthOff + 12 <= rec0.length && "EXTH".equals(ascii(rec0, exthOff, 4))) {
                int count = (int) u32(rec0, exthOff + 8);
                int p = exthOff + 12;
                Charset cs = encoding == 65001 ? StandardCharsets.UTF_8
                        : Charset.forName("windows-1252");
                for (int i = 0; i < count && p + 8 <= rec0.length; i++) {
                    int type = (int) u32(rec0, p);
                    int len = (int) u32(rec0, p + 4);
                    if (len < 8 || p + len > rec0.length) break;
                    if (type == 503) {
                        String t = new String(rec0, p + 8, len - 8, cs).trim();
                        if (!t.isEmpty()) return t;
                    }
                    p += len;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            int end = 0;
            while (end < 32 && rec0[end] != 0) end++;
            return new String(rec0, 0, end, StandardCharsets.ISO_8859_1).trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** 可扩容字节缓冲：数组与长度绑在一起，扩容不会丢（中-11 的根就在这）。 */
    private static class Buf {
        byte[] a;
        int n;

        Buf(int cap) {
            a = new byte[cap];
        }

        void put(byte b) {
            if (n >= a.length) a = grow(a);
            a[n++] = b;
        }
    }

    /** PalmDOC LZ77 解压（compression=2 的标准算法）。 */
    public static byte[] palmDocDecompress(byte[] data) {
        // 此前 put(out,n,b) 里 out=grow(out) 只改形参，扩容出的新数组被丢弃：
        // 写满初始容量后继续往旧数组越界写 → 解压必崩/截断。改 Buf 持有数组本体。
        Buf out = new Buf(Math.max(16, data.length * 2));
        int i = 0;
        while (i < data.length) {
            int c = data[i++] & 0xFF;
            if (c == 0) {
                out.put((byte) 0);
            } else if (c <= 8) {
                for (int k = 0; k < c && i < data.length; k++) {
                    out.put(data[i++]);
                }
            } else if (c <= 0x7F) {
                out.put((byte) c);
            } else if (c <= 0xBF) {
                if (i >= data.length) break;
                int c2 = data[i++] & 0xFF;
                int code = (c << 8) | c2;
                int dist = (code & 0x3FFF) >> 3;
                int len = (code & 7) + 3;
                for (int k = 0; k < len; k++) {
                    byte b = (dist <= out.n && dist > 0) ? out.a[out.n - dist] : 0;
                    out.put(b);
                }
            } else {
                out.put((byte) 0x20);
                out.put((byte) (c ^ 0x80));
            }
        }
        byte[] r = new byte[out.n];
        System.arraycopy(out.a, 0, r, 0, out.n);
        return r;
    }

    private static byte[] grow(byte[] out) {
        byte[] r = new byte[out.length * 2];
        System.arraycopy(out, 0, r, 0, out.length);
        return r;
    }

    /**
     * 去掉记录尾不完整的 UTF-8 字符（多字节重叠区）。
     * 只删"本来也解不出来"的字节，不碰有效数据。
     */
    public static byte[] stripTrailingIncompleteUtf8(byte[] data) {
        int n = data.length;
        if (n == 0) return data;
        // 从尾往前找 UTF-8 起始字节
        int i = n - 1;
        while (i >= 0 && (data[i] & 0xC0) == 0x80) i--;
        if (i < 0) return data;
        int b = data[i] & 0xFF;
        int expect;
        if (b < 0x80) return data;
        else if ((b & 0xE0) == 0xC0) expect = 2;
        else if ((b & 0xF0) == 0xE0) expect = 3;
        else if ((b & 0xF8) == 0xF0) expect = 4;
        else return data;
        int have = n - i;
        if (have < expect) {
            byte[] r = new byte[i];
            System.arraycopy(data, 0, r, 0, i);
            return r;
        }
        return data;
    }

    // ---------- 二进制小工具 ----------

    private static int u16(byte[] d, int off) {
        return ((d[off] & 0xFF) << 8) | (d[off + 1] & 0xFF);
    }

    private static long u32(byte[] d, int off) {
        return ((long) (d[off] & 0xFF) << 24) | ((long) (d[off + 1] & 0xFF) << 16)
                | ((long) (d[off + 2] & 0xFF) << 8) | (d[off + 3] & 0xFF);
    }

    private static String ascii(byte[] d, int off, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len && off + i < d.length; i++) sb.append((char) (d[off + i] & 0xFF));
        return sb.toString();
    }

    private static byte[] slice(byte[] d, int from, int to) {
        from = Math.max(0, from);
        to = Math.min(d.length, to);
        if (to <= from) return new byte[0];
        byte[] r = new byte[to - from];
        System.arraycopy(d, from, r, 0, r.length);
        return r;
    }

    private static byte[] readAll(File f) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long len = Math.min(raf.length(), MAX_FILE);
            byte[] d = new byte[(int) len];
            raf.readFully(d);
            return d;
        }
    }
}
