package com.fongmi.android.tv.api;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.clash.MihomoManager;
import com.fongmi.android.tv.clash.SourceProxyPolicy;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Util;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 首页预热：App 冷启动、主页首屏加载发起后，后台把所有 py/js 点播蜘蛛源的首页
 * 提前跑一遍写进预热缓存，用户切任何源都是秒出。
 *
 * 独立通道（与前台首页加载互不打架）：
 * - 不走前台首页加载的批次作废体系：不用 spiderHomePool、
 *   不登记 homeLoadFutures、不读不写 homeLoadGen；前台切源/进详情/搜索/刷新
 *   触发的任何作废都碰不到预热任务（射程外的独立池＋独立 future 表）。
 * - 绝不调用任何作废接口、不递增前台代次。
 * - 慢源专用耐心（两遍）：第一遍每源最多等 25 秒，超时不算失败、不记 doneThisBoot，
 *   挪到队尾第二遍；第二遍按账自适应（历史耗时×1.5＋10 秒余量，60–300 秒钳制；
 *   无账新源默认 120 秒）。前台 30/35 秒口径一字不动；只有真挂死才跳过。
 * - 驱动跨源并行：同一遍内同时推进 K=2 个源（池 4 线程、每源内部 home＋homeVideoContent
 *   两路各占 1 线程，正好占满；K 锁死 2，僵尸任务白占线程，池子留 headroom 不许加码）。
 *   逐源语义与旧串行版一字不变：pass1 超时仍 RETRY_LATER 挪队尾、pass2 才算真失败；
 *   两遍之间仍是 pass1 全完才开 pass2。
 * - 耗时记账：每个源每次成功加载的实际耗时持久化，下次启动慢源优先先热。
 * - 走节点源遇内核没就绪：不算失败、不记 doneThisBoot，挪到补跑队列稍后试一次；
 *   补跑时仍没就绪则放弃（不无限循环）；非节点源完全不受影响，队列不被堵。
 */
public final class HomePrewarm {


    /** 预热单源默认上限（秒）：无账可查的新源用它；慢但健康的源（带登记握手、第一次十几秒）正是预热要救的，不许套前台口径。 */
    static final long PREWARM_SOURCE_TIMEOUT_SECONDS = 120;
    /**
     * 第一遍单源等待上限（秒）：坏源卡住 worker 是预热慢的主因（真机实证：黄果占满 120 秒、
     * 双 worker 静默卡 82 秒，50 源 6 分钟只完 18 个）。第一遍只给 25 秒：超时不算失败、
     * 不记 doneThisBoot，挪到队尾第二遍再用自适应耐心慢慢等——快源先热完，用户体感先到位。
     */
    static final long PASS1_TIMEOUT_SECONDS = 25;
    /** 耐心上限钳制（毫秒）：按账自适应也不许超过它，避免真挂死的源拖住队列。 */
    private static final long TIMEOUT_MAX_MS = 300_000;
    private static final long TIMEOUT_MIN_MS = 60_000;
    /** 无账源的排序估计（毫秒）：未知源按中等偏慢排，不抢已知慢源的先机，也不垫底。 */
    private static final long UNKNOWN_COST_ESTIMATE_MS = 15_000;
    /** 耗时账本 key 前缀：prewarm_cost_<源key> → 最近一次成功加载耗时（毫秒）。 */
    private static final String COST_PREFIX = "prewarm_cost_";
    /** 前台切到在途预热源时的最长等待（秒）：接上在途那趟，不重跑。 */
    static final long PREWARM_JOIN_TIMEOUT_SECONDS = 25;
    /** 预热条目 TTL（毫秒）：用户慢慢逛时不轻易过期；被打开命中即晋升为普通首页缓存。 */
    static final long PREWARM_CACHE_TTL = 15 * 60 * 1000;
    /** 首屏先让路：主页首屏加载发起后延迟再开始，避免跟首屏抢 Python。 */
    private static final long START_DELAY_MS = 3000;
    /** 播放让路轮询间隔。 */
    private static final long PLAYBACK_WAIT_MS = 2000;
    /** 等配置就绪上限（毫秒）：冷启动 kick 常常打在配置装填完成之前，此时读到的源列表是空的，轮询等一会儿。 */
    private static final long CONFIG_WAIT_MS = 30_000;
    /** 等配置轮询间隔（毫秒）。 */
    private static final long CONFIG_POLL_MS = 1000;

