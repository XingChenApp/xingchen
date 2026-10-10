package com.fongmi.android.tv.api;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.hk.HkDetailBridge;
import com.fongmi.android.tv.api.hk.HkPlay;
import com.fongmi.android.tv.api.hk.HkRouter;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.web.WebHomeInlineVodStore;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Response;

public class SiteApi {

    public static final String PUSH = "push_agent";

    public static String call(@NonNull Site site, @NonNull ArrayMap<String, String> params) throws IOException {
        if (!site.getExt().isEmpty()) params.put("extend", site.getExt());
        Call call = site.getExt().length() <= 1000 ? OkHttp.newCall(site.getApi(), site.getHeader(), params) : OkHttp.newCall(site.getApi(), site.getHeader(), OkHttp.toBody(params));
        try (Response response = call.execute()) {
            return response.body().string();
        }
    }

    private static boolean isSpider(@NonNull String key, @NonNull Site site) {
        return site.getType() == 3 || key.startsWith("py_");
    }



    private static String ac(int type) {
        return type == 0 ? "videolist" : "detail";
    }

    private static final long DETAIL_CACHE_TTL = 5 * 60 * 1000;
    private static final ConcurrentHashMap<String, DetailCacheEntry> detailCache = new ConcurrentHashMap<>();

    private static class DetailCacheEntry {
        final long time;
        final String detail;

        DetailCacheEntry(long time, String detail) {
            this.time = time;
            this.detail = detail;
        }

        boolean fresh() {
            return System.currentTimeMillis() - time < DETAIL_CACHE_TTL;
        }
    }

    public static void clearDetailCache() {
        detailCache.clear();
    }

    public static void clearDetailCache(String key) {
        if (key != null) detailCache.remove(key);
    }

    public static void clearHomeCache() {
    }

    public static void clearHomeCache(String key) {
    }

    @NonNull
    public static Result homeContent(@NonNull Site site) throws Exception {
        return homeContent(site, false);
    }

