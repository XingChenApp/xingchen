package com.fongmi.android.tv.player.danmaku.live;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 识别层：斗鱼/B站强信号（前缀/链接）成立；B站裸数字只许带 B站明文语境作候选，
 * 其余一律不接——裸数字/标题/域名类弱信号永不单独定罪（设计第四节降级口径）。
 */
public class LiveRoomResolverTest {

    private static LiveRoomResolver.Snapshot snapshot(String vodId, String playUrl) {
        return new LiveRoomResolver.Snapshot("site", vodId, playUrl, "", "", "");
    }

    @Test
    public void douyuPrefixResolves() {
        Optional<RoomRef> ref = LiveRoomResolver.resolve(snapshot("douyu_9999", ""));
        assertTrue(ref.isPresent());
        assertEquals(RoomRef.Platform.DOUYU, ref.get().platform());
        assertEquals("9999", ref.get().roomId());
        assertEquals("douyu:9999", ref.get().key());
    }

    @Test
    public void douyuLinkResolvesFromVodIdAndPlayUrl() {
        assertEquals("12345", LiveRoomResolver.resolve(snapshot("https://live.douyu.com/12345", "")).get().roomId());
        assertEquals("777", LiveRoomResolver.resolve(snapshot("", "episode$https://live.douyu.com/777?from=x")).get().roomId());
    }

