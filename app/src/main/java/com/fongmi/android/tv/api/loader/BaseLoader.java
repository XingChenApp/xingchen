package com.fongmi.android.tv.api.loader;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.Proxy;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Util;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import dalvik.system.DexClassLoader;

public class BaseLoader {

    private final JarLoader jarLoader;
    private final PyLoader pyLoader;
    private final JsLoader jsLoader;
    private final java.util.concurrent.ConcurrentHashMap<String, Spider> catSpiders;

    private BaseLoader() {
        jarLoader = new JarLoader();
        pyLoader = new PyLoader();
        jsLoader = new JsLoader();
        catSpiders = new java.util.concurrent.ConcurrentHashMap<>();
    }

    public static BaseLoader get() {
        return Loader.INSTANCE;
    }

    private static boolean isJs(String api) {
        // 这里只是 JS 家族的总闸（.js/.wv 都先进 JsLoader）；通道细分在
        // JsLoader.getSpider 按内容判定：新格式宿主型 JS（wvB/WvSpider 类，
        // 无 __JS_SPIDER__、带 @config/routes/同名入口函数）分发到 WvSpider
        // 新通道，不再塞进普通 drpy 通道；普通源路径不变。
        return api.contains(".js") || api.contains(".wv");
    }

    private static boolean isPy(String api) {
        return api.contains(".py");
    }

    private static boolean isCsp(String api) {
        return api.startsWith("csp_");
    }

    // 退桥残留守卫：node: 前缀是旧桥独占的 api 方案（旧 NodeConfigMapper 把
    // 站 api 统一改写成 node:/spider/<站>/<type> 或 node:/<原 api 路径>，余数
    // 恒以 / 开头、从不是 py/js 文件引用），旧桥退场后全树再无生产者、新链
    // 走 http 本机 /spider/ 路由（CatSpider）。但余数里可能带 .js/.py 子串
    // （如 node:/spider/foo.js/3），若任由下面的子串谓词判型会被 Py/JsLoader
    // 抢走误分，故凡 node: 开头在分发前一律明确落 SpiderNull／no-op，与
    // 退桥口径一致。合法 py/js/csp/cat 的 api 都不以 node: 开头（py/js 是
    // 文件路径或 http 地址、csp 以 csp_ 开头、cat 以 http 开头），不误伤。
    private static boolean isNode(String api) {
        return api.startsWith("node:");
    }

    public void clear() {
        clear("unspecified");
    }

    /** 全清（配蜘蛛失效口径）：原因透传给 PyLoader 记日志，真机重建频率靠它看。\n     * 详情空计数同步清（换配置／恢复备份都走这里，旧一笔不算进“连续”）。 */
    public void clear(String reason) {
        Task.execute(() -> {
            jarLoader.clear();
            pyLoader.clear(reason);
            jsLoader.clear();
            catSpiders.clear();
            try {
                SiteApi.clearDetailEmptyStrikes();
            } catch (Throwable ignored) {
            }
        });
    }

    public void clearPy() {
        clearPy("unspecified");
    }

    public void clearPy(String reason) {
        pyLoader.clear(reason);
    }

    public void clearJs() {
        jsLoader.clear();
    }

    public void removePySpider(String key) {
        removePySpider(key, "unspecified");
    }

    public void removePySpider(String key, String reason) {
        pyLoader.remove(key, reason);
    }

    /** 按脚本文件单清（目录监听／删插件／导入插件用）：只清该文件对应的蜘蛛＋首页缓存。 */
    public void invalidatePyScript(String absolutePath) {
        pyLoader.invalidateScript(absolutePath);
    }

    public void removeJsSpider(String key) {
        jsLoader.remove(key);
    }

    /**
     * 统一驱逐分发：类型谓词与顺序严格复用 getSpider（Cat → Node(no-op) → Py → JS → CSP），
     * 调用方不许再自维护近似判型（详情空驱逐旧写法只判 .js、其余皆 Py，
     * jar 蜘蛛全误落 PyLoader 空转、.wv 也误分）。jar 必须传 Site 的原串：
     * JarLoader 创建键是对完整原串取 md5，驱逐端任何拆解/归一都会算错键。
     * api 为空或未知类型为 no-op，不误伤任何一张表。
     */
    public void removeSpider(String key, String api, String jar, String reason) {
        if (api == null) return;
        if (CatSpider.matches(api)) catSpiders.remove(key);
        else if (isNode(api)) {
            // node: 蜘蛛已随旧桥退场、无任何缓存表持有它，驱逐为 no-op；
            // 不加此支，带 .py/.js 子串的 node: 串会误驱逐同 key 的 py/js 蜘蛛。
        }
        else if (isPy(api)) pyLoader.remove(key, reason);
        else if (isJs(api)) jsLoader.remove(key);
        else if (isCsp(api)) jarLoader.removeSpider(key, api, jar, reason);
    }

