package com.fongmi.android.tv.localbook;

import org.apache.commons.compress.PasswordRequiredException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * cb7（7z 图包）：commons-compress 纯 Java 解包。
 * 章节口径与 zip/cbz 一致：顶层目录=章节＋自然序；加密包拒收。
 */
public class LocalSevenZ {

    public static class EncryptedException extends Exception {
        public EncryptedException() {
            super("encrypted");
        }
    }

    /** 包内全部图片条目（自然序，目录除外）。 */
    public static List<String> listImages(File fz) throws Exception {
        List<String> out = new ArrayList<>();
        try (SevenZFile zf = new SevenZFile(fz)) {
            for (SevenZArchiveEntry e : zf.getEntries()) {
                if (e.isDirectory()) continue;
                String name = normalize(e.getName());
                if (LocalBook.isImageName(name)) out.add(name);
            }
        } catch (PasswordRequiredException p) {
            throw new EncryptedException();
        }
        out.sort(LocalBook::naturalCompare);
        return out;
    }

    /** 按顶层目录分组为章节（与 LocalArchive.chaptersOf 同口径）。 */
    public static List<LocalBook.ChapterRef> chaptersOf(File fz) throws Exception {
        List<String> images = listImages(fz);
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
    public static List<String> imagesOf(File fz, LocalBook.ChapterRef ref) throws Exception {
        List<String> all = listImages(fz);
        if (ref.entry == null || ref.entry.isEmpty()) return all;
        List<String> out = new ArrayList<>();
        for (String n : all) {
            if (n.startsWith(ref.entry)) out.add(n);
        }
        return out;
    }

    /** 加密检测：读第一张图，撞密码墙即判定加密。 */
    public static boolean isEncrypted(File fz) {
        try (SevenZFile zf = new SevenZFile(fz)) {
            SevenZArchiveEntry e;
            while ((e = zf.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = normalize(e.getName());
                if (!LocalBook.isImageName(name)) continue;
                byte[] buf = new byte[16];
                zf.read(buf, 0, 16);
                return false;
            }
            return false;
        } catch (PasswordRequiredException p) {
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 抽取条目到目标文件（图片懒解包用）。 */
    public static void extractTo(File fz, String entryName, File target) throws Exception {
        try (SevenZFile zf = new SevenZFile(fz)) {
            SevenZArchiveEntry e;
            boolean hit = false;
            while ((e = zf.getNextEntry()) != null) {
                if (normalize(e.getName()).equals(entryName)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) throw new Exception("entry missing");
            boolean tooLarge = false;
            try (OutputStream os = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int r;
                long total = 0;
                while ((r = zf.read(buf, 0, buf.length)) > 0) {
                    total += r;
                    // 超限不许静默截断交付（低-15，与 zip/rar 同一口径）：删半成品并抛错
                    if (total > 100L * 1024 * 1024) {
                        tooLarge = true;
                        break;
                    }
                    os.write(buf, 0, r);
                }
            }
            if (tooLarge) {
                target.delete();
                throw new Exception("entry too large");
            }
        } catch (PasswordRequiredException p) {
            throw new EncryptedException();
        }
    }

    private static String normalize(String name) {
        return name == null ? "" : name.replace('\\', '/');
    }
}
