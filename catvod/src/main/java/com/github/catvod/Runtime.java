package com.github.catvod;

import android.app.Activity;
import android.content.Context;

import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Runtime {

    private volatile Supplier<Activity> activity;
    private volatile Consumer<String> toast;
    private volatile BiFunction<String, Function<String, CompletableFuture<String>>, CompletableFuture<String>> dialog;
    private WeakReference<Context> context;

    private static Runtime get() {
        return Loader.INSTANCE;
    }

    public static void set(Context context) {
        get().context = new WeakReference<>(context);
    }

    public static Context context() {
        // fork 适配（上游原文为 return get().context.get()）：上游 App 调 Runtime.set()，
        // 本树 App（App.java:94）只调 Init.set()、App.java 不在本批可动文件内，故 Runtime
        // 从未被 set；原文在此会空指针。未 set 时回落到本树同一 Application 上下文持有者
        // Init.context()，已 set 时仍以自有值为准。
        WeakReference<Context> ref = get().context;
        Context context = ref == null ? null : ref.get();
        return context == null ? Init.context() : context;
    }

    public static void setActivity(Supplier<Activity> activity) {
        get().activity = activity;
    }

    public static Activity activity() {
        Supplier<Activity> supplier = get().activity;
        Activity activity = supplier == null ? null : supplier.get();
        return activity == null || activity.isFinishing() || activity.isDestroyed() ? null : activity;
    }

    public static void setToast(Consumer<String> toast) {
        get().toast = toast;
    }

    public static void toast(String text) {
        // toast 消费方由壳子注入，未注入时静默跳过，不许 NPE。
        Consumer<String> toast = get().toast;
        if (toast != null) toast.accept(text);
    }

    // 对话框宿主注册口（FongMi 新仓原文移植，py 新形态 dialog 适配器的落点）：
    // 宿主由 App 侧注入；未注入时 dialog() 返回失败 future，与上游
    // 「Dialog requires a compatible App」口径一致。本树 App 侧宿主未落（记名），
    // 注册口先行，宿主接入时桥接零改动。
    public static void setDialog(BiFunction<String, Function<String, CompletableFuture<String>>, CompletableFuture<String>> dialog) {
        get().dialog = dialog;
    }

    public static CompletableFuture<String> dialog(String options, Function<String, CompletableFuture<String>> callback) {
        BiFunction<String, Function<String, CompletableFuture<String>>, CompletableFuture<String>> dialog = get().dialog;
        if (dialog == null) return com.github.catvod.utils.Task.failedFuture(new UnsupportedOperationException("Dialog requires a compatible App"));
        return dialog.apply(options, callback);
    }

    private static class Loader {
        static volatile Runtime INSTANCE = new Runtime();
    }
}
