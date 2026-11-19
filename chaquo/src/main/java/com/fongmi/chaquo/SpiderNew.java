package com.fongmi.chaquo;

import android.content.Context;

import androidx.annotation.Keep;

import com.chaquo.python.PyObject;
import com.github.catvod.net.Net;
import com.github.catvod.utils.Local;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * py 新形态桥（FongMi 5.8.9 chaquo Spider 的并行移植）：与老桥 Spider 并存，
 * 只服务 PyForm 判出的新形态源。桥接语义照新仓原文：init 前 bindNet 绑
 * net/local/js/dialog 四适配器、init 内先 prepare 备齐 getDependence 依赖；
 * 内容口入参在桥内解析成对象（extend/ids/vipFlags 经 JSON 文本过桥、Python
 * 侧 json.loads 成真对象）、返回只收 dict/list 由 Python 侧 dumps 成串。
 * 调用纪律沿咱家老桥：每蜘蛛一条专用线程串行执行、普通调用 60 秒闸、
 * init 单独 120 秒；PyObject 关法照老桥类头纪律——现造的 JSON 串返回值
 * 取值后即关，liveContent 与布尔单例不关，obj 只在 destroy 关。
 * danmaku(id, emit) 桥口照原文备齐，消费侧已接：app SiteApi.danmaku 解析
 * spider:// 地址后调本口，播放器 SpiderDanmakuSession 订阅并喂 DanmakuController。
 */
public class SpiderNew extends com.github.catvod.crawler.Spider {

    private static final Gson GSON = new Gson();
    private static final long TIMEOUT_SECONDS = 60;
    private static final long INIT_TIMEOUT_SECONDS = 120;

    private final PyObject app;
    private final PyObject obj;
    private final Net.Cookies cookies;
    private final UserAgent userAgent;
    private final Streams streams = new Streams();
    private final JsHost js = new JsHost();
    private final ExecutorService executor;
    private Net net;
    private Local local;
    private volatile boolean destroyed;

    public SpiderNew(PyObject app, PyObject obj, Net.Cookies cookies, Supplier<String> userAgent, Net net) {
        this.app = app;
        this.obj = obj;
        this.cookies = cookies;
        this.userAgent = new UserAgent(userAgent);
        this.net = net;
        this.executor = newCallExecutor();
    }

