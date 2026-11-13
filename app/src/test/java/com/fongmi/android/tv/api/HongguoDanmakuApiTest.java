package com.fongmi.android.tv.api;

import org.json.JSONObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * HongguoDanmakuApi 纯逻辑单测：窗口推进纪律（不推进即停，防死循环）、
 * Bilibili XML 转换（转义/时间格式/排序）、窗口合并去重、一窗解析与回验闸。
 * 样本形态照 hongguo-danmaku-trace.md §2 与第一步实打样本 danmaku-window0。
 */
public class HongguoDanmakuApiTest {

    private static final String SERIES = "7695727473111092286";
    private static final String VID = "7695729394496588862";

    // ---------- 窗口推进 ----------

    @Test
    public void windowAdvancesWhenServerPushesForward() {
        assertEquals(30000L, HongguoDanmakuApi.nextWindowStart(0, 30000, true));
        assertEquals(60000L, HongguoDanmakuApi.nextWindowStart(30000, 60000, true));
    }

    @Test
    public void windowStopsWhenNoMore() {
        assertEquals(-1L, HongguoDanmakuApi.nextWindowStart(0, 30000, false));
    }

    @Test
    public void windowStopsWhenCursorStalls() {
        // 服务端回原地/回退/没给推进值，一律停——游标不推进即停
        assertEquals(-1L, HongguoDanmakuApi.nextWindowStart(30000, 30000, true));
        assertEquals(-1L, HongguoDanmakuApi.nextWindowStart(30000, 15000, true));
        assertEquals(-1L, HongguoDanmakuApi.nextWindowStart(0, -1, true));
    }

    // ---------- XML 转换 ----------

    @Test
    public void xmlEscapesSpecialChars() {
        List<HongguoDanmakuApi.Item> items = new ArrayList<>();
        items.add(new HongguoDanmakuApi.Item(0, "a<b>&\"'c"));
        String xml = HongguoDanmakuApi.buildXml(items);
        assertTrue(xml.contains("a&lt;b&gt;&amp;&quot;&apos;c"));
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?><i>"));
        assertTrue(xml.endsWith("</i>"));
    }

    @Test
    public void xmlDropsIllegalControlChars() {
        List<HongguoDanmakuApi.Item> items = new ArrayList<>();
        items.add(new HongguoDanmakuApi.Item(0, "前后"));
        String xml = HongguoDanmakuApi.buildXml(items);
        assertTrue(xml.contains("前后"));
    }

    @Test
    public void xmlTimeFormatAndSort() {
        List<HongguoDanmakuApi.Item> items = new ArrayList<>();
        items.add(new HongguoDanmakuApi.Item(20187, "晚"));
        items.add(new HongguoDanmakuApi.Item(2163, "早"));
        String xml = HongguoDanmakuApi.buildXml(items);
        int early = xml.indexOf("p=\"2.163,1,25,16777215,0,0\"");
        int late = xml.indexOf("p=\"20.187,1,25,16777215,0,0\"");
        assertTrue(early >= 0 && late > early);
    }

    // ---------- 合并去重 ----------

    @Test
    public void mergeDedupesByOffsetAndText() {
        List<HongguoDanmakuApi.Item> acc = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<HongguoDanmakuApi.Item> window = new ArrayList<>();
        window.add(new HongguoDanmakuApi.Item(1000, "同一条"));
        window.add(new HongguoDanmakuApi.Item(1000, "同一条"));
        window.add(new HongguoDanmakuApi.Item(1000, "同时间不同文"));
        window.add(new HongguoDanmakuApi.Item(2000, ""));
        window.add(new HongguoDanmakuApi.Item(-5, "无时间"));
        HongguoDanmakuApi.mergeWindow(acc, seen, window);
        assertEquals(2, acc.size());
    }

    // ---------- 一窗解析 ----------

