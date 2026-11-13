package com.fongmi.android.tv.api.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Util;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dalvik.system.DexClassLoader;
import sun.misc.Unsafe;

/**
 * jar 蜘蛛单只驱逐桩测（本地 JVM harness，与 HkEpisodeMergerTest 同法跑：
 * classpath 置前功能版 android.text.TextUtils 影子桩，其余用合树编译产物）。
 * 覆盖：同 jar 双蜘蛛隔离、不同 jar 隔离/错 jar no-op、未缓存 no-op、
 * 创建失败未缓存、destroy 抛异常吞掉、创建/驱逐竞态、
 * BaseLoader 分发回归（.py/.js/.wv/csp/未知）。
 * BaseLoader/PyLoader/JsLoader 用 Unsafe 免构造注入，避免在 JVM 上拉起
 * chaquo/quickjs 的原生构造；被测的 remove/removeSpider 逻辑本身是真代码。
 */
public class JarEvictionTest {

    private static final String JAR = "http://example.com/shared.jar;md5;0123456789abcdef";
    private static final String JAR2 = "http://example.com/other.jar;md5;fedcba9876543210";

    static class FakeSpider extends Spider {
        final AtomicInteger destroys = new AtomicInteger();
        volatile boolean throwOnDestroy;

        @Override
        public void destroy() {
            destroys.incrementAndGet();
            if (throwOnDestroy) throw new RuntimeException("destroy boom");
        }
    }

    // ---------- 反射/免构造小工具 ----------

    private static Unsafe unsafe() throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Spider> spidersOf(JarLoader loader) throws Exception {
        return get(loader, "spiders");
    }

    private static FakeSpider seed(JarLoader loader, String spKey) {
        FakeSpider fake = new FakeSpider();
        Spider got = loader.getOrCreateSpider(spKey, () -> fake);
        assertSame(fake, got);
        return fake;
    }

    /** 往共享载体表里塞哨兵对象（免构造分配），返回哨兵以便事后比身份。 */
    private static Object seedSentinel(JarLoader loader, String field, String jaKey, Class<?> type) throws Exception {
        Object sentinel = unsafe().allocateInstance(type);
        Map<String, Object> map = get(loader, field);
        map.put(jaKey, sentinel);
        return sentinel;
    }

    // ---------- JarLoader 单只驱逐 ----------

    @Test
    public void sameJarSiblingSurvives() throws Exception {
        JarLoader loader = new JarLoader();
        String h = Util.md5(JAR);
        FakeSpider a = seed(loader, h + "csp_A");
        FakeSpider b = seed(loader, h + "csp_B");
        Object loaderSentinel = seedSentinel(loader, "loaders", h, DexClassLoader.class);
        Class<?> proxyMethod = Class.forName("com.fongmi.android.tv.api.loader.JarLoader$ProxyMethod");
        Object methodSentinel = seedSentinel(loader, "methods", h, proxyMethod);
        set(loader, "recent", h);

        assertTrue(loader.removeSpider("csp_A", "csp_A", JAR, "detail-empty"));

        assertEquals(1, a.destroys.get());
        assertEquals(0, b.destroys.get());
        assertFalse(spidersOf(loader).containsKey(h + "csp_A"));
        assertSame(b, spidersOf(loader).get(h + "csp_B"));
        // 共享载体四件：loader/method 身份不变、recent 不变、创建锁条目保留
        assertSame(loaderSentinel, ((Map<?, ?>) get(loader, "loaders")).get(h));
        assertSame(methodSentinel, ((Map<?, ?>) get(loader, "methods")).get(h));
        assertEquals(h, get(loader, "recent"));
        assertTrue(((Map<?, ?>) get(loader, "locks")).containsKey("spider:" + h + "csp_A"));

        // B 再取：命中旧实例，工厂不跑
        AtomicInteger bFactory = new AtomicInteger();
        Spider bAgain = loader.getOrCreateSpider(h + "csp_B", () -> {
            bFactory.incrementAndGet();
            return new FakeSpider();
        });
        assertSame(b, bAgain);
        assertEquals(0, bFactory.get());

        // A 再取：只重建 A 自己，工厂跑一次，新实例与旧的不同且被缓存
        AtomicInteger aFactory = new AtomicInteger();
        FakeSpider aNew = new FakeSpider();
        Spider aAgain = loader.getOrCreateSpider(h + "csp_A", () -> {
            aFactory.incrementAndGet();
            return aNew;
        });
        assertSame(aNew, aAgain);
        assertNotSame(a, aAgain);
        assertEquals(1, aFactory.get());
        assertSame(aNew, spidersOf(loader).get(h + "csp_A"));
    }

