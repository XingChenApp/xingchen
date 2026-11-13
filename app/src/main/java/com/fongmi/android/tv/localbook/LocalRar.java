package com.fongmi.android.tv.localbook;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * cbr（RAR 图包）：junrar 纯 Java 解包（支持 RAR 1.5-4.x，不支持 RAR5）。
 * 章节口径与 zip/cbz 一致：顶层目录=章节＋自然序；加密包拒收。
 */
public class LocalRar {

    public static class Rar5Exception extends Exception {
        public Rar5Exception() {
            super("rar5");
        }
    }

    /** 包内全部图片条目（自然序，目录除外）。 */
    public static List<String> listImages(File rar) throws Exception {
        List<String> out = new ArrayList<>();
        Archive a = open(rar);
        try {
            for (FileHeader fh : a.getFileHeaders()) {
                if (fh.isDirectory()) continue;
                String name = normalize(fh.getFileNameString());
                if (LocalBook.isImageName(name)) out.add(name);
            }
        } finally {
            a.close();
        }
        out.sort(LocalBook::naturalCompare);
        return out;
    }

    /** 按顶层目录分组为章节（与 LocalArchive.chaptersOf 同口径）。 */
    public static List<LocalBook.ChapterRef> chaptersOf(File rar) throws Exception {
        List<String> images = listImages(rar);
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String name : images) {
            int slash = name.indexOf('/');
            String key = slash >= 0 ? name.substring(0, slash) : "";
            List<String> g = groups.get(key);
            if (g == null) {
                g = new ArrayList<>();
                groups.put(key, g);
            }
            g.add(name);
        }
        List<LocalBook.ChapterRef> out = new ArrayList<>();
        if (groups.size() == 1 && groups.containsKey("")) {
            LocalBook.ChapterRef c = new LocalBook.ChapterRef();
            c.name = "全一册";
            c.entry = "";
            c.imageCount = groups.get("").size();
            out.add(c);
            return out;
        }
        List<String> keys = new ArrayList<>(groups.keySet());
        keys.sort(LocalBook::naturalCompare);
        for (String key : keys) {
            if (key.isEmpty()) continue;
            LocalBook.ChapterRef c = new LocalBook.ChapterRef();
            c.name = key;
            c.entry = key + "/";
            c.imageCount = groups.get(key).size();
            out.add(c);
        }
        if (groups.containsKey("") && !out.isEmpty()) {
            out.get(0).imageCount += groups.get("").size();
        }
        return out;
    }

    /** 取某章节的图片条目（自然序）。 */
    public static List<String> imagesOf(File rar, LocalBook.ChapterRef ref) throws Exception {
        List<String> all = listImages(rar);
        if (ref.entry == null || ref.entry.isEmpty()) return all;
        List<String> out = new ArrayList<>();
        for (String n : all) {
            if (n.startsWith(ref.entry)) out.add(n);
        }
        return out;
    }

    /** 加密检测：任一条目加密即拒收。 */
    public static boolean isEncrypted(File rar) {
        Archive a = null;
        try {
            a = open(rar);
            for (FileHeader fh : a.getFileHeaders()) {
                if (!fh.isDirectory() && fh.isEncrypted()) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        } finally {
            if (a != null) {
                try {
                    a.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 抽取条目到目标文件（图片懒解包用）。 */
    public static void extractTo(File rar, String entryName, File target) throws Exception {
        Archive a = open(rar);
        try {
            FileHeader hit = null;
            for (FileHeader fh : a.getFileHeaders()) {
                if (fh.isDirectory()) continue;
                if (normalize(fh.getFileNameString()).equals(entryName)) {
                    hit = fh;
                    break;
                }
            }
            if (hit == null) throw new Exception("entry missing");
            if (hit.isEncrypted()) throw new Exception("encrypted");
            // junrar 整条目直写没有上限（低-15）：套计数流，超 100MB 与 zip/7z 同口径抛错删半成品
            CountingOutputStream cos = new CountingOutputStream(new FileOutputStream(target));
            try (OutputStream os = cos) {
                a.extractFile(hit, os);
            } catch (Exception ex) {
                if (cos.total > 100L * 1024 * 1024) {
                    target.delete();
                    throw new Exception("entry too large");
                }
                throw ex;
            }
        } finally {
            a.close();
        }
    }

    /** 计数输出流：累计写入量，超限从 write 里抛 IOException 打断 junrar 的整条目直写。 */
    private static class CountingOutputStream extends java.io.FilterOutputStream {
        long total = 0;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws java.io.IOException {
            if (++total > 100L * 1024 * 1024) throw new java.io.IOException("entry too large");
            super.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws java.io.IOException {
            total += len;
            if (total > 100L * 1024 * 1024) throw new java.io.IOException("entry too large");
            super.write(b, off, len);
        }
    }

    private static Archive open(File rar) throws Rar5Exception, Exception {
        try {
            return new Archive(rar);
        } catch (com.github.junrar.exception.UnsupportedRarV5Exception v5) {
            throw new Rar5Exception();
        } catch (RarException e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("Rar5") || msg.contains("RAR5") || msg.contains("rar5")
                    || msg.contains("Unknown RAR version")) {
                throw new Rar5Exception();
            }
            throw new Exception("RAR 解析失败：" + msg);
        } catch (java.io.IOException e) {
            throw new Exception("RAR 打不开：" + e.getMessage());
        }
    }

    /** 统一分隔符（RAR 里可能是反斜杠）。 */
    private static String normalize(String name) {
        return name == null ? "" : name.replace('\\', '/');
    }
}
