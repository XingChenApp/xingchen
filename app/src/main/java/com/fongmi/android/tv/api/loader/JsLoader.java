package com.fongmi.android.tv.api.loader;

import android.os.FileObserver;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.quickjs.crawler.DrpySpider;
import com.fongmi.quickjs.crawler.Loader;
import com.fongmi.quickjs.crawler.WvSpider;
import com.fongmi.quickjs.utils.Module;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.SpiderNull;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class JsLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private final ConcurrentHashMap<String, Set<String>> pathKeys;
    private final Loader loader;
    private volatile String recent;
    private FileObserver watcher;
    private File watchDir;

    public JsLoader() {
        spiders = new ConcurrentHashMap<>();
        pathKeys = new ConcurrentHashMap<>();
        loader = new Loader();
    }

    public void clear() {
        spiders.values().forEach(Spider::destroy);
        Module.get().clear();
        spiders.clear();
        pathKeys.clear();
        recent = null;
    }

    public void remove(String key) {
        Spider spider = spiders.remove(key);
        removeKeyFromPaths(key);
        if (key != null && key.equals(recent)) recent = null;
        // 与 PyLoader.remove 同款：删 JS 源先清该源解析缓存，否则同 key|flag|id
        // 的旧解析 URL 在 TTL 内仍可命中（按 key| 前缀清，与无参全清版区分）。
        try {
            SiteApi.clearPlayerCache(key);
        } catch (Throwable ignored) {
        }
        if (spider != null) {
            try {
                spider.destroy();
            } catch (Throwable ignored) {}
        }
    }

    private void recordPath(String key, String api) {
        if (key == null || api == null) return;
        pathKeys.computeIfAbsent(api, k -> ConcurrentHashMap.newKeySet()).add(key);
        try {
            File f = new File(api);
            if (f.isAbsolute()) pathKeys.computeIfAbsent(f.getAbsolutePath(), k -> ConcurrentHashMap.newKeySet()).add(key);
            String name = f.getName();
            if (name != null && !name.isEmpty()) pathKeys.computeIfAbsent(name, k -> ConcurrentHashMap.newKeySet()).add(key);
        } catch (Throwable ignored) {
        }
    }

    private void removeKeyFromPaths(String key) {
        if (key == null) return;
        for (Set<String> keys : pathKeys.values()) keys.remove(key);
    }

    private List<String> keysForEvent(String path) {
        List<String> out = new ArrayList<>();
        if (path == null) return out;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        String[] lookups = new String[]{path, new File(path).getName(),
                watchDir == null ? null : new File(watchDir, path).getAbsolutePath()};
        for (String lookup : lookups) {
            if (lookup == null) continue;
            Set<String> keys = pathKeys.get(lookup);
            if (keys != null) seen.addAll(keys);
        }
        out.addAll(seen);
        return out;
    }

    /** 按文件单清：只清事件文件对应的蜘蛛＋该源首页缓存＋解析缓存，并发首页刷新。 */
    private void invalidateScript(String path) {
        for (String key : keysForEvent(path)) {
            try {
                SiteApi.clearHomeCache(key);
            } catch (Throwable ignored) {
            }
            try {
                SiteApi.clearPlayerCache(key);
            } catch (Throwable ignored) {
            }
            remove(key);
        }
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    /** 监听 js 插件目录：文件被替换/删除时只清该文件对应的蜘蛛，下次使用即加载新版，不再全量清。 */
    public synchronized void watchPlugins(File dir) {
        if (dir == null || watcher != null) return;
        watchDir = new File(dir.getAbsolutePath());
        try {
            watcher = new FileObserver(dir.getAbsolutePath(),
                    FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.MOVED_FROM
                            | FileObserver.DELETE | FileObserver.CREATE) {
                @Override
                public void onEvent(int event, String path) {
                    SpiderDebug.log("jsloader", "watch event=%s path=%s", event, path);
                    if (path == null || !(path.toLowerCase().endsWith(".js") || path.toLowerCase().endsWith(".wv"))) return;
                    try {
                        invalidateScript(path);
                        RefreshEvent.home();
                    } catch (Throwable ignored) {
                    }
                }
            };
            watcher.startWatching();
        } catch (Throwable ignored) {}
    }

    private static class SpiderLoadFailed extends RuntimeException {
        SpiderLoadFailed(Throwable cause) {
            super(cause);
        }
    }

    private static boolean isInterrupted(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof InterruptedException) return true;
        }
        return false;
    }

    /**
     * 通道分发：新格式宿主型 JS（wvB/WvSpider 类）进 WvSpider，drpy 型
     * （规则＋引擎）进 DrpySpider，其余仍走普通通道。互斥顺序定死：
     * ① api 名字指 drpy2 引擎的先判（不取内容，远程引擎一律改走内置定版）；
     * ② 再按内容判 Wv（判定口径与原 isWv 一字未动，含 .wv 后缀回退）；
     * ③ 再按内容判 drpy（引擎指纹或裸规则形态，__JS_SPIDER__ 本体在
     * isBareRule 里有一票否决）；④ 都不中才是普通通道。已有三路的判定
     * 与路径不变，drpy 只在它们都不认时才接。
     */
    private boolean isWv(String api) {
        try {
            String content = Module.get().fetch(api);
            if (content != null && !content.isEmpty()) return WvSpider.isWvSource(content);
        } catch (Throwable ignored) {
        }
        return api != null && api.contains(".wv");
    }

    private boolean isDrpyContent(String api) {
        try {
            String content = Module.get().fetch(api);
            if (content == null || content.isEmpty()) return false;
            return DrpySpider.isEngineContent(content) || DrpySpider.isBareRule(content);
        } catch (Throwable ignored) {
        }
        return false;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        try {
            return spiders.computeIfAbsent(key, k -> {
                Spider spider = null;
                try {
                    spider = DrpySpider.isEngineApi(api) ? loader.drpySpider(api, BaseLoader.get().dex(jar))
                            : isWv(api) ? loader.wvSpider(api)
                            : isDrpyContent(api) ? loader.drpySpider(api, BaseLoader.get().dex(jar))
                            : loader.spider(api, BaseLoader.get().dex(jar));
                    spider.siteKey = key;
                    spider.init(App.get(), ext);
                    recordPath(key, api);
                    return spider;
                } catch (Throwable e) {
                    if (isInterrupted(e)) {
                        // 被取消（顶替/销毁/外层超时打断等待）：不是 init 失败，
                        // 不打失败日志与堆栈；销毁与不缓存口径与失败相同。
                        SpiderDebug.log("JsLoader", "JS spider init cancelled: key=%s api=%s", key, api);
                    } else {
                        SpiderDebug.log("JsLoader", "JS spider init failed: key=%s api=%s", key, api);
                        SpiderDebug.log("JsLoader", e);
                        e.printStackTrace();
                    }
                    // init 失败也要确定性销毁（destroy 内部走 releaseJS），别把 QuickJS 上下文留给 GC。
                    if (spider != null) {
                        try {
                            spider.destroy();
                        } catch (Throwable ignored) {
                        }
                    }
                    throw new SpiderLoadFailed(e);
                }
            });
        } catch (SpiderLoadFailed e) {
            return new SpiderNull();
        }
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = spiders.get(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