    private static ExecutorService newCallExecutor() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "chaquo-spider-new");
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
        invoke(() -> {
            initInternal(extend);
            return null;
        }, INIT_TIMEOUT_SECONDS);
    }

    private void initInternal(String extend) {
        if (net == null) net = new Net(cookies, siteKey);
        local = new Local(siteKey);
        try {
            obj.put("siteKey", siteKey);
            app.callAttr("bindNet", obj, net, userAgent, local, streams, js);
            app.callAttr("init", obj, extend);
        } catch (RuntimeException | Error e) {
            // 初始化失败（照新仓 Spider.init）：关资源＋释放运行时后原样抛，
            // 不走 destroy 钩子；destroyed 立旗，上层 destroy() 幂等跳过。
            destroyed = true;
            closeResources();
            try {
                app.callAttr("release", obj);
            } catch (RuntimeException | Error error) {
                if (e != error) e.addSuppressed(error);
            }
            throw e;
        }
    }

    private void closeResources() {
        try {
            js.close();
        } catch (Throwable ignored) {
        }
        try {
            streams.close();
        } catch (Throwable ignored) {
        }
        try {
            if (net != null) net.close();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String homeContent(boolean filter) {
        if (destroyed) return null;
        return invoke(() -> {
            if (destroyed) return null;
            try (PyObject r = app.callAttr("homeContent", obj, filter)) {
                return r == null ? null : r.toString();
            } catch (RuntimeException e) {
                if (isClosed(e)) return null;
                throw e;
            }
        });
    }

    @Override
    public String homeVideoContent() {
        if (destroyed) return null;
        return invoke(() -> {
            if (destroyed) return null;
            try (PyObject r = app.callAttr("homeVideoContent", obj)) {
                return r == null ? null : r.toString();
            } catch (RuntimeException e) {
                if (isClosed(e)) return null;
                throw e;
            }
        });
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        if (destroyed) return null;
        return invoke(() -> {
            if (destroyed) return null;
            try (PyObject r = app.callAttr("categoryContent", obj, tid, pg, filter, GSON.toJson(extend))) {
                return r == null ? null : r.toString();
            } catch (RuntimeException e) {
                if (isClosed(e)) return null;
                throw e;
            }
        });
    }

    @Override
    public String detailContent(List<String> ids) {
        if (destroyed) return null;
        return invoke(() -> {
            if (destroyed) return null;
            try (PyObject r = app.callAttr("detailContent", obj, GSON.toJson(ids))) {
                return r == null ? null : r.toString();
            } catch (RuntimeException e) {
                if (isClosed(e)) return null;
                throw e;
            }
        });
    }

    @Override
    public String searchContent(String key, boolean quick) {
        if (destroyed) return null;
        return invoke(() -> {
            if (destroyed) return null;
            try (PyObject r = app.callAttr("searchContent", obj, key, quick)) {
                return r == null ? null : r.toString();
            } catch (RuntimeException e) {
                if (isClosed(e)) return null;
                throw e;
            }
        });
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        if (destroyed) return null;
        return invoke(() -> {
            if (destroyed) return null;
            try (PyObject r = app.callAttr("searchContent", obj, key, quick, pg)) {
                return r == null ? null : r.toString();
            } catch (RuntimeException e) {
                if (isClosed(e)) return null;
                throw e;
            }
        });
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        if (destroyed) return null;
        return invoke(() -> {
            if (destroyed) return null;
            try (PyObject r = app.callAttr("playerContent", obj, flag, id, GSON.toJson(vipFlags))) {
                return r == null ? null : r.toString();
            } catch (RuntimeException e) {
                if (isClosed(e)) return null;
                throw e;
            }
        });
    }

    @Override
    public String liveContent(String url) {
        if (destroyed) return "";
        return invoke(() -> {
            if (destroyed) return "";
            try {
                // 返回值可能是蜘蛛的模块级常量字符串（共享包装），不关（老桥同口径）。
                PyObject r = app.callAttr("liveContent", obj, url);
                return r == null ? "" : r.toString();
            } catch (RuntimeException e) {
                if (isClosed(e)) return "";
                throw e;
            }
        });
    }

    @Override
    public boolean manualVideoCheck() {
        if (destroyed) return false;
        return invoke(() -> {
            if (destroyed) return false;
            try {
                // 返回值是 True/False 全局单例包装，不关。
                return app.callAttr("manualVideoCheck", obj).toBoolean();
            } catch (RuntimeException e) {
                if (isClosed(e)) return false;
                throw e;
            }
        });
    }

    @Override
    public boolean isVideoFormat(String url) {
        if (destroyed) return false;
        return invoke(() -> {
            if (destroyed) return false;
            try {
                return app.callAttr("isVideoFormat", obj, url).toBoolean();
            } catch (RuntimeException e) {
                if (isClosed(e)) return false;
                throw e;
            }
        });
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        if (destroyed) return null;
        return invoke(() -> proxyInternal(params));
    }

    private Object[] proxyInternal(Map<String, String> params) throws Exception {
        if (destroyed) return null;
        PyObject result = app.callAttr("proxy", obj, GSON.toJson(params));
        if (result == null) return null;
        try {
            List<PyObject> list = result.asList();
            try {
                if (list.size() < 3) throw new IllegalArgumentException("Proxy response requires status, MIME and body");
                InputStream stream = proxyBody(list.get(2));
                try {
                    boolean header = list.size() > 3 && list.get(3) != null;
                    return new Object[]{proxyCode(list.get(0)), proxyMime(list.get(1)), stream, header ? getHeader(list.get(3)) : null};
                } catch (RuntimeException | Error error) {
                    closeProxyStream(stream, error);
                    throw error;
                }
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
        return invoke(() -> {
            if (destroyed) return null;
            try (PyObject r = app.callAttr("action", obj, action)) {
                return r == null ? null : r.toString();
            } catch (RuntimeException e) {
                if (isClosed(e)) return null;
                throw e;
            }
        });
    }

    /**
     * 弹幕桥口（新仓 Spider.danmaku 原文形态）：返回的 AutoCloseable 是停止句柄，
     * override crawler 基类契约（基类默认抛 UnsupportedOperationException）。
     */
    @Override
    public AutoCloseable danmaku(String id, Consumer<JSONObject> emit) {
        if (destroyed) throw new IllegalStateException("spider destroyed");
        return invoke(() -> {
            Emitter emitter = new Emitter(emit);
            PyObject stop;
            try {
                stop = app.callAttr("danmaku", obj, id, emitter);
            } catch (RuntimeException | Error error) {
                emitter.close();
                throw error;
            }
            // stop 是 Python 侧现造的可调用对象，句柄持有到关闭时调用，之后由
            // 关闭方释放：句柄 lambda 捕获它，调用后关包装，不留 finalize 兜底。
            return (AutoCloseable) () -> {
                if (emitter.close()) {
                    try {
                        stop.call();
                    } finally {
                        closeQuietly(stop);
                    }
                }
            };
        });
    }

    @Override
    public synchronized void destroy() {
        if (destroyed) return;
        destroyed = true;
        try {
            // Python 侧 destroy 钩子（关 dialog＋蜘蛛 destroy＋运行时释放）走同一条
            // 专用队列与在途调用串行，只等 3 秒，不让卡死的调用拖住释放（老桥同口径）。
            executor.submit(() -> {
                try {
                    app.callAttr("destroy", obj);
                } catch (Exception ignored) {
                }
                return null;
            }).get(3, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        } finally {
            closeResources();
            closeQuietly(obj);
            executor.shutdown();
        }
    }

    private Map<String, String> getHeader(PyObject obj) {
        Map<String, String> header = new HashMap<>();
        for (Map.Entry<PyObject, PyObject> entry : obj.asMap().entrySet()) header.put(headerString(entry.getKey()), headerValue(entry.getValue()));
        return header;
    }

    private String headerString(PyObject value) {
        if (value == null) throw new IllegalArgumentException("Invalid proxy response header");
        return value.toJava(String.class);
    }

    private String headerValue(PyObject value) {
        try {
            return headerString(value);
        } catch (ClassCastException error) {
            String[] values = value.toJava(String[].class);
            for (String part : values) if (part == null) throw new IllegalArgumentException("Invalid proxy response header");
            return String.join(", ", values);
        }
    }

    private void closeProxyStream(InputStream stream, Throwable error) {
        try {
            stream.close();
        } catch (IOException | RuntimeException | Error failure) {
            if (failure != error) error.addSuppressed(failure);
        }
    }

    private int proxyCode(PyObject value) {
        if (value == null) throw new IllegalArgumentException("Proxy response status requires an integer from 100 to 599");
        int code = value.toInt();
        if (code < 100 || code > 599) throw new IllegalArgumentException("Proxy response status requires an integer from 100 to 599");
        return code;
    }

    private String proxyMime(PyObject value) {
        if (value == null) throw new IllegalArgumentException("Proxy response MIME requires a string");
        return value.toJava(String.class);
    }

    private InputStream proxyBody(PyObject body) {
        if (body == null) throw new IllegalArgumentException("Proxy body requires text, bytes or InputStream");
        Object value = body.toJava(Object.class);
        if (value instanceof InputStream stream) return stream;
        if (value instanceof byte[] bytes) return new ByteArrayInputStream(bytes);
        if (value instanceof String text) return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        throw new IllegalArgumentException("Proxy body requires text, bytes or InputStream");
    }

    @Keep
    public static final class Emitter {

        private final AtomicReference<Consumer<JSONObject>> listener;

        private Emitter(Consumer<JSONObject> listener) {
            this.listener = new AtomicReference<>(listener);
        }

        public void emit(String json) throws JSONException {
            Consumer<JSONObject> current = listener.get();
            if (current != null) current.accept(new JSONObject(json));
        }

        private boolean close() {
            return listener.getAndSet(null) != null;
        }
    }

    public static final class UserAgent {

        private final Supplier<String> supplier;

        private UserAgent(Supplier<String> supplier) {
            this.supplier = supplier;
        }

        @Keep
        public String getUserAgent() {
            return supplier == null ? "" : supplier.get();
        }
    }
}