    /**
     * 进程级门闩（状态机，杜绝并发两轮）：
     * IDLE → STARTING（kick 打响）→ RUNNING（配置就绪、队列真正开始消费，此刻门闩落下）→ DONE（跑完一轮）。
     * 超时/崩溃恢复/被中断导致一轮都没跑成时，复位回 IDLE，进程级机会不烧掉——
     * 后续 kick（如下拉刷新、配置就绪后再次 homeContent）还能重新驱动。
     */
    private static final int STATE_IDLE = 0, STATE_STARTING = 1, STATE_RUNNING = 2, STATE_DONE = 3;
    private static final AtomicInteger state = new AtomicInteger(STATE_IDLE);

    /** 播放进行中标记：SiteApi.playerContent 置 true；PlayerManager.stop()/release() 清 false。
     * 纯解析调用走 SiteApi.playerContentParseOnly，进出还原，不置不泄漏。 */
    public static volatile boolean playbackActive = false;

    private static final AtomicInteger threadNo = new AtomicInteger(0);

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name + "-" + threadNo.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /** 驱动：单线程，只负责拉起各遍、等两个 slot 收工、跑补跑队列；源的推进由 slots 并行做。 */
    private static final ExecutorService driver = Executors.newSingleThreadExecutor(daemon("prewarm-driver"));
    /**
     * 推进位（slot）：K=2，同时推进的源数。每遍内两个 slot 从同一个并发队列取源并行跑；
     * 每个源内部 home＋homeVideoContent 两路各占 1 个 workers 线程，2 源正好占满 4 线程。
     * K 锁死 2：①僵尸任务白占线程（驱动侧超时后 Python 侧停不下来，JNI 里 interrupt 无效），
     * 池子要留 headroom；②workers 只有 4 线程，加码 slot 只是让更多的 home/homeVideo 任务
     * 在池队列里排队，不加真吞吐。不要私自改 K 或扩池。
     */
    private static final int SLOT_COUNT = 2;
    private static final ExecutorService slots = Executors.newFixedThreadPool(SLOT_COUNT, daemon("prewarm-slot"));
    /**
     * 子任务：4 线程，只跑预热自己的 home/homeVideo 双路，不占前台 spiderHomePool。
     * 说明：每个源固定提交 home＋homeVideoContent 两路（并发跑），2 个 slot 各带 1 个源，
     * 4 路正好占满；僵尸任务（驱动侧超时后 Python 侧停不下来，JNI 里 interrupt 无效，
     * cancel(true) 只打断 Java 等待）继续占着线程直到脚本自己返回，线程多一点队列不
     * 容易被僵尸堵死。Chaquo 层没有 Java 全局锁（chaquo/Spider.java 无 synchronized/Lock）：
     * 单进程单解释器的 GIL 只串行 Python 字节码，网络 I/O（gate_patch 把 requests/urllib
     * 钩到 Java 侧 OkHttp，via=py）等待时 GIL 释放，多路 homeContent 在墙钟上是交叠跑的——
     * 加线程对 py 有效，不是摆设。
     */
    static final ExecutorService workers = Executors.newFixedThreadPool(4, daemon("prewarm-worker"));

    /** 在途预热（single-flight）：前台切到正在预热的源时复用/等待它，不另起一趟。 */
    private static final ConcurrentHashMap<String, CompletableFuture<String>> inflight = new ConcurrentHashMap<>();
    /** 本次启动已处理过的源：坏源只跳过一次，不重试打站点。 */
    private static final Set<String> doneThisBoot = ConcurrentHashMap.newKeySet();
    /**
     * 等节点补跑队列：内核没就绪时碰到的走节点源不算失败、不记 doneThisBoot，
     * 挪到这里稍后补跑一次；驱动单线程消费，随 kick 只进一次。
     * 两个 slot 会并发往里加：必须用并发 deque（原来单线程驱动时 ArrayDeque 够用）。
     */
    private static final Deque<Site> deferredProxy = new ConcurrentLinkedDeque<>();