    @NonNull
    public static Result homeContent(@NonNull Site site, boolean forceRefresh) throws Exception {
        if (isSpider(site.getKey(), site)) {
            Spider spider = site.recent().spider();
            boolean crash = Prefers.getBoolean("crash");
            String home;
            String video;
            if (crash) {
                home = "";
                video = "";
            } else {
                Future<String> fHome = Task.executor().submit(() -> spider.homeContent(true));
                Future<String> fVideo = Task.executor().submit(spider::homeVideoContent);
                try {
                    home = fHome.get();
                    video = fVideo.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof Exception) throw (Exception) cause;
                    else throw new RuntimeException(cause);
                }
            }
            Prefers.put("crash", false);
            SpiderDebug.log("homeVideo", video);
            Result result = Result.fromJson(home);
            List<Vod> list = Result.fromJson(video).getList();
            if (!list.isEmpty()) result.setList(list);
            setTypes(site, result);
            return result;
        } else if (site.getType() == 4) {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("filter", "true");
            String homeContent = call(site.fetchExt(), params);
            SpiderDebug.log("home", homeContent);
            Result result = Result.fromJson(homeContent);
            setTypes(site, result);
            return result;
        } else {
            try (Response response = OkHttp.newCall(site.getApi(), site.getHeader()).execute()) {
                String homeContent = response.body().string();
                SpiderDebug.log("home", homeContent);
                Result result = Result.fromType(site.getType(), homeContent);
                fetchPic(site, result);
                setTypes(site, result);
                return result;
            }
        }
    }

    @NonNull
    public static Result categoryContent(@NonNull String key, @NonNull String tid, @NonNull String page, boolean filter, @NonNull HashMap<String, String> extend) throws Exception {
        SpiderDebug.log("category", "key=%s,tid=%s,page=%s,filter=%s,extend=%s", key, tid, page, filter, extend);
        Site site = VodConfig.get().getSite(key);
        if (isSpider(key, site)) {
            String categoryContent = site.recent().spider().categoryContent(tid, page, filter, extend);
            SpiderDebug.log("category", categoryContent);
            return Result.fromJson(categoryContent);
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            if (site.getType() == 1 && !extend.isEmpty()) params.put("f", App.gson().toJson(extend));
            if (site.getType() == 4) params.put("ext", Util.base64(App.gson().toJson(extend), Util.URL_SAFE));
            params.put("ac", ac(site.getType()));
            params.put("t", tid);
            params.put("pg", page);
            String categoryContent = call(site, params);
            SpiderDebug.log("category", categoryContent);
            return Result.fromType(site.getType(), categoryContent);
        }
    }

    @NonNull
    public static Result detailContent(@NonNull String key, @NonNull String id) throws Exception {
        return detailContent(key, id, false);
    }

    @NonNull
    public static Result detailContent(@NonNull String key, @NonNull String id, boolean forceRefresh) throws Exception {
        SpiderDebug.log("detail", "key=%s,id=%s", key, id);
        if (WebHomeInlineVodStore.KEY.equals(key)) return WebHomeInlineVodStore.detail(id);
        Site site = VodConfig.get().getSite(key);
        if (site.isEmpty() && PUSH.equals(key)) {
            Vod vod = new Vod();
            vod.setId(id);
            vod.setName(id);
            vod.setPlayUrl(id);
            vod.setPlayFrom(ResUtil.getString(R.string.push));
            vod.setPic(ResUtil.getString(R.string.push_image));
            Source.get().parse(vod.setFlags());
            return Result.vod(vod);
        } else if (isSpider(key, site)) {
            String cacheKey = key + "_" + id;
            if (!forceRefresh) {
                DetailCacheEntry cached = detailCache.get(cacheKey);
                if (cached != null && cached.fresh()) {
                    Result result = Result.fromJson(cached.detail);
                    Source.get().parse(result.getVod().setFlags());
                    return result;
                }
            }
            String detailContent = site.recent().spider().detailContent(Arrays.asList(id));
            SpiderDebug.log("detail", detailContent);
            Result result = Result.fromJson(detailContent);
            Vod vod = result.getVod();
            if (vod == null || vod.getPlayFrom().isEmpty() || vod.getPlayUrl().isEmpty()) {
                BaseLoader.get().removePySpider(key);
            } else {
                detailCache.put(cacheKey, new DetailCacheEntry(System.currentTimeMillis(), detailContent));
            }
            Source.get().parse(result.getVod().setFlags());
            return result;
        } else {
            String cacheKey = key + "_" + id;
            if (!forceRefresh) {
                DetailCacheEntry cached = detailCache.get(cacheKey);
                if (cached != null && cached.fresh()) {
                    Result result = Result.fromType(site.getType(), cached.detail);
                    Source.get().parse(result.getVod().setFlags());
                    return result;
                }
            }
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("ac", ac(site.getType()));
            params.put("ids", id);
            String detailContent = call(site, params);
            SpiderDebug.log("detail", detailContent);
            detailCache.put(cacheKey, new DetailCacheEntry(System.currentTimeMillis(), detailContent));
            Result result = Result.fromType(site.getType(), detailContent);
            Source.get().parse(result.getVod().setFlags());
            return result;
        }
    }

    /**
     * CloudFront rejects requests carrying an unexpected Referer (HTTP 403).
     * The PY sets Referer to its api domain; strip it for CloudFront video URLs
     * so the request goes out clean (verified: curl with no Referer gets 200).
     * Other headers (e.g. User-Agent) are kept intact.
     */
    private static void stripRefererForCloudFront(@NonNull Result result) {
        String url = result.getUrl().v();
        if (url.isEmpty() || !url.contains("cloudfront.net")) return;
        Map<String, String> header = result.getHeader();
        if (header.isEmpty()) return;
        header.remove("Referer");
        header.remove("referer");
    }

    @NonNull
    public static Result playerContent(@NonNull String key, @NonNull String flag, @NonNull String id) throws Exception {
        return playerContent(key, flag, id, PlayerSetting.getPlayer());
    }

    @NonNull
    public static Result playerContent(@NonNull String key, @NonNull String flag, @NonNull String id, int playerType) throws Exception {
        SpiderDebug.log("player", "key=%s,flag=%s,id=%s", key, flag, id);
        Source.get().stop();
        if (WebHomeInlineVodStore.KEY.equals(key)) return WebHomeInlineVodStore.player(flag, id);
        if (HkDetailBridge.isHkKey(key)) return hkPlayerContent(key, flag, id);
        Site site = VodConfig.get().getSite(key);
        if (isSpider(key, site)) {
            String playerContent = site.recent().spider().playerContent(flag, id, VodConfig.get().getFlags());
            SpiderDebug.log("player", playerContent);
            Result result = Result.fromJson(playerContent);
            if (result.getFlag().isEmpty()) result.setFlag(flag);
            result.setUrl(Source.get().fetch(result, playerType));
            result.setHeader(site.getHeader());
            stripRefererForCloudFront(result);
            result.setKey(key);
            return result;
        } else if (site.getType() == 4) {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("play", id);
            params.put("flag", flag);
            String playerContent = call(site, params);
            SpiderDebug.log("player", playerContent);
            Result result = Result.fromJson(playerContent);
            if (result.getFlag().isEmpty()) result.setFlag(flag);
            result.setUrl(Source.get().fetch(result, playerType));
            result.setHeader(site.getHeader());
            return result;
        } else if (site.isEmpty() && "push_agent".equals(key)) {
            Result result = new Result();
            result.setUrl(id);
            result.setParse(0);
            result.setFlag(flag);
            result.setUrl(Source.get().fetch(result, playerType));
            SpiderDebug.log("player", result.toString());
            return result;
        } else {
            Result result = new Result();
            result.setUrl(id);
            result.setFlag(flag);
            result.setHeader(site.getHeader());
            result.setPlayUrl(site.getPlayUrl());
            result.setParse(Sniffer.isVideoFormat(id) && result.getPlayUrl().isEmpty() ? 0 : 1);
            result.setUrl(Source.get().fetch(result, playerType));
            SpiderDebug.log("player", result.toString());
            return result;
        }
    }

    /**
     * 海阔播放路由缓存。
     *
     * <p>根因：之前每次播放都 {@code HkRouter.open(title)} 全新初始化——新建 QuickJSContext、
     * 注册上百个 API、读 kv/config 文件、跑 preRule（很多规则的 preRule 会发网络请求）。
     * 点一次封面要等好几秒，大头是这里重复初始化，而不是真正的地址解析。</p>
     *
     * <p>缓存按规则名复用已初始化的 HkRouter：HkJsRuntime 的 JS 调用全部走单线程 executor
     * 串行，跨播放复用线程安全；每次取用前校验规则文件 mtime（用户改规则立即失效）
     * 与 15 分钟 TTL（preRule 里的登录态/cookie 定期刷新）。最多缓存 3 个规则，
     * 淘汰时 destroy 释放 QuickJS 上下文。</p>
     */
    private static final int HK_ROUTER_CACHE_MAX = 3;
    private static final long HK_ROUTER_CACHE_TTL_MS = 15 * 60 * 1000L;
    private static final ExecutorService HK_INIT_EXEC = Executors.newSingleThreadExecutor();
    private static final ConcurrentHashMap<String, HkRouterCacheEntry> hkRouterCache = new ConcurrentHashMap<>();

    private static class HkRouterCacheEntry {
        volatile Future<HkRouter> future;
        volatile long ruleModified;
        volatile long createdAt;
    }

    /**
     * 预热某规则的播放路由（点封面预检出直接播放地址后调用）：后台提前做
     * QuickJS 初始化 + preRule，等播放器真正调 playerContent 时缓存已就绪。
     * UI 线程调用也安全，只提交任务立即返回。
     */
    public static void warmHkRouter(String title) {
        if (title == null || title.isEmpty()) return;
        try {
            acquireHkRouterFuture(title);
        } catch (Throwable ignored) {
        }
    }

    /** 取已初始化的 HkRouter（缓存命中直接返回，未命中则同步初始化）。 */
    private static HkRouter acquireHkRouter(String title) throws Exception {
        Future<HkRouter> f = acquireHkRouterFuture(title);
        try {
            HkRouter router = f.get(30, TimeUnit.SECONDS);
            // 口令流程的一次性 input 覆盖只属于详情页那次求值，播放路由复用时清掉，
            // 防止上次残留污染本次 lazyRule 的 input。
            try {
                router.getEngine().getJsRuntime().setNextInputOverride(null);
            } catch (Throwable ignored) {
            }
            return router;
        } catch (ExecutionException ee) {
            // 初始化失败：清掉坏条目，下次重建；把原始异常抛给调用方（行为与之前一致）
            hkRouterCache.remove(title);
            Throwable c = ee.getCause();
            if (c instanceof Exception) throw (Exception) c;
            throw new RuntimeException(c);
        }
    }

    private static synchronized Future<HkRouter> acquireHkRouterFuture(String title) {
        long mtime = HkRuleManager.get().ruleModified(title);
        HkRouterCacheEntry e = hkRouterCache.get(title);
        if (e != null && e.ruleModified == mtime && !hkRouterExpired(e) && !hkRouterFailed(e)) {
            return e.future;
        }
        if (e != null) {
            hkRouterCache.remove(title);
            destroyHkRouterFuture(e.future);
        }
        // LRU 淘汰：按创建时间踢掉最老的
        while (hkRouterCache.size() >= HK_ROUTER_CACHE_MAX) {
            String oldest = null;
            long oldestAt = Long.MAX_VALUE;
            for (Map.Entry<String, HkRouterCacheEntry> en : hkRouterCache.entrySet()) {
                if (en.getValue().createdAt < oldestAt) {
                    oldestAt = en.getValue().createdAt;
                    oldest = en.getKey();
                }
            }
            if (oldest == null) break;
            HkRouterCacheEntry out = hkRouterCache.remove(oldest);
            if (out != null) destroyHkRouterFuture(out.future);
        }
        HkRouterCacheEntry ne = new HkRouterCacheEntry();
        ne.ruleModified = mtime;
        ne.createdAt = System.currentTimeMillis();
        ne.future = HK_INIT_EXEC.submit(() -> HkRouter.open(title));
        hkRouterCache.put(title, ne);
        return ne.future;
    }

    private static boolean hkRouterExpired(HkRouterCacheEntry e) {
        return System.currentTimeMillis() - e.createdAt > HK_ROUTER_CACHE_TTL_MS;
    }

    private static boolean hkRouterFailed(HkRouterCacheEntry e) {
        Future<HkRouter> f = e.future;
        if (f == null || !f.isDone() || f.isCancelled()) return false;
        try {
            f.get(1, TimeUnit.MILLISECONDS);
            return false;
        } catch (ExecutionException ee) {
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void destroyHkRouterFuture(Future<HkRouter> f) {
        if (f == null) return;
        if (!f.isDone()) {
            // 初始化还在排队/执行：cancel(false) 不中断，只防未开始的任务启动；
            // 若已在执行，完成后顺手 destroy，避免泄漏 QuickJS 上下文。
            f.cancel(false);
            HK_INIT_EXEC.submit(() -> {
                try {
                    HkRouter r = f.get(60, TimeUnit.SECONDS);
                    if (r != null) r.destroy();
                } catch (Throwable ignored) {
                }
            });
            return;
        }
        try {
            HkRouter r = f.get(1, TimeUnit.MILLISECONDS);
            if (r != null) r.destroy();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 海阔小程序播放（M4）：{@code hk_<规则名>} key 拦截 → {@link HkRouter#play(String)}
     * 分流链解析出真地址 → 直接组装 Result，不走 spider/嗅探。
     *
     * <p>详情页点选集（VideoActivity → SiteViewModel.playerContent → 本方法）统一走这里，
     * 因此选集、下一集、历史续播都会先经过海阔分流链。</p>
     */
    @NonNull
    private static Result hkPlayerContent(@NonNull String key, @NonNull String flag, @NonNull String id) throws Exception {
        String title = key.substring(HkDetailBridge.KEY_PREFIX.length());
        // 路由缓存复用：不再每次播放重建 QuickJS 上下文+跑 preRule（之前点封面等几秒的主因）
        HkRouter router = acquireHkRouter(title);
        HkPlay play = router.play(id);
        Result result = new Result();
        result.setUrl(play.getUrl());
        result.setParse(0);
        result.setFlag(flag);
        if (!play.getHeaders().isEmpty()) result.setHeader(play.getHeaders());
        result.setKey(key);
        SpiderDebug.log("player", "hk play resolved: %s", play.getUrl());
        return result;
    }

    @NonNull
    public static Result searchContent(@NonNull Site site, @NonNull String keyword, boolean quick, @NonNull String page) throws Exception {
        SpiderDebug.log("search", "site=%s,keyword=%s,quick=%s,page=%s", site.getName(), keyword, quick, page);
        boolean hasPage = !page.equals("1");
        if (isSpider(site.getKey(), site)) {
            String searchContent = hasPage ? site.spider().searchContent(keyword, quick, page) : site.spider().searchContent(keyword, quick);
            SpiderDebug.log("search", searchContent);
            Result result = Result.fromJson(searchContent);
            for (Vod vod : result.getList()) vod.setSite(site);
            return result;
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("wd", keyword);
            params.put("quick", String.valueOf(quick));
            params.put("extend", "");
            if (hasPage) params.put("pg", page);
            String searchContent = call(site, params);
            SpiderDebug.log("search", searchContent);
            Result result = fetchPic(site, Result.fromType(site.getType(), searchContent));
            for (Vod vod : result.getList()) vod.setSite(site);
            return result;
        }
    }

    @NonNull
    public static Result action(@NonNull String key, @NonNull String action) throws Exception {
        Site site = VodConfig.get().getSite(key);
        if (isSpider(key, site)) return Result.fromJson(site.recent().spider().action(action));
        if (site.getType() == 4) return Result.fromJson(OkHttp.string(action));
        return Result.empty();
    }

    @NonNull
    public static Result fetchPic(@NonNull Site site, @NonNull Result result) throws Exception {
        if (site.getType() > 2 || result.getList().isEmpty() || !result.getVod().getPic().isEmpty()) return result;
        ArrayList<String> ids = new ArrayList<>();
        boolean empty = site.getCategories().isEmpty();
        for (Vod item : result.getList()) if (empty || site.getCategories().contains(item.getTypeName())) ids.add(item.getId());
        if (ids.isEmpty()) return result.clear();
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("ac", ac(site.getType()));
        params.put("ids", TextUtils.join(",", ids));
        try (Response response = OkHttp.newCall(site.getApi(), site.getHeader(), params).execute()) {
            result.setList(Result.fromType(site.getType(), response.body().string()).getList());
            return result;
        }
    }

    private static void setTypes(@NonNull Site site, @NonNull Result result) {
        result.getTypes().stream().filter(type -> result.getFilters().containsKey(type.getTypeId())).forEach(type -> type.setFilters(result.getFilters().get(type.getTypeId())));
        if (site.getCategories().isEmpty()) return;
        Map<String, Class> typeByName = new HashMap<>();
        result.getTypes().forEach(type -> typeByName.put(type.getTypeName(), type));
        List<Class> types = site.getCategories().stream().map(typeByName::get).filter(Objects::nonNull).toList();
        if (!types.isEmpty()) result.setTypes(types);
    }

}



