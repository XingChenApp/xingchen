package com.fongmi.android.tv.api.loader;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.chaquo.Loader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private final Loader loader;
    private volatile String recent;

    public PyLoader() {
        spiders = new ConcurrentHashMap<>();
        loader = new Loader();
    }

    public void clear() {
        spiders.values().forEach(Spider::destroy);
        spiders.clear();
        recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext) {
        return spiders.computeIfAbsent(key, k -> {
            try {
                logDiag("getSpider key=" + key + " api=" + api + " ext=" + ext);
                String resolvedApi = api;
                if (api.startsWith("plugins/") && App.get() != null && App.get().getFilesDir() != null) {
                    java.io.File _f = new java.io.File(App.get().getFilesDir(), api);
                    logDiag("file exists=" + _f.exists() + " size=" + (_f.exists() ? _f.length() : -1) + " path=" + _f.getAbsolutePath());
                    if (_f.exists()) resolvedApi = _f.getAbsolutePath();
                }
                logDiag("calling loader.spider with: " + resolvedApi.substring(0, Math.min(200, resolvedApi.length())));
                Spider spider = loader.spider(resolvedApi);
                logDiag("loader.spider returned: " + spider.getClass().getName());
                spider.siteKey = key;
                spider.init(App.get(), normalizeExt(ext));
                return spider;
            } catch (Throwable e) {
                e.printStackTrace();
                logDiag("EXCEPTION: " + e.getClass().getName() + ": " + e.getMessage());
                String msg = "PY加载失败: " + e.getMessage();
                android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                h.post(() -> android.widget.Toast.makeText(App.get(), msg, android.widget.Toast.LENGTH_LONG).show());
                return new SpiderNull();
            }
        });
    }

    private String normalizeExt(String ext) {
        String value = TextUtils.isEmpty(ext) ? "" : ext.trim();
        // Many live Python spiders treat ext as an option object and call .get().
        // TV-style configs commonly express an empty extension as [], which would
        // otherwise deserialize to a list and crash those spiders during init.
        return "[]".equals(value) ? "{}" : ext;
    }

    private static void logDiag(String msg) {
        try {
            java.io.File logFile = new java.io.File(App.get().getFilesDir(), "py_diag.log");
            java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
            fw.write(System.currentTimeMillis() + " " + msg + "\n");
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = spiders.get(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
