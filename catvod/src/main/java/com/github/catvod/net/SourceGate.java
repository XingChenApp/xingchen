package com.github.catvod.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按源代理决策层：catvod 侧的中立挂载点。
 * <p>
 * 模块方向约束：app 模块的 clash 包依赖 catvod，反向不行。
 * 这里只放纯静态的「策略提供者接口 + 线程级源归属标记 + 选择器包装」，
 * 具体策略（节点开关/混合口/全部走节点/按源走节点表）由 app 侧
 * 在启动时 register 进来（见 Startup 的 ClashGate.register）。
 * <p>
 * 决策语义：提供者为空或总开关未开 → 透传原链；
 * 回环地址永远直连；
 * 当前线程有源归属 → 按 shouldProxy(kind,id) 决定；
 * 无归属 → 按全部走节点开关决定；
 * 命中 → 返回 127.0.0.1:混合口 的 HTTP 代理。
 */
public final class SourceGate {

    /** 决策策略提供者（app 侧实现，启动时注册一次）。 */
    public interface Provider {
        /** 节点开关开且内核在跑。 */
        boolean enabled();

        /** 本地混合口。 */
        int port();

        /** 无源归属的流量是否走节点（「全部走节点」开关）。 */
        boolean globalMode();

        /** 某来源（kind=py/js/hk，id=源标识/规则名）是否走节点。 */
        boolean shouldProxy(String kind, String id);
    }

    /** 当前线程正在处理的来源归属。 */
    public static final class Mark {
        public final String kind;
        public final String id;

        Mark(String kind, String id) {
            this.kind = kind;
            this.id = id;
        }
    }

    private static volatile Provider provider;
    private static final ThreadLocal<Mark> HOLDER = new ThreadLocal<>();

    /**
     * 按 host 认领归属的钩子（app 侧注册）：播放器取流线程、Glide 线程这类
     * 无线程打点的请求，按请求 host 反查归属（如 Emby 服地址）。
     * 模块方向约束同 Provider：实现由 app 侧提供。
     * 返回 "kind|id"，无归属返回 null。实现里可做内核按需拉起，
     * 但必须保证：host 不命中时足够便宜（select 是热路径）。
     */
    public interface HostClaim {
        String claim(String host);
    }

    private static volatile HostClaim hostClaim;

    public static void registerHostClaim(HostClaim c) {
        hostClaim = c;
    }

    private SourceGate() {
    }

    public static void register(Provider p) {
        provider = p;
    }

    /** 打点：调用方必须在 finally 里配套 clear()。kind 为空不打点。 */
    public static void mark(String kind, String id) {
        if (kind == null || kind.isEmpty()) return;
        String safeId = id == null ? "" : id;
        HOLDER.set(new Mark(kind, safeId));
        RECENT.put(kind + "|" + safeId, System.currentTimeMillis());
    }

    /** 近期打点登记（给跨线程回退用）：key=kind|id，value=最近打点时刻。 */
    private static final ConcurrentHashMap<String, Long> RECENT = new ConcurrentHashMap<>();
    private static final long FALLBACK_WINDOW_MS = 15_000;