    public void watchPyPlugins(java.io.File dir) {
        pyLoader.watchPlugins(dir);
    }

    public void watchJsPlugins(java.io.File dir) {
        jsLoader.watchPlugins(dir);
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        // 猫源站点 type 也是 3，但 api 指向本机 bundle 的 HTTP 路由，得走协议适配而非本地引擎
        if (CatSpider.matches(api)) return catSpiders.computeIfAbsent(key, k -> new CatSpider(api));
        if (isNode(api)) return new SpiderNull();
        if (isPy(api)) return pyLoader.getSpider(key, api, ext);
        else if (isJs(api)) return jsLoader.getSpider(key, api, ext, jar);
        else if (isCsp(api)) return jarLoader.getSpider(key, api, ext, jar);
        else return new SpiderNull();
    }

    /**
     * 静默加载（PY 失败不弹 Toast、只落强制日志，结果由调用方统一展示）。
     * 健康检查与后台路径（首页预热／蜘蛛预热／直播配置自动解析）共用：
     * JS 本就不弹 Toast 走原路；jar 蜘蛛同样不弹 Toast（JarLoader 只落强制日志），
     * 走原路保证混合类型后台路径的预热语义与前台一致，不因静默把 jar 源漏暖。
     */
    public Spider getSpiderQuiet(String key, String api, String ext, String jar) {
        if (CatSpider.matches(api)) return catSpiders.computeIfAbsent(key, k -> new CatSpider(api));
        if (isNode(api)) return new SpiderNull();
        if (isPy(api)) return pyLoader.getSpiderQuiet(key, api, ext);
        else if (isJs(api)) return jsLoader.getSpider(key, api, ext, jar);
        else if (isCsp(api)) return jarLoader.getSpider(key, api, ext, jar);
        else return new SpiderNull();
    }

    public Spider getSpider(String key) {
        Site site = VodConfig.get().getSite(key);
        Live live = LiveConfig.get().getLive(key);
        if (!site.isEmpty()) return site.spider();
        if (!live.isEmpty()) return live.spider();
        return new SpiderNull();
    }

    public void setRecent(String key, String api, String jar) {
        if (isNode(api)) return;
        if (isJs(api)) jsLoader.setRecent(key);
        else if (isPy(api)) pyLoader.setRecent(key);
        else if (isCsp(api)) jarLoader.setRecent(Util.md5(jar));
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        // 代理 seam（本地 NanoHTTPD 唯一入口 server/process/Proxy.java:41）：
        // ① 存活判定：siteKey 路先解析蜘蛛——解析出来是 SpiderNull（源已删／无此源）
        // 就是已死的代理，直接返回 null，不许死蜘蛛进代理；do=py/do=js 路由 recent，
        // recent 已清／蜘蛛已摘时 loader 本身返回 null，同样不进代理。
        // ② 已关闭对象／空列表转换失败：删源／重载／世代切换后仍在途的代理请求可能
        // 撞上刚被 destroy() 的蜘蛛——依赖库内 Spider.proxy 读已关闭的 PyObject
        // （PyObject is closed）或对 null 返回做 asList() 抛 NPE，seam 拦不住库内那
        // 一行，但能拦住它抛出来的结果。口径（2026-11-01 修订）：源本体健康时不许
        // 直接判死——siteKey 路在这里重新解析活蜘蛛重试一次、do=py 路在
        // PyLoader.proxy 内摘死实例按来源重建重试一次；重试仍旧关闭／解析不出活
        // 蜘蛛／空列表转换 NPE，才按“代理上游已死”返回 null、由 Proxy 转 502 可见
        // 错，不许以 500／崩溃／静默空结果收场；其它异常（网络、上游 4xx/5xx 等）
        // 原样上抛，保持原有错误口径。
        // ③ 逐字段判空：蜘蛛返回的 Object[] 只有到这里才看得见，逐下标归一——
        // [0] 状态码非 Integer、[2] 数据流非 InputStream、长度不足 3，一律按无效
        // 返回 null（Proxy 转 502）；[1] mime 为空、[3] 请求头不是 Map／为空，按空
        // 处理不抛（mime 透传 null、请求头透传 null，Proxy.createResponse 同样容忍）。
        // 注意：py 返回列表本身为 null 时库内 asList() 已抛 NPE，seam 看不到“空列
        // 表”，只能按②转 502——这是 seam 的边界，见交回卡点。
        Object[] rs;
        try {
            if (params.containsKey("siteKey")) {
                Spider spider = getSpider(params.get("siteKey"));
                if (spider instanceof SpiderNull) {
                    SpiderDebug.log("proxy", "dead spider siteKey=%s: source removed or unknown, skip proxy", params.get("siteKey"));
                    return null;
                }
                rs = spider.proxy(params);
            } else if ("js".equals(params.get("do"))) {
                rs = jsLoader.proxy(params);
            } else if ("py".equals(params.get("do"))) {
                rs = pyLoader.proxy(params);
            } else {
                rs = jarLoader.proxy(params);
            }
        } catch (NullPointerException e) {
            SpiderDebug.log("proxy", "proxy result null conversion do=%s siteKey=%s: %s", params.get("do"), params.get("siteKey"), e);
            return null;
        } catch (Exception e) {
            // 已关闭对象报错走 Exception 族（IllegalStateException／PyException），
            // 这里只接 Exception：Error 不拦、不转 502，原样上抛。
            if (isClosedObject(e)) {
                SpiderDebug.log("proxy", "proxy spider closed do=%s siteKey=%s: %s", params.get("do"), params.get("siteKey"), e);
                // siteKey 路同型兜底：解析到的实例可能在途被重载顶替／销毁，
                // 本体健康的源不许就此回空——重新解析一次拿当前活蜘蛛重试
                // （loader 的 getSpider 已会摘死实例重建），仍旧关闭或解析
                // 不出活蜘蛛才按“代理上游已死”回 null。do=py 路的失效重取
                // 在 PyLoader.proxy 内自理，不在这里重复。
                if (params.containsKey("siteKey")) {
                    try {
                        Spider fresh = getSpider(params.get("siteKey"));
                        if (!(fresh instanceof SpiderNull)) return normalizeProxyResult(fresh.proxy(params));
                    } catch (Throwable retry) {
                        SpiderDebug.log("proxy", "proxy spider closed retry failed siteKey=%s: %s", params.get("siteKey"), retry);
                    }
                }
                return null;
            }
            throw e;
        }
        return normalizeProxyResult(rs);
    }

