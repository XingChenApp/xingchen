package com.github.catvod.net;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 兜底认领越界守卫（1.4.24 真机实证）：无线程打点的请求先被回退打点
 * （近 15 秒唯一活跃源）猜测归属，再轮到 host 认领——host 已登记归属某
 * 来源且该来源未启用时，回退猜测不得把它判给另一个已启用来源走节点。
 */
public class SourceGateFallbackOwnerTest {

    private static final int PORT = 7890;

    private final Set<String> enabled = new HashSet<>();
    private final Map<String, String> claims = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        clearStaticMap("RECENT");
        clearStaticMap("LOG_AT");
        enabled.clear();
        claims.clear();
        SourceGate.register(new SourceGate.Provider() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public int port() {
                return PORT;
            }

            @Override
            public boolean globalMode() {
                return false;
            }

            @Override
            public boolean shouldProxy(String kind, String id) {
                return enabled.contains(kind + "|" + id);
            }
        });
        SourceGate.registerHostClaim(claims::get);
        SourceGate.clear();
    }

    @After
    public void tearDown() throws Exception {
        SourceGate.clear();
        SourceGate.register(null);
        SourceGate.registerHostClaim(null);
        clearStaticMap("RECENT");
        clearStaticMap("LOG_AT");
    }

    private static void clearStaticMap(String name) throws Exception {
        Field f = SourceGate.class.getDeclaredField(name);
        f.setAccessible(true);
        ((Map<?, ?>) f.get(null)).clear();
    }

    /** 只打点不留线程标记：模拟回退窗口里唯一活跃源。 */
    private void markRecentOnly(String kind, String id) {
        SourceGate.mark(kind, id);
        SourceGate.clear();
    }

    private static boolean proxiedViaGate(List<Proxy> out) {
        for (Proxy p : out) {
            if (p.type() == Proxy.Type.HTTP && p.address() != null
                    && p.address().toString().contains(":" + PORT)) return true;
        }
        return false;
    }

    @Test
    public void ownerNotEnabled_blocksFallbackClaim() {
        enabled.add("hk|ActiveSource");
        claims.put("owned.example", "hk|OwnerOff");
        markRecentOnly("hk", "ActiveSource");
        ProxySelector selector = SourceGate.wrapSelector(null);
        List<Proxy> out = selector.select(URI.create("https://owned.example/search"));
        assertFalse("归属未启用时回退认领不得判走节点", proxiedViaGate(out));
    }

    @Test
    public void ownerEnabled_fallbackStillProxies() {
        enabled.add("hk|ActiveSource");
        enabled.add("hk|OwnerOn");
        claims.put("owned.example", "hk|OwnerOn");
        markRecentOnly("hk", "ActiveSource");
        ProxySelector selector = SourceGate.wrapSelector(null);
        List<Proxy> out = selector.select(URI.create("https://owned.example/search"));
        assertTrue("归属已启用时维持现状，回退认领照常判走节点", proxiedViaGate(out));
    }

    @Test
    public void noOwner_fallbackStillProxies() {
        enabled.add("hk|ActiveSource");
        markRecentOnly("hk", "ActiveSource");
        ProxySelector selector = SourceGate.wrapSelector(null);
        List<Proxy> out = selector.select(URI.create("https://free.example/search"));
        assertTrue("host 无登记归属时维持现状，回退认领照常判走节点", proxiedViaGate(out));
    }

    @Test
    public void ownerIsTheFallbackSource_itselfProxies() {
        enabled.add("hk|ActiveSource");
        claims.put("self.example", "hk|ActiveSource");
        markRecentOnly("hk", "ActiveSource");
        ProxySelector selector = SourceGate.wrapSelector(null);
        List<Proxy> out = selector.select(URI.create("https://self.example/search"));
        assertTrue("归属与回退猜测是同一来源时不拦", proxiedViaGate(out));
    }
}