    @Test
    public void differentJarIsolationAndWrongJarNoop() throws Exception {
        JarLoader loader = new JarLoader();
        String h1 = Util.md5(JAR);
        String h2 = Util.md5(JAR2);
        assertFalse(h1.equals(h2));
        FakeSpider a = seed(loader, h1 + "csp_A");
        FakeSpider b = seed(loader, h2 + "csp_B");
        Object loader2 = seedSentinel(loader, "loaders", h2, DexClassLoader.class);

        // 错 jar（键公式不同）→ no-op，A 不受影响
        assertFalse(loader.removeSpider("csp_A", "csp_A", JAR2, "detail-empty"));
        assertEquals(0, a.destroys.get());
        assertSame(a, spidersOf(loader).get(h1 + "csp_A"));

        // 对的 jar → 只摘 A，jar2 的 B 与载体不动
        assertTrue(loader.removeSpider("csp_A", "csp_A", JAR, "detail-empty"));
        assertEquals(1, a.destroys.get());
        assertEquals(0, b.destroys.get());
        assertSame(b, spidersOf(loader).get(h2 + "csp_B"));
        assertSame(loader2, ((Map<?, ?>) get(loader, "loaders")).get(h2));
    }

    @Test
    public void uncachedNoop() throws Exception {
        JarLoader loader = new JarLoader();
        assertFalse(loader.removeSpider("csp_Nope", "csp_Nope", JAR, "detail-empty"));
        assertFalse(loader.removeSpider(null, "csp_X", JAR, "detail-empty"));
        assertFalse(loader.removeSpider("", "csp_X", JAR, "detail-empty"));
        // jar 为 null 不许抛（Util.md5 空串口径 → 键退化为纯站点 key，照样 no-op）
        assertFalse(loader.removeSpider("csp_X", "csp_X", null, "detail-empty"));
        assertTrue(spidersOf(loader).isEmpty());
        assertTrue(((Map<?, ?>) get(loader, "loaders")).isEmpty());
        assertTrue(((Map<?, ?>) get(loader, "methods")).isEmpty());
        assertNull(get(loader, "recent"));
    }

    @Test
    public void createFailureNotCachedThenEvictFalse() throws Exception {
        JarLoader loader = new JarLoader();
        String h = Util.md5(JAR);
        Spider got = loader.getOrCreateSpider(h + "csp_F", SpiderNull::new);
        assertTrue(got instanceof SpiderNull);
        assertFalse(spidersOf(loader).containsKey(h + "csp_F"));
        // 没建起来时 hadSpider=false 是合理结果，不是新接口失效
        assertFalse(loader.removeSpider("csp_F", "csp_F", JAR, "detail-empty"));
    }

    @Test
    public void destroyThrowingIsSwallowed() throws Exception {
        JarLoader loader = new JarLoader();
        String h = Util.md5(JAR);
        FakeSpider a = seed(loader, h + "csp_A");
        a.throwOnDestroy = true;
        try {
            assertTrue(loader.removeSpider("csp_A", "csp_A", JAR, "detail-empty"));
        } catch (Throwable t) {
            fail("destroy exception must not escape removeSpider: " + t);
        }
        assertEquals(1, a.destroys.get());
        assertFalse(spidersOf(loader).containsKey(h + "csp_A"));
    }

