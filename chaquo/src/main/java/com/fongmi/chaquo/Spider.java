package com.fongmi.chaquo;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.UriUtil;
import com.github.catvod.utils.Util;
import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Spider extends com.github.catvod.crawler.Spider {

    // 超时口径（S8-05）：Chaquopy 调用没有原生超时，运行中的 Python 调用无法
    // 从 Java 侧抢占——GIL 被 C 层死循环攥住时只有重启进程能救，这一点不假装
    // 能解。能落地的是把爆炸半径收住：每个蜘蛛的全部调用改走自己的一条
    // 专用线程串行执行，调用方最多等 TIMEOUT_SECONDS 就拿到超时错误返回，
    // 不再无限期占用壳子的加载/搜索/播放线程；卡住的只是这只蜘蛛自己的
    // 队列（网络型挂死在 socket 上会释放 GIL，纯 Python 循环有 GIL 时间片，
    // 其它蜘蛛仍能跑）。超时后任务不强杀：它可能稍后自己返回，队列随后恢复。
    private static final long TIMEOUT_SECONDS = 60;

    // init 单独放宽（与普通调用分闸）：市面源有把「拉域名列表＋逐个域名试活＋
    // 注册」全塞进 init 的（如萝莉岛：现行列表 25 个域名串行试活，实测合法
    // init 就要约 50-60 秒），与普通调用共用 60 秒闸时用户亲手点开也必然
    // 超时、源永远开不出来。创建期 init 这一路单独放宽到 120 秒，普通调用
    // 维持 60 秒一字不动。代价只是真死的源在创建时多等一倍才报错；后台
    // 预热反复重试的烧时靠 PyLoader 的失败退避堵，不靠收紧这里解决。
    private static final long INIT_TIMEOUT_SECONDS = 120;

    // setHomeGeneration 是静态模块调用，没有单只蜘蛛的队列可借（FIX-H23
    // 收尾）：单独一条守护线程串行执行＋有界等待，与 invoke 同口径，不许
    // 调用方（壳子的加载/作废线程）被一次卡死的 Python 调用无限期挂住。
    // 代次在 Python 侧有只进不退的守卫，等待超时放弃的旧值迟到执行也会
    // 被忽略；下一次推送带更新的代次，不丢最终状态。
    private static final ExecutorService HOME_GEN_EXECUTOR = newHomeGenExecutor();
    private static final long HOME_GEN_WAIT_SECONDS = 5;

    private static ExecutorService newHomeGenExecutor() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "chaquo-homegen");
            t.setDaemon(true);
            return t;
        });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private final PyObject app;
    private final PyObject obj;
    private final String api;
    private final Gson gson;
    private final ExecutorService executor;
    private volatile boolean destroyed;

    public Spider(PyObject app, PyObject obj, String api) {
        this.gson = new Gson();
        this.app = app;
        this.obj = obj;
        this.api = api;
        this.executor = newCallExecutor();
    }

    private static ExecutorService newCallExecutor() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "chaquo-spider");
            t.setDaemon(true);
            return t;
        });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private <T> T invoke(Callable<T> task) {
        return invoke(task, TIMEOUT_SECONDS);
    }

    private <T> T invoke(Callable<T> task, long timeoutSeconds) {
        Future<T> future = executor.submit(task);
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("python call timeout", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new RuntimeException(e);
        }
    }

    // Chaquopy 纪律（2026-10-21 修订）：先分清哪些 PyObject 能关、哪些不能。
    // Chaquopy 按 Python 对象身份缓存 Java 包装（PyObject.getInstance 的静态
    // cache，成品 dex 反编译实证）：同一个 Python 对象全进程只有一个 Java
    // 包装实例，close() 会把这个共享实例 closeNative 并将 addr 清零——关掉的
    // 不是「你这一份」，是所有人手里的同一份。
    // 【能关】本次调用现造、调用方独占的返回值：json.dumps 新串、现造的
    // list/dict 及其元素。这类若不关，全靠 GC finalize 兜底，FinalizerDaemon
    // 抢不到 GIL 时 10 秒超时把进程带崩（1.4.1 华为机真机实证），必须转成
    // Java 值后立即关。
    // 【永不关】全局单例与进程级对象的包装：模块对象（getModule("app") 与
    // Loader.app 是同一个实例）、None / True / False、type() 返回的类型对象，
    // 以及来源不可控的返回值（如 liveContent，可能是蜘蛛的模块级常量）。
    // 1.4.2 曾把 setHomeGeneration 里 getModule("app") 的包装顺手关掉，等于
    // 关死 Loader.app：此后全进程每个 py 源加载都报 ValueError: PyObject is
    // closed（1.4.3 真机实证，全 py 通杀）。obj 是本实例的蜘蛛对象，只在
    // destroy() 里关。

    private static void closeQuietly(PyObject o) {
        if (o == null) return;
        try {
            o.close();
        } catch (Throwable ignored) {
        }
    }

    private static boolean isClosed(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.toLowerCase(java.util.Locale.ROOT).contains("is closed")) return true;
        }
        return false;
    }

    /** 存活查询（给 PyLoader 的分发前校验与失效重取用）：destroy() 执行后恒为 true。 */
    public boolean isDestroyed() {
        return destroyed;
    }

    @Override
    public void init(Context context, String extend) {
        // 全树唯一走 120 秒闸的调用点：精确圈定创建期 init 这一次调用。
        invoke(() -> {
            initInternal(extend);
            return null;
        }, INIT_TIMEOUT_SECONDS);
    }

    private void initInternal(String extend) {
        PyObject dependence = app.callAttr("getDependence", obj);
        try {
            if (dependence != null) {
                List<PyObject> items = dependence.asList();
                try {
                    for (PyObject item : items) download(item + ".py");
                } finally {
                    for (PyObject item : items) closeQuietly(item);
                }
            }
        } finally {
            closeQuietly(dependence);
        }
        obj.put("siteKey", siteKey);
        // init 返回 None，是全局单例包装，不关（见类头纪律）。
        app.callAttr("init", obj, extend);
    }

    @Override
    public String homeContent(boolean filter) {
        return homeContent(filter, -1);
    }

    /**
     * 首页代次：SiteApi 首页加载把当前代次带进来；Python 透传层（app.py）发现代次
     * 过期直接返回空 JSON，不再进脚本。gen 传 -1 表示不校验（兼容老路径）。
     */
    public String homeContent(boolean filter, int homeGen) {
        if (destroyed) return null;
        return invoke(() -> homeContentInternal(filter, homeGen));
    }

    private String homeContentInternal(boolean filter, int homeGen) {
        if (destroyed) return null;
        try (PyObject r = app.callAttr("homeContent", obj, filter, homeGen)) {
            return r == null ? null : r.toString();
        } catch (RuntimeException e) {
            if (isClosed(e)) return null;
            throw e;
        }
    }

    @Override
    public String homeVideoContent() {
        return homeVideoContent(-1);
    }

    public String homeVideoContent(int homeGen) {
        if (destroyed) return null;
        return invoke(() -> homeVideoContentInternal(homeGen));
    }

    private String homeVideoContentInternal(int homeGen) {
        if (destroyed) return null;
        try (PyObject r = app.callAttr("homeVideoContent", obj, homeGen)) {
            return r == null ? null : r.toString();
        } catch (RuntimeException e) {
            if (isClosed(e)) return null;
            throw e;
        }
    }

    /**
     * 推送首页代次到 Python 透传层。由 SiteApi 的批次认领（claimHomeLoadBatch，
     * 新加载顶替旧加载时）与配置切换作废（invalidateHomeLoads）调用，代次只进
     * 不退；Python 未启动时跳过（此时也不可能有在途 py 加载）。调用走
     * HOME_GEN_EXECUTOR 串行＋有界等待（见该字段注释），不直调。
     */
    public static void setHomeGeneration(int gen) {
        try {
            if (!Python.isStarted()) return;
            HOME_GEN_EXECUTOR.submit(() -> {
                try {
                    // 两个 PyObject 都不许关：getModule("app") 经身份缓存返回的与
                    // Loader.app 是同一个实例，关掉即关死全进程的 app 通道（1.4.3 全
                    // py 通杀点）；callAttr 返回值是 None 单例包装，同理。见类头纪律。
                    Python.getInstance().getModule("app").callAttr("set_home_generation", gen);
                } catch (Throwable ignored) {
                }
                return null;
            }).get(HOME_GEN_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // 等待被自家取消机制打断＝本次推送被取消：恢复中断旗，不报错
            // （与 invoke 的中断分类同口径；排队中的任务迟到执行也无妨，
            // Python 侧代次只进不退）。
            Thread.currentThread().interrupt();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        if (destroyed) return null;
        return invoke(() -> categoryContentInternal(tid, pg, filter, extend));
    }

    private String categoryContentInternal(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        if (destroyed) return null;
        try (PyObject r = app.callAttr("categoryContent", obj, tid, pg, filter, gson.toJson(extend))) {
            return r == null ? null : r.toString();
        } catch (RuntimeException e) {
            if (isClosed(e)) return null;
            throw e;
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        if (destroyed) return null;
        return invoke(() -> detailContentInternal(ids));
    }

    private String detailContentInternal(List<String> ids) {
        if (destroyed) return null;
        try (PyObject r = app.callAttr("detailContent", obj, gson.toJson(ids))) {
            return r == null ? null : r.toString();
        } catch (RuntimeException e) {
            if (isClosed(e)) return null;
            throw e;
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        if (destroyed) return null;
        return invoke(() -> searchContentInternal(key, quick));
    }

    private String searchContentInternal(String key, boolean quick) {
        if (destroyed) return null;
        try (PyObject r = app.callAttr("searchContent", obj, key, quick)) {
            return r == null ? null : r.toString();
        } catch (RuntimeException e) {
            if (isClosed(e)) return null;
            throw e;
        }
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        if (destroyed) return null;
        return invoke(() -> searchContentInternal(key, quick, pg));
    }

    private String searchContentInternal(String key, boolean quick, String pg) {
        if (destroyed) return null;
        try (PyObject r = app.callAttr("searchContent", obj, key, quick, pg)) {
            return r == null ? null : r.toString();
        } catch (RuntimeException e) {
            if (isClosed(e)) return null;
            throw e;
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        if (destroyed) return null;
        return invoke(() -> playerContentInternal(flag, id, vipFlags));
    }

    private String playerContentInternal(String flag, String id, List<String> vipFlags) {
        if (destroyed) return null;
        try (PyObject r = app.callAttr("playerContent", obj, flag, id, gson.toJson(vipFlags))) {
            return r == null ? null : r.toString();
        } catch (RuntimeException e) {
            if (isClosed(e)) return null;
            throw e;
        }
    }

    @Override
    public String liveContent(String url) {
        if (destroyed) return "";
        return invoke(() -> liveContentInternal(url));
    }

    private String liveContentInternal(String url) {
        if (destroyed) return "";
        try {
            // 返回值由蜘蛛自备，可能是模块级常量字符串（共享包装），不关（见类头纪律）。
            PyObject r = app.callAttr("liveContent", obj, url);
            return r == null ? "" : r.toString();
        } catch (RuntimeException e) {
            if (isClosed(e)) return "";
            throw e;
        }
    }

    @Override
    public boolean manualVideoCheck() {
        if (destroyed) return false;
        return invoke(this::manualVideoCheckInternal);
    }

    private boolean manualVideoCheckInternal() {
        if (destroyed) return false;
        try {
            // 返回值是 True/False 全局单例包装，不关（见类头纪律）。
            return app.callAttr("manualVideoCheck", obj).toBoolean();
        } catch (RuntimeException e) {
            if (isClosed(e)) return false;
            throw e;
        }
    }

    @Override
    public boolean isVideoFormat(String url) {
        if (destroyed) return false;
        return invoke(() -> isVideoFormatInternal(url));
    }

    private boolean isVideoFormatInternal(String url) {
        if (destroyed) return false;
        try {
            // 返回值是 True/False 全局单例包装，不关（见类头纪律）。
            return app.callAttr("isVideoFormat", obj, url).toBoolean();
        } catch (RuntimeException e) {
            if (isClosed(e)) return false;
            throw e;
        }
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        if (destroyed) return null;
        return invoke(() -> proxyInternal(params));
    }

    private Object[] proxyInternal(Map<String, String> params) throws Exception {
        if (destroyed) return null;
        PyObject result;
        try {
            result = app.callAttr("localProxy", obj, gson.toJson(params));
        } catch (Exception e) {
            if (String.valueOf(e.getMessage()).contains("is closed")) return null;
            throw e;
        }
        if (result == null) return null;
        try {
            List<PyObject> list = result.asList();
            try {
                // 前三位（状态码/类型/流）是 localProxy 的契约下限：短于 3 的
                // 返回是坏结果，按「无代理结果」返回 null，不许 get(0/1/2)
                // 越界把异常打穿上层（上层按返回 null 处理无代理）。
                if (list.size() < 3) return null;
                boolean base64 = list.size() > 4 && list.get(4).toInt() == 1;
                boolean header = list.size() > 3 && list.get(3) != null;
                Object[] out = new Object[4];
                out[0] = list.get(0).toInt();
                out[1] = list.get(1).toString();
                out[2] = getStream(list.get(2), base64);
                out[3] = header ? getHeader(list.get(3)) : null;
                return out;
            } finally {
                for (PyObject item : list) closeQuietly(item);
            }
        } finally {
            closeQuietly(result);
        }
    }

    @Override
    public String action(String action) {
        if (destroyed) return null;
        return invoke(() -> actionInternal(action));
    }

    private String actionInternal(String action) {
        if (destroyed) return null;
        try (PyObject r = app.callAttr("action", obj, action)) {
            return r == null ? null : r.toString();
        } catch (RuntimeException e) {
            if (isClosed(e)) return null;
            throw e;
        }
    }

    @Override
    public void destroy() {
        // 先立已销毁标志：排队中与后续的调用入口先看到标志直接返回，
        // 不再拿将关的 obj 去调（与旧口径一致，只是提前到入口）。
        destroyed = true;
        try {
            // destroy 返回 None，是全局单例包装，不关（见类头纪律）；obj 本人关。
            // Python 侧 destroy 钩子走同一条专用队列与在途调用串行，但只等
            // 3 秒：在途调用真卡死时不等它，关 obj 的动作不能被拖住——
            // 卡死那次的 Python destroy 钩子会被跳过，属可接受代价。
            executor.submit(() -> {
                try {
                    app.callAttr("destroy", obj);
                } catch (Exception ignored) {
                }
                return null;
            }).get(3, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        } finally {
            // 蜘蛛被淘汰（文件变更/清缓存/删源）时确定性释放实例对象，
            // 不再把引用计数释放留给 GC finalize。
            closeQuietly(obj);
            executor.shutdown();
        }
    }

    private Map<String, String> getHeader(PyObject obj) {
        Map<PyObject, PyObject> map;
        try {
            map = obj.asMap();
        } catch (Exception e) {
            return null;
        }
        Map<String, String> header = new HashMap<>();
        boolean ok = true;
        try {
            for (Map.Entry<PyObject, PyObject> entry : map.entrySet())
                header.put(entry.getKey().toString(), entry.getValue().toString());
        } catch (Exception e) {
            ok = false;
        } finally {
            for (Map.Entry<PyObject, PyObject> entry : map.entrySet()) {
                closeQuietly(entry.getKey());
                closeQuietly(entry.getValue());
            }
        }
        return ok ? header : null;
    }

    private ByteArrayInputStream getStream(PyObject o, boolean base64) {
        if (o == null) return null;
        // type() 返回类型对象的全局单例包装，不关（见类头纪律）。
        boolean bytes = o.type().toString().contains("bytes");
        if (bytes) return new ByteArrayInputStream(o.toJava(byte[].class));
        String content = o.toString();
        if (base64 && content.contains("base64,")) content = content.split("base64,")[1];
        return new ByteArrayInputStream(base64 ? Util.decode(content) : content.getBytes());
    }

    /** 依赖缓存失效口：download() 记下的依赖名（主脚本与依赖同在 Path.py() 目录，
     * 不能整目录删，只清记名文件）。全清（PyLoader.clear）时调，单删插件不调——
     * 依赖按名多源共用，单删时不知道哪些依赖只属于被删插件，清掉会逼无关蜘蛛重下。 */
    private static final java.util.Set<String> DEP_NAMES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void clearDepCache() {
        for (String name : DEP_NAMES) {
            try {
                java.io.File f = Path.py(name);
                if (f.exists()) f.delete();
                java.io.File tmp = new java.io.File(f.getAbsolutePath() + ".tmp");
                if (tmp.exists()) tmp.delete();
            } catch (Throwable ignored) {
            }
        }
        DEP_NAMES.clear();
    }

    private void download(String name) {
        java.io.File file = Path.py(name);
        DEP_NAMES.add(name);
        String path = file.getAbsolutePath();
        String url = UriUtil.resolve(api, name);
        // download 返回状态串（skipped-length／skipped-hash／cached-offline／updated），
        // 是现造 str，取值后立即关；万一拿到 None 单例（旧 Python）不许关（见类头纪律）。
        PyObject result;
        try {
            result = app.callAttr("download", path, url);
        } catch (RuntimeException e) {
            // 依赖下载失败不许打穿 init：app.py 的 download 本就用状态串表达
            // 失败（cached-offline 等），抛错属同一类非致命失败——记下真因
            // 继续，依赖可能已在缓存里；真缺依赖会在用到它时再报错。
            SpiderDebug.log("python-spider", "dep download failed name=%s error=%s", name, e.getMessage());
            return;
        }
        String status = result == null ? "none" : result.toString();
        if (!"None".equals(status)) closeQuietly(result);
        SpiderDebug.log("python-spider", "dep %s name=%s", status, name);
    }
}
