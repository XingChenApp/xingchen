package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.CatSource;
import com.fongmi.android.tv.api.CspWarmup;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;
import com.github.catvod.bean.Doh;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VodConfig extends BaseConfig {

    private static final String TAG = VodConfig.class.getSimpleName();

    private Site home;
    private String wall;
    private Parse parse;
    private List<Doh> doh;
    private List<Rule> rules;
    private List<Site> sites;
    private List<String> ads;
    private List<String> flags;
    private List<Parse> parses;
    // Transient runtime registry for dynamically created Sites (e.g. plugins/py scripts from search).
    // Search creates Site objects in memory without adding them to the config; detail/playback resolve
    // them through getSite(), so register them here as a fallback. Never persisted.
    private final Map<String, Site> dynamicSites = new LinkedHashMap<>();

    public static VodConfig get() {
        return Loader.INSTANCE;
    }

    public static int getCid() {
        return get().getConfig().getId();
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static int getHomeIndex() {
        return get().getSites().indexOf(get().getHome());
    }

    public static boolean hasParse() {
        return !get().getParses().isEmpty();
    }

    public static void load(Config config, Callback callback) {
        get().clear().config(config).load(callback);
    }

    public VodConfig init() {
        return config(Config.vod());
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig clear() {
        ads = null;
        doh = null;
        home = null;
        wall = null;
        parse = null;
        sites = null;
        flags = null;
        rules = null;
        parses = null;
        dynamicSites.clear();
        WebHomeExtensionRegistry.get().setGlobalSources(null, "");
        BaseLoader.get().clear("vod-config-clear");
        // 配置都没了，旧配置的在途首页加载整体作废：不推代次的话，它跑完会
        // 按同 key 把旧结果写进新配置的首页缓存（新配置先吃一口旧数据）。
        SiteApi.invalidateHomeLoads();
        SiteApi.clearHomeCache();
        SiteApi.clearDetailCache();
        // 解析缓存与上面两缓存是三张独立表：清解析缓存不重复清首页/详情缓存，
        // 只解决换配置、删源后同 key|flag|id 命中旧解析 URL 的问题。
        // 接口在 SiteApi 里（clearPlayerCache()，另一路已落树，本路只加调用）。
        SiteApi.clearPlayerCache();
        RuleConfig.get().invalidate();
        return this;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Config defaultConfig() {
        return Config.vod();
    }

    @Override
    protected void postEvent() {
        super.postEvent();
        ConfigEvent.vod();
    }

    @Override
    protected void load(Config config) throws Throwable {
        // 猫源填的是 bundle 地址（.js.md5），要先在本机把 Node 服务跑起来，再读它的 /config
        boolean bundle = CatSource.isBundle(config.getUrl());
        String url = bundle ? CatSource.serve(config.getUrl()) : UrlUtil.convert(config.getUrl());
        String json = Decoder.getJson(url, TAG);
        checkJson(config, CatSource.normalize(url, Json.parse(json)));
    }

    @Override
    protected boolean isLoaded() {
        return !getSites().isEmpty();
    }

    @Override
    protected void beforeLoad() {
        CspWarmup.reset();
    }

    @Override
    protected void onLoadSuccess() {
        CspWarmup.schedule("vod-config-loaded");
    }

    private void checkJson(Config config, JsonObject object) throws Throwable {
        if (object.has("msg")) {
            throw new Exception(object.get("msg").getAsString());
        } else if (object.has("urls")) {
            parseDepot(config, object);
        } else {
            parseConfig(config, object);
        }
    }

    private void parseDepot(Config config, JsonObject object) throws Throwable {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, VOD));
        if (configs.isEmpty()) throw new Exception("Depot urls is empty");
        load(this.config = configs.get(0));
        Config.delete(config.getUrl());
    }

    private void parseConfig(Config config, JsonObject object) {
        CustomCspSetting.inject(object);
        initList(object);
        initLive(config, object);
        initWall(config, object);
        initSite(config, object);
        initParse(config, object);
        WebHomeExtensionRegistry.get().setGlobalSources(object.get("webHomeExtensions"), config.getUrl());
        config.setLogo(Json.safeString(object, "logo"));
        config.setAssrt(Json.safeString(object, "assrt"));
        config.setNotice(Json.safeString(object, "notice"));
        config.setDanmaku(Json.safeString(object, "danmaku"));
    }

    private void initList(JsonObject object) {
        setHeaders(Header.arrayFrom(fetchArray(object, "headers")));
        setProxy(Proxy.arrayFrom(fetchArray(object, "proxy")));
        setRules(Rule.arrayFrom(fetchArray(object, "rules")));
        setDoh(Doh.arrayFrom(fetchArray(object, "doh")));
        setFlags(Json.safeListString(object, "flags"));
        setHosts(Json.safeListString(object, "hosts"));
        setAds(Json.safeListString(object, "ads"));
    }

    private void initLive(Config config, JsonObject object) {
        if (Json.isEmpty(object, "lives")) return;
        Config temp = Config.find(config, LIVE).save();
        boolean sync = LiveConfig.get().needSync(config.getUrl());
        if (sync) LiveConfig.get().config(temp.update()).parse(object);
    }

    private void initWall(Config config, JsonObject object) {
        if (Json.isEmpty(object, "wallpaper")) return;
        this.wall = Json.safeString(object, "wallpaper");
        Config temp = Config.find(wall, config.getName(), WALL).save();
        boolean sync = WallConfig.get().needSync(wall);
        if (sync) WallConfig.get().config(temp.update());
    }

    private void initSite(Config config, JsonObject object) {
        String spider = Json.safeString(object, "spider");
        BaseLoader.get().parseJar(spider, true);
        setSites(Json.safeListElement(object, "sites").stream().map(e -> Site.objectFrom(e, spider)).distinct().collect(Collectors.toCollection(ArrayList::new)));
        Map<String, Site> items = Site.findAll().stream().collect(Collectors.toMap(Site::getKey, Function.identity()));
        getSites().forEach(site -> site.sync(items.get(site.getKey())));
        CustomCspSetting.Result custom = CustomCspSetting.inject(getSites());
        Site home = !custom.home().isEmpty() ? custom.home() : getSites().stream().filter(item -> item.getKey().equals(config.getHome())).findFirst().orElse(null);
        if (home == null) {
            // 配置 sites 里没有这个主页 key：外部导入的动态脚本源（py_/js_）从不进
            // sites，先经 getSite（含当前 home 与 dynamicSites）兜一手，兜不到才按
            // 旧口径回第一个源/空 Site——别让配置重装填把导入的源静默顶掉
            // （定根账 py-import-open-diag-20261031 三·4）。
            Site dynamic = getSite(config.getHome());
            home = TextUtils.isEmpty(dynamic.getKey()) ? (getSites().isEmpty() ? new Site() : getSites().get(0)) : dynamic;
        }
        setHome(config, home, false);
    }

    private void initParse(Config config, JsonObject object) {
        setParses(Json.safeListElement(object, "parses").stream().map(Parse::objectFrom).distinct().collect(Collectors.toCollection(ArrayList::new)));
        setParse(config, getParses().isEmpty() ? new Parse() : getParses().stream().filter(item -> item.getName().equals(config.getParse())).findFirst().orElse(getParses().get(0)), false);
    }

    public List<Site> getSites() {
        return sites == null ? Collections.emptyList() : sites;
    }

    private void setSites(List<Site> sites) {
        this.sites = sites;
    }

    public List<Parse> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    private void setParses(List<Parse> parses) {
        if (!parses.isEmpty()) parses.add(0, Parse.god());
        this.parses = parses;
    }

    public List<Doh> getDoh() {
        List<Doh> items = Doh.get(App.get());
        if (doh == null) return items;
        items.removeAll(doh);
        items.addAll(doh);
        return items;
    }

    private void setDoh(List<Doh> doh) {
        this.doh = doh;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    private void setRules(List<Rule> rules) {
        this.rules = rules;
        RuleConfig.get().invalidate();
    }

    public List<Parse> getParses(int type) {
        return getParses().stream().filter(item -> item.getType() == type).toList();
    }

    public List<Parse> getParses(int type, String flag) {
        List<Parse> items = getParses(type);
        List<Parse> filter = items.stream().filter(item -> item.getExt().getFlag().contains(flag)).toList();
        return filter.isEmpty() ? items : filter;
    }

    public List<String> getFlags() {
        return flags == null ? Collections.emptyList() : flags;
    }

    private void setFlags(List<String> flags) {
        this.flags = flags;
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
        RuleConfig.get().invalidate();
    }

    public Parse getParse() {
        return parse == null ? new Parse() : parse;
    }

    public void setParse(Parse parse) {
        setParse(getConfig(), parse, true);
    }

    public Site getHome() {
        return home == null ? new Site() : home;
    }

    public void setHome(Site site) {
        setHome(getConfig(), site, true);
        RefreshEvent.home();
    }

    public String getWall() {
        return TextUtils.isEmpty(wall) ? "" : wall;
    }

    public Parse getParse(String name) {
        return getParses().stream().filter(item -> item.getName().equals(name)).findFirst().orElse(new Parse());
    }

    public Site getSite(String key) {
        Site home = getHome();
        if (key != null && key.trim().equals(home.getKey().trim())) return home;
        Site site = getSites().stream().filter(item -> item.getKey().equals(key)).findFirst().orElse(null);
        if (site == null) site = dynamicSites.get(key);
        return site == null ? new Site() : site;
    }

    /**
     * Register a dynamically created Site (e.g. a plugins/py script built in memory by search)
     * so that detail/playback can resolve it via {@link #getSite(String)}.
     * Memory-only, never written to the config.
     */
    public void registerDynamicSite(Site site) {
        if (site != null && !TextUtils.isEmpty(site.getKey())) dynamicSites.put(site.getKey(), site);
    }

    // ── 导入待开标记（确定性打开的凭据，定根账 fix168-pyimport2）──
    // setHome 发的 RefreshEvent.home 是普通 EventBus 事件：首页 Fragment 尚不存在
    // （冷开）或其生命周期时点对不上时就丢了，两轮靠 Fragment 生命周期兜底仍被他
    // 真机证伪。改走持久标记：导入成功登记目标 key，首页 Fragment 在 initView／
    // onResume／切回可见任一时点自查消费——只要它最终露面就必然补开，不依赖
    // 事件送达、也不靠手动点。标记落 SharedPreferences，导入后进程被杀也不丢。
    private static final String IMPORT_PREFS = "xc_import";
    private static final String KEY_PENDING_IMPORT = "pending_home_key";
    private volatile String pendingImportKey;

    /** 导入成功且 setHome 已落到当前 home 后登记待开目标（调用方负责核对 home key）。 */
    public void markImportedHome(String key) {
        if (TextUtils.isEmpty(key)) return;
        pendingImportKey = key;
        try {
            App.get().getSharedPreferences(IMPORT_PREFS, android.content.Context.MODE_PRIVATE)
                    .edit().putString(KEY_PENDING_IMPORT, key).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 读待开目标（不消费）；没有返回 null。 */
    public String pendingImportedHome() {
        if (pendingImportKey != null) return pendingImportKey;
        try {
            pendingImportKey = App.get().getSharedPreferences(IMPORT_PREFS, android.content.Context.MODE_PRIVATE)
                    .getString(KEY_PENDING_IMPORT, null);
        } catch (Throwable ignored) {
        }
        return pendingImportKey;
    }

    /** 首页已按待开目标补开后清除标记。 */
    public void clearImportedHome() {
        pendingImportKey = null;
        try {
            App.get().getSharedPreferences(IMPORT_PREFS, android.content.Context.MODE_PRIVATE)
                    .edit().remove(KEY_PENDING_IMPORT).apply();
        } catch (Throwable ignored) {
        }
    }

    private void setParse(Config config, Parse parse, boolean save) {
        this.parse = parse;
        this.parse.setSelected(true);
        config.setParse(parse.getName());
        getParses().forEach(item -> item.setSelected(parse));
        if (save) config.save();
    }

    private void setHome(Config config, Site site, boolean save) {
        home = site;
        home.setSelected(true);
        config.setHome(home.getKey());
        if (save) config.save();
        getSites().forEach(item -> item.setSelected(home));
    }

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }
}