    @Test
    public void createEvictRace() throws Exception {
        JarLoader loader = new JarLoader();
        String h = Util.md5(JAR);
        String spKey = h + "csp_R";
        FakeSpider created = new FakeSpider();
        CountDownLatch factoryEntered = new CountDownLatch(1);
        CountDownLatch releaseFactory = new CountDownLatch(1);
        AtomicReference<Spider> createResult = new AtomicReference<>();
        Thread creator = new Thread(() -> createResult.set(loader.getOrCreateSpider(spKey, () -> {
            factoryEntered.countDown();
            try {
                if (!releaseFactory.await(10, TimeUnit.SECONDS)) throw new RuntimeException("latch timeout");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return created;
        })));
        creator.start();
        assertTrue(factoryEntered.await(10, TimeUnit.SECONDS));

        AtomicReference<Boolean> evictResult = new AtomicReference<>();
        Thread evictor = new Thread(() -> evictResult.set(loader.removeSpider("csp_R", "csp_R", JAR, "detail-empty")));
        evictor.start();
        // 创建还攥着锁，驱逐必须在锁外等，不许先跑完
        Thread.sleep(300);
        assertTrue(evictor.isAlive());
        assertNull(evictResult.get());

        releaseFactory.countDown();
        creator.join(10000);
        evictor.join(10000);
        assertFalse(creator.isAlive());
        assertFalse(evictor.isAlive());
        assertSame(created, createResult.get());
        // 创建完成 → 被驱逐：实例恰好 destroy 一次，表里不留
        assertEquals(Boolean.TRUE, evictResult.get());
        assertEquals(1, created.destroys.get());
        assertFalse(spidersOf(loader).containsKey(spKey));

        // 之后再取：重新走工厂建新实例
        FakeSpider rebuilt = new FakeSpider();
        AtomicInteger factoryRuns = new AtomicInteger();
        Spider again = loader.getOrCreateSpider(spKey, () -> {
            factoryRuns.incrementAndGet();
            return rebuilt;
        });
        assertSame(rebuilt, again);
        assertEquals(1, factoryRuns.get());
    }

    // ---------- BaseLoader 统一分发回归 ----------

    @Test
    public void baseLoaderDispatch() throws Exception {
        Unsafe u = unsafe();
        BaseLoader base = (BaseLoader) u.allocateInstance(BaseLoader.class);
        JarLoader jarLoader = new JarLoader();
        PyLoader pyLoader = (PyLoader) u.allocateInstance(PyLoader.class);
        set(pyLoader, "spiders", new ConcurrentHashMap<String, Spider>());
        set(pyLoader, "fileStamps", new ConcurrentHashMap<String, Long>());
        set(pyLoader, "failMarks", new ConcurrentHashMap<String, Object>());
        set(pyLoader, "pathKeys", new ConcurrentHashMap<String, Object>());
        JsLoader jsLoader = (JsLoader) u.allocateInstance(JsLoader.class);
        set(jsLoader, "spiders", new ConcurrentHashMap<String, Spider>());
        set(jsLoader, "pathKeys", new ConcurrentHashMap<String, Object>());
        set(base, "jarLoader", jarLoader);
        set(base, "pyLoader", pyLoader);
        set(base, "jsLoader", jsLoader);

        String h = Util.md5(JAR);
        FakeSpider pyFake = new FakeSpider();
        FakeSpider jsFake = new FakeSpider();
        FakeSpider wvFake = new FakeSpider();
        Map<String, Spider> pySpiders = get(pyLoader, "spiders");
        Map<String, Spider> jsSpiders = get(jsLoader, "spiders");
        pySpiders.put("py_site", pyFake);
        jsSpiders.put("js_site", jsFake);
        jsSpiders.put("wv_site", wvFake);
        FakeSpider jarFake = seed(jarLoader, h + "csp_J");

        base.removeSpider("py_site", "http://x/a.py", "", "detail-empty");
        assertEquals(1, pyFake.destroys.get());
        assertFalse(pySpiders.containsKey("py_site"));
        assertEquals(0, jsFake.destroys.get());
        assertEquals(0, jarFake.destroys.get());

        base.removeSpider("js_site", "http://x/a.js", "", "detail-empty");
        assertEquals(1, jsFake.destroys.get());
        assertFalse(jsSpiders.containsKey("js_site"));
        assertEquals(0, wvFake.destroys.get());

        // .wv 必须进 JsLoader（旧 SiteApi 二分只认 .js、会误落 Py）
        base.removeSpider("wv_site", "http://x/a.wv", "", "detail-empty");
        assertEquals(1, wvFake.destroys.get());
        assertFalse(jsSpiders.containsKey("wv_site"));

        base.removeSpider("csp_J", "csp_J", JAR, "detail-empty");
        assertEquals(1, jarFake.destroys.get());
        assertFalse(spidersOf(jarLoader).containsKey(h + "csp_J"));

        // 未知类型与空 api：no-op、不许抛、不误伤
        base.removeSpider("csp_J", "http://x/a.txt", JAR, "detail-empty");
        base.removeSpider("csp_J", null, JAR, "detail-empty");
    }
}