    private static JSONObject windowJson(String bookId, String groupId, boolean hasMore, long next) throws Exception {
        String body = "{"
                + "\"code\":0,"
                + "\"data\":{"
                + "\"common_list_info\":{\"total\":5,\"has_more\":" + hasMore + ",\"cursor\":\"cur-1\"},"
                + "\"extra\":{\"next_query_danmaku_list_time\":" + next + "},"
                + "\"data_list\":["
                + "{\"comment\":{\"common\":{\"group_id\":\"" + groupId + "\",\"content\":{\"text\":\"第一条\"}},\"expand\":{\"book_id\":\"" + bookId + "\",\"offset_time\":2163}}},"
                + "{\"comment\":{\"common\":{\"group_id\":\"" + groupId + "\",\"content\":{\"text\":\"\"}},\"expand\":{\"book_id\":\"" + bookId + "\",\"offset_time\":3000}}},"
                + "{\"comment\":{\"common\":{\"group_id\":\"" + groupId + "\",\"content\":{\"text\":\"没时间\"}},\"expand\":{\"book_id\":\"" + bookId + "\"}}}"
                + "]}}";
        return new JSONObject(body);
    }

    @Test
    public void parseWindowKeepsValidRowsOnly() throws Exception {
        HongguoDanmakuApi.Window window = HongguoDanmakuApi.parseWindow(windowJson(SERIES, VID, true, 30000), SERIES, VID);
        assertTrue(window != null);
        assertEquals(1, window.items.size());
        assertEquals(2163L, window.items.get(0).offsetMs);
        assertEquals("第一条", window.items.get(0).text);
        assertEquals(30000L, window.nextStart);
        assertTrue(window.hasMore);
        assertEquals("cur-1", window.cursor);
        // 游标原样带出、推进交给 nextWindowStart 裁定（面板/抓取共用同一纪律）
        assertEquals(30000L, HongguoDanmakuApi.nextWindowStart(0, window.nextStart, window.hasMore));
    }

    @Test
    public void parseWindowGateRejectsForeignBook() throws Exception {
        assertNull(HongguoDanmakuApi.parseWindow(windowJson("9999999999999999999", VID, true, 30000), SERIES, VID));
        assertNull(HongguoDanmakuApi.parseWindow(windowJson(SERIES, "8888888888888888888", true, 30000), SERIES, VID));
    }

    @Test
    public void parseWindowRejectsErrorCode() throws Exception {
        JSONObject bad = new JSONObject("{\"code\":110001,\"data\":{}}");
        assertNull(HongguoDanmakuApi.parseWindow(bad, SERIES, VID));
        assertNull(HongguoDanmakuApi.parseWindow(null, SERIES, VID));
    }

    @Test
    public void parseWindowSkipsZeroBookIdLeg() throws Exception {
        // 601 真人行 book_id 恒为 "0"（未带书籍归属）：此腿跳过，窗口照常产出，不许整窗作废
        HongguoDanmakuApi.Window window = HongguoDanmakuApi.parseWindow(windowJson("0", VID, true, 30000), SERIES, VID);
        assertTrue(window != null);
        assertEquals(1, window.items.size());
        assertEquals(2163L, window.items.get(0).offsetMs);
    }

    // ---------- 真样本回归（第一步实打落盘的 601 首窗响应） ----------