    /**
     * 无线程归属时的回退归属：蜘蛛自己开的 Python 线程（如域名竞速）回调 Java 时
     * 是新附着的线程、ThreadLocal 为空（1.3.22 真机实证 no-mark 漏直连）。
     * 只在窗口内恰好只有一个同类来源被打点过时才回退——宁可直连也不串源。
     */
    private static Mark fallbackMark(String kindWanted) {
        long now = System.currentTimeMillis();
        Mark found = null;
        for (java.util.Map.Entry<String, Long> e : RECENT.entrySet()) {
            if (now - e.getValue() > FALLBACK_WINDOW_MS) {
                RECENT.remove(e.getKey());
                continue;
            }
            int bar = e.getKey().indexOf('|');
            if (bar < 0) continue;
            String kind = e.getKey().substring(0, bar);
            if (kindWanted != null && !kindWanted.equals(kind)) continue;
            Mark m = new Mark(kind, e.getKey().substring(bar + 1));
            if (found != null && !(found.kind.equals(m.kind) && found.id.equals(m.id))) return null;
            found = m;
        }
        return found;
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 嵌套打点：返回打点前的旧归属，调用方在 finally 里配套 pop(prev) 恢复。
     * EmbyApi 内层打点用它，避免内层 clear() 把外层（如 SiteApi 的 vod 标记）一起吞掉。
     * 仅支持一层嵌套（现行调用链最深就是一层：SiteApi→EmbyApi）。
     */
    public static Mark push(String kind, String id) {
        Mark prev = HOLDER.get();
        mark(kind, id);
        return prev;
    }

    /** 恢复 push() 之前的归属：有旧值则恢复，无则清掉。 */
    public static void pop(Mark prev) {
        if (prev != null) HOLDER.set(prev);
        else HOLDER.remove();
    }

    public static Mark current() {
        return HOLDER.get();
    }

    /** 决策留痕节流：同一键 30 秒内只记一条（他发来的日志里此前没有任何定向决策记录，定不了案）。 */
    private static final ConcurrentHashMap<String, Long> LOG_AT = new ConcurrentHashMap<>();

    private static void logDecision(String key, String msg) {
        long now = System.currentTimeMillis();
        Long last = LOG_AT.get(key);
        if (last != null && now - last < 30_000) return;
        LOG_AT.put(key, now);
        try {
            com.github.catvod.crawler.SpiderDebug.log("clash", "%s", msg);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Python 取数口：py 蜘蛛在 chaquo 里用 requests 自己取数，不经 Java 网络栈，
     * 选择器管不到它——这正是 py 定向从来没通过的原因（1.3.16 日志里 py 调用
     * 一条 gate 决策记录都没有、超时形态与无内核时一模一样）。
     * base/spider.py 的 fetch/post 在发请求前问这里：当前线程的源标记命中策略
     * 就返回本地混合口，py 侧据此给 requests 传 proxies；否则返回 0 走直连。
     * 标记由 SiteApi.marked 设在调用蜘蛛的线程上，py 回调 Java 仍在同一线程，可见。
     */
    public static int proxyPortForCurrent() {
        Provider p = provider;
        Mark m = HOLDER.get();
        boolean viaFallback = false;
        if (m == null) {
            m = fallbackMark("py");
            viaFallback = m != null;
        }
        if (p == null) {
            if (m != null) logDecision("noprov|" + m.kind + "|" + m.id,
                    "gate direct reason=no-provider kind=" + m.kind + " id=" + m.id + " via=py");
            return 0;
        }
        if (!p.enabled()) {
            if (m != null) logDecision("disabled|" + m.kind + "|" + m.id,
                    "gate direct reason=disabled(开关关或内核没跑) kind=" + m.kind + " id=" + m.id + " via=py");
            return 0;
        }
        boolean hit = (m != null) ? p.shouldProxy(m.kind, m.id) : p.globalMode();
        if (!hit) {
            if (m != null) logDecision("notenabled|" + m.kind + "|" + m.id,
                    "gate direct reason=not-enabled kind=" + m.kind + " id=" + m.id + " via=py");
            else logDecision("nomark", "gate direct reason=no-mark via=py (调用线程无源归属，查打点口径)");
            return 0;
        }
        int port = p.port();
        if (port <= 0 || port > 65535) {
            logDecision("badport", "gate direct reason=bad-port port=" + port + " via=py");
            return 0;
        }
        logDecision("proxy|" + (m == null ? "global" : m.kind + "|" + m.id),
                "gate proxy kind=" + (m == null ? "-" : m.kind) + " id=" + (m == null ? "-" : m.id) + " port=" + port + " via=py" + (viaFallback ? "-fallback" : ""));
        return port;
    }

    /** 把原选择器包在外面：命中策略 → 本地口；否则透传原链。回环永远直连。 */
    public static ProxySelector wrapSelector(ProxySelector inner) {
        final ProxySelector shell = inner != null ? inner : ProxySelector.getDefault();
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                Provider p = provider;
                Mark m = HOLDER.get();
                String via = null;
                if (m == null) {
                    m = fallbackMark(null);
                    if (m != null) via = "fallback";
                }
                if (p == null) {
                    if (m != null) logDecision("noprov|" + m.kind + "|" + m.id,
                            "gate direct reason=no-provider kind=" + m.kind + " id=" + m.id);
                    return shell.select(uri);
                }
                if (isLoopback(uri)) return List.of(Proxy.NO_PROXY);
                // 无线程打点时按 host 认领归属（如 Emby 播放流/海报跑在 Exo/Glide 线程上）。
                // 认领实现里可做内核按需拉起，所以放在 enabled() 门控之前。
                if (m == null && hostClaim != null) {
                    m = claimHost(uri);
                    if (m != null) via = "host";
                }
                if (!p.enabled()) {
                    if (m != null) logDecision("disabled|" + m.kind + "|" + m.id,
                            "gate direct reason=disabled(开关关或内核没跑) kind=" + m.kind + " id=" + m.id + " host=" + hostOf(uri) + viaSuffix(via));
                    return shell.select(uri);
                }
                // 兜底认领越界守卫（1.4.24 真机实证）：回退打点（via=fallback）只是
                // 「近 15 秒内唯一活跃源」的猜测归属，排在 host 认领之前咨询，会把
                // 已登记归属他人的 host 抢走——missav.com 登记本人是 MissAV（其逐源
                // 开关没开、本场已判 not-enabled 直连），随后一发无打点请求却被回退
                // 猜成当时唯一活跃的 JAV Verse、判走节点，同一 host 两条相悖裁决并存。
                // 治本口径：规则本人对自家 host 的裁决优先于一切兜底认领——host 有
                // 登记归属、归属与回退猜测不同、且归属未启用时，本人未开就是未开，
                // 回退认领不得把该请求判给任何其它已启用来源走节点。归属与猜测相同、
                // 归属已启用、或 host 无登记归属时，维持现状不拦。
                if ("fallback".equals(via) && hostClaim != null) {
                    Mark owner = claimHost(uri);
                    if (owner != null
                            && !(owner.kind.equals(m.kind) && owner.id.equals(m.id))
                            && !p.shouldProxy(owner.kind, owner.id)) {
                        logDecision("owneroff|" + owner.kind + "|" + owner.id + "|" + hostOf(uri),
                                "gate direct reason=owner-not-enabled kind=" + owner.kind + " id=" + owner.id
                                        + " host=" + hostOf(uri) + " via=fallback claimant=" + m.kind + "|" + m.id);
                        return shell.select(uri);
                    }
                }
                boolean hit = (m != null) ? p.shouldProxy(m.kind, m.id) : p.globalMode();
                if (hit) {
                    int port = p.port();
                    if (port > 0 && port <= 65535) {
                        logDecision("proxy|" + (m == null ? "global" : m.kind + "|" + m.id),
                                "gate proxy kind=" + (m == null ? "-" : m.kind) + " id=" + (m == null ? "-" : m.id) + " port=" + port + " host=" + hostOf(uri) + viaSuffix(via));
                        return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", port)));
                    }
                    logDecision("badport", "gate direct reason=bad-port port=" + port);
                } else if (m != null) {
                    logDecision("notenabled|" + m.kind + "|" + m.id,
                            "gate direct reason=not-enabled kind=" + m.kind + " id=" + m.id + " host=" + hostOf(uri) + viaSuffix(via));
                } else {
                    // 无归属也记一条（带 host）：以前流/图的直连是黑盒，现在可查。
                    logDecision("nomark|" + hostOf(uri),
                            "gate direct reason=no-mark host=" + hostOf(uri) + " (调用线程无源归属，查打点口径)");
                }
                return shell.select(uri);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                shell.connectFailed(uri, sa, ioe);
            }
        };
    }

    private static boolean isLoopback(URI uri) {
        try {
            String host = uri.getHost();
            if (host == null) return false;
            String h = host.toLowerCase();
            return h.equals("localhost") || h.equals("::1") || h.equals("[::1]") || h.startsWith("127.") || h.equals("0.0.0.0");
        } catch (Exception e) {
            return false;
        }
    }

    /** 按 host 认领归属：host 归一化（小写去端口），认领结果 "kind|id" 转成 Mark。 */
    private static Mark claimHost(URI uri) {
        try {
            HostClaim c = hostClaim;
            if (c == null) return null;
            String host = hostOf(uri);
            if (host.isEmpty()) return null;
            String claimed = c.claim(host);
            if (claimed == null || claimed.isEmpty()) return null;
            int bar = claimed.indexOf('|');
            if (bar < 0) return null;
            String kind = claimed.substring(0, bar);
            if (kind.isEmpty()) return null;
            return new Mark(kind, claimed.substring(bar + 1));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String hostOf(URI uri) {
        try {
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    private static String viaSuffix(String via) {
        return via == null ? "" : " via=" + via;
    }
}
