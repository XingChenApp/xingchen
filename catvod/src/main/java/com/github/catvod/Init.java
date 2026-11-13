package com.github.catvod;

import android.content.Context;

import java.lang.ref.WeakReference;

public class Init {

    private WeakReference<Context> context;

    private static Init get() {
        return Loader.INSTANCE;
    }

    public static void set(Context context) {
        get().context = new WeakReference<>(context);
    }

    public static Context context() {
        // 未 set 或 WeakReference 已被回收时回 null（调用方按无上下文
        // 处理），不许在这里先 NPE 把真因盖掉。
        WeakReference<Context> ref = get().context;
        return ref == null ? null : ref.get();
    }

    private static class Loader {
        static volatile Init INSTANCE = new Init();
    }
}