    /** 预热缓存 key：末段均为 ext 哈希（与 SiteApi 首页缓存键同口径，可直接晋升）。
     * py：key|脚本戳|md5(ext)，与 SiteApi 首页缓存键逐字同式——登录改 ext 时旧预热
     * 键自然失效，晋升路不再把旧预热 JSON 写进新首页缓存键、绕过自动失效。
     * js：key|js|md5(ext)——js 无脚本戳、无普通首页缓存键，沿固定后缀补同一 ext 段，
     * 防预热直返（SiteApi 首页预热命中分支）绕过失效。 */
    static String cacheKey(Site site) {
        String key = site.getKey();
        String api = site.getApi();
        String extHash;
        try {
            extHash = Util.md5(site.getExt());
        } catch (Throwable t) {
            extHash = "";
        }
        boolean py = (key != null && key.startsWith("py_")) || (api != null && api.contains(".py"));
        if (py) {
            String stamp;
            try {
                stamp = com.fongmi.android.tv.api.loader.PyLoader.scriptStamp(api);
            } catch (Throwable t) {
                stamp = "nostamp";
            }
            return key + "|" + stamp + "|" + extHash;
        }
        return key + "|js|" + extHash;
    }

    /** 只预热点播 py/js 蜘蛛源：海阔规则、type4、push、直播一律不进；html 网页源无蜘蛛、不进（key 前缀一刀切，防「x.js.html」这类文件名里的 .js 子串误判）。 */
    static boolean isPrewarmable(String key, Site site) {
        if (site == null || key == null) return false;
        if (key.startsWith("html_")) return false;
        boolean spider = site.getType() == 3 || key.startsWith("py_");
        if (!spider) return false;
        String api = site.getApi();
        String a = api == null ? "" : api.toLowerCase();
        return key.startsWith("py_") || a.contains(".py") || a.contains(".js") || a.contains(".wv");
    }

    /**
     * 全量首页预热已关闭：保留入口供既有调用方使用，但不启动队列，
     * 避免后台提前跑遍各源首页与前台加载争抢资源。
     */
    public static void kick() {
        com.github.catvod.crawler.SpiderDebug.log("prewarm", "kick ignored: full home prewarm disabled");
    }

    /**
     * 枚举用户插件文件（与 SourceScriptsDialog.listScripts 完全同口径）：
     * 目录 getFilesDir()/plugins/py（*.py）、plugins/js（*.js、*.wv）、plugins/html（*.html）；
     * py/js 拼 Site 与 VodFragment.onScriptSelected 完全同口径：key=py_/js_+去后缀文件名、
     * name=去后缀、api=文件绝对路径、type=3、ext=PyExtConfig.load（空则"{}"）、jar=""。
     * html 照 VodFragment 的 HTML 动态源定形：key=html_+去后缀、api=文件绝对路径、
     * homePage=file://+绝对路径、type=3、无 ext（PyExtConfig 不适用）。
     * 有些用户的源全是插件文件，UI 现场拼临时 Site（setHome）从不进 VodConfig.sites——
     * 只枚举 getSites() 的话，预热对这类用户结构性空跑。
     * 注：html 源无蜘蛛，{@link #isPrewarmable} 对它恒 false——收进来只为枚举全套
     * （队列去重与计数口径与 UI 现场拼的 Site 同 key），不会被当蜘蛛预热。
     */
    private static List<Site> collectPluginSites() {
        List<Site> sites = new ArrayList<>();
        try {
            java.io.File filesDir = App.get().getFilesDir();
            collectPluginDir(sites, new java.io.File(filesDir, "plugins/py"), ".py", true);
            collectPluginDir(sites, new java.io.File(filesDir, "plugins/js"), ".js", false);
            collectHtmlPluginDir(sites, new java.io.File(filesDir, "plugins/html"));
        } catch (Throwable ignored) {
        }
        return sites;
    }

