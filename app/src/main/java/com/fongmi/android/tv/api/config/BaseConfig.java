package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

abstract class BaseConfig {

    public static final int VOD = 0;
    public static final int LIVE = 1;
    public static final int WALL = 2;

    private final AtomicInteger taskId = new AtomicInteger(0);

    protected boolean sync;
    protected volatile Config config;
    private volatile Future<?> future;

    protected abstract String getTag();

    protected abstract Config defaultConfig();

    protected abstract void load(Config config) throws Throwable;

    protected abstract boolean isLoaded();

    protected void beforeLoad() {
    }

    protected void onLoadSuccess() {
    }

    public synchronized void ensureLoaded() {
        try {
            if (isLoaded()) return;
            beforeLoad();
            if (config == null) config = defaultConfig();
            Server.get().start();
            load(config);
            onLoadSuccess();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    protected void postEvent() {
        ConfigEvent.common();
    }

    public boolean needSync(String url) {
        return sync || config == null || TextUtils.isEmpty(config.getUrl()) || url.equals(config.getUrl());
    }

    public Config getConfig() {
        return config == null ? defaultConfig() : config;
    }

    protected void setHeaders(List<Header> headers) {
        OkHttp.responseInterceptor().addAll(headers);
    }

    protected void setProxy(List<Proxy> proxy) {
        OkHttp.selector().addAll(proxy);
    }

    protected void setHosts(List<String> hosts) {
        OkHttp.dns().addAll(hosts);
    }

    public void load(Callback callback) {
        beforeLoad();
        int id = taskId.incrementAndGet();
        if (future != null && !future.isDone()) {
            cancelLoad();
            future.cancel(true);
        }
        future = Task.submit(() -> loadConfig(id, config, callback));
        callback.start();
    }

    public void cancel() {
        taskId.incrementAndGet();
        cancelLoad();
        OkHttp.cancel(getTag());
        if (future != null && !future.isDone()) future.cancel(true);
    }

    // 取消在途加载的附带清理钩（上游同名口径）：默认无操作，需要收口的
    // 子类覆写。原 VodConfig 覆写（取消旧 Node 专用链加载）已随 E14 分发
    // 改形退场，现无子类覆写。
    protected void cancelLoad() {
    }

    /**
     * 本地回环配置的连接失败重试（仅回环＋仅连接失败才重试）：
     * 已选配置若是实验室服务的回环地址，冷启时服务进程要几秒才监听，
     * 首读必然先撞连接拒绝。回环连接拒绝是毫秒级返回的，短睡重试不拖慢
     * 正常路径；窗口 10 次×2 秒＝最多约 20 秒，按真机 Node 冷启量级
     * （进程拉起＋运行时链接＋监听，本机样本实测 1 秒内监听、真机 Termux
     * 运行时慢数倍）留了数倍余量，真死了 20 秒后照旧只弹一次错。
     * 远程配置地址不是回环、失败不是连接失败（解析错/内容错）都不进重试，
     * 原行为一字不变。
     */
    private static final int LOOPBACK_MAX_RETRIES = 10;
    private static final long LOOPBACK_RETRY_INTERVAL_MS = 2000;

    protected void loadConfig(int id, Config config, Callback callback) {
        int loopbackRetries = 0;
        while (true) {
            boolean success = false;
            boolean retryLoopback = false;
            try {
                Server.get().start();
                OkHttp.cancel(getTag());
                load(config);
                if (taskId.get() != id) return;
                if (config.equals(this.config)) config.update();
                onLoadSuccess();
                // 接口公告不再弹（2026-10-31 他点名「删了还一直弹」）：notice 是接口
                // 配置自带的内容，加载成功就弹一次，删净前会反复出现；显示权在咱这边，
                // 索性关掉——任何接口的公告都不再弹出。
                App.post(callback::success);
                success = true;
            } catch (Throwable e) {
                if (isCanceled(e)) return;
                if (taskId.get() != id) return;
                if (loopbackRetries < LOOPBACK_MAX_RETRIES && isLoopbackConnectFailure(config, e)) {
                    retryLoopback = true;
                } else {
                    e.printStackTrace();
                    if (TextUtils.isEmpty(config.getUrl())) App.post(() -> callback.error(""));
                    else App.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
                }
            } finally {
                // 只在成功时发事件：失败路径已有 callback.error 弹错＋StateEvent.empty，
                // 再发事件会让首页（HomeActivity VOD 分支 → RefreshEvent.home）无条件重拉一次。
                // 失败时无人依赖该事件：消费方核查——HomeActivity（VOD 转首页刷新／LIVE、COMMON 只调
                // setNavigation）、leanback Home（VOD 转首页刷新）、VideoActivity 两版（只刷新解析器列表，
                // 失败时解析器本就为空／旧数据）、PlaybackService（VOD/LIVE 清浏览树，失败不清、留旧树，
                // 且 session 为空时直接返回）、SettingFragment/leanback Setting（只刷新壁纸文案）。
                if (success && taskId.get() == id) postEvent();
            }
            if (!retryLoopback) return;
            loopbackRetries++;
            try {
                Thread.sleep(LOOPBACK_RETRY_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            if (taskId.get() != id) return;
        }
    }

    /** 失败可否按「本地服务还没起来」重试：地址是回环＋异常链里有连接失败，两条都中才算。 */
    private boolean isLoopbackConnectFailure(Config config, Throwable e) {
        if (config == null || !isLoopbackUrl(config.getUrl())) return false;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.ConnectException) return true;
            String m = t.getMessage();
            if (m != null && m.contains("Failed to connect")) return true;
        }
        return false;
    }

    private static boolean isLoopbackUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        try {
            String host = new java.net.URI(url.trim()).getHost();
            // URI.getHost 对 IPv6 回环返回带括号的 "[::1]"，两种都认
            return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)
                    || "::1".equals(host) || "[::1]".equals(host);
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean isCanceled(Throwable e) {
        if ("Canceled".equals(e.getMessage())) return true;
        if (e instanceof InterruptedException) return true;
        if (e instanceof InterruptedIOException) return true;
        return e.getCause() instanceof InterruptedIOException;
    }

    protected JsonArray fetchArray(JsonObject object, String key) {
        if (!object.has(key)) return new JsonArray();
        JsonElement element = object.get(key);
        if (element.isJsonObject()) return new JsonArray();
        if (element.isJsonPrimitive()) element = fetch(element.getAsString());
        JsonArray result = new JsonArray();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item.isJsonPrimitive()) result.addAll(fetch(item.getAsString()));
            else if (item.isJsonObject()) result.add(item);
        }
        return result;
    }

    private JsonArray fetch(String url) {
        try {
            JsonElement parsed = Json.parse(OkHttp.string(UrlUtil.convert(url)));
            if (parsed.isJsonArray()) return parsed.getAsJsonArray();
            // 扫描 O4：拉到但不是数组＝内容不对版，与失败一样留痕，不许静默吞成空数组
            SpiderDebug.log("config",
                    "fetch sub-file not an array url=%s", url);
            return new JsonArray();
        } catch (Exception e) {
            // 扫描 O4：headers/proxy/rules/doh 子文件拉取失败此前静默变空数组、
            // 配置照样报加载成功（代理/规则/请求头/DoH 缺失无任何线索）；
            // 行为不变（仍回空数组不阻断配置加载），但必须留痕可查。
            SpiderDebug.log("config",
                    "fetch sub-file failed url=%s err=%s", url, e.toString());
            return new JsonArray();
        }
    }
}
