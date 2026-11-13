package androidx.media3.ui.danmaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.player.danmaku.live.LiveDanmakuStamp;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.junit.Test;

import sun.misc.Unsafe;

/**
 * 直播弹幕装配时间戳与渲染激活窗的对齐断言（douyu-danmaku-stops-trace.md 第三节）。
 *
 * <p>生产装配（PlayerManager.onLiveDanmakuBatch）把每条直播弹幕的时间戳打成
 * {@link LiveDanmakuStamp#timeMsAt(long)}（装配时刻的播放进度）；渲染层
 * DanmakuView 每帧以 copyRange(进度−{@link DanmakuView#MAX_ACTIVATION_WINDOW_MS},
 * 进度]（左开右闭）捞候选。本测试用真实 DanmakuRenderPool 的 copyRange 验证：
 * 进度 ≥3 秒时按新口径装配的条目落在窗内、旧口径恒 0 的条目形态被排除在窗外。
 *
 * <p>DanmakuRenderPool 的构造依赖 Looper/Painter（JVM 桩不可用），故用
 * Unsafe.allocateInstance 跳过构造、只反射注入 copyRange 真正读的 lock 与
 * poolChunks 两字段；分桶方式与生产 createAndMeasure 一致（按 item.timeMs 建桶）。
 */
public class LiveDanmakuTimestampWindowTest {

    private static Danmaku danmaku(String text, long timeMs) {
        return new Danmaku(text, timeMs, Danmaku.TYPE_SCROLL, 0xFFFFFF, 0f,
                Danmaku.POOL_NORMAL, "", 0L);
    }

    private static DanmakuRenderPool poolWith(Danmaku... items) throws Exception {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Unsafe unsafe = (Unsafe) theUnsafe.get(null);
        DanmakuRenderPool pool =
                (DanmakuRenderPool) unsafe.allocateInstance(DanmakuRenderPool.class);
        setField(pool, "lock", new Object());
        TreeMap<Long, List<DanmakuRenderItem>> chunk = new TreeMap<>();
        for (Danmaku item : items) {
            DanmakuRenderItem renderItem = new DanmakuRenderItem(item);
            chunk.computeIfAbsent(renderItem.timeMs, key -> new ArrayList<>()).add(renderItem);
        }
        List<TreeMap<Long, List<DanmakuRenderItem>>> chunks = new ArrayList<>();
        chunks.add(chunk);
        setField(pool, "poolChunks", chunks);
        return pool;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static List<DanmakuRenderItem> copyRange(
            DanmakuRenderPool pool, long fromMs, long toMs) {
        List<DanmakuRenderItem> out = new ArrayList<>();
        pool.copyRange(fromMs, toMs, out);
        return out;
    }

    @Test
    public void stampIsAssemblyPositionClampedAtZero() {
        assertEquals(10_000L, LiveDanmakuStamp.timeMsAt(10_000L));
        assertEquals(0L, LiveDanmakuStamp.timeMsAt(0L));
        assertEquals(0L, LiveDanmakuStamp.timeMsAt(-9_223_372_036_854_775L));
    }

    @Test
    public void assembledItemFallsInsideActivationWindowBeyondThreeSeconds() throws Exception {
        long positionMs = 10_000L; // 播放进度已过 3 秒窗
        Danmaku assembled = danmaku("live", LiveDanmakuStamp.timeMsAt(positionMs));
        DanmakuRenderPool pool = poolWith(assembled);
        // DanmakuView.activateNewItems 的原样窗口：(进度−3000, 进度]
        List<DanmakuRenderItem> candidates = copyRange(
                pool, positionMs - DanmakuView.MAX_ACTIVATION_WINDOW_MS, positionMs);
        assertEquals(1, candidates.size());
        assertEquals(positionMs, candidates.get(0).timeMs);
    }

    @Test
    public void assembledItemStaysActivatableUntilWindowPasses() throws Exception {
        long positionMs = 10_000L;
        DanmakuRenderPool pool = poolWith(danmaku("live", LiveDanmakuStamp.timeMsAt(positionMs)));
        long window = DanmakuView.MAX_ACTIVATION_WINDOW_MS;
        // 装配后近 3 秒内任一帧仍能捞到（左开右闭：fromMs = 本帧进度−窗长）
        long lateFrameMs = positionMs + window - 1;
        assertEquals(1, copyRange(pool, lateFrameMs - window, lateFrameMs).size());
        // 窗口整段滑过之后不再重复激活（与点播弹幕同一语义，直播侧另有 TTL 过期兜底）
        long pastFrameMs = positionMs + window + 1;
        assertTrue(copyRange(pool, pastFrameMs - window, pastFrameMs).isEmpty());
    }

    @Test
    public void legacyZeroStampDropsOutOfWindowAfterThreeSeconds() throws Exception {
        // 旧装配形态：时间戳恒 0（1.4.24 以前 PlayerManager 的写法）
        Danmaku legacy = danmaku("legacy", 0L);
        DanmakuRenderPool pool = poolWith(legacy);
        long window = DanmakuView.MAX_ACTIVATION_WINDOW_MS;
        // 起播头 3 秒内还能被捞到——与真机「出了几个」的形态一致
        assertEquals(1, copyRange(pool, 500L - window, 500L).size());
        // 进度一过 3 秒，0 时刻条目永远落在窗外：这正是断流主断点的离线复现
        assertTrue(copyRange(pool, 10_000L - window, 10_000L).isEmpty());
        assertTrue(copyRange(pool, 60_000L - window, 60_000L).isEmpty());
        assertFalse(copyRange(pool, 500L - window, 500L).isEmpty());
    }
}
