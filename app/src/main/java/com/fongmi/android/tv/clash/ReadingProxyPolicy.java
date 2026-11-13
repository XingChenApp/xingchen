package com.fongmi.android.tv.clash;

import android.content.Context;

/**
 * ReadingProxyGate 的 Android 策略实现：把 ClashSettings / MihomoManager /
 * SourceProxyPolicy（SharedPreferences）转成快照口径。
 * <p>
 * 判定语义与 SourceGate.Provider（ClashGate.register）完全一致，
 * 只是换成可离线断言的纯数据口径。
 */
public final class ReadingProxyPolicy {

    private ReadingProxyPolicy() {
    }

    public static ReadingProxyGate.Policy android(final Context context) {
        final Context app = context.getApplicationContext();
        return new ReadingProxyGate.Policy() {
            @Override
            public boolean kernelRunning() {
                try {
                    return MihomoManager.get().isRunning();
                } catch (Throwable ignored) {
                    return false;
                }
            }

            @Override
            public boolean totalOn() {
                try {
                    return ClashSettings.isEnabled(app);
                } catch (Throwable ignored) {
                    return false;
                }
            }

            @Override
            public boolean globalMode() {
                try {
                    return ClashSettings.isGlobalMode(app);
                } catch (Throwable ignored) {
                    return false;
                }
            }

            @Override
            public boolean flagOn(String kind, String id) {
                try {
                    return SourceProxyPolicy.isEnabled(app, kind, id);
                } catch (Throwable ignored) {
                    return false;
                }
            }

            @Override
            public int mixedPort() {
                try {
                    return ClashSettings.getMixedPort(app);
                } catch (Throwable ignored) {
                    return 0;
                }
            }
        };
    }
}
