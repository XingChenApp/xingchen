package com.fongmi.chaquo;

import androidx.annotation.Keep;

import com.whl.quickjs.wrapper.QuickJSContext;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * py 新形态 self.js 适配器的 Java 宿主（对 FongMi com.github.catvod.utils.Js
 * 的同形件）：eval(源码, 选项 JSON) 在一条专用线程上求值、结果以 JSON 串回。
 * 咱家 QuickJS 是内置 :quickjs 模块（whl 包装），没有上游外置运行时的
 * QuickJsRuntime.evaluate 入口，故宿主在本模块内直建上下文：QuickJSContext
 * 是线程亲和的，上下文的建、用、毁全收在这条专用线程上，调用方阻塞等结果。
 * 求值语义：源码按全局 eval 求完成值、JSON.stringify 成串（与 Python 侧
 * JsAdapter 的 json.loads(返回值) 契约对齐）；undefined 回 "null"。
 * options 目前无消费方（上游 evaluate 的 options 语义未随仓公开），收下不用。
 */
public final class JsHost implements AutoCloseable {

    private static final long EVAL_TIMEOUT_SECONDS = 60;

    private final ExecutorService executor;
    private QuickJSContext ctx;
    private volatile boolean closed;

    public JsHost() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "chaquo-jshost");
            t.setDaemon(true);
            return t;
        });
    }

    @Keep
    public String eval(String source, String options) throws Exception {
        if (closed) throw new IllegalStateException("Script owner closed");
        Future<String> future = executor.submit(() -> evalInternal(source));
        try {
            return future.get(EVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("js eval timeout", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw e;
        }
    }

    private String evalInternal(String source) {
        if (ctx == null) ctx = QuickJSContext.create();
        // 全局包装是上下文内部缓存的同一份，只取来挂源码串，绝不 release
        // （放掉会把全上下文共用的包装打死，同树 QuickJS 纪律）。
        ctx.getGlobalObject().setProperty("__js_eval_source__", source);
        Object text = ctx.evaluate("JSON.stringify((0, eval)(__js_eval_source__))");
        ctx.evaluate("delete globalThis.__js_eval_source__");
        return text instanceof String ? (String) text : "null";
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            executor.submit(() -> {
                if (ctx != null) {
                    ctx.destroy();
                    ctx = null;
                }
                return null;
            }).get(3, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        } finally {
            executor.shutdown();
        }
    }
}