    private String readResource(String resource) throws Exception {
        InputStream stream = getClass().getResourceAsStream(resource);
        assertNotNull(stream);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    @Test
    public void parseWindowAcceptsRealSample() throws Exception {
        // 真样本形态：第 0 行是平台活动行（book_id＝本剧），第 1~4 行真人弹幕 book_id 全为 "0"。
        // 旧闸照评论语义判 0≠seriesId 整窗作废、真机每窗必废——此样本必须整窗过、5 条全入。
        JSONObject sample = new JSONObject(readResource("/hongguo-danmaku-window0.json"));
        HongguoDanmakuApi.Window window = HongguoDanmakuApi.parseWindow(sample, SERIES, VID);
        assertTrue(window != null);
        assertEquals(5, window.items.size());
        long[] offsets = {0, 2163, 10686, 14890, 20187};
        for (int i = 0; i < offsets.length; i++) assertEquals(offsets[i], window.items.get(i).offsetMs);
        assertTrue(window.hasMore);
        assertEquals(30000L, window.nextStart);
    }

    @Test
    public void collectProducesFromRealSampleFirstWindow() throws Exception {
        // 真样本当第一窗喂进拉取循环：首窗即产出 5 条并触发增长回调，不再恒空收场
        JSONObject sample = new JSONObject(readResource("/hongguo-danmaku-window0.json"));
        HongguoDanmakuApi.Window first = HongguoDanmakuApi.parseWindow(sample, SERIES, VID);
        assertTrue(first != null);
        ScriptedSource source = new ScriptedSource(List.of(first));
        List<List<HongguoDanmakuApi.Item>> growth = new ArrayList<>();
        List<HongguoDanmakuApi.Item> all = HongguoDanmakuApi.collectWindows(source, 0, growth::add);
        assertEquals(5, all.size());
        assertEquals(1, growth.size());
        assertEquals(5, growth.get(0).size());
    }

    // ---------- 增量拉取循环（collectWindows） ----------

    private static HongguoDanmakuApi.Window windowOf(long next, boolean hasMore, HongguoDanmakuApi.Item... items) {
        return new HongguoDanmakuApi.Window(List.of(items), next, hasMore, "cur");
    }

    private static HongguoDanmakuApi.Item item(long offset, String text) {
        return new HongguoDanmakuApi.Item(offset, text);
    }

    /** 按脚本逐窗回放的假数据源：脚本走完回 null（模拟失败/无更多）。 */
    private static class ScriptedSource implements HongguoDanmakuApi.WindowSource {
        private final List<HongguoDanmakuApi.Window> script;
        int fetches;

        ScriptedSource(List<HongguoDanmakuApi.Window> script) {
            this.script = script;
        }

        @Override
        public HongguoDanmakuApi.Window fetch(long start, String cursor) {
            fetches++;
            return fetches <= script.size() ? script.get(fetches - 1) : null;
        }
    }

    @Test
    public void collectKeepsPartialWhenLaterWindowFails() {
        // 第一窗拿到货、第二窗失败（回 null）：已得部分保留，不再整集连坐
        ScriptedSource source = new ScriptedSource(List.of(
                windowOf(30000, true, item(1000, "第一条"), item(2000, "第二条"))));
        List<List<HongguoDanmakuApi.Item>> growth = new ArrayList<>();
        List<HongguoDanmakuApi.Item> all = HongguoDanmakuApi.collectWindows(source, 0, growth::add);
        assertEquals(2, all.size());
        assertEquals(1, growth.size());
        assertEquals(2, growth.get(0).size());
        assertEquals(2, source.fetches);
    }

    @Test
    public void collectFirstWindowFailureYieldsEmpty() {
        ScriptedSource source = new ScriptedSource(List.of());
        List<List<HongguoDanmakuApi.Item>> growth = new ArrayList<>();
        List<HongguoDanmakuApi.Item> all = HongguoDanmakuApi.collectWindows(source, 0, growth::add);
        assertTrue(all.isEmpty());
        assertTrue(growth.isEmpty());
    }

    @Test
    public void collectGrowthSnapshotsAccumulateWithDedup() {
        // 三窗连续推进：快照逐窗变大，跨窗重复条目被去重吃掉、不触发虚假增长
        ScriptedSource source = new ScriptedSource(List.of(
                windowOf(30000, true, item(1000, "一")),
                windowOf(60000, true, item(1000, "一"), item(31000, "二")),
                windowOf(-1, false, item(61000, "三"))));
        List<List<HongguoDanmakuApi.Item>> growth = new ArrayList<>();
        List<HongguoDanmakuApi.Item> all = HongguoDanmakuApi.collectWindows(source, 0, growth::add);
        assertEquals(3, all.size());
        assertEquals(3, growth.size());
        assertEquals(1, growth.get(0).size());
        assertEquals(2, growth.get(1).size());
        assertEquals(3, growth.get(2).size());
    }

    @Test
    public void collectRetryAfterFailureHasNoStickyState() {
        // 同一个数据源先失败后恢复：抓取层无静态状态，第二次调用从头来、能拿全
        ScriptedSource flaky = new ScriptedSource(List.of(
                windowOf(30000, true, item(1000, "一"))));
        assertEquals(1, HongguoDanmakuApi.collectWindows(flaky, 0, null).size());
        ScriptedSource healthy = new ScriptedSource(List.of(
                windowOf(30000, true, item(1000, "一")),
                windowOf(-1, false, item(31000, "二"))));
        List<HongguoDanmakuApi.Item> all = HongguoDanmakuApi.collectWindows(healthy, 0, null);
        assertEquals(2, all.size());
        assertEquals(2, healthy.fetches);
    }

    @Test
    public void collectStopsAtDurationBoundary() {
        // 已知时长：起点越过时长就停，不再多取窗
        ScriptedSource source = new ScriptedSource(List.of(
                windowOf(30000, true, item(1000, "一")),
                windowOf(60000, true, item(31000, "二"))));
        List<HongguoDanmakuApi.Item> all = HongguoDanmakuApi.collectWindows(source, 30000, null);
        assertEquals(1, all.size());
        assertEquals(1, source.fetches);
    }

    // ---------- 完整性结果与种子续拉（预拉/缓存复用口径） ----------

    @Test
    public void collectCompleteWhenServerEndsNaturally() {
        // 两窗拉完、服务端明说没更多了：自然收尾，complete=true（配落完成标记、免重复拉）
        ScriptedSource source = new ScriptedSource(List.of(
                windowOf(30000, true, item(1000, "一")),
                windowOf(-1, false, item(31000, "二"))));
        HongguoDanmakuApi.CollectOutcome outcome = HongguoDanmakuApi.collectWindows(source, 0, null, null);
        assertTrue(outcome.complete);
        assertEquals(2, outcome.items.size());
        assertEquals(2, source.fetches);
    }

    @Test
    public void collectIncompleteWhenWindowFailsMidway() {
        // 第二窗失败：已得部分保留，但 complete=false——半截不记完成，下次继续补
        ScriptedSource source = new ScriptedSource(List.of(
                windowOf(30000, true, item(1000, "一"), item(2000, "二"))));
        HongguoDanmakuApi.CollectOutcome outcome = HongguoDanmakuApi.collectWindows(source, 0, null, null);
        assertEquals(2, outcome.items.size());
        assertTrue(!outcome.complete);
    }

    @Test
    public void collectCompleteAtDurationBoundary() {
        // 时长到头收尾算自然收尾，不是失败
        ScriptedSource source = new ScriptedSource(List.of(
                windowOf(30000, true, item(1000, "一")),
                windowOf(60000, true, item(31000, "二"))));
        HongguoDanmakuApi.CollectOutcome outcome = HongguoDanmakuApi.collectWindows(source, 30000, null, null);
        assertTrue(outcome.complete);
        assertEquals(1, outcome.items.size());
        assertEquals(1, source.fetches);
    }

    @Test
    public void collectSeedIsKeptAndMergedWithDedup() {
        // 半截文件的旧货当种子：重拉的新窗并进来、跨新旧重复的去重吃掉，只长不缩
        List<HongguoDanmakuApi.Item> seed = List.of(item(1000, "一"), item(5000, "旧"));
        ScriptedSource source = new ScriptedSource(List.of(
                windowOf(30000, true, item(1000, "一"), item(31000, "二")),
                windowOf(-1, false, item(61000, "三"))));
        List<List<HongguoDanmakuApi.Item>> growth = new ArrayList<>();
        HongguoDanmakuApi.CollectOutcome outcome = HongguoDanmakuApi.collectWindows(source, 0, growth::add, seed);
        assertEquals(4, outcome.items.size());
        assertTrue(outcome.complete);
        // 增长快照含种子：第一窗后 3 条、第二窗后 4 条
        assertEquals(2, growth.size());
        assertEquals(3, growth.get(0).size());
        assertEquals(4, growth.get(1).size());
    }

    @Test
    public void collectSeedSurvivesImmediateFailure() {
        // 种子在手、重拉第一窗就失败：旧货不丢，且不冒充完整
        List<HongguoDanmakuApi.Item> seed = List.of(item(1000, "一"), item(5000, "旧"));
        ScriptedSource source = new ScriptedSource(List.of());
        HongguoDanmakuApi.CollectOutcome outcome = HongguoDanmakuApi.collectWindows(source, 0, null, seed);
        assertEquals(2, outcome.items.size());
        assertTrue(!outcome.complete);
    }

    @Test
    public void xmlItemsRoundTripThroughBuildAndParse() {
        // buildXml 写出的文件能被 parseXmlItems 原样读回（种子续拉的前提），特殊字符也对得上
        List<HongguoDanmakuApi.Item> items = new ArrayList<>();
        items.add(new HongguoDanmakuApi.Item(20187, "晚"));
        items.add(new HongguoDanmakuApi.Item(2163, "a<b>&\"'c"));
        List<HongguoDanmakuApi.Item> back = HongguoDanmakuApi.parseXmlItems(HongguoDanmakuApi.buildXml(items));
        assertEquals(2, back.size());
        assertEquals(2163L, back.get(0).offsetMs);
        assertEquals("a<b>&\"'c", back.get(0).text);
        assertEquals(20187L, back.get(1).offsetMs);
        assertEquals("晚", back.get(1).text);
    }
}
