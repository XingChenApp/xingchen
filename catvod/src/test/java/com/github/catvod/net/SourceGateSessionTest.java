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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 在播会话绑定（1.4.134 真机实证）：播放器取流线程无线程打点、流媒体
 * host 多为未登记 CDN，近期回退窗口外必漏判直连。会话绑定是起播时按
 * 当前条目源归属写入的确定归属，咨询顺序为 线程打点 → host 认领 →
 * 会话绑定 → 近期回退：认领（登记归属）与会话（在播归属）都压过回退猜测。
 */
public class SourceGateSessionTest {

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
        clearSession();
    }

    @After
    public void tearDown() throws Exception {
        SourceGate.clear();
        clearSession();
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

    private static void clearSession() {
        SourceGate.Mark s = SourceGate.session();
        if (s != null) SourceGate.clearSession(s.kind, s.id);
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
    public void sessionProxies_whenNothingElseAttributes() {
        enabled.add("py|py_x");
        SourceGate.bindSession("py", "py_x");
        ProxySelector selector = SourceGate.wrapSelector(null);
        List<Proxy> out = selector.select(URI.create("https://video.example/v.mp4"));
        assertTrue("在播会话已启用时，取流应按会话归属走节点", proxiedViaGate(out));
    }

    @Test
    public void hostClaimBeatsSession() {
        enabled.add("py|py_x");
        claims.put("owned.example", "hk|OwnerOff");
        SourceGate.bindSession("py", "py_x");
        ProxySelector selector = SourceGate.wrapSelector(null);
        List<Proxy> out = selector.select(URI.create("https://owned.example/search"));
        assertFalse("host 已登记归属且本人未启用时，会话绑定不得越过认领判走节点", proxiedViaGate(out));
    }

    @Test
    public void sessionBeatsFallbackGuess() {
        enabled.add("py|py_x");
        markRecentOnly("js", "OtherJs");
        SourceGate.bindSession("py", "py_x");
        ProxySelector selector = SourceGate.wrapSelector(null);
        List<Proxy> out = selector.select(URI.create("https://free.example/v.mp4"));
        assertTrue("确定归属（会话）应压过回退猜测：猜测源未启用也不得把播放腿判直连", proxiedViaGate(out));
    }

    @Test
    public void clearSession_requiresMatchingValue() {
        enabled.add("py|py_x");
        SourceGate.bindSession("py", "py_x");
        SourceGate.clearSession("py", "other");
        assertNotNull("解绑值不一致时不得清掉在播会话", SourceGate.session());
        ProxySelector selector = SourceGate.wrapSelector(null);
        assertTrue(proxiedViaGate(selector.select(URI.create("https://video.example/v.mp4"))));
        SourceGate.clearSession("py", "py_x");
        assertNull(SourceGate.session());
        assertFalse(proxiedViaGate(selector.select(URI.create("https://video.example/v.mp4"))));
    }
}
