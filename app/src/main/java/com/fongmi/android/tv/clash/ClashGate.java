package com.fongmi.android.tv.clash;

import android.content.Context;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.hk.HkDetailBridge;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.db.AppDatabase;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.SourceGate;

import java.util.concurrent.atomic.AtomicBoolean;

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

    /**
     * 播放会话归属解析：站点 key → {kind, id}，与取数侧打点同口径。
     * hk_ 直达 key 的 id 取规则名（key 拼法 hk_&lt;规则名&gt;，历史键带 @@@
     * 后缀时在分隔符处截断），与 HkRouter 播放分流的打点 (hk, 规则名) 对齐；
     * 其余 key 必须能在配置里查到站点才认——查不到（听书/朗读的合成 key、
     * 本地文件等）返回 null 不绑定，保住 host 认领与全局口径的原有裁决。
     */
    public static String[] playbackAttribution(String key) {
        if (key == null || key.isEmpty()) return null;
        if (key.startsWith(HkDetailBridge.KEY_PREFIX)) {
            String rest = key.substring(HkDetailBridge.KEY_PREFIX.length());
            int at = rest.indexOf(AppDatabase.SYMBOL);
            String title = at >= 0 ? rest.substring(0, at) : rest;
            if (title.isEmpty()) return null;
            return new String[]{SourceProxyPolicy.KIND_HK, title};
        }
        Site site = null;
        try {
            site = VodConfig.get().getSite(key);
        } catch (Throwable ignored) {
        }
        if (site == null) return null;
        String kind = SiteApi.proxyKindForKey(key);
        if (kind == null || kind.isEmpty()) return null;
        return new String[]{kind, key};
    }

    /**
     * 起播绑定：把当前条目的源归属绑进 SourceGate 会话层，播放器取流线程
     * 据此裁决（见 SourceGate.bindSession）。归属解析不出返回 null（不绑）。
     * 返回值原样留给调用方，停播时交回 {@link #unbindPlayback(String[])} 按值解绑。
     */
    public static String[] bindPlayback(Context context, String key) {
        String[] attr = playbackAttribution(key);
        if (attr == null) return null;
        try {
            Context app = context.getApplicationContext();
            // 与阅读 host 认领同口径：归属命中走节点而内核没跑时按需拉起——
            // 播放腿此前没有任何人为它拉起内核（取数侧有蜘蛛打点链、嗅探有
            // 装载口、阅读有认领口，唯独播放腿三样都没有）。
            // 拉起只能在后台线程：ensureRunning 同步阻塞（含最长约 10 秒健康
            // 检查，其自家注释明文要求后台调用），而本口在起播主线程路径上
            // （PlayerManager.start/parse 同栈带 UI 动作），同步拉会冻主线程
            // （复查账 P1）。照 MihomoManager.setEnabled 的树内现行形起后台
            // 线程拉起，主线程只登记会话绑定即返回。
            // 拉起未完成期间该源的取流请求按既有未就绪形态裁决：Provider
            // .enabled() 在内核没跑时为 false，SourceGate 透传原选择器直连
            // （留痕 reason=disabled、via=session）——与总开关关/内核没跑时
            // 全系统同形，窗口内不等待、不重试，内核起来后的请求自然命中。
            if (SourceProxyPolicy.shouldProxy(app, attr[0], attr[1])
                    && !MihomoManager.get().isRunning()) {
                startKernelInBackground(app);
            }
        } catch (Throwable ignored) {
        }
        SourceGate.bindSession(attr[0], attr[1]);
        return attr;
    }

    /** 播放腿按需拉起的单飞闸（照 KernelDownloader 防重口径）：一发在途时不重复起线程，在途那发的结果对后来者同样有效。 */
    private static final AtomicBoolean playbackStartInFlight = new AtomicBoolean(false);

    private static void startKernelInBackground(final Context app) {
        if (!playbackStartInFlight.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                MihomoManager.get().ensureRunning(app);
            } catch (Throwable ignored) {
            } finally {
                playbackStartInFlight.set(false);
            }
        }, "clash-playback-start").start();
    }

    /** 停播解绑：按绑定时的归属值解绑（值不一致不清，防旧会话收尾误清新会话）。 */
    public static void unbindPlayback(String[] attr) {
        if (attr == null || attr.length < 2) return;
        SourceGate.clearSession(attr[0], attr[1]);
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
