package com.fongmi.android.tv.localbook;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * zip/cbz 图包：按文件名自然序出图；章节按顶层目录分组（无目录则全一册）。
 * 加密 zip：java.util.zip 不支持加密条目，读取时抛异常即判定加密，拒收。
 */
public class LocalArchive {

    /** 包内全部图片条目（自然序）。 */
    public static List<String> listImages(ZipFile zip) {
        List<String> out = new ArrayList<>();
        java.util.Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (!e.isDirectory() && LocalBook.isImageName(e.getName())) {
                out.add(e.getName());
            }
        }
        out.sort(LocalBook::naturalCompare);
        return out;
    }

    /** 按顶层目录分组为章节。 */
    public static List<LocalBook.ChapterRef> chaptersOf(ZipFile zip) {
        List<String> images = listImages(zip);
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
        // 根目录的散图并入第一章
        if (groups.containsKey("") && !out.isEmpty()) {
            out.get(0).imageCount += groups.get("").size();
        }
        return out;
    }

    /** 取某章节的图片条目（自然序）。 */
    public static List<String> imagesOf(ZipFile zip, LocalBook.ChapterRef ref) {
        List<String> all = listImages(zip);
        if (ref.entry == null || ref.entry.isEmpty()) return all;
        List<String> out = new ArrayList<>();
        for (String n : all) {
            if (n.startsWith(ref.entry)) out.add(n);
        }
        return out;
    }

    /** 加密检测：尝试读第一张图的流，加密条目会抛异常。 */
    public static boolean isEncrypted(ZipFile zip) {
        try {
            List<String> images = listImages(zip);
            if (images.isEmpty()) return false;
            ZipEntry e = zip.getEntry(images.get(0));
            try (InputStream in = zip.getInputStream(e)) {
                byte[] b = new byte[16];
                int r = in.read(b);
                return r < 0;
            }
        } catch (Throwable t) {
            String msg = String.valueOf(t.getMessage()).toLowerCase();
            return msg.contains("encrypt") || msg.contains("password");
        }
    }

    /** 抽取条目到目标文件（图片懒解包用）。单条目上限 100MB，三种格式同一口径（低-15）。 */
    public static void extractTo(ZipFile zip, String entryName, java.io.File target) throws Exception {
        ZipEntry e = zip.getEntry(entryName);
        if (e == null) throw new Exception("entry missing");
        boolean tooLarge = false;
        try (InputStream in = zip.getInputStream(e);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(target)) {
            byte[] buf = new byte[8192];
            int r;
            long total = 0;
            while ((r = in.read(buf)) > 0) {
                total += r;
                // 超限不许静默截断当完整图交付（解出一半的图会被当好图缓存/显示）
                if (total > 100L * 1024 * 1024) {
                    tooLarge = true;
                    break;
                }
                fos.write(buf, 0, r);
            }
        }
        if (tooLarge) {
            target.delete();
            throw new Exception("entry too large");
        }
    }
}
