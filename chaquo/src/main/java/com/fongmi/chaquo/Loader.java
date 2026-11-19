package com.fongmi.chaquo;

import android.text.TextUtils;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.CookieStore;
import com.github.catvod.net.GlobalProxy;
import com.github.catvod.net.Net;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okhttp3.HttpUrl;
import okhttp3.Response;

public class Loader {

    // 蜘蛛创建（模块加载＋Spider 构造）同样没有原生超时：模块顶层代码卡死时
    // 创建调用会无限期挂住加载线程。创建走共享小池＋60 秒有界等待，卡死时
    // 调用方拿到超时错误而不是假死；池线程是守护线程，单只蜘蛛的创建卡死
    // 不占住后续创建（GIL 被 C 层攥死的情况除外，那种只有重启进程能救）。
    private static final ExecutorService CREATE_EXECUTOR = createExecutor();

    private static ExecutorService createExecutor() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(0, 4, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "chaquo-loader");
            t.setDaemon(true);
            return t;
        });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private final PyObject app;

    // 新形态桥模块（app2）：与 app 同为进程级模块对象，永不 close。导入失败
    // 只记日志、置 null，新形态分发自动关停，老路一字不受影响。
    private final PyObject app2;

    // 新形态 Net 的 Cookie 通道：接咱家 CookieStore（按 host 存取），与
    // FongMi PyLoader 传 JsHost 作 cookies 的位置等价（站点级 Cookie 共享）。
    private static final Net.Cookies COOKIES = new Net.Cookies() {
        @Override
        public String getCookie(String url) {
            HttpUrl parsed = url == null ? null : HttpUrl.parse(url);
            return parsed == null ? null : CookieStore.get(parsed.host());
        }

        @Override
        public boolean setCookie(String url, String cookie) {
            HttpUrl parsed = url == null ? null : HttpUrl.parse(url);
            if (parsed == null || cookie == null) return false;
            CookieStore.save(parsed, cookie);
            return true;
        }
    };

    public Loader() {
        if (!Python.isStarted()) Python.start(Platform.create());
        try {
            // 模块包装永不关（与 chaquo Spider 类头纪律统一）：getModule 经
            // 身份缓存返回全进程共享的同一个包装，close 关掉的是所有人手里
            // 的那一份（1.4.3 关 app 包装导致全 py 通杀，同一机理）。install
            // 返回的 None 也是全局单例包装，同样不关。
            Python.getInstance().getModule("webhtv_logging").callAttr("install");
        } catch (RuntimeException error) {
            SpiderDebug.log("python-spider", error);
        }
        // 按源定向的环境级补丁：给 requests/urllib 装上「发请求前问 SourceGate」的钩子。
        // 只靠 base/spider 的 fetch/post 覆盖不全——自己写 fetch 的蜘蛛（XVideos 用
        // Session、JavMenu 用 urllib）全绕过去，定向对它们从来没生效过（真机实证：
        // py 源跑了多轮，gate 的 via=py 决策记录一条都没有）。
        try {
            // 同上：模块包装永不关（见 chaquo Spider 类头纪律）。
            Python.getInstance().getModule("gate_patch").callAttr("install");
        } catch (RuntimeException error) {
            SpiderDebug.log("python-spider", error);
        }
        // Python 刚启动：把全局代理配置同步进 os.environ，供 requests 读取
        GlobalProxy.syncPython();
        // app 是进程级模块对象：随 Loader（PyLoader 持有）活到进程结束，永不 close。
        app = Python.getInstance().getModule("app");
        PyObject newApp = null;
        try {
            newApp = Python.getInstance().getModule("app2");
        } catch (RuntimeException error) {
            SpiderDebug.log("python-spider", error);
        }
        app2 = newApp;
    }

    // 进程级预热实例：App 冷启动用 warmUp() 提前把 Python 拉起来。这个实例
    // 必须被静态持有到进程结束——若像旧写法那样 new 完即弃，它的 app 包装
    // 对象就是全树最后一个靠 GC finalize 释放的 PyObject：量级虽小（单对象、
    // 启动空闲期），但机制与 finalize 超时崩溃同源，故不留例外。warmUp 只做
    // 预热，真正干活的 Loader 仍是 PyLoader 自己持有的那个，互不影响。
    private static Loader startup;

    public static synchronized void warmUp() {
        if (startup == null) startup = new Loader();
    }

    static void closeQuietly(PyObject o) {
        if (o == null) return;
        try {
            o.close();
        } catch (Throwable ignored) {
        }
    }

    public Spider spider(String api) {
        String name = scriptName(api);
        String source = source(api, name);
        return createOld(api, source, name);
    }

    /**
     * 带站点 key 的创建入口（PyLoader 唯一调用）：源码先按 PyForm 判形态——
     * 新形态走 app2 并行路（隔离运行时＋四适配器），老形态与 spider(api)
     * 完全同一条老路。判据只看源码特征（见 PyForm），老源永不误分。
     * 源码在此只作判据取样：新路按 FongMi 语义把 api 原串交 Python 侧
     * read_source 取数（远程走 Net 通道、本地读文件、内联直用），不复用这份。
     */
    public com.github.catvod.crawler.Spider spider(String api, String siteKey) {
        String name = scriptName(api);
        String source = source(api, name);
        if (app2 != null && PyForm.isNewForm(source)) return spiderNew(api, siteKey);
        return createOld(api, source, name);
    }

    private Spider createOld(String api, String source, String name) {
        PyObject obj;
        java.util.concurrent.Future<PyObject> create = CREATE_EXECUTOR.submit(() -> app.callAttr("spider", Path.py().getAbsolutePath(), source, name));
        try {
            obj = create.get(60, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 与下面 InterruptedException 分支同口径：等待方不等了，创建任务
            // 一并掐掉——不 cancel 的话任务跑完产出的 PyObject 无人接收、
            // 无人 close，只能靠 GC finalize 释放（1.4.1 同机理崩点）。
            create.cancel(true);
            throw new IllegalStateException("python spider create timeout: " + name, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            // 等待方被取消（顶替/销毁/外层超时）：创建任务一并掐掉，不留它跑完
            // 产出一个无人接、无人关的 PyObject 孤儿；中断旗照旧恢复，语义是
            // 「本次创建被取消」，由 PyLoader 按取消分类，不算加载失败。
            create.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return new Spider(app, obj, api);
    }

    // 新形态创建（照 FongMi Loader.spider 形态）：先建站点级 Net，再经 app2
    // 建隔离运行时；创建超时/取消/失败的分类口径与 createOld 完全一致，
    // 失败时照 FongMi releaseSpider 关 Net＋释放运行时，不留孤儿。
    private SpiderNew spiderNew(String api, String siteKey) {
        Net net = new Net(COOKIES, siteKey);
        java.util.concurrent.Future<PyObject> create = CREATE_EXECUTOR.submit(() -> app2.callAttr("spider", Path.py().getAbsolutePath(), api, siteKey, net));
        PyObject obj;
        try {
            obj = create.get(60, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            create.cancel(true);
            IllegalStateException failure = new IllegalStateException("python spider create timeout: " + api, e);
            releaseNew(net, null, failure);
            throw failure;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            RuntimeException failure = cause instanceof RuntimeException ? (RuntimeException) cause : new RuntimeException(cause);
            releaseNew(net, null, failure);
            throw failure;
        } catch (InterruptedException e) {
            create.cancel(true);
            Thread.currentThread().interrupt();
            RuntimeException failure = new RuntimeException(e);
            releaseNew(net, null, failure);
            throw failure;
        }
        SpiderNew spider = new SpiderNew(app2, obj, COOKIES, null, net);
        spider.siteKey = siteKey;
        return spider;
    }

    private void releaseNew(Net net, PyObject obj, Throwable failure) {
        try {
            net.close();
        } catch (RuntimeException | Error error) {
            if (error != failure) failure.addSuppressed(error);
        }
        try {
            if (obj != null) app2.callAttr("release", obj);
        } catch (RuntimeException | Error error) {
            if (error != failure) failure.addSuppressed(error);
        }
    }

    private String source(String api, String name) {
        if (api.startsWith("http")) {
            File cache = Path.py(name);
            try (Response response = OkHttp.newCall(OkHttp.client(15000), api).execute()) {
                if (!response.isSuccessful()) throw new IllegalStateException("HTTP " + response.code());
                String source = response.body().string();
                if (TextUtils.isEmpty(source)) throw new IllegalStateException("Empty python script");
                Path.write(cache, source.getBytes(StandardCharsets.UTF_8));
                return source;
            } catch (Exception e) {
                if (cache.exists() && cache.length() > 0) {
                    try {
                        byte[] bytes = java.nio.file.Files.readAllBytes(cache.toPath());
                        String cached = new String(bytes, StandardCharsets.UTF_8);
                        if (!TextUtils.isEmpty(cached)) return cached;
                    } catch (Exception ex) {
                    }
                }
                throw new IllegalStateException("Unable to download python script: " + api, e);
            }
        } else {
            try {
                java.io.File f = new java.io.File(api);
                if (f.exists() && f.isFile()) {
                    byte[] bytes = null;
                    try {
                        bytes = java.nio.file.Files.readAllBytes(f.toPath());
                    } catch (Exception readEx) {
                        // Files.readAllBytes 对中文路径可能失败，fallback 用 FileInputStream
                        java.io.FileInputStream fis = null;
                        try {
                            fis = new java.io.FileInputStream(f);
                            byte[] buf = new byte[(int) f.length()];
                            int offset = 0, n;
                            while (offset < buf.length && (n = fis.read(buf, offset, buf.length - offset)) != -1) offset += n;
                            bytes = buf;
                        } finally {
                            if (fis != null) try { fis.close(); } catch (Exception ignored) {}
                        }
                    }
                    return new String(bytes, StandardCharsets.UTF_8);
                }
                if (api.contains("class Spider") || api.contains("def homeContent") || api.contains("import ")) {
                    return api;
                }
                return api;
            } catch (Exception e) {
                // 详细日志：文件名、可读性、长度、异常链
                java.io.File f = new java.io.File(api);
                StringBuilder log = new StringBuilder("PY read failed: ").append(api)
                        .append(" | exists=").append(f.exists())
                        .append(" | isFile=").append(f.isFile())
                        .append(" | canRead=").append(f.canRead())
                        .append(" | length=").append(f.length());
                Throwable cause = e;
                while (cause != null) {
                    log.append(" | cause=").append(cause.getClass().getSimpleName()).append(":").append(cause.getMessage());
                    cause = cause.getCause();
                }
                SpiderDebug.log("python-spider", log.toString());
                throw new IllegalStateException("Unable to read python script: " + api, e);
            }
        }
    }

    private String scriptName(String api) {
        String lower = api.toLowerCase();
        String suffix = lower.contains(".py") ? ".py" : ".script";
        return Util.md5(api) + suffix;
    }
}
