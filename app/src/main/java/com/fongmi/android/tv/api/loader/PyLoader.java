package com.fongmi.android.tv.api.loader;

import android.os.FileObserver;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.chaquo.Loader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private final ConcurrentHashMap<String, Long> fileStamps;
    private final Loader loader;
    private volatile String recent;
    private FileObserver watcher;

    public PyLoader() {
        spiders = new ConcurrentHashMap<>();
        fileStamps = new ConcurrentHashMap<>();
        loader = new Loader();
    }

    public void clear() {
        spiders.values().forEach(Spider::destroy);
        spiders.clear();
        fileStamps.clear();
        recent = null;
    }

    public void remove(String key) {
        Spider spider = spiders.remove(key);
        fileStamps.remove(key);
        if (spider != null) {
            try {
                spider.destroy();
            } catch (Throwable ignored) {}
        }
        if (key.equals(recent)) recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    /** 监听 py 插件目录：文件被替换/删除时立即清掉内存里的旧 Spider，下次使用即加载新版。 */
    public synchronized void watchPlugins(File dir) {
        if (dir == null || watcher != null) return;
        try {
            watcher = new FileObserver(dir.getAbsolutePath(),
                    FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.MOVED_FROM
                            | FileObserver.DELETE | FileObserver.CREATE) {
                @Override
                public void onEvent(int event, String path) {
                    clear();
                }
            };
            watcher.startWatching();
        } catch (Throwable ignored) {}
    }

    private String resolve(String api) {
        if (api.startsWith("plugins/") && App.get() != null && App.get().getFilesDir() != null) {
            File f = new File(App.get().getFilesDir(), api);
            if (f.exists()) return f.getAbsolutePath();
        }
        return api;
    }

    private static long stampOf(File f) {
        return f.lastModified() * 31L + f.length();
    }

    public Spider getSpider(String key, String api, String ext) {
        String resolvedApi = resolve(api);
        boolean localFile = !api.startsWith("http");
        if (localFile && fileStamps.containsKey(key)) {
            File f = new File(resolvedApi);
            if (!f.exists()) {
                // 文件已被删除：丢掉旧缓存，不再提供过期 Spider
                remove(key);
                return new SpiderNull();
            }
            if (stampOf(f) != fileStamps.get(key)) {
                // 文件被替换：丢掉旧缓存，重新加载
                remove(key);
            }
        }
        return spiders.computeIfAbsent(key, k -> {
            try {
                Spider spider = loader.spider(resolvedApi);
                spider.siteKey = key;
                spider.init(App.get(), normalizeExt(ext));
                File f = new File(resolvedApi);
                if (localFile && f.exists()) fileStamps.put(k, stampOf(f));
                else fileStamps.remove(k);
                return spider;
            } catch (Throwable e) {
                e.printStackTrace();
                String msg = "PY加载失败 [" + key + "]: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                h.post(() -> android.widget.Toast.makeText(App.get(), msg, android.widget.Toast.LENGTH_LONG).show());
                spiders.remove(key);
                fileStamps.remove(key);
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


    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = spiders.get(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
