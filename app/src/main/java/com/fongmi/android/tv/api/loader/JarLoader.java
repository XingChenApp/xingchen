package com.fongmi.android.tv.api.loader;

import android.content.Context;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import dalvik.system.DexClassLoader;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class JarLoader {

    private static final int SPIDER_INIT_ATTEMPTS = 2;
    /** 无 md5 时本地已下 jar 的复用有效期：24 小时，过期才重下。 */
    private static final long JAR_CACHE_TTL_MS = 24L * 60 * 60 * 1000;
    private final ConcurrentHashMap<String, DexClassLoader> loaders;
    private final ConcurrentHashMap<String, ProxyMethod> methods;
    private final ConcurrentHashMap<String, Spider> spiders;
    private final ConcurrentHashMap<String, Object> locks;
    /** Proxy 类在场但加载/兼容层安装失败的 jar 键：proxy() 对这些键抛错收口，不许静默无响应。 */
    private final java.util.Set<String> proxyFailed = ConcurrentHashMap.newKeySet();
    private volatile String recent;

    public JarLoader() {
        loaders = new ConcurrentHashMap<>();
        methods = new ConcurrentHashMap<>();
        spiders = new ConcurrentHashMap<>();
        locks = new ConcurrentHashMap<>();
    }

    public void clear() {
        SpiderDebug.log("jar-loader", "clear loaders=%s spiders=%s methods=%s", loaders.size(), spiders.size(), methods.size());
        spiders.values().forEach(Spider::destroy);
        methods.values().forEach(ProxyMethod::close);
        loaders.clear();
        methods.clear();
        spiders.clear();
        locks.clear();
        proxyFailed.clear();
        recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
        SpiderDebug.log("jar-loader", "recent=%s", recent);
    }

    private void load(String key, File file) {
        long start = System.currentTimeMillis();
        if (Thread.interrupted()) {
            SpiderDebug.log("jar-loader", "load skip interrupted key=%s", key);
            return;
        }
        if (!Path.exists(file)) {
            SpiderDebug.log("jar-loader", "load skip missing key=%s file=%s", key, file);
            return;
        }
        if (!file.setReadOnly()) {
            SpiderDebug.log("jar-loader", "load skip readonly failed key=%s file=%s size=%s", key, file.getAbsolutePath(), file.length());
            return;
        }
        String cachePath = Path.jar().getAbsolutePath();
        SpiderDebug.log("jar-loader", "load start key=%s file=%s size=%s cache=%s", key, file.getAbsolutePath(), file.length(), cachePath);
        DexClassLoader loader = new CspDexClassLoader(file.getAbsolutePath(), cachePath, cachePath, App.get().getClassLoader());
        if (!invokeInit(key, loader, file)) {
            SpiderDebug.forceLog("jar-loader", "load aborted init failed key=%s", key);
            return;
        }
        if (!invokeNetworkCompat(key, loader)) {
            SpiderDebug.forceLog("jar-loader", "load aborted network inject failed key=%s", key);
            return;
        }
        invokeProxy(key, loader, file);
        loaders.put(key, loader);
        SpiderDebug.log("jar-loader", "load done key=%s cost=%sms", key, System.currentTimeMillis() - start);
    }

    private boolean invokeNetworkCompat(String key, DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.parser.merge.j0.b");
            Method method = clz.getMethod("b", okhttp3.OkHttpClient.class);
            OkHttpClient client = OkHttp.client().newBuilder().addInterceptor(chain -> {
                Request request = chain.request();
                boolean iframe = request.url().host().endsWith("youtube.com") && request.url().encodedPath().contains("iframe_api");
                long start = System.currentTimeMillis();
                try {
                    Response response = chain.proceed(request);
                    if (iframe) SpiderDebug.log("youtube-iframe", "response code=%s final=%s type=%s encoding=%s length=%s cost=%sms", response.code(), response.request().url(), response.header("Content-Type"), response.header("Content-Encoding"), response.header("Content-Length"), System.currentTimeMillis() - start);
                    return response;
                } catch (Throwable e) {
                    if (iframe) SpiderDebug.log("youtube-iframe", "failed url=%s cost=%sms error=%s", request.url(), System.currentTimeMillis() - start, error(e));
                    throw e;
                }
            }).build();
            Class<?> environment = loader.loadClass("com.github.catvod.parser.merge.j0.c");
            environment.getConstructor(okhttp3.OkHttpClient.class).newInstance(client);
            SpiderDebug.log("jar-loader", "network client injected key=%s", key);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Optional compatibility hook used only by jars which expose it.
            return true;
        } catch (Throwable e) {
            // 钩子在场但注入失败：该 jar 的网络能力已残缺，不许照常登记成可用源。
            // 回 false 让 load() 中止登记，下游以 loader missing 失败态收口。
            SpiderDebug.forceLog("jar-loader", "network client inject failed key=%s error=%s", key, error(e));
            SpiderDebug.forceLog("jar-loader", e);
            return false;
        }
        final Class<?> clz;
        final Object fetcher;
        try {
            clz = loader.loadClass("com.github.catvod.parser.merge.j0.b");
            Class<?> provider = loader.loadClass("com.github.catvod.parser.merge.P1.P");
            fetcher = provider.getMethod("b").invoke(null);
        } catch (Throwable e) {
            SpiderDebug.log("youtube-iframe", "probe skipped key=%s error=%s", key, error(e));
            return true;
        }
        SpiderDebug.log("youtube-iframe", "probe async dispatched key=%s", key);
        Task.submit(() -> probeYoutube(key, clz, fetcher));
        return true;
    }

    /** 探针实拨只做诊断、结果无人消费，移到后台线程，不再串在进源主路径上同步等。 */
    private void probeYoutube(String key, Class<?> clz, Object fetcher) {
        try {
            Object response = clz.getMethod("a", String.class).invoke(fetcher, "https://www.youtube.com/iframe_api");
            Object raw = response.getClass().getMethod("a").invoke(response);
            String body = raw instanceof String ? (String) raw : null;
            SpiderDebug.log("youtube-iframe", "probe success chars=%s", body == null ? -1 : body.length());
        } catch (Throwable e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            SpiderDebug.log("youtube-iframe", "probe failed error=%s", cause.getClass().getName() + ":" + cause.getMessage());
            SpiderDebug.log("youtube-iframe", cause);
        }
    }

    private boolean invokeInit(String key, DexClassLoader loader, File file) {
        long start = System.currentTimeMillis();
        try {
            SpiderDebug.log("jar-loader", "jar init start key=%s", key);
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            Method method = clz.getMethod("init", Context.class);
            // 只有潇洒包（本地仓 pkg3，内容键逐字对上）递宿主身份门面过它的包名白名单；
            // 其余 jar 一律仍递真 App，行为零变化（账 pkg3-whitelist-chain-20261031）。
            Context context = App.get();
            if (Pkg3HostFacade.matches(file)) {
                context = Pkg3HostFacade.create();
                SpiderDebug.log("jar-loader", "pkg3 host facade applied key=%s", key);
            }
            method.invoke(clz, context);
            SpiderDebug.log("jar-loader", "jar init done key=%s cost=%sms", key, System.currentTimeMillis() - start);
            return true;
        } catch (ClassNotFoundException e) {
            // jar 本就没有 Init 类＝没有初始化步，属合法缺失，不按失败中止。
            SpiderDebug.log("jar-loader", "jar init absent key=%s", key);
            return true;
        } catch (Throwable e) {
            // jar 装载初始化失败走强制通道：调试开关没开也必须落盘（断点判别靠它）。
            // 回 false 让 load() 中止登记：不再把初始化缺失的 jar 当可用源递出去，
            // 下游 createJarSpider 会以 loader missing 强制留痕并回 SpiderNull。
            SpiderDebug.forceLog("jar-loader", "jar init error key=%s cost=%sms error=%s", key, System.currentTimeMillis() - start, error(e));
            SpiderDebug.forceLog("jar-loader", e);
            return false;
        }
    }

    private void invokeProxy(String key, DexClassLoader loader, File file) {
        long start = System.currentTimeMillis();
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            Method method = clz.getMethod("proxy", Map.class);
            PanProxyCompat compat = null;
            try {
                compat = PanProxyCompat.install(file, loader, message -> SpiderDebug.log("pan-proxy", message));
                if (compat != null) {
                    SpiderDebug.log("jar-loader", "pan proxy lifecycle compat enabled key=%s sha256=%s", key, PanProxyCompat.JAR_SHA256);
                }
            } catch (Exception e) {
                // install 只对哈希命中的那个 jar 抛错（不命中直接回 null）：
                // 命中 jar 的兼容层装不上还照常以 compat=null 注册，等于让该 jar
                // 的代理在生命周期场景静默异常。改为不注册并记入 proxyFailed，
                // proxy() 对该 key 走既有抛错出口，不再静默无响应。
                SpiderDebug.forceLog("jar-loader", "pan proxy lifecycle compat install failed key=%s error=%s", key, error(e));
                proxyFailed.add(key);
                return;
            }
            methods.put(key, new ProxyMethod(method, compat));
            proxyFailed.remove(key);
            SpiderDebug.log("jar-loader", "proxy method ready key=%s cost=%sms", key, System.currentTimeMillis() - start);
        } catch (ClassNotFoundException e) {
            // jar 本就没有 Proxy 类是合法缺失（多数 jar 不提供代理），不记失败。
            SpiderDebug.log("jar-loader", "proxy method absent key=%s cost=%sms", key, System.currentTimeMillis() - start);
        } catch (Throwable e) {
            // Proxy 类在但加载/取法失败：记入 proxyFailed，proxy() 对该 key
            // 抛错收口，不再与「没有任何代理处理」同形地静默回 null。
            SpiderDebug.forceLog("jar-loader", "proxy method load failed key=%s cost=%sms error=%s", key, System.currentTimeMillis() - start, error(e));
            SpiderDebug.forceLog("jar-loader", e);
            proxyFailed.add(key);
        }
    }

    public void parseJar(String key, String jar) {
        if (loaders.containsKey(key)) return;
        if (jar.startsWith("assets")) jar = UrlUtil.convert(jar);
        // clan://localhost/<路径> 与 file://<路径> 同义（本地仓包的订阅形态）：
        // jar 引用不走 UrlUtil.convert，在此归一成 file 形态，落既有 file 分支直读存储文件。
        if (jar.startsWith("clan://")) jar = "file://" + jar.substring(7).replaceFirst("(?i)^localhost/", "");
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            if (loaders.containsKey(key)) return;
            String[] texts = jar.split(";md5;");
            String md5 = texts.length > 1 ? texts[1].trim() : "";
            if (md5.startsWith("http")) md5 = OkHttp.string(md5).trim();
            jar = texts[0];
            SpiderDebug.log("jar-loader", "parse start key=%s source=%s md5=%s", key, source(jar), !md5.isEmpty());
            if (!md5.isEmpty() && Util.equals(jar, md5)) {
                load(key, Path.jar(jar));
            } else if (jar.startsWith("http")) {
                File local = Path.jar(jar);
                // 默影视口径：配置声明了 md5 就以 md5 为准——能走到这里说明本地那份与声明对不上，必须重下；
                // 24 小时本地复用只许用在没声明 md5 的 jar 上，不许短路 md5 校验。
                if (md5.isEmpty() && shouldReuseLocalJar(local)) {
                    SpiderDebug.log("jar-loader", "parse reuse local key=%s file=%s age=%sh", key, local.getAbsolutePath(), (System.currentTimeMillis() - local.lastModified()) / 3600000);
                    load(key, local);
                } else {
                    if (!md5.isEmpty()) SpiderDebug.log("jar-loader", "parse redownload md5 mismatch key=%s file=%s", key, local.getAbsolutePath());
                    load(key, Download.createForJar(jar, local).get());
                }
            } else if (jar.startsWith("file")) {
                load(key, Path.local(jar));
            }
        }
    }

    /**
     * 无 md5 时复用本地已下 jar：24 小时内视为新鲜，直接装载；过期/不存在/空文件才重下。
     * 文件名已是 md5(url) 天然键。jar 换包不换 URL 的场景最多延迟一天生效。
     * 前置条件由调用方（parseJar）保证：仅在配置没声明 md5 时才查本方法；
     * 声明了 md5 的一律先走 Util.equals，对不上直接重下，不进本方法。
     */
    public static boolean shouldReuseLocalJar(File local) {
        return local != null && local.exists() && local.length() > 0
                && System.currentTimeMillis() - local.lastModified() < JAR_CACHE_TTL_MS;
    }

    public DexClassLoader dex(String jar) {
        try {
            String jaKey = Util.md5(jar);
            parseJar(jaKey, jar);
            return loaders.get(jaKey);
        } catch (Throwable e) {
            // dex 装载失败走强制通道落盘（printStackTrace 在日志页查不到根因）；
            // null 仍是既有失败值，下游蜘蛛据此走初始化失败态。
            SpiderDebug.forceLog("jar-loader", "dex load failed jar=%s error=%s", source(jar), error(e));
            SpiderDebug.forceLog("jar-loader", e);
            return null;
        }
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        String jaKey = Util.md5(jar);
        String spKey = jaKey + key;
        return getOrCreateSpider(spKey, () -> createJarSpider(key, api, ext, jar, jaKey));
    }

    private Spider createJarSpider(String key, String api, String ext, String jar, String jaKey) {
        long start = System.currentTimeMillis();
        try {
            if (Thread.currentThread().isInterrupted()) {
                SpiderDebug.log("jar-loader", "spider init skip interrupted site=%s api=%s jar=%s", key, api, jaKey);
                return new SpiderNull();
            }
            SpiderDebug.log("jar-loader", "spider init start site=%s api=%s jar=%s ext=%s", key, api, jaKey, ext == null ? 0 : ext.length());
            parseJar(jaKey, jar);
            DexClassLoader loader = loaders.get(jaKey);
            if (loader == null) {
                // jar 根本没装上也是蜘蛛初始化失败的一种，走强制通道留痕。
                SpiderDebug.forceLog("jar-loader", "spider init skip loader missing site=%s api=%s jar=%s cost=%sms", key, api, jaKey, System.currentTimeMillis() - start);
                return new SpiderNull();
            }
            Spider spider = (Spider) loader.loadClass("com.github.catvod.spider." + api.split("csp_")[1]).newInstance();
            spider.siteKey = key;
            spider.init(App.get(), ext);
            SpiderDebug.log("jar-loader", "spider init done site=%s api=%s jar=%s class=%s cost=%sms", key, api, jaKey, spider.getClass().getName(), System.currentTimeMillis() - start);
            return spider;
        } catch (Throwable e) {
            if (isInterrupted(e)) Thread.currentThread().interrupt();
            // 蜘蛛初始化失败走强制通道：调试开关没开也必须落盘（断点判别靠它）。
            SpiderDebug.forceLog("jar-loader", "spider init error site=%s api=%s jar=%s cost=%sms error=%s", key, api, jaKey, System.currentTimeMillis() - start, error(e));
            SpiderDebug.forceLog("jar-loader", e);
            e.printStackTrace();
            return new SpiderNull();
        }
    }

    Spider getOrCreateSpider(String key, Supplier<Spider> factory) {
        // A canceled request must stop immediately; a transient initialization failure
        // gets one retry and SpiderNull is never cached across requests.
        if (Thread.currentThread().isInterrupted()) return new SpiderNull();
        Spider cached = spiders.get(key);
        if (cached != null) return cached;
        Object lock = locks.computeIfAbsent("spider:" + key, ignored -> new Object());
        synchronized (lock) {
            if (Thread.currentThread().isInterrupted()) return new SpiderNull();
            cached = spiders.get(key);
            if (cached != null) return cached;
            Spider created = null;
            for (int attempt = 1; attempt <= SPIDER_INIT_ATTEMPTS; attempt++) {
                if (Thread.currentThread().isInterrupted()) break;
                created = factory.get();
                if (created != null && !(created instanceof SpiderNull)) {
                    spiders.put(key, created);
                    if (!Thread.currentThread().isInterrupted()) return created;
                    SpiderDebug.log("jar-loader", "spider init completed after cancel key=%s", key);
                    return new SpiderNull();
                }
                if (Thread.currentThread().isInterrupted() || attempt == SPIDER_INIT_ATTEMPTS) break;
                SpiderDebug.log("jar-loader", "spider init retry key=%s attempt=%s", key, attempt + 1);
            }
            return created == null ? new SpiderNull() : created;
        }
    }

    /**
     * 单只驱逐（详情空阈值满后经 BaseLoader 统一分发进来）：只摘本条目对应的蜘蛛
     * 实例并 destroy，不许拿 clear() 顶替——那会把全部 jar 蜘蛛一锅端。
     * 键公式与创建端逐字同式：spiders[md5(完整 jar 原串) + siteKey]。
     * 共享载体一概不动：loaders/methods（同 jar 共用，删了会连带兄弟站）、
     * recent（存的是 jar 键不是站点键）、locks 条目（删锁会让另一线程新建
     * 第二把锁，破坏同 spKey 创建/驱逐的串行关系）。
     * 摘除在与创建相同的 "spider:" + spKey 锁内完成：同一蜘蛛正在创建时，
     * 驱逐等创建把实例写入后再摘，不会驱逐先跑完、创建又把坏实例塞回。
     * destroy 吞异常，不打穿调用方（详情链）。
     */
    public boolean removeSpider(String key, String api, String jar, String reason) {
        if (key == null || key.isEmpty()) return false;
        String jaKey = Util.md5(jar);
        List<String> targets = new ArrayList<>();
        targets.add(jaKey + key);
        boolean removedAny = false;
        for (String spKey : targets) {
            Object lock = locks.computeIfAbsent("spider:" + spKey, ignored -> new Object());
            synchronized (lock) {
                Spider removed = spiders.remove(spKey);
                if (removed != null) {
                    removedAny = true;
                    try {
                        removed.destroy();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        // 与 Py/Js 两家 remove 同口径：无论摘没摘到，都按站点 key 清一次玩家解析缓存。
        try {
            SiteApi.clearPlayerCache(key);
        } catch (Throwable ignored) {
        }
        SpiderDebug.log("jar-loader", "remove site=%s api=%s jar=%s reason=%s hadSpider=%s", key, api, jaKey, reason, removedAny);
        return removedAny;
    }

    private static boolean isInterrupted(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException || cause instanceof CancellationException) return true;
        }
        return false;
    }

    private String source(String jar) {
        if (jar.startsWith("http")) return "http:" + Util.md5(jar);
        if (jar.startsWith("file")) return "file:" + jar.length();
        return jar;
    }

    private String error(Throwable e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        return cause.getClass().getSimpleName() + ":" + cause.getMessage();
    }

    private DexClassLoader requireRecentLoader() {
        DexClassLoader loader = loaders.get(recent);
        if (loader == null) throw new IllegalStateException("No jar loaded for recent key: " + recent);
        return loader;
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Json" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class);
        Object result = method.invoke(null, jxs, url);
        return result instanceof JSONObject ? (JSONObject) result : null;
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Mix" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
        Object result = method.invoke(null, jxs, name, flag, url);
        return result instanceof JSONObject ? (JSONObject) result : null;
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        ProxyMethod method = recent != null ? methods.get(recent) : null;
        Object[] result = proxyInvoke(method, params);
        if (result != null) return result;
        result = tryOthers(params);
        if (result != null) return result;
        // 本 jar 的 Proxy 是坏的（invokeProxy 已记 proxyFailed）且无人接手时，
        // 走 proxy() 既有 throws 出口报错，不许与「无代理处理」同形回 null。
        if (recent != null && proxyFailed.contains(recent)) throw new Exception("jar proxy unavailable key=" + recent);
        return null;
    }

    private Object[] tryOthers(Map<String, String> p) {
        return methods.entrySet().stream().filter(e -> !e.getKey().equals(recent)).map(e -> proxyInvoke(e.getValue(), p)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private Object[] proxyInvoke(ProxyMethod proxy, Map<String, String> params) {
        try {
            if (proxy == null) return null;
            if (proxy.compat != null && "pan".equals(params.get("do"))) {
                return proxy.compat.invoke(() -> {
                    Object r = proxy.method.invoke(null, params);
                    return r instanceof Object[] ? (Object[]) r : null;
                });
            }
            Object result = proxy.method.invoke(null, params);
            return result instanceof Object[] ? (Object[]) result : null;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    private record ProxyMethod(Method method, PanProxyCompat compat) {
        void close() {
            if (compat != null) compat.close();
        }
    }
}
