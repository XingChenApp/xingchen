package com.fongmi.android.tv.player.danmaku.live;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.player.engine.PlaySpec;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 直播弹幕识别接缝（douyu-live-danmaku-noshow-trace.md 第二节）：快照必须从 PlaySpec 的
 * 真实构造路造——蜘蛛 playerContent 的 play 结果（无 list）经 PlaySpec.from 成 spec、
 * 详情页装配写入 detailVodId，再走 PlaySpec.liveDanmakuSnapshot() 进 Resolver。
 * 不许直接 new Snapshot 带 id 绕过接缝（第一步验收就是这么漏的）。
 * Result 用 bean 直构：Result.fromJson 依赖 App.gson()，其类初始化在 JVM 单测里摸不到 Looper。
 */
public class LiveSnapshotFromSpecTest {

    private static final String HISTORY_KEY = "simplelive@@@douyu_6979222@@@0";

    /** play 结果的真实形状：只有流地址、没有 list（五平台 JS 源与 py 蜘蛛同理）。 */
    private static Result playResult() {
        Result result = new Result();
        result.setUrl("https://douyu-cdn.example.com/live/6979222.flv");
        return result;
    }

    /** 详情结果的真实形状：list 第一项即详情 Vod（douyu_{rid} 前缀）。
     *  不设 vodName：Vod.getName() 走 Html.fromHtml，JVM 单测的 android 桩不可用；本套件只验 id 接缝，名字不在断言内。 */
    private static Result detailResult() {
        Vod vod = new Vod();
        vod.setId("douyu_6979222");
        vod.setPlayFrom("斗鱼直播");
        vod.setPlayUrl("原画$douyu_6979222");
        Result result = new Result();
        result.setList(List.of(vod));
        return result;
    }

    @Test
    public void douyuResolvesViaDetailVodIdOnPlayShapedSpec() {
        Result play = playResult();
        // 生产形状自证：play 结果的 Vod 恒空，这正是旧快照 id 恒空的根
        assertEquals("", play.getVod().getId());
        PlaySpec spec = PlaySpec.from(play, HISTORY_KEY, null).setDetailVodId("douyu_6979222");

        Optional<RoomRef> ref = LiveRoomResolver.resolve(spec.liveDanmakuSnapshot());

        assertTrue(ref.isPresent());
        assertEquals(RoomRef.Platform.DOUYU, ref.get().platform());
        assertEquals("6979222", ref.get().roomId());
    }

    @Test
    public void playShapedSpecWithoutDetailIdStaysUnresolved() {
        PlaySpec spec = PlaySpec.from(playResult(), HISTORY_KEY, null);
        assertTrue(LiveRoomResolver.resolve(spec.liveDanmakuSnapshot()).isEmpty());
    }

    @Test
    public void resultVodFallbackStillResolves() {
        PlaySpec spec = PlaySpec.from(detailResult(), HISTORY_KEY, null);
        Optional<RoomRef> ref = LiveRoomResolver.resolve(spec.liveDanmakuSnapshot());
        assertTrue(ref.isPresent());
        assertEquals("6979222", ref.get().roomId());
    }

    @Test
    public void detailVodIdWinsOverResultVod() {
        PlaySpec spec = PlaySpec.from(detailResult(), HISTORY_KEY, null).setDetailVodId("douyu_111");
        Optional<RoomRef> ref = LiveRoomResolver.resolve(spec.liveDanmakuSnapshot());
        assertTrue(ref.isPresent());
        assertEquals("111", ref.get().roomId());
    }

    @Test
    public void formatCopyKeepsDetailVodId() {
        PlaySpec spec = PlaySpec.from(playResult(), HISTORY_KEY, null).setDetailVodId("douyu_6979222");
        PlaySpec copy = spec.copyWithFormat("application/x-mpegURL");
        assertEquals("douyu_6979222", copy.getDetailVodId());
        assertTrue(LiveRoomResolver.resolve(copy.liveDanmakuSnapshot()).isPresent());
    }
}
