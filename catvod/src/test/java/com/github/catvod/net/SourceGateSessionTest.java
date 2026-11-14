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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 在播会话绑定（1.4.134 真机实证）：播放器取流线程无线程打点、流媒体
 * host 多为未登记 CDN，近期回退窗口外必漏判直连。会话绑定是起播时按
 * 当前条目源归属写入的确定归属，咨询顺序为 线程打点 → host 认领 →
 * 会话绑定 → 近期回退：认领（登记归属）与会话（在播归属）都压过回退猜测。
 * <p>
 * 绑定按播放器（owner）各记一份（接缝普查毛病7）：主服务与短剧听书
 * 服务各持一个播放器，会在对方在播期间起播/停播；有效归属取在册绑定
 * 里最后绑定的一份，某台停播只删自家那份，余下绑定继续生效。
 */
public class SourceGateSessionTest {

    private static final int PORT = 7890;

    /** 模拟主服务的播放器。 */
    private final Object videoOwner = new Object();

    /** 模拟短剧听书服务的播放器。 */
    private final Object bookOwner = new Object();

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
        clearAllSessions();
    }

    @After
    public void tearDown() throws Exception {
        SourceGate.clear();
        clearAllSessions();
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

    /** 清空全部 owner 的绑定并复位有效归属缓存（测试收口，与 clearStaticMap 同法）。 */
    private static void clearAllSessions() throws Exception {
        clearStaticMap("SESSION_MARKS");
        Field f = SourceGate.class.getDeclaredField("sessionMark");
        f.setAccessible(true);
        f.set(null, null);
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
        SourceGate.bindSession(videoOwner, "py", "py_x");
        ProxySelector selector = SourceGate.wrapSelector(null);
        List<Proxy> out = selector.select(URI.create("https://video.example/v.mp4"));
        assertTrue("在播会话已启用时，取流应按会话归属走节点", proxiedViaGate(out));
    }

    @Test
    public void hostClaimBeatsSession() {
        enabled.add("py|py_x");
        claims.put("owned.example", "hk|OwnerOff");
        SourceGate.bindSession(videoOwner, "py", "py_x");
        ProxySelector selector = SourceGate.wrapSelector(null);
        List<Proxy> out = selector.select(URI.create("https://owned.example/search"));
        assertFalse("host 已登记归属且本人未启用时，会话绑定不得越过认领判走节点", proxiedViaGate(out));
    }

    @Test
    public void sessionBeatsFallbackGuess() {
        enabled.add("py|py_x");
        markRecentOnly("js", "OtherJs");
        SourceGate.bindSession(videoOwner, "py", "py_x");
        ProxySelector selector = SourceGate.wrapSelector(null);
        List<Proxy> out = selector.select(URI.create("https://free.example/v.mp4"));
        assertTrue("确定归属（会话）应压过回退猜测：猜测源未启用也不得把播放腿判直连", proxiedViaGate(out));
    }

    @Test
    public void clearSession_requiresMatchingValue() {
        enabled.add("py|py_x");
        SourceGate.bindSession(videoOwner, "py", "py_x");
        SourceGate.clearSession(videoOwner, "py", "other");
        assertNotNull("解绑值不一致时不得清掉在播会话", SourceGate.session());
        ProxySelector selector = SourceGate.wrapSelector(null);
        assertTrue(proxiedViaGate(selector.select(URI.create("https://video.example/v.mp4"))));
        SourceGate.clearSession(videoOwner, "py", "py_x");
        assertNull(SourceGate.session());
        assertFalse(proxiedViaGate(selector.select(URI.create("https://video.example/v.mp4"))));
    }

    /**
     * 毛病7 主序列（先听书后视频）：短剧听书先起播绑定，后主视频起播，
     * 视频停播时只许删自家那份——听书仍在播，它的归属必须自动回位，
     * 不许像旧单槽那样被视频的解绑一并清掉（听书续播不重走 start/parse、
     * 不会重绑，槽空即长期丢归属）。
     */
    @Test
    public void laterStopRestoresEarlierBinding_bookFirstThenVideo() {
        enabled.add("hk|RuleX");
        enabled.add("py|py_x");
        SourceGate.bindSession(bookOwner, "hk", "RuleX");
        SourceGate.bindSession(videoOwner, "py", "py_x");
        assertEquals("后绑定者为当前有效归属", "py", SourceGate.session().kind);
        SourceGate.clearSession(videoOwner, "py", "py_x");
        assertNotNull("视频停播不得把听书的绑定一并清掉", SourceGate.session());
        assertEquals("听书归属应自动回位", "hk", SourceGate.session().kind);
        ProxySelector selector = SourceGate.wrapSelector(null);
        assertTrue("视频停播后，后台听书的取流应按听书归属走节点",
                proxiedViaGate(selector.select(URI.create("https://cdn.example/book.mp3"))));
    }

    /** 毛病7 反向序列（先视频后听书）：听书停播后，仍在播的视频归属回位。 */
    @Test
    public void laterStopRestoresEarlierBinding_videoFirstThenBook() {
        enabled.add("hk|RuleX");
        enabled.add("py|py_x");
        SourceGate.bindSession(videoOwner, "py", "py_x");
        SourceGate.bindSession(bookOwner, "hk", "RuleX");
        assertEquals("后绑定者为当前有效归属", "hk", SourceGate.session().kind);
        SourceGate.clearSession(bookOwner, "hk", "RuleX");
        assertNotNull("听书停播不得把视频的绑定一并清掉", SourceGate.session());
        assertEquals("视频归属应自动回位", "py", SourceGate.session().kind);
        ProxySelector selector = SourceGate.wrapSelector(null);
        assertTrue("听书停播后，仍在播的视频取流应按视频归属走节点",
                proxiedViaGate(selector.select(URI.create("https://cdn.example/v.mp4"))));
    }

    /** 一台的解绑够不到另一台：拿对方的值解绑不动任何一份，解绑自家只删自家。 */
    @Test
    public void clearByOneOwnerNeverTouchesOther() {
        SourceGate.bindSession(videoOwner, "py", "py_x");
        SourceGate.bindSession(bookOwner, "hk", "RuleX");
        SourceGate.clearSession(videoOwner, "hk", "RuleX");
        assertEquals("拿对方的值解绑不得动对方的绑定", "hk", SourceGate.session().kind);
        SourceGate.clearSession(videoOwner, "py", "py_x");
        assertNotNull(SourceGate.session());
        assertEquals("解绑自家只删自家份，听书那份仍在", "hk", SourceGate.session().kind);
        SourceGate.clearSession(bookOwner, "hk", "RuleX");
        assertNull(SourceGate.session());
    }
}
