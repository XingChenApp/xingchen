package com.fongmi.android.tv.api.loader;
import com.fongmi.android.tv.utils.ToastUtil;

import android.os.FileObserver;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.chaquo.Loader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private final ConcurrentHashMap<String, Long> fileStamps;
    /** key → 连续创建失败标记（次数＋最近失败时刻）：给静默路径的失败退避用，内存态、不落盘。 */
    private final ConcurrentHashMap<String, FailMark> failMarks;
    /** 脚本绝对路径 → 用它创建过的源 key（给目录监听单清用：只清该文件对应的蜘蛛）。 */
    private final ConcurrentHashMap<String, Set<String>> pathKeys;
    /** key → 创建来源（api 原串＋ext）：代理分发撞上死实例时按它重建当前活蜘蛛，
     * 代理路（do=py 只带 recent）自己没有 api/ext，不记来源就只能回空等死。 */
    private final ConcurrentHashMap<String, String[]> origins;
    private final Loader loader;
    private volatile String recent;
    private FileObserver watcher;
    private File watchDir;

    /** 本进程是否有蜘蛛成功创建过：给首页冷热预算判定用（SiteApi.isColdPyHomeLoad）。 */
    private static final AtomicBoolean anySpider = new AtomicBoolean(false);
    private static final java.util.Set<String> createdKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean hasAnySpider() {
        return anySpider.get();
    }

    /** 该源的蜘蛛是否已成功创建过：给首页预算判定用（只看本源，不看进程第一次）。 */
    public static boolean hasSpider(String key) {
        return key != null && createdKeys.contains(key);
    }

    /** 蜘蛛创建失败的包装（unchecked）：穿过 computeIfAbsent 时不被缓存，下次调用可重试。 */
    private static class SpiderLoadFailed extends RuntimeException {
        SpiderLoadFailed(Throwable cause) {
            super(cause);
        }
    }

    /**
     * 蜘蛛创建被取消的包装：异常链里含 InterruptedException＝本次加载线程被自家
     * 取消机制打断（新加载顶替／页面销毁／外层超时），不是源加载失败——外层必须
     * 静默给空蜘蛛：不弹「PY加载失败」、不打失败日志。顶替的那一发会自己把蜘蛛
     * 建好，死掉的这一发除了这条提示不该留下任何痕迹（1.4.14 真机实证：列表
     * 正常出来、底部却弹 RuntimeException: InterruptedException）。
     */
    private static class SpiderLoadCancelled extends RuntimeException {
        SpiderLoadCancelled(Throwable cause) {
            super(cause);
        }
    }

    private static boolean isInterrupted(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof InterruptedException) return true;
        }
        return false;
    }

    /** 连续创建失败标记：次数随每次真失败 +1，成功／驱逐／全清时整条摘除（＝清零）。 */
    private static class FailMark {
        final AtomicInteger count = new AtomicInteger();
        volatile long lastFailMs;
    }

    /**
     * 静默路径退避窗（毫秒），随连续失败次数涨、30 分钟封顶：
     * 第 1 次失败 5 分钟——先短，一次网络抖动不该把源在后台晾太久；
     * 第 2 次 15 分钟；第 3 次起 30 分钟——反复撞不活的源（死域名扫描型
     * init 一撞就是满额超时）必须把后台重试拉到半小时一级，堵住每趟预热
     * 重烧一遍。封顶保证源恢复后后台最迟半小时自动复活；且前台点开不受
     * 退避影响、成功即清零，实际恢复感知远快于窗长。
     */
    private static long backoffWindowMs(int failCount) {
        if (failCount <= 1) return 5 * 60 * 1000L;
        if (failCount == 2) return 15 * 60 * 1000L;
        return 30 * 60 * 1000L;
    }

    /** 记一次真失败（被取消不算，见 createSpider 的分类）。前台后台共用同一本账。 */
    private void noteLoadFailure(String key) {
        if (key == null) return;
        FailMark mark = failMarks.computeIfAbsent(key, k -> new FailMark());
        int n = mark.count.incrementAndGet();
        mark.lastFailMs = System.currentTimeMillis();
        SpiderDebug.log("pyloader", "load failure noted key=%s consecutive=%d backoffMs=%d", key, n, backoffWindowMs(n));
    }

    /** 该 key 是否还在退避窗内（只给静默路径查；前台 getSpider 永不调用）。 */
    private boolean isBackedOff(String key) {
        if (key == null) return false;
        FailMark mark = failMarks.get(key);
        if (mark == null) return false;
        return System.currentTimeMillis() - mark.lastFailMs < backoffWindowMs(mark.count.get());
    }

    public PyLoader() {
        spiders = new ConcurrentHashMap<>();
        fileStamps = new ConcurrentHashMap<>();
        failMarks = new ConcurrentHashMap<>();
        pathKeys = new ConcurrentHashMap<>();
        origins = new ConcurrentHashMap<>();
        loader = new Loader();
    }

    public void clear() {
        clear("unspecified");
    }

    /** 全清（配蜘蛛失效口径）：记原因＋清掉的只数，真机重建频率靠这条日志看。\n     * 依赖缓存同步清（只清 download 记名的依赖文件，主脚本不动）：全清是重置动作，\n     * 下次 init 重下依赖，顺带捡回上游更新与同长换内容这种比对漏网；单删插件不走这里\n     * （依赖按名多源共用，单删时不知道哪些依赖只属于被删插件）。 */
    public void clear(String reason) {
        int count = spiders.size();
        SpiderDebug.log("pyloader", "clear reason=%s spiders=%d keys=%s", reason, count, spiders.keySet());
        spiders.values().forEach(Spider::destroy);
        spiders.clear();
        createdKeys.clear();
        failMarks.clear();
        fileStamps.clear();
        pathKeys.clear();
        origins.clear();
        recent = null;
        try {
            com.fongmi.chaquo.Spider.clearDepCache();
        } catch (Throwable ignored) {
        }
    }

    public void remove(String key) {
        remove(key, "unspecified");
    }

    /** 单只驱逐：记原因＋key，真机重建频率靠这条日志看。
     * 顺序：先从表里摘掉并先清 recent（代理 recent 路不再路由到将死的蜘蛛），
     * 再 destroy；同时清该源解析缓存，删源后旧解析结果不许再命中。 */
    public void remove(String key, String reason) {
        Spider spider = spiders.remove(key);
        createdKeys.remove(key);
        if (key != null) origins.remove(key);
        if (key != null) failMarks.remove(key);
        fileStamps.remove(key);
        removeKeyFromPaths(key);
        if (key != null && key.equals(recent)) recent = null;
        try {
            SiteApi.clearPlayerCache(key);
        } catch (Throwable ignored) {
        }
        if (spider != null) {
            try {
                spider.destroy();
            } catch (Throwable ignored) {}
        }
        SpiderDebug.log("pyloader", "remove key=%s reason=%s hadSpider=%s", key, reason, spider != null);
    }

    /** 一个源同一时间只对应一个脚本：先把该 key 从旧路径摘掉，再挂到新路径。 */
    private void recordPath(String key, String absolutePath) {
        if (key == null || absolutePath == null) return;
        removeKeyFromPaths(key);
        pathKeys.computeIfAbsent(absolutePath, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    private void removeKeyFromPaths(String key) {
        if (key == null) return;
        for (Set<String> keys : pathKeys.values()) keys.remove(key);
    }

    /** 某脚本文件对应的源 key 快照（监听／删插件单清用；无缓存返回空，不触发全清）。 */
    public List<String> keysForScript(String absolutePath) {
        if (absolutePath == null) return new ArrayList<>();
        Set<String> keys = pathKeys.get(absolutePath);
        return keys == null ? new ArrayList<>() : new ArrayList<>(keys);
    }

    /**
     * 按文件单清：只清该文件对应的蜘蛛＋首页缓存（删插件／覆盖插件后首页瞬间刷新口径），
     * 其它源的蜘蛛和首页缓存一律不动——目录监听／删插件／导入插件三路共用。
     */
    public void invalidateScript(String absolutePath) {
        if (absolutePath == null) return;
        for (String key : keysForScript(absolutePath)) {
            try {
                SiteApi.clearHomeCache(key);
            } catch (Throwable ignored) {
            }
            try {
                SiteApi.clearPlayerCache(key);
            } catch (Throwable ignored) {
            }
        }
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    /** 实例是否已销毁（chaquo 蜘蛛 destroy 后 obj 已关、再调只会撞 PyObject is closed）。 */
    private static boolean isDead(Spider spider) {
        return spider instanceof com.fongmi.chaquo.Spider
                && ((com.fongmi.chaquo.Spider) spider).isDestroyed();
    }

    /** 原因链里出现“已关闭对象”特征（与 BaseLoader seam 同口径）。 */
    private static boolean isClosedObject(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.toLowerCase(java.util.Locale.ROOT).contains("is closed")) return true;
        }
        return false;
    }

    /** 摘掉表里这只已死实例（只摘仍是本体的那只，不误伤并发重建的新实例）并确定性销毁。
     * 不走 remove()：remove 会清 recent 与来源，而调用方正要按来源原地重建。 */
    private void evictDead(String key, Spider spider) {
        if (key == null || spider == null) return;
        if (spiders.remove(key, spider)) {
            createdKeys.remove(key);
            try {
                spider.destroy();
            } catch (Throwable ignored) {
            }
            SpiderDebug.log("pyloader", "evict dead spider key=%s", key);
        }
    }

    /**
     * 按创建来源重建一只活蜘蛛（代理分发失效重取专用）：无来源记录／重建失败／
     * 建出来仍是死的，一律返回 null（调用方按原口径回空、Proxy 转 502）。
     * 走静默路径：代理跑在播放器请求线程上，不许把前台的失败 Toast 带进来；
     * 退避窗内的源（连续创建真失败）同样不硬撞，维持原口径。
     */
    private Spider revive(String key) {
        String[] origin = origins.get(key);
        if (origin == null) return null;
        Spider spider = getSpiderQuiet(key, origin[0], origin[1]);
        if (spider == null || spider instanceof SpiderNull || isDead(spider)) return null;
        return spider;
    }

    /**
     * 监听 py 插件目录：只清事件文件对应的单只蜘蛛＋该源首页缓存（删 py 后首页瞬间刷新口径），
     * 其它源的蜘蛛和首页缓存一律不动。
     * 旧写法是任意事件（包括下载中的 .tmp、编辑器临时文件、非 py 文件）都全清全部蜘蛛＋
     * 全部首页缓存——插件一多，重建总量随源数线性放大，就是“py 加载更慢了”的放大器。
     * 不加防抖：清操作本身幂等（蜘蛛已清掉再清一次是空操作），真正的重建只在下次使用时
     * 发生一次，同一文件的多次事件不会导致多次重建。
     */
    public synchronized void watchPlugins(File dir) {
        if (dir == null || watcher != null) return;
        watchDir = new File(dir.getAbsolutePath());
        try {
            watcher = new FileObserver(watchDir.getAbsolutePath(),
                    FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.MOVED_FROM
                            | FileObserver.DELETE | FileObserver.CREATE) {
                @Override
                public void onEvent(int event, String path) {
                    SpiderDebug.log("pyloader", "watch event=%s path=%s", watchEventName(event), path);
                    if (path == null || !path.toLowerCase().endsWith(".py")) return;
                    // 失效与首页刷新拆开：失效失败只记日志，不许连坐掉刷新事件
                    // （旧写法同 try 时失效一抛，刷新永不发出、旧蜘蛛继续被用且无提示）。
                    try {
                        invalidateScript(new File(watchDir, path).getAbsolutePath());
                    } catch (Throwable e) {
                        SpiderDebug.log("pyloader", "invalidate on watch event failed path=%s error=%s", path, e.getMessage());
                    }
                    try {
                        RefreshEvent.home();
                    } catch (Throwable e) {
                        SpiderDebug.log("pyloader", "home refresh on watch event failed path=%s error=%s", path, e.getMessage());
                    }
                }
            };
            watcher.startWatching();
        } catch (Throwable ignored) {}
    }

    private static String watchEventName(int event) {
        switch (event & FileObserver.ALL_EVENTS) {
            case FileObserver.CLOSE_WRITE: return "CLOSE_WRITE";
            case FileObserver.MOVED_TO: return "MOVED_TO";
            case FileObserver.MOVED_FROM: return "MOVED_FROM";
            case FileObserver.DELETE: return "DELETE";
            case FileObserver.CREATE: return "CREATE";
            default: return "event_" + event;
        }
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

    /**
     * 脚本文件戳（给首页结果内存缓存当 key 的一部分）：
     * 本地文件 → 「绝对路径#mtime*31+size」；文件已删 → 路径#missing（key 变化，旧缓存自然失效）；
     * 远程脚本 → api 串本身。
     */
    public static String scriptStamp(String api) {
        if (api == null) return "";
        if (api.startsWith("http")) return api;
        String resolved = api;
        if (api.startsWith("plugins/") && App.get() != null && App.get().getFilesDir() != null) {
            File f = new File(App.get().getFilesDir(), api);
            if (f.exists()) resolved = f.getAbsolutePath();
        }
        File f = new File(resolved);
        if (f.exists()) return resolved + "#" + (f.lastModified() * 31L + f.length());
        return resolved + "#missing";
    }

    /**
     * 健康检查用：与 getSpider 同逻辑，但加载失败时静默返回 SpiderNull，不弹 Toast
     *（批量检测时逐个 Toast 会刷屏；检测结果由调用方统一展示）。
     */
    public Spider getSpiderQuiet(String key, String api, String ext) {
        String resolvedApi = resolve(api);
        boolean localFile = !api.startsWith("http");
        if (localFile) {
            File f = new File(resolvedApi);
            if (!f.exists()) {
                remove(key, "file-missing");
                return new SpiderNull();
            }
            if (fileStamps.containsKey(key) && stampOf(f) != fileStamps.get(key)) {
                remove(key, "stamp-changed");
            }
        }
        if (isBackedOff(key)) {
            // 后台退避：该源连续创建失败且还在退避窗内（窗长见 backoffWindowMs），
            // 静默路径直接跳过，不再每趟预热/检测都重撞一次满额超时（慢 init 源
            // 一撞就是 60 秒起步、还留僵尸扫描）。只拦静默路径：前台 getSpider
            // 不查退避，用户亲手点开永远真试、成功即清零。前面的 stamp-changed
            // 分支经 remove 已清标记——脚本被替换过照样立即重试，不被旧账拖住。
            SpiderDebug.log("pyloader", "quiet skip backoff key=%s", key);
            return new SpiderNull();
        }
        origins.put(key, new String[]{api, ext});
        Spider stale = spiders.get(key);
        if (stale != null && isDead(stale)) evictDead(key, stale);
        try {
            return spiders.computeIfAbsent(key, k -> createSpider(k, resolvedApi, ext, localFile));
        } catch (SpiderLoadCancelled e) {
            // 被取消的一发：静默，与失败无关。
            return new SpiderNull();
        } catch (SpiderLoadFailed e) {
            // 失败不缓存（computeIfAbsent 在映射函数抛异常时不写入），下次调用重试。
            // 静默只是不弹 Toast，不是无痕：与 getSpider 同口径落强制日志
            //（key＋异常全文，分类 pyloader），后台路径（首页预热等）挂掉的源
            // 事后能从日志页查到是谁、什么错。
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String msg = "PY加载失败 [" + key + "]: " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
            SpiderDebug.forceLog("pyloader", "%s", msg);
            SpiderDebug.forceLog("pyloader", cause);
            return new SpiderNull();
        }
    }

    /**
     * 创建并初始化一只蜘蛛（computeIfAbsent 的映射函数体）。
     * 成功才写入缓存并标记 anySpider；失败抛 SpiderLoadFailed 穿透出去，
     * 外层负责提示并返回未缓存的 SpiderNull——一次失败不再毒化该源整个进程。
     * 计时分两段记（蜘蛛创建＝脚本取数＋编译加载，init＝依赖下载＋蜘蛛 init），
     * 真机分清重建成本里哪段是大头；另记设备 __pycache__ 存在性（执行文件一律
     * 落在 Path.py() 缓存目录，pyc 是否命中看它）。
     */
    private Spider createSpider(String key, String api, String ext, boolean localFile) {
        long spiderStart = System.currentTimeMillis();
        Spider spider;
        try {
            spider = loader.spider(api);
        } catch (Throwable t) {
            if (isInterrupted(t)) {
                SpiderDebug.log("pyloader", "create cancelled key=%s cost=%dms", key, System.currentTimeMillis() - spiderStart);
                throw new SpiderLoadCancelled(t);
            }
            SpiderDebug.log("pyloader", "create failed key=%s cost=%dms error=%s", key, System.currentTimeMillis() - spiderStart, t);
            t.printStackTrace();
            noteLoadFailure(key);
            throw new SpiderLoadFailed(t);
        }
        long spiderCost = System.currentTimeMillis() - spiderStart;
        long initStart = System.currentTimeMillis();
        try {
            spider.siteKey = key;
            spider.init(App.get(), normalizeExt(ext));
            File f = new File(api);
            if (localFile && f.exists()) {
                fileStamps.put(key, stampOf(f));
                recordPath(key, f.getAbsolutePath());
            } else {
                fileStamps.remove(key);
            }
            anySpider.set(true);
            createdKeys.add(key);
            failMarks.remove(key);
            boolean pycache = false;
            try {
                pycache = new File(Path.py(), "__pycache__").exists();
            } catch (Throwable ignored) {
            }
            SpiderDebug.log("pyloader", "create key=%s spiderCost=%dms initCost=%dms pycache=%s", key, spiderCost, System.currentTimeMillis() - initStart, pycache);
            return spider;
        } catch (Throwable t) {
            boolean cancelled = isInterrupted(t);
            if (cancelled) {
                SpiderDebug.log("pyloader", "create cancelled key=%s spiderCost=%dms initCost=%dms", key, spiderCost, System.currentTimeMillis() - initStart);
            } else {
                SpiderDebug.log("pyloader", "create failed key=%s spiderCost=%dms initCost=%dms error=%s", key, spiderCost, System.currentTimeMillis() - initStart, t);
                t.printStackTrace();
            }
            // init 失败也要确定性销毁：蜘蛛已造出、既不入缓存也不能掉给 finalize（1.4.1 同款崩法）。
            // 被取消同理：半成品蜘蛛一样要销毁，不能因为「只是取消」就漏给 finalize。
            try {
                spider.destroy();
            } catch (Throwable ignored) {
            }
            if (cancelled) throw new SpiderLoadCancelled(t);
            noteLoadFailure(key);
            throw new SpiderLoadFailed(t);
        }
    }

    public Spider getSpider(String key, String api, String ext) {
        String resolvedApi = resolve(api);
        boolean localFile = !api.startsWith("http");
        if (localFile) {
            File f = new File(resolvedApi);
            if (!f.exists()) {
                // 脚本文件已不存在（被删/未导入）：静默给空蜘蛛，不走下面的加载失败弹窗。
                // 此前只有登记过时间戳的 key 才有这条静默路径；时间戳被目录监听清掉后，
                // 源还指着已删文件就会每次进首页都弹一遍“PY加载失败”的错误码。
                remove(key, "file-missing");
                return new SpiderNull();
            }
            if (fileStamps.containsKey(key) && stampOf(f) != fileStamps.get(key)) {
                // 文件被替换：丢掉旧缓存，重新加载
                remove(key, "stamp-changed");
            }
        }
        origins.put(key, new String[]{api, ext});
        // 已销毁实例不许再被 computeIfAbsent 原样递出（clear 扫除窗口内它还在表里，
        // 递出去后每个方法都只会静默回空／撞已关闭对象）：先摘再建。
        Spider stale = spiders.get(key);
        if (stale != null && isDead(stale)) evictDead(key, stale);
        try {
            return spiders.computeIfAbsent(key, k -> createSpider(k, resolvedApi, ext, localFile));
        } catch (SpiderLoadCancelled e) {
            // 本次加载被取消（顶替/页面销毁/外层超时打断了等待）：不是加载失败，
            // 不弹提示、不记源坏——顶替的那一发自会建好蜘蛛并出列表。
            SpiderDebug.log("pyloader", "getSpider cancelled key=%s", key);
            return new SpiderNull();
        } catch (SpiderLoadFailed e) {
            // 失败不缓存：下次打开重试。computeIfAbsent 在映射函数抛异常时不写入，
            // 此前这里返回 SpiderNull 会被写进缓存，一次失败毒化该源整个进程。
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String msg = "PY加载失败 [" + key + "]: " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
            // 失败必落强制日志（照 jar 先例）：弹窗一闪而过、事后无处查，
            // 这里把同一份 msg 与异常全文无视调试开关记下来，回头从日志页读回。
            SpiderDebug.forceLog("pyloader", "%s", msg);
            SpiderDebug.forceLog("pyloader", cause);
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> ToastUtil.show(App.get(), msg, android.widget.Toast.LENGTH_LONG));
            return new SpiderNull();
        }
    }

    private String normalizeExt(String ext) {
        String value = TextUtils.isEmpty(ext) ? "" : ext.trim();
        // Many live Python spiders treat ext as an option object and call .get().
        // TV-style configs commonly express an empty extension as [], which would
        // otherwise deserialize to a list and crash those spiders during init.
        return "[]".equals(value) ? "{}" : ext;
    }


    /**
     * 代理分发（do=py 唯一入口）：分发前先校验实例存活——表里是已销毁实例
     * （重载／清表扫除窗口／世代切换的残留）时，不许把请求递给死对象等它撞
     * PyObject is closed 后静默回空（2026-11-01 真机实证：蜘蛛本体健康、
     * playerContent 刚成功，代理却拿到已关闭对象、localProxy 根本没执行，
     * 播放器只看到 null_or_empty）。死实例先摘除、按创建来源重建当前活蜘蛛
     * 再分发；调用途中撞已关闭／途中被销毁的同样摘+重建+重试一次（封顶一
     * 次）；来源缺失或重建不出来才回 null，维持 Proxy 转 502 的原口径。
     */
    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        String key = recent;
        Spider spider = spiders.get(key);
        if (spider == null || isDead(spider)) {
            if (spider != null) evictDead(key, spider);
            spider = revive(key);
            if (spider == null) return null;
        }
        try {
            Object[] rs = spider.proxy(params);
            if (rs == null && isDead(spider)) {
                // 调用途中被销毁：chaquo 各入口只静默回 null，看不出是真无结果
                // 还是实例已死——实例既死就换活的重试一次。
                evictDead(key, spider);
                Spider fresh = revive(key);
                if (fresh != null) return fresh.proxy(params);
            }
            return rs;
        } catch (Exception e) {
            if (!isClosedObject(e)) throw e;
            SpiderDebug.log("proxy", "py proxy hit closed object key=%s, revive and retry once: %s", key, e);
            evictDead(key, spider);
            Spider fresh = revive(key);
            if (fresh == null) return null;
            return fresh.proxy(params);
        }
    }
}
