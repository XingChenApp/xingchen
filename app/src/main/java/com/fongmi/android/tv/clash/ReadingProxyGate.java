package com.fongmi.android.tv.clash;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 阅读按 host 认领走节点。
 * <p>
 * 背景：漫画图片（Glide 线程）、听书音频（Exo 取流线程）没有线程打点，
 * 只开某书源「走节点」时选路认不出它们，只能直连。
 * 解法：各取数点把「图片/音频 URL 的 host」登记到本表，
 * SourceGate 的 HostClaim 按 host 反查归属，再走同一套 Provider 决策。
 * <p>
 * 冲突口径：同一 host 被不同 (kind,id) 登记时，保留先登记者（TTL 内），
 * 不覆盖——节点是超集，直连需求让位给走节点需求，宁可多走不可错直连。
 * <p>
 * 纯 java.util 实现（无 Android 依赖），可被离线回放脚手架直接编译断言。
 */
public final class ReadingProxyGate {

    private ReadingProxyGate() {
    }

    /** 策略快照：Android 实现见 ReadingProxyPolicy，回放用假实现。 */
    public interface Policy {
        /** 内核在跑。 */
        boolean kernelRunning();

        /** 节点总开关开。 */
        boolean totalOn();

        /** 「全部走节点」开。 */
        boolean globalMode();

        /** 某来源开了走节点。 */
        boolean flagOn(String kind, String id);

        /** 本地混合口。 */
        int mixedPort();
    }

    /** 路由判定结果。 */
    public enum Route {
        PROXY, DIRECT
    }

    private static final class Claim {
        final String kind;
        final String id;
        final long expiresAt;

        Claim(String kind, String id, long expiresAt) {
            this.kind = kind;
            this.id = id;
            this.expiresAt = expiresAt;
        }
    }

    /** 认领存活：10 分钟；登记上限 512（LRU 淘汰最旧）。 */
    static final long TTL_MS = 10 * 60 * 1000L;
    private static final int CAP = 512;

    private static final Map<String, Claim> TABLE = new LinkedHashMap<String, Claim>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Claim> eldest) {
            return size() > CAP;
        }
    };

    private static volatile Policy policy;

    /** ClashGate.register 里注入 Android 策略实现。 */
    public static void setPolicy(Policy p) {
        policy = p;
    }

    static Policy getPolicy() {
        return policy;
    }

    /** URL 取 host：小写、无端口；取不到返回 ""。 */
    public static String hostOf(String url) {
        try {
            if (url == null) return "";
            String u = url.trim();
            int s = u.indexOf("://");
            if (s < 0) return "";
            String rest = u.substring(s + 3);
            int e = rest.length();
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (c == '/' || c == '?' || c == '#') {
                    e = i;
                    break;
                }
            }
            String hostPort = rest.substring(0, e);
            String host;
            if (hostPort.startsWith("[")) {
                // IPv6 字面量：取括号内，不切端口
                int rb = hostPort.indexOf(']');
                host = rb > 0 ? hostPort.substring(1, rb) : hostPort;
            } else {
                int colon = hostPort.lastIndexOf(':');
                host = colon >= 0 ? hostPort.substring(0, colon) : hostPort;
            }
            host = host.trim().toLowerCase(Locale.ROOT);
            return host;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * 登记 host 归属。同一 host 在 TTL 内被不同 (kind,id) 登记时保留先登记者；
     * 相同则刷新过期时间。
     */
    public static synchronized void recordHost(String host, String kind, String id) {
        if (host == null || kind == null) return;
        host = host.trim().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || kind.isEmpty()) return;
        String safeId = id == null ? "" : id;
        long now = System.currentTimeMillis();
        Claim old = TABLE.get(host);
        if (old != null && now < old.expiresAt) {
            if (old.kind.equals(kind) && old.id.equals(safeId)) {
                TABLE.put(host, new Claim(kind, safeId, now + TTL_MS));
            }
            return;
        }
        TABLE.put(host, new Claim(kind, safeId, now + TTL_MS));
    }

    /** 登记一批 URL 的 host。 */
    public static void recordUrls(List<String> urls, String kind, String id) {
        if (urls == null || kind == null || kind.isEmpty()) return;
        for (String u : urls) {
            String h = hostOf(u);
            if (!h.isEmpty()) recordHost(h, kind, id);
        }
    }

    /** 登记单个 URL 的 host。 */
    public static void recordUrl(String url, String kind, String id) {
        String h = hostOf(url);
        if (!h.isEmpty()) recordHost(h, kind, id);
    }

    /**
     * HostClaim 用：host 命中 → "kind|id"；未命中/过期 → null。
     * 注意：返回归属不代表走节点，最终由 SourceGate 的 Provider 决策
     * （总开关、逐源开关、全部走节点）。
     */
    public static synchronized String claim(String host) {
        if (host == null) return null;
        host = host.trim().toLowerCase(Locale.ROOT);
        Claim c = TABLE.get(host);
        if (c == null) return null;
        if (System.currentTimeMillis() >= c.expiresAt) {
            TABLE.remove(host);
            return null;
        }
        return c.kind + "|" + c.id;
    }

    /**
     * 路由判定（阅读 kind：book/py/js/hk）。
     * <p>
     * 与 SourceGate.Provider/shouldProxy 同语义：
     * 内核没跑或总开关关 → 一律 DIRECT；
     * 有认领 → 看该来源的逐源开关；
     * 无认领 → 看「全部走节点」。
     */
    public static Route decide(Policy p, String kind, String id, boolean claimed) {
        if (p == null) return Route.DIRECT;
        if (!p.kernelRunning()) return Route.DIRECT;
        if (!p.totalOn()) return Route.DIRECT;
        if (claimed) {
            if (!p.totalOn()) return Route.DIRECT;
            return p.flagOn(kind, id) ? Route.PROXY : Route.DIRECT;
        }
        if (!p.totalOn()) return Route.DIRECT;
        return p.globalMode() ? Route.PROXY : Route.DIRECT;
    }

    /**
     * 按判定给代理：PROXY 且端口合法 → 127.0.0.1:混合口 的 HTTP 代理；
     * 否则 NO_PROXY。供不走选择器链的直连点（如章节缓存下载）手动选路。
     */
    public static Proxy proxyFor(Policy p, String kind, String id, boolean claimed) {
        if (decide(p, kind, id, claimed) != Route.PROXY) return Proxy.NO_PROXY;
        int port = p.mixedPort();
        if (port <= 0 || port > 65535) return Proxy.NO_PROXY;
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", port));
    }

    /** 用已注入的策略判（未注入 → DIRECT）。 */
    public static Route decide(String kind, String id, boolean claimed) {
        return decide(policy, kind, id, claimed);
    }

    /** 用已注入的策略选代理（未注入 → NO_PROXY）。 */
    public static Proxy proxyFor(String kind, String id, boolean claimed) {
        return proxyFor(policy, kind, id, claimed);
    }

    /** 当前登记数（回放断言/诊断用）。 */
    public static synchronized int size() {
        return TABLE.size();
    }

    /** 清空登记表（测试/源变更失效用）。 */
    public static synchronized void clear() {
        TABLE.clear();
    }

    /** 快照（诊断用）：host → kind|id。 */
    public static synchronized List<String> snapshot() {
        List<String> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Claim> e : TABLE.entrySet()) {
            if (now < e.getValue().expiresAt) out.add(e.getKey() + " -> " + e.getValue().kind + "|" + e.getValue().id);
        }
        return out;
    }
}