    /** HTML 插件目录枚举：网页源形态（homePage 网页首页形），与 py/js 的蜘蛛形态分开拼。 */
    private static void collectHtmlPluginDir(List<Site> out, java.io.File dir) {
        try {
            if (dir == null || !dir.exists() || !dir.isDirectory()) return;
            java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".html"));
            if (files == null) return;
            for (java.io.File f : files) {
                try {
                    String name = f.getName();
                    String baseName = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                    Site site = new Site();
                    site.setKey("html_" + baseName);
                    site.setName(baseName);
                    site.setApi(f.getAbsolutePath());
                    site.setHomePage("file://" + f.getAbsolutePath());
                    // 与 VodFragment 打开路同口径：导入 HTML 源带框打开（chromeMode=normal），不落 edge 裸页。
                    site.setChromeMode(com.fongmi.android.tv.web.WebHomeChrome.NORMAL);
                    site.setType(3);
                    site.setJar("");
                    out.add(site);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void collectPluginDir(List<Site> out, java.io.File dir, String ext, boolean isPy) {
        try {
            if (dir == null || !dir.exists() || !dir.isDirectory()) return;
            java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(ext) || (".js".equals(ext) && name.endsWith(".wv")));
            if (files == null) return;
            String prefix = isPy ? "py_" : "js_";
            for (java.io.File f : files) {
                try {
                    String name = f.getName();
                    String baseName = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                    Site site = new Site();
                    site.setKey(prefix + baseName);
                    site.setName(baseName);
                    site.setApi(f.getAbsolutePath());
                    site.setType(3);
                    String savedExt = com.fongmi.android.tv.utils.PyExtConfig.load(App.get(), prefix + baseName);
                    site.setExt(savedExt == null || savedExt.isEmpty() ? "{}" : savedExt);
                    site.setJar("");
                    out.add(site);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 耗时记账：只读/写一个小表（SharedPreferences，apply 异步落盘），不进加载主链。
     * 成功才记账：失败/超时的那趟不记，避免把"挂死"写成"慢"、下次给更大的耐心。
     */
    static void recordCost(String siteKey, long costMs) {
        if (siteKey == null || costMs <= 0) return;
        try {
            Prefers.put(COST_PREFIX + siteKey, costMs);
        } catch (Throwable ignored) {
        }
    }

    /** 最近一次成功耗时（毫秒）；无账返回 -1。 */
    static long costOf(String siteKey) {
        if (siteKey == null) return -1;
        try {
            return Prefers.getLong(COST_PREFIX + siteKey, -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 单源耐心：历史耗时 ×1.5 ＋ 10 秒余量，钳制在 60–300 秒；无账用默认 120 秒。 */
    static long timeoutFor(String siteKey) {
        long h = costOf(siteKey);
        if (h <= 0) return PREWARM_SOURCE_TIMEOUT_SECONDS;
        long ms = (long) (h * 1.5) + 10_000;
        if (ms < TIMEOUT_MIN_MS) ms = TIMEOUT_MIN_MS;
        if (ms > TIMEOUT_MAX_MS) ms = TIMEOUT_MAX_MS;
        return ms / 1000;
    }

    /** 走节点口径：与 ClashGate/网络层同口径——kind 走 SiteApi.proxyKindOf，id 用源 key。 */
    static boolean needsProxyWarm(Site site) {
        try {
            if (site == null || site.getKey() == null) return false;
            String kind = SiteApi.proxyKindOf(site);
            if (kind == null) return false;
            return SourceProxyPolicy.shouldProxy(App.get(), kind, site.getKey());
        } catch (Throwable t) {
            return false; // 判不出就当直连：fail-open，绝不挡预热
        }
    }

    /** 走节点的源碰上内核没就绪：现在跑会静默直连，挪到补跑队列，稍后再试。 */
    static boolean proxyNotReady(Site site) {
        if (!needsProxyWarm(site)) return false;
        try {
            return !MihomoManager.get().isRunning();
        } catch (Throwable t) {
            return false; // 判不出就绪态也当可跑：不许堵队列
        }
    }

    /** 播放让路：用户开始播放/解析时暂停推进，播完再续（只等，不取消）；返回 false 表示被中断，调用方应退出队列。 */
    private static boolean waitPlayback() {
        while (playbackActive) {
            try {
                Thread.sleep(PLAYBACK_WAIT_MS);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return true;
    }

    private static void runQueue() {
        try {
            Thread.sleep(START_DELAY_MS);
        } catch (InterruptedException e) {
            resetIdle();
            return;
        }

        // 队列源 = 插件文件源 ∪ VodConfig 配置源。
        // 插件文件非空就直接开跑、不等配置：用户 UI 现场拼临时 Site（setHome）从不进
        // VodConfig.sites，等配置的轮询对此类用户永远等不到——这就是之前整进程零日志的根。
        List<Site> pluginSites = collectPluginSites();
        List<Site> configSites;
        if (pluginSites.isEmpty()) {
            // 两者皆空才保留原有轮询兜底（给纯配置用户）。
            configSites = waitForSites();
            if (configSites == null || configSites.isEmpty()) {
                com.github.catvod.crawler.SpiderDebug.log("prewarm", "giveup (config not ready after 30s)");
                resetIdle();
                return;
            }
        } else {
            List<Site> cs = null;
            try {
                cs = VodConfig.get().getSites();
            } catch (Throwable e) {
                com.github.catvod.crawler.SpiderDebug.log("prewarm", "config sites read failed, continue with plugin sites only: " + e.getMessage());
            }
            configSites = cs == null ? new ArrayList<Site>() : cs;
        }
        // 按 key 去重、插件文件优先（与前台手选覆盖行为一致）。
        List<Site> raw = new ArrayList<>(pluginSites.size() + configSites.size());
        java.util.Set<String> seen = new HashSet<>();
        for (Site s : pluginSites) {
            if (s != null && s.getKey() != null && seen.add(s.getKey())) raw.add(s);
        }
        for (Site s : configSites) {
            if (s != null && s.getKey() != null && seen.add(s.getKey())) raw.add(s);
        }
        if (raw.isEmpty()) {
            com.github.catvod.crawler.SpiderDebug.log("prewarm", "giveup (no warmable sites)");
            resetIdle();
            return;
        }
        // 快照一份再排：源列表是 VodConfig 的内部列表，原地 sort 会打乱用户界面的源顺序。
        List<Site> sites = new ArrayList<>(raw);
        // 配置就绪、队列真正开始消费：此刻门闩落下。
        state.compareAndSet(STATE_STARTING, STATE_RUNNING);
        final AtomicInteger warmed = new AtomicInteger(0), skipped = new AtomicInteger(0);
        final AtomicBoolean aborted = new AtomicBoolean(false); // 播放让路被中断时两遍都停（与旧串行版 waitPlayback false 即 return 语义一致）
        try {
            Site home = null;
            try {
                home = VodConfig.get().getHome();
            } catch (Throwable ignored) {
            }
            final Site homeSite = home;
            // 慢源优先：按账本耗时降序排，慢的先热（痛点先治），快的捎带就完；
            // 无账源按中等偏慢估计，不抢已知慢源的先机。
            try {
                sites.sort((a, b) -> Long.compare(sortCost(b), sortCost(a)));
            } catch (Throwable ignored) {
            }
            com.github.catvod.crawler.SpiderDebug.log("prewarm", "queue start: " + sites.size() + " sites");
            // 第一遍：快源先热完。每源最多等 25 秒——超时的不算失败、不记 doneThisBoot，
            // 挪到 pass2 队尾；只有秒挂（抛异常/空结果）才当场记 doneThisBoot 跳过。
            Deque<Site> pass2 = new ConcurrentLinkedDeque<>();
            com.github.catvod.crawler.SpiderDebug.log("prewarm", "pass1 start: " + sites.size() + " sites");
            drivePass(new ConcurrentLinkedDeque<>(sites), homeSite, pass2, 1, warmed, skipped, aborted);
            if (aborted.get()) return;
            com.github.catvod.crawler.SpiderDebug.log("prewarm", "progress pass1 " + sites.size() + "/" + sites.size() + " (done)");
            // 第二遍：只跑第一遍超时的源，用现行自适应耐心慢慢等。这里超时才算真失败记 doneThisBoot。
            // 两遍之间仍是 pass1 全完才开 pass2（drivePass 内部等两个 slot 收工才返回）。
            if (!pass2.isEmpty()) {
                com.github.catvod.crawler.SpiderDebug.log("prewarm", "pass2 start: " + pass2.size() + " sites");
                Deque<Site> noop = new ConcurrentLinkedDeque<>(); // pass=2 超时算真失败、返回 SKIPPED，RETRY_LATER 只在 pass1 出现
                drivePass(pass2, homeSite, noop, 2, warmed, skipped, aborted);
                if (!noop.isEmpty()) skipped.addAndGet(noop.size()); // 保险：真有漏网也只计跳过，不循环
                if (aborted.get()) return;
            }
            // 补跑一轮：只此一轮，不无限循环。
            Site retry;
            while ((retry = deferredProxy.poll()) != null) {
                try {
                    if (!wantSite(retry, home)) continue; // 中途前台已暖或状态变化就跳过
                    if (!waitPlayback()) return;
                    if (proxyNotReady(retry)) {
                        // 补跑时内核仍没就绪：放弃本次启动，不算失败，记 done 防止再被拾起。
                        doneThisBoot.add(retry.getKey());
                        com.github.catvod.crawler.SpiderDebug.log("prewarm", "giveup (kernel not ready): " + safeKey(retry));
                        continue;
                    }
                    if (prewarmOne(retry, timeoutFor(retry.getKey()), 0) == WARMED) warmed.incrementAndGet(); else skipped.incrementAndGet(); // 内核已就绪：正常一趟；就绪时进的主循环那一趟没被推迟，不会跑第二趟
                } catch (Throwable t) {
                    com.github.catvod.crawler.SpiderDebug.log("prewarm", "retry item failed: " + safeKey(retry));
                }
            }
        } finally {
            // 跑过一轮（正常结束或中途被播放让路打断都算）：门闩落下，进程级机会消耗。
            state.set(STATE_DONE);
            com.github.catvod.crawler.SpiderDebug.log("prewarm", "queue done: warmed=" + warmed.get() + " skipped=" + skipped.get());
        }
    }

    /**
     * 复位门闩：一轮都没跑成（被中断/崩溃恢复/配置等不到），进程级机会不烧掉，
     * 后续 kick（如下拉刷新、配置就绪后再次 homeContent）还能重新驱动。
     */
    private static void resetIdle() {
        state.compareAndSet(STATE_STARTING, STATE_IDLE);
    }

    /**
     * 等配置就绪：冷启动 kick 常常打在配置装填完成之前，此时 getSites() 返回空列表。
     * 短间隔轮询、上限约 30 秒；等的过程中只是 sleep、零资源消耗，不挡播放；
     * 等不到返回 null，调用方放弃并复位门闩。
     */
    private static List<Site> waitForSites() {
        long deadline = System.currentTimeMillis() + CONFIG_WAIT_MS;
        boolean logged = false;
        while (System.currentTimeMillis() < deadline) {
            List<Site> sites = null;
            try {
                sites = VodConfig.get().getSites();
            } catch (Throwable ignored) {
            }
            if (sites != null && !sites.isEmpty()) return sites;
            if (!logged) {
                com.github.catvod.crawler.SpiderDebug.log("prewarm", "waiting for config (sites empty)");
                logged = true;
            }
            try {
                Thread.sleep(CONFIG_POLL_MS);
            } catch (InterruptedException e) {
                return null;
            }
        }
        return null;
    }

    private static boolean wantSite(Site site, Site home) {
        if (site == null) return false;
        String key = site.getKey();
        if (key == null) return false;
        if (home != null && key.equals(home.getKey())) return false; // 当前源：它自己正在加载
        if (!isPrewarmable(key, site)) return false;
        if (doneThisBoot.contains(key)) return false;                 // 本次启动只试一次
        try {
            if (SiteApi.homeCacheHasFresh(key)) return false;         // 已经暖了，不重跑
        } catch (Throwable ignored) {
        }
        return true;
    }

    private static long sortCost(Site site) {
        long c = costOf(site == null ? null : site.getKey());
        return c > 0 ? c : UNKNOWN_COST_ESTIMATE_MS;
    }

    /**
     * 一遍内多源并行推进：K=2 个 slot 从同一个并发队列取源并行跑，两个 slot 都收工才返回。
     * 逐源语义与旧串行版一字不变：wantSite 门控、播放让路、节点 defer、pass1 超时→retryOut、
     * pass1 固定 25 秒 / pass2 自适应耐心、坏源只跳过不连累。进度日志每完成 5 个源打一行。
     */
    private static void drivePass(Deque<Site> queue, Site home, Deque<Site> retryOut, int pass,
                                 AtomicInteger warmed, AtomicInteger skipped, AtomicBoolean aborted) {
        final int total = queue.size();
        final AtomicInteger done = new AtomicInteger(0);
        final String passTag = pass == 1 ? "pass1" : "pass2";
        List<Future<?>> jobs = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            jobs.add(slots.submit(() -> {
                Site site;
                while ((site = queue.poll()) != null) {
                    int d = done.incrementAndGet();
                    try {
                        if (!wantSite(site, home)) continue;
                        // 播放让路：每源开跑前仍生效；用户开始播放/解析时暂停推进，播完再续。
                        if (!waitPlayback()) {
                            aborted.set(true);
                            return;
                        }
                        if (proxyNotReady(site)) {
                            // 走节点但内核没就绪：不算失败、不记 doneThisBoot，挪到补跑队列稍后一次。
                            deferredProxy.add(site);
                            com.github.catvod.crawler.SpiderDebug.log("prewarm", passTag + " defer (kernel not ready): " + safeKey(site));
                            continue;
                        }
                        long timeout = pass == 1 ? PASS1_TIMEOUT_SECONDS : timeoutFor(site.getKey());
                        int r = prewarmOne(site, timeout, pass);
                        if (r == WARMED) warmed.incrementAndGet();
                        else if (r == RETRY_LATER) retryOut.add(site);
                        else skipped.incrementAndGet();
                        if (d % 5 == 0)
                            com.github.catvod.crawler.SpiderDebug.log("prewarm", "progress " + passTag + " " + d + "/" + total);
                    } catch (Throwable t) {
                        // 队列尽力而为：单个条目异常绝不中断全队列。
                        com.github.catvod.crawler.SpiderDebug.log("prewarm", passTag + " item failed: " + safeKey(site));
                    }
                }
            }));
        }
        for (Future<?> j : jobs) {
            try {
                j.get();
            } catch (Throwable ignored) {
            }
        }
    }

    /** prewarmOne 返回值：暖好 / 跳过（失败或空） / 第一遍超时→第二遍队尾。 */
    private static final int WARMED = 1, SKIPPED = 0, RETRY_LATER = -1;

    /**
     * 跑一个源：成功才记账（耗时进 prewarm_cost_*）。
     * pass=1（第一遍）：超时不算失败、不记 doneThisBoot，返回 RETRY_LATER 由调用方挪到队尾第二遍。
     * pass=2（第二遍）/其它：超时也算真失败记 doneThisBoot。其它异常/空结果各遍都当场记 doneThisBoot 跳过。
     */
    private static int prewarmOne(Site site, long timeoutSeconds, int pass) {
        String key = site.getKey();
        String tag = pass == 1 ? "pass1" : pass == 2 ? "pass2" : "proxy";
        boolean firstPass = pass == 1;
        CompletableFuture<String> f = new CompletableFuture<>();
        if (inflight.putIfAbsent(key, f) != null) return SKIPPED; // 已有在途：不重复
        try {
            long start = System.currentTimeMillis();
            String json = SiteApi.prewarmSpiderHome(site, timeoutSeconds); // 抛异常/超时/空都算失败
            long cost = System.currentTimeMillis() - start;
            if (json != null && !json.isEmpty()) {
                SiteApi.putPrewarmCache(cacheKey(site), json);
                recordCost(key, cost); // 成功才记账
                com.github.catvod.crawler.SpiderDebug.log("prewarm", tag + " warmed: " + key + " cost=" + cost + "ms");
                f.complete(json);
                doneThisBoot.add(key); // 成功或空结果都算试过，本次启动不再重试
                return WARMED;
            }
            com.github.catvod.crawler.SpiderDebug.log("prewarm", tag + " skip (empty): " + key);
            f.complete(json);
            doneThisBoot.add(key);
            return SKIPPED;
        } catch (java.util.concurrent.TimeoutException te) {
            // 注意：驱动侧超时后 Python 侧的任务停不下来（JNI 里 interrupt 无效），
            // 僵尸继续占着 worker 线程直到脚本自己返回——所以 worker 提到 4 线程留 headroom。
            com.github.catvod.crawler.SpiderDebug.log("prewarm", tag + " timeout: " + key + (firstPass ? " -> pass2" : ""));
            f.completeExceptionally(te);
            if (!firstPass) doneThisBoot.add(key); // 第二遍/补跑超时才算真失败
            return firstPass ? RETRY_LATER : SKIPPED;
        } catch (Throwable t) {
            // 坏源只跳过：记一笔日志，继续下一个，绝不卡死队列；失败不记账。
            com.github.catvod.crawler.SpiderDebug.log("prewarm", tag + " skip (fail): " + key + " " + t.getClass().getSimpleName());
            f.completeExceptionally(t);
            doneThisBoot.add(key);
            return SKIPPED;
        } finally {
            inflight.remove(key, f);
        }
    }

    /** 前台 single-flight：窥视在途预热（不移除，预热完成后自己清）。 */
    static CompletableFuture<String> pollInflight(String key) {
        return key == null ? null : inflight.get(key);
    }

    private static String safeKey(Site site) {
        try {
            return site == null ? "null" : String.valueOf(site.getKey());
        } catch (Throwable t) {
            return "?";
        }
    }
}