    @Test
    public void weakSignalsNeverResolve() {
        assertTrue(LiveRoomResolver.resolve(snapshot("9999", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(snapshot("", "https://cdn.example.com/douyucdn/9999.flv")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(new LiveRoomResolver.Snapshot("斗鱼源", "", "", "斗鱼直播", "某主播", "")).isEmpty());
    }

    @Test
    public void malformedIdsDoNotResolve() {
        assertTrue(LiveRoomResolver.resolve(snapshot("douyu_", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(snapshot("douyu_abc", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(snapshot("douyu_1234567890123456", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(snapshot("bili_", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(snapshot("bili_abc", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(snapshot("bili_1234567890123456", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(LiveRoomResolver.Snapshot.EMPTY).isEmpty());
        assertTrue(LiveRoomResolver.resolve(null).isEmpty());
    }

    @Test
    public void biliPrefixResolves() {
        Optional<RoomRef> ref = LiveRoomResolver.resolve(snapshot("bili_9999", ""));
        assertTrue(ref.isPresent());
        assertEquals(RoomRef.Platform.BILI, ref.get().platform());
        assertEquals("9999", ref.get().roomId());
        assertEquals("bili:9999", ref.get().key());
    }

    @Test
    public void biliLinkResolvesFromVodIdAndPlayUrl() {
        assertEquals("21452505", LiveRoomResolver.resolve(snapshot("https://live.bilibili.com/21452505", "")).get().roomId());
        assertEquals("888", LiveRoomResolver.resolve(snapshot("", "episode$https://live.bilibili.com/888?from=x")).get().roomId());
    }

    @Test
    public void biliBareDigitsNeedExplicitBiliContext() {
        // SimpleLive 形态：源名 SimpleLive＋playFrom 哔哩直播＋裸数字
        Optional<RoomRef> bySource = LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("SimpleLive", "123456", "", "哔哩直播", "某主播", ""));
        assertTrue(bySource.isPresent());
        assertEquals(RoomRef.Platform.BILI, bySource.get().platform());
        assertEquals("123456", bySource.get().roomId());
        // 只有 playFrom 明文标记也成立
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("site", "123456", "", "哔哩直播", "", "")).isPresent());
        // 无语境的裸数字永不成立（与斗鱼共用同一断言面）
        assertTrue(LiveRoomResolver.resolve(snapshot("123456", "")).isEmpty());
        // 别家语境不许冒认：斗鱼/虎牙 playFrom 下的裸数字不归 B站
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("site", "123456", "", "斗鱼直播", "", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("site", "123456", "", "虎牙直播", "", "")).isEmpty());
        // B站 CDN 域名只是旁证、不单独成立
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("site", "123456", "", "", "", "https://cdn.example.com/bilivideo/123456.flv")).isEmpty());
        // 聚合源名 SimpleLive 只是弱语境：playFrom/标题明写别家时不许被它冒认成 B站
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("SimpleLive", "123456", "", "斗鱼直播", "", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("SimpleLive", "123456", "", "抖音直播", "", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("SimpleLive", "123456", "", "", "虎牙直播某主播", "")).isEmpty());
        // 聚合源名下无别家标记时弱语境成立（该源里裸数字即 B站形态）
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("SimpleLive", "123456", "", "", "某主播", "")).isPresent());
        // B站明文标记优先于标题里的别家字样
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("site", "123456", "", "哔哩直播", "斗鱼一哥跨界", "")).isPresent());
    }

    @Test
    public void huyaPrefixResolves() {
        Optional<RoomRef> ref = LiveRoomResolver.resolve(snapshot("huya_998", ""));
        assertTrue(ref.isPresent());
        assertEquals(RoomRef.Platform.HUYA, ref.get().platform());
        assertEquals("998", ref.get().roomId());
        assertEquals("huya:998", ref.get().key());
        // 字母短号房（主播自定义房号）同样成立
        assertEquals("lpl", LiveRoomResolver.resolve(snapshot("huya_lpl", "")).get().roomId());
    }

    @Test
    public void huyaLinkResolvesFromVodIdAndPlayUrl() {
        assertEquals("998", LiveRoomResolver.resolve(snapshot("https://www.huya.com/998", "")).get().roomId());
        assertEquals("518622", LiveRoomResolver.resolve(snapshot("", "episode$https://m.huya.com/518622?from=x")).get().roomId());
    }

    @Test
    public void huyaBareDigitsNeverResolve() {
        // 虎牙不设裸数字路：带虎牙语境的裸数字也不认（防冒认口径，与斗鱼/B站的强信号原则一致）
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("site", "123456", "", "虎牙直播", "", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("SimpleLive", "123456", "", "虎牙直播", "某主播", "")).isEmpty());
    }

    @Test
    public void huyaMalformedIdsDoNotResolve() {
        assertTrue(LiveRoomResolver.resolve(snapshot("huya_", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(snapshot("huya_123456789012345678901", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(snapshot("huya_有中文", "")).isEmpty());
    }

    @Test
    public void douyinPrefixResolves() {
        Optional<RoomRef> ref = LiveRoomResolver.resolve(snapshot("douyin_974709925783", ""));
        assertTrue(ref.isPresent());
        assertEquals(RoomRef.Platform.DOUYIN, ref.get().platform());
        assertEquals("974709925783", ref.get().roomId());
        assertEquals("douyin:974709925783", ref.get().key());
    }

    @Test
    public void douyinLinkResolvesFromVodIdAndPlayUrl() {
        assertEquals("974709925783", LiveRoomResolver.resolve(snapshot("https://live.douyin.com/974709925783", "")).get().roomId());
        assertEquals("73897632289", LiveRoomResolver.resolve(snapshot("", "episode$https://live.douyin.com/73897632289?from=x")).get().roomId());
    }

    @Test
    public void douyinBareDigitsNeverResolve() {
        // 抖音不设裸数字路：带抖音语境的裸数字也不认（与虎牙同一防冒认口径）
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("site", "974709925783", "", "抖音直播", "", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("SimpleLive", "974709925783", "", "抖音直播", "某主播", "")).isEmpty());
        // 别家域名里的数字段不许被抖音链接式冒认
        assertTrue(LiveRoomResolver.resolve(snapshot("", "https://live.douyu.com/974709925783")).get().platform() == RoomRef.Platform.DOUYU);
    }

    @Test
    public void douyinMalformedIdsDoNotResolve() {
        assertTrue(LiveRoomResolver.resolve(snapshot("douyin_", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(snapshot("douyin_abc", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(snapshot("douyin_12345678901234567890123", "")).isEmpty());
    }

    @Test
    public void kuaishouPrefixResolves() {
        Optional<RoomRef> ref = LiveRoomResolver.resolve(snapshot("kuaishou_3xbjm6fvq23k6ec", ""));
        assertTrue(ref.isPresent());
        assertEquals(RoomRef.Platform.KUAISHOU, ref.get().platform());
        assertEquals("3xbjm6fvq23k6ec", ref.get().roomId());
        assertEquals("kuaishou:3xbjm6fvq23k6ec", ref.get().key());
        // 带连字符的主播自定义 id（laoayi-88 形态）同样成立
        assertEquals("laoayi-88", LiveRoomResolver.resolve(snapshot("kuaishou_laoayi-88", "")).get().roomId());
    }

    @Test
    public void kuaishouLinkResolvesFromVodIdAndPlayUrl() {
        assertEquals("3xw9zpeg8xyf432", LiveRoomResolver.resolve(snapshot("https://live.kuaishou.com/u/3xw9zpeg8xyf432", "")).get().roomId());
        assertEquals("zzz8800555", LiveRoomResolver.resolve(snapshot("", "episode$https://live.kuaishou.com/u/zzz8800555?from=x")).get().roomId());
    }

    @Test
    public void kuaishouBareDigitsNeverResolve() {
        // 快手不设裸数字路：带快手语境的裸数字也不认（与虎牙/抖音同一防冒认口径）
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("site", "123456", "", "快手直播", "", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(
                new LiveRoomResolver.Snapshot("SimpleLive", "123456", "", "快手直播", "某主播", "")).isEmpty());
        // 快手 CDN 域名里的数字段不许被快手链接式冒认（链接式只认 /u/ 段）
        assertTrue(LiveRoomResolver.resolve(snapshot("", "https://tx-origin.pull.yximgs.com/gifshow/1_B7BrugJfg.flv")).isEmpty());
    }

    @Test
    public void kuaishouMalformedIdsDoNotResolve() {
        assertTrue(LiveRoomResolver.resolve(snapshot("kuaishou_", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(snapshot("kuaishou_有中文", "")).isEmpty());
        assertTrue(LiveRoomResolver.resolve(snapshot("kuaishou_123456789012345678901234567890123", "")).isEmpty());
    }
}
