package com.fongmi.android.tv.localbook;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * txt 分章：按行扫描，章节标题正则命中即切一章，记录字节（或字符）偏移。
 * 分不出章节时整本一章，不报错。标题行过长（>48 字）不认，防正文误切。
 */
public class LocalTxtIndex {

    private static final Pattern CHAPTER = Pattern.compile(
            "^\\s*(?:"
                    + "第[一二三四五六七八九十百千万两零〇○\\d\\s]+[章节回节篇卷集话]"
                    + "|序章|楔子|引子|前言|后记|终章|尾声"
                    + "|番外(?:[（(]?\\d+[）)]?)?"
                    + "|[Cc]hapter\\s*\\d+"
                    + ")\\s*.{0,40}$");

    public static class Result {
        public List<LocalBook.ChapterRef> chapters = new ArrayList<>();
        public String charset = "UTF-8";
        /** 字符模式文件超 256MB 上限被截断（低-13）：导入侧据此提示用户后半本缺失，别静默当全本。 */
        public boolean truncated = false;
    }

    public static Result index(File file, String title) {
        Result res = new Result();
        try {
            // 先定编码（读样本）
            String charset = "UTF-8";
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                int n = (int) Math.min(raf.length(), 128 * 1024);
                byte[] sample = new byte[n];
                raf.readFully(sample);
                charset = LocalEncoding.detectBytes(sample, n);
            }
            res.charset = charset;
            if (LocalEncoding.isCharMode(charset)) {
                indexCharMode(file, charset, title, res);
            } else {
                indexByteMode(file, charset, title, res);
            }
        } catch (Throwable t) {
            res.chapters.clear();
        }
        if (res.chapters.isEmpty()) {
            LocalBook.ChapterRef one = new LocalBook.ChapterRef();
            one.name = title;
            one.start = 0;
            one.length = file.length();
            res.chapters.add(one);
        }
        return res;
    }

    /** 字节模式：UTF-8/GBK/Big5，按 \n 切行，逐行记录字节偏移。 */
    private static void indexByteMode(File file, String charsetName, String title, Result res) throws Exception {
        Charset cs = LocalEncoding.charsetOf(charsetName);
        List<Long> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLen = raf.length();
            long pos = 0;
            java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream();
            boolean firstLine = true;
            // 分块读（低-13）：此前逐字节 raf.read()，大 txt 每字节一次系统调用，导入慢到像卡死。
            // 分行/偏移语义与逐字节版逐字一致（pos 仍按字节累计、lineStart 公式不变）。
            byte[] chunk = new byte[64 * 1024];
            int got;
            while ((got = raf.read(chunk)) != -1) {
                for (int ci = 0; ci < got; ci++) {
                    int b = chunk[ci] & 0xFF;
                    pos++;
                    if (b == '\n' || pos >= fileLen) {
                        if (pos >= fileLen && b != '\n' && b != '\r') line.write(b);
                        byte[] raw = line.toByteArray();
                        int end = raw.length;
                        if (end > 0 && raw[end - 1] == '\r') end--;
                        String text = new String(raw, 0, end, cs).trim();
                        long lineStart = pos - raw.length - (b == '\n' ? 1 : 0);
                        if (text.length() > 0 && text.length() <= 48 && isChapterTitle(text)) {
                            starts.add(lineStart);
                            names.add(text);
                        } else if (firstLine && text.length() > 0) {
                            // 卷首语：第一行不是标题时，从文件头起一章也记不上，先留着
                        }
                        firstLine = false;
                        line.reset();
                    } else {
                        line.write(b);
                    }
                }
            }
            long fileEnd = fileLen;
            // 卷首（第一个标题之前的部分）单独成章"开头"
            if (!starts.isEmpty() && starts.get(0) > 0) {
                LocalBook.ChapterRef pre = new LocalBook.ChapterRef();
                pre.name = "开头";
                pre.start = 0;
                pre.length = starts.get(0);
                res.chapters.add(pre);
            }
            for (int i = 0; i < starts.size(); i++) {
                LocalBook.ChapterRef c = new LocalBook.ChapterRef();
                c.name = names.get(i);
                c.start = starts.get(i);
                c.length = (i + 1 < starts.size() ? starts.get(i + 1) : fileEnd) - c.start;
                res.chapters.add(c);
            }
        }
    }

    /** 字符模式：UTF-16 类整串解码后按字符偏移分章（整本解码一次，异常直接整本一章）。 */
    private static void indexCharMode(File file, String charsetName, String title, Result res) throws Exception {
        Charset cs = LocalEncoding.charsetOf(charsetName);
        if (file.length() > 256L * 1024 * 1024) res.truncated = true;
        byte[] all = new byte[(int) Math.min(file.length(), 256L * 1024 * 1024)];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.readFully(all, 0, (int) Math.min(file.length(), all.length));
        }
        String text = new String(all, cs);
        String[] lines = text.split("\n", -1);
        List<Integer> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int offset = 0;
        for (String raw : lines) {
            String t = raw.trim();
            if (t.length() > 0 && t.length() <= 48 && isChapterTitle(t)) {
                starts.add(offset);
                names.add(t);
            }
            offset += raw.length() + 1;
        }
        if (!starts.isEmpty() && starts.get(0) > 0) {
            LocalBook.ChapterRef pre = new LocalBook.ChapterRef();
            pre.name = "开头";
            pre.charMode = true;
            pre.charStart = 0;
            pre.charLength = starts.get(0);
            res.chapters.add(pre);
        }
        for (int i = 0; i < starts.size(); i++) {
            LocalBook.ChapterRef c = new LocalBook.ChapterRef();
            c.name = names.get(i);
            c.charMode = true;
            c.charStart = starts.get(i);
            c.charLength = (i + 1 < starts.size() ? starts.get(i + 1) : text.length()) - c.charStart;
            res.chapters.add(c);
        }
    }

    private static boolean isChapterTitle(String line) {
        Matcher m = CHAPTER.matcher(line);
        return m.matches();
    }

    /** 按章节引用取正文（字节模式）。 */
    public static String readChapter(File file, String charsetName, LocalBook.ChapterRef ref) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long len = Math.min(ref.length, 8L * 1024 * 1024);
            byte[] buf = new byte[(int) len];
            raf.seek(ref.start);
            raf.readFully(buf);
            return new String(buf, LocalEncoding.charsetOf(charsetName));
        } catch (Throwable t) {
            return "";
        }
    }

    /** 按章节引用取正文（字符模式）。 */
    public static String readChapterCharMode(File file, String charsetName, LocalBook.ChapterRef ref) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            int n = (int) Math.min(file.length(), 256L * 1024 * 1024);
            byte[] all = new byte[n];
            raf.readFully(all);
            String text = new String(all, LocalEncoding.charsetOf(charsetName));
            int s = (int) Math.min(ref.charStart, text.length());
            int e = (int) Math.min(ref.charStart + ref.charLength, text.length());
            return s >= e ? "" : text.substring(s, e);
        } catch (Throwable t) {
            return "";
        }
    }
}
