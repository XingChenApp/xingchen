package com.fongmi.quickjs.method;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.fongmi.quickjs.bean.Req;
import com.fongmi.quickjs.utils.Connect;
import com.fongmi.quickjs.utils.Crypto;
import com.fongmi.quickjs.utils.JSUtil;
import com.fongmi.quickjs.utils.Parser;
import com.github.catvod.Proxy;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Trans;
import com.github.catvod.utils.UriUtil;
import com.orhanobut.logger.Logger;
import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSMethod;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class Global {

    private final Map<Integer, Timeout> timers;
    private final ExecutorService executor;
    private final AtomicInteger timerId;
    private final Parser parser;
    private final QuickJSContext ctx;
    private final Timer timer;
    // 本对象在蜘蛛执行器线程上构造（Spider.createFun 在 initializeJS 的提交内），
    // 该线程即 ctx 的属主线程；关机兜底路径上要 release 时先比对它，异线程不放
    // （freeValue 头一步查线程亲和，异线程 release 直接抛 QuickJSException）。
    private final Thread ownerThread;

    private volatile boolean destroyed;

    private Global(QuickJSContext ctx, ExecutorService executor) {
        this.ownerThread = Thread.currentThread();
        this.executor = executor;
        this.timerId = new AtomicInteger();
        this.timers = new ConcurrentHashMap<>();
        this.parser = new Parser();
        this.timer = new Timer("quickjs-timer", true);
        this.ctx = ctx;
        setProperty();
    }

    public static Global create(QuickJSContext ctx, ExecutorService executor) {
        return new Global(ctx, executor);
    }

    public void destroy() {
        destroyed = true;
        for (Timeout timeout : timers.values()) timeout.cancelAndRelease();
        timers.clear();
        timer.cancel();
    }

    private void setProperty() {
        // 全局包装只取一份走完注册：它是上下文内部缓存的同一份，只取来
        // 注册，绝不 release（放掉即打死全上下文共用包装，见 Spider
        // createCtx 注；批五 5eab204f4 误加、本注随撤销一并改回）。
        JSObject globalObj = ctx.getGlobalObject();
        for (Method method : getClass().getMethods()) {
            if (!method.isAnnotationPresent(JSMethod.class)) continue;
            globalObj.setProperty(method.getName(), args -> {
                try {
                    return method.invoke(this, args);
                } catch (Exception e) {
                    // JS 侧口径不变（仍回 null），但真因必须留痕：旧写法静默
                    // 吞掉，req/md5/aes 等方法坏掉时 JS 侧只见 null、无从排查。
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    SpiderDebug.log("quickjs", "js method %s failed error=%s", method.getName(), cause);
                    return null;
                }
            });
        }
        globalObj.setProperty("pd", args -> {
            if (args == null || args.length < 2) return "";
            return pd(String.valueOf(args[0]), String.valueOf(args[1]), args.length > 2 ? String.valueOf(args[2]) : "");
        });
    }

    private boolean submit(Runnable runnable) {
        try {
            if (destroyed) return false;
            if (executor.isShutdown()) return false;
            executor.submit(runnable);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Keep
    @JSMethod
    public String s2t(String text) {
        return Trans.s2t(false, text);
    }

    @Keep
    @JSMethod
    public String t2s(String text) {
        return Trans.t2s(false, text);
    }

    @Keep
    @JSMethod
    public Integer getPort() {
        return Proxy.getPort();
    }

    @Keep
    @JSMethod
    public String getProxy(Boolean local) {
        return Proxy.getUrl(local) + "?do=js";
    }

    @Keep
    @JSMethod
    public String js2Proxy(Boolean dynamic, Integer siteType, String siteKey, String url, JSObject headers) {
        return getProxy(!dynamic) + String.format("&from=catvod&siteType=%s&siteKey=%s&header=%s&url=%s", siteType, siteKey, URLEncoder.encode(headers.stringify()), URLEncoder.encode(url));
    }

    @Keep
    @JSMethod
    public Integer setTimeout(JSFunction func, Integer delay) {
        Timeout timeout = createTimeout(func);
        if (timeout == null) return 0;
        return schedule(timeout, delay) ? timeout.id : 0;
    }

    @Keep
    @JSMethod
    public Object clearTimeout(Integer id) {
        cancel(id);
        return null;
    }

    @Keep
    @JSMethod
    public JSObject _http(String url, JSObject options) {
        JSFunction complete = options.getJSFunction("complete");
        if (complete == null) return req(url, options);
        requestAsync(url, options, complete);
        return null;
    }

    @Keep
    @JSMethod
    public JSObject req(String url, JSObject options) {
        try {
            Req req = Req.objectFrom(options.stringify());
            Response res = Connect.to(url, req).execute();
            return Connect.success(ctx, req, res);
        } catch (Exception e) {
            return Connect.error(ctx, e);
        }
    }

    @Keep
    @JSMethod
    public String joinUrl(String parent, String child) {
        return UriUtil.resolve(parent, child);
    }

    public String pd(String html, String rule, String urlKey) {
        return parser.pdfh(html, rule, urlKey);
    }

    @Keep
    @JSMethod
    public String pdfh(String html, String rule) {
        return parser.pdfh(html, rule, "");
    }

    @Keep
    @JSMethod
    public JSArray pdfa(String html, String rule) {
        return JSUtil.toArray(ctx, parser.pdfa(html, rule));
    }

    @Keep
    @JSMethod
    public JSArray pdfl(String html, String rule, String texts, String urls, String urlKey) {
        return JSUtil.toArray(ctx, parser.pdfl(html, rule, texts, urls, urlKey));
    }

    @Keep
    @JSMethod
    public String md5X(String text) {
        String result = Crypto.md5(text);
        Logger.t("md5X").d("text:%s\nresult:\n%s", text, result);
        return result;
    }

    @Keep
    @JSMethod
    public String sha256X(String text) {
        String result = Crypto.sha256(text);
        Logger.t("sha256X").d("text:%s\nresult:\n%s", text, result);
        return result;
    }

    @Keep
    @JSMethod
    public String randomX(Integer size) {
        String result = Crypto.randomUrlSafe(size == null ? 0 : size);
        Logger.t("randomX").d("size:%s\nresult:\n%s", size, result);
        return result;
    }

    @Keep
    @JSMethod
    public String aesX(String mode, boolean encrypt, String input, boolean inBase64, String key, String iv, boolean outBase64) {
        String result = Crypto.aes(mode, encrypt, input, inBase64, key, iv, outBase64);
        Logger.t("aesX").d("mode:%s\nencrypt:%s\ninBase64:%s\noutBase64:%s\nkey:%s\niv:%s\ninput:\n%s\nresult:\n%s", mode, encrypt, inBase64, outBase64, key, iv, input, result);
        return result;
    }

    @Keep
    @JSMethod
    public String desX(String mode, boolean encrypt, String input, boolean inBase64, String key, String iv, boolean outBase64) {
        String result = Crypto.des(mode, encrypt, input, inBase64, key, iv, outBase64);
        Logger.t("desX").d("mode:%s\nencrypt:%s\ninBase64:%s\noutBase64:%s\nkey:%s\niv:%s\ninput:\n%s\nresult:\n%s", mode, encrypt, inBase64, outBase64, key, iv, input, result);
        return result;
    }

    @Keep
    @JSMethod
    public String rsaX(String mode, boolean pub, boolean encrypt, String input, boolean inBase64, String key, boolean outBase64) {
        String result = Crypto.rsa(mode, pub, encrypt, input, inBase64, key, outBase64);
        Logger.t("rsaX").d("mode:%s\npub:%s\nencrypt:%s\ninBase64:%s\noutBase64:%s\nkey:\n%s\ninput:\n%s\nresult:\n%s", mode, pub, encrypt, inBase64, outBase64, key, input, result);
        return result;
    }

    private Callback getCallback(JSFunction complete, Req req) {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response res) {
                completeSuccess(complete, req, res);
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                completeError(complete, e);
            }
        };
    }

    private void requestAsync(String url, JSObject options, JSFunction complete) {
        complete.hold();
        try {
            Req req = Req.objectFrom(options.stringify());
            Connect.to(url, req).enqueue(getCallback(complete, req));
        } catch (Throwable e) {
            completeError(complete, e);
        }
    }

    private void completeSuccess(JSFunction complete, Req req, Response res) {
        boolean posted = postCallback(complete, () -> complete.call(Connect.success(ctx, req, res)));
        if (!posted) res.close();
    }

    private void completeError(JSFunction complete, Throwable error) {
        postCallback(complete, () -> complete.call(Connect.error(ctx, error)));
    }

    private boolean postCallback(JSFunction callback, Runnable runnable) {
        boolean posted = submit(() -> callAndRelease(callback, runnable));
        // 投递失败＝执行器已关停：只在属主线程上才兜底 release（本方法也可能从
        // JS 线程的同步失败路进来）；在 OkHttp 回调线程上硬放会抛线程亲和异常，
        // 放弃释放、随 ctx 销毁回收。
        if (!posted && Thread.currentThread() == ownerThread) callback.release();
        return posted;
    }

    private void callAndRelease(JSFunction callback, Runnable runnable) {
        try {
            if (!destroyed) runnable.run();
        } finally {
            callback.release();
        }
    }

    private Timeout createTimeout(JSFunction func) {
        if (func == null || destroyed) return null;
        Timeout timeout = new Timeout(timerId.incrementAndGet(), func);
        timers.put(timeout.id, timeout);
        func.hold();
        return timeout;
    }

    private boolean schedule(Timeout timeout, Integer delay) {
        try {
            timer.schedule(timeout, getDelay(delay));
            return true;
        } catch (Throwable e) {
            cancel(timeout.id);
            return false;
        }
    }

    private int getDelay(Integer delay) {
        return Math.max(0, delay == null ? 0 : delay);
    }

    private void cancel(Integer id) {
        if (id == null) return;
        Timeout timeout = timers.remove(id);
        if (timeout != null) timeout.cancelAndRelease();
    }

    private class Timeout extends TimerTask {

        private final JSFunction func;
        private final int id;
        private volatile boolean canceled;
        private boolean released;

        private Timeout(int id, JSFunction func) {
            this.func = func;
            this.id = id;
        }

        @Override
        public void run() {
            if (submit(this::fire)) return;
            Global.this.cancel(id);
        }

        private void fire() {
            if (canceled) return;
            try {
                func.call();
            } finally {
                Global.this.cancel(id);
            }
        }

        private synchronized void cancelAndRelease() {
            canceled = true;
            cancel();
            release();
        }

        private synchronized void release() {
            if (released) return;
            released = true;
            // cancelAndRelease 也可能由定时器线程在投递失败时触发：异线程不放，
            // 理由同 postCallback 的兜底（随 ctx 销毁回收）。
            if (Thread.currentThread() == ownerThread) func.release();
        }
    }
}
