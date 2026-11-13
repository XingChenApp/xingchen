package com.fongmi.android.tv.clash;

import android.content.Context;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.SourceGate;

/**
 * app 侧把节点策略注册进 catvod 的 SourceGate（模块方向：app → catvod）。
 * 在 Startup（XingChenProxy.apply 同区域）调用一次。
 */
public final class ClashGate {

    private ClashGate() {
    }

    /**
     * WebView 嗅探会话专用的同步裁决口（唯一调用方是 CustomWebView 的嗅探 start）：
     * WebView 不经 OkHttp 选择器，装载前只能问这里。
     * <p>
     * 裁决与取数侧的逐源表（{@link SourceProxyPolicy#shouldProxy}）<b>不同口径</b>：
     * 嗅探不是源的取数流量，是用户当次点击触发、壳内浏览器对源页面的一次装载——
     * 它要渲染的就是源本身的页面。逐源表默认关，全部导出日志里 html 类源仅有的
     * 两条取数裁决留痕都是 not-enabled（该留痕只在总开关开、逐源未开时出现）：
     * 总开关开着，嗅探若与取数同口径照样恒回 0、恒直连——目标站（www.javrate.com）
     * 在他设备直连路径上跨版本恒 ERR_CONNECTION_RESET。上一版（c54bc5f81）把嗅探
     * 挂到逐源表上等于没装，他真机判「没修」与此逐项吻合。
     * <p>
     * 本口规则：内核在跑 且（节点总开关开 或 该源逐源表命中）→ 返回本地混合口，
     * 否则 0（直连）。逐源表命中单列保留，音乐类源「单源开即生效、不要求总开关」
     * 的既有语义经 shouldProxy 原样带入。裁决带 route 留痕，不重回黑盒。
     */
    public static int proxyPortFor(Context context, String kind, String id) {
        int port = 0;
        String route = "error";
        try {
            if (!MihomoManager.get().isRunning()) {
                route = "kernel-off";
            } else {
                final Context app = context.getApplicationContext();
                boolean perSource = SourceProxyPolicy.shouldProxy(app, kind, id);
                if (perSource || ClashSettings.isEnabled(app)) {
                    // getMixedPort 对非法存值钳回默认 7890，恒回合法口，无坏口一支。
                    port = ClashSettings.getMixedPort(app);
                    route = perSource ? "policy" : "master";
                } else {
                    route = "master-off";
                }
            }
        } catch (Throwable ignored) {
            port = 0;
            route = "error";
        }
        try {
            SpiderDebug.log("clash", "gate webview port=%s kind=%s id=%s route=%s", port, kind, id, route);
        } catch (Throwable ignored) {
        }
        return port;
    }

    public static void register(Context context) {
        final Context app = context.getApplicationContext();
        SourceGate.register(new SourceGate.Provider() {
            @Override
            public boolean enabled() {
                // 门控 = 内核在跑 且（总开关开 或 任一音乐源开了走节点）。
                // 音乐单源走节点不再要求总开关先开：发请求前按需拉起内核
                // （FreeJsBridge 的桥内拉起）；
                // PikPak 默认带上、只随总开关（总开关开时本门控已由第一项成立）；
                // 其它源维持原语义（总开关关 → 它们的按源开关不生效）。
                if (!MihomoManager.get().isRunning()) return false;
                return ClashSettings.isEnabled(app)
                        || SourceProxyPolicy.anyEnabled(app, SourceProxyPolicy.KIND_MUSIC);
            }

            @Override
            public int port() {
                return ClashSettings.getMixedPort(app);
            }

            @Override
            public boolean globalMode() {
                return ClashSettings.isGlobalMode(app);
            }

            @Override
            public boolean shouldProxy(String kind, String id) {
                return SourceProxyPolicy.shouldProxy(app, kind, id);
            }
        });
        // 阅读按 host 认领：漫画图片（Glide 线程）、听书音频（Exo 取流线程）无打点，
        // 按请求 host 反查图片/音频登记时的书源归属（ReadingProxyGate）。
        ReadingProxyGate.setPolicy(ReadingProxyPolicy.android(app));
        SourceGate.registerHostClaim(new SourceGate.HostClaim() {
            @Override
            public String claim(String host) {
                String r = ReadingProxyGate.claim(host);
                if (r == null) return null;
                try {
                    if (!MihomoManager.get().isRunning()
                            && !MihomoManager.get().ensureRunning(app)) return null;
                } catch (Throwable ignored) {
                    return null;
                }
                return r;
            }
        });
    }
}
