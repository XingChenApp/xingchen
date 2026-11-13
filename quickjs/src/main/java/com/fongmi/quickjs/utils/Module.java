package com.fongmi.quickjs.utils;

import android.text.TextUtils;
import android.util.LruCache;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Util;

public class Module {

    private static final int MAX_SIZE = 50;
    private final LruCache<String, String> cache;

    public Module() {
        cache = new LruCache<>(MAX_SIZE);
    }

    public static Module get() {
        return Loader.INSTANCE;
    }

    public String fetch(String name) {
        String content = cache.get(name);
        if (!TextUtils.isEmpty(content)) return content;
        String url = name;
        String md5 = "";
        if (name.contains(";md5;")) {
            String[] texts = name.split(";md5;");
            url = texts[0];
            md5 = texts.length > 1 ? texts[1].trim() : "";
            if (md5.startsWith("http")) md5 = OkHttp.string(md5).trim();
        }
        if (url.startsWith("http")) cache.put(name, content = OkHttp.string(url));
        else if (url.startsWith("assets")) cache.put(name, content = Asset.read(url));
        else if (url.startsWith("lib/")) cache.put(name, content = Asset.read("js/" + url));
        else if (url.startsWith("/")) cache.put(name, content = readLocalFile(url));
        if (!md5.isEmpty() && !TextUtils.isEmpty(content)) {
            String actual = Util.md5(content);
            if (!actual.equalsIgnoreCase(md5)) {
                cache.remove(name);
                throw new RuntimeException("js md5 mismatch: " + url);
            }
        }
        return content;
    }

    private String readLocalFile(String path) {
        try {
            java.io.File file = new java.io.File(path);
            if (!file.exists()) return null;
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            // 单次 read 不保证读满：循环读到文件尾，慢存储／大文件也不会
            // 拿到截断的脚本；流走 try-with-resources，抛错也关。
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                int read;
                while (offset < bytes.length && (read = fis.read(bytes, offset, bytes.length - offset)) != -1) offset += read;
            }
            return new String(bytes, 0, offset, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void clear() {
        cache.evictAll();
    }

    private static class Loader {
        static volatile Module INSTANCE = new Module();
    }
}