    /** 原因链里出现“已关闭对象”特征：Chaquopy 已关闭 PyObject 的访问报错。 */
    private static boolean isClosedObject(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.toLowerCase(java.util.Locale.ROOT).contains("is closed")) return true;
        }
        return false;
    }

    /** 逐字段归一：Response 直通原样返回；其余按 [状态码, mime, 数据流, 请求头] 归一。 */
    private static Object[] normalizeProxyResult(Object[] rs) {
        if (rs == null || rs.length == 0) return null;
        if (rs[0] instanceof fi.iki.elonen.NanoHTTPD.Response) return rs;
        if (rs.length < 3 || !(rs[0] instanceof Integer) || !(rs[2] instanceof java.io.InputStream)) return null;
        Object[] out = new Object[4];
        out[0] = rs[0];
        out[1] = rs[1] == null ? null : rs[1].toString();
        out[2] = rs[2];
        out[3] = rs.length > 3 && rs[3] instanceof Map ? rs[3] : null;
        return out;
    }

    // 上游原文口径（上游 BaseLoader.requireProxy :157、getProxyUri :166）：起播前
    // 对取到的地址做一次代理归属拉起——是 proxy:// 或本地 /proxy? 地址时，按
    // siteKey/do 找到对应运行时；非代理地址直接返回，不做任何事。
    // fork 适配两处：① 上游 requireRuntime 调 RuntimeModule.{PYTHON,QUICKJS}
    // .require()（外置包形态）；本树 py/js 运行时已内置（本类构造时即建 pyLoader/
    // jsLoader），故这两支是查表确认、不实例化蜘蛛、不抛外置包缺失错；② 上游无空串
    // 保护，本树 fetch 路可递 null（sanitizer 对 null 原样返回），故 null 直接返回。
    // 本方法只做归属确认，不实例化蜘蛛，避免在播放线程上拉起蜘蛛。
    public void requireProxy(String url) {
        if (url == null) return;
        Uri uri = getProxyUri(url);
        if (uri == null) return;
        String key = uri.getQueryParameter("siteKey");
        if (key != null) requireSite(key);
        // do=py/do=js：上游此处调 RuntimeModule.PYTHON/QUICKJS.require()，本树
        // 运行时已内置（本类构造时即建 pyLoader/jsLoader），无需 require，故不调。
    }

    private Uri getProxyUri(String url) {
        String prefix = Proxy.getUrl(true) + "?";
        if (url.startsWith("proxy://")) url = prefix + url.substring(8);
        return url.startsWith(prefix) ? Uri.parse(url) : null;
    }

    private void requireSite(String key) {
        Site site = VodConfig.get().getSite(key);
        Live live = LiveConfig.get().getLive(key);
        requireRuntime(!site.isEmpty() ? site.getApi() : live.getApi());
    }

    private void requireRuntime(String api) {
        if (api == null) return;
        if (isPy(api) || isJs(api)) return;
    }

    public void parseJar(String jar, boolean recent) {
        if (TextUtils.isEmpty(jar)) return;
        String key = Util.md5(jar);
        jarLoader.parseJar(key, jar);
        if (recent) jarLoader.setRecent(key);
    }

    public DexClassLoader dex(String jar) {
        return jarLoader.dex(jar);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    private static class Loader {
        static volatile BaseLoader INSTANCE = new BaseLoader();
    }
}
